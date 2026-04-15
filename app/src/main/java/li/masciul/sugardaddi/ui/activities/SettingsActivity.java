package li.masciul.sugardaddi.ui.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import li.masciul.sugardaddi.data.sources.ciqual.CiqualImportService;
import com.google.android.material.navigation.NavigationView;

import li.masciul.sugardaddi.R;
import li.masciul.sugardaddi.data.database.AppDatabase;
import li.masciul.sugardaddi.managers.LanguageManager;
import li.masciul.sugardaddi.managers.ThemeManager;

/**
 * SettingsActivity - Unified settings for Language and Theme (v3.0 - Unified Architecture)
 *
 * ✅ REFACTORED FOR UNIFIED ARCHITECTURE:
 * - Uses correct DAO methods (foodProductDao, getProductCount, clearAllProducts)
 * - Proper database cache clearing
 * - Enhanced error handling
 * - Clean separation of concerns
 * - Extends BaseActivity for consistent theme/language handling
 *
 * FEATURES:
 * - Language selection (English/French) with instant apply
 * - Theme selection (Light/Dark/System) with instant apply
 * - Database cache clearing with user feedback
 * - Proper lifecycle management
 *
 * @version 3.0
 * @since Unified Architecture refactor
 */
public class SettingsActivity extends BaseActivity implements
        NavigationView.OnNavigationItemSelectedListener {

    private static final String TAG = "SettingsActivity";

    // ========== NAVIGATION DRAWER ==========
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private ActionBarDrawerToggle drawerToggle;

    // ========== UI COMPONENTS ==========
    private RadioGroup languageRadioGroup;
    private RadioGroup themeRadioGroup;
    private MaterialButton clearCacheButton;
    private MaterialButton ciqualImportButton;
    private LinearProgressIndicator ciqualProgressBar;
    private android.widget.TextView ciqualStatusText;
    private android.widget.TextView ciqualVersionText;
    private android.widget.TextView ciqualProgressText;
    private android.view.View ciqualStatusDot;
    private android.widget.TextView ciqualIntegrityResult;
    private android.widget.TextView ciqualCheckIntegrityButton;
    private android.view.View ciqualProgressRow;
    private android.widget.TextView ciqualProgressPercent;
    private BroadcastReceiver ciqualImportReceiver;

    @Override
    protected void onBaseActivityCreated(Bundle savedInstanceState) {
        setContentView(R.layout.activity_settings);

        setupToolbar();
        setupNavigationDrawer();
        initializeViews();
        loadCurrentSettings();
        setupListeners();

        logDebug("SettingsActivity initialized successfully with unified architecture");
    }

    // ========== INITIALIZATION ==========

    /**
     * Setup toolbar with drawer toggle
     */
    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.menu_settings);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeButtonEnabled(true);
        }
    }

    // ========== NAVIGATION DRAWER SETUP ==========

    /**
     * Setup navigation drawer
     */
    private void setupNavigationDrawer() {
        // Initialize drawer components
        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);

        // Setup drawer toggle (hamburger icon)
        Toolbar toolbar = findViewById(R.id.toolbar);
        drawerToggle = new ActionBarDrawerToggle(
                this,
                drawerLayout,
                toolbar,
                R.string.navigation_drawer_open,
                R.string.navigation_drawer_close
        );
        drawerLayout.addDrawerListener(drawerToggle);
        drawerToggle.syncState();

        // CRITICAL FIX: Set drawer toggle icon color to white
        drawerToggle.getDrawerArrowDrawable().setColor(
                getResources().getColor(android.R.color.white, null)
        );

        // Set navigation listener
        navigationView.setNavigationItemSelectedListener(this);

        // Highlight current menu item
        navigationView.setCheckedItem(R.id.nav_settings);

        logDebug("Navigation drawer initialized");
    }

    /**
     * Handle navigation drawer item clicks
     */
    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        // Navigate based on selected item
        if (id == R.id.nav_journal) {
            logDebug("Navigate to Journal");
            startActivity(new Intent(this, JournalActivity.class));
        } else if (id == R.id.nav_create_meal) {
            logDebug("Navigate to Create Meal");
            startActivity(new Intent(this, CreateMealActivity.class));
        } else if (id == R.id.nav_search) {
            logDebug("Navigate to Search");
            startActivity(new Intent(this, MainActivity.class));
        } else if (id == R.id.nav_settings) {
            // Already in settings - just close drawer
            logDebug("Already in Settings");
        }

        // Close drawer after navigation
        drawerLayout.closeDrawer(GravityCompat.START);
        return true;
    }

    /**
     * Handle back button - close drawer first if open
     */
    @Override
    public void onBackPressed() {
        if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    /**
     * Initialize UI components
     */
    private void initializeViews() {
        languageRadioGroup = findViewById(R.id.languageRadioGroup);
        themeRadioGroup = findViewById(R.id.themeRadioGroup);
        clearCacheButton = findViewById(R.id.clearCacheButton);

        // Set button text using string resources
        if (clearCacheButton != null) {
            clearCacheButton.setText(getSafeString(R.string.menu_clear_cache));
        }
    }

    /**
     * Load current settings from managers
     */
    private void loadCurrentSettings() {
        loadLanguageSettings();
        loadThemeSettings();
    }

    /**
     * Load current language setting
     */
    private void loadLanguageSettings() {
        LanguageManager.SupportedLanguage currentLang = getCurrentLanguage();

        if (currentLang == LanguageManager.SupportedLanguage.FRENCH) {
            languageRadioGroup.check(R.id.radioFrench);
        } else {
            languageRadioGroup.check(R.id.radioEnglish);
        }

        logDebug("Current language loaded: " + currentLang.getDisplayName());
    }

    /**
     * Load current theme setting
     */
    private void loadThemeSettings() {
        ThemeManager.Theme currentTheme = getCurrentTheme();

        switch (currentTheme) {
            case LIGHT:
                themeRadioGroup.check(R.id.radioLightTheme);
                break;
            case DARK:
                themeRadioGroup.check(R.id.radioDarkTheme);
                break;
            case SYSTEM:
            default:
                themeRadioGroup.check(R.id.radioSystemTheme);
                break;
        }

        logDebug("Current theme loaded: " + currentTheme.getValue());
    }

    // ========== EVENT LISTENERS ==========

    /**
     * Setup event listeners for all controls
     */
    private void setupListeners() {
        setupLanguageListener();
        setupThemeListener();
        setupCacheButton();
        setupCiqualImportSection();
    }

    /**
     * Setup language change listener with BaseActivity integration
     */
    private void setupLanguageListener() {
        languageRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            LanguageManager.SupportedLanguage newLanguage;

            if (checkedId == R.id.radioFrench) {
                newLanguage = LanguageManager.SupportedLanguage.FRENCH;
            } else {
                newLanguage = LanguageManager.SupportedLanguage.ENGLISH;
            }

            LanguageManager.SupportedLanguage currentLang = getCurrentLanguage();
            if (!currentLang.equals(newLanguage)) {
                logDebug("Language changing from " + currentLang.getDisplayName() +
                        " to " + newLanguage.getDisplayName());

                // Use BaseActivity's efficient method
                changeLanguage(newLanguage);
            }
        });
    }

    /**
     * Setup theme change listener with BaseActivity integration
     */
    private void setupThemeListener() {
        themeRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            ThemeManager.Theme newTheme;

            if (checkedId == R.id.radioLightTheme) {
                newTheme = ThemeManager.Theme.LIGHT;
            } else if (checkedId == R.id.radioDarkTheme) {
                newTheme = ThemeManager.Theme.DARK;
            } else {
                newTheme = ThemeManager.Theme.SYSTEM;
            }

            // Only change if different - using BaseActivity method
            ThemeManager.Theme currentTheme = getCurrentTheme();
            if (!currentTheme.equals(newTheme)) {
                logDebug("Theme changing from " + currentTheme.getValue() +
                        " to " + newTheme.getValue());

                // Use BaseActivity's theme change method
                changeTheme(newTheme);
            }
        });
    }

    /**
     * Setup cache clearing functionality
     */
    private void setupCacheButton() {
        clearCacheButton.setOnClickListener(v -> clearApplicationCache());
    }

    // ========== CACHE MANAGEMENT ==========

    /**
     * Clear application cache with user feedback
     *
     * ✅ UPDATED: Uses correct DAO methods for unified architecture
     * - foodProductDao() instead of foodItemDao()
     * - getProductCount() instead of getItemCount()
     * - clearAllProducts() instead of deleteAll()
     * - Also clears nutrition orphans for complete cleanup
     */
    private void clearApplicationCache() {
        logDebug("Cache clearing initiated by user");

        // Show loading state
        clearCacheButton.setEnabled(false);
        clearCacheButton.setText(getSafeString(R.string.clearing_cache));

        // Perform cache clearing in background thread
        new Thread(() -> {
            try {
                // Get database instance
                AppDatabase database = AppDatabase.getInstance(this);

                int clearedItems = 0;

                try {
                    // Get count before clearing (for feedback)
                    clearedItems = database.foodProductDao().getProductCount();

                    // Clear all food products (non-favorites will be cleared)
                    // Note: If you want to keep favorites, use clearNonFavoriteCache() instead
                    database.foodProductDao().clearAllProducts();

                    // Clean up orphaned nutrition entries
                    database.nutritionDao().deleteOrphanedNutrition();

                    // Optionally clear all database tables (comprehensive clearing)
                    // database.clearAllTables();

                    logDebug("Cleared " + clearedItems + " products and orphaned nutrition data");

                } catch (Exception dbError) {
                    logError("Database clear operation encountered an error", dbError);

                    // Fallback: try comprehensive clearing
                    try {
                        database.clearAllTables();
                        logDebug("Performed comprehensive database clear (all tables)");
                    } catch (Exception fallbackError) {
                        logError("Fallback clear also failed", fallbackError);
                        throw fallbackError; // Will be caught by outer try-catch
                    }
                }

                // Update UI on main thread - SUCCESS
                final int finalClearedItems = clearedItems;
                runOnUiThread(() -> {
                    clearCacheButton.setEnabled(true);
                    clearCacheButton.setText(getSafeString(R.string.menu_clear_cache));

                    // Show success message
                    String message = getSafeString(R.string.cache_cleared_items, finalClearedItems);
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show();

                    logDebug("Cache cleared successfully: " + finalClearedItems + " items removed");

                    // Cache clear wipes Ciqual rows from food_products.
                    // Reset import prefs so isImported() returns false,
                    // then refresh the card to grey dot + not-ready state.
                    resetCiqualImportPrefs();
                    updateCiqualStatus();
                });

            } catch (Exception e) {
                logError("Failed to clear cache", e);

                // Update UI on main thread - ERROR
                runOnUiThread(() -> {
                    clearCacheButton.setEnabled(true);
                    clearCacheButton.setText(getSafeString(R.string.menu_clear_cache));

                    // Show error message
                    Toast.makeText(this,
                            getSafeString(R.string.cache_clear_failed),
                            Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    /**
     * Get cache statistics for display
     *
     * ✅ UPDATED: Uses correct DAO methods
     */
    private void showCacheStatistics() {
        new Thread(() -> {
            try {
                AppDatabase database = AppDatabase.getInstance(this);

                // Get statistics using correct DAO
                int productCount = database.foodProductDao().getProductCount();
                int favoriteCount = database.foodProductDao().getFavoriteCount();

                runOnUiThread(() -> {
                    String message = getSafeString(R.string.cache_stats, productCount, favoriteCount);
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show();

                    logDebug("Cache stats - Total: " + productCount + ", Favorites: " + favoriteCount);
                });

            } catch (Exception e) {
                logError("Failed to get cache statistics", e);

                runOnUiThread(() -> {
                    Toast.makeText(this,
                            getSafeString(R.string.cache_stats_failed),
                            Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    // ========== LIFECYCLE MANAGEMENT ==========

    /**
     * Handle activity resume - check for setting changes from other sources
     */
    @Override
    protected void onActivityResumed() {
        registerCiqualReceiver();
        updateCiqualStatus();
        super.onActivityResumed();
        loadCurrentSettings();
        logDebug("Settings reloaded on activity resume");
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterCiqualReceiver();
    }
    // ========================================================================
    // CIQUAL DATABASE IMPORT
    // ========================================================================

    private void setupCiqualImportSection() {
        ciqualImportButton  = findViewById(R.id.ciqualImportButton);
        ciqualProgressBar   = findViewById(R.id.ciqualProgressBar);
        ciqualProgressText  = findViewById(R.id.ciqualProgressText);
        ciqualStatusText    = findViewById(R.id.ciqualStatusText);
        ciqualVersionText   = findViewById(R.id.ciqualVersionText);
        ciqualStatusDot           = findViewById(R.id.ciqualStatusDot);
        ciqualIntegrityResult      = findViewById(R.id.ciqualIntegrityResult);
        ciqualCheckIntegrityButton = findViewById(R.id.ciqualCheckIntegrityButton);
        ciqualProgressRow          = findViewById(R.id.ciqualProgressRow);
        ciqualProgressPercent      = findViewById(R.id.ciqualProgressPercent);
        if (ciqualImportButton == null) return;
        ciqualImportButton.setOnClickListener(v -> startCiqualImport());
        if (ciqualCheckIntegrityButton != null)
            ciqualCheckIntegrityButton.setOnClickListener(v -> runIntegrityCheck());
        updateCiqualStatus();
    }

    private void updateCiqualStatus() {
        if (ciqualStatusText == null) return;
        boolean ready  = CiqualImportService.isImported(this);
        boolean stale  = CiqualImportService.needsUpdate(this) && !ready;
        String version = CiqualImportService.getImportedVersion(this);

        if (ready) {
            if (ciqualStatusDot != null) ciqualStatusDot.setBackgroundColor(0xFF4CAF50); // green
            ciqualStatusText.setText(getSafeString(R.string.ciqual_status_ready));
            if (ciqualVersionText != null && version != null) {
                ciqualVersionText.setText("Ciqual " + version.replace("_", "-") + " · 3 484 aliments");
                ciqualVersionText.setVisibility(android.view.View.VISIBLE);
            }
        } else if (stale) {
            if (ciqualStatusDot != null) ciqualStatusDot.setBackgroundColor(0xFFFF9800); // orange
            ciqualStatusText.setText(getSafeString(R.string.ciqual_status_update_available));
            if (ciqualVersionText != null && version != null) {
                ciqualVersionText.setText("v" + version.replace("_", "-") + " installée");
                ciqualVersionText.setVisibility(android.view.View.VISIBLE);
            }
        } else {
            if (ciqualStatusDot != null) ciqualStatusDot.setBackgroundColor(0xFF9E9E9E); // grey
            ciqualStatusText.setText(getSafeString(R.string.ciqual_status_not_ready));
            if (ciqualVersionText != null) ciqualVersionText.setVisibility(android.view.View.GONE);
        }

        if (ciqualProgressRow != null)  ciqualProgressRow.setVisibility(android.view.View.GONE);
        if (ciqualProgressText != null)  ciqualProgressText.setVisibility(android.view.View.GONE);
        if (ciqualImportButton != null)  ciqualImportButton.setEnabled(true);
    }

    private void startCiqualImport() {
        // Files are bundled in assets/ — instant, no download needed
        if (ciqualImportButton != null)  ciqualImportButton.setEnabled(false);
        if (ciqualProgressRow != null) {
            ciqualProgressRow.setVisibility(android.view.View.VISIBLE);
        }
        if (ciqualProgressBar != null) {
            ciqualProgressBar.setIndeterminate(false);
            ciqualProgressBar.setProgress(0);
        }
        if (ciqualProgressPercent != null) {
            ciqualProgressPercent.setText("0%");
        }
        if (ciqualProgressText != null) {
            ciqualProgressText.setText(getSafeString(R.string.ciqual_importing));
            ciqualProgressText.setVisibility(android.view.View.VISIBLE);
        }
        startForegroundService(new Intent(this, CiqualImportService.class));
    }

    private void registerCiqualReceiver() {
        if (ciqualImportReceiver != null) return;
        ciqualImportReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(android.content.Context ctx, Intent intent) {
                if (intent == null || intent.getAction() == null) return;
                switch (intent.getAction()) {
                    case CiqualImportService.BROADCAST_PROGRESS:
                        onImportProgress(
                                intent.getStringExtra(CiqualImportService.EXTRA_PHASE),
                                intent.getIntExtra(CiqualImportService.EXTRA_PROGRESS_PCT, 0));
                        break;
                    case CiqualImportService.BROADCAST_COMPLETE:
                        onImportComplete();
                        break;
                    case CiqualImportService.BROADCAST_ERROR:
                        onImportError(intent.getStringExtra(CiqualImportService.EXTRA_ERROR_MSG));
                        break;
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(CiqualImportService.BROADCAST_PROGRESS);
        filter.addAction(CiqualImportService.BROADCAST_COMPLETE);
        filter.addAction(CiqualImportService.BROADCAST_ERROR);
        // RECEIVER_NOT_EXPORTED: broadcasts come only from our own process
        ContextCompat.registerReceiver(this, ciqualImportReceiver, filter,
                ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    private void unregisterCiqualReceiver() {
        if (ciqualImportReceiver != null) {
            try { unregisterReceiver(ciqualImportReceiver); } catch (IllegalArgumentException ignored) {}
            ciqualImportReceiver = null;
        }
    }

    private void onImportProgress(@androidx.annotation.Nullable String phase, int pct) {
        if (ciqualProgressRow != null)
            ciqualProgressRow.setVisibility(android.view.View.VISIBLE);
        if (ciqualProgressBar != null) {
            // Always determinate — user can see real progress from 0%
            ciqualProgressBar.setIndeterminate(false);
            int safePct = Math.max(0, Math.min(100, pct));
            ciqualProgressBar.setProgress(safePct, true);
        }
        if (ciqualProgressPercent != null) {
            int safePct = Math.max(0, Math.min(100, pct));
            ciqualProgressPercent.setText(safePct + "%");
        }
        if (ciqualProgressText != null && phase != null) {
            ciqualProgressText.setText(phase);
            ciqualProgressText.setVisibility(android.view.View.VISIBLE);
        }
    }

    private void onImportComplete() {
        if (ciqualProgressRow != null) ciqualProgressRow.setVisibility(android.view.View.GONE);
        if (ciqualProgressText != null) ciqualProgressText.setVisibility(android.view.View.GONE);
        if (ciqualImportButton != null) ciqualImportButton.setEnabled(true);
        updateCiqualStatus();
        android.widget.Toast.makeText(this,
                getSafeString(R.string.ciqual_import_success),
                android.widget.Toast.LENGTH_LONG).show();
    }

    private void onImportError(@androidx.annotation.Nullable String message) {
        if (ciqualProgressRow != null) ciqualProgressRow.setVisibility(android.view.View.GONE);
        if (ciqualProgressText != null) ciqualProgressText.setVisibility(android.view.View.GONE);
        if (ciqualImportButton != null) ciqualImportButton.setEnabled(true);
        if (ciqualStatusText != null) ciqualStatusText.setText(getSafeString(R.string.ciqual_status_not_ready));
        android.widget.Toast.makeText(this,
                getSafeString(R.string.ciqual_import_error) + (message != null ? ": " + message : ""),
                android.widget.Toast.LENGTH_LONG).show();
    }


    // ─────────────────────────────────────────────────────────────────────
    // CIQUAL PREFS RESET
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Clears CiqualImportService SharedPreferences so that isImported() returns
     * false. Call this whenever the DB is found empty (integrity check or cache
     * clear) so the card state stays consistent with reality.
     */
    private void resetCiqualImportPrefs() {
        getSharedPreferences(
                li.masciul.sugardaddi.data.sources.ciqual.CiqualImportService.PREFS_NAME,
                MODE_PRIVATE)
                .edit().clear().apply();
    }

    // ─────────────────────────────────────────────────────────────────────
    // CIQUAL INTEGRITY CHECK
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Queries Room for CIQUAL product and nutrition counts and displays them
     * inline next to the check button. Runs on a background thread.
     *
     * Expected: CiqualConstants.EXPECTED_FOOD_COUNT products (3484) and
     * an equal number of nutrition records.
     * The check works even after a cache wipe — it reads the live DB state.
     */
    private void runIntegrityCheck() {
        if (ciqualCheckIntegrityButton != null) ciqualCheckIntegrityButton.setEnabled(false);
        if (ciqualIntegrityResult != null) {
            ciqualIntegrityResult.setText(getSafeString(R.string.ciqual_integrity_checking));
            ciqualIntegrityResult.setVisibility(android.view.View.VISIBLE);
        }

        new Thread(() -> {
            try {
                // Wait briefly if DB was just cleared — give Room time to settle
                Thread.sleep(200);

                li.masciul.sugardaddi.data.database.AppDatabase db =
                        li.masciul.sugardaddi.data.database.AppDatabase.getInstance(getApplicationContext());

                // Product count
                int productCount = db.combinedProductDao()
                        .getCountBySource(li.masciul.sugardaddi.data.sources.ciqual.CiqualConstants.SOURCE_ID);

                // Nutrition count — filter from grouped results
                int nutritionCount = 0;
                java.util.List<li.masciul.sugardaddi.data.database.dao.NutritionDao.DataSourceCount> nutritionCounts =
                        db.nutritionDao().getNutritionCountBySource();
                for (li.masciul.sugardaddi.data.database.dao.NutritionDao.DataSourceCount row : nutritionCounts) {
                    if (li.masciul.sugardaddi.data.sources.ciqual.CiqualConstants.SOURCE_ID
                            .equals(row.dataSource)) {
                        nutritionCount = row.count;
                        break;
                    }
                }

                final int finalProducts  = productCount;
                final int finalNutrition = nutritionCount;
                final int expected       = 3484;

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return; // activity gone
                    if (ciqualCheckIntegrityButton != null) ciqualCheckIntegrityButton.setEnabled(true);
                    if (ciqualIntegrityResult == null) return;

                    String verdict;
                    int    colour;
                    if (finalProducts == 0) {
                        verdict = getSafeString(R.string.ciqual_integrity_empty);
                        colour  = 0xFFE53935; // red
                        // DB is empty — invalidate the import prefs so the card
                        // reflects reality (isImported() will now return false)
                        resetCiqualImportPrefs();
                        updateCiqualStatus();
                    } else if (finalProducts < expected) {
                        verdict = finalProducts + " / " + expected + " "
                                + getSafeString(R.string.ciqual_integrity_products)
                                + " · " + finalNutrition + " "
                                + getSafeString(R.string.ciqual_integrity_nutrition_records);
                        colour  = 0xFFFF9800; // orange — partial
                    } else {
                        verdict = finalProducts + " "
                                + getSafeString(R.string.ciqual_integrity_products)
                                + " · " + finalNutrition + " "
                                + getSafeString(R.string.ciqual_integrity_nutrition_records);
                        colour  = 0xFF4CAF50; // green — complete
                    }

                    ciqualIntegrityResult.setText(verdict);
                    ciqualIntegrityResult.setTextColor(colour);
                    ciqualIntegrityResult.setVisibility(android.view.View.VISIBLE);
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    if (ciqualCheckIntegrityButton != null) ciqualCheckIntegrityButton.setEnabled(true);
                    if (ciqualIntegrityResult != null) {
                        ciqualIntegrityResult.setText(getSafeString(R.string.ciqual_integrity_error));
                        ciqualIntegrityResult.setTextColor(0xFFE53935);
                        ciqualIntegrityResult.setVisibility(android.view.View.VISIBLE);
                    }
                });
            }
        }).start();
    }

}