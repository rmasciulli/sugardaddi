package li.masciul.sugardaddi.data.repository;

import android.content.Context;
import android.util.Log;

import li.masciul.sugardaddi.core.interfaces.Searchable;
import li.masciul.sugardaddi.core.models.Error;
import li.masciul.sugardaddi.core.models.FoodProduct;
import li.masciul.sugardaddi.core.models.Recipe;
import li.masciul.sugardaddi.core.models.Meal;
import li.masciul.sugardaddi.core.enums.ProductType;
import li.masciul.sugardaddi.core.enums.DataSource;
import li.masciul.sugardaddi.data.network.ApiConfig;
import li.masciul.sugardaddi.managers.LanguageManager;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * SearchRepository - Unified search across all nutritional content types
 *
 * This repository demonstrates the power of the new unified architecture:
 * - Searches FoodProducts, Recipes, and Meals with the same interface
 * - Returns polymorphic Searchable results
 * - Intelligent ranking and filtering across types
 * - Seamless integration with existing managers
 * - Advanced features like type filtering and result aggregation
 */
public class SearchRepository {

    private static final String TAG = ApiConfig.SEARCH_LOG_TAG;

    // Dependencies
    private final Context context;
    private final ProductRepository productRepository;
    private final RecipeRepository recipeRepository;
    private final MealRepository mealRepository;

    // Search state
    private boolean isSearchInProgress = false;
    private String currentQuery = "";

    // Configuration
    private Set<ProductType> enabledTypes = new HashSet<>();
    private int maxResultsPerType = 20;
    private boolean enableCrossTypeRanking = true;

    /**
     * Unified search result container
     */
    public static class UnifiedSearchResult {
        private final List<Searchable> items;
        private final String query;
        private final Map<ProductType, Integer> countsByType;
        private final long searchTimeMs;
        private final String language;

        public UnifiedSearchResult(List<Searchable> items, String query,
                                   Map<ProductType, Integer> countsByType,
                                   long searchTimeMs, String language) {
            this.items = new ArrayList<>(items);
            this.query = query;
            this.countsByType = new HashMap<>(countsByType);
            this.searchTimeMs = searchTimeMs;
            this.language = language;
        }

        public List<Searchable> getItems() { return new ArrayList<>(items); }
        public String getQuery() { return query; }
        public Map<ProductType, Integer> getCountsByType() { return new HashMap<>(countsByType); }
        public long getSearchTimeMs() { return searchTimeMs; }
        public String getLanguage() { return language; }
        public int getTotalCount() { return items.size(); }

        public List<Searchable> getItemsOfType(ProductType type) {
            return items.stream()
                    .filter(item -> item.getProductType() == type)
                    .collect(java.util.stream.Collectors.toList());
        }
    }

    /**
     * Callback interface for unified search operations
     */
    public interface UnifiedSearchCallback {
        void onSuccess(UnifiedSearchResult result);
        void onError(Error error);
        void onLoading();
        void onProgress(String status, int completed, int total);
    }

    // ========== CONSTRUCTOR ==========

    public SearchRepository(Context context, ProductRepository productRepository) {
        this.context = context.getApplicationContext();
        this.productRepository = productRepository;
        this.recipeRepository = new RecipeRepository(context);
        this.mealRepository = new MealRepository(context);

        // Enable all types by default
        enabledTypes.addAll(Arrays.asList(ProductType.values()));

        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, "SearchRepository initialized with unified search support");
        }
    }

    // ========== PUBLIC SEARCH METHODS ==========

    /**
     * Search across all enabled content types
     *
     * This is the main search method that demonstrates true polymorphism
     */
    public void searchAll(String query, UnifiedSearchCallback callback) {
        if (callback == null) {
            Log.w(TAG, "UnifiedSearchCallback is null");
            return;
        }

        if (query == null || query.trim().length() < ApiConfig.MIN_SEARCH_LENGTH) {
            callback.onError(Error.network("Search query too short", null));
            return;
        }

        String normalizedQuery = query.trim();
        currentQuery = normalizedQuery;
        String language = LanguageManager.getCurrentLanguage(context).getCode();

        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, "Starting unified search for: '" + normalizedQuery + "' in " + language);
        }

        long startTime = System.currentTimeMillis();
        isSearchInProgress = true;
        callback.onLoading();

        // Search all enabled types concurrently
        List<CompletableFuture<List<Searchable>>> searchTasks = new ArrayList<>();
        int totalTasks = enabledTypes.size();
        AtomicInteger completedTasks = new AtomicInteger(0);

        // Food search
        if (enabledTypes.contains(ProductType.FOOD)) {
            CompletableFuture<List<Searchable>> foodSearch = searchFoodsAsync(normalizedQuery, language);
            searchTasks.add(foodSearch);

            foodSearch.whenComplete((result, throwable) -> {
                int completed = completedTasks.incrementAndGet();
                callback.onProgress("Searching foods...", completed, totalTasks);
            });
        }

        // Recipe search
        if (enabledTypes.contains(ProductType.RECIPE)) {
            CompletableFuture<List<Searchable>> recipeSearch = searchRecipesAsync(normalizedQuery, language);
            searchTasks.add(recipeSearch);

            recipeSearch.whenComplete((result, throwable) -> {
                int completed = completedTasks.incrementAndGet();
                callback.onProgress("Searching recipes...", completed, totalTasks);
            });
        }

        // Meal search
        if (enabledTypes.contains(ProductType.MEAL)) {
            CompletableFuture<List<Searchable>> mealSearch = searchMealsAsync(normalizedQuery, language);
            searchTasks.add(mealSearch);

            mealSearch.whenComplete((result, throwable) -> {
                int completed = completedTasks.incrementAndGet();
                callback.onProgress("Searching meals...", completed, totalTasks);
            });
        }

        // Combine all results
        CompletableFuture.allOf(searchTasks.toArray(new CompletableFuture[0]))
                .whenComplete((voidResult, throwable) -> {
                    isSearchInProgress = false;

                    if (throwable != null) {
                        Log.e(TAG, "Unified search failed", throwable);
                        callback.onError(Error.fromThrowable(throwable, "Search failed"));
                        return;
                    }

                    try {
                        // Collect all results
                        List<Searchable> allResults = new ArrayList<>();
                        Map<ProductType, Integer> countsByType = new HashMap<>();

                        for (CompletableFuture<List<Searchable>> task : searchTasks) {
                            List<Searchable> results = task.get();
                            allResults.addAll(results);

                            // Count by type
                            for (Searchable item : results) {
                                ProductType type = item.getProductType();
                                countsByType.put(type, countsByType.getOrDefault(type, 0) + 1);
                            }
                        }

                        // Apply cross-type ranking if enabled
                        if (enableCrossTypeRanking) {
                            allResults = rankResults(allResults, normalizedQuery, language);
                        }

                        long searchTime = System.currentTimeMillis() - startTime;
                        UnifiedSearchResult result = new UnifiedSearchResult(
                                allResults, normalizedQuery, countsByType, searchTime, language);

                        if (ApiConfig.DEBUG_LOGGING) {
                            Log.d(TAG, String.format("Unified search completed in %dms: %d total results (%s)",
                                    searchTime, allResults.size(), formatCounts(countsByType)));
                        }

                        callback.onSuccess(result);

                    } catch (Exception e) {
                        Log.e(TAG, "Error combining search results", e);
                        callback.onError(Error.network("Failed to process search results", null));
                    }
                });
    }

    /**
     * Search only specific types
     */
    public void searchTypes(String query, Set<ProductType> types, UnifiedSearchCallback callback) {
        Set<ProductType> originalTypes = new HashSet<>(enabledTypes);
        enabledTypes = new HashSet<>(types);

        searchAll(query, new UnifiedSearchCallback() {
            @Override
            public void onSuccess(UnifiedSearchResult result) {
                enabledTypes = originalTypes; // Restore
                callback.onSuccess(result);
            }

            @Override
            public void onError(Error error) {
                enabledTypes = originalTypes; // Restore
                callback.onError(error);
            }

            @Override
            public void onLoading() {
                callback.onLoading();
            }

            @Override
            public void onProgress(String status, int completed, int total) {
                callback.onProgress(status, completed, total);
            }
        });
    }

    /**
     * Quick search for suggestions (faster, fewer results)
     */
    public void searchQuick(String query, UnifiedSearchCallback callback) {
        int originalMax = maxResultsPerType;
        maxResultsPerType = 5; // Limit for quick search

        searchAll(query, new UnifiedSearchCallback() {
            @Override
            public void onSuccess(UnifiedSearchResult result) {
                maxResultsPerType = originalMax; // Restore
                callback.onSuccess(result);
            }

            @Override
            public void onError(Error error) {
                maxResultsPerType = originalMax; // Restore
                callback.onError(error);
            }

            @Override
            public void onLoading() { callback.onLoading(); }
            @Override
            public void onProgress(String status, int completed, int total) {
                callback.onProgress(status, completed, total);
            }
        });
    }

    // ========== TYPE-SPECIFIC SEARCH METHODS ==========

    private CompletableFuture<List<Searchable>> searchFoodsAsync(String query, String language) {
        CompletableFuture<List<Searchable>> future = new CompletableFuture<>();

        productRepository.searchFood(query, new ProductRepository.SearchCallback() {
            @Override
            public void onSuccess(List<FoodProduct> products) {
                List<Searchable> searchableItems = new ArrayList<>();

                // FoodProducts already implement Searchable
                for (FoodProduct product : products) {
                    searchableItems.add(product);
                }

                // Limit results
                if (searchableItems.size() > maxResultsPerType) {
                    searchableItems = searchableItems.subList(0, maxResultsPerType);
                }

                if (ApiConfig.DEBUG_LOGGING) {
                    Log.d(TAG, "Food search returned " + searchableItems.size() + " results");
                }

                future.complete(searchableItems);
            }

            @Override
            public void onError(Error error) {
                Log.w(TAG, "Food search failed: " + error.getMessage());
                future.complete(new ArrayList<>()); // Return empty list instead of failing
            }

            @Override
            public void onLoading() {
                // Handled by parent
            }
        });

        return future;
    }

    private CompletableFuture<List<Searchable>> searchRecipesAsync(String query, String language) {
        CompletableFuture<List<Searchable>> future = new CompletableFuture<>();

        recipeRepository.search(query, language, new RecipeRepository.RecipeSearchCallback() {
            @Override
            public void onSuccess(List<Recipe> recipes) {
                List<Searchable> searchableItems = new ArrayList<>();

                // Filter and rank recipes
                for (Recipe recipe : recipes) {
                    int relevance = recipe.getSearchRelevance(query, language);
                    if (relevance >= 20) { // Minimum relevance threshold
                        searchableItems.add(recipe);
                    }
                }

                // Sort by relevance
                searchableItems.sort((a, b) -> Integer.compare(
                        b.getSearchRelevance(query, language),
                        a.getSearchRelevance(query, language)
                ));

                // Limit results
                if (searchableItems.size() > maxResultsPerType) {
                    searchableItems = searchableItems.subList(0, maxResultsPerType);
                }

                if (ApiConfig.DEBUG_LOGGING) {
                    Log.d(TAG, "Recipe search returned " + searchableItems.size() + " results");
                }

                future.complete(searchableItems);
            }

            @Override
            public void onError(String error) {
                Log.w(TAG, "Recipe search failed: " + error);
                future.complete(new ArrayList<>());
            }
        });

        return future;
    }

    private CompletableFuture<List<Searchable>> searchMealsAsync(String query, String language) {
        CompletableFuture<List<Searchable>> future = new CompletableFuture<>();

        mealRepository.search(query, maxResultsPerType, new MealRepository.MealListCallback() {
            @Override
            public void onSuccess(List<Meal> meals) {
                List<Searchable> searchableItems = new ArrayList<>();

                // Filter and rank meals
                for (Meal meal : meals) {
                    int relevance = meal.getSearchRelevance(query, language);
                    if (relevance >= 15) { // Lower threshold for meals
                        searchableItems.add(meal);
                    }
                }

                // Sort by relevance
                searchableItems.sort((a, b) -> Integer.compare(
                        b.getSearchRelevance(query, language),
                        a.getSearchRelevance(query, language)
                ));

                // Limit results
                if (searchableItems.size() > maxResultsPerType) {
                    searchableItems = searchableItems.subList(0, maxResultsPerType);
                }

                if (ApiConfig.DEBUG_LOGGING) {
                    Log.d(TAG, "Meal search returned " + searchableItems.size() + " results");
                }

                future.complete(searchableItems);
            }

            @Override
            public void onError(String error) {
                Log.w(TAG, "Meal search failed: " + error);
                future.complete(new ArrayList<>());
            }
        });

        return future;
    }

    // ========== RESULT RANKING AND FILTERING ==========

    /**
     * Intelligent cross-type ranking
     * This shows the power of the Searchable interface - same code works for all types!
     */
    private List<Searchable> rankResults(List<Searchable> items, String query, String language) {
        if (items.isEmpty()) return items;

        // Calculate composite scores considering both relevance and type priority
        List<ScoredResult> scoredResults = new ArrayList<>();

        for (Searchable item : items) {
            int relevanceScore = item.getSearchRelevance(query, language);
            int typeBonus = getTypeBonus(item.getProductType());

            int totalScore = relevanceScore + typeBonus;
            scoredResults.add(new ScoredResult(item, totalScore));
        }

        // Sort by total score
        scoredResults.sort((a, b) -> Integer.compare(b.score, a.score));

        // Extract items
        List<Searchable> rankedItems = new ArrayList<>();
        for (ScoredResult scored : scoredResults) {
            rankedItems.add(scored.item);
        }

        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, "Ranked " + items.size() + " results with cross-type scoring");
        }

        return rankedItems;
    }

    private static class ScoredResult {
        final Searchable item;
        final int score;

        ScoredResult(Searchable item, int score) {
            this.item = item;
            this.score = score;
        }
    }

    private int getTypeBonus(ProductType type) {
        // Prioritize certain types based on context
        switch (type) {
            case FOOD: return 10;      // Foods are most common
            case RECIPE: return 8;     // Recipes are valuable
            case MEAL: return 5;       // Meals are situational
            default: return 0;
        }
    }

    // ========== CONFIGURATION ==========

    public void setEnabledTypes(Set<ProductType> types) {
        enabledTypes = new HashSet<>(types);
        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, "Enabled types updated: " + types);
        }
    }

    public void setMaxResultsPerType(int maxResults) {
        maxResultsPerType = Math.max(1, Math.min(maxResults, 100));
        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, "Max results per type set to: " + maxResultsPerType);
        }
    }

    public void setCrossTypeRanking(boolean enabled) {
        enableCrossTypeRanking = enabled;
        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, "Cross-type ranking " + (enabled ? "enabled" : "disabled"));
        }
    }

    // ========== STATE METHODS ==========

    public boolean isSearchInProgress() {
        return isSearchInProgress;
    }

    public String getCurrentQuery() {
        return currentQuery;
    }

    public void cancelSearch() {
        if (isSearchInProgress) {
            productRepository.cancelCurrentSearch();
            recipeRepository.cancelSearch();
            mealRepository.cancelSearch();
            isSearchInProgress = false;

            if (ApiConfig.DEBUG_LOGGING) {
                Log.d(TAG, "Unified search cancelled");
            }
        }
    }

    // ========== UTILITY METHODS ==========

    private String formatCounts(Map<ProductType, Integer> counts) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<ProductType, Integer> entry : counts.entrySet()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(entry.getKey().getDisplayName()).append(": ").append(entry.getValue());
        }
        return sb.toString();
    }

    /**
     * Get search suggestions based on query
     */
    public void getSuggestions(String partialQuery, UnifiedSearchCallback callback) {
        if (partialQuery == null || partialQuery.length() < 2) {
            callback.onSuccess(new UnifiedSearchResult(
                    new ArrayList<>(), partialQuery, new HashMap<>(), 0, ""));
            return;
        }

        // Use quick search for suggestions
        searchQuick(partialQuery, callback);
    }
}