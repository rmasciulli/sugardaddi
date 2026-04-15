package li.masciul.sugardaddi.data.repository;

import android.util.Log;
import li.masciul.sugardaddi.data.network.ApiConfig;
import li.masciul.sugardaddi.core.models.FoodProduct;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * FoodSearchCache - Intelligent caching system for search results
 *
 * This cache implementation provides fast access to recently searched food items,
 * dramatically improving user experience by reducing unnecessary API calls.
 *
 * Features:
 * - LRU (Least Recently Used) eviction policy
 * - Time-based expiration for data freshness
 * - Memory-efficient storage with configurable limits
 * - Thread-safe operations for concurrent access
 * - Detailed statistics for monitoring and optimization
 *
 * Cache Strategy:
 * - Stores processed/filtered search results (not raw API responses)
 * - Uses query string as key for exact match lookups
 * - Automatically removes old entries to prevent memory bloat
 * - Balances performance with data freshness
 *
 * Performance Benefits:
 * - Instant results for repeated searches
 * - Reduced network traffic and API quota usage
 * - Lower battery consumption on mobile devices
 * - Better offline experience (cached results remain available)
 *
 * Memory Management:
 * - Configurable maximum cache size
 * - Automatic cleanup of expired entries
 * - Efficient data structures to minimize memory overhead
 */
public class FoodSearchCache {

    private static final String TAG = ApiConfig.NETWORK_LOG_TAG;

    /**
     * Cache entry wrapper that includes timestamp for expiration checking
     */
    private static class CacheEntry {
        final List<FoodProduct> items;
        final long timestamp;
        final String query;

        CacheEntry(String query, List<FoodProduct> items) {
            this.query = query;
            this.items = new ArrayList<>(items); // Defensive copy
            this.timestamp = System.currentTimeMillis();
        }

        /**
         * Check if this cache entry is still fresh
         */
        boolean isFresh() {
            return (System.currentTimeMillis() - timestamp) < ApiConfig.CACHE_EXPIRY_MS;
        }

        /**
         * Get age of this entry in seconds
         */
        long getAgeSeconds() {
            return (System.currentTimeMillis() - timestamp) / 1000;
        }
    }

    /**
     * LRU cache with automatic size management
     * This implementation automatically removes the least recently used entries
     * when the cache exceeds the maximum size limit.
     */
    private final Map<String, CacheEntry> cache = new LinkedHashMap<String, CacheEntry>(
            ApiConfig.CACHE_MAX_SIZE + 1, // Initial capacity
            0.75f, // Load factor
            true // Access order (LRU behavior)
    ) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
            boolean shouldRemove = size() > ApiConfig.CACHE_MAX_SIZE;
            if (shouldRemove && ApiConfig.DEBUG_LOGGING) {
                Log.d(TAG, "Cache full - removing oldest entry: '" + eldest.getKey() +
                        "' (age: " + eldest.getValue().getAgeSeconds() + "s)");
            }
            return shouldRemove;
        }
    };

    // Cache statistics
    private int hitCount = 0;
    private int missCount = 0;
    private int putCount = 0;
    private int evictionCount = 0;

    /**
     * Retrieve cached search results for a query
     *
     * @param query The search query string (case-sensitive)
     * @return List of cached FoodItems, or null if not found/expired
     */
    public synchronized List<FoodProduct> get(String query) {
        if (query == null || query.trim().isEmpty()) {
            return null;
        }

        // Normalize query for consistent cache keys
        String normalizedQuery = query.trim();

        CacheEntry entry = cache.get(normalizedQuery);

        if (entry == null) {
            // Cache miss - no entry for this query
            missCount++;
            if (ApiConfig.DEBUG_LOGGING) {
                Log.d(TAG, "Cache miss for query: '" + normalizedQuery + "'");
            }
            return null;
        }

        if (!entry.isFresh()) {
            // Entry exists but is expired - remove it
            cache.remove(normalizedQuery);
            missCount++;
            evictionCount++;

            if (ApiConfig.DEBUG_LOGGING) {
                Log.d(TAG, "Cache entry expired for query: '" + normalizedQuery +
                        "' (age: " + entry.getAgeSeconds() + "s)");
            }
            return null;
        }

        // Cache hit - return fresh results
        hitCount++;
        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, "Cache hit for query: '" + normalizedQuery +
                    "' (" + entry.items.size() + " items, age: " + entry.getAgeSeconds() + "s)");
        }

        // Return defensive copy to prevent external modification
        return new ArrayList<>(entry.items);
    }

    /**
     * Store search results in the cache
     *
     * @param query The search query string
     * @param items The search results to cache
     */
    public synchronized void put(String query, List<FoodProduct> items) {
        if (query == null || query.trim().isEmpty() || items == null) {
            Log.w(TAG, "Cannot cache null or empty query/items");
            return;
        }

        String normalizedQuery = query.trim();

        // Don't cache empty results or very small result sets
        if (items.isEmpty()) {
            if (ApiConfig.DEBUG_LOGGING) {
                Log.d(TAG, "Not caching empty results for: '" + normalizedQuery + "'");
            }
            return;
        }

        CacheEntry entry = new CacheEntry(normalizedQuery, items);
        cache.put(normalizedQuery, entry);
        putCount++;

        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, "Cached " + items.size() + " items for query: '" + normalizedQuery +
                    "' (cache size: " + cache.size() + "/" + ApiConfig.CACHE_MAX_SIZE + ")");
        }
    }

    /**
     * Remove a specific query from the cache
     *
     * @param query The query to remove
     * @return true if the query was found and removed
     */
    public synchronized boolean remove(String query) {
        if (query == null) return false;

        String normalizedQuery = query.trim();
        CacheEntry removed = cache.remove(normalizedQuery);

        if (removed != null && ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, "Manually removed cache entry for: '" + normalizedQuery + "'");
        }

        return removed != null;
    }

    /**
     * Clear all cached entries
     * Useful for memory management or data refresh scenarios
     */
    public synchronized void clear() {
        int size = cache.size();
        cache.clear();
        evictionCount += size;

        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, "Cache cleared - removed " + size + " entries");
        }
    }

    /**
     * Remove all expired entries from the cache
     * This is automatically called periodically, but can be manually triggered
     *
     * @return Number of expired entries removed
     */
    public synchronized int cleanupExpiredEntries() {
        int removedCount = 0;
        long currentTime = System.currentTimeMillis();

        // Use iterator to safely remove entries while iterating
        var iterator = cache.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();

            if ((currentTime - entry.getValue().timestamp) >= ApiConfig.CACHE_EXPIRY_MS) {
                iterator.remove();
                removedCount++;
                evictionCount++;
            }
        }

        if (removedCount > 0 && ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, "Cleanup removed " + removedCount + " expired entries");
        }

        return removedCount;
    }

    /**
     * Get current cache size
     */
    public synchronized int size() {
        return cache.size();
    }

    /**
     * Check if cache is empty
     */
    public synchronized boolean isEmpty() {
        return cache.isEmpty();
    }

    /**
     * Get cache hit rate as a percentage
     */
    public synchronized double getHitRate() {
        int totalRequests = hitCount + missCount;
        return totalRequests > 0 ? (hitCount * 100.0 / totalRequests) : 0.0;
    }

    /**
     * Get detailed cache statistics for monitoring and debugging
     */
    public synchronized String getStats() {
        int totalRequests = hitCount + missCount;
        return String.format(
                "Cache Stats: Size=%d/%d, Hits=%d, Misses=%d, Puts=%d, Evictions=%d, Hit Rate=%.1f%%",
                cache.size(), ApiConfig.CACHE_MAX_SIZE, hitCount, missCount, putCount,
                evictionCount, getHitRate()
        );
    }

    /**
     * Get detailed information about cached queries
     * Useful for debugging and understanding cache behavior
     */
    public synchronized String getCacheContents() {
        StringBuilder sb = new StringBuilder();
        sb.append("Cached Queries (").append(cache.size()).append("):\n");

        for (Map.Entry<String, CacheEntry> entry : cache.entrySet()) {
            CacheEntry cacheEntry = entry.getValue();
            sb.append("  '").append(entry.getKey()).append("': ")
                    .append(cacheEntry.items.size()).append(" items, ")
                    .append(cacheEntry.getAgeSeconds()).append("s old, ")
                    .append(cacheEntry.isFresh() ? "fresh" : "EXPIRED").append("\n");
        }

        return sb.toString();
    }

    /**
     * Reset all statistics counters
     * Useful for testing or periodic monitoring resets
     */
    public synchronized void resetStats() {
        hitCount = 0;
        missCount = 0;
        putCount = 0;
        evictionCount = 0;

        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, "Cache statistics reset");
        }
    }

    /**
     * Check if a specific query is cached and fresh
     *
     * @param query The query to check
     * @return true if the query is cached and not expired
     */
    public synchronized boolean contains(String query) {
        if (query == null) return false;

        String normalizedQuery = query.trim();
        CacheEntry entry = cache.get(normalizedQuery);

        return entry != null && entry.isFresh();
    }
}