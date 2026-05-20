package li.masciul.sugardaddi.data.repository;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;

// Database imports
import li.masciul.sugardaddi.core.interfaces.Searchable;
import li.masciul.sugardaddi.core.models.Error;
import li.masciul.sugardaddi.data.database.entities.FoodProductEntity;
import li.masciul.sugardaddi.data.database.entities.NutritionEntity;
import li.masciul.sugardaddi.data.database.relations.FoodProductWithNutrition;
import li.masciul.sugardaddi.data.database.AppDatabase;

// Network imports
import li.masciul.sugardaddi.data.network.NetworkManager;
import li.masciul.sugardaddi.data.network.ApiConfig;

// DataSource imports
import li.masciul.sugardaddi.data.sources.base.DataSource;
import li.masciul.sugardaddi.data.sources.base.DataSourceCallback;

// Core models
import li.masciul.sugardaddi.core.models.FoodProduct;

// Managers and utilities
import li.masciul.sugardaddi.managers.LanguageManager;
import li.masciul.sugardaddi.managers.DataSourceManager;

// Cache

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

/**
 * ProductRepository - CONSOLIDATED DATA ACCESS LAYER
 *
 * *** REPOSITORY CONSOLIDATION v3.0 ***
 * This repository combines the functionality of the former FoodRepository and ProductRepository
 * into a single, comprehensive data access layer for all product operations.
 *
 * DUAL FUNCTIONALITY:
 * 1. SEARCH OPERATIONS (from FoodRepository):
 *    - Language-aware search with DataSource support
 *    - Multi-source search aggregation
 *    - Search caching and filtering
 *    - Advanced pagination and progress tracking
 *
 * 2. PRODUCT MANAGEMENT (from original ProductRepository):
 *    - Dual-table database storage (FoodProductEntity + NutritionEntity)
 *    - Favorite product management
 *    - Intelligent cache/network strategy with TTL
 *    - Background database operations
 *    - Lifecycle management and resource cleanup
 *
 * ARCHITECTURE BENEFITS:
 * - Single source of truth for all product operations
 * - Consistent threading model across all operations
 * - Unified error handling and logging
 * - Simplified dependency injection
 * - Better resource management
 *
 * MIGRATION NOTES:
 * - Replaces both FoodRepository and the old ProductRepository
 * - All existing interfaces preserved for backward compatibility
 * - SearchManager, SearchRepository, and UI components need minimal changes
 * - Database functionality enhanced with search capabilities
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
    // Database cache settings
    private static final long DEFAULT_CACHE_VALIDITY_MS = 24 * 60 * 60 * 1000; // 24 hours
    private static final long FAVORITE_CACHE_VALIDITY_MS = 7 * 24 * 60 * 60 * 1000; // 7 days for favorites
    private long cacheValidityMs = DEFAULT_CACHE_VALIDITY_MS;

    // ========== STATE TRACKING ==========
    private boolean isOperationInProgress = false;

    // ========== CALLBACK INTERFACES ==========

    /**
     * Called with search results. Items are FoodProduct for food sources,
     * Recipe for recipe sources (TheMealDB). Use instanceof to discriminate.
     */
    public interface SearchCallback {
        void onSuccess(List<Searchable> items);
        void onError(Error error);
        void onLoading();
    }

    /**
     * Callback interface for individual product operations (from ProductRepository)
     */
    public interface ProductCallback {
        void onSuccess(FoodProduct product);
        void onError(Error error);
        void onLoading();
    }

    /**
     * Callback interface for favorite operations (from ProductRepository)
     */
    public interface FavoriteCallback {
        void onFavoriteStatus(boolean isFavorite);
        void onFavoriteToggled(boolean newStatus);
        void onError(String message);
    }

    /**
     * Callback interface for batch operations (from ProductRepository)
     */
    public interface BatchCallback {
        void onComplete(int successCount, int failureCount);
        void onProgress(int current, int total);
        void onError(String message);
    }

    /**
     * Cache statistics callback (from ProductRepository)
     */
    public interface CacheStatsCallback {
        void onStats(CacheStatistics stats);
        void onError(String message);
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

    /**
     * Load product details with intelligent cache/network strategy
     */
    public void loadProduct(String barcode, ProductCallback callback) {
        if (barcode == null || barcode.trim().isEmpty()) {
            callback.onError(Error.invalidRequest("Invalid barcode provided", null));
            return;
        }

        final String cleanBarcode = barcode.trim();
        callback.onLoading();
        isOperationInProgress = true;

        backgroundExecutor.execute(() -> {
            try {
                // Try to load from cache first (joining both tables)
                FoodProductWithNutrition cached = database.combinedProductDao()
                        .getProductWithNutrition(cleanBarcode);

                if (cached != null && cached.product != null) {
                    // Update access tracking
                    cached.product.recordAccess();
                    database.foodProductDao().updateProduct(cached.product);

                    // Check cache freshness
                    long cacheAge = System.currentTimeMillis() - cached.product.getLastUpdated();
                    long maxAge = cached.product.isFavorite() ?
                            FAVORITE_CACHE_VALIDITY_MS : cacheValidityMs;

                    if (cacheAge < maxAge) {
                        // Cache is fresh - use it
                        FoodProduct product = cached.toFoodProduct();

                        if (ApiConfig.DEBUG_LOGGING) {
                            Log.d(TAG, "Loaded product from database cache: " + cleanBarcode +
                                    " (age: " + (cacheAge / 1000) + "s)");
                        }

                        isOperationInProgress = false;
                        runOnMainThread(() -> callback.onSuccess(product));
                        return;
                    } else {
                        if (ApiConfig.DEBUG_LOGGING) {
                            Log.d(TAG, "Database cache stale for: " + cleanBarcode +
                                    " (age: " + (cacheAge / 1000) + "s)");
                        }
                    }
                }

                // Cache miss or stale - fetch from network
                runOnMainThread(() -> fetchFromNetwork(cleanBarcode, callback));

            } catch (Exception e) {
                Log.e(TAG, "Database error loading product", e);
                isOperationInProgress = false;
                // Fall back to network on database error
                runOnMainThread(() -> fetchFromNetwork(cleanBarcode, callback));
            }
        });
    }

    /**
     * Load product from specific data source using source-specific ID
     *
     * This method enables loading products that use source-specific identifiers
     * rather than standard barcodes. For example:
     * - Ciqual products: Use internal IDs like "31020"
     * - Custom sources: May use their own ID systems
     *
     * PROCESS:
     * 1. Get the specified DataSource from DataSourceManager
     * 2. Validate the source exists and is available
     * 3. Call source.getProduct() with the product ID
     * 4. Save successful results to database
     * 5. Return product via callback
     *
     * IMPORTANT NOTES:
     * - Does NOT use database cache (source-specific IDs may not be in DB)
     * - Always fetches fresh from the specified source
     * - Saves to database after successful fetch for future barcode lookups
     *
     * @param sourceId Data source identifier (e.g., "CIQUAL", "OPENFOODFACTS")
     * @param productId Source-specific product ID (e.g., "31020" for Ciqual)
     * @param callback Product callback for results
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

        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, String.format("Loading product from source: %s, ID: %s",
                    cleanSourceId, cleanProductId));
        }

        callback.onLoading();
        isOperationInProgress = true;

        // Get the specified data source
        DataSource source = dataSourceManager.getDataSource(cleanSourceId);

        if (source == null) {
            String errorMsg = String.format("Data source '%s' not found or not registered", cleanSourceId);
            Log.e(TAG, errorMsg);
            isOperationInProgress = false;
            callback.onError(Error.validation(errorMsg, "Available sources: " +
                    dataSourceManager.getAllDataSources().size()));
            return;
        }

        // Check if source is available and initialized
        if (!source.isAvailable()) {
            String errorMsg = String.format("Data source '%s' is not available or not initialized",
                    cleanSourceId);
            Log.w(TAG, errorMsg);
            isOperationInProgress = false;
            callback.onError(Error.validation(errorMsg,
                    "Source may still be initializing. Try again in a moment."));
            return;
        }

        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, String.format("Found available source: %s (%s)",
                    source.getSourceName(), cleanSourceId));
        }

        // Get current language for the request
        String language = LanguageManager.getCurrentLanguage(context).getCode();

        // Call the source's getProduct method
        source.getProduct(cleanProductId, language, new DataSourceCallback<FoodProduct>() {
            @Override
            public void onSuccess(FoodProduct product) {
                if (ApiConfig.DEBUG_LOGGING) {
                    Log.d(TAG, String.format("Successfully loaded product from %s: %s",
                            cleanSourceId, product.getName()));
                }

                // Save to database for future lookups
                // (if product has a barcode, it can be found later via barcode search)
                saveProductToDatabase(product, false);

                isOperationInProgress = false;
                callback.onSuccess(product);
            }

            @Override
            public void onError(Error error) {
                Log.w(TAG, String.format("Failed to load product from %s: %s",
                        cleanSourceId, error.getMessage()));

                isOperationInProgress = false;
                callback.onError(error);
            }

            @Override
            public void onLoading() {
                // Already handled by callback.onLoading() above
            }
        });
    }

    /**
     * Get product by barcode with DataSource support
     */
    public void getProductByBarcode(String barcode, ProductCallback callback) {
        if (callback == null || barcode == null || barcode.trim().isEmpty()) {
            if (callback != null) {
                callback.onError(Error.network("Invalid barcode", null));
            }
            return;
        }

        callback.onLoading();

        List<DataSource> activeSources = dataSourceManager.getActiveSources();
        if (activeSources.isEmpty()) {
            callback.onError(Error.network("No data sources available", null));
            return;
        }
        DataSource primarySource = activeSources.get(0);

        String language = LanguageManager.getCurrentLanguage(context).getCode();
        primarySource.getProductByBarcode(barcode, language,
            new DataSourceCallback<FoodProduct>() {
                @Override
                public void onSuccess(FoodProduct foodProduct) {
                    // Save to database as well
                    saveProductToDatabase(foodProduct, false);
                    callback.onSuccess(foodProduct);
                }

                @Override
                public void onError(Error error) {
                    callback.onError(error);
                }

                @Override
                public void onLoading() {
                    // Already handled
                }
            }
        );
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

    // ========== FAVORITE MANAGEMENT (from ProductRepository) ==========

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
     * Toggle favorite status for a product
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

                if (entity != null) {
                    // Toggle existing product
                    boolean newStatus = !entity.isFavorite();
                    entity.setFavorite(newStatus);
                    entity.setUpdatedAt(System.currentTimeMillis());
                    database.foodProductDao().updateProduct(entity);

                    if (ApiConfig.DEBUG_LOGGING) {
                        Log.d(TAG, "Toggled favorite: " + productId + " -> " + newStatus);
                    }

                    runOnMainThread(() -> callback.onFavoriteToggled(newStatus));

                } else {
                    // Product not in database - save it as favorite
                    saveProductToDatabase(product, true);

                    if (ApiConfig.DEBUG_LOGGING) {
                        Log.d(TAG, "Saved new favorite: " + productId);
                    }

                    runOnMainThread(() -> callback.onFavoriteToggled(true));
                }

            } catch (Exception e) {
                Log.e(TAG, "Database error toggling favorite", e);
                runOnMainThread(() -> callback.onError("Could not update favorite status"));
            }
        });
    }

    /**
     * Get all favorite products
     */
    public LiveData<List<FoodProductWithNutrition>> getFavoriteProducts() {
        return database.combinedProductDao().getFavoriteProductsWithNutrition();
    }

    // ========== DATASOURCE MANAGEMENT ==========

    /**
     * Get available data sources
     */
    public List<DataSource> getAvailableDataSources() {
        return dataSourceManager.getActiveSources();
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

    /**
     * Set database cache validity duration
     */
    public void setCacheValidity(long milliseconds) {
        this.cacheValidityMs = milliseconds;

        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, "Database cache validity set to: " + (milliseconds / 1000) + " seconds");
        }
    }

    // ========== BATCH OPERATIONS ==========

    /**
     * Load multiple products efficiently
     */
    public void loadProducts(List<String> barcodes, BatchCallback callback) {
        if (barcodes == null || barcodes.isEmpty()) {
            callback.onComplete(0, 0);
            return;
        }

        backgroundExecutor.execute(() -> {
            int successCount = 0;
            int failureCount = 0;
            int total = barcodes.size();

            for (int i = 0; i < barcodes.size(); i++) {
                String barcode = barcodes.get(i);

                try {
                    // Try cache first
                    FoodProductWithNutrition cached = database.combinedProductDao()
                            .getProductWithNutrition(barcode);

                    if (cached != null && cached.product != null &&
                            !cached.product.isStale(cacheValidityMs)) {
                        successCount++;
                    } else {
                        // Would need network fetch - count as pending
                        failureCount++;
                    }

                    // Report progress
                    final int current = i + 1;
                    runOnMainThread(() -> callback.onProgress(current, total));

                } catch (Exception e) {
                    Log.e(TAG, "Error loading product: " + barcode, e);
                    failureCount++;
                }
            }

            final int finalSuccess = successCount;
            final int finalFailure = failureCount;
            runOnMainThread(() -> callback.onComplete(finalSuccess, finalFailure));
        });
    }

    /**
     * Get cache statistics
     */
    public void getCacheStatistics(CacheStatsCallback callback) {
        backgroundExecutor.execute(() -> {
            try {
                int productCount = database.foodProductDao().getProductCount();
                int nutritionCount = database.nutritionDao().getNutritionCount();
                int favoriteCount = database.foodProductDao().getFavoriteCount();

                CacheStatistics stats = new CacheStatistics(
                        productCount, nutritionCount, favoriteCount);

                runOnMainThread(() -> callback.onStats(stats));

            } catch (Exception e) {
                Log.e(TAG, "Error getting cache statistics", e);
                runOnMainThread(() -> callback.onError("Could not get cache statistics"));
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

    private void saveProductToDatabase(FoodProduct product, boolean asFavorite) {
        if (product == null) return;

        backgroundExecutor.execute(() -> {
            try {
                // 1. Check if product already exists and preserve favorite status
                FoodProductEntity existingEntity = database.foodProductDao()
                        .getProductById(product.getSearchableId());
                boolean preserveFavorite = (existingEntity != null && existingEntity.isFavorite());

                // 2. Prepare and save product entity (WITHOUT nutrition)
                FoodProductEntity productEntity = FoodProductEntity.fromFoodProduct(product);
                if (asFavorite || preserveFavorite) {
                    productEntity.setFavorite(true);
                }
                productEntity.markAsUpdated();

                database.foodProductDao().insertProduct(productEntity);

                // 2. Save nutrition separately if present
                if (product.getNutrition() != null) {
                    NutritionEntity nutritionEntity = NutritionEntity.fromNutrition(
                            product.getNutrition(),
                            "product",
                            product.getSearchableId()
                    );
                    database.nutritionDao().insertNutrition(nutritionEntity);

                    if (ApiConfig.DEBUG_LOGGING) {
                        Log.d(TAG, "Saved product with nutrition to both tables: " +
                                product.getSearchableId() + " (favorite: " + asFavorite + ")");
                    }
                } else {
                    if (ApiConfig.DEBUG_LOGGING) {
                        Log.d(TAG, "Saved product without nutrition: " +
                                product.getSearchableId() + " (favorite: " + asFavorite + ")");
                    }
                }

            } catch (Exception e) {
                Log.e(TAG, "Failed to save product to database", e);
            }
        });
    }

    /**
     * Get current language for API requests
     */
    private String getCurrentLanguageCode() {
        try {
            // Read directly from SharedPreferences for consistency
            SharedPreferences prefs = context.getSharedPreferences("language_pref", Context.MODE_PRIVATE);
            String savedLanguage = prefs.getString("selected_language", null);

            if (savedLanguage != null) {
                if (ApiConfig.DEBUG_LOGGING) Log.d(TAG, "Language from prefs: " + savedLanguage);
                return savedLanguage;
            }

            // Fallback to LanguageManager
            LanguageManager.SupportedLanguage currentLang = LanguageManager.getCurrentLanguage(context);
            if (ApiConfig.DEBUG_LOGGING) Log.d(TAG, "Language from manager: " + currentLang.getCode());
            return currentLang.getCode();
        } catch (Exception e) {
            Log.e(TAG, "Failed to get language code, using default", e);
            return "en"; // Safe fallback
        }
    }

    /**
     * Run task on main thread
     */
    private void runOnMainThread(Runnable runnable) {
        new android.os.Handler(android.os.Looper.getMainLooper()).post(runnable);
    }

    // ========== INNER CLASSES ==========

    /**
     * Cache statistics
     */
    public static class CacheStatistics {
        public final int productCount;
        public final int nutritionCount;
        public final int favoriteCount;

        CacheStatistics(int productCount, int nutritionCount, int favoriteCount) {
            this.productCount = productCount;
            this.nutritionCount = nutritionCount;
            this.favoriteCount = favoriteCount;
        }
    }
}