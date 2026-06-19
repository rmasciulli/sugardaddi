package li.masciul.sugardaddi.data.database;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
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
 * The project has no shipped users and the schema is still evolving, so there
 * are deliberately NO hand-written Room migrations. The builder uses
 * fallbackToDestructiveMigration(): any version bump recreates the schema from
 * scratch and clears all local data. Updating or reinstalling the app after a
 * schema change therefore resets the local database. Formal migrations return
 * once the schema stabilises toward v1.0.
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
                RecipeEntity.class         // Recipes (user-created + external)
        },
        version = 14,        // keep getDatabaseVersion() in sync
        exportSchema = true
)
@TypeConverters({
        GeneralConverters.class,
        ServingSizeConverter.class,
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
                // Alpha policy: no hand-written migrations - recreate on version bump.
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
        return 14;
    }

    /** Database file name. */
    public static String getDatabaseName() {
        return DATABASE_NAME;
    }
}