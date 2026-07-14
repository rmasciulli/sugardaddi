package li.masciul.sugardaddi.core.enums;

import android.content.Context;

import androidx.annotation.Nullable;

import li.masciul.sugardaddi.R;

/**
 * DataConfidence - Describes the provenance and reliability of nutrition data.
 *
 * Attached to every {@link li.masciul.sugardaddi.core.models.Nutrition} object
 * to give users and the app transparency about how trustworthy a nutrition value is.
 *
 * ORDERED FROM HIGHEST TO LOWEST CONFIDENCE.
 *
 * UI USAGE
 * ========
 * Each level maps to a distinct indicator in the detail screen:
 *   SCIENTIFIC  → 🔬 (lab flask) - "Scientific reference data"
 *   DECLARED    → 🏷️ (label tag)  - "Declared by manufacturer"
 *   COMPUTED    → 🧮 (calculator) - "Calculated from ingredients"
 *   ESTIMATED   → ~ (tilde)       - "Estimated value"
 *   USER        → ✏️ (pencil)     - "User-entered value"
 *
 * SOURCE MAPPING
 * ==============
 *   Ciqual (ANSES)              → SCIENTIFIC
 *   USDA Foundation Foods       → SCIENTIFIC
 *   USDA SR Legacy              → SCIENTIFIC
 *   USDA Survey (FNDDS)         → SCIENTIFIC  (measured composite dishes)
 *   OpenFoodFacts               → DECLARED    (manufacturer label)
 *   Computed from ingredients   → COMPUTED    (recipe engine)
 *   TheMealDB                   → COMPUTED    (no direct data; derived from ingredient resolution)
 *   Fuzzy ingredient match      → ESTIMATED   (unresolved match)
 *   Manual user input           → USER
 *
 * CIQUAL QUALITY CODE MAPPING
 * ===========================
 * Ciqual uses its own A/B/C/D confidence system:
 *   A - Representative French data, recent measurement → SCIENTIFIC
 *   B - Data from other EU countries, well-documented  → SCIENTIFIC
 *   C - Data from literature, calculation              → ESTIMATED
 *   D - Data older than 10 years, low confidence       → ESTIMATED
 *
 * This mapping is applied in CiqualMapper per nutrient.
 * The Nutrition object carries the lowest confidence code across all nutrients.
 *
 * STORAGE
 * =======
 * Stored as a String in Room via {@link li.masciul.sugardaddi.data.database.converters.DataConfidenceConverter}.
 * Null-safe: a null value in the database is treated as ESTIMATED on read.
 */
public enum DataConfidence {

    /**
     * Lab-measured by a recognised scientific institution.
     * Sources: Ciqual (ANSES), USDA Foundation Foods, USDA SR Legacy,
     * USDA Survey (FNDDS).
     *
     * Most reliable - values measured under controlled conditions,
     * peer-reviewed methodology.
     */
    SCIENTIFIC,

    /**
     * Declared by the food manufacturer on the product label.
     * Sources: OpenFoodFacts (when sourced from packaging).
     *
     * Generally accurate but subject to rounding rules (EU/FDA regulations
     * allow ±20% tolerance on label values) and potential transcription errors
     * from crowd-sourced entry.
     */
    DECLARED,

    /**
     * Calculated by the app's nutrition engine from ingredient portions.
     * Sources: user-created recipes, composite meals.
     *
     * Accuracy depends on the quality of the underlying ingredient data.
     * Each ingredient's own confidence level propagates upward - a recipe
     * with all SCIENTIFIC ingredients is more reliable than one mixing sources.
     */
    COMPUTED,

    /**
     * Approximated - fuzzy ingredient match, partial data, or low-confidence
     * source measurement.
     * Sources: unresolved ingredient matches, Ciqual C/D quality codes,
     * incomplete product entries.
     *
     * Use with caution for medical or dietary calculations.
     */
    ESTIMATED,

    /**
     * Entered manually by the user with no external source verification.
     * Sources: custom foods, manual recipe overrides.
     *
     * No validation - treat as personal reference only.
     */
    USER;

    // =========================================================================
    // DISPLAY (i18n)
    // =========================================================================

    /**
     * Get localized display name for this confidence level.
     */
    public String getDisplayName(Context context) {
        switch (this) {
            case SCIENTIFIC: return context.getString(R.string.confidence_scientific);
            case DECLARED: return context.getString(R.string.confidence_declared);
            case COMPUTED: return context.getString(R.string.confidence_computed);
            case ESTIMATED: return context.getString(R.string.confidence_estimated);
            case USER: return context.getString(R.string.confidence_user);
            default: return context.getString(R.string.confidence_estimated);
        }
    }

    /**
     * Emoji indicator for this confidence level. Not localized - the symbols
     * (🔬 🏷️ 🧮 ~ ✏️) carry the same meaning in every language, so unlike
     * DataSourceType's emoji this is a plain constant, not a string resource.
     */
    public String getEmoji() {
        switch (this) {
            case SCIENTIFIC: return "\uD83D\uDD2C";     // 🔬
            case DECLARED: return "\uD83C\uDFF7\uFE0F"; // 🏷️
            case COMPUTED: return "\uD83E\uDDEE";       // 🧮
            case ESTIMATED: return "~";
            case USER: return "\u270F\uFE0F";           // ✏️
            default: return "~";
        }
    }

    /**
     * Display name prefixed with the emoji indicator.
     */
    public String getDisplayWithEmoji(Context context) {
        return getEmoji() + " " + getDisplayName(context);
    }

    // =========================================================================
    // UTILITY METHODS
    // =========================================================================

    /**
     * True if this confidence level is suitable for scientific or medical use.
     * Only SCIENTIFIC qualifies - declared values have ±20% tolerance,
     * computed values have cumulative uncertainty.
     */
    public boolean isHighConfidence() {
        return this == SCIENTIFIC;
    }

    /**
     * True if this value should display a caveat in the UI.
     * ESTIMATED and USER values are shown with a warning indicator.
     */
    public boolean requiresCaveat() {
        return this == ESTIMATED || this == USER;
    }

    /**
     * Ordinal-based comparison - returns the lower (less confident) of two levels.
     * Useful when aggregating confidence across nutrients or ingredients:
     * the composite confidence is the lowest individual confidence.
     *
     * Example: SCIENTIFIC.lowest(DECLARED) → DECLARED
     * Example: DECLARED.lowest(ESTIMATED)  → ESTIMATED
     */
    public DataConfidence lowest(DataConfidence other) {
        if (other == null) return this;
        // Ordinal increases as confidence decreases (SCIENTIFIC=0, USER=4)
        return this.ordinal() >= other.ordinal() ? this : other;
    }

    /**
     * Map a Ciqual quality code (A/B/C/D) to the appropriate DataConfidence level.
     *
     * ALL four codes represent scientifically-sourced data - none are industrial
     * or declared values. The distinction is about measurement quality:
     *   A - Analyzed, recent, representative French samples
     *   B - Analyzed, from other reliable EU/international sources
     *   C - Calculated, from literature, or lower representativeness
     *   D - Old data (>10 years), foreign sources, high variability
     *
     * C and D map to ESTIMATED because precision/representativeness is lower,
     * not because the data origin is different.
     *
     * @param ciqualCode Single character "A", "B", "C", or "D". Null-safe.
     * @return DataConfidence level. Returns ESTIMATED for unknown/null codes.
     */
    public static DataConfidence fromCiqualCode(@Nullable String ciqualCode) {
        if (ciqualCode == null || ciqualCode.trim().isEmpty()) return ESTIMATED;
        switch (ciqualCode.trim().toUpperCase()) {
            case "A":
            case "B":
                return SCIENTIFIC;
            case "C":
            case "D":
                return ESTIMATED;
            default:
                return ESTIMATED;
        }
    }

    /**
     * Derive a default DataConfidence from a DataSource.
     * Used when no per-nutrient quality code is available.
     *
     * @param source The data source that provided the nutrition data.
     * @return Most appropriate DataConfidence for that source.
     */
    public static DataConfidence fromDataSource(@Nullable DataSourceType source) {
        if (source == null) return ESTIMATED;
        switch (source) {
            case CIQUAL:
            case USDA:
                return SCIENTIFIC;
            case OPENFOODFACTS:
                return DECLARED;
            case USER:
            case CUSTOM:
                return USER;
            case THEMEALDB:
                // TheMealDB provides no nutrition data directly.
                // Any nutrition attached to a MealDB recipe is calculated by the
                // recipe engine from its resolved ingredients - that is COMPUTED,
                // not declared or measured. ESTIMATED would apply only at the
                // individual ingredient level if resolution is fuzzy, which the
                // engine handles via lowest() propagation - not here.
                return COMPUTED;
            default:
                return ESTIMATED;
        }
    }

    /**
     * Safe deserialisation from a stored String value.
     * Returns ESTIMATED (not null) for unrecognised or null values,
     * so downstream code never needs to null-check.
     *
     * @param value Stored enum name, e.g. "SCIENTIFIC"
     * @return Corresponding DataConfidence, or ESTIMATED if unrecognised.
     */
    public static DataConfidence fromString(@Nullable String value) {
        if (value == null || value.trim().isEmpty()) return ESTIMATED;
        try {
            return DataConfidence.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return ESTIMATED;
        }
    }
}