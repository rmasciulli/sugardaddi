package li.masciul.sugardaddi.data.sources.thecocktaildb;

/**
 * TheCocktailDbConstants — All TheCocktailDB data source constants.
 *
 * API NOTES
 * =========
 * TheCocktailDB v1 (free tier, test key "1"):
 * - The API key is embedded as a PATH SEGMENT, not a query parameter:
 *       https://www.thecocktaildb.com/api/json/v1/{key}/search.php?s=query
 * - Development key "1" is TheCocktailDB's own documented public test key.
 *   No signup required. Safe to ship in open-source code.
 * - Public app store release requires a Patreon-tier key.
 *   See: https://www.thecocktaildb.com/api.php
 *
 * RELATIONSHIP TO THEMEALDB
 * =========================
 * TheCocktailDB and TheMealDB share the same developer and very similar API
 * structure. Key differences:
 *   - Root JSON key is "drinks" (not "meals")
 *   - ID/name/thumbnail fields are prefixed "Drink" (not "Meal")
 *   - 15 ingredient slots (not 20)
 *   - No strArea field — cocktails have no geographic origin
 *   - Extra fields: strAlcoholic, strGlass
 *
 * Despite the similarity, TheCocktailDB is a fully independent data source
 * in this codebase. No files are shared with the TheMealDB integration.
 *
 * API KEY PRIORITY (same pattern as TheMealDB and USDA)
 * ======================================================
 * 1. SharedPreferences — user entered their own key in Settings
 * 2. BuildConfig.THECOCKTAILDB_API_KEY — from local.properties at compile time
 * 3. DEMO_KEY ("1") — hardcoded fallback, always works for development
 *
 * BASE URL CONSTRUCTION
 * =====================
 * Because the key is a path segment, the base URL cannot be a static constant.
 * Use {@link #buildBaseUrl(String)} to construct the correct URL for a given key.
 *
 * ENDPOINTS USED
 * ==============
 * - search.php?s={name}  → search by cocktail name (returns full drink objects)
 * - lookup.php?i={id}    → fetch full drink detail by ID
 *
 * ENDPOINTS NOT USED (out of scope or Patreon-only)
 * ==================================================
 * - filter.php?i={ingredient} → filter by ingredient (stripped response only)
 * - filter.php?c={category}   → filter by category
 * - filter.php?a={alcoholic}  → filter by alcoholic/non-alcoholic
 * - filter.php?g={glass}      → filter by glass type
 * - random.php                → random cocktail
 */
public final class TheCocktailDbConstants {

    // ===== SOURCE IDENTIFICATION =====

    /** Stable source ID — used as Room sourceId discriminator and DataSourceManager key. */
    public static final String SOURCE_ID   = "THECOCKTAILDB";

    /** Human-readable source name for logging and display. */
    public static final String SOURCE_NAME = "TheCocktailDB";

    /** Short attribution string. */
    public static final String ATTRIBUTION = "Data provided by TheCocktailDB (thecocktaildb.com)";

    // ===== API CONFIGURATION =====

    /**
     * Base URL template. The {key} placeholder is replaced by the active API key.
     * The key is a PATH SEGMENT — not a query parameter.
     * Format: https://www.thecocktaildb.com/api/json/v1/{key}/
     */
    public static final String BASE_URL_TEMPLATE =
            "https://www.thecocktaildb.com/api/json/v1/%s/";

    /**
     * TheCocktailDB's public development key.
     * Documented for open development use — safe to hardcode.
     * Sufficient for search and lookup on the free v1 tier.
     */
    public static final String DEMO_KEY = "1";

    /**
     * User-Agent sent with all requests.
     * Identifies the app to TheCocktailDB in server logs.
     */
    public static final String USER_AGENT =
            "SugarDaddi/1.0 (Android; open-source nutrition tracker)";

    // ===== API ENDPOINTS (relative to base URL) =====

    /** Search cocktails by name. Returns full drink objects. */
    public static final String ENDPOINT_SEARCH = "search.php";

    /** Fetch full drink detail by TheCocktailDB numeric ID. */
    public static final String ENDPOINT_LOOKUP = "lookup.php";

    // ===== QUERY PARAMETERS =====

    /** Query parameter for name search: ?s={query} */
    public static final String PARAM_SEARCH = "s";

    /** Query parameter for ID lookup: ?i={id} */
    public static final String PARAM_ID = "i";

    // ===== SEARCH BEHAVIOUR =====

    /**
     * Maximum number of search results to forward to the aggregator.
     * TheCocktailDB search.php returns all matches at once with no pagination.
     * Capped to avoid flooding the diversity strategy with cocktail results.
     */
    public static final int MAX_SEARCH_RESULTS = 20;

    /**
     * Minimum query length before firing a search request.
     * Single characters return too many results to be useful.
     */
    public static final int MIN_QUERY_LENGTH = 2;

    // ===== INGREDIENT LIST SIZE =====

    /**
     * TheCocktailDB uses exactly 15 parallel ingredient/measure field pairs.
     * Note: TheMealDB uses 20 — this is a deliberate difference in the API.
     * Used by the mapper when iterating strIngredient1..15.
     */
    public static final int MAX_INGREDIENTS = 15;

    // ===== ALCOHOLIC STATUS VALUES =====
    // Values returned by the strAlcoholic field. Stored as tags on the Recipe.

    /** strAlcoholic value for alcoholic cocktails. Stored as tag "alcoholic". */
    public static final String ALCOHOLIC_VALUE     = "Alcoholic";

    /** strAlcoholic value for non-alcoholic cocktails. Stored as tag "non_alcoholic". */
    public static final String NON_ALCOHOLIC_VALUE = "Non alcoholic";

    /** strAlcoholic value for cocktails that can be made either way. Stored as tag "optional_alcohol". */
    public static final String OPTIONAL_ALCOHOL_VALUE = "Optional alcohol";

    // Tags written to Recipe.getTags() by the mapper
    public static final String TAG_ALCOHOLIC         = "alcoholic";
    public static final String TAG_NON_ALCOHOLIC     = "non_alcoholic";
    public static final String TAG_OPTIONAL_ALCOHOL  = "optional_alcohol";
    public static final String TAG_HAS_VIDEO         = "has_video";

    // ===== PREFS =====

    /** SharedPreferences file name for this source's persisted state. */
    public static final String PREFS_NAME  = "thecocktaildb_prefs";

    /** SharedPreferences key for the enabled/disabled toggle. */
    public static final String PREF_ENABLED = "enabled";

    /** SharedPreferences key for the user-supplied API key. */
    public static final String PREF_API_KEY = "api_key";

    // ===== URL BUILDER =====

    /**
     * Build the Retrofit base URL for a given API key.
     *
     * The key is embedded as a path segment:
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

    private TheCocktailDbConstants() {
        throw new UnsupportedOperationException("TheCocktailDbConstants is a utility class");
    }
}