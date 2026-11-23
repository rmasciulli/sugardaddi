package li.masciul.sugardaddi.data.sources.openfoodfacts.api.dto;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.annotations.JsonAdapter;

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * OpenFoodFactsNutriments - OFF's "nutriments" object, kept as a map of keys to
 * numbers.
 *
 * WHY A MAP: OFF writes each nutrient under several keys, one per set:
 *   "{nutrient}_100g"           as sold, per 100 g / 100 ml
 *   "{nutrient}_prepared_100g"  prepared following the package instructions
 * A typed field per nutrient and per set would mean two copies of about seventy
 * fields; with a map the mapper reads both sets with the same code, asking for
 * ("calcium", AS_SOLD) or ("calcium", PREPARED).
 *
 * WHAT THE VALUES ARE (OFF product nutrition schema): already per 100 g / 100 ml,
 * whatever the label gave (OFF computes them from per-serving entries itself), in
 * grams for every nutrient measured by weight, kJ for "energy-kj" and "energy",
 * kcal for "energy-kcal", % vol for "alcohol".
 *
 * NOT READ: the unsuffixed "{nutrient}" keys (per 100 g OR per serving, as typed)
 * and "{nutrient}_serving". The map keeps every numeric entry, but the mapper only
 * asks for the two suffixes above.
 *
 * Numbers sent as strings ("12.5") are accepted; anything else (units, "~"
 * modifiers, empty strings) is skipped.
 */
@JsonAdapter(OpenFoodFactsNutriments.Deserializer.class)
public class OpenFoodFactsNutriments {

    /** Suffix of the as-sold set: the reference, see NutritionPreparation. */
    public static final String AS_SOLD = "_100g";

    /** Suffix of the prepared set. */
    public static final String PREPARED = "_prepared_100g";

    private final Map<String, Double> values;

    public OpenFoodFactsNutriments(@NonNull Map<String, Double> values) {
        this.values = Collections.unmodifiableMap(new HashMap<>(values));
    }

    /**
     * Value of "{nutrient}{set}", e.g. get("calcium", AS_SOLD) reads "calcium_100g".
     *
     * @param nutrient OFF nutrient key ("proteins", "saturated-fat", "vitamin-c")
     * @param set      AS_SOLD or PREPARED
     * @return the value, or null when OFF has none
     */
    @Nullable
    public Double get(@NonNull String nutrient, @NonNull String set) {
        return values.get(nutrient + set);
    }

    /** Number of numeric entries read (for logs and tests). */
    public int size() {
        return values.size();
    }

    // ========== GSON ==========

    /** Reads every numeric entry of the "nutriments" object into the map. */
    public static class Deserializer implements JsonDeserializer<OpenFoodFactsNutriments> {
        @Override
        public OpenFoodFactsNutriments deserialize(JsonElement json, Type typeOfT,
                                                   JsonDeserializationContext context) {
            Map<String, Double> values = new HashMap<>();
            if (json != null && json.isJsonObject()) {
                JsonObject object = json.getAsJsonObject();
                for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                    Double value = number(entry.getValue());
                    if (value != null) values.put(entry.getKey(), value);
                }
            }
            return new OpenFoodFactsNutriments(values);
        }

        /** A JSON number, or a string holding one; null for anything else. */
        @Nullable
        private static Double number(JsonElement element) {
            if (element == null || !element.isJsonPrimitive()) return null;
            JsonPrimitive primitive = element.getAsJsonPrimitive();
            double value;
            if (primitive.isNumber()) {
                value = primitive.getAsDouble();
            } else if (primitive.isString()) {
                try {
                    value = Double.parseDouble(primitive.getAsString().trim());
                } catch (NumberFormatException e) {
                    return null;
                }
            } else {
                return null;
            }
            return Double.isNaN(value) || Double.isInfinite(value) ? null : value;
        }
    }
}
