package li.masciul.sugardaddi.data.sources.usda.api.dto;

import androidx.annotation.Nullable;
import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;

/**
 * FDCFoodDetail — Full food detail from GET /food/{fdcId}?format=full
 *
 * This response contains the complete nutrient profile for a single food,
 * compared to FDCSearchFood which only has a subset of key nutrients.
 *
 * STRUCTURE:
 * {
 *   "fdcId": 747447,
 *   "description": "Broccoli, raw",
 *   "dataType": "Foundation",
 *   "publicationDate": "2019-04-01",
 *   "foodCategory": { "description": "Vegetables and Vegetable Products" },
 *   "foodNutrients": [
 *     {
 *       "nutrient": { "id": 1008, "name": "Energy", "number": "208", "unitName": "kcal" },
 *       "amount": 34.0
 *     },
 *     ...
 *   ]
 * }
 *
 * NOTE: publicationDate vs publishedDate — the detail endpoint uses "publicationDate",
 * the search endpoint uses "publishedDate". Different field names, same concept.
 */
public class FDCFoodDetail {

    @SerializedName("fdcId")
    private int fdcId;

    @SerializedName("description")
    @Nullable
    private String description;

    @SerializedName("dataType")
    @Nullable
    private String dataType;

    @SerializedName("publicationDate")
    @Nullable
    private String publicationDate;

    @SerializedName("foodCategory")
    @Nullable
    private FDCFoodCategory foodCategory;

    @SerializedName("foodNutrients")
    @Nullable
    private List<FDCFoodNutrient> foodNutrients;

    // ===== CONSTRUCTORS =====

    public FDCFoodDetail() {
        this.foodNutrients = new ArrayList<>();
    }

    // ===== ACCESSORS =====

    public int getFdcId()                        { return fdcId; }
    @Nullable public String getDescription()     { return description; }
    @Nullable public String getDataType()        { return dataType; }
    @Nullable public String getPublicationDate() { return publicationDate; }

    @Nullable
    public String getFoodCategoryDescription() {
        return foodCategory != null ? foodCategory.getDescription() : null;
    }

    public List<FDCFoodNutrient> getFoodNutrients() {
        return foodNutrients != null ? foodNutrients : new ArrayList<>();
    }

    public boolean isValid() {
        return fdcId > 0 && description != null && !description.trim().isEmpty();
    }

    public String getSearchableId() {
        return "USDA:" + fdcId;
    }

    /**
     * Find a nutrient by its FDC nutrient ID.
     * Common IDs: 1008=Energy(kcal), 2047=Energy(kJ), 1003=Protein,
     * 1004=Total fat, 1005=Carbohydrates, 1079=Fiber, 1093=Sodium,
     * 1258=Saturated fat, 1292=Monounsaturated fat, 2000=Sugars
     */
    @Nullable
    public FDCFoodNutrient getNutrientById(int nutrientId) {
        for (FDCFoodNutrient n : getFoodNutrients()) {
            if (n.getNutrientId() == nutrientId) return n;
        }
        return null;
    }

    /** Convenience accessor — energy in kcal (nutrient 1008) */
    @Nullable
    public Double getEnergyKcal() {
        FDCFoodNutrient n = getNutrientById(1008);
        return n != null ? n.getAmount() : null;
    }

    /** Convenience accessor — energy in kJ (nutrient 2047) */
    @Nullable
    public Double getEnergyKj() {
        FDCFoodNutrient n = getNutrientById(2047);
        return n != null ? n.getAmount() : null;
    }

    /** Convenience accessor — protein in g (nutrient 1003) */
    @Nullable
    public Double getProtein() {
        FDCFoodNutrient n = getNutrientById(1003);
        return n != null ? n.getAmount() : null;
    }

    /** Convenience accessor — total fat in g (nutrient 1004) */
    @Nullable
    public Double getFat() {
        FDCFoodNutrient n = getNutrientById(1004);
        return n != null ? n.getAmount() : null;
    }

    /** Convenience accessor — carbohydrates in g (nutrient 1005) */
    @Nullable
    public Double getCarbohydrates() {
        FDCFoodNutrient n = getNutrientById(1005);
        return n != null ? n.getAmount() : null;
    }

    /** Convenience accessor — dietary fiber in g (nutrient 1079) */
    @Nullable
    public Double getFiber() {
        FDCFoodNutrient n = getNutrientById(1079);
        return n != null ? n.getAmount() : null;
    }

    /** Convenience accessor — total sugars in g (nutrient 2000) */
    @Nullable
    public Double getSugars() {
        FDCFoodNutrient n = getNutrientById(2000);
        return n != null ? n.getAmount() : null;
    }

    /** Convenience accessor — saturated fat in g (nutrient 1258) */
    @Nullable
    public Double getSaturatedFat() {
        FDCFoodNutrient n = getNutrientById(1258);
        return n != null ? n.getAmount() : null;
    }

    /** Convenience accessor — sodium in mg (nutrient 1093) */
    @Nullable
    public Double getSodium() {
        FDCFoodNutrient n = getNutrientById(1093);
        return n != null ? n.getAmount() : null;
    }

    @Override
    public String toString() {
        return "FDCFoodDetail{fdcId=" + fdcId + ", description='" + description
                + "', nutrients=" + getFoodNutrients().size() + "}";
    }

    // =========================================================================
    // NESTED: FDCFoodCategory
    // =========================================================================

    public static class FDCFoodCategory {

        @SerializedName("description")
        @Nullable
        private String description;

        @Nullable
        public String getDescription() { return description; }
    }

    // =========================================================================
    // NESTED: FDCFoodNutrient
    // =========================================================================

    /**
     * A single nutrient entry in the food detail response.
     *
     * Structure:
     * {
     *   "nutrient": { "id": 1008, "name": "Energy", "number": "208", "unitName": "kcal" },
     *   "amount": 34.0
     * }
     *
     * Note: Foundation Foods also include "min", "max", "median", "dataPoints"
     * for statistical context. We capture amount only — the mean value.
     */
    public static class FDCFoodNutrient {

        @SerializedName("nutrient")
        @Nullable
        private FDCNutrient nutrient;

        @SerializedName("amount")
        private double amount;

        public int getNutrientId() {
            return nutrient != null ? nutrient.getId() : 0;
        }

        @Nullable
        public String getNutrientName() {
            return nutrient != null ? nutrient.getName() : null;
        }

        @Nullable
        public String getUnitName() {
            return nutrient != null ? nutrient.getUnitName() : null;
        }

        public double getAmount() { return amount; }

        @Nullable
        public FDCNutrient getNutrient() { return nutrient; }
    }

    // =========================================================================
    // NESTED: FDCNutrient
    // =========================================================================

    /**
     * Nutrient metadata within a food detail nutrient entry.
     *
     * Structure:
     * { "id": 1008, "name": "Energy", "number": "208", "unitName": "kcal" }
     */
    public static class FDCNutrient {

        @SerializedName("id")
        private int id;

        @SerializedName("name")
        @Nullable
        private String name;

        /** INFOODS tagname/number (e.g. "208" for energy kcal) */
        @SerializedName("number")
        @Nullable
        private String number;

        @SerializedName("unitName")
        @Nullable
        private String unitName;

        public int getId()              { return id; }
        @Nullable public String getName()     { return name; }
        @Nullable public String getNumber()   { return number; }
        @Nullable public String getUnitName() { return unitName; }
    }
}