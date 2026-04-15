package li.masciul.sugardaddi.core.interfaces;

import li.masciul.sugardaddi.core.models.Nutrition;
import li.masciul.sugardaddi.core.models.ServingSize;

/**
 * Nutritional - Interface for items that contain nutrition information
 *
 * Implemented by FoodProduct, Recipe, and Meal to enable:
 * - Polymorphic nutrition display in UI
 * - Unified nutrition calculations
 * - Consistent serving size handling
 */
public interface Nutritional {

    /**
     * Get nutrition information
     * @return Nutrition data, or null if not available
     */
    Nutrition getNutrition();

    /**
     * Check if nutrition data is available and valid
     * @return true if nutrition data exists and has meaningful values
     */
    boolean hasNutritionData();

    /**
     * Get serving size information
     * @return ServingSize data, or null if not available
     */
    ServingSize getServingSize();

    /**
     * Check if this item is liquid (affects unit calculations)
     * @return true if liquid, false if solid
     */
    boolean isLiquid();

    /**
     * Calculate nutrition for a specific quantity
     * @param quantity Amount to calculate for
     * @param unit Unit of measurement
     * @return Calculated nutrition, or null if not possible
     */
    default Nutrition calculateNutritionForQuantity(double quantity, String unit) {
        Nutrition baseNutrition = getNutrition();
        if (baseNutrition == null || quantity <= 0) {
            return null;
        }

        // Create a copy of the nutrition with scaled values
        Nutrition scaledNutrition = new Nutrition();
        scaledNutrition.setDataSource(baseNutrition.getDataSource() + " (scaled)");

        double multiplier = calculateNutritionMultiplier(quantity, unit);
        if (multiplier <= 0) {
            return null;
        }

        // Scale all nutrients
        scaledNutrition.setEnergyKcal(scaleNutrient(baseNutrition.getEnergyKcal(), multiplier));
        scaledNutrition.setProteins(scaleNutrient(baseNutrition.getProteins(), multiplier));
        scaledNutrition.setCarbohydrates(scaleNutrient(baseNutrition.getCarbohydrates(), multiplier));
        scaledNutrition.setFat(scaleNutrient(baseNutrition.getFat(), multiplier));
        scaledNutrition.setSugars(scaleNutrient(baseNutrition.getSugars(), multiplier));
        scaledNutrition.setFiber(scaleNutrient(baseNutrition.getFiber(), multiplier));
        scaledNutrition.setSalt(scaleNutrient(baseNutrition.getSalt(), multiplier));
        scaledNutrition.setSaturatedFat(scaleNutrient(baseNutrition.getSaturatedFat(), multiplier));

        return scaledNutrition;
    }

    /**
     * Calculate multiplier for nutrition scaling
     * @param quantity Amount
     * @param unit Unit of measurement
     * @return Multiplier to apply to nutrition values
     */
    private double calculateNutritionMultiplier(double quantity, String unit) {
        // Base nutrition is per 100g/100ml
        if ("g".equals(unit) || "ml".equals(unit)) {
            return quantity / 100.0;
        }

        // Handle serving-based units
        if ("serving".equals(unit) || "servings".equals(unit)) {
            ServingSize servingSize = getServingSize();
            if (servingSize != null && servingSize.getQuantity() != null) {
                return (quantity * servingSize.getQuantity()) / 100.0;
            }
        }

        // Handle meal/recipe specific units
        if ("meal".equals(unit)) {
            return quantity; // 1 meal = total nutrition
        }

        return quantity / 100.0; // Default to per-100g basis
    }

    /**
     * Scale a single nutrient value
     */
    private Double scaleNutrient(Double value, double multiplier) {
        return value != null ? value * multiplier : null;
    }

    /**
     * Get nutrition density (nutrition per unit weight)
     * Useful for comparing nutritional efficiency
     */
    default double getNutritionDensity() {
        Nutrition nutrition = getNutrition();
        if (nutrition == null || nutrition.getEnergyKcal() == null) {
            return 0.0;
        }

        double totalMacros = 0.0;
        if (nutrition.getProteins() != null) totalMacros += nutrition.getProteins();
        if (nutrition.getCarbohydrates() != null) totalMacros += nutrition.getCarbohydrates();
        if (nutrition.getFat() != null) totalMacros += nutrition.getFat();

        return totalMacros > 0 ? nutrition.getEnergyKcal() / totalMacros : 0.0;
    }

    /**
     * Check if this item is high in a specific nutrient
     */
    default boolean isHighIn(NutrientType nutrientType) {
        Nutrition nutrition = getNutrition();
        if (nutrition == null) return false;

        // Thresholds based on EU regulations (per 100g)
        switch (nutrientType) {
            case PROTEIN:
                return nutrition.getProteins() != null && nutrition.getProteins() > 12.0;
            case FIBER:
                return nutrition.getFiber() != null && nutrition.getFiber() > 6.0;
            case FAT:
                return nutrition.getFat() != null && nutrition.getFat() > 17.5;
            case SATURATED_FAT:
                return nutrition.getSaturatedFat() != null && nutrition.getSaturatedFat() > 5.0;
            case SUGARS:
                return nutrition.getSugars() != null && nutrition.getSugars() > 22.5;
            case SALT:
                return nutrition.getSalt() != null && nutrition.getSalt() > 1.5;
            default:
                return false;
        }
    }

    /**
     * Nutrient types for analysis
     */
    enum NutrientType {
        PROTEIN, FIBER, FAT, SATURATED_FAT, SUGARS, SALT, CARBOHYDRATES
    }
}