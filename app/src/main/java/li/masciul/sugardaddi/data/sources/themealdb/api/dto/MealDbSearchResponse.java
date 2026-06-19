package li.masciul.sugardaddi.data.sources.themealdb.api.dto;

import androidx.annotation.Nullable;
import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

/**
 * MealDbSearchResponse - Gson DTO for all TheMealDB list responses.
 *
 * Used by:
 *   GET /search.php?s={query}      - search by name
 *   GET /lookup.php?i={id}         - detail by ID
 *   GET /filter.php?c={category}   - filter by category (returns MealDbFilterMeal, not full meals)
 *   GET /filter.php?i={ingredient} - filter by ingredient
 *   GET /filter.php?a={area}       - filter by area
 *
 * NOTE: search.php and lookup.php return FULL MealDbMeal objects in the meals array.
 * filter.php returns stripped objects (idMeal, strMeal, strMealThumb only).
 * Both reuse this response envelope. The mapper decides which DTO to use
 * based on which endpoint was called.
 *
 * IMPORTANT - NULL WHEN EMPTY:
 * TheMealDB returns { "meals": null } (not []) when a search returns no results.
 * getMeals() handles this defensively and always returns a non-null list.
 */
public class MealDbSearchResponse {

    /**
     * List of meal objects. NULL when no results found - not an empty array.
     * Always use getMeals() rather than accessing this field directly.
     */
    @SerializedName("meals")
    @Nullable
    private List<MealDbMeal> meals;

    /** Default constructor required by Gson. */
    public MealDbSearchResponse() {}

    // ========== ACCESSORS ==========

    /**
     * Get the list of meals.
     * Returns an empty list when the API returned null (no results found).
     *
     * @return List of meals. Never null, may be empty.
     */
    public List<MealDbMeal> getMeals() {
        return meals != null ? meals : new ArrayList<>();
    }

    /** @return True if at least one meal was returned. */
    public boolean hasResults() {
        return meals != null && !meals.isEmpty();
    }

    /** @return Number of meals returned. */
    public int getCount() {
        return meals != null ? meals.size() : 0;
    }

    @Override
    public String toString() {
        return "MealDbSearchResponse{count=" + getCount() + "}";
    }
}