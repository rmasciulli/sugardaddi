package li.masciul.sugardaddi.core.future.category;

import li.masciul.sugardaddi.core.models.FoodProduct;
import li.masciul.sugardaddi.core.models.Nutrition;

import java.util.*;

/**
 * CategoryStats - Category analytics and statistical data
 *
 * STATUS: INTERFACE DEFINED, COMPUTATION TODO
 * See: core/future/category/README.md for details
 *
 * CURRENT USAGE:
 * - OpenFoodFactsDataSource.getCategoryStats() returns empty() for now
 * - CategoryComparison expects this data
 * - Infrastructure ready, population TODO
 *
 * TODO:
 * - Implement Ciqual local statistics computation
 * - Add OFF API category statistics fetching
 * - Cache strategy per source type
 *
 * This class handles statistical information about food categories:
 * - CIQUAL: Precomputed local statistics from database
 * - OFF: API-provided category statistics with caching
 * - User: Aggregate statistics from user's meal history
 */
public class CategoryStats {

    // ========== CATEGORY IDENTIFICATION ==========
    private String categoryId;              // Category identifier (source-specific)
    private String categoryName;            // Human-readable category name
    private String sourceId;                // "ciqual", "openfoodfacts", "logmeal"
    private String language;                // Language of category name

    // ========== BASIC STATISTICS ==========
    private int totalProducts;             // Total products in this category
    private int analyzedProducts;          // Products with nutrition data
    private double completenessRate;       // % of products with complete nutrition

    // ========== NUTRITION AVERAGES ==========
    private Double avgCalories;            // Average kcal per 100g
    private Double avgProteins;            // Average proteins per 100g
    private Double avgCarbohydrates;       // Average carbohydrates per 100g
    private Double avgSugars;              // Average sugars per 100g
    private Double avgFat;                 // Average fat per 100g
    private Double avgSaturatedFat;        // Average saturated fat per 100g
    private Double avgFiber;               // Average fiber per 100g
    private Double avgSalt;                // Average salt per 100g
    private Double avgSodium;              // Average sodium per 100g

    // ========== EXTENDED NUTRITION (when available) ==========
    private Double avgCalcium;             // Average calcium (mg per 100g)
    private Double avgIron;                // Average iron (mg per 100g)
    private Double avgVitaminC;            // Average vitamin C (mg per 100g)
    private Double avgVitaminD;            // Average vitamin D (μg per 100g)

    // ========== STATISTICAL RANGES ==========
    private NutritionalRange caloriesRange;
    private NutritionalRange proteinsRange;
    private NutritionalRange carbohydratesRange;
    private NutritionalRange sugarsRange;
    private NutritionalRange fatRange;
    private NutritionalRange fiberRange;
    private NutritionalRange saltRange;

    // ========== CACHE & FRESHNESS ==========
    private long computedAt;               // When stats were computed/fetched
    private long validUntil;               // Cache expiry time
    private boolean isFromLocalData;       // True for Ciqual, false for API sources
    private boolean isComplete;            // True if all expected data is present
    private String computationMethod;      // "precomputed", "api_endpoint", "on_demand"

    // ========== SOURCE-SPECIFIC METADATA ==========
    private Map<String, Object> sourceMetadata; // Source-specific additional data
    private String apiVersion;             // For API sources (OFF, LogMeal)
    private String datasetVersion;         // For local sources (Ciqual)

    // ========== QUALITY INDICATORS ==========
    private double reliabilityScore;       // 0.0-1.0 based on sample size and source
    private Set<String> missingNutrients;  // Nutrients not available in this category
    private Set<String> warnings;          // Data quality warnings

    // ========== CONSTRUCTORS ==========

    public CategoryStats() {
        this.computedAt = System.currentTimeMillis();
        this.sourceMetadata = new HashMap<>();
        this.missingNutrients = new HashSet<>();
        this.warnings = new HashSet<>();
        this.language = "en";
    }

    /**
     * Create category stats with basic information
     */
    public CategoryStats(String categoryId, String categoryName, String sourceId) {
        this();
        this.categoryId = categoryId;
        this.categoryName = categoryName;
        this.sourceId = sourceId;
        this.isFromLocalData = "ciqual".equals(sourceId);
        this.computationMethod = isFromLocalData ? "precomputed" : "api_endpoint";
    }

    /**
     * Create empty stats (for fallback cases)
     */
    public static CategoryStats empty() {
        CategoryStats empty = new CategoryStats();
        empty.totalProducts = 0;
        empty.analyzedProducts = 0;
        empty.completenessRate = 0.0;
        empty.isComplete = false;
        empty.reliabilityScore = 0.0;
        return empty;
    }

    /**
     * Create stats for single product (fallback when no category data available)
     */
    public static CategoryStats fromSingleProduct(FoodProduct product) {
        CategoryStats stats = new CategoryStats();
        stats.totalProducts = 1;
        stats.analyzedProducts = product.hasNutritionData() ? 1 : 0;
        stats.completenessRate = product.hasNutritionData() ? 1.0 : 0.0;

        if (product.hasNutritionData()) {
            Nutrition nutrition = product.getNutrition();
            stats.avgCalories = nutrition.getEnergyKcal();
            stats.avgProteins = nutrition.getProteins();
            stats.avgCarbohydrates = nutrition.getCarbohydrates();
            stats.avgSugars = nutrition.getSugars();
            stats.avgFat = nutrition.getFat();
            stats.avgSaturatedFat = nutrition.getSaturatedFat();
            stats.avgFiber = nutrition.getFiber();
            stats.avgSalt = nutrition.getSalt();
            stats.avgSodium = nutrition.getSodium();
        }

        stats.isComplete = true;
        stats.reliabilityScore = 0.1; // Low reliability for single product
        stats.computationMethod = "single_product_fallback";

        return stats;
    }

    // ========== VALIDATION & QUALITY ==========

    /**
     * Check if stats have meaningful nutrition data
     */
    public boolean hasNutritionData() {
        return avgCalories != null || avgProteins != null ||
                avgCarbohydrates != null || avgFat != null;
    }

    /**
     * Check if stats are fresh and usable
     */
    public boolean isFresh() {
        if (isFromLocalData) {
            return true; // Local data doesn't expire
        }
        return System.currentTimeMillis() < validUntil;
    }

    /**
     * Check if sample size is statistically meaningful
     */
    public boolean isStatisticallySignificant() {
        if (analyzedProducts >= 50) return true;   // Large sample
        if (analyzedProducts >= 20) return reliabilityScore > 0.7; // Medium sample with high quality
        if (analyzedProducts >= 5) return reliabilityScore > 0.9;  // Small sample must be very reliable
        return false; // Too small to be meaningful
    }

    /**
     * Get data quality description
     */
    public String getQualityDescription() {
        if (!hasNutritionData()) return "No Data";
        if (!isFresh()) return "Expired";
        if (analyzedProducts >= 100) return "Excellent";
        if (analyzedProducts >= 50) return "Very Good";
        if (analyzedProducts >= 20) return "Good";
        if (analyzedProducts >= 10) return "Fair";
        if (analyzedProducts >= 5) return "Limited";
        return "Insufficient";
    }

    /**
     * Calculate completeness score
     */
    public void calculateCompleteness() {
        int totalNutrients = 8; // Basic nutrients we track
        int availableNutrients = 0;

        if (avgCalories != null) availableNutrients++;
        if (avgProteins != null) availableNutrients++;
        if (avgCarbohydrates != null) availableNutrients++;
        if (avgSugars != null) availableNutrients++;
        if (avgFat != null) availableNutrients++;
        if (avgSaturatedFat != null) availableNutrients++;
        if (avgFiber != null) availableNutrients++;
        if (avgSalt != null) availableNutrients++;

        this.completenessRate = (double) availableNutrients / totalNutrients;
        this.isComplete = completenessRate >= 0.75; // 75% or more nutrients available
    }

    /**
     * Calculate reliability score based on source and sample size
     */
    public void calculateReliability() {
        double baseScore = getSourceReliability();
        double sampleScore = calculateSampleScore();
        double freshnessScore = isFresh() ? 1.0 : 0.5;

        this.reliabilityScore = (baseScore * 0.4 + sampleScore * 0.4 + freshnessScore * 0.2);
        this.reliabilityScore = Math.max(0.0, Math.min(1.0, reliabilityScore));
    }

    private double getSourceReliability() {
        switch (sourceId) {
            case "ciqual": return 0.95;      // Official data
            case "openfoodfacts": return 0.80; // Community data
            case "logmeal": return 0.85;     // AI analysis
            default: return 0.60;            // Unknown source
        }
    }

    private double calculateSampleScore() {
        if (analyzedProducts >= 100) return 1.0;
        if (analyzedProducts >= 50) return 0.9;
        if (analyzedProducts >= 20) return 0.7;
        if (analyzedProducts >= 10) return 0.5;
        if (analyzedProducts >= 5) return 0.3;
        return 0.1;
    }

    // ========== COMPARISON UTILITIES ==========

    /**
     * Compare a product's nutrition to category average
     */
    public NutritionComparison compareProduct(FoodProduct product) {
        if (!hasNutritionData() || !product.hasNutritionData()) {
            return null;
        }

        Nutrition productNutrition = product.getNutrition();
        NutritionComparison comparison = new NutritionComparison();

        comparison.caloriesVsAvg = compareNutrient(productNutrition.getEnergyKcal(), avgCalories);
        comparison.proteinsVsAvg = compareNutrient(productNutrition.getProteins(), avgProteins);
        comparison.carbohydratesVsAvg = compareNutrient(productNutrition.getCarbohydrates(), avgCarbohydrates);
        comparison.sugarsVsAvg = compareNutrient(productNutrition.getSugars(), avgSugars);
        comparison.fatVsAvg = compareNutrient(productNutrition.getFat(), avgFat);
        comparison.fiberVsAvg = compareNutrient(productNutrition.getFiber(), avgFiber);
        comparison.saltVsAvg = compareNutrient(productNutrition.getSalt(), avgSalt);

        return comparison;
    }

    private Double compareNutrient(Double productValue, Double categoryAvg) {
        if (productValue == null || categoryAvg == null || categoryAvg == 0) {
            return null;
        }
        return (productValue - categoryAvg) / categoryAvg; // Percentage difference
    }

    /**
     * Get percentile ranking for a nutrient value
     */
    public Integer getPercentileRank(String nutrient, Double value) {
        if (value == null) return null;

        NutritionalRange range = getNutritionalRange(nutrient);
        if (range == null) return null;

        return range.calculatePercentile(value);
    }

    private NutritionalRange getNutritionalRange(String nutrient) {
        switch (nutrient.toLowerCase()) {
            case "calories": return caloriesRange;
            case "proteins": return proteinsRange;
            case "carbohydrates": return carbohydratesRange;
            case "sugars": return sugarsRange;
            case "fat": return fatRange;
            case "fiber": return fiberRange;
            case "salt": return saltRange;
            default: return null;
        }
    }

    // ========== CACHE MANAGEMENT ==========

    /**
     * Set cache expiry based on source type
     */
    public void setCacheExpiry(long durationMillis) {
        if (isFromLocalData) {
            this.validUntil = Long.MAX_VALUE; // Local data doesn't expire
        } else {
            this.validUntil = System.currentTimeMillis() + durationMillis;
        }
    }

    /**
     * Set standard cache expiry for API sources
     */
    public void setStandardCacheExpiry() {
        switch (sourceId) {
            case "ciqual":
                setCacheExpiry(Long.MAX_VALUE); // Never expires
                break;
            case "openfoodfacts":
                setCacheExpiry(4 * 60 * 60 * 1000L); // 4 hours
                break;
            case "logmeal":
                setCacheExpiry(2 * 60 * 60 * 1000L); // 2 hours
                break;
            default:
                setCacheExpiry(60 * 60 * 1000L); // 1 hour
        }
    }

    /**
     * Check if cache needs refresh
     */
    public boolean needsRefresh() {
        return !isFresh() || (!isComplete && System.currentTimeMillis() - computedAt > 24 * 60 * 60 * 1000L);
    }

    // ========== GETTERS AND SETTERS ==========

    // Category identification
    public String getCategoryId() { return categoryId; }
    public void setCategoryId(String categoryId) { this.categoryId = categoryId; }

    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }

    public String getSourceId() { return sourceId; }
    public void setSourceId(String sourceId) { this.sourceId = sourceId; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    // Basic statistics
    public int getTotalProducts() { return totalProducts; }
    public void setTotalProducts(int totalProducts) { this.totalProducts = totalProducts; }

    public int getAnalyzedProducts() { return analyzedProducts; }
    public void setAnalyzedProducts(int analyzedProducts) { this.analyzedProducts = analyzedProducts; }

    public double getCompletenessRate() { return completenessRate; }
    public void setCompletenessRate(double completenessRate) { this.completenessRate = completenessRate; }

    // Nutrition averages
    public Double getAvgCalories() { return avgCalories; }
    public void setAvgCalories(Double avgCalories) { this.avgCalories = avgCalories; }

    public Double getAvgProteins() { return avgProteins; }
    public void setAvgProteins(Double avgProteins) { this.avgProteins = avgProteins; }

    public Double getAvgCarbohydrates() { return avgCarbohydrates; }
    public void setAvgCarbohydrates(Double avgCarbohydrates) { this.avgCarbohydrates = avgCarbohydrates; }

    public Double getAvgSugars() { return avgSugars; }
    public void setAvgSugars(Double avgSugars) { this.avgSugars = avgSugars; }

    public Double getAvgFat() { return avgFat; }
    public void setAvgFat(Double avgFat) { this.avgFat = avgFat; }

    public Double getAvgSaturatedFat() { return avgSaturatedFat; }
    public void setAvgSaturatedFat(Double avgSaturatedFat) { this.avgSaturatedFat = avgSaturatedFat; }

    public Double getAvgFiber() { return avgFiber; }
    public void setAvgFiber(Double avgFiber) { this.avgFiber = avgFiber; }

    public Double getAvgSalt() { return avgSalt; }
    public void setAvgSalt(Double avgSalt) { this.avgSalt = avgSalt; }

    public Double getAvgSodium() { return avgSodium; }
    public void setAvgSodium(Double avgSodium) { this.avgSodium = avgSodium; }

    // Extended nutrition
    public Double getAvgCalcium() { return avgCalcium; }
    public void setAvgCalcium(Double avgCalcium) { this.avgCalcium = avgCalcium; }

    public Double getAvgIron() { return avgIron; }
    public void setAvgIron(Double avgIron) { this.avgIron = avgIron; }

    public Double getAvgVitaminC() { return avgVitaminC; }
    public void setAvgVitaminC(Double avgVitaminC) { this.avgVitaminC = avgVitaminC; }

    public Double getAvgVitaminD() { return avgVitaminD; }
    public void setAvgVitaminD(Double avgVitaminD) { this.avgVitaminD = avgVitaminD; }

    // Statistical ranges
    public NutritionalRange getCaloriesRange() { return caloriesRange; }
    public void setCaloriesRange(NutritionalRange caloriesRange) { this.caloriesRange = caloriesRange; }

    public NutritionalRange getProteinsRange() { return proteinsRange; }
    public void setProteinsRange(NutritionalRange proteinsRange) { this.proteinsRange = proteinsRange; }

    public NutritionalRange getCarbohydratesRange() { return carbohydratesRange; }
    public void setCarbohydratesRange(NutritionalRange carbohydratesRange) { this.carbohydratesRange = carbohydratesRange; }

    public NutritionalRange getSugarsRange() { return sugarsRange; }
    public void setSugarsRange(NutritionalRange sugarsRange) { this.sugarsRange = sugarsRange; }

    public NutritionalRange getFatRange() { return fatRange; }
    public void setFatRange(NutritionalRange fatRange) { this.fatRange = fatRange; }

    public NutritionalRange getFiberRange() { return fiberRange; }
    public void setFiberRange(NutritionalRange fiberRange) { this.fiberRange = fiberRange; }

    public NutritionalRange getSaltRange() { return saltRange; }
    public void setSaltRange(NutritionalRange saltRange) { this.saltRange = saltRange; }

    // Cache & freshness
    public long getComputedAt() { return computedAt; }
    public void setComputedAt(long computedAt) { this.computedAt = computedAt; }

    public long getValidUntil() { return validUntil; }
    public void setValidUntil(long validUntil) { this.validUntil = validUntil; }

    public boolean isFromLocalData() { return isFromLocalData; }
    public void setFromLocalData(boolean fromLocalData) { this.isFromLocalData = fromLocalData; }

    public boolean isComplete() { return isComplete; }
    public void setComplete(boolean complete) { this.isComplete = complete; }

    public String getComputationMethod() { return computationMethod; }
    public void setComputationMethod(String computationMethod) { this.computationMethod = computationMethod; }

    // Source metadata
    public Map<String, Object> getSourceMetadata() { return new HashMap<>(sourceMetadata); }
    public void setSourceMetadata(Map<String, Object> sourceMetadata) {
        this.sourceMetadata = new HashMap<>(sourceMetadata);
    }

    public void addSourceMetadata(String key, Object value) {
        sourceMetadata.put(key, value);
    }

    public String getApiVersion() { return apiVersion; }
    public void setApiVersion(String apiVersion) { this.apiVersion = apiVersion; }

    public String getDatasetVersion() { return datasetVersion; }
    public void setDatasetVersion(String datasetVersion) { this.datasetVersion = datasetVersion; }

    // Quality indicators
    public double getReliabilityScore() { return reliabilityScore; }
    public void setReliabilityScore(double reliabilityScore) {
        this.reliabilityScore = Math.max(0.0, Math.min(1.0, reliabilityScore));
    }

    public Set<String> getMissingNutrients() { return new HashSet<>(missingNutrients); }
    public void setMissingNutrients(Set<String> missingNutrients) {
        this.missingNutrients = new HashSet<>(missingNutrients);
    }

    public void addMissingNutrient(String nutrient) {
        missingNutrients.add(nutrient);
    }

    public Set<String> getWarnings() { return new HashSet<>(warnings); }
    public void setWarnings(Set<String> warnings) {
        this.warnings = new HashSet<>(warnings);
    }

    public void addWarning(String warning) {
        warnings.add(warning);
    }

    // ========== INNER CLASSES ==========

    /**
     * Nutritional range information (min, max, median, percentiles)
     */
    public static class NutritionalRange {
        private Double min;
        private Double max;
        private Double median;
        private Double q25; // 25th percentile
        private Double q75; // 75th percentile

        public NutritionalRange() {}

        public NutritionalRange(Double min, Double max, Double median, Double q25, Double q75) {
            this.min = min;
            this.max = max;
            this.median = median;
            this.q25 = q25;
            this.q75 = q75;
        }

        public Integer calculatePercentile(Double value) {
            if (value == null || min == null || max == null) return null;
            if (value <= min) return 0;
            if (value >= max) return 100;

            // Simple linear interpolation (can be enhanced with actual percentile data)
            double range = max - min;
            double position = (value - min) / range;
            return (int) Math.round(position * 100);
        }

        // Getters and setters
        public Double getMin() { return min; }
        public void setMin(Double min) { this.min = min; }

        public Double getMax() { return max; }
        public void setMax(Double max) { this.max = max; }

        public Double getMedian() { return median; }
        public void setMedian(Double median) { this.median = median; }

        public Double getQ25() { return q25; }
        public void setQ25(Double q25) { this.q25 = q25; }

        public Double getQ75() { return q75; }
        public void setQ75(Double q75) { this.q75 = q75; }
    }

    /**
     * Product vs category nutrition comparison
     */
    public static class NutritionComparison {
        public Double caloriesVsAvg;      // % difference from category average
        public Double proteinsVsAvg;
        public Double carbohydratesVsAvg;
        public Double sugarsVsAvg;
        public Double fatVsAvg;
        public Double fiberVsAvg;
        public Double saltVsAvg;

        public String getComparisonText(String nutrient) {
            Double diff = getNutrientDifference(nutrient);
            if (diff == null) return "No data";

            if (Math.abs(diff) < 0.05) return "Similar to average";

            String direction = diff > 0 ? "higher" : "lower";
            int percentage = (int) Math.round(Math.abs(diff) * 100);

            return String.format("%d%% %s than average", percentage, direction);
        }

        private Double getNutrientDifference(String nutrient) {
            switch (nutrient.toLowerCase()) {
                case "calories": return caloriesVsAvg;
                case "proteins": return proteinsVsAvg;
                case "carbohydrates": return carbohydratesVsAvg;
                case "sugars": return sugarsVsAvg;
                case "fat": return fatVsAvg;
                case "fiber": return fiberVsAvg;
                case "salt": return saltVsAvg;
                default: return null;
            }
        }
    }

    // ========== OBJECT METHODS ==========

    @Override
    public String toString() {
        return String.format("CategoryStats{%s:%s, products=%d, reliability=%.2f, fresh=%b}",
                sourceId, categoryId, analyzedProducts, reliabilityScore, isFresh());
    }

    /**
     * Get comprehensive debug information
     */
    public String getDebugInfo() {
        return String.format(
                "CategoryStats Debug:\n" +
                        "  Category: %s (%s) [%s]\n" +
                        "  Products: %d total, %d analyzed (%.1f%% complete)\n" +
                        "  Nutrition: Calories=%.1f, Proteins=%.1f, Carbs=%.1f, Fat=%.1f\n" +
                        "  Quality: %s (%.2f reliability, %s)\n" +
                        "  Cache: %s, Valid until %s\n" +
                        "  Warnings: %s",
                categoryId, categoryName, sourceId,
                totalProducts, analyzedProducts, completenessRate * 100,
                avgCalories, avgProteins, avgCarbohydrates, avgFat,
                getQualityDescription(), reliabilityScore, isStatisticallySignificant() ? "significant" : "limited",
                computationMethod, new Date(validUntil),
                warnings.isEmpty() ? "None" : String.join(", ", warnings)
        );
    }
}