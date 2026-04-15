package li.masciul.sugardaddi.core.models;

import java.util.*;

/**
 * MealTranslation - Lightweight translation container for Meal
 *
 * Contains ONLY translatable text fields for meals.
 * Much lighter than LocalizedContent (5 fields vs 50+).
 *
 * Design Philosophy:
 * - Store only meal-specific translatable content
 * - Minimal fields since meals are primarily composition-based
 * - Support for meal context (occasion, notes)
 * - Language-agnostic portions handled by Meal directly
 *
 * Key Differences from RecipeTranslation:
 * - Simpler: just name, description, notes, occasion
 * - No instructions (meals are assemblies, not recipes)
 * - Focus on context and description
 */
public class MealTranslation {

    // ========== CORE TRANSLATABLE FIELDS ==========
    private String name;                    // Meal name ("Quick Breakfast" / "Petit-déjeuner rapide")
    private String description;             // Meal description
    private String notes;                   // Personal notes about the meal
    private String occasion;                // Occasion context ("Quick lunch" / "Déjeuner rapide")
    private String location;                // Location where consumed (if translatable, like "At home")

    // ========== METADATA ==========
    private long lastUpdated;               // When this translation was last updated
    private String source;                  // Translation source: "manual", "auto"
    private boolean verified;               // Is this translation verified?

    // ========== CONSTRUCTORS ==========

    public MealTranslation() {
        this.lastUpdated = System.currentTimeMillis();
        this.verified = false;
    }

    /**
     * Create translation with name (most common case)
     */
    public MealTranslation(String name) {
        this();
        this.name = name;
    }

    /**
     * Create complete translation
     */
    public MealTranslation(String name, String description, String notes) {
        this();
        this.name = name;
        this.description = description;
        this.notes = notes;
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

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
        touch();
    }

    public String getOccasion() {
        return occasion;
    }

    public void setOccasion(String occasion) {
        this.occasion = occasion;
        touch();
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
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
    }

    public boolean isVerified() {
        return verified;
    }

    public void setVerified(boolean verified) {
        this.verified = verified;
    }

    // ========== UTILITY METHODS ==========

    /**
     * Update timestamp on modification
     */
    private void touch() {
        this.lastUpdated = System.currentTimeMillis();
    }

    /**
     * Check if translation has any content
     */
    public boolean hasContent() {
        return hasText(name) || hasText(description) || hasText(notes) ||
                hasText(occasion) || hasText(location);
    }

    /**
     * Check if translation is complete (has core fields)
     */
    public boolean isComplete() {
        return hasText(name) && hasText(description);
    }

    /**
     * Calculate completeness score (0.0 - 1.0)
     */
    public float getCompletenessScore() {
        int totalFields = 5;
        int filledFields = 0;

        if (hasText(name)) filledFields++;
        if (hasText(description)) filledFields++;
        if (hasText(notes)) filledFields++;
        if (hasText(occasion)) filledFields++;
        if (hasText(location)) filledFields++;

        return (float) filledFields / totalFields;
    }

    /**
     * Helper to check if text has content
     */
    private boolean hasText(String text) {
        return text != null && !text.trim().isEmpty();
    }

    /**
     * Create a copy of this translation
     */
    public MealTranslation copy() {
        MealTranslation copy = new MealTranslation();
        copy.name = this.name;
        copy.description = this.description;
        copy.notes = this.notes;
        copy.occasion = this.occasion;
        copy.location = this.location;
        copy.lastUpdated = this.lastUpdated;
        copy.source = this.source;
        copy.verified = this.verified;
        return copy;
    }

    /**
     * Merge another translation into this one (non-null fields only)
     */
    public void mergeFrom(MealTranslation other) {
        if (other == null) return;

        if (other.name != null) this.name = other.name;
        if (other.description != null) this.description = other.description;
        if (other.notes != null) this.notes = other.notes;
        if (other.occasion != null) this.occasion = other.occasion;
        if (other.location != null) this.location = other.location;

        // Update metadata if other is more recent
        if (other.lastUpdated > this.lastUpdated) {
            this.lastUpdated = other.lastUpdated;
            this.source = other.source;
            this.verified = other.verified;
        }
    }

    @Override
    public String toString() {
        return String.format("MealTranslation{name='%s', completeness=%.2f, verified=%s}",
                name, getCompletenessScore(), verified);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MealTranslation that = (MealTranslation) o;
        return Objects.equals(name, that.name) &&
                Objects.equals(description, that.description) &&
                Objects.equals(notes, that.notes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, description, notes);
    }
}