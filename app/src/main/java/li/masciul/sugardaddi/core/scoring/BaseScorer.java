package li.masciul.sugardaddi.core.scoring;

import li.masciul.sugardaddi.core.interfaces.Searchable;
import li.masciul.sugardaddi.core.interfaces.SourceSpecificScorer;
import li.masciul.sugardaddi.core.models.FoodProduct;
import li.masciul.sugardaddi.core.models.Recipe;
import li.masciul.sugardaddi.core.models.ScoredProduct;
import li.masciul.sugardaddi.core.utils.SearchFilter;

/**
 * BaseScorer - Abstract base class for source-specific scorers
 *
 * Provides common scoring logic that all scorers share, while allowing
 * each source to override and customize behavior for their specific strengths.
 *
 * DESIGN PATTERN: Template Method + Strategy
 * - Template Method: scoreProduct() orchestrates the scoring flow
 * - Strategy: Each source implements scoreSourceSpecificAttributes()
 *
 * ARCHITECTURE:
 * - Common logic (name, category, favorite) implemented here
 * - Source-specific logic (NutriScore, images, etc.) in subclasses
 * - Override capability for all methods (maximum flexibility)
 *
 * SCORING FLOW:
 * 1. scoreProduct() called by SearchFilter
 * 2. scoreNameMatch() - can be overridden
 * 3. scoreCategoryMatch() - can be overridden
 * 4. scoreSourceSpecificAttributes() - MUST be implemented
 * 5. applyFavoriteBonus() - common implementation
 * 6. Return ScoredProduct with breakdown
 *
 * OVERRIDE GUIDELINES:
 * - Override to ADD source-specific logic (e.g., brand matching for OFF)
 * - Call super.method() to keep base behavior, then add more
 * - Or completely replace if source needs different weighting
 *
 * EXAMPLE USAGE:
 * ```java
 * public class OpenFoodFactsScorer extends BaseScorer<FoodProduct> {
 *     @Override
 *     protected int scoreNameMatch(...) {
 *         // Call base implementation
 *         int score = super.scoreNameMatch(item, query, lang, breakdown);
 *
 *         // Add brand matching (OFF-specific)
 *         score += scoreBrandMatch(item, query, lang, breakdown);
 *
 *         return score;
 *     }
 *
 *     @Override
 *     protected int scoreSourceSpecificAttributes(...) {
 *         // NutriScore, EcoScore, images, etc.
 *         int score = 0;
 *         if (item.getNutriScore() != null) score += 10;
 *         return score;
 *     }
 * }
 * ```
 *
 * @param <T> Type of searchable item (FoodProduct or Recipe)
 * @version 1.0
 * @since Search Diversity Refactor
 */
public abstract class BaseScorer<T extends Searchable> implements SourceSpecificScorer<T> {

    // ========== MAIN SCORING METHOD ==========

    /**
     * Score an item based on search query
     *
     * This is the template method that orchestrates the scoring flow.
     * Subclasses typically don't override this, but can if needed.
     *
     * @param item Item to score (FoodProduct or Recipe)
     * @param normalizedQuery Search query (already normalized by ProductRepository)
     * @param language User's language
     * @return ScoredProduct with total score and breakdown
     */
    @Override
    public ScoredProduct scoreProduct(T item, String normalizedQuery, String language) {
        int totalScore = 0;
        StringBuilder breakdown = new StringBuilder();

        // 1. Name matching (can be overridden for source-specific behavior)
        totalScore += scoreNameMatch(item, normalizedQuery, language, breakdown);

        // 2. Category matching (can be overridden)
        totalScore += scoreCategoryMatch(item, normalizedQuery, language, breakdown);

        // 3. Source-specific attributes (MUST be implemented by subclass)
        totalScore += scoreSourceSpecificAttributes(item, normalizedQuery, language, breakdown);

        // 4. Favorite bonus (common across all sources)
        totalScore += applyFavoriteBonus(item, breakdown);

        // 5. Create and return scored product
        return new ScoredProduct(item, totalScore, breakdown.toString().trim(), getDataSource());
    }

    // ========== COMMON SCORING METHODS (CAN BE OVERRIDDEN) ==========

    /**
     * Score name matching
     *
     * Default implementation uses standard name matching logic.
     * Subclasses can override to add brand matching, generic name, etc.
     *
     * @param item Item to score
     * @param normalizedQuery Normalized query
     * @param language Language
     * @param breakdown Score breakdown builder
     * @return Score for name matching (0-40)
     */
    protected int scoreNameMatch(T item, String normalizedQuery, String language, StringBuilder breakdown) {
        // Get and normalize the item's display name
        String displayName = item.getDisplayName(language);
        if (displayName == null) {
            return 0;
        }

        String normalizedName = SearchFilter.normalizeSearchTerm(displayName);

        // Calculate name match score using utility
        int score = ScoringUtils.calculateNameMatchScore(normalizedName, normalizedQuery);

        if (score > 0) {
            String matchType = getMatchType(score);
            breakdown.append(matchType).append(":").append(score).append(" ");
        }

        return score;
    }

    /**
     * Score category matching
     *
     * Default implementation checks if categories contain the query.
     * Subclasses can override for more sophisticated category matching.
     *
     * @param item Item to score
     * @param normalizedQuery Normalized query
     * @param language Language
     * @param breakdown Score breakdown builder
     * @return Score for category matching (0-20)
     */
    protected int scoreCategoryMatch(T item, String normalizedQuery, String language, StringBuilder breakdown) {
        int score = 0;

        // Get categories text (works for both FoodProduct and Recipe via Searchable)
        String categories = getCategoriesText(item, language);
        if (categories != null) {
            String normalizedCategories = SearchFilter.normalizeSearchTerm(categories);
            int categoryScore = ScoringUtils.calculateCategoryMatchScore(normalizedCategories, normalizedQuery);

            if (categoryScore > 0) {
                breakdown.append("category:").append(categoryScore).append(" ");
                score += categoryScore;
            }
        }

        // Primary category (if available)
        String primaryCategory = getPrimaryCategory(item, language);
        if (primaryCategory != null) {
            String normalizedPrimary = SearchFilter.normalizeSearchTerm(primaryCategory);
            int primaryScore = ScoringUtils.calculatePrimaryCategoryMatchScore(normalizedPrimary, normalizedQuery);

            if (primaryScore > 0) {
                breakdown.append("primary_category:").append(primaryScore).append(" ");
                score += primaryScore;
            }
        }

        return score;
    }

    /**
     * Apply favorite bonus
     *
     * Consistent +15 points across all sources if item is favorited.
     * Subclasses typically don't override this.
     *
     * @param item Item to check
     * @param breakdown Score breakdown builder
     * @return 15 if favorite, 0 otherwise
     */
    protected int applyFavoriteBonus(T item, StringBuilder breakdown) {
        boolean isFavorite = isFavorited(item);
        int bonus = ScoringUtils.calculateFavoriteBonus(isFavorite);

        if (bonus > 0) {
            breakdown.append("favorite:").append(bonus).append(" ");
        }

        return bonus;
    }

    // ========== ABSTRACT METHOD (MUST BE IMPLEMENTED) ==========

    /**
     * Score source-specific attributes
     *
     * Each source implements this to score attributes specific to their data.
     *
     * Examples:
     * - OpenFoodFacts: NutriScore, EcoScore, images, completeness
     * - Ciqual: Scientific names, nutrition completeness, portion info
     * - Recipes: Ingredient matches, meal type, difficulty, steps
     *
     * @param item Item to score
     * @param normalizedQuery Normalized query
     * @param language Language
     * @param breakdown Score breakdown builder
     * @return Score for source-specific attributes
     */
    protected abstract int scoreSourceSpecificAttributes(T item, String normalizedQuery, String language, StringBuilder breakdown);

    // ========== HELPER METHODS ==========

    /**
     * Get match type label based on score
     */
    private String getMatchType(int score) {
        if (score >= 40) return "exact_name";
        if (score >= 25) return "starts_with";
        if (score >= 20) return "contains";
        return "partial_match";
    }

    /**
     * Get categories text from item (handles both FoodProduct and Recipe)
     */
    private String getCategoriesText(T item, String language) {
        if (item instanceof FoodProduct) {
            return ((FoodProduct) item).getCategoriesText(language);
        } else if (item instanceof Recipe) {
            return ((Recipe) item).getCategoriesText(language);
        }
        return null;
    }

    /**
     * Get primary category from item (handles both FoodProduct and Recipe)
     */
    private String getPrimaryCategory(T item, String language) {
        if (item instanceof FoodProduct) {
            return ((FoodProduct) item).getPrimaryCategory(language);
        } else if (item instanceof Recipe) {
            return ((Recipe) item).getPrimaryCategory(language);
        }
        return null;
    }

    /**
     * Check if item is favorited (handles both FoodProduct and Recipe)
     */
    private boolean isFavorited(T item) {
        if (item instanceof FoodProduct) {
            return ((FoodProduct) item).isFavorite();
        } else if (item instanceof Recipe) {
            return ((Recipe) item).isFavorite();
        }
        return false;
    }
}