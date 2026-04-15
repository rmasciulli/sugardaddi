package li.masciul.sugardaddi.core.models;

import java.util.Objects;

/**
 * RecipeStep - Display/Helper class combining metadata and translation
 *
 * ARCHITECTURE:
 * - This is a DISPLAY CLASS, not storage
 * - Combines RecipeStepMetadata + RecipeStepTranslation for UI
 * - Created on-the-fly by Recipe.getStep(stepNumber, language)
 * - NOT stored in database
 *
 * STORAGE PATTERN:
 * - Database stores: RecipeStepMetadata (once) + RecipeStepTranslation (per language)
 * - Display uses: RecipeStep (combined view)
 *
 * USAGE:
 * RecipeStep step = recipe.getStep(1, "en");
 * displayStep(step.getInstruction(), step.getDuration(), step.getImageUrl());
 */
public class RecipeStep {

    // ========== FROM METADATA ==========
    private int stepNumber;
    private Integer durationMinutes;
    private String imageUrl;
    private String videoUrl;
    private boolean isOptional;
    private boolean isCritical;
    private String equipment;
    private Integer temperatureCelsius;

    // ========== FROM TRANSLATION ==========
    private String instruction;
    private String tip;
    private boolean translationVerified;

    // ========== CONSTRUCTORS ==========

    public RecipeStep() {
    }

    /**
     * Create from separate metadata and translation
     */
    public RecipeStep(RecipeStepMetadata metadata, RecipeStepTranslation translation) {
        if (metadata != null) {
            this.stepNumber = metadata.getStepNumber();
            this.durationMinutes = metadata.getDurationMinutes();
            this.imageUrl = metadata.getImageUrl();
            this.videoUrl = metadata.getVideoUrl();
            this.isOptional = metadata.isOptional();
            this.isCritical = metadata.isCritical();
            this.equipment = metadata.getEquipment();
            this.temperatureCelsius = metadata.getTemperatureCelsius();
        }

        if (translation != null) {
            // Verify step numbers match
            if (metadata != null && translation.getStepNumber() != metadata.getStepNumber()) {
                throw new IllegalArgumentException(
                        "Step number mismatch: metadata=" + metadata.getStepNumber() +
                                ", translation=" + translation.getStepNumber()
                );
            }

            this.instruction = translation.getInstruction();
            this.tip = translation.getTip();
            this.translationVerified = translation.isVerified();
        }
    }

    // ========== GETTERS AND SETTERS ==========

    public int getStepNumber() {
        return stepNumber;
    }

    public void setStepNumber(int stepNumber) {
        this.stepNumber = stepNumber;
    }

    public String getInstruction() {
        return instruction;
    }

    public void setInstruction(String instruction) {
        this.instruction = instruction;
    }

    public Integer getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(Integer durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public String getTip() {
        return tip;
    }

    public void setTip(String tip) {
        this.tip = tip;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getVideoUrl() {
        return videoUrl;
    }

    public void setVideoUrl(String videoUrl) {
        this.videoUrl = videoUrl;
    }

    public boolean isOptional() {
        return isOptional;
    }

    public void setOptional(boolean optional) {
        isOptional = optional;
    }

    public boolean isCritical() {
        return isCritical;
    }

    public void setCritical(boolean critical) {
        isCritical = critical;
    }

    public String getEquipment() {
        return equipment;
    }

    public void setEquipment(String equipment) {
        this.equipment = equipment;
    }

    public Integer getTemperatureCelsius() {
        return temperatureCelsius;
    }

    public void setTemperatureCelsius(Integer temperatureCelsius) {
        this.temperatureCelsius = temperatureCelsius;
    }

    public boolean isTranslationVerified() {
        return translationVerified;
    }

    public void setTranslationVerified(boolean translationVerified) {
        this.translationVerified = translationVerified;
    }

    // ========== HELPER METHODS ==========

    public boolean hasMedia() {
        return (imageUrl != null && !imageUrl.isEmpty()) ||
                (videoUrl != null && !videoUrl.isEmpty());
    }

    public boolean hasTiming() {
        return durationMinutes != null && durationMinutes > 0;
    }

    public boolean hasTip() {
        return tip != null && !tip.trim().isEmpty();
    }

    public Integer getTemperatureFahrenheit() {
        if (temperatureCelsius == null) {
            return null;
        }
        return (int) Math.round((temperatureCelsius * 9.0 / 5.0) + 32);
    }

    public String formatForDisplay() {
        StringBuilder sb = new StringBuilder();
        sb.append(stepNumber).append(". ");

        if (instruction != null) {
            sb.append(instruction);
        }

        if (durationMinutes != null) {
            sb.append(" (").append(durationMinutes).append(" min)");
        }

        if (isOptional) {
            sb.append(" [Optional]");
        }

        return sb.toString();
    }

    // ========== OBJECT METHODS ==========

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RecipeStep that = (RecipeStep) o;
        return stepNumber == that.stepNumber &&
                Objects.equals(instruction, that.instruction) &&
                Objects.equals(durationMinutes, that.durationMinutes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(stepNumber, instruction, durationMinutes);
    }

    @Override
    public String toString() {
        return formatForDisplay();
    }
}