package li.masciul.sugardaddi.core.enums;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Which state of the food a {@link li.masciul.sugardaddi.core.models.Nutrition}
 * object's values describe: the food as sold, or the food once prepared
 * following the package instructions (cocoa powder made with milk, a dried soup
 * with water, reconstituted infant formula).
 *
 * WHY IT EXISTS: EU labels give values for the food as sold, but may give them
 * for the food after preparation instead, provided the package carries detailed
 * preparation instructions (Regulation (EU) No 1169/2011, Article 31(3)). Some
 * products therefore only have prepared values. Showing those as if they were the
 * powder's would be wrong by a large factor (cocoa powder: about 380 kcal per
 * 100 g as sold, about 80 kcal per 100 ml once made with milk).
 *
 * RULES, like {@link NutritionBasis}:
 * - A property of the nutrition DATA, as declared by the source.
 * - One Nutrition object describes ONE state. As-sold and prepared values are
 *   never mixed in the same object, and never merged field by field.
 * - The amount the user logs refers to the same state: 250 ml of a PREPARED
 *   item is 250 ml of the drink, not of powder.
 * - AS_SOLD is the reference and the default. A source offering both states
 *   gives the as-sold one (the EU default for nutrition labelling); PREPARED is
 *   used only when the source has no as-sold values at all.
 *
 * Persisted through NutritionEntity.preparation (null or unknown = AS_SOLD).
 */
public enum NutritionPreparation {

    /** Values for the food as sold. The reference, and the default. */
    AS_SOLD("as_sold"),

    /** Values for the food prepared following the package instructions. */
    PREPARED("prepared");

    private final String id;

    NutritionPreparation(String id) {
        this.id = id;
    }

    /** Stable identifier, matches NutritionEntity.preparation values. */
    @NonNull
    public String getId() {
        return id;
    }

    /** From a persisted identifier: null and unknown values are AS_SOLD. */
    @NonNull
    public static NutritionPreparation fromId(@Nullable String id) {
        if (PREPARED.id.equals(id)) {
            return PREPARED;
        }
        return AS_SOLD;
    }
}
