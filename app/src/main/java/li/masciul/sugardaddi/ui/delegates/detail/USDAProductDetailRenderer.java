package li.masciul.sugardaddi.ui.delegates.detail;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import li.masciul.sugardaddi.R;
import li.masciul.sugardaddi.core.enums.DataSource;
import li.masciul.sugardaddi.core.enums.NutritionLabelMode;
import li.masciul.sugardaddi.core.enums.ProductType;
import li.masciul.sugardaddi.core.interfaces.Searchable;
import li.masciul.sugardaddi.core.models.FoodProduct;
import li.masciul.sugardaddi.core.models.Nutrition;
import li.masciul.sugardaddi.core.models.ServingSize;
import li.masciul.sugardaddi.ui.components.NutritionLabelManager;

import java.util.Locale;

/**
 * USDAProductDetailRenderer — Detail screen for USDA FoodData Central products.
 *
 * USDA FoodData Central is a US government scientific database. Its products are:
 *   - Scientific food compositions (raw agricultural commodities and ingredients)
 *   - English-only names (sentence-cased by USDAMapper)
 *   - Rich in micro-nutrient data, especially Foundation Foods (100+ nutrients)
 *   - Without product images, Nutri-Score, Green-Score, Nova Group, or allergens
 *
 * DISPLAYS:
 *   - USDA attribution banner (amber card, tappable → fdc.nal.usda.gov)
 *   - Product name
 *   - Category (USDA taxonomy, e.g. "Vegetables and Vegetable Products")
 *   - Serving size (if available)
 *   - Quick nutrition summary (carbs + kcal, same as search card)
 *   - Full nutrition facts (via NutritionLabelManager, DETAILED mode)
 *
 * HIDES:
 *   - Hero image (USDA has none)
 *   - Score section (Nutri-Score, Green-Score, Nova absent)
 *   - Allergens (not part of USDA FDC dataset)
 *
 * Mirrors CiqualProductDetailRenderer exactly — same structure, same populate
 * helpers, different source check and layout reference.
 *
 * @version 1.0
 */
public class USDAProductDetailRenderer implements DetailRenderer {

    private static final String TAG = "USDAProductDetailRenderer";

    private final Context context;

    /** Holds reference to the current NutritionLabelManager for amount updates. */
    private NutritionLabelManager nutritionLabelManager;

    /** TextWatcher reference for cleanup in destroy(). */
    private TextWatcher customAmountWatcher;

    public USDAProductDetailRenderer(@NonNull Context context) {
        this.context = context;
    }

    // ========== DetailRenderer CONTRACT ==========

    /** Handles FoodProducts from USDA FoodData Central only. */
    @Override
    public boolean supports(@NonNull Searchable item) {
        return item.getProductType() == ProductType.FOOD
                && item.getDataSource() == DataSource.USDA;
    }

    @NonNull
    @Override
    public View inflate(@NonNull LayoutInflater inflater, @NonNull ViewGroup container) {
        return inflater.inflate(R.layout.detail_usda_product, container, false);
    }

    @Override
    public void populate(@NonNull View view, @NonNull Searchable item,
                         @NonNull String language) {
        if (!(item instanceof FoodProduct)) return;
        FoodProduct product = (FoodProduct) item;

        populateHeader(view, product, language);
        populateNutrition(view, product, language);
        // Attribution panel last — informational, never obscures critical content
        DetailRendererUtils.populateAttribution(context, view, product);
    }

    /** Forward amount changes from ItemDetailsActivity to NutritionLabelManager. */
    @Override
    public void onAmountChanged(@NonNull View view, @NonNull Searchable item,
                                double amount, @NonNull String language) {
        if (nutritionLabelManager != null && amount > 0) {
            nutritionLabelManager.updateCustomAmount(amount);
        }
    }

    /** Use the product name as the toolbar title. */
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
     * Populate the header card: product name, category, serving size,
     * and quick nutrition summary (carbs + kcal).
     *
     * Brand is not shown — USDA describes generic/raw foods, not branded products.
     */
    private void populateHeader(@NonNull View view, @NonNull FoodProduct product,
                                @NonNull String language) {

        // Product name
        TextView productName = view.findViewById(R.id.productName);
        if (productName != null) {
            String name = product.getDisplayName(language);
            productName.setText((name != null && !name.trim().isEmpty())
                    ? name
                    : context.getString(R.string.unknown_product));
        }

        // USDA food category — flat string, no breadcrumb formatting needed
        TextView categoryText = view.findViewById(R.id.categoryText);
        if (categoryText != null) {
            String rawCat = product.getCategoriesText(language);
            if (rawCat == null || rawCat.trim().isEmpty()) {
                rawCat = product.getPrimaryCategory(language);
            }
            if (rawCat != null && !rawCat.trim().isEmpty()) {
                categoryText.setText(rawCat.trim());
                categoryText.setVisibility(View.VISIBLE);
            } else {
                categoryText.setVisibility(View.GONE);
            }
        }

        // Serving size — Foundation Foods occasionally provide portion data
        TextView servingSizeText = view.findViewById(R.id.servingSizeText);
        if (servingSizeText != null) {
            ServingSize serving = product.getServingSize();
            if (serving != null && serving.isValid()) {
                String servingDisplay = serving.getDisplayText();
                if (servingDisplay != null && !servingDisplay.trim().isEmpty()) {
                    servingSizeText.setText(
                            context.getString(R.string.serving_size_with_amount, servingDisplay));
                    servingSizeText.setVisibility(View.VISIBLE);
                } else {
                    servingSizeText.setVisibility(View.GONE);
                }
            } else {
                servingSizeText.setVisibility(View.GONE);
            }
        }

        // Quick nutrition summary row: carbs left, kcal badge right
        LinearLayout nutritionSummaryRow = view.findViewById(R.id.nutritionSummaryRow);
        TextView nutritionSummary        = view.findViewById(R.id.nutritionSummary);
        TextView kcalBadge               = view.findViewById(R.id.kcalBadge);
        Nutrition nutrition = product.getNutrition();

        if (nutrition != null && nutritionSummaryRow != null) {
            boolean hasCarbs = nutrition.getCarbohydrates() != null
                    && nutrition.getCarbohydrates() > 0;
            boolean hasKcal  = nutrition.getEnergyKcal() != null
                    && nutrition.getEnergyKcal() > 0;

            if (hasCarbs || hasKcal) {
                nutritionSummaryRow.setVisibility(View.VISIBLE);
                if (hasCarbs && nutritionSummary != null) {
                    // Honour app language for the nutrient label
                    String carbLabel = "fr".equals(language) ? "glucides" : "carbohydrates";
                    String unit      = product.isLiquid() ? "100ml" : "100g";
                    nutritionSummary.setText(String.format(
                            Locale.getDefault(),
                            "%.1fg of %s per %s",
                            nutrition.getCarbohydrates(), carbLabel, unit));
                    nutritionSummary.setVisibility(View.VISIBLE);
                }
                if (hasKcal && kcalBadge != null) {
                    kcalBadge.setText(String.format(
                            Locale.getDefault(), "%.0f kcal", nutrition.getEnergyKcal()));
                    kcalBadge.setVisibility(View.VISIBLE);
                }
            } else {
                nutritionSummaryRow.setVisibility(View.GONE);
            }
        } else if (nutritionSummaryRow != null) {
            nutritionSummaryRow.setVisibility(View.GONE);
        }
    }

    /**
     * Set up NutritionLabelManager and the custom amount input.
     *
     * USDA Foundation Foods have exhaustive nutrient profiles (100+ nutrients).
     * DETAILED mode lets users see the full depth of the data including
     * vitamins, minerals, and amino acids in the expandable sections.
     */
    private void populateNutrition(@NonNull View view, @NonNull FoodProduct product,
                                   @NonNull String language) {

        LinearLayout nutritionContainer = view.findViewById(R.id.nutritionContainer);
        if (nutritionContainer == null) return;

        nutritionLabelManager = new NutritionLabelManager(
                context, nutritionContainer, NutritionLabelMode.DETAILED);

        double defaultAmount = getSmartDefaultAmount(product);
        nutritionLabelManager.displayProduct(product, defaultAmount);

        TextInputLayout   amountLayout = view.findViewById(R.id.customAmountInputLayout);
        TextInputEditText amountInput  = view.findViewById(R.id.customAmountEditText);

        if (amountLayout != null && amountInput != null) {
            updateAmountHint(amountLayout, product);

            customAmountWatcher = new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int i, int i1, int i2) {}
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

    private void updateAmountHint(@NonNull TextInputLayout layout,
                                  @NonNull FoodProduct product) {
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
                ? String.format(java.util.Locale.getDefault(), "%.0f", amount)
                : String.format(java.util.Locale.getDefault(), "%.1f", amount);
    }
}