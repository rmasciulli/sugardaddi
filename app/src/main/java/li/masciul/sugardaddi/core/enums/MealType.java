package li.masciul.sugardaddi.core.enums;

import android.content.Context;

import java.time.LocalTime;

import li.masciul.sugardaddi.R;

/**
 * MealType - Defines different types of meals
 *
 * Used for:
 * - Meal categorization and organization
 * - Automatic meal suggestions based on time
 * - Nutrition goal tracking per meal type
 * - UI filtering and display
 * - Localized display names via getLocalizedName(context)
 *
 * IMPORTANT: For UI display, use getLocalizedName(context) instead of
 * getDisplayName() to respect the user's language preference.
 */
public enum MealType {

    BREAKFAST("breakfast", "Breakfast", "🌅", LocalTime.of(7, 0), LocalTime.of(10, 0),
            "Morning meal to start the day"),

    MORNING_SNACK("morning_snack", "Morning Snack", "🍎", LocalTime.of(10, 0), LocalTime.of(12, 0),
            "Light snack between breakfast and lunch"),

    LUNCH("lunch", "Lunch", "☀️", LocalTime.of(12, 0), LocalTime.of(14, 0),
            "Midday meal"),

    AFTERNOON_SNACK("afternoon_snack", "Afternoon Snack", "🥨", LocalTime.of(14, 0), LocalTime.of(18, 0),
            "Light snack between lunch and dinner"),

    DINNER("dinner", "Dinner", "🌙", LocalTime.of(18, 0), LocalTime.of(21, 0),
            "Evening meal"),

    EVENING_SNACK("evening_snack", "Evening Snack", "🍪", LocalTime.of(21, 0), LocalTime.of(6, 59),
            "Light snack before bed"),

    SNACK("snack", "Snack", "🍿", null, null,
            "General snack (time-independent)"),

    OTHER("other", "Other", "🍽️", null, null,
            "Other meal or custom timing");

    private final String id;
    private final String displayName;
    private final String emoji;
    private final LocalTime typicalStartTime;
    private final LocalTime typicalEndTime;
    private final String description;

    MealType(String id, String displayName, String emoji,
             LocalTime typicalStartTime, LocalTime typicalEndTime, String description) {
        this.id = id;
        this.displayName = displayName;
        this.emoji = emoji;
        this.typicalStartTime = typicalStartTime;
        this.typicalEndTime = typicalEndTime;
        this.description = description;
    }

    // ========== GETTERS ==========

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getEmoji() { return emoji; }
    public LocalTime getTypicalStartTime() { return typicalStartTime; }
    public LocalTime getTypicalEndTime() { return typicalEndTime; }
    public String getDescription() { return description; }

    /**
     * Get localized display name for this meal type
     *
     * Returns the appropriate string resource based on the current app language.
     * This should be used instead of getDisplayName() which returns hardcoded English.
     *
     * @param context Android context for accessing string resources
     * @return Localized meal type name
     *
     * @example
     * String localizedName = MealType.BREAKFAST.getLocalizedName(context);
     */
    public String getLocalizedName(Context context) {
        if (context == null) {
            return displayName;  // Fallback to English if no context
        }

        switch (this) {
            case BREAKFAST:
                return context.getString(R.string.meal_type_breakfast);
            case MORNING_SNACK:
                return context.getString(R.string.meal_type_morning_snack);
            case LUNCH:
                return context.getString(R.string.meal_type_lunch);
            case AFTERNOON_SNACK:
                return context.getString(R.string.meal_type_afternoon_snack);
            case DINNER:
                return context.getString(R.string.meal_type_dinner);
            case EVENING_SNACK:
                return context.getString(R.string.meal_type_evening_snack);
            case SNACK:
                return context.getString(R.string.meal_type_snack);
            case OTHER:
                return context.getString(R.string.meal_type_other);
            default:
                return displayName;  // Fallback to English
        }
    }

    // ========== UTILITY METHODS ==========

    /**
     * Get MealType from string ID
     */
    public static MealType fromId(String id) {
        if (id == null) return null;

        for (MealType type : values()) {
            if (type.id.equalsIgnoreCase(id)) {
                return type;
            }
        }
        return null;
    }

    /**
     * Get MealType from display name
     */
    public static MealType fromDisplayName(String displayName) {
        if (displayName == null) return null;

        for (MealType type : values()) {
            if (type.displayName.equalsIgnoreCase(displayName)) {
                return type;
            }
        }
        return null;
    }

    /**
     * Get the most appropriate meal type for a given time
     */
    public static MealType fromTime(LocalTime time) {
        if (time == null) return OTHER;

        for (MealType type : values()) {
            if (type.typicalStartTime != null && type.typicalEndTime != null) {
                if (isTimeInRange(time, type.typicalStartTime, type.typicalEndTime)) {
                    return type;
                }
            }
        }

        return OTHER;
    }

    /**
     * Get all main meal types (excludes snacks)
     */
    public static MealType[] getMainMeals() {
        return new MealType[]{BREAKFAST, LUNCH, DINNER};
    }

    /**
     * Get all snack types
     */
    public static MealType[] getSnacks() {
        return new MealType[]{MORNING_SNACK, AFTERNOON_SNACK, EVENING_SNACK, SNACK};
    }

    /**
     * Get display text with emoji
     */
    public String getDisplayWithEmoji() {
        return emoji + " " + displayName;
    }

    /**
     * Check if this is a main meal (not a snack)
     */
    public boolean isMainMeal() {
        return this == BREAKFAST || this == LUNCH || this == DINNER;
    }

    /**
     * Check if this is a snack
     */
    public boolean isSnack() {
        return this == MORNING_SNACK || this == AFTERNOON_SNACK ||
                this == EVENING_SNACK || this == SNACK;
    }

    /**
     * Check if time falls within typical range for this meal
     */
    public boolean isTypicalTime(LocalTime time) {
        if (time == null || typicalStartTime == null || typicalEndTime == null) {
            return false;
        }
        return isTimeInRange(time, typicalStartTime, typicalEndTime);
    }

    /**
     * Get the next meal type after this one
     */
    public MealType getNext() {
        switch (this) {
            case BREAKFAST: return MORNING_SNACK;
            case MORNING_SNACK: return LUNCH;
            case LUNCH: return AFTERNOON_SNACK;
            case AFTERNOON_SNACK: return DINNER;
            case DINNER: return EVENING_SNACK;
            case EVENING_SNACK: return BREAKFAST; // Next day
            default: return BREAKFAST;
        }
    }

    /**
     * Get the previous meal type before this one
     */
    public MealType getPrevious() {
        switch (this) {
            case BREAKFAST: return EVENING_SNACK; // Previous day
            case MORNING_SNACK: return BREAKFAST;
            case LUNCH: return MORNING_SNACK;
            case AFTERNOON_SNACK: return LUNCH;
            case DINNER: return AFTERNOON_SNACK;
            case EVENING_SNACK: return DINNER;
            default: return DINNER;
        }
    }

    /**
     * Get typical calorie percentage for this meal type
     */
    public double getTypicalCaloriePercentage() {
        switch (this) {
            case BREAKFAST: return 0.25;        // 25% of daily calories
            case LUNCH: return 0.35;            // 35% of daily calories
            case DINNER: return 0.30;           // 30% of daily calories
            case MORNING_SNACK:
            case AFTERNOON_SNACK:
            case EVENING_SNACK: return 0.05;    // 5% each snack
            default: return 0.10;               // 10% for other
        }
    }

    // ========== HELPER METHODS ==========

    private static boolean isTimeInRange(LocalTime time, LocalTime start, LocalTime end) {
        if (start.isBefore(end)) {
            // Same day range (e.g., 12:00 to 14:00)
            return !time.isBefore(start) && time.isBefore(end);
        } else {
            // Crosses midnight (e.g., 21:00 to 06:00)
            return !time.isBefore(start) || time.isBefore(end);
        }
    }

    @Override
    public String toString() {
        return displayName;
    }
}