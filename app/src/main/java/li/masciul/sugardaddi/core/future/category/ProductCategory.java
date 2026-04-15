package li.masciul.sugardaddi.core.future.category;

import java.util.*;

/**
 * ProductCategory - Dual representation category model
 *
 * STATUS: WORK IN PROGRESS - Part of taxonomy system
 * See: core/future/category/README.md for details
 *
 * CURRENT USAGE:
 * - OpenFoodFactsDataSource.categorizeProduct() returns this
 * - CategoryStats uses this as a field
 * - NOT yet fully integrated with CategoryMatcher
 *
 * TODO:
 * - Complete mapping between source categories and unified taxonomy
 * - Integrate with CategoryMatcher confidence scoring
 * - Implement LanguaL multifaceted categorization
 *
 * This class represents a product's category using a dual approach:
 * 1. SOURCE-NATIVE: Preserves original source category data
 * 2. UNIFIED: Maps to analytics taxonomy for cross-source comparisons
 */
public class ProductCategory {

    // ========== SOURCE-NATIVE DATA (Display & Accuracy) ==========
    private String sourceCategory;          // Original category from source: "01.1", "en:milk"
    private String sourceName;              // Native name: "Lait et boissons lactées"
    private String sourceId;                // Data source: "ciqual", "openfoodfacts", "logmeal"
    private String sourceLanguage;          // Language of source name: "fr", "en"
    private int sourceConfidence;           // Source confidence: 95 (Ciqual), 80 (OFF), 85 (LogMeal)
    private Map<String, String> sourceNames; // Multi-language source names

    // ========== UNIFIED TAXONOMY (Analytics & Intelligence) ==========
    private String unifiedId;              // Analytics ID: "dairy.milk"
    private String unifiedName;            // Unified name: "Milk and Milk Beverages"
    private List<String> hierarchyPath;    // Full path: ["food", "dairy", "dairy.milk"]
    private int taxonomyLevel;             // Depth in taxonomy: 3
    private String parentCategory;         // Parent for navigation: "dairy"
    private List<String> childCategories;  // Children for drill-down

    // ========== ANALYTICS METADATA ==========
    private int productCount;              // Products in this category (for stats)
    private boolean hasLocalStats;         // True if we compute stats locally (Ciqual)
    private boolean hasApiStats;           // True if source provides API stats (OFF)
    private long statsLastUpdated;         // When stats were last computed/fetched
    private long statsValidUntil;          // Cache expiry for API-sourced stats

    // ========== CREATION METADATA ==========
    private long createdAt;
    private long lastUpdated;
    private String processingMethod;        // "precomputed", "api_mapped", "ai_generated"

    // ========== CONSTRUCTORS ==========

    public ProductCategory() {
        this.createdAt = System.currentTimeMillis();
        this.lastUpdated = this.createdAt;
        this.sourceNames = new HashMap<>();
        this.hierarchyPath = new ArrayList<>();
        this.childCategories = new ArrayList<>();
    }

    /**
     * Create category with source-native data
     */
    public ProductCategory(String sourceCategory, String sourceName, String sourceId) {
        this();
        this.sourceCategory = sourceCategory;
        this.sourceName = sourceName;
        this.sourceId = sourceId;
        this.sourceConfidence = getDefaultConfidence(sourceId);
    }

    /**
     * Create full category with both source and unified data
     */
    public ProductCategory(String sourceCategory, String sourceName, String sourceId,
                           String unifiedId, String unifiedName) {
        this(sourceCategory, sourceName, sourceId);
        this.unifiedId = unifiedId;
        this.unifiedName = unifiedName;
    }

    // ========== DISPLAY METHODS (Use Source-Native Data) ==========

    /**
     * Get display name for UI (uses source-native data for accuracy)
     */
    public String getDisplayName(String language) {
        // Try requested language first
        if (sourceNames.containsKey(language)) {
            return sourceNames.get(language);
        }

        // Fallback to source language
        if (sourceName != null && sourceLanguage != null && sourceLanguage.equals(language)) {
            return sourceName;
        }

        // Fallback to primary source name
        if (sourceName != null) {
            return sourceName;
        }

        // Last resort: use unified name
        return unifiedName != null ? unifiedName : sourceCategory;
    }

    /**
     * Get formatted category path for display
     */
    public String getDisplayPath(String language, String separator) {
        if (hierarchyPath == null || hierarchyPath.isEmpty()) {
            return getDisplayName(language);
        }

        // Build human-readable path using display names
        List<String> displayPath = new ArrayList<>();
        for (String pathId : hierarchyPath) {
            // For now, use simplified display - can be enhanced with path name resolution
            displayPath.add(pathId.replace(".", " → "));
        }

        return String.join(separator, displayPath);
    }

    /**
     * Get source-specific confidence level
     */
    public String getConfidenceDescription() {
        if (sourceConfidence >= 90) return "Very High";
        if (sourceConfidence >= 80) return "High";
        if (sourceConfidence >= 70) return "Medium";
        if (sourceConfidence >= 60) return "Low";
        return "Very Low";
    }

    // ========== ANALYTICS METHODS (Use Unified Data) ==========

    /**
     * Get analytics category ID for stats/comparisons
     */
    public String getAnalyticsId() {
        return unifiedId;
    }

    /**
     * Get parent category for suggestions
     */
    public String getParentForSuggestions() {
        return parentCategory;
    }

    /**
     * Get hierarchy for navigation/breadcrumbs
     */
    public List<String> getHierarchyForNavigation() {
        return new ArrayList<>(hierarchyPath);
    }

    /**
     * Check if this category supports local analytics (Ciqual)
     */
    public boolean supportsLocalAnalytics() {
        return hasLocalStats && "ciqual".equals(sourceId);
    }

    /**
     * Check if this category supports API analytics (OFF)
     */
    public boolean supportsApiAnalytics() {
        return hasApiStats && ("openfoodfacts".equals(sourceId) || "logmeal".equals(sourceId));
    }

    /**
     * Check if analytics data is fresh/valid
     */
    public boolean isAnalyticsDataFresh() {
        if (supportsLocalAnalytics()) {
            return true; // Local data doesn't expire
        }

        if (supportsApiAnalytics()) {
            return System.currentTimeMillis() < statsValidUntil;
        }

        return false;
    }

    // ========== VALIDATION & UTILITY ==========

    /**
     * Check if category has valid source data
     */
    public boolean hasValidSourceData() {
        return sourceCategory != null &&
                sourceName != null &&
                sourceId != null &&
                sourceConfidence > 0;
    }

    /**
     * Check if category has unified mapping
     */
    public boolean hasUnifiedMapping() {
        return unifiedId != null && unifiedName != null;
    }

    /**
     * Check if category is complete (has both source and unified data)
     */
    public boolean isComplete() {
        return hasValidSourceData() && hasUnifiedMapping();
    }

    /**
     * Update timestamp when category data changes
     */
    public void touch() {
        this.lastUpdated = System.currentTimeMillis();
    }

    /**
     * Get default confidence based on source reliability
     */
    private static int getDefaultConfidence(String sourceId) {
        switch (sourceId) {
            case "ciqual": return 95;        // Official French database
            case "openfoodfacts": return 80; // Community with EU focus
            case "logmeal": return 85;       // AI-powered
            default: return 60;              // Unknown source
        }
    }

    // ========== GETTERS AND SETTERS ==========

    // Source-native data
    public String getSourceCategory() { return sourceCategory; }
    public void setSourceCategory(String sourceCategory) {
        this.sourceCategory = sourceCategory;
        touch();
    }

    public String getSourceName() { return sourceName; }
    public void setSourceName(String sourceName) {
        this.sourceName = sourceName;
        touch();
    }

    public String getSourceId() { return sourceId; }
    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
        touch();
    }

    public String getSourceLanguage() { return sourceLanguage; }
    public void setSourceLanguage(String sourceLanguage) {
        this.sourceLanguage = sourceLanguage;
        touch();
    }

    public int getSourceConfidence() { return sourceConfidence; }
    public void setSourceConfidence(int sourceConfidence) {
        this.sourceConfidence = Math.max(0, Math.min(100, sourceConfidence));
        touch();
    }

    public Map<String, String> getSourceNames() { return new HashMap<>(sourceNames); }
    public void setSourceNames(Map<String, String> sourceNames) {
        this.sourceNames = new HashMap<>(sourceNames);
        touch();
    }

    public void addSourceName(String language, String name) {
        sourceNames.put(language, name);
        touch();
    }

    // Unified taxonomy data
    public String getUnifiedId() { return unifiedId; }
    public void setUnifiedId(String unifiedId) {
        this.unifiedId = unifiedId;
        touch();
    }

    public String getUnifiedName() { return unifiedName; }
    public void setUnifiedName(String unifiedName) {
        this.unifiedName = unifiedName;
        touch();
    }

    public List<String> getHierarchyPath() { return new ArrayList<>(hierarchyPath); }
    public void setHierarchyPath(List<String> hierarchyPath) {
        this.hierarchyPath = new ArrayList<>(hierarchyPath);
        touch();
    }

    public int getTaxonomyLevel() { return taxonomyLevel; }
    public void setTaxonomyLevel(int taxonomyLevel) {
        this.taxonomyLevel = taxonomyLevel;
        touch();
    }

    public String getParentCategory() { return parentCategory; }
    public void setParentCategory(String parentCategory) {
        this.parentCategory = parentCategory;
        touch();
    }

    public List<String> getChildCategories() { return new ArrayList<>(childCategories); }
    public void setChildCategories(List<String> childCategories) {
        this.childCategories = new ArrayList<>(childCategories);
        touch();
    }

    // Analytics metadata
    public int getProductCount() { return productCount; }
    public void setProductCount(int productCount) {
        this.productCount = productCount;
        touch();
    }

    public boolean hasLocalStats() { return hasLocalStats; }
    public void setHasLocalStats(boolean hasLocalStats) {
        this.hasLocalStats = hasLocalStats;
        touch();
    }

    public boolean hasApiStats() { return hasApiStats; }
    public void setHasApiStats(boolean hasApiStats) {
        this.hasApiStats = hasApiStats;
        touch();
    }

    public long getStatsLastUpdated() { return statsLastUpdated; }
    public void setStatsLastUpdated(long statsLastUpdated) {
        this.statsLastUpdated = statsLastUpdated;
        touch();
    }

    public long getStatsValidUntil() { return statsValidUntil; }
    public void setStatsValidUntil(long statsValidUntil) {
        this.statsValidUntil = statsValidUntil;
        touch();
    }

    // Creation metadata
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(long lastUpdated) { this.lastUpdated = lastUpdated; }

    public String getProcessingMethod() { return processingMethod; }
    public void setProcessingMethod(String processingMethod) {
        this.processingMethod = processingMethod;
        touch();
    }

    // ========== OBJECT METHODS ==========

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        ProductCategory that = (ProductCategory) obj;
        return Objects.equals(sourceCategory, that.sourceCategory) &&
                Objects.equals(sourceId, that.sourceId) &&
                Objects.equals(unifiedId, that.unifiedId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourceCategory, sourceId, unifiedId);
    }

    @Override
    public String toString() {
        return String.format("ProductCategory{source=%s:%s, unified=%s, confidence=%d%%}",
                sourceId, sourceCategory, unifiedId, sourceConfidence);
    }

    /**
     * Get detailed debug information
     */
    public String getDebugInfo() {
        return String.format(
                "ProductCategory Debug:\n" +
                        "  Source: %s (%s) [%s]\n" +
                        "  Unified: %s (%s)\n" +
                        "  Hierarchy: %s\n" +
                        "  Confidence: %d%% (%s)\n" +
                        "  Analytics: Local=%b, API=%b, Fresh=%b\n" +
                        "  Created: %s, Updated: %s",
                sourceCategory, sourceName, sourceId,
                unifiedId, unifiedName,
                hierarchyPath,
                sourceConfidence, getConfidenceDescription(),
                hasLocalStats, hasApiStats, isAnalyticsDataFresh(),
                new Date(createdAt), new Date(lastUpdated)
        );
    }
}