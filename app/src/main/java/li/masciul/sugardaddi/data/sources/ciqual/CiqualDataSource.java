package li.masciul.sugardaddi.data.sources.ciqual;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import li.masciul.sugardaddi.core.logging.LogLevel;
import li.masciul.sugardaddi.core.models.Error;
import li.masciul.sugardaddi.core.models.FoodProduct;
import li.masciul.sugardaddi.core.logging.ErrorLogger;
import li.masciul.sugardaddi.data.database.AppDatabase;
import li.masciul.sugardaddi.data.database.dao.CombinedProductDao;
import li.masciul.sugardaddi.data.database.dao.FoodProductDao;
import li.masciul.sugardaddi.data.database.entities.FoodProductEntity;
import li.masciul.sugardaddi.data.database.dao.NutritionDao;
import li.masciul.sugardaddi.data.network.NetworkClient;
import li.masciul.sugardaddi.data.network.NetworkConfig;
import li.masciul.sugardaddi.data.sources.base.BaseDataSource;
import li.masciul.sugardaddi.data.sources.base.DataSourceCallback;
import li.masciul.sugardaddi.data.sources.base.DataSource.SearchResult;
import li.masciul.sugardaddi.data.sources.ciqual.api.CiqualAPI;
import li.masciul.sugardaddi.data.sources.ciqual.api.CiqualSearchRequest;
import li.masciul.sugardaddi.data.sources.ciqual.api.dto.CiqualElasticsearchResponse;
import li.masciul.sugardaddi.data.sources.ciqual.mappers.CiqualElasticsearchMapper;
import li.masciul.sugardaddi.data.sources.ciqual.xml.CiqualCategoryLookup;
import li.masciul.sugardaddi.data.sources.ciqual.CiqualImportService;
import li.masciul.sugardaddi.data.sources.ciqual.xml.CiqualXmlParser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * CiqualDataSource - French food composition database by ANSES
 *
 * VERSION 2.1 - ASYNC INITIALIZATION WITH DUAL-MODE READINESS
 *
 * TRI-MODAL OPERATIONAL STRATEGY:
 * ================================
 * This data source supports THREE operational modes with automatic fallback:
 *
 * MODE 1: ELASTICSEARCH API (Phase 1) [ACTIVE] ACTIVE
 * - Real-time search via Elasticsearch endpoint
 * - Fast (1-10ms response time)
 * - Relevance-scored results
 * - No authentication required
 * - Status: [DONE] IMPLEMENTED
 * - Availability: Ready once network is up (~500ms)
 *
 * MODE 2: XML DOWNLOAD & PARSE (Phase 2) [BUNDLED] PLANNED
 * - Download full Ciqual database as ZIP
 * - Parse 3000+ XML files with CiqualXmlParser
 * - One-time setup, periodic updates
 * - Populates local database
 * - Status: [WIP] Initialization framework ready, implementation TODO
 * - Availability: Ready after XML parsing completes (~30-60s)
 *
 * MODE 3: LOCAL DATABASE (Phase 3) [LOCAL DB] PLANNED
 * - Query local Room database
 * - Fast, offline-capable
 * - Pre-populated from XML or API
 * - Status: [WIP] TODO
 *
 * ✨ NEW IN v2.1: PROGRESSIVE AVAILABILITY
 * ========================================
 * Sources can now signal readiness in stages:
 *
 * 1. API READY (~500ms):
 *    - Network client initialized
 *    - Can perform searches and lookups
 *    - Requires internet connection
 *    → onInitialized() callback fires
 *    → isAvailable() returns true
 *
 * 2. XML READY (~30-60s, background):
 *    - XML parser initialized
 *    - Full database available locally
 *    - Can perform enriched lookups
 *    → onXmlParserReady() callback fires (optional listener)
 *    → xmlParserAvailable() returns true
 *
 * This allows the app to start using the API immediately while XML
 * parsing happens in the background without blocking.
 *
 * USAGE:
 * ```java
 * CiqualConfig config = new CiqualConfig();
 * CiqualDataSource source = new CiqualDataSource(context, config);
 *
 * // Async initialization with progressive callbacks
 * source.initialize(context, new InitializationCallback() {
 *     @Override
 *     public void onInitialized() {
 *         // API is ready! Can start searching immediately
 *         source.search("fraise", "fr", 10, searchCallback);
 *     }
 *
 *     @Override
 *     public void onInitializationFailed(Error error) {
 *         // Handle initialization failure
 *     }
 * });
 *
 * // Optional: Listen for XML parser readiness
 * source.setXmlParserListener(new XmlParserListener() {
 *     @Override
 *     public void onXmlParserReady() {
 *         // XML parser ready - can do enriched lookups
 *     }
 * });
 * ```
 *
 * @see BaseDataSource
 * @see CiqualConfig
 * @see CiqualAPI
 * @author SugarDaddi Team
 * @version 2.1 (Async Dual-Mode Initialization)
 */
public class CiqualDataSource extends BaseDataSource {

    private static final String TAG = "CiqualDataSource";
    private static final String PREFS_NAME = "ciqual_prefs";
    private static final String KEY_LAST_UPDATE = "last_update";
    private static final String KEY_DB_READY = "database_ready";

    // ========== PHASE 1: ELASTICSEARCH API (ACTIVE) ==========

    /**
     * Elasticsearch API instance
     * Initialized first, available within ~500ms
     */
    private CiqualAPI elasticsearchApi;

    /**
     * Network configuration
     */
    private final CiqualConfig config;

    /**
     * Active API calls for cancellation tracking
     */
    private Set<Call<?>> activeCalls;

    /**
     * API ready flag - tracks if network API is initialized
     */
    private final AtomicBoolean apiReady = new AtomicBoolean(false);

    // ========== PHASE 2: XML DOWNLOAD (PLANNED) ==========

    /**
     * XML parser for Ciqual database files
     * Initialized in background after API is ready
     */
    private CiqualXmlParser xmlParser;

    /**
     * Background executor for XML parsing
     */
    private final ExecutorService backgroundExecutor;

    /**
     * XML parser ready flag - tracks if parser is initialized
     */
    private final AtomicBoolean xmlParserReady = new AtomicBoolean(false);

    /**
     * XML parser initialization in progress
     */
    private final AtomicBoolean xmlParserInitializing = new AtomicBoolean(false);

    // ========== PHASE 3: LOCAL DATABASE (PLANNED) ==========

    /**
     * Room database instance
     */
    private final AppDatabase database;

    /**
     * DAOs for database access
     */
    private final FoodProductDao productDao;
    private final NutritionDao nutritionDao;
    private final CombinedProductDao combinedDao;

    /**
     * Database ready flag
     */
    private boolean databaseReady = false;

    /**
     * Shared preferences for state persistence
     */
    private final SharedPreferences prefs;

    /**
     * Application context
     */
    private final Context context;

    // ========== LISTENERS ==========

    /**
     * Optional listener for XML parser readiness
     */
    public interface XmlParserListener {
        void onXmlParserReady();
        void onXmlParserFailed(Error error);
    }

    private XmlParserListener xmlParserListener;

    // ========== CONSTRUCTOR ==========

    /**
     * Creates Ciqual data source with tri-modal architecture
     *
     * NOTE: Constructor is lightweight - no heavy initialization here!
     * Heavy work (API client creation, XML parsing) happens in onInitialize()
     *
     * @param context Application context
     * @param config Ciqual network configuration
     */
    public CiqualDataSource(@NonNull Context context, @NonNull CiqualConfig config) {
        super();  // BaseDataSource no-arg constructor

        this.context = context.getApplicationContext();
        this.config = config;

        // Light initialization only
        this.backgroundExecutor = Executors.newSingleThreadExecutor();

        // Phase 3: Database components (ready for future use)
        this.database = AppDatabase.getInstance(context);
        this.productDao = database.foodProductDao();
        this.nutritionDao = database.nutritionDao();
        this.combinedDao = database.combinedProductDao();
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.databaseReady = prefs.getBoolean(KEY_DB_READY, false);

        Log.d(TAG, "CiqualDataSource created (deferred initialization)");
    }

    // ========== LIFECYCLE - IMPLEMENTS DataSource INTERFACE ==========

    /**
     * Synchronous initialize method required by DataSource interface
     *
     * This is the method that external callers use to initialize the data source.
     * It delegates to the async version from BaseDataSource internally.
     *
     * For async initialization with callbacks, use initialize(Context, InitializationCallback)
     * from BaseDataSource.
     *
     * @param context Application context
     */
    @Override
    public void initialize(@NonNull Context context) {
        if (initialized) {
            Log.d(TAG, "Already initialized, skipping");
            return;
        }

        try {
            Log.d(TAG, "Initializing CiqualDataSource synchronously...");

            // Validate config
            config.validate();

            // STAGE 1: Initialize API (fast)
            initializeApi();

            // STAGE 2: Initialize XML parser (slow, background)
            // Start in background but don't wait for it
            initializeXmlParserAsync();

            // STAGE 2B: Category lookup from bundled asset (fast, ~50ms, background)
            initializeCategoryLookupAsync();

            // DB import is triggered from MainActivity.onActivityResumed()
            // to guarantee the app is in the foreground when startForegroundService() is called.

            // Mark as initialized
            initialized = true;
            Log.i(TAG, "CiqualDataSource initialization complete");

        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize CiqualDataSource", e);
            ErrorLogger.log(Error.fromThrowable(e, "Ciqual initialization failed"));
        }
    }

    // ========== ASYNC INITIALIZATION (FOR BaseDataSource v2.1) ==========

    /**
     * [DONE] NEW: Async initialization with dual-stage readiness
     *
     * STAGE 1: API Initialization (~500ms)
     * - Creates Retrofit client
     * - Sets up Elasticsearch API
     * - Calls onInitialized() when ready
     * - Source becomes available for searches
     *
     * STAGE 2: XML Parser Initialization (~30-60s, background)
     * - Parses XML files
     * - Calls onXmlParserReady() when ready (if listener set)
     * - Enables enriched lookups
     *
     * This method is called by BaseDataSource on a background thread.
     * Heavy operations are safe here.
     */
    @Override
    protected void onInitialize(@NonNull Context context) throws Exception {
        logInfo("Starting Ciqual initialization...");

        // STAGE 1: Initialize API (fast, ~500ms)
        initializeApi();

        // STAGE 2: Initialize XML parser (slow, background)
        // Start in background but don't wait for it
        initializeXmlParserAsync();

        logInfo("Ciqual API initialized successfully");
        // onInitialized() callback will fire from BaseDataSource
    }

    /**
     * Initialize Elasticsearch API (fast)
     * Called on background thread, can do network operations
     */
    private void initializeApi() throws Exception {
        try {
            // Validate config
            config.validate();

            // Use getBaseUrl() instead of getApiBaseUrl()
            // getBaseUrl() is the protected method in NetworkConfig
            Retrofit retrofit = new Retrofit.Builder()
                    .baseUrl(config.getBaseUrl())
                    .client(NetworkClient.createHttpClient(config, context))
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();

            // Create API instance
            elasticsearchApi = retrofit.create(CiqualAPI.class);

            // Initialize active calls set
            activeCalls = Collections.synchronizedSet(new HashSet<>());

            // Mark API as ready
            apiReady.set(true);

            logInfo("Ciqual Elasticsearch API initialized");

        } catch (Exception e) {
            logError("Failed to initialize Ciqual API", e);
            throw new Exception("Failed to initialize Ciqual API: " + e.getMessage(), e);
        }
    }

    /**
     * Initialize XML parser asynchronously (slow, background)
     * Does not block API initialization
     */
    private void initializeXmlParserAsync() {
        if (xmlParserInitializing.getAndSet(true)) {
            logDebug("XML parser initialization already in progress");
            return;
        }

        logInfo("Starting XML parser initialization in background...");

        backgroundExecutor.execute(() -> {
            try {
                // Create XML parser (heavy operation - parses files)
                xmlParser = new CiqualXmlParser(context);

                // Mark as ready
                xmlParserReady.set(true);
                xmlParserInitializing.set(false);

                logInfo("Ciqual XML parser initialized successfully");

                // Notify listener if set
                if (xmlParserListener != null) {
                    executeOnMainThread(() -> xmlParserListener.onXmlParserReady());
                }

            } catch (Exception e) {
                logError("Failed to initialize XML parser", e);
                xmlParserInitializing.set(false);

                // Create error
                Error error = Error.fromThrowable(e, "Failed to initialize XML parser");

                // Notify listener if set
                if (xmlParserListener != null) {
                    executeOnMainThread(() -> xmlParserListener.onXmlParserFailed(error));
                }

                // Don't throw - API is still usable without XML parser
            }
        });
    }

    /**
     * Initialize the category hierarchy lookup from the bundled asset file.
     *
     * Reads alim_grp_2025_11_03.xml from assets — a flat 80KB XML file containing
     * all 138 Ciqual food group/subgroup/sub-subgroup entries.
     *
     * This is SEPARATE from the full XML parser (Phase 2B) and much lighter:
     * - No ZIP download required
     * - Completes in <50ms on any device
     * - Enables getCategoryHierarchy() in CiqualElasticsearchMapper immediately
     *
     * Safe to call multiple times — CiqualCategoryLookup.parseFromAssets() is idempotent.
     */
    private void initializeCategoryLookupAsync() {
        backgroundExecutor.execute(() -> {
            try {
                CiqualCategoryLookup.getInstance().parseFromAssets(context);
                logDebug(CiqualCategoryLookup.getInstance().getDebugSummary());
            } catch (Exception e) {
                logError("Failed to initialize CiqualCategoryLookup (non-fatal)", e);
                // Non-fatal: ES search still works, just shows flat category names
            }
        });
    }

    /**
     * Automatically starts CiqualImportService if:
     *   - The DB has never been imported, OR
     *   - The stored version doesn't match CiqualConstants.DATASET_VERSION
     *
     * Uses startForegroundService so the OS allows it from a background thread.
     * The import runs in its own foreground service — this call returns immediately.
     * search() continues using the ES API as fallback until the import completes.
     */
    /**
     * Starts CiqualImportService if the local DB is missing or stale.
     *
     * THREADING: initializeCategoryLookupAsync() runs on backgroundExecutor.
     * startForegroundService() must be called from the main thread on Android 12+
     * to avoid ForegroundServiceStartNotAllowedException.
     * We post to mainHandler to guarantee this.
     */
    public void triggerImportIfNeeded() {
        if (!CiqualImportService.needsUpdate(context)) {
            logDebug("Ciqual DB is current (v" + CiqualImportService.getImportedVersion(context) + ") — skipping import");
            return;
        }
        logInfo("Ciqual DB needs import (stored=" + CiqualImportService.getImportedVersion(context)
                + ", current=" + CiqualConstants.DATASET_VERSION + ")");
        // Post to main thread — startForegroundService() must be called from foreground
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
            try {
                Intent intent = new Intent(context, CiqualImportService.class);
                context.startForegroundService(intent);
                logInfo("CiqualImportService started");
            } catch (Exception e) {
                logError("Failed to start CiqualImportService (non-fatal, ES API still available)", e);
            }
        });
    }

    // ========== XML PARSER LISTENER ==========

    /**
     * Set listener for XML parser readiness (optional)
     * Useful if you want to be notified when enriched lookups become available
     */
    public void setXmlParserListener(XmlParserListener listener) {
        this.xmlParserListener = listener;

        // If already ready, notify immediately
        if (xmlParserReady.get() && listener != null) {
            listener.onXmlParserReady();
        }
    }

    /**
     * Check if XML parser is available
     */
    public boolean isXmlParserAvailable() {
        return xmlParserReady.get();
    }

    // ========== REQUIRED BASEDATASOURCE METHODS ==========

    @NonNull
    @Override
    public NetworkConfig getNetworkConfig() {
        return config;
    }

    @NonNull
    @Override
    public String getSourceId() {
        return CiqualConstants.SOURCE_ID;
    }

    @NonNull
    @Override
    public String getSourceName() {
        return CiqualConstants.SOURCE_NAME;
    }

    /**
     * [DONE] UPDATED: Check if API is ready (not XML parser)
     * Source is available once API is initialized, even if XML parser isn't ready yet
     */
    @Override
    public boolean isAvailable() {
        // Check base availability (enabled, initialized, network)
        if (!super.isAvailable()) {
            return false;
        }

        // Check if API is ready
        return apiReady.get();
    }

    // ========== SEARCH ==========

    /**
     * Search Ciqual — local Room when DB imported, ES API fallback.
     *
     * LOCAL: queries food_products WHERE sourceId=CIQUAL AND searchableText LIKE query.
     *        searchableText contains both nameFr + nameEn — bilingual search.
     *        Falls through to ES if local returns no results.
     * ES API: original Elasticsearch path for when DB not yet downloaded.
     */
    @Override
    public void search(@NonNull String query, @NonNull String language, int limit,
                       int page, @NonNull DataSourceCallback<SearchResult> callback) {
        if (!checkEnabled(callback)) return;

        callback.onLoading();
        onOperationStart();

        final String effectiveLanguage = language.equals("en") ? "en" : "fr";

        // Local-first when Ciqual DB has been downloaded and imported
        if (CiqualImportService.isImported(context)) {
            backgroundExecutor.execute(() -> {
                try {
                    List<FoodProductEntity> entities =
                            productDao.searchProductsBySource(
                                    CiqualConstants.SOURCE_ID, query, limit);

                    if (entities != null && !entities.isEmpty()) {
                        List<FoodProduct> products = new ArrayList<>();
                        AppDatabase db = AppDatabase.getInstance(context);
                        for (FoodProductEntity e : entities) {
                            FoodProduct p = e.toFoodProduct();
                            // Attach nutrition so cards show carbs/kcal
                            li.masciul.sugardaddi.data.database.entities.NutritionEntity ne =
                                    db.nutritionDao().getNutritionBySource("product", e.getId());
                            if (ne != null) p.setNutrition(ne.toNutrition());
                            products.add(p);
                        }

                        SearchResult result = new SearchResult(
                                products, products.size(), products.size() >= limit,
                                query, effectiveLanguage, CiqualConstants.SOURCE_ID);

                        onOperationSuccess();
                        logDebug("Local search: " + products.size() + " results for '" + query + "'");
                        executeOnMainThread(() -> callback.onSuccess(result));
                        return;
                    }
                } catch (Exception e) {
                    logError("Local Ciqual search failed, falling back to ES", e);
                }
                // No local results — fall through to ES
                searchElasticsearch(query, effectiveLanguage, limit, callback);
            });
            return;
        }

        // ES API path — DB not yet imported
        if (!apiReady.get()) {
            Error error = Error.unknown("Ciqual API not yet initialized", null);
            handleError(error, callback);
            return;
        }
        searchElasticsearch(query, effectiveLanguage, limit, callback);
    }

    /**
     * Execute Elasticsearch search — extracted from original search() so the
     * local-first path above can delegate here cleanly.
     */
    private void searchElasticsearch(@NonNull String query, @NonNull String effectiveLanguage,
                                     int limit, @NonNull DataSourceCallback<SearchResult> callback) {
        try {
            String json = CiqualSearchRequest.buildSearchQuery(query, effectiveLanguage, 0, limit);
            RequestBody body = RequestBody.create(MediaType.parse("application/json"), json);
            Call<CiqualElasticsearchResponse> call = elasticsearchApi.search(body);
            activeCalls.add(call);

            call.enqueue(new Callback<CiqualElasticsearchResponse>() {
                @Override
                public void onResponse(@NonNull Call<CiqualElasticsearchResponse> call,
                                       @NonNull Response<CiqualElasticsearchResponse> response) {
                    activeCalls.remove(call);
                    if (response.isSuccessful() && response.body() != null) {
                        CiqualElasticsearchResponse r = response.body();
                        if (r.hasResults()) {
                            List<FoodProduct> products = CiqualElasticsearchMapper
                                    .mapSearchResponse(r, effectiveLanguage);
                            SearchResult result = new SearchResult(
                                    products, r.getHits().size(), products.size() >= limit,
                                    query, effectiveLanguage, CiqualConstants.SOURCE_ID);
                            onOperationSuccess();
                            executeOnMainThread(() -> callback.onSuccess(result));
                        } else {
                            SearchResult empty = new SearchResult(
                                    Collections.emptyList(), 0, false,
                                    query, effectiveLanguage, CiqualConstants.SOURCE_ID);
                            onOperationSuccess();
                            executeOnMainThread(() -> callback.onSuccess(empty));
                        }
                    } else {
                        handleError(Error.fromHttpCode(response.code(),
                                "Ciqual search failed", CiqualConstants.SOURCE_ID), callback);
                    }
                }

                @Override
                public void onFailure(@NonNull Call<CiqualElasticsearchResponse> call,
                                      @NonNull Throwable t) {
                    activeCalls.remove(call);
                    if (!call.isCanceled()) {
                        handleError(Error.network(t.getMessage(), CiqualConstants.SOURCE_ID), callback);
                    }
                }
            });
        } catch (Exception e) {
            handleError(Error.fromThrowable(e, "Failed to build Ciqual search request"), callback);
        }
    }


    // ========== PRODUCT LOOKUP ==========

    /**
     * Get single product by Ciqual code
     *
     * AVAILABILITY: Requires API to be ready (not XML parser)
     *
     * @param productId Ciqual product code (e.g., "13004")
     * @param language Language code ("fr" or "en")
     * @param callback Product callback
     */
    @Override
    public void getProduct(@NonNull String productId, @NonNull String language,
                           @NonNull DataSourceCallback<FoodProduct> callback) {
        if (!checkEnabled(callback)) return;

        if (!apiReady.get()) {
            Error error = Error.unknown("Ciqual API not yet initialized", null);
            handleError(error, callback);
            return;
        }

        Log.d(TAG, "Get product: " + productId + " (lang: " + language + ")");

        callback.onLoading();
        onOperationStart();

        // Ensure language is supported
        final String effectiveLanguage = language.equals("en") ? "en" : "fr";

        try {
            // Build exact match query for product code
            String json = CiqualSearchRequest.buildProductQuery(productId, effectiveLanguage);
            RequestBody body = RequestBody.create(MediaType.parse("application/json"), json);

            // Execute API call
            Call<CiqualElasticsearchResponse> call = elasticsearchApi.search(body);

            // Track active call
            activeCalls.add(call);

            call.enqueue(new Callback<CiqualElasticsearchResponse>() {
                @Override
                public void onResponse(@NonNull Call<CiqualElasticsearchResponse> call,
                                       @NonNull Response<CiqualElasticsearchResponse> response) {
                    // Remove from active calls
                    activeCalls.remove(call);

                    if (response.isSuccessful() && response.body() != null) {
                        CiqualElasticsearchResponse apiResponse = response.body();

                        if (apiResponse.hasResults()) {
                            // Get first result (should be exact match)
                            FoodProduct product = CiqualElasticsearchMapper.mapToFoodProduct(
                                    apiResponse.getHits().get(0).getSource(),
                                    effectiveLanguage,
                                    true  // Include nutrition for product details
                            );

                            if (product != null) {
                                onOperationSuccess();
                                executeOnMainThread(() -> callback.onSuccess(product));
                                Log.d(TAG, "Product found: " + productId);
                            } else {
                                // Mapping failed
                                Error error = Error.notFound("Product not found: " + productId);
                                handleError(error, callback);
                            }

                        } else {
                            // No results
                            Error error = Error.notFound("Product not found: " + productId);
                            handleError(error, callback);
                        }

                    } else {
                        // HTTP error
                        Error error = Error.fromHttpCode(
                                response.code(),
                                "Product lookup failed: " + response.message(),
                                CiqualConstants.SOURCE_ID
                        );

                        handleError(error, callback);
                    }
                }

                @Override
                public void onFailure(@NonNull Call<CiqualElasticsearchResponse> call,
                                      @NonNull Throwable t) {
                    // Remove from active calls
                    activeCalls.remove(call);

                    if (call.isCanceled()) {
                        Log.d(TAG, "Product lookup cancelled");
                        return;
                    }

                    // Network error
                    Error error = Error.fromThrowable(t, "Failed to get product from Ciqual");

                    handleError(error, callback);
                }
            });

        } catch (Exception e) {
            Error error = Error.fromThrowable(e, "Failed to build product request");
            handleError(error, callback);
        }
    }

    /**
     * Get product by barcode - NOT SUPPORTED
     */
    @Override
    public void getProductByBarcode(@NonNull String barcode, @NonNull String language,
                                    @NonNull DataSourceCallback<FoodProduct> callback) {
        Log.w(TAG, "Barcode lookup not supported by Ciqual: " + barcode);

        Error error = Error.invalidRequest(
                "Ciqual does not support barcode lookup",
                "Use product code (Ciqual code) instead. Ciqual is a French government database."
        );

        handleError(error, callback);
    }

    // ========== NEW: AUTOCOMPLETE METHOD ==========

    /**
     * Autocomplete search using optimized phrase prefix query
     *
     * NEW METHOD: Separate from regular search() - uses match_phrase_prefix for
     * partial word matching, making it ideal for typeahead suggestions.
     *
     * DIFFERENCE FROM search():
     * - Regular search: Uses multi_match (requires complete words)
     * - Autocomplete: Uses match_phrase_prefix (partial word matching)
     *
     * ERROR HANDLING:
     * - Follows same pattern as search() method
     * - Uses Error.fromHttpCode() for HTTP errors
     * - Uses Error.network() for network errors
     * - Uses Error.fromThrowable() for exceptions
     *
     * @param query Partial query (e.g., "choco", "potat")
     * @param language Language code ("en" or "fr")
     * @param limit Max suggestions (typically 5-10)
     * @param callback Results callback
     */
    public void autocomplete(@NonNull String query, @NonNull String language, int limit,
                             @NonNull DataSourceCallback<SearchResult> callback) {

        if (!checkEnabled(callback)) return;

        if (!apiReady.get()) {
            Error error = Error.unknown("Ciqual API not yet initialized", null);
            handleError(error, callback);
            return;
        }

        // Validate and normalize language
        final String effectiveLanguage = language.equals("en") ? "en" : "fr";

        Log.d(TAG, String.format("Autocomplete: '%s' (lang: %s, limit: %d)",
                query, effectiveLanguage, limit));

        callback.onLoading();
        onOperationStart();

        try {
            // Use AUTOCOMPLETE query (not regular search query!)
            String json = CiqualSearchRequest.buildAutocompleteQuery(
                    query,
                    effectiveLanguage,
                    limit
            );

            RequestBody body = RequestBody.create(MediaType.parse("application/json"), json);

            // Execute API call
            Call<CiqualElasticsearchResponse> call = elasticsearchApi.search(body);

            // Track active call for cancellation
            activeCalls.add(call);

            call.enqueue(new Callback<CiqualElasticsearchResponse>() {
                @Override
                public void onResponse(@NonNull Call<CiqualElasticsearchResponse> call,
                                       @NonNull Response<CiqualElasticsearchResponse> response) {
                    // Remove from active calls
                    activeCalls.remove(call);

                    if (response.isSuccessful() && response.body() != null) {
                        CiqualElasticsearchResponse apiResponse = response.body();

                        if (apiResponse.hasResults()) {
                            // Use mapSearchResponse to convert Elasticsearch hits to FoodProducts
                            List<FoodProduct> products = CiqualElasticsearchMapper.mapSearchResponse(
                                    apiResponse,
                                    effectiveLanguage  // Pass language for proper field extraction
                            );

                            // Create SearchResult with metadata
                            SearchResult result = new SearchResult(
                                    products,
                                    apiResponse.getHits().size(),
                                    products.size() >= limit,
                                    query,
                                    effectiveLanguage,
                                    CiqualConstants.SOURCE_ID
                            );

                            onOperationSuccess();
                            executeOnMainThread(() -> callback.onSuccess(result));

                            Log.d(TAG, String.format("Autocomplete completed: %d results in %s",
                                    products.size(), effectiveLanguage));

                        } else {
                            // Empty result
                            SearchResult emptyResult = new SearchResult(
                                    Collections.emptyList(),
                                    0,
                                    false,
                                    query,
                                    effectiveLanguage,
                                    CiqualConstants.SOURCE_ID
                            );

                            onOperationSuccess();
                            executeOnMainThread(() -> callback.onSuccess(emptyResult));

                            Log.d(TAG, "Autocomplete completed: no results");
                        }

                    } else {
                        // HTTP error - use Error.fromHttpCode() like search() does
                        Error error = Error.fromHttpCode(
                                response.code(),
                                "Ciqual autocomplete failed",
                                CiqualConstants.SOURCE_ID
                        );
                        handleError(error, callback);
                    }
                }

                @Override
                public void onFailure(@NonNull Call<CiqualElasticsearchResponse> call,
                                      @NonNull Throwable t) {
                    // Remove from active calls
                    activeCalls.remove(call);

                    if (!call.isCanceled()) {
                        // Use Error.network() like search() does
                        Error error = Error.network(
                                "Network error: " + t.getMessage(),
                                CiqualConstants.SOURCE_ID
                        );
                        handleError(error, callback);

                        Log.e(TAG, "Autocomplete failed", t);
                    } else {
                        Log.d(TAG, "Autocomplete canceled");
                    }
                }
            });

        } catch (Exception e) {
            // Use Error.fromThrowable() like search() does
            Error error = Error.fromThrowable(e, "Failed to build autocomplete request");
            handleError(error, callback);
        }
    }

    // ========== CAPABILITY METHODS ==========

    @Override
    public boolean supportsBarcodeLookup() {
        return false;  // Ciqual uses product codes, not barcodes
    }

    @Override
    public boolean requiresNetwork() {
        // Phase 1: Always requires network (API only)
        // Phase 3: Will check databaseReady flag
        return !databaseReady;
    }

    @NonNull
    @Override
    public Set<String> getSupportedLanguages() {
        return new HashSet<>(Arrays.asList("fr", "en"));
    }

    @NonNull
    @Override
    public String getPrimaryLanguage() {
        return "fr";  // Ciqual is a French database
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
        logDebug("All operations cancelled");
    }

    @Override
    public void cleanup() {
        super.cleanup();
        cancelOperations();

        // Shutdown executor
        if (backgroundExecutor != null && !backgroundExecutor.isShutdown()) {
            backgroundExecutor.shutdown();
        }

        // Clear state
        apiReady.set(false);
        xmlParserReady.set(false);
        xmlParserInitializing.set(false);

        logDebug("Cleanup completed");
    }
}