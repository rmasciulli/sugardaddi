package li.masciul.sugardaddi.data.network;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;

import okhttp3.Interceptor;

/**
 * NetworkConfig - Base configuration for data source network operations
 *
 * Provides a configurable, extensible base for all network operations.
 * Each data source can extend this class to customize behavior.
 *
 * FEATURES:
 * - DEV/PROD environment switching
 * - ON/OFF toggle per source
 * - Timeout configuration
 * - Retry strategy
 * - Custom headers
 * - Request interceptors
 *
 * USAGE EXAMPLE:
 * <pre>
 * public class OpenFoodFactsConfig extends NetworkConfig {
 *     public OpenFoodFactsConfig() {
 *         super("OpenFoodFacts", Environment.PRODUCTION);
 *     }
 *
 *     {@literal @}Override
 *     protected String getBaseUrl() {
 *         return isProduction()
 *             ? "https://world.openfoodfacts.org/api/v2/"
 *             : "https://world.openfoodfacts.net/api/v2/";
 *     }
 * }
 * </pre>
 *
 * @author SugarDaddi Team
 * @version 1.0
 */
public abstract class NetworkConfig {

    // ========== ENVIRONMENT ENUM ==========

    public enum Environment {
        DEVELOPMENT,
        PRODUCTION
    }

    // ========== PROPERTIES ==========

    private final String sourceId;
    private final Environment environment;
    private boolean enabled;
    private RetryStrategy retryStrategy;
    private Map<String, String> customHeaders;

    // Timeout overrides (null = use defaults from ApiConfig)
    private Integer connectTimeoutSeconds;
    private Integer readTimeoutSeconds;
    private Integer writeTimeoutSeconds;

    // ========== CONSTRUCTOR ==========

    protected NetworkConfig(@NonNull String sourceId, @NonNull Environment environment) {
        this.sourceId = sourceId;
        this.environment = environment;
        this.enabled = true;
        this.retryStrategy = RetryStrategy.EXPONENTIAL;
        this.customHeaders = new HashMap<>();

        initializeDefaults();
    }

    protected NetworkConfig(@NonNull String sourceId) {
        this(sourceId, Environment.PRODUCTION);
    }

    // ========== ABSTRACT METHODS (Must Implement) ==========

    @NonNull
    protected abstract String getBaseUrl();

    @NonNull
    protected abstract String getUserAgent();

    // ========== OPTIONAL OVERRIDES ==========

    protected void initializeDefaults() {
        // Override to set custom defaults
    }

    @NonNull
    protected Interceptor[] getInterceptors() {
        return new Interceptor[0];
    }

    protected boolean shouldRetryForError(int statusCode, @Nullable String error) {
        if (statusCode == -1) return true; // Network errors
        if (statusCode >= 500 && statusCode < 600) return true; // Server errors
        if (statusCode == 429) return true; // Rate limiting
        if (statusCode >= 400 && statusCode < 500) return false; // Client errors
        return true; // Default: retry
    }

    // ========== ENABLE/DISABLE ==========

    public void enable() {
        this.enabled = true;
    }

    public void disable() {
        this.enabled = false;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void toggle() {
        this.enabled = !this.enabled;
    }

    // ========== ENVIRONMENT CHECKS ==========

    public boolean isProduction() {
        return environment == Environment.PRODUCTION;
    }

    public boolean isDevelopment() {
        return environment == Environment.DEVELOPMENT;
    }

    @NonNull
    public Environment getEnvironment() {
        return environment;
    }

    // ========== GETTERS ==========

    @NonNull
    public String getSourceId() {
        return sourceId;
    }

    @NonNull
    public RetryStrategy getRetryStrategy() {
        return retryStrategy;
    }

    public int getConnectTimeoutSeconds() {
        return connectTimeoutSeconds != null
                ? connectTimeoutSeconds
                : ApiConfig.CONNECT_TIMEOUT_SECONDS;
    }

    public int getReadTimeoutSeconds() {
        return readTimeoutSeconds != null
                ? readTimeoutSeconds
                : ApiConfig.READ_TIMEOUT_SECONDS;
    }

    public int getWriteTimeoutSeconds() {
        return writeTimeoutSeconds != null
                ? writeTimeoutSeconds
                : ApiConfig.WRITE_TIMEOUT_SECONDS;
    }

    @NonNull
    public Map<String, String> getHeaders() {
        Map<String, String> headers = new HashMap<>(customHeaders);
        headers.put("User-Agent", getUserAgent());
        return headers;
    }

    @Nullable
    public String getHeader(String headerName) {
        return customHeaders.get(headerName);
    }

    public boolean isDebugLoggingEnabled() {
        return isDevelopment() || ApiConfig.DEBUG_LOGGING;
    }

    // ========== SETTERS (Fluent API) ==========

    @NonNull
    public NetworkConfig setRetryStrategy(@NonNull RetryStrategy strategy) {
        this.retryStrategy = strategy;
        return this;
    }

    @NonNull
    public NetworkConfig setConnectTimeoutSeconds(int seconds) {
        this.connectTimeoutSeconds = seconds;
        return this;
    }

    @NonNull
    public NetworkConfig setReadTimeoutSeconds(int seconds) {
        this.readTimeoutSeconds = seconds;
        return this;
    }

    @NonNull
    public NetworkConfig setWriteTimeoutSeconds(int seconds) {
        this.writeTimeoutSeconds = seconds;
        return this;
    }

    @NonNull
    public NetworkConfig addHeader(@NonNull String name, @NonNull String value) {
        this.customHeaders.put(name, value);
        return this;
    }

    @NonNull
    public NetworkConfig removeHeader(@NonNull String name) {
        this.customHeaders.remove(name);
        return this;
    }

    @NonNull
    public NetworkConfig clearHeaders() {
        this.customHeaders.clear();
        return this;
    }

    // ========== UTILITY METHODS ==========

    public void validate() {
        if (sourceId == null || sourceId.trim().isEmpty()) {
            throw new IllegalStateException("Source ID cannot be empty");
        }

        String baseUrl = getBaseUrl();
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            throw new IllegalStateException("Base URL cannot be empty for source: " + sourceId);
        }

        if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
            throw new IllegalStateException("Base URL must start with http:// or https://: " + baseUrl);
        }

        String userAgent = getUserAgent();
        if (userAgent == null || userAgent.trim().isEmpty()) {
            throw new IllegalStateException("User-Agent cannot be empty for source: " + sourceId);
        }
    }

    @NonNull
    public String getSummary() {
        return String.format(
                "NetworkConfig[source=%s, env=%s, enabled=%s, retry=%s, timeout=%ds/%ds/%ds]",
                sourceId,
                environment,
                enabled,
                retryStrategy,
                getConnectTimeoutSeconds(),
                getReadTimeoutSeconds(),
                getWriteTimeoutSeconds()
        );
    }

    @Override
    public String toString() {
        return getSummary();
    }
}