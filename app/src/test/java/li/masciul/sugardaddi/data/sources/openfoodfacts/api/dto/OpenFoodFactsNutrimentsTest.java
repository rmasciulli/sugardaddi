package li.masciul.sugardaddi.data.sources.openfoodfacts.api.dto;

import com.google.gson.Gson;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * OFF "nutriments" read through Gson into the key map, as Retrofit does.
 */
public class OpenFoodFactsNutrimentsTest {

    private static final double EPS = 1e-9;
    private static final Gson GSON = new Gson();

    private static OpenFoodFactsNutriments parse(String json) {
        return GSON.fromJson(json, OpenFoodFactsNutriments.class);
    }

    /** 5410769300085 Chouffe IPA, "nutriments" copied from API v2 (2026-11-22). */
    private static final String CHOUFFE_IPA = "{"
            + "\"added-sugars\":4.0,\"added-sugars_100g\":4,\"added-sugars_modifier\":\"~\","
            + "\"added-sugars_unit\":\"g\",\"added-sugars_value\":4.0,"
            + "\"alcohol\":9,\"alcohol_100g\":9,\"alcohol_serving\":9,"
            + "\"alcohol_unit\":\"% vol\",\"alcohol_value\":9,"
            + "\"fruits-vegetables-legumes-estimate-from-ingredients_100g\":9.53,"
            + "\"fruits-vegetables-nuts-estimate-from-ingredients_100g\":0.0,"
            + "\"nova-group\":4,\"nova-group_100g\":4,\"nova-group_serving\":4,"
            + "\"nova-group_unit\":\"\",\"nova-group_value\":4}";

    @Test
    public void realRecord_numbersKept_textSkipped() {
        OpenFoodFactsNutriments n = parse(CHOUFFE_IPA);
        assertEquals(9.0, n.get("alcohol", OpenFoodFactsNutriments.AS_SOLD), EPS);
        assertNull(n.get("alcohol", OpenFoodFactsNutriments.PREPARED));
        assertNull(n.get("proteins", OpenFoodFactsNutriments.AS_SOLD));
        // "% vol", "~" and "" are not numbers: not in the map
        assertEquals(13, n.size());
    }

    /**
     * 3033710073467 Ricore L'Original (100 g tin of powder), prepared values only,
     * per 100 ml ("nutrition_data_prepared_per": "100ml"); part of its
     * "nutriments" copied from API v2 (2026-11-23).
     */
    private static final String RICORE = "{"
            + "\"energy-kcal_prepared\":50,\"energy-kcal_prepared_100g\":50,"
            + "\"energy-kcal_prepared_serving\":100,\"energy-kcal_unit\":\"kcal\","
            + "\"energy-kj_prepared_100g\":211,\"energy_prepared_100g\":211,"
            + "\"magnesium_prepared_100g\":0.0282,\"magnesium_unit\":\"g\","
            + "\"sodium_prepared_100g\":0.04,\"sodium_prepared_modifier\":\"~\","
            + "\"sugars_prepared_100g\":4.9,\"sugars_unit\":\"g\"}";

    @Test
    public void realPreparedOnlyRecord_readWithItsOwnSuffix() {
        OpenFoodFactsNutriments n = parse(RICORE);
        assertNull(n.get("energy-kcal", OpenFoodFactsNutriments.AS_SOLD));
        assertEquals(50.0, n.get("energy-kcal", OpenFoodFactsNutriments.PREPARED), EPS);
        assertEquals(211.0, n.get("energy", OpenFoodFactsNutriments.PREPARED), EPS);
        // grams in OFF: 28.2 mg of magnesium, 40 mg of sodium per 100 ml prepared
        assertEquals(0.0282, n.get("magnesium", OpenFoodFactsNutriments.PREPARED), EPS);
        assertEquals(0.04, n.get("sodium", OpenFoodFactsNutriments.PREPARED), EPS);
    }

    @Test
    public void numbersAsStrings_andOddValues() {
        OpenFoodFactsNutriments n = parse("{"
                + "\"fat_100g\":\"12.5\",\"salt_100g\":\" 0.8 \",\"sugars_100g\":\"traces\","
                + "\"fiber_100g\":null,\"proteins_100g\":true,\"energy_100g\":{\"x\":1}}");
        assertEquals(12.5, n.get("fat", OpenFoodFactsNutriments.AS_SOLD), EPS);
        assertEquals(0.8, n.get("salt", OpenFoodFactsNutriments.AS_SOLD), EPS);
        assertNull(n.get("sugars", OpenFoodFactsNutriments.AS_SOLD));
        assertNull(n.get("fiber", OpenFoodFactsNutriments.AS_SOLD));
        assertNull(n.get("proteins", OpenFoodFactsNutriments.AS_SOLD));
        assertNull(n.get("energy", OpenFoodFactsNutriments.AS_SOLD));
    }

    @Test
    public void emptyOrMissingObject() {
        assertEquals(0, parse("{}").size());
        assertNull(parse("null"));
    }
}
