package li.masciul.sugardaddi.data.database.converters;

import androidx.room.TypeConverter;

import li.masciul.sugardaddi.core.enums.DataConfidence;

/**
 * DataConfidenceConverter — Room TypeConverter for {@link DataConfidence}.
 *
 * Stores the enum as its name String (e.g. "SCIENTIFIC") in the database.
 * Null-safe in both directions — null DB value reads back as null,
 * not as a fallback value (that logic lives in DataConfidence.fromString()).
 *
 * Register in AppDatabase via @TypeConverters({..., DataConfidenceConverter.class})
 */
public class DataConfidenceConverter {

    @TypeConverter
    public static String fromDataConfidence(DataConfidence confidence) {
        return confidence != null ? confidence.name() : null;
    }

    @TypeConverter
    public static DataConfidence toDataConfidence(String value) {
        return value != null ? DataConfidence.fromString(value) : null;
    }
}