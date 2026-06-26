package li.masciul.sugardaddi.data.repository;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import li.masciul.sugardaddi.data.database.AppDatabase;
import li.masciul.sugardaddi.data.network.ApiConfig;

/**
 * CacheRepository - single home for the Settings "cached data" card.
 *
 * Cache management is inherently cross-cutting: it spans both the food_products and
 * recipes tables plus their shared nutrition rows, and it is network-free. Rather
 * than splitting half the logic onto ProductRepository (which would drag in a
 * NetworkManager) and half onto RecipeRepository, all of it lives here, depending
 * only on Room.
 *
 * THE MODEL - two orthogonal retention pins on every row:
 *   - isFavorite   (favourite pin)
 *   - localImport  (downloaded-dataset pin)
 * Clearing a "section" clears one pin; a row that still holds the other pin is
 * downgraded (kept), a row left with no pin is deleted:
 *
 *   Section 1  Searched items     no pin                  -> delete
 *   Section 2  Favourites         favourite pin           -> delete if no import pin,
 *                                                            else clear favourite pin
 *   Section 3  Downloaded source  import pin (per source) -> delete if no favourite
 *                                                            pin, else clear import pin
 *
 * Every operation is mirrored across both tables (FoodProductDao + RecipeDao) so that
 * a future importable recipe source is covered automatically. The actual SQL lives in
 * the DAOs' "CACHE MANAGEMENT (settings)" sections; this class composes them.
 *
 * SCOPE: Room only. Image-file purging (ImagePurgeManager) and resetting a source's
 * import state (SettingsProvider.resetDatabaseState) are infra/UI concerns and stay in
 * the Settings layer that orchestrates this repository.
 *
 * THREADING: every method is synchronous and annotated {@code @WorkerThread} - call
 * them off the main thread (e.g. from the caller's background executor). Each mutation
 * runs in a single Room transaction, so a section clear is all-or-nothing.
 */
public class CacheRepository {

    private static final String TAG = "CacheRepository";

    private final AppDatabase database;

    public CacheRepository(@NonNull Context context) {
        this.database = AppDatabase.getInstance(context.getApplicationContext());
    }

    // ========== COUNTS (for the card) ==========
    // Per-type, so each section can show products and recipes separately.

    /** Section 1 - browsed-cache products (no retention pin). */
    @WorkerThread
    public int getBrowsedProductCount() {
        return database.foodProductDao().getBrowsedCacheCount();
    }

    /** Section 1 - browsed-cache recipes (no retention pin). */
    @WorkerThread
    public int getBrowsedRecipeCount() {
        return database.recipeDao().getBrowsedCacheCount();
    }

    /** Section 2 - favourite products (favourited, downloaded or not). */
    @WorkerThread
    public int getFavoriteProductCount() {
        return database.foodProductDao().getFavoriteCount();
    }

    /** Section 2 - favourite recipes. */
    @WorkerThread
    public int getFavoriteRecipeCount() {
        return database.recipeDao().getFavoriteCount();
    }

    /** Section 3 - downloaded products for one source (localImport rows). */
    @WorkerThread
    public int getDownloadProductCount(@NonNull String sourceId) {
        return database.foodProductDao().getDownloadCountBySource(sourceId);
    }

    /** Section 3 - downloaded recipes for one source (0 until recipes are importable). */
    @WorkerThread
    public int getDownloadRecipeCount(@NonNull String sourceId) {
        return database.recipeDao().getDownloadCountBySource(sourceId);
    }

    // ========== CLEAR OPERATIONS ==========
    // Each runs in one transaction (all-or-nothing) and ends with a single orphaned-
    // nutrition sweep (deleteOrphanedNutrition covers both products and recipes).

    /**
     * Section 1 - clear the browsed search cache: rows with no retention pin
     * (localImport 0, isFavorite 0) in both tables. Favourites and downloaded datasets
     * are untouched.
     */
    @WorkerThread
    public void clearBrowsedCache() {
        database.runInTransaction(() -> {
            database.foodProductDao().deleteBrowsedCache();
            database.recipeDao().deleteBrowsedCache();
            database.nutritionDao().deleteOrphanedNutrition();
        });
        if (ApiConfig.DEBUG_LOGGING) Log.d(TAG, "Cleared browsed cache (products + recipes)");
    }

    /**
     * Section 2 - clear favourites: delete rows favourited with no import pin, and on
     * rows that are also downloaded, clear only the favourite pin (they stay as plain
     * dataset rows). Mirrored across both tables.
     */
    @WorkerThread
    public void clearFavorites() {
        database.runInTransaction(() -> {
            database.foodProductDao().deleteFavoritesOnly();
            database.foodProductDao().unmarkFavoriteOnDownloads();
            database.recipeDao().deleteFavoritesOnly();
            database.recipeDao().unmarkFavoriteOnDownloads();
            database.nutritionDao().deleteOrphanedNutrition();
        });
        if (ApiConfig.DEBUG_LOGGING) Log.d(TAG, "Cleared favourites (products + recipes)");
    }

    /**
     * Section 3 - remove one source's downloaded dataset: delete its dataset rows that
     * are not favourited, and on rows that are also favourited, clear only the import
     * pin (they stay as plain favourites). Mirrored across both tables.
     *
     * Room only - the caller resets the source's import state
     * (SettingsProvider.resetDatabaseState) and purges orphaned image files.
     *
     * @param sourceId The data source id whose dataset is being removed (e.g. "CIQUAL")
     */
    @WorkerThread
    public void removeDownloadedSource(@NonNull String sourceId) {
        database.runInTransaction(() -> {
            database.foodProductDao().deleteDownloadsOnlyBySource(sourceId);
            database.foodProductDao().unmarkDownloadOnFavoritesBySource(sourceId);
            database.recipeDao().deleteDownloadsOnlyBySource(sourceId);
            database.recipeDao().unmarkDownloadOnFavoritesBySource(sourceId);
            database.nutritionDao().deleteOrphanedNutrition();
        });
        if (ApiConfig.DEBUG_LOGGING) Log.d(TAG, "Removed downloaded dataset for source " + sourceId);
    }

    /**
     * "Clear all" - wipe every cached product and recipe regardless of pin, then sweep
     * their now-orphaned nutrition. Equivalent to clearing all three sections at once.
     *
     * Room only - the caller resets every local source's import state and purges all
     * image files.
     */
    @WorkerThread
    public void clearAll() {
        database.runInTransaction(() -> {
            database.foodProductDao().clearAllProducts();
            database.recipeDao().deleteAll();
            database.nutritionDao().deleteOrphanedNutrition();
        });
        if (ApiConfig.DEBUG_LOGGING) Log.d(TAG, "Cleared all cached products and recipes");
    }
}