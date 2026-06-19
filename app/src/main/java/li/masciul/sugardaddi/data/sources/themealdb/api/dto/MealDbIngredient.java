package li.masciul.sugardaddi.data.sources.themealdb.api.dto;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * MealDbIngredient - A single ingredient extracted from TheMealDB's flat field schema.
 *
 * TheMealDB does not return ingredients as a JSON array. Instead the API uses
 * 20 parallel string fields on the meal object:
 *
 *   strIngredient1 / strMeasure1
 *   strIngredient2 / strMeasure2
 *   ...
 *   strIngredient20 / strMeasure20
 *
 * Empty or null slots indicate the recipe has fewer than 20 ingredients.
 * {@link MealDbMeal} holds these raw fields; {@link MealDbMeal#getIngredients()}
 * iterates them and produces a clean list of MealDbIngredient objects,
 * skipping all empty/null pairs.
 *
 * IMPORTANT: Both fields are plain English strings - no numeric quantities,
 * no standardised units. Parsing "3/4 cup" or "1 tbsp" into grams requires
 * a unit conversion layer that is OUT OF SCOPE for this integration phase.
 *
 * Examples from the API:
 *   name="soy sauce"    measure="3/4 cup"
 *   name="sesame oil"   measure="1 teaspoon"
 *   name="cornstarch"   measure="4 tablespoons"
 *   name="chicken"      measure="1 (3 pound)"
 */
public class MealDbIngredient {

    /** Ingredient name as returned by the API (English, free text). Never null or blank. */
    @NonNull
    private final String name;

    /**
     * Measure/quantity as returned by the API (English, free text).
     * Examples: "3/4 cup", "1 tbsp", "to taste", "1 (12 oz.)", "pinch"
     * May be null or empty when the API provides an ingredient with no measure.
     */
    @Nullable
    private final String measure;

    /**
     * Constructor. Called by {@link MealDbMeal#getIngredients()} only.
     *
     * @param name    Ingredient name - must not be null or blank
     * @param measure Measure string - may be null or empty
     */
    public MealDbIngredient(@NonNull String name, @Nullable String measure) {
        this.name = name;
        // Treat blank measure strings as null for cleaner downstream handling
        this.measure = (measure != null && !measure.trim().isEmpty()) ? measure.trim() : null;
    }

    // ========== ACCESSORS ==========

    /** @return Ingredient name, trimmed. Never null. */
    @NonNull
    public String getName() {
        return name;
    }

    /**
     * @return Measure string (e.g. "3/4 cup"), or null if none was provided.
     *         Null here means "unspecified quantity" - treat as "to taste" in the UI.
     */
    @Nullable
    public String getMeasure() {
        return measure;
    }

    /**
     * True if this ingredient has a non-null, non-empty measure string.
     * Use this to decide whether to show the measure column in a recipe layout.
     */
    public boolean hasMeasure() {
        return measure != null;
    }

    /**
     * Display string combining name and measure, suitable for a single-line list item.
     * Examples:
     *   "soy sauce - 3/4 cup"
     *   "salt"          (no measure)
     *
     * @return Formatted display string
     */
    @NonNull
    public String toDisplayString() {
        if (measure != null) {
            return name + " - " + measure;
        }
        return name;
    }

    @Override
    public String toString() {
        return "MealDbIngredient{name='" + name + "', measure='" + measure + "'}";
    }
}