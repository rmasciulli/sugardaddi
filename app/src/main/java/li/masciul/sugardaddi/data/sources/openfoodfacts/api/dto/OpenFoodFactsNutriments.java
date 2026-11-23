package li.masciul.sugardaddi.data.sources.openfoodfacts.api.dto;

import com.google.gson.annotations.SerializedName;

/**
 * OpenFoodFactsNutriments - COMPLETE OpenFoodFacts nutriments model
 *
 * Only OFF's "{nutrient}_100g" fields are read. OFF computes them itself, per
 * 100 g / 100 ml even when the label gives values per serving, and in grams for
 * every nutrient measured by weight (kJ for energy, % vol for alcohol). The
 * unsuffixed "{nutrient}" fields are per 100 g OR per serving, as typed: not used.
 *
 * "{nutrient}_prepared_100g" (the product once prepared: cocoa powder made with
 * milk, reconstituted formula) is not read either. The app logs the product as
 * sold, so a prepared value would be wrong for it (about 80 kcal instead of 380
 * for cocoa powder). No as-sold values on OFF means no nutrition in the app.
 */
public class OpenFoodFactsNutriments {

    // ========== ENERGY (both formats) ==========
    @SerializedName("energy-kj_100g")
    private Double energyKj100g;

    @SerializedName("energy-kcal_100g")
    private Double energyKcal100g;

    @SerializedName("energy_100g")
    private Double energy100g;  // Generic fallback

    // ========== MACRONUTRIENTS - BASIC (both formats) ==========
    @SerializedName("proteins_100g")
    private Double proteins100g;

    @SerializedName("carbohydrates_100g")
    private Double carbohydrates100g;

    @SerializedName("fat_100g")
    private Double fat100g;

    @SerializedName("fiber_100g")
    private Double fiber100g;

    @SerializedName("alcohol_100g")
    private Double alcohol100g;

    @SerializedName("water_100g")
    private Double water100g;

    // ========== CARBOHYDRATE DETAILS (both formats) ==========
    @SerializedName("sugars_100g")
    private Double sugars100g;

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

    @SerializedName("monounsaturated-fat_100g")
    private Double monounsaturatedFat100g;

    @SerializedName("polyunsaturated-fat_100g")
    private Double polyunsaturatedFat100g;

    @SerializedName("trans-fat_100g")
    private Double transFat100g;

    @SerializedName("cholesterol_100g")
    private Double cholesterol100g;

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

    @SerializedName("sodium_100g")
    private Double sodium100g;

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

    // ========== GETTERS ==========

    /**
     * Energy in kJ per 100 g. energy_100g is kJ too (OFF: the same as energy-kj,
     * or computed from energy-kcal).
     */
    public Double getEnergyKj100g() {
        if (energyKj100g != null) return energyKj100g;
        return energy100g;
    }

    /** Energy in kcal per 100 g. */
    public Double getEnergyKcal100g() {
        return energyKcal100g;
    }

    // ========== GETTERS - MACRONUTRIENTS ==========

    public Double getProteins100g() {
        return proteins100g;
    }

    public Double getCarbohydrates100g() {
        return carbohydrates100g;
    }

    public Double getFat100g() {
        return fat100g;
    }

    public Double getFiber100g() {
        return fiber100g;
    }

    public Double getAlcohol100g() { return alcohol100g; }
    public Double getWater100g() { return water100g; }

    // ========== GETTERS - CARBOHYDRATES ==========

    public Double getSugars100g() {
        return sugars100g;
    }

    public Double getStarch100g() { return starch100g; }
    public Double getPolyols100g() { return polyols100g; }
    public Double getGlucose100g() { return glucose100g; }
    public Double getFructose100g() { return fructose100g; }
    public Double getSucrose100g() { return sucrose100g; }
    public Double getLactose100g() { return lactose100g; }
    public Double getMaltose100g() { return maltose100g; }
    public Double getGalactose100g() { return galactose100g; }

    // ========== GETTERS - FATS ==========

    public Double getSaturatedFat100g() {
        return saturatedFat100g;
    }

    public Double getMonounsaturatedFat100g() { return monounsaturatedFat100g; }
    public Double getPolyunsaturatedFat100g() { return polyunsaturatedFat100g; }

    public Double getTransFat100g() { return transFat100g; }

    public Double getCholesterol100g() { return cholesterol100g; }

    public Double getOmega3Fat100g() { return omega3Fat100g; }
    public Double getOmega6Fat100g() { return omega6Fat100g; }
    public Double getOmega9Fat100g() { return omega9Fat100g; }
    public Double getAlphaLinolenicAcid100g() { return alphaLinolenicAcid100g; }
    public Double getEicosapentaenoicAcid100g() { return eicosapentaenoicAcid100g; }
    public Double getDocosahexaenoicAcid100g() { return docosahexaenoicAcid100g; }
    public Double getLinoleicAcid100g() { return linoleicAcid100g; }
    public Double getArachidonicAcid100g() { return arachidonicAcid100g; }
    public Double getGammaLinolenicAcid100g() { return gammaLinolenicAcid100g; }

    // ========== GETTERS - MINERALS ==========

    public Double getSalt100g() {
        return salt100g;
    }

    public Double getSodium100g() {
        return sodium100g;
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