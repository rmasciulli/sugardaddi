package li.masciul.sugardaddi.data.database.converters;

import androidx.room.TypeConverter;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import li.masciul.sugardaddi.core.models.RecipeStepTranslation;
import li.masciul.sugardaddi.data.network.ApiConfig;
import android.util.Log;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * RecipeStepTranslationListConverter - Converts List<RecipeStepTranslation> for Room storage
 *
 * PURPOSE:
 * - Store translatable text for recipe steps (instructions, tips)
 * - Stored PER LANGUAGE (in Recipe and RecipeTranslation)
 * - Paired with RecipeStepMetadata for complete step display
 *
 * ARCHITECTURE:
 * - Recipe.stepTranslations (primary language): List<RecipeStepTranslation>
 * - RecipeTranslation.stepTranslations (other languages): List<RecipeStepTranslation>
 * - References Recipe.stepStructure via stepNumber
 *
 * STORAGE PATTERN:
 * Recipe with 10 steps in English + French:
 *   Recipe.stepStructure: [Metadata(1), ..., Metadata(10)]  ← ONCE
 *   Recipe.stepTranslations (en): [Translation(1), ..., Translation(10)]
 *   RecipeTranslation(fr).stepTranslations: [Translation(1), ..., Translation(10)]
 *
 * EXAMPLE JSON:
 * [
 *   {
 *     "stepNumber": 1,
 *     "instruction": "Preheat oven to 350°F",
 *     "tip": "Make sure oven is fully preheated",
 *     "verified": true
 *   },
 *   {
 *     "stepNumber": 2,
 *     "instruction": "Mix flour, sugar, and butter in a large bowl",
 *     "tip": "Use room temperature butter for easier mixing"
 *   }
 * ]
 */
public class RecipeStepTranslationListConverter {

    private static final String TAG = ApiConfig.DATABASE_LOG_TAG;

    private static final Gson gson = new GsonBuilder()
            .serializeNulls()
            .create();

    private static final Type LIST_TYPE = new TypeToken<List<RecipeStepTranslation>>(){}.getType();

    // ========== SERIALIZATION ==========

    /**
     * Convert List<RecipeStepTranslation> to JSON for database storage
     *
     * @param translations List of step translations for one language
     * @return JSON array string, or null if list is empty/null
     */
    @TypeConverter
    public static String fromList(List<RecipeStepTranslation> translations) {
        if (translations == null || translations.isEmpty()) {
            return null;
        }

        try {
            String json = gson.toJson(translations, LIST_TYPE);

            if (ApiConfig.DEBUG_LOGGING) {
                Log.d(TAG, "Converted RecipeStepTranslation list to JSON with " +
                        translations.size() + " steps (size: " + json.length() + " bytes)");
            }

            return json;

        } catch (Exception e) {
            Log.e(TAG, "Failed to convert RecipeStepTranslation list to JSON", e);
            if (ApiConfig.DEBUG_LOGGING) {
                Log.e(TAG, "List contained " + translations.size() + " translations");
            }
            return null;
        }
    }

    // ========== DESERIALIZATION ==========

    /**
     * Convert JSON string from database to List<RecipeStepTranslation>
     *
     * @param json JSON array string from database
     * @return List of RecipeStepTranslations, or empty list if parsing fails
     */
    @TypeConverter
    public static List<RecipeStepTranslation> toList(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new ArrayList<>();
        }

        try {
            List<RecipeStepTranslation> translations = gson.fromJson(json, LIST_TYPE);

            if (ApiConfig.DEBUG_LOGGING && translations != null) {
                Log.d(TAG, "Converted JSON to RecipeStepTranslation list with " +
                        translations.size() + " steps");

                // Validate step numbering
                if (!isValidStepSequence(translations)) {
                    Log.w(TAG, "RecipeStepTranslation sequence warning: steps may not be sequential");
                }

                // Count incomplete translations
                int incomplete = countIncomplete(translations);
                if (incomplete > 0) {
                    Log.w(TAG, "Found " + incomplete + " incomplete translations (missing instruction text)");
                }
            }

            return translations != null ? new ArrayList<>(translations) : new ArrayList<>();

        } catch (Exception e) {
            Log.e(TAG, "Failed to convert JSON to RecipeStepTranslation list", e);
            return new ArrayList<>();
        }
    }

    // ========== VALIDATION HELPERS ==========

    /**
     * Validate that translations have proper sequential numbering
     */
    private static boolean isValidStepSequence(List<RecipeStepTranslation> translations) {
        if (translations == null || translations.isEmpty()) {
            return true;
        }

        for (int i = 0; i < translations.size(); i++) {
            RecipeStepTranslation translation = translations.get(i);
            if (translation.getStepNumber() != i + 1) {
                return false;
            }
        }
        return true;
    }

    /**
     * Count incomplete translations (missing instruction text)
     */
    private static int countIncomplete(List<RecipeStepTranslation> translations) {
        if (translations == null || translations.isEmpty()) {
            return 0;
        }

        int count = 0;
        for (RecipeStepTranslation translation : translations) {
            if (!translation.isComplete()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Check if translation list is empty
     */
    public static boolean isEmpty(List<RecipeStepTranslation> translations) {
        return translations == null || translations.isEmpty();
    }

    /**
     * Find translation by step number
     */
    public static RecipeStepTranslation findByStepNumber(List<RecipeStepTranslation> translations, int stepNumber) {
        if (translations == null || translations.isEmpty()) {
            return null;
        }

        for (RecipeStepTranslation translation : translations) {
            if (translation.getStepNumber() == stepNumber) {
                return translation;
            }
        }
        return null;
    }

    /**
     * Count translations with tips
     */
    public static int getTranslationsWithTipsCount(List<RecipeStepTranslation> translations) {
        if (translations == null || translations.isEmpty()) {
            return 0;
        }

        int count = 0;
        for (RecipeStepTranslation translation : translations) {
            if (translation.hasTip()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Count verified translations
     */
    public static int getVerifiedCount(List<RecipeStepTranslation> translations) {
        if (translations == null || translations.isEmpty()) {
            return 0;
        }

        int count = 0;
        for (RecipeStepTranslation translation : translations) {
            if (translation.isVerified()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Get total instruction text length (useful for UI optimization)
     */
    public static int getTotalTextLength(List<RecipeStepTranslation> translations) {
        if (translations == null || translations.isEmpty()) {
            return 0;
        }

        int total = 0;
        for (RecipeStepTranslation translation : translations) {
            total += translation.getInstructionLength();
            if (translation.getTip() != null) {
                total += translation.getTip().length();
            }
        }
        return total;
    }

    /**
     * Test if JSON is valid RecipeStepTranslation list structure
     */
    public static boolean isValidJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return true;
        }

        try {
            List<RecipeStepTranslation> list = gson.fromJson(json, LIST_TYPE);
            return list != null;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Estimate JSON size for translation list
     */
    public static int estimateJsonSize(List<RecipeStepTranslation> translations) {
        if (translations == null || translations.isEmpty()) {
            return 0;
        }
        // Translation text varies widely: ~100-300 bytes per step
        return translations.size() * 200;
    }
}