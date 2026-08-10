package li.masciul.sugardaddi.ui.delegates.search;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import li.masciul.sugardaddi.R;
import li.masciul.sugardaddi.core.enums.DataSourceType;
import li.masciul.sugardaddi.core.enums.ProductType;
import li.masciul.sugardaddi.core.interfaces.Searchable;
import li.masciul.sugardaddi.core.models.FoodPortion;
import li.masciul.sugardaddi.core.models.Recipe;
import li.masciul.sugardaddi.ui.adapters.TagChipAdapter;
import li.masciul.sugardaddi.ui.delegates.ItemViewDelegate;
import li.masciul.sugardaddi.ui.delegates.ViewType;
import li.masciul.sugardaddi.ui.utils.CardThumbnailHelper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * FatSecretRecipeSearchDelegate - Search result card rendering for
 * DataSourceType.FATSECRET recipes.
 *
 * DISPLAYS:
 * - Recipe name (bold, 1 line)
 * - Source badge ("FatSecret")
 * - "Recipe" product type label - no video concept, unlike MealDB
 * - Thumbnail image (optional - collapses when recipe_image is absent)
 * - recipe_description
 * - Ingredient count (derived from Recipe portions list, name-stub
 *   ingredients from recipes/search/v3 - same as TheMealDB's approach)
 * - Tag chips from recipe_types (small, curated set - "Dessert",
 *   "Beverage"). recipe_categories (the richer, detail-only list) is not
 *   available in the search response - see RecipeSearchResponse's class
 *   javadoc - and is shown instead on the detail screen's Categories card.
 *
 * ARCHITECTURE: Direct structural port of MealDbRecipeSearchDelegate, minus
 * the video-indicator logic (FatSecret recipes have no video field at all).
 * Must be registered BEFORE DefaultRecipeSearchDelegate in
 * SearchResultsAdapter so it takes priority for FatSecret recipes.
 */
public class FatSecretRecipeSearchDelegate
        implements ItemViewDelegate<FatSecretRecipeSearchDelegate.ViewHolder> {

    private final Context context;

    public FatSecretRecipeSearchDelegate(@NonNull Context context) {
        this.context = context;
    }

    // ========== ItemViewDelegate CONTRACT ==========

    @Override
    public int getViewType() {
        return ViewType.RECIPE_FATSECRET;
    }

    @Override
    public int getLayoutResId() {
        return R.layout.item_search_recipe_fatsecret;
    }

    @Override
    public boolean canHandle(@NonNull Searchable item) {
        return item.getProductType() == ProductType.RECIPE
                && item.getDataSource() == DataSourceType.FATSECRET;
    }

    @NonNull
    @Override
    public ViewHolder createViewHolder(@NonNull View view) {
        return new ViewHolder(view);
    }

    @Override
    public void bind(@NonNull ViewHolder holder, @NonNull Searchable item,
                     @NonNull String language) {
        Recipe recipe = (Recipe) item;
        bindName(holder, recipe, language);
        bindDescription(holder, recipe, language);
        CardThumbnailHelper.bindRecipeThumbnail(context,
                holder.thumbnailContainer, holder.thumbnailImage, holder.thumbnailExpandIcon,
                recipe);
        bindIngredientCount(holder, recipe);
        bindTags(holder, recipe);
    }

    // ========== BINDING HELPERS ==========

    private void bindName(@NonNull ViewHolder holder, @NonNull Recipe recipe,
                          @NonNull String language) {
        String name = recipe.getDisplayName(language);
        holder.recipeName.setText(name != null && !name.trim().isEmpty() ? name : "-");
    }

    /** recipe_description, when present. */
    private void bindDescription(@NonNull ViewHolder holder, @NonNull Recipe recipe,
                                 @NonNull String language) {
        String description = recipe.getDescription(language);
        if (description != null && !description.trim().isEmpty()) {
            holder.recipeDescription.setText(description);
            holder.recipeDescription.setVisibility(View.VISIBLE);
        } else {
            holder.recipeDescription.setVisibility(View.GONE);
        }
    }

    /**
     * Ingredient count derived from the recipe's FoodPortion list.
     * recipes/search/v3 gives ingredient names only (mapIngredientNames),
     * same shape as TheMealDB's stub portions.
     */
    private void bindIngredientCount(@NonNull ViewHolder holder, @NonNull Recipe recipe) {
        List<FoodPortion> portions = recipe.getPortions();
        if (portions != null && !portions.isEmpty()) {
            holder.ingredientCount.setText(
                    context.getString(R.string.recipe_ingredient_count, portions.size()));
            holder.ingredientCount.setVisibility(View.VISIBLE);
        } else {
            holder.ingredientCount.setVisibility(View.GONE);
        }
    }

    /**
     * Tag chips from recipe_types (see FatSecretMapper.mapRecipeSearchResult).
     * Excludes "fatsecret" itself and anything already shown in the
     * description, mirroring MealDbRecipeSearchDelegate's exclusion logic.
     */
    private void bindTags(@NonNull ViewHolder holder, @NonNull Recipe recipe) {
        Set<String> tags = recipe.getTags();
        if (tags == null || tags.isEmpty()) {
            holder.tagsChipGroup.setVisibility(View.GONE);
            return;
        }

        Set<String> excluded = new HashSet<>();
        excluded.add("fatsecret");
        excluded.add("recipe");
        String description = recipe.getDescription("en");
        if (description != null) {
            for (String part : description.split(" · ")) {
                excluded.add(part.trim().toLowerCase());
            }
        }

        List<String> displayable = new ArrayList<>();
        for (String tag : tags) {
            if (excluded.contains(tag.toLowerCase())) continue;
            displayable.add(capitalize(tag));
        }

        holder.tagChipAdapter.submitTags(displayable);
        holder.tagsChipGroup.setVisibility(displayable.isEmpty() ? View.GONE : View.VISIBLE);
    }

    /** Capitalize the first letter of a tag string. */
    private String capitalize(@NonNull String tag) {
        if (tag.isEmpty()) return tag;
        return Character.toUpperCase(tag.charAt(0)) + tag.substring(1);
    }

    // ========== VIEW HOLDER ==========

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView   recipeName;
        final TextView   sourceBadge;
        final TextView   productType;
        final View       thumbnailContainer;
        final ImageView  thumbnailImage;
        final ImageView  thumbnailExpandIcon;
        final TextView   recipeDescription;
        final TextView       ingredientCount;
        final RecyclerView   tagsChipGroup;
        final TagChipAdapter tagChipAdapter;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            recipeName          = itemView.findViewById(R.id.recipeName);
            sourceBadge         = itemView.findViewById(R.id.sourceBadge);
            productType         = itemView.findViewById(R.id.productType);
            thumbnailContainer  = itemView.findViewById(R.id.thumbnailContainer);
            thumbnailImage      = itemView.findViewById(R.id.thumbnailImage);
            thumbnailExpandIcon = itemView.findViewById(R.id.thumbnailExpandIcon);
            recipeDescription   = itemView.findViewById(R.id.recipeDescription);
            ingredientCount     = itemView.findViewById(R.id.ingredientCount);
            tagsChipGroup       = itemView.findViewById(R.id.tagsChipGroup);

            tagChipAdapter = new TagChipAdapter();
            tagsChipGroup.setAdapter(tagChipAdapter);
        }
    }
}