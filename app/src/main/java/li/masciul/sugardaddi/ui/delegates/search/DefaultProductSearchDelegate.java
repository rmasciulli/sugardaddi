package li.masciul.sugardaddi.ui.delegates.search;

import android.content.Context;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import li.masciul.sugardaddi.R;
import li.masciul.sugardaddi.core.enums.DataSource;
import li.masciul.sugardaddi.core.enums.ProductType;
import li.masciul.sugardaddi.core.interfaces.Searchable;
import li.masciul.sugardaddi.core.models.FoodProduct;
import li.masciul.sugardaddi.core.models.ServingSize;
import li.masciul.sugardaddi.ui.delegates.ItemViewDelegate;
import li.masciul.sugardaddi.ui.delegates.ViewType;
import li.masciul.sugardaddi.ui.utils.GlideImageLoader;

import java.util.Locale;

/**
 * DefaultProductSearchDelegate - Fallback rendering for products from sources
 * without a dedicated delegate: USDA, USER, CUSTOM, IMPORTED, CACHE, or null.
 *
 * Layout mirrors item_search_product_off.xml exactly, without the score stack.
 * All the same fields: productType / brandName / categories / servingInfo.
 *
 * Must be registered AFTER OffProductSearchDelegate and CiqualProductSearchDelegate
 * in DelegateRegistry -- canHandle() accepts any remaining FoodProduct.
 *
 * @version 2.0
 */
public class DefaultProductSearchDelegate
        implements ItemViewDelegate<DefaultProductSearchDelegate.ViewHolder> {

    private final Context context;

    public DefaultProductSearchDelegate(@NonNull Context context) {
        this.context = context;
    }

    // ========== ItemViewDelegate CONTRACT ==========

    @Override
    public int getViewType() {
        return ViewType.PRODUCT_DEFAULT;
    }

    @Override
    public int getLayoutResId() {
        return R.layout.item_search_product_default;
    }

    /** Catches any FoodProduct not matched by OFF or Ciqual. Must be last food delegate. */
    @Override
    public boolean canHandle(@NonNull Searchable item) {
        return item.getProductType() == ProductType.FOOD;
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
        bindSourceBadge(holder, product);
        bindProductType(holder, product);
        bindBrand(holder, product, language);
        bindCategory(holder, product, language);
        bindServingInfo(holder, product);
        bindImage(holder, product);
    }

    // ========== BINDING HELPERS ==========

    private void bindName(ViewHolder holder, FoodProduct product, String language) {
        String name = product.getDisplayName(language);
        holder.productName.setText(
                name != null && !name.trim().isEmpty() ? name : "-");
    }

    private void bindSourceBadge(ViewHolder holder, FoodProduct product) {
        DataSource source = product.getDataSource();
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

    /**
     * productType: "Food product" or "Recipe" based on ProductType enum.
     * Uniform style: LabelSmall bold, colorPrimary - same as OFF and Ciqual delegates.
     */
    private void bindProductType(ViewHolder holder, FoodProduct product) {
        int labelRes = product.getProductType() == li.masciul.sugardaddi.core.enums.ProductType.RECIPE
                ? R.string.product_type_recipe
                : R.string.product_type_food;
        holder.productType.setText(labelRes);
        holder.productType.setVisibility(View.VISIBLE);
    }

    private void bindBrand(ViewHolder holder, FoodProduct product, String language) {
        String brand = product.getBrand(language);
        if (brand != null && !brand.trim().isEmpty()
                && !brand.equalsIgnoreCase("unknown")) {
            holder.brandName.setText(brand.trim());
            holder.brandName.setVisibility(View.VISIBLE);
        } else {
            holder.brandName.setVisibility(View.GONE);
        }
    }

    /** Category in sentence case, 2 lines */
    private void bindCategory(ViewHolder holder, FoodProduct product, String language) {
        String raw = product.getPrimaryCategory(language);
        if (raw != null && !raw.trim().isEmpty()) {
            String lower = raw.trim().toLowerCase(Locale.getDefault());
            String display = Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
            holder.categories.setText(display);
            holder.categories.setVisibility(View.VISIBLE);
        } else {
            holder.categories.setVisibility(View.GONE);
        }
    }

    private void bindServingInfo(ViewHolder holder, FoodProduct product) {
        StringBuilder info = new StringBuilder();
        ServingSize servingSize = product.getServingSize();
        String servingText = servingSize != null ? servingSize.getDisplayText() : null;
        info.append(servingText != null && !servingText.trim().isEmpty()
                ? servingText : "100g");
        if (product.getNutrition() != null) {
            Double energy = product.getNutrition().getEnergyKcal();
            if (energy != null && energy > 0) {
                info.append(" \u00b7 ");
                info.append(String.format(Locale.getDefault(), "%.0f kcal/100g", energy));
            }
        }
        holder.servingInfo.setText(info.toString());
        holder.servingInfo.setVisibility(View.VISIBLE);
    }

    private void bindImage(ViewHolder holder, FoodProduct product) {
        String imageUrl = product.getImageUrl();
        if (imageUrl != null && !imageUrl.trim().isEmpty()) {
            GlideImageLoader.load(context, imageUrl)
                    .placeholder(R.drawable.ic_food_placeholder)
                    .error(R.drawable.ic_food_error)
                    .centerCrop()
                    .into(holder.productImage);
        } else {
            holder.productImage.setImageResource(R.drawable.ic_food_placeholder);
        }
    }

    // ========== VIEW HOLDER ==========

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView  productName;
        final TextView  sourceBadge;
        final TextView  productType;   // source qualifier, e.g. "USDA"
        final TextView  brandName;
        final TextView  categories;
        final TextView  servingInfo;
        final ImageView productImage;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            productName  = itemView.findViewById(R.id.productName);
            sourceBadge  = itemView.findViewById(R.id.sourceBadge);
            productType  = itemView.findViewById(R.id.productType);
            brandName    = itemView.findViewById(R.id.brandName);
            categories   = itemView.findViewById(R.id.categories);
            servingInfo  = itemView.findViewById(R.id.servingInfo);
            productImage = itemView.findViewById(R.id.productImage);
        }
    }
}