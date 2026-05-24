package li.masciul.sugardaddi.business.search;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import li.masciul.sugardaddi.core.interfaces.Searchable;
import li.masciul.sugardaddi.core.models.Error;
import li.masciul.sugardaddi.core.models.FoodProduct;
import li.masciul.sugardaddi.data.database.AppDatabase;
import li.masciul.sugardaddi.data.database.entities.FoodProductEntity;
import li.masciul.sugardaddi.data.network.ApiConfig;
import li.masciul.sugardaddi.data.repository.SearchResultCache;
import li.masciul.sugardaddi.data.sources.base.DataSource;
import li.masciul.sugardaddi.data.sources.base.DataSourceCallback;
import li.masciul.sugardaddi.data.sources.ciqual.CiqualDataSource;
import li.masciul.sugardaddi.data.sources.openfoodfacts.OpenFoodFactsDataSource;
import li.masciul.sugardaddi.data.sources.usda.USDADataSource;
import li.masciul.sugardaddi.managers.DataSourceManager;
import li.masciul.sugardaddi.managers.LanguageManager;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * SearchCache — Search infrastructure for SearchManager.
 *
 * RESPONSIBILITIES
 * ================
 * 1. In-memory LRU result cache (SearchResultCache)
 *    Page-1 results are cached by query string so that repeated searches
 *    are instant without hitting the network.
 *
 * 2. Room enrichment (enrichSearchResultsFromDatabase)
 *    When the user has previously opened a product detail screen, the full
 *    API response (e.g. OFF v2) was saved to Room. That version is richer
 *    than the lightweight Searchalicious search result. This method upgrades
 *    matching FoodProduct items in-place using two batch Room queries.
 *    Recipe items pass through untouched — they have no FoodProductEntity.
 *
 * 3. enrichAndCache() — convenience wrapper used by SearchManager
 *    Runs enrichment on a background thread, optionally writes the result
 *    to SearchResultCache, then invokes onDone on the main thread.
 *
 * WHY THIS CLASS EXISTS
 * =====================
 * Previously this logic lived in ProductRepository, which made SearchManager
 * depend on ProductRepository even though it has nothing to do with product
 * detail operations (barcode lookup, favorites, CRUD). Extracting search
 * infrastructure here breaks that coupling:
 *
 *   SearchManager  →  SearchCache  →  SearchResultCache + AppDatabase
 *   ProductManager →  ProductRepository  →  AppDatabase
 *
 * Neither manager knows about the other's repository.
 *
 * THREADING
 * =========
 * getCachedResults() and put()/invalidate()/clear() are called on any thread
 * — SearchResultCache is fully synchronized.
 *
 * enrichAndCache() dispatches enrichment to backgroundExecutor, then posts
 * onDone to the main thread via mainHandler. Never call Room queries directly
 * on the main thread.
 */
public class SearchCache {

    private static final String TAG = "SearchCache";

    // =========================================================================
    // DEPENDENCIES
    // =========================================================================

    private final Context context;
    private final AppDatabase database;
    private final SearchResultCache resultCache;
    private final DataSourceManager dataSourceManager;
    private final ExecutorService backgroundExecutor;
    private final android.os.Handler mainHandler =
            new android.os.Handler(android.os.Looper.getMainLooper());

    // =========================================================================
    // CONSTRUCTOR
    // =========================================================================

    /**
     * @param context Application or Activity context — used to get AppDatabase singleton.
     */
    public SearchCache(@NonNull Context context) {
        this.context            = context.getApplicationContext();
        this.database           = AppDatabase.getInstance(context.getApplicationContext());
        this.resultCache        = new SearchResultCache();
        this.backgroundExecutor = Executors.newSingleThreadExecutor();
        this.dataSourceManager  = DataSourceManager.getInstance(context.getApplicationContext());
    }

    // =========================================================================
    // CACHE READ / WRITE
    // =========================================================================

    /**
     * Return cached page-1 results for the given query, or null on miss/expiry.
     *
     * Safe to call on any thread — SearchResultCache is synchronized.
     *
     * @param query Normalized search query
     * @return Defensive copy of cached items, or null
     */
    @Nullable
    public List<Searchable> getCachedResults(@NonNull String query) {
        return resultCache.get(query);
    }

    /**
     * Store results in the cache directly (without enrichment).
     * Used when you already have enriched items and just want to persist them.
     *
     * @param query Normalized search query
     * @param items Items to cache — a defensive copy is stored internally
     */
    public void put(@NonNull String query, @NonNull List<Searchable> items) {
        resultCache.put(query, items);
    }

    /**
     * Invalidate a specific query entry.
     * Use when you know results for a query are stale (e.g. after a source reset).
     */
    public void invalidate(@NonNull String query) {
        resultCache.invalidate(query);
    }

    /** Clear all cached entries. */
    public void clear() {
        resultCache.clear();
    }

    /** Human-readable cache statistics for debug logging. */
    @NonNull
    public String getStats() {
        return resultCache.getStats();
    }

    // =========================================================================
    // ENRICHMENT + CACHE
    // =========================================================================

    /**
     * Enrich items from Room and optionally write the result to SearchResultCache.
     *
     * Always runs on a background thread. Calls onDone on the main thread after
     * enrichment (and optional cache write) completes.
     *
     * WHEN TO CACHE:
     *   cacheResult=true  — page 1 results: write to cache so the next search()
     *                        call for the same query is served instantly.
     *   cacheResult=false — pagination pages: do not overwrite the page-1 cache
     *                        entry. Pagination pages are transient — they are
     *                        appended to the adapter, not re-fetched on resume.
     *
     * @param items       Items to enrich in-place (FoodProduct only; Recipe pass-through)
     * @param query       Cache key — only used when cacheResult=true
     * @param cacheResult True to store enriched result in SearchResultCache
     * @param onDone      Runnable invoked on the main thread after completion
     */
    public void enrichAndCache(
            @NonNull List<Searchable> items,
            @NonNull String query,
            boolean cacheResult,
            @NonNull Runnable onDone) {

        backgroundExecutor.execute(() -> {
            enrichSearchResultsFromDatabase(items);
            if (cacheResult) {
                resultCache.put(query, items);
            }
            mainHandler.post(onDone);
        });
    }

    // =========================================================================
    // ROOM ENRICHMENT  (package-visible for testing)
    // =========================================================================

    /**
     * Upgrade FoodProduct search results in-place with richer data from Room.
     *
     * When the user has previously opened a product detail screen, the full
     * API response was saved to Room. That version has more complete data than
     * the lightweight Searchalicious result: richer category (agribalyse name),
     * scores, full nutrition, better images. This method upgrades matching items.
     *
     * IMPLEMENTATION:
     *   1. Separate FoodProduct items by identity type: barcoded vs non-barcoded
     *   2. One batch Room query per type (two queries total, regardless of list size)
     *   3. Build lookup maps: barcode → FoodProduct, searchableId → FoodProduct
     *   4. For each item, call enrichWith(richer) if a DB match is found
     *      enrichWith() is a non-destructive field-level merge — never downgrades
     *
     * Recipe items are skipped — they have no FoodProductEntity representation.
     *
     * THREADING: Must be called on a background thread. Called exclusively from
     * the backgroundExecutor inside enrichAndCache().
     *
     * @param items Search result items to potentially upgrade (modified in-place)
     */
    void enrichSearchResultsFromDatabase(@NonNull List<Searchable> items) {
        if (items.isEmpty()) return;

        // Extract FoodProduct items only
        List<FoodProduct> products = new ArrayList<>();
        for (Searchable item : items) {
            if (item instanceof FoodProduct) {
                products.add((FoodProduct) item);
            }
        }
        if (products.isEmpty()) return;

        try {
            // Separate by identity type
            List<String> barcodes      = new ArrayList<>();
            List<String> searchableIds = new ArrayList<>();

            for (FoodProduct product : products) {
                String barcode = product.getBarcode();
                if (barcode != null && !barcode.trim().isEmpty()) {
                    barcodes.add(barcode.trim());
                } else {
                    String sid = product.getSearchableId();
                    if (sid != null && !sid.trim().isEmpty()) {
                        searchableIds.add(sid.trim());
                    }
                }
            }

            // Batch Room queries — two total, regardless of result set size
            Map<String, FoodProduct> barcodeToRicher = new HashMap<>();
            Map<String, FoodProduct> idToRicher      = new HashMap<>();

            if (!barcodes.isEmpty()) {
                List<FoodProductEntity> cached =
                        database.foodProductDao().getProductsByBarcodes(barcodes);
                for (FoodProductEntity entity : cached) {
                    if (entity.getBarcode() != null) {
                        barcodeToRicher.put(entity.getBarcode(), entity.toFoodProduct());
                    }
                }
            }

            if (!searchableIds.isEmpty()) {
                List<FoodProductEntity> cached =
                        database.foodProductDao().getProductsBySearchableIds(searchableIds);
                for (FoodProductEntity entity : cached) {
                    if (entity.getId() != null) {
                        idToRicher.put(entity.getId(), entity.toFoodProduct());
                    }
                }
            }

            // Enrich each product if a richer Room version exists
            int enrichedCount = 0;
            for (FoodProduct product : products) {
                FoodProduct richer = null;

                String barcode = product.getBarcode();
                if (barcode != null && !barcode.trim().isEmpty()) {
                    richer = barcodeToRicher.get(barcode.trim());
                }
                if (richer == null) {
                    String sid = product.getSearchableId();
                    if (sid != null) richer = idToRicher.get(sid.trim());
                }

                if (richer != null) {
                    product.enrichWith(richer);
                    enrichedCount++;
                }
            }

            if (ApiConfig.DEBUG_LOGGING && enrichedCount > 0) {
                Log.d(TAG, "Enriched " + enrichedCount + "/" + products.size()
                        + " products from Room cache");
            }

        } catch (Exception e) {
            // Never crash the search flow — enrichment is best-effort
            Log.w(TAG, "enrichSearchResultsFromDatabase failed (non-fatal): " + e.getMessage());
        }
    }

    // =========================================================================
    // AUTOCOMPLETE
    // =========================================================================

    /**
     * Lightweight autocomplete across dedicated source endpoints.
     *
     * Calls CiqualDataSource.autocomplete() and OpenFoodFactsDataSource.autocomplete()
     * in parallel — these hit optimised endpoints (match_phrase_prefix for Ciqual,
     * AUTOCOMPLETE_FIELDS for OFF) rather than going through the full aggregator pipeline.
     * Results are merged in insertion order (Ciqual first) with putIfAbsent deduplication.
     *
     * Does NOT use the DataSourceAggregator. Does NOT compete with in-flight searches.
     *
     * @param query    Partial query (min 3 chars, enforced upstream by SearchManager)
     * @param callback Delivers List<Searchable> on the main thread — never null, may be empty
     */
    public void autocomplete(@NonNull String query,
                             @NonNull SearchCache.AutocompleteCallback callback) {
        final String language = LanguageManager.getCurrentLanguage(context).getCode();
        final int limit = 10;

        // Separate active sources by type
        List<DataSource> active = dataSourceManager.getActiveSources();
        List<CiqualDataSource>        ciqualSources = new ArrayList<>();
        List<OpenFoodFactsDataSource> offSources    = new ArrayList<>();
        List<USDADataSource>          usdaSources   = new ArrayList<>();

        for (DataSource source : active) {
            if (source instanceof CiqualDataSource)        ciqualSources.add((CiqualDataSource) source);
            else if (source instanceof OpenFoodFactsDataSource) offSources.add((OpenFoodFactsDataSource) source);
            else if (source instanceof USDADataSource)     usdaSources.add((USDADataSource) source);
        }

        int totalSources = ciqualSources.size() + offSources.size() + usdaSources.size();

        if (totalSources == 0) {
            mainHandler.post(() -> callback.onSuggestions(Collections.emptyList()));
            return;
        }

        AtomicInteger remaining = new AtomicInteger(totalSources);

        // LinkedHashMap preserves insertion order: Ciqual first, then OFF, then USDA
        // putIfAbsent ensures the first source to populate an ID wins
        Map<String, Searchable> merged =
                Collections.synchronizedMap(new LinkedHashMap<>());

        Runnable onSourceDone = () -> {
            if (remaining.decrementAndGet() == 0) {
                List<Searchable> results = new ArrayList<>(merged.values());
                if (ApiConfig.DEBUG_LOGGING) {
                    Log.d(TAG, "Autocomplete: " + results.size()
                            + " suggestions for '" + query + "'");
                }
                mainHandler.post(() -> callback.onSuggestions(results));
            }
        };

        for (CiqualDataSource source : ciqualSources) {
            source.autocomplete(query, language, limit,
                    new DataSourceCallback<DataSource.SearchResult>() {
                        @Override public void onSuccess(DataSource.SearchResult result) {
                            for (Searchable item : result.items) {
                                if (item.getSearchableId() != null) {
                                    merged.putIfAbsent(item.getSearchableId(), item);
                                }
                            }
                            onSourceDone.run();
                        }
                        @Override public void onError(Error error) { onSourceDone.run(); }
                        @Override public void onLoading() {}
                    });
        }

        for (OpenFoodFactsDataSource source : offSources) {
            source.autocomplete(query, language, limit,
                    new DataSourceCallback<DataSource.SearchResult>() {
                        @Override public void onSuccess(DataSource.SearchResult result) {
                            for (Searchable item : result.items) {
                                if (item.getSearchableId() != null) {
                                    merged.putIfAbsent(item.getSearchableId(), item);
                                }
                            }
                            onSourceDone.run();
                        }
                        @Override public void onError(Error error) { onSourceDone.run(); }
                        @Override public void onLoading() {}
                    });
        }

        for (USDADataSource source : usdaSources) {
            source.autocomplete(query, language, limit,
                    new DataSourceCallback<DataSource.SearchResult>() {
                        @Override public void onSuccess(DataSource.SearchResult result) {
                            for (Searchable item : result.items) {
                                if (item.getSearchableId() != null) {
                                    merged.putIfAbsent(item.getSearchableId(), item);
                                }
                            }
                            onSourceDone.run();
                        }
                        @Override public void onError(Error error) { onSourceDone.run(); }
                        @Override public void onLoading() {}
                    });
        }
    }

    /**
     * Callback for autocomplete results.
     */
    public interface AutocompleteCallback {
        /** Called on the main thread with merged suggestions. Never null, may be empty. */
        void onSuggestions(@NonNull List<Searchable> suggestions);
    }

    // =========================================================================
    // LIFECYCLE
    // =========================================================================

    /**
     * Shut down the background executor. Call when the owning component is destroyed.
     */
    public void cleanup() {
        backgroundExecutor.shutdown();
        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, "SearchCache cleaned up. " + getStats());
        }
    }
}