package li.masciul.sugardaddi.data.sources.usda.mappers;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import li.masciul.sugardaddi.core.enums.Unit;
import li.masciul.sugardaddi.core.models.ServingSize;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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

    // ========== density (7B) ==========

    @Test
    public void density_srLegacyMilk_allVolumePortionsAgree() {
        // 244/236.588, 30.5/29.5735, 976/946.353 = 1.0313; 15/14.7868 = 1.0144 (within 5%)
        assertEquals(1.0313, USDAPortions.density(srLegacyMilk()), 1e-4);
    }

    @Test
    public void density_surveyMilk_fromDescriptions_guidelinesIgnored() {
        assertEquals(1.0313, USDAPortions.density(surveyMilk()), 1e-4);
    }

    @Test
    public void density_cutSolid_none() {
        assertNull(USDAPortions.density(foundationBroccoli()));
        assertNull(USDAPortions.density(Collections.singletonList(
                portion(148.0, 1.0, 1, "undetermined", "cup, whole", null))));
    }

    @Test
    public void density_singleVolumePortion_accepted() {
        // Choice A: one volume portion is enough (soups with only "1 cup")
        assertEquals(1.0144, USDAPortions.density(Collections.singletonList(
                portion(240.0, 1.0, 1, "undetermined", "cup", null))), 1e-4);
    }

    @Test
    public void density_disagreeingPortions_none() {
        assertNull(USDAPortions.density(Arrays.asList(
                portion(244.0, 1.0, 1, "undetermined", "cup", null),
                portion(20.0, 1.0, 2, "undetermined", "tbsp", null))));   // 1.35 vs 1.03
    }

    @Test
    public void density_implausible_none() {
        assertNull(USDAPortions.density(Collections.singletonList(
                portion(10.0, 1.0, 1, "undetermined", "cup", null))));    // 0.04 g/ml
    }

    @Test
    public void volume_readsQuantitiesUnitsAndBrackets() {
        assertEquals(236.588, USDAPortions.volumeMl("1 cup"), 1e-6);
        assertEquals(118.294, USDAPortions.volumeMl("1/2 cup"), 1e-6);
        assertEquals(354.882, USDAPortions.volumeMl("1 1/2 cups"), 1e-6);
        assertEquals(29.5735, USDAPortions.volumeMl("1 fl oz"), 1e-6);
        assertEquals(354.882, USDAPortions.volumeMl("1 can (12 fl oz)"), 1e-6);
        assertEquals(500.0, USDAPortions.volumeMl("1 bottle (500 ml)"), 1e-6);
        assertEquals(14.7868, USDAPortions.volumeMl("tbsp"), 1e-6);
        assertNull(USDAPortions.volumeMl("1 oz"));                        // weight
        assertNull(USDAPortions.volumeMl("1 large"));                     // "l" is not "large"
        assertNull(USDAPortions.volumeMl("1 individual school container"));
    }

    @Test
    public void isLiquid_byCategory_byName_orNot() {
        assertTrue(USDAPortions.isLiquid("Dairy and Egg Products", "Milk, whole"));
        assertTrue(USDAPortions.isLiquid("Milk, whole", "Milk, whole"));            // WWEIA
        assertTrue(USDAPortions.isLiquid("SOFT DRINKS", "Cola"));                   // case ignored
        assertTrue(USDAPortions.isLiquid("Fruits and Fruit Juices", "Orange juice, raw"));
        assertTrue(USDAPortions.isLiquid("Sweets", "Syrups, maple"));
        assertFalse(USDAPortions.isLiquid("Fruits and Fruit Juices", "Blueberries, raw"));
        assertFalse(USDAPortions.isLiquid("Vegetables and Vegetable Products", "Broccoli, raw"));
        assertFalse(USDAPortions.isLiquid("Cheese", "Cheese, cheddar"));
        assertFalse(USDAPortions.isLiquid(null, "Milk"));
    }
}
