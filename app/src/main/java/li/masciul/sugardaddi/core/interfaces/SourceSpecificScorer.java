package li.masciul.sugardaddi.core.interfaces;

import li.masciul.sugardaddi.core.enums.DataSource;
import li.masciul.sugardaddi.core.models.ScoredProduct;

/**
 * SourceSpecificScorer - Interface for source-specific scoring strategies
 *
 * PROBLEM THIS SOLVES:
 * Different data sources (OpenFoodFacts, Ciqual, User Recipes) have different
 * strengths and provide different types of data. A single universal scoring
 * algorithm unfairly penalizes sources for missing attributes they don't provide.
 *
 * For example:
 * - OpenFoodFacts provides: NutriScore, EcoScore, images, brands
 * - Ciqual provides: Scientific nutrition data, portion information
 * - Recipes provide: Ingredients, cooking steps, difficulty ratings
 *
 * Universal scoring favored OFF and filtered out Ciqual products entirely!
 *
 * SOLUTION:
 * Each data source gets its own scorer that values what IT provides:
 * - OpenFoodFactsScorer: Values NutriScore, images, completeness
 * - CiqualScorer: Values scientific names, nutrition completeness
 * - RecipeScorer: Values ingredient matches, meal type, difficulty
 *
 * ARCHITECTURE:
 * This is a Strategy Pattern implementation where:
 * - Context: SearchFilter
 * - Strategy: SourceSpecificScorer (this interface)
 * - Concrete Strategies: OpenFoodFactsScorer, CiqualScorer, RecipeScorer
 *
 * Uses generics to support both FoodProduct and Recipe while maintaining type safety.
 * Follows the same pattern as SearchManager which returns List<Searchable>.
 *
 * DESIGN PRINCIPLES:
 * 1. Fair scoring - Each source scored by its own strengths
 * 2. Normalized output - All scorers produce comparable scores
 * 3. Transparency - scoreBreakdown explains the score
 * 4. Extensibility - Easy to add new sources
 * 5. Type safety - Generics ensure correct item types
 *
 * USAGE:
 * ```java
 * SourceSpecificScorer<FoodProduct> scorer = new OpenFoodFactsScorer();
 * ScoredProduct scored = scorer.scoreProduct(product, "chocolate", "en");
 * System.out.println("Score: " + scored.getScore());
 * System.out.println("Why: " + scored.getScoreBreakdown());
 * ```
 *
 * SCORING RANGES (current design):
 * - Ciqual: 0-100 points
 * - OpenFoodFacts: 0-120 points (more data available)
 * - Recipes: 0-100 points
 * - All sources: +15 bonus for favorites
 *
 * FUTURE ENHANCEMENTS:
 * - Could add batch scoring: scoreProducts(List<T>)
 * - Could add score caching for performance
 * - Could add machine learning weights
 * - Could add user preference adjustments
 *
 * @param <T> Type of searchable item (FoodProduct or Recipe)
 * @version 1.0
 * @since Search Diversity Refactor
 */
public interface SourceSpecificScorer<T extends Searchable> {

    /**
     * Score a product based on source-specific criteria
     *
     * Each scorer implementation evaluates the item against the search query
     * using criteria relevant to its data source. The score reflects how well
     * the item matches the query, with higher scores indicating better matches.
     *
     * IMPLEMENTATION NOTES:
     * - Use SearchFilter.normalizeSearchTerm() for text normalization
     * - Break down score into components (name match, category match, bonuses)
     * - Include favorites bonus (+15 points) if item is favorited
     * - Return detailed breakdown string for debugging/transparency
     * - Handle null/empty fields gracefully
     *
     * SCORE COMPONENTS (typical pattern):
     * - Name/Brand matching: 25-50 points (exact/partial/starts-with)
     * - Category matching: 15-20 points
     * - Source-specific bonuses: 10-30 points (varies by source)
     * - Quality indicators: 5-20 points (completeness, images, etc.)
     * - Favorite bonus: +15 points (if favorited)
     *
     * BREAKDOWN FORMAT:
     * Space-separated "component:points" pairs, e.g.:
     * "exact_product:40 category:20 has_nutrition:15 favorite:15"
     *
     * @param item Item to score (FoodProduct or Recipe)
     * @param normalizedQuery Search query (already normalized by ProductRepository)
     * @param language User's current language (for name/category matching)
     * @return ScoredProduct containing item, score, breakdown, and source
     */
    ScoredProduct scoreProduct(T item, String normalizedQuery, String language);

    /**
     * Get the data source this scorer handles
     *
     * Used by SearchFilter to route products to the correct scorer.
     * Each scorer should return a specific DataSource enum value.
     *
     * @return The data source this scorer is designed for
     */
    DataSource getDataSource();

    /**
     * Get maximum possible score for this source
     *
     * Used for:
     * - Normalizing scores across sources (if needed)
     * - Determining quality thresholds
     * - Analytics and debugging
     *
     * CURRENT MAXIMUMS:
     * - OpenFoodFactsScorer: 120 points
     * - CiqualScorer: 100 points
     * - RecipeScorer: 100 points
     *
     * NOTE: These are maximums BEFORE the +15 favorite bonus.
     * Actual maximum with favorite = getMaxScore() + 15
     *
     * @return Maximum possible score (excluding favorite bonus)
     */
    int getMaxScore();

    /**
     * Get minimum score threshold for this source
     *
     * Products scoring below this threshold are filtered out as low-quality
     * or irrelevant results. Helps remove noise from search results.
     *
     * RECOMMENDED THRESHOLDS:
     * - High-quality sources (Ciqual, OFF): 15-20 points
     * - User-generated (Recipes): 10-15 points
     *
     * @return Minimum score to include in results (default: 15)
     */
    default int getMinimumScore() {
        return 15;
    }

    /**
     * Check if this scorer can handle the given item
     *
     * Validates that the item is from the correct data source.
     * Used for defensive programming and debugging.
     *
     * @param item Item to check (FoodProduct or Recipe)
     * @return true if this scorer can score the item
     */
    default boolean canScore(T item) {
        if (item == null) return false;
        return item.getDataSource() == getDataSource();
    }

    /**
     * Get a human-readable name for this scorer
     *
     * Used for logging and debugging output.
     * Each implementation MUST override this method.
     *
     * @return Scorer name (e.g., "OpenFoodFacts Scorer", "Ciqual Scorer")
     */
    String getScorerName();
}