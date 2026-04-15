package li.masciul.sugardaddi.ui.components;

import android.content.Context;

import li.masciul.sugardaddi.R;
import li.masciul.sugardaddi.core.utils.EUDietaryReferenceValues;
import li.masciul.sugardaddi.core.enums.NutrientBannerStyle;
import li.masciul.sugardaddi.core.enums.NutrientLevel;
import li.masciul.sugardaddi.core.models.Nutrition;

/**
 * NutrientBannerHelper - Factory for creating nutrient banner views
 *
 * VERSION 2.0 - Simplified for scalable banner system
 *
 * Changes from v1:
 * - Removed complex sizing calculations (handled by NutrientBannerView)
 * - Simplified API (just creates banners with data)
 * - Banners automatically scale to fit container
 * - Max width calculated once and shared across all banners
 *
 * The new system:
 * 1. NutrientBannerView calculates optimal size on first creation
 * 2. Size is based on fitting 5 banners in portrait mode
 * 3. This becomes max width for both portrait and landscape
 * 4. Banners maintain aspect ratio while respecting max width
 * 5. Container uses HorizontalScrollView for landscape overflow
 *
 * Usage remains the same:
 * ```java
 * NutrientBannerView[] banners = NutrientBannerHelper.createDailySummaryBanners(
 *     context, nutrition, NutrientBannerStyle.VERTICAL
 * );
 * ```
 *
 * @version 2.0 - Simplified for scalable system
 */
public class NutrientBannerHelper {

    // ========== CONSTANTS ==========

    private static final double MIN_MEANINGFUL_VALUE = 0.5;

    // ========== MAIN FACTORY METHODS ==========

    /**
     * Create energy banner (special case with KJ + Kcal)
     */
    public static NutrientBannerView createEnergyBanner(
            Context context,
            Nutrition nutrition,
            NutrientBannerStyle style) {

        if (nutrition == null) return null;

        // Treat null as 0
        Double kcal = nutrition.getEnergyKcal();
        Double kj = nutrition.getEnergyKj();

        double kcalValue = (kcal != null) ? kcal : 0.0;
        double kjValue = (kj != null) ? kj : 0.0;

        // Calculate KJ if not provided
        if (kjValue == 0 && kcalValue > 0) {
            kjValue = kcalValue * 4.184;
        }

        // Calculate % DRV
        double percentDRV = EUDietaryReferenceValues.calculatePercentDRV(
                kcalValue,
                EUDietaryReferenceValues.ENERGY_KCAL
        );

        // Force NEUTRAL for zero or very low values
        NutrientLevel level;
        if (kcalValue < MIN_MEANINGFUL_VALUE) {
            level = NutrientLevel.NEUTRAL;
        } else {
            level = NutrientLevel.NEUTRAL;  // Energy is always neutral
        }

        // Create view
        NutrientBannerView view = new NutrientBannerView(context);
        String energyLabel = context.getString(R.string.nutrient_energy);
        view.setEnergy(energyLabel, kcalValue, kjValue, percentDRV, level, style);


        return view;
    }

    /**
     * Create standard nutrient banner
     */
    public static NutrientBannerView createNutrientBanner(
            Context context,
            String name,
            Double value,
            String unit,
            Nutrition.NutrientInfo nutrientInfo,
            NutrientBannerStyle style) {

        // Treat null as 0, reject negative
        if (value != null && value < 0) return null;
        double actualValue = (value != null) ? value : 0.0;

        // Get DRV
        Double drv = EUDietaryReferenceValues.getDRV(nutrientInfo);

        // Calculate % DRV
        double percentDRV = 0.0;
        if (drv != null && drv > 0) {
            percentDRV = EUDietaryReferenceValues.calculatePercentDRV(actualValue, drv);
        }

        // Evaluate level (force NEUTRAL for negligible values)
        NutrientLevel level;
        if (actualValue < MIN_MEANINGFUL_VALUE) {
            level = NutrientLevel.NEUTRAL;
        } else {
            level = NutrientLevel.evaluateNutrient(nutrientInfo, percentDRV);
        }

        // Create view
        NutrientBannerView view = new NutrientBannerView(context);
        view.setNutrient(name, actualValue, unit, percentDRV, level, style);

        return view;
    }

    /**
     * Create banner from NutrientInfo and Nutrition object
     */
    public static NutrientBannerView createBannerFromNutrientInfo(
            Context context,
            Nutrition nutrition,
            Nutrition.NutrientInfo nutrientInfo,
            NutrientBannerStyle style) {

        if (nutrition == null || nutrientInfo == null) return null;

        // Special case: Energy
        if (nutrientInfo == Nutrition.NutrientInfo.ENERGY_KCAL ||
                nutrientInfo == Nutrition.NutrientInfo.ENERGY_KJ) {
            return createEnergyBanner(context, nutrition, style);
        }

        // Get value
        Double value = nutrientInfo.getValue(nutrition);

        // Get unit
        String unit = nutrientInfo.getUnit();

        // Get display name
        String name = getNutrientDisplayName(context, nutrientInfo);

        return createNutrientBanner(context, name, value, unit, nutrientInfo, style);
    }

    // ========== CONVENIENCE METHODS ==========

    /**
     * Create set of 5 summary banners for daily overview
     *
     * Banners automatically scale to fit container width.
     * In portrait: fills width (5 banners side-by-side)
     * In landscape: maintains portrait size, scrolls horizontally
     */
    public static NutrientBannerView[] createDailySummaryBanners(
            Context context,
            Nutrition nutrition,
            NutrientBannerStyle style) {

        NutrientBannerView[] banners = new NutrientBannerView[5];

        if (nutrition == null) {
            // Create empty banners for consistency
            Nutrition emptyNutrition = new Nutrition();
            nutrition = emptyNutrition;
        }

        // Energy
        banners[0] = createEnergyBanner(context, nutrition, style);

        // Carbs
        banners[1] = createBannerFromNutrientInfo(
                context, nutrition, Nutrition.NutrientInfo.CARBOHYDRATES, style
        );

        // Proteins
        banners[2] = createBannerFromNutrientInfo(
                context, nutrition, Nutrition.NutrientInfo.PROTEINS, style
        );

        // Fats
        banners[3] = createBannerFromNutrientInfo(
                context, nutrition, Nutrition.NutrientInfo.FAT, style
        );

        // Fiber
        banners[4] = createBannerFromNutrientInfo(
                context, nutrition, Nutrition.NutrientInfo.FIBER, style
        );

        return banners;
    }

    // ========== OTHER CONVENIENCE METHODS ==========

    public static NutrientBannerView createProteinBanner(
            Context context,
            Nutrition nutrition,
            NutrientBannerStyle style) {
        return createBannerFromNutrientInfo(
                context, nutrition, Nutrition.NutrientInfo.PROTEINS, style
        );
    }

    public static NutrientBannerView createCarbsBanner(
            Context context,
            Nutrition nutrition,
            NutrientBannerStyle style) {
        return createBannerFromNutrientInfo(
                context, nutrition, Nutrition.NutrientInfo.CARBOHYDRATES, style
        );
    }

    public static NutrientBannerView createFatBanner(
            Context context,
            Nutrition nutrition,
            NutrientBannerStyle style) {
        return createBannerFromNutrientInfo(
                context, nutrition, Nutrition.NutrientInfo.FAT, style
        );
    }

    public static NutrientBannerView createFiberBanner(
            Context context,
            Nutrition nutrition,
            NutrientBannerStyle style) {
        return createBannerFromNutrientInfo(
                context, nutrition, Nutrition.NutrientInfo.FIBER, style
        );
    }

    public static NutrientBannerView createSaltBanner(
            Context context,
            Nutrition nutrition,
            NutrientBannerStyle style) {
        return createBannerFromNutrientInfo(
                context, nutrition, Nutrition.NutrientInfo.SALT, style
        );
    }

    public static NutrientBannerView createSugarBanner(
            Context context,
            Nutrition nutrition,
            NutrientBannerStyle style) {
        return createBannerFromNutrientInfo(
                context, nutrition, Nutrition.NutrientInfo.SUGARS, style
        );
    }

    public static NutrientBannerView createSaturatedFatBanner(
            Context context,
            Nutrition nutrition,
            NutrientBannerStyle style) {
        return createBannerFromNutrientInfo(
                context, nutrition, Nutrition.NutrientInfo.SATURATED_FAT, style
        );
    }

    // ========== HELPER METHODS ==========

    /**
     * Get display name for nutrient - uses localized short name for banners
     *
     * @param context Android context for string resource access
     * @param nutrientInfo Nutrient to get name for
     * @return Localized short display name suitable for banners
     */
    private static String getNutrientDisplayName(
            Context context,
            Nutrition.NutrientInfo nutrientInfo) {

        // Use the localized short display name method
        return nutrientInfo.getShortDisplayName(context);
    }

    private NutrientBannerHelper() {
        throw new AssertionError("Cannot instantiate NutrientBannerHelper");
    }
}