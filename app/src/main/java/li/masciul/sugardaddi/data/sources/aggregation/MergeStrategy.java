package li.masciul.sugardaddi.data.sources.aggregation;

import li.masciul.sugardaddi.data.sources.base.DataSource;
import li.masciul.sugardaddi.core.models.FoodProduct;

import java.util.Map;

/**
 * Strategy interface for merging results from multiple sources
 */
public interface MergeStrategy {

    /**
     * Merge results from multiple data sources
     *
     * @param resultsBySource Map of source ID to search results
     * @param sourcePriorities Map of source ID to priority (higher = preferred)
     * @return Merged and deduplicated results
     */
    AggregatedSearchResult merge(Map<String, DataSource.SearchResult> resultsBySource,
                                 Map<String, Integer> sourcePriorities);

    /**
     * Determine if two items are the same product
     */
    boolean areItemsEquivalent(FoodProduct item1, FoodProduct item2);

    /**
     * Merge two equivalent items into one
     */
    FoodProduct mergeItems(FoodProduct primary, FoodProduct secondary);
}