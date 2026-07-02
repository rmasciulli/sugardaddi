package li.masciul.sugardaddi.data.sources.openfoodfacts.mappers;

/**
 * OffImageUrls - helpers for transforming Open Food Facts image URLs.
 *
 * OFF serves each selected image at fixed resolutions using the filename pattern
 * <name>.<rev>.<resolution>.jpg, where resolution is one of 100, 200, 400, or the
 * keyword "full" for the full-resolution original. The convenience fields the API
 * returns (image_front_url, image_url) point at the 400px "display" variant.
 *
 * For the cached hero we want the full-resolution original (ImageProfile.HERO then
 * bounds it to <= 1920px on disk), so we swap the trailing resolution segment for
 * "full". Thumbnails deliberately keep the small variant and are not transformed.
 */
final class OffImageUrls {

    /**
     * Trailing ".<digits>.jpg" resolution segment of an OFF image URL, e.g. the
     * ".400.jpg" in "front_en.6.400.jpg". The revision (".6.") is not matched
     * because it is not immediately followed by ".jpg".
     */
    private static final String RESOLUTION_SUFFIX = "\\.\\d+\\.jpg$";

    private OffImageUrls() { /* no instances */ }

    /**
     * Returns the full-resolution variant of an OFF image URL by replacing its
     * trailing ".<resolution>.jpg" segment with ".full.jpg".
     *
     * Safe and idempotent: a URL that does not match the expected pattern - already
     * ".full.jpg", a non-jpg, or anything not shaped like an OFF image URL - is
     * returned unchanged (replaceAll is a no-op when the pattern does not match).
     *
     * @param url An OFF image URL (e.g. .../front_en.6.400.jpg), or null.
     * @return The .../front_en.6.full.jpg variant, or the input unchanged if it does
     *         not match the resolution pattern.
     */
    static String toFullResolutionUrl(String url) {
        if (url == null || url.isEmpty()) {
            return url;
        }
        return url.replaceAll(RESOLUTION_SUFFIX, ".full.jpg");
    }
}