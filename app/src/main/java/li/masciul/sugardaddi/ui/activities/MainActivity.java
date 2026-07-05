package li.masciul.sugardaddi.ui.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.widget.Toolbar;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputLayout;

import li.masciul.sugardaddi.R;
import li.masciul.sugardaddi.business.search.SearchCache;
import li.masciul.sugardaddi.business.search.SearchFilter;
import li.masciul.sugardaddi.business.search.SearchManager;
import li.masciul.sugardaddi.core.enums.ProductType;
import li.masciul.sugardaddi.core.interfaces.Searchable;
import li.masciul.sugardaddi.core.models.Error;
import li.masciul.sugardaddi.core.models.FoodProduct;
import li.masciul.sugardaddi.core.models.Recipe;
import li.masciul.sugardaddi.data.network.ApiConfig;
import li.masciul.sugardaddi.data.sources.aggregation.DataSourceAggregator;
import li.masciul.sugardaddi.data.sources.base.DataSource;
import li.masciul.sugardaddi.managers.DataSourceManager;
import li.masciul.sugardaddi.ui.adapters.AutocompleteAdapter;
import li.masciul.sugardaddi.ui.adapters.SearchResultsAdapter;
import li.masciul.sugardaddi.ui.scan.ProductBarcodeScanner;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * MainActivity - Search entry point for SugarDaddi.
 *
 * ARCHITECTURE v3.0 - Clean wiring
 * ==================================
 * MainActivity owns no repositories. All search state and orchestration lives
 * in SearchManager. ProductRepository and RecipeRepository are instantiated by
 * ProductManager and RecipeManager respectively - MainActivity has no visibility
 * into either.
 *
 * DEPENDENCIES (instantiated here, passed to SearchManager):
 *   DataSourceAggregator - stateless parallel search executor
 *   SearchCache           - SearchResultCache + Room enrichment
 *
 * SEARCH FLOW:
 *   1. User types          → autocomplete (debounced via SearchManager)
 *   2. User presses Enter  → full search (SearchManager.searchImmediate)
 *   3. User taps suggestion → full search (SearchManager.searchImmediate)
 *   4. User scrolls to end → pagination (SearchManager.loadMoreResults)
 *
 * ADD-TO-MEAL MODE:
 *   Launched by MealDetailsActivity with extras "mode"="add_to_meal" and
 *   "extra_meal_id". Disables the navigation drawer and passes the meal ID
 *   through to ProductDetailsActivity so the "Add to Meal" bottom bar appears.
 */
public class MainActivity extends BaseActivity implements
        SearchResultsAdapter.OnItemClickListener,
        SearchResultsAdapter.OnLoadMoreListener,
        SearchManager.SearchListener,
        SearchManager.AutocompleteListener,
        NavigationView.OnNavigationItemSelectedListener {

    private static final String TAG = "MainActivity";

    // Trigger loadMoreResults when within this many items of the list end
    private static final int PAGINATION_TRIGGER_THRESHOLD = 3;

    // Minimum milliseconds between loadMore calls triggered by the scroll listener.
    // SearchManager has its own guard too - this is a UI-level safety net.
    private static final long LOAD_MORE_DEBOUNCE_MS = 1000;

    // =========================================================================
    // UI COMPONENTS
    // =========================================================================

    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private ActionBarDrawerToggle drawerToggle;

    private AutoCompleteTextView searchEditText;
    private TextInputLayout searchInputLayout;

    // Filter pills
    private MaterialButton filterTypeButton;
    private MaterialButton filterSourceButton;

    private LinearProgressIndicator progressIndicator;
    private RecyclerView recyclerView;
    private View emptyStateView;
    private View errorStateView;
    private TextView emptyTitle;
    private TextView emptyMessage;
    private TextView errorTitle;
    private TextView errorMessage;
    private Button tryAgainButton;
    private FloatingActionButton fabScan;

    private SearchResultsAdapter adapter;
    private AutocompleteAdapter autocompleteAdapter;

    // =========================================================================
    // BUSINESS LOGIC
    // =========================================================================

    /**
     * Owns all search state: pagination, deduplication, debouncing, caching.
     * Instantiated in initializeBusinessLogic(). Never null after that.
     */
    private SearchManager searchManager;

    // =========================================================================
    // STATE
    // =========================================================================

    /**
     * Last query committed to a full search (Enter / suggestion tap).
     * Used by the retry button and debug logging. SearchManager tracks its
     * own currentQuery internally - this is purely for the UI layer.
     */
    private String lastQuery = "";

    /**
     * Meal ID passed by MealDetailsActivity when launching in add-to-meal mode.
     * Null in normal operation. Forwarded to ProductDetailsActivity as
     * "RETURN_TO_MEAL" so the "Add to Meal" bottom bar is shown.
     */
    private String returnToMealId = null;

    /** Timestamp of last scroll-triggered loadMore - prevents rapid-fire calls. */
    private long lastLoadMoreTimestamp = 0;

    // =========================================================================
    // LIFECYCLE
    // =========================================================================

    @Override
    protected void onBaseActivityCreated(Bundle savedInstanceState) {
        setContentView(R.layout.activity_main);

        // Detect add-to-meal launch mode
        String mode = getIntent().getStringExtra("mode");
        returnToMealId = getIntent().getStringExtra("extra_meal_id");
        if ("add_to_meal".equals(mode) && returnToMealId != null) {
            logDebug("Opened in add-to-meal mode for meal: " + returnToMealId);
        }

        setupToolbar();
        setupNavigationDrawer();
        initializeUIComponents();
        initializeBusinessLogic();
        setupScrollListener();
        setupAutocomplete();
        setupEventListeners();

        showEmptyState();
        logDebug("MainActivity v3.0 initialized");
    }

    /**
     * Re-enrich the displayed results from Room on every resume so that an image
     * set or removed on a detail screen is reflected on the search card when the
     * user navigates back - without re-running the search. No-op when there are no
     * results on screen. The list objects are shared with the search cache by
     * reference, so enriching them in place refreshes the cache too.
     */
    @Override
    protected void onActivityResumed() {
        super.onActivityResumed();
        if (searchManager != null && adapter != null && !adapter.getItems().isEmpty()) {
            searchManager.refreshDisplayedResults(
                    adapter.getItems(),
                    () -> adapter.notifyDataSetChanged());
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (searchManager != null) searchManager.cancel();
        if (searchEditText != null) searchEditText.dismissDropDown();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (searchManager != null) {
            searchManager.cancel();
            searchManager.cleanup();
        }
        if (ApiConfig.DEBUG_LOGGING && searchManager != null) {
            logDebug(searchManager.getSearchStats());
        }
        logDebug("MainActivity destroyed");
    }

    // =========================================================================
    // INITIALIZATION
    // =========================================================================

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (returnToMealId != null) {
            // Add-to-meal mode: show back button, custom title
            if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle(getSafeString(R.string.search_food_for_meal));
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                getSupportActionBar().setHomeButtonEnabled(true);
            }
            toolbar.setNavigationOnClickListener(v -> finish());
        } else {
            if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle(getSafeString(R.string.main_activity_title));
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                getSupportActionBar().setHomeButtonEnabled(true);
            }
        }
    }

    private void setupNavigationDrawer() {
        drawerLayout   = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);

        if (returnToMealId != null) {
            // Disable drawer in add-to-meal mode - back button is the only exit
            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
            return;
        }

        Toolbar toolbar = findViewById(R.id.toolbar);
        drawerToggle = new ActionBarDrawerToggle(
                this, drawerLayout, toolbar,
                R.string.navigation_drawer_open,
                R.string.navigation_drawer_close);
        drawerLayout.addDrawerListener(drawerToggle);
        drawerToggle.syncState();
        navigationView.setNavigationItemSelectedListener(this);
        navigationView.setCheckedItem(R.id.nav_search);
    }

    private void initializeUIComponents() {
        searchEditText     = findViewById(R.id.searchEditText);
        searchInputLayout  = findViewById(R.id.searchInputLayout);
        progressIndicator  = findViewById(R.id.progressIndicator);
        filterTypeButton   = findViewById(R.id.filterTypeButton);
        filterSourceButton = findViewById(R.id.filterSourceButton);
        recyclerView       = findViewById(R.id.recyclerView);
        emptyStateView     = findViewById(R.id.emptyStateView);
        errorStateView     = findViewById(R.id.errorStateView);
        emptyTitle         = findViewById(R.id.emptyTitle);
        emptyMessage       = findViewById(R.id.emptyMessage);
        errorTitle         = findViewById(R.id.errorTitle);
        errorMessage       = findViewById(R.id.errorMessage);
        tryAgainButton     = findViewById(R.id.tryAgainButton);
        fabScan            = findViewById(R.id.fabScan);

        // Adapter
        adapter = new SearchResultsAdapter(this, this);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(adapter);

        updateFilterPillLabels();
    }

    /**
     * Wire up SearchManager with its two dependencies.
     *
     * Neither ProductRepository nor RecipeRepository is needed here.
     * ProductManager (inside ProductDetailsActivity) owns ProductRepository.
     * RecipeManager (inside RecipeDetailsActivity) owns RecipeRepository.
     */
    private void initializeBusinessLogic() {
        try {
            DataSourceAggregator aggregator = new DataSourceAggregator(this);
            SearchCache searchCache = new SearchCache(this);

            searchManager = new SearchManager(this, aggregator, searchCache);
            searchManager.setListener(this);
            searchManager.setAutocompleteListener(this);

            logDebug("SearchManager initialized");
        } catch (Exception e) {
            Log.e(TAG, "Error initializing business logic", e);
            Toast.makeText(this,
                    "Error initializing app: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void setupScrollListener() {
        LinearLayoutManager layoutManager =
                (LinearLayoutManager) recyclerView.getLayoutManager();
        if (layoutManager == null) return;

        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                super.onScrolled(rv, dx, dy);
                logDebug("On scrolled called");
                if (dy <= 0) return; // Only trigger on downward scroll

                int totalItemCount   = layoutManager.getItemCount();
                int lastVisibleItem  = layoutManager.findLastVisibleItemPosition();

                if (lastVisibleItem >= totalItemCount - PAGINATION_TRIGGER_THRESHOLD) {
                    // Post to next frame - calling notifyItemChanged inside a scroll
                    // callback throws IllegalStateException
                    rv.post(() -> onLoadMore());
                }
                logDebug("End of on scrolled");
            }
        });
    }

    private void setupAutocomplete() {
        autocompleteAdapter = new AutocompleteAdapter(this);
        searchEditText.setAdapter(autocompleteAdapter);
        searchEditText.setThreshold(3);

        searchInputLayout.post(() -> {
            searchEditText.setDropDownWidth(searchInputLayout.getWidth());
            logDebug("Autocomplete dropdown width set");
        });
    }

    private void setupEventListeners() {
        // 1. Text changes → autocomplete (debounced in SearchManager)
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                String query = s.toString().trim();
                if (query.length() >= 3 && searchManager != null) {
                    searchManager.autocomplete(query);
                } else if (query.isEmpty()) {
                    showEmptyState();
                }
            }
        });

        // 2. IME action (Enter / Search key) → full immediate search
        searchEditText.setOnEditorActionListener((v, actionId, event) -> {
            String query = searchEditText.getText().toString().trim();
            if (!query.isEmpty()) {
                searchEditText.dismissDropDown();
                autocompleteAdapter.clear();
                hideKeyboard(searchEditText);
                performSearch(query);
            }
            return true;
        });

        // 3. Autocomplete suggestion tap → full immediate search
        searchEditText.setOnItemClickListener((parent, view, position, id) -> {
            String suggestion = (String) parent.getItemAtPosition(position);
            if (suggestion != null) {
                searchEditText.setText(suggestion);
                searchEditText.dismissDropDown();
                autocompleteAdapter.clear();
                hideKeyboard(searchEditText);
                performSearch(suggestion);
            }
        });

        // 4. Barcode FAB
        fabScan.setOnClickListener(v ->
                new ProductBarcodeScanner(this).scanAndOpenProduct());

        // 5. Error retry
        tryAgainButton.setOnClickListener(v -> {
            if (!lastQuery.isEmpty()) {
                hideKeyboard(searchEditText);
                performSearch(lastQuery);
            }
        });

        // 6. Type filter pill
        filterTypeButton.setOnClickListener(v -> showTypeFilterPopup());

        // 7. Source filter pill
        filterSourceButton.setOnClickListener(v -> showSourceFilterPopup());
    }

    // =========================================================================
    // SEARCH
    // =========================================================================

    private void performSearch(@NonNull String query) {
        String trimmed = query.trim();
        if (trimmed.isEmpty()) {
            showEmptyState();
            return;
        }
        lastQuery = trimmed;
        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, "Full search: '" + trimmed + "'");
        }
        if (searchManager != null) {
            searchManager.searchImmediate(trimmed);
        }
    }

    // =========================================================================
    // PAGINATION
    // =========================================================================

    @Override
    public void onLoadMore() {
        if (lastQuery.isEmpty() || searchManager == null) return;

        // Scroll-listener debounce - SearchManager has its own guard too
        long now = System.currentTimeMillis();
        if (now - lastLoadMoreTimestamp < LOAD_MORE_DEBOUNCE_MS) return;
        lastLoadMoreTimestamp = now;

        logDebug("Load more requested for: '" + lastQuery + "'");
        searchManager.loadMoreResults();
    }

    // =========================================================================
    // SearchManager.SearchListener
    // =========================================================================

    @Override
    public void onSearchResults(@NonNull List<Searchable> results, boolean hasMore) {
        if (ApiConfig.DEBUG_LOGGING) {
            int products = 0, recipes = 0;
            for (Searchable item : results) {
                if (item.getProductType() == ProductType.FOOD)   products++;
                else if (item.getProductType() == ProductType.RECIPE) recipes++;
            }
            Log.d(TAG, "Search results: " + results.size()
                    + " (" + products + " products, " + recipes + " recipes)"
                    + " hasMore=" + hasMore);
        }
        adapter.updateItems(results, hasMore);
        showSearchResults();
    }

    @Override
    public void onSearchError(@NonNull Error error) {
        Log.w(TAG, "Search error: " + error.getMessage());
        showError(error);
    }

    @Override
    public void onSearchLoading() {
        showLoading();
    }

    @Override
    public void onSearchEmpty() {
        showEmptyState();
    }

    @Override
    public void onMoreResults(@NonNull List<Searchable> results, boolean hasMore) {
        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, "More results: " + results.size() + " items, hasMore=" + hasMore);
        }
        adapter.addMoreItems(results, hasMore);
        adapter.setLoadingMore(false);
    }

    @Override
    public void onMoreResultsError(@NonNull Error error) {
        adapter.setLoadingMore(false);
        Toast.makeText(this, error.getMessage(), Toast.LENGTH_SHORT).show();
        Log.w(TAG, "Load more error: " + error.getMessage());
    }

    @Override
    public void onLoadingMore() {
        adapter.setLoadingMore(true);
    }

    @Override
    public void onSearchCancelled() {
        logDebug("Search cancelled");
    }

    // =========================================================================
    // SearchManager.AutocompleteListener
    // =========================================================================

    @Override
    public void onAutocompleteSuggestions(@NonNull List<String> suggestions) {
        autocompleteAdapter.setSuggestions(suggestions);
        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, "Autocomplete: " + suggestions.size() + " suggestions");
        }
    }

    @Override
    public void onAutocompleteError(@NonNull Error error) {
        // Silent - empty dropdown is acceptable for autocomplete failures
        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, "Autocomplete error (ignored): " + error.getMessage());
        }
    }

    @Override
    public void onQueryTooShort() {
        autocompleteAdapter.clear();
    }

    // =========================================================================
    // SearchResultsAdapter.OnItemClickListener
    // =========================================================================

    @Override
    public void onItemClick(@NonNull Searchable item) {
        String language = getCurrentLanguage().getCode();

        if (item instanceof FoodProduct) {
            Intent intent = new Intent(this, ProductDetailsActivity.class);
            intent.putExtra(ProductDetailsActivity.EXTRA_FOOD_ITEM, item.getSearchableId());
            if (returnToMealId != null) {
                intent.putExtra("RETURN_TO_MEAL", returnToMealId);
            }
            startActivity(intent);
            logDebug("Opening product: " + item.getDisplayName(language));

        } else if (item instanceof Recipe) {
            Intent intent = new Intent(this, RecipeDetailsActivity.class);
            intent.putExtra(RecipeDetailsActivity.EXTRA_RECIPE_ID, item.getSearchableId());
            startActivity(intent);
            logDebug("Opening recipe: " + item.getDisplayName(language));

        } else {
            Log.w(TAG, "Unknown item type: " + item.getClass().getSimpleName());
        }
    }

    @Override
    public void onItemLongClick(@NonNull Searchable item, int position) {
        Toast.makeText(this,
                getSafeString(R.string.long_click_hint),
                Toast.LENGTH_SHORT).show();
    }

    // =========================================================================
    // NavigationView.OnNavigationItemSelectedListener
    // =========================================================================

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.nav_journal) {
            startActivity(new Intent(this, JournalActivity.class));
        } else if (id == R.id.nav_create_meal) {
            startActivity(new Intent(this, CreateMealActivity.class));
        } else if (id == R.id.nav_favorites) {
            Intent intent = new Intent(this, FavoritesActivity.class);
            if (returnToMealId != null) {
                intent.putExtra("RETURN_TO_MEAL", returnToMealId);
            }
            startActivity(intent);
        } else if (id == R.id.nav_data_sources) {
            startActivity(new Intent(this, DataSourcesActivity.class));
        } else if (id == R.id.nav_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
        }
        // nav_search: already here, just close the drawer

        if (drawerLayout != null) {
            drawerLayout.closeDrawers();
        }
        return true;
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout != null
                && drawerLayout.isDrawerOpen(androidx.core.view.GravityCompat.START)) {
            drawerLayout.closeDrawer(androidx.core.view.GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    // =========================================================================
    // UI STATE
    // =========================================================================

    private void showLoading() {
        progressIndicator.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE);
        emptyStateView.setVisibility(View.GONE);
        errorStateView.setVisibility(View.GONE);
    }

    private void showSearchResults() {
        progressIndicator.setVisibility(View.GONE);
        recyclerView.setVisibility(View.VISIBLE);
        emptyStateView.setVisibility(View.GONE);
        errorStateView.setVisibility(View.GONE);
    }

    private void showEmptyState() {
        progressIndicator.setVisibility(View.GONE);
        recyclerView.setVisibility(View.GONE);
        emptyStateView.setVisibility(View.VISIBLE);
        errorStateView.setVisibility(View.GONE);
    }

    private void showError(@NonNull Error error) {
        progressIndicator.setVisibility(View.GONE);
        recyclerView.setVisibility(View.GONE);
        emptyStateView.setVisibility(View.GONE);
        errorStateView.setVisibility(View.VISIBLE);

        if (errorTitle != null) {
            errorTitle.setText(getSafeString(R.string.error_general_title));
        }
        if (errorMessage != null) {
            errorMessage.setText(error.getMessage() != null
                    ? error.getMessage()
                    : getSafeString(R.string.error_network_message));
        }
    }

    // =========================================================================
    // SEARCH FILTERS
    // =========================================================================

    /**
     * Show a popup below the type pill listing available item types.
     * Current selection is pre-checked. Search re-triggers on dismiss.
     */
    private void showTypeFilterPopup() {
        SearchFilter current = searchManager.getActiveFilter();
        Set<ProductType> currentTypes = current.getAllowedTypes();

        boolean foodSelected   = !current.isTypeFilterActive()
                || currentTypes.contains(ProductType.FOOD);
        boolean recipeSelected = !current.isTypeFilterActive()
                || currentTypes.contains(ProductType.RECIPE);

        PopupMenu popup = new PopupMenu(this, filterTypeButton);
        popup.getMenu().add(0, 1, 0, getString(R.string.filter_type_food))
                .setCheckable(true).setChecked(foodSelected);
        popup.getMenu().add(0, 2, 1, getString(R.string.filter_type_recipe))
                .setCheckable(true).setChecked(recipeSelected);

        popup.setOnMenuItemClickListener(item -> {
            item.setChecked(!item.isChecked());
            item.setShowAsAction(MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW);
            item.setActionView(new View(this));
            return false;
        });

        popup.setOnDismissListener(menu -> {
            boolean food   = popup.getMenu().findItem(1).isChecked();
            boolean recipe = popup.getMenu().findItem(2).isChecked();

            Set<ProductType> selected = new HashSet<>();
            if (food)   selected.add(ProductType.FOOD);
            if (recipe) selected.add(ProductType.RECIPE);

            boolean noFilter = selected.size() == 2 || selected.isEmpty();

            SearchFilter newFilter = new SearchFilter(
                    noFilter ? Collections.emptySet() : selected,
                    current.getAllowedSources()
            );
            searchManager.setFilters(newFilter);
            updateFilterPillLabels();
        });

        popup.show();
    }

    /**
     * Show a popup below the source pill listing all active sources.
     * Disabled sources in Settings do not appear here.
     * Search re-triggers on dismiss.
     */
    private void showSourceFilterPopup() {
        SearchFilter current = searchManager.getActiveFilter();
        List<DataSource> activeSources =
                DataSourceManager.getInstance(this).getActiveSources();

        if (activeSources.isEmpty()) return;

        PopupMenu popup = new PopupMenu(this, filterSourceButton);
        for (int i = 0; i < activeSources.size(); i++) {
            DataSource source = activeSources.get(i);
            boolean checked = !current.isSourceFilterActive()
                    || current.getAllowedSources().contains(source.getSourceId());
            popup.getMenu().add(0, i, i, source.getSourceName())
                    .setCheckable(true).setChecked(checked);
        }

        popup.setOnMenuItemClickListener(item -> {
            item.setChecked(!item.isChecked());
            item.setShowAsAction(MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW);
            item.setActionView(new View(this));
            return false;
        });

        popup.setOnDismissListener(menu -> {
            Set<String> selected = new HashSet<>();
            for (int i = 0; i < activeSources.size(); i++) {
                if (popup.getMenu().findItem(i).isChecked()) {
                    selected.add(activeSources.get(i).getSourceId());
                }
            }

            boolean noFilter = selected.size() == activeSources.size();

            SearchFilter newFilter = new SearchFilter(
                    current.getAllowedTypes(),
                    noFilter ? Collections.emptySet() : selected
            );
            searchManager.setFilters(newFilter);
            updateFilterPillLabels();
        });

        popup.show();
    }

    /**
     * Update both pill labels to reflect the current active filter state.
     * Called after dismissing either popup and on first layout.
     */
    private void updateFilterPillLabels() {
        if (searchManager == null || filterTypeButton == null) return;

        SearchFilter filter = searchManager.getActiveFilter();
        int totalSources = DataSourceManager.getInstance(this).getActiveSources().size();

        // Type label
        Set<ProductType> types = filter.getAllowedTypes();
        if (!filter.isTypeFilterActive() || types.size() >= 2) {
            filterTypeButton.setText(getString(R.string.filter_type_all));
        } else if (types.contains(ProductType.FOOD)) {
            filterTypeButton.setText(getString(R.string.filter_type_food));
        } else if (types.contains(ProductType.RECIPE)) {
            filterTypeButton.setText(getString(R.string.filter_type_recipe));
        } else {
            filterTypeButton.setText(getString(R.string.filter_type_all));
        }

        // Source label
        if (!filter.isSourceFilterActive()
                || filter.getAllowedSources().size() == totalSources) {
            filterSourceButton.setText(getString(R.string.filter_source_all));
        } else {
            filterSourceButton.setText(getString(R.string.filter_source_subset,
                    filter.getAllowedSources().size(), totalSources));
        }
    }

    // =========================================================================
    // UTILITY
    // =========================================================================

    private void hideKeyboard(@NonNull View view) {
        android.view.inputmethod.InputMethodManager imm =
                (android.view.inputmethod.InputMethodManager)
                        getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    @NonNull
    public String getCurrentQuery() {
        return lastQuery;
    }
}