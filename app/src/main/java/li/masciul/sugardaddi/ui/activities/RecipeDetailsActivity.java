package li.masciul.sugardaddi.ui.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;

import li.masciul.sugardaddi.R;
import li.masciul.sugardaddi.core.models.Error;
import li.masciul.sugardaddi.core.models.Recipe;
import li.masciul.sugardaddi.data.repository.RecipeRepository;
import li.masciul.sugardaddi.managers.RecipeManager;
import li.masciul.sugardaddi.ui.delegates.detail.CocktailDbRecipeDetailRenderer;
import li.masciul.sugardaddi.ui.delegates.detail.DefaultRecipeDetailRenderer;
import li.masciul.sugardaddi.ui.delegates.detail.DetailRenderer;
import li.masciul.sugardaddi.ui.delegates.detail.DetailRendererRegistry;
import li.masciul.sugardaddi.ui.delegates.detail.MealDbRecipeDetailRenderer;

/**
 * RecipeDetailsActivity — Detail screen for Recipe items.
 *
 * ARCHITECTURE
 * ============
 * Mirrors ProductDetailsActivity exactly in structure, but operates on Recipe
 * objects instead of FoodProduct objects. The same thin-shell pattern applies:
 *
 *   WHAT THIS ACTIVITY OWNS:
 *     - Toolbar setup and navigation
 *     - Menu (favourite, share, video link)
 *     - RecipeManager lifecycle (load, cancel)
 *     - State machine: loading / content / error
 *     - DetailRendererRegistry: resolves to the correct recipe renderer
 *
 *   WHAT IT DELEGATES:
 *     - Layout inflation  → DetailRenderer.inflate()
 *     - Data binding      → DetailRenderer.populate()
 *     - Toolbar title     → DetailRenderer.getToolbarTitle()
 *     - Cleanup           → DetailRenderer.destroy()
 *
 * RENDERER REGISTRATION ORDER (first match wins — most specific first):
 *   1. MealDbRecipeDetailRenderer  — Recipe + DataSourceType.THEMEALDB
 *   2. DefaultRecipeDetailRenderer — Recipe (catch-all for USER and future sources)
 *
 * INTENT EXTRAS
 * =============
 *   EXTRA_RECIPE_ID : String — source-qualified recipe ID (e.g. "THEMEALDB:52772",
 *                    "USER:some-uuid"). Produced by Recipe.getSearchableId().
 *
 * MENU
 * ====
 *   Favourite : toggles border/filled star via RecipeManager.toggleFavorite()
 *   Share     : shares recipe name and source as plain text
 *   Video     : opens videoUrl in browser — shown only when recipe.getVideoUrl() != null
 *
 * @version 1.0
 */
public class RecipeDetailsActivity extends BaseActivity
        implements RecipeManager.RecipeListener {

    public static final String EXTRA_RECIPE_ID = "extra_recipe_id";

    private static final String TAG = "RecipeDetailsActivity";

    // ========== RENDERER INFRASTRUCTURE ==========

    /** Registry containing recipe renderers in priority order. */
    private DetailRendererRegistry rendererRegistry;

    /**
     * The currently active renderer.
     * Assigned in displayRecipe(), used in onActivityResumed() and onDestroy().
     */
    private DetailRenderer activeRenderer;

    /**
     * The view inflated by the active renderer.
     * Lives inside rendererContentContainer.
     */
    private View activeRendererView;

    // ========== ACTIVITY-OWNED UI ==========

    /** Container into which the active renderer inflates its layout. */
    private FrameLayout rendererContentContainer;

    // State views — same IDs as activity_product_details.xml
    private View loadingView;
    private View errorView;
    private TextView errorTitle;
    private TextView errorMessage;

    // ========== BUSINESS LOGIC ==========

    private RecipeManager recipeManager;

    // ========== LIFECYCLE ==========

    @Override
    protected void onBaseActivityCreated(Bundle savedInstanceState) {
        setContentView(R.layout.activity_product_details);

        setupToolbar();
        initializeRendererRegistry();
        initializeUIComponents();
        initializeBusinessLogic();

        // Load recipe from intent
        String recipeId = getIntent().getStringExtra(EXTRA_RECIPE_ID);
        if (recipeId != null && !recipeId.trim().isEmpty()) {
            recipeManager.loadRecipe(recipeId);
        } else {
            showError(Error.validation(
                    getSafeString(R.string.error_no_recipe_id), null));
        }

        logDebug("RecipeDetailsActivity v1.0 initialized");
    }

    /**
     * Build and populate the renderer registry.
     * ORDER IS CRITICAL: most specific renderers first, catch-all last.
     */
    private void initializeRendererRegistry() {
        rendererRegistry = new DetailRendererRegistry();
        rendererRegistry.register(new MealDbRecipeDetailRenderer(this));
        rendererRegistry.register(new CocktailDbRecipeDetailRenderer(this));
        rendererRegistry.register(new DefaultRecipeDetailRenderer(this)); // must be last
        logDebug("Recipe renderer registry initialized with "
                + rendererRegistry.size() + " renderers");
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setupToolbarNavigation(toolbar, R.string.recipe_details_title);
    }

    private void initializeUIComponents() {
        rendererContentContainer = findViewById(R.id.rendererContentContainer);
        loadingView              = findViewById(R.id.loadingView);
        errorView                = findViewById(R.id.errorView);
        errorTitle               = findViewById(R.id.errorTitle);
        errorMessage             = findViewById(R.id.errorMessage);

        // Retry button — reload the same recipe ID on tap
        View retryButton = findViewById(R.id.retryButton);
        if (retryButton != null) {
            retryButton.setOnClickListener(v -> {
                String recipeId = getIntent().getStringExtra(EXTRA_RECIPE_ID);
                if (recipeId != null) {
                    recipeManager.loadRecipe(recipeId);
                }
            });
        }

        // "Add to Meal" container (present in layout) — hide it:
        // recipe-to-meal composition is a future feature, not implemented yet.
        View addToMealContainer = findViewById(R.id.addToMealContainer);
        if (addToMealContainer != null) {
            addToMealContainer.setVisibility(View.GONE);
        }
    }

    private void initializeBusinessLogic() {
        RecipeRepository recipeRepository = new RecipeRepository(this);
        recipeManager = new RecipeManager(recipeRepository);
        recipeManager.setListener(this);
    }

    @Override
    protected void onActivityResumed() {
        super.onActivityResumed();
        // If language changed while we were away, repopulate the active renderer
        Recipe recipe = recipeManager.getCurrentRecipe();
        if (recipe != null && activeRenderer != null && activeRendererView != null) {
            String language = getCurrentLanguage().getCode();
            activeRenderer.populate(activeRendererView, recipe, language);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (activeRenderer != null) {
            activeRenderer.destroy();
            activeRenderer = null;
        }

        if (recipeManager != null) {
            recipeManager.cleanup();
        }

        logDebug("RecipeDetailsActivity destroyed");
    }

    // ========== RecipeManager.RecipeListener IMPLEMENTATION ==========

    @Override
    public void onRecipeLoaded(@NonNull Recipe recipe) {
        String language = getCurrentLanguage().getCode();
        logDebug("Recipe loaded: " + recipe.getDisplayName(language)
                + " [source=" + recipe.getDataSource() + "]");
        displayRecipe(recipe, language);

        // Invalidate the options menu so onPrepareOptionsMenu() can show/hide
        // the video button now that we have recipe data.
        invalidateOptionsMenu();
    }

    @Override
    public void onRecipeError(@NonNull Error error) {
        logDebug("Recipe load failed: " + error.getMessage());
        showError(error);
    }

    @Override
    public void onRecipeLoading() {
        showLoading();
    }

    @Override
    public void onFavoriteStatusChanged(boolean isFavorite) {
        // Refresh the favourite icon in the toolbar
        invalidateOptionsMenu();
    }

    @Override
    public void onFavoriteToggled(boolean newStatus, @NonNull String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        invalidateOptionsMenu();
    }

    @Override
    public void onFavoriteError(@NonNull String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    // ========== DISPLAY ==========

    /**
     * Resolve a renderer, inflate its layout, and populate it with recipe data.
     *
     * Mirrors ProductDetailsActivity.displayProduct() step for step:
     *   1. Resolve renderer via registry
     *   2. Destroy previous renderer (prevents TextWatcher leaks etc.)
     *   3. Clear the container
     *   4. Inflate new renderer layout
     *   5. Add to container
     *   6. Populate with data
     *   7. Cache renderer + view for onActivityResumed()
     *   8. Update toolbar title
     *   9. Show content
     */
    private void displayRecipe(@NonNull Recipe recipe, @NonNull String language) {
        // 1. Resolve renderer
        DetailRenderer renderer = rendererRegistry.resolveOrNull(recipe);
        if (renderer == null) {
            // No renderer registered for this recipe type — programming error, surface it gracefully
            Log.e(TAG, "No renderer found for recipe source: " + recipe.getDataSource());
            showError(Error.validation(getSafeString(R.string.error_loading_recipe), null));
            return;
        }
        logDebug("Dispatching to renderer: " + renderer.getClass().getSimpleName());

        // 2. Destroy previous renderer if switching to a different one
        if (activeRenderer != null && activeRenderer != renderer) {
            activeRenderer.destroy();
        }

        // 3. Clear the container
        rendererContentContainer.removeAllViews();

        // 4. Inflate
        LayoutInflater inflater = LayoutInflater.from(this);
        View rendererView = renderer.inflate(inflater, rendererContentContainer);

        // 5. Add to container
        rendererContentContainer.addView(rendererView);

        // 6. Populate
        renderer.populate(rendererView, recipe, language);

        // 7. Cache
        activeRenderer    = renderer;
        activeRendererView = rendererView;

        // 8. Update toolbar title from renderer
        String rendererTitle = renderer.getToolbarTitle(recipe, language);
        if (rendererTitle != null && getSupportActionBar() != null) {
            getSupportActionBar().setTitle(rendererTitle);
        }

        // 9. Show content
        showContent();
    }

    // ========== STATE MANAGEMENT ==========

    private void showLoading() {
        rendererContentContainer.setVisibility(View.GONE);
        loadingView.setVisibility(View.VISIBLE);
        errorView.setVisibility(View.GONE);
    }

    private void showContent() {
        rendererContentContainer.setVisibility(View.VISIBLE);
        loadingView.setVisibility(View.GONE);
        errorView.setVisibility(View.GONE);
    }

    private void showError(@NonNull Error error) {
        rendererContentContainer.setVisibility(View.GONE);
        loadingView.setVisibility(View.GONE);
        errorView.setVisibility(View.VISIBLE);

        if (errorTitle == null || errorMessage == null) return;

        errorTitle.setTextColor(getTextPrimaryColor());
        errorMessage.setTextColor(getTextSecondaryColor());

        switch (error.getType()) {
            case NO_DATA:
                errorTitle.setText(getSafeString(R.string.recipe_not_found));
                errorMessage.setText(error.getMessage());
                break;
            case NETWORK:
                errorTitle.setText(getSafeString(R.string.error_network_title));
                errorMessage.setText(getSafeString(R.string.error_network_message));
                break;
            case SERVER:
                errorTitle.setText(getSafeString(R.string.error_server_error));
                errorMessage.setText(error.getMessage());
                break;
            default:
                errorTitle.setText(getSafeString(R.string.error_loading_recipe));
                errorMessage.setText(error.getMessage());
                break;
        }
    }

    // ========== MENU ==========

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_recipe_details, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        // Favourite icon: filled vs border depending on current state
        MenuItem favoriteItem = menu.findItem(R.id.action_favorite);
        if (favoriteItem != null) {
            boolean isFavorite = recipeManager.isFavorite();
            favoriteItem.setIcon(isFavorite
                    ? R.drawable.ic_favorite_rounded_filled
                    : R.drawable.ic_favorite_rounded_border);
            favoriteItem.setTitle(isFavorite
                    ? getSafeString(R.string.remove_from_favorites)
                    : getSafeString(R.string.add_to_favorites));
        }

        // Video button: visible only when the loaded recipe has a video URL
        MenuItem videoItem = menu.findItem(R.id.action_video);
        if (videoItem != null) {
            Recipe recipe = recipeManager.getCurrentRecipe();
            boolean hasVideo = recipe != null
                    && recipe.getVideoUrl() != null
                    && !recipe.getVideoUrl().trim().isEmpty();
            videoItem.setVisible(hasVideo);
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == android.R.id.home) {
            finish();
            return true;
        } else if (id == R.id.action_favorite) {
            recipeManager.toggleFavorite();
            return true;
        } else if (id == R.id.action_share) {
            shareRecipe();
            return true;
        } else if (id == R.id.action_video) {
            openVideo();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    // ========== SHARE ==========

    /**
     * Share the recipe name, source, and video URL (if present) as plain text.
     */
    private void shareRecipe() {
        Recipe recipe = recipeManager.getCurrentRecipe();
        if (recipe == null) return;

        String language = getCurrentLanguage().getCode();
        StringBuilder shareText = new StringBuilder();

        shareText.append(recipe.getDisplayName(language));

        // Append source attribution (e.g. "via TheMealDB")
        if (recipe.getDataSource() != null) {
            shareText.append("\n")
                    .append(getSafeString(R.string.share_recipe_source_prefix))
                    .append(" ")
                    .append(recipe.getDataSource().getDisplayName(this));
        }

        // Append video URL if present
        if (recipe.getVideoUrl() != null && !recipe.getVideoUrl().trim().isEmpty()) {
            shareText.append("\n").append(recipe.getVideoUrl());
        }

        shareText.append("\n\n").append(getSafeString(R.string.shared_via_sugardaddi));

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, shareText.toString());
        shareIntent.putExtra(Intent.EXTRA_SUBJECT,
                getSafeString(R.string.share_recipe_subject,
                        recipe.getDisplayName(language)));

        startActivity(Intent.createChooser(shareIntent,
                getSafeString(R.string.share_via)));
    }

    // ========== VIDEO ==========

    /**
     * Open the recipe's video URL in the device browser.
     * Only called when videoUrl is confirmed non-null (menu item is hidden otherwise).
     */
    private void openVideo() {
        Recipe recipe = recipeManager.getCurrentRecipe();
        if (recipe == null || recipe.getVideoUrl() == null) return;

        try {
            startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse(recipe.getVideoUrl())));
        } catch (Exception e) {
            Toast.makeText(this,
                    getSafeString(R.string.browser_open_failed),
                    Toast.LENGTH_SHORT).show();
            logError("Failed to open video URL", e);
        }
    }
}