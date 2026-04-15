package li.masciul.sugardaddi.data.repository;

import android.content.Context;
import android.util.Log;

import li.masciul.sugardaddi.core.models.FoodPortion;
import li.masciul.sugardaddi.core.models.Recipe;
import li.masciul.sugardaddi.core.enums.Difficulty;
import li.masciul.sugardaddi.data.database.AppDatabase;
import li.masciul.sugardaddi.data.database.dao.RecipeDao;
import li.masciul.sugardaddi.data.database.entities.RecipeEntity;
import li.masciul.sugardaddi.data.network.ApiConfig;

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
    private final ProductRepository productRepository;
    private final Executor backgroundExecutor;

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
        this.productRepository = new ProductRepository(null, context); // TODO: Inject properly
        this.backgroundExecutor = Executors.newSingleThreadExecutor();

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
     * Search recipes by query
     */
    public void search(String query, String language, RecipeSearchCallback callback) {
        if (query == null || query.trim().length() < 2) {
            callback.onError("Search query too short");
            return;
        }

        backgroundExecutor.execute(() -> {
            try {
                List<RecipeEntity> entities = recipeDao.search(query, 100);
                List<Recipe> recipes = new ArrayList<>();

                for (RecipeEntity entity : entities) {
                    Recipe recipe = entity.toRecipe();
                    // Filter by relevance
                    int relevance = recipe.getSearchRelevance(query, language);
                    if (relevance >= 20) {
                        recipes.add(recipe);
                    }
                }

                // Sort by relevance
                recipes.sort((a, b) -> Integer.compare(
                        b.getSearchRelevance(query, language),
                        a.getSearchRelevance(query, language)
                ));

                runOnMainThread(() -> {
                    if (ApiConfig.DEBUG_LOGGING) {
                        Log.d(TAG, "Recipe search returned " + recipes.size() + " results for: " + query);
                    }
                    callback.onSuccess(recipes);
                });

            } catch (Exception e) {
                Log.e(TAG, "Error searching recipes", e);
                runOnMainThread(() -> callback.onError("Search failed: " + e.getMessage()));
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
        // Cancel any ongoing operations if needed
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