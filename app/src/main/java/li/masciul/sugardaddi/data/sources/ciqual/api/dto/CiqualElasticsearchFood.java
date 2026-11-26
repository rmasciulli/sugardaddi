package li.masciul.sugardaddi.data.sources.ciqual.api.dto;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;

/**
 * CiqualElasticsearchFood - Ciqual food data from Elasticsearch _source
 *
 * Represents a complete food item from the Ciqual database.
 * Contains basic information (always present) and detailed nutrition data (only in product queries).
 *
 * TWO USAGE MODES:
 * ================
 * 1. SEARCH RESULTS: Only basic fields populated (code, names, categories)
 * 2. PRODUCT DETAILS: basic fields plus the "compos" nutrition array
 *
 * BASIC FIELDS (always present):
 * - code: Ciqual food code (e.g., "13014")
 * - nomFr: French name (e.g., "Fraise,crue")
 * - nomEn: English name (may be null)
 * - groupeAfficheFr: Display category (e.g., "fruitscrus")
 * - urlFr, urlEn: Website URLs
 * - grps: Category hierarchy
 *
 * NUTRITION: only in the "compos" array, one entry per constituent with its
 * name and value (see CiqualElasticsearchComposition, read through
 * CiqualConstituents). The API has no flat nutrient fields.
 *
 * EXAMPLE SEARCH RESULT:
 * ```json
 * {
 *   "code": "13014",
 *   "nomFr": "Fraise,crue",
 *   "groupeAfficheFr": "fruitscrus",
 *   "grps": [
 *     {"code": "02", "lvl": 1},
 *     {"code": "0204", "lvl": 2}
 *   ]
 * }
 * ```
 *
 * EXAMPLE DETAILED PRODUCT:
 * ```json
 * {
 *   "code": "13014",
 *   "nomFr": "Fraise,crue",
 *   "compos": [
 *     {"constNomFr": "Energie, Règlement UE N° 1169/2011 (kcal/100 g)", "compoTeneur": "32", ...},
 *     ...
 *   ]
 * }
 * ```
 *
 * @author SugarDaddi Team
 * @version 1.0
 */
public class CiqualElasticsearchFood {

    // ========== BASIC IDENTIFICATION ==========

    /**
     * Ciqual food code (unique identifier)
     * Example: "13014" for strawberries
     * Always present
     */
    @SerializedName("code")
    private String code;

    /**
     * French name
     * Example: "Fraise,crue"
     * Always present
     */
    @SerializedName("nomFr")
    private String nomFr;

    /**
     * English name
     * Example: "Strawberry, raw"
     * May be null or empty
     */
    @SerializedName("nomEng")
    private String nomEng;

    /**
     * Display category in French
     * Example: "fruitscrus" (raw fruits)
     * Used for grouping in UI
     */
    @SerializedName("groupeAfficheFr")
    private String groupeAfficheFr;

    /**
     * Display category in English
     * May be null
     */
    @SerializedName("groupeAfficheEng")
    private String groupeAfficheEng;

    /**
     * French website URL
     * Example: "/aliments/13014/fraise-crue"
     * Relative path on ciqual.anses.fr
     */
    @SerializedName("urlFr")
    private String urlFr;

    /**
     * English website URL
     * Example: "/aliments/13014/strawberry-raw"
     * May be null
     */
    @SerializedName("urlEng")
    private String urlEng;

    /**
     * Category hierarchy
     * Array of category groups with levels
     * Example: [{"code": "02", "lvl": 1}, {"code": "0204", "lvl": 2}]
     */
    @SerializedName("grps")
    private List<CategoryGroup> grps;

    // ========== SORTING/INDEXING FIELDS (excluded in searches) ==========

    /**
     * French sort name (normalized for sorting)
     * Usually excluded from search results via _source.excludes
     */
    @SerializedName("nomSortFr")
    private String nomSortFr;

    /**
     * English sort name (normalized for sorting)
     * Usually excluded from search results
     */
    @SerializedName("nomSortEng")
    private String nomSortEng;

    /**
     * French index name (optimized for search)
     * Usually excluded from search results
     */
    @SerializedName("nomIndexFr")
    private String nomIndexFr;

    /**
     * English index name (optimized for search)
     * Usually excluded from search results
     */
    @SerializedName("nomIndexEng")
    private String nomIndexEng;
    // ========== NUTRITION DATA (compos array) ==========

    /**
     * Composition data - ALL nutrition information
     *
     * CRITICAL: Ciqual's Elasticsearch API does NOT return direct fields like
     * "energie_kcal", "proteines", "glucides", etc. Instead, ALL nutrition data
     * is in this "compos" array where each entry represents one nutrient.
     *
     * Each CiqualElasticsearchComposition contains:
     * - compoTeneur: The value (e.g., "492")
     * - constNomFr/Eng: Nutrient name (e.g., "Energie...kcal", "Protéines")
     * - compoCodeConfiance: Quality code (A/B/C/D)
     *
     * To extract nutrition, the mapper matches constNomFr/Eng against patterns.
     *
     * Example from API:
     * ```json
     * "compos": [
     *   {"compoTeneur":"492", "constNomFr":"Energie...kcal/100 g)"},
     *   {"compoTeneur":"8.7", "constNomFr":"Protéines (g/100 g)"},
     *   {"compoTeneur":"54", "constNomFr":"Glucides (g/100 g)"}
     * ]
     * ```
     *
     * Note: This field is excluded in search queries (_source.excludes=["compos"])
     * and only included in product detail queries for performance.
     *
     * @see CiqualElasticsearchComposition
     */
    @SerializedName("compos")
    private List<CiqualElasticsearchComposition> compos;

    // ========== NESTED CLASSES ==========

    /**
     * Category group with hierarchy level
     * Used to organize foods into nested categories
     *
     * Example: {"code": "02", "lvl": 1} = Level 1 category "Fruits"
     */
    public static class CategoryGroup {
        /**
         * Category code
         * Hierarchical code (e.g., "02" > "0204" > "020401")
         */
        @SerializedName("code")
        private String code;

        /**
         * Hierarchy level
         * 1 = top level (e.g., "Fruits")
         * 2 = subcategory (e.g., "Raw fruits")
         * 3 = specific type (e.g., "Berries")
         */
        @SerializedName("lvl")
        private Integer lvl;

        public String getCode() { return code; }
        public Integer getLevel() { return lvl; }

        @Override
        public String toString() {
            return String.format("Category[code=%s, lvl=%d]", code, lvl);
        }
    }

    // ========== GETTERS - BASIC FIELDS ==========

    public String getCode() { return code; }
    public String getNomFr() { return nomFr; }
    public String getNomEng() { return nomEng; }
    public String getGroupeAfficheFr() { return groupeAfficheFr; }
    public String getGroupeAfficheEng() { return groupeAfficheEng; }
    public String getUrlFr() { return urlFr; }
    public String getUrlEng() { return urlEng; }

    public List<CategoryGroup> getGroups() {
        return grps != null ? grps : new ArrayList<>();
    }

    /**
     * Gets the composition (nutrition) data
     * Returns empty list if no nutrition data available
     *
     * @return List of nutrition components
     */
    public List<CiqualElasticsearchComposition> getCompos() {
        return compos != null ? compos : new ArrayList<>();
    }

    // ========== GETTERS - SORTING/INDEXING ==========

    public String getNomSortFr() { return nomSortFr; }
    public String getNomSortEng() { return nomSortEng; }
    public String getNomIndexFr() { return nomIndexFr; }
    public String getNomIndexEng() { return nomIndexEng; }

    // ========== CONVENIENCE METHODS ==========

    /**
     * Gets the display name for a given language
     *
     * @param language Language code ("fr" or "en")
     * @return Name in requested language, or French as fallback
     */
    public String getName(String language) {
        if ("en".equalsIgnoreCase(language) && nomEng != null && !nomEng.isEmpty()) {
            return nomEng;
        }
        return nomFr != null ? nomFr : "";
    }

    /**
     * Gets the URL for a given language
     *
     * @param language Language code ("fr" or "en")
     * @return URL in requested language, or French as fallback
     */
    public String getUrl(String language) {
        if ("en".equalsIgnoreCase(language) && urlEng != null && !urlEng.isEmpty()) {
            return urlEng;
        }
        return urlFr;
    }

    /**
     * Checks if this food has detailed nutrition data
     *
     * @return true if at least one nutrition field is populated
     */
    public boolean hasNutritionData() {
        return compos != null && !compos.isEmpty();
    }

    /**
     * Gets the full website URL
     *
     * @param language Language code
     * @return Full URL to food page on ciqual.anses.fr
     */
    public String getFullUrl(String language) {
        String relativeUrl = getUrl(language);
        if (relativeUrl != null && !relativeUrl.isEmpty()) {
            return "https://ciqual.anses.fr" + relativeUrl;
        }
        return null;
    }

    /**
     * Gets the top-level category code
     *
     * @return Top category code (level 1), or null if no categories
     */
    public String getTopCategoryCode() {
        if (grps != null) {
            for (CategoryGroup group : grps) {
                if (group.lvl != null && group.lvl == 1) {
                    return group.code;
                }
            }
        }
        return null;
    }

    // ========== DEBUG ==========

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("CiqualElasticsearchFood[code=%s, name=%s", code, nomFr));

        if (hasNutritionData()) {
            sb.append(", compos=").append(compos.size());
        }

        sb.append("]");
        return sb.toString();
    }
}