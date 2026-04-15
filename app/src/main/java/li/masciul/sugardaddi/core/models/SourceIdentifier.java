package li.masciul.sugardaddi.core.models;

/**
 * SourceIdentifier - Identifies items across multiple data sources
 *
 * This class handles the complex task of identifying food items
 * that may come from different sources but represent the same product
 */
public class SourceIdentifier {

    private String sourceId;        // "OFF", "CIQUAL", "USER", etc.
    private String originalId;      // ID from the original source
    private String combinedId;      // "OFF:123456789" format

    // ========== CONSTRUCTORS ==========

    public SourceIdentifier() {}

    public SourceIdentifier(String sourceId, String originalId) {
        this.sourceId = sourceId;
        this.originalId = originalId;
        this.combinedId = generateCombinedId();
    }

    // ========== GETTERS/SETTERS ==========

    public String getSourceId() { return sourceId; }
    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
        this.combinedId = generateCombinedId();
    }

    public String getOriginalId() { return originalId; }
    public void setOriginalId(String originalId) {
        this.originalId = originalId;
        this.combinedId = generateCombinedId();
    }

    public String getCombinedId() { return combinedId; }

    // ========== UTILITY METHODS ==========

    private String generateCombinedId() {
        if (sourceId == null || originalId == null) {
            return null;
        }
        return sourceId + ":" + originalId;
    }

    /**
     * Parse combined ID back into components
     */
    public static SourceIdentifier fromCombinedId(String combinedId) {
        if (combinedId == null || !combinedId.contains(":")) {
            return null;
        }

        String[] parts = combinedId.split(":", 2);
        if (parts.length != 2) {
            return null;
        }

        return new SourceIdentifier(parts[0], parts[1]);
    }

    /**
     * Check if this identifier is valid
     */
    public boolean isValid() {
        return sourceId != null && !sourceId.trim().isEmpty() &&
                originalId != null && !originalId.trim().isEmpty();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        SourceIdentifier that = (SourceIdentifier) obj;
        return java.util.Objects.equals(combinedId, that.combinedId);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(combinedId);
    }

    @Override
    public String toString() {
        return combinedId != null ? combinedId : "Invalid SourceIdentifier";
    }
}