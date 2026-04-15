package li.masciul.sugardaddi.core.models;

import li.masciul.sugardaddi.core.enums.DataSource;
import li.masciul.sugardaddi.core.enums.MealType;
import li.masciul.sugardaddi.core.enums.ProductType;
import li.masciul.sugardaddi.core.enums.Unit;
import li.masciul.sugardaddi.core.interfaces.Nutritional;
import li.masciul.sugardaddi.core.interfaces.Searchable;
import li.masciul.sugardaddi.core.interfaces.Categorizable;
import li.masciul.sugardaddi.core.interfaces.AllergenAware;
import li.masciul.sugardaddi.core.utils.AllergenUtils;
import android.content.Context;
import android.util.Log;

import java.util.*;
import java.time.LocalDateTime;
import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Meal - Meal tracking and planning model (REFACTORED v2.0)
 *
 * HYBRID LANGUAGE ARCHITECTURE (same as FoodProduct and Recipe):
 * - Primary fields store content in currentLanguage
 * - Translation map stores all OTHER languages using MealTranslation
 * - DEFAULT_LANGUAGE: Target language for the app (typically "en")
 * - Smart getters/setters with automatic language switching
 */
public class Meal implements Nutritional, Searchable, Categorizable, AllergenAware {

    private static final String TAG = "Meal";

    // ========== LANGUAGE CONFIGURATION ==========
    public static final String DEFAULT_LANGUAGE = "en";

    // ========== IDENTIFICATION ==========
    private String id;
    private String originalId;
    private SourceIdentifier sourceIdentifier;
    private ProductType productType = ProductType.MEAL;
    private long lastUpdated;
    private long createdAt;

    // ========== PRIMARY CONTENT (in currentLanguage) ==========
    private String name;                    // Meal name in currentLanguage
    private String description;             // Meal description
    private String notes;                   // Personal notes
    private String occasion;                // Occasion context
    private String location;                // Location where consumed

    // ========== LANGUAGE TRACKING ==========
    private String currentLanguage = DEFAULT_LANGUAGE;
    private boolean needsDefaultLanguageUpdate = false;
    private Map<String, MealTranslation> translations = new HashMap<>();
    private String searchableText;

    // ========== MEAL STRUCTURE ==========
    private MealType mealType = MealType.LUNCH;
    private List<FoodPortion> portions = new ArrayList<>();

    // ========== TIMING ==========
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private int durationMinutes;

    // ========== STATUS ==========
    private boolean isPlanned = true;
    private boolean isTemplate = false;
    private boolean isHomeMade = true;

    // ========== NUTRITION ==========
    private Nutrition providedNutrition;
    private Nutrition calculatedNutrition;
    private boolean nutritionNeedsRecalculation = true;

    // ========== STRUCTURED DATA (language-independent) ==========
    private List<Category> categoryList = new ArrayList<>();
    private List<String> categoryHierarchy = new ArrayList<>();
    private int allergenFlags = 0;

    // ========== METADATA ==========
    private String userId;
    private String imageUrl;
    private Set<String> tags = new HashSet<>();
    private float satisfaction = 0.0f;
    private Double estimatedCost;
    private float completenessScore = 0.0f;
    private int accessCount = 0;

    // ========== CONSTRUCTORS ==========

    public Meal() {
        long currentTime = System.currentTimeMillis();
        this.id = generateMealId();
        this.originalId = this.id;
        this.createdAt = currentTime;
        this.lastUpdated = currentTime;
        this.startTime = LocalDateTime.now();
        this.sourceIdentifier = new SourceIdentifier(DataSource.USER.getId(), this.id);
    }

    public Meal(MealType mealType, LocalDateTime startTime) {
        this();
        this.mealType = mealType;
        this.startTime = startTime;
        this.name = createDefaultMealName();
    }

    public Meal(MealType mealType, String customName) {
        this();
        this.mealType = mealType;
        this.name = customName;
    }

    private static String generateMealId() {
        return "MEAL_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String createDefaultMealName() {
        if (startTime == null) return mealType.getDisplayName();

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM d");
        String date = startTime.format(formatter);
        return mealType.getDisplayName() + " - " + date;
    }

    // ========== SMART LANGUAGE GETTERS ==========

    public String getName(String language) {
        if (language == null || language.equals(currentLanguage)) {
            return name;
        }

        MealTranslation translation = translations.get(language);
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

        MealTranslation translation = translations.get(language);
        if (translation != null && translation.getDescription() != null) {
            return translation.getDescription();
        }

        // Generate from portions if no description
        if (description == null || description.trim().isEmpty()) {
            return createDescriptionFromPortions(language);
        }

        return description;
    }

    public String getDescription() {
        return getDescription(DEFAULT_LANGUAGE);
    }

    public String getNotes(String language) {
        if (language == null || language.equals(currentLanguage)) {
            return notes;
        }

        MealTranslation translation = translations.get(language);
        if (translation != null && translation.getNotes() != null) {
            return translation.getNotes();
        }

        return notes;
    }

    public String getNotes() {
        return getNotes(DEFAULT_LANGUAGE);
    }

    public String getOccasion(String language) {
        if (language == null || language.equals(currentLanguage)) {
            return occasion;
        }

        MealTranslation translation = translations.get(language);
        if (translation != null && translation.getOccasion() != null) {
            return translation.getOccasion();
        }

        return occasion;
    }

    public String getOccasion() {
        return getOccasion(DEFAULT_LANGUAGE);
    }

    public String getLocation(String language) {
        if (language == null || language.equals(currentLanguage)) {
            return location;
        }

        MealTranslation translation = translations.get(language);
        if (translation != null && translation.getLocation() != null) {
            return translation.getLocation();
        }

        return location;
    }

    public String getLocation() {
        return getLocation(DEFAULT_LANGUAGE);
    }

    // ========== SMART LANGUAGE SETTERS ==========

    public void setName(String name, String language) {
        if (name == null || language == null) return;

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

        updateSearchableText();
        touch();
    }

    public void setName(String name) {
        setName(name, DEFAULT_LANGUAGE);
    }

    public void setDescription(String description, String language) {
        if (description == null || language == null) return;

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
    }

    public void setDescription(String description) {
        setDescription(description, DEFAULT_LANGUAGE);
    }

    public void setNotes(String notes, String language) {
        if (notes == null || language == null) return;

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
        setNotes(notes, DEFAULT_LANGUAGE);
    }

    public void setOccasion(String occasion, String language) {
        if (occasion == null || language == null) return;

        if (language.equals(DEFAULT_LANGUAGE)) {
            if (!currentLanguage.equals(DEFAULT_LANGUAGE) && this.occasion != null) {
                getOrCreateTranslation(currentLanguage).setOccasion(this.occasion);
            }
            this.occasion = occasion;
            this.currentLanguage = DEFAULT_LANGUAGE;
            this.needsDefaultLanguageUpdate = false;
        } else if (language.equals(currentLanguage)) {
            this.occasion = occasion;
            if (!currentLanguage.equals(DEFAULT_LANGUAGE)) {
                getOrCreateTranslation(currentLanguage).setOccasion(occasion);
            }
        } else {
            getOrCreateTranslation(language).setOccasion(occasion);
        }

        updateSearchableText();
        touch();
    }

    public void setOccasion(String occasion) {
        setOccasion(occasion, DEFAULT_LANGUAGE);
    }

    public void setLocation(String location, String language) {
        if (location == null || language == null) return;

        if (language.equals(DEFAULT_LANGUAGE)) {
            if (!currentLanguage.equals(DEFAULT_LANGUAGE) && this.location != null) {
                getOrCreateTranslation(currentLanguage).setLocation(this.location);
            }
            this.location = location;
            this.currentLanguage = DEFAULT_LANGUAGE;
            this.needsDefaultLanguageUpdate = false;
        } else if (language.equals(currentLanguage)) {
            this.location = location;
            if (!currentLanguage.equals(DEFAULT_LANGUAGE)) {
                getOrCreateTranslation(currentLanguage).setLocation(location);
            }
        } else {
            getOrCreateTranslation(language).setLocation(location);
        }

        touch();
    }

    public void setLocation(String location) {
        setLocation(location, DEFAULT_LANGUAGE);
    }

    // ========== TRANSLATION MANAGEMENT ==========

    private MealTranslation getOrCreateTranslation(String language) {
        MealTranslation translation = translations.get(language);
        if (translation == null) {
            translation = new MealTranslation();
            translations.put(language, translation);
        }
        return translation;
    }

    public Set<String> getAvailableLanguages() {
        Set<String> languages = new HashSet<>();
        languages.add(currentLanguage);
        languages.addAll(translations.keySet());
        return languages;
    }

    public boolean hasLanguage(String language) {
        if (language.equals(currentLanguage)) {
            return name != null;
        }
        MealTranslation translation = translations.get(language);
        return translation != null && translation.hasContent();
    }

    public Map<String, MealTranslation> getTranslations() {
        return translations;
    }

    public void setTranslations(Map<String, MealTranslation> translations) {
        this.translations = translations != null ? translations : new HashMap<>();
        updateSearchableText();
    }

    public void addTranslation(String language, MealTranslation translation) {
        if (language != null && translation != null) {
            translations.put(language, translation);
            updateSearchableText();
        }
    }

    // ========== SEARCH OPTIMIZATION ==========

    private void updateSearchableText() {
        Set<String> searchTerms = new HashSet<>();

        // Add primary language content
        addSearchTerm(searchTerms, name);
        addSearchTerm(searchTerms, description);
        addSearchTerm(searchTerms, occasion);

        // Add all translations
        for (MealTranslation translation : translations.values()) {
            addSearchTerm(searchTerms, translation.getName());
            addSearchTerm(searchTerms, translation.getDescription());
            addSearchTerm(searchTerms, translation.getOccasion());
        }

        this.searchableText = String.join(" ", searchTerms).toLowerCase();
    }

    private void addSearchTerm(Set<String> terms, String value) {
        if (value != null && !value.trim().isEmpty()) {
            terms.add(value.trim().toLowerCase());
        }
    }

    public String getSearchableText() {
        if (searchableText == null) {
            updateSearchableText();
        }
        return searchableText;
    }

    public void setSearchableText(String searchableText) {
        this.searchableText = searchableText;
    }

    // ========== LANGUAGE STATE MANAGEMENT ==========

    public String getCurrentLanguage() {
        return currentLanguage;
    }

    public void setCurrentLanguage(String currentLanguage) {
        this.currentLanguage = currentLanguage;
        this.needsDefaultLanguageUpdate = !currentLanguage.equals(DEFAULT_LANGUAGE);
    }

    public boolean needsDefaultLanguageUpdate() {
        return needsDefaultLanguageUpdate;
    }

    public void setNeedsDefaultLanguageUpdate(boolean needsUpdate) {
        this.needsDefaultLanguageUpdate = needsUpdate;
    }

    // ========== PORTIONS MANAGEMENT ==========

    public List<FoodPortion> getPortions() {
        return new ArrayList<>(portions);
    }

    public void setPortions(List<FoodPortion> portions) {
        this.portions = portions != null ? new ArrayList<>(portions) : new ArrayList<>();
        markNutritionForRecalculation();
    }

    public void addPortion(FoodPortion portion) {
        if (portion != null) {
            portions.add(portion);
            markNutritionForRecalculation();
        }
    }

    public void removePortion(int index) {
        if (index >= 0 && index < portions.size()) {
            portions.remove(index);
            markNutritionForRecalculation();
        }
    }

    public boolean hasPortions() {
        return portions != null && !portions.isEmpty();
    }

    /**
     * Create description from portions
     */
    private String createDescriptionFromPortions(String language) {
        if (portions.isEmpty()) {
            return "Empty meal";
        }

        if (portions.size() == 1) {
            FoodPortion portion = portions.get(0);
            if (portion.getFoodProduct() != null) {
                return portion.getFoodProduct().getName(language);
            } else if (portion.getRecipe() != null) {
                return portion.getRecipe().getName(language);
            }
        }

        return portions.size() + " items";
    }

    // ========== NUTRITION CALCULATION ==========

    public void calculateNutrition() {
        if (!nutritionNeedsRecalculation && calculatedNutrition != null) {
            return;
        }

        if (portions.isEmpty()) {
            calculatedNutrition = null;
            nutritionNeedsRecalculation = false;
            return;
        }

        Nutrition totalNutrition = new Nutrition();
        boolean hasAnyNutrition = false;

        for (FoodPortion portion : portions) {
            Log.d(TAG, "calculateNutrition - portion: " + portion.getItemId());
            Log.d(TAG, "  foodProduct: " + portion.getFoodProduct());
            Nutrition portionNutrition = portion.calculateNutrition();
            Log.d(TAG, "  portionNutrition: " + portionNutrition);
            if (portionNutrition != null && portionNutrition.hasData()) {
                totalNutrition = totalNutrition.add(portionNutrition);
                hasAnyNutrition = true;
            }
        }

        Log.d(TAG, "calculateNutrition - COMPLETE: hasAnyNutrition=" + hasAnyNutrition);
        Log.d(TAG, "  calculatedNutrition before assignment: " + calculatedNutrition);
        Log.d(TAG, "  totalNutrition: " + totalNutrition);

        if (hasAnyNutrition) {
            calculatedNutrition = totalNutrition;
            calculatedNutrition.calculateCompleteness();
        } else {
            calculatedNutrition = null;
        }

        Log.d(TAG, "calculateNutrition - FINAL calculatedNutrition: " + calculatedNutrition);

        nutritionNeedsRecalculation = false;
        touch();
    }

    private void markNutritionForRecalculation() {
        nutritionNeedsRecalculation = true;
        touch();
    }

    // ========== TIMING MANAGEMENT ==========

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
        updateDuration();
        touch();
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
        updateDuration();
        touch();
    }

    public int getDurationMinutes() {
        if (durationMinutes > 0) {
            return durationMinutes;
        }
        if (startTime != null && endTime != null) {
            return (int) Duration.between(startTime, endTime).toMinutes();
        }
        return 0;
    }

    public void setDurationMinutes(int durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    private void updateDuration() {
        if (startTime != null && endTime != null) {
            this.durationMinutes = (int) Duration.between(startTime, endTime).toMinutes();
        }
    }

    public long getMealTimestamp() {
        if (startTime == null) return 0;
        return startTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    public void setMealTimestamp(long timestamp) {
        this.startTime = LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(timestamp),
                ZoneId.systemDefault());
        touch();
    }

    // ========== INTERFACE IMPLEMENTATIONS ==========

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
        Set<String> allTags = new HashSet<>(tags);

        allTags.add(mealType.name().toLowerCase());
        allTags.add(isPlanned ? "planned" : "consumed");
        if (isTemplate) allTags.add("template");
        if (isHomeMade) allTags.add("homemade");

        if (startTime != null) {
            String dayOfWeek = startTime.getDayOfWeek().name().toLowerCase();
            allTags.add(dayOfWeek);
        }

        return allTags;
    }

    @Override
    public DataSource getDataSource() {
        return DataSource.USER;
    }

    @Override
    public List<Category> getCategories(String language) {
        return new ArrayList<>(categoryList);
    }

    @Override
    public String getPrimaryCategory(String language) {
        if (!categoryList.isEmpty()) {
            return categoryList.get(0).getName();
        }
        return mealType.getDisplayName();
    }

    @Override
    public List<String> getCategoryHierarchy(String language) {
        return new ArrayList<>(categoryHierarchy);
    }

    @Override
    public Nutrition getNutrition() {
        if (providedNutrition != null) {
            return providedNutrition;
        }

        if (nutritionNeedsRecalculation) {
            calculateNutrition();
        }

        Log.d(TAG, "getNutrition - returning calculatedNutrition: " + calculatedNutrition);
        Log.d(TAG, "  providedNutrition: " + providedNutrition);
        Log.d(TAG, "  nutritionNeedsRecalculation: " + nutritionNeedsRecalculation);

        return calculatedNutrition;
    }

    public void setProvidedNutrition(Nutrition nutrition) {
        this.providedNutrition = nutrition;
        touch();
    }

    @Override
    public boolean hasNutritionData() {
        Nutrition nutrition = getNutrition();
        return nutrition != null && nutrition.hasData();
    }

    @Override
    public ServingSize getServingSize() {
        ServingSize serving = new ServingSize();
        serving.setDescription("1 meal");
        serving.setQuantity(1.0);
        serving.setUnit(Unit.OTHER);
        serving.setUnitText("meal");
        return serving;
    }

    @Override
    public boolean isLiquid() {
        if (portions.isEmpty()) return false;

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

    @Override
    public int getAllergenFlags() {
        return allergenFlags;
    }

    @Override
    public void setAllergenFlags(int allergenFlags) {
        this.allergenFlags = allergenFlags;
    }

    public boolean hasAllergen(int allergenBitFlag) {
        return AllergenUtils.hasAllergen(allergenFlags, allergenBitFlag);
    }

    public String getAllergenNames(Context context) {
        return AllergenUtils.getAllergenNames(context, allergenFlags);
    }

    @Override
    public ProductType getProductType() {
        return productType;
    }

    // ========== STANDARD GETTERS AND SETTERS ==========

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getOriginalId() {
        return originalId;
    }

    public void setOriginalId(String originalId) {
        this.originalId = originalId;
    }

    public SourceIdentifier getSourceIdentifier() {
        return sourceIdentifier;
    }

    public void setSourceIdentifier(SourceIdentifier sourceIdentifier) {
        this.sourceIdentifier = sourceIdentifier;
    }

    public long getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(long lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public MealType getMealType() {
        return mealType;
    }

    public void setMealType(MealType mealType) {
        this.mealType = mealType;
    }

    public boolean isPlanned() {
        return isPlanned;
    }

    public void setPlanned(boolean planned) {
        this.isPlanned = planned;
        touch();
    }

    public boolean isTemplate() {
        return isTemplate;
    }

    public void setTemplate(boolean template) {
        this.isTemplate = template;
    }

    public boolean isHomeMade() {
        return isHomeMade;
    }

    public void setHomeMade(boolean homeMade) {
        this.isHomeMade = homeMade;
    }

    public List<Category> getCategoryList() {
        return categoryList;
    }

    public void setCategoryList(List<Category> categoryList) {
        this.categoryList = categoryList != null ? categoryList : new ArrayList<>();
    }

    public void setCategoryHierarchy(List<String> categoryHierarchy) {
        this.categoryHierarchy = categoryHierarchy != null ? categoryHierarchy : new ArrayList<>();
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public Set<String> getTags() {
        return new HashSet<>(tags);
    }

    public void setTags(Set<String> tags) {
        this.tags = tags != null ? new HashSet<>(tags) : new HashSet<>();
    }

    public void addTag(String tag) {
        this.tags.add(tag);
    }

    public void removeTag(String tag) {
        this.tags.remove(tag);
    }

    public float getSatisfaction() {
        return satisfaction;
    }

    public void setSatisfaction(float satisfaction) {
        this.satisfaction = Math.max(0.0f, Math.min(5.0f, satisfaction));
    }

    public Double getEstimatedCost() {
        return estimatedCost;
    }

    public void setEstimatedCost(Double estimatedCost) {
        this.estimatedCost = estimatedCost;
    }

    public float getCompletenessScore() {
        return completenessScore;
    }

    public int getAccessCount() {
        return accessCount;
    }

    public void incrementAccessCount() {
        this.accessCount++;
        touch();
    }

    // ========== UTILITY METHODS ==========

    public void touch() {
        this.lastUpdated = System.currentTimeMillis();
    }

    public boolean hasImage() {
        return imageUrl != null && !imageUrl.isEmpty();
    }

    public void calculateCompleteness() {
        int totalFields = 0;
        int filledFields = 0;

        totalFields += 4;
        if (name != null && !name.trim().isEmpty()) filledFields++;
        if (hasPortions()) filledFields++;
        if (startTime != null) filledFields++;
        if (mealType != null) filledFields++;

        totalFields += 4;
        if (hasImage()) filledFields++;
        if (notes != null && !notes.trim().isEmpty()) filledFields++;
        if (satisfaction > 0) filledFields++;
        if (!tags.isEmpty()) filledFields++;

        this.completenessScore = totalFields > 0 ? (float) filledFields / totalFields : 0;
    }

    /**
     * Create a copy of this meal
     */
    public Meal copy() {
        Meal copy = new Meal();

        // Copy basic properties
        copy.name = this.name;
        copy.description = this.description;
        copy.notes = this.notes;
        copy.occasion = this.occasion;
        copy.location = this.location;
        copy.currentLanguage = this.currentLanguage;
        copy.needsDefaultLanguageUpdate = this.needsDefaultLanguageUpdate;
        copy.mealType = this.mealType;
        copy.startTime = this.startTime;
        copy.endTime = this.endTime;
        copy.durationMinutes = this.durationMinutes;
        copy.isPlanned = this.isPlanned;
        copy.isHomeMade = this.isHomeMade;

        // Copy portions
        for (FoodPortion portion : this.portions) {
            copy.addPortion(portion.copy());
        }

        // Copy translations
        for (Map.Entry<String, MealTranslation> entry : this.translations.entrySet()) {
            copy.addTranslation(entry.getKey(), entry.getValue().copy());
        }

        // Copy nutrition if provided
        if (this.providedNutrition != null) {
            copy.providedNutrition = this.providedNutrition.copy();
        }

        // Copy metadata
        copy.imageUrl = this.imageUrl;
        copy.tags = new HashSet<>(this.tags);
        copy.satisfaction = this.satisfaction;
        copy.estimatedCost = this.estimatedCost;
        copy.allergenFlags = this.allergenFlags;

        return copy;
    }

    /**
     * Create template from this meal
     */
    public Meal createTemplate(String templateName) {
        Meal template = this.copy();

        template.setId(generateMealId());
        template.setTemplate(true);
        template.setPlanned(true);
        template.setName(templateName);
        template.setDescription("Template based on " + this.getName());

        return template;
    }

    @Override
    public String toString() {
        return String.format("Meal{name='%s', type=%s, portions=%d, lang='%s', needsUpdate=%s}",
                name, mealType, portions.size(), currentLanguage, needsDefaultLanguageUpdate);
    }
}