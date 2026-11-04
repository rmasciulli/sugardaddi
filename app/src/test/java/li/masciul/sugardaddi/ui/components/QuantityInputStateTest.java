package li.masciul.sugardaddi.ui.components;

import org.junit.Test;

import li.masciul.sugardaddi.core.enums.Unit;
import li.masciul.sugardaddi.core.models.PortionQuantity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The rules of the quantity input: steps, conversions, which value is logged.
 */
public class QuantityInputStateTest {

    private static final double EPS = 1e-9;

    // ========== steps ==========

    @Test
    public void stepUp_goesToNextHalf() {
        assertEquals(1.5, QuantityInputState.stepUp(1.0), EPS);
        assertEquals(4.0, QuantityInputState.stepUp(3.5), EPS);
        assertEquals(3.5, QuantityInputState.stepUp(3.33), EPS);
        assertEquals(0.5, QuantityInputState.stepUp(0.2), EPS);
        assertEquals(4.0, QuantityInputState.stepUp(3.4999999999), EPS);
    }

    @Test
    public void stepDown_goesToPreviousHalfNeverBelowMinimum() {
        assertEquals(0.5, QuantityInputState.stepDown(1.0), EPS);
        assertEquals(3.0, QuantityInputState.stepDown(3.33), EPS);
        assertEquals(3.0, QuantityInputState.stepDown(3.5), EPS);
        assertEquals(0.5, QuantityInputState.stepDown(0.5), EPS);
        assertEquals(0.5, QuantityInputState.stepDown(0.3), EPS);
    }

    // ========== fresh state ==========

    @Test
    public void newState_isOneServingInServingsMode() {
        QuantityInputState state = new QuantityInputState(30.0, Unit.G);
        assertEquals(1.0, state.getServingCount(), EPS);
        assertEquals(PortionQuantity.InputMode.SERVINGS, state.getInputMode());
        assertEquals(30.0, state.getCustomQuantity(), EPS);
        assertTrue(state.canDecrease());
    }

    @Test
    public void newState_withoutServingSize_hasNoCustomQuantity() {
        QuantityInputState state = new QuantityInputState(null, Unit.G);
        assertNull(state.getServingQuantity());
        assertNull(state.getCustomQuantity());
    }

    @Test
    public void newState_treatsInvalidServingSizeAsUnknown() {
        assertNull(new QuantityInputState(0.0, Unit.G).getServingQuantity());
        assertNull(new QuantityInputState(-5.0, Unit.G).getServingQuantity());
        assertNull(new QuantityInputState(Double.NaN, Unit.G).getServingQuantity());
    }

    @Test
    public void newState_onlyKeepsGramsOrMillilitres() {
        assertEquals(Unit.ML, new QuantityInputState(null, Unit.ML).getUnit());
        assertEquals(Unit.G, new QuantityInputState(null, Unit.CUP).getUnit());
    }

    // ========== servings ==========

    @Test
    public void servings_updateCustomQuantityWhenServingKnown() {
        QuantityInputState state = new QuantityInputState(30.0, Unit.G);
        state.increaseServings();
        state.increaseServings();
        state.increaseServings();
        state.increaseServings();
        state.increaseServings();
        assertEquals(3.5, state.getServingCount(), EPS);
        assertEquals(105.0, state.getCustomQuantity(), EPS);
    }

    @Test
    public void servings_stopAtMinimum() {
        QuantityInputState state = new QuantityInputState(30.0, Unit.G);
        state.decreaseServings();
        assertEquals(0.5, state.getServingCount(), EPS);
        assertFalse(state.canDecrease());
        state.decreaseServings();
        assertEquals(0.5, state.getServingCount(), EPS);
    }

    @Test
    public void servings_logAServingsEntry() {
        QuantityInputState state = new QuantityInputState(30.0, Unit.G);
        state.increaseServings();
        PortionQuantity logged = state.toPortionQuantity();
        assertNotNull(logged);
        assertEquals(PortionQuantity.InputMode.SERVINGS, logged.getInputMode());
        assertEquals(1.5, logged.getServingCount(), EPS);
        assertEquals(45.0, logged.getTotalQuantity(), EPS);
        assertEquals(Unit.G, logged.getUnit());
    }

    @Test
    public void servings_withoutServingSize_logCountOnly() {
        QuantityInputState state = new QuantityInputState(null, Unit.G);
        state.increaseServings();
        PortionQuantity logged = state.toPortionQuantity();
        assertNotNull(logged);
        assertEquals(1.5, logged.getServingCount(), EPS);
        assertNull(logged.getTotalQuantity());
        assertNull(logged.getUnit());
    }

    // ========== typed quantity ==========

    @Test
    public void typed_updatesServingCountWhenServingKnown() {
        QuantityInputState state = new QuantityInputState(30.0, Unit.G);
        state.setCustomQuantity(100.0);
        assertEquals(PortionQuantity.InputMode.QUANTITY, state.getInputMode());
        assertEquals(100.0 / 30.0, state.getServingCount(), EPS);
    }

    @Test
    public void typed_thenStep_movesToNextHalf() {
        QuantityInputState state = new QuantityInputState(30.0, Unit.G);
        state.setCustomQuantity(100.0);   // 3.33 servings
        state.increaseServings();
        assertEquals(3.5, state.getServingCount(), EPS);
        assertEquals(105.0, state.getCustomQuantity(), EPS);
        assertEquals(PortionQuantity.InputMode.SERVINGS, state.getInputMode());
    }

    @Test
    public void typed_logsAQuantityEntryInTheInputUnit() {
        QuantityInputState state = new QuantityInputState(null, Unit.ML);
        state.setCustomQuantity(250.0);
        PortionQuantity logged = state.toPortionQuantity();
        assertNotNull(logged);
        assertEquals(PortionQuantity.InputMode.QUANTITY, logged.getInputMode());
        assertEquals(250.0, logged.getTotalQuantity(), EPS);
        assertEquals(Unit.ML, logged.getUnit());
        assertNull(logged.getServingCount());
    }

    @Test
    public void typed_emptyOrInvalid_logsNothing() {
        QuantityInputState state = new QuantityInputState(30.0, Unit.G);
        state.setCustomQuantity(null);
        assertNull(state.toPortionQuantity());
        state.setCustomQuantity(-3.0);
        assertNull(state.toPortionQuantity());
        state.setCustomQuantity(Double.POSITIVE_INFINITY);
        assertNull(state.toPortionQuantity());
    }

    @Test
    public void typed_emptyThenServingsEdited_isValidAgain() {
        QuantityInputState state = new QuantityInputState(30.0, Unit.G);
        state.setCustomQuantity(null);
        state.increaseServings();
        assertNotNull(state.toPortionQuantity());
    }

    // ========== restore ==========

    @Test
    public void restore_servings_rederivesQuantityFromCurrentServingSize() {
        // The recipe was refreshed and its serving size changed from 30 g to 40 g.
        QuantityInputState state = new QuantityInputState(40.0, Unit.G);
        state.restoreEdits(PortionQuantity.InputMode.SERVINGS, 2.5, 75.0);
        assertEquals(2.5, state.getServingCount(), EPS);
        assertEquals(100.0, state.getCustomQuantity(), EPS);
        assertEquals(PortionQuantity.InputMode.SERVINGS, state.getInputMode());
    }

    @Test
    public void restore_quantity_keepsTypedValue() {
        QuantityInputState state = new QuantityInputState(40.0, Unit.G);
        state.restoreEdits(PortionQuantity.InputMode.QUANTITY, 3.33, 100.0);
        assertEquals(100.0, state.getCustomQuantity(), EPS);
        assertEquals(2.5, state.getServingCount(), EPS);
        assertEquals(PortionQuantity.InputMode.QUANTITY, state.getInputMode());
    }

    @Test
    public void restore_emptyTypedValue_staysInvalid() {
        QuantityInputState state = new QuantityInputState(30.0, Unit.G);
        state.restoreEdits(PortionQuantity.InputMode.QUANTITY, 1.0, null);
        assertNull(state.toPortionQuantity());
    }

    @Test
    public void restore_ignoresInvalidServingCount() {
        QuantityInputState state = new QuantityInputState(30.0, Unit.G);
        state.restoreEdits(PortionQuantity.InputMode.SERVINGS, 0.0, null);
        assertEquals(1.0, state.getServingCount(), EPS);
    }

    // ========== from a logged entry (Update) ==========

    @Test
    public void from_servings_keepsLoggedServingSize() {
        // Logged as 2 servings of 30 g; the item's serving is 40 g today.
        PortionQuantity logged = PortionQuantity.ofServings(2.0, 30.0, Unit.G);
        QuantityInputState state = QuantityInputState.from(logged, 40.0, Unit.G);
        assertEquals(30.0, state.getServingQuantity(), EPS);
        assertEquals(2.0, state.getServingCount(), EPS);
        assertEquals(60.0, state.getCustomQuantity(), EPS);
        assertEquals(PortionQuantity.InputMode.SERVINGS, state.getInputMode());
    }

    @Test
    public void from_unchanged_savesTheSameAmount() {
        PortionQuantity logged = PortionQuantity.ofServings(2.0, 30.0, Unit.G);
        PortionQuantity saved = QuantityInputState.from(logged, 40.0, Unit.G).toPortionQuantity();
        assertNotNull(saved);
        assertEquals(60.0, saved.getTotalQuantity(), EPS);
        assertEquals(30.0, saved.getServingQuantity(), EPS);
        assertEquals(2.0, saved.getServingCount(), EPS);
    }

    @Test
    public void from_quantity_keepsTypedTotal() {
        PortionQuantity logged = PortionQuantity.ofQuantity(100.0, Unit.G, 30.0);
        QuantityInputState state = QuantityInputState.from(logged, 40.0, Unit.G);
        assertEquals(PortionQuantity.InputMode.QUANTITY, state.getInputMode());
        assertEquals(100.0, state.getCustomQuantity(), EPS);
        assertEquals(100.0 / 30.0, state.getServingCount(), EPS);
    }

    @Test
    public void from_servingsWithoutSize_usesCurrentSize() {
        PortionQuantity logged = PortionQuantity.ofServings(2.0, null, null);
        QuantityInputState state = QuantityInputState.from(logged, 40.0, Unit.G);
        assertEquals(40.0, state.getServingQuantity(), EPS);
        assertEquals(80.0, state.getCustomQuantity(), EPS);
    }

    @Test
    public void from_quantityInMl_ignoresCurrentSizeInGrams() {
        PortionQuantity logged = PortionQuantity.ofQuantity(250.0, Unit.ML, null);
        QuantityInputState state = QuantityInputState.from(logged, 40.0, Unit.G);
        assertEquals(Unit.ML, state.getUnit());
        assertNull(state.getServingQuantity());
        assertEquals(250.0, state.getCustomQuantity(), EPS);
    }
}
