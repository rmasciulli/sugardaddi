package li.masciul.sugardaddi.data.sources.base;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.Set;

import li.masciul.sugardaddi.core.models.FoodProduct;
import li.masciul.sugardaddi.data.network.NetworkConfig;
import li.masciul.sugardaddi.data.sources.base.settings.SettingsProvider;

/**
 * DataSource — Base interface for all food data sources.
 *
 * ARCHITECTURE v3.0 — Settings refactor
 * ======================================
 * Three additions vs v2.0:
 *
 *   1. {@link #isEnabled()} / {@link #setEnabled(boolean)}
 *      Operational enable/disable persisted by each source in its own
 *      SharedPreferences.  Replaces the former DataSourceConfig class which
 *      kept a single cross-source enabled-set — a design that required every
 *      new source to be registered in three separate places.
 *
 *   2. {@link #getSettingsProvider()}
 *      Returns the source's optional settings contract (credentials, local DB,
 *      broadcast actions).  Returning null means "no user-configurable settings".
 *      The Settings UI never imports source-specific classes; it only calls
 *      methods on this interface.
 *
 * WHAT BELONGS HERE vs. SettingsProvider
 * =======================================
 * isEnabled/setEnabled ARE on this interface because the DataSourceManager and
 * DataSourceAggregator need to know whether to include a source in searches —
 * that is an operational concern, not a UI concern.
 *
 * Everything else (credentials, import control, broadcast actions) lives in
 * SettingsProvider, which is strictly a UI contract.
 *
 * IMPLEMENTATION GUIDE
 * ====================
 * - Extend BaseDataSource for common boilerplate
 * - Provide a NetworkConfig subclass for HTTP sources (null for local-only)
 * - Implement search / getProduct / getProductByBarcode for data retrieval
 * - Override getSettingsProvider() if the source has user-configurable state
 */
public interface DataSource {

    // =========================================================================
    // IDENTIFICATION
    // =========================================================================

    /**
     * Stable unique identifier for this source.
     * Used as a map key in DataSourceManager and as a Room sourceId discriminator.
     * Convention: SCREAMING_SNAKE_CASE — e.g. "OPENFOODFACTS", "CIQUAL", "USDA".
     */
    @NonNull
    String getSourceId();

    /**
     * Human-readable name for UI display.
     * Example: "OpenFoodFacts", "Ciqual", "USDA FoodData Central".
     */
    @NonNull
    String getSourceName();

    /**
     * Rich metadata snapshot for diagnostics and logging.
     * Contains health, request counts, error rates — NOT configuration.
     * Built fresh on each call from current runtime state.
     */
    @NonNull
    DataSourceInfo getSourceInfo();

    // =========================================================================
    // NETWORK CONFIGURATION
    // =========================================================================

    /**
     * HTTP configuration for this source, or null for local-only sources.
     * Used by NetworkClient to build OkHttpClient instances.
     */
    @Nullable
    NetworkConfig getNetworkConfig();

    // =========================================================================
    // ENABLE / DISABLE  (operational — affects search participation)
    // =========================================================================

    /**
     * Whether this source is currently enabled by the user.
     *
     * Disabled sources are skipped by DataSourceAggregator.
     * Each source persists this flag in its own SharedPreferences so the
     * user's preference survives app restarts without a shared config class.
     *
     * Default: true (enabled on first launch).
     */
    boolean isEnabled();

    /**
     * Enable or disable this source.
     *
     * Implementations must persist the new value immediately so it survives
     * process death.  The DataSourceManager calls this when the user toggles
     * the switch on the settings card.
     *
     * @param context Application context (needed for SharedPreferences write)
     * @param enabled true to include in searches, false to exclude
     */
    void setEnabled(@NonNull Context context, boolean enabled);

    // =========================================================================
    // AVAILABILITY  (runtime — considers init state, network, error rate)
    // =========================================================================

    /**
     * True if the source is enabled, initialised, and ready to serve requests.
     * A source that is enabled but still initialising returns false here.
     */
    boolean isAvailable();

    /** Current lifecycle/health status for UI display. */
    @NonNull
    DataSourceStatus getStatus();

    // =========================================================================
    // CORE OPERATIONS
    // =========================================================================

    /**
     * Search for food products matching the query.
     *
     * @param query    Search string (minimum 3 characters recommended)
     * @param language BCP-47 language code, e.g. "en", "fr"
     * @param limit    Maximum results per page (1–100)
     * @param page     1-based page number
     * @param callback Receives results, loading state, or error
     */
    void search(@NonNull String query,
                @NonNull String language,
                int limit,
                int page,
                @NonNull DataSourceCallback<SearchResult> callback);

    /**
     * Fetch a single product by its source-specific identifier.
     *
     * @param productId Source-native product ID (barcode for OFF, alim_code for Ciqual)
     * @param language  BCP-47 language code
     * @param callback  Receives the product or an error
     */
    void getProduct(@NonNull String productId,
                    @NonNull String language,
                    @NonNull DataSourceCallback<FoodProduct> callback);

    /**
     * Fetch a product by barcode.  Sources that do not support barcode lookup
     * should call {@code callback.onError(...)} immediately.
     *
     * @param barcode  EAN/UPC barcode string
     * @param language BCP-47 language code
     * @param callback Receives the product or an error
     */
    void getProductByBarcode(@NonNull String barcode,
                             @NonNull String language,
                             @NonNull DataSourceCallback<FoodProduct> callback);

    /** Cancel all ongoing network/database operations for this source. */
    void cancelOperations();

    // =========================================================================
    // CAPABILITIES
    // =========================================================================

    /** True if this source can perform barcode lookups. */
    boolean supportsBarcodeLookup();

    /** True if this source requires a network connection to function. */
    boolean requiresNetwork();

    /**
     * BCP-47 codes of languages this source explicitly supports.
     * An empty set means "all languages supported" (e.g. OpenFoodFacts).
     */
    @NonNull
    Set<String> getSupportedLanguages();

    /**
     * The language in which this source's data is natively authored.
     * Example: "fr" for Ciqual, "en" for USDA.
     */
    @NonNull
    String getPrimaryLanguage();

    // =========================================================================
    // LIFECYCLE
    // =========================================================================

    /**
     * Synchronous initialisation.  Called once by DataSourceManager on a
     * background thread.  Heavy work (HTTP clients, XML parsing) belongs here.
     */
    void initialize(@NonNull Context context);

    /**
     * Release all resources held by this source.
     * Called when the app is closing or the source is being deregistered.
     */
    void cleanup();

    // =========================================================================
    // SETTINGS (UI contract — optional)
    // =========================================================================

    /**
     * Returns the settings contract for this source, or null if the source
     * has no user-configurable state beyond the enable/disable toggle.
     *
     * The Settings UI casts nothing to source-specific types; it only calls
     * methods on the returned SettingsProvider.  This keeps the UI layer
     * completely decoupled from individual source implementations.
     *
     * Override in subclasses that have credentials, local databases, or
     * import services.  The default implementation returns null.
     */
    @Nullable
    default SettingsProvider getSettingsProvider() {
        return null;
    }

    // =========================================================================
    // NESTED: SearchResult
    // =========================================================================

    /**
     * Immutable result container returned by {@link #search}.
     */
    class SearchResult {

        @NonNull
        public final List<FoodProduct> items;

        /** Total results available server-side (may exceed items.size()). */
        public final int totalCount;

        /** True if additional pages are available. */
        public final boolean hasMore;

        public final String query;
        public final String language;
        public final String sourceId;

        public SearchResult(@NonNull List<FoodProduct> items,
                            int totalCount,
                            boolean hasMore,
                            String query,
                            String language,
                            String sourceId) {
            this.items = items;
            this.totalCount = totalCount;
            this.hasMore = hasMore;
            this.query = query;
            this.language = language;
            this.sourceId = sourceId;
        }

        public boolean isEmpty() {
            return items.isEmpty();
        }

        public int getItemCount() {
            return items.size();
        }
    }

    // =========================================================================
    // NESTED: DataSourceStatus
    // =========================================================================

    /**
     * Runtime health/lifecycle status for UI display and diagnostics.
     */
    enum DataSourceStatus {

        READY("Ready", true),
        INITIALIZING("Initializing…", false),
        DISABLED("Disabled", false),
        NO_NETWORK("No network", false),
        RATE_LIMITED("Rate limited", false),
        ERROR("Error", false),
        CLEANUP("Shutting down", false);

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