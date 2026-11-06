package li.masciul.sugardaddi.core.utils;

import org.junit.Test;

import li.masciul.sugardaddi.core.enums.Unit;
import li.masciul.sugardaddi.core.models.ServingSize;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Grams and milliliters through a density: amounts, per-100 factors, servings.
 * Whole milk (1.033 g/ml) is the running example.
 */
public class DensityConversionTest {

    private static final double EPS = 1e-9;
    private static final double MILK = 1.033;

    // ========== kindOf ==========

    @Test
    public void kindOf_readsWeightAndVolume() {
        assertEquals(Unit.G, DensityConversion.kindOf(Unit.KG));
        assertEquals(Unit.G, DensityConversion.kindOf(Unit.OZ));
        assertEquals(Unit.ML, DensityConversion.kindOf(Unit.CUP));
        assertEquals(Unit.ML, DensityConversion.kindOf(Unit.L));
        assertNull(DensityConversion.kindOf(Unit.SERVING));
        assertNull(DensityConversion.kindOf(null));
    }

    // ========== convert ==========

    @Test
    public void convert_sameUnit_isUnchanged_evenWithoutDensity() {
        assertEquals(250.0, DensityConversion.convert(250, Unit.ML, Unit.ML, null), EPS);
        assertEquals(75.0, DensityConversion.convert(75, Unit.G, Unit.G, null), EPS);
    }

    @Test
    public void convert_throughDensity_bothWays() {
        assertEquals(258.25, DensityConversion.convert(250, Unit.ML, Unit.G, MILK), EPS);
        assertEquals(250.0, DensityConversion.convert(258.25, Unit.G, Unit.ML, MILK), EPS);
    }

    @Test
    public void convert_roundTrip_keepsTheAmount() {
        Double ml = DensityConversion.convert(75, Unit.G, Unit.ML, MILK);
        assertEquals(75.0, DensityConversion.convert(ml, Unit.ML, Unit.G, MILK), EPS);
    }

    @Test
    public void convert_otherUnitWithoutDensity_isNull_neverOneToOne() {
        assertNull(DensityConversion.convert(250, Unit.ML, Unit.G, null));
        assertNull(DensityConversion.convert(250, Unit.ML, Unit.G, 0.0));
    }

    @Test
    public void convert_refusesUnitsOtherThanGAndMl() {
        assertNull(DensityConversion.convert(1, Unit.CUP, Unit.G, MILK));
        assertNull(DensityConversion.convert(1, Unit.G, Unit.SERVING, MILK));
    }

    // ========== per100Factor ==========

    @Test
    public void per100Factor_gramBasisShownPer100ml_isTheDensity() {
        // 60 kcal per 100 g -> 62 kcal per 100 ml
        Double factor = DensityConversion.per100Factor(Unit.G, Unit.ML, MILK);
        assertEquals(MILK, factor, EPS);
        assertEquals(61.98, 60 * factor, EPS);
    }

    @Test
    public void per100Factor_mlBasisShownPer100g_isTheInverse() {
        assertEquals(1 / MILK, DensityConversion.per100Factor(Unit.ML, Unit.G, MILK), EPS);
    }

    @Test
    public void per100Factor_basisItself_isOne_otherWithoutDensity_isNull() {
        assertEquals(1.0, DensityConversion.per100Factor(Unit.G, Unit.G, null), EPS);
        assertNull(DensityConversion.per100Factor(Unit.G, Unit.ML, null));
    }

    // ========== servingIn ==========

    @Test
    public void servingIn_sameKind_readsItDirectly() {
        assertEquals(240.0, DensityConversion.servingIn(new ServingSize(240, Unit.ML), Unit.ML, null), EPS);
        assertEquals(30.0, DensityConversion.servingIn(new ServingSize(30, Unit.G), Unit.G, null), EPS);
    }

    @Test
    public void servingIn_otherKind_convertsThroughDensity() {
        // FatSecret "1 cup" of milk kept in ml, item stored per 100 g
        assertEquals(247.92, DensityConversion.servingIn(new ServingSize(240, Unit.ML), Unit.G, MILK), EPS);
        assertEquals(240.0, DensityConversion.servingIn(new ServingSize(247.92, Unit.G), Unit.ML, MILK), EPS);
    }

    @Test
    public void servingIn_volumeUnits_areReadAsVolumeFirst() {
        // 1 cup = 240 ml in Unit, then through the density
        assertEquals(247.92, DensityConversion.servingIn(new ServingSize(1, Unit.CUP), Unit.G, MILK), EPS);
    }

    @Test
    public void servingIn_otherKindWithoutDensity_keepsTheHistoricalReading() {
        // ServingSize counts a ml as a gram: unchanged behaviour without a density
        assertEquals(250.0, DensityConversion.servingIn(new ServingSize(250, Unit.ML), Unit.G, null), EPS);
    }

    @Test
    public void servingIn_noSize_isNull() {
        assertNull(DensityConversion.servingIn(null, Unit.G, MILK));
        assertNull(DensityConversion.servingIn(new ServingSize(1, Unit.SERVING), Unit.G, MILK));
    }
}
