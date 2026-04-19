package li.masciul.sugardaddi.data.sources.openfoodfacts.api.dto;

import androidx.annotation.Nullable;
import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;

/**
 * AutocompleteResponse - Response from SearchAlicious /autocomplete taxonomy endpoint
 *
 * WHAT THIS IS:
 * The SearchAlicious autocomplete endpoint returns taxonomy suggestions
 * (categories, brands, ingredients), NOT products. The response structure
 * is completely different from SearchAliciousResponse.
 *
 * ENDPOINT: GET /autocomplete?q=...&taxonomy_names=category,brand&lang=en&size=5
 *
 * RESPONSE STRUCTURE:
 * {
 *   "options": [
 *     {"id": "en:chocolates",   "text": "Chocolates",   "taxonomy_name": "category"},
 *     {"id": "en:chocolate-bars", "text": "Chocolate bars", "taxonomy_name": "category"},
 *     {"id": "milka",           "text": "Milka",         "taxonomy_name": "brand"}
 *   ]
 * }
 *
 * CURRENT STATUS:
 * The Retrofit interface declares the endpoint; the DTO is compiled and ready.
 * OpenFoodFactsDataSource does NOT call this endpoint yet — its autocomplete()
 * method uses the /search endpoint with AUTOCOMPLETE_FIELDS for product-name
 * suggestions, which is consistent with CiqualDataSource's pattern.
 *
 * FUTURE USE:
 * This DTO enables a second autocomplete mode: taxonomy suggestions
 * ("Chocolates (category)", "Milka (brand)") shown above product suggestions.
 * When implemented, OpenFoodFactsDataSource.autocomplete() will call
 * searchApi.autocomplete() alongside searchApi.search() and merge both.
 */
public class AutocompleteResponse {

    /** Array of taxonomy suggestions returned by the endpoint */
    @SerializedName("options")
    private List<AutocompleteSuggestion> options;

    // ========== CONSTRUCTORS ==========

    public AutocompleteResponse() {
        this.options = new ArrayList<>();
    }

    public AutocompleteResponse(List<AutocompleteSuggestion> options) {
        this.options = options != null ? options : new ArrayList<>();
    }

    // ========== ACCESSORS ==========

    public List<AutocompleteSuggestion> getOptions() {
        return options != null ? options : new ArrayList<>();
    }

    public void setOptions(List<AutocompleteSuggestion> options) {
        this.options = options;
    }

    public boolean hasSuggestions() {
        return options != null && !options.isEmpty();
    }

    public int getSuggestionCount() {
        return options != null ? options.size() : 0;
    }

    /**
     * Extract plain display texts from all suggestions.
     * Useful for populating a simple string dropdown.
     */
    public List<String> getSuggestionTexts() {
        List<String> texts = new ArrayList<>();
        for (AutocompleteSuggestion s : getOptions()) {
            if (s.getText() != null) texts.add(s.getText());
        }
        return texts;
    }

    @Override
    public String toString() {
        return "AutocompleteResponse{suggestions=" + getSuggestionCount() + "}";
    }

    // ========== NESTED CLASS ==========

    /**
     * AutocompleteSuggestion - One taxonomy term returned by the autocomplete endpoint.
     *
     * Each suggestion comes from a single taxonomy (category, brand, ingredient).
     * The "text" field is the human-readable display string; "id" is the
     * taxonomy key (e.g., "en:chocolates") used for filtering.
     */
    public static class AutocompleteSuggestion {

        /** Taxonomy key, e.g. "en:chocolates" or "milka" */
        @SerializedName("id")
        private String id;

        /** Human-readable display text, e.g. "Chocolates" or "Milka" */
        @SerializedName("text")
        private String text;

        /** Which taxonomy this came from: "category", "brand", "ingredient" */
        @SerializedName("taxonomy_name")
        @Nullable
        private String taxonomyName;

        // ========== CONSTRUCTORS ==========

        public AutocompleteSuggestion() {}

        public AutocompleteSuggestion(String id, String text, @Nullable String taxonomyName) {
            this.id = id;
            this.text = text;
            this.taxonomyName = taxonomyName;
        }

        // ========== ACCESSORS ==========

        public String getId()           { return id; }
        public String getText()         { return text; }
        @Nullable
        public String getTaxonomyName() { return taxonomyName; }

        public void setId(String id)                     { this.id = id; }
        public void setText(String text)                 { this.text = text; }
        public void setTaxonomyName(String taxonomyName) { this.taxonomyName = taxonomyName; }

        // ========== CONVENIENCE ==========

        public boolean isCategory()   { return "category".equals(taxonomyName); }
        public boolean isBrand()      { return "brand".equals(taxonomyName); }
        public boolean isIngredient() { return "ingredient".equals(taxonomyName); }

        @Override
        public String toString() {
            return "AutocompleteSuggestion{id='" + id + "', text='" + text
                    + "', taxonomy='" + taxonomyName + "'}";
        }
    }
}