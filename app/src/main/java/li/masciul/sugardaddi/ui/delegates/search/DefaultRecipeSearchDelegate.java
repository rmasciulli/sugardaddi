package li.masciul.sugardaddi.ui.delegates.search;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;

import li.masciul.sugardaddi.R;
import li.masciul.sugardaddi.core.enums.DataSourceType;
import li.masciul.sugardaddi.core.enums.ProductType;
import li.masciul.sugardaddi.core.interfaces.Searchable;
import li.masciul.sugardaddi.core.models.Recipe;
import li.masciul.sugardaddi.ui.delegates.ItemViewDelegate;
import li.masciul.sugardaddi.ui.delegates.ViewType;

/**
 * DefaultRecipeSearchDelegate - Search result rendering for recipes
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
        return R.layout.item_search_recipe;
    }


    @Override
    public boolean canHandle(@NonNull Searchable item) {
        // Only handle user-created recipes (stored in Room via RecipeEntity).
        // TheMealDB recipes are handled by MealDbRecipeSearchDelegate instead.
        return item.getProductType() == ProductType.RECIPE
                && item.getDataSource() == DataSourceType.USER;
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
        holder.recipeName.setText(name != null && !name.trim().isEmpty() ? name : "-");
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

    /*
    // Placeholder method for when users will be able to add their own thumbnails to recipes
    private void bindImage(@NonNull ViewHolder holder, @NonNull Recipe recipe) {
        // User recipes have no remote URL and no auto-downloaded thumbnail.
        // Only show an image if the user has explicitly set one.
        int sizePx = Math.round(72 * context.getResources().getDisplayMetrics().density);
        Object source = resolveUserRecipeThumbnailSource(recipe);
        if (source != null && holder.recipeImage != null) {
            Glide.with(context)
                    .load(source)
                    .override(sizePx, sizePx)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .placeholder(R.drawable.ic_food_placeholder)
                    .error(R.drawable.ic_food_error)
                    .centerCrop()
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .into(holder.recipeImage);
            if (holder.imageContainer != null) {
                holder.imageContainer.setVisibility(View.VISIBLE);
            }
        } else {
            if (holder.imageContainer != null) {
                holder.imageContainer.setVisibility(View.GONE);
            }
        }
    }
    */

    /**
     * For user recipes, only local paths are checked - no remote URL exists.
     * userThumbnailPath would be set if the user replaced the auto-cached thumbnail.
     * userImagePath would be set if the user explicitly assigned a full-size image.
     */
    @Nullable
    private Object resolveRecipeThumbnailSource(@NonNull Recipe recipe) {
        // User recipes have no remote URL and no auto-downloaded thumbnail.
        // Only local paths are checked.
        String userThumb = recipe.getUserThumbnailPath();
        if (userThumb != null && !userThumb.trim().isEmpty()) {
            java.io.File f = new java.io.File(userThumb);
            if (f.exists()) return f;
        }
        
        String thumbPath = recipe.getThumbnailPath();
        if (thumbPath != null && !thumbPath.trim().isEmpty()) {
            java.io.File f = new java.io.File(thumbPath);
            if (f.exists()) return f;
        }
        
        String userImage = recipe.getUserImagePath();
        if (userImage != null && !userImage.trim().isEmpty()) {
            java.io.File f = new java.io.File(userImage);
            if (f.exists()) return f;
        }
        
        return null;
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