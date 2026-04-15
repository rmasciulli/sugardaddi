package li.masciul.sugardaddi.data.sources.base;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import li.masciul.sugardaddi.core.logging.ErrorLogger;
import li.masciul.sugardaddi.core.models.Error;
import li.masciul.sugardaddi.data.network.NetworkConfig;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * BaseDataSource - Abstract base implementation for all data sources
 *
 * v2.1: ASYNCHRONOUS INITIALIZATION
 * - Non-blocking initialization with callbacks
 * - Sources can signal when they're ready
 * - UI doesn't need to wait for all sources
 * - Parallel initialization of multiple sources
 *
 * INITIALIZATION LIFECYCLE:
 * 1. CREATED → source instantiated but not initialized
 * 2. INITIALIZING → async initialization in progress
 * 3. READY → initialized and available for use
 * 4. ERROR → initialization failed
 *
 * USAGE:
 * ```java
 * source.initialize(context, new InitializationCallback() {
 *     @Override
 *     public void onInitialized() {
 *         // Source is ready - can start using it
 *     }
 *
 *     @Override
 *     public void onInitializationFailed(Error error) {
 *         // Handle initialization failure
 *     }
 * });
 * ```
 */
public abstract class BaseDataSource implements DataSource {

    // ========== STATE ==========

    protected Context context;
    protected boolean initialized = false;
    protected boolean enabled = true;

    // Initialization state tracking
    private final AtomicBoolean isInitializing = new AtomicBoolean(false);
    private final AtomicBoolean initializationFailed = new AtomicBoolean(false);
    private Error initializationError = null;

    // ========== STATISTICS ==========

    protected int totalRequests = 0;
    protected int successCount = 0;
    protected int errorCount = 0;
    protected long lastUsed = 0;

    // ========== THREADING ==========

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final ExecutorService initExecutor = Executors.newCachedThreadPool();

    // ========== CONSTRUCTOR ==========

    protected BaseDataSource() {
        // Subclasses can initialize in their constructors
    }

    // ========== ABSTRACT METHODS ==========

    /**
     * Get network configuration (or null for local sources)
     */
    @Nullable
    public abstract NetworkConfig getNetworkConfig();

    /**
     * Perform source-specific initialization (called on background thread)
     * Override this to do heavy initialization work like loading XML files,
     * creating network clients, etc.
     *
     * DEFAULT: Does nothing (assumes source is ready immediately)
     */
    protected void onInitialize(@NonNull Context context) throws Exception {
        // Override in subclass if needed
    }

    // ========== LIFECYCLE WITH ASYNC SUPPORT ==========

    /**
     * Callback interface for initialization
     */
    public interface InitializationCallback {
        void onInitialized();
        void onInitializationFailed(@NonNull Error error);
    }

    /**
     * Asynchronous initialization with callback
     */
    public void initialize(@NonNull Context context, @NonNull InitializationCallback callback) {
        this.context = context.getApplicationContext();

        // Check if already initialized
        if (initialized) {
            executeOnMainThread(callback::onInitialized);
            return;
        }

        // Check if initialization already in progress
        if (isInitializing.getAndSet(true)) {
            logWarn("Initialization already in progress for " + getSourceId());
            return;
        }

        // Check if initialization previously failed
        if (initializationFailed.get()) {
            Error error = initializationError != null
                    ? initializationError
                    : Error.unknown("Previous initialization failed", null);
            executeOnMainThread(() -> callback.onInitializationFailed(error));
            return;
        }

        // Perform initialization on background thread
        initExecutor.execute(() -> {
            try {
                logInfo("Starting initialization for " + getSourceId());

                // Validate network config if present
                NetworkConfig config = getNetworkConfig();
                if (config != null) {
                    config.validate();
                }

                // Call subclass-specific initialization
                onInitialize(context);

                // Mark as initialized
                initialized = true;
                isInitializing.set(false);

                logInfo("Initialization completed for " + getSourceId());

                // Notify success on main thread
                executeOnMainThread(callback::onInitialized);

            } catch (Exception e) {
                logError("Initialization failed for " + getSourceId(), e);

                // Mark initialization as failed
                initializationFailed.set(true);
                initializationError = Error.fromThrowable(e, "Failed to initialize " + getSourceName());
                isInitializing.set(false);

                // Notify failure on main thread
                final Error error = initializationError;
                executeOnMainThread(() -> callback.onInitializationFailed(error));
            }
        });
    }

    @Override
    public void cleanup() {
        cancelOperations();
        this.initialized = false;
        this.isInitializing.set(false);
    }

    // ========== STATUS & AVAILABILITY ==========

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;

        NetworkConfig config = getNetworkConfig();
        if (config != null) {
            if (enabled) {
                config.enable();
            } else {
                config.disable();
            }
        }
    }

    @Override
    public boolean isAvailable() {
        if (!enabled) {
            return false;
        }

        if (!initialized || isInitializing.get()) {
            return false;
        }

        if (initializationFailed.get()) {
            return false;
        }

        NetworkConfig config = getNetworkConfig();
        if (config != null && !config.isEnabled()) {
            return false;
        }

        return true;
    }

    @NonNull
    @Override
    public DataSourceStatus getStatus() {
        if (!enabled) {
            return DataSourceStatus.DISABLED;
        }

        if (initializationFailed.get()) {
            return DataSourceStatus.ERROR;
        }

        if (!initialized || isInitializing.get()) {
            return DataSourceStatus.INITIALIZING;
        }

        if (requiresNetwork() && !isNetworkAvailable()) {
            return DataSourceStatus.NO_NETWORK;
        }

        // Check error rate for health
        if (totalRequests > 10) {
            double errorRate = (double) errorCount / totalRequests;
            if (errorRate > 0.5) {
                return DataSourceStatus.ERROR;
            }
        }

        return DataSourceStatus.READY;
    }

    // ========== CAPABILITIES ==========

    @Override
    public boolean supportsBarcodeLookup() {
        return false;
    }

    @Override
    public boolean requiresNetwork() {
        NetworkConfig config = getNetworkConfig();
        return config != null;
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

    // ========== INFO ==========

    @NonNull
    @Override
    public DataSourceInfo getSourceInfo() {
        return new DataSourceInfo.Builder(getSourceId())
                .setName(getSourceName())
                .setEnabled(enabled)
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
        if (totalRequests == 0) {
            return DataSourceInfo.HealthStatus.READY;
        }

        double errorRate = (double) errorCount / totalRequests;
        return DataSourceInfo.HealthStatus.fromErrorRate(errorRate);
    }

    // ========== HELPER METHODS ==========

    protected <T> boolean checkEnabled(@NonNull DataSourceCallback<T> callback) {
        if (!enabled) {
            Error error = Error.notFound("Data source " + getSourceId() + " is disabled");
            handleError(error, callback);
            return false;
        }

        if (!initialized) {
            Error error = Error.unknown("Data source " + getSourceId() + " not initialized", null);
            handleError(error, callback);
            return false;
        }

        return true;
    }

    protected <T> void handleError(@NonNull Error error, @NonNull DataSourceCallback<T> callback) {
        onOperationError();
        ErrorLogger.log(error, "DataSource: " + getSourceId());
        executeOnMainThread(() -> callback.onError(error));
    }

    protected <T> void handleException(@NonNull Throwable throwable,
                                       @Nullable String userMessage,
                                       @NonNull DataSourceCallback<T> callback) {
        Error error = Error.fromThrowable(throwable, userMessage);
        handleError(error, callback);
    }

    protected <T> void handleDataSourceException(@NonNull DataSourceException exception,
                                                 @NonNull DataSourceCallback<T> callback) {
        Error error = exception.toError();
        handleError(error, callback);
    }

    protected void executeOnMainThread(@NonNull Runnable runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run();
        } else {
            mainHandler.post(runnable);
        }
    }

    protected boolean isNetworkAvailable() {
        // TODO: Implement actual network check
        return true;
    }

    // ========== STATISTICS ==========

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
        successCount = 0;
        errorCount = 0;
    }

    // ========== LOGGING ==========

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
        if (throwable != null) {
            android.util.Log.e(getLogTag(), message, throwable);
        } else {
            android.util.Log.e(getLogTag(), message);
        }
    }

    @Override
    public void cancelOperations() {
        logDebug("cancelOperations() called");
    }

    @Override
    @NonNull
    public String toString() {
        return String.format("%s{id=%s, enabled=%s, status=%s, requests=%d, errors=%d}",
                getClass().getSimpleName(), getSourceId(), enabled, getStatus(),
                totalRequests, errorCount);
    }
}