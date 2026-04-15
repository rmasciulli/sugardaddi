package li.masciul.sugardaddi.data.sources.openfoodfacts.api.dto;

import androidx.annotation.Nullable;
import com.google.gson.annotations.SerializedName;

/**
 * SearchAliciousWarning - Warning message from search-a-licious API
 *
 * WHAT ARE WARNINGS?
 * Warnings are non-critical issues reported by the search API:
 * - Query syntax issues (e.g., unrecognized operators)
 * - Performance warnings (e.g., query too complex)
 * - Deprecated parameter usage
 * - Timeout warnings (partial results returned)
 *
 * HANDLING STRATEGY:
 * - Log all warnings for debugging
 * - Display user-friendly message if title indicates user error
 * - Continue with results (warnings don't block response)
 * - Monitor warning frequency in production
 *
 * EXAMPLE WARNINGS:
 * ```json
 * {
 *   "title": "Query syntax warning",
 *   "description": "Unrecognized field 'product_nmae' (did you mean 'product_name'?)"
 * }
 * ```
 *
 * @author SugarDaddi Team
 * @version 2.0 (Search-a-licious Integration)
 */
public class SearchAliciousWarning {

    /**
     * Warning title (always present)
     * Short description of the warning type
     */
    @SerializedName("title")
    private String title;

    /**
     * Detailed warning description (optional)
     * Provides more context about the warning
     */
    @SerializedName("description")
    @Nullable
    private String description;

    // ========== CONSTRUCTORS ==========

    /**
     * Default constructor required by Gson
     */
    public SearchAliciousWarning() {
    }

    /**
     * Constructor for manual warning creation
     *
     * @param title Warning title
     * @param description Warning description (can be null)
     */
    public SearchAliciousWarning(String title, @Nullable String description) {
        this.title = title;
        this.description = description;
    }

    // ========== GETTERS ==========

    public String getTitle() {
        return title;
    }

    @Nullable
    public String getDescription() {
        return description;
    }

    // ========== SETTERS ==========

    public void setTitle(String title) {
        this.title = title;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    // ========== HELPER METHODS ==========

    /**
     * Get complete warning message
     * Combines title and description if both present
     *
     * @return Full warning message for logging
     */
    public String getFullMessage() {
        if (description != null && !description.trim().isEmpty()) {
            return title + ": " + description;
        }
        return title;
    }

    /**
     * Check if warning has detailed description
     *
     * @return true if description is present and non-empty
     */
    public boolean hasDescription() {
        return description != null && !description.trim().isEmpty();
    }

    // ========== OBJECT METHODS ==========

    @Override
    public String toString() {
        return "SearchAliciousWarning{" +
                "title='" + title + '\'' +
                (hasDescription() ? ", description='" + description + '\'' : "") +
                '}';
    }
}