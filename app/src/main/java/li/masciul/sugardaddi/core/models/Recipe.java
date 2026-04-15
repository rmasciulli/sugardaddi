package li.masciul.sugardaddi.core.models;

import li.masciul.sugardaddi.core.enums.DataSource;
import li.masciul.sugardaddi.core.enums.Difficulty;
import li.masciul.sugardaddi.core.enums.ProductType;
import li.masciul.sugardaddi.core.enums.Unit;
import li.masciul.sugardaddi.core.interfaces.Nutritional;
import li.masciul.sugardaddi.core.interfaces.Searchable;
import li.masciul.sugardaddi.core.interfaces.Categorizable;
import li.masciul.sugardaddi.core.interfaces.AllergenAware;
import li.masciul.sugardaddi.core.utils.AllergenUtils;
import android.content.Context;

import java.util.*;

/**
 * Recipe - User-created recipe model (REFACTORED v3.0)
 *
 * HYBRID LANGUAGE ARCHITECTURE:
 * - Primary fields store content in currentLanguage
 * - Translation map stores all OTHER languages using RecipeTranslation
 * - DEFAULT_LANGUAGE: Target language for the app (typically "en")
 * - Smart getters/setters with automatic language switching
 *
 * STEP ARCHITECTURE v3.0:
 * - stepStructure: List<RecipeStepMetadata> - Universal metadata (stored ONCE)
 * - stepTranslations: List<RecipeStepTranslation> - Primary language text
 * - RecipeTranslation contains stepTranslations for other languages
 * - Combined via getStep(stepNumber, language) for display
 *
 * STORAGE EFFICIENCY:
 * Recipe with 10 steps in 3 languages:
 *   OLD: 30 RecipeStep objects (full duplication)
 *   NEW: 10 metadata + 30 translations (60% less duplication)
 *
 * EDITING PATTERN:
 * - Edit metadata: updateStepMetadata() - affects ALL languages
 * - Edit text: updateStepTranslation() - affects ONE language
 * - Add step: addStep() - creates metadata + translation
 */
public class Recipe implements Nutritional, Searchable, Categorizable, AllergenAware {

    // ========== LANGUAGE CONFIGURATION ==========
    public static final String DEFAULT_LANGUAGE = "en";

    // ========== IDENTIFICATION ==========
    private String id;
    private String originalId;
    private SourceIdentifier sourceIdentifier;
    private ProductType productType = ProductType.RECIPE;
    private long lastUpdated;
    private long createdAt;

    // ========== PRIMARY CONTENT (in currentLanguage) ==========
    private String name;
    private String description;
    private String instructions;                    // Full text (optional)
    private String cuisine;
    private String notes;
    private List<String> equipmentNeeded;
    private List<String> cookingTips;
    private String yieldDescription;
    private String recipeSource;

    // ========== STEP STRUCTURE ==========
    private List<RecipeStepMetadata> stepStructure;
    private List<RecipeStepTranslation> stepTranslations;

    // ========== RECIPE PROPERTIES ==========
    private List<FoodPortion> portions;
    private Nutrition nutrition;
    private ServingSize servingSize;
    private Integer servings;
    private Integer prepTimeMinutes;
    private Integer cookTimeMinutes;
    private Integer totalTimeMinutes;
    private Difficulty difficulty;

    // ========== DIETARY FLAGS ==========
    private boolean isVegan = false;
    private boolean isVegetarian = false;
    private boolean isGlutenFree = false;
    private boolean isDairyFree = false;
    private boolean isKeto = false;
    private boolean isPaleo = false;

    // ========== STATUS FLAGS ==========
    private boolean isFavorite = false;
    private boolean isTemplate = false;

    // ========== MEDIA ==========
    private String imageUrl;

    // ========== TAGS ==========
    private Set<String> tags = new HashSet<>();

    // ========== CATEGORIZATION ==========
    private int allergenFlags;
    private List<Category> categories;
    private String categoriesText;

    // ========== METADATA ==========
    private boolean isPublic = false;
    private boolean isVerified = false;
    private String authorId;
    private int favoriteCount = 0;
    private float rating = 0.0f;
    private int ratingCount = 0;
    private float completenessScore = 0.0f;

    // ========== LANGUAGE MANAGEMENT ==========
    private String currentLanguage = DEFAULT_LANGUAGE;
    private boolean needsDefaultLanguageUpdate = false;
    private Map<String, RecipeTranslation> translations;
    private String searchableText;

    // ========== CONSTRUCTORS ==========

    public Recipe() {
        this.id = UUID.randomUUID().toString();
        this.createdAt = System.currentTimeMillis();
        this.lastUpdated = this.createdAt;
        this.translations = new HashMap<>();
        this.portions = new ArrayList<>();
        this.allergenFlags = 0;
        this.categories = new ArrayList<>();
        this.equipmentNeeded = new ArrayList<>();
        this.cookingTips = new ArrayList<>();
        this.stepStructure = new ArrayList<>();
        this.stepTranslations = new ArrayList<>();
        this.tags = new HashSet<>();
    }

    public Recipe(String name) {
        this();
        this.name = name;
        updateSearchableText();
    }

    // ========== STEP MANAGEMENT ==========

    /**
     * Get complete step (metadata + translation) for display
     */
    public RecipeStep getStep(int stepNumber, String language) {
        RecipeStepMetadata metadata = findStepMetadata(stepNumber);
        if (metadata == null) {
            return null;
        }

        RecipeStepTranslation translation;
        if (language == null || language.equals(currentLanguage)) {
            translation = findStepTranslation(stepTranslations, stepNumber);
        } else {
            RecipeTranslation recipeTranslation = translations.get(language);
            if (recipeTranslation != null) {
                translation = recipeTranslation.getStepTranslation(stepNumber);
            } else {
                translation = findStepTranslation(stepTranslations, stepNumber);
            }
        }

        return new RecipeStep(metadata, translation);
    }

    public RecipeStep getStep(int stepNumber) {
        return getStep(stepNumber, DEFAULT_LANGUAGE);
    }

    /**
     * Get all steps for display
     */
    public List<RecipeStep> getSteps(String language) {
        if (stepStructure == null || stepStructure.isEmpty()) {
            return new ArrayList<>();
        }

        List<RecipeStep> steps = new ArrayList<>();
        for (RecipeStepMetadata metadata : stepStructure) {
            RecipeStep step = getStep(metadata.getStepNumber(), language);
            if (step != null) {
                steps.add(step);
            }
        }
        return steps;
    }

    public List<RecipeStep> getSteps() {
        return getSteps(DEFAULT_LANGUAGE);
    }

    /**
     * Add new step
     */
    public void addStep(String instruction, Integer durationMinutes, String equipment, String language) {
        int stepNumber = stepStructure.size() + 1;

        // Create metadata
        RecipeStepMetadata metadata = new RecipeStepMetadata(stepNumber);
        metadata.setDurationMinutes(durationMinutes);
        metadata.setEquipment(equipment);
        stepStructure.add(metadata);

        // Create translation
        RecipeStepTranslation translation = new RecipeStepTranslation(stepNumber, instruction);

        if (language == null || language.equals(currentLanguage)) {
            stepTranslations.add(translation);
        } else {
            RecipeTranslation recipeTranslation = getOrCreateTranslation(language);
            recipeTranslation.getStepTranslations().add(translation);
        }

        touch();
        updateSearchableText();
    }

    public void addStep(String instruction, Integer durationMinutes, String equipment) {
        addStep(instruction, durationMinutes, equipment, currentLanguage);
    }

    public void addStep(String instruction) {
        addStep(instruction, null, null, currentLanguage);
    }

    /**
     * Update step metadata (affects ALL languages)
     */
    public void updateStepMetadata(int stepNumber, Integer durationMinutes, String equipment,
                                   boolean isOptional, String imageUrl) {
        RecipeStepMetadata metadata = findStepMetadata(stepNumber);
        if (metadata == null) {
            return;
        }

        if (durationMinutes != null) {
            metadata.setDurationMinutes(durationMinutes);
        }
        if (equipment != null) {
            metadata.setEquipment(equipment);
        }
        metadata.setOptional(isOptional);
        if (imageUrl != null) {
            metadata.setImageUrl(imageUrl);
        }

        touch();
    }

    /**
     * Update step translation (affects ONE language)
     */
    public void updateStepTranslation(int stepNumber, String instruction, String tip, String language) {
        if (language == null || language.equals(currentLanguage)) {
            RecipeStepTranslation translation = findStepTranslation(stepTranslations, stepNumber);
            if (translation != null) {
                if (instruction != null) {
                    translation.setInstruction(instruction);
                }
                if (tip != null) {
                    translation.setTip(tip);
                }
            }
        } else {
            RecipeTranslation recipeTranslation = translations.get(language);
            if (recipeTranslation != null) {
                recipeTranslation.setStepTranslation(stepNumber, instruction, tip);
            }
        }

        touch();
        updateSearchableText();
    }

    /**
     * Remove step (from all languages)
     */
    public void removeStep(int stepNumber) {
        stepStructure.removeIf(m -> m.getStepNumber() == stepNumber);
        stepTranslations.removeIf(t -> t.getStepNumber() == stepNumber);

        for (RecipeTranslation translation : translations.values()) {
            translation.getStepTranslations().removeIf(t -> t.getStepNumber() == stepNumber);
        }

        renumberSteps();
        touch();
        updateSearchableText();
    }

    /**
     * Renumber steps sequentially
     */
    private void renumberSteps() {
        for (int i = 0; i < stepStructure.size(); i++) {
            stepStructure.get(i).setStepNumber(i + 1);
        }

        for (int i = 0; i < stepTranslations.size(); i++) {
            stepTranslations.get(i).setStepNumber(i + 1);
        }

        for (RecipeTranslation translation : translations.values()) {
            List<RecipeStepTranslation> steps = translation.getStepTranslations();
            for (int i = 0; i < steps.size(); i++) {
                steps.get(i).setStepNumber(i + 1);
            }
        }
    }

    /**
     * Find metadata by step number
     */
    private RecipeStepMetadata findStepMetadata(int stepNumber) {
        if (stepStructure == null) {
            return null;
        }

        for (RecipeStepMetadata metadata : stepStructure) {
            if (metadata.getStepNumber() == stepNumber) {
                return metadata;
            }
        }
        return null;
    }

    /**
     * Find translation by step number
     */
    private RecipeStepTranslation findStepTranslation(List<RecipeStepTranslation> translations, int stepNumber) {
        if (translations == null) {
            return null;
        }

        for (RecipeStepTranslation translation : translations) {
            if (translation.getStepNumber() == stepNumber) {
                return translation;
            }
        }
        return null;
    }

    // ========== STEP ACCESSORS ==========

    public List<RecipeStepMetadata> getStepStructure() {
        return stepStructure != null ? stepStructure : new ArrayList<>();
    }

    public void setStepStructure(List<RecipeStepMetadata> stepStructure) {
        this.stepStructure = stepStructure != null ? stepStructure : new ArrayList<>();
        touch();
    }

    public List<RecipeStepTranslation> getStepTranslations() {
        return stepTranslations != null ? stepTranslations : new ArrayList<>();
    }

    public void setStepTranslations(List<RecipeStepTranslation> stepTranslations) {
        this.stepTranslations = stepTranslations != null ? stepTranslations : new ArrayList<>();
        touch();
        updateSearchableText();
    }

    public int getStepCount() {
        return stepStructure != null ? stepStructure.size() : 0;
    }

    public Integer calculateTotalTimeFromSteps() {
        if (stepStructure == null || stepStructure.isEmpty()) {
            return null;
        }

        int total = 0;
        for (RecipeStepMetadata metadata : stepStructure) {
            if (metadata.getDurationMinutes() != null) {
                total += metadata.getDurationMinutes();
            } else {
                return null;
            }
        }
        return total;
    }

    // ========== SMART GETTERS ==========

    public String getName(String language) {
        if (language == null || language.equals(currentLanguage)) {
            return name;
        }

        RecipeTranslation translation = translations.get(language);
        if (translation != null && translation.getName() != null) {
            return translation.getName();
        }

        return name;
    }

    public String getName() {
        return getName(DEFAULT_LANGUAGE);
    }

    public String getDescription(String language) {
        if (language == null || language.equals(currentLanguage)) {
            return description;
        }

        RecipeTranslation translation = translations.get(language);
        if (translation != null && translation.getDescription() != null) {
            return translation.getDescription();
        }

        return description;
    }

    public String getDescription() {
        return getDescription(DEFAULT_LANGUAGE);
    }

    public String getInstructions(String language) {
        if (language == null || language.equals(currentLanguage)) {
            return instructions;
        }

        RecipeTranslation translation = translations.get(language);
        if (translation != null && translation.getInstructions() != null) {
            return translation.getInstructions();
        }

        return instructions;
    }

    public String getInstructions() {
        return getInstructions(DEFAULT_LANGUAGE);
    }

    public String getCuisine(String language) {
        if (language == null || language.equals(currentLanguage)) {
            return cuisine;
        }

        RecipeTranslation translation = translations.get(language);
        if (translation != null && translation.getCuisine() != null) {
            return translation.getCuisine();
        }

        return cuisine;
    }

    public String getCuisine() {
        return getCuisine(DEFAULT_LANGUAGE);
    }

    public String getNotes(String language) {
        if (language == null || language.equals(currentLanguage)) {
            return notes;
        }

        RecipeTranslation translation = translations.get(language);
        if (translation != null && translation.getNotes() != null) {
            return translation.getNotes();
        }

        return notes;
    }

    public String getNotes() {
        return getNotes(DEFAULT_LANGUAGE);
    }

    public List<String> getEquipmentNeeded(String language) {
        if (language == null || language.equals(currentLanguage)) {
            return equipmentNeeded != null ? new ArrayList<>(equipmentNeeded) : new ArrayList<>();
        }

        RecipeTranslation translation = translations.get(language);
        if (translation != null && translation.hasEquipment()) {
            return translation.getEquipmentNeeded();
        }

        return equipmentNeeded != null ? new ArrayList<>(equipmentNeeded) : new ArrayList<>();
    }

    public List<String> getEquipmentNeeded() {
        return getEquipmentNeeded(DEFAULT_LANGUAGE);
    }

    public List<String> getCookingTips(String language) {
        if (language == null || language.equals(currentLanguage)) {
            return cookingTips != null ? new ArrayList<>(cookingTips) : new ArrayList<>();
        }

        RecipeTranslation translation = translations.get(language);
        if (translation != null && translation.hasCookingTips()) {
            return translation.getCookingTips();
        }

        return cookingTips != null ? new ArrayList<>(cookingTips) : new ArrayList<>();
    }

    public List<String> getCookingTips() {
        return getCookingTips(DEFAULT_LANGUAGE);
    }

    public String getYieldDescription(String language) {
        if (language == null || language.equals(currentLanguage)) {
            return yieldDescription;
        }

        RecipeTranslation translation = translations.get(language);
        if (translation != null && translation.getYieldDescription() != null) {
            return translation.getYieldDescription();
        }

        return yieldDescription;
    }

    public String getYieldDescription() {
        return getYieldDescription(DEFAULT_LANGUAGE);
    }

    public String getRecipeSource(String language) {
        if (language == null || language.equals(currentLanguage)) {
            return recipeSource;
        }

        RecipeTranslation translation = translations.get(language);
        if (translation != null && translation.getRecipeSource() != null) {
            return translation.getRecipeSource();
        }

        return recipeSource;
    }

    public String getRecipeSource() {
        return getRecipeSource(DEFAULT_LANGUAGE);
    }

    public String getCategoriesText(String language) {
        if (language == null || language.equals(currentLanguage)) {
            return categoriesText;
        }

        return categoriesText;
    }

    public String getCategoriesText() {
        return getCategoriesText(DEFAULT_LANGUAGE);
    }

    // ========== SMART SETTERS ==========

    public void setName(String name, String language) {
        if (language.equals(DEFAULT_LANGUAGE)) {
            if (!currentLanguage.equals(DEFAULT_LANGUAGE) && this.name != null) {
                getOrCreateTranslation(currentLanguage).setName(this.name);
            }

            this.name = name;
            this.currentLanguage = DEFAULT_LANGUAGE;
            this.needsDefaultLanguageUpdate = false;

        } else if (language.equals(currentLanguage)) {
            this.name = name;

            if (!currentLanguage.equals(DEFAULT_LANGUAGE)) {
                getOrCreateTranslation(currentLanguage).setName(name);
            }

        } else {
            getOrCreateTranslation(language).setName(name);
        }

        touch();
        updateSearchableText();
    }

    public void setName(String name) {
        setName(name, currentLanguage);
    }

    public void setDescription(String description, String language) {
        if (language.equals(DEFAULT_LANGUAGE)) {
            if (!currentLanguage.equals(DEFAULT_LANGUAGE) && this.description != null) {
                getOrCreateTranslation(currentLanguage).setDescription(this.description);
            }

            this.description = description;
            this.currentLanguage = DEFAULT_LANGUAGE;
            this.needsDefaultLanguageUpdate = false;

        } else if (language.equals(currentLanguage)) {
            this.description = description;

            if (!currentLanguage.equals(DEFAULT_LANGUAGE)) {
                getOrCreateTranslation(currentLanguage).setDescription(description);
            }

        } else {
            getOrCreateTranslation(language).setDescription(description);
        }

        touch();
        updateSearchableText();
    }

    public void setDescription(String description) {
        setDescription(description, currentLanguage);
    }

    public void setInstructions(String instructions, String language) {
        if (language.equals(DEFAULT_LANGUAGE)) {
            if (!currentLanguage.equals(DEFAULT_LANGUAGE) && this.instructions != null) {
                getOrCreateTranslation(currentLanguage).setInstructions(this.instructions);
            }

            this.instructions = instructions;
            this.currentLanguage = DEFAULT_LANGUAGE;
            this.needsDefaultLanguageUpdate = false;

        } else if (language.equals(currentLanguage)) {
            this.instructions = instructions;

            if (!currentLanguage.equals(DEFAULT_LANGUAGE)) {
                getOrCreateTranslation(currentLanguage).setInstructions(instructions);
            }

        } else {
            getOrCreateTranslation(language).setInstructions(instructions);
        }

        touch();
        updateSearchableText();
    }

    public void setInstructions(String instructions) {
        setInstructions(instructions, currentLanguage);
    }

    public void setCuisine(String cuisine, String language) {
        if (language.equals(DEFAULT_LANGUAGE)) {
            if (!currentLanguage.equals(DEFAULT_LANGUAGE) && this.cuisine != null) {
                getOrCreateTranslation(currentLanguage).setCuisine(this.cuisine);
            }

            this.cuisine = cuisine;
            this.currentLanguage = DEFAULT_LANGUAGE;
            this.needsDefaultLanguageUpdate = false;

        } else if (language.equals(currentLanguage)) {
            this.cuisine = cuisine;

            if (!currentLanguage.equals(DEFAULT_LANGUAGE)) {
                getOrCreateTranslation(currentLanguage).setCuisine(cuisine);
            }

        } else {
            getOrCreateTranslation(language).setCuisine(cuisine);
        }

        touch();
    }

    public void setCuisine(String cuisine) {
        setCuisine(cuisine, currentLanguage);
    }

    public void setNotes(String notes, String language) {
        if (language.equals(DEFAULT_LANGUAGE)) {
            if (!currentLanguage.equals(DEFAULT_LANGUAGE) && this.notes != null) {
                getOrCreateTranslation(currentLanguage).setNotes(this.notes);
            }

            this.notes = notes;
            this.currentLanguage = DEFAULT_LANGUAGE;
            this.needsDefaultLanguageUpdate = false;

        } else if (language.equals(currentLanguage)) {
            this.notes = notes;

            if (!currentLanguage.equals(DEFAULT_LANGUAGE)) {
                getOrCreateTranslation(currentLanguage).setNotes(notes);
            }

        } else {
            getOrCreateTranslation(language).setNotes(notes);
        }

        touch();
    }

    public void setNotes(String notes) {
        setNotes(notes, currentLanguage);
    }

    public void setEquipmentNeeded(List<String> equipment, String language) {
        if (language.equals(DEFAULT_LANGUAGE)) {
            if (!currentLanguage.equals(DEFAULT_LANGUAGE) && this.equipmentNeeded != null) {
                getOrCreateTranslation(currentLanguage).setEquipmentNeeded(this.equipmentNeeded);
            }

            this.equipmentNeeded = equipment;
            this.currentLanguage = DEFAULT_LANGUAGE;
            this.needsDefaultLanguageUpdate = false;

        } else if (language.equals(currentLanguage)) {
            this.equipmentNeeded = equipment;

            if (!currentLanguage.equals(DEFAULT_LANGUAGE)) {
                getOrCreateTranslation(currentLanguage).setEquipmentNeeded(equipment);
            }

        } else {
            getOrCreateTranslation(language).setEquipmentNeeded(equipment);
        }

        touch();
    }

    public void setEquipmentNeeded(List<String> equipment) {
        setEquipmentNeeded(equipment, currentLanguage);
    }

    public void setCookingTips(List<String> tips, String language) {
        if (language.equals(DEFAULT_LANGUAGE)) {
            if (!currentLanguage.equals(DEFAULT_LANGUAGE) && this.cookingTips != null) {
                getOrCreateTranslation(currentLanguage).setCookingTips(this.cookingTips);
            }

            this.cookingTips = tips;
            this.currentLanguage = DEFAULT_LANGUAGE;
            this.needsDefaultLanguageUpdate = false;

        } else if (language.equals(currentLanguage)) {
            this.cookingTips = tips;

            if (!currentLanguage.equals(DEFAULT_LANGUAGE)) {
                getOrCreateTranslation(currentLanguage).setCookingTips(tips);
            }

        } else {
            getOrCreateTranslation(language).setCookingTips(tips);
        }

        touch();
    }

    public void setCookingTips(List<String> tips) {
        setCookingTips(tips, currentLanguage);
    }

    public void setYieldDescription(String yieldDesc, String language) {
        if (language.equals(DEFAULT_LANGUAGE)) {
            if (!currentLanguage.equals(DEFAULT_LANGUAGE) && this.yieldDescription != null) {
                getOrCreateTranslation(currentLanguage).setYieldDescription(this.yieldDescription);
            }

            this.yieldDescription = yieldDesc;
            this.currentLanguage = DEFAULT_LANGUAGE;
            this.needsDefaultLanguageUpdate = false;

        } else if (language.equals(currentLanguage)) {
            this.yieldDescription = yieldDesc;

            if (!currentLanguage.equals(DEFAULT_LANGUAGE)) {
                getOrCreateTranslation(currentLanguage).setYieldDescription(yieldDesc);
            }

        } else {
            getOrCreateTranslation(language).setYieldDescription(yieldDesc);
        }

        touch();
    }

    public void setYieldDescription(String yieldDesc) {
        setYieldDescription(yieldDesc, currentLanguage);
    }

    public void setRecipeSource(String source, String language) {
        if (language.equals(DEFAULT_LANGUAGE)) {
            if (!currentLanguage.equals(DEFAULT_LANGUAGE) && this.recipeSource != null) {
                getOrCreateTranslation(currentLanguage).setRecipeSource(this.recipeSource);
            }

            this.recipeSource = source;
            this.currentLanguage = DEFAULT_LANGUAGE;
            this.needsDefaultLanguageUpdate = false;

        } else if (language.equals(currentLanguage)) {
            this.recipeSource = source;

            if (!currentLanguage.equals(DEFAULT_LANGUAGE)) {
                getOrCreateTranslation(currentLanguage).setRecipeSource(source);
            }

        } else {
            getOrCreateTranslation(language).setRecipeSource(source);
        }

        touch();
    }

    public void setRecipeSource(String source) {
        setRecipeSource(source, currentLanguage);
    }

    public void setCategoriesText(String categoriesText) {
        this.categoriesText = categoriesText;
        touch();
        updateSearchableText();
    }

    // ========== SIMPLE GETTERS/SETTERS ==========

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getOriginalId() { return originalId; }
    public void setOriginalId(String originalId) { this.originalId = originalId; }

    public SourceIdentifier getSourceIdentifier() { return sourceIdentifier; }
    public void setSourceIdentifier(SourceIdentifier sourceIdentifier) { this.sourceIdentifier = sourceIdentifier; }

    public long getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(long lastUpdated) { this.lastUpdated = lastUpdated; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public Integer getServings() { return servings; }
    public void setServings(Integer servings) { this.servings = servings; touch(); }

    public Integer getPrepTimeMinutes() { return prepTimeMinutes; }
    public void setPrepTimeMinutes(Integer prepTimeMinutes) { this.prepTimeMinutes = prepTimeMinutes; touch(); }

    public Integer getCookTimeMinutes() { return cookTimeMinutes; }
    public void setCookTimeMinutes(Integer cookTimeMinutes) { this.cookTimeMinutes = cookTimeMinutes; touch(); }

    public Integer getTotalTimeMinutes() { return totalTimeMinutes; }
    public void setTotalTimeMinutes(Integer totalTimeMinutes) { this.totalTimeMinutes = totalTimeMinutes; touch(); }

    public Difficulty getDifficulty() { return difficulty; }
    public void setDifficulty(Difficulty difficulty) { this.difficulty = difficulty; touch(); }

    // ========== PORTION MANAGEMENT METHODS ==========

    public List<FoodPortion> getPortions() { return portions != null ? portions : new ArrayList<>(); }
    public void setPortions(List<FoodPortion> portions) { this.portions = portions; touch(); }

    public void addPortion(FoodPortion portion) {
        if (portion != null) {
            if (portions == null) {
                portions = new ArrayList<>();
            }
            portions.add(portion);
            touch();
        }
    }

    public void removePortion(int index) {
        if (portions != null && index >= 0 && index < portions.size()) {
            portions.remove(index);
            touch();
        }
    }

    public void updatePortion(int index, FoodPortion portion) {
        if (portions != null && portion != null && index >= 0 && index < portions.size()) {
            portions.set(index, portion);
            touch();
        }
    }

    // ========== DIETARY FLAGS ==========

    public boolean isVegan() { return isVegan; }
    public void setVegan(boolean vegan) { this.isVegan = vegan; touch(); }

    public boolean isVegetarian() { return isVegetarian; }
    public void setVegetarian(boolean vegetarian) { this.isVegetarian = vegetarian; touch(); }

    public boolean isGlutenFree() { return isGlutenFree; }
    public void setGlutenFree(boolean glutenFree) { this.isGlutenFree = glutenFree; touch(); }

    public boolean isDairyFree() { return isDairyFree; }
    public void setDairyFree(boolean dairyFree) { this.isDairyFree = dairyFree; touch(); }

    public boolean isKeto() { return isKeto; }
    public void setKeto(boolean keto) { this.isKeto = keto; touch(); }

    public boolean isPaleo() { return isPaleo; }
    public void setPaleo(boolean paleo) { this.isPaleo = paleo; touch(); }

    // ========== STATUS FLAGS ==========

    public boolean isFavorite() { return isFavorite; }
    public void setFavorite(boolean favorite) { this.isFavorite = favorite; touch(); }

    public boolean isTemplate() { return isTemplate; }
    public void setTemplate(boolean template) { this.isTemplate = template; touch(); }

    // ========== MEDIA ==========

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; touch(); }

    // ========== TAGS ==========

    public Set<String> getTags() { return tags != null ? tags : new HashSet<>(); }
    public void setTags(Set<String> tags) { this.tags = tags != null ? tags : new HashSet<>(); touch(); }

    public void addTag(String tag) {
        if (tags == null) {
            tags = new HashSet<>();
        }
        if (tag != null && !tag.trim().isEmpty()) {
            tags.add(tag.toLowerCase().trim());
            touch();
        }
    }

    public void removeTag(String tag) {
        if (tags != null && tag != null) {
            tags.remove(tag.toLowerCase().trim());
            touch();
        }
    }

    public boolean hasTag(String tag) {
        return tags != null && tag != null && tags.contains(tag.toLowerCase().trim());
    }

    public List<Category> getCategories() { return categories != null ? categories : new ArrayList<>(); }
    public void setCategories(List<Category> categories) { this.categories = categories; touch(); }

    public boolean isPublic() { return isPublic; }
    public void setPublic(boolean isPublic) { this.isPublic = isPublic; touch(); }

    public boolean isVerified() { return isVerified; }
    public void setVerified(boolean verified) { isVerified = verified; touch(); }

    public String getAuthorId() { return authorId; }
    public void setAuthorId(String authorId) { this.authorId = authorId; }

    public int getFavoriteCount() { return favoriteCount; }
    public void setFavoriteCount(int favoriteCount) { this.favoriteCount = favoriteCount; }

    public float getRating() { return rating; }
    public void setRating(float rating) { this.rating = rating; }

    public int getRatingCount() { return ratingCount; }
    public void setRatingCount(int ratingCount) { this.ratingCount = ratingCount; }

    // ========== LANGUAGE MANAGEMENT ==========

    public String getCurrentLanguage() { return currentLanguage; }
    public void setCurrentLanguage(String currentLanguage) { this.currentLanguage = currentLanguage; }

    public boolean needsDefaultLanguageUpdate() { return needsDefaultLanguageUpdate; }
    public void setNeedsDefaultLanguageUpdate(boolean needsDefaultLanguageUpdate) {
        this.needsDefaultLanguageUpdate = needsDefaultLanguageUpdate;
    }

    public Map<String, RecipeTranslation> getTranslations() {
        return translations != null ? translations : new HashMap<>();
    }

    public void setTranslations(Map<String, RecipeTranslation> translations) {
        this.translations = translations != null ? translations : new HashMap<>();
    }

    public String getSearchableText() { return searchableText; }
    public void setSearchableText(String searchableText) { this.searchableText = searchableText; }

    private RecipeTranslation getOrCreateTranslation(String language) {
        RecipeTranslation translation = translations.get(language);
        if (translation == null) {
            translation = new RecipeTranslation();
            translations.put(language, translation);
        }
        return translation;
    }

    public void touch() {
        this.lastUpdated = System.currentTimeMillis();
    }

    private void updateSearchableText() {
        StringBuilder sb = new StringBuilder();

        if (name != null) sb.append(name).append(" ");
        if (description != null) sb.append(description).append(" ");
        if (cuisine != null) sb.append(cuisine).append(" ");
        if (categoriesText != null) sb.append(categoriesText).append(" ");

        if (stepTranslations != null) {
            for (RecipeStepTranslation translation : stepTranslations) {
                if (translation.getInstruction() != null) {
                    sb.append(translation.getInstruction()).append(" ");
                }
            }
        }

        this.searchableText = sb.toString().toLowerCase().trim();
    }

    // ========== SEARCHABLE INTERFACE ==========

    @Override
    public String getSearchableId() {
        return id;
    }

    @Override
    public String getDisplayName(String language) {
        return getName(language);
    }

    @Override
    public Set<String> getSearchTags() {
        Set<String> allTags = new HashSet<>();

        // Add cuisine as tag
        if (cuisine != null) {
            allTags.add(cuisine.toLowerCase());
        }

        // Add difficulty
        if (difficulty != null) {
            allTags.add(difficulty.name().toLowerCase());
        }

        // Add recipe type
        allTags.add("recipe");
        allTags.add("user-created");

        // Add public/private status
        if (isPublic) {
            allTags.add("public");
        }

        // Add verified status
        if (isVerified) {
            allTags.add("verified");
        }

        return allTags;
    }

    public List<String> getSearchKeywords() {
        List<String> keywords = new ArrayList<>();

        if (name != null) keywords.add(name.toLowerCase());
        if (cuisine != null) keywords.add(cuisine.toLowerCase());
        if (description != null) {
            keywords.addAll(Arrays.asList(description.toLowerCase().split("\\s+")));
        }

        return keywords;
    }

    public boolean matchesQuery(String query) {
        if (query == null || query.trim().isEmpty()) {
            return true;
        }

        String lowerQuery = query.toLowerCase();
        return searchableText != null && searchableText.contains(lowerQuery);
    }

    @Override
    public DataSource getDataSource() {
        return DataSource.USER;  // Recipes are always user-created
    }

    // ========== CATEGORIZABLE INTERFACE ==========

    @Override
    public String getPrimaryCategory(String language) {
        if (categories != null && !categories.isEmpty()) {
            return categories.get(0).getName();
        }

        // Fallback to cuisine or "Recipes"
        if (cuisine != null && !cuisine.isEmpty()) {
            return cuisine;
        }

        return "Recipes";
    }

    @Override
    public List<String> getCategoryHierarchy(String language) {
        List<String> hierarchy = new ArrayList<>();

        // Build hierarchy: Recipes > Cuisine > Category
        hierarchy.add("Recipes");

        if (cuisine != null && !cuisine.isEmpty()) {
            hierarchy.add(cuisine);
        }

        if (categories != null && !categories.isEmpty()) {
            hierarchy.add(categories.get(0).getName());
        }

        return hierarchy;
    }

    @Override
    public List<Category> getCategories(String language) {
        return categories != null ? new ArrayList<>(categories) : new ArrayList<>();
    }

    public void addCategory(Category category) {
        if (categories == null) {
            categories = new ArrayList<>();
        }
        if (!categories.contains(category)) {
            categories.add(category);
            touch();
        }
    }

    public void removeCategory(Category category) {
        if (categories != null) {
            categories.remove(category);
            touch();
        }
    }

    @Override
    public ProductType getProductType() {
        return productType;
    }

    public void setProductType(ProductType productType) {
        this.productType = productType;
    }

    // ========== ALLERGENAWARE INTERFACE ==========

    @Override
    public int getAllergenFlags() {
        return allergenFlags;
    }

    @Override
    public void setAllergenFlags(int allergenFlags) {
        this.allergenFlags = allergenFlags;
        touch();
    }

    // Additional helper methods
    public boolean hasAllergen(int allergenBitFlag) {
        return AllergenUtils.hasAllergen(allergenFlags, allergenBitFlag);
    }

    public String getAllergenNames(Context context) {
        return AllergenUtils.getAllergenNames(context, allergenFlags);
    }

    // ========== NUTRITIONAL INTERFACE ==========

    @Override
    public Nutrition getNutrition() {
        return nutrition;
    }

    public void setNutrition(Nutrition nutrition) {
        this.nutrition = nutrition; touch();
    }

    @Override
    public boolean hasNutritionData() {
        return nutrition != null && nutrition.hasData();
    }

    @Override
    public ServingSize getServingSize() {
        return servingSize;
    }

    public void setServingSize(ServingSize servingSize) {
        this.servingSize = servingSize; touch();
    }

    @Override
    public boolean isLiquid() {
        if (portions == null || portions.isEmpty()) {
            return false;
        }

        for (FoodPortion portion : portions) {
            if (portion.getFoodProduct() != null && !portion.getFoodProduct().isLiquid()) {
                return false;
            }
            if (portion.getRecipe() != null && !portion.getRecipe().isLiquid()) {
                return false;
            }
        }
        return true;
    }

    // Helper method
    public Nutrition calculateNutrition() {
        Nutrition total = new Nutrition();

        if (portions != null) {
            for (FoodPortion portion : portions) {
                Nutrition portionNutrition = portion.calculateNutrition();
                if (portionNutrition != null) {
                    total = total.add(portionNutrition);
                }
            }
        }

        if (servings != null && servings > 0) {
            total = total.scale(1.0 / servings);
        }

        this.nutrition = total;
        return total;
    }

    /**
     * Calculate and update completeness score based on filled fields
     */
    public void calculateCompleteness() {
        int totalFields = 10;
        int filledFields = 0;

        // Basic info
        if (name != null && !name.trim().isEmpty()) filledFields++;
        if (description != null && !description.trim().isEmpty()) filledFields++;
        if (instructions != null && !instructions.trim().isEmpty()) filledFields++;

        // Recipe details
        if (cuisine != null && !cuisine.trim().isEmpty()) filledFields++;
        if (servings != null && servings > 0) filledFields++;
        if (prepTimeMinutes != null && prepTimeMinutes > 0) filledFields++;
        if (cookTimeMinutes != null && cookTimeMinutes > 0) filledFields++;

        // Additional content
        if (portions != null && !portions.isEmpty()) filledFields++;
        if (stepStructure != null && !stepStructure.isEmpty()) filledFields++;
        if (imageUrl != null && !imageUrl.isEmpty()) filledFields++;

        this.completenessScore = totalFields > 0 ? (float) filledFields / totalFields : 0;
    }

    // ========== COPY AND TEMPLATE METHODS ==========

    /**
     * Create a copy of this recipe
     */
    public Recipe copy() {
        Recipe copy = new Recipe();

        // Copy identification and metadata
        copy.authorId = this.authorId;
        copy.originalId = this.originalId;
        copy.sourceIdentifier = this.sourceIdentifier;

        // Copy primary content fields
        copy.name = this.name;
        copy.description = this.description;
        copy.instructions = this.instructions;
        copy.cuisine = this.cuisine;
        copy.notes = this.notes;
        copy.yieldDescription = this.yieldDescription;
        copy.recipeSource = this.recipeSource;
        copy.equipmentNeeded = this.equipmentNeeded != null ? new ArrayList<>(this.equipmentNeeded) : null;
        copy.cookingTips = this.cookingTips != null ? new ArrayList<>(this.cookingTips) : null;
        copy.categoriesText = this.categoriesText;

        // Copy step architecture
        copy.stepStructure = this.stepStructure != null ? new ArrayList<>(this.stepStructure) : null;
        copy.stepTranslations = this.stepTranslations != null ? new ArrayList<>(this.stepTranslations) : null;

        // Copy language management
        copy.currentLanguage = this.currentLanguage;
        copy.needsDefaultLanguageUpdate = this.needsDefaultLanguageUpdate;
        copy.translations = this.translations != null ? new HashMap<>(this.translations) : new HashMap<>();
        copy.searchableText = this.searchableText;

        // Copy recipe properties
        copy.servings = this.servings;
        copy.prepTimeMinutes = this.prepTimeMinutes;
        copy.cookTimeMinutes = this.cookTimeMinutes;
        copy.totalTimeMinutes = this.totalTimeMinutes;
        copy.difficulty = this.difficulty;

        // Copy portions (deep copy)
        if (this.portions != null) {
            copy.portions = new ArrayList<>();
            for (FoodPortion portion : this.portions) {
                copy.portions.add(portion.copy());
            }
        }

        // Copy dietary flags
        copy.isVegan = this.isVegan;
        copy.isVegetarian = this.isVegetarian;
        copy.isGlutenFree = this.isGlutenFree;
        copy.isDairyFree = this.isDairyFree;
        copy.isKeto = this.isKeto;
        copy.isPaleo = this.isPaleo;
        copy.allergenFlags = this.allergenFlags;

        // Copy status flags
        copy.isFavorite = this.isFavorite;
        copy.isTemplate = this.isTemplate;
        copy.isPublic = this.isPublic;
        copy.isVerified = this.isVerified;

        // Copy ratings and media
        copy.favoriteCount = this.favoriteCount;
        copy.rating = this.rating;
        copy.ratingCount = this.ratingCount;
        copy.imageUrl = this.imageUrl;

        // Copy tags
        copy.tags = this.tags != null ? new HashSet<>(this.tags) : new HashSet<>();

        // Copy categories
        copy.categories = this.categories != null ? new ArrayList<>(this.categories) : new ArrayList<>();

        // Copy nutrition if available
        if (this.nutrition != null) {
            copy.nutrition = this.nutrition.copy();
        }

        // Timestamps will be set when saving

        return copy;
    }

    /**
     * Create template from this recipe
     */
    public Recipe createTemplate(String templateName, String language) {
        Recipe template = this.copy();

        template.setId(UUID.randomUUID().toString());
        template.setTemplate(true);
        template.setPublic(false);
        template.setName(templateName, language);
        template.setDescription("Template based on " + this.getName(language), language);

        // Reset certain fields for template
        template.setFavorite(false);
        template.setFavoriteCount(0);
        template.setRating(0);
        template.setRatingCount(0);
        template.setCreatedAt(System.currentTimeMillis());
        template.setLastUpdated(System.currentTimeMillis());

        return template;
    }

    @Override
    public String toString() {
        return "Recipe{" +
                "name='" + name + '\'' +
                ", steps=" + getStepCount() +
                ", servings=" + servings +
                ", cuisine='" + cuisine + '\'' +
                ", currentLanguage='" + currentLanguage + '\'' +
                '}';
    }
}