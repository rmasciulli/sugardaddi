package li.masciul.sugardaddi.utils.image;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

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
 * ThumbnailDownloader - Downloads remote thumbnail images to local disk.
 *
 * RESPONSIBILITIES
 * ================
 * - Accept a remote image URL and a source-qualified item ID
 * - Resolve the destination path via {@link ImageStorageManager}
 * - Skip the download if the file already exists on disk (natural deduplication)
 * - Stream the response bytes directly to disk (no Bitmap allocation - thumbnails
 *   from CDNs are already small and require no resizing)
 * - Deliver the local file path (or an error) via callback on the main thread
 *
 * WHAT THIS CLASS DOES NOT DO
 * ============================
 * - No image processing / resizing   → see ImageProcessor
 * - No disk path management logic    → see ImageStorageManager
 * - No database access               → see ImagePurgeManager
 * - No camera / gallery UI           → see ImagePickerHelper
 * - No Glide integration             → Glide loads remote URLs from the network;
 *                                       this class handles local persistence only
 *
 * WHY NOT GLIDE FOR THIS
 * =======================
 * Glide's disk cache is opaque and managed entirely by Glide. We need a file at
 * a predictable, app-controlled path so that:
 *   - Room can persist the path alongside the favourite record
 *   - ImagePurgeManager can cross-reference disk files against Room at startup
 *   - Unfavouriting can delete the exact file immediately
 * Glide's cache provides none of these guarantees.
 *
 * THREADING
 * =========
 * Downloads run on a dedicated single-thread executor (not the app's shared
 * background pool) to avoid starving search / database operations.
 * Callbacks are always delivered on the main thread.
 *
 * DEDUPLICATION
 * =============
 * Filenames are derived from the source-qualified item ID (e.g. "OFF:3017620422003"
 * → "OFF_3017620422003.jpg") via {@link ImageStorageManager#getThumbnailFile(String)}.
 * If the file already exists, the download is skipped and the existing path is
 * returned immediately - no network call is made.
 *
 * USAGE (from a background thread or main thread - both are safe)
 * ================================================================
 * <pre>
 *   thumbnailDownloader.download(
 *       "https://images.openfoodfacts.org/images/products/.../front_fr.200.jpg",
 *       "OFF:3017620422003",
 *       new ThumbnailDownloader.Callback() {
 *           {@literal @}Override
 *           public void onSuccess(String localPath) {
 *               // Persist localPath in Room, then reload the list item
 *           }
 *           {@literal @}Override
 *           public void onError(String reason) {
 *               // Log and ignore - the remote URL is still available as fallback
 *           }
 *       });
 * </pre>
 */
public class ThumbnailDownloader {

    private static final String TAG = "SugarDaddi_Images";

    /**
     * Buffer size for streaming response bytes to disk.
     * 8 KB is a common sweet spot - large enough to amortise syscall overhead,
     * small enough to avoid wasting memory on the stack.
     */
    private static final int BUFFER_SIZE_BYTES = 8 * 1024;

    /**
     * HTTP connect timeout for thumbnail downloads.
     * Thumbnails are small (~5–50 KB) so a tight timeout is acceptable.
     */
    private static final int CONNECT_TIMEOUT_SECONDS = 15;

    /**
     * HTTP read timeout for thumbnail downloads.
     * Should complete well within 30 s even on a slow connection.
     */
    private static final int READ_TIMEOUT_SECONDS = 30;

    // =========================================================================
    // CALLBACK INTERFACE
    // =========================================================================

    /**
     * Callback for thumbnail download results.
     * Both methods are always called on the main thread.
     */
    public interface Callback {

        /**
         * Called when the thumbnail is available on disk (either freshly
         * downloaded or already present from a previous download).
         *
         * @param localPath Absolute path to the local file. Never null.
         *                  Suitable for direct persistence in Room or
         *                  passing to {@code Glide.with(ctx).load(new File(localPath))}.
         */
        void onSuccess(@NonNull String localPath);

        /**
         * Called when the download fails for any reason (network error, HTTP error,
         * disk write failure, unavailable external storage, etc.).
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

    private final ImageStorageManager storageManager;

    /**
     * Single-thread executor dedicated to thumbnail downloads.
     * Keeps downloads sequential to avoid saturating the network with
     * parallel thumbnail fetches when the user favourites multiple items quickly.
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
     * @param storageManager The storage manager used to resolve thumbnail paths.
     *                       Should be the application-scoped singleton instance.
     */
    public ThumbnailDownloader(@NonNull ImageStorageManager storageManager) {
        this.storageManager   = storageManager;
        this.downloadExecutor = Executors.newSingleThreadExecutor();
        this.mainHandler      = new Handler(Looper.getMainLooper());
        this.httpClient       = buildHttpClient();
    }

    // =========================================================================
    // PUBLIC API
    // =========================================================================

    /**
     * Downloads the thumbnail at {@code remoteUrl} and saves it to the
     * {@code cache/thumbnails/} directory under the filename derived from
     * {@code sourceQualifiedId}.
     *
     * If the file is already on disk, the download is skipped and {@code callback}
     * is notified with the existing path immediately (no network call).
     *
     * The callback is always delivered on the main thread, regardless of which
     * thread this method is called from.
     *
     * @param remoteUrl         The remote image URL to download. Must be non-null
     *                          and a valid HTTP/HTTPS URL.
     * @param sourceQualifiedId The item's source-qualified ID, e.g. "OFF:3017620422003".
     *                          Used to construct the deterministic filename.
     * @param callback          Result callback - never null. Called on the main thread.
     */
    public void download(
            @NonNull String remoteUrl,
            @NonNull String sourceQualifiedId,
            @NonNull Callback callback) {

        downloadExecutor.execute(() -> {

            // ── 1. Resolve destination path ───────────────────────────────────
            File destination = storageManager.getThumbnailFile(sourceQualifiedId);
            if (destination == null) {
                // External storage is unavailable (e.g. SD card removed).
                deliverError(callback, "External storage unavailable - cannot save thumbnail");
                return;
            }

            // ── 2. Check for existing file (deduplication) ────────────────────
            if (destination.exists() && destination.length() > 0) {
                // File already present - skip network call.
                Log.d(TAG, "Thumbnail already on disk, skipping download: "
                        + destination.getName());
                deliverSuccess(callback, destination.getAbsolutePath());
                return;
            }

            // ── 3. Build and execute HTTP request ─────────────────────────────
            Log.d(TAG, "Downloading thumbnail: " + remoteUrl
                    + " → " + destination.getName());

            Request request = new Request.Builder()
                    .url(remoteUrl)
                    // Identify the app politely - same convention as other HTTP calls
                    .header("User-Agent", "SugarDaddi/1.0 (Android; thumbnail download)")
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {

                // ── 4. Validate HTTP response ──────────────────────────────────
                if (!response.isSuccessful()) {
                    deliverError(callback,
                            "HTTP " + response.code() + " downloading thumbnail from " + remoteUrl);
                    return;
                }

                ResponseBody body = response.body();
                if (body == null) {
                    deliverError(callback, "Empty response body for thumbnail URL: " + remoteUrl);
                    return;
                }

                // ── 5. Stream bytes to disk ────────────────────────────────────
                // We stream rather than loading all bytes into memory - thumbnails
                // are small but good practice regardless.
                boolean written = streamToDisk(body, destination);
                if (!written) {
                    // Partial file may exist - delete it so a retry starts clean.
                    if (destination.exists()) destination.delete();
                    deliverError(callback, "Failed to write thumbnail to disk: "
                            + destination.getAbsolutePath());
                    return;
                }

                Log.d(TAG, "Thumbnail saved: " + destination.getName()
                        + " (" + destination.length() / 1024 + " KB)");
                deliverSuccess(callback, destination.getAbsolutePath());

            } catch (IOException e) {
                // Network failure or interrupted stream.
                // Clean up any partial file.
                if (destination.exists()) destination.delete();
                Log.w(TAG, "Network error downloading thumbnail from " + remoteUrl
                        + ": " + e.getMessage());
                deliverError(callback, "Network error: " + e.getMessage());
            }
        });
    }

    /**
     * Deletes the locally cached thumbnail for the given item, if it exists.
     *
     * This is the mirror of {@link #download} - call it when the user unfavourites
     * an item. Delegates to {@link ImageStorageManager#deleteFile(String)}.
     *
     * Safe to call from any thread. No callback - deletion is fire-and-forget
     * (failure is logged but not propagated, since the file will be cleaned up
     * by {@code ImagePurgeManager} at the next startup anyway).
     *
     * @param sourceQualifiedId The item's source-qualified ID, e.g. "OFF:3017620422003".
     */
    public void deleteThumbnail(@NonNull String sourceQualifiedId) {
        downloadExecutor.execute(() -> {
            File file = storageManager.getThumbnailFile(sourceQualifiedId);
            if (file == null) return; // Storage unavailable - nothing to delete.
            storageManager.deleteFile(file.getAbsolutePath());
        });
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
     * Builds the OkHttpClient used for all thumbnail downloads.
     *
     * Intentionally does NOT reuse the app's NetworkManager/NetworkClient clients:
     * - Those clients inject source-specific User-Agent headers and retry interceptors
     *   designed for JSON APIs, not binary downloads
     * - A dedicated client with tight timeouts is more appropriate for small images
     * - Thumbnails do not go through Retrofit - we need raw byte streaming
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
     * Streams the response body bytes to the destination file.
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
            Log.e(TAG, "Error streaming thumbnail to disk: " + destination.getName(), e);
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
        Log.w(TAG, "ThumbnailDownloader error: " + reason);
        mainHandler.post(() -> callback.onError(reason));
    }
}