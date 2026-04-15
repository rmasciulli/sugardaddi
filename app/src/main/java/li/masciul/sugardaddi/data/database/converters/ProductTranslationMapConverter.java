package li.masciul.sugardaddi.data.database.converters;

import androidx.room.TypeConverter;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import li.masciul.sugardaddi.core.models.ProductTranslation;
import li.masciul.sugardaddi.data.network.ApiConfig;
import android.util.Log;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

/**
 * ProductTranslationMapConverter - Converts Map<String, ProductTranslation> for Room storage
 *
 * HYBRID LANGUAGE ARCHITECTURE:
 * - Stores translations for all NON-PRIMARY languages
 * - Primary language content stored directly in FoodProductEntity fields
 * - Empty map = only primary language available
 * - Map size = number of additional languages
 *
 * DESIGN NOTES:
 * - Replaces LocalizedContentMapConverter (reduced 85% storage)
 * - Only 9 translatable fields vs 50+ in old LocalizedContent
 * - Efficient JSON serialization with Gson
 * - Null-safe: returns empty map on errors
 *
 * EXAMPLE JSON STRUCTURE:
 * {
 *   "fr": {
 *     "name": "Pomme",
 *     "genericName": "Fruit",
 *     "description": "Fruit rond et croquant",
 *     "categories": "Fruits",
 *     "lastUpdated": 1696348800000,
 *     "verified": true
 *   },
 *   "es": {
 *     "name": "Manzana",
 *     "genericName": "Fruta",
 *     ...
 *   }
 * }
 */
public class ProductTranslationMapConverter {

    private static final String TAG = ApiConfig.DATABASE_LOG_TAG;

    /**
     * Gson instance configured for translation serialization
     * - serializeNulls: Explicitly handle null fields for consistency
     */
    private static final Gson gson = new GsonBuilder()
            .serializeNulls()
            .create();

    /**
     * Type token for Map deserialization
     * Required for Gson to properly deserialize generic types
     */
    private static final Type MAP_TYPE = new TypeToken<Map<String, ProductTranslation>>(){}.getType();

    // ========== SERIALIZATION ==========

    /**
     * Convert Map<String, ProductTranslation> to JSON string for database storage
     *
     * @param map Translation map (language code -> ProductTranslation)
     * @return JSON string, or null if map is empty/null
     */
    @TypeConverter
    public static String fromMap(Map<String, ProductTranslation> map) {
        // Don't store empty translations - saves database space
        if (map == null || map.isEmpty()) {
            return null;
        }

        try {
            String json = gson.toJson(map, MAP_TYPE);

            if (ApiConfig.DEBUG_LOGGING) {
                Log.d(TAG, "Converted ProductTranslation map to JSON for " +
                        map.size() + " languages (size: " + json.length() + " bytes)");
            }

            return json;

        } catch (Exception e) {
            Log.e(TAG, "Failed to convert ProductTranslation map to JSON", e);
            // Return null to avoid storing corrupted data
            return null;
        }
    }

    // ========== DESERIALIZATION ==========

    /**
     * Convert JSON string from database to Map<String, ProductTranslation>
     *
     * @param json JSON string from database
     * @return Translation map, or empty map if parsing fails
     */
    @TypeConverter
    public static Map<String, ProductTranslation> toMap(String json) {
        // Empty/null JSON = no translations available
        if (json == null || json.trim().isEmpty()) {
            return new HashMap<>();
        }

        try {
            Map<String, ProductTranslation> map = gson.fromJson(json, MAP_TYPE);

            if (ApiConfig.DEBUG_LOGGING && map != null) {
                Log.d(TAG, "Converted JSON to ProductTranslation map with " +
                        map.size() + " languages");
            }

            // Null-safety: Gson might return null on malformed JSON
            return map != null ? map : new HashMap<>();

        } catch (Exception e) {
            Log.e(TAG, "Failed to convert JSON to ProductTranslation map", e);
            // Return empty map to avoid crashes - data loss is better than app crash
            return new HashMap<>();
        }
    }

    // ========== VALIDATION HELPERS ==========

    /**
     * Check if a translation map is effectively empty
     * Useful for UI logic and cache decisions
     */
    public static boolean isEmpty(Map<String, ProductTranslation> map) {
        return map == null || map.isEmpty();
    }

    /**
     * Count number of available languages in a translation map
     */
    public static int getLanguageCount(Map<String, ProductTranslation> map) {
        return map == null ? 0 : map.size();
    }

    /**
     * Test if JSON string is valid translation map structure
     * Useful for migration and debugging
     */
    public static boolean isValidJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return true; // Empty is valid (means no translations)
        }

        try {
            Map<String, ProductTranslation> map = gson.fromJson(json, MAP_TYPE);
            return map != null;
        } catch (Exception e) {
            return false;
        }
    }
}