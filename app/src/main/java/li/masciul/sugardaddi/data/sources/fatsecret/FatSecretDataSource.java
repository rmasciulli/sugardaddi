package li.masciul.sugardaddi.data.sources.fatsecret;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import li.masciul.sugardaddi.core.enums.ProductType;
import li.masciul.sugardaddi.core.interfaces.Searchable;
import li.masciul.sugardaddi.core.models.Error;
import li.masciul.sugardaddi.core.models.FoodProduct;
import li.masciul.sugardaddi.core.models.Recipe;
import li.masciul.sugardaddi.data.network.NetworkClient;
import li.masciul.sugardaddi.data.network.NetworkConfig;
import li.masciul.sugardaddi.data.sources.base.BaseDataSource;
import li.masciul.sugardaddi.data.sources.base.DataSourceCallback;
import li.masciul.sugardaddi.data.sources.fatsecret.api.FatSecretAPI;
import li.masciul.sugardaddi.data.sources.fatsecret.api.dto.FoodGetResponse;
import li.masciul.sugardaddi.data.sources.fatsecret.api.dto.FoodSearchResponse;
import li.masciul.sugardaddi.data.sources.fatsecret.api.dto.RecipeGetResponse;
import li.masciul.sugardaddi.data.sources.fatsecret.api.dto.RecipeSearchResponse;
import li.masciul.sugardaddi.data.sources.fatsecret.mappers.FatSecretMapper;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * FatSecretDataSource - FatSecret Platform data source, accessed via
 * glucogate (a private server-side proxy - see FatSecretConfig's Javadoc
 * for why this can't call FatSecret directly).
 *
 * ONE SOURCE, TWO PRODUCED TYPES
 * ================================
 * Unlike every other source in this app, FatSecret produces both FOOD
 * (via foods.search/food.get) and RECIPE (via recipes.search/recipe.get)
 * items from a single account - deliberately kept as one DataSource
 * registration, one settings card, one toggle, rather than split into two
 * sources. This required adding a requestedTypes parameter to
 * DataSource.search() itself (see that method's Javadoc) so a "recipes
 * only" filter can skip the foods.search call entirely instead of
 * fetching it and discarding the result downstream.
 *
 * search() dispatches to one or both upstream endpoints depending on
 * requestedTypes:
 * - RECIPE only  → recipes.search
 * - FOOD only    → foods.search
 * - both         → both, in parallel, merged into one SearchResult
 *
 * PAGE NUMBERING
 * ===============
 * FatSecret's page_number is zero-based; this method's own page parameter
 * (like every other source's) is 1-based. Converted once, locally.
 *
 * AVAILABILITY
 * =============
 * Both recipes.search/recipe.get and foods.search/food.get are confirmed
 * to work on Basic tier - neither requires the "premier" OAuth2 scope
 * (only their optional region/generic_description parameters do, which
 * this app doesn't use). So isAvailable() here does NOT gate on the
 * premier capability the way an earlier draft of this class did - only on
 * whether glucogate is configured and reachable at all. Whether results
 * get persisted to Room long-term is a separate question, governed by
 * DataSourceType.FATSECRET.allowsCaching() (already true, per FatSecret's
 * written confirmation), not by anything in this class.
 */
public class FatSecretDataSource extends BaseDataSource {

    private static final String TAG = "FatSecretDataSource";
    private static final String SOURCE_ID = "FATSECRET";

    private final FatSecretConfig config;
    private Context context;

    private FatSecretAPI api;

    public FatSecretDataSource(@NonNull Context context, @NonNull FatSecretConfig config) {
        super();
        this.context = context.getApplicationContext();
        this.config = config;
    }

    // ========================================================================
    // BASEDATASOURCE REQUIRED METHODS
    // ========================================================================

    @NonNull
    @Override
    public NetworkConfig getNetworkConfig() {
        return config;
    }

    @NonNull
    @Override
    public String getSourceId() {
        return SOURCE_ID;
    }

    @NonNull
    @Override
    public String getSourceName() {
        return "FatSecret";
    }

    @NonNull
    @Override
    public Set<ProductType> getProducedTypes() {
        Set<ProductType> types = new HashSet<>();
        types.add(ProductType.FOOD);
        types.add(ProductType.RECIPE);
        return types;
    }

    @Override
    public boolean supportsBarcodeLookup() {
        return false;
    }

    @NonNull
    @Override
    public Set<String> getSupportedLanguages() {
        Set<String> langs = new HashSet<>();
        langs.add("en");
        return langs;
    }

    @NonNull
    @Override
    public String getPrimaryLanguage() {
        return "en";
    }

    @Override
    public boolean isAvailable() {
        // See class Javadoc "AVAILABILITY" - no premier-scope gate here,
        // search genuinely works on Basic tier.
        return super.isAvailable() && config.isConfigured();
    }

    // ========================================================================
    // INITIALIZATION
    // ========================================================================

    @Override
    protected void onInitialize(@NonNull Context context) throws Exception {
        logInfo("Initializing FatSecret data source (via glucogate)...");

        if (!config.isConfigured()) {
            logWarn("GLUCOGATE_BASE_URL/GLUCOGATE_PROXY_SECRET not set - "
                    + "FatSecret features unavailable for this build");
            return;
        }

        config.validate();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(config.getResolvedBaseUrl())
                .client(NetworkClient.createHttpClient(config, context))
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        api = retrofit.create(FatSecretAPI.class);
    }

    // ========================================================================
    // SEARCH - dispatches by requestedTypes
    // ========================================================================

    @Override
    public void search(@NonNull String query, @NonNull String language, int limit, int page,
                       @NonNull Set<ProductType> requestedTypes,
                       @NonNull DataSourceCallback<SearchResult> callback) {
        if (!checkEnabled(callback)) return;

        boolean wantsRecipes = requestedTypes.contains(ProductType.RECIPE);
        boolean wantsFoods = requestedTypes.contains(ProductType.FOOD);

        if (!wantsRecipes && !wantsFoods) {
            // Shouldn't normally happen - the aggregator wouldn't call us
            // with an empty intersection - but fail explicitly rather than
            // silently return nothing.
            callback.onError(Error.validation("FatSecret: requestedTypes is empty", null));
            return;
        }

        onOperationStart();
        callback.onLoading();

        int fatSecretPage = Math.max(0, page - 1); // FatSecret is zero-based

        if (wantsRecipes && wantsFoods) {
            searchBoth(query, language, limit, fatSecretPage, callback);
        } else if (wantsRecipes) {
            searchRecipesOnly(query, language, limit, fatSecretPage, callback);
        } else {
            searchFoodsOnly(query, language, limit, fatSecretPage, callback);
        }
    }

    private void searchRecipesOnly(String query, String language, int limit, int fatSecretPage,
                                   DataSourceCallback<SearchResult> callback) {
        api.searchRecipes(query, fatSecretPage, limit, "json").enqueue(new Callback<RecipeSearchResponse>() {
            @Override
            public void onResponse(@NonNull Call<RecipeSearchResponse> call,
                                   @NonNull Response<RecipeSearchResponse> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    handleError(Error.network(
                            "FatSecret recipes.search failed with status " + response.code(), null), callback);
                    return;
                }

                List<Recipe> recipes = FatSecretMapper.mapRecipeSearchResponse(response.body(), language);
                int total = response.body().recipes != null ? response.body().recipes.totalResults : recipes.size();
                boolean hasMore = (long) (fatSecretPage + 1) * limit < total;

                onOperationSuccess();
                List<Searchable> items = new ArrayList<>(recipes);
                SearchResult result = new SearchResult(items, total, hasMore, query, language, getSourceId());
                executeOnMainThread(() -> callback.onSuccess(result));
            }

            @Override
            public void onFailure(@NonNull Call<RecipeSearchResponse> call, @NonNull Throwable t) {
                handleException(t, "FatSecret recipes.search request failed", callback);
            }
        });
    }

    private void searchFoodsOnly(String query, String language, int limit, int fatSecretPage,
                                 DataSourceCallback<SearchResult> callback) {
        api.searchFoods(query, fatSecretPage, limit, "json").enqueue(new Callback<FoodSearchResponse>() {
            @Override
            public void onResponse(@NonNull Call<FoodSearchResponse> call,
                                   @NonNull Response<FoodSearchResponse> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    handleError(Error.network(
                            "FatSecret foods.search failed with status " + response.code(), null), callback);
                    return;
                }

                List<FoodProduct> foods = FatSecretMapper.mapFoodSearchResponse(response.body(), language);
                int total = response.body().foods != null ? response.body().foods.totalResults : foods.size();
                boolean hasMore = (long) (fatSecretPage + 1) * limit < total;

                onOperationSuccess();
                List<Searchable> items = new ArrayList<>(foods);
                SearchResult result = new SearchResult(items, total, hasMore, query, language, getSourceId());
                executeOnMainThread(() -> callback.onSuccess(result));
            }

            @Override
            public void onFailure(@NonNull Call<FoodSearchResponse> call, @NonNull Throwable t) {
                handleException(t, "FatSecret foods.search request failed", callback);
            }
        });
    }

    /**
     * Both endpoints are queried in parallel and merged into one
     * SearchResult. Partial failure is tolerated: if one endpoint fails but
     * the other succeeds, the successful half is still delivered rather
     * than failing the whole search - only fails outright if BOTH calls
     * fail. Thread-safety: Retrofit callback threads aren't guaranteed to
     * be the main thread in this codebase (every other async path here
     * explicitly hops via executeOnMainThread rather than assuming it), so
     * the shared merge state uses thread-safe primitives throughout.
     */
    private void searchBoth(String query, String language, int limit, int fatSecretPage,
                            DataSourceCallback<SearchResult> callback) {
        List<Searchable> combined = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger remaining = new AtomicInteger(2);
        AtomicBoolean anySucceeded = new AtomicBoolean(false);
        AtomicInteger combinedTotal = new AtomicInteger(0);
        AtomicBoolean anyHasMore = new AtomicBoolean(false);

        Runnable deliverIfDone = () -> {
            if (remaining.decrementAndGet() != 0) return;

            if (!anySucceeded.get()) {
                handleError(Error.network("FatSecret: both recipe and food search failed", null), callback);
                return;
            }

            onOperationSuccess();
            SearchResult result = new SearchResult(
                    new ArrayList<>(combined), combinedTotal.get(), anyHasMore.get(),
                    query, language, getSourceId());
            executeOnMainThread(() -> callback.onSuccess(result));
        };

        api.searchRecipes(query, fatSecretPage, limit, "json").enqueue(new Callback<RecipeSearchResponse>() {
            @Override
            public void onResponse(@NonNull Call<RecipeSearchResponse> call,
                                   @NonNull Response<RecipeSearchResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<Recipe> recipes = FatSecretMapper.mapRecipeSearchResponse(response.body(), language);
                    combined.addAll(recipes);
                    if (response.body().recipes != null) {
                        combinedTotal.addAndGet(response.body().recipes.totalResults);
                        if ((long) (fatSecretPage + 1) * limit < response.body().recipes.totalResults) {
                            anyHasMore.set(true);
                        }
                    }
                    anySucceeded.set(true);
                } else {
                    Log.w(TAG, "FatSecret recipes.search failed with status " + response.code()
                            + " (continuing with food results, if any)");
                }
                deliverIfDone.run();
            }

            @Override
            public void onFailure(@NonNull Call<RecipeSearchResponse> call, @NonNull Throwable t) {
                Log.w(TAG, "FatSecret recipes.search request failed (continuing with food results, if any)", t);
                deliverIfDone.run();
            }
        });

        api.searchFoods(query, fatSecretPage, limit, "json").enqueue(new Callback<FoodSearchResponse>() {
            @Override
            public void onResponse(@NonNull Call<FoodSearchResponse> call,
                                   @NonNull Response<FoodSearchResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<FoodProduct> foods = FatSecretMapper.mapFoodSearchResponse(response.body(), language);
                    combined.addAll(foods);
                    if (response.body().foods != null) {
                        combinedTotal.addAndGet(response.body().foods.totalResults);
                        if ((long) (fatSecretPage + 1) * limit < response.body().foods.totalResults) {
                            anyHasMore.set(true);
                        }
                    }
                    anySucceeded.set(true);
                } else {
                    Log.w(TAG, "FatSecret foods.search failed with status " + response.code()
                            + " (continuing with recipe results, if any)");
                }
                deliverIfDone.run();
            }

            @Override
            public void onFailure(@NonNull Call<FoodSearchResponse> call, @NonNull Throwable t) {
                Log.w(TAG, "FatSecret foods.search request failed (continuing with recipe results, if any)", t);
                deliverIfDone.run();
            }
        });
    }

    // ========================================================================
    // DETAIL FETCHES - no type ambiguity, each implies its own type
    // ========================================================================

    @Override
    public void getRecipe(@NonNull String recipeId, @NonNull String language,
                          @NonNull DataSourceCallback<Recipe> callback) {
        if (!checkEnabled(callback)) return;

        onOperationStart();
        callback.onLoading();

        api.getRecipe(recipeId, "json").enqueue(new Callback<RecipeGetResponse>() {
            @Override
            public void onResponse(@NonNull Call<RecipeGetResponse> call,
                                   @NonNull Response<RecipeGetResponse> response) {
                if (!response.isSuccessful() || response.body() == null || response.body().recipe == null) {
                    handleError(Error.network(
                            "FatSecret recipe.get failed with status " + response.code(), null), callback);
                    return;
                }

                Recipe recipe = FatSecretMapper.mapRecipeDetail(response.body().recipe, language);
                if (recipe == null) {
                    handleError(Error.validation("FatSecret returned an invalid recipe", null), callback);
                    return;
                }

                onOperationSuccess();
                executeOnMainThread(() -> callback.onSuccess(recipe));
            }

            @Override
            public void onFailure(@NonNull Call<RecipeGetResponse> call, @NonNull Throwable t) {
                handleException(t, "FatSecret recipe.get request failed", callback);
            }
        });
    }

    @Override
    public void getProduct(@NonNull String productId, @NonNull String language,
                           @NonNull DataSourceCallback<FoodProduct> callback) {
        if (!checkEnabled(callback)) return;

        onOperationStart();
        callback.onLoading();

        api.getFood(productId, "json").enqueue(new Callback<FoodGetResponse>() {
            @Override
            public void onResponse(@NonNull Call<FoodGetResponse> call,
                                   @NonNull Response<FoodGetResponse> response) {
                if (!response.isSuccessful() || response.body() == null || response.body().food == null) {
                    handleError(Error.network(
                            "FatSecret food.get failed with status " + response.code(), null), callback);
                    return;
                }

                FoodProduct product = FatSecretMapper.mapFoodDetail(response.body().food, language);
                if (product == null) {
                    handleError(Error.validation("FatSecret returned an invalid food", null), callback);
                    return;
                }

                onOperationSuccess();
                executeOnMainThread(() -> callback.onSuccess(product));
            }

            @Override
            public void onFailure(@NonNull Call<FoodGetResponse> call, @NonNull Throwable t) {
                handleException(t, "FatSecret food.get request failed", callback);
            }
        });
    }

    @Override
    public void cancelOperations() {
        // TODO: track and cancel in-flight calls if this becomes necessary -
        // low priority given calls are short-lived and infrequent relative
        // to, say, a search-as-you-type flow against a single-type source.
        logDebug("cancelOperations() called on FATSECRET");
    }
}