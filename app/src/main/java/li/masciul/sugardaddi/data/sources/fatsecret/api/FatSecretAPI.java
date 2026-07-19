package li.masciul.sugardaddi.data.sources.fatsecret.api;

import li.masciul.sugardaddi.data.sources.fatsecret.api.dto.FoodGetResponse;
import li.masciul.sugardaddi.data.sources.fatsecret.api.dto.FoodSearchResponse;
import li.masciul.sugardaddi.data.sources.fatsecret.api.dto.RecipeGetResponse;
import li.masciul.sugardaddi.data.sources.fatsecret.api.dto.RecipeSearchResponse;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

/**
 * Retrofit interface for glucogate - NOT FatSecret directly. Base URL is
 * glucogate's own address; FatSecret's consumer credentials never appear
 * anywhere in this app. Query parameter names match FatSecret's real
 * contract exactly (glucogate forwards them verbatim), not a translation
 * of them - search_expression, recipe_id, food_id, page_number, etc. are
 * FatSecret's own parameter names.
 *
 * page_number is FatSecret's own convention: zero-based. DataSource.search()'s
 * page parameter is 1-based (matching every other source in this app) -
 * FatSecretDataSource converts between the two, this interface does not.
 */
public interface FatSecretAPI {

    @GET("fatsecret/recipes/search")
    Call<RecipeSearchResponse> searchRecipes(
            @Query("search_expression") String searchExpression,
            @Query("page_number") int pageNumber,
            @Query("max_results") int maxResults,
            @Query("format") String format
    );

    @GET("fatsecret/recipe")
    Call<RecipeGetResponse> getRecipe(
            @Query("recipe_id") String recipeId,
            @Query("format") String format
    );

    @GET("fatsecret/foods/search")
    Call<FoodSearchResponse> searchFoods(
            @Query("search_expression") String searchExpression,
            @Query("page_number") int pageNumber,
            @Query("max_results") int maxResults,
            @Query("format") String format
    );

    @GET("fatsecret/food")
    Call<FoodGetResponse> getFood(
            @Query("food_id") String foodId,
            @Query("format") String format
    );
}