package li.masciul.sugardaddi.data.sources.thecocktaildb.api.dto;

import androidx.annotation.Nullable;
import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

/**
 * CocktailDbSearchResponse — Gson DTO for all TheCocktailDB list responses.
 *
 * Used by:
 *   GET /search.php?s={query}  — search by name
 *   GET /lookup.php?i={id}     — detail by ID
 *
 * Both endpoints return full {@link CocktailDbDrink} objects inside a "drinks" array.
 *
 * IMPORTANT — NULL WHEN EMPTY:
 * TheCocktailDB returns { "drinks": null } (not []) when a search returns no results.
 * getDrinks() handles this defensively and always returns a non-null list.
 *
 * This is the direct parallel of MealDbSearchResponse, with "drinks" replacing "meals".
 */
public class CocktailDbSearchResponse {

    /**
     * List of drink objects. NULL when no results found — not an empty array.
     * Always use getDrinks() rather than accessing this field directly.
     */
    @SerializedName("drinks")
    @Nullable
    private List<CocktailDbDrink> drinks;

    /** Default constructor required by Gson. */
    public CocktailDbSearchResponse() {}

    // ========== ACCESSORS ==========

    /**
     * Get the list of drinks.
     * Returns an empty list when the API returned null (no results found).
     *
     * @return List of drinks. Never null, may be empty.
     */
    public List<CocktailDbDrink> getDrinks() {
        return drinks != null ? drinks : new ArrayList<>();
    }

    /** @return True if at least one drink was returned. */
    public boolean hasResults() {
        return drinks != null && !drinks.isEmpty();
    }

    /** @return Number of drinks returned. */
    public int getCount() {
        return drinks != null ? drinks.size() : 0;
    }

    @Override
    public String toString() {
        return "CocktailDbSearchResponse{count=" + getCount() + "}";
    }
}