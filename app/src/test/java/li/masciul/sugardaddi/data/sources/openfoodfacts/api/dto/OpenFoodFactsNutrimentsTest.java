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
     * Prepared-only shape (keys as in OFF's schema: "<nutrient>_prepared_100g").
     * Constructed: values of a cocoa drink made with milk, for the test only.
     */
    @Test
    public void preparedSet_readWithItsOwnSuffix() {
        OpenFoodFactsNutriments n = parse("{"
                + "\"energy-kcal_prepared_100g\":79,\"energy-kj_prepared_100g\":333,"
                + "\"sugars_prepared_100g\":9.5,\"sodium_prepared_100g\":0.05}");
        assertNull(n.get("energy-kcal", OpenFoodFactsNutriments.AS_SOLD));
        assertEquals(79.0, n.get("energy-kcal", OpenFoodFactsNutriments.PREPARED), EPS);
        assertEquals(0.05, n.get("sodium", OpenFoodFactsNutriments.PREPARED), EPS);
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
