package li.masciul.sugardaddi.core.enums;

/**
 * Unit - Measurement units for serving sizes and ingredients
 *
 * Provides conversion capabilities and standardized display
 * for all measurement units used in the app
 */
public enum Unit {

    // ========== WEIGHT UNITS ==========
    G("g", "gram", "grams", UnitType.WEIGHT, 1.0, 1.0),
    KG("kg", "kilogram", "kilograms", UnitType.WEIGHT, 1000.0, 1000.0),
    MG("mg", "milligram", "milligrams", UnitType.WEIGHT, 0.001, 0.001),
    CG("cg", "centigram", "centigrams", UnitType.WEIGHT, 0.01, 0.01),
    OZ("oz", "ounce", "ounces", UnitType.WEIGHT, 28.35, 28.35),
    LB("lb", "pound", "pounds", UnitType.WEIGHT, 453.59, 453.59),

    // ========== VOLUME UNITS ==========
    ML("ml", "milliliter", "milliliters", UnitType.VOLUME, 1.0, 1.0),
    L("l", "liter", "liters", UnitType.VOLUME, 1000.0, 1000.0),
    CL("cl", "centiliter", "centiliters", UnitType.VOLUME, 10.0, 10.0),
    DL("dl", "deciliter", "deciliters", UnitType.VOLUME, 100.0, 100.0),

    // ========== US VOLUME UNITS ==========
    FL_OZ("fl oz", "fluid ounce", "fluid ounces", UnitType.VOLUME, 29.57, 29.57),
    CUP("cup", "cup", "cups", UnitType.VOLUME, 240.0, 240.0),
    PINT("pint", "pint", "pints", UnitType.VOLUME, 473.18, 473.18),
    QUART("quart", "quart", "quarts", UnitType.VOLUME, 946.35, 946.35),
    GALLON("gallon", "gallon", "gallons", UnitType.VOLUME, 3785.41, 3785.41),

    // ========== COOKING UNITS ==========
    TSP("tsp", "teaspoon", "teaspoons", UnitType.VOLUME, 5.0, 5.0),
    TBSP("tbsp", "tablespoon", "tablespoons", UnitType.VOLUME, 15.0, 15.0),

    // ========== PIECE/COUNT UNITS ==========
    PIECE("piece", "piece", "pieces", UnitType.COUNT, null, null),
    SLICE("slice", "slice", "slices", UnitType.COUNT, 30.0, 30.0), // Average bread slice
    SERVING("serving", "serving", "servings", UnitType.COUNT, null, null),
    PORTION("portion", "portion", "portions", UnitType.COUNT, null, null),

    // ========== FOOD-SPECIFIC UNITS ==========
    CLOVE("clove", "clove", "cloves", UnitType.COUNT, 3.0, 3.0),     // Garlic clove
    BULB("bulb", "bulb", "bulbs", UnitType.COUNT, 40.0, 40.0),       // Garlic bulb
    HEAD("head", "head", "heads", UnitType.COUNT, 500.0, 500.0),     // Lettuce head
    BUNCH("bunch", "bunch", "bunches", UnitType.COUNT, 100.0, 100.0), // Herb bunch
    STALK("stalk", "stalk", "stalks", UnitType.COUNT, 15.0, 15.0),   // Celery stalk
    LEAF("leaf", "leaf", "leaves", UnitType.COUNT, 2.0, 2.0),        // Individual leaf

    // ========== SIZE-BASED UNITS ==========
    SMALL("small", "small", "small", UnitType.SIZE, 80.0, 80.0),     // Small fruit
    MEDIUM("medium", "medium", "medium", UnitType.SIZE, 150.0, 150.0), // Medium fruit
    LARGE("large", "large", "large", UnitType.SIZE, 250.0, 250.0),   // Large fruit

    // ========== SPECIAL UNITS ==========
    PINCH("pinch", "pinch", "pinches", UnitType.VOLUME, 0.3, 0.3),   // Tiny amount
    DASH("dash", "dash", "dashes", UnitType.VOLUME, 0.6, 0.6),       // Small amount
    DROP("drop", "drop", "drops", UnitType.VOLUME, 0.05, 0.05),      // Liquid drop

    // ========== CUSTOM/OTHER ==========
    OTHER("other", "other", "other", UnitType.OTHER, null, null);

    private final String abbreviation;
    private final String singular;
    private final String plural;
    private final UnitType type;
    private final Double gramsEquivalent;    // How many grams this unit equals (for 1 unit)
    private final Double mlEquivalent;       // How many ml this unit equals (for 1 unit)

    Unit(String abbreviation, String singular, String plural, UnitType type,
         Double gramsEquivalent, Double mlEquivalent) {
        this.abbreviation = abbreviation;
        this.singular = singular;
        this.plural = plural;
        this.type = type;
        this.gramsEquivalent = gramsEquivalent;
        this.mlEquivalent = mlEquivalent;
    }

    // ========== GETTERS ==========

    public String getAbbreviation() { return abbreviation; }
    public String getSingular() { return singular; }
    public String getPlural() { return plural; }
    public UnitType getType() { return type; }
    public Double getGramsEquivalent() { return gramsEquivalent; }
    public Double getMlEquivalent() { return mlEquivalent; }

    // ========== DISPLAY METHODS ==========

    public String getDisplayName() {
        return singular;
    }

    public String getDisplayName(double quantity) {
        return quantity == 1.0 ? singular : plural;
    }

    // ========== CONVERSION METHODS ==========

    /**
     * Convert quantity of this unit to grams
     */
    public Double convertToGrams(double quantity) {
        if (gramsEquivalent == null) {
            return null; // Cannot convert
        }
        return quantity * gramsEquivalent;
    }

    /**
     * Convert quantity of this unit to milliliters
     */
    public Double convertToMilliliters(double quantity) {
        if (mlEquivalent == null) {
            return null; // Cannot convert
        }
        return quantity * mlEquivalent;
    }

    /**
     * Convert grams to this unit
     */
    public Double convertFromGrams(double grams) {
        if (gramsEquivalent == null || gramsEquivalent == 0) {
            return null; // Cannot convert
        }
        return grams / gramsEquivalent;
    }

    /**
     * Convert milliliters to this unit
     */
    public Double convertFromMilliliters(double ml) {
        if (mlEquivalent == null || mlEquivalent == 0) {
            return null; // Cannot convert
        }
        return ml / mlEquivalent;
    }

    // ========== UTILITY METHODS ==========

    /**
     * Parse unit from string
     */
    public static Unit fromString(String unitStr) {
        if (unitStr == null || unitStr.trim().isEmpty()) {
            return null;
        }

        String normalized = unitStr.toLowerCase().trim();

        // Try exact matches first
        for (Unit unit : values()) {
            if (unit.abbreviation.equals(normalized) ||
                    unit.singular.equals(normalized) ||
                    unit.plural.equals(normalized)) {
                return unit;
            }
        }

        // Try common variations
        switch (normalized) {
            case "gram": case "grams": case "gr": return G;
            case "kilogram": case "kilograms": case "kilo": case "kilos": return KG;
            case "milligram": case "milligrams": case "mgs": return MG;
            case "centigram": case "centigrams": case "cgs": return CG;
            case "milliliter": case "milliliters": case "millilitre": case "millilitres": return ML;
            case "liter": case "liters": case "litre": case "litres": return L;
            case "ounce": case "ounces": return OZ;
            case "pound": case "pounds": case "lbs": return LB;
            case "fluid ounce": case "fluid ounces": case "floz": return FL_OZ;
            case "teaspoon": case "teaspoons": case "tsp.": return TSP;
            case "tablespoon": case "tablespoons": case "tbsp.": case "tbs": return TBSP;
            case "cups": return CUP;
            case "pieces": return PIECE;
            case "slices": return SLICE;
            case "servings": return SERVING;
            case "portions": return PORTION;
            default: return OTHER;
        }
    }

    /**
     * Check if this unit can be converted to weight
     */
    public boolean canConvertToWeight() {
        return gramsEquivalent != null;
    }

    /**
     * Check if this unit can be converted to volume
     */
    public boolean canConvertToVolume() {
        return mlEquivalent != null;
    }

    /**
     * Check if this unit is metric
     */
    public boolean isMetric() {
        return this == G || this == KG || this == MG || this == CG ||
                this == ML || this == L || this == CL || this == DL;
    }

    /**
     * Check if this unit is imperial/US
     */
    public boolean isImperial() {
        return this == OZ || this == LB || this == FL_OZ || this == CUP ||
                this == PINT || this == QUART || this == GALLON;
    }

    /**
     * Get appropriate unit for liquid vs solid
     */
    public static Unit getDefaultUnit(boolean isLiquid) {
        return isLiquid ? ML : G;
    }

    /**
     * Get all units of a specific type
     */
    public static Unit[] getUnitsOfType(UnitType type) {
        return java.util.Arrays.stream(values())
                .filter(unit -> unit.type == type)
                .toArray(Unit[]::new);
    }

    @Override
    public String toString() {
        return singular;
    }

    // ========== UNIT TYPE ENUM ==========

    public enum UnitType {
        WEIGHT("Weight"),
        VOLUME("Volume"),
        COUNT("Count"),
        SIZE("Size"),
        OTHER("Other");

        private final String displayName;

        UnitType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}