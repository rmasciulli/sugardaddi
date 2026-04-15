package li.masciul.sugardaddi.ui.delegates;

import android.view.View;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import li.masciul.sugardaddi.core.interfaces.Searchable;

/**
 * ItemViewDelegate - Core contract for list item rendering in RecyclerView
 *
 * Each delegate is a self-contained unit responsible for:
 * - Deciding if it can handle a given item (canHandle)
 * - Providing the layout resource to inflate (getLayoutResId)
 * - Creating its ViewHolder from the inflated view (createViewHolder)
 * - Binding data to the ViewHolder (bind)
 *
 * DELEGATE RESOLUTION:
 * The DelegateRegistry iterates registered delegates in priority order.
 * The FIRST delegate where canHandle() returns true wins. This means:
 * - Source-specific delegates (OffProduct, CiqualProduct) register first
 * - Generic fallbacks (DefaultProduct) register last
 *
 * SHARED ACROSS ADAPTERS:
 * The same delegate instances are reused by SearchResultsAdapter,
 * FavoritesAdapter (removed — now uses the generic DelegateAdapter),
 * and any future list that displays Searchable items.
 *
 * TYPE SAFETY:
 * Each delegate declares its own ViewHolder type. The adapter performs
 * an unchecked cast in onBindViewHolder, which is safe because
 * getItemViewType → onCreateViewHolder → onBindViewHolder always
 * routes through the same delegate for a given viewType.
 *
 * @param <VH> The ViewHolder type this delegate creates and binds
 *
 * @version 1.0
 */
public interface ItemViewDelegate<VH extends RecyclerView.ViewHolder> {

    /**
     * Unique view type identifier for this delegate.
     *
     * Must be unique across all delegates in a registry. Used by RecyclerView
     * to route onCreateViewHolder calls to the correct delegate.
     *
     * Convention: use constants like VIEW_TYPE_PRODUCT_OFF = 1, etc.
     *
     * @return Unique integer identifying this view type
     */
    int getViewType();

    /**
     * Layout resource to inflate for this delegate's items.
     *
     * @return Layout resource ID (e.g., R.layout.item_search_product_off)
     */
    @LayoutRes
    int getLayoutResId();

    /**
     * Determine if this delegate can handle the given item.
     *
     * Resolution is first-match, so more specific delegates should
     * return true for narrower conditions (e.g., FoodProduct + OPENFOODFACTS)
     * while fallback delegates accept broader conditions (e.g., any FoodProduct).
     *
     * @param item The item to check
     * @return true if this delegate should handle this item
     */
    boolean canHandle(@NonNull Searchable item);

    /**
     * Create a ViewHolder from the inflated view.
     *
     * Called once per recycled view. The view has already been inflated
     * from getLayoutResId(). Find and cache view references here.
     *
     * @param view The inflated item view
     * @return A new ViewHolder wrapping this view
     */
    @NonNull
    VH createViewHolder(@NonNull View view);

    /**
     * Bind data to the ViewHolder.
     *
     * Called every time an item scrolls into view. Should be efficient —
     * avoid allocations, use cached formatters, etc.
     *
     * @param holder   The ViewHolder to bind data to
     * @param item     The Searchable item to display
     * @param language Current language code for localized content
     */
    void bind(@NonNull VH holder, @NonNull Searchable item, @NonNull String language);
}