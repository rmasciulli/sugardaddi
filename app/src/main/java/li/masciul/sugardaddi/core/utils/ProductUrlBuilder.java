package li.masciul.sugardaddi.core.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import li.masciul.sugardaddi.core.models.SourceIdentifier;

/**
 * ProductUrlBuilder - Centralized URL generation for product websites
 *
 * Builds external website URLs for food products from different data sources.
 * Each data source has its own URL pattern and requirements.
 *
 * SUPPORTED SOURCES:
 * - OpenFoodFacts (OFF): Product pages with full details and images
 * - Ciqual (CIQUAL): Limited support (browser language only)
 * - USDA FoodData Central (USDA): Product details pages
 *
 * USAGE:
 * ```java
 * SourceIdentifier sourceId = product.getSourceIdentifier();
 * String url = ProductUrlBuilder.getWebsiteUrl(sourceId);
 *
 * if (url != null) {
 *     Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
 *     startActivity(intent);
 * }
 * ```
 *
 * NOTES:
 * - Returns null if source doesn't support website links
 * - URLs are built on-the-fly (not stored in database)
 * - Thread-safe (all methods are static and stateless)
 *
 * @author SugarDaddi Team
 * @version 1.0
 */
public final class ProductUrlBuilder {

    // ========== URL PATTERNS ==========

    /**
     * OpenFoodFacts product page URL pattern
     * Example: https://world.openfoodfacts.org/product/3017620422003
     */
    private static final String OFF_PRODUCT_URL_PATTERN =
            "https://world.openfoodfacts.org/product/%s";

    /**
     * Ciqual product page URL pattern
     * Example: https://ciqual.anses.fr/#/aliments/31120
     *
     * NOTE: Language is determined by browser settings
     *       We cannot control language via URL parameters
     */
    private static final String CIQUAL_PRODUCT_URL_PATTERN =
            "https://ciqual.anses.fr/#/aliments/%s";

    /**
     * USDA FoodData Central product page URL pattern
     * Example: https://fdc.nal.usda.gov/fdc-app.html#/food-details/789067
     */
    private static final String USDA_PRODUCT_URL_PATTERN =
            "https://fdc.nal.usda.gov/fdc-app.html#/food-details/%s";

    // Private constructor - utility class
    private ProductUrlBuilder() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    // ========== PUBLIC API ==========

    /**
     * Gets the website URL for a product
     *
     * @param sourceIdentifier Product's source identifier (contains prefix and ID)
     * @return Website URL, or null if source doesn't support website links
     *
     * @example
     * SourceIdentifier offId = new SourceIdentifier("OFF", "3017620422003");
     * String url = ProductUrlBuilder.getWebsiteUrl(offId);
     * // Returns: "https://world.openfoodfacts.org/product/3017620422003"
     */
    @Nullable
    public static String getWebsiteUrl(@Nullable SourceIdentifier sourceIdentifier) {
        if (sourceIdentifier == null) {
            return null;
        }

        String sourceId = sourceIdentifier.getSourceId();
        String originalId = sourceIdentifier.getOriginalId();

        if (sourceId == null || originalId == null || originalId.trim().isEmpty()) {
            return null;
        }

        switch (sourceId) {
            case "OPENFOODFACTS":
                return String.format(OFF_PRODUCT_URL_PATTERN, originalId);

            case "CIQUAL":
                return String.format(CIQUAL_PRODUCT_URL_PATTERN, originalId);

            case "USDA":
                return String.format(USDA_PRODUCT_URL_PATTERN, originalId);

            default:
                // Unknown source - no website URL available
                return null;
        }
    }

    /**
     * Checks if a product source supports website links
     *
     * @param sourceIdentifier Product's source identifier
     * @return true if website URL is available for this source
     */
    public static boolean hasWebsiteSupport(@Nullable SourceIdentifier sourceIdentifier) {
        return getWebsiteUrl(sourceIdentifier) != null;
    }

    /**
     * Gets the website display name for a data source
     * Used for showing "View on OpenFoodFacts" vs "View on Ciqual"
     *
     * @param sourceIdentifier Product's source identifier
     * @return Human-readable website name, or null if no website support
     */
    @Nullable
    public static String getWebsiteName(@Nullable SourceIdentifier sourceIdentifier) {
        if (sourceIdentifier == null) {
            return null;
        }

        switch (sourceIdentifier.getSourceId()) {
            case "OPENFOODFACT":
                return "OpenFoodFacts";

            case "CIQUAL":
                return "Ciqual";

            case "USDA":
                return "USDA FoodData Central";

            default:
                return null;
        }
    }

    // ========== SOURCE-SPECIFIC NOTES ==========

    /**
     * OPENFOODFACTS NOTES:
     * - World site (world.openfoodfacts.org) supports all languages
     * - URL uses barcode as identifier
     * - Rich content: images, ingredients, allergens, etc.
     * - Community-contributed data
     */

    /**
     * CIQUAL NOTES:
     * - Language cannot be controlled via URL
     * - Browser's Accept-Language header determines display language
     * - French phone → French page
     * - English phone → English page
     * - No way to force specific language from app
     * - SPA with client-side routing (#/aliments/31120)
     */

    /**
     * USDA NOTES:
     * - FoodData Central (FDC) is the main USDA database
     * - Uses FDC ID as identifier
     * - English only (no language selection)
     * - Comprehensive nutrition data
     * - US-centric food products
     */
}