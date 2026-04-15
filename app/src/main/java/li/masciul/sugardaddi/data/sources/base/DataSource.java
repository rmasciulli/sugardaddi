package li.masciul.sugardaddi.data.sources.base;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.Set;

import li.masciul.sugardaddi.core.models.FoodProduct;
import li.masciul.sugardaddi.data.network.NetworkConfig;

/**
 * DataSource - Base interface for all food data sources
 *
 * UPDATED v2.0 (Network Refactor):
 * - Integrated with NetworkConfig for unified network management
 * - Uses Error instead of ApiError
 * - Added enable/disable capability
 * - Added status tracking
 * - Enhanced lifecycle management
 *
 * DESIGN PHILOSOPHY:
 * - Simple: Core operations (search, getProduct, getByBarcode)
 * - Flexible: Supports both network and local data sources
 * - Consistent: All sources use the same callback pattern
 * - Observable: Status tracking for data source availability
 * - Configurable: Network behavior via NetworkConfig
 *
 * IMPLEMENTATION GUIDE:
 * - Extend BaseDataSource for common functionality
 * - Provide NetworkConfig implementation (e.g., OpenFoodFactsConfig, CiqualConfig)
 * - Implement search/getProduct/getByBarcode logic
 * - Use DataSourceCallback for async operations
 * - Throw DataSourceException for errors
 *
 * @author SugarDaddi Team
 * @version 2.0 (Network Refactor)
 */
public interface DataSource {

    // ========== IDENTIFICATION ==========

    /**
     * Get unique source identifier (e.g., "OPENFOODFACTS", "CIQUAL")
     * Must match the NetworkConfig.getSourceId()
     */
    @NonNull
    String getSourceId();

    /**
     * Get human-readable source name for UI display
     * Example: "OpenFoodFacts", "Ciqual"
     */
    @NonNull
    String getSourceName();

    /**
     * Get detailed information about this data source
     */
    @NonNull
    DataSourceInfo getSourceInfo();

    // ========== NETWORK CONFIGURATION ==========

    /**
     * Get network configuration for this data source
     * Returns null for local-only data sources (e.g., USER, CUSTOM)
     */
    @Nullable
    NetworkConfig getNetworkConfig();

    // ========== STATUS & AVAILABILITY ==========

    /**
     * Check if source is currently enabled
     * Disabled sources will be skipped by DataSourceAggregator
     */
    boolean isEnabled();

    /**
     * Enable or disable this data source
     */
    void setEnabled(boolean enabled);

    /**
     * Check if source is currently available
     * Considers: enabled status, network availability, API health
     */
    boolean isAvailable();

    /**
     * Get current status of this data source
     */
    @NonNull
    DataSourceStatus getStatus();

    // ========== CORE OPERATIONS ==========

    /**
     /**
     * Search for products in this data source
     *
     * IMPORTANT: Check isEnabled() before executing search
     *
     * @param query Search query (min 3 characters recommended)
     * @param language Target language code (e.g., "en", "fr")
     * @param limit Maximum results to return (1-100)
     * @param page Page number (1-based, use 1 for first page)
     * @param callback Callback for results
     */
    void search(@NonNull String query, @NonNull String language, int limit, int page,
                @NonNull DataSourceCallback<SearchResult> callback);

    /**
     * Get product by source-specific ID
     *
     * @param productId Product ID specific to this source
     * @param language Target language code
     * @param callback Callback for result
     */
    void getProduct(@NonNull String productId, @NonNull String language,
                    @NonNull DataSourceCallback<FoodProduct> callback);

    /**
     * Get product by barcode (if supported)
     *
     * @param barcode Product barcode (8-15 digits)
     * @param language Target language code
     * @param callback Callback for result
     */
    void getProductByBarcode(@NonNull String barcode, @NonNull String language,
                             @NonNull DataSourceCallback<FoodProduct> callback);

    /**
     * Cancel any ongoing operations
     * Called when user navigates away or app is closing
     */
    void cancelOperations();

    // ========== CAPABILITIES ==========

    /**
     * Check if this source supports barcode lookup
     */
    boolean supportsBarcodeLookup();

    /**
     * Check if this source requires network connection
     */
    boolean requiresNetwork();

    /**
     * Get languages supported by this source
     * Empty set means all languages supported
     */
    @NonNull
    Set<String> getSupportedLanguages();

    /**
     * Get primary language of this source
     * Example: "fr" for Ciqual, "en" for OpenFoodFacts
     */
    @NonNull
    String getPrimaryLanguage();

    // ========== LIFECYCLE ==========

    /**
     * Initialize the data source
     * Called once when app starts or source is first accessed
     *
     * @param context Application context
     */
    void initialize(@NonNull Context context);

    /**
     * Clean up resources
     * Called when app is closing or source is being removed
     */
    void cleanup();

    // ========== NESTED CLASSES ==========

    /**
     * Search result container
     * Immutable result object with metadata
     */
    class SearchResult {
        @NonNull
        public final List<FoodProduct> items;
        public final int totalCount;      // Total results available (can be > items.size())
        public final boolean hasMore;     // True if more results available (pagination)
        public final String query;        // Original search query
        public final String language;     // Language used for search
        public final String sourceId;     // Source that provided these results

        public SearchResult(@NonNull List<FoodProduct> items, int totalCount,
                            boolean hasMore, String query, String language, String sourceId) {
            this.items = items;
            this.totalCount = totalCount;
            this.hasMore = hasMore;
            this.query = query;
            this.language = language;
            this.sourceId = sourceId;
        }

        /**
         * Check if result has any items
         */
        public boolean isEmpty() {
            return items.isEmpty();
        }

        /**
         * Get number of items in this result
         */
        public int getItemCount() {
            return items.size();
        }
    }

    /**
     * Data source status enum
     * Used for monitoring and debugging
     */
    enum DataSourceStatus {
        /** Source is ready and operational */
        READY("Ready", true),

        /** Source is initializing */
        INITIALIZING("Initializing...", false),

        /** Source is disabled by user/config */
        DISABLED("Disabled", false),

        /** Network not available but required */
        NO_NETWORK("No Network", false),

        /** API rate limited */
        RATE_LIMITED("Rate Limited", false),

        /** API error or unreachable */
        ERROR("Error", false),

        /** Source is being cleaned up */
        CLEANUP("Shutting Down", false);

        private final String displayText;
        private final boolean operational;

        DataSourceStatus(String displayText, boolean operational) {
            this.displayText = displayText;
            this.operational = operational;
        }

        public String getDisplayText() {
            return displayText;
        }

        public boolean isOperational() {
            return operational;
        }
    }
}