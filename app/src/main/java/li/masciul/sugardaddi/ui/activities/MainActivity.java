package li.masciul.sugardaddi.ui.activities;

import android.content.Intent;
import li.masciul.sugardaddi.data.sources.ciqual.CiqualDataSource;
import li.masciul.sugardaddi.data.sources.ciqual.CiqualImportService;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputLayout;

import java.util.List;

import li.masciul.sugardaddi.R;
import li.masciul.sugardaddi.business.search.SearchManager;
import li.masciul.sugardaddi.core.enums.ProductType;
import li.masciul.sugardaddi.core.interfaces.Searchable;
import li.masciul.sugardaddi.core.models.Error;
import li.masciul.sugardaddi.core.models.FoodProduct;
import li.masciul.sugardaddi.core.models.Recipe;
import li.masciul.sugardaddi.data.network.ApiConfig;
import li.masciul.sugardaddi.data.network.NetworkManager;
import li.masciul.sugardaddi.data.repository.ProductRepository;
import li.masciul.sugardaddi.data.repository.RecipeRepository;
import li.masciul.sugardaddi.managers.DataSourceManager;
import li.masciul.sugardaddi.ui.adapters.AutocompleteAdapter;
import li.masciul.sugardaddi.ui.adapters.SearchResultsAdapter;

/**
 * MainActivity - Unified search interface with clean listener-based architecture
 *
 * UNIFIED SEARCH v2.1 - WITH NAVIGATION DRAWER
 *
 * This activity provides a unified search interface for products and recipes with:
 * - Navigation Drawer (hamburger menu) for app-wide navigation
 * - Listener-based SearchManager integration (no inline callbacks)
 * - SearchScope configuration (PRODUCTS_ONLY, RECIPES_ONLY, ALL)
 * - Autocomplete support with lightweight suggestions
 * - Full search with comprehensive results
 * - Pagination support for products
 * - Polymorphic item handling (FoodProduct and Recipe)
 * - Type-aware navigation
 * - Clean semantic method names
 *
 * CURRENT CONFIGURATION:
 * - SearchScope.PRODUCTS_ONLY (food products only)
 * - Future: Can easily change to ALL for meal composition
 *
 * SEARCH FLOW:
 * 1. User types → Autocomplete (debounced, lightweight suggestions)
 * 2. User presses Enter → Full search (comprehensive results)
 * 3. User clicks suggestion → Full search
 * 4. User scrolls near bottom → Load more results (pagination)
 *
 * @version 2.1 - Added Navigation Drawer
 * @author SugarDaddi Team
 */
public class MainActivity extends BaseActivity implements
        SearchResultsAdapter.OnItemClickListener,
        SearchResultsAdapter.OnLoadMoreListener,
        SearchManager.SearchListener,
        SearchManager.AutocompleteListener,
        NavigationView.OnNavigationItemSelectedListener {

    private static final String TAG = "MainActivity";

    // Pagination: trigger loadMore when user is within N items of the end
    private static final int PAGINATION_TRIGGER_THRESHOLD = 3;

    // Current search scope (can be changed for meal composition)
    private static final SearchManager.SearchScope SEARCH_SCOPE =
            SearchManager.SearchScope.PRODUCTS_ONLY;

    // ========== UI COMPONENTS ==========

    // Drawer (NEW)
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private ActionBarDrawerToggle drawerToggle;

    // Search (existing)
    private AutoCompleteTextView searchEditText;
    private TextInputLayout searchInputLayout;
    private RecyclerView recyclerView;
    private LinearProgressIndicator progressIndicator;
    private View emptyStateView;
    private View errorStateView;
    private TextView emptyTitle;
    private TextView emptyMessage;
    private TextView errorTitle;
    private TextView errorMessage;
    private Button tryAgainButton;
    private FloatingActionButton fabScan;
    private String returnToMealId = null;

    // ========== BUSINESS LOGIC COMPONENTS ==========
    private SearchResultsAdapter adapter;
    private SearchManager searchManager;
    private ProductRepository productRepository;
    private RecipeRepository recipeRepository;
    private AutocompleteAdapter autocompleteAdapter;

    // ========== STATE ==========
    private String lastQuery = "";

    @Override
    protected void onBaseActivityCreated(Bundle savedInstanceState) {
        setContentView(R.layout.activity_main);

        // Check if we are in "add to meal" mode
        // MealDetailsActivity passes both "mode" and EXTRA_MEAL_ID
        String mode = getIntent().getStringExtra("mode");
        returnToMealId = getIntent().getStringExtra("extra_meal_id");

        // Validate: if mode is "add_to_meal", we must have a meal ID
        if ("add_to_meal".equals(mode) && returnToMealId != null) {
            logDebug("MainActivity opened in add to meal mode for meal: " + returnToMealId);
        }

        setupToolbar();
        setupNavigationDrawer();
        initializeUIComponents();
        initializeBusinessLogic();
        setupEventListeners();

        showEmptyState();
        logDebug("MainActivity initialized with unified SearchManager (v2.1 + Drawer)");
    }

    // ========== INITIALIZATION ==========

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (returnToMealId != null) {
            // In "add to meal" mode - show back button
            if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle(getSafeString(R.string.search_food_for_meal));
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                getSupportActionBar().setHomeButtonEnabled(true);
            }
            // Handle back button click
            toolbar.setNavigationOnClickListener(v -> {
                finish(); // Return to AddFoodItemsActivity
            });
        } else {
            // Normal mode - show app title
            if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle(getSafeString(R.string.main_activity_title));
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                getSupportActionBar().setHomeButtonEnabled(true);
            }
        }
    }

    // ========== NAVIGATION DRAWER SETUP (NEW) ==========

    private void setupNavigationDrawer() {
        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);

        // Only setup drawer if NOT in "add to meal" mode
        if (returnToMealId == null) {
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

            // Set navigation listener
            navigationView.setNavigationItemSelectedListener(this);

            // Set initial checked item
            navigationView.setCheckedItem(R.id.nav_search);
        } else {
            // Disable drawer when in "add to meal" mode
            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
        }
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        // Handle navigation clicks
        if (id == R.id.nav_journal) {
            // Navigate to Journal
            Intent intent = new Intent(this, JournalActivity.class);
            startActivity(intent);
        } else if (id == R.id.nav_create_meal) {
            // Navigate to Create Meal
            Intent intent = new Intent(this, CreateMealActivity.class);
            startActivity(intent);
        } else if (id == R.id.nav_search) {
            // Already on search (MainActivity)
            // Just close drawer
        } else if (id == R.id.nav_favorites) {
            // Navigate to Favorites — forward meal context if in add-to-meal mode
            Intent intent = new Intent(this, FavoritesActivity.class);
            if (returnToMealId != null) {
                intent.putExtra("RETURN_TO_MEAL", returnToMealId);
            }
            startActivity(intent);
        } else if (id == R.id.nav_settings) {
            // Navigate to Settings
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
        }

        // Close drawer
        drawerLayout.closeDrawer(GravityCompat.START);
        return true;
    }

    // ========== TOOLBAR OPTIONS MENU ==========

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_favorites) {
            Intent intent = new Intent(this, FavoritesActivity.class);
            if (returnToMealId != null) {
                intent.putExtra("RETURN_TO_MEAL", returnToMealId);
            }
            startActivity(intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void initializeUIComponents() {
        searchEditText = findViewById(R.id.searchEditText);
        searchInputLayout = findViewById(R.id.searchInputLayout);
        recyclerView = findViewById(R.id.recyclerView);
        progressIndicator = findViewById(R.id.progressIndicator);

        emptyStateView = findViewById(R.id.emptyStateView);
        errorStateView = findViewById(R.id.errorStateView);
        emptyTitle = findViewById(R.id.emptyTitle);
        emptyMessage = findViewById(R.id.emptyMessage);
        errorTitle = findViewById(R.id.errorTitle);
        errorMessage = findViewById(R.id.errorMessage);
        tryAgainButton = findViewById(R.id.tryAgainButton);

        fabScan = findViewById(R.id.fabScan);

        setupRecyclerView();
        setupAutocomplete();
    }

    private void setupRecyclerView() {
        adapter = new SearchResultsAdapter(this);
        adapter.setOnItemClickListener(this);
        adapter.setOnLoadMoreListener(this);

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(adapter);

        // Pagination scroll listener — triggers loadMore when user scrolls
        // near the bottom of the list. This replaces the old onBind-based trigger
        // which was broken because NestedScrollView disabled view recycling.
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                super.onScrolled(rv, dx, dy);

                // Only check on downward scroll
                if (dy <= 0) return;

                int totalItemCount = layoutManager.getItemCount();
                int lastVisibleItem = layoutManager.findLastVisibleItemPosition();

                // Trigger when user is within 3 items of the end
                if (lastVisibleItem >= totalItemCount - PAGINATION_TRIGGER_THRESHOLD) {
                    onLoadMore();
                }
            }
        });
    }

    private void setupAutocomplete() {
        autocompleteAdapter = new AutocompleteAdapter(this);
        searchEditText.setAdapter(autocompleteAdapter);
        searchEditText.setThreshold(3);

        // Set dropdown width to match the TextInputLayout width after it's laid out
        searchInputLayout.post(() -> {
            int width = searchInputLayout.getWidth();
            searchEditText.setDropDownWidth(width);
            logDebug("Autocomplete dropdown width set to: " + width + "px");
        });

        logDebug("Autocomplete configured: threshold=3 chars, scope=" + SEARCH_SCOPE);
    }

    private void initializeBusinessLogic() {
        try {
            NetworkManager networkManager = NetworkManager.getInstance(this);

            // Create repositories
            productRepository = new ProductRepository(networkManager, this);
            recipeRepository = new RecipeRepository(this);

            // Create unified SearchManager with both repositories
            searchManager = new SearchManager(productRepository, recipeRepository);

            // Configure search manager
            searchManager.setSearchScope(SEARCH_SCOPE);
            searchManager.setLanguage(getCurrentLanguage().getCode());

            // Set listeners (MainActivity implements both interfaces)
            searchManager.setListener(this);
            searchManager.setAutocompleteListener(this);

            logDebug("SearchManager initialized - scope: " + SEARCH_SCOPE +
                    ", language: " + getCurrentLanguage().getCode());
        } catch (Exception e) {
            Log.e(TAG, "Error initializing business logic", e);
            Toast.makeText(this, "Error initializing app: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void setupEventListeners() {
        // 1. Text change & Autocomplete
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString();

                // Trigger autocomplete (lightweight, debounced)
                if (searchManager != null) {
                    searchManager.autocomplete(query);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // 2. Enter key & Full search
        searchEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    performSearch(v.getText().toString());
                    searchEditText.dismissDropDown();
                    hideKeyboard(v);
                    return true;
                }
                return false;
            }
        });

        // 3. Suggestion click Full search
        searchEditText.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String suggestion = (String) parent.getItemAtPosition(position);

                // Force set the text
                searchEditText.setText(suggestion);
                searchEditText.setSelection(suggestion.length());

                // Dismiss dropdown explicitly
                searchEditText.dismissDropDown();

                if (ApiConfig.DEBUG_LOGGING) {
                    Log.d(TAG, "Suggestion selected: " + suggestion);
                }

                performSearch(suggestion);
                hideKeyboard(view);
            }
        });

        // 4. Barcode scanner
        fabScan.setOnClickListener(v -> {
            Intent intent = new Intent(this, BarcodeScannerActivity.class);
            startActivity(intent);
        });

        // 5. Error retry
        tryAgainButton.setOnClickListener(v -> {
            if (!lastQuery.isEmpty()) {
                performSearch(lastQuery);
            }
        });
    }

    // ========== SEARCH METHODS ==========

    /**
     * Perform full search
     */
    private void performSearch(@NonNull String query) {
        String trimmedQuery = query.trim();

        if (trimmedQuery.isEmpty()) {
            showEmptyState();
            return;
        }

        lastQuery = trimmedQuery;

        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, "Performing full search: '" + trimmedQuery + "' (scope: " + SEARCH_SCOPE + ")");
        }

        // Execute full search (results come via listener callbacks)
        if (searchManager != null) {
            searchManager.search(trimmedQuery);
        }
    }

    // ========== SearchManager.SearchListener IMPLEMENTATION ==========

    @Override
    public void onSearchResults(@NonNull List<Searchable> results) {
        if (ApiConfig.DEBUG_LOGGING) {
            // Count types for detailed logging
            int products = 0, recipes = 0, other = 0;
            for (Searchable item : results) {
                ProductType type = item.getProductType();
                if (type == ProductType.FOOD) products++;
                else if (type == ProductType.RECIPE) recipes++;
                else other++;
            }

            Log.d(TAG, "========================================");
            Log.d(TAG, "SEARCH RESULTS RECEIVED");
            Log.d(TAG, "Total items: " + results.size());
            Log.d(TAG, "  - Products: " + products);
            Log.d(TAG, "  - Recipes: " + recipes);
            if (other > 0) Log.d(TAG, "  - Other: " + other);
            Log.d(TAG, "========================================");
        }

        adapter.updateItems(results);
        showSearchResults();
        logDebug("Search completed: " + results.size() + " results");
    }

    @Override
    public void onSearchError(@NonNull Error error) {
        Log.w(TAG, "Search error: " + error.getMessage());
        showError(error);
    }

    @Override
    public void onSearchLoading() {
        logDebug("Search loading...");
        showLoading();
    }

    @Override
    public void onSearchEmpty() {
        logDebug("Search query too short or empty");
        showEmptyState();
    }

    @Override
    public void onMoreResults(@NonNull List<Searchable> results) {
        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, "More results received: " + results.size() + " items");
        }

        adapter.addMoreItems(results);
        adapter.setLoadingMore(false);
        logDebug("Loaded " + results.size() + " more results");
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
        logDebug("Loading more results...");
    }

    @Override
    public void onSearchCancelled() {
        logDebug("Search cancelled");
    }

    // ========== SearchManager.AutocompleteListener IMPLEMENTATION ==========

    @Override
    public void onAutocompleteSuggestions(@NonNull List<String> suggestions) {
        autocompleteAdapter.setSuggestions(suggestions);

        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, "Autocomplete: " + suggestions.size() + " suggestions");
        }
    }

    @Override
    public void onAutocompleteError(@NonNull Error error) {
        // Silently ignore autocomplete errors (better UX)
        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, "Autocomplete error (ignored): " + error.getMessage());
        }
    }

    @Override
    public void onQueryTooShort() {
        // Clear suggestions when query < 3 chars
        autocompleteAdapter.clear();
    }

    // ========== SearchResultsAdapter.OnItemClickListener IMPLEMENTATION ==========

    @Override
    public void onItemClick(@NonNull Searchable item) {
        String language = getCurrentLanguage().getCode();

        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, "Item clicked - Type: " + item.getProductType() +
                    ", Name: " + item.getDisplayName(language));
        }

        // Polymorphic navigation based on item type
        if (item instanceof FoodProduct) {
            // Open product details
            Intent intent = new Intent(this, ItemDetailsActivity.class);
            intent.putExtra(ItemDetailsActivity.EXTRA_FOOD_ITEM, item.getSearchableId());

            // Pass meal context if present
            if (returnToMealId != null) {
                intent.putExtra("RETURN_TO_MEAL", returnToMealId);
            }

            startActivity(intent);

            logDebug("Opening product details: " + item.getDisplayName(language));
        } else if (item instanceof Recipe) {
            // TODO: Open recipe details when RecipeDetailsActivity is implemented
            Toast.makeText(this,
                    "Recipe details coming soon: " + item.getDisplayName(language),
                    Toast.LENGTH_SHORT).show();

            logDebug("Recipe clicked (details not yet implemented): " + item.getDisplayName(language));
        } else {
            Log.w(TAG, "Unknown item type clicked: " + item.getClass().getSimpleName());
        }
    }

    @Override
    public void onItemLongClick(@NonNull Searchable item, int position) {
        String language = getCurrentLanguage().getCode();
        logDebug("Long clicked - Type: " + item.getProductType() +
                ", Name: " + item.getDisplayName(language));

        Toast.makeText(this,
                getSafeString(R.string.long_click_hint),
                Toast.LENGTH_SHORT).show();
    }

    // ========== PAGINATION ==========

    /** Timestamp of last loadMore call — prevents rapid-fire triggers from scroll listener */
    private long lastLoadMoreTimestamp = 0;
    private static final long LOAD_MORE_DEBOUNCE_MS = 1000;

    @Override
    public void onLoadMore() {
        if (!lastQuery.isEmpty() && searchManager != null) {
            // Debounce: the scroll listener fires continuously, so prevent
            // calling loadMoreResults more than once per second
            long now = System.currentTimeMillis();
            if (now - lastLoadMoreTimestamp < LOAD_MORE_DEBOUNCE_MS) {
                return;
            }
            lastLoadMoreTimestamp = now;

            logDebug("Load more requested for: " + lastQuery);
            searchManager.loadMoreResults();
        }
    }

    // ========== UI STATE MANAGEMENT ==========

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

        emptyTitle.setText(getSafeString(R.string.empty_initial_title));
        emptyMessage.setText(getSafeString(R.string.empty_initial_message));
    }

    private void showError(@NonNull Error error) {
        progressIndicator.setVisibility(View.GONE);
        recyclerView.setVisibility(View.GONE);
        emptyStateView.setVisibility(View.GONE);
        errorStateView.setVisibility(View.VISIBLE);

        if (errorTitle != null && errorMessage != null) {
            switch (error.getType()) {
                case NETWORK:
                    errorTitle.setText(getSafeString(R.string.error_network_title));
                    errorMessage.setText(getSafeString(R.string.error_network_message));
                    break;
                case NO_DATA:
                    errorTitle.setText(getSafeString(R.string.empty_search_title));
                    errorMessage.setText(error.getMessage());
                    break;
                case SERVER:
                    errorTitle.setText(getSafeString(R.string.error_server_error));
                    errorMessage.setText(error.getMessage());
                    break;
                case RATE_LIMITED:
                    errorTitle.setText(getSafeString(R.string.error_api_limit));
                    errorMessage.setText(error.getMessage());
                    break;
                default:
                    errorTitle.setText(getSafeString(R.string.error_general_title));
                    errorMessage.setText(error.getMessage());
                    break;
            }
        }

        if (tryAgainButton != null) {
            tryAgainButton.setVisibility(error.isRetryable() ? View.VISIBLE : View.GONE);
        }
    }

    // ========== LIFECYCLE MANAGEMENT ==========

    // NEW: Handle back button for drawer
    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onActivityResumed() {
        super.onActivityResumed();

        // Update checked item in drawer
        if (navigationView != null) {
            navigationView.setCheckedItem(R.id.nav_search);
        }

        logDebug("MainActivity resumed");

        // Auto-trigger Ciqual import if DB is missing or stale.
        // Called here (not in Application.onCreate) because startForegroundService()
        // requires the app to be in the foreground — guaranteed once onResume fires.
        triggerCiqualImportIfNeeded();
    }

    /**
     * Starts CiqualImportService silently in the background if the local DB
     * has never been imported or a newer dataset version is available.
     * search() keeps using the ES API as fallback while the import runs.
     */
    private void triggerCiqualImportIfNeeded() {
        if (!CiqualImportService.needsUpdate(this)) return;
        // Retrieve CiqualDataSource from the aggregator and delegate
        try {
            Intent intent = new Intent(this, CiqualImportService.class);
            startForegroundService(intent);
            logDebug("CiqualImportService auto-started");
        } catch (Exception e) {
            logError("Failed to auto-start CiqualImportService", e);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Cancel all searches and clean up
        if (searchManager != null) {
            searchManager.cancel();
            searchManager.cleanup();
        }

        if (productRepository != null) {
            productRepository.cancelCurrentSearch();
        }

        logDebug("MainActivity destroyed");

        // Log cache statistics
        if (productRepository != null && ApiConfig.DEBUG_LOGGING) {
            logDebug(productRepository.getCacheStats());
        }

        if (searchManager != null && ApiConfig.DEBUG_LOGGING) {
            logDebug(searchManager.getSearchStats());
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Cancel searches when pausing
        if (searchManager != null) {
            searchManager.cancel();
        }

        // Dismiss autocomplete dropdown
        if (searchEditText != null) {
            searchEditText.dismissDropDown();
        }
    }

    // ========== UTILITY METHODS ==========

    private void hideKeyboard(@NonNull View view) {
        android.view.inputmethod.InputMethodManager imm =
                (android.view.inputmethod.InputMethodManager)
                        getSystemService(android.content.Context.INPUT_METHOD_SERVICE);

        if (imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    /**
     * Check if search is in progress
     */
    public boolean isSearching() {
        return progressIndicator != null &&
                progressIndicator.getVisibility() == View.VISIBLE;
    }

    /**
     * Get current query
     */
    @NonNull
    public String getCurrentQuery() {
        return lastQuery;
    }
}