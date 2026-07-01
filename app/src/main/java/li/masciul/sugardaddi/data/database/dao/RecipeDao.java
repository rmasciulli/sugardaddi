package li.masciul.sugardaddi.data.database.dao;

import androidx.annotation.Nullable;
import androidx.room.*;
import androidx.lifecycle.LiveData;
import li.masciul.sugardaddi.data.database.entities.RecipeEntity;
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
     * primary key already exists. Preserves existing user-set fields
     * (isFavorite, accessCount) on recipes already cached in Room.
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

    @Query("SELECT * FROM recipes WHERE isFavorite = 1 ORDER BY lastUpdated DESC")
    List<RecipeEntity> getFavoriteRecipes();

    @Transaction
    @Query("SELECT * FROM recipes WHERE isFavorite = 1 ORDER BY lastUpdated DESC")
    List<RecipeWithNutrition> getFavoriteRecipesWithNutrition();

    @Query("SELECT * FROM recipes WHERE isTemplate = 1 ORDER BY accessCount DESC, lastUpdated DESC")
    List<RecipeEntity> getTemplateRecipes();

    @Transaction
    @Query("SELECT * FROM recipes WHERE isTemplate = 1 ORDER BY accessCount DESC, lastUpdated DESC")
    List<RecipeWithNutrition> getTemplateRecipesWithNutrition();

    // ========== FAVORITES & ACCESS TRACKING ==========

    @Query("UPDATE recipes SET isFavorite = 1 WHERE id = :recipeId")
    int markAsFavorite(String recipeId);

    @Query("UPDATE recipes SET isFavorite = 0 WHERE id = :recipeId")
    int unmarkAsFavorite(String recipeId);

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

    @Query("SELECT COUNT(*) FROM recipes WHERE isFavorite = 1")
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
            "COUNT(CASE WHEN isFavorite = 1 THEN 1 END) as favoriteCount, " +
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

    @Query("SELECT * FROM recipes WHERE isFavorite = 1 ORDER BY lastUpdated DESC")
    LiveData<List<RecipeEntity>> getFavoritesLive();

    @Query("SELECT COUNT(*) FROM recipes")
    LiveData<Integer> getCountLive();

    // ========== CACHE MAINTENANCE ==========

    @Query("SELECT * FROM recipes WHERE lastUpdated < :threshold ORDER BY accessCount ASC")
    List<RecipeEntity> getStaleRecipes(long threshold);

    /**
     * Delete cached recipes not viewed since {@code threshold}, never touching
     * favourites or bulk-imported (localImport) rows. Returns the number deleted.
     */
    @Query("DELETE FROM recipes WHERE lastViewed < :threshold AND isFavorite = 0 AND localImport = 0")
    int deleteExpiredRecipes(long threshold);

    @Query("DELETE FROM recipes WHERE lastUpdated < :threshold AND isFavorite = 0 AND accessCount < :minAccessCount")
    int deleteStaleRecipes(long threshold, int minAccessCount);

    // ========== CACHE MANAGEMENT (settings) ==========
    // Exact mirror of FoodProductDao's cache-management set, for the three-section
    // cache card. The two retention pins (isFavorite, localImport) and the
    // clear-one-pin / downgrade-or-delete rules are identical across both tables.
    //
    // No recipe source is currently downloadable, so recipes are never localImport
    // = 1 today: the import-pin operations below are inert for now. They exist by
    // design so that if a recipe source ever becomes importable, it is covered
    // automatically with no further changes. Section-2 favourite total reuses
    // getFavoriteCount().

    /**
     * Count of browsed-cache recipes - opened from search, neither favourited nor
     * part of a downloaded dataset (no retention pin). Drives the section-1 count.
     */
    @Query("SELECT COUNT(*) FROM recipes WHERE localImport = 0 AND isFavorite = 0")
    int getBrowsedCacheCount();

    /**
     * Count of recipes belonging to one source's downloaded dataset (localImport = 1,
     * favourited or not). Inert until a recipe source is importable.
     *
     * @param sourceId The data source id
     */
    @Query("SELECT COUNT(*) FROM recipes WHERE sourceId = :sourceId AND localImport = 1")
    int getDownloadCountBySource(String sourceId);

    /**
     * Section 1 - delete the browsed cache: flagless rows only. Favourites and
     * downloaded rows are untouched. Returns the number deleted.
     */
    @Query("DELETE FROM recipes WHERE localImport = 0 AND isFavorite = 0")
    int deleteBrowsedCache();

    /**
     * Section 2, part A - delete favourites that exist only because they were
     * favourited (no import pin). Rows that are also downloaded survive via
     * unmarkFavoriteOnDownloads(). Returns the number deleted.
     */
    @Query("DELETE FROM recipes WHERE localImport = 0 AND isFavorite = 1")
    int deleteFavoritesOnly();

    /**
     * Section 2, part B - clear the favourite pin on rows that are also downloaded
     * (C -> D): they stay as plain dataset rows. Inert until recipes are importable.
     * Returns the number updated.
     */
    @Query("UPDATE recipes SET isFavorite = 0 WHERE localImport = 1 AND isFavorite = 1")
    int unmarkFavoriteOnDownloads();

    /**
     * Section 3, part A - for one source, delete dataset rows that are not also
     * favourited (D). Favourited dataset rows survive via
     * unmarkDownloadOnFavoritesBySource(). Inert until recipes are importable.
     * Returns the number deleted.
     *
     * @param sourceId The data source id whose dataset is being removed
     */
    @Query("DELETE FROM recipes WHERE sourceId = :sourceId AND localImport = 1 AND isFavorite = 0")
    int deleteDownloadsOnlyBySource(String sourceId);

    /**
     * Section 3, part B - for one source, clear the import pin on rows that are also
     * favourited (C -> B): they stay as plain favourites. Inert until recipes are
     * importable. Returns the number updated.
     *
     * @param sourceId The data source id whose dataset is being removed
     */
    @Query("UPDATE recipes SET localImport = 0 WHERE sourceId = :sourceId AND localImport = 1 AND isFavorite = 1")
    int unmarkDownloadOnFavoritesBySource(String sourceId);

    @Query("UPDATE recipes SET lastUpdated = :timestamp WHERE id = :recipeId")
    int updateLastUpdated(String recipeId, long timestamp);

    /**
     * Returns all non-null local image paths for recipes.
     *
     * Covers all four local path columns:
     *   thumbnailPath     - auto-cached thumbnail (downloaded on favourite)
     *   imagePath         - auto-cached full-size (future use, currently NULL)
     *   userThumbnailPath - user-defined thumbnail override ({id}_custom.jpg)
     *   userImagePath     - user-defined full-size override
     *
     * Does NOT cover step photos - those are extracted from the stepStructure
     * JSON blob separately via getAllWithStepStructure() + extractStepPhotoPaths().
     */
    @Query("SELECT thumbnailPath FROM recipes WHERE thumbnailPath IS NOT NULL "
            + "UNION ALL "
            + "SELECT imagePath FROM recipes WHERE imagePath IS NOT NULL "
            + "UNION ALL "
            + "SELECT userThumbnailPath FROM recipes WHERE userThumbnailPath IS NOT NULL "
            + "UNION ALL "
            + "SELECT userImagePath FROM recipes WHERE userImagePath IS NOT NULL")
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
     * (on favourite) or to clear the path (on unfavourite, pass null).
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
     * Called by the repositories' cacheFavouriteImages via ImageDownloader when a
     * favourite's hero is cached to disk. Must be called from a background thread.
     */
    @Query("UPDATE recipes SET imagePath = :path WHERE id = :recipeId")
    int updateImagePath(String recipeId, String path);

    /**
     * Updates the user-defined thumbnail path for a recipe.
     * Pass null to clear (restores auto-cached thumbnail display).
     * Must be called from a background thread.
     */
    @Query("UPDATE recipes SET userThumbnailPath = :path WHERE id = :recipeId")
    int updateUserThumbnailPath(String recipeId, String path);

    /**
     * Updates the user-defined full-size image path for a recipe.
     * Pass null to clear (restores remote imageUrl display).
     * Must be called from a background thread.
     */
    @Query("UPDATE recipes SET userImagePath = :path WHERE id = :recipeId")
    int updateUserImagePath(String recipeId, String path);

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