package li.masciul.sugardaddi.ui.delegates.search;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import li.masciul.sugardaddi.R;
import li.masciul.sugardaddi.core.enums.DataSource;
import li.masciul.sugardaddi.core.enums.ProductType;
import li.masciul.sugardaddi.core.interfaces.Searchable;
import li.masciul.sugardaddi.core.models.FoodProduct;
import li.masciul.sugardaddi.core.models.Nutrition;
import li.masciul.sugardaddi.ui.delegates.ItemViewDelegate;
import li.masciul.sugardaddi.ui.delegates.ViewType;

import java.util.Locale;

/**
 * USDAProductSearchDelegate — Search result rendering for USDA FoodData Central entries.
 *
 * USDA FoodData Central is a US government scientific database:
 * - No product photos (scientific reference data, no images)
 * - No brand, no NutriScore, no EcoScore
 * - Names are scientific USDA descriptions (sentence-cased by USDAMapper)
 * - Category is the primary metadata (USDA food category taxonomy)
 * - Nutrition: carbohydrates + kcal badge (same as Ciqual)
 *
 * DISPLAYS:
 * - Product name (bold, 1 line)
 * - Source badge ("USDA" pill, secondary container)
 * - productType ("Food product" label)
 * - Category (Roboto Condensed light, sentence case)
 * - Nutrition summary (carbs in grams, per 100g)
 * - Kcal badge (tertiary container pill)
 *
 * Mirrors CiqualProductSearchDelegate exactly — same layout structure,
 * same binding logic, different source check and badge text.
 *
 * @version 1.0
 */
public class USDAProductSearchDelegate
        implements ItemViewDelegate<USDAProductSearchDelegate.ViewHolder> {

    private final Context context;

    public USDAProductSearchDelegate(@NonNull Context context) {
        this.context = context;
    }

    // ========== ItemViewDelegate CONTRACT ==========

    @Override
    public int getViewType() {
        return ViewType.PRODUCT_USDA;
    }

    @Override
    public int getLayoutResId() {
        return R.layout.item_search_product_usda;
    }

    @Override
    public boolean canHandle(@NonNull Searchable item) {
        return item.getProductType() == ProductType.FOOD
                && item.getDataSource() == DataSource.USDA;
    }

    @NonNull
    @Override
    public ViewHolder createViewHolder(@NonNull View view) {
        return new ViewHolder(view);
    }

    @Override
    public void bind(@NonNull ViewHolder holder, @NonNull Searchable item,
                     @NonNull String language) {
        FoodProduct product = (FoodProduct) item;
        bindName(holder, product, language);
        bindSourceBadge(holder);
        bindProductType(holder);
        bindCategory(holder, product, language);
        bindNutritionSummary(holder, product, language);
        bindKcalBadge(holder, product);
    }

    // ========== BINDING HELPERS ==========

    private void bindName(@NonNull ViewHolder holder, @NonNull FoodProduct product,
                          @NonNull String language) {
        String name = product.getDisplayName(language);
        holder.productName.setText(name != null && !name.trim().isEmpty() ? name : "-");
    }

    /** "USDA" badge — secondary container, same styling as Ciqual badge. */
    private void bindSourceBadge(@NonNull ViewHolder holder) {
        // Use short label "USDA" rather than the full source name to keep the pill compact.
        holder.sourceBadge.setText(R.string.source_name_usda);
        holder.sourceBadge.setVisibility(View.VISIBLE);
    }

    private void bindProductType(@NonNull ViewHolder holder) {
        holder.productType.setText(R.string.product_type_food);
        holder.productType.setVisibility(View.VISIBLE);
    }

    /**
     * Category from USDA food category taxonomy.
     * USDA returns English-only categories (e.g. "Vegetables and Vegetable Products").
     * USDAMapper.toSentenceCase() already sentence-cases these during mapping.
     */
    private void bindCategory(@NonNull ViewHolder holder, @NonNull FoodProduct product,
                              @NonNull String language) {
        String rawCategory = product.getCategoriesText(language);
        if (rawCategory == null || rawCategory.trim().isEmpty()) {
            rawCategory = product.getPrimaryCategory(language);
        }
        // USDA categories are flat strings (no breadcrumb separator), so no
        // breadcrumb formatting is needed — just display directly.
        if (rawCategory != null && !rawCategory.trim().isEmpty()) {
            holder.category.setText(rawCategory.trim());
            holder.category.setVisibility(View.VISIBLE);
        } else {
            holder.category.setVisibility(View.GONE);
        }
    }

    /**
     * Carbohydrate summary: "x.xg carbohydrates per 100g" (EN) / "x.xg glucides per 100g" (FR).
     * USDA is English-only but the UI language may be French — honour the language param.
     */
    private void bindNutritionSummary(@NonNull ViewHolder holder, @NonNull FoodProduct product,
                                      @NonNull String language) {
        Nutrition nutrition = product.getNutrition();
        if (nutrition == null) {
            holder.nutritionSummary.setVisibility(View.GONE);
            return;
        }
        Double carbs = nutrition.getCarbohydrates();
        if (carbs != null && carbs > 0) {
            String label = "fr".equals(language) ? "glucides" : "carbohydrates";
            String unit  = product.isLiquid() ? "100ml" : "100g";
            holder.nutritionSummary.setText(
                    String.format(Locale.getDefault(), "%.1fg of %s per %s", carbs, label, unit));
            holder.nutritionSummary.setVisibility(View.VISIBLE);
        } else {
            holder.nutritionSummary.setVisibility(View.GONE);
        }
    }

    /** Kcal pill badge — integer value, tertiary container styling. */
    private void bindKcalBadge(@NonNull ViewHolder holder, @NonNull FoodProduct product) {
        Nutrition nutrition = product.getNutrition();
        if (nutrition == null) {
            holder.kcalBadge.setVisibility(View.GONE);
            return;
        }
        Double kcal = nutrition.getEnergyKcal();
        if (kcal != null && kcal > 0) {
            holder.kcalBadge.setText(
                    String.format(Locale.getDefault(), "%.0f kcal", kcal));
            holder.kcalBadge.setVisibility(View.VISIBLE);
        } else {
            holder.kcalBadge.setVisibility(View.GONE);
        }
    }

    // ========== VIEW HOLDER ==========

    public static class ViewHolder extends RecyclerView.ViewHolder {

        final TextView productName;
        final TextView sourceBadge;
        final TextView productType;
        final TextView category;
        final TextView nutritionSummary;
        final TextView kcalBadge;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            productName      = itemView.findViewById(R.id.productName);
            sourceBadge      = itemView.findViewById(R.id.sourceBadge);
            productType      = itemView.findViewById(R.id.productType);
            category         = itemView.findViewById(R.id.category);
            nutritionSummary = itemView.findViewById(R.id.nutritionSummary);
            kcalBadge        = itemView.findViewById(R.id.kcalBadge);
        }
    }
}