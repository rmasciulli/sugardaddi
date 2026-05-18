package li.masciul.sugardaddi.data.sources.themealdb;

import android.content.Context;
import android.util.Log;
import android.util.LruCache;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import li.masciul.sugardaddi.core.interfaces.Searchable;
import li.masciul.sugardaddi.core.models.Error;
import li.masciul.sugardaddi.core.models.FoodProduct;
import li.masciul.sugardaddi.core.models.Recipe;
import li.masciul.sugardaddi.data.network.ApiConfig;
import li.masciul.sugardaddi.data.network.NetworkClient;
import li.masciul.sugardaddi.data.network.NetworkConfig;
import li.masciul.sugardaddi.data.sources.base.BaseDataSource;
import li.masciul.sugardaddi.data.sources.base.DataSource;
import li.masciul.sugardaddi.data.sources.base.DataSourceCallback;
import li.masciul.sugardaddi.data.sources.base.settings.SettingsProvider;
import li.masciul.sugardaddi.data.sources.themealdb.api.TheMealDbAPI;
import li.masciul.sugardaddi.data.sources.themealdb.api.dto.MealDbSearchResponse;
import li.masciul.sugardaddi.data.sources.themealdb.mappers.TheMealDbMapper;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * TheMealDbDataSource - TheMealDB recipe data source.
 *
 * ARCHITECTURE
 * ============
 * Extends {@link BaseDataSource} and implements {@link DataSource} so it
 * registers and initialises via {@link li.masciul.sugardaddi.managers.DataSourceManager}
 * exactly like all other sources (OFF, Ciqual, USDA).
 *
 * Unlike food sources, this source returns {@link Recipe} objects wrapped in
 * {@link SearchResult} items. This is possible because {@link SearchResult}
 * now holds {@code List<Searchable>} - both {@link FoodProduct} and {@link Recipe}
 * implement {@link Searchable}.
 *
 * SEARCH FLOW
 * ===========
 * search() → GET search.php?s={query}
 * Returns full Recipe objects (ingredients, instructions, thumbnail, tags).
 * No pagination - TheMealDB returns all matches in one response; we cap at
 * {@link TheMealDbConstants#MAX_SEARCH_RESULTS}.
 *
 * RECIPE DETAIL
 * =============
 * getRecipe() → GET lookup.php?i={id}
 * Overrides DataSource.getRecipe() - the standard pipeline entry point.
 * Checks the LRU cache first; falls back to network via lookupById().
 *
 * PRODUCT DETAIL / BARCODE LOOKUP
 * ================================
 * Not supported. TheMealDB produces recipes, not food products.
 * getProduct() and getProductByBarcode() inherit the DataSource default
 * which fires onError() immediately - no override needed.
 *
 * API KEY
 * =======
 * The development key "1" is a path segment, not a query parameter.
 * Base URL is built dynamically from the active key via
 * {@link TheMealDbConstants#buildBaseUrl(String)}.
 * Key change requires Retrofit reinitialisation - call reinitialize() after
 * saving a new key in Settings.
 *
 * LRU CACHE
 * =========
 * Session-scoped in-memory cache for Recipe objects keyed by TheMealDB meal ID.
 * Not persisted to disk per TheMealDB ToS. Capacity: 50 recipes.
 * Persistent caching is handled by RecipeRepository (Room) on user interaction.
 *
 * THREADING
 * =========
 * onInitialize() runs on BaseDataSource's background init thread.
 * search() and getRecipeById() use Retrofit's async enqueue() - callbacks
 * delivered via executeOnMainThread() from BaseDataSource.
 */
public class TheMealDbDataSource extends BaseDataSource {

    private static final String TAG = "TheMealDbDataSource";

    // =========================================================================
    // FIELDS
    // =========================================================================

    private final TheMealDbConfig  config;
    private final TheMealDbMapper  mapper;
    private final Context          context;

    /** Retrofit API interface - created in onInitialize(). */
    private TheMealDbAPI api;

    /** Active Retrofit calls - tracked for cancellation support. */
    private final Set<Call<?>> activeCalls =
            Collections.synchronizedSet(new HashSet<>());

    /**
     * Session-scoped LRU cache for Recipe objects keyed by TheMealDB meal ID.
     * Capacity: 50 recipes - sufficient for a full browsing session.
     * Not persisted to disk (ToS restriction).
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
     * @param config  TheMealDB network configuration (holds active API key logic)
     */
    public TheMealDbDataSource(@NonNull Context context,
                               @NonNull TheMealDbConfig config) {
        super();
        this.context = context.getApplicationContext();
        this.config  = config;
        this.mapper  = new TheMealDbMapper();

        Log.d(TAG, "TheMealDbDataSource created (deferred initialization)");
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
        return TheMealDbConstants.SOURCE_ID;
    }

    @NonNull
    @Override
    public String getSourceName() {
        return TheMealDbConstants.SOURCE_NAME;
    }

    @NonNull
    @Override
    public SettingsProvider getSettingsProvider() {
        // Pass reinitialize callback so key changes trigger Retrofit rebuild
        return new TheMealDbSettingsProvider(this::reinitialize);
    }

    @Override
    public boolean supportsBarcodeLookup() {
        // TheMealDB has no barcode concept - recipes are identified by meal ID
        return false;
    }

    @NonNull
    @Override
    public Set<String> getSupportedLanguages() {
        // TheMealDB v1 is English-only
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
     *   https://www.themealdb.com/api/json/v1/{key}/
     * This is why we build it here from config.getActiveApiKey() rather than
     * using a static constant.
     */
    @Override
    protected void onInitialize(@NonNull Context context) throws Exception {
        logInfo("Initializing TheMealDB data source...");

        config.validate();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(TheMealDbConstants.buildBaseUrl(config.getActiveApiKey()))
                .client(NetworkClient.createHttpClient(config, context))
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        api = retrofit.create(TheMealDbAPI.class);

        logInfo("TheMealDB API initialized - key: "
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
            Log.e(TAG, "TheMealDB initialization failed", e);
        }
    }

    /**
     * Reinitialize the Retrofit instance after an API key change.
     *
     * Because the TheMealDB key is a URL path segment (not a query parameter),
     * changing the key requires rebuilding the Retrofit instance with the new
     * base URL. This method is called by TheMealDbSettingsProvider via the
     * ReinitializeCallback after the user saves a new key.
     */
    public void reinitialize() {
        initialized = false;
        api = null;
        recipeCache.evictAll();
        Log.d(TAG, "TheMealDB reinitializing with new API key...");
        initialize(context);
    }

    // =========================================================================
    // SEARCH
    // =========================================================================

    /**
     * Search TheMealDB for recipes matching the query.
     *
     * Fires: GET search.php?s={query}
     *
     * Returns up to {@link TheMealDbConstants#MAX_SEARCH_RESULTS} Recipe objects
     * wrapped in a SearchResult. The items list contains Recipe instances
     * (not FoodProduct) - downstream handling must use instanceof or
     * item.getProductType() to discriminate.
     *
     * Short-circuits with an empty success (not an error) when:
     * - Query is shorter than {@link TheMealDbConstants#MIN_QUERY_LENGTH}
     * These are not error conditions - callers treat empty results normally.
     *
     * @param query    Search query. Must not be null.
     * @param language Language code - ignored (TheMealDB is English-only).
     * @param limit    Max results - capped to MAX_SEARCH_RESULTS.
     * @param page     Page number - ignored (TheMealDB has no pagination).
     * @param callback Result callback. Always called on the main thread.
     */
    @Override
    public void search(@NonNull String query,
                       @NonNull String language,
                       int limit,
                       int page,
                       @NonNull DataSourceCallback<SearchResult> callback) {
        if (!checkEnabled(callback)) return;

        if (api == null) {
            handleError(Error.unknown("TheMealDB API not initialized", null), callback);
            return;
        }

        // Enforce minimum query length - very short queries return too much noise
        if (query.trim().length() < TheMealDbConstants.MIN_QUERY_LENGTH) {
            logDebug("Query too short for TheMealDB (" + query.length()
                    + " < " + TheMealDbConstants.MIN_QUERY_LENGTH + ") - returning empty");
            executeOnMainThread(() -> callback.onSuccess(
                    new SearchResult(new ArrayList<>(), 0, false,
                            query, language, TheMealDbConstants.SOURCE_ID)));
            return;
        }

        callback.onLoading();
        onOperationStart();

        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, "Searching TheMealDB: '" + query + "'");
        }

        Call<MealDbSearchResponse> call = api.searchByName(query.trim());
        activeCalls.add(call);

        call.enqueue(new Callback<MealDbSearchResponse>() {

            @Override
            public void onResponse(@NonNull Call<MealDbSearchResponse> call,
                                   @NonNull Response<MealDbSearchResponse> response) {
                activeCalls.remove(call);
                if (call.isCanceled()) return;

                if (!response.isSuccessful()) {
                    handleHttpError(response.code(), "TheMealDB search failed", callback);
                    return;
                }

                // Map DTOs to Recipe domain objects
                List<Recipe> all = mapper.mapSearchResponse(response.body());

                // Cap to MAX_SEARCH_RESULTS - no pagination on TheMealDB
                int effectiveLimit = Math.min(
                        Math.min(limit, TheMealDbConstants.MAX_SEARCH_RESULTS),
                        all.size());
                List<Recipe> capped = all.subList(0, effectiveLimit);

                // Populate LRU cache - detail lookups for these results are free
                for (Recipe recipe : capped) {
                    if (recipe.getOriginalId() != null) {
                        recipeCache.put(recipe.getOriginalId(), recipe);
                    }
                }

                // Widen List<Recipe> to List<Searchable> for SearchResult
                List<Searchable> items = new ArrayList<>(capped);

                SearchResult result = new SearchResult(
                        items,
                        all.size(),
                        false,          // No pagination - TheMealDB is all-or-nothing
                        query,
                        language,
                        TheMealDbConstants.SOURCE_ID
                );

                onOperationSuccess();

                if (ApiConfig.DEBUG_LOGGING) {
                    Log.d(TAG, "TheMealDB search '" + query + "': "
                            + capped.size() + " results (total API: " + all.size() + ")");
                }

                executeOnMainThread(() -> callback.onSuccess(result));
            }

            @Override
            public void onFailure(@NonNull Call<MealDbSearchResponse> call,
                                  @NonNull Throwable t) {
                activeCalls.remove(call);
                if (call.isCanceled()) return;
                Log.e(TAG, "TheMealDB search network failure", t);
                handleError(createNetworkError(t, "TheMealDB search failed"), callback);
            }
        });
    }

    // =========================================================================
    // RECIPE DETAIL - TheMealDB-specific (bypasses DataSource interface)
    // =========================================================================

    /**
     * Fetch a full Recipe by TheMealDB meal ID.
     *
     * Checks the LRU cache first - avoids a network round-trip for recipes
     * already fetched during this session (e.g. from a search result).
     *
     * Called by RecipeRepository when the user opens a TheMealDB recipe detail
     * screen and the recipe is not yet in Room.
     *
     * @param mealDbId TheMealDB numeric ID string (e.g. "52772")
     * @param callback Called with the Recipe, or onError if not found
     */
    @Override
    public void getRecipe(@NonNull String mealDbId,
                          @NonNull String language,
                          @NonNull DataSourceCallback<Recipe> callback) {
        if (!isEnabled()) {
            callback.onError(Error.notFound("TheMealDB is disabled"));
            return;
        }

        if (api == null) {
            callback.onError(Error.unknown("TheMealDB API not initialized", null));
            return;
        }

        // Check LRU cache first
        Recipe cached = recipeCache.get(mealDbId);
        if (cached != null) {
            if (ApiConfig.DEBUG_LOGGING) {
                Log.d(TAG, "LRU cache hit for meal ID: " + mealDbId);
            }
            callback.onSuccess(cached);
            return;
        }

        callback.onLoading();
        onOperationStart();

        Call<MealDbSearchResponse> call = api.lookupById(mealDbId.trim());
        activeCalls.add(call);

        call.enqueue(new Callback<MealDbSearchResponse>() {

            @Override
            public void onResponse(@NonNull Call<MealDbSearchResponse> call,
                                   @NonNull Response<MealDbSearchResponse> response) {
                activeCalls.remove(call);
                if (call.isCanceled()) return;

                if (!response.isSuccessful()) {
                    onOperationError();
                    executeOnMainThread(() -> callback.onError(
                            Error.fromHttpCode(response.code(),
                                    "TheMealDB lookup failed",
                                    TheMealDbConstants.SOURCE_ID)));
                    return;
                }

                List<Recipe> results = mapper.mapSearchResponse(response.body());

                if (results.isEmpty()) {
                    onOperationError();
                    executeOnMainThread(() -> callback.onError(
                            Error.notFound("Recipe not found: " + mealDbId)));
                    return;
                }

                // lookup.php always returns exactly one result
                Recipe recipe = results.get(0);
                recipeCache.put(mealDbId, recipe);

                onOperationSuccess();

                if (ApiConfig.DEBUG_LOGGING) {
                    Log.d(TAG, "Fetched recipe: " + recipe.getDisplayName("en")
                            + " (ID: " + mealDbId + ")");
                }

                executeOnMainThread(() -> callback.onSuccess(recipe));
            }

            @Override
            public void onFailure(@NonNull Call<MealDbSearchResponse> call,
                                  @NonNull Throwable t) {
                activeCalls.remove(call);
                if (call.isCanceled()) return;
                Log.e(TAG, "TheMealDB lookup network failure for ID: " + mealDbId, t);
                handleError(createNetworkError(t, "TheMealDB lookup failed"),
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
        logDebug("All TheMealDB operations cancelled");
    }

    @Override
    public void cleanup() {
        super.cleanup();
        cancelOperations();
        recipeCache.evictAll();
        backgroundExecutor.shutdown();
        logDebug("TheMealDbDataSource cleanup complete");
    }

    // =========================================================================
    // CACHE MANAGEMENT
    // =========================================================================

    /** Clear the session-scoped recipe LRU cache. */
    public void clearCache() {
        recipeCache.evictAll();
        Log.d(TAG, "TheMealDB recipe cache cleared");
    }

    /** @return Number of recipes currently held in the LRU cache. */
    public int getCacheSize() {
        return recipeCache.size();
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    /**
     * Build a typed network Error from a throwable.
     * IOExceptions are classified as network errors; others as unknown.
     */
    @NonNull
    private Error createNetworkError(@NonNull Throwable t,
                                     @NonNull String message) {
        if (t instanceof IOException) {
            return Error.network(message, t.getMessage(),
                    TheMealDbConstants.SOURCE_ID);
        }
        return Error.fromThrowable(t, message);
    }

    private <T> void handleHttpError(int code, String message,
                                     @NonNull DataSourceCallback<T> callback) {
        handleError(Error.fromHttpCode(code, message, TheMealDbConstants.SOURCE_ID), callback);
    }
    
}