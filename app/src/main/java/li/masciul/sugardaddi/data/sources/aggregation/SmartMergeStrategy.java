package li.masciul.sugardaddi.data.sources.aggregation;

import android.util.Log;

import li.masciul.sugardaddi.core.interfaces.Searchable;
import li.masciul.sugardaddi.core.models.FoodProduct;
import li.masciul.sugardaddi.core.models.Nutrition;
import li.masciul.sugardaddi.data.network.ApiConfig;
import li.masciul.sugardaddi.data.sources.base.DataSource;

import java.util.*;

/**
 * Smart merging strategy that handles duplicates and enriches data
 */
public class SmartMergeStrategy implements MergeStrategy {

    private static final String TAG = "SmartMergeStrategy";

    @Override
    public AggregatedSearchResult merge(Map<String, DataSource.SearchResult> resultsBySource,
                                        Map<String, Integer> sourcePriorities) {
        long startTime = System.currentTimeMillis();

        // Collect all items with source tracking
        Map<FoodProduct, String> foodItemToSource = new HashMap<>();
        List<Searchable> passthroughItems = new ArrayList<>();

        Map<String, AggregatedSearchResult.SourceStats> sourceStats = new HashMap<>();
        int totalItemsBeforeMerge = 0;

        // Process each source's results
        for (Map.Entry<String, DataSource.SearchResult> entry : resultsBySource.entrySet()) {
            String sourceId = entry.getKey();
            DataSource.SearchResult result = entry.getValue();

            if (result != null && result.items != null) {
                for (Searchable item : result.items) {
                    if (item instanceof FoodProduct) {
                        // FoodProduct items go through deduplication
                        foodItemToSource.put((FoodProduct) item, sourceId);
                    } else {
                        // Recipe and other Searchable types pass through without deduplication.
                        // Cross-source merging is only meaningful for FoodProduct items
                        // (barcode/sourceIdentifier matching). Recipes have no equivalent.
                        passthroughItems.add(item);
                    }
                    totalItemsBeforeMerge++;
                }

                sourceStats.put(sourceId, new AggregatedSearchResult.SourceStats(
                        result.items.size(),
                        0, // Response time would be tracked by aggregator
                        null
                ));
            }
        }

        // Group equivalent items
        List<List<FoodProduct>> itemGroups = groupEquivalentItems(
                new ArrayList<>(foodItemToSource.keySet())
        );
        // Merge each group
        List<Searchable> mergedItems = new ArrayList<>();
        int duplicatesFound = 0;

        for (List<FoodProduct> group : itemGroups) {
            if (group.size() == 1) {
                mergedItems.add(group.get(0));
            } else {
                duplicatesFound += group.size() - 1;
                FoodProduct merged = mergeGroup(group, foodItemToSource, sourcePriorities);
                mergedItems.add(merged);
            }
        }

        // Append passthrough items (Recipe etc.) — no deduplication applied
        mergedItems.addAll(passthroughItems);
        // Sort by relevance/quality
        sortByRelevance(mergedItems);

        long searchDuration = System.currentTimeMillis() - startTime;

        // Extract query and language from first result
        String query = "";
        String language = "";
        if (!resultsBySource.isEmpty()) {
            DataSource.SearchResult firstResult = resultsBySource.values().iterator().next();
            query = firstResult.query;
            language = firstResult.language;
        }

        // hasMore and sourceHasMore are placeholder values here —
        // DataSourceAggregator.mergeAndDeliver() computes the real values
        // from raw SearchResult objects and overwrites them in the final result.
        return new AggregatedSearchResult(
                mergedItems,
                sourceStats,
                query,
                language,
                searchDuration,
                duplicatesFound,
                totalItemsBeforeMerge,
                false,          // placeholder — aggregator sets real value
                Collections.emptyMap()  // placeholder — aggregator sets real value
        );
    }

    @Override
    public boolean areItemsEquivalent(FoodProduct item1, FoodProduct item2) {
        // Same source and ID
        if (item1.getSourceIdentifier().equals(item2.getSourceIdentifier())) {
            return true;
        }

        // Check barcode match (if both have barcodes)
        String barcode1 = item1.getBarcode();
        String barcode2 = item2.getBarcode();
        if (barcode1 != null && barcode2 != null && !barcode1.isEmpty() && !barcode2.isEmpty()) {
            return barcode1.equals(barcode2);
        }

        // Fuzzy matching by name and brand
        return fuzzyMatch(item1, item2);
    }

    @Override
    public FoodProduct mergeItems(FoodProduct primary, FoodProduct secondary) {
        // Create a copy of primary item
        FoodProduct merged = copyItem(primary);

        // Enrich with data from secondary
        enrichWithData(merged, secondary);

        // Update data completeness
        merged.setDataCompleteness(Math.max(
                primary.getDataCompleteness(),
                secondary.getDataCompleteness()
        ));

        return merged;
    }

    // ========== PRIVATE HELPER METHODS ==========

    /**
     * Group equivalent items together
     */
    private List<List<FoodProduct>> groupEquivalentItems(List<FoodProduct> items) {
        List<List<FoodProduct>> groups = new ArrayList<>();
        Set<FoodProduct> processed = new HashSet<>();

        for (FoodProduct item : items) {
            if (processed.contains(item)) continue;

            List<FoodProduct> group = new ArrayList<>();
            group.add(item);
            processed.add(item);

            // Find all equivalent items
            for (FoodProduct other : items) {
                if (!processed.contains(other) && areItemsEquivalent(item, other)) {
                    group.add(other);
                    processed.add(other);
                }
            }

            groups.add(group);
        }

        return groups;
    }

    /**
     * Merge a group of equivalent items
     */
    private FoodProduct mergeGroup(List<FoodProduct> group,
                                       Map<FoodProduct, String> itemToSource,
                                       Map<String, Integer> sourcePriorities) {
        // Sort by source priority
        group.sort((a, b) -> {
            String sourceA = itemToSource.get(a);
            String sourceB = itemToSource.get(b);
            int priorityA = sourcePriorities.getOrDefault(sourceA, 0);
            int priorityB = sourcePriorities.getOrDefault(sourceB, 0);
            return Integer.compare(priorityB, priorityA);
        });

        // Start with highest priority item
        FoodProduct merged = copyItem(group.get(0));

        // Enrich with data from other sources
        for (int i = 1; i < group.size(); i++) {
            enrichWithData(merged, group.get(i));
        }

        return merged;
    }

    /**
     * Fuzzy matching logic
     */
    private boolean fuzzyMatch(FoodProduct item1, FoodProduct item2) {
        String raw1 = item1.getDisplayName("en");
        String raw2 = item2.getDisplayName("en");

        // Null name means incomplete data — can't fuzzy match, assume not duplicate
        if (raw1 == null || raw2 == null) return false;

        // Get names in same language
        String name1 = item1.getDisplayName("en").toLowerCase();
        String name2 = item2.getDisplayName("en").toLowerCase();

        // Get brands
        String brand1 = item1.getBrand("en");
        String brand2 = item2.getBrand("en");

        // Exact name and brand match
        if (name1.equals(name2) && brand1 != null && brand2 != null &&
                brand1.equalsIgnoreCase(brand2)) {
            return true;
        }

        // Calculate similarity score
        double nameSimilarity = calculateSimilarity(name1, name2);
        double brandSimilarity = 0;
        if (brand1 != null && brand2 != null) {
            brandSimilarity = calculateSimilarity(brand1.toLowerCase(), brand2.toLowerCase());
        }

        // High similarity threshold
        return nameSimilarity > 0.85 && brandSimilarity > 0.85;
    }

    /**
     * Calculate string similarity (simplified Jaccard index)
     */
    private double calculateSimilarity(String s1, String s2) {
        Set<String> words1 = new HashSet<>(Arrays.asList(s1.split("\\s+")));
        Set<String> words2 = new HashSet<>(Arrays.asList(s2.split("\\s+")));

        Set<String> intersection = new HashSet<>(words1);
        intersection.retainAll(words2);

        Set<String> union = new HashSet<>(words1);
        union.addAll(words2);

        if (union.isEmpty()) return 0;
        return (double) intersection.size() / union.size();
    }

    /**
     * Create a copy of an item
     */
    private FoodProduct copyItem(FoodProduct original) {
        // Simple copy - in production, use a proper deep copy
        FoodProduct copy = new FoodProduct(
                original.getSourceIdentifier().getSourceId(),
                original.getSourceIdentifier().getOriginalId()
        );

        copy.setBarcode(original.getBarcode());
        copy.setNutrition(original.getNutrition());
        copy.setLiquid(original.isLiquid());
        copy.setLastUpdated(original.getLastUpdated());

        // Copy hybrid translation data
        copy.setCurrentLanguage(original.getCurrentLanguage());
        copy.setName(original.getName(original.getCurrentLanguage()), original.getCurrentLanguage());
        copy.setBrand(original.getBrand(original.getCurrentLanguage()), original.getCurrentLanguage());
        copy.setGenericName(original.getGenericName(original.getCurrentLanguage()), original.getCurrentLanguage());
        copy.setCategoriesText(original.getCategoriesText(original.getCurrentLanguage()), original.getCurrentLanguage());

        // Copy all translations
        copy.setTranslations(new java.util.HashMap<>(original.getTranslations()));

        return copy;
    }

    /**
     * Enrich primary item with data from secondary
     */
    private void enrichWithData(FoodProduct primary, FoodProduct secondary) {
        // Add missing localized content
        for (Map.Entry<String, li.masciul.sugardaddi.core.models.ProductTranslation> entry : secondary.getTranslations().entrySet()) {
            String lang = entry.getKey();
            if (primary.getTranslations().get(lang) == null) {
                primary.addTranslation(lang, entry.getValue());
            }
        }

        // Add barcode if missing
        if (primary.getBarcode() == null && secondary.getBarcode() != null) {
            primary.setBarcode(secondary.getBarcode());
        }

        // Merge nutrition if missing or more complete
        if (primary.getNutrition() == null && secondary.getNutrition() != null) {
            primary.setNutrition(secondary.getNutrition());
        } else if (primary.getNutrition() != null && secondary.getNutrition() != null) {
            mergeNutrition(primary.getNutrition(), secondary.getNutrition());
        }
    }

    /**
     * Merge nutrition data
     */
    private void mergeNutrition(Nutrition primary, Nutrition secondary) {
        // Fill missing values from secondary
        if (primary.getEnergyKcal() == null && secondary.getEnergyKcal() != null) {
            primary.setEnergyKcal(secondary.getEnergyKcal());
        }
        if (primary.getProteins() == null && secondary.getProteins() != null) {
            primary.setProteins(secondary.getProteins());
        }
        if (primary.getCarbohydrates() == null && secondary.getCarbohydrates() != null) {
            primary.setCarbohydrates(secondary.getCarbohydrates());
        }
        if (primary.getFat() == null && secondary.getFat() != null) {
            primary.setFat(secondary.getFat());
        }

        // Add sugar breakdown if available
        if (!primary.hasSugarBreakdown() && secondary.hasSugarBreakdown()) {
            primary.setGlucose(secondary.getGlucose());
            primary.setFructose(secondary.getFructose());
            primary.setSucrose(secondary.getSucrose());
            primary.setLactose(secondary.getLactose());
            primary.setMaltose(secondary.getMaltose());
        }
    }

    /**
     * Sort items by relevance and quality
     */
    private void sortByRelevance(List<Searchable> items) {
        items.sort((a, b) -> {
            // FoodProduct items sort before Recipe items by default
            if (a instanceof FoodProduct && !(b instanceof FoodProduct)) return -1;
            if (!(a instanceof FoodProduct) && b instanceof FoodProduct) return 1;

            // Both FoodProduct — preserve existing three-tier logic
            if (a instanceof FoodProduct && b instanceof FoodProduct) {
                FoodProduct fa = (FoodProduct) a;
                FoodProduct fb = (FoodProduct) b;

                // Tier 1: data completeness (higher is better)
                int completenessCompare = Float.compare(
                        fb.getDataCompleteness(), fa.getDataCompleteness());
                if (completenessCompare != 0) return completenessCompare;

                // Tier 2: barcode presence (products with barcodes ranked higher)
                boolean aHasBarcode = fa.getBarcode() != null && !fa.getBarcode().isEmpty();
                boolean bHasBarcode = fb.getBarcode() != null && !fb.getBarcode().isEmpty();
                if (aHasBarcode && !bHasBarcode) return -1;
                if (!aHasBarcode && bHasBarcode) return 1;

                // Tier 3: alphabetical by display name (null-safe)
                String nameA = fa.getDisplayName("en");
                String nameB = fb.getDisplayName("en");
                if (nameA == null && nameB == null) return 0;
                if (nameA == null) return 1;
                if (nameB == null) return -1;
                return nameA.compareTo(nameB);
            }

            // Both Recipe or other Searchable types — preserve insertion order
            return 0;
        });
    }
}