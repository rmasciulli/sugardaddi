package li.masciul.sugardaddi.data.sources.base;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * DataSourceInfo — Immutable diagnostic snapshot of a data source's runtime state.
 *
 * ARCHITECTURE v3.0 — Settings refactor
 * ======================================
 * REMOVED: {@code priority} field.
 *   Priority was used by DataSourceConfig to sort search results. DataSourceConfig
 *   is deleted. Sources are now ordered alphabetically for Settings display and
 *   by registration order for search. There is no user-adjustable priority.
 *
 * KEPT: {@code enabled} field (as a read-only snapshot).
 *   The live source of truth for enabled state is each source's own
 *   SharedPreferences (read via DataSource.isEnabled()). This field captures
 *   that value at the moment getSourceInfo() is called — useful for logging
 *   and diagnostics. It is NOT used to make routing decisions.
 *
 * PURPOSE
 * =======
 * This object is built fresh by BaseDataSource.getSourceInfo() and is intended
 * for diagnostics, logging, and the status summary in the settings card header.
 * It is NOT a configuration object — nothing routes or filters based on its fields.
 *
 * IMMUTABILITY
 * ============
 * All fields are final. To "update" info, call getSourceInfo() again — it builds
 * a new snapshot from current runtime state.
 */
public class DataSourceInfo {

    // =========================================================================
    // CORE IDENTITY
    // =========================================================================

    @NonNull
    private final String id;

    @NonNull
    private final String name;

    @Nullable
    private final String description;

    @Nullable
    private final String version;

    // =========================================================================
    // ATTRIBUTION
    // =========================================================================

    private final boolean requiresAttribution;

    @Nullable
    private final String attributionText;

    @Nullable
    private final String websiteUrl;

    // =========================================================================
    // RUNTIME SNAPSHOT (not configuration)
    // =========================================================================

    /** Snapshot of DataSource.isEnabled() at the time this info was built. */
    private final boolean enabled;

    /** Whether this source requires a network connection. */
    private final boolean requiresNetwork;

    // =========================================================================
    // STATISTICS
    // =========================================================================

    private final long   createdAt;
    private final long   lastUpdated;
    private final long   lastUsed;
    private final int    totalRequests;
    private final int    errorCount;
    private final int    successCount;

    // =========================================================================
    // HEALTH
    // =========================================================================

    @NonNull
    private final HealthStatus health;

    @Nullable
    private final String healthMessage;

    // =========================================================================
    // CONSTRUCTOR (use Builder)
    // =========================================================================

    private DataSourceInfo(Builder builder) {
        this.id                  = builder.id;
        this.name                = builder.name;
        this.description         = builder.description;
        this.version             = builder.version;
        this.requiresAttribution = builder.requiresAttribution;
        this.attributionText     = builder.attributionText;
        this.websiteUrl          = builder.websiteUrl;
        this.enabled             = builder.enabled;
        this.requiresNetwork     = builder.requiresNetwork;
        this.createdAt           = builder.createdAt;
        this.lastUpdated         = builder.lastUpdated;
        this.lastUsed            = builder.lastUsed;
        this.totalRequests       = builder.totalRequests;
        this.errorCount          = builder.errorCount;
        this.successCount        = builder.successCount;
        this.health              = builder.health;
        this.healthMessage       = builder.healthMessage;
    }

    // =========================================================================
    // GETTERS
    // =========================================================================

    @NonNull  public String getId()              { return id; }
    @NonNull  public String getName()            { return name; }
    @Nullable public String getDescription()     { return description; }
    @Nullable public String getVersion()         { return version; }
    public boolean requiresAttribution()         { return requiresAttribution; }
    @Nullable public String getAttributionText() { return attributionText; }
    @Nullable public String getWebsiteUrl()      { return websiteUrl; }

    /** Snapshot of enabled state at build time. Use DataSource.isEnabled() for live state. */
    public boolean isEnabled()          { return enabled; }
    public boolean requiresNetwork()    { return requiresNetwork; }

    public long getCreatedAt()          { return createdAt; }
    public long getLastUpdated()        { return lastUpdated; }
    public long getLastUsed()           { return lastUsed; }
    public int  getTotalRequests()      { return totalRequests; }
    public int  getErrorCount()         { return errorCount; }
    public int  getSuccessCount()       { return successCount; }

    @NonNull  public HealthStatus getHealth()        { return health; }
    @Nullable public String       getHealthMessage() { return healthMessage; }

    // =========================================================================
    // COMPUTED
    // =========================================================================

    /** @return Success rate 0.0–1.0 (1.0 if no requests yet). */
    public double getSuccessRate() {
        if (totalRequests == 0) return 1.0;
        return (double) successCount / totalRequests;
    }

    /** @return Error rate 0.0–1.0 (0.0 if no requests yet). */
    public double getErrorRate() {
        if (totalRequests == 0) return 0.0;
        return (double) errorCount / totalRequests;
    }

    /** @return True if this source was used within the last 24 hours. */
    public boolean wasUsedRecently() {
        long dayAgo = System.currentTimeMillis() - (24L * 60 * 60 * 1000);
        return lastUsed > dayAgo;
    }

    /**
     * One-line status suitable for a settings card subtitle.
     * Examples: "Healthy", "Disabled", "Degraded — high error rate".
     */
    @NonNull
    public String getStatusSummary() {
        if (!enabled) return "Disabled";
        return health.getDisplayText()
                + (healthMessage != null ? " — " + healthMessage : "");
    }

    // =========================================================================
    // BUILDER
    // =========================================================================

    public static class Builder {

        // Required
        private final String id;
        private String name;

        // Optional metadata
        @Nullable private String description;
        @Nullable private String version;
        private boolean requiresAttribution = false;
        @Nullable private String attributionText;
        @Nullable private String websiteUrl;

        // Runtime snapshot
        private boolean enabled        = true;
        private boolean requiresNetwork = true;

        // Statistics
        private long createdAt     = System.currentTimeMillis();
        private long lastUpdated   = System.currentTimeMillis();
        private long lastUsed      = 0;
        private int  totalRequests = 0;
        private int  errorCount    = 0;
        private int  successCount  = 0;

        // Health
        private HealthStatus health = HealthStatus.READY;
        @Nullable private String healthMessage;

        public Builder(@NonNull String id) {
            this.id   = id;
            this.name = id; // Fallback: use ID as name until setName() is called
        }

        public Builder setName(@NonNull String name) {
            this.name = name; return this;
        }

        public Builder setDescription(@Nullable String description) {
            this.description = description; return this;
        }

        public Builder setVersion(@Nullable String version) {
            this.version = version; return this;
        }

        public Builder setRequiresAttribution(boolean requiresAttribution) {
            this.requiresAttribution = requiresAttribution; return this;
        }

        public Builder setAttributionText(@Nullable String attributionText) {
            this.attributionText = attributionText; return this;
        }

        public Builder setWebsiteUrl(@Nullable String websiteUrl) {
            this.websiteUrl = websiteUrl; return this;
        }

        public Builder setEnabled(boolean enabled) {
            this.enabled = enabled; return this;
        }

        public Builder setRequiresNetwork(boolean requiresNetwork) {
            this.requiresNetwork = requiresNetwork; return this;
        }

        public Builder setLastUsed(long lastUsed) {
            this.lastUsed = lastUsed; return this;
        }

        public Builder setTotalRequests(int totalRequests) {
            this.totalRequests = totalRequests; return this;
        }

        public Builder setErrorCount(int errorCount) {
            this.errorCount = errorCount; return this;
        }

        public Builder setSuccessCount(int successCount) {
            this.successCount = successCount; return this;
        }

        public Builder setHealth(@NonNull HealthStatus health) {
            this.health = health; return this;
        }

        public Builder setHealthMessage(@Nullable String healthMessage) {
            this.healthMessage = healthMessage; return this;
        }

        @NonNull
        public DataSourceInfo build() {
            if (id == null || id.trim().isEmpty())
                throw new IllegalStateException("DataSource ID is required");
            if (name == null || name.trim().isEmpty())
                throw new IllegalStateException("DataSource name is required");
            return new DataSourceInfo(this);
        }
    }

    // =========================================================================
    // HEALTH STATUS ENUM
    // =========================================================================

    public enum HealthStatus {

        HEALTHY("Healthy"),
        DEGRADED("Degraded"),
        UNHEALTHY("Unhealthy"),
        READY("Ready"),
        UNKNOWN("Unknown");

        private final String displayText;

        HealthStatus(String displayText) {
            this.displayText = displayText;
        }

        public String getDisplayText() {
            return displayText;
        }

        /**
         * Derive health from a request error rate.
         * ≥50% errors → UNHEALTHY, ≥20% → DEGRADED, otherwise HEALTHY.
         */
        public static HealthStatus fromErrorRate(double errorRate) {
            if (errorRate >= 0.5) return UNHEALTHY;
            if (errorRate >= 0.2) return DEGRADED;
            return HEALTHY;
        }
    }

    @Override
    @NonNull
    public String toString() {
        return String.format(
                "DataSourceInfo{id='%s', name='%s', enabled=%s, health=%s, " +
                        "requests=%d, errors=%d, successRate=%.1f%%}",
                id, name, enabled, health,
                totalRequests, errorCount, getSuccessRate() * 100);
    }
}