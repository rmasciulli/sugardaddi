package li.masciul.sugardaddi.core.enums;

/**
 * ProductType - Defines the different types of nutritional items
 *
 * Used throughout the system for:
 * - Polymorphic handling of different item types
 * - UI filtering and display
 * - Search categorization
 * - Database organization
 */
public enum ProductType {

    FOOD("food", "Food Product", "🍎", "Individual food items and products"),
    RECIPE("recipe", "Recipe", "👨‍🍳", "User-created recipes with ingredients"),
    MEAL("meal", "Meal", "🍽️", "Planned or consumed meals"),
    MEAL_PLAN("meal_plan", "Meal Plan", "📅", "Planned meals for multiple days"),
    INGREDIENT("ingredient", "Ingredient", "🥕", "Individual recipe ingredients");

    private final String id;
    private final String displayName;
    private final String emoji;
    private final String description;

    ProductType(String id, String displayName, String emoji, String description) {
        this.id = id;
        this.displayName = displayName;
        this.emoji = emoji;
        this.description = description;
    }

    // ========== GETTERS ==========

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getEmoji() { return emoji; }
    public String getDescription() { return description; }

    // ========== UTILITY METHODS ==========

    /**
     * Get ProductType from string ID
     */
    public static ProductType fromId(String id) {
        if (id == null) return null;

        for (ProductType type : values()) {
            if (type.id.equalsIgnoreCase(id)) {
                return type;
            }
        }
        return null;
    }

    /**
     * Get ProductType from display name
     */
    public static ProductType fromDisplayName(String displayName) {
        if (displayName == null) return null;

        for (ProductType type : values()) {
            if (type.displayName.equalsIgnoreCase(displayName)) {
                return type;
            }
        }
        return null;
    }

    /**
     * Get display text with emoji
     */
    public String getDisplayWithEmoji() {
        return emoji + " " + displayName;
    }

    /**
     * Check if this type can contain other items
     */
    public boolean isContainer() {
        return this == RECIPE || this == MEAL || this == MEAL_PLAN;
    }

    /**
     * Check if this type can be used as an ingredient
     */
    public boolean canBeIngredient() {
        return this == FOOD || this == RECIPE;
    }

    /**
     * Check if this type can be added to meals
     */
    public boolean canBeInMeal() {
        return this == FOOD || this == RECIPE;
    }

    /**
     * Get plural form
     */
    public String getPlural() {
        switch (this) {
            case FOOD: return "Foods";
            case RECIPE: return "Recipes";
            case MEAL: return "Meals";
            case MEAL_PLAN: return "Meal Plans";
            case INGREDIENT: return "Ingredients";
            default: return displayName + "s";
        }
    }

    @Override
    public String toString() {
        return displayName;
    }
}