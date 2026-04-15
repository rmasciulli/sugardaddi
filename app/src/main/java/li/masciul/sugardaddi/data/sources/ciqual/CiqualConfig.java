package li.masciul.sugardaddi.data.sources.ciqual;

import androidx.annotation.NonNull;
import li.masciul.sugardaddi.data.network.NetworkConfig;
import li.masciul.sugardaddi.data.network.RetryStrategy;

/**
 * CiqualConfig - Network configuration for Ciqual data source
 *
 * TRI-MODAL ARCHITECTURE SUPPORT:
 * =================================
 * This config supports three operational modes:
 *
 * MODE 1: ELASTICSEARCH API (Phase 1) âš¡
 * - Endpoint: /esearch/aliments/_search
 * - Real-time search with relevance scoring
 * - Fast, modern, no authentication required
 *
 * MODE 2: XML DOWNLOAD (Phase 2) ðŸ“¦
 * - Download full Ciqual database as ZIP
 * - Parse XML files with CiqualXmlParser
 * - One-time setup, periodic updates
 *
 * MODE 3: LOCAL DATABASE (Phase 3) ðŸ’¾
 * - Query local Room database
 * - Fast, offline-capable
 * - Populated from XML or API
 *
 * NETWORK CONFIGURATION:
 * - Base URL: https://ciqual.anses.fr/
 * - User-Agent: SugarDaddi/1.0 (Android App - Ciqual Integration)
 * - Timeouts: Standard (from NetworkConfig)
 * - Retries: Enabled with exponential backoff
 *
 * USAGE:
 * ```java
 * CiqualConfig config = new CiqualConfig();
 * CiqualDataSource source = new CiqualDataSource(context, config);
 * source.initialize(context);
 * ```
 *
 * @see NetworkConfig
 * @see CiqualDataSource
 * @see CiqualConstants
 * @author SugarDaddi Team
 * @version 1.0 (Tri-Modal Architecture)
 */
public class CiqualConfig extends NetworkConfig {

    // ========== BASE URLS ==========

    /**
     * Main Ciqual website base URL
     * Used for all API endpoints
     */
    private static final String BASE_URL = "https://ciqual.anses.fr/";

    /**
     * Elasticsearch search endpoint path
     * Appended to BASE_URL for search operations
     */
    public static final String ELASTICSEARCH_ENDPOINT = "esearch/aliments/_search";

    /**
     * XML database download URL (Phase 2)
     * Full Ciqual database as ZIP file (~50MB)
     * Updated periodically by ANSES
     */
    private static final String XML_ZIP_URL =
            "https://ciqual.anses.fr/cms/sites/default/files/inline-files/XML_2020_07_07.zip";

    // ========== USER AGENT ==========

    /**
     * User-Agent header for Ciqual requests
     * Identifies the app to ANSES servers
     */
    private static final String USER_AGENT = "SugarDaddi/1.0 (Android App - Ciqual Integration)";

    // ========== CONSTRUCTOR ==========

    /**
     * Creates Ciqual network configuration
     * Uses production environment and Ciqual source ID
     */
    public CiqualConfig() {
        super(CiqualConstants.SOURCE_ID, Environment.PRODUCTION);
        // Ciqual search is a read-only lookup -- retries add latency without benefit.
        // The aggregator latch handles timeouts; let OkHttp fail fast.
        setRetryStrategy(RetryStrategy.NONE);
    }

    // ========== REQUIRED OVERRIDES ==========

    /**
     * Returns the base URL for Ciqual API
     *
     * @return Base URL (https://ciqual.anses.fr/)
     */
    @NonNull
    @Override
    protected String getBaseUrl() {
        return BASE_URL;
    }

    /**
     * Returns the User-Agent header for requests
     *
     * @return User-Agent string identifying the app
     */
    @NonNull
    @Override
    protected String getUserAgent() {
        return USER_AGENT;
    }

    // ========== ADDITIONAL CONFIGURATION ==========

    /**
     * Returns the full Elasticsearch search endpoint URL
     * Combines base URL with Elasticsearch path
     *
     * @return Full URL to Elasticsearch endpoint
     */
    @NonNull
    public String getElasticsearchUrl() {
        return BASE_URL + ELASTICSEARCH_ENDPOINT;
    }

    /**
     * Returns the XML database download URL (Phase 2)
     * Used for downloading the full Ciqual database
     *
     * @return URL to XML ZIP file
     */
    @NonNull
    public String getXmlDownloadUrl() {
        return XML_ZIP_URL;
    }

    // ========== CAPABILITIES ==========

    /**
     * Checks if Elasticsearch API is available
     * Currently always returns true (Elasticsearch is working)
     *
     * @return true if Elasticsearch endpoint is available
     */
    public boolean isElasticsearchAvailable() {
        return true;  // Confirmed working in testing
    }

    /**
     * Checks if XML download is enabled (Phase 2)
     * Will be implemented when XML download is added
     *
     * @return true if XML download is enabled
     */
    public boolean isXmlDownloadEnabled() {
        return false;  // TODO: Phase 2 - Implement XML download
    }

    /**
     * Checks if local database is ready (Phase 3)
     * Will check database state when implemented
     *
     * @return true if local database is populated and ready
     */
    public boolean isDatabaseReady() {
        return false;  // TODO: Phase 3 - Check database state
    }
}