package li.masciul.sugardaddi.core.utils;

import android.util.Log;

import li.masciul.sugardaddi.core.enums.DataSource;
import li.masciul.sugardaddi.core.interfaces.Searchable;
import li.masciul.sugardaddi.core.models.ScoredProduct;

import java.util.*;

/**
 * DiversityStrategy - Ensures source diversity in search results
 *
 * PROBLEM:
 * Without diversity enforcement, high-scoring sources (OpenFoodFacts) dominate
 * search results, completely filtering out other valuable sources (Ciqual).
 *
 * For example, searching "Pommes" returned:
 * - 20 OpenFoodFacts products (scores 70-85)
 * - 0 Ciqual products (scores 15-20, filtered out)
 *
 * This is unfair because Ciqual wasn't penalized for missing data it doesn't
 * provide (images, NutriScore), yet still got excluded from results.
 *
 * SOLUTION: Minimum Guarantee + Quality Fill
 *
 * ALGORITHM:
 * 1. Group scored products by data source
 * 2. Take minimum N results from each source (e.g., top 5 from each)
 * 3. Fill remaining slots with highest-scored products globally
 * 4. Result: Guaranteed diversity + quality prioritization
 *
 * EXAMPLE (MAX_RESULTS = 20, MIN_PER_SOURCE = 5):
 * ```
 * Input:
 * - OpenFoodFacts: 20 products (scores 70-85)
 * - Ciqual: 20 products (scores 60-75)
 * - Recipes: 10 products (scores 50-65)
 *
 * Step 1 - Minimum guarantee (5 per source):
 * - Take top 5 OFF (scores 85, 84, 83, 82, 81)
 * - Take top 5 Ciqual (scores 75, 74, 73, 72, 71)
 * - Take top 5 Recipes (scores 65, 64, 63, 62, 61)
 * = 15 products selected
 *
 * Step 2 - Quality fill (remaining 5 slots):
 * - Take next highest globally:
 *   - OFF #6 (score 80)
 *   - OFF #7 (score 79)
 *   - OFF #8 (score 78)
 *   - Ciqual #6 (score 70)
 *   - OFF #9 (score 77)
 * = 5 more products selected
 *
 * Final result: 20 products
 * - 9 OpenFoodFacts (naturally scored higher)
 * - 6 Ciqual (guaranteed representation)
 * - 5 Recipes (guaranteed representation)
 * ```
 *
 * BENEFITS:
 * - Users see results from ALL data sources
 * - High-quality results still prioritized
 * - No artificial score manipulation needed
 * - Configurable via ApiConfig
 *
 * @version 1.0
 * @since Search Diversity Refactor
 */
public class DiversityStrategy {

    /**
     * Private constructor - utility class
     */
    private DiversityStrategy() {
        throw new UnsupportedOperationException("DiversityStrategy is a utility class");
    }

    /**
     * Apply diversity strategy to search results
     *
     * Implements "Minimum Guarantee + Quality Fill" algorithm.
     * Ensures each source gets minimum representation, then fills with top-scored.
     *
     * @param scoredBySource Products grouped by source, already scored and sorted
     * @param minPerSource Minimum results to guarantee from each source
     * @param maxTotal Maximum total results to return
     * @return Diverse, quality-prioritized result list
     */
    public static List<Searchable> applyDiversity(
            Map<DataSource, List<ScoredProduct>> scoredBySource,
            int minPerSource,
            int maxTotal) {

        List<Searchable> result = new ArrayList<>();
        List<ScoredProduct> remainingProducts = new ArrayList<>();

        // ========== PHASE 1: MINIMUM GUARANTEE ==========
        // Take minimum N from each source to ensure diversity

        for (Map.Entry<DataSource, List<ScoredProduct>> entry : scoredBySource.entrySet()) {
            List<ScoredProduct> sourceProducts = entry.getValue();

            // How many to take from this source (minimum guarantee)
            int toTake = Math.min(minPerSource, sourceProducts.size());

            // Add top N from this source to results
            for (int i = 0; i < toTake; i++) {
                result.add(sourceProducts.get(i).getItem());
            }

            // Add the rest to "remaining" pool for quality fill
            for (int i = toTake; i < sourceProducts.size(); i++) {
                remainingProducts.add(sourceProducts.get(i));
            }
        }

        // ========== PHASE 2: QUALITY FILL ==========
        // Fill remaining slots with highest-scored products globally

        if (result.size() < maxTotal && !remainingProducts.isEmpty()) {
            // Sort remaining products by score (descending)
            Collections.sort(remainingProducts, new Comparator<ScoredProduct>() {
                @Override
                public int compare(ScoredProduct a, ScoredProduct b) {
                    return Integer.compare(b.getScore(), a.getScore()); // Higher scores first
                }
            });

            // Calculate how many slots remain
            int slotsRemaining = maxTotal - result.size();
            int toAdd = Math.min(slotsRemaining, remainingProducts.size());

            // Add top-scored products to fill remaining slots
            for (int i = 0; i < toAdd; i++) {
                result.add(remainingProducts.get(i).getItem());
            }
        }

        return result;
    }

    /**
     * Apply diversity strategy without grouping (convenience method)
     *
     * Takes a flat list of scored products and applies diversity strategy.
     * Automatically groups by source before applying algorithm.
     *
     * @param scoredProducts All scored products (mixed sources)
     * @param minPerSource Minimum results per source
     * @param maxTotal Maximum total results
     * @return Diverse, quality-prioritized result list
     */
    public static List<Searchable> applyDiversity(
            List<ScoredProduct> scoredProducts,
            int minPerSource,
            int maxTotal) {

        // Group by source
        Map<DataSource, List<ScoredProduct>> bySource = groupBySource(scoredProducts);

        // Apply diversity strategy
        return applyDiversity(bySource, minPerSource, maxTotal);
    }

    /**
     * Group scored products by data source
     *
     * Each source's products are sorted by score (descending) within the group.
     *
     * @param scoredProducts All scored products
     * @return Map of DataSource to sorted list of products
     */
    public static Map<DataSource, List<ScoredProduct>> groupBySource(List<ScoredProduct> scoredProducts) {
        Map<DataSource, List<ScoredProduct>> grouped = new HashMap<>();

        // Group products by source
        for (ScoredProduct scored : scoredProducts) {
            DataSource source = scored.getSource();

            if (!grouped.containsKey(source)) {
                grouped.put(source, new ArrayList<>());
            }

            grouped.get(source).add(scored);
        }

        // Sort each source's products by score (descending)
        for (List<ScoredProduct> sourceProducts : grouped.values()) {
            Collections.sort(sourceProducts, new Comparator<ScoredProduct>() {
                @Override
                public int compare(ScoredProduct a, ScoredProduct b) {
                    return Integer.compare(b.getScore(), a.getScore()); // Higher scores first
                }
            });
        }

        return grouped;
    }

    /**
     * Get diversity statistics for debugging/analytics
     *
     * Returns a map showing how many results came from each source.
     * Useful for verifying diversity is working correctly.
     *
     * @param results Final result list
     * @return Map of DataSource to count
     */
    public static Map<DataSource, Integer> getDiversityStats(List<Searchable> results) {
        Map<DataSource, Integer> stats = new HashMap<>();

        for (Searchable item : results) {
            DataSource source = item.getDataSource();
            stats.put(source, stats.getOrDefault(source, 0) + 1);
        }

        return stats;
    }

    /**
     * Format diversity statistics as human-readable string
     *
     * @param results Result list
     * @return Formatted string (e.g., "OFF:10, Ciqual:6, Recipes:4")
     */
    public static String formatDiversityStats(List<Searchable> results) {
        Map<DataSource, Integer> stats = getDiversityStats(results);

        StringBuilder sb = new StringBuilder();

        boolean first = true;
        for (Map.Entry<DataSource, Integer> entry : stats.entrySet()) {
            if (!first) sb.append(", ");

            sb.append(entry.getKey().getId()).append(":").append(entry.getValue());
            first = false;
        }

        return sb.toString();
    }
}