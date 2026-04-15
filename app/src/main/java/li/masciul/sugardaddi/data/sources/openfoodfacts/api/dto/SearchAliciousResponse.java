package li.masciul.sugardaddi.data.sources.openfoodfacts.api.dto;

import androidx.annotation.Nullable;
import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;

/**
 * SearchAliciousResponse - Main response wrapper from search-a-licious API
 *
 * RESPONSE STRUCTURE:
 * The search-a-licious API returns a comprehensive response with:
 * - hits: Array of product documents (SearchAliciousHit)
 * - Pagination metadata (page, page_size, page_count)
 * - Total count and accuracy indicator (count, is_count_exact)
 * - Performance metrics (took, timed_out)
 * - Optional warnings for query issues
 *
 * PAGINATION:
 * - page: Current page number (1-based)
 * - page_size: Number of results per page
 * - page_count: Total number of pages available
 * - count: Total matching products
 *
 * PERFORMANCE:
 * - took: Query execution time in milliseconds
 * - timed_out: Whether query exceeded time limit
 * - If timed_out=true, results may be incomplete
 *
 * COUNT ACCURACY:
 * - is_count_exact: true = exact count, false = estimated
 * - Elasticsearch may estimate counts for very large result sets
 * - Use this to determine if "X of Y" display should show "~" prefix
 *
 * WARNINGS:
 * - Optional array of non-critical issues
 * - Should be logged for debugging
 * - Doesn't prevent successful response
 *
 * EXAMPLE RESPONSE:
 * ```json
 * {
 *   "hits": [
 *     {"code": "123", "product_name": "Chocolate", ...},
 *     {"code": "456", "product_name": "Milk", ...}
 *   ],
 *   "count": 1234,
 *   "is_count_exact": true,
 *   "page": 1,
 *   "page_size": 20,
 *   "page_count": 62,
 *   "took": 45,
 *   "timed_out": false,
 *   "warnings": []
 * }
 * ```
 *
 * @author SugarDaddi Team
 * @version 2.0 (Search-a-licious Integration)
 */
public class SearchAliciousResponse {

    // ========== RESULTS ==========

    /**
     * Array of product hits (search results)
     * Each hit is a product document from Elasticsearch
     */
    @SerializedName("hits")
    private List<SearchAliciousHit> hits;

    // ========== COUNT METADATA ==========

    /**
     * Total number of matching products
     * May be exact or estimated (see is_count_exact)
     */
    @SerializedName("count")
    private int count;

    /**
     * Whether the count is exact or estimated
     * - true: Exact count (reliable for display)
     * - false: Estimated count (show "~1234 results")
     */
    @SerializedName("is_count_exact")
    private boolean isCountExact;

    // ========== PAGINATION ==========

    /**
     * Current page number (1-based)
     */
    @SerializedName("page")
    private int page;

    /**
     * Number of results per page
     */
    @SerializedName("page_size")
    private int pageSize;

    /**
     * Total number of pages available
     * Calculated as: ceil(count / page_size)
     */
    @SerializedName("page_count")
    private int pageCount;

    // ========== PERFORMANCE METRICS ==========

    /**
     * Query execution time in milliseconds
     * Useful for performance monitoring
     */
    @SerializedName("took")
    private int took;

    /**
     * Whether the query timed out
     * - false: Normal execution, complete results
     * - true: Query exceeded time limit, results may be incomplete
     */
    @SerializedName("timed_out")
    private boolean timedOut;

    // ========== WARNINGS ==========

    /**
     * Optional array of warning messages
     * Non-critical issues that don't prevent successful response
     * Examples: query syntax warnings, performance warnings
     */
    @SerializedName("warnings")
    @Nullable
    private List<SearchAliciousWarning> warnings;

    // ========== OPTIONAL METADATA ==========

    /**
     * Debug information (optional)
     * Contains Elasticsearch query details for debugging
     * Only included when debug mode is enabled
     */
    @SerializedName("debug")
    @Nullable
    private Object debug;  // Can be parsed if needed for debugging

    // ========== CONSTRUCTORS ==========

    /**
     * Default constructor required by Gson
     */
    public SearchAliciousResponse() {
        this.hits = new ArrayList<>();
        this.warnings = new ArrayList<>();
    }

    // ========== GETTERS ==========

    public List<SearchAliciousHit> getHits() {
        return hits != null ? hits : new ArrayList<>();
    }

    public int getCount() {
        return count;
    }

    public boolean isCountExact() {
        return isCountExact;
    }

    public int getPage() {
        return page;
    }

    public int getPageSize() {
        return pageSize;
    }

    public int getPageCount() {
        return pageCount;
    }

    public int getTook() {
        return took;
    }

    public boolean isTimedOut() {
        return timedOut;
    }

    @Nullable
    public List<SearchAliciousWarning> getWarnings() {
        return warnings;
    }

    @Nullable
    public Object getDebug() {
        return debug;
    }

    // ========== SETTERS ==========

    public void setHits(List<SearchAliciousHit> hits) {
        this.hits = hits;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public void setCountExact(boolean countExact) {
        isCountExact = countExact;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public void setPageCount(int pageCount) {
        this.pageCount = pageCount;
    }

    public void setTook(int took) {
        this.took = took;
    }

    public void setTimedOut(boolean timedOut) {
        this.timedOut = timedOut;
    }

    public void setWarnings(List<SearchAliciousWarning> warnings) {
        this.warnings = warnings;
    }

    public void setDebug(Object debug) {
        this.debug = debug;
    }

    // ========== HELPER METHODS ==========

    /**
     * Check if response contains results
     *
     * @return true if hits array is not empty
     */
    public boolean hasResults() {
        return hits != null && !hits.isEmpty();
    }

    /**
     * Check if response is empty (no results found)
     *
     * @return true if no hits returned
     */
    public boolean isEmpty() {
        return !hasResults();
    }

    /**
     * Get number of results in current page
     *
     * @return Number of hits in this response
     */
    public int getResultCount() {
        return hits != null ? hits.size() : 0;
    }

    /**
     * Check if there are more pages available
     *
     * @return true if current page is not the last page
     */
    public boolean hasMorePages() {
        return page < pageCount;
    }

    /**
     * Get next page number
     *
     * @return Next page number, or current page if no more pages
     */
    public int getNextPage() {
        return hasMorePages() ? page + 1 : page;
    }

    /**
     * Check if there are warnings
     *
     * @return true if warnings array is not empty
     */
    public boolean hasWarnings() {
        return warnings != null && !warnings.isEmpty();
    }

    /**
     * Get warning count
     *
     * @return Number of warnings in response
     */
    public int getWarningCount() {
        return warnings != null ? warnings.size() : 0;
    }

    /**
     * Get all warning messages as a single string
     * Useful for logging
     *
     * @return Concatenated warning messages, or empty string if no warnings
     */
    public String getWarningMessages() {
        if (!hasWarnings()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < warnings.size(); i++) {
            if (i > 0) sb.append("; ");
            sb.append(warnings.get(i).getFullMessage());
        }
        return sb.toString();
    }

    /**
     * Check if response indicates a successful search
     *
     * A successful search means:
     * - Query completed (not timed out)
     * - Has results OR count is valid (empty results are still "successful")
     *
     * @return true if search executed successfully
     */
    public boolean isSuccessful() {
        return !timedOut;
    }

    /**
     * Get performance summary string
     * Useful for logging and debugging
     *
     * @return Human-readable performance summary
     */
    public String getPerformanceSummary() {
        return String.format("Query took %dms, returned %d/%d results (page %d/%d)%s",
                took,
                getResultCount(),
                count,
                page,
                pageCount,
                timedOut ? " [TIMED OUT]" : "");
    }

    /**
     * Get display count string
     * Shows exact or approximate count based on is_count_exact
     *
     * @return Formatted count string (e.g., "1,234" or "~1,234")
     */
    public String getDisplayCount() {
        String countStr = String.format("%,d", count);
        return isCountExact ? countStr : "~" + countStr;
    }

    /**
     * Calculate percentage of results shown
     * Useful for progress indicators
     *
     * @return Percentage of total results shown so far (0.0-100.0)
     */
    public double getProgressPercentage() {
        if (count == 0) return 100.0;
        int shownResults = page * pageSize;
        return Math.min(100.0, (shownResults * 100.0) / count);
    }

    /**
     * Filter hits by quality threshold
     * Returns only hits with completeness >= threshold
     *
     * @param minCompleteness Minimum completeness score (0.0-1.0)
     * @return Filtered list of high-quality hits
     */
    public List<SearchAliciousHit> getQualityFilteredHits(double minCompleteness) {
        List<SearchAliciousHit> filtered = new ArrayList<>();
        for (SearchAliciousHit hit : getHits()) {
            Double completeness = hit.getCompleteness();
            if (completeness != null && completeness >= minCompleteness) {
                filtered.add(hit);
            }
        }
        return filtered;
    }

    /**
     * Get only pertinent hits (using isPertinent() check)
     *
     * @return List of hits that meet minimum quality standards
     */
    public List<SearchAliciousHit> getPertinentHits() {
        List<SearchAliciousHit> pertinent = new ArrayList<>();
        for (SearchAliciousHit hit : getHits()) {
            if (hit.isPertinent()) {
                pertinent.add(hit);
            }
        }
        return pertinent;
    }

    // ========== OBJECT METHODS ==========

    @Override
    public String toString() {
        return String.format("SearchAliciousResponse{hits=%d, count=%d%s, page=%d/%d, took=%dms%s%s}",
                getResultCount(),
                count,
                isCountExact ? "" : "(~)",
                page,
                pageCount,
                took,
                timedOut ? ", TIMED_OUT" : "",
                hasWarnings() ? ", warnings=" + getWarningCount() : "");
    }
}