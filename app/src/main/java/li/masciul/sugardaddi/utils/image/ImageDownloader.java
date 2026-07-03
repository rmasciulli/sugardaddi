package li.masciul.sugardaddi.utils.image;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * ImageDownloader - Downloads remote images to local disk (thumbnails raw, heroes processed).
 *
 * RESPONSIBILITIES
 * ================
 * - Accept a remote image URL, a caller-supplied destination File, and an
 *   optional {@link ImageProfile}
 * - Skip the download if the destination already exists on disk (deduplication)
 * - Stream the response bytes to a temporary sibling file, then either rename it
 *   onto the destination (raw passthrough) or run it through {@link ImageProcessor}
 *   into the destination (when a profile is given)
 * - Deliver the local file path (or an error) via callback on the main thread
 *
 * WHAT THIS CLASS DOES NOT DO
 * ============================
 * - No image processing / resizing   → see ImageProcessor (this class only invokes it)
 * - No disk path management logic    → see ImageStorageManager (the CALLER resolves paths)
 * - No database access               → see ImagePurgeManager
 * - No camera / gallery UI           → see ImagePickerHelper
 * - No Glide integration             → Glide loads remote URLs from the network;
 *                                       this class handles local persistence only
 *
 * DESIGN PRINCIPLE - CALLER OWNS THE PATH
 * ========================================
 * The caller constructs the destination File (via {@link ImageStorageManager})
 * before calling {@link #download}. This class is path-agnostic: it does not know
 * or decide whether the image is a thumbnail, a product hero, or a recipe hero -
 * the caller passes the resolved destination, exactly as ImagePickerHelper does.
 *
 * RAW vs PROCESSED
 * ================
 * - profile == null  → raw passthrough. The downloaded bytes are the final image
 *                      (e.g. a CDN thumbnail, already correctly sized). Streamed to
 *                      a temp file and atomically renamed onto the destination.
 * - profile != null  → processed. The downloaded bytes are the full-size original;
 *                      ImageProcessor resizes/recompresses them (per the profile)
 *                      into the destination. Use ImageProfile.HERO for hero images.
 *
 * WHY TEMP-THEN-RENAME
 * ====================
 * The destination is never written directly. A download interrupted mid-stream
 * would otherwise leave a partial file on the destination path, which the
 * deduplication check would later mistake for a complete image. By streaming to
 * "<name>.tmp" in the same directory and only producing the destination on
 * success (rename, or process-into-place), the destination is all-or-nothing:
 * if it exists and is non-empty, it is guaranteed complete.
 *
 * WHY NOT GLIDE FOR THIS
 * =======================
 * Glide's disk cache is opaque and managed entirely by Glide. We need a file at
 * a predictable, app-controlled path so that:
 *   - Room can persist the path alongside the favorite record
 *   - ImagePurgeManager can cross-reference disk files against Room at startup
 *   - Unfavouriting can delete the exact file immediately
 * Glide's cache provides none of these guarantees.
 *
 * THREADING
 * =========
 * Downloads (and the processing step, when a profile is given) run on a dedicated
 * single-thread executor, not the app's shared background pool. This keeps
 * downloads sequential to avoid saturating the network, and keeps the CPU-bound
 * processing off the UI and shared pools. Callbacks are always delivered on the
 * main thread.
 *
 * DEDUPLICATION
 * =============
 * If the caller-supplied destination already exists and is non-empty, the download
 * is skipped and the existing path is returned immediately - no network call.
 * Combined with temp-then-rename, an existing destination is always a complete image.
 *
 * USAGE (from a background thread or main thread - both are safe)
 * ================================================================
 * <pre>
 *   // Raw thumbnail (already CDN-sized): no processing.
 *   imageDownloader.download(
 *       thumbnailUrl,
 *       storageManager.getThumbnailFile("OFF:3017620422003"),
 *       null,
 *       callback);
 *
 *   // Full hero: resized/recompressed via ImageProfile.HERO.
 *   imageDownloader.download(
 *       imageUrl,
 *       storageManager.getProductHeroFile("OFF:3017620422003"),
 *       ImageProfile.HERO,
 *       callback);
 * </pre>
 */
public class ImageDownloader {

    private static final String TAG = "SugarDaddi_Images";

    /**
     * Buffer size for streaming response bytes to disk.
     * 8 KB is a common sweet spot - large enough to amortise syscall overhead,
     * small enough to avoid wasting memory on the stack.
     */
    private static final int BUFFER_SIZE_BYTES = 8 * 1024;

    /**
     * HTTP connect timeout. Applies to establishing the connection only, so it is
     * independent of image size.
     */
    private static final int CONNECT_TIMEOUT_SECONDS = 15;

    /**
     * HTTP read timeout. This is a per-read inactivity timeout, not a total-transfer
     * budget, so a steadily streaming multi-MB hero will not trip it as long as bytes
     * keep arriving within the window.
     */
    private static final int READ_TIMEOUT_SECONDS = 30;

    // =========================================================================
    // CALLBACK INTERFACE
    // =========================================================================

    /**
     * Callback for image download results.
     * Both methods are always called on the main thread.
     */
    public interface Callback {

        /**
         * Called when the image is available on disk (either freshly downloaded /
         * processed or already present from a previous download).
         *
         * @param localPath Absolute path to the local file. Never null.
         *                  Suitable for direct persistence in Room or
         *                  passing to {@code Glide.with(ctx).load(new File(localPath))}.
         */
        void onSuccess(@NonNull String localPath);

        /**
         * Called when the download fails for any reason (network error, HTTP error,
         * disk write failure, processing failure, unavailable storage, etc.).
         *
         * The failure is non-fatal: the remote URL remains available as a fallback
         * for display purposes. The caller should log and continue gracefully.
         *
         * @param reason Human-readable description of the failure. Never null.
         */
        void onError(@NonNull String reason);
    }

    // =========================================================================
    // STATE
    // =========================================================================

    /**
     * Used only by {@link #delete(File)} to route deletions through the same
     * logging/guards as the rest of the image layer. Downloads no longer resolve
     * paths here - the caller owns the destination.
     */
    private final ImageStorageManager storageManager;

    /**
     * Single-thread executor dedicated to downloads and their processing step.
     * Keeps work sequential to avoid saturating the network with parallel fetches
     * when the user favorites multiple items quickly.
     */
    private final ExecutorService downloadExecutor;

    /** Delivers callbacks on the main thread. */
    private final Handler mainHandler;

    /** Shared OkHttpClient - created once, reuses the connection pool. */
    private final OkHttpClient httpClient;

    // =========================================================================
    // CONSTRUCTOR
    // =========================================================================

    /**
     * @param storageManager The storage manager, used for delete routing.
     *                       Should be the application-scoped singleton instance.
     */
    public ImageDownloader(@NonNull ImageStorageManager storageManager) {
        this.storageManager   = storageManager;
        this.downloadExecutor = Executors.newSingleThreadExecutor();
        this.mainHandler      = new Handler(Looper.getMainLooper());
        this.httpClient       = buildHttpClient();
    }

    // =========================================================================
    // PUBLIC API
    // =========================================================================

    /**
     * Downloads the image at {@code remoteUrl} and saves it to {@code destination}.
     *
     * If {@code profile} is null the bytes are saved as-is (raw passthrough). If a
     * profile is given, the bytes are treated as a full-size original and
     * resized/recompressed via {@link ImageProcessor} into the destination.
     *
     * If the destination already exists and is non-empty, the download is skipped
     * and {@code callback} is notified with the existing path immediately (no
     * network call). The callback is always delivered on the main thread,
     * regardless of which thread this method is called from.
     *
     * @param remoteUrl   The remote image URL to download. Must be a valid HTTP/HTTPS URL.
     * @param destination The final file to produce. The CALLER resolves this via
     *                    {@link ImageStorageManager} (e.g. getThumbnailFile,
     *                    getProductHeroFile, getRecipeHeroFile).
     * @param profile     Processing preset, or null for raw passthrough.
     * @param callback    Result callback - never null. Called on the main thread.
     */
    public void download(
            @NonNull String remoteUrl,
            @NonNull File destination,
            @Nullable ImageProfile profile,
            @NonNull Callback callback) {

        downloadExecutor.execute(() -> {

            // ── 1. Deduplication ──────────────────────────────────────────────
            // Thanks to temp-then-rename below, an existing non-empty destination
            // is guaranteed to be a complete image: skip the network entirely.
            if (destination.exists() && destination.length() > 0) {
                Log.d(TAG, "Image already on disk, skipping download: "
                        + destination.getName());
                deliverSuccess(callback, destination.getAbsolutePath());
                return;
            }

            // ── 2. Ensure the destination directory exists ────────────────────
            File parent = destination.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                deliverError(callback, "Could not create directory for "
                        + destination.getName());
                return;
            }

            // ── 3. Stream bytes to a temporary sibling file ───────────────────
            // Never write the destination directly - see WHY TEMP-THEN-RENAME.
            File temp = new File(parent, destination.getName() + ".tmp");

            Log.d(TAG, "Downloading image: " + remoteUrl
                    + " → " + destination.getName());

            Request request = new Request.Builder()
                    .url(remoteUrl)
                    // Identify the app politely - same convention as other HTTP calls.
                    .header("User-Agent", "SugarDaddi/1.0 (Android; image download)")
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {

                // ── 4. Validate HTTP response ──────────────────────────────────
                if (!response.isSuccessful()) {
                    deliverError(callback, "HTTP " + response.code()
                            + " downloading image from " + remoteUrl);
                    return;
                }

                ResponseBody body = response.body();
                if (body == null) {
                    deliverError(callback, "Empty response body for image URL: " + remoteUrl);
                    return;
                }

                // ── 5. Stream bytes to the temp file ───────────────────────────
                if (!streamToDisk(body, temp)) {
                    if (temp.exists()) temp.delete();
                    deliverError(callback, "Failed to write image to disk: "
                            + destination.getName());
                    return;
                }

                // ── 6. Finalise: raw rename, or process-into-place ─────────────
                if (!finalise(temp, destination, profile)) {
                    // finalise() has already cleaned up temp and any partial dest.
                    deliverError(callback, "Failed to finalise image: "
                            + destination.getName());
                    return;
                }

                Log.d(TAG, "Image saved: " + destination.getName()
                        + " (" + destination.length() / 1024 + " KB)");
                deliverSuccess(callback, destination.getAbsolutePath());

            } catch (IOException e) {
                // Network failure or interrupted stream - clean up the temp file.
                if (temp.exists()) temp.delete();
                Log.w(TAG, "Network error downloading image from " + remoteUrl
                        + ": " + e.getMessage());
                deliverError(callback, "Network error: " + e.getMessage());
            }
        });
    }

    /**
     * Deletes a locally cached image file, if it exists.
     *
     * This is the mirror of {@link #download}: call it when the user unfavorites
     * an item, passing the same destination File that was downloaded (resolved via
     * {@link ImageStorageManager}). Works for any cached image - thumbnail or hero -
     * because the caller owns the path.
     *
     * Runs on the download executor. Fire-and-forget: no callback, failures are
     * logged but not propagated, since {@code ImagePurgeManager} will reclaim any
     * orphan at the next startup anyway.
     *
     * @param file The cached file to delete. The caller resolves it via
     *             ImageStorageManager (e.g. getThumbnailFile / getProductHeroFile).
     */
    public void delete(@NonNull File file) {
        downloadExecutor.execute(() ->
                storageManager.deleteFile(file.getAbsolutePath()));
    }

    /**
     * Shuts down the download executor cleanly.
     *
     * In-flight downloads are allowed to complete (up to 5 seconds) before
     * the executor is terminated. Call this from your Application.onTerminate()
     * or from the owning component's cleanup method.
     */
    public void shutdown() {
        downloadExecutor.shutdown();
        try {
            if (!downloadExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                downloadExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            downloadExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================

    /**
     * Finalises a freshly downloaded temp file into its destination.
     *
     * If {@code profile} is null the temp file is the final artifact and is
     * atomically renamed onto the destination (raw passthrough). If a profile is
     * given, the temp file is the unprocessed original: {@link ImageProcessor}
     * reads it and writes the resized/recompressed result to the destination,
     * after which the temp is removed.
     *
     * Either way the destination only comes into existence once the artifact is
     * complete, so the deduplication check in {@link #download} can trust any
     * existing destination to be whole.
     *
     * @param temp        The downloaded bytes (raw original). Always removed by the
     *                    time this method returns.
     * @param destination The final file to produce.
     * @param profile     Processing preset, or null for raw passthrough.
     * @return true on success (destination now exists, temp removed); false on
     *         failure (temp and any partial destination removed).
     */
    private boolean finalise(@NonNull File temp,
                             @NonNull File destination,
                             @Nullable ImageProfile profile) {
        if (profile == null) {
            // Raw passthrough: the temp IS the final image. Rename is atomic
            // within the same directory (same filesystem).
            if (temp.renameTo(destination)) {
                return true;
            }
            Log.e(TAG, "Could not rename temp onto destination: "
                    + destination.getName());
            if (temp.exists()) temp.delete();
            return false;
        }

        // Processed: decode the temp original, write the resized result to dest.
        File result = ImageProcessor.process(temp, destination, profile);

        // The original temp is no longer needed regardless of outcome.
        if (temp.exists()) temp.delete();

        if (result != null) {
            return true;
        }

        // Processing failed: leave nothing half-written behind.
        if (destination.exists()) destination.delete();
        Log.e(TAG, "ImageProcessor failed for " + destination.getName());
        return false;
    }

    /**
     * Builds the OkHttpClient used for all image downloads.
     *
     * Intentionally does NOT reuse the app's NetworkManager/NetworkClient clients:
     * - Those clients inject source-specific User-Agent headers and retry interceptors
     *   designed for JSON APIs, not binary downloads
     * - A dedicated client with tight timeouts is more appropriate here
     * - Images do not go through Retrofit - we need raw byte streaming
     */
    @NonNull
    private OkHttpClient buildHttpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();
    }

    /**
     * Streams the response body bytes to the given file.
     *
     * Uses a fixed-size byte buffer to avoid loading the entire image into memory.
     * The output stream is always closed in the finally block.
     *
     * @param body        OkHttp response body to read from.
     * @param destination File to write to. Parent directory must already exist.
     * @return true if all bytes were written successfully.
     */
    private boolean streamToDisk(@NonNull ResponseBody body, @NonNull File destination) {
        FileOutputStream out = null;
        InputStream in = body.byteStream();

        try {
            out = new FileOutputStream(destination);
            byte[] buffer = new byte[BUFFER_SIZE_BYTES];
            int bytesRead;

            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
            out.flush();
            return true;

        } catch (IOException e) {
            Log.e(TAG, "Error streaming image to disk: " + destination.getName(), e);
            return false;

        } finally {
            // Always close the output stream. The response body (and its input stream)
            // is closed by the try-with-resources block in download().
            if (out != null) {
                try { out.close(); } catch (IOException ignored) {}
            }
        }
    }

    /**
     * Posts a success callback to the main thread.
     *
     * @param callback  The callback to notify.
     * @param localPath The absolute local file path to pass to the callback.
     */
    private void deliverSuccess(@NonNull Callback callback, @NonNull String localPath) {
        mainHandler.post(() -> callback.onSuccess(localPath));
    }

    /**
     * Posts an error callback to the main thread and logs the reason.
     *
     * @param callback The callback to notify.
     * @param reason   Human-readable error description.
     */
    private void deliverError(@NonNull Callback callback, @NonNull String reason) {
        Log.w(TAG, "ImageDownloader error: " + reason);
        mainHandler.post(() -> callback.onError(reason));
    }
}
