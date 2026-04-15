package li.masciul.sugardaddi.data.sources.openfoodfacts.api;

import com.google.gson.JsonObject;

import li.masciul.sugardaddi.data.network.ApiConfig;
import li.masciul.sugardaddi.data.sources.openfoodfacts.OpenFoodFactsConstants;
import li.masciul.sugardaddi.data.sources.openfoodfacts.api.dto.OpenFoodFactsProduct;
import li.masciul.sugardaddi.data.sources.openfoodfacts.api.dto.OpenFoodFactsSearchResponse;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;
import retrofit2.http.Url;

/**
 * OpenFoodFactsAPI - Simplified Language-Aware Retrofit Interface
 *
 * MAJOR IMPROVEMENT: All API methods now consistently require language parameters,
 * eliminating confusion between "WithLanguage" and regular variants. This ensures
 * all data is returned in the user's preferred language.
 *
 * KEY CHANGES:
 * 1. Removed confusing dual methods (e.g., getProduct vs getProductWithLanguage)
 * 2. All methods now require explicit language parameter (@Query("lc"))
 * 3. Simplified interface with consistent parameter ordering
 * 4. Better documentation explaining language behavior
 * 5. Updated field definitions to reflect language-aware approach
 *
 * LANGUAGE PARAMETER ("lc"):
 * The "lc" (language code) parameter tells OpenFoodFacts which language to prioritize
 * in the response. Examples: "en" for English, "fr" for French, "es" for Spanish.
 * This affects product names, ingredients, categories, and other text fields.
 */
public interface OpenFoodFactsAPI {

    // ========== SEARCH OPERATIONS ==========

    /**
     * Search for food products with comprehensive language support
     *
     * This is the unified search method that replaces the previous confusing
     * searchProducts/searchProductsSimple/searchProductsWithLanguage variants.
     *
     * LANGUAGE BEHAVIOR:
     * - Results prioritize the specified language ("lc" parameter)
     * - If data unavailable in requested language, falls back to product's main language
     * - Category names, product names, and ingredients returned in requested language when available
     *
     * PAGINATION:
     * - Use page=1 for first page, page=2 for second page, etc.
     * - CRITICAL: Always use the same language across all pages for consistency
     *
     * @param query Search query (e.g., "chocolate cookies", "milka")
     * @param pageSize Number of results per page (max 100, recommended 20-50)
     * @param page Page number starting from 1
     * @param fields Comma-separated list of fields to retrieve (use Fields constants)
     * @param language Language code for localized results (e.g., "en", "fr", "es")
     * @return Call containing OpenFoodFactsSearchResponse with products in requested language
     */
    @GET("search")
    Call<OpenFoodFactsSearchResponse> searchProducts(
            @Query("q") String query,
            @Query("page_size") int pageSize,
            @Query("page") int page,
            @Query("fields") String fields,
            @Query("lc") String language,
            @Query("sort_by") String sortBy
    );

    // ========== PRODUCT DETAIL OPERATIONS ==========

    /**
     * Get comprehensive product information by barcode with language support
     *
     * This unified method replaces getProduct/getProductWithLanguage variants.
     * All product data is returned in the specified language when available.
     *
     * LANGUAGE BEHAVIOR:
     * - Product name returned in requested language if available
     * - Ingredients list returned in requested language if available
     * - Categories returned in requested language if available
     * - Nutritional information is language-independent (same across languages)
     *
     * @param barcode Product barcode (EAN-13, UPC-A, etc., 8-14 digits)
     * @param language Language code for localized product information
     * @return Call containing ProductResponse with localized product data
     */
    @GET("product/{barcode}.json")
    Call<ProductResponse> getProduct(
            @Path("barcode") String barcode,
            @Query("lc") String language  // MANDATORY: Language for product data
    );

    /**
     * Get product with specific field selection and language
     *
     * Use this for optimized requests when you only need specific product fields.
     * Particularly useful for nutrition-only queries or image-focused requests.
     *
     * @param barcode Product barcode
     * @param fields Specific fields to retrieve (use Fields constants)
     * @param language Language for localized fields
     * @return Call containing ProductResponse with selected fields only
     */
    @GET("product/{barcode}.json")
    Call<ProductResponse> getProductWithFields(
            @Path("barcode") String barcode,
            @Query("fields") String fields,
            @Query("lc") String language  // MANDATORY: Language for localized fields
    );

    // ========== TAXONOMY OPERATIONS ==========

    /**
     * Get official category taxonomy with language support
     *
     * Downloads the complete OpenFoodFacts category taxonomy, which provides
     * the hierarchical structure of food categories in multiple languages.
     *
     * TAXONOMY STRUCTURE:
     * - Contains categories in many languages (en, fr, es, de, it, etc.)
     * - Includes parent-child relationships for hierarchy
     * - Provides synonyms and alternative names for better matching
     *
     * LANGUAGE BEHAVIOR:
     * The taxonomy contains ALL languages, but the "lc" parameter may influence
     * the priority of certain fields in the response.
     *
     * @param taxonomyUrl URL to taxonomy JSON (use TaxonomyUrls constants)
     * @param language Language preference for taxonomy data
     * @return Call containing JsonObject with complete taxonomy
     */
    @GET
    Call<JsonObject> getCategoryTaxonomy(
            @Url String taxonomyUrl,
            @Query("lc") String language  // Language preference for taxonomy
    );

    // ========== CONSTANTS AND CONFIGURATION ==========

    /**
     * Taxonomy URLs - Official OpenFoodFacts taxonomy endpoints
     */
    public static final class TaxonomyUrls {
        /**
         * Complete categories taxonomy with all languages and hierarchy
         * This is the recommended endpoint for comprehensive category data
         */
        public static final String CATEGORIES_JSON = "https://static.openfoodfacts.org/data/taxonomies/categories.json";

        /**
         * Simplified categories endpoint (legacy, use CATEGORIES_JSON instead)
         */
        public static final String CATEGORIES_SIMPLE = "https://world.openfoodfacts.org/categories.json";
    }

    // ========== RESPONSE WRAPPER CLASSES ==========

    /**
     * ProductResponse - Enhanced wrapper for product API responses
     *
     * OpenFoodFacts returns product data in a structured format with status information.
     * This class handles the response structure and provides convenient access methods.
     *
     * RESPONSE STRUCTURE:
     * {
     *   "status": 1,                    // 1 = found, 0 = not found
     *   "status_verbose": "product found",
     *   "product": { ... }              // OpenFoodFactsProduct DTO (not domain model!)
     * }
     *
     * IMPORTANT: The "product" field contains an OpenFoodFactsProduct DTO which must be
     * mapped to FoodProduct domain model using OpenFoodFactsMapper.
     */
    public static class ProductResponse {
        private int status;
        private String status_verbose;
        private OpenFoodFactsProduct product;  // ← CHANGED from FoodProduct to OpenFoodFactsProduct

        // Default constructor required by Gson
        public ProductResponse() {}

        // ========== GETTERS ==========

        /**
         * Get API response status code
         * @return 1 if product found, 0 if not found
         */
        public int getStatus() {
            return status;
        }

        /**
         * Get human-readable status message
         * @return Status description from API (may be localized)
         */
        public String getStatusVerbose() {
            return status_verbose;
        }

        /**
         * Get the raw product DTO from API
         * @return OpenFoodFactsProduct DTO (needs mapping to domain model), or null if not found
         */
        public OpenFoodFactsProduct getProduct() {  // ← CHANGED return type to OpenFoodFactsProduct
            return product;
        }

        // ========== SETTERS ==========

        public void setStatus(int status) {
            this.status = status;
        }

        public void setStatusVerbose(String status_verbose) {
            this.status_verbose = status_verbose;
        }

        public void setProduct(OpenFoodFactsProduct product) {  // ← CHANGED parameter type to OpenFoodFactsProduct
            this.product = product;
        }

        // ========== UTILITY METHODS ==========

        /**
         * Check if the API request was successful
         * @return true if product was found (status == 1)
         */
        public boolean isSuccess() {
            return status == 1;
        }

        /**
         * Check if product data is available
         * @return true if product object exists and is not null
         */
        public boolean hasProduct() {
            return product != null;
        }

        /**
         * Check if response contains valid, usable product data
         * @return true if successful response with valid product
         */
        public boolean isValid() {
            return isSuccess() && hasProduct();
        }

        @Override
        public String toString() {
            return String.format("ProductResponse{status=%d, hasProduct=%s}",
                    status, hasProduct());
        }
    }

    // ========== FIELD DEFINITIONS ==========

    /**
     * Field Sets - Language-aware field combinations for different use cases
     *
     * These field sets are optimized for language-aware requests and ensure
     * that localized data is properly retrieved from the API.
     */
    public static final class Fields {

        /**
         * Basic fields for search results and list views
         * Optimized for performance with essential localized data
         *
         * LANGUAGE BEHAVIOR: Product names and basic info in requested language
         */
        public static final String BASIC = OpenFoodFactsConstants.SEARCH_FIELDS;

        /**
         * Comprehensive fields for detailed product views
         * Includes all nutrition data, ingredients, and localized metadata
         *
         * LANGUAGE BEHAVIOR: All text fields prioritize requested language
         */
        public static final String DETAILED = OpenFoodFactsConstants.DETAIL_FIELDS;

        /**
         * Category-enhanced fields for proper taxonomy processing
         * Essential for the CategoryMatcher to work with localized categories
         *
         * INCLUDES: All category-related fields in multiple languages
         * - categories: Raw category string as entered by users
         * - categories_hierarchy: Processed category hierarchy
         * - categories_tags: Standardized category tags with language prefixes
         */
        public static final String CATEGORY_ENHANCED =
                "code,product_name,brands,image_url,nutriments,serving_size,serving_quantity," +
                        "nutrition_grades,categories,categories_hierarchy,categories_tags," +
                        "categories_tags_en,categories_tags_fr"; // Explicit language variants for categories

        /**
         * Nutrition-focused fields for dietary analysis
         * Language-independent nutritional data with minimal localized text
         *
         * LANGUAGE BEHAVIOR: Product name localized, nutrition data universal
         */
        public static final String NUTRITION_ONLY =
                "code,product_name,nutriments,serving_size,serving_quantity,nutrition_grades";

        /**
         * Extended basic fields with serving information
         * Good balance between performance and information completeness
         */
        public static final String EXTENDED_BASIC =
                OpenFoodFactsConstants.SEARCH_FIELDS + ",quantity,serving_size,serving_quantity";

        /**
         * Image-focused fields for visual product displays
         * Prioritizes image URLs with minimal localized text
         */
        public static final String IMAGE_FOCUS =
                "code,product_name,brands,image_url,image_front_url,image_nutrition_url";

        /**
         * Minimal fields for quick existence checks
         * Absolute minimum for verifying product exists with basic localized name
         */
        public static final String MINIMAL = "code,product_name";

        /**
         * Language-comprehensive category fields
         * All category-related fields across multiple languages for robust matching
         */
        public static final String MULTILINGUAL_CATEGORIES =
                "categories,categories_hierarchy,categories_tags," +
                        "categories_tags_en,categories_tags_fr,categories_tags_es,categories_tags_de";
    }

    // ========== DEFAULT VALUES ==========

    /**
     * Default Values - Updated for language-aware operations
     */
    public static final class Defaults {

        /**
         * Default page size for search operations
         * Balanced for performance and user experience
         */
        public static final int PAGE_SIZE = ApiConfig.API_PAGE_SIZE;

        /**
         * Maximum allowed page size (API limitation)
         */
        public static final int MAX_PAGE_SIZE = ApiConfig.API_MAX_PAGE_SIZE;

        /**
         * First page number (API uses 1-based indexing)
         */
        public static final int FIRST_PAGE = 1;

        /**
         * Default sort order for search results
         */
        public static final String SORT_BY = OpenFoodFactsConstants.DEFAULT_SORT_BY;

        /**
         * Default language fallback when user language unavailable
         * English is used as it has the most complete data coverage
         */
        public static final String FALLBACK_LANGUAGE = "en";

        /**
         * Secondary language fallback
         * French has the second-most complete data coverage
         */
        public static final String SECONDARY_FALLBACK_LANGUAGE = "fr";
    }

    // ========== SEARCH PARAMETERS ==========

    /**
     * Search Parameters - Language-aware parameter values
     */
    public static final class SearchParams {

        /**
         * Sort options for search results
         */
        public static final class SortBy {
            public static final String POPULARITY = "unique_scans_n";
            public static final String COMPLETENESS = "completeness";
            public static final String PRODUCT_NAME = "product_name";
            public static final String CREATED_DATE = "created_t";
            public static final String LAST_MODIFIED = "last_modified_t";
        }

        /**
         * Language codes for internationalization
         * These are the most commonly supported languages in OpenFoodFacts
         */
        public static final class Languages {
            public static final String ENGLISH = "en";
            public static final String FRENCH = "fr";
            public static final String SPANISH = "es";
            public static final String GERMAN = "de";
            public static final String ITALIAN = "it";
            public static final String PORTUGUESE = "pt";
            public static final String DUTCH = "nl";
            public static final String RUSSIAN = "ru";
            public static final String CHINESE = "zh";
            public static final String JAPANESE = "ja";
        }

        /**
         * Common search filters that work across languages
         */
        public static final class Filters {
            public static final String WITH_NUTRITION = "nutrition_grades_tags:a,b,c,d,e";
            public static final String WITH_IMAGES = "image_url:*";
            public static final String COMPLETE_PRODUCTS = "completeness:0.7";
        }
    }
}