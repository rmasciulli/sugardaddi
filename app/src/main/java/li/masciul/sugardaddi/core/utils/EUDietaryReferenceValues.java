package li.masciul.sugardaddi.core.utils;

/**
 * EUDietaryReferenceValues - EU Regulation (EU) No 1169/2011
 *
 * Dietary Reference Values (DRVs) for nutrition labeling in the European Union.
 * Based on Annex XIII of the EU Food Information Regulation.
 *
 * CRITICAL NOTES:
 * - All values are for ADULTS (average 2000 kcal diet)
 * - Energy reference: 8400 kJ / 2000 kcal
 * - Values are per DAY, not per 100g
 * - Used for calculating % DRV on nutrition labels
 *
 * Reference: https://eur-lex.europa.eu/legal-content/EN/TXT/?uri=CELEX:32011R1169
 *
 * @version 1.0 - EU Standards
 */
public class EUDietaryReferenceValues {

    // ========== ENERGY ==========

    /**
     * Reference daily energy intake (kJ)
     * EU Standard: 8400 kJ
     */
    public static final double ENERGY_KJ = 8400.0;

    /**
     * Reference daily energy intake (kcal)
     * EU Standard: 2000 kcal
     */
    public static final double ENERGY_KCAL = 2000.0;

    // ========== MACRONUTRIENTS ==========

    /**
     * Reference daily fat intake
     * EU Standard: 70g
     */
    public static final double FAT = 70.0; // grams

    /**
     * Reference daily saturated fat intake
     * EU Standard: 20g
     */
    public static final double SATURATED_FAT = 20.0; // grams

    /**
     * Reference daily carbohydrate intake
     * EU Standard: 260g
     */
    public static final double CARBOHYDRATES = 260.0; // grams

    /**
     * Reference daily sugars intake
     * EU Standard: 90g
     */
    public static final double SUGARS = 90.0; // grams

    /**
     * Reference daily protein intake
     * EU Standard: 50g
     */
    public static final double PROTEINS = 50.0; // grams

    /**
     * Reference daily fiber intake
     * EU Standard: 25g (EFSA recommendation, not in Reg 1169/2011)
     */
    public static final double FIBER = 25.0; // grams

    /**
     * Reference daily salt intake
     * EU Standard: 6g
     */
    public static final double SALT = 6.0; // grams

    /**
     * Reference daily sodium intake
     * Calculated from salt: 6g salt = 2.4g sodium
     */
    public static final double SODIUM = 2.4; // grams (2400 mg)

    // ========== VITAMINS (Fat-Soluble) ==========

    /**
     * Vitamin A (µg)
     * EU Standard: 800 µg RE (Retinol Equivalents)
     */
    public static final double VITAMIN_A = 800.0; // µg

    /**
     * Vitamin D (µg)
     * EU Standard: 5 µg
     */
    public static final double VITAMIN_D = 5.0; // µg

    /**
     * Vitamin E (mg)
     * EU Standard: 12 mg α-tocopherol
     */
    public static final double VITAMIN_E = 12.0; // mg

    /**
     * Vitamin K (µg)
     * EU Standard: 75 µg
     */
    public static final double VITAMIN_K = 75.0; // µg

    // ========== VITAMINS (Water-Soluble - B Complex) ==========

    /**
     * Vitamin B1 - Thiamine (mg)
     * EU Standard: 1.1 mg
     */
    public static final double VITAMIN_B1 = 1.1; // mg

    /**
     * Vitamin B2 - Riboflavin (mg)
     * EU Standard: 1.4 mg
     */
    public static final double VITAMIN_B2 = 1.4; // mg

    /**
     * Vitamin B3 - Niacin (mg)
     * EU Standard: 16 mg NE (Niacin Equivalents)
     */
    public static final double VITAMIN_B3 = 16.0; // mg

    /**
     * Vitamin B5 - Pantothenic Acid (mg)
     * EU Standard: 6 mg
     */
    public static final double VITAMIN_B5 = 6.0; // mg

    /**
     * Vitamin B6 - Pyridoxine (mg)
     * EU Standard: 1.4 mg
     */
    public static final double VITAMIN_B6 = 1.4; // mg

    /**
     * Vitamin B7 - Biotin (µg)
     * EU Standard: 50 µg
     */
    public static final double VITAMIN_B7 = 50.0; // µg

    /**
     * Vitamin B9 - Folate (µg)
     * EU Standard: 200 µg DFE (Dietary Folate Equivalents)
     */
    public static final double VITAMIN_B9 = 200.0; // µg

    /**
     * Vitamin B12 - Cobalamin (µg)
     * EU Standard: 2.5 µg
     */
    public static final double VITAMIN_B12 = 2.5; // µg

    /**
     * Vitamin C - Ascorbic Acid (mg)
     * EU Standard: 80 mg
     */
    public static final double VITAMIN_C = 80.0; // mg

    // ========== MINERALS (Major) ==========

    /**
     * Calcium (mg)
     * EU Standard: 800 mg
     */
    public static final double CALCIUM = 800.0; // mg

    /**
     * Phosphorus (mg)
     * EU Standard: 700 mg
     */
    public static final double PHOSPHORUS = 700.0; // mg

    /**
     * Magnesium (mg)
     * EU Standard: 375 mg
     */
    public static final double MAGNESIUM = 375.0; // mg

    /**
     * Potassium (mg)
     * EU Standard: 2000 mg
     */
    public static final double POTASSIUM = 2000.0; // mg

    /**
     * Chloride (mg)
     * EU Standard: 800 mg
     */
    public static final double CHLORIDE = 800.0; // mg

    // ========== MINERALS (Trace) ==========

    /**
     * Iron (mg)
     * EU Standard: 14 mg
     */
    public static final double IRON = 14.0; // mg

    /**
     * Zinc (mg)
     * EU Standard: 10 mg
     */
    public static final double ZINC = 10.0; // mg

    /**
     * Copper (mg)
     * EU Standard: 1 mg
     */
    public static final double COPPER = 1.0; // mg

    /**
     * Manganese (mg)
     * EU Standard: 2 mg
     */
    public static final double MANGANESE = 2.0; // mg

    /**
     * Selenium (µg)
     * EU Standard: 55 µg
     */
    public static final double SELENIUM = 55.0; // µg

    /**
     * Iodine (µg)
     * EU Standard: 150 µg
     */
    public static final double IODINE = 150.0; // µg

    /**
     * Chromium (µg)
     * EU Standard: 40 µg
     */
    public static final double CHROMIUM = 40.0; // µg

    /**
     * Molybdenum (µg)
     * EU Standard: 50 µg
     */
    public static final double MOLYBDENUM = 50.0; // µg

    /**
     * Fluoride (mg)
     * EU Standard: 3.5 mg
     */
    public static final double FLUORIDE = 3.5; // mg

    // ========== UTILITY METHODS ==========

    /**
     * Calculate % of DRV for a given nutrient amount
     *
     * @param amount Amount in appropriate unit (g, mg, µg)
     * @param drv Daily Reference Value
     * @return Percentage of DRV (0-100+)
     */
    public static double calculatePercentDRV(double amount, double drv) {
        if (drv <= 0) return 0;
        return (amount / drv) * 100.0;
    }

    /**
     * Get DRV for a specific nutrient
     * Maps Nutrition.NutrientInfo to EU DRV values
     *
     * @param nutrientInfo The nutrient information
     * @return DRV value in appropriate unit, or null if no EU DRV exists
     */
    public static Double getDRV(li.masciul.sugardaddi.core.models.Nutrition.NutrientInfo nutrientInfo) {
        if (nutrientInfo == null) return null;

        switch (nutrientInfo) {
            // Energy
            case ENERGY_KJ: return ENERGY_KJ;
            case ENERGY_KCAL: return ENERGY_KCAL;

            // Macronutrients
            case FAT: return FAT;
            case SATURATED_FAT: return SATURATED_FAT;
            case CARBOHYDRATES: return CARBOHYDRATES;
            case SUGARS: return SUGARS;
            case PROTEINS: return PROTEINS;
            case FIBER: return FIBER;
            case SALT: return SALT;
            case SODIUM: return SODIUM * 1000; // Convert g to mg

            // Fat-soluble vitamins
            case VITAMIN_A: return VITAMIN_A;
            case VITAMIN_D: return VITAMIN_D;
            case VITAMIN_E: return VITAMIN_E;
            case VITAMIN_K: return VITAMIN_K;

            // Water-soluble vitamins
            case VITAMIN_B1: return VITAMIN_B1;
            case VITAMIN_B2: return VITAMIN_B2;
            case VITAMIN_B3: return VITAMIN_B3;
            case VITAMIN_B5: return VITAMIN_B5;
            case VITAMIN_B6: return VITAMIN_B6;
            case VITAMIN_B7: return VITAMIN_B7;
            case VITAMIN_B9: return VITAMIN_B9;
            case VITAMIN_B12: return VITAMIN_B12;
            case VITAMIN_C: return VITAMIN_C;

            // Major minerals
            case CALCIUM: return CALCIUM;
            case PHOSPHORUS: return PHOSPHORUS;
            case MAGNESIUM: return MAGNESIUM;
            case POTASSIUM: return POTASSIUM;
            case CHLORIDE: return CHLORIDE;

            // Trace minerals
            case IRON: return IRON;
            case ZINC: return ZINC;
            case COPPER: return COPPER;
            case MANGANESE: return MANGANESE;
            case SELENIUM: return SELENIUM;
            case IODINE: return IODINE;
            case CHROMIUM: return CHROMIUM;
            case MOLYBDENUM: return MOLYBDENUM;
            case FLUORIDE: return FLUORIDE * 1000; // Convert mg to µg

            default:
                return null; // No EU DRV defined for this nutrient
        }
    }

    /**
     * Check if a nutrient has an EU DRV
     *
     * @param nutrientInfo The nutrient information
     * @return true if EU DRV exists
     */
    public static boolean hasDRV(li.masciul.sugardaddi.core.models.Nutrition.NutrientInfo nutrientInfo) {
        return getDRV(nutrientInfo) != null;
    }

    // ========== PRIVATE CONSTRUCTOR ==========

    /**
     * Private constructor - utility class, don't instantiate
     */
    private EUDietaryReferenceValues() {
        throw new AssertionError("Cannot instantiate EUDietaryReferenceValues");
    }
}
