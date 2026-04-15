package li.masciul.sugardaddi.data.sources.openfoodfacts.api.dto;

import androidx.annotation.Nullable;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * OpenFoodFactsProduct - COMPLETE OpenFoodFacts product DTO
 *
 * Maps ALL product fields from OFF v2 API to our FoodProduct domain model.
 * Only includes fields that ACTUALLY EXIST in the OFF API.
 *
 * v2.0 - Added ecoscore_data nested DTOs for Agribalyse category extraction.
 * The ecoscore_data.agribalyse block provides:
 * - Bilingual category names (name_en + name_fr always together)
 * - Agribalyse/Ciqual numeric code (cross-source taxonomy key)
 * Available for products that have been assigned a Green-Score (~80%+ of OFF).
 *
 * NOTE: OFF v2 does not always return ecoscore_data even when it exists in the DB
 * (Nutella is a known example). Always null-check getEcoScoreData().
 */
public class OpenFoodFactsProduct {

    // ========== IDENTIFICATION ==========
    @SerializedName("code")
    private String code;  // Barcode

    @SerializedName("_id")
    private String id;

    // ========== BASIC INFO - MULTILINGUAL ==========
    @SerializedName("product_name")
    private String productName;  // Generic name, language-independent fallback

    @SerializedName("product_name_en")
    private String productNameEn;  // English product name

    @SerializedName("product_name_fr")
    private String productNameFr;  // French product name

    // NOTE: DE, ES, IT names do NOT exist in OFF DTO

    @SerializedName("generic_name")
    private String genericName;

    @SerializedName("generic_name_en")
    private String genericNameEn;

    @SerializedName("generic_name_fr")
    private String genericNameFr;

    @SerializedName("brands")
    private String brands;

    @SerializedName("quantity")
    private String quantity;

    // ========== CATEGORIES ==========
    @SerializedName("categories")
    private String categories;  // Comma-separated free-text string (multi-language, dirty)

    @SerializedName("categories_tags")
    private List<String> categoriesTags;  // Normalized en: tag list

    @SerializedName("categories_hierarchy")
    private List<String> categoriesHierarchy;  // Ordered general→specific path (authoritative)

    // ========== IMAGES ==========
    @SerializedName("image_url")
    private String imageUrl;

    @SerializedName("image_small_url")
    private String imageSmallUrl;

    @SerializedName("image_front_url")
    private String imageFrontUrl;

    @SerializedName("image_front_small_url")
    private String imageFrontSmallUrl;

    // ========== INGREDIENTS - MULTILINGUAL ==========
    @SerializedName("ingredients_text")
    private String ingredientsText;

    @SerializedName("ingredients_text_en")
    private String ingredientsTextEn;

    @SerializedName("ingredients_text_fr")
    private String ingredientsTextFr;

    // ========== ALLERGENS - WITH FLEXIBLE DESERIALIZER ==========
    @SerializedName("allergens")
    private String allergens;

    @SerializedName("allergens_tags")
    @JsonAdapter(FlexibleListDeserializer.class)
    private List<String> allergensTags;

    @SerializedName("traces")
    private String traces;

    @SerializedName("traces_tags")
    @JsonAdapter(FlexibleListDeserializer.class)
    private List<String> tracesTags;

    // ========== QUALITY SCORES ==========

    /** NutriScore grade (A-E). OFF uses "nutriscore_grade" (singular). */
    @SerializedName("nutriscore_grade")
    private String nutriscoreGrade;

    /** NutriScore numeric score (used to calculate grade) */
    @SerializedName("nutriscore_score")
    private Integer nutriscoreScore;

    /** NOVA group (1-4) — food processing classification */
    @SerializedName("nova_group")
    private String novaGroup;

    /** EcoScore/Green-Score grade (A-E) — environmental impact */
    @SerializedName("ecoscore_grade")
    private String ecoscoreGrade;

    /** EcoScore numeric score */
    @SerializedName("ecoscore_score")
    private Integer ecoscoreScore;

    /**
     * Full EcoScore data block including Agribalyse category match.
     * Present for products that have been assigned a Green-Score.
     * May be null even when ecoscore_grade is set (OFF v2 data completeness varies).
     * Use getAgribalyseNameEn() / getAgribalyseNameFr() convenience accessors.
     */
    @SerializedName("ecoscore_data")
    @Nullable
    private EcoScoreData ecoScoreData;

    // ========== NUTRITION ==========
    @SerializedName("nutriments")
    private OpenFoodFactsNutriments nutriments;

    @SerializedName("nutrition_data_per")
    private String nutritionDataPer;  // "100g" or "serving"

    @SerializedName("serving_size")
    private String servingSize;

    @SerializedName("serving_quantity")
    private Double servingQuantity;

    // ========== METADATA ==========
    @SerializedName("countries")
    private String countries;

    @SerializedName("countries_tags")
    private List<String> countriesTags;

    @SerializedName("stores")
    private String stores;

    @SerializedName("manufacturing_places")
    private String manufacturingPlaces;

    // ========== LABELS & CERTIFICATIONS ==========
    @SerializedName("labels")
    private String labels;

    @SerializedName("labels_tags")
    private List<String> labelsTags;

    // ========== STATUS ==========
    @SerializedName("states")
    private String states;

    @SerializedName("states_tags")
    private List<String> statesTags;

    @SerializedName("completeness")
    private Double completeness;

    @SerializedName("last_modified_t")
    private Long lastModified;

    // ========== NESTED DTOs ==========

    /**
     * EcoScoreData — maps the ecoscore_data JSON object from OFF v2.
     *
     * The full object contains packaging scores, per-country grades, adjustments, etc.
     * We only map the agribalyse block — Gson silently ignores unknown fields.
     *
     * Identical structure to SearchAliciousHit.EcoScoreData; kept separate
     * because they belong to different DTO classes in different packages.
     */
    public static class EcoScoreData {

        /**
         * Agribalyse data block — cross-source category bridge.
         * Present when the product has been matched to an Agribalyse/Ciqual food entry.
         * NOTE: OFF v2 does not always return this even when the product has a Green-Score.
         */
        @SerializedName("agribalyse")
        @Nullable
        private AgribalyseData agribalyse;

        @Nullable
        public AgribalyseData getAgribalyse() { return agribalyse; }
        public void setAgribalyse(AgribalyseData agribalyse) { this.agribalyse = agribalyse; }
    }

    /**
     * AgribalyseData — maps the ecoscore_data.agribalyse JSON object from OFF v2.
     *
     * Agribalyse is the ADEME/ANSES Life Cycle Assessment database, built on Ciqual.
     * The numeric code is the shared key between OFF products and Ciqual entries:
     * - agribalyse_food_code   : exact Ciqual food match (most precise)
     * - agribalyse_proxy_food_code: approximate Ciqual match (used when no exact match)
     * - code                   : the active code used for this product's Green-Score
     *
     * NOTE: code is a String in the JSON ("24036"), not an integer.
     * name_en and name_fr are always present together when this block exists.
     *
     * The agribalyse.code value may differ between Searchalicious (stale index) and
     * OFF v2 (authoritative). OFF v2 should be preferred when available.
     */
    public static class AgribalyseData {

        /**
         * Active Agribalyse/Ciqual code used for this product's Green-Score.
         * String in JSON (e.g. "24036"). Cross-source taxonomy key.
         */
        @SerializedName("code")
        @Nullable
        private String code;

        /**
         * Exact Agribalyse food code — set when an exact Ciqual match exists.
         * More precise than proxy. Prefer this over agribalyse_proxy_food_code.
         */
        @SerializedName("agribalyse_food_code")
        @Nullable
        private String agribalyseFoodCode;

        /**
         * Proxy Agribalyse food code — used when no exact Ciqual match exists.
         * The product is categorized under an approximate food type.
         */
        @SerializedName("agribalyse_proxy_food_code")
        @Nullable
        private String agribalyseProxyFoodCode;

        /**
         * Agribalyse category name in English.
         * Example: "Biscuit (cookie), with chocolate, prepacked"
         * Official ANSES scientific label — more precise than OFF taxonomy tags.
         */
        @SerializedName("name_en")
        @Nullable
        private String nameEn;

        /**
         * Agribalyse category name in French.
         * Example: "Biscuit sec chocolaté, préemballé"
         * Always present alongside name_en when this block exists.
         */
        @SerializedName("name_fr")
        @Nullable
        private String nameFr;

        // Getters
        @Nullable public String getCode() { return code; }
        @Nullable public String getAgribalyseFoodCode() { return agribalyseFoodCode; }
        @Nullable public String getAgribalyseProxyFoodCode() { return agribalyseProxyFoodCode; }
        @Nullable public String getNameEn() { return nameEn; }
        @Nullable public String getNameFr() { return nameFr; }

        // Setters
        public void setCode(String code) { this.code = code; }
        public void setAgribalyseFoodCode(String code) { this.agribalyseFoodCode = code; }
        public void setAgribalyseProxyFoodCode(String code) { this.agribalyseProxyFoodCode = code; }
        public void setNameEn(String nameEn) { this.nameEn = nameEn; }
        public void setNameFr(String nameFr) { this.nameFr = nameFr; }

        /**
         * Whether this product has an exact Ciqual food match (vs. a proxy).
         * Exact matches are more nutritionally precise for CategoryComparison.
         */
        public boolean isExactMatch() {
            return agribalyseFoodCode != null;
        }
    }

    // ========== AGRIBALYSE CONVENIENCE ACCESSORS ==========
    // Null-safe delegation through the nested DTO chain.

    /**
     * Get Agribalyse category name in English, or null if unavailable.
     */
    @Nullable
    public String getAgribalyseNameEn() {
        if (ecoScoreData == null) return null;
        AgribalyseData agri = ecoScoreData.getAgribalyse();
        if (agri == null) return null;
        return agri.getNameEn();
    }

    /**
     * Get Agribalyse category name in French, or null if unavailable.
     */
    @Nullable
    public String getAgribalyseNameFr() {
        if (ecoScoreData == null) return null;
        AgribalyseData agri = ecoScoreData.getAgribalyse();
        if (agri == null) return null;
        return agri.getNameFr();
    }

    /**
     * Get the active Agribalyse/Ciqual code as String (e.g. "24036"), or null.
     * This is the cross-source taxonomy key for CategoryComparison.
     */
    @Nullable
    public String getAgribalyseCode() {
        if (ecoScoreData == null) return null;
        AgribalyseData agri = ecoScoreData.getAgribalyse();
        if (agri == null) return null;
        return agri.getCode();
    }

    /**
     * Whether this product has Agribalyse category data available.
     */
    public boolean hasAgribalyseData() {
        return getAgribalyseNameEn() != null || getAgribalyseNameFr() != null;
    }

    // ========== GETTERS - IDENTIFICATION ==========
    public String getCode() { return code; }
    public String getId() { return id; }

    // ========== GETTERS - BASIC INFO ==========
    public String getProductName() { return productName; }
    public String getProductNameEn() { return productNameEn; }
    public String getProductNameFr() { return productNameFr; }
    public String getGenericName() { return genericName; }
    public String getGenericNameEn() { return genericNameEn; }
    public String getGenericNameFr() { return genericNameFr; }
    public String getBrands() { return brands; }
    public String getQuantity() { return quantity; }

    // ========== GETTERS - CATEGORIES ==========
    public String getCategories() { return categories; }
    public List<String> getCategoriesTags() { return categoriesTags; }
    public List<String> getCategoriesHierarchy() { return categoriesHierarchy; }

    // ========== GETTERS - IMAGES ==========
    public String getImageUrl() { return imageUrl; }
    public String getImageSmallUrl() { return imageSmallUrl; }
    public String getImageFrontUrl() { return imageFrontUrl; }
    public String getImageFrontSmallUrl() { return imageFrontSmallUrl; }

    // ========== GETTERS - INGREDIENTS ==========
    public String getIngredientsText() { return ingredientsText; }
    public String getIngredientsTextEn() { return ingredientsTextEn; }
    public String getIngredientsTextFr() { return ingredientsTextFr; }

    // ========== GETTERS - ALLERGENS ==========
    public String getAllergens() { return allergens; }
    public List<String> getAllergensTags() { return allergensTags; }
    public String getTraces() { return traces; }
    public List<String> getTracesTags() { return tracesTags; }

    // ========== GETTERS - QUALITY SCORES ==========
    public String getNutriscoreGrade() { return nutriscoreGrade; }
    public Integer getNutriscoreScore() { return nutriscoreScore; }
    public String getNovaGroup() { return novaGroup; }
    public String getEcoscoreGrade() { return ecoscoreGrade; }
    public Integer getEcoscoreScore() { return ecoscoreScore; }
    @Nullable public EcoScoreData getEcoScoreData() { return ecoScoreData; }

    // ========== GETTERS - NUTRITION ==========
    public OpenFoodFactsNutriments getNutriments() { return nutriments; }
    public String getNutritionDataPer() { return nutritionDataPer; }
    public String getServingSize() { return servingSize; }
    public Double getServingQuantity() { return servingQuantity; }

    // ========== GETTERS - METADATA ==========
    public String getCountries() { return countries; }
    public List<String> getCountriesTags() { return countriesTags; }
    public String getStores() { return stores; }
    public String getManufacturingPlaces() { return manufacturingPlaces; }
    public String getLabels() { return labels; }
    public List<String> getLabelsTags() { return labelsTags; }
    public String getStates() { return states; }
    public List<String> getStatesTags() { return statesTags; }
    public Double getCompleteness() { return completeness; }
    public Long getLastModified() { return lastModified; }

    // ========== HELPER METHODS ==========

    public boolean hasNutrition() {
        return nutriments != null;
    }

    public boolean hasImage() {
        return imageFrontUrl != null && !imageFrontUrl.isEmpty();
    }

    /**
     * Get best available product name based on language preference.
     * Fallback chain: language-specific → generic → "Unknown Product".
     */
    public String getBestProductName(String language) {
        if ("fr".equals(language) && productNameFr != null && !productNameFr.trim().isEmpty()) {
            return productNameFr;
        }
        if ("en".equals(language) && productNameEn != null && !productNameEn.trim().isEmpty()) {
            return productNameEn;
        }
        if (productName != null && !productName.trim().isEmpty()) {
            return productName;
        }
        if (genericName != null && !genericName.trim().isEmpty()) {
            return genericName;
        }
        return "Unknown Product";
    }
}