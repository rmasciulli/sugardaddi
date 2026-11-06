package li.masciul.sugardaddi.core.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import li.masciul.sugardaddi.core.enums.Unit;
import li.masciul.sugardaddi.core.models.ServingSize;

/**
 * DensityConversion - grams and milliliters of one food, through its density.
 *
 * WHY: nutrition is stored in ONE basis, per 100 g or per 100 ml, with the
 * source's own numbers, and stored values are never converted. A density
 * (grams per ml, source-derived only: Nutrition.getDensity()) lets amounts,
 * servings and the per-100 column be read in the other unit, on screen and in
 * calculations, without touching what is stored.
 *
 *   whole milk, 1.033 g/ml:  250 ml = 258.25 g,  per 100 ml = per 100 g x 1.033
 *
 * RULES:
 * - Only G and ML are converted between. Other units are first read in their
 *   own kind (a cup is a volume, an ounce a weight) through ServingSize.
 * - No density, no conversion: null. Callers decide what null means (an
 *   unknown amount for a logged entry, the fallback amount for a default...).
 *   The one exception is servingIn(), which keeps the app's historical reading
 *   for servings, see there.
 *
 * Plain Java (no Android), covered by DensityConversionTest.
 */
public final class DensityConversion {

    private DensityConversion() {
        // Static helpers only
    }

    /** G for a weight unit, ML for a volume unit, null for anything else (or null). */
    @Nullable
    public static Unit kindOf(@Nullable Unit unit) {
        if (unit == null) return null;
        if (unit.getType() == Unit.UnitType.WEIGHT) return Unit.G;
        if (unit.getType() == Unit.UnitType.VOLUME) return Unit.ML;
        return null;
    }

    /**
     * An amount in {@code from} expressed in {@code to}, both G or ML.
     *
     * @param density grams per ml, or null when unknown
     * @return the amount unchanged when both units are the same; converted
     *         through the density when they differ; null when they differ and
     *         there is no density, or when a unit is neither G nor ML
     */
    @Nullable
    public static Double convert(double amount, @NonNull Unit from, @NonNull Unit to,
                                 @Nullable Double density) {
        if (!isGOrMl(from) || !isGOrMl(to)) return null;
        if (from == to) return amount;
        if (!isPositive(density)) return null;
        return from == Unit.ML ? amount * density : amount / density;
    }

    /**
     * The factor turning values per 100 of {@code basis} into values per 100 of
     * {@code unit}: 1 for the basis itself, the density from per 100 g to per
     * 100 ml (100 ml of milk weighs 103.3 g), its inverse the other way.
     *
     * @return null when the units differ and there is no density
     */
    @Nullable
    public static Double per100Factor(@NonNull Unit basis, @NonNull Unit unit,
                                      @Nullable Double density) {
        Double hundred = convert(100.0, unit, basis, density);
        return hundred != null ? hundred / 100.0 : null;
    }

    /**
     * A serving's size in {@code target} (G or ML).
     *
     * The serving is read in its own kind first (grams for a weight, ml for a
     * volume, the explicit equivalents of ServingSize winning), then converted
     * through the density when the kinds differ.
     *
     * Without a density, the historical reading is kept: ServingSize counts a
     * ml as a gram, so "250 ml" read in grams is 250. Every serving reader used
     * that before; it is left as is here rather than changed silently (open
     * question, see the roadmap's loose ends).
     *
     * @return the size, or null when the serving has none
     */
    @Nullable
    public static Double servingIn(@Nullable ServingSize serving, @NonNull Unit target,
                                   @Nullable Double density) {
        if (serving == null || !isGOrMl(target)) return null;
        Unit kind = kindOf(serving.getUnit());
        if (kind != null && kind != target) {
            Double own = kind == Unit.ML ? serving.getAsMilliliters() : serving.getAsGrams();
            if (isPositive(own)) {
                Double converted = convert(own, kind, target, density);
                if (converted != null) return converted;
            }
        }
        // Same kind, no unit kind (a "1 slice" with a gram equivalent), or no density.
        Double size = target == Unit.ML ? serving.getAsMilliliters() : serving.getAsGrams();
        return isPositive(size) ? size : null;
    }

    private static boolean isGOrMl(@Nullable Unit unit) {
        return unit == Unit.G || unit == Unit.ML;
    }

    private static boolean isPositive(@Nullable Double value) {
        return value != null && value > 0 && !Double.isInfinite(value) && !Double.isNaN(value);
    }
}
