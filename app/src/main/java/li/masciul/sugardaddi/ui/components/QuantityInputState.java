package li.masciul.sugardaddi.ui.components;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import li.masciul.sugardaddi.core.enums.Unit;
import li.masciul.sugardaddi.core.models.PortionQuantity;

/**
 * QuantityInputState - the rules of the quantity input, without any view.
 *
 * Plain Java (no Android), so every rule is covered by unit tests.
 * QuantityInputView only displays this state and forwards the user's edits.
 *
 * WHAT IT HOLDS:
 * - the item's serving size (may be unknown) and the one unit of the input;
 * - a number of servings (slide "Number of servings");
 * - a typed quantity (slide "Portion size"), null while empty or invalid;
 * - which of the two the user edited last (inputMode).
 *
 * RULES:
 * - The value edited last is what gets logged (PortionQuantity.InputMode).
 *   Looking at the other slide never changes it.
 * - Servings start at 1, move in steps of 0.5, never below 0.5. A count that is
 *   not on a step (3.33, from a typed quantity) moves to the next step: 3.5 up,
 *   3 down.
 * - When the serving size is known, each value is kept in line with the other:
 *   3.5 servings of 30 g show as 105 g, and 100 g typed shows as 3.33 servings.
 *   When it is unknown, nothing is converted, since there is nothing to convert with.
 * - The servings value is always valid. A typed quantity is valid only when it
 *   is a positive number: until then toPortionQuantity() returns null, so the
 *   screen can disable its Add button.
 */
public final class QuantityInputState {

    public static final double SERVING_STEP = 0.5;
    public static final double MIN_SERVINGS = 0.5;

    /** Size of one serving in unit; null when the item has none. */
    @Nullable private final Double servingQuantity;
    /** G or ML: unit of the serving size and of the typed quantity. */
    @NonNull private final Unit unit;

    private double servingCount = 1.0;
    @Nullable private Double customQuantity;
    @NonNull private PortionQuantity.InputMode inputMode = PortionQuantity.InputMode.SERVINGS;

    /**
     * A fresh input: 1 serving, servings mode, no typed quantity.
     *
     * @param servingQuantity size of one serving in unit, or null if unknown
     *                        (zero, negative or not finite counts as unknown)
     * @param unit            G or ML; anything else is read as G
     */
    public QuantityInputState(@Nullable Double servingQuantity, @NonNull Unit unit) {
        this.servingQuantity = isPositive(servingQuantity) ? servingQuantity : null;
        this.unit = unit == Unit.ML ? Unit.ML : Unit.G;
        syncCustomFromServings();
    }

    // ========== EDITS ==========

    /** "+" on the servings slide. */
    public void increaseServings() {
        setServingsEdited(stepUp(servingCount));
    }

    /** "-" on the servings slide. Never goes below MIN_SERVINGS. */
    public void decreaseServings() {
        setServingsEdited(stepDown(servingCount));
    }

    /**
     * The user typed on the portion size slide.
     *
     * @param quantity the parsed value, or null when the field is empty or invalid
     */
    public void setCustomQuantity(@Nullable Double quantity) {
        customQuantity = isPositive(quantity) ? quantity : null;
        inputMode = PortionQuantity.InputMode.QUANTITY;
        if (customQuantity != null && servingQuantity != null) {
            servingCount = customQuantity / servingQuantity;
        }
    }

    /**
     * Put back what the user had entered (screen rotation, recipe refreshed).
     * The value that was edited last is restored as is and the other one is
     * derived again from the current serving size, which may have changed.
     *
     * @param mode           which value was edited last
     * @param servingCount   servings at that time; ignored unless positive
     * @param customQuantity typed quantity at that time, or null
     */
    public void restoreEdits(@NonNull PortionQuantity.InputMode mode,
                             double servingCount, @Nullable Double customQuantity) {
        if (mode == PortionQuantity.InputMode.QUANTITY) {
            setCustomQuantity(customQuantity);
        } else if (isPositive(servingCount)) {
            setServingsEdited(servingCount);
        }
    }

    // ========== READS ==========

    @Nullable
    public Double getServingQuantity() {
        return servingQuantity;
    }

    @NonNull
    public Unit getUnit() {
        return unit;
    }

    public double getServingCount() {
        return servingCount;
    }

    /** Typed or derived quantity; null while the field is empty or invalid. */
    @Nullable
    public Double getCustomQuantity() {
        return customQuantity;
    }

    @NonNull
    public PortionQuantity.InputMode getInputMode() {
        return inputMode;
    }

    /** False at the minimum, so the screen can disable "-". */
    public boolean canDecrease() {
        return servingCount > MIN_SERVINGS;
    }

    /**
     * What Add would log now, frozen as a PortionQuantity.
     *
     * @return null while the typed quantity is the value being edited and is
     *         empty or invalid
     */
    @Nullable
    public PortionQuantity toPortionQuantity() {
        if (inputMode == PortionQuantity.InputMode.SERVINGS) {
            return PortionQuantity.ofServings(servingCount, servingQuantity,
                    servingQuantity != null ? unit : null);
        }
        if (customQuantity == null) return null;
        return PortionQuantity.ofQuantity(customQuantity, unit, servingQuantity);
    }

    // ========== RULES (package-private for tests) ==========

    /**
     * Next half-step strictly above: 1 -> 1.5, 3.5 -> 4, 3.33 -> 3.5 (not 3.83).
     * The epsilon keeps 3.5 (stored as 3.4999...) from stepping to 3.5 again.
     */
    static double stepUp(double count) {
        return Math.floor(count / SERVING_STEP + 1e-9) * SERVING_STEP + SERVING_STEP;
    }

    /** Previous half-step strictly below, never under MIN_SERVINGS: 3.33 -> 3, 0.5 -> 0.5. */
    static double stepDown(double count) {
        double down = Math.ceil(count / SERVING_STEP - 1e-9) * SERVING_STEP - SERVING_STEP;
        return Math.max(MIN_SERVINGS, down);
    }

    // ========== INTERNALS ==========

    private void setServingsEdited(double count) {
        servingCount = count;
        inputMode = PortionQuantity.InputMode.SERVINGS;
        syncCustomFromServings();
    }

    /** Keep the portion size slide showing the same amount, when it can be converted. */
    private void syncCustomFromServings() {
        if (servingQuantity != null) {
            customQuantity = servingCount * servingQuantity;
        }
    }

    private static boolean isPositive(@Nullable Double value) {
        return value != null && value > 0 && !Double.isInfinite(value) && !Double.isNaN(value);
    }
}
