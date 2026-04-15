package li.masciul.sugardaddi.data.database.entities;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.Index;
import androidx.room.TypeConverters;
import androidx.annotation.NonNull;

import li.masciul.sugardaddi.core.models.Recipe;
import li.masciul.sugardaddi.core.models.RecipeTranslation;
import li.masciul.sugardaddi.core.models.RecipeStepMetadata;
import li.masciul.sugardaddi.core.models.RecipeStepTranslation;
import li.masciul.sugardaddi.core.models.FoodPortion;
import li.masciul.sugardaddi.core.enums.Difficulty;
import li.masciul.sugardaddi.data.database.converters.GeneralConverters;
import li.masciul.sugardaddi.data.database.converters.RecipeTranslationMapConverter;
import li.masciul.sugardaddi.data.database.converters.RecipeStepMetadataListConverter;
import li.masciul.sugardaddi.data.database.converters.RecipeStepTranslationListConverter;

import java.util.*;

/**
 * RecipeEntity - Room entity for recipe storage (v3.0 - Hybrid Translation)
 *
 * ARCHITECTURE UPDATE v3.0:
 * - Nutrition data stored separately in NutritionEntity table
 * - NEW: Hybrid translation system with split step architecture
 * - Primary fields store content in currentLanguage
 * - Translation map stores all OTHER languages (RecipeTranslation objects)
 * - Step structure (metadata) stored once
 * - Step translations stored per language
 * - Use RecipeWithNutrition relation for queries needing nutrition
 */
@Entity(
        tableName = "recipes",
        indices = {
                @Index(value = {"authorId"}),
                @Index(value = {"isFavorite"}),
                @Index(value = {"isPublic"}),
                @Index(value = {"isTemplate"}),
                @Index(value = {"lastUpdated"})
        }
)
public class RecipeEntity {

    @PrimaryKey
    @NonNull
    private String id = "";

    // ========== IDENTIFICATION ==========
    private String authorId;                        // User who created this recipe

    // ========== PRIMARY CONTENT (in currentLanguage) ==========
    private String name;
    private String description;
    private String instructions;                    // Full text (optional)
    private String cuisine;
    private String notes;
    private String yieldDescription;
    private String recipeSource;

    @TypeConverters(GeneralConverters.class)
    private List<String> equipmentNeeded;

    @TypeConverters(GeneralConverters.class)
    private List<String> cookingTips;

    // ========== STEP ARCHITECTURE v3.0 ==========
    @TypeConverters(RecipeStepMetadataListConverter.class)
    private List<RecipeStepMetadata> stepStructure;          // Universal metadata (once)

    @TypeConverters(RecipeStepTranslationListConverter.class)
    private List<RecipeStepTranslation> stepTranslations;    // Primary language text

    // ========== LANGUAGE MANAGEMENT ==========
    private String currentLanguage = "en";
    private boolean needsDefaultLanguageUpdate = false;

    @TypeConverters(RecipeTranslationMapConverter.class)
    private Map<String, RecipeTranslation> translations;

    private String searchableText;

    // ========== RECIPE PROPERTIES ==========
    private int servings = 1;
    private int prepTimeMinutes = 0;
    private int cookTimeMinutes = 0;
    private String difficulty;                      // Stored as String (ID)

    // ========== INGREDIENTS ==========
    private String portionsJson;                    // List<FoodPortion> as JSON

    // ========== DIETARY FLAGS ==========
    private boolean isVegan = false;
    private boolean isVegetarian = false;
    private boolean isGlutenFree = false;
    private boolean isDairyFree = false;
    private boolean isKeto = false;
    private boolean isPaleo = false;

    // ========== ALLERGEN FLAGS (NEW v3.0) ==========
    /**
     * Combined allergen flags from all recipe ingredients
     * Calculated by aggregating allergenFlags from all FoodProducts in portions
     */
    private int allergenFlags = 0;

    // ========== STATUS FLAGS ==========
    private boolean isPublic = false;
    private boolean isFavorite = false;
    private boolean isTemplate = false;

    // ========== RATINGS ==========
    private float rating = 0.0f;
    private int ratingCount = 0;

    // ========== MEDIA ==========
    private String imageUrl;

    // ========== TAGS ==========
    private String tagsJson;                        // Set<String> as JSON

    // ========== QUALITY METRICS ==========
    private float completenessScore = 0.0f;
    private int accessCount = 0;

    // ========== TIMESTAMPS ==========
    private long createdAt;
    private long lastUpdated;

    // ========== CONSTRUCTORS ==========

    public RecipeEntity() {
        long now = System.currentTimeMillis();
        this.createdAt = now;
        this.lastUpdated = now;
        this.translations = new HashMap<>();
        this.equipmentNeeded = new ArrayList<>();
        this.cookingTips = new ArrayList<>();
        this.stepStructure = new ArrayList<>();
        this.stepTranslations = new ArrayList<>();
    }

    // ========== CONVERSION METHODS ==========

    /**
     * Convert RecipeEntity to Recipe domain model
     * NOTE: Nutrition must be loaded separately from NutritionEntity
     */
    public Recipe toRecipe() {
        Recipe recipe = new Recipe();

        recipe.setId(this.id);
        recipe.setAuthorId(this.authorId);
        recipe.setCreatedAt(this.createdAt);
        recipe.setLastUpdated(this.lastUpdated);

        // Set primary content fields
        recipe.setName(this.name);
        recipe.setDescription(this.description);
        recipe.setInstructions(this.instructions);
        recipe.setCuisine(this.cuisine);
        recipe.setNotes(this.notes);
        recipe.setYieldDescription(this.yieldDescription);
        recipe.setRecipeSource(this.recipeSource);
        recipe.setEquipmentNeeded(this.equipmentNeeded);
        recipe.setCookingTips(this.cookingTips);

        // Set step architecture
        recipe.setStepStructure(this.stepStructure);
        recipe.setStepTranslations(this.stepTranslations);

        // Set language management
        recipe.setCurrentLanguage(this.currentLanguage);
        recipe.setNeedsDefaultLanguageUpdate(this.needsDefaultLanguageUpdate);
        recipe.setTranslations(this.translations != null ? this.translations : new HashMap<>());
        recipe.setSearchableText(this.searchableText);

        // Set recipe properties
        recipe.setServings(this.servings);
        recipe.setPrepTimeMinutes(this.prepTimeMinutes);
        recipe.setCookTimeMinutes(this.cookTimeMinutes);

        // Convert difficulty
        if (this.difficulty != null) {
            try {
                recipe.setDifficulty(Difficulty.fromId(this.difficulty));
            } catch (Exception e) {
                recipe.setDifficulty(Difficulty.MEDIUM);
            }
        }

        // Set dietary flags
        recipe.setVegan(this.isVegan);
        recipe.setVegetarian(this.isVegetarian);
        recipe.setGlutenFree(this.isGlutenFree);
        recipe.setDairyFree(this.isDairyFree);
        recipe.setKeto(this.isKeto);
        recipe.setPaleo(this.isPaleo);

        // Set allergen flags (NEW v3.0)
        recipe.setAllergenFlags(this.allergenFlags);

        // Set status flags
        recipe.setPublic(this.isPublic);
        recipe.setFavorite(this.isFavorite);
        recipe.setTemplate(this.isTemplate);

        // Set ratings
        recipe.setRating(this.rating);
        recipe.setRatingCount(this.ratingCount);

        // Set media
        recipe.setImageUrl(this.imageUrl);

        // Convert portions from JSON
        if (portionsJson != null) {
            try {
                List<FoodPortion> portions = GeneralConverters.getGson().fromJson(
                        portionsJson,
                        new com.google.gson.reflect.TypeToken<List<FoodPortion>>(){}.getType()
                );
                if (portions != null) {
                    recipe.setPortions(portions);
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
                    recipe.setTags(tags);
                }
            } catch (Exception e) {
                // Log error but continue
            }
        }

        // NOTE: Nutrition NOT set here - must be loaded via RecipeWithNutrition

        return recipe;
    }

    /**
     * Create RecipeEntity from Recipe domain model
     * NOTE: This does NOT include nutrition - save separately to NutritionEntity
     */
    public static RecipeEntity fromRecipe(Recipe recipe) {
        if (recipe == null) return null;

        RecipeEntity entity = new RecipeEntity();

        entity.setId(recipe.getId() != null ? recipe.getId() : UUID.randomUUID().toString());
        entity.setAuthorId(recipe.getAuthorId());
        entity.setCreatedAt(recipe.getCreatedAt());
        entity.setLastUpdated(recipe.getLastUpdated());

        // Set primary content fields (in currentLanguage)
        entity.setName(recipe.getName(recipe.getCurrentLanguage()));
        entity.setDescription(recipe.getDescription(recipe.getCurrentLanguage()));
        entity.setInstructions(recipe.getInstructions(recipe.getCurrentLanguage()));
        entity.setCuisine(recipe.getCuisine(recipe.getCurrentLanguage()));
        entity.setNotes(recipe.getNotes(recipe.getCurrentLanguage()));
        entity.setYieldDescription(recipe.getYieldDescription(recipe.getCurrentLanguage()));
        entity.setRecipeSource(recipe.getRecipeSource(recipe.getCurrentLanguage()));
        entity.setEquipmentNeeded(recipe.getEquipmentNeeded(recipe.getCurrentLanguage()));
        entity.setCookingTips(recipe.getCookingTips(recipe.getCurrentLanguage()));

        // Set step architecture
        entity.setStepStructure(recipe.getStepStructure());
        entity.setStepTranslations(recipe.getStepTranslations());

        // Set language management
        entity.setCurrentLanguage(recipe.getCurrentLanguage());
        entity.setNeedsDefaultLanguageUpdate(recipe.needsDefaultLanguageUpdate());
        entity.setTranslations(recipe.getTranslations());
        entity.setSearchableText(recipe.getSearchableText());

        // Set recipe properties
        entity.setServings(recipe.getServings());
        entity.setPrepTimeMinutes(recipe.getPrepTimeMinutes());
        entity.setCookTimeMinutes(recipe.getCookTimeMinutes());

        // Convert difficulty
        if (recipe.getDifficulty() != null) {
            entity.setDifficulty(recipe.getDifficulty().getId());
        }

        // Set dietary flags
        entity.setVegan(recipe.isVegan());
        entity.setVegetarian(recipe.isVegetarian());
        entity.setGlutenFree(recipe.isGlutenFree());
        entity.setDairyFree(recipe.isDairyFree());
        entity.setKeto(recipe.isKeto());
        entity.setPaleo(recipe.isPaleo());

        // Set allergen flags (NEW v3.0)
        entity.setAllergenFlags(recipe.getAllergenFlags());

        // Set status flags
        entity.setPublic(recipe.isPublic());
        entity.setFavorite(recipe.isFavorite());
        entity.setTemplate(recipe.isTemplate());

        // Set ratings
        entity.setRating(recipe.getRating());
        entity.setRatingCount(recipe.getRatingCount());

        // Set media
        entity.setImageUrl(recipe.getImageUrl());

        // Convert portions to JSON
        List<FoodPortion> portions = recipe.getPortions();
        if (portions != null && !portions.isEmpty()) {
            entity.setPortionsJson(GeneralConverters.getGson().toJson(portions));
        }

        // Convert tags to JSON
        Set<String> tags = recipe.getTags();
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

    public String getAuthorId() { return authorId; }
    public void setAuthorId(String authorId) { this.authorId = authorId; }

    // Primary content
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getInstructions() { return instructions; }
    public void setInstructions(String instructions) { this.instructions = instructions; }

    public String getCuisine() { return cuisine; }
    public void setCuisine(String cuisine) { this.cuisine = cuisine; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getYieldDescription() { return yieldDescription; }
    public void setYieldDescription(String yieldDescription) { this.yieldDescription = yieldDescription; }

    public String getRecipeSource() { return recipeSource; }
    public void setRecipeSource(String recipeSource) { this.recipeSource = recipeSource; }

    public List<String> getEquipmentNeeded() { return equipmentNeeded; }
    public void setEquipmentNeeded(List<String> equipmentNeeded) { this.equipmentNeeded = equipmentNeeded; }

    public List<String> getCookingTips() { return cookingTips; }
    public void setCookingTips(List<String> cookingTips) { this.cookingTips = cookingTips; }

    // Step architecture
    public List<RecipeStepMetadata> getStepStructure() { return stepStructure; }
    public void setStepStructure(List<RecipeStepMetadata> stepStructure) {
        this.stepStructure = stepStructure;
    }

    public List<RecipeStepTranslation> getStepTranslations() { return stepTranslations; }
    public void setStepTranslations(List<RecipeStepTranslation> stepTranslations) {
        this.stepTranslations = stepTranslations;
    }

    // Language management
    public String getCurrentLanguage() { return currentLanguage; }
    public void setCurrentLanguage(String currentLanguage) { this.currentLanguage = currentLanguage; }

    public boolean isNeedsDefaultLanguageUpdate() { return needsDefaultLanguageUpdate; }
    public void setNeedsDefaultLanguageUpdate(boolean needsDefaultLanguageUpdate) {
        this.needsDefaultLanguageUpdate = needsDefaultLanguageUpdate;
    }

    public Map<String, RecipeTranslation> getTranslations() { return translations; }
    public void setTranslations(Map<String, RecipeTranslation> translations) {
        this.translations = translations;
    }

    public String getSearchableText() { return searchableText; }
    public void setSearchableText(String searchableText) { this.searchableText = searchableText; }

    // Recipe properties
    public int getServings() { return servings; }
    public void setServings(int servings) { this.servings = servings; }

    public int getPrepTimeMinutes() { return prepTimeMinutes; }
    public void setPrepTimeMinutes(int prepTimeMinutes) { this.prepTimeMinutes = prepTimeMinutes; }

    public int getCookTimeMinutes() { return cookTimeMinutes; }
    public void setCookTimeMinutes(int cookTimeMinutes) { this.cookTimeMinutes = cookTimeMinutes; }

    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }

    public String getPortionsJson() { return portionsJson; }
    public void setPortionsJson(String portionsJson) { this.portionsJson = portionsJson; }

    // Dietary flags
    public boolean isVegan() { return isVegan; }
    public void setVegan(boolean vegan) { isVegan = vegan; }

    public boolean isVegetarian() { return isVegetarian; }
    public void setVegetarian(boolean vegetarian) { isVegetarian = vegetarian; }

    public boolean isGlutenFree() { return isGlutenFree; }
    public void setGlutenFree(boolean glutenFree) { isGlutenFree = glutenFree; }

    public boolean isDairyFree() { return isDairyFree; }
    public void setDairyFree(boolean dairyFree) { isDairyFree = dairyFree; }

    public boolean isKeto() { return isKeto; }
    public void setKeto(boolean keto) { isKeto = keto; }

    public boolean isPaleo() { return isPaleo; }
    public void setPaleo(boolean paleo) { isPaleo = paleo; }

    public int getAllergenFlags() { return allergenFlags; }
    public void setAllergenFlags(int allergenFlags) { this.allergenFlags = allergenFlags; }

    // Status flags
    public boolean isPublic() { return isPublic; }
    public void setPublic(boolean aPublic) { isPublic = aPublic; }

    public boolean isFavorite() { return isFavorite; }
    public void setFavorite(boolean favorite) { isFavorite = favorite; }

    public boolean isTemplate() { return isTemplate; }
    public void setTemplate(boolean template) { isTemplate = template; }

    // Ratings
    public float getRating() { return rating; }
    public void setRating(float rating) { this.rating = rating; }

    public int getRatingCount() { return ratingCount; }
    public void setRatingCount(int ratingCount) { this.ratingCount = ratingCount; }

    // Media
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

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