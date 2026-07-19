package li.masciul.sugardaddi.data.sources.fatsecret.api.dto;

import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Response from recipes/search/v3. Field names and nesting match FatSecret's
 * documented schema exactly (platform.fatsecret.com/docs/v3/recipes.search) -
 * this proxy relays FatSecret's response verbatim, so this DTO is the real
 * contract, not a translation of it.
 *
 * recipe_nutrition here is a lightweight summary (calories/carbs/protein/fat
 * only) - the full nutrient profile requires a separate recipe/v2 (get by
 * id) call, mapped by RecipeGetResponse below.
 */
public class RecipeSearchResponse {

    @SerializedName("recipes")
    public RecipesWrapper recipes;

    public static class RecipesWrapper {
        @SerializedName("max_results")
        public int maxResults;

        @SerializedName("total_results")
        public int totalResults;

        @SerializedName("page_number")
        public int pageNumber;

        @SerializedName("recipe")
        @JsonAdapter(SingleOrArrayDeserializer.class)
        public List<RecipeSearchResult> recipe;
    }

    public static class RecipeSearchResult {
        @SerializedName("recipe_id")
        public long recipeId;

        @SerializedName("recipe_name")
        public String recipeName;

        @SerializedName("recipe_description")
        public String recipeDescription;

        @SerializedName("recipe_image")
        public String recipeImage;

        @SerializedName("recipe_nutrition")
        public RecipeNutritionSummary recipeNutrition;

        @SerializedName("recipe_ingredients")
        public RecipeIngredientsWrapper recipeIngredients;

        @SerializedName("recipe_types")
        public RecipeTypesWrapper recipeTypes;
    }

    public static class RecipeNutritionSummary {
        @SerializedName("calories") public Double calories;
        @SerializedName("carbohydrate") public Double carbohydrate;
        @SerializedName("protein") public Double protein;
        @SerializedName("fat") public Double fat;
    }

    public static class RecipeIngredientsWrapper {
        @SerializedName("ingredient")
        @JsonAdapter(SingleOrArrayDeserializer.class)
        public List<String> ingredient;
    }

    public static class RecipeTypesWrapper {
        @SerializedName("recipe_type")
        @JsonAdapter(SingleOrArrayDeserializer.class)
        public List<String> recipeType;
    }
}