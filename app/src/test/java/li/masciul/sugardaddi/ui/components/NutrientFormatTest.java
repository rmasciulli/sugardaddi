package li.masciul.sugardaddi.ui.components;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * How nutrient values look on screen: decimals, rounding, units, separators.
 */
public class NutrientFormatTest {

    private static final String UG = "µg";

    private Locale savedLocale;

    @Before
    public void saveLocale() {
        savedLocale = Locale.getDefault();
    }

    @After
    public void restoreLocale() {
        Locale.setDefault(savedLocale);
    }

    // ========== number ==========

    @Test
    public void number_keepsUpToTwoDecimals() {
        assertEquals("1.76", NutrientFormat.number(1.76, '.'));
        assertEquals("1.77", NutrientFormat.number(1.766, '.'));
        assertEquals("46.5", NutrientFormat.number(46.5, '.'));
        assertEquals("19.65", NutrientFormat.number(19.65, '.'));
    }

    @Test
    public void number_dropsTrailingZeros() {
        assertEquals("1.7", NutrientFormat.number(1.70, '.'));
        assertEquals("24", NutrientFormat.number(24.0, '.'));
        assertEquals("0.4", NutrientFormat.number(0.40, '.'));
        assertEquals("20", NutrientFormat.number(20.0, '.'));
        assertEquals("1234.5", NutrientFormat.number(1234.5, '.'));
        assertEquals("1000", NutrientFormat.number(1000.0, '.'));  // no "1E+3"
    }

    @Test
    public void number_roundsHalfUp() {
        assertEquals("1.77", NutrientFormat.number(1.765, '.'));
        assertEquals("0.13", NutrientFormat.number(0.125, '.'));
        assertEquals("2.68", NutrientFormat.number(2.675, '.'));  // 2.67499999... as a double
    }

    @Test
    public void number_ignoresFloatingPointNoiseFromSums() {
        // 0.1 + 0.2 = 0.30000000000000004
        assertEquals("0.3", NutrientFormat.number(0.1 + 0.2, '.'));
        // a sum landing just under .xx5 still rounds up
        assertEquals("1.77", NutrientFormat.number(1.7649999999999999, '.'));
        // scaling: 5.3 g per 100 g, 150 g -> 7.95 (7.949999999999999 as a double)
        assertEquals("7.95", NutrientFormat.number(5.3 * 1.5, '.'));
    }

    @Test
    public void number_usesTheGivenSeparator() {
        assertEquals("1,76", NutrientFormat.number(1.76, ','));
        assertEquals("24", NutrientFormat.number(24.0, ','));
        assertEquals("1.76", NutrientFormat.number(1.76, ';'));  // unknown -> '.'
    }

    @Test
    public void number_followsTheAppLanguage() {
        Locale.setDefault(Locale.FRENCH);
        assertEquals("1,76", NutrientFormat.number(1.76));
        Locale.setDefault(Locale.ENGLISH);
        assertEquals("1.76", NutrientFormat.number(1.76));
    }

    @Test
    public void number_zeroAndOddValues() {
        assertEquals("0", NutrientFormat.number(0.0, '.'));
        assertEquals("0", NutrientFormat.number(0.004, '.'));
        assertEquals("0", NutrientFormat.number(-0.001, '.'));  // no "-0"
        assertEquals("0", NutrientFormat.number(Double.NaN, '.'));
    }

    // ========== energy ==========

    @Test
    public void energy_wholeNumbersHalfUp() {
        assertEquals("43", NutrientFormat.energy(43.4, '.'));
        assertEquals("65", NutrientFormat.energy(64.5, '.'));
        assertEquals("267", NutrientFormat.energy(267.0, '.'));
        assertEquals("0", NutrientFormat.energy(0.4, '.'));
    }

    // ========== value ==========

    @Test
    public void value_unknownZeroAndTraces() {
        assertEquals("-", NutrientFormat.value(null, "g", '.'));
        assertEquals("0 g", NutrientFormat.value(0.0, "g", '.'));
        assertEquals("< 0.01 g", NutrientFormat.value(0.004, "g", '.'));
        assertEquals("< 0,01 mg", NutrientFormat.value(0.009, "mg", ','));
        assertEquals("0.01 g", NutrientFormat.value(0.01, "g", '.'));
    }

    @Test
    public void value_nutrientsAndEnergy() {
        assertEquals("1.76 g", NutrientFormat.value(1.76, "g", '.'));
        assertEquals("0.4 g", NutrientFormat.value(0.40, "g", '.'));
        assertEquals("20 mg", NutrientFormat.value(20.0, "mg", '.'));
        assertEquals("43 kcal", NutrientFormat.value(43.37, "kcal", '.'));
        assertEquals("178 kJ", NutrientFormat.value(178.2, "kJ", '.'));
        assertEquals("0 kcal", NutrientFormat.value(0.0, "kcal", '.'));
        assertEquals("-", NutrientFormat.value(null, "kcal", '.'));
    }

    @Test
    public void isEnergyUnit() {
        assertTrue(NutrientFormat.isEnergyUnit("kcal"));
        assertTrue(NutrientFormat.isEnergyUnit("kJ"));
        assertFalse(NutrientFormat.isEnergyUnit("g"));
        assertFalse(NutrientFormat.isEnergyUnit(null));
    }

    // ========== unit steps ==========

    @Test
    public void displayUnit_climbsAsManyStepsAsNeeded() {
        assertEquals("g", NutrientFormat.displayUnit(1_760_000.0, UG));
        assertEquals("g", NutrientFormat.displayUnit(24_000.0, "mg"));
        assertEquals("mg", NutrientFormat.displayUnit(1_500.0, UG));
        assertEquals(UG, NutrientFormat.displayUnit(999.0, UG));
        assertEquals("g", NutrientFormat.displayUnit(1_200.0, "g"));       // no larger unit
        assertEquals("kcal", NutrientFormat.displayUnit(2_000.0, "kcal"));
        assertEquals("mg", NutrientFormat.displayUnit(null, "mg"));
    }

    @Test
    public void unitDivisor_matchesTheSteps() {
        assertEquals(1_000_000.0, NutrientFormat.unitDivisor(UG, "g"), 0.0);
        assertEquals(1_000.0, NutrientFormat.unitDivisor("mg", "g"), 0.0);
        assertEquals(1.0, NutrientFormat.unitDivisor("mg", "mg"), 0.0);
        assertEquals(1.0, NutrientFormat.unitDivisor("g", UG), 0.0);       // never downwards
    }

    @Test
    public void stepsAndValueTogether() {
        // The Nesquick case: 1 760 000 µg and 1 766 000 µg of vitamin D
        double v1 = 1_760_000.0;
        String u1 = NutrientFormat.displayUnit(v1, UG);
        assertEquals("1.76 g", NutrientFormat.value(v1 / NutrientFormat.unitDivisor(UG, u1), u1, '.'));
        double v2 = 1_766_000.0;
        String u2 = NutrientFormat.displayUnit(v2, UG);
        assertEquals("1.77 g", NutrientFormat.value(v2 / NutrientFormat.unitDivisor(UG, u2), u2, '.'));
        // 7450 mg
        double v3 = 7_450.0;
        String u3 = NutrientFormat.displayUnit(v3, "mg");
        assertEquals("7.45 g", NutrientFormat.value(v3 / NutrientFormat.unitDivisor("mg", u3), u3, '.'));
    }
}
