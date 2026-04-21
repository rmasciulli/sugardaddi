package li.masciul.sugardaddi.data.sources.usda;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import li.masciul.sugardaddi.BuildConfig;
import li.masciul.sugardaddi.core.models.FoodProduct;
import li.masciul.sugardaddi.core.models.Error;
import li.masciul.sugardaddi.core.logging.ErrorLogger;
import li.masciul.sugardaddi.data.database.AppDatabase;
import li.masciul.sugardaddi.data.database.dao.FoodProductDao;
import li.masciul.sugardaddi.data.database.dao.NutritionDao;
import li.masciul.sugardaddi.data.database.dao.CombinedProductDao;
import li.masciul.sugardaddi.data.database.entities.FoodProductEntity;
import li.masciul.sugardaddi.data.network.ApiConfig;
import li.masciul.sugardaddi.data.network.NetworkClient;
import li.masciul.sugardaddi.data.network.NetworkConfig;
import li.masciul.sugardaddi.data.sources.base.BaseDataSource;
import li.masciul.sugardaddi.data.sources.base.DataSourceCallback;
import li.masciul.sugardaddi.data.sources.base.DataSource.SearchResult;
import li.masciul.sugardaddi.data.sources.base.settings.SettingsProvider;
import li.masciul.sugardaddi.data.sources.usda.api.FoodDataCentralAPI;
import li.masciul.sugardaddi.data.sources.usda.api.dto.FDCFoodDetail;
import li.masciul.sugardaddi.data.sources.usda.api.dto.FDCSearchRequest;
import li.masciul.sugardaddi.data.sources.usda.api.dto.FDCSearchResponse;
import li.masciul.sugardaddi.data.sources.usda.mappers.USDAMapper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * USDADataSource — USDA FoodData Central data source.
 *
 * DUAL-MODE ARCHITECTURE
 * ======================
 * MODE 1: REST API (always active, ~200ms)
 *   Uses FoodDataCentralAPI via Retrofit. Queries Foundation + SR Legacy + Survey.
 *   API key read from SharedPreferences (user key) or BuildConfig (from local.properties).
 *
 * MODE 2: Local Room DB (opt-in, offline-capable)
 *   Populated by USDAImportService (user-initiated from Settings).
 *   Not auto-triggered — ~215MB download requires user consent.
 *   Once imported, search() queries Room first and falls back to API if no results.
 *
 * SEARCH FLOW
 * ===========
 * 1. If local DB imported: query Room → return if results found
 * 2. Fall through to API: GET /foods/search with API key + data type filter
 * 3. Map FDCSearchFood → FoodProduct via USDAMapper
 *
 * PRODUCT DETAIL FLOW
 * ===================
 * Always fetches from API (GET /food/{fdcId}?format=full) for complete nutrition.
 * Room stores only the subset of fields from search results, not the full profile.
 *
 * API KEY PRIORITY
 * ================
 * 1. SharedPreferences (user entered a real key in Settings)
 * 2. BuildConfig.USDA_API_KEY (from local.properties → compile-time)
 * 3. USDAConstants.DEMO_KEY (hardcoded fallback — rate-limited)
 */
public class USDADataSource extends BaseDataSource {

    private static final String TAG = "USDADataSource";

    // ===== API & CONFIGURATION =====
    private FoodDataCentralAPI api;
    private final USDAConfig config;
    private final Context context;

    /** Tracks active Retrofit calls for cancellation */
    private final Set<Call<?>> activeCalls =
            Collections.synchronizedSet(new HashSet<>());

    // ===== DATABASE =====
    private final AppDatabase   database;
    private final FoodProductDao productDao;
    private final NutritionDao   nutritionDao;
    private final CombinedProductDao combinedDao;

    // ===== BACKGROUND WORK =====
    private final ExecutorService backgroundExecutor =
            Executors.newSingleThreadExecutor();

    // ===== SETTINGS =====
    private final USDASettingsProvider settingsProvider = new USDASettingsProvider();

    // ===== CONSTRUCTOR =====

    public USDADataSource(@NonNull Context context, @NonNull USDAConfig config) {
        super();
        this.context = context.getApplicationContext();
        this.config   = config;

        database     = AppDatabase.getInstance(context);
        productDao   = database.foodProductDao();
        nutritionDao = database.nutritionDao();
        combinedDao  = database.combinedProductDao();

        Log.d(TAG, "USDADataSource created (deferred initialization)");
    }

    // ===== BASEDATASOURCE REQUIRED METHODS =====

    @NonNull
    @Override
    public NetworkConfig getNetworkConfig() {
        return config;
    }

    @NonNull
    @Override
    public String getSourceId() {
        return USDAConstants.SOURCE_ID;
    }

    @NonNull
    @Override
    public String getSourceName() {
        return USDAConstants.SOURCE_NAME;
    }

    @Nullable
    @Override
    public SettingsProvider getSettingsProvider() {
        return settingsProvider;
    }

    @Override
    public boolean supportsBarcodeLookup() {
        // FDC uses fdcId (integer), not EAN/UPC barcodes
        return false;
    }

    @NonNull
    @Override
    public Set<String> getSupportedLanguages() {
        // FDC is English-only
        Set<String> langs = new HashSet<>();
        langs.add("en");
        return langs;
    }

    @NonNull
    @Override
    public String getPrimaryLanguage() {
        return "en";
    }

    // ===== INITIALIZATION =====

    @Override
    protected void onInitialize(@NonNull Context context) throws Exception {
        logInfo("Initializing USDA FoodData Central data source...");

        config.validate();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(USDAConstants.BASE_URL)
                .client(NetworkClient.createHttpClient(config, context))
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        api = retrofit.create(FoodDataCentralAPI.class);

        logInfo("USDA FoodData Central API initialized");
    }

    @Override
    public void initialize(@NonNull Context context) {
        if (initialized) {
            Log.d(TAG, "Already initialized, skipping");
            return;
        }
        try {
            onInitialize(context);
            initialized = true;
        } catch (Exception e) {
            Log.e(TAG, "Initialization failed", e);
            ErrorLogger.log(Error.fromThrowable(e, "USDA initialization failed"));
        }
    }

    // ===== SEARCH =====

    /**
     * Search for USDA foods.
     *
     * If the local DB has been imported, Room is queried first.
     * Falls through to the REST API if Room returns no results or DB not imported.
     *
     * @param query    Search term
     * @param language User language (FDC is EN-only; ignored for field selection)
     * @param limit    Maximum results
     * @param page     Page number (1-based)
     * @param callback Result callback
     */
    @Override
    public void search(@NonNull String query, @NonNull String language,
                       int limit, int page,
                       @NonNull DataSourceCallback<SearchResult> callback) {
        if (!checkEnabled(callback)) return;

        callback.onLoading();
        onOperationStart();

        // ── Local DB path ──────────────────────────────────────────────────
        if (USDAImportService.isImported(context)) {
            backgroundExecutor.execute(() -> {
                try {
                    List<FoodProductEntity> entities =
                            productDao.searchProductsBySource(USDAConstants.SOURCE_ID, query, limit);

                    if (entities != null && !entities.isEmpty()) {
                        List<FoodProduct> products = new ArrayList<>();
                        for (FoodProductEntity e : entities) {
                            FoodProduct p = e.toFoodProduct();
                            li.masciul.sugardaddi.data.database.entities.NutritionEntity ne =
                                    nutritionDao.getNutritionBySource("product", e.getId());
                            if (ne != null) p.setNutrition(ne.toNutrition());
                            products.add(p);
                        }

                        SearchResult result = new SearchResult(
                                products, products.size(), products.size() >= limit,
                                query, language, USDAConstants.SOURCE_ID);

                        onOperationSuccess();
                        logDebug("Local search: " + products.size() + " results for '" + query + "'");
                        executeOnMainThread(() -> callback.onSuccess(result));
                        return;
                    }
                } catch (Exception e) {
                    logError("Local USDA search failed, falling back to API", e);
                }

                // No local results — fall through to API
                executeOnMainThread(() -> searchApi(query, language, limit, page, callback));
            });
            return;
        }

        // ── API path ───────────────────────────────────────────────────────
        if (api == null) {
            handleError(Error.unknown("USDA API not initialized", null), callback);
            return;
        }

        searchApi(query, language, limit, page, callback);
    }

    /**
     * Execute a REST API search against FoodData Central.
     * Uses API_DATA_TYPES (Foundation + SR Legacy + Survey) to exclude Branded.
     */
    private void searchApi(@NonNull String query, @NonNull String language,
                           int limit, int page,
                           @NonNull DataSourceCallback<SearchResult> callback) {
        String apiKey = getActiveApiKey();

        FDCSearchRequest request = new FDCSearchRequest(
                query,
                java.util.Arrays.asList("Foundation", "SR Legacy", "Survey (FNDDS)"),
                Math.min(limit, USDAConstants.MAX_PAGE_SIZE),
                page,
                USDAConstants.DEFAULT_SORT_BY,
                USDAConstants.DEFAULT_SORT_ORDER);

        Call<FDCSearchResponse> call = api.searchFoods(apiKey, request);

        activeCalls.add(call);

        call.enqueue(new Callback<FDCSearchResponse>() {
            @Override
            public void onResponse(@NonNull Call<FDCSearchResponse> call,
                                   @NonNull Response<FDCSearchResponse> response) {
                activeCalls.remove(call);

                if (!response.isSuccessful()) {
                    handleHttpError(response.code(), "Search failed", callback);
                    return;
                }

                FDCSearchResponse body = response.body();
                if (body == null || !body.hasResults()) {
                    SearchResult empty = new SearchResult(
                            new ArrayList<>(), 0, false,
                            query, language, USDAConstants.SOURCE_ID);
                    onOperationSuccess();
                    executeOnMainThread(() -> callback.onSuccess(empty));
                    return;
                }

                List<FoodProduct> products = USDAMapper.mapSearchResponse(body, language);

                if (ApiConfig.DEBUG_LOGGING) {
                    Log.d(TAG, "API search: " + products.size()
                            + "/" + body.getTotalHits() + " for '" + query + "'");
                }

                SearchResult result = new SearchResult(
                        products,
                        body.getTotalHits(),
                        body.hasMorePages(),
                        query,
                        language,
                        USDAConstants.SOURCE_ID
                );

                onOperationSuccess();
                executeOnMainThread(() -> callback.onSuccess(result));
            }

            @Override
            public void onFailure(@NonNull Call<FDCSearchResponse> call,
                                  @NonNull Throwable t) {
                activeCalls.remove(call);
                if (call.isCanceled()) return;
                Log.e(TAG, "Search API call failed", t);
                handleError(createNetworkError(t, "USDA search failed"), callback);
            }
        });
    }

    // ===== PRODUCT DETAIL =====

    /**
     * Fetch full product detail from FDC by fdcId.
     *
     * productId format: either "747447" (raw fdcId) or "USDA:747447"
     * (the SearchableId format used by the rest of the app).
     *
     * Always fetches from API — Room search results don't include the full
     * nutrient profile that this endpoint provides.
     */
    @Override
    public void getProduct(@NonNull String productId, @NonNull String language,
                           @NonNull DataSourceCallback<FoodProduct> callback) {
        if (!checkEnabled(callback)) return;

        if (api == null) {
            handleError(Error.unknown("USDA API not initialized", null), callback);
            return;
        }

        // Extract raw fdcId integer — strip "USDA:" prefix if present
        String rawId = productId.startsWith("USDA:")
                ? productId.substring(5) : productId;

        int fdcId;
        try {
            fdcId = Integer.parseInt(rawId.trim());
        } catch (NumberFormatException e) {
            handleError(Error.validation("Invalid USDA fdcId: " + productId, null), callback);
            return;
        }

        onOperationStart();
        callback.onLoading();

        String apiKey = getActiveApiKey();

        Call<FDCFoodDetail> call = api.getFood(fdcId, apiKey, "full");
        activeCalls.add(call);

        call.enqueue(new Callback<FDCFoodDetail>() {
            @Override
            public void onResponse(@NonNull Call<FDCFoodDetail> call,
                                   @NonNull Response<FDCFoodDetail> response) {
                activeCalls.remove(call);

                if (!response.isSuccessful()) {
                    handleHttpError(response.code(), "Product fetch failed", callback);
                    return;
                }

                FDCFoodDetail detail = response.body();
                if (detail == null || !detail.isValid()) {
                    handleError(Error.notFound("USDA food not found: fdcId=" + fdcId), callback);
                    return;
                }

                FoodProduct product = USDAMapper.mapFoodDetail(detail, language);
                if (product == null) {
                    handleError(Error.noData("Failed to parse USDA food detail"), callback);
                    return;
                }

                if (ApiConfig.DEBUG_LOGGING) {
                    Log.d(TAG, "Product fetched: " + product.getName()
                            + " (" + detail.getFoodNutrients().size() + " nutrients)");
                }

                onOperationSuccess();
                executeOnMainThread(() -> callback.onSuccess(product));
            }

            @Override
            public void onFailure(@NonNull Call<FDCFoodDetail> call,
                                  @NonNull Throwable t) {
                activeCalls.remove(call);
                if (call.isCanceled()) return;
                Log.e(TAG, "Product fetch failed", t);
                handleError(createNetworkError(t, "USDA product fetch failed"), callback);
            }
        });
    }

    @Override
    public void getProductByBarcode(@NonNull String barcode, @NonNull String language,
                                    @NonNull DataSourceCallback<FoodProduct> callback) {
        // USDA does not use barcodes — route to getProduct() by fdcId if caller passes one,
        // otherwise return an informative error.
        handleError(Error.validation(
                "USDA FoodData Central does not support barcode lookup. " +
                        "Use getProduct() with an fdcId.", null), callback);
    }

    // ===== AUTOCOMPLETE =====

    /**
     * Autocomplete search — uses the regular API with a small limit.
     * Same pattern as OpenFoodFactsDataSource and CiqualDataSource.
     *
     * @param query    Partial user input
     * @param language User language (ignored — FDC is EN-only)
     * @param limit    Max suggestions (typically 5–10)
     * @param callback Result callback
     */
    public void autocomplete(@NonNull String query, @NonNull String language,
                             int limit,
                             @NonNull DataSourceCallback<SearchResult> callback) {
        if (!checkEnabled(callback)) return;

        if (api == null) {
            handleError(Error.unknown("USDA API not initialized", null), callback);
            return;
        }

        String apiKey = getActiveApiKey();
        onOperationStart();

        FDCSearchRequest request = new FDCSearchRequest(
                query,
                java.util.Arrays.asList("Foundation", "SR Legacy", "Survey (FNDDS)"),
                Math.min(limit, USDAConstants.MAX_PAGE_SIZE),
                1, // Page number always set to 1 for autocomplete
                USDAConstants.DEFAULT_SORT_BY,
                USDAConstants.DEFAULT_SORT_ORDER);

        Call<FDCSearchResponse> call = api.searchFoods(apiKey, request);

        activeCalls.add(call);

        call.enqueue(new Callback<FDCSearchResponse>() {
            @Override
            public void onResponse(@NonNull Call<FDCSearchResponse> call,
                                   @NonNull Response<FDCSearchResponse> response) {
                activeCalls.remove(call);

                if (!response.isSuccessful()) {
                    // Silent failure for autocomplete
                    executeOnMainThread(() -> callback.onSuccess(
                            new SearchResult(new ArrayList<>(), 0, false,
                                    query, language, USDAConstants.SOURCE_ID)));
                    return;
                }

                FDCSearchResponse body = response.body();
                List<FoodProduct> products = (body != null && body.hasResults())
                        ? USDAMapper.mapSearchResponse(body, language)
                        : new ArrayList<>();

                if (ApiConfig.DEBUG_LOGGING) {
                    Log.d(TAG, "Autocomplete: " + products.size() + " for '" + query + "'");
                }

                SearchResult result = new SearchResult(
                        products, products.size(), false,
                        query, language, USDAConstants.SOURCE_ID);

                onOperationSuccess();
                executeOnMainThread(() -> callback.onSuccess(result));
            }

            @Override
            public void onFailure(@NonNull Call<FDCSearchResponse> call,
                                  @NonNull Throwable t) {
                activeCalls.remove(call);
                if (call.isCanceled()) return;
                // Silent failure for autocomplete — return empty list
                executeOnMainThread(() -> callback.onSuccess(
                        new SearchResult(new ArrayList<>(), 0, false,
                                query, language, USDAConstants.SOURCE_ID)));
            }
        });
    }

    // ===== CANCELLATION =====

    @Override
    public void cancelOperations() {
        synchronized (activeCalls) {
            for (Call<?> call : activeCalls) {
                if (call != null && !call.isCanceled()) call.cancel();
            }
            activeCalls.clear();
        }
        logDebug("All USDA operations cancelled");
    }

    @Override
    public void cleanup() {
        super.cleanup();
        cancelOperations();
        backgroundExecutor.shutdown();
        logDebug("USDADataSource cleanup complete");
    }

    // ===== HELPERS =====

    /**
     * Resolve the active API key from SharedPreferences → BuildConfig → DEMO_KEY.
     * Called on every request so key changes take effect immediately.
     */
    @NonNull
    private String getActiveApiKey() {
        String stored = context.getSharedPreferences(
                        USDAConstants.PREFS_NAME, Context.MODE_PRIVATE)
                .getString(USDAConstants.PREF_API_KEY, null);
        if (stored != null && !stored.trim().isEmpty()) return stored.trim();

        // BuildConfig value comes from local.properties at compile time
        if (BuildConfig.USDA_API_KEY != null
                && !BuildConfig.USDA_API_KEY.equals(USDAConstants.DEMO_KEY)) {
            return BuildConfig.USDA_API_KEY;
        }

        return USDAConstants.DEMO_KEY;
    }

    private <T> void handleHttpError(int code, String message,
                                     @NonNull DataSourceCallback<T> callback) {
        handleError(Error.fromHttpCode(code, message, USDAConstants.SOURCE_ID), callback);
    }

    private Error createNetworkError(Throwable t, String message) {
        if (t instanceof java.io.IOException) {
            return Error.network(message, t.getMessage(), USDAConstants.SOURCE_ID);
        }
        return Error.fromThrowable(t, message);
    }
}