package li.masciul.sugardaddi.data.sources.aggregation;

/**
 * Tracks which source contributed what data to a merged item
 */
public class SourceContribution {
    private final String sourceId;
    private boolean providedNutrition;
    private boolean providedImages;
    private boolean providedCategories;
    private boolean providedIngredients;
    private long contributionTimestamp;

    public SourceContribution(String sourceId) {
        this.sourceId = sourceId;
        this.contributionTimestamp = System.currentTimeMillis();
    }

    // Getters and setters
    public String getSourceId() { return sourceId; }

    public boolean providedNutrition() { return providedNutrition; }
    public void setProvidedNutrition(boolean provided) { this.providedNutrition = provided; }

    public boolean providedImages() { return providedImages; }
    public void setProvidedImages(boolean provided) { this.providedImages = provided; }

    public boolean providedCategories() { return providedCategories; }
    public void setProvidedCategories(boolean provided) { this.providedCategories = provided; }

    public boolean providedIngredients() { return providedIngredients; }
    public void setProvidedIngredients(boolean provided) { this.providedIngredients = provided; }

    public long getContributionTimestamp() { return contributionTimestamp; }
}