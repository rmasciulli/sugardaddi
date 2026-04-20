package li.masciul.sugardaddi.data.sources.usda.mappers;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import li.masciul.sugardaddi.core.enums.DataSource;
import li.masciul.sugardaddi.core.models.FoodProduct;
import li.masciul.sugardaddi.core.models.Nutrition;
import li.masciul.sugardaddi.core.models.SourceIdentifier;
import li.masciul.sugardaddi.data.sources.usda.USDAConstants;
import li.masciul.sugardaddi.data.sources.usda.api.dto.FDCFoodDetail;
import li.masciul.sugardaddi.data.sources.usda.api.dto.FDCSearchFood;
import li.masciul.sugardaddi.data.sources.usda.api.dto.FDCSearchResponse;

/**
 * USDAMapper — Maps USDA FoodData Central DTOs to FoodProduct domain models.
 *
 * TWO MAPPING PATHS
 * =================
 * 1. mapSearchResponse() — FDCSearchResponse → List<FoodProduct>
 *    Lightweight: name, category, fdcId, subset of nutrients from search result.
 *    Used by USDADataSource.search() and autocomplete().
 *
 * 2. mapFoodDetail() — FDCFoodDetail → FoodProduct
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
 * Energy:        1008=kcal, 2047=kJ
 * Macros:        1003=protein, 1004=fat, 1005=carbs, 1079=fiber
 * Carbs detail:  2000=sugars, 1050=sugars-alt, 1009=starch
 * Fat detail:    1258=saturated, 1292=monounsat, 1293=polyunsat, 1257=trans
 * Minerals:      1093=sodium, 1087=calcium, 1089=iron, 1090=magnesium,
 *                1091=phosphorus, 1092=potassium, 1095=zinc
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
     * @param language  User language (ignored — FDC is EN-only)
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
        product.setDataSource(DataSource.USDA);

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
        // No barcode — FDC uses fdcId, not EAN/UPC

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
            Log.w(TAG, "Invalid FDCFoodDetail — fdcId=" + detail.getFdcId());
            return null;
        }

        FoodProduct product = new FoodProduct();

        // ── Identification ────────────────────────────────────────────────────
        String fdcIdStr = String.valueOf(detail.getFdcId());
        product.setOriginalId(fdcIdStr);
        product.setSourceIdentifier(new SourceIdentifier(USDAConstants.SOURCE_ID, fdcIdStr));
        product.setDataSource(DataSource.USDA);

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
        Double kcal  = food.getEnergyKcal();
        Double prot  = food.getProtein();
        Double fat   = food.getFat();
        Double carbs = food.getCarbohydrates();

        if (kcal == null && prot == null && fat == null && carbs == null) {
            return null;
        }

        Nutrition n = new Nutrition();
        n.setEnergyKcal(kcal);
        n.setProteins(prot);
        n.setFat(fat);
        n.setCarbohydrates(carbs);
        return n;
    }

    /**
     * Map the full nutrient list from a food detail response.
     * Uses FDC nutrient IDs (stable INFOODS identifiers) for mapping,
     * not string matching — more reliable than name patterns.
     */
    @NonNull
    private static Nutrition mapDetailNutrition(@NonNull FDCFoodDetail detail) {
        Nutrition n = new Nutrition();

        for (FDCFoodDetail.FDCFoodNutrient fn : detail.getFoodNutrients()) {
            int id     = fn.getNutrientId();
            double val = fn.getAmount();
            if (val <= 0) continue; // Skip zero/negative values

            mapNutrientById(n, id, val);
        }

        return n;
    }

    /**
     * Map a single nutrient value by FDC nutrient ID into a Nutrition object.
     * IDs are INFOODS-based and stable across USDA releases.
     *
     * Units: all values are in the nutrient's native unit (g, mg, µg, kcal).
     * Sodium is in mg — convert to g for our model (same as Ciqual convention).
     */
    public static void mapNutrientById(@NonNull Nutrition n, int id, double val) {
        switch (id) {
            // Energy
            case 1008: n.setEnergyKcal(val); break;
            case 2047: n.setEnergyKj(val);   break;

            // Macronutrients (g)
            case 1003: n.setProteins(val);       break;
            case 1004: n.setFat(val);             break;
            case 1005: n.setCarbohydrates(val);   break;
            case 1079: n.setFiber(val);           break;
            case 1051: n.setWater(val);           break;

            // Carbohydrate detail (g)
            case 2000: // fall-through
            case 1050: n.setSugars(val);          break;
            case 1009: n.setStarch(val);          break;

            // Fat detail (g)
            case 1258: n.setSaturatedFat(val);       break;
            case 1292: n.setMonounsaturatedFat(val); break;
            case 1293: n.setPolyunsaturatedFat(val); break;
            case 1257: n.setTransFat(val);            break;
            case 1253: n.setCholesterol(val);         break;

            // Specific omega fatty acids (g)
            case 1404: n.setALA(val);  break; // 18:3 n-3 (ALA)
            case 1405: n.setEPA(val);  break; // 20:5 n-3 (EPA)  — rare in Foundation
            case 1406: n.setDHA(val);  break; // 22:6 n-3 (DHA)

            // Minerals — mg in FDC, converted to g for our model
            case 1093: n.setSodium(val / 1000.0);    break; // mg → g
            case 1087: n.setCalcium(val / 1000.0);   break;
            case 1089: n.setIron(val / 1000.0);      break;
            case 1090: n.setMagnesium(val / 1000.0); break;
            case 1091: n.setPhosphorus(val / 1000.0);break;
            case 1092: n.setPotassium(val / 1000.0); break;
            case 1095: n.setZinc(val / 1000.0);      break;

            // Compute salt from sodium: salt = sodium × 2.5 (if not already set)
            // We do this after the loop in mapDetailNutrition if salt remains null.

            // Fat-soluble vitamins
            case 1106: n.setVitaminA(val);    break; // µg RAE
            case 1114: n.setVitaminD(val);    break; // µg
            case 1109: n.setVitaminE(val);    break; // mg
            case 1185: n.setVitaminK1(val);   break; // µg

            // Water-soluble vitamins
            case 1165: n.setVitaminB1(val);   break; // mg thiamin
            case 1166: n.setVitaminB2(val);   break; // mg riboflavin
            case 1167: n.setVitaminB3(val);   break; // mg niacin
            case 1170: n.setVitaminB5(val);   break; // mg pantothenic acid
            case 1175: n.setVitaminB6(val);   break; // mg
            case 1177: n.setVitaminB9(val);   break; // µg DFE folate
            case 1178: n.setVitaminB12(val);  break; // µg
            case 1162: n.setVitaminC(val);    break; // mg

            default:
                break; // Unmapped nutrient — silently skip
        }
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    /**
     * Convert USDA ALL-CAPS description to sentence case.
     * "BROCCOLI, RAW" → "Broccoli, raw"
     * Preserves acronyms after the first word (heuristic: if a word is ≤3 chars
     * and all caps, leave it — handles "RAW", "NFS", "NS").
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