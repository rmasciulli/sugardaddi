package li.masciul.sugardaddi.ui.delegates.detail;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import li.masciul.sugardaddi.core.interfaces.Searchable;

import java.util.ArrayList;
import java.util.List;

/**
 * DetailRendererRegistry - Resolves items to their detail screen renderer.
 *
 * ARCHITECTURE:
 * This is the detail-screen parallel to DelegateRegistry (which handles search result cards).
 * Where DelegateRegistry drives a RecyclerView, DetailRendererRegistry drives a single-item
 * full-screen display inside ItemDetailsActivity.
 *
 * RESOLUTION ORDER (register specific before generic):
 *   1. OffProductDetailRenderer     — FoodProduct + DataSource.OPENFOODFACTS
 *   2. CiqualProductDetailRenderer  — FoodProduct + DataSource.CIQUAL
 *   3. DefaultProductDetailRenderer — FoodProduct (any remaining source — catch-all)
 *   (Future: RecipeDetailRenderer, etc.)
 *
 * USAGE IN ItemDetailsActivity:
 * <pre>
 *   DetailRendererRegistry registry = new DetailRendererRegistry();
 *   registry.register(new OffProductDetailRenderer(context));
 *   registry.register(new CiqualProductDetailRenderer(context));
 *   registry.register(new DefaultProductDetailRenderer(context));
 *
 *   DetailRenderer renderer = registry.resolve(product);
 *   View contentView = renderer.inflate(inflater, container);
 *   renderer.populate(contentView, product, language);
 * </pre>
 *
 * @version 1.0
 */
public class DetailRendererRegistry {

    private static final String TAG = "DetailRendererRegistry";

    /** Renderers in registration order — first match wins in resolve() */
    private final List<DetailRenderer> renderers = new ArrayList<>();

    // ========== REGISTRATION ==========

    /**
     * Register a renderer. Registration order matters — first match wins in resolve().
     * Register specific renderers (OFF, Ciqual) before generic fallbacks (Default).
     *
     * @param renderer The renderer to register
     */
    public void register(@NonNull DetailRenderer renderer) {
        renderers.add(renderer);
        Log.d(TAG, "Registered renderer: " + renderer.getClass().getSimpleName());
    }

    // ========== RESOLUTION ==========

    /**
     * Find the first renderer that supports this item.
     *
     * Iterates renderers in registration order, returns the first one where
     * supports(item) returns true.
     *
     * This should never fail if a catch-all DefaultProductDetailRenderer is registered last.
     *
     * @param item The item to display
     * @return The matching renderer
     * @throws IllegalStateException if no renderer supports the item
     *         (this means you forgot to register a fallback renderer)
     */
    @NonNull
    public DetailRenderer resolve(@NonNull Searchable item) {
        for (DetailRenderer renderer : renderers) {
            if (renderer.supports(item)) {
                Log.d(TAG, "Resolved renderer: " + renderer.getClass().getSimpleName()
                        + " for item type=" + item.getProductType()
                        + ", source=" + item.getDataSource());
                return renderer;
            }
        }

        // If we reach this, no fallback was registered. That's a programming error.
        throw new IllegalStateException(
                "No detail renderer supports item: " + item.getClass().getSimpleName()
                        + " (type=" + item.getProductType()
                        + ", source=" + item.getDataSource() + "). "
                        + "Did you forget to register a DefaultProductDetailRenderer?");
    }

    /**
     * Find the first renderer that supports this item, or null.
     *
     * Use when you want to handle the no-match case yourself rather than via exception.
     *
     * @param item The item to display
     * @return The matching renderer, or null if none found
     */
    @Nullable
    public DetailRenderer resolveOrNull(@NonNull Searchable item) {
        for (DetailRenderer renderer : renderers) {
            if (renderer.supports(item)) {
                return renderer;
            }
        }
        return null;
    }

    /**
     * Number of registered renderers (useful for assertions/tests).
     *
     * @return Count of registered renderers
     */
    public int size() {
        return renderers.size();
    }
}