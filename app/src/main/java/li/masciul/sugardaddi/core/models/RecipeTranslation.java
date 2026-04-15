package li.masciul.sugardaddi.core.models;

import java.util.*;

/**
 * RecipeTranslation - Lightweight translation container for Recipe (v3.0)
 *
 * ARCHITECTURE:
 * - stepTranslations: List<RecipeStepTranslation> (instruction text + tips)
 * - References Recipe.stepStructure via stepNumber
 * - Combined for display using Recipe.getStep(stepNumber, language)
 *
 * Contains ONLY translatable text fields for recipes.
 * Much lighter than LocalizedContent (10 fields vs 50+).
 */
public class RecipeTranslation {

    // ========== CORE TRANSLATABLE FIELDS ==========
    private String name;
    private String description;
    private String instructions;                    // Full instructions as single text (optional)
    private List<RecipeStepTranslation> stepTranslations;
    private String recipeSource;

    // ========== RECIPE-SPECIFIC FIELDS ==========
    private String cuisine;
    private String notes;
    private List<String> equipmentNeeded;
    private List<String> cookingTips;
    private String yieldDescription;

    // ========== METADATA ==========
    private long lastUpdated;
    private String source;                          // "manual", "community", "ai"
    private boolean verified;

    // ========== CONSTRUCTORS ==========

    public RecipeTranslation() {
        this.lastUpdated = System.currentTimeMillis();
        this.verified = false;
        this.stepTranslations = new ArrayList<>();
        this.equipmentNeeded = new ArrayList<>();
        this.cookingTips = new ArrayList<>();
    }

    public RecipeTranslation(String name) {
        this();
        this.name = name;
    }

    public RecipeTranslation(String name, String description, String instructions) {
        this();
        this.name = name;
        this.description = description;
        this.instructions = instructions;
    }

    // ========== GETTERS AND SETTERS ==========

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        touch();
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
        touch();
    }

    public String getInstructions() {
        return instructions;
    }

    public void setInstructions(String instructions) {
        this.instructions = instructions;
        touch();
    }

    public List<RecipeStepTranslation> getStepTranslations() {
        return stepTranslations != null ? stepTranslations : new ArrayList<>();
    }

    public void setStepTranslations(List<RecipeStepTranslation> stepTranslations) {
        this.stepTranslations = stepTranslations != null ? stepTranslations : new ArrayList<>();
        touch();
    }

    public String getRecipeSource() {
        return recipeSource;
    }

    public void setRecipeSource(String recipeSource) {
        this.recipeSource = recipeSource;
        touch();
    }

    public String getCuisine() {
        return cuisine;
    }

    public void setCuisine(String cuisine) {
        this.cuisine = cuisine;
        touch();
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
        touch();
    }

    public List<String> getEquipmentNeeded() {
        return equipmentNeeded != null ? equipmentNeeded : new ArrayList<>();
    }

    public void setEquipmentNeeded(List<String> equipmentNeeded) {
        this.equipmentNeeded = equipmentNeeded != null ? equipmentNeeded : new ArrayList<>();
        touch();
    }

    public List<String> getCookingTips() {
        return cookingTips != null ? cookingTips : new ArrayList<>();
    }

    public void setCookingTips(List<String> cookingTips) {
        this.cookingTips = cookingTips != null ? cookingTips : new ArrayList<>();
        touch();
    }

    public String getYieldDescription() {
        return yieldDescription;
    }

    public void setYieldDescription(String yieldDescription) {
        this.yieldDescription = yieldDescription;
        touch();
    }

    public long getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(long lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
        touch();
    }

    public boolean isVerified() {
        return verified;
    }

    public void setVerified(boolean verified) {
        this.verified = verified;
        touch();
    }

    // ========== HELPER METHODS ==========

    private void touch() {
        this.lastUpdated = System.currentTimeMillis();
    }

    public boolean hasStructuredSteps() {
        return stepTranslations != null && !stepTranslations.isEmpty();
    }

    public boolean isComplete() {
        return name != null && !name.trim().isEmpty() &&
                ((instructions != null && !instructions.trim().isEmpty()) ||
                        hasStructuredSteps());
    }

    public RecipeStepTranslation getStepTranslation(int stepNumber) {
        if (stepTranslations == null) {
            return null;
        }

        for (RecipeStepTranslation translation : stepTranslations) {
            if (translation.getStepNumber() == stepNumber) {
                return translation;
            }
        }
        return null;
    }

    public void setStepTranslation(int stepNumber, String instruction, String tip) {
        if (stepTranslations == null) {
            stepTranslations = new ArrayList<>();
        }

        RecipeStepTranslation translation = getStepTranslation(stepNumber);
        if (translation == null) {
            translation = new RecipeStepTranslation(stepNumber, instruction);
            translation.setTip(tip);
            stepTranslations.add(translation);
        } else {
            translation.setInstruction(instruction);
            translation.setTip(tip);
        }

        touch();
    }

    public int getStepCount() {
        return stepTranslations != null ? stepTranslations.size() : 0;
    }

    public boolean hasEquipment() {
        return equipmentNeeded != null && !equipmentNeeded.isEmpty();
    }

    public boolean hasCookingTips() {
        return cookingTips != null && !cookingTips.isEmpty();
    }

    // ========== OBJECT METHODS ==========

    @Override
    public String toString() {
        return "RecipeTranslation{" +
                "name='" + name + '\'' +
                ", steps=" + getStepCount() +
                ", verified=" + verified +
                '}';
    }
}