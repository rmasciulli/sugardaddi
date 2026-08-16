package li.masciul.sugardaddi.core.models;

import li.masciul.sugardaddi.core.enums.Unit;
import li.masciul.sugardaddi.core.interfaces.Nutritional;
import li.masciul.sugardaddi.core.interfaces.Searchable;
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
 *
 * RESOLUTION MODEL: itemType/itemId identify what to look up and which
 * DAO to query (see MealRepository.getMealWithProducts()) - that's the
 * one place where a real Searchable object doesn't exist yet to
 * dispatch against, so a string tag is genuinely needed there.
 * Everywhere else, use getResolvedItem() and instanceof checks against
 * it instead of comparing itemType strings directly - mirrors the
 * dispatch MainActivity.onItemClick() already does for FoodProduct vs
 * Recipe against Searchable. The one deliberate exception is
 * isValid(), which stays itemType/itemId-based - see its own Javadoc
 * for why.
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
     * Create a portion for a recipe, by weight - mirrors the FoodProduct
     * constructor above exactly. Recipe.getNutrition() is per-100g (see
     * FatSecretMapper.mapRecipeServing()), the same basis as
     * FoodProduct.getNutrition(), so the same grams-based scaling in
     * calculateNutrition() applies unchanged.
     */
    public FoodPortion(Recipe recipe, double quantity, Unit unit) {
        this();
        this.itemType = "RECIPE";
        this.itemId = recipe.getSearchableId();
        this.recipe = recipe;
        this.serving = new ServingSize(quantity, unit);
        calculateGramsEquivalent();
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

    // ========== RESOLUTION ==========

    /**
     * Returns whichever concrete item this portion resolved to - the
     * FoodProduct if this is a FOOD_PRODUCT portion, the Recipe if
     * RECIPE - or null if resolution hasn't happened yet (a freshly
     * built portion, or a database lookup that hasn't run or found no
     * match).
     *
     * This is the read side of resolution. itemType still decides
     * which DAO to query during the write/load side (see
     * MealRepository.getMealWithProducts()); nothing about that
     * changes. Everything downstream of resolution should call this
     * instead of comparing itemType strings.
     */
    public Searchable getResolvedItem() {
        if (foodProduct != null) return foodProduct;
        if (recipe != null) return recipe;
        return null;
    }

    // ========== NUTRITION CALCULATION ==========

    /**
     * Calculate nutrition for this portion.
     *
     * Dispatches on the resolved item's Nutritional capability rather
     * than itemType - FoodProduct and Recipe both implement
     * Nutritional, so one branch serves both instead of two
     * near-identical ones keyed on a string.
     */
    public Nutrition calculateNutrition() {
        // Return cached if available
        if (calculatedNutrition != null) {
            return calculatedNutrition;
        }

        Searchable resolved = getResolvedItem();
        if (!(resolved instanceof Nutritional)) {
            return null;
        }

        Nutritional nutritionalItem = (Nutritional) resolved;
        if (!nutritionalItem.hasNutritionData()) {
            return null;
        }

        Nutrition baseNutrition = nutritionalItem.getNutrition();

        // Both FoodProduct.getNutrition() and Recipe.getNutrition() are
        // genuinely per-100g - Recipe confirmed at its source,
        // FatSecretMapper.mapRecipeServing() explicitly normalizes by
        // gramsPerPortion and sets NutritionBasis.PER_100G - so the same
        // grams-based scaling applies regardless of which type resolved.
        // (The RECIPE case used to scale by a fraction of total recipe
        // servings instead, which doesn't compose with a per-100g value
        // at all - fixed in b4d1f2d; preserved here now that both cases
        // share one branch.)
        double multiplier = 1.0;
        Double grams = serving.getAsGrams();
        if (grams == null && gramsEquivalent != null) {
            grams = gramsEquivalent;
        }
        if (grams != null) {
            multiplier = grams / 100.0; // Nutrition is per 100g
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
     * Get display name for UI.
     *
     * Delegates straight to the resolved item's own
     * getDisplayName(language) - Searchable already declares that
     * method, so no instanceof/branching is needed here at all (unlike
     * calculateNutrition(), which needs Nutritional specifically for
     * hasNutritionData()/getNutrition()).
     */
    public String getDisplayName(String language) {
        Searchable resolved = getResolvedItem();
        if (resolved != null) {
            return resolved.getDisplayName(language);
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
     * Check if portion is valid.
     *
     * Deliberately itemType/itemId-based, NOT getResolvedItem()-based.
     * This has to stay true for a portion that's legitimately
     * constructed but not yet resolved - e.g. a TheMealDB ingredient
     * stub (see TheMealDbMapper), built with a real itemType and a
     * name-string itemId, whose foodProduct/recipe stay null until
     * ingredient-mapping resolution lands (open backlog item). If this
     * checked getResolvedItem() instead, every such stub would read as
     * invalid today, which it structurally isn't - it's just not
     * resolved yet.
     */
    public boolean isValid() {
        return itemType != null &&
                itemId != null && !itemId.trim().isEmpty() &&
                serving != null && serving.isValid();
    }

    /**
     * Check if nutrition can be calculated - i.e. the portion has
     * resolved to a real item that itself carries nutrition data.
     * Unlike isValid() above, this is legitimately about resolved
     * state: an unresolved stub correctly returns false here until
     * resolution happens, which is the right behavior for this method
     * specifically.
     */
    public boolean canCalculateNutrition() {
        Searchable resolved = getResolvedItem();
        return resolved instanceof Nutritional
                && ((Nutritional) resolved).hasNutritionData();
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