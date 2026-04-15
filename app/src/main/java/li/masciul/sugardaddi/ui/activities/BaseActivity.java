package li.masciul.sugardaddi.ui.activities;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import li.masciul.sugardaddi.managers.LanguageManager;
import li.masciul.sugardaddi.managers.ThemeManager;
import li.masciul.sugardaddi.utils.category.CategoryColorHelper;

/**
 * BaseActivity - Foundation for all activities in SugarDaddi
 * Efficient and robust theme/language management
 *
 * Provides:
 * - Automatic language management
 * - Theme initialization and switching
 * - Common utility methods
 * - Consistent behavior across all activities
 */
public abstract class BaseActivity extends AppCompatActivity {

    private static final String TAG = "BaseActivity";
    private LanguageManager.SupportedLanguage creationLanguage;

    @Override
    protected void attachBaseContext(Context newBase) {
        // Apply current language before creating the activity
        LanguageManager.SupportedLanguage currentLanguage = LanguageManager.getCurrentLanguage(newBase);
        Context context = LanguageManager.applyLanguageToContext(newBase, currentLanguage);
        super.attachBaseContext(context);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Initialize theme before super.onCreate()
        ThemeManager.initializeTheme(this);
        super.onCreate(savedInstanceState);

        // Store creation language for later comparison
        creationLanguage = LanguageManager.getCurrentLanguage(this);

        // Call child implementation
        onBaseActivityCreated(savedInstanceState);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Check if language changed while activity was in background
        LanguageManager.SupportedLanguage currentLanguage = LanguageManager.getCurrentLanguage(this);
        if (creationLanguage != null && !creationLanguage.equals(currentLanguage)) {
            logDebug("Language changed while in background - recreating");
            recreate();
            return;
        }

        onActivityResumed();
    }

    /**
     * Handle configuration changes efficiently
     */
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        // Reapply language context if needed
        LanguageManager.SupportedLanguage currentLanguage = LanguageManager.getCurrentLanguage(this);
        if (creationLanguage != null && !creationLanguage.equals(currentLanguage)) {
            logDebug("Configuration changed - language mismatch detected");
            recreate();
        }
    }

    /**
     * Change language with proper activity stack management
     */
    protected void changeLanguage(LanguageManager.SupportedLanguage newLanguage) {
        LanguageManager.SupportedLanguage currentLang = getCurrentLanguage();

        if (!currentLang.equals(newLanguage)) {
            logDebug("Changing language: " + currentLang.getDisplayName() +
                    " -> " + newLanguage.getDisplayName());

            // Save the new language preference
            LanguageManager.setLanguage(this, newLanguage);

            // Recreate current activity
            recreate();
        }
    }

    /**
     * Change theme efficiently
     */
    protected void changeTheme(ThemeManager.Theme newTheme) {
        ThemeManager.Theme currentTheme = getCurrentTheme();
        if (!currentTheme.equals(newTheme)) {
            ThemeManager.setTheme(this, newTheme);
            // Theme changes are handled automatically by AppCompatDelegate
        }
    }

    // ========================
    // CHILD IMPLEMENTATION POINTS
    // ========================

    /**
     * Called after BaseActivity initialization - implement instead of onCreate
     */
    protected abstract void onBaseActivityCreated(Bundle savedInstanceState);

    /**
     * Called when activity resumes - override for custom behavior
     */
    protected void onActivityResumed() {
        // Override in child activities if needed
    }

    // ========================
    // LANGUAGE/THEME ACCESSORS
    // ========================

    protected LanguageManager.SupportedLanguage getCurrentLanguage() {
        return LanguageManager.getCurrentLanguage(this);
    }

    protected ThemeManager.Theme getCurrentTheme() {
        return ThemeManager.getCurrentTheme(this);
    }

    protected boolean isDarkModeActive() {
        return ThemeManager.isDarkModeActive(this);
    }

    // ========================
    // THEME-AWARE COLORS
    // ========================

    protected int getPrimaryColor() {
        return CategoryColorHelper.getButtonColor(this);
    }

    protected int getTextPrimaryColor() {
        return CategoryColorHelper.getTextPrimaryColor(this);
    }

    protected int getTextSecondaryColor() {
        return CategoryColorHelper.getTextSecondaryColor(this);
    }

    protected int getBackgroundColor() {
        return CategoryColorHelper.getBackgroundColor(this);
    }

    protected int getSurfaceColor() {
        return CategoryColorHelper.getSurfaceColor(this);
    }

    protected int getCardBackgroundColor() {
        return CategoryColorHelper.getCardBackgroundColor(this);
    }

    protected int getNutriScoreColor(String grade) {
        return CategoryColorHelper.getNutriScoreColor(this, grade);
    }

    protected int getNutriScoreBackgroundColor(String grade) {
        return CategoryColorHelper.getNutriScoreBackgroundColor(this, grade);
    }

    protected int getCategoryColor(String category) {
        return CategoryColorHelper.getCategoryColor(this, category);
    }

    protected int getStatusColor(String status) {
        return CategoryColorHelper.getStatusColor(this, status);
    }

    // ========================
    // UTILITIES
    // ========================

    protected String getSafeString(int stringRes) {
        try {
            return getString(stringRes);
        } catch (Exception e) {
            return "";
        }
    }

    protected String getSafeString(int stringRes, Object... formatArgs) {
        try {
            return getString(stringRes, formatArgs);
        } catch (Exception e) {
            return "";
        }
    }

    protected void logDebug(String message) {
        android.util.Log.d(TAG + ":" + getClass().getSimpleName(), message);
    }

    protected void logError(String message, Throwable throwable) {
        android.util.Log.e(TAG + ":" + getClass().getSimpleName(), message, throwable);
    }

    // ========================
    // NAVIGATION HELPERS
    // ========================

    protected void setupToolbarNavigation(androidx.appcompat.widget.Toolbar toolbar) {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        // Use OnBackPressedDispatcher to avoid deprecated onBackPressed() override
        toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
    }

    protected void setupToolbarNavigation(androidx.appcompat.widget.Toolbar toolbar, int titleRes) {
        setupToolbarNavigation(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(titleRes);
        }
    }

    protected void setupToolbarNavigation(androidx.appcompat.widget.Toolbar toolbar, String title) {
        setupToolbarNavigation(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(title);
        }
    }
}