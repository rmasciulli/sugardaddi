package li.masciul.sugardaddi.data.sources.ciqual.api.dto;

import com.google.gson.annotations.SerializedName;

/**
 * CiqualElasticsearchHit - Elasticsearch result wrapper with metadata
 *
 * IMPORTANT: This is a WRAPPER class, not the actual food data!
 * ================================================================
 *
 * Elasticsearch returns search results wrapped in metadata containers.
 * This class represents ONE result item with:
 * - Elasticsearch metadata (_index, _type, _id, _score)
 * - The actual food data (in _source field)
 *
 * ARCHITECTURE:
 * =============
 * CiqualElasticsearchResponse (API response)
 *   └─ hits (container with total count, max score)
 *       └─ hits[] (array of result wrappers)
 *           └─ CiqualElasticsearchHit ← YOU ARE HERE
 *               ├─ _index: "ciqual" (Elasticsearch index name)
 *               ├─ _type: "aliments" (document type)
 *               ├─ _id: "AXeP_5rwWzZQdECwP-JN" (internal ES ID)
 *               ├─ _score: 16.874912 (relevance score for ranking)
 *               └─ _source: CiqualElasticsearchFood ← THE ACTUAL FOOD DATA
 *                   ├─ code: "13014" (Ciqual code)
 *                   ├─ nomFr: "Fraise,crue" (French name)
 *                   ├─ compos: [...] (nutrition data)
 *                   └─ ... (all food information)
 *
 * WHY THE WRAPPER?
 * ================
 * Elasticsearch uses this structure to provide:
 * 1. RELEVANCE SCORING: _score tells us how well this result matches the query
 * 2. RESULT RANKING: Higher scores = better matches (used for sorting)
 * 3. METADATA: Internal IDs for Elasticsearch operations
 * 4. SEPARATION: Clean separation between search metadata and actual data
 *
 * TYPICAL USAGE:
 * ==============
 * ```java
 * // Get search results
 * CiqualElasticsearchResponse response = api.search(...);
 *
 * // Iterate through result wrappers
 * for (CiqualElasticsearchHit hit : response.getHits()) {
 *     // Extract relevance score (from wrapper)
 *     double relevance = hit.getScore();
 *
 *     // Extract actual food data (from _source)
 *     CiqualElasticsearchFood food = hit.getSource();
 *
 *     // Now you can use the food data
 *     String name = food.getNomFr();
 *     String code = food.getCode();
 *
 *     // Example: Filter by relevance
 *     if (relevance > 10.0) {
 *         // High-quality match
 *     }
 * }
 * ```
 *
 * COMPARISON WITH OpenFoodFacts:
 * ==============================
 * OpenFoodFacts API returns products directly in an array.
 * Ciqual uses Elasticsearch, so results come wrapped with metadata.
 * This is standard Elasticsearch behavior, not specific to Ciqual.
 *
 * @see CiqualElasticsearchResponse The full API response
 * @see CiqualElasticsearchFood The actual food data (inside _source)
 * @author SugarDaddi Team
 * @version 1.0
 */
public class CiqualElasticsearchHit {

    /**
     * Elasticsearch index name
     * Always "ciqual" for Ciqual searches
     */
    @SerializedName("_index")
    private String index;

    /**
     * Elasticsearch document type
     * Always "aliments" (foods) for Ciqual
     */
    @SerializedName("_type")
    private String type;

    /**
     * Elasticsearch document ID
     * Internal unique identifier (not the Ciqual code)
     */
    @SerializedName("_id")
    private String id;

    /**
     * Relevance score for this result
     * Higher scores indicate better matches to the query
     * Typically ranges from 1.0 to 20.0+
     */
    @SerializedName("_score")
    private Double score;

    /**
     * The actual food data
     * Contains all Ciqual food information
     */
    @SerializedName("_source")
    private CiqualElasticsearchFood source;

    // ========== GETTERS ==========

    /**
     * Gets the Elasticsearch index name
     *
     * @return Index name ("ciqual")
     */
    public String getIndex() {
        return index;
    }

    /**
     * Gets the Elasticsearch document type
     *
     * @return Document type ("aliments")
     */
    public String getType() {
        return type;
    }

    /**
     * Gets the Elasticsearch document ID
     * Note: This is NOT the Ciqual food code
     *
     * @return Internal document ID
     */
    public String getId() {
        return id;
    }

    /**
     * Gets the relevance score for this result
     * Higher scores indicate better matches
     *
     * @return Relevance score (0.0 if not available)
     */
    public double getScore() {
        return score != null ? score : 0.0;
    }

    /**
     * Gets the food data from this hit
     *
     * @return CiqualElasticsearchFood object with all food information
     */
    public CiqualElasticsearchFood getSource() {
        return source;
    }

    // ========== CONVENIENCE METHODS ==========

    /**
     * Checks if this hit has valid food data
     *
     * @return true if source data exists
     */
    public boolean hasSource() {
        return source != null;
    }

    /**
     * Gets the Ciqual food code from the source
     * Convenience method to avoid null checks
     *
     * @return Ciqual code, or null if not available
     */
    public String getCiqualCode() {
        return source != null ? source.getCode() : null;
    }

    /**
     * Gets the French name from the source
     * Convenience method to avoid null checks
     *
     * @return French name, or null if not available
     */
    public String getFrenchName() {
        return source != null ? source.getNomFr() : null;
    }

    /**
     * Checks if this is a high-relevance result
     * Useful for filtering or highlighting top results
     *
     * @return true if score is above 10.0 (arbitrary threshold)
     */
    public boolean isHighRelevance() {
        return getScore() > 10.0;
    }

    // ========== DEBUG ==========

    @Override
    public String toString() {
        return String.format(
                "CiqualElasticsearchHit[id=%s, score=%.2f, code=%s, name=%s]",
                id,
                getScore(),
                getCiqualCode(),
                getFrenchName()
        );
    }
}