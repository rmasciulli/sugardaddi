package li.masciul.sugardaddi.data.repository;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import li.masciul.sugardaddi.SugarDaddiApplication;
import li.masciul.sugardaddi.core.models.Error;
import li.masciul.sugardaddi.core.models.Recipe;
import li.masciul.sugardaddi.core.models.SourceIdentifier;
import li.masciul.sugardaddi.data.database.AppDatabase;
import li.masciul.sugardaddi.data.database.dao.RecipeDao;
import li.masciul.sugardaddi.data.database.entities.RecipeEntity;
import li.masciul.sugardaddi.data.network.ApiConfig;
import li.masciul.sugardaddi.data.sources.base.CacheStrategy;
import li.masciul.sugardaddi.data.sources.base.DataSource;
import li.masciul.sugardaddi.data.sources.base.DataSourceCallback;
import li.masciul.sugardaddi.managers.DataSourceManager;
import li.masciul.sugardaddi.utils.image.ImageDownloader;
import li.masciul.sugardaddi.utils.image.ImageProfile;
import li.masciul.sugardaddi.utils.image.ImageStorageManager;

import java.io.File;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * RecipeRepository - Complete recipe data management
 *
 * Handles all recipe-related operations:
 * - CRUD operations for user recipes
 * - Recipe search and filtering
 * - Ingredient management
 * - Nutrition calculation from ingredients
 * - Recipe sharing and templates
 * - Import/export functionality
 */
public class RecipeRepository {

    private static final String TAG = "RecipeRepository";

    // ========== CORE DEPENDENCIES ==========

    private final Context context;
    private final AppDatabase database;
    private final RecipeDao recipeDao;
    private final Executor backgroundExecutor;

    // ========== SEARCH INFRASTRUCTURE ==========

    /**
     * Shared DataSourceManager singleton - same instance used by ProductRepository.
     * Recipe detail fetches call source.getRecipe() on any registered DataSource,
     * exactly as ProductRepository calls source.getProduct().
     */
    private final DataSourceManager dataSourceManager;

    // ========== CALLBACK INTERFACES ==========

    /**
     * Callback interfaces
     */
    public interface RecipeCallback {
        void onSuccess(Recipe recipe);
        void onError(String error);

        /**
         * A stale-triggered background refresh found CHANGED upstream content.
         * The candidate is offered, not applied - the detail screen shows the
         * refresh FAB and applies it via applyCandidate() on tap. Default no-op:
         * callers that don't show a detail screen ignore it.
         */
        default void onRefreshAvailable(Recipe candidate) {}
    }

    public interface RecipeOperationCallback {
        void onSuccess();
        void onError(String error);
    }

    // ========== CONSTRUCTOR ==========

    public RecipeRepository(Context context) {
        this.context = context.getApplicationContext();
        this.database = AppDatabase.getInstance(context);
        this.recipeDao = database.recipeDao();
        this.backgroundExecutor = Executors.newSingleThreadExecutor();
        // Use the shared DataSourceManager singleton - sources are already initialised
        // at app startup. No separate initialisation needed here.
        this.dataSourceManager = DataSourceManager.getInstance(this.context);

        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, "RecipeRepository initialized");
        }
    }

    // ========== CRUD OPERATIONS ==========

    /**
     * Load a recipe by its source-qualified searchable ID.
     *
     * PRIMARY ENTRY POINT for RecipeDetailsActivity. Accepts the "SOURCE:id"
     * format returned by {@link Recipe#getSearchableId()} and routes to the
     * correct backend - identical pattern to
     * {@link ProductRepository#loadProductFromSource}:
     *
     *   "USER:some-uuid"   → Room lookup (user-created recipe)
     *   "THEMEALDB:52772"  → dataSourceManager.getDataSource("THEMEALDB").getRecipe(...)
     *   "FUTURE_SOURCE:x"  → dataSourceManager.getDataSource("FUTURE_SOURCE").getRecipe(...)
     *
     * ADDING A NEW RECIPE SOURCE
     * ==========================
     * 1. Override getRecipe() in the new DataSource implementation
     * 2. Register it in DataSourceManager.initializeDataSources()
     * Zero changes needed here.
     *
     * @param searchableId  Source-qualified ID (e.g. "USER:abc", "THEMEALDB:52772")
     * @param callback      Called on the main thread with the loaded Recipe or an error
     */
    public void getRecipeBySearchableId(@NonNull String searchableId,
                                        @NonNull RecipeCallback callback) {
        SourceIdentifier identifier = SourceIdentifier.fromCombinedId(searchableId);

        if (identifier == null || !identifier.isValid()) {
            callback.onError("Invalid recipe identifier: " + searchableId);
            return;
        }

        String sourceId   = identifier.getSourceId();
        String originalId = identifier.getOriginalId();

        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, "Loading recipe: source=" + sourceId + " originalId=" + originalId);
        }

        // Recipes are external-only (no user-authored recipes), so every id is
        // "SOURCE:originalId" - resolve the source and load cache-first.
        DataSource source = dataSourceManager.getDataSource(sourceId);

        if (source == null) {
            Log.e(TAG, "No data source registered for: " + sourceId);
            callback.onError("Unknown recipe source: " + sourceId);
            return;
        }

        if (!source.isAvailable()) {
            Log.w(TAG, "Data source not yet available: " + sourceId);
            callback.onError("Data source not ready: " + sourceId
                    + " - it may still be initialising. Please retry.");
            return;
        }

        String language = li.masciul.sugardaddi.managers.LanguageManager
                .getCurrentLanguage(context).getCode();

        resolveExternalRecipe(sourceId, originalId, source, language, callback);
    }

    // ========== EXTERNAL RECIPE PERSISTENCE ==========

    /**
     * Persist an externally-sourced recipe to Room (fire-and-forget + callback).
     *
     * For toggling favourites on an already-cached recipe use setRecipeFavorite(),
     * which preserves user-set fields instead of replacing the row.
     */
    public void saveExternalRecipe(@NonNull Recipe recipe, @NonNull RecipeCallback callback) {
        backgroundExecutor.execute(() -> {
            try {
                saveExternalRecipeSync(recipe);
                runOnMainThread(() -> {
                    if (ApiConfig.DEBUG_LOGGING) {
                        Log.d(TAG, "External recipe cached: "
                                + recipe.getDataSource().getId() + ":" + recipe.getOriginalId());
                    }
                    callback.onSuccess(recipe);
                });
            } catch (Exception e) {
                Log.e(TAG, "Error caching external recipe", e);
                runOnMainThread(() -> callback.onError("Failed to cache recipe: " + e.getMessage()));
            }
        });
    }

    /**
     * Synchronous save of an external recipe. MUST run on a background thread.
     * Returns after the Room write so the caller can re-read the merged row.
     *
     * Preserved from any existing row: the four local image paths, isFavorite,
     * localImport, lastViewed, accessCount. No nutrition (external recipes have none yet).
     */
    @WorkerThread
    private void saveExternalRecipeSync(@NonNull Recipe recipe) {
        recipe.calculateCompleteness();
        recipe.setLastUpdated(System.currentTimeMillis());
        if (recipe.getCreatedAt() == 0) {
            recipe.setCreatedAt(System.currentTimeMillis());
        }

        RecipeEntity entity = RecipeEntity.fromRecipe(recipe);
        RecipeEntity existingEntity = recipeDao.getById(recipe.getSearchableId());

        if (existingEntity != null) {
            // Local image paths - the API response has no knowledge of local files.
            if (existingEntity.getThumbnailPath() != null) {
                entity.setThumbnailPath(existingEntity.getThumbnailPath());
            }
            if (existingEntity.getImagePath() != null) {
                entity.setImagePath(existingEntity.getImagePath());
            }
            if (existingEntity.getUserThumbnailPath() != null) {
                entity.setUserThumbnailPath(existingEntity.getUserThumbnailPath());
            }
            if (existingEntity.getUserImagePath() != null) {
                entity.setUserImagePath(existingEntity.getUserImagePath());
            }

            // Favourite: this runs on every detail open, so never unfavourite.
            if (existingEntity.isFavorite()) {
                entity.setFavorite(true);
            }

            // View-time state - mirror of the product side.
            entity.setLocalImport(existingEntity.isLocalImport());
            entity.setLastViewed(existingEntity.getLastViewed());
            entity.setAccessCount(existingEntity.getAccessCount());
        }

        recipeDao.insert(entity);   // REPLACE on conflict

        // Heal-on-open: keep a favourite's thumbnail + hero cached for offline use.
        if (entity.isFavorite()) {
            cacheFavoriteImages(recipe, recipe.getSearchableId(),
                    entity.getThumbnailPath(), entity.getImagePath());
        }
    }

    // ── Cache-first external-recipe resolver (mirror of ProductRepository.resolveProduct) ──

    /**
     * Cache-first load of an external recipe.
     *  - Room hit, fresh : return the cached recipe immediately.
     *  - Room hit, stale : return cached immediately, refresh quietly for next open.
     *  - Room miss        : fetch, save, re-read the merged row, return it.
     *
     * Goes Room-first (no memory-cache short-circuit) so staleness and view-time
     * recording always run; cacheRecipe() still populates the in-memory cache.
     */
    private void resolveExternalRecipe(@NonNull String sourceId, @NonNull String originalId,
                                       @NonNull DataSource source, @NonNull String language,
                                       @NonNull RecipeCallback callback) {
        backgroundExecutor.execute(() -> {
            try {
                RecipeEntity cached = recipeDao.getBySourceAndOriginalId(sourceId, originalId);

                if (cached != null) {
                    cached.recordAccess();  // accessCount + lastViewed
                    recipeDao.update(cached);

                    boolean stale = isStale(cached.getLastUpdated(), source.getCacheStrategy());

                    Recipe recipe = cached.toRecipe();
                    runOnMainThread(() -> callback.onSuccess(recipe));

                    if (stale) {
                        backgroundRefreshRecipe(source, originalId, language, recipe, callback);

                    }
                    return;
                }

                fetchSaveAndPushRecipe(source, originalId, language, callback);

            } catch (Exception e) {
                Log.e(TAG, "resolveExternalRecipe error for " + sourceId + ":" + originalId, e);
                fetchSaveAndPushRecipe(source, originalId, language, callback);
            }
        });
    }

    /** Stale once older than the source's freshness window. Refresh is independent
     *  of favourite/localImport - those only affect eviction. */
    private boolean isStale(long lastUpdatedMs, CacheStrategy strategy) {
        if (strategy.isNeverStale()) return false;
        return (System.currentTimeMillis() - lastUpdatedMs) > strategy.getStaleAfterMs();
    }
    
    /** Network fetch → synchronous save → re-read merged row → push to caller. */
    private void fetchSaveAndPushRecipe(@NonNull DataSource source, @NonNull String originalId,
                                        @NonNull String language, @NonNull RecipeCallback callback) {
        source.getRecipe(originalId, language, new DataSourceCallback<Recipe>() {
            @Override public void onSuccess(Recipe recipe) {
                backgroundExecutor.execute(() -> {
                    saveExternalRecipeSync(recipe);
                    Recipe result = reReadRecipeOrFallback(recipe);
                    runOnMainThread(() -> callback.onSuccess(result));
                });
            }
            @Override public void onError(Error error) {
                runOnMainThread(() -> callback.onError(error.getMessage()));
            }
            @Override public void onLoading() {}
        });
    }

    /**
     * Staleness refresh: fetch the live recipe and compare it against what the
     * user is viewing.
     *  - Unchanged upstream → re-save to reset the freshness clock (lastUpdated).
     *    No UI signal, no visible change.
     *  - Changed → hand the candidate to the screen via onRefreshAvailable(); it
     *    is NOT saved here. The user applies it through the refresh FAB
     *    (applyCandidate), so the recipe never changes under them mid-view.
     */
    private void backgroundRefreshRecipe(@NonNull DataSource source, @NonNull String originalId,
                                         @NonNull String language,
                                         @NonNull Recipe baseline, @NonNull RecipeCallback callback) {
        source.getRecipe(originalId, language, new DataSourceCallback<Recipe>() {
            @Override public void onSuccess(Recipe fetched) {
                backgroundExecutor.execute(() -> {
                    if (baseline.contentEquals(fetched)) {
                        // No real change - just re-stamp freshness (preserves local fields).
                        saveExternalRecipeSync(fetched);
                    } else {
                        // Real change - offer it; do not apply silently.
                        runOnMainThread(() -> callback.onRefreshAvailable(fetched));
                    }
                });
            }
            @Override public void onError(Error error) {
                if (ApiConfig.DEBUG_LOGGING) {
                    Log.d(TAG, "Background recipe refresh failed: " + error.getMessage());
                }
            }
            @Override public void onLoading() {}
        });
    }

    /**
     * Apply a candidate previously offered via onRefreshAvailable(): save it
     * (preserving local fields), re-read the merged row, and push it back so the
     * screen re-renders. Invoked by the refresh FAB.
     */
    public void applyCandidate(@NonNull Recipe candidate, @NonNull RecipeCallback callback) {
        backgroundExecutor.execute(() -> {
            saveExternalRecipeSync(candidate);
            Recipe result = reReadRecipeOrFallback(candidate);
            runOnMainThread(() -> callback.onSuccess(result));
        });
    }

    /** Re-read the just-saved row so merged local image paths reach the UI. */
    @WorkerThread
    private Recipe reReadRecipeOrFallback(@NonNull Recipe fetched) {
        RecipeEntity saved = recipeDao.getById(fetched.getSearchableId());
        return (saved != null) ? saved.toRecipe() : fetched;
    }

    public void readFromCache(@NonNull String searchableId, @NonNull RecipeCallback callback) {
        backgroundExecutor.execute(() -> {
            RecipeEntity cached = recipeDao.getById(searchableId);
            if (cached != null) {
                Recipe recipe = cached.toRecipe();
                runOnMainThread(() -> callback.onSuccess(recipe));
            } else {
                runOnMainThread(() -> callback.onError("Recipe not in cache: " + searchableId));
            }
        });
    }

    /**
     * Toggle the favourite flag on a recipe, persisting the change to Room.
     *
     * If the recipe is not yet cached (first interaction) it is saved in full
     * via saveExternalRecipe(); if already cached, only the favourite flag and
     * timestamp are updated, and on unfavourite the cached thumbnail is cleared.
     *
     * @param recipe   The recipe to favourite/unfavourite.
     * @param favorite True to favourite, false to unfavourite.
     * @param callback Operation result - called on the main thread.
     */
    public void setRecipeFavorite(@NonNull Recipe recipe,
                                  boolean favorite,
                                  @NonNull RecipeOperationCallback callback) {
        recipe.setFavorite(favorite);
        recipe.touch();

        final String sourceId   = recipe.getDataSource().getId();
        final String originalId = recipe.getOriginalId();

        backgroundExecutor.execute(() -> {
            try {
                RecipeEntity existing = (sourceId != null && originalId != null)
                        ? recipeDao.getBySourceAndOriginalId(sourceId, originalId)
                        : null;

                if (existing != null) {
                    // Already in Room - update only the favourite flag and timestamp
                    existing.setFavorite(favorite);
                    existing.touch();
                    recipeDao.update(existing);

                    if (!favorite) {
                        // Clear both cached-image paths before the files are deleted.
                        existing.setThumbnailPath(null);
                        existing.setImagePath(null);
                        recipeDao.update(existing); // second write to clear the paths
                    }
                    handleFavoriteImages(recipe, recipe.getSearchableId(), favorite);

                    runOnMainThread(callback::onSuccess);
                } else {
                    // First interaction - persist the full recipe, then return
                    runOnMainThread(() -> saveExternalRecipe(recipe, new RecipeCallback() {
                        @Override
                        public void onSuccess(Recipe saved) {
                            handleFavoriteImages(recipe, recipe.getSearchableId(), favorite);
                            callback.onSuccess();
                        }
                        @Override
                        public void onError(String error) {
                            callback.onError(error);
                        }
                    }));
                }

            } catch (Exception e) {
                Log.e(TAG, "Error updating favourite for recipe", e);
                runOnMainThread(() -> callback.onError(
                        "Failed to update favourite: " + e.getMessage()));
            }
        });
    }

    /**
     * Downloads the thumbnail on favouriting or deletes it on unfavouriting.
     *
     * FAVOURITING
     * ===========
     * For recipes, imageUrl is the only available image - it is used as both
     * the search card thumbnail and the detail view hero fallback.
     * Downloaded once to thumbnails/ and persisted as thumbnailPath.
     * userImagePath remains null until the user explicitly sets one.
     *
     * UNFAVOURITING
     * =============
     * Deletes the cached file from disk. thumbnailPath is cleared in Room
     * before this method is called (see setRecipeFavorite()).
     *
     * @param recipe     Recipe domain object (provides imageUrl).
     * @param recipeId   Source-qualified ID (Room primary key).
     * @param isFavorite The new favourite state just written to Room.
     */
    private void handleFavoriteImages(
            @NonNull Recipe recipe,
            @NonNull String recipeId,
            boolean isFavorite) {

        if (isFavorite) {
            cacheFavoriteImages(recipe, recipeId, null, null);
        } else {
            // Unfavourite: remove the cached thumbnail and hero from disk.
            // Both paths are already cleared in Room before this call.
            ImageDownloader downloader = getImageDownloader();
            ImageStorageManager storage = getImageStorageManager();
            if (downloader != null && storage != null) {
                downloader.delete(storage.getThumbnailFile(recipeId));
                downloader.delete(storage.getRecipeHeroFile(recipeId));
            }
        }
    }

    /**
     * Ensures a favourited recipe's thumbnail and hero are cached on disk, healing
     * any that are missing. See ProductRepository#cacheFavoriteImages - same
     * contract. Recipes expose a single image URL, used for both slots; the
     * downloader's dedup makes the overlap cheap when they resolve identically.
     *
     * @param recipe        Domain object providing the remote image URL.
     * @param recipeId      Source-qualified id (cache filename + Room key).
     * @param thumbnailPath Current cached thumbnail path, or null if not yet cached.
     * @param imagePath     Current cached hero path, or null if not yet cached.
     */
    private void cacheFavoriteImages(@NonNull Recipe recipe,
                                      @NonNull String recipeId,
                                      @Nullable String thumbnailPath,
                                      @Nullable String imagePath) {
        ImageDownloader downloader = getImageDownloader();
        ImageStorageManager storage = getImageStorageManager();
        if (downloader == null || storage == null) return;

        String url = recipe.getImageUrl();
        if (url == null || url.trim().isEmpty()) return;

        // --- Thumbnail (raw passthrough) ---
        File thumb = storage.getThumbnailFile(recipeId);
        if (thumb != null && (thumbnailPath == null || !thumb.exists())) {
            downloader.download(url, thumb, null, new ImageDownloader.Callback() {
                @Override
                public void onSuccess(@NonNull String localPath) {
                    backgroundExecutor.execute(() -> {
                        try {
                            recipeDao.updateThumbnailPath(recipeId, localPath);
                        } catch (Exception e) {
                            Log.w(TAG, "Failed to persist thumbnailPath for recipe "
                                    + recipeId, e);
                        }
                    });
                }
                @Override
                public void onError(@NonNull String reason) {
                    Log.d(TAG, "Thumbnail cache failed for recipe "
                            + recipeId + ": " + reason);
                }
            });
        }

        // --- Hero (resized/recompressed via ImageProfile.HERO) ---
        File hero = storage.getRecipeHeroFile(recipeId);
        if (hero != null && (imagePath == null || !hero.exists())) {
            downloader.download(url, hero, ImageProfile.HERO, new ImageDownloader.Callback() {
                @Override
                public void onSuccess(@NonNull String localPath) {
                    backgroundExecutor.execute(() -> {
                        try {
                            recipeDao.updateImagePath(recipeId, localPath);
                        } catch (Exception e) {
                            Log.w(TAG, "Failed to persist imagePath for recipe "
                                    + recipeId, e);
                        }
                    });
                }
                @Override
                public void onError(@NonNull String reason) {
                    Log.d(TAG, "Hero cache failed for recipe "
                            + recipeId + ": " + reason);
                }
            });
        }
    }

    /**
     * Returns the application-scoped ImageDownloader singleton.
     */
    @Nullable
    private ImageDownloader getImageDownloader() {
        if (context.getApplicationContext() instanceof SugarDaddiApplication) {
            return ((SugarDaddiApplication) context.getApplicationContext())
                    .getImageDownloader();
        }
        Log.e(TAG, "Application context is not SugarDaddiApplication "
                + "- thumbnail pipeline unavailable");
        return null;
    }

    /**
     * Returns the application-scoped ImageStorageManager singleton.
     * Used to resolve cache file paths (caller-owns-path) for ImageDownloader.
     */
    private ImageStorageManager getImageStorageManager() {
        if (context.getApplicationContext() instanceof SugarDaddiApplication) {
            return ((SugarDaddiApplication) context.getApplicationContext())
                    .getImageStorageManager();
        }
        Log.e(TAG, "Application context is not SugarDaddiApplication "
                + "- image storage unavailable");
        return null;
    }

    // ========== UTILITY METHODS ==========

    private void runOnMainThread(Runnable runnable) {
        android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        mainHandler.post(runnable);
    }

    /**
     * Cancel any in-progress network operations across all registered sources.
     * Mirrors DataSourceAggregator.cancelSearches().
     */
    public void cancelSearch() {
        for (DataSource source : dataSourceManager.getAllDataSources()) {
            source.cancelOperations();
        }
        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, "Recipe search cancelled");
        }
    }
}