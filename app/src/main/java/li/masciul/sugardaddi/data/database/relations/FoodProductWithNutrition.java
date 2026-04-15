package li.masciul.sugardaddi.data.database.relations;

import androidx.room.Embedded;
import androidx.room.Relation;

import li.masciul.sugardaddi.core.models.FoodProduct;
import li.masciul.sugardaddi.data.database.entities.FoodProductEntity;
import li.masciul.sugardaddi.data.database.entities.NutritionEntity;

/**
 * FoodProductWithNutrition - Combines product and nutrition data
 *
 * Room automatically handles the JOIN when you query this
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
     * Convert to domain model
     */
    public FoodProduct toFoodProduct() {
        FoodProduct foodProduct = product.toFoodProduct();
        if (nutrition != null) {
            foodProduct.setNutrition(nutrition.toNutrition());
        }
        return foodProduct;
    }
}