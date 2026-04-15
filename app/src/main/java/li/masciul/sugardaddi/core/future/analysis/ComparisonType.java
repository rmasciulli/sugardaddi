package li.masciul.sugardaddi.core.future.analysis;

/**
 * ComparisonType - Types of product vs category comparisons
 *
 * Defines different comparison methodologies for analyzing products
 * against their category statistics and peer products.
 *
 * STATUS: UNUSED - Reserved for CategoryComparison feature
 * See: core/future/analysis/README.md
 *
 * TODO: Integrate when CategoryComparison is enabled in UI
 */
public enum ComparisonType {

    /**
     * Nutritional comparison - comparing nutrition values to category averages
     */
    NUTRITIONAL("nutritional", "Nutritional Analysis", "Compare nutrition values to category averages"),

    /**
     * Quality comparison - comparing data quality and source reliability
     */
    QUALITY("quality", "Data Quality", "Compare data completeness and source reliability"),

    /**
     * Ranking comparison - percentile ranking within category
     */
    RANKING("ranking", "Category Ranking", "Percentile ranking compared to similar products"),

    /**
     * Health comparison - health-focused analysis (sodium, sugar, fiber, etc.)
     */
    HEALTH("health", "Health Analysis", "Focus on health-relevant nutrients and recommendations"),

    /**
     * Dietary comparison - specific dietary requirements (vegan, keto, etc.)
     */
    DIETARY("dietary", "Dietary Fit", "Analyze fit for specific dietary requirements"),

    /**
     * Environmental comparison - sustainability and environmental impact
     */
    ENVIRONMENTAL("environmental", "Environmental Impact", "Compare environmental footprint"),

    /**
     * Economic comparison - price and value analysis
     */
    ECONOMIC("economic", "Value Analysis", "Compare price and nutritional value"),

    /**
     * Comprehensive comparison - all aspects combined
     */
    COMPREHENSIVE("comprehensive", "Complete Analysis", "Comprehensive analysis across all dimensions");

    private final String id;
    private final String displayName;
    private final String description;

    ComparisonType(String id, String displayName, String description) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }

    /**
     * Get comparison type by ID
     */
    public static ComparisonType fromId(String id) {
        for (ComparisonType type : values()) {
            if (type.getId().equals(id)) {
                return type;
            }
        }
        return COMPREHENSIVE; // Default fallback
    }

    /**
     * Get comparison types suitable for UI selection
     */
    public static ComparisonType[] getMainTypes() {
        return new ComparisonType[]{
                NUTRITIONAL, HEALTH, RANKING, COMPREHENSIVE
        };
    }

    /**
     * Check if this comparison type focuses on health aspects
     */
    public boolean isHealthFocused() {
        return this == HEALTH || this == DIETARY || this == COMPREHENSIVE;
    }

    /**
     * Check if this comparison type requires detailed nutrition data
     */
    public boolean requiresDetailedNutrition() {
        return this == NUTRITIONAL || this == HEALTH || this == COMPREHENSIVE;
    }

    /**
     * Get recommended comparison type based on use case
     */
    public static ComparisonType getRecommendedType(String useCase) {
        switch (useCase.toLowerCase()) {
            case "diet":
            case "weight_loss":
            case "fitness":
                return HEALTH;
            case "nutrition_facts":
            case "detailed_analysis":
                return NUTRITIONAL;
            case "quick_comparison":
            case "shopping":
                return RANKING;
            case "research":
            case "analysis":
                return COMPREHENSIVE;
            default:
                return NUTRITIONAL;
        }
    }
}