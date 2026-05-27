package li.masciul.sugardaddi.ui.utils;

import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.yalantis.ucrop.UCrop;

import java.io.File;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import li.masciul.sugardaddi.utils.image.ImageProcessor;
import li.masciul.sugardaddi.utils.image.ImageStorageManager;

/**
 * ImagePickerHelper — UI layer handler for picking and processing user photos.
 *
 * RESPONSIBILITIES
 * ================
 * - Register ActivityResultLaunchers for camera, gallery, and uCrop in onCreate()
 * - Build a FileProvider URI for the camera destination file
 * - Launch the camera or gallery on demand
 * - Route the result through uCrop (mandatory crop step for all user photos)
 * - After crop, call ImageProcessor to resize/compress on a background thread
 * - Deliver the final processed file path via callback on the main thread
 *
 * WHAT THIS CLASS DOES NOT DO
 * ============================
 * - No disk path management        → ImageStorageManager
 * - No image resizing/compression  → ImageProcessor
 * - No thumbnail downloading       → ThumbnailDownloader
 * - No database access             → caller is responsible for persisting the path
 *
 * LIFECYCLE CONTRACT — CRITICAL
 * ==============================
 * ActivityResultLaunchers MUST be registered before onStart(). This class must
 * therefore be instantiated inside Activity.onCreate(), before super.onCreate()
 * returns. Instantiating it in onResume(), onStart(), or in response to a click
 * will throw an IllegalStateException from the Activity Result API.
 *
 * CORRECT USAGE
 * =============
 * <pre>
 * public class CreateMealActivity extends AppCompatActivity {
 *
 *     private ImagePickerHelper imagePicker;
 *
 *     {@literal @}Override
 *     protected void onCreate(Bundle savedInstanceState) {
 *         super.onCreate(savedInstanceState);
 *
 *         // Must be created here — registers launchers before onStart()
 *         ImageStorageManager storageManager = new ImageStorageManager(this);
 *         imagePicker = new ImagePickerHelper(this, storageManager, ImagePickerHelper.Target.MEALS);
 *
 *         setContentView(R.layout.activity_create_meal);
 *
 *         addPhotoButton.setOnClickListener(v ->
 *             imagePicker.showSourceChooser());  // safe to call any time after onCreate
 *     }
 * }
 * </pre>
 *
 * PICK FLOW
 * =========
 *   showCamera()  ─┐
 *                  ├─▶ uCrop (mandatory crop)
 *   showGallery() ─┘        │
 *                            ▼
 *                     ImageProcessor.process()   (background thread)
 *                            │
 *                            ▼
 *                     Callback.onImageReady(path) (main thread)
 *
 * UCROP CONFIGURATION
 * ====================
 * Free-style crop is enabled — no fixed aspect ratio. The crop frame initially
 * matches the full source image so the user sees their photo immediately and can
 * accept it as-is or adjust. No initial zoom is applied.
 *
 * FILE PROVIDER AUTHORITY
 * =======================
 * Authority: li.masciul.sugardaddi.fileprovider
 * Declared in AndroidManifest.xml; paths defined in res/xml/file_paths.xml.
 * Required for sharing a File URI with the camera app on API 24+.
 */
public class ImagePickerHelper {

    private static final String TAG = "SugarDaddi_Images";

    /** FileProvider authority — must match AndroidManifest.xml exactly. */
    public static final String FILE_PROVIDER_AUTHORITY =
            "li.masciul.sugardaddi.fileprovider";

    // =========================================================================
    // TARGET — where the picked image will be stored
    // =========================================================================

    /**
     * Determines which subdirectory the processed image is saved to,
     * and what maximum dimension/quality settings are applied.
     *
     * Pass the appropriate Target when constructing this helper.
     */
    public enum Target {
        /** Photo attached to a meal journal entry → photos/meals/ */
        MEALS,
        /** Photo attached to a recipe preparation step → photos/steps/ */
        STEPS,
        /** Hero image for a food product → photos/products/ */
        PRODUCTS
    }

    // =========================================================================
    // CALLBACK INTERFACE
    // =========================================================================

    /**
     * Callback for image picking results.
     * Both methods are always called on the main thread.
     */
    public interface Callback {

        /**
         * Called when a photo has been picked, cropped, processed, and saved.
         *
         * @param localPath Absolute path to the processed image file.
         *                  Ready to persist in Room or load via Glide.
         */
        void onImageReady(@NonNull String localPath);

        /**
         * Called when the pick was cancelled (user pressed back in the camera,
         * gallery, or uCrop) or when an error occurred at any step.
         *
         * Cancellation is not an error — it is normal user behaviour. The caller
         * should simply keep the existing image state unchanged.
         *
         * @param reason Human-readable description. "Cancelled" for user cancellations,
         *               a more specific message for errors.
         */
        void onCancelled(@NonNull String reason);
    }

    // =========================================================================
    // STATE
    // =========================================================================

    private final AppCompatActivity activity;
    private final ImageStorageManager storageManager;
    private final Target target;
    private final Handler mainHandler;
    private final ExecutorService processingExecutor;

    /**
     * The URI passed to the camera intent as the output destination.
     * Stored as a field because it must survive between the launch of the
     * camera and the receipt of its result in the ActivityResultLauncher callback.
     * Null when no camera session is in progress.
     */
    @Nullable
    private Uri pendingCameraUri;

    /**
     * The File backing pendingCameraUri.
     * Kept separately so we can pass it directly to uCrop without re-resolving.
     */
    @Nullable
    private File pendingCameraFile;

    /**
     * The File uCrop should write its output to.
     * Created fresh for every pick session (UUID-based name in the target directory).
     * Stored as a field because it must survive between uCrop launch and result.
     */
    @Nullable
    private File pendingCropOutputFile;

    /**
     * The current result callback.
     * Set by showCamera() / showGallery(), cleared after delivery.
     * A new pick session overwrites any previously pending callback.
     */
    @Nullable
    private Callback pendingCallback;

    // =========================================================================
    // ACTIVITY RESULT LAUNCHERS
    // =========================================================================

    /**
     * Launcher for the system camera (ACTION_IMAGE_CAPTURE).
     * Registered unconditionally in onCreate() — registration after onStart()
     * throws IllegalStateException.
     */
    private final ActivityResultLauncher<Uri> cameraLauncher;

    /**
     * Launcher for the system gallery / file picker (ACTION_GET_CONTENT).
     * Registered unconditionally in onCreate().
     */
    private final ActivityResultLauncher<String> galleryLauncher;

    /**
     * Launcher for uCrop.
     * uCrop returns its result via a standard startActivityForResult flow,
     * so we wrap it in an ActivityResultLauncher<Intent> using the generic
     * StartActivityForResult contract.
     */
    private final ActivityResultLauncher<Intent> cropLauncher;

    // =========================================================================
    // CONSTRUCTOR — MUST be called inside Activity.onCreate()
    // =========================================================================

    /**
     * Constructs the helper and registers all three ActivityResultLaunchers.
     *
     * MUST be called inside {@code Activity.onCreate()}, before
     * {@code super.onCreate()} returns or at the latest before {@code onStart()}.
     * Violating this constraint will throw an {@link IllegalStateException}.
     *
     * @param activity       The host Activity. Used for launcher registration and
     *                       FileProvider URI construction. Not retained beyond onCreate() —
     *                       the launchers hold their own lifecycle-aware references.
     * @param storageManager Used to construct destination file paths.
     * @param target         Where the processed image will be saved.
     */
    public ImagePickerHelper(
            @NonNull AppCompatActivity activity,
            @NonNull ImageStorageManager storageManager,
            @NonNull Target target) {

        this.activity          = activity;
        this.storageManager    = storageManager;
        this.target            = target;
        this.mainHandler       = new Handler(Looper.getMainLooper());
        this.processingExecutor = Executors.newSingleThreadExecutor();

        // ── Register camera launcher ──────────────────────────────────────────
        // TakePicture contract: takes a Uri (the output file URI), returns boolean
        // (true = picture taken, false = cancelled).
        // The camera writes the full-resolution photo to the URI we provide.
        this.cameraLauncher = activity.registerForActivityResult(
                new ActivityResultContracts.TakePicture(),
                this::onCameraResult);

        // ── Register gallery launcher ─────────────────────────────────────────
        // GetContent contract: takes a MIME type string ("image/*"), returns the
        // Uri the user selected, or null if they cancelled.
        this.galleryLauncher = activity.registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                this::onGalleryResult);

        // ── Register uCrop launcher ───────────────────────────────────────────
        // uCrop uses a plain Activity intent flow; we wrap it with the generic
        // StartActivityForResult contract and parse the result ourselves.
        this.cropLauncher = activity.registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                this::onCropResult);

        Log.d(TAG, "ImagePickerHelper initialised for target: " + target.name());
    }

    // =========================================================================
    // PUBLIC API — call from UI event handlers (clicks etc.)
    // =========================================================================

    /**
     * Launches the system camera.
     *
     * Creates a temporary file in the target directory to receive the photo,
     * wraps it in a FileProvider URI (required on API 24+), then launches
     * the camera intent. On success the photo flows through uCrop and
     * ImageProcessor before the callback fires.
     *
     * Safe to call from any thread after onCreate() has completed.
     *
     * @param callback Result callback. Must not be null.
     */
    public void showCamera(@NonNull Callback callback) {
        this.pendingCallback = callback;

        // Create the destination file that the camera will write into.
        // UUID filename avoids collisions between sessions.
        String filename = UUID.randomUUID().toString() + "_camera_raw.jpg";
        File cameraFile = createTempFileForTarget(filename);

        if (cameraFile == null) {
            deliverCancelled(callback, "Could not create camera output file");
            return;
        }

        // Ensure the empty file exists on disk so FileProvider can serve it.
        File created = storageManager.createEmptyFile(cameraFile);
        if (created == null) {
            deliverCancelled(callback, "Could not prepare camera output file");
            return;
        }

        // Build the FileProvider URI — required for cross-app file sharing on API 24+.
        // The camera app receives this URI and writes the photo directly into it.
        Uri cameraUri = FileProvider.getUriForFile(
                activity,
                FILE_PROVIDER_AUTHORITY,
                created);

        this.pendingCameraUri  = cameraUri;
        this.pendingCameraFile = created;

        Log.d(TAG, "Launching camera → " + created.getName());
        cameraLauncher.launch(cameraUri);
    }

    /**
     * Launches the system gallery / image picker.
     *
     * The user selects an image; on confirmation it flows through uCrop and
     * ImageProcessor before the callback fires.
     *
     * Safe to call from any thread after onCreate() has completed.
     *
     * @param callback Result callback. Must not be null.
     */
    public void showGallery(@NonNull Callback callback) {
        this.pendingCallback = callback;
        Log.d(TAG, "Launching gallery picker");
        // "image/*" accepts all image formats the system supports.
        galleryLauncher.launch("image/*");
    }

    /**
     * Shuts down the background processing executor.
     *
     * Call from Activity.onDestroy() to avoid leaking the executor thread
     * if the Activity is destroyed while processing is in progress.
     */
    public void shutdown() {
        processingExecutor.shutdown();
    }

    // =========================================================================
    // ACTIVITY RESULT HANDLERS — private, called by registered launchers
    // =========================================================================

    /**
     * Receives the camera result.
     *
     * @param success True if the user took a photo; false if they cancelled.
     *                The photo has been written to pendingCameraFile on success.
     */
    private void onCameraResult(boolean success) {
        if (!success || pendingCameraFile == null) {
            cleanupPendingFiles();
            deliverCancelled(pendingCallback, "Camera cancelled");
            return;
        }

        // Photo is now in pendingCameraFile — hand it to uCrop.
        Log.d(TAG, "Camera photo received: " + pendingCameraFile.getName());
        launchCrop(Uri.fromFile(pendingCameraFile));
    }

    /**
     * Receives the gallery result.
     *
     * @param selectedUri The URI of the image the user selected, or null if cancelled.
     *                    This is a content:// URI — we must copy it to our own storage
     *                    before passing it to uCrop, because uCrop needs a writable
     *                    output URI and the source must remain readable throughout.
     */
    private void onGalleryResult(@Nullable Uri selectedUri) {
        if (selectedUri == null) {
            deliverCancelled(pendingCallback, "Gallery cancelled");
            return;
        }

        // uCrop requires a file:// or content:// source URI that it can read.
        // The gallery returns a content:// URI that uCrop handles natively.
        Log.d(TAG, "Gallery image selected: " + selectedUri);
        launchCrop(selectedUri);
    }

    /**
     * Receives the uCrop result.
     *
     * uCrop signals success via Activity.RESULT_OK and puts the output URI in
     * the result data. Cancellation is RESULT_CANCELED with no output URI.
     * Errors are RESULT_CANCELED with a Throwable in the result extras.
     *
     * @param result The ActivityResult from uCrop.
     */
    private void onCropResult(@NonNull ActivityResult result) {
        if (result.getResultCode() == AppCompatActivity.RESULT_OK
                && result.getData() != null) {

            // uCrop wrote the cropped image to pendingCropOutputFile.
            Uri croppedUri = UCrop.getOutput(result.getData());
            if (croppedUri == null || pendingCropOutputFile == null) {
                cleanupPendingFiles();
                deliverCancelled(pendingCallback, "uCrop returned no output URI");
                return;
            }

            // At this point pendingCropOutputFile is the cropped image on disk.
            // Process it (resize + compress) on a background thread.
            Log.d(TAG, "Crop completed: " + pendingCropOutputFile.getName());
            processAndDeliver(pendingCropOutputFile, pendingCallback);

        } else if (result.getData() != null) {
            // uCrop puts the error Throwable in the intent when it fails.
            Throwable error = UCrop.getError(result.getData());
            String reason = (error != null) ? error.getMessage() : "uCrop failed";
            cleanupPendingFiles();
            deliverCancelled(pendingCallback, "Crop failed: " + reason);

        } else {
            // User pressed back in uCrop — normal cancellation.
            cleanupPendingFiles();
            deliverCancelled(pendingCallback, "Crop cancelled");
        }
    }

    // =========================================================================
    // UCROP LAUNCH
    // =========================================================================

    /**
     * Launches uCrop with the given source URI.
     *
     * Creates a fresh UUID-named output file in the target directory for uCrop
     * to write into. The source may be a file:// (camera output) or
     * content:// (gallery pick) URI — uCrop handles both.
     *
     * @param sourceUri The URI of the image to crop.
     */
    private void launchCrop(@NonNull Uri sourceUri) {
        // Create the output file that uCrop will write the cropped image into.
        // UUID-based name in the correct target directory.
        String outputFilename = UUID.randomUUID().toString() + ".jpg";
        File outputFile = createTempFileForTarget(outputFilename);

        if (outputFile == null) {
            cleanupPendingFiles();
            deliverCancelled(pendingCallback, "Could not create crop output file");
            return;
        }

        this.pendingCropOutputFile = outputFile;
        Uri outputUri = Uri.fromFile(outputFile);

        // ── uCrop options ─────────────────────────────────────────────────────
        UCrop.Options options = new UCrop.Options();

        // Free-style crop: the user can drag corners freely.
        // No setAspectRatio() call → crop frame matches the source image aspect
        // ratio initially, so the user sees the full photo and can accept immediately.
        options.setFreeStyleCropEnabled(true);

        // Hide the "reset" button to keep the UI simple.
        options.setShowCropGrid(true);

        // Build and launch the uCrop intent.
        Intent cropIntent = UCrop.of(sourceUri, outputUri)
                .withOptions(options)
                .getIntent(activity);

        Log.d(TAG, "Launching uCrop: " + sourceUri + " → " + outputFile.getName());
        cropLauncher.launch(cropIntent);
    }

    // =========================================================================
    // IMAGE PROCESSING
    // =========================================================================

    /**
     * Runs ImageProcessor on the cropped file on a background thread,
     * then delivers the result path via callback on the main thread.
     *
     * The processed output overwrites the crop output file in-place
     * (source == destination) — no intermediate file needed.
     *
     * @param croppedFile The file produced by uCrop. Also used as the output
     *                    destination — ImageProcessor is safe with source == destination.
     * @param callback    The callback to notify on completion.
     */
    private void processAndDeliver(
            @NonNull File croppedFile,
            @Nullable Callback callback) {

        processingExecutor.execute(() -> {
            File result = ImageProcessor.process(
                    croppedFile,
                    croppedFile,          // process in-place
                    ImageProcessor.MAX_DIMENSION_USER_PHOTO,
                    ImageProcessor.JPEG_QUALITY_USER_PHOTO);

            // Clean up the raw camera file if it still exists
            // (it's separate from the crop output file).
            if (pendingCameraFile != null
                    && pendingCameraFile.exists()
                    && !pendingCameraFile.equals(croppedFile)) {
                pendingCameraFile.delete();
            }
            clearPendingState();

            if (result != null) {
                Log.d(TAG, "Processing complete: " + result.getAbsolutePath());
                mainHandler.post(() -> {
                    if (callback != null) {
                        callback.onImageReady(result.getAbsolutePath());
                    }
                });
            } else {
                mainHandler.post(() ->
                        deliverCancelled(callback, "Image processing failed"));
            }
        });
    }

    // =========================================================================
    // FILE HELPERS
    // =========================================================================

    /**
     * Creates a File object at the correct path for the current Target.
     *
     * Does NOT create the file on disk — use ImageStorageManager.createEmptyFile()
     * if the file must exist before being handed to an external app (camera).
     *
     * @param filename The filename (with extension) for the new file.
     * @return A File at the correct path, or null if the directory is unavailable.
     */
    @Nullable
    private File createTempFileForTarget(@NonNull String filename) {
        switch (target) {
            case MEALS:    return storageManager.getMealPhotoFile(filename);
            case STEPS:    return storageManager.getStepPhotoFile(filename);
            case PRODUCTS: return storageManager.getProductPhotoFile(filename);
            default:
                Log.e(TAG, "Unknown target: " + target);
                return null;
        }
    }

    /**
     * Deletes any temporary files created during a pick session that did not
     * complete successfully. Called on cancellation or error paths.
     */
    private void cleanupPendingFiles() {
        if (pendingCameraFile != null && pendingCameraFile.exists()) {
            pendingCameraFile.delete();
            Log.d(TAG, "Cleaned up camera temp file: " + pendingCameraFile.getName());
        }
        if (pendingCropOutputFile != null && pendingCropOutputFile.exists()) {
            pendingCropOutputFile.delete();
            Log.d(TAG, "Cleaned up crop output file: " + pendingCropOutputFile.getName());
        }
        clearPendingState();
    }

    /**
     * Clears all pending session state fields.
     * Called after successful delivery or after cleanup on error/cancellation.
     */
    private void clearPendingState() {
        pendingCameraUri       = null;
        pendingCameraFile      = null;
        pendingCropOutputFile  = null;
        pendingCallback        = null;
    }

    // =========================================================================
    // CALLBACK DELIVERY HELPERS
    // =========================================================================

    /**
     * Delivers a cancellation/error notification on the main thread.
     *
     * @param callback The callback to notify. No-op if null.
     * @param reason   Human-readable reason string.
     */
    private void deliverCancelled(@Nullable Callback callback, @NonNull String reason) {
        Log.d(TAG, "Pick cancelled/failed: " + reason);
        if (callback == null) return;
        if (Looper.myLooper() == Looper.getMainLooper()) {
            callback.onCancelled(reason);
        } else {
            mainHandler.post(() -> callback.onCancelled(reason));
        }
    }
}