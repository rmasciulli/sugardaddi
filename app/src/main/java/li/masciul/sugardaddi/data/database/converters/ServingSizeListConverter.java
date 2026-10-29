package li.masciul.sugardaddi.data.database.converters;

import androidx.room.TypeConverter;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import li.masciul.sugardaddi.core.models.ServingSize;
import li.masciul.sugardaddi.data.network.ApiConfig;
import android.util.Log;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * ServingSizeListConverter - Converts List<ServingSize> for Room storage
 *
 * PURPOSE:
 * - Stores FoodProduct.servingOptions / Recipe.servingOptions: every serving
 *   the source offers in the item's nutrition basis unit (see the field
 *   Javadoc on either model for what the list may and may not contain).
 * - Same Gson setup as ServingSizeConverter, so a single ServingSize
 *   serializes identically whether it sits in servingSize or in this list.
 *
 * CONTRACT:
 * - null or empty list  -> null column (no "[]" rows to tell apart from null)
 * - null or blank column -> empty list, never null
 * - unreadable JSON      -> empty list, logged (a bad row must not break the
 *                           whole product load)
 *
 * EXAMPLE JSON:
 * [
 *   { "description": "1 bar", "quantity": 40.0, "unit": "G", ... },
 *   { "description": "100 g", "quantity": 100.0, "unit": "G", ... }
 * ]
 */
public class ServingSizeListConverter {

    private static final String TAG = ApiConfig.DATABASE_LOG_TAG;

    private static final Gson gson = new GsonBuilder()
            .serializeNulls()
            .create();

    private static final Type LIST_TYPE = new TypeToken<List<ServingSize>>(){}.getType();

    /**
     * Convert List<ServingSize> to JSON for database storage.
     *
     * @param servings Servings to store
     * @return JSON array string, or null if the list is null or empty
     */
    @TypeConverter
    public static String fromList(List<ServingSize> servings) {
        if (servings == null || servings.isEmpty()) {
            return null;
        }

        try {
            return gson.toJson(servings, LIST_TYPE);
        } catch (Exception e) {
            Log.e(TAG, "Failed to convert ServingSize list to JSON", e);
            return null;
        }
    }

    /**
     * Convert a JSON string from the database to List<ServingSize>.
     *
     * @param json JSON array string from the database
     * @return The servings, or an empty list if absent or unreadable
     */
    @TypeConverter
    public static List<ServingSize> toList(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new ArrayList<>();
        }

        try {
            List<ServingSize> servings = gson.fromJson(json, LIST_TYPE);
            return servings != null ? servings : new ArrayList<>();
        } catch (Exception e) {
            Log.e(TAG, "Failed to convert JSON to ServingSize list", e);
            return new ArrayList<>();
        }
    }
}
