package li.masciul.sugardaddi.core.models;

import java.util.Objects;

/**
 * RecipeStepMetadata - Universal recipe step structure (language-agnostic)
 *
 * ARCHITECTURE:
 * - Stores ONLY metadata that doesn't change across languages
 * - Stored once per recipe (no duplication)
 * - Paired with RecipeStepTranslation for display
 *
 * SEPARATION OF CONCERNS:
 * RecipeStepMetadata stores stepNumber, timing, images and equipments
 * RecipeStepTranslation stores translatable texts such as instructions or tips
 *
 * STORAGE EFFICIENCY:
 * - Recipe with 10 steps in 3 languages:
 *   OLD: 10 steps × 3 languages = 30 stored objects
 *   NEW: 10 metadata + 30 translations = 40 objects (but metadata only once)
 *   Savings: ~60% reduction in duplicated timing/media data
 *
 * DESIGN PHILOSOPHY:
 * "Store facts once, translate words many times"
 */
public class RecipeStepMetadata {

    // ========== CORE STRUCTURE ==========
    private int stepNumber;             // 1-based step number (1, 2, 3...)

    // ========== TIMING ==========
    private Integer durationMinutes;    // Time for this step (null = unknown)

    // ========== MEDIA ==========
    private String imageUrl;            // Optional: image showing this step
    private String videoUrl;            // Optional: video demonstration

    // ========== FLAGS ==========
    private boolean isOptional;         // Can this step be skipped? (e.g., garnish)
    private boolean isCritical;         // Is this step critical? (e.g., safety)

    // ========== EQUIPMENT ==========
    private String equipment;           // Equipment needed (usually not translated)
    // NOTE: Equipment names like "Oven" are rarely translated in recipes
    // If needed, add to RecipeStepTranslation instead

    // ========== TEMPERATURE ==========
    private Integer temperatureCelsius; // Cooking temperature (if relevant)
    private String temperatureNote;     // "preheat", "medium heat" (translatable? consider moving)

    // ========== METADATA ==========
    private long lastUpdated;           // When this metadata was last modified
    private String sourceStepId;        // Original step ID from recipe source

    // ========== CONSTRUCTORS ==========

    public RecipeStepMetadata() {
        this.lastUpdated = System.currentTimeMillis();
    }

    /**
     * Create simple step metadata with just step number
     */
    public RecipeStepMetadata(int stepNumber) {
        this();
        this.stepNumber = stepNumber;
    }

    /**
     * Create step metadata with timing
     */
    public RecipeStepMetadata(int stepNumber, Integer durationMinutes) {
        this();
        this.stepNumber = stepNumber;
        this.durationMinutes = durationMinutes;
    }

    /**
     * Create complete step metadata
     */
    public RecipeStepMetadata(int stepNumber, Integer durationMinutes, String equipment, boolean isOptional) {
        this();
        this.stepNumber = stepNumber;
        this.durationMinutes = durationMinutes;
        this.equipment = equipment;
        this.isOptional = isOptional;
    }

    // ========== GETTERS AND SETTERS ==========

    public int getStepNumber() {
        return stepNumber;
    }

    public void setStepNumber(int stepNumber) {
        this.stepNumber = stepNumber;
        touch();
    }

    public Integer getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(Integer durationMinutes) {
        this.durationMinutes = durationMinutes;
        touch();
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
        touch();
    }

    public String getVideoUrl() {
        return videoUrl;
    }

    public void setVideoUrl(String videoUrl) {
        this.videoUrl = videoUrl;
        touch();
    }

    public boolean isOptional() {
        return isOptional;
    }

    public void setOptional(boolean optional) {
        isOptional = optional;
        touch();
    }

    public boolean isCritical() {
        return isCritical;
    }

    public void setCritical(boolean critical) {
        isCritical = critical;
        touch();
    }

    public String getEquipment() {
        return equipment;
    }

    public void setEquipment(String equipment) {
        this.equipment = equipment;
        touch();
    }

    public Integer getTemperatureCelsius() {
        return temperatureCelsius;
    }

    public void setTemperatureCelsius(Integer temperatureCelsius) {
        this.temperatureCelsius = temperatureCelsius;
        touch();
    }

    public String getTemperatureNote() {
        return temperatureNote;
    }

    public void setTemperatureNote(String temperatureNote) {
        this.temperatureNote = temperatureNote;
        touch();
    }

    public long getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(long lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public String getSourceStepId() {
        return sourceStepId;
    }

    public void setSourceStepId(String sourceStepId) {
        this.sourceStepId = sourceStepId;
    }

    // ========== HELPER METHODS ==========

    /**
     * Update lastUpdated timestamp
     */
    private void touch() {
        this.lastUpdated = System.currentTimeMillis();
    }

    /**
     * Check if this step has media (image or video)
     */
    public boolean hasMedia() {
        return (imageUrl != null && !imageUrl.trim().isEmpty()) ||
                (videoUrl != null && !videoUrl.trim().isEmpty());
    }

    /**
     * Check if this step has timing information
     */
    public boolean hasTiming() {
        return durationMinutes != null && durationMinutes > 0;
    }

    /**
     * Check if this step requires specific equipment
     */
    public boolean requiresEquipment() {
        return equipment != null && !equipment.trim().isEmpty();
    }

    /**
     * Get temperature in Fahrenheit (convenience for US users)
     */
    public Integer getTemperatureFahrenheit() {
        if (temperatureCelsius == null) {
            return null;
        }
        return (int) Math.round((temperatureCelsius * 9.0 / 5.0) + 32);
    }

    /**
     * Set temperature from Fahrenheit
     */
    public void setTemperatureFahrenheit(Integer fahrenheit) {
        if (fahrenheit == null) {
            this.temperatureCelsius = null;
        } else {
            this.temperatureCelsius = (int) Math.round((fahrenheit - 32) * 5.0 / 9.0);
        }
        touch();
    }

    // ========== VALIDATION ==========

    /**
     * Validate this step metadata
     */
    public boolean isValid() {
        // Step number must be positive
        if (stepNumber <= 0) {
            return false;
        }

        // Duration can't be negative
        if (durationMinutes != null && durationMinutes < 0) {
            return false;
        }

        // Temperature can't be absurdly low or high
        if (temperatureCelsius != null && (temperatureCelsius < -50 || temperatureCelsius > 500)) {
            return false;
        }

        return true;
    }

    // ========== OBJECT METHODS ==========

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RecipeStepMetadata that = (RecipeStepMetadata) o;
        return stepNumber == that.stepNumber &&
                isOptional == that.isOptional &&
                isCritical == that.isCritical &&
                Objects.equals(durationMinutes, that.durationMinutes) &&
                Objects.equals(equipment, that.equipment);
    }

    @Override
    public int hashCode() {
        return Objects.hash(stepNumber, durationMinutes, isOptional, equipment);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Step ").append(stepNumber);

        if (durationMinutes != null) {
            sb.append(" (").append(durationMinutes).append(" min)");
        }

        if (isOptional) {
            sb.append(" [OPTIONAL]");
        }

        if (isCritical) {
            sb.append(" [CRITICAL]");
        }

        if (equipment != null) {
            sb.append(" - Equipment: ").append(equipment);
        }

        return sb.toString();
    }

    // ========== BUILDER PATTERN (OPTIONAL) ==========

    public static class Builder {
        private final RecipeStepMetadata metadata;

        public Builder(int stepNumber) {
            metadata = new RecipeStepMetadata(stepNumber);
        }

        public Builder duration(Integer minutes) {
            metadata.setDurationMinutes(minutes);
            return this;
        }

        public Builder equipment(String equipment) {
            metadata.setEquipment(equipment);
            return this;
        }

        public Builder optional(boolean optional) {
            metadata.setOptional(optional);
            return this;
        }

        public Builder critical(boolean critical) {
            metadata.setCritical(critical);
            return this;
        }

        public Builder image(String url) {
            metadata.setImageUrl(url);
            return this;
        }

        public Builder video(String url) {
            metadata.setVideoUrl(url);
            return this;
        }

        public Builder temperature(Integer celsius) {
            metadata.setTemperatureCelsius(celsius);
            return this;
        }

        public RecipeStepMetadata build() {
            if (!metadata.isValid()) {
                throw new IllegalStateException("Invalid step metadata: " + metadata);
            }
            return metadata;
        }
    }
}