package li.masciul.sugardaddi.data.sources.openfoodfacts.api.dto;

import com.google.gson.*;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * FlexibleListDeserializer - Handles inconsistent OFF API responses
 *
 * The OpenFoodFacts API sometimes returns:
 * - List fields as a String: "allergens_tags": "en:milk"
 * - List fields as an Array: "allergens_tags": ["en:milk", "en:eggs"]
 *
 * This deserializer handles both cases gracefully by:
 * 1. Checking if the value is a JsonArray (normal case)
 * 2. If String, converting it to a single-element List
 * 3. If null or empty, returning an empty List
 */
public class FlexibleListDeserializer implements JsonDeserializer<List<String>> {

    @Override
    public List<String> deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {

        if (json == null || json.isJsonNull()) {
            return new ArrayList<>();
        }

        // Normal case: JSON array
        if (json.isJsonArray()) {
            List<String> result = new ArrayList<>();
            JsonArray array = json.getAsJsonArray();
            for (JsonElement element : array) {
                if (!element.isJsonNull()) {
                    result.add(element.getAsString());
                }
            }
            return result;
        }

        // Edge case: API returned a String instead of an array
        if (json.isJsonPrimitive()) {
            List<String> result = new ArrayList<>();
            String value = json.getAsString();
            if (value != null && !value.trim().isEmpty()) {
                result.add(value);
            }
            return result;
        }

        // Unknown case: return empty list
        return new ArrayList<>();
    }
}