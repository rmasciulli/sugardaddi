package li.masciul.sugardaddi.data.sources.usda.mappers;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import li.masciul.sugardaddi.core.enums.Unit;
import li.masciul.sugardaddi.core.models.ServingSize;

/**
 * USDAPortions - USDA FoodData Central portions turned into serving sizes.
 *
 * WHERE PORTIONS COME FROM: "foodPortions" on a food, read by two paths that
 * both fill a Portion and call this class: the detail endpoint (FDCFoodDetail,
 * through USDAMapper.mapFoodDetail()) and the bulk import (USDAImportService
 * stream-parses the same objects). One set of rules for both.
 *
 * THE THREE FORMATS (checked on real records, 2026-11-21):
 *   SR Legacy   milk 171265:    measureUnit "undetermined", label in modifier
 *                               ("cup", "fl oz", "tbsp", "quart")
 *   Foundation  broccoli 747447: label in measureUnit.name ("cup"), modifier is
 *                               a detail ("chopped"); a RACC row is included
 *   Survey      milk 2705385:    measureUnit "undetermined", label in
 *                               portionDescription ("1 cup"), modifier is a
 *                               numeric code; "Guideline amount per ..." rows;
 *                               "Quantity not specified" = the survey's default
 * In all three, the list is NOT sorted: sequenceNumber gives USDA's order.
 *
 * RULES:
 * - Kept: a portion with a positive gram weight, except RACC (a regulatory
 *   reference amount, not a portion) and "Guideline amount per ..." rows.
 *   Every portion has its own gram weight, so even "1 oz" is unambiguous.
 * - Stored in grams, with a label as the description: "1 cup, chopped (76 g)".
 *   No density is derived from volume portions (decision 7A: a cup of a solid
 *   is a bulk density, not the food's).
 * - Typical serving (decision B, Romain 2026-11-21): "Quantity not specified"
 *   when present (Survey), else the first portion in USDA's order.
 *
 * Plain Java (no Android), covered by USDAPortionsTest.
 */
public final class USDAPortions {

    /** One FDC portion, as read from either path. Every field may be missing. */
    public static final class Portion {
        @Nullable public Double gramWeight;
        @Nullable public Double amount;
        @Nullable public Integer sequenceNumber;
        /** measureUnit.name: "cup", "RACC", or "undetermined". */
        @Nullable public String unitName;
        /** SR Legacy: the unit ("cup"). Foundation: a detail ("chopped"). Survey: a code. */
        @Nullable public String modifier;
        /** Survey: the label ("1 cup", "Quantity not specified"). */
        @Nullable public String portionDescription;
    }

    private static final String UNDETERMINED = "undetermined";
    private static final String RACC = "RACC";
    private static final String GUIDELINE_PREFIX = "guideline amount";
    private static final String QUANTITY_NOT_SPECIFIED = "Quantity not specified";
    private static final String NO_DESCRIPTION = "NONE";

    private USDAPortions() {
        // Static helpers only
    }

    /** Every usable portion as a serving size in grams, in USDA's order. */
    @NonNull
    public static List<ServingSize> servingOptions(@Nullable List<Portion> portions) {
        List<ServingSize> options = new ArrayList<>();
        for (Portion p : usable(portions)) options.add(toServingSize(p));
        return options;
    }

    /**
     * The typical serving: "Quantity not specified" when present, else the first
     * usable portion in USDA's order; null when there is none.
     */
    @Nullable
    public static ServingSize typicalServing(@Nullable List<Portion> portions) {
        List<Portion> usable = usable(portions);
        for (Portion p : usable) {
            if (QUANTITY_NOT_SPECIFIED.equalsIgnoreCase(trimmed(p.portionDescription))) {
                return toServingSize(p);
            }
        }
        return usable.isEmpty() ? null : toServingSize(usable.get(0));
    }

    // ========== RULES (package-private for tests) ==========

    /** Kept portions, sorted by sequenceNumber (missing numbers last, ties kept in order). */
    @NonNull
    static List<Portion> usable(@Nullable List<Portion> portions) {
        List<Portion> kept = new ArrayList<>();
        if (portions == null) return kept;
        for (Portion p : portions) {
            if (p == null || p.gramWeight == null || !(p.gramWeight > 0)) continue;
            if (RACC.equalsIgnoreCase(trimmed(p.unitName))) continue;
            String description = trimmed(p.portionDescription);
            if (description != null
                    && description.toLowerCase(Locale.ROOT).startsWith(GUIDELINE_PREFIX)) continue;
            kept.add(p);
        }
        // Stable sort: equal numbers keep the source order.
        Collections.sort(kept, Comparator.comparingInt(
                p -> p.sequenceNumber != null ? p.sequenceNumber : Integer.MAX_VALUE));
        return kept;
    }

    /**
     * The portion's own words, without the grams: "1 cup, chopped", "1 cup",
     * "1 fl oz"; null when it has none worth showing ("Quantity not specified"
     * and a bare code say nothing to the user).
     */
    @Nullable
    static String label(@NonNull Portion p) {
        String unit = trimmed(p.unitName);
        String modifier = isWord(p.modifier) ? trimmed(p.modifier) : null;
        String number = p.amount != null && p.amount > 0 ? format(p.amount) + " " : "";

        // Foundation: a real measure unit, the modifier as a detail.
        if (unit != null && !UNDETERMINED.equalsIgnoreCase(unit)) {
            return number + unit + (modifier != null ? ", " + modifier : "");
        }
        // Survey: the description is the label.
        String description = trimmed(p.portionDescription);
        if (description != null && !NO_DESCRIPTION.equalsIgnoreCase(description)) {
            return QUANTITY_NOT_SPECIFIED.equalsIgnoreCase(description) ? null : description;
        }
        // SR Legacy: the modifier is the unit.
        return modifier != null ? number + modifier : null;
    }

    /** "1 cup, chopped (76 g)", or "244 g" when the portion has no words of its own. */
    @NonNull
    static ServingSize toServingSize(@NonNull Portion p) {
        double grams = p.gramWeight != null ? p.gramWeight : 0.0;
        ServingSize serving = new ServingSize(grams, Unit.G);
        String label = label(p);
        String weight = format(grams) + " g";
        // Quantity and unit are both set, so setDescription() keeps them.
        serving.setDescription(label != null ? label + " (" + weight + ")" : weight);
        return serving;
    }

    // ========== HELPERS ==========

    /** 1.0 -> "1", 30.5 -> "30.5", 0.25 -> "0.25" ('.' always: USDA text is English). */
    @NonNull
    static String format(double value) {
        if (value == Math.rint(value) && Math.abs(value) < 1e15) {
            return String.valueOf((long) value);
        }
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    /** Text with letters (not empty, not a numeric code like "10205"). */
    private static boolean isWord(@Nullable String text) {
        String t = trimmed(text);
        if (t == null) return false;
        for (int i = 0; i < t.length(); i++) {
            if (Character.isLetter(t.charAt(i))) return true;
        }
        return false;
    }

    @Nullable
    private static String trimmed(@Nullable String text) {
        if (text == null) return null;
        String t = text.trim();
        return t.isEmpty() ? null : t;
    }
}
