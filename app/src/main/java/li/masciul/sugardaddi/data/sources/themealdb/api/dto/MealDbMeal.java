package li.masciul.sugardaddi.data.sources.themealdb.api.dto;

import androidx.annotation.Nullable;
import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

/**
 * MealDbMeal - Gson DTO for a full meal object from TheMealDB API.
 *
 * Maps the response of:
 *   GET https://www.themealdb.com/api/json/v1/1/lookup.php?i={id}
 *   GET https://www.themealdb.com/api/json/v1/1/search.php?s={query}
 *
 * Both endpoints return the same full object structure inside a "meals" array:
 * {
 *   "meals": [ { ...MealDbMeal fields... } ]
 * }
 *
 * IMPORTANT - FLAT INGREDIENT SCHEMA:
 * TheMealDB does not use a nested array for ingredients. It uses 20 parallel
 * string fields (strIngredient1..20, strMeasure1..20). Empty slots are either
 * null or an empty string "". Use getIngredients() to get a clean parsed list.
 *
 * NUTRITION DATA: None. TheMealDB v1 does not provide any nutritional values.
 * Neither macronutrients, calories, nor micronutrients are available from this API.
 *
 * LANGUAGE: English only. The free v1 API has no localisation support.
 *
 * @see MealDbSearchResponse  - wraps this DTO in a "meals" array
 * @see MealDbIngredient      - parsed ingredient+measure pair
 */
public class MealDbMeal {

    // ========== IDENTIFICATION ==========

    /** TheMealDB unique numeric ID for this meal (stored as String in the API). */
    @SerializedName("idMeal")
    @Nullable
    private String idMeal;

    /** Meal name in English. */
    @SerializedName("strMeal")
    @Nullable
    private String strMeal;

    /**
     * Alternate meal name (rare, usually null in v1).
     * Example: "Teriyaki Chicken Casserole" may have "Chicken Teriyaki" as alternate.
     */
    @SerializedName("strMealAlternate")
    @Nullable
    private String strMealAlternate;

    // ========== CLASSIFICATION ==========

    /**
     * Category name. Single value, English.
     * Examples: "Chicken", "Beef", "Vegetarian", "Dessert", "Seafood"
     * Full category list available via /categories.php
     */
    @SerializedName("strCategory")
    @Nullable
    private String strCategory;

    /**
     * Cuisine area (country/region of origin). Single value, English.
     * Examples: "Japanese", "French", "Indian", "British", "American"
     * Full area list available via /list.php?a=list
     */
    @SerializedName("strArea")
    @Nullable
    private String strArea;

    // ========== CONTENT ==========

    /**
     * Full cooking instructions as a plain text blob. English only.
     * May contain newlines and numbered steps, but formatting is inconsistent
     * across recipes - do not rely on any specific structure.
     * Can be very long (500–2000+ characters).
     */
    @SerializedName("strInstructions")
    @Nullable
    private String strInstructions;

    /**
     * Tags: freeform comma-separated string. Not structured.
     * Examples: "Meat,Casserole", "Pasta,Baking", "Vegetarian,Healthy"
     * May be null for many recipes.
     */
    @SerializedName("strTags")
    @Nullable
    private String strTags;

    // ========== MEDIA ==========

    /**
     * Thumbnail image URL. CDN-hosted JPEG.
     * Example: "https://www.themealdb.com/images/media/meals/wvpsxx1468256321.jpg"
     * Always present for published meals. Load with Glide.
     */
    @SerializedName("strMealThumb")
    @Nullable
    private String strMealThumb;

    /** YouTube video URL for the recipe. May be null. */
    @SerializedName("strYoutube")
    @Nullable
    private String strYoutube;

    /** Original source URL (food blog, website). Often null in v1. */
    @SerializedName("strSource")
    @Nullable
    private String strSource;

    // ========== INGREDIENTS (flat schema, 20 slots) ==========
    // TheMealDB uses parallel indexed fields instead of a JSON array.
    // Empty/null slots mean the recipe has fewer than 20 ingredients.
    // Use getIngredients() rather than accessing these fields directly.

    @SerializedName("strIngredient1")  @Nullable private String strIngredient1;
    @SerializedName("strIngredient2")  @Nullable private String strIngredient2;
    @SerializedName("strIngredient3")  @Nullable private String strIngredient3;
    @SerializedName("strIngredient4")  @Nullable private String strIngredient4;
    @SerializedName("strIngredient5")  @Nullable private String strIngredient5;
    @SerializedName("strIngredient6")  @Nullable private String strIngredient6;
    @SerializedName("strIngredient7")  @Nullable private String strIngredient7;
    @SerializedName("strIngredient8")  @Nullable private String strIngredient8;
    @SerializedName("strIngredient9")  @Nullable private String strIngredient9;
    @SerializedName("strIngredient10") @Nullable private String strIngredient10;
    @SerializedName("strIngredient11") @Nullable private String strIngredient11;
    @SerializedName("strIngredient12") @Nullable private String strIngredient12;
    @SerializedName("strIngredient13") @Nullable private String strIngredient13;
    @SerializedName("strIngredient14") @Nullable private String strIngredient14;
    @SerializedName("strIngredient15") @Nullable private String strIngredient15;
    @SerializedName("strIngredient16") @Nullable private String strIngredient16;
    @SerializedName("strIngredient17") @Nullable private String strIngredient17;
    @SerializedName("strIngredient18") @Nullable private String strIngredient18;
    @SerializedName("strIngredient19") @Nullable private String strIngredient19;
    @SerializedName("strIngredient20") @Nullable private String strIngredient20;

    // ========== MEASURES (flat schema, 20 slots) ==========

    @SerializedName("strMeasure1")  @Nullable private String strMeasure1;
    @SerializedName("strMeasure2")  @Nullable private String strMeasure2;
    @SerializedName("strMeasure3")  @Nullable private String strMeasure3;
    @SerializedName("strMeasure4")  @Nullable private String strMeasure4;
    @SerializedName("strMeasure5")  @Nullable private String strMeasure5;
    @SerializedName("strMeasure6")  @Nullable private String strMeasure6;
    @SerializedName("strMeasure7")  @Nullable private String strMeasure7;
    @SerializedName("strMeasure8")  @Nullable private String strMeasure8;
    @SerializedName("strMeasure9")  @Nullable private String strMeasure9;
    @SerializedName("strMeasure10") @Nullable private String strMeasure10;
    @SerializedName("strMeasure11") @Nullable private String strMeasure11;
    @SerializedName("strMeasure12") @Nullable private String strMeasure12;
    @SerializedName("strMeasure13") @Nullable private String strMeasure13;
    @SerializedName("strMeasure14") @Nullable private String strMeasure14;
    @SerializedName("strMeasure15") @Nullable private String strMeasure15;
    @SerializedName("strMeasure16") @Nullable private String strMeasure16;
    @SerializedName("strMeasure17") @Nullable private String strMeasure17;
    @SerializedName("strMeasure18") @Nullable private String strMeasure18;
    @SerializedName("strMeasure19") @Nullable private String strMeasure19;
    @SerializedName("strMeasure20") @Nullable private String strMeasure20;

    // ========== CONSTRUCTOR ==========

    /** Default constructor required by Gson. */
    public MealDbMeal() {}

    // ========== ACCESSORS ==========

    @Nullable public String getIdMeal()           { return idMeal; }
    @Nullable public String getStrMeal()          { return strMeal; }
    @Nullable public String getStrMealAlternate() { return strMealAlternate; }
    @Nullable public String getStrCategory()      { return strCategory; }
    @Nullable public String getStrArea()          { return strArea; }
    @Nullable public String getStrInstructions()  { return strInstructions; }
    @Nullable public String getStrTags()          { return strTags; }
    @Nullable public String getStrMealThumb()     { return strMealThumb; }
    @Nullable public String getStrYoutube()       { return strYoutube; }
    @Nullable public String getStrSource()        { return strSource; }

    // ========== INGREDIENT PARSING ==========

    /**
     * Parse the 20 flat ingredient/measure fields into a clean list.
     *
     * Iteration rules:
     * - Skip any slot where the ingredient name is null or blank
     * - Treat a blank measure as null (no quantity specified)
     * - Stop early if we hit a null ingredient (remaining slots are guaranteed empty)
     *
     * The returned list contains only real ingredients - no empty slots.
     *
     * @return Immutable snapshot of this meal's ingredients. Never null, may be empty.
     */
    public List<MealDbIngredient> getIngredients() {
        // Build parallel arrays from the flat fields for clean iteration
        String[] names = {
                strIngredient1,  strIngredient2,  strIngredient3,  strIngredient4,
                strIngredient5,  strIngredient6,  strIngredient7,  strIngredient8,
                strIngredient9,  strIngredient10, strIngredient11, strIngredient12,
                strIngredient13, strIngredient14, strIngredient15, strIngredient16,
                strIngredient17, strIngredient18, strIngredient19, strIngredient20
        };
        String[] measures = {
                strMeasure1,  strMeasure2,  strMeasure3,  strMeasure4,
                strMeasure5,  strMeasure6,  strMeasure7,  strMeasure8,
                strMeasure9,  strMeasure10, strMeasure11, strMeasure12,
                strMeasure13, strMeasure14, strMeasure15, strMeasure16,
                strMeasure17, strMeasure18, strMeasure19, strMeasure20
        };

        List<MealDbIngredient> result = new ArrayList<>();
        for (int i = 0; i < names.length; i++) {
            String name = names[i];
            // Null ingredient means we've passed the end of the ingredient list
            if (name == null || name.trim().isEmpty()) continue;
            result.add(new MealDbIngredient(name.trim(), measures[i]));
        }
        return result;
    }

    /**
     * Parse strTags into a list of individual tag strings.
     * Returns an empty list if strTags is null or blank.
     *
     * Example: "Meat,Casserole" → ["Meat", "Casserole"]
     *
     * @return List of tag strings, trimmed. Never null.
     */
    public List<String> getParsedTags() {
        List<String> tags = new ArrayList<>();
        if (strTags == null || strTags.trim().isEmpty()) return tags;
        for (String tag : strTags.split(",")) {
            String trimmed = tag.trim();
            if (!trimmed.isEmpty()) tags.add(trimmed);
        }
        return tags;
    }

    // ========== VALIDATION ==========

    /**
     * True if this meal has the minimum required fields to be displayed.
     * A meal without an ID or name cannot be shown in the UI.
     */
    public boolean isValid() {
        return idMeal != null && !idMeal.trim().isEmpty()
                && strMeal != null && !strMeal.trim().isEmpty();
    }

    @Override
    public String toString() {
        return "MealDbMeal{id='" + idMeal + "', name='" + strMeal + "'}";
    }
}