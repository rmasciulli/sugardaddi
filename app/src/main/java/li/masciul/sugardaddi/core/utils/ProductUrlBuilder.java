package li.masciul.sugardaddi.core.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import li.masciul.sugardaddi.core.models.SourceIdentifier;

/**
 * ProductUrlBuilder - The single, official resolver for a product's link to
 * its own page on the source's website. Every mapper (FatSecret, OFF,
 * Ciqual, USDA) calls resolveUrl() at mapping time and stores the result
 * into FoodProduct.sourceUrl - no mapper builds or checks a URL on its own.
 *
 * resolveUrl() prefers a real, API-provided URL when the mapper has one
 * (FatSecret's food_url, OFF's url, Ciqual's urlFr/urlEng - all more
 * accurate than a guessed pattern, and the only way to get a genuinely
 * correct result for Ciqual, whose real URL varies by language in a way
 * the computed CIQUAL_PRODUCT_URL_PATTERN below never could - that pattern
 * relies on the browser's own Accept-Language header, which cannot be
 * controlled from here). Falls back to a computed pattern only when no
 * API-provided value exists - USDA's only path, and a safety net for OFF/
 * Ciqual if their API value is ever missing for a specific item.
 *
 * SUPPORTED SOURCES (computed fallback):
 * - OpenFoodFacts (OFF)
 * - Ciqual (CIQUAL)
 * - USDA FoodData Central (USDA)
 *
 * USAGE (from a mapper):
 * ```java
 * String url = ProductUrlBuilder.resolveUrl(product.getSourceIdentifier(), apiProvidedUrlOrNull);
 * if (url != null) {
 *     product.setSourceUrl(url, language);
 * }
 * ```
 *
 * NOTES:
 * - Thread-safe (all methods are static and stateless)
 */
public final class ProductUrlBuilder {

    // ========== URL PATTERNS (computed fallback only) ==========

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
     * NOTE: Language is determined by browser settings - this pattern
     * cannot control language via URL parameters, which is exactly why
     * the real API-provided urlFr/urlEng are preferred whenever available.
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
     * Resolves the best available URL for a product - the one method every
     * mapper should call, rather than checking an API field or a computed
     * pattern independently.
     *
     * @param sourceIdentifier Product's source identifier - used for the
     *                         computed fallback; may be null (some sources
     *                         don't set one yet), in which case only the
     *                         API-provided value can be used.
     * @param apiProvidedUrl   The real URL from the source's own API
     *                         response, if the mapper has one; null or
     *                         blank if not.
     * @return The resolved URL, or null if neither an API value nor a
     *         computed pattern is available for this source.
     */
    @Nullable
    public static String resolveUrl(@Nullable SourceIdentifier sourceIdentifier,
                                    @Nullable String apiProvidedUrl) {
        if (apiProvidedUrl != null && !apiProvidedUrl.trim().isEmpty()) {
            return apiProvidedUrl.trim();
        }
        return getWebsiteUrl(sourceIdentifier);
    }

    /**
     * Computes the website URL for a product from a fixed pattern - the
     * fallback path used by resolveUrl() when no API-provided URL exists.
     * Prefer resolveUrl() from mapper code; this is kept public since
     * hasWebsiteSupport()/getWebsiteName() below build on it directly.
     *
     * @param sourceIdentifier Product's source identifier (contains prefix and ID)
     * @return Website URL, or null if source doesn't support website links
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
                return null;
        }
    }

    /**
     * Checks if a product source has any website link support at all -
     * computed or (if checked separately by the caller) API-provided.
     *
     * @param sourceIdentifier Product's source identifier
     * @return true if a computed website URL is available for this source
     */
    public static boolean hasWebsiteSupport(@Nullable SourceIdentifier sourceIdentifier) {
        return getWebsiteUrl(sourceIdentifier) != null;
    }

    /**
     * Gets the website display name for a data source.
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
            case "OPENFOODFACTS":
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
     * - The real API-provided "url" field is preferred (see OpenFoodFactsMapper) -
     *   this pattern is only the fallback if that's ever missing
     */

    /**
     * CIQUAL NOTES:
     * - Language cannot be controlled via this computed pattern's URL
     * - The real API-provided urlFr/urlEng are preferred and genuinely
     *   language-correct - this pattern is only the fallback
     * - SPA with client-side routing (#/aliments/31120)
     */

    /**
     * USDA NOTES:
     * - FoodData Central (FDC) is the main USDA database
     * - Uses FDC ID as identifier
     * - English only (no language selection)
     * - No API-provided URL field exists - this pattern is the only source
     */
}