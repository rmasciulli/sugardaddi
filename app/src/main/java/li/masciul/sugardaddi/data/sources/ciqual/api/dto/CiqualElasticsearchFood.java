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
 * 2. PRODUCT DETAILS: All fields including comprehensive nutrition data
 *
 * BASIC FIELDS (always present):
 * - code: Ciqual food code (e.g., "13014")
 * - nomFr: French name (e.g., "Fraise,crue")
 * - nomEn: English name (may be null)
 * - groupeAfficheFr: Display category (e.g., "fruitscrus")
 * - urlFr, urlEn: Website URLs
 * - grps: Category hierarchy
 *
 * NUTRITION FIELDS (only in detailed queries):
 * - All fields nullable
 * - Values in standard units (g/100g, kcal/100g, etc.)
 * - Comprehensive coverage of macros, vitamins, minerals
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
 *   "energie_kcal": 32.0,
 *   "proteines": 0.7,
 *   "glucides": 7.0,
 *   "lipides": 0.3,
 *   ...
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

    // ========== DEPRECATED DIRECT NUTRITION FIELDS ==========
    //
    // WARNING: The fields below do NOT exist in Ciqual's Elasticsearch API!
    // The actual API returns all nutrition data in the "compos" array above.
    // These fields will ALWAYS be null. Use getCompos() instead.
    //
// ========== ENERGY VALUES (kcal, kJ) ==========

    /**
     * Energy in kilocalories per 100g
     * Standard nutritional energy value
     */
    @SerializedName("energie_kcal")
    private Double energieKcal;

    /**
     * Energy in kilojoules per 100g
     * Alternative energy measurement (1 kcal â‰ˆ 4.184 kJ)
     */
    @SerializedName("energie_kj")
    private Double energieKj;

    // ========== MACRONUTRIENTS (g/100g) ==========

    /**
     * Water content (g/100g)
     * Typically 50-95% for most foods
     */
    @SerializedName("eau")
    private Double eau;

    /**
     * Protein content (g/100g)
     * Essential for body building and repair
     */
    @SerializedName("proteines")
    private Double proteines;

    /**
     * Total carbohydrates (g/100g)
     * Includes sugars and starches
     */
    @SerializedName("glucides")
    private Double glucides;

    /**
     * Total lipids/fats (g/100g)
     * Includes all types of fats
     */
    @SerializedName("lipides")
    private Double lipides;

    /**
     * Sugars (g/100g)
     * Simple carbohydrates (glucose, fructose, sucrose, etc.)
     */
    @SerializedName("sucres")
    private Double sucres;

    /**
     * Dietary fiber (g/100g)
     * Non-digestible carbohydrates
     */
    @SerializedName("fibres")
    private Double fibres;

    // ========== SPECIFIC FATS (g/100g) ==========

    /**
     * Saturated fatty acids (g/100g)
     * "Bad" fats that should be limited
     */
    @SerializedName("ag_satures")
    private Double agSatures;

    /**
     * Monounsaturated fatty acids (g/100g)
     * "Good" fats (e.g., olive oil)
     */
    @SerializedName("ag_monoinsatures")
    private Double agMonoinsatures;

    /**
     * Polyunsaturated fatty acids (g/100g)
     * "Good" fats including omega-3 and omega-6
     */
    @SerializedName("ag_polyinsatures")
    private Double agPolyinsatures;

    /**
     * Cholesterol (mg/100g)
     * Only found in animal products
     */
    @SerializedName("cholesterol")
    private Double cholesterol;

    // ========== MINERALS (mg/100g unless specified) ==========

    /**
     * Sodium (mg/100g)
     * Important for fluid balance
     */
    @SerializedName("sodium")
    private Double sodium;

    /**
     * Salt equivalent (g/100g)
     * Calculated from sodium (salt â‰ˆ sodium Ã— 2.5)
     */
    @SerializedName("sel")
    private Double sel;

    /**
     * Calcium (mg/100g)
     * Important for bones and teeth
     */
    @SerializedName("calcium")
    private Double calcium;

    /**
     * Magnesium (mg/100g)
     * Important for muscle and nerve function
     */
    @SerializedName("magnesium")
    private Double magnesium;

    /**
     * Phosphorus (mg/100g)
     * Important for bones and energy metabolism
     */
    @SerializedName("phosphore")
    private Double phosphore;

    /**
     * Potassium (mg/100g)
     * Important for heart and muscle function
     */
    @SerializedName("potassium")
    private Double potassium;

    /**
     * Iron (mg/100g)
     * Important for blood and oxygen transport
     */
    @SerializedName("fer")
    private Double fer;

    /**
     * Zinc (mg/100g)
     * Important for immune function
     */
    @SerializedName("zinc")
    private Double zinc;

    // ========== VITAMINS ==========

    /**
     * Vitamin C / Ascorbic acid (mg/100g)
     * Antioxidant, immune support
     */
    @SerializedName("vitamine_c")
    private Double vitamineC;

    /**
     * Vitamin B1 / Thiamin (mg/100g)
     * Energy metabolism
     */
    @SerializedName("vitamine_b1")
    private Double vitamineB1;

    /**
     * Vitamin B2 / Riboflavin (mg/100g)
     * Energy metabolism
     */
    @SerializedName("vitamine_b2")
    private Double vitamineB2;

    /**
     * Vitamin B3 / Niacin (mg/100g)
     * Energy metabolism
     */
    @SerializedName("vitamine_b3")
    private Double vitamineB3;

    /**
     * Vitamin B5 / Pantothenic acid (mg/100g)
     * Energy metabolism
     */
    @SerializedName("vitamine_b5")
    private Double vitamineB5;

    /**
     * Vitamin B6 / Pyridoxine (mg/100g)
     * Protein metabolism
     */
    @SerializedName("vitamine_b6")
    private Double vitamineB6;

    /**
     * Vitamin B9 / Folate (Âµg/100g)
     * DNA synthesis, important for pregnancy
     */
    @SerializedName("vitamine_b9")
    private Double vitamineB9;

    /**
     * Vitamin B12 / Cobalamin (Âµg/100g)
     * Red blood cell formation (only in animal products)
     */
    @SerializedName("vitamine_b12")
    private Double vitamineB12;

    /**
     * Vitamin A / Retinol (Âµg/100g)
     * Vision, immune function
     */
    @SerializedName("vitamine_a")
    private Double vitamineA;

    /**
     * Vitamin D (Âµg/100g)
     * Calcium absorption, bone health
     */
    @SerializedName("vitamine_d")
    private Double vitamineD;

    /**
     * Vitamin E / Tocopherol (mg/100g)
     * Antioxidant
     */
    @SerializedName("vitamine_e")
    private Double vitamineE;

    /**
     * Vitamin K (Âµg/100g)
     * Blood clotting
     */
    @SerializedName("vitamine_k")
    private Double vitamineK;

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

    // ========== GETTERS - ENERGY ==========

    public Double getEnergieKcal() { return energieKcal; }
    public Double getEnergieKj() { return energieKj; }

    // ========== GETTERS - MACRONUTRIENTS ==========

    public Double getEau() { return eau; }
    public Double getProteines() { return proteines; }
    public Double getGlucides() { return glucides; }
    public Double getLipides() { return lipides; }
    public Double getSucres() { return sucres; }
    public Double getFibres() { return fibres; }

    // ========== GETTERS - FATS ==========

    public Double getAgSatures() { return agSatures; }
    public Double getAgMonoinsatures() { return agMonoinsatures; }
    public Double getAgPolyinsatures() { return agPolyinsatures; }
    public Double getCholesterol() { return cholesterol; }

    // ========== GETTERS - MINERALS ==========

    public Double getSodium() { return sodium; }
    public Double getSel() { return sel; }
    public Double getCalcium() { return calcium; }
    public Double getMagnesium() { return magnesium; }
    public Double getPhosphore() { return phosphore; }
    public Double getPotassium() { return potassium; }
    public Double getFer() { return fer; }
    public Double getZinc() { return zinc; }

    // ========== GETTERS - VITAMINS ==========

    public Double getVitamineC() { return vitamineC; }
    public Double getVitamineB1() { return vitamineB1; }
    public Double getVitamineB2() { return vitamineB2; }
    public Double getVitamineB3() { return vitamineB3; }
    public Double getVitamineB5() { return vitamineB5; }
    public Double getVitamineB6() { return vitamineB6; }
    public Double getVitamineB9() { return vitamineB9; }
    public Double getVitamineB12() { return vitamineB12; }
    public Double getVitamineA() { return vitamineA; }
    public Double getVitamineD() { return vitamineD; }
    public Double getVitamineE() { return vitamineE; }
    public Double getVitamineK() { return vitamineK; }

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
            sb.append(String.format(", energy=%.1fkcal", energieKcal != null ? energieKcal : 0.0));
        }

        sb.append("]");
        return sb.toString();
    }
}