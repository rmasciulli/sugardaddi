package li.masciul.sugardaddi.managers;

import android.content.Context;
import android.util.Log;

import li.masciul.sugardaddi.core.models.Error;
import li.masciul.sugardaddi.data.network.ApiConfig;
import li.masciul.sugardaddi.data.sources.base.BaseDataSource;
import li.masciul.sugardaddi.data.sources.base.DataSource;
import li.masciul.sugardaddi.data.sources.ciqual.CiqualConfig;
import li.masciul.sugardaddi.data.sources.ciqual.CiqualDataSource;
import li.masciul.sugardaddi.data.sources.openfoodfacts.OpenFoodFactsConfig;
import li.masciul.sugardaddi.data.sources.openfoodfacts.OpenFoodFactsDataSource;
import li.masciul.sugardaddi.data.sources.config.DataSourceConfig;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * DataSourceManager - UPDATED: Async parallel initialization
 *
 * v2.1: NON-BLOCKING INITIALIZATION
 * - Sources initialize in parallel (not sequentially)
 * - Callback when each source is ready
 * - Callback when all sources are ready
 * - No more blocking UI thread
 * - Can start using sources as soon as they're available
 *
 * BEFORE (Blocking):
 * ```
 * DataSourceManager manager = DataSourceManager.getInstance(context);
 * // Had to wait 5+ seconds before using any source
 * ```
 *
 * AFTER (Non-blocking):
 * ```
 * DataSourceManager manager = DataSourceManager.getInstance(context);
 * manager.setInitializationListener(new InitializationListener() {
 *     @Override
 *     public void onSourceReady(String sourceId) {
 *         // This source is ready - can use it now!
 *     }
 *
 *     @Override
 *     public void onAllSourcesReady(int successCount, int failedCount) {
 *         // All sources finished initializing
 *     }
 * });
 * // Can immediately continue - sources will signal when ready
 * ```
 */
public class DataSourceManager {

    private static final String TAG = "DataSourceManager";

    // Singleton instance
    private static volatile DataSourceManager instance;

    // Data sources registry
    private final Map<String, DataSource> dataSources = new ConcurrentHashMap<>();
    private final DataSourceConfig config;
    private final Context context;

    // Initialization tracking
    private final Set<String> initializedSources = Collections.synchronizedSet(new HashSet<>());
    private final Set<String> failedSources = Collections.synchronizedSet(new HashSet<>());
    private final Map<String, Error> initializationErrors = new ConcurrentHashMap<>();
    private final AtomicInteger pendingInitializations = new AtomicInteger(0);

    // Callback for initialization events
    private InitializationListener initializationListener;

    /**
     * Callback interface for initialization events
     */
    public interface InitializationListener {
        /**
         * Called when a specific source finishes initializing successfully
         * @param sourceId The ID of the source that's ready
         */
        void onSourceReady(String sourceId);

        /**
         * Called when a source fails to initialize
         * @param sourceId The ID of the source that failed
         * @param error The error that occurred
         */
        void onSourceFailed(String sourceId, Error error);

        /**
         * Called when all sources have completed initialization (success or failure)
         * @param successCount Number of sources successfully initialized
         * @param failedCount Number of sources that failed to initialize
         */
        void onAllSourcesReady(int successCount, int failedCount);
    }

    private DataSourceManager(Context context) {
        this.context = context.getApplicationContext();
        this.config = new DataSourceConfig(context);
        initializeDataSources();
    }

    public static DataSourceManager getInstance(Context context) {
        if (instance == null) {
            synchronized (DataSourceManager.class) {
                if (instance == null) {
                    instance = new DataSourceManager(context);
                }
            }
        }
        return instance;
    }

    /**
     * Set listener for initialization events
     */
    public void setInitializationListener(InitializationListener listener) {
        this.initializationListener = listener;

        // If some sources are already initialized, notify immediately
        synchronized (initializedSources) {
            for (String sourceId : initializedSources) {
                if (listener != null) {
                    listener.onSourceReady(sourceId);
                }
            }
        }

        // If all sources are done, notify completion
        if (pendingInitializations.get() == 0 && !dataSources.isEmpty()) {
            if (listener != null) {
                listener.onAllSourcesReady(initializedSources.size(), failedSources.size());
            }
        }
    }

    /**
     * Initialize all data sources IN PARALLEL (non-blocking)
     */
    private void initializeDataSources() {
        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, "Starting parallel initialization of data sources...");
        }

        // Count how many sources we're initializing
        int sourceCount = 2; // OFF + Ciqual
        pendingInitializations.set(sourceCount);

        // Initialize OpenFoodFacts (async)
        OpenFoodFactsConfig offConfig = new OpenFoodFactsConfig();
        OpenFoodFactsDataSource offSource = new OpenFoodFactsDataSource(context, offConfig);
        registerDataSource(offSource);

        if (offSource instanceof BaseDataSource) {
            ((BaseDataSource) offSource).initialize(context, new BaseDataSource.InitializationCallback() {
                @Override
                public void onInitialized() {
                    handleSourceInitialized(offSource.getSourceId());
                }

                @Override
                public void onInitializationFailed(Error error) {
                    handleSourceFailed(offSource.getSourceId(), error);
                }
            });
        } else {
            // Fallback to sync init
            offSource.initialize(context);
            handleSourceInitialized(offSource.getSourceId());
        }

        // Initialize Ciqual (async, in parallel with OFF)
        CiqualConfig ciqualConfig = new CiqualConfig();
        CiqualDataSource ciqualSource = new CiqualDataSource(context, ciqualConfig);
        registerDataSource(ciqualSource);

        if (ciqualSource instanceof BaseDataSource) {
            ((BaseDataSource) ciqualSource).initialize(context, new BaseDataSource.InitializationCallback() {
                @Override
                public void onInitialized() {
                    handleSourceInitialized(ciqualSource.getSourceId());
                }

                @Override
                public void onInitializationFailed(Error error) {
                    handleSourceFailed(ciqualSource.getSourceId(), error);
                }
            });
        } else {
            // Fallback to sync init
            ciqualSource.initialize(context);
            handleSourceInitialized(ciqualSource.getSourceId());
        }

        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, "Triggered initialization of " + sourceCount + " data sources (async)");
        }
    }

    /**
     * Handle successful initialization of a source
     */
    private void handleSourceInitialized(String sourceId) {
        initializedSources.add(sourceId);
        int remaining = pendingInitializations.decrementAndGet();

        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, "✓ Source initialized: " + sourceId +
                    " (remaining: " + remaining + ")");
        }

        // Notify listener
        if (initializationListener != null) {
            initializationListener.onSourceReady(sourceId);
        }

        // Check if all done
        checkAllInitialized();
    }

    /**
     * Handle failed initialization of a source
     */
    private void handleSourceFailed(String sourceId, Error error) {
        failedSources.add(sourceId);
        initializationErrors.put(sourceId, error);
        int remaining = pendingInitializations.decrementAndGet();

        Log.e(TAG, "✗ Source initialization failed: " + sourceId +
                " - " + error.getMessage() + " (remaining: " + remaining + ")");

        // Notify listener
        if (initializationListener != null) {
            initializationListener.onSourceFailed(sourceId, error);
        }

        // Check if all done
        checkAllInitialized();
    }

    /**
     * Check if all sources have finished initializing
     */
    private void checkAllInitialized() {
        if (pendingInitializations.get() == 0) {
            int successCount = initializedSources.size();
            int failedCount = failedSources.size();

            if (ApiConfig.DEBUG_LOGGING) {
                Log.d(TAG, "All data sources initialized: " + successCount +
                        " successful, " + failedCount + " failed");
            }

            // Notify listener
            if (initializationListener != null) {
                initializationListener.onAllSourcesReady(successCount, failedCount);
            }
        }
    }

    /**
     * Check if a specific source is initialized and ready
     */
    public boolean isSourceReady(String sourceId) {
        return initializedSources.contains(sourceId);
    }

    /**
     * Get number of sources that are ready
     */
    public int getReadySourceCount() {
        return initializedSources.size();
    }

    /**
     * Get number of sources that failed
     */
    public int getFailedSourceCount() {
        return failedSources.size();
    }

    /**
     * Check if all sources have finished initializing (success or failure)
     */
    public boolean isInitializationComplete() {
        return pendingInitializations.get() == 0;
    }

    /**
     * Get initialization error for a specific source (if any)
     */
    public Error getInitializationError(String sourceId) {
        return initializationErrors.get(sourceId);
    }

    /**
     * Register a data source
     */
    public void registerDataSource(DataSource dataSource) {
        String sourceId = dataSource.getSourceId();
        dataSources.put(sourceId, dataSource);

        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, "Registered data source: " + sourceId);
        }
    }

    /**
     * Get a specific data source
     */
    public DataSource getDataSource(String sourceId) {
        return dataSources.get(sourceId);
    }

    /**
     * Get all enabled AND AVAILABLE (initialized) data sources
     */
    public List<DataSource> getEnabledDataSources() {
        Set<String> enabledIds = config.getEnabledSources();
        List<DataSource> enabledSources = new ArrayList<>();

        for (String sourceId : enabledIds) {
            DataSource source = dataSources.get(sourceId);
            if (source != null && source.isAvailable()) {
                enabledSources.add(source);
            }
        }

        // Sort by priority
        enabledSources.sort((a, b) ->
                Integer.compare(
                        config.getSourcePriority(b.getSourceId()),
                        config.getSourcePriority(a.getSourceId())
                )
        );

        return enabledSources;
    }

    /**
     * Get primary data source (highest priority enabled source)
     */
    public DataSource getPrimaryDataSource() {
        List<DataSource> enabled = getEnabledDataSources();
        return enabled.isEmpty() ? null : enabled.get(0);
    }

    public boolean isSourceEnabled(String sourceId) {
        return config.getEnabledSources().contains(sourceId);
    }

    public void setSourceEnabled(String sourceId, boolean enabled) {
        config.setSourceEnabled(sourceId, enabled);

        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, "Source " + sourceId + " " + (enabled ? "enabled" : "disabled"));
        }
    }

    public Collection<DataSource> getAllDataSources() {
        return new ArrayList<>(dataSources.values());
    }

    /**
     * Clean up all data sources
     */
    public void cleanup() {
        for (DataSource source : dataSources.values()) {
            try {
                source.cleanup();
            } catch (Exception e) {
                Log.e(TAG, "Error cleaning up source: " + source.getSourceId(), e);
            }
        }
        dataSources.clear();
        initializedSources.clear();
        failedSources.clear();
        initializationErrors.clear();
    }

    /**
     * Get detailed status information
     */
    public String getStatus() {
        StringBuilder status = new StringBuilder();
        status.append("DataSource Status:\n");
        status.append("  Total sources: ").append(dataSources.size()).append("\n");
        status.append("  Initialized: ").append(initializedSources.size()).append("\n");
        status.append("  Failed: ").append(failedSources.size()).append("\n");
        status.append("  Pending: ").append(pendingInitializations.get()).append("\n\n");

        for (DataSource source : dataSources.values()) {
            String sourceId = source.getSourceId();
            status.append("  ").append(sourceId).append(": ");

            if (initializedSources.contains(sourceId)) {
                status.append("✓ READY");
            } else if (failedSources.contains(sourceId)) {
                status.append("✗ FAILED");
                Error error = initializationErrors.get(sourceId);
                if (error != null) {
                    status.append(" (").append(error.getMessage()).append(")");
                }
            } else {
                status.append("⟳ INITIALIZING");
            }

            status.append(", Enabled: ").append(isSourceEnabled(sourceId));
            status.append(", Priority: ").append(config.getSourcePriority(sourceId));
            status.append("\n");
        }

        return status.toString();
    }
}