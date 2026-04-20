package li.masciul.sugardaddi.data.sources.usda.api.dto;

import androidx.annotation.Nullable;
import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;

/**
 * FDCSearchFood — One food item returned by GET /foods/search.
 *
 * IMPORTANT: Search results include only a SUBSET of nutrients.
 * The set varies but usually covers energy, protein, fat, carbohydrates.
 * For a complete nutrient profile, call GET /food/{fdcId} → FDCFoodDetail.
 *
 * FIELD NOTES:
 * - fdcId       — stable integer identifier, used for detail lookups
 * - description — food name (English only, USDA database is en-only)
 * - dataType    — "Foundation", "SR Legacy", "Survey (FNDDS)", or "Branded"
 * - score       — relevance score from FDC Elasticsearch, higher = more relevant
 * - foodNutrients — inline nutrient summary (not exhaustive)
 */
public class FDCSearchFood {

    @SerializedName("fdcId")
    private int fdcId;

    @SerializedName("description")
    @Nullable
    private String description;

    @SerializedName("dataType")
    @Nullable
    private String dataType;

    @SerializedName("foodCategory")
    @Nullable
    private String foodCategory;

    @SerializedName("publishedDate")
    @Nullable
    private String publishedDate;

    /** Elasticsearch relevance score — higher is better */
    @SerializedName("score")
    private double score;

    /** Inline nutrient summary (partial — not all nutrients) */
    @SerializedName("foodNutrients")
    @Nullable
    private List<FDCSearchNutrient> foodNutrients;

    // ===== CONSTRUCTORS =====

    public FDCSearchFood() {
        this.foodNutrients = new ArrayList<>();
    }

    // ===== ACCESSORS =====

    public int getFdcId()            { return fdcId; }
    @Nullable public String getDescription()  { return description; }
    @Nullable public String getDataType()     { return dataType; }
    @Nullable public String getFoodCategory() { return foodCategory; }
    @Nullable public String getPublishedDate(){ return publishedDate; }
    public double getScore()                  { return score; }

    public List<FDCSearchNutrient> getFoodNutrients() {
        return foodNutrients != null ? foodNutrients : new ArrayList<>();
    }

    /**
     * Find a specific nutrient by its FDC nutrient ID.
     * Common IDs: 1008=Energy(kcal), 1003=Protein, 1004=Fat, 1005=Carbs,
     *             1079=Fiber, 1093=Sodium, 2047=Energy(kJ)
     *
     * @param nutrientId FDC nutrient identifier
     * @return Matching nutrient entry, or null if not present in this response
     */
    @Nullable
    public FDCSearchNutrient getNutrientById(int nutrientId) {
        for (FDCSearchNutrient n : getFoodNutrients()) {
            if (n.getNutrientId() == nutrientId) return n;
        }
        return null;
    }

    /**
     * Convenience: energy in kcal, or null if not in the search response.
     * Nutrient ID 1008 = Energy, KCAL.
     */
    @Nullable
    public Double getEnergyKcal() {
        FDCSearchNutrient n = getNutrientById(1008);
        return n != null ? n.getValue() : null;
    }

    /** Nutrient ID 1003 = Protein, G */
    @Nullable
    public Double getProtein() {
        FDCSearchNutrient n = getNutrientById(1003);
        return n != null ? n.getValue() : null;
    }

    /** Nutrient ID 1004 = Total lipid (fat), G */
    @Nullable
    public Double getFat() {
        FDCSearchNutrient n = getNutrientById(1004);
        return n != null ? n.getValue() : null;
    }

    /** Nutrient ID 1005 = Carbohydrate by difference, G */
    @Nullable
    public Double getCarbohydrates() {
        FDCSearchNutrient n = getNutrientById(1005);
        return n != null ? n.getValue() : null;
    }

    /** Searchable ID used to look up this food in Room or via the detail API. */
    public String getSearchableId() {
        return "USDA:" + fdcId;
    }

    @Override
    public String toString() {
        return "FDCSearchFood{fdcId=" + fdcId + ", description='" + description
                + "', dataType='" + dataType + "'}";
    }

    // =========================================================================
    // NESTED: FDCSearchNutrient
    // =========================================================================

    /**
     * Inline nutrient entry in a search result food.
     *
     * Structure (from API):
     * {
     *   "nutrientId":     1008,
     *   "nutrientName":   "Energy",
     *   "nutrientNumber": "208",
     *   "unitName":       "KCAL",
     *   "value":          34.0
     * }
     *
     * NOTE: This differs from FDCFoodNutrient in the detail response,
     * which wraps the nutrient info in a nested "nutrient" object.
     */
    public static class FDCSearchNutrient {

        @SerializedName("nutrientId")
        private int nutrientId;

        @SerializedName("nutrientName")
        @Nullable
        private String nutrientName;

        @SerializedName("nutrientNumber")
        @Nullable
        private String nutrientNumber;

        @SerializedName("unitName")
        @Nullable
        private String unitName;

        @SerializedName("value")
        private double value;

        public int getNutrientId()        { return nutrientId; }
        @Nullable public String getNutrientName()   { return nutrientName; }
        @Nullable public String getNutrientNumber() { return nutrientNumber; }
        @Nullable public String getUnitName()       { return unitName; }
        public double getValue()          { return value; }
    }
}