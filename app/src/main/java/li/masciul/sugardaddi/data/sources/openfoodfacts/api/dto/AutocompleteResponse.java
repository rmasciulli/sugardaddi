package li.masciul.sugardaddi.data.sources.openfoodfacts.api.dto;

import androidx.annotation.Nullable;
import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;

/**
 * AutocompleteResponse - Response from autocomplete endpoint
 *
 * WHAT IS AUTOCOMPLETE?
 * The autocomplete endpoint provides fast typeahead suggestions from
 * OpenFoodFacts taxonomies (categories, brands, ingredients).
 *
 * RESPONSE STRUCTURE:
 * The API returns an array of suggestion objects, each containing:
 * - id: Unique identifier for the term
 * - text: Display text for the suggestion
 * - taxonomy_name: Which taxonomy this belongs to
 * - Additional metadata (optional)
 *
 * TYPICAL USE CASES:
 * 1. Search box typeahead
 * 2. Category filtering dropdown
 * 3. Brand selection autocomplete
 * 4. Ingredient search suggestions
 *
 * EXAMPLE REQUEST:
 * GET /autocomplete?q=choc&taxonomy_names=category,brand&lang=en&size=5
 *
 * EXAMPLE RESPONSE:
 * ```json
 * {
 *   "options": [
 *     {"id": "en:chocolates", "text": "Chocolates", "taxonomy_name": "category"},
 *     {"id": "en:chocolate-bars", "text": "Chocolate bars", "taxonomy_name": "category"},
 *     {"id": "milka", "text": "Milka", "taxonomy_name": "brand"}
 *   ]
 * }
 * ```
 *
 * @author SugarDaddi Team
 * @version 2.0 (Search-a-licious Integration)
 */
public class AutocompleteResponse {

    /**
     * Array of autocomplete suggestions
     * Each suggestion is a matched term from the requested taxonomies
     */
    @SerializedName("options")
    private List<AutocompleteSuggestion> options;

    // ========== CONSTRUCTORS ==========

    /**
     * Default constructor required by Gson
     */
    public AutocompleteResponse() {
        this.options = new ArrayList<>();
    }

    /**
     * Constructor with suggestions
     *
     * @param options List of autocomplete suggestions
     */
    public AutocompleteResponse(List<AutocompleteSuggestion> options) {
        this.options = options != null ? options : new ArrayList<>();
    }

    // ========== GETTERS ==========

    public List<AutocompleteSuggestion> getOptions() {
        return options != null ? options : new ArrayList<>();
    }

    // ========== SETTERS ==========

    public void setOptions(List<AutocompleteSuggestion> options) {
        this.options = options;
    }

    // ========== HELPER METHODS ==========

    /**
     * Check if response contains suggestions
     *
     * @return true if options array is not empty
     */
    public boolean hasSuggestions() {
        return options != null && !options.isEmpty();
    }

    /**
     * Check if response is empty (no suggestions)
     *
     * @return true if no suggestions returned
     */
    public boolean isEmpty() {
        return !hasSuggestions();
    }

    /**
     * Get number of suggestions
     *
     * @return Count of suggestions in response
     */
    public int getSuggestionCount() {
        return options != null ? options.size() : 0;
    }

    /**
     * Get suggestions for a specific taxonomy
     *
     * @param taxonomyName Taxonomy name (e.g., "category", "brand")
     * @return Filtered list of suggestions for that taxonomy
     */
    public List<AutocompleteSuggestion> getSuggestionsForTaxonomy(String taxonomyName) {
        List<AutocompleteSuggestion> filtered = new ArrayList<>();
        for (AutocompleteSuggestion suggestion : getOptions()) {
            if (taxonomyName.equals(suggestion.getTaxonomyName())) {
                filtered.add(suggestion);
            }
        }
        return filtered;
    }

    /**
     * Get all suggestion texts as a simple string list
     * Useful for simple autocomplete UI components
     *
     * @return List of suggestion texts
     */
    public List<String> getSuggestionTexts() {
        List<String> texts = new ArrayList<>();
        for (AutocompleteSuggestion suggestion : getOptions()) {
            texts.add(suggestion.getText());
        }
        return texts;
    }

    // ========== OBJECT METHODS ==========

    @Override
    public String toString() {
        return String.format("AutocompleteResponse{suggestions=%d}", getSuggestionCount());
    }

    // ========== NESTED CLASS: AutocompleteSuggestion ==========

    /**
     * AutocompleteSuggestion - Individual suggestion from autocomplete
     *
     * Represents a single matched term from the taxonomy search.
     */
    public static class AutocompleteSuggestion {

        /**
         * Unique identifier for the term
         * Format: "taxonomy_prefix:term" (e.g., "en:chocolates")
         */
        @SerializedName("id")
        private String id;

        /**
         * Display text for the suggestion
         * Human-readable, localized term name
         */
        @SerializedName("text")
        private String text;

        /**
         * Taxonomy this suggestion belongs to
         * Values: "category", "brand", "ingredient", etc.
         */
        @SerializedName("taxonomy_name")
        @Nullable
        private String taxonomyName;

        // ========== CONSTRUCTORS ==========

        /**
         * Default constructor required by Gson
         */
        public AutocompleteSuggestion() {
        }

        /**
         * Constructor with all fields
         *
         * @param id Unique identifier
         * @param text Display text
         * @param taxonomyName Taxonomy name
         */
        public AutocompleteSuggestion(String id, String text, @Nullable String taxonomyName) {
            this.id = id;
            this.text = text;
            this.taxonomyName = taxonomyName;
        }

        // ========== GETTERS ==========

        public String getId() {
            return id;
        }

        public String getText() {
            return text;
        }

        @Nullable
        public String getTaxonomyName() {
            return taxonomyName;
        }

        // ========== SETTERS ==========

        public void setId(String id) {
            this.id = id;
        }

        public void setText(String text) {
            this.text = text;
        }

        public void setTaxonomyName(String taxonomyName) {
            this.taxonomyName = taxonomyName;
        }

        // ========== HELPER METHODS ==========

        /**
         * Check if suggestion is from category taxonomy
         *
         * @return true if taxonomy_name is "category"
         */
        public boolean isCategory() {
            return "category".equals(taxonomyName);
        }

        /**
         * Check if suggestion is from brand taxonomy
         *
         * @return true if taxonomy_name is "brand"
         */
        public boolean isBrand() {
            return "brand".equals(taxonomyName);
        }

        /**
         * Check if suggestion is from ingredient taxonomy
         *
         * @return true if taxonomy_name is "ingredient"
         */
        public boolean isIngredient() {
            return "ingredient".equals(taxonomyName);
        }

        // ========== OBJECT METHODS ==========

        @Override
        public String toString() {
            return String.format("AutocompleteSuggestion{id='%s', text='%s', taxonomy='%s'}",
                    id, text, taxonomyName);
        }
    }
}