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
 * AppDatabase - main Room database configuration.
 *
 * ALPHA SCHEMA POLICY
 * ===================
 * The project has no shipped users and the schema is still evolving. The
 * builder uses fallbackToDestructiveMigration(): a version bump with no
 * migration for it recreates the schema from scratch and clears all local data.
 *
 * Hand-written migrations are added when a change is small and worth keeping
 * the data for. First one: MIGRATION_21_22 (one nullable column), so meals,
 * favorites and customisations survive the update. Older versions still wipe.
 *
 * NOTE: destructive recreation clears Room rows only - image files cached on
 * disk are NOT removed by Room and must be purged separately.
 *
 * STRUCTURE
 * =========
 * - Dual-table storage: products / recipes / meals each pair with a separate
 *   nutrition table; relations handle the joins.
 * - Hybrid translation: a primary language plus a translation map per entity.
 * - Allergen bit flags for efficient database-level safety filtering.
 */
@Database(
        entities = {
                FoodProductEntity.class,   // Product metadata (nutrition stored separately)
                NutritionEntity.class,     // Shared nutrition storage
                MealEntity.class,          // Meal journal entries
                MealPortionEntity.class,   // One row per logged item per meal
                RecipeEntity.class,        // Recipes (user-created + external)
                UserItemDataEntity.class   // User data on items: favorite, serving, photos
        },
        version = 22,        // keep getDatabaseVersion() in sync - bumped for
                             // user_item_data.preferredUnit (MIGRATION_21_22)
        exportSchema = true
)
@TypeConverters({
        GeneralConverters.class,
        ServingSizeConverter.class,
        ServingSizeListConverter.class,
        ProductTranslationMapConverter.class,
        RecipeTranslationMapConverter.class,
        MealTranslationMapConverter.class,
        RecipeStepMetadataListConverter.class,
        RecipeStepTranslationListConverter.class,
        DataConfidenceConverter.class
})
public abstract class AppDatabase extends RoomDatabase {

    private static final String TAG = ApiConfig.DATABASE_LOG_TAG;
    private static final String DATABASE_NAME = "sugardaddi_database";

    /**
     * v21 -> v22: user_item_data.preferredUnit, the unit (g or ml) the user
     * chose for an item. Nullable TEXT with no default: exactly what Room
     * expects for the entity's @Nullable String field, so its schema check
     * after the migration passes. Existing rows get NULL (the source's unit).
     */
    static final Migration MIGRATION_21_22 = new Migration(21, 22) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE `user_item_data` ADD COLUMN `preferredUnit` TEXT");
        }
    };

    private static volatile AppDatabase INSTANCE;
    private static final Object LOCK = new Object();

    // ========== DAO DECLARATIONS ==========

    public abstract FoodProductDao foodProductDao();
    public abstract NutritionDao nutritionDao();
    public abstract CombinedProductDao combinedProductDao();
    public abstract MealDao mealDao();
    public abstract MealPortionDao mealPortionDao();
    public abstract RecipeDao recipeDao();
    public abstract UserItemDataDao userItemDataDao();

    // ========== SINGLETON PATTERN ==========

    public static AppDatabase getInstance(@NonNull Context context) {
        if (INSTANCE == null) {
            synchronized (LOCK) {
                if (INSTANCE == null) {
                    INSTANCE = buildDatabase(context.getApplicationContext());
                    if (ApiConfig.DEBUG_LOGGING) {
                        Log.d(TAG, "Database instance created (v" + getDatabaseVersion() + ")");
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
                // Known migrations first; any other version bump recreates the
                // schema (alpha policy, see the class comment).
                .addMigrations(MIGRATION_21_22)
                .fallbackToDestructiveMigration()
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .setQueryExecutor(Executors.newFixedThreadPool(4))
                .setTransactionExecutor(Executors.newSingleThreadExecutor())
                .addCallback(new RoomDatabase.Callback() {
                    @Override
                    public void onCreate(@NonNull SupportSQLiteDatabase db) {
                        super.onCreate(db);
                        if (ApiConfig.DEBUG_LOGGING) {
                            Log.d(TAG, "Database created");
                        }
                    }

                    @Override
                    public void onOpen(@NonNull SupportSQLiteDatabase db) {
                        super.onOpen(db);
                        if (ApiConfig.DEBUG_LOGGING) {
                            Log.d(TAG, "Database opened");
                        }
                    }
                })
                .build();
    }

    // ========== UTILITY METHODS ==========

    /** Close database and clear instance (for testing). */
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

    /** True if the database is open and ready. */
    public boolean isDatabaseReady() {
        return isOpen();
    }

    /** Current schema version. Keep in sync with the @Database version above. */
    public static int getDatabaseVersion() {
        return 22;
    }

    /** Database file name. */
    public static String getDatabaseName() {
        return DATABASE_NAME;
    }
}