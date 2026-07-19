package li.masciul.sugardaddi.data.sources.thecocktaildb;

import android.content.Context;
import android.util.Log;
import android.util.LruCache;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import li.masciul.sugardaddi.core.enums.ProductType;
import li.masciul.sugardaddi.core.interfaces.Searchable;
import li.masciul.sugardaddi.core.models.Error;
import li.masciul.sugardaddi.core.models.Recipe;
import li.masciul.sugardaddi.data.network.ApiConfig;
import li.masciul.sugardaddi.data.network.NetworkClient;
import li.masciul.sugardaddi.data.network.NetworkConfig;
import li.masciul.sugardaddi.data.sources.base.BaseDataSource;
import li.masciul.sugardaddi.data.sources.base.DataSource;
import li.masciul.sugardaddi.data.sources.base.DataSourceCallback;
import li.masciul.sugardaddi.data.sources.base.management.ManagementProvider;
import li.masciul.sugardaddi.data.sources.thecocktaildb.api.TheCocktailDbAPI;
import li.masciul.sugardaddi.data.sources.thecocktaildb.api.dto.CocktailDbSearchResponse;
import li.masciul.sugardaddi.data.sources.thecocktaildb.mappers.TheCocktailDbMapper;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * TheCocktailDbDataSource - TheCocktailDB cocktail data source.
 *
 * ARCHITECTURE
 * ============
 * Extends {@link BaseDataSource} and implements {@link DataSource} so it
 * registers and initialises via DataSourceManager exactly like all other
 * sources (OFF, Ciqual, USDA, TheMealDB).
 *
 * Returns {@link Recipe} objects wrapped in {@link SearchResult} items.
 * Cocktails are treated as recipes - they share the same domain model,
 * same Room persistence, and same detail screen pipeline.
 *
 * SEARCH FLOW
 * ===========
 * search() → GET search.php?s={query}
 * Returns full Recipe objects (ingredients, instructions, thumbnail, tags).
 * No pagination - TheCocktailDB returns all matches in one response; we cap
 * at {@link TheCocktailDbConstants#MAX_SEARCH_RESULTS}.
 *
 * RECIPE DETAIL
 * =============
 * getRecipe() → GET lookup.php?i={id}
 * Checks the LRU cache first; falls back to network via lookupById().
 *
 * PRODUCT DETAIL / BARCODE LOOKUP
 * ================================
 * Not supported. TheCocktailDB produces cocktail recipes, not food products.
 * getProduct() and getProductByBarcode() inherit the DataSource default no-op.
 *
 * API KEY
 * =======
 * The key is a path segment in the base URL - changing it requires rebuilding
 * the Retrofit instance. Call reinitialize() after saving a new key in Settings.
 *
 * LRU CACHE
 * =========
 * Session-scoped in-memory cache for Recipe objects keyed by TheCocktailDB drink ID.
 * Not persisted to disk. Capacity: 50 cocktails.
 */
public class TheCocktailDbDataSource extends BaseDataSource {

    private static final String TAG = "TheCocktailDbDataSource";

    // =========================================================================
    // FIELDS
    // =========================================================================

    private final TheCocktailDbConfig  config;
    private final TheCocktailDbMapper  mapper;
    private final Context              context;

    /** Retrofit API interface - created in onInitialize(). */
    private TheCocktailDbAPI api;

    /** Active Retrofit calls - tracked for cancellation support. */
    private final Set<Call<?>> activeCalls =
            Collections.synchronizedSet(new HashSet<>());

    /**
     * Session-scoped LRU cache keyed by TheCocktailDB drink ID.
     * Capacity: 50 cocktails - sufficient for a full browsing session.
     */
    private final LruCache<String, Recipe> recipeCache = new LruCache<>(50);

    /** Background executor for cache-check operations. */
    private final ExecutorService backgroundExecutor =
            Executors.newSingleThreadExecutor();

    // =========================================================================
    // CONSTRUCTOR
    // =========================================================================

    /**
     * @param context Application context
     * @param config  TheCocktailDB network configuration (holds active API key logic)
     */
    public TheCocktailDbDataSource(@NonNull Context context,
                                   @NonNull TheCocktailDbConfig config) {
        super();
        this.context = context.getApplicationContext();
        this.config  = config;
        this.mapper  = new TheCocktailDbMapper();

        Log.d(TAG, "TheCocktailDbDataSource created (deferred initialization)");
    }

    // =========================================================================
    // BASEDATASOURCE REQUIRED METHODS
    // =========================================================================

    @NonNull
    @Override
    public NetworkConfig getNetworkConfig() {
        return config;
    }

    @NonNull
    @Override
    public String getSourceId() {
        return TheCocktailDbConstants.SOURCE_ID;
    }

    @NonNull
    @Override
    public String getSourceName() {
        return TheCocktailDbConstants.SOURCE_NAME;
    }

    @NonNull
    @Override
    public Set<ProductType> getProducedTypes() {
        return Collections.singleton(ProductType.RECIPE);
    }

    @NonNull
    @Override
    public ManagementProvider getManagementProvider() {
        // Pass reinitialize callback so key changes trigger Retrofit rebuild immediately
        return new TheCocktailDbManagementProvider(this::reinitialize);
    }

    @Override
    public boolean supportsBarcodeLookup() {
        // TheCocktailDB has no barcode concept
        return false;
    }

    @NonNull
    @Override
    public Set<String> getSupportedLanguages() {
        // TheCocktailDB v1 is English-only
        Set<String> langs = new HashSet<>();
        langs.add("en");
        return langs;
    }

    @NonNull
    @Override
    public String getPrimaryLanguage() {
        return "en";
    }

    // =========================================================================
    // INITIALIZATION
    // =========================================================================

    /**
     * Called by BaseDataSource on a background thread during async init.
     * Creates the Retrofit instance using the active API key.
     *
     * The base URL contains the key as a path segment:
     *   https://www.thecocktaildb.com/api/json/v1/{key}/
     */
    @Override
    protected void onInitialize(@NonNull Context context) throws Exception {
        logInfo("Initializing TheCocktailDB data source...");

        config.validate();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(TheCocktailDbConstants.buildBaseUrl(config.getActiveApiKey()))
                .client(NetworkClient.createHttpClient(config, context))
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        api = retrofit.create(TheCocktailDbAPI.class);

        logInfo("TheCocktailDB API initialized - key: "
                + (config.isUsingDemoKey() ? "DEMO (\"1\")" : "Patreon key"));
    }

    /**
     * Synchronous initialization - delegates to onInitialize().
     * Called by DataSourceManager's synchronous fallback path.
     */
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
            Log.e(TAG, "TheCocktailDB initialization failed", e);
        }
    }

    /**
     * Reinitialize the Retrofit instance after an API key change.
     *
     * Because the key is a URL path segment, changing it requires rebuilding
     * the Retrofit instance with the new base URL. Called by
     * TheCocktailDbManagementProvider via the ReinitializeCallback after the
     * user saves a new key in Settings.
     */
    public void reinitialize() {
        initialized = false;
        api = null;
        recipeCache.evictAll();
        Log.d(TAG, "TheCocktailDB reinitializing with new API key...");
        initialize(context);
    }

    // =========================================================================
    // SEARCH
    // =========================================================================

    /**
     * Search TheCocktailDB for cocktails matching the query.
     *
     * Fires: GET search.php?s={query}
     *
     * Returns up to {@link TheCocktailDbConstants#MAX_SEARCH_RESULTS} Recipe objects
     * wrapped in a SearchResult. The items list contains Recipe instances -
     * downstream handling must use instanceof or item.getProductType() to discriminate.
     *
     * Short-circuits with an empty success (not an error) when the query is
     * shorter than {@link TheCocktailDbConstants#MIN_QUERY_LENGTH}.
     *
     * @param query    Search query. Must not be null.
     * @param language Language code - ignored (TheCocktailDB is English-only).
     * @param limit    Max results - capped to MAX_SEARCH_RESULTS.
     * @param page     Page number (1-based). The upstream API has no real pagination -
     *                 the full match list is fetched every call and paginated locally
     *                 by slicing it according to page/limit.
     * @param callback Result callback. Always called on the main thread.
     */
    @Override
    public void search(@NonNull String query,
                       @NonNull String language,
                       int limit,
                       int page,
                       @NonNull Set<ProductType> requestedTypes,
                       @NonNull DataSourceCallback<SearchResult> callback) {
        // requestedTypes intentionally unused - TheCocktailDB is a
        // recipe-only source; the aggregator already excludes it entirely
        // when RECIPE isn't in the active filter (see DataSource.search()
        // Javadoc).
        if (!checkEnabled(callback)) return;

        if (api == null) {
            handleError(Error.unknown("TheCocktailDB API not initialized", null), callback);
            return;
        }

        // Enforce minimum query length
        if (query.trim().length() < TheCocktailDbConstants.MIN_QUERY_LENGTH) {
            logDebug("Query too short for TheCocktailDB (" + query.length()
                    + " < " + TheCocktailDbConstants.MIN_QUERY_LENGTH + ") - returning empty");
            executeOnMainThread(() -> callback.onSuccess(
                    new SearchResult(new ArrayList<>(), 0, false,
                            query, language, TheCocktailDbConstants.SOURCE_ID)));
            return;
        }

        callback.onLoading();
        onOperationStart();

        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, "Searching TheCocktailDB: '" + query + "'");
        }

        Call<CocktailDbSearchResponse> call = api.searchByName(query.trim());
        activeCalls.add(call);

        call.enqueue(new Callback<CocktailDbSearchResponse>() {

            @Override
            public void onResponse(@NonNull Call<CocktailDbSearchResponse> call,
                                   @NonNull Response<CocktailDbSearchResponse> response) {
                activeCalls.remove(call);
                if (call.isCanceled()) return;

                if (!response.isSuccessful()) {
                    handleHttpError(response.code(), "TheCocktailDB search failed", callback);
                    return;
                }

                // Map DTOs to Recipe domain objects
                List<Recipe> all = mapper.mapSearchResponse(response.body());

                // TheCocktailDB's search.php has no server-side pagination - it
                // always returns every match for the query in one response. Real
                // pagination is done locally here: slice the already-fetched full
                // list by page, and report hasMore based on whether more of that
                // already-fetched data remains. Previously this always took
                // [0, effectiveLimit) regardless of page and hardcoded
                // hasMore=false - silently discarding everything beyond the first
                // page's worth on every call, even though the full list had
                // already been fetched.
                int cappedTotal = Math.min(all.size(), TheCocktailDbConstants.MAX_SEARCH_RESULTS);
                int fromIndex = Math.min((page - 1) * limit, cappedTotal);
                int toIndex = Math.min(fromIndex + limit, cappedTotal);
                List<Recipe> pageItems = all.subList(fromIndex, toIndex);

                // Populate LRU cache - detail lookups for these results are free
                for (Recipe recipe : pageItems) {
                    if (recipe.getOriginalId() != null) {
                        recipeCache.put(recipe.getOriginalId(), recipe);
                    }
                }

                // Widen List<Recipe> to List<Searchable> for SearchResult
                List<Searchable> items = new ArrayList<>(pageItems);

                SearchResult result = new SearchResult(
                        items,
                        all.size(),
                        toIndex < cappedTotal, // more of the already-fetched data remains
                        query,
                        language,
                        TheCocktailDbConstants.SOURCE_ID
                );

                onOperationSuccess();

                if (ApiConfig.DEBUG_LOGGING) {
                    Log.d(TAG, "TheCocktailDB search '" + query + "': page " + page + ", "
                            + pageItems.size() + " results (total API: " + all.size() + ")");
                }

                executeOnMainThread(() -> callback.onSuccess(result));
            }

            @Override
            public void onFailure(@NonNull Call<CocktailDbSearchResponse> call,
                                  @NonNull Throwable t) {
                activeCalls.remove(call);
                if (call.isCanceled()) return;
                Log.e(TAG, "TheCocktailDB search network failure", t);
                handleError(createNetworkError(t, "TheCocktailDB search failed"), callback);
            }
        });
    }

    // =========================================================================
    // RECIPE DETAIL
    // =========================================================================

    /**
     * Fetch a full Recipe by TheCocktailDB drink ID.
     *
     * Checks the LRU cache first - avoids a network round-trip for cocktails
     * already fetched during this session (e.g. from a search result).
     *
     * Called by RecipeRepository when the user opens a TheCocktailDB recipe
     * detail screen and the recipe is not yet in Room.
     *
     * @param drinkId  TheCocktailDB numeric ID string (e.g. "11007")
     * @param language Language code - ignored (English-only source)
     * @param callback Called with the Recipe, or onError if not found
     */
    @Override
    public void getRecipe(@NonNull String drinkId,
                          @NonNull String language,
                          @NonNull DataSourceCallback<Recipe> callback) {
        if (!isEnabled()) {
            callback.onError(Error.notFound("TheCocktailDB is disabled"));
            return;
        }

        if (api == null) {
            callback.onError(Error.unknown("TheCocktailDB API not initialized", null));
            return;
        }

        // Check LRU cache first
        Recipe cached = recipeCache.get(drinkId);
        if (cached != null) {
            if (ApiConfig.DEBUG_LOGGING) {
                Log.d(TAG, "LRU cache hit for drink ID: " + drinkId);
            }
            callback.onSuccess(cached);
            return;
        }

        callback.onLoading();
        onOperationStart();

        Call<CocktailDbSearchResponse> call = api.lookupById(drinkId.trim());
        activeCalls.add(call);

        call.enqueue(new Callback<CocktailDbSearchResponse>() {

            @Override
            public void onResponse(@NonNull Call<CocktailDbSearchResponse> call,
                                   @NonNull Response<CocktailDbSearchResponse> response) {
                activeCalls.remove(call);
                if (call.isCanceled()) return;

                if (!response.isSuccessful()) {
                    onOperationError();
                    executeOnMainThread(() -> callback.onError(
                            Error.fromHttpCode(response.code(),
                                    "TheCocktailDB lookup failed",
                                    TheCocktailDbConstants.SOURCE_ID)));
                    return;
                }

                List<Recipe> results = mapper.mapSearchResponse(response.body());

                if (results.isEmpty()) {
                    onOperationError();
                    executeOnMainThread(() -> callback.onError(
                            Error.notFound("Cocktail not found: " + drinkId)));
                    return;
                }

                // lookup.php always returns exactly one result
                Recipe recipe = results.get(0);
                recipeCache.put(drinkId, recipe);

                onOperationSuccess();

                if (ApiConfig.DEBUG_LOGGING) {
                    Log.d(TAG, "Fetched cocktail: " + recipe.getDisplayName("en")
                            + " (ID: " + drinkId + ")");
                }

                executeOnMainThread(() -> callback.onSuccess(recipe));
            }

            @Override
            public void onFailure(@NonNull Call<CocktailDbSearchResponse> call,
                                  @NonNull Throwable t) {
                activeCalls.remove(call);
                if (call.isCanceled()) return;
                Log.e(TAG, "TheCocktailDB lookup network failure for ID: " + drinkId, t);
                handleError(createNetworkError(t, "TheCocktailDB lookup failed"),
                        new DataSourceCallback<SearchResult>() {
                            @Override public void onSuccess(SearchResult r) {}
                            @Override public void onError(Error e) { callback.onError(e); }
                            @Override public void onLoading() {}
                        });
            }
        });
    }

    // =========================================================================
    // CANCELLATION AND CLEANUP
    // =========================================================================

    @Override
    public void cancelOperations() {
        synchronized (activeCalls) {
            for (Call<?> call : activeCalls) {
                if (call != null && !call.isCanceled()) call.cancel();
            }
            activeCalls.clear();
        }
        logDebug("All TheCocktailDB operations cancelled");
    }

    @Override
    public void cleanup() {
        super.cleanup();
        cancelOperations();
        recipeCache.evictAll();
        backgroundExecutor.shutdown();
        logDebug("TheCocktailDbDataSource cleanup complete");
    }

    // =========================================================================
    // CACHE MANAGEMENT
    // =========================================================================

    /** Clear the session-scoped cocktail LRU cache. */
    public void clearCache() {
        recipeCache.evictAll();
        Log.d(TAG, "TheCocktailDB recipe cache cleared");
    }

    /** @return Number of cocktails currently held in the LRU cache. */
    public int getCacheSize() {
        return recipeCache.size();
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    @NonNull
    private Error createNetworkError(@NonNull Throwable t, @NonNull String message) {
        if (t instanceof IOException) {
            return Error.network(message, t.getMessage(), TheCocktailDbConstants.SOURCE_ID);
        }
        return Error.fromThrowable(t, message);
    }

    private <T> void handleHttpError(int code, String message,
                                     @NonNull DataSourceCallback<T> callback) {
        handleError(Error.fromHttpCode(code, message, TheCocktailDbConstants.SOURCE_ID), callback);
    }
}