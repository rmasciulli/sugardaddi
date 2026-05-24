package li.masciul.sugardaddi.data.sources.aggregation;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import li.masciul.sugardaddi.business.search.SearchFilter;
import li.masciul.sugardaddi.core.logging.ErrorLogger;
import li.masciul.sugardaddi.core.models.Error;
import li.masciul.sugardaddi.data.network.ApiConfig;
import li.masciul.sugardaddi.data.sources.base.DataSource;
import li.masciul.sugardaddi.data.sources.base.DataSourceCallback;
import li.masciul.sugardaddi.managers.DataSourceManager;
import li.masciul.sugardaddi.managers.LanguageManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * DataSourceAggregator — Parallel search across all active data sources.
 *
 * ARCHITECTURE v4.0 — Unified pipeline
 * ======================================
 * Single entry point: searchAll(query, limit, page, exhaustedSources, callback).
 * All previous overloads are gone — no backward compatibility kept.
 *
 * The aggregator is stateless across calls. All pagination state (current page,
 * exhausted sources, seen IDs) lives in SearchManager, which owns the search
 * lifecycle. The aggregator is a pure parallel executor: it fires sources,
 * waits, merges, and delivers.
 *
 * EXHAUSTED SOURCES
 * =================
 * SearchManager passes a Set<String> of source IDs that have already reported
 * hasMore=false for the current query. The aggregator skips those sources
 * entirely — they are not counted toward the active latch, so they add zero
 * latency. This is the correct fix for non-paginating sources (e.g. TheMealDB):
 * they self-exhaust on page 1 and are never called again for the same query.
 *
 * hasMore / sourceHasMore
 * =======================
 * mergeAndDeliver() computes both fields by scanning raw SearchResult objects
 * before passing them to SmartMergeStrategy:
 *
 *   sourceHasMore — Map<sourceId, Boolean>. false = exhausted for this query.
 *                   SearchManager reads this to update its exhaustedSources set.
 *
 *   hasMore       — true if any non-exhausted source still has pages.
 *                   SearchManager uses this to control hasMorePages / UI footer.
 *
 * Errored and timed-out sources are treated as exhausted (hasMore=false) —
 * no point retrying them on the next page for the same query.
 *
 * SEARCH FLOW
 * ===========
 * 1. Filter active sources: getActiveSources() minus exhaustedSources
 * 2. Fire each source's search() in parallel on searchExecutor
 * 3. Each source blocks on a CountDownLatch with SEARCH_TIMEOUT_SECONDS
 * 4. As each source finishes, fire onPartialResult on the main thread
 * 5. Once all sources finish (or time out), call mergeAndDeliver()
 * 6. mergeAndDeliver() computes hasMore/sourceHasMore, delegates to
 *    SmartMergeStrategy, patches in real timing, delivers AggregatedSearchResult
 *
 * CANCELLATION
 * ============
 * cancelSearches() sets cancelRequested and calls cancelOperations() on every
 * registered source. Callbacks are suppressed after cancellation.
 * Safe to call from any thread.
 *
 * THREADING
 * =========
 * - searchAll() may be called from any thread
 * - Source search() calls run on searchExecutor (cached thread pool)
 * - All AggregatorCallback methods are delivered on the main thread
 */
public class DataSourceAggregator {

    private static final String TAG = "DataSourceAggregator";

    /**
     * Per-source search timeout.
     * Long enough for Ciqual ES cold start (~1–3s), short enough not to block
     * the user indefinitely if a source is unreachable.
     */
    private static final int SEARCH_TIMEOUT_SECONDS = 10;

    // =========================================================================
    // DEPENDENCIES
    // =========================================================================

    private final Context context;
    private final DataSourceManager dataSourceManager;
    private final MergeStrategy mergeStrategy;
    private final ExecutorService searchExecutor;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // =========================================================================
    // CALL-SCOPED STATE  (reset at the top of each searchAll call)
    // =========================================================================

    /**
     * Tracks how many sources are still in flight for the current call.
     * Decremented in each source's finally block. When it hits 0, mergeAndDeliver
     * is invoked exactly once.
     */
    private final AtomicInteger activeSearches = new AtomicInteger(0);

    /**
     * Set to true by cancelSearches(). Checked before every main-thread post
     * and before mergeAndDeliver to suppress callbacks after cancellation.
     */
    private volatile boolean cancelRequested = false;

    // =========================================================================
    // CONSTRUCTOR
    // =========================================================================

    /**
     * @param context Application or Activity context (stored as applicationContext)
     */
    public DataSourceAggregator(@NonNull Context context) {
        this.context           = context.getApplicationContext();
        this.dataSourceManager = DataSourceManager.getInstance(context);
        this.mergeStrategy     = new SmartMergeStrategy();
        this.searchExecutor    = Executors.newCachedThreadPool();
    }

    // =========================================================================
    // PUBLIC API — single entry point
    // =========================================================================

    /**
     * Search all active, non-exhausted sources in parallel.
     *
     * PARAMETERS
     * ----------
     * @param query            Search string (min length enforced upstream by SearchManager)
     * @param limit            Max results requested per source per page
     * @param page             1-based page number — passed to each source's search()
     * @param exhaustedSources Source IDs to skip for this call. Pass
     *                         {@link Collections#emptySet()} for page 1.
     *                         SearchManager builds this from previous pages'
     *                         {@link AggregatedSearchResult#getSourceHasMore()}.
     * @param callback         All methods called on the main thread
     */
    public void searchAll(
            @NonNull String query,
            int limit,
            int page,
            @NonNull Set<String> exhaustedSources,
            @NonNull SearchFilter filter,
            @NonNull AggregatorCallback callback) {

        cancelRequested = false;

        final String language = LanguageManager.getCurrentLanguage(context).getCode();

        // ── 1. Build the active source list, applying exhaustion + user filter ────
        List<DataSource> allActive = dataSourceManager.getActiveSources();
        List<DataSource> sourcesToSearch = new ArrayList<>(allActive.size());

        for (DataSource source : allActive) {
            if (exhaustedSources.contains(source.getSourceId())) {
                if (ApiConfig.DEBUG_LOGGING) {
                    Log.d(TAG, "Skipping exhausted source: " + source.getSourceId());
                }
                continue;
            }
            if (!filter.allowsSource(source.getSourceId(), source.getProducedTypes())) {
                if (ApiConfig.DEBUG_LOGGING) {
                    Log.d(TAG, "Skipping filtered source: " + source.getSourceId()
                            + " (filter=" + filter + ")");
                }
                continue;
            }
            sourcesToSearch.add(source);
        }

        if (sourcesToSearch.isEmpty()) {
            if (ApiConfig.DEBUG_LOGGING) {
                Log.w(TAG, "searchAll: no non-exhausted sources available");
            }
            callback.onSearchError("No data sources available");
            return;
        }

        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, String.format(
                    "searchAll: '%s' page=%d limit=%d sources=%d exhausted=%s",
                    query, page, limit, sourcesToSearch.size(), exhaustedSources));
        }

        // ── 2. Initialise call-scoped state ───────────────────────────────────
        final long startTime = System.currentTimeMillis();

        // Set counter AFTER filtering so it matches the sources we'll actually wait for
        activeSearches.set(sourcesToSearch.size());

        // Thread-safe result containers — written by executor threads, read by finally block
        final Map<String, DataSource.SearchResult> results       = new ConcurrentHashMap<>();
        final Map<String, Long>                    responseTimes = new ConcurrentHashMap<>();
        final Map<String, String>                  errors        = new ConcurrentHashMap<>();

        // ── 3. Fire each source in parallel ──────────────────────────────────
        for (DataSource source : sourcesToSearch) {
            final String sourceId = source.getSourceId();

            searchExecutor.submit(() -> {
                if (cancelRequested) {
                    // Cancelled before this thread even started — skip and decrement
                    int remaining = activeSearches.decrementAndGet();
                    if (remaining == 0 && !cancelRequested) {
                        mergeAndDeliver(results, errors, responseTimes,
                                query, language, startTime, callback);
                    }
                    return;
                }

                final long sourceStart = System.currentTimeMillis();

                try {
                    // One latch per source — blocks this executor thread until the
                    // source calls onSuccess or onError (or we time out)
                    final CountDownLatch latch = new CountDownLatch(1);
                    final DataSource.SearchResult[] resultHolder = new DataSource.SearchResult[1];
                    final Error[]                   errorHolder  = new Error[1];

                    source.search(query, language, limit, page,
                            new DataSourceCallback<DataSource.SearchResult>() {
                                @Override
                                public void onSuccess(DataSource.SearchResult result) {
                                    resultHolder[0] = result;
                                    latch.countDown();
                                }

                                @Override
                                public void onError(Error error) {
                                    errorHolder[0] = error;
                                    latch.countDown();
                                }

                                @Override
                                public void onLoading() {
                                    // Progress signal only — latch stays open
                                }
                            });

                    final boolean finished =
                            latch.await(SEARCH_TIMEOUT_SECONDS, TimeUnit.SECONDS);

                    if (!finished) {
                        // Source did not respond within the timeout window
                        errors.put(sourceId,
                                "Timed out after " + SEARCH_TIMEOUT_SECONDS + "s");
                        Log.w(TAG, sourceId + " timed out");

                    } else if (errorHolder[0] != null) {
                        // Source responded with an error
                        errors.put(sourceId, errorHolder[0].getMessage());
                        if (ApiConfig.DEBUG_LOGGING) {
                            Log.w(TAG, sourceId + " error: " + errorHolder[0].getMessage());
                        }

                    } else if (resultHolder[0] != null) {
                        // Source responded successfully
                        results.put(sourceId, resultHolder[0]);
                        responseTimes.put(sourceId, System.currentTimeMillis() - sourceStart);

                        if (ApiConfig.DEBUG_LOGGING) {
                            Log.d(TAG, String.format("%s: %d item(s) in %dms, hasMore=%b",
                                    sourceId,
                                    resultHolder[0].getItemCount(),
                                    System.currentTimeMillis() - sourceStart,
                                    resultHolder[0].hasMore));
                        }

                        // Fire partial result on main thread immediately — the UI can show
                        // the first fast source (e.g. OFF) without waiting for slower ones
                        if (!cancelRequested) {
                            final DataSource.SearchResult partial = resultHolder[0];
                            mainHandler.post(() -> {
                                if (!cancelRequested) {
                                    callback.onPartialResult(sourceId,
                                            Collections.singletonList(partial));
                                }
                            });
                        }
                    }

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    errors.put(sourceId, "Interrupted");

                } catch (Exception e) {
                    errors.put(sourceId, e.getMessage());
                    Log.e(TAG, "Exception searching " + sourceId, e);
                    ErrorLogger.log(
                            Error.fromThrowable(e, "Aggregator search failed for " + sourceId),
                            "DataSourceAggregator.searchAll");

                } finally {
                    final int remaining = activeSearches.decrementAndGet();
                    final int completed = sourcesToSearch.size() - remaining;

                    // Progress update — always delivered, even on error/timeout
                    mainHandler.post(() ->
                            callback.onSearchProgress(sourceId, completed,
                                    sourcesToSearch.size()));

                    // Last source finished — merge and deliver the final result
                    if (remaining == 0 && !cancelRequested) {
                        mergeAndDeliver(results, errors, responseTimes,
                                query, language, startTime, callback);
                    }
                }
            });
        }
    }

    // =========================================================================
    // CANCELLATION
    // =========================================================================

    /**
     * Cancel any in-flight searchAll call.
     *
     * Sets cancelRequested so that pending main-thread posts and mergeAndDeliver
     * are suppressed. Also calls cancelOperations() on every registered source
     * (not just active ones — a source may have just become active mid-search).
     *
     * Safe to call from any thread.
     */
    public void cancelSearches() {
        cancelRequested = true;
        for (DataSource source : dataSourceManager.getAllDataSources()) {
            source.cancelOperations();
        }
        if (ApiConfig.DEBUG_LOGGING) Log.d(TAG, "All searches cancelled");
    }

    // =========================================================================
    // CLEANUP
    // =========================================================================

    /**
     * Shut down the executor. Call when the owning component is destroyed.
     * After this call, searchAll() must not be used.
     */
    public void cleanup() {
        cancelSearches();
        searchExecutor.shutdown();
    }

    // =========================================================================
    // MERGE & DELIVER
    // =========================================================================

    /**
     * Called exactly once per searchAll() call, after all sources have finished
     * (successfully, with error, or timed out).
     *
     * Responsibilities:
     *   1. Compute sourceHasMore and hasMore from raw SearchResult objects
     *   2. Build the equal-priority map for SmartMergeStrategy
     *   3. Delegate to SmartMergeStrategy for FoodProduct deduplication
     *   4. Patch real response times and error messages into SourceStats
     *   5. Construct and deliver the final AggregatedSearchResult on the main thread
     */
    private void mergeAndDeliver(
            @NonNull Map<String, DataSource.SearchResult> results,
            @NonNull Map<String, String>                  errors,
            @NonNull Map<String, Long>                    responseTimes,
            @NonNull String query,
            @NonNull String language,
            long startTime,
            @NonNull AggregatorCallback callback) {

        try {
            // ── Step 1: Compute per-source hasMore flags ──────────────────────
            // Done before merge because SmartMergeStrategy doesn't surface this.
            // Sources that errored or timed out are treated as exhausted — no
            // benefit in retrying them for the same query on the next page.
            final Map<String, Boolean> sourceHasMore = new HashMap<>();
            boolean anyHasMore = false;

            for (Map.Entry<String, DataSource.SearchResult> entry : results.entrySet()) {
                final boolean more = entry.getValue().hasMore;
                sourceHasMore.put(entry.getKey(), more);
                if (more) anyHasMore = true;
            }

            // Mark errored/timed-out sources as exhausted
            for (String erroredId : errors.keySet()) {
                sourceHasMore.put(erroredId, false);
            }

            // ── Step 2: Build equal-priority map for SmartMergeStrategy ──────
            // Priority is no longer user-configurable. All sources get equal weight;
            // SmartMergeStrategy uses dataCompleteness to pick the winning item in
            // each duplicate group.
            final Map<String, Integer> equalPriorities = new HashMap<>();
            for (String sourceId : results.keySet()) {
                equalPriorities.put(sourceId, 50);
            }

            // ── Step 3: Merge (level-1 FoodProduct deduplication) ────────────
            final AggregatedSearchResult merged =
                    mergeStrategy.merge(results, equalPriorities);

            // ── Step 4: Patch real timing and errors into SourceStats ─────────
            final Map<String, AggregatedSearchResult.SourceStats> enhancedStats =
                    new HashMap<>();

            for (Map.Entry<String, AggregatedSearchResult.SourceStats> entry :
                    merged.getSourceStats().entrySet()) {
                final String sourceId = entry.getKey();
                final AggregatedSearchResult.SourceStats stat = entry.getValue();
                enhancedStats.put(sourceId, new AggregatedSearchResult.SourceStats(
                        stat.itemCount,
                        responseTimes.getOrDefault(sourceId, 0L),
                        errors.get(sourceId)   // null if source succeeded
                ));
            }

            // Add entries for sources that errored and produced no items at all
            for (Map.Entry<String, String> err : errors.entrySet()) {
                if (!enhancedStats.containsKey(err.getKey())) {
                    enhancedStats.put(err.getKey(), new AggregatedSearchResult.SourceStats(
                            0,
                            responseTimes.getOrDefault(err.getKey(), 0L),
                            err.getValue()
                    ));
                }
            }

            // ── Step 5: Build and deliver final result ────────────────────────
            final long totalDuration = System.currentTimeMillis() - startTime;

            final AggregatedSearchResult finalResult = new AggregatedSearchResult(
                    merged.getItems(),
                    enhancedStats,
                    query,
                    language,
                    totalDuration,
                    merged.getDuplicatesFound(),
                    merged.getTotalItemsBeforeMerge(),
                    anyHasMore,      // true if any source still has pages
                    sourceHasMore    // per-source exhaustion map for SearchManager
            );

            if (ApiConfig.DEBUG_LOGGING) {
                Log.d(TAG, "Aggregation complete:\n" + finalResult.getSummary());
            }

            mainHandler.post(() -> {
                if (!cancelRequested) callback.onSearchComplete(finalResult);
            });

        } catch (Exception e) {
            Log.e(TAG, "mergeAndDeliver failed", e);
            mainHandler.post(() -> {
                if (!cancelRequested) {
                    callback.onSearchError("Failed to merge results: " + e.getMessage());
                }
            });
        }
    }

    // =========================================================================
    // CALLBACK INTERFACE
    // =========================================================================

    /**
     * Callback for aggregated search results.
     *
     * All methods are delivered on the main thread.
     */
    public interface AggregatorCallback {

        /**
         * Called once all sources have responded and results are merged.
         *
         * @param result Combined, deduplicated, scored results. Contains hasMore
         *               and sourceHasMore for SearchManager's pagination bookkeeping.
         */
        void onSearchComplete(@NonNull AggregatedSearchResult result);

        /**
         * Called if the aggregation itself fails (e.g. no sources available).
         * Individual source errors are silently folded into SourceStats — they
         * do not trigger this method.
         */
        void onSearchError(@NonNull String error);

        /**
         * Called immediately when one source finishes, before the others.
         * Use this to show partial results early — the UI doesn't need to wait
         * for the slowest source before displaying anything.
         *
         * Default no-op so callers that don't need early results can ignore it.
         *
         * @param sourceId       The source that just responded
         * @param partialResults Singleton list containing that source's SearchResult
         */
        default void onPartialResult(
                @NonNull String sourceId,
                @NonNull List<DataSource.SearchResult> partialResults) {}

        /**
         * Called as each source completes (success, error, or timeout).
         * Use for progress indicators.
         *
         * @param sourceId         Source that just finished
         * @param completedSources Running count of finished sources this call
         * @param totalSources     Total sources participating this call
         */
        void onSearchProgress(
                @NonNull String sourceId,
                int completedSources,
                int totalSources);
    }
}