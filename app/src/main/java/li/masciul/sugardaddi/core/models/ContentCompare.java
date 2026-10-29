package li.masciul.sugardaddi.core.models;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Field-comparison helpers for change detection (contentEquals on the models).
 *
 * Compares source-provided *content*, tolerant of the noise that shouldn't count
 * as a change: float jitter from mapper recomputation, null-vs-empty strings, and
 * list ordering.
 */
public final class ContentCompare {

    /** Relative tolerance for nutrient/quantity values (0.1%). */
    private static final double REL_EPSILON = 0.001;

    /** Absolute floor so near-zero values don't blow up the relative check. */
    private static final double ABS_FLOOR = 1e-6;

    private ContentCompare() {}

    /** Null-aware, relative-epsilon numeric compare (both null = equal). */
    public static boolean numbersEqual(Double a, Double b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        double diff = Math.abs(a - b);
        if (diff <= ABS_FLOOR) return true;
        return diff <= REL_EPSILON * Math.max(Math.abs(a), Math.abs(b));
    }

    /** Treats null and blank as equal, trims, otherwise exact. */
    public static boolean stringsEqual(String a, String b) {
        String x = (a == null) ? "" : a.trim();
        String y = (b == null) ? "" : b.trim();
        return x.equals(y);
    }

    /** Null-safe equals for enums / value objects. */
    public static boolean objectsEqual(Object a, Object b) {
        return (a == null) ? (b == null) : a.equals(b);
    }

    /** Order-insensitive list compare (null = empty). */
    public static boolean listsEqual(List<String> a, List<String> b) {
        Set<String> sa = (a == null) ? Collections.emptySet() : new HashSet<>(a);
        Set<String> sb = (b == null) ? Collections.emptySet() : new HashSet<>(b);
        return sa.equals(sb);
    }

    /** Null-aware nutrition compare (both null = equal). */
    public static boolean nutritionEqual(Nutrition a, Nutrition b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.contentEquals(b);
    }

    /** Null-aware serving-size compare (both null = equal). */
    public static boolean servingEqual(ServingSize a, ServingSize b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.contentEquals(b);
    }

    /**
     * Order-insensitive serving-list compare (null = empty). Each serving of
     * one list must match a distinct serving of the other. Order is ignored on
     * purpose: a source reshuffling the same servings is not a content change,
     * and must not offer the user a refresh.
     */
    public static boolean servingListsEqual(List<ServingSize> a, List<ServingSize> b) {
        int na = (a == null) ? 0 : a.size();
        int nb = (b == null) ? 0 : b.size();
        if (na != nb) return false;
        if (na == 0) return true;

        List<ServingSize> unmatched = new java.util.ArrayList<>(b);
        for (ServingSize sa : a) {
            boolean found = false;
            for (int i = 0; i < unmatched.size(); i++) {
                if (servingEqual(sa, unmatched.get(i))) {
                    unmatched.remove(i);
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }
        return true;
    }
}