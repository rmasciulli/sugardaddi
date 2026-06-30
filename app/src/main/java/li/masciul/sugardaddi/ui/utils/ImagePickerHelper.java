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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import li.masciul.sugardaddi.utils.image.ImageProcessor;
import li.masciul.sugardaddi.utils.image.ImageProfile;
import li.masciul.sugardaddi.utils.image.ImageStorageManager;

/**
 * ImagePickerHelper - UI layer handler for picking and processing user photos.
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
 * - No disk path management        → ImageStorageManager (caller's responsibility)
 * - No image resizing/compression  → ImageProcessor
 * - No thumbnail downloading       → ImageDownloader
 * - No database access             → caller persists the returned path in Room
 * - No filename generation         → caller constructs the destination File
 *
 * DESIGN PRINCIPLE - CALLER OWNS THE PATH
 * ========================================
 * The caller is responsible for constructing the destination File before calling
 * showCamera() or showGallery(). This keeps ImagePickerHelper truly path-agnostic:
 * it does not know whether the image is a product hero, recipe thumbnail, meal photo,
 * or step photo - that is the caller's concern.
 *
 * The caller also chooses the {@link ImageProfile} (size + quality preset) that
 * ImageProcessor will apply after the crop.
 *
 * Example (product hero image):
 *   File dest = storageManager.getProductHeroFile(UUID.randomUUID() + ".jpg");
 *   imagePicker.showCamera(dest, ImageProfile.HERO, callback);
 *
 * Example (user thumbnail override, deterministic filename):
 *   File dest = storageManager.getUserThumbnailFile(product.getSearchableId());
 *   imagePicker.showGallery(dest, ImageProfile.THUMBNAIL, callback);
 *
 * LIFECYCLE CONTRACT - CRITICAL
 * ==============================
 * ActivityResultLaunchers MUST be registered before onStart(). This class must
 * therefore be instantiated inside Activity.onCreate(), before super.onCreate()
 * returns. Instantiating it in onResume(), onStart(), or in a click handler
 * will throw an IllegalStateException from the Activity Result API.
 *
 * CORRECT USAGE
 * =============
 * <pre>
 * public class ProductDetailsActivity extends AppCompatActivity {
 *
 *     private ImagePickerHelper imagePicker;
 *
 *     {@literal @}Override
 *     protected void onCreate(Bundle savedInstanceState) {
 *         super.onCreate(savedInstanceState);
 *
 *         // Must be created here - registers launchers before onStart()
 *         imagePicker = new ImagePickerHelper(this);
 *
 *         setContentView(R.layout.activity_product_details);
 *     }
 *
 *     private void replaceHeroImage() {
 *         ImageStorageManager storage =
 *             ((SugarDaddiApplication) getApplication()).getImageStorageManager();
 *         File dest = storage.getProductHeroFile(UUID.randomUUID() + ".jpg");
 *         imagePicker.showGallery(dest, ImageProfile.HERO, path -> persistHeroImage(path));
 *     }
 *
 *     {@literal @}Override
 *     protected void onDestroy() {
 *         super.onDestroy();
 *         imagePicker.shutdown();
 *     }
 * }
 * </pre>
 *
 * PICK FLOW
 * =========
 *   showCamera(dest, ...)  ─┐
 *                           ├─▶ uCrop (mandatory crop/confirm step, writes to dest)
 *   showGallery(dest, ...) ─┘        │
 *                                    ▼
 *                             ImageProcessor.process()   (background thread, in-place)
 *                                    │
 *                                    ▼
 *                             Callback.onImageReady(path) (main thread)
 *
 * UCROP CONFIGURATION
 * ====================
 * Free-style crop is enabled with no fixed aspect ratio. The crop frame initially
 * shows the full source image so the user can accept immediately or adjust.
 *
 * FILE PROVIDER AUTHORITY
 * =======================
 * Authority: li.masciul.sugardaddi.fileprovider
 * Declared in AndroidManifest.xml; paths defined in res/xml/file_paths.xml.
 * Required for sharing a File URI with the camera app on API 24+.
 */
public class ImagePickerHelper {

    private static final String TAG = "SugarDaddi_Images";

    /** FileProvider authority - must match AndroidManifest.xml exactly. */
    public static final String FILE_PROVIDER_AUTHORITY =
            "li.masciul.sugardaddi.fileprovider";

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
         * Called when the pick was cancelled or failed at any step.
         *
         * Cancellation is normal user behaviour - simply keep the existing
         * image state unchanged. Only log errors, don't show an error UI
         * for plain cancellations.
         *
         * @param reason Human-readable description.
         */
        void onCancelled(@NonNull String reason);
    }

    // =========================================================================
    // STATE
    // =========================================================================

    private final AppCompatActivity activity;
    private final Handler           mainHandler;
    private final ExecutorService   processingExecutor;

    /**
     * The URI passed to the camera intent. Must survive between the launch
     * and the result - stored as a field for exactly that reason.
     * Null when no camera session is in progress.
     */
    @Nullable private Uri pendingCameraUri;

    /**
     * The File backing pendingCameraUri. Kept separately so we can hand it
     * directly to uCrop without re-resolving the URI.
     */
    @Nullable private File pendingCameraFile;

    /**
     * The caller-provided destination file. uCrop writes its cropped output
     * here; ImageProcessor then processes it in-place.
     * Set at the start of each pick session; cleared after delivery.
     */
    @Nullable private File pendingDestinationFile;

    /**
     * The active result callback. Set by showCamera()/showGallery(),
     * cleared after delivery. A new pick session overwrites any pending one.
     */
    @Nullable private Callback pendingCallback;

    /**
     * The size + quality preset for the processed image.
     * Set by the caller per session - determines resize/compression behaviour
     * in ImageProcessor. Cleared after delivery.
     */
    @Nullable private ImageProfile pendingProfile;

    // =========================================================================
    // ACTIVITY RESULT LAUNCHERS
    // =========================================================================

    // Camera launcher - TakePicture contract. Registered unconditionally in onCreate().
    private final ActivityResultLauncher<Uri>    cameraLauncher;

    // Gallery launcher - GetContent contract. Registered unconditionally in onCreate().
    private final ActivityResultLauncher<String> galleryLauncher;

    /**
     * uCrop launcher - StartActivityForResult contract.
     * uCrop uses a plain Activity intent; we wrap it and parse the result ourselves.
     */
    private final ActivityResultLauncher<Intent> cropLauncher;

    /**
     * Camera permission launcher - requests android.permission.CAMERA at runtime.
     * On grant, resumes the pending camera session. On deny, delivers cancelled.
     * Must be registered unconditionally in onCreate() alongside the other launchers.
     */
    private final ActivityResultLauncher<String> cameraPermissionLauncher;

    // =========================================================================
    // CONSTRUCTOR - MUST be called inside Activity.onCreate()
    // =========================================================================

    /**
     * Constructs the helper and registers all three ActivityResultLaunchers.
     *
     * MUST be called inside {@code Activity.onCreate()}, before {@code onStart()}.
     * Violating this constraint throws {@link IllegalStateException}.
     *
     * The caller is fully responsible for constructing destination Files before
     * calling showCamera() or showGallery(). Use ImageStorageManager for paths.
     *
     * @param activity The host Activity.
     */
    public ImagePickerHelper(@NonNull AppCompatActivity activity) {
        this.activity           = activity;
        this.mainHandler        = new Handler(Looper.getMainLooper());
        this.processingExecutor = Executors.newSingleThreadExecutor();

        // TakePicture: takes output URI, returns boolean (taken vs cancelled).
        this.cameraLauncher = activity.registerForActivityResult(
                new ActivityResultContracts.TakePicture(),
                this::onCameraResult);

        // GetContent: takes MIME type string, returns selected URI or null.
        this.galleryLauncher = activity.registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                this::onGalleryResult);

        // StartActivityForResult: generic wrapper for uCrop's Activity flow.
        this.cropLauncher = activity.registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                this::onCropResult);

        // RequestPermission: requests CAMERA at runtime (API 23+).
        // On grant → resume pending camera session.
        // On deny → deliver cancelled and clear pending state.
        this.cameraPermissionLauncher = activity.registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (granted) {
                        // Permission just granted - resume the pending session.
                        if (pendingDestinationFile != null && pendingCallback != null) {
                            launchCameraInternal();
                        }
                    } else {
                        deliverCancelled(pendingCallback, "Camera permission denied");
                        clearPendingState();
                    }
                });

        Log.d(TAG, "ImagePickerHelper initialised");
    }

    // =========================================================================
    // PUBLIC API
    // =========================================================================

    /**
     * Launches the system camera.
     *
     * Wraps the destination file in a FileProvider URI (required on API 24+)
     * and launches ACTION_IMAGE_CAPTURE. On success the photo flows through
     * uCrop → ImageProcessor before the callback fires.
     *
     * Safe to call from any thread after onCreate() has completed.
     *
     * @param destinationFile The file where the final processed image will be saved.
     *                        The caller constructs this via ImageStorageManager.
     *                        Must be in a directory covered by file_paths.xml.
     * @param profile         The size + quality preset to apply after cropping.
     *                        Use {@link ImageProfile#HERO} for full-size images or
     *                        {@link ImageProfile#THUMBNAIL} for thumbnail overrides.
     * @param callback        Result callback. Must not be null.
     */
    public void showCamera(
            @NonNull File destinationFile,
            @NonNull ImageProfile profile,
            @NonNull Callback callback) {

        // Store pending session state regardless of permission outcome -
        // launchCameraInternal() and the permission callback both rely on it.
        this.pendingCallback        = callback;
        this.pendingDestinationFile = destinationFile;
        this.pendingProfile         = profile;

        // On API 23+, CAMERA is a runtime permission - check before launching.
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                activity, android.Manifest.permission.CAMERA)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "CAMERA permission not granted - requesting");
            cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA);
            return;
        }

        launchCameraInternal();
    }

    /**
     * Internal camera launch - called after CAMERA permission is confirmed.
     * Shared by showCamera() (permission already held) and the permission
     * result callback (permission just granted).
     */
    private void launchCameraInternal() {
        if (!ensureFileExists(pendingDestinationFile)) {
            deliverCancelled(pendingCallback, "Could not prepare camera output file");
            return;
        }

        Uri cameraUri = FileProvider.getUriForFile(
                activity, FILE_PROVIDER_AUTHORITY, pendingDestinationFile);

        this.pendingCameraUri  = cameraUri;
        this.pendingCameraFile = pendingDestinationFile;

        Log.d(TAG, "Launching camera → " + pendingDestinationFile.getName());
        cameraLauncher.launch(cameraUri);
    }

    /**
     * Launches the system gallery / image picker.
     *
     * The selected image flows through uCrop → ImageProcessor before the
     * callback fires. The processed result is written to {@code destinationFile}.
     *
     * Safe to call from any thread after onCreate() has completed.
     *
     * @param destinationFile The file where the final processed image will be saved.
     *                        The caller constructs this via ImageStorageManager.
     * @param profile         The size + quality preset to apply after cropping.
     *                        Use {@link ImageProfile#HERO} or
     *                        {@link ImageProfile#THUMBNAIL}.
     * @param callback        Result callback. Must not be null.
     */
    public void showGallery(
            @NonNull File destinationFile,
            @NonNull ImageProfile profile,
            @NonNull Callback callback) {

        this.pendingCallback        = callback;
        this.pendingDestinationFile = destinationFile;
        this.pendingProfile         = profile;

        Log.d(TAG, "Launching gallery picker → " + destinationFile.getName());
        galleryLauncher.launch("image/*");
    }

    /**
     * Shuts down the background processing executor.
     * Call from Activity.onDestroy() to avoid executor thread leaks.
     */
    public void shutdown() {
        processingExecutor.shutdown();
    }

    // =========================================================================
    // ACTIVITY RESULT HANDLERS
    // =========================================================================

    private void onCameraResult(boolean success) {
        if (!success || pendingCameraFile == null) {
            cleanupPendingFiles();
            deliverCancelled(pendingCallback, "Camera cancelled");
            return;
        }
        Log.d(TAG, "Camera photo received: " + pendingCameraFile.getName());
        // Camera wrote to pendingCameraFile, which is also pendingDestinationFile.
        // Pass it directly to uCrop - no separate raw file to manage.
        launchCrop(Uri.fromFile(pendingCameraFile));
    }

    private void onGalleryResult(@Nullable Uri selectedUri) {
        if (selectedUri == null) {
            deliverCancelled(pendingCallback, "Gallery cancelled");
            return;
        }
        Log.d(TAG, "Gallery image selected: " + selectedUri);
        launchCrop(selectedUri);
    }

    private void onCropResult(@NonNull ActivityResult result) {
        if (result.getResultCode() == AppCompatActivity.RESULT_OK
                && result.getData() != null) {

            Uri croppedUri = UCrop.getOutput(result.getData());
            if (croppedUri == null || pendingDestinationFile == null) {
                cleanupPendingFiles();
                deliverCancelled(pendingCallback, "uCrop returned no output URI");
                return;
            }

            Log.d(TAG, "Crop complete → " + pendingDestinationFile.getName());
            processAndDeliver(pendingDestinationFile, pendingCallback);

        } else if (result.getData() != null) {
            Throwable error = UCrop.getError(result.getData());
            String reason   = (error != null) ? error.getMessage() : "uCrop failed";
            cleanupPendingFiles();
            deliverCancelled(pendingCallback, "Crop failed: " + reason);

        } else {
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
     * uCrop writes its cropped output directly to the caller-provided destination
     * file. Source may be file:// (camera) or content:// (gallery).
     */
    private void launchCrop(@NonNull Uri sourceUri) {
        if (pendingDestinationFile == null) {
            deliverCancelled(pendingCallback, "No destination file set for crop");
            return;
        }

        // Ensure the destination file exists so uCrop can write to it.
        ensureFileExists(pendingDestinationFile);

        Uri outputUri = Uri.fromFile(pendingDestinationFile);

        UCrop.Options options = new UCrop.Options();
        // Free-style crop - no fixed ratio. Crop frame initially covers the full
        // image so the user can accept immediately or adjust freely.
        options.setFreeStyleCropEnabled(true);
        options.setShowCropGrid(true);

        Intent cropIntent = UCrop.of(sourceUri, outputUri)
                .withOptions(options)
                .getIntent(activity);

        Log.d(TAG, "Launching uCrop: " + sourceUri
                + " → " + pendingDestinationFile.getName());
        cropLauncher.launch(cropIntent);
    }

    // =========================================================================
    // IMAGE PROCESSING
    // =========================================================================

    /**
     * Compresses and resizes the cropped file on a background thread, then
     * delivers the result path via callback on the main thread.
     *
     * Processes in-place (source == destination) - ImageProcessor is safe for this
     * because the bitmap is fully decoded into memory before the output stream opens.
     *
     * Uses the ImageProfile provided by the caller at session start.
     */
    private void processAndDeliver(@NonNull File destinationFile,
                                   @Nullable Callback callback) {
        // Capture the session profile before clearing pending state. Both public
        // entry points (showCamera/showGallery) always set it; a null here means
        // a broken call flow, so fail loudly rather than guess a size.
        final ImageProfile profile = this.pendingProfile;
        if (profile == null) {
            clearPendingState();
            deliverCancelled(callback, "No image profile set for processing");
            return;
        }

        processingExecutor.execute(() -> {
            File result = ImageProcessor.process(
                    destinationFile,
                    destinationFile,   // in-place: no intermediate file needed
                    profile);

            clearPendingState();

            if (result != null) {
                Log.d(TAG, "Processing complete: " + result.getAbsolutePath());
                mainHandler.post(() -> {
                    if (callback != null) callback.onImageReady(result.getAbsolutePath());
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
     * Ensures the given file exists on disk, creating it and its parent directories
     * if necessary. Required before handing the URI to the camera or uCrop.
     *
     * @return true if the file exists (or was created); false on failure.
     */
    private boolean ensureFileExists(@NonNull File file) {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                if (!parent.mkdirs()) {
                    Log.e(TAG, "Failed to create parent dirs for: "
                            + file.getAbsolutePath());
                    return false;
                }
            }
            if (!file.exists()) {
                boolean created = file.createNewFile();
                if (!created) {
                    // File may already exist after a crash/retry - acceptable.
                    Log.d(TAG, "File already exists: " + file.getAbsolutePath());
                }
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to ensure file exists: " + file.getAbsolutePath(), e);
            return false;
        }
    }

    /** Deletes any temporary files from a pick session that did not complete. */
    private void cleanupPendingFiles() {
        // For camera sessions, pendingCameraFile == pendingDestinationFile.
        // If the session was cancelled we clean up the partial/empty destination.
        if (pendingDestinationFile != null && pendingDestinationFile.exists()) {
            pendingDestinationFile.delete();
            Log.d(TAG, "Cleaned up destination file: "
                    + pendingDestinationFile.getName());
        }
        clearPendingState();
    }

    /** Clears all pending session state without deleting files. */
    private void clearPendingState() {
        pendingCameraUri        = null;
        pendingCameraFile       = null;
        pendingDestinationFile  = null;
        pendingCallback         = null;
        pendingProfile          = null;
    }

    /** Delivers a cancellation via the given callback on the main thread. */
    private void deliverCancelled(@Nullable Callback callback,
                                  @NonNull String reason) {
        Log.d(TAG, "Pick cancelled: " + reason);
        if (callback != null) {
            mainHandler.post(() -> callback.onCancelled(reason));
        }
    }
}