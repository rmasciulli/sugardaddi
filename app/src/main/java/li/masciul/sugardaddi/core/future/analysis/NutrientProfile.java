package li.masciul.sugardaddi.core.future.analysis;

/**
 * NutrientProfile - Product nutrition profile classifications
 *
 * Categorizes products based on their standout nutritional characteristics
 * compared to their category averages. Used for quick identification and
 * filtering of products with specific nutritional properties.
 *
 * STATUS: UNUSED - Reserved for product analysis feature
 * See: core/future/analysis/README.md
 *
 * TODO: Use for quick product filtering and badges (e.g., "High Protein" badge)
 */
public enum NutrientProfile {

    /**
     * High protein content compared to category
     */
    HIGH_PROTEIN("high_protein", "High Protein", "Rich in protein compared to similar products", "💪"),

    /**
     * Low sugar content compared to category
     */
    LOW_SUGAR("low_sugar", "Low Sugar", "Lower sugar content than category average", "🚫🍯"),

    /**
     * High fiber content compared to category
     */
    HIGH_FIBER("high_fiber", "High Fiber", "Good source of dietary fiber", "🌾"),

    /**
     * Low calorie compared to category
     */
    LOW_CALORIE("low_calorie", "Low Calorie", "Lower calorie content than similar products", "📉"),

    /**
     * Low sodium/salt compared to category
     */
    LOW_SODIUM("low_sodium", "Low Sodium", "Reduced sodium content", "🧂❌"),

    /**
     * High healthy fats (unsaturated) compared to category
     */
    HEALTHY_FATS("healthy_fats", "Healthy Fats", "Good source of healthy fats", "🥑"),

    /**
     * Rich in vitamins compared to category
     */
    VITAMIN_RICH("vitamin_rich", "Vitamin Rich", "High vitamin content", "🍊"),

    /**
     * Rich in minerals compared to category
     */
    MINERAL_RICH("mineral_rich", "Mineral Rich", "Good source of essential minerals", "⚡"),

    /**
     * Combination: High protein and low sugar
     */
    HIGH_PROTEIN_LOW_SUGAR("high_protein_low_sugar", "High Protein, Low Sugar",
            "Excellent protein source with minimal sugar", "💪🚫🍯"),

    /**
     * Combination: High fiber and low calorie
     */
    HIGH_FIBER_LOW_CALORIE("high_fiber_low_calorie", "High Fiber, Low Calorie",
            "Filling and low calorie", "🌾📉"),

    /**
     * Combination: Low sugar and low sodium
     */
    LOW_SUGAR_LOW_SODIUM("low_sugar_low_sodium", "Low Sugar, Low Sodium",
            "Heart-healthy choice", "❤️"),

    /**
     * Well-balanced nutrition profile
     */
    BALANCED("balanced", "Balanced", "Well-balanced nutritional profile", "⚖️"),

    /**
     * Standard nutrition profile (no standouts)
     */
    STANDARD("standard", "Standard", "Typical nutrition for this category", "📊"),

    /**
     * High calorie density
     */
    HIGH_CALORIE("high_calorie", "High Calorie", "Energy-dense food", "🔥"),

    /**
     * High sugar content
     */
    HIGH_SUGAR("high_sugar", "High Sugar", "Higher sugar content", "🍯⚠️"),

    /**
     * High sodium content
     */
    HIGH_SODIUM("high_sodium", "High Sodium", "Higher sodium content", "🧂⚠️"),

    /**
     * High saturated fat content
     */
    HIGH_SATURATED_FAT("high_saturated_fat", "High Saturated Fat", "Higher saturated fat content", "🧈⚠️"),

    /**
     * Nutritionally poor (multiple concerning nutrients)
     */
    POOR_NUTRITION("poor_nutrition", "Poor Nutrition", "Multiple nutritional concerns", "⚠️"),

    /**
     * Unknown or insufficient data for classification
     */
    UNKNOWN("unknown", "Unknown", "Insufficient data for classification", "❓");

    private final String id;
    private final String displayName;
    private final String description;
    private final String emoji;

    NutrientProfile(String id, String displayName, String description, String emoji) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.emoji = emoji;
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public String getEmoji() { return emoji; }

    /**
     * Get profile by ID
     */
    public static NutrientProfile fromId(String id) {
        for (NutrientProfile profile : values()) {
            if (profile.getId().equals(id)) {
                return profile;
            }
        }
        return UNKNOWN;
    }

    /**
     * Check if this is a positive nutrition profile
     */
    public boolean isPositive() {
        switch (this) {
            case HIGH_PROTEIN:
            case LOW_SUGAR:
            case HIGH_FIBER:
            case LOW_CALORIE:
            case LOW_SODIUM:
            case HEALTHY_FATS:
            case VITAMIN_RICH:
            case MINERAL_RICH:
            case HIGH_PROTEIN_LOW_SUGAR:
            case HIGH_FIBER_LOW_CALORIE:
            case LOW_SUGAR_LOW_SODIUM:
            case BALANCED:
                return true;
            default:
                return false;
        }
    }

    /**
     * Check if this is a concerning nutrition profile
     */
    public boolean isConcerning() {
        switch (this) {
            case HIGH_SUGAR:
            case HIGH_SODIUM:
            case HIGH_SATURATED_FAT:
            case POOR_NUTRITION:
                return true;
            default:
                return false;
        }
    }

    /**
     * Check if this is a neutral nutrition profile
     */
    public boolean isNeutral() {
        return !isPositive() && !isConcerning();
    }

    /**
     * Get profiles suitable for specific dietary goals
     */
    public static NutrientProfile[] getProfilesForGoal(String goal) {
        switch (goal.toLowerCase()) {
            case "weight_loss":
                return new NutrientProfile[]{
                        LOW_CALORIE, HIGH_FIBER, LOW_SUGAR, HIGH_FIBER_LOW_CALORIE
                };
            case "muscle_building":
                return new NutrientProfile[]{
                        HIGH_PROTEIN, HIGH_PROTEIN_LOW_SUGAR, BALANCED
                };
            case "heart_health":
                return new NutrientProfile[]{
                        LOW_SODIUM, LOW_SUGAR_LOW_SODIUM, HEALTHY_FATS
                };
            case "diabetes":
                return new NutrientProfile[]{
                        LOW_SUGAR, HIGH_FIBER, LOW_SUGAR_LOW_SODIUM
                };
            case "general_health":
                return new NutrientProfile[]{
                        BALANCED, HIGH_FIBER, LOW_SODIUM, VITAMIN_RICH
                };
            default:
                return new NutrientProfile[]{BALANCED, STANDARD};
        }
    }

    /**
     * Get positive profiles (good choices)
     */
    public static NutrientProfile[] getPositiveProfiles() {
        return new NutrientProfile[]{
                HIGH_PROTEIN, LOW_SUGAR, HIGH_FIBER, LOW_CALORIE, LOW_SODIUM,
                HEALTHY_FATS, VITAMIN_RICH, MINERAL_RICH, HIGH_PROTEIN_LOW_SUGAR,
                HIGH_FIBER_LOW_CALORIE, LOW_SUGAR_LOW_SODIUM, BALANCED
        };
    }

    /**
     * Get concerning profiles (should be limited)
     */
    public static NutrientProfile[] getConcerningProfiles() {
        return new NutrientProfile[]{
                HIGH_SUGAR, HIGH_SODIUM, HIGH_SATURATED_FAT, POOR_NUTRITION
        };
    }

    /**
     * Get UI color for this profile
     */
    public String getUIColor() {
        if (isPositive()) {
            return "#4CAF50"; // Green
        } else if (isConcerning()) {
            return "#F44336"; // Red
        } else {
            return "#FF9800"; // Orange
        }
    }

    /**
     * Get priority score for ranking (higher = better)
     */
    public int getPriorityScore() {
        switch (this) {
            case HIGH_PROTEIN_LOW_SUGAR: return 95;
            case HIGH_FIBER_LOW_CALORIE: return 90;
            case LOW_SUGAR_LOW_SODIUM: return 88;
            case HIGH_PROTEIN: return 85;
            case HIGH_FIBER: return 83;
            case LOW_SUGAR: return 80;
            case LOW_CALORIE: return 78;
            case LOW_SODIUM: return 75;
            case HEALTHY_FATS: return 73;
            case VITAMIN_RICH: return 70;
            case MINERAL_RICH: return 68;
            case BALANCED: return 65;
            case STANDARD: return 50;
            case HIGH_CALORIE: return 30;
            case HIGH_SUGAR: return 20;
            case HIGH_SODIUM: return 15;
            case HIGH_SATURATED_FAT: return 10;
            case POOR_NUTRITION: return 5;
            case UNKNOWN: return 0;
            default: return 40;
        }
    }

    /**
     * Get dietary compatibility
     */
    public boolean isCompatibleWith(String dietaryRestriction) {
        switch (dietaryRestriction.toLowerCase()) {
            case "low_carb":
            case "keto":
                return this == HIGH_PROTEIN || this == HEALTHY_FATS || this == LOW_SUGAR;
            case "low_fat":
                return this != HEALTHY_FATS && this != HIGH_SATURATED_FAT;
            case "low_sodium":
                return this == LOW_SODIUM || this == LOW_SUGAR_LOW_SODIUM;
            case "diabetic":
                return this == LOW_SUGAR || this == HIGH_FIBER || this == LOW_SUGAR_LOW_SODIUM;
            case "heart_healthy":
                return this == LOW_SODIUM || this == HEALTHY_FATS || this == LOW_SUGAR_LOW_SODIUM;
            default:
                return true;
        }
    }

    /**
     * Get formatted display text with emoji
     */
    public String getFormattedDisplayName() {
        return emoji + " " + displayName;
    }

    /**
     * Get detailed description for UI tooltips
     */
    public String getDetailedDescription() {
        String base = description;

        if (isPositive()) {
            base += " - Good choice for most people.";
        } else if (isConcerning()) {
            base += " - Consider limiting consumption.";
        }

        return base;
    }
}