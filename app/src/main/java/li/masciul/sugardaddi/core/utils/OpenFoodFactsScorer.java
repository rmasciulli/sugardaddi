package li.masciul.sugardaddi.core.utils;

import li.masciul.sugardaddi.core.enums.DataSource;
import li.masciul.sugardaddi.core.models.FoodProduct;
import li.masciul.sugardaddi.data.network.ApiConfig;

/**
 * OpenFoodFactsScorer - Scoring strategy for OpenFoodFacts products
 *
 * VALUES WHAT OPENFOODFACTS PROVIDES:
 * - Product names and brands (excellent coverage)
 * - NutriScore grades (A-E nutritional rating)
 * - EcoScore grades (A-E environmental rating)
 * - Product images and thumbnails
 * - Data completeness metrics
 * - Nova group (processing level)
 * - Comprehensive ingredient lists
 *
 * SCORING BREAKDOWN (max 120 points):
 * - Name matching: 0-40 pts (base implementation)
 * - Brand matching: 0-30 pts (OFF-specific, added to name score)
 * - Category matching: 0-20 pts (base implementation)
 * - NutriScore: 10 pts if present
 * - EcoScore: 10 pts if present
 * - Image: 5 pts if present
 * - Data completeness: 0-20 pts (>80% = 20, >50% = 10)
 * - Has nutrition data: 15 pts
 * - Complete data (brand + name): 10 pts
 * - Favorite bonus: +15 pts (common)
 *
 * MAXIMUM SCORE: 120 points (before favorite bonus)
 * MAXIMUM WITH FAVORITE: 135 points
 *
 * WHY 120 MAX (vs Ciqual's 100):
 * - OpenFoodFacts provides more diverse attributes
 * - More data fields to score against
 * - Naturally scores higher when complete
 * - Fair given the breadth of available data
 *
 * ARCHITECTURE v3.0:
 * - Uses ScoringUtils for GENERIC methods only (name, category, favorite)
 * - Has PRIVATE helper methods for OFF-specific logic (brand, completeness)
 * - Keeps all OFF-specific scoring logic in this file
 *
 * @version 3.0 (Self-Contained OFF Logic)
 * @since Search Diversity Refactor
 */
public class OpenFoodFactsScorer extends BaseScorer<FoodProduct> {

    // ========== SINGLETON PATTERN ==========

    private static OpenFoodFactsScorer instance;

    /**
     * Get singleton instance (Android optimization - avoid repeated instantiation)
     */
    public static OpenFoodFactsScorer getInstance() {
        if (instance == null) {
            instance = new OpenFoodFactsScorer();
        }
        return instance;
    }

    /**
     * Private constructor for singleton
     */
    private OpenFoodFactsScorer() {
        // Private constructor
    }

    // ========== INTERFACE IMPLEMENTATION ==========

    @Override
    public DataSource getDataSource() {
        return DataSource.OPENFOODFACTS;
    }

    @Override
    public int getMaxScore() {
        return 120; // Higher than Ciqual due to more attributes
    }

    @Override
    public int getMinimumScore() {
        return ApiConfig.Scoring.MINIMUM_SCORE; // Standard threshold
    }

    @Override
    public String getScorerName() {
        return "OpenFoodFacts Scorer";
    }

    // ========== ENHANCED NAME MATCHING ==========

    /**
     * Override name matching to add brand matching
     *
     * OpenFoodFacts has excellent brand coverage, so we add brand matching
     * on top of the base name matching logic.
     */
    @Override
    protected int scoreNameMatch(FoodProduct product, String normalizedQuery, String language, StringBuilder breakdown) {
        // Get base name score (0-40 pts) from generic ScoringUtils
        int score = super.scoreNameMatch(product, normalizedQuery, language, breakdown);

        // Add brand matching (OpenFoodFacts specific - 0-30 pts)
        String brand = product.getBrand(language);
        if (brand != null && !brand.isEmpty()) {
            String normalizedBrand = SearchFilter.normalizeSearchTerm(brand);
            int brandScore = calculateBrandMatchScore(normalizedBrand, normalizedQuery);

            if (brandScore > 0) {
                String brandMatchType = brandScore >= ApiConfig.Scoring.OpenFoodFacts.EXACT_BRAND_MATCH ? "exact_brand" : "partial_brand";
                breakdown.append(brandMatchType).append(":").append(brandScore).append(" ");
                score += brandScore;
            }
        }

        return score;
    }

    // ========== SOURCE-SPECIFIC ATTRIBUTES ==========

    /**
     * Score OpenFoodFacts-specific attributes
     *
     * This is where we value what makes OpenFoodFacts unique:
     * - Quality indicators (NutriScore, EcoScore)
     * - Visual content (images)
     * - Data completeness
     * - Nutrition data availability
     */
    @Override
    protected int scoreSourceSpecificAttributes(FoodProduct product, String normalizedQuery, String language, StringBuilder breakdown) {
        int score = 0;

        // 1. NutriScore bonus (10 pts)
        // Indicates good nutritional data quality
        if (product.getNutriScore() != null && !product.getNutriScore().isEmpty()) {
            score += ApiConfig.Scoring.OpenFoodFacts.HAS_NUTRISCORE;
            breakdown.append("nutriscore:").append(ApiConfig.Scoring.OpenFoodFacts.HAS_NUTRISCORE).append(" ");
        }

        // 2. EcoScore bonus (10 pts)
        // Indicates environmental impact data available
        if (product.getEcoScore() != null && !product.getEcoScore().isEmpty()) {
            score += ApiConfig.Scoring.OpenFoodFacts.HAS_ECOSCORE;
            breakdown.append("ecoscore:").append(ApiConfig.Scoring.OpenFoodFacts.HAS_ECOSCORE).append(" ");
        }

        // 3. Image bonus (5 pts)
        // Products with images are more trustworthy
        if (product.getImageUrl() != null && !product.getImageUrl().isEmpty()) {
            score += ApiConfig.Scoring.OpenFoodFacts.HAS_IMAGE;
            breakdown.append("image:").append(ApiConfig.Scoring.OpenFoodFacts.HAS_IMAGE).append(" ");
        }

        // 4. Data completeness bonus (0-20 pts)
        // Rewards well-documented products
        int completenessScore = calculateCompletenessScore(product.getDataCompleteness());
        if (completenessScore > 0) {
            score += completenessScore;
            breakdown.append("completeness:").append(completenessScore).append(" ");
        }

        // 5. Nutrition data bonus (15 pts)
        // Having nutrition facts is valuable
        if (product.hasNutritionData()) {
            score += ApiConfig.Scoring.OpenFoodFacts.HAS_NUTRITION_DATA;
            breakdown.append("nutrition:").append(ApiConfig.Scoring.OpenFoodFacts.HAS_NUTRITION_DATA).append(" ");
        }

        // 6. Complete data bonus (10 pts)
        // Having both brand and name indicates quality entry
        String name = product.getName(language);
        String brand = product.getBrand(language);
        boolean hasName = name != null && !name.trim().isEmpty();
        boolean hasBrand = brand != null && !brand.trim().isEmpty();

        if (hasName && hasBrand) {
            score += ApiConfig.Scoring.OpenFoodFacts.COMPLETE_DATA_BONUS;
            breakdown.append("complete_data:").append(ApiConfig.Scoring.OpenFoodFacts.COMPLETE_DATA_BONUS).append(" ");
        }

        return score;
    }

    // ========== OPENFOODFACTS-SPECIFIC HELPER METHODS ==========

    /**
     * Calculate score for brand matching (OpenFoodFacts specific)
     *
     * Only OpenFoodFacts has brand information, so this logic stays here.
     *
     * PRECONDITION: Both brand and query must already be normalized
     *
     * SCORING (from ApiConfig.Scoring.OpenFoodFacts):
     * - Exact match: EXACT_BRAND_MATCH points (30)
     * - Partial match: PARTIAL_BRAND_MATCH points (15)
     * - No match: 0 points
     *
     * @param normalizedBrand Normalized brand name
     * @param normalizedQuery Normalized search query
     * @return Score (0-30)
     */
    private static int calculateBrandMatchScore(String normalizedBrand, String normalizedQuery) {
        if (normalizedBrand == null || normalizedQuery == null) {
            return 0;
        }

        if (normalizedBrand.equals(normalizedQuery)) {
            return ApiConfig.Scoring.OpenFoodFacts.EXACT_BRAND_MATCH;
        } else if (normalizedBrand.contains(normalizedQuery) || normalizedQuery.contains(normalizedBrand)) {
            return ApiConfig.Scoring.OpenFoodFacts.PARTIAL_BRAND_MATCH;
        }

        return 0; // No match
    }

    /**
     * Calculate score based on data completeness (OpenFoodFacts specific)
     *
     * OpenFoodFacts provides a completeness metric (0.0 to 1.0) that indicates
     * how complete the product data is. This is specific to OFF's data model.
     *
     * SCORING (from ApiConfig.Scoring.OpenFoodFacts):
     * - Very complete (>80%): COMPLETENESS_HIGH points (20)
     * - Moderately complete (>50%): COMPLETENESS_MEDIUM points (10)
     * - Incomplete: 0 points
     *
     * @param completeness Completeness ratio (0.0 to 1.0)
     * @return Score (0-20)
     */
    private static int calculateCompletenessScore(float completeness) {
        if (completeness > 0.8f) {
            return ApiConfig.Scoring.OpenFoodFacts.COMPLETENESS_HIGH;
        } else if (completeness > 0.5f) {
            return ApiConfig.Scoring.OpenFoodFacts.COMPLETENESS_MEDIUM;
        }
        return 0; // Low completeness
    }
}