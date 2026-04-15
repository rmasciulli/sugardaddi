package li.masciul.sugardaddi.data.repository;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import androidx.lifecycle.LiveData;

// Database imports
import li.masciul.sugardaddi.core.models.Error;
import li.masciul.sugardaddi.data.database.entities.FoodProductEntity;
import li.masciul.sugardaddi.data.database.entities.NutritionEntity;
import li.masciul.sugardaddi.data.database.relations.FoodProductWithNutrition;
import li.masciul.sugardaddi.data.database.AppDatabase;

// Network imports
import li.masciul.sugardaddi.data.network.NetworkManager;
import li.masciul.sugardaddi.data.network.ApiConfig;
import li.masciul.sugardaddi.data.sources.openfoodfacts.mappers.OpenFoodFactsMapper;
import li.masciul.sugardaddi.data.sources.openfoodfacts.api.dto.OpenFoodFactsSearchResponse;

// DataSource imports
import li.masciul.sugardaddi.data.sources.base.DataSource;
import li.masciul.sugardaddi.data.sources.base.DataSourceCallback;
import li.masciul.sugardaddi.data.sources.base.DataSourceException;
import li.masciul.sugardaddi.data.sources.aggregation.AggregatedSearchResult;
import li.masciul.sugardaddi.data.sources.aggregation.DataSourceAggregator;

// Core models
import li.masciul.sugardaddi.core.models.FoodProduct;

// Managers and utilities
import li.masciul.sugardaddi.managers.LanguageManager;
import li.masciul.sugardaddi.managers.DataSourceManager;
import li.masciul.sugardaddi.data.sources.ciqual.CiqualDataSource;
import li.masciul.sugardaddi.core.utils.SearchFilter;

// Cache

import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

/**
 * ProductRepository - CONSOLIDATED DATA ACCESS LAYER
 *
 * *** REPOSITORY CONSOLIDATION v3.0 ***
 * This repository combines the functionality of the former FoodRepository and ProductRepository
 * into a single, comprehensive data access layer for all product operations.
 *
 * DUAL FUNCTIONALITY:
 * 1. SEARCH OPERATIONS (from FoodRepository):
 *    - Language-aware search with DataSource support
 *    - Multi-source search aggregation
 *    - Search caching and filtering
 *    - Advanced pagination and progress tracking
 *
 * 2. PRODUCT MANAGEMENT (from original ProductRepository):
 *    - Dual-table database storage (FoodProductEntity + NutritionEntity)
 *    - Favorite product management
 *    - Intelligent cache/network strategy with TTL
 *    - Background database operations
 *    - Lifecycle management and resource cleanup
 *
 * ARCHITECTURE BENEFITS:
 * - Single source of truth for all product operations
 * - Consistent threading model across all operations
 * - Unified error handling and logging
 * - Simplified dependency injection
 * - Better resource management
 *
 * MIGRATION NOTES:
 * - Replaces both FoodRepository and the old ProductRepository
 * - All existing interfaces preserved for backward compatibility
 * - SearchManager, SearchRepository, and UI components need minimal changes
 * - Database functionality enhanced with search capabilities
 */
public class ProductRepository {

    private static final String TAG = ApiConfig.SEARCH_LOG_TAG;

    // ========== CORE DEPENDENCIES ==========
    private final NetworkManager networkManager;
    private final Context context;
    private final ExecutorService backgroundExecutor;
    private final AppDatabase database;

    // ========== SEARCH INFRASTRUCTURE ==========
    private final FoodSearchCache searchCache;
    private final DataSourceManager dataSourceManager;
    private final DataSourceAggregator aggregator;

    // ========== CONFIGURATION ==========
    // Database cache settings
    private static final long DEFAULT_CACHE_VALIDITY_MS = 24 * 60 * 60 * 1000; // 24 hours
    private static final long FAVORITE_CACHE_VALIDITY_MS = 7 * 24 * 60 * 60 * 1000; // 7 days for favorites
    private long cacheValidityMs = DEFAULT_CACHE_VALIDITY_MS;

    // DataSource settings
    private boolean useDataSourceMode = true;

    // ========== STATE TRACKING ==========
    private String currentSearchQuery = "";
    private boolean isSearchInProgress = false;
    private boolean isOperationInProgress = false;

    // ========== CALLBACK INTERFACES ==========

    /**
     * Callback interface for search operations (from FoodRepository)
     */
    public interface SearchCallback {
        void onSuccess(List<FoodProduct> products);
        void onError(Error error);
        void onLoading();
    }

    /**
     * Callback interface for individual product operations (from ProductRepository)
     */
    public interface ProductCallback {
        void onSuccess(FoodProduct product);
        void onError(Error error);
        void onLoading();
    }

    /**
     * Callback interface for favorite operations (from ProductRepository)
     */
    public interface FavoriteCallback {
        void onFavoriteStatus(boolean isFavorite);
        void onFavoriteToggled(boolean newStatus);
        void onError(String message);
    }

    /**
     * Callback interface for batch operations (from ProductRepository)
     */
    public interface BatchCallback {
        void onComplete(int successCount, int failureCount);
        void onProgress(int current, int total);
        void onError(String message);
    }

    /**
     * Cache statistics callback (from ProductRepository)
     */
    public interface CacheStatsCallback {
        void onStats(CacheStatistics stats);
        void onError(String message);
    }

    // ========== CONSTRUCTOR ==========

    /**
     * Constructor - requires configured dependencies
     * Consolidates initialization from both former repositories
     */
    public ProductRepository(NetworkManager networkManager, Context context) {
        this.networkManager = networkManager;
        this.context = context.getApplicationContext();
        this.backgroundExecutor = Executors.newSingleThreadExecutor();
        this.database = AppDatabase.getInstance(context);

        // Initialize search infrastructure (from FoodRepository)
        this.searchCache = new FoodSearchCache();
        this.dataSourceManager = DataSourceManager.getInstance(context);
        this.aggregator = new DataSourceAggregator(context);

        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, "ProductRepository initialized with consolidated functionality (Search + Database)");
        }
    }

    // ========== SEARCH OPERATIONS ==========

    /**
     * Enhanced search method with DataSource support
     */
    public void searchFood(String query, SearchCallback callback) {
        Log.d(TAG, "========================================");
        Log.d(TAG, "searchFood() ENTRY");
        Log.d(TAG, "  query: '" + query + "'");
        Log.d(TAG, "  useDataSourceMode: " + useDataSourceMode);
        Log.d(TAG, "  dataSourceManager: " + (dataSourceManager != null ? "present" : "NULL"));
        Log.d(TAG, "========================================");

        if (callback == null) {
            Log.w(TAG, "SearchCallback is null - cannot return results");
            return;
        }

        String normalizedQuery = normalizeQuery(query);
        if (normalizedQuery == null) {
            Log.e(TAG, "Query normalized to NULL - failing");
            callback.onError(Error.network("Invalid search query", null));
            return;
        }

        Log.d(TAG, "Normalized query: '" + normalizedQuery + "'");
        currentSearchQuery = normalizedQuery;

        // Check cache first
        List<FoodProduct> cachedResults = searchCache.get(normalizedQuery);
        if (cachedResults != null) {
            if (ApiConfig.DEBUG_LOGGING) {
                Log.d(TAG, "Returning cached search results for: '" + normalizedQuery +
                        "' (" + cachedResults.size() + " items)");
            }
            // Enrich even on cache hit: the user may have opened a product detail
            // since this query was cached, adding richer OFF v2 data to the DB.
            // This is cheap (one batch DB query) and runs on the caller's thread —
            // the search cache path is already on a background-compatible path.
            backgroundExecutor.execute(() -> {
                enrichSearchResultsFromDatabase(cachedResults);
                runOnMainThread(() -> callback.onSuccess(cachedResults));
            });
            return;
        }

        // Use DataSource mode or legacy mode
        Log.d(TAG, "Cache miss - choosing search mode...");
        if (useDataSourceMode) {
            Log.d(TAG, ">>> CALLING performDataSourceSearch()");
            performDataSourceSearch(normalizedQuery, callback);
        } else {
            Log.d(TAG, ">>> CALLING performNetworkSearch()");
            performNetworkSearch(normalizedQuery, callback);
        }

        Log.d(TAG, "searchFood() EXIT");
    }


    /**
     * Search using DataSource (new mode) - ALL ENABLED SOURCES
     * REFACTORED v3.1: Now uses DataSourceAggregator to search ALL enabled sources in parallel
     *
     * PREVIOUS BEHAVIOR: Only searched primary source (OpenFoodFacts)
     * NEW BEHAVIOR: Searches ALL enabled sources (OpenFoodFacts + Ciqual + any others)
     *
     * BENEFITS:
     * - More comprehensive results from multiple databases
     * - Parallel execution for speed
     * - Automatic deduplication
     * - Priority-based result merging
     * - Detailed source statistics
     */
    private void performDataSourceSearch(String query, SearchCallback callback) {
        Log.d(TAG, "===========================================");
        Log.d(TAG, "performDataSourceSearch() ENTRY - MULTI-SOURCE MODE");
        Log.d(TAG, "===========================================");

        try {
            callback.onLoading();
            isSearchInProgress = true;

            // Get ALL enabled data sources (not just primary)
            Log.d(TAG, "Getting ALL enabled data sources...");
            List<DataSource> enabledSources = dataSourceManager.getEnabledDataSources();
            Log.d(TAG, "Found " + enabledSources.size() + " enabled sources");

            if (enabledSources.isEmpty()) {
                Log.e(TAG, "NO ENABLED SOURCES!");
                callback.onError(Error.network("No data sources available", null));
                isSearchInProgress = false;
                return;
            }

            // Log which sources will be searched
            if (ApiConfig.DEBUG_LOGGING) {
                Log.d(TAG, "Sources to be searched:");
                for (DataSource source : enabledSources) {
                    Log.d(TAG, "  - " + source.getSourceId() + " (available: " + source.isAvailable() + ")");
                }
            }

            // Use aggregator to search ALL sources in parallel
            Log.d(TAG, "Starting parallel search across all enabled sources...");
            aggregator.searchAll(query, new DataSourceAggregator.AggregatorCallback() {

                @Override
                public void onPartialResult(String sourceId,
                                            java.util.List<DataSource.SearchResult> partialResults) {
                    // A single source finished — show its results immediately.
                    // The user sees OFF results ~500ms in; Ciqual arrives and merges later.
                    if (partialResults.isEmpty()) return;
                    List<FoodProduct> partialItems = new ArrayList<>();
                    for (DataSource.SearchResult r : partialResults) {
                        partialItems.addAll(r.items);
                    }
                    List<FoodProduct> filtered = SearchFilter.filterAndSort(partialItems, query);
                    if (!filtered.isEmpty()) {
                        Log.d(TAG, "Partial results from " + sourceId + ": " + filtered.size());
                        // Enrich partial results from DB before showing.
                        // onPartialResult fires on main thread (via mainHandler.post),
                        // so we must enrich on a background thread then post back.
                        final List<FoodProduct> toShow = filtered;
                        backgroundExecutor.execute(() -> {
                            enrichSearchResultsFromDatabase(toShow);
                            runOnMainThread(() -> callback.onSuccess(toShow));
                        });
                    }
                }

                @Override
                public void onSearchComplete(AggregatedSearchResult result) {
                    isSearchInProgress = false;

                    // Extract all items from aggregated result
                    List<FoodProduct> foodProducts = new ArrayList<>(result.getItems());

                    // Apply existing filtering and sorting
                    List<FoodProduct> filteredProducts = SearchFilter.filterAndSort(foodProducts, query);

                    if (filteredProducts.isEmpty()) {
                        Log.w(TAG, "All results filtered out for query: '" + query + "'");
                        callback.onError(Error.noData("No results found for: " + query));
                        return;
                    }

                    // Enrich search results with any cached detail data from DB.
                    // If the user previously opened a product, its full OFF v2 data is
                    // in the Room DB and is richer than the Searchalicious lightweight version.
                    // This is source-agnostic: quality signals (dataCompleteness) drive the merge.
                    enrichSearchResultsFromDatabase(filteredProducts);

                    // Cache the enriched results
                    searchCache.put(query, filteredProducts);

                    if (ApiConfig.DEBUG_LOGGING) {
                        Log.d(TAG, "===========================================");
                        Log.d(TAG, "Multi-source search completed:");
                        Log.d(TAG, "  Query: '" + query + "'");
                        Log.d(TAG, "  Total results: " + foodProducts.size());
                        Log.d(TAG, "  After filtering: " + filteredProducts.size());
                        Log.d(TAG, "  Sources used: " + result.getSourceStats().size());
                        Log.d(TAG, "  Duplicates merged: " + result.getDuplicatesFound());
                        Log.d(TAG, "  Search duration: " + result.getSearchDurationMs() + "ms");
                        Log.d(TAG, "");
                        Log.d(TAG, result.getSummary());
                        Log.d(TAG, "===========================================");
                    }

                    // Final callback replaces the partial results with full merged set
                    callback.onSuccess(filteredProducts);
                }

                @Override
                public void onSearchError(String error) {
                    isSearchInProgress = false;
                    Log.e(TAG, "Multi-source search failed: " + error);
                    callback.onError(Error.network(error, null));
                }

                @Override
                public void onSearchProgress(String sourceId, int completed, int total) {
                    if (ApiConfig.DEBUG_LOGGING) {
                        Log.d(TAG, String.format("Search progress: %s completed (%d/%d)",
                                sourceId, completed, total));
                    }
                }
            });

            Log.d(TAG, "Aggregator.searchAll() called successfully - waiting for results...");
        } catch (Exception e) {
            Log.e(TAG, "EXCEPTION in performDataSourceSearch()", e);
            isSearchInProgress = false;
            callback.onError(Error.network("Search failed: " + e.getMessage(), e.getClass().getSimpleName()));
        }
    }

    /**
     * Search using aggregator (multiple sources)
     */
    public void searchFoodMultiSource(String query, SearchCallback callback) {
        String normalizedQuery = normalizeQuery(query);
        if (normalizedQuery == null) {
            callback.onError(Error.network("Invalid search query", null));
            return;
        }

        callback.onLoading();

        aggregator.searchAll(normalizedQuery, new DataSourceAggregator.AggregatorCallback() {
            @Override
            public void onSearchComplete(AggregatedSearchResult result) {
                List<FoodProduct> foodProducts = new ArrayList<>();

                for (FoodProduct product : result.getItems()) {
                    foodProducts.add(product);
                }

                if (ApiConfig.DEBUG_LOGGING) {
                    Log.d(TAG, "Multi-source search complete:\n" + result.getSummary());
                }

                callback.onSuccess(foodProducts);
            }

            @Override
            public void onSearchError(String error) {
                callback.onError(Error.network(error, null));
            }

            @Override
            public void onSearchProgress(String sourceId, int completed, int total) {
                if (ApiConfig.DEBUG_LOGGING) {
                    Log.d(TAG, String.format("Search progress: %s completed (%d/%d)",
                            sourceId, completed, total));
                }
            }
        });
    }

    /**
     * Advanced search with pagination
     *
     * UPDATED v3.2: Now uses DataSource mode for pagination consistency
     * - Uses same API as initial search (SearchAlicious)
     * - Benefits from popularity sorting
     * - Works with multi-source aggregation
     */
    public void searchFoodAdvanced(String query, int page, SearchCallback callback) {
        String normalizedQuery = normalizeQuery(query);
        if (normalizedQuery == null || callback == null) {
            if (callback != null) {
                callback.onError(Error.network("Invalid search parameters", null));
            }
            return;
        }

        Log.d(TAG, "searchFoodAdvanced: query='" + normalizedQuery + "', page=" + page);

        callback.onLoading();

        if (useDataSourceMode) {
            // Use DataSource mode with pagination (consistent with initial search)
            performDataSourceSearchWithPagination(normalizedQuery, page, callback);
        } else {
            // Legacy mode (deprecated - kept for fallback)
            performNetworkSearchPaginated(normalizedQuery, page, callback);
        }
    }

    /**
     * Perform paginated search using DataSource (NEW in v3.2)
     *
     * This ensures pagination uses the SAME API as initial search:
     * - SearchAlicious (not v2)
     * - Popularity sorting
     * - Same quality filters
     * - Multi-source support
     */
    private void performDataSourceSearchWithPagination(String query, int page, SearchCallback callback) {
        Log.d(TAG, "performDataSourceSearchWithPagination: page=" + page);

        try {
            // Get enabled data sources
            List<DataSource> enabledSources = dataSourceManager.getEnabledDataSources();

            if (enabledSources.isEmpty()) {
                callback.onError(Error.network("No data sources available", null));
                return;
            }

            // Use aggregator with pagination
            aggregator.searchAll(query, ApiConfig.API_PAGE_SIZE, page, new DataSourceAggregator.AggregatorCallback() {
                @Override
                public void onSearchComplete(AggregatedSearchResult result) {
                    List<FoodProduct> allProducts = new ArrayList<>(result.getItems());

                    // Apply filtering (same as initial search)
                    List<FoodProduct> filteredProducts = SearchFilter.filterAndSort(allProducts, query);

                    if (filteredProducts.isEmpty()) {
                        callback.onError(Error.noData("No more results found for: " + query));
                    } else {
                        if (ApiConfig.DEBUG_LOGGING) {
                            Log.d(TAG, "Pagination page " + page + ": " + filteredProducts.size() + " results");
                        }
                        callback.onSuccess(filteredProducts);
                    }
                }

                @Override
                public void onSearchError(String error) {
                    Log.w(TAG, "Pagination error: " + error);
                    callback.onError(Error.network(error, null));
                }

                @Override
                public void onSearchProgress(String sourceId, int completed, int total) {
                    // No progress tracking for pagination
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Exception during paginated search", e);
            callback.onError(Error.fromThrowable(e, "Pagination failed"));
        }
    }

    /**
     * Perform paginated search using NetworkManager (LEGACY)
     *
     * @deprecated Use performDataSourceSearchWithPagination instead
     * Kept for fallback compatibility only
     */
    @Deprecated
    private void performNetworkSearchPaginated(String query, int page, SearchCallback callback) {
        final OpenFoodFactsMapper offMapper = new OpenFoodFactsMapper();
        final String language = getCurrentLanguageCode();

        networkManager.searchProductsPaginated(
                query,
                page,
                ApiConfig.API_PAGE_SIZE,
                new NetworkManager.NetworkCallback<OpenFoodFactsSearchResponse>() {
                    @Override
                    public void onSuccess(OpenFoodFactsSearchResponse offSearchResponse) {
                        if (offSearchResponse == null || !offSearchResponse.hasResults()) {
                            callback.onError(Error.noData("No results found for: " + query));
                            return;
                        }

                        List<FoodProduct> products = offMapper.mapSearchResponse(offSearchResponse, language);
                        List<FoodProduct> filteredProducts = SearchFilter.filterAndSort(products, query);

                        if (filteredProducts.isEmpty()) {
                            callback.onError(Error.noData("No results found for: " + query));
                        } else {
                            callback.onSuccess(filteredProducts);
                        }
                    }

                    @Override
                    public void onFailure(String error) {
                        callback.onError(Error.noData("No results found for: " + query));
                    }
                }
        );
    }


    /**
     * Lightweight autocomplete search for dropdown suggestions
     *
     * ADDED v3.1 - For unified search refactoring
     *
     * This method performs a quick search optimized for autocomplete scenarios.
     * It returns a small number of results (typically 5-10) to populate a
     * suggestion dropdown as the user types.
     *
     * IMPLEMENTATION NOTES:
     * - Uses existing search infrastructure for consistency
     * - Limits results to 10 items for fast response
     * - Reuses searchFoodAdvanced with page 1 and small limit
     * - Debouncing is handled by SearchManager, not here
     * - Silent errors are OK (SearchManager handles gracefully)
     *
     * USAGE:
     * Called by SearchManager.autocomplete() to provide suggestions as user types.
     * Results are extracted to List<String> of product names for dropdown display.
     *
     * @param query Search query (minimum 3 characters recommended)
     * @param callback Callback for results (uses existing SearchCallback)
     */
    public void autocomplete(String query, SearchCallback callback) {
        // ========== VALIDATION ==========

        if (callback == null) {
            Log.w(TAG, "Autocomplete callback is null - cannot return results");
            return;
        }

        String normalizedQuery = normalizeQuery(query);
        if (normalizedQuery == null) {
            // Query too short or invalid
            callback.onError(Error.validation(
                    "Query too short for autocomplete",
                    "Minimum " + ApiConfig.MIN_SEARCH_LENGTH + " characters required"
            ));
            return;
        }

        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, "Autocomplete search: '" + normalizedQuery + "'");
        }

        // ========== CACHE CHECK ==========

        // Check if we have cached results for this query
        List<FoodProduct> cachedResults = searchCache.get(normalizedQuery);
        if (cachedResults != null && !cachedResults.isEmpty()) {
            // Return first 10 items from cache for fast autocomplete
            int limit = Math.min(10, cachedResults.size());
            List<FoodProduct> limitedResults = cachedResults.subList(0, limit);

            if (ApiConfig.DEBUG_LOGGING) {
                Log.d(TAG, "Autocomplete: returning " + limitedResults.size() +
                        " cached suggestions for '" + normalizedQuery + "'");
            }

            callback.onSuccess(limitedResults);
            return;
        }

        // ========== NETWORK SEARCH ==========

        // No loading indicator for autocomplete (too fast, would flicker)
        // Cache miss - perform lightweight network search

        if (useDataSourceMode) {
            // Use DataSource mode with limited results
            performAutocompleteDataSourceSearch(normalizedQuery, callback);
        } else {
            // Use legacy network mode with pagination (page 1, limit 10)
            performAutocompleteLegacySearch(normalizedQuery, callback);
        }
    }

    /**
     * Perform autocomplete using DataSource mode (limited results)
     *
     * UPDATED: Now uses dedicated autocomplete method for Ciqual with
     * match_phrase_prefix query for partial word matching.
     */
    private void performAutocompleteDataSourceSearch(String query, SearchCallback callback) {
        try {
            // Get enabled sources
            List<DataSource> enabledSources = dataSourceManager.getEnabledDataSources();

            if (enabledSources.isEmpty()) {
                callback.onError(Error.network("No data sources available", null));
                return;
            }

            // Get current language
            String language = LanguageManager.getCurrentLanguage(context).getCode();

            // For now, use Ciqual directly with dedicated autocomplete method
            // TODO: Add OpenFoodFacts autocomplete when ready
            DataSource ciqualSource = null;
            for (DataSource source : enabledSources) {
                if ("CIQUAL".equals(source.getSourceId())) {
                    ciqualSource = source;
                    break;
                }
            }

            if (ciqualSource != null && ciqualSource instanceof CiqualDataSource) {
                CiqualDataSource ciqual = (CiqualDataSource) ciqualSource;

                // Use dedicated autocomplete method (not regular search!)
                ciqual.autocomplete(query, language, 10, new DataSourceCallback<DataSource.SearchResult>() {
                    @Override
                    public void onSuccess(DataSource.SearchResult result) {
                        List<FoodProduct> products = new ArrayList<>(result.items);

                        // Apply filtering
                        List<FoodProduct> filteredProducts = SearchFilter.filterAndSort(products, query);

                        if (ApiConfig.DEBUG_LOGGING) {
                            Log.d(TAG, "Autocomplete (Ciqual): " + filteredProducts.size() +
                                    " suggestions for '" + query + "'");
                        }

                        // Don't cache autocomplete results (they're partial)
                        callback.onSuccess(filteredProducts);
                    }

                    @Override
                    public void onError(Error error) {
                        if (ApiConfig.DEBUG_LOGGING) {
                            Log.d(TAG, "Autocomplete error (silent): " + error.getMessage());
                        }
                        // Silent failure for autocomplete - return empty list
                        callback.onSuccess(new ArrayList<>());
                    }

                    @Override
                    public void onLoading() {
                        // No progress tracking for autocomplete
                    }
                });
            } else {
                // Fallback: Use aggregator with regular search if Ciqual not available
                // (This is the old behavior, kept as fallback)
                aggregator.searchAll(query, new DataSourceAggregator.AggregatorCallback() {
                    @Override
                    public void onSearchComplete(AggregatedSearchResult result) {
                        List<FoodProduct> allProducts = new ArrayList<>(result.getItems());

                        // Apply filtering
                        List<FoodProduct> filteredProducts = SearchFilter.filterAndSort(allProducts, query);

                        // Limit to 10 results for autocomplete
                        int limit = Math.min(10, filteredProducts.size());
                        List<FoodProduct> limitedResults = limit > 0 ?
                                filteredProducts.subList(0, limit) : filteredProducts;

                        if (ApiConfig.DEBUG_LOGGING) {
                            Log.d(TAG, "Autocomplete (DataSource fallback): " + limitedResults.size() +
                                    " suggestions for '" + query + "'");
                        }

                        // Don't cache autocomplete results (they're partial)
                        callback.onSuccess(limitedResults);
                    }

                    @Override
                    public void onSearchError(String error) {
                        if (ApiConfig.DEBUG_LOGGING) {
                            Log.d(TAG, "Autocomplete error (silent): " + error);
                        }
                        // Silent failure for autocomplete - return empty list
                        callback.onSuccess(new ArrayList<>());
                    }

                    @Override
                    public void onSearchProgress(String sourceId, int completed, int total) {
                        // No progress tracking for autocomplete
                    }
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "Autocomplete exception", e);
            // Silent failure - return empty list
            callback.onSuccess(new ArrayList<>());
        }
    }

    /**
     * Perform autocomplete using legacy network mode (paginated with limit)
     */
    private void performAutocompleteLegacySearch(String query, SearchCallback callback) {
        // Initialize mapper
        final OpenFoodFactsMapper offMapper = new OpenFoodFactsMapper();
        final String language = getCurrentLanguageCode();

        // Use paginated search with page 1 and small limit
        networkManager.searchProductsPaginated(
                query,
                1,              // Page 1
                10,             // Limit to 10 results for autocomplete
                new NetworkManager.NetworkCallback<OpenFoodFactsSearchResponse>() {
                    @Override
                    public void onSuccess(OpenFoodFactsSearchResponse offSearchResponse) {
                        if (offSearchResponse == null || !offSearchResponse.hasResults()) {
                            // No results - return empty list (silent failure for autocomplete)
                            callback.onSuccess(new ArrayList<>());
                            return;
                        }

                        List<FoodProduct> products = offMapper.mapSearchResponse(offSearchResponse, language);
                        List<FoodProduct> filteredProducts = SearchFilter.filterAndSort(products, query);

                        // Already limited by API, but ensure we don't exceed 10
                        int limit = Math.min(10, filteredProducts.size());
                        List<FoodProduct> limitedResults = limit > 0 ?
                                filteredProducts.subList(0, limit) : filteredProducts;

                        if (ApiConfig.DEBUG_LOGGING) {
                            Log.d(TAG, "Autocomplete (Legacy): " + limitedResults.size() +
                                    " suggestions for '" + query + "'");
                        }

                        callback.onSuccess(limitedResults);
                    }

                    @Override
                    public void onFailure(String error) {
                        if (ApiConfig.DEBUG_LOGGING) {
                            Log.d(TAG, "Autocomplete network error (silent): " + error);
                        }
                        // Silent failure - return empty list for better UX
                        callback.onSuccess(new ArrayList<>());
                    }
                }
        );
    }


    // ========== INDIVIDUAL PRODUCT OPERATIONS ==========

    /**
     * Load product details with intelligent cache/network strategy
     */
    public void loadProduct(String barcode, ProductCallback callback) {
        if (barcode == null || barcode.trim().isEmpty()) {
            callback.onError(Error.invalidRequest("Invalid barcode provided", null));
            return;
        }

        final String cleanBarcode = barcode.trim();
        callback.onLoading();
        isOperationInProgress = true;

        backgroundExecutor.execute(() -> {
            try {
                // Try to load from cache first (joining both tables)
                FoodProductWithNutrition cached = database.combinedProductDao()
                        .getProductWithNutrition(cleanBarcode);

                if (cached != null && cached.product != null) {
                    // Update access tracking
                    cached.product.recordAccess();
                    database.foodProductDao().updateProduct(cached.product);

                    // Check cache freshness
                    long cacheAge = System.currentTimeMillis() - cached.product.getLastUpdated();
                    long maxAge = cached.product.isFavorite() ?
                            FAVORITE_CACHE_VALIDITY_MS : cacheValidityMs;

                    if (cacheAge < maxAge) {
                        // Cache is fresh - use it
                        FoodProduct product = cached.toFoodProduct();

                        if (ApiConfig.DEBUG_LOGGING) {
                            Log.d(TAG, "Loaded product from database cache: " + cleanBarcode +
                                    " (age: " + (cacheAge / 1000) + "s)");
                        }

                        isOperationInProgress = false;
                        runOnMainThread(() -> callback.onSuccess(product));
                        return;
                    } else {
                        if (ApiConfig.DEBUG_LOGGING) {
                            Log.d(TAG, "Database cache stale for: " + cleanBarcode +
                                    " (age: " + (cacheAge / 1000) + "s)");
                        }
                    }
                }

                // Cache miss or stale - fetch from network
                runOnMainThread(() -> fetchFromNetwork(cleanBarcode, callback));

            } catch (Exception e) {
                Log.e(TAG, "Database error loading product", e);
                isOperationInProgress = false;
                // Fall back to network on database error
                runOnMainThread(() -> fetchFromNetwork(cleanBarcode, callback));
            }
        });
    }

    /**
     * Load product from specific data source using source-specific ID
     *
     * This method enables loading products that use source-specific identifiers
     * rather than standard barcodes. For example:
     * - Ciqual products: Use internal IDs like "31020"
     * - Custom sources: May use their own ID systems
     *
     * PROCESS:
     * 1. Get the specified DataSource from DataSourceManager
     * 2. Validate the source exists and is available
     * 3. Call source.getProduct() with the product ID
     * 4. Save successful results to database
     * 5. Return product via callback
     *
     * IMPORTANT NOTES:
     * - Does NOT use database cache (source-specific IDs may not be in DB)
     * - Always fetches fresh from the specified source
     * - Saves to database after successful fetch for future barcode lookups
     *
     * @param sourceId Data source identifier (e.g., "CIQUAL", "OPENFOODFACTS")
     * @param productId Source-specific product ID (e.g., "31020" for Ciqual)
     * @param callback Product callback for results
     */
    public void loadProductFromSource(String sourceId, String productId, ProductCallback callback) {
        if (sourceId == null || sourceId.trim().isEmpty()) {
            callback.onError(Error.validation("Source ID cannot be empty", null));
            return;
        }

        if (productId == null || productId.trim().isEmpty()) {
            callback.onError(Error.validation("Product ID cannot be empty", null));
            return;
        }

        final String cleanSourceId = sourceId.trim();
        final String cleanProductId = productId.trim();

        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, String.format("Loading product from source: %s, ID: %s",
                    cleanSourceId, cleanProductId));
        }

        callback.onLoading();
        isOperationInProgress = true;

        // Get the specified data source
        DataSource source = dataSourceManager.getDataSource(cleanSourceId);

        if (source == null) {
            String errorMsg = String.format("Data source '%s' not found or not registered", cleanSourceId);
            Log.e(TAG, errorMsg);
            isOperationInProgress = false;
            callback.onError(Error.validation(errorMsg, "Available sources: " +
                    dataSourceManager.getAllDataSources().size()));
            return;
        }

        // Check if source is available and initialized
        if (!source.isAvailable()) {
            String errorMsg = String.format("Data source '%s' is not available or not initialized",
                    cleanSourceId);
            Log.w(TAG, errorMsg);
            isOperationInProgress = false;
            callback.onError(Error.validation(errorMsg,
                    "Source may still be initializing. Try again in a moment."));
            return;
        }

        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, String.format("Found available source: %s (%s)",
                    source.getSourceName(), cleanSourceId));
        }

        // Get current language for the request
        String language = LanguageManager.getCurrentLanguage(context).getCode();

        // Call the source's getProduct method
        source.getProduct(cleanProductId, language, new DataSourceCallback<FoodProduct>() {
            @Override
            public void onSuccess(FoodProduct product) {
                if (ApiConfig.DEBUG_LOGGING) {
                    Log.d(TAG, String.format("Successfully loaded product from %s: %s",
                            cleanSourceId, product.getName()));
                }

                // Save to database for future lookups
                // (if product has a barcode, it can be found later via barcode search)
                saveProductToDatabase(product, false);

                isOperationInProgress = false;
                callback.onSuccess(product);
            }

            @Override
            public void onError(Error error) {
                Log.w(TAG, String.format("Failed to load product from %s: %s",
                        cleanSourceId, error.getMessage()));

                isOperationInProgress = false;
                callback.onError(error);
            }

            @Override
            public void onLoading() {
                // Already handled by callback.onLoading() above
            }
        });
    }

    /**
     * Get product by barcode with DataSource support
     */
    public void getProductByBarcode(String barcode, ProductCallback callback) {
        if (callback == null || barcode == null || barcode.trim().isEmpty()) {
            if (callback != null) {
                callback.onError(Error.network("Invalid barcode", null));
            }
            return;
        }

        callback.onLoading();

        if (useDataSourceMode) {
            DataSource primarySource = dataSourceManager.getPrimaryDataSource();
            if (primarySource == null) {
                callback.onError(Error.network("No data sources available", null));
                return;
            }

            String language = LanguageManager.getCurrentLanguage(context).getCode();

            primarySource.getProductByBarcode(barcode, language,
                    new DataSourceCallback<FoodProduct>() {
                        @Override
                        public void onSuccess(FoodProduct foodProduct) {
                            // Save to database as well
                            saveProductToDatabase(foodProduct, false);
                            callback.onSuccess(foodProduct);
                        }

                        @Override
                        public void onError(Error error) {
                            callback.onError(error);
                        }

                        @Override
                        public void onLoading() {
                            // Already handled
                        }
                    });
        } else {
            // Legacy mode - use NetworkManager directly
            networkManager.getProduct(barcode, new NetworkManager.NetworkCallback<FoodProduct>() {
                @Override
                public void onSuccess(FoodProduct product) {
                    // Save to database as well
                    saveProductToDatabase(product, false);
                    callback.onSuccess(product);
                }

                @Override
                public void onFailure(String error) {
                    callback.onError(Error.network(error, null));
                }
            });
        }
    }

    /**
     * Force refresh product from network (ignores cache)
     */
    public void refreshProduct(String barcode, ProductCallback callback) {
        if (barcode == null || barcode.trim().isEmpty()) {
            callback.onError(Error.invalidRequest("Invalid barcode provided", null));
            return;
        }

        callback.onLoading();
        fetchFromNetwork(barcode.trim(), callback);
    }

    // ========== FAVORITE MANAGEMENT (from ProductRepository) ==========

    /**
     * Get favorite status for a product
     */
    public void getFavoriteStatus(String productId, FavoriteCallback callback) {
        if (productId == null || productId.trim().isEmpty()) {
            callback.onError("Invalid product ID");
            return;
        }

        backgroundExecutor.execute(() -> {
            try {
                // Use getProductById (same as toggleFavorite) instead of getProductByBarcode
                FoodProductEntity product = database.foodProductDao()
                        .getProductById(productId.trim());
                boolean isFavorite = product != null && product.isFavorite();
                runOnMainThread(() -> callback.onFavoriteStatus(isFavorite));
            } catch (Exception e) {
                Log.e(TAG, "Error checking favorite status", e);
                runOnMainThread(() -> callback.onError("Could not check favorite status"));
            }
        });
    }

    /**
     * Toggle favorite status for a product
     */
    public void toggleFavorite(FoodProduct product, FavoriteCallback callback) {
        if (product == null || product.getSearchableId() == null) {
            callback.onError("Invalid product");
            return;
        }

        backgroundExecutor.execute(() -> {
            try {
                String productId = product.getSearchableId();
                FoodProductEntity entity = database.foodProductDao()
                        .getProductById(productId);

                if (entity != null) {
                    // Toggle existing product
                    boolean newStatus = !entity.isFavorite();
                    entity.setFavorite(newStatus);
                    entity.setUpdatedAt(System.currentTimeMillis());
                    database.foodProductDao().updateProduct(entity);

                    if (ApiConfig.DEBUG_LOGGING) {
                        Log.d(TAG, "Toggled favorite: " + productId + " -> " + newStatus);
                    }

                    runOnMainThread(() -> callback.onFavoriteToggled(newStatus));

                } else {
                    // Product not in database - save it as favorite
                    saveProductToDatabase(product, true);

                    if (ApiConfig.DEBUG_LOGGING) {
                        Log.d(TAG, "Saved new favorite: " + productId);
                    }

                    runOnMainThread(() -> callback.onFavoriteToggled(true));
                }

            } catch (Exception e) {
                Log.e(TAG, "Database error toggling favorite", e);
                runOnMainThread(() -> callback.onError("Could not update favorite status"));
            }
        });
    }

    /**
     * Get all favorite products
     */
    public LiveData<List<FoodProductWithNutrition>> getFavoriteProducts() {
        return database.combinedProductDao().getFavoriteProductsWithNutrition();
    }

    // ========== DATASOURCE MANAGEMENT ==========

    /**
     * Enable or disable DataSource mode
     */
    public void setDataSourceMode(boolean enabled) {
        this.useDataSourceMode = enabled;
        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, "DataSource mode " + (enabled ? "enabled" : "disabled"));
        }
    }

    /**
     * Get available data sources
     */
    public List<DataSource> getAvailableDataSources() {
        return dataSourceManager.getEnabledDataSources();
    }

    // ========== CACHE MANAGEMENT ==========

    /**
     * Cancel current search operations
     */
    public void cancelCurrentSearch() {
        if (isSearchInProgress) {
            networkManager.cancelCurrentSearch();
            isSearchInProgress = false;

            if (ApiConfig.DEBUG_LOGGING) {
                Log.d(TAG, "Cancelled ongoing search for: '" + currentSearchQuery + "'");
            }
        }
    }

    /**
     * Cancel any ongoing network operations
     */
    public void cancelCurrentOperation() {
        networkManager.cancelCurrentProductFetch();
        isOperationInProgress = false;

        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, "Cancelled ongoing product operations");
        }
    }

    /**
     * Clear search cache
     */
    public void clearCache() {
        searchCache.clear();
        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, "Search cache cleared");
        }
    }

    /**
     * Clear all non-favorite products from database cache
     */
    public void clearNonFavoriteCache() {
        backgroundExecutor.execute(() -> {
            try {
                database.foodProductDao().clearNonFavoriteCache();
                // Also clean up orphaned nutrition entries
                database.nutritionDao().deleteOrphanedNutrition();

                if (ApiConfig.DEBUG_LOGGING) {
                    Log.d(TAG, "Cleared non-favorite database cache");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error clearing database cache", e);
            }
        });
    }

    /**
     * Clear entire cache (both search and database)
     */
    public void clearAllCache() {
        // Clear search cache
        searchCache.clear();

        // Clear database cache
        backgroundExecutor.execute(() -> {
            try {
                database.foodProductDao().clearAllProducts();
                database.nutritionDao().clearAllNutrition();
                database.mealDao().deleteAll();
                database.recipeDao().deleteAll();

                if (ApiConfig.DEBUG_LOGGING) {
                    Log.d(TAG, "Cleared all cache (search + database)");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error clearing all cache", e);
            }
        });
    }

    /**
     * Get search cache statistics
     */
    public String getCacheStats() {
        return searchCache.getStats();
    }

    /**
     * Set database cache validity duration
     */
    public void setCacheValidity(long milliseconds) {
        this.cacheValidityMs = milliseconds;

        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, "Database cache validity set to: " + (milliseconds / 1000) + " seconds");
        }
    }

    // ========== BATCH OPERATIONS ==========

    /**
     * Load multiple products efficiently
     */
    public void loadProducts(List<String> barcodes, BatchCallback callback) {
        if (barcodes == null || barcodes.isEmpty()) {
            callback.onComplete(0, 0);
            return;
        }

        backgroundExecutor.execute(() -> {
            int successCount = 0;
            int failureCount = 0;
            int total = barcodes.size();

            for (int i = 0; i < barcodes.size(); i++) {
                String barcode = barcodes.get(i);

                try {
                    // Try cache first
                    FoodProductWithNutrition cached = database.combinedProductDao()
                            .getProductWithNutrition(barcode);

                    if (cached != null && cached.product != null &&
                            !cached.product.isStale(cacheValidityMs)) {
                        successCount++;
                    } else {
                        // Would need network fetch - count as pending
                        failureCount++;
                    }

                    // Report progress
                    final int current = i + 1;
                    runOnMainThread(() -> callback.onProgress(current, total));

                } catch (Exception e) {
                    Log.e(TAG, "Error loading product: " + barcode, e);
                    failureCount++;
                }
            }

            final int finalSuccess = successCount;
            final int finalFailure = failureCount;
            runOnMainThread(() -> callback.onComplete(finalSuccess, finalFailure));
        });
    }

    /**
     * Get cache statistics
     */
    public void getCacheStatistics(CacheStatsCallback callback) {
        backgroundExecutor.execute(() -> {
            try {
                int productCount = database.foodProductDao().getProductCount();
                int nutritionCount = database.nutritionDao().getNutritionCount();
                int favoriteCount = database.foodProductDao().getFavoriteCount();

                CacheStatistics stats = new CacheStatistics(
                        productCount, nutritionCount, favoriteCount);

                runOnMainThread(() -> callback.onStats(stats));

            } catch (Exception e) {
                Log.e(TAG, "Error getting cache statistics", e);
                runOnMainThread(() -> callback.onError("Could not get cache statistics"));
            }
        });
    }

    // ========== STATE QUERIES ==========

    /**
     * Check if a search operation is in progress
     */
    public boolean isSearchInProgress() {
        return isSearchInProgress;
    }

    /**
     * Get current search query
     */
    public String getCurrentSearchQuery() {
        return currentSearchQuery;
    }

    /**
     * Check if any operation is in progress
     */
    public boolean isOperationInProgress() {
        return isOperationInProgress || isSearchInProgress;
    }

    // ========== LIFECYCLE MANAGEMENT ==========

    /**
     * Shutdown repository and clean up resources
     */
    public void shutdown() {
        cancelCurrentOperation();
        cancelCurrentSearch();
        backgroundExecutor.shutdown();

        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, "ProductRepository shutdown complete");
        }
    }

    // ========== PRIVATE IMPLEMENTATION METHODS ==========

    /**
     * Normalize search query
     */
    private String normalizeQuery(String query) {
        if (query == null) return null;

        String normalized = query.trim();
        if (normalized.length() < ApiConfig.MIN_SEARCH_LENGTH) {
            return null;
        }

        // Remove excessive whitespace and normalize
        normalized = normalized.replaceAll("\\s+", " ");

        return normalized;
    }

    /**
     * Use language-aware network search
     */
    private void performNetworkSearch(String query, SearchCallback callback) {
        callback.onLoading();
        isSearchInProgress = true;

        // Initialize mapper
        final OpenFoodFactsMapper offMapper = new OpenFoodFactsMapper();
        final String language = getCurrentLanguageCode();

        // Use language-aware search
        networkManager.searchProducts(query, new NetworkManager.NetworkCallback<OpenFoodFactsSearchResponse>() {
            @Override
            public void onSuccess(OpenFoodFactsSearchResponse offSearchResponse) {
                isSearchInProgress = false;

                if (offSearchResponse == null || !offSearchResponse.hasResults()) {
                    if (ApiConfig.DEBUG_LOGGING) {
                        Log.d(TAG, "Search returned no results for: '" + query + "'");
                    }
                    callback.onError(Error.noData("No results found for: " + query));
                    return;
                }

                // Apply intelligent filtering and ranking
                List<FoodProduct> allProducts = offMapper.mapSearchResponse(offSearchResponse, language);
                List<FoodProduct> filteredProducts = SearchFilter.filterAndSort(allProducts, query);

                if (filteredProducts.isEmpty()) {
                    if (ApiConfig.DEBUG_LOGGING) {
                        Log.d(TAG, "All products filtered out for query: '" + query +
                                "' (started with " + allProducts.size() + " products)");
                    }
                    callback.onError(Error.noData("No results found for: " + query));
                    return;
                }

                // Enrich from DB cache before storing (same logic as DataSource mode)
                enrichSearchResultsFromDatabase(filteredProducts);

                // Cache the enriched results for future use
                searchCache.put(query, filteredProducts);

                if (ApiConfig.DEBUG_LOGGING) {
                    Log.d(TAG, String.format("Search completed: %d filtered results from %d total for '%s'",
                            filteredProducts.size(), allProducts.size(), query));
                }

                callback.onSuccess(filteredProducts);
            }

            @Override
            public void onFailure(String error) {
                isSearchInProgress = false;

                Log.e(TAG, "Network search failed for '" + query + "': " + error);

                // Convert generic error string to structured Error
                Error apiError;
                if (error.toLowerCase().contains("network") || error.toLowerCase().contains("connection")) {
                    apiError = Error.network(error, null);
                } else if (error.toLowerCase().contains("timeout")) {
                    apiError = Error.network("Request timed out. Please try again.", null);
                } else {
                    apiError = Error.unknown(error, null);
                }

                callback.onError(apiError);
            }
        });
    }

    /**
     * Fetch product from network using NetworkManager
     */
    private void fetchFromNetwork(String barcode, ProductCallback callback) {
        networkManager.getProduct(barcode, new NetworkManager.NetworkCallback<FoodProduct>() {
            @Override
            public void onSuccess(FoodProduct product) {
                if (ApiConfig.DEBUG_LOGGING) {
                    Log.d(TAG, "Network fetch successful for: " + barcode);
                }

                // Save to both tables
                saveProductToDatabase(product, false);

                isOperationInProgress = false;
                callback.onSuccess(product);
            }

            @Override
            public void onFailure(String error) {
                Log.w(TAG, "Network fetch failed for " + barcode + ": " + error);

                // Convert network error to structured Error
                Error apiError;
                if (error.toLowerCase().contains("not found")) {
                    apiError = Error.noData("No product found for barcode: " + barcode);
                } else if (error.toLowerCase().contains("network")) {
                    apiError = Error.network(error, null);
                } else {
                    apiError = Error.network("Failed to load product: " + error, null);
                }

                isOperationInProgress = false;
                callback.onError(apiError);
            }
        });
    }

    /**
     * Save product to BOTH database tables
     *
     * @param product Product to save
     * @param asFavorite Whether to mark as favorite
     */
    /**
     * Enrich search result products with higher-quality data from the Room database.
     *
     * WHAT THIS DOES:
     * When the user has previously opened a product detail view, the full API response
     * (e.g. OFF v2) was saved to Room DB. That version has more complete data than
     * the lightweight Searchalicious search result: better category (agribalyse name),
     * scores, nutrition, etc. This method upgrades matching search results in-place.
     *
     * HOW IT WORKS (source-agnostic):
     * 1. Collect barcodes from all barcoded search results (one batch DB query)
     * 2. Collect searchable IDs from non-barcoded results (one batch DB query)
     * 3. Build a lookup map from barcode/id → cached FoodProductEntity
     * 4. For each search result, call product.enrichWith(cachedProduct) if found
     *    enrichWith() uses dataCompleteness as the quality gate — never downgrades
     *
     * PERFORMANCE:
     * - Runs on the background thread already used by the search completion callback
     * - Two batch DB queries regardless of result set size (not N individual queries)
     * - Modifies the list in-place before searchCache.put() — cache already holds enriched data
     *
     * @param products Search result products to potentially enrich (modified in-place)
     */
    public void enrichSearchResultsFromDatabase(List<FoodProduct> products) {
        if (products == null || products.isEmpty()) return;

        try {
            // Separate barcoded products from non-barcoded (e.g. Ciqual)
            List<String> barcodes = new ArrayList<>();
            List<String> searchableIds = new ArrayList<>();

            for (FoodProduct product : products) {
                if (product.getBarcode() != null && !product.getBarcode().trim().isEmpty()) {
                    barcodes.add(product.getBarcode().trim());
                } else {
                    String sid = product.getSearchableId();
                    if (sid != null && !sid.trim().isEmpty()) {
                        searchableIds.add(sid.trim());
                    }
                }
            }

            // Batch DB queries — one per identity type
            Map<String, FoodProduct> barcodeToRicher = new HashMap<>();
            Map<String, FoodProduct> idToRicher = new HashMap<>();

            if (!barcodes.isEmpty()) {
                List<FoodProductEntity> cached = database.foodProductDao()
                        .getProductsByBarcodes(barcodes);
                for (FoodProductEntity entity : cached) {
                    if (entity.getBarcode() != null) {
                        barcodeToRicher.put(entity.getBarcode(), entity.toFoodProduct());
                    }
                }
            }

            if (!searchableIds.isEmpty()) {
                List<FoodProductEntity> cached = database.foodProductDao()
                        .getProductsBySearchableIds(searchableIds);
                for (FoodProductEntity entity : cached) {
                    if (entity.getId() != null) {
                        idToRicher.put(entity.getId(), entity.toFoodProduct());
                    }
                }
            }

            // Enrich each product if a richer version exists
            int enrichedCount = 0;
            for (FoodProduct product : products) {
                FoodProduct richer = null;

                if (product.getBarcode() != null && !product.getBarcode().trim().isEmpty()) {
                    richer = barcodeToRicher.get(product.getBarcode().trim());
                }
                if (richer == null) {
                    String sid = product.getSearchableId();
                    if (sid != null) {
                        richer = idToRicher.get(sid.trim());
                    }
                }

                if (richer != null) {
                    product.enrichWith(richer);
                    enrichedCount++;
                }
            }

            if (ApiConfig.DEBUG_LOGGING && enrichedCount > 0) {
                Log.d(TAG, "enrichSearchResultsFromDatabase: enriched " + enrichedCount
                        + "/" + products.size() + " products from DB cache");
            }

        } catch (Exception e) {
            // Never crash the search flow — enrichment is a best-effort enhancement
            Log.w(TAG, "enrichSearchResultsFromDatabase failed (non-fatal): " + e.getMessage());
        }
    }

    private void saveProductToDatabase(FoodProduct product, boolean asFavorite) {
        if (product == null) return;

        backgroundExecutor.execute(() -> {
            try {
                // 1. Check if product already exists and preserve favorite status
                FoodProductEntity existingEntity = database.foodProductDao()
                        .getProductById(product.getSearchableId());
                boolean preserveFavorite = (existingEntity != null && existingEntity.isFavorite());

                // 2. Prepare and save product entity (WITHOUT nutrition)
                FoodProductEntity productEntity = FoodProductEntity.fromFoodProduct(product);
                if (asFavorite || preserveFavorite) {
                    productEntity.setFavorite(true);
                }
                productEntity.markAsUpdated();

                database.foodProductDao().insertProduct(productEntity);

                // 2. Save nutrition separately if present
                if (product.getNutrition() != null) {
                    NutritionEntity nutritionEntity = NutritionEntity.fromNutrition(
                            product.getNutrition(),
                            "product",
                            product.getSearchableId()
                    );
                    database.nutritionDao().insertNutrition(nutritionEntity);

                    if (ApiConfig.DEBUG_LOGGING) {
                        Log.d(TAG, "Saved product with nutrition to both tables: " +
                                product.getSearchableId() + " (favorite: " + asFavorite + ")");
                    }
                } else {
                    if (ApiConfig.DEBUG_LOGGING) {
                        Log.d(TAG, "Saved product without nutrition: " +
                                product.getSearchableId() + " (favorite: " + asFavorite + ")");
                    }
                }

            } catch (Exception e) {
                Log.e(TAG, "Failed to save product to database", e);
            }
        });
    }

    /**
     * Get current language for API requests
     */
    private String getCurrentLanguageCode() {
        try {
            // Read directly from SharedPreferences for consistency
            SharedPreferences prefs = context.getSharedPreferences("language_pref", Context.MODE_PRIVATE);
            String savedLanguage = prefs.getString("selected_language", null);

            if (savedLanguage != null) {
                Log.d(TAG, "Language from prefs: " + savedLanguage);
                return savedLanguage;
            }

            // Fallback to LanguageManager
            LanguageManager.SupportedLanguage currentLang = LanguageManager.getCurrentLanguage(context);
            Log.d(TAG, "Language from manager: " + currentLang.getCode());
            return currentLang.getCode();
        } catch (Exception e) {
            Log.e(TAG, "Failed to get language code, using default", e);
            return "en"; // Safe fallback
        }
    }

    /**
     * Run task on main thread
     */
    private void runOnMainThread(Runnable runnable) {
        new android.os.Handler(android.os.Looper.getMainLooper()).post(runnable);
    }

    // ========== INNER CLASSES ==========

    /**
     * Cache statistics
     */
    public static class CacheStatistics {
        public final int productCount;
        public final int nutritionCount;
        public final int favoriteCount;

        CacheStatistics(int productCount, int nutritionCount, int favoriteCount) {
            this.productCount = productCount;
            this.nutritionCount = nutritionCount;
            this.favoriteCount = favoriteCount;
        }
    }
}