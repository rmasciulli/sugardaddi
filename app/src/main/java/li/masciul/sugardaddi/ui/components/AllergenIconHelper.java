package li.masciul.sugardaddi.ui.components;

import android.content.Context;
import android.graphics.Typeface;

import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.google.android.material.color.MaterialColors;

import java.util.ArrayList;
import java.util.List;

import li.masciul.sugardaddi.R;
import li.masciul.sugardaddi.core.utils.AllergenUtils;

/**
 * AllergenIconHelper - Dynamic allergen icon display with localized labels
 *
 * Creates allergen icons with your custom drawable designs:
 * - Circular badge with icon illustration
 * - Top label: "Contains" (EN) / "Contient" (FR) OR Allergen name (free version)
 * - Bottom label: Allergen name OR "Free" (EN) / "Sans" (FR)
 *
 * Drawable naming convention:
 * - Contains version: allergens_gluten.xml
 * - Free version: allergens_gluten_free.xml (with diagonal line)
 *
 * Usage:
 * <pre>
 * // Single "contains" icon
 * View allergenBadge = AllergenIconHelper.createContainsIcon(context, AllergenUtils.GLUTEN, 80);
 * layout.addView(allergenBadge);
 *
 * // Single "free" icon
 * View freeIcon = AllergenIconHelper.createFreeIcon(context, AllergenUtils.GLUTEN, 80);
 * layout.addView(freeIcon);
 *
 * // Multiple allergens, compact wrapping layout
 * int allergens = product.getAllergenFlags();
 * View icons = AllergenIconHelper.createMultipleIconsGrid(context, allergens, 60, true);
 * layout.addView(icons);
 * </pre>
 */
public class AllergenIconHelper {

    // ========== ICON SIZE CONSTANTS ==========
    private static final float TEXT_SIZE_RATIO = 0.12f;  // Text size relative to icon size
    private static final float LABEL_PADDING_RATIO = 0.08f;  // Padding around labels

    // Fixed spacing between icons in createMultipleIconsGrid()'s wrapping
    // layout - always this value regardless of how many icons a given
    // product has, unlike the previous stretch-to-fill grid which spread
    // whatever few icons existed across the full row width.
    private static final int ICON_SPACING_DP = 12;

    /**
     * Allergen metadata for icon selection
     *
     * Note: Drawable references updated to match your naming:
     * - allergens_gluten.xml (contains version)
     * - allergens_gluten_free.xml (free version with diagonal line)
     */
    private enum AllergenInfo {
        GLUTEN(AllergenUtils.GLUTEN, R.string.allergen_gluten,
                R.drawable.allergens_gluten),
        CRUSTACEANS(AllergenUtils.CRUSTACEANS, R.string.allergen_crustaceans,
                R.drawable.allergens_crustaceans),
        EGGS(AllergenUtils.EGGS, R.string.allergen_eggs,
                R.drawable.allergens_eggs),
        FISH(AllergenUtils.FISH, R.string.allergen_fish,
                R.drawable.allergens_fish),
        PEANUTS(AllergenUtils.PEANUTS, R.string.allergen_peanuts,
                R.drawable.allergens_peanuts),
        SOY(AllergenUtils.SOY, R.string.allergen_soy,
                R.drawable.allergens_soy),
        MILK(AllergenUtils.MILK, R.string.allergen_milk,
                R.drawable.allergens_milk),
        NUTS(AllergenUtils.NUTS, R.string.allergen_nuts,
                R.drawable.allergens_nuts),
        CELERY(AllergenUtils.CELERY, R.string.allergen_celery,
                R.drawable.allergens_celery),
        MUSTARD(AllergenUtils.MUSTARD, R.string.allergen_mustard,
                R.drawable.allergens_mustard),
        SESAME(AllergenUtils.SESAME, R.string.allergen_sesame,
                R.drawable.allergens_sesame),
        SULFITES(AllergenUtils.SULFITES, R.string.allergen_sulfites,
                R.drawable.allergens_sulfites),
        LUPIN(AllergenUtils.LUPIN, R.string.allergen_lupin,
                R.drawable.allergens_lupin),
        MOLLUSCS(AllergenUtils.MOLLUSCS, R.string.allergen_molluscs,
                R.drawable.allergens_molluscs);

        final int allergenFlag;
        final int nameResId;
        final int drawableResId;

        AllergenInfo(int flag, int name, int drawable) {
            this.allergenFlag = flag;
            this.nameResId = name;
            this.drawableResId = drawable;
        }

        static AllergenInfo fromFlag(int flag) {
            for (AllergenInfo info : values()) {
                if (info.allergenFlag == flag) {
                    return info;
                }
            }
            throw new IllegalArgumentException("Unknown allergen flag: " + flag);
        }
    }

    // ========== PUBLIC API ==========

    /**
     * Create a "Contains XXX" allergen icon with text labels
     *
     * Creates a vertical layout containing:
     * - Circular allergen icon (from drawable)
     * - "Contains" label (localized: "Contains" in EN, "Contient" in FR)
     * - Allergen name label (localized: e.g., "Wheat" / "Blé")
     *
     * The icon is sized exactly to the specified dp dimensions using setAdjustViewBounds
     * to ensure consistent sizing across all allergens regardless of drawable intrinsic size.
     *
     * Usage:
     * <pre>
     * View glutenIcon = AllergenIconHelper.createContainsIcon(context, AllergenUtils.GLUTEN, 60);
     * container.addView(glutenIcon);
     * </pre>
     *
     * @param context Android context for resources and string localization
     * @param allergenFlag Single allergen bit flag (e.g., AllergenUtils.GLUTEN)
     * @param sizeDp Size of the circular icon in density-independent pixels (recommended: 60dp)
     * @return LinearLayout containing icon and labels, suitable for adding to any ViewGroup
     * @throws IllegalArgumentException if allergenFlag doesn't match any known allergen
     */
    @NonNull
    public static View createContainsIcon(@NonNull Context context, int allergenFlag, int sizeDp) {
        AllergenInfo info = AllergenInfo.fromFlag(allergenFlag);

        // Create vertical container (icon + text)
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.CENTER);
        int sizePx = dpToPx(context, sizeDp);
        container.setLayoutParams(new ViewGroup.LayoutParams(sizePx, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Create icon
        ImageView icon = new ImageView(context);
        icon.setImageResource(info.drawableResId);
        icon.setAdjustViewBounds(true);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);

        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(sizePx, sizePx);
        icon.setLayoutParams(iconParams);
        container.addView(icon);

        // Add small spacing
        View spacer = new View(context);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(context, 4)
        ));
        container.addView(spacer);

        // Add allergen name
        TextView label = new TextView(context);
        label.setText(info.nameResId); // "Wheat" / "Blé"
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        label.setTypeface(null, Typeface.NORMAL);
        label.setGravity(Gravity.CENTER);
        label.setTextColor(MaterialColors.getColor(context,
                com.google.android.material.R.attr.colorOnSurface,
                ContextCompat.getColor(context, R.color.md_theme_onSurface)));
        label.setMaxLines(2);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        container.addView(label);

        return container;
    }

    /**
     * Create an "XXX-Free" allergen icon with text labels
     *
     * Creates a vertical layout containing:
     * - Circular allergen icon with diagonal line (from allergens_XXX_free.xml drawable)
     * - Two localized labels arranged by language:
     *   - English: "Gluten" (top) / "Free" (bottom)
     *   - French: "Sans" (top) / "Gluten" (bottom)
     *
     * The icon is sized exactly to the specified dp dimensions using setAdjustViewBounds
     * to ensure consistent sizing across all allergens regardless of drawable intrinsic size.
     *
     * Usage:
     * <pre>
     * View glutenFreeIcon = AllergenIconHelper.createFreeIcon(context, AllergenUtils.GLUTEN, 60);
     * container.addView(glutenFreeIcon);
     * </pre>
     *
     * @param context Android context for resources and string localization
     * @param allergenFlag Single allergen bit flag (e.g., AllergenUtils.GLUTEN)
     * @param sizeDp Size of the circular icon in density-independent pixels (recommended: 50-60dp)
     * @return LinearLayout containing icon and labels, suitable for adding to any ViewGroup
     * @throws IllegalArgumentException if allergenFlag doesn't match any known allergen
     */
    @NonNull
    public static View createFreeIcon(@NonNull Context context, int allergenFlag, int sizeDp) {
        AllergenInfo info = AllergenInfo.fromFlag(allergenFlag);

        // Create vertical container
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.CENTER);
        int sizePx = dpToPx(context, sizeDp);
        container.setLayoutParams(new ViewGroup.LayoutParams(sizePx, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Create icon (free version)
        ImageView icon = new ImageView(context);
        icon.setImageResource(getFreeDrawable(info.allergenFlag));
        icon.setAdjustViewBounds(true);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);

        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(sizePx, sizePx);
        icon.setLayoutParams(iconParams);
        container.addView(icon);

        // Add spacing
        View spacer = new View(context);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(context, 4)
        ));
        container.addView(spacer);

        // Get current language for label arrangement
        String language = context.getResources().getConfiguration().locale.getLanguage();
        boolean isFrench = "fr".equals(language);

        // Add first label (allergen name for EN, "Sans" for FR)
        TextView label = new TextView(context);
        label.setText(info.nameResId);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9);
        label.setTypeface(null, Typeface.BOLD);
        label.setGravity(Gravity.CENTER);
        label.setTextColor(ContextCompat.getColor(context, android.R.color.black));
        label.setMaxLines(2);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        container.addView(label);

        return container;
    }

    /**
     * Create allergen icons in a compact, left-aligned wrapping layout.
     *
     * Fixed spacing (ICON_SPACING_DP) between icons, wrapping to a new row
     * only once the current row is genuinely full - unlike the previous
     * RecyclerView/GridLayoutManager implementation, which stretched
     * whatever few icons a product had across the entire row width,
     * producing oversized gaps for products with fewer allergens than a
     * full row's worth. A product with 2 allergens now shows 2 icons
     * packed together at the same density as one with 8.
     *
     * @param context Android context
     * @param allergenFlags Combined allergen flags
     * @param sizeDp Size of each icon in dp
     * @param showContains true for "contains" versions
     * @return LinearLayout (vertical) of one or more wrapped horizontal
     *         rows, suitable for adding to any ViewGroup
     */
    @NonNull
    public static View createMultipleIconsGrid(@NonNull Context context,
                                               int allergenFlags,
                                               int sizeDp,
                                               boolean showContains) {

        // Collect all icons
        List<View> icons = new ArrayList<>();
        for (AllergenInfo info : AllergenInfo.values()) {
            if ((allergenFlags & info.allergenFlag) != 0) {
                View icon = showContains
                        ? createContainsIcon(context, info.allergenFlag, sizeDp)
                        : createFreeIcon(context, info.allergenFlag, sizeDp);
                icons.add(icon);
            }
        }

        // Calculate available width
        int screenWidth = context.getResources().getDisplayMetrics().widthPixels;

        // Account for:
        // - Card margins: 16dp × 2 = 32dp
        // - Card content padding: 16dp × 2 = 32dp (from app:contentPadding in XML)
        // Total: 64dp
        int totalPaddingPx = dpToPx(context, 64);
        int availableWidth = screenWidth - totalPaddingPx;

        int sizePx = dpToPx(context, sizeDp);
        int spacingPx = dpToPx(context, ICON_SPACING_DP);

        // How many icons fit per row at this fixed spacing - unlike the
        // previous implementation, spacingPx is never stretched to fill
        // leftover row width, only used to compute how many icons fit.
        int perRow = Math.max(1, (availableWidth + spacingPx) / (sizePx + spacingPx));

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout currentRow = null;
        for (int i = 0; i < icons.size(); i++) {
            int column = i % perRow;

            if (column == 0) {
                currentRow = new LinearLayout(context);
                currentRow.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                if (i > 0) rowParams.topMargin = spacingPx;
                currentRow.setLayoutParams(rowParams);
                container.addView(currentRow);
            }

            View icon = icons.get(i);
            LinearLayout.LayoutParams iconParams =
                    new LinearLayout.LayoutParams(sizePx, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (column > 0) {
                iconParams.leftMargin = spacingPx;
            }
            icon.setLayoutParams(iconParams);
            currentRow.addView(icon);
        }

        return container;
    }

    // ========== PRIVATE HELPERS ==========

    /**
     * Create a text label for top or bottom of icon
     */
    private static TextView createLabel(Context context, int iconSizeDp, int gravity) {
        TextView label = new TextView(context);

        // Text size proportional to icon size
        float textSizePx = iconSizeDp * TEXT_SIZE_RATIO *
                context.getResources().getDisplayMetrics().density;
        label.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSizePx);

        // Bold uppercase text
        label.setTypeface(null, Typeface.BOLD);
        label.setAllCaps(true);
        label.setGravity(Gravity.CENTER);

        // Position the label
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);

        int paddingPx = dpToPx(context, (int)(iconSizeDp * LABEL_PADDING_RATIO));

        if (gravity == Gravity.TOP) {
            params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            label.setPadding(paddingPx, paddingPx, paddingPx, 0);
        } else {
            params.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            label.setPadding(paddingPx, 0, paddingPx, paddingPx);
        }

        label.setLayoutParams(params);
        return label;
    }

    /**
     * Get the "free" version drawable for an allergen
     *
     * Maps to your allergens_XXX_free.xml files (with diagonal line)
     */
    @DrawableRes
    private static int getFreeDrawable(int allergenFlag) {
        // Map to free version drawables (allergens_XXX_free.xml)
        switch (allergenFlag) {
            case AllergenUtils.GLUTEN:
                return R.drawable.allergens_gluten_free;
            case AllergenUtils.CRUSTACEANS:
                return R.drawable.allergens_crustaceans_free;
            case AllergenUtils.EGGS:
                return R.drawable.allergens_eggs_free;
            case AllergenUtils.FISH:
                return R.drawable.allergens_fish_free;
            case AllergenUtils.PEANUTS:
                return R.drawable.allergens_peanuts_free;
            case AllergenUtils.SOY:
                return R.drawable.allergens_soy_free;
            case AllergenUtils.MILK:
                return R.drawable.allergens_milk_free;
            case AllergenUtils.NUTS:
                return R.drawable.allergens_nuts_free;
            case AllergenUtils.CELERY:
                return R.drawable.allergens_celery_free;
            case AllergenUtils.MUSTARD:
                return R.drawable.allergens_mustard_free;
            case AllergenUtils.SESAME:
                return R.drawable.allergens_sesame_free;
            case AllergenUtils.SULFITES:
                return R.drawable.allergens_sulfites_free;
            case AllergenUtils.LUPIN:
                return R.drawable.allergens_lupin_free;
            case AllergenUtils.MOLLUSCS:
                return R.drawable.allergens_molluscs_free;
            default:
                throw new IllegalArgumentException("Unknown allergen flag: " + allergenFlag);
        }
    }

    /**
     * Convert dp to pixels
     */
    private static int dpToPx(Context context, int dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                context.getResources().getDisplayMetrics());
    }
}