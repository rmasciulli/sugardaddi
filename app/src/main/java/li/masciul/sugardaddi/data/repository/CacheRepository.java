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
 * THE MODEL - what keeps a cached row ("pins"):
 *   - localImport            downloaded-dataset pin (column on the row)
 *   - favorite               user_item_data.isFavorite
 *   - customisation          user_item_data: custom photo or preferred serving
 *   - used by a meal         meal_portions (journal, not cache)
 *   - user-created recipe    dataSource = 'USER' (the user's content, not cache)
 * Clearing a section removes one kind of pin; a row still held by another pin is
 * kept, a row left with none is deleted:
 *
 *   Section 1  Searched items     held by no pin               -> delete
 *   Section 2  Favorites          favorite pin                 -> delete if held by
 *                                                                  nothing else; pin removed
 *   Section 3  Downloaded source   import pin (per source)      -> delete if held by
 *                                                                  nothing else; pin removed
 *   Clear all                      favorite + import pins       -> delete if held by
 *                                                                  nothing else; pins removed
 *   Reset customisations           custom photos + servings     -> pins removed; the rows
 *                                                                  become plain cache
 *
 * Every operation is mirrored across both tables (FoodProductDao + RecipeDao) so that
 * a future importable recipe source is covered automatically. The actual SQL lives in
 * the DAOs' "CACHE MANAGEMENT (settings)" sections; this class composes them.
 *
 * SCOPE: Room only. Image-file purging (ImagePurgeManager) and resetting a source's
 * import state (ManagementProvider.resetDatabaseState) are infra/UI concerns and stay in
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

    /** Section 2 - favorite products (favorited, downloaded or not). */
    @WorkerThread
    public int getFavoriteProductCount() {
        return database.foodProductDao().getFavoriteCount();
    }

    /** Section 2 - favorite recipes. */
    @WorkerThread
    public int getFavoriteRecipeCount() {
        return database.recipeDao().getFavoriteCount();
    }

    /** Items with a customisation (custom photo or preferred serving), both types. */
    @WorkerThread
    public int getCustomisedCount() {
        return database.userItemDataDao().getCustomisedCount();
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
    // No operation deletes an item held by another pin (see THE MODEL above).

    /**
     * Section 1 - clear the browsed search cache: rows held by no pin, in both
     * tables. Favorites, customised items, datasets, meal items and user-created
     * recipes are untouched.
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
     * Section 2 - clear favorites: delete the rows held only by their favorite
     * pin, then remove every favorite pin. Rows also held by a customisation, a
     * dataset or a meal stay.
     */
    @WorkerThread
    public void clearFavorites() {
        database.runInTransaction(() -> {
            database.foodProductDao().deleteFavoritesOnly();
            database.recipeDao().deleteFavoritesOnly();
            database.userItemDataDao().clearAllFavorites();
            database.nutritionDao().deleteOrphanedNutrition();
        });
        if (ApiConfig.DEBUG_LOGGING) Log.d(TAG, "Cleared favorites (products + recipes)");
    }

    /**
     * Section 3 - remove one source's downloaded dataset: delete its dataset rows
     * held by nothing else, and the ones that remain (favorite, customisation,
     * meal) lose the import pin. Mirrored across both tables.
     *
     * Room only - the caller resets the source's import state
     * (ManagementProvider.resetDatabaseState) and purges orphaned image files.
     *
     * @param sourceId The data source id whose dataset is being removed (e.g. "CIQUAL")
     */
    @WorkerThread
    public void removeDownloadedSource(@NonNull String sourceId) {
        database.runInTransaction(() -> {
            database.foodProductDao().deleteDownloadsOnlyBySource(sourceId);
            database.foodProductDao().unmarkDownloadsBySource(sourceId);
            database.recipeDao().deleteDownloadsOnlyBySource(sourceId);
            database.recipeDao().unmarkDownloadsBySource(sourceId);
            database.nutritionDao().deleteOrphanedNutrition();
        });
        if (ApiConfig.DEBUG_LOGGING) Log.d(TAG, "Removed downloaded dataset for source " + sourceId);
    }

    /**
     * "Clear all" - the three sections at once: favorite and import pins removed,
     * then every row held by nothing else deleted. Kept: customised items (the
     * user's photos and servings; see resetCustomisations()), meal items and
     * user-created recipes, as plain rows.
     *
     * Room only - the caller resets every local source's import state and purges
     * the image files no row refers to any more.
     */
    @WorkerThread
    public void clearAll() {
        database.runInTransaction(() -> {
            database.userItemDataDao().clearAllFavorites();
            database.foodProductDao().deleteAllExceptKept();
            database.foodProductDao().unmarkAllDownloads();
            database.recipeDao().deleteAllExceptKept();
            database.recipeDao().unmarkAllDownloads();
            database.nutritionDao().deleteOrphanedNutrition();
        });
        if (ApiConfig.DEBUG_LOGGING) Log.d(TAG, "Cleared all cached products and recipes");
    }

    /**
     * "Reset my customisations" - remove every custom photo and preferred serving
     * (favorites stay). The items become plain cache, subject to the usual rules
     * from now on; nothing is deleted here.
     *
     * Room only - the caller purges the custom photo files, now orphans.
     */
    @WorkerThread
    public void resetCustomisations() {
        database.userItemDataDao().clearAllCustomisations();
        if (ApiConfig.DEBUG_LOGGING) Log.d(TAG, "Reset all customisations");
    }
}
