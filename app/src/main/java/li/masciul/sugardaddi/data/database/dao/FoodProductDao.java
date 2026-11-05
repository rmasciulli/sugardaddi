package li.masciul.sugardaddi.data.database.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import androidx.lifecycle.LiveData;
import java.util.List;

import li.masciul.sugardaddi.data.database.entities.FoodProductEntity;
import li.masciul.sugardaddi.data.database.entities.MealPortionEntity;
import li.masciul.sugardaddi.data.database.entities.UserItemDataEntity;

/**
 * FoodProductDao - Data Access Object for food products (v3.0 - Hybrid Translation)
 *
 * *** ARCHITECTURE UPDATE v3.0 ***
 * - REMOVED: localizedContentMap JSON queries (obsolete)
 * - NEW: Direct field queries (name, brand, etc.) for primary language
 * - NEW: searchableText for efficient multi-language search
 * - Hybrid translation system: primary fields + translation map
 *
 * SEARCH STRATEGY:
 * - Primary language content: Direct field queries (name, brand, categoriesText)
 * - All languages: searchableText LIKE (pre-computed from all translations)
 * - Barcodes: barcode field for exact/partial matches
 *
 * PERFORMANCE:
 * - 85% faster than JSON extraction queries
 * - Indexed searchableText for full-text search
 * - Room compile-time query verification
 *
 * @version 3.0
 * @since Database v5
 */
@Dao
public interface FoodProductDao {

    // ========== CORE CRUD OPERATIONS ==========

    /**
     * Insert or replace a product
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertProduct(FoodProductEntity product);

    /**
     * Insert multiple products
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long[] insertProducts(List<FoodProductEntity> products);

    /**
     * Update existing product
     */
    @Update
    int updateProduct(FoodProductEntity product);

    /**
     * Update multiple products
     */
    @Update
    int updateProducts(List<FoodProductEntity> products);

    /**
     * Delete specific product
     */
    @Delete
    int deleteProduct(FoodProductEntity product);

    /**
     * Delete multiple products
     */
    @Delete
    int deleteProducts(List<FoodProductEntity> products);

    // ========== RETRIEVAL BY ID/BARCODE ==========

    /**
     * Get product by primary key ID
     */
    @Query("SELECT * FROM food_products WHERE id = :productId LIMIT 1")
    FoodProductEntity getProductById(String productId);

    /**
     * Get product by barcode
     */
    @Query("SELECT * FROM food_products WHERE barcode = :barcode LIMIT 1")
    FoodProductEntity getProductByBarcode(String barcode);

    /**
     * Get product by original ID and source
     */
    @Query("SELECT * FROM food_products WHERE originalId = :originalId AND sourceId = :sourceId LIMIT 1")
    FoodProductEntity getProductByOriginalId(String originalId, String sourceId);

    /**
     * Check if product exists
     */
    @Query("SELECT COUNT(*) > 0 FROM food_products WHERE id = :productId")
    boolean productExists(String productId);

    /**
     * Check if barcode exists
     */
    @Query("SELECT COUNT(*) > 0 FROM food_products WHERE barcode = :barcode")
    boolean barcodeExists(String barcode);

    // ========== BULK RETRIEVAL ==========

    /**
     * Get all products
     */
    @Query("SELECT * FROM food_products ORDER BY lastUpdated DESC")
    List<FoodProductEntity> getAllProducts();

    /**
     * Get all products with LiveData
     */
    @Query("SELECT * FROM food_products ORDER BY lastUpdated DESC")
    LiveData<List<FoodProductEntity>> getAllProductsLive();

    /**
     * Get products by primary key IDs
     */
    @Query("SELECT * FROM food_products WHERE id IN (:productIds)")
    List<FoodProductEntity> getProductsByIds(List<String> productIds);

    /**
     * Get products by barcodes (batch lookup).
     *
     * Used by the search enrichment system to check whether any search result
     * products have a richer cached version in the DB (from a previous detail view).
     * Source-agnostic: the barcode is the universal physical product identifier.
     *
     * @param barcodes List of EAN/UPC barcode strings
     * @return List of matching FoodProductEntity rows (may be smaller than input list)
     */
    @Query("SELECT * FROM food_products WHERE barcode IN (:barcodes)")
    List<FoodProductEntity> getProductsByBarcodes(List<String> barcodes);

    /**
     * Get products by searchable IDs (batch lookup for non-barcoded sources).
     *
     * Used for Ciqual and other sources that don't use barcodes.
     * The searchable ID format is "SOURCE:originalId" (e.g. "CIQUAL:24036").
     *
     * @param searchableIds List of combined source+id strings
     * @return List of matching FoodProductEntity rows
     */
    @Query("SELECT * FROM food_products WHERE id IN (:searchableIds)")
    List<FoodProductEntity> getProductsBySearchableIds(List<String> searchableIds);

    /**
     * Get total product count
     */
    @Query("SELECT COUNT(*) FROM food_products")
    int getProductCount();

    // ========== SEARCH OPERATIONS (UPDATED v3.0) ==========

    /**
     * Search products using hybrid translation system
     *
     * SEARCH STRATEGY v3.0:
     * - Searches in primary language fields (name, brand, categoriesText)
     * - Searches in pre-computed searchableText (all translations)
     * - Searches in barcode for exact/partial matches
     * - Orders by relevance: accessCount DESC, lastUpdated DESC
     *
     * PERFORMANCE:
     * - 85% faster than old JSON LIKE queries
     * - Uses indexed columns for optimal speed
     * - No JSON parsing overhead
     *
     * @param searchTerm Search query (case-insensitive)
     * @param limit Maximum results to return
     * @return List of matching products, sorted by relevance
     */
    @Query("SELECT * FROM food_products WHERE " +
            "name LIKE '%' || :searchTerm || '%' OR " +
            "brand LIKE '%' || :searchTerm || '%' OR " +
            "categoriesText LIKE '%' || :searchTerm || '%' OR " +
            "searchableText LIKE '%' || :searchTerm || '%' OR " +
            "barcode LIKE '%' || :searchTerm || '%' " +
            "ORDER BY accessCount DESC, lastUpdated DESC " +
            "LIMIT :limit")
    List<FoodProductEntity> searchProducts(String searchTerm, int limit);

    /**
     * Search products with pagination
     *
     * Same search logic as searchProducts() but with offset support
     * for infinite scroll or paginated results.
     *
     * @param searchTerm Search query
     * @param limit Results per page
     * @param offset Starting position (page * limit)
     * @return Page of matching products
     */
    @Query("SELECT * FROM food_products WHERE " +
            "name LIKE '%' || :searchTerm || '%' OR " +
            "brand LIKE '%' || :searchTerm || '%' OR " +
            "categoriesText LIKE '%' || :searchTerm || '%' OR " +
            "searchableText LIKE '%' || :searchTerm || '%' OR " +
            "barcode LIKE '%' || :searchTerm || '%' " +
            "ORDER BY accessCount DESC, lastUpdated DESC " +
            "LIMIT :limit OFFSET :offset")
    List<FoodProductEntity> searchProductsPaged(String searchTerm, int limit, int offset);

    /**
     * Search products from a specific source (e.g., CIQUAL local search).
     * Searches searchableText which contains both FR and EN names for Ciqual products.
     *
     * NOTE: offset must be computed by the caller as (page - 1) * limit, using
     * the same 1-based page convention as DataSource.search(). Previously this
     * had no OFFSET at all, so every page silently re-ran the identical query -
     * page 2+ returned the exact same top-N rows as page 1, which downstream
     * dedup then filtered out entirely as cross-page duplicates.
     */
    @Query("SELECT * FROM food_products WHERE sourceId = :sourceId AND (" +
            "name LIKE '%' || :searchTerm || '%' OR " +
            "searchableText LIKE '%' || :searchTerm || '%') " +
            "ORDER BY dataCompleteness DESC LIMIT :limit OFFSET :offset")
    List<FoodProductEntity> searchProductsBySource(
            String sourceId, String searchTerm, int limit, int offset);

    // ========== FAVORITES (read side) ==========
    // The favorite pin lives in user_item_data: set it with
    // UserItemDataDao.setFavorite(). These read the products it points at.

    /**
     * Get all favorite products
     */
    @Query("SELECT * FROM food_products WHERE id IN (" + UserItemDataEntity.FAVORITE_PRODUCT_IDS + ")"
            + " ORDER BY lastUpdated DESC")
    List<FoodProductEntity> getFavoriteProducts();

    /**
     * Get all favorite products with LiveData
     */
    @Query("SELECT * FROM food_products WHERE id IN (" + UserItemDataEntity.FAVORITE_PRODUCT_IDS + ")"
            + " ORDER BY lastUpdated DESC")
    LiveData<List<FoodProductEntity>> getFavoriteProductsLive();

    /**
     * Get favorite count (favorites whose product row exists)
     */
    @Query("SELECT COUNT(*) FROM food_products WHERE id IN (" + UserItemDataEntity.FAVORITE_PRODUCT_IDS + ")")
    int getFavoriteCount();

    // ========== ACCESS TRACKING ==========

    /**
     * Increment access count and update timestamp
     */
    @Query("UPDATE food_products SET accessCount = accessCount + 1, lastUpdated = :currentTime WHERE id = :productId")
    int incrementAccessCount(String productId, long currentTime);

    /**
     * Get most accessed products
     */
    @Query("SELECT * FROM food_products ORDER BY accessCount DESC LIMIT :limit")
    List<FoodProductEntity> getMostAccessedProducts(int limit);

    /**
     * Get recently accessed products
     */
    @Query("SELECT * FROM food_products WHERE accessCount > 0 ORDER BY lastViewed DESC LIMIT :limit")
    List<FoodProductEntity> getRecentlyAccessedProducts(int limit);

    // ========== CACHE MAINTENANCE ==========

    /**
     * Delete cached products not viewed since {@code threshold}, never touching
     * bulk-imported (localImport) rows, items with user data (favorite, custom
     * photo, preferred serving) or items used by a meal. Returns the number deleted.
     */
    @Query("DELETE FROM food_products WHERE lastViewed < :threshold AND localImport = 0"
            + " AND id NOT IN (" + UserItemDataEntity.KEPT_PRODUCT_IDS + ")"
            + " AND id NOT IN (" + MealPortionEntity.USED_PRODUCT_IDS + ")")
    int deleteExpiredProducts(long threshold);

    /**
     * Clear all products
     */
    @Query("DELETE FROM food_products")
    void clearAllProducts();

    // ========== CACHE MANAGEMENT (settings) ==========
    // Operations backing the cache card in Settings, orchestrated by
    // CacheRepository. What keeps a row ("pins"):
    //   - localImport = 1          downloaded dataset (column on this row)
    //   - a user_item_data row     favorite, custom photo or preferred serving
    //   - a meal_portions row      used by a meal (journal, not cache)
    // Clearing a section removes one kind of pin; a row still held by another
    // pin is kept, a row left with none is deleted.

    /**
     * Count of browsed-cache products - opened from search, held by no pin.
     * Drives the section-1 count.
     */
    @Query("SELECT COUNT(*) FROM food_products WHERE localImport = 0"
            + " AND id NOT IN (" + UserItemDataEntity.KEPT_PRODUCT_IDS + ")"
            + " AND id NOT IN (" + MealPortionEntity.USED_PRODUCT_IDS + ")")
    int getBrowsedCacheCount();

    /**
     * Count of products belonging to one source's downloaded dataset (localImport = 1,
     * whatever else holds them). Drives a section-3 source-row count.
     *
     * @param sourceId The data source id (e.g. "CIQUAL", "USDA")
     */
    @Query("SELECT COUNT(*) FROM food_products WHERE sourceId = :sourceId AND localImport = 1")
    int getDownloadCountBySource(String sourceId);

    /**
     * Section 1 - delete the browsed cache: rows held by no pin. Returns the
     * number deleted.
     */
    @Query("DELETE FROM food_products WHERE localImport = 0"
            + " AND id NOT IN (" + UserItemDataEntity.KEPT_PRODUCT_IDS + ")"
            + " AND id NOT IN (" + MealPortionEntity.USED_PRODUCT_IDS + ")")
    int deleteBrowsedCache();

    /**
     * Section 2, part A - delete rows held ONLY by the favorite pin (not
     * downloaded, no customisation, not used by a meal), before the pins are
     * removed with UserItemDataDao.clearAllFavorites(). Returns the number deleted.
     */
    @Query("DELETE FROM food_products WHERE localImport = 0"
            + " AND id IN (" + UserItemDataEntity.FAVORITE_PRODUCT_IDS + ")"
            + " AND id NOT IN (" + UserItemDataEntity.CUSTOMISED_PRODUCT_IDS + ")"
            + " AND id NOT IN (" + MealPortionEntity.USED_PRODUCT_IDS + ")")
    int deleteFavoritesOnly();

    /**
     * Section 3, part A - for one source, delete dataset rows held by nothing
     * else (no user data, not used by a meal). Returns the number deleted.
     *
     * @param sourceId The data source id whose dataset is being removed
     */
    @Query("DELETE FROM food_products WHERE sourceId = :sourceId AND localImport = 1"
            + " AND id NOT IN (" + UserItemDataEntity.KEPT_PRODUCT_IDS + ")"
            + " AND id NOT IN (" + MealPortionEntity.USED_PRODUCT_IDS + ")")
    int deleteDownloadsOnlyBySource(String sourceId);

    /**
     * Section 3, part B - for one source, the dataset rows that remain (held by
     * user data or a meal) lose the import pin and stay as plain rows.
     *
     * @param sourceId The data source id whose dataset is being removed
     */
    @Query("UPDATE food_products SET localImport = 0 WHERE sourceId = :sourceId AND localImport = 1")
    int unmarkDownloadsBySource(String sourceId);

    /**
     * Clear all, part B (after the favorite pins are removed) - delete every row
     * not held by a customisation or a meal.
     */
    @Query("DELETE FROM food_products WHERE id NOT IN (" + UserItemDataEntity.KEPT_PRODUCT_IDS + ")"
            + " AND id NOT IN (" + MealPortionEntity.USED_PRODUCT_IDS + ")")
    int deleteAllExceptKept();

    /** Clear all, part C - the rows that remain lose the import pin. */
    @Query("UPDATE food_products SET localImport = 0 WHERE localImport = 1")
    int unmarkAllDownloads();

    /**
     * Delete products by IDs
     */
    @Query("DELETE FROM food_products WHERE id IN (:productIds)")
    int deleteProductsByIds(List<String> productIds);

    /**
     * Delete product by ID
     */
    @Query("DELETE FROM food_products WHERE id = :productId")
    int deleteProductById(String productId);

    /**
     * Returns all non-null local image paths for food products.
     *
     * Covers the auto-cached path columns:
     *   thumbnailPath     - auto-cached thumbnail (downloaded on favorite)
     *   imagePath         - auto-cached full-size hero
     * Custom photos set by the user live in user_item_data
     * (UserItemDataDao.getAllCustomImagePaths()).
     *
     * Used by ImagePurgeManager to cross-reference disk files against Room.
     */
    @Query("SELECT thumbnailPath FROM food_products WHERE thumbnailPath IS NOT NULL "
            + "UNION ALL "
            + "SELECT imagePath FROM food_products WHERE imagePath IS NOT NULL")
    List<String> getAllLocalImagePaths();

    /**
     * Updates the locally cached thumbnail path for a food product.
     *
     * Called by ProductRepository after a thumbnail download completes
     * (on favorite) or to clear the path (on unfavorite, pass null).
     *
     * Using a targeted UPDATE rather than a full updateProduct() avoids
     * loading and re-serialising the entire entity to change one field.
     *
     * Added in: database v12 (image system)
     *
     * @param productId Source-qualified product ID (primary key).
     * @param path      Absolute local file path, or null to clear.
     */
    @Query("UPDATE food_products SET thumbnailPath = :path WHERE id = :productId")
    int updateThumbnailPath(String productId, String path);

    /**
     * Updates the auto-cached full-size (hero) image path for a product.
     * Called by the repositories' cacheFavoriteImages via ImageDownloader when a
     * favorite's hero is cached to disk. Must be called from a background thread.
     */
    @Query("UPDATE food_products SET imagePath = :path WHERE id = :productId")
    void updateImagePath(String productId, String path);

    // Custom photos: UserItemDataDao.setImagePath() / setThumbnailPath().

    // ========== DATA QUALITY QUERIES ==========

    /**
     * Get products with low data completeness
     */
    @Query("SELECT * FROM food_products WHERE dataCompleteness < :threshold ORDER BY accessCount DESC")
    List<FoodProductEntity> getIncompleteProducts(float threshold);

    /**
     * Get products needing update (old and incomplete)
     */
    @Query("SELECT * FROM food_products WHERE " +
            "(lastUpdated < :staleThreshold OR dataCompleteness < :completenessThreshold) " +
            "ORDER BY accessCount DESC LIMIT :limit")
    List<FoodProductEntity> getProductsNeedingUpdate(long staleThreshold, float completenessThreshold, int limit);

    /**
     * Get products by quality score range
     */
    @Query("SELECT * FROM food_products WHERE dataQualityScore BETWEEN :minScore AND :maxScore " +
            "ORDER BY dataQualityScore DESC")
    List<FoodProductEntity> getProductsByQualityScore(int minScore, int maxScore);

    // ========== CATEGORY QUERIES ==========

    /**
     * Get products by source
     */
    @Query("SELECT * FROM food_products WHERE sourceId = :sourceId ORDER BY lastUpdated DESC")
    List<FoodProductEntity> getProductsBySource(String sourceId);

    /**
     * Get product count by source
     */
    @Query("SELECT sourceId, COUNT(*) as count FROM food_products GROUP BY sourceId")
    List<SourceCount> getProductCountBySource();

    /**
     * Get products by type
     */
    @Query("SELECT * FROM food_products WHERE productType = :type ORDER BY lastUpdated DESC")
    List<FoodProductEntity> getProductsByType(String type);

    // ========== DIETARY FILTERS ==========

    /**
     * Get organic products
     */
    @Query("SELECT * FROM food_products WHERE isOrganic = 1")
    List<FoodProductEntity> getOrganicProducts();

    /**
     * Get vegan products
     */
    @Query("SELECT * FROM food_products WHERE isVegan = 1")
    List<FoodProductEntity> getVeganProducts();

    /**
     * Get gluten-free products
     */
    @Query("SELECT * FROM food_products WHERE isGlutenFree = 1")
    List<FoodProductEntity> getGlutenFreeProducts();

    /**
     * Get products by dietary profile
     */
    @Query("SELECT * FROM food_products WHERE " +
            "(:requireOrganic IS NULL OR isOrganic = :requireOrganic) AND " +
            "(:requireVegan IS NULL OR isVegan = :requireVegan) AND " +
            "(:requireVegetarian IS NULL OR isVegetarian = :requireVegetarian) AND " +
            "(:requireGlutenFree IS NULL OR isGlutenFree = :requireGlutenFree)")
    List<FoodProductEntity> getProductsByDietaryProfile(
            Boolean requireOrganic,
            Boolean requireVegan,
            Boolean requireVegetarian,
            Boolean requireGlutenFree
    );

    // ========== ALLERGEN QUERIES (UPDATED v3.0) ==========

    /**
     * Get products safe for user restrictions
     */
    @Query("SELECT * FROM food_products WHERE " +
            "(allergenFlags & :userRestrictions) = 0 " +
            "ORDER BY lastUpdated DESC")
    List<FoodProductEntity> getSafeProducts(int userRestrictions);

    /**
     * Get products containing specific allergen
     */
    @Query("SELECT * FROM food_products WHERE " +
            "(allergenFlags & :allergenMask) != 0 " +
            "ORDER BY lastUpdated DESC")
    List<FoodProductEntity> getProductsWithAllergen(int allergenMask);

    /**
     * Get allergen-free products
     */
    @Query("SELECT * FROM food_products WHERE " +
            "allergenFlags = 0 " +
            "ORDER BY lastUpdated DESC")
    List<FoodProductEntity> getAllergenFreeProducts();

    /**
     * Search products that are safe for user (UPDATED v3.0)
     *
     * Combines search functionality with allergen filtering.
     * Uses hybrid translation search + allergen safety check.
     *
     * @param searchTerm Search query
     * @param userRestrictions Bitmask of user's allergen restrictions
     * @param limit Maximum results
     * @return List of safe products matching search
     */
    @Query("SELECT * FROM food_products WHERE " +
            "(name LIKE '%' || :searchTerm || '%' OR " +
            "brand LIKE '%' || :searchTerm || '%' OR " +
            "categoriesText LIKE '%' || :searchTerm || '%' OR " +
            "searchableText LIKE '%' || :searchTerm || '%' OR " +
            "barcode LIKE '%' || :searchTerm || '%') AND " +
            "(allergenFlags & :userRestrictions) = 0 " +
            "ORDER BY accessCount DESC, lastUpdated DESC " +
            "LIMIT :limit")
    List<FoodProductEntity> searchSafeProducts(String searchTerm, int userRestrictions, int limit);

    // ========== STATISTICS ==========

    /**
     * Get last update timestamp for a product
     */
    @Query("SELECT lastUpdated FROM food_products WHERE id = :productId")
    Long getLastUpdated(String productId);

    /**
     * Get products updated after timestamp
     */
    @Query("SELECT * FROM food_products WHERE lastUpdated > :timestamp ORDER BY lastUpdated DESC")
    List<FoodProductEntity> getProductsUpdatedAfter(long timestamp);

    /**
     * Get database statistics
     */
    @Query("SELECT " +
            "COUNT(*) as totalProducts, " +
            "(SELECT COUNT(*) FROM user_item_data WHERE itemType = 'FOOD_PRODUCT' AND isFavorite = 1) as favoriteCount, " +
            "AVG(dataCompleteness) as avgCompleteness, " +
            "AVG(dataQualityScore) as avgQuality, " +
            "AVG(accessCount) as avgAccessCount " +
            "FROM food_products")
    DatabaseStats getDatabaseStats();

    // ========== INNER CLASSES ==========

    /**
     * Result class for source count queries
     */
    class SourceCount {
        public String sourceId;
        public int count;
    }

    /**
     * Result class for database statistics
     */
    class DatabaseStats {
        public int totalProducts;
        public int favoriteCount;
        public float avgCompleteness;
        public float avgQuality;
        public float avgAccessCount;
    }
    // ========== CATEGORY CODE QUERIES (v7) ==========

    /**
     * Get products in the same category code.
     * Powers "alternatives in same category" and cross-source comparison.
     */
    @Query("SELECT * FROM food_products WHERE category_code = :categoryCode " +
            "ORDER BY dataCompleteness DESC LIMIT :limit")
    List<FoodProductEntity> getProductsByCategoryCode(String categoryCode, int limit);

    /**
     * Get products from a specific source in a specific category.
     * Example: all CIQUAL products in subgroup "0702" (chocolate products).
     */
    @Query("SELECT * FROM food_products " +
            "WHERE sourceId = :sourceId AND category_code = :categoryCode " +
            "ORDER BY dataCompleteness DESC LIMIT :limit")
    List<FoodProductEntity> getProductsBySourceAndCategoryCode(
            String sourceId, String categoryCode, int limit);

    /**
     * Delete all products from a specific source.
     * Used by CiqualImportService before re-import.
     */
    @Query("DELETE FROM food_products WHERE sourceId = :sourceId")
    int deleteProductsBySource(String sourceId);

    /**
     * Count products per category code (import validation + coverage stats).
     */
    @Query("SELECT category_code, COUNT(*) as count FROM food_products " +
            "WHERE category_code IS NOT NULL " +
            "GROUP BY category_code ORDER BY count DESC")
    List<CategoryCodeCount> getCategoryCodeCounts();

    class CategoryCodeCount {
        public String category_code;
        public int count;
    }


}