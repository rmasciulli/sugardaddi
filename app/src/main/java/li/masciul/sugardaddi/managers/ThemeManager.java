package li.masciul.sugardaddi.managers;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatDelegate;

/**
 * ThemeManager - Handles app theme switching (Light/Dark/System)
 */
public class ThemeManager {

    private static final String PREFS_NAME = "theme_prefs";
    private static final String KEY_THEME = "selected_theme";

    /**
     * Available app themes
     */
    public enum Theme {
        LIGHT("light"),
        DARK("dark"),
        SYSTEM("system");

        private final String value;

        Theme(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public static Theme fromString(String value) {
            for (Theme theme : Theme.values()) {
                if (theme.value.equals(value)) {
                    return theme;
                }
            }
            return SYSTEM; // Default fallback
        }
    }

    /**
     * Get the currently selected theme
     */
    public static Theme getCurrentTheme(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String themeValue = prefs.getString(KEY_THEME, Theme.SYSTEM.getValue());
        return Theme.fromString(themeValue);
    }

    /**
     * Set the app theme and apply it immediately
     */
    public static void setTheme(Context context, Theme theme) {
        // Save preference
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_THEME, theme.getValue()).apply();

        // Apply theme immediately
        applyTheme(theme);
    }

    /**
     * Apply the theme using AppCompatDelegate
     */
    public static void applyTheme(Theme theme) {
        switch (theme) {
            case LIGHT:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case DARK:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            case SYSTEM:
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
        }
    }

    /**
     * Initialize theme on app startup - call this in Application.onCreate() or MainActivity.onCreate()
     */
    public static void initializeTheme(Context context) {
        Theme currentTheme = getCurrentTheme(context);
        applyTheme(currentTheme);
    }

    /**
     * Check if dark mode is currently active
     */
    public static boolean isDarkModeActive(Context context) {
        int currentNightMode = context.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }
}