package li.masciul.sugardaddi.data.repository;

import android.util.Log;
import li.masciul.sugardaddi.core.enums.DataSource;
import li.masciul.sugardaddi.core.models.FoodProduct;
import li.masciul.sugardaddi.data.network.ApiConfig;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ProductDetailsCache - Short-term cache for product details
 *
 * ARCHITECTURE v1.0 - Respects Data Source Caching Policies
 *
 * This cache stores individual product details that were viewed by the user.
 * It's different from FoodSearchCache which caches search results.
 *
 * WHY THIS CACHE?
 * - User opens product detail screen
 * - User hits back button
 * - User reopens same product
 * - Without cache: Re-fetch from API/database
 * - With cache: Instant display
 *
 * CACHING POLICY - RESPECTS API TERMS OF SERVICE:
 * - OpenFoodFacts: ✅ Cached (Open Database License allows it)
 * - Ciqual: ❌ NOT cached (Local database)
 * - USDA: ❌ NOT cached (API terms prohibit caching)
 * - User content: ✅ Cached (user owns the data)
 *
 * CACHE STRATEGY:
 * - Very short duration: 5 minutes (not 1 hour like search cache)
 * - Small size: 50 products max (not 500 like search cache)
 * - LRU eviction: Least recently viewed products removed first
 * - Source-aware: Only caches sources that allow caching
 *
 * PERFORMANCE:
 * - Minimal memory footprint (50 products × ~5KB each = ~250KB)
 * - Fast lookup (HashMap O(1))
 * - Automatic cleanup of expired entries
 *
 * @see DataSource#allowsCaching()
 */
public class ProductDetailsCache {

    private static final String TAG = ApiConfig.CACHE_LOG_TAG;

    /**
     * Cache configuration
     * - EXPIRY: 5 minutes (much shorter than search cache)
     * - MAX_SIZE: 50 products (much smaller than search cache)
     */
    private static final long CACHE_EXPIRY_MS = 5 * 60 * 1000; // 5 minutes
    private static final int MAX_CACHE_SIZE = 50; // Small cache

    /**
     * Cache entry wrapper with timestamp
     */
    private static class CacheEntry {
        final FoodProduct product;
        final long timestamp;
        final DataSource source;

        CacheEntry(FoodProduct product) {
            this.product = product;
            this.timestamp = System.currentTimeMillis();
            this.source = product.getDataSource();
        }

        /**
         * Check if this entry is still fresh
         */
        boolean isFresh() {
            return (System.currentTimeMillis() - timestamp) < CACHE_EXPIRY_MS;
        }

        /**
         * Get age in seconds
         */
        long getAgeSeconds() {
            return (System.currentTimeMillis() - timestamp) / 1000;
        }
    }

    /**
     * LRU cache for product details
     * Key: Product identifier (barcode or database ID)
     */
    private final Map<String, CacheEntry> cache = new LinkedHashMap<String, CacheEntry>(
            MAX_CACHE_SIZE + 1,
            0.75f,
            true // Access order for LRU
    ) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
            boolean shouldRemove = size() > MAX_CACHE_SIZE;
            if (shouldRemove && ApiConfig.DEBUG_LOGGING) {
                Log.d(TAG, "ProductDetailsCache full - removing: " + eldest.getKey() +
                        " (age: " + eldest.getValue().getAgeSeconds() + "s)");
            }
            return shouldRemove;
        }
    };

    // Statistics
    private int hitCount = 0;
    private int missCount = 0;
    private int putCount = 0;
    private int rejectedCount = 0; // Products not cached due to source policy

    /**
     * Get cached product by identifier
     *
     * @param identifier Product identifier (barcode or database ID)
     * @return Cached product or null if not found/expired
     */
    public synchronized FoodProduct get(String identifier) {
        if (identifier == null || identifier.trim().isEmpty()) {
            return null;
        }

        String normalizedId = identifier.trim();
        CacheEntry entry = cache.get(normalizedId);

        if (entry == null) {
            missCount++;
            if (ApiConfig.DEBUG_LOGGING) {
                Log.d(TAG, "ProductDetailsCache miss: " + normalizedId);
            }
            return null;
        }

        if (!entry.isFresh()) {
            // Expired - remove it
            cache.remove(normalizedId);
            missCount++;

            if (ApiConfig.DEBUG_LOGGING) {
                Log.d(TAG, "ProductDetailsCache expired: " + normalizedId +
                        " (age: " + entry.getAgeSeconds() + "s)");
            }
            return null;
        }

        // Cache hit!
        hitCount++;
        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, "ProductDetailsCache HIT: " + normalizedId +
                    " (age: " + entry.getAgeSeconds() + "s, source: " + entry.source + ")");
        }

        return entry.product;
    }

    /**
     * Cache a product detail
     *
     * IMPORTANT: Respects data source caching policies!
     * - OpenFoodFacts: Cached ✅
     * - Ciqual: NOT cached ❌ NOT cached (Local database)
     * - USDA: NOT cached ❌ (API terms prohibit)
     * - User content: Cached ✅
     *
     * @param identifier Product identifier (barcode or database ID)
     * @param product Product to cache
     */
    public synchronized void put(String identifier, FoodProduct product) {
        if (identifier == null || identifier.trim().isEmpty() || product == null) {
            Log.w(TAG, "Cannot cache null identifier or product");
            return;
        }

        String normalizedId = identifier.trim();
        DataSource source = product.getDataSource();

        // CRITICAL: Check if this source allows caching
        if (source != null && !source.allowsCaching()) {
            rejectedCount++;

            if (ApiConfig.DEBUG_LOGGING) {
                Log.d(TAG, "ProductDetailsCache REJECTED (policy): " + normalizedId +
                        " - Source " + source + " does not allow caching");
            }
            return;
        }

        // Cache the product
        CacheEntry entry = new CacheEntry(product);
        cache.put(normalizedId, entry);
        putCount++;

        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, "ProductDetailsCache CACHED: " + normalizedId +
                    " (source: " + source + ", cache size: " + cache.size() + "/" + MAX_CACHE_SIZE + ")");
        }
    }

    /**
     * Remove a product from cache
     *
     * @param identifier Product identifier
     * @return true if removed
     */
    public synchronized boolean remove(String identifier) {
        if (identifier == null) return false;

        String normalizedId = identifier.trim();
        CacheEntry removed = cache.remove(normalizedId);

        if (removed != null && ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, "ProductDetailsCache removed: " + normalizedId);
        }

        return removed != null;
    }

    /**
     * Clear all cached products
     */
    public synchronized void clear() {
        int size = cache.size();
        cache.clear();

        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, "ProductDetailsCache cleared - removed " + size + " products");
        }
    }

    /**
     * Cleanup expired entries
     *
     * @return Number of expired entries removed
     */
    public synchronized int cleanupExpiredEntries() {
        int removedCount = 0;
        long currentTime = System.currentTimeMillis();

        var iterator = cache.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();

            if ((currentTime - entry.getValue().timestamp) >= CACHE_EXPIRY_MS) {
                iterator.remove();
                removedCount++;
            }
        }

        if (removedCount > 0 && ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, "ProductDetailsCache cleanup: removed " + removedCount + " expired products");
        }

        return removedCount;
    }

    /**
     * Get cache size
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
     * Get hit rate as percentage
     */
    public synchronized double getHitRate() {
        int totalRequests = hitCount + missCount;
        return totalRequests > 0 ? (hitCount * 100.0 / totalRequests) : 0.0;
    }

    /**
     * Get cache statistics
     */
    public synchronized String getStats() {
        return String.format(
                "ProductDetailsCache: Size=%d/%d, Hits=%d, Misses=%d, Puts=%d, Rejected=%d, Hit Rate=%.1f%%",
                cache.size(), MAX_CACHE_SIZE, hitCount, missCount, putCount,
                rejectedCount, getHitRate()
        );
    }

    /**
     * Reset statistics counters
     */
    public synchronized void resetStats() {
        hitCount = 0;
        missCount = 0;
        putCount = 0;
        rejectedCount = 0;

        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, "ProductDetailsCache stats reset");
        }
    }

    /**
     * Check if a product is cached and fresh
     *
     * @param identifier Product identifier
     * @return true if cached and not expired
     */
    public synchronized boolean contains(String identifier) {
        if (identifier == null) return false;

        String normalizedId = identifier.trim();
        CacheEntry entry = cache.get(normalizedId);

        return entry != null && entry.isFresh();
    }

    /**
     * Get cache contents for debugging
     */
    public synchronized String getCacheContents() {
        StringBuilder sb = new StringBuilder();
        sb.append("ProductDetailsCache (").append(cache.size()).append("):\n");

        for (Map.Entry<String, CacheEntry> entry : cache.entrySet()) {
            CacheEntry cacheEntry = entry.getValue();
            sb.append("  '").append(entry.getKey()).append("': ")
                    .append(cacheEntry.source).append(", ")
                    .append(cacheEntry.getAgeSeconds()).append("s old, ")
                    .append(cacheEntry.isFresh() ? "fresh" : "EXPIRED").append("\n");
        }

        return sb.toString();
    }
}