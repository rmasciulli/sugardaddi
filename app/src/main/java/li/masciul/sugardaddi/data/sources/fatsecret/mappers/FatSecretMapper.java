package li.masciul.sugardaddi.data.sources.fatsecret.mappers;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import li.masciul.sugardaddi.core.enums.DataConfidence;
import li.masciul.sugardaddi.core.enums.DataSourceType;
import li.masciul.sugardaddi.core.enums.NutritionBasis;
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

        product.setName(food.foodName.trim(), language);

        // Brand is stored separately (FoodProduct.brand - the same field OFF
        // already populates and DefaultProductDetailRenderer/SearchDelegate
        // already display), not prefixed onto the name. Available on search
        // results too, unlike rating.
        if (food.brandName != null && !food.brandName.trim().isEmpty()) {
            product.setBrand(food.brandName.trim(), language);
        }

        // Best-effort estimate for the search card - see
        // parseFoodDescriptionNutrition()'s javadoc for exactly when this
        // does and doesn't produce a value.
        product.setNutrition(parseFoodDescriptionNutrition(food.foodDescription));

        // No structured nutrition from search results - see class Javadoc.
        // A getProduct() call (mapFoodDetail below) is required for real values.

        // Without this, dataCompleteness stays at its default and every
        // FatSecret search result fails ResultPipeline's minimum-completeness
        // quality gate outright - found via 20 items in, 0 out in production.
        product.calculateCompleteness();

        return product;
    }

    /**
     * Matches FatSecret's food_description ONLY when the quantity clause is
     * an explicit gram figure - e.g. "Per 100g", "Per 212g", "Per 1034g".
     * Confirmed against real search data (not just FatSecret's docs example):
     * every Generic food expresses its quantity this way, even when not
     * literally 100g. Branded foods never do - "Per 1 bar", "Per 3 squares",
     * "Per 1 cup" have no gram equivalent anywhere in the string, so they
     * never match and are correctly left unparsed (same gap as food.get's
     * missing metric_serving_amount for these same items).
     *
     * Tolerant of the double-space-before-dash quirk seen in real responses
     * ("Per 2 squares  - Calories...").
     */
    private static final Pattern FOOD_DESCRIPTION_PATTERN = Pattern.compile(
            "^Per\\s+(\\d+(?:\\.\\d+)?)g\\s*-\\s*" +
                    "Calories:\\s*(\\d+(?:\\.\\d+)?)kcal\\s*\\|\\s*" +
                    "Fat:\\s*(\\d+(?:\\.\\d+)?)g\\s*\\|\\s*" +
                    "Carbs:\\s*(\\d+(?:\\.\\d+)?)g\\s*\\|\\s*" +
                    "Protein:\\s*(\\d+(?:\\.\\d+)?)g",
            Pattern.CASE_INSENSITIVE);

    /**
     * Best-effort nutrition estimate from a search result's food_description,
     * scaled to per-100g. Returns null when the quantity clause isn't an
     * explicit gram figure - see FOOD_DESCRIPTION_PATTERN's javadoc. This is
     * a preview only: opening the item still calls mapFoodDetail(), whose
     * own (independent, more complete when available) nutrition replaces
     * this the moment the detail screen loads.
     */
    @Nullable
    private static Nutrition parseFoodDescriptionNutrition(@Nullable String description) {
        if (description == null) return null;
        Matcher m = FOOD_DESCRIPTION_PATTERN.matcher(description.trim());
        if (!m.find()) return null;

        double grams = Double.parseDouble(m.group(1));
        if (grams <= 0) return null;
        double scale = 100.0 / grams;

        Nutrition n = new Nutrition();
        n.setBasis(NutritionBasis.PER_100G);
        n.setEnergyKcal(Double.parseDouble(m.group(2)) * scale);
        n.setFat(Double.parseDouble(m.group(3)) * scale);
        n.setCarbohydrates(Double.parseDouble(m.group(4)) * scale);
        n.setProteins(Double.parseDouble(m.group(5)) * scale);
        // Parsed from a formatted summary string and linearly scaled, not a
        // lab measurement or the source's own declared per-100g figure -
        // ESTIMATED is the honest tier here.
        n.setDataConfidence(DataConfidence.ESTIMATED);
        n.setDataSource(DataSourceType.FATSECRET.getId());
        return n;
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

        // See mapFoodSearchResult() for the rationale.
        if (detail.brandName != null && !detail.brandName.trim().isEmpty()) {
            product.setBrand(detail.brandName.trim(), language);
        }

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
        // Basis follows the unit the chosen serving was expressed in: an
        // exact-100ml serving (preference 1) yields honest per-100ml
        // values; everything else reaching here is gram-based, since
        // preference 2 only accepts "g" servings.
        n.setBasis("ml".equalsIgnoreCase(chosen.metricServingUnit)
                ? NutritionBasis.PER_100ML
                : NutritionBasis.PER_100G);
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

        // FatSecret exposes no salt field - only elemental sodium. Derive
        // salt via the standard EU labeling formula (salt = sodium x 2.5)
        // when sodium is present and nothing already set salt directly.
        // sodium is mg, salt is g, hence the /1000. Same derivation as
        // USDAMapper - both are US-convention, sodium-only sources.
        if (n.getSalt() == null && n.getSodium() != null) {
            n.setSalt(n.getSodium() * 2.5 / 1000.0);
        }

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

        // recipe_nutrition is deliberately NOT mapped. The docs omit its
        // basis, but the endpoint's own sort options ("caloriesPerServing
        // Ascending/Descending") and the numbers in the docs' example
        // confirm the values are PER SERVING - not per 100 of anything -
        // and the search response carries no grams_per_portion to
        // normalize with. Storing them would plant per-serving numbers in
        // a per-100 model with no truthful NutritionBasis to declare.
        // Real per-100g nutrition arrives with the recipe/v2 detail call
        // when the user opens the recipe (see mapToRecipe). Nothing on the
        // search side consumes preview nutrition: the quality gate never
        // requires it, RecipeScorer ignores it, and no recipe search
        // delegate displays it.

        // recipes/search/v3 gives ingredient NAMES only (no quantities, no
        // directions - that's recipe/v2's job). Mark this recipe as a
        // preview so ResultPipeline waives the instructions requirement it
        // can never satisfy from search data alone (see Recipe.isPreview
        // Javadoc) - a real getRecipe() call replaces this with full detail
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
        // Normalized by grams_per_portion - per-100g by construction.
        // Explicit (despite matching the default) because this is the
        // normalization site where the basis is actually decided.
        n.setBasis(NutritionBasis.PER_100G);
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
        // intentionally not mapped. They are %DV, not absolute values, and
        // FatSecret does not document which FDA reference table the
        // percentages are computed against (pre-2016 and current tables
        // differ by 30-50% for calcium/vitamin C; vitamin A even changed
        // unit, IU vs mcg RAE). Converting on a guessed table would produce
        // plausible-looking but unverifiable numbers. The correct future
        // path is ingredient resolution via food.get (ingredients carry
        // real food_ids), which yields true absolute values - see
        // mapRecipeIngredients().

        // Salt derived from sodium (salt = sodium x 2.5, mg to g) - same
        // rationale as the food path above: FatSecret has no salt field.
        if (n.getSalt() == null && n.getSodium() != null) {
            n.setSalt(n.getSodium() * 2.5 / 1000.0);
        }

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