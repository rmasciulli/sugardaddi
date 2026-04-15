package li.masciul.sugardaddi.ui.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import li.masciul.sugardaddi.R;

/**
 * AutocompleteAdapter - Adapter for autocomplete suggestion dropdown
 *
 * PURPOSE:
 * - Displays autocomplete suggestions in a dropdown list
 * - Used with AutoCompleteTextView in MainActivity
 * - Shows lightweight, fast suggestions as user types
 * - Designed for SearchBehaviorManager's autocomplete feature
 *
 * DESIGN DECISIONS:
 * - Extends ArrayAdapter for simplicity and compatibility with AutoCompleteTextView
 * - Uses custom layout (item_autocomplete_suggestion.xml) for Material Design styling
 * - Implements Filterable but with custom filter that doesn't filter (we manage filtering externally)
 * - Lightweight: only stores and displays suggestion strings
 *
 * USAGE:
 * ```java
 * AutocompleteAdapter adapter = new AutocompleteAdapter(context);
 * autoCompleteTextView.setAdapter(adapter);
 *
 * // When new suggestions arrive from SearchBehaviorManager:
 * adapter.setSuggestions(suggestions);
 *
 * // To clear suggestions:
 * adapter.clear();
 * ```
 *
 * THREADING:
 * - All methods should be called on UI thread
 * - SearchBehaviorManager already handles debouncing and threading
 *
 * @version 1.0
 * @since SearchBehaviorManager migration
 */
public class AutocompleteAdapter extends ArrayAdapter<String> implements Filterable {

    private static final String TAG = "AutocompleteAdapter";

    // Suggestion data
    private List<String> suggestions;
    private List<String> allSuggestions; // Keep original list for filtering

    // UI components
    private final LayoutInflater inflater;

    /**
     * Constructor
     *
     * @param context Application context for inflating layouts
     */
    public AutocompleteAdapter(@NonNull Context context) {
        super(context, R.layout.item_autocomplete_suggestion);
        this.suggestions = new ArrayList<>();
        this.allSuggestions = new ArrayList<>();
        this.inflater = LayoutInflater.from(context);
    }

    /**
     * Update suggestions list with new data
     * Called when SearchBehaviorManager returns new autocomplete results
     *
     * @param suggestions New list of suggestion strings
     */
    public void setSuggestions(@NonNull List<String> suggestions) {
        java.util.LinkedHashSet<String> uniqueSuggestions = new java.util.LinkedHashSet<>(suggestions);

        this.suggestions.clear();
        this.suggestions.addAll(uniqueSuggestions);
        this.allSuggestions.clear();
        this.allSuggestions.addAll(uniqueSuggestions);
        notifyDataSetChanged();
    }

    /**
     * Clear all suggestions
     * Called when query is too short or autocomplete should be hidden
     */
    @Override
    public void clear() {
        this.suggestions.clear();
        this.allSuggestions.clear();
        notifyDataSetChanged();
    }

    /**
     * Get number of suggestions
     *
     * @return Count of current suggestions
     */
    @Override
    public int getCount() {
        return suggestions.size();
    }

    /**
     * Get suggestion at position
     *
     * @param position Index of suggestion
     * @return Suggestion string at position
     */
    @Override
    @Nullable
    public String getItem(int position) {
        if (position >= 0 && position < suggestions.size()) {
            return suggestions.get(position);
        }
        return null;
    }

    /**
     * Get item ID (using position as ID)
     *
     * @param position Index of item
     * @return Position as long
     */
    @Override
    public long getItemId(int position) {
        return position;
    }

    /**
     * Create/reuse view for suggestion item
     * Inflates custom layout with icon + text
     *
     * @param position Position of item in list
     * @param convertView Recycled view (may be null)
     * @param parent Parent view group
     * @return View for this suggestion
     */
    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        ViewHolder holder;

        // View recycling pattern
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_autocomplete_suggestion, parent, false);
            holder = new ViewHolder();
            holder.suggestionText = convertView.findViewById(R.id.suggestionText);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        // Bind data
        String suggestion = getItem(position);
        if (suggestion != null && holder.suggestionText != null) {
            holder.suggestionText.setText(suggestion);
        }

        return convertView;
    }

    /**
     * Custom filter that doesn't actually filter
     * We manage filtering externally in SearchBehaviorManager
     * This just returns all suggestions as-is
     *
     * @return NoOpFilter that shows all suggestions
     */
    @NonNull
    @Override
    public Filter getFilter() {
        return new NoOpFilter();
    }

    /**
     * ViewHolder pattern for efficient view recycling
     * Caches view references to avoid repeated findViewById() calls
     */
    private static class ViewHolder {
        TextView suggestionText;
    }

    /**
     * NoOpFilter - Filter that doesn't filter
     *
     * REASON:
     * - AutoCompleteTextView requires a Filter
     * - But we don't want it to filter suggestions (SearchBehaviorManager does that)
     * - This filter just returns all suggestions unchanged
     * - Prevents AutoCompleteTextView's default filtering behavior
     */
    private class NoOpFilter extends Filter {

        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            // Don't filter - return all suggestions
            FilterResults results = new FilterResults();
            results.values = allSuggestions;
            results.count = allSuggestions.size();
            return results;
        }

        @Override
        protected void publishResults(CharSequence constraint, FilterResults results) {
            // Accept all results without filtering
            if (results.values != null) {
                @SuppressWarnings("unchecked")
                List<String> resultList = (List<String>) results.values;
                suggestions.clear();
                suggestions.addAll(resultList);

                if (results.count > 0) {
                    notifyDataSetChanged();
                } else {
                    notifyDataSetInvalidated();
                }
            }
        }
    }
}