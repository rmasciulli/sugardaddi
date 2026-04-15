package li.masciul.sugardaddi.core.models;

import android.content.Context;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import li.masciul.sugardaddi.R;


/**
 * Nutrition - Universal nutrition model supporting all data sources
 *
 * CRITICAL CONVENTIONS:
 * 1. ALL values are stored per 100g (solids) or 100ml (liquids)
 * 2. NULL means "unknown/not measured", 0.0 means "confirmed zero"
 * 3. Energy is stored in both kJ and kcal for international compatibility
 * 4. Minerals/vitamins use standard scientific units (mg, µg)
 *
 * Example: Carrot alcohol = 0.0 (measured, none present)
 *         Carrot caffeine = null (not typically measured)
 */
public class Nutrition {
    // ========== STORAGE CONVENTION ==========
    private static final String MEASUREMENT_BASIS = "per_100g_or_100ml";

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
    private Double DHA;                    // mg per 100g/ml - Docosahexaenoic acid
    private Double EPA;                    // mg per 100g/ml - Eicosapentaenoic acid
    private Double ALA;                    // mg per 100g/ml - Alpha-linolenic acid

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
    private Double cholesterol;     // mg per 100g/ml
    private Double caffeine;        // mg per 100g/ml
    private Double theobromine;     // mg per 100g/ml
    private Double taurine;         // mg per 100g/ml
    private Double organicAcids;    // g per 100g/ml
    private Double ash;             // g per 100g/ml - mineral content

    // ========== VITAMIN D SUBFORMS (Ciqual 2025 / USDA) ==========
    // vitaminD = total (D2+D3). D2 and D3 are sub-components stored separately.
    private Double vitaminD2;       // µg per 100g/ml - Ergocalciferol
    private Double vitaminD3;       // µg per 100g/ml - Cholecalciferol

    // ========== FOLATE SUBFORMS (Ciqual 2025) ==========
    // vitaminB9 = total DFE. intrinsicFolate + folicAcid are its components.
    private Double intrinsicFolate; // µg per 100g/ml - Natural folate in food matrix
    private Double folicAcid;       // µg per 100g/ml - Fortification folate (added)

    // ========== DATA QUALITY ==========
    /**
     * Source-specific data quality tier.
     * Ciqual: "A" (representative French data, highest) to "D" (data > 10 years old).
     * USDA: equivalent quality tiers can be mapped here.
     * OFF: null (no equivalent confidence system).
     */
    private String dataConfidenceCode; // "A", "B", "C", "D", or null

    // ========== METADATA ==========
    private String dataSource;        // "OFF", "ciqual", "usda", etc.
    private float completeness = -1;   // % of basic fields with data
    private float dataCompleteness = -1; // Alternative completeness measure
    private long lastUpdated;
    private Map<String, Object> sourceSpecificData = new HashMap<>();

    // ========== CONSTRUCTORS ==========

    public Nutrition() {
        this.lastUpdated = System.currentTimeMillis();
    }

    /**
     * Copy constructor for calculations
     */
    public Nutrition(Nutrition source) {
        if (source != null) {
            // Energy
            this.energyKj = source.energyKj;
            this.energyKcal = source.energyKcal;

            // Basic macronutrients
            this.proteins = source.proteins;
            this.carbohydrates = source.carbohydrates;
            this.fat = source.fat;
            this.fiber = source.fiber;
            this.alcohol = source.alcohol;
            this.water = source.water;

            // Carbohydrate breakdown
            this.sugars = source.sugars;
            this.starch = source.starch;
            this.polyols = source.polyols;

            // Specific sugars
            this.glucose = source.glucose;
            this.fructose = source.fructose;
            this.sucrose = source.sucrose;
            this.lactose = source.lactose;
            this.maltose = source.maltose;
            this.galactose = source.galactose;

            // Fats - detailed breakdown
            this.saturatedFat = source.saturatedFat;
            this.monounsaturatedFat = source.monounsaturatedFat;
            this.polyunsaturatedFat = source.polyunsaturatedFat;
            this.transFat = source.transFat;

            // Essential fatty acids
            this.omega3 = source.omega3;
            this.omega6 = source.omega6;
            this.omega9 = source.omega9;
            this.linoleicAcid = source.linoleicAcid;

            // Specific omega-3 fatty acids
            this.DHA = source.DHA;
            this.EPA = source.EPA;
            this.ALA = source.ALA;

            // Other important fatty acids
            this.arachidonicAcid = source.arachidonicAcid;
            this.gammaLinolenicAcid = source.gammaLinolenicAcid;
            this.conjugatedLinoleicAcid = source.conjugatedLinoleicAcid;

            // Specific saturated fatty acids
            this.butyricAcid = source.butyricAcid;
            this.caproicAcid = source.caproicAcid;
            this.caprylicAcid = source.caprylicAcid;
            this.capricAcid = source.capricAcid;
            this.lauricAcid = source.lauricAcid;
            this.myristicAcid = source.myristicAcid;
            this.palmiticAcid = source.palmiticAcid;
            this.stearicAcid = source.stearicAcid;

            // Fat-soluble vitamins
            this.vitaminA = source.vitaminA;
            this.retinol = source.retinol;
            this.betaCarotene = source.betaCarotene;
            this.vitaminD = source.vitaminD;
            this.vitaminE = source.vitaminE;
            this.vitaminK = source.vitaminK;
            this.vitaminK1 = source.vitaminK1;
            this.vitaminK2 = source.vitaminK2;

            // Water-soluble vitamins (B-complex)
            this.vitaminB1 = source.vitaminB1;
            this.vitaminB2 = source.vitaminB2;
            this.vitaminB3 = source.vitaminB3;
            this.vitaminB5 = source.vitaminB5;
            this.vitaminB6 = source.vitaminB6;
            this.vitaminB7 = source.vitaminB7;
            this.vitaminB9 = source.vitaminB9;
            this.folate = source.folate;
            this.vitaminB12 = source.vitaminB12;
            this.vitaminC = source.vitaminC;
            this.choline = source.choline;

            // Amino acids
            this.tryptophan = source.tryptophan;
            this.threonine = source.threonine;
            this.isoleucine = source.isoleucine;
            this.leucine = source.leucine;
            this.lysine = source.lysine;
            this.methionine = source.methionine;
            this.cysteine = source.cysteine;
            this.phenylalanine = source.phenylalanine;
            this.tyrosine = source.tyrosine;
            this.valine = source.valine;
            this.arginine = source.arginine;
            this.histidine = source.histidine;
            this.alanine = source.alanine;
            this.asparticAcid = source.asparticAcid;
            this.glutamicAcid = source.glutamicAcid;
            this.glycine = source.glycine;
            this.proline = source.proline;
            this.serine = source.serine;

            // Minerals
            this.calcium = source.calcium;
            this.phosphorus = source.phosphorus;
            this.magnesium = source.magnesium;
            this.potassium = source.potassium;
            this.sodium = source.sodium;
            this.salt = source.salt;
            this.chloride = source.chloride;
            this.iron = source.iron;
            this.zinc = source.zinc;
            this.copper = source.copper;
            this.manganese = source.manganese;
            this.selenium = source.selenium;
            this.iodine = source.iodine;
            this.chromium = source.chromium;
            this.molybdenum = source.molybdenum;
            this.fluoride = source.fluoride;

            // Other bioactive compounds
            this.cholesterol = source.cholesterol;
            this.caffeine = source.caffeine;
            this.theobromine = source.theobromine;
            this.taurine = source.taurine;
            this.organicAcids = source.organicAcids;
            this.ash = source.ash;

            this.dataSource = source.dataSource;
            this.dataCompleteness = source.dataCompleteness;
            this.lastUpdated = System.currentTimeMillis();
        }
    }

    /**
     * Create a deep copy of this Nutrition object
     * Essential for recipes and meals that need independent nutrition data
     */
    public Nutrition copy() {
        Nutrition copy = new Nutrition();

        // Energy
        copy.energyKj = this.energyKj;
        copy.energyKcal = this.energyKcal;

        // Macronutrients
        copy.proteins = this.proteins;
        copy.carbohydrates = this.carbohydrates;
        copy.fat = this.fat;
        copy.fiber = this.fiber;
        copy.water = this.water;
        copy.alcohol = this.alcohol;
        copy.ash = this.ash;

        // Carbohydrate details
        copy.sugars = this.sugars;
        copy.starch = this.starch;
        copy.polyols = this.polyols;
        copy.glucose = this.glucose;
        copy.fructose = this.fructose;
        copy.sucrose = this.sucrose;
        copy.lactose = this.lactose;
        copy.maltose = this.maltose;
        copy.galactose = this.galactose;

        // Fat details
        copy.saturatedFat = this.saturatedFat;
        copy.monounsaturatedFat = this.monounsaturatedFat;
        copy.polyunsaturatedFat = this.polyunsaturatedFat;
        copy.transFat = this.transFat;
        copy.cholesterol = this.cholesterol;
        copy.omega3 = this.omega3;
        copy.omega6 = this.omega6;
        copy.omega9 = this.omega9;
        copy.linoleicAcid = this.linoleicAcid;
        copy.DHA = this.DHA;
        copy.EPA = this.EPA;
        copy.ALA = this.ALA;
        copy.arachidonicAcid = this.arachidonicAcid;
        copy.gammaLinolenicAcid = this.gammaLinolenicAcid;
        copy.conjugatedLinoleicAcid = this.conjugatedLinoleicAcid;

        // Saturated fatty acids
        copy.butyricAcid = this.butyricAcid;
        copy.caproicAcid = this.caproicAcid;
        copy.caprylicAcid = this.caprylicAcid;
        copy.capricAcid = this.capricAcid;
        copy.lauricAcid = this.lauricAcid;
        copy.myristicAcid = this.myristicAcid;
        copy.palmiticAcid = this.palmiticAcid;
        copy.stearicAcid = this.stearicAcid;

        // Minerals
        copy.salt = this.salt;
        copy.sodium = this.sodium;
        copy.calcium = this.calcium;
        copy.iron = this.iron;
        copy.magnesium = this.magnesium;
        copy.phosphorus = this.phosphorus;
        copy.potassium = this.potassium;
        copy.zinc = this.zinc;
        copy.copper = this.copper;
        copy.manganese = this.manganese;
        copy.selenium = this.selenium;
        copy.iodine = this.iodine;
        copy.chloride = this.chloride;
        copy.chromium = this.chromium;
        copy.fluoride = this.fluoride;
        copy.molybdenum = this.molybdenum;

        // Vitamins
        copy.vitaminA = this.vitaminA;
        copy.retinol = this.retinol;
        copy.betaCarotene = this.betaCarotene;
        copy.vitaminD = this.vitaminD;
        copy.vitaminE = this.vitaminE;
        copy.vitaminK = this.vitaminK;
        copy.vitaminK1 = this.vitaminK1;
        copy.vitaminK2 = this.vitaminK2;
        copy.vitaminC = this.vitaminC;
        copy.vitaminB1 = this.vitaminB1;
        copy.vitaminB2 = this.vitaminB2;
        copy.vitaminB3 = this.vitaminB3;
        copy.vitaminB5 = this.vitaminB5;
        copy.vitaminB6 = this.vitaminB6;
        copy.vitaminB7 = this.vitaminB7;
        copy.vitaminB9 = this.vitaminB9;
        copy.folate = this.folate;
        copy.vitaminB12 = this.vitaminB12;
        copy.choline = this.choline;

        // Amino acids
        copy.tryptophan = this.tryptophan;
        copy.threonine = this.threonine;
        copy.isoleucine = this.isoleucine;
        copy.leucine = this.leucine;
        copy.lysine = this.lysine;
        copy.methionine = this.methionine;
        copy.cysteine = this.cysteine;
        copy.phenylalanine = this.phenylalanine;
        copy.tyrosine = this.tyrosine;
        copy.valine = this.valine;
        copy.arginine = this.arginine;
        copy.histidine = this.histidine;
        copy.alanine = this.alanine;
        copy.asparticAcid = this.asparticAcid;
        copy.glutamicAcid = this.glutamicAcid;
        copy.glycine = this.glycine;
        copy.proline = this.proline;
        copy.serine = this.serine;

        // Other compounds
        copy.caffeine = this.caffeine;
        copy.theobromine = this.theobromine;
        copy.taurine = this.taurine;

        // Metadata
        copy.dataSource = this.dataSource;
        copy.dataCompleteness = this.dataCompleteness;
        copy.lastUpdated = System.currentTimeMillis();

        // Copy source-specific data if needed
        if (this.sourceSpecificData != null) {
            copy.sourceSpecificData = new HashMap<>(this.sourceSpecificData);
        }

        return copy;
    }

    // ========== HAS METHODS - Check data availability ==========

    // Generic methods
    public boolean hasData() { return energyKcal != null || proteins != null || carbohydrates != null || fat != null || fiber != null || salt != null; }
    public boolean hasBasicMacros() { return energyKcal != null && proteins != null && carbohydrates != null && fat != null; }
    public boolean hasDetailedFattyAcids() { return hasOmega3() || hasOmega6() || hasDHA() || hasEPA(); }
    public boolean hasSugarBreakdown() { return glucose != null || fructose != null || sucrose != null || lactose != null || maltose != null; }
    public boolean hasAminoAcids() { return tryptophan != null || leucine != null || lysine != null || methionine != null; }

    // Energy specific methods
    public boolean hasEnergyKj() { return energyKj != null; }
    public boolean hasEnergyKcal() { return energyKcal != null; }

    // Macronutrients specific methods
    public boolean hasProteins() { return proteins != null; }
    public boolean hasCarbohydrates() { return carbohydrates != null; }
    public boolean hasFat() { return fat != null; }
    public boolean hasFiber() { return fiber != null; }
    public boolean hasAlcohol() { return alcohol != null; }
    public boolean hasWater() { return water != null; }

    // Sugars specific methods
    public boolean hasSugars() { return sugars != null; }
    public boolean hasStarch() { return starch != null; }
    public boolean hasPolyols() { return polyols != null; }
    public boolean hasGlucose() { return glucose != null; }
    public boolean hasFructose() { return fructose != null; }
    public boolean hasSucrose() { return sucrose != null; }
    public boolean hasLactose() { return lactose != null; }
    public boolean hasMaltose() { return maltose != null; }
    public boolean hasGalactose() { return galactose != null; }

    // Fats specific methods
    public boolean hasSaturatedFat() { return saturatedFat != null; }
    public boolean hasMonounsaturatedFat() { return monounsaturatedFat != null; }
    public boolean hasPolyunsaturatedFat() { return polyunsaturatedFat != null; }
    public boolean hasTransFat() { return transFat != null; }

    public boolean hasOmega3() { return omega3 != null; }
    public boolean hasOmega6() { return omega6 != null; }
    public boolean hasOmega9() { return omega9 != null; }
    public boolean hasLinoleicAcid() { return linoleicAcid != null; }

    public boolean hasDHA() { return DHA != null; }
    public boolean hasEPA() { return EPA != null; }
    public boolean hasALA() { return ALA != null; }

    public boolean hasArachidonicAcid() { return arachidonicAcid != null; }
    public boolean hasGammaLinolenicAcid() { return gammaLinolenicAcid != null; }
    public boolean hasConjugatedLinoleicAcid() { return conjugatedLinoleicAcid != null; }

    public boolean hasButyricAcid() { return butyricAcid != null; }
    public boolean hasCaproicAcid() { return caproicAcid != null; }
    public boolean hasCaprylicAcid() { return caprylicAcid != null; }
    public boolean hasCapricAcid() { return capricAcid != null; }
    public boolean hasLauricAcid() { return lauricAcid != null; }
    public boolean hasMyristicAcid() { return myristicAcid != null; }
    public boolean hasPalmiticAcid() { return palmiticAcid != null; }
    public boolean hasStearicAcid() { return stearicAcid != null; }

    // Vitamins specifics methods
    public boolean hasVitaminA() { return vitaminA != null; }
    public boolean hasRetinol() { return retinol != null; }
    public boolean hasBetaCarotene() { return betaCarotene != null; }
    public boolean hasVitaminD() { return vitaminD != null; }
    public boolean hasVitaminE() { return vitaminE != null; }
    public boolean hasVitaminK() { return vitaminK != null; }
    public boolean hasVitaminK1() { return vitaminK1 != null; }
    public boolean hasVitaminK2() { return vitaminK2 != null; }

    public boolean hasVitaminB1() { return vitaminB1 != null; }
    public boolean hasVitaminB2() { return vitaminB2 != null; }
    public boolean hasVitaminB3() { return vitaminB3 != null; }
    public boolean hasVitaminB5() { return vitaminB5 != null; }
    public boolean hasVitaminB6() { return vitaminB6 != null; }
    public boolean hasVitaminB7() { return vitaminB7 != null; }
    public boolean hasVitaminB9() { return vitaminB9 != null; }
    public boolean hasFolate() { return folate != null; }
    public boolean hasVitaminB12() { return vitaminB12 != null; }
    public boolean hasVitaminC() { return vitaminC != null; }
    public boolean hasCholine() { return choline != null; }

    // Amino acids specific methods
    public boolean hasTryptophan() { return tryptophan != null; }
    public boolean hasThreonine() { return threonine != null; }
    public boolean hasIsoleucine() { return isoleucine != null; }
    public boolean hasLeucine() { return leucine != null; }
    public boolean hasLysine() { return lysine != null; }
    public boolean hasMethionine() { return methionine != null; }
    public boolean hasCysteine() { return cysteine != null; }
    public boolean hasPhenylalanine() { return phenylalanine != null; }
    public boolean hasTyrosine() { return tyrosine != null; }
    public boolean hasValine() { return valine != null; }
    public boolean hasArginine() { return arginine != null; }
    public boolean hasHistidine() { return histidine != null; }
    public boolean hasAlanine() { return alanine != null; }
    public boolean hasAsparticAcid() { return asparticAcid != null; }
    public boolean hasGlutamicAcid() { return glutamicAcid != null; }
    public boolean hasGlycine() { return glycine != null; }
    public boolean hasProline() { return proline != null; }
    public boolean hasSerine() { return serine != null; }

    // Minerals specific methods
    public boolean hasCalcium() { return calcium != null; }
    public boolean hasPhosphorus() { return phosphorus != null; }
    public boolean hasMagnesium() { return magnesium != null; }
    public boolean hasPotassium() { return potassium != null; }
    public boolean hasSodium() { return sodium != null; }
    public boolean hasSalt() { return salt != null; }
    public boolean hasChloride() { return chloride != null; }
    public boolean hasIron() { return iron != null; }
    public boolean hasZinc() { return zinc != null; }
    public boolean hasCopper() { return copper != null; }
    public boolean hasManganese() { return manganese != null; }
    public boolean hasSelenium() { return selenium != null; }
    public boolean hasIodine() { return iodine != null; }
    public boolean hasChromium() { return chromium != null; }
    public boolean hasMolybdenum() { return molybdenum != null; }
    public boolean hasFluoride() { return fluoride != null; }

    // Other bioactive compounds
    public boolean hasCholesterol() { return cholesterol != null; }
    public boolean hasCaffeine() { return caffeine != null; }
    public boolean hasTheobromine() { return theobromine != null; }
    public boolean hasTaurine() { return taurine != null; }
    public boolean hasOrganicAcids() { return organicAcids != null; }
    public boolean hasAsh() { return ash != null; }

    // ========== CALCULATION METHODS ==========

    /**
     * Calculate nutrition for a specific amount
     * @param amountInGrams The amount in grams (or ml for liquids)
     * @return New Nutrition object with scaled values
     */
    public Nutrition calculateForAmount(double amountInGrams) {
        if (amountInGrams <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }

        double multiplier = amountInGrams / 100.0;
        return scale(multiplier);
    }

    /**
     * Scale all nutrition values by a multiplier
     */
    public Nutrition scale(double multiplier) {
        if (multiplier <= 0) {
            throw new IllegalArgumentException("Multiplier must be positive");
        }

        Nutrition scaled = new Nutrition(this);

        // Scale all non-null values
        if (energyKj != null) scaled.energyKj = energyKj * multiplier;
        if (energyKcal != null) scaled.energyKcal = energyKcal * multiplier;
        if (proteins != null) scaled.proteins = proteins * multiplier;
        if (carbohydrates != null) scaled.carbohydrates = carbohydrates * multiplier;
        if (fat != null) scaled.fat = fat * multiplier;
        if (fiber != null) scaled.fiber = fiber * multiplier;
        if (alcohol != null) scaled.alcohol = alcohol * multiplier;
        if (water != null) scaled.water = water * multiplier;
        if (sugars != null) scaled.sugars = sugars * multiplier;
        if (starch != null) scaled.starch = starch * multiplier;
        if (polyols != null) scaled.polyols = polyols * multiplier;
        if (glucose != null) scaled.glucose = glucose * multiplier;
        if (fructose != null) scaled.fructose = fructose * multiplier;
        if (sucrose != null) scaled.sucrose = sucrose * multiplier;
        if (lactose != null) scaled.lactose = lactose * multiplier;
        if (maltose != null) scaled.maltose = maltose * multiplier;
        if (galactose != null) scaled.galactose = galactose * multiplier;
        if (saturatedFat != null) scaled.saturatedFat = saturatedFat * multiplier;
        if (monounsaturatedFat != null) scaled.monounsaturatedFat = monounsaturatedFat * multiplier;
        if (polyunsaturatedFat != null) scaled.polyunsaturatedFat = polyunsaturatedFat * multiplier;
        if (transFat != null) scaled.transFat = transFat * multiplier;
        if (omega3 != null) scaled.omega3 = omega3 * multiplier;
        if (omega6 != null) scaled.omega6 = omega6 * multiplier;
        if (omega9 != null) scaled.omega9 = omega9 * multiplier;
        if (linoleicAcid != null) scaled.linoleicAcid = linoleicAcid * multiplier;
        if (DHA != null) scaled.DHA = DHA * multiplier;
        if (EPA != null) scaled.EPA = EPA * multiplier;
        if (ALA != null) scaled.ALA = ALA * multiplier;
        if (arachidonicAcid != null) scaled.arachidonicAcid = arachidonicAcid * multiplier;
        if (gammaLinolenicAcid != null) scaled.gammaLinolenicAcid = gammaLinolenicAcid * multiplier;
        if (conjugatedLinoleicAcid != null) scaled.conjugatedLinoleicAcid = conjugatedLinoleicAcid * multiplier;
        if (butyricAcid != null) scaled.butyricAcid = butyricAcid * multiplier;
        if (caproicAcid != null) scaled.caproicAcid = caproicAcid * multiplier;
        if (caprylicAcid != null) scaled.caprylicAcid = caprylicAcid * multiplier;
        if (capricAcid != null) scaled.capricAcid = capricAcid * multiplier;
        if (lauricAcid != null) scaled.lauricAcid = lauricAcid * multiplier;
        if (myristicAcid != null) scaled.myristicAcid = myristicAcid * multiplier;
        if (palmiticAcid != null) scaled.palmiticAcid = palmiticAcid * multiplier;
        if (stearicAcid != null) scaled.stearicAcid = stearicAcid * multiplier;
        if (vitaminA != null) scaled.vitaminA = vitaminA * multiplier;
        if (retinol != null) scaled.retinol = retinol * multiplier;
        if (betaCarotene != null) scaled.betaCarotene = betaCarotene * multiplier;
        if (vitaminD != null) scaled.vitaminD = vitaminD * multiplier;
        if (vitaminE != null) scaled.vitaminE = vitaminE * multiplier;
        if (vitaminK != null) scaled.vitaminK = vitaminK * multiplier;
        if (vitaminK1 != null) scaled.vitaminK1 = vitaminK1 * multiplier;
        if (vitaminK2 != null) scaled.vitaminK2 = vitaminK2 * multiplier;
        if (vitaminB1 != null) scaled.vitaminB1 = vitaminB1 * multiplier;
        if (vitaminB2 != null) scaled.vitaminB2 = vitaminB2 * multiplier;
        if (vitaminB3 != null) scaled.vitaminB3 = vitaminB3 * multiplier;
        if (vitaminB5 != null) scaled.vitaminB5 = vitaminB5 * multiplier;
        if (vitaminB6 != null) scaled.vitaminB6 = vitaminB6 * multiplier;
        if (vitaminB7 != null) scaled.vitaminB7 = vitaminB7 * multiplier;
        if (vitaminB9 != null) scaled.vitaminB9 = vitaminB9 * multiplier;
        if (folate != null) scaled.folate = folate * multiplier;
        if (vitaminB12 != null) scaled.vitaminB12 = vitaminB12 * multiplier;
        if (vitaminC != null) scaled.vitaminC = vitaminC * multiplier;
        if (choline != null) scaled.choline = choline * multiplier;

        // Amino acids
        if (tryptophan != null) scaled.tryptophan = tryptophan * multiplier;
        if (threonine != null) scaled.threonine = threonine * multiplier;
        if (isoleucine != null) scaled.isoleucine = isoleucine * multiplier;
        if (leucine != null) scaled.leucine = leucine * multiplier;
        if (lysine != null) scaled.lysine = lysine * multiplier;
        if (methionine != null) scaled.methionine = methionine * multiplier;
        if (cysteine != null) scaled.cysteine = cysteine * multiplier;
        if (phenylalanine != null) scaled.phenylalanine = phenylalanine * multiplier;
        if (tyrosine != null) scaled.tyrosine = tyrosine * multiplier;
        if (valine != null) scaled.valine = valine * multiplier;
        if (arginine != null) scaled.arginine = arginine * multiplier;
        if (histidine != null) scaled.histidine = histidine * multiplier;
        if (alanine != null) scaled.alanine = alanine * multiplier;
        if (asparticAcid != null) scaled.asparticAcid = asparticAcid * multiplier;
        if (glutamicAcid != null) scaled.glutamicAcid = glutamicAcid * multiplier;
        if (glycine != null) scaled.glycine = glycine * multiplier;
        if (proline != null) scaled.proline = proline * multiplier;
        if (serine != null) scaled.serine = serine * multiplier;

        // Minerals
        if (calcium != null) scaled.calcium = calcium * multiplier;
        if (phosphorus != null) scaled.phosphorus = phosphorus * multiplier;
        if (magnesium != null) scaled.magnesium = magnesium * multiplier;
        if (potassium != null) scaled.potassium = potassium * multiplier;
        if (sodium != null) scaled.sodium = sodium * multiplier;
        if (salt != null) scaled.salt = salt * multiplier;
        if (chloride != null) scaled.chloride = chloride * multiplier;
        if (iron != null) scaled.iron = iron * multiplier;
        if (zinc != null) scaled.zinc = zinc * multiplier;
        if (copper != null) scaled.copper = copper * multiplier;
        if (manganese != null) scaled.manganese = manganese * multiplier;
        if (selenium != null) scaled.selenium = selenium * multiplier;
        if (iodine != null) scaled.iodine = iodine * multiplier;
        if (chromium != null) scaled.chromium = chromium * multiplier;
        if (molybdenum != null) scaled.molybdenum = molybdenum * multiplier;
        if (fluoride != null) scaled.fluoride = fluoride * multiplier;

        // Other compounds
        if (cholesterol != null) scaled.cholesterol = cholesterol * multiplier;
        if (caffeine != null) scaled.caffeine = caffeine * multiplier;
        if (theobromine != null) scaled.theobromine = theobromine * multiplier;
        if (taurine != null) scaled.taurine = taurine * multiplier;
        if (organicAcids != null) scaled.organicAcids = organicAcids * multiplier;
        if (ash != null) scaled.ash = ash * multiplier;

        scaled.dataSource = this.dataSource + " (scaled)";
        return scaled;
    }

    /**
     * Add another nutrition object to this one
     */
    public Nutrition add(Nutrition other) {
        if (other == null) return new Nutrition(this);

        Nutrition sum = new Nutrition();

        sum.energyKj = addNullable(this.energyKj, other.energyKj);
        sum.energyKcal = addNullable(this.energyKcal, other.energyKcal);
        sum.proteins = addNullable(this.proteins, other.proteins);
        sum.carbohydrates = addNullable(this.carbohydrates, other.carbohydrates);
        sum.fat = addNullable(this.fat, other.fat);
        sum.fiber = addNullable(this.fiber, other.fiber);
        sum.alcohol = addNullable(this.alcohol, other.alcohol);
        sum.water = addNullable(this.water, other.water);
        sum.sugars = addNullable(this.sugars, other.sugars);
        sum.starch = addNullable(this.starch, other.starch);
        sum.polyols = addNullable(this.polyols, other.polyols);
        sum.glucose = addNullable(this.glucose, other.glucose);
        sum.fructose = addNullable(this.fructose, other.fructose);
        sum.sucrose = addNullable(this.sucrose, other.sucrose);
        sum.lactose = addNullable(this.lactose, other.lactose);
        sum.maltose = addNullable(this.maltose, other.maltose);
        sum.galactose = addNullable(this.galactose, other.galactose);
        sum.saturatedFat = addNullable(this.saturatedFat, other.saturatedFat);
        sum.monounsaturatedFat = addNullable(this.monounsaturatedFat, other.monounsaturatedFat);
        sum.polyunsaturatedFat = addNullable(this.polyunsaturatedFat, other.polyunsaturatedFat);
        sum.transFat = addNullable(this.transFat, other.transFat);
        sum.omega3 = addNullable(this.omega3, other.omega3);
        sum.omega6 = addNullable(this.omega6, other.omega6);
        sum.omega9 = addNullable(this.omega9, other.omega9);
        sum.linoleicAcid = addNullable(this.linoleicAcid, other.linoleicAcid);
        sum.DHA = addNullable(this.DHA, other.DHA);
        sum.EPA = addNullable(this.EPA, other.EPA);
        sum.ALA = addNullable(this.ALA, other.ALA);
        sum.arachidonicAcid = addNullable(this.arachidonicAcid, other.arachidonicAcid);
        sum.gammaLinolenicAcid = addNullable(this.gammaLinolenicAcid, other.gammaLinolenicAcid);
        sum.conjugatedLinoleicAcid = addNullable(this.conjugatedLinoleicAcid, other.conjugatedLinoleicAcid);
        sum.butyricAcid = addNullable(this.butyricAcid, other.butyricAcid);
        sum.caproicAcid = addNullable(this.caproicAcid, other.caproicAcid);
        sum.caprylicAcid = addNullable(this.caprylicAcid, other.caprylicAcid);
        sum.capricAcid = addNullable(this.capricAcid, other.capricAcid);
        sum.lauricAcid = addNullable(this.lauricAcid, other.lauricAcid);
        sum.myristicAcid = addNullable(this.myristicAcid, other.myristicAcid);
        sum.palmiticAcid = addNullable(this.palmiticAcid, other.palmiticAcid);
        sum.stearicAcid = addNullable(this.stearicAcid, other.stearicAcid);
        sum.vitaminA = addNullable(this.vitaminA, other.vitaminA);
        sum.retinol = addNullable(this.retinol, other.retinol);
        sum.betaCarotene = addNullable(this.betaCarotene, other.betaCarotene);
        sum.vitaminD = addNullable(this.vitaminD, other.vitaminD);
        sum.vitaminE = addNullable(this.vitaminE, other.vitaminE);
        sum.vitaminK = addNullable(this.vitaminK, other.vitaminK);
        sum.vitaminK1 = addNullable(this.vitaminK1, other.vitaminK1);
        sum.vitaminK2 = addNullable(this.vitaminK2, other.vitaminK2);
        sum.vitaminB1 = addNullable(this.vitaminB1, other.vitaminB1);
        sum.vitaminB2 = addNullable(this.vitaminB2, other.vitaminB2);
        sum.vitaminB3 = addNullable(this.vitaminB3, other.vitaminB3);
        sum.vitaminB5 = addNullable(this.vitaminB5, other.vitaminB5);
        sum.vitaminB6 = addNullable(this.vitaminB6, other.vitaminB6);
        sum.vitaminB7 = addNullable(this.vitaminB7, other.vitaminB7);
        sum.vitaminB9 = addNullable(this.vitaminB9, other.vitaminB9);
        sum.folate = addNullable(this.folate, other.folate);
        sum.vitaminB12 = addNullable(this.vitaminB12, other.vitaminB12);
        sum.vitaminC = addNullable(this.vitaminC, other.vitaminC);
        sum.choline = addNullable(this.choline, other.choline);

        // Amino acids
        sum.tryptophan = addNullable(this.tryptophan, other.tryptophan);
        sum.threonine = addNullable(this.threonine, other.threonine);
        sum.isoleucine = addNullable(this.isoleucine, other.isoleucine);
        sum.leucine = addNullable(this.leucine, other.leucine);
        sum.lysine = addNullable(this.lysine, other.lysine);
        sum.methionine = addNullable(this.methionine, other.methionine);
        sum.cysteine = addNullable(this.cysteine, other.cysteine);
        sum.phenylalanine = addNullable(this.phenylalanine, other.phenylalanine);
        sum.tyrosine = addNullable(this.tyrosine, other.tyrosine);
        sum.valine = addNullable(this.valine, other.valine);
        sum.arginine = addNullable(this.arginine, other.arginine);
        sum.histidine = addNullable(this.histidine, other.histidine);
        sum.alanine = addNullable(this.alanine, other.alanine);
        sum.asparticAcid = addNullable(this.asparticAcid, other.asparticAcid);
        sum.glutamicAcid = addNullable(this.glutamicAcid, other.glutamicAcid);
        sum.glycine = addNullable(this.glycine, other.glycine);
        sum.proline = addNullable(this.proline, other.proline);
        sum.serine = addNullable(this.serine, other.serine);

        // Minerals
        sum.calcium = addNullable(this.calcium, other.calcium);
        sum.phosphorus = addNullable(this.phosphorus, other.phosphorus);
        sum.magnesium = addNullable(this.magnesium, other.magnesium);
        sum.potassium = addNullable(this.potassium, other.potassium);
        sum.sodium = addNullable(this.sodium, other.sodium);
        sum.salt = addNullable(this.salt, other.salt);
        sum.chloride = addNullable(this.chloride, other.chloride);
        sum.iron = addNullable(this.iron, other.iron);
        sum.zinc = addNullable(this.zinc, other.zinc);
        sum.copper = addNullable(this.copper, other.copper);
        sum.manganese = addNullable(this.manganese, other.manganese);
        sum.selenium = addNullable(this.selenium, other.selenium);
        sum.iodine = addNullable(this.iodine, other.iodine);
        sum.chromium = addNullable(this.chromium, other.chromium);
        sum.molybdenum = addNullable(this.molybdenum, other.molybdenum);
        sum.fluoride = addNullable(this.fluoride, other.fluoride);

        // Other compounds
        sum.cholesterol = addNullable(this.cholesterol, other.cholesterol);
        sum.caffeine = addNullable(this.caffeine, other.caffeine);
        sum.theobromine = addNullable(this.theobromine, other.theobromine);
        sum.taurine = addNullable(this.taurine, other.taurine);
        sum.organicAcids = addNullable(this.organicAcids, other.organicAcids);
        sum.ash = addNullable(this.ash, other.ash);

        sum.dataSource = "calculated";
        return sum;
    }

    private Double addNullable(Double a, Double b) {
        if (a == null && b == null) return null;
        if (a == null) return b;
        if (b == null) return a;
        return a + b;
    }

    /**
     * Calculate completeness percentage
     */
    public float calculateCompleteness() {
        int totalFields = 0;
        int filledFields = 0;

        // Check essential nutrients
        totalFields++; if (energyKcal != null) filledFields++;
        totalFields++; if (proteins != null) filledFields++;
        totalFields++; if (carbohydrates != null) filledFields++;
        totalFields++; if (fat != null) filledFields++;
        totalFields++; if (fiber != null) filledFields++;
        totalFields++; if (sugars != null) filledFields++;
        totalFields++; if (saturatedFat != null) filledFields++;
        totalFields++; if (salt != null || sodium != null) filledFields++;

        this.completeness = totalFields > 0 ?
                (float) filledFields / totalFields * 100 : 0;

        return this.completeness;
    }

    // ========== GETTERS AND SETTERS ==========

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

    public Double getSugars() { return sugars; }
    public void setSugars(Double sugars) { this.sugars = sugars; }

    public Double getStarch() { return starch; }
    public void setStarch(Double starch) { this.starch = starch; }

    public Double getPolyols() { return polyols; }
    public void setPolyols(Double polyols) { this.polyols = polyols; }

    // Fats
    public Double getFat() { return fat; }
    public void setFat(Double fat) { this.fat = fat; }

    public Double getSaturatedFat() { return saturatedFat; }
    public void setSaturatedFat(Double saturatedFat) { this.saturatedFat = saturatedFat; }

    public Double getMonounsaturatedFat() { return monounsaturatedFat; }
    public void setMonounsaturatedFat(Double monounsaturatedFat) { this.monounsaturatedFat = monounsaturatedFat; }

    public Double getPolyunsaturatedFat() { return polyunsaturatedFat; }
    public void setPolyunsaturatedFat(Double polyunsaturatedFat) { this.polyunsaturatedFat = polyunsaturatedFat; }

    public Double getTransFat() { return transFat; }
    public void setTransFat(Double transFat) { this.transFat = transFat; }

    // Omega fatty acids
    public Double getOmega3() { return omega3; }
    public void setOmega3(Double omega3) { this.omega3 = omega3; }

    public Double getOmega6() { return omega6; }
    public void setOmega6(Double omega6) { this.omega6 = omega6; }

    public Double getOmega9() { return omega9; }
    public void setOmega9(Double omega9) { this.omega9 = omega9; }

    public Double getLinoleicAcid() { return linoleicAcid; }
    public void setLinoleicAcid(Double linoleicAcid) { this.linoleicAcid = linoleicAcid; }

    public Double getDHA() { return DHA; }
    public void setDHA(Double DHA) { this.DHA = DHA; }

    public Double getEPA() { return EPA; }
    public void setEPA(Double EPA) { this.EPA = EPA; }

    public Double getALA() { return ALA; }
    public void setALA(Double ALA) { this.ALA = ALA; }

    public Double getArachidonicAcid() { return arachidonicAcid; }
    public void setArachidonicAcid(Double arachidonicAcid) { this.arachidonicAcid = arachidonicAcid; }

    public Double getGammaLinolenicAcid() { return gammaLinolenicAcid; }
    public void setGammaLinolenicAcid(Double gammaLinolenicAcid) { this.gammaLinolenicAcid = gammaLinolenicAcid; }

    public Double getConjugatedLinoleicAcid() { return conjugatedLinoleicAcid; }
    public void setConjugatedLinoleicAcid(Double conjugatedLinoleicAcid) { this.conjugatedLinoleicAcid = conjugatedLinoleicAcid; }

    // Specific saturated fatty acids
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

    // Other nutrients
    public Double getFiber() { return fiber; }
    public void setFiber(Double fiber) { this.fiber = fiber; }

    public Double getAlcohol() { return alcohol; }
    public void setAlcohol(Double alcohol) { this.alcohol = alcohol; }

    public Double getWater() { return water; }
    public void setWater(Double water) { this.water = water; }

    public Double getSalt() { return salt; }
    public void setSalt(Double salt) { this.salt = salt; }

    public Double getSodium() { return sodium; }
    public void setSodium(Double sodium) { this.sodium = sodium; }

    // Minerals
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

    // Vitamins
    public Double getVitaminA() { return vitaminA; }
    public void setVitaminA(Double vitaminA) { this.vitaminA = vitaminA; }

    public Double getRetinol() { return retinol; }
    public void setRetinol(Double retinol) { this.retinol = retinol; }

    public Double getBetaCarotene() { return betaCarotene; }
    public void setBetaCarotene(Double betaCarotene) { this.betaCarotene = betaCarotene; }

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

    public Double getCholine() { return choline; }
    public void setCholine(Double choline) { this.choline = choline; }

    // Amino acids getters and setters
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

    public Double getCysteine() { return cysteine; }
    public void setCysteine(Double cysteine) { this.cysteine = cysteine; }

    public Double getPhenylalanine() { return phenylalanine; }
    public void setPhenylalanine(Double phenylalanine) { this.phenylalanine = phenylalanine; }

    public Double getTyrosine() { return tyrosine; }
    public void setTyrosine(Double tyrosine) { this.tyrosine = tyrosine; }

    public Double getValine() { return valine; }
    public void setValine(Double valine) { this.valine = valine; }

    public Double getArginine() { return arginine; }
    public void setArginine(Double arginine) { this.arginine = arginine; }

    public Double getHistidine() { return histidine; }
    public void setHistidine(Double histidine) { this.histidine = histidine; }

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

    // Sugar breakdown
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

    // Other bioactive compounds
    public Double getCholesterol() { return cholesterol; }
    public void setCholesterol(Double cholesterol) { this.cholesterol = cholesterol; }

    public Double getCaffeine() { return caffeine; }
    public void setCaffeine(Double caffeine) { this.caffeine = caffeine; }

    public Double getTheobromine() { return theobromine; }
    public void setTheobromine(Double theobromine) { this.theobromine = theobromine; }

    public Double getTaurine() { return taurine; }
    public void setTaurine(Double taurine) { this.taurine = taurine; }

    public Double getOrganicAcids() { return organicAcids; }
    public void setOrganicAcids(Double organicAcids) { this.organicAcids = organicAcids; }

    public Double getAsh() { return ash; }
    public void setAsh(Double ash) { this.ash = ash; }

    // Metadata
    public String getDataSource() { return dataSource; }
    public void setDataSource(String dataSource) { this.dataSource = dataSource; }

    public float getCompleteness() {
        if (completeness < 0) calculateCompleteness();
        return completeness;
    }

    public void setCompleteness(float completeness) { this.completeness = completeness; }

    public float getDataCompleteness() { return dataCompleteness; }
    public void setDataCompleteness(float dataCompleteness) { this.dataCompleteness = dataCompleteness; }

    public long getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(long lastUpdated) { this.lastUpdated = lastUpdated; }

    public Map<String, Object> getSourceSpecificData() { return sourceSpecificData; }
    public void setSourceSpecificData(Map<String, Object> sourceSpecificData) {
        this.sourceSpecificData = sourceSpecificData != null ? sourceSpecificData : new HashMap<>();
    }

    // ========== EU NUTRITION THRESHOLDS & FLAGS ==========
    /**
     * EU Regulation No 1169/2011 - Nutrition thresholds
     * Used for nutrition claims and traffic light labeling
     *
     * HIGH thresholds: Above this = "High in X" warning
     * LOW thresholds: Below this = "Low in X" claim allowed
     *
     * All thresholds are per 100g (solids) or 100ml (liquids)
     * Source: EU Regulation 1169/2011 Annex XIII
     */

    // Fat thresholds
    public static final double HIGH_FAT_THRESHOLD_PER_100G = 17.5;
    public static final double LOW_FAT_THRESHOLD_PER_100G = 3.0;

    // Saturated fat thresholds
    public static final double HIGH_SATURATED_FAT_THRESHOLD_PER_100G = 5.0;
    public static final double LOW_SATURATED_FAT_THRESHOLD_PER_100G = 1.5;

    // Sugar thresholds
    public static final double HIGH_SUGAR_THRESHOLD_PER_100G = 22.5;
    public static final double LOW_SUGAR_THRESHOLD_PER_100G = 5.0;

    // Salt thresholds
    public static final double HIGH_SALT_THRESHOLD_PER_100G = 1.5;
    public static final double LOW_SALT_THRESHOLD_PER_100G = 0.3;

    // Fiber threshold (only high threshold defined in EU regulations)
    public static final double HIGH_FIBER_THRESHOLD_PER_100G = 6.0;

    // Protein threshold (only high threshold defined in EU regulations)
    public static final double HIGH_PROTEIN_THRESHOLD_PER_100G = 12.0;

    /**
     * Nutrition flag types
     * Used for UI badges and warnings
     */
    public enum NutritionFlag {
        // Warnings (red/orange in UI)
        HIGH_FAT,
        HIGH_SATURATED_FAT,
        HIGH_SUGAR,
        HIGH_SALT,

        // Positives (green in UI)
        LOW_FAT,
        LOW_SATURATED_FAT,
        LOW_SUGAR,
        LOW_SALT,
        HIGH_FIBER,
        HIGH_PROTEIN
    }

    // ========== FAT FLAGS ==========

    /**
     * Check if fat content is high
     * EU threshold: >17.5g per 100g
     *
     * @return true if fat is high
     */
    public boolean hasHighFat() {
        return fat != null && fat > HIGH_FAT_THRESHOLD_PER_100G;
    }

    /**
     * Check if fat content is low
     * EU threshold: ≤3g per 100g
     *
     * @return true if fat is low
     */
    public boolean hasLowFat() {
        return fat != null && fat <= LOW_FAT_THRESHOLD_PER_100G;
    }

    /**
     * Get fat level: "HIGH", "MODERATE", "LOW", or null if unknown
     */
    public String getFatLevel() {
        if (fat == null) return null;
        if (fat > HIGH_FAT_THRESHOLD_PER_100G) return "HIGH";
        if (fat <= LOW_FAT_THRESHOLD_PER_100G) return "LOW";
        return "MODERATE";
    }

    // ========== SATURATED FAT FLAGS ==========

    /**
     * Check if saturated fat content is high
     * EU threshold: >5g per 100g
     *
     * @return true if saturated fat is high
     */
    public boolean hasHighSaturatedFat() {
        return saturatedFat != null && saturatedFat > HIGH_SATURATED_FAT_THRESHOLD_PER_100G;
    }

    /**
     * Check if saturated fat content is low
     * EU threshold: ≤1.5g per 100g
     *
     * @return true if saturated fat is low
     */
    public boolean hasLowSaturatedFat() {
        return saturatedFat != null && saturatedFat <= LOW_SATURATED_FAT_THRESHOLD_PER_100G;
    }

    /**
     * Get saturated fat level: "HIGH", "MODERATE", "LOW", or null if unknown
     */
    public String getSaturatedFatLevel() {
        if (saturatedFat == null) return null;
        if (saturatedFat > HIGH_SATURATED_FAT_THRESHOLD_PER_100G) return "HIGH";
        if (saturatedFat <= LOW_SATURATED_FAT_THRESHOLD_PER_100G) return "LOW";
        return "MODERATE";
    }

    // ========== SUGAR FLAGS ==========

    /**
     * Check if sugar content is high
     * EU threshold: >22.5g per 100g
     *
     * @return true if sugars are high
     */
    public boolean hasHighSugar() {
        return sugars != null && sugars > HIGH_SUGAR_THRESHOLD_PER_100G;
    }

    /**
     * Check if sugar content is low
     * EU threshold: ≤5g per 100g
     *
     * @return true if sugars are low
     */
    public boolean hasLowSugar() {
        return sugars != null && sugars <= LOW_SUGAR_THRESHOLD_PER_100G;
    }

    /**
     * Get sugar level: "HIGH", "MODERATE", "LOW", or null if unknown
     */
    public String getSugarLevel() {
        if (sugars == null) return null;
        if (sugars > HIGH_SUGAR_THRESHOLD_PER_100G) return "HIGH";
        if (sugars <= LOW_SUGAR_THRESHOLD_PER_100G) return "LOW";
        return "MODERATE";
    }

    // ========== SALT FLAGS ==========

    /**
     * Check if salt content is high
     * EU threshold: >1.5g per 100g
     *
     * @return true if salt is high
     */
    public boolean hasHighSalt() {
        return salt != null && salt > HIGH_SALT_THRESHOLD_PER_100G;
    }

    /**
     * Check if salt content is low
     * EU threshold: ≤0.3g per 100g
     *
     * @return true if salt is low
     */
    public boolean hasLowSalt() {
        return salt != null && salt <= LOW_SALT_THRESHOLD_PER_100G;
    }

    /**
     * Get salt level: "HIGH", "MODERATE", "LOW", or null if unknown
     */
    public String getSaltLevel() {
        if (salt == null) return null;
        if (salt > HIGH_SALT_THRESHOLD_PER_100G) return "HIGH";
        if (salt <= LOW_SALT_THRESHOLD_PER_100G) return "LOW";
        return "MODERATE";
    }

    // ========== FIBER FLAGS ==========

    /**
     * Check if fiber content is high (source of fiber)
     * EU threshold: ≥6g per 100g
     *
     * @return true if fiber is high
     */
    public boolean hasHighFiber() {
        return fiber != null && fiber >= HIGH_FIBER_THRESHOLD_PER_100G;
    }

    /**
     * Check if fiber content is very high (high in fiber)
     * EU threshold: ≥12g per 100g (double the normal threshold)
     *
     * @return true if fiber is very high
     */
    public boolean hasVeryHighFiber() {
        return fiber != null && fiber >= (HIGH_FIBER_THRESHOLD_PER_100G * 2);
    }

    /**
     * Get fiber level: "VERY_HIGH", "HIGH", "MODERATE", "LOW", or null if unknown
     */
    public String getFiberLevel() {
        if (fiber == null) return null;
        if (fiber >= HIGH_FIBER_THRESHOLD_PER_100G * 2) return "VERY_HIGH";
        if (fiber >= HIGH_FIBER_THRESHOLD_PER_100G) return "HIGH";
        if (fiber >= 3.0) return "MODERATE";
        return "LOW";
    }

    // ========== PROTEIN FLAGS ==========

    /**
     * Check if protein content is high (source of protein)
     * EU threshold: ≥12g per 100g
     *
     * @return true if protein is high
     */
    public boolean hasHighProtein() {
        return proteins != null && proteins >= HIGH_PROTEIN_THRESHOLD_PER_100G;
    }

    /**
     * Check if protein content is very high (high in protein)
     * EU threshold: ≥24g per 100g (double the normal threshold)
     *
     * @return true if protein is very high
     */
    public boolean hasVeryHighProtein() {
        return proteins != null && proteins >= (HIGH_PROTEIN_THRESHOLD_PER_100G * 2);
    }

    /**
     * Get protein level: "VERY_HIGH", "HIGH", "MODERATE", "LOW", or null if unknown
     */
    public String getProteinLevel() {
        if (proteins == null) return null;
        if (proteins >= HIGH_PROTEIN_THRESHOLD_PER_100G * 2) return "VERY_HIGH";
        if (proteins >= HIGH_PROTEIN_THRESHOLD_PER_100G) return "HIGH";
        if (proteins >= 6.0) return "MODERATE";
        return "LOW";
    }

    // ========== SUMMARY METHODS ==========

    /**
     * Get all nutrition warnings (HIGH levels of bad nutrients)
     * Returns list of warning flags for UI display
     *
     * @return List of NutritionFlag warnings (HIGH_FAT, HIGH_SUGAR, etc.)
     */
    public List<NutritionFlag> getNutritionWarnings() {
        List<NutritionFlag> warnings = new ArrayList<>();

        if (hasHighFat()) warnings.add(NutritionFlag.HIGH_FAT);
        if (hasHighSaturatedFat()) warnings.add(NutritionFlag.HIGH_SATURATED_FAT);
        if (hasHighSugar()) warnings.add(NutritionFlag.HIGH_SUGAR);
        if (hasHighSalt()) warnings.add(NutritionFlag.HIGH_SALT);

        return warnings;
    }

    /**
     * Get all nutrition positives (LOW bad nutrients or HIGH good nutrients)
     * Returns list of positive flags for UI display
     *
     * @return List of NutritionFlag positives (LOW_FAT, HIGH_FIBER, etc.)
     */
    public List<NutritionFlag> getNutritionPositives() {
        List<NutritionFlag> positives = new ArrayList<>();

        // Low levels of things to limit
        if (hasLowFat()) positives.add(NutritionFlag.LOW_FAT);
        if (hasLowSaturatedFat()) positives.add(NutritionFlag.LOW_SATURATED_FAT);
        if (hasLowSugar()) positives.add(NutritionFlag.LOW_SUGAR);
        if (hasLowSalt()) positives.add(NutritionFlag.LOW_SALT);

        // High levels of good things
        if (hasHighFiber()) positives.add(NutritionFlag.HIGH_FIBER);
        if (hasHighProtein()) positives.add(NutritionFlag.HIGH_PROTEIN);

        return positives;
    }

    /**
     * Get all nutrition flags (both warnings and positives)
     * Useful for complete nutrition analysis
     *
     * @return List of all NutritionFlags that apply
     */
    public List<NutritionFlag> getAllNutritionFlags() {
        List<NutritionFlag> allFlags = new ArrayList<>();
        allFlags.addAll(getNutritionWarnings());
        allFlags.addAll(getNutritionPositives());
        return allFlags;
    }

    /**
     * Check if this product has any nutrition warnings
     * Quick check for UI to show warning indicator
     *
     * @return true if any warnings exist
     */
    public boolean hasAnyWarnings() {
        return hasHighFat() || hasHighSaturatedFat() || hasHighSugar() || hasHighSalt();
    }

    /**
     * Check if this product has any nutrition positives
     * Quick check for UI to show positive indicator
     *
     * @return true if any positives exist
     */
    public boolean hasAnyPositives() {
        return hasLowFat() || hasLowSaturatedFat() || hasLowSugar() || hasLowSalt() ||
                hasHighFiber() || hasHighProtein();
    }

    /**
     * Get nutrition quality score (0-100)
     * Higher score = better nutrition profile
     *
     * Calculation:
     * - Start at 50 (neutral)
     * - Subtract 5 for each warning (high fat, sugar, salt, etc.)
     * - Add 5 for each positive (low fat, high fiber, etc.)
     * - Clamp to 0-100 range
     *
     * @return Quality score 0-100
     */
    public int getNutritionQualityScore() {
        int score = 50; // Start neutral

        // Penalties for warnings
        score -= getNutritionWarnings().size() * 5;

        // Bonuses for positives
        score += getNutritionPositives().size() * 5;

        // Additional bonus for very high fiber/protein
        if (hasVeryHighFiber()) score += 5;
        if (hasVeryHighProtein()) score += 5;

        // Clamp to 0-100
        return Math.max(0, Math.min(100, score));
    }

    /**
     * Get nutrition quality rating: "EXCELLENT", "GOOD", "FAIR", "POOR", or "UNKNOWN"
     * Based on quality score
     *
     * @return Quality rating as string
     */
    public String getNutritionQualityRating() {
        // Check if we have enough data to rate
        if (fat == null && sugars == null && salt == null && proteins == null && fiber == null) {
            return "UNKNOWN";
        }

        int score = getNutritionQualityScore();

        if (score >= 70) return "EXCELLENT";
        if (score >= 55) return "GOOD";
        if (score >= 40) return "FAIR";
        return "POOR";
    }

    // ========== DISPLAY HELPERS ==========

    /**
     * Get display-friendly string for a nutrient value
     */
    public String getDisplayValue(Double value, String unit) {
        if (value == null) {
            return "-";  // Unknown
        } else if (value == 0.0) {
            return "0" + unit;  // Confirmed zero
        } else if (value < 0.01) {
            return "<0.01" + unit;  // Trace amounts
        } else if (value < 1) {
            return String.format("%.2f%s", value, unit);
        } else if (value < 10) {
            return String.format("%.1f%s", value, unit);
        } else {
            return String.format("%.0f%s", value, unit);
        }
    }

    @Override
    public String toString() {
        return String.format("Nutrition{calories=%s, protein=%s, carbs=%s, fat=%s, completeness=%.1f%%}",
                getDisplayValue(energyKcal, "kcal"),
                getDisplayValue(proteins, "g"),
                getDisplayValue(carbohydrates, "g"),
                getDisplayValue(fat, "g"),
                getCompleteness());
    }

    /**
     * NutrientCategory - Logical grouping of nutrients for display organization
     */
    public enum NutrientCategory {
        /**
         * Macronutrients - Energy and basic building blocks
         * Includes: Energy (kJ/kcal), Proteins, Carbohydrates, Fat, Fiber, Alcohol, Water
         * Plus all their breakdowns (sugars, fatty acids, etc.) shown via indentation
         */
        MACROS("nutrition_category_macronutrients", 1),

        /**
         * Vitamins - Fat-soluble and water-soluble vitamins
         * Includes: A, D, E, K, B-complex, C, etc.
         */
        VITAMINS("nutrition_category_vitamins", 2),

        /**
         * Minerals - Major and trace minerals
         * Includes: Calcium, Iron, Zinc, Sodium, etc.
         */
        MINERALS("nutrition_category_minerals", 3),

        /**
         * Amino Acids - Essential, semi-essential, and non-essential
         * Includes: All 18 amino acids
         */
        AMINO_ACIDS("nutrition_category_amino_acids", 4),

        /**
         * Other Compounds - Bioactive compounds and other nutrients
         * Includes: Cholesterol, Caffeine, Theobromine, etc.
         */
        OTHER("nutrition_category_other", 5);

        private final String stringKey;      // For translation lookup
        private final int displayOrder;      // Order to display categories

        NutrientCategory(String stringKey, int displayOrder) {
            this.stringKey = stringKey;
            this.displayOrder = displayOrder;
        }

        public String getStringKey() {
            return stringKey;
        }

        public int getDisplayOrder() {
            return displayOrder;
        }
    }

    /**
     * NutrientInfo - Complete metadata for each nutrient
     *
     * This enum serves as the single source of truth for:
     * - Field names (for reflection/getter access)
     * - String resource keys (for i18n)
     * - Units (g, mg, µg, kcal, kJ)
     * - Categories (for grouping)
     * - Mandatory status (EU regulations)
     * - Display order
     * - Indentation level (for visual hierarchy)
     *
     * USAGE EXAMPLE:
     *   for (NutrientInfo info : NutrientInfo.values()) {
     *       Double value = info.getValue(nutrition);
     *       if (info.isMandatory() || value != null) {
     *           displayRow(info.getStringKey(), value, info.getUnit());
     *       }
     *   }
     */
    /**
     * NutrientInfo - Complete metadata for each nutrient
     *
     * SCIENTIFICALLY ACCURATE HIERARCHY (EU Regulation 1169/2011):
     *
     * CARBOHYDRATES:
     *   Total Carbohydrates = Sugars + Starch + Polyols
     *   - Sugars (simple carbs, isMandatory)
     *     - Glucose, Fructose, Sucrose, etc. (optional breakdown)
     *   - Starch (complex carbs, optional)
     *   - Polyols (sugar alcohols, optional)
     *
     * FATS:
     *   Total Fat = Saturated + Monounsaturated + Polyunsaturated + Trans
     *   - Saturated fats (isMandatory)
     *     - Specific saturated fatty acids (optional)
     *   - Monounsaturated fats (optional)
     *     - Omega-9 (optional)
     *   - Polyunsaturated fats (optional)
     *     - Omega-3 (ALA, EPA, DHA)
     *     - Omega-6 (Linoleic acid, etc.)
     *   - Trans fats (optional)
     *
     * PHASE 2 FIXES APPLIED:
     * 1. ENERGY_KJ: isMandatory=true (was false)
     * 2. SUGARS: category=MACROS (was SUGARS)
     * 3. STARCH: indent=1, category=MACROS (was indent=0, category=SUGARS)
     * 4. POLYOLS: indent=1, category=MACROS (was indent=0, category=SUGARS)
     * 5. Specific sugars: indent=2 (was indent=1)
     * 6. OMEGA3: indent=2 (was indent=1) - under polyunsaturated
     * 7. OMEGA6: indent=2 (was indent=1) - under polyunsaturated
     * 8. OMEGA9: indent=2 (was indent=1) - under monounsaturated
     * 9. Specific omega-3/6 acids: indent=3 (was indent=2)
     * 10. CLA moved under trans fat where it belongs
     */
    public enum NutrientInfo {

        // ========================================
        // CATEGORY 1: MACRONUTRIENTS
        // ========================================
        // Order: 0-49
        // All energy and macronutrients with their hierarchical breakdowns

        ENERGY_KJ("energyKj", "nutrient_energy_kj", "kJ", NutrientCategory.MACROS, true, 0, 0),
        ENERGY_KCAL("energyKcal", "nutrient_energy_kcal", "kcal", NutrientCategory.MACROS, true, 1, 0),
        PROTEINS("proteins", "nutrient_proteins", "g", NutrientCategory.MACROS, true, 2, 0),
        FIBER("fiber", "nutrient_fiber", "g", NutrientCategory.MACROS, true, 3, 0),

        CARBOHYDRATES("carbohydrates", "nutrient_carbohydrates", "g", NutrientCategory.MACROS, true, 4, 0),
        // Children of CARBOHYDRATES - shown indented (indent=1)
        SUGARS("sugars", "nutrient_sugars", "g", NutrientCategory.MACROS, true, 5, 1),
        // Children of SUGARS - shown double-indented (indent=2)
        GLUCOSE("glucose", "nutrient_glucose", "g", NutrientCategory.MACROS, false, 6, 2),
        FRUCTOSE("fructose", "nutrient_fructose", "g", NutrientCategory.MACROS, false, 7, 2),
        SUCROSE("sucrose", "nutrient_sucrose", "g", NutrientCategory.MACROS, false, 8, 2),
        LACTOSE("lactose", "nutrient_lactose", "g", NutrientCategory.MACROS, false, 9, 2),
        MALTOSE("maltose", "nutrient_maltose", "g", NutrientCategory.MACROS, false, 10, 2),
        GALACTOSE("galactose", "nutrient_galactose", "g", NutrientCategory.MACROS, false, 11, 2),
        // Children of CARBOHYDRATES - shown indented (indent=1)
        STARCH("starch", "nutrient_starch", "g", NutrientCategory.MACROS, false, 12, 1),
        POLYOLS("polyols", "nutrient_polyols", "g", NutrientCategory.MACROS, false, 13, 1),

        FAT("fat", "nutrient_fat", "g", NutrientCategory.MACROS, true, 14, 0),
        // Children of FAT - shown indented (indent=1)
        SATURATED_FAT("saturatedFat", "nutrient_saturated_fat", "g", NutrientCategory.MACROS, true, 15, 1),
        // Children of SATURATED_FAT - shown double-indented (indent=2)
        BUTYRIC_ACID("butyricAcid", "nutrient_butyric_acid", "g", NutrientCategory.MACROS, false, 16, 2),
        CAPROIC_ACID("caproicAcid", "nutrient_caproic_acid", "g", NutrientCategory.MACROS, false, 17, 2),
        CAPRYLIC_ACID("caprylicAcid", "nutrient_caprylic_acid", "g", NutrientCategory.MACROS, false, 18, 2),
        CAPRIC_ACID("capricAcid", "nutrient_capric_acid", "g", NutrientCategory.MACROS, false, 19, 2),
        LAURIC_ACID("lauricAcid", "nutrient_lauric_acid", "g", NutrientCategory.MACROS, false, 20, 2),
        MYRISTIC_ACID("myristicAcid", "nutrient_myristic_acid", "g", NutrientCategory.MACROS, false, 21, 2),
        PALMITIC_ACID("palmiticAcid", "nutrient_palmitic_acid", "g", NutrientCategory.MACROS, false, 22, 2),
        STEARIC_ACID("stearicAcid", "nutrient_stearic_acid", "g", NutrientCategory.MACROS, false, 23, 2),
        // Children of FAT - shown indented (indent=1)
        MONOUNSATURATED_FAT("monounsaturatedFat", "nutrient_monounsaturated_fat", "g", NutrientCategory.MACROS, false, 24, 1),
        // Children of MONOUNSATURATED_FAT - shown double-indented (indent=2)
        OMEGA9("omega9", "nutrient_omega9", "g", NutrientCategory.MACROS, false, 25, 2),
        // Children of FAT - shown indented (indent=1)
        POLYUNSATURATED_FAT("polyunsaturatedFat", "nutrient_polyunsaturated_fat", "g", NutrientCategory.MACROS, false, 26, 1),
        // Children of POLYUNSATURATED_FAT - shown double-indented (indent=2)
        OMEGA3("omega3", "nutrient_omega3", "g", NutrientCategory.MACROS, false, 27, 2),
        // Children of OMEGA3 - shown triple-indented (indent=3)
        ALA("ALA", "nutrient_ala", "mg", NutrientCategory.MACROS, false, 28, 3),
        EPA("EPA", "nutrient_epa", "mg", NutrientCategory.MACROS, false, 29, 3),
        DHA("DHA", "nutrient_dha", "mg", NutrientCategory.MACROS, false, 30, 3),
        // Children of POLYUNSATURATED_FAT - shown double-indented (indent=2)
        OMEGA6("omega6", "nutrient_omega6", "g", NutrientCategory.MACROS, false, 31, 2),
        // Children of OMEGA6 - shown triple-indented (indent=3)
        LINOLEIC_ACID("linoleicAcid", "nutrient_linoleic_acid", "g", NutrientCategory.MACROS, false, 32, 3),
        ARACHIDONIC_ACID("arachidonicAcid", "nutrient_arachidonic_acid", "mg", NutrientCategory.MACROS, false, 33, 3),
        GAMMA_LINOLENIC_ACID("gammaLinolenicAcid", "nutrient_gamma_linolenic_acid", "mg", NutrientCategory.MACROS, false, 34, 3),
        // Children of FAT - shown indented (indent=1)
        TRANS_FAT("transFat", "nutrient_trans_fat", "g", NutrientCategory.MACROS, false, 35, 1),
        // Child of TRANS_FAT - shown double-indented (indent=2)
        CONJUGATED_LINOLEIC_ACID("conjugatedLinoleicAcid", "nutrient_conjugated_linoleic_acid", "mg", NutrientCategory.MACROS, false, 36, 2),

        WATER("water", "nutrient_water", "g", NutrientCategory.MACROS, false, 37, 0),
        ALCOHOL("alcohol", "nutrient_alcohol", "g", NutrientCategory.MACROS, false, 38, 0),

        // ========================================
        // CATEGORY 2: VITAMINS
        // ========================================
        // Order: 50-68
        // All vitamins at indent=0 (no sub-categories)

        // ========== FAT-SOLUBLE VITAMINS (Order: 50-57) ==========
        VITAMIN_A("vitaminA", "nutrient_vitamin_a", "µg", NutrientCategory.VITAMINS, false, 50, 0),
        RETINOL("retinol", "nutrient_retinol", "µg", NutrientCategory.VITAMINS, false, 51, 0),
        BETA_CAROTENE("betaCarotene", "nutrient_beta_carotene", "µg", NutrientCategory.VITAMINS, false, 52, 0),
        VITAMIN_D("vitaminD", "nutrient_vitamin_d", "µg", NutrientCategory.VITAMINS, false, 53, 0),
        VITAMIN_E("vitaminE", "nutrient_vitamin_e", "mg", NutrientCategory.VITAMINS, false, 54, 0),
        VITAMIN_K("vitaminK", "nutrient_vitamin_k", "µg", NutrientCategory.VITAMINS, false, 55, 0),
        VITAMIN_K1("vitaminK1", "nutrient_vitamin_k1", "µg", NutrientCategory.VITAMINS, false, 56, 0),
        VITAMIN_K2("vitaminK2", "nutrient_vitamin_k2", "µg", NutrientCategory.VITAMINS, false, 57, 0),

        // ========== WATER-SOLUBLE VITAMINS - B-COMPLEX (Order: 58-66) ==========
        VITAMIN_B1("vitaminB1", "nutrient_vitamin_b1", "mg", NutrientCategory.VITAMINS, false, 58, 0),
        VITAMIN_B2("vitaminB2", "nutrient_vitamin_b2", "mg", NutrientCategory.VITAMINS, false, 59, 0),
        VITAMIN_B3("vitaminB3", "nutrient_vitamin_b3", "mg", NutrientCategory.VITAMINS, false, 60, 0),
        VITAMIN_B5("vitaminB5", "nutrient_vitamin_b5", "mg", NutrientCategory.VITAMINS, false, 61, 0),
        VITAMIN_B6("vitaminB6", "nutrient_vitamin_b6", "mg", NutrientCategory.VITAMINS, false, 62, 0),
        VITAMIN_B7("vitaminB7", "nutrient_vitamin_b7", "µg", NutrientCategory.VITAMINS, false, 63, 0),
        VITAMIN_B9("vitaminB9", "nutrient_vitamin_b9", "µg", NutrientCategory.VITAMINS, false, 64, 0),
        FOLATE("folate", "nutrient_folate", "µg", NutrientCategory.VITAMINS, false, 65, 0),
        VITAMIN_B12("vitaminB12", "nutrient_vitamin_b12", "µg", NutrientCategory.VITAMINS, false, 66, 0),

        // ========== OTHER WATER-SOLUBLE VITAMINS (Order: 67-68) ==========
        VITAMIN_C("vitaminC", "nutrient_vitamin_c", "mg", NutrientCategory.VITAMINS, false, 67, 0),
        CHOLINE("choline", "nutrient_choline", "mg", NutrientCategory.VITAMINS, false, 68, 0),

        // ========================================
        // CATEGORY 3: MINERALS
        // ========================================
        // Order: 80-95
        // All minerals at indent=0 (no sub-categories)

        // ========== MAJOR MINERALS (Order: 80-86) ==========
        CALCIUM("calcium", "nutrient_calcium", "mg", NutrientCategory.MINERALS, false, 80, 0),
        PHOSPHORUS("phosphorus", "nutrient_phosphorus", "mg", NutrientCategory.MINERALS, false, 81, 0),
        MAGNESIUM("magnesium", "nutrient_magnesium", "mg", NutrientCategory.MINERALS, false, 82, 0),
        POTASSIUM("potassium", "nutrient_potassium", "mg", NutrientCategory.MINERALS, false, 83, 0),
        SODIUM("sodium", "nutrient_sodium", "mg", NutrientCategory.MINERALS, false, 84, 0),
        SALT("salt", "nutrient_salt", "g", NutrientCategory.MINERALS, true, 85, 0),
        CHLORIDE("chloride", "nutrient_chloride", "mg", NutrientCategory.MINERALS, false, 86, 0),

        // ========== TRACE MINERALS (Order: 87-95) ==========
        IRON("iron", "nutrient_iron", "mg", NutrientCategory.MINERALS, false, 87, 0),
        ZINC("zinc", "nutrient_zinc", "mg", NutrientCategory.MINERALS, false, 88, 0),
        COPPER("copper", "nutrient_copper", "mg", NutrientCategory.MINERALS, false, 89, 0),
        MANGANESE("manganese", "nutrient_manganese", "mg", NutrientCategory.MINERALS, false, 90, 0),
        SELENIUM("selenium", "nutrient_selenium", "µg", NutrientCategory.MINERALS, false, 91, 0),
        IODINE("iodine", "nutrient_iodine", "µg", NutrientCategory.MINERALS, false, 92, 0),
        CHROMIUM("chromium", "nutrient_chromium", "µg", NutrientCategory.MINERALS, false, 93, 0),
        MOLYBDENUM("molybdenum", "nutrient_molybdenum", "µg", NutrientCategory.MINERALS, false, 94, 0),
        FLUORIDE("fluoride", "nutrient_fluoride", "µg", NutrientCategory.MINERALS, false, 95, 0),

        // ========================================
        // CATEGORY 4: AMINO ACIDS
        // ========================================
        // Order: 100-117
        // All amino acids at indent=0 (no sub-categories)

        // ========== ESSENTIAL AMINO ACIDS (Order: 100-108) ==========
        TRYPTOPHAN("tryptophan", "nutrient_tryptophan", "mg", NutrientCategory.AMINO_ACIDS, false, 100, 0),
        THREONINE("threonine", "nutrient_threonine", "mg", NutrientCategory.AMINO_ACIDS, false, 101, 0),
        ISOLEUCINE("isoleucine", "nutrient_isoleucine", "mg", NutrientCategory.AMINO_ACIDS, false, 102, 0),
        LEUCINE("leucine", "nutrient_leucine", "mg", NutrientCategory.AMINO_ACIDS, false, 103, 0),
        LYSINE("lysine", "nutrient_lysine", "mg", NutrientCategory.AMINO_ACIDS, false, 104, 0),
        METHIONINE("methionine", "nutrient_methionine", "mg", NutrientCategory.AMINO_ACIDS, false, 105, 0),
        PHENYLALANINE("phenylalanine", "nutrient_phenylalanine", "mg", NutrientCategory.AMINO_ACIDS, false, 106, 0),
        VALINE("valine", "nutrient_valine", "mg", NutrientCategory.AMINO_ACIDS, false, 107, 0),
        HISTIDINE("histidine", "nutrient_histidine", "mg", NutrientCategory.AMINO_ACIDS, false, 108, 0),

        // ========== SEMI-ESSENTIAL AMINO ACIDS (Order: 109-111) ==========
        CYSTEINE("cysteine", "nutrient_cysteine", "mg", NutrientCategory.AMINO_ACIDS, false, 109, 0),
        TYROSINE("tyrosine", "nutrient_tyrosine", "mg", NutrientCategory.AMINO_ACIDS, false, 110, 0),
        ARGININE("arginine", "nutrient_arginine", "mg", NutrientCategory.AMINO_ACIDS, false, 111, 0),

        // ========== NON-ESSENTIAL AMINO ACIDS (Order: 112-117) ==========
        ALANINE("alanine", "nutrient_alanine", "mg", NutrientCategory.AMINO_ACIDS, false, 112, 0),
        ASPARTIC_ACID("asparticAcid", "nutrient_aspartic_acid", "mg", NutrientCategory.AMINO_ACIDS, false, 113, 0),
        GLUTAMIC_ACID("glutamicAcid", "nutrient_glutamic_acid", "mg", NutrientCategory.AMINO_ACIDS, false, 114, 0),
        GLYCINE("glycine", "nutrient_glycine", "mg", NutrientCategory.AMINO_ACIDS, false, 115, 0),
        PROLINE("proline", "nutrient_proline", "mg", NutrientCategory.AMINO_ACIDS, false, 116, 0),
        SERINE("serine", "nutrient_serine", "mg", NutrientCategory.AMINO_ACIDS, false, 117, 0),

        // ========================================
        // CATEGORY 5: OTHER COMPOUNDS
        // ========================================
        // Order: 120-125
        // Bioactive compounds and other nutrients at indent=0

        CHOLESTEROL("cholesterol", "nutrient_cholesterol", "mg", NutrientCategory.OTHER, false, 120, 0),
        CAFFEINE("caffeine", "nutrient_caffeine", "mg", NutrientCategory.OTHER, false, 121, 0),
        THEOBROMINE("theobromine", "nutrient_theobromine", "mg", NutrientCategory.OTHER, false, 122, 0),
        TAURINE("taurine", "nutrient_taurine", "mg", NutrientCategory.OTHER, false, 123, 0),
        ORGANIC_ACIDS("organicAcids", "nutrient_organic_acids", "g", NutrientCategory.OTHER, false, 124, 0),
        ASH("ash", "nutrient_ash", "g", NutrientCategory.OTHER, false, 125, 0);

        // ========== ENUM FIELDS AND CONSTRUCTOR (unchanged) ==========

        private final String fieldName;
        private final String stringKey;
        private final String unit;
        private final NutrientCategory category;
        private final boolean isMandatory;
        private final int displayOrder;
        private final int indentLevel;

        NutrientInfo(String fieldName, String stringKey, String unit,
                     NutrientCategory category, boolean isMandatory,
                     int displayOrder, int indentLevel) {
            this.fieldName = fieldName;
            this.stringKey = stringKey;
            this.unit = unit;
            this.category = category;
            this.isMandatory = isMandatory;
            this.displayOrder = displayOrder;
            this.indentLevel = indentLevel;
        }

        // Getters
        public String getFieldName() { return fieldName; }
        public String getStringKey() { return stringKey; }
        public String getUnit() { return unit; }
        public NutrientCategory getCategory() { return category; }
        public boolean isMandatory() { return isMandatory; }
        public int getDisplayOrder() { return displayOrder; }
        public int getIndentLevel() { return indentLevel; }

        public Double getValue(Nutrition nutrition) {
            if (nutrition == null) return null;
            try {
                String getterName = "get" + fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
                java.lang.reflect.Method getter = Nutrition.class.getMethod(getterName);
                return (Double) getter.invoke(nutrition);
            } catch (Exception e) {
                return getValueFallback(nutrition);
            }
        }

        private Double getValueFallback(Nutrition nutrition) {
            switch (this) {
                case ENERGY_KJ: return nutrition.getEnergyKj();
                case ENERGY_KCAL: return nutrition.getEnergyKcal();
                case PROTEINS: return nutrition.getProteins();
                case CARBOHYDRATES: return nutrition.getCarbohydrates();
                case FAT: return nutrition.getFat();
                case SATURATED_FAT: return nutrition.getSaturatedFat();
                case SUGARS: return nutrition.getSugars();
                case FIBER: return nutrition.getFiber();
                case SALT: return nutrition.getSalt();
                case SODIUM: return nutrition.getSodium();
                case CHOLESTEROL: return nutrition.getCholesterol();
                default: return null;
            }
        }

        public static List<NutrientInfo> getMandatoryNutrients() {
            List<NutrientInfo> mandatory = new ArrayList<>();
            for (NutrientInfo info : values()) {
                if (info.isMandatory) {
                    mandatory.add(info);
                }
            }
            return mandatory;
        }

        public static List<NutrientInfo> getByCategory(NutrientCategory category) {
            List<NutrientInfo> nutrients = new ArrayList<>();
            for (NutrientInfo info : values()) {
                if (info.category == category) {
                    nutrients.add(info);
                }
            }
            return nutrients;
        }

        public static List<NutrientInfo> getAvailableNutrients(Nutrition nutrition) {
            List<NutrientInfo> available = new ArrayList<>();
            for (NutrientInfo info : values()) {
                if (info.getValue(nutrition) != null) {
                    available.add(info);
                }
            }
            return available;
        }

        /**
         * Get short display name for compact displays (banners, charts)
         * Uses localized string resources for proper internationalization
         *
         * @param context Android context for string resource access
         * @return Localized shortened nutrient name suitable for small spaces
         */
        public String getShortDisplayName(Context context) {
            // Map each nutrient to its short string resource
            int stringResId = 0;

            switch (this) {
                case ENERGY_KCAL:
                case ENERGY_KJ:
                    stringResId = R.string.nutrient_energy_short;
                    break;

                case PROTEINS:
                    stringResId = R.string.nutrient_proteins_short;
                    break;

                case CARBOHYDRATES:
                    stringResId = R.string.nutrient_carbohydrates_short;
                    break;

                case SUGARS:
                    stringResId = R.string.nutrient_sugars_short;
                    break;

                case FAT:
                    stringResId = R.string.nutrient_fat_short;
                    break;

                case SATURATED_FAT:
                    stringResId = R.string.nutrient_saturated_fat_short;
                    break;

                case FIBER:
                    stringResId = R.string.nutrient_fiber_short;
                    break;

                case SALT:
                    stringResId = R.string.nutrient_salt_short;
                    break;

                case SODIUM:
                    stringResId = R.string.nutrient_sodium_short;
                    break;

                case CALCIUM:
                    stringResId = R.string.nutrient_calcium_short;
                    break;

                case IRON:
                    stringResId = R.string.nutrient_iron_short;
                    break;

                case MAGNESIUM:
                    stringResId = R.string.nutrient_magnesium_short;
                    break;

                case PHOSPHORUS:
                    stringResId = R.string.nutrient_phosphorus_short;
                    break;

                case POTASSIUM:
                    stringResId = R.string.nutrient_potassium_short;
                    break;

                case VITAMIN_A:
                    stringResId = R.string.nutrient_vitamin_a_short;
                    break;

                case VITAMIN_B1:
                    stringResId = R.string.nutrient_vitamin_b1_short;
                    break;

                case VITAMIN_B2:
                    stringResId = R.string.nutrient_vitamin_b2_short;
                    break;

                case VITAMIN_B3:
                    stringResId = R.string.nutrient_vitamin_b3_short;
                    break;

                case VITAMIN_B5:
                    stringResId = R.string.nutrient_vitamin_b5_short;
                    break;

                case VITAMIN_B6:
                    stringResId = R.string.nutrient_vitamin_b6_short;
                    break;

                case VITAMIN_B9:
                    stringResId = R.string.nutrient_vitamin_b9_short;
                    break;

                case VITAMIN_B12:
                    stringResId = R.string.nutrient_vitamin_b12_short;
                    break;

                case VITAMIN_C:
                    stringResId = R.string.nutrient_vitamin_c_short;
                    break;

                case VITAMIN_D:
                    stringResId = R.string.nutrient_vitamin_d_short;
                    break;

                case VITAMIN_E:
                    stringResId = R.string.nutrient_vitamin_e_short;
                    break;

                case VITAMIN_K:
                    stringResId = R.string.nutrient_vitamin_k_short;
                    break;

                case ZINC:
                    stringResId = R.string.nutrient_zinc_short;
                    break;

                case COPPER:
                    stringResId = R.string.nutrient_copper_short;
                    break;

                case MANGANESE:
                    stringResId = R.string.nutrient_manganese_short;
                    break;

                case SELENIUM:
                    stringResId = R.string.nutrient_selenium_short;
                    break;

                case CHLORIDE:
                    stringResId = R.string.nutrient_chloride_short;
                    break;

                case ALCOHOL:
                    stringResId = R.string.nutrient_alcohol_short;
                    break;

                case ORGANIC_ACIDS:
                    stringResId = R.string.nutrient_organic_acids_short;
                    break;

                case POLYOLS:
                    stringResId = R.string.nutrient_polyols_short;
                    break;

                case STARCH:
                    stringResId = R.string.nutrient_starch_short;
                    break;

                case WATER:
                    stringResId = R.string.nutrient_water_short;
                    break;
            }

            // If string resource found, return localized string
            if (stringResId != 0) {
                try {
                    return context.getString(stringResId);
                } catch (Exception e) {
                    // Fall through to fallback
                }
            }

            // Fallback: use field name if no string resource
            return getFieldName();
        }
    }

    /**
     * Get all nutrients as a map with their values
     * Useful for iteration in UI components
     *
     * @return Map of NutrientInfo to their values (including nulls)
     */
    public Map<NutrientInfo, Double> getAllNutrientsMap() {
        Map<NutrientInfo, Double> map = new java.util.LinkedHashMap<>();
        for (NutrientInfo info : NutrientInfo.values()) {
            map.put(info, info.getValue(this));
        }
        return map;
    }

    /**
     * Get only nutrients with non-null values
     *
     * @return Map of NutrientInfo to their values (nulls excluded)
     */
    public Map<NutrientInfo, Double> getAvailableNutrientsMap() {
        Map<NutrientInfo, Double> map = new java.util.LinkedHashMap<>();
        for (NutrientInfo info : NutrientInfo.values()) {
            Double value = info.getValue(this);
            if (value != null) {
                map.put(info, value);
            }
        }
        return map;
    }
    // ========== VITAMIN D SUBFORMS ==========
    public Double getVitaminD2() { return vitaminD2; }
    public void setVitaminD2(Double vitaminD2) { this.vitaminD2 = vitaminD2; }

    public Double getVitaminD3() { return vitaminD3; }
    public void setVitaminD3(Double vitaminD3) { this.vitaminD3 = vitaminD3; }

    // ========== FOLATE SUBFORMS ==========
    public Double getIntrinsicFolate() { return intrinsicFolate; }
    public void setIntrinsicFolate(Double intrinsicFolate) { this.intrinsicFolate = intrinsicFolate; }

    public Double getFolicAcid() { return folicAcid; }
    public void setFolicAcid(Double folicAcid) { this.folicAcid = folicAcid; }

    // ========== DATA QUALITY ==========
    public String getDataConfidenceCode() { return dataConfidenceCode; }
    public void setDataConfidenceCode(String dataConfidenceCode) {
        this.dataConfidenceCode = dataConfidenceCode;
    }


}