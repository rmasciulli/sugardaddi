package li.masciul.sugardaddi.core.models;

import li.masciul.sugardaddi.core.enums.NutritionBasis;
import li.masciul.sugardaddi.core.enums.Unit;
import li.masciul.sugardaddi.core.interfaces.Nutritional;
import li.masciul.sugardaddi.core.interfaces.Searchable;
import li.masciul.sugardaddi.core.utils.DensityConversion;
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

    /**
     * What was logged, frozen at logging time - set for meal entries, null for
     * recipe ingredients (which keep their free-text ServingSize). When set it is
     * the source of truth: serving is derived from it for display, and its total
     * drives nutrition. See PortionQuantity.
     */
    private PortionQuantity quantity;

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
        applyQuantity(snapshotFor(product, quantity, unit));
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
        applyQuantity(snapshotFor(recipe, quantity, unit));
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

        // FoodProduct and Recipe nutrition are both per 100 basis units
        // (Recipe confirmed at its source: FatSecretMapper.mapRecipeServing()
        // normalizes by gramsPerPortion), so one scaling serves both. (The
        // RECIPE case used to scale by a fraction of total recipe servings
        // instead, which doesn't compose with a per-100 value at all - fixed
        // in b4d1f2d; preserved here now that both cases share one branch.)
        //
        // An unknown amount means NO data. This used to fall through with a
        // multiplier of 1.0 - silently counting the portion as 100 g - which
        // any "2 servings" of an item without a serving weight would have hit.
        Double amount = getAmountInBasisUnits();
        if (baseNutrition == null || amount == null || amount <= 0) {
            return null;
        }

        calculatedNutrition = baseNutrition.scale(amount / 100.0);
        return calculatedNutrition;
    }

    /**
     * This portion's amount in the item's nutrition basis units - grams for a
     * per-100g item, ml for a per-100ml one - or null when it can't be known.
     *
     * - A logged entry (snapshot): its frozen total, in its own unit. When
     *   that unit is not the item's basis ("250 ml" of milk stored per 100 g),
     *   it is converted through the item's CURRENT density, like per-100
     *   nutrition, which is not frozen either. No density: no amount, never a
     *   1:1 reading (decision, unit preference).
     * - Weight or volume units (G, ML, KG...) without a snapshot: the
     *   serving's own conversion, where Unit.ML counts 1:1 (recipe
     *   ingredients; unchanged).
     * - SERVING / PORTION: quantity x the item's own serving weight, read
     *   from the resolved item at calculation time - "2 servings" of a bar
     *   whose serving is 40 g is 80 g, and stays right if the source later
     *   corrects its serving size. No serving weight (TheMealDB,
     *   TheCocktailDB, most Ciqual foods) means no amount.
     * - Any other unit without a weight (PIECE, OTHER...): no amount.
     */
    public Double getAmountInBasisUnits() {
        // A logged entry is a record: its frozen total is the amount, never
        // recomputed from the item's current serving size. Null total = the
        // entry has no known amount (servings of an item without a serving size).
        if (quantity != null) {
            Double total = quantity.getTotalQuantity();
            return total != null ? inBasisUnits(total, quantity.getUnit()) : null;
        }

        // Below: portions without a snapshot (recipe ingredients, and any entry
        // built through the generic ServingSize constructor).
        if (serving == null) return null;

        // An explicit gram equivalent or a weight/volume unit always wins.
        Double direct = serving.getAsGrams();
        if (direct != null) return direct;

        Unit unit = serving.getUnit();
        Double quantity = serving.getQuantity();
        if ((unit == Unit.SERVING || unit == Unit.PORTION) && quantity != null) {
            Double servingWeight = resolvedServingWeight();
            return servingWeight != null ? quantity * servingWeight : null;
        }

        // Persisted with the portion's JSON; only ever differs from the
        // serving's own conversion if someone set it explicitly.
        return gramsEquivalent;
    }

    /**
     * Weight of ONE serving of the resolved item, in its basis units, or null.
     * A serving in the other unit is converted through the item's density
     * (DensityConversion.servingIn()).
     */
    private Double resolvedServingWeight() {
        Searchable resolved = getResolvedItem();
        if (!(resolved instanceof Nutritional)) return null;
        Nutritional item = (Nutritional) resolved;
        return DensityConversion.servingIn(item.getServingSize(), basisUnitOf(item),
                densityOf(item));
    }

    /**
     * A logged total in the resolved item's basis units. Same unit as the
     * basis: unchanged. The other one (g / ml): through the item's density,
     * null without one. A total with no g / ml unit, or an item without
     * nutrition, is taken as is (nothing to convert against).
     */
    private Double inBasisUnits(double total, Unit unit) {
        Searchable resolved = getResolvedItem();
        if (!(resolved instanceof Nutritional)) return total;
        Nutritional item = (Nutritional) resolved;
        if (!item.hasNutritionData()) return total;
        Unit kind = DensityConversion.kindOf(unit);
        if (kind == null) return total;
        return DensityConversion.convert(total, kind, basisUnitOf(item), densityOf(item));
    }

    /** ML for a per-100 ml item, G otherwise (including items without nutrition). */
    private static Unit basisUnitOf(Nutritional item) {
        Nutrition nutrition = item != null ? item.getNutrition() : null;
        return nutrition != null && nutrition.getBasis() == NutritionBasis.PER_100ML
                ? Unit.ML : Unit.G;
    }

    /** Grams per ml of the item, or null. */
    private static Double densityOf(Nutritional item) {
        Nutrition nutrition = item != null ? item.getNutrition() : null;
        return nutrition != null ? nutrition.getDensity() : null;
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
        if (this.quantity != null) {
            // Immutable: safe to share; the display serving is rebuilt from it.
            copy.quantity = this.quantity;
            copy.serving = this.quantity.toServingSize();
        } else {
            copy.serving = this.serving != null ?
                    new ServingSize(serving.getQuantity(), serving.getUnit()) : null;
        }
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

    /**
     * Set a free-form serving. Drops any logged snapshot: from here on the
     * serving is the only description of the amount, so the two can't disagree.
     */
    public void setServing(ServingSize serving) {
        this.serving = serving;
        this.quantity = null;
        clearCache();
    }

    /** The logged snapshot, or null for recipe ingredients. */
    public PortionQuantity getQuantity() { return quantity; }

    /**
     * Replace what was logged (e.g. the user changed 2 servings to 120 g).
     * Rebuilds the display serving from it and resets the cached nutrition. When
     * the portion belongs to a meal, go through Meal.updatePortionQuantity() so
     * the meal's total is recalculated too.
     *
     * @throws IllegalArgumentException if quantity is null - build it with
     *         PortionQuantity.ofServings()/ofQuantity(), which validate the figures.
     */
    public void setQuantity(PortionQuantity quantity) {
        if (quantity == null) {
            throw new IllegalArgumentException("Quantity is required");
        }
        applyQuantity(quantity);
    }

    /** Snapshot becomes the source of truth; serving is derived from it for display. */
    private void applyQuantity(PortionQuantity quantity) {
        this.quantity = quantity;
        this.serving = quantity.toServingSize();
        clearCache();
    }

    /**
     * Snapshot for a meal entry built from an item and an amount. Freezes the
     * item's typical serving size as it is right now.
     *
     * Until the quantity input lands (roadmap change 6), the add-to-meal screens
     * pass Unit.G meaning "the item's basis unit": their amount field and the
     * label already work in g for per-100g items and ml for per-100ml ones. So
     * the stored unit follows the item's nutrition basis rather than the unit
     * passed. An item without nutrition has no basis: the unit of its own serving
     * size is used, else the passed unit when it is g or ml, else grams.
     */
    private static PortionQuantity snapshotFor(Nutritional item, double amount, Unit unit) {
        ServingSize itemServing = item != null ? item.getServingSize() : null;

        Unit entryUnit;
        if (item != null && item.hasNutritionData()) {
            entryUnit = item.getNutrition().getBasis() == NutritionBasis.PER_100ML ? Unit.ML : Unit.G;
        } else if (itemServing != null && itemServing.getUnit() == Unit.ML) {
            entryUnit = Unit.ML;
        } else if (unit == Unit.ML) {
            entryUnit = Unit.ML;
        } else {
            entryUnit = Unit.G;
        }

        // The typical serving frozen with the entry, in the entry's unit (through
        // the item's density when the serving is in the other unit).
        Double servingQuantity = DensityConversion.servingIn(itemServing, entryUnit,
                densityOf(item));

        if (unit == Unit.SERVING || unit == Unit.PORTION) {
            return PortionQuantity.ofServings(amount, servingQuantity, entryUnit);
        }
        Double total = new ServingSize(amount, unit).getAsGrams();
        if (total == null || total <= 0) {
            // A unit with no weight (PIECE, OTHER...): keep what we were given.
            return PortionQuantity.fromServingSize(new ServingSize(amount, unit));
        }
        return PortionQuantity.ofQuantity(total, entryUnit, servingQuantity);
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