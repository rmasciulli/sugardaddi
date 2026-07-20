package li.masciul.sugardaddi.ui.delegates.detail;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import li.masciul.sugardaddi.R;
import li.masciul.sugardaddi.core.enums.DataSourceType;
import li.masciul.sugardaddi.core.enums.NutritionLabelMode;
import li.masciul.sugardaddi.core.enums.ProductType;
import li.masciul.sugardaddi.core.interfaces.Searchable;
import li.masciul.sugardaddi.core.models.FoodProduct;
import li.masciul.sugardaddi.core.models.Nutrition;
import li.masciul.sugardaddi.core.models.ServingSize;
import li.masciul.sugardaddi.ui.components.NutritionLabelManager;
import li.masciul.sugardaddi.ui.utils.ImageDisplayUtils;

import java.util.Locale;

/**
 * CiqualProductDetailRenderer - Detail screen for Ciqual (ANSES) products.
 *
 * Ciqual is the French scientific nutritional database. Its products are:
 *   - Scientific food compositions (not branded products)
 *   - Often French-language names (dual-language naming where available)
 *   - Rich in micro-nutrient data (vitamins, minerals, amino acids)
 *   - Without product images, Nutri-Score, Green-Score, or Nova Group
 *
 * DISPLAYS:
 *   - Ciqual attribution banner (amber card, explains data source)
 *   - Product name
 *   - Category (Ciqual taxonomy, if available)
 *   - Serving size (if available)
 *   - Nutrition facts (via NutritionLabelManager, DETAILED mode)
 *
 * HIDES:
 *   - Hero image (Ciqual has none)
 *   - Score section (Nutri-Score, Green-Score, Nova are all absent)
 *   - Allergens (not part of the Ciqual dataset)
 *
 * @version 1.0
 */
public class CiqualProductDetailRenderer implements DetailRenderer {

    private static final String TAG = "CiqualProductDetailRenderer";

    private final Context context;

    // Stateful: holds reference to the current NutritionLabelManager for amount updates
    private NutritionLabelManager<FoodProduct> nutritionLabelManager;

    // TextWatcher reference for cleanup in destroy()
    private TextWatcher customAmountWatcher;

    public CiqualProductDetailRenderer(@NonNull Context context) {
        this.context = context;
    }

    // ========== DetailRenderer CONTRACT ==========

    /**
     * Handles FoodProducts from the Ciqual data source only.
     */
    @Override
    public boolean supports(@NonNull Searchable item) {
        return item.getProductType() == ProductType.FOOD
                && item.getDataSource() == DataSourceType.CIQUAL;
    }

    @NonNull
    @Override
    public View inflate(@NonNull LayoutInflater inflater, @NonNull ViewGroup container) {
        return inflater.inflate(R.layout.detail_ciqual_product, container, false);
    }

    @Override
    public void populate(@NonNull View view, @NonNull Searchable item, @NonNull String language) {
        if (!(item instanceof FoodProduct)) return;
        FoodProduct product = (FoodProduct) item;

        populateImage(view, product);
        populateHeader(view, product, language);
        populateNutrition(view, product, language);
        // Attribution panel last - informational, never obscures critical content
        DetailRendererUtils.populateAttribution(context, view, product.getDataSource());
    }

    /**
     * Forward amount changes from ProductDetailsActivity to NutritionLabelManager.
     */
    @Override
    public void onAmountChanged(@NonNull View view, @NonNull Searchable item,
                                double amount, @NonNull String language) {
        if (nutritionLabelManager != null && amount > 0) {
            nutritionLabelManager.updateCustomAmount(amount);
        }
    }

    /**
     * Use the product name as the toolbar title.
     */
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
     * Load the full-size product image (when one resolves) and make the hero
     * tappable to open the full original full-screen. Ciqual has no remote image,
     * so the hero appears only when the user set a custom image.
     */
    private void populateImage(@NonNull View view, @NonNull FoodProduct product) {
        View heroContainer = view.findViewById(R.id.heroImageContainer);
        ImageView heroImage = view.findViewById(R.id.heroImage);
        if (heroContainer == null || heroImage == null) return;
        View heroExpandIcon = view.findViewById(R.id.heroExpandIcon);

        Object source = ImageDisplayUtils.resolveProductImageSource(product);
        if (source != null) {
            heroContainer.setVisibility(View.VISIBLE);
            ImageDisplayUtils.loadHeroImage(context, source, heroImage);
        } else {
            heroContainer.setVisibility(View.GONE);
        }
        // Tap to open the full original; the expand icon shows only when openable.
        ImageDisplayUtils.bindFullScreenTap(context, heroImage, heroExpandIcon, source);
    }

    /**
     * Resolves image source for a food product.
     * Ciqual has no remote image URL and no auto-downloaded thumbnail.
     * Only user-defined paths are checked (userImagePath, userThumbnailPath).
     */
    @Nullable
    private Object resolveProductImageSource(@NonNull FoodProduct product) {
        String userImage = product.getUserImagePath();
        if (userImage != null && !userImage.trim().isEmpty()) {
            java.io.File f = new java.io.File(userImage);
            if (f.exists()) return f;
        }

        String userThumb = product.getUserThumbnailPath();
        if (userThumb != null && !userThumb.trim().isEmpty()) {
            java.io.File f = new java.io.File(userThumb);
            if (f.exists()) return f;
        }

        return null;
    }

    /**
     * Populate the header card: product name, category, serving size.
     *
     * NOTE: Brand is not shown for Ciqual products because Ciqual describes
     * generic food items (e.g. "Whole milk, pasteurized"), not branded products.
     * The "brand" field is typically empty or irrelevant for this data source.
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

        // Category - last 2 breadcrumb levels, matching search card style
        TextView categoryText = view.findViewById(R.id.categoryText);
        if (categoryText != null) {
            String rawCat = product.getCategoriesText(language);
            if (rawCat == null || rawCat.trim().isEmpty()) {
                rawCat = product.getPrimaryCategory(language);
            }
            String category = formatBreadcrumb(rawCat);
            if (category != null && !category.isEmpty()) {
                categoryText.setText(category);
                categoryText.setVisibility(View.VISIBLE);
            } else {
                categoryText.setVisibility(View.GONE);
            }
        }

        // Nutrition summary row - carbs + kcal, same style as search card
        android.widget.LinearLayout nutritionSummaryRow =
                view.findViewById(R.id.nutritionSummaryRow);
        TextView nutritionSummary = view.findViewById(R.id.nutritionSummary);
        TextView kcalBadge       = view.findViewById(R.id.kcalBadge);
        Nutrition nutrition = product.getNutrition();
        if (nutrition != null && nutritionSummaryRow != null) {
            boolean hasCarbs = nutrition.getCarbohydrates() != null
                    && nutrition.getCarbohydrates() > 0;
            boolean hasKcal  = nutrition.getEnergyKcal() != null
                    && nutrition.getEnergyKcal() > 0;
            if (hasCarbs || hasKcal) {
                nutritionSummaryRow.setVisibility(View.VISIBLE);
                if (hasCarbs && nutritionSummary != null) {
                    String carbLabel = "fr".equals(language) ? "glucides" : "carbohydrates";
                    String unit = "100" + nutrition.getBasis().getUnitLabel();
                    nutritionSummary.setText(String.format(
                            java.util.Locale.getDefault(),
                            "%.1fg of %s per %s", nutrition.getCarbohydrates(), carbLabel, unit));
                    nutritionSummary.setVisibility(View.VISIBLE);
                }
                if (hasKcal && kcalBadge != null) {
                    kcalBadge.setText(String.format(
                            java.util.Locale.getDefault(),
                            "%.0f kcal", nutrition.getEnergyKcal()));
                    kcalBadge.setVisibility(View.VISIBLE);
                }
            } else {
                nutritionSummaryRow.setVisibility(View.GONE);
            }
        } else if (nutritionSummaryRow != null) {
            nutritionSummaryRow.setVisibility(View.GONE);
        }

        // Serving size - show when defined by the data source
        TextView servingSizeText = view.findViewById(R.id.servingSizeText);
        if (servingSizeText != null) {
            ServingSize serving = product.getServingSize();
            if (serving != null && serving.isValid()) {
                String servingDisplay = serving.getDisplayText();
                if (servingDisplay != null && !servingDisplay.trim().isEmpty()) {
                    // Format as "Serving size: 125ml"
                    String label = context.getString(R.string.serving_size_with_amount, servingDisplay);
                    servingSizeText.setText(label);
                    servingSizeText.setVisibility(View.VISIBLE);
                } else {
                    servingSizeText.setVisibility(View.GONE);
                }
            } else {
                servingSizeText.setVisibility(View.GONE);
            }
        }
    }

    /**
     * Set up the NutritionLabelManager and custom amount input.
     *
     * Ciqual products have rich micro-nutrient data - DETAILED mode shows all of it
     * including the expandable vitamins/minerals section.
     */
    private void populateNutrition(@NonNull View view, @NonNull FoodProduct product,
                                   @NonNull String language) {

        LinearLayout nutritionContainer = view.findViewById(R.id.nutritionContainer);
        if (nutritionContainer == null) return;

        nutritionLabelManager = new NutritionLabelManager<>(
                context, nutritionContainer, NutritionLabelMode.DETAILED);

        double defaultAmount = getSmartDefaultAmount(product);
        nutritionLabelManager.display(product, defaultAmount);

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
                            if (amount > 0) {
                                nutritionLabelManager.updateCustomAmount(amount);
                            }
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
        String unit = product.getNutrition() != null
                ? product.getNutrition().getBasis().getUnitLabel()
                : "g";

        if (serving != null && serving.isValid()) {
            Double servingGrams = serving.getAsGrams();
            if (servingGrams != null && servingGrams > 0) {
                String servingText = formatAmount(servingGrams) + unit;
                layout.setHint(context.getString(R.string.custom_amount_with_serving, servingText));
                return;
            }
        }
        layout.setHint(context.getString(R.string.custom_amount_default));
    }

    private double getSmartDefaultAmount(@NonNull FoodProduct product) {
        ServingSize serving = product.getServingSize();
        if (serving != null && serving.isValid()) {
            Double servingGrams = serving.getAsGrams();
            if (servingGrams != null && servingGrams > 0) {
                return servingGrams;
            }
        }
        return 20.0;
    }

    private String formatAmount(double amount) {
        return (amount == Math.floor(amount))
                ? String.format(Locale.getDefault(), "%.0f", amount)
                : String.format(Locale.getDefault(), "%.1f", amount);
    }
    /**
     * Returns last 2 segments of a " > " breadcrumb with " › " separator, sentence-cased.
     * "milk and milk products > dairy products > dairy desserts" → "Dairy products › dairy desserts"
     */
    @Nullable
    private static String formatBreadcrumb(@Nullable String breadcrumb) {
        if (breadcrumb == null || breadcrumb.trim().isEmpty()) return null;
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


}