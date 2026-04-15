package li.masciul.sugardaddi.core.models;

import java.util.Objects;

/**
 * RecipeStepTranslation - Translatable text for a recipe step
 *
 * ARCHITECTURE:
 * - Stores ONLY text that varies by language
 * - References RecipeStepMetadata via stepNumber
 * - Multiple translations can reference same metadata
 *
 * PAIRING WITH METADATA:
 * - RecipeStepMetadata.stepNumber = RecipeStepTranslation.stepNumber
 * - Combined by Recipe.getStep(stepNumber, language) helper
 * - Display using RecipeStep (combines both)
 *
 * STORAGE PATTERN:
 * Recipe:
 *   stepStructure: [Metadata(1), Metadata(2), Metadata(3)]
 *   stepTranslations (en): [Translation(1, "Preheat..."), Translation(2, "Mix...")]
 *
 * RecipeTranslation (fr):
 *   stepTranslations: [Translation(1, "Préchauffer..."), Translation(2, "Mélanger...")]
 *
 * DESIGN PHILOSOPHY:
 * "Text varies by language, facts remain constant"
 */
public class RecipeStepTranslation {

    // ========== REFERENCE ==========
    private int stepNumber;             // Links to RecipeStepMetadata.stepNumber

    // ========== TRANSLATABLE TEXT ==========
    private String instruction;         // Step instruction in this language
    private String tip;                 // Optional cooking tip in this language

    // ========== METADATA ==========
    private long lastUpdated;           // When this translation was last modified
    private String translatedBy;        // "auto", "user", "community"
    private boolean verified;           // Is this translation verified?

    // ========== CONSTRUCTORS ==========

    public RecipeStepTranslation() {
        this.lastUpdated = System.currentTimeMillis();
    }

    /**
     * Create translation with step number and instruction
     */
    public RecipeStepTranslation(int stepNumber, String instruction) {
        this();
        this.stepNumber = stepNumber;
        this.instruction = instruction;
    }

    /**
     * Create complete translation
     */
    public RecipeStepTranslation(int stepNumber, String instruction, String tip) {
        this();
        this.stepNumber = stepNumber;
        this.instruction = instruction;
        this.tip = tip;
    }

    // ========== GETTERS AND SETTERS ==========

    public int getStepNumber() {
        return stepNumber;
    }

    public void setStepNumber(int stepNumber) {
        this.stepNumber = stepNumber;
        touch();
    }

    public String getInstruction() {
        return instruction;
    }

    public void setInstruction(String instruction) {
        this.instruction = instruction;
        touch();
    }

    public String getTip() {
        return tip;
    }

    public void setTip(String tip) {
        this.tip = tip;
        touch();
    }

    public long getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(long lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public String getTranslatedBy() {
        return translatedBy;
    }

    public void setTranslatedBy(String translatedBy) {
        this.translatedBy = translatedBy;
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

    /**
     * Update lastUpdated timestamp
     */
    private void touch() {
        this.lastUpdated = System.currentTimeMillis();
    }

    /**
     * Check if this translation has a tip
     */
    public boolean hasTip() {
        return tip != null && !tip.trim().isEmpty();
    }

    /**
     * Check if this translation is complete
     */
    public boolean isComplete() {
        return instruction != null && !instruction.trim().isEmpty();
    }

    /**
     * Get instruction length (useful for UI layout)
     */
    public int getInstructionLength() {
        return instruction != null ? instruction.length() : 0;
    }

    // ========== VALIDATION ==========

    /**
     * Validate this translation
     */
    public boolean isValid() {
        // Must have step number
        if (stepNumber <= 0) {
            return false;
        }

        // Must have instruction text
        if (instruction == null || instruction.trim().isEmpty()) {
            return false;
        }

        return true;
    }

    // ========== OBJECT METHODS ==========

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RecipeStepTranslation that = (RecipeStepTranslation) o;
        return stepNumber == that.stepNumber &&
                Objects.equals(instruction, that.instruction) &&
                Objects.equals(tip, that.tip);
    }

    @Override
    public int hashCode() {
        return Objects.hash(stepNumber, instruction, tip);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Step ").append(stepNumber).append(": ");

        if (instruction != null) {
            String preview = instruction.length() > 50
                    ? instruction.substring(0, 47) + "..."
                    : instruction;
            sb.append(preview);
        }

        if (hasTip()) {
            sb.append(" [HAS TIP]");
        }

        if (verified) {
            sb.append(" ✓");
        }

        return sb.toString();
    }

    // ========== BUILDER PATTERN ==========

    public static class Builder {
        private final RecipeStepTranslation translation;

        public Builder(int stepNumber) {
            translation = new RecipeStepTranslation(stepNumber, "");
        }

        public Builder instruction(String instruction) {
            translation.setInstruction(instruction);
            return this;
        }

        public Builder tip(String tip) {
            translation.setTip(tip);
            return this;
        }

        public Builder translatedBy(String translatedBy) {
            translation.setTranslatedBy(translatedBy);
            return this;
        }

        public Builder verified(boolean verified) {
            translation.setVerified(verified);
            return this;
        }

        public RecipeStepTranslation build() {
            if (!translation.isValid()) {
                throw new IllegalStateException("Invalid step translation: " + translation);
            }
            return translation;
        }
    }
}