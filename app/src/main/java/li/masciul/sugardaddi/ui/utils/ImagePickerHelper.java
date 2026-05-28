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
 * - No database access             → caller persists the returned path in Room
 *
 * LIFECYCLE CONTRACT — CRITICAL
 * ==============================
 * ActivityResultLaunchers MUST be registered before onStart(). This class must
 * therefore be instantiated inside Activity.onCreate(), before super.onCreate()
 * returns. Instantiating it in onResume(), onStart(), or in a click handler
 * will throw an IllegalStateException from the Activity Result API.
 *
 * CORRECT USAGE
 * =============
 * <pre>
 * public class MealDetailActivity extends AppCompatActivity {
 *
 *     private ImagePickerHelper imagePicker;
 *
 *     {@literal @}Override
 *     protected void onCreate(Bundle savedInstanceState) {
 *         super.onCreate(savedInstanceState);
 *
 *         // Must be created here — registers launchers before onStart()
 *         ImageStorageManager storageManager =
 *             ((SugarDaddiApplication) getApplication()).getImageStorageManager();
 *         imagePicker = new ImagePickerHelper(
 *             this, storageManager, ImagePickerHelper.Target.MEAL);
 *
 *         setContentView(R.layout.activity_meal_detail);
 *
 *         addPhotoButton.setOnClickListener(v ->
 *             imagePicker.showCamera(myCallback)); // safe any time after onCreate
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
 *   showCamera()  ─┐
 *                  ├─▶ uCrop (mandatory crop/confirm step)
 *   showGallery() ─┘        │
 *                            ▼
 *                     ImageProcessor.process()   (background thread)
 *                            │
 *                            ▼
 *                     Callback.onImageReady(path) (main thread)
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

    /** FileProvider authority — must match AndroidManifest.xml exactly. */
    public static final String FILE_PROVIDER_AUTHORITY =
            "li.masciul.sugardaddi.fileprovider";

    // =========================================================================
    // TARGET — determines which directory the processed image is saved to
    // =========================================================================

    /**
     * Determines the destination directory for the processed image.
     *
     * Matches the directory structure in ImageStorageManager:
     *   PRODUCT_HERO → sugardaddi/products/
     *   RECIPE_HERO  → sugardaddi/recipes/
     *   MEAL         → sugardaddi/meals/
     *   STEP         → sugardaddi/steps/
     */
    public enum Target {
        /** Hero image for a food product — stored in products/. */
        PRODUCT_HERO,
        /** Hero image for a recipe — stored in recipes/. */
        RECIPE_HERO,
        /** Photo attached to a meal journal entry — stored in meals/. */
        MEAL,
        /** Instructional photo for a recipe preparation step — stored in steps/. */
        STEP
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
         * Called when the pick was cancelled or failed at any step.
         *
         * Cancellation is normal user behaviour — simply keep the existing
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

    private final AppCompatActivity  activity;
    private final ImageStorageManager storageManager;
    private final Target              target;
    private final Handler             mainHandler;
    private final ExecutorService     processingExecutor;

    /**
     * The URI passed to the camera intent. Must survive between the launch
     * and the result — stored as a field for exactly that reason.
     * Null when no camera session is in progress.
     */
    @Nullable private Uri  pendingCameraUri;

    /**
     * The File backing pendingCameraUri. Kept separately so we can hand it
     * directly to uCrop without re-resolving the URI.
     */
    @Nullable private File pendingCameraFile;

    /**
     * The File uCrop should write its cropped output to.
     * Created fresh per pick session (UUID-based name in the target directory).
     */
    @Nullable private File pendingCropOutputFile;

    /**
     * The active result callback. Set by showCamera()/showGallery(),
     * cleared after delivery. A new pick session overwrites any pending one.
     */
    @Nullable private Callback pendingCallback;

    // =========================================================================
    // ACTIVITY RESULT LAUNCHERS
    // =========================================================================

    /** Camera launcher — TakePicture contract. Registered unconditionally in onCreate(). */
    private final ActivityResultLauncher<Uri>    cameraLauncher;

    /** Gallery launcher — GetContent contract. Registered unconditionally in onCreate(). */
    private final ActivityResultLauncher<String> galleryLauncher;

    /**
     * uCrop launcher — StartActivityForResult contract.
     * uCrop uses a plain Activity intent; we wrap it and parse the result ourselves.
     */
    private final ActivityResultLauncher<Intent> cropLauncher;

    // =========================================================================
    // CONSTRUCTOR — MUST be called inside Activity.onCreate()
    // =========================================================================

    /**
     * Constructs the helper and registers all three ActivityResultLaunchers.
     *
     * MUST be called inside {@code Activity.onCreate()}, before {@code onStart()}.
     * Violating this constraint throws {@link IllegalStateException}.
     *
     * @param activity       The host Activity.
     * @param storageManager Used to construct destination file paths.
     *                       Use the application-scoped singleton from SugarDaddiApplication.
     * @param target         Determines which directory the processed image is saved to.
     */
    public ImagePickerHelper(
            @NonNull AppCompatActivity activity,
            @NonNull ImageStorageManager storageManager,
            @NonNull Target target) {

        this.activity           = activity;
        this.storageManager     = storageManager;
        this.target             = target;
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

        Log.d(TAG, "ImagePickerHelper initialised for target: " + target.name());
    }

    // =========================================================================
    // PUBLIC API
    // =========================================================================

    /**
     * Launches the system camera.
     *
     * Creates a destination file in the target directory, wraps it in a
     * FileProvider URI (required on API 24+), and launches ACTION_IMAGE_CAPTURE.
     * On success the photo flows through uCrop → ImageProcessor before the
     * callback fires.
     *
     * Safe to call from any thread after onCreate() has completed.
     *
     * @param callback Result callback. Must not be null.
     */
    public void showCamera(@NonNull Callback callback) {
        this.pendingCallback = callback;

        // UUID filename avoids collisions between sessions.
        String filename   = UUID.randomUUID().toString() + "_raw.jpg";
        File   cameraFile = resolveFileForTarget(filename);

        if (cameraFile == null) {
            deliverCancelled(callback, "Could not create camera output file");
            return;
        }

        // Camera app requires a pre-existing file URI.
        File created = storageManager.createEmptyFile(cameraFile);
        if (created == null) {
            deliverCancelled(callback, "Could not prepare camera output file");
            return;
        }

        Uri cameraUri = FileProvider.getUriForFile(
                activity, FILE_PROVIDER_AUTHORITY, created);

        this.pendingCameraUri  = cameraUri;
        this.pendingCameraFile = created;

        Log.d(TAG, "Launching camera → " + created.getName());
        cameraLauncher.launch(cameraUri);
    }

    /**
     * Launches the system gallery / image picker.
     *
     * The selected image flows through uCrop → ImageProcessor before the
     * callback fires.
     *
     * Safe to call from any thread after onCreate() has completed.
     *
     * @param callback Result callback. Must not be null.
     */
    public void showGallery(@NonNull Callback callback) {
        this.pendingCallback = callback;
        Log.d(TAG, "Launching gallery picker");
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
            if (croppedUri == null || pendingCropOutputFile == null) {
                cleanupPendingFiles();
                deliverCancelled(pendingCallback, "uCrop returned no output URI");
                return;
            }

            Log.d(TAG, "Crop complete: " + pendingCropOutputFile.getName());
            processAndDeliver(pendingCropOutputFile, pendingCallback);

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
     * Creates a fresh UUID-named output file in the target directory for uCrop
     * to write into. Source may be file:// (camera) or content:// (gallery).
     */
    private void launchCrop(@NonNull Uri sourceUri) {
        String outputFilename  = UUID.randomUUID().toString() + ".jpg";
        File   outputFile      = resolveFileForTarget(outputFilename);

        if (outputFile == null) {
            cleanupPendingFiles();
            deliverCancelled(pendingCallback, "Could not create crop output file");
            return;
        }

        this.pendingCropOutputFile = outputFile;
        Uri outputUri = Uri.fromFile(outputFile);

        UCrop.Options options = new UCrop.Options();
        // Free-style crop — no fixed ratio. Crop frame initially covers the full
        // image so the user can accept immediately or adjust freely.
        options.setFreeStyleCropEnabled(true);
        options.setShowCropGrid(true);

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
     * Compresses and resizes the cropped file on a background thread, then
     * delivers the result path via callback on the main thread.
     *
     * Processes in-place (source == destination) — ImageProcessor is safe for this
     * because the bitmap is fully decoded into memory before the output stream opens.
     */
    private void processAndDeliver(@NonNull File croppedFile, @Nullable Callback callback) {
        processingExecutor.execute(() -> {
            File result = ImageProcessor.process(
                    croppedFile,
                    croppedFile,   // in-place: no intermediate file needed
                    ImageProcessor.MAX_DIMENSION_USER_PHOTO,
                    ImageProcessor.JPEG_QUALITY_USER_PHOTO);

            // Delete raw camera file if it differs from the crop output.
            if (pendingCameraFile != null
                    && pendingCameraFile.exists()
                    && !pendingCameraFile.equals(croppedFile)) {
                pendingCameraFile.delete();
            }
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
     * Resolves the correct destination File for the current Target.
     *
     * Does NOT create the file on disk — use ImageStorageManager.createEmptyFile()
     * when the file must exist before being handed to an external app (camera).
     */
    @Nullable
    private File resolveFileForTarget(@NonNull String filename) {
        switch (target) {
            case PRODUCT_HERO: return storageManager.getProductHeroFile(filename);
            case RECIPE_HERO:  return storageManager.getRecipeHeroFile(filename);
            case MEAL:         return storageManager.getMealPhotoFile(filename);
            case STEP:         return storageManager.getStepPhotoFile(filename);
            default:
                Log.e(TAG, "Unknown target: " + target);
                return null;
        }
    }

    /** Deletes any temporary files from a pick session that did not complete. */
    private void cleanupPendingFiles() {
        if (pendingCameraFile != null && pendingCameraFile.exists()) {
            pendingCameraFile.delete();
            Log.d(TAG, "Cleaned up camera temp: " + pendingCameraFile.getName());
        }
        if (pendingCropOutputFile != null && pendingCropOutputFile.exists()) {
            pendingCropOutputFile.delete();
            Log.d(TAG, "Cleaned up crop output: " + pendingCropOutputFile.getName());
        }
        clearPendingState();
    }

    /** Clears all pending session state. */
    private void clearPendingState() {
        pendingCameraUri      = null;
        pendingCameraFile     = null;
        pendingCropOutputFile = null;
        pendingCallback       = null;
    }

    // =========================================================================
    // CALLBACK DELIVERY
    // =========================================================================

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