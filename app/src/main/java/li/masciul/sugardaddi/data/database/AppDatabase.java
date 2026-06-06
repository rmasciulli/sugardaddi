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
 * AppDatabase - Main Room database configuration (v13.0 - Media field rename + expansion)
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
        version = 13, // Media field rename + expansion (v13)
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
                        Log.d(TAG, "Creating new database instance (v12)");
                        Log.d(TAG, "Database created (v12)");
                        Log.d(TAG, "Database opened (v12)");

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
                .addMigrations(MIGRATION_4_5, MIGRATION_7_8, MIGRATION_8_9,
                               MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12,
                               MIGRATION_12_13)
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

    /**
     * Migration from v11 to v12: Image system field rename + expansion.
     *
     * CHANGES:
     * - food_products: localImagePath → thumbnailPath + heroImagePath
     * - recipes:       localImagePath → thumbnailPath + heroImagePath
     * - meals:         localImagePath → photoPath
     * - RecipeStepMetadata.stepPhotoPath: Java rename only, no SQL needed
     */
    static final Migration MIGRATION_11_12 = new Migration(11, 12) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            Log.i(TAG, "Migrating database from v11 to v12 (image field rename + expansion)");

            migrateFoodProducts(database);
            migrateRecipes(database);
            migrateMeals(database);

            Log.i(TAG, "Migration v11→v12 complete");
        }

        // ── food_products ─────────────────────────────────────────────────────

        private void migrateFoodProducts(@NonNull SupportSQLiteDatabase db) {
            // Step 1: Create new table with correct schema.
            // localImagePath removed; thumbnailPath + heroImagePath added.
            // All other columns, constraints, and column order are preserved
            // exactly from the v11 schema JSON createSql.
            db.execSQL("CREATE TABLE IF NOT EXISTS `food_products_new` ("
                    + "`id` TEXT NOT NULL, "
                    + "`barcode` TEXT, "
                    + "`originalId` TEXT, "
                    + "`sourceId` TEXT, "
                    + "`productType` TEXT, "
                    + "`scientific_name` TEXT, "
                    + "`category_code` TEXT, "
                    + "`name` TEXT, "
                    + "`genericName` TEXT, "
                    + "`brand` TEXT, "
                    + "`description` TEXT, "
                    + "`ingredients` TEXT, "
                    + "`categoriesText` TEXT, "
                    + "`packaging` TEXT, "
                    + "`origins` TEXT, "
                    + "`stores` TEXT, "
                    + "`currentLanguage` TEXT, "
                    + "`needsDefaultLanguageUpdate` INTEGER NOT NULL, "
                    + "`translations` TEXT, "
                    + "`searchableText` TEXT, "
                    + "`imageUrl` TEXT, "
                    + "`imageThumbnailUrl` TEXT, "
                    + "`thumbnailPath` TEXT, "       // replaces localImagePath (thumbnail role)
                    + "`heroImagePath` TEXT, "       // new: user hero image
                    + "`nutriScore` TEXT, "
                    + "`ecoScore` TEXT, "
                    + "`novaGroup` TEXT, "
                    + "`quantity` TEXT, "
                    + "`isLiquid` INTEGER NOT NULL, "
                    + "`density` REAL, "
                    + "`isOrganic` INTEGER NOT NULL, "
                    + "`isVegan` INTEGER NOT NULL, "
                    + "`isVegetarian` INTEGER NOT NULL, "
                    + "`isGlutenFree` INTEGER NOT NULL, "
                    + "`isPalmOilFree` INTEGER NOT NULL, "
                    + "`isFairTrade` INTEGER NOT NULL, "
                    + "`allergenFlags` INTEGER NOT NULL, "
                    + "`servingSize` TEXT, "
                    + "`tags` TEXT, "
                    + "`dataCompleteness` REAL NOT NULL, "
                    + "`dataQualityScore` INTEGER NOT NULL, "
                    + "`isFavorite` INTEGER NOT NULL, "
                    + "`accessCount` INTEGER NOT NULL, "
                    + "`lastUpdated` INTEGER NOT NULL, "
                    + "`createdAt` INTEGER NOT NULL, "
                    + "`updatedAt` INTEGER NOT NULL, "
                    + "PRIMARY KEY(`id`))");

            // Step 2: Copy all data. localImagePath was always NULL in v11
            // so thumbnailPath and heroImagePath both start NULL — correct.
            db.execSQL("INSERT INTO food_products_new ("
                    + "id, barcode, originalId, sourceId, productType, scientific_name, "
                    + "category_code, name, genericName, brand, description, ingredients, "
                    + "categoriesText, packaging, origins, stores, currentLanguage, "
                    + "needsDefaultLanguageUpdate, translations, searchableText, "
                    + "imageUrl, imageThumbnailUrl, "
                    + "thumbnailPath, heroImagePath, "   // both NULL (localImagePath was NULL)
                    + "nutriScore, ecoScore, novaGroup, quantity, isLiquid, density, "
                    + "isOrganic, isVegan, isVegetarian, isGlutenFree, isPalmOilFree, "
                    + "isFairTrade, allergenFlags, servingSize, tags, dataCompleteness, "
                    + "dataQualityScore, isFavorite, accessCount, lastUpdated, createdAt, updatedAt"
                    + ") SELECT "
                    + "id, barcode, originalId, sourceId, productType, scientific_name, "
                    + "category_code, name, genericName, brand, description, ingredients, "
                    + "categoriesText, packaging, origins, stores, currentLanguage, "
                    + "needsDefaultLanguageUpdate, translations, searchableText, "
                    + "imageUrl, imageThumbnailUrl, "
                    + "NULL, NULL, "                     // thumbnailPath, heroImagePath
                    + "nutriScore, ecoScore, novaGroup, quantity, isLiquid, density, "
                    + "isOrganic, isVegan, isVegetarian, isGlutenFree, isPalmOilFree, "
                    + "isFairTrade, allergenFlags, servingSize, tags, dataCompleteness, "
                    + "dataQualityScore, isFavorite, accessCount, lastUpdated, createdAt, updatedAt "
                    + "FROM food_products");

            // Step 3: Drop old table and rename new one.
            db.execSQL("DROP TABLE food_products");
            db.execSQL("ALTER TABLE food_products_new RENAME TO food_products");

            // Step 4: Recreate all indices from v11 schema JSON.
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_food_products_barcode` "
                    + "ON `food_products` (`barcode`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_food_products_sourceId_originalId` "
                    + "ON `food_products` (`sourceId`, `originalId`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_food_products_isFavorite` "
                    + "ON `food_products` (`isFavorite`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_food_products_lastUpdated` "
                    + "ON `food_products` (`lastUpdated`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_food_products_accessCount` "
                    + "ON `food_products` (`accessCount`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_food_products_category_code` "
                    + "ON `food_products` (`category_code`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_food_products_sourceId_category_code` "
                    + "ON `food_products` (`sourceId`, `category_code`)");

            Log.d(TAG, "  food_products migrated");
        }

        // ── recipes ───────────────────────────────────────────────────────────

        private void migrateRecipes(@NonNull SupportSQLiteDatabase db) {
            // Step 1: Create new table.
            // localImagePath removed; thumbnailPath + heroImagePath added.
            // Column order and all constraints match v11 schema JSON createSql.
            db.execSQL("CREATE TABLE IF NOT EXISTS `recipes_new` ("
                    + "`id` TEXT NOT NULL, "
                    + "`authorId` TEXT, "
                    + "`dataSource` TEXT NOT NULL, "
                    + "`originalId` TEXT, "
                    + "`sourceId` TEXT, "
                    + "`name` TEXT, "
                    + "`description` TEXT, "
                    + "`instructions` TEXT, "
                    + "`cuisine` TEXT, "
                    + "`notes` TEXT, "
                    + "`yieldDescription` TEXT, "
                    + "`recipeSource` TEXT, "
                    + "`equipmentNeeded` TEXT, "
                    + "`cookingTips` TEXT, "
                    + "`stepStructure` TEXT, "
                    + "`stepTranslations` TEXT, "
                    + "`currentLanguage` TEXT, "
                    + "`needsDefaultLanguageUpdate` INTEGER NOT NULL, "
                    + "`translations` TEXT, "
                    + "`searchableText` TEXT, "
                    + "`servings` INTEGER NOT NULL, "
                    + "`prepTimeMinutes` INTEGER NOT NULL, "
                    + "`cookTimeMinutes` INTEGER NOT NULL, "
                    + "`difficulty` TEXT, "
                    + "`portionsJson` TEXT, "
                    + "`isVegan` INTEGER NOT NULL, "
                    + "`isVegetarian` INTEGER NOT NULL, "
                    + "`isGlutenFree` INTEGER NOT NULL, "
                    + "`isDairyFree` INTEGER NOT NULL, "
                    + "`isKeto` INTEGER NOT NULL, "
                    + "`isPaleo` INTEGER NOT NULL, "
                    + "`allergenFlags` INTEGER NOT NULL, "
                    + "`isPublic` INTEGER NOT NULL, "
                    + "`isFavorite` INTEGER NOT NULL, "
                    + "`isTemplate` INTEGER NOT NULL, "
                    + "`rating` REAL NOT NULL, "
                    + "`ratingCount` INTEGER NOT NULL, "
                    + "`imageUrl` TEXT, "
                    + "`videoUrl` TEXT, "
                    + "`thumbnailPath` TEXT, "           // replaces localImagePath (thumbnail role)
                    + "`heroImagePath` TEXT, "           // new: user hero image
                    + "`tagsJson` TEXT, "
                    + "`completenessScore` REAL NOT NULL, "
                    + "`accessCount` INTEGER NOT NULL, "
                    + "`createdAt` INTEGER NOT NULL, "
                    + "`lastUpdated` INTEGER NOT NULL, "
                    + "PRIMARY KEY(`id`))");

            // Step 2: Copy all data.
            db.execSQL("INSERT INTO recipes_new ("
                    + "id, authorId, dataSource, originalId, sourceId, name, description, "
                    + "instructions, cuisine, notes, yieldDescription, recipeSource, "
                    + "equipmentNeeded, cookingTips, stepStructure, stepTranslations, "
                    + "currentLanguage, needsDefaultLanguageUpdate, translations, searchableText, "
                    + "servings, prepTimeMinutes, cookTimeMinutes, difficulty, portionsJson, "
                    + "isVegan, isVegetarian, isGlutenFree, isDairyFree, isKeto, isPaleo, "
                    + "allergenFlags, isPublic, isFavorite, isTemplate, rating, ratingCount, "
                    + "imageUrl, videoUrl, "
                    + "thumbnailPath, heroImagePath, "   // both NULL (localImagePath was NULL)
                    + "tagsJson, completenessScore, accessCount, createdAt, lastUpdated"
                    + ") SELECT "
                    + "id, authorId, dataSource, originalId, sourceId, name, description, "
                    + "instructions, cuisine, notes, yieldDescription, recipeSource, "
                    + "equipmentNeeded, cookingTips, stepStructure, stepTranslations, "
                    + "currentLanguage, needsDefaultLanguageUpdate, translations, searchableText, "
                    + "servings, prepTimeMinutes, cookTimeMinutes, difficulty, portionsJson, "
                    + "isVegan, isVegetarian, isGlutenFree, isDairyFree, isKeto, isPaleo, "
                    + "allergenFlags, isPublic, isFavorite, isTemplate, rating, ratingCount, "
                    + "imageUrl, videoUrl, "
                    + "NULL, NULL, "                     // thumbnailPath, heroImagePath
                    + "tagsJson, completenessScore, accessCount, createdAt, lastUpdated "
                    + "FROM recipes");

            // Step 3: Drop old table and rename.
            db.execSQL("DROP TABLE recipes");
            db.execSQL("ALTER TABLE recipes_new RENAME TO recipes");

            // Step 4: Recreate all indices from v11 schema JSON.
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_recipes_authorId` "
                    + "ON `recipes` (`authorId`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_recipes_dataSource` "
                    + "ON `recipes` (`dataSource`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_recipes_isFavorite` "
                    + "ON `recipes` (`isFavorite`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_recipes_isPublic` "
                    + "ON `recipes` (`isPublic`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_recipes_isTemplate` "
                    + "ON `recipes` (`isTemplate`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_recipes_lastUpdated` "
                    + "ON `recipes` (`lastUpdated`)");

            Log.d(TAG, "  recipes migrated");
        }

        // ── meals ─────────────────────────────────────────────────────────────

        private void migrateMeals(@NonNull SupportSQLiteDatabase db) {
            // Step 1: Create new table.
            // localImagePath removed; photoPath added.
            // Column order and all constraints match v11 schema JSON createSql.
            db.execSQL("CREATE TABLE IF NOT EXISTS `meals_new` ("
                    + "`id` TEXT NOT NULL, "
                    + "`userId` TEXT, "
                    + "`name` TEXT, "
                    + "`description` TEXT, "
                    + "`notes` TEXT, "
                    + "`occasion` TEXT, "
                    + "`location` TEXT, "
                    + "`currentLanguage` TEXT, "
                    + "`needsDefaultLanguageUpdate` INTEGER NOT NULL, "
                    + "`translations` TEXT, "
                    + "`searchableText` TEXT, "
                    + "`mealType` TEXT, "
                    + "`mealDateTime` INTEGER NOT NULL, "
                    + "`isPlanned` INTEGER NOT NULL, "
                    + "`isTemplate` INTEGER NOT NULL, "
                    + "`isHomeMade` INTEGER NOT NULL, "
                    + "`allergenFlags` INTEGER NOT NULL, "
                    + "`estimatedCost` REAL, "
                    + "`satisfaction` REAL NOT NULL, "
                    + "`imageUrl` TEXT, "
                    + "`photoPath` TEXT, "              // replaces localImagePath
                    + "`portionsJson` TEXT, "
                    + "`tagsJson` TEXT, "
                    + "`completenessScore` REAL NOT NULL, "
                    + "`accessCount` INTEGER NOT NULL, "
                    + "`createdAt` INTEGER NOT NULL, "
                    + "`lastUpdated` INTEGER NOT NULL, "
                    + "PRIMARY KEY(`id`))");

            // Step 2: Copy all data.
            db.execSQL("INSERT INTO meals_new ("
                    + "id, userId, name, description, notes, occasion, location, "
                    + "currentLanguage, needsDefaultLanguageUpdate, translations, searchableText, "
                    + "mealType, mealDateTime, isPlanned, isTemplate, isHomeMade, allergenFlags, "
                    + "estimatedCost, satisfaction, imageUrl, "
                    + "photoPath, "                     // NULL (localImagePath was NULL)
                    + "portionsJson, tagsJson, completenessScore, accessCount, createdAt, lastUpdated"
                    + ") SELECT "
                    + "id, userId, name, description, notes, occasion, location, "
                    + "currentLanguage, needsDefaultLanguageUpdate, translations, searchableText, "
                    + "mealType, mealDateTime, isPlanned, isTemplate, isHomeMade, allergenFlags, "
                    + "estimatedCost, satisfaction, imageUrl, "
                    + "NULL, "                          // photoPath
                    + "portionsJson, tagsJson, completenessScore, accessCount, createdAt, lastUpdated "
                    + "FROM meals");

            // Step 3: Drop old table and rename.
            db.execSQL("DROP TABLE meals");
            db.execSQL("ALTER TABLE meals_new RENAME TO meals");

            // Step 4: Recreate all indices from v11 schema JSON.
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_meals_userId` "
                    + "ON `meals` (`userId`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_meals_mealDateTime` "
                    + "ON `meals` (`mealDateTime`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_meals_mealType` "
                    + "ON `meals` (`mealType`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_meals_isTemplate` "
                    + "ON `meals` (`isTemplate`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_meals_lastUpdated` "
                    + "ON `meals` (`lastUpdated`)");

            Log.d(TAG, "  meals migrated");
        }
    };

    /**
     * Migration from v12 to v13: Media field rename + expansion
     *
     * WHAT CHANGES
     * ============
     * food_products:
     *   RENAMED  imageThumbnailUrl  →  thumbnailUrl
     *   RENAMED  heroImagePath      →  userImagePath
     *   ADDED    imagePath          TEXT  (auto-cached full-size, initially NULL)
     *   ADDED    userThumbnailPath  TEXT  (user-defined thumbnail override, initially NULL)
     *
     * recipes:
     *   RENAMED  heroImagePath      →  userImagePath
     *   ADDED    thumbnailUrl       TEXT  (remote thumbnail URL symmetry slot, initially NULL)
     *   ADDED    imagePath          TEXT  (auto-cached full-size, initially NULL)
     *   ADDED    userThumbnailPath  TEXT  (user-defined thumbnail override, initially NULL)
     *
     * meals:
     *   RENAMED  photoPath          →  userImagePath
     *
     * WHY TABLE RECREATION (not ALTER TABLE RENAME COLUMN)
     * ====================================================
     * RENAME COLUMN requires SQLite 3.25+, which maps to Android API 30+.
     * SugarDaddi targets API 26 (Android 8), so we use the safe copy-recreate
     * pattern for all three tables. This is the same pattern used in v12.
     *
     * DATA SAFETY
     * ===========
     * All existing data is copied verbatim. Only column names change — no
     * type changes, no default value changes, no dropped non-null columns.
     * New columns are all nullable TEXT so no DEFAULT is required.
     *
     * NUTRITION TABLE
     * ===============
     * Not touched — no media fields in that table.
     */
    static final Migration MIGRATION_12_13 = new Migration(12, 13) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            Log.i(TAG, "Migrating database from v12 to v13 (media field rename + expansion)");
            migrateFoodProducts(database);
            migrateRecipes(database);
            migrateMeals(database);
            Log.i(TAG, "Migration v12→v13 complete");
        }

        // ── food_products ─────────────────────────────────────────────────────

        private void migrateFoodProducts(@NonNull SupportSQLiteDatabase db) {

            // Step 1: Create the new table.
            //
            // Column order follows the v12 schema JSON exactly, with:
            //   imageThumbnailUrl  →  thumbnailUrl         (renamed, same position)
            //   heroImagePath      →  userImagePath        (renamed, same position)
            //   imagePath          TEXT                    (new, after userImagePath)
            //   userThumbnailPath  TEXT                    (new, after imagePath)
            //
            // All NOT NULL constraints and PRIMARY KEY are preserved verbatim.
            db.execSQL("CREATE TABLE IF NOT EXISTS `food_products_new` ("
                    + "`id` TEXT NOT NULL, "
                    + "`barcode` TEXT, "
                    + "`originalId` TEXT, "
                    + "`sourceId` TEXT, "
                    + "`productType` TEXT, "
                    + "`scientific_name` TEXT, "
                    + "`category_code` TEXT, "
                    + "`name` TEXT, "
                    + "`genericName` TEXT, "
                    + "`brand` TEXT, "
                    + "`description` TEXT, "
                    + "`ingredients` TEXT, "
                    + "`categoriesText` TEXT, "
                    + "`packaging` TEXT, "
                    + "`origins` TEXT, "
                    + "`stores` TEXT, "
                    + "`currentLanguage` TEXT, "
                    + "`needsDefaultLanguageUpdate` INTEGER NOT NULL, "
                    + "`translations` TEXT, "
                    + "`searchableText` TEXT, "
                    + "`imageUrl` TEXT, "
                    + "`thumbnailUrl` TEXT, "          // was: imageThumbnailUrl
                    + "`thumbnailPath` TEXT, "
                    + "`userImagePath` TEXT, "         // was: heroImagePath
                    + "`imagePath` TEXT, "             // new: auto-cached full-size
                    + "`userThumbnailPath` TEXT, "     // new: user-defined thumbnail
                    + "`nutriScore` TEXT, "
                    + "`ecoScore` TEXT, "
                    + "`novaGroup` TEXT, "
                    + "`quantity` TEXT, "
                    + "`isLiquid` INTEGER NOT NULL, "
                    + "`density` REAL, "
                    + "`isOrganic` INTEGER NOT NULL, "
                    + "`isVegan` INTEGER NOT NULL, "
                    + "`isVegetarian` INTEGER NOT NULL, "
                    + "`isGlutenFree` INTEGER NOT NULL, "
                    + "`isPalmOilFree` INTEGER NOT NULL, "
                    + "`isFairTrade` INTEGER NOT NULL, "
                    + "`allergenFlags` INTEGER NOT NULL, "
                    + "`servingSize` TEXT, "
                    + "`tags` TEXT, "
                    + "`dataCompleteness` REAL NOT NULL, "
                    + "`dataQualityScore` INTEGER NOT NULL, "
                    + "`isFavorite` INTEGER NOT NULL, "
                    + "`accessCount` INTEGER NOT NULL, "
                    + "`lastUpdated` INTEGER NOT NULL, "
                    + "`createdAt` INTEGER NOT NULL, "
                    + "`updatedAt` INTEGER NOT NULL, "
                    + "PRIMARY KEY(`id`))");

            // Step 2: Copy all existing data.
            //
            // imageThumbnailUrl → thumbnailUrl  : existing remote thumbnail URLs preserved
            // heroImagePath     → userImagePath : existing local hero paths preserved
            // imagePath         : NULL for all existing rows (no auto-cached full-size yet)
            // userThumbnailPath : NULL for all existing rows (no user thumbnails yet)
            db.execSQL("INSERT INTO food_products_new ("
                    + "id, barcode, originalId, sourceId, productType, scientific_name, "
                    + "category_code, name, genericName, brand, description, ingredients, "
                    + "categoriesText, packaging, origins, stores, currentLanguage, "
                    + "needsDefaultLanguageUpdate, translations, searchableText, "
                    + "imageUrl, thumbnailUrl, thumbnailPath, userImagePath, "
                    + "imagePath, userThumbnailPath, "
                    + "nutriScore, ecoScore, novaGroup, quantity, isLiquid, density, "
                    + "isOrganic, isVegan, isVegetarian, isGlutenFree, isPalmOilFree, "
                    + "isFairTrade, allergenFlags, servingSize, tags, dataCompleteness, "
                    + "dataQualityScore, isFavorite, accessCount, lastUpdated, createdAt, updatedAt"
                    + ") SELECT "
                    + "id, barcode, originalId, sourceId, productType, scientific_name, "
                    + "category_code, name, genericName, brand, description, ingredients, "
                    + "categoriesText, packaging, origins, stores, currentLanguage, "
                    + "needsDefaultLanguageUpdate, translations, searchableText, "
                    + "imageUrl, imageThumbnailUrl, thumbnailPath, heroImagePath, "  // old → new
                    + "NULL, NULL, "                                                  // imagePath, userThumbnailPath
                    + "nutriScore, ecoScore, novaGroup, quantity, isLiquid, density, "
                    + "isOrganic, isVegan, isVegetarian, isGlutenFree, isPalmOilFree, "
                    + "isFairTrade, allergenFlags, servingSize, tags, dataCompleteness, "
                    + "dataQualityScore, isFavorite, accessCount, lastUpdated, createdAt, updatedAt "
                    + "FROM food_products");

            // Step 3: Swap tables.
            db.execSQL("DROP TABLE food_products");
            db.execSQL("ALTER TABLE food_products_new RENAME TO food_products");

            // Step 4: Recreate all indices from v12 schema JSON.
            // These are identical to v12 — no index references renamed columns.
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_food_products_barcode` "
                    + "ON `food_products` (`barcode`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_food_products_sourceId_originalId` "
                    + "ON `food_products` (`sourceId`, `originalId`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_food_products_isFavorite` "
                    + "ON `food_products` (`isFavorite`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_food_products_lastUpdated` "
                    + "ON `food_products` (`lastUpdated`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_food_products_accessCount` "
                    + "ON `food_products` (`accessCount`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_food_products_category_code` "
                    + "ON `food_products` (`category_code`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_food_products_sourceId_category_code` "
                    + "ON `food_products` (`sourceId`, `category_code`)");

            Log.d(TAG, "  food_products migrated");
        }

        // ── recipes ───────────────────────────────────────────────────────────

        private void migrateRecipes(@NonNull SupportSQLiteDatabase db) {

            // Step 1: Create the new table.
            //
            // Column order follows the v12 schema JSON exactly, with:
            //   heroImagePath  →  userImagePath      (renamed, same position)
            //   thumbnailUrl   TEXT                  (new symmetry slot, after imageUrl/videoUrl)
            //   imagePath      TEXT                  (new, after thumbnailPath/userImagePath)
            //   userThumbnailPath TEXT               (new, after imagePath)
            //
            // New columns are placed after the existing media block for clarity.
            db.execSQL("CREATE TABLE IF NOT EXISTS `recipes_new` ("
                    + "`id` TEXT NOT NULL, "
                    + "`authorId` TEXT, "
                    + "`dataSource` TEXT NOT NULL, "
                    + "`originalId` TEXT, "
                    + "`sourceId` TEXT, "
                    + "`name` TEXT, "
                    + "`description` TEXT, "
                    + "`instructions` TEXT, "
                    + "`cuisine` TEXT, "
                    + "`notes` TEXT, "
                    + "`yieldDescription` TEXT, "
                    + "`recipeSource` TEXT, "
                    + "`equipmentNeeded` TEXT, "
                    + "`cookingTips` TEXT, "
                    + "`stepStructure` TEXT, "
                    + "`stepTranslations` TEXT, "
                    + "`currentLanguage` TEXT, "
                    + "`needsDefaultLanguageUpdate` INTEGER NOT NULL, "
                    + "`translations` TEXT, "
                    + "`searchableText` TEXT, "
                    + "`servings` INTEGER NOT NULL, "
                    + "`prepTimeMinutes` INTEGER NOT NULL, "
                    + "`cookTimeMinutes` INTEGER NOT NULL, "
                    + "`difficulty` TEXT, "
                    + "`portionsJson` TEXT, "
                    + "`isVegan` INTEGER NOT NULL, "
                    + "`isVegetarian` INTEGER NOT NULL, "
                    + "`isGlutenFree` INTEGER NOT NULL, "
                    + "`isDairyFree` INTEGER NOT NULL, "
                    + "`isKeto` INTEGER NOT NULL, "
                    + "`isPaleo` INTEGER NOT NULL, "
                    + "`allergenFlags` INTEGER NOT NULL, "
                    + "`isPublic` INTEGER NOT NULL, "
                    + "`isFavorite` INTEGER NOT NULL, "
                    + "`isTemplate` INTEGER NOT NULL, "
                    + "`rating` REAL NOT NULL, "
                    + "`ratingCount` INTEGER NOT NULL, "
                    + "`imageUrl` TEXT, "
                    + "`videoUrl` TEXT, "
                    + "`thumbnailUrl` TEXT, "          // new: remote thumbnail symmetry slot
                    + "`thumbnailPath` TEXT, "
                    + "`userImagePath` TEXT, "         // was: heroImagePath
                    + "`imagePath` TEXT, "             // new: auto-cached full-size
                    + "`userThumbnailPath` TEXT, "     // new: user-defined thumbnail
                    + "`tagsJson` TEXT, "
                    + "`completenessScore` REAL NOT NULL, "
                    + "`accessCount` INTEGER NOT NULL, "
                    + "`createdAt` INTEGER NOT NULL, "
                    + "`lastUpdated` INTEGER NOT NULL, "
                    + "PRIMARY KEY(`id`))");

            // Step 2: Copy all existing data.
            //
            // heroImagePath → userImagePath : existing user-set hero paths preserved
            // thumbnailUrl  : NULL (no source currently provides a separate thumbnail URL)
            // imagePath     : NULL (no auto-cached full-size yet)
            // userThumbnailPath : NULL (no user thumbnails yet)
            db.execSQL("INSERT INTO recipes_new ("
                    + "id, authorId, dataSource, originalId, sourceId, "
                    + "name, description, instructions, cuisine, notes, "
                    + "yieldDescription, recipeSource, equipmentNeeded, cookingTips, "
                    + "stepStructure, stepTranslations, currentLanguage, needsDefaultLanguageUpdate, "
                    + "translations, searchableText, servings, prepTimeMinutes, cookTimeMinutes, "
                    + "difficulty, portionsJson, isVegan, isVegetarian, isGlutenFree, isDairyFree, "
                    + "isKeto, isPaleo, allergenFlags, isPublic, isFavorite, isTemplate, "
                    + "rating, ratingCount, "
                    + "imageUrl, videoUrl, thumbnailUrl, thumbnailPath, userImagePath, "
                    + "imagePath, userThumbnailPath, "
                    + "tagsJson, completenessScore, accessCount, createdAt, lastUpdated"
                    + ") SELECT "
                    + "id, authorId, dataSource, originalId, sourceId, "
                    + "name, description, instructions, cuisine, notes, "
                    + "yieldDescription, recipeSource, equipmentNeeded, cookingTips, "
                    + "stepStructure, stepTranslations, currentLanguage, needsDefaultLanguageUpdate, "
                    + "translations, searchableText, servings, prepTimeMinutes, cookTimeMinutes, "
                    + "difficulty, portionsJson, isVegan, isVegetarian, isGlutenFree, isDairyFree, "
                    + "isKeto, isPaleo, allergenFlags, isPublic, isFavorite, isTemplate, "
                    + "rating, ratingCount, "
                    + "imageUrl, videoUrl, NULL, thumbnailPath, heroImagePath, "  // thumbnailUrl=NULL, heroImagePath→userImagePath
                    + "NULL, NULL, "                                               // imagePath, userThumbnailPath
                    + "tagsJson, completenessScore, accessCount, createdAt, lastUpdated "
                    + "FROM recipes");

            // Step 3: Swap tables.
            db.execSQL("DROP TABLE recipes");
            db.execSQL("ALTER TABLE recipes_new RENAME TO recipes");

            // Step 4: Recreate all indices from v12 schema JSON.
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_recipes_authorId` "
                    + "ON `recipes` (`authorId`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_recipes_dataSource` "
                    + "ON `recipes` (`dataSource`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_recipes_isFavorite` "
                    + "ON `recipes` (`isFavorite`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_recipes_isPublic` "
                    + "ON `recipes` (`isPublic`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_recipes_isTemplate` "
                    + "ON `recipes` (`isTemplate`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_recipes_lastUpdated` "
                    + "ON `recipes` (`lastUpdated`)");

            Log.d(TAG, "  recipes migrated");
        }

        // ── meals ─────────────────────────────────────────────────────────────

        private void migrateMeals(@NonNull SupportSQLiteDatabase db) {

            // Step 1: Create the new table.
            //
            // Meals are always user-created — no remote data source produces Meal
            // objects with image URLs. The media section therefore contains only
            // userImagePath (renamed from photoPath).
            //
            // imageUrl and imagePath are intentionally absent — they have no
            // concrete use case for meals and would be dead weight in the schema.
            //
            // Column order follows the v12 schema JSON exactly, with:
            //   photoPath  →  userImagePath    (renamed, same position)
            //   imageUrl                       (REMOVED — was dead weight in v12)
            db.execSQL("CREATE TABLE IF NOT EXISTS `meals_new` ("
                    + "`id` TEXT NOT NULL, "
                    + "`userId` TEXT, "
                    + "`name` TEXT, "
                    + "`description` TEXT, "
                    + "`notes` TEXT, "
                    + "`occasion` TEXT, "
                    + "`location` TEXT, "
                    + "`currentLanguage` TEXT, "
                    + "`needsDefaultLanguageUpdate` INTEGER NOT NULL, "
                    + "`translations` TEXT, "
                    + "`searchableText` TEXT, "
                    + "`mealType` TEXT, "
                    + "`mealDateTime` INTEGER NOT NULL, "
                    + "`isPlanned` INTEGER NOT NULL, "
                    + "`isTemplate` INTEGER NOT NULL, "
                    + "`isHomeMade` INTEGER NOT NULL, "
                    + "`allergenFlags` INTEGER NOT NULL, "
                    + "`estimatedCost` REAL, "
                    + "`satisfaction` REAL NOT NULL, "
                    + "`userImagePath` TEXT, "    // was: photoPath; imageUrl dropped entirely
                    + "`portionsJson` TEXT, "
                    + "`tagsJson` TEXT, "
                    + "`completenessScore` REAL NOT NULL, "
                    + "`accessCount` INTEGER NOT NULL, "
                    + "`createdAt` INTEGER NOT NULL, "
                    + "`lastUpdated` INTEGER NOT NULL, "
                    + "PRIMARY KEY(`id`))");

            // Step 2: Copy all existing data.
            //
            // photoPath → userImagePath : existing meal photos preserved
            // imageUrl deliberately not copied — data is discarded (was never set)
            db.execSQL("INSERT INTO meals_new ("
                    + "id, userId, name, description, notes, occasion, location, "
                    + "currentLanguage, needsDefaultLanguageUpdate, translations, searchableText, "
                    + "mealType, mealDateTime, isPlanned, isTemplate, isHomeMade, allergenFlags, "
                    + "estimatedCost, satisfaction, "
                    + "userImagePath, "
                    + "portionsJson, tagsJson, completenessScore, accessCount, createdAt, lastUpdated"
                    + ") SELECT "
                    + "id, userId, name, description, notes, occasion, location, "
                    + "currentLanguage, needsDefaultLanguageUpdate, translations, searchableText, "
                    + "mealType, mealDateTime, isPlanned, isTemplate, isHomeMade, allergenFlags, "
                    + "estimatedCost, satisfaction, "
                    + "photoPath, "   // photoPath → userImagePath; imageUrl silently dropped
                    + "portionsJson, tagsJson, completenessScore, accessCount, createdAt, lastUpdated "
                    + "FROM meals");

            // Step 3: Swap tables.
            db.execSQL("DROP TABLE meals");
            db.execSQL("ALTER TABLE meals_new RENAME TO meals");

            // Step 4: Recreate all indices from v12 schema JSON.
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_meals_userId` "
                    + "ON `meals` (`userId`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_meals_mealDateTime` "
                    + "ON `meals` (`mealDateTime`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_meals_mealType` "
                    + "ON `meals` (`mealType`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_meals_isTemplate` "
                    + "ON `meals` (`isTemplate`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_meals_lastUpdated` "
                    + "ON `meals` (`lastUpdated`)");

            Log.d(TAG, "  meals migrated");
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
        return 13;
    }

    /**
     * Get database name
     */
    public static String getDatabaseName() {
        return DATABASE_NAME;
    }
}