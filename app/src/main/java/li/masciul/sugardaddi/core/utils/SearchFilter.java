package li.masciul.sugardaddi.core.utils;

import android.util.Log;

import androidx.annotation.NonNull;

import li.masciul.sugardaddi.core.enums.DataSourceType;
import li.masciul.sugardaddi.core.interfaces.Searchable;
import li.masciul.sugardaddi.core.interfaces.SourceSpecificScorer;
import li.masciul.sugardaddi.core.models.FoodProduct;
import li.masciul.sugardaddi.core.models.Recipe;
import li.masciul.sugardaddi.core.models.ScoredProduct;
import li.masciul.sugardaddi.core.scoring.CiqualScorer;
import li.masciul.sugardaddi.core.scoring.OpenFoodFactsScorer;
import li.masciul.sugardaddi.core.scoring.RecipeScorer;
import li.masciul.sugardaddi.core.scoring.USDAScorer;
import li.masciul.sugardaddi.data.network.ApiConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * SearchFilter — Filters and ranks search results using source-specific scoring
 * and a diversity strategy that ensures fair representation across all sources.
 *
 * ARCHITECTURE
 * ============
 * Operates on {@link Searchable} items, which may be {@link FoodProduct} or
 * {@link Recipe} instances. Each type is routed to its own scorer:
 *   - FoodProduct → source-specific scorer (Ciqual, OFF, USDA)
 *   - Recipe      → RecipeScorer (regardless of recipe's DataSource)
 *
 * The pipeline:
 *   1. Quality filtering   — remove items that don't meet minimum display requirements
 *   2. Source-specific scoring — each item scored by the scorer suited to its type/source
 *   3. Diversity enforcement  — ensure all sources are represented (min guarantee + fill)
 *   4. Result capping         — trim to ApiConfig.MAX_RESULTS
 *
 * LANGUAGE
 * ========
 * Language must always be passed explicitly. There is no language inference fallback —
 * callers are responsible for providing the user's current language via LanguageManager.
 *
 * THREAD SAFETY
 * =============
 * All methods are stateless and safe to call from any thread.
 */
public class SearchFilter {

    private static final String TAG = ApiConfig.SEARCH_LOG_TAG;

    // =========================================================================
    // MAIN FILTER + SORT
    // =========================================================================

    /**
     * Filter and rank a mixed list of {@link Searchable} items.
     *
     * Routes each item to the appropriate scorer based on runtime type:
     * - {@link FoodProduct} → source-specific scorer via {@link #getScorer(DataSourceType)}
     * - {@link Recipe}      → {@link RecipeScorer#getInstance()}
     *
     * Items that fail quality requirements are dropped before scoring.
     * The diversity strategy then ensures all sources are represented in the output.
     *
     * @param items    Raw items from the aggregated search result. May be empty, not null.
     * @param query    Original search query as typed by the user.
     * @param language BCP-47 language code for name/category matching (e.g. "en", "fr").
     *                 Must be supplied by the caller — use LanguageManager.getCurrentLanguage().
     * @return Filtered, scored, and diversity-balanced list. Never null, may be empty.
     */
    @NonNull
    public static List<Searchable> filterAndSort(@NonNull List<Searchable> items,
                                                 @NonNull String query,
                                                 @NonNull String language) {
        if (items.isEmpty()) {
            Log.d(TAG, "filterAndSort: empty input — nothing to filter");
            return new ArrayList<>();
        }

        Log.d(TAG, String.format("filterAndSort: %d items, query='%s', lang=%s",
                items.size(), query, language));

        String normalizedQuery = normalizeSearchTerm(query);
        List<ScoredProduct> scoredProducts = new ArrayList<>();

        for (Searchable item : items) {
            if (item instanceof FoodProduct) {
                FoodProduct product = (FoodProduct) item;

                // Drop products that don't meet basic display requirements
                if (!meetsQualityRequirements(product, language)) continue;

                SourceSpecificScorer<FoodProduct> scorer = getScorer(product.getDataSource());
                ScoredProduct scored = scorer.scoreProduct(product, normalizedQuery, language);

                if (scored.getScore() >= scorer.getMinimumScore()) {
                    scoredProducts.add(scored);
                    if (ApiConfig.DEBUG_LOGGING) {
                        Log.d(TAG, String.format("  FoodProduct '%s' [%s] → %d (%s)",
                                product.getDisplayName(language),
                                product.getDataSource(),
                                scored.getScore(),
                                scored.getScoreBreakdown()));
                    }
                }

            } else if (item instanceof Recipe) {
                Recipe recipe = (Recipe) item;

                // Minimum gate — recipe must have a displayable name
                String displayName = recipe.getDisplayName(language);
                if (displayName == null || displayName.trim().isEmpty()) continue;

                RecipeScorer scorer = RecipeScorer.getInstance();
                ScoredProduct scored = scorer.scoreProduct(recipe, normalizedQuery, language);

                if (scored.getScore() >= scorer.getMinimumScore()) {
                    scoredProducts.add(scored);
                    if (ApiConfig.DEBUG_LOGGING) {
                        Log.d(TAG, String.format("  Recipe '%s' [%s] → %d (%s)",
                                displayName,
                                recipe.getDataSource(),
                                scored.getScore(),
                                scored.getScoreBreakdown()));
                    }
                }

            }
            // Additional Searchable types (Meal etc.) — extend here when needed
        }

        // Apply diversity strategy or plain score sort
        List<Searchable> result;
        if (ApiConfig.SourceDiversity.ENFORCE_SOURCE_DIVERSITY) {
            result = DiversityStrategy.applyDiversity(
                    scoredProducts,
                    ApiConfig.SourceDiversity.MIN_RESULTS_PER_SOURCE,
                    ApiConfig.MAX_RESULTS
            );
            if (ApiConfig.DEBUG_LOGGING) {
                Log.d(TAG, "Diversity stats: "
                        + DiversityStrategy.formatDiversityStats(result));
            }
        } else {
            scoredProducts.sort((a, b) -> Integer.compare(b.getScore(), a.getScore()));
            result = new ArrayList<>();
            int cap = Math.min(scoredProducts.size(), ApiConfig.MAX_RESULTS);
            for (int i = 0; i < cap; i++) {
                result.add(scoredProducts.get(i).getItem());
            }
        }

        Log.d(TAG, String.format("filterAndSort: %d → %d items after filter",
                items.size(), result.size()));

        return result;
    }

    // =========================================================================
    // QUALITY REQUIREMENTS — FoodProduct only
    // =========================================================================

    /**
     * Returns true if a {@link FoodProduct} meets the minimum requirements
     * to be displayed in search results.
     *
     * A product is dropped if:
     * - It has no display name in the requested language
     * - Its data completeness score is below the configured minimum
     *
     * These checks are FoodProduct-specific. Recipe quality is handled
     * separately in the main scoring loop (name presence only).
     *
     * @param product  Product to evaluate
     * @param language Language for name lookup
     * @return True if the product should proceed to scoring
     */
    private static boolean meetsQualityRequirements(@NonNull FoodProduct product,
                                                    @NonNull String language) {
        // Must have a display name
        String name = product.getDisplayName(language);
        if (name == null || name.trim().isEmpty()) return false;

        // Must meet minimum data completeness
        if (product.getDataCompleteness() < ApiConfig.MIN_DATA_COMPLETENESS) return false;

        return true;
    }

    // =========================================================================
    // SCORER LOOKUP — FoodProduct sources only
    // =========================================================================

    /**
     * Returns the appropriate {@link SourceSpecificScorer} for a given data source.
     *
     * This method is only called for {@link FoodProduct} items. {@link Recipe} items
     * always use {@link RecipeScorer} directly in the main scoring loop above,
     * regardless of their {@link DataSourceType}.
     *
     * @param source The item's data source. Null-safe — falls back to OFF scorer.
     * @return Scorer instance. Never null.
     */
    @NonNull
    private static SourceSpecificScorer<FoodProduct> getScorer(DataSourceType source) {
        if (source == null) return OpenFoodFactsScorer.getInstance();

        switch (source) {
            case CIQUAL:
                return CiqualScorer.getInstance();
            case USDA:
                return USDAScorer.getInstance();
            case OPENFOODFACTS:
                return OpenFoodFactsScorer.getInstance();
            case THEMEALDB:
                // TheMealDB produces Recipe objects — they never reach this method.
                // This case is a safety net only.
                return OpenFoodFactsScorer.getInstance();
            default:
                // USER, CUSTOM, IMPORTED, API_CACHE, etc.
                return OpenFoodFactsScorer.getInstance();
        }
    }

    // =========================================================================
    // QUERY NORMALISATION
    // =========================================================================

    /**
     * Normalise a search query for consistent matching across scorers.
     *
     * Trims whitespace, converts to lowercase, and collapses multiple
     * spaces into one. Diacritics are preserved — scorers handle
     * language-specific normalisation internally.
     *
     * @param query Raw query from the user. Null-safe — returns "" for null.
     * @return Normalised query string. Never null.
     */
    @NonNull
    public static String normalizeSearchTerm(String query) {
        if (query == null) return "";
        return query.trim().toLowerCase().replaceAll("\\s+", " ");
    }
}