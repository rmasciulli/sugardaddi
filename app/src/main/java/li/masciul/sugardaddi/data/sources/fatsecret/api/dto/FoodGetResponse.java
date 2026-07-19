package li.masciul.sugardaddi.data.sources.fatsecret.api.dto;

import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Response from food/v5 (get by id) - platform.fatsecret.com/docs/v5/food.get.
 *
 * Unlike RecipeGetResponse, vitaminA/vitaminC/calcium/iron here ARE absolute
 * values (micrograms/milligrams, as documented), not percent-of-daily-value.
 * Do not conflate with recipe.get's fields of the same name - see
 * RecipeGetResponse's Javadoc for that distinction.
 *
 * For "Brand" foods, FatSecret often includes a synthetic serving_id=0 entry
 * representing 100g/100ml/1oz - FatSecretMapper prefers this serving when
 * present, since it needs no further normalization math at all.
 */
public class FoodGetResponse {

    @SerializedName("food")
    public FoodDetail food;

    public static class FoodDetail {
        @SerializedName("food_id") public long foodId;
        @SerializedName("food_name") public String foodName;
        /** "Brand" or "Generic". */
        @SerializedName("food_type") public String foodType;
        @SerializedName("food_url") public String foodUrl;
        @SerializedName("brand_name") public String brandName;

        @SerializedName("servings")
        public ServingsWrapper servings;
    }

    public static class ServingsWrapper {
        @SerializedName("serving")
        @JsonAdapter(SingleOrArrayDeserializer.class)
        public List<FoodServing> serving;
    }

    public static class FoodServing {
        @SerializedName("serving_id") public long servingId;
        @SerializedName("serving_description") public String servingDescription;
        @SerializedName("serving_url") public String servingUrl;
        @SerializedName("metric_serving_amount") public Double metricServingAmount;
        /** "g", "ml", or "oz". */
        @SerializedName("metric_serving_unit") public String metricServingUnit;
        @SerializedName("number_of_units") public Double numberOfUnits;
        @SerializedName("measurement_description") public String measurementDescription;
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
        @SerializedName("added_sugars") public Double addedSugars;
        /** Absolute micrograms - not %DV, unlike recipe.get's field of the same name. */
        @SerializedName("vitamin_a") public Double vitaminA;
        /** Absolute milligrams - not %DV, unlike recipe.get's field of the same name. */
        @SerializedName("vitamin_c") public Double vitaminC;
        @SerializedName("vitamin_d") public Double vitaminD;
        /** Absolute milligrams - not %DV, unlike recipe.get's field of the same name. */
        @SerializedName("calcium") public Double calcium;
        /** Absolute milligrams - not %DV, unlike recipe.get's field of the same name. */
        @SerializedName("iron") public Double iron;
        @SerializedName("is_default") public Integer isDefault;
    }
}