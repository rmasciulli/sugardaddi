package li.masciul.sugardaddi.ui.components;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import li.masciul.sugardaddi.R;
import li.masciul.sugardaddi.core.enums.DataConfidence;
import li.masciul.sugardaddi.core.enums.DataSourceType;
import li.masciul.sugardaddi.core.enums.NutritionBasis;
import li.masciul.sugardaddi.core.enums.NutritionLabelMode;
import li.masciul.sugardaddi.core.enums.Unit;
import li.masciul.sugardaddi.core.interfaces.Nutritional;
import li.masciul.sugardaddi.core.interfaces.Searchable;
import li.masciul.sugardaddi.core.models.FoodProduct;
import li.masciul.sugardaddi.core.models.Meal;
import li.masciul.sugardaddi.core.models.Nutrition;
import li.masciul.sugardaddi.core.models.Nutrition.NutrientCategory;
import li.masciul.sugardaddi.core.models.Nutrition.NutrientInfo;
import li.masciul.sugardaddi.core.models.Recipe;
import li.masciul.sugardaddi.core.models.ServingSize;
import li.masciul.sugardaddi.core.utils.DensityConversion;

/**
 * NutritionLabelManager - Nutrition Facts Label Display Component
 *
 * GENERALIZED v3.0 - Item-agnostic display + source transparency
 *
 * Renders an EU-style nutrition facts label for any item that both carries
 * nutrition data and can identify its own origin. Previously coupled to
 * {@code FoodProduct} directly; generalized so the same component serves
 * products, recipes, and meals without a wrapper/adapter object.
 *
 * TYPE BOUND: {@code <T extends Searchable & Nutritional>}
 * Two distinct capabilities are needed from the displayed item, and both are
 * required together - a bounded generic is used instead of a plain
 * {@code Nutritional} parameter plus a separately-passed {@code DataSourceType},
 * because the latter would let a caller pass an item and a "source" that don't
 * actually belong together. {@link Nutritional} supplies the nutrition facts
 * themselves (values, serving size, liquid/solid unit). {@link Searchable}
 * supplies {@code getDataSource()} - the item's OWN origin (e.g. TheMealDB),
 * which is compared against the nutrition's origin (see below) to decide
 * whether a secondary attribution is shown.
 *
 * ARCHITECTURE:
 * - Column 1: Nutrient name (with hierarchy indentation)
 * - Column 2: per 100g (ALWAYS visible - EU legal requirement)
 * - Column 3: per XXg (dynamic, updates with custom amount)
 *
 * DETAILED COLUMNS (see applyColumnWidths() and fitNameRow()):
 * - Columns 2 and 3 are as wide as their widest title or value, measured on
 *   every build: no wrapping ("per 100ml"), no auto-size, the gap between
 *   them is @dimen/nutrition_column_gap.
 * - A name stays on one line. When it is longer than the room left, it runs
 *   under the value columns, a 16dp fade marks where, and the row is dragged
 *   sideways to read it (one scroller per row for now; moving every row
 *   together is a later step: share scrollX and give all names one width).
 *
 * SOURCE TRANSPARENCY (v3.0):
 * Every label now shows the {@link DataConfidence} of the displayed nutrition
 * (e.g. "🔬 Scientific reference data") directly under the header. If the
 * nutrition's own origin ({@link Nutrition#getDataSource()}, a free-text
 * field such as "FATSECRET") differs from the displayed item's origin
 * ({@code item.getDataSource()}, e.g. {@code THEMEALDB}), a second,
 * smaller attribution line is shown for the nutrition specifically - this is
 * the case for a TheMealDB/TheCocktailDB recipe whose ingredients provide no
 * nutrition data and whose values were instead estimated by FatSecret. The
 * item's own primary attribution panel (populated separately, elsewhere, via
 * {@code DetailRendererUtils.populateAttribution}) is untouched by this -
 * it still correctly credits the item's real content source.
 *
 * @param <T> the displayed item's type; must be both searchable (for its own
 *            data source) and nutritional (for the facts being displayed).
 *            FoodProduct, Recipe, and Meal all satisfy this bound today.
 *
 * @version 3.0 - GENERALIZED (Nutritional/Searchable) + CONFIDENCE/ATTRIBUTION
 */
public class NutritionLabelManager<T extends Searchable & Nutritional> {

    private static final double DEFAULT_CUSTOM_AMOUNT = 20.0;

    private final Context context;
    private final LinearLayout container;
    private final LayoutInflater inflater;

    private NutritionLabelMode mode = NutritionLabelMode.DETAILED;

    private boolean isExpanded = false;
    private T currentItem;
    private double currentCustomAmount = DEFAULT_CUSTOM_AMOUNT;
    /**
     * G or ML: the unit the table is shown in, chosen on the quantity input.
     * Null = the nutrition's own basis. The other unit is shown only through
     * the item's density (shownNutrition()); without one, the basis is shown.
     */
    private Unit displayUnit;

    private View headerView;
    private LinearLayout subContainer;
    private LinearLayout nutrientSection;
    private MaterialButton toggleButton;
    private final List<View> optionalRows = new ArrayList<>();

    /**
     * DETAILED rows of the current build, shown or not: their value columns
     * get the measured widths (applyColumnWidths()).
     */
    private final List<View> detailRows = new ArrayList<>();

    /**
     * Width of the value columns of the current build (column 2 + gap +
     * column 3), in px: a name longer than the row minus this runs under them.
     */
    private int valuesWidth;

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
     * Display an item with default custom amount (serving size or 20g)
     */
    public void display(T item) {
        double defaultAmount = getDefaultCustomAmount(item);
        display(item, defaultAmount);
    }

    /**
     * Display an item with a specific custom amount
     */
    public void display(T item, double customAmount) {
        if (item == null) {
            container.setVisibility(View.GONE);
            return;
        }

        Nutrition nutrition = item.getNutrition();
        if (nutrition == null || !nutrition.hasData()) {
            container.setVisibility(View.GONE);
            return;
        }

        this.currentItem = item;
        this.currentCustomAmount = customAmount;
        this.isExpanded = false;
        this.optionalRows.clear();
        this.detailRows.clear();

        container.removeAllViews();
        container.setVisibility(View.VISIBLE);

        buildNutritionLabel(item, shownNutrition(nutrition), customAmount);
    }

    /**
     * Update custom amount and refresh display
     * CRITICAL: Preserves expanded/collapsed state AND updates header
     *
     * @param customAmount the amount of the third column, in unit
     * @param unit         G or ML: the unit the whole table is shown in (the
     *                     quantity input's unit); the other unit than the
     *                     nutrition's basis needs a density, see shownNutrition()
     */
    public void updateCustomAmount(double customAmount, @NonNull Unit unit) {
        if (currentItem != null && customAmount > 0) {
            this.currentCustomAmount = customAmount;
            this.displayUnit = unit;

            // PRESERVE STATE: Remember if expanded before rebuilding
            boolean wasExpanded = this.isExpanded;

            display(currentItem, customAmount);

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
        currentItem = null;
        isExpanded = false;
        optionalRows.clear();
        detailRows.clear();
    }

    // ========================================================================
    // SMART DEFAULTS
    // ========================================================================

    /** Amount (g or ml) used when an item has no usable serving size. */
    public static final double FALLBACK_AMOUNT = 20.0;

    /**
     * Get default custom amount for column 3
     * Priority: serving size > 20g fallback
     */
    private double getDefaultCustomAmount(T item) {
        return defaultAmountFor(item);
    }

    /**
     * THE default amount (g or ml) for an item, used everywhere an amount
     * is needed before the user has chosen one: the label's custom
     * column, the pre-filled amount field in every detail renderer, and
     * what "Add to meal" adds. Having a single rule means the three can
     * no longer drift apart (they used to: recipes showed 150 g in the
     * renderer but added 100 g).
     *
     * Rule (change 5b): the user's preferred serving when set, else the
     * item's own serving size when it has a usable one, else
     * {@link #FALLBACK_AMOUNT}. The unit follows the item's nutrition basis
     * (g or ml) - this returns only the number, read in that unit. A serving
     * in the other unit (FatSecret's "1 cup (240 ml)" on a per-100 g drink)
     * is converted through the item's density when it has one
     * (DensityConversion); without one, a user value in the other unit is
     * ignored and a source serving keeps the historical reading.
     */
    public static double defaultAmountFor(@NonNull Nutritional item) {
        // Meal.getNutrition() is already an absolute total (summed across
        // every portion), not a per-100g figure like FoodProduct/Recipe -
        // buildNutrientsUnified()'s scaling formula (valuePer100 *
        // (amount / 100.0)) only makes sense for the latter. customAmount
        // must be exactly 100.0 for a Meal to make that formula a true
        // no-op; otherwise the meal's real total gets silently rescaled
        // by whatever this method falls through to. Confirmed as the
        // actual cause of the Nutritional information table showing
        // every value at precisely 20% of the correct Summary banner
        // totals - the 20.0 fallback below, applied to an item type it
        // was never designed to handle.
        if (item instanceof Meal) {
            return 100.0;
        }

        Nutrition nutrition = item.getNutrition();
        Unit basisUnit = nutrition != null && nutrition.getBasis() == NutritionBasis.PER_100ML
                ? Unit.ML : Unit.G;
        Double density = nutrition != null ? nutrition.getDensity() : null;

        // 1. The user's preferred serving, read in the item's basis unit.
        Double userQuantity = null;
        Unit userUnit = null;
        if (item instanceof FoodProduct) {
            userQuantity = ((FoodProduct) item).getUserServingQuantity();
            userUnit = ((FoodProduct) item).getUserServingUnit();
        } else if (item instanceof Recipe) {
            userQuantity = ((Recipe) item).getUserServingQuantity();
            userUnit = ((Recipe) item).getUserServingUnit();
        }
        if (userQuantity != null && userQuantity > 0) {
            Double amount = DensityConversion.convert(userQuantity,
                    userUnit == Unit.ML ? Unit.ML : Unit.G, basisUnit, density);
            if (amount != null && amount > 0) {
                return amount;
            }
        }

        // 2. The source's serving size, read in the basis unit.
        ServingSize serving = item.getServingSize();
        if (serving != null && serving.isValid()) {
            Double amount = DensityConversion.servingIn(serving, basisUnit, density);
            if (amount != null && amount > 0) {
                return amount;
            }
        }
        return FALLBACK_AMOUNT;
    }

    /**
     * Text to pre-fill an amount field with: "20" or "12.5". Always uses
     * a '.' decimal separator (Locale.ROOT) on purpose - the field is
     * later read back with Double.parseDouble(), which does not accept
     * the ',' a French locale would produce.
     */
    public static String formatAmountForInput(double amount) {
        return (amount == Math.floor(amount))
                ? String.format(Locale.ROOT, "%.0f", amount)
                : String.format(Locale.ROOT, "%.1f", amount);
    }

    // ========================================================================
    // LABEL BUILDING
    // ========================================================================

    /**
     * The values to show: the stored ones in their own basis, or, when the
     * table is shown in the other unit, a converted copy for display only
     * (never stored): per 100 ml = per 100 g x density, per 100 g = per
     * 100 ml / density. Without a density the stored values are shown as they
     * are, in their own unit.
     */
    @NonNull
    private Nutrition shownNutrition(@NonNull Nutrition nutrition) {
        Unit basisUnit = nutrition.getBasis() == NutritionBasis.PER_100ML ? Unit.ML : Unit.G;
        if (displayUnit == null || displayUnit == basisUnit) return nutrition;
        Double factor = DensityConversion.per100Factor(basisUnit, displayUnit, nutrition.getDensity());
        if (factor == null) return nutrition;
        Nutrition shown = nutrition.scale(factor);
        shown.setBasis(displayUnit == Unit.ML ? NutritionBasis.PER_100ML : NutritionBasis.PER_100G);
        return shown;
    }

    private boolean categoryHasMandatoryNutrients(NutrientCategory category) {
        for (NutrientInfo info : NutrientInfo.getMandatoryNutrients()) {
            if (info.getCategory() == category) {
                return true;
            }
        }
        return false;
    }

    private void buildNutritionLabel(T item, Nutrition nutrition, double customAmount) {
        isFirstCategoryHeader = true;  // Reset for each new label

        // Confidence badge goes directly into container, BEFORE subContainer
        // exists - this is what keeps it visually outside/above the bordered
        // table rather than sandwiched inside it as a table row.
        addSourceInfoRow(item, nutrition);

        subContainer = new LinearLayout(context);
        subContainer.setOrientation(LinearLayout.VERTICAL);

        // Bordered "table" look - same bg_nutrition_block treatment used by
        // every caller's own XML previously (activity_meal_details.xml,
        // detail_off_product.xml, etc.) - now owned here instead, so every
        // caller can go back to a plain, unstyled container and this always
        // renders consistently regardless of which screen it's on.
        subContainer.setBackgroundResource(R.drawable.bg_nutrition_block);

        int paddingPx = (int) (6 * context.getResources().getDisplayMetrics().density);
        subContainer.setPadding(paddingPx, paddingPx, paddingPx, paddingPx);

        container.addView(subContainer);

        addHeader(nutrition, customAmount);  // Column headers + dynamic 3rd column

        nutrientSection = new LinearLayout(context);
        nutrientSection.setOrientation(LinearLayout.VERTICAL);
        subContainer.addView(nutrientSection);

        buildNutrientsUnified(nutrition, customAmount);

        if (mode == NutritionLabelMode.DETAILED) {
            applyColumnWidths();
        }

        if (!optionalRows.isEmpty()) {
            addToggleButton();
        }
    }

    /**
     * Every nutrient the item has, as a tree (fat > saturated > lauric acid...),
     * at any depth.
     *
     * RULES (Romain, 2026-11-22):
     * - "-" (no value) is shown only on the mandatory rows, the EU label ones.
     * - Any other nutrient is drawn only when it has a value (0 included).
     * - An optional parent without a value is not drawn, but its known children
     *   are: they take its place in the tree (ALA directly under polyunsaturated
     *   fat when omega-3 is unknown). Children used to be dropped with their
     *   parent, and nothing below the third level (ALA, EPA, DHA, linoleic
     *   acid...) was ever shown.
     * - Optional rows are hidden until "Show all nutrients".
     * - Parent / child links: isActualChildOf().
     */
    private void buildNutrientsUnified(Nutrition nutrition, double customAmount) {
        List<NutrientInfo> roots = new ArrayList<>();
        for (NutrientInfo info : NutrientInfo.values()) {
            if (info.getIndentLevel() == 0 && isShown(info, nutrition)) {
                roots.add(info);
            }
        }
        roots.sort(Comparator.comparingInt(NutrientInfo::getDisplayOrder));

        NutrientCategory lastCategory = null;
        for (NutrientInfo root : roots) {
            NutrientCategory category = root.getCategory();
            if (category != lastCategory) {
                View headerView = addCategoryHeader(nutrientSection, category);
                if (!categoryHasMandatoryNutrients(category)) {
                    optionalRows.add(headerView);
                    headerView.setVisibility(View.GONE);
                }
                lastCategory = category;
            }
            addNutrientTree(root, nutrition, customAmount, 0);
        }
    }

    /**
     * The row of one nutrient, then its shown children, recursively. An
     * optional nutrient without a value draws no row: its children are drawn
     * at its depth instead.
     */
    private void addNutrientTree(NutrientInfo info, Nutrition nutrition,
                                 double customAmount, int depth) {
        Double value = info.getValue(nutrition);
        int childDepth = depth;
        if (info.isMandatory() || value != null) {
            View row = addNutrientRow(nutrientSection, info, value, customAmount, depth);
            if (!info.isMandatory()) {
                optionalRows.add(row);
                row.setVisibility(View.GONE);
            }
            childDepth = depth + 1;
        }
        for (NutrientInfo child : childrenOf(info)) {
            if (isShown(child, nutrition)) {
                addNutrientTree(child, nutrition, customAmount, childDepth);
            }
        }
    }

    /** Mandatory, known (0 included), or carrying a known nutrient below it. */
    private boolean isShown(NutrientInfo info, Nutrition nutrition) {
        if (info.isMandatory() || info.getValue(nutrition) != null) return true;
        for (NutrientInfo child : childrenOf(info)) {
            if (isShown(child, nutrition)) return true;
        }
        return false;
    }

    /** Direct children of a nutrient (one indent level down), in display order. */
    private List<NutrientInfo> childrenOf(NutrientInfo parent) {
        List<NutrientInfo> children = new ArrayList<>();
        for (NutrientInfo candidate : NutrientInfo.values()) {
            if (candidate.getIndentLevel() == parent.getIndentLevel() + 1
                    && isActualChildOf(candidate, parent)) {
                children.add(candidate);
            }
        }
        children.sort(Comparator.comparingInt(NutrientInfo::getDisplayOrder));
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
                    childKey.equals("nutrient_omega6") ||
                    childKey.equals("nutrient_conjugated_linoleic_acid");
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

        if (parentKey.equals("nutrient_vitamin_d")) {
            return childKey.equals("nutrient_vitamin_d2") ||
                    childKey.equals("nutrient_vitamin_d3");
        }

        if (parentKey.equals("nutrient_vitamin_b9")) {
            return childKey.equals("nutrient_food_folate") ||
                    childKey.equals("nutrient_folic_acid");
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
     */
    private void addHeader(Nutrition nutrition, double customAmount) {
        // Choose header layout based on mode
        int headerLayout = (mode == NutritionLabelMode.SUMMARY)
                ? R.layout.nutrition_label_header_summary
                : R.layout.nutrition_label_header;

        headerView = inflater.inflate(headerLayout, subContainer, false);

        if (mode == NutritionLabelMode.SUMMARY) {
            // Summary header (already set up in XML)
            subContainer.addView(headerView);
        } else {
            // Detailed header (existing logic)
            TextView col1 = headerView.findViewById(R.id.headerNutrient);
            TextView col2 = headerView.findViewById(R.id.headerPer100g);
            TextView col3 = headerView.findViewById(R.id.headerCustomAmount);

            // Column 1: Nutrient name
            col1.setText(R.string.nutrition_label_nutrient_column);

            // Column 2: per-100 in the unit shown: the source's own basis, or
            // the other unit converted through the source's density (see
            // shownNutrition(); never a density of our own).
            String unit = nutrition.getBasis().getUnitLabel();
            col2.setText(context.getString(R.string.nutrition_per_100, unit));

            // Column 3: SIMPLIFIED - Just "per 20g" (dynamic)
            String customHeader = formatAmount(customAmount) + unit;
            col3.setText(context.getString(R.string.nutrition_per_custom, customHeader));

            // Prepared values: one caption above the columns covers both of
            // them, where EU labels put it, instead of "prepared" in each title.
            if (nutrition.isPrepared()) {
                addPreparedCaption();
            }
            subContainer.addView(headerView);
        }
    }

    /**
     * Caption above the column titles for prepared values (see
     * NutritionPreparation): every value and amount in the table is of the
     * product prepared as the package says, water or milk included - logging
     * that milk again would count it twice. Note role (nutrition_text.xml).
     */
    private void addPreparedCaption() {
        float density = context.getResources().getDisplayMetrics().density;
        TextView caption = new TextView(context);
        caption.setText(R.string.nutrition_prepared_caption);
        caption.setTextAppearance(R.style.TextAppearance_SugarDaddi_Nutrition_Note);
        caption.setTextColor(MaterialColors.getColor(context,
                com.google.android.material.R.attr.colorOnSurfaceVariant,
                ContextCompat.getColor(context, R.color.md_theme_onSurfaceVariant)));
        // Same side padding as the column titles (nutrition_label_header.xml)
        int side = (int) (6 * density);
        caption.setPadding(side, (int) (10 * density), side, 0);
        subContainer.addView(caption);
    }

    /**
     * Add the source-transparency badge directly above the bordered
     * nutrition table (added to container, BEFORE subContainer exists -
     * see buildNutritionLabel()): a {@link DataConfidence} indicator for
     * the displayed nutrition, and - only when the nutrition's own origin
     * differs from the displayed item's own origin (e.g. a TheMealDB
     * recipe whose nutrition was estimated by FatSecret) - a small
     * secondary attribution line naming that origin.
     *
     * Reuses the same amber attribution-card colors already used by
     * DetailRendererUtils.populateAttribution()'s panel (?attr/
     * colorAttributionCardAmber/Container) for DataConfidence.
     * requiresCaveat() tiers specifically (today: only ESTIMATED) - both
     * are "here's a caveat about this data" signals, so sharing the same
     * visual language is intentional rather than introducing a second,
     * competing warm color. Every other tier gets a calm, neutral
     * colorSurfaceVariant tint instead - escalating visual weight only
     * where DataConfidence itself says the data genuinely warrants a
     * second look.
     *
     * The item's PRIMARY attribution (who provided the product/recipe
     * itself) is handled elsewhere by
     * {@code DetailRendererUtils.populateAttribution} and is not touched
     * here - this badge is nutrition-specific.
     */
    private void addSourceInfoRow(T item, Nutrition nutrition) {
        float density = context.getResources().getDisplayMetrics().density;

        // Confidence - always shown; null-safe fallback to ESTIMATED
        // mirrors how the value is treated on read from Room (see
        // DataConfidence Javadoc) without mutating the Nutrition object here.
        DataConfidence confidence = nutrition.getDataConfidence() != null
                ? nutrition.getDataConfidence()
                : DataConfidence.ESTIMATED;

        boolean caution = confidence.requiresCaveat();

        int bgColor;
        int textColor;
        if (caution) {
            bgColor = MaterialColors.getColor(context, R.attr.colorAttributionCardAmberContainer,
                    ContextCompat.getColor(context, R.color.colorAttributionCardAmberContainer));
            textColor = MaterialColors.getColor(context, R.attr.colorAttributionCardAmber,
                    ContextCompat.getColor(context, R.color.colorAttributionCardAmber));
        } else {
            bgColor = MaterialColors.getColor(context,
                    com.google.android.material.R.attr.colorSurfaceVariant,
                    ContextCompat.getColor(context, R.color.md_theme_surfaceVariant));
            textColor = MaterialColors.getColor(context,
                    com.google.android.material.R.attr.colorOnSurfaceVariant,
                    ContextCompat.getColor(context, R.color.md_theme_onSurfaceVariant));
        }

        LinearLayout badge = new LinearLayout(context);
        badge.setOrientation(LinearLayout.VERTICAL);

        GradientDrawable badgeBackground = new GradientDrawable();
        badgeBackground.setColor(bgColor);
        badgeBackground.setCornerRadius(8 * density);
        badge.setBackground(badgeBackground);

        int paddingPx = (int) (10 * density);
        badge.setPadding(paddingPx, paddingPx, paddingPx, paddingPx);

        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        badgeParams.bottomMargin = (int) (10 * density);
        badge.setLayoutParams(badgeParams);

        // A meal is an aggregate of many items, so the item-level wording
        // ("from this recipe's ingredients") does not fit it - it gets its
        // own variants.
        boolean isMeal = item instanceof Meal;

        TextView confidenceView = new TextView(context);
        confidenceView.setText(confidence.getEmoji() + " "
                + (isMeal
                ? getMealConfidenceExplanation((Meal) item, confidence)
                : confidence.getConfidenceExplanation(context)));
        // Secondary role (nutrition_text.xml); colour after the appearance,
        // which would otherwise reset it.
        confidenceView.setTextAppearance(R.style.TextAppearance_SugarDaddi_Nutrition_Secondary);
        confidenceView.setTextColor(textColor);
        badge.addView(confidenceView);

        // No "Nutrition data via ..." line for a meal, on purpose. A meal
        // total mixes several items, so one source line either claims the
        // whole meal comes from one source (e.g. "Open Food Facts" when a
        // TheMealDB recipe in the same meal contributes nothing) or has to
        // list sources that supplied no numbers. Each component card
        // already shows its own source badge, and the confidence sentence
        // above already says what is missing.
        if (!isMeal) {
            // Secondary attribution - only when the nutrition's actual source
            // resolves to a real DataSourceType and differs from the item's own.
            DataSourceType nutritionSource = DataSourceType.fromString(nutrition.getDataSource());
            DataSourceType itemSource = item.getDataSource();

            if (nutritionSource != null && nutritionSource != itemSource) {
                addAttributionLine(badge, nutritionSource.getDisplayWithEmoji(context),
                        textColor, density);
            }

        }

        container.addView(badge);
    }

    /**
     * Small italic "Nutrition data via ..." line under the confidence text.
     * Note role (nutrition_text.xml).
     */
    private void addAttributionLine(LinearLayout badge, String sources, int textColor, float density) {
        TextView attributionView = new TextView(context);
        attributionView.setText(context.getString(R.string.nutrition_secondary_attribution, sources));
        attributionView.setTextAppearance(R.style.TextAppearance_SugarDaddi_Nutrition_Note);
        attributionView.setTextColor(textColor);
        LinearLayout.LayoutParams attrParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        attrParams.topMargin = (int) (2 * density);
        attributionView.setLayoutParams(attrParams);
        badge.addView(attributionView);
    }

    /**
     * Confidence sentence for a meal. If some items contribute no
     * nutrition the meal total is only a lower bound, and the sentence
     * says so with the count (Meal downgrades the confidence to ESTIMATED
     * in that case, so the badge is amber too). Otherwise the item-level
     * wording applies, except COMPUTED, whose generic text talks about
     * "this recipe's ingredients".
     */
    private String getMealConfidenceExplanation(Meal meal, DataConfidence confidence) {
        int missing = meal.getPortionsWithoutNutritionCount();
        if (missing > 0) {
            return context.getResources().getQuantityString(
                    R.plurals.meal_nutrition_incomplete, missing, missing);
        }
        if (confidence == DataConfidence.COMPUTED) {
            return context.getString(R.string.confidence_computed_meal_detail);
        }
        return confidence.getConfidenceExplanation(context);
    }

    /**
     * Add nutrient row with proper column alignment
     */
    /**
     * One row of the table.
     *
     * @param depth where the row sits in the DRAWN tree (0 = top level). It is
     *              not the nutrient's own indent level: a hidden unknown parent
     *              lifts its children one level up (see addNutrientTree()).
     *              Depth 2 and more use the deepest layout and the "- " prefix;
     *              each level past 2 adds 16dp more (ALA under omega-3 used to
     *              fall back to the top-level layout).
     */
    private View addNutrientRow(LinearLayout parent, NutrientInfo info, Double value,
                                double customAmount, int depth) {
        View row;

        // Set nutrient name with dash for level 2+
        String nutrientName = getTranslatedNutrientName(info);
        if (depth >= 2) {
            nutrientName = "- " + nutrientName;
        }

        // One unit for both columns, chosen from the per-100 value:
        // 7450 mg reads "7.45 g", 1 760 000 µg reads "1.76 g", and the custom
        // column follows (NutrientFormat.displayUnit()).
        String unit = NutrientFormat.displayUnit(value, info.getUnit());
        double divisor = NutrientFormat.unitDivisor(info.getUnit(), unit);
        Double shown100 = value != null ? value / divisor : null;
        Double shownCustom = calculateScaledValue(shown100, customAmount);

        TextView nameView;
        if (mode == NutritionLabelMode.SUMMARY) {
            // SUMMARY MODE: 2-column layout
            int layoutId;
            switch (Math.min(depth, 2)) {
                case 0:
                    layoutId = R.layout.nutrition_label_row_summary;
                    break;
                case 1:
                    layoutId = R.layout.nutrition_label_row_summary_indented;
                    break;
                default:
                    layoutId = R.layout.nutrition_label_row_summary_double_indented;
                    break;
            }

            row = inflater.inflate(layoutId, parent, false);

            // Populate 2-column layout
            nameView = row.findViewById(R.id.nutrientName);
            TextView totalView = row.findViewById(R.id.nutrientTotal);

            nameView.setText(nutrientName);

            if (totalView != null) {
                totalView.setText(formatNutrientValue(shownCustom, unit));
            }

        } else {
            // DETAILED MODE: 3-column layout
            int layoutId;
            switch (Math.min(depth, 2)) {
                case 0:
                    layoutId = R.layout.nutrition_label_row;
                    break;
                case 1:
                    layoutId = R.layout.nutrition_label_row_indented;
                    break;
                default:
                    layoutId = R.layout.nutrition_label_row_double_indented;
                    break;
            }

            row = inflater.inflate(layoutId, parent, false);

            // Populate 3-column layout
            nameView = row.findViewById(R.id.nutrientName);
            TextView per100View = row.findViewById(R.id.valuePer100g);
            TextView customView = row.findViewById(R.id.valueCustomAmount);

            nameView.setText(nutrientName);

            // Column 2: per 100g value
            if (per100View != null) {
                per100View.setText(formatNutrientValue(shown100, unit));
            }

            // Column 3: custom amount value
            if (customView != null) {
                customView.setText(formatNutrientValue(shownCustom, unit));
            }

            detailRows.add(row);
            fitNameRow(row);
        }

        // Past the deepest layout: push the name and the divider further right.
        if (depth > 2 && nameView != null) {
            int extra = Math.round((depth - 2) * 16 * context.getResources().getDisplayMetrics().density);
            nameView.setPaddingRelative(nameView.getPaddingStart() + extra, nameView.getPaddingTop(),
                    nameView.getPaddingEnd(), nameView.getPaddingBottom());
            if (row instanceof ViewGroup && ((ViewGroup) row).getChildCount() > 1) {
                View divider = ((ViewGroup) row).getChildAt(((ViewGroup) row).getChildCount() - 1);
                if (divider.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
                    ViewGroup.MarginLayoutParams lp =
                            (ViewGroup.MarginLayoutParams) divider.getLayoutParams();
                    lp.setMarginStart(lp.getMarginStart() + extra);
                    divider.setLayoutParams(lp);
                }
            }
        }

        parent.addView(row);
        return row;
    }

    /**
     * Columns 2 and 3 of the DETAILED table as wide as their widest text:
     * the column title and every row's value, hidden optional rows included
     * (so "Show all nutrients" does not move the columns). Run once per build,
     * after the header and every row exist; a unit switch (g / ml) rebuilds
     * the table and so measures "per 100ml" again.
     *
     * Measured with each view's own paint (its text appearance: Title for the
     * header, Primary / Sub / Secondary for the rows), plus its padding and
     * 1px of rounding margin so the text never wraps.
     */
    private void applyColumnWidths() {
        List<TextView> col2 = new ArrayList<>();
        List<TextView> col3 = new ArrayList<>();
        if (headerView != null) {
            col2.add(headerView.findViewById(R.id.headerPer100g));
            col3.add(headerView.findViewById(R.id.headerCustomAmount));
        }
        for (View row : detailRows) {
            col2.add(row.findViewById(R.id.valuePer100g));
            col3.add(row.findViewById(R.id.valueCustomAmount));
        }
        int w2 = setSameWidth(col2);
        int w3 = setSameWidth(col3);
        int gap = context.getResources().getDimensionPixelSize(R.dimen.nutrition_column_gap);
        valuesWidth = w2 + gap + w3;
    }

    /** Give every view of a column the width of its widest text; returns it. */
    private static int setSameWidth(List<TextView> column) {
        int width = 0;
        for (TextView view : column) {
            if (view == null) continue;
            width = Math.max(width, textWidth(view)
                    + view.getPaddingLeft() + view.getPaddingRight() + 1);
        }
        for (TextView view : column) {
            if (view == null) continue;
            ViewGroup.LayoutParams lp = view.getLayoutParams();
            lp.width = width;
            view.setLayoutParams(lp);
        }
        return width;
    }

    /** Width of a one-line TextView's text with its own paint (size, weight). */
    private static int textWidth(TextView view) {
        CharSequence text = view.getText();
        return text == null ? 0
                : (int) Math.ceil(view.getPaint().measureText(text.toString()));
    }

    /**
     * A DETAILED row whose name may be longer than the room left of the value
     * columns (nutrition_label_row*.xml): the name is on one line in a
     * HorizontalScrollView under the value block.
     *
     * After each layout of the row (first draw, "Show all nutrients",
     * rotation), the name is checked against the room it has:
     * - it fits: no fade, no end padding, nothing to drag;
     * - it runs under the values: the fade shows at the start of the value
     *   block, and the name gets an end padding of the values + the fade, so
     *   dragging it to the end shows its last letter just left of the fade.
     * Changes are applied only when the state changes, so the relayout they
     * cause stops there.
     */
    private void fitNameRow(View row) {
        View rowContent = row.findViewById(R.id.rowContent);
        HorizontalScrollView scroll = row.findViewById(R.id.nameScroll);
        TextView nameView = row.findViewById(R.id.nutrientName);
        View fade = row.findViewById(R.id.valueFade);
        if (rowContent == null || scroll == null || nameView == null || fade == null) return;

        int fadeWidth = context.getResources().getDimensionPixelSize(R.dimen.nutrition_name_fade);
        fade.setBackground(nameFadeDrawable());

        rowContent.addOnLayoutChangeListener((v, l, top, r, bottom, ol, ot, or, ob) -> {
            int room = v.getWidth() - v.getPaddingLeft() - v.getPaddingRight();
            if (room <= 0) return;
            // Where the text ends, from the row's start (the indent is the
            // name's start padding, depth past 2 included).
            int textEnd = nameView.getPaddingStart() + textWidth(nameView);
            boolean overflows = textEnd > room - valuesWidth;

            int wantedEnd = overflows ? valuesWidth + fadeWidth : 0;
            int wantedFade = overflows ? View.VISIBLE : View.GONE;
            if (nameView.getPaddingEnd() == wantedEnd && fade.getVisibility() == wantedFade) {
                return;
            }
            // Not during the layout pass that called us
            v.post(() -> {
                nameView.setPaddingRelative(nameView.getPaddingStart(), nameView.getPaddingTop(),
                        wantedEnd, nameView.getPaddingBottom());
                fade.setVisibility(wantedFade);
                if (!overflows) scroll.scrollTo(0, 0);
            });
        });
    }

    /**
     * The fade at the start of the value block: from the table's fill,
     * transparent, to the same fill, opaque (colorSurfaceContainerLowest, as
     * bg_nutrition_block and valueColumns), so the name fades out under
     * the values in light and dark mode. Mirrored for right-to-left.
     */
    private GradientDrawable nameFadeDrawable() {
        int fill = MaterialColors.getColor(context,
                com.google.android.material.R.attr.colorSurfaceContainerLowest,
                ContextCompat.getColor(context, R.color.md_theme_surfaceContainerLowest));
        // From the configuration: the row is not attached yet, its own
        // direction is not resolved.
        boolean rtl = context.getResources().getConfiguration().getLayoutDirection()
                == View.LAYOUT_DIRECTION_RTL;
        return new GradientDrawable(
                rtl ? GradientDrawable.Orientation.RIGHT_LEFT
                    : GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{fill & 0x00FFFFFF, fill});
    }

    private void addToggleButton() {
        // Material outlined button, like the - / + and pencil buttons of the
        // quantity input: no elevation shadow (a plain Button keeps the theme's
        // one), the theme's outline, corners in dp. Primary role text.
        toggleButton = new MaterialButton(context, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        toggleButton.setText(R.string.show_all_nutrients);
        toggleButton.setOnClickListener(v -> toggleExpanded());
        toggleButton.setAllCaps(false);
        toggleButton.setTextAppearance(R.style.TextAppearance_SugarDaddi_Nutrition_Primary);

        float density = context.getResources().getDisplayMetrics().density;
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        int margin = (int) (8 * density);
        params.setMargins(margin, margin, margin, margin);
        toggleButton.setLayoutParams(params);

        // Colours from the current theme (light and dark): text on surface,
        // outline stroke, as before.
        int textColor = MaterialColors.getColor(context,
                com.google.android.material.R.attr.colorOnSurface,
                ContextCompat.getColor(context, R.color.md_theme_onSurface));
        int strokeColor = MaterialColors.getColor(context,
                com.google.android.material.R.attr.colorOutline,
                ContextCompat.getColor(context, R.color.md_theme_outline));
        toggleButton.setTextColor(textColor);
        toggleButton.setStrokeColor(android.content.res.ColorStateList.valueOf(strokeColor));
        toggleButton.setStrokeWidth((int) (1 * density));
        toggleButton.setCornerRadius((int) (8 * density));

        subContainer.addView(toggleButton);
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

    /** The value for the custom amount; null stays null (unknown is not 0). */
    @Nullable
    private Double calculateScaledValue(@Nullable Double valuePer100, double amount) {
        if (valuePer100 == null) {
            return null;
        }
        return valuePer100 * (amount / 100.0);
    }

    /**
     * "-" for an unknown value (null), "0 g" for a confirmed zero, up to 2
     * decimals otherwise, energy in whole numbers: the rules of
     * NutrientFormat, shared with every other screen.
     */
    private String formatNutrientValue(@Nullable Double value, String unit) {
        return NutrientFormat.value(value, unit);
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