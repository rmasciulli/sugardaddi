package li.masciul.sugardaddi.ui.utils;

import android.content.Context;
import android.icu.text.PluralRules;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

import li.masciul.sugardaddi.R;
import li.masciul.sugardaddi.core.enums.Unit;
import li.masciul.sugardaddi.core.models.PortionQuantity;
import li.masciul.sugardaddi.ui.components.QuantityText;

/**
 * MealEntryLabel - how a logged amount reads in a meal's list of items.
 *
 * Built from the entry's PortionQuantity snapshot, exactly as it was logged:
 *   servings, serving size known   -> "2 servings (80 g)"  / "2 portions (80 g)"
 *   servings, serving size unknown -> "1 serving"          / "1 portion"
 *   typed quantity                 -> "250 ml", "105 g"
 * Numbers go through QuantityText, so they look the same as in the quantity input.
 *
 * PLURALS WITH DECIMALS: Android's plural resources only take whole numbers,
 * but "1.5 servings" (English) and "1,5 portion" (French: anything under 2 is
 * singular) need the decimal. The category is picked with the platform's ICU
 * plural rules for the app language, then a whole number of that same
 * category is passed to getQuantityString() so the right text is chosen.
 */
public final class MealEntryLabel {

    private MealEntryLabel() {
        // Static helpers only
    }

    /**
     * @return the label, or null when the snapshot is missing or holds nothing
     *         to show (the caller then falls back to its own text)
     */
    @Nullable
    public static String format(@NonNull Context context, @Nullable PortionQuantity quantity) {
        if (quantity == null) return null;
        Double total = quantity.getTotalQuantity();
        Unit unit = quantity.getUnit();
        boolean hasAmount = total != null && total > 0 && (unit == Unit.G || unit == Unit.ML);

        Double count = quantity.getServingCount();
        if (quantity.getInputMode() == PortionQuantity.InputMode.SERVINGS && count != null && count > 0) {
            String servings = servingsText(context, count);
            return hasAmount
                    ? context.getString(R.string.meal_entry_servings_amount,
                            servings, QuantityText.formatAmount(total, unit))
                    : servings;
        }
        return hasAmount ? QuantityText.formatAmount(total, unit) : null;
    }

    /** "2 servings", "1 serving", "1,5 portion": number as displayed, matching plural. */
    @NonNull
    private static String servingsText(@NonNull Context context, double count) {
        String number = QuantityText.format(count);
        // Choose the plural from the number as DISPLAYED: 1.04 shows as "1".
        Double shown = QuantityText.parse(number);
        double value = shown != null ? shown : count;

        String category = PluralRules.forLocale(Locale.getDefault()).select(value);
        return context.getResources().getQuantityString(
                R.plurals.meal_entry_servings, representativeOf(category), number);
    }

    /**
     * A whole number in the given plural category, valid for the app's two
     * languages: "one" -> 1, "many" -> 1,000,000 (French), anything else -> 2.
     * Revisit when a language with "two" / "few" categories is added.
     */
    private static int representativeOf(@NonNull String category) {
        switch (category) {
            case "one":     // PluralRules.KEYWORD_ONE
                return 1;
            case "many":    // PluralRules.KEYWORD_MANY
                return 1_000_000;
            default:
                return 2;
        }
    }
}
