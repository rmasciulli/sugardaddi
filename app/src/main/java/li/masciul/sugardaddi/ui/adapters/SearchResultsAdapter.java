package li.masciul.sugardaddi.ui.adapters;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import li.masciul.sugardaddi.R;
import li.masciul.sugardaddi.core.interfaces.Searchable;
import li.masciul.sugardaddi.core.models.Nutrition;
import li.masciul.sugardaddi.data.network.ApiConfig;
import li.masciul.sugardaddi.managers.LanguageManager;
import li.masciul.sugardaddi.ui.delegates.DelegateRegistry;
import li.masciul.sugardaddi.ui.delegates.search.CocktailDbRecipeSearchDelegate;
import li.masciul.sugardaddi.ui.delegates.search.DefaultRecipeSearchDelegate;
import li.masciul.sugardaddi.ui.delegates.search.FatSecretRecipeSearchDelegate;
import li.masciul.sugardaddi.ui.delegates.search.FooterDelegate;
import li.masciul.sugardaddi.ui.delegates.ItemViewDelegate;
import li.masciul.sugardaddi.ui.delegates.ViewType;
import li.masciul.sugardaddi.ui.delegates.search.CiqualProductSearchDelegate;
import li.masciul.sugardaddi.ui.delegates.search.DefaultProductSearchDelegate;
import li.masciul.sugardaddi.ui.delegates.search.MealDbRecipeSearchDelegate;
import li.masciul.sugardaddi.ui.delegates.search.OffProductSearchDelegate;
import li.masciul.sugardaddi.ui.delegates.search.USDAProductSearchDelegate;
import li.masciul.sugardaddi.ui.utils.NutritionGridHelper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * SearchResultsAdapter - Delegate-driven adapter for unified search results.
 *
 * All item rendering is handled by the DelegateRegistry. Each data source
 * (OpenFoodFacts, Ciqual) and item type (Recipe) has its own dedicated
 * delegate controlling layout, ViewHolder, and binding.
 *
 * DELEGATE RESOLUTION ORDER (first-match wins):
 *   1. OffProductSearchDelegate     -- FoodProduct from OPENFOODFACTS
 *   2. CiqualProductSearchDelegate  -- FoodProduct from CIQUAL
 *   3. DefaultProductSearchDelegate -- any other FoodProduct (fallback)
 *   4. DefaultRecipeSearchDelegate         -- Recipe items
 *   Footer is a special case at position items.size(), not resolved via registry.
 *
 * PAGINATION:
 *   Triggered by MainActivity's NestedScrollView scroll listener, NOT from
 *   onBindViewHolder, to avoid runaway loading when all items bind at once.
 *
 * @version 3.0 - Delegate-driven
 */
@SuppressWarnings("unchecked") // Safe: registry guarantees viewType -> delegate -> VH consistency
public class SearchResultsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final String TAG = ApiConfig.UI_LOG_TAG;

    // ========== STATE ==========

    private final Context context;
    private final DelegateRegistry registry;
    private final String currentLanguage;

    private List<Searchable> items = new ArrayList<>();

    // Positions currently showing their expandable_nutrition_detail
    // section - mirrors MealPortionsAdapter's own expandedPositions Set.
    // Card tap toggles this; navigateChevron (separate element, see
    // onCreateViewHolder) is what now calls onItemClick() instead.
    private final Set<Integer> expandedPositions = new HashSet<>();

    // Both null by default (every current caller). Non-null only when a
    // caller like MealDetailsActivity opts in - see setNutritionResolver()/
    // setQuantityResolver() Javadoc.
    @Nullable private NutritionResolver nutritionResolver;
    @Nullable private QuantityResolver quantityResolver;

    @Nullable private OnItemClickListener clickListener;
    @Nullable private OnLoadMoreListener loadMoreListener;

    private boolean isLoadingMore = false;
    private boolean hasMoreItems = true;
    /** False in FavoritesActivity -- all items load at once, no footer needed. */
    private boolean paginationEnabled = true;
    /**
     * Off by default - deliberately opt-in, not a global behavior change.
     * The expand-to-preview-nutrition feature (card tap expands,
     * navigateChevron navigates instead) is meant for a specific,
     * not-yet-built "pick an item to add to this meal" context, NOT for
     * MainActivity's general search or FavoritesActivity, which are the
     * two current callers of this adapter (confirmed via grep - there is
     * no third caller). Both keep their original whole-card-tap-navigates
     * behavior untouched unless setExpansionEnabled(true) is called.
     */
    private boolean expansionEnabled = false;

    // ========== CONSTRUCTOR ==========

    /**
     * Registers all delegates in priority order.
     * Specific sources (OFF, Ciqual) must come before the generic fallback.
     */
    public SearchResultsAdapter(@NonNull Context context, OnItemClickListener listener) {
        this.context = context;
        this.currentLanguage = LanguageManager.getCurrentLanguage(context).getCode();
        this.clickListener = listener;
        this.registry = new DelegateRegistry();
        registry.register(new OffProductSearchDelegate(context));
        registry.register(new CiqualProductSearchDelegate(context));
        registry.register(new USDAProductSearchDelegate(context));
        registry.register(new DefaultProductSearchDelegate(context));

        registry.register(new MealDbRecipeSearchDelegate(context));
        registry.register(new CocktailDbRecipeSearchDelegate(context));
        registry.register(new FatSecretRecipeSearchDelegate(context));
        registry.register(new DefaultRecipeSearchDelegate(context));
        registry.register(new FooterDelegate(context));

        setHasStableIds(true);
    }

    // ========== PUBLIC API ==========

    public void setOnItemClickListener(@Nullable OnItemClickListener listener) {
        this.clickListener = listener;
    }

    public void setOnLoadMoreListener(@Nullable OnLoadMoreListener listener) {
        this.loadMoreListener = listener;
    }

    /**
     * Opt into the expand-to-preview-nutrition feature - see
     * expansionEnabled's Javadoc. Off by default; not called by either
     * current caller (MainActivity, FavoritesActivity), so this has no
     * effect until something explicitly turns it on.
     */
    public void setExpansionEnabled(boolean enabled) {
        this.expansionEnabled = enabled;
    }

    /**
     * Supplies the nutrition grid's values when expanded, overriding the
     * item's own raw Nutritional.getNutrition() - used by
     * MealDetailsActivity to show portion-scaled nutrition (231g -> 271
     * kcal) instead of a generic per-100g figure. Null (default, every
     * current caller except MealDetailsActivity) means the grid uses the
     * item's own value unchanged.
     */
    public void setNutritionResolver(@Nullable NutritionResolver resolver) {
        this.nutritionResolver = resolver;
    }

    /**
     * Supplies the mealQuantityBadge's text - null (default) leaves the
     * badge hidden. The only real use today is MealDetailsActivity
     * showing how much of this item is actually in the meal ("231 g"),
     * something no search card has any other way to express - search
     * results have no notion of "quantity added," only the item itself.
     */
    public void setQuantityResolver(@Nullable QuantityResolver resolver) {
        this.quantityResolver = resolver;
    }

    /** See setNutritionResolver(). */
    public interface NutritionResolver {
        @Nullable Nutrition resolveNutrition(@NonNull Searchable item);
    }

    /** See setQuantityResolver(). */
    public interface QuantityResolver {
        @Nullable String resolveQuantityLabel(@NonNull Searchable item);
    }

    /** Replace all items and reset pagination. */
    public void updateItems(@Nullable List<Searchable> newItems, boolean hasMore) {
        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, String.format("updateItems: %d -> %d, hasMore=%b",
                    items.size(), newItems != null ? newItems.size() : 0, hasMore));
        }
        isLoadingMore = false;
        hasMoreItems = hasMore;
        expandedPositions.clear();
        items.clear();
        if (newItems != null) items.addAll(newItems);
        notifyDataSetChanged();
    }

    /**
     * Get a snapshot of current items for enrichment purposes.
     * Returns the live list - callers must not modify it directly.
     * Used by MainActivity.onActivityResumed() to re-enrich after returning
     * from ProductDetailsActivity without a full re-search.
     */
    public List<Searchable> getItems() {
        return items;
    }

    /** Append next page of items. */
    public void addMoreItems(@Nullable List<Searchable> newItems, boolean hasMore) {
        if (newItems == null || newItems.isEmpty()) {
            isLoadingMore = false;
            hasMoreItems = false;
            notifyItemChanged(items.size());
            return;
        }
        int oldSize = items.size();
        // Remove the footer from its old position BEFORE inserting new
        // items, rather than letting notifyItemRangeInserted implicitly
        // "shift" it forward. RecyclerView's default item animator has no
        // clean way to represent "this same footer view moved because
        // unrelated items were inserted before its old spot" - that
        // ambiguity is exactly what produced the loading indicator
        // rendering behind/between newly-inserted cards mid-animation.
        // Explicit remove-then-insert-as-one-block removes the ambiguity:
        // the footer is unambiguously a fresh item at its new index, not
        // something being animated as "moved."
        notifyItemRemoved(oldSize);
        items.addAll(newItems);
        isLoadingMore = false;
        hasMoreItems = hasMore;
        notifyItemRangeInserted(oldSize, newItems.size() + 1);
    }

    /** Disable pagination footer (e.g. in FavoritesActivity). */
    public void setPaginationEnabled(boolean enabled) {
        if (paginationEnabled != enabled) {
            paginationEnabled = enabled;
            if (!items.isEmpty()) notifyItemChanged(items.size());
        }
    }

    public void setLoadingMore(boolean loading) {
        if (isLoadingMore != loading) {
            isLoadingMore = loading;
            notifyItemChanged(items.size());
        }
    }

    // ========== RECYCLER VIEW OVERRIDES ==========

    @Override
    public int getItemCount() {
        if (items.isEmpty()) return 0;
        // +1 for footer - but only where a footer could ever be needed.
        // Previously this always reserved the slot and relied on
        // bindFooter() collapsing it to GONE when not paginating (every
        // caller with paginationEnabled=false: MealDetailsActivity,
        // FavoritesActivity) - a real, always-present RecyclerView item
        // whose content happens to be invisible, not the same as there
        // being no item at all. Gated on paginationEnabled specifically
        // (not hasMoreItems, which toggles live during MainActivity's
        // actual pagination) so this only removes the phantom slot where
        // pagination never applies in the first place, and never touches
        // MainActivity's own pagination behavior.
        return paginationEnabled ? items.size() + 1 : items.size();
    }

    @Override
    public long getItemId(int position) {
        if (position < items.size()) {
            String id = items.get(position).getSearchableId();
            return id != null ? id.hashCode() : position;
        }
        // NO_ID, not a fixed constant like Long.MIN_VALUE. With
        // setHasStableIds(true), RecyclerView uses getItemId() to decide
        // whether two notify calls refer to the same logical item - a
        // hardcoded constant meant every footer instance, across every
        // pagination append, shared one identity. Removing the old footer
        // and inserting a new one (addMoreItems()) was being collapsed
        // into "this one item moved" instead of "old one gone, new one
        // arrived," triggering a move animation instead of a clean
        // remove+insert - exactly what produced the footer lingering
        // visually behind newly-inserted cards. NO_ID tells RecyclerView
        // this position has no identity to track across notify calls,
        // which is the correct semantics for a transient loading
        // placeholder.
        return RecyclerView.NO_ID;
    }

    @Override
    public int getItemViewType(int position) {
        if (position >= items.size()) return ViewType.FOOTER;
        return registry.resolve(items.get(position)).getViewType();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemViewDelegate delegate = registry.findByViewType(viewType);
        View view = LayoutInflater.from(context)
                .inflate(delegate.getLayoutResId(), parent, false);
        RecyclerView.ViewHolder holder = delegate.createViewHolder(view);

        // Attach click listeners for non-footer items
        if (viewType != ViewType.FOOTER) {
            // Card tap toggles expansion ONLY when opted in - otherwise
            // this is the original, unchanged onItemClick-on-card-tap
            // behavior every current caller (MainActivity,
            // FavoritesActivity) relies on.
            view.setOnClickListener(v -> {
                int pos = holder.getAdapterPosition();
                if (pos == RecyclerView.NO_ID || pos >= items.size()) return;
                if (expansionEnabled) {
                    toggleExpansion(pos);
                } else if (clickListener != null) {
                    clickListener.onItemClick(items.get(pos));
                }
            });
            view.setOnLongClickListener(v -> {
                int pos = holder.getAdapterPosition();
                if (pos != RecyclerView.NO_ID && pos < items.size() && clickListener != null) {
                    clickListener.onItemLongClick(items.get(pos), pos);
                    return true;
                }
                return false;
            });

            // Dedicated navigate element - null until a card's layout adds
            // R.id.navigateChevron (all 8 search cards do as of this
            // change); findViewById returning null here would just mean
            // this card can't navigate via chevron, not a crash.
            View navigateChevron = view.findViewById(R.id.navigateChevron);
            if (navigateChevron != null) {
                navigateChevron.setOnClickListener(v -> {
                    int pos = holder.getAdapterPosition();
                    if (pos != RecyclerView.NO_ID && pos < items.size() && clickListener != null) {
                        clickListener.onItemClick(items.get(pos));
                    }
                });
            }
        }

        return holder;
    }

    /** Toggle a position's expand state and re-bind just that item. */
    private void toggleExpansion(int position) {
        if (expandedPositions.contains(position)) {
            expandedPositions.remove(position);
        } else {
            expandedPositions.add(position);
        }
        notifyItemChanged(position);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (position >= items.size()) {
            if (holder instanceof FooterDelegate.ViewHolder) {
                bindFooter((FooterDelegate.ViewHolder) holder);
            }
            return;
        }
        Searchable item = items.get(position);
        ItemViewDelegate delegate = registry.findByViewType(getItemViewType(position));
        delegate.bind(holder, item, currentLanguage);

        // Expandable nutrition-detail section - bound generically here
        // rather than through ItemViewDelegate, mirroring how
        // CardThumbnailHelper already handles thumbnails outside the
        // delegate contract. Gated on expansionEnabled: when off (the
        // current default for both real callers), the section, the
        // expand indicator, navigateChevron, AND mealQuantityBadge are
        // all explicitly hidden - they're visible by default in the
        // layout XML, so simply not binding them isn't enough to keep
        // them off screen.
        View expandableSection = holder.itemView.findViewById(R.id.expandableDetailSection);
        View expandIndicator = holder.itemView.findViewById(R.id.expandIndicator);
        View navigateChevron = holder.itemView.findViewById(R.id.navigateChevron);
        View quantityBadge = holder.itemView.findViewById(R.id.mealQuantityBadge);
        if (expandableSection != null) {
            if (expansionEnabled) {
                boolean isExpanded = expandedPositions.contains(position);
                Nutrition nutritionOverride = nutritionResolver != null
                        ? nutritionResolver.resolveNutrition(item) : null;
                NutritionGridHelper.bind(expandableSection, expandIndicator, isExpanded, item, nutritionOverride);
                if (navigateChevron != null) navigateChevron.setVisibility(View.VISIBLE);

                if (quantityBadge instanceof TextView) {
                    String label = quantityResolver != null
                            ? quantityResolver.resolveQuantityLabel(item) : null;
                    if (label != null) {
                        ((TextView) quantityBadge).setText(label);
                        quantityBadge.setVisibility(View.VISIBLE);
                    } else {
                        quantityBadge.setVisibility(View.GONE);
                    }
                }
            } else {
                expandableSection.setVisibility(View.GONE);
                if (expandIndicator != null) expandIndicator.setVisibility(View.GONE);
                if (navigateChevron != null) navigateChevron.setVisibility(View.GONE);
                if (quantityBadge != null) quantityBadge.setVisibility(View.GONE);
            }
        }
    }

    @Override
    public void onViewAttachedToWindow(@NonNull RecyclerView.ViewHolder holder) {
        super.onViewAttachedToWindow(holder);
        if (holder instanceof FooterDelegate.ViewHolder) holder.setIsRecyclable(false);
    }

    @Override
    public void onViewDetachedFromWindow(@NonNull RecyclerView.ViewHolder holder) {
        super.onViewDetachedFromWindow(holder);
        if (holder instanceof FooterDelegate.ViewHolder) holder.setIsRecyclable(true);
    }

    // ========== FOOTER ==========

    private void bindFooter(@NonNull FooterDelegate.ViewHolder holder) {
        boolean showFooter = paginationEnabled && hasMoreItems;
        holder.setLoadingState(showFooter, isLoadingMore, items.size());
    }

    // ========== INTERFACES ==========

    public interface OnItemClickListener {
        void onItemClick(@NonNull Searchable item);
        void onItemLongClick(@NonNull Searchable item, int position);
    }

    public interface OnLoadMoreListener {
        void onLoadMore();
    }

    // ========== STATUS ==========

    public boolean isLoadingMore() { return isLoadingMore; }
    public boolean hasMoreItems() { return hasMoreItems; }
    public int getItemCountWithoutFooter() { return items.size(); }
}