package li.masciul.sugardaddi.data.sources.usda.mappers;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import li.masciul.sugardaddi.core.enums.DataConfidence;
import li.masciul.sugardaddi.core.enums.DataSourceType;
import li.masciul.sugardaddi.core.models.FoodProduct;
import li.masciul.sugardaddi.core.models.Nutrition;
import li.masciul.sugardaddi.core.models.SourceIdentifier;
import li.masciul.sugardaddi.core.utils.ProductUrlBuilder;
import li.masciul.sugardaddi.data.sources.usda.USDAConstants;
import li.masciul.sugardaddi.data.sources.usda.api.dto.FDCFoodDetail;
import li.masciul.sugardaddi.data.sources.usda.api.dto.FDCSearchFood;
import li.masciul.sugardaddi.data.sources.usda.api.dto.FDCSearchResponse;

/**
 * USDAMapper - Maps USDA FoodData Central DTOs to FoodProduct domain models.
 *
 * TWO MAPPING PATHS
 * =================
 * 1. mapSearchResponse() - FDCSearchResponse → List<FoodProduct>
 *    Lightweight: name, category, fdcId, subset of nutrients from search result.
 *    Used by USDADataSource.search() and autocomplete().
 *
 * 2. mapFoodDetail() - FDCFoodDetail → FoodProduct
 *    Comprehensive: full nutrient profile from the detail endpoint.
 *    Used by USDADataSource.getProduct().
 *
 * LANGUAGE NOTE
 * =============
 * FoodData Central is English-only. We store names in English regardless
 * of the user's language preference. The language parameter is accepted
 * for API consistency but not used for field selection.
 *
 * NUTRIENT ID MAP (FDC INFOODS numbers)
 * ======================================
 * Energy:        1008=kcal, 1062=kJ; 2047 / 2048 = kcal by the Atwater general /
 *                specific factors (recent Foundation foods have only these;
 *                USDA stopped reporting kJ in 2020). See nutritionFromValues().
 * Macros:        1003=protein, 1004=fat, 1005=carbs, 1079=fiber
 * Carbs:         1005=by difference, else 1050=by summation (Foundation)
 * Sugars:        2000=Total Sugars, else 1063=Sugars, Total (Foundation)
 * Fat:           1004=Total lipid, else 1085=Total fat (NLEA) (Foundation oils)
 * Carbs detail:  1009=starch
 * Fat detail:    1258=saturated, 1292=monounsat, 1293=polyunsat, 1257=trans
 * Minerals:      1093=sodium, 1087=calcium, 1089=iron, 1090=magnesium,
 *                1091=phosphorus, 1092=potassium, 1095=zinc (all mg,
 *                stored directly - see mapNutrientById for why)
 * Vitamins:      1106=vit-A, 1114=vit-D, 1109=vit-E, 1185=vit-K1,
 *                1165=vit-B1, 1166=vit-B2, 1167=vit-B3, 1170=vit-B5,
 *                1175=vit-B6, 1177=vit-B9(folate), 1178=vit-B12, 1162=vit-C
 * Other:         1253=cholesterol, 1051=water
 */
public final class USDAMapper {

    private static final String TAG = "USDAMapper";

    // =========================================================================
    // SEARCH RESPONSE → LIST<FOODPRODUCT>
    // =========================================================================

    /**
     * Map a search response to a list of FoodProduct domain models.
     * Filters out foods with no description (rare but possible).
     *
     * @param response  FDC search API response
     * @param language  User language (ignored - FDC is EN-only)
     * @return List of FoodProduct, never null, may be empty
     */
    @NonNull
    public static List<FoodProduct> mapSearchResponse(
            @NonNull FDCSearchResponse response,
            @NonNull String language) {

        List<FoodProduct> results = new ArrayList<>();
        for (FDCSearchFood food : response.getFoods()) {
            FoodProduct product = mapSearchFood(food, language);
            if (product != null) results.add(product);
        }

        if (li.masciul.sugardaddi.data.network.ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, "Mapped " + results.size() + "/" + response.getFoods().size()
                    + " search results");
        }

        return results;
    }

    /**
     * Map a single FDCSearchFood to a FoodProduct.
     * Returns null if the food lacks a description (not displayable).
     */
    @Nullable
    public static FoodProduct mapSearchFood(
            @NonNull FDCSearchFood food,
            @NonNull String language) {

        if (food.getDescription() == null || food.getDescription().trim().isEmpty()) {
            return null;
        }

        FoodProduct product = new FoodProduct();

        // ── Identification ────────────────────────────────────────────────────
        String fdcIdStr = String.valueOf(food.getFdcId());
        product.setOriginalId(fdcIdStr);
        product.setSourceIdentifier(new SourceIdentifier(USDAConstants.SOURCE_ID, fdcIdStr));
        product.setDataSource(DataSourceType.USDA);

        // ── Name (sentence-case the USDA ALL-CAPS descriptions) ───────────────
        // USDA descriptions are written in ALL CAPS (e.g., "BROCCOLI, RAW").
        // Convert to sentence case for readability: "Broccoli, raw"
        String name = toSentenceCase(food.getDescription().trim());
        product.setName(name, language);

        // ── Category ─────────────────────────────────────────────────────────
        if (food.getFoodCategory() != null && !food.getFoodCategory().trim().isEmpty()) {
            product.setCategoriesText(toSentenceCase(food.getFoodCategory()), language);
        }

        // ── Data completeness (based on data type quality ranking) ────────────
        product.setDataCompleteness(dataTypeCompleteness(food.getDataType()));

        // ── Partial nutrition from search result ──────────────────────────────
        Nutrition nutrition = mapSearchNutrition(food);
        if (nutrition != null) {
            product.setNutrition(nutrition);
        }

        // USDA has no product images or Nutri-Score
        // No barcode - FDC uses fdcId, not EAN/UPC

        // Computed, not API-provided - USDA has no url field of its own.
        // ProductUrlBuilder already builds this correctly (English-only,
        // FDC-id-based, no language variance to account for); relocated
        // here from ProductDetailsActivity's on-the-fly click-time call so
        // every source populates sourceUrl the same way, at mapper time.
        String sourceUrl = ProductUrlBuilder.resolveUrl(product.getSourceIdentifier(), null);
        if (sourceUrl != null) {
            product.setSourceUrl(sourceUrl, language);
        }

        return product;
    }

    // =========================================================================
    // FOOD DETAIL → FOODPRODUCT
    // =========================================================================

    /**
     * Map a full FDCFoodDetail to a FoodProduct with complete nutrition.
     * Returns null if the detail response is invalid (no fdcId or description).
     */
    @Nullable
    public static FoodProduct mapFoodDetail(
            @NonNull FDCFoodDetail detail,
            @NonNull String language) {

        if (!detail.isValid()) {
            Log.w(TAG, "Invalid FDCFoodDetail - fdcId=" + detail.getFdcId());
            return null;
        }

        FoodProduct product = new FoodProduct();

        // ── Identification ────────────────────────────────────────────────────
        String fdcIdStr = String.valueOf(detail.getFdcId());
        product.setOriginalId(fdcIdStr);
        product.setSourceIdentifier(new SourceIdentifier(USDAConstants.SOURCE_ID, fdcIdStr));
        product.setDataSource(DataSourceType.USDA);

        // ── Name ──────────────────────────────────────────────────────────────
        product.setName(toSentenceCase(detail.getDescription().trim()), language);

        // ── Category ──────────────────────────────────────────────────────────
        String category = detail.getFoodCategoryDescription();
        if (category != null && !category.trim().isEmpty()) {
            product.setCategoriesText(toSentenceCase(category), language);
        }

        // ── Data completeness ─────────────────────────────────────────────────
        product.setDataCompleteness(dataTypeCompleteness(detail.getDataType()));

        // ── Full nutrition ────────────────────────────────────────────────────
        Nutrition nutrition = mapDetailNutrition(detail);
        product.setNutrition(nutrition);

        // ── Portions: serving list in grams + typical serving (USDAPortions) ──
        List<USDAPortions.Portion> portions = toPortions(detail.getFoodPortions());
        product.setServingOptions(USDAPortions.servingOptions(portions));
        product.setServingSize(USDAPortions.typicalServing(portions));

        // ── Density: liquid-like foods only, from their volume portions ───────
        // Same USDA data as the values above, so the same confidence. It only
        // lets the screen show and log this food in ml; values stay per 100 g.
        if (USDAPortions.isLiquid(detail.getFoodCategoryDescription(), detail.getDescription())) {
            nutrition.setDensity(USDAPortions.density(portions), DataConfidence.SCIENTIFIC);
        }

        // See mapSearchFood() for the rationale - same computed, relocated call.
        String sourceUrl = ProductUrlBuilder.resolveUrl(product.getSourceIdentifier(), null);
        if (sourceUrl != null) {
            product.setSourceUrl(sourceUrl, language);
        }

        return product;
    }

    // =========================================================================
    // NUTRITION MAPPING
    // =========================================================================

    /**
     * Map partial nutrition from a search result food.
     * Only key macros are available; returns null if none are present.
     */
    @Nullable
    private static Nutrition mapSearchNutrition(@NonNull FDCSearchFood food) {
        // Same energy precedence as nutritionFromValues(): 1008, else the
        // Atwater kcal values (recent Foundation foods have no 1008).
        Double kcal  = firstPresent(food.getEnergyKcal(),
                searchValue(food, NUTRIENT_KCAL_ATWATER_GENERAL),
                searchValue(food, NUTRIENT_KCAL_ATWATER_SPECIFIC));
        Double prot  = food.getProtein();
        Double fat   = food.getFat();
        Double carbs = food.getCarbohydrates();

        if (kcal == null && prot == null && fat == null && carbs == null) {
            return null;
        }

        Nutrition n = new Nutrition();
        n.setEnergyKcal(kcal);
        if (kcal != null) n.setEnergyKj(kcal * KJ_PER_KCAL);
        n.setProteins(prot);
        n.setFat(fat);
        n.setCarbohydrates(carbs);
        n.setDataConfidence(DataConfidence.SCIENTIFIC);
        return n;
    }

    /**
     * Map the full nutrient list from a food detail response.
     * Uses FDC nutrient IDs (stable INFOODS identifiers) for mapping,
     * not string matching - more reliable than name patterns.
     */
    @NonNull
    private static Nutrition mapDetailNutrition(@NonNull FDCFoodDetail detail) {
        Map<Integer, Double> values = new HashMap<>();
        for (FDCFoodDetail.FDCFoodNutrient fn : detail.getFoodNutrients()) {
            int id     = fn.getNutrientId();
            Double val = fn.getAmount();
            // No amount = a group heading ("Proximates") or no value: skipped.
            // 0 is kept (see nutritionFromValues(), ZEROS); negatives dropped.
            if (id > 0 && val != null && val >= 0) values.put(id, val);
        }
        return nutritionFromValues(values);
    }

    // FDC nutrient ids handled outside mapNutrientById() (precedence rules).
    private static final int NUTRIENT_KCAL = 1008;
    private static final int NUTRIENT_KCAL_ATWATER_GENERAL = 2047;
    private static final int NUTRIENT_KCAL_ATWATER_SPECIFIC = 2048;
    private static final double KJ_PER_KCAL = 4.184;
    private static final int NUTRIENT_CARBS_BY_SUMMATION = 1050;
    private static final int NUTRIENT_SUGARS_FOUNDATION = 1063;
    private static final int NUTRIENT_FAT_NLEA = 1085;
    private static final int NUTRIENT_CYSTINE = 1216;

    /**
     * Build a Nutrition from FDC nutrient values (id -> amount, native units).
     * The ONE place both paths go through: the detail endpoint
     * (mapDetailNutrition) and the bulk import (USDAImportService), so the
     * rules below cannot drift apart again.
     *
     * ENERGY:
     * - kcal: 1008, else 2047 (Atwater general factors), else 2048 (Atwater
     *   specific factors). Recent Foundation foods have no 1008 any more.
     * - kJ: 1062 when the food has it (SR Legacy, older Foundation foods),
     *   else computed from kcal (x 4.184), as for Open Food Facts and
     *   FatSecret. USDA stopped reporting kJ in October 2020.
     *
     * SALT: FDC has only elemental sodium (1093, mg). Salt (g) = sodium x 2.5
     * / 1000, the EU labelling formula.
     *
     * FOUNDATION FALLBACKS (checked on real records, 2026-11-22): Foundation
     * foods often carry other ids than SR Legacy for the same nutrient.
     * - carbohydrates: 1005 (by difference), else 1050 (by summation).
     *   1050 used to be read as SUGARS: broccoli 747447 showed 3.8 g of sugars
     *   (its carbohydrate by summation) instead of 1.4 g.
     * - sugars: 2000, else 1063 ("Sugars, Total", Foundation's id).
     * - fat: 1004, else 1085 ("Total fat (NLEA)"): Foundation oils (olive oil
     *   748608, peanut oil 1750348) have only 1085, so they showed nothing.
     * Missing ids are never invented: a Foundation oil without energy, protein
     * or carbohydrates shows only what USDA measured.
     *
     * ZEROS: a 0 is kept. USDA's 0 is measured (or "assumed zero" by USDA,
     * e.g. fiber in milk) and Nutrition distinguishes 0 (none present) from
     * null (unknown). Only negative values are dropped.
     *
     * All USDA FDC data types (Foundation, SR Legacy, Survey) are measured or
     * compiled by USDA nutritionists: SCIENTIFIC confidence.
     */
    @NonNull
    public static Nutrition nutritionFromValues(@NonNull Map<Integer, Double> values) {
        Nutrition n = new Nutrition();
        for (Map.Entry<Integer, Double> e : values.entrySet()) {
            if (e.getKey() != null && e.getValue() != null) {
                mapNutrientById(n, e.getKey(), e.getValue());
            }
        }

        if (n.getCarbohydrates() == null) n.setCarbohydrates(values.get(NUTRIENT_CARBS_BY_SUMMATION));
        if (n.getSugars() == null) n.setSugars(values.get(NUTRIENT_SUGARS_FOUNDATION));
        if (n.getFat() == null) n.setFat(values.get(NUTRIENT_FAT_NLEA));
        // Cysteine: 1232, else cystine (1216, two cysteines bound: the form SR
        // Legacy reports, listed as "cystine/cysteine" in food tables), g -> mg.
        if (n.getCysteine() == null && values.get(NUTRIENT_CYSTINE) != null) {
            n.setCysteine(values.get(NUTRIENT_CYSTINE) * MG_PER_G);
        }

        n.setEnergyKcal(firstPresent(values.get(NUTRIENT_KCAL),
                values.get(NUTRIENT_KCAL_ATWATER_GENERAL),
                values.get(NUTRIENT_KCAL_ATWATER_SPECIFIC)));
        if (n.getEnergyKj() == null && n.getEnergyKcal() != null) {
            n.setEnergyKj(n.getEnergyKcal() * KJ_PER_KCAL);
        }

        if (n.getSalt() == null && n.getSodium() != null) {
            n.setSalt(n.getSodium() * 2.5 / 1000.0);
        }

        n.setDataConfidence(DataConfidence.SCIENTIFIC);
        return n;
    }

    /** USDA reports these in g per 100 g; Nutrition stores them in mg. */
    private static final double MG_PER_G = 1000.0;

    /**
     * Map a single nutrient value by FDC nutrient ID into a Nutrition object.
     * IDs are INFOODS-based and stable across USDA releases. Every id below was
     * checked by name and unit on real records (SR Legacy, Foundation, Survey,
     * 2026-11-22).
     *
     * UNITS: FDC values are in the nutrient's own unit (g, mg, µg, kcal), and
     * most match Nutrition's field unit. Two groups do not: individual omega
     * fatty acids (ALA, EPA, DHA, GLA, CLA) and amino acids are g in FDC and mg
     * in Nutrition (see its field comments), so they are multiplied by 1000.
     *
     * NOT HERE: kcal (1008, 2047, 2048) and the Foundation fallbacks (1050,
     * 1063, 1085, 1216) are applied by nutritionFromValues(), which knows the
     * precedence between ids.
     *
     * NOT MAPPED (no matching Nutrition field, or not the same nutrient):
     * undifferentiated groups such as "PUFA 18:3" (1270), "PUFA 20:4" (1271)
     * or "MUFA 18:1" (1268), which mix isomers (ALA, arachidonic acid, oleic
     * acid are each only part of them); "Folate, DFE" (1190, a computed
     * equivalent); IU duplicates of vitamins A and D (1104, 1110); individual
     * carotenoids, tocopherols and sterols without a field.
     */
    public static void mapNutrientById(@NonNull Nutrition n, int id, double val) {
        switch (id) {
            // Energy in kJ (kcal: see nutritionFromValues())
            case 1062: n.setEnergyKj(val);              break;

            // Macronutrients (g)
            case 1003: n.setProteins(val);              break;
            case 1004: n.setFat(val);                   break;
            case 1005: n.setCarbohydrates(val);         break;
            case 1079: n.setFiber(val);                 break;
            case 1051: n.setWater(val);                 break;
            case 1018: n.setAlcohol(val);               break;
            case 1007: n.setAsh(val);                   break;

            // Carbohydrate detail (g)
            case 2000: n.setSugars(val);                break;
            case 1009: n.setStarch(val);                break;
            case 1011: n.setGlucose(val);               break;
            case 1012: n.setFructose(val);              break;
            case 1010: n.setSucrose(val);               break;
            case 1013: n.setLactose(val);               break;
            case 1014: n.setMaltose(val);               break;
            case 1075: n.setGalactose(val);             break;

            // Fat detail (g)
            case 1258: n.setSaturatedFat(val);          break;
            case 1292: n.setMonounsaturatedFat(val);    break;
            case 1293: n.setPolyunsaturatedFat(val);    break;
            case 1257: n.setTransFat(val);              break;
            case 1259: n.setButyricAcid(val);           break; // SFA 4:0
            case 1260: n.setCaproicAcid(val);           break; // SFA 6:0
            case 1261: n.setCaprylicAcid(val);          break; // SFA 8:0
            case 1262: n.setCapricAcid(val);            break; // SFA 10:0
            case 1263: n.setLauricAcid(val);            break; // SFA 12:0
            case 1264: n.setMyristicAcid(val);          break; // SFA 14:0
            case 1265: n.setPalmiticAcid(val);          break; // SFA 16:0
            case 1266: n.setStearicAcid(val);           break; // SFA 18:0
            case 1316: n.setLinoleicAcid(val);          break; // PUFA 18:2 n-6 c,c (g)

            // Individual omega fatty acids: g in FDC, mg in Nutrition
            case 1404: n.setALA(val * MG_PER_G);                    break; // PUFA 18:3 n-3 c,c,c
            case 1278: n.setEPA(val * MG_PER_G);                    break; // PUFA 20:5 n-3 (1405 is 20:3 n-3)
            case 1272: n.setDHA(val * MG_PER_G);                    break; // PUFA 22:6 n-3 (1406 is 20:3 n-6)
            case 1321: n.setGammaLinolenicAcid(val * MG_PER_G);     break; // PUFA 18:3 n-6 c,c,c
            case 1311: n.setConjugatedLinoleicAcid(val * MG_PER_G); break; // PUFA 18:2 CLAs

            case 1253: n.setCholesterol(val);           break; // mg

            // Minerals (mg, except selenium and iodine in µg) - FDC's units
            // match Nutrition's own convention, no conversion
            case 1093: n.setSodium(val);                break;
            case 1087: n.setCalcium(val);               break;
            case 1089: n.setIron(val);                  break;
            case 1090: n.setMagnesium(val);             break;
            case 1091: n.setPhosphorus(val);            break;
            case 1092: n.setPotassium(val);             break;
            case 1095: n.setZinc(val);                  break;
            case 1098: n.setCopper(val);                break;
            case 1101: n.setManganese(val);             break;
            case 1103: n.setSelenium(val);              break; // µg
            case 1100: n.setIodine(val);                break; // µg

            // Fat-soluble vitamins
            case 1106: n.setVitaminA(val);              break; // µg RAE
            case 1105: n.setRetinol(val);               break; // µg
            case 1107: n.setBetaCarotene(val);          break; // µg
            case 1114: n.setVitaminD(val);              break; // µg (D2 + D3)
            case 1111: n.setVitaminD2(val);             break; // µg
            case 1112: n.setVitaminD3(val);             break; // µg
            case 1109: n.setVitaminE(val);              break; // mg alpha-tocopherol
            case 1185: n.setVitaminK1(val);             break; // µg phylloquinone
            case 1183: n.setVitaminK2(val);             break; // µg menaquinone-4

            // Water-soluble vitamins
            case 1165: n.setVitaminB1(val);             break; // mg thiamin
            case 1166: n.setVitaminB2(val);             break; // mg riboflavin
            case 1167: n.setVitaminB3(val);             break; // mg niacin
            case 1170: n.setVitaminB5(val);             break; // mg pantothenic acid
            case 1175: n.setVitaminB6(val);             break; // mg
            case 1176: n.setVitaminB7(val);             break; // µg biotin
            case 1177: n.setVitaminB9(val);             break; // µg folate, total
            case 1187: n.setIntrinsicFolate(val);       break; // µg folate, food
            case 1186: n.setFolicAcid(val);             break; // µg folic acid (added)
            case 1178: n.setVitaminB12(val);            break; // µg
            case 1162: n.setVitaminC(val);              break; // mg
            case 1180: n.setCholine(val);               break; // mg

            // Amino acids: g in FDC, mg in Nutrition
            case 1210: n.setTryptophan(val * MG_PER_G);     break;
            case 1211: n.setThreonine(val * MG_PER_G);      break;
            case 1212: n.setIsoleucine(val * MG_PER_G);     break;
            case 1213: n.setLeucine(val * MG_PER_G);        break;
            case 1214: n.setLysine(val * MG_PER_G);         break;
            case 1215: n.setMethionine(val * MG_PER_G);     break;
            case 1232: n.setCysteine(val * MG_PER_G);       break; // 1216 cystine: fallback
            case 1217: n.setPhenylalanine(val * MG_PER_G);  break;
            case 1218: n.setTyrosine(val * MG_PER_G);       break;
            case 1219: n.setValine(val * MG_PER_G);         break;
            case 1220: n.setArginine(val * MG_PER_G);       break;
            case 1221: n.setHistidine(val * MG_PER_G);      break;
            case 1222: n.setAlanine(val * MG_PER_G);        break;
            case 1223: n.setAsparticAcid(val * MG_PER_G);   break;
            case 1224: n.setGlutamicAcid(val * MG_PER_G);   break;
            case 1225: n.setGlycine(val * MG_PER_G);        break;
            case 1226: n.setProline(val * MG_PER_G);        break;
            case 1227: n.setSerine(val * MG_PER_G);         break;

            // Other (mg)
            case 1057: n.setCaffeine(val);              break;
            case 1058: n.setTheobromine(val);           break;

            default:
                break; // Unmapped nutrient - silently skip
        }
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    /** The detail response's portions in the shared form read by USDAPortions. */
    @NonNull
    private static List<USDAPortions.Portion> toPortions(
            @NonNull List<FDCFoodDetail.FDCFoodPortion> dtos) {
        List<USDAPortions.Portion> portions = new ArrayList<>();
        for (FDCFoodDetail.FDCFoodPortion dto : dtos) {
            if (dto == null) continue;
            USDAPortions.Portion p = new USDAPortions.Portion();
            p.gramWeight = dto.getGramWeight();
            p.amount = dto.getAmount();
            p.sequenceNumber = dto.getSequenceNumber();
            p.unitName = dto.getMeasureUnitName();
            p.modifier = dto.getModifier();
            p.portionDescription = dto.getPortionDescription();
            portions.add(p);
        }
        return portions;
    }

    /** The first non-null value, or null. */
    @Nullable
    private static Double firstPresent(@Nullable Double... values) {
        for (Double v : values) if (v != null) return v;
        return null;
    }

    /** A search result's value for one nutrient id, or null (zero counts as absent). */
    @Nullable
    private static Double searchValue(@NonNull FDCSearchFood food, int nutrientId) {
        FDCSearchFood.FDCSearchNutrient n = food.getNutrientById(nutrientId);
        Double v = n != null ? n.getValue() : null;
        return v != null && v > 0 ? v : null;
    }

    /**
     * Convert USDA ALL-CAPS description to sentence case.
     * "BROCCOLI, RAW" → "Broccoli, raw"
     * Preserves acronyms after the first word (heuristic: if a word is ≤3 chars
     * and all caps, leave it - handles "RAW", "NFS", "NS").
     * Simple approach: lowercase everything, capitalise first character.
     */
    @NonNull
    public static String toSentenceCase(@NonNull String input) {
        if (input.isEmpty()) return input;
        String lower = input.toLowerCase();
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    /**
     * Assign a data completeness score based on FDC data type.
     * Foundation Foods have the most exhaustive nutrient measurements;
     * SR Legacy is comprehensive; Survey is good but less precise.
     */
    private static float dataTypeCompleteness(@Nullable String dataType) {
        if (dataType == null) return 0.5f;
        switch (dataType) {
            case "Foundation":     return 0.95f;
            case "SR Legacy":     return 0.85f;
            case "Survey (FNDDS)": return 0.70f;
            default:              return 0.50f;
        }
    }

    private USDAMapper() {
        throw new UnsupportedOperationException("USDAMapper is a utility class");
    }
}