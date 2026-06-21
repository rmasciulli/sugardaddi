package li.masciul.sugardaddi.data.repository;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.LiveData;

// Database imports
import li.masciul.sugardaddi.SugarDaddiApplication;
import li.masciul.sugardaddi.core.interfaces.Searchable;
import li.masciul.sugardaddi.core.models.Error;
import li.masciul.sugardaddi.core.models.SourceIdentifier;
import li.masciul.sugardaddi.data.database.entities.FoodProductEntity;
import li.masciul.sugardaddi.data.database.entities.NutritionEntity;
import li.masciul.sugardaddi.data.database.relations.FoodProductWithNutrition;
import li.masciul.sugardaddi.data.database.AppDatabase;

// Network imports
import li.masciul.sugardaddi.data.network.NetworkManager;
import li.masciul.sugardaddi.data.network.ApiConfig;

// DataSource imports
import li.masciul.sugardaddi.data.sources.base.CacheStrategy;
import li.masciul.sugardaddi.data.sources.base.DataSource;
import li.masciul.sugardaddi.data.sources.base.DataSourceCallback;

// Core models
import li.masciul.sugardaddi.core.models.FoodProduct;

// Managers and utilities
import li.masciul.sugardaddi.managers.LanguageManager;
import li.masciul.sugardaddi.managers.DataSourceManager;
import li.masciul.sugardaddi.utils.image.ThumbnailDownloader;

// Cache

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

/**
 * ProductRepository - product detail loading, caching, and favourite management.
 *
 *   - Cache-first detail loading (resolveProduct): Room-first reads with
 *     stale-while-revalidate, dual-table storage (FoodProductEntity + NutritionEntity).
 *   - Favourite management and thumbnail caching.
 *   - Cache maintenance (clear / stats / validity).
 */
public class ProductRepository {

    private static final String TAG = ApiConfig.SEARCH_LOG_TAG;

    // ========== CORE DEPENDENCIES ==========

    private final NetworkManager networkManager;
    private final Context context;
    private final ExecutorService backgroundExecutor;
    private final AppDatabase database;

    // ========== SEARCH INFRASTRUCTURE ==========

    private final DataSourceManager dataSourceManager;

    // ========== CONFIGURATION ==========

    // Staleness threshold (7 days for favorites)
    private static final long FAVORITE_CACHE_VALIDITY_MS = 7 * 24 * 60 * 60 * 1000;

    // ========== STATE TRACKING ==========

    private boolean isOperationInProgress = false;

    // ========== CALLBACK INTERFACES ==========

    /**
     * Callback interface for individual product operations
     */
    public interface ProductCallback {
        void onSuccess(FoodProduct product);
        void onError(Error error);
        void onLoading();

        /**
         * A stale-triggered background refresh found CHANGED upstream content.
         * The candidate is offered, not applied - the detail screen shows the
         * refresh FAB and applies it via applyCandidate() on tap, so data never
         * changes under the user mid-view. Default no-op: callers that don't show
         * a detail screen (search, favourites) ignore it.
         */
        default void onRefreshAvailable(FoodProduct candidate) {}
    }

    /**
     * Callback interface for favorite operations
     */
    public interface FavoriteCallback {
        void onFavoriteStatus(boolean isFavorite);
        void onFavoriteToggled(boolean newStatus);
        void onError(String message);
    }

    private interface NetworkFetch {
        void fetch(FetchCallback cb);
    }
    
    private interface FetchCallback {
        void onSuccess(FoodProduct product);
        void onError(Error error);
    }

    // ========== CONSTRUCTOR ==========

    /**
     * Constructor - requires configured dependencies
     * Consolidates initialization from both former repositories
     */
    public ProductRepository(NetworkManager networkManager, Context context) {
        this.networkManager = networkManager;
        this.context = context.getApplicationContext();
        this.backgroundExecutor = Executors.newSingleThreadExecutor();
        this.database = AppDatabase.getInstance(context);

        // Initialize search infrastructure (from FoodRepository)
        this.dataSourceManager = DataSourceManager.getInstance(context);

        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, "ProductRepository initialized with consolidated functionality (Search + Database)");
        }
    }

    // ========== INDIVIDUAL PRODUCT OPERATIONS ==========

    /** Load a product by barcode (OpenFoodFacts), cache-first. */
    public void loadProduct(String barcode, ProductCallback callback) {
        if (barcode == null || barcode.trim().isEmpty()) {
            callback.onError(Error.invalidRequest("Invalid barcode provided", null));
            return;
        }
        final String cleanBarcode = barcode.trim();
        // Barcode rows are keyed on the barcode column; OFF is the network source.
        resolveProduct(cleanBarcode,
                cb -> networkManager.getProduct(cleanBarcode,
                        new NetworkManager.NetworkCallback<FoodProduct>() {
                            @Override public void onSuccess(FoodProduct product) { cb.onSuccess(product); }
                            @Override public void onFailure(String error) { cb.onError(mapNetworkError(error, cleanBarcode)); }
                        }),
                CacheStrategy.defaultStrategy(),
                callback);
    }

    /**
     * Load a product from a specific source by its source-native id (e.g. Ciqual
     * "31020"), cache-first. Goes Room-first like the barcode path - the previous
     * "does NOT use cache" behaviour was the root of the source-path image bug.
     */
    public void loadProductFromSource(String sourceId, String productId, ProductCallback callback) {
        if (sourceId == null || sourceId.trim().isEmpty()) {
            callback.onError(Error.validation("Source ID cannot be empty", null));
            return;
        }
        if (productId == null || productId.trim().isEmpty()) {
            callback.onError(Error.validation("Product ID cannot be empty", null));
            return;
        }
        final String cleanSourceId = sourceId.trim();
        final String cleanProductId = productId.trim();

        DataSource source = dataSourceManager.getDataSource(cleanSourceId);
        if (source == null) {
            callback.onError(Error.validation(
                    String.format("Data source '%s' not found or not registered", cleanSourceId),
                    "Available sources: " + dataSourceManager.getAllDataSources().size()));
            return;
        }
        if (!source.isAvailable()) {
            callback.onError(Error.validation(
                    String.format("Data source '%s' is not available or not initialized", cleanSourceId),
                    "Source may still be initializing. Try again in a moment."));
            return;
        }

        final String language = LanguageManager.getCurrentLanguage(context).getCode();
        // Same string as FoodProduct.getSearchableId() / the entity id.
        final String roomKey = new SourceIdentifier(cleanSourceId, cleanProductId).getCombinedId();

        resolveProduct(roomKey,
                cb -> source.getProduct(cleanProductId, language,
                        new DataSourceCallback<FoodProduct>() {
                            @Override public void onSuccess(FoodProduct product) { cb.onSuccess(product); }
                            @Override public void onError(Error error) { cb.onError(error); }
                            @Override public void onLoading() {}
                        }),
                source.getCacheStrategy(),
                callback);
    }

    /**
     * Force refresh product from network (ignores cache)
     */
    public void refreshProduct(String barcode, ProductCallback callback) {
        if (barcode == null || barcode.trim().isEmpty()) {
            callback.onError(Error.invalidRequest("Invalid barcode provided", null));
            return;
        }

        callback.onLoading();
        fetchFromNetwork(barcode.trim(), callback);
    }

    // ========== FAVORITE MANAGEMENT ==========

    /**
     * Get favorite status for a product
     */
    public void getFavoriteStatus(String productId, FavoriteCallback callback) {
        if (productId == null || productId.trim().isEmpty()) {
            callback.onError("Invalid product ID");
            return;
        }

        backgroundExecutor.execute(() -> {
            try {
                // Use getProductById (same as toggleFavorite) instead of getProductByBarcode
                FoodProductEntity product = database.foodProductDao()
                        .getProductById(productId.trim());
                boolean isFavorite = product != null && product.isFavorite();
                runOnMainThread(() -> callback.onFavoriteStatus(isFavorite));
            } catch (Exception e) {
                Log.e(TAG, "Error checking favorite status", e);
                runOnMainThread(() -> callback.onError("Could not check favorite status"));
            }
        });
    }

    /**
     * Toggles the favourite status of a product.
     *
     * After the Room write completes, handleThumbnailForFavorite() is called
     * to download or delete the cached thumbnail in the background. The
     * FavoriteCallback fires before the thumbnail operation completes - the
     * UI does not wait for it.
     */
    public void toggleFavorite(FoodProduct product, FavoriteCallback callback) {
        if (product == null || product.getSearchableId() == null) {
            callback.onError("Invalid product");
            return;
        }

        backgroundExecutor.execute(() -> {
            try {
                String productId = product.getSearchableId();
                FoodProductEntity entity = database.foodProductDao()
                        .getProductById(productId);

                boolean newStatus;

                if (entity != null) {
                    newStatus = !entity.isFavorite();
                    entity.setFavorite(newStatus);

                    // Clear thumbnailPath immediately when unfavouriting so Room
                    // is consistent before the file is deleted from disk.
                    if (!newStatus) {
                        entity.setThumbnailPath(null);
                    }
                    database.foodProductDao().updateProduct(entity);
                } else {
                    // Not yet in Room - save as favourite
                    saveProductToDatabase(product, true);
                    newStatus = true;
                }

                if (ApiConfig.DEBUG_LOGGING) {
                    Log.d(TAG, "Toggled favorite: " + productId + " → " + newStatus);
                }

                // Thumbnail download/deletion runs on ThumbnailDownloader's own
                // executor - does not block this backgroundExecutor thread.
                handleThumbnailForFavorite(product, productId, newStatus);

                final boolean finalNewStatus = newStatus;
                runOnMainThread(() -> callback.onFavoriteToggled(finalNewStatus));

            } catch (Exception e) {
                Log.e(TAG, "Database error toggling favorite", e);
                runOnMainThread(() -> callback.onError("Could not update favorite status"));
            }
        });
    }

    /**
     * Downloads the thumbnail on favouriting or deletes it on unfavouriting.
     *
     * FAVOURITING
     * ===========
     * Prefers imageThumbnailUrl (CDN-optimised small image) over imageUrl.
     * Falls back to imageUrl if imageThumbnailUrl is absent.
     * On success: persists the local path to food_products.thumbnailPath.
     * On failure: logs and continues - Glide falls back to the remote URL.
     *
     * UNFAVOURITING
     * =============
     * Deletes the cached file from disk via ThumbnailDownloader.deleteThumbnail().
     * thumbnailPath is already cleared in Room before this method is called.
     *
     * @param product    FoodProduct domain object (provides image URLs).
     * @param productId  Source-qualified ID (Room primary key).
     * @param isFavorite The new favourite state just written to Room.
     */
    private void handleThumbnailForFavorite(
            @NonNull FoodProduct product,
            @NonNull String productId,
            boolean isFavorite) {

        ThumbnailDownloader downloader = getThumbnailDownloader();
        if (downloader == null) return;

        if (isFavorite) {
            // Prefer the CDN thumbnail URL; fall back to the full image URL.
            String url = product.getThumbnailUrl();
            if (url == null || url.trim().isEmpty()) {
                url = product.getImageUrl();
            }
            if (url == null || url.trim().isEmpty()) {
                Log.d(TAG, "No image URL available for product " + productId
                        + " - skipping thumbnail download");
                return;
            }

            final String finalUrl = url;
            downloader.download(finalUrl, productId, new ThumbnailDownloader.Callback() {
                @Override
                public void onSuccess(@NonNull String localPath) {
                    // ThumbnailDownloader delivers this callback on the main thread.
                    // Room writes must happen on a background thread.
                    backgroundExecutor.execute(() -> {
                        try {
                            database.foodProductDao().updateThumbnailPath(productId, localPath);
                            if (ApiConfig.DEBUG_LOGGING) {
                                Log.d(TAG, "Thumbnail cached for product "
                                        + productId + ": " + localPath);
                            }
                        } catch (Exception e) {
                            Log.w(TAG, "Failed to persist thumbnailPath for "
                                    + productId, e);
                        }
                    });
                }

                @Override
                public void onError(@NonNull String reason) {
                    // Non-fatal - Glide will load from the remote URL instead.
                    Log.d(TAG, "Thumbnail download failed for product "
                            + productId + ": " + reason);
                }
            });

        } else {
            // File deletion is handled by ThumbnailDownloader on its own executor.
            // thumbnailPath is already null in Room (cleared before this call).
            downloader.deleteThumbnail(productId);
        }
    }

    /**
     * Returns the application-scoped ThumbnailDownloader singleton.
     * Returns null (and logs) if the context is not SugarDaddiApplication -
     * should never happen in production.
     */
    @Nullable
    private ThumbnailDownloader getThumbnailDownloader() {
        if (context.getApplicationContext() instanceof SugarDaddiApplication) {
            return ((SugarDaddiApplication) context.getApplicationContext())
                    .getThumbnailDownloader();
        }
        Log.e(TAG, "Application context is not SugarDaddiApplication "
                + "- thumbnail pipeline unavailable");
        return null;
    }

    /**
     * Get all favorite products
     */
    public LiveData<List<FoodProductWithNutrition>> getFavoriteProducts() {
        return database.combinedProductDao().getFavoriteProductsWithNutrition();
    }

    // ========== CACHE MANAGEMENT ==========

    /**
     * Cancel any ongoing network operations
     */
    public void cancelCurrentOperation() {
        networkManager.cancelCurrentProductFetch();
        isOperationInProgress = false;

        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, "Cancelled ongoing product operations");
        }
    }

    /**
     * Clear all non-favorite products from database cache
     */
    public void clearNonFavoriteCache() {
        backgroundExecutor.execute(() -> {
            try {
                database.foodProductDao().clearNonFavoriteCache();
                // Also clean up orphaned nutrition entries
                database.nutritionDao().deleteOrphanedNutrition();

                if (ApiConfig.DEBUG_LOGGING) {
                    Log.d(TAG, "Cleared non-favorite database cache");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error clearing database cache", e);
            }
        });
    }

    /**
     * Clear entire cache (both search and database)
     */
    public void clearAllCache() {
        // Clear database cache
        backgroundExecutor.execute(() -> {
            try {
                database.foodProductDao().clearAllProducts();
                database.nutritionDao().clearAllNutrition();
                database.mealDao().deleteAll();
                database.recipeDao().deleteAll();

                if (ApiConfig.DEBUG_LOGGING) {
                    Log.d(TAG, "Cleared all cache (search + database)");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error clearing all cache", e);
            }
        });
    }

    // ========== LIFECYCLE MANAGEMENT ==========

    /**
     * Shutdown repository and clean up resources
     */
    public void shutdown() {
        cancelCurrentOperation();
        backgroundExecutor.shutdown();

        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, "ProductRepository shutdown complete");
        }
    }

    // ========== PRIVATE IMPLEMENTATION METHODS ==========

    /**
     * Fetch product from network using NetworkManager
     */
    private void fetchFromNetwork(String barcode, ProductCallback callback) {
        networkManager.getProduct(barcode, new NetworkManager.NetworkCallback<FoodProduct>() {
            @Override
            public void onSuccess(FoodProduct product) {
                if (ApiConfig.DEBUG_LOGGING) {
                    Log.d(TAG, "Network fetch successful for: " + barcode);
                }

                // Save to both tables
                saveProductToDatabase(product, false);

                isOperationInProgress = false;
                callback.onSuccess(product);
            }

            @Override
            public void onFailure(String error) {
                Log.w(TAG, "Network fetch failed for " + barcode + ": " + error);

                // Convert network error to structured Error
                Error apiError;
                if (error.toLowerCase().contains("not found")) {
                    apiError = Error.noData("No product found for barcode: " + barcode);
                } else if (error.toLowerCase().contains("network")) {
                    apiError = Error.network(error, null);
                } else {
                    apiError = Error.network("Failed to load product: " + error, null);
                }

                isOperationInProgress = false;
                callback.onError(apiError);
            }
        });
    }

    /**
     * Fire-and-forget save of a product (+ nutrition). Enqueues the synchronous
     * save on the background executor. Used by callers that don't read the result
     * back (e.g. toggleFavorite's new-favourite branch).
     */
    private void saveProductToDatabase(FoodProduct product, boolean asFavorite) {
        if (product == null) return;
        backgroundExecutor.execute(() -> saveProductToDatabaseSync(product, asFavorite));
    }

    /**
     * Synchronous save of a product (+ nutrition). MUST run on a background thread.
     *
     * Returns only after the Room write completes, so the caller can immediately
     * re-read the saved row. This is what lets resolveProduct() return the merged
     * entity (with preserved local image paths) rather than the raw network object.
     *
     * Preserved from any existing row (never overwritten by a fresh fetch):
     *   isFavorite, the four local image paths, localImport, lastViewed, accessCount.
     */
    @WorkerThread
    private void saveProductToDatabaseSync(FoodProduct product, boolean asFavorite) {
        if (product == null) return;
        try {
            // Existing favourite status must survive a refresh. asFavorite covers the
            // "save as new favourite" path; preserveFavorite covers refreshing a row
            // that was already a favourite.
            FoodProductEntity existingEntity = database.foodProductDao()
                    .getProductById(product.getSearchableId());
            boolean preserveFavorite = (existingEntity != null && existingEntity.isFavorite());

            FoodProductEntity productEntity = FoodProductEntity.fromFoodProduct(product);
            if (asFavorite || preserveFavorite) {
                productEntity.setFavorite(true);
            }

            if (existingEntity != null) {
                // Local image paths - user-set or auto-cached. The API response has
                // no knowledge of local files, so a refresh must never wipe them.
                if (existingEntity.getThumbnailPath() != null) {
                    productEntity.setThumbnailPath(existingEntity.getThumbnailPath());
                }
                if (existingEntity.getImagePath() != null) {
                    productEntity.setImagePath(existingEntity.getImagePath());
                }
                if (existingEntity.getUserThumbnailPath() != null) {
                    productEntity.setUserThumbnailPath(existingEntity.getUserThumbnailPath());
                }
                if (existingEntity.getUserImagePath() != null) {
                    productEntity.setUserImagePath(existingEntity.getUserImagePath());
                }

                // View-time state - a background sync must not reset the eviction
                // clock, zero the access count, or demote a dataset row to evictable.
                productEntity.setLocalImport(existingEntity.isLocalImport());
                productEntity.setLastViewed(existingEntity.getLastViewed());
                productEntity.setAccessCount(existingEntity.getAccessCount());
            }

            productEntity.touch();   // stamps lastUpdated = now (the sync time)

            database.foodProductDao().insertProduct(productEntity);

            // Nutrition lives in a separate table.
            if (product.getNutrition() != null) {
                NutritionEntity nutritionEntity = NutritionEntity.fromNutrition(
                        product.getNutrition(), "product", product.getSearchableId());
                database.nutritionDao().insertNutrition(nutritionEntity);
                if (ApiConfig.DEBUG_LOGGING) {
                    Log.d(TAG, "Saved product with nutrition: "
                            + product.getSearchableId() + " (favorite: " + asFavorite + ")");
                }
            } else if (ApiConfig.DEBUG_LOGGING) {
                Log.d(TAG, "Saved product without nutrition: "
                        + product.getSearchableId() + " (favorite: " + asFavorite + ")");
            }

        } catch (Exception e) {
            Log.e(TAG, "Failed to save product to database", e);
        }
    }

    /**
     * Run task on main thread
     */
    private void runOnMainThread(Runnable runnable) {
        new android.os.Handler(android.os.Looper.getMainLooper()).post(runnable);
    }

    /**
     * Cache-first product load.
     *
     *  - Room hit, fresh : return the cached row immediately, no network.
     *  - Room hit, stale : return the cached row immediately, then refresh in the
     *                      background; the fresh data applies on the NEXT open
     *                      (never changes values under the user mid-view).
     *  - Room miss        : fetch, save, then re-read the merged row so locally
     *                      preserved fields (image paths, etc.) reach the UI.
     *
     * Replaces the old split where loadProductFromSource() never consulted Room -
     * the source path now caches like the barcode path, fixing image persistence.
     */
    private void resolveProduct(@NonNull String roomKey,
                                @NonNull NetworkFetch fetch,
                                @NonNull CacheStrategy strategy,
                                @NonNull ProductCallback callback) {
        callback.onLoading();
        isOperationInProgress = true;

        backgroundExecutor.execute(() -> {
            try {
                FoodProductWithNutrition cached = database.combinedProductDao()
                        .getProductWithNutrition(roomKey);

                if (cached != null && cached.product != null) {
                    // Record the view (accessCount + lastViewed) and persist it.
                    cached.product.recordAccess();
                    database.foodProductDao().updateProduct(cached.product);

                    // Staleness comes from the source strategy + row overrides.
                    boolean stale = isStale(cached.product.isLocalImport(),
                            cached.product.isFavorite(),
                            cached.product.getLastUpdated(), strategy);

                    // Always show the cached version immediately.
                    FoodProduct product = cached.toFoodProduct();
                    isOperationInProgress = false;
                    runOnMainThread(() -> callback.onSuccess(product));

                    // If stale, refresh quietly for next open - no live push.
                    if (stale) {
                        backgroundRefresh(roomKey, fetch, product, callback);
                    }
                    return;
                }

                // Room miss → fetch, save, re-read, push.
                fetchSaveAndPush(fetch, callback);

            } catch (Exception e) {
                Log.e(TAG, "resolveProduct error for " + roomKey, e);
                // DB error: fall back to a straight network fetch (treat as a miss).
                fetchSaveAndPush(fetch, callback);
            }
        });
    }

    /**
     * Combined staleness: the source CacheStrategy plus row-level overrides.
     *  - localImport rows (bulk dataset members) never auto-refresh.
     *  - favourites stay fresh for at least the favourite floor.
     */
    private boolean isStale(boolean localImport, boolean favorite,
                            long lastUpdatedMs, CacheStrategy strategy) {
        if (localImport || strategy.isNeverStale()) return false;
        long ttl = favorite
                ? Math.max(strategy.getStaleAfterMs(), FAVORITE_CACHE_VALIDITY_MS)
                : strategy.getStaleAfterMs();
        return (System.currentTimeMillis() - lastUpdatedMs) > ttl;
    }

    /** Network fetch → synchronous save → re-read merged row → push to caller. */
    private void fetchSaveAndPush(@NonNull NetworkFetch fetch,
                                  @NonNull ProductCallback callback) {
        fetch.fetch(new FetchCallback() {
            @Override public void onSuccess(FoodProduct fetched) {
                backgroundExecutor.execute(() -> {
                    saveProductToDatabaseSync(fetched, false);
                    FoodProduct result = reReadOrFallback(fetched);
                    isOperationInProgress = false;
                    runOnMainThread(() -> callback.onSuccess(result));
                });
            }
            @Override public void onError(Error error) {
                isOperationInProgress = false;
                runOnMainThread(() -> callback.onError(error));
            }
        });
    }

    /**
     * Staleness refresh: fetch the live version and compare it against what the user
     * is currently viewing.
     *  - Unchanged upstream → re-save to reset the freshness clock (lastUpdated)
     *    so we don't refetch on every open. No UI signal, no visible change.
     *  - Changed → hand the candidate to the screen via onRefreshAvailable(); it is
     *    deliberately NOT saved here. The user applies it through the refresh FAB
     *    (applyCandidate), so values never change under them mid-view.
     */
    private void backgroundRefresh(@NonNull String roomKey, @NonNull NetworkFetch fetch,
                                   @NonNull FoodProduct baseline, @NonNull ProductCallback callback) {
        fetch.fetch(new FetchCallback() {
            @Override public void onSuccess(FoodProduct fetched) {
                backgroundExecutor.execute(() -> {
                    if (baseline.contentEquals(fetched)) {
                        // No real change - just re-stamp freshness (preserves local fields).
                        saveProductToDatabaseSync(fetched, false);
                    } else {
                        // Real change - offer it; do not apply silently.
                        runOnMainThread(() -> callback.onRefreshAvailable(fetched));
                    }
                });
            }
            @Override public void onError(Error error) {
                // Silent - the user already has the still-valid cached version.
                if (ApiConfig.DEBUG_LOGGING) {
                    Log.d(TAG, "Background refresh failed for " + roomKey + ": " + error.getMessage());
                }
            }
        });
    }

    /**
     * Apply a candidate previously offered via onRefreshAvailable(): save it
     * (preserving local fields), re-read the merged row, and push it back so the
     * screen re-renders. Invoked by the refresh FAB.
     */
    public void applyCandidate(@NonNull FoodProduct candidate, @NonNull ProductCallback callback) {
        backgroundExecutor.execute(() -> {
            saveProductToDatabaseSync(candidate, false);
            FoodProduct result = reReadOrFallback(candidate);
            runOnMainThread(() -> callback.onSuccess(result));
        });
    }

    /**
     * Re-reads the just-saved row so fields merged during save (local image paths)
     * reach the UI. Falls back to the raw fetched object if the re-read fails.
     * Must run on a background thread.
     */
    @WorkerThread
    private FoodProduct reReadOrFallback(@NonNull FoodProduct fetched) {
        FoodProductWithNutrition saved = database.combinedProductDao()
                .getProductWithNutrition(fetched.getSearchableId());
        return (saved != null && saved.product != null) ? saved.toFoodProduct() : fetched;
    }

    /** Maps a NetworkManager failure string to a structured Error. */
    private Error mapNetworkError(@NonNull String error, @NonNull String barcode) {
        if (error.toLowerCase().contains("not found")) {
            return Error.noData("No product found for barcode: " + barcode);
        } else if (error.toLowerCase().contains("network")) {
            return Error.network(error, null);
        }
        return Error.network("Failed to load product: " + error, null);
    }
}