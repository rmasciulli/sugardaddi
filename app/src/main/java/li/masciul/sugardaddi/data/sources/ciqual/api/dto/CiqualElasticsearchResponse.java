package li.masciul.sugardaddi.data.sources.ciqual.api.dto;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;

/**
 * CiqualElasticsearchResponse - Elasticsearch response wrapper
 *
 * Represents the complete response structure from Ciqual's Elasticsearch endpoint:
 * POST https://ciqual.anses.fr/esearch/aliments/_search
 *
 * RESPONSE STRUCTURE:
 * {
 *   "took": 2,              // Query execution time (ms)
 *   "timed_out": false,     // Whether query timed out
 *   "_shards": {...},       // Shard information
 *   "hits": {
 *     "total": 9,           // Total matching results
 *     "max_score": 16.87,   // Highest relevance score
 *     "hits": [...]         // Array of results
 *   }
 * }
 *
 * USAGE:
 * ```java
 * Call<CiqualElasticsearchResponse> call = api.search(requestBody);
 * CiqualElasticsearchResponse response = call.execute().body();
 * List<CiqualElasticsearchHit> hits = response.getHits();
 * ```
 *
 * @see CiqualElasticsearchHit
 * @see CiqualElasticsearchFood
 * @author SugarDaddi Team
 * @version 1.0
 */
public class CiqualElasticsearchResponse {

    /**
     * Query execution time in milliseconds
     * Typically 1-10ms for most queries
     */
    @SerializedName("took")
    private Integer took;

    /**
     * Whether the query timed out
     * Rare, only happens for very complex queries
     */
    @SerializedName("timed_out")
    private Boolean timedOut;

    /**
     * Elasticsearch shard information
     * Contains total/successful/failed shard counts
     */
    @SerializedName("_shards")
    private Shards shards;

    /**
     * Container for search hits (results)
     * Contains total count, max score, and results array
     */
    @SerializedName("hits")
    private Hits hits;

    // ========== NESTED CLASSES ==========

    /**
     * Shard information for the query
     * Elasticsearch distributes data across multiple shards
     */
    public static class Shards {
        @SerializedName("total")
        private Integer total;

        @SerializedName("successful")
        private Integer successful;

        @SerializedName("failed")
        private Integer failed;

        public Integer getTotal() { return total; }
        public Integer getSuccessful() { return successful; }
        public Integer getFailed() { return failed; }
    }

    /**
     * Container for search results
     * Includes total count, max relevance score, and result array
     */
    public static class Hits {
        /**
         * Total number of matching documents
         * May be more than hits.size() due to pagination
         */
        @SerializedName("total")
        private Integer total;

        /**
         * Highest relevance score in results
         * Used for relevance analysis
         */
        @SerializedName("max_score")
        private Double maxScore;

        /**
         * Array of search results (hits)
         * Each hit contains document metadata and _source data
         */
        @SerializedName("hits")
        private List<CiqualElasticsearchHit> hits;

        public Integer getTotal() { return total; }
        public Double getMaxScore() { return maxScore; }
        public List<CiqualElasticsearchHit> getHits() {
            return hits != null ? hits : new ArrayList<>();
        }
    }

    // ========== GETTERS ==========

    public Integer getTook() {
        return took;
    }

    public Boolean getTimedOut() {
        return timedOut != null ? timedOut : false;
    }

    public Shards getShards() {
        return shards;
    }

    public Hits getHitsContainer() {
        return hits;
    }

    // ========== CONVENIENCE METHODS ==========

    /**
     * Checks if the response contains any results
     *
     * @return true if results exist
     */
    public boolean hasResults() {
        return hits != null
                && hits.hits != null
                && !hits.hits.isEmpty();
    }

    /**
     * Gets the total number of matching documents
     * May be more than the number of returned hits
     *
     * @return Total match count, or 0 if no results
     */
    public int getTotal() {
        return hits != null && hits.total != null ? hits.total : 0;
    }

    /**
     * Gets the list of search result hits
     * Returns empty list if no results
     *
     * @return List of hits (never null)
     */
    public List<CiqualElasticsearchHit> getHits() {
        return hits != null && hits.hits != null
                ? hits.hits
                : new ArrayList<>();
    }

    /**
     * Gets the maximum relevance score in results
     *
     * @return Max score, or 0.0 if no results
     */
    public double getMaxScore() {
        return hits != null && hits.maxScore != null
                ? hits.maxScore
                : 0.0;
    }

    /**
     * Gets the number of returned hits
     * Different from getTotal() which returns total matches
     *
     * @return Number of hits in this response
     */
    public int getHitCount() {
        return getHits().size();
    }

    /**
     * Checks if there are more results available
     * True if total results exceed returned hits
     *
     * @return true if pagination is needed for all results
     */
    public boolean hasMoreResults() {
        return getTotal() > getHitCount();
    }

    /**
     * Checks if the query was successful
     * Successful = has results OR explicitly returned zero results (not an error)
     *
     * @return true if query executed successfully
     */
    public boolean isSuccessful() {
        return !getTimedOut()
                && shards != null
                && shards.failed != null
                && shards.failed == 0;
    }

    // ========== DEBUG ==========

    @Override
    public String toString() {
        return String.format(
                "CiqualElasticsearchResponse[took=%dms, total=%d, hits=%d, maxScore=%.2f, timedOut=%s]",
                took != null ? took : 0,
                getTotal(),
                getHitCount(),
                getMaxScore(),
                getTimedOut()
        );
    }
}