package li.masciul.sugardaddi.data.database.converters;

import androidx.room.TypeConverter;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import li.masciul.sugardaddi.core.models.RecipeTranslation;
import li.masciul.sugardaddi.data.network.ApiConfig;
import android.util.Log;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

/**
 * RecipeTranslationMapConverter - Converts Map<String, RecipeTranslation> for Room storage
 *
 * HYBRID LANGUAGE ARCHITECTURE:
 * - Stores translations for all NON-PRIMARY languages
 * - Primary language content stored directly in RecipeEntity fields
 * - Handles complex nested structures (List<RecipeStep>, List<String>)
 *
 * RECIPE-SPECIFIC CONSIDERATIONS:
 * - RecipeTranslation contains List<RecipeStep> (nested objects)
 * - Multiple List<String> fields (equipment, tips)
 * - Potentially larger JSON than ProductTranslation
 * - Instructions can be lengthy text
 *
 * EXAMPLE JSON STRUCTURE:
 * {
 *   "fr": {
 *     "name": "Tarte aux Pommes",
 *     "description": "Une délicieuse tarte française",
 *     "instructions": "...",
 *     "instructionSteps": [
 *       {"stepNumber": 1, "instruction": "Préchauffer le four...", "durationMinutes": 5},
 *       {"stepNumber": 2, "instruction": "Préparer la pâte...", "durationMinutes": 15}
 *     ],
 *     "equipmentNeeded": ["Four", "Rouleau à pâtisserie"],
 *     "cookingTips": ["Utiliser des pommes bien mûres"],
 *     "cuisine": "Française",
 *     "lastUpdated": 1696348800000
 *   }
 * }
 */
public class RecipeTranslationMapConverter {

    private static final String TAG = ApiConfig.DATABASE_LOG_TAG;

    /**
     * Gson instance configured for translation serialization
     * NOTE: RecipeTranslation contains nested objects (RecipeStep, Lists)
     */
    private static final Gson gson = new GsonBuilder()
            .serializeNulls()
            .create();

    /**
     * Type token for Map deserialization
     */
    private static final Type MAP_TYPE = new TypeToken<Map<String, RecipeTranslation>>(){}.getType();

    // ========== SERIALIZATION ==========

    /**
     * Convert Map<String, RecipeTranslation> to JSON string for database storage
     *
     * NOTE: RecipeTranslation JSON can be significantly larger than ProductTranslation
     * due to instruction steps and lists
     *
     * @param map Translation map (language code -> RecipeTranslation)
     * @return JSON string, or null if map is empty/null
     */
    @TypeConverter
    public static String fromMap(Map<String, RecipeTranslation> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }

        try {
            String json = gson.toJson(map, MAP_TYPE);

            if (ApiConfig.DEBUG_LOGGING) {
                // Recipe translations can be large - log size for monitoring
                int avgSizePerLanguage = json.length() / map.size();
                Log.d(TAG, "Converted RecipeTranslation map to JSON for " +
                        map.size() + " languages (total: " + json.length() +
                        " bytes, avg: " + avgSizePerLanguage + " bytes/lang)");
            }

            return json;

        } catch (Exception e) {
            Log.e(TAG, "Failed to convert RecipeTranslation map to JSON", e);
            // Log which recipe had the issue if possible
            if (ApiConfig.DEBUG_LOGGING) {
                Log.e(TAG, "Map contained " + map.size() + " languages");
            }
            return null;
        }
    }

    // ========== DESERIALIZATION ==========

    /**
     * Convert JSON string from database to Map<String, RecipeTranslation>
     *
     * @param json JSON string from database
     * @return Translation map, or empty map if parsing fails
     */
    @TypeConverter
    public static Map<String, RecipeTranslation> toMap(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new HashMap<>();
        }

        try {
            Map<String, RecipeTranslation> map = gson.fromJson(json, MAP_TYPE);

            if (ApiConfig.DEBUG_LOGGING && map != null) {
                Log.d(TAG, "Converted JSON to RecipeTranslation map with " +
                        map.size() + " languages");
            }

            return map != null ? map : new HashMap<>();

        } catch (Exception e) {
            Log.e(TAG, "Failed to convert JSON to RecipeTranslation map", e);
            // Return empty map to maintain app stability
            return new HashMap<>();
        }
    }

    // ========== VALIDATION HELPERS ==========

    /**
     * Check if a translation map is effectively empty
     */
    public static boolean isEmpty(Map<String, RecipeTranslation> map) {
        return map == null || map.isEmpty();
    }

    /**
     * Count number of available languages in a translation map
     */
    public static int getLanguageCount(Map<String, RecipeTranslation> map) {
        return map == null ? 0 : map.size();
    }

    /**
     * Estimate JSON size for a translation map (useful for optimization)
     * NOTE: This is an approximation, actual JSON size may vary
     */
    public static int estimateJsonSize(Map<String, RecipeTranslation> map) {
        if (map == null || map.isEmpty()) {
            return 0;
        }
        // Rough estimate: ~500 bytes per translation (instructions are longer)
        return map.size() * 500;
    }

    /**
     * Test if JSON string is valid translation map structure
     */
    public static boolean isValidJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return true;
        }

        try {
            Map<String, RecipeTranslation> map = gson.fromJson(json, MAP_TYPE);
            return map != null;
        } catch (Exception e) {
            return false;
        }
    }
}