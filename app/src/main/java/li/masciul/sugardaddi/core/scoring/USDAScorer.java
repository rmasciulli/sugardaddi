package li.masciul.sugardaddi.core.scoring;

import li.masciul.sugardaddi.core.enums.DataSource;
import li.masciul.sugardaddi.core.models.FoodProduct;
import li.masciul.sugardaddi.data.network.ApiConfig;

/**
 * USDAScorer — Scoring strategy for USDA FoodData Central products.
 *
 * VALUES WHAT USDA PROVIDES:
 * - Scientific food names (precise USDA nomenclature, e.g. "Broccoli, raw")
 * - Exhaustive nutrient profiles (Foundation Foods: 100+ nutrients measured)
 * - Food category from USDA taxonomy
 * - Data type quality (Foundation > SR Legacy > Survey)
 *
 * WHAT USDA DOESN'T PROVIDE (and shouldn't be penalized for):
 * - Brand names (USDA covers generic/raw foods, not branded products)
 * - Product images
 * - NutriScore / EcoScore / NOVA group (US-government data, no EU scores)
 * - Serving size (Foundation Foods have portions, SR Legacy rarely)
 *
 * SCORING BREAKDOWN (max 100 points):
 * - Name matching:        0–40 pts (base — exact/starts-with/contains)
 * - Category matching:   0–20 pts (base)
 * - Nutrition quality:   0–25 pts (USDA-specific — more nutrients = higher)
 * - Data type bonus:     0–10 pts (Foundation=10, SR Legacy=7, Survey=3)
 * - Favorite bonus:      +15 pts (common across all sources)
 *
 * MAXIMUM SCORE: 95 points (before favorite)
 * MAXIMUM WITH FAVORITE: 110 points
 *
 * WHY 95 MAX (vs Ciqual's 100, OFF's 120):
 * USDA scores similarly to Ciqual — both are scientific databases covering
 * generic foods with excellent nutrient data. USDA has slightly fewer bonus
 * factors (no generic name field like Ciqual) but the data type bonus makes
 * up for it for Foundation Foods.
 */
public class USDAScorer extends BaseScorer<FoodProduct> {

    // ===== SINGLETON =====

    private static USDAScorer instance;

    public static USDAScorer getInstance() {
        if (instance == null) instance = new USDAScorer();
        return instance;
    }

    private USDAScorer() {}

    // ===== INTERFACE IMPLEMENTATION =====

    @Override
    public DataSource getDataSource() {
        return DataSource.USDA;
    }

    @Override
    public int getMaxScore() {
        return 95;
    }

    @Override
    public int getMinimumScore() {
        return ApiConfig.Scoring.MINIMUM_SCORE;
    }

    @Override
    public String getScorerName() {
        return "USDA Scorer";
    }

    // ===== SOURCE-SPECIFIC ATTRIBUTES =====

    /**
     * Score USDA-specific attributes.
     *
     * Two dimensions unique to USDA:
     * 1. Nutrition completeness — Foundation Foods have exhaustive profiles.
     *    More nutrients filled in = higher score (mirrors Ciqual's approach).
     * 2. Data type bonus — Foundation > SR Legacy > Survey in scientific rigour.
     *    Helps surface the highest-quality records when multiple match.
     */
    @Override
    protected int scoreSourceSpecificAttributes(
            FoodProduct product,
            String normalizedQuery,
            String language,
            StringBuilder breakdown) {

        int score = 0;

        // ── 1. Nutrition data quality (0–25 pts) ──────────────────────────────
        if (product.hasNutritionData()) {
            int nutritionScore = calculateNutritionCompletenessScore(product);
            if (nutritionScore > 0) {
                score += nutritionScore;
                breakdown.append("nutrition_complete:").append(nutritionScore).append(" ");
            }
        }

        // ── 2. Data type bonus (0–10 pts) ─────────────────────────────────────
        // dataCompleteness was set by USDAMapper.dataTypeCompleteness():
        //   Foundation  → 0.95f
        //   SR Legacy   → 0.85f
        //   Survey      → 0.70f
        float completeness = product.getDataCompleteness();
        int dataTypeBonus;
        if (completeness >= 0.90f) {
            dataTypeBonus = 10; // Foundation Foods
        } else if (completeness >= 0.80f) {
            dataTypeBonus = 7;  // SR Legacy
        } else if (completeness >= 0.65f) {
            dataTypeBonus = 3;  // Survey (FNDDS)
        } else {
            dataTypeBonus = 0;
        }

        if (dataTypeBonus > 0) {
            score += dataTypeBonus;
            breakdown.append("data_type:").append(dataTypeBonus).append(" ");
        }

        return score;
    }

    // ===== PRIVATE HELPERS =====

    /**
     * Score nutrition completeness based on how many key nutrients are filled.
     * Mirrors CiqualScorer's approach — USDA and Ciqual both shine in nutrient depth.
     *
     * Checks 5 key nutrients (energy, protein, fat, carbs, fiber):
     *   5 present → 25 pts
     *   4 present → 20 pts
     *   3 present → 15 pts
     *   2 present →  8 pts
     *   1 present →  0 pts (too sparse to be meaningful)
     */
    private int calculateNutritionCompletenessScore(FoodProduct product) {
        if (product.getNutrition() == null) return 0;

        int filled = 0;
        if (product.getNutrition().getEnergyKcal() != null) filled++;
        if (product.getNutrition().getProteins()    != null) filled++;
        if (product.getNutrition().getFat()         != null) filled++;
        if (product.getNutrition().getCarbohydrates()!= null) filled++;
        if (product.getNutrition().getFiber()       != null) filled++;

        switch (filled) {
            case 5: return ApiConfig.Scoring.Ciqual.NUTRITION_COMPLETE_HIGH;
            case 4: return ApiConfig.Scoring.Ciqual.NUTRITION_COMPLETE_MEDIUM;
            case 3: return ApiConfig.Scoring.Ciqual.NUTRITION_COMPLETE_LOW;
            case 2: return ApiConfig.Scoring.Ciqual.NUTRITION_COMPLETE_LOW / 2;
            default: return 0;
        }
    }
}