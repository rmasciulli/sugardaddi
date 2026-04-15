package li.masciul.sugardaddi.data.database.relations;

import androidx.room.Embedded;
import androidx.room.Relation;

import li.masciul.sugardaddi.core.models.Meal;
import li.masciul.sugardaddi.data.database.entities.MealEntity;
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
     * Convert to domain model with nutrition
     */
    public Meal toMeal() {
        if (meal == null) {
            return null;
        }

        // Convert meal entity to domain model
        Meal mealModel = meal.toMeal();

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