package li.masciul.sugardaddi.data.sources.openfoodfacts.api.dto;

import com.google.gson.annotations.SerializedName;

/**
 * OpenFoodFactsNutriments - COMPLETE OpenFoodFacts nutriments model
 *
 * FIXED v2.0 - Handles both per-100g and per-serving data
 *
 * Maps ALL nutrition fields from OFF API to our Nutrition domain model.
 * Supports BOTH data formats:
 * - nutrition_data_per: "100g" → uses "nutrient_100g" fields
 * - nutrition_data_per: "serving" → uses "nutrient" fields (no suffix)
 *
 * Field naming: OFF uses "nutrient_100g" format (e.g., "energy-kcal_100g")
 * BUT some products only have "nutrient" (per serving)
 */
public class OpenFoodFactsNutriments {

    // ========== ENERGY (both formats) ==========
    @SerializedName("energy-kj_100g")
    private Double energyKj100g;

    @SerializedName("energy-kj")
    private Double energyKj;  // Per serving fallback

    @SerializedName("energy-kcal_100g")
    private Double energyKcal100g;

    @SerializedName("energy-kcal")
    private Double energyKcal;  // Per serving fallback

    @SerializedName("energy_100g")
    private Double energy100g;  // Generic fallback

    @SerializedName("energy")
    private Double energy;  // Generic per serving

    // ========== MACRONUTRIENTS - BASIC (both formats) ==========
    @SerializedName("proteins_100g")
    private Double proteins100g;

    @SerializedName("proteins")
    private Double proteins;  // Per serving fallback

    @SerializedName("carbohydrates_100g")
    private Double carbohydrates100g;

    @SerializedName("carbohydrates")
    private Double carbohydrates;  // Per serving fallback

    @SerializedName("fat_100g")
    private Double fat100g;

    @SerializedName("fat")
    private Double fat;  // Per serving fallback

    @SerializedName("fiber_100g")
    private Double fiber100g;

    @SerializedName("fiber")
    private Double fiber;  // Per serving fallback

    @SerializedName("alcohol_100g")
    private Double alcohol100g;

    @SerializedName("water_100g")
    private Double water100g;

    // ========== CARBOHYDRATE DETAILS (both formats) ==========
    @SerializedName("sugars_100g")
    private Double sugars100g;

    @SerializedName("sugars")
    private Double sugars;  // Per serving fallback

    @SerializedName("starch_100g")
    private Double starch100g;

    @SerializedName("polyols_100g")
    private Double polyols100g;

    // Specific sugars
    @SerializedName("glucose_100g")
    private Double glucose100g;

    @SerializedName("fructose_100g")
    private Double fructose100g;

    @SerializedName("sucrose_100g")
    private Double sucrose100g;

    @SerializedName("lactose_100g")
    private Double lactose100g;

    @SerializedName("maltose_100g")
    private Double maltose100g;

    @SerializedName("galactose_100g")
    private Double galactose100g;

    // ========== FAT DETAILS (both formats) ==========
    @SerializedName("saturated-fat_100g")
    private Double saturatedFat100g;

    @SerializedName("saturated-fat")
    private Double saturatedFat;  // Per serving fallback

    @SerializedName("monounsaturated-fat_100g")
    private Double monounsaturatedFat100g;

    @SerializedName("polyunsaturated-fat_100g")
    private Double polyunsaturatedFat100g;

    @SerializedName("trans-fat_100g")
    private Double transFat100g;

    @SerializedName("trans-fat")
    private Double transFat;  // Per serving fallback

    @SerializedName("cholesterol_100g")
    private Double cholesterol100g;

    @SerializedName("cholesterol")
    private Double cholesterol;  // Per serving fallback

    // Omega fatty acids
    @SerializedName("omega-3-fat_100g")
    private Double omega3Fat100g;

    @SerializedName("omega-6-fat_100g")
    private Double omega6Fat100g;

    @SerializedName("omega-9-fat_100g")
    private Double omega9Fat100g;

    // Specific omega-3s
    @SerializedName("alpha-linolenic-acid_100g")
    private Double alphaLinolenicAcid100g;

    @SerializedName("eicosapentaenoic-acid_100g")
    private Double eicosapentaenoicAcid100g;

    @SerializedName("docosahexaenoic-acid_100g")
    private Double docosahexaenoicAcid100g;

    // Other fatty acids
    @SerializedName("linoleic-acid_100g")
    private Double linoleicAcid100g;

    @SerializedName("arachidonic-acid_100g")
    private Double arachidonicAcid100g;

    @SerializedName("gamma-linolenic-acid_100g")
    private Double gammaLinolenicAcid100g;

    // ========== MINERALS - MAJOR (both formats) ==========
    @SerializedName("salt_100g")
    private Double salt100g;

    @SerializedName("salt")
    private Double salt;  // Per serving fallback

    @SerializedName("sodium_100g")
    private Double sodium100g;

    @SerializedName("sodium")
    private Double sodium;  // Per serving fallback

    @SerializedName("calcium_100g")
    private Double calcium100g;

    @SerializedName("phosphorus_100g")
    private Double phosphorus100g;

    @SerializedName("magnesium_100g")
    private Double magnesium100g;

    @SerializedName("potassium_100g")
    private Double potassium100g;

    @SerializedName("chloride_100g")
    private Double chloride100g;

    // ========== MINERALS - TRACE ==========
    @SerializedName("iron_100g")
    private Double iron100g;

    @SerializedName("zinc_100g")
    private Double zinc100g;

    @SerializedName("copper_100g")
    private Double copper100g;

    @SerializedName("manganese_100g")
    private Double manganese100g;

    @SerializedName("selenium_100g")
    private Double selenium100g;

    @SerializedName("iodine_100g")
    private Double iodine100g;

    @SerializedName("chromium_100g")
    private Double chromium100g;

    @SerializedName("molybdenum_100g")
    private Double molybdenum100g;

    @SerializedName("fluoride_100g")
    private Double fluoride100g;

    // ========== VITAMINS ==========
    @SerializedName("vitamin-a_100g")
    private Double vitaminA100g;

    @SerializedName("vitamin-d_100g")
    private Double vitaminD100g;

    @SerializedName("vitamin-e_100g")
    private Double vitaminE100g;

    @SerializedName("vitamin-k_100g")
    private Double vitaminK100g;

    @SerializedName("vitamin-c_100g")
    private Double vitaminC100g;

    @SerializedName("vitamin-b1_100g")
    private Double vitaminB1100g;

    @SerializedName("vitamin-b2_100g")
    private Double vitaminB2100g;

    @SerializedName("vitamin-b3_100g")
    private Double vitaminB3100g;

    @SerializedName("vitamin-b5_100g")
    private Double vitaminB5100g;

    @SerializedName("vitamin-b6_100g")
    private Double vitaminB6100g;

    @SerializedName("vitamin-b9_100g")
    private Double vitaminB9100g;

    @SerializedName("vitamin-b12_100g")
    private Double vitaminB12100g;

    @SerializedName("biotin_100g")
    private Double biotin100g;

    @SerializedName("beta-carotene_100g")
    private Double betaCarotene100g;

    // ========== OTHER COMPOUNDS ==========
    @SerializedName("caffeine_100g")
    private Double caffeine100g;

    @SerializedName("taurine_100g")
    private Double taurine100g;

    // ========== PREPARED VALUES (powder/concentrate products) ==========
    // Some products (e.g. cocoa powder, infant formula) only carry "_prepared_100g"
    // values representing the reconstituted product. Used as last-resort fallback.
    @SerializedName("energy-kj_prepared_100g")
    private Double energyKjPrepared100g;

    @SerializedName("energy-kcal_prepared_100g")
    private Double energyKcalPrepared100g;

    @SerializedName("energy_prepared_100g")
    private Double energyPrepared100g;

    @SerializedName("proteins_prepared_100g")
    private Double proteinsPrepared100g;

    @SerializedName("carbohydrates_prepared_100g")
    private Double carbohydratesPrepared100g;

    @SerializedName("fat_prepared_100g")
    private Double fatPrepared100g;

    @SerializedName("fiber_prepared_100g")
    private Double fiberPrepared100g;

    @SerializedName("sugars_prepared_100g")
    private Double sugarsPrepared100g;

    @SerializedName("saturated-fat_prepared_100g")
    private Double saturatedFatPrepared100g;

    @SerializedName("salt_prepared_100g")
    private Double saltPrepared100g;

    @SerializedName("sodium_prepared_100g")
    private Double sodiumPrepared100g;

    // ========== GETTERS WITH FALLBACK LOGIC ==========

    /**
     * Get energy in kJ per 100g
     * Falls back to per-serving value if 100g not available
     */
    public Double getEnergyKj100g() {
        if (energyKj100g != null) return energyKj100g;
        if (energyKj != null) return energyKj;
        if (energy100g != null) return energy100g;
        return energyKjPrepared100g;
    }

    /**
     * Get energy in kcal per 100g
     * Falls back to per-serving value if 100g not available
     */
    public Double getEnergyKcal100g() {
        if (energyKcal100g != null) return energyKcal100g;
        if (energyKcal != null) return energyKcal;
        return energyKcalPrepared100g;
    }

    public Double getEnergy100g() {
        if (energy100g != null) return energy100g;
        return energy;
    }

    // ========== GETTERS - MACRONUTRIENTS WITH FALLBACK ==========

    public Double getProteins100g() {
        if (proteins100g != null) return proteins100g;
        if (proteins != null) return proteins;
        return proteinsPrepared100g;
    }

    public Double getCarbohydrates100g() {
        if (carbohydrates100g != null) return carbohydrates100g;
        if (carbohydrates != null) return carbohydrates;
        return carbohydratesPrepared100g;
    }

    public Double getFat100g() {
        if (fat100g != null) return fat100g;
        if (fat != null) return fat;
        return fatPrepared100g;
    }

    public Double getFiber100g() {
        if (fiber100g != null) return fiber100g;
        if (fiber != null) return fiber;
        return fiberPrepared100g;
    }

    public Double getAlcohol100g() { return alcohol100g; }
    public Double getWater100g() { return water100g; }

    // ========== GETTERS - CARBOHYDRATES WITH FALLBACK ==========

    public Double getSugars100g() {
        if (sugars100g != null) return sugars100g;
        if (sugars != null) return sugars;
        return sugarsPrepared100g;
    }

    public Double getStarch100g() { return starch100g; }
    public Double getPolyols100g() { return polyols100g; }
    public Double getGlucose100g() { return glucose100g; }
    public Double getFructose100g() { return fructose100g; }
    public Double getSucrose100g() { return sucrose100g; }
    public Double getLactose100g() { return lactose100g; }
    public Double getMaltose100g() { return maltose100g; }
    public Double getGalactose100g() { return galactose100g; }

    // ========== GETTERS - FATS WITH FALLBACK ==========

    public Double getSaturatedFat100g() {
        if (saturatedFat100g != null) return saturatedFat100g;
        if (saturatedFat != null) return saturatedFat;
        return saturatedFatPrepared100g;
    }

    public Double getMonounsaturatedFat100g() { return monounsaturatedFat100g; }
    public Double getPolyunsaturatedFat100g() { return polyunsaturatedFat100g; }

    public Double getTransFat100g() {
        if (transFat100g != null) return transFat100g;
        return transFat;
    }

    public Double getCholesterol100g() {
        if (cholesterol100g != null) return cholesterol100g;
        return cholesterol;
    }

    public Double getOmega3Fat100g() { return omega3Fat100g; }
    public Double getOmega6Fat100g() { return omega6Fat100g; }
    public Double getOmega9Fat100g() { return omega9Fat100g; }
    public Double getAlphaLinolenicAcid100g() { return alphaLinolenicAcid100g; }
    public Double getEicosapentaenoicAcid100g() { return eicosapentaenoicAcid100g; }
    public Double getDocosahexaenoicAcid100g() { return docosahexaenoicAcid100g; }
    public Double getLinoleicAcid100g() { return linoleicAcid100g; }
    public Double getArachidonicAcid100g() { return arachidonicAcid100g; }
    public Double getGammaLinolenicAcid100g() { return gammaLinolenicAcid100g; }

    // ========== GETTERS - MINERALS WITH FALLBACK ==========

    public Double getSalt100g() {
        if (salt100g != null) return salt100g;
        if (salt != null) return salt;
        return saltPrepared100g;
    }

    public Double getSodium100g() {
        if (sodium100g != null) return sodium100g;
        if (sodium != null) return sodium;
        return sodiumPrepared100g;
    }

    public Double getCalcium100g() { return calcium100g; }
    public Double getPhosphorus100g() { return phosphorus100g; }
    public Double getMagnesium100g() { return magnesium100g; }
    public Double getPotassium100g() { return potassium100g; }
    public Double getChloride100g() { return chloride100g; }
    public Double getIron100g() { return iron100g; }
    public Double getZinc100g() { return zinc100g; }
    public Double getCopper100g() { return copper100g; }
    public Double getManganese100g() { return manganese100g; }
    public Double getSelenium100g() { return selenium100g; }
    public Double getIodine100g() { return iodine100g; }
    public Double getChromium100g() { return chromium100g; }
    public Double getMolybdenum100g() { return molybdenum100g; }
    public Double getFluoride100g() { return fluoride100g; }

    // ========== GETTERS - VITAMINS ==========
    public Double getVitaminA100g() { return vitaminA100g; }
    public Double getVitaminD100g() { return vitaminD100g; }
    public Double getVitaminE100g() { return vitaminE100g; }
    public Double getVitaminK100g() { return vitaminK100g; }
    public Double getVitaminC100g() { return vitaminC100g; }
    public Double getVitaminB1100g() { return vitaminB1100g; }
    public Double getVitaminB2100g() { return vitaminB2100g; }
    public Double getVitaminB3100g() { return vitaminB3100g; }
    public Double getVitaminB5100g() { return vitaminB5100g; }
    public Double getVitaminB6100g() { return vitaminB6100g; }
    public Double getVitaminB9100g() { return vitaminB9100g; }
    public Double getVitaminB12100g() { return vitaminB12100g; }
    public Double getBiotin100g() { return biotin100g; }
    public Double getBetaCarotene100g() { return betaCarotene100g; }

    // ========== GETTERS - OTHER ==========
    public Double getCaffeine100g() { return caffeine100g; }
    public Double getTaurine100g() { return taurine100g; }
}