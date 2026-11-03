package li.masciul.sugardaddi.core.models;

import li.masciul.sugardaddi.core.enums.Unit;

/**
 * PortionQuantity - what was logged for one meal entry, frozen at logging time.
 *
 * A meal entry is a RECORD: it must never shift afterwards, even if the item's
 * typical serving size changes later (source update, user edit). So the three
 * numbers the user saw are all stored, not recomputed:
 *
 *   inputMode        SERVINGS or QUANTITY - which one the user actually entered
 *                    (slide 1 "Servings" or slide 2 "Custom quantity")
 *   servingCount     e.g. 3.5 - entered, or derived as total / servingQuantity
 *   servingQuantity  e.g. 30  - size of ONE serving at logging time, in unit
 *   totalQuantity    e.g. 105 - entered, or derived as count x servingQuantity
 *   unit             G or ML  - unit of both quantities
 *
 * totalQuantity is what drives nutrition (it is in the item's basis unit: grams
 * for per-100g nutrition, ml for per-100ml). It is null only when it cannot be
 * known - servings of an item whose serving size is unknown - and then the entry
 * simply has no nutrition data.
 *
 * Redundancy is deliberate: when all three are known they are consistent
 * (total = count x servingQuantity), stored at full precision, and rounded only
 * for display.
 *
 * Persisted as plain columns of meal_portions (see MealPortionEntity), so every
 * figure stays queryable in SQL. Immutable: change an entry by building a new one.
 */
public final class PortionQuantity {

    /** Which figure the user entered - decides display and the slide Update reopens on. */
    public enum InputMode { SERVINGS, QUANTITY }

    private final InputMode inputMode;
    private final Double servingCount;
    private final Double servingQuantity;
    private final Double totalQuantity;
    private final Unit unit;

    private PortionQuantity(InputMode inputMode, Double servingCount,
                            Double servingQuantity, Double totalQuantity, Unit unit) {
        this.inputMode = inputMode;
        this.servingCount = servingCount;
        this.servingQuantity = servingQuantity;
        this.totalQuantity = totalQuantity;
        this.unit = unit;
    }

    // ========== FACTORIES ==========

    /**
     * The user entered a number of servings (slide 1).
     *
     * @param servingCount    number of servings, > 0 (e.g. 3.5)
     * @param servingQuantity size of one serving at logging time, or null if the
     *                        item has none (then the total is unknown too)
     * @param unit            G or ML - required when servingQuantity is given
     * @throws IllegalArgumentException on a non-positive or non-finite figure, or a
     *         servingQuantity without a G/ML unit
     */
    public static PortionQuantity ofServings(double servingCount, Double servingQuantity, Unit unit) {
        requirePositive(servingCount, "servingCount");
        if (servingQuantity != null) {
            requirePositive(servingQuantity, "servingQuantity");
            requireMassOrVolume(unit);
        }
        Double total = servingQuantity != null ? servingCount * servingQuantity : null;
        return new PortionQuantity(InputMode.SERVINGS, servingCount, servingQuantity, total,
                servingQuantity != null ? unit : null);
    }

    /**
     * The user entered a total quantity (slide 2). The number of servings it
     * represents is derived when the serving size is known (100 g of a 30 g
     * serving = 3.33 servings).
     *
     * @param totalQuantity   total eaten, > 0, in unit
     * @param unit            G or ML
     * @param servingQuantity size of one serving at logging time, or null
     * @throws IllegalArgumentException on a non-positive or non-finite figure, or a
     *         unit other than G/ML
     */
    public static PortionQuantity ofQuantity(double totalQuantity, Unit unit, Double servingQuantity) {
        requirePositive(totalQuantity, "totalQuantity");
        requireMassOrVolume(unit);
        if (servingQuantity != null) {
            requirePositive(servingQuantity, "servingQuantity");
        }
        Double count = servingQuantity != null ? totalQuantity / servingQuantity : null;
        return new PortionQuantity(InputMode.QUANTITY, count, servingQuantity, totalQuantity, unit);
    }

    /**
     * Rebuild a stored entry exactly as it was saved - no validation, no
     * recomputation. Only for reading back what the database holds.
     */
    public static PortionQuantity restore(InputMode inputMode, Double servingCount,
                                          Double servingQuantity, Double totalQuantity, Unit unit) {
        return new PortionQuantity(
                inputMode != null ? inputMode : InputMode.QUANTITY,
                servingCount, servingQuantity, totalQuantity, unit);
    }

    /**
     * Best-effort snapshot of a portion that only has a free-form ServingSize
     * (built before snapshots existed, or through the generic constructor).
     * SERVING/PORTION units become a servings entry; anything else a quantity
     * entry in grams, or ml for ML.
     */
    public static PortionQuantity fromServingSize(ServingSize serving) {
        if (serving == null) {
            return restore(InputMode.QUANTITY, null, null, null, null);
        }
        Unit servingUnit = serving.getUnit();
        if (servingUnit == Unit.SERVING || servingUnit == Unit.PORTION) {
            Double total = serving.getGramEquivalent();
            return restore(InputMode.SERVINGS, serving.getQuantity(), null, total,
                    total != null ? Unit.G : null);
        }
        Double total = serving.getAsGrams();
        return restore(InputMode.QUANTITY, null, null, total,
                total != null ? (servingUnit == Unit.ML ? Unit.ML : Unit.G) : null);
    }

    // ========== CONVERSION ==========

    /**
     * The same entry as a ServingSize, for the code that still displays portions
     * through ServingSize (getDisplayText(), getAsGrams()). A servings entry keeps
     * its total as the gram equivalent, so getAsGrams() returns the logged total.
     */
    public ServingSize toServingSize() {
        if (inputMode == InputMode.SERVINGS && servingCount != null) {
            ServingSize serving = new ServingSize(servingCount, Unit.SERVING);
            serving.setGramEquivalent(totalQuantity);
            return serving;
        }
        if (totalQuantity != null) {
            return new ServingSize(totalQuantity, unit != null ? unit : Unit.G);
        }
        return new ServingSize();
    }

    // ========== GETTERS ==========

    public InputMode getInputMode() { return inputMode; }
    public Double getServingCount() { return servingCount; }
    public Double getServingQuantity() { return servingQuantity; }
    /** In the item's basis unit; null when unknown (entry then has no nutrition data). */
    public Double getTotalQuantity() { return totalQuantity; }
    public Unit getUnit() { return unit; }

    // ========== HELPERS ==========

    private static void requirePositive(double value, String name) {
        if (!(value > 0) || Double.isInfinite(value)) {
            throw new IllegalArgumentException(name + " must be a positive number: " + value);
        }
    }

    private static void requireMassOrVolume(Unit unit) {
        if (unit != Unit.G && unit != Unit.ML) {
            throw new IllegalArgumentException("Unit must be G or ML: " + unit);
        }
    }

    @Override
    public String toString() {
        return "PortionQuantity{" + inputMode + ", count=" + servingCount
                + ", serving=" + servingQuantity + ", total=" + totalQuantity
                + ", unit=" + unit + "}";
    }
}
