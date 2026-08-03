package li.masciul.sugardaddi.data.sources.fatsecret.api.dto;

import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Response from foods/search - either foods/search/v1 or foods/search/v5,
 * depending on which glucogate's self-learned fallback actually served
 * (see glucogate's SearchFoods()) - the app has no way to know which in
 * advance, so both shapes are modeled here rather than assuming one.
 *
 * v1 root: {"foods": {"food": [...], max_results, total_results, page_number}}
 *   food_description is a FORMATTED SUMMARY STRING, not structured data -
 *   e.g. "Per 100g - Calories: 22kcal | Fat: 0.34g | Carbs: 3.28g |
 *   Protein: 3.09g". servings is absent. See
 *   FatSecretMapper.parseFoodDescriptionNutrition() for how this is
 *   turned into a best-effort estimate.
 *
 * v5 root: {"foods_search": {"results": {"food": [...]}, max_results,
 *   total_results, page_number}} - a different root key AND an extra
 *   "results" nesting level, confirmed against a real captured response,
 *   not assumed from documentation. Each food carries a full servings
 *   array identical in shape to food/v5's own detail response (reuses
 *   FoodGetResponse.ServingsWrapper/FoodServing directly) - real
 *   structured nutrition in search results, including the same
 *   serving_id=0 standardized-serving synthesis for Brand foods that
 *   food/v5 provides, confirmed live on a Valbest Chicken Breast result.
 *   food_description is absent in this shape.
 *
 * getFoodList() is the one method callers should use - it returns
 * whichever shape's list is actually present, so FatSecretMapper doesn't
 * need to know or care which version answered a given request.
 */
public class FoodSearchResponse {

    @SerializedName("foods")
    public FoodsWrapper foods;

    @SerializedName("foods_search")
    public FoodsSearchWrapper foodsSearch;

    /**
     * Whichever shape's list is actually populated - exactly one of the
     * two root fields above is ever non-null for a real response, since
     * one HTTP call is served by exactly one FatSecret version.
     */
    public List<FoodSearchResult> getFoodList() {
        if (foodsSearch != null && foodsSearch.results != null && foodsSearch.results.food != null) {
            return foodsSearch.results.food;
        }
        if (foods != null && foods.food != null) {
            return foods.food;
        }
        return null;
    }

    public static class FoodsWrapper {
        @SerializedName("max_results") public int maxResults;
        @SerializedName("total_results") public int totalResults;
        @SerializedName("page_number") public int pageNumber;

        @SerializedName("food")
        @JsonAdapter(SingleOrArrayDeserializer.class)
        public List<FoodSearchResult> food;
    }

    public static class FoodsSearchWrapper {
        @SerializedName("max_results") public int maxResults;
        @SerializedName("total_results") public int totalResults;
        @SerializedName("page_number") public int pageNumber;

        @SerializedName("results")
        public ResultsWrapper results;
    }

    public static class ResultsWrapper {
        @SerializedName("food")
        @JsonAdapter(SingleOrArrayDeserializer.class)
        public List<FoodSearchResult> food;
    }

    public static class FoodSearchResult {
        @SerializedName("food_id") public long foodId;
        @SerializedName("food_name") public String foodName;
        /** Only present when foodType is "Brand", e.g. "McDonald's". */
        @SerializedName("brand_name") public String brandName;
        /** "Brand" or "Generic". */
        @SerializedName("food_type") public String foodType;
        @SerializedName("food_url") public String foodUrl;

        /** v1 shape only - see class Javadoc. Null when v5 served this response. */
        @SerializedName("food_description") public String foodDescription;

        /**
         * v5 shape only - see class Javadoc. Null when v1 served this
         * response. Reuses FoodGetResponse's serving classes directly -
         * identical structure to food/v5's own detail response, confirmed
         * against a real captured search response, not assumed.
         */
        @SerializedName("servings")
        public FoodGetResponse.ServingsWrapper servings;

        /**
         * v5 shape only, and only present with include_food_images=true.
         * Confirmed present on generic foods in a real captured search
         * response; absent on Valbest Chicken Breast (a Brand item) in
         * the same response - matches FatSecret's own staged-rollout
         * language for Brand foods. Reuses FoodGetResponse's classes -
         * identical shape to food/v5's own detail response.
         */
        @SerializedName("food_images")
        public FoodGetResponse.FoodImagesWrapper foodImages;

        /** See foodImages' Javadoc - same access requirement and rollout pattern. */
        @SerializedName("food_sub_categories")
        public FoodGetResponse.FoodSubCategoriesWrapper foodSubCategories;
    }
}