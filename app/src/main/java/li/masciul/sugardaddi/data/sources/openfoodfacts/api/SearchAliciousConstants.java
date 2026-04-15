package li.masciul.sugardaddi.data.sources.openfoodfacts.api;

import java.util.Locale;

/**
 * SearchAliciousConstants - Field definitions and query helpers for search-a-licious API
 *
 * ARCHITECTURE:
 * search-a-licious is OpenFoodFacts' modern search API built on Elasticsearch.
 * It provides fast, relevance-scored search with advanced filtering using Lucene syntax.
 *
 * KEY DIFFERENCES FROM OFF v2:
 * - Language: Uses "langs" parameter (comma-separated) instead of "lc"
 * - Fields: Must explicitly request fields using "fields" parameter for optimal performance
 * - Response: Returns "hits" array (not "products")
 * - Score: Includes relevance "_score" for each hit
 * - Completeness: Has direct "completeness" field (0.0-1.0)
 *
 * FIELD NAMING CONVENTIONS:
 * - Language-specific: product_name_en, product_name_fr, agribalyse.name_en
 * - Arrays: brands[], categories_tags[]
 * - Nested: agribalyse.name_xx, images.front_xx.sizes
 * - Scores: nutrition_grades (singular), ecoscore_grade
 *
 * @author SugarDaddi Team
 * @version 2.0 (Search-a-licious Integration)
 */
public final class SearchAliciousConstants {

    // ========== FIELD SUBSETS ==========

    /**
     * SEARCH_RESULTS - Fields for search result list view
     *
     * Optimized for fast loading and display in RecyclerView.
     * Includes all data needed for item rendering without additional requests.
     *
     * INCLUDES:
     * - Product identification (code, name)
     * - Visual elements (image URLs)
     * - Quality indicators (nutriscore, ecoscore)
     * - Metadata (brand, category, serving size)
     * - Quality metrics (completeness, relevance score)
     */
    public static final String SEARCH_RESULTS_FIELDS =
            "code," +                           // Barcode (for navigation to details)
                    "product_name," +                   // Default product name
                    "product_name_en," +                // English name
                    "product_name_fr," +                // French name (fallback)
                    "brands," +                         // Brand array (use [0])
                    "categories_tags," +                // Category tags array
                    "agribalyse.name_en," +             // Agribalyse category (EN)
                    "agribalyse.name_fr," +             // Agribalyse category (FR)
                    "image_url," +                      // Main product image
                    "image_front_url," +                // Front image (preferred)
                    "image_front_small_url," +          // Small front image (fastest)
                    "image_small_url," +                // Small image (fallback)
                    "nutrition_grades," +               // Nutri-Score (a-e)
                    "ecoscore_grade," +                 // Eco-Score (a-e)
                    "quantity," +                       // Serving size/quantity
                    "completeness," +                   // Data quality score (0.0-1.0)
                    "_score";                           // ES relevance score

    /**
     * PERTINENCE_CHECK - Minimum fields for quality validation
     *
     * Used to determine if a product has sufficient data to be useful.
     * Smaller field set for faster queries when only checking existence/quality.
     *
     * VALIDATION LOGIC:
     * - code != null (must have barcode)
     * - product_name exists and not empty
     * - nutriments != null (has nutrition data)
     * - completeness >= 0.5 (at least 50% complete)
     */
    public static final String PERTINENCE_CHECK_FIELDS =
            "code," +
                    "product_name," +
                    "nutriments," +                     // Nutrition data object
                    "completeness";

    /**
     * AUTOCOMPLETE - Minimal fields for autocomplete suggestions
     *
     * Lightweight response for fast autocomplete/typeahead functionality.
     * Only includes fields needed for suggestion display.
     */
    public static final String AUTOCOMPLETE_FIELDS =
            "code," +
                    "product_name," +
                    "product_name_en," +
                    "product_name_fr," +
                    "brands," +
                    "image_small_url";

    // ========== LUCENE QUERY PATTERNS ==========

    /**
     * Query Builders - Helpers for constructing Lucene syntax queries
     *
     * LUCENE SYNTAX EXAMPLES:
     * - Simple: "chocolate"
     * - AND: "chocolate AND milk"
     * - OR: "milk OR cream"
     * - Field: "brands:milka"
     * - Range: "completeness:[0.5 TO 1.0]"
     * - Phrase: "\"dark chocolate\""
     */
    public static final class QueryBuilder {

        /**
         * Build quality filter for completeness score
         *
         * @param minCompleteness Minimum completeness (0.0-1.0)
         * @return Lucene range query: "completeness:[min TO 1.0]"
         */
        public static String withMinCompleteness(double minCompleteness) {
            return String.format(Locale.US, "completeness:[%.2f TO 1.0]", minCompleteness);
        }

        /**
         * Build query requiring nutrition data
         *
         * @param userQuery User's search query
         * @return Combined query with nutrition requirement
         */
        public static String withNutritionData(String userQuery) {
            return String.format("%s AND nutrition_grades:[a TO e]", userQuery);
        }

        /**
         * Build query with quality threshold
         *
         * @param userQuery User's search query
         * @param minCompleteness Minimum completeness threshold
         * @return Combined query: "query AND completeness:[min TO 1.0]"
         */
        public static String withQualityFilter(String userQuery, double minCompleteness) {
            // Simple wildcard search with completeness filter
            // SearchAlicious searches across all relevant fields automatically
            return String.format(Locale.US,
                    "%s* AND completeness:[%.2f TO 1.0]",
                    userQuery,
                    minCompleteness
            );
        }

        /**
         * Build query for specific category
         *
         * @param userQuery User's search query
         * @param categoryTag Category tag (e.g., "en:beverages")
         * @return Combined query with category filter
         */
        public static String inCategory(String userQuery, String categoryTag) {
            return String.format("%s AND categories_tags:\"%s\"", userQuery, categoryTag);
        }

        /**
         * Build query with ecoscore filter
         *
         * @param userQuery User's search query
         * @param minGrade Minimum ecoscore grade (a-e)
         * @return Combined query with ecoscore requirement
         */
        public static String withMinEcoscore(String userQuery, char minGrade) {
            return String.format("%s AND ecoscore_grade:[a TO %c]", userQuery, minGrade);
        }

        /**
         * Build comprehensive quality query
         *
         * Ensures products have:
         * - Good completeness (>= 0.5)
         * - Nutrition data present
         * - Images available
         *
         * @param userQuery User's search query
         * @return High-quality results query
         */
        public static String highQualityOnly(String userQuery) {
            return String.format(Locale.US,
                    "%s AND completeness:[0.5 TO 1.0] AND nutrition_grades:[a TO e] AND image_url:*",
                    userQuery
            );
        }
    }

    // ========== DEFAULT VALUES ==========

    /**
     * Default search parameters
     */
    public static final class Defaults {
        /** Default page size for search results */
        public static final int PAGE_SIZE = 20;

        /** Maximum page size allowed by API */
        public static final int MAX_PAGE_SIZE = 100;

        /** First page number (1-based indexing) */
        public static final int FIRST_PAGE = 1;

        /** Default completeness threshold for quality filtering */
        public static final double MIN_COMPLETENESS = 0.5;

        /** Default language for search (English) */
        public static final String DEFAULT_LANGUAGE = "en";

        /** Secondary language (French - high data coverage) */
        public static final String SECONDARY_LANGUAGE = "fr";

        /** Default languages string (comma-separated) */
        public static final String DEFAULT_LANGS = "en,fr";

        /** Autocomplete result size */
        public static final int AUTOCOMPLETE_SIZE = 10;
    }

    // ========== VALIDATION THRESHOLDS ==========

    /**
     * Quality thresholds for product pertinence validation
     */
    public static final class QualityThresholds {
        /** Minimum completeness to consider product usable (50%) */
        public static final double MIN_COMPLETENESS = 0.5;

        /** Good completeness threshold (70%) */
        public static final double GOOD_COMPLETENESS = 0.7;

        /** Excellent completeness threshold (90%) */
        public static final double EXCELLENT_COMPLETENESS = 0.9;

        /** Minimum relevance score (ES _score) */
        public static final double MIN_RELEVANCE_SCORE = 10.0;
    }

    // ========== LANGUAGE CODES ==========

    /**
     * Common language codes for search-a-licious
     * Used for product_name_xx, agribalyse.name_xx fields
     */
    public static final class Languages {
        public static final String ENGLISH = "en";
        public static final String FRENCH = "fr";
        public static final String SPANISH = "es";
        public static final String GERMAN = "de";
        public static final String ITALIAN = "it";
    }

    // ========== SORTING OPTIONS ==========

    /**
     * Sort options for SearchAlicious
     *
     * POPULARITY SORTING:
     * Uses unique_scans_n field which tracks how many times a product
     * has been scanned. Popular products (Lindt, Milka, Kinder) have
     * high scan counts and appear first.
     *
     * USAGE:
     * - Prefix with "-" for descending order (high to low)
     * - No prefix for ascending order (low to high)
     * - Pass null to use default relevance scoring
     *
     * EXAMPLE:
     * searchApi.search(query, langs, size, page, fields, SortBy.POPULARITY)
     */
    public static final class SortBy {
        /**
         * Sort by popularity (most scanned products first)
         * RECOMMENDED for search results
         */
        public static final String POPULARITY = "-unique_scans_n";

        /**
         * Sort by relevance score (default)
         * Use null to let Elasticsearch rank by relevance
         */
        public static final String RELEVANCE = null;

        /**
         * Sort by data completeness (highest quality first)
         */
        public static final String COMPLETENESS = "-completeness";

        /**
         * Sort by newest products
         */
        public static final String NEWEST = "-created_t";

        /**
         * Sort by recently updated products
         */
        public static final String RECENTLY_UPDATED = "-last_modified_t";
    }

    // ========== CONSTRUCTOR ==========

    private SearchAliciousConstants() {
        throw new UnsupportedOperationException(
                "SearchAliciousConstants is a utility class and cannot be instantiated"
        );
    }
}