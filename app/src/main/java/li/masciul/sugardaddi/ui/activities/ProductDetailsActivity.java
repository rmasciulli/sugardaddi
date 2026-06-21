package li.masciul.sugardaddi.ui.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.widget.Toolbar;

import androidx.annotation.NonNull;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.File;
import java.util.concurrent.Executors;

import li.masciul.sugardaddi.R;
import li.masciul.sugardaddi.SugarDaddiApplication;
import li.masciul.sugardaddi.core.models.Error;
import li.masciul.sugardaddi.core.models.FoodProduct;
import li.masciul.sugardaddi.core.utils.ProductUrlBuilder;
import li.masciul.sugardaddi.data.database.AppDatabase;
import li.masciul.sugardaddi.data.network.NetworkManager;
import li.masciul.sugardaddi.data.repository.MealRepository;
import li.masciul.sugardaddi.data.repository.ProductRepository;
import li.masciul.sugardaddi.managers.ProductManager;
import li.masciul.sugardaddi.ui.delegates.detail.CiqualProductDetailRenderer;
import li.masciul.sugardaddi.ui.delegates.detail.DefaultProductDetailRenderer;
import li.masciul.sugardaddi.ui.delegates.detail.DetailRenderer;
import li.masciul.sugardaddi.ui.delegates.detail.DetailRendererRegistry;
import li.masciul.sugardaddi.ui.delegates.detail.OffProductDetailRenderer;
import li.masciul.sugardaddi.core.models.FoodPortion;
import li.masciul.sugardaddi.core.models.ServingSize;
import li.masciul.sugardaddi.core.enums.Unit;
import li.masciul.sugardaddi.ui.delegates.detail.USDAProductDetailRenderer;
import li.masciul.sugardaddi.ui.utils.ImagePickerHelper;
import li.masciul.sugardaddi.utils.image.ImageProcessor;
import li.masciul.sugardaddi.utils.image.ImageStorageManager;

/**
 * ProductDetailsActivity - REFACTORED THIN SHELL (v5.1)
 *
 * ARCHITECTURE CHANGE (v4.0 → v5.0):
 * This activity no longer contains any product display logic.
 * All rendering is delegated to the appropriate DetailRenderer implementation.
 *
 * WHAT THIS ACTIVITY OWNS:
 *   - Toolbar setup and navigation
 *   - Menu (favorites, share, open in browser)
 *   - ProductManager lifecycle (load, refresh, cancel)
 *   - State machine: loading / content / error
 *   - Add-to-meal flow
 *   - DetailRendererRegistry: finds and dispatches to the right renderer
 *
 * WHAT IT DELEGATES:
 *   - Layout inflation     → DetailRenderer.inflate()
 *   - Data binding         → DetailRenderer.populate()
 *   - Amount changes       → DetailRenderer.onAmountChanged()
 *   - Toolbar title        → DetailRenderer.getToolbarTitle()
 *   - Cleanup              → DetailRenderer.destroy()
 *
 * RENDERER REGISTRATION ORDER (first match wins - most specific first):
 *   1. OffProductDetailRenderer     - FoodProduct + OPENFOODFACTS
 *   2. CiqualProductDetailRenderer  - FoodProduct + CIQUAL
 *   3. DefaultProductDetailRenderer - FoodProduct (catch-all)
 *
 * INTENT EXTRAS:
 *   EXTRA_FOOD_ITEM              : String - product ID to load
 *   EXTRA_FALLBACK_CATEGORY_EN   : String - search-result category (EN), used if detail has none
 *   EXTRA_FALLBACK_CATEGORY_FR   : String - search-result category (FR), used if detail has none
 *   EXTRA_FALLBACK_NUTRISCORE    : String - search-result NutriScore, used if detail has none
 *   EXTRA_FALLBACK_ECOSCORE      : String - search-result EcoScore, used if detail has none
 *   RETURN_TO_MEAL               : String - meal ID to return to after "Add to Meal"
 *
 * @version 5.1 - added search-result fallback extras
 */
public class ProductDetailsActivity extends BaseActivity implements ProductManager.ProductListener {

    public static final String EXTRA_FOOD_ITEM = "extra_food_item";

    /**
     * Fallback data passed from the search result (MainActivity).
     * Used when the detail API returns incomplete data - e.g. OFF v2 sometimes
     * omits ecoscore_data.agribalyse even when Searchalicious had it.
     * These are FALLBACKS: only applied when the loaded product has no value.
     */
    public static final String EXTRA_FALLBACK_CATEGORY_EN = "extra_fallback_category_en";
    public static final String EXTRA_FALLBACK_CATEGORY_FR = "extra_fallback_category_fr";
    public static final String EXTRA_FALLBACK_NUTRISCORE  = "extra_fallback_nutriscore";
    public static final String EXTRA_FALLBACK_ECOSCORE    = "extra_fallback_ecoscore";

    private static final String TAG = "ProductDetailsActivity";

    // ========== RENDERER INFRASTRUCTURE ==========

    /** Registry containing all registered detail renderers in priority order */
    private DetailRendererRegistry rendererRegistry;

    /**
     * The currently active renderer.
     * Assigned in displayProduct(), cleared in showLoading()/showError().
     * Used to forward onAmountChanged events and to call destroy() on cleanup.
     */
    private DetailRenderer activeRenderer;

    /**
     * The view inflated by the active renderer.
     * Lives inside rendererContentContainer.
     */
    private View activeRendererView;

    // ========== ACTIVITY-OWNED UI ==========

    /** Container into which the active renderer inflates its layout */
    private FrameLayout rendererContentContainer;

    // Error state views
    private View loadingView;
    private View errorView;
    private TextView errorTitle;
    private TextView errorMessage;

    // "Add to Meal" bottom bar (shown only in meal context)
    private LinearLayout addToMealContainer;

    // Refresh product information FAB
    private View refreshFabContainer;
    private FloatingActionButton refreshFab;

    // ========== BUSINESS LOGIC ==========

    private ProductManager productManager;
    private ImagePickerHelper imagePicker;
    private ProductRepository productRepository;

    // Meal context - set when launched with RETURN_TO_MEAL intent extra
    private String returnToMealId = null;

    // ========== LIFECYCLE ==========

    @Override
    protected void onBaseActivityCreated(Bundle savedInstanceState) {
        // Register ActivityResultLaunchers before onStart() - safe here because
        // onBaseActivityCreated() is called from BaseActivity.onCreate().
        imagePicker = new ImagePickerHelper(this);

        setContentView(R.layout.activity_product_details);

        setupToolbar();
        initializeRendererRegistry();
        initializeUIComponents();
        initializeBusinessLogic();
        setupMealContext();

        // Load product from intent
        String productId = getIntent().getStringExtra(EXTRA_FOOD_ITEM);
        if (productId != null && !productId.trim().isEmpty()) {
            productManager.loadProduct(productId);
        } else {
            showError(Error.network(getSafeString(R.string.error_no_product_id), null));
        }

        logDebug("ProductDetailsActivity v5.1 initialized with DetailRendererRegistry");
    }

    /**
     * Build and populate the renderer registry.
     * ORDER IS CRITICAL: most specific renderers first, catch-all last.
     */
    private void initializeRendererRegistry() {
        rendererRegistry = new DetailRendererRegistry();
        rendererRegistry.register(new OffProductDetailRenderer(this));
        rendererRegistry.register(new CiqualProductDetailRenderer(this));
        rendererRegistry.register(new USDAProductDetailRenderer(this));
        rendererRegistry.register(new DefaultProductDetailRenderer(this));  // must be last
        logDebug("Renderer registry initialized with " + rendererRegistry.size() + " renderers");
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setupToolbarNavigation(toolbar, R.string.product_details_title);
    }

    private void initializeUIComponents() {
        rendererContentContainer = findViewById(R.id.rendererContentContainer);
        loadingView               = findViewById(R.id.loadingView);
        errorView                 = findViewById(R.id.errorView);
        errorTitle                = findViewById(R.id.errorTitle);
        errorMessage              = findViewById(R.id.errorMessage);
        addToMealContainer        = findViewById(R.id.addToMealContainer);

        // Retry button in error state
        View retryButton = findViewById(R.id.retryButton);
        if (retryButton != null) {
            retryButton.setOnClickListener(v -> productManager.refreshProduct());
        }

        // Refresh product information FAB
        refreshFabContainer = findViewById(R.id.refreshFabContainer);
        refreshFab = findViewById(R.id.refreshFab);
        if (refreshFab != null) {
            refreshFab.setOnClickListener(v -> productManager.applyPendingRefresh());
        }
    }

    private void initializeBusinessLogic() {
        NetworkManager networkManager = NetworkManager.getInstance(this);
        productRepository = new ProductRepository(networkManager, this);
        productManager = new ProductManager(productRepository);
        productManager.setListener(this);
    }

    /**
     * Check if this activity was launched from a MealDetailsActivity ("Add to meal" flow).
     * If so, show the Add to Meal bottom bar.
     */
    private void setupMealContext() {
        returnToMealId = getIntent().getStringExtra("RETURN_TO_MEAL");
        if (returnToMealId != null && addToMealContainer != null) {
            addToMealContainer.setVisibility(View.VISIBLE);

            View btnAddToMeal = addToMealContainer.findViewById(R.id.btnAddToMeal);
            if (btnAddToMeal != null) {
                btnAddToMeal.setOnClickListener(v -> addToMeal());
            }
        }
    }

    @Override
    protected void onActivityResumed() {
        super.onActivityResumed();
        // If the language changed while we were away, repopulate the active renderer
        FoodProduct product = productManager.getCurrentProduct();
        if (product != null && activeRenderer != null && activeRendererView != null) {
            String language = getCurrentLanguage().getCode();
            activeRenderer.populate(activeRendererView, product, language);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (imagePicker != null) imagePicker.shutdown();

        // Destroy the active renderer to release any held resources
        if (activeRenderer != null) {
            activeRenderer.destroy();
            activeRenderer = null;
        }

        if (productManager != null) {
            productManager.cancelOperations();
        }

        logDebug("ProductDetailsActivity destroyed, renderer cleaned up");
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (productManager != null) {
            productManager.cancelOperations();
        }
    }

    // ========== ProductManager.ProductListener IMPLEMENTATION ==========

    @Override
    public void onProductLoaded(FoodProduct product) {
        String language = getCurrentLanguage().getCode();
        logDebug("Product loaded: " + product.getDisplayName(language)
                + " [source=" + product.getDataSource() + "]");

        // Apply search-result fallback data before rendering.
        // OFF v2 sometimes returns incomplete data (e.g. no agribalyse in ecoscore_data).
        // Fallback values were passed as Intent extras from MainActivity, sourced from the
        // Searchalicious search result. Only applied when the loaded product has no value.
        applySearchResultFallbacks(product);

        displayProduct(product, language);
        invalidateOptionsMenu(); // Refresh menu (favorite icon, web button)
        if (refreshFabContainer != null) refreshFabContainer.setVisibility(View.GONE);
    }

    @Override
    public void onProductError(Error error) {
        logError("Product loading failed: " + error.getType() + " - " + error.getMessage(), null);
        if (refreshFabContainer != null) refreshFabContainer.setVisibility(View.GONE);
        showError(error);
    }

    @Override
    public void onProductLoading() {
        if (refreshFabContainer != null) refreshFabContainer.setVisibility(View.GONE);
        showLoading();
    }

    @Override
    public void onRefreshAvailable() {
        if (refreshFabContainer != null) refreshFabContainer.setVisibility(View.VISIBLE);
    }

    @Override
    public void onFavoriteStatusChanged(boolean isFavorite) {
        invalidateOptionsMenu();
    }

    @Override
    public void onFavoriteToggled(boolean newStatus, String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        invalidateOptionsMenu();
    }

    @Override
    public void onFavoriteError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        logError("Favorite operation failed: " + message, null);
    }

    // ========== SEARCH RESULT FALLBACK ==========

    /**
     * Apply search-result fallback data to a product loaded from the detail API.
     *
     * When OFF v2 returns incomplete data (e.g. no agribalyse category, missing scores),
     * we use the data already known from the Searchalicious search result, which was
     * passed as Intent extras by MainActivity.
     *
     * This is purely additive - only fills fields that are null/empty in the loaded product.
     * Never overwrites data that the detail API did return.
     *
     * @param product The product just loaded by the detail API (modified in-place if needed)
     */
    private void applySearchResultFallbacks(FoodProduct product) {
        if (product == null) return;

        Intent intent = getIntent();

        // Category fallback (EN)
        String fallbackCategoryEn = intent.getStringExtra(EXTRA_FALLBACK_CATEGORY_EN);
        if (fallbackCategoryEn != null && !fallbackCategoryEn.trim().isEmpty()) {
            String current = product.getCategoriesText("en");
            if (current == null || current.trim().isEmpty()) {
                product.setCategoriesText(fallbackCategoryEn.trim(), "en");
                logDebug("Applied fallback category EN: " + fallbackCategoryEn);
            }
        }

        // Category fallback (FR)
        String fallbackCategoryFr = intent.getStringExtra(EXTRA_FALLBACK_CATEGORY_FR);
        if (fallbackCategoryFr != null && !fallbackCategoryFr.trim().isEmpty()) {
            String current = product.getCategoriesText("fr");
            if (current == null || current.trim().isEmpty()) {
                product.setCategoriesText(fallbackCategoryFr.trim(), "fr");
                logDebug("Applied fallback category FR: " + fallbackCategoryFr);
            }
        }

        // NutriScore fallback
        String fallbackNutriScore = intent.getStringExtra(EXTRA_FALLBACK_NUTRISCORE);
        if (fallbackNutriScore != null && !fallbackNutriScore.trim().isEmpty()) {
            if (product.getNutriScore() == null || product.getNutriScore().trim().isEmpty()) {
                product.setNutriScore(fallbackNutriScore.trim());
                logDebug("Applied fallback NutriScore: " + fallbackNutriScore);
            }
        }

        // EcoScore fallback
        String fallbackEcoScore = intent.getStringExtra(EXTRA_FALLBACK_ECOSCORE);
        if (fallbackEcoScore != null && !fallbackEcoScore.trim().isEmpty()) {
            if (product.getEcoScore() == null || product.getEcoScore().trim().isEmpty()) {
                product.setEcoScore(fallbackEcoScore.trim());
                logDebug("Applied fallback EcoScore: " + fallbackEcoScore);
            }
        }
    }

    // ========== PRODUCT DISPLAY - RENDERER DISPATCH ==========

    /**
     * Core rendering method:
     *   1. Resolve the correct renderer via the registry
     *   2. Destroy the previously active renderer (if any)
     *   3. Remove the old renderer view from the container
     *   4. Inflate the new renderer's layout
     *   5. Add it to the container
     *   6. Populate it with data
     *   7. Update the toolbar title
     *   8. Show the content state
     */
    private void displayProduct(@NonNull FoodProduct product, @NonNull String language) {
        // 1. Resolve renderer
        DetailRenderer renderer = rendererRegistry.resolve(product);
        logDebug("Dispatching to renderer: " + renderer.getClass().getSimpleName());

        // 2. Destroy previous renderer if different (prevents leak from old TextWatchers etc.)
        if (activeRenderer != null && activeRenderer != renderer) {
            activeRenderer.destroy();
        }

        // 3. Clear the container
        rendererContentContainer.removeAllViews();

        // 4. Inflate new renderer layout
        LayoutInflater inflater = LayoutInflater.from(this);
        View rendererView = renderer.inflate(inflater, rendererContentContainer);

        // 5. Add to container
        rendererContentContainer.addView(rendererView);

        // 6. Populate with data
        renderer.populate(rendererView, product, language);

        // 7. Cache active renderer and view for future calls (onAmountChanged, onResume)
        activeRenderer = renderer;
        activeRendererView = rendererView;

        // 8. Update toolbar title from renderer (falls back to default string if null)
        String rendererTitle = renderer.getToolbarTitle(product, language);
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

    private void showError(Error error) {
        rendererContentContainer.setVisibility(View.GONE);
        loadingView.setVisibility(View.GONE);
        errorView.setVisibility(View.VISIBLE);

        if (errorTitle != null && errorMessage != null) {
            errorTitle.setTextColor(getTextPrimaryColor());
            errorMessage.setTextColor(getTextSecondaryColor());

            switch (error.getType()) {
                case NO_DATA:
                    errorTitle.setText(getSafeString(R.string.product_not_found));
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
                    errorTitle.setText(getSafeString(R.string.error_loading_product));
                    errorMessage.setText(error.getMessage());
                    break;
            }
        }
    }

    // ========== MENU ==========

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_product_details, menu);

        // Favorite icon: filled vs outline depending on current state
        MenuItem favoriteItem = menu.findItem(R.id.action_favorite);
        if (favoriteItem != null) {
            boolean isFavorite = productManager.isFavorite();
            favoriteItem.setIcon(isFavorite
                    ? R.drawable.ic_favorite_rounded_filled
                    : R.drawable.ic_favorite_rounded_border);
            favoriteItem.setTitle(isFavorite
                    ? getSafeString(R.string.remove_from_favorites)
                    : getSafeString(R.string.add_to_favorites));
        }

        // Web button: show only if the product's data source has a website
        MenuItem webItem = menu.findItem(R.id.action_open_web);
        if (webItem != null) {
            FoodProduct product = productManager.getCurrentProduct();
            if (product != null) {
                boolean hasWebsite = ProductUrlBuilder.hasWebsiteSupport(
                        product.getSourceIdentifier());
                webItem.setVisible(hasWebsite);
            } else {
                webItem.setVisible(false);
            }
        }

        // Remove image - shown only when user has set a custom full-size image.
        MenuItem removeImageItem = menu.findItem(R.id.action_remove_image);
        if (removeImageItem != null) {
            FoodProduct product = productManager.getCurrentProduct();
            removeImageItem.setVisible(product != null
                    && product.getUserImagePath() != null
                    && !product.getUserImagePath().trim().isEmpty());
        }

        // Remove thumbnail - shown only when user has set a custom thumbnail.
        MenuItem removeThumbnailItem = menu.findItem(R.id.action_remove_thumbnail);
        if (removeThumbnailItem != null) {
            FoodProduct product = productManager.getCurrentProduct();
            removeThumbnailItem.setVisible(product != null
                    && ImageStorageManager.isUserDefinedThumbnail(
                    product.getUserThumbnailPath()));
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == android.R.id.home) {
            finish();
            return true;
        } else if (id == R.id.action_favorite) {
            productManager.toggleFavorite();
            return true;
        } else if (id == R.id.action_share) {
            shareProduct();
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
        FoodProduct product = productManager.getCurrentProduct();
        if (product == null) return;

        ImageStorageManager storage =
                ((SugarDaddiApplication) getApplication()).getImageStorageManager();

        // Construct the destination file - caller's responsibility per ImagePickerHelper contract.
        File destinationFile;
        int  maxDimension;
        int  jpegQuality;

        if (isThumbnail) {
            // Deterministic name: {id}_custom.jpg - co-exists with auto-cached thumbnail.
            destinationFile = storage.getUserThumbnailFile(product.getSearchableId());
            maxDimension    = ImageProcessor.MAX_DIMENSION_THUMBNAIL;
            jpegQuality     = ImageProcessor.JPEG_QUALITY_THUMBNAIL;
        } else {
            destinationFile = storage.getUserProductHeroFile(product.getSearchableId());
            maxDimension    = ImageProcessor.MAX_DIMENSION_HERO;
            jpegQuality     = ImageProcessor.JPEG_QUALITY_HERO;
        }

        if (destinationFile == null) {
            Toast.makeText(this, getSafeString(R.string.image_storage_unavailable),
                    Toast.LENGTH_SHORT).show();
            return;
        }

        // Capture finals for lambda capture.
        final File   dest     = destinationFile;
        final int    maxDim   = maxDimension;
        final int    quality  = jpegQuality;
        final boolean isThumb = isThumbnail;

        ImagePickerHelper.Callback callback = new ImagePickerHelper.Callback() {
            @Override
            public void onImageReady(@NonNull String localPath) {
                persistImage(localPath, isThumb);
            }

            @Override
            public void onCancelled(@NonNull String reason) {
                // No UI feedback for plain cancellation - user intentionally exited.
                logDebug("Image pick cancelled: " + reason);
            }
        };

        // Ask camera vs gallery.
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(getSafeString(R.string.image_picker_choose_source))
                .setPositiveButton(getSafeString(R.string.image_picker_camera),
                        (d, w) -> imagePicker.showCamera(dest, maxDim, quality, callback))
                .setNegativeButton(getSafeString(R.string.image_picker_gallery),
                        (d, w) -> imagePicker.showGallery(dest, maxDim, quality, callback))
                .show();
    }

    /**
     * Persists the picked image path to Room via a targeted DAO update,
     * then reloads the detail view to reflect the change.
     *
     * @param localPath   Absolute path delivered by ImagePickerHelper.
     * @param isThumbnail true → update userThumbnailPath; false → update userImagePath.
     */
    private void persistImage(@NonNull String localPath, boolean isThumbnail) {
        FoodProduct product = productManager.getCurrentProduct();
        if (product == null) return;

        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase db = AppDatabase.getInstance(this);
            if (isThumbnail) {
                db.foodProductDao().updateUserThumbnailPath(product.getSearchableId(), localPath);
            } else {
                db.foodProductDao().updateUserImagePath(product.getSearchableId(), localPath);
            }
            runOnUiThread(() -> {
                Toast.makeText(this,
                        getSafeString(isThumbnail
                                ? R.string.thumbnail_replaced
                                : R.string.image_replaced),
                        Toast.LENGTH_SHORT).show();
                if (isThumbnail) {
                    productManager.updateLocalImagePaths(null, localPath);
                } else {
                    productManager.updateLocalImagePaths(localPath, null);
                }
                invalidateOptionsMenu();
            });
        });
    }

    /**
     * Deletes the user-defined full-size image and clears userImagePath in Room.
     * Only called when userImagePath is confirmed non-null (menu item is hidden otherwise).
     */
    private void removeImage() {
        FoodProduct product = productManager.getCurrentProduct();
        if (product == null || product.getUserImagePath() == null) return;

        // Capture path before clearing - currentProduct.getUserImagePath()
        // will be null by the time runOnUiThread() fires.
        final String pathToDelete = product.getUserImagePath();

        ImageStorageManager storage =
                ((SugarDaddiApplication) getApplication()).getImageStorageManager();

        // Clear in-memory state immediately on the main thread so that
        // onActivityResumed() (triggered by menu dismissal) renders the
        // correct state before the background thread completes.
        productManager.updateLocalImagePaths(null, null);

        // Move deleteFile() to the background thread alongside the DAO update -
        // keeps the entire remove operation atomic and avoids a race with
        // onActivityResumed() which re-renders with the stale product if
        // deleteFile() runs on the main thread before updateLocalImagePaths() fires.
        Executors.newSingleThreadExecutor().execute(() -> {
            storage.deleteFile(pathToDelete);
            AppDatabase.getInstance(this)
                    .foodProductDao()
                    .updateUserImagePath(product.getSearchableId(), null);
            runOnUiThread(() -> {
                Toast.makeText(this,
                        getSafeString(R.string.image_removed),
                        Toast.LENGTH_SHORT).show();
                invalidateOptionsMenu();
            });
        });
    }

    /**
     * Deletes the user-defined thumbnail and clears userThumbnailPath in Room.
     * The auto-cached thumbnail (thumbnailPath) remains on disk and resumes display.
     * Only called when userThumbnailPath is confirmed non-null.
     */
    private void removeThumbnail() {
        FoodProduct product = productManager.getCurrentProduct();
        if (product == null || product.getUserThumbnailPath() == null) return;

        // Capture paths before clearing - both will be null by the time
        // runOnUiThread() fires so we capture them here on the main thread.
        final String pathToDelete       = product.getUserThumbnailPath();
        final String currentUserImage   = product.getUserImagePath();

        ImageStorageManager storage =
                ((SugarDaddiApplication) getApplication()).getImageStorageManager();

        // deleteFile() moved to background thread - keeps the entire remove
        // operation atomic and avoids a race with onActivityResumed().
        Executors.newSingleThreadExecutor().execute(() -> {
            storage.deleteFile(pathToDelete);
            AppDatabase.getInstance(this)
                    .foodProductDao()
                    .updateUserThumbnailPath(product.getSearchableId(), null);
            runOnUiThread(() -> {
                Toast.makeText(this,
                        getSafeString(R.string.thumbnail_removed),
                        Toast.LENGTH_SHORT).show();
                // Preserve userImagePath - we're only removing the thumbnail.
                productManager.updateLocalImagePaths(currentUserImage, null);
                invalidateOptionsMenu();
            });
        });
    }

    // ========== SHARE ==========

    /**
     * Share product name, brand, and basic energy info as plain text.
     */
    private void shareProduct() {
        FoodProduct product = productManager.getCurrentProduct();
        if (product == null) return;

        String language = getCurrentLanguage().getCode();
        StringBuilder shareText = new StringBuilder();
        shareText.append(getSafeString(R.string.share_product_prefix))
                .append(" ")
                .append(product.getDisplayName(language));

        String brand = product.getBrand(language);
        if (brand != null && !brand.trim().isEmpty()) {
            shareText.append(" - ").append(brand);
        }

        if (product.hasNutritionData() && product.getNutrition().getEnergyKcal() != null) {
            shareText.append(String.format("\n\n• %s: %.0f kcal",
                    getSafeString(R.string.nutrient_energy),
                    product.getNutrition().getEnergyKcal()));
        }

        shareText.append("\n\n").append(getSafeString(R.string.shared_via_sugardaddi));

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, shareText.toString());
        shareIntent.putExtra(Intent.EXTRA_SUBJECT,
                getSafeString(R.string.share_product_subject, product.getDisplayName(language)));

        startActivity(Intent.createChooser(shareIntent, getSafeString(R.string.share_via)));
    }

    // ========== OPEN IN BROWSER ==========

    private void openInBrowser() {
        FoodProduct product = productManager.getCurrentProduct();
        if (product == null) return;

        String url = ProductUrlBuilder.getWebsiteUrl(product.getSourceIdentifier());
        if (url == null) {
            Toast.makeText(this, getSafeString(R.string.website_not_available),
                    Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(this, getSafeString(R.string.browser_open_failed),
                    Toast.LENGTH_SHORT).show();
            logError("Failed to open browser", e);
        }
    }

    // ========== ADD TO MEAL ==========

    /**
     * Add the current product to the meal identified by returnToMealId.
     *
     * Uses the quantity currently visible in the renderer's custom amount input.
     * The renderer must be an OffProductDetailRenderer or DefaultProductDetailRenderer
     * for the input to be present; in all cases we fall back to a smart default.
     */
    private void addToMeal() {
        FoodProduct product = productManager.getCurrentProduct();
        if (product == null) {
            Toast.makeText(this, "Product not loaded", Toast.LENGTH_SHORT).show();
            return;
        }

        // Read quantity from the renderer's amount input (if accessible)
        double quantity = getQuantityFromRenderer();

        FoodPortion portion = new FoodPortion(product, quantity, Unit.G);
        portion.setParentType("MEAL");
        portion.setParentId(returnToMealId);

        // Disable button to prevent double-tap
        View btnAddToMeal = addToMealContainer.findViewById(R.id.btnAddToMeal);
        if (btnAddToMeal instanceof android.widget.Button) {
            ((android.widget.Button) btnAddToMeal).setEnabled(false);
            ((android.widget.Button) btnAddToMeal).setText(R.string.adding_to_meal);
        }

        MealRepository mealRepository = new MealRepository(this);
        mealRepository.getMeal(returnToMealId, new MealRepository.MealCallback() {
            @Override
            public void onSuccess(li.masciul.sugardaddi.core.models.Meal meal) {
                meal.addPortion(portion);
                mealRepository.updateMeal(meal, new MealRepository.MealCallback() {
                    @Override
                    public void onSuccess(li.masciul.sugardaddi.core.models.Meal savedMeal) {
                        runOnUiThread(() -> {
                            Toast.makeText(ProductDetailsActivity.this,
                                    R.string.added_to_meal, Toast.LENGTH_SHORT).show();
                            Intent intent = new Intent(ProductDetailsActivity.this,
                                    MealDetailsActivity.class);
                            intent.putExtra("extra_meal_id", returnToMealId);
                            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                                    | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                            startActivity(intent);
                            finish();
                        });
                    }

                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> {
                            Toast.makeText(ProductDetailsActivity.this,
                                    "Error saving: " + error, Toast.LENGTH_LONG).show();
                            resetAddToMealButton();
                        });
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(ProductDetailsActivity.this,
                            "Error loading meal: " + error, Toast.LENGTH_LONG).show();
                    resetAddToMealButton();
                });
            }
        });
    }

    /**
     * Read the quantity value from the renderer's custom amount input.
     *
     * The renderer inflated a view with @id/customAmountEditText inside it.
     * We look it up via the active renderer view. If not found (e.g. layout has
     * no input), fall back to serving size or 20g.
     */
    private double getQuantityFromRenderer() {
        if (activeRendererView != null) {
            com.google.android.material.textfield.TextInputEditText amountInput =
                    activeRendererView.findViewById(R.id.customAmountEditText);
            if (amountInput != null) {
                try {
                    String text = amountInput.getText().toString().trim();
                    if (!text.isEmpty()) {
                        double amount = Double.parseDouble(text);
                        if (amount > 0) return amount;
                    }
                } catch (NumberFormatException ignored) {}
            }
        }

        // Fallback: serving size or 20g
        FoodProduct product = productManager.getCurrentProduct();
        if (product != null) {
            ServingSize serving = product.getServingSize();
            if (serving != null && serving.isValid()) {
                Double servingGrams = serving.getAsGrams();
                if (servingGrams != null && servingGrams > 0) return servingGrams;
            }
        }
        return 20.0;
    }

    /**
     * Re-enable the Add to Meal button after a failed operation.
     */
    private void resetAddToMealButton() {
        View btn = addToMealContainer.findViewById(R.id.btnAddToMeal);
        if (btn instanceof android.widget.Button) {
            ((android.widget.Button) btn).setEnabled(true);
            ((android.widget.Button) btn).setText(R.string.add_to_meal);
        }
    }
}