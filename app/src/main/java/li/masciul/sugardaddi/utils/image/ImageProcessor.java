package li.masciul.sugardaddi.utils.image;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.exifinterface.media.ExifInterface;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * ImageProcessor - Pure image resizing and JPEG compression utility.
 *
 * RESPONSIBILITIES
 * ================
 * - Decode a source image file into a Bitmap (with memory-efficient sampling)
 * - Read and apply EXIF orientation so photos from camera/gallery are never upside-down
 * - Downscale the image so its longest side does not exceed a configurable maximum
 * - Compress and write the result to a destination File as JPEG
 *
 * WHAT THIS CLASS DOES NOT DO
 * ============================
 * - No network operations     → see ImageDownloader
 * - No disk path management   → see ImageStorageManager
 * - No database access        → see ImagePurgeManager
 * - No camera/gallery UI      → see ImagePickerHelper
 * - No cropping UI            → handled by uCrop (launched from ImagePickerHelper)
 *
 * WHY A SEPARATE CLASS
 * ====================
 * Image processing is CPU-bound and has no Android UI dependency beyond the
 * BitmapFactory / ExifInterface APIs. Keeping it isolated makes it straightforward
 * to call from any background thread and easy to unit-test.
 *
 * USAGE (from a background thread)
 * ==================================
 * <pre>
 *   File source      = ucropOutputFile;          // file produced by uCrop
 *   File destination = storageManager.getMealPhotoFile(uuid + ".jpg");
 *   File result = ImageProcessor.process(source, destination, ImageProfile.HERO);
 *   if (result != null) {
 *       // result == destination, ready to persist path in Room
 *   }
 * </pre>
 *
 * THREADING
 * =========
 * All methods in this class are blocking and must be called from a background thread.
 * They are safe to call concurrently (no shared mutable state).
 *
 * EXIF ORIENTATION
 * ================
 * Android cameras embed orientation in EXIF metadata rather than rotating the pixel
 * data. BitmapFactory.decodeFile() does NOT apply this rotation automatically.
 * Without correction, portrait photos appear rotated 90° in ImageViews.
 * readExifRotation() + applyRotation() correct this before writing the output.
 */
public final class ImageProcessor {

    private static final String TAG = "SugarDaddi_Images";

    // =========================================================================
    // CONSTRUCTOR - utility class, not instantiable
    // =========================================================================

    private ImageProcessor() {
        throw new UnsupportedOperationException("ImageProcessor is a utility class");
    }

    // =========================================================================
    // PUBLIC API
    // =========================================================================

    /**
     * Processes a source image file: corrects EXIF orientation, downscales if
     * the longest side exceeds {@code maxDimension}, compresses as JPEG at
     * {@code jpegQuality}, and writes the result to {@code destination}.
     *
     * The source and destination may be the same file - the bitmap is fully
     * decoded into memory before the output stream is opened, so there is no
     * risk of corrupting the source during writing.
     *
     * @param source       The input image file (JPEG, PNG, WebP - anything
     *                     BitmapFactory can decode). Must exist and be readable.
     * @param destination  The output file to write the processed JPEG into.
     *                     The parent directory must already exist. ImagePickerHelper
     *                     ensures this via ensureFileExists() before calling.
     * @param profile     The size + quality preset to apply. If the source is
     *                    already smaller than the profile's maxDimension, no
     *                    scaling is applied but EXIF correction and recompression
     *                    still occur.
     * @return The destination File on success, or {@code null} if processing failed.
     *         On failure the destination file may be empty or absent.
     */
    @Nullable
    public static File process(
            @NonNull File source,
            @NonNull File destination,
            @NonNull ImageProfile profile) {

        // Destructure the profile into the two values the body below already uses.
        // Keeping these locals means the processing logic is untouched by the
        // switch from loose ints to a profile object.
        final int maxDimension = profile.maxDimension;
        final int jpegQuality  = profile.jpegQuality;

        // ── 1. Decode source dimensions without loading pixels ────────────────
        // BitmapFactory.Options.inJustDecodeBounds skips pixel allocation and
        // only fills outWidth / outHeight. We use this to compute an inSampleSize
        // that halves the bitmap in memory before we do the final fine-scale.
        BitmapFactory.Options boundsOptions = new BitmapFactory.Options();
        boundsOptions.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(source.getAbsolutePath(), boundsOptions);

        if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) {
            Log.e(TAG, "Cannot read image dimensions from: " + source.getAbsolutePath());
            return null;
        }

        int sourceWidth  = boundsOptions.outWidth;
        int sourceHeight = boundsOptions.outHeight;
        Log.d(TAG, "Source image dimensions: " + sourceWidth + "×" + sourceHeight);

        // ── 2. Compute power-of-two sample size for initial decode ───────────
        // inSampleSize=2 halves each dimension (quarter pixels), =4 quarters, etc.
        // We pick the largest power-of-two that still gives us ≥ maxDimension on
        // the longest side so the subsequent Matrix scale never has to upscale.
        int sampleSize = computeSampleSize(sourceWidth, sourceHeight, maxDimension);
        Log.d(TAG, "Using inSampleSize=" + sampleSize);

        // ── 3. Decode with sampling ──────────────────────────────────────────
        BitmapFactory.Options decodeOptions = new BitmapFactory.Options();
        decodeOptions.inSampleSize = sampleSize;

        Bitmap sampled = BitmapFactory.decodeFile(source.getAbsolutePath(), decodeOptions);
        if (sampled == null) {
            Log.e(TAG, "BitmapFactory failed to decode: " + source.getAbsolutePath());
            return null;
        }

        // ── 4. Read and apply EXIF orientation ───────────────────────────────
        // Camera apps write orientation as EXIF metadata rather than rotating pixels.
        // We must rotate the bitmap ourselves before writing the output.
        int rotation = readExifRotation(source);
        Bitmap oriented = applyRotation(sampled, rotation);
        // applyRotation returns the same bitmap if rotation == 0 (no allocation).

        // ── 5. Fine-scale to target dimension ───────────────────────────────
        // The sampled bitmap may still be larger than maxDimension (inSampleSize
        // only uses powers of two). One createScaledBitmap call gets it exact.
        Bitmap scaled = scaleToMaxDimension(oriented, maxDimension);
        // scaleToMaxDimension returns the same bitmap if no scaling is needed.

        Log.d(TAG, "Final dimensions: " + scaled.getWidth() + "×" + scaled.getHeight());

        // ── 6. Write JPEG to destination ─────────────────────────────────────
        boolean written = writeJpeg(scaled, destination, jpegQuality);

        // ── 7. Recycle intermediate bitmaps to free native memory ────────────
        // Android's garbage collector does not immediately free Bitmap native memory.
        // Explicit recycle() is the correct pattern when the bitmap is no longer needed.
        if (scaled   != oriented) scaled.recycle();
        if (oriented != sampled)  oriented.recycle();
        sampled.recycle();

        if (!written) {
            Log.e(TAG, "Failed to write processed image to: " + destination.getAbsolutePath());
            return null;
        }

        Log.d(TAG, "Image processed successfully → " + destination.getAbsolutePath()
                + " (" + destination.length() / 1024 + " KB)");
        return destination;
    }

    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================

    /**
     * Computes the largest power-of-two inSampleSize such that the decoded bitmap
     * is still at least {@code targetDimension} on its longest side.
     *
     * Example: source = 4032×3024, target = 1920
     *   longest = 4032, 4032/2=2016 ≥ 1920 → sampleSize=2 ✓
     *             4032/4=1008 < 1920 → don't go to 4
     *   result: sampleSize=2  (decoded at 2016×1512, then Matrix scales to ≤1920)
     *
     * @param sourceWidth   Original image width in pixels.
     * @param sourceHeight  Original image height in pixels.
     * @param targetDimension  Maximum desired dimension (longest side) in pixels.
     * @return inSampleSize value (always ≥ 1).
     */
    private static int computeSampleSize(int sourceWidth, int sourceHeight, int targetDimension) {
        int longest = Math.max(sourceWidth, sourceHeight);
        int sampleSize = 1;

        // Keep halving until the next halving would bring us below the target.
        while ((longest / (sampleSize * 2)) >= targetDimension) {
            sampleSize *= 2;
        }
        return sampleSize;
    }

    /**
     * Reads the EXIF orientation tag from the given image file and converts it
     * to a clockwise rotation angle in degrees.
     *
     * Returns 0 if the file has no EXIF data, no orientation tag, or if reading fails.
     *
     * @param file The image file to read EXIF data from.
     * @return Clockwise rotation in degrees: 0, 90, 180, or 270.
     */
    private static int readExifRotation(@NonNull File file) {
        try {
            ExifInterface exif = new ExifInterface(file.getAbsolutePath());
            int orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL);

            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:  return 90;
                case ExifInterface.ORIENTATION_ROTATE_180: return 180;
                case ExifInterface.ORIENTATION_ROTATE_270: return 270;
                default:                                   return 0;
            }
        } catch (IOException e) {
            // Non-fatal - EXIF is best-effort. Photos without EXIF (e.g. gallery PNG)
            // are assumed to be correctly oriented.
            Log.w(TAG, "Could not read EXIF orientation from " + file.getName()
                    + " - assuming 0°: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Applies a clockwise rotation to the bitmap using a Matrix transformation.
     *
     * Returns the original bitmap unchanged if {@code degrees} is 0 - no new
     * bitmap is allocated in that case. For non-zero rotations, the original
     * bitmap is NOT recycled here; the caller is responsible for recycling it
     * once it has finished using the returned bitmap.
     *
     * @param bitmap  The source bitmap to rotate.
     * @param degrees Clockwise rotation in degrees (0, 90, 180, 270).
     * @return A rotated bitmap, or the original if no rotation is needed.
     */
    @NonNull
    private static Bitmap applyRotation(@NonNull Bitmap bitmap, int degrees) {
        if (degrees == 0) return bitmap;

        Matrix matrix = new Matrix();
        matrix.postRotate(degrees);

        Bitmap rotated = Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);

        Log.d(TAG, "Applied EXIF rotation: " + degrees + "°");
        return rotated;
    }

    /**
     * Scales the bitmap down so its longest side is ≤ {@code maxDimension}.
     *
     * Maintains the original aspect ratio exactly.
     * Returns the original bitmap unchanged if it already fits within the limit -
     * no new bitmap is allocated in that case.
     *
     * @param bitmap       The source bitmap.
     * @param maxDimension Maximum allowed size of the longest edge in pixels.
     * @return A scaled bitmap, or the original if no scaling is needed.
     */
    @NonNull
    private static Bitmap scaleToMaxDimension(@NonNull Bitmap bitmap, int maxDimension) {
        int width  = bitmap.getWidth();
        int height = bitmap.getHeight();
        int longest = Math.max(width, height);

        if (longest <= maxDimension) {
            // Already within bounds - return as-is, no allocation.
            return bitmap;
        }

        // Scale factor: shrink so longest side == maxDimension exactly.
        float scale = (float) maxDimension / longest;
        int targetWidth  = Math.round(width  * scale);
        int targetHeight = Math.round(height * scale);

        Bitmap scaled = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true);
        Log.d(TAG, "Scaled: " + width + "×" + height
                + " → " + targetWidth + "×" + targetHeight);
        return scaled;
    }

    /**
     * Writes the given bitmap to a file as JPEG at the given quality.
     *
     * The output stream is always closed in the finally block, whether or not
     * compression succeeded. Returns false and logs if any I/O error occurs.
     *
     * @param bitmap      The bitmap to compress.
     * @param destination The output file. Parent directory must already exist.
     * @param quality     JPEG quality (1–100).
     * @return true if the file was written successfully.
     */
    private static boolean writeJpeg(@NonNull Bitmap bitmap,
                                     @NonNull File destination,
                                     int quality) {
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(destination);
            // Bitmap.compress() returns false if compression fails (very rare).
            boolean success = bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out);
            out.flush();
            return success;
        } catch (IOException e) {
            Log.e(TAG, "Failed to write JPEG to " + destination.getAbsolutePath(), e);
            return false;
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException ignored) {
                    // Nothing meaningful to do if close() fails after a successful write.
                }
            }
        }
    }
}