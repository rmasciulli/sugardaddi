package li.masciul.sugardaddi.data.sources.fatsecret.api.dto;

import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Response from recipe/v2 (get by id). Field names match FatSecret's
 * documented v2 schema exactly (platform.fatsecret.com/docs/v2/recipe.get).
 *
 * UNIT WARNING - read before mapping this to Nutrition:
 * vitaminAPercentDv/vitaminCPercentDv/calciumPercentDv/ironPercentDv are
 * PERCENTAGE OF DAILY VALUE (2000-calorie diet basis), NOT absolute mg/mcg -
 * explicitly documented as such by FatSecret. This is a genuinely different
 * unit from FoodGetResponse's vitamin_a/vitamin_c/calcium/iron fields, which
 * ARE absolute values, despite sharing the same field names. Do not map both
 * the same way - see FatSecretMapper for the %DV -> absolute conversion.
 *
 * gramsPerPortion (new in v2) is what makes per-100g normalization possible
 * without summing ingredients ourselves - FatSecretMapper uses this directly.
 */
public class RecipeGetResponse {

    @SerializedName("recipe")
    public RecipeDetail recipe;

    public static class RecipeDetail {
        @SerializedName("recipe_id") public long recipeId;
        @SerializedName("recipe_name") public String recipeName;
        @SerializedName("recipe_url") public String recipeUrl;
        @SerializedName("recipe_description") public String recipeDescription;
        @SerializedName("number_of_servings") public Double numberOfServings;
        @SerializedName("grams_per_portion") public Double gramsPerPortion;
        @SerializedName("preparation_time_min") public Integer preparationTimeMin;
        @SerializedName("cooking_time_min") public Integer cookingTimeMin;
        @SerializedName("rating") public Integer rating;

        @SerializedName("recipe_types")
        public RecipeTypesWrapper recipeTypes;

        @SerializedName("recipe_categories")
        public RecipeCategoriesWrapper recipeCategories;

        @SerializedName("recipe_images")
        public RecipeImagesWrapper recipeImages;

        @SerializedName("serving_sizes")
        public ServingSizesWrapper servingSizes;

        @SerializedName("ingredients")
        public IngredientsWrapper ingredients;

        @SerializedName("directions")
        public DirectionsWrapper directions;
    }

    public static class RecipeTypesWrapper {
        @SerializedName("recipe_type")
        @JsonAdapter(SingleOrArrayDeserializer.class)
        public List<String> recipeType;
    }

    public static class RecipeCategoriesWrapper {
        @SerializedName("recipe_category")
        @JsonAdapter(SingleOrArrayDeserializer.class)
        public List<RecipeCategory> recipeCategory;
    }

    public static class RecipeCategory {
        @SerializedName("recipe_category_name") public String recipeCategoryName;
        @SerializedName("recipe_category_url") public String recipeCategoryUrl;
    }

    public static class RecipeImagesWrapper {
        @SerializedName("recipe_image")
        @JsonAdapter(SingleOrArrayDeserializer.class)
        public List<String> recipeImage;
    }

    public static class ServingSizesWrapper {
        // Always a single object, never array-wrapped - recipe.get only
        // ever returns nutrition "for the standard serving" (per FatSecret's
        // own description), unlike food.get which returns many servings.
        @SerializedName("serving")
        public ServingNutrition serving;
    }

    public static class ServingNutrition {
        @SerializedName("serving_size") public String servingSize;
        @SerializedName("calories") public Double calories;
        @SerializedName("carbohydrate") public Double carbohydrate;
        @SerializedName("protein") public Double protein;
        @SerializedName("fat") public Double fat;
        @SerializedName("saturated_fat") public Double saturatedFat;
        @SerializedName("polyunsaturated_fat") public Double polyunsaturatedFat;
        @SerializedName("monounsaturated_fat") public Double monounsaturatedFat;
        @SerializedName("trans_fat") public Double transFat;
        @SerializedName("cholesterol") public Double cholesterol;
        @SerializedName("sodium") public Double sodium;
        @SerializedName("potassium") public Double potassium;
        @SerializedName("fiber") public Double fiber;
        @SerializedName("sugar") public Double sugar;
        // % of daily value, NOT absolute - see class Javadoc above.
        @SerializedName("vitamin_a") public Double vitaminAPercentDv;
        @SerializedName("vitamin_c") public Double vitaminCPercentDv;
        @SerializedName("calcium") public Double calciumPercentDv;
        @SerializedName("iron") public Double ironPercentDv;
    }

    public static class IngredientsWrapper {
        @SerializedName("ingredient")
        @JsonAdapter(SingleOrArrayDeserializer.class)
        public List<RecipeIngredient> ingredient;
    }

    public static class RecipeIngredient {
        @SerializedName("food_id") public long foodId;
        @SerializedName("food_name") public String foodName;
        @SerializedName("serving_id") public long servingId;
        @SerializedName("number_of_units") public Double numberOfUnits;
        @SerializedName("measurement_description") public String measurementDescription;
        @SerializedName("ingredient_url") public String ingredientUrl;
        @SerializedName("ingredient_description") public String ingredientDescription;
    }

    public static class DirectionsWrapper {
        @SerializedName("direction")
        @JsonAdapter(SingleOrArrayDeserializer.class)
        public List<RecipeDirection> direction;
    }

    public static class RecipeDirection {
        @SerializedName("direction_number") public int directionNumber;
        @SerializedName("direction_description") public String directionDescription;
    }
}