package li.masciul.sugardaddi.data.database;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import li.masciul.sugardaddi.data.database.converters.*;
import li.masciul.sugardaddi.data.database.dao.*;
import li.masciul.sugardaddi.data.database.entities.*;
import li.masciul.sugardaddi.data.network.ApiConfig;

import java.util.concurrent.Executors;

/**
 * AppDatabase - Main Room database configuration (v10.0 - Recipe videoUrl)
 *
 * ARCHITECTURE UPDATE v6.0:
 * - Added allergenFlags persistence to FoodProductEntity, RecipeEntity, MealEntity
 * - Enables efficient database-level allergen filtering
 * - Safety-first approach: treats traces same as definite allergens
 *
 * ARCHITECTURE UPDATE v5.0:
 * - Replaced LocalizedContent with hybrid translation system
 * - 85% reduction in translation storage overhead
 * - New converters for ProductTranslation, RecipeTranslation, MealTranslation
 * - Split RecipeStep into metadata + translation for efficiency
 * - All entities support multi-language with primary + translation map
 *
 * CLEAN ARCHITECTURE v5/v6:
 * - Dual table storage for ALL entities (products, meals, recipes + nutrition)
 * - Nutrition stored separately for powerful queries
 * - Relations handle joins automatically
 * - Hybrid translation with currentLanguage + translation map
 * - Allergen bit flags for efficient safety filtering
 */
@Database(
        entities = {
                // ===== EXISTING ENTITIES =====
                FoodProductEntity.class,           // Product metadata (v3.0 - hybrid translation + allergens)
                NutritionEntity.class,             // Separated nutrition storage
                MealEntity.class,                  // Meal tracking (v3.0 - hybrid translation + allergens)
                RecipeEntity.class                 // Recipe storage (v3.0 - hybrid translation + split steps + allergens)

        },
        version = 11, // Add image system paths (localImagePath, photoPath)
        exportSchema = true
)
@TypeConverters({
        // Core converters
        GeneralConverters.class,
        ServingSizeConverter.class,

        // v5.0: Hybrid translation converters
        ProductTranslationMapConverter.class,
        RecipeTranslationMapConverter.class,
        MealTranslationMapConverter.class,

        // v5.0: Recipe step converters
        RecipeStepMetadataListConverter.class,
        RecipeStepTranslationListConverter.class,

        // v8.0 DataConfidenceCode to DataConfidence
        DataConfidenceConverter.class
})

public abstract class AppDatabase extends RoomDatabase {

    private static final String TAG = ApiConfig.DATABASE_LOG_TAG;
    private static final String DATABASE_NAME = "sugardaddi_database";

    private static volatile AppDatabase INSTANCE;
    private static final Object LOCK = new Object();

    // ========== DAO DECLARATIONS ==========

    public abstract FoodProductDao foodProductDao();
    public abstract NutritionDao nutritionDao();
    public abstract CombinedProductDao combinedProductDao();
    public abstract MealDao mealDao();
    public abstract RecipeDao recipeDao();

    // ========== SINGLETON PATTERN ==========

    public static AppDatabase getInstance(@NonNull Context context) {
        if (INSTANCE == null) {
            synchronized (LOCK) {
                if (INSTANCE == null) {
                    if (ApiConfig.DEBUG_LOGGING) {
                        Log.d(TAG, "Creating new database instance (v10)");
                        Log.d(TAG, "Database created (v10)");
                        Log.d(TAG, "Database opened (v10)");

                    }

                    INSTANCE = buildDatabase(context.getApplicationContext());

                    if (ApiConfig.DEBUG_LOGGING) {
                        Log.d(TAG, "Database instance created successfully");
                    }
                }
            }
        }
        return INSTANCE;
    }

    private static AppDatabase buildDatabase(Context context) {
        return Room.databaseBuilder(
                        context,
                        AppDatabase.class,
                        DATABASE_NAME)
                .addMigrations(MIGRATION_4_5, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
                .fallbackToDestructiveMigration()
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .setQueryExecutor(Executors.newFixedThreadPool(4))
                .setTransactionExecutor(Executors.newSingleThreadExecutor())
                .addCallback(new RoomDatabase.Callback() {
                    @Override
                    public void onCreate(@NonNull SupportSQLiteDatabase db) {
                        super.onCreate(db);
                        if (ApiConfig.DEBUG_LOGGING) {
                            Log.d(TAG, "Database created (v10)");
                        }
                    }

                    @Override
                    public void onOpen(@NonNull SupportSQLiteDatabase db) {
                        super.onOpen(db);
                        if (ApiConfig.DEBUG_LOGGING) {
                            Log.d(TAG, "Database opened (v10)");
                        }
                    }
                })
                .build();
    }

    // ========== MIGRATIONS ==========

    /**
     * Migration from v4 to v5: Hybrid Translation Refactor
     *
     * CHANGES:
     * - FoodProductEntity: Add hybrid translation fields, remove localizedContentMap
     * - RecipeEntity: Add hybrid translation fields, split steps, remove localizedContentJson
     * - MealEntity: Add hybrid translation fields, remove localizedContentJson
     *
     * WARNING: This migration causes data loss for existing translations!
     * For production, implement proper data migration logic.
     */
    static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            Log.w(TAG, "Migrating database from v4 to v5 (hybrid translation refactor)");
            Log.w(TAG, "WARNING: This migration will cause data loss for existing data!");

            // ========== FOOD PRODUCTS TABLE ==========

            // Add new columns for hybrid translation
            database.execSQL("ALTER TABLE food_products ADD COLUMN currentLanguage TEXT NOT NULL DEFAULT 'en'");
            database.execSQL("ALTER TABLE food_products ADD COLUMN needsDefaultLanguageUpdate INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE food_products ADD COLUMN searchable_text TEXT");
            database.execSQL("ALTER TABLE food_products ADD COLUMN translations TEXT");

            // Add primary content fields
            database.execSQL("ALTER TABLE food_products ADD COLUMN name TEXT");
            database.execSQL("ALTER TABLE food_products ADD COLUMN genericName TEXT");
            database.execSQL("ALTER TABLE food_products ADD COLUMN brand TEXT");
            database.execSQL("ALTER TABLE food_products ADD COLUMN description TEXT");
            database.execSQL("ALTER TABLE food_products ADD COLUMN ingredients TEXT");
            database.execSQL("ALTER TABLE food_products ADD COLUMN categoriesText TEXT");
            database.execSQL("ALTER TABLE food_products ADD COLUMN packaging TEXT");
            database.execSQL("ALTER TABLE food_products ADD COLUMN origins TEXT");
            database.execSQL("ALTER TABLE food_products ADD COLUMN stores TEXT");

            // Remove old column (after copying data if needed)
            // Note: SQLite doesn't support DROP COLUMN directly in older versions
            // For now, just leave localizedContentMap column unused
            // In production, you'd need to recreate the table

            Log.d(TAG, "FoodProducts table updated for hybrid translation");

            // ========== RECIPES TABLE ==========

            // Add new columns for hybrid translation
            database.execSQL("ALTER TABLE recipes ADD COLUMN currentLanguage TEXT NOT NULL DEFAULT 'en'");
            database.execSQL("ALTER TABLE recipes ADD COLUMN needsDefaultLanguageUpdate INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE recipes ADD COLUMN searchable_text TEXT");
            database.execSQL("ALTER TABLE recipes ADD COLUMN translations TEXT");

            // Add primary content fields
            database.execSQL("ALTER TABLE recipes ADD COLUMN name TEXT");
            database.execSQL("ALTER TABLE recipes ADD COLUMN description TEXT");
            database.execSQL("ALTER TABLE recipes ADD COLUMN instructions TEXT");
            database.execSQL("ALTER TABLE recipes ADD COLUMN cuisine TEXT");
            database.execSQL("ALTER TABLE recipes ADD COLUMN notes TEXT");
            database.execSQL("ALTER TABLE recipes ADD COLUMN yieldDescription TEXT");
            database.execSQL("ALTER TABLE recipes ADD COLUMN recipeSource TEXT");
            database.execSQL("ALTER TABLE recipes ADD COLUMN equipmentNeeded TEXT");
            database.execSQL("ALTER TABLE recipes ADD COLUMN cookingTips TEXT");

            // Add step architecture fields
            database.execSQL("ALTER TABLE recipes ADD COLUMN stepStructure TEXT");
            database.execSQL("ALTER TABLE recipes ADD COLUMN stepTranslations TEXT");

            Log.d(TAG, "Recipes table updated for hybrid translation");

            // ========== MEALS TABLE ==========

            // Add new columns for hybrid translation
            database.execSQL("ALTER TABLE meals ADD COLUMN currentLanguage TEXT NOT NULL DEFAULT 'en'");
            database.execSQL("ALTER TABLE meals ADD COLUMN needsDefaultLanguageUpdate INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE meals ADD COLUMN searchable_text TEXT");
            database.execSQL("ALTER TABLE meals ADD COLUMN translations TEXT");

            // Add primary content fields
            database.execSQL("ALTER TABLE meals ADD COLUMN name TEXT");
            database.execSQL("ALTER TABLE meals ADD COLUMN description TEXT");
            database.execSQL("ALTER TABLE meals ADD COLUMN notes TEXT");
            database.execSQL("ALTER TABLE meals ADD COLUMN occasion TEXT");
            database.execSQL("ALTER TABLE meals ADD COLUMN location TEXT");

            Log.d(TAG, "Meals table updated for hybrid translation");

            Log.i(TAG, "Migration from v4 to v5 completed");
            Log.w(TAG, "Existing translation data has been lost - database must be repopulated");
        }
    };

    static final Migration MIGRATION_7_8 = new Migration(7, 8) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // Add the new typed column
            database.execSQL(
                    "ALTER TABLE nutrition ADD COLUMN dataConfidence TEXT");

            // Migrate data from the old string code to the new enum string
            database.execSQL(
                    "UPDATE nutrition SET dataConfidence = CASE " +
                            "  WHEN dataConfidenceCode IN ('A','B') THEN 'SCIENTIFIC' " +
                            "  WHEN dataConfidenceCode IN ('C','D') THEN 'ESTIMATED' " +
                            "  ELSE NULL " +
                            "END");

            // Drop the old index — SQLite supports DROP INDEX even on older versions.
            // The dataConfidenceCode column itself cannot be dropped (requires SQLite 3.35
            // / Android 12+) so it stays as unused dead weight, which Room tolerates
            // for undeclared columns. Indexes however ARE validated by Room's schema
            // checker, so this drop is required.
            database.execSQL("DROP INDEX IF EXISTS index_nutrition_dataConfidenceCode");
        }
    };

    /**
     * Migration from v8 to v9: Recipe source identification
     *
     * CHANGES:
     * - recipes: Add dataSource TEXT NOT NULL DEFAULT 'USER'
     * - recipes: Add originalId TEXT (nullable)
     * - recipes: Add sourceId TEXT (nullable)
     * - recipes: Add index on dataSource for efficient source-filtered queries
     *
     * DEFAULT 'USER' ensures all existing user-created recipes retain correct
     * source attribution after migration — no data loss, no manual fixup needed.
     */
    static final Migration MIGRATION_8_9 = new Migration(8, 9) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            Log.i(TAG, "Migrating database from v8 to v9 (recipe source identification)");

            // Add source identification columns
            database.execSQL(
                    "ALTER TABLE recipes ADD COLUMN dataSource TEXT NOT NULL DEFAULT 'USER'"
            );
            database.execSQL(
                    "ALTER TABLE recipes ADD COLUMN originalId TEXT"
            );
            database.execSQL(
                    "ALTER TABLE recipes ADD COLUMN sourceId TEXT"
            );

            // Index on dataSource — used by getByDataSource() DAO query
            database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_recipes_dataSource ON recipes(dataSource)"
            );

            Log.i(TAG, "Migration v8→v9 complete: source identification added to recipes");
        }
    };

    /**
     * Migration from v9 to v10: Recipe video URL
     *
     * CHANGES:
     * - recipes: Add videoUrl TEXT (nullable)
     *
     * Stores the external video link for a recipe (e.g. YouTube URL from TheMealDB).
     * NULL for user-created recipes and any external recipe that has no video.
     */
    static final Migration MIGRATION_9_10 = new Migration(9, 10) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            Log.i(TAG, "Migrating database from v9 to v10 (recipe videoUrl)");

            database.execSQL(
                    "ALTER TABLE recipes ADD COLUMN videoUrl TEXT"
            );

            Log.i(TAG, "Migration v9→v10 complete: videoUrl added to recipes");
        }
    };

    /**
     * Migration from v10 to v11: Image system
     *
     * CHANGES:
     * - food_products: Add localImagePath TEXT (nullable)
     *   Stores path to locally cached thumbnail or user-added hero image.
     * - recipes:       Add localImagePath TEXT (nullable)
     *   Same as above for recipes.
     * - meals:         Add photoPath TEXT (nullable)
     *   Stores path to user-attached meal journal photo.
     * - RecipeStepMetadata.photoPath is NOT a column — it lives inside the
     *   stepStructure JSON blob. No ALTER TABLE needed; Gson deserialises
     *   the new field as null for all existing rows automatically.
     */
    static final Migration MIGRATION_10_11 = new Migration(10, 11) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            Log.i(TAG, "Migrating database from v10 to v11 (image system)");

            // food_products: local image path (thumbnail or hero image)
            database.execSQL(
                    "ALTER TABLE food_products ADD COLUMN localImagePath TEXT"
            );

            // recipes: local image path (thumbnail or hero image)
            database.execSQL(
                    "ALTER TABLE recipes ADD COLUMN localImagePath TEXT"
            );

            // meals: user-attached photo path
            database.execSQL(
                    "ALTER TABLE meals ADD COLUMN photoPath TEXT"
            );

            // NOTE: RecipeStepMetadata.photoPath requires no ALTER TABLE.
            // It is serialised inside recipes.stepStructure (JSON blob).
            // Gson silently deserialises missing JSON keys as null — existing
            // rows are unaffected and no data migration is needed.

            Log.i(TAG, "Migration v10→v11 complete: image paths added");
        }
    };

    // ========== UTILITY METHODS ==========

    /**
     * Close database and clear instance (for testing)
     */
    public static void destroyInstance() {
        if (INSTANCE != null) {
            if (INSTANCE.isOpen()) {
                INSTANCE.close();
            }
            INSTANCE = null;
            if (ApiConfig.DEBUG_LOGGING) {
                Log.d(TAG, "Database instance destroyed");
            }
        }
    }

    /**
     * Check if database is ready
     */
    public boolean isDatabaseReady() {
        return isOpen();
    }

    /**
     * Get database version
     */
    public static int getDatabaseVersion() {
        return 11;
    }

    /**
     * Get database name
     */
    public static String getDatabaseName() {
        return DATABASE_NAME;
    }
}