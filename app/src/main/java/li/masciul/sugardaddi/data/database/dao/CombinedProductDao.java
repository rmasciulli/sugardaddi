package li.masciul.sugardaddi.data.database.dao;

import androidx.room.*;
import androidx.lifecycle.LiveData;
import java.util.List;

import li.masciul.sugardaddi.data.database.entities.FoodProductEntity;
import li.masciul.sugardaddi.data.database.entities.NutritionEntity;
import li.masciul.sugardaddi.data.database.relations.FoodProductWithNutrition;

/**
 * CombinedProductDao - High-level DAO for coordinated product+nutrition operations
 *
 * ARCHITECTURE PURPOSE:
 * This DAO provides convenience methods that work with both tables together,
 * maintaining referential integrity and ensuring consistent operations.
 *
 * KEY FEATURES:
 * - Automatic JOIN operations via @Transaction
 * - Maintains foreign key relationships
 * - Powerful nutrition-based filtering
 * - Finding alternatives and comparisons
 * - Batch operations with progress tracking
 *
 * PERFORMANCE NOTES:
 * - All complex queries use indices for optimal performance
 * - @Transaction ensures consistency for multi-table operations
 * - LiveData support for reactive UI updates
 */
@Dao
public abstract class CombinedProductDao {

    // ========== SINGLE PRODUCT OPERATIONS ==========

    /**
     * Get product with its nutrition data (by ID or barcode)
     * Handles both ID formats for flexibility
     */
    @Transaction
    @Query("SELECT * FROM food_products WHERE id = :productId OR barcode = :productId LIMIT 1")
    public abstract FoodProductWithNutrition getProductWithNutrition(String productId);

    /**
     * Get product with nutrition by exact ID
     */
    @Transaction
    @Query("SELECT * FROM food_products WHERE id = :productId LIMIT 1")
    public abstract FoodProductWithNutrition getProductWithNutritionById(String productId);

    /**
     * Get product with nutrition by barcode
     */
    @Transaction
    @Query("SELECT * FROM food_products WHERE barcode = :barcode LIMIT 1")
    public abstract FoodProductWithNutrition getProductWithNutritionByBarcode(String barcode);

    // ========== BULK RETRIEVAL OPERATIONS ==========

    /**
     * Get all products with nutrition data
     */
    @Transaction
    @Query("SELECT * FROM food_products ORDER BY updatedAt DESC")
    public abstract List<FoodProductWithNutrition> getAllProductsWithNutrition();

    /**
     * Get all products with nutrition (LiveData for UI)
     */
    @Transaction
    @Query("SELECT * FROM food_products ORDER BY updatedAt DESC")
    public abstract LiveData<List<FoodProductWithNutrition>> getAllProductsWithNutritionLive();

    /**
     * Get recently accessed products with nutrition
     */
    @Transaction
    @Query("SELECT * FROM food_products ORDER BY updatedAt DESC LIMIT :limit")
    public abstract LiveData<List<FoodProductWithNutrition>> getRecentProductsWithNutrition(int limit);

    /**
     * Get most frequently accessed products with nutrition
     */
    @Transaction
    @Query("SELECT * FROM food_products ORDER BY accessCount DESC LIMIT :limit")
    public abstract LiveData<List<FoodProductWithNutrition>> getPopularProductsWithNutrition(int limit);

    /**
     * Get favorite products with nutrition
     */
    @Transaction
    @Query("SELECT * FROM food_products WHERE isFavorite = 1 ORDER BY updatedAt DESC")
    public abstract LiveData<List<FoodProductWithNutrition>> getFavoriteProductsWithNutrition();

    /**
     * Get favorite products (non-LiveData)
     */
    @Transaction
    @Query("SELECT * FROM food_products WHERE isFavorite = 1 ORDER BY updatedAt DESC")
    public abstract List<FoodProductWithNutrition> getFavoriteProductsList();

    /**
     * Search products by data source with hybrid translation support
     * Used by Ciqual and other data sources for filtered searches
     */
    @Transaction
    @Query("SELECT * FROM food_products p " +
            "LEFT JOIN nutrition n ON p.id = n.sourceId " +
            "WHERE p.sourceId = :sourceId AND " +
            "(p.name LIKE '%' || :query || '%' OR " +
            "p.brand LIKE '%' || :query || '%' OR " +
            "p.categoriesText LIKE '%' || :query || '%' OR " +
            "p.searchableText LIKE '%' || :query || '%') " +
            "LIMIT :limit")
    @SuppressWarnings(RoomWarnings.CURSOR_MISMATCH)
    public abstract List<FoodProductWithNutrition> searchBySource(String sourceId, String query, int limit);

    /**
     * Get Ciqual product by original code
     */
    @Transaction
    @Query("SELECT * FROM food_products p " +
            "LEFT JOIN nutrition n ON p.id = n.sourceId " +
            "WHERE p.sourceId = 'CIQUAL' AND p.originalId = :code")
    @SuppressWarnings(RoomWarnings.CURSOR_MISMATCH)
    public abstract FoodProductWithNutrition getCiqualProduct(String code);

    /**
     * Count products by source (for verification)
     */
    @Query("SELECT COUNT(*) FROM food_products WHERE sourceId = :sourceId")
    public abstract int getCountBySource(String sourceId);

    // ========== INSERTION OPERATIONS ==========

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract long insertProductEntity(FoodProductEntity product);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract long insertNutritionEntity(NutritionEntity nutrition);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract long[] insertProductEntities(List<FoodProductEntity> products);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract long[] insertNutritionEntities(List<NutritionEntity> nutritionList);

    /**
     * Insert product with its nutrition (transactional)
     * Ensures both tables are updated atomically
     */
    @Transaction
    public void insertProductWithNutrition(FoodProductEntity product, NutritionEntity nutrition) {
        if (product != null) {
            long productId = insertProductEntity(product);

            if (nutrition != null) {
                // Ensure the nutrition sourceId matches the product id
                nutrition.setSourceId(product.getId());
                nutrition.setSourceType("product");
                nutrition.setNutritionId(NutritionEntity.createNutritionId("product", product.getId()));
                insertNutritionEntity(nutrition);
            }
        }
    }

    /**
     * Insert multiple products with nutrition (batch operation)
     */
    @Transaction
    public void insertProductsWithNutrition(List<FoodProductEntity> products,
                                            List<NutritionEntity> nutritionList) {
        if (products != null && !products.isEmpty()) {
            insertProductEntities(products);

            if (nutritionList != null && !nutritionList.isEmpty()) {
                // Ensure all nutrition entries have correct source references
                for (int i = 0; i < nutritionList.size() && i < products.size(); i++) {
                    NutritionEntity nutrition = nutritionList.get(i);
                    FoodProductEntity product = products.get(i);

                    nutrition.setSourceId(product.getId());
                    nutrition.setSourceType("product");
                    nutrition.setNutritionId(
                            NutritionEntity.createNutritionId("product", product.getId()));
                }
                insertNutritionEntities(nutritionList);
            }
        }
    }

    // ========== UPDATE OPERATIONS ==========

    @Update
    protected abstract int updateProductEntity(FoodProductEntity product);

    @Update
    protected abstract int updateNutritionEntity(NutritionEntity nutrition);

    @Update
    protected abstract int updateProductEntities(List<FoodProductEntity> products);

    @Update
    protected abstract int updateNutritionEntities(List<NutritionEntity> nutritionList);

    /**
     * Update product with its nutrition (transactional)
     */
    @Transaction
    public void updateProductWithNutrition(FoodProductEntity product, NutritionEntity nutrition) {
        if (product != null) {
            product.setUpdatedAt(System.currentTimeMillis());
            updateProductEntity(product);

            if (nutrition != null) {
                nutrition.setLastUpdated(System.currentTimeMillis());
                updateNutritionEntity(nutrition);
            }
        }
    }

    /**
     * Update multiple products with nutrition
     */
    @Transaction
    public void updateProductsWithNutrition(List<FoodProductEntity> products,
                                            List<NutritionEntity> nutritionList) {
        if (products != null && !products.isEmpty()) {
            long now = System.currentTimeMillis();
            for (FoodProductEntity product : products) {
                product.setUpdatedAt(now);
            }
            updateProductEntities(products);

            if (nutritionList != null && !nutritionList.isEmpty()) {
                for (NutritionEntity nutrition : nutritionList) {
                    nutrition.setLastUpdated(now);
                }
                updateNutritionEntities(nutritionList);
            }
        }
    }

    // ========== DELETION OPERATIONS ==========

    /**
     * Delete product and its nutrition (transactional)
     */
    @Transaction
    public void deleteProductComplete(String productId) {
        deleteProductById(productId);
        deleteNutritionForProduct(productId);
    }

    /**
     * Delete multiple products and their nutrition
     */
    @Transaction
    public void deleteProductsComplete(List<String> productIds) {
        if (productIds != null && !productIds.isEmpty()) {
            for (String productId : productIds) {
                deleteProductById(productId);
                deleteNutritionForProduct(productId);
            }
        }
    }

    @Query("DELETE FROM food_products WHERE id = :productId")
    protected abstract void deleteProductById(String productId);

    @Query("DELETE FROM nutrition WHERE sourceType = 'product' AND sourceId = :productId")
    protected abstract void deleteNutritionForProduct(String productId);

    @Query("DELETE FROM food_products WHERE id IN (:productIds)")
    protected abstract void deleteProductsByIds(List<String> productIds);

    @Query("DELETE FROM nutrition WHERE sourceType = 'product' AND sourceId IN (:productIds)")
    protected abstract void deleteNutritionForProducts(List<String> productIds);

    // ========== NUTRITION-BASED SEARCH OPERATIONS ==========

    /**
     * Search products with comprehensive nutrition filters
     * All parameters are optional (use null to ignore)
     */
    @Transaction
    @Query("SELECT p.* FROM food_products p " +
            "INNER JOIN nutrition n ON n.sourceId = p.id " +
            "WHERE n.sourceType = 'product' " +
            "AND (:minCal IS NULL OR n.energyKcal >= :minCal) " +
            "AND (:maxCal IS NULL OR n.energyKcal <= :maxCal) " +
            "AND (:minProtein IS NULL OR n.proteins >= :minProtein) " +
            "AND (:maxProtein IS NULL OR n.proteins <= :maxProtein) " +
            "AND (:minCarbs IS NULL OR n.carbohydrates >= :minCarbs) " +
            "AND (:maxCarbs IS NULL OR n.carbohydrates <= :maxCarbs) " +
            "AND (:minFat IS NULL OR n.fat >= :minFat) " +
            "AND (:maxFat IS NULL OR n.fat <= :maxFat) " +
            "AND (:maxSugar IS NULL OR n.sugars <= :maxSugar) " +
            "AND (:maxSalt IS NULL OR n.salt <= :maxSalt) " +
            "ORDER BY n.dataCompleteness DESC, p.accessCount DESC " +
            "LIMIT :limit")
    public abstract List<FoodProductWithNutrition> searchWithNutritionFilters(
            Double minCal, Double maxCal,
            Double minProtein, Double maxProtein,
            Double minCarbs, Double maxCarbs,
            Double minFat, Double maxFat,
            Double maxSugar,
            Double maxSalt,
            int limit);

    /**
     * Find products in same category with better nutrition
     */
    @Transaction
    @Query("SELECT p.* FROM food_products p " +
            "INNER JOIN nutrition n ON n.sourceId = p.id " +
            "INNER JOIN nutrition orig ON orig.sourceId = :productId " +
            "WHERE n.sourceType = 'product' AND orig.sourceType = 'product' " +
            "AND p.id != :productId " +
            "AND n.category = orig.category " +
            "AND n.energyKcal < orig.energyKcal " +
            "AND n.sugars < orig.sugars " +
            "AND n.salt < orig.salt " +
            "ORDER BY n.dataCompleteness DESC, n.energyKcal ASC " +
            "LIMIT :limit")
    public abstract List<FoodProductWithNutrition> findHealthierAlternatives(
            String productId, int limit);

    /**
     * Find similar products by nutrition profile
     * Uses euclidean distance on normalized macros
     */
    @SuppressWarnings(RoomWarnings.CURSOR_MISMATCH)
    @Transaction
    @Query("SELECT p.*, " +
            "ABS(n.energyKcal - :targetCalories) + " +
            "ABS(n.proteins - :targetProtein) * 10 + " +
            "ABS(n.carbohydrates - :targetCarbs) * 2 + " +
            "ABS(n.fat - :targetFat) * 5 AS similarity_score " +
            "FROM food_products p " +
            "INNER JOIN nutrition n ON n.sourceId = p.id " +
            "WHERE n.sourceType = 'product' " +
            "AND n.energyKcal IS NOT NULL " +
            "AND n.proteins IS NOT NULL " +
            "AND n.carbohydrates IS NOT NULL " +
            "AND n.fat IS NOT NULL " +
            "ORDER BY similarity_score ASC " +
            "LIMIT :limit")
    public abstract List<FoodProductWithNutrition> findSimilarByNutrition(
            double targetCalories,
            double targetProtein,
            double targetCarbs,
            double targetFat,
            int limit);

    // ========== CATEGORY-BASED OPERATIONS ==========

    /**
     * Get products in a specific category
     */
    @Transaction
    @Query("SELECT p.* FROM food_products p " +
            "INNER JOIN nutrition n ON n.sourceId = p.id " +
            "WHERE n.sourceType = 'product' " +
            "AND n.category = :category " +
            "ORDER BY p.accessCount DESC " +
            "LIMIT :limit")
    public abstract List<FoodProductWithNutrition> getProductsByCategory(
            String category, int limit);

    /**
     * Get category statistics
     */
    @Query("SELECT n.category, " +
            "COUNT(*) as count, " +
            "AVG(n.energyKcal) as avgCalories, " +
            "AVG(n.proteins) as avgProtein, " +
            "AVG(n.carbohydrates) as avgCarbs, " +
            "AVG(n.fat) as avgFat " +
            "FROM nutrition n " +
            "WHERE n.sourceType = 'product' " +
            "AND n.category IS NOT NULL " +
            "GROUP BY n.category " +
            "ORDER BY count DESC")
    public abstract List<CategoryStats> getCategoryStatistics();

    // ========== ANALYTICS OPERATIONS ==========

    /**
     * Get products by quality score range
     */
    @Transaction
    @Query("SELECT * FROM food_products " +
            "WHERE dataQualityScore BETWEEN :minScore AND :maxScore " +
            "ORDER BY dataQualityScore DESC " +
            "LIMIT :limit")
    public abstract List<FoodProductWithNutrition> getProductsByQualityScore(
            int minScore, int maxScore, int limit);

    /**
     * Get products with specific dietary characteristics
     */
    @Transaction
    @Query("SELECT * FROM food_products " +
            "WHERE (:requireOrganic IS NULL OR isOrganic = :requireOrganic) " +
            "AND (:requireVegan IS NULL OR isVegan = :requireVegan) " +
            "AND (:requireVegetarian IS NULL OR isVegetarian = :requireVegetarian) " +
            "AND (:requireGlutenFree IS NULL OR isGlutenFree = :requireGlutenFree) " +
            "ORDER BY dataQualityScore DESC " +
            "LIMIT :limit")
    public abstract List<FoodProductWithNutrition> getProductsByDietaryProfile(
            Boolean requireOrganic,
            Boolean requireVegan,
            Boolean requireVegetarian,
            Boolean requireGlutenFree,
            int limit);

    // ========== CACHE MAINTENANCE ==========

    /**
     * Count products with incomplete data
     */
    @Query("SELECT COUNT(*) FROM food_products " +
            "WHERE dataCompleteness < :threshold")
    public abstract int countIncompleteProducts(float threshold);

    /**
     * Get products needing update (stale or incomplete)
     */
    @Transaction
    @Query("SELECT * FROM food_products " +
            "WHERE lastUpdated < :staleThreshold " +
            "OR dataCompleteness < :completenessThreshold " +
            "ORDER BY accessCount DESC " +
            "LIMIT :limit")
    public abstract List<FoodProductWithNutrition> getProductsNeedingUpdate(
            long staleThreshold,
            float completenessThreshold,
            int limit);

    // ========== INNER CLASS FOR STATISTICS ==========

    /**
     * Category statistics result
     */
    public static class CategoryStats {
        public String category;
        public int count;
        public Double avgCalories;
        public Double avgProtein;
        public Double avgCarbs;
        public Double avgFat;
    }
}