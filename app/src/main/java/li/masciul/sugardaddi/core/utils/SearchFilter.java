package li.masciul.sugardaddi.core.utils;

import li.masciul.sugardaddi.core.scoring.CiqualScorer;
import li.masciul.sugardaddi.core.scoring.OpenFoodFactsScorer;
import li.masciul.sugardaddi.core.scoring.USDAScorer;
import li.masciul.sugardaddi.data.network.ApiConfig;
import li.masciul.sugardaddi.core.enums.DataSource;
import li.masciul.sugardaddi.core.interfaces.Searchable;
import li.masciul.sugardaddi.core.interfaces.SourceSpecificScorer;
import li.masciul.sugardaddi.core.models.FoodProduct;
import li.masciul.sugardaddi.core.models.ScoredProduct;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * SearchFilter - Advanced search result filtering and ranking system
 *
 * REFACTORED v2.0 - Source-Specific Scoring with Diversity Strategy
 *
 * This class filters and ranks search results using source-specific scoring
 * algorithms that value each data source's unique strengths, then applies
 * a diversity strategy to ensure fair representation from all sources.
 *
 * KEY FEATURES:
 * - Source-specific scoring (OpenFoodFacts, Ciqual, Recipes)
 * - Diversity strategy (minimum guarantee + quality fill)
 * - Configurable behavior via ApiConfig
 * - Language-aware filtering
 * - Quality requirements checking
 *
 * ARCHITECTURE:
 * 1. Quality filtering (remove low-quality products)
 * 2. Source-specific scoring (each source scored fairly)
 * 3. Diversity enforcement (ensure all sources represented)
 * 4. Quality prioritization (fill remaining slots with top scores)
 *
 * @version 2.0 (Source-Specific Scoring)
 */
public class SearchFilter {

    private static final String TAG = ApiConfig.SEARCH_LOG_TAG;

    /**
     * Main filtering method - now with source-specific scoring and diversity
     *
     * @param products Raw products from API response
     * @param query Original search query from user
     * @param language Language to use for filtering (e.g., "en", "fr")
     * @return Filtered and sorted list of most relevant products
     */
    public static List<FoodProduct> filterAndSort(List<FoodProduct> products, String query, String language) {
        if (products == null || products.isEmpty()) {
            Log.d(TAG, "No products to filter");
            return new ArrayList<>();
        }

        Log.d(TAG, "Filtering " + products.size() + " products for query: '" + query + "' in language: " + language);

        // Normalize the search query for consistent matching
        String normalizedQuery = normalizeSearchTerm(query);

        // Score each product with source-specific scorers
        List<ScoredProduct> scoredProducts = new ArrayList<>();

        for (FoodProduct product : products) {
            // Skip products that don't meet basic quality requirements
            if (!meetsQualityRequirements(product, language)) {
                continue;
            }

            // Get appropriate scorer for this product's data source
            DataSource source = product.getDataSource();
            SourceSpecificScorer<FoodProduct> scorer = getScorer(source);

            // Score the product
            ScoredProduct scoredProduct = scorer.scoreProduct(product, normalizedQuery, language);

            // Only include products above minimum score threshold
            if (scoredProduct.getScore() >= scorer.getMinimumScore()) {
                scoredProducts.add(scoredProduct);

                if (ApiConfig.DEBUG_LOGGING) {
                    Log.d(TAG, String.format("Product '%s' scored %d: %s",
                            product.getDisplayName(language),
                            scoredProduct.getScore(),
                            scoredProduct.getScoreBreakdown()));
                }
            }
        }

        // Apply diversity strategy if enabled
        List<Searchable> diverseResults;
        if (ApiConfig.SourceDiversity.ENFORCE_SOURCE_DIVERSITY) {
            diverseResults = DiversityStrategy.applyDiversity(
                    scoredProducts,
                    ApiConfig.SourceDiversity.MIN_RESULTS_PER_SOURCE,
                    ApiConfig.MAX_RESULTS
            );

            if (ApiConfig.DEBUG_LOGGING) {
                String stats = DiversityStrategy.formatDiversityStats(diverseResults);
                Log.d(TAG, "Diversity stats: " + stats);
            }
        } else {
            // Legacy behavior: global top N
            Collections.sort(scoredProducts, new Comparator<ScoredProduct>() {
                @Override
                public int compare(ScoredProduct a, ScoredProduct b) {
                    return Integer.compare(b.getScore(), a.getScore());
                }
            });

            diverseResults = new ArrayList<>();
            int maxResults = Math.min(scoredProducts.size(), ApiConfig.MAX_RESULTS);
            for (int i = 0; i < maxResults; i++) {
                diverseResults.add(scoredProducts.get(i).getItem());
            }
        }

        // Convert Searchable back to FoodProduct
        List<FoodProduct> result = new ArrayList<>();
        for (Searchable item : diverseResults) {
            if (item instanceof FoodProduct) {
                result.add((FoodProduct) item);
            }
        }

        Log.d(TAG, String.format("Filtered to %d relevant products (from %d total)",
                result.size(), products.size()));

        return result;
    }

    /**
     * Overloaded method for backward compatibility - uses product's primary language
     */
    public static List<FoodProduct> filterAndSort(List<FoodProduct> products, String query) {
        if (products == null || products.isEmpty()) {
            return new ArrayList<>();
        }

        // Use the first product's primary language as default
        String defaultLanguage = products.get(0).getCurrentLanguage();
        if (defaultLanguage == null) {
            defaultLanguage = "en"; // Fallback to English
        }

        return filterAndSort(products, query, defaultLanguage);
    }

    /**
     * Get appropriate scorer for a data source
     * Uses singleton instances for efficiency (Android optimization)
     *
     * @param source Data source to get scorer for
     * @return Source-specific scorer instance
     */
    private static SourceSpecificScorer<FoodProduct> getScorer(DataSource source) {
        if (source == null) {
            return OpenFoodFactsScorer.getInstance(); // Default fallback
        }

        switch (source) {
            case OPENFOODFACTS:
                return OpenFoodFactsScorer.getInstance();
            case CIQUAL:
                return CiqualScorer.getInstance();
            case USDA:
                return USDAScorer.getInstance();
            default:
                // Fallback to OpenFoodFacts scorer for unknown sources
                return OpenFoodFactsScorer.getInstance();
        }
    }

    /**
     * Check if product meets minimum quality requirements
     * Now checks LocalizedContent for the specified language
     */
    private static boolean meetsQualityRequirements(FoodProduct product, String language) {
        // Must have a valid product ID
        if (product.getSearchableId() == null || product.getSearchableId().trim().isEmpty()) {
            return false;
        }

        // Must have at least a product name or brand
        String productName = product.getName(language);
        String brand = product.getBrand(language);

        // Fallback to other language if primary language returns null/empty
        if (productName == null || productName.trim().isEmpty()) {
            String fallbackLang = language.equals("fr") ? "en" : "fr";
            productName = product.getName(fallbackLang);

            if (ApiConfig.DEBUG_LOGGING && productName != null && !productName.trim().isEmpty()) {
                Log.d(TAG, "Using fallback language '" + fallbackLang + "' for product: " +
                        product.getSearchableId() + " (name: " + productName + ")");
            }
        }

        // Check brand with same fallback logic
        if (brand == null || brand.trim().isEmpty()) {
            String fallbackLang = language.equals("fr") ? "en" : "fr";
            brand = product.getBrand(fallbackLang);
        }

        boolean hasName = productName != null && !productName.trim().isEmpty();
        boolean hasBrand = brand != null && !brand.trim().isEmpty();

        if (!hasName && !hasBrand) {
            return false;
        }

        // Product name shouldn't be just generic terms
        if (hasName && isGenericProductName(productName)) {
            return false;
        }

        // If it has a barcode, that's a good sign of real product
        if (product.getBarcode() != null && !product.getBarcode().trim().isEmpty()) {
            return true;
        }

        return true;
    }

    /**
     * Check if all words from the query appear in the text (order doesn't matter)
     * Used by scoring utilities
     */
    public static boolean allWordsMatch(String text, String query) {
        if (text == null || query == null) return false;

        String[] queryWords = query.split("\\s+");
        for (String word : queryWords) {
            if (!text.contains(word)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Normalize search terms for consistent matching
     * Handles case, accents, and common variations
     *
     * NOW PUBLIC - Used by source-specific scorers and scoring utilities
     */
    public static String normalizeSearchTerm(String term) {
        if (term == null) return null;

        return term.toLowerCase()
                .trim()
                .replaceAll("\\s+", " ") // Normalize whitespace
                .replaceAll("[àáâãäå]", "a") // Handle accents
                .replaceAll("[èéêë]", "e")
                .replaceAll("[ìíîï]", "i")
                .replaceAll("[òóôõö]", "o")
                .replaceAll("[ùúûü]", "u")
                .replaceAll("[ç]", "c")
                .replaceAll("[ñ]", "n");
    }

    /**
     * Check if product name is too generic to be useful
     */
    private static boolean isGenericProductName(String productName) {
        if (productName == null) return true;

        String normalized = productName.toLowerCase().trim();

        // List of overly generic product names
        String[] genericTerms = {
                "", "product", "item", "food", "unknown", "n/a", "na", "null", "undefined",
                "test", "sample", "example", "placeholder", "temp", "temporary"
        };

        for (String generic : genericTerms) {
            if (normalized.equals(generic)) {
                return true;
            }
        }

        // Too short to be meaningful
        if (normalized.length() < 2) {
            return true;
        }

        return false;
    }
}