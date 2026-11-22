package li.masciul.sugardaddi.data.sources.usda.mappers;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
 * - Density (7B, see density() and isLiquid()): only for liquid-like foods, from
 *   the gram weight of their volume portions. A cup of a solid is a bulk density
 *   (it depends on how the food is cut), so solids get none.
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

    // ========== DENSITY (7B) ==========
    //
    // Decisions (Romain, 2026-11-21/22): density for liquid-like foods only,
    // chosen by USDA's own categories (isLiquid()); portions whose words show a
    // cut or packed solid are ignored; with two or more volume portions they
    // must agree within 5%, a single one is accepted; granulated sugar, butter
    // and ice cream are kept (real, if bulk or aerated, densities).

    /**
     * Milliliters per unit, US CUSTOMARY measures: USDA weighs its portions
     * with them. NOT Unit.CUP (240 ml, the US nutrition-label cup): with 240 ml,
     * USDA's whole milk (1 cup = 244 g) would give 1.017 g/ml instead of the
     * real 1.031, and its "1 fl oz = 30.5 g" (= 244 / 8) would disagree with
     * its own cup. Keys are lower case; longer names are tried first.
     */
    private static final Map<String, Double> VOLUME_ML = new LinkedHashMap<>();
    static {
        putVolume(29.5735, "fluid ounces", "fluid ounce", "fl. oz", "fl oz", "floz");
        putVolume(14.7868, "tablespoons", "tablespoon", "tbsp", "tbs");
        putVolume(4.92892, "teaspoons", "teaspoon", "tsp");
        putVolume(236.588, "cups", "cup");
        putVolume(473.176, "pints", "pint", "pt");
        putVolume(946.353, "quarts", "quart", "qt");
        putVolume(3785.41, "gallons", "gallon", "gal");
        putVolume(1.0, "milliliters", "millilitres", "milliliter", "millilitre", "ml");
        putVolume(1000.0, "liters", "litres", "liter", "litre", "l");
    }

    /** Words showing a cut, packed or otherwise non-liquid solid in a portion. */
    private static final Set<String> SOLID_WORDS = new HashSet<>(Arrays.asList(
            "chopped", "sliced", "slices", "diced", "shredded", "grated", "cubed",
            "crumbled", "mashed", "pieces", "packed", "halves", "melted", "whipped",
            "whole", "sections", "wedges"));

    /**
     * Liquid-like categories, matched as a whole (case ignored):
     * - USDA food groups (SR Legacy, Foundation);
     * - WWEIA subcategories (Survey / FNDDS), from "What We Eat in America
     *   Food Categories 2021-2023" (USDA ARS, November 2024).
     */
    private static final Set<String> LIQUID_CATEGORIES = new HashSet<>(Arrays.asList(
            // USDA food groups
            "dairy and egg products", "beverages", "fats and oils",
            "soups, sauces, and gravies",
            // WWEIA - milk and dairy
            "milk, whole", "milk, reduced fat", "milk, lowfat", "milk, nonfat",
            "flavored milk, whole", "flavored milk, reduced fat", "flavored milk, lowfat",
            "flavored milk, nonfat", "milk shakes and other dairy drinks",
            "yogurt, regular", "yogurt, greek", "plant-based milk", "plant-based yogurt",
            // WWEIA - soups
            "soups, broth-based", "soups, cream-based", "ramen and asian broth-based soups",
            // WWEIA - desserts
            "ice cream and frozen dairy desserts", "pudding", "gelatins, ices, sorbets",
            // WWEIA - beverages (alcohol and water included)
            "citrus juice", "apple juice", "other fruit juice", "vegetable juice",
            "diet soft drinks", "diet sport and energy drinks", "other diet drinks",
            "soft drinks", "fruit drinks", "sport and energy drinks", "nutritional beverages",
            "smoothies and grain drinks", "coffee", "tea", "beer", "wine",
            "liquor and cocktails", "tap water", "bottled water",
            "flavored or carbonated water", "enhanced water",
            // WWEIA - fats and oils
            "butter and animal fats", "margarine", "cream cheese, sour cream, whipped cream",
            "cream and cream substitutes", "mayonnaise", "salad dressings and vegetable oils",
            // WWEIA - condiments and sauces (not olives and pickles)
            "tomato-based condiments", "soy-based condiments", "mustard and other condiments",
            "pasta sauces, tomato-based", "dips, gravies, other sauces",
            // WWEIA - sugars
            "sugars and honey", "jams, syrups, toppings",
            // WWEIA - baby beverages, formulas
            "baby juice", "baby water", "formula, ready-to-feed",
            "formula, prepared from powder", "human milk"));

    /**
     * Mixed USDA food groups: liquid only when the food's NAME has one of these
     * words (fruit juices next to whole fruit, syrups next to candies...).
     */
    private static final Map<String, List<String>> LIQUID_BY_NAME = new HashMap<>();
    static {
        LIQUID_BY_NAME.put("fruits and fruit juices", Arrays.asList("juice", "nectar"));
        LIQUID_BY_NAME.put("vegetables and vegetable products", Arrays.asList("juice", "nectar"));
        LIQUID_BY_NAME.put("baby foods", Arrays.asList("juice", "formula", "milk"));
        LIQUID_BY_NAME.put("sweets", Arrays.asList("syrup", "honey", "molasses", "sugar",
                "jam", "jelly", "preserves", "topping"));
    }

    /** Volume portions must agree within 5% (same tolerance as FatSecret's density). */
    private static final double DENSITY_MAX_SPREAD = 0.05;
    /** Plausible foods: whipped toppings (~0.2) to honey / syrups (~1.4), as FatSecret. */
    private static final double DENSITY_MIN = 0.2;
    private static final double DENSITY_MAX = 2.0;

    /** "1", "1.5", "1/2", "1 1/2" at the start of a text, then the rest. */
    private static final Pattern LEADING_QUANTITY = Pattern.compile(
            "^\\s*(\\d+\\s+\\d+/\\d+|\\d+/\\d+|\\d+(?:\\.\\d+)?)\\s*(.*)$");
    /** A quantity and its unit in brackets: "1 can (12 fl oz)". */
    private static final Pattern BRACKET_VOLUME = Pattern.compile(
            "\\(\\s*(\\d+\\s+\\d+/\\d+|\\d+/\\d+|\\d+(?:\\.\\d+)?)\\s*([^)]*)\\)");

    /**
     * Whether a food may get a density: its category is liquid-like, or it is
     * a mixed food group and its name says it is a liquid (see the two lists).
     *
     * @param category    the raw USDA category (food group, or WWEIA subcategory)
     * @param description the food's raw name
     */
    public static boolean isLiquid(@Nullable String category, @Nullable String description) {
        String c = trimmed(category);
        if (c == null) return false;
        c = c.toLowerCase(Locale.ROOT);
        if (LIQUID_CATEGORIES.contains(c)) return true;
        List<String> words = LIQUID_BY_NAME.get(c);
        String name = trimmed(description);
        if (words == null || name == null) return false;
        name = name.toLowerCase(Locale.ROOT);
        for (String w : words) {
            if (name.contains(w)) return true;
        }
        return false;
    }

    /**
     * Grams per ml from the food's volume portions, or null.
     *
     * Each usable portion whose words give a volume ("1 cup", "1 fl oz",
     * "1 can (12 fl oz)") and no solid word gives one value: gramWeight / ml.
     * With two or more, they must agree within 5%; the median is kept, if
     * plausible (0.2 to 2.0). Call it only for foods isLiquid() accepts.
     *
     * Whole milk (SR Legacy 171265): 244 g / 236.6 ml, 30.5 g / 29.57 ml,
     * 976 g / 946.4 ml and 15 g / 14.79 ml -> 1.031 g/ml.
     */
    @Nullable
    public static Double density(@Nullable List<Portion> portions) {
        List<Double> ratios = new ArrayList<>();
        for (Portion p : usable(portions)) {
            String words = label(p);
            if (words == null || hasSolidWord(words)) continue;
            Double ml = volumeMl(words);
            if (ml == null || !(ml > 0)) continue;
            ratios.add(p.gramWeight / ml);
        }
        if (ratios.isEmpty()) return null;

        Collections.sort(ratios);
        double lowest = ratios.get(0);
        double highest = ratios.get(ratios.size() - 1);
        if (highest - lowest > DENSITY_MAX_SPREAD * lowest) return null;

        int mid = ratios.size() / 2;
        double median = ratios.size() % 2 == 1
                ? ratios.get(mid)
                : (ratios.get(mid - 1) + ratios.get(mid)) / 2.0;
        return median >= DENSITY_MIN && median <= DENSITY_MAX ? median : null;
    }

    /**
     * The volume, in ml, a portion's words describe: the leading quantity and
     * unit ("1 1/2 cups"), else the bracketed one when the leading unit is not a
     * volume ("1 can (12 fl oz)"); null when neither is a volume. A text starting
     * with a unit and no number counts as 1 of it.
     */
    @Nullable
    static Double volumeMl(@NonNull String words) {
        String text = words.toLowerCase(Locale.ROOT).trim();
        Matcher leading = LEADING_QUANTITY.matcher(text);
        Double quantity = leading.matches() ? parseQuantity(leading.group(1)) : Double.valueOf(1.0);
        String rest = leading.matches() ? leading.group(2) : text;
        Double perUnit = unitMl(rest);
        if (quantity != null && perUnit != null) return quantity * perUnit;

        Matcher bracket = BRACKET_VOLUME.matcher(text);
        if (bracket.find()) {
            Double inner = parseQuantity(bracket.group(1));
            Double innerUnit = unitMl(bracket.group(2));
            if (inner != null && innerUnit != null) return inner * innerUnit;
        }
        return null;
    }

    /** ml per unit when the text STARTS with a known volume unit (as a whole word). */
    @Nullable
    private static Double unitMl(@NonNull String text) {
        String t = text.trim();
        for (Map.Entry<String, Double> e : VOLUME_ML.entrySet()) {
            String unit = e.getKey();
            if (t.startsWith(unit)
                    && (t.length() == unit.length() || !Character.isLetter(t.charAt(unit.length())))) {
                return e.getValue();
            }
        }
        return null;
    }

    /** "2" -> 2, "1.5" -> 1.5, "1/2" -> 0.5, "1 1/2" -> 1.5; null when unreadable. */
    @Nullable
    private static Double parseQuantity(@NonNull String text) {
        try {
            String t = text.trim();
            String[] parts = t.split("\\s+");
            double total = 0;
            for (String part : parts) {
                int slash = part.indexOf('/');
                if (slash > 0) {
                    double den = Double.parseDouble(part.substring(slash + 1));
                    if (den == 0) return null;
                    total += Double.parseDouble(part.substring(0, slash)) / den;
                } else {
                    total += Double.parseDouble(part);
                }
            }
            return total > 0 ? total : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** True when one of the words shows a cut or packed solid (whole words only). */
    private static boolean hasSolidWord(@NonNull String words) {
        for (String w : words.toLowerCase(Locale.ROOT).split("[^a-z]+")) {
            if (SOLID_WORDS.contains(w)) return true;
        }
        return false;
    }

    private static void putVolume(double ml, String... names) {
        for (String n : names) VOLUME_ML.put(n, ml);
    }

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
