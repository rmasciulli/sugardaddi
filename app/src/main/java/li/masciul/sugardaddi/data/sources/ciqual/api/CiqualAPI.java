package li.masciul.sugardaddi.data.sources.ciqual.api;

import li.masciul.sugardaddi.data.sources.ciqual.api.dto.CiqualElasticsearchResponse;

import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Headers;
import retrofit2.http.POST;

/**
 * CiqualAPI - Retrofit interface for Ciqual Elasticsearch endpoint
 *
 * CLEAN IMPLEMENTATION - Elasticsearch Only
 * ==========================================
 * This interface provides access to the ONLY working Ciqual API endpoint:
 * the Elasticsearch search endpoint discovered through reverse-engineering.
 *
 * ENDPOINT: POST /esearch/aliments/_search
 * METHOD: POST with raw JSON body
 * CONTENT-TYPE: application/json
 *
 * WHY ONLY ONE METHOD?
 * ====================
 * The old "official" Ciqual REST API (cms/api/food/*) is:
 * - Dead (connection timeouts)
 * - Undocumented (no official docs exist)
 * - Unverified (no evidence it ever worked)
 * - Replaced by Elasticsearch (works perfectly)
 *
 * ELASTICSEARCH ENDPOINT USES:
 * ============================
 * This single endpoint handles ALL operations:
 * 1. Text search (with relevance scoring)
 * 2. Product lookup (by Ciqual code via match_phrase)
 * 3. Autocomplete (via prefix query)
 * 4. Category filtering (via bool queries)
 *
 * QUERY BUILDING:
 * ===============
 * Use CiqualSearchRequest helper class to build JSON request bodies:
 * - CiqualSearchRequest.buildSearchQuery() → Text search
 * - CiqualSearchRequest.buildProductQuery() → Product details
 * - CiqualSearchRequest.buildAutocompleteQuery() → Autocomplete
 *
 * USAGE EXAMPLE:
 * ==============
 * ```java
 * // Text search
 * String json = CiqualSearchRequest.buildSearchQuery("fraise", 0, 10);
 * RequestBody body = RequestBody.create(MediaType.parse("application/json"), json);
 * Call<CiqualElasticsearchResponse> call = api.search(body);
 *
 * // Product lookup
 * String json = CiqualSearchRequest.buildProductQuery("13014");
 * RequestBody body = RequestBody.create(MediaType.parse("application/json"), json);
 * Call<CiqualElasticsearchResponse> call = api.search(body);
 * ```
 *
 * ADVANTAGES:
 * ===========
 * - Fast (1-10ms response time)
 * - Relevance scoring for better results
 * - Flexible query DSL
 * - No authentication required
 * - Confirmed working in production
 * - Single endpoint = simpler API surface
 *
 * @see CiqualSearchRequest
 * @see CiqualElasticsearchResponse
 * @author SugarDaddi Team
 * @version 2.0 (Clean Elasticsearch Implementation)
 */
public interface CiqualAPI {

    /**
     * Search Ciqual database using Elasticsearch
     *
     * UNIVERSAL ENDPOINT:
     * This single endpoint handles all Ciqual operations by accepting
     * different Elasticsearch query JSON structures.
     *
     * SUPPORTED OPERATIONS:
     * ---------------------
     * 1. TEXT SEARCH: Full-text search with relevance scoring
     *    - Build with: CiqualSearchRequest.buildSearchQuery()
     *    - Returns: Multiple results sorted by relevance
     *
     * 2. PRODUCT LOOKUP: Get specific product by Ciqual code
     *    - Build with: CiqualSearchRequest.buildProductQuery()
     *    - Returns: Single result (exact match)
     *
     * 3. AUTOCOMPLETE: Prefix matching for suggestions
     *    - Build with: CiqualSearchRequest.buildAutocompleteQuery()
     *    - Returns: Few results (typically 5-10)
     *
     * QUERY FORMAT:
     * -------------
     * The request body is a raw JSON string following Elasticsearch Query DSL:
     * ```json
     * {
     *   "from": 0,
     *   "size": 10,
     *   "query": {
     *     "bool": {
     *       "must": [...],
     *       "should": [...]
     *     }
     *   }
     * }
     * ```
     *
     * RESPONSE FORMAT:
     * ----------------
     * Standard Elasticsearch response with hits array:
     * ```json
     * {
     *   "took": 2,
     *   "hits": {
     *     "total": 9,
     *     "hits": [
     *       {
     *         "_score": 16.87,
     *         "_source": {
     *           "code": "13014",
     *           "nomFr": "Fraise,crue",
     *           ...
     *         }
     *       }
     *     ]
     *   }
     * }
     * ```
     *
     * ERROR HANDLING:
     * ---------------
     * - 200 OK: Success with results (or empty results)
     * - 400 Bad Request: Invalid JSON query
     * - 500 Internal Server Error: Elasticsearch error
     *
     * PERFORMANCE:
     * ------------
     * - Typical response time: 1-10ms
     * - Handles concurrent requests
     * - No rate limiting observed
     *
     * @param requestBody Raw JSON request body (Elasticsearch query)
     * @return Call containing Elasticsearch response with hits
     * @see CiqualSearchRequest#buildSearchQuery(String, int, int)
     * @see CiqualSearchRequest#buildProductQuery(String)
     * @see CiqualSearchRequest#buildAutocompleteQuery(String, int)
     * @see CiqualElasticsearchResponse
     */
    @POST("esearch/aliments/_search")
    @Headers("Content-Type: application/json")
    Call<CiqualElasticsearchResponse> search(@Body RequestBody requestBody);
}