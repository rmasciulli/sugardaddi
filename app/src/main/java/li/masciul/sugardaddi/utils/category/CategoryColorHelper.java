package li.masciul.sugardaddi.utils.category;

import com.google.android.material.color.MaterialColors;
import android.content.Context;
import androidx.core.content.ContextCompat;
import li.masciul.sugardaddi.R;

import java.util.HashMap;
import java.util.Map;

/**
 * CategoryColorHelper - Comprehensive color helper for theme-aware UI colors
 *
 * Provides colors for:
 * - Food categories
 * - Nutri-Score grades
 * - Theme-aware UI elements
 * - Status indicators
 * - Nutrition level indicators
 */
public class CategoryColorHelper {

    // Food Category Colors
    private static final Map<String, Integer> CATEGORY_COLOR_MAP = new HashMap<>();

    static {
        // English categories
        CATEGORY_COLOR_MAP.put("fruits", R.color.category_fruits);
        CATEGORY_COLOR_MAP.put("vegetables", R.color.category_vegetables);
        CATEGORY_COLOR_MAP.put("grains", R.color.category_grains);
        CATEGORY_COLOR_MAP.put("proteins", R.color.category_proteins);
        CATEGORY_COLOR_MAP.put("dairy", R.color.category_dairy);
        CATEGORY_COLOR_MAP.put("sweets", R.color.category_sweets);
        CATEGORY_COLOR_MAP.put("beverages", R.color.category_beverages);
        CATEGORY_COLOR_MAP.put("snacks", R.color.category_snacks);

        // French categories
        CATEGORY_COLOR_MAP.put("légumes", R.color.category_vegetables);
        CATEGORY_COLOR_MAP.put("céréales", R.color.category_grains);
        CATEGORY_COLOR_MAP.put("protéines", R.color.category_proteins);
        CATEGORY_COLOR_MAP.put("produits laitiers", R.color.category_dairy);
        CATEGORY_COLOR_MAP.put("sucreries", R.color.category_sweets);
        CATEGORY_COLOR_MAP.put("boissons", R.color.category_beverages);
        CATEGORY_COLOR_MAP.put("collations", R.color.category_snacks);
    }

    // ========================
    // FOOD CATEGORY COLORS
    // ========================

    public static int getCategoryColor(Context context, String category) {
        if (category == null) {
            return MaterialColors.getColor(context,
                    com.google.android.material.R.attr.colorPrimary,
                    ContextCompat.getColor(context, R.color.md_theme_primary));
        }

        String lowerCategory = category.toLowerCase().trim();
        Integer colorRes = CATEGORY_COLOR_MAP.get(lowerCategory);

        if (colorRes != null) {
            return ContextCompat.getColor(context, colorRes);
        }

        // Check for partial matches
        for (String key : CATEGORY_COLOR_MAP.keySet()) {
            if (lowerCategory.contains(key) || key.contains(lowerCategory)) {
                return ContextCompat.getColor(context, CATEGORY_COLOR_MAP.get(key));
            }
        }

        return MaterialColors.getColor(context,
                com.google.android.material.R.attr.colorPrimary,
                ContextCompat.getColor(context, R.color.md_theme_primary));
    }

    // ========================
    // NUTRI-SCORE COLORS
    // ========================

    public static int getNutriScoreColor(Context context, String grade) {
        if (grade == null || grade.isEmpty()) {
            return MaterialColors.getColor(context,
                    com.google.android.material.R.attr.colorOnSurfaceVariant,
                    ContextCompat.getColor(context, R.color.md_theme_onSurfaceVariant));
        }

        switch (grade.toLowerCase()) {
            case "a":
                return ContextCompat.getColor(context, R.color.nutri_score_a);
            case "b":
                return ContextCompat.getColor(context, R.color.nutri_score_b);
            case "c":
                return ContextCompat.getColor(context, R.color.nutri_score_c);
            case "d":
                return ContextCompat.getColor(context, R.color.nutri_score_d);
            case "e":
                return ContextCompat.getColor(context, R.color.nutri_score_e);
            default:
                return ContextCompat.getColor(context, R.color.nutri_score_unknown);
        }
    }

    public static int getNutriScoreBackgroundColor(Context context, String grade) {
        if (grade == null || grade.isEmpty()) {
            return MaterialColors.getColor(context,
                    com.google.android.material.R.attr.colorSurfaceVariant,
                    ContextCompat.getColor(context, R.color.md_theme_surfaceVariant));
        }

        switch (grade.toLowerCase()) {
            case "a":
                return ContextCompat.getColor(context, R.color.nutri_score_a_container);
            case "b":
                return ContextCompat.getColor(context, R.color.nutri_score_b_container);
            case "c":
                return ContextCompat.getColor(context, R.color.nutri_score_c_container);
            case "d":
                return ContextCompat.getColor(context, R.color.nutri_score_d_container);
            case "e":
                return ContextCompat.getColor(context, R.color.nutri_score_e_container);
            default:
                return MaterialColors.getColor(context,
                        com.google.android.material.R.attr.colorSurfaceVariant,
                        ContextCompat.getColor(context, R.color.md_theme_surfaceVariant));
        }
    }

    // ========================
    // NUTRITION LEVEL INDICATORS
    // ========================

    public static int getNutritionLevelColor(Context context, String level) {
        if (level == null) {
            return MaterialColors.getColor(context,
                    com.google.android.material.R.attr.colorOnSurface,
                    ContextCompat.getColor(context, R.color.md_theme_onSurface));
        }

        switch (level.toLowerCase()) {
            case "low":
            case "faible":
                return ContextCompat.getColor(context, R.color.nutrition_level_low);
            case "moderate":
            case "modéré":
                return ContextCompat.getColor(context, R.color.nutrition_level_moderate);
            case "high":
            case "élevé":
                return ContextCompat.getColor(context, R.color.nutrition_level_high);
            default:
                return MaterialColors.getColor(context,
                        com.google.android.material.R.attr.colorOnSurface,
                        ContextCompat.getColor(context, R.color.md_theme_onSurface));
        }
    }

    // ========================
    // STATUS INDICATORS
    // ========================

    public static int getStatusColor(Context context, String status) {
        switch (status.toLowerCase()) {
            case "success":
            case "loaded":
            case "cached":
                return ContextCompat.getColor(context, R.color.success);
            case "error":
            case "failed":
                // error is an M3 theme role - resolve via theme attr, not named color
                return MaterialColors.getColor(context,
                        com.google.android.material.R.attr.colorError,
                        ContextCompat.getColor(context, R.color.md_theme_error));
            case "warning":
            case "outdated":
                return ContextCompat.getColor(context, R.color.warning);
            case "loading":
            case "searching":
                return MaterialColors.getColor(context,
                        com.google.android.material.R.attr.colorSecondary,
                        ContextCompat.getColor(context, R.color.md_theme_secondary));
            default:
                return MaterialColors.getColor(context,
                        com.google.android.material.R.attr.colorOnSurface,
                        ContextCompat.getColor(context, R.color.md_theme_onSurface));
        }
    }

    // ========================
    // UI COLORS (using your existing color system)
    // ========================

    public static int getCardBackgroundColor(Context context) {
        return MaterialColors.getColor(context,
                com.google.android.material.R.attr.colorSurfaceContainerLow,
                ContextCompat.getColor(context, R.color.md_theme_surfaceContainerLow));
    }

    public static int getTextPrimaryColor(Context context) {
        return MaterialColors.getColor(context,
                com.google.android.material.R.attr.colorOnSurface,
                ContextCompat.getColor(context, R.color.md_theme_onSurface));
    }

    public static int getTextSecondaryColor(Context context) {
        return MaterialColors.getColor(context,
                com.google.android.material.R.attr.colorOnSurfaceVariant,
                ContextCompat.getColor(context, R.color.md_theme_onSurfaceVariant));
    }

    public static int getButtonColor(Context context) {
        return MaterialColors.getColor(context,
                com.google.android.material.R.attr.colorPrimary,
                ContextCompat.getColor(context, R.color.md_theme_primary));
    }

    public static int getBackgroundColor(Context context) {
        return MaterialColors.getColor(context,
                android.R.attr.colorBackground,
                ContextCompat.getColor(context, R.color.md_theme_background));
    }

    public static int getSurfaceColor(Context context) {
        return MaterialColors.getColor(context,
                com.google.android.material.R.attr.colorSurface,
                ContextCompat.getColor(context, R.color.md_theme_surface));
    }

    public static int getFavoriteActiveColor(Context context) {
        return ContextCompat.getColor(context, R.color.favorite_active);
    }

    public static int getFavoriteInactiveColor(Context context) {
        return ContextCompat.getColor(context, R.color.favorite_inactive);
    }

    // ========================
    // UTILITY METHODS
    // ========================

    /**
     * Get contrasting text color for any background
     */
    public static int getContrastingTextColor(Context context, int backgroundColor) {
        // Simple luminance check
        int red = (backgroundColor >> 16) & 0xff;
        int green = (backgroundColor >> 8) & 0xff;
        int blue = backgroundColor & 0xff;

        double luminance = (0.299 * red + 0.587 * green + 0.114 * blue) / 255;

        if (luminance > 0.5) {
            return getTextPrimaryColor(context); // Dark text for light backgrounds
        } else {
            return ContextCompat.getColor(context, R.color.white); // Light text for dark backgrounds
        }
    }
}