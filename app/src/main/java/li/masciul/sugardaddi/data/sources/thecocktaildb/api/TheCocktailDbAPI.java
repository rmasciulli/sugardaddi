package li.masciul.sugardaddi.data.sources.thecocktaildb.api;

import li.masciul.sugardaddi.data.sources.thecocktaildb.TheCocktailDbConstants;
import li.masciul.sugardaddi.data.sources.thecocktaildb.api.dto.CocktailDbSearchResponse;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

/**
 * TheCocktailDbAPI — Retrofit interface for TheCocktailDB v1 REST API.
 *
 * BASE URL: https://www.thecocktaildb.com/api/json/v1/{key}/
 * (The "1" path segment IS the API key for the free development tier.)
 *
 * ENDPOINTS IMPLEMENTED
 * =====================
 *
 * 1. searchByName — search.php?s={query}
 *    Returns full drink objects matching the name query.
 *    Response envelope: { "drinks": [ ...CocktailDbDrink... ] }
 *    Returns { "drinks": null } when no results found — NOT an empty array.
 *    No pagination — all results returned in one response.
 *
 * 2. lookupById — lookup.php?i={id}
 *    Returns a single full drink object by TheCocktailDB numeric ID.
 *    Same response envelope as searchByName: { "drinks": [ ...CocktailDbDrink... ] }
 *    Returns { "drinks": null } if ID not found.
 *    Used for: fetching full detail when only idDrink is known.
 *
 * ENDPOINTS NOT IMPLEMENTED (Patreon-only or out of scope)
 * =========================================================
 * - filter.php?i={ingredient} — filter by ingredient (stripped response only)
 * - filter.php?c={category}   — filter by category
 * - filter.php?a={alcoholic}  — filter by alcoholic status
 * - filter.php?g={glass}      — filter by glass type
 * - random.php                — random cocktail
 *
 * Both implemented methods use {@link CocktailDbSearchResponse} as the response
 * type since both endpoints return the same { "drinks": [...] } envelope with
 * full {@link CocktailDbDrink} objects inside.
 */
public interface TheCocktailDbAPI {

    /**
     * Search for cocktails by name.
     *
     * Fires: GET search.php?s={query}
     *
     * Returns all drinks whose name contains the query string (case-insensitive,
     * server-side). No pagination — the full result set is returned at once.
     * Cap results in the data source to {@link TheCocktailDbConstants#MAX_SEARCH_RESULTS}.
     *
     * Empty result: { "drinks": null } — handled defensively by
     * {@link CocktailDbSearchResponse#getDrinks()}.
     *
     * @param query Cocktail name search string. Must not be null or blank.
     *              Minimum useful length: {@link TheCocktailDbConstants#MIN_QUERY_LENGTH}.
     * @return Call wrapping the CocktailDbSearchResponse envelope.
     */
    @GET("search.php")
    Call<CocktailDbSearchResponse> searchByName(
            @Query(TheCocktailDbConstants.PARAM_SEARCH) String query
    );

    /**
     * Look up a cocktail's full detail by its TheCocktailDB numeric ID.
     *
     * Fires: GET lookup.php?i={id}
     *
     * Returns a single full {@link CocktailDbDrink} inside the standard envelope.
     * Returns { "drinks": null } if the ID does not exist.
     *
     * @param id TheCocktailDB numeric ID string (e.g. "11007").
     * @return Call wrapping the CocktailDbSearchResponse envelope.
     */
    @GET("lookup.php")
    Call<CocktailDbSearchResponse> lookupById(
            @Query(TheCocktailDbConstants.PARAM_ID) String id
    );
}