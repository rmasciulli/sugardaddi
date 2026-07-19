package li.masciul.sugardaddi.data.sources.fatsecret.api.dto;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * SingleOrArrayDeserializer - Handles FatSecret's inconsistent list wrapping.
 *
 * FatSecret's REST API wraps a list of N items as a JSON array when N > 1,
 * but as a single bare object (not array-wrapped) when N == 1 - explicitly
 * documented on their own recipes.search page: "Response json is only in
 * array format when more than one object is returned." This shows up on
 * multiple nested list fields across all four endpoints this app uses
 * (recipe[], ingredient[], direction[], recipe_type[], food[]), not just
 * one - a generic, reusable deserializer avoids a hand-written special
 * case for each one.
 *
 * Applied per-field via @JsonAdapter(SingleOrArrayDeserializer.class) -
 * same registration pattern as OpenFoodFactsProduct's
 * FlexibleListDeserializer, which solves a different, String-specific
 * version of a similar problem (OFF sometimes sends a string instead of
 * a one-element array; this handles a bare object instead of a
 * one-element array).
 */
public class SingleOrArrayDeserializer<T> implements JsonDeserializer<List<T>> {

    @Override
    public List<T> deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {

        List<T> result = new ArrayList<>();
        if (json == null || json.isJsonNull()) {
            return result;
        }

        // The component type (T) inside List<T> - needed to deserialize
        // each element correctly, whether we found one bare object/value
        // or several inside an array.
        Type componentType = ((ParameterizedType) typeOfT).getActualTypeArguments()[0];

        if (json.isJsonArray()) {
            JsonArray array = json.getAsJsonArray();
            for (JsonElement element : array) {
                result.add(context.deserialize(element, componentType));
            }
        } else if (json.isJsonObject() || json.isJsonPrimitive()) {
            // Single item, not array-wrapped - FatSecret's documented quirk.
            // isJsonPrimitive() covers list-of-strings fields (e.g.
            // recipe_ingredients.ingredient, recipe_types.recipe_type),
            // which hit this same single-vs-array inconsistency.
            result.add(context.deserialize(json, componentType));
        }
        // Anything else (e.g. an empty string) - return the empty list

        return result;
    }
}