package li.masciul.sugardaddi.data.sources.base;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * DataSourceInfo - Information and metadata about a data source
 *
 * UPDATED v2.0 (Network Refactor):
 * - Added status tracking (enabled, health, last used)
 * - Added statistics (request count, error count)
 * - Enhanced metadata (version, capabilities)
 * - Builder pattern for easy construction
 *
 * DESIGN PHILOSOPHY:
 * - Immutable: Once created, info doesn't change (use builder for updates)
 * - Complete: All relevant metadata in one place
 * - UI-friendly: Display text, icons, attribution
 * - Debug-friendly: Stats, timestamps, health info
 *
 * USAGE:
 * ```java
 * DataSourceInfo info = new DataSourceInfo.Builder("OPENFOODFACTS")
 *     .setName("OpenFoodFacts")
 *     .setDescription("Open database of food products")
 *     .setWebsiteUrl("https://world.openfoodfacts.org")
 *     .setRequiresAttribution(true)
 *     .setAttributionText("Data from OpenFoodFacts.org")
 *     .setPriority(100)
 *     .setEnabled(true)
 *     .build();
 * ```
 *
 * @author SugarDaddi Team
 * @version 2.0 (Network Refactor)
 */
public class DataSourceInfo {

    // ========== CORE METADATA ==========

    @NonNull
    private final String id;

    @NonNull
    private final String name;

    @Nullable
    private final String description;

    @Nullable
    private final String version;

    @Nullable
    private final String iconUrl;

    // ========== ATTRIBUTION ==========

    private final boolean requiresAttribution;

    @Nullable
    private final String attributionText;

    @Nullable
    private final String websiteUrl;

    // ========== CONFIGURATION ==========

    private final int priority;                // For sorting/merging (0-100)
    private final boolean enabled;             // Is this source enabled?
    private final boolean requiresNetwork;     // Does this source need network?

    // ========== STATISTICS ==========

    private final long createdAt;              // When this info was created
    private final long lastUpdated;            // Last metadata update
    private final long lastUsed;               // Last time source was used
    private final int totalRequests;           // Total requests made
    private final int errorCount;              // Number of errors
    private final int successCount;            // Number of successes

    // ========== HEALTH ==========

    @NonNull
    private final HealthStatus health;

    @Nullable
    private final String healthMessage;        // Optional health message

    // ========== CONSTRUCTOR (Use Builder) ==========

    private DataSourceInfo(Builder builder) {
        this.id = builder.id;
        this.name = builder.name;
        this.description = builder.description;
        this.version = builder.version;
        this.iconUrl = builder.iconUrl;
        this.requiresAttribution = builder.requiresAttribution;
        this.attributionText = builder.attributionText;
        this.websiteUrl = builder.websiteUrl;
        this.priority = builder.priority;
        this.enabled = builder.enabled;
        this.requiresNetwork = builder.requiresNetwork;
        this.createdAt = builder.createdAt;
        this.lastUpdated = builder.lastUpdated;
        this.lastUsed = builder.lastUsed;
        this.totalRequests = builder.totalRequests;
        this.errorCount = builder.errorCount;
        this.successCount = builder.successCount;
        this.health = builder.health;
        this.healthMessage = builder.healthMessage;
    }

    // ========== GETTERS ==========

    @NonNull
    public String getId() {
        return id;
    }

    @NonNull
    public String getName() {
        return name;
    }

    @Nullable
    public String getDescription() {
        return description;
    }

    @Nullable
    public String getVersion() {
        return version;
    }

    @Nullable
    public String getIconUrl() {
        return iconUrl;
    }

    public boolean requiresAttribution() {
        return requiresAttribution;
    }

    @Nullable
    public String getAttributionText() {
        return attributionText;
    }

    @Nullable
    public String getWebsiteUrl() {
        return websiteUrl;
    }

    public int getPriority() {
        return priority;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean requiresNetwork() {
        return requiresNetwork;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getLastUpdated() {
        return lastUpdated;
    }

    public long getLastUsed() {
        return lastUsed;
    }

    public int getTotalRequests() {
        return totalRequests;
    }

    public int getErrorCount() {
        return errorCount;
    }

    public int getSuccessCount() {
        return successCount;
    }

    @NonNull
    public HealthStatus getHealth() {
        return health;
    }

    @Nullable
    public String getHealthMessage() {
        return healthMessage;
    }

    // ========== COMPUTED PROPERTIES ==========

    /**
     * Get success rate (0.0 - 1.0)
     */
    public double getSuccessRate() {
        if (totalRequests == 0) return 1.0; // No requests yet, assume healthy
        return (double) successCount / totalRequests;
    }

    /**
     * Get error rate (0.0 - 1.0)
     */
    public double getErrorRate() {
        if (totalRequests == 0) return 0.0;
        return (double) errorCount / totalRequests;
    }

    /**
     * Check if source was used recently (within last 24 hours)
     */
    public boolean wasUsedRecently() {
        long dayAgo = System.currentTimeMillis() - (24 * 60 * 60 * 1000);
        return lastUsed > dayAgo;
    }

    /**
     * Get display name with emoji (if available)
     */
    @NonNull
    public String getDisplayName() {
        // Note: Emoji is in DataSource enum, not here
        return name;
    }

    /**
     * Get status summary for UI
     */
    @NonNull
    public String getStatusSummary() {
        if (!enabled) {
            return "Disabled";
        }
        return health.getDisplayText() +
                (healthMessage != null ? " - " + healthMessage : "");
    }

    // ========== BUILDER ==========

    /**
     * Builder for DataSourceInfo
     */
    public static class Builder {
        // Required
        private final String id;
        private String name;

        // Optional
        private String description;
        private String version;
        private String iconUrl;
        private boolean requiresAttribution = false;
        private String attributionText;
        private String websiteUrl;
        private int priority = 50; // Default medium priority
        private boolean enabled = true;
        private boolean requiresNetwork = true;
        private long createdAt = System.currentTimeMillis();
        private long lastUpdated = System.currentTimeMillis();
        private long lastUsed = 0;
        private int totalRequests = 0;
        private int errorCount = 0;
        private int successCount = 0;
        private HealthStatus health = HealthStatus.READY;
        private String healthMessage;

        public Builder(@NonNull String id) {
            this.id = id;
            this.name = id; // Default name = id
        }

        public Builder setName(@NonNull String name) {
            this.name = name;
            return this;
        }

        public Builder setDescription(@Nullable String description) {
            this.description = description;
            return this;
        }

        public Builder setVersion(@Nullable String version) {
            this.version = version;
            return this;
        }

        public Builder setIconUrl(@Nullable String iconUrl) {
            this.iconUrl = iconUrl;
            return this;
        }

        public Builder setRequiresAttribution(boolean requiresAttribution) {
            this.requiresAttribution = requiresAttribution;
            return this;
        }

        public Builder setAttributionText(@Nullable String attributionText) {
            this.attributionText = attributionText;
            return this;
        }

        public Builder setWebsiteUrl(@Nullable String websiteUrl) {
            this.websiteUrl = websiteUrl;
            return this;
        }

        public Builder setPriority(int priority) {
            this.priority = Math.max(0, Math.min(100, priority)); // Clamp 0-100
            return this;
        }

        public Builder setEnabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder setRequiresNetwork(boolean requiresNetwork) {
            this.requiresNetwork = requiresNetwork;
            return this;
        }

        public Builder setLastUsed(long lastUsed) {
            this.lastUsed = lastUsed;
            return this;
        }

        public Builder setTotalRequests(int totalRequests) {
            this.totalRequests = totalRequests;
            return this;
        }

        public Builder setErrorCount(int errorCount) {
            this.errorCount = errorCount;
            return this;
        }

        public Builder setSuccessCount(int successCount) {
            this.successCount = successCount;
            return this;
        }

        public Builder setHealth(@NonNull HealthStatus health) {
            this.health = health;
            return this;
        }

        public Builder setHealthMessage(@Nullable String healthMessage) {
            this.healthMessage = healthMessage;
            return this;
        }

        @NonNull
        public DataSourceInfo build() {
            if (id == null || id.trim().isEmpty()) {
                throw new IllegalStateException("DataSource ID is required");
            }
            if (name == null || name.trim().isEmpty()) {
                throw new IllegalStateException("DataSource name is required");
            }
            return new DataSourceInfo(this);
        }
    }

    // ========== HEALTH STATUS ENUM ==========

    /**
     * Health status of data source
     */
    public enum HealthStatus {
        /** Source is healthy and operational */
        HEALTHY("Healthy"),

        /** Source is operational but degraded */
        DEGRADED("Degraded"),

        /** Source is experiencing issues */
        UNHEALTHY("Unhealthy"),

        /** Source is ready but not yet used */
        READY("Ready"),

        /** Source is unknown status */
        UNKNOWN("Unknown");

        private final String displayText;

        HealthStatus(String displayText) {
            this.displayText = displayText;
        }

        public String getDisplayText() {
            return displayText;
        }

        /**
         * Determine health from error rate
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
        return String.format("DataSourceInfo{id='%s', name='%s', enabled=%s, health=%s, " +
                        "requests=%d, errors=%d, successRate=%.1f%%}",
                id, name, enabled, health, totalRequests, errorCount, getSuccessRate() * 100);
    }
}