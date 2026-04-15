package li.masciul.sugardaddi.data.database.relations;

import androidx.room.Embedded;
import androidx.room.Relation;

import li.masciul.sugardaddi.core.models.Recipe;
import li.masciul.sugardaddi.data.database.entities.RecipeEntity;
import li.masciul.sugardaddi.data.database.entities.NutritionEntity;

/**
 * RecipeWithNutrition - Combines recipe and nutrition data
 *
 * Room automatically handles the JOIN when you query this.
 * Similar to FoodProductWithNutrition but for recipes.
 */
public class RecipeWithNutrition {

    @Embedded
    public RecipeEntity recipe;

    @Relation(
            parentColumn = "id",
            entityColumn = "sourceId",
            entity = NutritionEntity.class
    )
    public NutritionEntity nutrition;

    /**
     * Convert to domain model with nutrition
     */
    public Recipe toRecipe() {
        if (recipe == null) {
            return null;
        }

        // Convert recipe entity to domain model
        Recipe recipeModel = recipe.toRecipe();

        // Add nutrition if available
        if (nutrition != null) {
            recipeModel.setNutrition(nutrition.toNutrition());
        }

        return recipeModel;
    }

    /**
     * Check if this recipe has nutrition data
     */
    public boolean hasNutrition() {
        return nutrition != null && nutrition.hasBasicData();
    }

    /**
     * Get recipe ID
     */
    public String getRecipeId() {
        return recipe != null ? recipe.getId() : null;
    }

    /**
     * Get difficulty for filtering
     */
    public String getDifficulty() {
        return recipe != null ? recipe.getDifficulty() : null;
    }

    /**
     * Check if recipe is favorite
     */
    public boolean isFavorite() {
        return recipe != null && recipe.isFavorite();
    }

    /**
     * Check if recipe is public
     */
    public boolean isPublic() {
        return recipe != null && recipe.isPublic();
    }

    /**
     * Get total time (prep + cook)
     */
    public int getTotalTimeMinutes() {
        if (recipe != null) {
            return recipe.getPrepTimeMinutes() + recipe.getCookTimeMinutes();
        }
        return 0;
    }
}