package li.masciul.sugardaddi.ui.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.navigation.NavigationView;

import java.util.ArrayList;
import java.util.List;

import li.masciul.sugardaddi.R;
import li.masciul.sugardaddi.data.database.AppDatabase;
import li.masciul.sugardaddi.data.sources.base.DataSource;
import li.masciul.sugardaddi.managers.DataSourceManager;
import li.masciul.sugardaddi.managers.LanguageManager;
import li.masciul.sugardaddi.managers.ThemeManager;
import li.masciul.sugardaddi.ui.settings.DataSourceCardManager;

/**
 * SettingsActivity — v4.0 (Settings refactor)
 *
 * ARCHITECTURE CHANGE v4.0
 * ========================
 * REMOVED: All Ciqual-specific fields, receivers, and methods.
 *   The former implementation had ~15 private fields (ciqualStatusDot,
 *   ciqualProgressBar, ciqualImportReceiver, …) and ~10 methods tightly
 *   coupled to a single data source. Adding USDA would have doubled that.
 *
 * REPLACED BY: DataSourceCardManager list.
 *   One DataSourceCardManager is created per registered DataSource
 *   (alphabetical order from DataSourceManager.getAllSources()). Each manager
 *   inflates item_datasource_card.xml, binds its own SettingsProvider, and
 *   owns its BroadcastReceiver lifecycle. SettingsActivity knows nothing about
 *   any specific source — it just calls onResume/onPause/onDestroy on each
 *   manager at the right moment.
 *
 * WHAT REMAINS UNCHANGED
 * ======================
 * - Navigation drawer + toolbar
 * - Language radio group (EN / FR)
 * - Theme radio group (System / Light / Dark)
 * - Cache clear button + logic
 * - BaseActivity lifecycle hooks
 */
public class SettingsActivity extends BaseActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    private static final String TAG = "SettingsActivity";

    // =========================================================================
    // NAVIGATION DRAWER
    // =========================================================================

    private DrawerLayout   drawerLayout;
    private NavigationView navigationView;
    private ActionBarDrawerToggle drawerToggle;

    // =========================================================================
    // PREFERENCES UI
    // =========================================================================

    private RadioGroup    languageRadioGroup;
    private RadioGroup    themeRadioGroup;
    private MaterialButton clearCacheButton;

    // =========================================================================
    // DATA SOURCE CARDS
    // =========================================================================

    /**
     * One manager per registered DataSource, in alphabetical order.
     * Created once in onCreate; their lifecycle hooks are called in
     * onActivityResumed / onPause / onDestroy.
     */
    private final List<DataSourceCardManager> cardManagers = new ArrayList<>();

    // =========================================================================
    // LIFECYCLE
    // =========================================================================

    @Override
    protected void onBaseActivityCreated(Bundle savedInstanceState) {
        setContentView(R.layout.activity_settings);

        setupToolbar();
        setupNavigationDrawer();
        initializeViews();
        loadCurrentSettings();
        setupListeners();
        setupDataSourceCards();

        logDebug("SettingsActivity v4.0 initialised");
    }

    /**
     * onResume equivalent for BaseActivity subclasses.
     * Called every time the activity comes to the foreground.
     */
    @Override
    protected void onActivityResumed() {
        super.onActivityResumed();
        loadCurrentSettings();

        // Resume each card manager — registers its BroadcastReceiver
        // and refreshes status dot / version chip from current prefs.
        for (DataSourceCardManager manager : cardManagers) {
            manager.onResume();
        }

        logDebug("SettingsActivity resumed — " + cardManagers.size() + " card(s) refreshed");
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Unregister all import receivers to prevent leaks
        for (DataSourceCardManager manager : cardManagers) {
            manager.onPause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Shut down each card's background executor
        for (DataSourceCardManager manager : cardManagers) {
            manager.onDestroy();
        }
        cardManagers.clear();
    }

    // =========================================================================
    // TOOLBAR
    // =========================================================================

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.menu_settings);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeButtonEnabled(true);
        }
    }

    // =========================================================================
    // NAVIGATION DRAWER
    // =========================================================================

    private void setupNavigationDrawer() {
        drawerLayout   = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);

        Toolbar toolbar = findViewById(R.id.toolbar);
        drawerToggle = new ActionBarDrawerToggle(
                this, drawerLayout, toolbar,
                R.string.navigation_drawer_open,
                R.string.navigation_drawer_close);
        drawerLayout.addDrawerListener(drawerToggle);
        drawerToggle.syncState();

        // White hamburger icon to match the primary-colour toolbar
        drawerToggle.getDrawerArrowDrawable()
                .setColor(getResources().getColor(android.R.color.white, null));

        navigationView.setNavigationItemSelectedListener(this);
        navigationView.setCheckedItem(R.id.nav_settings);

        logDebug("Navigation drawer initialised");
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if      (id == R.id.nav_journal)     startActivity(new Intent(this, JournalActivity.class));
        else if (id == R.id.nav_create_meal) startActivity(new Intent(this, CreateMealActivity.class));
        else if (id == R.id.nav_search)      startActivity(new Intent(this, MainActivity.class));
        else if (id == R.id.nav_favorites)   startActivity(new Intent(this, FavoritesActivity.class));
        // nav_settings: already here — just close drawer

        drawerLayout.closeDrawer(GravityCompat.START);
        return true;
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    // =========================================================================
    // PREFERENCES VIEWS
    // =========================================================================

    private void initializeViews() {
        languageRadioGroup = findViewById(R.id.languageRadioGroup);
        themeRadioGroup    = findViewById(R.id.themeRadioGroup);
        clearCacheButton   = findViewById(R.id.clearCacheButton);

        if (clearCacheButton != null) {
            clearCacheButton.setText(getSafeString(R.string.menu_clear_cache));
        }
    }

    private void loadCurrentSettings() {
        loadLanguageSettings();
        loadThemeSettings();
    }

    private void loadLanguageSettings() {
        LanguageManager.SupportedLanguage lang = getCurrentLanguage();
        if (lang == LanguageManager.SupportedLanguage.FRENCH) {
            languageRadioGroup.check(R.id.radioFrench);
        } else {
            languageRadioGroup.check(R.id.radioEnglish);
        }
        logDebug("Language loaded: " + lang.getDisplayName());
    }

    private void loadThemeSettings() {
        ThemeManager.Theme theme = getCurrentTheme();
        switch (theme) {
            case LIGHT:  themeRadioGroup.check(R.id.radioLightTheme);  break;
            case DARK:   themeRadioGroup.check(R.id.radioDarkTheme);   break;
            default:     themeRadioGroup.check(R.id.radioSystemTheme); break;
        }
        logDebug("Theme loaded: " + theme.getValue());
    }

    // =========================================================================
    // LISTENERS
    // =========================================================================

    private void setupListeners() {
        setupLanguageListener();
        setupThemeListener();
        setupCacheButton();
    }

    private void setupLanguageListener() {
        languageRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            LanguageManager.SupportedLanguage newLang =
                    (checkedId == R.id.radioFrench)
                            ? LanguageManager.SupportedLanguage.FRENCH
                            : LanguageManager.SupportedLanguage.ENGLISH;

            LanguageManager.SupportedLanguage cur = getCurrentLanguage();
            if (!cur.equals(newLang)) {
                logDebug("Language: " + cur.getDisplayName() + " → " + newLang.getDisplayName());
                changeLanguage(newLang);
            }
        });
    }

    private void setupThemeListener() {
        themeRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            ThemeManager.Theme newTheme;
            if      (checkedId == R.id.radioLightTheme) newTheme = ThemeManager.Theme.LIGHT;
            else if (checkedId == R.id.radioDarkTheme)  newTheme = ThemeManager.Theme.DARK;
            else                                         newTheme = ThemeManager.Theme.SYSTEM;

            ThemeManager.Theme cur = getCurrentTheme();
            if (!cur.equals(newTheme)) {
                logDebug("Theme: " + cur.getValue() + " → " + newTheme.getValue());
                changeTheme(newTheme);
            }
        });
    }

    private void setupCacheButton() {
        if (clearCacheButton != null) {
            clearCacheButton.setOnClickListener(v -> clearApplicationCache());
        }
    }

    // =========================================================================
    // CACHE MANAGEMENT
    // =========================================================================

    /**
     * Clears all food products and orphaned nutrition rows from Room.
     *
     * After clearing, each DataSourceCardManager.onResume() will call
     * SettingsProvider.isDatabaseReady() which now returns false, so the cards
     * automatically reflect the empty state on next resume.
     */
    private void clearApplicationCache() {
        logDebug("Cache clear initiated");
        clearCacheButton.setEnabled(false);
        clearCacheButton.setText(getSafeString(R.string.clearing_cache));

        new Thread(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(this);
                int count = db.foodProductDao().getProductCount();

                try {
                    db.foodProductDao().clearAllProducts();
                    db.nutritionDao().deleteOrphanedNutrition();
                } catch (Exception dbError) {
                    logError("DB clear error — falling back to clearAllTables", dbError);
                    db.clearAllTables();
                }

                // After clearing, each source's SettingsProvider.resetDatabaseState()
                // must be called so isDatabaseReady() returns false for local-DB sources.
                // We do this by calling it on every registered source.
                for (DataSourceCardManager manager : cardManagers) {
                    // resetDatabaseState is a no-op for sources without a local DB
                    // (OpenFoodFacts) and meaningful for Ciqual / USDA.
                    // DataSourceCardManager exposes a package-private resetState() for this.
                    manager.resetSourceDatabaseState(this);
                }

                final int cleared = count;
                runOnUiThread(() -> {
                    clearCacheButton.setEnabled(true);
                    clearCacheButton.setText(getSafeString(R.string.menu_clear_cache));
                    Toast.makeText(this,
                            getSafeString(R.string.cache_cleared_items, cleared),
                            Toast.LENGTH_LONG).show();
                    logDebug("Cache cleared: " + cleared + " products removed");

                    // Refresh all cards to show updated (empty) DB state
                    for (DataSourceCardManager mgr : cardManagers) {
                        mgr.refresh();
                    }
                });

            } catch (Exception e) {
                logError("Cache clear failed", e);
                runOnUiThread(() -> {
                    clearCacheButton.setEnabled(true);
                    clearCacheButton.setText(getSafeString(R.string.menu_clear_cache));
                    Toast.makeText(this,
                            getSafeString(R.string.cache_clear_failed),
                            Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    // =========================================================================
    // DATA SOURCE CARDS
    // =========================================================================

    /**
     * Creates one DataSourceCardManager per registered DataSource and inflates
     * its card into the dataSourcesContainer LinearLayout.
     *
     * Sources are returned alphabetically by DataSourceManager.getAllSources().
     * No source-specific code here — each card is driven by its SettingsProvider.
     */
    private void setupDataSourceCards() {
        LinearLayout container = findViewById(R.id.dataSourcesContainer);
        if (container == null) {
            logError("dataSourcesContainer not found in layout", null);
            return;
        }

        List<DataSource> sources = DataSourceManager.getInstance(this).getAllSources();

        for (DataSource source : sources) {
            DataSourceCardManager manager =
                    new DataSourceCardManager(source, getApplicationContext());
            manager.attach(container);
            cardManagers.add(manager);
            logDebug("Card created for: " + source.getSourceId());
        }

        logDebug("setupDataSourceCards complete — " + cardManagers.size() + " card(s)");
    }
}