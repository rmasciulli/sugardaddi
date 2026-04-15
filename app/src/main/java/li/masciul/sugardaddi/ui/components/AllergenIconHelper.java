package li.masciul.sugardaddi.ui.components;

import android.content.Context;
import android.graphics.Rect;
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
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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
 * // Multiple allergens in a row
 * int allergens = product.getAllergenFlags();
 * ViewGroup icons = AllergenIconHelper.createMultipleIcons(context, allergens, 64, true);
 * layout.addView(icons);
 * </pre>
 */
public class AllergenIconHelper {

    // ========== ICON SIZE CONSTANTS ==========
    private static final float TEXT_SIZE_RATIO = 0.12f;  // Text size relative to icon size
    private static final float LABEL_PADDING_RATIO = 0.08f;  // Padding around labels

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
        label.setTextColor(ContextCompat.getColor(context, android.R.color.black));
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
     * Create allergen icons in a proper grid layout
     * Uses RecyclerView with GridLayoutManager for consistent column alignment
     *
     * @param context Android context
     * @param allergenFlags Combined allergen flags
     * @param sizeDp Size of each icon in dp
     * @param showContains true for "contains" versions
     * @return RecyclerView configured as a grid
     */
    @NonNull
    public static RecyclerView createMultipleIconsGrid(@NonNull Context context,
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
        int minSpacingPx = dpToPx(context, 8);

        // Calculate span count (how many columns fit)
        int spanCount = Math.max(1, (availableWidth + minSpacingPx) / (sizePx + minSpacingPx));

        // Calculate EXACT spacing to fill width perfectly
        int exactSpacingPx;
        if (spanCount == 1) {
            exactSpacingPx = 0;
        } else {
            int totalIconWidth = spanCount * sizePx;
            int remainingSpace = availableWidth - totalIconWidth;
            exactSpacingPx = Math.max(0, remainingSpace / (spanCount - 1));
        }

        // Create RecyclerView
        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        // Set GridLayoutManager
        GridLayoutManager gridLayoutManager = new GridLayoutManager(context, spanCount);
        recyclerView.setLayoutManager(gridLayoutManager);

        // Add spacing decoration WITHOUT edge spacing
        recyclerView.addItemDecoration(new GridSpacingItemDecoration(spanCount, exactSpacingPx, false));

        // Set adapter
        recyclerView.setAdapter(new AllergenGridAdapter(icons, sizePx));

        // Disable nested scrolling
        recyclerView.setNestedScrollingEnabled(false);

        return recyclerView;
    }

    /**
     * Simple adapter for allergen icon grid
     */
    private static class AllergenGridAdapter extends RecyclerView.Adapter<AllergenGridAdapter.ViewHolder> {
        private final List<View> icons;
        private final int iconSizePx;

        AllergenGridAdapter(List<View> icons, int iconSizePx) {
            this.icons = icons;
            this.iconSizePx = iconSizePx;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            FrameLayout container = new FrameLayout(parent.getContext());
            container.setLayoutParams(new RecyclerView.LayoutParams(
                    iconSizePx,                      // Width: fixed icon size
                    ViewGroup.LayoutParams.WRAP_CONTENT));  // Height: wrap content!
            return new ViewHolder(container);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.container.removeAllViews();
            holder.container.addView(icons.get(position));
        }

        @Override
        public int getItemCount() {
            return icons.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            final FrameLayout container;

            ViewHolder(FrameLayout container) {
                super(container);
                this.container = container;
            }
        }
    }

    /**
     * ItemDecoration for centered grid spacing
     * Adds equal spacing between items AND on the edges for centered appearance
     */
    private static class GridSpacingItemDecoration extends RecyclerView.ItemDecoration {
        private final int spanCount;
        private final int spacing;
        private final boolean includeEdge;

        GridSpacingItemDecoration(int spanCount, int spacing, boolean includeEdge) {
            this.spanCount = spanCount;
            this.spacing = spacing;
            this.includeEdge = includeEdge;
        }

        @Override
        public void getItemOffsets(@NonNull Rect outRect, @NonNull View view,
                                   @NonNull RecyclerView parent,
                                   @NonNull RecyclerView.State state) {
            int position = parent.getChildAdapterPosition(view);
            int column = position % spanCount;

            // Vertical spacing between rows is fixed at 5dp regardless of horizontal spacing.
            // The horizontal spacing fills the row width evenly — using that same value
            // vertically caused excessively large gaps between rows when icons are large.
            float density = view.getContext().getResources().getDisplayMetrics().density;
            int verticalSpacing = (int) (5 * density);

            if (includeEdge) {
                outRect.left = spacing - column * spacing / spanCount;
                outRect.right = (column + 1) * spacing / spanCount;

                if (position < spanCount) {
                    outRect.top = verticalSpacing;
                }
                outRect.bottom = verticalSpacing;
            } else {
                outRect.left = column * spacing / spanCount;
                outRect.right = spacing - (column + 1) * spacing / spanCount;

                if (position >= spanCount) {
                    outRect.top = verticalSpacing;
                }
            }
        }
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