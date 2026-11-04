package li.masciul.sugardaddi.ui.components;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Locale;

import li.masciul.sugardaddi.core.enums.Unit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Reading and writing quantities: what the user can type, and how figures look.
 */
public class QuantityTextTest {

    private Locale savedLocale;

    @Before
    public void saveLocale() {
        savedLocale = Locale.getDefault();
    }

    @After
    public void restoreLocale() {
        Locale.setDefault(savedLocale);
    }

    // ========== parse ==========

    @Test
    public void parse_acceptsIntegersAndBothSeparators() {
        assertEquals(105.0, QuantityText.parse("105"), 1e-9);
        assertEquals(33.5, QuantityText.parse("33,5"), 1e-9);
        assertEquals(33.5, QuantityText.parse("33.5"), 1e-9);
        assertEquals(0.5, QuantityText.parse(",5"), 1e-9);
        assertEquals(5.0, QuantityText.parse("5."), 1e-9);
    }

    @Test
    public void parse_ignoresSpacesIncludingNonBreaking() {
        assertEquals(1050.0, QuantityText.parse(" 1 050 "), 1e-9);
        assertEquals(1050.0, QuantityText.parse("1\u00A0050"), 1e-9);
    }

    @Test
    public void parse_rejectsEmptyAndNonPositive() {
        assertNull(QuantityText.parse(null));
        assertNull(QuantityText.parse(""));
        assertNull(QuantityText.parse("   "));
        assertNull(QuantityText.parse("0"));
        assertNull(QuantityText.parse("0,0"));
        assertNull(QuantityText.parse("."));
        assertNull(QuantityText.parse(","));
    }

    @Test
    public void parse_rejectsWhatParseDoubleWouldAccept() {
        assertNull(QuantityText.parse("1e5"));
        assertNull(QuantityText.parse("0x1p3"));
        assertNull(QuantityText.parse("NaN"));
        assertNull(QuantityText.parse("Infinity"));
        assertNull(QuantityText.parse("-5"));
        assertNull(QuantityText.parse("+5"));
        assertNull(QuantityText.parse("5d"));
    }

    @Test
    public void parse_rejectsTwoSeparators() {
        assertNull(QuantityText.parse("1.2.3"));
        assertNull(QuantityText.parse("1,2.3"));
    }

    // ========== format ==========

    @Test
    public void format_oneDecimalHalfUpNoGrouping() {
        assertEquals("105", QuantityText.format(105.0, '.'));
        assertEquals("3.5", QuantityText.format(3.5, '.'));
        assertEquals("3,5", QuantityText.format(3.5, ','));
        assertEquals("3.3", QuantityText.format(3.25, '.'));
        assertEquals("3.3", QuantityText.format(3.333, '.'));
        assertEquals("1234", QuantityText.format(1234.0, '.'));
        assertEquals("0.5", QuantityText.format(0.5, '.'));
    }

    @Test
    public void format_followsDefaultLocaleSeparator() {
        Locale.setDefault(Locale.FRANCE);
        assertEquals("3,5", QuantityText.format(3.5));
        Locale.setDefault(Locale.US);
        assertEquals("3.5", QuantityText.format(3.5));
    }

    @Test
    public void format_isReadBackByParse() {
        for (double value : new double[] {0.5, 1, 3.5, 33.3, 105, 1234.5}) {
            assertEquals(value, QuantityText.parse(QuantityText.format(value, ',')), 1e-9);
            assertEquals(value, QuantityText.parse(QuantityText.format(value, '.')), 1e-9);
        }
    }

    @Test
    public void formatAmount_appendsUnit() {
        Locale.setDefault(Locale.FRANCE);
        assertEquals("105 g", QuantityText.formatAmount(105, Unit.G));
        assertEquals("2,5 ml", QuantityText.formatAmount(2.5, Unit.ML));
    }
}
