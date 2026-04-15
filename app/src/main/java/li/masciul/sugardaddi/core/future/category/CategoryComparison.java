package li.masciul.sugardaddi.core.future.category;

import li.masciul.sugardaddi.core.future.analysis.NutrientProfile;
import li.masciul.sugardaddi.core.models.FoodProduct;
import li.masciul.sugardaddi.core.models.Nutrition;

import java.util.*;

/**
 * CategoryComparison - Product vs Category Analytics
 *
 * STATUS: COMPLEX LOGIC READY, INTEGRATION TODO
 * See: core/future/category/README.md for details
 *
 * CURRENT USAGE:
 * - CategoryCapable.compareToCategory() should return this
 * - Currently not called from UI
 * - All comparison logic implemented
 *
 * TODO:
 * - Connect to CategoryStats data source
 * - Add UI for displaying comparisons
 * - Implement percentile ranking visuals
 * - Add "better alternative" suggestions
 *
 * Provides comprehensive comparison between a product and its category:
 * - Nutritional benchmarking
 * - Quality assessment
 * - Percentile ranking
 * - Health score analysis
 */
public class CategoryComparison {

    // ========== COMPARISON METADATA ==========
    private String comparisonId;           // Unique identifier for this comparison
    private FoodProduct product;           // Product being compared
    private CategoryStats categoryStats;   // Category statistics for comparison
    private String categoryId;             // Category identifier
    private String sourceId;               // Data source (ciqual, openfoodfacts, logmeal)
    private long generatedAt;              // When comparison was generated
    private String language;               // Language for comparison texts

    // ========== OVERALL COMPARISON SCORES ==========
    private double overallScore;           // 0.0-1.0 overall comparison score
    private ComparisonGrade overallGrade;  // A+, A, B+, B, C+, C, D, F
    private String overallSummary;         // "Better than average", "Similar to category"
    private boolean isStatisticallyValid; // True if category has enough data for comparison

    // ========== NUTRITIONAL COMPARISONS ==========
    private NutrientComparison calories;
    private NutrientComparison proteins;
    private NutrientComparison carbohydrates;
    private NutrientComparison sugars;
    private NutrientComparison fat;
    private NutrientComparison saturatedFat;
    private NutrientComparison fiber;
    private NutrientComparison salt;
    private NutrientComparison sodium;

    // ========== EXTENDED NUTRITIONAL COMPARISONS ==========
    private NutrientComparison calcium;
    private NutrientComparison iron;
    private NutrientComparison vitaminC;
    private NutrientComparison vitaminD;

    // ========== QUALITY INDICATORS ==========
    private QualityComparison dataQuality;    // Data completeness vs category
    private QualityComparison sourceReliability; // Source reliability comparison
    private double nutritionCompleteness;     // % of nutrition data available

    // ========== RANKING & POSITIONING ==========
    private Map<String, Integer> percentileRankings; // Nutrient → percentile (0-100)
    private NutrientProfile nutritionProfile;        // BALANCED, HIGH_PROTEIN, LOW_SUGAR, etc.
    private Set<String> standoutNutrients;           // Nutrients significantly above/below average
    private Set<String> concerningNutrients;        // Nutrients that may be concerning

    // ========== RECOMMENDATIONS ==========
    private List<ComparisonRecommendation> recommendations; // Health and dietary recommendations
    private List<String> positiveAspects;                  // What's good about this product
    private List<String> improvementAreas;                 // What could be better
    private boolean recommendForCategory;                   // Is this a good example of the category?

    // ========== COMPARISON CONFIDENCE ==========
    private double comparisonConfidence;   // 0.0-1.0 confidence in comparison accuracy
    private Set<String> limitingFactors;  // What limits comparison accuracy
    private Set<String> warnings;         // Data quality or comparison warnings

    // ========== CONSTRUCTORS ==========

    public CategoryComparison() {
        this.generatedAt = System.currentTimeMillis();
        this.comparisonId = generateComparisonId();
        this.percentileRankings = new HashMap<>();
        this.standoutNutrients = new HashSet<>();
        this.concerningNutrients = new HashSet<>();
        this.recommendations = new ArrayList<>();
        this.positiveAspects = new ArrayList<>();
        this.improvementAreas = new ArrayList<>();
        this.limitingFactors = new HashSet<>();
        this.warnings = new HashSet<>();
        this.language = "en";
    }

    /**
     * Create comparison between product and category
     */
    public CategoryComparison(FoodProduct product, CategoryStats categoryStats) {
        this();
        this.product = product;
        this.categoryStats = categoryStats;
        this.categoryId = categoryStats.getCategoryId();
        this.sourceId = categoryStats.getSourceId();

        // Perform comparison analysis
        performComparison();
    }

    /**
     * Create comparison with language preference
     */
    public CategoryComparison(FoodProduct product, CategoryStats categoryStats, String language) {
        this(product, categoryStats);
        this.language = language;
        regenerateTexts(); // Regenerate texts in preferred language
    }

    // ========== COMPARISON ANALYSIS ==========

    /**
     * Perform comprehensive comparison analysis
     */
    private void performComparison() {
        if (!isComparisonPossible()) {
            createInvalidComparison();
            return;
        }

        // 1. Basic validation
        validateComparisonInputs();

        // 2. Nutritional comparisons
        performNutritionalComparisons();

        // 3. Quality comparisons
        performQualityComparisons();

        // 4. Calculate rankings
        calculatePercentileRankings();

        // 5. Determine nutrition profile
        determineNutritionProfile();

        // 6. Generate recommendations
        generateRecommendations();

        // 7. Calculate overall scores
        calculateOverallScores();

        // 8. Generate summary texts
        generateSummaryTexts();
    }

    private boolean isComparisonPossible() {
        return product != null &&
                categoryStats != null &&
                product.hasNutritionData() &&
                categoryStats.hasNutritionData();
    }

    private void createInvalidComparison() {
        this.overallScore = 0.0;
        this.overallGrade = ComparisonGrade.UNKNOWN;
        this.overallSummary = "Comparison not possible - insufficient data";
        this.isStatisticallyValid = false;
        this.comparisonConfidence = 0.0;

        if (product == null) addWarning("No product provided");
        if (categoryStats == null) addWarning("No category statistics available");
        if (product != null && !product.hasNutritionData()) addWarning("Product lacks nutrition data");
        if (categoryStats != null && !categoryStats.hasNutritionData()) addWarning("Category lacks nutrition averages");
    }

    private void validateComparisonInputs() {
        // Check statistical validity
        this.isStatisticallyValid = categoryStats.isStatisticallySignificant();
        if (!isStatisticallyValid) {
            addLimitingFactor("Small sample size in category (" + categoryStats.getAnalyzedProducts() + " products)");
        }

        // Check data freshness
        if (!categoryStats.isFresh()) {
            addWarning("Category data may be outdated");
        }

        // Calculate base confidence
        this.comparisonConfidence = calculateBaseConfidence();
    }

    private void performNutritionalComparisons() {
        Nutrition productNutrition = product.getNutrition();

        this.calories = compareNutrient("calories", productNutrition.getEnergyKcal(), categoryStats.getAvgCalories());
        this.proteins = compareNutrient("proteins", productNutrition.getProteins(), categoryStats.getAvgProteins());
        this.carbohydrates = compareNutrient("carbohydrates", productNutrition.getCarbohydrates(), categoryStats.getAvgCarbohydrates());
        this.sugars = compareNutrient("sugars", productNutrition.getSugars(), categoryStats.getAvgSugars());
        this.fat = compareNutrient("fat", productNutrition.getFat(), categoryStats.getAvgFat());
        this.saturatedFat = compareNutrient("saturatedFat", productNutrition.getSaturatedFat(), categoryStats.getAvgSaturatedFat());
        this.fiber = compareNutrient("fiber", productNutrition.getFiber(), categoryStats.getAvgFiber());
        this.salt = compareNutrient("salt", productNutrition.getSalt(), categoryStats.getAvgSalt());
        this.sodium = compareNutrient("sodium", productNutrition.getSodium(), categoryStats.getAvgSodium());

        // Extended nutrients (if available)
        this.calcium = compareNutrient("calcium", productNutrition.getCalcium(), categoryStats.getAvgCalcium());
        this.iron = compareNutrient("iron", productNutrition.getIron(), categoryStats.getAvgIron());
        this.vitaminC = compareNutrient("vitaminC", productNutrition.getVitaminC(), categoryStats.getAvgVitaminC());
        this.vitaminD = compareNutrient("vitaminD", productNutrition.getVitaminD(), categoryStats.getAvgVitaminD());
    }

    private NutrientComparison compareNutrient(String nutrientName, Double productValue, Double categoryAvg) {
        if (productValue == null || categoryAvg == null) {
            return NutrientComparison.unavailable(nutrientName);
        }

        if (categoryAvg == 0) {
            return NutrientComparison.unavailable(nutrientName, "Category average is zero");
        }

        double percentageDiff = (productValue - categoryAvg) / categoryAvg;
        int percentileRank = categoryStats.getPercentileRank(nutrientName, productValue);

        NutrientComparison comparison = new NutrientComparison(
                nutrientName, productValue, categoryAvg, percentageDiff, percentileRank);

        // Determine if this is a standout nutrient
        if (Math.abs(percentageDiff) > 0.25) { // 25% difference
            standoutNutrients.add(nutrientName);
        }

        // Check for concerning levels (context-dependent)
        if (isConcerningLevel(nutrientName, productValue, percentageDiff)) {
            concerningNutrients.add(nutrientName);
        }

        return comparison;
    }

    private boolean isConcerningLevel(String nutrient, Double value, double percentageDiff) {
        // Define concerning levels based on nutrition science
        switch (nutrient.toLowerCase()) {
            case "salt":
            case "sodium":
                return percentageDiff > 0.5; // 50% above average salt
            case "sugars":
                return percentageDiff > 0.4; // 40% above average sugar
            case "saturatedfat":
                return percentageDiff > 0.3; // 30% above average saturated fat
            case "calories":
                return percentageDiff > 0.6; // 60% above average calories
            default:
                return false;
        }
    }

    private void performQualityComparisons() {
        // Data quality comparison
        double productCompleteness = product.getNutrition().getCompleteness();
        double categoryCompleteness = categoryStats.getCompletenessRate();

        this.dataQuality = new QualityComparison("dataQuality", productCompleteness, categoryCompleteness);
        this.nutritionCompleteness = productCompleteness;

        // Source reliability comparison
        double productReliability = getProductReliability();
        double categoryReliability = categoryStats.getReliabilityScore();

        this.sourceReliability = new QualityComparison("sourceReliability", productReliability, categoryReliability);
    }

    private double getProductReliability() {
        String productSource = product.getSourceIdentifier().getSourceId();
        switch (productSource) {
            case "ciqual": return 0.95;
            case "openfoodfacts": return 0.80;
            case "logmeal": return 0.85;
            default: return 0.60;
        }
    }

    private void calculatePercentileRankings() {
        percentileRankings.clear();

        addPercentileIfAvailable("calories", calories);
        addPercentileIfAvailable("proteins", proteins);
        addPercentileIfAvailable("carbohydrates", carbohydrates);
        addPercentileIfAvailable("sugars", sugars);
        addPercentileIfAvailable("fat", fat);
        addPercentileIfAvailable("fiber", fiber);
        addPercentileIfAvailable("salt", salt);
    }

    private void addPercentileIfAvailable(String nutrient, NutrientComparison comparison) {
        if (comparison != null && comparison.isAvailable() && comparison.getPercentileRank() != null) {
            percentileRankings.put(nutrient, comparison.getPercentileRank());
        }
    }

    private void determineNutritionProfile() {
        // Analyze nutrition profile based on standout nutrients and percentile rankings

        if (isHighProtein() && isLowSugar()) {
            this.nutritionProfile = NutrientProfile.HIGH_PROTEIN_LOW_SUGAR;
        } else if (isHighProtein()) {
            this.nutritionProfile = NutrientProfile.HIGH_PROTEIN;
        } else if (isLowSugar()) {
            this.nutritionProfile = NutrientProfile.LOW_SUGAR;
        } else if (isHighFiber()) {
            this.nutritionProfile = NutrientProfile.HIGH_FIBER;
        } else if (isLowCalorie()) {
            this.nutritionProfile = NutrientProfile.LOW_CALORIE;
        } else if (isBalanced()) {
            this.nutritionProfile = NutrientProfile.BALANCED;
        } else {
            this.nutritionProfile = NutrientProfile.STANDARD;
        }
    }

    private boolean isHighProtein() {
        Integer proteinPercentile = percentileRankings.get("proteins");
        return proteinPercentile != null && proteinPercentile >= 75;
    }

    private boolean isLowSugar() {
        Integer sugarPercentile = percentileRankings.get("sugars");
        return sugarPercentile != null && sugarPercentile <= 25;
    }

    private boolean isHighFiber() {
        Integer fiberPercentile = percentileRankings.get("fiber");
        return fiberPercentile != null && fiberPercentile >= 75;
    }

    private boolean isLowCalorie() {
        Integer caloriePercentile = percentileRankings.get("calories");
        return caloriePercentile != null && caloriePercentile <= 30;
    }

    private boolean isBalanced() {
        // Check if most nutrients are within normal range (25-75 percentile)
        long normalNutrients = percentileRankings.values().stream()
                .mapToLong(percentile -> (percentile >= 25 && percentile <= 75) ? 1 : 0)
                .sum();

        return (double) normalNutrients / percentileRankings.size() >= 0.7; // 70% or more in normal range
    }

    private void generateRecommendations() {
        recommendations.clear();
        positiveAspects.clear();
        improvementAreas.clear();

        // Generate positive aspects
        for (String nutrient : standoutNutrients) {
            NutrientComparison comparison = getNutrientComparison(nutrient);
            if (comparison != null && comparison.isPositiveStandout()) {
                positiveAspects.add(comparison.getPositiveDescription(language));
            }
        }

        // Generate improvement areas
        for (String nutrient : concerningNutrients) {
            NutrientComparison comparison = getNutrientComparison(nutrient);
            if (comparison != null) {
                improvementAreas.add(comparison.getConcernDescription(language));
            }
        }

        // Generate specific recommendations
        generateSpecificRecommendations();

        // Determine if product is recommended for category
        this.recommendForCategory = calculateCategoryRecommendation();
    }

    private void generateSpecificRecommendations() {
        // High sodium/salt recommendation
        if (concerningNutrients.contains("salt") || concerningNutrients.contains("sodium")) {
            recommendations.add(new ComparisonRecommendation(
                    "Consider lower sodium alternatives within this category",
                    "health", "high_sodium"));
        }

        // High sugar recommendation
        if (concerningNutrients.contains("sugars")) {
            recommendations.add(new ComparisonRecommendation(
                    "Look for products with less added sugar in this category",
                    "health", "high_sugar"));
        }

        // High protein positive recommendation
        if (standoutNutrients.contains("proteins") && proteins.getPercentageDifference() > 0) {
            recommendations.add(new ComparisonRecommendation(
                    "Excellent protein source compared to similar products",
                    "positive", "high_protein"));
        }

        // High fiber positive recommendation
        if (standoutNutrients.contains("fiber") && fiber.getPercentageDifference() > 0) {
            recommendations.add(new ComparisonRecommendation(
                    "Good source of fiber compared to category average",
                    "positive", "high_fiber"));
        }
    }

    private boolean calculateCategoryRecommendation() {
        // Recommend if product is in top 40% overall and has no major concerns
        return overallScore >= 0.6 && concerningNutrients.isEmpty();
    }

    private void calculateOverallScores() {
        // Calculate weighted overall score
        double nutritionScore = calculateNutritionScore();
        double qualityScore = calculateQualityScore();
        double profileScore = calculateProfileScore();

        this.overallScore = (nutritionScore * 0.6 + qualityScore * 0.2 + profileScore * 0.2);
        this.overallGrade = determineGrade(overallScore);

        // Adjust confidence based on limiting factors
        adjustConfidenceForLimitations();
    }

    private double calculateNutritionScore() {
        List<Double> scores = new ArrayList<>();

        // Positive nutrients (higher is better)
        addNutrientScore(scores, proteins, true);
        addNutrientScore(scores, fiber, true);
        addNutrientScore(scores, calcium, true);
        addNutrientScore(scores, iron, true);
        addNutrientScore(scores, vitaminC, true);
        addNutrientScore(scores, vitaminD, true);

        // Negative nutrients (lower is better)
        addNutrientScore(scores, sugars, false);
        addNutrientScore(scores, salt, false);
        addNutrientScore(scores, saturatedFat, false);

        // Neutral nutrients (closer to average is better)
        addNeutralNutrientScore(scores, calories);
        addNeutralNutrientScore(scores, carbohydrates);
        addNeutralNutrientScore(scores, fat);

        return scores.isEmpty() ? 0.5 : scores.stream().mapToDouble(Double::doubleValue).average().orElse(0.5);
    }

    private void addNutrientScore(List<Double> scores, NutrientComparison comparison, boolean higherIsBetter) {
        if (comparison == null || !comparison.isAvailable()) return;

        Integer percentile = comparison.getPercentileRank();
        if (percentile == null) return;

        double score = higherIsBetter ? percentile / 100.0 : (100 - percentile) / 100.0;
        scores.add(score);
    }

    private void addNeutralNutrientScore(List<Double> scores, NutrientComparison comparison) {
        if (comparison == null || !comparison.isAvailable()) return;

        Integer percentile = comparison.getPercentileRank();
        if (percentile == null) return;

        // For neutral nutrients, closer to 50th percentile is better
        double distance = Math.abs(percentile - 50) / 50.0; // 0-1 scale
        double score = 1.0 - distance; // Invert so closer to average = higher score
        scores.add(score);
    }

    private double calculateQualityScore() {
        double completenessScore = nutritionCompleteness;
        double reliabilityScore = sourceReliability.isAvailable() ?
                sourceReliability.getProductValue() : 0.6;

        return (completenessScore + reliabilityScore) / 2.0;
    }

    private double calculateProfileScore() {
        // Score based on nutrition profile desirability
        switch (nutritionProfile) {
            case HIGH_PROTEIN_LOW_SUGAR: return 0.95;
            case HIGH_PROTEIN: return 0.85;
            case HIGH_FIBER: return 0.85;
            case LOW_SUGAR: return 0.80;
            case LOW_CALORIE: return 0.75;
            case BALANCED: return 0.70;
            case STANDARD: return 0.50;
            default: return 0.40;
        }
    }

    private ComparisonGrade determineGrade(double score) {
        if (score >= 0.95) return ComparisonGrade.A_PLUS;
        if (score >= 0.90) return ComparisonGrade.A;
        if (score >= 0.85) return ComparisonGrade.B_PLUS;
        if (score >= 0.80) return ComparisonGrade.B;
        if (score >= 0.75) return ComparisonGrade.C_PLUS;
        if (score >= 0.70) return ComparisonGrade.C;
        if (score >= 0.60) return ComparisonGrade.D;
        return ComparisonGrade.F;
    }

    private void adjustConfidenceForLimitations() {
        double penalty = limitingFactors.size() * 0.1; // 10% penalty per limiting factor
        this.comparisonConfidence = Math.max(0.1, this.comparisonConfidence - penalty);
    }

    private void generateSummaryTexts() {
        // Generate overall summary based on grade and profile
        if (overallGrade.ordinal() <= ComparisonGrade.B.ordinal()) {
            this.overallSummary = "Excellent choice in this category";
        } else if (overallGrade.ordinal() <= ComparisonGrade.C.ordinal()) {
            this.overallSummary = "Good choice in this category";
        } else if (overallGrade.ordinal() <= ComparisonGrade.D.ordinal()) {
            this.overallSummary = "Average for this category";
        } else {
            this.overallSummary = "Below average for this category";
        }

        // Add profile information to summary
        if (nutritionProfile != NutrientProfile.STANDARD) {
            this.overallSummary += " (" + nutritionProfile.getDisplayName() + ")";
        }
    }

    // ========== UTILITY METHODS ==========

    private NutrientComparison getNutrientComparison(String nutrient) {
        switch (nutrient.toLowerCase()) {
            case "calories": return calories;
            case "proteins": return proteins;
            case "carbohydrates": return carbohydrates;
            case "sugars": return sugars;
            case "fat": return fat;
            case "saturatedfat": return saturatedFat;
            case "fiber": return fiber;
            case "salt": return salt;
            case "sodium": return sodium;
            case "calcium": return calcium;
            case "iron": return iron;
            case "vitaminc": return vitaminC;
            case "vitamind": return vitaminD;
            default: return null;
        }
    }

    private double calculateBaseConfidence() {
        double sourceConfidence = getProductReliability();
        double categoryConfidence = categoryStats.getReliabilityScore();
        double dataConfidence = Math.min(nutritionCompleteness, categoryStats.getCompletenessRate());

        return (sourceConfidence + categoryConfidence + dataConfidence) / 3.0;
    }

    private void addWarning(String warning) {
        warnings.add(warning);
    }

    private void addLimitingFactor(String factor) {
        limitingFactors.add(factor);
    }

    private void regenerateTexts() {
        // Regenerate all text descriptions in the new language
        // This method would be called when language changes
        generateSummaryTexts();
    }

    private String generateComparisonId() {
        return "comp_" + System.currentTimeMillis() + "_" +
                (int)(Math.random() * 1000);
    }

    // ========== PUBLIC UTILITY METHODS ==========

    /**
     * Get top nutrients (best performing)
     */
    public List<String> getTopNutrients(int limit) {
        return percentileRankings.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(limit)
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Get bottom nutrients (worst performing)
     */
    public List<String> getBottomNutrients(int limit) {
        return percentileRankings.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .limit(limit)
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Get formatted comparison summary for UI
     */
    public String getFormattedSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append(overallSummary);

        if (isStatisticallyValid) {
            summary.append(String.format(" (Grade: %s, Confidence: %.0f%%)",
                    overallGrade.getDisplayName(), comparisonConfidence * 100));
        } else {
            summary.append(" (Limited data available)");
        }

        return summary.toString();
    }

    /**
     * Check if comparison is reliable enough for decision making
     */
    public boolean isReliableForDecisions() {
        return isStatisticallyValid &&
                comparisonConfidence >= 0.7 &&
                categoryStats.isFresh() &&
                warnings.isEmpty();
    }

    // ========== GETTERS AND SETTERS ==========

    public String getComparisonId() { return comparisonId; }
    public void setComparisonId(String comparisonId) { this.comparisonId = comparisonId; }

    public FoodProduct getProduct() { return product; }
    public void setProduct(FoodProduct product) { this.product = product; }

    public CategoryStats getCategoryStats() { return categoryStats; }
    public void setCategoryStats(CategoryStats categoryStats) { this.categoryStats = categoryStats; }

    public String getCategoryId() { return categoryId; }
    public void setCategoryId(String categoryId) { this.categoryId = categoryId; }

    public String getSourceId() { return sourceId; }
    public void setSourceId(String sourceId) { this.sourceId = sourceId; }

    public long getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(long generatedAt) { this.generatedAt = generatedAt; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) {
        this.language = language;
        regenerateTexts();
    }

    // Overall comparison scores
    public double getOverallScore() { return overallScore; }
    public void setOverallScore(double overallScore) { this.overallScore = overallScore; }

    public ComparisonGrade getOverallGrade() { return overallGrade; }
    public void setOverallGrade(ComparisonGrade overallGrade) { this.overallGrade = overallGrade; }

    public String getOverallSummary() { return overallSummary; }
    public void setOverallSummary(String overallSummary) { this.overallSummary = overallSummary; }

    public boolean isStatisticallyValid() { return isStatisticallyValid; }
    public void setStatisticallyValid(boolean statisticallyValid) { this.isStatisticallyValid = statisticallyValid; }

    // Nutritional comparisons
    public NutrientComparison getCalories() { return calories; }
    public void setCalories(NutrientComparison calories) { this.calories = calories; }

    public NutrientComparison getProteins() { return proteins; }
    public void setProteins(NutrientComparison proteins) { this.proteins = proteins; }

    public NutrientComparison getCarbohydrates() { return carbohydrates; }
    public void setCarbohydrates(NutrientComparison carbohydrates) { this.carbohydrates = carbohydrates; }

    public NutrientComparison getSugars() { return sugars; }
    public void setSugars(NutrientComparison sugars) { this.sugars = sugars; }

    public NutrientComparison getFat() { return fat; }
    public void setFat(NutrientComparison fat) { this.fat = fat; }

    public NutrientComparison getSaturatedFat() { return saturatedFat; }
    public void setSaturatedFat(NutrientComparison saturatedFat) { this.saturatedFat = saturatedFat; }

    public NutrientComparison getFiber() { return fiber; }
    public void setFiber(NutrientComparison fiber) { this.fiber = fiber; }

    public NutrientComparison getSalt() { return salt; }
    public void setSalt(NutrientComparison salt) { this.salt = salt; }

    public NutrientComparison getSodium() { return sodium; }
    public void setSodium(NutrientComparison sodium) { this.sodium = sodium; }

    // Extended nutrients
    public NutrientComparison getCalcium() { return calcium; }
    public void setCalcium(NutrientComparison calcium) { this.calcium = calcium; }

    public NutrientComparison getIron() { return iron; }
    public void setIron(NutrientComparison iron) { this.iron = iron; }

    public NutrientComparison getVitaminC() { return vitaminC; }
    public void setVitaminC(NutrientComparison vitaminC) { this.vitaminC = vitaminC; }

    public NutrientComparison getVitaminD() { return vitaminD; }
    public void setVitaminD(NutrientComparison vitaminD) { this.vitaminD = vitaminD; }

    // Quality indicators
    public QualityComparison getDataQuality() { return dataQuality; }
    public void setDataQuality(QualityComparison dataQuality) { this.dataQuality = dataQuality; }

    public QualityComparison getSourceReliability() { return sourceReliability; }
    public void setSourceReliability(QualityComparison sourceReliability) { this.sourceReliability = sourceReliability; }

    public double getNutritionCompleteness() { return nutritionCompleteness; }
    public void setNutritionCompleteness(double nutritionCompleteness) { this.nutritionCompleteness = nutritionCompleteness; }

    // Rankings and profile
    public Map<String, Integer> getPercentileRankings() { return new HashMap<>(percentileRankings); }
    public void setPercentileRankings(Map<String, Integer> percentileRankings) {
        this.percentileRankings = new HashMap<>(percentileRankings);
    }

    public NutrientProfile getNutritionProfile() { return nutritionProfile; }
    public void setNutritionProfile(NutrientProfile nutritionProfile) { this.nutritionProfile = nutritionProfile; }

    public Set<String> getStandoutNutrients() { return new HashSet<>(standoutNutrients); }
    public void setStandoutNutrients(Set<String> standoutNutrients) {
        this.standoutNutrients = new HashSet<>(standoutNutrients);
    }

    public Set<String> getConcerningNutrients() { return new HashSet<>(concerningNutrients); }
    public void setConcerningNutrients(Set<String> concerningNutrients) {
        this.concerningNutrients = new HashSet<>(concerningNutrients);
    }

    // Recommendations
    public List<ComparisonRecommendation> getRecommendations() { return new ArrayList<>(recommendations); }
    public void setRecommendations(List<ComparisonRecommendation> recommendations) {
        this.recommendations = new ArrayList<>(recommendations);
    }

    public List<String> getPositiveAspects() { return new ArrayList<>(positiveAspects); }
    public void setPositiveAspects(List<String> positiveAspects) {
        this.positiveAspects = new ArrayList<>(positiveAspects);
    }

    public List<String> getImprovementAreas() { return new ArrayList<>(improvementAreas); }
    public void setImprovementAreas(List<String> improvementAreas) {
        this.improvementAreas = new ArrayList<>(improvementAreas);
    }

    public boolean isRecommendForCategory() { return recommendForCategory; }
    public void setRecommendForCategory(boolean recommendForCategory) { this.recommendForCategory = recommendForCategory; }

    // Confidence and warnings
    public double getComparisonConfidence() { return comparisonConfidence; }
    public void setComparisonConfidence(double comparisonConfidence) {
        this.comparisonConfidence = Math.max(0.0, Math.min(1.0, comparisonConfidence));
    }

    public Set<String> getLimitingFactors() { return new HashSet<>(limitingFactors); }
    public void setLimitingFactors(Set<String> limitingFactors) {
        this.limitingFactors = new HashSet<>(limitingFactors);
    }

    public Set<String> getWarnings() { return new HashSet<>(warnings); }
    public void setWarnings(Set<String> warnings) {
        this.warnings = new HashSet<>(warnings);
    }

    // ========== INNER CLASSES ==========

    /**
     * Individual nutrient comparison
     */
    public static class NutrientComparison {
        private String nutrientName;
        private Double productValue;
        private Double categoryAverage;
        private Double percentageDifference; // -1.0 to +inf (e.g., 0.25 = 25% higher)
        private Integer percentileRank;      // 0-100
        private boolean available;
        private String unavailableReason;

        public NutrientComparison(String nutrientName, Double productValue, Double categoryAverage,
                                  Double percentageDifference, Integer percentileRank) {
            this.nutrientName = nutrientName;
            this.productValue = productValue;
            this.categoryAverage = categoryAverage;
            this.percentageDifference = percentageDifference;
            this.percentileRank = percentileRank;
            this.available = true;
        }

        public static NutrientComparison unavailable(String nutrientName) {
            return unavailable(nutrientName, "Data not available");
        }

        public static NutrientComparison unavailable(String nutrientName, String reason) {
            NutrientComparison comparison = new NutrientComparison(nutrientName, null, null, null, null);
            comparison.available = false;
            comparison.unavailableReason = reason;
            return comparison;
        }

        public String getComparisonText(String language) {
            if (!available) return "No data available";

            if (Math.abs(percentageDifference) < 0.05) {
                return "Similar to category average";
            }

            String direction = percentageDifference > 0 ? "higher" : "lower";
            int percentage = (int) Math.round(Math.abs(percentageDifference) * 100);

            return String.format("%d%% %s than category average", percentage, direction);
        }

        public String getPercentileText() {
            if (!available || percentileRank == null) return "No ranking available";

            if (percentileRank >= 90) return "Top 10%";
            if (percentileRank >= 75) return "Top 25%";
            if (percentileRank >= 60) return "Above average";
            if (percentileRank >= 40) return "Average";
            if (percentileRank >= 25) return "Below average";
            if (percentileRank >= 10) return "Bottom 25%";
            return "Bottom 10%";
        }

        public boolean isPositiveStandout() {
            return available && percentageDifference != null && percentageDifference > 0.25;
        }

        public String getPositiveDescription(String language) {
            if (!isPositiveStandout()) return "";

            int percentage = (int) Math.round(percentageDifference * 100);
            return String.format("Excellent %s content (%d%% above average)",
                    nutrientName, percentage);
        }

        public String getConcernDescription(String language) {
            if (!available || percentageDifference == null) return "";

            if (percentageDifference > 0.3) {
                int percentage = (int) Math.round(percentageDifference * 100);
                return String.format("High %s content (%d%% above average)",
                        nutrientName, percentage);
            }

            return "";
        }

        // Getters
        public String getNutrientName() { return nutrientName; }
        public Double getProductValue() { return productValue; }
        public Double getCategoryAverage() { return categoryAverage; }
        public Double getPercentageDifference() { return percentageDifference; }
        public Integer getPercentileRank() { return percentileRank; }
        public boolean isAvailable() { return available; }
        public String getUnavailableReason() { return unavailableReason; }
    }

    /**
     * Quality comparison (data completeness, source reliability, etc.)
     */
    public static class QualityComparison {
        private String qualityMetric;
        private double productValue;
        private double categoryValue;
        private boolean available;

        public QualityComparison(String qualityMetric, double productValue, double categoryValue) {
            this.qualityMetric = qualityMetric;
            this.productValue = productValue;
            this.categoryValue = categoryValue;
            this.available = true;
        }

        public String getComparisonText() {
            if (!available) return "No comparison available";

            if (Math.abs(productValue - categoryValue) < 0.05) {
                return "Similar to category";
            }

            String direction = productValue > categoryValue ? "better" : "lower";
            return String.format("Quality is %s than category average", direction);
        }

        // Getters
        public String getQualityMetric() { return qualityMetric; }
        public double getProductValue() { return productValue; }
        public double getCategoryValue() { return categoryValue; }
        public boolean isAvailable() { return available; }
    }

    /**
     * Comparison recommendation
     */
    public static class ComparisonRecommendation {
        private String text;
        private String type; // "health", "positive", "caution"
        private String category; // "high_protein", "low_sugar", etc.

        public ComparisonRecommendation(String text, String type, String category) {
            this.text = text;
            this.type = type;
            this.category = category;
        }

        // Getters
        public String getText() { return text; }
        public String getType() { return type; }
        public String getCategory() { return category; }
    }

    // ========== ENUMS ==========

    public enum ComparisonGrade {
        A_PLUS("A+"), A("A"), B_PLUS("B+"), B("B"),
        C_PLUS("C+"), C("C"), D("D"), F("F"), UNKNOWN("?");

        private final String displayName;

        ComparisonGrade(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() { return displayName; }
    }

    // ========== OBJECT METHODS ==========

    @Override
    public String toString() {
        return String.format("CategoryComparison{product=%s, category=%s, grade=%s, score=%.2f}",
                product != null ? product.getOriginalId() : "null",
                categoryId, overallGrade.getDisplayName(), overallScore);
    }

    /**
     * Get detailed debug information
     */
    public String getDebugInfo() {
        return String.format(
                "CategoryComparison Debug:\n" +
                        "  Product: %s\n" +
                        "  Category: %s (%s)\n" +
                        "  Overall: %s (%.2f) - %s\n" +
                        "  Profile: %s\n" +
                        "  Confidence: %.2f (Valid: %b)\n" +
                        "  Standout: %s\n" +
                        "  Concerns: %s\n" +
                        "  Recommendations: %d\n" +
                        "  Warnings: %s",
                product != null ? product.getDisplayName("en") : "null",
                categoryId, sourceId,
                overallGrade.getDisplayName(), overallScore, overallSummary,
                nutritionProfile != null ? nutritionProfile.getDisplayName() : "Unknown",
                comparisonConfidence, isStatisticallyValid,
                standoutNutrients,
                concerningNutrients,
                recommendations.size(),
                warnings.isEmpty() ? "None" : String.join(", ", warnings)
        );
    }
}