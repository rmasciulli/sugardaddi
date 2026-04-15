package li.masciul.sugardaddi.ui.activities;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import li.masciul.sugardaddi.R;
import li.masciul.sugardaddi.core.enums.MealType;
import li.masciul.sugardaddi.core.enums.NutrientBannerStyle;
import li.masciul.sugardaddi.core.models.FoodPortion;
import li.masciul.sugardaddi.core.models.FoodProduct;
import li.masciul.sugardaddi.core.models.Meal;
import li.masciul.sugardaddi.core.models.Nutrition;
import li.masciul.sugardaddi.data.repository.MealRepository;
import li.masciul.sugardaddi.managers.LanguageManager;
import li.masciul.sugardaddi.ui.adapters.MealPortionsAdapter;
import li.masciul.sugardaddi.ui.components.NutrientBannerHelper;
import li.masciul.sugardaddi.ui.components.NutrientBannerView;
import li.masciul.sugardaddi.ui.components.NutritionLabelManager;
import li.masciul.sugardaddi.core.enums.NutritionLabelMode;

import java.time.LocalDateTime;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

/**
 * MealDetailsActivity - View and edit meal details
 *
 * Features:
 * - View mode: Display meal info, portions, nutrition
 * - Edit mode: Swipe to delete portions, edit quantities
 * - Add items to meal
 * - Edit meal properties (name, time, type)
 * - Delete meal
 * - Real-time nutrition calculation
 *
 * @version 1.0
 */
public class MealDetailsActivity extends BaseActivity {

    // ========== CONSTANTS ==========
    private static final String TAG = "MealDetailsActivity";
    public static final String EXTRA_MEAL_ID = "extra_meal_id";
    private static final int REQUEST_ADD_ITEM = 200;
    private static final int REQUEST_EDIT_MEAL = 201;

    // ========== UI COMPONENTS ==========

    // Header
    private TextView mealTypeEmojiView;
    private TextView mealTypeText;
    private TextView mealDateTimeText;

    // Summary
    private LinearLayout nutrientBannersContainer;
    private MaterialCardView summaryCard;

    // Portions
    private RecyclerView portionsRecyclerView;
    private MealPortionsAdapter portionsAdapter;
    private LinearLayout emptyPortionsView;
    private MaterialButton addItemButton;

    // Nutrition
    private LinearLayout nutritionLabelContainer;

    // ========== DATA ==========
    private String mealId;
    private Meal currentMeal;
    private boolean isEditMode = false;
    private LanguageManager.SupportedLanguage lastLanguage;

    // Repository
    private MealRepository mealRepository;

    // ========== LIFECYCLE ==========

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_meal_details);

        // Initialize
        mealRepository = new MealRepository(this);
        lastLanguage = getCurrentLanguage();

        // Get meal ID from intent
        mealId = getIntent().getStringExtra(EXTRA_MEAL_ID);
        if (mealId == null || mealId.isEmpty()) {
            Toast.makeText(this, R.string.error_meal_not_found, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Setup UI
        initializeViews();
        setupToolbar();
        setupPortionsList();
        setupButtons();

        // Load meal
        loadMeal();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Clear cache to force fresh load from database
        // This ensures we see newly added items
        mealRepository.clearMealCache(mealId);

        // Reload meal from database
        loadMeal();

        // Update language tracking
        lastLanguage = getCurrentLanguage();
    }

    @Override
    protected void onBaseActivityCreated(@Nullable Bundle savedInstanceState) {
        // BaseActivity callback - not needed for this activity
    }

    // ========== INITIALIZATION ==========

    private void initializeViews() {
        // Header
        mealTypeEmojiView = findViewById(R.id.mealTypeEmoji);
        mealTypeText = findViewById(R.id.mealTypeText);
        mealDateTimeText = findViewById(R.id.mealDateTimeText);

        // Summary
        nutrientBannersContainer = findViewById(R.id.nutrientBannersContainer);
        summaryCard = findViewById(R.id.summaryCard);

        // Portions
        portionsRecyclerView = findViewById(R.id.portionsRecyclerView);
        emptyPortionsView = findViewById(R.id.emptyPortionsView);
        addItemButton = findViewById(R.id.addItemButton);

        // Nutrition
        nutritionLabelContainer = findViewById(R.id.nutritionLabelContainer);
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.meal_details_title);
        }
        toolbar.setNavigationOnClickListener(v -> {
            // Save changes before leaving
            if (hasUnsavedChanges()) {
                showSaveChangesDialog();
            } else {
                setResult(RESULT_OK);
                finish();
            }
        });
    }

    private void setupPortionsList() {
        portionsAdapter = new MealPortionsAdapter(this, new MealPortionsAdapter.PortionInteractionListener() {
            @Override
            public void onPortionClicked(FoodPortion portion) {
                String itemId = portion.getItemId();  // Get the persisted searchable ID
                if (itemId != null && !itemId.isEmpty()) {
                    Intent intent = new Intent(MealDetailsActivity.this, ItemDetailsActivity.class);
                    intent.putExtra(ItemDetailsActivity.EXTRA_FOOD_ITEM, itemId);
                    startActivity(intent);
                } else {
                    Toast.makeText(MealDetailsActivity.this,
                            "Product details not available",
                            Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onPortionQuantityChanged(FoodPortion portion, double newQuantity) {
                // Update portion quantity
                updatePortionQuantity(portion, newQuantity);
            }

            @Override
            public void onPortionDeleted(FoodPortion portion) {
                // Delete portion (only in edit mode)
                if (isEditMode) {
                    deletePortion(portion);
                }
            }
        });

        portionsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        portionsRecyclerView.setAdapter(portionsAdapter);

        // Setup swipe to delete (only in edit mode)
        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder viewHolder,
                                  @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                if (isEditMode) {
                    int position = viewHolder.getAdapterPosition();
                    FoodPortion portion = portionsAdapter.getPortionAt(position);
                    if (portion != null) {
                        deletePortion(portion);
                    }
                } else {
                    // Not in edit mode - restore item
                    portionsAdapter.notifyItemChanged(viewHolder.getAdapterPosition());
                }
            }

            @Override
            public int getSwipeDirs(@NonNull RecyclerView recyclerView,
                                    @NonNull RecyclerView.ViewHolder viewHolder) {
                // Only allow swiping in edit mode
                return isEditMode ? super.getSwipeDirs(recyclerView, viewHolder) : 0;
            }
        });

        itemTouchHelper.attachToRecyclerView(portionsRecyclerView);
    }

    private void setupButtons() {
        // Add item button
        addItemButton.setOnClickListener(v -> addItemToMeal());
    }

    // ========== MENU ==========

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_meal_details, menu);
        return true;
    }

    /**
     * Update the edit mode icon dynamically based on current state.
     * Called every time the menu is about to be shown.
     * - View mode: pencil icon, title "Edit Meal"
     * - Edit mode: checkmark icon, title "Done"
     */
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem editModeItem = menu.findItem(R.id.action_edit_mode);
        if (editModeItem != null) {
            if (isEditMode) {
                editModeItem.setIcon(R.drawable.ic_check);
                editModeItem.setTitle(R.string.done);
            } else {
                editModeItem.setIcon(R.drawable.ic_edit);
                editModeItem.setTitle(R.string.edit_meal);
            }
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();

        if (itemId == R.id.action_edit_mode) {
            toggleEditMode();
            // Force menu refresh so the icon updates immediately
            invalidateOptionsMenu();
            return true;
        } else if (itemId == R.id.action_edit_properties) {
            editMealProperties();
            return true;
        } else if (itemId == R.id.action_delete_meal) {
            deleteMeal();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    // ========== DATA LOADING ==========

    /**
     * Load meal from database
     *
     * Uses getMealWithProducts to ensure nutrition calculation works correctly
     */
    private void loadMeal() {
        // Clear cache before loading to get fresh data
        // This ensures we get updated portions when items are added/removed
        mealRepository.clearMealCache(mealId);

        // Use getMealWithProducts to populate transient foodProduct fields
        mealRepository.getMealWithProducts(mealId, new MealRepository.MealCallback() {
            @Override
            public void onSuccess(Meal meal) {
                runOnUiThread(() -> {
                    currentMeal = meal;
                    displayMeal();
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(MealDetailsActivity.this,
                            getString(R.string.error_loading_meal) + ": " + error,
                            Toast.LENGTH_LONG).show();
                    finish();
                });
            }
        });
    }

    // ========== DISPLAY ==========

    private void displayMeal() {
        if (currentMeal == null) return;

        // Header
        updateHeader();

        // Summary banners
        updateNutritionSummary();

        // Portions list
        updatePortionsList();

        // Nutrition label
        updateNutritionLabel();
    }

    private void updateHeader() {
        // Meal type
        MealType mealType = currentMeal.getMealType();
        mealTypeEmojiView.setText(mealType.getEmoji());
        String mealTypeName = getMealTypeName(mealType);
        mealTypeText.setText(mealTypeName);

        // Date and time - use locale-aware formatting
        String dateTime = formatDateTimeForLocale(currentMeal.getStartTime());
        mealDateTimeText.setText(dateTime);
    }

    /**
     * Format date and time according to current locale
     * Uses same logic as JournalActivity for consistency
     */
    private String formatDateTimeForLocale(LocalDateTime dateTime) {
        // Get current language
        String langCode = getCurrentLanguage().getCode();
        Locale currentLocale = new Locale(langCode);

        // Get day name (capitalized)
        String dayName = dateTime.getDayOfWeek()
                .getDisplayName(TextStyle.FULL, currentLocale);
        dayName = capitalize(dayName);

        // Get month name (capitalized for both English and French)
        String monthName = dateTime.getMonth()
                .getDisplayName(TextStyle.FULL, currentLocale);
        monthName = capitalize(monthName);

        int day = dateTime.getDayOfMonth();
        int year = dateTime.getYear();
        int hour = dateTime.getHour();
        int minute = dateTime.getMinute();

        // Format based on locale
        if (langCode.equals("fr")) {
            // French format: "Mercredi, 21 Janvier 2026 ⏰ 21:51"
            return String.format(currentLocale, "%s, %d %s %d ⏰ %02d:%02d",
                    dayName, day, monthName, year, hour, minute);
        } else {
            // English format: "Wednesday, January 21, 2026 ⏰ 09:21"
            return String.format(currentLocale, "%s, %s %d, %d ⏰ %02d:%02d",
                    dayName, monthName, day, year, hour, minute);
        }
    }

    private void updateNutritionSummary() {
        // Clear existing banners
        nutrientBannersContainer.removeAllViews();

        // Get nutrition (calculated from portions)
        Nutrition nutrition = currentMeal.getNutrition();
        // TO REMOVE
        Log.d(TAG, "updateNutritionSummary - nutrition: " + nutrition);
        if (nutrition != null) {
            Log.d(TAG, "  hasData: " + nutrition.hasData());
            Log.d(TAG, "  kcal: " + nutrition.getEnergyKcal());
            Log.d(TAG, "  portions: " + currentMeal.getPortions().size());
        }
        // END TO REMOVE

        if (nutrition == null || !nutrition.hasData()) {
            // No nutrition data - hide summary card
            summaryCard.setVisibility(View.GONE);
            return;
        }

        summaryCard.setVisibility(View.VISIBLE);

        // Create 5 summary banners
        NutrientBannerView[] banners = NutrientBannerHelper.createDailySummaryBanners(
                this,
                nutrition,
                NutrientBannerStyle.VERTICAL
        );

        // Add banners to container with proper spacing
        int spacingPx = (int) (4 * getResources().getDisplayMetrics().density);  // Half spacing on each side

        for (int i = 0; i < banners.length; i++) {
            NutrientBannerView banner = banners[i];
            if (banner != null) {
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        0,  // width = 0 (required for weight)
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1.0f  // Each banner gets equal width
                );

                // Add spacing: first and last get margin on one side only,
                // middle banners get margin on both sides (= 8dp total between banners)
                if (i == 0) {
                    // First banner: only right margin
                    params.setMarginEnd(spacingPx);
                } else if (i == banners.length - 1) {
                    // Last banner: only left margin
                    params.setMarginStart(spacingPx);
                } else {
                    // Middle banners: both sides
                    params.setMarginStart(spacingPx);
                    params.setMarginEnd(spacingPx);
                }

                banner.setLayoutParams(params);
                nutrientBannersContainer.addView(banner);
            }
        }
    }

    private void updatePortionsList() {
        if (currentMeal.hasPortions()) {
            // Show portions list
            portionsRecyclerView.setVisibility(View.VISIBLE);
            emptyPortionsView.setVisibility(View.GONE);
            portionsAdapter.setPortions(currentMeal.getPortions());
        } else {
            // Show empty state
            portionsRecyclerView.setVisibility(View.GONE);
            emptyPortionsView.setVisibility(View.VISIBLE);
        }
    }

    private void updateNutritionLabel() {
        // Clear existing label
        nutritionLabelContainer.removeAllViews();

        // Get nutrition
        Nutrition nutrition = currentMeal.getNutrition();

        if (nutrition == null || !nutrition.hasData()) {
            return;
        }

        // Create nutrition label manager with container
        NutritionLabelManager labelManager = new NutritionLabelManager(this, nutritionLabelContainer, NutritionLabelMode.SUMMARY);

        // Create a temporary FoodProduct to display nutrition
        FoodProduct tempProduct = new FoodProduct();
        tempProduct.setNutrition(nutrition);

        // Display the nutrition label
        labelManager.displayProduct(tempProduct);
    }

    // ========== ACTIONS ==========

    private void addItemToMeal() {
        // Navigate to search/add item activity
        Intent intent = new Intent(this, MainActivity.class);  // TODO: Create AddItemToMealActivity
        intent.putExtra("mode", "add_to_meal");
        intent.putExtra(EXTRA_MEAL_ID, mealId);
        startActivityForResult(intent, REQUEST_ADD_ITEM);
    }

    private void toggleEditMode() {
        isEditMode = !isEditMode;

        if (isEditMode) {
            // Switch to edit mode — icon updates via onPrepareOptionsMenu
            Toast.makeText(this, R.string.swipe_to_delete_portions, Toast.LENGTH_SHORT).show();
        } else {
            // Switch to view mode — save changes
            saveMeal();
        }

        // Update adapter (enables/disables swipe-to-delete)
        portionsAdapter.setEditMode(isEditMode);
    }

    private void editMealProperties() {
        // TODO: Navigate to EditMealPropertiesActivity/Dialog
        Toast.makeText(this, "Edit meal properties - Coming soon", Toast.LENGTH_SHORT).show();
    }

    private void deleteMeal() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_meal_title)
                .setMessage(R.string.delete_meal_message)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    performDeleteMeal();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void performDeleteMeal() {
        mealRepository.deleteMeal(mealId, new MealRepository.MealOperationCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    Toast.makeText(MealDetailsActivity.this,
                            R.string.meal_deleted_success,
                            Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(MealDetailsActivity.this,
                            getString(R.string.error_deleting_meal) + ": " + error,
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    // ========== PORTION MANAGEMENT ==========

    private void updatePortionQuantity(FoodPortion portion, double newQuantity) {
        // Update quantity in portion
        portion.getServing().setQuantity(newQuantity);

        // Recalculate nutrition
        currentMeal.calculateNutrition();

        // Update UI
        updateNutritionSummary();
        updateNutritionLabel();
        portionsAdapter.notifyDataSetChanged();

        Log.d(TAG, "Updated portion quantity: " + portion.getDisplayName(getCurrentLanguageCode()) +
                " to " + newQuantity + "g");
    }

    private void deletePortion(FoodPortion portion) {
        if (currentMeal == null || portion == null) return;

        // Find the index of this portion
        List<FoodPortion> portions = currentMeal.getPortions();
        int index = -1;
        for (int i = 0; i < portions.size(); i++) {
            if (portions.get(i).getId().equals(portion.getId())) {
                index = i;
                break;
            }
        }

        if (index == -1) {
            Log.w(TAG, "Portion not found in meal: " + portion.getId());
            return;
        }

        // Remove portion from meal by index
        currentMeal.removePortion(index);

        // Recalculate nutrition
        currentMeal.calculateNutrition();

        // Update UI
        updatePortionsList();
        updateNutritionSummary();
        updateNutritionLabel();

        Toast.makeText(this,
                getString(R.string.portion_deleted) + ": " + portion.getDisplayName(getCurrentLanguageCode()),
                Toast.LENGTH_SHORT).show();

        Log.d(TAG, "Deleted portion: " + portion.getDisplayName(getCurrentLanguageCode()));
    }

    // ========== SAVE ==========

    private void saveMeal() {
        if (currentMeal == null) return;

        mealRepository.updateMeal(currentMeal, new MealRepository.MealCallback() {
            @Override
            public void onSuccess(Meal meal) {
                runOnUiThread(() -> {
                    currentMeal = meal;
                    Toast.makeText(MealDetailsActivity.this,
                            R.string.meal_saved_success,
                            Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(MealDetailsActivity.this,
                            getString(R.string.error_saving_meal) + ": " + error,
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private boolean hasUnsavedChanges() {
        // TODO: Implement change tracking
        return false;
    }

    private void showSaveChangesDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.unsaved_changes_title)
                .setMessage(R.string.unsaved_changes_message)
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    saveMeal();
                    setResult(RESULT_OK);
                    finish();
                })
                .setNegativeButton(R.string.discard, (dialog, which) -> {
                    setResult(RESULT_OK);
                    finish();
                })
                .setNeutralButton(R.string.cancel, null)
                .show();
    }

    // ========== ACTIVITY RESULT ==========

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK) {
            if (requestCode == REQUEST_ADD_ITEM) {
                // Item was added - reload meal
                loadMeal();
            } else if (requestCode == REQUEST_EDIT_MEAL) {
                // Meal properties were edited - reload
                loadMeal();
            }
        }
    }

    // ========== HELPERS ==========

    @Override
    protected LanguageManager.SupportedLanguage getCurrentLanguage() {
        return LanguageManager.getCurrentLanguage(this);
    }

    private String getCurrentLanguageCode() {
        return getCurrentLanguage().getCode();
    }

    private String getMealTypeName(MealType mealType) {
        if (mealType == null) return getString(R.string.meal);

        switch (mealType) {
            case BREAKFAST:
                return getString(R.string.meal_type_breakfast);
            case LUNCH:
                return getString(R.string.meal_type_lunch);
            case DINNER:
                return getString(R.string.meal_type_dinner);
            case SNACK:
                return getString(R.string.meal_type_snack);
            default:
                return getString(R.string.meal);
        }
    }

    private String capitalize(String text) {
        if (text == null || text.isEmpty()) return text;
        return text.substring(0, 1).toUpperCase() + text.substring(1).toLowerCase();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mealRepository = null;
    }
}