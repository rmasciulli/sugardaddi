package li.masciul.sugardaddi.ui.utils;

import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

import li.masciul.sugardaddi.R;
import li.masciul.sugardaddi.core.interfaces.Nutritional;
import li.masciul.sugardaddi.core.interfaces.Searchable;
import li.masciul.sugardaddi.core.models.Nutrition;

/**
 * Binds the shared expandable_nutrition_detail.xml include (6-row
 * nutrition grid + tap-to-collapse hint) for any card representing a
 * Searchable - product or recipe, search card or meal portion card.
 *
 * Unlike CardThumbnailHelper, this doesn't need a per-type split: both
 * FoodProduct and Recipe implement Nutritional, so hasNutritionData()/
 * getNutrition() work identically regardless of which resolved. One
 * static method takes a plain Searchable and handles the instanceof
 * check itself.
 *
 * Not every item has data - see expandable_nutrition_detail.xml's
 * Javadoc for exactly which sources don't and why. When there's nothing
 * to show, hides the whole section AND the expand indicator - no dead
 * affordance that expands to an empty grid.
 */
public final class NutritionGridHelper {

    private NutritionGridHelper() {}

    /**
     * @param expandableSection expandable_nutrition_detail.xml's root view
     *                          (R.id.expandableDetailSection)
     * @param expandIndicator   the card's rotating chevron signaling expand
     *                          state - nullable; hidden along with the
     *                          section when there's nothing to show
     * @param isExpanded        current expand state for this position
     * @param item              the resolved Searchable this card represents;
     *                          null if not yet resolved
     */
    public static void bind(@NonNull View expandableSection,
                            @Nullable View expandIndicator,
                            boolean isExpanded,
                            @Nullable Searchable item) {
        bind(expandableSection, expandIndicator, isExpanded, item, null);
    }

    /**
     * Same as the four-arg bind(), plus an optional pre-computed
     * Nutrition that takes priority over the item's own raw
     * Nutritional.getNutrition() when present. Used by MealDetailsActivity
     * to show portion-scaled values (231g -> 271 kcal) instead of the
     * item's generic per-100g figure - the same object, same card, just a
     * different number depending on whether it's being reviewed as a
     * search result or as something already in a meal.
     *
     * @param nutritionOverride pre-computed Nutrition (e.g.
     *                          FoodPortion.calculateNutrition()) to show
     *                          instead of item.getNutrition(); null uses
     *                          the item's own raw value, same as the
     *                          four-arg overload
     */
    public static void bind(@NonNull View expandableSection,
                            @Nullable View expandIndicator,
                            boolean isExpanded,
                            @Nullable Searchable item,
                            @Nullable Nutrition nutritionOverride) {
        Nutrition nutrition = nutritionOverride;
        boolean hasData;
        if (nutrition != null) {
            hasData = nutrition.hasData();
        } else if (item instanceof Nutritional && ((Nutritional) item).hasNutritionData()) {
            nutrition = ((Nutritional) item).getNutrition();
            hasData = true;
        } else {
            hasData = false;
        }

        if (!hasData) {
            expandableSection.setVisibility(View.GONE);
            if (expandIndicator != null) expandIndicator.setVisibility(View.GONE);
            return;
        }

        if (expandIndicator != null) {
            expandIndicator.setVisibility(View.VISIBLE);
            expandIndicator.setRotation(isExpanded ? 180f : 0f);
        }

        if (!isExpanded) {
            expandableSection.setVisibility(View.GONE);
            return;
        }

        expandableSection.setVisibility(View.VISIBLE);
        populateGrid(expandableSection, nutrition);
    }

    private static void populateGrid(@NonNull View section, @Nullable Nutrition nutrition) {
        setText(section, R.id.detailEnergy, nutrition == null ? null :
                String.format(Locale.getDefault(), "%.0f kcal", nutrition.getEnergyKcal()));
        setText(section, R.id.detailCarbs, formatGrams(nutrition == null ? null : nutrition.getCarbohydrates()));
        setText(section, R.id.detailSugars, formatGrams(nutrition == null ? null : nutrition.getSugars()));
        setText(section, R.id.detailProteins, formatGrams(nutrition == null ? null : nutrition.getProteins()));
        setText(section, R.id.detailFats, formatGrams(nutrition == null ? null : nutrition.getFat()));
        setText(section, R.id.detailFibers, formatGrams(nutrition == null ? null : nutrition.getFiber()));
    }

    @Nullable
    private static String formatGrams(@Nullable Double value) {
        return value != null ? String.format(Locale.getDefault(), "%.1f g", value) : null;
    }

    private static void setText(@NonNull View root, int viewId, @Nullable String text) {
        TextView tv = root.findViewById(viewId);
        if (tv != null) tv.setText(text != null ? text : "-");
    }
}