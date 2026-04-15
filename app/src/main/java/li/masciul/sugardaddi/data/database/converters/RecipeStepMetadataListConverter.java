package li.masciul.sugardaddi.data.database.converters;

import androidx.room.TypeConverter;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import li.masciul.sugardaddi.core.models.RecipeStepMetadata;
import li.masciul.sugardaddi.data.network.ApiConfig;
import android.util.Log;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * RecipeStepMetadataListConverter - Converts List<RecipeStepMetadata> for Room storage
 *
 * PURPOSE:
 * - Store universal recipe step structure (timing, images, equipment)
 * - Stored ONCE per recipe (no duplication across languages)
 * - Paired with RecipeStepTranslation for complete step display
 *
 * ARCHITECTURE:
 * - Recipe.stepStructure: List<RecipeStepMetadata> (stored here)
 * - Recipe.stepTranslations: List<RecipeStepTranslation> (per language)
 * - Combined via Recipe.getStep(stepNumber, language) helper
 *
 * STORAGE EFFICIENCY:
 * - Recipe with 10 steps in 3 languages:
 *   OLD: 30 RecipeStep objects (full duplication)
 *   NEW: 10 metadata + 30 translations (60% less metadata duplication)
 *
 * EXAMPLE JSON:
 * [
 *   {
 *     "stepNumber": 1,
 *     "durationMinutes": 5,
 *     "equipment": "Oven",
 *     "isOptional": false,
 *     "isCritical": false,
 *     "temperatureCelsius": 180
 *   },
 *   {
 *     "stepNumber": 2,
 *     "durationMinutes": 15,
 *     "equipment": "Mixing bowl",
 *     "imageUrl": "https://...",
 *     "isOptional": false
 *   }
 * ]
 */
public class RecipeStepMetadataListConverter {

    private static final String TAG = ApiConfig.DATABASE_LOG_TAG;

    private static final Gson gson = new GsonBuilder()
            .serializeNulls()
            .create();

    private static final Type LIST_TYPE = new TypeToken<List<RecipeStepMetadata>>(){}.getType();

    // ========== SERIALIZATION ==========

    /**
     * Convert List<RecipeStepMetadata> to JSON for database storage
     *
     * @param metadataList List of step metadata (universal structure)
     * @return JSON array string, or null if list is empty/null
     */
    @TypeConverter
    public static String fromList(List<RecipeStepMetadata> metadataList) {
        if (metadataList == null || metadataList.isEmpty()) {
            return null;
        }

        try {
            String json = gson.toJson(metadataList, LIST_TYPE);

            if (ApiConfig.DEBUG_LOGGING) {
                Log.d(TAG, "Converted RecipeStepMetadata list to JSON with " +
                        metadataList.size() + " steps (size: " + json.length() + " bytes)");
            }

            return json;

        } catch (Exception e) {
            Log.e(TAG, "Failed to convert RecipeStepMetadata list to JSON", e);
            if (ApiConfig.DEBUG_LOGGING) {
                Log.e(TAG, "List contained " + metadataList.size() + " metadata objects");
            }
            return null;
        }
    }

    // ========== DESERIALIZATION ==========

    /**
     * Convert JSON string from database to List<RecipeStepMetadata>
     *
     * @param json JSON array string from database
     * @return List of RecipeStepMetadata, or empty list if parsing fails
     */
    @TypeConverter
    public static List<RecipeStepMetadata> toList(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new ArrayList<>();
        }

        try {
            List<RecipeStepMetadata> metadataList = gson.fromJson(json, LIST_TYPE);

            if (ApiConfig.DEBUG_LOGGING && metadataList != null) {
                Log.d(TAG, "Converted JSON to RecipeStepMetadata list with " +
                        metadataList.size() + " steps");

                // Validate step numbering
                if (!isValidStepSequence(metadataList)) {
                    Log.w(TAG, "RecipeStepMetadata sequence warning: steps may not be sequential");
                }
            }

            return metadataList != null ? new ArrayList<>(metadataList) : new ArrayList<>();

        } catch (Exception e) {
            Log.e(TAG, "Failed to convert JSON to RecipeStepMetadata list", e);
            return new ArrayList<>();
        }
    }

    // ========== VALIDATION HELPERS ==========

    /**
     * Validate that steps have proper sequential numbering (1, 2, 3, ...)
     */
    private static boolean isValidStepSequence(List<RecipeStepMetadata> metadataList) {
        if (metadataList == null || metadataList.isEmpty()) {
            return true;
        }

        for (int i = 0; i < metadataList.size(); i++) {
            RecipeStepMetadata metadata = metadataList.get(i);
            if (metadata.getStepNumber() != i + 1) {
                return false;
            }
        }
        return true;
    }

    /**
     * Check if metadata list is empty
     */
    public static boolean isEmpty(List<RecipeStepMetadata> metadataList) {
        return metadataList == null || metadataList.isEmpty();
    }

    /**
     * Get total duration from metadata list
     * Returns null if any step has unknown duration
     */
    public static Integer getTotalDuration(List<RecipeStepMetadata> metadataList) {
        if (metadataList == null || metadataList.isEmpty()) {
            return 0;
        }

        int total = 0;
        for (RecipeStepMetadata metadata : metadataList) {
            if (metadata.getDurationMinutes() == null) {
                return null; // Can't calculate if any step unknown
            }
            total += metadata.getDurationMinutes();
        }
        return total;
    }

    /**
     * Count how many steps are optional
     */
    public static int getOptionalStepCount(List<RecipeStepMetadata> metadataList) {
        if (metadataList == null || metadataList.isEmpty()) {
            return 0;
        }

        int count = 0;
        for (RecipeStepMetadata metadata : metadataList) {
            if (metadata.isOptional()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Count how many steps are critical
     */
    public static int getCriticalStepCount(List<RecipeStepMetadata> metadataList) {
        if (metadataList == null || metadataList.isEmpty()) {
            return 0;
        }

        int count = 0;
        for (RecipeStepMetadata metadata : metadataList) {
            if (metadata.isCritical()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Count steps with media (images/videos)
     */
    public static int getStepsWithMediaCount(List<RecipeStepMetadata> metadataList) {
        if (metadataList == null || metadataList.isEmpty()) {
            return 0;
        }

        int count = 0;
        for (RecipeStepMetadata metadata : metadataList) {
            if (metadata.hasMedia()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Find metadata by step number
     */
    public static RecipeStepMetadata findByStepNumber(List<RecipeStepMetadata> metadataList, int stepNumber) {
        if (metadataList == null || metadataList.isEmpty()) {
            return null;
        }

        for (RecipeStepMetadata metadata : metadataList) {
            if (metadata.getStepNumber() == stepNumber) {
                return metadata;
            }
        }
        return null;
    }

    /**
     * Test if JSON is valid RecipeStepMetadata list structure
     */
    public static boolean isValidJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return true;
        }

        try {
            List<RecipeStepMetadata> list = gson.fromJson(json, LIST_TYPE);
            return list != null;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Estimate JSON size for metadata list
     */
    public static int estimateJsonSize(List<RecipeStepMetadata> metadataList) {
        if (metadataList == null || metadataList.isEmpty()) {
            return 0;
        }
        // Metadata is compact: ~80-120 bytes per step
        return metadataList.size() * 100;
    }
}