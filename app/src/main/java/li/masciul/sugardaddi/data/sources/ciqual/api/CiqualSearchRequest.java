package li.masciul.sugardaddi.data.sources.ciqual.api;

import androidx.annotation.NonNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import android.util.Log;

import java.util.Locale;

/**
 * CiqualSearchRequest - Builds Elasticsearch query JSON for Ciqual API
 *
 * LANGUAGE SUPPORT:
 * - English: Uses nomIndexEng, nomEng, nomSortEng fields
 * - French: Uses nomIndexFr, nomFr, nomSortFr fields
 *
 * QUERY TYPES:
 * 1. SEARCH: Full-text search with relevance scoring
 * 2. PRODUCT: Lookup by Ciqual code (exact match)
 *
 * USAGE:
 * ```java
 * // Search in English
 * String json = CiqualSearchRequest.buildSearchQuery("potato", "en", 0, 10);
 *
 * // Search in French
 * String json = CiqualSearchRequest.buildSearchQuery("pomme de terre", "fr", 0, 10);
 *
 * // Get product by code
 * String json = CiqualSearchRequest.buildProductQuery("4090", "en");
 * ```
 *
 * @author SugarDaddi Team
 * @version 2.1 - Fixed double query wrapper bug
 */
public final class CiqualSearchRequest {

    private static final String TAG = "CiqualSearchRequest";

    // ========== CONSTANTS ==========

    private static final int MAX_RESULTS = 100;
    private static final int PREFIX_BOOST = 2;
    private static final int INDEXED_NAME_BOOST = 2;

    // Private constructor - utility class
    private CiqualSearchRequest() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    // ========== PUBLIC API ==========

    /**
     * Builds a language-aware search query
     *
     * Searches the appropriate fields based on language:
     * - English: nomIndexEng (boosted 2x, searchable)
     * - French: nomIndexFr (boosted 2x, searchable)
     *
     * NOTE: nomEng/nomFr are display-only fields (not indexed for search)
     *
     * @param query Search query (e.g., "potato", "pomme de terre")
     * @param language Language code ("en" or "fr")
     * @param from Pagination offset (0-based)
     * @param size Number of results (max 100)
     * @return JSON string for Elasticsearch request
     */
    @NonNull
    public static String buildSearchQuery(@NonNull String query, @NonNull String language,
                                          int from, int size) {
        try {
            String effectiveLanguage = normalizeLanguage(language);

            JSONObject root = new JSONObject();

            // Pagination
            root.put("from", Math.max(0, from));
            root.put("size", Math.min(size, MAX_RESULTS));

            // Build query with bool
            JSONObject boolQuery = createBoolQuery(query, effectiveLanguage);
            JSONObject queryWrapper = new JSONObject();
            queryWrapper.put("bool", boolQuery);
            root.put("query", queryWrapper);

            // Source exclusions
            root.put("_source", createSourceExclusions());

            String result = root.toString();
            Log.d(TAG, "Built search query: " + result);
            return result;

        } catch (JSONException e) {
            Log.e(TAG, "Failed to build search query", e);
            return buildFallbackQuery(query, language, from, size);
        }
    }

    /**
     * Builds a product lookup query by Ciqual code
     *
     * @param code Ciqual food code (e.g., "4090")
     * @param language Language for sorting ("en" or "fr")
     * @return JSON string for Elasticsearch request
     */
    @NonNull
    public static String buildProductQuery(@NonNull String code, @NonNull String language) {
        try {
            String effectiveLanguage = normalizeLanguage(language);

            JSONObject root = new JSONObject();
            root.put("from", 0);
            root.put("size", 1);

            // Exact match on code field
            JSONObject matchPhrase = new JSONObject();
            JSONObject codeQuery = new JSONObject();
            codeQuery.put("query", code.trim());
            matchPhrase.put("code", codeQuery);

            JSONObject queryWrapper = new JSONObject();
            queryWrapper.put("match_phrase", matchPhrase);
            root.put("query", queryWrapper);

            // Sort by language-appropriate name
            root.put("sort", getSortField(effectiveLanguage));

            String result = root.toString();
            Log.d(TAG, "Built product query: " + result);
            return result;

        } catch (JSONException e) {
            Log.e(TAG, "Failed to build product query", e);
            return buildFallbackProductQuery(code, language);
        }
    }

    // ========== NEW: AUTOCOMPLETE QUERY BUILDING ==========

    /**
     * Builds an autocomplete query optimized for partial matches
     *
     * Uses match_phrase_prefix for typeahead functionality, allowing
     * "choco" to match "chocolate" immediately.
     *
     * DIFFERENCE FROM buildSearchQuery():
     * - Regular search: Uses multi_match (requires complete words)
     * - Autocomplete: Uses match_phrase_prefix (partial word matching)
     *
     * TYPICAL USAGE:
     * ```java
     * // User types "choco" in search box
     * String json = CiqualSearchRequest.buildAutocompleteQuery("choco", "en", 10);
     * // Returns products starting with "chocolate"
     * ```
     *
     * @param query Partial query (e.g., "choco", "potat")
     * @param language Language code ("en" or "fr")
     * @param size Number of suggestions (typically 5-10)
     * @return JSON string for Elasticsearch request
     */
    @NonNull
    public static String buildAutocompleteQuery(@NonNull String query, @NonNull String language, int size) {
        try {
            String effectiveLanguage = normalizeLanguage(language);

            JSONObject root = new JSONObject();

            // No pagination for autocomplete (always start from 0)
            root.put("from", 0);
            root.put("size", Math.min(size, MAX_RESULTS));

            // Build autocomplete-specific bool query
            JSONObject boolQuery = createAutocompleteBoolQuery(query, effectiveLanguage);
            JSONObject queryWrapper = new JSONObject();
            queryWrapper.put("bool", boolQuery);
            root.put("query", queryWrapper);

            // Minimal source fields for autocomplete (performance)
            JSONArray sourceFields = new JSONArray();
            sourceFields.put("code");
            sourceFields.put("nomEng");
            sourceFields.put("nomFr");
            sourceFields.put("nomIndexEng");
            sourceFields.put("nomIndexFr");
            root.put("_source", sourceFields);

            String result = root.toString();
            Log.d(TAG, "Built autocomplete query: " + result);
            return result;

        } catch (JSONException e) {
            Log.e(TAG, "Failed to build autocomplete query", e);
            return buildFallbackAutocompleteQuery(query, language, size);
        }
    }

    // ========== PRIVATE: QUERY BUILDING ==========

    /**
     * Creates the bool query with must (required) and should (boosting) clauses
     */
    private static JSONObject createBoolQuery(String query, String language) throws JSONException {
        JSONObject boolQuery = new JSONObject();

        // MUST clause: Required match in language-specific fields
        JSONArray mustArray = new JSONArray();
        mustArray.put(createMultiMatchClause(query, language));
        boolQuery.put("must", mustArray);

        // SHOULD clause: Boost results starting with query
        JSONArray shouldArray = new JSONArray();
        shouldArray.put(createPrefixClause(query, language));
        boolQuery.put("should", shouldArray);

        return boolQuery;
    }

    /**
     * NEW: Creates bool query optimized for autocomplete
     *
     * Uses SHOULD with match_phrase_prefix instead of MUST with multi_match.
     * This allows partial word matching for typeahead functionality.
     */
    private static JSONObject createAutocompleteBoolQuery(String query, String language)
            throws JSONException {
        JSONObject boolQuery = new JSONObject();
        JSONArray shouldArray = new JSONArray();

        // Primary: match_phrase_prefix for partial word matching
        shouldArray.put(createPhrasePrefixClause(query, language));

        // Boost: prefix on sort field for items starting with query
        shouldArray.put(createPrefixClause(query, language));

        boolQuery.put("should", shouldArray);
        boolQuery.put("minimum_should_match", 1);  // At least one must match

        return boolQuery;
    }

    /**
     * Creates multi_match clause for full-text search
     */
    private static JSONObject createMultiMatchClause(String query, String language)
            throws JSONException {
        JSONObject multiMatch = new JSONObject();
        JSONObject multiMatchQuery = new JSONObject();

        multiMatchQuery.put("query", query.trim());
        multiMatchQuery.put("fields", getSearchFields(language));

        multiMatch.put("multi_match", multiMatchQuery);
        return multiMatch;
    }

    /**
     * Creates prefix clause for autocomplete-style boosting
     */
    private static JSONObject createPrefixClause(String query, String language)
            throws JSONException {
        JSONObject prefix = new JSONObject();
        JSONObject prefixQuery = new JSONObject();
        JSONObject prefixValue = new JSONObject();

        prefixValue.put("value", query.trim());
        prefixValue.put("boost", PREFIX_BOOST);

        prefixQuery.put(getSortField(language), prefixValue);
        prefix.put("prefix", prefixQuery);

        return prefix;
    }

    /**
     * NEW: Creates match_phrase_prefix clause for autocomplete
     *
     * This allows partial word matching (e.g., "choco" matches "chocolate")
     * which is essential for autocomplete functionality.
     */
    private static JSONObject createPhrasePrefixClause(String query, String language)
            throws JSONException {
        JSONObject matchPhrasePrefix = new JSONObject();
        JSONObject phraseQuery = new JSONObject();
        JSONObject phraseValue = new JSONObject();

        phraseValue.put("query", query.trim());
        phraseValue.put("boost", 2.0);  // High boost for phrase prefix matches

        // Use the indexed field (nomIndexEng or nomIndexFr)
        String field = "en".equals(language) ? "nomIndexEng" : "nomIndexFr";
        phraseQuery.put(field, phraseValue);

        matchPhrasePrefix.put("match_phrase_prefix", phraseQuery);
        return matchPhrasePrefix;
    }

    /**
     * Creates source exclusions for performance
     * Excludes internal fields but keeps both language names
     */
    private static JSONObject createSourceExclusions() throws JSONException {
        JSONObject source = new JSONObject();
        JSONArray excludes = new JSONArray();

        // Exclude internal/verbose fields
        // NOTE: "compos" is intentionally NOT excluded so nutrition data
        // is available for display in search result cards.
        excludes.put("nomSortEng");       // English sort (internal)
        excludes.put("nomSortFr");        // French sort (internal)
        excludes.put("nomIndexFr");       // French index (internal)
        excludes.put("nomIndexEng");      // English index (internal)

        // Keep nomEng and nomFr for language fallback!

        source.put("excludes", excludes);
        return source;
    }

    // ========== PRIVATE: FIELD SELECTION ==========

    /**
     * Gets search fields for the given language
     * Returns field names with optional boost notation
     */
    private static JSONArray getSearchFields(String language) throws JSONException {
        JSONArray fields = new JSONArray();

        if ("en".equals(language)) {
            fields.put(String.format(Locale.US, "nomIndexEng^%d", INDEXED_NAME_BOOST));
            fields.put("nomIndexEng");  // FIXED: Use indexed field (searchable)
        } else {
            // Default to French
            fields.put(String.format(Locale.US, "nomIndexFr^%d", INDEXED_NAME_BOOST));
            fields.put("nomIndexFr");  // FIXED: Use indexed field (searchable)
        }

        return fields;
    }

    /**
     * Gets sort/prefix field for the given language
     */
    private static String getSortField(String language) {
        return "en".equals(language) ? "nomSortEng" : "nomSortFr";
    }

    /**
     * Normalizes language code to supported values
     */
    private static String normalizeLanguage(String language) {
        return "en".equals(language) ? "en" : "fr";
    }

    // ========== PRIVATE: FALLBACK QUERIES ==========

    /**
     * Builds a minimal fallback query if JSON building fails
     * Uses simple string formatting for guaranteed success
     */
    private static String buildFallbackQuery(String query, String language, int from, int size) {
        String safeQuery = escapeJson(query);
        String field = "en".equals(language) ? "nomIndexEng" : "nomIndexFr";  // FIXED: Use indexed fields

        return String.format(Locale.US,
                "{\"from\":%d,\"size\":%d,\"query\":{\"multi_match\":{\"query\":\"%s\",\"fields\":[\"%s\"]}}}",
                Math.max(0, from),
                Math.min(size, MAX_RESULTS),
                safeQuery,
                field
        );
    }

    /**
     * Builds a minimal fallback product query
     */
    private static String buildFallbackProductQuery(String code, String language) {
        String safeCode = escapeJson(code);
        String sortField = getSortField(normalizeLanguage(language));

        return String.format(Locale.US,
                "{\"query\":{\"match_phrase\":{\"code\":{\"query\":\"%s\"}}},\"size\":1,\"sort\":\"%s\"}",
                safeCode,
                sortField
        );
    }

    /**
     * NEW: Builds a minimal fallback autocomplete query
     *
     * Used if JSON building fails. Uses simple string formatting with
     * match_phrase_prefix for autocomplete functionality.
     */
    private static String buildFallbackAutocompleteQuery(String query, String language, int size) {
        String safeQuery = escapeJson(query);
        String field = "en".equals(language) ? "nomIndexEng" : "nomIndexFr";

        return String.format(Locale.US,
                "{\"size\":%d,\"query\":{\"match_phrase_prefix\":{\"%s\":\"%s\"}}}",
                Math.min(size, MAX_RESULTS),
                field,
                safeQuery
        );
    }

    /**
     * Escapes quotes in strings to prevent JSON injection
     */
    private static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.trim()
                .replace("\\", "\\\\")  // Escape backslashes first
                .replace("\"", "\\\"")  // Then escape quotes
                .substring(0, Math.min(value.trim().length(), 200));  // Cap length
    }

    // ========== PUBLIC: UTILITY METHODS ==========

    /**
     * Validates a search query string
     *
     * @param query Query to validate
     * @return true if query is valid for searching
     */
    public static boolean isValidQuery(String query) {
        return query != null && !query.trim().isEmpty() && query.trim().length() >= 2;
    }

    /**
     * Validates a Ciqual product code
     *
     * @param code Code to validate
     * @return true if code appears valid
     */
    public static boolean isValidProductCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            return false;
        }
        // Ciqual codes are numeric strings, typically 4-5 digits
        String trimmed = code.trim();
        return trimmed.matches("\\d{4,6}");
    }
}