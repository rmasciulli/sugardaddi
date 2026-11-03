package li.masciul.sugardaddi.data.database.relations;

import androidx.room.Embedded;
import androidx.room.Relation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import li.masciul.sugardaddi.core.models.FoodPortion;
import li.masciul.sugardaddi.core.models.Meal;
import li.masciul.sugardaddi.data.database.entities.MealEntity;
import li.masciul.sugardaddi.data.database.entities.MealPortionEntity;
import li.masciul.sugardaddi.data.database.entities.NutritionEntity;

/**
 * MealWithNutrition - Combines meal and nutrition data
 *
 * Room automatically handles the JOIN when you query this.
 * Similar to FoodProductWithNutrition but for meals.
 */
public class MealWithNutrition {

    @Embedded
    public MealEntity meal;

    @Relation(
            parentColumn = "id",
            entityColumn = "sourceId",
            entity = NutritionEntity.class
    )
    public NutritionEntity nutrition;

    /**
     * The meal's logged entries. Every query returning MealWithNutrition (journal,
     * templates, planned meals, search, stats) loads them with no extra call -
     * Room runs the relation inside the query's @Transaction.
     */
    @Relation(
            parentColumn = "id",
            entityColumn = "mealId",
            entity = MealPortionEntity.class
    )
    public List<MealPortionEntity> portions;

    /**
     * Convert to domain model with portions and nutrition
     */
    public Meal toMeal() {
        if (meal == null) {
            return null;
        }

        // Convert meal entity to domain model
        Meal mealModel = meal.toMeal();

        // Portions in display order (Room doesn't order relation rows). Still
        // unresolved: MealRepository attaches each product/recipe afterwards.
        if (portions != null && !portions.isEmpty()) {
            List<MealPortionEntity> ordered = new ArrayList<>(portions);
            ordered.sort(Comparator.comparingInt(MealPortionEntity::getOrderIndex));
            List<FoodPortion> modelPortions = new ArrayList<>(ordered.size());
            for (MealPortionEntity row : ordered) {
                modelPortions.add(row.toPortion());
            }
            mealModel.setPortions(modelPortions);
        }

        // Add nutrition if available
        if (nutrition != null) {
            mealModel.setProvidedNutrition(nutrition.toNutrition());
        }

        return mealModel;
    }

    /**
     * Check if this meal has nutrition data
     */
    public boolean hasNutrition() {
        return nutrition != null && nutrition.hasBasicData();
    }

    /**
     * Get meal ID
     */
    public String getMealId() {
        return meal != null ? meal.getId() : null;
    }

    /**
     * Get meal type for filtering
     */
    public String getMealType() {
        return meal != null ? meal.getMealType() : null;
    }

    /**
     * Get meal date for filtering
     */
    public long getMealDateTime() {
        return meal != null ? meal.getMealDateTime() : 0;
    }
}