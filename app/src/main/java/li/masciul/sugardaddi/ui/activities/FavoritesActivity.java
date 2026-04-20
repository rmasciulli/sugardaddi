package li.masciul.sugardaddi.ui.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import li.masciul.sugardaddi.R;
import li.masciul.sugardaddi.core.enums.ProductType;
import li.masciul.sugardaddi.core.interfaces.Searchable;
import li.masciul.sugardaddi.core.models.FoodProduct;
import li.masciul.sugardaddi.core.models.Recipe;
import li.masciul.sugardaddi.data.database.AppDatabase;
import li.masciul.sugardaddi.data.database.entities.FoodProductEntity;
import li.masciul.sugardaddi.data.database.entities.RecipeEntity;
import li.masciul.sugardaddi.ui.adapters.SearchResultsAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * FavoritesActivity - Browse and search favorited products and recipes
 *
 * DELEGATE ARCHITECTURE v2.0
 *
 * Now backed by {@link SearchResultsAdapter} with pagination disabled.
 * FavoriteItem and FavoritesAdapter have been removed — this activity works
 * directly with {@link Searchable} objects (FoodProduct and Recipe), exactly
 * as the search results screen does. The delegate system automatically selects
 * the correct card layout per item type and data source.
 *
 * Features:
 * - Displays all favorited products and recipes in a unified list
 * - Live search bar for filtering favorites by name
 * - Filter chips: All / Products / Recipes
 * - Multi-type RecyclerView powered by delegate registry (same cards as search)
 * - Click to navigate to item/recipe details
 * - Empty state with contextual messaging
 * - Refreshes on resume (picks up favorite changes from other screens)
 *
 * DATA FLOW:
 * 1. Load favorite products + recipes from Room database on a background thread
 * 2. Convert entities to domain models (FoodProduct / Recipe)
 * 3. Apply type filter and search query
 * 4. Feed filtered List<Searchable> to SearchResultsAdapter
 *
 * @version 2.0 - Delegate architecture
 * @author SugarDaddi Team
 */
public class FavoritesActivity extends BaseActivity
        implements SearchResultsAdapter.OnItemClickListener {

    private static final String TAG = "FavoritesActivity";

    // ========== UI COMPONENTS ==========

    private EditText searchEditText;
    private ImageView clearSearchButton;
    private ChipGroup filterChipGroup;
    private Chip chipAll;
    private Chip chipProducts;
    private Chip chipRecipes;
    private TextView favoritesCountText;
    private RecyclerView favoritesRecyclerView;
    private LinearLayout emptyStateLayout;
    private TextView emptyStateTitle;
    private TextView emptyStateSubtitle;

    // ========== DATA ==========

    /** Delegate-based adapter — reused from search results, pagination disabled */
    private SearchResultsAdapter adapter;

    private AppDatabase database;
    private ExecutorService backgroundExecutor;

    /** All favorites loaded from DB (unfiltered, raw Searchable objects) */
    private List<Searchable> allFavorites = new ArrayList<>();

    /** Currently displayed after applying type filter + search query */
    private List<Searchable> displayedFavorites = new ArrayList<>();

    /** Meal ID to return to — forwarded from MainActivity in add-to-meal mode */
    private String returnToMealId = null;

    /** Current type filter selection */
    private FilterType currentFilter = FilterType.ALL;

    /** Current live search query (lowercased) */
    private String currentSearchQuery = "";

    /**
     * Filter types matching the chip group options in the layout.
     */
    private enum FilterType {
        ALL,
        PRODUCTS,
        RECIPES
    }

    // ========== LIFECYCLE ==========

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_favorites);

        database = AppDatabase.getInstance(this);
        backgroundExecutor = Executors.newSingleThreadExecutor();

        // Capture meal context forwarded from MainActivity (add-to-meal mode)
        returnToMealId = getIntent().getStringExtra("RETURN_TO_MEAL");

        initializeViews();
        setupToolbar();
        setupSearch();
        setupFilterChips();
        setupRecyclerView();

        loadFavorites();
    }

    @Override
    protected void onBaseActivityCreated(@Nullable Bundle savedInstanceState) {
        // Managed in onCreate — nothing needed here
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Refresh on resume: the user may have toggled a favorite in ItemDetailsActivity
        loadFavorites();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (backgroundExecutor != null && !backgroundExecutor.isShutdown()) {
            backgroundExecutor.shutdown();
        }
    }

    // ========== INITIALIZATION ==========

    private void initializeViews() {
        searchEditText       = findViewById(R.id.searchEditText);
        clearSearchButton    = findViewById(R.id.clearSearchButton);
        filterChipGroup      = findViewById(R.id.filterChipGroup);
        chipAll              = findViewById(R.id.chipAll);
        chipProducts         = findViewById(R.id.chipProducts);
        chipRecipes          = findViewById(R.id.chipRecipes);
        favoritesCountText   = findViewById(R.id.favoritesCountText);
        favoritesRecyclerView = findViewById(R.id.favoritesRecyclerView);
        emptyStateLayout     = findViewById(R.id.emptyStateLayout);
        emptyStateTitle      = findViewById(R.id.emptyStateTitle);
        emptyStateSubtitle   = findViewById(R.id.emptyStateSubtitle);
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.menu_favorites);
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupSearch() {
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                currentSearchQuery = s.toString().trim().toLowerCase();
                clearSearchButton.setVisibility(
                        currentSearchQuery.isEmpty() ? View.GONE : View.VISIBLE);
                applyFilters();
            }
        });

        clearSearchButton.setOnClickListener(v -> {
            searchEditText.setText("");
            searchEditText.clearFocus();
        });
    }

    private void setupFilterChips() {
        filterChipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;

            int checkedId = checkedIds.get(0);
            if (checkedId == R.id.chipAll)          currentFilter = FilterType.ALL;
            else if (checkedId == R.id.chipProducts) currentFilter = FilterType.PRODUCTS;
            else if (checkedId == R.id.chipRecipes)  currentFilter = FilterType.RECIPES;

            applyFilters();
        });
    }

    private void setupRecyclerView() {
        // Reuse the same delegate-based adapter as the search results screen.
        // Pagination is explicitly disabled — there is no load-more footer in favorites.
        adapter = new SearchResultsAdapter(this);
        adapter.setPaginationEnabled(false);
        adapter.setOnItemClickListener(this);

        favoritesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        favoritesRecyclerView.setAdapter(adapter);
    }

    // ========== DATA LOADING ==========

    /**
     * Load all favorites from Room on a background thread.
     *
     * Converts FoodProductEntity → FoodProduct and RecipeEntity → Recipe,
     * then rebuilds the allFavorites list as plain Searchable objects.
     * UI update happens on the main thread after loading completes.
     */
    private void loadFavorites() {
        backgroundExecutor.execute(() -> {
            try {
                List<Searchable> favorites = new ArrayList<>();

                // ── Products ──────────────────────────────────────────────────────
                List<FoodProductEntity> productEntities =
                        database.foodProductDao().getFavoriteProducts();
                for (FoodProductEntity entity : productEntities) {
                    FoodProduct product = entity.toFoodProduct();
                    if (product != null) {
                        favorites.add(product); // FoodProduct implements Searchable
                    }
                }

                // ── Recipes ───────────────────────────────────────────────────────
                List<RecipeEntity> recipeEntities =
                        database.recipeDao().getFavoriteRecipes();
                for (RecipeEntity entity : recipeEntities) {
                    Recipe recipe = entity.toRecipe();
                    if (recipe != null) {
                        favorites.add(recipe); // Recipe implements Searchable
                    }
                }

                Log.d(TAG, "Loaded favorites: " + productEntities.size() +
                        " products, " + recipeEntities.size() + " recipes");

                runOnUiThread(() -> {
                    allFavorites = favorites;
                    applyFilters();
                });

            } catch (Exception e) {
                Log.e(TAG, "Error loading favorites", e);
                runOnUiThread(() -> {
                    allFavorites = new ArrayList<>();
                    applyFilters();
                });
            }
        });
    }

    // ========== FILTERING ==========

    /**
     * Apply the current type filter and search query to the full favorites list,
     * then push the result to the adapter.
     *
     * Pipeline:
     *   allFavorites → [type filter] → [name search] → displayedFavorites → adapter
     */
    private void applyFilters() {
        String langCode = getCurrentLanguage().getCode();
        displayedFavorites = new ArrayList<>();

        for (Searchable item : allFavorites) {

            // ── Type filter ───────────────────────────────────────────────────────
            if (currentFilter == FilterType.PRODUCTS
                    && item.getProductType() != ProductType.FOOD) {
                continue;
            }
            if (currentFilter == FilterType.RECIPES
                    && item.getProductType() != ProductType.RECIPE) {
                continue;
            }

            // ── Search filter (case-insensitive name match) ───────────────────────
            if (!currentSearchQuery.isEmpty()) {
                String name = item.getDisplayName(langCode);
                if (name == null || !name.toLowerCase().contains(currentSearchQuery)) {
                    continue;
                }
            }

            displayedFavorites.add(item);
        }

        // Push to adapter — updateItems() also resets pagination state (harmless here)
        adapter.updateItems(displayedFavorites);

        updateCountText();
        updateEmptyState();
    }

    // ========== UI STATE UPDATES ==========

    /**
     * Update the count text.
     * Shows "12 favorites" when unfiltered, "3 of 12" when filtered.
     */
    private void updateCountText() {
        int count = displayedFavorites.size();
        int total = allFavorites.size();

        if (currentSearchQuery.isEmpty() && currentFilter == FilterType.ALL) {
            favoritesCountText.setText(
                    getString(R.string.favorites_count_format, count));
        } else {
            favoritesCountText.setText(
                    getString(R.string.favorites_filtered_count, count, total));
        }
    }

    /**
     * Show or hide the empty state with a contextual message.
     * Distinguishes between "no favorites at all" and "no results for current filter".
     */
    private void updateEmptyState() {
        if (displayedFavorites.isEmpty()) {
            emptyStateLayout.setVisibility(View.VISIBLE);
            favoritesRecyclerView.setVisibility(View.GONE);

            if (allFavorites.isEmpty()) {
                // First-time user or all favorites were removed
                emptyStateTitle.setText(R.string.no_favorites);
                emptyStateSubtitle.setText(R.string.favorites_subtitle);
            } else {
                // Has favorites, but current filter/search returns nothing
                emptyStateTitle.setText(R.string.no_favorites_match);
                emptyStateSubtitle.setText(R.string.no_favorites_match_subtitle);
            }
        } else {
            emptyStateLayout.setVisibility(View.GONE);
            favoritesRecyclerView.setVisibility(View.VISIBLE);
        }
    }

    // ========== SearchResultsAdapter.OnItemClickListener ==========

    /**
     * Unified click handler for both FoodProduct and Recipe items.
     *
     * Uses instanceof to route to the correct detail screen. This mirrors the
     * pattern in MainActivity and is consistent with the polymorphic Searchable API.
     */
    @Override
    public void onItemClick(@NonNull Searchable item) {
        String language = getCurrentLanguage().getCode();

        if (item instanceof FoodProduct) {
            Intent intent = new Intent(this, ItemDetailsActivity.class);
            intent.putExtra(ItemDetailsActivity.EXTRA_FOOD_ITEM, item.getSearchableId());

            // Forward meal context if active (add-to-meal mode from MainActivity)
            if (returnToMealId != null) {
                intent.putExtra("RETURN_TO_MEAL", returnToMealId);
            }

            startActivity(intent);
            Log.d(TAG, "Opening product details: " + item.getDisplayName(language));

        } else if (item instanceof Recipe) {
            // TODO: Navigate to RecipeDetailsActivity when available
            Log.d(TAG, "Recipe clicked (details not yet implemented): "
                    + item.getDisplayName(language));
        }
    }

    @Override
    public void onItemLongClick(@NonNull Searchable item, int position) {
        // Not used in Favorites — reserved for future context menus
    }

    // ========== NAVIGATION ==========

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}