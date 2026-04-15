package li.masciul.sugardaddi.ui.delegates.search;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;
import android.view.ViewGroup;
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
import li.masciul.sugardaddi.utils.category.CategoryCleaner;
import li.masciul.sugardaddi.ui.delegates.ItemViewDelegate;
import li.masciul.sugardaddi.ui.delegates.ViewType;
import li.masciul.sugardaddi.ui.utils.GlideImageLoader;
import li.masciul.sugardaddi.utils.scores.ScoreOverlayHelper;
import li.masciul.sugardaddi.utils.scores.ScoreUtils;

import java.util.Locale;

/**
 * OffProductSearchDelegate - Search result rendering for OpenFoodFacts products.
 *
 * DISPLAYS:
 * - Product name (system bold, 1 line)
 * - Source badge ("OFF" pill, top-right)
 * - productType label (explicitly GONE for standard food products)
 * - Brand name (Inter semi-bold, primary color)
 * - Primary category (Roboto Condensed light, sentence case, 2 lines)
 * - Serving info with kcal (Roboto Condensed italic)
 * - Product image (72dp, loaded via Glide)
 * - Green-Score leaf icon
 * - Nutri-Score TPL sticker
 *
 * @version 2.0
 */
public class OffProductSearchDelegate
        implements ItemViewDelegate<OffProductSearchDelegate.ViewHolder> {

    private final Context context;

    public OffProductSearchDelegate(@NonNull Context context) {
        this.context = context;
    }

    // ========== ItemViewDelegate CONTRACT ==========

    @Override
    public int getViewType() {
        return ViewType.PRODUCT_OFF;
    }

    @Override
    public int getLayoutResId() {
        return R.layout.item_search_product_off;
    }

    @Override
    public boolean canHandle(@NonNull Searchable item) {
        return item.getProductType() == ProductType.FOOD
                && item.getDataSource() == DataSource.OPENFOODFACTS;
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
        bindBrand(holder, product, language);
        bindCategory(holder, product, language);
        bindServingInfo(holder, product);
        bindImage(holder, product);
        bindGreenScore(holder, product);
        bindNutriScore(holder, product);
    }

    // ========== BINDING HELPERS ==========

    private void bindName(ViewHolder holder, FoodProduct product, String language) {
        String name = product.getDisplayName(language);
        if (name != null && !name.trim().isEmpty()) {
            holder.productName.setText(sentenceCase(name));
        } else {
            holder.productName.setText("Unknown Product");
        }
    }

    /**
     * Apply sentence-case only when the name is entirely lowercase.
     * "belvita" → "Belvita"
     * "Excellence 85% Cacao" → unchanged (mixed case — user intent preserved)
     * "belVita" → unchanged (intentional mixed case brand name)
     * "CHOCOLAT NOIR" → unchanged (all-caps — likely intentional, leave it)
     */
    @NonNull
    private static String sentenceCase(@NonNull String name) {
        String trimmed = name.trim();
        if (trimmed.isEmpty()) return trimmed;
        // Only fix if every letter in the name is lowercase
        boolean allLower = trimmed.chars()
                .filter(Character::isLetter)
                .allMatch(Character::isLowerCase);
        if (!allLower) return trimmed; // Already has uppercase — trust the data
        return Character.toUpperCase(trimmed.charAt(0)) + trimmed.substring(1);
    }

    /** "OFF" pill badge, always visible for OpenFoodFacts products */
    private void bindSourceBadge(ViewHolder holder) {
        holder.sourceBadge.setText("OFF");
        holder.sourceBadge.setVisibility(View.VISIBLE);
    }

    /**
     * productType: "Food product" label, always visible on OFF cards.
     * Uniform style across all product delegates: LabelSmall bold, colorPrimary.
     */
    private void bindProductType(ViewHolder holder) {
        holder.productType.setText(R.string.product_type_food);
        holder.productType.setVisibility(View.VISIBLE);
    }

    private void bindBrand(ViewHolder holder, FoodProduct product, String language) {
        String brand = product.getBrand(language);
        if (brand != null && !brand.trim().isEmpty()
                && !brand.equalsIgnoreCase("unknown")) {
            String[] brands = brand.split(",");
            String cleanBrand = CategoryCleaner.capitalizeWords(brands[0].trim());
            holder.brandName.setText(cleanBrand);
            holder.brandName.setVisibility(View.VISIBLE);
        } else {
            holder.brandName.setVisibility(View.GONE);
        }
    }

    /**
     * Category in sentence case: first character uppercase, rest lowercase.
     * Handles both Agribalyse title-cased strings ("Cocoa And Its Products")
     * and raw cleaned tag strings.
     */
    private void bindCategory(ViewHolder holder, FoodProduct product, String language) {
        String category = product.getPrimaryCategory(language);
        if (category != null && !category.trim().isEmpty()) {
            String lower = category.trim().toLowerCase(Locale.getDefault());
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

    // Compact score dimensions for search result cards.
    // ScoreOverlayHelper defaults are 32dp (leaf) and 48dp (nutriscore) -- too large for a list card.
    // We override after creation: the ViewTreeObserver inside fires on next layout pass
    // and recalculates text/strip proportions correctly at whatever size we set here.
    private static final int CARD_LEAF_SIZE_DP         = 24;
    private static final int CARD_NUTRISCORE_HEIGHT_DP = 36;

    private void bindGreenScore(ViewHolder holder, FoodProduct product) {
        String ecoScore = product.getEcoScore();
        int leafDrawable = ScoreUtils.getGreenScoreLeaf(ecoScore);
        // scoreContainer is HORIZONTAL: leaf left (24dp square), nutriscore right.
        // Simple fixed size - no gravity or centering needed.
        ScoreOverlayHelper.setupStickerImageView(
                context, holder.greenScoreLeaf, leafDrawable, CARD_LEAF_SIZE_DP);
        holder.greenScoreLeaf.setVisibility(View.VISIBLE);
        if (holder.scoreContainer != null) {
            holder.scoreContainer.setVisibility(View.VISIBLE);
        }
    }


    private void bindNutriScore(ViewHolder holder, FoodProduct product) {
        if (holder.nutriScoreContainer == null) return;
        String nutriScore = product.getNutriScore();
        holder.nutriScoreContainer.removeAllViews();
        try {
            // Create at default size, then immediately override to compact card size.
            // The ViewTreeObserver inside the sticker recalculates text/strip proportions on layout.
            FrameLayout sticker = ScoreOverlayHelper.createNutriScoreTplWithText(
                    context, nutriScore, "horizontal");
            if (sticker != null) {
                float density = context.getResources().getDisplayMetrics().density;
                int targetHeightPx = Math.round(CARD_NUTRISCORE_HEIGHT_DP * density);

                // Override sticker size using FrameLayout.LayoutParams (not ViewGroup.LayoutParams)
                // so we can set Gravity.END - anchors sticker to right edge of nutriScoreContainer.
                // Using plain ViewGroup.LayoutParams loses gravity and the sticker floats left.
                android.widget.FrameLayout.LayoutParams stickerParams =
                        new android.widget.FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                targetHeightPx,
                                android.view.Gravity.TOP | android.view.Gravity.END);
                sticker.setLayoutParams(stickerParams);

                // Constrain the host FrameLayout height to prevent it from expanding beyond target
                ViewGroup.LayoutParams containerParams = holder.nutriScoreContainer.getLayoutParams();
                if (containerParams != null) {
                    containerParams.height = targetHeightPx;
                    holder.nutriScoreContainer.setLayoutParams(containerParams);
                }

                holder.nutriScoreContainer.addView(sticker);
                holder.nutriScoreContainer.setVisibility(View.VISIBLE);
                if (holder.scoreContainer != null) {
                    holder.scoreContainer.setVisibility(View.VISIBLE);
                }
            } else {
                holder.nutriScoreContainer.setVisibility(View.GONE);
            }
        } catch (Exception e) {
            holder.nutriScoreContainer.setVisibility(View.GONE);
        }
    }

    // ========== VIEW HOLDER ==========

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView    productName;
        final TextView    sourceBadge;
        final TextView    productType;      // GONE for standard OFF food
        final TextView    brandName;
        final TextView    categories;
        final TextView    servingInfo;
        final ImageView   productImage;
        final ImageView   greenScoreLeaf;
        final FrameLayout nutriScoreContainer;
        final android.view.ViewGroup scoreContainer;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            productName         = itemView.findViewById(R.id.productName);
            sourceBadge         = itemView.findViewById(R.id.sourceBadge);
            productType         = itemView.findViewById(R.id.productType);
            brandName           = itemView.findViewById(R.id.brandName);
            categories          = itemView.findViewById(R.id.categories);
            servingInfo         = itemView.findViewById(R.id.servingInfo);
            productImage        = itemView.findViewById(R.id.productImage);
            greenScoreLeaf      = itemView.findViewById(R.id.greenScoreLeaf);
            nutriScoreContainer = itemView.findViewById(R.id.nutriScoreContainer);
            scoreContainer      = itemView.findViewById(R.id.scoreContainer);
        }
    }
}