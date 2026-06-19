package li.masciul.sugardaddi.data.sources.thecocktaildb.api.dto;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * CocktailDbIngredient - A single ingredient extracted from TheCocktailDB's flat field schema.
 *
 * TheCocktailDB does not return ingredients as a JSON array. Instead the API uses
 * 15 parallel string fields on the drink object:
 *
 *   strIngredient1 / strMeasure1
 *   strIngredient2 / strMeasure2
 *   ...
 *   strIngredient15 / strMeasure15
 *
 * Note: TheMealDB uses 20 slots; TheCocktailDB uses 15. This is a deliberate
 * difference in the API and is handled independently in this codebase.
 *
 * Empty or null slots indicate the recipe has fewer than 15 ingredients.
 * {@link CocktailDbDrink#getIngredients()} produces a clean list of these objects,
 * skipping all empty/null pairs.
 *
 * Examples from the API:
 *   name="Gin"          measure="1 3/4 shot"
 *   name="Lemon juice"  measure="1 shot"
 *   name="Grenadine"    measure="1/4 shot"
 *   name="Sugar"        measure="1 tsp"
 */
public class CocktailDbIngredient {

    /** Ingredient name as returned by the API (English, free text). Never null or blank. */
    @NonNull
    private final String name;

    /**
     * Measure/quantity as returned by the API (English, free text).
     * Examples: "1 3/4 shot", "1 tbsp", "to taste", "pinch"
     * May be null or empty when the API provides an ingredient with no measure.
     */
    @Nullable
    private final String measure;

    /**
     * Constructor. Called by {@link CocktailDbDrink#getIngredients()} only.
     *
     * @param name    Ingredient name - must not be null or blank
     * @param measure Measure string - may be null or empty
     */
    public CocktailDbIngredient(@NonNull String name, @Nullable String measure) {
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
     * @return Measure string (e.g. "1 3/4 shot"), or null if none was provided.
     *         Null means "unspecified quantity" - treat as "to taste" in the UI.
     */
    @Nullable
    public String getMeasure() {
        return measure;
    }

    /**
     * True if this ingredient has a non-null, non-empty measure string.
     */
    public boolean hasMeasure() {
        return measure != null;
    }

    @Override
    public String toString() {
        return "CocktailDbIngredient{name='" + name + "', measure='" + measure + "'}";
    }
}