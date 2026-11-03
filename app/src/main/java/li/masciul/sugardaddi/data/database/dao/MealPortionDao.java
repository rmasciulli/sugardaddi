package li.masciul.sugardaddi.data.database.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

import li.masciul.sugardaddi.data.database.entities.MealPortionEntity;

/**
 * MealPortionDao - writes for meal_portions.
 *
 * Reads go through MealWithNutrition's @Relation, so every query that already
 * returns meals loads their portions with no extra call. Writes are done by
 * MealRepository inside the meal's own transaction: delete the meal's rows,
 * then insert the current list (replaceForMeal pattern).
 */
@Dao
public interface MealPortionDao {

    /**
     * ABORT, not REPLACE: the unique (mealId, itemType, itemId) index exists to
     * enforce one portion per item. A duplicate must fail the transaction, never
     * silently overwrite the other entry.
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insertAll(List<MealPortionEntity> portions);

    @Query("DELETE FROM meal_portions WHERE mealId = :mealId")
    int deleteByMealId(String mealId);

    /**
     * Portions whose meal no longer exists. Only possible if a meal row was
     * deleted without going through MealRepository while foreign keys were not
     * enforced; swept by cache eviction so the table never accumulates them.
     */
    @Query("DELETE FROM meal_portions WHERE mealId NOT IN (SELECT id FROM meals)")
    int deleteOrphans();
}
