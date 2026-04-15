package li.masciul.sugardaddi.ui.utils;

import android.content.Context;
import android.util.Log;

import com.bumptech.glide.Glide;

import java.io.File;

/**
 * ImageCacheManager - Utility for managing Glide's image cache
 *
 * Provides methods to:
 * - Clear memory cache (synchronous, call on main thread)
 * - Clear disk cache (asynchronous, call on background thread)
 * - Get cache size
 * - Clear corrupted cache entries
 *
 * Use when:
 * - Images fail to load repeatedly
 * - Corrupted cache suspected
 * - Low storage space
 * - After app update
 */
public class ImageCacheManager {

    private static final String TAG = "ImageCacheManager";

    /**
     * Clear memory cache immediately (synchronous)
     * Must be called on the main thread
     */
    public static void clearMemoryCache(Context context) {
        try {
            Glide.get(context).clearMemory();
            Log.i(TAG, "Memory cache cleared");
        } catch (Exception e) {
            Log.e(TAG, "Error clearing memory cache", e);
        }
    }

    /**
     * Clear disk cache asynchronously
     * Can be called from any thread, runs on background thread
     */
    public static void clearDiskCache(Context context) {
        new Thread(() -> {
            try {
                Glide.get(context).clearDiskCache();
                Log.i(TAG, "Disk cache cleared");
            } catch (Exception e) {
                Log.e(TAG, "Error clearing disk cache", e);
            }
        }).start();
    }

    /**
     * Clear all caches (memory + disk)
     */
    public static void clearAllCaches(Context context) {
        clearMemoryCache(context);
        clearDiskCache(context);
    }

    /**
     * Get disk cache directory
     */
    public static File getCacheDir(Context context) {
        return Glide.getPhotoCacheDir(context);
    }

    /**
     * Get approximate cache size in MB
     */
    public static long getCacheSizeMB(Context context) {
        try {
            File cacheDir = getCacheDir(context);
            if (cacheDir != null && cacheDir.exists()) {
                long sizeBytes = getDirSize(cacheDir);
                return sizeBytes / (1024 * 1024); // Convert to MB
            }
        } catch (Exception e) {
            Log.e(TAG, "Error calculating cache size", e);
        }
        return 0;
    }

    /**
     * Calculate directory size recursively
     */
    private static long getDirSize(File dir) {
        long size = 0;
        try {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        size += getDirSize(file);
                    } else {
                        size += file.length();
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error calculating directory size", e);
        }
        return size;
    }

    /**
     * Clear cache if it exceeds specified size
     *
     * @param context Application context
     * @param maxSizeMB Maximum cache size in MB
     */
    public static void clearCacheIfNeeded(Context context, long maxSizeMB) {
        long currentSizeMB = getCacheSizeMB(context);
        if (currentSizeMB > maxSizeMB) {
            Log.i(TAG, "Cache size (" + currentSizeMB + " MB) exceeds limit (" + maxSizeMB + " MB), clearing...");
            clearDiskCache(context);
        }
    }
}