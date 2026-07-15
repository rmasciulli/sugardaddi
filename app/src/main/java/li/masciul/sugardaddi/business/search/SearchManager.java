package li.masciul.sugardaddi.business.search;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import li.masciul.sugardaddi.core.interfaces.Searchable;
import li.masciul.sugardaddi.core.models.Error;
import li.masciul.sugardaddi.data.network.ApiConfig;
import li.masciul.sugardaddi.data.sources.aggregation.AggregatedSearchResult;
import li.masciul.sugardaddi.data.sources.aggregation.DataSourceAggregator;
import li.masciul.sugardaddi.data.sources.base.DataSource;
import li.masciul.sugardaddi.managers.LanguageManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * SearchManager - Unified search orchestration.
 *
 * ARCHITECTURE v5.0 - Unified pipeline
 * =====================================
 * All search - products and recipes - flows through a single path:
 *
 *   search(query) / loadMoreResults()
 *       → DataSourceAggregator.searchAll(query, limit, page, exhaustedSources)
 *       → SmartMergeStrategy (level-1 deduplication, per-page)
 *       → ProductRepository.enrichAndCache() (Room enrichment, background thread)
 *       → level-2 deduplication via seenSearchableIds (cross-page)
 *       → SearchListener callbacks (main thread)
 *
 * WHAT WAS REMOVED vs v4.1
 * =========================
 * - SearchScope enum (PRODUCTS_ONLY / RECIPES_ONLY / ALL) - scope gating was
 *   hardcoded to PRODUCTS_ONLY and bypassed recipe search entirely. The aggregator
 *   already handles all Searchable types uniformly.
 * - performProductSearch / performRecipeSearch / performParallelSearch - replaced
 *   by a single performSearch() that calls the aggregator directly.
 * - RecipeRepository dependency - SearchManager no longer needs it. Recipe live
 *   search comes through the aggregator (TheMealDB is a registered DataSource).
 *   RecipeRepository.search() is now Room-only, used by dedicated recipe screens.
 * - searchFoodAdvanced() / ProductRepository pagination delegation - pagination
 *   is now owned here, not split across SearchManager + ProductRepository.
 * - parallelCombinedResults / AtomicInteger parallel state - gone with the scope.
 *
 * PAGINATION STATE
 * ================
 * SearchManager owns all pagination state for the current query:
 *
 *   currentPage       - 1-based, incremented by loadMoreResults()
 *   hasMorePages      - set from AggregatedSearchResult.hasMore() after each call
 *   exhaustedSources  - sources that reported hasMore=false; skipped on next pages
 *   seenSearchableIds - all IDs delivered to the UI; filters cross-page duplicates
 *
 * All four are reset together by resetSearchState() on every new query.
 *
 * DEDUPLICATION - TWO LEVELS
 * ==========================
 * Level 1 (SmartMergeStrategy, inside aggregator): deduplicates FoodProduct items
 *   from different sources within a single page, merging the richest data.
 *   Recipes pass through as-is (no cross-source recipe merging needed).
 *
 * Level 2 (seenSearchableIds, here): deduplicates across pages using
 *   Searchable.getSearchableId(). Handles the case where the same item appears
 *   on different pages due to server-side ranking drift. Works uniformly for
 *   both FoodProduct and Recipe because both implement Searchable.
 *
 * THREADING
 * =========
 * - search() and loadMoreResults() may be called from any thread.
 * - All listener callbacks are delivered on the main thread.
 * - Enrichment (Room read) runs on ProductRepository's background executor.
 * - searchHandler posts debounced searches; no other handler needed.
 */
public class SearchManager {

    private static final String TAG = ApiConfig.SEARCH_LOG_TAG;

    // =========================================================================
    // DEPENDENCIES
    // =========================================================================

    /** Parallel search across all registered DataSource instances. */
    private final DataSourceAggregator aggregator;

    /**
     * Room enrichment and SearchResultCache.
     * SearchManager does NOT call aggregator through searchCache -
     * it calls the aggregator directly and uses searchCache only for
     * enrichAndCache() and checkCache().
     */
    private final SearchCache searchCache;

    /** Application context - used for LanguageManager. */
    private final Context context;

    /** Debounce handler - all search scheduling goes through this. */
    private final Handler searchHandler;

    /** Autocomplete debounce handler - separate so it doesn't cancel full searches. */
    private final Handler autocompleteHandler;

    // =========================================================================
    // LISTENERS
    // =========================================================================

    @Nullable private SearchListener listener;
    @Nullable private AutocompleteListener autocompleteListener;

    // =========================================================================
    // SEARCH STATE
    // =========================================================================

    /** The query currently being searched or last searched. */
    private String currentQuery = "";

    /** True while a search call is in flight (page 1). */
    private boolean isSearchActive = false;

    /** True while a loadMoreResults call is in flight (page 2+). */
    private boolean isPaginationActive = false;

    /** True while an autocomplete call is in flight. */
    private boolean isAutocompleteActive = false;

    /** True once onSearchComplete has delivered final results for the current query. */
    private boolean finalResultDelivered = false;

    /**
     * Incremented every time resetSearchState() runs (i.e. every new page-1
     * search). Each performSearch()/loadMoreResults() call captures the
     * current value as myGeneration and compares it against the live field
     * in every async callback, so a slow, superseded call can never deliver
     * results over a newer one - even when both calls share the exact same
     * query text, which a plain query.equals(currentQuery) check can't
     * distinguish (two overlapping rounds for "cheese" look identical by
     * that comparison alone).
     */
    private int searchGeneration = 0;

    // =========================================================================
    // PAGINATION STATE  (all reset by resetSearchState on new query)
    // =========================================================================

    /** Current page number. Starts at 1, incremented by loadMoreResults(). */
    private int currentPage = 1;

    /**
     * True while more pages are available.
     * Set from AggregatedSearchResult.hasMore() - replaces the old heuristic
     * of items.size() >= API_PAGE_SIZE, which was wrong when results exhausted
     * exactly on a page boundary.
     */
    private boolean hasMorePages = true;

    /**
     * Source IDs that have reported hasMore=false for the current query.
     * Passed to DataSourceAggregator.searchAll() so those sources are skipped.
     * Prevents TheMealDB (all-in-one, no pagination) from being called again
     * on pages 2, 3, …
     */
    private final Set<String> exhaustedSources = new HashSet<>();

    /**
     * Searchable IDs already delivered to the listener for the current query.
     * Used for level-2 cross-page deduplication. A result whose ID is already
     * in this set is silently dropped before the listener callback.
     */
    private final Set<String> seenSearchableIds = new HashSet<>();

    // =========================================================================
    // FILTER STATE
    // =========================================================================

    /**
     * Active search filter set by the user via the filter UI in MainActivity.
     * Passed to DataSourceAggregator.searchAll() on every search call.
     * Default: no filter (all types, all sources).
     * Survives across queries - reset only if the user explicitly clears filters.
     */
    @NonNull
    private SearchFilter activeFilter = SearchFilter.noFilter();

    // =========================================================================
    // PENDING RUNNABLES (for debounce cancellation)
    // =========================================================================

    @Nullable private Runnable pendingSearch;
    @Nullable private Runnable pendingAutocomplete;

    // =========================================================================
    // SEARCH STATISTICS
    // =========================================================================

    private int searchCount     = 0;
    private int autocompleteCount = 0;
    private int cacheHits       = 0;
    private long lastSearchTime = 0;

    // =========================================================================
    // LISTENER INTERFACES
    // =========================================================================

    /**
     * Listener for full search lifecycle events.
     *
     * All methods are called on the main thread.
     *
     * hasMore is carried on onSearchResults and onMoreResults so the adapter
     * can show/hide the pagination footer with an accurate signal rather than
     * guessing from items.size().
     */
    public interface SearchListener {

        /**
         * Called when page-1 results are ready.
         * @param results Merged, deduplicated, scored items (FoodProduct + Recipe)
         * @param hasMore True if further pages are available via loadMoreResults()
         */
        void onSearchResults(@NonNull List<Searchable> results, boolean hasMore);

        /** Called when the search fails after all retries. */
        void onSearchError(@NonNull Error error);

        /** Called immediately when a search starts (show spinner). */
        void onSearchLoading();

        /** Called when query is empty or too short. */
        void onSearchEmpty();

        /**
         * Called when a pagination page is ready.
         * @param results New items to append to the list
         * @param hasMore True if further pages are still available
         */
        void onMoreResults(@NonNull List<Searchable> results, boolean hasMore);

        /** Called when a pagination call fails. */
        void onMoreResultsError(@NonNull Error error);

        /** Called when loadMoreResults() starts (show footer spinner). */
        void onLoadingMore();

        /** Called when the current search is cancelled. */
        default void onSearchCancelled() {}
    }

    /**
     * Listener for autocomplete suggestions.
     * All methods called on the main thread.
     */
    public interface AutocompleteListener {
        void onAutocompleteSuggestions(@NonNull List<String> suggestions);
        void onAutocompleteError(@NonNull Error error);
        void onQueryTooShort();
    }

    // =========================================================================
    // CONSTRUCTOR
    // =========================================================================

    /**
     * @param context       Application or Activity context
     * @param aggregator    Stateless parallel search executor
     * @param searchCache   Used for cache checks and Room enrichment
     */
    public SearchManager(@NonNull Context context,
                         @NonNull DataSourceAggregator aggregator,
                         @NonNull SearchCache searchCache) {
        this.context            = context.getApplicationContext();
        this.aggregator         = aggregator;
        this.searchCache        = searchCache;
        this.searchHandler      = new Handler(Looper.getMainLooper());
        this.autocompleteHandler = new Handler(Looper.getMainLooper());

        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, "SearchManager v5.0 initialized ("
                    + ApiConfig.SEARCH_DEBOUNCE_MS + "ms debounce)");
        }
    }

    // =========================================================================
    // CONFIGURATION
    // =========================================================================

    public void setListener(@Nullable SearchListener listener) {
        this.listener = listener;
    }

    public void setAutocompleteListener(@Nullable AutocompleteListener listener) {
        this.autocompleteListener = listener;
    }

    // =========================================================================
    // PUBLIC SEARCH API
    // =========================================================================

    /**
     * Update the active search filter and re-trigger the current search.
     *
     * Called by MainActivity when the user dismisses a filter popup.
     * No-op if the filter hasn't changed (avoids spurious re-searches).
     * No-op if no query is active.
     *
     * @param filter New filter state. Pass SearchFilter.noFilter() to clear.
     */
    public void setFilters(@NonNull SearchFilter filter) {
        if (filter.equals(activeFilter)) return;
        activeFilter = filter;

        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, "Filter updated: " + filter);
        }

        if (!currentQuery.isEmpty()) {
            // Invalidate cache for current query - cached results were produced
            // with the previous filter and must not be reused
            searchCache.invalidate(currentQuery);
            searchImmediate(currentQuery);
        }
    }

    /** @return The currently active search filter. Never null. */
    @NonNull
    public SearchFilter getActiveFilter() {
        return activeFilter;
    }

    /**
     * Schedule a debounced search for the given query.
     *
     * If the query is the same as the previous one (e.g. user re-submits),
     * the debounce is still applied so rapid re-triggers don't double-fire.
     * Resets all pagination state for the new query.
     *
     * @param query Raw user input - trimmed internally
     */
    public void search(@Nullable String query) {
        cancelPendingSearch();

        final String trimmed = query != null ? query.trim() : "";
        currentQuery = trimmed;

        // Reset all pagination state for this new query
        resetSearchState();

        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, "Search requested: '" + trimmed + "' (length=" + trimmed.length() + ")");
        }

        if (trimmed.length() < ApiConfig.MIN_SEARCH_LENGTH) {
            notifySearchEmpty();
            return;
        }

        pendingSearch = () -> {
            if (currentQuery.equals(trimmed)) {
                performSearch(trimmed);
            }
        };
        searchHandler.postDelayed(pendingSearch, ApiConfig.SEARCH_DEBOUNCE_MS);
    }

    /**
     * Execute a search immediately, bypassing the debounce delay.
     * Used when the user explicitly submits (Enter key, suggestion tap).
     */
    public void searchImmediate(@Nullable String query) {
        cancelPendingSearch();
        final String trimmed = query != null ? query.trim() : "";
        currentQuery = trimmed;
        resetSearchState();

        if (trimmed.length() >= ApiConfig.MIN_SEARCH_LENGTH) {
            performSearch(trimmed);
        } else {
            notifySearchEmpty();
        }
    }

    /**
     * Schedule a debounced autocomplete query.
     * Uses a shorter delay than full search for faster suggestions.
     *
     * Failures are silent - the dropdown simply stays empty.
     *
     * @param query Raw user input - trimmed internally
     */
    public void autocomplete(@Nullable String query) {
        cancelPendingAutocomplete();

        final String trimmed = query != null ? query.trim() : "";

        if (trimmed.length() < 3) {
            notifyQueryTooShort();
            return;
        }

        pendingAutocomplete = () -> performAutocomplete(trimmed);
        autocompleteHandler.postDelayed(pendingAutocomplete, ApiConfig.SEARCH_DEBOUNCE_MS / 2);
    }

    /**
     * Load the next page of results for the current query.
     *
     * Guards:
     * - currentQuery must be non-empty
     * - hasMorePages must be true (set from AggregatedSearchResult.hasMore())
     * - no pagination already in flight
     * - no page-1 search in flight
     *
     * Safe to call repeatedly from a scroll listener - guards prevent double-firing.
     */
    public void loadMoreResults() {
        if (currentQuery.isEmpty() || !hasMorePages
                || isPaginationActive || isSearchActive) {
            if (ApiConfig.DEBUG_LOGGING) {
                Log.d(TAG, "loadMoreResults skipped: query='" + currentQuery
                        + "' hasMore=" + hasMorePages
                        + " paginationActive=" + isPaginationActive
                        + " searchActive=" + isSearchActive);
            }
            return;
        }

        isPaginationActive = true;
        currentPage++;

        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, "Loading page " + currentPage
                    + " for '" + currentQuery + "'"
                    + " (exhausted=" + exhaustedSources + ")");
        }

        notifyLoadingMore();

        // Captured once, at call time - same purpose as in performSearch():
        // a page-1 search started while this pagination call is still
        // in flight (e.g. stuck behind a slow source's retries) must not
        // let this call's callbacks touch state that now belongs to the
        // newer round.
        final int myGeneration = searchGeneration;

        // Use an unmodifiable snapshot of exhaustedSources - the set must not be
        // modified while the aggregator is iterating it on a background thread.
        final Set<String> exhaustedSnapshot =
                Collections.unmodifiableSet(new HashSet<>(exhaustedSources));

        aggregator.searchAll(
                currentQuery,
                ApiConfig.API_PAGE_SIZE,
                currentPage,
                exhaustedSnapshot,
                activeFilter,
                new DataSourceAggregator.AggregatorCallback() {

                    @Override
                    public void onSearchComplete(@NonNull AggregatedSearchResult result) {
                        if (myGeneration != searchGeneration) {
                            // A newer page-1 search has already started - this
                            // pagination call belongs to a superseded round.
                            if (ApiConfig.DEBUG_LOGGING) {
                                Log.d(TAG, "Discarding stale page " + currentPage
                                        + " (generation " + myGeneration + " != "
                                        + searchGeneration + ")");
                            }
                            return;
                        }

                        // Update exhaustion state from this page's results
                        updateExhaustedSources(result.getSourceHasMore());

                        // Update hasMorePages from the aggregated signal
                        hasMorePages = result.hasMore();

                        // Deduplicate against everything already delivered (level 2)
                        List<Searchable> fresh = deduplicateAndRegister(result.getItems());

                        isPaginationActive = false;

                        if (fresh.isEmpty()) {
                            // All results were cross-page duplicates - treat as exhausted.
                            // Must still notify the adapter: hasMorePages here is a
                            // SearchManager field, separate from the adapter's own
                            // hasMoreItems/isLoadingMore, which only update via an
                            // actual updateItems()/addMoreItems() call. Without this,
                            // the footer's spinner was left stuck spinning forever -
                            // "disappears naturally" was never actually true.
                            if (ApiConfig.DEBUG_LOGGING) {
                                Log.d(TAG, "Page " + currentPage
                                        + " contained only duplicates - marking exhausted");
                            }
                            hasMorePages = false;
                            notifyMoreResults(Collections.emptyList(), false);
                            return;
                        }

                        finalResultDelivered = true;

                        // Enrich without caching (page 1 cache is the canonical entry)
                        searchCache.enrichAndCache(fresh, currentQuery, false, () -> {
                            if (myGeneration == searchGeneration) {
                                notifyMoreResults(fresh, hasMorePages);
                            }
                        });
                    }

                    @Override
                    public void onSearchError(@NonNull String error) {
                        if (myGeneration != searchGeneration) return;
                        isPaginationActive = false;
                        currentPage--; // Roll back so the user can retry
                        Log.w(TAG, "Pagination error page " + (currentPage + 1) + ": " + error);
                        notifyMoreResultsError(Error.network(error, null));
                    }

                    @Override
                    public void onSearchProgress(@NonNull String sourceId,
                                                 int completed, int total) {
                        // No per-source progress indicator for pagination
                    }
                });
    }

    /**
     * Re-enrich the currently displayed results against Room without re-searching.
     *
     * Thin facade over SearchCache.refreshFromDatabase(): MainActivity owns the
     * adapter list and SearchManager owns the SearchCache, so the activity routes
     * the refresh through here rather than reaching into the cache directly. Used by
     * MainActivity.onActivityResumed() to reflect detail-screen image edits on cards.
     *
     * @param items  The adapter's live result list (re-enriched in place)
     * @param onDone Run on the main thread when done (e.g. adapter.notifyDataSetChanged)
     */
    public void refreshDisplayedResults(@NonNull List<Searchable> items, @NonNull Runnable onDone) {
        searchCache.refreshFromDatabase(items, onDone);
    }

    /**
     * Cancel all in-flight and pending searches.
     * Does not clear state (currentQuery, pagination, etc.) - a subsequent
     * search() call for the same query can still proceed normally afterward.
     */
    public void cancel() {
        cancelAllSearches();
    }

    /**
     * Release all resources. Call from the owning Activity's onDestroy().
     */
    public void cleanup() {
        cancelAllSearches();
        listener = null;
        autocompleteListener = null;

        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, "SearchManager cleaned up. " + getSearchStats());
        }
    }

    // =========================================================================
    // STATE QUERIES
    // =========================================================================

    public boolean hasMoreResults()    { return hasMorePages && !currentQuery.isEmpty(); }
    public boolean isLoadingMore()     { return isPaginationActive; }
    public boolean isSearchInProgress() {
        return isSearchActive || pendingSearch != null
                || isPaginationActive || isAutocompleteActive;
    }
    public String getCurrentQuery()   { return currentQuery; }

    public String getSearchStats() {
        return String.format(
                "Searches=%d, Autocomplete=%d, CacheHits=%d, CacheRate=%.1f%%, LastSearch=%dms ago",
                searchCount, autocompleteCount, cacheHits,
                searchCount > 0 ? (cacheHits * 100.0 / searchCount) : 0.0,
                System.currentTimeMillis() - lastSearchTime);
    }

    // =========================================================================
    // PRIVATE - SEARCH EXECUTION
    // =========================================================================

    /**
     * Execute page-1 search for the given query.
     *
     * Flow:
     *   1. Check SearchResultCache - if hit, enrich and deliver immediately.
     *   2. If miss, call aggregator → enrich → deduplicate → deliver.
     *
     * Cache hits skip the aggregator entirely for instant results on repeated
     * queries within the same session.
     */
    private void performSearch(final String query) {
        // Cancel any previous in-flight aggregator call
        if (isSearchActive || isPaginationActive) {
            aggregator.cancelSearches();
            isPaginationActive = false;
            currentPage = 1;
        }

        isSearchActive = true;
        searchCount++;
        lastSearchTime = System.currentTimeMillis();

        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, "performSearch: '" + query + "' (search #" + searchCount + ")");
        }

        notifySearchLoading();

        // ── Cache check ───────────────────────────────────────────────────────
        // ProductRepository's SearchResultCache holds the enriched page-1 result.
        // If present, deliver immediately and mark all IDs as seen so pagination
        // deduplication still works correctly.
        List<Searchable> cached = searchCache.getCachedResults(query);
        if (cached != null) {
            cacheHits++;
            isSearchActive = false;

            // Register cached IDs for cross-page deduplication
            for (Searchable item : cached) {
                String id = item.getSearchableId();
                if (id != null) seenSearchableIds.add(id);
            }

            if (ApiConfig.DEBUG_LOGGING) {
                Log.d(TAG, "Cache hit: '" + query + "' → " + cached.size() + " items");
            }

            // We don't have a fresh hasMore signal from cache - assume true so
            // the user can still attempt pagination (loadMoreResults guards will
            // catch the case where the server has no more pages).
            notifySearchResults(cached, true);
            return;
        }

        // ── Live search via aggregator ────────────────────────────────────────
        // exhaustedSources is empty for page 1 (reset by resetSearchState).

        // Captured once, at call time - compared against the live field in
        // every callback below so a superseded call can never deliver.
        final int myGeneration = searchGeneration;

        // Running total of everything delivered via onPartialResult this
        // round. Each source used to replace the previous one's partial
        // outright (notifySearchResults -> adapter.updateItems is a full
        // list swap), so a fast source's results vanished the instant the
        // next source finished - visible as sources "disappearing" from the
        // feed whenever one source (e.g. OFF) was slow. All
        // AggregatorCallback methods are posted to the main thread by the
        // aggregator, so plain mutation here is safe - no synchronization.
        final List<Searchable> accumulatedPartials = new ArrayList<>();

        aggregator.searchAll(
                query,
                ApiConfig.API_PAGE_SIZE,
                1,
                Collections.emptySet(), // page 1: no sources exhausted yet
                activeFilter,
                new DataSourceAggregator.AggregatorCallback() {

                    @Override
                    public void onPartialResult(@NonNull String sourceId,
                                                @NonNull List<DataSource.SearchResult> partialResults) {
                        // A fast source (e.g. OFF) finished before the others.
                        // Show its results immediately for perceived speed.
                        if (partialResults.isEmpty()) return;

                        List<Searchable> partialItems = new ArrayList<>();
                        for (DataSource.SearchResult r : partialResults) {
                            partialItems.addAll(r.items);
                        }

                        // Apply active type filter to partial results.
                        // Source filter is already enforced upstream by DataSourceAggregator -
                        // only allowed sources are called. Type filter must be applied here
                        // since partial results arrive before the full aggregation completes.
                        if (activeFilter.isTypeFilterActive()) {
                            partialItems.removeIf(item ->
                                    !activeFilter.getAllowedTypes().contains(item.getProductType()));
                        }

                        // Apply scoring/filtering (same as full result path)
                        String lang = LanguageManager.getCurrentLanguage(context).getCode();
                        List<Searchable> filtered =
                                ResultPipeline
                                        .process(partialItems, query, lang);

                        if (!filtered.isEmpty()) {
                            // Accumulate onto whatever earlier sources already
                            // delivered this round, instead of replacing them.
                            accumulatedPartials.addAll(filtered);
                            List<Searchable> toDeliver = new ArrayList<>(accumulatedPartials);

                            // Enrich on background thread, then deliver the running
                            // total (no cache put yet - page 1 cache is written once,
                            // in onSearchComplete below).
                            searchCache.enrichAndCache(toDeliver, query, false, () -> {
                                // toDeliver.size() == accumulatedPartials.size() guards
                                // against a slower enrichAndCache callback for an
                                // earlier, smaller snapshot completing after a later,
                                // larger one - enrichAndCache is async, so completion
                                // order isn't guaranteed to match arrival order.
                                if (myGeneration == searchGeneration && !finalResultDelivered
                                        && toDeliver.size() == accumulatedPartials.size()) {
                                    notifySearchResults(toDeliver, true);
                                }
                            });
                        }
                    }

                    @Override
                    public void onSearchComplete(@NonNull AggregatedSearchResult result) {
                        isSearchActive = false;

                        if (myGeneration != searchGeneration) {
                            // A newer search started while this call was in flight -
                            // even for the identical query text, this call is
                            // superseded and must not deliver. It would otherwise
                            // dedup against IDs the newer call already registered
                            // and wipe its correctly-shown results down to empty).
                            if (ApiConfig.DEBUG_LOGGING) {
                                Log.d(TAG, "Discarding stale result for '" + query
                                        + "' (generation " + myGeneration + " != "
                                        + searchGeneration + ")");
                            }
                            return;
                        }

                        // Update pagination state from result
                        hasMorePages = result.hasMore();
                        updateExhaustedSources(result.getSourceHasMore());

                        // Apply scoring/filtering
                        String lang = LanguageManager.getCurrentLanguage(context).getCode();
                        List<Searchable> filtered =
                                ResultPipeline
                                        .process(result.getItems(), query, lang);

                        if (filtered.isEmpty()) {
                            Log.w(TAG, "All results filtered for: '" + query + "'");
                            notifySearchError(Error.noData("No results found for: " + query));
                            return;
                        }

                        // Level-2 deduplication and ID registration
                        List<Searchable> fresh = deduplicateAndRegister(filtered);

                        // Enrich on background thread AND cache (page 1 only).
                        // enrichAndCache is async - a newer search can start and
                        // bump searchGeneration in the gap before this completes,
                        // so re-check here even though onSearchComplete itself
                        // already passed the generation check synchronously above.
                        searchCache.enrichAndCache(fresh, query, true, () -> {
                            if (myGeneration == searchGeneration) {
                                notifySearchResults(fresh, hasMorePages);
                            }
                        });

                        if (ApiConfig.DEBUG_LOGGING) {
                            Log.d(TAG, "Search complete: '" + query + "' → "
                                    + fresh.size() + " items, hasMore=" + hasMorePages);
                        }
                    }

                    @Override
                    public void onSearchError(@NonNull String error) {
                        isSearchActive = false;
                        if (myGeneration == searchGeneration) {
                            Log.e(TAG, "Search error for '" + query + "': " + error);
                            notifySearchError(Error.network(error, null));
                        }
                    }

                    @Override
                    public void onSearchProgress(@NonNull String sourceId,
                                                 int completed, int total) {
                        if (ApiConfig.DEBUG_LOGGING) {
                            Log.d(TAG, "Search progress: " + sourceId
                                    + " (" + completed + "/" + total + ")");
                        }
                    }
                });
    }

    private void performAutocomplete(final String query) {
        isAutocompleteActive = true;
        autocompleteCount++;

        searchCache.autocomplete(query, suggestions -> {
            isAutocompleteActive = false;

            String lang = LanguageManager.getCurrentLanguage(context).getCode();
            List<String> names = new ArrayList<>();
            for (Searchable item : suggestions) {
                String name = item.getDisplayName(lang);
                if (name != null && !name.trim().isEmpty()) {
                    names.add(name);
                }
                if (names.size() >= 10) break;
            }

            if (ApiConfig.DEBUG_LOGGING) {
                Log.d(TAG, "Autocomplete '" + query + "': " + names.size() + " suggestions");
            }

            notifyAutocompleteSuggestions(names);
        });
    }

    // =========================================================================
    // PRIVATE - PAGINATION STATE MANAGEMENT
    // =========================================================================

    /**
     * Reset all pagination state for a new query.
     * Called at the start of search() and searchImmediate().
     */
    private void resetSearchState() {
        currentPage          = 1;
        hasMorePages         = true;
        finalResultDelivered = false;
        searchGeneration++;
        exhaustedSources.clear();
        seenSearchableIds.clear();
    }

    /**
     * Update exhaustedSources from a per-source hasMore map.
     * Sources reporting false are added; true sources are left in (or never added).
     *
     * @param sourceHasMore Map from AggregatedSearchResult.getSourceHasMore()
     */
    private void updateExhaustedSources(@NonNull Map<String, Boolean> sourceHasMore) {
        for (Map.Entry<String, Boolean> entry : sourceHasMore.entrySet()) {
            if (!Boolean.TRUE.equals(entry.getValue())) {
                // Source reported hasMore=false - exhausted for this query
                if (exhaustedSources.add(entry.getKey()) && ApiConfig.DEBUG_LOGGING) {
                    Log.d(TAG, "Source exhausted: " + entry.getKey());
                }
            }
        }
    }

    /**
     * Level-2 cross-page deduplication.
     *
     * Filters out any item whose searchableId was already delivered to the
     * listener in a previous page, then registers all remaining IDs.
     *
     * @param items Items from the current page (after level-1 SmartMergeStrategy)
     * @return Items not previously seen - safe to deliver to the listener
     */
    @NonNull
    private List<Searchable> deduplicateAndRegister(@NonNull List<Searchable> items) {
        List<Searchable> fresh = new ArrayList<>(items.size());
        for (Searchable item : items) {
            String id = item.getSearchableId();
            if (id == null || seenSearchableIds.add(id)) {
                // null IDs always pass through (edge case); new IDs are registered
                fresh.add(item);
            }
        }

        if (ApiConfig.DEBUG_LOGGING && fresh.size() < items.size()) {
            Log.d(TAG, "Level-2 dedup: dropped " + (items.size() - fresh.size())
                    + " cross-page duplicate(s)");
        }

        return fresh;
    }

    // =========================================================================
    // PRIVATE - CANCELLATION
    // =========================================================================

    private void cancelPendingSearch() {
        if (pendingSearch != null) {
            searchHandler.removeCallbacks(pendingSearch);
            pendingSearch = null;
        }
    }

    private void cancelPendingAutocomplete() {
        if (pendingAutocomplete != null) {
            autocompleteHandler.removeCallbacks(pendingAutocomplete);
            pendingAutocomplete = null;
        }
    }

    private void cancelAllSearches() {
        cancelPendingSearch();
        cancelPendingAutocomplete();

        if (isSearchActive || isPaginationActive || isAutocompleteActive) {
            aggregator.cancelSearches();
            isSearchActive      = false;
            isPaginationActive  = false;
            isAutocompleteActive = false;

            notifySearchCancelled();

            if (ApiConfig.DEBUG_LOGGING) Log.d(TAG, "All searches cancelled");
        }
    }

    // =========================================================================
    // PRIVATE - LISTENER NOTIFICATIONS  (all on main thread, null-safe)
    // =========================================================================

    private void notifySearchResults(@NonNull List<Searchable> results, boolean hasMore) {
        if (listener != null) {
            try { listener.onSearchResults(results, hasMore); }
            catch (Exception e) { Log.e(TAG, "onSearchResults callback error", e); }
        }
    }

    private void notifySearchError(@NonNull Error error) {
        if (listener != null) {
            try { listener.onSearchError(error); }
            catch (Exception e) { Log.e(TAG, "onSearchError callback error", e); }
        }
    }

    private void notifySearchLoading() {
        if (listener != null) {
            try { listener.onSearchLoading(); }
            catch (Exception e) { Log.e(TAG, "onSearchLoading callback error", e); }
        }
    }

    private void notifySearchEmpty() {
        if (listener != null) {
            try { listener.onSearchEmpty(); }
            catch (Exception e) { Log.e(TAG, "onSearchEmpty callback error", e); }
        }
    }

    private void notifyMoreResults(@NonNull List<Searchable> results, boolean hasMore) {
        if (listener != null) {
            try { listener.onMoreResults(results, hasMore); }
            catch (Exception e) { Log.e(TAG, "onMoreResults callback error", e); }
        }
    }

    private void notifyMoreResultsError(@NonNull Error error) {
        if (listener != null) {
            try { listener.onMoreResultsError(error); }
            catch (Exception e) { Log.e(TAG, "onMoreResultsError callback error", e); }
        }
    }

    private void notifyLoadingMore() {
        if (listener != null) {
            try { listener.onLoadingMore(); }
            catch (Exception e) { Log.e(TAG, "onLoadingMore callback error", e); }
        }
    }

    private void notifySearchCancelled() {
        if (listener != null) {
            try { listener.onSearchCancelled(); }
            catch (Exception e) { Log.e(TAG, "onSearchCancelled callback error", e); }
        }
    }

    private void notifyAutocompleteSuggestions(@NonNull List<String> suggestions) {
        if (autocompleteListener != null) {
            try { autocompleteListener.onAutocompleteSuggestions(suggestions); }
            catch (Exception e) { Log.e(TAG, "onAutocompleteSuggestions callback error", e); }
        }
    }

    private void notifyQueryTooShort() {
        if (autocompleteListener != null) {
            try { autocompleteListener.onQueryTooShort(); }
            catch (Exception e) { Log.e(TAG, "onQueryTooShort callback error", e); }
        }
    }
}