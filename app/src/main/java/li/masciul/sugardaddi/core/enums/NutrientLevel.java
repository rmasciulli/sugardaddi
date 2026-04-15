package li.masciul.sugardaddi.core.enums;

import li.masciul.sugardaddi.core.models.Nutrition;

/**
 * NutrientLevel - Quality assessment for nutrient content
 *
 * Maps nutrient amounts (as % DRV) to quality levels with visual indicators.
 * Supports both "low is good" (salt, sugar) and "high is good" (fiber, protein) nutrients.
 *
 * Color scheme matches nutricards banner designs:
 * - EXCELLENT: Dark green (#007F39)
 * - GOOD: Light green (#8DC723)
 * - AVERAGE: Yellow/Gold (#FFC000)
 * - MEDIOCRE: Red (#FE2803)
 * - BAD: Orange (#FF8E00)
 * - NEUTRAL: Dark blue (#0F3759) - No threshold data
 *
 * @version 1.0
 */
public enum NutrientLevel {

    /**
     * Excellent nutritional value
     * - LOW_IS_GOOD: < 5% DRV (very low salt/sugar/sat fat)
     * - HIGH_IS_GOOD: ≥ 15% DRV (high fiber/protein/vitamins)
     */
    EXCELLENT("excellent", "#007F39", 5),

    /**
     * Good nutritional value
     * - LOW_IS_GOOD: 5-10% DRV (low salt/sugar/sat fat)
     * - HIGH_IS_GOOD: 10-15% DRV (good fiber/protein/vitamins)
     */
    GOOD("good", "#8DC723", 4),

    /**
     * Average/acceptable nutritional value
     * - LOW_IS_GOOD: 10-20% DRV (moderate salt/sugar/sat fat)
     * - HIGH_IS_GOOD: 5-10% DRV (moderate fiber/protein/vitamins)
     */
    AVERAGE("average", "#FFC000", 3),

    /**
     * Mediocre/poor nutritional value
     * - LOW_IS_GOOD: 20-40% DRV (high salt/sugar/sat fat)
     * - HIGH_IS_GOOD: 2-5% DRV (low fiber/protein/vitamins)
     */
    MEDIOCRE("mediocre", "#FE2803", 2),

    /**
     * Bad/very poor nutritional value
     * - LOW_IS_GOOD: ≥ 40% DRV (very high salt/sugar/sat fat)
     * - HIGH_IS_GOOD: < 2% DRV (very low fiber/protein/vitamins)
     */
    BAD("bad", "#FF8E00", 1),

    /**
     * Neutral - no assessment possible
     * Used when:
     * - No DRV exists for nutrient
     * - Value is null
     * - Threshold type not defined
     */
    NEUTRAL("neutral", "#0F3759", 0);

    // ========== ENUM FIELDS ==========

    private final String key;
    private final String colorHex;
    private final int score; // For comparisons (5=best, 0=neutral)

    NutrientLevel(String key, String colorHex, int score) {
        this.key = key;
        this.colorHex = colorHex;
        this.score = score;
    }

    // ========== GETTERS ==========

    public String getKey() {
        return key;
    }

    public String getColorHex() {
        return colorHex;
    }

    public int getScore() {
        return score;
    }

    /**
     * Get Android color int
     * @return Color as int for Android
     */
    public int getColorInt() {
        return android.graphics.Color.parseColor(colorHex);
    }

    // ========== THRESHOLD EVALUATION ==========

    /**
     * Threshold type for nutrient evaluation
     */
    public enum ThresholdType {
        /**
         * Low values are good, high values are bad
         * Examples: salt, sugar, saturated fat, sodium, cholesterol
         */
        LOW_IS_GOOD,

        /**
         * High values are good, low values are bad
         * Examples: fiber, protein, vitamins, minerals
         */
        HIGH_IS_GOOD
    }

    /**
     * Evaluate nutrient quality based on % DRV
     *
     * @param percentDRV Percentage of Daily Reference Value (0-100+)
     * @param type Threshold type (LOW_IS_GOOD or HIGH_IS_GOOD)
     * @return NutrientLevel assessment
     */
    public static NutrientLevel evaluate(double percentDRV, ThresholdType type) {
        if (percentDRV < 0) {
            return NEUTRAL;
        }

        if (type == ThresholdType.LOW_IS_GOOD) {
            // For salt, sugar, saturated fat: lower is better
            if (percentDRV < 5.0) return EXCELLENT;   // < 5% = excellent
            if (percentDRV < 10.0) return GOOD;       // 5-10% = good
            if (percentDRV < 20.0) return AVERAGE;    // 10-20% = average
            if (percentDRV < 40.0) return MEDIOCRE;   // 20-40% = mediocre
            return BAD;                                // ≥ 40% = bad

        } else { // HIGH_IS_GOOD
            // For fiber, protein, vitamins: higher is better
            if (percentDRV >= 15.0) return EXCELLENT; // ≥ 15% = excellent
            if (percentDRV >= 10.0) return GOOD;      // 10-15% = good
            if (percentDRV >= 5.0) return AVERAGE;    // 5-10% = average
            if (percentDRV >= 2.0) return MEDIOCRE;   // 2-5% = mediocre
            return BAD;                                // < 2% = bad
        }
    }

    /**
     * Get threshold type for a specific nutrient
     *
     * @param nutrientInfo The nutrient to evaluate
     * @return ThresholdType or null if no threshold defined
     */
    public static ThresholdType getThresholdType(Nutrition.NutrientInfo nutrientInfo) {
        if (nutrientInfo == null) return null;

        switch (nutrientInfo) {
            // LOW_IS_GOOD: Bad if high
            case SALT:
            case SODIUM:
            case SUGARS:
            case SATURATED_FAT:
            case TRANS_FAT:
            case CHOLESTEROL:
                return ThresholdType.LOW_IS_GOOD;

            // HIGH_IS_GOOD: Good if high
            case FIBER:
            case PROTEINS:
            case VITAMIN_A:
            case VITAMIN_D:
            case VITAMIN_E:
            case VITAMIN_K:
            case VITAMIN_B1:
            case VITAMIN_B2:
            case VITAMIN_B3:
            case VITAMIN_B5:
            case VITAMIN_B6:
            case VITAMIN_B7:
            case VITAMIN_B9:
            case VITAMIN_B12:
            case VITAMIN_C:
            case CALCIUM:
            case PHOSPHORUS:
            case MAGNESIUM:
            case POTASSIUM:
            case IRON:
            case ZINC:
            case COPPER:
            case MANGANESE:
            case SELENIUM:
            case IODINE:
            case CHROMIUM:
            case MOLYBDENUM:
            case OMEGA3:
            case DHA:
            case EPA:
                return ThresholdType.HIGH_IS_GOOD;

            // NEUTRAL: No clear threshold
            case ENERGY_KJ:
            case ENERGY_KCAL:
            case FAT:
            case CARBOHYDRATES:
            case MONOUNSATURATED_FAT:
            case POLYUNSATURATED_FAT:
            case OMEGA6:
            case OMEGA9:
            default:
                return null; // No threshold - context-dependent
        }
    }

    /**
     * Evaluate a nutrient automatically based on its type
     *
     * @param nutrientInfo The nutrient to evaluate
     * @param percentDRV Percentage of Daily Reference Value
     * @return NutrientLevel assessment, or NEUTRAL if no threshold defined
     */
    public static NutrientLevel evaluateNutrient(
            Nutrition.NutrientInfo nutrientInfo,
            double percentDRV) {

        ThresholdType type = getThresholdType(nutrientInfo);
        if (type == null) {
            return NEUTRAL;
        }

        return evaluate(percentDRV, type);
    }

    // ========== COMPARISON ==========

    /**
     * Check if this level is better than another
     *
     * @param other Other nutrient level
     * @return true if this level has higher score
     */
    public boolean isBetterThan(NutrientLevel other) {
        if (other == null) return true;
        return this.score > other.score;
    }

    /**
     * Check if this level is worse than another
     *
     * @param other Other nutrient level
     * @return true if this level has lower score
     */
    public boolean isWorseThan(NutrientLevel other) {
        if (other == null) return false;
        return this.score < other.score;
    }

    /**
     * Get relative quality as string
     *
     * @return User-friendly quality description
     */
    public String getQualityDescription() {
        switch (this) {
            case EXCELLENT: return "Excellent";
            case GOOD: return "Good";
            case AVERAGE: return "Average";
            case MEDIOCRE: return "Poor";
            case BAD: return "Very poor";
            case NEUTRAL: return "Not assessed";
            default: return "Unknown";
        }
    }
}