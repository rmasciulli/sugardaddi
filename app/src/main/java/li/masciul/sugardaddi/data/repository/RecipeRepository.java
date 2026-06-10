package li.masciul.sugardaddi.data.repository;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import li.masciul.sugardaddi.SugarDaddiApplication;
import li.masciul.sugardaddi.core.enums.DataSourceType;
import li.masciul.sugardaddi.core.enums.Difficulty;
import li.masciul.sugardaddi.core.interfaces.Searchable;
import li.masciul.sugardaddi.core.models.Error;
import li.masciul.sugardaddi.core.models.FoodPortion;
import li.masciul.sugardaddi.core.models.Recipe;
import li.masciul.sugardaddi.core.models.SourceIdentifier;
import li.masciul.sugardaddi.data.database.AppDatabase;
import li.masciul.sugardaddi.data.database.dao.RecipeDao;
import li.masciul.sugardaddi.data.database.entities.RecipeEntity;
import li.masciul.sugardaddi.data.network.ApiConfig;
import li.masciul.sugardaddi.data.sources.base.DataSource;
import li.masciul.sugardaddi.data.sources.base.DataSourceCallback;
import li.masciul.sugardaddi.managers.DataSourceManager;
import li.masciul.sugardaddi.utils.image.ThumbnailDownloader;

import java.util.*;
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

    // Dependencies
    private final Context context;
    private final AppDatabase database;
    private final RecipeDao recipeDao;
    private final Executor backgroundExecutor;

    /**
     * Shared DataSourceManager singleton - same instance used by ProductRepository.
     * Recipe detail fetches call source.getRecipe() on any registered DataSource,
     * exactly as ProductRepository calls source.getProduct().
     */
    private final DataSourceManager dataSourceManager;

    // Caching
    private final Map<String, Recipe> recipeCache = new LinkedHashMap<>();
    private static final int MAX_CACHE_SIZE = 50;

    /**
     * Callback interfaces
     */
    public interface RecipeCallback {
        void onSuccess(Recipe recipe);
        void onError(String error);
    }

    public interface RecipeListCallback {
        void onSuccess(List<Recipe> recipes);
        void onError(String error);
    }

    public interface RecipeSearchCallback {
        void onSuccess(List<Recipe> recipes);
        void onError(String error);
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
     * Create a new recipe
     */
    public void createRecipe(Recipe recipe, RecipeCallback callback) {
        if (recipe == null) {
            callback.onError("Recipe cannot be null");
            return;
        }

        backgroundExecutor.execute(() -> {
            try {
                // Ensure recipe has valid data
                recipe.calculateNutrition();
                recipe.calculateCompleteness();

                // Save to database
                RecipeEntity entity = RecipeEntity.fromRecipe(recipe);
                recipeDao.insert(entity);

                // Cache the recipe
                cacheRecipe(recipe);

                runOnMainThread(() -> {
                    if (ApiConfig.DEBUG_LOGGING) {
                        Log.d(TAG, "Recipe created: " + recipe.getDisplayName(recipe.getCurrentLanguage()));
                    }
                    callback.onSuccess(recipe);
                });

            } catch (Exception e) {
                Log.e(TAG, "Error creating recipe", e);
                runOnMainThread(() -> callback.onError("Failed to create recipe: " + e.getMessage()));
            }
        });
    }

    /**
     * Get recipe by ID
     */
    public void getRecipe(String recipeId, RecipeCallback callback) {
        if (recipeId == null || recipeId.trim().isEmpty()) {
            callback.onError("Invalid recipe ID");
            return;
        }

        // Check cache first
        Recipe cached = recipeCache.get(recipeId);
        if (cached != null) {
            callback.onSuccess(cached);
            return;
        }

        backgroundExecutor.execute(() -> {
            try {
                RecipeEntity entity = recipeDao.getById(recipeId);
                if (entity != null) {
                    Recipe recipe = entity.toRecipe();
                    cacheRecipe(recipe);

                    runOnMainThread(() -> callback.onSuccess(recipe));
                } else {
                    runOnMainThread(() -> callback.onError("Recipe not found"));
                }

            } catch (Exception e) {
                Log.e(TAG, "Error loading recipe", e);
                runOnMainThread(() -> callback.onError("Failed to load recipe: " + e.getMessage()));
            }
        });
    }

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

        if ("USER".equals(sourceId)) {
            // USER recipes: Room lookup by searchableId which is the Room primary key
            getRecipe(searchableId, callback);
            return;
        }

        // External sources: resolve via DataSourceManager, call getRecipe()
        // Same routing pattern as ProductRepository.loadProductFromSource()
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

        // Check Room cache first - avoids a network round-trip for previously-viewed recipes
        getCachedExternalRecipe(sourceId, originalId, new RecipeCallback() {
            @Override
            public void onSuccess(Recipe recipe) {
                callback.onSuccess(recipe);
            }

            @Override
            public void onError(String cacheError) {
                // Not in Room - fetch live from the source via the standard interface
                if (ApiConfig.DEBUG_LOGGING) {
                    Log.d(TAG, "Room miss for " + sourceId + ":" + originalId
                            + " - fetching from network");
                }
                source.getRecipe(originalId, language, new DataSourceCallback<Recipe>() {
                    @Override
                    public void onSuccess(Recipe recipe) {
                        // Enrich with local image paths on a background thread -
                        // Room cannot be accessed on the main thread.
                        backgroundExecutor.execute(() -> {
                            enrichWithLocalImagePaths(recipe);
                            runOnMainThread(() -> callback.onSuccess(recipe));
                        });
                    }

                    @Override
                    public void onError(li.masciul.sugardaddi.core.models.Error error) {
                        Log.w(TAG, "Recipe fetch failed from " + sourceId
                                + ": " + error.getMessage());
                        callback.onError(error.getMessage());
                    }

                    @Override
                    public void onLoading() {}
                });
            }
        });
    }

    /**
     * Reads local image paths from the Room row for this recipe and applies
     * them to the in-memory domain object.
     *
     * Called after a network fetch to ensure the renderer receives the correct
     * local paths even though the API response has no knowledge of local files.
     *
     * Must be called from a background thread - performs a synchronous Room read.
     */
    private void enrichWithLocalImagePaths(@NonNull Recipe recipe) {
        try {
            RecipeEntity existing = recipeDao.getById(recipe.getSearchableId());
            if (existing == null) return;

            if (existing.getThumbnailPath() != null) {
                recipe.setThumbnailPath(existing.getThumbnailPath());
            }
            if (existing.getImagePath() != null) {
                recipe.setImagePath(existing.getImagePath());
            }
            if (existing.getUserThumbnailPath() != null) {
                recipe.setUserThumbnailPath(existing.getUserThumbnailPath());
            }
            if (existing.getUserImagePath() != null) {
                recipe.setUserImagePath(existing.getUserImagePath());
            }
        } catch (Exception e) {
            Log.w(TAG, "enrichWithLocalImagePaths failed for "
                    + recipe.getSearchableId() + ": " + e.getMessage());
        }
    }
    
    /**
     * Update existing recipe
     */
    public void updateRecipe(Recipe recipe, RecipeCallback callback) {
        if (recipe == null || recipe.getId() == null) {
            callback.onError("Invalid recipe data");
            return;
        }

        backgroundExecutor.execute(() -> {
            try {
                // Recalculate nutrition and completeness
                recipe.calculateNutrition();
                recipe.calculateCompleteness();
                recipe.touch();

                // Update database
                RecipeEntity entity = RecipeEntity.fromRecipe(recipe);
                recipeDao.update(entity);

                // Update cache
                cacheRecipe(recipe);

                runOnMainThread(() -> {
                    if (ApiConfig.DEBUG_LOGGING) {
                        Log.d(TAG, "Recipe updated: " + recipe.getId());
                    }
                    callback.onSuccess(recipe);
                });

            } catch (Exception e) {
                Log.e(TAG, "Error updating recipe", e);
                runOnMainThread(() -> callback.onError("Failed to update recipe: " + e.getMessage()));
            }
        });
    }

    /**
     * Delete recipe
     */
    public void deleteRecipe(String recipeId, RecipeOperationCallback callback) {
        if (recipeId == null || recipeId.trim().isEmpty()) {
            callback.onError("Invalid recipe ID");
            return;
        }

        backgroundExecutor.execute(() -> {
            try {
                recipeDao.deleteById(recipeId);
                recipeCache.remove(recipeId);

                runOnMainThread(() -> {
                    if (ApiConfig.DEBUG_LOGGING) {
                        Log.d(TAG, "Recipe deleted: " + recipeId);
                    }
                    callback.onSuccess();
                });

            } catch (Exception e) {
                Log.e(TAG, "Error deleting recipe", e);
                runOnMainThread(() -> callback.onError("Failed to delete recipe: " + e.getMessage()));
            }
        });
    }

    // ========== SEARCH AND FILTERING ==========

    /**
     * Search recipes from all sources.
     *
     * Queries two sources in parallel:
     *   1. Room - user-created recipes and previously cached external recipes
     *   2. TheMealDB - external recipe network source
     *
     * TheMealDB results are automatically saved to Room (background, non-blocking)
     * so they appear in subsequent Room-first queries - same pattern as FoodProduct.
     *
     * Results are merged, deduplicated by searchableId, sorted by relevance,
     * and delivered once both sources have responded.
     *
     * @param query    Search query. Minimum 2 characters.
     * @param language Language for relevance scoring.
     * @param callback Result callback - called on main thread.
     */
    public void search(String query, String language, RecipeSearchCallback callback) {
        if (query == null || query.trim().length() < 2) {
            callback.onError("Search query too short");
            return;
        }

        final String normalizedQuery = query.trim();

        backgroundExecutor.execute(() -> {
            try {
                List<RecipeEntity> entities = recipeDao.search(normalizedQuery, 100);
                List<Recipe> results = new ArrayList<>();

                for (RecipeEntity entity : entities) {
                    Recipe recipe = entity.toRecipe();
                    if (recipe.getSearchRelevance(normalizedQuery, language) >= 20) {
                        results.add(recipe);
                    }
                }

                results.sort((a, b) -> Integer.compare(
                        b.getSearchRelevance(normalizedQuery, language),
                        a.getSearchRelevance(normalizedQuery, language)));

                if (ApiConfig.DEBUG_LOGGING) {
                    Log.d(TAG, "Room recipe search: " + results.size()
                            + " results for '" + normalizedQuery + "'");
                }

                runOnMainThread(() -> callback.onSuccess(results));

            } catch (Exception e) {
                Log.e(TAG, "Room recipe search failed", e);
                runOnMainThread(() -> callback.onError(
                        "Recipe search failed: " + e.getMessage()));
            }
        });
    }

    /**
     * Get recipes by filters
     */
    public void getRecipesByFilters(RecipeFilters filters, RecipeListCallback callback) {
        backgroundExecutor.execute(() -> {
            try {
                List<RecipeEntity> entities;

                if (filters.difficulty != null) {
                    entities = recipeDao.getByDifficulty(filters.difficulty.getId());
                } else if (filters.maxTime > 0) {
                    entities = recipeDao.getByMaxTime(filters.maxTime);
                } else if (filters.isVegan) {
                    entities = recipeDao.getVeganRecipes();
                } else if (filters.isVegetarian) {
                    entities = recipeDao.getVegetarianRecipes();
                } else {
                    entities = recipeDao.getAll();
                }

                List<Recipe> recipes = new ArrayList<>();
                for (RecipeEntity entity : entities) {
                    Recipe recipe = entity.toRecipe();
                    if (filters.matches(recipe)) {
                        recipes.add(recipe);
                    }
                }

                runOnMainThread(() -> callback.onSuccess(recipes));

            } catch (Exception e) {
                Log.e(TAG, "Error filtering recipes", e);
                runOnMainThread(() -> callback.onError("Filter failed: " + e.getMessage()));
            }
        });
    }

    /**
     * Get user's recipes
     */
    public void getUserRecipes(String userId, RecipeListCallback callback) {
        backgroundExecutor.execute(() -> {
            try {
                List<RecipeEntity> entities = recipeDao.getByAuthor(userId);
                List<Recipe> recipes = new ArrayList<>();

                for (RecipeEntity entity : entities) {
                    recipes.add(entity.toRecipe());
                }

                // Sort by last updated
                recipes.sort((a, b) -> Long.compare(b.getLastUpdated(), a.getLastUpdated()));

                runOnMainThread(() -> callback.onSuccess(recipes));

            } catch (Exception e) {
                Log.e(TAG, "Error loading user recipes", e);
                runOnMainThread(() -> callback.onError("Failed to load recipes: " + e.getMessage()));
            }
        });
    }

    /**
     * Get favorite recipes
     */
    public void getFavoriteRecipes(RecipeListCallback callback) {
        backgroundExecutor.execute(() -> {
            try {
                List<RecipeEntity> entities = recipeDao.getFavoriteRecipes();
                List<Recipe> recipes = new ArrayList<>();

                for (RecipeEntity entity : entities) {
                    recipes.add(entity.toRecipe());
                }

                runOnMainThread(() -> callback.onSuccess(recipes));

            } catch (Exception e) {
                Log.e(TAG, "Error loading favorite recipes", e);
                runOnMainThread(() -> callback.onError("Failed to load favorites: " + e.getMessage()));
            }
        });
    }

    // ========== INGREDIENT MANAGEMENT ==========

    /**
     * Add portion to recipe
     */
    public void addPortion(String recipeId, FoodPortion portion, RecipeCallback callback) {
        getRecipe(recipeId, new RecipeCallback() {
            @Override
            public void onSuccess(Recipe recipe) {
                recipe.addPortion(portion);
                updateRecipe(recipe, callback);
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    /**
     * Remove portion from recipe
     */
    public void removePortion(String recipeId, int portionIndex, RecipeCallback callback) {
        getRecipe(recipeId, new RecipeCallback() {
            @Override
            public void onSuccess(Recipe recipe) {
                recipe.removePortion(portionIndex);
                updateRecipe(recipe, callback);
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    /**
     * Update portion in recipe
     */
    public void updatePortion(String recipeId, int portionIndex, FoodPortion portion, RecipeCallback callback) {
        getRecipe(recipeId, new RecipeCallback() {
            @Override
            public void onSuccess(Recipe recipe) {
                recipe.updatePortion(portionIndex, portion);
                updateRecipe(recipe, callback);
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    // ========== RECIPE OPERATIONS ==========

    /**
     * Toggle favorite status
     */
    public void toggleFavorite(String recipeId, RecipeOperationCallback callback) {
        getRecipe(recipeId, new RecipeCallback() {
            @Override
            public void onSuccess(Recipe recipe) {
                recipe.setFavorite(!recipe.isFavorite());
                updateRecipe(recipe, new RecipeCallback() {
                    @Override
                    public void onSuccess(Recipe updatedRecipe) {
                        callback.onSuccess();
                    }

                    @Override
                    public void onError(String error) {
                        callback.onError(error);
                    }
                });
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    /**
     * Create template from recipe
     */
    public void createTemplate(String recipeId, String templateName, String language, RecipeCallback callback) {
        getRecipe(recipeId, new RecipeCallback() {
            @Override
            public void onSuccess(Recipe recipe) {
                Recipe template = recipe.createTemplate(templateName, language);
                createRecipe(template, callback);
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    /**
     * Duplicate recipe
     */
    public void duplicateRecipe(String recipeId, String newName, RecipeCallback callback) {
        getRecipe(recipeId, new RecipeCallback() {
            @Override
            public void onSuccess(Recipe original) {
                // Use the copy method then customize
                Recipe duplicate = original.copy();

                // Generate new ID and reset metadata
                duplicate.setId(UUID.randomUUID().toString());
                duplicate.setCreatedAt(System.currentTimeMillis());
                duplicate.setLastUpdated(System.currentTimeMillis());
                duplicate.setFavorite(false);
                duplicate.setFavoriteCount(0);
                duplicate.setRating(0);
                duplicate.setRatingCount(0);

                // Update name in current language
                String language = original.getCurrentLanguage();
                duplicate.setName(newName, language);

                createRecipe(duplicate, callback);
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    // ========== EXTERNAL RECIPE PERSISTENCE ==========

    /**
     * Persist an externally-sourced recipe (e.g. TheMealDB) to Room.
     *
     * Called when the user interacts with an external recipe for the first time:
     * opening its detail screen, marking it as a favourite, or adding it to a meal.
     *
     * This follows the same pattern as FoodProductEntity - external items are
     * cached on first interaction, not pre-emptively on every search result.
     *
     * UPSERT BEHAVIOUR:
     * RecipeDao.insert() uses OnConflictStrategy.REPLACE, so calling this method
     * on an already-cached recipe replaces the row with fresh data. Fields the
     * user may have changed (isFavorite) should be preserved - use
     * setExternalRecipeFavorite() for toggling favourites on existing cached recipes.
     *
     * NUTRITION:
     * calculateNutrition() is NOT called. External recipes from TheMealDB have
     * no resolved FoodProduct ingredient references yet. Nutrition stays null
     * until the user manually enters it or ingredient resolution is implemented.
     *
     * @param recipe   External recipe to cache. Must have dataSource != USER.
     * @param callback Called with the saved recipe on the main thread.
     */
    public void saveExternalRecipe(@NonNull Recipe recipe,
                                   @NonNull RecipeCallback callback) {
        if (recipe.getDataSource() == DataSourceType.USER) {
            // Wrong method - user-created recipes go through createRecipe()
            callback.onError("Use createRecipe() for user-created recipes");
            return;
        }

        backgroundExecutor.execute(() -> {
            try {
                // Completeness only - no nutrition for external recipes yet
                recipe.calculateCompleteness();
                recipe.setLastUpdated(System.currentTimeMillis());
                if (recipe.getCreatedAt() == 0) {
                    recipe.setCreatedAt(System.currentTimeMillis());
                }

                RecipeEntity entity = RecipeEntity.fromRecipe(recipe);

                // Preserve local image paths from any existing cached row.
                // A network refresh must never wipe user-set or auto-cached
                // local paths - the API response has no knowledge of local files.
                RecipeEntity existingEntity = recipeDao.getById(recipe.getSearchableId());
                if (existingEntity != null) {
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
                    // Also preserve isFavorite - saveExternalRecipe() is called
                    // on every detail view open and must not unfavourite a product.
                    if (existingEntity.isFavorite()) {
                        entity.setFavorite(true);
                    }
                }

                recipeDao.insert(entity); // REPLACE on conflict

                // Populate memory cache
                cacheRecipe(recipe);

                runOnMainThread(() -> {
                    if (ApiConfig.DEBUG_LOGGING) {
                        Log.d(TAG, "External recipe cached: "
                                + recipe.getDataSource().getId()
                                + ":" + recipe.getOriginalId());
                    }
                    callback.onSuccess(recipe);
                });

            } catch (Exception e) {
                Log.e(TAG, "Error caching external recipe", e);
                runOnMainThread(() -> callback.onError(
                        "Failed to cache recipe: " + e.getMessage()));
            }
        });
    }

    /**
     * Auto-save a list of externally-fetched recipes to Room.
     *
     * Mirrors ProductRepository.saveProductToDatabase() - called automatically
     * after every successful TheMealDB fetch so that recipes are available for:
     * - Subsequent Room-first searches (avoiding repeat network calls)
     * - Favorites (RecipeEntity.isFavorite flag)
     * - Meal composition (RecipeEntity referenced by FoodPortion)
     * - FavoritesActivity (loads from Room directly)
     *
     * CONFLICT STRATEGY: OnConflictStrategy.IGNORE - if the recipe already exists
     * in Room (e.g. from a previous search or user interaction), the existing row
     * is preserved. This protects user-set fields (isFavorite, accessCount).
     * Use saveExternalRecipe() with REPLACE if you need to force-update.
     *
     * NUTRITION: calculateNutrition() is NOT called - TheMealDB has no nutrition
     * data. Nutrition stays null until user sets it or ingredient resolution runs.
     *
     * Fire-and-forget - runs on background executor, never blocks the caller.
     *
     * @param recipes Recipes to persist. Null or empty list is a no-op.
     */
    private void saveRecipesToDatabase(@NonNull List<Recipe> recipes) {
        if (recipes == null || recipes.isEmpty()) return;

        backgroundExecutor.execute(() -> {
            int savedCount = 0;
            for (Recipe recipe : recipes) {
                try {
                    // Skip user-created recipes - they go through createRecipe()
                    if (recipe.getDataSource() == DataSourceType.USER) continue;

                    recipe.calculateCompleteness();
                    recipe.setLastUpdated(System.currentTimeMillis());
                    if (recipe.getCreatedAt() == 0) {
                        recipe.setCreatedAt(System.currentTimeMillis());
                    }

                    RecipeEntity entity = RecipeEntity.fromRecipe(recipe);

                    // IGNORE on conflict - preserves existing user-set fields
                    // (isFavorite, accessCount, user notes if any)
                    recipeDao.insertIfNotExists(entity);
                    cacheRecipe(recipe);
                    savedCount++;

                } catch (Exception e) {
                    Log.w(TAG, "Failed to auto-save recipe: "
                            + recipe.getSearchableId() + " - " + e.getMessage());
                    // Never crash the search flow - persistence is best-effort
                }
            }

            if (ApiConfig.DEBUG_LOGGING && savedCount > 0) {
                Log.d(TAG, "Auto-saved " + savedCount + "/" + recipes.size()
                        + " external recipes to Room");
            }
        });
    }

    /**
     * Look up a cached external recipe by its source and original ID.
     *
     * Checks the in-memory cache first (O(n) scan on source+originalId),
     * then falls back to Room. Returns an error via callback if not found -
     * the caller should then fetch from the network and call saveExternalRecipe().
     *
     * This is the cache-check step in the network-first-or-cache pattern:
     *   1. Call getCachedExternalRecipe()
     *   2. If found → use it (no network call)
     *   3. If not found → fetch from network → call saveExternalRecipe()
     *
     * @param sourceId   DataSource ID string (e.g. "THEMEALDB")
     * @param originalId External recipe ID (e.g. "52772")
     * @param callback   Called with the cached Recipe, or onError if not found.
     */
    public void getCachedExternalRecipe(@NonNull String sourceId,
                                        @NonNull String originalId,
                                        @NonNull RecipeCallback callback) {
        // Check in-memory cache first - avoids a background thread dispatch
        Recipe memoryCached = findInCacheBySourceId(sourceId, originalId);
        if (memoryCached != null) {
            if (ApiConfig.DEBUG_LOGGING) {
                Log.d(TAG, "Memory cache hit: " + sourceId + ":" + originalId);
            }
            callback.onSuccess(memoryCached);
            return;
        }

        // Fall back to Room
        backgroundExecutor.execute(() -> {
            try {
                RecipeEntity entity =
                        recipeDao.getBySourceAndOriginalId(sourceId, originalId);

                if (entity != null) {
                    Recipe recipe = entity.toRecipe();
                    cacheRecipe(recipe);

                    if (ApiConfig.DEBUG_LOGGING) {
                        Log.d(TAG, "Room cache hit: " + sourceId + ":" + originalId);
                    }

                    runOnMainThread(() -> callback.onSuccess(recipe));
                } else {
                    // Not cached - caller should fetch from network
                    runOnMainThread(() -> callback.onError(
                            "Not cached: " + sourceId + ":" + originalId));
                }

            } catch (Exception e) {
                Log.e(TAG, "Error looking up cached external recipe", e);
                runOnMainThread(() -> callback.onError(
                        "Cache lookup failed: " + e.getMessage()));
            }
        });
    }

    /**
     * Toggle the favourite flag on an external recipe, persisting the change to Room.
     *
     * If the recipe is not yet cached in Room (first interaction), it is saved
     * in full first via saveExternalRecipe(). If already cached, only the
     * isFavorite flag and lastUpdated timestamp are updated - other fields
     * (especially user-set ones) are preserved.
     *
     * For user-created recipes, use updateRecipe() directly.
     *
     * @param recipe   The external recipe to favourite/unfavourite.
     * @param favorite True to favourite, false to unfavourite.
     * @param callback Operation result - called on main thread.
     */
    public void setExternalRecipeFavorite(@NonNull Recipe recipe,
                                          boolean favorite,
                                          @NonNull RecipeOperationCallback callback) {
        if (recipe.getDataSource() == DataSourceType.USER) {
            callback.onError("Use updateRecipe() for user-created recipes");
            return;
        }

        recipe.setFavorite(favorite);
        recipe.touch();

        final String sourceId   = recipe.getDataSource().getId();
        final String originalId = recipe.getOriginalId();

        backgroundExecutor.execute(() -> {
            try {
                // Check if already persisted
                RecipeEntity existing = (sourceId != null && originalId != null)
                        ? recipeDao.getBySourceAndOriginalId(sourceId, originalId)
                        : null;

                if (existing != null) {
                    // Already in Room - update only the favourite flag and timestamp
                    existing.setFavorite(favorite);
                    existing.touch();
                    recipeDao.update(existing);

                    if (!favorite) {
                        existing.setThumbnailPath(null);
                        // second write to clear path
                        recipeDao.update(existing);
                    }
                    handleThumbnailForFavorite(recipe, recipe.getSearchableId(), favorite);

                    // Sync memory cache
                    cacheRecipe(recipe);

                    runOnMainThread(callback::onSuccess);
                } else {
                    // First interaction - persist the full recipe, then return
                    runOnMainThread(() -> saveExternalRecipe(recipe, new RecipeCallback() {
                        @Override
                        public void onSuccess(Recipe saved) {
                            handleThumbnailForFavorite(recipe, recipe.getSearchableId(), favorite);
                            callback.onSuccess();
                        }
                        @Override
                        public void onError(String error) {
                            callback.onError(error);
                        }
                    }));
                }

            } catch (Exception e) {
                Log.e(TAG, "Error updating favourite for external recipe", e);
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
     * heroImagePath remains null until the user explicitly sets one.
     *
     * UNFAVOURITING
     * =============
     * Deletes the cached file from disk. thumbnailPath is cleared in Room
     * before this method is called (see setExternalRecipeFavorite()).
     *
     * @param recipe     Recipe domain object (provides imageUrl).
     * @param recipeId   Source-qualified ID (Room primary key).
     * @param isFavorite The new favourite state just written to Room.
     */
    private void handleThumbnailForFavorite(
            @NonNull Recipe recipe,
            @NonNull String recipeId,
            boolean isFavorite) {

        ThumbnailDownloader downloader = getThumbnailDownloader();
        if (downloader == null) return;

        if (isFavorite) {
            String url = recipe.getImageUrl();
            if (url == null || url.trim().isEmpty()) {
                Log.d(TAG, "No image URL available for recipe " + recipeId
                        + " - skipping thumbnail download");
                return;
            }

            downloader.download(url, recipeId, new ThumbnailDownloader.Callback() {
                @Override
                public void onSuccess(@NonNull String localPath) {
                    backgroundExecutor.execute(() -> {
                        try {
                            database.recipeDao().updateThumbnailPath(recipeId, localPath);
                            if (ApiConfig.DEBUG_LOGGING) {
                                Log.d(TAG, "Thumbnail cached for recipe "
                                        + recipeId + ": " + localPath);
                            }
                        } catch (Exception e) {
                            Log.w(TAG, "Failed to persist thumbnailPath for recipe "
                                    + recipeId, e);
                        }
                    });
                }

                @Override
                public void onError(@NonNull String reason) {
                    // Non-fatal - Glide will load from the remote URL instead.
                    Log.d(TAG, "Thumbnail download failed for recipe "
                            + recipeId + ": " + reason);
                }
            });

        } else {
            // thumbnailPath already cleared in Room before this call.
            downloader.deleteThumbnail(recipeId);
        }
    }

    /**
     * Returns the application-scoped ThumbnailDownloader singleton.
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
     * Search the in-memory cache for a recipe by source ID and original ID.
     *
     * Linear scan - acceptable given the small cache size (MAX_CACHE_SIZE = 50).
     * Used by getCachedExternalRecipe() to avoid a background thread dispatch
     * for hot cache hits.
     *
     * @param sourceId   DataSource ID (e.g. "THEMEALDB")
     * @param originalId External recipe ID (e.g. "52772")
     * @return Matching recipe, or null if not in memory cache.
     */
    @Nullable
    private Recipe findInCacheBySourceId(@NonNull String sourceId,
                                         @NonNull String originalId) {
        for (Recipe cached : recipeCache.values()) {
            SourceIdentifier si = cached.getSourceIdentifier();
            if (si != null
                    && sourceId.equals(si.getSourceId())
                    && originalId.equals(si.getOriginalId())) {
                return cached;
            }
        }
        return null;
    }

    // ========== UTILITY METHODS ==========

    private void cacheRecipe(Recipe recipe) {
        if (recipe == null || recipe.getId() == null) return;

        recipeCache.put(recipe.getId(), recipe);

        // Maintain cache size
        if (recipeCache.size() > MAX_CACHE_SIZE) {
            String firstKey = recipeCache.keySet().iterator().next();
            recipeCache.remove(firstKey);
        }
    }

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

    /**
     * Recipe filter criteria
     */
    public static class RecipeFilters {
        public Difficulty difficulty;
        public int maxTime = 0;
        public boolean isVegan = false;
        public boolean isVegetarian = false;
        public boolean isGlutenFree = false;
        public Set<String> tags = new HashSet<>();

        public boolean matches(Recipe recipe) {
            if (difficulty != null && recipe.getDifficulty() != difficulty) {
                return false;
            }

            if (maxTime > 0 && recipe.getTotalTimeMinutes() > maxTime) {
                return false;
            }

            if (isVegan && !recipe.isVegan()) {
                return false;
            }

            if (isVegetarian && !recipe.isVegetarian()) {
                return false;
            }

            if (isGlutenFree && !recipe.isGlutenFree()) {
                return false;
            }

            if (!tags.isEmpty()) {
                Set<String> recipeTags = recipe.getTags();
                for (String tag : tags) {
                    if (!recipeTags.contains(tag)) {
                        return false;
                    }
                }
            }

            return true;
        }
    }
}