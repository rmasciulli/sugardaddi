package li.masciul.sugardaddi.core.enums;

/**
 * Difficulty - Recipe difficulty levels
 *
 * Used for:
 * - Recipe categorization and filtering
 * - User skill level matching
 * - Time estimation assistance
 * - UI display and sorting
 */
public enum Difficulty {

    VERY_EASY("very_easy", "Very Easy", "⭐", "No cooking required",
            0, 15, "Minimal prep, ready in minutes"),

    EASY("easy", "Easy", "⭐⭐", "Basic cooking skills",
            10, 45, "Simple techniques, common ingredients"),

    MEDIUM("medium", "Medium", "⭐⭐⭐", "Some cooking experience",
            30, 90, "Multiple steps, moderate techniques"),

    HARD("hard", "Hard", "⭐⭐⭐⭐", "Advanced cooking skills",
            60, 180, "Complex techniques, precise timing"),

    EXPERT("expert", "Expert", "⭐⭐⭐⭐⭐", "Professional level",
            120, 300, "Advanced techniques, specialized equipment"),

    UNKNOWN("unknown", "Unknown", "❓", "Difficulty not specified",
            0, 0, "No difficulty information available");

    private final String id;
    private final String displayName;
    private final String stars;
    private final String description;
    private final int minTimeMinutes;     // Minimum typical time
    private final int maxTimeMinutes;     // Maximum typical time
    private final String skillDescription;

    Difficulty(String id, String displayName, String stars, String description,
               int minTimeMinutes, int maxTimeMinutes, String skillDescription) {
        this.id = id;
        this.displayName = displayName;
        this.stars = stars;
        this.description = description;
        this.minTimeMinutes = minTimeMinutes;
        this.maxTimeMinutes = maxTimeMinutes;
        this.skillDescription = skillDescription;
    }

    // ========== GETTERS ==========

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getStars() { return stars; }
    public String getDescription() { return description; }
    public int getMinTimeMinutes() { return minTimeMinutes; }
    public int getMaxTimeMinutes() { return maxTimeMinutes; }
    public String getSkillDescription() { return skillDescription; }

    // ========== UTILITY METHODS ==========

    /**
     * Get Difficulty from string ID
     */
    public static Difficulty fromId(String id) {
        if (id == null) return UNKNOWN;

        for (Difficulty difficulty : values()) {
            if (difficulty.id.equalsIgnoreCase(id)) {
                return difficulty;
            }
        }
        return UNKNOWN;
    }

    /**
     * Get Difficulty from display name
     */
    public static Difficulty fromDisplayName(String displayName) {
        if (displayName == null) return UNKNOWN;

        for (Difficulty difficulty : values()) {
            if (difficulty.displayName.equalsIgnoreCase(displayName)) {
                return difficulty;
            }
        }
        return UNKNOWN;
    }

    /**
     * Get difficulty from total time
     */
    public static Difficulty fromTime(int totalMinutes) {
        if (totalMinutes <= 0) return UNKNOWN;

        for (Difficulty difficulty : values()) {
            if (difficulty != UNKNOWN &&
                    totalMinutes >= difficulty.minTimeMinutes &&
                    totalMinutes <= difficulty.maxTimeMinutes) {
                return difficulty;
            }
        }

        // If time is outside ranges, pick closest
        if (totalMinutes < VERY_EASY.minTimeMinutes) return VERY_EASY;
        if (totalMinutes > EXPERT.maxTimeMinutes) return EXPERT;

        return MEDIUM; // Default fallback
    }

    /**
     * Get difficulty level as number (1-5, 0 for unknown)
     */
    public int getLevel() {
        switch (this) {
            case VERY_EASY: return 1;
            case EASY: return 2;
            case MEDIUM: return 3;
            case HARD: return 4;
            case EXPERT: return 5;
            default: return 0;
        }
    }

    /**
     * Get display text with stars
     */
    public String getDisplayWithStars() {
        return stars + " " + displayName;
    }

    /**
     * Get estimated time range text
     */
    public String getTimeRangeText() {
        if (this == UNKNOWN) return "Unknown time";

        if (minTimeMinutes == maxTimeMinutes) {
            return formatTime(minTimeMinutes);
        }

        return formatTime(minTimeMinutes) + " - " + formatTime(maxTimeMinutes);
    }

    /**
     * Check if this difficulty is suitable for beginners
     */
    public boolean isBeginnerFriendly() {
        return this == VERY_EASY || this == EASY;
    }

    /**
     * Check if this difficulty requires advanced skills
     */
    public boolean isAdvanced() {
        return this == HARD || this == EXPERT;
    }

    /**
     * Get the next difficulty level up
     */
    public Difficulty getNext() {
        switch (this) {
            case VERY_EASY: return EASY;
            case EASY: return MEDIUM;
            case MEDIUM: return HARD;
            case HARD: return EXPERT;
            default: return this;
        }
    }

    /**
     * Get the previous difficulty level down
     */
    public Difficulty getPrevious() {
        switch (this) {
            case EXPERT: return HARD;
            case HARD: return MEDIUM;
            case MEDIUM: return EASY;
            case EASY: return VERY_EASY;
            default: return this;
        }
    }

    /**
     * Compare difficulties by level
     */
    public int compareLevel(Difficulty other) {
        return Integer.compare(this.getLevel(), other.getLevel());
    }

    // ========== HELPER METHODS ==========

    private String formatTime(int minutes) {
        if (minutes < 60) {
            return minutes + " min";
        } else {
            int hours = minutes / 60;
            int remainingMinutes = minutes % 60;
            if (remainingMinutes == 0) {
                return hours + "h";
            } else {
                return hours + "h " + remainingMinutes + "m";
            }
        }
    }

    @Override
    public String toString() {
        return displayName;
    }
}