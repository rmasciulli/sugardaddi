package li.masciul.sugardaddi.data.network;

/**
 * ApiConfig - Centralized SHARED configuration for the entire application
 *
 * ARCHITECTURE UPDATE v3.1:
 * This class now contains ONLY shared configuration used across multiple data sources.
 * Source-specific constants (OFF, Ciqual) have been moved to their respective Constants classes:
 * - OpenFoodFacts-specific → OpenFoodFactsConstants.java
 * - Ciqual-specific → CiqualConstants.java
 *
 * WHAT BELONGS HERE:
 * - Network timeouts (shared by all HTTP clients)
 * - Logging flags (global debugging)
 * - Cache configuration (shared caching behavior)
 * - Search behavior (debounce, pagination, scoring)
 * - Validation limits (universal constraints)
 * - Nutrition thresholds (EU regulations, source-independent)
 *
 * WHAT DOESN'T BELONG HERE:
 * - API URLs (moved to OpenFoodFactsConstants, CiqualConstants)
 * - User agents (moved to source-specific constants)
 * - Field definitions (moved to API interfaces themselves)
 * - Data source specific settings
 */
public final class ApiConfig {

    // ========== NETWORK TIMEOUTS (SHARED) ==========
    /**
     * Network timeout settings (in seconds)
     * Used by all HTTP clients across all data sources
     */
    public static final int CONNECT_TIMEOUT_SECONDS = 15;
    public static final int READ_TIMEOUT_SECONDS = 30;
    public static final int WRITE_TIMEOUT_SECONDS = 15;

    // ========== LOGGING CONFIGURATION (SHARED) ==========
    /**
     * Global logging flags
     * Control logging behavior across all components
     */
    public static final boolean DEBUG_LOGGING = true;
    public static final String NETWORK_LOG_TAG = "SugarDaddi_Net";
    public static final String SEARCH_LOG_TAG = "SugarDaddi_Search";
    public static final String DATABASE_LOG_TAG = "SugarDaddi_DB";
    public static final String UI_LOG_TAG = "SugarDaddi_UI";
    public static final String CACHE_LOG_TAG = "SugarDaddi_Cache";

    // ========== SEARCH CONFIGURATION (SHARED) ==========
    /**
     * Universal search behavior settings
     */
    public static final long SEARCH_DEBOUNCE_MS = 300;  // 300ms typing debounce
    public static final int MIN_SEARCH_LENGTH = 3;      // Minimum characters to trigger search
    public static final int MAX_RESULTS = 20;           // Maximum results per search

    /**
     * Scoring configuration for search results
     * Centralized scoring weights for consistency and easy tuning
     */
    public static final class Scoring {

        // ===== COMMON SCORING (used by all sources) =====

        /** Name matching scores */
        public static final int EXACT_NAME_MATCH = 40;
        public static final int NAME_STARTS_WITH = 25;
        public static final int NAME_CONTAINS = 20;

        /** Category matching scores */
        public static final int CATEGORY_MATCH = 20;
        public static final int PRIMARY_CATEGORY_MATCH = 15;

        /** Universal bonuses */
        public static final int FAVORITE_BONUS = 15;

        /** Minimum thresholds */
        public static final int MINIMUM_SCORE = 15;

        // ===== OPENFOODFACTS SPECIFIC =====

        public static final class OpenFoodFacts {
            public static final int EXACT_BRAND_MATCH = 30;
            public static final int PARTIAL_BRAND_MATCH = 15;
            public static final int HAS_NUTRISCORE = 10;
            public static final int HAS_ECOSCORE = 10;
            public static final int HAS_IMAGE = 5;
            public static final int HAS_NUTRITION_DATA = 15;
            public static final int COMPLETENESS_HIGH = 20;     // >80%
            public static final int COMPLETENESS_MEDIUM = 10;   // >50%
            public static final int COMPLETE_DATA_BONUS = 10;   // has brand + name
        }

        // ===== CIQUAL SPECIFIC =====

        public static final class Ciqual {
            public static final int EXACT_GENERIC_NAME = 30;
            public static final int GENERIC_STARTS_WITH = 20;
            public static final int GENERIC_CONTAINS = 15;
            public static final int NUTRITION_COMPLETE_HIGH = 25;   // 7+ fields
            public static final int NUTRITION_COMPLETE_MEDIUM = 15; // 5-6 fields
            public static final int NUTRITION_COMPLETE_LOW = 5;     // 3-4 fields
            public static final int HAS_PORTION_INFO = 10;
        }

        // ===== RECIPE SPECIFIC =====

        public static final class Recipe {
            public static final int INGREDIENT_MATCH_MULTIPLE = 30;
            public static final int INGREDIENT_MATCH_EXACT = 25;
            public static final int INGREDIENT_MATCH_PARTIAL = 15;
            public static final int CUISINE_MATCH = 15;
            public static final int HAS_STEPS = 10;
            public static final int HAS_IMAGE = 5;
            public static final int ACCESSIBLE_DIFFICULTY = 5;
        }
    }

    /**
     * Source diversity configuration
     * Controls how search results are distributed across data sources
     *
     * DIVERSITY STRATEGY: Minimum Guarantee + Quality Fill
     * - Each source gets at least MIN_RESULTS_PER_SOURCE results (if available)
     * - Remaining slots filled with highest-scored products globally
     * - Ensures users see results from all sources, not just one
     *
     * Example with MAX_RESULTS=20, MIN_RESULTS_PER_SOURCE=5:
     * - Take top 5 OpenFoodFacts products
     * - Take top 5 Ciqual products
     * - Take top 5 Recipes
     * - Fill remaining 5 slots with top-scored overall
     * = 20 total with guaranteed diversity
     */
    public static final class SourceDiversity {
        /**
         * Minimum number of results to guarantee from each source
         * Set to 0 to disable diversity enforcement (legacy behavior)
         */
        public static final int MIN_RESULTS_PER_SOURCE = 5;

        /**
         * Enable or disable source diversity enforcement
         * true = Use diversity strategy (recommended)
         * false = Global top N (legacy behavior)
         */
        public static final boolean ENFORCE_SOURCE_DIVERSITY = true;
    }

    // ========== PAGINATION (SHARED) ==========
    /**
     * Pagination configuration used across all paginated APIs
     */
    public static final int API_PAGE_SIZE = 10;         // Changed from 20 to 10
    public static final int API_MAX_PAGE_SIZE = 100;    // Maximum allowed by most APIs
    public static final int API_MAX_RETRIES = 3;        // Number of retry attempts
    public static final long API_RETRY_DELAY_MS = 1000; // Delay between retries

    // ========== CACHE CONFIGURATION (SHARED) ==========
    /**
     * Cache settings for search and product data
     */
    public static final long CACHE_EXPIRY_MS = 60 * 60 * 1000;  // 1 hour
    public static final int CACHE_MAX_SIZE = 500;               // Maximum cached items

    // ========== VALIDATION LIMITS (SHARED) ==========
    /**
     * Universal validation constraints
     */
    public static final int MAX_SEARCH_QUERY_LENGTH = 100;
    public static final int MIN_BARCODE_LENGTH = 8;
    public static final int MAX_BARCODE_LENGTH = 15;
    public static final double MIN_CUSTOM_AMOUNT = 0.1;     // Minimum serving amount (grams)
    public static final double MAX_CUSTOM_AMOUNT = 10000;   // Maximum serving amount (grams)

    // ========== FEATURE FLAGS (SHARED) ==========
    /**
     * Global feature toggles for performance monitoring
     */
    public static final boolean ENABLE_PERFORMANCE_LOGGING = true;
    public static final boolean LOG_API_RESPONSE_TIMES = true;
    public static final boolean LOG_CACHE_HIT_RATES = true;

    // ========== CONSTRUCTOR ==========
    /**
     * Private constructor to prevent instantiation
     * This is a utility class with only static members
     */
    private ApiConfig() {
        throw new UnsupportedOperationException("ApiConfig is a utility class and cannot be instantiated");
    }
}