package li.masciul.sugardaddi.data.sources.openfoodfacts;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import li.masciul.sugardaddi.data.sources.base.BaseDataSource;
import li.masciul.sugardaddi.data.sources.base.DataSourceCallback;
import li.masciul.sugardaddi.data.network.NetworkClient;
import li.masciul.sugardaddi.data.network.NetworkConfig;
import li.masciul.sugardaddi.core.models.Error;
import li.masciul.sugardaddi.core.logging.ErrorLogger;
import li.masciul.sugardaddi.data.sources.openfoodfacts.api.SearchAliciousAPI;
import li.masciul.sugardaddi.data.sources.openfoodfacts.api.OpenFoodFactsAPI;
import li.masciul.sugardaddi.data.sources.openfoodfacts.api.SearchAliciousConstants;
import li.masciul.sugardaddi.data.sources.openfoodfacts.api.dto.SearchAliciousResponse;
import li.masciul.sugardaddi.data.sources.openfoodfacts.api.dto.AutocompleteResponse;
import li.masciul.sugardaddi.core.models.FoodProduct;

import java.io.IOException;
import java.util.*;

import li.masciul.sugardaddi.data.sources.openfoodfacts.mappers.OpenFoodFactsMapper;
import li.masciul.sugardaddi.data.sources.openfoodfacts.mappers.SearchAliciousMapper;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * OpenFoodFactsDataSource - OpenFoodFacts integration with dual-API architecture
 *
 * Async Initialization Support
 *
 * CRITICAL FIX:
 * - Added onInitialize() method to support BaseDataSource async initialization
 * - Both sync and async initialization paths now work correctly
 * - searchApi and productApi are properly initialized in all cases
 *
 * ARCHITECTURE IMPROVEMENTS:
 * - Extends BaseDataSource (automatic thread handling, error logging)
 * - Dual API strategy: SearchAlicious for search + OFF v2 for details
 * - Uses OpenFoodFactsConfig with dual base URLs
 * - Proper Error model (replaces DataSourceException)
 * - SearchAliciousMapper for search results
 * - OpenFoodFactsMapper for product details
 *
 * DUAL-API STRATEGY:
 * ==================
 * WHY TWO APIs?
 * - search-a-licious: Optimized for SEARCH (ES-backed, fast, relevance scoring)
 * - OFF v2 API: Optimized for DETAILS (comprehensive product data)
 *
 * WHEN TO USE EACH:
 * - search() → SearchAlicious API (lightweight results, fast)
 * - getProduct() → OFF v2 API (complete nutrition data, allergens)
 * - autocomplete() → SearchAlicious API (typeahead suggestions)
 *
 * @author SugarDaddi Team
 * @version 2.1 (Async Initialization Fix)
 */
public class OpenFoodFactsDataSource extends BaseDataSource {

    private static final String TAG = "OpenFoodFactsDataSource";

    // ========== QUALITY THRESHOLD ==========

    /**
     * Minimum completeness score for search results
     * 0.8 = 80% complete products only (high quality)
     */
    private static final double MIN_COMPLETENESS = 0.8;

    // ========== DUAL API INSTANCES ==========

    /**
     * SearchAlicious API - For fast search operations
     * Base URL: https://search.openfoodfacts.org/
     */
    private SearchAliciousAPI searchApi;

    /**
     * OFF v2 API - For detailed product information
     * Base URL: https://world.openfoodfacts.org/api/v2/
     */
    private OpenFoodFactsAPI productApi;

    // ========== MAPPERS ==========

    /**
     * OpenFoodFactsMapper - Maps detailed product data to FoodProduct
     * Creates complete products with full nutrition data
     */
    private final OpenFoodFactsMapper productMapper;

    // ========== CONFIGURATION ==========

    private final OpenFoodFactsConfig config;
    private final Context context;

    // ========== ACTIVE CALLS TRACKING ==========

    /**
     * Track active Retrofit calls for cancellation support
     */
    private final Set<Call<?>> activeCalls = Collections.synchronizedSet(new HashSet<>());

    // ========== CONSTRUCTOR ==========

    /**
     * Create OpenFoodFactsDataSource with configuration
     *
     * @param context Android context
     * @param config OpenFoodFactsConfig with dual base URLs and retry strategy
     */
    public OpenFoodFactsDataSource(@NonNull Context context, @NonNull OpenFoodFactsConfig config) {
        super();  // BaseDataSource has no-arg constructor
        this.context = context.getApplicationContext();
        this.config = config;
        this.productMapper = new OpenFoodFactsMapper();
    }

    // ========== NETWORK CONFIG (REQUIRED BY BaseDataSource) ==========

    /**
     * Implement getNetworkConfig() required by BaseDataSource
     * Returns the OpenFoodFactsConfig which extends NetworkConfig
     */
    @NonNull
    @Override
    public NetworkConfig getNetworkConfig() {
        return config;
    }

    // ========== DATA SOURCE IDENTIFICATION (REQUIRED BY DataSource INTERFACE) ==========

    /**
     * Implement getSourceId() required by DataSource interface
     */
    @NonNull
    @Override
    public String getSourceId() {
        return OpenFoodFactsConstants.SOURCE_ID;
    }

    /**
     * Implement getSourceName() required by DataSource interface
     */
    @NonNull
    @Override
    public String getSourceName() {
        return OpenFoodFactsConstants.SOURCE_NAME;
    }

    // ========== INITIALIZATION ==========

    /**
     * Async initialization support via BaseDataSource
     *
     * This method is called by BaseDataSource.initialize(Context, InitializationCallback)
     * when async initialization is used (e.g., from DataSourceManager).
     *
     * CRITICAL: This method MUST create the searchApi and productApi instances,
     * otherwise they will be null when search() is called!
     *
     * @param context Application context
     * @throws Exception if initialization fails
     */
    @Override
    protected void onInitialize(@NonNull Context context) throws Exception {
        Log.d(TAG, "onInitialize() called - setting up APIs asynchronously");

        try {
            // Validate configuration
            config.validate();

            // Create SearchAlicious Retrofit instance
            Retrofit searchRetrofit = new Retrofit.Builder()
                    .baseUrl(config.getSearchBaseUrl())
                    .client(NetworkClient.createHttpClient(config, context))
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();

            searchApi = searchRetrofit.create(SearchAliciousAPI.class);
            Log.d(TAG, "SearchAlicious API initialized (async): " + config.getSearchBaseUrl());

            // Create OFF v2 Retrofit instance
            Retrofit productRetrofit = new Retrofit.Builder()
                    .baseUrl(config.getProductBaseUrl())
                    .client(NetworkClient.createHttpClient(config, context))
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();

            productApi = productRetrofit.create(OpenFoodFactsAPI.class);
            Log.d(TAG, "OpenFoodFacts v2 API initialized (async): " + config.getProductBaseUrl());

            Log.i(TAG, "OpenFoodFactsDataSource async initialization complete (quality threshold: " + MIN_COMPLETENESS + ")");

        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize OpenFoodFactsDataSource (async)", e);
            throw new Exception("Failed to initialize OpenFoodFacts APIs: " + e.getMessage(), e);
        }
    }

    /**
     * Synchronous initialization (required by DataSource interface)
     *
     * This method delegates to onInitialize() to avoid code duplication.
     * Both async and sync paths now use the same initialization logic.
     *
     * @param context Application context
     */
    @Override
    public void initialize(Context context) {
        if (initialized) {
            Log.d(TAG, "Already initialized, skipping");
            return;
        }

        try {
            Log.d(TAG, "Synchronous initialize() called - delegating to onInitialize()");
            onInitialize(context);
            initialized = true;
            Log.i(TAG, "Synchronous initialization complete");
        } catch (Exception e) {
            Log.e(TAG, "Synchronous initialization failed", e);
            ErrorLogger.log(Error.fromThrowable(e, "OFF initialization failed"));
        }
    }

    // ========== SEARCH OPERATIONS ==========

    /**
     * Search products using SearchAlicious API
     *
     * SEARCH STRATEGY:
     * - Uses SearchAlicious API for fast, relevance-scored results
     * - Quality filtering (MIN_COMPLETENESS threshold)
     * - Field selection for optimal performance
     * - Language-aware search with fallback
     *
     * @param query Search query
     * @param language Language code
     * @param limit Max results
     * @param callback Result callback
     */
    @Override
    public void search(@NonNull String query, @NonNull String language, int limit,
                       int page, @NonNull DataSourceCallback<SearchResult> callback) {
        if (!checkEnabled(callback)) return;

        // Verify APIs are initialized
        if (searchApi == null) {
            Log.e(TAG, "searchApi instance is NULL! Initialization did not complete properly.");
            Error error = Error.unknown("Search API not initialized", null);
            handleError(error, callback);
            return;
        }

        onOperationStart();

        // Validate inputs
        if (query == null || query.trim().isEmpty()) {
            handleError(Error.validation("Query cannot be empty", null), callback);
            return;
        }

        final String targetLanguage = (language == null || language.isEmpty())
                ? SearchAliciousConstants.Defaults.DEFAULT_LANGUAGE
                : language;

        Log.d(TAG, String.format("Search: query='%s', lang=%s, limit=%d, page=%d", query, targetLanguage, limit, page));

        // Build language list with fallback
        String languagesList = buildLanguagesList(targetLanguage);
        String qualityQuery = SearchAliciousConstants.QueryBuilder.withQualityFilter(query, MIN_COMPLETENESS);
        Log.d(TAG, "OFF Query: " + qualityQuery);  // See actual query sent

        // Call SearchAlicious API
        Call<SearchAliciousResponse> call = searchApi.search(
                qualityQuery,
                languagesList,
                limit,
                page,  // Use provided page number
                SearchAliciousConstants.SEARCH_RESULTS_FIELDS,
                SearchAliciousConstants.SortBy.POPULARITY
        );

        activeCalls.add(call);

        call.enqueue(new Callback<SearchAliciousResponse>() {
            @Override
            public void onResponse(Call<SearchAliciousResponse> call,
                                   Response<SearchAliciousResponse> response) {
                activeCalls.remove(call);

                if (!response.isSuccessful()) {
                    handleHttpError(response.code(), "Search failed", callback);
                    return;
                }

                SearchAliciousResponse searchResponse = response.body();
                if (searchResponse == null) {
                    handleError(Error.noData("No results found"), callback);
                    return;
                }

                // Map SearchAlicious results to FoodProduct domain models
                List<FoodProduct> products = SearchAliciousMapper.mapSearchResponse(
                        searchResponse,
                        targetLanguage
                );


                for (FoodProduct p : products) {
                    Log.d(TAG, p.getName() + " completeness: " + p.getDataCompleteness());
                }



                Log.d(TAG, String.format("Search success: %d results, %d total (quality threshold: %.1f)",
                        products.size(), searchResponse.getCount(), MIN_COMPLETENESS));

                // Create SearchResult container
                SearchResult result = new SearchResult(
                        products,
                        searchResponse.getCount(),
                        products.size() < searchResponse.getCount(),  // hasMore
                        query,
                        targetLanguage,
                        OpenFoodFactsConstants.SOURCE_ID
                );

                onOperationSuccess();

                // Success callback on main thread
                executeOnMainThread(() -> callback.onSuccess(result));
            }

            @Override
            public void onFailure(Call<SearchAliciousResponse> call, Throwable t) {
                activeCalls.remove(call);

                if (call.isCanceled()) {
                    Log.d(TAG, "Search call was cancelled");
                    return;
                }

                Log.e(TAG, "Search failed", t);
                Error error = createNetworkError(t, "Search failed");
                handleError(error, callback);
            }
        });
    }

    // ========== PRODUCT OPERATIONS ==========

    /**
     * Get product details using OFF v2 API
     *
     * DETAIL STRATEGY:
     * - Uses OFF v2 API for complete product data
     * - Returns full FoodProduct with nutrition, allergens, images
     * - More data than search results, slower but comprehensive
     *
     * @param productId Product barcode (EAN-8, EAN-13, UPC-A)
     * @param language Language code
     * @param callback Product callback
     */
    @Override
    public void getProduct(@NonNull String productId, @NonNull String language,
                           @NonNull DataSourceCallback<FoodProduct> callback) {
        if (!checkEnabled(callback)) return;

        // Verify APIs are initialized
        if (productApi == null) {
            Log.e(TAG, "productApi instance is NULL! Initialization did not complete properly.");
            Error error = Error.unknown("Product API not initialized", null);
            handleError(error, callback);
            return;
        }

        onOperationStart();

        // Validate barcode
        if (productId == null || productId.trim().isEmpty()) {
            handleError(Error.validation("Product ID cannot be empty", null), callback);
            return;
        }

        final String targetLanguage = (language == null || language.isEmpty()) ? "en" : language;

        Log.d(TAG, String.format("Fetching product: %s (lang: %s)", productId, targetLanguage));

        Call<OpenFoodFactsAPI.ProductResponse> call = productApi.getProduct(productId, targetLanguage);

        activeCalls.add(call);

        call.enqueue(new Callback<OpenFoodFactsAPI.ProductResponse>() {
            @Override
            public void onResponse(
                    Call<OpenFoodFactsAPI.ProductResponse> call,
                    Response<OpenFoodFactsAPI.ProductResponse> response) {
                activeCalls.remove(call);

                if (!response.isSuccessful()) {
                    handleHttpError(response.code(), "Product fetch failed", callback);
                    return;
                }

                OpenFoodFactsAPI.ProductResponse productResponse = response.body();
                if (productResponse == null || !productResponse.isValid()) {
                    handleError(Error.notFound("Product not found: " + productId), callback);
                    return;
                }

                FoodProduct product = productMapper.mapToDomainModel(
                        productResponse.getProduct(),
                        targetLanguage
                );

                if (product == null) {
                    handleError(Error.noData("Failed to parse product data"), callback);
                    return;
                }

                Log.d(TAG, String.format("Product fetched: %s", product.getName()));

                onOperationSuccess();
                executeOnMainThread(() -> callback.onSuccess(product));
            }

            @Override
            public void onFailure(
                    Call<OpenFoodFactsAPI.ProductResponse> call,
                    Throwable t) {
                activeCalls.remove(call);

                if (call.isCanceled()) {
                    Log.d(TAG, "Product fetch was cancelled");
                    return;
                }

                Log.e(TAG, "Product fetch failed", t);
                Error error = createNetworkError(t, "Failed to fetch product");
                handleError(error, callback);
            }
        });
    }

    /**
     * Get product by barcode - delegates to getProduct()
     */
    @Override
    public void getProductByBarcode(@NonNull String barcode, @NonNull String language,
                                    @NonNull DataSourceCallback<FoodProduct> callback) {
        getProduct(barcode, language, callback);
    }

    // ========== CAPABILITIES ==========

    @Override
    public boolean supportsBarcodeLookup() {
        return true;  // OFF supports barcode lookup
    }

    @NonNull
    @Override
    public Set<String> getSupportedLanguages() {
        // OFF supports many languages, return empty set = all languages
        return new HashSet<>();
    }

    @NonNull
    @Override
    public String getPrimaryLanguage() {
        return "en";  // OpenFoodFacts is international, English is default
    }

    // ========== HELPER METHODS ==========

    /**
     * Build comma-separated language list for fallback
     */
    private String buildLanguagesList(String primaryLanguage) {
        // Add fallback languages
        Set<String> languages = new LinkedHashSet<>();
        languages.add(primaryLanguage);
        languages.add("en");  // Always include English as fallback
        languages.add("fr");  // Always include French as fallback

        return String.join(",", languages);
    }

    /**
     * Handle HTTP errors
     */
    private <T> void handleHttpError(int code, String message, DataSourceCallback<T> callback) {
        Error error = Error.fromHttpCode(code, message, OpenFoodFactsConstants.SOURCE_ID);
        handleError(error, callback);
    }

    /**
     * Create network error from throwable
     */
    private Error createNetworkError(Throwable t, String message) {
        if (t instanceof IOException) {
            return Error.network(message, t.getMessage(), OpenFoodFactsConstants.SOURCE_ID);
        }
        return Error.fromThrowable(t, message);
    }

    // ========== CANCELLATION ==========

    @Override
    public void cancelOperations() {
        synchronized (activeCalls) {
            for (Call<?> call : activeCalls) {
                if (call != null && !call.isCanceled()) {
                    call.cancel();
                }
            }
            activeCalls.clear();
        }
        Log.d(TAG, "All operations cancelled");
    }

    @Override
    public void cleanup() {
        super.cleanup();
        cancelOperations();
        Log.d(TAG, "Cleanup completed");
    }
}