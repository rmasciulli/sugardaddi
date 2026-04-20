package li.masciul.sugardaddi.managers;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import li.masciul.sugardaddi.core.models.Error;
import li.masciul.sugardaddi.data.network.ApiConfig;
import li.masciul.sugardaddi.data.sources.base.BaseDataSource;
import li.masciul.sugardaddi.data.sources.base.DataSource;
import li.masciul.sugardaddi.data.sources.ciqual.CiqualConfig;
import li.masciul.sugardaddi.data.sources.ciqual.CiqualDataSource;
import li.masciul.sugardaddi.data.sources.openfoodfacts.OpenFoodFactsConfig;
import li.masciul.sugardaddi.data.sources.openfoodfacts.OpenFoodFactsDataSource;
import li.masciul.sugardaddi.data.sources.usda.USDAConfig;
import li.masciul.sugardaddi.data.sources.usda.USDADataSource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * DataSourceManager — Singleton registry and initialisation orchestrator.
 *
 * ARCHITECTURE v3.0 — Settings refactor
 * ======================================
 * REMOVED: DataSourceConfig dependency.
 *   Enabled state is now persisted by each source in its own SharedPreferences
 *   (via BaseDataSource.isEnabled() / setEnabled()). This manager no longer
 *   needs a shared config file to know which sources are active.
 *
 * REMOVED: hardcoded {@code int sourceCount = 2}.
 *   The pending-initialisation counter is now incremented dynamically each time
 *   a source is registered for async init. Adding USDA (or any future source)
 *   requires no change here beyond calling registerAndInit().
 *
 * ADDED: {@link #getAllSources()} — all registered sources, sorted A→Z by name.
 *   Used by SettingsActivity to build the data-source cards in alphabetical order.
 *
 * ADDED: {@link #getActiveSources()} — enabled + initialised sources, A→Z.
 *   Used by DataSourceAggregator for parallel search. Replaces the former
 *   getEnabledDataSources() which filtered via DataSourceConfig.
 *
 * ENABLE / DISABLE
 * ================
 * The manager delegates directly to the source:
 *   {@code manager.setSourceEnabled(sourceId, context, enabled)}
 *   → calls {@code source.setEnabled(context, enabled)}
 *
 * No config file is written by this class.
 *
 * ADDING A NEW SOURCE
 * ===================
 * Inside initializeDataSources(), call registerAndInit() once per source.
 * That's the only change required. The pending counter is automatic.
 */
public class DataSourceManager {

    private static final String TAG = "DataSourceManager";

    // Singleton
    private static volatile DataSourceManager instance;

    // Registry: sourceId → DataSource
    private final Map<String, DataSource> dataSources = new ConcurrentHashMap<>();

    private final Context context;

    // Initialisation tracking
    private final Set<String>         initializedSources  = Collections.synchronizedSet(new HashSet<>());
    private final Set<String>         failedSources       = Collections.synchronizedSet(new HashSet<>());
    private final Map<String, Error>  initializationErrors = new ConcurrentHashMap<>();

    // Dynamic counter — incremented per source registered for async init
    private final AtomicInteger pendingInitializations = new AtomicInteger(0);

    // Optional listener for init events
    @Nullable
    private InitializationListener initializationListener;

    // =========================================================================
    // INITIALISATION CALLBACK
    // =========================================================================

    public interface InitializationListener {
        /** Called when a source finishes initialising successfully. */
        void onSourceReady(String sourceId);
        /** Called when a source fails to initialise. */
        void onSourceFailed(String sourceId, Error error);
        /** Called once all pending sources have finished (success or failure). */
        void onAllSourcesReady(int successCount, int failedCount);
    }

    // =========================================================================
    // SINGLETON
    // =========================================================================

    private DataSourceManager(Context context) {
        this.context = context.getApplicationContext();
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

    // =========================================================================
    // SOURCE REGISTRATION
    // =========================================================================

    /**
     * Register a source and kick off its async initialisation.
     *
     * Increments the pending counter BEFORE submitting init so the counter
     * is always ≥ the number of in-flight inits. This is the only place the
     * counter is incremented — adding a new source here is all that's needed.
     *
     * @param source The data source to register and initialise.
     */
    private void registerAndInit(@NonNull DataSource source) {
        String sourceId = source.getSourceId();
        dataSources.put(sourceId, source);

        if (source instanceof BaseDataSource) {
            // Increment BEFORE starting so the latch is correct
            pendingInitializations.incrementAndGet();

            ((BaseDataSource) source).initialize(context, new BaseDataSource.InitializationCallback() {
                @Override
                public void onInitialized() {
                    handleSourceInitialized(sourceId);
                }

                @Override
                public void onInitializationFailed(Error error) {
                    handleSourceFailed(sourceId, error);
                }
            });
        } else {
            // Synchronous fallback for sources that don't extend BaseDataSource
            source.initialize(context);
            initializedSources.add(sourceId);
            Log.i(TAG, "✓ " + sourceId + " initialized (sync)");
        }

        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, "Registered source: " + sourceId);
        }
    }

    /**
     * Register a data source without initialising it.
     * Use this for sources that self-initialise or are already ready.
     */
    public void registerDataSource(@NonNull DataSource source) {
        dataSources.put(source.getSourceId(), source);
        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, "Registered (no init): " + source.getSourceId());
        }
    }

    // =========================================================================
    // INITIALISE ALL SOURCES
    // =========================================================================

    /**
     * Create and initialise all data sources in parallel.
     *
     * TO ADD A NEW SOURCE: add one registerAndInit() call here.
     * No other change is required — the pending counter is dynamic.
     */
    private void initializeDataSources() {
        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, "Starting parallel data source initialisation…");
        }

        // ── OpenFoodFacts ──────────────────────────────────────────────────
        registerAndInit(new OpenFoodFactsDataSource(context, new OpenFoodFactsConfig()));

        // ── Ciqual ────────────────────────────────────────────────────────
        registerAndInit(new CiqualDataSource(context, new CiqualConfig()));

        // ── Future: USDA ──────────────────────────────────────────────────
        registerAndInit(new USDADataSource(context, new USDAConfig()));

        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, "Triggered async init for " + pendingInitializations.get() + " sources");
        }
    }

    // =========================================================================
    // INIT EVENT HANDLERS
    // =========================================================================

    private void handleSourceInitialized(String sourceId) {
        initializedSources.add(sourceId);
        int remaining = pendingInitializations.decrementAndGet();

        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, "✓ " + sourceId + " ready (pending=" + remaining + ")");
        }

        if (initializationListener != null) {
            initializationListener.onSourceReady(sourceId);
        }

        checkAllInitialized();
    }

    private void handleSourceFailed(String sourceId, Error error) {
        failedSources.add(sourceId);
        initializationErrors.put(sourceId, error);
        int remaining = pendingInitializations.decrementAndGet();

        Log.e(TAG, "✗ " + sourceId + " failed: " + error.getMessage()
                + " (pending=" + remaining + ")");

        if (initializationListener != null) {
            initializationListener.onSourceFailed(sourceId, error);
        }

        checkAllInitialized();
    }

    private void checkAllInitialized() {
        if (pendingInitializations.get() == 0) {
            if (ApiConfig.DEBUG_LOGGING) {
                Log.d(TAG, "All sources initialised — success=" + initializedSources.size()
                        + " failed=" + failedSources.size());
            }
            if (initializationListener != null) {
                initializationListener.onAllSourcesReady(
                        initializedSources.size(), failedSources.size());
            }
        }
    }

    // =========================================================================
    // LISTENER
    // =========================================================================

    public void setInitializationListener(@Nullable InitializationListener listener) {
        this.initializationListener = listener;

        // Replay already-completed events immediately
        synchronized (initializedSources) {
            for (String sourceId : initializedSources) {
                if (listener != null) listener.onSourceReady(sourceId);
            }
        }

        if (pendingInitializations.get() == 0 && !dataSources.isEmpty() && listener != null) {
            listener.onAllSourcesReady(initializedSources.size(), failedSources.size());
        }
    }

    // =========================================================================
    // SOURCE QUERIES
    // =========================================================================

    /**
     * All registered sources, sorted A→Z by display name.
     * Used by SettingsActivity to render cards in alphabetical order.
     * Includes sources that are disabled or still initialising.
     */
    @NonNull
    public List<DataSource> getAllSources() {
        List<DataSource> all = new ArrayList<>(dataSources.values());
        all.sort(Comparator.comparing(DataSource::getSourceName,
                String.CASE_INSENSITIVE_ORDER));
        return all;
    }

    /**
     * Sources that are enabled AND fully initialised, sorted A→Z.
     * Used by DataSourceAggregator to build the parallel search set.
     *
     * Replaces the former getEnabledDataSources() which filtered via
     * DataSourceConfig. Now each source owns its enabled flag.
     */
    @NonNull
    public List<DataSource> getActiveSources() {
        List<DataSource> active = new ArrayList<>();
        for (DataSource source : dataSources.values()) {
            if (source.isEnabled() && source.isAvailable()) {
                active.add(source);
            }
        }
        active.sort(Comparator.comparing(DataSource::getSourceName,
                String.CASE_INSENSITIVE_ORDER));
        return active;
    }

    /**
     * All registered sources as a raw collection (no sorting).
     * Kept for DataSourceAggregator.cancelSearches() which iterates all sources.
     */
    @NonNull
    public Collection<DataSource> getAllDataSources() {
        return new ArrayList<>(dataSources.values());
    }

    /**
     * Look up a specific source by ID.
     *
     * @param sourceId e.g. "CIQUAL", "OPENFOODFACTS"
     * @return The source, or null if not registered
     */
    @Nullable
    public DataSource getDataSource(String sourceId) {
        return dataSources.get(sourceId);
    }

    // =========================================================================
    // ENABLE / DISABLE  (delegates to source, no config file)
    // =========================================================================

    /**
     * Check whether a source is enabled.
     * Reads directly from the source's own SharedPreferences.
     */
    public boolean isSourceEnabled(String sourceId) {
        DataSource source = dataSources.get(sourceId);
        return source != null && source.isEnabled();
    }

    /**
     * Enable or disable a source.
     * Persists the choice via the source's own SharedPreferences.
     * The aggregator picks up the change on the next search call.
     *
     * @param sourceId e.g. "CIQUAL"
     * @param context  Application context (needed for SharedPreferences write)
     * @param enabled  true = include in searches, false = exclude
     */
    public void setSourceEnabled(String sourceId,
                                 @NonNull Context context,
                                 boolean enabled) {
        DataSource source = dataSources.get(sourceId);
        if (source != null) {
            source.setEnabled(context, enabled);
            if (ApiConfig.DEBUG_LOGGING) {
                Log.d(TAG, sourceId + (enabled ? " enabled" : " disabled"));
            }
        } else {
            Log.w(TAG, "setSourceEnabled: unknown source '" + sourceId + "'");
        }
    }

    // =========================================================================
    // INIT STATE QUERIES
    // =========================================================================

    public boolean isSourceReady(String sourceId) {
        return initializedSources.contains(sourceId);
    }

    public boolean isInitializationComplete() {
        return pendingInitializations.get() == 0;
    }

    public int getReadySourceCount()  { return initializedSources.size(); }
    public int getFailedSourceCount() { return failedSources.size(); }

    @Nullable
    public Error getInitializationError(String sourceId) {
        return initializationErrors.get(sourceId);
    }

    // =========================================================================
    // CLEANUP
    // =========================================================================

    public void cleanup() {
        for (DataSource source : dataSources.values()) {
            try { source.cleanup(); }
            catch (Exception e) {
                Log.e(TAG, "Error cleaning up " + source.getSourceId(), e);
            }
        }
        dataSources.clear();
        initializedSources.clear();
        failedSources.clear();
        initializationErrors.clear();
    }

    // =========================================================================
    // DIAGNOSTICS
    // =========================================================================

    /**
     * Human-readable status dump for debug logging.
     */
    @NonNull
    public String getStatus() {
        StringBuilder sb = new StringBuilder("DataSourceManager status:\n");
        sb.append("  Registered: ").append(dataSources.size()).append("\n");
        sb.append("  Ready:      ").append(initializedSources.size()).append("\n");
        sb.append("  Failed:     ").append(failedSources.size()).append("\n");
        sb.append("  Pending:    ").append(pendingInitializations.get()).append("\n\n");

        for (DataSource source : getAllSources()) {
            String id = source.getSourceId();
            sb.append("  ").append(id).append(": ");

            if (initializedSources.contains(id))  sb.append("✓ READY");
            else if (failedSources.contains(id)) {
                sb.append("✗ FAILED");
                Error err = initializationErrors.get(id);
                if (err != null) sb.append(" (").append(err.getMessage()).append(")");
            } else {
                sb.append("⟳ INITIALIZING");
            }

            sb.append(", enabled=").append(source.isEnabled()).append("\n");
        }
        return sb.toString();
    }
}