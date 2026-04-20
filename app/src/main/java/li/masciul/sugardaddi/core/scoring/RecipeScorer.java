package li.masciul.sugardaddi.core.scoring;

import li.masciul.sugardaddi.core.enums.DataSource;
import li.masciul.sugardaddi.core.enums.Difficulty;
import li.masciul.sugardaddi.core.models.FoodPortion;
import li.masciul.sugardaddi.core.models.Recipe;
import li.masciul.sugardaddi.core.utils.SearchFilter;
import li.masciul.sugardaddi.data.network.ApiConfig;

import java.util.List;

/**
 * RecipeScorer - Scoring strategy for user-created recipes
 *
 * VALUES WHAT RECIPES PROVIDE:
 * - Recipe names (user-created, descriptive)
 * - Ingredient lists (searchable by ingredients)
 * - Meal type tags (breakfast, lunch, dinner, snack)
 * - Difficulty ratings (easy, medium, hard)
 * - Cooking steps and instructions
 * - Preparation and cooking times
 * - Cuisine types
 *
 * WHAT RECIPES DON'T PROVIDE (and shouldn't be penalized for):
 * - NutriScore/EcoScore (not applicable to recipes)
 * - Brands (user-created content)
 * - Data completeness metrics (different structure)
 *
 * SCORING BREAKDOWN (max 100 points):
 * - Name matching: 0-40 pts (base implementation)
 * - Ingredient matching: 0-30 pts (Recipe-specific - key feature!)
 * - Cuisine matching: 0-15 pts (Recipe-specific)
 * - Category matching: 0-20 pts (base implementation)
 * - Has steps: 10 pts (quality indicator)
 * - Has image: 5 pts (visual appeal)
 * - Appropriate difficulty: 5 pts (accessibility)
 * - Favorite bonus: +15 pts (common)
 *
 * MAXIMUM SCORE: 100 points (before favorite bonus)
 * MAXIMUM WITH FAVORITE: 115 points
 *
 * WHY 100 MAX (same as Ciqual):
 * - User-generated content, variable quality
 * - Fewer standardized attributes than OFF
 * - But ingredient matching is very powerful
 * - Fair given the nature of recipe data
 *
 * EXAMPLE SCORING:
 * Perfect recipe match:
 * - exact_name: 40
 * - ingredient_match: 30
 * - cuisine_match: 15
 * - category: 20
 * - has_steps: 10
 * - has_image: 5
 * - appropriate_difficulty: 5
 * - favorite: 15
 * = 140 points total
 *
 * Typical good match:
 * - partial_name: 20
 * - ingredient_match: 20
 * - has_steps: 10
 * - has_image: 5
 * = 55 points
 *
 * @version 1.0
 * @since Search Diversity Refactor
 */
public class RecipeScorer extends BaseScorer<Recipe> {

    // ========== SINGLETON PATTERN ==========

    private static RecipeScorer instance;

    /**
     * Get singleton instance (Android optimization - avoid repeated instantiation)
     */
    public static RecipeScorer getInstance() {
        if (instance == null) {
            instance = new RecipeScorer();
        }
        return instance;
    }

    /**
     * Private constructor for singleton
     */
    private RecipeScorer() {
        // Private constructor
    }

    // ========== INTERFACE IMPLEMENTATION ==========

    @Override
    public DataSource getDataSource() {
        return DataSource.USER; // User-created recipes
    }

    @Override
    public int getMaxScore() {
        return 100; // Same as Ciqual
    }

    @Override
    public int getMinimumScore() {
        return ApiConfig.Scoring.MINIMUM_SCORE; // Slightly lower threshold for user content
    }

    @Override
    public String getScorerName() {
        return "Recipe Scorer";
    }

    // ========== SOURCE-SPECIFIC ATTRIBUTES ==========

    /**
     * Score Recipe-specific attributes
     *
     * This is where we value what makes recipes unique:
     * - Ingredient matching (very important!)
     * - Cuisine type
     * - Quality indicators (steps, images)
     * - Accessibility (difficulty)
     */
    @Override
    protected int scoreSourceSpecificAttributes(Recipe recipe, String normalizedQuery, String language, StringBuilder breakdown) {
        int score = 0;

        // 1. Ingredient matching (0-30 pts) - Recipe's main strength!
        // Users often search by ingredient (e.g., "chocolate cake")
        int ingredientScore = scoreIngredientMatch(recipe, normalizedQuery, language);
        if (ingredientScore > 0) {
            score += ingredientScore;
            breakdown.append("ingredients:").append(ingredientScore).append(" ");
        }

        // 2. Cuisine matching (0-15 pts)
        // Users search by cuisine type (e.g., "italian", "chinese")
        String cuisine = recipe.getCuisine(language);
        if (cuisine != null && !cuisine.isEmpty()) {
            String normalizedCuisine = SearchFilter.normalizeSearchTerm(cuisine);
            if (normalizedCuisine.contains(normalizedQuery)) {
                score += ApiConfig.Scoring.Recipe.CUISINE_MATCH;
                breakdown.append("cuisine:").append(ApiConfig.Scoring.Recipe.CUISINE_MATCH).append(" ");
            }
        }

        // 3. Has cooking steps (10 pts) - Quality indicator
        // Recipes with steps are more complete and useful
        if (recipe.getStepCount() > 0) {
            score += ApiConfig.Scoring.Recipe.HAS_STEPS;
            breakdown.append("has_steps:").append(ApiConfig.Scoring.Recipe.HAS_STEPS).append(" ");
        }

        // 4. Has image (5 pts) - Visual appeal
        // Images make recipes more attractive and trustworthy
        if (recipe.getImageUrl() != null && !recipe.getImageUrl().isEmpty()) {
            score += ApiConfig.Scoring.Recipe.HAS_IMAGE;
            breakdown.append("image:").append(ApiConfig.Scoring.Recipe.HAS_IMAGE).append(" ");
        }

        // 5. Appropriate difficulty (5 pts) - Accessibility
        // Easy and medium recipes are more accessible to users
        Difficulty difficulty = recipe.getDifficulty();
        if (difficulty == Difficulty.EASY || difficulty == Difficulty.MEDIUM) {
            score += ApiConfig.Scoring.Recipe.ACCESSIBLE_DIFFICULTY;
            breakdown.append("accessible:").append(ApiConfig.Scoring.Recipe.ACCESSIBLE_DIFFICULTY).append(" ");
        }

        return score;
    }

    // ========== HELPER METHODS ==========

    /**
     * Score ingredient matching
     *
     * This is crucial for recipes - users often search by ingredient.
     * We check if the query matches any ingredient names.
     *
     * SCORING:
     * - Multiple ingredient matches: 30 pts
     * - Single exact ingredient match: 25 pts
     * - Single partial ingredient match: 15 pts
     * - No matches: 0 pts
     *
     * @param recipe Recipe to check
     * @param normalizedQuery Normalized search query
     * @param language Language
     * @return Ingredient match score (0-30)
     */
    private int scoreIngredientMatch(Recipe recipe, String normalizedQuery, String language) {
        List<FoodPortion> portions = recipe.getPortions();
        if (portions == null || portions.isEmpty()) {
            return 0;
        }

        int matchCount = 0;
        boolean hasExactMatch = false;

        // Check each ingredient (FoodPortion)
        for (FoodPortion portion : portions) {
            if (portion.getFoodProduct() != null) {
                String ingredientName = portion.getFoodProduct().getName(language);
                if (ingredientName != null && !ingredientName.isEmpty()) {
                    String normalizedIngredient = SearchFilter.normalizeSearchTerm(ingredientName);

                    // Check for match
                    if (normalizedIngredient.equals(normalizedQuery)) {
                        hasExactMatch = true;
                        matchCount++;
                    } else if (normalizedIngredient.contains(normalizedQuery)) {
                        matchCount++;
                    }
                }
            }

            // Also check if the portion itself is a recipe (nested recipes)
            if (portion.getRecipe() != null) {
                String recipeName = portion.getRecipe().getName(language);
                if (recipeName != null && !recipeName.isEmpty()) {
                    String normalizedRecipeName = SearchFilter.normalizeSearchTerm(recipeName);

                    if (normalizedRecipeName.contains(normalizedQuery)) {
                        matchCount++;
                    }
                }
            }
        }

        // Calculate score based on matches
        if (matchCount >= 2) {
            return ApiConfig.Scoring.Recipe.INGREDIENT_MATCH_MULTIPLE;  // Multiple ingredient matches
        } else if (matchCount == 1 && hasExactMatch) {
            return ApiConfig.Scoring.Recipe.INGREDIENT_MATCH_EXACT;     // Single exact match
        } else if (matchCount == 1) {
            return ApiConfig.Scoring.Recipe.INGREDIENT_MATCH_PARTIAL;   // Single partial match
        }

        return 0; // No matches
    }
}