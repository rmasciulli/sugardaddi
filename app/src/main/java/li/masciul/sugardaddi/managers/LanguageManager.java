package li.masciul.sugardaddi.managers;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.os.LocaleList;
import java.util.Locale;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import android.util.Log;

/**
 * LanguageManager - Enhanced Language Management with Fallback Strategy
 *
 * MAJOR IMPROVEMENTS:
 * 1. Dynamic language support - easily extensible beyond EN/FR
 * 2. Language fallback chains for robust API operations
 * 3. Best available language selection for incomplete data
 * 4. Integration with NetworkManager for consistent language handling
 * 5. Validation methods for supported languages
 *
 * FALLBACK STRATEGY:
 * When requesting data in a specific language, the system uses this priority:
 * 1. User's preferred language (from settings)
 * 2. English (most complete data coverage)
 * 3. French (second-most complete data coverage)
 * 4. Any available language
 *
 * EXTENSIBILITY:
 * Adding new languages is now as simple as adding entries to SupportedLanguage enum.
 * No code changes needed in NetworkManager or other components.
 */
public class LanguageManager {
    private static final String TAG = "LanguageManager";
    private static final String PREF_NAME = "language_pref";
    private static final String KEY_LANGUAGE = "selected_language";

    /**
     * Supported Languages - Easily extensible enum
     *
     * EXPANSION GUIDE:
     * To add a new language:
     * 1. Add new enum entry with language code, display name, and flag
     * 2. Add corresponding string resources in values-XX/strings.xml
     * 3. That's it! NetworkManager and other components will automatically support it.
     */
    public enum SupportedLanguage {
        ENGLISH("en", "English", "🇬🇧"),
        FRENCH("fr", "Français", "🇫🇷"),
        SPANISH("es", "Español", "🇪🇸"),       // Ready for Spanish support
        GERMAN("de", "Deutsch", "🇩🇪"),        // Ready for German support
        ITALIAN("it", "Italiano", "🇮🇹"),      // Ready for Italian support
        PORTUGUESE("pt", "Português", "🇵🇹"),  // Ready for Portuguese support
        DUTCH("nl", "Nederlands", "🇳🇱");      // Ready for Dutch support

        private final String code;
        private final String displayName;
        private final String flag;

        SupportedLanguage(String code, String displayName, String flag) {
            this.code = code;
            this.displayName = displayName;
            this.flag = flag;
        }

        public String getCode() { return code; }
        public String getDisplayName() { return displayName; }
        public String getFlag() { return flag; }

        /**
         * Find supported language by code with enhanced matching
         * Supports both exact matches (en) and variant matches (en-US -> en)
         */
        public static SupportedLanguage fromCode(String code) {
            if (code == null) return ENGLISH;

            // Try exact match first
            for (SupportedLanguage lang : values()) {
                if (lang.code.equals(code)) {
                    return lang;
                }
            }

            // Try base language match (en-US -> en)
            String baseCode = code.split("[-_]")[0];
            for (SupportedLanguage lang : values()) {
                if (lang.code.equals(baseCode)) {
                    return lang;
                }
            }

            return ENGLISH; // Default fallback
        }

        /**
         * Check if this language matches a given code (including variants)
         */
        public boolean matches(String code) {
            if (code == null) return false;

            // Direct match
            if (this.code.equals(code)) return true;

            // Base language match
            String baseCode = code.split("[-_]")[0];
            return this.code.equals(baseCode);
        }
    }

    // ========== CORE LANGUAGE MANAGEMENT ==========

    /**
     * Get the current language setting with enhanced detection
     *
     * DETECTION PRIORITY:
     * 1. Explicitly set user preference
     * 2. System locale if supported
     * 3. English as fallback
     *
     * @param context Application context
     * @return Currently selected supported language
     */
    public static SupportedLanguage getCurrentLanguage(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String savedLanguage = prefs.getString(KEY_LANGUAGE, null);

        if (savedLanguage != null) {
            SupportedLanguage language = SupportedLanguage.fromCode(savedLanguage);
            Log.d(TAG, "Using saved language preference: " + language.getDisplayName());
            return language;
        }

        // Auto-detect from system locale with enhanced matching
        String systemLanguage = Locale.getDefault().getLanguage();
        SupportedLanguage detectedLanguage = SupportedLanguage.fromCode(systemLanguage);

        Log.d(TAG, "Auto-detected language from system: " + detectedLanguage.getDisplayName() +
                " (system was: " + systemLanguage + ")");

        return detectedLanguage;
    }

    /**
     * Set the app language and apply immediately
     *
     * @param context Application context
     * @param language The language to set
     */
    public static void setLanguage(Context context, SupportedLanguage language) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_LANGUAGE, language.getCode()).apply();

        Locale.setDefault(new Locale(language.getCode()));

        // Update configuration
        Configuration config = context.getResources().getConfiguration();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocale(new Locale(language.getCode()));
        } else {
            config.locale = new Locale(language.getCode());
        }
        context.getResources().updateConfiguration(config,
                context.getResources().getDisplayMetrics());

        // Restart the app properly
        if (context instanceof Activity) {
            Activity activity = (Activity) context;
            Intent intent = activity.getPackageManager()
                    .getLaunchIntentForPackage(activity.getPackageName());
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
            activity.finish();
        }

        Log.d(TAG, "Language set to: " + language.getDisplayName());
    }

    // ========== LANGUAGE FALLBACK STRATEGY ==========

    /**
     * Get language fallback chain for robust API operations
     *
     * This method provides a prioritized list of languages to try when making API calls.
     * If data is unavailable in the first language, try the next one, and so on.
     *
     * FALLBACK PRIORITY:
     * 1. User's preferred language
     * 2. English (most complete OpenFoodFacts data)
     * 3. French (second-most complete data)
     * 4. All other supported languages
     *
     * @param context Application context
     * @return Ordered list of language codes to try
     */
    public static List<String> getLanguageFallbackChain(Context context) {
        List<String> chain = new ArrayList<>();
        SupportedLanguage preferred = getCurrentLanguage(context);

        // 1. User's preferred language
        chain.add(preferred.getCode());

        // 2. English as universal fallback (if not already preferred)
        if (!preferred.equals(SupportedLanguage.ENGLISH)) {
            chain.add(SupportedLanguage.ENGLISH.getCode());
        }

        // 3. French as secondary fallback (if not already included)
        if (!preferred.equals(SupportedLanguage.FRENCH) &&
                !SupportedLanguage.ENGLISH.equals(SupportedLanguage.FRENCH)) {
            chain.add(SupportedLanguage.FRENCH.getCode());
        }

        // 4. All other supported languages as additional fallbacks
        for (SupportedLanguage lang : SupportedLanguage.values()) {
            String langCode = lang.getCode();
            if (!chain.contains(langCode)) {
                chain.add(langCode);
            }
        }

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Language fallback chain: " + chain);
        }

        return chain;
    }

    /**
     * Get the best available language from a set of options
     *
     * This method is useful when you have data available in certain languages
     * and need to pick the best one based on user preferences.
     *
     * @param context Application context
     * @param availableLanguages Set of language codes that have data available
     * @return Best language code to use, or fallback if none match preferences
     */
    public static String getBestAvailableLanguage(Context context, Set<String> availableLanguages) {
        if (availableLanguages == null || availableLanguages.isEmpty()) {
            Log.w(TAG, "No available languages provided, using current language");
            return getCurrentLanguage(context).getCode();
        }

        List<String> fallbackChain = getLanguageFallbackChain(context);

        // Try each language in fallback chain
        for (String preferredLang : fallbackChain) {
            if (availableLanguages.contains(preferredLang)) {
                Log.d(TAG, "Selected language: " + preferredLang +
                        " from available: " + availableLanguages);
                return preferredLang;
            }
        }

        // Last resort: return any available language
        String anyAvailable = availableLanguages.iterator().next();
        Log.w(TAG, "No preferred languages available, using: " + anyAvailable +
                " from: " + availableLanguages);
        return anyAvailable;
    }

    /**
     * Get primary language for API calls
     *
     * This is a convenience method for NetworkManager to get the language
     * that should be used for API calls.
     *
     * @param context Application context
     * @return Language code for API calls
     */
    public static String getPrimaryLanguageForAPI(Context context) {
        return getCurrentLanguage(context).getCode();
    }

    /**
     * Get fallback language for API calls
     *
     * When the primary language fails, this provides the best fallback option.
     *
     * @param context Application context
     * @return Fallback language code for API calls
     */
    public static String getFallbackLanguageForAPI(Context context) {
        List<String> fallbackChain = getLanguageFallbackChain(context);

        // Return second language in chain (first fallback after preferred)
        if (fallbackChain.size() > 1) {
            return fallbackChain.get(1);
        }

        // Ultimate fallback
        return SupportedLanguage.ENGLISH.getCode();
    }

    // ========== VALIDATION AND UTILITY METHODS ==========

    /**
     * Check if a language code is supported by the application
     *
     * @param languageCode Language code to check (e.g., "en", "fr", "en-US")
     * @return true if language is supported
     */
    public static boolean isLanguageSupported(String languageCode) {
        if (languageCode == null) return false;

        for (SupportedLanguage lang : SupportedLanguage.values()) {
            if (lang.matches(languageCode)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Get all supported language codes
     *
     * Useful for TaxonomyManager and other components that need to know
     * which languages to process.
     *
     * @return Set of supported language codes
     */
    public static Set<String> getSupportedLanguageCodes() {
        Set<String> codes = new HashSet<>();
        for (SupportedLanguage lang : SupportedLanguage.values()) {
            codes.add(lang.getCode());
        }
        return codes;
    }

    /**
     * Get supported language by code with validation
     *
     * @param languageCode Language code to find
     * @return SupportedLanguage object, or null if not supported
     */
    public static SupportedLanguage getSupportedLanguage(String languageCode) {
        if (!isLanguageSupported(languageCode)) {
            return null;
        }
        return SupportedLanguage.fromCode(languageCode);
    }

    /**
     * Normalize language code to supported format
     *
     * Converts language variants (en-US, en_US) to base language codes (en)
     * that are used by the application.
     *
     * @param languageCode Raw language code
     * @return Normalized language code, or null if not supported
     */
    public static String normalizeLanguageCode(String languageCode) {
        if (!isLanguageSupported(languageCode)) {
            return null;
        }

        SupportedLanguage lang = SupportedLanguage.fromCode(languageCode);
        return lang.getCode();
    }

    // ========== CONTEXT MANAGEMENT ==========

    /**
     * Apply language to context - IMPROVED VERSION with better error handling
     *
     * @param context The context to update
     * @param language The language to apply
     * @return Context with updated configuration
     */
    public static Context applyLanguageToContext(Context context, SupportedLanguage language) {
        try {
            Locale locale = new Locale(language.getCode());
            Configuration config = new Configuration(context.getResources().getConfiguration());

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                LocaleList localeList = new LocaleList(locale);
                config.setLocales(localeList);
                Locale.setDefault(locale);
                return context.createConfigurationContext(config);
            } else {
                config.locale = locale;
                Locale.setDefault(locale);
                context.getResources().updateConfiguration(config,
                        context.getResources().getDisplayMetrics());
                return context;
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to apply language to context: " + language.getDisplayName(), e);
            return context; // Return original context on failure
        }
    }

    /**
     * Initialize language on app start with enhanced detection
     *
     * @param context Application context
     */
    public static void initializeLanguage(Context context) {
        SupportedLanguage currentLanguage = getCurrentLanguage(context);
        setLanguage(context, currentLanguage);

        Log.i(TAG, "Language initialized: " + currentLanguage.getDisplayName() +
                " (" + currentLanguage.getCode() + ")");
        Log.d(TAG, "Fallback chain: " + getLanguageFallbackChain(context));
    }

    // ========== UI SUPPORT ==========

    /**
     * Get all supported languages for UI display
     *
     * @return Array of supported languages
     */
    public static SupportedLanguage[] getSupportedLanguages() {
        return SupportedLanguage.values();
    }

    /**
     * Check if language change requires app restart
     *
     * @param context Application context
     * @param newLanguage The new language to check
     * @return true if restart needed
     */
    public static boolean requiresRestart(Context context, SupportedLanguage newLanguage) {
        SupportedLanguage current = getCurrentLanguage(context);
        boolean needsRestart = !current.equals(newLanguage);

        if (needsRestart) {
            Log.d(TAG, "Language change requires restart: " +
                    current.getDisplayName() + " -> " + newLanguage.getDisplayName());
        }

        return needsRestart;
    }

    /**
     * Get language display name for UI
     *
     * @param languageCode Language code
     * @return Localized display name, or the code itself if not found
     */
    public static String getLanguageDisplayName(String languageCode) {
        SupportedLanguage lang = getSupportedLanguage(languageCode);
        return lang != null ? lang.getDisplayName() : languageCode;
    }

    // ========== DEBUGGING AND MONITORING ==========

    /**
     * Get comprehensive language status for debugging
     *
     * @param context Application context
     * @return Formatted string with language status information
     */
    public static String getLanguageStatus(Context context) {
        SupportedLanguage current = getCurrentLanguage(context);
        List<String> fallbackChain = getLanguageFallbackChain(context);
        Set<String> supportedCodes = getSupportedLanguageCodes();

        return String.format(
                "Language Status:\n" +
                        "  Current: %s (%s)\n" +
                        "  System: %s\n" +
                        "  Supported: %s\n" +
                        "  Fallback chain: %s",
                current.getDisplayName(), current.getCode(),
                Locale.getDefault().getLanguage(),
                supportedCodes,
                fallbackChain
        );
    }

    /**
     * Validate language configuration
     *
     * Checks if the current language setup is valid and logs any issues.
     *
     * @param context Application context
     * @return true if configuration is valid
     */
    public static boolean validateLanguageConfiguration(Context context) {
        try {
            SupportedLanguage current = getCurrentLanguage(context);
            List<String> fallbackChain = getLanguageFallbackChain(context);

            // Basic validation
            if (current == null) {
                Log.e(TAG, "Current language is null!");
                return false;
            }

            if (fallbackChain == null || fallbackChain.isEmpty()) {
                Log.e(TAG, "Fallback chain is empty!");
                return false;
            }

            if (!fallbackChain.contains(current.getCode())) {
                Log.e(TAG, "Current language not in fallback chain!");
                return false;
            }

            Log.d(TAG, "Language configuration validated successfully");
            return true;

        } catch (Exception e) {
            Log.e(TAG, "Language configuration validation failed", e);
            return false;
        }
    }
}