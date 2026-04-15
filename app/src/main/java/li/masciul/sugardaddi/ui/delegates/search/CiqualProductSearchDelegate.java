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
 * CiqualProductSearchDelegate - Search result rendering for Ciqual scientific database entries.
 *
 * Ciqual (ANSES) is a French scientific reference database:
 * - No product photos (no image container in layout)
 * - No brand, no NutriScore, no EcoScore
 * - Names are long scientific descriptions (2-line title)
 * - Category is the primary metadata (BodyMedium, prominent)
 * - Nutrition: carbohydrates (glucides) + kcal badge
 *
 * DISPLAYS:
 * - Product name (system bold, 2 lines)
 * - Source badge ("CIQUAL" pill, secondary container)
 * - productType ("Scientific data" italic label, visible)
 * - Category (Roboto Condensed light, sentence case, BodyMedium)
 * - Nutrition summary (carbs in grams, per 100g)
 * - Kcal badge (tertiary container pill)
 *
 * @version 2.0
 */
public class CiqualProductSearchDelegate
        implements ItemViewDelegate<CiqualProductSearchDelegate.ViewHolder> {

    private final Context context;

    public CiqualProductSearchDelegate(@NonNull Context context) {
        this.context = context;
    }

    // ========== ItemViewDelegate CONTRACT ==========

    @Override
    public int getViewType() {
        return ViewType.PRODUCT_CIQUAL;
    }

    @Override
    public int getLayoutResId() {
        return R.layout.item_search_product_ciqual;
    }

    @Override
    public boolean canHandle(@NonNull Searchable item) {
        return item.getProductType() == ProductType.FOOD
                && item.getDataSource() == DataSource.CIQUAL;
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

    private void bindName(ViewHolder holder, FoodProduct product, String language) {
        String name = product.getDisplayName(language);
        holder.productName.setText(name != null && !name.trim().isEmpty() ? name : "-");
    }

    /** "CIQUAL" badge in secondary container (distinct from OFF's primary container) */
    private void bindSourceBadge(ViewHolder holder) {
        holder.sourceBadge.setText(context.getString(R.string.source_name_ciqual));
        holder.sourceBadge.setVisibility(View.VISIBLE);
    }

    /**
     * productType: "Food product" label, uniform with OFF and Default delegates.
     * LabelSmall bold, colorPrimary - same style across all product cards.
     */
    private void bindProductType(ViewHolder holder) {
        holder.productType.setText(R.string.product_type_food);
        holder.productType.setVisibility(View.VISIBLE);
    }

    /**
     * Category in sentence case: first character uppercase, rest lowercase.
     * Ciqual API returns all-lowercase strings ("chocolate and chocolate products").
     */
    private void bindCategory(ViewHolder holder, FoodProduct product, String language) {
        // For Ciqual products the category is stored as a breadcrumb:
        // "sugar and confectionery > chocolate and chocolate products"
        // We display only the most specific level (last segment after " > ").
        // For other sources, getPrimaryCategory returns a plain string.
        // Use the full breadcrumb for richer context:
        // "dairy products > dairy desserts" → "Dairy products › dairy desserts"
        // Falls back to getPrimaryCategory if no breadcrumb stored.
        String rawCategory = product.getCategoriesText(language);
        if (rawCategory == null || rawCategory.trim().isEmpty()) {
            rawCategory = product.getPrimaryCategory(language);
        }
        String category = formatBreadcrumb(rawCategory);
        if (category != null && !category.isEmpty()) {
            holder.category.setText(category);
            holder.category.setVisibility(View.VISIBLE);
        } else {
            holder.category.setVisibility(View.GONE);
        }
    }

    /**
     * Carbohydrates summary: "x.xg carbohydrates per 100g" (EN) / "x.xg glucides per 100g" (FR).
     * Font bumped to BodySmall for readability (was LabelSmall).
     */
    private void bindNutritionSummary(ViewHolder holder, FoodProduct product,
                                      String language) {
        Nutrition nutrition = product.getNutrition();
        if (nutrition == null) {
            holder.nutritionSummary.setVisibility(View.GONE);
            return;
        }
        Double carbs = nutrition.getCarbohydrates();
        if (carbs != null && carbs > 0) {
            String label = "fr".equals(language) ? "glucides" : "carbohydrates";
            String unit = product.isLiquid() ? "100ml" : "100g";
            String text = String.format(Locale.getDefault(),
                    "%.1fg of %s per %s", carbs, label, unit);
            holder.nutritionSummary.setText(text);
            holder.nutritionSummary.setVisibility(View.VISIBLE);
        } else {
            holder.nutritionSummary.setVisibility(View.GONE);
        }
    }

    /** Kcal pill badge, integer value, tertiary container styling */
    private void bindKcalBadge(ViewHolder holder, FoodProduct product) {
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

    // ========== HELPERS ==========

    /**
     * Formats a " > " breadcrumb for display:
     * - Replaces " > " with " › " (single right angle quotation mark)
     * - Sentence-cases only the first character
     * "dairy products > dairy desserts" → "Dairy products › dairy desserts"
     * "sugar and confectionery" → "Sugar and confectionery"
     */
    @Nullable
    private static String formatBreadcrumb(@Nullable String breadcrumb) {
        if (breadcrumb == null || breadcrumb.trim().isEmpty()) return null;
        // Keep only the last 2 levels — level 1 (e.g. "Milk and milk products")
        // is too broad to be useful in a compact search card.
        // "milk and milk products > dairy products > dairy desserts"
        //   → "Dairy products › dairy desserts"
        String[] parts = breadcrumb.trim().split(" > ");
        String result;
        if (parts.length >= 2) {
            result = parts[parts.length - 2].trim() + " › " + parts[parts.length - 1].trim();
        } else {
            result = parts[0].trim();
        }
        if (result.isEmpty()) return null;
        return Character.toUpperCase(result.charAt(0)) + result.substring(1);
    }

    // ========== VIEW HOLDER ==========

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView productName;
        final TextView sourceBadge;
        final TextView productType;       // "Scientific data" italic label
        final TextView category;
        final TextView nutritionSummary;  // carbs per 100g
        final TextView kcalBadge;         // kcal pill

        ViewHolder(@NonNull View itemView) {
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