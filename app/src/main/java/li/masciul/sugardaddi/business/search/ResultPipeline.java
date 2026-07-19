package li.masciul.sugardaddi.business.search;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import li.masciul.sugardaddi.core.enums.DataSourceType;
import li.masciul.sugardaddi.core.interfaces.Searchable;
import li.masciul.sugardaddi.core.interfaces.SourceSpecificScorer;
import li.masciul.sugardaddi.core.models.FoodPortion;
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
 * ResultPipeline - Scores, ranks, and diversifies search results using
 * source-specific scoring and a diversity strategy that ensures fair
 * representation across all active data sources.
 *
 * ARCHITECTURE
 * ============
 * Operates on {@link Searchable} items, which may be {@link FoodProduct} or
 * {@link Recipe} instances. Each type passes through a dedicated quality gate
 * before being routed to its appropriate scorer:
 *   - FoodProduct → source-specific scorer (Ciqual, OFF, USDA)
 *   - Recipe      → RecipeScorer (regardless of the recipe's DataSource)
 *
 * THE PIPELINE (four steps):
 *   1. Quality gate        - drop items that don't meet minimum display requirements
 *   2. Scorer resolution   - route each item to the right scorer via getScorer()
 *   3. Diversity strategy  - guarantee minimum representation per source, then quality fill
 *   4. Result capping      - trim to ApiConfig.MAX_RESULTS
 *
 * QUALITY GATES
 * =============
 * FoodProduct: must have a display name + meet minimum data completeness threshold.
 * Recipe:      must have a display name + at least one ingredient + at least one
 *              instruction step (or non-empty raw instructions blob).
 *
 * These gates are intentionally lightweight - they drop structurally incomplete
 * items that would render poorly in the UI, not items that merely scored low.
 * Score-based filtering is handled by scorer.getMinimumScore().
 *
 * SCORER RESOLUTION
 * =================
 * getScorer() handles both FoodProduct and Recipe via instanceof dispatch.
 * Adding support for a new Searchable type requires only a new branch there -
 * process() does not need to change.
 *
 * LANGUAGE
 * ========
 * Language must always be passed explicitly. There is no language inference
 * fallback - callers are responsible for providing the user's current language
 * via LanguageManager.
 *
 * THREAD SAFETY
 * =============
 * All methods are stateless and safe to call from any thread.
 */
public class ResultPipeline {

    private static final String TAG = ApiConfig.SEARCH_LOG_TAG;

    // =========================================================================
    // MAIN ENTRY POINT
    // =========================================================================

    /**
     * Run the full result pipeline on a mixed list of {@link Searchable} items.
     *
     * Each item passes through:
     *   1. Type-specific quality gate (meetsQualityRequirements)
     *   2. Scorer resolution (getScorer)
     *   3. Minimum score threshold check
     *   4. Diversity strategy + result capping
     *
     * Items that fail the quality gate or score below the minimum are silently
     * dropped. The caller receives a ready-to-display list.
     *
     * @param items    Raw items from the aggregated search result. May be empty, not null.
     * @param query    Original search query as typed by the user.
     * @param language BCP-47 language code for name/category matching (e.g. "en", "fr").
     *                 Must be supplied by the caller via LanguageManager.getCurrentLanguage().
     * @return Scored, ranked, and diversity-balanced list. Never null, may be empty.
     */
    @NonNull
    public static List<Searchable> process(@NonNull List<Searchable> items,
                                           @NonNull String query,
                                           @NonNull String language) {
        if (items.isEmpty()) {
            Log.d(TAG, "process: empty input - nothing to process");
            return new ArrayList<>();
        }

        Log.d(TAG, String.format("process: %d items, query='%s', lang=%s",
                items.size(), query, language));

        String normalizedQuery = normalizeSearchTerm(query);
        List<ScoredProduct> scoredProducts = new ArrayList<>();

        for (Searchable item : items) {

            // ── 1. Quality gate - type-specific ─────────────────────────────
            if (item instanceof FoodProduct) {
                if (!meetsQualityRequirements((FoodProduct) item, language)) continue;
            } else if (item instanceof Recipe) {
                if (!meetsQualityRequirements((Recipe) item, language)) continue;
            }
            // Additional Searchable types - extend meetsQualityRequirements() when needed

            // ── 2. Scorer resolution + scoring ───────────────────────────────
            @SuppressWarnings("unchecked")
            SourceSpecificScorer<Searchable> scorer = getScorer(item);
            ScoredProduct scored = scorer.scoreProduct(item, normalizedQuery, language);

            // ── 3. Minimum score threshold ────────────────────────────────────
            if (scored.getScore() >= scorer.getMinimumScore()) {
                scoredProducts.add(scored);
                if (ApiConfig.DEBUG_LOGGING) {
                    Log.d(TAG, String.format("  %s '%s' [%s] → %d (%s)",
                            item.getClass().getSimpleName(),
                            item.getDisplayName(language),
                            item.getDataSource(),
                            scored.getScore(),
                            scored.getScoreBreakdown()));
                }
            }
        }

        // ── 4. Diversity strategy + result capping ────────────────────────────
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

        Log.d(TAG, String.format("process: %d → %d items after pipeline",
                items.size(), result.size()));

        return result;
    }

    // =========================================================================
    // QUALITY GATES
    // =========================================================================

    /**
     * Returns true if a {@link FoodProduct} meets quality requirements.
     *
     * A product is dropped if:
     * - It has no display name in the requested language
     * - Its data completeness score is below the configured minimum threshold
     *
     * @param product  Product to evaluate. Never null.
     * @param language Language for display name lookup.
     * @return True if the product should proceed to scoring.
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

    /**
     * Returns true if a {@link Recipe} meets quality requirements.
     *
     * A recipe is dropped if:
     * - It has no display name in the requested language
     * - It has no ingredients (a recipe with no ingredients cannot be used)
     * - It has neither structured steps nor a raw instructions blob
     *   (a recipe with no preparation guidance is not displayable)
     *
     * Note: image absence is NOT a gate criterion - it is a scoring bonus in
     * RecipeScorer. A text-only recipe is perfectly valid.
     *
     * @param recipe   Recipe to evaluate. Never null.
     * @param language Language for display name and instructions lookup.
     * @return True if the recipe should proceed to scoring.
     */
    private static boolean meetsQualityRequirements(@NonNull Recipe recipe,
                                                    @NonNull String language) {
        // Must have a display name
        String name = recipe.getDisplayName(language);
        if (name == null || name.trim().isEmpty()) return false;

        // Must have at least one ingredient
        List<FoodPortion> portions = recipe.getPortions();
        if (portions == null || portions.isEmpty()) return false;

        // Must have at least some preparation guidance - waived for
        // previews (search-result-only recipes explicitly missing full
        // detail by design, e.g. FatSecret's recipes/search/v3). A
        // non-preview recipe with genuinely empty instructions still
        // correctly fails this gate - the waiver only applies when the
        // source itself has explicitly marked the object as partial via
        // Recipe.setPreview(true), not inferred from empty fields alone
        // (which would also silently let real bad data through).
        if (!recipe.isPreview()) {
            boolean hasSteps = recipe.getStepCount() > 0;
            boolean hasRawInstructions = recipe.getInstructions(language) != null
                    && !recipe.getInstructions(language).trim().isEmpty();
            if (!hasSteps && !hasRawInstructions) return false;
        }

        return true;
    }

    // =========================================================================
    // SCORER RESOLUTION
    // =========================================================================

    /**
     * Returns the appropriate {@link SourceSpecificScorer} for any {@link Searchable}.
     *
     * Dispatches by runtime type:
     * - {@link Recipe}      → {@link RecipeScorer} regardless of DataSource.
     *                         TheMealDB, TheCocktailDB, and user recipes all use the same scorer.
     * - {@link FoodProduct} → source-specific scorer based on DataSourceType
     * - Unknown type        → falls back to OFF scorer as a safe default
     *
     * The unchecked cast to SourceSpecificScorer<Searchable> is safe because:
     * - Each scorer's scoreProduct() accepts its declared type T
     * - The instanceof dispatch above guarantees the item matches the scorer's T
     * - Java type erasure means the cast is a no-op at runtime
     *
     * Adding a new Searchable type: add a new instanceof branch here and a
     * corresponding meetsQualityRequirements() overload. process() does not change.
     *
     * @param item Item to resolve a scorer for. Never null.
     * @return Appropriate scorer instance. Never null.
     */
    @NonNull
    @SuppressWarnings("unchecked")
    private static SourceSpecificScorer<Searchable> getScorer(@NonNull Searchable item) {
        if (item instanceof Recipe) {
            // All recipe sources use the same scorer
            return (SourceSpecificScorer<Searchable>) (SourceSpecificScorer<?>)
                    RecipeScorer.getInstance();
        }

        if (item instanceof FoodProduct) {
            DataSourceType source = ((FoodProduct) item).getDataSource();
            if (source != null) {
                switch (source) {
                    case CIQUAL:
                        return (SourceSpecificScorer<Searchable>) (SourceSpecificScorer<?>)
                                CiqualScorer.getInstance();
                    case USDA:
                        return (SourceSpecificScorer<Searchable>) (SourceSpecificScorer<?>)
                                USDAScorer.getInstance();
                    case OPENFOODFACTS:
                        return (SourceSpecificScorer<Searchable>) (SourceSpecificScorer<?>)
                                OpenFoodFactsScorer.getInstance();
                    default:
                        break;
                }
            }
        }

        // Unknown type or unrecognised source - safe default
        Log.w(TAG, "getScorer: unhandled Searchable type "
                + item.getClass().getSimpleName() + " - using OFF scorer as fallback");
        return (SourceSpecificScorer<Searchable>) (SourceSpecificScorer<?>)
                OpenFoodFactsScorer.getInstance();
    }

    // =========================================================================
    // QUERY NORMALISATION
    // =========================================================================

    /**
     * Normalise a search query for consistent matching across all scorers.
     *
     * Trims whitespace, converts to lowercase, and collapses multiple spaces
     * into one. Diacritics are preserved - scorers handle language-specific
     * normalisation internally.
     *
     * Called once per search in process() and also used directly by scorers
     * for consistent text normalisation.
     *
     * @param query Raw query from the user. Null-safe - returns "" for null.
     * @return Normalised query string. Never null.
     */
    @NonNull
    public static String normalizeSearchTerm(@Nullable String query) {
        if (query == null) return "";
        return query.trim().toLowerCase().replaceAll("\\s+", " ");
    }
}