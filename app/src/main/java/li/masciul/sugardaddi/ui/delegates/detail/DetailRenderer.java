package li.masciul.sugardaddi.ui.delegates.detail;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import li.masciul.sugardaddi.core.interfaces.Searchable;

/**
 * DetailRenderer - Core contract for detail screen content rendering
 *
 * Each renderer handles one combination of (ProductType, DataSource).
 * ItemDetailsActivity asks each renderer "can you display this?" and
 * the first one that says yes inflates its layout and populates it.
 *
 * RENDERER LIFECYCLE:
 * 1. supports(item) → checked to find the right renderer
 * 2. inflate(inflater, container) → creates the view hierarchy
 * 3. populate(view, item, language) → fills in the data
 * 4. onAmountChanged(view, item, amount, language) → real-time updates (optional)
 * 5. destroy() → cleanup when activity is destroyed
 *
 * SEPARATION OF CONCERNS:
 * - The renderer owns the layout and data binding for its content area
 * - ItemDetailsActivity owns the toolbar, menu, favorites, share, navigation
 * - NutritionLabelManager is used by renderers that need nutrition display
 *
 * RESOLUTION ORDER (same as delegates):
 * - OFF-specific renderer first (handles scores, allergens, images)
 * - Ciqual-specific renderer (handles scientific data, no images/scores)
 * - Default product renderer (generic fallback)
 * - Recipe renderer (ingredients, steps, nutrition)
 *
 * @version 1.0
 */
public interface DetailRenderer {

    /**
     * Can this renderer handle the given item?
     *
     * @param item The item to check (FoodProduct, Recipe, etc.)
     * @return true if this renderer should display this item
     */
    boolean supports(@NonNull Searchable item);

    /**
     * Inflate the detail layout into the container.
     *
     * Called once when the item is first displayed. The inflated view is
     * added to the container by the caller (ItemDetailsActivity).
     *
     * @param inflater Layout inflater
     * @param container The parent container (FrameLayout in activity_item_details)
     * @return The inflated content view
     */
    @NonNull
    View inflate(@NonNull LayoutInflater inflater, @NonNull ViewGroup container);

    /**
     * Populate the inflated layout with data from the item.
     *
     * Called after inflate(). The view parameter is the same view returned
     * by inflate(). This is where you find views, set text, load images, etc.
     *
     * @param view     The view returned by inflate()
     * @param item     The item to display
     * @param language Current language code
     */
    void populate(@NonNull View view, @NonNull Searchable item, @NonNull String language);

    /**
     * Handle custom amount changes for nutrition recalculation.
     *
     * Called when the user changes the amount in the nutrition section.
     * Default implementation does nothing — override in renderers
     * that support custom amount display (product renderers).
     *
     * @param view     The view returned by inflate()
     * @param item     The current item
     * @param amount   New amount in grams
     * @param language Current language code
     */
    default void onAmountChanged(@NonNull View view, @NonNull Searchable item,
                                 double amount, @NonNull String language) {
        // Default: no-op. Override in renderers that support amount changes.
    }

    /**
     * Get the title to display in the toolbar.
     *
     * @param item The current item
     * @param language Current language code
     * @return Title string, or null to use the default
     */
    @Nullable
    default String getToolbarTitle(@NonNull Searchable item, @NonNull String language) {
        return null; // Default: let the activity decide
    }

    /**
     * Clean up resources when the activity is destroyed.
     *
     * Override to cancel pending image loads, release listeners, etc.
     */
    default void destroy() {
        // Default: no-op
    }
}