package li.masciul.sugardaddi.data.sources.openfoodfacts.mappers;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * OFF "_100g" values are grams for every nutrient measured by weight (OFF product
 * nutrition schema). The app's fields are g, mg or µg (NutrientInfo units).
 */
public class OffUnitsTest {

    private static final double EPS = 1e-9;

    @Test
    public void grams_unchanged() {
        assertEquals(3.6, OffUnits.fromGrams(3.6, "g"), EPS);
    }

    @Test
    public void milligrams_sodiumAndCalcium() {
        // sodium 400 mg is sodium_100g = 0.4; calcium 120 mg is calcium_100g = 0.12
        assertEquals(400.0, OffUnits.fromGrams(0.4, "mg"), EPS);
        assertEquals(120.0, OffUnits.fromGrams(0.12, "mg"), EPS);
    }

    @Test
    public void micrograms_allSpellings() {
        // vitamin D 2.5 µg is vitamin-d_100g = 0.0000025
        assertEquals(2.5, OffUnits.fromGrams(0.0000025, "µg"), EPS);
        assertEquals(2.5, OffUnits.fromGrams(0.0000025, "μg"), EPS);
        assertEquals(2.5, OffUnits.fromGrams(0.0000025, "ug"), EPS);
    }

    @Test
    public void nullAndUnknownUnit() {
        assertNull(OffUnits.fromGrams(null, "mg"));
        assertEquals(7.0, OffUnits.fromGrams(7.0, "kcal"), EPS);
        assertEquals(7.0, OffUnits.fromGrams(7.0, null), EPS);
    }
}
