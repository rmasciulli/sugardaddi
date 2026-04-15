package li.masciul.sugardaddi.data.sources.aggregation;

import li.masciul.sugardaddi.core.models.FoodProduct;
import java.util.*;

/**
 * Container for aggregated search results from multiple sources
 */
public class AggregatedSearchResult {

    private final List<FoodProduct> items;
    private final Map<String, SourceStats> sourceStats;
    private final String query;
    private final String language;
    private final long searchDurationMs;
    private final int duplicatesFound;
    private final int totalItemsBeforeMerge;

    public AggregatedSearchResult(List<FoodProduct> items,
                                  Map<String, SourceStats> sourceStats,
                                  String query,
                                  String language,
                                  long searchDurationMs,
                                  int duplicatesFound,
                                  int totalItemsBeforeMerge) {
        this.items = items;
        this.sourceStats = sourceStats;
        this.query = query;
        this.language = language;
        this.searchDurationMs = searchDurationMs;
        this.duplicatesFound = duplicatesFound;
        this.totalItemsBeforeMerge = totalItemsBeforeMerge;
    }

    // Getters
    public List<FoodProduct> getItems() { return items; }
    public Map<String, SourceStats> getSourceStats() { return sourceStats; }
    public String getQuery() { return query; }
    public String getLanguage() { return language; }
    public long getSearchDurationMs() { return searchDurationMs; }
    public int getDuplicatesFound() { return duplicatesFound; }
    public int getTotalItemsBeforeMerge() { return totalItemsBeforeMerge; }

    /**
     * Get items from a specific source
     */
    public List<FoodProduct> getItemsFromSource(String sourceId) {
        List<FoodProduct> sourceItems = new ArrayList<>();
        for (FoodProduct item : items) {
            if (sourceId.equals(item.getSourceIdentifier().getSourceId())) {
                sourceItems.add(item);
            }
        }
        return sourceItems;
    }

    /**
     * Get summary statistics
     */
    public String getSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append(String.format("Query: '%s' in %s\n", query, language));
        summary.append(String.format("Total results: %d (from %d before merge)\n",
                items.size(), totalItemsBeforeMerge));
        summary.append(String.format("Duplicates merged: %d\n", duplicatesFound));
        summary.append(String.format("Search time: %dms\n", searchDurationMs));

        summary.append("\nSource breakdown:\n");
        for (Map.Entry<String, SourceStats> entry : sourceStats.entrySet()) {
            SourceStats stats = entry.getValue();
            summary.append(String.format("  %s: %d items, %dms%s\n",
                    entry.getKey(),
                    stats.itemCount,
                    stats.responseTimeMs,
                    stats.error != null ? " (ERROR: " + stats.error + ")" : ""
            ));
        }

        return summary.toString();
    }

    /**
     * Statistics for each source
     */
    public static class SourceStats {
        public final int itemCount;
        public final long responseTimeMs;
        public final String error;

        public SourceStats(int itemCount, long responseTimeMs, String error) {
            this.itemCount = itemCount;
            this.responseTimeMs = responseTimeMs;
            this.error = error;
        }
    }
}