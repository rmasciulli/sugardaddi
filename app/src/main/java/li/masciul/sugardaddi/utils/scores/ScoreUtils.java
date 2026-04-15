package li.masciul.sugardaddi.utils.scores;

import li.masciul.sugardaddi.R;

/**
 * ScoreUtils - Utility class for Nutri-Score, Green-Score, and Nova Group sticker selection.
 *
 * This class handles drawable resource selection for all three scoring systems used in the app.
 * It is separate from category utilities as scores are independent of taxonomy.
 *
 * Handles:
 * - Nutri-Score drawable selection (with and without TPL versions)
 * - Green-Score drawable selection (leaf, horizontal, vertical)
 * - Nova Group drawable selection (groups 1–4)
 * - Unknown/neutral grade handling
 * - Proper fallback for missing data
 *
 * NOVA GROUP BACKGROUND:
 * Nova is a food classification system developed by researchers at the University of São Paulo.
 * It classifies foods into 4 groups based on the degree of industrial processing:
 *   Group 1: Unprocessed or minimally processed foods (vegetables, meat, eggs, etc.)
 *   Group 2: Processed culinary ingredients (oils, butter, flour, salt, sugar)
 *   Group 3: Processed foods (canned fish, cheese, cured ham, etc.)
 *   Group 4: Ultra-processed food and drink products (soft drinks, packaged snacks, etc.)
 *
 * Drawable naming convention in this project: novagroup_1, novagroup_2, novagroup_3, novagroup_4
 * (confirmed from ProjectTree.txt — no underscore before the digit)
 */
public class ScoreUtils {

    // ========================
    // NUTRI-SCORE STICKERS (TPL - without text for dynamic overlay)
    // ========================

    /**
     * Get Nutri-Score horizontal TPL sticker (WITHOUT text - for dynamic overlay)
     *
     * @param grade Nutri-Score grade (A-E, null, or empty for unknown)
     * @return Drawable resource ID
     */
    public static int getNutriScoreTplHorizontal(String grade) {
        if (grade == null || grade.trim().isEmpty()) {
            return R.drawable.nutriscore_tpl_horizontal_unknown;
        }

        switch (grade.toUpperCase().trim()) {
            case "A":   return R.drawable.nutriscore_tpl_horizontal_a;
            case "B":   return R.drawable.nutriscore_tpl_horizontal_b;
            case "C":   return R.drawable.nutriscore_tpl_horizontal_c;
            case "D":   return R.drawable.nutriscore_tpl_horizontal_d;
            case "E":   return R.drawable.nutriscore_tpl_horizontal_e;
            case "NEUTRAL": return R.drawable.nutriscore_tpl_horizontal_neutral;
            default:    return R.drawable.nutriscore_horizontal_unknown;
        }
    }

    /**
     * Get Nutri-Score vertical TPL sticker (WITHOUT text - for dynamic overlay)
     *
     * @param grade Nutri-Score grade (A-E, null, or empty for unknown)
     * @return Drawable resource ID
     */
    public static int getNutriScoreTplVertical(String grade) {
        if (grade == null || grade.trim().isEmpty()) {
            return R.drawable.nutriscore_tpl_vertical_neutral;
        }

        switch (grade.toUpperCase().trim()) {
            case "A":   return R.drawable.nutriscore_tpl_vertical_a;
            case "B":   return R.drawable.nutriscore_tpl_vertical_b;
            case "C":   return R.drawable.nutriscore_tpl_vertical_c;
            case "D":   return R.drawable.nutriscore_tpl_vertical_d;
            case "E":   return R.drawable.nutriscore_tpl_vertical_e;
            case "NEUTRAL": return R.drawable.nutriscore_tpl_vertical_neutral;
            default:    return R.drawable.nutriscore_vertical_unknown;
        }
    }

    /**
     * Get Nutri-Score horizontal (non-TPL version, includes static text baked into drawable)
     *
     * @param grade Nutri-Score grade (A-E, null, or empty for unknown)
     * @return Drawable resource ID
     */
    public static int getNutriScoreHorizontal(String grade) {
        if (grade == null || grade.trim().isEmpty()) {
            return R.drawable.nutriscore_horizontal_unknown;
        }

        switch (grade.toUpperCase().trim()) {
            case "A":   return R.drawable.nutriscore_horizontal_a;
            case "B":   return R.drawable.nutriscore_horizontal_b;
            case "C":   return R.drawable.nutriscore_horizontal_c;
            case "D":   return R.drawable.nutriscore_horizontal_d;
            case "E":   return R.drawable.nutriscore_horizontal_e;
            case "NEUTRAL": return R.drawable.nutriscore_horizontal_neutral;
            default:    return R.drawable.nutriscore_horizontal_unknown;
        }
    }

    /**
     * Get Nutri-Score vertical (non-TPL version)
     *
     * @param grade Nutri-Score grade (A-E, null, or empty for unknown)
     * @return Drawable resource ID
     */
    public static int getNutriScoreVertical(String grade) {
        if (grade == null || grade.trim().isEmpty()) {
            return R.drawable.nutriscore_vertical_unknown;
        }

        switch (grade.toUpperCase().trim()) {
            case "A":   return R.drawable.nutriscore_vertical_a;
            case "B":   return R.drawable.nutriscore_vertical_b;
            case "C":   return R.drawable.nutriscore_vertical_c;
            case "D":   return R.drawable.nutriscore_vertical_d;
            case "E":   return R.drawable.nutriscore_vertical_e;
            case "NEUTRAL": return R.drawable.nutriscore_vertical_neutral;
            default:    return R.drawable.nutriscore_vertical_unknown;
        }
    }

    // ========================
    // GREEN-SCORE STICKERS
    // ========================

    /**
     * Get Green-Score leaf icon (compact version, for search results and score rows)
     *
     * @param grade Green-Score grade (A+ to F, null for unknown)
     * @return Drawable resource ID
     */
    public static int getGreenScoreLeaf(String grade) {
        if (grade == null || grade.trim().isEmpty()) {
            return R.drawable.greenscore_leaf_unknown;
        }

        switch (grade.toUpperCase().trim()) {
            case "A+":  return R.drawable.greenscore_leaf_a_plus;
            case "A":   return R.drawable.greenscore_leaf_a;
            case "B":   return R.drawable.greenscore_leaf_b;
            case "C":   return R.drawable.greenscore_leaf_c;
            case "D":   return R.drawable.greenscore_leaf_d;
            case "E":   return R.drawable.greenscore_leaf_e;
            case "F":   return R.drawable.greenscore_leaf_f;
            default:    return R.drawable.greenscore_leaf_unknown;
        }
    }

    /**
     * Get Green-Score horizontal sticker (full sticker with text baked in)
     *
     * @param grade Green-Score grade (A+ to F, null for unknown)
     * @return Drawable resource ID
     */
    public static int getGreenScoreHorizontal(String grade) {
        if (grade == null || grade.trim().isEmpty()) {
            return R.drawable.greenscore_horizontal_unknown;
        }

        switch (grade.toUpperCase().trim()) {
            case "A+":  return R.drawable.greenscore_horizontal_a_plus;
            case "A":   return R.drawable.greenscore_horizontal_a;
            case "B":   return R.drawable.greenscore_horizontal_b;
            case "C":   return R.drawable.greenscore_horizontal_c;
            case "D":   return R.drawable.greenscore_horizontal_d;
            case "E":   return R.drawable.greenscore_horizontal_e;
            case "F":   return R.drawable.greenscore_horizontal_f;
            default:    return R.drawable.greenscore_horizontal_unknown;
        }
    }

    /**
     * Get Green-Score vertical sticker
     *
     * @param grade Green-Score grade (A+ to F, null for unknown)
     * @return Drawable resource ID
     */
    public static int getGreenScoreVertical(String grade) {
        if (grade == null || grade.trim().isEmpty()) {
            return R.drawable.greenscore_vertical_unknown;
        }

        switch (grade.toUpperCase().trim()) {
            case "A+":  return R.drawable.greenscore_vertical_a_plus;
            case "A":   return R.drawable.greenscore_vertical_a;
            case "B":   return R.drawable.greenscore_vertical_b;
            case "C":   return R.drawable.greenscore_vertical_c;
            case "D":   return R.drawable.greenscore_vertical_d;
            case "E":   return R.drawable.greenscore_vertical_e;
            case "F":   return R.drawable.greenscore_vertical_f;
            default:    return R.drawable.greenscore_vertical_unknown;
        }
    }

    // ========================
    // NOVA GROUP STICKERS
    // ========================

    /**
     * Get Nova Group drawable.
     *
     * Nova group is stored as a String (from OpenFoodFacts API): "1", "2", "3", or "4".
     * Some sources may return it as an integer disguised as a string — we handle that.
     * Null or empty → returns the group 4 badge (ultra-processed) as a safe fallback
     * because showing no Nova is more dangerous than showing a conservative estimate.
     *
     * Actually: null → no drawable at all. The caller decides whether to show or hide.
     * This method returns 0 (invalid resource) when no data is available,
     * which callers should treat as "hide the Nova badge entirely."
     *
     * Drawable naming: novagroup_1, novagroup_2, novagroup_3, novagroup_4
     * (confirmed from ProjectTree.txt)
     *
     * @param group Nova group string ("1", "2", "3", "4") or null
     * @return Drawable resource ID, or 0 if group is null/unknown (hide the badge)
     */
    public static int getNovaGroupDrawable(String group) {
        if (group == null || group.trim().isEmpty()) {
            return 0; // Caller should hide the Nova badge entirely
        }

        // Strip any decimal part in case we receive "1.0", "4.0", etc. from JSON
        String cleaned = group.trim().split("\\.")[0];

        switch (cleaned) {
            case "1":   return R.drawable.novagroup_1;
            case "2":   return R.drawable.novagroup_2;
            case "3":   return R.drawable.novagroup_3;
            case "4":   return R.drawable.novagroup_4;
            default:    return 0; // Unknown group → hide the badge
        }
    }

    /**
     * Check whether a Nova group value is valid and has a drawable.
     *
     * @param group Nova group string
     * @return true if group is "1", "2", "3", or "4"
     */
    public static boolean isValidNovaGroup(String group) {
        return getNovaGroupDrawable(group) != 0;
    }

    // ========================
    // VALIDATION
    // ========================

    /**
     * Check if a grade is valid for Nutri-Score (A–E, excludes NEUTRAL).
     * NEUTRAL is technically valid but not user-facing; use this for display guards.
     *
     * @param grade Nutri-Score grade string
     * @return true if A, B, C, D, or E
     */
    public static boolean isValidNutriScoreGrade(String grade) {
        if (grade == null || grade.trim().isEmpty()) return false;
        String g = grade.toUpperCase().trim();
        return g.equals("A") || g.equals("B") || g.equals("C") ||
                g.equals("D") || g.equals("E");
    }

    /**
     * Check if a grade is valid for Green-Score (A+ through F).
     *
     * @param grade Green-Score grade string
     * @return true if A+, A, B, C, D, E, or F
     */
    public static boolean isValidGreenScoreGrade(String grade) {
        if (grade == null || grade.trim().isEmpty()) return false;
        String g = grade.toUpperCase().trim();
        return g.equals("A+") || g.equals("A") || g.equals("B") ||
                g.equals("C") || g.equals("D") || g.equals("E") || g.equals("F");
    }
}