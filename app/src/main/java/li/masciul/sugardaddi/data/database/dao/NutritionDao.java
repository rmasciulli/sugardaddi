package li.masciul.sugardaddi.data.database.dao;

import androidx.room.*;
import androidx.lifecycle.LiveData;
import java.util.List;

import li.masciul.sugardaddi.core.enums.DataConfidence;
import li.masciul.sugardaddi.data.database.entities.NutritionEntity;
import li.masciul.sugardaddi.data.database.entities.FoodProductEntity;

/**
 * NutritionDao - Nutrition-specific queries and analytics
 *
 * CLEAN ARCHITECTURE v2:
 * - Powerful nutrition-based queries leveraging indexes
 * - No duplicate methods
 * - Consistent naming throughout
 */
@Dao
public interface NutritionDao {

    // ========== CORE CRUD OPERATIONS ==========

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public abstract long insertNutrition(NutritionEntity nutrition);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public abstract long[] insertNutritionBatch(List<NutritionEntity> nutritionList);

    @Update
    public abstract int updateNutrition(NutritionEntity nutrition);

    @Update
    public abstract int updateNutritionBatch(List<NutritionEntity> nutritionList);

    @Delete
    public abstract int deleteNutrition(NutritionEntity nutrition);

    @Query("DELETE FROM nutrition WHERE nutritionId = :id")
    public abstract int deleteNutritionById(String id);

    @Query("DELETE FROM nutrition WHERE sourceType = :type AND sourceId = :id")
    public abstract int deleteNutritionBySource(String type, String id);

    // ========== BASIC RETRIEVAL ==========

    @Query("SELECT * FROM nutrition WHERE nutritionId = :id LIMIT 1")
    public abstract NutritionEntity getNutritionById(String id);

    @Query("SELECT * FROM nutrition WHERE sourceType = :type AND sourceId = :id LIMIT 1")
    public abstract NutritionEntity getNutritionBySource(String type, String id);

    @Query("SELECT * FROM nutrition")
    public abstract List<NutritionEntity> getAllNutrition();

    @Query("SELECT COUNT(*) FROM nutrition")
    public abstract int getNutritionCount();

    // ========== MAINTENANCE OPERATIONS ==========

    /**
     * Delete orphaned nutrition entries (no matching product)
     */
    @Query("DELETE FROM nutrition WHERE sourceType = 'product' AND sourceId NOT IN " +
            "(SELECT id FROM food_products)")
    public abstract int deleteOrphanedNutrition();

    /**
     * Delete old nutrition entries
     */
    @Query("DELETE FROM nutrition WHERE lastUpdated < :threshold")
    public abstract int deleteOldNutrition(long threshold);

    /**
     * Delete empty nutrition entries
     */
    @Query("DELETE FROM nutrition WHERE " +
            "energyKcal IS NULL AND proteins IS NULL AND " +
            "carbohydrates IS NULL AND fat IS NULL")
    public abstract int deleteEmptyNutrition();

    /**
     * Clear all nutrition data
     */
    @Query("DELETE FROM nutrition")
    public abstract void clearAllNutrition();

    // ========== MACRONUTRIENT QUERIES ==========

    @Query("SELECT * FROM nutrition WHERE proteins > :minProtein " +
            "ORDER BY proteins DESC LIMIT :limit")
    public abstract List<NutritionEntity> findHighProteinProducts(double minProtein, int limit);

    @Query("SELECT * FROM nutrition WHERE energyKcal < :maxCalories AND energyKcal > 0 " +
            "ORDER BY energyKcal ASC LIMIT :limit")
    public abstract List<NutritionEntity> findLowCalorieProducts(double maxCalories, int limit);

    @Query("SELECT * FROM nutrition WHERE energyKcal BETWEEN :minCal AND :maxCal " +
            "ORDER BY energyKcal ASC")
    public abstract List<NutritionEntity> findProductsInCalorieRange(double minCal, double maxCal);

    @Query("SELECT * FROM nutrition WHERE sugars < :maxSugar OR sugars IS NULL " +
            "ORDER BY sugars ASC LIMIT :limit")
    public abstract List<NutritionEntity> findLowSugarProducts(double maxSugar, int limit);

    @Query("SELECT * FROM nutrition WHERE fiber > :minFiber " +
            "ORDER BY fiber DESC LIMIT :limit")
    public abstract List<NutritionEntity> findHighFiberProducts(double minFiber, int limit);

    @Query("SELECT * FROM nutrition WHERE saturatedFat < :maxSatFat " +
            "ORDER BY saturatedFat ASC LIMIT :limit")
    public abstract List<NutritionEntity> findLowSaturatedFatProducts(double maxSatFat, int limit);

    @Query("SELECT * FROM nutrition WHERE salt < :maxSalt OR sodium < :maxSodium " +
            "ORDER BY salt ASC LIMIT :limit")
    public abstract List<NutritionEntity> findLowSaltProducts(double maxSalt, double maxSodium, int limit);

    // ========== CATEGORY ANALYTICS ==========

    @Query("SELECT DISTINCT category FROM nutrition WHERE category IS NOT NULL ORDER BY category")
    public abstract List<String> getAllCategories();

    @Query("SELECT category, COUNT(*) as count FROM nutrition " +
            "WHERE category IS NOT NULL GROUP BY category ORDER BY count DESC")
    public abstract List<CategoryCount> getCategoryCounts();

    @Query("SELECT " +
            "AVG(energyKcal) as avgCalories, " +
            "AVG(proteins) as avgProteins, " +
            "AVG(carbohydrates) as avgCarbs, " +
            "AVG(fat) as avgFat, " +
            "AVG(fiber) as avgFiber, " +
            "AVG(sugars) as avgSugars, " +
            "AVG(salt) as avgSalt, " +
            "COUNT(*) as productCount " +
            "FROM nutrition WHERE category = :category")
    public abstract CategoryAverages getCategoryAverages(String category);

    // ========== ALTERNATIVE PRODUCTS ==========

    @Query("SELECT * FROM nutrition WHERE " +
            "category = :category AND nutritionId != :excludeId AND " +
            "energyKcal < :currentCalories AND " +
            "sugars < :currentSugars AND " +
            "saturatedFat < :currentSatFat " +
            "ORDER BY dataCompleteness DESC, energyKcal ASC " +
            "LIMIT :limit")
    public abstract List<NutritionEntity> findHealthierAlternatives(
            String category, String excludeId,
            double currentCalories, double currentSugars, double currentSatFat,
            int limit);

    @Query("SELECT * FROM nutrition WHERE " +
            "nutritionId != :excludeId AND " +
            "ABS(COALESCE(proteins, 0) - :targetProteins) < :proteinTolerance AND " +
            "ABS(COALESCE(energyKcal, 0) - :targetCalories) < :calorieTolerance " +
            "ORDER BY dataCompleteness DESC " +
            "LIMIT :limit")
    public abstract List<NutritionEntity> findSimilarProducts(
            String excludeId,
            double targetProteins, double proteinTolerance,
            double targetCalories, double calorieTolerance,
            int limit);

    // ========== VITAMIN & MINERAL QUERIES ==========

    @Query("SELECT * FROM nutrition WHERE iron > :minIron ORDER BY iron DESC LIMIT :limit")
    public abstract List<NutritionEntity> findIronRichProducts(double minIron, int limit);

    @Query("SELECT * FROM nutrition WHERE calcium > :minCalcium ORDER BY calcium DESC LIMIT :limit")
    public abstract List<NutritionEntity> findCalciumRichProducts(double minCalcium, int limit);

    @Query("SELECT * FROM nutrition WHERE vitaminC > :minVitaminC ORDER BY vitaminC DESC LIMIT :limit")
    public abstract List<NutritionEntity> findVitaminCRichProducts(double minVitaminC, int limit);

    @Query("SELECT * FROM nutrition WHERE vitaminD > :minVitaminD ORDER BY vitaminD DESC LIMIT :limit")
    public abstract List<NutritionEntity> findVitaminDRichProducts(double minVitaminD, int limit);

    @Query("SELECT * FROM nutrition WHERE vitaminB12 > :minB12 ORDER BY vitaminB12 DESC LIMIT :limit")
    public abstract List<NutritionEntity> findVitaminB12RichProducts(double minB12, int limit);

    // ========== DIET-SPECIFIC QUERIES ==========

    @Query("SELECT * FROM nutrition WHERE " +
            "carbohydrates < :maxCarbs AND fat > :minFat " +
            "ORDER BY (fat - carbohydrates) DESC LIMIT :limit")
    public abstract List<NutritionEntity> findKetoFriendlyProducts(double maxCarbs, double minFat, int limit);

    @Query("SELECT * FROM nutrition WHERE " +
            "proteins > :minProtein AND fat < :maxFat " +
            "ORDER BY proteins DESC LIMIT :limit")
    public abstract List<NutritionEntity> findHighProteinLowFatProducts(
            double minProtein, double maxFat, int limit);

    @Query("SELECT * FROM nutrition WHERE " +
            "fiber > :minFiber AND sugars < :maxSugar " +
            "ORDER BY fiber DESC LIMIT :limit")
    public abstract List<NutritionEntity> findHighFiberLowSugarProducts(
            double minFiber, double maxSugar, int limit);

    // ========== STATISTICS ==========

    @Query("SELECT COUNT(*) FROM nutrition WHERE dataCompleteness > :threshold")
    public abstract int getHighQualityNutritionCount(float threshold);

    @Query("SELECT dataSource, COUNT(*) as count FROM nutrition " +
            "GROUP BY dataSource ORDER BY count DESC")
    public abstract List<DataSourceCount> getNutritionCountBySource();

    @Query("SELECT " +
            "COUNT(*) as totalEntries, " +
            "AVG(dataCompleteness) as avgCompleteness, " +
            "COUNT(CASE WHEN energyKcal IS NOT NULL THEN 1 END) as withCalories, " +
            "COUNT(CASE WHEN proteins IS NOT NULL THEN 1 END) as withProteins, " +
            "COUNT(CASE WHEN vitaminC IS NOT NULL OR vitaminD IS NOT NULL OR vitaminE IS NOT NULL THEN 1 END) as withVitamins " +
            "FROM nutrition")
    public abstract NutritionStats getNutritionStats();

    // ========== LIVE DATA QUERIES ==========

    @Query("SELECT * FROM nutrition WHERE nutritionId = :id")
    public abstract LiveData<NutritionEntity> getNutritionByIdLive(String id);

    @Query("SELECT * FROM nutrition WHERE sourceType = :type AND sourceId = :id")
    public abstract LiveData<NutritionEntity> getNutritionBySourceLive(String type, String id);

    @Query("SELECT COUNT(*) FROM nutrition")
    public abstract LiveData<Integer> getNutritionCountLive();

    // ========== INNER CLASSES ==========

    public static class CategoryCount {
        public String category;
        public int count;
    }

    public static class CategoryAverages {
        public Double avgCalories;
        public Double avgProteins;
        public Double avgCarbs;
        public Double avgFat;
        public Double avgFiber;
        public Double avgSugars;
        public Double avgSalt;
        public int productCount;
    }

    public static class DataSourceCount {
        public String dataSource;
        public int count;
    }

    public static class NutritionStats {
        public int totalEntries;
        public float avgCompleteness;
        public int withCalories;
        public int withProteins;
        public int withVitamins;
    }
    // ========== CATEGORY CODE STATS (v7 — Ciqual taxonomy) ==========

    /**
     * Delete all nutrition rows from a specific data source.
     * Used by CiqualImportService before re-import.
     */
    @Query("DELETE FROM nutrition WHERE dataSource = :dataSource")
    int deleteNutritionByDataSource(String dataSource);

    /**
     * Category stats using the structured categoryCode JOIN.
     * Complements getCategoryAverages() which uses the free-text category field.
     * Available once Ciqual is imported (food_products.category_code populated).
     */
    @Query("SELECT " +
            "AVG(n.energyKcal) as avgCalories, " +
            "AVG(n.proteins) as avgProteins, " +
            "AVG(n.carbohydrates) as avgCarbs, " +
            "AVG(n.fat) as avgFat, " +
            "AVG(n.fiber) as avgFiber, " +
            "AVG(n.sugars) as avgSugars, " +
            "AVG(n.salt) as avgSalt, " +
            "COUNT(*) as productCount " +
            "FROM nutrition n " +
            "INNER JOIN food_products fp ON fp.id = n.sourceId " +
            "WHERE fp.category_code = :categoryCode AND n.sourceType = 'product'")
    CategoryAverages getCategoryStatsByCategoryCode(String categoryCode);

    /**
     * Find healthier alternatives using categoryCode JOIN.
     * More structured than findHealthierAlternatives() — uses the indexed categoryCode.
     */
    @Query("SELECT n.* FROM nutrition n " +
            "INNER JOIN food_products fp ON fp.id = n.sourceId " +
            "WHERE fp.category_code = :categoryCode " +
            "AND n.nutritionId != :excludeId AND n.sourceType = 'product' " +
            "AND (n.energyKcal IS NULL OR n.energyKcal <= :maxCalories) " +
            "AND (n.sugars IS NULL OR n.sugars <= :maxSugars) " +
            "AND (n.saturatedFat IS NULL OR n.saturatedFat <= :maxSatFat) " +
            "ORDER BY n.dataCompleteness DESC, n.energyKcal ASC LIMIT :limit")
    List<NutritionEntity> findAlternativesByCategoryCode(
            String categoryCode, String excludeId,
            double maxCalories, double maxSugars, double maxSatFat, int limit);

    /**
     * Filter by data confidence level.
     * SCIENTIFIC = lab-measured (Ciqual A/B, USDA)
     * DECLARED   = manufacturer label (OpenFoodFacts)
     * COMPUTED   = calculated from ingredients (recipes)
     * ESTIMATED  = lower confidence (Ciqual C/D, fuzzy match)
     * USER       = manually entered
     */
    @Query("SELECT * FROM nutrition WHERE dataConfidence = :confidence")
    List<NutritionEntity> getByConfidence(DataConfidence confidence);
}