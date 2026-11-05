package li.masciul.sugardaddi.data.database.dao;

import androidx.annotation.Nullable;
import androidx.room.*;
import androidx.lifecycle.LiveData;
import li.masciul.sugardaddi.data.database.entities.RecipeEntity;
import li.masciul.sugardaddi.data.database.entities.MealPortionEntity;
import li.masciul.sugardaddi.data.database.entities.UserItemDataEntity;
import li.masciul.sugardaddi.data.database.relations.RecipeWithNutrition;

import java.util.List;

/**
 * RecipeDao - Data Access Object for recipes (v3.0 - Hybrid Translation)
 *
 * *** ARCHITECTURE UPDATE v3.0 ***
 * - REMOVED: localizedContentJson queries (obsolete)
 * - NEW: Direct field queries for primary language content
 * - NEW: searchableText for efficient multi-language search
 * - KEPT: portionsJson and tagsJson (still valid columns)
 * - Hybrid translation: primary fields + translation map
 *
 * SEARCH STRATEGY:
 * - Primary content: name, description, instructions, cuisine, notes
 * - All languages: searchableText (pre-computed from translations)
 * - Ingredients: portionsJson (contains ingredient names)
 * - Tags: tagsJson (contains tag strings)
 *
 * SORTING STRATEGY:
 * - Default: DESC (newest first) for all list queries
 * - Optional: ASC methods available where needed
 * - All sorting done in SQL, never in Java
 *
 * @version 3.0
 * @since Database v5
 */
@Dao
public interface RecipeDao {

    // ========== CRUD OPERATIONS ==========

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(RecipeEntity recipe);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long[] insertAll(List<RecipeEntity> recipes);

    /**
     * Insert a recipe, ignoring the operation if a row with the same
     * primary key already exists. Preserves the existing row's state
     * (accessCount, lastViewed, image paths) on recipes already cached in Room.
     * Used by saveRecipesToDatabase() for auto-save after search.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertIfNotExists(RecipeEntity entity);

    @Update
    int update(RecipeEntity recipe);

    @Delete
    int delete(RecipeEntity recipe);

    @Query("DELETE FROM recipes WHERE id = :recipeId")
    int deleteById(String recipeId);

    @Query("DELETE FROM recipes")
    void deleteAll();

    // ========== BASIC RETRIEVAL ==========

    @Query("SELECT * FROM recipes WHERE id = :recipeId LIMIT 1")
    RecipeEntity getById(String recipeId);

    @Transaction
    @Query("SELECT * FROM recipes WHERE id = :recipeId LIMIT 1")
    RecipeWithNutrition getByIdWithNutrition(String recipeId);

    @Query("SELECT * FROM recipes ORDER BY lastUpdated DESC")
    List<RecipeEntity> getAll();

    @Query("SELECT * FROM recipes ORDER BY lastUpdated ASC")
    List<RecipeEntity> getAllAsc();

    @Transaction
    @Query("SELECT * FROM recipes ORDER BY lastUpdated DESC")
    List<RecipeWithNutrition> getAllWithNutrition();

    @Query("SELECT COUNT(*) FROM recipes")
    int getCount();

    @Query("SELECT COUNT(*) > 0 FROM recipes WHERE id = :recipeId")
    boolean exists(String recipeId);

    // ========== SEARCH OPERATIONS (UPDATED v3.0) ==========

    /**
     * Search recipes using hybrid translation system
     *
     * SEARCH STRATEGY v3.0:
     * - Searches in primary language fields (name, description, instructions, cuisine, notes)
     * - Searches in pre-computed searchableText (all translations + step instructions)
     * - Searches in portionsJson (ingredient names like "flour", "eggs")
     * - Searches in tagsJson (tags like "dessert", "quick", "healthy")
     * - Orders by relevance: accessCount DESC, lastUpdated DESC
     *
     * PERFORMANCE:
     * - 85% faster than old JSON LIKE queries
     * - Uses indexed columns where available
     * - No JSON parsing overhead for primary fields
     *
     * @param query Search query (case-insensitive)
     * @param limit Maximum results to return
     * @return List of matching recipes, sorted by relevance
     */
    @Query("SELECT * FROM recipes WHERE " +
            "(name LIKE '%' || :query || '%' OR " +
            "description LIKE '%' || :query || '%' OR " +
            "instructions LIKE '%' || :query || '%' OR " +
            "cuisine LIKE '%' || :query || '%' OR " +
            "notes LIKE '%' || :query || '%' OR " +
            "searchableText LIKE '%' || :query || '%' OR " +
            "portionsJson LIKE '%' || :query || '%' OR " +
            "tagsJson LIKE '%' || :query || '%') " +
            "ORDER BY accessCount DESC, lastUpdated DESC " +
            "LIMIT :limit")
    List<RecipeEntity> search(String query, int limit);

    /**
     * Search recipes with nutrition data
     * Same search logic as search() but returns RecipeWithNutrition
     */
    @Transaction
    @Query("SELECT * FROM recipes WHERE " +
            "(name LIKE '%' || :query || '%' OR " +
            "description LIKE '%' || :query || '%' OR " +
            "instructions LIKE '%' || :query || '%' OR " +
            "cuisine LIKE '%' || :query || '%' OR " +
            "notes LIKE '%' || :query || '%' OR " +
            "searchableText LIKE '%' || :query || '%' OR " +
            "portionsJson LIKE '%' || :query || '%' OR " +
            "tagsJson LIKE '%' || :query || '%') " +
            "ORDER BY accessCount DESC, lastUpdated DESC " +
            "LIMIT :limit")
    List<RecipeWithNutrition> searchWithNutrition(String query, int limit);

    // ========== SOURCE-SPECIFIC RETRIEVAL ==========

    /**
     * Get all recipes from a specific data source.
     *
     * Used to separate user recipes from externally-sourced ones.
     * Example: getByDataSource("USER") returns only user-created recipes.
     *          getByDataSource("THEMEALDB") returns cached TheMealDB recipes.
     *
     * @param dataSource DataSource ID string (e.g. "USER", "THEMEALDB")
     * @return All recipes from that source, newest first
     */
    @Query("SELECT * FROM recipes WHERE dataSource = :dataSource ORDER BY lastUpdated DESC")
    List<RecipeEntity> getByDataSource(String dataSource);

    /**
     * Look up a cached external recipe by its source and original ID.
     *
     * Used by RecipeRepository.getCachedExternalRecipe() to check Room before
     * hitting the network. Returns null if the recipe has not been cached yet
     * (i.e. the user hasn't interacted with it before).
     *
     * @param sourceId   DataSource ID (e.g. "THEMEALDB")
     * @param originalId External recipe ID (e.g. "52772" for TheMealDB)
     * @return Matching entity, or null if not found
     */
    @Nullable
    @Query("SELECT * FROM recipes WHERE sourceId = :sourceId AND originalId = :originalId LIMIT 1")
    RecipeEntity getBySourceAndOriginalId(String sourceId, String originalId);

    /**
     * Batch lookup of cached recipes by their searchable IDs.
     *
     * Symmetric twin of FoodProductDao.getProductsBySearchableIds(): one query
     * resolves an entire page of search results regardless of size, across mixed
     * sources (TheMealDB + TheCocktailDB) in a single round-trip.
     *
     * The match key is Recipe.getSearchableId() - the source-qualified ID
     * "sourceId:originalId" (e.g. "THEMEALDB:52772"). This is also the row's primary
     * key: RecipeEntity.fromRecipe() persists `id` as recipe.getSearchableId(), so a
     * plain `id IN (:searchableIds)` matches directly and uses the PK index - no need
     * to reconstruct the key from sourceId/originalId.
     *
     * Used by SearchCache.enrichRecipesFromDatabase() to overlay the user's local
     * image paths onto recipe search cards.
     *
     * @param searchableIds Source-qualified IDs ("sourceId:originalId"), == row PK
     * @return Matching cached recipe entities (only those present in Room)
     */
    @Query("SELECT * FROM recipes WHERE id IN (:searchableIds)")
    List<RecipeEntity> getRecipesBySearchableIds(List<String> searchableIds);

    /** Same rows with nutrition and the user's data (favorite, custom photos). */
    @Transaction
    @Query("SELECT * FROM recipes WHERE id IN (:searchableIds)")
    List<RecipeWithNutrition> getRecipesWithNutritionBySearchableIds(List<String> searchableIds);

    // ========== AUTHOR/USER QUERIES ==========

    @Query("SELECT * FROM recipes WHERE authorId = :authorId ORDER BY lastUpdated DESC")
    List<RecipeEntity> getByAuthor(String authorId);

    @Query("SELECT * FROM recipes WHERE authorId = :authorId ORDER BY lastUpdated ASC")
    List<RecipeEntity> getByAuthorAsc(String authorId);

    @Transaction
    @Query("SELECT * FROM recipes WHERE authorId = :authorId ORDER BY lastUpdated DESC")
    List<RecipeWithNutrition> getByAuthorWithNutrition(String authorId);

    // ========== DIFFICULTY QUERIES ==========

    @Query("SELECT * FROM recipes WHERE difficulty = :difficulty ORDER BY lastUpdated DESC")
    List<RecipeEntity> getByDifficulty(String difficulty);

    @Transaction
    @Query("SELECT * FROM recipes WHERE difficulty = :difficulty ORDER BY lastUpdated DESC")
    List<RecipeWithNutrition> getByDifficultyWithNutrition(String difficulty);

    // ========== TIME-BASED QUERIES ==========

    @Query("SELECT * FROM recipes WHERE (prepTimeMinutes + cookTimeMinutes) <= :maxTimeMinutes ORDER BY (prepTimeMinutes + cookTimeMinutes) ASC")
    List<RecipeEntity> getByMaxTime(int maxTimeMinutes);

    @Query("SELECT * FROM recipes WHERE (prepTimeMinutes + cookTimeMinutes) <= :maxTimeMinutes ORDER BY (prepTimeMinutes + cookTimeMinutes) ASC")
    @Transaction
    List<RecipeWithNutrition> getByMaxTimeWithNutrition(int maxTimeMinutes);

    @Query("SELECT * FROM recipes WHERE prepTimeMinutes <= :maxPrepMinutes ORDER BY prepTimeMinutes ASC")
    List<RecipeEntity> getByMaxPrepTime(int maxPrepMinutes);

    @Query("SELECT * FROM recipes WHERE cookTimeMinutes <= :maxCookMinutes ORDER BY cookTimeMinutes ASC")
    List<RecipeEntity> getByMaxCookTime(int maxCookMinutes);

    // ========== DIETARY QUERIES ==========

    @Query("SELECT * FROM recipes WHERE isVegan = 1 ORDER BY lastUpdated DESC")
    List<RecipeEntity> getVeganRecipes();

    @Transaction
    @Query("SELECT * FROM recipes WHERE isVegan = 1 ORDER BY lastUpdated DESC")
    List<RecipeWithNutrition> getVeganRecipesWithNutrition();

    @Query("SELECT * FROM recipes WHERE isVegetarian = 1 ORDER BY lastUpdated DESC")
    List<RecipeEntity> getVegetarianRecipes();

    @Transaction
    @Query("SELECT * FROM recipes WHERE isVegetarian = 1 ORDER BY lastUpdated DESC")
    List<RecipeWithNutrition> getVegetarianRecipesWithNutrition();

    @Query("SELECT * FROM recipes WHERE isGlutenFree = 1 ORDER BY lastUpdated DESC")
    List<RecipeEntity> getGlutenFreeRecipes();

    @Transaction
    @Query("SELECT * FROM recipes WHERE isGlutenFree = 1 ORDER BY lastUpdated DESC")
    List<RecipeWithNutrition> getGlutenFreeRecipesWithNutrition();

    @Query("SELECT * FROM recipes WHERE isDairyFree = 1 ORDER BY lastUpdated DESC")
    List<RecipeEntity> getDairyFreeRecipes();

    @Query("SELECT * FROM recipes WHERE isKeto = 1 ORDER BY lastUpdated DESC")
    List<RecipeEntity> getKetoRecipes();

    @Query("SELECT * FROM recipes WHERE isPaleo = 1 ORDER BY lastUpdated DESC")
    List<RecipeEntity> getPaleoRecipes();

    /**
     * Get recipes matching multiple dietary requirements
     */
    @Query("SELECT * FROM recipes WHERE " +
            "(:requireVegan IS NULL OR isVegan = :requireVegan) AND " +
            "(:requireVegetarian IS NULL OR isVegetarian = :requireVegetarian) AND " +
            "(:requireGlutenFree IS NULL OR isGlutenFree = :requireGlutenFree) AND " +
            "(:requireDairyFree IS NULL OR isDairyFree = :requireDairyFree) AND " +
            "(:requireKeto IS NULL OR isKeto = :requireKeto) AND " +
            "(:requirePaleo IS NULL OR isPaleo = :requirePaleo) " +
            "ORDER BY lastUpdated DESC")
    List<RecipeEntity> getByDietaryProfile(
            Boolean requireVegan,
            Boolean requireVegetarian,
            Boolean requireGlutenFree,
            Boolean requireDairyFree,
            Boolean requireKeto,
            Boolean requirePaleo
    );

    @Transaction
    @Query("SELECT * FROM recipes WHERE " +
            "(:requireVegan IS NULL OR isVegan = :requireVegan) AND " +
            "(:requireVegetarian IS NULL OR isVegetarian = :requireVegetarian) AND " +
            "(:requireGlutenFree IS NULL OR isGlutenFree = :requireGlutenFree) AND " +
            "(:requireDairyFree IS NULL OR isDairyFree = :requireDairyFree) AND " +
            "(:requireKeto IS NULL OR isKeto = :requireKeto) AND " +
            "(:requirePaleo IS NULL OR isPaleo = :requirePaleo) " +
            "ORDER BY lastUpdated DESC")
    List<RecipeWithNutrition> getByDietaryProfileWithNutrition(
            Boolean requireVegan,
            Boolean requireVegetarian,
            Boolean requireGlutenFree,
            Boolean requireDairyFree,
            Boolean requireKeto,
            Boolean requirePaleo
    );

    // ========== VISIBILITY & STATUS QUERIES ==========

    @Query("SELECT * FROM recipes WHERE isPublic = 1 ORDER BY lastUpdated DESC")
    List<RecipeEntity> getPublicRecipes();

    @Transaction
    @Query("SELECT * FROM recipes WHERE isPublic = 1 ORDER BY lastUpdated DESC")
    List<RecipeWithNutrition> getPublicRecipesWithNutrition();

    // Favorites: the pin lives in user_item_data (UserItemDataDao.setFavorite()).
    @Query("SELECT * FROM recipes WHERE id IN (" + UserItemDataEntity.FAVORITE_RECIPE_IDS + ")"
            + " ORDER BY lastUpdated DESC")
    List<RecipeEntity> getFavoriteRecipes();

    @Transaction
    @Query("SELECT * FROM recipes WHERE id IN (" + UserItemDataEntity.FAVORITE_RECIPE_IDS + ")"
            + " ORDER BY lastUpdated DESC")
    List<RecipeWithNutrition> getFavoriteRecipesWithNutrition();

    @Query("SELECT * FROM recipes WHERE isTemplate = 1 ORDER BY accessCount DESC, lastUpdated DESC")
    List<RecipeEntity> getTemplateRecipes();

    @Transaction
    @Query("SELECT * FROM recipes WHERE isTemplate = 1 ORDER BY accessCount DESC, lastUpdated DESC")
    List<RecipeWithNutrition> getTemplateRecipesWithNutrition();

    // ========== ACCESS TRACKING ==========
    // (Favorites: UserItemDataDao.setFavorite().)

    @Query("UPDATE recipes SET accessCount = accessCount + 1, lastUpdated = :currentTime WHERE id = :recipeId")
    int incrementAccessCount(String recipeId, long currentTime);

    @Query("SELECT * FROM recipes ORDER BY accessCount DESC, lastUpdated DESC LIMIT :limit")
    List<RecipeEntity> getMostAccessed(int limit);

    @Transaction
    @Query("SELECT * FROM recipes ORDER BY accessCount DESC, lastUpdated DESC LIMIT :limit")
    List<RecipeWithNutrition> getMostAccessedWithNutrition(int limit);

    // ========== RATING QUERIES ==========

    @Query("SELECT * FROM recipes WHERE rating >= :minRating ORDER BY rating DESC, ratingCount DESC LIMIT :limit")
    List<RecipeEntity> getTopRated(float minRating, int limit);

    @Transaction
    @Query("SELECT * FROM recipes WHERE rating >= :minRating ORDER BY rating DESC, ratingCount DESC LIMIT :limit")
    List<RecipeWithNutrition> getTopRatedWithNutrition(float minRating, int limit);

    @Query("UPDATE recipes SET rating = :rating, ratingCount = :ratingCount WHERE id = :recipeId")
    int updateRating(String recipeId, float rating, int ratingCount);

    // ========== SERVINGS QUERIES ==========

    @Query("SELECT * FROM recipes WHERE servings >= :minServings AND servings <= :maxServings ORDER BY lastUpdated DESC")
    List<RecipeEntity> getByServingRange(int minServings, int maxServings);

    @Query("SELECT * FROM recipes WHERE servings = :servings ORDER BY lastUpdated DESC")
    List<RecipeEntity> getByServings(int servings);

    // ========== CUISINE QUERIES ==========

    @Query("SELECT * FROM recipes WHERE cuisine = :cuisine ORDER BY lastUpdated DESC")
    List<RecipeEntity> getByCuisine(String cuisine);

    @Transaction
    @Query("SELECT * FROM recipes WHERE cuisine = :cuisine ORDER BY lastUpdated DESC")
    List<RecipeWithNutrition> getByCuisineWithNutrition(String cuisine);

    @Query("SELECT DISTINCT cuisine FROM recipes WHERE cuisine IS NOT NULL ORDER BY cuisine ASC")
    List<String> getAllCuisines();

    // ========== STATISTICS ==========

    @Query("SELECT COUNT(*) FROM recipes WHERE id IN (" + UserItemDataEntity.FAVORITE_RECIPE_IDS + ")")
    int getFavoriteCount();

    @Query("SELECT COUNT(*) FROM recipes WHERE isPublic = 1")
    int getPublicCount();

    @Query("SELECT COUNT(*) FROM recipes WHERE isTemplate = 1")
    int getTemplateCount();

    @Query("SELECT COUNT(*) FROM recipes WHERE authorId = :authorId")
    int getCountByAuthor(String authorId);

    @Query("SELECT AVG(rating) FROM recipes WHERE ratingCount > 0")
    Float getAverageRating();

    @Query("SELECT AVG(prepTimeMinutes + cookTimeMinutes) FROM recipes")
    Float getAverageTotalTime();

    @Query("SELECT " +
            "COUNT(*) as totalRecipes, " +
            "AVG(rating) as avgRating, " +
            "AVG(completenessScore) as avgCompleteness, " +
            "(SELECT COUNT(*) FROM user_item_data WHERE itemType = 'RECIPE' AND isFavorite = 1) as favoriteCount, " +
            "COUNT(CASE WHEN isPublic = 1 THEN 1 END) as publicCount " +
            "FROM recipes")
    RecipeStats getRecipeStats();

    // ========== LIVE DATA QUERIES ==========

    @Query("SELECT * FROM recipes WHERE id = :recipeId LIMIT 1")
    LiveData<RecipeEntity> getByIdLive(String recipeId);

    @Transaction
    @Query("SELECT * FROM recipes WHERE id = :recipeId LIMIT 1")
    LiveData<RecipeWithNutrition> getByIdWithNutritionLive(String recipeId);

    @Query("SELECT * FROM recipes ORDER BY lastUpdated DESC")
    LiveData<List<RecipeEntity>> getAllLive();

    @Query("SELECT * FROM recipes WHERE id IN (" + UserItemDataEntity.FAVORITE_RECIPE_IDS + ")"
            + " ORDER BY lastUpdated DESC")
    LiveData<List<RecipeEntity>> getFavoritesLive();

    @Query("SELECT COUNT(*) FROM recipes")
    LiveData<Integer> getCountLive();

    // ========== CACHE MAINTENANCE ==========

    @Query("SELECT * FROM recipes WHERE lastUpdated < :threshold ORDER BY accessCount ASC")
    List<RecipeEntity> getStaleRecipes(long threshold);

    /**
     * Recipes the user created are their own content, not cache: no cache
     * operation deletes them, whatever their pins.
     */
    String NOT_USER_CREATED = "dataSource != 'USER'";

    /**
     * Delete cached recipes not viewed since {@code threshold}, never touching
     * bulk-imported (localImport) rows, recipes with user data (favorite, custom
     * photo, preferred serving), recipes used by a meal, or user-created recipes.
     * Returns the number deleted.
     */
    @Query("DELETE FROM recipes WHERE lastViewed < :threshold AND localImport = 0"
            + " AND id NOT IN (" + UserItemDataEntity.KEPT_RECIPE_IDS + ")"
            + " AND id NOT IN (" + MealPortionEntity.USED_RECIPE_IDS + ")"
            + " AND " + NOT_USER_CREATED)
    int deleteExpiredRecipes(long threshold);

    @Query("DELETE FROM recipes WHERE lastUpdated < :threshold AND accessCount < :minAccessCount"
            + " AND id NOT IN (" + UserItemDataEntity.KEPT_RECIPE_IDS + ")"
            + " AND id NOT IN (" + MealPortionEntity.USED_RECIPE_IDS + ")"
            + " AND " + NOT_USER_CREATED)
    int deleteStaleRecipes(long threshold, int minAccessCount);

    // ========== CACHE MANAGEMENT (settings) ==========
    // Mirror of FoodProductDao's section (same pins, same rules), plus:
    // user-created recipes are never deleted. See CacheRepository.

    /** Count of browsed-cache recipes - opened from search, held by no pin. */
    @Query("SELECT COUNT(*) FROM recipes WHERE localImport = 0"
            + " AND id NOT IN (" + UserItemDataEntity.KEPT_RECIPE_IDS + ")"
            + " AND id NOT IN (" + MealPortionEntity.USED_RECIPE_IDS + ")"
            + " AND " + NOT_USER_CREATED)
    int getBrowsedCacheCount();

    /**
     * Count of recipes belonging to one source's downloaded dataset. Inert until a
     * recipe source is importable.
     */
    @Query("SELECT COUNT(*) FROM recipes WHERE sourceId = :sourceId AND localImport = 1")
    int getDownloadCountBySource(String sourceId);

    /** Section 1 - delete the browsed cache: rows held by no pin. */
    @Query("DELETE FROM recipes WHERE localImport = 0"
            + " AND id NOT IN (" + UserItemDataEntity.KEPT_RECIPE_IDS + ")"
            + " AND id NOT IN (" + MealPortionEntity.USED_RECIPE_IDS + ")"
            + " AND " + NOT_USER_CREATED)
    int deleteBrowsedCache();

    /**
     * Section 2, part A - delete rows held ONLY by the favorite pin, before the
     * pins are removed with UserItemDataDao.clearAllFavorites().
     */
    @Query("DELETE FROM recipes WHERE localImport = 0"
            + " AND id IN (" + UserItemDataEntity.FAVORITE_RECIPE_IDS + ")"
            + " AND id NOT IN (" + UserItemDataEntity.CUSTOMISED_RECIPE_IDS + ")"
            + " AND id NOT IN (" + MealPortionEntity.USED_RECIPE_IDS + ")"
            + " AND " + NOT_USER_CREATED)
    int deleteFavoritesOnly();

    /** Section 3, part A - for one source, delete dataset rows held by nothing else. */
    @Query("DELETE FROM recipes WHERE sourceId = :sourceId AND localImport = 1"
            + " AND id NOT IN (" + UserItemDataEntity.KEPT_RECIPE_IDS + ")"
            + " AND id NOT IN (" + MealPortionEntity.USED_RECIPE_IDS + ")"
            + " AND " + NOT_USER_CREATED)
    int deleteDownloadsOnlyBySource(String sourceId);

    /** Section 3, part B - for one source, the remaining dataset rows lose the import pin. */
    @Query("UPDATE recipes SET localImport = 0 WHERE sourceId = :sourceId AND localImport = 1")
    int unmarkDownloadsBySource(String sourceId);

    /**
     * Clear all, part B (after the favorite pins are removed) - delete every row
     * not held by a customisation or a meal, and not user-created.
     */
    @Query("DELETE FROM recipes WHERE id NOT IN (" + UserItemDataEntity.KEPT_RECIPE_IDS + ")"
            + " AND id NOT IN (" + MealPortionEntity.USED_RECIPE_IDS + ")"
            + " AND " + NOT_USER_CREATED)
    int deleteAllExceptKept();

    /** Clear all, part C - the rows that remain lose the import pin. */
    @Query("UPDATE recipes SET localImport = 0 WHERE localImport = 1")
    int unmarkAllDownloads();

    @Query("UPDATE recipes SET lastUpdated = :timestamp WHERE id = :recipeId")
    int updateLastUpdated(String recipeId, long timestamp);

    /**
     * Returns all non-null local image paths for recipes.
     *
     * Covers the auto-cached path columns:
     *   thumbnailPath     - auto-cached thumbnail (downloaded on favorite)
     *   imagePath         - auto-cached full-size hero
     * Custom photos set by the user live in user_item_data
     * (UserItemDataDao.getAllCustomImagePaths()).
     *
     * Does NOT cover step photos - those are extracted from the stepStructure
     * JSON blob separately via getAllWithStepStructure() + extractStepPhotoPaths().
     */
    @Query("SELECT thumbnailPath FROM recipes WHERE thumbnailPath IS NOT NULL "
            + "UNION ALL "
            + "SELECT imagePath FROM recipes WHERE imagePath IS NOT NULL")
    List<String> getAllLocalImagePaths();

    /**
     * Returns all recipe entities that have a non-null stepStructure column.
     *
     * Used by ImagePurgeManager to scan step photos: Room's
     * RecipeStepMetadataListConverter automatically deserialises stepStructure
     * into List<RecipeStepMetadata> when getStepStructure() is called,
     * so no manual JSON parsing is needed in the purge manager.
     *
     * Only recipes with non-null stepStructure are loaded, avoiding unnecessary
     * object allocation for the majority of simple recipes.
     *
     * Added in: database v11 (image system migration)
     */
    @Query("SELECT * FROM recipes WHERE stepStructure IS NOT NULL")
    List<RecipeEntity> getAllWithStepStructure();

    /**
     * Updates the locally cached thumbnail path for a recipe.
     *
     * Called by RecipeRepository after a thumbnail download completes
     * (on favorite) or to clear the path (on unfavorite, pass null).
     *
     * Added in: database v12 (image system)
     *
     * @param recipeId Source-qualified recipe ID (primary key).
     * @param path     Absolute local file path, or null to clear.
     */
    @Query("UPDATE recipes SET thumbnailPath = :path WHERE id = :recipeId")
    int updateThumbnailPath(String recipeId, String path);

    /**
     * Updates the auto-cached full-size (hero) image path for a recipe.
     * Called by the repositories' cacheFavoriteImages via ImageDownloader when a
     * favorite's hero is cached to disk. Must be called from a background thread.
     */
    @Query("UPDATE recipes SET imagePath = :path WHERE id = :recipeId")
    int updateImagePath(String recipeId, String path);

    // Custom photos: UserItemDataDao.setImagePath() / setThumbnailPath().

    // ========== INNER CLASS FOR STATISTICS ==========

    /**
     * Statistics result class
     */
    class RecipeStats {
        public int totalRecipes;
        public float avgRating;
        public float avgCompleteness;
        public int favoriteCount;
        public int publicCount;
    }
}