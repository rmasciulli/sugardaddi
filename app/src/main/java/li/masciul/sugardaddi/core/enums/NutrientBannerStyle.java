package li.masciul.sugardaddi.core.enums;

/**
 * NutrientBannerStyle - Visual design styles for nutrient display banners
 *
 * VERSION 2.0 - Updated for scalable, responsive design
 *
 * Key changes from v1:
 * - Removed fixed dimensions (banners now scale dynamically)
 * - Added aspect ratios (VERTICAL: 1:1.4, MODERN: 1:1.2)
 * - Removed absolute text positioning (uses LinearLayout weights)
 * - Simplified configuration (focuses on proportions, not pixels)
 *
 * Both styles use 3-section layout:
 * 1. Top section - Nutrient name
 * 2. Middle section - Value + unit (2 lines for energy: kcal + kj)
 * 3. Bottom section - % DRV (colored by NutrientLevel)
 *
 * @version 2.0 - Scalable responsive design
 */
public enum NutrientBannerStyle {

    /**
     * VERTICAL style - Rounded bottom, elegant design
     *
     * Aspect ratio: 1:1.4 (width:height)
     * Example: 70dp wide × 98dp tall
     *
     * Visual design:
     * ┌─────────────┐
     * │   Energy    │ ← Top 25%: White, nutrient name, black text
     * ├─────────────┤
     * │   356kcal   │ ← Middle 50%: White, value, black text
     * │   1258KJ    │   (2 lines for energy)
     * ├─────────────┤
     * │     12%     │ ← Bottom 25%: Colored rounded pill, white text
     * └─────────────┘
     *
     * Features:
     * - Clean, modern appearance
     * - Rounded bottom corners (12dp radius)
     * - White sections for name and value
     * - Colored bottom section based on NutrientLevel
     * - 1dp gray border around entire banner
     *
     * Section weights: 25-50-25
     */
    VERTICAL(
            1.4,   // Aspect ratio (height/width)
            25,    // Top section weight (name)
            50,    // Middle section weight (value)
            25     // Bottom section weight (percent)
    ),

    /**
     * MODERN style - Horizontal stripes, bold design
     *
     * Aspect ratio: 1:1.2 (width:height)
     * Example: 70dp wide × 84dp tall
     *
     * Visual design:
     * ┌─────────────┐
     * │   ENERGY    │ ← Top 20%: Blue strip, uppercase name, white text
     * ├─────────────┤
     * │   356kcal   │ ← Middle 60%: White section, value, black text
     * │   1258KJ    │   (2 lines for energy)
     * ├─────────────┤
     * │     12%     │ ← Bottom 20%: Colored strip, white text
     * └─────────────┘
     *
     * Features:
     * - Compact, information-dense
     * - Blue top strip for branding
     * - Uppercase nutrient names
     * - Square corners (no rounding)
     * - Colored bottom strip based on NutrientLevel
     * - 1dp gray border
     *
     * Section weights: 20-60-20
     */
    MODERN(
            1.2,   // Aspect ratio (height/width)
            20,    // Top section weight (name)
            60,    // Middle section weight (value)
            20     // Bottom section weight (percent)
    );

    // ========== ENUM FIELDS ==========

    private final double aspectRatio;  // height/width ratio
    private final int topWeight;       // Layout weight for top section
    private final int middleWeight;    // Layout weight for middle section
    private final int bottomWeight;    // Layout weight for bottom section

    NutrientBannerStyle(
            double aspectRatio,
            int topWeight,
            int middleWeight,
            int bottomWeight) {
        this.aspectRatio = aspectRatio;
        this.topWeight = topWeight;
        this.middleWeight = middleWeight;
        this.bottomWeight = bottomWeight;
    }

    // ========== GETTERS ==========

    /**
     * Get aspect ratio (height/width)
     *
     * @return Aspect ratio (e.g., 1.4 means height is 1.4× width)
     */
    public double getAspectRatio() {
        return aspectRatio;
    }

    /**
     * Get layout weight for top section (nutrient name)
     */
    public int getTopWeight() {
        return topWeight;
    }

    /**
     * Get layout weight for middle section (value + unit)
     */
    public int getMiddleWeight() {
        return middleWeight;
    }

    /**
     * Get layout weight for bottom section (% DRV)
     */
    public int getBottomWeight() {
        return bottomWeight;
    }

    /**
     * Get total weight sum (for LinearLayout weightSum)
     */
    public int getTotalWeight() {
        return topWeight + middleWeight + bottomWeight;
    }

    /**
     * Calculate height in dp for a given width
     *
     * @param widthDp Width in dp
     * @return Height in dp maintaining aspect ratio
     */
    public int calculateHeight(int widthDp) {
        return (int) (widthDp * aspectRatio);
    }

    // ========== DEPRECATED METHODS ==========

    /**
     * @deprecated No longer used in v2.0 (banners scale dynamically)
     */
    @Deprecated
    public int getWidthDp() {
        return 92;  // Default fallback
    }

    /**
     * @deprecated No longer used in v2.0 (banners scale dynamically)
     */
    @Deprecated
    public int getHeightDp() {
        return (int) (92 * aspectRatio);
    }

    /**
     * @deprecated No longer used in v2.0 (uses LinearLayout weights instead)
     */
    @Deprecated
    public String getDrawablePrefix() {
        return this == VERTICAL ? "vertical" : "modern";
    }

    /**
     * @deprecated No longer used in v2.0 (uses LinearLayout weights instead)
     */
    @Deprecated
    public TextSection getNameSection() {
        return new TextSection(15, 11, false);
    }

    /**
     * @deprecated No longer used in v2.0 (uses LinearLayout weights instead)
     */
    @Deprecated
    public TextSection getValueSection() {
        return new TextSection(70, 18, false);
    }

    /**
     * @deprecated No longer used in v2.0 (uses LinearLayout weights instead)
     */
    @Deprecated
    public TextSection getPercentSection() {
        return new TextSection(120, 24, false);
    }

    /**
     * @deprecated No longer used in v2.0 (colors applied programmatically)
     */
    @Deprecated
    public String getDrawableName(NutrientLevel level) {
        if (level == null) level = NutrientLevel.NEUTRAL;
        return "nutricards_" + getDrawablePrefix() + "_banner_" + level.getKey();
    }

    // ========== NESTED CLASS (DEPRECATED) ==========

    /**
     * @deprecated No longer used in v2.0 (uses LinearLayout weights instead of absolute positioning)
     */
    @Deprecated
    public static class TextSection {
        private final int yPositionDp;
        private final int textSizeSp;
        private final boolean uppercase;

        public TextSection(int yPositionDp, int textSizeSp, boolean uppercase) {
            this.yPositionDp = yPositionDp;
            this.textSizeSp = textSizeSp;
            this.uppercase = uppercase;
        }

        public int getYPositionDp() {
            return yPositionDp;
        }

        public int getTextSizeSp() {
            return textSizeSp;
        }

        public boolean isUppercase() {
            return uppercase;
        }
    }
}