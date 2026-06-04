package li.masciul.sugardaddi.ui.delegates.detail;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;

import li.masciul.sugardaddi.R;
import li.masciul.sugardaddi.core.enums.ProductType;
import li.masciul.sugardaddi.core.interfaces.Searchable;
import li.masciul.sugardaddi.core.models.FoodPortion;
import li.masciul.sugardaddi.core.models.Recipe;
import li.masciul.sugardaddi.core.models.RecipeStep;

import java.util.List;

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

        populateHeroImage(view, recipe);
        populateHeader(view, recipe, language);
        populateIngredients(view, recipe, language);
        populateInstructions(view, recipe, language);
        populateNotes(view, recipe, language);
        populateNutritionState(view, recipe);
        populateAttribution(view, recipe);
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

    // destroy() default no-op is sufficient - no TextWatchers or heavy resources held.

    // ========== POPULATE HELPERS ==========

    private void populateHeroImage(@NonNull View view, @NonNull Recipe recipe) {
        View heroContainer = view.findViewById(R.id.heroImageContainer);
        ImageView heroImage = view.findViewById(R.id.heroImage);

        // Both views may not exist yet in detail_default_recipe.xml
        // - this method is a no-op until the layout is updated.
        if (heroContainer == null || heroImage == null) return;

        android.util.DisplayMetrics dm = context.getResources().getDisplayMetrics();
        int widthPx  = dm.widthPixels;
        int heightPx = Math.round(200 * dm.density);

        // User recipes: only local paths, no remote URL
        Object source = resolveUserRecipeHeroSource(recipe);
        if (source != null) {
            Glide.with(context)
                    .load(source)
                    .override(widthPx, heightPx)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .placeholder(R.drawable.ic_food_placeholder)
                    .error(R.drawable.ic_food_error)
                    .centerCrop()
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .into(heroImage);
            heroContainer.setVisibility(View.VISIBLE);
        } else {
            heroContainer.setVisibility(View.GONE);
        }
    }

    @Nullable
    private Object resolveUserRecipeHeroSource(@NonNull Recipe recipe) {
        // User-set explicit hero image - first choice
        String heroPath = recipe.getHeroImagePath();
        if (heroPath != null && !heroPath.trim().isEmpty()) {
            java.io.File f = new java.io.File(heroPath);
            if (f.exists()) return f;
        }
        // Cached thumbnail - only present if user replaced the default
        String thumbPath = recipe.getThumbnailPath();
        if (thumbPath != null && !thumbPath.trim().isEmpty()) {
            java.io.File f = new java.io.File(thumbPath);
            if (f.exists()) return f;
        }
        // No remote URL for user recipes - return null (hero container hidden)
        return null;
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
     * Nutrition state: show a "no nutrition data" placeholder.
     *
     * User recipes start with no nutrition data. Rather than showing nothing,
     * we show a clear placeholder so the user understands the section exists
     * but is currently empty. When nutrition is eventually added (manual entry
     * or ingredient resolution), this card will be replaced by a full label.
     */
    private void populateNutritionState(@NonNull View view, @NonNull Recipe recipe) {
        View noNutritionCard = view.findViewById(R.id.noNutritionCard);
        // Show the placeholder whenever there's no nutrition data.
        // Future: when NutritionLabelManager supports Recipe, inflate it here instead.
        noNutritionCard.setVisibility(
                recipe.hasNutritionData() ? View.GONE : View.VISIBLE);
    }

    private void populateAttribution(@NonNull View view, @NonNull Recipe recipe) {
        DetailRendererUtils.populateAttribution(context, view, recipe.getDataSource());
    }
}