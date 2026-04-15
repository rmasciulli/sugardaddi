package li.masciul.sugardaddi.data.database.converters;

import androidx.room.TypeConverter;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import li.masciul.sugardaddi.core.models.ServingSize;
import li.masciul.sugardaddi.data.network.ApiConfig;
import android.util.Log;

/**
 * ServingSizeConverter - Converts ServingSize objects for Room storage
 */
public class ServingSizeConverter {

    private static final String TAG = ApiConfig.DATABASE_LOG_TAG;

    private static final Gson gson = new GsonBuilder()
            .serializeNulls()
            .create();

    @TypeConverter
    public static ServingSize fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        try {
            return gson.fromJson(value, ServingSize.class);
        } catch (Exception e) {
            Log.e(TAG, "Failed to convert JSON to ServingSize", e);
            return null;
        }
    }

    @TypeConverter
    public static String fromServingSize(ServingSize servingSize) {
        if (servingSize == null) {
            return null;
        }

        try {
            return gson.toJson(servingSize);
        } catch (Exception e) {
            Log.e(TAG, "Failed to convert ServingSize to JSON", e);
            return null;
        }
    }
}