package li.masciul.sugardaddi.core.models;

import li.masciul.sugardaddi.core.enums.DataSource;
import li.masciul.sugardaddi.core.interfaces.Searchable;

/**
 * ScoredProduct - Container for a searchable item with its calculated relevance score
 *
 * This class wraps a Searchable item (FoodProduct or Recipe) with its search relevance score,
 * score breakdown explanation, and source information. Used by the search
 * filtering and ranking system to sort results by relevance.
 *
 * ARCHITECTURE:
 * - Immutable design (all fields final) for thread safety
 * - Contains the scored item and metadata about the score
 * - Used by SourceSpecificScorer implementations
 * - Consumed by SearchFilter and DiversityStrategy
 * - Supports both FoodProduct and Recipe via Searchable interface
 *
 * SCORE BREAKDOWN FORMAT:
 * Human-readable string explaining how score was calculated, e.g.:
 * "exact_product:40 brand_in_query:15 has_image:5 favorite:15"
 *
 * Useful for:
 * - Debugging search relevance issues
 * - Explaining to users why something ranked high/low
 * - Analytics and optimization
 *
 * FUTURE EXTENSIONS:
 * - Could add normalized score (0-100) for cross-source comparison
 * - Could add quality category (excellent/good/poor)
 * - Could add timestamp for freshness tracking
 * - Could implement Comparable for easier sorting
 *
 * @version 1.0
 * @since Search Diversity Refactor
 */
public class ScoredProduct {

    // ========== FIELDS ==========

    /**
     * The searchable item being scored
     * Can be a FoodProduct or Recipe (both implement Searchable)
     */
    private final Searchable item;

    /**
     * Calculated relevance score
     * Higher = more relevant to the search query
     * Range depends on scorer (typically 0-100 or 0-120)
     */
    private final int score;

    /**
     * Human-readable explanation of how score was calculated
     * Format: "component1:points1 component2:points2 ..."
     * Example: "exact_product:40 has_nutrition:15 favorite:15"
     */
    private final String scoreBreakdown;

    /**
     * Data source this item came from
     * Used for diversity strategy and debugging
     */
    private final DataSource source;

    // ========== CONSTRUCTOR ==========

    /**
     * Create a scored product
     *
     * @param item The searchable item (FoodProduct or Recipe)
     * @param score The relevance score
     * @param scoreBreakdown Human-readable score explanation
     * @param source Data source the item came from
     */
    public ScoredProduct(Searchable item, int score, String scoreBreakdown, DataSource source) {
        this.item = item;
        this.score = score;
        this.scoreBreakdown = scoreBreakdown;
        this.source = source;
    }

    /**
     * Create a scored product (without source tracking)
     * Source is inferred from item's dataSource field
     *
     * @param item The searchable item (FoodProduct or Recipe)
     * @param score The relevance score
     * @param scoreBreakdown Human-readable score explanation
     */
    public ScoredProduct(Searchable item, int score, String scoreBreakdown) {
        this(item, score, scoreBreakdown, item.getDataSource());
    }

    // ========== GETTERS ==========

    /**
     * Get the scored item as Searchable
     * Caller should cast to FoodProduct or Recipe as needed
     */
    public Searchable getItem() {
        return item;
    }

    /**
     * Get the scored item as FoodProduct (convenience method)
     * Throws ClassCastException if item is not a FoodProduct
     */
    public FoodProduct getProduct() {
        return (FoodProduct) item;
    }

    /**
     * Get the scored item as Recipe (convenience method)
     * Throws ClassCastException if item is not a Recipe
     */
    public Recipe getRecipe() {
        return (Recipe) item;
    }

    public int getScore() {
        return score;
    }

    public String getScoreBreakdown() {
        return scoreBreakdown;
    }

    public DataSource getSource() {
        return source;
    }

    // ========== UTILITY METHODS ==========

    /**
     * Get normalized score (0-100 range)
     * Useful for cross-source comparison when sources have different maximums
     *
     * @param maxScore Maximum possible score for this scorer
     * @return Score normalized to 0-100 range
     */
    public int getNormalizedScore(int maxScore) {
        if (maxScore <= 0) return 0;
        return Math.min(100, (score * 100) / maxScore);
    }

    /**
     * Check if this is a high-quality result
     *
     * @param threshold Minimum score to be considered high quality
     * @return true if score meets or exceeds threshold
     */
    public boolean isHighQuality(int threshold) {
        return score >= threshold;
    }

    /**
     * Get quality category based on score
     *
     * @param maxScore Maximum possible score for this scorer
     * @return Quality category string
     */
    public String getQualityCategory(int maxScore) {
        int normalized = getNormalizedScore(maxScore);
        if (normalized >= 80) return "Excellent";
        if (normalized >= 60) return "Good";
        if (normalized >= 40) return "Fair";
        return "Poor";
    }

    // ========== COMPARISON ==========

    /**
     * Compare scores for sorting (higher scores first)
     * Can be used with Collections.sort() via a comparator
     *
     * @param other Another scored product
     * @return Negative if this < other, 0 if equal, positive if this > other
     */
    public int compareScoreTo(ScoredProduct other) {
        // Descending order (higher scores first)
        return Integer.compare(other.score, this.score);
    }

    // ========== OBJECT OVERRIDES ==========

    @Override
    public String toString() {
        // Simple toString for debugging - use source ID instead of localized name
        return String.format("ScoredProduct{item='%s', score=%d, source=%s}",
                item.getDisplayName(FoodProduct.DEFAULT_LANGUAGE), score,
                source != null ? source.getId() : "unknown");
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        ScoredProduct other = (ScoredProduct) obj;

        // Two scored products are equal if they wrap the same item
        // (score may vary with different queries)
        return item != null && item.equals(other.item);
    }

    @Override
    public int hashCode() {
        return item != null ? item.hashCode() : 0;
    }
}