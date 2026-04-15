package li.masciul.sugardaddi.ui.delegates.search;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import li.masciul.sugardaddi.R;
import li.masciul.sugardaddi.core.enums.ProductType;
import li.masciul.sugardaddi.core.interfaces.Searchable;
import li.masciul.sugardaddi.core.models.Recipe;
import li.masciul.sugardaddi.ui.delegates.ItemViewDelegate;
import li.masciul.sugardaddi.ui.delegates.ViewType;

/**
 * RecipeSearchDelegate - Search result rendering for recipes
 *
 * DISPLAYS:
 * - Recipe name
 * - Description (truncated)
 * - Total time (prep + cook)
 * - Servings count
 * - Difficulty level
 *
 * Recipes are identified by ProductType.RECIPE regardless of DataSource,
 * so this delegate matches on type alone.
 *
 * @version 1.0
 */
public class RecipeSearchDelegate implements ItemViewDelegate<RecipeSearchDelegate.ViewHolder> {

    private final Context context;

    public RecipeSearchDelegate(@NonNull Context context) {
        this.context = context;
    }

    // ========== ItemViewDelegate CONTRACT ==========

    @Override
    public int getViewType() {
        return ViewType.RECIPE;
    }

    @Override
    public int getLayoutResId() {
        return R.layout.item_search_recipe;
    }

    @Override
    public boolean canHandle(@NonNull Searchable item) {
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
        bindDescription(holder, recipe, language);
        bindTime(holder, recipe);
        bindServings(holder, recipe);
        bindDifficulty(holder, recipe);
    }

    // ========== BINDING HELPERS ==========

    private void bindName(ViewHolder holder, Recipe recipe, String language) {
        String name = recipe.getDisplayName(language);
        holder.recipeName.setText(name != null && !name.trim().isEmpty() ? name : "—");
    }

    private void bindDescription(ViewHolder holder, Recipe recipe, String language) {
        String description = recipe.getDescription(language);
        if (description != null && !description.trim().isEmpty()) {
            // Truncate long descriptions
            String shortDesc = description.length() > 80
                    ? description.substring(0, 77) + "…"
                    : description;
            holder.recipeDescription.setText(shortDesc);
            holder.recipeDescription.setVisibility(View.VISIBLE);
        } else {
            holder.recipeDescription.setVisibility(View.GONE);
        }
    }

    private void bindTime(ViewHolder holder, Recipe recipe) {
        int totalTime = recipe.getPrepTimeMinutes() + recipe.getCookTimeMinutes();
        if (totalTime > 0) {
            holder.recipeTime.setText(
                    context.getString(R.string.favorite_time_format, totalTime));
            holder.recipeTime.setVisibility(View.VISIBLE);
        } else {
            holder.recipeTime.setVisibility(View.GONE);
        }
    }

    private void bindServings(ViewHolder holder, Recipe recipe) {
        int servings = recipe.getServings();
        if (servings > 0) {
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

    // ========== VIEW HOLDER ==========

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView recipeName;
        final TextView recipeDescription;
        final TextView recipeTime;
        final TextView recipeServings;
        final TextView recipeDifficulty;

        ViewHolder(View itemView) {
            super(itemView);
            recipeName = itemView.findViewById(R.id.recipeName);
            recipeDescription = itemView.findViewById(R.id.recipeDescription);
            recipeTime = itemView.findViewById(R.id.recipeTime);
            recipeServings = itemView.findViewById(R.id.recipeServings);
            recipeDifficulty = itemView.findViewById(R.id.recipeDifficulty);
        }
    }
}