package li.masciul.sugardaddi.ui.adapters;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import li.masciul.sugardaddi.core.interfaces.Searchable;
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

import java.util.ArrayList;
import java.util.List;

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

    @Nullable private OnItemClickListener clickListener;
    @Nullable private OnLoadMoreListener loadMoreListener;

    private boolean isLoadingMore = false;
    private boolean hasMoreItems = true;
    /** False in FavoritesActivity -- all items load at once, no footer needed. */
    private boolean paginationEnabled = true;

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

    /** Replace all items and reset pagination. */
    public void updateItems(@Nullable List<Searchable> newItems, boolean hasMore) {
        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, String.format("updateItems: %d -> %d, hasMore=%b",
                    items.size(), newItems != null ? newItems.size() : 0, hasMore));
        }
        isLoadingMore = false;
        hasMoreItems = hasMore;
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
        return items.isEmpty() ? 0 : items.size() + 1; // +1 for footer
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
            view.setOnClickListener(v -> {
                int pos = holder.getAdapterPosition();
                if (pos != RecyclerView.NO_ID && pos < items.size() && clickListener != null) {
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
        }

        return holder;
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