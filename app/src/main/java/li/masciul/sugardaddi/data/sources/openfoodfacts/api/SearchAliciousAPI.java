package li.masciul.sugardaddi.data.sources.openfoodfacts.api;

import li.masciul.sugardaddi.data.sources.openfoodfacts.api.dto.SearchAliciousResponse;
import li.masciul.sugardaddi.data.sources.openfoodfacts.api.dto.AutocompleteResponse;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

/**
 * SearchAliciousAPI - Retrofit interface for search-a-licious API
 *
 * WHAT IS SEARCH-A-LICIOUS?
 * Official OpenFoodFacts search solution built on Elasticsearch, providing:
 * - Fast full-text search with relevance scoring
 * - Advanced filtering using Lucene query syntax
 * - Autocomplete and typeahead functionality
 * - Faceted navigation (brands, categories, nutrition)
 * - Real-time index updates
 *
 * API DOCUMENTATION:
 * - Base URL: https://search.openfoodfacts.org/
 * - Docs: https://search.openfoodfacts.org/docs
 * - OpenAPI: https://search.openfoodfacts.org/openapi.json
 * - Version: v1.4 (stable)
 *
 * KEY FEATURES:
 * - Language-aware search (langs parameter)
 * - Field selection (fields parameter for performance)
 * - Pagination (page, page_size)
 * - Relevance scoring (_score field)
 * - Quality filtering (completeness, nutrition_grades)
 *
 * USAGE WITH OpenFoodFactsDataSource:
 * ```java
 * OpenFoodFactsConfig config = new OpenFoodFactsConfig();
 * Retrofit retrofit = new Retrofit.Builder()
 *     .baseUrl(config.getSearchBaseUrl())  // search.openfoodfacts.org
 *     .addConverterFactory(GsonConverterFactory.create())
 *     .client(NetworkClient.createHttpClient(config, context))
 *     .build();
 * SearchAliciousAPI api = retrofit.create(SearchAliciousAPI.class);
 * ```
 *
 * @author SugarDaddi Team
 * @version 2.0 (Search-a-licious Integration)
 */
public interface SearchAliciousAPI {

    // ========== SEARCH ENDPOINT ==========

    /**
     * Search for food products using Elasticsearch
     *
     * LUCENE QUERY SYNTAX:
     * The "q" parameter supports powerful Lucene queries:
     * - Simple: "chocolate"
     * - Multiple words: "dark chocolate" (searches both words)
     * - AND operator: "chocolate AND milk"
     * - OR operator: "milk OR cream"
     * - Field search: "brands:milka"
     * - Range filter: "completeness:[0.5 TO 1.0]"
     * - Phrase: "\"dark chocolate\""
     * - Combined: "brands:milka AND completeness:[0.7 TO 1.0]"
     *
     * LANGUAGE HANDLING:
     * - "langs" parameter: Comma-separated language codes (e.g., "en,fr")
     * - Affects field selection for product_name_xx, agribalyse.name_xx
     * - Prioritizes first language, falls back to subsequent languages
     *
     * FIELD SELECTION (IMPORTANT):
     * - Always provide "fields" parameter for optimal performance
     * - Use SearchAliciousConstants field subsets (SEARCH_RESULTS_FIELDS, etc.)
     * - Reduces response size and improves speed
     * - Without fields, returns ALL document fields (slow, large response)
     *
     * PAGINATION:
     * - page: 1-based page number (page=1 for first page)
     * - page_size: Results per page (max 100, default 10)
     * - Response includes: page_count (total pages), count (total results)
     *
     * QUALITY FILTERING:
     * Best practice: Include completeness filter in query
     * ```java
     * String query = QueryBuilder.withQualityFilter("chocolate", 0.5);
     * // Returns: "(chocolate) AND completeness:[0.5 TO 1.0]"
     * ```
     *
     * RESPONSE STRUCTURE:
     * ```json
     * {
     *   "hits": [...],           // Array of products (SearchAliciousHit)
     *   "count": 1234,           // Total matching products
     *   "page": 1,               // Current page
     *   "page_size": 20,         // Results per page
     *   "page_count": 62,        // Total pages
     *   "is_count_exact": true,  // Whether count is exact
     *   "took": 45,              // Query time in ms
     *   "timed_out": false,      // Whether query timed out
     *   "warnings": [...]        // Optional warnings array
     * }
     * ```
     *
     * @param query Search query with optional Lucene filters (required)
     * @param langs Comma-separated language codes (e.g., "en,fr")
     * @param pageSize Number of results per page (1-100)
     * @param page Page number starting from 1
     * @param fields Comma-separated field list (use Constants.SEARCH_RESULTS_FIELDS)
     * @return Call containing SearchAliciousResponse with hits and metadata
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

    // ========== AUTOCOMPLETE ENDPOINT ==========

    /**
     * Get autocomplete suggestions for taxonomy terms
     *
     * WHAT IT DOES:
     * Provides fast typeahead suggestions from OpenFoodFacts taxonomies:
     * - Categories (e.g., "bev" → "beverages", "beverage mixes")
     * - Brands (e.g., "mil" → "milka", "milbona")
     * - Ingredients (e.g., "cho" → "chocolate", "chocolate chips")
     *
     * TAXONOMY NAMES:
     * - "category": Product categories
     * - "brand": Brand names
     * - "ingredient": Ingredient names
     * - Multiple: "category,brand" (comma-separated)
     *
     * LANGUAGE HANDLING:
     * - "lang" parameter: Single language code (e.g., "en")
     * - Returns suggestions in specified language when available
     * - Falls back to other languages if not available
     *
     * FUZZINESS:
     * - null or 0: Exact prefix matching only
     * - 1: Allow 1 character difference (typo tolerance)
     * - 2: Allow 2 character differences (more forgiving)
     *
     * TYPICAL USE CASE:
     * ```java
     * // User types "choc" in search box
     * autocomplete("choc", "category,brand", "en", 5, null)
     * // Returns: ["chocolate", "chocolate bars", "chocolates", "choco", "chocolatine"]
     * ```
     *
     * RESPONSE:
     * Returns array of suggestion objects with:
     * - Matched term
     * - Taxonomy name
     * - Language
     *
     * @param query User's partial input (e.g., "choc", "mil")
     * @param taxonomyNames Comma-separated taxonomy names (e.g., "category,brand")
     * @param lang Language code for suggestions (e.g., "en")
     * @param size Maximum number of suggestions to return (default 10)
     * @param fuzziness Typo tolerance level (null=none, 1=1 char, 2=2 chars)
     * @return Call containing AutocompleteResponse with suggestion array
     */
    @GET("autocomplete")
    Call<AutocompleteResponse> autocomplete(
            @Query("q") String query,
            @Query("taxonomy_names") String taxonomyNames,
            @Query("lang") String lang,
            @Query("size") Integer size,
            @Query("fuzziness") Integer fuzziness
    );

    // ========== DOCUMENT ENDPOINT (OPTIONAL - for future use) ==========

    /**
     * Get a single product document by barcode
     *
     * NOTE: This is optional - we can use OFF v2 API for product details.
     * Included here for completeness, but OpenFoodFactsDataSource should use
     * OpenFoodFactsAPI.getProduct() for detailed product data.
     *
     * @param identifier Product barcode/code
     * @return Call containing single product document
     */
    // @GET("document/{identifier}")
    // Call<JsonObject> getDocument(@Path("identifier") String identifier);
}