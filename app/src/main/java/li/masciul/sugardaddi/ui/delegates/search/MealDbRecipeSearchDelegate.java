package li.masciul.sugardaddi.ui.delegates.search;

import android.content.Context;
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
import li.masciul.sugardaddi.ui.delegates.ItemViewDelegate;
import li.masciul.sugardaddi.ui.delegates.ViewType;
import li.masciul.sugardaddi.ui.utils.ImageDisplayUtils;

import java.util.List;

/**
 * MealDbRecipeSearchDelegate - Search result card rendering for TheMealDB recipes.
 *
 * DISPLAYS:
 * - Recipe name (bold, 2 lines max)
 * - Source badge ("TheMealDB")
 * - "Recipe" product type label
 * - Thumbnail image (CDN-hosted JPEG, loaded via Glide)
 * - Area + Category description (e.g. "Japanese · Chicken")
 * - Ingredient count (derived from Recipe portions list)
 * - Video indicator (shown when recipe has a YouTube URL)
 * - Tag chips (shown when recipe has parsed tags from strTags)
 *
 * ARCHITECTURE:
 * Handles Recipe items from DataSourceType.THEMEALDB only. User-created
 * recipes (DataSourceType.USER) are handled by DefaultRecipeSearchDelegate.
 * Must be registered BEFORE DefaultRecipeSearchDelegate in SearchResultsAdapter.
 *
 * DATA MAPPING:
 * TheMealDB fields → Recipe domain model fields:
 *   strMealThumb  → recipe.getImageUrl()
 *   strArea + strCategory → recipe.getDescription("en")   (composed as "Area · Category")
 *   strYoutube    → recipe.getVideoUrl()                  (full URL stored on Recipe)
 *                   recipe tags                           (+ "has_video" tag for card display)
 *   ingredient count → recipe.getPortions().size()
 *   strTags       → recipe.getTags()
 *
 * NOTE ON VIDEO:
 * strYoutube is not stored as a dedicated field on Recipe - it was dropped
 * during mapping since Recipe has no videoUrl field. The video indicator
 * is derived from a tag "has_video" added by TheMealDbMapper when strYoutube
 * is present. If the tag is absent, the indicator is hidden.
 *
 * @version 1.0
 */
public class MealDbRecipeSearchDelegate
        implements ItemViewDelegate<MealDbRecipeSearchDelegate.ViewHolder> {

    private final Context context;

    public MealDbRecipeSearchDelegate(@NonNull Context context) {
        this.context = context;
    }

    // ========== ItemViewDelegate CONTRACT ==========

    @Override
    public int getViewType() {
        return ViewType.RECIPE_MEALDB;
    }

    @Override
    public int getLayoutResId() {
        return R.layout.item_search_recipe_themealdb;
    }

    @Override
    public boolean canHandle(@NonNull Searchable item) {
        // Only handle Recipe items from TheMealDB. Recipes from any other source
        // (or with no source-specific delegate) fall through to the generic
        // DefaultRecipeSearchDelegate, which is registered last.
        return item.getProductType() == ProductType.RECIPE
                && item.getDataSource() == DataSourceType.THEMEALDB;
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

    /**
     * Bind the product type label dynamically. Shows "Recipe with video"
     * when the recipe has a YouTube URL (has_video tag), "Recipe" otherwise.
     */
    private void bindProductType(@NonNull ViewHolder holder, @NonNull Recipe recipe) {
        boolean hasVideo = recipe.getTags() != null
                && recipe.getTags().contains("has_video");
        holder.productType.setText(hasVideo
                ? context.getString(R.string.product_type_recipe_with_video)
                : context.getString(R.string.product_type_recipe));
    }

    /**
     * Bind the area + category description.
     * TheMealDbMapper composes this as "Area · Category" in recipe.getDescription("en").
     * Example: "Japanese · Chicken"
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

    /**
     * Load the recipe thumbnail via Glide, and make it tappable to open the full
     * original full-screen. TheMealDB always provides strMealThumb for published
     * recipes - this should almost never be null, but we hide the container
     * gracefully if it is.
     */
    private void bindImage(@NonNull ViewHolder holder, @NonNull Recipe recipe) {
        Object source = ImageDisplayUtils.resolveRecipeThumbnailSource(recipe);
        if (source != null) {
            ImageDisplayUtils.loadCardThumbnail(context, source, holder.recipeImage);
            holder.imageContainer.setVisibility(View.VISIBLE);
        } else {
            holder.imageContainer.setVisibility(View.GONE);
        }
        // Tap to open the FULL original (resolve image source, not the thumbnail).
        // Always called so a recycled holder never keeps a stale tap target.
        ImageDisplayUtils.bindFullScreenTap(context, holder.recipeImage,
                ImageDisplayUtils.resolveRecipeImageSource(recipe));
    }

    /**
     * Bind the ingredient count derived from the recipe's FoodPortion list.
     *
     * TheMealDB ingredients are stored as FoodPortion stubs (unresolved).
     * The count reflects how many ingredients the API returned (1–20).
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
     * Populate tag chips from recipe.getTags().
     *
     * Excludes internal tags ("has_video" and cuisine/category tags already
     * shown in the description) to avoid redundancy.
     * Chips are created programmatically and added to the ChipGroup.
     * The ChipGroup is hidden entirely if no displayable tags exist.
     */
    private void bindTags(@NonNull ViewHolder holder, @NonNull Recipe recipe) {
        holder.tagsChipGroup.removeAllViews();

        java.util.Set<String> tags = recipe.getTags();
        if (tags == null || tags.isEmpty()) {
            holder.tagsChipGroup.setVisibility(View.GONE);
            return;
        }

        // Tags to exclude - internal or already shown elsewhere in the card
        java.util.Set<String> excluded = new java.util.HashSet<>();
        excluded.add("has_video");
        excluded.add("themealdb");
        excluded.add("recipe");
        String description = recipe.getDescription("en");
        if (description != null) {
            for (String part : description.split(" · ")) {
                excluded.add(part.trim().toLowerCase());
            }
        }

        boolean hasDisplayableTags = false;
        for (String tag : tags) {
            if (excluded.contains(tag.toLowerCase())) continue;

            // Inflate from layout to guarantee correct theme-aware styling.
            // This avoids any programmatic style/color resolution issues.
            Chip chip = (Chip) android.view.LayoutInflater.from(context)
                    .inflate(R.layout.chip_tag_compact, holder.tagsChipGroup, false);
            chip.setEnsureMinTouchTargetSize(false);
            chip.setClickable(false);
            chip.setCheckable(false);
            chip.setFocusable(false);
            chip.setText(capitalize(tag));
            holder.tagsChipGroup.addView(chip);
            hasDisplayableTags = true;
        }

        holder.tagsChipGroup.setVisibility(hasDisplayableTags ? View.VISIBLE : View.GONE);
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
        final View       imageContainer;
        final ImageView  recipeImage;
        final TextView   recipeDescription;
        final TextView   ingredientCount;
        final ChipGroup  tagsChipGroup;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            recipeName        = itemView.findViewById(R.id.recipeName);
            sourceBadge       = itemView.findViewById(R.id.sourceBadge);
            productType       = itemView.findViewById(R.id.productType);
            imageContainer    = itemView.findViewById(R.id.imageContainer);
            recipeImage       = itemView.findViewById(R.id.recipeImage);
            recipeDescription = itemView.findViewById(R.id.recipeDescription);
            ingredientCount   = itemView.findViewById(R.id.ingredientCount);
            tagsChipGroup     = itemView.findViewById(R.id.tagsChipGroup);
        }
    }
}