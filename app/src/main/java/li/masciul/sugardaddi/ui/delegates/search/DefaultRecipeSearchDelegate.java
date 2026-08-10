package li.masciul.sugardaddi.ui.delegates.search;

import android.content.Context;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import li.masciul.sugardaddi.R;
import li.masciul.sugardaddi.core.enums.DataSourceType;
import li.masciul.sugardaddi.core.enums.ProductType;
import li.masciul.sugardaddi.core.interfaces.Searchable;
import li.masciul.sugardaddi.core.models.Recipe;
import li.masciul.sugardaddi.ui.delegates.ItemViewDelegate;
import li.masciul.sugardaddi.ui.delegates.ViewType;
import li.masciul.sugardaddi.ui.utils.CardThumbnailHelper;

/**
 * DefaultRecipeSearchDelegate - generic (fallback) search result card for recipes.
 *
 * This is the LAST-registered recipe delegate. Source-specific delegates
 * (e.g. MealDbRecipeSearchDelegate, CocktailDbRecipeSearchDelegate) are checked
 * first; this one catches every remaining Recipe - any source without a dedicated
 * delegate, plus any future user-created recipes. Matching on ProductType.RECIPE
 * alone is deliberate: a "default" that only handled one source would not be a
 * fallback, and a new source's recipes would otherwise match no delegate at all.
 *
 * DISPLAYS:
 * - Thumbnail (user override / cached / remote, resolved via ImageDisplayUtils),
 *   hidden when no image source resolves
 * - Recipe name
 * - Description (truncated)
 * - Total time (prep + cook)
 * - Servings count
 * - Difficulty level
 *
 * @version 1.1
 */
public class DefaultRecipeSearchDelegate implements ItemViewDelegate<DefaultRecipeSearchDelegate.ViewHolder> {

    private final Context context;

    public DefaultRecipeSearchDelegate(@NonNull Context context) {
        this.context = context;
    }

    // ========== ItemViewDelegate CONTRACT ==========

    @Override
    public int getViewType() {
        return ViewType.RECIPE;
    }

    @Override
    public int getLayoutResId() {
        return R.layout.item_search_recipe_default;
    }

    @Override
    public boolean canHandle(@NonNull Searchable item) {
        // Generic fallback: handle ANY recipe. Registered last, so source-specific
        // recipe delegates (TheMealDB, TheCocktailDB) still take precedence; this
        // catches every other source and any future user-created recipes.
        return item.getProductType() == ProductType.RECIPE;
    }

    @NonNull
    @Override
    public ViewHolder createViewHolder(@NonNull View view) {
        return new ViewHolder(view);
    }

    @Override
    public void bind(@NonNull ViewHolder holder, @NonNull Searchable item, @NonNull String language) {
        Recipe recipe = (Recipe) item;

        bindName(holder, recipe, language);
        bindSourceBadge(holder, recipe);
        CardThumbnailHelper.bindRecipeThumbnail(context,
                holder.thumbnailContainer, holder.thumbnailImage, holder.thumbnailExpandIcon,
                recipe);
        bindDescription(holder, recipe, language);
        bindTime(holder, recipe);
        bindServings(holder, recipe);
        bindDifficulty(holder, recipe);
        // Separators depend on which of time/servings/difficulty actually
        // rendered above, so this runs last.
        bindSeparators(holder);
    }

    /** Mirrors DefaultProductSearchDelegate.bindSourceBadge() exactly. */
    private void bindSourceBadge(ViewHolder holder, Recipe recipe) {
        DataSourceType source = recipe.getDataSource();
        if (source != null) {
            String label = source.getDisplayName(context);
            if (label != null && !label.isEmpty()) {
                holder.sourceBadge.setText(label);
                holder.sourceBadge.setVisibility(View.VISIBLE);
                return;
            }
        }
        holder.sourceBadge.setVisibility(View.GONE);
    }

    // ========== BINDING HELPERS ==========

    private void bindName(ViewHolder holder, Recipe recipe, String language) {
        String name = recipe.getDisplayName(language);
        holder.recipeName.setText(name != null && !name.trim().isEmpty() ? name : "-");
    }

    private void bindDescription(ViewHolder holder, Recipe recipe, String language) {
        String description = recipe.getDescription(language);
        if (description != null && !description.trim().isEmpty()) {
            // Truncate long descriptions
            String shortDesc = description.length() > 80
                    ? description.substring(0, 77) + "\u2026"
                    : description;
            holder.recipeDescription.setText(shortDesc);
            holder.recipeDescription.setVisibility(View.VISIBLE);
        } else {
            holder.recipeDescription.setVisibility(View.GONE);
        }
    }

    private void bindTime(ViewHolder holder, Recipe recipe) {
        Integer prep = recipe.getPrepTimeMinutes();
        Integer cook = recipe.getCookTimeMinutes();
        int totalTime = (prep != null ? prep : 0) + (cook != null ? cook : 0);
        if (totalTime > 0) {
            holder.recipeTime.setText(
                    context.getString(R.string.favorite_time_format, totalTime));
            holder.recipeTime.setVisibility(View.VISIBLE);
        } else {
            holder.recipeTime.setVisibility(View.GONE);
        }
    }

    private void bindServings(ViewHolder holder, Recipe recipe) {
        Integer servings = recipe.getServings();
        if (servings != null && servings > 0) {
            holder.recipeServings.setText(
                    context.getString(R.string.favorite_servings_format, servings));
            holder.recipeServings.setVisibility(View.VISIBLE);
        } else {
            holder.recipeServings.setVisibility(View.GONE);
        }
    }

    private void bindDifficulty(ViewHolder holder, Recipe recipe) {
        if (recipe.getDifficulty() != null) {
            holder.recipeDifficulty.setText(recipe.getDifficulty().getDisplayName());
            holder.recipeDifficulty.setVisibility(View.VISIBLE);
        } else {
            holder.recipeDifficulty.setVisibility(View.GONE);
        }
    }

    /**
     * The two dot separators sit between time/servings/difficulty and must
     * not outlive their neighbors. Each separator is shown only when BOTH
     * the field before and after it are visible - previously they were
     * static in the layout and rendered alone (two lonely dots) whenever a
     * recipe had none of the three fields, e.g. several FatSecret entries.
     */
    private void bindSeparators(ViewHolder holder) {
        boolean timeVisible       = holder.recipeTime.getVisibility() == View.VISIBLE;
        boolean servingsVisible   = holder.recipeServings.getVisibility() == View.VISIBLE;
        boolean difficultyVisible = holder.recipeDifficulty.getVisibility() == View.VISIBLE;

        holder.separator1.setVisibility(
                (timeVisible && servingsVisible) ? View.VISIBLE : View.GONE);
        holder.separator2.setVisibility(
                (servingsVisible && difficultyVisible) ? View.VISIBLE : View.GONE);
    }

    // ========== VIEW HOLDER ==========

    static class ViewHolder extends RecyclerView.ViewHolder {
        final View      thumbnailContainer;
        final ImageView thumbnailImage;
        final ImageView thumbnailExpandIcon;
        final TextView  recipeName;
        final TextView  sourceBadge;
        final TextView  recipeDescription;
        final TextView  recipeTime;
        final TextView  recipeServings;
        final TextView  recipeDifficulty;
        final View      separator1;
        final View      separator2;

        ViewHolder(View itemView) {
            super(itemView);
            thumbnailContainer  = itemView.findViewById(R.id.thumbnailContainer);
            thumbnailImage      = itemView.findViewById(R.id.thumbnailImage);
            thumbnailExpandIcon = itemView.findViewById(R.id.thumbnailExpandIcon);
            recipeName          = itemView.findViewById(R.id.recipeName);
            sourceBadge         = itemView.findViewById(R.id.sourceBadge);
            recipeDescription   = itemView.findViewById(R.id.recipeDescription);
            recipeTime          = itemView.findViewById(R.id.recipeTime);
            recipeServings      = itemView.findViewById(R.id.recipeServings);
            recipeDifficulty    = itemView.findViewById(R.id.recipeDifficulty);
            separator1          = itemView.findViewById(R.id.separator1);
            separator2          = itemView.findViewById(R.id.separator2);
        }
    }
}