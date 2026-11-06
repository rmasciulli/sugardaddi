package li.masciul.sugardaddi.data.database.entities;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.Index;

import java.util.List;

import li.masciul.sugardaddi.core.enums.Unit;

/**
 * UserItemDataEntity - everything the USER set on a product or recipe (one row
 * per item that has any).
 *
 * WHY A SEPARATE TABLE (decision, change 5a):
 * Cached rows (food_products, recipes) hold SOURCE data only and are rewritten
 * by every refresh. User data stored on them needed hand-written copy-over code
 * on each refresh path, and died with the row when the cache evicted it (custom
 * photos were lost that way). Here, refreshes never touch it, and an item with a
 * row here is KEPT by every cache operation, exactly like an item used by a meal.
 *
 * WHAT IT HOLDS:
 * - isFavorite                     the favorite pin
 * - servingQuantity / servingUnit  the preferred typical serving (change 5b)
 * - preferredUnit                  "g" or "ml": the unit the item is shown and
 *                                  logged in, when the user chose one (DB v22)
 * - imagePath / thumbnailPath      custom photos set with ImagePickerHelper
 *
 * RULES:
 * - One row per item: primary key (itemType, itemId). itemType is needed
 *   because a product and a recipe can share an id (FatSecret numbers both).
 * - A row exists only while it holds something: the DAO deletes rows left with
 *   no favorite, no serving, no unit and no photo (deleteEmpty()), so "has a row" means
 *   "the user set something" and can drive retention directly.
 * - itemId has no foreign key (it points at food_products OR recipes). Rows of
 *   items that no longer exist are swept by cache eviction (deleteOrphans()).
 *
 * Read together with the item through @Relation in FoodProductWithNutrition and
 * RecipeWithNutrition, so every load path that builds a domain object carries it.
 */
@Entity(
        tableName = "user_item_data",
        primaryKeys = {"itemType", "itemId"},
        indices = {@Index(value = {"itemId"})}
)
public class UserItemDataEntity {

    public static final String TYPE_PRODUCT = "FOOD_PRODUCT";
    public static final String TYPE_RECIPE = "RECIPE";

    public static final String UNIT_G = "g";
    public static final String UNIT_ML = "ml";

    // ========== RETENTION SUBQUERIES (cache maintenance) ==========
    // Any row = kept. Favorite-only and customisation-only subsets are needed by
    // the Settings sections that clear one kind of pin.

    /** Products with any user data: never deleted by cache operations. */
    public static final String KEPT_PRODUCT_IDS =
            "SELECT itemId FROM user_item_data WHERE itemType = 'FOOD_PRODUCT'";
    /** Recipes with any user data: never deleted by cache operations. */
    public static final String KEPT_RECIPE_IDS =
            "SELECT itemId FROM user_item_data WHERE itemType = 'RECIPE'";

    public static final String FAVORITE_PRODUCT_IDS =
            "SELECT itemId FROM user_item_data WHERE itemType = 'FOOD_PRODUCT' AND isFavorite = 1";
    public static final String FAVORITE_RECIPE_IDS =
            "SELECT itemId FROM user_item_data WHERE itemType = 'RECIPE' AND isFavorite = 1";

    /** SQL condition: the row holds a customisation (anything but the favorite pin). */
    public static final String HAS_CUSTOMISATION =
            "(servingQuantity IS NOT NULL OR preferredUnit IS NOT NULL "
                    + "OR imagePath IS NOT NULL OR thumbnailPath IS NOT NULL)";

    public static final String CUSTOMISED_PRODUCT_IDS =
            "SELECT itemId FROM user_item_data WHERE itemType = 'FOOD_PRODUCT' AND " + HAS_CUSTOMISATION;
    public static final String CUSTOMISED_RECIPE_IDS =
            "SELECT itemId FROM user_item_data WHERE itemType = 'RECIPE' AND " + HAS_CUSTOMISATION;

    // ========== KEY ==========

    @NonNull
    private String itemType = "";       // TYPE_PRODUCT or TYPE_RECIPE

    @NonNull
    private String itemId = "";         // searchableId of the product or recipe

    // ========== USER DATA ==========

    private boolean isFavorite;

    /** Preferred typical serving, in servingUnit (change 5b). Null = use the source's. */
    @Nullable
    private Double servingQuantity;
    /** "g" or "ml"; set together with servingQuantity. */
    @Nullable
    private String servingUnit;

    /**
     * "g" or "ml": the unit the user chose on the input's "Measurements &
     * quantity" page. Null = the source's own unit. Added in v22 by a real
     * migration (AppDatabase.MIGRATION_21_22), so its column is nullable TEXT
     * with no default, exactly what ALTER TABLE ADD COLUMN creates.
     */
    @Nullable
    private String preferredUnit;

    /** Custom full-size photo (local file). */
    @Nullable
    private String imagePath;
    /** Custom thumbnail (local file). */
    @Nullable
    private String thumbnailPath;

    private long updatedAt;

    public UserItemDataEntity() {}

    /** An empty row for an item, ready for one field to be set. */
    @NonNull
    public static UserItemDataEntity empty(@NonNull String itemType, @NonNull String itemId, long now) {
        UserItemDataEntity entity = new UserItemDataEntity();
        entity.itemType = itemType;
        entity.itemId = itemId;
        entity.updatedAt = now;
        return entity;
    }

    /** The row of the given type in a relation's list (ids can repeat across types). */
    @Nullable
    public static UserItemDataEntity find(@Nullable List<UserItemDataEntity> rows,
                                          @NonNull String itemType) {
        if (rows == null) return null;
        for (UserItemDataEntity row : rows) {
            if (row != null && itemType.equals(row.itemType)) return row;
        }
        return null;
    }

    /**
     * The row's serving unit as a Unit: ML for "ml", G otherwise; null when no
     * serving is set. Static on purpose: an instance method named after the
     * column could be taken by Room for its getter.
     */
    @Nullable
    public static Unit servingUnitOf(@NonNull UserItemDataEntity data) {
        if (data.servingQuantity == null) return null;
        return UNIT_ML.equals(data.servingUnit) ? Unit.ML : Unit.G;
    }

    /** The row's preferred unit as a Unit (G or ML), or null when none is set. */
    @Nullable
    public static Unit preferredUnitOf(@NonNull UserItemDataEntity data) {
        if (data.preferredUnit == null) return null;
        return UNIT_ML.equals(data.preferredUnit) ? Unit.ML : Unit.G;
    }

    /** The column value for a Unit: "ml" for ML, "g" for anything else. */
    @NonNull
    public static String unitToColumn(@Nullable Unit unit) {
        return unit == Unit.ML ? UNIT_ML : UNIT_G;
    }

    /** True when nothing is set: such a row is deleted (see the class rules). */
    public boolean isEmpty() {
        return !isFavorite && servingQuantity == null && preferredUnit == null
                && imagePath == null && thumbnailPath == null;
    }

    // ========== GETTERS / SETTERS (Room) ==========

    @NonNull public String getItemType() { return itemType; }
    public void setItemType(@NonNull String itemType) { this.itemType = itemType; }

    @NonNull public String getItemId() { return itemId; }
    public void setItemId(@NonNull String itemId) { this.itemId = itemId; }

    public boolean isFavorite() { return isFavorite; }
    public void setFavorite(boolean favorite) { isFavorite = favorite; }

    @Nullable public Double getServingQuantity() { return servingQuantity; }
    public void setServingQuantity(@Nullable Double servingQuantity) { this.servingQuantity = servingQuantity; }

    @Nullable public String getServingUnit() { return servingUnit; }
    public void setServingUnit(@Nullable String servingUnit) { this.servingUnit = servingUnit; }

    @Nullable public String getPreferredUnit() { return preferredUnit; }
    public void setPreferredUnit(@Nullable String preferredUnit) { this.preferredUnit = preferredUnit; }

    @Nullable public String getImagePath() { return imagePath; }
    public void setImagePath(@Nullable String imagePath) { this.imagePath = imagePath; }

    @Nullable public String getThumbnailPath() { return thumbnailPath; }
    public void setThumbnailPath(@Nullable String thumbnailPath) { this.thumbnailPath = thumbnailPath; }

    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
}
