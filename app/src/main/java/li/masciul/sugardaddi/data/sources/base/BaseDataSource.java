package li.masciul.sugardaddi.data.sources.base;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import li.masciul.sugardaddi.core.logging.ErrorLogger;
import li.masciul.sugardaddi.core.models.Error;
import li.masciul.sugardaddi.data.network.NetworkConfig;
import li.masciul.sugardaddi.data.sources.base.settings.SettingsProvider;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * BaseDataSource — Abstract base implementation for all data sources.
 *
 * ARCHITECTURE v3.0 — Settings refactor
 * ======================================
 * Key changes vs v2.x:
 *
 *   REMOVED: in-memory {@code boolean enabled} field.
 *   The old field was never persisted — DataSourceConfig held the real state,
 *   which caused the two to drift.  Now each source persists its own enabled
 *   flag in a dedicated SharedPreferences file named after its source ID
 *   ("source_prefs_CIQUAL", "source_prefs_OPENFOODFACTS", …).
 *   isEnabled() reads from SharedPreferences; setEnabled() writes to it.
 *
 *   REMOVED: DataSourceConfig dependency.
 *   BaseDataSource no longer imports or references DataSourceConfig.
 *
 *   ADDED: default getSettingsProvider() returning null.
 *   Subclasses that have user-configurable state override this.
 *
 * INITIALIZATION LIFECYCLE
 * ========================
 *   CREATED      → instantiated, not yet initialised
 *   INITIALIZING → async init in progress
 *   READY        → initialised, available for searches
 *   ERROR        → init failed, will not retry automatically
 *
 * ENABLE/DISABLE PERSISTENCE
 * ==========================
 * SharedPreferences file: "source_prefs_{SOURCE_ID}"
 * Key: "enabled"
 * Default: true (enabled on first launch)
 *
 * This means each source independently persists its toggle state.
 * DataSourceManager calls source.isEnabled() directly — no shared config file.
 */
public abstract class BaseDataSource implements DataSource {

    // =========================================================================
    // SHARED PREFS — enable/disable persistence
    // =========================================================================

    /** Prefix for each source's private SharedPreferences file. */
    private static final String PREFS_PREFIX  = "source_prefs_";

    /** Key within that file for the enabled flag. */
    private static final String KEY_ENABLED   = "enabled";

    // =========================================================================
    // INITIALISATION STATE
    // =========================================================================

    protected Context context;
    protected boolean initialized = false;

    private final AtomicBoolean isInitializing       = new AtomicBoolean(false);
    private final AtomicBoolean initializationFailed = new AtomicBoolean(false);
    private Error initializationError = null;

    // =========================================================================
    // STATISTICS  (runtime, not persisted)
    // =========================================================================

    protected int  totalRequests = 0;
    protected int  successCount  = 0;
    protected int  errorCount    = 0;
    protected long lastUsed      = 0;

    // =========================================================================
    // THREADING
    // =========================================================================

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final ExecutorService initExecutor = Executors.newCachedThreadPool();

    // =========================================================================
    // CONSTRUCTOR
    // =========================================================================

    protected BaseDataSource() {
        // Lightweight — heavy work goes in onInitialize()
    }

    // =========================================================================
    // ABSTRACT CONTRACT
    // =========================================================================

    /**
     * HTTP network configuration, or null for local-only sources.
     * Must be non-null for sources that use Retrofit/OkHttp.
     */
    @Nullable
    public abstract NetworkConfig getNetworkConfig();

    /**
     * Source-specific initialisation — called on a background thread by
     * {@link #initialize(Context, InitializationCallback)}.
     * Override to create HTTP clients, open files, warm caches, etc.
     * Default does nothing (suitable for trivial/local sources).
     */
    protected void onInitialize(@NonNull Context context) throws Exception {
        // Default: no-op
    }

    // =========================================================================
    // ENABLE / DISABLE  — persisted in own SharedPreferences
    // =========================================================================

    /**
     * Returns the SharedPreferences file private to this source.
     * File name: "source_prefs_{SOURCE_ID}" (e.g. "source_prefs_CIQUAL").
     * Context must be available (i.e. after initialize() has been called).
     */
    private SharedPreferences getSourcePrefs(@NonNull Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS_PREFIX + getSourceId(), Context.MODE_PRIVATE);
    }

    /**
     * {@inheritDoc}
     *
     * Reads from this source's own SharedPreferences.
     * Falls back to true (enabled) if no value has been written yet,
     * so every source is enabled on first launch without explicit setup.
     *
     * If context is not yet available (called before initialize), returns true
     * as a safe default — the aggregator will call isAvailable() anyway which
     * also checks initialized state.
     */
    @Override
    public boolean isEnabled() {
        if (context == null) return true; // Safe default before init
        return getSourcePrefs(context).getBoolean(KEY_ENABLED, true);
    }

    /**
     * {@inheritDoc}
     *
     * Persists the new enabled flag immediately.
     * Also propagates to the NetworkConfig if one exists, so OkHttp
     * interceptors that check config.isEnabled() stay in sync.
     */
    @Override
    public void setEnabled(@NonNull Context context, boolean enabled) {
        getSourcePrefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply();

        // Keep NetworkConfig in sync so any HTTP interceptors see the same state
        NetworkConfig config = getNetworkConfig();
        if (config != null) {
            if (enabled) config.enable();
            else         config.disable();
        }

        logInfo("Source " + getSourceId() + (enabled ? " enabled" : " disabled"));
    }

    // =========================================================================
    // AVAILABILITY & STATUS
    // =========================================================================

    @Override
    public boolean isAvailable() {
        if (!isEnabled()) return false;

        if (!initialized || isInitializing.get()) return false;

        if (initializationFailed.get()) return false;

        NetworkConfig config = getNetworkConfig();
        if (config != null && !config.isEnabled()) return false;

        return true;
    }

    @NonNull
    @Override
    public DataSourceStatus getStatus() {
        if (!isEnabled()) return DataSourceStatus.DISABLED;

        if (initializationFailed.get()) return DataSourceStatus.ERROR;

        if (!initialized || isInitializing.get()) return DataSourceStatus.INITIALIZING;

        if (requiresNetwork() && !isNetworkAvailable()) return DataSourceStatus.NO_NETWORK;

        // Degrade to ERROR if more than half of recent requests failed
        if (totalRequests > 10) {
            double errorRate = (double) errorCount / totalRequests;
            if (errorRate > 0.5) return DataSourceStatus.ERROR;
        }

        return DataSourceStatus.READY;
    }

    // =========================================================================
    // SETTINGS PROVIDER — default: none
    // =========================================================================

    /**
     * Returns null by default — sources without user-configurable settings
     * do not need to override this.  The Settings card will show only the
     * name, status dot, and enable/disable toggle.
     */
    @Override
    @Nullable
    public SettingsProvider getSettingsProvider() {
        return null;
    }

    // =========================================================================
    // LIFECYCLE — async initialisation with callback
    // =========================================================================

    /**
     * Callback interface for async initialisation results.
     */
    public interface InitializationCallback {
        void onInitialized();
        void onInitializationFailed(@NonNull Error error);
    }

    /**
     * Asynchronous initialisation.  Runs {@link #onInitialize(Context)} on a
     * background thread and delivers the result via callback on the main thread.
     *
     * Safe to call multiple times — subsequent calls are no-ops if the source
     * is already initialised or initialisation is already in progress.
     */
    public void initialize(@NonNull Context context,
                           @NonNull InitializationCallback callback) {
        this.context = context.getApplicationContext();

        if (initialized) {
            executeOnMainThread(callback::onInitialized);
            return;
        }

        if (isInitializing.getAndSet(true)) {
            logWarn("Initialization already in progress for " + getSourceId());
            return;
        }

        if (initializationFailed.get()) {
            Error error = initializationError != null
                    ? initializationError
                    : Error.unknown("Previous initialization failed", null);
            executeOnMainThread(() -> callback.onInitializationFailed(error));
            return;
        }

        initExecutor.execute(() -> {
            try {
                logInfo("Initializing " + getSourceId() + "…");

                NetworkConfig config = getNetworkConfig();
                if (config != null) config.validate();

                onInitialize(this.context);

                initialized = true;
                isInitializing.set(false);

                logInfo(getSourceId() + " initialized successfully");
                executeOnMainThread(callback::onInitialized);

            } catch (Exception e) {
                logError("Initialization failed for " + getSourceId(), e);

                initializationFailed.set(true);
                initializationError = Error.fromThrowable(e, "Failed to initialize " + getSourceName());
                isInitializing.set(false);

                final Error err = initializationError;
                executeOnMainThread(() -> callback.onInitializationFailed(err));
            }
        });
    }

    /**
     * Synchronous initialisation — delegates to {@link #onInitialize(Context)}.
     * Used as a fallback when the async path is not available.
     */
    @Override
    public void initialize(@NonNull Context context) {
        if (initialized) return;
        this.context = context.getApplicationContext();
        try {
            NetworkConfig config = getNetworkConfig();
            if (config != null) config.validate();
            onInitialize(this.context);
            initialized = true;
        } catch (Exception e) {
            logError("Synchronous init failed for " + getSourceId(), e);
            initializationFailed.set(true);
            initializationError = Error.fromThrowable(e, "Failed to initialize " + getSourceName());
        }
    }

    @Override
    public void cleanup() {
        cancelOperations();
        initialized = false;
        isInitializing.set(false);
    }

    // =========================================================================
    // CAPABILITIES — sensible defaults, override as needed
    // =========================================================================

    @Override
    public boolean supportsBarcodeLookup() {
        return false;
    }

    @Override
    public boolean requiresNetwork() {
        return getNetworkConfig() != null;
    }

    @NonNull
    @Override
    public Set<String> getSupportedLanguages() {
        return new HashSet<>();
    }

    @NonNull
    @Override
    public String getPrimaryLanguage() {
        return "en";
    }

    @Override
    public void cancelOperations() {
        logDebug("cancelOperations() called on " + getSourceId());
    }

    // =========================================================================
    // SOURCE INFO  (diagnostic snapshot)
    // =========================================================================

    @NonNull
    @Override
    public DataSourceInfo getSourceInfo() {
        return new DataSourceInfo.Builder(getSourceId())
                .setName(getSourceName())
                .setEnabled(isEnabled())
                .setRequiresNetwork(requiresNetwork())
                .setTotalRequests(totalRequests)
                .setSuccessCount(successCount)
                .setErrorCount(errorCount)
                .setLastUsed(lastUsed)
                .setHealth(getHealthStatus())
                .build();
    }

    @NonNull
    protected DataSourceInfo.HealthStatus getHealthStatus() {
        if (totalRequests == 0) return DataSourceInfo.HealthStatus.READY;
        double errorRate = (double) errorCount / totalRequests;
        return DataSourceInfo.HealthStatus.fromErrorRate(errorRate);
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    /**
     * Check that the source is enabled and initialised before serving a request.
     * Returns false and fires callback.onError() if the check fails.
     */
    protected <T> boolean checkEnabled(@NonNull DataSourceCallback<T> callback) {
        if (!isEnabled()) {
            handleError(Error.notFound("Data source " + getSourceId() + " is disabled"), callback);
            return false;
        }
        if (!initialized) {
            handleError(Error.unknown("Data source " + getSourceId() + " not initialized", null), callback);
            return false;
        }
        return true;
    }

    protected <T> void handleError(@NonNull Error error,
                                   @NonNull DataSourceCallback<T> callback) {
        onOperationError();
        ErrorLogger.log(error, "DataSource: " + getSourceId());
        executeOnMainThread(() -> callback.onError(error));
    }

    protected <T> void handleException(@NonNull Throwable throwable,
                                       @Nullable String userMessage,
                                       @NonNull DataSourceCallback<T> callback) {
        handleError(Error.fromThrowable(throwable, userMessage), callback);
    }

    protected <T> void handleDataSourceException(@NonNull DataSourceException exception,
                                                 @NonNull DataSourceCallback<T> callback) {
        handleError(exception.toError(), callback);
    }

    protected void executeOnMainThread(@NonNull Runnable runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run();
        } else {
            mainHandler.post(runnable);
        }
    }

    protected boolean isNetworkAvailable() {
        // TODO: wire up to a real ConnectivityManager check if needed
        return true;
    }

    // =========================================================================
    // STATISTICS
    // =========================================================================

    protected void onOperationStart() {
        totalRequests++;
        lastUsed = System.currentTimeMillis();
    }

    protected void onOperationSuccess() {
        successCount++;
    }

    protected void onOperationError() {
        errorCount++;
    }

    protected void resetStatistics() {
        totalRequests = 0;
        successCount  = 0;
        errorCount    = 0;
    }

    // =========================================================================
    // LOGGING
    // =========================================================================

    @NonNull
    protected String getLogTag() {
        return "DataSource_" + getSourceId();
    }

    protected void logDebug(@NonNull String message) {
        NetworkConfig config = getNetworkConfig();
        if (config != null && config.isDebugLoggingEnabled()) {
            android.util.Log.d(getLogTag(), message);
        }
    }

    protected void logInfo(@NonNull String message) {
        android.util.Log.i(getLogTag(), message);
    }

    protected void logWarn(@NonNull String message) {
        android.util.Log.w(getLogTag(), message);
    }

    protected void logError(@NonNull String message, @Nullable Throwable throwable) {
        if (throwable != null) android.util.Log.e(getLogTag(), message, throwable);
        else                   android.util.Log.e(getLogTag(), message);
    }

    @Override
    @NonNull
    public String toString() {
        return String.format("%s{id=%s, enabled=%s, status=%s, requests=%d, errors=%d}",
                getClass().getSimpleName(), getSourceId(),
                isEnabled(), getStatus(), totalRequests, errorCount);
    }
}