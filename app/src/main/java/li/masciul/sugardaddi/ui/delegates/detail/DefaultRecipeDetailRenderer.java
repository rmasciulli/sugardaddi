package li.masciul.sugardaddi.ui.delegates.detail;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import li.masciul.sugardaddi.R;
import li.masciul.sugardaddi.core.enums.ProductType;
import li.masciul.sugardaddi.core.interfaces.Searchable;
import li.masciul.sugardaddi.core.enums.NutritionLabelMode;
import li.masciul.sugardaddi.core.models.FoodPortion;
import li.masciul.sugardaddi.core.models.Recipe;
import li.masciul.sugardaddi.core.models.RecipeStep;
import li.masciul.sugardaddi.core.models.ServingSize;
import li.masciul.sugardaddi.ui.components.NutritionLabelManager;
import li.masciul.sugardaddi.ui.utils.ImageDisplayUtils;

import java.util.List;
import java.util.Locale;

/**
 * DefaultRecipeDetailRenderer - Catch-all detail renderer for Recipe items.
 *
 * HANDLES: All Recipe items not matched by a more specific renderer.
 * Currently: DataSourceType.USER (user-created recipes).
 * Future: Any new recipe source without its own dedicated renderer.
 *
 * DISPLAYS:
 *   - Name, cuisine, difficulty, prep/cook time, servings
 *   - Ingredient list (FoodPortion list - resolved or stub)
 *   - Step-by-step instructions (RecipeStep list via recipe.getSteps())
 *   - Notes (when present)
 *   - "No nutrition data" placeholder (user recipes start with no nutrition)
 *
 * WHAT IS NOT SHOWN:
 *   - Hero image (user recipes don't currently support images)
 *   - Edit button (deferred - editing UI not yet implemented)
 *   - Attribution (user-created content needs none)
 *
 * REGISTRATION: Must be last in RecipeDetailsActivity's renderer registry
 * so that more specific renderers (MealDbRecipeDetailRenderer) take priority.
 *
 * @version 1.0
 */
public class DefaultRecipeDetailRenderer implements DetailRenderer {

    private final Context context;

    private NutritionLabelManager<Recipe> nutritionLabelManager;

    private TextWatcher customAmountWatcher;

    public DefaultRecipeDetailRenderer(@NonNull Context context) {
        this.context = context;
    }

    // ========== DetailRenderer CONTRACT ==========

    /**
     * Catch-all: handles any Recipe item not matched by a more specific renderer.
     * Checks ProductType.RECIPE only - DataSourceType is intentionally not checked.
     */
    @Override
    public boolean supports(@NonNull Searchable item) {
        return item.getProductType() == ProductType.RECIPE;
    }

    @NonNull
    @Override
    public View inflate(@NonNull LayoutInflater inflater, @NonNull ViewGroup container) {
        return inflater.inflate(R.layout.detail_default_recipe, container, false);
    }

    @Override
    public void populate(@NonNull View view, @NonNull Searchable item, @NonNull String language) {
        if (!(item instanceof Recipe)) return;
        Recipe recipe = (Recipe) item;

        populateImage(view, recipe);
        populateHeader(view, recipe, language);
        populateIngredients(view, recipe, language);
        populateInstructions(view, recipe, language);
        populateNotes(view, recipe, language);
        populateNutritionState(view, recipe);
        populateAttribution(view, recipe);
    }

    @Override
    public void destroy() {
        if (nutritionLabelManager != null) {
            nutritionLabelManager.clear();
            nutritionLabelManager = null;
        }
        customAmountWatcher = null;
    }

    /**
     * Use the recipe name as the toolbar title.
     */
    @Override
    public String getToolbarTitle(@NonNull Searchable item, @NonNull String language) {
        if (item instanceof Recipe) {
            String name = ((Recipe) item).getDisplayName(language);
            return (name != null && !name.trim().isEmpty()) ? name : null;
        }
        return null;
    }

    // ========== POPULATE HELPERS ==========

    /**
     * Load the full-size recipe image (when one resolves) and make the hero
     * tappable to open the full original full-screen. Default/user recipes have no
     * remote image, so the hero appears only when the user set a custom image.
     */
    private void populateImage(@NonNull View view, @NonNull Recipe recipe) {
        View heroContainer = view.findViewById(R.id.heroImageContainer);
        ImageView heroImage = view.findViewById(R.id.heroImage);
        if (heroContainer == null || heroImage == null) return;
        View heroExpandIcon = view.findViewById(R.id.heroExpandIcon);

        Object source = ImageDisplayUtils.resolveRecipeImageSource(recipe);
        if (source != null) {
            heroContainer.setVisibility(View.VISIBLE);
            ImageDisplayUtils.loadHeroImage(context, source, heroImage);
        } else {
            heroContainer.setVisibility(View.GONE);
        }
        // Tap to open the full original; the expand icon shows only when openable.
        ImageDisplayUtils.bindFullScreenTap(context, heroImage, heroExpandIcon, source);
    }

    /**
     * Header: name, cuisine, and the metadata row (difficulty · time · servings).
     *
     * The metadata row is GONE when all three fields are absent - avoids an
     * empty horizontal strip for sparse user recipes.
     */
    private void populateHeader(@NonNull View view, @NonNull Recipe recipe,
                                @NonNull String language) {
        TextView nameView        = view.findViewById(R.id.recipeName);
        TextView cuisineView     = view.findViewById(R.id.recipeCuisine);
        View metadataRow         = view.findViewById(R.id.metadataRow);
        TextView difficultyView  = view.findViewById(R.id.recipeDifficulty);
        TextView separator1      = view.findViewById(R.id.metadataSeparator1);
        TextView timeView        = view.findViewById(R.id.recipeTime);
        TextView separator2      = view.findViewById(R.id.metadataSeparator2);
        TextView servingsView    = view.findViewById(R.id.recipeServings);

        // Name - always shown
        String name = recipe.getDisplayName(language);
        nameView.setText(name != null ? name : "");

        // Cuisine
        String cuisine = recipe.getCuisine(language);
        if (cuisine != null && !cuisine.trim().isEmpty()) {
            cuisineView.setText(cuisine);
            cuisineView.setVisibility(View.VISIBLE);
        } else {
            cuisineView.setVisibility(View.GONE);
        }

        // Difficulty
        boolean hasDifficulty = recipe.getDifficulty() != null;
        if (hasDifficulty) {
            difficultyView.setText(recipe.getDifficulty().getDisplayName());
            difficultyView.setVisibility(View.VISIBLE);
        } else {
            difficultyView.setVisibility(View.GONE);
        }

        // Total time (prep + cook)
        Integer prep = recipe.getPrepTimeMinutes();
        Integer cook = recipe.getCookTimeMinutes();
        int total = (prep != null ? prep : 0) + (cook != null ? cook : 0);
        boolean hasTime = total > 0;
        if (hasTime) {
            timeView.setText(context.getString(R.string.favorite_time_format, total));
            timeView.setVisibility(View.VISIBLE);
        } else {
            timeView.setVisibility(View.GONE);
        }

        // Servings
        Integer servings = recipe.getServings();
        boolean hasServings = servings != null && servings > 0;
        if (hasServings) {
            servingsView.setText(
                    context.getString(R.string.favorite_servings_format, servings));
            servingsView.setVisibility(View.VISIBLE);
        } else {
            servingsView.setVisibility(View.GONE);
        }

        // Separators: show only between two visible fields
        separator1.setVisibility(hasDifficulty && hasTime ? View.VISIBLE : View.GONE);
        separator2.setVisibility(hasTime && hasServings ? View.VISIBLE : View.GONE);

        // Hide the whole metadata row if nothing to show
        boolean anyMetadata = hasDifficulty || hasTime || hasServings;
        metadataRow.setVisibility(anyMetadata ? View.VISIBLE : View.GONE);
    }

    /**
     * Ingredients: one row per FoodPortion.
     *
     * Handles both resolved (foodProduct != null) and unresolved (stub) portions:
     * - Resolved:   FoodPortion.getDisplayName(language) returns the product name
     * - Unresolved: FoodPortion.getDisplayName(language) falls back to itemId
     *
     * The card is GONE when there are no portions.
     */
    private void populateIngredients(@NonNull View view, @NonNull Recipe recipe,
                                     @NonNull String language) {
        View ingredientsCard = view.findViewById(R.id.ingredientsCard);
        LinearLayout container = view.findViewById(R.id.ingredientsContainer);
        container.removeAllViews();

        List<FoodPortion> portions = recipe.getPortions();
        if (portions == null || portions.isEmpty()) {
            ingredientsCard.setVisibility(View.GONE);
            return;
        }

        ingredientsCard.setVisibility(View.VISIBLE);
        LayoutInflater inflater = LayoutInflater.from(context);

        for (FoodPortion portion : portions) {
            View row = inflater.inflate(R.layout.item_ingredient_row, container, false);

            TextView nameView    = row.findViewById(R.id.ingredientName);
            TextView measureView = row.findViewById(R.id.ingredientMeasure);

            nameView.setText(portion.getDisplayName(language));

            String measure = portion.getServing() != null
                    ? portion.getServing().getDisplayText()
                    : "";
            if ("Unknown serving".equals(measure)) {
                measure = "";
            }
            measureView.setText(measure);

            container.addView(row);
        }
    }

    /**
     * Instructions: structured steps via recipe.getSteps(language).
     *
     * recipe.getSteps() combines RecipeStepMetadata + RecipeStepTranslation into
     * RecipeStep display objects. Each step shows its number, instruction text,
     * and optionally duration and equipment.
     *
     * Falls back gracefully: if stepStructure is empty but getInstructions()
     * returns a non-empty string, we display it as a plain-text blob.
     *
     * The card is GONE when neither steps nor plain-text instructions exist.
     */
    private void populateInstructions(@NonNull View view, @NonNull Recipe recipe,
                                      @NonNull String language) {
        View instructionsCard   = view.findViewById(R.id.instructionsCard);
        LinearLayout stepsContainer = view.findViewById(R.id.stepsContainer);
        stepsContainer.removeAllViews();

        List<RecipeStep> steps = recipe.getSteps(language);
        LayoutInflater inflater = LayoutInflater.from(context);

        if (steps != null && !steps.isEmpty()) {
            // Structured steps - inflate one view per step
            instructionsCard.setVisibility(View.VISIBLE);
            for (RecipeStep step : steps) {
                addStepRow(inflater, stepsContainer, step);
            }
        } else {
            // Fallback to plain-text instructions blob
            String raw = recipe.getInstructions(language);
            if (raw != null && !raw.trim().isEmpty()) {
                instructionsCard.setVisibility(View.VISIBLE);
                View fallbackRow = inflater.inflate(
                        R.layout.item_instruction_text, stepsContainer, false);
                TextView textView = fallbackRow.findViewById(R.id.instructionText);
                textView.setText(raw.trim());
                stepsContainer.addView(fallbackRow);
            } else {
                instructionsCard.setVisibility(View.GONE);
            }
        }
    }

    /**
     * Inflate and populate a single structured step row.
     *
     * Shows: step number, instruction text, optional duration, optional equipment.
     */
    private void addStepRow(@NonNull LayoutInflater inflater,
                            @NonNull LinearLayout container,
                            @NonNull RecipeStep step) {
        View row = inflater.inflate(R.layout.item_recipe_step, container, false);

        TextView stepNumber  = row.findViewById(R.id.stepNumber);
        TextView instruction = row.findViewById(R.id.stepInstruction);
        TextView duration    = row.findViewById(R.id.stepDuration);
        TextView equipment   = row.findViewById(R.id.stepEquipment);

        stepNumber.setText(String.valueOf(step.getStepNumber()));
        instruction.setText(step.getInstruction() != null ? step.getInstruction() : "");

        if (step.getDurationMinutes() != null && step.getDurationMinutes() > 0) {
            duration.setText(context.getString(
                    R.string.favorite_time_format, step.getDurationMinutes()));
            duration.setVisibility(View.VISIBLE);
        } else {
            duration.setVisibility(View.GONE);
        }

        if (step.getEquipment() != null && !step.getEquipment().trim().isEmpty()) {
            equipment.setText(step.getEquipment());
            equipment.setVisibility(View.VISIBLE);
        } else {
            equipment.setVisibility(View.GONE);
        }

        container.addView(row);
    }

    /**
     * Notes: shown only when recipe.getNotes() returns a non-empty string.
     */
    private void populateNotes(@NonNull View view, @NonNull Recipe recipe,
                               @NonNull String language) {
        View notesCard    = view.findViewById(R.id.notesCard);
        TextView notesText = view.findViewById(R.id.notesText);

        String notes = recipe.getNotes(language);
        if (notes != null && !notes.trim().isEmpty()) {
            notesText.setText(notes);
            notesCard.setVisibility(View.VISIBLE);
        } else {
            notesCard.setVisibility(View.GONE);
        }
    }

    /**
     * Nutrition state: show the real label when data exists, a placeholder
     * when it doesn't.
     *
     * This was previously placeholder-only - the "Future: when
     * NutritionLabelManager supports Recipe" comment predates
     * NutritionLabelManager actually being generalized to support Recipe.
     * Went unnoticed because no recipe source ever had real nutrition data
     * to display through this renderer until FatSecret.
     */
    private void populateNutritionState(@NonNull View view, @NonNull Recipe recipe) {
        View nutritionCard = view.findViewById(R.id.nutritionCard);
        LinearLayout nutritionContainer = view.findViewById(R.id.nutritionContainer);
        View noNutritionCard = view.findViewById(R.id.noNutritionCard);
        if (nutritionCard == null || nutritionContainer == null || noNutritionCard == null) return;

        if (recipe.hasNutritionData()) {
            nutritionCard.setVisibility(View.VISIBLE);
            noNutritionCard.setVisibility(View.GONE);

            nutritionLabelManager = new NutritionLabelManager<>(
                    context, nutritionContainer, NutritionLabelMode.DETAILED);

            double defaultAmount = getSmartDefaultAmount(recipe);
            nutritionLabelManager.display(recipe, defaultAmount);

            TextInputLayout amountLayout = view.findViewById(R.id.customAmountInputLayout);
            TextInputEditText amountInput = view.findViewById(R.id.customAmountEditText);

            if (amountLayout != null && amountInput != null) {
                updateAmountHint(amountLayout, recipe);

                customAmountWatcher = new TextWatcher() {
                    @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                    @Override public void afterTextChanged(Editable s) {}

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                        if (nutritionLabelManager == null) return;
                        try {
                            String input = s.toString().trim();
                            if (!input.isEmpty()) {
                                double amount = Double.parseDouble(input);
                                if (amount > 0) nutritionLabelManager.updateCustomAmount(amount);
                            }
                        } catch (NumberFormatException ignored) {}
                    }
                };
                amountInput.addTextChangedListener(customAmountWatcher);
            }
        } else {
            nutritionCard.setVisibility(View.GONE);
            noNutritionCard.setVisibility(View.VISIBLE);
        }
    }

    private void populateAttribution(@NonNull View view, @NonNull Recipe recipe) {
        DetailRendererUtils.populateAttribution(context, view, recipe.getDataSource());
    }

    // ========== NUTRITION AMOUNT HELPERS ==========

    /**
     * Hint text for the custom-amount field: shows the serving size in grams
     * when known, a generic prompt otherwise. Unit comes from the recipe's
     * own nutrition basis (per-100g or per-100ml, as declared by its
     * source) - never assumed from the recipe's physical nature.
     */
    private void updateAmountHint(@NonNull TextInputLayout layout, @NonNull Recipe recipe) {
        ServingSize serving = recipe.getServingSize();
        String unit = recipe.getNutrition() != null
                ? recipe.getNutrition().getBasis().getUnitLabel()
                : "g";
        if (serving != null && serving.isValid()) {
            Double servingGrams = serving.getAsGrams();
            if (servingGrams != null && servingGrams > 0) {
                layout.setHint(context.getString(R.string.custom_amount_with_serving,
                        formatAmount(servingGrams) + unit));
                return;
            }
        }
        layout.setHint(context.getString(R.string.custom_amount_default));
    }

    /**
     * Default amount for the nutrition label's third column: the recipe's
     * own portion weight when known (e.g. FatSecret's grams_per_portion,
     * see RecipeEntity.servingSize), 150g otherwise - a plated-portion
     * default, distinct from FoodProduct's 20g fallback (a spoonful of a
     * product vs. a serving of a dish are different scales).
     */
    private double getSmartDefaultAmount(@NonNull Recipe recipe) {
        ServingSize serving = recipe.getServingSize();
        if (serving != null && serving.isValid()) {
            Double servingGrams = serving.getAsGrams();
            if (servingGrams != null && servingGrams > 0) return servingGrams;
        }
        return 150.0;
    }

    private String formatAmount(double amount) {
        return (amount == Math.floor(amount))
                ? String.format(Locale.getDefault(), "%.0f", amount)
                : String.format(Locale.getDefault(), "%.1f", amount);
    }
}