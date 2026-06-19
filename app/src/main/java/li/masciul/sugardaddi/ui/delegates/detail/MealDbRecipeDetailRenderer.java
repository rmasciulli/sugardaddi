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
import li.masciul.sugardaddi.core.enums.DataSourceType;
import li.masciul.sugardaddi.core.enums.ProductType;
import li.masciul.sugardaddi.core.interfaces.Searchable;
import li.masciul.sugardaddi.core.models.FoodPortion;
import li.masciul.sugardaddi.core.models.Recipe;
import li.masciul.sugardaddi.core.models.RecipeStep;

import java.util.List;

/**
 * MealDbRecipeDetailRenderer - Detail renderer for TheMealDB recipes.
 *
 * HANDLES: Recipe items with DataSourceType.THEMEALDB.
 *
 * DISPLAYS:
 *   - Hero image (full-width)
 *   - Name + area·category description
 *   - Ingredient list (unresolved FoodPortion stubs - name + measure string)
 *   - Full cooking instructions (plain-text blob, split into paragraphs)
 *   - TheMealDB attribution
 *
 * DOES NOT DISPLAY:
 *   - Nutrition (TheMealDB provides none - placeholder shown instead)
 *   - Difficulty / prep time / cook time (not available from this source)
 *   - Edit button (external recipe - read-only)
 *   - Video link (handled by the activity toolbar action_video menu item)
 *
 * INGREDIENTS:
 *   TheMealDB ingredients arrive as unresolved FoodPortion stubs:
 *     - portion.getItemId()               = ingredient name (display fallback)
 *     - portion.getServing().getDisplayText() = natural-language measure ("3/4 cup")
 *   Ingredient rows are inflated programmatically into ingredientsContainer.
 *
 * INSTRUCTIONS:
 *   TheMealDB returns a single plain-text blob for strInstructions.
 *   We split on double newlines to produce visual paragraph breaks.
 *   Single newlines within a paragraph are preserved.
 *
 * @version 1.0
 */
public class MealDbRecipeDetailRenderer implements DetailRenderer {

    private static final String TAG = "MealDbRecipeRenderer";

    private final Context context;

    public MealDbRecipeDetailRenderer(@NonNull Context context) {
        this.context = context;
    }

    // ========== DetailRenderer CONTRACT ==========

    /**
     * Handles Recipe items from TheMealDB only.
     * DefaultRecipeDetailRenderer is the catch-all for all other sources.
     */
    @Override
    public boolean supports(@NonNull Searchable item) {
        return item.getProductType() == ProductType.RECIPE
                && item.getDataSource() == DataSourceType.THEMEALDB;
    }

    @NonNull
    @Override
    public View inflate(@NonNull LayoutInflater inflater, @NonNull ViewGroup container) {
        // Do NOT attach to container - RecipeDetailsActivity does that.
        return inflater.inflate(R.layout.detail_mealdb_recipe, container, false);
    }

    @Override
    public void populate(@NonNull View view, @NonNull Searchable item, @NonNull String language) {
        if (!(item instanceof Recipe)) return;
        Recipe recipe = (Recipe) item;

        populateImage(view, recipe);
        populateHeader(view, recipe, language);
        populateIngredients(view, recipe, language);
        populateInstructions(view, recipe, language);
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

    /**
     * Load the full-size recipe image via Glide.
     * Local-first: userImagePath → imagePath → thumbnailPath → imageUrl → hide.
     */
    private void populateImage(@NonNull View view, @NonNull Recipe recipe) {
        View heroContainer = view.findViewById(R.id.heroImageContainer);
        ImageView heroImage = view.findViewById(R.id.heroImage);

        android.util.DisplayMetrics dm = context.getResources().getDisplayMetrics();
        int widthPx  = dm.widthPixels;
        int heightPx = Math.round(200 * dm.density);

        // Local-first: userImagePath → imagePath → thumbnailPath → imageUrl → hide
        Object imageSource = resolveRecipeImageSource(recipe);

        if (imageSource != null) {
            Glide.with(context)
                    .load(imageSource)
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
    private Object resolveRecipeImageSource(@NonNull Recipe recipe) {
        // 1. User-defined full-size image takes priority.
        String userImage = recipe.getUserImagePath();
        if (userImage != null && !userImage.trim().isEmpty()) {
            java.io.File f = new java.io.File(userImage);
            if (f.exists()) return f;
        }

        // 2. Auto-cached full-size image (currently unused - imagePath always null).
        String imagePath = recipe.getImagePath();
        if (imagePath != null && !imagePath.trim().isEmpty()) {
            java.io.File f = new java.io.File(imagePath);
            if (f.exists()) return f;
        }

        // 3. Cached thumbnail - offline copy of imageUrl, acceptable as hero fallback.
        String thumbPath = recipe.getThumbnailPath();
        if (thumbPath != null && !thumbPath.trim().isEmpty()) {
            java.io.File f = new java.io.File(thumbPath);
            if (f.exists()) return f;
        }

        // 4. Remote image URL.
        String imageUrl = recipe.getImageUrl();
        if (imageUrl != null && !imageUrl.trim().isEmpty()) return imageUrl;

        return null;
    }

    /**
     * Header: recipe name and area·category description.
     * Description is GONE when empty (some TheMealDB entries have no area/category).
     */
    private void populateHeader(@NonNull View view, @NonNull Recipe recipe,
                                @NonNull String language) {
        TextView nameView = view.findViewById(R.id.recipeName);
        TextView descriptionView = view.findViewById(R.id.recipeDescription);

        String name = recipe.getDisplayName(language);
        nameView.setText(name != null ? name : "");

        String description = recipe.getDescription(language);
        if (description != null && !description.trim().isEmpty()) {
            descriptionView.setText(description);
            descriptionView.setVisibility(View.VISIBLE);
        } else {
            descriptionView.setVisibility(View.GONE);
        }
    }

    /**
     * Ingredients: one row per FoodPortion stub.
     *
     * Each row shows:
     *   LEFT  - ingredient name (portion.getItemId())
     *   RIGHT - measure string (portion.getServing().getDisplayText())
     *
     * Rows are sorted by orderIndex (API order). Portions are always present
     * for TheMealDB recipes (mapIngredients() guarantees at least 1), but we
     * guard against null to be safe.
     */
    private void populateIngredients(@NonNull View view, @NonNull Recipe recipe,
                                     @NonNull String language) {
        LinearLayout container = view.findViewById(R.id.ingredientsContainer);
        container.removeAllViews();

        List<FoodPortion> portions = recipe.getPortions();
        if (portions == null || portions.isEmpty()) {
            // Container is empty - divider and section header still show,
            // but that won't happen in practice since TheMealDB always has ingredients
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(context);
        for (FoodPortion portion : portions) {
            addIngredientRow(inflater, container, portion, language);
        }
    }

    /**
     * Inflate and populate a single ingredient row.
     *
     * For TheMealDB stubs: foodProduct is null, so we use:
     *   - portion.getItemId() as the ingredient name
     *   - portion.getServing().getDisplayText() as the measure
     *
     * FoodPortion.getDisplayName() falls back to itemId when foodProduct is null,
     * so both approaches produce the same result - we use getDisplayName() for
     * consistency with the rest of the codebase.
     */
    private void addIngredientRow(@NonNull LayoutInflater inflater,
                                  @NonNull LinearLayout container,
                                  @NonNull FoodPortion portion,
                                  @NonNull String language) {
        // Simple two-column row: name on left, measure on right
        View row = inflater.inflate(R.layout.item_ingredient_row, container, false);

        TextView nameView = row.findViewById(R.id.ingredientName);
        TextView measureView = row.findViewById(R.id.ingredientMeasure);

        // Name: use getDisplayName() which falls back to itemId for unresolved stubs
        String name = portion.getDisplayName(language);
        nameView.setText(name != null ? name : "");

        // Measure: raw natural-language string from TheMealDB ("3/4 cup", "to taste")
        String measure = portion.getServing() != null
                ? portion.getServing().getDisplayText()
                : "";
        // "Unknown serving" is the ServingSize fallback - replace with empty for cleaner UI
        if ("Unknown serving".equals(measure)) {
            measure = "";
        }
        measureView.setText(measure);

        container.addView(row);
    }

    /**
     * Instructions: rendered as numbered steps via recipe.getSteps(language).
     *
     * TheMealDbMapper.mapContent() parses strInstructions into structured
     * RecipeStepMetadata + RecipeStepTranslation entries using \r\n as the
     * step delimiter. Recipe.getSteps() assembles them into RecipeStep objects.
     *
     * Falls back to the raw instructions blob if getSteps() returns empty -
     * defensive against edge cases where the mapper receives malformed data.
     *
     * Duration and equipment are not available from TheMealDB - those views
     * are set to GONE in each inflated row.
     */
    private void populateInstructions(@NonNull View view, @NonNull Recipe recipe,
                                      @NonNull String language) {
        View instructionsSection    = view.findViewById(R.id.instructionsSection);
        LinearLayout stepsContainer = view.findViewById(R.id.stepsContainer);
        stepsContainer.removeAllViews();

        List<RecipeStep> steps = recipe.getSteps(language);

        if (steps != null && !steps.isEmpty()) {
            // Structured steps - inflate one row per step
            instructionsSection.setVisibility(View.VISIBLE);
            LayoutInflater inflater = LayoutInflater.from(context);

            for (RecipeStep step : steps) {
                View row = inflater.inflate(R.layout.item_recipe_step, stepsContainer, false);

                TextView stepNumber  = row.findViewById(R.id.stepNumber);
                TextView instruction = row.findViewById(R.id.stepInstruction);
                TextView duration    = row.findViewById(R.id.stepDuration);
                TextView equipment   = row.findViewById(R.id.stepEquipment);

                stepNumber.setText(String.valueOf(step.getStepNumber()));
                instruction.setText(step.getInstruction() != null
                        ? step.getInstruction() : "");

                // TheMealDB provides no per-step duration or equipment
                duration.setVisibility(View.GONE);
                equipment.setVisibility(View.GONE);

                stepsContainer.addView(row);
            }
        } else {
            // Fallback: display raw instructions blob as plain text
            String raw = recipe.getInstructions(language);
            if (raw != null && !raw.trim().isEmpty()) {
                instructionsSection.setVisibility(View.VISIBLE);
                LayoutInflater inflater = LayoutInflater.from(context);
                View fallbackRow = inflater.inflate(
                        R.layout.item_instruction_text, stepsContainer, false);
                TextView textView = fallbackRow.findViewById(R.id.instructionText);
                textView.setText(raw.trim());
                stepsContainer.addView(fallbackRow);
            } else {
                instructionsSection.setVisibility(View.GONE);
            }
        }
    }

    /**
     * Attribution: fixed TheMealDB credit string.
     * Always shown - TheMealDB requires attribution for public API use.
     */
    private void populateAttribution(@NonNull View view, @NonNull Recipe recipe) {
        DetailRendererUtils.populateAttribution(context, view, recipe.getDataSource());
    }
}