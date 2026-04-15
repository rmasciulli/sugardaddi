package li.masciul.sugardaddi.ui.delegates.detail;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.FrameLayout;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import li.masciul.sugardaddi.R;
import li.masciul.sugardaddi.core.enums.NutritionLabelMode;
import li.masciul.sugardaddi.core.enums.ProductType;
import li.masciul.sugardaddi.core.interfaces.Searchable;
import li.masciul.sugardaddi.core.models.FoodProduct;
import li.masciul.sugardaddi.core.models.ServingSize;
import li.masciul.sugardaddi.ui.components.AllergenIconHelper;
import li.masciul.sugardaddi.ui.components.NutritionLabelManager;
import li.masciul.sugardaddi.ui.utils.GlideImageLoader;
import li.masciul.sugardaddi.utils.scores.ScoreOverlayHelper;
import li.masciul.sugardaddi.utils.scores.ScoreUtils;

import java.util.Locale;

/**
 * DefaultProductDetailRenderer - Catch-all fallback renderer for any FoodProduct.
 *
 * Handles any product that wasn't claimed by a more specific renderer
 * (i.e., not OpenFoodFacts, not Ciqual). This includes:
 *   - USDA FoodData products
 *   - User-created foods
 *   - Any future data source added to the app
 *
 * GRACEFUL DEGRADATION:
 *   - Hero image: shown when imageUrl available, hidden otherwise
 *   - Scores card: shown only when at least one score (nutri/eco/nova) is non-null
 *   - Within scores card: each badge shown only when its data is available
 *   - Allergens: shown only when allergenFlags != 0
 *   - Nutrition: shown only when product.hasNutritionData() is true
 *
 * NO ASSUMPTIONS are made about what data is available. Every section
 * is independently guarded.
 *
 * REGISTRATION:
 * Must always be registered LAST in DetailRendererRegistry.
 * Its supports() is a catch-all that returns true for any FoodProduct.
 *
 * @version 1.0
 */
public class DefaultProductDetailRenderer implements DetailRenderer {

    private static final String TAG = "DefaultProductDetailRenderer";

    private final Context context;

    private NutritionLabelManager nutritionLabelManager;
    private TextWatcher customAmountWatcher;

    public DefaultProductDetailRenderer(@NonNull Context context) {
        this.context = context;
    }

    // ========== DetailRenderer CONTRACT ==========

    /**
     * Catch-all: accepts any FoodProduct not handled by a more specific renderer.
     * Register this LAST in DetailRendererRegistry so specific renderers get priority.
     */
    @Override
    public boolean supports(@NonNull Searchable item) {
        // Only handles food products — recipes are handled by a separate renderer (future)
        return item.getProductType() == ProductType.FOOD;
    }

    @NonNull
    @Override
    public View inflate(@NonNull LayoutInflater inflater, @NonNull ViewGroup container) {
        return inflater.inflate(R.layout.detail_default_product, container, false);
    }

    @Override
    public void populate(@NonNull View view, @NonNull Searchable item, @NonNull String language) {
        if (!(item instanceof FoodProduct)) return;
        FoodProduct product = (FoodProduct) item;

        populateHeroImage(view, product);
        populateHeader(view, product, language);
        populateScores(view, product);
        populateAllergens(view, product);
        populateNutrition(view, product, language);
        // Attribution panel last — informational, never obscures critical content
        DetailRendererUtils.populateAttribution(context, view, product);
    }

    @Override
    public void onAmountChanged(@NonNull View view, @NonNull Searchable item,
                                double amount, @NonNull String language) {
        if (nutritionLabelManager != null && amount > 0) {
            nutritionLabelManager.updateCustomAmount(amount);
        }
    }

    @Override
    public String getToolbarTitle(@NonNull Searchable item, @NonNull String language) {
        if (item instanceof FoodProduct) {
            String name = ((FoodProduct) item).getDisplayName(language);
            return (name != null && !name.trim().isEmpty()) ? name : null;
        }
        return null;
    }

    @Override
    public void destroy() {
        if (nutritionLabelManager != null) {
            nutritionLabelManager.clear();
            nutritionLabelManager = null;
        }
        customAmountWatcher = null;
    }

    // ========== POPULATE HELPERS ==========

    /**
     * Hero image: shown only when imageUrl is non-null and non-empty.
     * The ImageView is GONE by default in the layout; we set it VISIBLE here.
     */
    private void populateHeroImage(@NonNull View view, @NonNull FoodProduct product) {
        ImageView heroImage = view.findViewById(R.id.heroImage);
        if (heroImage == null) return;

        String imageUrl = product.getImageUrl();
        if (imageUrl != null && !imageUrl.trim().isEmpty()) {
            heroImage.setVisibility(View.VISIBLE);
            GlideImageLoader.load(context, imageUrl)
                    .placeholder(R.drawable.ic_food_placeholder)
                    .error(R.drawable.ic_food_error)
                    .centerCrop()
                    .into(heroImage);
        } else {
            // No image: hide the ImageView entirely (no empty grey box)
            heroImage.setVisibility(View.GONE);
        }
    }

    /**
     * Header: name always shown; brand, quantity, category conditionally shown.
     */
    private void populateHeader(@NonNull View view, @NonNull FoodProduct product,
                                @NonNull String language) {
        TextView productName = view.findViewById(R.id.productName);
        if (productName != null) {
            String name = product.getDisplayName(language);
            productName.setText((name != null && !name.trim().isEmpty())
                    ? name : context.getString(R.string.unknown_product));
        }

        TextView brandName = view.findViewById(R.id.brandName);
        if (brandName != null) {
            String brand = product.getBrand(language);
            if (brand != null && !brand.trim().isEmpty()
                    && !brand.equalsIgnoreCase("unknown")) {
                brandName.setText(brand.split(",")[0].trim());
                brandName.setVisibility(View.VISIBLE);
            } else {
                brandName.setVisibility(View.GONE);
            }
        }

        TextView quantityText = view.findViewById(R.id.quantityText);
        if (quantityText != null) {
            String quantity = product.getQuantity();
            if (quantity != null && !quantity.trim().isEmpty()) {
                quantityText.setText(quantity);
                quantityText.setVisibility(View.VISIBLE);
            } else {
                quantityText.setVisibility(View.GONE);
            }
        }

        TextView categoryText = view.findViewById(R.id.categoryText);
        if (categoryText != null) {
            String category = product.getPrimaryCategory(language);
            if (category != null && !category.trim().isEmpty()) {
                categoryText.setText(category);
                categoryText.setVisibility(View.VISIBLE);
            } else {
                categoryText.setVisibility(View.GONE);
            }
        }
    }

    /**
     * Scores: show scoresRow only when at least one score is valid.
     * Individual stickers/badges shown/hidden independently within the row.
     * No column wrapper LinearLayouts - stickers sit directly in the horizontal row.
     */
    private void populateScores(@NonNull View view, @NonNull FoodProduct product) {
        View scoresRow = view.findViewById(R.id.scoresRow);
        if (scoresRow == null) return;

        boolean hasNutriScore = ScoreUtils.isValidNutriScoreGrade(product.getNutriScore());
        boolean hasGreenScore = ScoreUtils.isValidGreenScoreGrade(product.getEcoScore());
        boolean hasNovaGroup  = ScoreUtils.isValidNovaGroup(product.getNovaGroup());

        if (!hasNutriScore && !hasGreenScore && !hasNovaGroup) {
            scoresRow.setVisibility(View.GONE);
            return;
        }

        scoresRow.setVisibility(View.VISIBLE);

        // Nutri-Score
        FrameLayout nutriContainer = view.findViewById(R.id.nutriScoreStickerContainer);
        if (nutriContainer != null) {
            if (hasNutriScore) {
                nutriContainer.removeAllViews();
                FrameLayout sticker = ScoreOverlayHelper.createNutriScoreTplWithText(
                        context, product.getNutriScore(), "horizontal");
                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.MATCH_PARENT);
                params.gravity = android.view.Gravity.CENTER;
                sticker.setLayoutParams(params);
                nutriContainer.addView(sticker);
                nutriContainer.setVisibility(View.VISIBLE);
            } else {
                nutriContainer.setVisibility(View.GONE);
            }
        }

        // Green-Score
        ImageView greenSticker = view.findViewById(R.id.greenScoreSticker);
        if (greenSticker != null) {
            if (hasGreenScore) {
                greenSticker.setImageResource(ScoreUtils.getGreenScoreHorizontal(product.getEcoScore()));
                greenSticker.setVisibility(View.VISIBLE);
            } else {
                greenSticker.setVisibility(View.GONE);
            }
        }

        // Nova Group
        ImageView novaBadge = view.findViewById(R.id.novaGroupBadge);
        if (novaBadge != null) {
            if (hasNovaGroup) {
                novaBadge.setImageResource(ScoreUtils.getNovaGroupDrawable(product.getNovaGroup()));
                novaBadge.setVisibility(View.VISIBLE);
            } else {
                novaBadge.setVisibility(View.GONE);
            }
        }
    }

    private void populateAllergens(@NonNull View view, @NonNull FoodProduct product) {
        View allergenDivider = view.findViewById(R.id.allergenDivider);
        View allergenSection = view.findViewById(R.id.allergenSection);

        int allergenFlags = product.getAllergenFlags();

        if (allergenFlags == 0) {
            if (allergenDivider != null) allergenDivider.setVisibility(View.GONE);
            if (allergenSection != null) allergenSection.setVisibility(View.GONE);
            return;
        }

        FrameLayout iconsContainer = view.findViewById(R.id.allergenIconsContainer);
        if (iconsContainer == null) {
            if (allergenDivider != null) allergenDivider.setVisibility(View.GONE);
            if (allergenSection != null) allergenSection.setVisibility(View.GONE);
            return;
        }

        iconsContainer.removeAllViews();
        View icons = AllergenIconHelper.createMultipleIconsGrid(context, allergenFlags, 60, true);
        iconsContainer.addView(icons);

        if (allergenDivider != null) allergenDivider.setVisibility(View.VISIBLE);
        if (allergenSection != null) allergenSection.setVisibility(View.VISIBLE);
    }

    private void populateNutrition(@NonNull View view, @NonNull FoodProduct product,
                                   @NonNull String language) {
        LinearLayout nutritionContainer = view.findViewById(R.id.nutritionContainer);
        if (nutritionContainer == null) return;

        nutritionLabelManager = new NutritionLabelManager(
                context, nutritionContainer, NutritionLabelMode.DETAILED);

        double defaultAmount = getSmartDefaultAmount(product);
        nutritionLabelManager.displayProduct(product, defaultAmount);

        TextInputLayout amountLayout = view.findViewById(R.id.customAmountInputLayout);
        TextInputEditText amountInput = view.findViewById(R.id.customAmountEditText);

        if (amountLayout != null && amountInput != null) {
            updateAmountHint(amountLayout, product);

            customAmountWatcher = new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void afterTextChanged(Editable s) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (nutritionLabelManager == null) return;
                    try {
                        String input = s.toString().trim();
                        if (!input.isEmpty()) {
                            double amount = Double.parseDouble(input);
                            if (amount > 0) nutritionLabelManager.updateCustomAmount(amount);
                        }
                    } catch (NumberFormatException ignored) {}
                }
            };
            amountInput.addTextChangedListener(customAmountWatcher);
        }
    }

    // ========== HELPERS ==========

    private void updateAmountHint(@NonNull TextInputLayout layout, @NonNull FoodProduct product) {
        ServingSize serving = product.getServingSize();
        String unit = product.isLiquid() ? "ml" : "g";
        if (serving != null && serving.isValid()) {
            Double servingGrams = serving.getAsGrams();
            if (servingGrams != null && servingGrams > 0) {
                layout.setHint(context.getString(R.string.custom_amount_with_serving,
                        formatAmount(servingGrams) + unit));
                return;
            }
        }
        layout.setHint(context.getString(R.string.custom_amount_default));
    }

    private double getSmartDefaultAmount(@NonNull FoodProduct product) {
        ServingSize serving = product.getServingSize();
        if (serving != null && serving.isValid()) {
            Double servingGrams = serving.getAsGrams();
            if (servingGrams != null && servingGrams > 0) return servingGrams;
        }
        return 20.0;
    }

    private String formatAmount(double amount) {
        return (amount == Math.floor(amount))
                ? String.format(Locale.getDefault(), "%.0f", amount)
                : String.format(Locale.getDefault(), "%.1f", amount);
    }
}