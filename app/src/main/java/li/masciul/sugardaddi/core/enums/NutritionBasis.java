package li.masciul.sugardaddi.core.enums;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The measurement basis a {@link li.masciul.sugardaddi.core.models.Nutrition}
 * object's per-100 values were expressed in BY THE SOURCE.
 *
 * CORE PRINCIPLE: the basis is a property of the nutrition DATA, not of the
 * item's physical nature. 100g and 100ml are never equivalent (density), so
 * the app NEVER converts between bases and NEVER relabels one as the other.
 * A label displays exactly the basis the source declared:
 * - Ciqual, USDA, TheMealDB estimates: always per-100g.
 * - FatSecret recipes: per-100g by construction (normalized via
 *   grams_per_portion).
 * - FatSecret foods: follows the metric_serving_unit of the serving used
 *   for normalization (an exact-100ml serving yields per-100ml values).
 * - Open Food Facts: OFF's "_100g" nutriment fields hold values per 100g
 *   OR per 100ml depending on how the product is sold (OFF's documented
 *   semantics) - resolved from the product's quantity string.
 *
 * Persisted through NutritionEntity.measurementBasis (a column that has
 * existed since schema v1 with default "per_100g" - matching the historical
 * behavior of every row ever written, so no data correction is needed).
 */
public enum NutritionBasis {

    /** Values are per 100 grams of the item. The default. */
    PER_100G("per_100g", "g"),

    /** Values are per 100 milliliters, as declared by the source. */
    PER_100ML("per_100ml", "ml");

    private final String id;
    private final String unitLabel;

    NutritionBasis(String id, String unitLabel) {
        this.id = id;
        this.unitLabel = unitLabel;
    }

    /** Stable identifier, matches NutritionEntity.measurementBasis values. */
    @NonNull
    public String getId() {
        return id;
    }

    /** Short unit suffix for display: "g" or "ml". */
    @NonNull
    public String getUnitLabel() {
        return unitLabel;
    }

    /**
     * Resolve from a persisted identifier. Null, "per_100g", and any
     * unknown/legacy value (the entity comment once anticipated a
     * "per_serving" that was never written) all resolve to {@link #PER_100G}
     * - the safe default matching all historical data.
     */
    @NonNull
    public static NutritionBasis fromId(@Nullable String id) {
        if (PER_100ML.id.equals(id)) {
            return PER_100ML;
        }
        return PER_100G;
    }
}