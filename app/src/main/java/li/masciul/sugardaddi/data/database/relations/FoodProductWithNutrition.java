package li.masciul.sugardaddi.data.database.relations;

import androidx.annotation.Nullable;
import androidx.room.Embedded;
import androidx.room.Relation;

import java.util.List;

import li.masciul.sugardaddi.core.models.FoodProduct;
import li.masciul.sugardaddi.data.database.entities.FoodProductEntity;
import li.masciul.sugardaddi.data.database.entities.NutritionEntity;
import li.masciul.sugardaddi.data.database.entities.UserItemDataEntity;

/**
 * FoodProductWithNutrition - Combines product and nutrition data
 *
 * Room automatically handles the JOIN when you query this.
 *
 * Also carries the user's data on the product (favorite, custom photos,
 * preferred serving) from user_item_data, so every load path that builds a
 * FoodProduct through this class gets it. Build domain products from cached
 * rows through this class, not FoodProductEntity.toFoodProduct() alone.
 */
public class FoodProductWithNutrition {

    @Embedded
    public FoodProductEntity product;

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

    /** The user's data on this product, or null when there is none. */
    @Nullable
    public UserItemDataEntity userData() {
        return UserItemDataEntity.find(userDataRows, UserItemDataEntity.TYPE_PRODUCT);
    }

    /**
     * Convert to domain model
     */
    public FoodProduct toFoodProduct() {
        FoodProduct foodProduct = product.toFoodProduct();
        if (nutrition != null) {
            foodProduct.setNutrition(nutrition.toNutrition());
        }
        UserItemDataEntity data = userData();
        if (data != null) {
            foodProduct.setFavorite(data.isFavorite());
            foodProduct.setUserImagePath(data.getImagePath());
            foodProduct.setUserThumbnailPath(data.getThumbnailPath());
            foodProduct.setUserServing(data.getServingQuantity(),
                    UserItemDataEntity.servingUnitOf(data));
        }
        return foodProduct;
    }
}