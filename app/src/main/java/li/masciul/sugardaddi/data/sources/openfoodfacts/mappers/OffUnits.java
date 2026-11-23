package li.masciul.sugardaddi.data.sources.openfoodfacts.mappers;

/**
 * OffUnits - Open Food Facts nutrient values into the app's units.
 *
 * OFF's "{nutrient}_100g" fields are normalized: per 100 g (or 100 ml), in GRAMS
 * for every nutrient measured by weight, vitamins and minerals included (OFF
 * product nutrition schema). Sodium 400 mg is sodium_100g = 0.4; vitamin D
 * 2.5 µg is vitamin-d_100g = 0.0000025. The unit typed by the contributor is in
 * "{nutrient}_unit", and the schema says not to rely on it.
 *
 * The app stores each nutrient in the unit of its NutrientInfo row (g, mg or µg),
 * so a value from OFF is multiplied by 1, 1000 or 1 000 000.
 *
 * Exceptions handled elsewhere: energy (kJ / kcal, not weights) and alcohol
 * (% vol, see OffAlcohol).
 *
 * Pure Java on purpose (no Android types) so it can be unit tested.
 */
public final class OffUnits {

    private OffUnits() { /* no instances */ }

    /**
     * Converts a per-100 value in grams to the given unit.
     *
     * @param grams    OFF value in grams (a "_100g" field), may be null
     * @param appUnit  unit of the app's field: "g", "mg" or "µg" (also "μg", "ug")
     * @return the value in appUnit, or null when grams is null; a unit this
     *         method does not know returns the value unchanged
     */
    public static Double fromGrams(Double grams, String appUnit) {
        if (grams == null || appUnit == null) return grams;
        switch (appUnit) {
            case "mg":
                return grams * 1_000;
            case "µg":   // µg, micro sign (the one NutrientInfo uses)
            case "μg":   // μg, Greek mu
            case "ug":
                return grams * 1_000_000;
            default:
                return grams;
        }
    }
}
