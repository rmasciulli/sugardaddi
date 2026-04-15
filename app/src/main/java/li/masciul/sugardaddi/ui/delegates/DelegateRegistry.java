package li.masciul.sugardaddi.ui.delegates;

import android.util.Log;
import android.util.SparseArray;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import li.masciul.sugardaddi.core.interfaces.Searchable;

import java.util.ArrayList;
import java.util.List;

/**
 * DelegateRegistry - Manages ItemViewDelegates and resolves items to the correct delegate
 *
 * REGISTRATION ORDER MATTERS:
 * Delegates are checked in registration order. Register specific delegates first,
 * then generic fallbacks last. For example:
 *   1. OffProductSearchDelegate     (matches FoodProduct + OPENFOODFACTS)
 *   2. CiqualProductSearchDelegate  (matches FoodProduct + CIQUAL)
 *   3. DefaultProductSearchDelegate (matches any FoodProduct — fallback)
 *   4. RecipeSearchDelegate         (matches Recipe)
 *   5. FooterDelegate               (matches nothing in canHandle — special case)
 *
 * DUAL LOOKUP:
 * - resolve(Searchable) → iterates delegates, returns first canHandle match
 * - findByViewType(int) → direct lookup by viewType ID (O(1) via SparseArray)
 *
 * REUSABLE ACROSS ADAPTERS:
 * Create one registry for search results, another for favorites (without footer).
 * Or share the same registry if both lists show the same item types.
 *
 * @version 1.0
 */
public class DelegateRegistry {

    private static final String TAG = "DelegateRegistry";

    /** Delegates in registration order — used for canHandle resolution */
    private final List<ItemViewDelegate<?>> delegates = new ArrayList<>();

    /** ViewType → Delegate lookup — used for onCreateViewHolder */
    private final SparseArray<ItemViewDelegate<?>> viewTypeMap = new SparseArray<>();

    // ========== REGISTRATION ==========

    /**
     * Register a delegate. Order matters — first match wins in resolve().
     *
     * @param delegate The delegate to register
     * @throws IllegalArgumentException if a delegate with the same viewType is already registered
     */
    public void register(@NonNull ItemViewDelegate<?> delegate) {
        int viewType = delegate.getViewType();

        // Guard against duplicate viewType IDs
        if (viewTypeMap.get(viewType) != null) {
            throw new IllegalArgumentException(
                    "Delegate with viewType " + viewType + " already registered: " +
                            viewTypeMap.get(viewType).getClass().getSimpleName() +
                            " conflicts with " + delegate.getClass().getSimpleName());
        }

        delegates.add(delegate);
        viewTypeMap.put(viewType, delegate);

        Log.d(TAG, "Registered delegate: " + delegate.getClass().getSimpleName() +
                " (viewType=" + viewType + ")");
    }

    // ========== RESOLUTION ==========

    /**
     * Find the first delegate that can handle this item.
     *
     * Iterates delegates in registration order. Returns the first one
     * where canHandle() returns true.
     *
     * @param item The item to resolve
     * @return The matching delegate
     * @throws IllegalStateException if no delegate can handle the item
     */
    @NonNull
    public ItemViewDelegate<?> resolve(@NonNull Searchable item) {
        for (ItemViewDelegate<?> delegate : delegates) {
            if (delegate.canHandle(item)) {
                return delegate;
            }
        }

        // This should never happen if a fallback delegate is registered
        throw new IllegalStateException(
                "No delegate can handle item: " + item.getClass().getSimpleName() +
                        " (type=" + item.getProductType() + ", source=" + item.getDataSource() + ")");
    }

    /**
     * Find the first delegate that can handle this item, or null.
     *
     * @param item The item to resolve
     * @return The matching delegate, or null if none found
     */
    @Nullable
    public ItemViewDelegate<?> resolveOrNull(@NonNull Searchable item) {
        for (ItemViewDelegate<?> delegate : delegates) {
            if (delegate.canHandle(item)) {
                return delegate;
            }
        }
        return null;
    }

    /**
     * Find a delegate by its viewType ID.
     *
     * Used in onCreateViewHolder — RecyclerView gives us the viewType,
     * and we need to find the delegate that creates that ViewHolder.
     *
     * @param viewType The viewType returned by getItemViewType
     * @return The matching delegate
     * @throws IllegalStateException if no delegate has this viewType
     */
    @NonNull
    public ItemViewDelegate<?> findByViewType(int viewType) {
        ItemViewDelegate<?> delegate = viewTypeMap.get(viewType);
        if (delegate == null) {
            throw new IllegalStateException("No delegate registered for viewType: " + viewType);
        }
        return delegate;
    }

    // ========== UTILITY ==========

    /**
     * @return Number of registered delegates
     */
    public int size() {
        return delegates.size();
    }

    /**
     * Check if a delegate is registered for the given viewType.
     */
    public boolean hasViewType(int viewType) {
        return viewTypeMap.get(viewType) != null;
    }
}