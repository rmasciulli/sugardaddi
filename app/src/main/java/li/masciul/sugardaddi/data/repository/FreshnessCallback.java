package li.masciul.sugardaddi.data.repository;

/**
 * Callback for a cache-first freshness check that does NOT record a view
 * (no accessCount/lastViewed mutation) and does NOT auto-apply a changed
 * result - it only reports whether one exists.
 *
 * Used by ProductRepository.checkProductFreshness() and
 * RecipeRepository.checkRecipeFreshness(), both consumed by
 * MealRepository's aggregate freshness sweep when a meal is opened:
 * opening a meal shouldn't count as "viewing" every product/recipe it
 * contains, and nothing should change under the user without them
 * explicitly applying it - the same rule the existing single-item
 * refresh-FAB flow (onRefreshAvailable/applyCandidate) already follows.
 *
 * @param <T> FoodProduct or Recipe
 */
public interface FreshnessCallback<T> {
    /** A genuinely different version was fetched - not saved, just reported. */
    void onCandidate(T candidate);

    /** Nothing to report: not stale, fetch failed, source unavailable,
     *  nothing cached yet, or fetched-but-unchanged (already re-stamped). */
    void onNoCandidate();
}