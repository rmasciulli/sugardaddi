package li.masciul.sugardaddi.data.database.entities;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.Index;
import androidx.room.TypeConverters;
import androidx.annotation.NonNull;

import li.masciul.sugardaddi.core.models.Meal;
import li.masciul.sugardaddi.core.models.MealTranslation;
import li.masciul.sugardaddi.core.models.FoodPortion;
import li.masciul.sugardaddi.core.enums.MealType;
import li.masciul.sugardaddi.data.database.converters.GeneralConverters;
import li.masciul.sugardaddi.data.database.converters.MealTranslationMapConverter;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * MealEntity - Room entity for meal storage (v3.0 - Hybrid Translation)
 *
 * ARCHITECTURE UPDATE v3.0:
 * - Nutrition data stored separately in NutritionEntity table
 * - NEW: Hybrid translation system replacing LocalizedContent
 * - Primary fields store content in currentLanguage
 * - Translation map stores all OTHER languages (MealTranslation objects)
 * - Use MealWithNutrition relation for queries needing nutrition
 */
@Entity(
        tableName = "meals",
        indices = {
                @Index(value = {"userId"}),
                @Index(value = {"mealDateTime"}),
                @Index(value = {"mealType"}),
                @Index(value = {"isTemplate"}),
                @Index(value = {"lastUpdated"})
        }
)
public class MealEntity {

    @PrimaryKey
    @NonNull
    private String id = "";

    // ========== IDENTIFICATION ==========
    private String userId;

    // ========== PRIMARY CONTENT (in currentLanguage) ==========
    private String name;
    private String description;
    private String notes;
    private String occasion;
    private String location;

    // ========== LANGUAGE MANAGEMENT ==========
    private String currentLanguage = "en";
    private boolean needsDefaultLanguageUpdate = false;

    @TypeConverters(MealTranslationMapConverter.class)
    private Map<String, MealTranslation> translations;

    private String searchableText;

    // ========== MEAL PROPERTIES ==========
    private String mealType = MealType.LUNCH.getId();
    private long mealDateTime;          // Timestamp of when meal was consumed/planned
    private boolean isPlanned = true;
    private boolean isTemplate = false;
    private boolean isHomeMade = true;

    // ========== ALLERGEN FLAGS (NEW v3.0) ==========
    /**
     * Combined allergen flags from all meal portions
     * Calculated by aggregating allergenFlags from all FoodProducts/Recipes in portions
     */
    private int allergenFlags = 0;

    // ========== MEAL CONTEXT ==========
    private Double estimatedCost;
    private float satisfaction = 0.0f;

    // ========== MEDIA ==========
    private String imageUrl;

    // ========== INGREDIENTS ==========
    private String portionsJson;        // List<FoodPortion> as JSON

    // ========== TAGS ==========
    private String tagsJson;            // Set<String> as JSON

    // ========== QUALITY METRICS ==========
    private float completenessScore = 0.0f;
    private int accessCount = 0;

    // ========== TIMESTAMPS ==========
    private long createdAt;
    private long lastUpdated;

    // ========== CONSTRUCTORS ==========

    public MealEntity() {
        long currentTime = System.currentTimeMillis();
        this.createdAt = currentTime;
        this.lastUpdated = currentTime;
        this.mealDateTime = currentTime;
        this.translations = new HashMap<>();
    }

    // ========== CONVERSION METHODS ==========

    /**
     * Convert MealEntity to Meal domain model
     * NOTE: Nutrition must be loaded separately from NutritionEntity
     */
    public Meal toMeal() {
        Meal meal = new Meal();

        meal.setId(this.id);
        meal.setUserId(this.userId);
        meal.setCreatedAt(this.createdAt);
        meal.setLastUpdated(this.lastUpdated);

        // Set primary content fields
        meal.setName(this.name);
        meal.setDescription(this.description);
        meal.setNotes(this.notes);
        meal.setOccasion(this.occasion);
        meal.setLocation(this.location);

        // Set language management
        meal.setCurrentLanguage(this.currentLanguage);
        meal.setNeedsDefaultLanguageUpdate(this.needsDefaultLanguageUpdate);
        meal.setTranslations(this.translations != null ? this.translations : new HashMap<>());
        meal.setSearchableText(this.searchableText);

        // Convert meal type
        if (this.mealType != null) {
            meal.setMealType(MealType.fromId(this.mealType));
        }

        // Convert timestamp to LocalDateTime
        meal.setStartTime(LocalDateTime.ofInstant(
                Instant.ofEpochMilli(this.mealDateTime),
                ZoneId.systemDefault()
        ));

        // Set meal properties
        meal.setPlanned(this.isPlanned);
        meal.setTemplate(this.isTemplate);
        meal.setHomeMade(this.isHomeMade);

        // Set allergen flags (NEW v3.0)
        meal.setAllergenFlags(this.allergenFlags);

        meal.setEstimatedCost(this.estimatedCost);
        meal.setSatisfaction(this.satisfaction);
        meal.setImageUrl(this.imageUrl);

        // Convert portions from JSON
        if (portionsJson != null) {
            try {
                List<FoodPortion> portions = GeneralConverters.getGson().fromJson(
                        portionsJson,
                        new com.google.gson.reflect.TypeToken<List<FoodPortion>>(){}.getType()
                );
                if (portions != null) {
                    meal.setPortions(portions);
                }
            } catch (Exception e) {
                // Log error but continue
            }
        }

        // Convert tags from JSON
        if (tagsJson != null) {
            try {
                Set<String> tags = GeneralConverters.getGson().fromJson(
                        tagsJson,
                        new com.google.gson.reflect.TypeToken<Set<String>>(){}.getType()
                );
                if (tags != null) {
                    meal.setTags(tags);
                }
            } catch (Exception e) {
                // Log error but continue
            }
        }

        // NOTE: Nutrition NOT set here - must be loaded via MealWithNutrition
        return meal;
    }

    /**
     * Create MealEntity from Meal domain model
     * NOTE: This does NOT include nutrition - save separately to NutritionEntity
     */
    public static MealEntity fromMeal(Meal meal) {
        if (meal == null) return null;

        MealEntity entity = new MealEntity();

        entity.setId(meal.getId() != null ? meal.getId() : UUID.randomUUID().toString());
        entity.setUserId(meal.getUserId());
        entity.setCreatedAt(meal.getCreatedAt());
        entity.setLastUpdated(meal.getLastUpdated());

        // Set primary content fields (in currentLanguage)
        entity.setName(meal.getName(meal.getCurrentLanguage()));
        entity.setDescription(meal.getDescription(meal.getCurrentLanguage()));
        entity.setNotes(meal.getNotes(meal.getCurrentLanguage()));
        entity.setOccasion(meal.getOccasion(meal.getCurrentLanguage()));
        entity.setLocation(meal.getLocation(meal.getCurrentLanguage()));

        // Set language management
        entity.setCurrentLanguage(meal.getCurrentLanguage());
        entity.setNeedsDefaultLanguageUpdate(meal.needsDefaultLanguageUpdate());
        entity.setTranslations(meal.getTranslations());
        entity.setSearchableText(meal.getSearchableText());

        // Convert meal type
        if (meal.getMealType() != null) {
            entity.setMealType(meal.getMealType().getId());
        }

        // Convert LocalDateTime to timestamp
        if (meal.getStartTime() != null) {
            entity.setMealDateTime(meal.getStartTime()
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli());
        }

        // Set meal properties
        entity.setPlanned(meal.isPlanned());
        entity.setTemplate(meal.isTemplate());
        entity.setHomeMade(meal.isHomeMade());

        // Set allergen flags (NEW v3.0)
        entity.setAllergenFlags(meal.getAllergenFlags());

        entity.setEstimatedCost(meal.getEstimatedCost());
        entity.setSatisfaction(meal.getSatisfaction());
        entity.setImageUrl(meal.getImageUrl());

        // Convert portions to JSON
        List<FoodPortion> portions = meal.getPortions();
        if (portions != null && !portions.isEmpty()) {
            entity.setPortionsJson(GeneralConverters.getGson().toJson(portions));
        }

        // Convert tags to JSON
        Set<String> tags = meal.getTags();
        if (tags != null && !tags.isEmpty()) {
            entity.setTagsJson(GeneralConverters.getGson().toJson(tags));
        }

        // NOTE: Nutrition NOT saved here - must be saved separately to NutritionEntity

        return entity;
    }

    /**
     * Update last updated timestamp
     */
    public void touch() {
        this.lastUpdated = System.currentTimeMillis();
    }

    // ========== GETTERS AND SETTERS ==========

    @NonNull
    public String getId() { return id; }
    public void setId(@NonNull String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    // Primary content
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getOccasion() { return occasion; }
    public void setOccasion(String occasion) { this.occasion = occasion; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    // Language management
    public String getCurrentLanguage() { return currentLanguage; }
    public void setCurrentLanguage(String currentLanguage) { this.currentLanguage = currentLanguage; }

    public boolean isNeedsDefaultLanguageUpdate() { return needsDefaultLanguageUpdate; }
    public void setNeedsDefaultLanguageUpdate(boolean needsDefaultLanguageUpdate) {
        this.needsDefaultLanguageUpdate = needsDefaultLanguageUpdate;
    }

    public Map<String, MealTranslation> getTranslations() { return translations; }
    public void setTranslations(Map<String, MealTranslation> translations) {
        this.translations = translations;
    }

    public String getSearchableText() { return searchableText; }
    public void setSearchableText(String searchableText) { this.searchableText = searchableText; }

    // Meal properties
    public String getMealType() { return mealType; }
    public void setMealType(String mealType) { this.mealType = mealType; }

    public long getMealDateTime() { return mealDateTime; }
    public void setMealDateTime(long mealDateTime) { this.mealDateTime = mealDateTime; }

    public boolean isPlanned() { return isPlanned; }
    public void setPlanned(boolean planned) { isPlanned = planned; }

    public boolean isTemplate() { return isTemplate; }
    public void setTemplate(boolean template) { isTemplate = template; }

    public boolean isHomeMade() { return isHomeMade; }
    public void setHomeMade(boolean homeMade) { isHomeMade = homeMade; }

    public int getAllergenFlags() { return allergenFlags; }
    public void setAllergenFlags(int allergenFlags) { this.allergenFlags = allergenFlags; }

    // Meal context
    public Double getEstimatedCost() { return estimatedCost; }
    public void setEstimatedCost(Double estimatedCost) { this.estimatedCost = estimatedCost; }

    public float getSatisfaction() { return satisfaction; }
    public void setSatisfaction(float satisfaction) { this.satisfaction = satisfaction; }

    // Media
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    // Ingredients
    public String getPortionsJson() { return portionsJson; }
    public void setPortionsJson(String portionsJson) { this.portionsJson = portionsJson; }

    // Tags
    public String getTagsJson() { return tagsJson; }
    public void setTagsJson(String tagsJson) { this.tagsJson = tagsJson; }

    // Quality metrics
    public float getCompletenessScore() { return completenessScore; }
    public void setCompletenessScore(float completenessScore) {
        this.completenessScore = completenessScore;
    }

    public int getAccessCount() { return accessCount; }
    public void setAccessCount(int accessCount) { this.accessCount = accessCount; }

    // Timestamps
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(long lastUpdated) { this.lastUpdated = lastUpdated; }
}