package li.masciul.sugardaddi.data.sources.openfoodfacts;

/**
 * OpenFoodFactsConstants - OpenFoodFacts-specific configuration
 *
 * Contains all constants specific to the OpenFoodFacts data source.
 * Separated from ApiConfig for better organization and maintainability.
 */
public final class OpenFoodFactsConstants {

    // ========== DATA SOURCE METADATA ==========
    public static final String SOURCE_ID = "OPENFOODFACTS";
    public static final String SOURCE_NAME = "OpenFoodFacts";
    public static final String ATTRIBUTION = "Data from OpenFoodFacts.org";

    // ========== API CONFIGURATION ==========
    public static final String BASE_URL = "https://world.openfoodfacts.org/api/";
    public static final String API_VERSION = "v2";
    public static final String USER_AGENT = "SugarDaddi/1.0 (Android App)";

    /**
     * Get the full versioned base URL
     */
    public static final String getVersionedBaseUrl() {
        return BASE_URL + API_VERSION + "/";
    }

    // ========== FIELD DEFINITIONS ==========
    /**
     * Field definitions for API requests
     * These specify which data fields to retrieve from the API
     */
    public static final String SEARCH_FIELDS =
            "code,product_name,brands,categories," +
                    "image_url,image_front_url,image_small_url,image_front_small_url," +
                    "nutrition_grade,ecoscore_grade," +
                    "serving_size,serving_quantity";

    public static final String DETAIL_FIELDS =
            "code,product_name,brands," +
                    "image_url,image_front_url,image_nutrition_url," +
                    "nutrition_grade,ecoscore_grade," +
                    "nutriments,ingredients_text,allergens,traces," +
                    "serving_size,serving_quantity,quantity," +
                    "packaging,manufacturing_places,origins,stores," +
                    "categories,categories_hierarchy,categories_tags,categories_lc";

    // ========== API BEHAVIOR ==========
    /**
     * Default sorting and language preferences for OFF API
     */
    public static final String DEFAULT_SORT_BY = "popularity_key";
    public static final String DEFAULT_LANGUAGE = "en";

    // ========== CONSTRUCTOR ==========
    /**
     * Private constructor to prevent instantiation
     */
    private OpenFoodFactsConstants() {
        throw new UnsupportedOperationException("OpenFoodFactsConstants is a utility class and cannot be instantiated");
    }
}