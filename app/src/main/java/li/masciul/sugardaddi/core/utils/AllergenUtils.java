package li.masciul.sugardaddi.core.utils;

import android.content.Context;
import li.masciul.sugardaddi.R;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for allergen bit manipulation
 * EU Regulation 1169/2011 - 14 mandatory allergens
 *
 * Uses bit flags for efficient storage and queries
 * Each allergen is represented by a single bit in an integer
 */
public class AllergenUtils {

    // ========== ALLERGEN BIT FLAGS ==========
    // Each allergen gets one bit position

    public static final int GLUTEN = 1 << 0;        // Bit 0: 0x0001
    public static final int CRUSTACEANS = 1 << 1;   // Bit 1: 0x0002
    public static final int EGGS = 1 << 2;          // Bit 2: 0x0004
    public static final int FISH = 1 << 3;          // Bit 3: 0x0008
    public static final int PEANUTS = 1 << 4;       // Bit 4: 0x0010
    public static final int SOY = 1 << 5;           // Bit 5: 0x0020
    public static final int MILK = 1 << 6;          // Bit 6: 0x0040
    public static final int NUTS = 1 << 7;          // Bit 7: 0x0080
    public static final int CELERY = 1 << 8;        // Bit 8: 0x0100
    public static final int MUSTARD = 1 << 9;       // Bit 9: 0x0200
    public static final int SESAME = 1 << 10;       // Bit 10: 0x0400
    public static final int SULFITES = 1 << 11;     // Bit 11: 0x0800
    public static final int LUPIN = 1 << 12;        // Bit 12: 0x1000
    public static final int MOLLUSCS = 1 << 13;     // Bit 13: 0x2000

    // ========== COMMON COMBINATIONS ==========

    public static final int NONE = 0;               // No allergens
    public static final int ALL = 0x3FFF;           // All 14 allergens

    // Common dietary restrictions
    public static final int SEAFOOD = FISH | CRUSTACEANS | MOLLUSCS;
    public static final int DAIRY = MILK;           // Lactose is included in milk
    public static final int TREE_NUTS_AND_PEANUTS = NUTS | PEANUTS;
    public static final int GLUTEN_CONTAINING = GLUTEN;

    // Vegan restrictions (all animal products)
    public static final int VEGAN_RESTRICTED = MILK | EGGS | FISH | CRUSTACEANS | MOLLUSCS;

    // Vegetarian restrictions
    public static final int VEGETARIAN_RESTRICTED = FISH | CRUSTACEANS | MOLLUSCS;

    // ========== ALLERGEN NAMES AND RESOURCES ==========

    private static final int[] ALLERGEN_STRING_IDS = {
            R.string.allergen_gluten,               // Bit 0
            R.string.allergen_crustaceans,          // Bit 1
            R.string.allergen_eggs,                 // Bit 2
            R.string.allergen_fish,                 // Bit 3
            R.string.allergen_peanuts,              // Bit 4
            R.string.allergen_soy,                  // Bit 5
            R.string.allergen_milk,                 // Bit 6
            R.string.allergen_nuts,                 // Bit 7
            R.string.allergen_celery,               // Bit 8
            R.string.allergen_mustard,              // Bit 9
            R.string.allergen_sesame,               // Bit 10
            R.string.allergen_sulfites,             // Bit 11
            R.string.allergen_lupin,                // Bit 12
            R.string.allergen_molluscs              // Bit 13
    };

    private static final String[] ALLERGEN_CODES = {
            "gluten", "crustaceans", "eggs", "fish", "peanuts", "soy",
            "milk", "nuts", "celery", "mustard", "sesame", "sulfites",
            "lupin", "molluscs"
    };

    // ========== BIT MANIPULATION METHODS ==========

    /**
     * Check if a specific allergen is present
     */
    public static boolean hasAllergen(int flags, int allergen) {
        return (flags & allergen) != 0;
    }

    /**
     * Set or clear a specific allergen
     */
    public static int setAllergen(int flags, int allergen, boolean present) {
        if (present) {
            return flags | allergen;  // Set bit
        } else {
            return flags & ~allergen; // Clear bit
        }
    }

    /**
     * Check if product is safe for user restrictions
     * @param productAllergens Allergens in the product
     * @param userRestrictions Allergens the user must avoid
     * @return true if product has none of the restricted allergens
     */
    public static boolean isSafeFor(int productAllergens, int userRestrictions) {
        return (productAllergens & userRestrictions) == 0;
    }

    /**
     * Combine allergens from multiple sources
     */
    public static int combineAllergens(int... sources) {
        int result = 0;
        for (int source : sources) {
            result |= source;
        }
        return result;
    }

    /**
     * Remove allergens from a set
     */
    public static int removeAllergens(int flags, int toRemove) {
        return flags & ~toRemove;
    }

    /**
     * Count number of allergens present
     */
    public static int countAllergens(int flags) {
        return Integer.bitCount(flags);
    }

    // ========== STRING CONVERSION METHODS ==========

    /**
     * Get localized allergen names
     * @param context Android context for string resources
     * @param flags Allergen bit flags
     * @return Comma-separated list of allergen names
     */
    public static String getAllergenNames(Context context, int flags) {
        if (flags == 0) {
            return context.getString(R.string.no_allergens);
        }

        List<String> names = new ArrayList<>();
        for (int i = 0; i < 14; i++) {
            if ((flags & (1 << i)) != 0) {
                names.add(context.getString(ALLERGEN_STRING_IDS[i]));
            }
        }

        return String.join(", ", names);
    }

    /**
     * Get allergen codes (for API/database use)
     */
    public static List<String> getAllergenCodes(int flags) {
        List<String> codes = new ArrayList<>();
        for (int i = 0; i < 14; i++) {
            if ((flags & (1 << i)) != 0) {
                codes.add(ALLERGEN_CODES[i]);
            }
        }
        return codes;
    }

    /**
     * Parse allergen flags from OpenFoodFacts tags
     */
    public static int parseFromOFFTags(List<String> allergenTags) {
        if (allergenTags == null || allergenTags.isEmpty()) {
            return 0;
        }

        int flags = 0;
        for (String tag : allergenTags) {
            String lower = tag.toLowerCase();

            // Check each allergen
            if (lower.contains("gluten") || lower.contains("wheat") ||
                    lower.contains("barley") || lower.contains("rye")) {
                flags |= GLUTEN;
            }
            if (lower.contains("crustacean") || lower.contains("shrimp") ||
                    lower.contains("lobster") || lower.contains("crab")) {
                flags |= CRUSTACEANS;
            }
            if (lower.contains("egg")) {
                flags |= EGGS;
            }
            if (lower.contains("fish") && !lower.contains("shellfish")) {
                flags |= FISH;
            }
            if (lower.contains("peanut") || lower.contains("groundnut")) {
                flags |= PEANUTS;
            }
            if (lower.contains("soy") || lower.contains("soja") || lower.contains("soya")) {
                flags |= SOY;
            }
            if (lower.contains("milk") || lower.contains("lactose") ||
                    lower.contains("dairy") || lower.contains("cheese")) {
                flags |= MILK;
            }
            if (lower.contains("nuts") || lower.contains("almond") ||
                    lower.contains("hazelnut") || lower.contains("walnut") ||
                    lower.contains("cashew") || lower.contains("pistachio")) {
                flags |= NUTS;
            }
            if (lower.contains("celery")) {
                flags |= CELERY;
            }
            if (lower.contains("mustard")) {
                flags |= MUSTARD;
            }
            if (lower.contains("sesame")) {
                flags |= SESAME;
            }
            if (lower.contains("sulphite") || lower.contains("sulfite") ||
                    lower.contains("sulphur") || lower.contains("sulfur")) {
                flags |= SULFITES;
            }
            if (lower.contains("lupin") || lower.contains("lupine")) {
                flags |= LUPIN;
            }
            if (lower.contains("mollusc") || lower.contains("mollusk") ||
                    lower.contains("oyster") || lower.contains("mussel") ||
                    lower.contains("scallop") || lower.contains("squid")) {
                flags |= MOLLUSCS;
            }
        }

        return flags;
    }
}