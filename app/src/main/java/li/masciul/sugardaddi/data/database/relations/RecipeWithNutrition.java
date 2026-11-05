package li.masciul.sugardaddi.data.database.relations;

import androidx.annotation.Nullable;
import androidx.room.Embedded;
import androidx.room.Relation;

import java.util.List;

import li.masciul.sugardaddi.core.models.Recipe;
import li.masciul.sugardaddi.data.database.entities.RecipeEntity;
import li.masciul.sugardaddi.data.database.entities.NutritionEntity;
import li.masciul.sugardaddi.data.database.entities.UserItemDataEntity;

/**
 * RecipeWithNutrition - Combines recipe and nutrition data
 *
 * Room automatically handles the JOIN when you query this.
 * Similar to FoodProductWithNutrition but for recipes.
 *
 * Also carries the user's data on the recipe (favorite, custom photos,
 * preferred serving) from user_item_data. Build domain recipes from cached
 * rows through this class, not RecipeEntity.toRecipe() alone.
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
     * Rows of user_item_data with this id. A product and a recipe can share an
     * id, so the matching row is picked by itemType in userData().
     */
    @Relation(
            parentColumn = "id",
            entityColumn = "itemId",
            entity = UserItemDataEntity.class
    )
    public List<UserItemDataEntity> userDataRows;

    /** The user's data on this recipe, or null when there is none. */
    @Nullable
    public UserItemDataEntity userData() {
        return UserItemDataEntity.find(userDataRows, UserItemDataEntity.TYPE_RECIPE);
    }

    /**
     * Convert to domain model with nutrition and the user's data
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

        // User data. Recipe.setFavorite() stamps lastUpdated (touch()), which
        // would make a cached recipe look freshly synced: keep the stored value.
        UserItemDataEntity data = userData();
        if (data != null) {
            long lastUpdated = recipeModel.getLastUpdated();
            recipeModel.setFavorite(data.isFavorite());
            recipeModel.setUserImagePath(data.getImagePath());
            recipeModel.setUserThumbnailPath(data.getThumbnailPath());
            recipeModel.setUserServing(data.getServingQuantity(),
                    UserItemDataEntity.servingUnitOf(data));
            recipeModel.setLastUpdated(lastUpdated);
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
        UserItemDataEntity data = userData();
        return data != null && data.isFavorite();
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