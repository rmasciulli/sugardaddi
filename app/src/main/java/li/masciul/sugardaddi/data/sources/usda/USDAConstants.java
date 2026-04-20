package li.masciul.sugardaddi.data.sources.usda;

import java.util.List;

/**
 * USDAConstants — All USDA FoodData Central data source constants.
 *
 * DATASET UPDATE PROCEDURE (when USDA releases a new FoodData Central build):
 * 1. Update DATASET_VERSION to the new release date string (e.g. "December_2026")
 * 2. Update FOUNDATION_JSON_URL and SR_LEGACY_JSON_URL to the new download URLs
 * 3. Update gitignore asset patterns if filenames change
 * 4. The app will detect the version mismatch on next launch and trigger a re-import
 *    prompt in the Settings card (no auto-import — USDA DB is opt-in only).
 *
 * DATA TYPE STRATEGY
 * ==================
 * FoodData Central has four data types. We use three:
 *
 *   Foundation Foods  — ~1,200 foods. Raw agricultural commodities with exhaustive
 *                       nutrient profiles measured by USDA labs. Highest scientific
 *                       quality. 6.5MB unzipped JSON. Always included.
 *
 *   SR Legacy         — ~7,700 foods. The former USDA Standard Reference database.
 *                       Broad coverage of generic foods. 205MB unzipped JSON.
 *                       Included in local DB and API queries.
 *
 *   Survey (FNDDS)    — ~7,300 foods. US dietary survey data (composite dishes,
 *                       "beef stew as eaten"). US-centric, useful for population
 *                       studies. 64MB unzipped JSON. Included in API queries only
 *                       (not local DB — adds limited value vs Foundation+SR Legacy).
 *
 *   Branded           — ~400,000 foods. Consumer products with inconsistent quality.
 *                       Heavily overlaps with OpenFoodFacts. EXCLUDED from both
 *                       API queries and local DB.
 *
 * API vs LOCAL DB
 * ===============
 * API queries use Foundation + SR Legacy + Survey (broadest results, no download cost).
 * Local DB imports Foundation + SR Legacy only (~215MB total, ~14MB zipped).
 * Local DB is opt-in — user initiates from Settings. Never auto-triggered.
 */
public final class USDAConstants {

    // ===== SOURCE IDENTIFICATION =====
    public static final String SOURCE_ID   = "USDA";
    public static final String SOURCE_NAME = "USDA FoodData";
    public static final String ATTRIBUTION =
            "U.S. Department of Agriculture, Agricultural Research Service. " +
                    "FoodData Central. fdc.nal.usda.gov";

    // ===== API CONFIGURATION =====
    public static final String BASE_URL    = "https://api.nal.usda.gov/fdc/v1/";
    public static final String USER_AGENT  = "SugarDaddi/1.0 (Android; contact: sugardaddi@example.com)";

    /**
     * DEMO_KEY is the USDA public fallback key.
     * Rate limits: 30 requests/hour per IP, 1000 requests/day.
     * Users should register for a free key at https://fdc.nal.usda.gov/api-key-signup/
     * to get 3600 requests/hour with no daily cap.
     */
    public static final String DEMO_KEY = "DEMO_KEY";

    // ===== DATA TYPES =====
    /**
     * Comma-separated dataType values for API search requests.
     * Survey (FNDDS) is included for broader online results at no extra cost.
     * Branded is excluded — OFF covers that space better.
     */
    public static final List<String> API_DATA_TYPES =
            java.util.Arrays.asList("Foundation", "SR Legacy", "Survey (FNDDS)");

    /**
     * Data types included in the local DB download.
     * Survey excluded — 64MB for marginal value over Foundation+SR Legacy.
     */
    public static final List<String> LOCAL_DB_DATA_TYPES =
            java.util.Arrays.asList("Foundation", "SR Legacy");

    // ===== DATASET VERSION =====
    /**
     * Current dataset version. Matches USDA release naming.
     * Foundation Foods: December 2025 release.
     * SR Legacy: April 2018 (stable — no newer release planned).
     * When this changes, the settings card shows an "update available" state.
     */
    public static final String DATASET_VERSION = "December_2025";

    // ===== DOWNLOAD URLS =====
    /**
     * Foundation Foods JSON — December 2025 release.
     * Zipped: 467KB, Unzipped: 6.5MB. ~1,200 foods.
     */
    public static final String FOUNDATION_JSON_URL =
            "https://fdc.nal.usda.gov/fdc-datasets/FoodData_Central_foundation_food_json_2025-12-18.zip";

    /**
     * SR Legacy JSON — April 2018 release (latest available).
     * Zipped: 12.3MB, Unzipped: 205MB. ~7,700 foods.
     * Note: Large unzipped size — stream-parse rather than loading entirely into memory.
     */
    public static final String SR_LEGACY_JSON_URL =
            "https://fdc.nal.usda.gov/fdc-datasets/FoodData_Central_sr_legacy_food_json_2018-04.zip";

    // ===== API ENDPOINTS (relative to BASE_URL) =====
    /** Search endpoint — GET /foods/search */
    public static final String ENDPOINT_SEARCH = "foods/search";

    /** Food detail endpoint — GET /food/{fdcId} */
    public static final String ENDPOINT_FOOD = "food/";

    // ===== API QUERY PARAMETERS =====
    /** Default number of results per API search page */
    public static final int DEFAULT_PAGE_SIZE = 25;

    /** Maximum allowed page size by the FDC API */
    public static final int MAX_PAGE_SIZE = 200;

    /** Default sort field for search results */
    public static final String DEFAULT_SORT_BY    = "score";
    public static final String DEFAULT_SORT_ORDER = "desc";

    // ===== PREFS (for import state persistence) =====
    public static final String PREFS_NAME          = "usda_import";
    public static final String PREF_DB_READY       = "db_ready";
    public static final String PREF_IMPORT_VERSION = "import_version";
    public static final String PREF_IMPORT_DATE    = "import_date";
    public static final String PREF_API_KEY        = "api_key";

    // ===== BROADCASTS =====
    public static final String BROADCAST_PROGRESS = "li.masciul.sugardaddi.usda.PROGRESS";
    public static final String BROADCAST_COMPLETE = "li.masciul.sugardaddi.usda.COMPLETE";
    public static final String BROADCAST_ERROR    = "li.masciul.sugardaddi.usda.ERROR";
    public static final String EXTRA_PHASE        = "phase";
    public static final String EXTRA_PROGRESS_PCT = "progress_pct";
    public static final String EXTRA_ERROR_MSG    = "error_msg";

    // ===== NOTIFICATION =====
    public static final String NOTIF_CHANNEL_ID = "usda_import";
    public static final int    NOTIF_ID         = 7002;

    // ===== IMPORT TUNING =====
    public static final int IMPORT_BATCH_SIZE    = 200;
    /** 5 minutes — SR Legacy is 205MB on variable connections */
    public static final int DOWNLOAD_TIMEOUT_MS  = 300_000;
    public static final int CONNECT_TIMEOUT_MS   = 30_000;

    private USDAConstants() {
        throw new UnsupportedOperationException("USDAConstants is a utility class");
    }
}