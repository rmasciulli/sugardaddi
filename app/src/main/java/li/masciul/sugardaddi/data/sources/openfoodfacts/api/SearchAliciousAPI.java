package li.masciul.sugardaddi.data.sources.openfoodfacts.api;

import li.masciul.sugardaddi.data.sources.openfoodfacts.api.dto.AutocompleteResponse;
import li.masciul.sugardaddi.data.sources.openfoodfacts.api.dto.SearchAliciousResponse;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

/**
 * SearchAliciousAPI - Retrofit interface for the search-a-licious API
 *
 * WHAT IS SEARCH-A-LICIOUS?
 * OpenFoodFacts' official search backend, built on Elasticsearch.
 * Base URL: https://search.openfoodfacts.org/
 * Docs:     https://search.openfoodfacts.org/docs
 *
 * TWO ENDPOINTS
 * =============
 * 1. /search  — full-text product search returning SearchAliciousResponse (product hits)
 *    Used by: OpenFoodFactsDataSource.search() and OpenFoodFactsDataSource.autocomplete()
 *
 * 2. /autocomplete — taxonomy suggestions (categories, brands, ingredients)
 *    Returns: AutocompleteResponse (option list, NOT products)
 *    Used by: NOT YET CALLED — infrastructure is ready, see note below.
 *
 * AUTOCOMPLETE STRATEGY
 * =====================
 * OpenFoodFactsDataSource.autocomplete() currently uses the /search endpoint
 * with AUTOCOMPLETE_FIELDS and a small page_size. This mirrors how
 * CiqualDataSource.autocomplete() reuses the Elasticsearch /search endpoint
 * with a match_phrase_prefix query — one consistent pattern across both sources.
 *
 * The /autocomplete endpoint (taxonomy) will be wired in a future iteration
 * to surface "Chocolates (category)" and "Milka (brand)" suggestions alongside
 * product-name suggestions. The DTO is ready; only the DataSource call is pending.
 *
 * @version 2.1 — autocomplete endpoint declared with correct DTO
 */
public interface SearchAliciousAPI {

    // ========== SEARCH ENDPOINT ==========

    /**
     * Full-text product search via Elasticsearch.
     *
     * LUCENE QUERY SYNTAX (q parameter):
     * - Simple:   "chocolate"
     * - AND:      "chocolate AND milk"
     * - Field:    "brands:milka"
     * - Range:    "completeness:[0.5 TO 1.0]"
     * - Phrase:   "\"dark chocolate\""
     *
     * LANGUAGE HANDLING:
     * "langs" is comma-separated (e.g., "en,fr"). The API prioritises the first
     * language for product_name_xx fields and falls back to subsequent ones.
     *
     * FIELD SELECTION:
     * Always pass the "fields" parameter — omitting it returns the full document
     * (slow and large). Use SearchAliciousConstants field subsets.
     *
     * RESPONSE STRUCTURE:
     * {
     *   "hits":         [...],   // SearchAliciousHit array
     *   "count":        1234,    // Total matching products
     *   "page":         1,
     *   "page_size":    20,
     *   "page_count":   62,
     *   "is_count_exact": true,
     *   "took":         45,      // ms
     *   "timed_out":    false
     * }
     *
     * @param query     Lucene query string (required)
     * @param langs     Comma-separated language codes, e.g. "en,fr"
     * @param pageSize  Results per page (1–100)
     * @param page      Page number, 1-based
     * @param fields    Comma-separated field list (use SearchAliciousConstants)
     * @param sortBy    Sort field (use SearchAliciousConstants.SortBy)
     * @return Call wrapping SearchAliciousResponse
     */
    @GET("search")
    Call<SearchAliciousResponse> search(
            @Query("q") String query,
            @Query("langs") String langs,
            @Query("page_size") int pageSize,
            @Query("page") int page,
            @Query("fields") String fields,
            @Query("sort_by") String sortBy
    );

    // ========== AUTOCOMPLETE (TAXONOMY) ENDPOINT ==========

    /**
     * Taxonomy autocomplete — returns category/brand/ingredient suggestions,
     * NOT products.
     *
     * EXAMPLE REQUEST:
     *   GET /autocomplete?q=choc&taxonomy_names=category,brand&lang=en&size=5
     *
     * EXAMPLE RESPONSE:
     * {
     *   "options": [
     *     {"id": "en:chocolates",    "text": "Chocolates",    "taxonomy_name": "category"},
     *     {"id": "en:chocolate-bars","text": "Chocolate bars","taxonomy_name": "category"},
     *     {"id": "milka",            "text": "Milka",          "taxonomy_name": "brand"}
     *   ]
     * }
     *
     * TAXONOMY NAMES (taxonomy_names parameter):
     * - "category"    — food categories from OFF taxonomy
     * - "brand"       — brand names
     * - "ingredient"  — ingredient names
     * - Multiple:     "category,brand" (comma-separated)
     *
     * FUZZINESS:
     * - null / omit — exact prefix matching only
     * - 1           — allow 1 character difference (typo tolerance)
     * - 2           — allow 2 character differences
     *
     * NOTE: This endpoint is declared and the DTO is ready.
     * OpenFoodFactsDataSource does NOT call this yet — product-name autocomplete
     * is handled via the /search endpoint for consistency with Ciqual.
     * Wire this when taxonomy suggestions ("Chocolates (category)") are added to the UI.
     *
     * @param query         Partial user input, e.g. "choc"
     * @param taxonomyNames Comma-separated taxonomy names, e.g. "category,brand"
     * @param lang          Single language code, e.g. "en"
     * @param size          Max suggestions (default 10)
     * @param fuzziness     Typo tolerance: null = none, 1 = 1 char, 2 = 2 chars
     * @return Call wrapping AutocompleteResponse (taxonomy options, not products)
     */
    @GET("autocomplete")
    Call<AutocompleteResponse> autocomplete(
            @Query("q") String query,
            @Query("taxonomy_names") String taxonomyNames,
            @Query("lang") String lang,
            @Query("size") Integer size,
            @Query("fuzziness") Integer fuzziness
    );
}