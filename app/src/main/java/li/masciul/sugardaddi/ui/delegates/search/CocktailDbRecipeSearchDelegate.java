package li.masciul.sugardaddi.ui.delegates.search;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import li.masciul.sugardaddi.R;
import li.masciul.sugardaddi.core.enums.DataSourceType;
import li.masciul.sugardaddi.core.enums.ProductType;
import li.masciul.sugardaddi.core.interfaces.Searchable;
import li.masciul.sugardaddi.core.models.FoodPortion;
import li.masciul.sugardaddi.core.models.Recipe;
import li.masciul.sugardaddi.data.sources.thecocktaildb.TheCocktailDbConstants;
import li.masciul.sugardaddi.ui.delegates.ItemViewDelegate;
import li.masciul.sugardaddi.ui.delegates.ViewType;
import li.masciul.sugardaddi.ui.utils.ImageDisplayUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * CocktailDbRecipeSearchDelegate - Search result card rendering for TheCocktailDB cocktails.
 *
 * DISPLAYS:
 * - Cocktail name (bold, 2 lines max)
 * - Source badge ("TheCocktailDB")
 * - "Cocktail" product type label
 * - Thumbnail image (CDN-hosted JPEG, loaded via Glide)
 * - Category + alcoholic status description (e.g. "Cocktail · Alcoholic")
 * - Ingredient count (derived from Recipe portions list)
 * - Video indicator (shown when recipe has a video URL)
 * - Tag chips (shown when recipe has parsed tags from strTags)
 *
 * ARCHITECTURE:
 * Handles Recipe items from DataSourceType.THECOCKTAILDB only. User-created
 * recipes (DataSourceType.USER) are handled by DefaultRecipeSearchDelegate.
 * Must be registered BEFORE DefaultRecipeSearchDelegate in SearchResultsAdapter.
 *
 * DESIGN:
 * Layout and visual structure intentionally mirrors item_search_recipe_mealdb.xml.
 * Both sources use the same Recipe domain model and the same detail screen pipeline.
 *
 * @version 1.0
 */
public class CocktailDbRecipeSearchDelegate
        implements ItemViewDelegate<CocktailDbRecipeSearchDelegate.ViewHolder> {

    private final Context context;

    public CocktailDbRecipeSearchDelegate(@NonNull Context context) {
        this.context = context;
    }

    // ========== ItemViewDelegate CONTRACT ==========

    @Override
    public int getViewType() {
        return ViewType.RECIPE_COCKTAILDB;
    }

    @Override
    public int getLayoutResId() {
        return R.layout.item_search_recipe_cocktaildb;
    }

    @Override
    public boolean canHandle(@NonNull Searchable item) {
        return item.getProductType() == ProductType.RECIPE
                && item.getDataSource() == DataSourceType.THECOCKTAILDB;
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
        bindSourceBadge(holder);
        bindProductType(holder, recipe);
        bindDescription(holder, recipe, language);
        bindImage(holder, recipe);
        bindIngredientCount(holder, recipe);
        bindTags(holder, recipe);
    }

    // ========== BINDING HELPERS ==========

    private void bindName(@NonNull ViewHolder holder, @NonNull Recipe recipe,
                          @NonNull String language) {
        String name = recipe.getDisplayName(language);
        holder.recipeName.setText(name != null && !name.trim().isEmpty() ? name : "-");
    }

    private void bindSourceBadge(@NonNull ViewHolder holder) {
        holder.sourceBadge.setText(context.getString(R.string.source_name_thecocktaildb));
        holder.sourceBadge.setVisibility(View.VISIBLE);
    }

    /**
     * Product type label: "Cocktail" or "Cocktail · Video" when video is available.
     */
    private void bindProductType(@NonNull ViewHolder holder, @NonNull Recipe recipe) {
        boolean hasVideo = recipe.getTags() != null
                && recipe.getTags().contains("has_video");
        holder.productType.setText(hasVideo
                ? context.getString(R.string.product_type_cocktail_with_video)
                : context.getString(R.string.product_type_cocktail));
    }

    /**
     * Description: "Category · Alcoholic status" composed by TheCocktailDbMapper.
     * Example: "Cocktail · Alcoholic", "Shot · Non alcoholic"
     */
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

    private void bindImage(@NonNull ViewHolder holder, @NonNull Recipe recipe) {
        Object source = ImageDisplayUtils.resolveRecipeThumbnailSource(recipe);
        if (source != null) {
            ImageDisplayUtils.loadCardThumbnail(context, source, holder.recipeImage);
            holder.imageContainer.setVisibility(View.VISIBLE);
        } else {
            holder.imageContainer.setVisibility(View.GONE);
        }
        // Expand affordance on the thumbnail; opens the FULL original, icon shows
        // only when openable.
        ImageDisplayUtils.bindFullScreenTap(context, holder.recipeImage,
                holder.cardExpandIcon,
                ImageDisplayUtils.resolveRecipeImageSource(recipe));
    }

    /**
     * Ingredient count from the recipe's FoodPortion list.
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
     * Tag chips: shown for strTags values (e.g. "IBA", "NewEra").
     * Excludes structural tags ("alcoholic", "non_alcoholic", "optional_alcohol",
     * "has_video", "glass:*", category, alcoholic status) - those are displayed
     * via other fields and would be noisy if repeated here.
     */
    private void bindTags(@NonNull ViewHolder holder, @NonNull Recipe recipe) {
        holder.tagChips.removeAllViews();

        Set<String> tags = recipe.getTags();
        if (tags == null || tags.isEmpty()) {
            holder.tagChips.setVisibility(View.GONE);
            return;
        }

        // Build exclusion set - structural tags already shown via other fields
        Set<String> excluded = new HashSet<>();
        excluded.add("has_video");

        // Exclude description parts (category · alcoholic status)
        String description = recipe.getDescription("en");
        if (description != null) {
            for (String part : description.split(" · ")) {
                excluded.add(part.trim().toLowerCase());
            }
        }

        boolean hasDisplayableTags = false;
        for (String tag : tags) {
            // Skip structural tags
            if (excluded.contains(tag.toLowerCase())) continue;
            if (tag.startsWith("glass:")) continue;
            // Skip alcoholic status tags - already in description
            if (tag.equals(TheCocktailDbConstants.TAG_ALCOHOLIC)) continue;
            if (tag.equals(TheCocktailDbConstants.TAG_NON_ALCOHOLIC)) continue;
            if (tag.equals(TheCocktailDbConstants.TAG_OPTIONAL_ALCOHOL)) continue;

            // Inflate chip_tag_compact - same as MealDbRecipeSearchDelegate
            Chip chip = (Chip) LayoutInflater.from(context)
                    .inflate(R.layout.chip_tag_compact, holder.tagChips, false);
            chip.setEnsureMinTouchTargetSize(false);
            chip.setClickable(false);
            chip.setCheckable(false);
            chip.setFocusable(false);
            chip.setText(capitalize(tag));
            holder.tagChips.addView(chip);
            hasDisplayableTags = true;
        }

        holder.tagChips.setVisibility(hasDisplayableTags ? View.VISIBLE : View.GONE);
    }

    private String capitalize(@NonNull String tag) {
        if (tag.isEmpty()) return tag;
        return Character.toUpperCase(tag.charAt(0)) + tag.substring(1);
    }

    // ========== VIEW HOLDER ==========

    public static class ViewHolder extends RecyclerView.ViewHolder {

        final TextView   recipeName;
        final TextView   sourceBadge;
        final TextView   productType;
        final TextView   recipeDescription;
        final View       imageContainer;
        final ImageView  recipeImage;
        final ImageView  cardExpandIcon;
        final TextView   ingredientCount;
        final ChipGroup  tagChips;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            recipeName        = itemView.findViewById(R.id.recipeName);
            sourceBadge       = itemView.findViewById(R.id.sourceBadge);
            productType       = itemView.findViewById(R.id.productType);
            recipeDescription = itemView.findViewById(R.id.recipeDescription);
            imageContainer    = itemView.findViewById(R.id.imageContainer);
            recipeImage       = itemView.findViewById(R.id.recipeImage);
            cardExpandIcon    = itemView.findViewById(R.id.cardExpandIcon);
            ingredientCount   = itemView.findViewById(R.id.ingredientCount);
            tagChips          = itemView.findViewById(R.id.tagChips);
        }
    }
}