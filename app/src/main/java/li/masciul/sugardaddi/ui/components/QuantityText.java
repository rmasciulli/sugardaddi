package li.masciul.sugardaddi.ui.components;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.math.RoundingMode;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.Locale;

import li.masciul.sugardaddi.core.enums.Unit;

/**
 * QuantityText - how quantities are read from and written to text.
 *
 * Plain Java (no Android), so every rule is covered by unit tests. Used by the
 * quantity input, and meant for every place that shows a logged amount (meal
 * rows in change 4b), so a figure always looks the same wherever it appears.
 *
 * RULES:
 * - Reading: digits and at most one decimal separator, "." or "," (French
 *   keyboards send ","), spaces ignored. Anything else is rejected, including
 *   what Double.parseDouble() alone would accept ("1e5", "0x1p3", "NaN").
 * - Writing: at most one decimal, rounded half up, no grouping, ASCII digits,
 *   with the app language's decimal separator. ASCII digits whatever the device
 *   language, so parse() can always read back what format() wrote.
 */
public final class QuantityText {

    private QuantityText() {
        // Static helpers only
    }

    /**
     * Read a typed quantity: "105", "33,5" or "33.5" (spaces ignored).
     *
     * @return the value, or null when empty, malformed, zero, negative or not finite
     */
    @Nullable
    public static Double parse(@Nullable String text) {
        if (text == null) return null;
        String cleaned = text.replace(" ", "").replace("\u00A0", "").replace(',', '.');
        // Digits with one optional separator, and at least one digit somewhere.
        if (!cleaned.matches("[0-9]*\\.?[0-9]*") || !cleaned.matches(".*[0-9].*")) return null;
        try {
            double value = Double.parseDouble(cleaned);
            return (value > 0 && !Double.isInfinite(value) && !Double.isNaN(value)) ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * format(value, separator) with the app language's decimal separator.
     * LanguageManager sets the default locale, so "3,5" in French, "3.5" in English.
     */
    @NonNull
    public static String format(double value) {
        return format(value, defaultSeparator());
    }

    /**
     * At most one decimal, rounded half up, ASCII digits, no grouping:
     * 3.5 -> "3,5" with ',', 105.0 -> "105", 3.25 -> "3,3", 1234.0 -> "1234".
     *
     * @param decimalSeparator ',' or '.'; anything else is treated as '.'
     */
    @NonNull
    public static String format(double value, char decimalSeparator) {
        NumberFormat format = NumberFormat.getNumberInstance(Locale.ROOT);
        format.setGroupingUsed(false);
        format.setMinimumFractionDigits(0);
        format.setMaximumFractionDigits(1);
        format.setRoundingMode(RoundingMode.HALF_UP);
        String text = format.format(value);
        return decimalSeparator == ',' ? text.replace('.', ',') : text;
    }

    /** A value followed by its unit: "105 g", "3,5 ml". */
    @NonNull
    public static String formatAmount(double value, @NonNull Unit unit) {
        return format(value) + " " + unit.getAbbreviation();
    }

    /** ',' when the default locale writes decimals with a comma, '.' otherwise. */
    private static char defaultSeparator() {
        char separator = DecimalFormatSymbols.getInstance(Locale.getDefault()).getDecimalSeparator();
        return separator == ',' ? ',' : '.';
    }
}
