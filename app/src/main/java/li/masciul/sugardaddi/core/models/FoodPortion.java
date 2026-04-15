package li.masciul.sugardaddi.core.models;

import li.masciul.sugardaddi.core.enums.Unit;
import java.util.UUID;

/**
 * FoodPortion - Represents a quantified food item in recipes or meals
 *
 * Unified replacement for both Ingredient and MealComponent.
 * Can reference FoodProduct or Recipe with specific quantities.
 *
 * Database mapping:
 * - parent_type: RECIPE or MEAL
 * - parent_id: recipe_id or meal_id
 * - item_type: FOOD_PRODUCT or RECIPE
 * - item_id: referenced item's ID
 */
public class FoodPortion {

    // ========== IDENTIFICATION ==========
    private String id;
    private String parentType;      // "RECIPE" or "MEAL"
    private String parentId;        // ID of the containing recipe/meal
    private String itemType;        // "FOOD_PRODUCT" or "RECIPE"
    private String itemId;          // ID of the referenced item

    // ========== QUANTITY & SERVING ==========
    private ServingSize serving;    // Contains quantity, unit, and description
    private Double gramsEquivalent; // Cached gram equivalent for quick calculations

    // ========== METADATA ==========
    private int orderIndex;          // Order in recipe/meal
    private String preparationNote;  // "diced", "cooked", "raw", etc.
    private boolean isOptional;      // Optional ingredient
    private boolean isEstimated;     // Was quantity estimated?

    // ========== TRANSIENT FIELDS ==========
    private transient FoodProduct foodProduct;  // Loaded food product
    private transient Recipe recipe;             // Loaded recipe
    private transient Nutrition calculatedNutrition; // Cached nutrition

    // ========== CONSTRUCTORS ==========

    public FoodPortion() {
        this.id = generateId();
        this.serving = new ServingSize();
        this.isOptional = false;
        this.isEstimated = false;
    }

    /**
     * Create portion for a food product
     */
    public FoodPortion(FoodProduct product, double quantity, Unit unit) {
        this();
        this.itemType = "FOOD_PRODUCT";
        this.itemId = product.getSearchableId();
        this.foodProduct = product;
        this.serving = new ServingSize(quantity, unit);
        calculateGramsEquivalent();
    }

    /**
     * Create portion for a recipe
     */
    public FoodPortion(Recipe recipe, double servings) {
        this();
        this.itemType = "RECIPE";
        this.itemId = recipe.getSearchableId();
        this.recipe = recipe;
        this.serving = ServingSize.forRecipe((int) servings);
    }

    /**
     * Create portion with ServingSize
     */
    public FoodPortion(String itemType, String itemId, ServingSize serving) {
        this();
        this.itemType = itemType;
        this.itemId = itemId;
        this.serving = serving;
        calculateGramsEquivalent();
    }

    // ========== NUTRITION CALCULATION ==========

    /**
     * Calculate nutrition for this portion
     */
    public Nutrition calculateNutrition() {
        // Return cached if available
        if (calculatedNutrition != null) {
            return calculatedNutrition;
        }

        Nutrition baseNutrition = null;
        double multiplier = 1.0;

        if ("FOOD_PRODUCT".equals(itemType) && foodProduct != null) {
            if (!foodProduct.hasNutritionData()) {
                return null;
            }

            baseNutrition = foodProduct.getNutrition();

            // Calculate multiplier based on grams
            Double grams = serving.getAsGrams();
            if (grams == null && gramsEquivalent != null) {
                grams = gramsEquivalent;
            }

            if (grams != null) {
                multiplier = grams / 100.0; // Nutrition is per 100g
            }

        } else if ("RECIPE".equals(itemType) && recipe != null) {
            if (!recipe.hasNutritionData()) {
                return null;
            }

            baseNutrition = recipe.getNutrition();

            // Recipe nutrition is per 100g, but we need per serving
            // First get total recipe weight, then calculate our portion
            Double servingCount = serving.getQuantity();
            if (servingCount != null && recipe.getServings() > 0) {
                // Our portion is X servings out of total recipe servings
                multiplier = servingCount / recipe.getServings();
            }
        }

        if (baseNutrition != null && multiplier > 0) {
            calculatedNutrition = baseNutrition.scale(multiplier);
            return calculatedNutrition;
        }

        return null;
    }

    /**
     * Calculate and cache gram equivalent
     */
    private void calculateGramsEquivalent() {
        if (serving != null) {
            this.gramsEquivalent = serving.getAsGrams();
        }
    }

    // ========== DISPLAY METHODS ==========

    /**
     * Get display name for UI
     */
    public String getDisplayName(String language) {
        if ("FOOD_PRODUCT".equals(itemType) && foodProduct != null) {
            return foodProduct.getDisplayName(language);
        } else if ("RECIPE".equals(itemType) && recipe != null) {
            return recipe.getDisplayName(language);
        }
        return itemId != null ? itemId : "Unknown item";
    }

    /**
     * Get full display text with quantity
     */
    public String getDisplayText(String language) {
        StringBuilder display = new StringBuilder();

        // Add serving size
        if (serving != null) {
            display.append(serving.getDisplayText());
            display.append(" ");
        }

        // Add item name
        display.append(getDisplayName(language));

        // Add preparation note if present
        if (preparationNote != null && !preparationNote.trim().isEmpty()) {
            display.append(", ").append(preparationNote);
        }

        // Add optional indicator
        if (isOptional) {
            display.append(" (optional)");
        }

        // Add estimated indicator
        if (isEstimated) {
            display.append(" (estimated)");
        }

        return display.toString();
    }

    /**
     * Get short display text
     */
    public String getShortDisplayText(String language) {
        if (serving != null && serving.getShortDisplayText() != null) {
            return serving.getShortDisplayText() + " " + getDisplayName(language);
        }
        return getDisplayName(language);
    }

    // ========== VALIDATION ==========

    /**
     * Check if portion is valid
     */
    public boolean isValid() {
        return itemType != null &&
                itemId != null && !itemId.trim().isEmpty() &&
                serving != null && serving.isValid();
    }

    /**
     * Check if nutrition can be calculated
     */
    public boolean canCalculateNutrition() {
        if ("FOOD_PRODUCT".equals(itemType)) {
            return foodProduct != null && foodProduct.hasNutritionData();
        } else if ("RECIPE".equals(itemType)) {
            return recipe != null && recipe.hasNutritionData();
        }
        return false;
    }

    // ========== UTILITY METHODS ==========

    private String generateId() {
        return "portion_" + UUID.randomUUID().toString()
                .replace("-", "").substring(0, 12);
    }

    /**
     * Clear cached data (when portion changes)
     */
    public void clearCache() {
        this.calculatedNutrition = null;
        calculateGramsEquivalent();
    }

    /**
     * Create a copy of this portion
     */
    public FoodPortion copy() {
        FoodPortion copy = new FoodPortion();
        copy.parentType = this.parentType;
        copy.parentId = this.parentId;
        copy.itemType = this.itemType;
        copy.itemId = this.itemId;
        copy.serving = this.serving != null ?
                new ServingSize(serving.getQuantity(), serving.getUnit()) : null;
        copy.gramsEquivalent = this.gramsEquivalent;
        copy.orderIndex = this.orderIndex;
        copy.preparationNote = this.preparationNote;
        copy.isOptional = this.isOptional;
        copy.isEstimated = this.isEstimated;
        // Don't copy transient fields or ID
        return copy;
    }

    // ========== GETTERS AND SETTERS ==========

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getParentType() { return parentType; }
    public void setParentType(String parentType) { this.parentType = parentType; }

    public String getParentId() { return parentId; }
    public void setParentId(String parentId) { this.parentId = parentId; }

    public String getItemType() { return itemType; }
    public void setItemType(String itemType) {
        this.itemType = itemType;
        clearCache();
    }

    public String getItemId() { return itemId; }
    public void setItemId(String itemId) { this.itemId = itemId; }

    public ServingSize getServing() { return serving; }
    public void setServing(ServingSize serving) {
        this.serving = serving;
        clearCache();
    }

    public Double getGramsEquivalent() { return gramsEquivalent; }
    public void setGramsEquivalent(Double gramsEquivalent) {
        this.gramsEquivalent = gramsEquivalent;
    }

    public int getOrderIndex() { return orderIndex; }
    public void setOrderIndex(int orderIndex) { this.orderIndex = orderIndex; }

    public String getPreparationNote() { return preparationNote; }
    public void setPreparationNote(String preparationNote) {
        this.preparationNote = preparationNote;
    }

    public boolean isOptional() { return isOptional; }
    public void setOptional(boolean optional) { this.isOptional = optional; }

    public boolean isEstimated() { return isEstimated; }
    public void setEstimated(boolean estimated) { this.isEstimated = estimated; }

    // Transient getters/setters
    public FoodProduct getFoodProduct() { return foodProduct; }
    public void setFoodProduct(FoodProduct foodProduct) {
        this.foodProduct = foodProduct;
        if (foodProduct != null) {
            this.itemType = "FOOD_PRODUCT";
            this.itemId = foodProduct.getSearchableId();
        }
        clearCache();
    }

    public Recipe getRecipe() { return recipe; }
    public void setRecipe(Recipe recipe) {
        this.recipe = recipe;
        if (recipe != null) {
            this.itemType = "RECIPE";
            this.itemId = recipe.getSearchableId();
        }
        clearCache();
    }

    @Override
    public String toString() {
        return String.format("FoodPortion{type=%s, id=%s, serving=%s}",
                itemType, itemId, serving);
    }
}