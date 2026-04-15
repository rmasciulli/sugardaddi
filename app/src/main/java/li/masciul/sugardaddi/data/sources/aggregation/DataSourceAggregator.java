package li.masciul.sugardaddi.data.sources.aggregation;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import li.masciul.sugardaddi.core.logging.ErrorLogger;
import li.masciul.sugardaddi.core.models.Error;
import li.masciul.sugardaddi.data.network.ApiConfig;
import li.masciul.sugardaddi.data.sources.base.DataSource;
import li.masciul.sugardaddi.data.sources.base.DataSourceCallback;
import li.masciul.sugardaddi.data.sources.config.DataSourceConfig;
import li.masciul.sugardaddi.managers.DataSourceManager;
import li.masciul.sugardaddi.managers.LanguageManager;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * DataSourceAggregator - Aggregates search results from multiple data sources
 *
 * REFACTORED v2.0 - Modern Error model integration
 *
 * This class enables parallel searching across multiple data sources,
 * merging results and handling timeouts/errors gracefully.
 *
 * KEY FEATURES:
 * - Parallel execution (searches sources simultaneously)
 * - Timeout handling (10 second per-source timeout)
 * - Result merging with deduplication
 * - Progress tracking
 * - Cancellation support
 * - Thread-safe operations
 *
 * ARCHITECTURE:
 * - Uses ExecutorService for parallel searches
 * - CountDownLatch for synchronization
 * - Main thread callback delivery
 * - Smart merge strategy for combining results
 *
 * USAGE:
 * ```java
 * DataSourceAggregator aggregator = new DataSourceAggregator(context);
 * aggregator.searchAll("banana", new AggregatorCallback() {
 *     @Override
 *     public void onSearchComplete(AggregatedSearchResult result) {
 *         // Handle merged results
 *     }
 *
 *     @Override
 *     public void onSearchError(String error) {
 *         // Handle error
 *     }
 *
 *     @Override
 *     public void onSearchProgress(String sourceId, int completed, int total) {
 *         // Update UI progress
 *     }
 * });
 * ```
 *
 * THREAD SAFETY:
 * - All callbacks delivered on main thread
 * - Internal state protected with atomic operations
 * - ConcurrentHashMap for result storage
 *
 * @author SugarDaddi Team
 * @version 2.0 (Error Model Refactor)
 */
public class DataSourceAggregator {

    private static final String TAG = "DataSourceAggregator";
    // 4s: enough for Ciqual Elasticsearch cold start (~1-3s), not painful to wait
    private static final int SEARCH_TIMEOUT_SECONDS = 10;

    // Dependencies
    private final Context context;
    private final DataSourceManager dataSourceManager;
    private final DataSourceConfig config;
    private final MergeStrategy mergeStrategy;

    // Thread management
    private final ExecutorService searchExecutor;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Current search tracking
    private final AtomicInteger activeSearches = new AtomicInteger(0);
    private volatile boolean cancelRequested = false;

    /**
     * Constructor
     *
     * @param context Application or Activity context
     */
    public DataSourceAggregator(Context context) {
        this.context = context.getApplicationContext();
        this.dataSourceManager = DataSourceManager.getInstance(context);
        this.config = new DataSourceConfig(context);
        this.mergeStrategy = new SmartMergeStrategy();
        this.searchExecutor = Executors.newCachedThreadPool();
    }

    /**
     * Callback for aggregated search results
     *
     * All methods are called on the main thread.
     */
    public interface AggregatorCallback {
        /**
         * Called when all searches complete and results are merged
         *
         * @param result Aggregated results from all sources
         */
        void onSearchComplete(AggregatedSearchResult result);

        /**
         * Called if aggregated search fails
         *
         * @param error Error message
         */
        void onSearchError(String error);

        /**
         * Called immediately when a single source returns results.
         * Use this to show partial results without waiting for all sources.
         *
         * @param sourceId Source that just completed (e.g. "openfoodfacts")
         * @param partialResults Products from that source only
         */
        default void onPartialResult(String sourceId, List<DataSource.SearchResult> partialResults) {}

        /**
         * Called as sources complete (progress tracking)
         *
         * @param sourceId Source that just completed
         * @param completedSources Number of sources completed
         * @param totalSources Total number of sources being searched
         */
        void onSearchProgress(String sourceId, int completedSources, int totalSources);
    }

    /**
     /**
     * Search all enabled sources with default limit
     *
     * @param query Search query
     * @param callback Results callback
     */
    public void searchAll(String query, AggregatorCallback callback) {
        searchAll(query, 20, 1, callback); // Default limit of 20 per source, page 1
    }

    /**
     * Search all enabled sources with specified limit
     *
     * Searches all enabled data sources in parallel, merges results,
     * and delivers them via callback on the main thread.
     *
     * @param query Search query
     * @param limit Maximum results per source
     * @param callback Results callback
     */
    public void searchAll(String query, int limit, AggregatorCallback callback) {
        searchAll(query, limit, 1, callback); // Page 1 by default
    }

    /**
     * Search all enabled sources with specified limit and page
     *
     * Searches all enabled data sources in parallel, merges results,
     * and delivers them via callback on the main thread.
     *
     * @param query Search query
     * @param limit Maximum results per source
     * @param page Page number (1-based)
     * @param callback Results callback
     */
    public void searchAll(String query, int limit, int page, AggregatorCallback callback) {
        if (callback == null) {
            Log.w(TAG, "Callback is null");
            return;
        }

        cancelRequested = false;
        String language = LanguageManager.getCurrentLanguage(context).getCode();

        List<DataSource> enabledSources = dataSourceManager.getEnabledDataSources();

        if (enabledSources.isEmpty()) {
            callback.onSearchError("No data sources available");
            return;
        }

        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, String.format("Starting aggregated search for '%s' across %d sources (page %d)",
                    query, enabledSources.size(), page));
        }

        long startTime = System.currentTimeMillis();
        activeSearches.set(enabledSources.size());

        // Track results and timing
        Map<String, DataSource.SearchResult> results = new ConcurrentHashMap<>();
        Map<String, Long> responseTimes = new ConcurrentHashMap<>();
        Map<String, String> errors = new ConcurrentHashMap<>();

        // Search each source in parallel
        for (DataSource source : enabledSources) {
            searchExecutor.submit(() -> {
                if (cancelRequested) return;

                String sourceId = source.getSourceId();
                long sourceStartTime = System.currentTimeMillis();

                try {
                    // Use a latch to wait for callback
                    CountDownLatch latch = new CountDownLatch(1);
                    final DataSource.SearchResult[] resultHolder = new DataSource.SearchResult[1];
                    final Error[] errorHolder = new Error[1];

                    source.search(query, language, limit, page, new DataSourceCallback<DataSource.SearchResult>() {
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
                            // Progress update
                        }
                    });

                    // Wait for result with timeout
                    if (latch.await(SEARCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                        long responseTime = System.currentTimeMillis() - sourceStartTime;
                        responseTimes.put(sourceId, responseTime);


                        if (resultHolder[0] != null) {
                            results.put(sourceId, resultHolder[0]);

                            if (ApiConfig.DEBUG_LOGGING) {
                                Log.d(TAG, String.format("Source %s returned %d results in %dms",
                                        sourceId, resultHolder[0].items.size(), responseTime));
                            }

                            // Fire partial result immediately so UI can show this source
                            // without waiting for remaining sources to finish
                            final DataSource.SearchResult partial = resultHolder[0];
                            mainHandler.post(() -> {
                                if (!cancelRequested) {
                                    List<DataSource.SearchResult> partials = new java.util.ArrayList<>();
                                    partials.add(partial);
                                    callback.onPartialResult(sourceId, partials);
                                }
                            });

                        } else if (errorHolder[0] != null) {
                            errors.put(sourceId, errorHolder[0].getMessage());
                            Log.w(TAG, "Source " + sourceId + " error: " + errorHolder[0].getMessage());

                            ErrorLogger.log(errorHolder[0], "During aggregated search from " + sourceId);

                        }
                    } else {
                        errors.put(sourceId, "Timeout");
                        Log.w(TAG, "Source " + sourceId + " timed out after " + SEARCH_TIMEOUT_SECONDS + "s");
                    }

                } catch (Exception e) {
                    errors.put(sourceId, e.getMessage());
                    Log.e(TAG, "Error searching source " + sourceId, e);

                    ErrorLogger.log(
                            Error.fromThrowable(e, "Aggregator search failed for " + sourceId),
                            "During parallel search execution"
                    );
                } finally {
                    int remaining = activeSearches.decrementAndGet();
                    int completed = enabledSources.size() - remaining;

                    // Progress callback on main thread
                    mainHandler.post(() -> {
                        callback.onSearchProgress(sourceId, completed, enabledSources.size());
                    });

                    // If all searches complete, merge results
                    if (remaining == 0 && !cancelRequested) {
                        mergeAndDeliverResults(
                                results,
                                errors,
                                responseTimes,
                                query,
                                language,
                                startTime,
                                callback
                        );
                    }
                }
            });
        }
    }

    /**
     * Cancel ongoing searches
     *
     * Cancels all active searches across all data sources.
     * Callbacks will not be delivered after cancellation.
     */
    public void cancelSearches() {
        cancelRequested = true;

        // Cancel all data sources
        for (DataSource source : dataSourceManager.getAllDataSources()) {
            source.cancelOperations();
        }

        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, "All searches cancelled");
        }
    }

    /**
     * Merge results from all sources and deliver to callback
     *
     * Uses MergeStrategy to combine results with deduplication.
     * Adds timing and error information to the final result.
     * Delivers on main thread.
     *
     * @param results Search results by source ID
     * @param errors Errors by source ID
     * @param responseTimes Response times by source ID
     * @param query Original search query
     * @param language Search language
     * @param startTime Aggregation start time
     * @param callback Results callback
     */
    private void mergeAndDeliverResults(Map<String, DataSource.SearchResult> results,
                                        Map<String, String> errors,
                                        Map<String, Long> responseTimes,
                                        String query,
                                        String language,
                                        long startTime,
                                        AggregatorCallback callback) {
        // Get source priorities for merge strategy
        Map<String, Integer> priorities = new HashMap<>();
        for (String sourceId : results.keySet()) {
            priorities.put(sourceId, config.getSourcePriority(sourceId));
        }

        // Merge results using strategy
        AggregatedSearchResult aggregated = mergeStrategy.merge(results, priorities);

        // Add error information to source stats
        Map<String, AggregatedSearchResult.SourceStats> enhancedStats = new HashMap<>();
        for (Map.Entry<String, AggregatedSearchResult.SourceStats> entry :
                aggregated.getSourceStats().entrySet()) {
            String sourceId = entry.getKey();
            AggregatedSearchResult.SourceStats stats = entry.getValue();

            enhancedStats.put(sourceId, new AggregatedSearchResult.SourceStats(
                    stats.itemCount,
                    responseTimes.getOrDefault(sourceId, 0L),
                    errors.get(sourceId)
            ));
        }

        // Add sources that errored out (no results)
        for (Map.Entry<String, String> error : errors.entrySet()) {
            if (!enhancedStats.containsKey(error.getKey())) {
                enhancedStats.put(error.getKey(), new AggregatedSearchResult.SourceStats(
                        0,
                        responseTimes.getOrDefault(error.getKey(), 0L),
                        error.getValue()
                ));
            }
        }

        // Create final result with all metadata
        long totalDuration = System.currentTimeMillis() - startTime;
        AggregatedSearchResult finalResult = new AggregatedSearchResult(
                aggregated.getItems(),
                enhancedStats,
                query,
                language,
                totalDuration,
                aggregated.getDuplicatesFound(),
                aggregated.getTotalItemsBeforeMerge()
        );

        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, "Aggregation complete:\n" + finalResult.getSummary());
        }

        // Deliver on main thread
        mainHandler.post(() -> {
            if (!cancelRequested) {
                callback.onSearchComplete(finalResult);
            }
        });
    }

    /**
     * Cleanup resources
     *
     * Cancels ongoing searches and shuts down thread pool.
     * Should be called when aggregator is no longer needed.
     */
    public void cleanup() {
        cancelSearches();
        searchExecutor.shutdown();
    }
}