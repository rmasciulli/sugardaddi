package li.masciul.sugardaddi.ui.activities;

import android.content.Intent;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.core.widget.NestedScrollView;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationView;

import li.masciul.sugardaddi.R;
import li.masciul.sugardaddi.core.enums.NutrientBannerStyle;
import li.masciul.sugardaddi.core.models.Meal;
import li.masciul.sugardaddi.core.models.Nutrition;
import li.masciul.sugardaddi.data.repository.MealRepository;
import li.masciul.sugardaddi.ui.adapters.TimelineAdapter;
import li.masciul.sugardaddi.ui.adapters.timeline.HourMarkerTimelineItem;
import li.masciul.sugardaddi.ui.adapters.timeline.MealTimelineItem;
import li.masciul.sugardaddi.ui.adapters.timeline.TimelineDecoration;
import li.masciul.sugardaddi.ui.adapters.timeline.TimelineItem;
import li.masciul.sugardaddi.ui.components.NutrientBannerHelper;
import li.masciul.sugardaddi.ui.components.NutrientBannerView;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * JournalActivity - Daily meal journal with timeline view
 *
 * Features:
 * - Date navigation (prev/next/calendar)
 * - Daily nutrition summary with colored banners
 * - 24-hour timeline with meals
 * - "Now" indicator with auto-scroll to center
 * - Smart auto-scroll: centers on current time, respects user manual scrolling
 * - Click meals to view details
 * - Navigation drawer for app-wide navigation
 *
 * AUTO-SCROLL BEHAVIOR:
 * - On first load (today): auto-scrolls so the "now" indicator is centered in the container
 * - On date change: resets scroll state and auto-scrolls if navigating to today
 * - On resume (today): auto-scrolls unless user has manually scrolled during this session
 * - Programmatic scrolls do NOT set the "user scrolled" flag (avoids self-defeating behavior)
 * - Manual user scrolls disable auto-scroll for the current date session
 * - Navigating to a different date resets the flag
 *
 * @version 2.1 - Swipe gestures + 40% container height + end-of-day sentinel
 */
public class JournalActivity extends BaseActivity implements
        NavigationView.OnNavigationItemSelectedListener {

    // ========== CONSTANTS ==========

    private static final String TAG = "JournalActivity";
    private static final int REQUEST_CREATE_MEAL = 100;
    private static final int REQUEST_VIEW_MEAL = 101;

    // ========== NAVIGATION DRAWER ==========

    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private ActionBarDrawerToggle drawerToggle;

    // ========== UI COMPONENTS ==========

    // Date navigation
    private MaterialButton previousDayButton;
    private MaterialButton nextDayButton;
    private MaterialButton calendarButton;
    private TextView currentDateText;
    private TextView dayIndicatorText;

    // Daily summary
    private LinearLayout nutrientBannersContainer;

    // Timeline
    private RecyclerView timelineRecyclerView;
    private TimelineAdapter timelineAdapter;
    private TimelineDecoration timelineDecoration;
    private LinearLayout emptyStateLayout;

    // Timeline container (for dedicated scrolling)
    private FrameLayout timelineContainer;
    private NestedScrollView timelineScrollView;

    // FAB
    private FloatingActionButton addMealFab;

    // ========== AUTO-SCROLL STATE ==========

    /**
     * Gesture detector for horizontal swipe navigation on the timeline.
     * Stored as a field so dispatchTouchEvent() can feed it events.
     */
    private GestureDetector timelineGestureDetector;

    /**
     * Tracks whether the user has manually scrolled the timeline.
     * When true, auto-scroll is suppressed to respect the user's position.
     * Reset when navigating to a different date.
     */
    private boolean userHasScrolledTimeline = false;

    /**
     * Tracks whether an ongoing scroll was triggered programmatically (auto-scroll).
     * Prevents the scroll listener from setting userHasScrolledTimeline when
     * the scroll was initiated by our own smoothScrollTo() call.
     */
    private boolean isProgrammaticScroll = false;

    // ========== DATA ==========

    private LocalDate currentDate = LocalDate.now();
    private List<Meal> mealsForCurrentDate = new ArrayList<>();
    private Nutrition dailyNutritionTotal;

    // Repository
    private MealRepository mealRepository;

    // ========== LIFECYCLE ==========

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_journal);

        // Initialize
        initializeViews();
        setupToolbar();
        setupNavigationDrawer();
        setupDateNavigation();
        setupDailySummary();
        setupTimeline();
        setupTimelineContainer();
        setupFAB();

        // Load data
        loadMealsForDate(currentDate);
    }

    @Override
    protected void onBaseActivityCreated(@Nullable Bundle savedInstanceState) {
        // BaseActivity callback - not needed since JournalActivity manages its own onCreate
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Refresh data when returning from other activities
        loadMealsForDate(currentDate);

        // Update "now" indicator and auto-scroll if viewing today
        if (currentDate.equals(LocalDate.now())) {
            timelineDecoration.setCurrentTime(LocalTime.now());
            timelineRecyclerView.invalidateItemDecorations();
            scrollToCurrentTime();
        }
    }

    // ========== INITIALIZATION ==========

    private void initializeViews() {
        // Drawer
        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);

        // Date navigation
        previousDayButton = findViewById(R.id.previousDayButton);
        nextDayButton = findViewById(R.id.nextDayButton);
        calendarButton = findViewById(R.id.calendarButton);
        currentDateText = findViewById(R.id.currentDateText);
        dayIndicatorText = findViewById(R.id.dayIndicatorText);

        // Daily summary
        nutrientBannersContainer = findViewById(R.id.nutrientBannersContainer);

        // Timeline
        timelineRecyclerView = findViewById(R.id.timelineRecyclerView);
        emptyStateLayout = findViewById(R.id.emptyStateLayout);
        timelineContainer = findViewById(R.id.timelineContainer);
        timelineScrollView = findViewById(R.id.timelineScrollView);

        // FAB
        addMealFab = findViewById(R.id.addMealFab);

        // Repository
        mealRepository = new MealRepository(getApplicationContext());
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(getSafeString(R.string.journal_title));
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeButtonEnabled(true);
        }
    }

    // ========== NAVIGATION DRAWER SETUP ==========

    private void setupNavigationDrawer() {
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

        navigationView.setNavigationItemSelectedListener(this);
        navigationView.setCheckedItem(R.id.nav_journal);
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.nav_journal) {
            // Already on journal - just close drawer
        } else if (id == R.id.nav_create_meal) {
            Intent intent = new Intent(this, CreateMealActivity.class);
            startActivity(intent);
        } else if (id == R.id.nav_search) {
            Intent intent = new Intent(this, MainActivity.class);
            startActivity(intent);
        } else if (id == R.id.nav_favorites) {
            Intent intent = new Intent(this, FavoritesActivity.class);
            startActivity(intent);
        } else if (id == R.id.nav_settings) {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
        }

        drawerLayout.closeDrawer(GravityCompat.START);
        return true;
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    // ========== DATE NAVIGATION ==========

    private void setupDateNavigation() {
        previousDayButton.setOnClickListener(v -> navigateToPreviousDay());
        nextDayButton.setOnClickListener(v -> navigateToNextDay());
        calendarButton.setOnClickListener(v -> showDatePicker());
        updateDateDisplay();
    }

    private void navigateToPreviousDay() {
        currentDate = currentDate.minusDays(1);
        userHasScrolledTimeline = false;  // Reset scroll flag on date change
        updateDateDisplay();
        loadMealsForDate(currentDate);
    }

    private void navigateToNextDay() {
        if (currentDate.isBefore(LocalDate.now())) {
            currentDate = currentDate.plusDays(1);
            userHasScrolledTimeline = false;  // Reset scroll flag on date change
            updateDateDisplay();
            loadMealsForDate(currentDate);
        }
    }

    private void showDatePicker() {
        MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Select date")
                .setSelection(currentDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())
                .build();

        datePicker.addOnPositiveButtonClickListener(selection -> {
            LocalDate selectedDate = LocalDate.ofEpochDay(selection / (24 * 60 * 60 * 1000));

            // Don't allow future dates
            if (selectedDate.isAfter(LocalDate.now())) {
                selectedDate = LocalDate.now();
            }

            currentDate = selectedDate;
            userHasScrolledTimeline = false;  // Reset scroll flag on date change
            updateDateDisplay();
            loadMealsForDate(currentDate);
        });

        datePicker.show(getSupportFragmentManager(), "DATE_PICKER");
    }

    private void updateDateDisplay() {
        String formattedDate = formatDateForLocale(currentDate);
        currentDateText.setText(formattedDate);

        LocalDate today = LocalDate.now();
        long daysDiff = ChronoUnit.DAYS.between(currentDate, today);

        if (daysDiff == 0) {
            dayIndicatorText.setText(getSafeString(R.string.today));
            dayIndicatorText.setVisibility(View.VISIBLE);

            // Update "now" indicator if timeline is already setup
            if (timelineDecoration != null) {
                timelineDecoration.setCurrentTime(LocalTime.now());
                timelineRecyclerView.invalidateItemDecorations();
            }
        } else if (daysDiff == 1) {
            dayIndicatorText.setText(getSafeString(R.string.yesterday));
            dayIndicatorText.setVisibility(View.VISIBLE);
        } else if (daysDiff == -1) {
            dayIndicatorText.setText(getSafeString(R.string.tomorrow));
            dayIndicatorText.setVisibility(View.VISIBLE);
        } else {
            dayIndicatorText.setVisibility(View.GONE);
        }

        // Disable next button if current date is today
        nextDayButton.setEnabled(!currentDate.equals(today));
        nextDayButton.setAlpha(currentDate.equals(today) ? 0.5f : 1.0f);
    }

    /**
     * Format date with proper capitalization for current locale
     * French: "Jeudi, 15 Janvier 2026"
     * English: "Thursday, January 15, 2026"
     */
    private String formatDateForLocale(LocalDate date) {
        String langCode = getCurrentLanguage().getCode();
        Locale currentLocale = new Locale(langCode);

        String dayName = capitalize(date.getDayOfWeek()
                .getDisplayName(TextStyle.FULL, currentLocale));
        String monthName = capitalize(date.getMonth()
                .getDisplayName(TextStyle.FULL, currentLocale));

        int day = date.getDayOfMonth();
        int year = date.getYear();

        if (currentLocale.getLanguage().equals("fr")) {
            return String.format(currentLocale, "%s, %d %s %d", dayName, day, monthName, year);
        } else {
            return String.format(currentLocale, "%s, %s %d, %d", dayName, monthName, day, year);
        }
    }

    private String capitalize(String text) {
        if (text == null || text.isEmpty()) return text;
        return text.substring(0, 1).toUpperCase() + text.substring(1);
    }

    // ========== DAILY SUMMARY ==========

    private void setupDailySummary() {
        // Banner container is in layout - banners populated when data loads
    }

    private void updateDailySummary() {
        Log.d(TAG, "===== updateDailySummary() START =====");

        nutrientBannersContainer.removeAllViews();

        if (dailyNutritionTotal == null) {
            Log.e(TAG, "ERROR: dailyNutritionTotal is NULL!");
            return;
        }

        Log.d(TAG, "Energy (kcal): " + dailyNutritionTotal.getEnergyKcal());
        Log.d(TAG, "Proteins: " + dailyNutritionTotal.getProteins());
        Log.d(TAG, "Carbs: " + dailyNutritionTotal.getCarbohydrates());
        Log.d(TAG, "Fat: " + dailyNutritionTotal.getFat());
        Log.d(TAG, "Fiber: " + dailyNutritionTotal.getFiber());

        // Create 5 summary banners (Energy, Carbs, Proteins, Fats, Fiber)
        NutrientBannerView[] banners = NutrientBannerHelper.createDailySummaryBanners(
                this,
                dailyNutritionTotal,
                NutrientBannerStyle.VERTICAL
        );

        if (banners == null) {
            Log.e(TAG, "ERROR: createDailySummaryBanners returned NULL!");
            return;
        }

        // Add banners to container with spacing
        int spacingPx = (int) (4 * getResources().getDisplayMetrics().density);

        for (int i = 0; i < banners.length; i++) {
            NutrientBannerView banner = banners[i];
            if (banner != null) {
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1.0f  // Each banner gets 1/5 of width
                );

                if (i == 0) {
                    params.setMarginEnd(spacingPx);
                } else if (i == banners.length - 1) {
                    params.setMarginStart(spacingPx);
                } else {
                    params.setMarginStart(spacingPx);
                    params.setMarginEnd(spacingPx);
                }

                banner.setLayoutParams(params);
                nutrientBannersContainer.addView(banner);
            }
        }

        Log.d(TAG, "Total banners added: " + nutrientBannersContainer.getChildCount());
        Log.d(TAG, "===== updateDailySummary() END =====");
    }

    // ========== TIMELINE SETUP ==========

    private void setupTimeline() {
        timelineAdapter = new TimelineAdapter(this);
        timelineAdapter.setOnMealClickListener(meal -> openMealDetails(meal));

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        timelineRecyclerView.setLayoutManager(layoutManager);
        timelineRecyclerView.setAdapter(timelineAdapter);

        // Add timeline decoration (vertical line + hour separators + now indicator)
        timelineDecoration = new TimelineDecoration(this);
        timelineRecyclerView.addItemDecoration(timelineDecoration);
    }

    /**
     * Setup the timeline container height and scroll tracking.
     *
     * The container is set to 1/3 of screen height to show a window into the
     * 24-hour timeline. A NestedScrollView provides independent scrolling.
     *
     * SCROLL TRACKING:
     * - Programmatic scrolls (auto-scroll) set isProgrammaticScroll = true BEFORE scrolling
     * - The scroll listener checks this flag and only marks user scroll when it's false
     * - This prevents the auto-scroll from defeating itself
     */
    private void setupTimelineContainer() {
        // Set container height to 40% of screen
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        int containerHeight = metrics.heightPixels * 40 / 100;

        ViewGroup.LayoutParams params = timelineContainer.getLayoutParams();
        params.height = containerHeight;
        timelineContainer.setLayoutParams(params);

        Log.d(TAG, "Timeline container height set to: " + containerHeight + "px");

        // Track user scrolling to prevent auto-scroll override
        if (timelineScrollView != null) {
            timelineScrollView.setOnScrollChangeListener(
                    (NestedScrollView.OnScrollChangeListener) (v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
                        // Only flag as user scroll if NOT triggered by our own auto-scroll
                        if (!isProgrammaticScroll && Math.abs(scrollY - oldScrollY) > 10) {
                            userHasScrolledTimeline = true;
                            Log.d(TAG, "User manually scrolled timeline - auto-scroll disabled for this date");
                        }
                    });
        }

        // Setup horizontal swipe gestures for date navigation
        setupTimelineSwipeGesture();
    }

    /**
     * Setup horizontal swipe gesture on the timeline for date navigation.
     *
     * Swipe RIGHT → previous day
     * Swipe LEFT  → next day (capped at today)
     *
     * TOUCH EVENT ARCHITECTURE:
     * The NestedScrollView intercepts touch sequences for its vertical scrolling.
     * On a perfectly horizontal swipe, it can alter or cancel events before our
     * GestureDetector sees the full sequence, causing onFling to never fire.
     *
     * Solution: We store the GestureDetector as a field and feed it events from
     * the Activity's dispatchTouchEvent(), which runs BEFORE any view's touch
     * handling. We only feed events whose coordinates fall within the timeline
     * container bounds. This guarantees the detector sees every DOWN → MOVE → UP
     * regardless of what the NestedScrollView does internally.
     */
    private void setupTimelineSwipeGesture() {
        // Swipe detection thresholds — tuned for natural thumb gestures
        final int SWIPE_THRESHOLD_DP = 25;          // Min distance (~6mm physical)
        final int SWIPE_VELOCITY_THRESHOLD_DP = 50;  // Min speed — catches even slow drags
        float density = getResources().getDisplayMetrics().density;
        final float swipeThreshold = SWIPE_THRESHOLD_DP * density;
        final float velocityThreshold = SWIPE_VELOCITY_THRESHOLD_DP * density;

        timelineGestureDetector = new GestureDetector(this,
                new GestureDetector.SimpleOnGestureListener() {

                    @Override
                    public boolean onDown(MotionEvent e) {
                        return true;
                    }

                    @Override
                    public boolean onFling(MotionEvent e1, MotionEvent e2,
                                           float velocityX, float velocityY) {
                        if (e1 == null || e2 == null) return false;

                        float diffX = e2.getX() - e1.getX();
                        float diffY = e2.getY() - e1.getY();

                        // Only process predominantly horizontal swipes
                        if (Math.abs(diffX) > Math.abs(diffY) * 1.5f
                                && Math.abs(diffX) > swipeThreshold
                                && Math.abs(velocityX) > velocityThreshold) {

                            if (diffX > 0) {
                                navigateToPreviousDay();
                            } else {
                                navigateToNextDay();
                            }
                            return true;
                        }
                        return false;
                    }
                });
    }

    /**
     * Intercept all touch events at the Activity level.
     *
     * Events within the timeline container bounds are fed to the swipe gesture
     * detector BEFORE any view processes them. This ensures perfectly horizontal
     * swipes are detected even though the NestedScrollView would normally
     * intercept and alter the touch sequence.
     *
     * The event is never consumed here — it always continues to the normal
     * dispatch chain so vertical scrolling and all other touch handling works.
     */
    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (timelineGestureDetector != null && timelineContainer != null) {
            // Check if the touch is within the timeline container
            if (isTouchInsideView(event, timelineContainer)) {
                timelineGestureDetector.onTouchEvent(event);
            }
        }
        return super.dispatchTouchEvent(event);
    }

    /**
     * Check if a touch event's coordinates fall within a view's screen bounds.
     *
     * @param event Touch event with raw screen coordinates
     * @param view  View to test against
     * @return true if touch is inside the view
     */
    private boolean isTouchInsideView(MotionEvent event, View view) {
        int[] location = new int[2];
        view.getLocationOnScreen(location);
        float x = event.getRawX();
        float y = event.getRawY();
        return x >= location[0]
                && x <= location[0] + view.getWidth()
                && y >= location[1]
                && y <= location[1] + view.getHeight();
    }

    /**
     * Update the timeline with current data.
     *
     * Always shows the 24-hour timeline (with or without meals).
     * Auto-scrolls to current time when viewing today, regardless of whether
     * there are meals - the timeline is always useful for time orientation.
     */
    private void updateTimeline() {
        // Build timeline items (meals + hour markers)
        List<TimelineItem> timelineItems = buildTimelineItems();
        timelineAdapter.setItems(timelineItems);

        // Always show timeline (hour markers visible even without meals)
        timelineRecyclerView.setVisibility(View.VISIBLE);

        // Show/hide empty state message
        emptyStateLayout.setVisibility(
                mealsForCurrentDate.isEmpty() ? View.VISIBLE : View.GONE);

        // Auto-scroll to current time if viewing today
        // Works with or without meals - the timeline always has 24 hour markers
        if (currentDate.equals(LocalDate.now())) {
            scrollToCurrentTime();
        }
    }

    /**
     * Build the sorted list of timeline items (hour markers + meals + sentinel).
     *
     * Creates 25 hour markers: 0:00 through 23:00 plus an end-of-day sentinel
     * displaying "0:00" at the bottom. The sentinel provides visual space for
     * the now indicator between 23:00 and midnight.
     *
     * Items are sorted by time. The sentinel uses LocalTime.MAX internally
     * to ensure it always sorts last.
     */
    private List<TimelineItem> buildTimelineItems() {
        List<TimelineItem> items = new ArrayList<>();

        // Add hour markers (00:00 to 23:00)
        for (int hour = 0; hour < 24; hour++) {
            items.add(new HourMarkerTimelineItem(hour));
        }

        // Add end-of-day sentinel (displays "0:00", sorts last)
        items.add(HourMarkerTimelineItem.createEndOfDaySentinel());

        // Add meals
        for (Meal meal : mealsForCurrentDate) {
            items.add(new MealTimelineItem(meal));
        }

        // Sort by time
        items.sort(Comparator.comparing(TimelineItem::getTime));

        return items;
    }

    // ========== AUTO-SCROLL ==========

    /**
     * Auto-scroll the timeline so the "now" indicator is centered in the container.
     *
     * ALGORITHM:
     * 1. Find the adapter position of the current hour marker
     * 2. Wait for layout to complete (post to RecyclerView)
     * 3. Get the view's top position within the RecyclerView
     * 4. Add a fractional offset for minutes within the hour (e.g., 14:30 = halfway between 14:00 and 15:00)
     * 5. Subtract half the container height to center the position
     * 6. Smooth scroll to that position
     *
     * GUARD CONDITIONS:
     * - Only scrolls if user hasn't manually scrolled (respects user intent)
     * - Marks scroll as programmatic so the scroll listener doesn't flag it
     * - Resets the programmatic flag after the scroll animation settles
     */
    private void scrollToCurrentTime() {
        // Respect user's manual scroll position
        if (userHasScrolledTimeline) {
            Log.d(TAG, "Skipping auto-scroll - user has manually scrolled");
            return;
        }

        if (timelineScrollView == null || timelineRecyclerView == null) {
            return;
        }

        // Wait for layout to complete after setItems() / notifyDataSetChanged()
        // Since nestedScrollingEnabled=false, all views are laid out immediately
        timelineRecyclerView.post(() -> {
            try {
                LocalTime now = LocalTime.now();

                // Find adapter position of the current hour marker
                int targetPosition = findHourMarkerPosition(now.getHour());
                if (targetPosition < 0) {
                    Log.w(TAG, "Could not find hour marker for hour: " + now.getHour());
                    return;
                }

                LinearLayoutManager layoutManager =
                        (LinearLayoutManager) timelineRecyclerView.getLayoutManager();
                if (layoutManager == null) return;

                // Get the view for the current hour marker
                View hourView = layoutManager.findViewByPosition(targetPosition);
                if (hourView == null) {
                    Log.w(TAG, "Hour view not found at position: " + targetPosition);
                    return;
                }

                // Calculate the "now" Y position using view centers
                // This matches TimelineDecoration which draws the now indicator
                // interpolated between the centers of surrounding hour marker views
                int hourViewCenter = hourView.getTop() + (hourView.getHeight() / 2);
                int scrollTarget = hourViewCenter;

                // Add fractional offset for minutes within the hour
                // (e.g., at 14:30, scroll to halfway between 14:00 center and 15:00 center)
                int nextPosition = findNextHourMarkerPosition(targetPosition);
                if (nextPosition >= 0) {
                    View nextHourView = layoutManager.findViewByPosition(nextPosition);
                    if (nextHourView != null) {
                        int nextViewCenter = nextHourView.getTop() + (nextHourView.getHeight() / 2);
                        float minuteFraction = now.getMinute() / 60f;
                        int hourSlotHeight = nextViewCenter - hourViewCenter;
                        scrollTarget += (int) (minuteFraction * hourSlotHeight);
                    }
                }

                // Center the "now" position in the visible scroll viewport
                // Use scrollView height (not container) to account for container padding
                int visibleHeight = timelineScrollView.getHeight();
                scrollTarget -= (visibleHeight / 2);

                // Clamp to valid range
                scrollTarget = Math.max(0, scrollTarget);

                // Mark as programmatic BEFORE scrolling so the listener ignores this scroll
                isProgrammaticScroll = true;
                timelineScrollView.smoothScrollTo(0, scrollTarget);

                // Reset the programmatic flag after the scroll animation completes
                // smoothScrollTo takes ~250ms, 400ms gives comfortable margin
                timelineScrollView.postDelayed(() -> isProgrammaticScroll = false, 400);

                Log.d(TAG, String.format("Auto-scrolled to %02d:%02d (target Y: %d, viewport: %d)",
                        now.getHour(), now.getMinute(), scrollTarget, visibleHeight));

            } catch (Exception e) {
                Log.e(TAG, "Error auto-scrolling timeline", e);
                isProgrammaticScroll = false;  // Safety reset
            }
        });
    }

    /**
     * Find the adapter position of the hour marker for the given hour.
     *
     * Queries the adapter's item list directly instead of rebuilding it,
     * ensuring consistent positions.
     *
     * @param hour Hour to find (0-23)
     * @return Adapter position, or -1 if not found
     */
    private int findHourMarkerPosition(int hour) {
        List<TimelineItem> items = timelineAdapter.getItems();
        for (int i = 0; i < items.size(); i++) {
            TimelineItem item = items.get(i);
            if (item instanceof HourMarkerTimelineItem) {
                if (((HourMarkerTimelineItem) item).getHour() == hour) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * Find the next hour marker position after the given adapter position.
     *
     * Used to calculate the pixel height of one hour slot for minute-level
     * scroll precision.
     *
     * @param currentPosition Current adapter position
     * @return Next hour marker position, or -1 if not found
     */
    private int findNextHourMarkerPosition(int currentPosition) {
        List<TimelineItem> items = timelineAdapter.getItems();
        for (int i = currentPosition + 1; i < items.size(); i++) {
            if (items.get(i) instanceof HourMarkerTimelineItem) {
                return i;
            }
        }
        return -1;
    }

    // ========== FAB ==========

    private void setupFAB() {
        addMealFab.setOnClickListener(v -> createNewMeal());
    }

    private void createNewMeal() {
        Intent intent = new Intent(this, CreateMealActivity.class);
        intent.putExtra("selectedDate", currentDate.toString());
        startActivityForResult(intent, REQUEST_CREATE_MEAL);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK) {
            if (requestCode == REQUEST_CREATE_MEAL || requestCode == REQUEST_VIEW_MEAL) {
                loadMealsForDate(currentDate);
            }
        }
    }

    // ========== DATA LOADING ==========

    private void loadMealsForDate(LocalDate date) {
        mealRepository.getMealsForDate(date, true, new MealRepository.MealListCallback() {
            @Override
            public void onSuccess(List<Meal> meals) {
                runOnUiThread(() -> {
                    mealsForCurrentDate = meals;
                    calculateDailyNutritionTotal();
                    updateDailySummary();
                    updateTimeline();
                });
            }

            @Override
            public void onError(String errorMessage) {
                runOnUiThread(() -> {
                    mealsForCurrentDate = new ArrayList<>();
                    dailyNutritionTotal = new Nutrition();
                    updateDailySummary();
                    updateTimeline();
                });
            }
        });
    }

    // ========== NUTRITION CALCULATION ==========

    private void calculateDailyNutritionTotal() {
        dailyNutritionTotal = new Nutrition();

        for (Meal meal : mealsForCurrentDate) {
            Nutrition mealNutrition = meal.getNutrition();
            if (mealNutrition != null) {
                dailyNutritionTotal = dailyNutritionTotal.add(mealNutrition);
            }
        }
    }

    // ========== NAVIGATION ==========

    private void openMealDetails(Meal meal) {
        Intent intent = new Intent(this, MealDetailsActivity.class);
        intent.putExtra(MealDetailsActivity.EXTRA_MEAL_ID, meal.getId());
        startActivityForResult(intent, REQUEST_VIEW_MEAL);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}