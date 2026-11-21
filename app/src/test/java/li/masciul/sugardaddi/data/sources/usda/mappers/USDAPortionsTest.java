package li.masciul.sugardaddi.data.sources.usda.mappers;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import li.masciul.sugardaddi.core.enums.Unit;
import li.masciul.sugardaddi.core.models.ServingSize;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * USDA portions -> serving sizes, on the three real records checked on
 * 2026-11-21 (fields copied from the API responses, in the API's order).
 */
public class USDAPortionsTest {

    private static final double EPS = 1e-9;

    private static USDAPortions.Portion portion(Double grams, Double amount, Integer seq,
                                                String unit, String modifier, String description) {
        USDAPortions.Portion p = new USDAPortions.Portion();
        p.gramWeight = grams;
        p.amount = amount;
        p.sequenceNumber = seq;
        p.unitName = unit;
        p.modifier = modifier;
        p.portionDescription = description;
        return p;
    }

    /** SR Legacy, Milk, whole (171265): label in modifier, list not sorted. */
    private static List<USDAPortions.Portion> srLegacyMilk() {
        return Arrays.asList(
                portion(30.5, 1.0, 2, "undetermined", "fl oz", null),
                portion(244.0, 1.0, 1, "undetermined", "cup", null),
                portion(15.0, 1.0, 3, "undetermined", "tbsp", null),
                portion(976.0, 1.0, 4, "undetermined", "quart", null));
    }

    /** Foundation, Broccoli, raw (747447): real unit + detail, and a RACC row. */
    private static List<USDAPortions.Portion> foundationBroccoli() {
        return Arrays.asList(
                portion(76.0, 1.0, 1, "cup", "chopped", null),
                portion(85.0, 1.0, 1, "RACC", null, null));
    }

    /** Survey (FNDDS), Milk, whole (2705385): descriptions, codes, guideline rows. */
    private static List<USDAPortions.Portion> surveyMilk() {
        return Arrays.asList(
                portion(2.5, null, 4, "undetermined", "63480", "Guideline amount per fl oz of beverage"),
                portion(244.0, null, 6, "undetermined", "90000", "Quantity not specified"),
                portion(244.0, null, 3, "undetermined", "64294", "1 individual school container"),
                portion(244.0, null, 1, "undetermined", "10205", "1 cup"),
                portion(61.0, null, 5, "undetermined", "63546", "Guideline amount per cup of hot cereal"),
                portion(30.5, null, 2, "undetermined", "30000", "1 fl oz"));
    }

    private static List<String> descriptions(List<ServingSize> servings) {
        String[] out = new String[servings.size()];
        for (int i = 0; i < out.length; i++) out[i] = servings.get(i).getDescription();
        return Arrays.asList(out);
    }

    // ========== SR Legacy ==========

    @Test
    public void srLegacy_sortedBySequence_labelFromModifier() {
        assertEquals(Arrays.asList("1 cup (244 g)", "1 fl oz (30.5 g)", "1 tbsp (15 g)",
                        "1 quart (976 g)"),
                descriptions(USDAPortions.servingOptions(srLegacyMilk())));
    }

    @Test
    public void srLegacy_typicalIsFirstInUsdaOrder() {
        ServingSize typical = USDAPortions.typicalServing(srLegacyMilk());
        assertEquals(244.0, typical.getQuantity(), EPS);
        assertEquals(Unit.G, typical.getUnit());
        assertEquals("1 cup (244 g)", typical.getDescription());
    }

    // ========== Foundation ==========

    @Test
    public void foundation_unitWithDetail_raccSkipped() {
        assertEquals(Collections.singletonList("1 cup, chopped (76 g)"),
                descriptions(USDAPortions.servingOptions(foundationBroccoli())));
        assertEquals(76.0, USDAPortions.typicalServing(foundationBroccoli()).getQuantity(), EPS);
    }

    // ========== Survey ==========

    @Test
    public void survey_guidelinesSkipped_descriptionIsTheLabel() {
        assertEquals(Arrays.asList("1 cup (244 g)", "1 fl oz (30.5 g)",
                        "1 individual school container (244 g)", "244 g"),
                descriptions(USDAPortions.servingOptions(surveyMilk())));
    }

    @Test
    public void survey_typicalIsQuantityNotSpecified() {
        ServingSize typical = USDAPortions.typicalServing(surveyMilk());
        assertEquals(244.0, typical.getQuantity(), EPS);
        assertEquals("244 g", typical.getDescription());
    }

    // ========== edges ==========

    @Test
    public void noUsablePortion_noTypical() {
        assertNull(USDAPortions.typicalServing(null));
        assertTrue(USDAPortions.servingOptions(Collections.emptyList()).isEmpty());
        assertNull(USDAPortions.typicalServing(Arrays.asList(
                portion(0.0, 1.0, 1, "undetermined", "cup", null),
                portion(null, 1.0, 2, "undetermined", "tbsp", null),
                portion(85.0, 1.0, 1, "RACC", null, null))));
    }

    @Test
    public void bareOunceIsKept_itsGramWeightIsGiven() {
        assertEquals("1 oz (28.35 g)", USDAPortions.typicalServing(Collections.singletonList(
                portion(28.35, 1.0, 1, "undetermined", "oz", null))).getDescription());
    }

    @Test
    public void format_dropsUselessDecimals() {
        assertEquals("1", USDAPortions.format(1.0));
        assertEquals("30.5", USDAPortions.format(30.5));
        assertEquals("0.25", USDAPortions.format(0.25));
    }
}
