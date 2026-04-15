package li.masciul.sugardaddi.ui.activities;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.widget.Toolbar;

import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;

import li.masciul.sugardaddi.R;
import li.masciul.sugardaddi.core.enums.MealType;
import li.masciul.sugardaddi.core.models.Meal;
import li.masciul.sugardaddi.data.repository.MealRepository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Locale;
import java.util.UUID;

/**
 * CreateMealActivity - Step 1 of meal creation workflow
 *
 * FINAL VERSION - Large Material Design pickers with proper theming
 *
 * Features:
 * - MaterialDatePicker (large, detailed calendar view)
 * - MaterialTimePicker (large, detailed clock view)
 * - Inherits #5DADE2 from app theme automatically
 * - No custom themes needed
 * - Consistent sizing and appearance
 *
 * @version 4.0 - Material Components Final
 */
public class CreateMealActivity extends BaseActivity {

    // Date/Time Selection
    private enum DateTimeSource {
        NOW,
        CUSTOM
    }

    // UI Components
    private Button btnNow;
    private ImageButton btnCalendar;
    private TextView tvDateTime;
    private Button btnBreakfast;
    private Button btnLunch;
    private Button btnDinner;
    private Button btnSnack;
    private Button btnCreateMeal;

    // State
    private DateTimeSource dateTimeSource = DateTimeSource.NOW;
    private LocalDateTime selectedDateTime = LocalDateTime.now();
    private MealType selectedMealType = null;

    // Repository
    private MealRepository mealRepository;


    @Override
    protected void onBaseActivityCreated(Bundle savedInstanceState) {
        setContentView(R.layout.activity_create_meal);

        // Setup toolbar
        setupToolbar();

        // Initialize repository
        mealRepository = new MealRepository(this);

        // Initialize formatter

        // Initialize UI
        initializeViews();
        setupClickListeners();

        // Set initial state
        setInitialState();
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.new_meal_title);
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void initializeViews() {
        // Date/Time buttons
        btnNow = findViewById(R.id.btnNow);
        btnCalendar = findViewById(R.id.btnCalendar);
        tvDateTime = findViewById(R.id.tvDateTime);

        // Meal type buttons
        btnBreakfast = findViewById(R.id.btnBreakfast);
        btnLunch = findViewById(R.id.btnLunch);
        btnDinner = findViewById(R.id.btnDinner);
        btnSnack = findViewById(R.id.btnSnack);

        // Action buttons
        btnCreateMeal = findViewById(R.id.btnCreateMeal);
    }

    private void setupClickListeners() {
        // Date/Time selection
        btnNow.setOnClickListener(v -> onNowButtonClicked());
        btnCalendar.setOnClickListener(v -> onCalendarButtonClicked());

        // Meal type selection
        btnBreakfast.setOnClickListener(v -> onMealTypeSelected(MealType.BREAKFAST, btnBreakfast));
        btnLunch.setOnClickListener(v -> onMealTypeSelected(MealType.LUNCH, btnLunch));
        btnDinner.setOnClickListener(v -> onMealTypeSelected(MealType.DINNER, btnDinner));
        btnSnack.setOnClickListener(v -> onMealTypeSelected(MealType.SNACK, btnSnack));

        // Action buttons
        btnCreateMeal.setOnClickListener(v -> onCreateMealClicked());
    }

    private void setInitialState() {
        // Set NOW as selected by default
        dateTimeSource = DateTimeSource.NOW;
        selectedDateTime = LocalDateTime.now();

        // Update button states
        updateDateTimeButtonStates();

        // Update date/time display
        updateDateTimeDisplay();

        // Smart pre-selection: Suggest meal type based on current time
        preselectMealTypeBasedOnTime(selectedDateTime);

        // Enable/disable create button based on validation
        validateAndUpdateCreateButton();
    }

    // ========== DATE/TIME SELECTION ==========

    private void onNowButtonClicked() {
        dateTimeSource = DateTimeSource.NOW;
        selectedDateTime = LocalDateTime.now();

        updateDateTimeButtonStates();
        updateDateTimeDisplay();
        validateAndUpdateCreateButton();

        // Re-preselect meal type when NOW is clicked
        preselectMealTypeBasedOnTime(selectedDateTime);

    }

    private void onCalendarButtonClicked() {
        // Convert LocalDateTime to milliseconds for MaterialDatePicker
        long selectionMillis = selectedDateTime
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();

        // Build MaterialDatePicker
        // Inherits theme from app automatically via materialCalendarTheme
        // App theme provides #5DADE2 blue for all elements
        MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText(getString(R.string.select_date))
                .setSelection(selectionMillis)
                // NO .setTheme() call - inherits from app theme
                .build();

        // Handle date selection
        datePicker.addOnPositiveButtonClickListener(selection -> {
            // Convert milliseconds back to LocalDateTime
            LocalDateTime selectedDate = Instant.ofEpochMilli(selection)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime();

            // Show time picker with the selected date
            showTimePicker(selectedDate);
        });

        // Show the date picker
        datePicker.show(getSupportFragmentManager(), "DATE_PICKER");
    }

    private void showTimePicker(LocalDateTime dateWithTime) {
        // Build MaterialTimePicker
        // Inherits theme from app automatically via materialTimePickerTheme
        // App theme provides #5DADE2 blue for all elements
        MaterialTimePicker timePicker = new MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_24H)
                .setHour(dateWithTime.getHour())
                .setMinute(dateWithTime.getMinute())
                .setTitleText(getString(R.string.select_time))
                // NO .setTheme() call - inherits from app theme
                .build();

        // Handle time selection
        timePicker.addOnPositiveButtonClickListener(v -> {
            // Update selected date/time with the chosen time
            dateTimeSource = DateTimeSource.CUSTOM;
            selectedDateTime = LocalDateTime.of(
                    dateWithTime.getYear(),
                    dateWithTime.getMonth(),
                    dateWithTime.getDayOfMonth(),
                    timePicker.getHour(),
                    timePicker.getMinute()
            );

            updateDateTimeButtonStates();
            updateDateTimeDisplay();
            validateAndUpdateCreateButton();
        });

        // Show the time picker
        timePicker.show(getSupportFragmentManager(), "TIME_PICKER");
    }

    private void updateDateTimeButtonStates() {
        if (dateTimeSource == DateTimeSource.NOW) {
            // NOW is selected (blue)
            btnNow.setSelected(true);
            btnNow.setTextColor(Color.WHITE);

            // Calendar is not selected (white with border)
            btnCalendar.setSelected(false);
        } else {
            // NOW is not selected
            btnNow.setSelected(false);
            btnNow.setTextColor(Color.BLACK);

            // Calendar is selected (blue)
            btnCalendar.setSelected(true);
        }
    }

    private void updateDateTimeDisplay() {
        String formattedDateTime = formatDateTimeForLocale(selectedDateTime);
        tvDateTime.setText(formattedDateTime);
    }

    // ========== MEAL TYPE SELECTION ==========

    private void onMealTypeSelected(MealType mealType, Button clickedButton) {
        selectedMealType = mealType;

        updateAllMealTypeButtons();
        validateAndUpdateCreateButton();
    }

    private void updateAllMealTypeButtons() {
        updateMealTypeButton(btnBreakfast, MealType.BREAKFAST);
        updateMealTypeButton(btnLunch, MealType.LUNCH);
        updateMealTypeButton(btnDinner, MealType.DINNER);
        updateMealTypeButton(btnSnack, MealType.SNACK);
    }

    private void updateMealTypeButton(Button button, MealType mealType) {
        boolean isSelected = (selectedMealType == mealType);
        button.setSelected(isSelected);
        button.setTextColor(isSelected ? Color.WHITE : Color.BLACK);
    }

    // ========== VALIDATION ==========

    private void validateAndUpdateCreateButton() {
        boolean isValid = (dateTimeSource != null && selectedMealType != null);

        btnCreateMeal.setEnabled(isValid);
        btnCreateMeal.setAlpha(isValid ? 1.0f : 0.5f);
    }

    // ========== MEAL CREATION ==========

    private void onCreateMealClicked() {
        if (selectedMealType == null) {
            Toast.makeText(this, "Please select a meal type", Toast.LENGTH_SHORT).show();
            return;
        }

        // Disable button to prevent double-click
        btnCreateMeal.setEnabled(false);

        // Create meal object
        Meal meal = new Meal();
        meal.setId(UUID.randomUUID().toString());
        meal.setUserId(getUserId());
        meal.setStartTime(selectedDateTime);
        meal.setMealType(selectedMealType);
        meal.setName(selectedMealType.getLocalizedName(this));
        meal.setPlanned(false);
        meal.setTemplate(false);
        meal.setHomeMade(true);

        // Save to database
        mealRepository.createMeal(meal, new MealRepository.MealCallback() {
            @Override
            public void onSuccess(Meal createdMeal) {
                runOnUiThread(() -> {
                    // Navigate to MealDetailsActivity to add food items
                    Intent intent = new Intent(CreateMealActivity.this, MealDetailsActivity.class);
                    intent.putExtra(MealDetailsActivity.EXTRA_MEAL_ID, createdMeal.getId());
                    startActivity(intent);

                    // Close CreateMealActivity
                    finish();
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(CreateMealActivity.this,
                            getString(R.string.meal_creation_failed) + ": " + error,
                            Toast.LENGTH_LONG).show();

                    // Re-enable button
                    btnCreateMeal.setEnabled(true);
                });
            }
        });
    }

    // ========== HELPER METHODS ==========

    private String getUserId() {
        return "default_user";
    }

    /**
     * Smart meal type pre-selection based on time
     *
     * Uses MealType.fromTime() to suggest the most appropriate meal type
     * for the given time, then selects it automatically.
     *
     * Maps enum values to available buttons:
     * - BREAKFAST → Breakfast button
     * - LUNCH → Lunch button
     * - DINNER → Dinner button
     * - MORNING_SNACK, AFTERNOON_SNACK, EVENING_SNACK, SNACK → Snack button
     * - OTHER → No pre-selection
     */
    private void preselectMealTypeBasedOnTime(LocalDateTime dateTime) {
        if (dateTime == null) {
            selectedMealType = null;
            updateAllMealTypeButtons();
            return;
        }

        // Get suggested meal type based on time
        LocalTime time = dateTime.toLocalTime();
        MealType suggestedType = MealType.fromTime(time);

        // Map enum to available buttons (we only have 4 buttons: Breakfast, Lunch, Dinner, Snack)
        MealType buttonType = mapToAvailableButton(suggestedType);

        // Pre-select the button
        selectedMealType = buttonType;
        updateAllMealTypeButtons();
    }

    /**
     * Map any MealType to one of the 4 available buttons
     *
     * @param mealType The suggested meal type
     * @return The meal type corresponding to an available button
     */
    private MealType mapToAvailableButton(MealType mealType) {
        if (mealType == null) return null;

        switch (mealType) {
            case BREAKFAST:
                return MealType.BREAKFAST;
            case LUNCH:
                return MealType.LUNCH;
            case DINNER:
                return MealType.DINNER;
            case MORNING_SNACK:
            case AFTERNOON_SNACK:
            case EVENING_SNACK:
            case SNACK:
                return MealType.SNACK;
            case OTHER:
            default:
                return null; // No pre-selection for OTHER
        }
    }

    /**
     * Format date and time according to current locale
     * Uses same logic as MealDetailsActivity and JournalActivity for consistency
     */
    private String formatDateTimeForLocale(LocalDateTime dateTime) {
        // Get current language
        String langCode = getCurrentLanguage().getCode();
        Locale currentLocale = new Locale(langCode);

        // Get day name (capitalized)
        String dayName = dateTime.getDayOfWeek()
                .getDisplayName(java.time.format.TextStyle.FULL, currentLocale);
        dayName = capitalize(dayName);

        // Get month name (capitalized for both English and French)
        String monthName = dateTime.getMonth()
                .getDisplayName(java.time.format.TextStyle.FULL, currentLocale);
        monthName = capitalize(monthName);

        int day = dateTime.getDayOfMonth();
        int year = dateTime.getYear();
        int hour = dateTime.getHour();
        int minute = dateTime.getMinute();

        // Format based on locale
        if (langCode.equals("fr")) {
            // French format: "Jeudi, 22 Janvier 2026 ⏰ 00:26"
            return String.format(currentLocale, "%s, %d %s %d ⏰ %02d:%02d",
                    dayName, day, monthName, year, hour, minute);
        } else {
            // English format: "Thursday, January 22, 2026 ⏰ 00:26"
            return String.format(currentLocale, "%s, %s %d, %d ⏰ %02d:%02d",
                    dayName, monthName, day, year, hour, minute);
        }
    }

    /**
     * Capitalize first letter of string
     */
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