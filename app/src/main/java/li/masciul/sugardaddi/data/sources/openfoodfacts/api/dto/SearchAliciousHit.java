package li.masciul.sugardaddi.data.sources.openfoodfacts.api.dto;

import androidx.annotation.Nullable;
import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * SearchAliciousHit - Individual product result from search-a-licious API
 *
 * ELASTICSEARCH DOCUMENT STRUCTURE:
 * Each hit represents a product document from the OpenFoodFacts Elasticsearch index.
 * Fields are dynamic and may not always be present, depending on:
 * - Product data completeness
 * - Fields requested in query
 * - Language availability
 *
 * FIELD NAMING CONVENTIONS:
 * - Language-specific fields: product_name_en, product_name_fr
 * - Arrays: brands[], categories_tags[]
 * - Scores: nutrition_grades (plural), ecoscore_grade
 * - Nested objects: ecoscore_data → agribalyse (proper Gson nested DTOs)
 *
 * LANGUAGE FALLBACK STRATEGY:
 * For localized fields (product_name, category):
 * 1. Try language-specific field (e.g., product_name_en)
 * 2. Fall back to default field (product_name)
 * 3. Fall back to secondary language (product_name_fr)
 *
 * CATEGORY STRATEGY:
 * Priority: ecoscore_data.agribalyse.name_xx → categories_tags[last cleaned]
 * Agribalyse names are preferred because:
 * - Present in both languages (name_en + name_fr always together)
 * - Consistent between Searchalicious and OFF v2 API
 * - Official ADEME/ANSES reference, stable numeric code
 * - Searchalicious categories_tags is a stale snapshot (subset of OFF v2 hierarchy)
 *
 * IMPORTANT: agribalyse.code is a String in the JSON ("24036"), not an integer.
 *
 * @version 3.0 - Proper nested ecoscore_data deserialization
 */
public class SearchAliciousHit {

    // ========== IDENTIFICATION ==========

    /** Product barcode (EAN-13, UPC-A, etc.) — primary identifier */
    @SerializedName("code")
    private String code;

    // ========== PRODUCT NAMES ==========

    /** Default product name (language-neutral or main language) */
    @SerializedName("product_name")
    @Nullable
    private String productName;

    /** Product name in English */
    @SerializedName("product_name_en")
    @Nullable
    private String productNameEn;

    /** Product name in French */
    @SerializedName("product_name_fr")
    @Nullable
    private String productNameFr;

    /** Product name in Spanish */
    @SerializedName("product_name_es")
    @Nullable
    private String productNameEs;

    /** Product name in German */
    @SerializedName("product_name_de")
    @Nullable
    private String productNameDe;

    // ========== BRAND ==========

    /**
     * Brand names array — use brands[0] for display.
     * Use getBrand() helper instead of direct access.
     */
    @SerializedName("brands")
    @Nullable
    private List<String> brands;

    // ========== CATEGORIES ==========

    /**
     * Category tags (stale Elasticsearch snapshot of OFF taxonomy).
     * NOTE: This array is a subset of OFF v2's categories_hierarchy.
     * The last element is the most specific category available in this snapshot,
     * but may be less specific than what OFF v2 returns for the same product.
     * Prefer agribalyse names when available.
     * Format: ["en:snacks", "en:sweet-snacks", "en:biscuits-and-cakes", ...]
     */
    @SerializedName("categories_tags")
    @Nullable
    private List<String> categoriesTags;

    /**
     * EcoScore data including Agribalyse category information.
     * This is a nested JSON object: ecoscore_data → agribalyse → name_en/name_fr/code
     * Populated when the product has been assigned an EcoScore/Green-Score.
     * Use getAgribalyseNameEn(), getAgribalyseNameFr(), getAgribalyseCode() helpers.
     */
    @SerializedName("ecoscore_data")
    @Nullable
    private EcoScoreData ecoScoreData;

    // ========== IMAGES ==========

    /** Main product image URL (full size) */
    @SerializedName("image_url")
    @Nullable
    private String imageUrl;

    /** Front image URL (preferred display image) */
    @SerializedName("image_front_url")
    @Nullable
    private String imageFrontUrl;

    /**
     * Small front image URL — recommended for list views.
     * Optimized thumbnail, fastest to load.
     */
    @SerializedName("image_front_small_url")
    @Nullable
    private String imageFrontSmallUrl;

    /** Small image URL (fallback if front image unavailable) */
    @SerializedName("image_small_url")
    @Nullable
    private String imageSmallUrl;

    // ========== SCORES ==========

    /**
     * Nutri-Score grade: a, b, c, d, e (lowercase).
     * Note: field is "nutrition_grades" (plural) in Searchalicious,
     * unlike OFF v2 which uses "nutrition_grade" (singular).
     */
    @SerializedName("nutrition_grades")
    @Nullable
    private String nutritionGrades;

    /**
     * EcoScore/Green-Score grade: a, b, c, d, e (lowercase).
     * Environmental impact score — a = minimal impact.
     */
    @SerializedName("ecoscore_grade")
    @Nullable
    private String ecoscoreGrade;

    /**
     * NOVA group: 1, 2, 3, or 4.
     * Food processing classification — 4 = ultra-processed.
     */
    @SerializedName("nova_group")
    @Nullable
    private Integer novaGroup;

    // ========== METADATA ==========

    /**
     * Product quantity string.
     * Format: "100 g", "1 L", "250 ml", "225 g"
     */
    @SerializedName("quantity")
    @Nullable
    private String quantity;

    /**
     * Data completeness score (0.0 to 1.0).
     * Indicates how much product data is filled in OFF.
     * Use for quality filtering: >= 0.5 is usable, >= 0.8 is good.
     */
    @SerializedName("completeness")
    @Nullable
    private Double completeness;

    /**
     * Elasticsearch relevance score for this search query.
     * Higher = more relevant. Used internally by Elasticsearch ranking.
     */
    @SerializedName("_score")
    @Nullable
    private Double score;

    // ========== NESTED DTOs ==========

    /**
     * EcoScoreData — maps the ecoscore_data JSON object.
     *
     * The full ecoscore_data object contains adjustments, packaging scores,
     * per-country grades, etc. We only map what we need: the agribalyse block.
     * Gson's strict=false equivalent: unknown fields are silently ignored.
     */
    public static class EcoScoreData {

        /**
         * Agribalyse data block — the cross-source category bridge.
         * Present when the product has been matched to an Agribalyse/Ciqual food entry.
         * Contains bilingual names (EN + FR) and the numeric Agribalyse code.
         */
        @SerializedName("agribalyse")
        @Nullable
        private AgribalyseData agribalyse;

        @Nullable
        public AgribalyseData getAgribalyse() {
            return agribalyse;
        }

        public void setAgribalyse(AgribalyseData agribalyse) {
            this.agribalyse = agribalyse;
        }
    }

    /**
     * AgribalyseData — maps the ecoscore_data.agribalyse JSON object.
     *
     * Agribalyse is the French LCA (Life Cycle Assessment) database published
     * by ADEME/ANSES. It's built on Ciqual food data, so:
     * - agribalyse.code == ciqual_food_code for exact matches
     * - agribalyse.agribalyse_proxy_food_code == ciqual code for proxy matches
     *
     * The code is stored as a String in the JSON ("24036"), not an integer.
     * name_en and name_fr are always present together when the block exists.
     */
    public static class AgribalyseData {

        /**
         * Agribalyse/Ciqual numeric code as String (e.g. "24036").
         * This is the unified taxonomy key linking OFF products to Ciqual entries.
         * Use as the cross-source category ID for comparison purposes.
         */
        @SerializedName("code")
        @Nullable
        private String code;

        /**
         * Agribalyse category name in English.
         * Example: "Biscuit (cookie), with chocolate, prepacked"
         * More precise than categories_tags — official ANSES label.
         */
        @SerializedName("name_en")
        @Nullable
        private String nameEn;

        /**
         * Agribalyse category name in French.
         * Example: "Biscuit sec chocolaté, préemballé"
         * Always present alongside name_en when agribalyse block exists.
         */
        @SerializedName("name_fr")
        @Nullable
        private String nameFr;

        /**
         * Agribalyse proxy food code (String).
         * Present when the product was matched to an approximate Agribalyse entry
         * (proxy match) rather than an exact food entry.
         * May equal code when the proxy is the same as the matched entry.
         */
        @SerializedName("agribalyse_proxy_food_code")
        @Nullable
        private String agribalyseProxyFoodCode;

        // Getters
        @Nullable public String getCode() { return code; }
        @Nullable public String getNameEn() { return nameEn; }
        @Nullable public String getNameFr() { return nameFr; }
        @Nullable public String getAgribalyseProxyFoodCode() { return agribalyseProxyFoodCode; }

        // Setters
        public void setCode(String code) { this.code = code; }
        public void setNameEn(String nameEn) { this.nameEn = nameEn; }
        public void setNameFr(String nameFr) { this.nameFr = nameFr; }
        public void setAgribalyseProxyFoodCode(String code) { this.agribalyseProxyFoodCode = code; }
    }

    // ========== CONSTRUCTORS ==========

    /** Default constructor required by Gson */
    public SearchAliciousHit() {}

    // ========== AGRIBALYSE CONVENIENCE ACCESSORS ==========
    // These delegate through the nested DTOs so callers don't need to
    // chain null checks on ecoScoreData → agribalyse → field.

    /**
     * Get Agribalyse category name in English, or null if not available.
     * Convenience accessor — handles null chain internally.
     */
    @Nullable
    public String getAgribalyseNameEn() {
        if (ecoScoreData == null) return null;
        AgribalyseData agri = ecoScoreData.getAgribalyse();
        if (agri == null) return null;
        return agri.getNameEn();
    }

    /**
     * Get Agribalyse category name in French, or null if not available.
     * Convenience accessor — handles null chain internally.
     */
    @Nullable
    public String getAgribalyseNameFr() {
        if (ecoScoreData == null) return null;
        AgribalyseData agri = ecoScoreData.getAgribalyse();
        if (agri == null) return null;
        return agri.getNameFr();
    }

    /**
     * Get Agribalyse/Ciqual numeric code as String (e.g. "24036"), or null.
     * This is the cross-source taxonomy key — same code in Ciqual and Agribalyse
     * for exact food matches. Use for CategoryComparison and CategoryStats.
     */
    @Nullable
    public String getAgribalyseCode() {
        if (ecoScoreData == null) return null;
        AgribalyseData agri = ecoScoreData.getAgribalyse();
        if (agri == null) return null;
        return agri.getCode();
    }

    /**
     * Check whether this hit has Agribalyse category data.
     * When true, getAgribalyseNameEn() and getAgribalyseNameFr() are non-null.
     */
    public boolean hasAgribalyseData() {
        return getAgribalyseNameEn() != null || getAgribalyseNameFr() != null;
    }

    // ========== GETTERS ==========

    public String getCode() { return code; }

    @Nullable public String getProductName() { return productName; }
    @Nullable public String getProductNameEn() { return productNameEn; }
    @Nullable public String getProductNameFr() { return productNameFr; }
    @Nullable public String getProductNameEs() { return productNameEs; }
    @Nullable public String getProductNameDe() { return productNameDe; }
    @Nullable public List<String> getBrands() { return brands; }
    @Nullable public List<String> getCategoriesTags() { return categoriesTags; }
    @Nullable public EcoScoreData getEcoScoreData() { return ecoScoreData; }
    @Nullable public String getImageUrl() { return imageUrl; }
    @Nullable public String getImageFrontUrl() { return imageFrontUrl; }
    @Nullable public String getImageFrontSmallUrl() { return imageFrontSmallUrl; }
    @Nullable public String getImageSmallUrl() { return imageSmallUrl; }
    @Nullable public String getNutritionGrades() { return nutritionGrades; }
    @Nullable public String getEcoscoreGrade() { return ecoscoreGrade; }
    @Nullable public Integer getNovaGroup() { return novaGroup; }
    @Nullable public String getQuantity() { return quantity; }
    @Nullable public Double getCompleteness() { return completeness; }
    @Nullable public Double getScore() { return score; }

    // ========== SETTERS ==========

    public void setCode(String code) { this.code = code; }
    public void setProductName(String productName) { this.productName = productName; }
    public void setProductNameEn(String productNameEn) { this.productNameEn = productNameEn; }
    public void setProductNameFr(String productNameFr) { this.productNameFr = productNameFr; }
    public void setProductNameEs(String productNameEs) { this.productNameEs = productNameEs; }
    public void setProductNameDe(String productNameDe) { this.productNameDe = productNameDe; }
    public void setBrands(List<String> brands) { this.brands = brands; }
    public void setCategoriesTags(List<String> categoriesTags) { this.categoriesTags = categoriesTags; }
    public void setEcoScoreData(EcoScoreData ecoScoreData) { this.ecoScoreData = ecoScoreData; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public void setImageFrontUrl(String imageFrontUrl) { this.imageFrontUrl = imageFrontUrl; }
    public void setImageFrontSmallUrl(String imageFrontSmallUrl) { this.imageFrontSmallUrl = imageFrontSmallUrl; }
    public void setImageSmallUrl(String imageSmallUrl) { this.imageSmallUrl = imageSmallUrl; }
    public void setNutritionGrades(String nutritionGrades) { this.nutritionGrades = nutritionGrades; }
    public void setEcoscoreGrade(String ecoscoreGrade) { this.ecoscoreGrade = ecoscoreGrade; }
    public void setNovaGroup(Integer novaGroup) { this.novaGroup = novaGroup; }
    public void setQuantity(String quantity) { this.quantity = quantity; }
    public void setCompleteness(Double completeness) { this.completeness = completeness; }
    public void setScore(Double score) { this.score = score; }

    // ========== HELPER METHODS ==========

    /**
     * Get localized product name with fallback strategy.
     *
     * Priority:
     * 1. Language-specific field (product_name_en / product_name_fr / etc.)
     * 2. Default product_name field
     * 3. English name (most common in OFF)
     * 4. French name (high data coverage in OFF)
     *
     * @param language Preferred language code ("en", "fr", "es", "de")
     * @return Product name in best available language, or null
     */
    @Nullable
    public String getLocalizedProductName(String language) {
        if (language != null) {
            switch (language.toLowerCase()) {
                case "en":
                    if (productNameEn != null) return productNameEn;
                    break;
                case "fr":
                    if (productNameFr != null) return productNameFr;
                    break;
                case "es":
                    if (productNameEs != null) return productNameEs;
                    break;
                case "de":
                    if (productNameDe != null) return productNameDe;
                    break;
            }
        }

        // Fallback chain
        if (productName != null) return productName;
        if (productNameEn != null) return productNameEn;
        if (productNameFr != null) return productNameFr;
        return null;
    }

    /**
     * Get Agribalyse category name for the requested language, with cross-language fallback.
     *
     * When agribalyse data is present, both name_en and name_fr are always available.
     * This method handles the case where a language is requested but only the other is present.
     *
     * @param language Preferred language code ("en", "fr")
     * @return Agribalyse name in best available language, or null if no agribalyse data
     */
    @Nullable
    public String getLocalizedAgribalyseName(String language) {
        String nameEn = getAgribalyseNameEn();
        String nameFr = getAgribalyseNameFr();

        if (language != null) {
            switch (language.toLowerCase()) {
                case "en":
                    if (nameEn != null) return nameEn;
                    return nameFr; // cross-language fallback
                case "fr":
                    if (nameFr != null) return nameFr;
                    return nameEn; // cross-language fallback
            }
        }

        // Default: prefer EN
        return nameEn != null ? nameEn : nameFr;
    }

    /**
     * Get display category for this product in the requested language.
     *
     * Priority:
     * 1. Agribalyse name — standardized, bilingual, consistent across APIs
     * 2. Last categories_tags entry — raw taxonomy tag (may be stale/noisy)
     *
     * The returned string from categories_tags may still contain the "en:" prefix —
     * callers should clean it with cleanCategoryTag() if displaying to users.
     *
     * @param language Preferred language code ("en", "fr")
     * @return Category display string, or null if none available
     */
    @Nullable
    public String getCategory(String language) {
        // Agribalyse is preferred: standardized, bilingual, stable
        String agribalyseName = getLocalizedAgribalyseName(language);
        if (agribalyseName != null && !agribalyseName.trim().isEmpty()) {
            return agribalyseName;
        }

        // Fallback: last (most specific) categories_tags entry
        if (categoriesTags != null && !categoriesTags.isEmpty()) {
            return categoriesTags.get(categoriesTags.size() - 1);
        }

        return null;
    }

    /**
     * Get first brand name, or null if no brands.
     */
    @Nullable
    public String getBrand() {
        if (brands != null && !brands.isEmpty()) {
            return brands.get(0);
        }
        return null;
    }

    /**
     * Get best available image URL for list display.
     *
     * Priority (fastest/smallest first):
     * 1. image_front_small_url (optimized thumbnail)
     * 2. image_front_url (full front image)
     * 3. image_small_url (generic small)
     * 4. image_url (full size — avoid for lists)
     *
     * @return Best available image URL, or null
     */
    @Nullable
    public String getBestImageUrl() {
        if (imageFrontSmallUrl != null) return imageFrontSmallUrl;
        if (imageFrontUrl != null) return imageFrontUrl;
        if (imageSmallUrl != null) return imageSmallUrl;
        return imageUrl;
    }

    /**
     * Check if product has sufficient data quality for display.
     * Requires: barcode, product name, completeness >= 0.5.
     */
    public boolean isPertinent() {
        return code != null
                && !code.trim().isEmpty()
                && getLocalizedProductName("en") != null
                && completeness != null
                && completeness >= 0.5;
    }

    /**
     * Get quality level based on completeness score.
     * @return "poor", "fair", "good", "excellent", or "unknown"
     */
    public String getQualityLevel() {
        if (completeness == null) return "unknown";
        if (completeness >= 0.9) return "excellent";
        if (completeness >= 0.7) return "good";
        if (completeness >= 0.5) return "fair";
        return "poor";
    }

    // ========== OBJECT METHODS ==========

    @Override
    public String toString() {
        return String.format(
                "SearchAliciousHit{code='%s', name='%s', brand='%s', agribalyse='%s', completeness=%.2f}",
                code,
                getLocalizedProductName("en"),
                getBrand(),
                getAgribalyseNameEn() != null ? getAgribalyseNameEn() : "none",
                completeness != null ? completeness : 0.0);
    }
}