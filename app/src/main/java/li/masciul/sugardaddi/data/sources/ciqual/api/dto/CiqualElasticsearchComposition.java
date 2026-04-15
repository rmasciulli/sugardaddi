package li.masciul.sugardaddi.data.sources.ciqual.api.dto;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * CiqualElasticsearchComposition - Individual nutrition component from Ciqual database
 *
 * Represents ONE nutrition data point from the "compos" array in Ciqual's Elasticsearch response.
 * The Ciqual API doesn't provide direct nutrition fields like "energie_kcal" or "proteines".
 * Instead, ALL nutrition data is stored in a "compos" array where each entry represents
 * one nutrient with its value, unit, confidence level, and metadata.
 *
 * REAL API STRUCTURE:
 * ===================
 * Instead of:
 * ```json
 * {
 *   "energie_kcal": 492,
 *   "proteines": 8.7,
 *   "glucides": 54
 * }
 * ```
 *
 * Ciqual returns:
 * ```json
 * {
 *   "compos": [
 *     {
 *       "compoTeneur": "492",
 *       "constNomFr": "Energie, Règlement UE N° 1169/2011 (kcal/100 g)",
 *       "constNomEng": "Energy, Regulation EU No 1169/2011 (kcal/100g)",
 *       "compoCodeConfiance": "D"
 *     },
 *     {
 *       "compoTeneur": "8.7",
 *       "constNomFr": "Protéines (g/100 g)",
 *       "constNomEng": "Protein (g/100g)",
 *       "compoCodeConfiance": "B"
 *     },
 *     ...
 *   ]
 * }
 * ```
 *
 * FIELD DESCRIPTIONS:
 * ===================
 * - compoTeneur: The actual value (always a string, can be numeric or "-")
 * - constNomFr/Eng: Nutrient name with unit (e.g., "Protéines (g/100 g)")
 * - compoCodeConfiance: Quality code (A=excellent, B=good, C=fair, D=estimated)
 * - compoRang: Sort order for display
 * - compoSourceCode: Source of the data
 *
 * PARSING STRATEGY:
 * =================
 * To extract nutrients, we match constNomFr/constNomEng against known patterns:
 * - "Energie.*kcal" → energy in kcal
 * - "Energie.*kJ" → energy in kJ
 * - "Protéines" / "Protein" → proteins
 * - "Glucides" / "Carbohydrate" → carbohydrates
 * - "Lipides" / "Fat" → fats
 * - etc.
 *
 * CONFIDENCE CODES:
 * =================
 * - A: Measured value from direct analysis
 * - B: Measured but with some uncertainty
 * - C: Calculated/estimated with good confidence
 * - D: Estimated/imputed with lower confidence
 *
 * EXAMPLE ENTRY:
 * ==============
 * ```json
 * {
 *   "compoTeneur": "492",
 *   "compoMin": "",
 *   "compoMax": "",
 *   "compoCodeConfiance": "D",
 *   "compoRang": 199,
 *   "compoSourceCode": "444",
 *   "compoSourceLibelles": ["Valeur ajustée/calculée/imputée Ciqual"],
 *   "constNomFr": "Energie, Règlement UE N° 1169/2011 (kcal/100 g)",
 *   "constNomEng": "Energy, Regulation EU No 1169/2011 (kcal/100g)",
 *   "constGrpCode": "1.1",
 *   "constOrdre": 1,
 *   "constInco": "1"
 * }
 * ```
 *
 * This represents: Energy = 492 kcal/100g (estimated value)
 *
 * @see CiqualElasticsearchFood
 * @see li.masciul.sugardaddi.data.sources.ciqual.mappers.CiqualElasticsearchMapper
 * @author SugarDaddi Team
 * @version 1.0
 */
public class CiqualElasticsearchComposition {

    // ========== VALUE FIELDS ==========

    /**
     * Nutrient value (always a string)
     * Examples: "492", "8.7", "54", "-", ""
     *
     * Important: This is ALWAYS a string in the API, even for numbers.
     * Use getValueAsDouble() to safely parse as a number.
     * A value of "-" or "" means "not available" or "trace amounts"
     */
    @SerializedName("compoTeneur")
    private String compoTeneur;

    /**
     * Minimum value (if range available)
     * Usually empty string ""
     */
    @SerializedName("compoMin")
    private String compoMin;

    /**
     * Maximum value (if range available)
     * Usually empty string ""
     */
    @SerializedName("compoMax")
    private String compoMax;

    // ========== QUALITY INDICATORS ==========

    /**
     * Confidence/Quality code
     * A = Measured (excellent)
     * B = Measured (good)
     * C = Calculated (fair)
     * D = Estimated (lower confidence)
     */
    @SerializedName("compoCodeConfiance")
    private String compoCodeConfiance;

    /**
     * Display rank/order
     * Used for sorting nutrients in UI
     * Lower numbers = higher priority
     */
    @SerializedName("compoRang")
    private Integer compoRang;

    // ========== SOURCE METADATA ==========

    /**
     * Source code (where the data comes from)
     * Example: "444" = Ciqual calculated/adjusted value
     */
    @SerializedName("compoSourceCode")
    private String compoSourceCode;

    /**
     * Source labels (human-readable descriptions)
     * Example: ["Valeur ajustée/calculée/imputée Ciqual"]
     */
    @SerializedName("compoSourceLibelles")
    private List<String> compoSourceLibelles;

    // ========== CONSTITUENT (NUTRIENT) IDENTIFICATION ==========

    /**
     * Constituent name in French (with unit)
     * Examples:
     * - "Energie, Règlement UE N° 1169/2011 (kcal/100 g)"
     * - "Protéines (g/100 g)"
     * - "Glucides (g/100 g)"
     * - "AG saturés (g/100 g)"
     *
     * This is the PRIMARY field for identifying which nutrient this is
     */
    @SerializedName("constNomFr")
    private String constNomFr;

    /**
     * Constituent name in English (with unit)
     * Examples:
     * - "Energy, Regulation EU No 1169/2011 (kcal/100g)"
     * - "Protein (g/100g)"
     * - "Carbohydrate (g/100g)"
     * - "Saturated FA (g/100g)"
     *
     * Useful for English language support
     */
    @SerializedName("constNomEng")
    private String constNomEng;

    /**
     * Constituent group code
     * Organizes nutrients into categories
     * Example: "1.1" = Energy group
     */
    @SerializedName("constGrpCode")
    private String constGrpCode;

    /**
     * Constituent display order
     * Used for sorting within a group
     */
    @SerializedName("constOrdre")
    private Integer constOrdre;

    /**
     * Constituent INCO flag (unclear purpose)
     * Possibly related to mandatory declaration requirements
     */
    @SerializedName("constInco")
    private String constInco;

    // ========== GETTERS ==========

    public String getCompoTeneur() { return compoTeneur; }
    public String getCompoMin() { return compoMin; }
    public String getCompoMax() { return compoMax; }
    public String getCompoCodeConfiance() { return compoCodeConfiance; }
    public Integer getCompoRang() { return compoRang; }
    public String getCompoSourceCode() { return compoSourceCode; }
    public List<String> getCompoSourceLibelles() { return compoSourceLibelles; }
    public String getConstNomFr() { return constNomFr; }
    public String getConstNomEng() { return constNomEng; }
    public String getConstGrpCode() { return constGrpCode; }
    public Integer getConstOrdre() { return constOrdre; }
    public String getConstInco() { return constInco; }

    // ========== CONVENIENCE METHODS ==========

    /**
     * Safely parses the value as a Double
     *
     * Returns null if:
     * - Value is null or empty string
     * - Value is "-" (means not available)
     * - Value cannot be parsed as a number
     *
     * @return Numeric value, or null if not available/parseable
     */
    public Double getValueAsDouble() {
        if (compoTeneur == null || compoTeneur.isEmpty() || "-".equals(compoTeneur)) {
            return null;
        }

        try {
            // Handle French decimal separator (comma)
            String normalized = compoTeneur.replace(',', '.');
            return Double.parseDouble(normalized);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Checks if this composition has a valid numeric value
     *
     * @return true if value can be parsed as a number
     */
    public boolean hasValue() {
        return getValueAsDouble() != null;
    }

    /**
     * Gets the nutrient name in the specified language
     *
     * @param language "fr" or "en"
     * @return Nutrient name with unit
     */
    public String getName(String language) {
        if ("en".equalsIgnoreCase(language) && constNomEng != null && !constNomEng.isEmpty()) {
            return constNomEng;
        }
        return constNomFr != null ? constNomFr : "";
    }

    /**
     * Checks if this is a high-quality measurement
     *
     * @return true if confidence code is A or B
     */
    public boolean isHighQuality() {
        return "A".equalsIgnoreCase(compoCodeConfiance)
                || "B".equalsIgnoreCase(compoCodeConfiance);
    }

    /**
     * Checks if this value is estimated/calculated
     *
     * @return true if confidence code is C or D
     */
    public boolean isEstimated() {
        return "C".equalsIgnoreCase(compoCodeConfiance)
                || "D".equalsIgnoreCase(compoCodeConfiance);
    }

    // ========== DEBUG ==========

    @Override
    public String toString() {
        return String.format(
                "CiqualElasticsearchComposition[name=%s, value=%s, confidence=%s]",
                constNomFr != null ? constNomFr : constNomEng,
                compoTeneur,
                compoCodeConfiance
        );
    }
}