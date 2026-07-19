package li.masciul.sugardaddi.data.sources.fatsecret.api.dto;

import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Response from foods/search/v1 (platform.fatsecret.com/docs/v1/foods.search).
 *
 * foodDescription is a FORMATTED SUMMARY STRING, not structured data - e.g.
 * "Per 100g - Calories: 22kcal | Fat: 0.34g | Carbs: 3.28g | Protein: 3.09g".
 * There is no structured nutrition here at all. A selected result needs a
 * separate food/v5 (get by id) call - mapped by FoodGetResponse below - to
 * get real, structured macro/micronutrient data.
 */
public class FoodSearchResponse {

    @SerializedName("foods")
    public FoodsWrapper foods;

    public static class FoodsWrapper {
        @SerializedName("max_results") public int maxResults;
        @SerializedName("total_results") public int totalResults;
        @SerializedName("page_number") public int pageNumber;

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
        @SerializedName("food_description") public String foodDescription;
    }
}