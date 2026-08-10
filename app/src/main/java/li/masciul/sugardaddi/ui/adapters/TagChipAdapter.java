package li.masciul.sugardaddi.ui.adapters;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;

import li.masciul.sugardaddi.R;

import java.util.ArrayList;
import java.util.List;

/**
 * TagChipAdapter - backs the horizontal, natively-scrollable tag row shared
 * by every tag-based recipe search card (MealDB, CocktailDB, FatSecret).
 * Reuses chip_tag_compact.xml directly as the item root - a Chip is already
 * a valid standalone View, no separate item layout needed.
 *
 * Replaces an earlier ChipGroup + HorizontalScrollView attempt: ChipGroup is
 * built for wrapping, not scrolling, and pairing it with a generic pixel-
 * scrolling container meant hand-rolling truncation/ellipsis ourselves. A
 * horizontal RecyclerView is Android's own tool for a scrollable row of
 * discrete items - real native drag/fling, and a partially-visible next
 * chip at the edge as the natural "there's more" signal, nothing custom
 * to build or maintain.
 */
public class TagChipAdapter extends RecyclerView.Adapter<TagChipAdapter.ViewHolder> {

    /** Small gap between chips - chip_tag_compact.xml itself has no margin,
     *  since it's shared with contexts (a plain ChipGroup) where a
     *  RecyclerView-specific margin wouldn't make sense. */
    private static final int CHIP_SPACING_DP = 4;

    private final List<String> tags = new ArrayList<>();

    /** Replaces the current tag list and refreshes the row. Tags should already be capitalized. */
    public void submitTags(@NonNull List<String> newTags) {
        tags.clear();
        tags.addAll(newTags);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Chip chip = (Chip) LayoutInflater.from(parent.getContext())
                .inflate(R.layout.chip_tag_compact, parent, false);

        float density = parent.getResources().getDisplayMetrics().density;
        RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.WRAP_CONTENT, RecyclerView.LayoutParams.WRAP_CONTENT);
        params.setMarginEnd(Math.round(CHIP_SPACING_DP * density));
        chip.setLayoutParams(params);

        return new ViewHolder(chip);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.chip.setText(tags.get(position));
    }

    @Override
    public int getItemCount() {
        return tags.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final Chip chip;

        ViewHolder(@NonNull Chip chip) {
            super(chip);
            this.chip = chip;
            chip.setEnsureMinTouchTargetSize(false);
            chip.setClickable(false);
            chip.setCheckable(false);
            chip.setFocusable(false);
        }
    }
}