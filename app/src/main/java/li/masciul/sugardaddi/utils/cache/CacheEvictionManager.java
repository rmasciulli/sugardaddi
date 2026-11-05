package li.masciul.sugardaddi.utils.cache;

import android.util.Log;
import androidx.annotation.WorkerThread;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import li.masciul.sugardaddi.data.database.AppDatabase;
import li.masciul.sugardaddi.data.network.ApiConfig;

/**
 * CacheEvictionManager - deletes cached products and recipes the user hasn't
 * opened within RETENTION_MS, freeing the database. Never evicted: bulk-imported
 * (localImport) rows, items with user data (favorite, custom photo, preferred
 * serving - user_item_data), items used by a meal, and user-created recipes.
 *
 * Row deletion only: image files are reclaimed by ImagePurgeManager's orphan
 * purge, which must run AFTER eviction so the evicted rows' images count as
 * orphans. SugarDaddiApplication sequences the two at startup.
 */
public class CacheEvictionManager {

    private static final String TAG = "CacheEvictionManager";

    /** Rows unviewed for longer than this are evicted (7 days). */
    public static final long RETENTION_MS = 7L * 24 * 60 * 60 * 1000;

    private final AppDatabase database;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public CacheEvictionManager(AppDatabase database) {
        this.database = database;
    }

    /** Fire-and-forget eviction (e.g. a Settings "clean up now" action). */
    public void evictExpiredAsync() {
        executor.execute(this::evictExpiredSync);
    }

    /**
     * Deletes expired product and recipe rows plus orphaned nutrition. Must run on
     * a background thread. Image cleanup is the orphan purge's job (run it after).
     */
    @WorkerThread
    public void evictExpiredSync() {
        try {
            long threshold = System.currentTimeMillis() - RETENTION_MS;
            // Portions of meals that no longer exist would otherwise keep their
            // items alive forever (the queries below skip any item a portion uses).
            database.mealPortionDao().deleteOrphans();
            int products  = database.foodProductDao().deleteExpiredProducts(threshold);
            int recipes   = database.recipeDao().deleteExpiredRecipes(threshold);
            int nutrition = database.nutritionDao().deleteOrphanedNutrition();
            // User data of items that no longer exist (e.g. a dataset removed with
            // deleteProductsBySource()).
            database.userItemDataDao().deleteOrphans();
            if (ApiConfig.DEBUG_LOGGING) {
                Log.d(TAG, "Eviction: " + products + " product(s), " + recipes
                        + " recipe(s), " + nutrition + " orphaned nutrition row(s) removed");
            }
        } catch (Exception e) {
            Log.e(TAG, "Cache eviction failed", e);
        }
    }

    public void shutdown() {
        executor.shutdown();
    }
}