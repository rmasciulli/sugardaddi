package li.masciul.sugardaddi.data.sources.thecocktaildb.api.dto;

import androidx.annotation.Nullable;
import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

/**
 * CocktailDbDrink — Gson DTO for a full drink object from TheCocktailDB API.
 *
 * Maps the response of:
 *   GET https://www.thecocktaildb.com/api/json/v1/1/lookup.php?i={id}
 *   GET https://www.thecocktaildb.com/api/json/v1/1/search.php?s={query}
 *
 * Both endpoints return the same full object structure inside a "drinks" array:
 * {
 *   "drinks": [ { ...CocktailDbDrink fields... } ]
 * }
 *
 * DIFFERENCES FROM THEMEALDB MealDbMeal
 * ======================================
 * - Root array key: "drinks" (not "meals")
 * - ID field: idDrink (not idMeal)
 * - Name field: strDrink (not strMeal)
 * - Thumbnail: strDrinkThumb (not strMealThumb)
 * - No strArea field — cocktails have no geographic origin concept
 * - Extra field: strAlcoholic ("Alcoholic" / "Non alcoholic" / "Optional alcohol")
 * - Extra field: strGlass (e.g. "Highball glass", "Cocktail glass")
 * - 15 ingredient slots (not 20)
 *
 * FLAT INGREDIENT SCHEMA:
 * TheCocktailDB does not use a nested array for ingredients. It uses 15 parallel
 * string fields (strIngredient1..15, strMeasure1..15). Empty slots are null or "".
 * Use getIngredients() to get a clean parsed list.
 *
 * NUTRITION DATA: None. TheCocktailDB v1 provides no nutritional values.
 *
 * LANGUAGE: English only. The free v1 API has no localisation support.
 *
 * @see CocktailDbSearchResponse — wraps this DTO in a "drinks" array
 * @see CocktailDbIngredient     — parsed ingredient+measure pair
 */
public class CocktailDbDrink {

    // ========== IDENTIFICATION ==========

    /** TheCocktailDB unique numeric ID for this drink (stored as String in the API). */
    @SerializedName("idDrink")
    @Nullable
    private String idDrink;

    /** Drink name in English. */
    @SerializedName("strDrink")
    @Nullable
    private String strDrink;

    /**
     * Alternate drink name (rare, usually null in v1).
     */
    @SerializedName("strDrinkAlternate")
    @Nullable
    private String strDrinkAlternate;

    // ========== CLASSIFICATION ==========

    /**
     * Category name. Single value, English.
     * Examples: "Cocktail", "Shot", "Punch / Party Drink", "Shake", "Beer", "Soft Drink"
     */
    @SerializedName("strCategory")
    @Nullable
    private String strCategory;

    /**
     * Alcoholic status. One of three values:
     *   "Alcoholic"        → tag "alcoholic"
     *   "Non alcoholic"    → tag "non_alcoholic"
     *   "Optional alcohol" → tag "optional_alcohol"
     *
     * Mapped to tags in TheCocktailDbMapper — see TheCocktailDbConstants for tag name constants.
     */
    @SerializedName("strAlcoholic")
    @Nullable
    private String strAlcoholic;

    /**
     * Glass type recommended for serving. English, free text.
     * Examples: "Highball glass", "Cocktail glass", "Old-fashioned glass", "Shot glass"
     * Stored as tag "glass:{lowercased_value}" by the mapper.
     */
    @SerializedName("strGlass")
    @Nullable
    private String strGlass;

    // ========== CONTENT ==========

    /**
     * Full preparation instructions as a plain text blob. English only.
     * May contain newlines. Formatting is inconsistent across recipes.
     */
    @SerializedName("strInstructions")
    @Nullable
    private String strInstructions;

    /**
     * Tags: freeform comma-separated string. Not structured.
     * Examples: "IBA,NewEra" — used alongside strAlcoholic and strGlass tags.
     */
    @SerializedName("strTags")
    @Nullable
    private String strTags;

    /**
     * CDN-hosted JPEG thumbnail URL.
     * Examples: "https://www.thecocktaildb.com/images/media/drink/...jpg"
     * Almost always present for published cocktails.
     */
    @SerializedName("strDrinkThumb")
    @Nullable
    private String strDrinkThumb;

    /**
     * YouTube video URL for preparation tutorial.
     * Not always present. When present, stored on Recipe.videoUrl and
     * a "has_video" tag is added by the mapper.
     */
    @SerializedName("strVideo")
    @Nullable
    private String strVideo;

    /**
     * Original source URL (external recipe page or blog post).
     * Rarely populated in v1. Not used in current implementation.
     */
    @SerializedName("strSource")
    @Nullable
    private String strSource;

    // ========== INGREDIENTS (flat schema, 15 slots) ==========

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

    // ========== MEASURES (flat schema, 15 slots) ==========

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

    // ========== CONSTRUCTOR ==========

    /** Default constructor required by Gson. */
    public CocktailDbDrink() {}

    // ========== ACCESSORS ==========

    @Nullable public String getIdDrink()          { return idDrink; }
    @Nullable public String getStrDrink()         { return strDrink; }
    @Nullable public String getStrDrinkAlternate(){ return strDrinkAlternate; }
    @Nullable public String getStrCategory()      { return strCategory; }
    @Nullable public String getStrAlcoholic()     { return strAlcoholic; }
    @Nullable public String getStrGlass()         { return strGlass; }
    @Nullable public String getStrInstructions()  { return strInstructions; }
    @Nullable public String getStrTags()          { return strTags; }
    @Nullable public String getStrDrinkThumb()    { return strDrinkThumb; }
    @Nullable public String getStrVideo()         { return strVideo; }
    @Nullable public String getStrSource()        { return strSource; }

    // ========== INGREDIENT PARSING ==========

    /**
     * Parse the 15 flat ingredient/measure fields into a clean list.
     *
     * Iteration rules:
     * - Skip any slot where the ingredient name is null or blank
     * - Treat a blank measure as null (no quantity specified)
     *
     * The returned list contains only real ingredients — no empty slots.
     *
     * @return List of this drink's ingredients. Never null, may be empty.
     */
    public List<CocktailDbIngredient> getIngredients() {
        String[] names = {
                strIngredient1,  strIngredient2,  strIngredient3,  strIngredient4,
                strIngredient5,  strIngredient6,  strIngredient7,  strIngredient8,
                strIngredient9,  strIngredient10, strIngredient11, strIngredient12,
                strIngredient13, strIngredient14, strIngredient15
        };
        String[] measures = {
                strMeasure1,  strMeasure2,  strMeasure3,  strMeasure4,
                strMeasure5,  strMeasure6,  strMeasure7,  strMeasure8,
                strMeasure9,  strMeasure10, strMeasure11, strMeasure12,
                strMeasure13, strMeasure14, strMeasure15
        };

        List<CocktailDbIngredient> result = new ArrayList<>();
        for (int i = 0; i < names.length; i++) {
            String name = names[i];
            if (name == null || name.trim().isEmpty()) continue;
            result.add(new CocktailDbIngredient(name.trim(), measures[i]));
        }
        return result;
    }

    /**
     * Parse strTags into a list of individual tag strings.
     * Returns an empty list if strTags is null or blank.
     *
     * Example: "IBA,NewEra" → ["IBA", "NewEra"]
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
     * True if this drink has the minimum required fields to be displayed.
     * A drink without an ID or name cannot be shown in the UI.
     */
    public boolean isValid() {
        return idDrink != null && !idDrink.trim().isEmpty()
                && strDrink != null && !strDrink.trim().isEmpty();
    }

    @Override
    public String toString() {
        return "CocktailDbDrink{id='" + idDrink + "', name='" + strDrink + "'}";
    }
}