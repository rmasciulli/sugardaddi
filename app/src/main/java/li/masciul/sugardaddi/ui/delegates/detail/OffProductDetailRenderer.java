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
import li.masciul.sugardaddi.core.enums.DataSource;
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
 * OffProductDetailRenderer - Detail screen for OpenFoodFacts products.
 *
 * DISPLAYS:
 *   - Hero product image (full-width, 220dp)
 *   - Product name, brand, quantity, primary category
 *   - Scores row: Nutri-Score (TPL with overlay) | Green-Score (horizontal) | Nova Group badge
 *   - Allergens section (hidden if no allergen data)
 *   - Nutrition facts (via NutritionLabelManager, DETAILED mode)
 *   - Custom amount input with real-time nutrition recalculation
 *
 * SCORE HANDLING:
 *   - Nutri-Score: programmatic sticker via ScoreOverlayHelper.createNutriScoreTplWithText()
 *   - Green-Score: static horizontal sticker via ScoreUtils.getGreenScoreHorizontal()
 *   - Nova Group: static badge via ScoreUtils.getNovaGroupDrawable()
 *   - Any absent score shows the "unavailable" state gracefully
 *
 * LIFECYCLE:
 *   inflate() → inflate detail_off_product.xml and cache view references
 *   populate() → bind all product data to views
 *   onAmountChanged() → delegate to NutritionLabelManager.updateCustomAmount()
 *   destroy() → clear NutritionLabelManager, release TextWatcher
 *
 * @version 1.0
 */
public class OffProductDetailRenderer implements DetailRenderer {

    private static final String TAG = "OffProductDetailRenderer";

    private final Context context;

    // NutritionLabelManager is stateful; keep a reference for amount updates and cleanup.
    private NutritionLabelManager nutritionLabelManager;

    // Keep a reference to the TextWatcher so we can remove it on destroy()
    // to prevent memory leaks when the activity is destroyed.
    private TextWatcher customAmountWatcher;

    public OffProductDetailRenderer(@NonNull Context context) {
        this.context = context;
    }

    // ========== DetailRenderer CONTRACT ==========

    /**
     * This renderer handles FoodProducts from OpenFoodFacts only.
     * The Default renderer is the catch-all for everything else.
     */
    @Override
    public boolean supports(@NonNull Searchable item) {
        return item.getProductType() == ProductType.FOOD
                && item.getDataSource() == DataSource.OPENFOODFACTS;
    }

    @NonNull
    @Override
    public View inflate(@NonNull LayoutInflater inflater, @NonNull ViewGroup container) {
        // Inflate our dedicated layout — do NOT attach to container here,
        // ItemDetailsActivity does that after receiving the view.
        return inflater.inflate(R.layout.detail_off_product, container, false);
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

    /**
     * Called by ItemDetailsActivity when the custom amount input changes.
     * Delegates to NutritionLabelManager for real-time recalculation.
     */
    @Override
    public void onAmountChanged(@NonNull View view, @NonNull Searchable item,
                                double amount, @NonNull String language) {
        if (nutritionLabelManager != null && amount > 0) {
            nutritionLabelManager.updateCustomAmount(amount);
        }
    }

    /**
     * Use the product name as toolbar title.
     */
    @Override
    public String getToolbarTitle(@NonNull Searchable item, @NonNull String language) {
        if (item instanceof FoodProduct) {
            String name = ((FoodProduct) item).getDisplayName(language);
            return (name != null && !name.trim().isEmpty()) ? name : null;
        }
        return null;
    }

    /**
     * Remove TextWatcher and clear NutritionLabelManager on destroy.
     * This prevents memory leaks if the renderer outlives the view.
     */
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
     * Load the product hero image via Glide.
     * Falls back to ic_food_placeholder when imageUrl is null or empty.
     */
    private void populateHeroImage(@NonNull View view, @NonNull FoodProduct product) {
        ImageView heroImage = view.findViewById(R.id.heroImage);
        if (heroImage == null) return;

        String imageUrl = product.getImageUrl();
        if (imageUrl != null && !imageUrl.trim().isEmpty()) {
            GlideImageLoader.load(context, imageUrl)
                    .placeholder(R.drawable.ic_food_placeholder)
                    .error(R.drawable.ic_food_error)
                    .centerCrop()
                    .into(heroImage);
        } else {
            // No image available: show a centered placeholder (no scrim, no overlay text)
            heroImage.setImageResource(R.drawable.ic_food_placeholder);
            heroImage.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        }
    }

    /**
     * Populate the header card: product name, brand, quantity, category.
     * Each field is hidden (GONE) when not available to avoid empty-space gaps.
     */
    private void populateHeader(@NonNull View view, @NonNull FoodProduct product,
                                @NonNull String language) {

        // Product name — always shown (fallback to string resource)
        TextView productName = view.findViewById(R.id.productName);
        if (productName != null) {
            String name = product.getDisplayName(language);
            productName.setText((name != null && !name.trim().isEmpty())
                    ? name
                    : context.getString(R.string.unknown_product));
        }

        // Brand name
        TextView brandName = view.findViewById(R.id.brandName);
        if (brandName != null) {
            String brand = product.getBrand(language);
            if (brand != null && !brand.trim().isEmpty()
                    && !brand.equalsIgnoreCase("unknown")) {
                // Use only the first brand when multiple are comma-separated
                String cleanBrand = brand.split(",")[0].trim();
                brandName.setText(cleanBrand);
                brandName.setVisibility(View.VISIBLE);
            } else {
                brandName.setVisibility(View.GONE);
            }
        }

        // Quantity (e.g. "500g", "1L")
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

        // Primary category in sentence case
        TextView categoryText = view.findViewById(R.id.categoryText);
        if (categoryText != null) {
            String category = product.getPrimaryCategory(language);
            if (category != null && !category.trim().isEmpty()) {
                // Sentence case: first char uppercase, rest lowercase
                String lower = category.trim().toLowerCase(Locale.getDefault());
                String display = Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
                categoryText.setText(display);
                categoryText.setVisibility(View.VISIBLE);
            } else {
                categoryText.setVisibility(View.GONE);
            }
        }
    }

    /**
     * Populate the three score stickers.
     *
     * The scores live in a left-aligned horizontal strip inside the product card.
     * scoresRow is GONE by default; we make it VISIBLE here.
     * No scoreDivider in v3 — the score strip flows directly under the product info
     * with only paddingBottom separating it from the next section divider.
     */
    private void populateScores(@NonNull View view, @NonNull FoodProduct product) {
        View scoresRow = view.findViewById(R.id.scoresRow);
        if (scoresRow != null) scoresRow.setVisibility(View.VISIBLE);

        populateNutriScore(view, product);
        populateGreenScore(view, product);
        populateNovaGroup(view, product);
    }

    /**
     * Nutri-Score: programmatic TPL sticker with localized text overlay.
     *
     * The nutriScoreStickerContainer in the layout has a fixed height of 64dp.
     * We add the sticker as a child with MATCH_PARENT height so it fills that box,
     * and WRAP_CONTENT width so the sticker scales its width from its aspect ratio.
     * We must NOT set the container itself to MATCH_PARENT or WRAP_CONTENT height —
     * the XML 64dp declaration is the authoritative constraint.
     */
    private void populateNutriScore(@NonNull View view, @NonNull FoodProduct product) {
        FrameLayout container = view.findViewById(R.id.nutriScoreStickerContainer);
        if (container == null) return;

        container.removeAllViews();

        FrameLayout sticker = ScoreOverlayHelper.createNutriScoreTplWithText(
                context,
                product.getNutriScore(),
                "horizontal"
        );

        // MATCH_PARENT height: fill the 64dp fixed container declared in the layout.
        // WRAP_CONTENT width: let the sticker scale its width from its aspect ratio.
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );
        params.gravity = android.view.Gravity.CENTER;
        sticker.setLayoutParams(params);
        container.addView(sticker);
    }

    /**
     * Green-Score: horizontal sticker.
     *
     * The greenScoreSticker ImageView in the layout has a fixed height of 64dp,
     * wrap_content width, adjustViewBounds=true, and fitCenter scaleType.
     * This means the drawable naturally scales its width from its aspect ratio
     * within the 64dp height box — no programmatic sizing needed.
     *
     * We only set the drawable. Calling setupStickerImageView() here would override
     * the XML height to 48dp, fighting the intended layout constraint.
     */
    private void populateGreenScore(@NonNull View view, @NonNull FoodProduct product) {
        ImageView sticker = view.findViewById(R.id.greenScoreSticker);
        if (sticker == null) return;

        sticker.setImageResource(ScoreUtils.getGreenScoreHorizontal(product.getEcoScore()));
        sticker.setVisibility(View.VISIBLE);
    }

    /**
     * Nova Group: static badge image.
     * When the group is null or unknown, show the "unavailable" text label instead
     * so the score column doesn't appear empty.
     */
    private void populateNovaGroup(@NonNull View view, @NonNull FoodProduct product) {
        ImageView badge = view.findViewById(R.id.novaGroupBadge);
        TextView unavailable = view.findViewById(R.id.novaGroupUnavailable);

        String novaGroup = product.getNovaGroup();

        if (ScoreUtils.isValidNovaGroup(novaGroup)) {
            // Valid group → show badge, hide unavailable label
            int drawable = ScoreUtils.getNovaGroupDrawable(novaGroup);
            if (badge != null) {
                badge.setImageResource(drawable);
                badge.setVisibility(View.VISIBLE);
            }
            if (unavailable != null) {
                unavailable.setVisibility(View.GONE);
            }
        } else {
            // No Nova data → show "N/A" label, hide badge
            if (badge != null) {
                badge.setVisibility(View.GONE);
            }
            if (unavailable != null) {
                unavailable.setVisibility(View.VISIBLE);
            }
        }
    }

    /**
     * Populate the allergens section inside the main content card.
     *
     * Three views are toggled together:
     *   allergenDivider  — 1dp line above the section
     *   allergenSection  — LinearLayout containing the header label + icon grid
     *   allergenIconsContainer — FrameLayout inside allergenSection
     *
     * All three are GONE by default in the layout. When allergenFlags == 0,
     * they all stay hidden. When flags are present, all three become VISIBLE.
     */
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

    /**
     * Set up the NutritionLabelManager and custom amount input.
     *
     * NutritionLabelManager is instantiated fresh for this view instance
     * so that it holds a reference to the correct container LinearLayout.
     *
     * The TextWatcher is attached here and removed in destroy() to prevent leaks.
     */
    private void populateNutrition(@NonNull View view, @NonNull FoodProduct product,
                                   @NonNull String language) {

        LinearLayout nutritionContainer = view.findViewById(R.id.nutritionContainer);
        if (nutritionContainer == null) return;

        // Create a new NutritionLabelManager for this view instance
        nutritionLabelManager = new NutritionLabelManager(
                context, nutritionContainer, NutritionLabelMode.DETAILED);

        // Calculate smart default amount: serving size if available, otherwise 20g
        double defaultAmount = getSmartDefaultAmount(product);
        nutritionLabelManager.displayProduct(product, defaultAmount);

        // Wire custom amount input
        TextInputLayout amountLayout = view.findViewById(R.id.customAmountInputLayout);
        TextInputEditText amountInput = view.findViewById(R.id.customAmountEditText);

        if (amountLayout != null && amountInput != null) {
            // Update hint to reflect the serving size
            updateAmountHint(amountLayout, product);

            // TextWatcher: live recalculation as user types
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
                    } catch (NumberFormatException ignored) {
                        // User mid-type: do nothing, wait for valid input
                    }
                }
            };
            amountInput.addTextChangedListener(customAmountWatcher);
        }
    }

    // ========== HELPERS ==========

    /**
     * Update the custom amount input hint to reflect serving size.
     * "Custom amount (serving size: 125ml)" or "Custom amount" if no serving.
     */
    private void updateAmountHint(@NonNull TextInputLayout layout, @NonNull FoodProduct product) {
        ServingSize serving = product.getServingSize();
        String unit = product.isLiquid() ? "ml" : "g";

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

    /**
     * Smart default amount: serving size if valid, else 20g.
     */
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

    /**
     * Format a double amount: drops trailing ".0" for clean display.
     * 20.0 → "20", 12.5 → "12.5"
     */
    private String formatAmount(double amount) {
        return (amount == Math.floor(amount))
                ? String.format(Locale.getDefault(), "%.0f", amount)
                : String.format(Locale.getDefault(), "%.1f", amount);
    }
}