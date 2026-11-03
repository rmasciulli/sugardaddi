package li.masciul.sugardaddi.data.database.entities;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.util.ArrayList;
import java.util.List;

import li.masciul.sugardaddi.core.enums.Unit;
import li.masciul.sugardaddi.core.models.FoodPortion;
import li.masciul.sugardaddi.core.models.Meal;
import li.masciul.sugardaddi.core.models.PortionQuantity;

/**
 * MealPortionEntity - one logged entry of a meal (one row per item per meal).
 *
 * WHY A TABLE (and not JSON inside the meal row):
 * - Integrity: "is this item used by a meal?" is a query, so cache eviction and
 *   the Settings clear operations can keep every item a meal still needs
 *   (see USED_PRODUCT_IDS / USED_RECIPE_IDS).
 * - SQL: per-food questions (how much of X this week) become plain joins with
 *   the nutrition table: totalQuantity / 100 x nutrition.<nutrient>.
 * - Edits touch one row, not the whole meal.
 *
 * RULES:
 * - One portion per item per meal: UNIQUE (mealId, itemType, itemId), mirroring
 *   Meal.addPortion(). Inserts use ABORT, so a duplicate fails loudly instead of
 *   silently replacing the other entry.
 * - itemId has no foreign key: it points at food_products OR recipes. What keeps
 *   those rows alive is the eviction queries, not a constraint.
 * - Deleting a meal deletes its portions (cascade). MealRepository also deletes
 *   them explicitly, and eviction sweeps orphans, so correctness never depends
 *   on SQLite's foreign-key enforcement being switched on.
 *
 * Quantity columns are a frozen snapshot of what was logged - see PortionQuantity.
 * Recipe ingredients are NOT stored here (they stay in the recipe's JSON).
 */
@Entity(
        tableName = "meal_portions",
        foreignKeys = @ForeignKey(
                entity = MealEntity.class,
                parentColumns = "id",
                childColumns = "mealId",
                onDelete = ForeignKey.CASCADE
        ),
        indices = {
                @Index(value = {"mealId"}),
                @Index(value = {"itemId"}),
                @Index(value = {"mealId", "itemType", "itemId"}, unique = true)
        }
)
public class MealPortionEntity {

    /**
     * Item ids still used by at least one existing meal, as SQL subqueries for the
     * cache-maintenance queries. The join on meals makes orphaned portions (a meal
     * deleted without its portions) count for nothing.
     */
    public static final String USED_PRODUCT_IDS =
            "SELECT mp.itemId FROM meal_portions mp JOIN meals m ON m.id = mp.mealId"
                    + " WHERE mp.itemType = 'FOOD_PRODUCT'";
    public static final String USED_RECIPE_IDS =
            "SELECT mp.itemId FROM meal_portions mp JOIN meals m ON m.id = mp.mealId"
                    + " WHERE mp.itemType = 'RECIPE'";

    private static final String UNIT_G = "g";
    private static final String UNIT_ML = "ml";

    // ========== IDENTITY ==========
    @PrimaryKey
    @NonNull
    private String id = "";             // FoodPortion.getId() - stable across saves

    @NonNull
    private String mealId = "";

    @NonNull
    private String itemType = "";       // "FOOD_PRODUCT" or "RECIPE"

    @NonNull
    private String itemId = "";         // searchableId of the product or recipe

    private int orderIndex;             // display order within the meal

    // ========== LOGGED QUANTITY (PortionQuantity snapshot) ==========
    private String inputMode;           // "SERVINGS" or "QUANTITY"
    private Double servingCount;        // e.g. 3.5
    private Double servingQuantity;     // size of one serving when logged, e.g. 30
    private Double totalQuantity;       // e.g. 105 - drives nutrition
    private String unit;                // "g" or "ml"

    public MealPortionEntity() {}

    // ========== CONVERSION ==========

    /**
     * Rows for every portion of a meal, in the meal's current order. Portions
     * without a snapshot (only possible if built through the generic ServingSize
     * constructor) get a best-effort one from their serving.
     */
    @NonNull
    public static List<MealPortionEntity> fromMeal(@NonNull Meal meal) {
        List<MealPortionEntity> rows = new ArrayList<>();
        List<FoodPortion> portions = meal.getPortions();
        for (int i = 0; i < portions.size(); i++) {
            FoodPortion portion = portions.get(i);
            if (portion == null || portion.getItemType() == null || portion.getItemId() == null) {
                continue;
            }
            rows.add(fromPortion(portion, meal.getId(), i));
        }
        return rows;
    }

    @NonNull
    public static MealPortionEntity fromPortion(@NonNull FoodPortion portion,
                                                @NonNull String mealId, int orderIndex) {
        PortionQuantity quantity = portion.getQuantity() != null
                ? portion.getQuantity()
                : PortionQuantity.fromServingSize(portion.getServing());

        MealPortionEntity entity = new MealPortionEntity();
        entity.id = portion.getId();
        entity.mealId = mealId;
        entity.itemType = portion.getItemType();
        entity.itemId = portion.getItemId();
        entity.orderIndex = orderIndex;
        entity.inputMode = quantity.getInputMode().name();
        entity.servingCount = quantity.getServingCount();
        entity.servingQuantity = quantity.getServingQuantity();
        entity.totalQuantity = quantity.getTotalQuantity();
        entity.unit = unitToColumn(quantity.getUnit());
        return entity;
    }

    /**
     * Back to an (unresolved) FoodPortion with its snapshot restored exactly as
     * saved. The product or recipe itself is attached later by the repository.
     */
    @NonNull
    public FoodPortion toPortion() {
        FoodPortion portion = new FoodPortion(itemType, itemId, null);
        portion.setId(id);
        portion.setParentType("MEAL");
        portion.setParentId(mealId);
        portion.setOrderIndex(orderIndex);
        portion.setQuantity(PortionQuantity.restore(
                inputModeFromColumn(inputMode), servingCount, servingQuantity,
                totalQuantity, unitFromColumn(unit)));
        return portion;
    }

    @Nullable
    private static String unitToColumn(@Nullable Unit unit) {
        if (unit == Unit.ML) return UNIT_ML;
        if (unit == Unit.G) return UNIT_G;
        return null;
    }

    @Nullable
    private static Unit unitFromColumn(@Nullable String value) {
        if (UNIT_ML.equals(value)) return Unit.ML;
        if (UNIT_G.equals(value)) return Unit.G;
        return null;
    }

    @NonNull
    private static PortionQuantity.InputMode inputModeFromColumn(@Nullable String value) {
        return PortionQuantity.InputMode.SERVINGS.name().equals(value)
                ? PortionQuantity.InputMode.SERVINGS
                : PortionQuantity.InputMode.QUANTITY;
    }

    // ========== GETTERS / SETTERS (Room) ==========

    @NonNull public String getId() { return id; }
    public void setId(@NonNull String id) { this.id = id; }

    @NonNull public String getMealId() { return mealId; }
    public void setMealId(@NonNull String mealId) { this.mealId = mealId; }

    @NonNull public String getItemType() { return itemType; }
    public void setItemType(@NonNull String itemType) { this.itemType = itemType; }

    @NonNull public String getItemId() { return itemId; }
    public void setItemId(@NonNull String itemId) { this.itemId = itemId; }

    public int getOrderIndex() { return orderIndex; }
    public void setOrderIndex(int orderIndex) { this.orderIndex = orderIndex; }

    public String getInputMode() { return inputMode; }
    public void setInputMode(String inputMode) { this.inputMode = inputMode; }

    public Double getServingCount() { return servingCount; }
    public void setServingCount(Double servingCount) { this.servingCount = servingCount; }

    public Double getServingQuantity() { return servingQuantity; }
    public void setServingQuantity(Double servingQuantity) { this.servingQuantity = servingQuantity; }

    public Double getTotalQuantity() { return totalQuantity; }
    public void setTotalQuantity(Double totalQuantity) { this.totalQuantity = totalQuantity; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
}
