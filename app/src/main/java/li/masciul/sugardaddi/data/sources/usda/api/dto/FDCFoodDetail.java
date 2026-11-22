package li.masciul.sugardaddi.data.sources.usda.api.dto;

import androidx.annotation.Nullable;
import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;

/**
 * FDCFoodDetail - Full food detail from GET /food/{fdcId}?format=full
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
 * NOTE: publicationDate vs publishedDate - the detail endpoint uses "publicationDate",
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

    @SerializedName("wweiaFoodCategory")
    @Nullable
    private WweiaFoodCategory wweiaFoodCategory;

    @SerializedName("foodNutrients")
    @Nullable
    private List<FDCFoodNutrient> foodNutrients;

    /** Household measures with their gram weight (see USDAPortions). */
    @SerializedName("foodPortions")
    @Nullable
    private List<FDCFoodPortion> foodPortions;

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
        if (foodCategory != null && foodCategory.getDescription() != null) {
            return foodCategory.getDescription();
        }
        // Survey (FNDDS) foods use wweiaFoodCategory instead of foodCategory
        if (wweiaFoodCategory != null) {
            return wweiaFoodCategory.getDescription();
        }
        return null;
    }

    public List<FDCFoodNutrient> getFoodNutrients() {
        return foodNutrients != null ? foodNutrients : new ArrayList<>();
    }

    public List<FDCFoodPortion> getFoodPortions() {
        return foodPortions != null ? foodPortions : new ArrayList<>();
    }

    public boolean isValid() {
        return fdcId > 0 && description != null && !description.trim().isEmpty();
    }

    public String getSearchableId() {
        return "USDA:" + fdcId;
    }

    /**
     * Find a nutrient by its FDC nutrient ID.
     * Common IDs: 1008=Energy(kcal), 1062=Energy(kJ), 2047/2048=Energy(kcal,
     * Atwater general / specific factors), 1003=Protein,
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

    /** Convenience accessor - energy in kcal (nutrient 1008) */
    @Nullable
    public Double getEnergyKcal() {
        FDCFoodNutrient n = getNutrientById(1008);
        return n != null ? n.getAmount() : null;
    }

    /** Convenience accessor - energy in kJ (nutrient 1062; 2047 is kcal) */
    @Nullable
    public Double getEnergyKj() {
        FDCFoodNutrient n = getNutrientById(1062);
        return n != null ? n.getAmount() : null;
    }

    /** Convenience accessor - protein in g (nutrient 1003) */
    @Nullable
    public Double getProtein() {
        FDCFoodNutrient n = getNutrientById(1003);
        return n != null ? n.getAmount() : null;
    }

    /** Convenience accessor - total fat in g (nutrient 1004) */
    @Nullable
    public Double getFat() {
        FDCFoodNutrient n = getNutrientById(1004);
        return n != null ? n.getAmount() : null;
    }

    /** Convenience accessor - carbohydrates in g (nutrient 1005) */
    @Nullable
    public Double getCarbohydrates() {
        FDCFoodNutrient n = getNutrientById(1005);
        return n != null ? n.getAmount() : null;
    }

    /** Convenience accessor - dietary fiber in g (nutrient 1079) */
    @Nullable
    public Double getFiber() {
        FDCFoodNutrient n = getNutrientById(1079);
        return n != null ? n.getAmount() : null;
    }

    /** Convenience accessor - total sugars in g (nutrient 2000) */
    @Nullable
    public Double getSugars() {
        FDCFoodNutrient n = getNutrientById(2000);
        return n != null ? n.getAmount() : null;
    }

    /** Convenience accessor - saturated fat in g (nutrient 1258) */
    @Nullable
    public Double getSaturatedFat() {
        FDCFoodNutrient n = getNutrientById(1258);
        return n != null ? n.getAmount() : null;
    }

    /** Convenience accessor - sodium in mg (nutrient 1093) */
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
    // NESTED: WweiaFoodCategory
    // =========================================================================

    public static class WweiaFoodCategory {
        @SerializedName("wweiaFoodCategoryDescription")
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
     * for statistical context. We capture amount only - the mean value.
     */
    public static class FDCFoodNutrient {

        @SerializedName("nutrient")
        @Nullable
        private FDCNutrient nutrient;

        /** Boxed: group headings ("Proximates") have no amount, which is not 0. */
        @SerializedName("amount")
        @Nullable
        private Double amount;

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

        @Nullable public Double getAmount() { return amount; }

        @Nullable
        public FDCNutrient getNutrient() { return nutrient; }
    }

    // =========================================================================
    // NESTED: FDCFoodPortion
    // =========================================================================

    /**
     * A household measure of the food. Which field holds the words depends on
     * the data type (see USDAPortions for the three formats):
     * {
     *   "gramWeight": 244.0, "amount": 1.0, "sequenceNumber": 1,
     *   "modifier": "cup", "portionDescription": "1 cup",
     *   "measureUnit": { "id": 9999, "name": "undetermined", "abbreviation": "undetermined" }
     * }
     * Boxed types: any field may be absent.
     */
    public static class FDCFoodPortion {

        @SerializedName("gramWeight")
        @Nullable
        private Double gramWeight;

        @SerializedName("amount")
        @Nullable
        private Double amount;

        @SerializedName("sequenceNumber")
        @Nullable
        private Integer sequenceNumber;

        @SerializedName("modifier")
        @Nullable
        private String modifier;

        @SerializedName("portionDescription")
        @Nullable
        private String portionDescription;

        @SerializedName("measureUnit")
        @Nullable
        private FDCMeasureUnit measureUnit;

        @Nullable public Double getGramWeight()           { return gramWeight; }
        @Nullable public Double getAmount()               { return amount; }
        @Nullable public Integer getSequenceNumber()      { return sequenceNumber; }
        @Nullable public String getModifier()             { return modifier; }
        @Nullable public String getPortionDescription()   { return portionDescription; }

        @Nullable
        public String getMeasureUnitName() {
            return measureUnit != null ? measureUnit.name : null;
        }
    }

    /** measureUnit of a portion: only the name is used ("cup", "RACC", "undetermined"). */
    public static class FDCMeasureUnit {
        @SerializedName("name")
        @Nullable
        private String name;
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