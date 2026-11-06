package li.masciul.sugardaddi.data.database.dao;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

import java.util.List;

import li.masciul.sugardaddi.data.database.entities.UserItemDataEntity;

/**
 * UserItemDataDao - reads and writes of the user's data on items.
 *
 * Reads for display go through the @Relation of FoodProductWithNutrition /
 * RecipeWithNutrition; this DAO serves direct lookups, the setters, the
 * Settings operations and the image purge.
 *
 * SETTERS: each one creates the row when missing, sets one field, then
 * deletes rows left empty (see UserItemDataEntity), in one transaction. Callers
 * never have to care whether the row exists.
 *
 * Every method must run off the main thread.
 */
@Dao
public abstract class UserItemDataDao {

    // ========== READS ==========

    @Nullable
    @Query("SELECT * FROM user_item_data WHERE itemType = :itemType AND itemId = :itemId LIMIT 1")
    public abstract UserItemDataEntity get(String itemType, String itemId);

    @Query("SELECT COUNT(*) FROM user_item_data WHERE itemType = :itemType AND isFavorite = 1")
    public abstract int getFavoriteCount(String itemType);

    /** Items with a customisation (preferred serving or unit, custom photo), for Settings. */
    @Query("SELECT COUNT(*) FROM user_item_data WHERE " + UserItemDataEntity.HAS_CUSTOMISATION)
    public abstract int getCustomisedCount();

    /** Every custom photo path, so the orphan image purge keeps those files. */
    @Query("SELECT imagePath FROM user_item_data WHERE imagePath IS NOT NULL "
            + "UNION "
            + "SELECT thumbnailPath FROM user_item_data WHERE thumbnailPath IS NOT NULL")
    public abstract List<String> getAllCustomImagePaths();

    // ========== SETTERS ==========

    @Transaction
    public void setFavorite(@NonNull String itemType, @NonNull String itemId, boolean favorite) {
        long now = System.currentTimeMillis();
        insertIfAbsent(UserItemDataEntity.empty(itemType, itemId, now));
        updateFavorite(itemType, itemId, favorite, now);
        deleteEmpty();
    }

    /** Custom full-size photo; null removes it. */
    @Transaction
    public void setImagePath(@NonNull String itemType, @NonNull String itemId, @Nullable String path) {
        long now = System.currentTimeMillis();
        insertIfAbsent(UserItemDataEntity.empty(itemType, itemId, now));
        updateImagePath(itemType, itemId, path, now);
        deleteEmpty();
    }

    /** Custom thumbnail; null removes it. */
    @Transaction
    public void setThumbnailPath(@NonNull String itemType, @NonNull String itemId, @Nullable String path) {
        long now = System.currentTimeMillis();
        insertIfAbsent(UserItemDataEntity.empty(itemType, itemId, now));
        updateThumbnailPath(itemType, itemId, path, now);
        deleteEmpty();
    }

    /**
     * Preferred typical serving (change 5b); a null quantity removes it.
     *
     * @param unit UserItemDataEntity.UNIT_G or UNIT_ML; ignored when quantity is null
     */
    @Transaction
    public void setServing(@NonNull String itemType, @NonNull String itemId,
                           @Nullable Double quantity, @Nullable String unit) {
        long now = System.currentTimeMillis();
        insertIfAbsent(UserItemDataEntity.empty(itemType, itemId, now));
        updateServing(itemType, itemId, quantity, quantity != null ? unit : null, now);
        deleteEmpty();
    }

    /**
     * Unit the item is shown and logged in (the input's "Measurements &
     * quantity" page); null goes back to the source's own unit.
     *
     * @param unit UserItemDataEntity.UNIT_G, UNIT_ML or null
     */
    @Transaction
    public void setPreferredUnit(@NonNull String itemType, @NonNull String itemId,
                                 @Nullable String unit) {
        long now = System.currentTimeMillis();
        insertIfAbsent(UserItemDataEntity.empty(itemType, itemId, now));
        updatePreferredUnit(itemType, itemId, unit, now);
        deleteEmpty();
    }

    // ========== SETTINGS OPERATIONS ==========

    /** Every favorite pin removed (rows left with a customisation stay). */
    @Transaction
    public void clearAllFavorites() {
        unfavoriteAll();
        deleteEmpty();
    }

    /**
     * "Reset my customisations": preferred servings and units and custom photos removed
     * (favorites stay). The photo files become orphans for the image purge.
     */
    @Transaction
    public void clearAllCustomisations() {
        clearCustomisationColumns();
        deleteEmpty();
    }

    /** Rows of items that no longer exist in either table (swept by eviction). */
    @Query("DELETE FROM user_item_data WHERE "
            + "(itemType = 'FOOD_PRODUCT' AND itemId NOT IN (SELECT id FROM food_products)) "
            + "OR (itemType = 'RECIPE' AND itemId NOT IN (SELECT id FROM recipes))")
    public abstract int deleteOrphans();

    // ========== BUILDING BLOCKS ==========

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract void insertIfAbsent(UserItemDataEntity entity);

    @Query("UPDATE user_item_data SET isFavorite = :favorite, updatedAt = :now "
            + "WHERE itemType = :itemType AND itemId = :itemId")
    protected abstract void updateFavorite(String itemType, String itemId, boolean favorite, long now);

    @Query("UPDATE user_item_data SET imagePath = :path, updatedAt = :now "
            + "WHERE itemType = :itemType AND itemId = :itemId")
    protected abstract void updateImagePath(String itemType, String itemId, String path, long now);

    @Query("UPDATE user_item_data SET thumbnailPath = :path, updatedAt = :now "
            + "WHERE itemType = :itemType AND itemId = :itemId")
    protected abstract void updateThumbnailPath(String itemType, String itemId, String path, long now);

    @Query("UPDATE user_item_data SET servingQuantity = :quantity, servingUnit = :unit, updatedAt = :now "
            + "WHERE itemType = :itemType AND itemId = :itemId")
    protected abstract void updateServing(String itemType, String itemId, Double quantity, String unit, long now);

    @Query("UPDATE user_item_data SET preferredUnit = :unit, updatedAt = :now "
            + "WHERE itemType = :itemType AND itemId = :itemId")
    protected abstract void updatePreferredUnit(String itemType, String itemId, String unit, long now);

    @Query("UPDATE user_item_data SET isFavorite = 0")
    protected abstract void unfavoriteAll();

    @Query("UPDATE user_item_data SET servingQuantity = NULL, servingUnit = NULL, "
            + "preferredUnit = NULL, imagePath = NULL, thumbnailPath = NULL")
    protected abstract void clearCustomisationColumns();

    /** Rows holding nothing any more: removing them releases the item to the cache rules. */
    @Query("DELETE FROM user_item_data WHERE isFavorite = 0 AND NOT " + UserItemDataEntity.HAS_CUSTOMISATION)
    protected abstract int deleteEmpty();
}
