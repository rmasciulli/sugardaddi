package li.masciul.sugardaddi.utils.image;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import li.masciul.sugardaddi.core.models.RecipeStepMetadata;
import li.masciul.sugardaddi.data.database.AppDatabase;
import li.masciul.sugardaddi.data.database.entities.RecipeEntity;

/**
 * ImagePurgeManager - Orphan image cleanup for SugarDaddi.
 *
 * RESPONSIBILITIES
 * ================
 * - At startup (or on demand), scan all image directories under sugardaddi/
 * - Cross-reference every file on disk against all paths stored in Room
 * - Delete any file no longer referenced by any entity
 *
 * WHY THIS EXISTS
 * ================
 * Immediate deletion at the point of unfavouriting / item deletion is the primary
 * mechanism keeping the library clean. This class is the safety net: if an error
 * interrupts a deletion (crash, power loss, race condition), orphaned files would
 * accumulate indefinitely without a background sweep.
 *
 * THIS IS NOT a replacement for immediate deletion - it is a startup sanity check
 * that can also be triggered manually from Settings.
 *
 * WHAT THIS CLASS DOES NOT DO
 * ============================
 * - No image processing  → ImageProcessor
 * - No downloading       → ThumbnailDownloader
 * - No camera/gallery UI → ImagePickerHelper
 * - No gallery scanning  → ImageStorageManager
 *
 * DIRECTORY COVERAGE - five directories, each cross-referenced independently:
 *
 *   sugardaddi/thumbnails/  → food_products.thumbnailPath + userThumbnailPath
 *                             + recipes.thumbnailPath + userThumbnailPath
 *   sugardaddi/products/    → food_products.imagePath + userImagePath
 *   sugardaddi/recipes/     → recipes.imagePath + userImagePath
 *   sugardaddi/meals/       → meals.userImagePath
 *   sugardaddi/steps/       → all recipes.stepStructure[*].userImagePath (JSON)
 *
 * STEP PHOTO CROSS-REFERENCING
 * =============================
 * RecipeStepMetadata is serialised to JSON inside RecipeEntity.stepStructure.
 * It is NOT a separate Room table. At purge time, all recipe entities with a
 * non-null stepStructure are loaded; Room's RecipeStepMetadataListConverter
 * deserialises the JSON automatically when getStepStructure() is called.
 * Each step's imagePath and userImagePath are then extracted. This is slightly
 * heavier than a simple SQL query but the dataset is small and the purge runs
 * only once per launch on a background thread.
 *
 * REQUIRED DAO METHODS
 * ====================
 * FoodProductDao.getAllLocalImagePaths()  - unions all four product image columns
 * RecipeDao.getAllLocalImagePaths()       - unions all four recipe image columns
 * RecipeDao.getAllWithStepStructure()     - recipes whose stepStructure is non-null
 * MealDao.getAllLocalImagePaths()         - meals.userImagePath
 * (See each DAO for the authoritative SQL - deliberately not duplicated here.)
 *
 * THREADING
 * =========
 * All public methods are fire-and-forget - they submit work to a dedicated
 * single-thread executor and return immediately. Purge must never block the
 * main thread or delay visible app startup.
 *
 * USAGE (from SugarDaddiApplication.onCreate())
 * ==============================================
 * <pre>
 *   imagePurgeManager.purgeOrphansAsync(); // fire-and-forget, safe every launch
 * </pre>
 */
public class ImagePurgeManager {

    private static final String TAG = "SugarDaddi_Images";

    // =========================================================================
    // STATE
    // =========================================================================

    private final ImageStorageManager storageManager;
    private final AppDatabase         database;

    /**
     * Single-thread executor dedicated to purge operations.
     * Single-threaded to prevent concurrent directory scans from racing.
     */
    private final ExecutorService purgeExecutor;

    // =========================================================================
    // CONSTRUCTOR
    // =========================================================================

    /**
     * @param context        Application context.
     * @param storageManager Application-scoped singleton for directory access.
     */
    public ImagePurgeManager(
            @NonNull Context context,
            @NonNull ImageStorageManager storageManager) {
        this.storageManager = storageManager;
        this.database       = AppDatabase.getInstance(context.getApplicationContext());
        this.purgeExecutor  = Executors.newSingleThreadExecutor();
    }

    // =========================================================================
    // PUBLIC API
    // =========================================================================

    /**
     * Scans all managed directories, cross-references every file against Room,
     * and deletes any orphan. Fire-and-forget - returns immediately.
     *
     * Safe to call every launch. Fast when no orphans exist (directory listing
     * + Set lookup only). Idempotent: multiple concurrent calls are queued by
     * the single-thread executor, not run in parallel.
     */
    public void purgeOrphansAsync() {
        purgeExecutor.execute(() -> {
            try {
                Log.d(TAG, "ImagePurgeManager: starting orphan scan");
                long start   = System.currentTimeMillis();
                int  deleted = purgeOrphans();
                long elapsed = System.currentTimeMillis() - start;
                Log.i(TAG, "ImagePurgeManager: complete - "
                        + deleted + " orphan(s) deleted in " + elapsed + "ms");
            } catch (Exception e) {
                // Non-fatal - the app functions normally without purge.
                Log.e(TAG, "ImagePurgeManager: unexpected error during purge", e);
            }
        });
    }

    /**
     * Deletes EVERY file in all managed image directories, WITHOUT consulting
     * Room. Use only after a destructive database recreation: the DB has been
     * wiped, so every image file is necessarily orphaned and cross-referencing
     * an empty Room would be pointless (and the orphan sweep would do it only by
     * accident of the set being empty). Fire-and-forget.
     */
    public void purgeAllAsync() {
        purgeExecutor.execute(() -> {
            try {
                int deleted = purgeAll();
                Log.i(TAG, "ImagePurgeManager: full wipe - " + deleted + " file(s) deleted");
            } catch (Exception e) {
                Log.e(TAG, "ImagePurgeManager: error during full wipe", e);
            }
        });
    }

    @WorkerThread
    private int purgeAll() {
        Set<String> none = java.util.Collections.emptySet();   // nothing is "known" → delete all
        int total = 0;
        total += scanAndPurge(storageManager.getThumbnailsDir(), none, "thumbnails");
        total += scanAndPurge(storageManager.getProductsDir(),   none, "products");
        total += scanAndPurge(storageManager.getRecipesDir(),    none, "recipes");
        total += scanAndPurge(storageManager.getMealsDir(),      none, "meals");
        total += scanAndPurge(storageManager.getStepsDir(),      none, "steps");
        return total;
    }
    
    /**
     * Synchronous purge - for testing or user-triggered cleanup from Settings.
     *
     * Must be called from a background thread.
     *
     * @return Number of orphan files deleted.
     */
    @WorkerThread
    public int purgeOrphansSync() {
        return purgeOrphans();
    }

    /**
     * Shuts down the purge executor.
     * Safe to call from Application.onTerminate() if needed.
     */
    public void shutdown() {
        purgeExecutor.shutdown();
    }

    // =========================================================================
    // CORE PURGE LOGIC
    // =========================================================================

    @WorkerThread
    private int purgeOrphans() {
        // ── 1. Collect all paths currently known to Room ──────────────────────
        Set<String> knownPaths = collectKnownPaths();
        Log.d(TAG, "Known image paths in Room: " + knownPaths.size());

        // ── 2. Scan each directory and delete anything not in the known set ───
        int total = 0;
        total += scanAndPurge(storageManager.getThumbnailsDir(), knownPaths, "thumbnails");
        total += scanAndPurge(storageManager.getProductsDir(),   knownPaths, "products");
        total += scanAndPurge(storageManager.getRecipesDir(),    knownPaths, "recipes");
        total += scanAndPurge(storageManager.getMealsDir(),      knownPaths, "meals");
        total += scanAndPurge(storageManager.getStepsDir(),      knownPaths, "steps");
        return total;
    }

    /**
     * Builds the complete set of absolute file paths referenced by any entity in Room.
     *
     * Covered fields:
     *   food_products: thumbnailPath, imagePath, userThumbnailPath, userImagePath
     *   recipes:       thumbnailPath, imagePath, userThumbnailPath, userImagePath
     *   meals:         userImagePath
     *   RecipeStepMetadata: imagePath, userImagePath (steps/, via JSON deserialization)
     */
    @WorkerThread
    @NonNull
    private Set<String> collectKnownPaths() {
        Set<String> knownPaths = new HashSet<>();

        // ── Food product paths (thumbnail + hero) ─────────────────────────────
        try {
            List<String> paths = database.foodProductDao().getAllLocalImagePaths();
            if (paths != null) {
                knownPaths.addAll(paths);
                Log.d(TAG, "  Product paths: " + paths.size());
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to load product image paths - skipping", e);
        }

        // ── Recipe paths (thumbnail + hero) ───────────────────────────────────
        try {
            List<String> paths = database.recipeDao().getAllLocalImagePaths();
            if (paths != null) {
                knownPaths.addAll(paths);
                Log.d(TAG, "  Recipe paths: " + paths.size());
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to load recipe image paths - skipping", e);
        }

        // ── Meal photo paths ──────────────────────────────────────────────────
        try {
            List<String> paths = database.mealDao().getAllLocalImagePaths();
            if (paths != null) {
                knownPaths.addAll(paths);
                Log.d(TAG, "  Meal paths: " + paths.size());
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to load meal photo paths - skipping", e);
        }

        // ── Recipe step photo paths (from JSON stepStructure) ─────────────────
        try {
            List<RecipeEntity> recipes = database.recipeDao().getAllWithStepStructure();
            if (recipes != null) {
                List<String> stepPaths = extractStepPhotoPaths(recipes);
                knownPaths.addAll(stepPaths);
                Log.d(TAG, "  Step paths: " + stepPaths.size()
                        + " (from " + recipes.size() + " recipe(s))");
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to load step photo paths - skipping", e);
        }

        return knownPaths;
    }

    /**
     * Scans a single directory and deletes any file not in {@code knownPaths}.
     *
     * Only regular files are deleted - subdirectories are never touched.
     * Hidden files (starting with '.') are skipped.
     * Null directories (external storage unavailable) return 0 gracefully.
     *
     * @param dir        The directory to scan. Null-safe.
     * @param knownPaths Paths that must NOT be deleted.
     * @param label      Human-readable label for log messages.
     * @return Number of files deleted from this directory.
     */
    @WorkerThread
    private int scanAndPurge(
            @Nullable File dir,
            @NonNull Set<String> knownPaths,
            @NonNull String label) {

        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            Log.d(TAG, "  [" + label + "] absent - skipping");
            return 0;
        }

        File[] files = dir.listFiles();
        if (files == null || files.length == 0) {
            Log.d(TAG, "  [" + label + "] empty - nothing to purge");
            return 0;
        }

        int deleted = 0;
        for (File file : files) {
            if (!file.isFile() || file.getName().startsWith(".")) continue;

            if (!knownPaths.contains(file.getAbsolutePath())) {
                boolean success = file.delete();
                if (success) {
                    deleted++;
                    Log.d(TAG, "  [" + label + "] deleted orphan: " + file.getName());
                } else {
                    Log.w(TAG, "  [" + label + "] failed to delete: " + file.getName());
                }
            }
        }

        Log.d(TAG, "  [" + label + "] scanned " + files.length
                + ", deleted " + deleted);
        return deleted;
    }

    // =========================================================================
    // STEP PHOTO EXTRACTION
    // =========================================================================

    /**
     * Extracts all non-null step image paths (imagePath + userImagePath) from
     * the step structures of the given recipe entities.
     *
     * Room's RecipeStepMetadataListConverter automatically deserialises the
     * stepStructure JSON column - no manual Gson parsing is needed here.
     */
    @WorkerThread
    @NonNull
    private List<String> extractStepPhotoPaths(@NonNull List<RecipeEntity> recipes) {
        List<String> paths = new ArrayList<>();

        for (RecipeEntity recipe : recipes) {
            List<RecipeStepMetadata> steps = recipe.getStepStructure();
            if (steps == null || steps.isEmpty()) continue;

            for (RecipeStepMetadata step : steps) {
                // imageUrl      - remote image from TheMealDB/TheCocktailDB, never stored locally
                // imagePath     - auto-cached local copy of imageUrl (downloaded on favourite)
                // userImagePath - user-defined local photo set via ImagePickerHelper
                String imagePath = step.getImagePath();
                if (imagePath != null && !imagePath.trim().isEmpty()) {
                    paths.add(imagePath);
                }
                String userImagePath = step.getUserImagePath();
                if (userImagePath != null && !userImagePath.trim().isEmpty()) {
                    paths.add(userImagePath);
                }
            }
        }

        return paths;
    }
}