package li.masciul.sugardaddi.core.utils;

import li.masciul.sugardaddi.core.enums.DataSource;
import li.masciul.sugardaddi.core.models.FoodProduct;
import li.masciul.sugardaddi.data.network.ApiConfig;

/**
 * CiqualScorer - Scoring strategy for Ciqual products
 *
 * VALUES WHAT CIQUAL PROVIDES:
 * - Scientific product names (precise, authoritative)
 * - Comprehensive nutrition data (French scientific database)
 * - Generic food names (raw ingredients, not branded products)
 * - Portion size information
 * - Category precision (scientific classification)
 * - High-quality scientific data from ANSES
 *
 * WHAT CIQUAL DOESN'T PROVIDE (and shouldn't be penalized for):
 * - Brand names (Ciqual is ingredients, not products)
 * - Product images (scientific database)
 * - NutriScore/EcoScore (not part of Ciqual's scope)
 * - Commercial product details
 *
 * SCORING BREAKDOWN (max 100 points):
 * - Name matching: 0-40 pts (base implementation)
 * - Generic name matching: 0-30 pts (Ciqual-specific)
 * - Category matching: 0-20 pts (enhanced - scientific precision)
 * - Nutrition completeness: 0-25 pts (Ciqual's strength!)
 * - Has portion info: 10 pts
 * - Favorite bonus: +15 pts (common)
 *
 * MAXIMUM SCORE: 100 points (before favorite bonus)
 * MAXIMUM WITH FAVORITE: 115 points
 *
 * WHY 100 MAX (vs OpenFoodFacts' 120):
 * - Fewer scorable attributes (no brands, images, scores)
 * - But nutrition data quality is superior
 * - Fair given Ciqual's scientific focus
 * - Prevents unfair penalization for missing commercial data
 *
 * EXAMPLE SCORING:
 * Perfect scientific match:
 * - exact_name: 40
 * - generic_name: 30
 * - category: 20
 * - nutrition_complete: 25
 * - has_portions: 10
 * - favorite: 15
 * = 140 points total
 *
 * Typical good match:
 * - partial_name: 20
 * - category: 20
 * - nutrition_complete: 25
 * - has_portions: 10
 * = 75 points
 *
 * @version 1.0
 * @since Search Diversity Refactor
 */
public class CiqualScorer extends BaseScorer<FoodProduct> {

    // ========== SINGLETON PATTERN ==========

    private static CiqualScorer instance;

    /**
     * Get singleton instance (Android optimization - avoid repeated instantiation)
     */
    public static CiqualScorer getInstance() {
        if (instance == null) {
            instance = new CiqualScorer();
        }
        return instance;
    }

    /**
     * Private constructor for singleton
     */
    private CiqualScorer() {
        // Private constructor
    }

    // ========== INTERFACE IMPLEMENTATION ==========

    @Override
    public DataSource getDataSource() {
        return DataSource.CIQUAL;
    }

    @Override
    public int getMaxScore() {
        return 100; // Lower than OFF, but fair given scope
    }

    @Override
    public int getMinimumScore() {
        return ApiConfig.Scoring.MINIMUM_SCORE; // Standard threshold
    }

    @Override
    public String getScorerName() {
        return "Ciqual Scorer";
    }

    // ========== ENHANCED NAME MATCHING ==========

    /**
     * Override name matching to add generic name matching
     *
     * Ciqual often has both a scientific name and a generic name.
     * The generic name is very important for matching user queries
     * (e.g., "Pomme" as generic name for "Pomme, crue, pulpe et peau")
     */
    @Override
    protected int scoreNameMatch(FoodProduct product, String normalizedQuery, String language, StringBuilder breakdown) {
        // Get base name score (0-40 pts)
        int score = super.scoreNameMatch(product, normalizedQuery, language, breakdown);

        // Add generic name matching (Ciqual specific - 0-30 pts)
        String genericName = product.getGenericName(language);
        if (genericName != null && !genericName.isEmpty()) {
            String normalizedGeneric = SearchFilter.normalizeSearchTerm(genericName);

            // Calculate generic name score
            int genericScore = 0;
            if (normalizedGeneric.equals(normalizedQuery)) {
                genericScore = ApiConfig.Scoring.Ciqual.EXACT_GENERIC_NAME;     // Exact generic match
            } else if (normalizedGeneric.startsWith(normalizedQuery)) {
                genericScore = ApiConfig.Scoring.Ciqual.GENERIC_STARTS_WITH;    // Starts with
            } else if (normalizedGeneric.contains(normalizedQuery)) {
                genericScore = ApiConfig.Scoring.Ciqual.GENERIC_CONTAINS;       // Contains
            }

            if (genericScore > 0) {
                breakdown.append("generic_name:").append(genericScore).append(" ");
                score += genericScore;
            }
        }

        return score;
    }

    // ========== SOURCE-SPECIFIC ATTRIBUTES ==========

    /**
     * Score Ciqual-specific attributes
     *
     * This is where we value what makes Ciqual unique:
     * - Scientific nutrition data quality (Ciqual's main strength)
     * - Portion information
     * - Scientific precision
     *
     * NOTE: We do NOT penalize for missing:
     * - Images (Ciqual doesn't provide these)
     * - NutriScore/EcoScore (not in Ciqual's scope)
     * - Brands (Ciqual is ingredients, not products)
     */
    @Override
    protected int scoreSourceSpecificAttributes(FoodProduct product, String normalizedQuery, String language, StringBuilder breakdown) {
        int score = 0;

        // 1. Nutrition data completeness (0-25 pts) - Ciqual's main strength!
        // Ciqual provides highly complete, scientific nutrition data
        if (product.hasNutritionData()) {
            // Check how complete the nutrition data is
            // Ciqual typically has very complete data
            int nutritionScore = calculateNutritionCompletenessScore(product);
            if (nutritionScore > 0) {
                score += nutritionScore;
                breakdown.append("nutrition_complete:").append(nutritionScore).append(" ");
            }
        }

        // 2. Portion information bonus (10 pts)
        // Ciqual provides standard portion sizes
        if (product.getServingSize() != null) {
            score += ApiConfig.Scoring.Ciqual.HAS_PORTION_INFO;
            breakdown.append("has_portions:").append(ApiConfig.Scoring.Ciqual.HAS_PORTION_INFO).append(" ");
        }

        // 3. Category precision bonus (already handled in base class, but could enhance)
        // Ciqual has very precise scientific categories
        // This is already scored in scoreCategoryMatch(), so no additional points here

        return score;
    }

    // ========== HELPER METHODS ==========

    /**
     * Calculate nutrition completeness score for Ciqual products
     *
     * Ciqual's nutrition data is typically very complete.
     * We reward high-quality nutrition data.
     *
     * SCORING:
     * - Has extensive nutrition data: 25 pts
     * - Has basic nutrition data: 15 pts
     * - Has minimal nutrition data: 5 pts
     *
     * @param product Product to check
     * @return Nutrition completeness score (0-25)
     */
    private int calculateNutritionCompletenessScore(FoodProduct product) {
        if (!product.hasNutritionData()) {
            return 0;
        }

        // Get nutrition object
        var nutrition = product.getNutrition();
        if (nutrition == null) {
            return 0;
        }

        // Count available nutrition fields
        int fieldsAvailable = 0;

        if (nutrition.getEnergyKcal() != null && nutrition.getEnergyKcal() > 0) fieldsAvailable++;
        if (nutrition.getProteins() != null && nutrition.getProteins() > 0) fieldsAvailable++;
        if (nutrition.getCarbohydrates() != null && nutrition.getCarbohydrates() > 0) fieldsAvailable++;
        if (nutrition.getFat() != null && nutrition.getFat() > 0) fieldsAvailable++;
        if (nutrition.getFiber() != null && nutrition.getFiber() > 0) fieldsAvailable++;
        if (nutrition.getSugars() != null && nutrition.getSugars() > 0) fieldsAvailable++;
        if (nutrition.getSalt() != null && nutrition.getSalt() > 0) fieldsAvailable++;
        if (nutrition.getSaturatedFat() != null && nutrition.getSaturatedFat() > 0) fieldsAvailable++;

        // Score based on completeness
        if (fieldsAvailable >= 7) {
            return ApiConfig.Scoring.Ciqual.NUTRITION_COMPLETE_HIGH;    // Very complete (Ciqual's typical quality)
        } else if (fieldsAvailable >= 5) {
            return ApiConfig.Scoring.Ciqual.NUTRITION_COMPLETE_MEDIUM;  // Good completeness
        } else if (fieldsAvailable >= 3) {
            return ApiConfig.Scoring.Ciqual.NUTRITION_COMPLETE_LOW;     // Basic nutrition data
        }

        return 0;
    }
}