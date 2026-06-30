package li.masciul.sugardaddi.utils.image;

/**
 * ImageProfile - named size + quality presets for image processing.
 *
 * WHY THIS EXISTS
 * ===============
 * Resizing/compression is fully described by two values that always travel
 * together: the maximum longest-edge dimension and the JPEG quality. Passing
 * them as a loose (int, int) pair through every layer (caller -> ImagePickerHelper
 * -> ImageProcessor, and now the downloader) duplicated the pairing and invited
 * mismatched values. Bundling them into one self-documenting type removes that
 * duplication and lets every call site read its own intent.
 *
 * SCOPE - SIZE AND QUALITY ONLY
 * =============================
 * A profile describes HOW BIG and HOW COMPRESSED, nothing about SHAPE. Aspect
 * ratio / orientation framing (landscape, portrait, square) is deliberately NOT
 * modelled here: it is a display-time concern handled by a Glide transform over
 * the full-frame image, so the focal-point feature can reframe a cached hero
 * without the pixels having been cropped away at save time.
 * Do NOT add crop/aspect members to this enum.
 *
 * "NO PROCESSING" IS NOT A PROFILE
 * ================================
 * Some downloads (CDN thumbnails) are already optimised and must be written to
 * disk byte-for-byte. That is expressed by passing a {@code null} ImageProfile
 * to the downloader (null == raw passthrough), never by a member of this enum.
 *
 * ZERO RUNTIME COST
 * =================
 * Members are singletons created once at class load; the fields are final ints.
 * There is no per-call allocation. Adding a new preset is a single line.
 *
 * USAGE
 * =====
 * <pre>
 *   ImageProcessor.process(source, dest, ImageProfile.HERO);
 *   imagePicker.showGallery(dest, ImageProfile.THUMBNAIL, callback);
 *   imageDownloader.download(url, heroFile,  ImageProfile.HERO, callback);
 *   imageDownloader.download(url, thumbFile, null,              callback); // raw
 * </pre>
 */
public enum ImageProfile {

    /**
     * Full-size hero images (product / recipe detail heroes, meal photos,
     * recipe step photos). A 4000x3000 original becomes <= 1920x1440.
     * Brings a ~10 MB JPEG to ~200-400 KB at this quality.
     */
    HERO(1920, 85),

    /**
     * User-defined thumbnail overrides shown at ~72dp in search cards.
     * 512px covers every current screen density (xxxhdpi 4x = 288px).
     * Slightly lower quality than HERO - imperceptible at thumbnail size.
     */
    THUMBNAIL(512, 78);

    /** Maximum length of the longest edge, in pixels, after downscaling. */
    public final int maxDimension;

    /** JPEG compression quality, 1-100. */
    public final int jpegQuality;

    ImageProfile(int maxDimension, int jpegQuality) {
        this.maxDimension = maxDimension;
        this.jpegQuality  = jpegQuality;
    }
}