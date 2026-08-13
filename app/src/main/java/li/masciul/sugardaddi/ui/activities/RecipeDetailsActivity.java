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

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.File;
import java.util.concurrent.Executors;

import li.masciul.sugardaddi.R;
import li.masciul.sugardaddi.SugarDaddiApplication;
import li.masciul.sugardaddi.core.models.Error;
import li.masciul.sugardaddi.core.models.Recipe;
import li.masciul.sugardaddi.core.utils.RecipeUrlBuilder;
import li.masciul.sugardaddi.data.database.AppDatabase;
import li.masciul.sugardaddi.data.repository.RecipeRepository;
import li.masciul.sugardaddi.managers.RecipeManager;
import li.masciul.sugardaddi.ui.delegates.detail.CocktailDbRecipeDetailRenderer;
import li.masciul.sugardaddi.ui.delegates.detail.DefaultRecipeDetailRenderer;
import li.masciul.sugardaddi.ui.delegates.detail.DetailRenderer;
import li.masciul.sugardaddi.ui.delegates.detail.DetailRendererRegistry;
import li.masciul.sugardaddi.ui.delegates.detail.FatSecretRecipeDetailRenderer;
import li.masciul.sugardaddi.ui.delegates.detail.MealDbRecipeDetailRenderer;
import li.masciul.sugardaddi.ui.utils.ImagePickerHelper;
import li.masciul.sugardaddi.utils.image.ImageProfile;
import li.masciul.sugardaddi.utils.image.ImageStorageManager;

/**
 * RecipeDetailsActivity - Detail screen for Recipe items.
 *
 * ARCHITECTURE
 * ============
 * Mirrors ProductDetailsActivity exactly in structure, but operates on Recipe
 * objects instead of FoodProduct objects. The same thin-shell pattern applies:
 *
 *   WHAT THIS ACTIVITY OWNS:
 *     - Toolbar setup and navigation
 *     - Menu (favorite, share, video link)
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
 * RENDERER REGISTRATION ORDER (first match wins - most specific first):
 *   1. MealDbRecipeDetailRenderer  - Recipe + DataSourceType.THEMEALDB
 *   2. DefaultRecipeDetailRenderer - Recipe (catch-all for USER and future sources)
 *
 * INTENT EXTRAS
 * =============
 *   EXTRA_RECIPE_ID : String - source-qualified recipe ID (e.g. "THEMEALDB:52772",
 *                    "USER:some-uuid"). Produced by Recipe.getSearchableId().
 *
 * MENU
 * ====
 *   Favorite : toggles border/filled star via RecipeManager.toggleFavorite()
 *   Share     : shares recipe name and source as plain text
 *   Video     : opens videoUrl in browser - shown only when recipe.getVideoUrl() != null
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

    // State views - same IDs as activity_product_details.xml
    private View loadingView;
    private View errorView;
    private TextView errorTitle;
    private TextView errorMessage;

    // Refresh recipe information FAB
    private View refreshFabContainer;
    private FloatingActionButton refreshFab;

    // ========== BUSINESS LOGIC ==========

    private RecipeManager recipeManager;
    private ImagePickerHelper imagePicker;

    // ========== LIFECYCLE ==========

    @Override
    protected void onBaseActivityCreated(Bundle savedInstanceState) {
        // Register ActivityResultLaunchers before onStart() - safe here because
        // onBaseActivityCreated() is called from BaseActivity.onCreate().
        imagePicker = new ImagePickerHelper(this);

        setContentView(R.layout.activity_recipe_details);

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
        rendererRegistry.register(new FatSecretRecipeDetailRenderer(this));
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

        // Retry button - reload the same recipe ID on tap
        View retryButton = findViewById(R.id.retryButton);
        if (retryButton != null) {
            retryButton.setOnClickListener(v -> {
                String recipeId = getIntent().getStringExtra(EXTRA_RECIPE_ID);
                if (recipeId != null) {
                    recipeManager.loadRecipe(recipeId);
                }
            });
        }

        // "Add to Meal" container (present in layout) - hide it:
        // recipe-to-meal composition is a future feature, not implemented yet.
        View addToMealContainer = findViewById(R.id.addToMealContainer);
        if (addToMealContainer != null) {
            addToMealContainer.setVisibility(View.GONE);
        }

        // Refresh recipe information FAB
        refreshFabContainer = findViewById(R.id.refreshFabContainer);
        refreshFab = findViewById(R.id.refreshFab);
        if (refreshFab != null) {
            refreshFab.setOnClickListener(v -> recipeManager.applyPendingRefresh());
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
        if (imagePicker != null) imagePicker.shutdown();

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
        if (refreshFabContainer != null) refreshFabContainer.setVisibility(View.GONE);
    }

    @Override
    public void onRecipeError(@NonNull Error error) {
        logDebug("Recipe load failed: " + error.getMessage());
        if (refreshFabContainer != null) refreshFabContainer.setVisibility(View.GONE);
        showError(error);
    }

    @Override
    public void onRecipeLoading() {
        if (refreshFabContainer != null) refreshFabContainer.setVisibility(View.GONE);
        showLoading();
    }

    @Override
    public void onRefreshAvailable() {
        if (refreshFabContainer != null) refreshFabContainer.setVisibility(View.VISIBLE);
    }

    @Override
    public void onFavoriteStatusChanged(boolean isFavorite) {
        // Refresh the favorite icon in the toolbar
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
            // No renderer registered for this recipe type - programming error, surface it gracefully
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
        // Favorite icon: filled vs border depending on current state
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

        // Web button: see ProductDetailsActivity's equivalent for the
        // rationale - reads the already-resolved sourceUrl, no longer
        // calls RecipeUrlBuilder directly here.
        MenuItem webItem = menu.findItem(R.id.action_open_web);
        if (webItem != null) {
            Recipe recipe = recipeManager.getCurrentRecipe();
            webItem.setVisible(recipe != null
                    && recipe.getSourceUrl(getCurrentLanguage().getCode()) != null);
        }

        // Remove image - shown only when user has set a custom full-size image.
        MenuItem removeImageItem = menu.findItem(R.id.action_remove_image);
        if (removeImageItem != null) {
            Recipe recipe = recipeManager.getCurrentRecipe();
            removeImageItem.setVisible(recipe != null
                    && recipe.getUserImagePath() != null
                    && !recipe.getUserImagePath().trim().isEmpty());
        }

        // Remove thumbnail - shown only when user has set a custom thumbnail.
        MenuItem removeThumbnailItem = menu.findItem(R.id.action_remove_thumbnail);
        if (removeThumbnailItem != null) {
            Recipe recipe = recipeManager.getCurrentRecipe();
            removeThumbnailItem.setVisible(recipe != null
                    && ImageStorageManager.isUserDefinedThumbnail(
                    recipe.getUserThumbnailPath()));
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
        } else if (id == R.id.action_open_web) {
            openInBrowser();
            return true;
        } else if (id == R.id.action_replace_image) {
            showImageSourceDialog(false);
            return true;
        } else if (id == R.id.action_replace_thumbnail) {
            showImageSourceDialog(true);
            return true;
        } else if (id == R.id.action_remove_image) {
            removeImage();
            return true;
        } else if (id == R.id.action_remove_thumbnail) {
            removeThumbnail();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    // ========== IMAGE MANAGEMENT ==========

    /**
     * Shows a dialog asking whether to use the camera or gallery,
     * then launches ImagePickerHelper with the appropriate destination file.
     *
     * @param isThumbnail true when replacing the thumbnail (userThumbnailPath),
     *                    false when replacing the full-size image (userImagePath).
     */
    private void showImageSourceDialog(boolean isThumbnail) {
        Recipe recipe = recipeManager.getCurrentRecipe();
        if (recipe == null) return;

        ImageStorageManager storage =
                ((SugarDaddiApplication) getApplication()).getImageStorageManager();

        File destinationFile;
        ImageProfile profile;

        if (isThumbnail) {
            // Deterministic name: {id}_custom.jpg - co-exists with auto-cached thumbnail.
            destinationFile = storage.getUserThumbnailFile(recipe.getSearchableId());
            profile         = ImageProfile.THUMBNAIL;
        } else {
            destinationFile = storage.getUserProductHeroFile(recipe.getSearchableId());
            profile         = ImageProfile.HERO;
        }

        if (destinationFile == null) {
            Toast.makeText(this, getSafeString(R.string.image_storage_unavailable),
                    Toast.LENGTH_SHORT).show();
            return;
        }

        // Capture finals for lambda capture.
        final File         dest    = destinationFile;
        final ImageProfile prof    = profile;
        final boolean      isThumb = isThumbnail;

        ImagePickerHelper.Callback callback = new ImagePickerHelper.Callback() {
            @Override
            public void onImageReady(@NonNull String localPath) {
                persistImage(localPath, isThumb);
            }

            @Override
            public void onCancelled(@NonNull String reason) {
                logDebug("Image pick cancelled: " + reason);
            }
        };

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(getSafeString(R.string.image_picker_choose_source))
                .setPositiveButton(getSafeString(R.string.image_picker_camera),
                        (d, w) -> imagePicker.showCamera(dest, prof, callback))
                .setNegativeButton(getSafeString(R.string.image_picker_gallery),
                        (d, w) -> imagePicker.showGallery(dest, prof, callback))
                .show();
    }

    /**
     * Persists the picked image path to Room via a targeted DAO update,
     * then reloads the detail view to reflect the change.
     */
    private void persistImage(@NonNull String localPath, boolean isThumbnail) {
        Recipe recipe = recipeManager.getCurrentRecipe();
        if (recipe == null) return;

        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase db = AppDatabase.getInstance(this);
            if (isThumbnail) {
                db.recipeDao().updateUserThumbnailPath(recipe.getSearchableId(), localPath);
            } else {
                db.recipeDao().updateUserImagePath(recipe.getSearchableId(), localPath);
            }
            runOnUiThread(() -> {
                Toast.makeText(this, getSafeString(isThumbnail
                                ? R.string.thumbnail_replaced : R.string.image_replaced),
                        Toast.LENGTH_SHORT).show();
                recipeManager.reloadCurrentFromCache();
            });
        });
    }

    /**
     * Deletes the user-defined full-size image and clears userImagePath in Room.
     */
    private void removeImage() {
        Recipe recipe = recipeManager.getCurrentRecipe();
        if (recipe == null || recipe.getUserImagePath() == null) return;
        final String pathToDelete = recipe.getUserImagePath();
        ImageStorageManager storage =
                ((SugarDaddiApplication) getApplication()).getImageStorageManager();
        Executors.newSingleThreadExecutor().execute(() -> {
            storage.deleteFile(pathToDelete);
            AppDatabase.getInstance(this).recipeDao()
                    .updateUserImagePath(recipe.getSearchableId(), null);
            runOnUiThread(() -> {
                Toast.makeText(this, getSafeString(R.string.image_removed),
                        Toast.LENGTH_SHORT).show();
                recipeManager.reloadCurrentFromCache();
            });
        });
    }

    /**
     * Deletes the user-defined thumbnail and clears userThumbnailPath in Room.
     * The auto-cached thumbnail (thumbnailPath) remains and resumes display.
     */
    private void removeThumbnail() {
        Recipe recipe = recipeManager.getCurrentRecipe();
        if (recipe == null || recipe.getUserThumbnailPath() == null) return;
        final String pathToDelete = recipe.getUserThumbnailPath();
        ImageStorageManager storage =
                ((SugarDaddiApplication) getApplication()).getImageStorageManager();
        Executors.newSingleThreadExecutor().execute(() -> {
            storage.deleteFile(pathToDelete);
            AppDatabase.getInstance(this).recipeDao()
                    .updateUserThumbnailPath(recipe.getSearchableId(), null);
            runOnUiThread(() -> {
                Toast.makeText(this, getSafeString(R.string.thumbnail_removed),
                        Toast.LENGTH_SHORT).show();
                recipeManager.reloadCurrentFromCache();
            });
        });
    }

    // ========== SHARE ==========

    /**
     * Shares the recipe name, source attribution, recipe page URL, and
     * optionally the video URL as plain text.
     *
     * Recipe page URL (TheMealDB / TheCocktailDB) is the primary shareable
     * link - it works for anyone regardless of whether they have the app.
     * Video URL is appended as a secondary link when available.
     */
    private void shareRecipe() {
        Recipe recipe = recipeManager.getCurrentRecipe();
        if (recipe == null) return;

        String language = getCurrentLanguage().getCode();
        StringBuilder shareText = new StringBuilder();

        shareText.append(recipe.getDisplayName(language));

        // Source attribution - "via TheMealDB" etc.
        if (recipe.getDataSource() != null) {
            shareText.append("\n")
                    .append(getSafeString(R.string.share_recipe_source_prefix))
                    .append(" ")
                    .append(recipe.getDataSource().getDisplayName(this));
        }

        // Recipe page URL - primary shareable link
        String recipeUrl = recipe.getSourceUrl(getCurrentLanguage().getCode());
        if (recipeUrl != null) {
            shareText.append("\n").append(recipeUrl);
        }

        // Video URL - secondary link, appended when present
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

    // ========== OPEN IN BROWSER ==========

    /**
     * Opens the recipe's page on TheMealDB or TheCocktailDB in the device browser.
     *
     * Only called when the web button is visible - which only happens when
     * RecipeUrlBuilder.hasWebsiteSupport() returns true for this recipe.
     * Mirrors ProductDetailsActivity.openInBrowser() exactly.
     */
    private void openInBrowser() {
        Recipe recipe = recipeManager.getCurrentRecipe();
        if (recipe == null) return;

        String url = recipe.getSourceUrl(getCurrentLanguage().getCode());
        if (url == null) {
            Toast.makeText(this,
                    getSafeString(R.string.website_not_available),
                    Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(this,
                    getSafeString(R.string.browser_open_failed),
                    Toast.LENGTH_SHORT).show();
            logError("Failed to open recipe URL in browser", e);
        }
    }
}