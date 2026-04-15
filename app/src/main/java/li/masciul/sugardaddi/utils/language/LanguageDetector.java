package li.masciul.sugardaddi.utils.language;

import android.content.Context;
import android.util.Log;

import com.google.mlkit.nl.languageid.LanguageIdentification;
import com.google.mlkit.nl.languageid.LanguageIdentifier;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class LanguageDetector {
    private static final String TAG = "LanguageDetector";
    private static LanguageIdentifier languageIdentifier;

    // Cache for detected languages to avoid repeated processing
    private static final ConcurrentHashMap<String, String> languageCache = new ConcurrentHashMap<>();
    private static final int MAX_CACHE_SIZE = 1000;

    static {
        languageIdentifier = LanguageIdentification.getClient();
    }

    /**
     * Detect language synchronously using ONLY heuristics (main-thread safe)
     * This method can be safely called from UI thread
     */
    public static String detectLanguageSync(String text) {
        if (text == null || text.trim().isEmpty()) {
            return "und";
        }

        // Check cache first
        String cached = languageCache.get(text);
        if (cached != null) {
            return cached;
        }

        // Use ONLY heuristics for sync detection (no ML Kit calls)
        String result = detectLanguageHeuristic(text);
        cacheResult(text, result);
        return result;
    }

    /**
     * Async language detection with ML Kit for background processing
     * Use this for better accuracy when you can handle async results
     */
    public static void detectLanguageAsync(String text, LanguageCallback callback) {
        if (text == null || text.trim().isEmpty()) {
            callback.onResult("und");
            return;
        }

        // Check cache first
        String cached = languageCache.get(text);
        if (cached != null) {
            callback.onResult(cached);
            return;
        }

        // For very short texts, use heuristics (faster and reliable)
        if (text.length() < 5) {
            String result = detectLanguageHeuristic(text);
            cacheResult(text, result);
            callback.onResult(result);
            return;
        }

        // Use ML Kit for longer texts (async only)
        languageIdentifier.identifyLanguage(text)
                .addOnSuccessListener(result -> {
                    cacheResult(text, result);
                    callback.onResult(result);
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Async language detection failed for: " + text, e);
                    String fallback = detectLanguageHeuristic(text);
                    cacheResult(text, fallback);
                    callback.onResult(fallback);
                });
    }

    /**
     * Enhanced heuristic language detection (main-thread safe)
     * This is reliable enough for food categories
     */
    private static String detectLanguageHeuristic(String text) {
        String lower = text.toLowerCase().trim();

        // French indicators (accented characters and common food words)
        if (lower.matches(".*[àâäçéèêëïîôöùûüÿæœ].*") ||
                // French food terms
                lower.contains("dérivés") || lower.contains("sucrés") ||
                lower.contains("salés") || lower.contains("chocolat") ||
                lower.contains("lait") || lower.contains("beurre") ||
                lower.contains("confiseries") || lower.contains("biscuits") ||
                lower.contains("gâteaux") || lower.contains("pâtisseries") ||
                lower.contains("fromage") || lower.contains("yaourt") ||
                lower.contains("légumes") || lower.contains("fruits") ||
                lower.contains("viande") || lower.contains("poisson") ||
                // French category patterns
                lower.contains("-et-") || lower.contains("-de-") ||
                lower.contains("-du-") || lower.contains("-des-")) {
            return "fr";
        }

        // English indicators
        if (lower.contains("chocolate") || lower.contains("sweet") ||
                lower.contains("snacks") || lower.contains("cocoa") ||
                lower.contains("milk") || lower.contains("butter") ||
                lower.contains("confectioneries") || lower.contains("cookies") ||
                lower.contains("cakes") || lower.contains("pastries") ||
                lower.contains("cheese") || lower.contains("yogurt") ||
                lower.contains("vegetables") || lower.contains("fruits") ||
                lower.contains("meat") || lower.contains("fish") ||
                // English category patterns
                lower.contains("-and-") || lower.contains("-based") ||
                lower.contains("products") || lower.contains("foods")) {
            return "en";
        }

        // Pattern-based detection
        if (lower.matches(".*-and-.*") || lower.matches(".*products.*")) {
            return "en";
        }

        if (lower.matches(".*-et-.*") || lower.matches(".*dérivés.*")) {
            return "fr";
        }

        // Default: if it has basic Latin characters, assume English
        if (lower.matches(".*[a-z]+.*")) {
            return "en";
        }

        return "und"; // Undetermined
    }

    /**
     * Get the current app language dynamically
     */
    public static String getCurrentAppLanguage(Context context) {
        if (context != null) {
            // Get current app configuration language
            return context.getResources().getConfiguration().getLocales().get(0).getLanguage();
        }

        // Fallback to system locale
        return java.util.Locale.getDefault().getLanguage();
    }

    /**
     * Check if detected language matches the target language
     * Handles language variants and fallbacks automatically
     */
    public static boolean languageMatches(String detectedLanguage, String targetLanguage) {
        if (detectedLanguage == null || targetLanguage == null) {
            return false;
        }

        // Direct match
        if (detectedLanguage.equals(targetLanguage)) {
            return true;
        }

        // Handle language variants (e.g., en-US, en-GB both match "en")
        String detectedBase = detectedLanguage.split("[-_]")[0];
        String targetBase = targetLanguage.split("[-_]")[0];

        return detectedBase.equals(targetBase);
    }

    /**
     * Get supported languages list (easily extensible)
     */
    public static List<String> getSupportedLanguages() {
        return Arrays.asList("fr", "en", "es", "de", "it", "pt", "nl"); // Easy to extend
    }

    /**
     * Find best matching language from supported list
     */
    public static String findBestMatchingLanguage(String detectedLanguage, String preferredLanguage) {
        // First try preferred language
        if (languageMatches(detectedLanguage, preferredLanguage)) {
            return preferredLanguage;
        }

        // Try all supported languages
        for (String supportedLang : getSupportedLanguages()) {
            if (languageMatches(detectedLanguage, supportedLang)) {
                return supportedLang;
            }
        }

        // Fallback to English if no match
        return "en";
    }

    /**
     * Pre-warm cache with common categories (call in background)
     */
    public static void preWarmCacheAsync(String[] commonCategories) {
        new Thread(() -> {
            for (String category : commonCategories) {
                if (!languageCache.containsKey(category)) {
                    // Use async detection for cache warming
                    detectLanguageAsync(category, result -> {
                        // Result automatically cached by detectLanguageAsync
                    });
                }
            }
        }).start();
    }

    /**
     * Cache management with size limit
     */
    private static void cacheResult(String text, String result) {
        if (languageCache.size() >= MAX_CACHE_SIZE) {
            // Simple cache eviction - remove 25% of entries
            languageCache.entrySet().removeIf(entry ->
                    Math.random() < 0.25);
        }
        languageCache.put(text, result);
    }

    /**
     * Clear cache (useful for testing or memory management)
     */
    public static void clearCache() {
        languageCache.clear();
    }

    /**
     * Get cache statistics
     */
    public static String getCacheStats() {
        return String.format("Language cache: %d entries", languageCache.size());
    }

    public interface LanguageCallback {
        void onResult(String languageCode);
    }

    /**
     * Clean up resources
     */
    public static void cleanup() {
        if (languageIdentifier != null) {
            languageIdentifier.close();
        }
        languageCache.clear();
    }
}