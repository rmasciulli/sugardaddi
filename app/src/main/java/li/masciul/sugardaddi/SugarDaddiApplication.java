package li.masciul.sugardaddi;

import android.app.Application;
import android.content.Context;
import android.util.Log;

import li.masciul.sugardaddi.data.database.AppDatabase;
import li.masciul.sugardaddi.data.network.ApiConfig;
import li.masciul.sugardaddi.data.sources.base.DataSource;
import li.masciul.sugardaddi.data.sources.openfoodfacts.OpenFoodFactsConstants;
import li.masciul.sugardaddi.managers.DataSourceManager;
import li.masciul.sugardaddi.managers.LanguageManager;
import li.masciul.sugardaddi.managers.ThemeManager;
import li.masciul.sugardaddi.utils.image.ImagePurgeManager;
import li.masciul.sugardaddi.utils.image.ImageStorageManager;
import li.masciul.sugardaddi.utils.image.ThumbnailDownloader;
import li.masciul.sugardaddi.utils.language.LanguageDetector;

import java.util.List;

/**
 * SugarDaddiApplication - global application initialisation.
 *
 * Responsibilities:
 *   1. Language context wrapping (attachBaseContext)
 *   2. Theme initialisation
 *   3. Room database warm-up
 *   4. DataSourceManager singleton boot - sources initialise themselves in parallel,
 *      each reading its own persisted enabled flag (no central source config here)
 *   5. Network system defaults (User-Agent)
 *   6. Performance monitoring toggle
 */
public class SugarDaddiApplication extends Application {

    private static final String TAG = ApiConfig.UI_LOG_TAG;

    // =========================================================================
    // IMAGE SYSTEM SINGLETONS
    // =========================================================================

    /**
     * Application-scoped image storage manager.
     * Provides directory access and file path construction for all image categories.
     * Safe to call from any thread after onCreate() completes.
     */
    private ImageStorageManager imageStorageManager;

    /**
     * Application-scoped thumbnail downloader.
     * Downloads and caches remote thumbnails for favourited items.
     * Owns a dedicated single-thread executor and OkHttpClient.
     */
    private ThumbnailDownloader thumbnailDownloader;

    /**
     * Application-scoped orphan image purger.
     * Scans all managed directories at startup and deletes unreferenced files.
     * Owns a dedicated single-thread executor.
     */
    private ImagePurgeManager imagePurgeManager;

    // =========================================================================
    // CONTEXT WRAPPING - must happen before onCreate
    // =========================================================================

    @Override
    protected void attachBaseContext(Context base) {
        // Apply the user's saved language before any Activity creates its context
        LanguageManager.SupportedLanguage lang = LanguageManager.getCurrentLanguage(base);
        super.attachBaseContext(LanguageManager.applyLanguageToContext(base, lang));
    }

    // =========================================================================
    // APPLICATION LIFECYCLE
    // =========================================================================

    @Override
    public void onCreate() {
        super.onCreate();

        if (ApiConfig.DEBUG_LOGGING) Log.d(TAG, "SugarDaddi starting…");

        initializeLanguageSystem();
        initializeThemeSystem();
        initializeDatabaseSystem();
        initializeImageSystem();
        initializeDataSourceSystem();
        initializeNetworkSystem();
        initializePerformanceMonitoring();

        if (ApiConfig.DEBUG_LOGGING) Log.d(TAG, "SugarDaddi init complete");
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        cleanupResources();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        if (ApiConfig.DEBUG_LOGGING) Log.w(TAG, "Low memory - consider clearing caches");
    }

    // =========================================================================
    // LANGUAGE
    // =========================================================================

    private void initializeLanguageSystem() {
        try {
            LanguageManager.initializeLanguage(this);
            if (ApiConfig.DEBUG_LOGGING) {
                LanguageManager.SupportedLanguage lang = LanguageManager.getCurrentLanguage(this);
                Log.d(TAG, "Language: " + lang.getDisplayName());
            }
        } catch (Exception e) {
            Log.e(TAG, "Language init failed", e);
        }
    }

    // =========================================================================
    // THEME
    // =========================================================================

    private void initializeThemeSystem() {
        try {
            ThemeManager.initializeTheme(this);
            if (ApiConfig.DEBUG_LOGGING) {
                Log.d(TAG, "Theme: " + ThemeManager.getCurrentTheme(this).getValue());
            }
        } catch (Exception e) {
            Log.e(TAG, "Theme init failed", e);
        }
    }

    // =========================================================================
    // DATABASE
    // =========================================================================

    private void initializeDatabaseSystem() {
        try {
            // Pre-warm Room so the first query doesn't block the UI thread
            AppDatabase.getInstance(this);
            if (ApiConfig.DEBUG_LOGGING) Log.d(TAG, "Database ready");
        } catch (Exception e) {
            Log.e(TAG, "Database init failed", e);
        }
    }

    // =========================================================================
    // IMAGE SYSTEM
    // =========================================================================

    /**
     * Initialises the image system and schedules a background orphan purge.
     *
     * Must be called AFTER initializeDatabaseSystem() - ImagePurgeManager
     * queries Room to cross-reference disk files against known paths.
     *
     * Singletons created here are held for the lifetime of the process and
     * exposed via getters so repositories and activities can access them
     * without constructing their own instances.
     */
    private void initializeImageSystem() {
        if (ApiConfig.DEBUG_LOGGING) Log.d(TAG, "Initialising image system…");

        // Lightweight - no I/O on construction.
        imageStorageManager = new ImageStorageManager(this);

        // Owns a dedicated OkHttpClient + single-thread executor.
        thumbnailDownloader = new ThumbnailDownloader(imageStorageManager);

        // Owns a single-thread executor. Must be created after the database.
        imagePurgeManager = new ImagePurgeManager(this, imageStorageManager);

        // Fire-and-forget: scans all image directories, deletes orphans.
        // This also handles cleanup after a destructive DB recreation - an empty
        // Room means every image file is unreferenced, so all are purged. Do NOT
        // add a "skip when Room is empty" guard; that would strand orphaned files.
        imagePurgeManager.purgeOrphansAsync();

        if (ApiConfig.DEBUG_LOGGING) Log.d(TAG, "Image system initialised - purge scheduled");
    }

    // =========================================================================
    // DATA SOURCES
    // =========================================================================

    /**
     * Boot the DataSourceManager singleton.
     *
     * This is the only thing we do here now. The manager creates each source
     * and starts async initialisation. Each source reads its own enabled flag
     * from its own SharedPreferences - we don't touch any config here.
     *
     * Sources are enabled by default on first launch (BaseDataSource returns
     * true when no SharedPreferences value has been written yet). The user can
     * then disable individual sources from the Settings screen, and that choice
     * persists across restarts without being overwritten here.
     */
    private void initializeDataSourceSystem() {
        try {
            if (ApiConfig.DEBUG_LOGGING) {
                Log.d(TAG, "Booting DataSourceManager…");
            }

            DataSourceManager manager = DataSourceManager.getInstance(this);

            if (ApiConfig.DEBUG_LOGGING) {
                // Log source state after a short delay to let async init complete
                new android.os.Handler().postDelayed(() -> {
                    List<DataSource> active = manager.getActiveSources();
                    Log.d(TAG, "Active sources after boot: " + active.size());
                    for (DataSource s : active) {
                        Log.d(TAG, "  · " + s.getSourceId()
                                + " enabled=" + s.isEnabled()
                                + " available=" + s.isAvailable());
                    }
                }, 2000);
            }
        } catch (Exception e) {
            Log.e(TAG, "DataSource system init failed", e);
        }
    }

    // =========================================================================
    // NETWORK
    // =========================================================================

    private void initializeNetworkSystem() {
        try {
            // Set default User-Agent for all HTTP connections
            System.setProperty("http.agent", OpenFoodFactsConstants.USER_AGENT);
            if (ApiConfig.DEBUG_LOGGING) {
                Log.d(TAG, "Network defaults applied");
            }
        } catch (Exception e) {
            Log.e(TAG, "Network init failed", e);
        }
    }

    // =========================================================================
    // PERFORMANCE MONITORING
    // =========================================================================

    private void initializePerformanceMonitoring() {
        if (!ApiConfig.ENABLE_PERFORMANCE_LOGGING) return;
        try {
            if (ApiConfig.DEBUG_LOGGING) {
                Log.d(TAG, "Performance monitoring enabled");
                Log.d(TAG, "  Response time logging: " + ApiConfig.LOG_API_RESPONSE_TIMES);
                Log.d(TAG, "  Cache hit rate logging: " + ApiConfig.LOG_CACHE_HIT_RATES);
            }
        } catch (Exception e) {
            Log.e(TAG, "Performance monitoring init failed", e);
        }
    }

    // =========================================================================
    // CLEANUP
    // =========================================================================

    private void cleanupResources() {
        try {
            LanguageDetector.cleanup();
            if (ApiConfig.DEBUG_LOGGING) Log.d(TAG, "Resources cleaned up");
        } catch (Exception e) {
            Log.e(TAG, "Cleanup error", e);
        }
    }

    // =========================================================================
    // UTILITY METHODS
    // =========================================================================

    /**
     * Returns the application-scoped ImageStorageManager singleton.
     * Never construct a new instance - always use this.
     */
    public ImageStorageManager getImageStorageManager() {
        return imageStorageManager;
    }

    /**
     * Returns the application-scoped ThumbnailDownloader singleton.
     * Holds a shared OkHttpClient and executor - never construct your own.
     */
    public ThumbnailDownloader getThumbnailDownloader() {
        return thumbnailDownloader;
    }

    /**
     * Returns the application-scoped ImagePurgeManager singleton.
     * Exposed primarily for the Settings screen manual purge trigger.
     */
    public ImagePurgeManager getImagePurgeManager() {
        return imagePurgeManager;
    }
}