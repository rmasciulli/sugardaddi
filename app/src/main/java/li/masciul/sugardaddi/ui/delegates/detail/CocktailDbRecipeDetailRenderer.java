package li.masciul.sugardaddi.ui.delegates.detail;

import android.content.Context;
import android.util.DisplayMetrics;
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
import java.util.Set;

/**
 * CocktailDbRecipeDetailRenderer - Detail renderer for TheCocktailDB cocktails.
 *
 * HANDLES: Recipe items with DataSourceType.THECOCKTAILDB.
 *
 * DISPLAYS:
 *   - Hero image (full-width, 200dp)
 *   - Name + category·alcoholic status description
 *   - Glass type row (shown when tag "glass:*" is present)
 *   - Ingredient list (unresolved FoodPortion stubs - name + measure string)
 *   - Preparation instructions (parsed into structured steps)
 *   - TheCocktailDB attribution
 *
 * DOES NOT DISPLAY:
 *   - Nutrition (TheCocktailDB provides none)
 *   - Difficulty / prep time / cook time (not available from this source)
 *   - Edit button (external recipe - read-only)
 *
 * GLASS TYPE:
 *   Stored as a tag with prefix "glass:" by TheCocktailDbMapper.
 *   Example: "glass:highball glass" → displayed as "Highball glass"
 *   The row is hidden entirely when no glass tag is present.
 *
 * INGREDIENTS:
 *   TheCocktailDB ingredients arrive as unresolved FoodPortion stubs:
 *     - portion.getItemId()               = ingredient name (display fallback)
 *     - portion.getServing().getDisplayText() = natural-language measure ("1 3/4 shot")
 *
 * REGISTRATION: Must be registered BEFORE DefaultRecipeDetailRenderer in
 * RecipeDetailsActivity's renderer registry.
 *
 * @version 1.0
 */
public class CocktailDbRecipeDetailRenderer implements DetailRenderer {

    private static final String TAG = "CocktailDbRenderer";

    // Prefix used by TheCocktailDbMapper when storing glass type as a tag
    private static final String GLASS_TAG_PREFIX = "glass:";

    private final Context context;

    public CocktailDbRecipeDetailRenderer(@NonNull Context context) {
        this.context = context;
    }

    // ========== DetailRenderer CONTRACT ==========

    /**
     * Handles Recipe items from TheCocktailDB only.
     * DefaultRecipeDetailRenderer is the catch-all for all other sources.
     */
    @Override
    public boolean supports(@NonNull Searchable item) {
        return item.getProductType() == ProductType.RECIPE
                && item.getDataSource() == DataSourceType.THECOCKTAILDB;
    }

    @NonNull
    @Override
    public View inflate(@NonNull LayoutInflater inflater, @NonNull ViewGroup container) {
        return inflater.inflate(R.layout.detail_cocktaildb_recipe, container, false);
    }

    @Override
    public void populate(@NonNull View view, @NonNull Searchable item, @NonNull String language) {
        if (!(item instanceof Recipe)) return;
        Recipe recipe = (Recipe) item;

        populateHeroImage(view, recipe);
        populateHeader(view, recipe, language);
        populateGlassType(view, recipe);
        populateIngredients(view, recipe, language);
        populateInstructions(view, recipe, language);
        populateAttribution(view, recipe);
    }

    /**
     * Use the cocktail name as the toolbar title.
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
     * Hero image: full-width, 200dp tall.
     * Container is GONE by default - shown only when imageUrl is present.
     *
     * Uses explicit pixel override to avoid Glide measuring an unmeasured view
     * and falling back to SIZE_ORIGINAL (which causes slow decode and blur).
     */
    private void populateHeroImage(@NonNull View view, @NonNull Recipe recipe) {
        View heroContainer = view.findViewById(R.id.heroImageContainer);
        ImageView heroImage = view.findViewById(R.id.heroImage);

        android.util.DisplayMetrics dm = context.getResources().getDisplayMetrics();
        int widthPx  = dm.widthPixels;
        int heightPx = Math.round(200 * dm.density);

        Object imageSource = resolveRecipeHeroSource(recipe);

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
    private Object resolveRecipeHeroSource(@NonNull Recipe recipe) {
        String heroPath = recipe.getHeroImagePath();
        if (heroPath != null && !heroPath.trim().isEmpty()) {
            java.io.File f = new java.io.File(heroPath);
            if (f.exists()) return f;
        }

        String thumbPath = recipe.getThumbnailPath();
        if (thumbPath != null && !thumbPath.trim().isEmpty()) {
            java.io.File f = new java.io.File(thumbPath);
            if (f.exists()) return f;
        }

        String imageUrl = recipe.getImageUrl();
        if (imageUrl != null && !imageUrl.trim().isEmpty()) return imageUrl;
        return null;
    }

    /**
     * Header: cocktail name and category·alcoholic status description.
     * Description is GONE when empty.
     */
    private void populateHeader(@NonNull View view, @NonNull Recipe recipe,
                                @NonNull String language) {
        TextView nameView        = view.findViewById(R.id.recipeName);
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
     * Glass type: extracted from the "glass:*" tag stored by the mapper.
     * Capitalises the first letter for display.
     * The entire row is GONE when no glass tag is present.
     */
    private void populateGlassType(@NonNull View view, @NonNull Recipe recipe) {
        View glassRow       = view.findViewById(R.id.glassTypeRow);
        TextView glassValue = view.findViewById(R.id.glassTypeValue);

        if (glassRow == null || glassValue == null) return;

        String glassType = extractGlassType(recipe.getTags());
        if (glassType != null) {
            // Capitalise first letter: "highball glass" → "Highball glass"
            String display = Character.toUpperCase(glassType.charAt(0))
                    + glassType.substring(1);
            glassValue.setText(display);
            glassRow.setVisibility(View.VISIBLE);
        } else {
            glassRow.setVisibility(View.GONE);
        }
    }

    /**
     * Ingredients: one row per FoodPortion stub, inflated into ingredientsContainer.
     *
     * Each row shows:
     *   LEFT  - ingredient name (portion.getItemId())
     *   RIGHT - measure string (portion.getServing().getDisplayText())
     *
     * Rows are sorted by orderIndex (API order).
     */
    private void populateIngredients(@NonNull View view, @NonNull Recipe recipe,
                                     @NonNull String language) {
        LinearLayout container = view.findViewById(R.id.ingredientsContainer);
        if (container == null) return;
        container.removeAllViews();

        List<FoodPortion> portions = recipe.getPortions();
        if (portions == null || portions.isEmpty()) {
            View emptyView = view.findViewById(R.id.ingredientsEmpty);
            if (emptyView != null) emptyView.setVisibility(View.VISIBLE);
            return;
        }

        // Sort by orderIndex to preserve API ingredient order
        portions.sort((a, b) -> Integer.compare(a.getOrderIndex(), b.getOrderIndex()));

        LayoutInflater inflater = LayoutInflater.from(context);
        for (FoodPortion portion : portions) {
            View row = inflater.inflate(R.layout.item_ingredient_row, container, false);

            TextView nameView    = row.findViewById(R.id.ingredientName);
            TextView measureView = row.findViewById(R.id.ingredientMeasure);

            // itemId is the ingredient name for unresolved stubs
            String ingredientName = portion.getItemId();
            nameView.setText(ingredientName != null ? ingredientName : "-");

            // Measure string from ServingSize
            String measure = portion.getServing() != null
                    ? portion.getServing().getDisplayText()
                    : null;
            if (measure != null && !measure.trim().isEmpty()) {
                measureView.setText(measure);
                measureView.setVisibility(View.VISIBLE);
            } else {
                measureView.setVisibility(View.GONE);
            }

            container.addView(row);
        }
    }

    /**
     * Instructions: displayed as structured steps when available,
     * or as a plain-text fallback for cocktails with unparseable instructions.
     */
    private void populateInstructions(@NonNull View view, @NonNull Recipe recipe,
                                      @NonNull String language) {
        LinearLayout container = view.findViewById(R.id.instructionsContainer);
        if (container == null) return;
        container.removeAllViews();

        List<RecipeStep> steps = recipe.getSteps(language);
        LayoutInflater inflater = LayoutInflater.from(context);

        if (steps != null && !steps.isEmpty()) {
            // Structured steps - inflate item_recipe_step for each
            for (RecipeStep step : steps) {
                View row = inflater.inflate(R.layout.item_recipe_step, container, false);

                TextView stepNumber  = row.findViewById(R.id.stepNumber);
                TextView instruction = row.findViewById(R.id.stepInstruction); // not stepText
                TextView duration    = row.findViewById(R.id.stepDuration);
                TextView equipment   = row.findViewById(R.id.stepEquipment);

                stepNumber.setText(String.valueOf(step.getStepNumber()));
                instruction.setText(step.getInstruction() != null   // not getText()
                        ? step.getInstruction() : "");

                // TheCocktailDB provides no per-step duration or equipment
                duration.setVisibility(View.GONE);
                equipment.setVisibility(View.GONE);

                container.addView(row);
            }
        } else {
            // Plain-text fallback - inflate item_instruction_text
            String raw = recipe.getInstructions(language);
            if (raw != null && !raw.trim().isEmpty()) {
                View fallbackRow = inflater.inflate(
                        R.layout.item_instruction_text, container, false);
                TextView textView = fallbackRow.findViewById(R.id.instructionText);
                textView.setText(raw.trim());
                container.addView(fallbackRow);
            }
        }
    }

    /**
     * Attribution panel - TheCocktailDB credit.
     * Uses the shared DetailRendererUtils helper.
     */
    private void populateAttribution(@NonNull View view, @NonNull Recipe recipe) {
        DetailRendererUtils.populateAttribution(context, view, DataSourceType.THECOCKTAILDB);
    }

    // ========== HELPERS ==========

    /**
     * Extract the glass type from the recipe's tag set.
     * Returns the value after the "glass:" prefix, or null if no glass tag is present.
     *
     * Example: tags = {"alcoholic", "glass:highball glass", "cocktail"}
     *          → returns "highball glass"
     */
    @androidx.annotation.Nullable
    private String extractGlassType(@androidx.annotation.Nullable Set<String> tags) {
        if (tags == null) return null;
        for (String tag : tags) {
            if (tag.startsWith(GLASS_TAG_PREFIX)) {
                String value = tag.substring(GLASS_TAG_PREFIX.length()).trim();
                return value.isEmpty() ? null : value;
            }
        }
        return null;
    }
}