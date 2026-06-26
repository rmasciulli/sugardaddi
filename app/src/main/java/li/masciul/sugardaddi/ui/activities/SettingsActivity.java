package li.masciul.sugardaddi.ui.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.navigation.NavigationView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import li.masciul.sugardaddi.R;
import li.masciul.sugardaddi.SugarDaddiApplication;
import li.masciul.sugardaddi.data.database.AppDatabase;
import li.masciul.sugardaddi.data.repository.CacheRepository;
import li.masciul.sugardaddi.data.sources.base.DataSource;
import li.masciul.sugardaddi.data.sources.base.settings.SettingsProvider;
import li.masciul.sugardaddi.managers.DataSourceManager;
import li.masciul.sugardaddi.managers.LanguageManager;
import li.masciul.sugardaddi.managers.ThemeManager;
import li.masciul.sugardaddi.ui.settings.DataSourceCardManager;
import li.masciul.sugardaddi.utils.image.ImagePurgeManager;
import li.masciul.sugardaddi.utils.image.ImageStorageManager;

/**
 * SettingsActivity - v4.0 (Settings refactor)
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
 *   any specific source - it just calls onResume/onPause/onDestroy on each
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
    private boolean isLoadingSettings = false;

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

    // =========================================================================
    // CACHE CARD
    // =========================================================================

    // Section 1 - searched items (no retention pin)
    private TextView       cacheBrowsedText;
    private TextView       cacheBrowsedProductCount;
    private TextView       cacheBrowsedRecipeCount;
    private MaterialButton clearBrowsedButton;

    // Section 2 - favourites
    private TextView       cacheFavoritesText;
    private TextView       cacheFavoriteProductCount;
    private TextView       cacheFavoriteRecipeCount;
    private MaterialButton clearFavoritesButton;

    // Section 3 - downloaded data sources (rows inflated dynamically)
    private TextView       cacheDownloadedText;
    private LinearLayout   downloadedSourcesContainer;
    private TextView       downloadedSourcesEmpty;

    // Clear all
    private MaterialButton clearAllButton;

    private CacheRepository cacheRepository;

    // =========================================================================
    // IMAGE LIBRARY CARD
    // =========================================================================

    // View references
    private MaterialButton purgeImagesButton;
    private TextView       purgeStatusText;
    private MaterialSwitch galleryVisibilitySwitch;
    private MaterialSwitch thumbnailVisibilitySwitch;

    // SharedPreferences keys
    private static final String PREF_KEY_GALLERY_VISIBLE    = "image_gallery_visible";
    private static final String PREF_KEY_THUMBNAIL_VISIBLE  = "image_thumbnail_visible";

    /**
     * Stored so onResume() can refresh storage counts when the user navigates
     * back to Settings (e.g. after favouriting an item).
     */
    private Runnable refreshStorageRunnable;

    /**
     * Dedicated background executor for file listing, MediaScanner calls,
     * and synchronous purge. Must not run these on the main thread.
     */
    private final ExecutorService imageSettingsExecutor =
            Executors.newSingleThreadExecutor();

    // =========================================================================
    // LIFECYCLE
    // =========================================================================

    @Override
    protected void onBaseActivityCreated(Bundle savedInstanceState) {
        setContentView(R.layout.activity_settings);

        setupToolbar();
        setupNavigationDrawer();
        initializeViews();
        setupListeners();
        loadCurrentSettings();
        setupCacheCard();
        setupImageLibraryCard();

        logDebug("SettingsActivity v4.0 initialised");
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        // Do not restore radio group state from bundle - we always load
        // fresh from SharedPreferences in loadCurrentSettings() to avoid
        // triggering theme/language change listeners with stale state.
    }

    /**
     * onResume equivalent for BaseActivity subclasses.
     * Called every time the activity comes to the foreground.
     */
    @Override
    protected void onActivityResumed() {
        super.onActivityResumed();
        loadCurrentSettings();

        if (navigationView != null) {
            MenuItem settingsItem = navigationView.getMenu().findItem(R.id.nav_settings);
            if (settingsItem != null) {
                settingsItem.setChecked(true);
            }
        }

        refreshCacheCounts();

        // Refresh storage summary so counts stay accurate when adding items
        // as favorites in other screens before navigating back to Settings.
        if (refreshStorageRunnable != null) refreshStorageRunnable.run();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Shut down the image library card's background executor
        imageSettingsExecutor.shutdown();
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
                .setColor(ContextCompat.getColor(this, R.color.white));

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
        else if (id == R.id.nav_data_sources) startActivity(new Intent(this, DataSourcesActivity.class));
        // nav_settings: already here - just close drawer

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
    }

    private void loadCurrentSettings() {
        loadLanguageSettings();
        loadThemeSettings();
    }

    private void loadLanguageSettings() {
        isLoadingSettings = true;
        LanguageManager.SupportedLanguage lang = getCurrentLanguage();
        if (lang == LanguageManager.SupportedLanguage.FRENCH) {
            languageRadioGroup.check(R.id.radioFrench);
        } else {
            languageRadioGroup.check(R.id.radioEnglish);
        }
        isLoadingSettings = false;
    }

    private void loadThemeSettings() {
        isLoadingSettings = true;
        ThemeManager.Theme theme = getCurrentTheme();
        switch (theme) {
            case LIGHT:  themeRadioGroup.check(R.id.radioLightTheme);  break;
            case DARK:   themeRadioGroup.check(R.id.radioDarkTheme);   break;
            default:     themeRadioGroup.check(R.id.radioSystemTheme); break;
        }
        isLoadingSettings = false;
    }

    // =========================================================================
    // LISTENERS
    // =========================================================================

    private void setupListeners() {
        setupLanguageListener();
        setupThemeListener();
    }

    private void setupLanguageListener() {
        languageRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (isLoadingSettings) return;
            LanguageManager.SupportedLanguage newLang = (checkedId == R.id.radioFrench)
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
            if (isLoadingSettings) return;
            ThemeManager.Theme newTheme;
            if      (checkedId == R.id.radioLightTheme)  newTheme = ThemeManager.Theme.LIGHT;
            else if (checkedId == R.id.radioDarkTheme)   newTheme = ThemeManager.Theme.DARK;
            else                                         newTheme = ThemeManager.Theme.SYSTEM;
            ThemeManager.Theme cur = getCurrentTheme();
            if (!cur.equals(newTheme)) {
                logDebug("Theme: " + cur.getValue() + " → " + newTheme.getValue());
                changeTheme(newTheme);
            }
        });
    }

    // =========================================================================
    // CACHE CARD
    // =========================================================================

    /**
     * Binds the three-section cache card and wires each bin button to a
     * confirmation dialog. Section 3 (downloaded sources) is built dynamically
     * in {@link #refreshCacheCounts()}. Keeps the setupCacheCard() call site.
     */
    private void setupCacheCard() {
        cacheRepository = new CacheRepository(this);

        cacheBrowsedProductCount   = findViewById(R.id.cacheBrowsedProductCount);
        cacheBrowsedRecipeCount    = findViewById(R.id.cacheBrowsedRecipeCount);
        clearBrowsedButton         = findViewById(R.id.clearBrowsedButton);
        cacheFavoriteProductCount  = findViewById(R.id.cacheFavoriteProductCount);
        cacheFavoriteRecipeCount   = findViewById(R.id.cacheFavoriteRecipeCount);
        clearFavoritesButton       = findViewById(R.id.clearFavoritesButton);
        downloadedSourcesContainer = findViewById(R.id.downloadedSourcesContainer);
        downloadedSourcesEmpty     = findViewById(R.id.downloadedSourcesEmpty);
        clearAllButton             = findViewById(R.id.clearAllButton);
        cacheBrowsedText           = findViewById(R.id.cacheBrowsedText);
        cacheFavoritesText         = findViewById(R.id.cacheFavoritesText);
        cacheDownloadedText        = findViewById(R.id.cacheDownloadedText);

        if (clearBrowsedButton != null) {
            clearBrowsedButton.setOnClickListener(v ->
                    confirmAndClear(R.string.cache_browsed_action, this::clearBrowsed));
        }
        if (clearFavoritesButton != null) {
            clearFavoritesButton.setOnClickListener(v ->
                    confirmAndClear(R.string.cache_favorites_action, this::clearFavorites));
        }
        if (clearAllButton != null) {
            clearAllButton.setOnClickListener(v ->
                    confirmAndClear(R.string.cache_clear_all_action, this::clearAll));
        }

        // Static section text: description + what deleting it does.
        if (cacheBrowsedText != null) cacheBrowsedText.setText(
                sectionText(R.string.cache_browsed_desc, R.string.cache_browsed_action));
        if (cacheFavoritesText != null) cacheFavoritesText.setText(
                sectionText(R.string.cache_favorites_desc, R.string.cache_favorites_action));
        if (cacheDownloadedText != null) cacheDownloadedText.setText(
                sectionText(R.string.cache_downloaded_desc, R.string.cache_downloaded_note));

        refreshCacheCounts();
    }

    /**
     * Confirmation dialog for the fixed sections (browsed / favourites / clear-all).
     * The per-source section uses {@link #confirmRemoveSource(DataSource)} because its
     * message needs the source name formatted in.
     *
     * @param messageRes Body text describing exactly what will be removed
     * @param onConfirm  Runnable executed only if the user confirms
     */
    private void confirmAndClear(@StringRes int messageRes, Runnable onConfirm) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.cache_confirm_title)
                .setMessage(messageRes)
                .setPositiveButton(R.string.cache_confirm_remove, (d, w) -> onConfirm.run())
                .setNegativeButton(R.string.cache_confirm_cancel, null)
                .show();
    }

    /**
     * Reads all per-type counts off the main thread and rebuilds the section-3
     * source rows, then applies everything to the UI. Called on every resume so the
     * card stays accurate after favouriting/importing elsewhere.
     */
    private void refreshCacheCounts() {
        imageSettingsExecutor.execute(() -> {
            try {
                int browsedProducts  = cacheRepository.getBrowsedProductCount();
                int browsedRecipes   = cacheRepository.getBrowsedRecipeCount();
                int favoriteProducts = cacheRepository.getFavoriteProductCount();
                int favoriteRecipes  = cacheRepository.getFavoriteRecipeCount();

                // A source shows in section 3 only if it has a local database and
                // currently holds downloaded rows (count-based, so no isImported guess).
                List<SourceRow> sourceRows = new ArrayList<>();
                List<DataSource> sources =
                        DataSourceManager.getInstance(this).getAllSources();
                for (DataSource source : sources) {
                    SettingsProvider provider = source.getSettingsProvider();
                    if (provider == null || !provider.hasLocalDatabase()) continue;

                    String sourceId = source.getSourceId();
                    int products = cacheRepository.getDownloadProductCount(sourceId);
                    int recipes  = cacheRepository.getDownloadRecipeCount(sourceId);
                    if (products > 0 || recipes > 0) {
                        sourceRows.add(new SourceRow(source, products, recipes));
                    }
                }

                runOnUiThread(() -> {
                    setCount(cacheBrowsedProductCount,  R.string.cache_products_count, browsedProducts);
                    setCount(cacheBrowsedRecipeCount,   R.string.cache_recipes_count,  browsedRecipes);
                    setCount(cacheFavoriteProductCount, R.string.cache_products_count, favoriteProducts);
                    setCount(cacheFavoriteRecipeCount,  R.string.cache_recipes_count,  favoriteRecipes);
                    renderDownloadedSources(sourceRows);
                });
            } catch (Exception e) {
                logError("refreshCacheCounts failed", e);
            }
        });
    }

    /** Formats "Products: N" / "Recipes: N" into a count TextView (null-safe). */
    private void setCount(TextView view, @StringRes int formatRes, int count) {
        if (view != null) view.setText(getSafeString(formatRes, count));
    }


    /**
     * Joins a section's description and its delete-consequence into one text block
     * (blank line between). The action half stays a separate string so the confirm
     * dialog can reuse it verbatim.
     */
    private String sectionText(@StringRes int descRes, @StringRes int actionRes) {
        return getSafeString(descRes) + " " + getSafeString(actionRes);
    }

    /**
     * Rebuilds the dynamic section-3 list: one inflated item_cache_source row per
     * downloaded source, or the "no sources" placeholder when the list is empty.
     */
    private void renderDownloadedSources(List<SourceRow> rows) {
        if (downloadedSourcesContainer == null) return;
        downloadedSourcesContainer.removeAllViews();

        if (rows.isEmpty()) {
            if (downloadedSourcesEmpty != null) downloadedSourcesEmpty.setVisibility(View.VISIBLE);
            return;
        }
        if (downloadedSourcesEmpty != null) downloadedSourcesEmpty.setVisibility(View.GONE);

        LayoutInflater inflater = LayoutInflater.from(this);
        for (SourceRow row : rows) {
            View item = inflater.inflate(
                    R.layout.item_cache_source, downloadedSourcesContainer, false);

            TextView       name     = item.findViewById(R.id.cacheSourceName);
            TextView       products = item.findViewById(R.id.cacheSourceProductCount);
            TextView       recipes  = item.findViewById(R.id.cacheSourceRecipeCount);
            MaterialButton bin      = item.findViewById(R.id.clearSourceButton);

            name.setText(row.source.getSourceName());
            setCount(products, R.string.cache_products_count, row.products);
            setCount(recipes,  R.string.cache_recipes_count,  row.recipes);
            bin.setContentDescription(
                    getSafeString(R.string.cache_delete_source_cd, row.source.getSourceName()));
            bin.setOnClickListener(v -> confirmRemoveSource(row.source));

            downloadedSourcesContainer.addView(item);
        }
    }

    /** Per-source confirmation dialog (message needs the source name formatted in). */
    private void confirmRemoveSource(DataSource source) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.cache_confirm_title)
                .setMessage(getSafeString(R.string.cache_downloaded_action, source.getSourceName()))
                .setPositiveButton(R.string.cache_confirm_remove,
                        (d, w) -> removeDownloadedSource(source))
                .setNegativeButton(R.string.cache_confirm_cancel, null)
                .show();
    }

    // ---- clear handlers (Room op on executor, then image purge + UI refresh) ----

    /** Section 1 - delete searched items (no pin); favourites/downloads untouched. */
    private void clearBrowsed() {
        runCacheOp(() -> cacheRepository.clearBrowsedCache(), false);
    }

    /** Section 2 - delete favourites (downloaded favourites just lose the pin). */
    private void clearFavorites() {
        runCacheOp(() -> cacheRepository.clearFavorites(), false);
    }

    /**
     * Section 3 - remove one source's downloaded dataset, then reset that source's
     * import state so its data-source card re-enables download.
     */
    private void removeDownloadedSource(DataSource source) {
        runCacheOp(() -> {
            cacheRepository.removeDownloadedSource(source.getSourceId());
            SettingsProvider provider = source.getSettingsProvider();
            if (provider != null) provider.resetDatabaseState(this);
        }, false);
    }

    /** Clear all - wipe every product and recipe, then reset every local source. */
    private void clearAll() {
        runCacheOp(() -> {
            cacheRepository.clearAll();
            List<DataSource> sources = DataSourceManager.getInstance(this).getAllSources();
            for (DataSource source : sources) {
                SettingsProvider provider = source.getSettingsProvider();
                if (provider != null) provider.resetDatabaseState(this);
            }
        }, true);
    }

    /**
     * Runs a Room cache operation on the background executor, then purges orphaned
     * image files (or all of them on a full clear), then refreshes the card.
     *
     * @param roomOp    The synchronous CacheRepository call(s) to run
     * @param fullPurge true for "clear all" (purge every image), false otherwise
     */
    private void runCacheOp(Runnable roomOp, boolean fullPurge) {
        imageSettingsExecutor.execute(() -> {
            try {
                roomOp.run();

                ImagePurgeManager purgeManager =
                        ((SugarDaddiApplication) getApplication()).getImagePurgeManager();
                if (purgeManager != null) {
                    if (fullPurge) purgeManager.purgeAllAsync();
                    else           purgeManager.purgeOrphansAsync();
                }

                runOnUiThread(() -> {
                    Toast.makeText(this, getSafeString(R.string.cache_cleared),
                            Toast.LENGTH_SHORT).show();
                    refreshCacheCounts();
                });
            } catch (Exception e) {
                logError("cache operation failed", e);
            }
        });
    }

    /** Lightweight holder for one downloaded-source row while building section 3. */
    private static final class SourceRow {
        final DataSource source;
        final int products;
        final int recipes;
        SourceRow(DataSource source, int products, int recipes) {
            this.source = source;
            this.products = products;
            this.recipes = recipes;
        }
    }

    // =========================================================================
    // IMAGE LIBRARY CARD
    // =========================================================================

    /**
     * Wires all views in the image library settings card.
     *
     * STORAGE SUMMARY
     * ================
     * Loads immediately on open and on every onResume() so counts stay accurate
     * after favouriting items. Refresh button re-runs the same calculation.
     * Calculation runs on imageSettingsExecutor (file I/O, not main thread).
     *
     * GALLERY VISIBILITY (products/, recipes/, meals/, steps/)
     * =========================================================
     * Persisted preference. On toggle: retroactively scans or unscans all
     * existing files in those four directories via MediaScannerConnection /
     * ContentResolver. thumbnails/ is handled by the separate toggle below.
     *
     * THUMBNAIL VISIBILITY (thumbnails/)
     * ====================================
     * Same mechanism, applied only to the thumbnails/ directory.
     * Thumbnails are auto-managed small images; exposing them in the gallery
     * is optional and controlled separately from user-created photos.
     *
     * PURGE BUTTON
     * =============
     * Calls ImagePurgeManager.purgeOrphansSync() on imageSettingsExecutor.
     * Shows deleted count + bytes freed in purgeStatusText.
     * Button is disabled while purge runs to prevent re-entry.
     */
    private void setupImageLibraryCard() {
        // ── Bind views ────────────────────────────────────────────────────────
        purgeImagesButton        = findViewById(R.id.purgeImagesButton);
        purgeStatusText          = findViewById(R.id.purgeStatusText);
        galleryVisibilitySwitch  = findViewById(R.id.galleryVisibilitySwitch);
        thumbnailVisibilitySwitch = findViewById(R.id.thumbnailVisibilitySwitch);

        MaterialButton refreshStorageButton = findViewById(R.id.refreshStorageButton);

        TextView storageCountThumbnails = findViewById(R.id.storageCountThumbnails);
        TextView storageSizeThumbnails  = findViewById(R.id.storageSizeThumbnails);
        TextView storageCountProducts   = findViewById(R.id.storageCountProducts);
        TextView storageSizeProducts    = findViewById(R.id.storageSizeProducts);
        TextView storageCountRecipes    = findViewById(R.id.storageCountRecipes);
        TextView storageSizeRecipes     = findViewById(R.id.storageSizeRecipes);
        TextView storageCountMeals      = findViewById(R.id.storageCountMeals);
        TextView storageSizeMeals       = findViewById(R.id.storageSizeMeals);
        TextView storageCountSteps      = findViewById(R.id.storageCountSteps);
        TextView storageSizeSteps       = findViewById(R.id.storageSizeSteps);
        TextView storageCountTotal      = findViewById(R.id.storageCountTotal);
        TextView storageSizeTotal       = findViewById(R.id.storageSizeTotal);

        if (purgeImagesButton == null || galleryVisibilitySwitch == null
                || thumbnailVisibilitySwitch == null || refreshStorageButton == null) {
            Log.w(TAG, "Image library card views not found - skipping setup");
            return;
        }

        ImageStorageManager storageManager =
                ((SugarDaddiApplication) getApplication()).getImageStorageManager();

        SharedPreferences prefs = getPreferences(MODE_PRIVATE);

        // ── Storage summary ───────────────────────────────────────────────────
        refreshStorageRunnable = () -> {
            imageSettingsExecutor.execute(() -> {
                // Five directories in display order
                File[] dirs = {
                        storageManager.getThumbnailsDir(),
                        storageManager.getProductsDir(),
                        storageManager.getRecipesDir(),
                        storageManager.getMealsDir(),
                        storageManager.getStepsDir()
                };
                String[] labels = { "Thumbnails", "Products", "Recipes", "Meals", "Steps" };

                long[] counts = new long[5];
                long[] sizes  = new long[5];
                long totalCount = 0, totalSize = 0;

                for (int i = 0; i < dirs.length; i++) {
                    if (dirs[i] == null || !dirs[i].exists()) continue;
                    File[] files = dirs[i].listFiles();
                    if (files == null) continue;
                    for (File f : files) {
                        if (f.isFile() && !f.getName().startsWith(".")) {
                            counts[i]++;
                            sizes[i] += f.length();
                        }
                    }
                    totalCount += counts[i];
                    totalSize  += sizes[i];
                }

                final long[] fc = counts.clone();
                final long[] fs = sizes.clone();
                final long   ftc = totalCount;
                final long   fts = totalSize;
                final String[] fl = labels;

                runOnUiThread(() -> {
                    setStorageRow(storageCountThumbnails, storageSizeThumbnails, fc[0], fs[0]);
                    setStorageRow(storageCountProducts,   storageSizeProducts,   fc[1], fs[1]);
                    setStorageRow(storageCountRecipes,    storageSizeRecipes,    fc[2], fs[2]);
                    setStorageRow(storageCountMeals,      storageSizeMeals,      fc[3], fs[3]);
                    setStorageRow(storageCountSteps,      storageSizeSteps,      fc[4], fs[4]);
                    setStorageRow(storageCountTotal,      storageSizeTotal,      ftc,   fts);
                    // Make total bold explicitly since setStorageRow sets plain text
                });
            });
        };

        // Load immediately on open.
        refreshStorageRunnable.run();

        // Refresh button re-runs the same runnable.
        refreshStorageButton.setOnClickListener(v -> refreshStorageRunnable.run());

        // ── Gallery visibility toggle ─────────────────────────────────────────
        galleryVisibilitySwitch.setChecked(
                prefs.getBoolean(PREF_KEY_GALLERY_VISIBLE, false));

        galleryVisibilitySwitch.setOnCheckedChangeListener((btn, isChecked) -> {
            prefs.edit().putBoolean(PREF_KEY_GALLERY_VISIBLE, isChecked).apply();
            applyGalleryVisibility(isChecked, storageManager);
        });

        // ── Thumbnail visibility toggle ───────────────────────────────────────
        thumbnailVisibilitySwitch.setChecked(
                prefs.getBoolean(PREF_KEY_THUMBNAIL_VISIBLE, false));

        thumbnailVisibilitySwitch.setOnCheckedChangeListener((btn, isChecked) -> {
            prefs.edit().putBoolean(PREF_KEY_THUMBNAIL_VISIBLE, isChecked).apply();
            applyThumbnailVisibility(isChecked, storageManager);
        });

        // ── Purge button ──────────────────────────────────────────────────────
        purgeImagesButton.setOnClickListener(v -> {
            purgeImagesButton.setEnabled(false);
            purgeStatusText.setText(R.string.image_library_purge_running);
            purgeStatusText.setVisibility(View.VISIBLE);

            ImagePurgeManager purgeManager =
                    ((SugarDaddiApplication) getApplication()).getImagePurgeManager();

            // purgeOrphansSync() is @WorkerThread - must run on background thread.
            imageSettingsExecutor.execute(() -> {
                // Measure freed space before purge
                long bytesBefore = getTotalStorageBytes(storageManager);
                int deleted = purgeManager.purgeOrphansSync();
                long bytesAfter  = getTotalStorageBytes(storageManager);
                long bytesFreed  = Math.max(0, bytesBefore - bytesAfter);

                runOnUiThread(() -> {
                    purgeImagesButton.setEnabled(true);

                    String result = deleted == 0
                            ? getString(R.string.image_library_purge_result_clean)
                            : getString(R.string.image_library_purge_result_deleted,
                            deleted, formatBytes(bytesFreed));

                    purgeStatusText.setText(result);
                    purgeStatusText.setVisibility(View.VISIBLE);

                    // Refresh storage summary to reflect the freed space.
                    if (refreshStorageRunnable != null) refreshStorageRunnable.run();

                    Log.i(TAG, "Manual purge: " + deleted + " orphan(s) deleted, "
                            + formatBytes(bytesFreed) + " freed");
                });
            });
        });
    }

    /**
     * Scans or unscans all files in products/, recipes/, meals/, steps/
     * into/from the device MediaStore. thumbnails/ is NOT touched here -
     * it is handled by applyThumbnailVisibility().
     *
     * Runs on imageSettingsExecutor - file listing and MediaScanner calls
     * must not block the main thread.
     */
    private void applyGalleryVisibility(boolean makeVisible,
                                        @NonNull ImageStorageManager storageManager) {
        imageSettingsExecutor.execute(() -> {
            File[] dirs = {
                    storageManager.getProductsDir(),
                    storageManager.getRecipesDir(),
                    storageManager.getMealsDir(),
                    storageManager.getStepsDir()
            };
            scanOrUnscanDirs(dirs, makeVisible, storageManager,
                    "Gallery visibility (products/recipes/meals/steps)");
        });
    }

    /**
     * Scans or unscans all files in thumbnails/ into/from the device MediaStore.
     *
     * Thumbnails are auto-managed and normally private. This toggle gives the
     * user explicit control over their gallery visibility.
     */
    private void applyThumbnailVisibility(boolean makeVisible,
                                          @NonNull ImageStorageManager storageManager) {
        imageSettingsExecutor.execute(() -> {
            File[] dirs = { storageManager.getThumbnailsDir() };
            scanOrUnscanDirs(dirs, makeVisible, storageManager,
                    "Thumbnail visibility");
        });
    }

    /**
     * Scans or unscans all regular files in the given directories.
     * Must be called from a background thread.
     *
     * SCANNING (makeVisible = true)
     * ==============================
     * Uses storageManager.scanFile() which inserts file content directly into
     * MediaStore via ContentResolver.insert(). MediaScannerConnection.scanFile()
     * cannot be used here because files in getExternalFilesDir() are app-private
     * and intentionally excluded from MediaStore indexing by Android design.
     * Each file is processed sequentially - storageManager.scanFile() is blocking.
     *
     * UNSCANNING (makeVisible = false)
     * ==================================
     * Uses storageManager.unscanFile() which queries MediaStore by display name
     * and deletes the entry. If the user has already deleted the image manually
     * from their gallery, unscanFile() returns false gracefully - no crash,
     * no action needed, the desired state is already achieved.
     *
     * @param dirs          Directories to scan. Null entries are skipped.
     * @param makeVisible   true to add to gallery, false to remove from gallery.
     * @param storageManager The application-scoped storage manager instance.
     * @param logLabel      Human-readable label for log messages.
     */
    private void scanOrUnscanDirs(@NonNull File[] dirs,
                                  boolean makeVisible,
                                  @NonNull ImageStorageManager storageManager,
                                  @NonNull String logLabel) {
        // Collect all regular files across all provided directories.
        java.util.List<String> allPaths = new java.util.ArrayList<>();
        for (File dir : dirs) {
            if (dir == null || !dir.exists() || !dir.isDirectory()) continue;
            File[] files = dir.listFiles();
            if (files == null) continue;
            for (File f : files) {
                if (f.isFile() && !f.getName().startsWith(".")) {
                    allPaths.add(f.getAbsolutePath());
                }
            }
        }

        if (allPaths.isEmpty()) {
            Log.d(TAG, logLabel + ": no files found to "
                    + (makeVisible ? "scan" : "unscan"));
            return;
        }

        if (makeVisible) {
            // MediaScannerConnection.scanFile() does not work for files in
            // getExternalFilesDir() - those paths are app-private and intentionally
            // excluded from MediaStore indexing. Use storageManager.scanFile()
            // which inserts file content directly into MediaStore via
            // ContentResolver.insert() + openOutputStream().
            // storageManager.scanFile() is blocking - safe here since we are
            // already on imageSettingsExecutor (background thread).
            int scanned = 0;
            for (String path : allPaths) {
                File file = new File(path);
                storageManager.scanFile(file, (scannedPath, uri) -> {
                    if (uri != null) {
                        Log.d(TAG, logLabel + " added to gallery: "
                                + new File(scannedPath).getName());
                    } else {
                        Log.w(TAG, logLabel + " failed to add to gallery: "
                                + new File(scannedPath).getName());
                    }
                });
                scanned++;
            }
            Log.d(TAG, logLabel + ": processed " + scanned + " file(s) for gallery");

        } else {
            // unscanFile() queries MediaStore by display name and deletes the entry.
            // Returns false gracefully if the entry is already absent (e.g. the user
            // manually deleted it from their gallery) - no crash, no action needed.
            int removed = 0;
            for (String path : allPaths) {
                if (storageManager.unscanFile(new File(path))) removed++;
            }
            Log.d(TAG, logLabel + ": unscanned " + removed
                    + "/" + allPaths.size() + " file(s)");
        }
    }

    /**
     * Returns the total byte size of all files across all managed directories.
     * Used to calculate bytes freed after a purge.
     * Must be called from a background thread.
     */
    private long getTotalStorageBytes(@NonNull ImageStorageManager storageManager) {
        File[] dirs = {
                storageManager.getThumbnailsDir(),
                storageManager.getProductsDir(),
                storageManager.getRecipesDir(),
                storageManager.getMealsDir(),
                storageManager.getStepsDir()
        };
        long total = 0;
        for (File dir : dirs) {
            if (dir == null || !dir.exists()) continue;
            File[] files = dir.listFiles();
            if (files == null) continue;
            for (File f : files) {
                if (f.isFile()) total += f.length();
            }
        }
        return total;
    }

    /**
     * Sets the count and size TextViews for one storage row.
     * Empty directories show "-" in both cells.
     */
    private void setStorageRow(@NonNull TextView countView,
                               @NonNull TextView sizeView,
                               long count, long bytes) {
        if (count == 0) {
            countView.setText("-");
            sizeView.setText("-");
        } else {
            countView.setText(count + " image" + (count == 1 ? "" : "s"));
            sizeView.setText(formatBytes(bytes));
        }
    }

    /**
     * Converts a byte count to a human-readable string.
     * Examples: "843 B", "12.3 KB", "4.2 MB", "1.1 GB"
     */
    private String formatBytes(long bytes) {
        if (bytes < 1024)
            return bytes + " B";
        if (bytes < 1024L * 1024)
            return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024)
            return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }
}