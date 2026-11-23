package li.masciul.sugardaddi.data.sources.openfoodfacts.mappers;

import java.text.Normalizer;
import java.util.Locale;

/**
 * OffProductNames - which name an Open Food Facts product gets, and how it is shown.
 *
 * WHY: OFF records often have an empty name in their main language. Belgian beers
 * entered in Dutch are typical: product_name and product_name_nl are "" while the
 * only name is product_name_fr ("Blonde", "La Chouffe"). Reading the requested
 * language and the main language only gave "Unknown Product" on such records.
 *
 * Only the app's languages (EN, FR) and the record's main name are read. Other
 * language fields are not trusted blindly: on 5410769100098 product_name_de holds
 * Hebrew text with the volume in it.
 *
 * Pure Java on purpose (no Android types) so it can be unit tested.
 */
public final class OffProductNames {

    private OffProductNames() { /* no instances */ }

    /**
     * Name to store for a product, or null when the record has none at all
     * (the screen then shows R.string.unknown_product).
     *
     * Order:
     *   1. name in the requested language
     *   2. product_name (the record's main language)
     *   3. name in the other app language
     *   4. generic name, same order (requested, main, other)
     *   5. first brand
     *
     * @param language requested language ("en" or "fr")
     */
    public static String pick(String language,
                              String nameEn, String nameFr, String name,
                              String genericEn, String genericFr, String generic,
                              String brands) {
        boolean fr = "fr".equals(language);
        String requestedName = fr ? nameFr : nameEn;
        String otherName = fr ? nameEn : nameFr;
        String requestedGeneric = fr ? genericFr : genericEn;
        String otherGeneric = fr ? genericEn : genericFr;

        return firstNonBlank(requestedName, name, otherName,
                requestedGeneric, generic, otherGeneric,
                firstBrand(brands));
    }

    /**
     * First brand of OFF's comma-separated "brands" field ("Leffe, AB InBev" gives
     * "Leffe"), or null when there is none. "unknown" counts as none, as in the
     * detail and search screens.
     */
    public static String firstBrand(String brands) {
        if (brands == null) return null;
        String first = brands.split(",")[0].trim();
        if (first.isEmpty() || first.equalsIgnoreCase("unknown")) return null;
        return first;
    }

    /**
     * Title shown for a product: the brand in front of the name, unless the name
     * already contains it ("Blonde" + "Leffe" gives "Leffe Blonde"; "La Chouffe" +
     * "Chouffe" stays "La Chouffe"). Accents and case are ignored in that check.
     * Display only: the stored name is not changed.
     *
     * @return the title, or null when there is neither a name nor a brand
     */
    public static String withBrand(String name, String brands) {
        String brand = firstBrand(brands);
        boolean hasName = name != null && !name.trim().isEmpty();
        if (!hasName) return brand;
        String trimmed = name.trim();
        if (brand == null || normalize(trimmed).contains(normalize(brand))) {
            return trimmed;
        }
        return brand + " " + trimmed;
    }

    /**
     * True when the brand line under the title would only repeat it (the title is
     * the brand itself, as when the record has no name at all).
     */
    public static boolean titleIsBrand(String title, String brands) {
        String brand = firstBrand(brands);
        return brand != null && title != null
                && normalize(title.trim()).equals(normalize(brand));
    }

    // ========== HELPERS ==========

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) return value.trim();
        }
        return null;
    }

    /** Lower case, accents removed: "Château" and "chateau" compare equal. */
    private static String normalize(String text) {
        String decomposed = Normalizer.normalize(text, Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
}
