package li.masciul.sugardaddi.data.sources.thecocktaildb.mappers;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import li.masciul.sugardaddi.core.enums.DataSourceType;
import li.masciul.sugardaddi.core.models.FoodPortion;
import li.masciul.sugardaddi.core.models.Recipe;
import li.masciul.sugardaddi.core.models.ServingSize;
import li.masciul.sugardaddi.core.models.SourceIdentifier;
import li.masciul.sugardaddi.data.network.ApiConfig;
import li.masciul.sugardaddi.data.sources.thecocktaildb.TheCocktailDbConstants;
import li.masciul.sugardaddi.data.sources.thecocktaildb.api.dto.CocktailDbDrink;
import li.masciul.sugardaddi.data.sources.thecocktaildb.api.dto.CocktailDbIngredient;
import li.masciul.sugardaddi.data.sources.thecocktaildb.api.dto.CocktailDbSearchResponse;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * TheCocktailDbMapper - Maps TheCocktailDB DTOs to {@link Recipe} domain models.
 *
 * OUTPUT MODEL
 * ============
 * Cocktails map to {@link Recipe}. A cocktail is structurally a recipe:
 * it has a name, ingredients with measures, and preparation instructions.
 * The Recipe domain model captures all of this correctly.
 *
 * LANGUAGE
 * ========
 * TheCocktailDB v1 is English-only. All content is stored under language "en".
 *
 * NUTRITION
 * =========
 * TheCocktailDB provides NO nutrition data. The recipe's nutrition field is left null.
 * {@link Recipe#hasNutritionData()} will return false.
 * Note: ethanol calories are not computable from ingredient data alone without
 * ABV values, which are not provided by this API.
 *
 * INGREDIENT MAPPING
 * ==================
 * TheCocktailDB ingredients are unresolved plain-text strings ("Gin", "Lemon juice").
 * They are stored as {@link FoodPortion} stubs, exactly as TheMealDB ingredients are.
 *
 * ALCOHOLIC STATUS AND GLASS TYPE
 * ================================
 * strAlcoholic and strGlass have no dedicated fields in the Recipe domain model.
 * Both are stored as tags for simplicity and forward compatibility:
 *   strAlcoholic "Alcoholic"        → tag "alcoholic"
 *   strAlcoholic "Non alcoholic"    → tag "non_alcoholic"
 *   strAlcoholic "Optional alcohol" → tag "optional_alcohol"
 *   strGlass "Highball glass"       → tag "glass:highball glass"
 *
 * The glass tag uses a "glass:" prefix to distinguish it from other tags and
 * allow filtering or display in the future without ambiguity.
 *
 * INSTRUCTIONS
 * ============
 * strInstructions is stored as a raw blob via setInstructions() and also
 * parsed into structured RecipeStep entries via Recipe.addStep(), splitting
 * on newlines and filtering bare step numbers - same approach as TheMealDbMapper.
 *
 * IDENTIFIERS
 * ===========
 * sourceIdentifier = SourceIdentifier("THECOCKTAILDB", idDrink)
 * originalId       = idDrink (raw string from API, e.g. "11007")
 */
public class TheCocktailDbMapper {

    private static final String TAG = "TheCocktailDbMapper";

    // TheCocktailDB is English-only - all content stored under this language key
    private static final String LANGUAGE = "en";

    // ========== PUBLIC API ==========

    /**
     * Map a search/lookup response to a list of Recipe domain models.
     *
     * Skips any drink that fails {@link CocktailDbDrink#isValid()} validation
     * or returns null from {@link #mapToDomainModel(CocktailDbDrink)}.
     *
     * @param response Deserialized API response envelope. May be null.
     * @return List of mapped recipes. Never null, may be empty.
     */
    @NonNull
    public List<Recipe> mapSearchResponse(@Nullable CocktailDbSearchResponse response) {
        List<Recipe> recipes = new ArrayList<>();

        if (response == null || !response.hasResults()) {
            return recipes;
        }

        for (CocktailDbDrink drink : response.getDrinks()) {
            Recipe recipe = mapToDomainModel(drink);
            if (recipe != null) {
                recipes.add(recipe);
            }
        }

        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, "Mapped " + recipes.size() + "/" + response.getCount()
                    + " drinks from TheCocktailDB response");
        }

        return recipes;
    }

    /**
     * Map a single {@link CocktailDbDrink} DTO to a {@link Recipe} domain model.
     *
     * Returns null if the drink is invalid (missing ID or name) - callers
     * should skip null results silently.
     *
     * @param drink TheCocktailDB DTO. May be null.
     * @return Mapped Recipe, or null if drink is invalid.
     */
    @Nullable
    public Recipe mapToDomainModel(@Nullable CocktailDbDrink drink) {
        if (drink == null || !drink.isValid()) {
            Log.w(TAG, "Skipping invalid drink: " + drink);
            return null;
        }

        Recipe recipe = new Recipe();

        // ── Source identification ─────────────────────────────────────────────
        mapIdentification(recipe, drink);

        // ── Content fields ────────────────────────────────────────────────────
        mapContent(recipe, drink);

        // ── Ingredients → FoodPortion stubs ──────────────────────────────────
        mapIngredients(recipe, drink);

        // ── Tags (alcoholic status, glass type, category, strTags) ────────────
        mapTags(recipe, drink);

        // ── Timestamps ───────────────────────────────────────────────────────
        recipe.setLastUpdated(System.currentTimeMillis());
        recipe.setCreatedAt(System.currentTimeMillis());

        // ── Completeness ─────────────────────────────────────────────────────
        recipe.calculateCompleteness();

        return recipe;
    }

    // ========== PRIVATE MAPPING METHODS ==========

    /**
     * Map source identification fields.
     *
     * Sets DataSource, SourceIdentifier, and originalId so the recipe
     * can be attributed and round-tripped back to the API by ID.
     */
    private void mapIdentification(@NonNull Recipe recipe, @NonNull CocktailDbDrink drink) {
        recipe.setDataSource(DataSourceType.THECOCKTAILDB);

        // SourceIdentifier("THECOCKTAILDB", "11007") → searchableId = "THECOCKTAILDB:11007"
        recipe.setSourceIdentifier(
                new SourceIdentifier(TheCocktailDbConstants.SOURCE_ID, drink.getIdDrink())
        );

        // Keep the raw TheCocktailDB ID for API lookups (e.g. detail screen refresh)
        recipe.setOriginalId(drink.getIdDrink());

        recipe.setId(TheCocktailDbConstants.SOURCE_ID + ":" + drink.getIdDrink());
    }

    /**
     * Map content fields: name, description, instructions, image, video.
     *
     * All content is English-only.
     *
     * TheCocktailDB has no strArea (geographic origin). Description is composed
     * from category alone (e.g. "Cocktail", "Shot") - still gives the search
     * result card a meaningful subtitle.
     */
    private void mapContent(@NonNull Recipe recipe, @NonNull CocktailDbDrink drink) {
        recipe.setCurrentLanguage(LANGUAGE);

        // Name - guaranteed non-null by isValid() check above
        recipe.setName(drink.getStrDrink(), LANGUAGE);

        // Description - category only (no strArea in TheCocktailDB)
        String description = buildDescription(drink.getStrCategory(), drink.getStrAlcoholic());
        if (description != null) {
            recipe.setDescription(description, LANGUAGE);
        }

        // Instructions - raw blob + structured steps
        if (drink.getStrInstructions() != null
                && !drink.getStrInstructions().trim().isEmpty()) {

            String raw = drink.getStrInstructions().trim();

            // Store raw blob for search indexing and plain-text fallback
            recipe.setInstructions(raw, LANGUAGE);

            // Parse into structured steps - same approach as TheMealDbMapper
            String normalised = raw
                    .replace("\r\n", "\n")
                    .replace("\r", "\n")
                    .replaceAll("\n{3,}", "\n\n")
                    .trim();

            String[] lines = normalised.split("\n");
            for (String line : lines) {
                String step = line.trim();
                // Skip empty lines and bare step numbers
                if (step.isEmpty() || step.matches("^\\d+\\.?$")) {
                    continue;
                }
                // Strip leading step numbers inline (e.g. "1. Pour..." → "Pour...")
                String cleaned = step
                        .replaceFirst("^[Ss]tep\\s+\\d+[.:\\-]?\\s*", "")
                        .replaceFirst("^\\d+[.):\\-]\\s*", "");
                recipe.addStep(cleaned.trim(), null, null, LANGUAGE);
            }

            if (ApiConfig.DEBUG_LOGGING) {
                Log.d(TAG, "Parsed " + recipe.getSteps(LANGUAGE).size()
                        + " steps for: " + drink.getStrDrink());
            }
        }

        // Image URL - CDN-hosted JPEG, load with Glide
        if (drink.getStrDrinkThumb() != null
                && !drink.getStrDrinkThumb().trim().isEmpty()) {
            recipe.setImageUrl(drink.getStrDrinkThumb().trim());
        }

        // Video URL - store for detail screen video button
        if (drink.getStrVideo() != null && !drink.getStrVideo().trim().isEmpty()) {
            recipe.setVideoUrl(drink.getStrVideo().trim());
        }

        // Mark as a public recipe (not user-private)
        recipe.setPublic(true);
    }

    /**
     * Map TheCocktailDB's flat ingredient list to {@link FoodPortion} stubs.
     *
     * Identical strategy to TheMealDbMapper: each ingredient becomes an
     * unresolved FoodPortion stub with itemId = ingredient name and
     * serving = ServingSize from measure string.
     *
     * Ingredients with no name are skipped. Ingredients with no measure get
     * a ServingSize with null quantity.
     */
    private void mapIngredients(@NonNull Recipe recipe, @NonNull CocktailDbDrink drink) {
        List<CocktailDbIngredient> ingredients = drink.getIngredients();

        if (ingredients.isEmpty()) {
            Log.w(TAG, "No ingredients found for drink: " + drink.getIdDrink()
                    + " (" + drink.getStrDrink() + ")");
            return;
        }

        List<FoodPortion> portions = new ArrayList<>(ingredients.size());

        for (int i = 0; i < ingredients.size(); i++) {
            CocktailDbIngredient ingredient = ingredients.get(i);

            // Build serving size from the natural language measure string
            ServingSize serving = ingredient.hasMeasure()
                    ? new ServingSize(ingredient.getMeasure())
                    : new ServingSize();

            // Unresolved stub - itemId = ingredient name as display fallback
            FoodPortion portion = new FoodPortion(
                    "FOOD_PRODUCT",      // itemType - intended type when resolved
                    ingredient.getName(), // itemId - display fallback
                    serving
            );

            portion.setOrderIndex(i);
            portion.setEstimated(true); // Natural language quantities are approximate
            portion.setParentType("RECIPE");
            portion.setParentId(recipe.getId());

            portions.add(portion);
        }

        recipe.setPortions(portions);

        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, "Mapped " + portions.size() + " ingredient stubs for: "
                    + drink.getStrDrink());
        }
    }

    /**
     * Map tags from strTags, strCategory, strAlcoholic, strGlass, and strVideo.
     *
     * Tag strategy:
     *   strTags parsed list (e.g. ["IBA", "NewEra"]) → lowercased
     *   strCategory (e.g. "Cocktail") → lowercased
     *   strAlcoholic → one of: "alcoholic", "non_alcoholic", "optional_alcohol"
     *   strGlass (e.g. "Highball glass") → "glass:highball glass"
     *   strVideo (present) → "has_video"
     *
     * All tags are lowercased for consistent matching.
     */
    private void mapTags(@NonNull Recipe recipe, @NonNull CocktailDbDrink drink) {
        Set<String> tags = new HashSet<>();

        // Tags from strTags field (comma-separated)
        for (String tag : drink.getParsedTags()) {
            tags.add(tag.toLowerCase());
        }

        // Category as tag
        if (drink.getStrCategory() != null && !drink.getStrCategory().trim().isEmpty()) {
            tags.add(drink.getStrCategory().trim().toLowerCase());
        }

        // Alcoholic status → structured tag
        if (drink.getStrAlcoholic() != null) {
            String alcoholic = drink.getStrAlcoholic().trim();
            if (TheCocktailDbConstants.ALCOHOLIC_VALUE.equalsIgnoreCase(alcoholic)) {
                tags.add(TheCocktailDbConstants.TAG_ALCOHOLIC);
            } else if (TheCocktailDbConstants.NON_ALCOHOLIC_VALUE.equalsIgnoreCase(alcoholic)) {
                tags.add(TheCocktailDbConstants.TAG_NON_ALCOHOLIC);
            } else if (TheCocktailDbConstants.OPTIONAL_ALCOHOL_VALUE.equalsIgnoreCase(alcoholic)) {
                tags.add(TheCocktailDbConstants.TAG_OPTIONAL_ALCOHOL);
            }
        }

        // Glass type → prefixed tag for unambiguous future retrieval
        if (drink.getStrGlass() != null && !drink.getStrGlass().trim().isEmpty()) {
            tags.add("glass:" + drink.getStrGlass().trim().toLowerCase());
        }

        // Video availability flag - cheap boolean for the search card delegate
        if (drink.getStrVideo() != null && !drink.getStrVideo().trim().isEmpty()) {
            tags.add(TheCocktailDbConstants.TAG_HAS_VIDEO);
        }

        if (!tags.isEmpty()) {
            recipe.setTags(tags);
        }
    }

    // ========== HELPERS ==========

    /**
     * Build the description string from category and alcoholic status.
     *
     * TheCocktailDB has no strArea, so description is composed differently
     * from TheMealDB. Category + alcoholic status gives a useful subtitle:
     *   category="Cocktail", alcoholic="Alcoholic"     → "Cocktail · Alcoholic"
     *   category="Shot",     alcoholic="Non alcoholic" → "Shot · Non alcoholic"
     *   category="Cocktail", alcoholic=null            → "Cocktail"
     *   category=null,       alcoholic="Alcoholic"     → "Alcoholic"
     *   category=null,       alcoholic=null            → null
     *
     * @param category  strCategory from TheCocktailDB. May be null.
     * @param alcoholic strAlcoholic from TheCocktailDB. May be null.
     * @return Composed description, or null if both inputs are null/blank.
     */
    @Nullable
    private String buildDescription(@Nullable String category, @Nullable String alcoholic) {
        boolean hasCategory  = category  != null && !category.trim().isEmpty();
        boolean hasAlcoholic = alcoholic != null && !alcoholic.trim().isEmpty();

        if (hasCategory && hasAlcoholic) {
            return category.trim() + " · " + alcoholic.trim();
        } else if (hasCategory) {
            return category.trim();
        } else if (hasAlcoholic) {
            return alcoholic.trim();
        }
        return null;
    }
}