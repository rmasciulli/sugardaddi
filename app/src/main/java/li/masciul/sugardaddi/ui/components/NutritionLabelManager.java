package li.masciul.sugardaddi.ui.components;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import li.masciul.sugardaddi.R;
import li.masciul.sugardaddi.core.models.FoodProduct;
import li.masciul.sugardaddi.core.models.Nutrition;
import li.masciul.sugardaddi.core.enums.NutritionLabelMode;
import li.masciul.sugardaddi.core.models.Nutrition.NutrientCategory;
import li.masciul.sugardaddi.core.models.Nutrition.NutrientInfo;
import li.masciul.sugardaddi.core.models.ServingSize;

/**
 * NutritionLabelManager - Nutrition Facts Label Display Component
 *
 * FIXED v2.1 - Column Alignment & Dynamic Headers
 *
 * ARCHITECTURE:
 * - Column 1: Nutrient name (with hierarchy indentation)
 * - Column 2: per 100g (ALWAYS visible - EU legal requirement)
 * - Column 3: per XXg (dynamic, updates with custom amount)
 *
 * KEY FIXES (v2.1):
 * - Proper column alignment across all row types
 * - Simplified 3rd column header: "per 20g" (not "per serving (12.5g)")
 * - Dynamic header updates when custom amount changes
 * - Consistent row padding (indentation only on nutrient name)
 *
 * @version 2.1 - ALIGNMENT & DYNAMIC HEADERS FIXED
 */
public class NutritionLabelManager {

    private static final double DEFAULT_CUSTOM_AMOUNT = 20.0;

    private final Context context;
    private final LinearLayout container;
    private final LayoutInflater inflater;

    private NutritionLabelMode mode = NutritionLabelMode.DETAILED;

    private boolean isExpanded = false;
    private FoodProduct currentProduct;
    private double currentCustomAmount = DEFAULT_CUSTOM_AMOUNT;

    private View headerView;
    private LinearLayout nutrientSection;
    private Button toggleButton;
    private final List<View> optionalRows = new ArrayList<>();

    // Track if this is the first category header (for spacing)
    private boolean isFirstCategoryHeader = true;


    /**
     * Constructor - requires explicit mode selection
     *
     * @param context Android context
     * @param container LinearLayout container for nutrition label
     * @param mode Display mode (SUMMARY or DETAILED)
     */
    public NutritionLabelManager(Context context, LinearLayout container, NutritionLabelMode mode) {
        this.context = context;
        this.container = container;
        this.mode = mode;
        this.inflater = LayoutInflater.from(context);
    }

    // ========================================================================
    // PUBLIC API
    // ========================================================================

    /**
     * Display product with default custom amount (serving size or 20g)
     */
    public void displayProduct(FoodProduct product) {
        double defaultAmount = getDefaultCustomAmount(product);
        displayProduct(product, defaultAmount);
    }

    /**
     * Display product with specific custom amount
     */
    public void displayProduct(FoodProduct product, double customAmount) {
        if (product == null) {
            container.setVisibility(View.GONE);
            return;
        }

        Nutrition nutrition = product.getNutrition();
        if (nutrition == null || !nutrition.hasData()) {
            container.setVisibility(View.GONE);
            return;
        }

        this.currentProduct = product;
        this.currentCustomAmount = customAmount;
        this.isExpanded = false;
        this.optionalRows.clear();

        container.removeAllViews();
        container.setVisibility(View.VISIBLE);

        buildNutritionLabel(product, nutrition, customAmount);
    }

    /**
     * Update custom amount and refresh display
     * CRITICAL: Preserves expanded/collapsed state AND updates header
     */
    public void updateCustomAmount(double customAmount) {
        if (currentProduct != null && customAmount > 0) {
            this.currentCustomAmount = customAmount;

            // PRESERVE STATE: Remember if expanded before rebuilding
            boolean wasExpanded = this.isExpanded;

            displayProduct(currentProduct, customAmount);

            // RESTORE STATE: Re-expand if it was expanded
            if (wasExpanded && !isExpanded) {
                toggleExpanded();
            }
        }
    }

    /**
     * Toggle visibility of optional nutrients
     */
    public void toggleExpanded() {
        isExpanded = !isExpanded;

        for (View row : optionalRows) {
            row.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
        }

        if (toggleButton != null) {
            toggleButton.setText(isExpanded ?
                    R.string.hide_additional_nutrients :
                    R.string.show_all_nutrients);
        }
    }

    public boolean isExpanded() {
        return isExpanded;
    }

    public void clear() {
        container.removeAllViews();
        container.setVisibility(View.GONE);
        currentProduct = null;
        isExpanded = false;
        optionalRows.clear();
    }

    // ========================================================================
    // SMART DEFAULTS
    // ========================================================================

    /**
     * Get default custom amount for column 3
     * Priority: serving size > 20g fallback
     */
    private double getDefaultCustomAmount(FoodProduct product) {
        ServingSize serving = product.getServingSize();
        if (serving != null && serving.isValid()) {
            Double servingGrams = serving.getAsGrams();
            if (servingGrams != null && servingGrams > 0) {
                return servingGrams;
            }
        }
        return 20.0;
    }

    // ========================================================================
    // LABEL BUILDING
    // ========================================================================

    private boolean categoryHasMandatoryNutrients(NutrientCategory category) {
        for (NutrientInfo info : NutrientInfo.getMandatoryNutrients()) {
            if (info.getCategory() == category) {
                return true;
            }
        }
        return false;
    }

    private void buildNutritionLabel(FoodProduct product, Nutrition nutrition, double customAmount) {
        isFirstCategoryHeader = true;  // Reset for each new label
        addHeader(product, customAmount);  // FIXED: Pass customAmount for dynamic header

        LinearLayout subContainer = new LinearLayout(context);
        subContainer.setOrientation(LinearLayout.VERTICAL);

        int categoryBgColor = context.getResources().getColor(R.color.white);
        subContainer.setBackgroundColor(categoryBgColor);

        int paddingPx = (int) (6 * context.getResources().getDisplayMetrics().density);
        subContainer.setPadding(paddingPx, paddingPx, paddingPx, paddingPx);

        container.addView(subContainer);

        nutrientSection = new LinearLayout(context);
        nutrientSection.setOrientation(LinearLayout.VERTICAL);
        subContainer.addView(nutrientSection);

        buildNutrientsUnified(nutrition, product, customAmount);

        if (!optionalRows.isEmpty()) {
            addToggleButton();
        }
    }

    private void buildNutrientsUnified(Nutrition nutrition, FoodProduct product, double customAmount) {
        List<NutrientInfo> allNutrients = new ArrayList<>();

        for (NutrientInfo info : NutrientInfo.values()) {
            if (info.isMandatory()) {
                allNutrients.add(info);
            } else {
                if (info.getValue(nutrition) != null) {
                    allNutrients.add(info);
                }
            }
        }

        List<NutrientInfo> parents = new ArrayList<>();
        List<NutrientInfo> children = new ArrayList<>();

        for (NutrientInfo info : allNutrients) {
            if (info.getIndentLevel() == 0) {
                parents.add(info);
            } else {
                children.add(info);
            }
        }

        parents.sort(Comparator.comparingInt(NutrientInfo::getDisplayOrder));

        NutrientCategory lastCategory = null;

        for (NutrientInfo parent : parents) {
            NutrientCategory category = parent.getCategory();

            if (category != lastCategory) {
                View headerView = addCategoryHeader(nutrientSection, category);

                if (!categoryHasMandatoryNutrients(category)) {
                    optionalRows.add(headerView);
                    headerView.setVisibility(View.GONE);
                }

                lastCategory = category;
            }

            Double value = parent.getValue(nutrition);
            View parentRow = addNutrientRow(nutrientSection, parent, value, product, customAmount);

            if (!parent.isMandatory()) {
                optionalRows.add(parentRow);
                parentRow.setVisibility(View.GONE);
            }

            addChildNutrients(parent, children, nutrition, product, customAmount);
        }
    }

    private void addChildNutrients(NutrientInfo parent, List<NutrientInfo> candidates,
                                   Nutrition nutrition, FoodProduct product, double customAmount) {
        List<NutrientInfo> directChildren = getChildrenOf(parent, candidates);
        directChildren.sort(Comparator.comparingInt(NutrientInfo::getDisplayOrder));

        for (NutrientInfo child : directChildren) {
            Double childValue = child.getValue(nutrition);

            if (child.isMandatory() || childValue != null) {
                View childRow = addNutrientRow(nutrientSection, child, childValue, product, customAmount);

                if (!child.isMandatory()) {
                    optionalRows.add(childRow);
                    childRow.setVisibility(View.GONE);
                }

                List<NutrientInfo> grandchildren = getChildrenOf(child, candidates);
                grandchildren.sort(Comparator.comparingInt(NutrientInfo::getDisplayOrder));

                for (NutrientInfo grandchild : grandchildren) {
                    Double grandchildValue = grandchild.getValue(nutrition);

                    if (grandchild.isMandatory() || grandchildValue != null) {
                        View grandchildRow = addNutrientRow(nutrientSection, grandchild, grandchildValue, product, customAmount);

                        if (!grandchild.isMandatory()) {
                            optionalRows.add(grandchildRow);
                            grandchildRow.setVisibility(View.GONE);
                        }
                    }
                }
            }
        }
    }

    // ========================================================================
    // PARENT-CHILD RELATIONSHIPS
    // ========================================================================

    private List<NutrientInfo> getChildrenOf(NutrientInfo parent, List<NutrientInfo> candidates) {
        List<NutrientInfo> children = new ArrayList<>();

        for (NutrientInfo candidate : candidates) {
            if (candidate.getIndentLevel() != parent.getIndentLevel() + 1) {
                continue;
            }

            if (isActualChildOf(candidate, parent)) {
                children.add(candidate);
            }
        }

        return children;
    }

    private boolean isActualChildOf(NutrientInfo child, NutrientInfo parent) {
        String parentKey = parent.getStringKey();
        String childKey = child.getStringKey();

        if (parentKey.equals("nutrient_carbohydrates")) {
            return childKey.equals("nutrient_sugars") ||
                    childKey.equals("nutrient_polyols") ||
                    childKey.equals("nutrient_starch");
        }

        if (parentKey.equals("nutrient_sugars")) {
            return childKey.equals("nutrient_glucose") ||
                    childKey.equals("nutrient_fructose") ||
                    childKey.equals("nutrient_sucrose") ||
                    childKey.equals("nutrient_lactose") ||
                    childKey.equals("nutrient_maltose") ||
                    childKey.equals("nutrient_galactose");
        }

        if (parentKey.equals("nutrient_fat")) {
            return childKey.equals("nutrient_saturated_fat") ||
                    childKey.equals("nutrient_monounsaturated_fat") ||
                    childKey.equals("nutrient_polyunsaturated_fat") ||
                    childKey.equals("nutrient_trans_fat");
        }

        if (parentKey.equals("nutrient_saturated_fat")) {
            return childKey.equals("nutrient_butyric_acid") ||
                    childKey.equals("nutrient_caproic_acid") ||
                    childKey.equals("nutrient_caprylic_acid") ||
                    childKey.equals("nutrient_capric_acid") ||
                    childKey.equals("nutrient_lauric_acid") ||
                    childKey.equals("nutrient_myristic_acid") ||
                    childKey.equals("nutrient_palmitic_acid") ||
                    childKey.equals("nutrient_stearic_acid");
        }

        if (parentKey.equals("nutrient_monounsaturated_fat")) {
            return childKey.equals("nutrient_omega9");
        }

        if (parentKey.equals("nutrient_polyunsaturated_fat")) {
            return childKey.equals("nutrient_omega3") ||
                    childKey.equals("nutrient_omega6");
        }

        if (parentKey.equals("nutrient_omega3")) {
            return childKey.equals("nutrient_ala") ||
                    childKey.equals("nutrient_epa") ||
                    childKey.equals("nutrient_dha");
        }

        if (parentKey.equals("nutrient_omega6")) {
            return childKey.equals("nutrient_linoleic_acid") ||
                    childKey.equals("nutrient_arachidonic_acid") ||
                    childKey.equals("nutrient_gamma_linolenic_acid");
        }

        return false;
    }

    // ========================================================================
    // UI COMPONENTS
    // ========================================================================

    private View addCategoryHeader(LinearLayout section, NutrientCategory category) {
        int stringResId = context.getResources().getIdentifier(
                category.getStringKey(),
                "string",
                context.getPackageName()
        );

        if (stringResId == 0) {
            return null;
        }

        String categoryName = context.getString(stringResId);

        // Choose layout based on whether this is the first category header
        // First header: 10dp top padding (close to table header)
        // Subsequent headers: 16dp top padding (visual grouping/separation)
        int layoutId = isFirstCategoryHeader
                ? R.layout.nutrition_label_category_header_first
                : R.layout.nutrition_label_category_header;

        View headerView = inflater.inflate(
                layoutId,
                section,
                false
        );

        TextView headerText = headerView.findViewById(R.id.categoryHeaderText);
        headerText.setText(categoryName);

        section.addView(headerView);

        // After first header, all subsequent ones use the standard layout
        isFirstCategoryHeader = false;

        return headerView;
    }

    /**
     * Add table header with simplified, dynamic 3rd column
     * FIXED: Column 3 now shows "per 20g" (updates dynamically)
     */
    private void addHeader(FoodProduct product, double customAmount) {
        // Choose header layout based on mode
        int headerLayout = (mode == NutritionLabelMode.SUMMARY)
                ? R.layout.nutrition_label_header_summary
                : R.layout.nutrition_label_header;

        headerView = inflater.inflate(headerLayout, container, false);

        if (mode == NutritionLabelMode.SUMMARY) {
            // Summary header (already set up in XML)
            container.addView(headerView);
        } else {
            // Detailed header (existing logic)
            TextView col1 = headerView.findViewById(R.id.headerNutrient);
            TextView col2 = headerView.findViewById(R.id.headerPer100g);
            TextView col3 = headerView.findViewById(R.id.headerCustomAmount);

            // Column 1: Nutrient name
            col1.setText(R.string.nutrition_label_nutrient_column);

            // Column 2: ALWAYS per 100g (EU requirement)
            String unit = product.isLiquid() ? "ml" : "g";
            col2.setText(context.getString(R.string.nutrition_per_100, unit));

            // Column 3: SIMPLIFIED - Just "per 20g" (dynamic)
            String customHeader = formatAmount(customAmount) + unit;
            col3.setText(context.getString(R.string.nutrition_per_custom, customHeader));

            container.addView(headerView);
        }
    }

    /**
     * Add nutrient row with proper column alignment
     */
    private View addNutrientRow(LinearLayout parent, NutrientInfo info, Double value,
                                FoodProduct product, double customAmount) {
        View row;

        // Set nutrient name with dash for level 2+
        String nutrientName = getTranslatedNutrientName(info);
        if (info.getIndentLevel() >= 2) {
            nutrientName = "- " + nutrientName;
        }

        if (mode == NutritionLabelMode.SUMMARY) {
            // SUMMARY MODE: 2-column layout
            int layoutId;
            switch (info.getIndentLevel()) {
                case 0:
                    layoutId = R.layout.nutrition_label_row_summary;
                    break;
                case 1:
                    layoutId = R.layout.nutrition_label_row_summary_indented;
                    break;
                case 2:
                    layoutId = R.layout.nutrition_label_row_summary_double_indented;
                    break;
                default:
                    layoutId = R.layout.nutrition_label_row_summary;
            }

            row = inflater.inflate(layoutId, parent, false);

            // Populate 2-column layout
            TextView nameView = row.findViewById(R.id.nutrientName);
            TextView totalView = row.findViewById(R.id.nutrientTotal);

            nameView.setText(nutrientName);

            if (totalView != null) {
                double customValue = calculateScaledValue(value, customAmount);
                totalView.setText(formatNutrientValue(customValue, info.getUnit(), info.isMandatory()));
            }

        } else {
            // DETAILED MODE: 3-column layout
            int layoutId;
            switch (info.getIndentLevel()) {
                case 0:
                    layoutId = R.layout.nutrition_label_row;
                    break;
                case 1:
                    layoutId = R.layout.nutrition_label_row_indented;
                    break;
                case 2:
                    layoutId = R.layout.nutrition_label_row_double_indented;
                    break;
                default:
                    layoutId = R.layout.nutrition_label_row;
            }

            row = inflater.inflate(layoutId, parent, false);

            // Populate 3-column layout
            TextView nameView = row.findViewById(R.id.nutrientName);
            TextView per100View = row.findViewById(R.id.valuePer100g);
            TextView customView = row.findViewById(R.id.valueCustomAmount);

            nameView.setText(nutrientName);

            // Column 2: per 100g value
            if (per100View != null) {
                per100View.setText(formatNutrientValue(value != null ? value : 0.0,
                        info.getUnit(), info.isMandatory()));
            }

            // Column 3: custom amount value
            if (customView != null) {
                double customValue = calculateScaledValue(value, customAmount);
                customView.setText(formatNutrientValue(customValue, info.getUnit(), info.isMandatory()));
            }
        }

        parent.addView(row);
        return row;
    }

    private void addToggleButton() {
        toggleButton = new Button(context);
        toggleButton.setText(R.string.show_all_nutrients);
        toggleButton.setOnClickListener(v -> toggleExpanded());

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);

        params.setMargins(24, 24, 24, 24);
        toggleButton.setLayoutParams(params);

        // FDA-compliant design: black text, white background, black border
        toggleButton.setTextColor(0xFF000000);  // Black text
        toggleButton.setAllCaps(false);  // Lowercase (no ALL_CAPS)
        toggleButton.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14);

        // Create outline border (black stroke, white fill)
        android.graphics.drawable.GradientDrawable shape =
                new android.graphics.drawable.GradientDrawable();
        shape.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        shape.setColor(0xFFFFFFFF);  // White background
        shape.setStroke(2, 0xFF000000);  // Black 2dp border
        shape.setCornerRadius(8f);  // Subtle rounded corners

        toggleButton.setBackground(shape);

        container.addView(toggleButton);
    }

    // ========================================================================
    // HELPERS
    // ========================================================================

    private String getTranslatedNutrientName(NutrientInfo info) {
        String key = info.getStringKey();

        int resId = context.getResources().getIdentifier(key, "string", context.getPackageName());
        if (resId != 0) {
            return context.getString(resId);
        }

        if (key.endsWith("s")) {
            String singular = key.substring(0, key.length() - 1);
            resId = context.getResources().getIdentifier(singular, "string", context.getPackageName());
            if (resId != 0) {
                return context.getString(resId);
            }
        }

        return makeReadable(info.getFieldName());
    }

    private String makeReadable(String fieldName) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < fieldName.length(); i++) {
            char c = fieldName.charAt(i);
            if (i > 0 && Character.isUpperCase(c)) {
                result.append(' ');
            }
            result.append(i == 0 ? Character.toUpperCase(c) : c);
        }
        return result.toString();
    }

    private double calculateScaledValue(Double valuePer100, double amount) {
        if (valuePer100 == null) {
            return 0.0;
        }
        return valuePer100 * (amount / 100.0);
    }

    private String formatNutrientValue(double value, String unit, boolean isMandatory) {
        if (value == 0.0 && !isMandatory) {
            return "0 " + unit;
        }

        if (value == 0.0) {
            return "-";
        }

        if (value < 0.01) {
            return "< 0.01 " + unit;
        }

        if (value < 1.0) {
            return String.format("%.2f %s", value, unit);
        } else if (value < 10.0) {
            return String.format("%.1f %s", value, unit);
        } else {
            return String.format("%.0f %s", value, unit);
        }
    }

    private String formatAmount(double amount) {
        if (amount == Math.floor(amount)) {
            return String.format("%.0f", amount);
        } else {
            return String.format("%.1f", amount);
        }
    }

    private boolean hasOptionalNutrients(Nutrition nutrition) {
        for (NutrientInfo info : NutrientInfo.values()) {
            if (!info.isMandatory() && info.getValue(nutrition) != null) {
                return true;
            }
        }
        return false;
    }
}