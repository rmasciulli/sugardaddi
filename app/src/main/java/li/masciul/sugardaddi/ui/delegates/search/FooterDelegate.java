package li.masciul.sugardaddi.ui.delegates.search;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.progressindicator.CircularProgressIndicator;

import li.masciul.sugardaddi.R;
import li.masciul.sugardaddi.core.interfaces.Searchable;
import li.masciul.sugardaddi.ui.delegates.ItemViewDelegate;
import li.masciul.sugardaddi.ui.delegates.ViewType;

/**
 * FooterDelegate - Pagination loading footer
 *
 * SPECIAL CASE:
 * This delegate never matches via canHandle() â€” it's not driven by item data.
 * Instead, the adapter explicitly uses ViewType.FOOTER for the extra item
 * appended after the real data items.
 *
 * The adapter controls this footer's state via setLoadingState() on the
 * ViewHolder, rather than through the standard bind() pathway.
 *
 * @version 1.0
 */
public class FooterDelegate implements ItemViewDelegate<FooterDelegate.ViewHolder> {

    private final Context context;

    public FooterDelegate(@NonNull Context context) {
        this.context = context;
    }

    // ========== ItemViewDelegate CONTRACT ==========

    @Override
    public int getViewType() {
        return ViewType.FOOTER;
    }

    @Override
    public int getLayoutResId() {
        return R.layout.item_loading_footer;
    }

    /**
     * Always returns false â€” the footer is not resolved via canHandle().
     * The adapter uses ViewType.FOOTER explicitly for the footer position.
     */
    @Override
    public boolean canHandle(@NonNull Searchable item) {
        return false;
    }

    @NonNull
    @Override
    public ViewHolder createViewHolder(@NonNull View view) {
        return new ViewHolder(view);
    }

    /**
     * Standard bind â€” not used for footer. State is managed by the adapter
     * calling ViewHolder.setLoadingState() directly.
     */
    @Override
    public void bind(@NonNull ViewHolder holder, @NonNull Searchable item, @NonNull String language) {
        // No-op: footer state is managed externally by the adapter
    }

    // ========== VIEW HOLDER ==========

    /**
     * Footer ViewHolder with loading state management.
     *
     * The adapter calls setLoadingState() to show/hide the loading spinner
     * based on the current pagination state.
     */
    public static class ViewHolder extends RecyclerView.ViewHolder {
        final LinearLayout footerContainer;
        final CircularProgressIndicator progressIndicator;
        final TextView loadingText;

        ViewHolder(View itemView) {
            super(itemView);
            footerContainer = itemView.findViewById(R.id.footerContainer);
            progressIndicator = itemView.findViewById(R.id.progressIndicator);
            loadingText = itemView.findViewById(R.id.loadingText);
        }

        /**
         * Update footer display based on pagination state.
         *
         * The footer is ONLY visible when a load is actively in progress.
         * It collapses to GONE both when idle (waiting for scroll) and when
         * there are no more items — preventing the ghost gap and stale text
         * that appeared between items when hasMoreItems was true but isLoading
         * was false.
         *
         * @param hasMoreItems  Whether more items are available to load
         * @param isLoading     Whether a load is currently in progress
         * @param itemCount     Number of actual data items (0 = hide footer entirely)
         */
        public void setLoadingState(boolean hasMoreItems, boolean isLoading, int itemCount) {
            // Show only when ALL three conditions are true:
            // - there are actual items in the list
            // - more items exist on the server
            // - a load request is actively in flight
            if (!hasMoreItems || !isLoading || itemCount == 0) {
                footerContainer.setVisibility(View.GONE);
                return;
            }

            // Actively loading — show spinner and label
            footerContainer.setVisibility(View.VISIBLE);
            progressIndicator.setVisibility(View.VISIBLE);
            loadingText.setVisibility(View.VISIBLE);
            loadingText.setText(R.string.loading_more_results);
        }
    }
}