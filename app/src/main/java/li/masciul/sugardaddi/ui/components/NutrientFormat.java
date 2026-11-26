package li.masciul.sugardaddi.ui.components;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * NutrientFormat - how nutrient values are written on screen, everywhere: the
 * nutrition table, the day and meal banners, search cards, detail headers,
 * journal rows, shared text.
 *
 * Plain Java (no Android), so every rule is covered by unit tests
 * (NutrientFormatTest). Quantities (the amount logged) have their own rules
 * in QuantityText.
 *
 * DISPLAY ONLY: stored values, scaled values and sums are never rounded
 * (Nutrition.scale(), add(), sum() work on full doubles). Rounding happens
 * here, once, on the way to the screen, so two screens showing the same value
 * always show the same text. Each screen used to have its own format (table
 * %.1f half up, banners "0.#" half even, cards %.1f, journal %.0f), which is
 * how one meal read 19.6 g on a banner and 19.7 g in the table.
 *
 * RULES (Romain, 2026-11-25):
 * - At most 2 decimals, only when they carry information: 1.76 g, 1.7 g, 24 g,
 *   0.4 g, 46.5 g (no trailing zeros).
 * - Rounded half up: 1.766 -> 1.77, 1.765 -> 1.77. Floating point noise from
 *   sums and scaling (1.7649999999999999 for 1.765) is removed first, so a
 *   value is never rounded down because of how a double stores it.
 * - Energy (kcal, kJ) in whole numbers, like EU labels: 43 kcal.
 * - Unknown (null) is "-", a confirmed zero is "0 g", a value under 0.01 is
 *   "< 0.01 g" (not "0 g": it is not zero).
 * - Large values climb units, as many steps as needed: 7450 mg is shown in g,
 *   1 760 000 µg in g (displayUnit(), unitDivisor()).
 * - ASCII digits, no grouping, the app language's decimal separator
 *   (LanguageManager sets the default locale): "1,76 g" in French.
 */
public final class NutrientFormat {

    /** Most decimals ever shown for a nutrient value. */
    public static final int MAX_DECIMALS = 2;

    /** Under this (and above zero), a value reads "< 0.01". */
    private static final double SMALLEST_SHOWN = 0.01;

    /**
     * Significant digits kept before rounding: removes the binary noise of
     * doubles (1.7649999999999999 -> 1.765) and keeps every real digit of a
     * nutrient value (no food value needs 12 significant digits).
     */
    private static final MathContext NOISE = new MathContext(12, RoundingMode.HALF_UP);

    private static final String MICROGRAM = "µg";

    private NutrientFormat() {
        // Static helpers only
    }

    // ========================================================================
    // NUMBERS
    // ========================================================================

    /** number(value, separator) with the app language's decimal separator. */
    @NonNull
    public static String number(double value) {
        return number(value, defaultSeparator());
    }

    /**
     * At most 2 decimals, rounded half up, no trailing zeros, ASCII digits,
     * no grouping: 1.766 -> "1.77", 1.70 -> "1.7", 24.0 -> "24",
     * 1234.5 -> "1234.5".
     *
     * @param decimalSeparator ',' or '.'; anything else is treated as '.'
     */
    @NonNull
    public static String number(double value, char decimalSeparator) {
        return write(round(value, MAX_DECIMALS), decimalSeparator);
    }

    /** energy(value, separator) with the app language's decimal separator. */
    @NonNull
    public static String energy(double value) {
        return energy(value, defaultSeparator());
    }

    /**
     * Energy in a whole number, rounded half up: 43.4 -> "43", 64.5 -> "65".
     * The separator is unused today (no decimals) but kept so every format
     * has the same shape.
     */
    @NonNull
    public static String energy(double value, char decimalSeparator) {
        return write(round(value, 0), decimalSeparator);
    }

    // ========================================================================
    // VALUE + UNIT
    // ========================================================================

    /** value(value, unit, separator) with the app language's decimal separator. */
    @NonNull
    public static String value(@Nullable Double value, @NonNull String unit) {
        return value(value, unit, defaultSeparator());
    }

    /**
     * A nutrient value with its unit, as the nutrition table shows it:
     * null -> "-", 0 -> "0 g", 0.004 -> "< 0.01 g", 1.766 -> "1.77 g",
     * 43.4 kcal -> "43 kcal". The value is shown in the unit given: convert
     * first with displayUnit() / unitDivisor() when the unit may climb.
     */
    @NonNull
    public static String value(@Nullable Double value, @NonNull String unit, char decimalSeparator) {
        if (value == null) {
            return "-";
        }
        if (isEnergyUnit(unit)) {
            return energy(value, decimalSeparator) + " " + unit;
        }
        if (value == 0.0) {
            return "0 " + unit;
        }
        if (value > 0 && value < SMALLEST_SHOWN) {
            return "< " + write(BigDecimal.valueOf(SMALLEST_SHOWN), decimalSeparator) + " " + unit;
        }
        return number(value, decimalSeparator) + " " + unit;
    }

    /** kcal and kJ: whole numbers. */
    public static boolean isEnergyUnit(@Nullable String unit) {
        return "kcal".equals(unit) || "kJ".equals(unit);
    }

    // ========================================================================
    // UNIT STEPS (µg -> mg -> g)
    // ========================================================================

    /**
     * The unit to show a value in: from 1000 up, the next unit up, as many
     * steps as needed. 7450 mg is shown in g, 1 760 000 µg in g too (one step
     * used to stop it at "1760.0 mg"). Units without a larger one (g, kcal,
     * kJ, %) never change. Stored values never change, only the display.
     *
     * @param value the stored value, in unit; null keeps the unit
     */
    @NonNull
    public static String displayUnit(@Nullable Double value, @NonNull String unit) {
        if (value == null) return unit;
        double shown = value;
        String shownUnit = unit;
        String larger = largerUnit(shownUnit);
        while (shown >= 1000.0 && larger != null) {
            shown /= 1000.0;
            shownUnit = larger;
            larger = largerUnit(shownUnit);
        }
        return shownUnit;
    }

    /**
     * What to divide a stored value by to show it in the unit chosen by
     * displayUnit(): 1000 per step from the stored unit, 1 for the same unit
     * (or a unit that cannot be reached from it).
     */
    public static double unitDivisor(@NonNull String storedUnit, @NonNull String shownUnit) {
        double divisor = 1.0;
        String unit = storedUnit;
        while (unit != null && !unit.equals(shownUnit)) {
            divisor *= 1000.0;
            unit = largerUnit(unit);
        }
        return unit == null ? 1.0 : divisor;
    }

    /** The unit 1000 times larger (µg -> mg -> g), or null at the top. */
    @Nullable
    static String largerUnit(@NonNull String unit) {
        if (MICROGRAM.equals(unit)) return "mg";
        if ("mg".equals(unit)) return "g";
        return null;
    }

    // ========================================================================
    // HELPERS
    // ========================================================================

    /** Noise removed, then rounded half up to the given decimals. */
    @NonNull
    private static BigDecimal round(double value, int decimals) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return BigDecimal.ZERO;
        }
        // BigDecimal.valueOf() starts from the shortest text of the double
        // ("1.765"), round(NOISE) clears what sums and scaling leave behind
        // ("1.7649999999999999" -> "1.76500000000").
        return BigDecimal.valueOf(value).round(NOISE).setScale(decimals, RoundingMode.HALF_UP);
    }

    /** No trailing zeros, no exponent ("24", not "2.4E+1"), chosen separator. */
    @NonNull
    private static String write(@NonNull BigDecimal rounded, char decimalSeparator) {
        String text = rounded.signum() == 0
                ? "0"
                : rounded.stripTrailingZeros().toPlainString();
        return decimalSeparator == ',' ? text.replace('.', ',') : text;
    }

    /** ',' when the default locale writes decimals with a comma, '.' otherwise. */
    private static char defaultSeparator() {
        char separator = DecimalFormatSymbols.getInstance(Locale.getDefault()).getDecimalSeparator();
        return separator == ',' ? ',' : '.';
    }
}
