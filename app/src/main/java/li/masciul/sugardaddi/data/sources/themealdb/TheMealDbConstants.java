package li.masciul.sugardaddi.data.sources.themealdb;

/**
 * TheMealDbConstants — All TheMealDB data source constants.
 *
 * API NOTES
 * =========
 * TheMealDB v1 (free tier, test key "1"):
 * - The API key is embedded as a PATH SEGMENT, not a query parameter:
 *       https://www.themealdb.com/api/json/v1/{key}/search.php?s=query
 * - Development key "1" is TheMealDB's own documented public test key.
 *   No signup required. Safe to ship in open-source code.
 * - Public app store release requires a Patreon-tier key.
 *   See: https://www.themealdb.com/api.php
 *
 * API KEY PRIORITY (same pattern as USDA)
 * =======================================
 * 1. SharedPreferences — user entered their own key in Settings
 * 2. BuildConfig.THEMEALDB_API_KEY — from local.properties at compile time
 * 3. DEMO_KEY ("1") — hardcoded fallback, always works for development
 *
 * BASE URL CONSTRUCTION
 * =====================
 * Because the key is a path segment, the base URL cannot be a static constant.
 * Use {@link #buildBaseUrl(String)} to construct the correct URL for a given key.
 *
 * ENDPOINTS USED
 * ==============
 * - search.php?s={name}  → search by meal name (returns full meal objects)
 * - lookup.php?i={id}    → fetch full meal detail by ID
 *
 * ENDPOINTS NOT USED (out of scope or Patreon-only)
 * ==================================================
 * - filter.php?i={ingredient} → filter by ingredient (stripped response only)
 * - filter.php?c={category}   → filter by category
 * - filter.php?a={area}       → filter by area
 * - random.php                → random meal
 */
public final class TheMealDbConstants {

    // ===== SOURCE IDENTIFICATION =====

    /** Stable source ID — used as Room sourceId discriminator and DataSourceManager key. */
    public static final String SOURCE_ID   = "THEMEALDB";

    /** Human-readable source name for logging. */
    public static final String SOURCE_NAME = "TheMealDB";

    /** Short attribution string for search result badges. */
    public static final String ATTRIBUTION = "Data provided by TheMealDB (themealdb.com)";

    // ===== API CONFIGURATION =====

    /**
     * Base URL template. The {key} placeholder is replaced by the active API key.
     * The key is a PATH SEGMENT — not a query parameter.
     * Format: https://www.themealdb.com/api/json/v1/{key}/
     */
    public static final String BASE_URL_TEMPLATE =
            "https://www.themealdb.com/api/json/v1/%s/";

    /**
     * TheMealDB's public development key.
     * Documented by TheMealDB for open development use — safe to hardcode.
     * Functionally limited vs. a Patreon key (no randomMeals plural, etc.)
     * but fully sufficient for search and lookup.
     */
    public static final String DEMO_KEY = "1";

    /**
     * User-Agent sent with all requests.
     * Identifies the app to TheMealDB in server logs.
     */
    public static final String USER_AGENT =
            "SugarDaddi/1.0 (Android; open-source nutrition tracker)";

    // ===== API ENDPOINTS (relative to base URL) =====

    /** Search meals by name. Returns full meal objects. */
    public static final String ENDPOINT_SEARCH = "search.php";

    /** Fetch full meal detail by TheMealDB numeric ID. */
    public static final String ENDPOINT_LOOKUP = "lookup.php";

    // ===== QUERY PARAMETERS =====

    /** Query parameter for name search: ?s={query} */
    public static final String PARAM_SEARCH = "s";

    /** Query parameter for ID lookup: ?i={id} */
    public static final String PARAM_ID = "i";

    // ===== SEARCH BEHAVIOUR =====

    /**
     * Maximum number of search results to forward to the aggregator.
     * TheMealDB search.php returns all matches at once with no pagination.
     * We cap to avoid flooding the diversity strategy with recipe results.
     */
    public static final int MAX_SEARCH_RESULTS = 20;

    /**
     * Minimum query length before firing a search request.
     * Single characters return too many results to be useful.
     */
    public static final int MIN_QUERY_LENGTH = 2;

    // ===== PREFS =====

    /** SharedPreferences file name for this source's persisted state. */
    public static final String PREFS_NAME = "themealdb_prefs";

    /** SharedPreferences key for the enabled/disabled toggle. */
    public static final String PREF_ENABLED = "enabled";

    /** SharedPreferences key for the user-supplied API key. */
    public static final String PREF_API_KEY = "api_key";

    // ===== INGREDIENT LIST SIZE =====

    /**
     * TheMealDB uses exactly 20 parallel ingredient/measure field pairs.
     * Used by the mapper when iterating strIngredient1..20.
     */
    public static final int MAX_INGREDIENTS = 20;

    // ===== URL BUILDER =====

    /**
     * Build the Retrofit base URL for a given API key.
     *
     * The key is embedded as a path segment per TheMealDB's URL scheme:
     *   /api/json/v1/1/   → development key
     *   /api/json/v1/abc/ → Patreon key
     *
     * Falls back to {@link #DEMO_KEY} if the provided key is null or blank.
     *
     * @param apiKey Active API key (from SharedPreferences, BuildConfig, or demo)
     * @return Complete Retrofit base URL with trailing slash
     */
    public static String buildBaseUrl(String apiKey) {
        String key = (apiKey != null && !apiKey.trim().isEmpty())
                ? apiKey.trim()
                : DEMO_KEY;
        return String.format(BASE_URL_TEMPLATE, key);
    }

    // ===== UTILITY =====

    private TheMealDbConstants() {
        throw new UnsupportedOperationException("TheMealDbConstants is a utility class");
    }
}