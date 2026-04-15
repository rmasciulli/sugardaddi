package li.masciul.sugardaddi.business.search;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import li.masciul.sugardaddi.core.interfaces.Searchable;
import li.masciul.sugardaddi.core.models.Error;
import li.masciul.sugardaddi.core.models.Recipe;
import li.masciul.sugardaddi.data.network.ApiConfig;
import li.masciul.sugardaddi.core.models.FoodProduct;
import li.masciul.sugardaddi.data.repository.ProductRepository;
import li.masciul.sugardaddi.data.repository.RecipeRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * SearchManager - Unified search orchestration with debouncing and state management
 *
 * UNIFIED SEARCH v4.1 - Clean API with Autocomplete
 *
 * This manager handles all search operations across products and recipes with intelligent
 * debouncing, state management, and flexible scope configuration.
 *
 * KEY FEATURES:
 * - SearchScope enum (PRODUCTS_ONLY, RECIPES_ONLY, ALL)
 * - Unified List<Searchable> results supporting both FoodProduct and Recipe
 * - Parallel search execution for ALL scope
 * - Autocomplete support with separate lightweight queries
 * - Debounced search to prevent excessive API calls
 * - Automatic cancellation of outdated searches
 * - Pagination with loadMoreResults()
 * - Retry capability for failed searches
 * - Search statistics tracking
 * - Comprehensive error handling
 * - Handler-based threading for UI safety
 * - Clean resource management
 *
 * USAGE:
 * ```java
 * SearchManager manager = new SearchManager(productRepository, recipeRepository);
 * manager.setListener(this);
 * manager.setAutocompleteListener(this);
 * manager.setSearchScope(SearchScope.ALL);
 * manager.setLanguage("en");
 * manager.search("pizza");
 * ```
 *
 * @version 4.1 - Unified Search with Autocomplete (Beta Clean)
 * @author SugarDaddi Team
 */
public class SearchManager {

    private static final String TAG = ApiConfig.SEARCH_LOG_TAG;

    // ========== SEARCH SCOPE ==========

    /**
     * Search scope determines what to search
     */
    public enum SearchScope {
        /** Search only products */
        PRODUCTS_ONLY,

        /** Search only recipes */
        RECIPES_ONLY,

        /** Search both products and recipes in parallel */
        ALL
    }

    // ========== CORE DEPENDENCIES ==========

    private final ProductRepository productRepository;
    private final RecipeRepository recipeRepository;
    private final Handler searchHandler;
    private final Handler autocompleteHandler;

    // ========== SEARCH CONFIGURATION ==========

    private SearchScope searchScope = SearchScope.PRODUCTS_ONLY;
    private String currentLanguage = "en";

    // ========== SEARCH STATE ==========

    private Runnable pendingSearch;
    private Runnable pendingAutocomplete;
    private SearchListener listener;
    private AutocompleteListener autocompleteListener;
    private String currentQuery = "";
    private String lastSuccessfulQuery = "";
    private boolean isSearchActive = false;
    private boolean isAutocompleteActive = false;

    // ========== PAGINATION STATE ==========

    private boolean isPaginationActive = false;
    private int currentPage = 1;
    private boolean hasMorePages = true;

    // ========== SEARCH STATISTICS ==========

    private int searchCount = 0;
    private int autocompleteCount = 0;
    private int cacheHits = 0;
    private long lastSearchTime = 0;

    // ========== PARALLEL SEARCH STATE ==========

    private boolean isParallelSearchActive = false;
    private final AtomicInteger parallelSearchesCompleted = new AtomicInteger(0);
    private final AtomicInteger parallelSearchesFailed = new AtomicInteger(0);
    private final List<Searchable> parallelCombinedResults = new ArrayList<>();

    /**
     * Listener interface for full search events
     */
    public interface SearchListener {
        void onSearchResults(List<Searchable> results);
        void onSearchError(Error error);
        void onSearchLoading();
        void onSearchEmpty();

        void onMoreResults(List<Searchable> results);
        void onMoreResultsError(Error error);
        void onLoadingMore();

        default void onSearchCancelled() {}
    }

    /**
     * Listener interface for autocomplete events
     */
    public interface AutocompleteListener {
        void onAutocompleteSuggestions(List<String> suggestions);
        void onAutocompleteError(Error error);
        void onQueryTooShort();
    }

    /**
     * Constructor
     *
     * @param productRepository Repository for product search
     * @param recipeRepository Repository for recipe search
     */
    public SearchManager(@NonNull ProductRepository productRepository,
                         @NonNull RecipeRepository recipeRepository) {
        this.productRepository = productRepository;
        this.recipeRepository = recipeRepository;
        this.searchHandler = new Handler(Looper.getMainLooper());
        this.autocompleteHandler = new Handler(Looper.getMainLooper());

        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, "SearchManager initialized (v4.1 with Autocomplete) with " +
                    ApiConfig.SEARCH_DEBOUNCE_MS + "ms debounce");
            Log.d(TAG, "Default scope: " + searchScope);
        }
    }

    // ========== CONFIGURATION ==========

    public void setSearchScope(@NonNull SearchScope scope) {
        if (scope == SearchScope.RECIPES_ONLY && recipeRepository == null) {
            Log.e(TAG, "Cannot set RECIPES_ONLY scope - RecipeRepository is null");
            return;
        }

        if (scope == SearchScope.ALL && recipeRepository == null) {
            Log.e(TAG, "Cannot set ALL scope - RecipeRepository is null");
            return;
        }

        this.searchScope = scope;

        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, "Search scope changed to: " + scope);
        }
    }

    @NonNull
    public SearchScope getSearchScope() {
        return searchScope;
    }

    public void setLanguage(@NonNull String languageCode) {
        this.currentLanguage = languageCode;

        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, "Recipe search language set to: " + languageCode);
        }
    }

    public void setListener(SearchListener listener) {
        this.listener = listener;
    }

    public void setAutocompleteListener(AutocompleteListener listener) {
        this.autocompleteListener = listener;
    }

    // ========== SEARCH METHODS ==========

    public void search(String query) {
        cancelPendingSearch();

        String trimmedQuery = query != null ? query.trim() : "";
        currentQuery = trimmedQuery;

        currentPage = 1;
        hasMorePages = true;

        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, "Search requested: '" + trimmedQuery + "' (length: " +
                    trimmedQuery.length() + ", scope: " + searchScope + ")");
        }

        if (trimmedQuery.length() < ApiConfig.MIN_SEARCH_LENGTH) {
            if (trimmedQuery.isEmpty()) {
                notifySearchEmpty();
            }
            return;
        }

        pendingSearch = new Runnable() {
            @Override
            public void run() {
                if (currentQuery.equals(trimmedQuery)) {
                    performSearch(trimmedQuery);
                }
            }
        };

        searchHandler.postDelayed(pendingSearch, ApiConfig.SEARCH_DEBOUNCE_MS);

        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, "Search scheduled for: '" + trimmedQuery + "' in " +
                    ApiConfig.SEARCH_DEBOUNCE_MS + "ms");
        }
    }

    public void searchImmediate(String query) {
        cancelPendingSearch();
        currentQuery = query != null ? query.trim() : "";
        currentPage = 1;
        hasMorePages = true;

        if (currentQuery.length() >= ApiConfig.MIN_SEARCH_LENGTH) {
            performSearch(currentQuery);
        } else {
            notifySearchEmpty();
        }
    }

    /**
     * Perform autocomplete search (lightweight, fast suggestions)
     * Debounced separately from full search for better UX
     */
    public void autocomplete(String query) {
        cancelPendingAutocomplete();

        String trimmedQuery = query != null ? query.trim() : "";

        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, "Autocomplete requested: '" + trimmedQuery + "' (length: " + trimmedQuery.length() + ")");
        }

        // Minimum 3 characters for autocomplete
        if (trimmedQuery.length() < 3) {
            notifyQueryTooShort();
            return;
        }

        pendingAutocomplete = new Runnable() {
            @Override
            public void run() {
                performAutocomplete(trimmedQuery);
            }
        };

        // Shorter debounce for autocomplete (faster feedback)
        autocompleteHandler.postDelayed(pendingAutocomplete, ApiConfig.SEARCH_DEBOUNCE_MS / 2);
    }

    public void loadMoreResults() {
        if (currentQuery.isEmpty() || !hasMorePages || isPaginationActive || isSearchActive) {
            if (ApiConfig.DEBUG_LOGGING) {
                Log.d(TAG, "Cannot load more: query=" + currentQuery +
                        ", hasMore=" + hasMorePages + ", paginationActive=" + isPaginationActive);
            }
            return;
        }

        if (searchScope != SearchScope.PRODUCTS_ONLY) {
            if (ApiConfig.DEBUG_LOGGING) {
                Log.w(TAG, "Pagination not yet supported for scope: " + searchScope);
            }
            return;
        }

        isPaginationActive = true;
        currentPage++;

        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, "Loading page " + currentPage + " for query: '" + currentQuery + "'");
        }

        notifyLoadingMore();

        productRepository.searchFoodAdvanced(currentQuery, currentPage, new ProductRepository.SearchCallback() {
            @Override
            public void onSuccess(List<FoodProduct> items) {
                isPaginationActive = false;

                if (items.isEmpty()) {
                    hasMorePages = false;
                    if (ApiConfig.DEBUG_LOGGING) {
                        Log.d(TAG, "No more results available for: '" + currentQuery + "'");
                    }
                } else {
                    List<Searchable> searchableItems = new ArrayList<>(items);

                    if (ApiConfig.DEBUG_LOGGING) {
                        Log.d(TAG, "Loaded " + items.size() + " more results for page " + currentPage);
                    }
                    notifyMoreResults(searchableItems);
                }
            }

            @Override
            public void onError(Error error) {
                isPaginationActive = false;
                currentPage--;

                Log.w(TAG, "Failed to load more results: " + error.getMessage());
                notifyMoreResultsError(error);
            }

            @Override
            public void onLoading() {
                // Already handled
            }
        });
    }

    public void retryLastSearch() {
        if (!currentQuery.isEmpty()) {
            if (ApiConfig.DEBUG_LOGGING) {
                Log.d(TAG, "Retrying search for: '" + currentQuery + "'");
            }
            performSearch(currentQuery);
        } else if (!lastSuccessfulQuery.isEmpty()) {
            if (ApiConfig.DEBUG_LOGGING) {
                Log.d(TAG, "Retrying last successful search: '" + lastSuccessfulQuery + "'");
            }
            performSearch(lastSuccessfulQuery);
        } else {
            Log.w(TAG, "No query available for retry");
        }
    }

    /**
     * Cancel all active searches and pending operations
     * Simple alias for cancelAllSearches() for better API ergonomics
     */
    public void cancel() {
        cancelAllSearches();
    }

    public void cancelAllSearches() {
        cancelPendingSearch();
        cancelPendingAutocomplete();

        if (isSearchActive || isPaginationActive || isParallelSearchActive) {
            productRepository.cancelCurrentSearch();

            if (recipeRepository != null) {
                recipeRepository.cancelSearch();
            }

            isSearchActive = false;
            isPaginationActive = false;
            isParallelSearchActive = false;
            isAutocompleteActive = false;

            if (listener != null) {
                listener.onSearchCancelled();
            }

            if (ApiConfig.DEBUG_LOGGING) {
                Log.d(TAG, "All searches cancelled");
            }
        }
    }

    public void cleanup() {
        cancelAllSearches();
        listener = null;
        autocompleteListener = null;

        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, "SearchManager cleaned up. Stats: " + getSearchStats());
        }
    }

    // ========== STATE QUERIES ==========

    public boolean hasMoreResults() {
        return hasMorePages && !currentQuery.isEmpty();
    }

    public boolean isLoadingMore() {
        return isPaginationActive;
    }

    public boolean isSearchInProgress() {
        return isSearchActive || pendingSearch != null || isPaginationActive ||
                isParallelSearchActive || isAutocompleteActive;
    }

    public String getCurrentQuery() {
        return currentQuery;
    }

    public String getSearchStats() {
        return String.format("Searches: %d, Autocomplete: %d, Cache hits: %d, Cache rate: %.1f%%, Last search: %dms ago, Scope: %s",
                searchCount, autocompleteCount, cacheHits,
                (searchCount > 0 ? (cacheHits * 100.0 / searchCount) : 0),
                (System.currentTimeMillis() - lastSearchTime), searchScope);
    }

    // ========== PRIVATE METHODS ==========

    private void cancelPendingSearch() {
        if (pendingSearch != null) {
            searchHandler.removeCallbacks(pendingSearch);
            pendingSearch = null;
        }
    }

    private void cancelPendingAutocomplete() {
        if (pendingAutocomplete != null) {
            autocompleteHandler.removeCallbacks(pendingAutocomplete);
            pendingAutocomplete = null;
        }
    }

    private void performSearch(String query) {
        if (isSearchActive) {
            productRepository.cancelCurrentSearch();
            if (recipeRepository != null) {
                recipeRepository.cancelSearch();
            }
        }

        isSearchActive = true;
        searchCount++;
        lastSearchTime = System.currentTimeMillis();

        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, "Executing search: '" + query + "' (search #" + searchCount +
                    ", scope: " + searchScope + ")");
        }

        switch (searchScope) {
            case PRODUCTS_ONLY:
                performProductSearch(query);
                break;

            case RECIPES_ONLY:
                performRecipeSearch(query);
                break;

            case ALL:
                performParallelSearch(query);
                break;
        }
    }

    private void performAutocomplete(String query) {
        if (isAutocompleteActive) {
            // Cancel previous autocomplete if still running
            productRepository.cancelCurrentSearch();
        }

        isAutocompleteActive = true;
        autocompleteCount++;

        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, "Executing autocomplete: '" + query + "' (autocomplete #" + autocompleteCount + ")");
        }

        // Use lightweight autocomplete API from ProductRepository
        productRepository.autocomplete(query, new ProductRepository.SearchCallback() {
            @Override
            public void onSuccess(List<FoodProduct> items) {
                isAutocompleteActive = false;

                // Extract product names for suggestions
                List<String> suggestions = new ArrayList<>();
                for (FoodProduct product : items) {
                    String name = product.getDisplayName(currentLanguage);
                    if (name != null && !name.trim().isEmpty()) {
                        suggestions.add(name);
                    }
                }

                if (ApiConfig.DEBUG_LOGGING) {
                    Log.d(TAG, "Autocomplete successful: '" + query + "' returned " +
                            suggestions.size() + " suggestions");
                }

                notifyAutocompleteSuggestions(suggestions);
            }

            @Override
            public void onError(Error error) {
                isAutocompleteActive = false;

                if (ApiConfig.DEBUG_LOGGING) {
                    Log.d(TAG, "Autocomplete failed for '" + query + "': " + error.getMessage());
                }

                // Don't notify errors for autocomplete (silent failure for better UX)
                // Just provide empty suggestions
                notifyAutocompleteSuggestions(new ArrayList<>());
            }

            @Override
            public void onLoading() {
                // No loading indicator for autocomplete (too fast)
            }
        });
    }

    private void performProductSearch(String query) {
        productRepository.searchFood(query, new ProductRepository.SearchCallback() {
            @Override
            public void onSuccess(List<FoodProduct> items) {
                isSearchActive = false;

                if (query.equals(currentQuery)) {
                    lastSuccessfulQuery = query;
                    List<Searchable> searchableItems = new ArrayList<>(items);

                    if (ApiConfig.DEBUG_LOGGING) {
                        Log.d(TAG, "Product search successful: '" + query + "' returned " +
                                items.size() + " results");
                    }

                    notifySearchResults(searchableItems);
                } else {
                    if (ApiConfig.DEBUG_LOGGING) {
                        Log.d(TAG, "Ignoring outdated search results for: '" + query + "'");
                    }
                }
            }

            @Override
            public void onError(Error error) {
                isSearchActive = false;

                if (query.equals(currentQuery)) {
                    Log.w(TAG, "Product search failed for '" + query + "': " + error.getMessage());
                    notifySearchError(error);
                }
            }

            @Override
            public void onLoading() {
                if (query.equals(currentQuery)) {
                    notifySearchLoading();
                }
            }
        });
    }

    private void performRecipeSearch(String query) {
        if (recipeRepository == null) {
            isSearchActive = false;
            Log.e(TAG, "RecipeRepository is null - cannot perform recipe search");
            notifySearchError(Error.unknown(
                    "Recipe search not available",
                    "RecipeRepository not initialized"
            ));
            return;
        }

        notifySearchLoading();

        recipeRepository.search(query, currentLanguage, new RecipeRepository.RecipeSearchCallback() {
            @Override
            public void onSuccess(List<Recipe> recipes) {
                isSearchActive = false;

                if (query.equals(currentQuery)) {
                    lastSuccessfulQuery = query;
                    List<Searchable> searchableItems = new ArrayList<>(recipes);

                    if (ApiConfig.DEBUG_LOGGING) {
                        Log.d(TAG, "Recipe search successful: '" + query + "' returned " +
                                recipes.size() + " results");
                    }

                    notifySearchResults(searchableItems);
                } else {
                    if (ApiConfig.DEBUG_LOGGING) {
                        Log.d(TAG, "Ignoring outdated recipe search results for: '" + query + "'");
                    }
                }
            }

            @Override
            public void onError(String errorMessage) {
                isSearchActive = false;

                if (query.equals(currentQuery)) {
                    Log.w(TAG, "Recipe search failed for '" + query + "': " + errorMessage);
                    notifySearchError(Error.unknown("Recipe search failed", errorMessage));
                }
            }
        });
    }

    private void performParallelSearch(String query) {
        if (recipeRepository == null) {
            Log.w(TAG, "RecipeRepository is null - falling back to products only");
            performProductSearch(query);
            return;
        }

        isParallelSearchActive = true;
        parallelSearchesCompleted.set(0);
        parallelSearchesFailed.set(0);

        synchronized (parallelCombinedResults) {
            parallelCombinedResults.clear();
        }

        notifySearchLoading();

        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, "Starting parallel search for: '" + query + "'");
        }

        // Launch product search
        productRepository.searchFood(query, new ProductRepository.SearchCallback() {
            @Override
            public void onSuccess(List<FoodProduct> items) {
                synchronized (parallelCombinedResults) {
                    parallelCombinedResults.addAll(items);

                    if (ApiConfig.DEBUG_LOGGING) {
                        Log.d(TAG, "Products found in parallel search: " + items.size());
                    }

                    checkParallelSearchCompletion(query);
                }
            }

            @Override
            public void onError(Error error) {
                parallelSearchesFailed.incrementAndGet();

                if (ApiConfig.DEBUG_LOGGING) {
                    Log.e(TAG, "Product search failed in parallel search: " + error.getMessage());
                }

                checkParallelSearchCompletion(query);
            }

            @Override
            public void onLoading() {
                // Already notified
            }
        });

        // Launch recipe search
        recipeRepository.search(query, currentLanguage, new RecipeRepository.RecipeSearchCallback() {
            @Override
            public void onSuccess(List<Recipe> recipes) {
                synchronized (parallelCombinedResults) {
                    parallelCombinedResults.addAll(recipes);

                    if (ApiConfig.DEBUG_LOGGING) {
                        Log.d(TAG, "Recipes found in parallel search: " + recipes.size());
                    }

                    checkParallelSearchCompletion(query);
                }
            }

            @Override
            public void onError(String errorMessage) {
                parallelSearchesFailed.incrementAndGet();

                if (ApiConfig.DEBUG_LOGGING) {
                    Log.e(TAG, "Recipe search failed in parallel search: " + errorMessage);
                }

                checkParallelSearchCompletion(query);
            }
        });
    }

    private void checkParallelSearchCompletion(String query) {
        int completed = parallelSearchesCompleted.incrementAndGet();

        if (completed == 2) {
            isSearchActive = false;
            isParallelSearchActive = false;

            if (!query.equals(currentQuery)) {
                if (ApiConfig.DEBUG_LOGGING) {
                    Log.d(TAG, "Ignoring outdated parallel search results for: '" + query + "'");
                }
                return;
            }

            if (parallelSearchesFailed.get() == 2) {
                Log.w(TAG, "Both product and recipe searches failed for: '" + query + "'");
                notifySearchError(Error.network(
                        "Search failed for all sources",
                        "Both product and recipe searches failed"
                ));
                return;
            }

            List<Searchable> results;
            synchronized (parallelCombinedResults) {
                results = new ArrayList<>(parallelCombinedResults);
            }

            lastSuccessfulQuery = query;

            if (ApiConfig.DEBUG_LOGGING) {
                Log.d(TAG, "Parallel search completed: '" + query + "' returned " +
                        results.size() + " total results (" +
                        parallelSearchesFailed.get() + " sources failed)");
            }

            notifySearchResults(results);
        }
    }

    // ========== NOTIFICATIONS ==========

    private void notifySearchResults(List<Searchable> results) {
        if (listener != null) {
            try {
                listener.onSearchResults(results);
            } catch (Exception e) {
                Log.e(TAG, "Error in search results callback", e);
            }
        }
    }

    private void notifySearchError(Error error) {
        if (listener != null) {
            try {
                listener.onSearchError(error);
            } catch (Exception e) {
                Log.e(TAG, "Error in search error callback", e);
            }
        }
    }

    private void notifySearchLoading() {
        if (listener != null) {
            try {
                listener.onSearchLoading();
            } catch (Exception e) {
                Log.e(TAG, "Error in search loading callback", e);
            }
        }
    }

    private void notifySearchEmpty() {
        if (listener != null) {
            try {
                listener.onSearchEmpty();
            } catch (Exception e) {
                Log.e(TAG, "Error in search empty callback", e);
            }
        }
    }

    private void notifyMoreResults(List<Searchable> results) {
        if (listener != null) {
            try {
                listener.onMoreResults(results);
            } catch (Exception e) {
                Log.e(TAG, "Error in more results callback", e);
            }
        }
    }

    private void notifyMoreResultsError(Error error) {
        if (listener != null) {
            try {
                listener.onMoreResultsError(error);
            } catch (Exception e) {
                Log.e(TAG, "Error in more results error callback", e);
            }
        }
    }

    private void notifyLoadingMore() {
        if (listener != null) {
            try {
                listener.onLoadingMore();
            } catch (Exception e) {
                Log.e(TAG, "Error in loading more callback", e);
            }
        }
    }

    private void notifyAutocompleteSuggestions(List<String> suggestions) {
        if (autocompleteListener != null) {
            try {
                autocompleteListener.onAutocompleteSuggestions(suggestions);
            } catch (Exception e) {
                Log.e(TAG, "Error in autocomplete suggestions callback", e);
            }
        }
    }

    private void notifyQueryTooShort() {
        if (autocompleteListener != null) {
            try {
                autocompleteListener.onQueryTooShort();
            } catch (Exception e) {
                Log.e(TAG, "Error in query too short callback", e);
            }
        }
    }
}