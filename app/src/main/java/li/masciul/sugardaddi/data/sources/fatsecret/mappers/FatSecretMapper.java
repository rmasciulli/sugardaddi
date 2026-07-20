package li.masciul.sugardaddi.data.sources.fatsecret.mappers;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import li.masciul.sugardaddi.core.enums.DataConfidence;
import li.masciul.sugardaddi.core.enums.DataSourceType;
import li.masciul.sugardaddi.core.enums.Unit;
import li.masciul.sugardaddi.core.models.FoodPortion;
import li.masciul.sugardaddi.core.models.FoodProduct;
import li.masciul.sugardaddi.core.models.Nutrition;
import li.masciul.sugardaddi.core.models.Recipe;
import li.masciul.sugardaddi.core.models.ServingSize;
import li.masciul.sugardaddi.core.models.SourceIdentifier;
import li.masciul.sugardaddi.data.sources.fatsecret.api.dto.FoodGetResponse;
import li.masciul.sugardaddi.data.sources.fatsecret.api.dto.FoodSearchResponse;
import li.masciul.sugardaddi.data.sources.fatsecret.api.dto.RecipeGetResponse;
import li.masciul.sugardaddi.data.sources.fatsecret.api.dto.RecipeSearchResponse;

/**
 * FatSecretMapper - Maps FatSecret DTOs to FoodProduct/Recipe domain models.
 *
 * FOUR MAPPING PATHS
 * ===================
 * 1. mapFoodSearchResponse() - lightweight, from foods/search/v1. No
 *    structured nutrition (foods.search only returns a formatted summary
 *    string, e.g. "Per 100g - Calories: 22kcal | ..."); nutrition stays
 *    null here, matching how a search card looks until getProduct() is
 *    called for the full detail. Deliberate - parsing that summary string
 *    with regex was considered and rejected as too fragile (inconsistent
 *    "Per 100g"/"Per 1 serving"/"Per 157g" prefixes, locale-dependent
 *    number formats) for a foundation to build on.
 * 2. mapFoodDetail() - comprehensive, from food/v5. Real structured
 *    nutrition, normalized to per-100g.
 * 3. mapRecipeSearchResponse() - lightweight, from recipes/search/v3.
 *    Partial nutrition (calories/carbs/protein/fat only) IS available and
 *    structured here, unlike food search - populated, matching
 *    USDAMapper's search-result pattern.
 * 4. mapRecipeDetail() - comprehensive, from recipe/v2. Full nutrition,
 *    normalized to per-100g via gramsPerPortion.
 *
 * UNIT CONVENTIONS
 * ================
 * Sodium/calcium/iron/potassium/cholesterol map directly with NO unit
 * conversion - FatSecret documents these as milligrams natively, matching
 * Nutrition's own "mg per 100g/ml" field convention exactly (same
 * convention Ciqual's mapper already uses correctly - see the separate
 * fix needed in USDAMapper, which incorrectly divides by 1000).
 *
 * recipe.get's vitamin_a/vitamin_c/calcium/iron are %DV, NOT the same unit
 * as food.get's absolute values despite sharing field names - left null
 * here pending confirmation of which daily-value reference FatSecret uses
 * (pre-2016 vs current FDA values differ meaningfully for some of these).
 */
public final class FatSecretMapper {

    private static final String TAG = "FatSecretMapper";

    // ========================================================================
    // FOOD: SEARCH RESPONSE → LIST<FOODPRODUCT>
    // ========================================================================

    @NonNull
    public static List<FoodProduct> mapFoodSearchResponse(
            @Nullable FoodSearchResponse response,
            @NonNull String language) {

        List<FoodProduct> results = new ArrayList<>();
        if (response == null || response.foods == null || response.foods.food == null) {
            return results;
        }

        for (FoodSearchResponse.FoodSearchResult food : response.foods.food) {
            FoodProduct product = mapFoodSearchResult(food, language);
            if (product != null) results.add(product);
        }

        Log.d(TAG, "Mapped " + results.size() + "/" + response.foods.food.size()
                + " food search results");

        return results;
    }

    @Nullable
    public static FoodProduct mapFoodSearchResult(
            @NonNull FoodSearchResponse.FoodSearchResult food,
            @NonNull String language) {

        if (food.foodName == null || food.foodName.trim().isEmpty()) {
            return null;
        }

        FoodProduct product = new FoodProduct();

        String foodIdStr = String.valueOf(food.foodId);
        product.setOriginalId(foodIdStr);
        product.setSourceIdentifier(new SourceIdentifier(DataSourceType.FATSECRET.getId(), foodIdStr));
        product.setDataSource(DataSourceType.FATSECRET);

        String name = food.brandName != null && !food.brandName.trim().isEmpty()
                ? food.foodName.trim()  // Brand name is stored separately, not prefixed onto the name
                : food.foodName.trim();
        product.setName(name, language);

        // No structured nutrition from search results - see class Javadoc.
        // A getProduct() call (mapFoodDetail below) is required for real values.

        // Without this, dataCompleteness stays at its default and every
        // FatSecret search result fails ResultPipeline's minimum-completeness
        // quality gate outright - found via 20 items in, 0 out in production.
        product.calculateCompleteness();

        return product;
    }


    // ========================================================================
    // FOOD: DETAIL → FOODPRODUCT
    // ========================================================================

    @Nullable
    public static FoodProduct mapFoodDetail(
            @NonNull FoodGetResponse.FoodDetail detail,
            @NonNull String language) {

        if (detail.foodName == null || detail.foodName.trim().isEmpty()) {
            Log.w(TAG, "Invalid FoodGetResponse.FoodDetail - foodId=" + detail.foodId);
            return null;
        }

        FoodProduct product = new FoodProduct();

        String foodIdStr = String.valueOf(detail.foodId);
        product.setOriginalId(foodIdStr);
        product.setSourceIdentifier(new SourceIdentifier(DataSourceType.FATSECRET.getId(), foodIdStr));
        product.setDataSource(DataSourceType.FATSECRET);

        product.setName(detail.foodName.trim(), language);

        Nutrition nutrition = mapFoodServings(
                detail.servings != null ? detail.servings.serving : null);
        if (nutrition != null) {
            product.setNutrition(nutrition);
        }

        return product;
    }

    /**
     * Select the best available serving and normalize it to per-100g.
     *
     * Preference order:
     * 1. A serving already at exactly 100 g or 100 ml - no scaling needed.
     *    FatSecret often provides this as a synthetic serving_id=0 for
     *    "Brand" foods specifically.
     * 2. Any gram-based serving, scaled by 100/metricServingAmount.
     * 3. None found (e.g. only oz-based or unmeasured servings) - returns
     *    null rather than guess at a conversion.
     */
    @Nullable
    private static Nutrition mapFoodServings(@Nullable List<FoodGetResponse.FoodServing> servings) {
        if (servings == null || servings.isEmpty()) {
            return null;
        }

        FoodGetResponse.FoodServing chosen = null;
        double scale = 1.0;

        for (FoodGetResponse.FoodServing s : servings) {
            if (s.metricServingAmount == null || s.metricServingUnit == null) continue;
            boolean isGramOrMl = "g".equalsIgnoreCase(s.metricServingUnit)
                    || "ml".equalsIgnoreCase(s.metricServingUnit);
            if (isGramOrMl && s.metricServingAmount == 100.0) {
                chosen = s;
                scale = 1.0;
                break; // exact match - stop looking
            }
        }

        if (chosen == null) {
            for (FoodGetResponse.FoodServing s : servings) {
                if (s.metricServingAmount != null && s.metricServingAmount > 0
                        && "g".equalsIgnoreCase(s.metricServingUnit)) {
                    chosen = s;
                    scale = 100.0 / s.metricServingAmount;
                    break;
                }
            }
        }

        if (chosen == null) {
            Log.w(TAG, "No gram/ml-based serving found - cannot normalize to per-100g");
            return null;
        }

        Nutrition n = new Nutrition();
        n.setEnergyKcal(scaled(chosen.calories, scale));
        n.setCarbohydrates(scaled(chosen.carbohydrate, scale));
        n.setProteins(scaled(chosen.protein, scale));
        n.setFat(scaled(chosen.fat, scale));
        n.setSaturatedFat(scaled(chosen.saturatedFat, scale));
        n.setMonounsaturatedFat(scaled(chosen.monounsaturatedFat, scale));
        n.setPolyunsaturatedFat(scaled(chosen.polyunsaturatedFat, scale));
        n.setTransFat(scaled(chosen.transFat, scale));
        n.setSugars(scaled(chosen.sugar, scale));
        n.setFiber(scaled(chosen.fiber, scale));
        // mg fields - no unit conversion, FatSecret's native unit already
        // matches Nutrition's "mg per 100g/ml" convention (only the amount
        // needs scaling to reach per-100g, same as every other field here).
        n.setCholesterol(scaled(chosen.cholesterol, scale));
        n.setSodium(scaled(chosen.sodium, scale));
        n.setPotassium(scaled(chosen.potassium, scale));
        n.setVitaminA(scaled(chosen.vitaminA, scale));
        n.setVitaminC(scaled(chosen.vitaminC, scale));
        n.setCalcium(scaled(chosen.calcium, scale));
        n.setIron(scaled(chosen.iron, scale));

        // Brand foods (e.g. McDonald's) are manufacturer-declared label
        // values; Generic foods aggregate multiple contributor sources of
        // varying rigor. Neither is independently lab-verified the way
        // Ciqual/USDA's own direct integrations are, so DECLARED rather
        // than SCIENTIFIC for all FatSecret food data.
        n.setDataConfidence(DataConfidence.DECLARED);
        n.setDataSource(DataSourceType.FATSECRET.getId());

        return n;
    }

    // ========================================================================
    // RECIPE: SEARCH RESPONSE → LIST<RECIPE>
    // ========================================================================

    @NonNull
    public static List<Recipe> mapRecipeSearchResponse(
            @Nullable RecipeSearchResponse response,
            @NonNull String language) {

        List<Recipe> results = new ArrayList<>();
        if (response == null || response.recipes == null || response.recipes.recipe == null) {
            return results;
        }

        for (RecipeSearchResponse.RecipeSearchResult recipe : response.recipes.recipe) {
            Recipe mapped = mapRecipeSearchResult(recipe, language);
            if (mapped != null) results.add(mapped);
        }

        Log.d(TAG, "Mapped " + results.size() + "/" + response.recipes.recipe.size()
                + " recipe search results");

        return results;
    }

    @Nullable
    public static Recipe mapRecipeSearchResult(
            @NonNull RecipeSearchResponse.RecipeSearchResult result,
            @NonNull String language) {

        if (result.recipeName == null || result.recipeName.trim().isEmpty()) {
            return null;
        }

        Recipe recipe = new Recipe();

        String recipeIdStr = String.valueOf(result.recipeId);
        recipe.setOriginalId(recipeIdStr);
        recipe.setSourceIdentifier(new SourceIdentifier(DataSourceType.FATSECRET.getId(), recipeIdStr));
        recipe.setDataSource(DataSourceType.FATSECRET);

        recipe.setName(result.recipeName.trim(), language);
        if (result.recipeDescription != null) {
            recipe.setDescription(result.recipeDescription.trim(), language);
        }
        if (result.recipeImage != null) {
            recipe.setImageUrl(result.recipeImage);
        }

        // Partial nutrition IS structured here (unlike food search) - see class javadoc.
        if (result.recipeNutrition != null) {
            RecipeSearchResponse.RecipeNutritionSummary rn = result.recipeNutrition;
            if (rn.calories != null || rn.carbohydrate != null || rn.protein != null || rn.fat != null) {
                Nutrition n = new Nutrition();
                n.setEnergyKcal(rn.calories);
                n.setCarbohydrates(rn.carbohydrate);
                n.setProteins(rn.protein);
                n.setFat(rn.fat);
                // FatSecret's own aggregate for the whole recipe, derived from
                // its ingredients - COMPUTED, matching TheMealDB's convention
                // for the same kind of value (see DataConfidence javadoc).
                n.setDataConfidence(DataConfidence.COMPUTED);
                n.setDataSource(DataSourceType.FATSECRET.getId());
                recipe.setNutrition(n);
            }
        }

        // recipes/search/v3 gives ingredient NAMES only (no quantities, no
        // directions - that's recipe/v2's job). Mark this recipe as a
        // preview so ResultPipeline waives the instructions requirement it
        // can never satisfy from search data alone (see Recipe.isPreview
        // javadoc) - a real getRecipe() call replaces this with full detail
        // when the user actually opens it.
        recipe.setPreview(true);
        if (result.recipeIngredients != null && result.recipeIngredients.ingredient != null) {
            recipe.setPortions(mapIngredientNames(recipe, result.recipeIngredients.ingredient));
        }

        recipe.setLastUpdated(System.currentTimeMillis());
        recipe.setCreatedAt(System.currentTimeMillis());
        recipe.calculateCompleteness();

        return recipe;
    }

    /**
     * Map a bare list of ingredient name strings (recipes/search/v3's
     * ingredient shape) to FoodPortion stubs - same name-stub pattern as
     * mapRecipeIngredients() below, but for the simpler case where only a
     * name is available, no food_id/quantity/measurement.
     */
    @NonNull
    private static List<FoodPortion> mapIngredientNames(
            @NonNull Recipe recipe,
            @NonNull List<String> ingredientNames) {

        List<FoodPortion> portions = new ArrayList<>(ingredientNames.size());

        for (int i = 0; i < ingredientNames.size(); i++) {
            String ingredientName = ingredientNames.get(i);
            if (ingredientName == null || ingredientName.trim().isEmpty()) continue;

            FoodPortion portion = new FoodPortion(
                    "FOOD_PRODUCT",
                    ingredientName.trim(),
                    new ServingSize()
            );
            portion.setOrderIndex(i);
            portion.setEstimated(true);
            portion.setParentType("RECIPE");
            portion.setParentId(recipe.getId());

            portions.add(portion);
        }

        return portions;
    }

    // ========================================================================
    // RECIPE: DETAIL → RECIPE
    // ========================================================================

    @Nullable
    public static Recipe mapRecipeDetail(
            @NonNull RecipeGetResponse.RecipeDetail detail,
            @NonNull String language) {

        if (detail.recipeName == null || detail.recipeName.trim().isEmpty()) {
            Log.w(TAG, "Invalid RecipeGetResponse.RecipeDetail - recipeId=" + detail.recipeId);
            return null;
        }

        Recipe recipe = new Recipe();

        String recipeIdStr = String.valueOf(detail.recipeId);
        recipe.setOriginalId(recipeIdStr);
        recipe.setSourceIdentifier(new SourceIdentifier(DataSourceType.FATSECRET.getId(), recipeIdStr));
        recipe.setDataSource(DataSourceType.FATSECRET);

        recipe.setName(detail.recipeName.trim(), language);
        if (detail.recipeDescription != null) {
            recipe.setDescription(detail.recipeDescription.trim(), language);
        }
        if (detail.numberOfServings != null) {
            recipe.setServings(detail.numberOfServings.intValue());
        }
        if (detail.preparationTimeMin != null) {
            recipe.setPrepTimeMinutes(detail.preparationTimeMin);
        }
        if (detail.cookingTimeMin != null) {
            recipe.setCookTimeMinutes(detail.cookingTimeMin);
        }
        if (detail.recipeImages != null && detail.recipeImages.recipeImage != null
                && !detail.recipeImages.recipeImage.isEmpty()) {
            recipe.setImageUrl(detail.recipeImages.recipeImage.get(0));
        }

        // ── Directions → single instructions text ──────────────────────────
        if (detail.directions != null && detail.directions.direction != null
                && !detail.directions.direction.isEmpty()) {
            StringBuilder instructions = new StringBuilder();
            for (RecipeGetResponse.RecipeDirection d : detail.directions.direction) {
                if (d.directionDescription == null) continue;
                if (instructions.length() > 0) instructions.append("\n\n");
                instructions.append(d.directionNumber).append(". ").append(d.directionDescription);
            }
            if (instructions.length() > 0) {
                recipe.setInstructions(instructions.toString(), language);
            }
        }

        // ── Ingredients → FoodPortion stubs (same pattern as TheMealDbMapper)
        if (detail.ingredients != null && detail.ingredients.ingredient != null) {
            recipe.setPortions(mapRecipeIngredients(recipe, detail.ingredients.ingredient));
        }

        // ── Portion weight → ServingSize ────────────────────────────────────
        // Persisted with the recipe (RecipeEntity.servingSize) and used by the
        // detail screen as the smart default amount for the nutrition label.
        // Set independently of the nutrition block below: the portion weight
        // is valid on its own even when servingSizes is missing and per-100g
        // normalization is impossible.
        if (detail.gramsPerPortion != null && detail.gramsPerPortion > 0) {
            recipe.setServingSize(new ServingSize(detail.gramsPerPortion, Unit.G));
        }

        // ── Nutrition, normalized via gramsPerPortion ───────────────────────
        if (detail.servingSizes != null && detail.servingSizes.serving != null
                && detail.gramsPerPortion != null && detail.gramsPerPortion > 0) {
            Nutrition nutrition = mapRecipeServing(detail.servingSizes.serving, detail.gramsPerPortion);
            if (nutrition != null) {
                recipe.setNutrition(nutrition);
            }
        } else {
            Log.w(TAG, "Missing servingSizes or gramsPerPortion for recipe "
                    + detail.recipeId + " - cannot normalize nutrition to per-100g");
        }

        recipe.setLastUpdated(System.currentTimeMillis());
        recipe.setCreatedAt(System.currentTimeMillis());
        recipe.calculateCompleteness();

        return recipe;
    }

    /**
     * Normalize recipe.get's per-serving nutrition to per-100g using
     * gramsPerPortion. vitamin_a/vitamin_c/calcium/iron are deliberately
     * NOT mapped - see class Javadoc for why.
     */
    @Nullable
    private static Nutrition mapRecipeServing(
            @NonNull RecipeGetResponse.ServingNutrition serving,
            double gramsPerPortion) {

        double scale = 100.0 / gramsPerPortion;

        Nutrition n = new Nutrition();
        n.setEnergyKcal(scaled(serving.calories, scale));
        n.setCarbohydrates(scaled(serving.carbohydrate, scale));
        n.setProteins(scaled(serving.protein, scale));
        n.setFat(scaled(serving.fat, scale));
        n.setSaturatedFat(scaled(serving.saturatedFat, scale));
        n.setMonounsaturatedFat(scaled(serving.monounsaturatedFat, scale));
        n.setPolyunsaturatedFat(scaled(serving.polyunsaturatedFat, scale));
        n.setTransFat(scaled(serving.transFat, scale));
        n.setSugars(scaled(serving.sugar, scale));
        n.setFiber(scaled(serving.fiber, scale));
        n.setCholesterol(scaled(serving.cholesterol, scale));
        n.setSodium(scaled(serving.sodium, scale));
        n.setPotassium(scaled(serving.potassium, scale));
        // vitaminAPercentDv/vitaminCPercentDv/calciumPercentDv/ironPercentDv
        // intentionally not mapped - %DV, not absolute values, see class Javadoc.

        n.setDataConfidence(DataConfidence.COMPUTED);
        n.setDataSource(DataSourceType.FATSECRET.getId());

        return n;
    }

    /**
     * Map recipe.get's ingredient list to FoodPortion stubs - same pattern
     * as TheMealDbMapper.mapIngredients(): itemId = food name (display
     * fallback), foodProduct left unresolved. Unlike TheMealDB, FatSecret
     * ingredients DO carry a real foodId - a future ingredient-resolution
     * pass could look these up directly via food/v5 instead of just
     * displaying the name, but that's not implemented here.
     */
    @NonNull
    private static List<FoodPortion> mapRecipeIngredients(
            @NonNull Recipe recipe,
            @NonNull List<RecipeGetResponse.RecipeIngredient> ingredients) {

        List<FoodPortion> portions = new ArrayList<>(ingredients.size());

        for (int i = 0; i < ingredients.size(); i++) {
            RecipeGetResponse.RecipeIngredient ingredient = ingredients.get(i);
            if (ingredient.foodName == null || ingredient.foodName.trim().isEmpty()) continue;

            ServingSize serving;
            if (ingredient.numberOfUnits != null && ingredient.measurementDescription != null) {
                serving = new ServingSize(ingredient.numberOfUnits + " " + ingredient.measurementDescription);
            } else if (ingredient.ingredientDescription != null) {
                serving = new ServingSize(ingredient.ingredientDescription);
            } else {
                serving = new ServingSize();
            }

            FoodPortion portion = new FoodPortion(
                    "FOOD_PRODUCT",
                    ingredient.foodName,
                    serving
            );
            portion.setOrderIndex(i);
            portion.setEstimated(true);
            portion.setParentType("RECIPE");
            portion.setParentId(recipe.getId());

            portions.add(portion);
        }

        return portions;
    }

    // ========================================================================
    // HELPERS
    // ========================================================================

    @Nullable
    private static Double scaled(@Nullable Double value, double scale) {
        return value != null ? value * scale : null;
    }

    private FatSecretMapper() {
        throw new UnsupportedOperationException("FatSecretMapper is a utility class");
    }
}