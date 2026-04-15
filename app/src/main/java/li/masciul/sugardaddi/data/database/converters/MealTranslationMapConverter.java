package li.masciul.sugardaddi.data.database.converters;

import androidx.room.TypeConverter;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import li.masciul.sugardaddi.core.models.MealTranslation;
import li.masciul.sugardaddi.data.network.ApiConfig;
import android.util.Log;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

/**
 * MealTranslationMapConverter - Converts Map<String, MealTranslation> for Room storage
 *
 * HYBRID LANGUAGE ARCHITECTURE:
 * - Stores translations for all NON-PRIMARY languages
 * - Primary language content stored directly in MealEntity fields
 * - Simplest translation structure (only 5 fields)
 *
 * MEAL-SPECIFIC CONSIDERATIONS:
 * - Smallest translation class (5 fields vs 9-10 for others)
 * - Most compact JSON representation
 * - Meals are primarily composition-based (portions), not text-heavy
 * - Focus on context (occasion, location) rather than instructions
 *
 * EXAMPLE JSON STRUCTURE:
 * {
 *   "fr": {
 *     "name": "Petit-déjeuner rapide",
 *     "description": "Repas simple pour commencer la journée",
 *     "notes": "Préparé en 10 minutes",
 *     "occasion": "Déjeuner du matin",
 *     "location": "À la maison",
 *     "lastUpdated": 1696348800000
 *   },
 *   "es": {
 *     "name": "Desayuno rápido",
 *     ...
 *   }
 * }
 */
public class MealTranslationMapConverter {

    private static final String TAG = ApiConfig.DATABASE_LOG_TAG;

    /**
     * Gson instance configured for translation serialization
     */
    private static final Gson gson = new GsonBuilder()
            .serializeNulls()
            .create();

    /**
     * Type token for Map deserialization
     */
    private static final Type MAP_TYPE = new TypeToken<Map<String, MealTranslation>>(){}.getType();

    // ========== SERIALIZATION ==========

    /**
     * Convert Map<String, MealTranslation> to JSON string for database storage
     *
     * NOTE: MealTranslation is the smallest/simplest translation class
     * JSON size is typically ~150-200 bytes per language
     *
     * @param map Translation map (language code -> MealTranslation)
     * @return JSON string, or null if map is empty/null
     */
    @TypeConverter
    public static String fromMap(Map<String, MealTranslation> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }

        try {
            String json = gson.toJson(map, MAP_TYPE);

            if (ApiConfig.DEBUG_LOGGING) {
                Log.d(TAG, "Converted MealTranslation map to JSON for " +
                        map.size() + " languages (size: " + json.length() + " bytes)");
            }

            return json;

        } catch (Exception e) {
            Log.e(TAG, "Failed to convert MealTranslation map to JSON", e);
            return null;
        }
    }

    // ========== DESERIALIZATION ==========

    /**
     * Convert JSON string from database to Map<String, MealTranslation>
     *
     * @param json JSON string from database
     * @return Translation map, or empty map if parsing fails
     */
    @TypeConverter
    public static Map<String, MealTranslation> toMap(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new HashMap<>();
        }

        try {
            Map<String, MealTranslation> map = gson.fromJson(json, MAP_TYPE);

            if (ApiConfig.DEBUG_LOGGING && map != null) {
                Log.d(TAG, "Converted JSON to MealTranslation map with " +
                        map.size() + " languages");
            }

            return map != null ? map : new HashMap<>();

        } catch (Exception e) {
            Log.e(TAG, "Failed to convert JSON to MealTranslation map", e);
            return new HashMap<>();
        }
    }

    // ========== VALIDATION HELPERS ==========

    /**
     * Check if a translation map is effectively empty
     */
    public static boolean isEmpty(Map<String, MealTranslation> map) {
        return map == null || map.isEmpty();
    }

    /**
     * Count number of available languages in a translation map
     */
    public static int getLanguageCount(Map<String, MealTranslation> map) {
        return map == null ? 0 : map.size();
    }

    /**
     * Test if JSON string is valid translation map structure
     */
    public static boolean isValidJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return true;
        }

        try {
            Map<String, MealTranslation> map = gson.fromJson(json, MAP_TYPE);
            return map != null;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Estimate JSON size for a translation map
     * MealTranslation is compact (~150 bytes per translation)
     */
    public static int estimateJsonSize(Map<String, MealTranslation> map) {
        if (map == null || map.isEmpty()) {
            return 0;
        }
        // Meals have fewer fields - smaller estimate than recipes
        return map.size() * 150;
    }
}