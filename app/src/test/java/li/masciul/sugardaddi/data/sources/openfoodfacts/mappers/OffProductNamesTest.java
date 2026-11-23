package li.masciul.sugardaddi.data.sources.openfoodfacts.mappers;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Names and alcohol on the four real OFF records checked on 2026-11-22
 * (API v2, fields copied as returned; "" kept where OFF returns "").
 */
public class OffProductNamesTest {

    private static final double EPS = 1e-9;

    // ========== names: the four records ==========

    /** 5410228222958 Leffe 75 cl: main language nl, names "" except fr "Blonde". */
    @Test
    public void leffe75cl_frenchNameUsedInEnglish() {
        String name = OffProductNames.pick("en", null, "Blonde", "", null, "Bière", "", "Leffe");
        assertEquals("Blonde", name);
        assertEquals("Leffe Blonde", OffProductNames.withBrand(name, "Leffe"));
    }

    /** 5410769100098 La Chouffe: fr "La Chouffe", the brand is already in it. */
    @Test
    public void laChouffe_brandAlreadyInName() {
        String name = OffProductNames.pick("en", null, "La Chouffe", "", null, "Bière forte", "", "Chouffe");
        assertEquals("La Chouffe", name);
        assertEquals("La Chouffe", OffProductNames.withBrand(name, "Chouffe"));
    }

    /** 5410228197089 Leffe Rituel: main name filled. */
    @Test
    public void rituel_mainName() {
        String name = OffProductNames.pick("en", null, "Rituel 9°", "Rituel 9°", null, "", "", "Leffe");
        assertEquals("Rituel 9°", name);
        assertEquals("Leffe Rituel 9°", OffProductNames.withBrand(name, "Leffe"));
    }

    /** 5410769300085 Chouffe IPA: English name, empty brands. */
    @Test
    public void chouffeIpa_englishName_noBrand() {
        String name = OffProductNames.pick("en", "Chouffe IPA", "Chouffe", "Chouffe", null, "", "", "");
        assertEquals("Chouffe IPA", name);
        assertEquals("Chouffe IPA", OffProductNames.withBrand(name, ""));
    }

    // ========== names: order and edges ==========

    @Test
    public void french_requestedFirst_thenMain_thenEnglish() {
        assertEquals("Lait", OffProductNames.pick("fr", "Milk", "Lait", "Melk", null, null, null, null));
        assertEquals("Melk", OffProductNames.pick("fr", "Milk", "", "Melk", null, null, null, null));
        assertEquals("Milk", OffProductNames.pick("fr", "Milk", null, "  ", null, null, null, null));
    }

    @Test
    public void genericNames_thenBrand_thenNothing() {
        assertEquals("Bière", OffProductNames.pick("en", "", "", "", null, "Bière", "", "Leffe"));
        assertEquals("Leffe", OffProductNames.pick("en", "", "", "", "", "", "", "Leffe, AB InBev"));
        assertNull(OffProductNames.pick("en", null, null, null, null, null, null, "unknown"));
    }

    @Test
    public void withBrand_ignoresCaseAndAccents_andHandlesNoName() {
        assertEquals("Château Margaux", OffProductNames.withBrand("Château Margaux", "chateau"));
        assertEquals("Leffe", OffProductNames.withBrand(null, "Leffe, AB InBev"));
        assertNull(OffProductNames.withBrand(" ", null));
        assertTrue(OffProductNames.titleIsBrand("Leffe", "Leffe, AB InBev"));
        assertFalse(OffProductNames.titleIsBrand("Leffe Blonde", "Leffe"));
    }

    // ========== alcohol ==========

    /** Chouffe IPA: alcohol 9 "% vol", per 100 ml. */
    @Test
    public void alcohol_percentVolToGrams() {
        assertEquals(7.101, OffAlcohol.gramsPer100ml(9.0, true), EPS);
    }

    @Test
    public void alcohol_per100g_orImpossible_isEmpty() {
        assertNull(OffAlcohol.gramsPer100ml(9.0, false));
        assertNull(OffAlcohol.gramsPer100ml(120.0, true));
        assertNull(OffAlcohol.gramsPer100ml(null, true));
        assertEquals(0.0, OffAlcohol.gramsPer100ml(0.0, true), EPS);
    }
}
