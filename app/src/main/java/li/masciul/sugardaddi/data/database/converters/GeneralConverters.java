package li.masciul.sugardaddi.data.database.converters;

import androidx.room.TypeConverter;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import li.masciul.sugardaddi.data.network.ApiConfig;
import android.util.Log;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.Date;

/**
 * GeneralConverters - Common type converters for Room database
 *
 * Handles basic types like Lists, Sets, Dates, etc.
 */
public class GeneralConverters {

    private static final String TAG = ApiConfig.DATABASE_LOG_TAG;

    private static final Gson gson = new GsonBuilder()
            .serializeNulls()
            .create();

    // ========== STRING LIST CONVERSION ==========

    @TypeConverter
    public static List<String> stringToList(String value) {
        if (value == null || value.trim().isEmpty()) {
            return new ArrayList<>();
        }

        try {
            Type listType = new TypeToken<List<String>>(){}.getType();
            List<String> result = gson.fromJson(value, listType);
            return result != null ? result : new ArrayList<>();
        } catch (Exception e) {
            Log.e(TAG, "Failed to convert JSON to List<String>", e);
            return new ArrayList<>();
        }
    }

    @TypeConverter
    public static String listToString(List<String> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }

        try {
            return gson.toJson(list);
        } catch (Exception e) {
            Log.e(TAG, "Failed to convert List<String> to JSON", e);
            return null;
        }
    }

    // ========== STRING SET CONVERSION ==========

    @TypeConverter
    public static Set<String> stringToSet(String value) {
        if (value == null || value.trim().isEmpty()) {
            return new HashSet<>();
        }

        try {
            Type setType = new TypeToken<Set<String>>(){}.getType();
            Set<String> result = gson.fromJson(value, setType);
            return result != null ? result : new HashSet<>();
        } catch (Exception e) {
            Log.e(TAG, "Failed to convert JSON to Set<String>", e);
            return new HashSet<>();
        }
    }

    @TypeConverter
    public static String setToString(Set<String> set) {
        if (set == null || set.isEmpty()) {
            return null;
        }

        try {
            return gson.toJson(set);
        } catch (Exception e) {
            Log.e(TAG, "Failed to convert Set<String> to JSON", e);
            return null;
        }
    }

    // ========== DATE CONVERSION ==========

    @TypeConverter
    public static Date timestampToDate(Long timestamp) {
        return timestamp == null ? null : new Date(timestamp);
    }

    @TypeConverter
    public static Long dateToTimestamp(Date date) {
        return date == null ? null : date.getTime();
    }

    // ========== LONG CONVERSION ==========

    @TypeConverter
    public static Long stringToLong(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @TypeConverter
    public static String longToString(Long value) {
        return value == null ? null : value.toString();
    }

    // ========== VALIDATION HELPERS ==========

    /**
     * Test if JSON string is valid
     */
    public static boolean isValidJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return false;
        }

        try {
            gson.fromJson(json, Object.class);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get Gson instance for external use if needed
     */
    public static Gson getGson() {
        return gson;
    }
}