package li.masciul.sugardaddi.data.sources.aggregation;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import li.masciul.sugardaddi.core.logging.ErrorLogger;
import li.masciul.sugardaddi.core.models.Error;
import li.masciul.sugardaddi.data.network.ApiConfig;
import li.masciul.sugardaddi.data.sources.base.DataSource;
import li.masciul.sugardaddi.data.sources.base.DataSourceCallback;
import li.masciul.sugardaddi.managers.DataSourceManager;
import li.masciul.sugardaddi.managers.LanguageManager;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * DataSourceAggregator — Parallel search across all active data sources.
 *
 * ARCHITECTURE v3.0 — Settings refactor
 * ======================================
 * REMOVED: DataSourceConfig dependency.
 *   The former constructor created a {@code DataSourceConfig} instance to filter
 *   enabled sources. Now the manager's {@link DataSourceManager#getActiveSources()}
 *   is called at search time — it returns only sources that are both enabled
 *   (per each source's own SharedPreferences) and fully initialised.
 *
 * Everything else is unchanged: parallel execution, CountDownLatch timeout,
 * SmartMergeStrategy deduplication, main-thread callback delivery.
 *
 * SEARCH FLOW
 * ===========
 * 1. Get active sources from DataSourceManager (enabled + initialised, A→Z)
 * 2. Fire each source's search() in parallel on searchExecutor
 * 3. Wait up to SEARCH_TIMEOUT_SECONDS per source via CountDownLatch
 * 4. Once all sources finish (or time out), merge results via SmartMergeStrategy
 * 5. Deliver AggregatedSearchResult on the main thread
 *
 * CANCELLATION
 * ============
 * cancelSearches() sets the cancelRequested flag and calls cancelOperations()
 * on every registered source (not just active ones — a source might have just
 * become active mid-search).
 */
public class DataSourceAggregator {

    private static final String TAG = "DataSourceAggregator";

    /**
     * Per-source search timeout. Long enough for Ciqual ES cold start (~1–3s),
     * short enough not to make the user wait indefinitely.
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
    // SEARCH STATE  (reset on each call)
    // =========================================================================

    private final AtomicInteger activeSearches = new AtomicInteger(0);
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
    // PUBLIC SEARCH API
    // =========================================================================

    /**
     * Search all active sources with default limit (20) and page 1.
     */
    public void searchAll(@NonNull String query,
                          @NonNull AggregatorCallback callback) {
        searchAll(query, 20, 1, callback);
    }

    /**
     * Search all active sources with a custom limit, page 1.
     */
    public void searchAll(@NonNull String query,
                          int limit,
                          @NonNull AggregatorCallback callback) {
        searchAll(query, limit, 1, callback);
    }

    /**
     * Search all active sources with custom limit and page.
     *
     * "Active" = enabled by the user AND fully initialised.
     * The set is evaluated at call time so a source toggled between calls
     * is immediately included or excluded.
     *
     * @param query    Search string
     * @param limit    Max results requested per source
     * @param page     1-based page number
     * @param callback Receives progress, partial, and final results on main thread
     */
    public void searchAll(@NonNull String query,
                          int limit,
                          int page,
                          @NonNull AggregatorCallback callback) {
        cancelRequested = false;

        String language = LanguageManager.getCurrentLanguage(context).getCode();

        // Ask the manager for the current active set — no DataSourceConfig involved
        List<DataSource> activeSources = dataSourceManager.getActiveSources();

        if (activeSources.isEmpty()) {
            if (ApiConfig.DEBUG_LOGGING) Log.w(TAG, "No active sources available for search");
            callback.onSearchError("No data sources available");
            return;
        }

        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, String.format("Aggregated search: '%s' across %d source(s) (page %d)",
                    query, activeSources.size(), page));
        }

        long startTime = System.currentTimeMillis();
        activeSearches.set(activeSources.size());

        // Thread-safe containers for per-source results
        Map<String, DataSource.SearchResult> results       = new ConcurrentHashMap<>();
        Map<String, Long>                    responseTimes = new ConcurrentHashMap<>();
        Map<String, String>                  errors        = new ConcurrentHashMap<>();

        // Fire each source in parallel
        for (DataSource source : activeSources) {
            final String sourceId = source.getSourceId();

            searchExecutor.submit(() -> {
                if (cancelRequested) return;

                long sourceStart = System.currentTimeMillis();

                try {
                    CountDownLatch latch = new CountDownLatch(1);
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
                                public void onLoading() { /* progress only */ }
                            });

                    // Block until the source replies or we time out
                    boolean finished = latch.await(SEARCH_TIMEOUT_SECONDS, TimeUnit.SECONDS);

                    if (!finished) {
                        errors.put(sourceId, "Timed out after " + SEARCH_TIMEOUT_SECONDS + "s");
                        if (ApiConfig.DEBUG_LOGGING) {
                            Log.w(TAG, sourceId + " timed out after " + SEARCH_TIMEOUT_SECONDS + "s");
                        }
                    } else if (errorHolder[0] != null) {
                        errors.put(sourceId, errorHolder[0].getMessage());
                        if (ApiConfig.DEBUG_LOGGING) {
                            Log.w(TAG, sourceId + " returned error: " + errorHolder[0].getMessage());
                        }
                    } else if (resultHolder[0] != null) {
                        results.put(sourceId, resultHolder[0]);
                        responseTimes.put(sourceId, System.currentTimeMillis() - sourceStart);

                        if (ApiConfig.DEBUG_LOGGING) {
                            Log.d(TAG, sourceId + " returned "
                                    + resultHolder[0].getItemCount() + " result(s) in "
                                    + (System.currentTimeMillis() - sourceStart) + "ms");
                        }

                        // Deliver partial results immediately so the UI doesn't wait
                        // for all sources before showing anything
                        if (!cancelRequested) {
                            java.util.List<DataSource.SearchResult> partial =
                                    java.util.Collections.singletonList(resultHolder[0]);
                            mainHandler.post(() ->
                                    callback.onPartialResult(sourceId, partial));
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
                    int remaining  = activeSearches.decrementAndGet();
                    int completed  = activeSources.size() - remaining;

                    // Progress update on main thread
                    mainHandler.post(() ->
                            callback.onSearchProgress(sourceId, completed, activeSources.size()));

                    // All sources done — merge and deliver
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
     * Cancel any in-flight searches.
     * Callbacks will NOT be delivered after this call.
     * Safe to call from any thread.
     */
    public void cancelSearches() {
        cancelRequested = true;
        // Cancel all sources (not just active — one may have just become active)
        for (DataSource source : dataSourceManager.getAllDataSources()) {
            source.cancelOperations();
        }
        if (ApiConfig.DEBUG_LOGGING) Log.d(TAG, "All searches cancelled");
    }

    // =========================================================================
    // MERGE & DELIVER
    // =========================================================================

    private void mergeAndDeliver(
            @NonNull Map<String, DataSource.SearchResult> results,
            @NonNull Map<String, String>                  errors,
            @NonNull Map<String, Long>                    responseTimes,
            @NonNull String query,
            @NonNull String language,
            long     startTime,
            @NonNull AggregatorCallback callback) {

        try {
            // Build a neutral equal-priority map — DataSourceConfig is gone, sources are
            // ordered alphabetically by DataSourceManager; priority is no longer user-adjustable.
            Map<String, Integer> equalPriorities = new java.util.HashMap<>();
            for (String sourceId : results.keySet()) {
                equalPriorities.put(sourceId, 50); // neutral equal weight for all sources
            }

            // Delegate deduplication and ranking to the merge strategy.
            // merge() returns an AggregatedSearchResult with a Map<String, SourceStats>
            // already populated from the raw results — we then patch in real timing + errors.
            AggregatedSearchResult merged = mergeStrategy.merge(results, equalPriorities);

            // Rebuild the stats map with real response times and any error messages.
            // AggregatedSearchResult.getSourceStats() returns Map<String, SourceStats>.
            Map<String, AggregatedSearchResult.SourceStats> enhancedStats =
                    new java.util.HashMap<>();

            // Patch timing into stats that the merge strategy already created
            for (Map.Entry<String, AggregatedSearchResult.SourceStats> entry :
                    merged.getSourceStats().entrySet()) {
                String sourceId = entry.getKey();
                AggregatedSearchResult.SourceStats stat = entry.getValue();
                enhancedStats.put(sourceId, new AggregatedSearchResult.SourceStats(
                        stat.itemCount,
                        responseTimes.getOrDefault(sourceId, 0L),
                        errors.get(sourceId)         // null if no error for this source
                ));
            }

            // Add entries for sources that errored out and produced no results at all
            for (Map.Entry<String, String> err : errors.entrySet()) {
                if (!enhancedStats.containsKey(err.getKey())) {
                    enhancedStats.put(err.getKey(), new AggregatedSearchResult.SourceStats(
                            0,
                            responseTimes.getOrDefault(err.getKey(), 0L),
                            err.getValue()
                    ));
                }
            }

            long totalDuration = System.currentTimeMillis() - startTime;

            // AggregatedSearchResult constructor: (items, Map<String,SourceStats>,
            //   query, language, searchDurationMs, duplicatesFound, totalItemsBeforeMerge)
            AggregatedSearchResult finalResult = new AggregatedSearchResult(
                    merged.getItems(),
                    enhancedStats,
                    query,
                    language,
                    totalDuration,
                    merged.getDuplicatesFound(),
                    merged.getTotalItemsBeforeMerge()
            );

            if (ApiConfig.DEBUG_LOGGING) {
                Log.d(TAG, "Aggregation complete:\n" + finalResult.getSummary());
            }

            mainHandler.post(() -> {
                if (!cancelRequested) callback.onSearchComplete(finalResult);
            });

        } catch (Exception e) {
            Log.e(TAG, "Merge failed", e);
            mainHandler.post(() ->
                    callback.onSearchError("Failed to merge results: " + e.getMessage()));
        }
    }

    // =========================================================================
    // CLEANUP
    // =========================================================================

    public void cleanup() {
        cancelSearches();
        searchExecutor.shutdown();
    }

    // =========================================================================
    // CALLBACK INTERFACE
    // =========================================================================

    /**
     * Callback for aggregated search results.
     * All methods are called on the main thread.
     */
    public interface AggregatorCallback {

        /**
         * Called once all sources have responded and results are merged.
         * @param result Combined, deduplicated results from all sources
         */
        void onSearchComplete(@NonNull AggregatedSearchResult result);

        /**
         * Called if the aggregation itself fails (not individual source errors).
         * Individual source errors are silently folded into the stats.
         */
        void onSearchError(@NonNull String error);

        /**
         * Called immediately when one source returns results.
         * Use this to show partial results without waiting for all sources.
         *
         * @param sourceId      The source that just responded
         * @param partialResults Its individual SearchResult (wrapped in a list)
         */
        default void onPartialResult(@NonNull String sourceId,
                                     @NonNull List<DataSource.SearchResult> partialResults) {}

        /**
         * Called as each source completes (for progress indicators).
         *
         * @param sourceId         Source that just finished
         * @param completedSources Running count of finished sources
         * @param totalSources     Total sources in this search
         */
        void onSearchProgress(@NonNull String sourceId,
                              int completedSources,
                              int totalSources);
    }
}