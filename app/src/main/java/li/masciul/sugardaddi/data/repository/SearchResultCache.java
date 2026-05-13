package li.masciul.sugardaddi.data.repository;

import android.util.Log;

import li.masciul.sugardaddi.core.interfaces.Searchable;
import li.masciul.sugardaddi.data.network.ApiConfig;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SearchResultCache - LRU cache for search results across all sources.
 *
 * Stores processed, filtered, and scored {@link Searchable} results keyed by
 * query string. Items may be {@link li.masciul.sugardaddi.core.models.FoodProduct}
 * (from Ciqual, OFF, USDA) or {@link li.masciul.sugardaddi.core.models.Recipe}
 * (from TheMealDB) - both are cached uniformly since they implement Searchable.
 *
 * PREVIOUSLY: FoodSearchCache - stored List<FoodProduct> only.
 * UPDATED: Widened to List<Searchable> to support recipe sources.
 *
 * CACHE STRATEGY:
 * - Stores processed/filtered results (not raw API responses)
 * - Query string as key - exact match lookups
 * - LRU eviction when max size is exceeded
 * - Time-based expiration for data freshness
 * - Thread-safe (all public methods synchronized)
 *
 * PERFORMANCE:
 * - Instant results for repeated searches
 * - Reduced network traffic and API quota usage
 * - Lower battery consumption on mobile
 */
public class SearchResultCache {

    private static final String TAG = ApiConfig.CACHE_LOG_TAG;

    // =========================================================================
    // CACHE ENTRY
    // =========================================================================

    /**
     * Cache entry wrapping results and timestamp for expiry checking.
     */
    private static class CacheEntry {
        final List<Searchable> items;
        final long timestamp;
        final String query;

        CacheEntry(String query, List<Searchable> items) {
            this.query = query;
            this.items = new ArrayList<>(items); // Defensive copy
            this.timestamp = System.currentTimeMillis();
        }

        /** True if this entry is still within the expiry window. */
        boolean isFresh() {
            return (System.currentTimeMillis() - timestamp) < ApiConfig.CACHE_EXPIRY_MS;
        }

        /** Age of this entry in seconds - used in log messages. */
        long getAgeSeconds() {
            return (System.currentTimeMillis() - timestamp) / 1000;
        }
    }

    // =========================================================================
    // LRU CACHE
    // =========================================================================

    /**
     * LinkedHashMap in access-order mode provides LRU behaviour:
     * the eldest entry (least recently accessed) is removed when size exceeds max.
     */
    private final Map<String, CacheEntry> cache = new LinkedHashMap<String, CacheEntry>(
            ApiConfig.CACHE_MAX_SIZE + 1,
            0.75f,
            true // Access order - required for LRU eviction
    ) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
            boolean shouldRemove = size() > ApiConfig.CACHE_MAX_SIZE;
            if (shouldRemove && ApiConfig.DEBUG_LOGGING) {
                Log.d(TAG, "SearchResultCache full - removing oldest entry: '"
                        + eldest.getKey() + "' (age: "
                        + eldest.getValue().getAgeSeconds() + "s)");
            }
            return shouldRemove;
        }
    };

    // =========================================================================
    // STATISTICS
    // =========================================================================

    private int hitCount      = 0;
    private int missCount     = 0;
    private int putCount      = 0;
    private int evictionCount = 0;

    // =========================================================================
    // PUBLIC API
    // =========================================================================

    /**
     * Retrieve cached search results for a query.
     *
     * Returns null on cache miss or if the entry has expired - callers
     * should fall through to the network search path in both cases.
     *
     * @param query The search query string.
     * @return Defensive copy of cached items, or null if not found/expired.
     */
    public synchronized List<Searchable> get(String query) {
        if (query == null || query.trim().isEmpty()) return null;

        String normalizedQuery = query.trim();
        CacheEntry entry = cache.get(normalizedQuery);

        if (entry == null) {
            missCount++;
            if (ApiConfig.DEBUG_LOGGING) {
                Log.d(TAG, "Cache miss: '" + normalizedQuery + "'");
            }
            return null;
        }

        if (!entry.isFresh()) {
            cache.remove(normalizedQuery);
            missCount++;
            evictionCount++;
            if (ApiConfig.DEBUG_LOGGING) {
                Log.d(TAG, "Cache expired: '" + normalizedQuery
                        + "' (age: " + entry.getAgeSeconds() + "s)");
            }
            return null;
        }

        hitCount++;
        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, "Cache hit: '" + normalizedQuery
                    + "' (" + entry.items.size() + " items"
                    + ", age: " + entry.getAgeSeconds() + "s)");
        }

        return new ArrayList<>(entry.items); // Defensive copy
    }

    /**
     * Store search results in the cache.
     *
     * Items may be any {@link Searchable} subtype - FoodProduct, Recipe, etc.
     * A defensive copy is stored so external list mutations don't affect the cache.
     *
     * @param query The search query string. Null or empty queries are ignored.
     * @param items The results to cache. Null or empty lists are ignored.
     */
    public synchronized void put(String query, List<Searchable> items) {
        if (query == null || query.trim().isEmpty() || items == null || items.isEmpty()) {
            if (ApiConfig.DEBUG_LOGGING) {
                Log.w(TAG, "Cannot cache null/empty query or items");
            }
            return;
        }

        String normalizedQuery = query.trim();
        cache.put(normalizedQuery, new CacheEntry(normalizedQuery, items));
        putCount++;

        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, "Cached " + items.size() + " items for query: '" + normalizedQuery + "'");
        }
    }

    /**
     * Remove a specific query from the cache.
     * Use when you know results for a query are stale (e.g. after a data source reset).
     *
     * @param query The query to invalidate.
     */
    public synchronized void invalidate(String query) {
        if (query == null) return;
        cache.remove(query.trim());
    }

    /** Clear all entries from the cache. */
    public synchronized void clear() {
        int size = cache.size();
        cache.clear();
        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, "Cache cleared (" + size + " entries removed)");
        }
    }

    /** @return Number of entries currently in the cache. */
    public synchronized int size() {
        return cache.size();
    }

    /**
     * @return Human-readable cache statistics string for logging/debugging.
     */
    public synchronized String getStats() {
        int total = hitCount + missCount;
        float hitRate = total > 0 ? (hitCount * 100f / total) : 0f;
        return String.format(
                "SearchResultCache{size=%d/%d, hits=%d, misses=%d, hitRate=%.1f%%, puts=%d, evictions=%d}",
                cache.size(), ApiConfig.CACHE_MAX_SIZE,
                hitCount, missCount, hitRate, putCount, evictionCount);
    }
}