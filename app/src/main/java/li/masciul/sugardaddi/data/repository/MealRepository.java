package li.masciul.sugardaddi.data.repository;

import android.content.Context;
import android.util.Log;
import androidx.lifecycle.LiveData;

import li.masciul.sugardaddi.core.models.FoodPortion;
import li.masciul.sugardaddi.core.models.Meal;
import li.masciul.sugardaddi.core.models.Nutrition;
import li.masciul.sugardaddi.core.enums.MealType;
import li.masciul.sugardaddi.data.database.AppDatabase;
import li.masciul.sugardaddi.data.database.dao.MealDao;
import li.masciul.sugardaddi.data.database.dao.NutritionDao;
import li.masciul.sugardaddi.data.database.entities.MealEntity;
import li.masciul.sugardaddi.data.database.entities.NutritionEntity;
import li.masciul.sugardaddi.data.database.relations.MealWithNutrition;
import li.masciul.sugardaddi.data.database.relations.RecipeWithNutrition;
import li.masciul.sugardaddi.data.network.ApiConfig;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * MealRepository - Complete meal tracking with dual-table nutrition storage
 *
 * ARCHITECTURE v2:
 * - Dual-table storage (MealEntity + NutritionEntity)
 * - Uses Room @Transaction for automatic nutrition loading
 * - All sorting done in SQL, never in Java
 * - Complete null safety throughout
 * - Supports templates, planning, search, and analytics
 */
public class MealRepository {

    private static final String TAG = "MealRepository";

    // Dependencies
    private final Context context;
    private final AppDatabase database;
    private final MealDao mealDao;
    private final NutritionDao nutritionDao;
    private final Executor backgroundExecutor;

    // Caching
    private final Map<String, Meal> mealCache = Collections.synchronizedMap(new LinkedHashMap<>());
    private static final int MAX_CACHE_SIZE = 100;

    // Constants
    private static final int MIN_SEARCH_LENGTH = 2;
    private static final int DEFAULT_SEARCH_LIMIT = 100;

    /**
     * Callback interfaces
     */
    public interface MealCallback {
        void onSuccess(Meal meal);
        void onError(String error);
    }

    /**
     * Callback for meal search operations
     */
    public interface MealSearchCallback {
        void onMealsFound(List<Meal> meals);
        void onError(String error);
    }

    public interface MealListCallback {
        void onSuccess(List<Meal> meals);
        void onError(String error);
    }

    public interface MealOperationCallback {
        void onSuccess();
        void onError(String error);
    }

    public interface NutritionSummaryCallback {
        void onSuccess(DailyNutritionSummary summary);
        void onError(String error);
    }

    public interface MealStatsCallback {
        void onSuccess(MealStatistics stats);
        void onError(String error);
    }

    public interface NutritionAveragesCallback {
        void onSuccess(NutritionDao.MealNutritionAverages averages);
        void onError(String error);
    }

    public interface DailyNutritionTotalsCallback {
        void onSuccess(List<NutritionDao.DailyNutritionTotal> dailyTotals);
        void onError(String error);
    }

    public interface MealCountCallback {
        void onSuccess(int totalCount, double averagePerDay);
        void onError(String error);
    }

    /**
     * Rolling statistics window, always measured back from "now" - not
     * calendar-day-aligned. LAST_24_HOURS needs no special case: it's just
     * days=1 in the same uniform (now - days*24h) formula every other
     * window uses.
     */
    public enum StatsWindow {
        LAST_24_HOURS(1),
        LAST_7_DAYS(7),
        LAST_14_DAYS(14),
        LAST_30_DAYS(30),
        LAST_60_DAYS(60),
        LAST_90_DAYS(90);

        private final int days;

        StatsWindow(int days) {
            this.days = days;
        }

        public int getDays() {
            return days;
        }
    }

    // ========== CONSTRUCTOR ==========

    public MealRepository(Context context) {
        this.context = context.getApplicationContext();
        this.database = AppDatabase.getInstance(context);
        this.mealDao = database.mealDao();
        this.nutritionDao = database.nutritionDao();
        this.backgroundExecutor = Executors.newSingleThreadExecutor();

        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, "MealRepository initialized with dual-table storage");
        }
    }

    // ========== CRUD OPERATIONS ==========

    /**
     * Create a new meal with nutrition
     */
    public void createMeal(Meal meal, MealCallback callback) {
        if (meal == null) {
            if (callback != null) callback.onError("Meal cannot be null");
            return;
        }

        backgroundExecutor.execute(() -> {
            try {
                // Generate ID if needed
                if (meal.getId() == null || meal.getId().isEmpty()) {
                    meal.setId(UUID.randomUUID().toString());
                }

                // Calculate nutrition if needed
                if (meal.getNutrition() == null) {
                    meal.calculateNutrition();
                }
                meal.calculateCompleteness();

                // Meal + nutrition commit as one unit - same rationale as
                // ProductRepository/RecipeRepository: a nutrition-write
                // failure after a successful meal insert would otherwise
                // leave the meal row committed with no persisted nutrition
                // snapshot, invisible until something goes looking for it.
                // No createdAt-preservation lookup needed here specifically:
                // meal.getId() was just freshly generated above, so no
                // existing nutrition row can exist for it yet.
                MealEntity entity = MealEntity.fromMeal(meal);
                Nutrition nutrition = meal.getNutrition();

                database.runInTransaction(() -> {
                    mealDao.insert(entity);
                    if (nutrition != null && nutrition.hasData()) {
                        NutritionEntity nutritionEntity = NutritionEntity.fromNutrition(
                                nutrition, "meal", meal.getId());
                        nutritionDao.insertNutrition(nutritionEntity);
                    }
                });

                cacheMeal(meal);

                if (ApiConfig.DEBUG_LOGGING) {
                    Log.d(TAG, "Created meal: " + meal.getId());
                }

                runOnMainThread(() -> {
                    if (callback != null) callback.onSuccess(meal);
                });

            } catch (Exception e) {
                Log.e(TAG, "Error creating meal", e);
                runOnMainThread(() -> {
                    if (callback != null) callback.onError("Failed to create meal: " + e.getMessage());
                });
            }
        });
    }

    /**
     * Get meal by ID with nutrition
     */
    public void getMeal(String mealId, MealCallback callback) {
        if (mealId == null || mealId.trim().isEmpty()) {
            if (callback != null) callback.onError("Invalid meal ID");
            return;
        }

        // Check cache first
        Meal cached = mealCache.get(mealId);
        if (cached != null) {
            if (callback != null) callback.onSuccess(cached);
            mealDao.incrementAccessCount(mealId, System.currentTimeMillis());
            return;
        }

        backgroundExecutor.execute(() -> {
            try {
                // Use @Transaction query to get meal with nutrition
                MealWithNutrition mealWithNutrition = mealDao.getByIdWithNutrition(mealId);

                if (mealWithNutrition != null) {
                    Meal meal = mealWithNutrition.toMeal();
                    cacheMeal(meal);
                    mealDao.incrementAccessCount(mealId, System.currentTimeMillis());

                    runOnMainThread(() -> {
                        if (callback != null) callback.onSuccess(meal);
                    });
                } else {
                    runOnMainThread(() -> {
                        if (callback != null) callback.onError("Meal not found");
                    });
                }

            } catch (Exception e) {
                Log.e(TAG, "Error loading meal", e);
                runOnMainThread(() -> {
                    if (callback != null) callback.onError("Failed to load meal: " + e.getMessage());
                });
            }
        });
    }

    /**
     * Get meal by ID with products populated for nutrition calculation
     *
     * CRITICAL: This method ensures that transient FoodProduct fields are populated
     * in each FoodPortion, which is required for correct nutrition calculation.
     *
     * Without populating these fields, nutrition calculation will fail because:
     * 1. FoodProduct is marked as transient (not persisted to database)
     * 2. When loaded from DB, foodProduct is null
     * 3. calculateNutrition() tries to access null.getNutrition() → returns null
     * 4. Cached result is null forever
     *
     * USE THIS METHOD instead of getMeal() when displaying meals with nutrition!
     *
     * @param mealId Meal ID
     * @param callback Callback with meal and populated products
     */
    public void getMealWithProducts(String mealId, MealCallback callback) {
        if (mealId == null || mealId.trim().isEmpty()) {
            if (callback != null) callback.onError("Invalid meal ID");
            return;
        }

        backgroundExecutor.execute(() -> {
            try {
                // Load meal with nutrition using @Transaction
                MealWithNutrition mealWithNutrition = mealDao.getByIdWithNutrition(mealId);

                if (mealWithNutrition == null) {
                    runOnMainThread(() -> {
                        if (callback != null) callback.onError("Meal not found");
                    });
                    return;
                }

                // Convert to domain model
                Meal meal = mealWithNutrition.toMeal();

                // CRITICAL: Populate transient foodProduct/recipe fields
                List<FoodPortion> portions = meal.getPortions();
                if (portions != null && !portions.isEmpty()) {
                    for (FoodPortion portion : portions) {
                        String itemId = portion.getItemId();
                        if (itemId != null && "FOOD_PRODUCT".equals(portion.getItemType())) {
                            try {
                                // Load product with nutrition from database
                                var productWithNutrition =
                                        database.combinedProductDao()
                                                .getProductWithNutritionById(itemId);

                                if (productWithNutrition != null) {
                                    // Convert and set on portion (this clears cache!)
                                    portion.setFoodProduct(productWithNutrition.toFoodProduct());
                                } else {
                                    Log.w(TAG, "Product not found in database: " + itemId);
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Failed to load product for portion: " + itemId, e);
                            }
                        } else if (itemId != null && "RECIPE".equals(portion.getItemType())) {
                            try {
                                RecipeWithNutrition recipeWithNutrition =
                                        database.recipeDao().getByIdWithNutrition(itemId);

                                if (recipeWithNutrition != null) {
                                    portion.setRecipe(recipeWithNutrition.toRecipe());
                                } else {
                                    Log.w(TAG, "Recipe not found in database: " + itemId);
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Failed to load recipe for portion: " + itemId, e);
                            }
                        }
                    }
                }

                // CRITICAL: Clear old provided nutrition before recalculating
                // Otherwise getNutrition() will return the old cached value!
                meal.setProvidedNutrition(null);

                // Recalculate nutrition after all products are loaded
                meal.calculateNutrition();

                // Cache and update access count
                cacheMeal(meal);
                mealDao.incrementAccessCount(mealId, System.currentTimeMillis());

                // Return on main thread
                runOnMainThread(() -> {
                    if (callback != null) callback.onSuccess(meal);
                });

            } catch (Exception e) {
                Log.e(TAG, "Error loading meal with products", e);
                runOnMainThread(() -> {
                    if (callback != null) callback.onError("Failed to load meal: " + e.getMessage());
                });
            }
        });
    }

    /**
     * Update existing meal
     */
    public void updateMeal(Meal meal, MealCallback callback) {
        if (meal == null || meal.getId() == null) {
            if (callback != null) callback.onError("Invalid meal data");
            return;
        }

        backgroundExecutor.execute(() -> {
            try {
                // Recalculate if needed
                if (meal.getNutrition() == null) {
                    meal.calculateNutrition();
                }
                meal.calculateCompleteness();
                meal.touch();

                // Meal + nutrition commit as one unit - see createMeal()'s
                // comment for the rationale. The existing-row lookup and
                // @Update/@Insert branch are gone: NutritionEntity's
                // nutritionId is deterministic (sourceType:sourceId), so
                // insertNutrition()'s REPLACE strategy already overwrites
                // the correct row unconditionally, same as
                // ProductRepository/RecipeRepository - the branch was
                // doing extra work to reach the same outcome REPLACE gives
                // for free.
                MealEntity entity = MealEntity.fromMeal(meal);
                Nutrition nutrition = meal.getNutrition();
                Log.d(TAG, "updateMeal - saving nutrition: " + nutrition
                        + " hasData: " + (nutrition != null ? nutrition.hasData() : "null"));

                database.runInTransaction(() -> {
                    mealDao.update(entity);
                    if (nutrition != null && nutrition.hasData()) {
                        NutritionEntity nutritionEntity = NutritionEntity.fromNutrition(
                                nutrition, "meal", meal.getId());
                        // REPLACE discards the row; preserve the original
                        // creation date the same way Product/Recipe do.
                        NutritionEntity existingNutrition =
                                nutritionDao.getNutritionBySource("meal", meal.getId());
                        if (existingNutrition != null) {
                            nutritionEntity.setCreatedAt(existingNutrition.getCreatedAt());
                        }
                        nutritionDao.insertNutrition(nutritionEntity);
                    }
                });

                cacheMeal(meal);

                runOnMainThread(() -> {
                    if (callback != null) callback.onSuccess(meal);
                });

            } catch (Exception e) {
                Log.e(TAG, "Error updating meal", e);
                runOnMainThread(() -> {
                    if (callback != null) callback.onError("Failed to update meal: " + e.getMessage());
                });
            }
        });
    }

    /**
     * Delete meal and its nutrition
     */
    public void deleteMeal(String mealId, MealOperationCallback callback) {
        if (mealId == null || mealId.trim().isEmpty()) {
            if (callback != null) callback.onError("Invalid meal ID");
            return;
        }

        backgroundExecutor.execute(() -> {
            try {
                // Delete meal (nutrition deleted via cascade or manually)
                int deleted = mealDao.deleteById(mealId);

                // Also delete nutrition explicitly to be safe
                nutritionDao.deleteNutritionBySource("meal", mealId);

                mealCache.remove(mealId);

                runOnMainThread(() -> {
                    if (callback != null) callback.onSuccess();
                });

            } catch (Exception e) {
                Log.e(TAG, "Error deleting meal", e);
                runOnMainThread(() -> {
                    if (callback != null) callback.onError("Failed to delete meal: " + e.getMessage());
                });
            }
        });
    }

    // ========== DATE-BASED QUERIES ==========

    /**
     * Get meals for a specific date (chronological order for daily view)
     */
    public void getMealsForDate(LocalDate date, boolean ascending, MealListCallback callback) {
        if (date == null) {
            if (callback != null) callback.onError("Date cannot be null");
            return;
        }

        backgroundExecutor.execute(() -> {
            try {
                long startOfDay = date.atStartOfDay()
                        .atZone(ZoneId.systemDefault())
                        .toInstant().toEpochMilli();
                long endOfDay = date.plusDays(1).atStartOfDay()
                        .atZone(ZoneId.systemDefault())
                        .toInstant().toEpochMilli() - 1;

                // Use ascending for daily view (breakfast -> dinner)
                List<MealWithNutrition> mealsWithNutrition = ascending ?
                        mealDao.getMealsBetweenDatesWithNutritionAsc(startOfDay, endOfDay) :
                        mealDao.getMealsBetweenDatesWithNutrition(startOfDay, endOfDay);

                List<Meal> meals = convertToMeals(mealsWithNutrition);

                runOnMainThread(() -> {
                    if (callback != null) callback.onSuccess(meals);
                });

            } catch (Exception e) {
                Log.e(TAG, "Error loading meals for date", e);
                runOnMainThread(() -> {
                    if (callback != null) callback.onError("Failed to load meals: " + e.getMessage());
                });
            }
        });
    }

    // Convenience method with default ordering
    public void getMealsForDate(LocalDate date, MealListCallback callback) {
        getMealsForDate(date, true, callback); // Default ascending for daily view
    }

    /**
     * Get meals for week
     */
    public void getMealsForWeek(LocalDate weekStart, MealListCallback callback) {
        if (weekStart == null) {
            if (callback != null) callback.onError("Week start date cannot be null");
            return;
        }

        backgroundExecutor.execute(() -> {
            try {
                long startTime = weekStart.atStartOfDay()
                        .atZone(ZoneId.systemDefault())
                        .toInstant().toEpochMilli();
                long endTime = weekStart.plusDays(7).atStartOfDay()
                        .atZone(ZoneId.systemDefault())
                        .toInstant().toEpochMilli() - 1;

                List<MealWithNutrition> mealsWithNutrition =
                        mealDao.getMealsForWeekWithNutrition(startTime, endTime);
                List<Meal> meals = convertToMeals(mealsWithNutrition);

                runOnMainThread(() -> {
                    if (callback != null) callback.onSuccess(meals);
                });

            } catch (Exception e) {
                Log.e(TAG, "Error loading meals for week", e);
                runOnMainThread(() -> {
                    if (callback != null) callback.onError("Failed to load meals: " + e.getMessage());
                });
            }
        });
    }

    // ========== MEAL TYPE QUERIES ==========

    /**
     * Get meals by type
     */
    public void getMealsByType(MealType mealType, MealListCallback callback) {
        if (mealType == null) {
            if (callback != null) callback.onError("Meal type cannot be null");
            return;
        }

        backgroundExecutor.execute(() -> {
            try {
                List<MealWithNutrition> mealsWithNutrition =
                        mealDao.getMealsByTypeWithNutrition(mealType.getId());
                List<Meal> meals = convertToMeals(mealsWithNutrition);

                runOnMainThread(() -> {
                    if (callback != null) callback.onSuccess(meals);
                });

            } catch (Exception e) {
                Log.e(TAG, "Error loading meals by type", e);
                runOnMainThread(() -> {
                    if (callback != null) callback.onError("Failed to load meals: " + e.getMessage());
                });
            }
        });
    }

    // ========== SEARCH ==========

    /**
     * Search meals
     */
    public void search(String query, int limit, MealListCallback callback) {
        if (query == null || query.trim().length() < MIN_SEARCH_LENGTH) {
            if (callback != null) callback.onError("Search query too short");
            return;
        }

        backgroundExecutor.execute(() -> {
            try {
                String cleanQuery = query.trim();
                List<MealWithNutrition> results =
                        mealDao.searchWithNutrition(cleanQuery, limit);
                List<Meal> meals = convertToMeals(results);

                runOnMainThread(() -> {
                    if (callback != null) callback.onSuccess(meals);
                });

            } catch (Exception e) {
                Log.e(TAG, "Error searching meals", e);
                runOnMainThread(() -> {
                    if (callback != null) callback.onError("Search failed: " + e.getMessage());
                });
            }
        });
    }

    // Convenience method with default limit
    public void search(String query, MealListCallback callback) {
        search(query, DEFAULT_SEARCH_LIMIT, callback);
    }

    // ========== PLANNING & TEMPLATES ==========

    /**
     * Get upcoming planned meals
     */
    public void getPlannedMeals(MealListCallback callback) {
        backgroundExecutor.execute(() -> {
            try {
                long currentTime = System.currentTimeMillis();
                List<MealWithNutrition> planned =
                        mealDao.getUpcomingPlannedMealsWithNutrition(currentTime);
                List<Meal> meals = convertToMeals(planned);

                runOnMainThread(() -> {
                    if (callback != null) callback.onSuccess(meals);
                });

            } catch (Exception e) {
                Log.e(TAG, "Error loading planned meals", e);
                runOnMainThread(() -> {
                    if (callback != null) callback.onError("Failed to load planned meals: " + e.getMessage());
                });
            }
        });
    }

    /**
     * Log planned meal as consumed
     */
    public void logMealAsConsumed(String mealId, MealOperationCallback callback) {
        if (mealId == null || mealId.trim().isEmpty()) {
            if (callback != null) callback.onError("Invalid meal ID");
            return;
        }

        backgroundExecutor.execute(() -> {
            try {
                long consumedTime = System.currentTimeMillis();
                int updated = mealDao.markAsConsumed(mealId, consumedTime);
                mealCache.remove(mealId); // Clear cache to force reload

                runOnMainThread(() -> {
                    if (callback != null) callback.onSuccess();
                });

            } catch (Exception e) {
                Log.e(TAG, "Error marking meal as consumed", e);
                runOnMainThread(() -> {
                    if (callback != null) callback.onError("Failed to log meal: " + e.getMessage());
                });
            }
        });
    }

    /**
     * Get meal templates
     */
    public void getMealTemplates(MealListCallback callback) {
        backgroundExecutor.execute(() -> {
            try {
                List<MealWithNutrition> templates =
                        mealDao.getTemplatesWithNutrition();
                List<Meal> meals = convertToMeals(templates);

                runOnMainThread(() -> {
                    if (callback != null) callback.onSuccess(meals);
                });

            } catch (Exception e) {
                Log.e(TAG, "Error loading templates", e);
                runOnMainThread(() -> {
                    if (callback != null) callback.onError("Failed to load templates: " + e.getMessage());
                });
            }
        });
    }

    /**
     * Create template from existing meal
     */
    public void createTemplate(String mealId, MealOperationCallback callback) {
        if (mealId == null || mealId.trim().isEmpty()) {
            if (callback != null) callback.onError("Invalid meal ID");
            return;
        }

        backgroundExecutor.execute(() -> {
            try {
                int updated = mealDao.markAsTemplate(mealId);
                mealCache.remove(mealId); // Clear cache

                runOnMainThread(() -> {
                    if (callback != null) callback.onSuccess();
                });

            } catch (Exception e) {
                Log.e(TAG, "Error creating template", e);
                runOnMainThread(() -> {
                    if (callback != null) callback.onError("Failed to create template: " + e.getMessage());
                });
            }
        });
    }

    /**
     * Create meal from template
     */
    public void createMealFromTemplate(String templateId, LocalDateTime scheduledTime, MealCallback callback) {
        getMeal(templateId, new MealCallback() {
            @Override
            public void onSuccess(Meal template) {
                // Create new meal copying template properties
                Meal newMeal = new Meal();

                // Copy basic properties
                newMeal.setMealType(template.getMealType());
                newMeal.setUserId(template.getUserId());
                newMeal.setCurrentLanguage(template.getCurrentLanguage());
                newMeal.setUserImagePath(template.getUserImagePath());

                // Copy all translations (contains all language data)
                newMeal.setTranslations(new HashMap<>(template.getTranslations()));

                // Set primary fields from current language
                String lang = template.getCurrentLanguage();
                newMeal.setName(template.getName(lang));
                newMeal.setDescription(template.getDescription(lang));
                newMeal.setNotes(template.getNotes(lang));
                newMeal.setOccasion(template.getOccasion(lang));
                newMeal.setLocation(template.getLocation(lang));

                // Copy portions (deep copy to avoid reference issues)
                List<FoodPortion> templatePortions = template.getPortions();
                for (FoodPortion portion : templatePortions) {
                    newMeal.addPortion(portion);
                }

                // Copy tags
                newMeal.setTags(new HashSet<>(template.getTags()));

                // Set new meal specific properties
                newMeal.setId(UUID.randomUUID().toString());
                newMeal.setTemplate(false);
                newMeal.setPlanned(true);
                newMeal.setStartTime(scheduledTime);
                newMeal.setCreatedAt(System.currentTimeMillis());
                newMeal.setLastUpdated(System.currentTimeMillis());
                // accessCount starts at 0 automatically

                // Save as new meal
                createMeal(newMeal, callback);
            }

            @Override
            public void onError(String error) {
                if (callback != null) callback.onError("Template not found: " + error);
            }
        });
    }

    // ========== ANALYTICS ==========

    /**
     * Get daily nutrition summary
     */
    public void getDailyNutritionSummary(LocalDate date, NutritionSummaryCallback callback) {
        getMealsForDate(date, true, new MealListCallback() {
            @Override
            public void onSuccess(List<Meal> meals) {
                DailyNutritionSummary summary = calculateDailyNutrition(meals, date);
                if (callback != null) callback.onSuccess(summary);
            }

            @Override
            public void onError(String error) {
                if (callback != null) callback.onError(error);
            }
        });
    }

    /**
     * Get meal statistics for date range
     */
    public void getMealStatistics(LocalDate startDate, LocalDate endDate, MealStatsCallback callback) {
        if (startDate == null || endDate == null) {
            if (callback != null) callback.onError("Date range cannot be null");
            return;
        }

        backgroundExecutor.execute(() -> {
            try {
                long startTime = startDate.atStartOfDay()
                        .atZone(ZoneId.systemDefault())
                        .toInstant().toEpochMilli();
                long endTime = endDate.plusDays(1).atStartOfDay()
                        .atZone(ZoneId.systemDefault())
                        .toInstant().toEpochMilli() - 1;

                // Get basic stats
                int totalMeals = mealDao.getMealCountInRange(startTime, endTime);
                Double avgSatisfaction = mealDao.getAverageSatisfactionInRange(startTime, endTime);

                // Get meals for detailed nutrition stats
                List<MealWithNutrition> mealsWithNutrition =
                        mealDao.getMealsBetweenDatesWithNutrition(startTime, endTime);

                MealStatistics stats = calculateStatistics(
                        convertToMeals(mealsWithNutrition),
                        totalMeals,
                        avgSatisfaction != null ? avgSatisfaction : 0.0
                );

                runOnMainThread(() -> {
                    if (callback != null) callback.onSuccess(stats);
                });

            } catch (Exception e) {
                Log.e(TAG, "Error calculating statistics", e);
                runOnMainThread(() -> {
                    if (callback != null) callback.onError("Failed to calculate statistics: " + e.getMessage());
                });
            }
        });
    }

    // ========== NUTRITION STATISTICS (window-based, via nutrition JOIN) ==========

    /**
     * Average nutrients per meal over a rolling window. mealType is
     * nullable - null answers "average per meal" across all types; a
     * specific MealType answers "average per meal of that type" (e.g.
     * SNACK for "average nutrients per snack").
     */
    public void getNutritionAverages(StatsWindow window, MealType mealType,
                                     NutritionAveragesCallback callback) {
        if (window == null) {
            if (callback != null) callback.onError("Window cannot be null");
            return;
        }
        backgroundExecutor.execute(() -> {
            try {
                long[] bounds = computeWindowBoundaries(window);
                String typeFilter = mealType != null ? mealType.name() : null;
                NutritionDao.MealNutritionAverages averages =
                        mealDao.getAverageNutritionInRange(bounds[0], bounds[1], typeFilter);
                runOnMainThread(() -> {
                    if (callback != null) callback.onSuccess(averages);
                });
            } catch (Exception e) {
                Log.e(TAG, "Error calculating nutrition averages", e);
                runOnMainThread(() -> {
                    if (callback != null) callback.onError(
                            "Failed to calculate nutrition averages: " + e.getMessage());
                });
            }
        });
    }

    /**
     * Per-day nutrient totals over a rolling window - the "average nutrient
     * value per day" answer is AVG() over the returned list's totalX
     * fields; this returns the full daily breakdown rather than a single
     * collapsed number, since a trend is more useful for the same query
     * cost. Days with no logged meals simply don't appear as a row.
     */
    public void getDailyNutritionTotals(StatsWindow window, DailyNutritionTotalsCallback callback) {
        if (window == null) {
            if (callback != null) callback.onError("Window cannot be null");
            return;
        }
        backgroundExecutor.execute(() -> {
            try {
                long[] bounds = computeWindowBoundaries(window);
                List<NutritionDao.DailyNutritionTotal> totals =
                        mealDao.getDailyNutritionTotals(bounds[0], bounds[1]);
                runOnMainThread(() -> {
                    if (callback != null) callback.onSuccess(totals);
                });
            } catch (Exception e) {
                Log.e(TAG, "Error calculating daily nutrition totals", e);
                runOnMainThread(() -> {
                    if (callback != null) callback.onError(
                            "Failed to calculate daily nutrition totals: " + e.getMessage());
                });
            }
        });
    }

    /**
     * Meal count over a rolling window, plus the average per day already
     * computed for convenience. mealType is nullable - null answers
     * "average meals per day"; MealType.SNACK answers "average snacks per
     * day". For "average snacks per week", multiply averagePerDay by 7 -
     * no separate query needed, the precision is the same either way.
     */
    public void getMealCount(StatsWindow window, MealType mealType, MealCountCallback callback) {
        if (window == null) {
            if (callback != null) callback.onError("Window cannot be null");
            return;
        }
        backgroundExecutor.execute(() -> {
            try {
                long[] bounds = computeWindowBoundaries(window);
                String typeFilter = mealType != null ? mealType.name() : null;
                int count = mealDao.getMealCountInRangeByType(bounds[0], bounds[1], typeFilter);
                double averagePerDay = count / (double) window.getDays();
                runOnMainThread(() -> {
                    if (callback != null) callback.onSuccess(count, averagePerDay);
                });
            } catch (Exception e) {
                Log.e(TAG, "Error calculating meal count", e);
                runOnMainThread(() -> {
                    if (callback != null) callback.onError(
                            "Failed to calculate meal count: " + e.getMessage());
                });
            }
        });
    }

    /** (now - window.getDays()*24h) to now, in epoch millis. */
    private long[] computeWindowBoundaries(StatsWindow window) {
        long endTime = System.currentTimeMillis();
        long startTime = endTime - (window.getDays() * 24L * 60 * 60 * 1000);
        return new long[]{startTime, endTime};
    }

    // ========== LIVEDATA (for UI observation) ==========

    /**
     * Get LiveData for meal templates
     */
    public LiveData<List<MealWithNutrition>> getTemplatesLive() {
        return mealDao.getTemplatesWithNutritionLive();
    }

    /**
     * Get LiveData for date range
     */
    public LiveData<List<MealWithNutrition>> getMealsLive(long startTime, long endTime) {
        return mealDao.getMealsWithNutritionLive(startTime, endTime);
    }

    // ========== HELPER METHODS ==========

    private List<Meal> convertToMeals(List<MealWithNutrition> mealsWithNutrition) {
        if (mealsWithNutrition == null) return new ArrayList<>();

        List<Meal> meals = new ArrayList<>();

        for (MealWithNutrition mealWithNutrition : mealsWithNutrition) {
            if (mealWithNutrition == null) continue;

            // toMeal() already sets providedNutrition from the persisted
            // NutritionEntity snapshot (nutrition.toNutrition()) - no need
            // to re-resolve every FOOD_PRODUCT portion and recompute live.
            // That was only ever necessary because the write path used to
            // be unreliable; it's fixed now (see MealRepository.createMeal/
            // updateMeal), so the persisted value can be trusted directly.
            Meal meal = mealWithNutrition.toMeal();
            if (meal == null) continue;

            meals.add(meal);
        }

        return meals;
    }

    private void cacheMeal(Meal meal) {
        if (meal == null || meal.getId() == null) return;

        synchronized (mealCache) {
            mealCache.put(meal.getId(), meal);

            // Maintain cache size
            if (mealCache.size() > MAX_CACHE_SIZE) {
                Iterator<String> iterator = mealCache.keySet().iterator();
                if (iterator.hasNext()) {
                    iterator.next();
                    iterator.remove();
                }
            }
        }
    }

    /**
     * Clear a specific meal from cache
     * Use this when you want to force a fresh load from database
     */
    public void clearMealCache(String mealId) {
        if (mealId != null) {
            synchronized (mealCache) {
                mealCache.remove(mealId);
            }
        }
    }

    private DailyNutritionSummary calculateDailyNutrition(List<Meal> meals, LocalDate date) {
        DailyNutritionSummary summary = new DailyNutritionSummary(date);

        for (Meal meal : meals) {
            if (meal != null && meal.hasNutritionData()) {
                summary.addMeal(meal);
            }
        }

        return summary;
    }

    private MealStatistics calculateStatistics(List<Meal> meals, int totalCount, double avgSatisfaction) {
        MealStatistics stats = new MealStatistics();
        stats.totalMeals = totalCount;
        stats.averageSatisfaction = avgSatisfaction;

        // Calculate nutrition averages
        double totalCalories = 0;
        double totalProtein = 0;
        double totalCarbs = 0;
        double totalFat = 0;
        int nutritionCount = 0;

        for (Meal meal : meals) {
            if (meal != null && meal.hasNutritionData()) {
                Nutrition nutrition = meal.getNutrition();
                if (nutrition.getEnergyKcal() != null) totalCalories += nutrition.getEnergyKcal();
                if (nutrition.getProteins() != null) totalProtein += nutrition.getProteins();
                if (nutrition.getCarbohydrates() != null) totalCarbs += nutrition.getCarbohydrates();
                if (nutrition.getFat() != null) totalFat += nutrition.getFat();
                nutritionCount++;
            }
        }

        if (nutritionCount > 0) {
            stats.averageCalories = totalCalories / nutritionCount;
            stats.averageProtein = totalProtein / nutritionCount;
            stats.averageCarbs = totalCarbs / nutritionCount;
            stats.averageFat = totalFat / nutritionCount;
        }

        return stats;
    }

    private void runOnMainThread(Runnable runnable) {
        if (runnable != null) {
            new android.os.Handler(android.os.Looper.getMainLooper()).post(runnable);
        }
    }

    /**
     * Cancel any pending search operations
     */
    public void cancelSearch() {
        // Currently synchronous, but could be enhanced with cancellable futures
        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, "Search cancelled");
        }
    }

    // ========== DATA CLASSES ==========

    /**
     * Daily nutrition summary
     */
    public static class DailyNutritionSummary {
        private final LocalDate date;
        private final List<Meal> meals = new ArrayList<>();
        private double totalCalories = 0;
        private double totalProtein = 0;
        private double totalCarbs = 0;
        private double totalFat = 0;
        private double totalFiber = 0;

        public DailyNutritionSummary(LocalDate date) {
            this.date = date;
        }

        public void addMeal(Meal meal) {
            if (meal == null) return;

            meals.add(meal);

            if (meal.hasNutritionData()) {
                Nutrition nutrition = meal.getNutrition();
                if (nutrition != null) {
                    if (nutrition.getEnergyKcal() != null) totalCalories += nutrition.getEnergyKcal();
                    if (nutrition.getProteins() != null) totalProtein += nutrition.getProteins();
                    if (nutrition.getCarbohydrates() != null) totalCarbs += nutrition.getCarbohydrates();
                    if (nutrition.getFat() != null) totalFat += nutrition.getFat();
                    if (nutrition.getFiber() != null) totalFiber += nutrition.getFiber();
                }
            }
        }

        // Getters
        public LocalDate getDate() { return date; }
        public List<Meal> getMeals() { return new ArrayList<>(meals); }
        public double getTotalCalories() { return totalCalories; }
        public double getTotalProtein() { return totalProtein; }
        public double getTotalCarbs() { return totalCarbs; }
        public double getTotalFat() { return totalFat; }
        public double getTotalFiber() { return totalFiber; }
        public int getMealCount() { return meals.size(); }
    }

    /**
     * Meal statistics
     */
    public static class MealStatistics {
        public int totalMeals;
        public double averageSatisfaction;
        public double averageCalories;
        public double averageProtein;
        public double averageCarbs;
        public double averageFat;

        @Override
        public String toString() {
            return String.format(Locale.US,
                    "Meals: %d, Satisfaction: %.1f, Avg Cal: %.0f, P: %.1fg, C: %.1fg, F: %.1fg",
                    totalMeals, averageSatisfaction, averageCalories,
                    averageProtein, averageCarbs, averageFat);
        }
    }
}