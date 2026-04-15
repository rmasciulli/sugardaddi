package li.masciul.sugardaddi.data.network;

import android.content.Context;
import android.net.ConnectivityManager;
import android.util.Log;

import li.masciul.sugardaddi.data.sources.openfoodfacts.api.dto.OpenFoodFactsSearchResponse;
import li.masciul.sugardaddi.data.sources.openfoodfacts.api.dto.OpenFoodFactsProduct;
import li.masciul.sugardaddi.data.sources.openfoodfacts.api.OpenFoodFactsAPI;
import li.masciul.sugardaddi.data.sources.openfoodfacts.OpenFoodFactsConstants;
import li.masciul.sugardaddi.data.sources.openfoodfacts.mappers.OpenFoodFactsMapper;
import li.masciul.sugardaddi.managers.LanguageManager;
import li.masciul.sugardaddi.core.models.FoodProduct;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * NetworkManager - Language-Aware Network Communication Layer
 *
 * ARCHITECTURE UPDATE v3.1:
 * - Uses OpenFoodFactsConstants for OFF-specific configuration (getVersionedBaseUrl(), fields)
 * - ApiConfig only for shared settings (timeouts, logging)
 * - Updated imports for new DTO location
 * - Cleaner separation of concerns
 *
 * RESPONSIBILITIES:
 * - HTTP client management with optimal configuration
 * - Language-aware API calls to OpenFoodFacts
 * - Request tracking and cancellation
 * - Performance monitoring
 * - Network state management
 */
public class NetworkManager {

    private static final String TAG = ApiConfig.NETWORK_LOG_TAG;

    // ========== SINGLETON IMPLEMENTATION ==========
    private static volatile NetworkManager instance;
    private static final Object LOCK = new Object();

    // ========== CORE COMPONENTS ==========
    private final OpenFoodFactsAPI apiService;
    private final Context context;
    private final OkHttpClient httpClient;

    // ========== REQUEST TRACKING ==========
    private volatile Call<OpenFoodFactsSearchResponse> currentSearchCall;
    private volatile Call<OpenFoodFactsAPI.ProductResponse> currentProductCall;
    private volatile Call<OpenFoodFactsSearchResponse> currentAdvancedSearchCall;

    // ========== PERFORMANCE MONITORING ==========
    private final AtomicInteger totalSearchCount = new AtomicInteger(0);
    private final AtomicInteger totalProductFetchCount = new AtomicInteger(0);
    private final AtomicInteger cancelledOperationCount = new AtomicInteger(0);
    private final AtomicInteger failedOperationCount = new AtomicInteger(0);
    private final AtomicInteger successfulOperationCount = new AtomicInteger(0);
    private volatile long lastOperationStartTime = 0;
    private volatile long totalOperationTime = 0;

    // ========== CONSTRUCTOR ==========
    private NetworkManager(Context context) {
        this.context = context.getApplicationContext();
        this.httpClient = createOptimizedHttpClient();
        this.apiService = createRetrofitService();

        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, "NetworkManager initialized with language-aware API support");
        }
    }

    public static NetworkManager getInstance(Context context) {
        if (instance == null) {
            synchronized (LOCK) {
                if (instance == null) {
                    instance = new NetworkManager(context);
                }
            }
        }
        return instance;
    }

    // ========== HTTP CLIENT SETUP ==========

    /**
     * Create optimized HTTP client with proper timeouts and logging
     */
    private OkHttpClient createOptimizedHttpClient() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(ApiConfig.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(ApiConfig.READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(ApiConfig.WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true);

        // Add user agent header
        builder.addInterceptor(chain -> chain.proceed(
                chain.request().newBuilder()
                        .header("User-Agent", OpenFoodFactsConstants.USER_AGENT)
                        .build()
        ));

        // Add logging in debug mode
        if (ApiConfig.DEBUG_LOGGING) {
            HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
            loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BASIC);
            builder.addInterceptor(loggingInterceptor);
        }

        return builder.build();
    }

    /**
     * Create Retrofit service with OpenFoodFacts base URL
     */
    private OpenFoodFactsAPI createRetrofitService() {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(OpenFoodFactsConstants.getVersionedBaseUrl())
                .client(httpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        return retrofit.create(OpenFoodFactsAPI.class);
    }

    // ========== LANGUAGE-AWARE SEARCH OPERATIONS ==========

    /**
     * Search for products with explicit language specification
     */
    public void searchProducts(String query, String language, NetworkCallback<OpenFoodFactsSearchResponse> callback) {
        // Input validation
        if (query == null || query.trim().isEmpty()) {
            if (callback != null) {
                callback.onFailure("Search query cannot be empty");
            }
            return;
        }

        if (language == null || language.trim().isEmpty()) {
            if (callback != null) {
                callback.onFailure("Language parameter is required");
            }
            return;
        }

        if (callback == null) {
            Log.w(TAG, "NetworkCallback is null - search results will be ignored");
            return;
        }

        // Network connectivity check
        if (!isNetworkAvailable()) {
            callback.onFailure("No internet connection available. Please check your network settings.");
            return;
        }

        // Cancel any ongoing search to prevent race conditions
        cancelCurrentSearch();

        // Update statistics and timing
        int searchNumber = totalSearchCount.incrementAndGet();
        lastOperationStartTime = System.currentTimeMillis();

        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, String.format("Starting language-aware search #%d: '%s' in language '%s'",
                    searchNumber, query.trim(), language));
        }

        // Create API call using OpenFoodFactsConstants for fields
        currentSearchCall = apiService.searchProducts(
                query.trim(),
                ApiConfig.API_PAGE_SIZE,
                OpenFoodFactsAPI.Defaults.FIRST_PAGE, // First page
                OpenFoodFactsConstants.SEARCH_FIELDS,  // ← Uses OpenFoodFactsConstants now
                language,
                OpenFoodFactsConstants.DEFAULT_SORT_BY
        );

        // Execute with comprehensive callback handling
        currentSearchCall.enqueue(new Callback<OpenFoodFactsSearchResponse>() {
            @Override
            public void onResponse(Call<OpenFoodFactsSearchResponse> call, Response<OpenFoodFactsSearchResponse> response) {
                handleSearchResponse(call, response, callback, searchNumber, language);
            }

            @Override
            public void onFailure(Call<OpenFoodFactsSearchResponse> call, Throwable t) {
                handleSearchFailure(call, t, callback, searchNumber);
            }
        });
    }

    /**
     * Convenience method that auto-detects current language
     */
    public void searchProducts(String query, NetworkCallback<OpenFoodFactsSearchResponse> callback) {
        String currentLanguage = LanguageManager.getCurrentLanguage(context).getCode();

        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, "Auto-detected language for search: " + currentLanguage);
        }

        searchProducts(query, currentLanguage, callback);
    }

    /**
     * Paginated search with language support
     */
    public void searchProductsPaginated(String query, int page, int pageSize,
                                        String language, NetworkCallback<OpenFoodFactsSearchResponse> callback) {
        // Input validation
        if (query == null || query.trim().isEmpty()) {
            if (callback != null) {
                callback.onFailure("Search query cannot be empty");
            }
            return;
        }

        if (language == null || language.trim().isEmpty()) {
            if (callback != null) {
                callback.onFailure("Language parameter is required for pagination");
            }
            return;
        }

        if (callback == null) {
            Log.w(TAG, "NetworkCallback is null for paginated search");
            return;
        }

        if (page < 1) {
            callback.onFailure("Page number must be 1 or greater");
            return;
        }

        // Network connectivity check
        if (!isNetworkAvailable()) {
            callback.onFailure("No internet connection available");
            return;
        }

        // Cancel previous paginated search
        cancelCurrentAdvancedSearch();

        // Clamp page size to API limits
        int clampedPageSize = Math.max(1, Math.min(pageSize, OpenFoodFactsAPI.Defaults.MAX_PAGE_SIZE));

        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, String.format("Paginated search: '%s' page %d, size %d, language '%s'",
                    query.trim(), page, clampedPageSize, language));
        }

        // Create API call using OpenFoodFactsConstants for fields
        currentAdvancedSearchCall = apiService.searchProducts(
                query.trim(),
                clampedPageSize,
                page,
                OpenFoodFactsConstants.DETAIL_FIELDS,  // ← Uses OpenFoodFactsConstants now
                language,
                OpenFoodFactsConstants.DEFAULT_SORT_BY
        );

        // Execute with standard callback pattern
        currentAdvancedSearchCall.enqueue(new Callback<OpenFoodFactsSearchResponse>() {
            @Override
            public void onResponse(Call<OpenFoodFactsSearchResponse> call, Response<OpenFoodFactsSearchResponse> response) {
                handlePaginatedSearchResponse(call, response, callback, page, language);
            }

            @Override
            public void onFailure(Call<OpenFoodFactsSearchResponse> call, Throwable t) {
                handlePaginatedSearchFailure(call, t, callback, page);
            }
        });
    }

    /**
     * Convenience paginated search with auto-detected language
     */
    public void searchProductsPaginated(String query, int page, int pageSize,
                                        NetworkCallback<OpenFoodFactsSearchResponse> callback) {
        String currentLanguage = LanguageManager.getCurrentLanguage(context).getCode();
        searchProductsPaginated(query, page, pageSize, currentLanguage, callback);
    }

    // ========== LANGUAGE-AWARE PRODUCT OPERATIONS ==========

    /**
     * Get product with explicit language specification
     * Accepts both raw barcodes and source-prefixed identifiers
     */
    public void getProduct(String productIdentifier, String language, NetworkCallback<FoodProduct> callback) {
        // Input validation
        if (productIdentifier == null || productIdentifier.trim().isEmpty()) {
            if (callback != null) {
                callback.onFailure("Product identifier cannot be empty");
            }
            return;
        }

        if (language == null || language.trim().isEmpty()) {
            if (callback != null) {
                callback.onFailure("Language parameter is required");
            }
            return;
        }

        if (callback == null) {
            Log.w(TAG, "NetworkCallback is null for product fetch");
            return;
        }

        // Extract barcode from combined identifier if needed
        String cleanBarcode = productIdentifier.trim();
        if (cleanBarcode.contains(":")) {
            String[] parts = cleanBarcode.split(":", 2);
            if (parts.length == 2) {
                cleanBarcode = parts[1].trim();
                if (ApiConfig.DEBUG_LOGGING) {
                    Log.d(TAG, "Extracted barcode from identifier: " +
                            parts[0] + " → " + cleanBarcode);
                }
            }
        }

        // Validate barcode format
        if (cleanBarcode.length() < ApiConfig.MIN_BARCODE_LENGTH ||
                cleanBarcode.length() > ApiConfig.MAX_BARCODE_LENGTH) {
            callback.onFailure("Invalid barcode format");
            return;
        }

        // Network connectivity check
        if (!isNetworkAvailable()) {
            callback.onFailure("No internet connection available");
            return;
        }

        // Cancel previous product fetch
        cancelCurrentProductFetch();

        // Update statistics
        int fetchNumber = totalProductFetchCount.incrementAndGet();
        lastOperationStartTime = System.currentTimeMillis();

        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, String.format("Fetching product #%d: %s (language: %s)",
                    fetchNumber, cleanBarcode, language));
        }

        // Make API call
        currentProductCall = apiService.getProduct(cleanBarcode, language);

        currentProductCall.enqueue(new Callback<OpenFoodFactsAPI.ProductResponse>() {
            @Override
            public void onResponse(Call<OpenFoodFactsAPI.ProductResponse> call,
                                   Response<OpenFoodFactsAPI.ProductResponse> response) {
                handleProductResponse(call, response, callback, fetchNumber, language);
            }

            @Override
            public void onFailure(Call<OpenFoodFactsAPI.ProductResponse> call, Throwable t) {
                handleProductFailure(call, t, callback, fetchNumber);
            }
        });
    }

    /**
     * Convenience method for product fetch with auto-detected language
     */
    public void getProduct(String productIdentifier, NetworkCallback<FoodProduct> callback) {
        String currentLanguage = LanguageManager.getCurrentLanguage(context).getCode();
        getProduct(productIdentifier, currentLanguage, callback);
    }

    // ========== RESPONSE HANDLERS ==========

    private void handleSearchResponse(Call<OpenFoodFactsSearchResponse> call, Response<OpenFoodFactsSearchResponse> response,
                                      NetworkCallback<OpenFoodFactsSearchResponse> callback, int searchNumber, String language) {
        long operationTime = System.currentTimeMillis() - lastOperationStartTime;
        totalOperationTime += operationTime;

        if (call.isCanceled()) {
            if (ApiConfig.DEBUG_LOGGING) {
                Log.d(TAG, "Ignoring response from cancelled search #" + searchNumber);
            }
            return;
        }

        if (response.isSuccessful() && response.body() != null) {
            OpenFoodFactsSearchResponse searchResponse = response.body();
            successfulOperationCount.incrementAndGet();

            if (ApiConfig.DEBUG_LOGGING) {
                Log.d(TAG, String.format("Search #%d successful: %d results in %dms (language: %s)",
                        searchNumber, searchResponse.getCount(), operationTime, language));
            }

            callback.onSuccess(searchResponse);
        } else {
            failedOperationCount.incrementAndGet();
            String errorMessage = "Search failed: " + response.code();
            Log.e(TAG, errorMessage + " (language: " + language + ")");
            callback.onFailure(errorMessage);
        }

        if (currentSearchCall == call) {
            currentSearchCall = null;
        }
    }

    private void handleSearchFailure(Call<OpenFoodFactsSearchResponse> call, Throwable t,
                                     NetworkCallback<OpenFoodFactsSearchResponse> callback, int searchNumber) {
        if (call.isCanceled()) {
            return;
        }

        failedOperationCount.incrementAndGet();
        String errorMessage = "Network error: " + (t.getMessage() != null ? t.getMessage() : "Unknown error");
        Log.e(TAG, "Search #" + searchNumber + " network error", t);
        callback.onFailure(errorMessage);

        if (currentSearchCall == call) {
            currentSearchCall = null;
        }
    }

    private void handlePaginatedSearchResponse(Call<OpenFoodFactsSearchResponse> call, Response<OpenFoodFactsSearchResponse> response,
                                               NetworkCallback<OpenFoodFactsSearchResponse> callback, int page, String language) {
        if (call.isCanceled()) {
            if (ApiConfig.DEBUG_LOGGING) {
                Log.d(TAG, "Ignoring response from cancelled paginated search");
            }
            return;
        }

        if (response.isSuccessful() && response.body() != null) {
            OpenFoodFactsSearchResponse searchResponse = response.body();
            successfulOperationCount.incrementAndGet();

            if (ApiConfig.DEBUG_LOGGING) {
                Log.d(TAG, String.format("Paginated search successful: page %d/%d, %d results (language: %s)",
                        page, searchResponse.getPageCount(), searchResponse.getProducts().size(), language));
            }

            callback.onSuccess(searchResponse);
        } else {
            failedOperationCount.incrementAndGet();
            String errorMessage = "Paginated search failed: " + response.code();
            Log.e(TAG, errorMessage + " (language: " + language + ")");
            callback.onFailure(errorMessage);
        }

        if (currentAdvancedSearchCall == call) {
            currentAdvancedSearchCall = null;
        }
    }

    private void handlePaginatedSearchFailure(Call<OpenFoodFactsSearchResponse> call, Throwable t,
                                              NetworkCallback<OpenFoodFactsSearchResponse> callback, int page) {
        if (call.isCanceled()) {
            return;
        }

        failedOperationCount.incrementAndGet();
        String errorMessage = "Network error: " + (t.getMessage() != null ? t.getMessage() : "Unknown error");
        Log.e(TAG, "Paginated search page " + page + " network error", t);
        callback.onFailure(errorMessage);

        if (currentAdvancedSearchCall == call) {
            currentAdvancedSearchCall = null;
        }
    }

    private void handleProductResponse(Call<OpenFoodFactsAPI.ProductResponse> call,
                                       Response<OpenFoodFactsAPI.ProductResponse> response,
                                       NetworkCallback<FoodProduct> callback, int fetchNumber, String language) {
        long operationTime = System.currentTimeMillis() - lastOperationStartTime;
        totalOperationTime += operationTime;

        if (call.isCanceled()) {
            if (ApiConfig.DEBUG_LOGGING) {
                Log.d(TAG, "Ignoring response from cancelled product fetch #" + fetchNumber);
            }
            return;
        }

        if (response.isSuccessful() && response.body() != null) {
            OpenFoodFactsAPI.ProductResponse productResponse = response.body();

            if (productResponse.getStatus() == 1 && productResponse.getProduct() != null) {
                successfulOperationCount.incrementAndGet();

                // Map DTO to domain model using OpenFoodFactsMapper
                OpenFoodFactsMapper mapper = new OpenFoodFactsMapper();
                OpenFoodFactsProduct offProduct = productResponse.getProduct();  // ← Now returns OpenFoodFactsProduct
                FoodProduct product = mapper.mapToDomainModel(offProduct, language);  // ← Map it

                if (ApiConfig.DEBUG_LOGGING) {
                    Log.d(TAG, String.format("Product #%d fetched successfully in %dms (language: %s)",
                            fetchNumber, operationTime, language));
                }

                callback.onSuccess(product);
            } else {
                failedOperationCount.incrementAndGet();
                Log.w(TAG, "Product not found or incomplete response (fetch #" + fetchNumber + ")");
                callback.onFailure("Product not found");
            }
        } else {
            failedOperationCount.incrementAndGet();
            String errorMessage = "Product fetch failed: " + response.code();
            Log.e(TAG, errorMessage + " (language: " + language + ")");
            callback.onFailure(errorMessage);
        }

        if (currentProductCall == call) {
            currentProductCall = null;
        }
    }

    private void handleProductFailure(Call<OpenFoodFactsAPI.ProductResponse> call, Throwable t,
                                      NetworkCallback<FoodProduct> callback, int fetchNumber) {
        if (call.isCanceled()) {
            return;
        }

        failedOperationCount.incrementAndGet();
        String errorMessage = "Network error: " + (t.getMessage() != null ? t.getMessage() : "Unknown error");
        Log.e(TAG, "Product fetch #" + fetchNumber + " network error", t);
        callback.onFailure(errorMessage);

        if (currentProductCall == call) {
            currentProductCall = null;
        }
    }

    // ========== REQUEST MANAGEMENT ==========

    public void cancelCurrentSearch() {
        if (currentSearchCall != null && !currentSearchCall.isCanceled()) {
            currentSearchCall.cancel();
            cancelledOperationCount.incrementAndGet();
            currentSearchCall = null;

            if (ApiConfig.DEBUG_LOGGING) {
                Log.d(TAG, "Cancelled current search");
            }
        }
    }

    public void cancelCurrentProductFetch() {
        if (currentProductCall != null && !currentProductCall.isCanceled()) {
            currentProductCall.cancel();
            cancelledOperationCount.incrementAndGet();
            currentProductCall = null;

            if (ApiConfig.DEBUG_LOGGING) {
                Log.d(TAG, "Cancelled current product fetch");
            }
        }
    }

    public void cancelCurrentAdvancedSearch() {
        if (currentAdvancedSearchCall != null && !currentAdvancedSearchCall.isCanceled()) {
            currentAdvancedSearchCall.cancel();
            cancelledOperationCount.incrementAndGet();
            currentAdvancedSearchCall = null;

            if (ApiConfig.DEBUG_LOGGING) {
                Log.d(TAG, "Cancelled current advanced search");
            }
        }
    }

    public void cancelAllOperations() {
        cancelCurrentSearch();
        cancelCurrentProductFetch();
        cancelCurrentAdvancedSearch();

        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, "Cancelled all operations");
        }
    }

    // ========== NETWORK STATE ==========

    /**
     * Check network availability with modern API support
     * Updated to work with Android 10+ and emulators
     */
    /**
     * Check network availability.
     *
     * minSdk = 26 (Android 8.0 Oreo), so NetworkCapabilities is always available.
     * The legacy NetworkInfo path (deprecated in API 29) has been removed — it was
     * dead code that could only run on API < 23, which we never target.
     */
    public boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            Log.w(TAG, "ConnectivityManager is null");
            return false;
        }

        android.net.Network network = cm.getActiveNetwork();
        if (network == null) {
            if (ApiConfig.DEBUG_LOGGING) Log.d(TAG, "No active network");
            return false;
        }

        android.net.NetworkCapabilities capabilities = cm.getNetworkCapabilities(network);
        if (capabilities == null) {
            if (ApiConfig.DEBUG_LOGGING) Log.d(TAG, "No network capabilities");
            return false;
        }

        boolean hasInternet =
                capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        && capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED);

        if (ApiConfig.DEBUG_LOGGING) Log.d(TAG, "Network available: " + hasInternet);
        return hasInternet;
    }

    // ========== PERFORMANCE STATISTICS ==========

    public String getPerformanceStats() {
        int totalOperations = successfulOperationCount.get() + failedOperationCount.get();
        long avgOperationTime = totalOperations > 0 ? totalOperationTime / totalOperations : 0;

        return String.format(
                "NetworkManager Stats:\n" +
                        "Total Searches: %d\n" +
                        "Total Product Fetches: %d\n" +
                        "Successful: %d\n" +
                        "Failed: %d\n" +
                        "Cancelled: %d\n" +
                        "Avg Response Time: %dms",
                totalSearchCount.get(),
                totalProductFetchCount.get(),
                successfulOperationCount.get(),
                failedOperationCount.get(),
                cancelledOperationCount.get(),
                avgOperationTime
        );
    }

    // ========== CALLBACK INTERFACE ==========

    public interface NetworkCallback<T> {
        void onSuccess(T result);
        void onFailure(String error);
    }
}