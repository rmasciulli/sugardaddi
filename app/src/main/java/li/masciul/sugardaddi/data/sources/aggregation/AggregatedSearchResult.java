package li.masciul.sugardaddi.data.sources.aggregation;

import androidx.annotation.NonNull;

import li.masciul.sugardaddi.core.interfaces.Searchable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AggregatedSearchResult - Container for results merged from all active data sources.
 *
 * PAGINATION AWARENESS (v4.0)
 * ===========================
 * Two new fields support the pagination refactor:
 *
 *   hasMore          - true if at least one source still has pages beyond this one.
 *                      SearchManager reads this to update its own hasMorePages flag
 *                      instead of guessing from items.size() >= PAGE_SIZE.
 *
 *   sourceHasMore    - per-source exhaustion map (sourceId → hasMore).
 *                      SearchManager adds exhausted sources (value=false) to its
 *                      exhaustedSources set so the aggregator skips them on the
 *                      next page call.
 *
 * Both are computed by DataSourceAggregator.mergeAndDeliver() by scanning the
 * raw SearchResult objects before SmartMergeStrategy processes them.
 *
 * Everything else is unchanged from v3.0.
 */
public class AggregatedSearchResult {

    // =========================================================================
    // FIELDS
    // =========================================================================

    /** Merged, deduplicated, scored list of Searchable items (FoodProduct + Recipe). */
    private final List<Searchable> items;

    /** Per-source statistics for logging and diagnostics. */
    private final Map<String, SourceStats> sourceStats;

    /** Original search query. */
    private final String query;

    /** BCP-47 language code used for this search. */
    private final String language;

    /** Wall-clock time from first source call to final merge, in milliseconds. */
    private final long searchDurationMs;

    /** Number of cross-source duplicate FoodProduct items that were merged away. */
    private final int duplicatesFound;

    /** Total items received from all sources before deduplication. */
    private final int totalItemsBeforeMerge;

    /**
     * True if at least one source still has more pages available beyond this result.
     *
     * Sources that return all results at once (e.g. TheMealDB) set hasMore=false
     * on their SearchResult. Paginated sources (OFF, Ciqual) set hasMore=true
     * while their server-side result set isn't exhausted.
     *
     * SearchManager uses this to decide whether to show the pagination footer
     * and whether to allow loadMoreResults().
     */
    private final boolean hasMore;

    /**
     * Per-source exhaustion map: sourceId → true if that source has more pages.
     *
     * SearchManager reads this after each page to update its exhaustedSources set.
     * Sources mapped to false are skipped on subsequent searchAll() calls for the
     * same query, preventing repeated calls to non-paginating sources.
     */
    private final Map<String, Boolean> sourceHasMore;

    // =========================================================================
    // CONSTRUCTOR
    // =========================================================================

    /**
     * Full constructor. Called by DataSourceAggregator.mergeAndDeliver() only.
     *
     * @param items               Merged result list
     * @param sourceStats         Per-source count/timing/error stats
     * @param query               Search query
     * @param language            Language code
     * @param searchDurationMs    Total aggregation wall time
     * @param duplicatesFound     Items removed by cross-source deduplication
     * @param totalItemsBeforeMerge Raw item count before merge
     * @param hasMore             True if any source has further pages
     * @param sourceHasMore       Per-source hasMore flags for exhaustion tracking
     */
    public AggregatedSearchResult(
            @NonNull List<Searchable> items,
            @NonNull Map<String, SourceStats> sourceStats,
            @NonNull String query,
            @NonNull String language,
            long searchDurationMs,
            int duplicatesFound,
            int totalItemsBeforeMerge,
            boolean hasMore,
            @NonNull Map<String, Boolean> sourceHasMore) {

        this.items                = items;
        this.sourceStats          = sourceStats;
        this.query                = query;
        this.language             = language;
        this.searchDurationMs     = searchDurationMs;
        this.duplicatesFound      = duplicatesFound;
        this.totalItemsBeforeMerge = totalItemsBeforeMerge;
        this.hasMore              = hasMore;
        this.sourceHasMore        = sourceHasMore;
    }

    // =========================================================================
    // GETTERS
    // =========================================================================

    /** Merged, scored list of all Searchable items from all sources. */
    @NonNull
    public List<Searchable> getItems() { return items; }

    /** Per-source count/timing/error breakdown. */
    @NonNull
    public Map<String, SourceStats> getSourceStats() { return sourceStats; }

    public String getQuery() { return query; }
    public String getLanguage() { return language; }
    public long getSearchDurationMs() { return searchDurationMs; }
    public int getDuplicatesFound() { return duplicatesFound; }
    public int getTotalItemsBeforeMerge() { return totalItemsBeforeMerge; }

    /**
     * True if at least one active source has more pages available.
     * SearchManager uses this to control pagination UI and loadMoreResults().
     */
    public boolean hasMore() { return hasMore; }

    /**
     * Per-source exhaustion map for SearchManager's exhaustedSources set.
     * Key: sourceId. Value: true = source has more pages, false = exhausted.
     */
    @NonNull
    public Map<String, Boolean> getSourceHasMore() { return sourceHasMore; }

    // =========================================================================
    // HELPERS
    // =========================================================================

    /**
     * Filter items to those from a specific source.
     * Works uniformly for FoodProduct and Recipe via Searchable.getDataSource().
     *
     * @param sourceId e.g. "OPENFOODFACTS", "CIQUAL", "THEMEALDB"
     * @return Items from that source, in their merged order
     */
    @NonNull
    public List<Searchable> getItemsFromSource(@NonNull String sourceId) {
        List<Searchable> result = new ArrayList<>();
        for (Searchable item : items) {
            if (item.getDataSource() != null
                    && sourceId.equals(item.getDataSource().getId())) {
                result.add(item);
            }
        }
        return result;
    }

    /**
     * Human-readable summary for debug logging.
     */
    @NonNull
    public String getSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Query: '%s' in %s\n", query, language));
        sb.append(String.format("Total results: %d (from %d before merge)\n",
                items.size(), totalItemsBeforeMerge));
        sb.append(String.format("Duplicates merged: %d\n", duplicatesFound));
        sb.append(String.format("Search time: %dms\n", searchDurationMs));
        sb.append(String.format("Has more pages: %b\n", hasMore));

        sb.append("\nSource breakdown:\n");
        for (Map.Entry<String, SourceStats> entry : sourceStats.entrySet()) {
            SourceStats stats = entry.getValue();
            boolean sourceMore = Boolean.TRUE.equals(sourceHasMore.get(entry.getKey()));
            sb.append(String.format("  %s: %d items, %dms, hasMore=%b%s\n",
                    entry.getKey(),
                    stats.itemCount,
                    stats.responseTimeMs,
                    sourceMore,
                    stats.error != null ? " (ERROR: " + stats.error + ")" : ""));
        }

        return sb.toString();
    }

    // =========================================================================
    // NESTED: SourceStats
    // =========================================================================

    /**
     * Per-source statistics snapshot.
     * Populated by DataSourceAggregator after patching in real response times.
     */
    public static class SourceStats {

        /** Number of items this source contributed to the result. */
        public final int itemCount;

        /** Wall-clock time this source took to respond, in milliseconds. */
        public final long responseTimeMs;

        /**
         * Error message if this source failed, null if it succeeded.
         * Errors are non-fatal - other sources' results are still delivered.
         */
        public final String error;

        public SourceStats(int itemCount, long responseTimeMs, String error) {
            this.itemCount     = itemCount;
            this.responseTimeMs = responseTimeMs;
            this.error         = error;
        }
    }
}