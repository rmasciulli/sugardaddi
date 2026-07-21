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