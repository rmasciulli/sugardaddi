package li.masciul.sugardaddi.data.sources.usda;

import androidx.annotation.NonNull;

import li.masciul.sugardaddi.data.network.NetworkConfig;
import li.masciul.sugardaddi.data.network.RetryStrategy;

/**
 * USDAConfig — NetworkConfig implementation for USDA FoodData Central.
 *
 * DUAL-MODE ARCHITECTURE:
 * ========================
 * MODE 1: REST API (always active)
 *   - Base URL: https://api.nal.usda.gov/fdc/v1/
 *   - Auth: api_key query parameter (from BuildConfig.USDA_API_KEY)
 *   - Data types: Foundation + SR Legacy + Survey (FNDDS)
 *   - No caching restriction — FDC data is CC0 public domain
 *
 * MODE 2: Local JSON database (opt-in, user-initiated)
 *   - Downloads Foundation Foods + SR Legacy JSON ZIPs from fdc.nal.usda.gov
 *   - Parses into Room DB via USDAImportService (foreground service)
 *   - Not auto-triggered: ~215MB download is Wifi-appropriate but not assumed
 *   - Once imported, search() queries Room first, API as fallback
 *
 * RETRY STRATEGY:
 * USDA FDC is a stable government API with good uptime. Standard exponential
 * backoff is appropriate — unlike Ciqual which uses NONE to fail fast.
 */
public class USDAConfig extends NetworkConfig {

    // ===== CONSTRUCTOR =====

    public USDAConfig() {
        super(USDAConstants.SOURCE_ID, Environment.PRODUCTION);
        setRetryStrategy(RetryStrategy.EXPONENTIAL);
    }

    // ===== REQUIRED OVERRIDES =====

    @NonNull
    @Override
    protected String getBaseUrl() {
        return USDAConstants.BASE_URL;
    }

    @NonNull
    @Override
    protected String getUserAgent() {
        return USDAConstants.USER_AGENT;
    }

    // ===== TIMEOUT OVERRIDES =====
    // SR Legacy is 205MB — give downloads room to breathe.
    // API calls themselves are fast and use default timeouts.

    /**
     * Download timeout for large JSON files (5 minutes).
     * Overrides the default READ_TIMEOUT_SECONDS from ApiConfig for
     * long-running download operations in USDAImportService.
     */
    public int getDownloadTimeoutMs() {
        return USDAConstants.DOWNLOAD_TIMEOUT_MS;
    }

    public int getConnectTimeoutMs() {
        return USDAConstants.CONNECT_TIMEOUT_MS;
    }
}