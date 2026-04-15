package li.masciul.sugardaddi;

import android.app.Application;
import android.content.Context;
import android.util.Log;
import li.masciul.sugardaddi.data.network.ApiConfig;
import li.masciul.sugardaddi.data.sources.openfoodfacts.OpenFoodFactsConstants;
import li.masciul.sugardaddi.managers.LanguageManager;
import li.masciul.sugardaddi.managers.ThemeManager;
import li.masciul.sugardaddi.managers.DataSourceManager;
import li.masciul.sugardaddi.data.database.AppDatabase;
import li.masciul.sugardaddi.data.sources.config.DataSourceConfig;
import li.masciul.sugardaddi.data.sources.base.DataSource;
import li.masciul.sugardaddi.utils.language.LanguageDetector;

import java.util.List;
import java.util.Set;

/**
 * SugarDaddiApplication - Application class for global initialization
 *
 * LIFECYCLE MANAGEMENT:
 * - Initializes core systems in proper order
 * - Sets up global error handling
 * - Prepares database and network layers
 * - Configures data sources explicitly
 * - Configures logging and debugging
 * - Handles language/theme context at application level
 *
 * @version 3.0 - Added explicit data source initialization
 */
public class SugarDaddiApplication extends Application {

    private static final String TAG = ApiConfig.UI_LOG_TAG;

    @Override
    protected void attachBaseContext(Context base) {
        // Apply language before creating application context
        LanguageManager.SupportedLanguage currentLanguage = LanguageManager.getCurrentLanguage(base);
        Context context = LanguageManager.applyLanguageToContext(base, currentLanguage);
        super.attachBaseContext(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, "SugarDaddi Application starting...");
        }

        // Initialize core systems in order
        initializeLanguageSystem();
        initializeThemeSystem();
        initializeDatabaseSystem();
        initializeDataSourceSystem();  // NEW - Explicit data source configuration
        initializeNetworkSystem();
        initializePerformanceMonitoring();

        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, "SugarDaddi Application initialization complete");
        }
    }

    /**
     * Initialize language system with enhanced context management
     */
    private void initializeLanguageSystem() {
        try {
            LanguageManager.initializeLanguage(this);

            if (ApiConfig.DEBUG_LOGGING) {
                LanguageManager.SupportedLanguage currentLang = LanguageManager.getCurrentLanguage(this);
                Log.d(TAG, "Language system initialized: " + currentLang.getDisplayName());
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize language system", e);
        }
    }

    /**
     * Initialize theme system for consistent styling
     */
    private void initializeThemeSystem() {
        try {
            ThemeManager.initializeTheme(this);

            if (ApiConfig.DEBUG_LOGGING) {
                ThemeManager.Theme currentTheme = ThemeManager.getCurrentTheme(this);
                Log.d(TAG, "Theme system initialized: " + currentTheme.getValue());
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize theme system", e);
        }
    }

    /**
     * Initialize database system for proper lifecycle
     */
    private void initializeDatabaseSystem() {
        try {
            // Pre-initialize database to ensure Room is ready
            AppDatabase database = AppDatabase.getInstance(this);

            if (ApiConfig.DEBUG_LOGGING) {
                Log.d(TAG, "Database system initialized successfully");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize database system", e);
        }
    }

    /**
     * Initialize data source system with explicit configuration
     *
     * NEW in v3.0 - Explicit data source configuration
     *
     * PHILOSOPHY:
     * - Explicit is better than implicit
     * - Clear state at startup (no hidden SharedPreferences surprises)
     * - Developer controls what's enabled in one place
     * - Easy to see and modify configuration
     *
     * WHAT THIS DOES:
     * - Sets which data sources are enabled (OpenFoodFacts, Ciqual, etc.)
     * - Sets priority for each source (higher = searched first)
     * - Initializes DataSourceManager (which creates the actual data sources)
     * - Logs configuration for debugging
     *
     * TO ENABLE/DISABLE SOURCES:
     * Just change the setSourceEnabled() calls below
     */
    private void initializeDataSourceSystem() {
        try {
            if (ApiConfig.DEBUG_LOGGING) {
                Log.d(TAG, "=================================");
                Log.d(TAG, "Initializing data source system...");
                Log.d(TAG, "=================================");
            }

            // Get the configuration manager
            DataSourceConfig config = new DataSourceConfig(this);

            // ========== EXPLICIT CONFIGURATION ==========
            // This runs on EVERY app start, ensuring consistent state
            // No surprises from old SharedPreferences values!

            config.setSourceEnabled("OPENFOODFACTS", true);  // Always enabled
            config.setSourceEnabled("CIQUAL", true);         // Always enabled

            // Set priorities (higher number = searched first, results ranked higher)
            config.setSourcePriority("OPENFOODFACTS", 100);  // Primary source (brands, products)
            config.setSourcePriority("CIQUAL", 90);          // Secondary (scientific data)

            if (ApiConfig.DEBUG_LOGGING) {
                Log.d(TAG, "Data source configuration set:");
                Log.d(TAG, "  - OPENFOODFACTS: enabled=true, priority=100");
                Log.d(TAG, "  - CIQUAL: enabled=true, priority=90");
            }

            // Initialize DataSourceManager
            // This creates the actual data source instances
            DataSourceManager dsManager = DataSourceManager.getInstance(this);

            if (ApiConfig.DEBUG_LOGGING) {
                // Log current configuration
                Set<String> enabled = config.getEnabledSources();
                Log.d(TAG, "Enabled sources from config: " + enabled);

                // Wait a moment for async initialization, then check availability
                new android.os.Handler().postDelayed(() -> {
                    List<DataSource> availableSources = dsManager.getEnabledDataSources();
                    Log.d(TAG, "=================================");
                    Log.d(TAG, "Data source initialization complete");
                    Log.d(TAG, "Available sources: " + availableSources.size());
                    for (DataSource source : availableSources) {
                        Log.d(TAG, String.format("  - %s (enabled: %s, available: %s)",
                                source.getSourceId(),
                                source.isEnabled(),
                                source.isAvailable()));
                    }
                    Log.d(TAG, "=================================");
                }, 1500);  // Wait 1.5 seconds for async initialization
            }

        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize data source system", e);
        }
    }

    /**
     * Initialize network system preparation
     */
    private void initializeNetworkSystem() {
        try {
            // NetworkManager will be initialized on-demand, but we can set up global config
            System.setProperty("http.agent", OpenFoodFactsConstants.USER_AGENT);

            if (ApiConfig.DEBUG_LOGGING) {
                Log.d(TAG, "Network system configuration applied");
                Log.d(TAG, "API Base URL: " + OpenFoodFactsConstants.getVersionedBaseUrl());
                Log.d(TAG, "User Agent: " + OpenFoodFactsConstants.USER_AGENT);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize network system", e);
        }
    }

    /**
     * Initialize performance monitoring if enabled
     */
    private void initializePerformanceMonitoring() {
        if (!ApiConfig.ENABLE_PERFORMANCE_LOGGING) {
            return;
        }

        try {
            // Set up global performance monitoring
            if (ApiConfig.DEBUG_LOGGING) {
                Log.d(TAG, "Performance monitoring enabled");
                Log.d(TAG, "Debug logging: " + ApiConfig.DEBUG_LOGGING);
                Log.d(TAG, "API response time logging: " + ApiConfig.LOG_API_RESPONSE_TIMES);
                Log.d(TAG, "Cache hit rate logging: " + ApiConfig.LOG_CACHE_HIT_RATES);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize performance monitoring", e);
        }
    }

    @Override
    public void onTerminate() {
        super.onTerminate();

        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, "SugarDaddi Application terminating...");
        }

        // Clean up resources
        cleanupResources();
    }

    /**
     * Clean up application resources
     */
    private void cleanupResources() {
        try {
            // Language detector cleanup
            LanguageDetector.cleanup();

            if (ApiConfig.DEBUG_LOGGING) {
                Log.d(TAG, "Application resources cleaned up");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error during resource cleanup", e);
        }
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();

        if (ApiConfig.DEBUG_LOGGING) {
            Log.w(TAG, "Low memory warning received");
        }

        // Perform memory cleanup if needed
        performMemoryCleanup();
    }

    /**
     * Perform memory cleanup during low memory conditions
     */
    private void performMemoryCleanup() {
        try {
            // Clear non-essential caches
            AppDatabase database = AppDatabase.getInstance(this);

            // Run cleanup in background thread
            new Thread(() -> {
                try {
                    // Clear old cache entries to free memory
                    long oneDayAgo = System.currentTimeMillis() - (24 * 60 * 60 * 1000);
                    int clearedItems = database.foodProductDao().deleteOldProducts(oneDayAgo);

                    if (ApiConfig.DEBUG_LOGGING) {
                        Log.d(TAG, "Memory cleanup: cleared " + clearedItems + " old cache entries");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error during memory cleanup", e);
                }
            }).start();

        } catch (Exception e) {
            Log.e(TAG, "Failed to perform memory cleanup", e);
        }
    }

    /**
     * Get application version for debugging
     */
    public String getAppVersion() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "Unknown";
        }
    }
}