package li.masciul.sugardaddi.ui.delegates;

/**
 * ViewType - Central registry of RecyclerView view type constants
 *
 * All delegates reference these constants for their getViewType() return value.
 * Centralizing them here prevents accidental collisions between delegates.
 *
 * NAMING CONVENTION:
 * - SEARCH_* for search result list delegates
 * - DETAIL_* for detail screen renderers (if ever needed)
 * - FOOTER for the pagination loading footer
 *
 * ADDING NEW TYPES:
 * 1. Add a constant here
 * 2. Create the delegate class
 * 3. Register it in the appropriate DelegateRegistry setup
 *
 * @version 1.0
 */
public final class ViewType {

    private ViewType() {
        // Prevent instantiation — constants only
    }

    // ========== PRODUCT VIEW TYPES ==========

    /** OpenFoodFacts product — image, brand, scores, categories */
    public static final int PRODUCT_OFF = 1;

    /** Ciqual product — scientific name, category, nutrition summary, no image */
    public static final int PRODUCT_CIQUAL = 2;

    /** USDA product (future) — similar to Ciqual */
    public static final int PRODUCT_USDA = 3;

    /** Default/fallback product — generic display for unknown sources */
    public static final int PRODUCT_DEFAULT = 10;

    // ========== RECIPE VIEW TYPES ==========

    /** Recipe — name, description, time, servings, difficulty */
    public static final int RECIPE = 20;

    // ========== SPECIAL VIEW TYPES ==========

    /** Loading footer for pagination */
    public static final int FOOTER = 99;
}