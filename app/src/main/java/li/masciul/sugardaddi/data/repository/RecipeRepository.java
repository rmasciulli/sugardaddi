package li.masciul.sugardaddi.data.repository;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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
import li.masciul.sugardaddi.data.sources.themealdb.TheMealDbConfig;
import li.masciul.sugardaddi.data.sources.themealdb.TheMealDbConstants;
import li.masciul.sugardaddi.data.sources.themealdb.TheMealDbDataSource;
import li.masciul.sugardaddi.data.sources.base.DataSourceCallback;

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
    private final TheMealDbDataSource mealDbDataSource;

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
        this.mealDbDataSource = new TheMealDbDataSource(
                this.context, new TheMealDbConfig(this.context));
        this.mealDbDataSource.initialize(this.context);

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
     *   1. Room — user-created recipes and previously cached external recipes
     *   2. TheMealDB — external recipe network source
     *
     * TheMealDB results are automatically saved to Room (background, non-blocking)
     * so they appear in subsequent Room-first queries — same pattern as FoodProduct.
     *
     * Results are merged, deduplicated by searchableId, sorted by relevance,
     * and delivered once both sources have responded.
     *
     * @param query    Search query. Minimum 2 characters.
     * @param language Language for relevance scoring.
     * @param callback Result callback — called on main thread.
     */
    public void search(String query, String language, RecipeSearchCallback callback) {
        if (query == null || query.trim().length() < 2) {
            callback.onError("Search query too short");
            return;
        }

        final String normalizedQuery = query.trim();

        // AtomicInteger tracks completion of both parallel sources
        final java.util.concurrent.atomic.AtomicInteger pending =
                new java.util.concurrent.atomic.AtomicInteger(2);

        // Synchronized list — both sources write here
        final List<Recipe> combined =
                java.util.Collections.synchronizedList(new ArrayList<>());

        // Deduplication set — prevents same recipe appearing twice
        // (e.g. if TheMealDB result was already cached in Room)
        final Set<String> seenIds =
                java.util.Collections.synchronizedSet(new java.util.HashSet<>());

        // Fired once both sources complete — sorts and delivers
        final Runnable onBothDone = () -> {
            List<Recipe> sorted = new ArrayList<>(combined);
            sorted.sort((a, b) -> Integer.compare(
                    b.getSearchRelevance(normalizedQuery, language),
                    a.getSearchRelevance(normalizedQuery, language)));

            if (ApiConfig.DEBUG_LOGGING) {
                Log.d(TAG, "Combined recipe search: " + sorted.size()
                        + " results for '" + normalizedQuery + "'");
            }

            runOnMainThread(() -> callback.onSuccess(sorted));
        };

        // ── Source 1: Room ────────────────────────────────────────────────────
        // Queries both user-created AND previously cached external recipes
        backgroundExecutor.execute(() -> {
            try {
                List<RecipeEntity> entities = recipeDao.search(normalizedQuery, 100);
                for (RecipeEntity entity : entities) {
                    Recipe recipe = entity.toRecipe();
                    int relevance = recipe.getSearchRelevance(normalizedQuery, language);
                    if (relevance >= 20 && seenIds.add(recipe.getSearchableId())) {
                        combined.add(recipe);
                    }
                }
                if (ApiConfig.DEBUG_LOGGING) {
                    Log.d(TAG, "Room recipe search: " + entities.size()
                            + " for '" + normalizedQuery + "'");
                }
            } catch (Exception e) {
                Log.e(TAG, "Room recipe search failed", e);
            } finally {
                if (pending.decrementAndGet() == 0) onBothDone.run();
            }
        });

        // ── Source 2: TheMealDB ───────────────────────────────────────────────
        mealDbDataSource.search(
                normalizedQuery,
                language,
                TheMealDbConstants.MAX_SEARCH_RESULTS,
                1,
                new DataSourceCallback<DataSource.SearchResult>() {
                    @Override
                    public void onSuccess(DataSource.SearchResult result) {
                        List<Recipe> fetchedRecipes = new ArrayList<>();

                        for (Searchable item : result.items) {
                            if (item instanceof Recipe) {
                                Recipe recipe = (Recipe) item;
                                int relevance = recipe.getSearchRelevance(normalizedQuery, language);
                                if (relevance >= 20 && seenIds.add(recipe.getSearchableId())) {
                                    combined.add(recipe);
                                    fetchedRecipes.add(recipe);
                                }
                            }
                        }

                        // Auto-save to Room — same as ProductRepository.saveProductToDatabase()
                        // Fire-and-forget, background thread, non-blocking
                        if (!fetchedRecipes.isEmpty()) {
                            saveRecipesToDatabase(fetchedRecipes);
                        }

                        if (ApiConfig.DEBUG_LOGGING) {
                            Log.d(TAG, "TheMealDB search: " + fetchedRecipes.size()
                                    + " results for '" + normalizedQuery + "'");
                        }

                        if (pending.decrementAndGet() == 0) onBothDone.run();
                    }

                    @Override
                    public void onError(Error error) {
                        // Non-fatal — Room results still deliver
                        Log.w(TAG, "TheMealDB search failed (non-fatal): "
                                + error.getMessage());
                        if (pending.decrementAndGet() == 0) onBothDone.run();
                    }

                    @Override
                    public void onLoading() {}
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
     * This follows the same pattern as FoodProductEntity — external items are
     * cached on first interaction, not pre-emptively on every search result.
     *
     * UPSERT BEHAVIOUR:
     * RecipeDao.insert() uses OnConflictStrategy.REPLACE, so calling this method
     * on an already-cached recipe replaces the row with fresh data. Fields the
     * user may have changed (isFavorite) should be preserved — use
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
            // Wrong method — user-created recipes go through createRecipe()
            callback.onError("Use createRecipe() for user-created recipes");
            return;
        }

        backgroundExecutor.execute(() -> {
            try {
                // Completeness only — no nutrition for external recipes yet
                recipe.calculateCompleteness();
                recipe.setLastUpdated(System.currentTimeMillis());
                if (recipe.getCreatedAt() == 0) {
                    recipe.setCreatedAt(System.currentTimeMillis());
                }

                RecipeEntity entity = RecipeEntity.fromRecipe(recipe);
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
     * Mirrors ProductRepository.saveProductToDatabase() — called automatically
     * after every successful TheMealDB fetch so that recipes are available for:
     * - Subsequent Room-first searches (avoiding repeat network calls)
     * - Favorites (RecipeEntity.isFavorite flag)
     * - Meal composition (RecipeEntity referenced by FoodPortion)
     * - FavoritesActivity (loads from Room directly)
     *
     * CONFLICT STRATEGY: OnConflictStrategy.IGNORE — if the recipe already exists
     * in Room (e.g. from a previous search or user interaction), the existing row
     * is preserved. This protects user-set fields (isFavorite, accessCount).
     * Use saveExternalRecipe() with REPLACE if you need to force-update.
     *
     * NUTRITION: calculateNutrition() is NOT called — TheMealDB has no nutrition
     * data. Nutrition stays null until user sets it or ingredient resolution runs.
     *
     * Fire-and-forget — runs on background executor, never blocks the caller.
     *
     * @param recipes Recipes to persist. Null or empty list is a no-op.
     */
    private void saveRecipesToDatabase(@NonNull List<Recipe> recipes) {
        if (recipes == null || recipes.isEmpty()) return;

        backgroundExecutor.execute(() -> {
            int savedCount = 0;
            for (Recipe recipe : recipes) {
                try {
                    // Skip user-created recipes — they go through createRecipe()
                    if (recipe.getDataSource() == DataSourceType.USER) continue;

                    recipe.calculateCompleteness();
                    recipe.setLastUpdated(System.currentTimeMillis());
                    if (recipe.getCreatedAt() == 0) {
                        recipe.setCreatedAt(System.currentTimeMillis());
                    }

                    RecipeEntity entity = RecipeEntity.fromRecipe(recipe);

                    // IGNORE on conflict — preserves existing user-set fields
                    // (isFavorite, accessCount, user notes if any)
                    recipeDao.insertIfNotExists(entity);
                    cacheRecipe(recipe);
                    savedCount++;

                } catch (Exception e) {
                    Log.w(TAG, "Failed to auto-save recipe: "
                            + recipe.getSearchableId() + " — " + e.getMessage());
                    // Never crash the search flow — persistence is best-effort
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
     * then falls back to Room. Returns an error via callback if not found —
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
        // Check in-memory cache first — avoids a background thread dispatch
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
                    // Not cached — caller should fetch from network
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
     * isFavorite flag and lastUpdated timestamp are updated — other fields
     * (especially user-set ones) are preserved.
     *
     * For user-created recipes, use updateRecipe() directly.
     *
     * @param recipe   The external recipe to favourite/unfavourite.
     * @param favorite True to favourite, false to unfavourite.
     * @param callback Operation result — called on main thread.
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
                    // Already in Room — update only the favourite flag and timestamp
                    existing.setFavorite(favorite);
                    existing.touch();
                    recipeDao.update(existing);

                    // Sync memory cache
                    cacheRecipe(recipe);

                    runOnMainThread(callback::onSuccess);
                } else {
                    // First interaction — persist the full recipe, then return
                    runOnMainThread(() -> saveExternalRecipe(recipe, new RecipeCallback() {
                        @Override
                        public void onSuccess(Recipe saved) {
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
     * Search the in-memory cache for a recipe by source ID and original ID.
     *
     * Linear scan — acceptable given the small cache size (MAX_CACHE_SIZE = 50).
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

    public void cancelSearch() {
        mealDbDataSource.cancelOperations();
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