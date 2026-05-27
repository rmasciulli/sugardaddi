package li.masciul.sugardaddi.utils.image;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import li.masciul.sugardaddi.data.database.AppDatabase;
import li.masciul.sugardaddi.data.database.entities.RecipeEntity;
import li.masciul.sugardaddi.core.models.RecipeStepMetadata;

/**
 * ImagePurgeManager — Orphan image cleanup for SugarDaddi.
 *
 * RESPONSIBILITIES
 * ================
 * - At startup (or on demand), scan all image subdirectories under sugardaddi/
 * - Cross-reference every file found on disk against known paths stored in Room
 * - Delete any file that is no longer referenced by any entity
 *
 * WHY THIS EXISTS
 * ================
 * Immediate deletion at the point of unfavouriting / meal deletion / step deletion
 * is the primary mechanism for keeping the image library clean. This class is the
 * safety net: if an error interrupts a deletion (crash, power loss, race condition),
 * orphaned files would accumulate indefinitely without a purge mechanism.
 *
 * THIS CLASS IS NOT a replacement for immediate deletion — it is a background
 * sanity check that runs once per app launch and can also be triggered manually
 * from the Settings screen (future feature).
 *
 * WHAT THIS CLASS DOES NOT DO
 * ============================
 * - No image processing     → ImageProcessor
 * - No downloading          → ThumbnailDownloader
 * - No camera/gallery UI    → ImagePickerHelper
 * - No gallery scanning     → ImageStorageManager
 *
 * DIRECTORY COVERAGE
 * ==================
 * Purge covers all four managed subdirectories:
 *
 *   sugardaddi/cache/thumbnails/  ← cross-referenced against food_products.localImagePath
 *                                    AND recipes.localImagePath
 *   sugardaddi/photos/products/   ← cross-referenced against food_products.localImagePath
 *   sugardaddi/photos/meals/      ← cross-referenced against meals.localImagePath
 *   sugardaddi/photos/steps/      ← cross-referenced against all recipes.stepStructure
 *                                    JSON blobs (deserialised via Room's converter)
 *
 * STEP PHOTO CROSS-REFERENCING (Option A — path-based)
 * =====================================================
 * RecipeStepMetadata is serialised to JSON inside RecipeEntity.stepStructure.
 * It is NOT a separate Room table. At purge time, we load all RecipeEntity rows,
 * iterate their stepStructure list (already deserialised by Room's TypeConverter),
 * and collect every non-null localImagePath. This is slightly heavier than a simple
 * SQL query but acceptable given:
 *   - The dataset is small (pre-release, few cached recipes)
 *   - Purge runs once on a background thread at startup
 *   - Avoids adding schema complexity for an edge-case cleanup operation
 *
 * REQUIRED DAO ADDITIONS
 * =======================
 * This class requires the following queries to be added to the respective DAOs
 * BEFORE this class can compile. Add them alongside the existing DAO queries:
 *
 * FoodProductDao:
 *   @Query("SELECT localImagePath FROM food_products WHERE localImagePath IS NOT NULL")
 *   List<String> getAllLocalImagePaths();
 *
 * RecipeDao:
 *   @Query("SELECT localImagePath FROM recipes WHERE localImagePath IS NOT NULL")
 *   List<String> getAllLocalImagePaths();
 *
 *   @Query("SELECT * FROM recipes WHERE stepStructure IS NOT NULL")
 *   List<RecipeEntity> getAllWithStepStructure();
 *
 * MealDao:
 *   @Query("SELECT localImagePath FROM meals WHERE localImagePath IS NOT NULL")
 *   List<String> getAllLocalImagePaths();
 *
 * THREADING
 * =========
 * All public methods are fire-and-forget: they submit work to a single-thread
 * background executor and return immediately. This is intentional — purge must
 * never block the main thread or delay app startup visibly.
 *
 * Callbacks (success/completion) are not provided — purge is silent by design.
 * Results are logged at DEBUG level for development tracing.
 *
 * USAGE (from SugarDaddiApplication.onCreate())
 * ==============================================
 * <pre>
 *   ImageStorageManager storageManager = new ImageStorageManager(this);
 *   ImagePurgeManager purgeManager = new ImagePurgeManager(this, storageManager);
 *   purgeManager.purgeOrphansAsync(); // fire-and-forget, safe to call every launch
 * </pre>
 */
public class ImagePurgeManager {

    private static final String TAG = "SugarDaddi_Images";

    // =========================================================================
    // STATE
    // =========================================================================

    private final Context context;
    private final ImageStorageManager storageManager;
    private final AppDatabase database;

    /**
     * Single-thread executor dedicated to purge operations.
     * Single-threaded to prevent concurrent directory scans from racing each other.
     */
    private final ExecutorService purgeExecutor;

    // =========================================================================
    // CONSTRUCTOR
    // =========================================================================

    /**
     * @param context        Application context.
     * @param storageManager Used to access managed directory references.
     *                       Should be the application-scoped singleton instance.
     */
    public ImagePurgeManager(
            @NonNull Context context,
            @NonNull ImageStorageManager storageManager) {
        this.context        = context.getApplicationContext();
        this.storageManager = storageManager;
        this.database       = AppDatabase.getInstance(this.context);
        this.purgeExecutor  = Executors.newSingleThreadExecutor();
    }

    // =========================================================================
    // PUBLIC API
    // =========================================================================

    /**
     * Scans all managed image directories, cross-references every file against
     * Room, and deletes any file that is no longer referenced.
     *
     * Fire-and-forget — returns immediately. Results are logged only.
     * Safe to call on every app launch (fast when no orphans exist).
     *
     * This method is idempotent: calling it multiple times concurrently is safe
     * because the single-thread executor queues submissions rather than running
     * them in parallel.
     */
    public void purgeOrphansAsync() {
        purgeExecutor.execute(() -> {
            try {
                Log.d(TAG, "ImagePurgeManager: starting orphan scan");
                long startMs = System.currentTimeMillis();

                int deleted = purgeOrphans();

                long elapsed = System.currentTimeMillis() - startMs;
                Log.i(TAG, "ImagePurgeManager: purge complete — "
                        + deleted + " orphan(s) deleted in " + elapsed + "ms");

            } catch (Exception e) {
                // Non-fatal — log and continue. The app functions normally without purge.
                Log.e(TAG, "ImagePurgeManager: unexpected error during purge", e);
            }
        });
    }

    /**
     * Synchronous purge — for testing or for explicit user-triggered cleanup.
     *
     * Must be called from a background thread (annotated accordingly).
     * Returns the count of files deleted.
     *
     * @return Number of orphan files deleted.
     */
    @WorkerThread
    public int purgeOrphansSync() {
        return purgeOrphans();
    }

    // =========================================================================
    // CORE PURGE LOGIC
    // =========================================================================

    /**
     * Collects all known image paths from Room, then scans all managed
     * directories and deletes any file whose path is not in the known set.
     *
     * @return Number of files deleted.
     */
    @WorkerThread
    private int purgeOrphans() {
        // ── 1. Collect all paths currently referenced in Room ─────────────────
        Set<String> knownPaths = collectKnownPaths();
        Log.d(TAG, "Known image paths in Room: " + knownPaths.size());

        // ── 2. Scan each managed directory and purge orphans ──────────────────
        int totalDeleted = 0;
        totalDeleted += scanAndPurge(storageManager.getThumbnailsDir(),   knownPaths, "thumbnails");
        totalDeleted += scanAndPurge(storageManager.getProductPhotosDir(), knownPaths, "photos/products");
        totalDeleted += scanAndPurge(storageManager.getMealsDir(),         knownPaths, "photos/meals");
        totalDeleted += scanAndPurge(storageManager.getStepsDir(),         knownPaths, "photos/steps");

        return totalDeleted;
    }

    /**
     * Builds a Set of all absolute file paths currently referenced by any entity
     * in Room. A file whose path is in this set is "known" and must not be deleted.
     *
     * Sources:
     *   - food_products.localImagePath   (thumbnails + product hero images)
     *   - recipes.localImagePath         (thumbnails + recipe hero images)
     *   - meals.localImagePath                (meal journal photos)
     *   - recipes.stepStructure[*].localImagePath  (recipe step photos, from JSON)
     *
     * @return Immutable set of known absolute file paths. Never null.
     */
    @WorkerThread
    @NonNull
    private Set<String> collectKnownPaths() {
        Set<String> knownPaths = new HashSet<>();

        try {
            // ── Food product local image paths ────────────────────────────────
            // Covers both cache/thumbnails/ and photos/products/ for products.
            // Requires FoodProductDao.getAllLocalImagePaths() — see class javadoc.
            List<String> productPaths = database.foodProductDao().getAllLocalImagePaths();
            if (productPaths != null) {
                knownPaths.addAll(productPaths);
                Log.d(TAG, "  Product image paths: " + productPaths.size());
            }

        } catch (Exception e) {
            Log.w(TAG, "Failed to load product image paths from Room — skipping", e);
        }

        try {
            // ── Recipe local image paths ──────────────────────────────────────
            // Covers both cache/thumbnails/ and photos/products/ for recipes
            // (if hero image override is ever added for recipes).
            // Requires RecipeDao.getAllLocalImagePaths() — see class javadoc.
            List<String> recipePaths = database.recipeDao().getAllLocalImagePaths();
            if (recipePaths != null) {
                knownPaths.addAll(recipePaths);
                Log.d(TAG, "  Recipe image paths: " + recipePaths.size());
            }

        } catch (Exception e) {
            Log.w(TAG, "Failed to load recipe image paths from Room — skipping", e);
        }

        try {
            // ── Meal photo paths ──────────────────────────────────────────────
            // Covers photos/meals/.
            // Requires MealDao.getAllLocalImagePaths() — see class javadoc.
            List<String> mealPaths = database.mealDao().getAllLocalImagePaths();
            if (mealPaths != null) {
                knownPaths.addAll(mealPaths);
                Log.d(TAG, "  Meal photo paths: " + mealPaths.size());
            }

        } catch (Exception e) {
            Log.w(TAG, "Failed to load meal photo paths from Room — skipping", e);
        }

        try {
            // ── Recipe step photo paths ───────────────────────────────────────
            // Covers photos/steps/.
            //
            // RecipeStepMetadata is serialised to JSON inside RecipeEntity.stepStructure.
            // Room's RecipeStepMetadataListConverter deserialises it automatically
            // when we call entity.getStepStructure() — no manual Gson call needed here.
            //
            // We load all recipes that have a non-null stepStructure column,
            // iterate their steps, and collect every non-null localImagePath.
            //
            // Requires RecipeDao.getAllWithStepStructure() — see class javadoc.
            List<RecipeEntity> recipesWithSteps = database.recipeDao().getAllWithStepStructure();
            if (recipesWithSteps != null) {
                List<String> stepPaths = extractStepPhotoPaths(recipesWithSteps);
                knownPaths.addAll(stepPaths);
                Log.d(TAG, "  Step photo paths: " + stepPaths.size()
                        + " (from " + recipesWithSteps.size() + " recipe(s))");
            }

        } catch (Exception e) {
            Log.w(TAG, "Failed to load step photo paths from Room — skipping", e);
        }

        return knownPaths;
    }

    /**
     * Scans a single directory and deletes any file whose absolute path is not
     * in {@code knownPaths}.
     *
     * Only deletes regular files — never touches subdirectories.
     * Hidden files (starting with '.') are skipped.
     * Returns 0 and logs a warning if the directory is null or doesn't exist.
     *
     * @param dir       The directory to scan. May be null if external storage
     *                  is unavailable (ImageStorageManager returns null in that case).
     * @param knownPaths The set of paths that must NOT be deleted.
     * @param label     Human-readable directory label for log messages.
     * @return Number of files deleted from this directory.
     */
    @WorkerThread
    private int scanAndPurge(
            @NonNull File dir,
            @NonNull Set<String> knownPaths,
            @NonNull String label) {

        if (!dir.exists() || !dir.isDirectory()) {
            Log.d(TAG, "  [" + label + "] directory absent — nothing to scan");
            return 0;
        }

        File[] files = dir.listFiles();
        if (files == null || files.length == 0) {
            Log.d(TAG, "  [" + label + "] empty — nothing to purge");
            return 0;
        }

        int deleted = 0;
        for (File file : files) {
            // Skip directories and hidden files — we only manage flat file lists
            if (!file.isFile() || file.getName().startsWith(".")) {
                continue;
            }

            String absolutePath = file.getAbsolutePath();
            if (!knownPaths.contains(absolutePath)) {
                // Orphan — not referenced by any Room entity.
                boolean success = file.delete();
                if (success) {
                    deleted++;
                    Log.d(TAG, "  [" + label + "] deleted orphan: " + file.getName());
                } else {
                    Log.w(TAG, "  [" + label + "] failed to delete orphan: " + file.getName());
                }
            }
        }

        Log.d(TAG, "  [" + label + "] scanned " + files.length
                + " file(s), deleted " + deleted + " orphan(s)");
        return deleted;
    }

    // =========================================================================
    // STEP PHOTO PATH EXTRACTION
    // =========================================================================

    /**
     * Extracts all non-null {@code localImagePath} values from the stepStructure of
     * each provided RecipeEntity.
     *
     * Room's {@code RecipeStepMetadataListConverter} automatically deserialises
     * the {@code stepStructure} JSON column when {@code getStepStructure()} is
     * called — no manual Gson parsing is needed here.
     *
     * @param recipes List of RecipeEntity rows (should be those with non-null stepStructure).
     * @return List of absolute photo paths found across all recipe steps.
     */
    @WorkerThread
    @NonNull
    private List<String> extractStepPhotoPaths(@NonNull List<RecipeEntity> recipes) {
        List<String> paths = new ArrayList<>();

        for (RecipeEntity recipe : recipes) {
            List<RecipeStepMetadata> steps = recipe.getStepStructure();
            if (steps == null || steps.isEmpty()) continue;

            for (RecipeStepMetadata step : steps) {
                // localImagePath is the locally stored user photo.
                // imageUrl is the remote image from TheMealDB/TheCocktailDB — never stored locally.
                String photoPath = step.getLocalImagePath();
                if (photoPath != null && !photoPath.trim().isEmpty()) {
                    paths.add(photoPath);
                }
            }
        }

        return paths;
    }

    // =========================================================================
    // LIFECYCLE
    // =========================================================================

    /**
     * Shuts down the purge executor cleanly.
     *
     * Any in-progress purge will complete before shutdown.
     * Safe to call from Application.onTerminate() if needed, though in practice
     * the process is killed directly on Android and this is rarely necessary.
     */
    public void shutdown() {
        purgeExecutor.shutdown();
    }
}