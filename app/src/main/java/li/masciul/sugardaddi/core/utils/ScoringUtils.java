package li.masciul.sugardaddi.core.utils;

import li.masciul.sugardaddi.data.network.ApiConfig;

/**
 * ScoringUtils - Pure GENERIC scoring calculation utilities
 *
 * Provides reusable scoring logic that applies to ALL sources.
 * These are stateless helper methods that perform common scoring calculations.
 *
 * ARCHITECTURE v3.0 - GENERIC ONLY:
 * This class contains ONLY truly generic scoring methods used across all sources.
 * Source-specific scoring logic belongs in the respective scorer classes:
 * - OpenFoodFacts-specific â†' OpenFoodFactsScorer.java
 * - Ciqual-specific â†' CiqualScorer.java
 * - Recipe-specific â†' RecipeScorer.java
 *
 * WHAT BELONGS HERE:
 * - Name matching (used by all sources)
 * - Category matching (used by all sources)
 * - Favorite bonus (consistent across all sources)
 * - Generic boolean attribute scoring
 * - Text matching utilities
 * - Score manipulation utilities
 *
 * WHAT DOESN'T BELONG HERE:
 * - Brand matching (OpenFoodFacts only)
 * - Completeness scoring (OpenFoodFacts specific metric)
 * - Ingredient matching (Recipe only)
 * - Generic name matching (Ciqual only)
 * - Any source-specific logic
 *
 * IMPORTANT - SEPARATION OF CONCERNS:
 * - This class does NOT normalize text (use SearchFilter.normalizeSearchTerm())
 * - This class does NOT fetch data (handled by repositories)
 * - This class ONLY calculates scores based on already-normalized inputs
 *
 * USAGE PATTERN:
 * ```java
 * // In a scorer implementation
 * String normalizedName = SearchFilter.normalizeSearchTerm(product.getName(lang));
 * String normalizedQuery = SearchFilter.normalizeSearchTerm(query);
 * int nameScore = ScoringUtils.calculateNameMatchScore(normalizedName, normalizedQuery);
 * ```
 *
 * PERFORMANCE NOTES:
 * - All methods are static (no instance creation overhead)
 * - No allocations in hot paths
 * - Simple string operations only (no regex, no reflection)
 * - Android-optimized for speed
 *
 * @version 3.0 (Generic Only - Source-Specific Removed)
 * @since Search Diversity Refactor
 */
public final class ScoringUtils {

    // ========== CONSTRUCTOR ==========

    /**
     * Private constructor - utility class should not be instantiated
     */
    private ScoringUtils() {
        throw new UnsupportedOperationException("ScoringUtils is a utility class");
    }

    // ========== NAME MATCHING SCORES (GENERIC) ==========

    /**
     * Calculate score for name matching
     *
     * GENERIC: Used by all sources (OpenFoodFacts, Ciqual, Recipes)
     *
     * PRECONDITION: Both name and query must already be normalized
     * (use SearchFilter.normalizeSearchTerm() before calling)
     *
     * SCORING (from ApiConfig.Scoring):
     * - Exact match: EXACT_NAME_MATCH points (40)
     * - Starts with query: NAME_STARTS_WITH points (25)
     * - Contains query: NAME_CONTAINS points (20)
     * - No match: 0 points
     *
     * @param normalizedName Normalized product/recipe name
     * @param normalizedQuery Normalized search query
     * @return Score (0-40)
     */
    public static int calculateNameMatchScore(String normalizedName, String normalizedQuery) {
        if (normalizedName == null || normalizedQuery == null) {
            return 0;
        }

        if (normalizedName.equals(normalizedQuery)) {
            return ApiConfig.Scoring.EXACT_NAME_MATCH;
        } else if (normalizedName.startsWith(normalizedQuery)) {
            return ApiConfig.Scoring.NAME_STARTS_WITH;
        } else if (normalizedName.contains(normalizedQuery)) {
            return ApiConfig.Scoring.NAME_CONTAINS;
        }

        return 0; // No match
    }

    // ========== CATEGORY MATCHING SCORES (GENERIC) ==========

    /**
     * Calculate score for category matching
     *
     * GENERIC: Used by all sources
     *
     * PRECONDITION: Both categories and query must already be normalized
     *
     * SCORING (from ApiConfig.Scoring):
     * - Category contains query: CATEGORY_MATCH points (20)
     * - No match: 0 points
     *
     * @param normalizedCategories Normalized category text
     * @param normalizedQuery Normalized search query
     * @return Score (0-20)
     */
    public static int calculateCategoryMatchScore(String normalizedCategories, String normalizedQuery) {
        if (normalizedCategories == null || normalizedQuery == null) {
            return 0;
        }

        return normalizedCategories.contains(normalizedQuery) ? ApiConfig.Scoring.CATEGORY_MATCH : 0;
    }

    /**
     * Calculate score for primary category matching
     *
     * GENERIC: Used by all sources
     *
     * Primary category is more important than general categories,
     * so it gets its own scoring method.
     *
     * PRECONDITION: Both category and query must already be normalized
     *
     * SCORING (from ApiConfig.Scoring):
     * - Primary category contains query: PRIMARY_CATEGORY_MATCH points (15)
     * - No match: 0 points
     *
     * @param normalizedPrimaryCategory Normalized primary category
     * @param normalizedQuery Normalized search query
     * @return Score (0-15)
     */
    public static int calculatePrimaryCategoryMatchScore(String normalizedPrimaryCategory, String normalizedQuery) {
        if (normalizedPrimaryCategory == null || normalizedQuery == null) {
            return 0;
        }

        return normalizedPrimaryCategory.contains(normalizedQuery) ? ApiConfig.Scoring.PRIMARY_CATEGORY_MATCH : 0;
    }

    // ========== WORD MATCHING (GENERIC) ==========

    /**
     * Check if all words from query appear in text (order doesn't matter)
     *
     * GENERIC: Useful text matching utility
     *
     * PRECONDITION: Both text and query must already be normalized
     *
     * Example:
     * - text: "dark chocolate bar"
     * - query: "chocolate dark"
     * - result: true (all words present)
     *
     * @param normalizedText Normalized text to search in
     * @param normalizedQuery Normalized query
     * @return true if all query words appear in text
     */
    public static boolean allWordsMatch(String normalizedText, String normalizedQuery) {
        if (normalizedText == null || normalizedQuery == null) {
            return false;
        }

        String[] queryWords = normalizedQuery.split("\\s+");
        for (String word : queryWords) {
            if (!normalizedText.contains(word)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Calculate score for all-words matching
     *
     * GENERIC: Can be used by any source
     *
     * If all words from the query appear in the text (regardless of order),
     * give partial credit.
     *
     * PRECONDITION: Both text and query must already be normalized
     *
     * SCORING (from ApiConfig.Scoring):
     * - All words match: PRIMARY_CATEGORY_MATCH points (15)
     * - Not all words match: 0 points
     *
     * NOTE: We use PRIMARY_CATEGORY_MATCH as the score for all-words matching
     * because it represents a similar level of relevance (partial but meaningful match).
     *
     * @param normalizedText Normalized text to search in
     * @param normalizedQuery Normalized query
     * @return Score (0-15)
     */
    public static int calculateAllWordsMatchScore(String normalizedText, String normalizedQuery) {
        return allWordsMatch(normalizedText, normalizedQuery) ? ApiConfig.Scoring.PRIMARY_CATEGORY_MATCH : 0;
    }

    // ========== BOOLEAN ATTRIBUTE SCORES (GENERIC) ==========

    /**
     * Calculate score for a boolean attribute
     *
     * GENERIC: Generic helper for any yes/no attribute
     *
     * This is a simple utility method that can be used by any scorer
     * to score boolean attributes with custom point values.
     *
     * @param hasAttribute Whether the attribute is present
     * @param points Points to award if present
     * @return Score (0 or points)
     */
    public static int calculateBooleanAttributeScore(boolean hasAttribute, int points) {
        return hasAttribute ? points : 0;
    }

    // ========== FAVORITE BONUS (GENERIC) ==========

    /**
     * Calculate favorite bonus score
     *
     * GENERIC: Consistent across ALL sources
     *
     * This is a universal bonus applied consistently across all data sources
     * to boost items that the user has marked as favorites.
     *
     * SCORING (from ApiConfig.Scoring):
     * - Is favorite: FAVORITE_BONUS points (15)
     * - Not favorite: 0 points
     *
     * @param isFavorite Whether the item is marked as favorite
     * @return Score (0 or FAVORITE_BONUS = 15)
     */
    public static int calculateFavoriteBonus(boolean isFavorite) {
        return isFavorite ? ApiConfig.Scoring.FAVORITE_BONUS : 0;
    }

    // ========== SCORE MANIPULATION UTILITIES (GENERIC) ==========

    /**
     * Safely add score components, preventing overflow
     *
     * GENERIC: Utility for score calculation
     *
     * @param currentScore Current total score
     * @param additionalScore Score to add
     * @return Combined score (capped at Integer.MAX_VALUE)
     */
    public static int addScore(int currentScore, int additionalScore) {
        // Prevent integer overflow
        if (currentScore > Integer.MAX_VALUE - additionalScore) {
            return Integer.MAX_VALUE;
        }
        return currentScore + additionalScore;
    }

    /**
     * Clamp score to valid range
     *
     * GENERIC: Utility for score validation
     *
     * @param score Score to clamp
     * @param min Minimum allowed value
     * @param max Maximum allowed value
     * @return Clamped score
     */
    public static int clampScore(int score, int min, int max) {
        if (score < min) return min;
        if (score > max) return max;
        return score;
    }
}