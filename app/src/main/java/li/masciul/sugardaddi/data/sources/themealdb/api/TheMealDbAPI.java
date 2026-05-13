package li.masciul.sugardaddi.data.sources.themealdb.api;

import li.masciul.sugardaddi.data.sources.themealdb.TheMealDbConstants;
import li.masciul.sugardaddi.data.sources.themealdb.api.dto.MealDbSearchResponse;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

/**
 * TheMealDbAPI — Retrofit interface for TheMealDB v1 REST API.
 *
 * BASE URL: https://www.themealdb.com/api/json/v1/1/
 * (The "1" path segment IS the API key for the free development tier.)
 *
 * ENDPOINTS IMPLEMENTED
 * =====================
 *
 * 1. searchByName — search.php?s={query}
 *    Returns full meal objects matching the name query.
 *    Response envelope: { "meals": [ ...MealDbMeal... ] }
 *    Returns { "meals": null } when no results found — NOT an empty array.
 *    No pagination — all results returned in one response.
 *
 * 2. lookupById — lookup.php?i={id}
 *    Returns a single full meal object by TheMealDB numeric ID.
 *    Same response envelope as searchByName: { "meals": [ ...MealDbMeal... ] }
 *    Returns { "meals": null } if ID not found.
 *    Used for: fetching full detail when only idMeal is known.
 *
 * ENDPOINTS NOT IMPLEMENTED (v2 / Patreon only, or out of scope)
 * ==============================================================
 * - filter.php?i={ingredient} — filter by ingredient (stripped response only)
 * - filter.php?c={category}   — filter by category
 * - filter.php?a={area}       — filter by area
 * - categories.php            — list all categories
 * - list.php?a=list           — list all areas
 * - random.php                — random meal
 *
 * Both implemented methods reuse {@link MealDbSearchResponse} as the response
 * type since both endpoints return the same { "meals": [...] } envelope with
 * full {@link li.masciul.sugardaddi.data.sources.themealdb.api.dto.MealDbMeal}
 * objects inside.
 */
public interface TheMealDbAPI {

    /**
     * Search for meals by name.
     *
     * Fires: GET search.php?s={query}
     *
     * Returns all meals whose name contains the query string (case-insensitive,
     * server-side). No pagination — the full result set is returned at once.
     * Cap results in the data source to {@link TheMealDbConstants#MAX_SEARCH_RESULTS}.
     *
     * Empty result: { "meals": null } — handled defensively by
     * {@link MealDbSearchResponse#getMeals()}.
     *
     * @param query Meal name search string. Must not be null or blank.
     *              Minimum useful length: {@link TheMealDbConstants#MIN_QUERY_LENGTH}.
     * @return Call wrapping the MealDbSearchResponse envelope.
     */
    @GET("search.php")
    Call<MealDbSearchResponse> searchByName(
            @Query(TheMealDbConstants.PARAM_SEARCH) String query
    );

    /**
     * Look up a meal's full detail by its TheMealDB numeric ID.
     *
     * Fires: GET lookup.php?i={id}
     *
     * Returns a single meal in the standard { "meals": [ ... ] } envelope,
     * or { "meals": null } if the ID is not found.
     *
     * Use this when a search result summary needs to be enriched with full
     * ingredient/instruction data — for example, after a filter.php call
     * (not currently implemented) which returns stripped objects only.
     *
     * For our current implementation, search.php already returns full objects,
     * so lookupById is used primarily for the detail screen when navigating
     * directly to a recipe by ID.
     *
     * @param id TheMealDB numeric ID string (e.g. "52772"). Must not be null.
     * @return Call wrapping the MealDbSearchResponse envelope.
     */
    @GET("lookup.php")
    Call<MealDbSearchResponse> lookupById(
            @Query(TheMealDbConstants.PARAM_ID) String id
    );
}