package li.masciul.sugardaddi.core.models;

import li.masciul.sugardaddi.core.enums.Unit;

/**
 * ServingSize - Universal serving size handling
 *
 * Replaces the serving size logic scattered in FoodItem
 * with a clean, reusable model
 */
public class ServingSize {

    private String description;        // "1 cup", "1 slice", "1 medium apple"
    private Double quantity;           // Numeric amount (240.0)
    private Unit unit;                // G, ML, CUP, TBSP, etc.
    private String unitText;          // Custom unit text if Unit.OTHER

    // Conversion support
    private Double gramEquivalent;     // How many grams this serving equals
    private Double mlEquivalent;       // How many ml this serving equals (for liquids)

    // Metadata
    private boolean isApproximate = false;  // Is this an estimated serving?
    private String source;             // Where this serving size came from

    // ========== CONSTRUCTORS ==========

    public ServingSize() {}

    public ServingSize(String description) {
        this.description = description;
        parseDescription();
    }

    public ServingSize(double quantity, Unit unit) {
        this.quantity = quantity;
        this.unit = unit;
        this.description = formatDescription();
    }

    public ServingSize(double quantity, String unitText) {
        this.quantity = quantity;
        this.unit = Unit.OTHER;
        this.unitText = unitText;
        this.description = quantity + " " + unitText;
    }

    // ========== BASIC GETTERS/SETTERS ==========

    public String getDescription() { return description; }
    public void setDescription(String description) {
        this.description = description;
        if (quantity == null || unit == null) {
            parseDescription(); // Try to extract structured data
        }
    }

    public Double getQuantity() { return quantity; }
    public void setQuantity(Double quantity) { this.quantity = quantity; }

    public Unit getUnit() { return unit; }
    public void setUnit(Unit unit) { this.unit = unit; }

    public String getUnitText() { return unitText; }
    public void setUnitText(String unitText) { this.unitText = unitText; }

    public Double getGramEquivalent() { return gramEquivalent; }
    public void setGramEquivalent(Double gramEquivalent) { this.gramEquivalent = gramEquivalent; }

    public Double getMlEquivalent() { return mlEquivalent; }
    public void setMlEquivalent(Double mlEquivalent) { this.mlEquivalent = mlEquivalent; }

    public boolean isApproximate() { return isApproximate; }
    public void setApproximate(boolean approximate) { this.isApproximate = approximate; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    // ========== UTILITY METHODS ==========

    /**
     * Check if this serving size has meaningful data
     */
    public boolean isValid() {
        return (description != null && !description.trim().isEmpty()) ||
                (quantity != null && quantity > 0 && unit != null);
    }

    /**
     * Get the serving size as grams (for nutrition calculations)
     */
    public Double getAsGrams() {
        if (gramEquivalent != null) {
            return gramEquivalent;
        }

        if (quantity == null || unit == null) {
            return null;
        }

        return unit.convertToGrams(quantity);
    }

    /**
     * Get the serving size as ml (for liquid nutrition calculations)
     */
    public Double getAsMilliliters() {
        if (mlEquivalent != null) {
            return mlEquivalent;
        }

        if (quantity == null || unit == null) {
            return null;
        }

        return unit.convertToMilliliters(quantity);
    }

    /**
     * Get display text for UI
     */
    public String getDisplayText() {
        if (description != null && !description.trim().isEmpty()) {
            return description;
        }

        if (quantity != null && unit != null) {
            return formatDescription();
        }

        return "Unknown serving";
    }

    /**
     * Get short display text (for compact UI)
     */
    public String getShortDisplayText() {
        if (quantity != null && unit != null) {
            if (quantity == Math.floor(quantity)) {
                return String.format("%.0f%s", quantity, unit.getAbbreviation());
            } else {
                return String.format("%.1f%s", quantity, unit.getAbbreviation());
            }
        }

        // Try to shorten description
        if (description != null) {
            String shortened = description.replaceFirst("^1\\s+", ""); // Remove leading "1 "
            if (shortened.length() > 10) {
                return shortened.substring(0, 7) + "...";
            }
            return shortened;
        }

        return "1 serving";
    }

    // ========== PARSING AND FORMATTING ==========

    /**
     * Parse description to extract structured data
     */
    private void parseDescription() {
        if (description == null || description.trim().isEmpty()) {
            return;
        }

        String desc = description.toLowerCase().trim();

        // Common patterns: "240ml", "1 cup", "2 tbsp", etc.
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "(\\d+(?:\\.\\d+)?)\\s*(\\w+)"
        );
        java.util.regex.Matcher matcher = pattern.matcher(desc);

        if (matcher.find()) {
            try {
                double parsedQuantity = Double.parseDouble(matcher.group(1));
                String unitStr = matcher.group(2);
                Unit parsedUnit = Unit.fromString(unitStr);

                if (parsedUnit != null) {
                    this.quantity = parsedQuantity;
                    this.unit = parsedUnit;
                } else {
                    this.quantity = parsedQuantity;
                    this.unit = Unit.OTHER;
                    this.unitText = unitStr;
                }
            } catch (NumberFormatException e) {
                // Keep original description
            }
        }
    }

    /**
     * Format structured data into description
     */
    private String formatDescription() {
        if (quantity == null || unit == null) {
            return null;
        }

        String unitDisplay = unit == Unit.OTHER ? unitText : unit.getDisplayName();

        if (quantity == 1.0) {
            return "1 " + unitDisplay;
        } else if (quantity == Math.floor(quantity)) {
            return String.format("%.0f %s", quantity, unitDisplay);
        } else {
            return String.format("%.1f %s", quantity, unitDisplay);
        }
    }

    // ========== CONVERSION METHODS ==========

    /**
     * Convert this serving to a different unit
     */
    public ServingSize convertTo(Unit targetUnit) {
        if (quantity == null || unit == null || targetUnit == null) {
            return this; // Can't convert
        }

        Double grams = unit.convertToGrams(quantity);
        if (grams == null) {
            return this; // Can't convert
        }

        Double targetQuantity = targetUnit.convertFromGrams(grams);
        if (targetQuantity == null) {
            return this; // Can't convert
        }

        ServingSize converted = new ServingSize();
        converted.quantity = targetQuantity;
        converted.unit = targetUnit;
        converted.description = converted.formatDescription();
        converted.gramEquivalent = this.gramEquivalent;
        converted.mlEquivalent = this.mlEquivalent;
        converted.isApproximate = true; // Conversions are approximate
        converted.source = this.source;

        return converted;
    }

    /**
     * Scale this serving by a multiplier
     */
    public ServingSize scale(double multiplier) {
        if (multiplier <= 0) {
            return this;
        }

        ServingSize scaled = new ServingSize();
        scaled.unit = this.unit;
        scaled.unitText = this.unitText;
        scaled.isApproximate = this.isApproximate || multiplier != 1.0;
        scaled.source = this.source;

        if (quantity != null) {
            scaled.quantity = quantity * multiplier;
        }

        if (gramEquivalent != null) {
            scaled.gramEquivalent = gramEquivalent * multiplier;
        }

        if (mlEquivalent != null) {
            scaled.mlEquivalent = mlEquivalent * multiplier;
        }

        scaled.description = scaled.formatDescription();
        return scaled;
    }

    // ========== STATIC FACTORY METHODS ==========

    /**
     * Create serving size from grams
     */
    public static ServingSize fromGrams(double grams) {
        ServingSize serving = new ServingSize(grams, Unit.G);
        serving.gramEquivalent = grams;
        return serving;
    }

    /**
     * Create serving size from milliliters
     */
    public static ServingSize fromMilliliters(double ml) {
        ServingSize serving = new ServingSize(ml, Unit.ML);
        serving.mlEquivalent = ml;
        return serving;
    }

    /**
     * Create serving size for recipes (servings)
     */
    public static ServingSize forRecipe(int servings) {
        return new ServingSize(servings, Unit.SERVING);
    }

    @Override
    public String toString() {
        return getDisplayText();
    }
}