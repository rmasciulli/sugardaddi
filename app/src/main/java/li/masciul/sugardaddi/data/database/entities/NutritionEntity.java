package li.masciul.sugardaddi.data.database.entities;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import li.masciul.sugardaddi.core.enums.DataConfidence;
import li.masciul.sugardaddi.core.models.Nutrition;

/**
 * NutritionEntity - Centralized nutrition storage
 *
 * DESIGN PHILOSOPHY:
 * - Single nutrition table for ALL entities (FoodProduct, Recipe, Meal)
 * - All values are per 100g/100ml for consistency
 * - NULL means "unknown", 0.0 means "confirmed zero"
 * - Optimized indexes for common queries
 */
@Entity(
        tableName = "nutrition",
        indices = {
                @Index(value = {"sourceType", "sourceId"}, unique = true),
                @Index(value = {"category"}),
                @Index(value = {"energyKcal"}),
                @Index(value = {"proteins"}),
                @Index(value = {"carbohydrates"}),
                @Index(value = {"fat"}),
                @Index(value = {"sugars"}),
                @Index(value = {"salt"}),
                @Index(value = {"fiber"}),
                @Index(value = {"dataCompleteness"}),
                @Index(value = {"lastUpdated"}),
                @Index(value = {"dataConfidence"})
        }
)
public class NutritionEntity {

    // ========== PRIMARY KEY ==========
    @PrimaryKey
    @NonNull
    private String nutritionId = "";  // Format: "sourceType:sourceId" e.g., "product:OFF:3017620422003"

    // ========== SOURCE IDENTIFICATION ==========
    @NonNull
    private String sourceType = "";     // "product", "recipe", "meal"
    @NonNull
    private String sourceId = "";       // The ID of the source entity
    private String dataSource;          // "OFF", "ciqual", "user", etc.

    // ========== CATEGORIZATION (for analytics) ==========
    private String category;            // "dairy", "fruits", etc.
    private String subcategory;         // "milk", "apples", etc.

    // ========== MEASUREMENT BASIS ==========
    private String measurementBasis = "per_100g";  // or "per_100ml", "per_serving"
    private boolean isLiquid = false;

    // ========== ENERGY ==========
    private Double energyKj;        // Kilojoules per 100g/ml
    private Double energyKcal;      // Kilocalories per 100g/ml

    // ========== BASIC MACRONUTRIENTS ==========
    private Double proteins;        // g per 100g/ml
    private Double carbohydrates;   // g per 100g/ml - total carbs
    private Double fat;             // g per 100g/ml - total fat
    private Double fiber;           // g per 100g/ml - dietary fiber
    private Double alcohol;         // g per 100g/ml
    private Double water;           // g per 100g/ml
    private Double ash;             // g per 100g/ml - mineral content

    // ========== CARBOHYDRATE BREAKDOWN ==========
    private Double sugars;          // g per 100g/ml - part of carbs
    private Double starch;          // g per 100g/ml - part of carbs
    private Double polyols;         // g per 100g/ml - sugar alcohols

    // Specific sugars
    private Double glucose;         // g per 100g/ml
    private Double fructose;        // g per 100g/ml
    private Double sucrose;         // g per 100g/ml
    private Double lactose;         // g per 100g/ml
    private Double maltose;         // g per 100g/ml
    private Double galactose;       // g per 100g/ml

    // ========== FATS - DETAILED BREAKDOWN ==========

    // Basic fat categories
    private Double saturatedFat;           // g per 100g/ml
    private Double monounsaturatedFat;     // g per 100g/ml
    private Double polyunsaturatedFat;     // g per 100g/ml
    private Double transFat;               // g per 100g/ml

    // Essential fatty acids
    private Double omega3;                 // g per 100g/ml - total omega-3
    private Double omega6;                 // g per 100g/ml - total omega-6
    private Double omega9;                 // g per 100g/ml - total omega-9
    private Double linoleicAcid;           // g per 100g/ml

    // Specific omega-3 fatty acids
    private Double dha;                    // mg per 100g/ml - Docosahexaenoic acid
    private Double epa;                    // mg per 100g/ml - Eicosapentaenoic acid
    private Double ala;                    // mg per 100g/ml - Alpha-linolenic acid

    // Other important fatty acids
    private Double arachidonicAcid;        // mg per 100g/ml
    private Double gammaLinolenicAcid;     // mg per 100g/ml
    private Double conjugatedLinoleicAcid; // mg per 100g/ml

    // Specific saturated fatty acids
    private Double butyricAcid;            // g per 100g/ml (C4:0)
    private Double caproicAcid;            // g per 100g/ml (C6:0)
    private Double caprylicAcid;           // g per 100g/ml (C8:0)
    private Double capricAcid;             // g per 100g/ml (C10:0)
    private Double lauricAcid;             // g per 100g/ml (C12:0)
    private Double myristicAcid;           // g per 100g/ml (C14:0)
    private Double palmiticAcid;           // g per 100g/ml (C16:0)
    private Double stearicAcid;            // g per 100g/ml (C18:0)

    private Double cholesterol;     // mg per 100g/ml

    // ========== VITAMINS ==========

    // Fat-soluble vitamins
    private Double vitaminA;        // µg per 100g/ml - Retinol equivalent
    private Double retinol;         // µg per 100g/ml
    private Double betaCarotene;    // µg per 100g/ml
    private Double vitaminD;        // µg per 100g/ml
    private Double vitaminE;        // mg per 100g/ml - Alpha-tocopherol
    private Double vitaminK;        // µg per 100g/ml
    private Double vitaminK1;       // µg per 100g/ml - Phylloquinone
    private Double vitaminK2;       // µg per 100g/ml - Menaquinone

    // Water-soluble vitamins (B-complex)
    private Double vitaminB1;       // mg per 100g/ml - Thiamine
    private Double vitaminB2;       // mg per 100g/ml - Riboflavin
    private Double vitaminB3;       // mg per 100g/ml - Niacin
    private Double vitaminB5;       // mg per 100g/ml - Pantothenic acid
    private Double vitaminB6;       // mg per 100g/ml - Pyridoxine
    private Double vitaminB7;       // µg per 100g/ml - Biotin
    private Double vitaminB9;       // µg per 100g/ml - Folate
    private Double folate;          // µg per 100g/ml - Natural folate (alternative name)
    private Double vitaminB12;      // µg per 100g/ml - Cobalamin

    // Other water-soluble vitamins
    private Double vitaminC;        // mg per 100g/ml - Ascorbic acid
    private Double choline;         // mg per 100g/ml

    // ========== AMINO ACIDS ==========

    // Essential amino acids
    private Double tryptophan;      // mg per 100g/ml
    private Double threonine;       // mg per 100g/ml
    private Double isoleucine;      // mg per 100g/ml
    private Double leucine;         // mg per 100g/ml
    private Double lysine;          // mg per 100g/ml
    private Double methionine;      // mg per 100g/ml
    private Double phenylalanine;   // mg per 100g/ml
    private Double valine;          // mg per 100g/ml
    private Double histidine;       // mg per 100g/ml

    // Semi-essential amino acids
    private Double cysteine;        // mg per 100g/ml
    private Double tyrosine;        // mg per 100g/ml
    private Double arginine;        // mg per 100g/ml

    // Non-essential amino acids
    private Double alanine;         // mg per 100g/ml
    private Double asparticAcid;    // mg per 100g/ml
    private Double glutamicAcid;    // mg per 100g/ml
    private Double glycine;         // mg per 100g/ml
    private Double proline;         // mg per 100g/ml
    private Double serine;          // mg per 100g/ml

    // ========== MINERALS ==========

    // Major minerals
    private Double calcium;         // mg per 100g/ml
    private Double phosphorus;      // mg per 100g/ml
    private Double magnesium;       // mg per 100g/ml
    private Double potassium;       // mg per 100g/ml
    private Double sodium;          // mg per 100g/ml
    private Double salt;            // g per 100g/ml - calculated from sodium
    private Double chloride;        // mg per 100g/ml

    // Trace minerals
    private Double iron;            // mg per 100g/ml
    private Double zinc;            // mg per 100g/ml
    private Double copper;          // mg per 100g/ml
    private Double manganese;       // mg per 100g/ml
    private Double selenium;        // µg per 100g/ml
    private Double iodine;          // µg per 100g/ml
    private Double chromium;        // µg per 100g/ml
    private Double molybdenum;      // µg per 100g/ml
    private Double fluoride;        // µg per 100g/ml

    // ========== OTHER BIOACTIVE COMPOUNDS ==========
    private Double caffeine;        // mg per 100g/ml
    private Double theobromine;     // mg per 100g/ml
    private Double taurine;         // mg per 100g/ml
    private Double organicAcids;    // g per 100g/ml

    // ========== VITAMIN D SUBFORMS (v7) ==========
    private Double vitaminD2;       // µg/100g - Ergocalciferol
    private Double vitaminD3;       // µg/100g - Cholecalciferol

    // ========== FOLATE SUBFORMS (v7) ==========
    private Double intrinsicFolate; // µg/100g - Natural folate
    private Double folicAcid;       // µg/100g - Fortification folate

    // ========== DATA QUALITY TIER (v8) ==========
    private DataConfidence dataConfidence;

    // ========== QUALITY METRICS ==========
    private float dataCompleteness = 0.0f;  // % of fields with data
    private int dataQualityScore = 0;       // 0-100 quality rating
    private long lastUpdated;
    private long createdAt;

    // ========== CONSTRUCTORS ==========

    public NutritionEntity() {
        long now = System.currentTimeMillis();
        this.createdAt = now;
        this.lastUpdated = now;
    }

    // ========== GETTERS AND SETTERS ==========

    // Primary Key and Identification
    @NonNull
    public String getNutritionId() { return nutritionId; }
    public void setNutritionId(@NonNull String nutritionId) { this.nutritionId = nutritionId; }

    @NonNull
    public String getSourceType() { return sourceType; }
    public void setSourceType(@NonNull String sourceType) { this.sourceType = sourceType; }

    @NonNull
    public String getSourceId() { return sourceId; }
    public void setSourceId(@NonNull String sourceId) { this.sourceId = sourceId; }

    public String getDataSource() { return dataSource; }
    public void setDataSource(String dataSource) { this.dataSource = dataSource; }

    // Categorization
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getSubcategory() { return subcategory; }
    public void setSubcategory(String subcategory) { this.subcategory = subcategory; }

    // Measurement
    public String getMeasurementBasis() { return measurementBasis; }
    public void setMeasurementBasis(String measurementBasis) { this.measurementBasis = measurementBasis; }

    public boolean isLiquid() { return isLiquid; }
    public void setLiquid(boolean liquid) { isLiquid = liquid; }

    // Energy
    public Double getEnergyKj() { return energyKj; }
    public void setEnergyKj(Double energyKj) { this.energyKj = energyKj; }

    public Double getEnergyKcal() { return energyKcal; }
    public void setEnergyKcal(Double energyKcal) { this.energyKcal = energyKcal; }

    // Macronutrients
    public Double getProteins() { return proteins; }
    public void setProteins(Double proteins) { this.proteins = proteins; }

    public Double getCarbohydrates() { return carbohydrates; }
    public void setCarbohydrates(Double carbohydrates) { this.carbohydrates = carbohydrates; }

    public Double getFat() { return fat; }
    public void setFat(Double fat) { this.fat = fat; }

    public Double getFiber() { return fiber; }
    public void setFiber(Double fiber) { this.fiber = fiber; }

    public Double getWater() { return water; }
    public void setWater(Double water) { this.water = water; }

    public Double getAlcohol() { return alcohol; }
    public void setAlcohol(Double alcohol) { this.alcohol = alcohol; }

    public Double getAsh() { return ash; }
    public void setAsh(Double ash) { this.ash = ash; }

    // Carbohydrate Details
    public Double getSugars() { return sugars; }
    public void setSugars(Double sugars) { this.sugars = sugars; }

    public Double getStarch() { return starch; }
    public void setStarch(Double starch) { this.starch = starch; }

    public Double getPolyols() { return polyols; }
    public void setPolyols(Double polyols) { this.polyols = polyols; }

    public Double getGlucose() { return glucose; }
    public void setGlucose(Double glucose) { this.glucose = glucose; }

    public Double getFructose() { return fructose; }
    public void setFructose(Double fructose) { this.fructose = fructose; }

    public Double getSucrose() { return sucrose; }
    public void setSucrose(Double sucrose) { this.sucrose = sucrose; }

    public Double getLactose() { return lactose; }
    public void setLactose(Double lactose) { this.lactose = lactose; }

    public Double getMaltose() { return maltose; }
    public void setMaltose(Double maltose) { this.maltose = maltose; }

    public Double getGalactose() { return galactose; }
    public void setGalactose(Double galactose) { this.galactose = galactose; }

    // Fat Details
    public Double getSaturatedFat() { return saturatedFat; }
    public void setSaturatedFat(Double saturatedFat) { this.saturatedFat = saturatedFat; }

    public Double getMonounsaturatedFat() { return monounsaturatedFat; }
    public void setMonounsaturatedFat(Double monounsaturatedFat) { this.monounsaturatedFat = monounsaturatedFat; }

    public Double getPolyunsaturatedFat() { return polyunsaturatedFat; }
    public void setPolyunsaturatedFat(Double polyunsaturatedFat) { this.polyunsaturatedFat = polyunsaturatedFat; }

    public Double getTransFat() { return transFat; }
    public void setTransFat(Double transFat) { this.transFat = transFat; }

    public Double getOmega3() { return omega3; }
    public void setOmega3(Double omega3) { this.omega3 = omega3; }

    public Double getOmega6() { return omega6; }
    public void setOmega6(Double omega6) { this.omega6 = omega6; }

    public Double getOmega9() { return omega9; }
    public void setOmega9(Double omega9) { this.omega9 = omega9; }

    public Double getLinoleicAcid() { return linoleicAcid; }
    public void setLinoleicAcid(Double linoleicAcid) { this.linoleicAcid = linoleicAcid; }

    public Double getDha() { return dha; }
    public void setDha(Double dha) { this.dha = dha; }

    public Double getEpa() { return epa; }
    public void setEpa(Double epa) { this.epa = epa; }

    public Double getAla() { return ala; }
    public void setAla(Double ala) { this.ala = ala; }

    public Double getArachidonicAcid() { return arachidonicAcid; }
    public void setArachidonicAcid(Double arachidonicAcid) { this.arachidonicAcid = arachidonicAcid; }

    public Double getGammaLinolenicAcid() { return gammaLinolenicAcid; }
    public void setGammaLinolenicAcid(Double gammaLinolenicAcid) { this.gammaLinolenicAcid = gammaLinolenicAcid; }

    public Double getConjugatedLinoleicAcid() { return conjugatedLinoleicAcid; }
    public void setConjugatedLinoleicAcid(Double conjugatedLinoleicAcid) { this.conjugatedLinoleicAcid = conjugatedLinoleicAcid; }

    public Double getButyricAcid() { return butyricAcid; }
    public void setButyricAcid(Double butyricAcid) { this.butyricAcid = butyricAcid; }

    public Double getCaproicAcid() { return caproicAcid; }
    public void setCaproicAcid(Double caproicAcid) { this.caproicAcid = caproicAcid; }

    public Double getCaprylicAcid() { return caprylicAcid; }
    public void setCaprylicAcid(Double caprylicAcid) { this.caprylicAcid = caprylicAcid; }

    public Double getCapricAcid() { return capricAcid; }
    public void setCapricAcid(Double capricAcid) { this.capricAcid = capricAcid; }

    public Double getLauricAcid() { return lauricAcid; }
    public void setLauricAcid(Double lauricAcid) { this.lauricAcid = lauricAcid; }

    public Double getMyristicAcid() { return myristicAcid; }
    public void setMyristicAcid(Double myristicAcid) { this.myristicAcid = myristicAcid; }

    public Double getPalmiticAcid() { return palmiticAcid; }
    public void setPalmiticAcid(Double palmiticAcid) { this.palmiticAcid = palmiticAcid; }

    public Double getStearicAcid() { return stearicAcid; }
    public void setStearicAcid(Double stearicAcid) { this.stearicAcid = stearicAcid; }

    public Double getCholesterol() { return cholesterol; }
    public void setCholesterol(Double cholesterol) { this.cholesterol = cholesterol; }

    // Vitamins
    public Double getVitaminA() { return vitaminA; }
    public void setVitaminA(Double vitaminA) { this.vitaminA = vitaminA; }

    public Double getRetinol() { return retinol; }
    public void setRetinol(Double retinol) { this.retinol = retinol; }

    public Double getBetaCarotene() { return betaCarotene; }
    public void setBetaCarotene(Double betaCarotene) { this.betaCarotene = betaCarotene; }

    public Double getVitaminB1() { return vitaminB1; }
    public void setVitaminB1(Double vitaminB1) { this.vitaminB1 = vitaminB1; }

    public Double getVitaminB2() { return vitaminB2; }
    public void setVitaminB2(Double vitaminB2) { this.vitaminB2 = vitaminB2; }

    public Double getVitaminB3() { return vitaminB3; }
    public void setVitaminB3(Double vitaminB3) { this.vitaminB3 = vitaminB3; }

    public Double getVitaminB5() { return vitaminB5; }
    public void setVitaminB5(Double vitaminB5) { this.vitaminB5 = vitaminB5; }

    public Double getVitaminB6() { return vitaminB6; }
    public void setVitaminB6(Double vitaminB6) { this.vitaminB6 = vitaminB6; }

    public Double getVitaminB7() { return vitaminB7; }
    public void setVitaminB7(Double vitaminB7) { this.vitaminB7 = vitaminB7; }

    public Double getVitaminB9() { return vitaminB9; }
    public void setVitaminB9(Double vitaminB9) { this.vitaminB9 = vitaminB9; }

    public Double getFolate() { return folate; }
    public void setFolate(Double folate) { this.folate = folate; }

    public Double getVitaminB12() { return vitaminB12; }
    public void setVitaminB12(Double vitaminB12) { this.vitaminB12 = vitaminB12; }

    public Double getVitaminC() { return vitaminC; }
    public void setVitaminC(Double vitaminC) { this.vitaminC = vitaminC; }

    public Double getVitaminD() { return vitaminD; }
    public void setVitaminD(Double vitaminD) { this.vitaminD = vitaminD; }

    public Double getVitaminE() { return vitaminE; }
    public void setVitaminE(Double vitaminE) { this.vitaminE = vitaminE; }

    public Double getVitaminK() { return vitaminK; }
    public void setVitaminK(Double vitaminK) { this.vitaminK = vitaminK; }

    public Double getVitaminK1() { return vitaminK1; }
    public void setVitaminK1(Double vitaminK1) { this.vitaminK1 = vitaminK1; }

    public Double getVitaminK2() { return vitaminK2; }
    public void setVitaminK2(Double vitaminK2) { this.vitaminK2 = vitaminK2; }

    public Double getCholine() { return choline; }
    public void setCholine(Double choline) { this.choline = choline; }

    // Amino acids
    public Double getTryptophan() { return tryptophan; }
    public void setTryptophan(Double tryptophan) { this.tryptophan = tryptophan; }

    public Double getThreonine() { return threonine; }
    public void setThreonine(Double threonine) { this.threonine = threonine; }

    public Double getIsoleucine() { return isoleucine; }
    public void setIsoleucine(Double isoleucine) { this.isoleucine = isoleucine; }

    public Double getLeucine() { return leucine; }
    public void setLeucine(Double leucine) { this.leucine = leucine; }

    public Double getLysine() { return lysine; }
    public void setLysine(Double lysine) { this.lysine = lysine; }

    public Double getMethionine() { return methionine; }
    public void setMethionine(Double methionine) { this.methionine = methionine; }

    public Double getPhenylalanine() { return phenylalanine; }
    public void setPhenylalanine(Double phenylalanine) { this.phenylalanine = phenylalanine; }

    public Double getValine() { return valine; }
    public void setValine(Double valine) { this.valine = valine; }

    public Double getHistidine() { return histidine; }
    public void setHistidine(Double histidine) { this.histidine = histidine; }

    public Double getCysteine() { return cysteine; }
    public void setCysteine(Double cysteine) { this.cysteine = cysteine; }

    public Double getTyrosine() { return tyrosine; }
    public void setTyrosine(Double tyrosine) { this.tyrosine = tyrosine; }

    public Double getArginine() { return arginine; }
    public void setArginine(Double arginine) { this.arginine = arginine; }

    public Double getAlanine() { return alanine; }
    public void setAlanine(Double alanine) { this.alanine = alanine; }

    public Double getAsparticAcid() { return asparticAcid; }
    public void setAsparticAcid(Double asparticAcid) { this.asparticAcid = asparticAcid; }

    public Double getGlutamicAcid() { return glutamicAcid; }
    public void setGlutamicAcid(Double glutamicAcid) { this.glutamicAcid = glutamicAcid; }

    public Double getGlycine() { return glycine; }
    public void setGlycine(Double glycine) { this.glycine = glycine; }

    public Double getProline() { return proline; }
    public void setProline(Double proline) { this.proline = proline; }

    public Double getSerine() { return serine; }
    public void setSerine(Double serine) { this.serine = serine; }

    // Minerals
    public Double getSalt() { return salt; }
    public void setSalt(Double salt) { this.salt = salt; }

    public Double getSodium() { return sodium; }
    public void setSodium(Double sodium) { this.sodium = sodium; }

    public Double getCalcium() { return calcium; }
    public void setCalcium(Double calcium) { this.calcium = calcium; }

    public Double getIron() { return iron; }
    public void setIron(Double iron) { this.iron = iron; }

    public Double getMagnesium() { return magnesium; }
    public void setMagnesium(Double magnesium) { this.magnesium = magnesium; }

    public Double getPhosphorus() { return phosphorus; }
    public void setPhosphorus(Double phosphorus) { this.phosphorus = phosphorus; }

    public Double getPotassium() { return potassium; }
    public void setPotassium(Double potassium) { this.potassium = potassium; }

    public Double getZinc() { return zinc; }
    public void setZinc(Double zinc) { this.zinc = zinc; }

    public Double getCopper() { return copper; }
    public void setCopper(Double copper) { this.copper = copper; }

    public Double getManganese() { return manganese; }
    public void setManganese(Double manganese) { this.manganese = manganese; }

    public Double getSelenium() { return selenium; }
    public void setSelenium(Double selenium) { this.selenium = selenium; }

    public Double getIodine() { return iodine; }
    public void setIodine(Double iodine) { this.iodine = iodine; }

    public Double getChloride() { return chloride; }
    public void setChloride(Double chloride) { this.chloride = chloride; }

    public Double getChromium() { return chromium; }
    public void setChromium(Double chromium) { this.chromium = chromium; }

    public Double getMolybdenum() { return molybdenum; }
    public void setMolybdenum(Double molybdenum) { this.molybdenum = molybdenum; }

    public Double getFluoride() { return fluoride; }
    public void setFluoride(Double fluoride) { this.fluoride = fluoride; }

    // Other bioactive compounds
    public Double getCaffeine() { return caffeine; }
    public void setCaffeine(Double caffeine) { this.caffeine = caffeine; }

    public Double getTheobromine() { return theobromine; }
    public void setTheobromine(Double theobromine) { this.theobromine = theobromine; }

    public Double getTaurine() { return taurine; }
    public void setTaurine(Double taurine) { this.taurine = taurine; }

    public Double getOrganicAcids() { return organicAcids; }
    public void setOrganicAcids(Double organicAcids) { this.organicAcids = organicAcids; }

    // Quality Metrics
    public float getDataCompleteness() { return dataCompleteness; }
    public void setDataCompleteness(float dataCompleteness) { this.dataCompleteness = dataCompleteness; }

    public int getDataQualityScore() { return dataQualityScore; }
    public void setDataQualityScore(int dataQualityScore) { this.dataQualityScore = dataQualityScore; }

    public long getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(long lastUpdated) { this.lastUpdated = lastUpdated; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    // ========== HELPER METHODS ==========

    /**
     * Calculate data completeness percentage
     */
    public void calculateCompleteness() {
        int totalFields = 0;
        int filledFields = 0;

        // Check basic macronutrients (most important)
        if (energyKcal != null) { filledFields++; } totalFields++;
        if (proteins != null) { filledFields++; } totalFields++;
        if (carbohydrates != null) { filledFields++; } totalFields++;
        if (fat != null) { filledFields++; } totalFields++;
        if (fiber != null) { filledFields++; } totalFields++;
        if (sugars != null) { filledFields++; } totalFields++;
        if (salt != null) { filledFields++; } totalFields++;
        if (saturatedFat != null) { filledFields++; } totalFields++;

        this.dataCompleteness = totalFields > 0 ?
                (float) filledFields / totalFields * 100 : 0;
    }

    /**
     * Check if has basic nutrition data
     */
    public boolean hasBasicData() {
        return energyKcal != null || proteins != null ||
                carbohydrates != null || fat != null;
    }

    /**
     * Create nutrition ID from source
     */
    public static String createNutritionId(String sourceType, String sourceId) {
        return sourceType + ":" + sourceId;
    }

    /**
     * Convert to domain Nutrition model
     */
    public Nutrition toNutrition() {
        Nutrition nutrition = new Nutrition();

        // Energy
        nutrition.setEnergyKj(this.energyKj);
        nutrition.setEnergyKcal(this.energyKcal);

        // Basic macronutrients
        nutrition.setProteins(this.proteins);
        nutrition.setCarbohydrates(this.carbohydrates);
        nutrition.setFat(this.fat);
        nutrition.setFiber(this.fiber);
        nutrition.setWater(this.water);
        nutrition.setAlcohol(this.alcohol);
        nutrition.setAsh(this.ash);

        // Carbohydrate breakdown
        nutrition.setSugars(this.sugars);
        nutrition.setStarch(this.starch);
        nutrition.setPolyols(this.polyols);
        nutrition.setGlucose(this.glucose);
        nutrition.setFructose(this.fructose);
        nutrition.setSucrose(this.sucrose);
        nutrition.setLactose(this.lactose);
        nutrition.setMaltose(this.maltose);
        nutrition.setGalactose(this.galactose);

        // Fats - detailed breakdown
        nutrition.setSaturatedFat(this.saturatedFat);
        nutrition.setMonounsaturatedFat(this.monounsaturatedFat);
        nutrition.setPolyunsaturatedFat(this.polyunsaturatedFat);
        nutrition.setTransFat(this.transFat);

        // Essential fatty acids
        nutrition.setOmega3(this.omega3);
        nutrition.setOmega6(this.omega6);
        nutrition.setOmega9(this.omega9);
        nutrition.setLinoleicAcid(this.linoleicAcid);

        // Specific omega-3 fatty acids
        nutrition.setDHA(this.dha);
        nutrition.setEPA(this.epa);
        nutrition.setALA(this.ala);

        // Other important fatty acids
        nutrition.setArachidonicAcid(this.arachidonicAcid);
        nutrition.setGammaLinolenicAcid(this.gammaLinolenicAcid);
        nutrition.setConjugatedLinoleicAcid(this.conjugatedLinoleicAcid);

        // Specific saturated fatty acids
        nutrition.setButyricAcid(this.butyricAcid);
        nutrition.setCaproicAcid(this.caproicAcid);
        nutrition.setCaprylicAcid(this.caprylicAcid);
        nutrition.setCapricAcid(this.capricAcid);
        nutrition.setLauricAcid(this.lauricAcid);
        nutrition.setMyristicAcid(this.myristicAcid);
        nutrition.setPalmiticAcid(this.palmiticAcid);
        nutrition.setStearicAcid(this.stearicAcid);
        nutrition.setCholesterol(this.cholesterol);

        // Fat-soluble vitamins
        nutrition.setVitaminA(this.vitaminA);
        nutrition.setRetinol(this.retinol);
        nutrition.setBetaCarotene(this.betaCarotene);
        nutrition.setVitaminD(this.vitaminD);
        nutrition.setVitaminE(this.vitaminE);
        nutrition.setVitaminK(this.vitaminK);
        nutrition.setVitaminK1(this.vitaminK1);
        nutrition.setVitaminK2(this.vitaminK2);

        // Water-soluble vitamins (B-complex)
        nutrition.setVitaminB1(this.vitaminB1);
        nutrition.setVitaminB2(this.vitaminB2);
        nutrition.setVitaminB3(this.vitaminB3);
        nutrition.setVitaminB5(this.vitaminB5);
        nutrition.setVitaminB6(this.vitaminB6);
        nutrition.setVitaminB7(this.vitaminB7);
        nutrition.setVitaminB9(this.vitaminB9);
        nutrition.setFolate(this.folate);
        nutrition.setVitaminB12(this.vitaminB12);
        nutrition.setVitaminC(this.vitaminC);
        nutrition.setCholine(this.choline);

        // Amino acids
        nutrition.setTryptophan(this.tryptophan);
        nutrition.setThreonine(this.threonine);
        nutrition.setIsoleucine(this.isoleucine);
        nutrition.setLeucine(this.leucine);
        nutrition.setLysine(this.lysine);
        nutrition.setMethionine(this.methionine);
        nutrition.setCysteine(this.cysteine);
        nutrition.setPhenylalanine(this.phenylalanine);
        nutrition.setTyrosine(this.tyrosine);
        nutrition.setValine(this.valine);
        nutrition.setArginine(this.arginine);
        nutrition.setHistidine(this.histidine);
        nutrition.setAlanine(this.alanine);
        nutrition.setAsparticAcid(this.asparticAcid);
        nutrition.setGlutamicAcid(this.glutamicAcid);
        nutrition.setGlycine(this.glycine);
        nutrition.setProline(this.proline);
        nutrition.setSerine(this.serine);

        // Minerals
        nutrition.setCalcium(this.calcium);
        nutrition.setPhosphorus(this.phosphorus);
        nutrition.setMagnesium(this.magnesium);
        nutrition.setPotassium(this.potassium);
        nutrition.setSodium(this.sodium);
        nutrition.setSalt(this.salt);
        nutrition.setChloride(this.chloride);
        nutrition.setIron(this.iron);
        nutrition.setZinc(this.zinc);
        nutrition.setCopper(this.copper);
        nutrition.setManganese(this.manganese);
        nutrition.setSelenium(this.selenium);
        nutrition.setIodine(this.iodine);
        nutrition.setChromium(this.chromium);
        nutrition.setMolybdenum(this.molybdenum);
        nutrition.setFluoride(this.fluoride);

        // Other bioactive compounds
        nutrition.setCaffeine(this.caffeine);
        nutrition.setTheobromine(this.theobromine);
        nutrition.setTaurine(this.taurine);
        nutrition.setOrganicAcids(this.organicAcids);

        // Vitamin D subforms (v7)
        nutrition.setVitaminD2(this.vitaminD2);
        nutrition.setVitaminD3(this.vitaminD3);

        // Folate subforms (v7)
        nutrition.setIntrinsicFolate(this.intrinsicFolate);
        nutrition.setFolicAcid(this.folicAcid);

        // Data quality (v7)
        nutrition.setDataConfidence(this.dataConfidence);

        // Metadata
        nutrition.setDataSource(this.dataSource);
        nutrition.setDataCompleteness(this.dataCompleteness);

        return nutrition;
    }

    /**
     * Create from domain Nutrition model
     */
    public static NutritionEntity fromNutrition(Nutrition nutrition, String sourceType, String sourceId) {
        NutritionEntity entity = new NutritionEntity();

        // Set IDs
        entity.setNutritionId(createNutritionId(sourceType, sourceId));
        entity.setSourceType(sourceType);
        entity.setSourceId(sourceId);

        if (nutrition != null) {
            // Energy
            entity.setEnergyKj(nutrition.getEnergyKj());
            entity.setEnergyKcal(nutrition.getEnergyKcal());

            // Basic macronutrients
            entity.setProteins(nutrition.getProteins());
            entity.setCarbohydrates(nutrition.getCarbohydrates());
            entity.setFat(nutrition.getFat());
            entity.setFiber(nutrition.getFiber());
            entity.setWater(nutrition.getWater());
            entity.setAlcohol(nutrition.getAlcohol());
            entity.setAsh(nutrition.getAsh());

            // Carbohydrate breakdown
            entity.setSugars(nutrition.getSugars());
            entity.setStarch(nutrition.getStarch());
            entity.setPolyols(nutrition.getPolyols());
            entity.setGlucose(nutrition.getGlucose());
            entity.setFructose(nutrition.getFructose());
            entity.setSucrose(nutrition.getSucrose());
            entity.setLactose(nutrition.getLactose());
            entity.setMaltose(nutrition.getMaltose());
            entity.setGalactose(nutrition.getGalactose());

            // Fats - detailed breakdown
            entity.setSaturatedFat(nutrition.getSaturatedFat());
            entity.setMonounsaturatedFat(nutrition.getMonounsaturatedFat());
            entity.setPolyunsaturatedFat(nutrition.getPolyunsaturatedFat());
            entity.setTransFat(nutrition.getTransFat());

            // Essential fatty acids
            entity.setOmega3(nutrition.getOmega3());
            entity.setOmega6(nutrition.getOmega6());
            entity.setOmega9(nutrition.getOmega9());
            entity.setLinoleicAcid(nutrition.getLinoleicAcid());

            // Specific omega-3 fatty acids
            entity.setDha(nutrition.getDHA());
            entity.setEpa(nutrition.getEPA());
            entity.setAla(nutrition.getALA());

            // Other important fatty acids
            entity.setArachidonicAcid(nutrition.getArachidonicAcid());
            entity.setGammaLinolenicAcid(nutrition.getGammaLinolenicAcid());
            entity.setConjugatedLinoleicAcid(nutrition.getConjugatedLinoleicAcid());

            // Specific saturated fatty acids
            entity.setButyricAcid(nutrition.getButyricAcid());
            entity.setCaproicAcid(nutrition.getCaproicAcid());
            entity.setCaprylicAcid(nutrition.getCaprylicAcid());
            entity.setCapricAcid(nutrition.getCapricAcid());
            entity.setLauricAcid(nutrition.getLauricAcid());
            entity.setMyristicAcid(nutrition.getMyristicAcid());
            entity.setPalmiticAcid(nutrition.getPalmiticAcid());
            entity.setStearicAcid(nutrition.getStearicAcid());

            entity.setCholesterol(nutrition.getCholesterol());

            // Fat-soluble vitamins
            entity.setVitaminA(nutrition.getVitaminA());
            entity.setRetinol(nutrition.getRetinol());
            entity.setBetaCarotene(nutrition.getBetaCarotene());
            entity.setVitaminD(nutrition.getVitaminD());
            entity.setVitaminE(nutrition.getVitaminE());
            entity.setVitaminK(nutrition.getVitaminK());
            entity.setVitaminK1(nutrition.getVitaminK1());
            entity.setVitaminK2(nutrition.getVitaminK2());

            // Water-soluble vitamins (B-complex)
            entity.setVitaminB1(nutrition.getVitaminB1());
            entity.setVitaminB2(nutrition.getVitaminB2());
            entity.setVitaminB3(nutrition.getVitaminB3());
            entity.setVitaminB5(nutrition.getVitaminB5());
            entity.setVitaminB6(nutrition.getVitaminB6());
            entity.setVitaminB7(nutrition.getVitaminB7());
            entity.setVitaminB9(nutrition.getVitaminB9());
            entity.setFolate(nutrition.getFolate());
            entity.setVitaminB12(nutrition.getVitaminB12());
            entity.setVitaminC(nutrition.getVitaminC());
            entity.setCholine(nutrition.getCholine());

            // Amino acids
            entity.setTryptophan(nutrition.getTryptophan());
            entity.setThreonine(nutrition.getThreonine());
            entity.setIsoleucine(nutrition.getIsoleucine());
            entity.setLeucine(nutrition.getLeucine());
            entity.setLysine(nutrition.getLysine());
            entity.setMethionine(nutrition.getMethionine());
            entity.setCysteine(nutrition.getCysteine());
            entity.setPhenylalanine(nutrition.getPhenylalanine());
            entity.setTyrosine(nutrition.getTyrosine());
            entity.setValine(nutrition.getValine());
            entity.setArginine(nutrition.getArginine());
            entity.setHistidine(nutrition.getHistidine());
            entity.setAlanine(nutrition.getAlanine());
            entity.setAsparticAcid(nutrition.getAsparticAcid());
            entity.setGlutamicAcid(nutrition.getGlutamicAcid());
            entity.setGlycine(nutrition.getGlycine());
            entity.setProline(nutrition.getProline());
            entity.setSerine(nutrition.getSerine());

            // Minerals
            entity.setCalcium(nutrition.getCalcium());
            entity.setPhosphorus(nutrition.getPhosphorus());
            entity.setMagnesium(nutrition.getMagnesium());
            entity.setPotassium(nutrition.getPotassium());
            entity.setSodium(nutrition.getSodium());
            entity.setSalt(nutrition.getSalt());
            entity.setChloride(nutrition.getChloride());
            entity.setIron(nutrition.getIron());
            entity.setZinc(nutrition.getZinc());
            entity.setCopper(nutrition.getCopper());
            entity.setManganese(nutrition.getManganese());
            entity.setSelenium(nutrition.getSelenium());
            entity.setIodine(nutrition.getIodine());
            entity.setChromium(nutrition.getChromium());
            entity.setMolybdenum(nutrition.getMolybdenum());
            entity.setFluoride(nutrition.getFluoride());

            // Other bioactive compounds
            entity.setCaffeine(nutrition.getCaffeine());
            entity.setTheobromine(nutrition.getTheobromine());
            entity.setTaurine(nutrition.getTaurine());
            entity.setOrganicAcids(nutrition.getOrganicAcids());

            // Vitamin D subforms (v7)
            entity.setVitaminD2(nutrition.getVitaminD2());
            entity.setVitaminD3(nutrition.getVitaminD3());

            // Folate subforms (v7)
            entity.setIntrinsicFolate(nutrition.getIntrinsicFolate());
            entity.setFolicAcid(nutrition.getFolicAcid());

            // Data quality (v8)
            entity.setDataConfidence(nutrition.getDataConfidence());

            // Metadata
            entity.setDataSource(nutrition.getDataSource());
            entity.setDataCompleteness(nutrition.getDataCompleteness());
        }

        entity.calculateCompleteness();
        return entity;
    }
    // ===== VITAMIN D SUBFORMS (v7) =====
    public Double getVitaminD2() { return vitaminD2; }
    public void setVitaminD2(Double v) { this.vitaminD2 = v; }
    public Double getVitaminD3() { return vitaminD3; }
    public void setVitaminD3(Double v) { this.vitaminD3 = v; }

    // ===== FOLATE SUBFORMS (v7) =====
    public Double getIntrinsicFolate() { return intrinsicFolate; }
    public void setIntrinsicFolate(Double v) { this.intrinsicFolate = v; }
    public Double getFolicAcid() { return folicAcid; }
    public void setFolicAcid(Double v) { this.folicAcid = v; }

    // ===== DATA QUALITY TIER (v8) =====
    public DataConfidence getDataConfidence() { return dataConfidence; }
    public void setDataConfidence(DataConfidence v) { this.dataConfidence = v; }
}