package li.masciul.sugardaddi.core.enums;

/**
 * NutritionLabelMode - Display mode for nutrition labels
 *
 * Determines which column layout to use when displaying nutritional information.
 *
 * DETAILED:
 * - 3 columns: Nutrient | per 100g | per serving
 * - Used for food products where showing reference amount (100g) is meaningful
 * - EU regulation compliant
 *
 * SUMMARY:
 * - 2 columns: Nutrient | Total
 * - Used for meals/recipes where "per 100g" is meaningless
 * - Shows aggregate nutrition in simpler format
 *
 * @version 1.0
 */
public enum NutritionLabelMode {
    /**
     * Detailed 3-column layout
     * Shows: Nutrient name | Amount per 100g/ml | Amount per serving
     *
     * Use for:
     * - Food products
     * - Individual ingredients
     * - Items from product databases (OpenFoodFacts, Ciqual, etc.)
     */
    DETAILED,

    /**
     * Summary 2-column layout
     * Shows: Nutrient name | Total amount
     *
     * Use for:
     * - Meals (composed of multiple items)
     * - Recipes
     * - Composite items where "per 100g" doesn't make sense
     */
    SUMMARY
}