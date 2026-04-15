package li.masciul.sugardaddi.core.interfaces;

import android.content.Context;
import li.masciul.sugardaddi.core.utils.AllergenUtils;

/**
 * Interface for entities that track allergen information
 * Implements EU Regulation 1169/2011 - 14 mandatory allergens
 *
 * Any class implementing this interface only needs to provide
 * getAllergenFlags() and setAllergenFlags() - all other methods
 * are provided as defaults.
 */
public interface AllergenAware {

    /**
     * Get the allergen bit flags
     * @return Integer with bits set for present allergens
     */
    int getAllergenFlags();

    /**
     * Set the allergen bit flags
     * @param flags Integer with bits set for present allergens
     */
    void setAllergenFlags(int flags);

    // ========== INDIVIDUAL ALLERGEN GETTERS ==========

    default boolean containsGluten() {
        return AllergenUtils.hasAllergen(getAllergenFlags(), AllergenUtils.GLUTEN);
    }

    default boolean containsCrustaceans() {
        return AllergenUtils.hasAllergen(getAllergenFlags(), AllergenUtils.CRUSTACEANS);
    }

    default boolean containsEggs() {
        return AllergenUtils.hasAllergen(getAllergenFlags(), AllergenUtils.EGGS);
    }

    default boolean containsFish() {
        return AllergenUtils.hasAllergen(getAllergenFlags(), AllergenUtils.FISH);
    }

    default boolean containsPeanuts() {
        return AllergenUtils.hasAllergen(getAllergenFlags(), AllergenUtils.PEANUTS);
    }

    default boolean containsSoy() {
        return AllergenUtils.hasAllergen(getAllergenFlags(), AllergenUtils.SOY);
    }

    default boolean containsMilk() {
        return AllergenUtils.hasAllergen(getAllergenFlags(), AllergenUtils.MILK);
    }

    default boolean containsNuts() {
        return AllergenUtils.hasAllergen(getAllergenFlags(), AllergenUtils.NUTS);
    }

    default boolean containsCelery() {
        return AllergenUtils.hasAllergen(getAllergenFlags(), AllergenUtils.CELERY);
    }

    default boolean containsMustard() {
        return AllergenUtils.hasAllergen(getAllergenFlags(), AllergenUtils.MUSTARD);
    }

    default boolean containsSesame() {
        return AllergenUtils.hasAllergen(getAllergenFlags(), AllergenUtils.SESAME);
    }

    default boolean containsSulfites() {
        return AllergenUtils.hasAllergen(getAllergenFlags(), AllergenUtils.SULFITES);
    }

    default boolean containsLupin() {
        return AllergenUtils.hasAllergen(getAllergenFlags(), AllergenUtils.LUPIN);
    }

    default boolean containsMolluscs() {
        return AllergenUtils.hasAllergen(getAllergenFlags(), AllergenUtils.MOLLUSCS);
    }

    // ========== INDIVIDUAL ALLERGEN SETTERS ==========

    default void setContainsGluten(boolean contains) {
        setAllergenFlags(AllergenUtils.setAllergen(getAllergenFlags(), AllergenUtils.GLUTEN, contains));
    }

    default void setContainsCrustaceans(boolean contains) {
        setAllergenFlags(AllergenUtils.setAllergen(getAllergenFlags(), AllergenUtils.CRUSTACEANS, contains));
    }

    default void setContainsEggs(boolean contains) {
        setAllergenFlags(AllergenUtils.setAllergen(getAllergenFlags(), AllergenUtils.EGGS, contains));
    }

    default void setContainsFish(boolean contains) {
        setAllergenFlags(AllergenUtils.setAllergen(getAllergenFlags(), AllergenUtils.FISH, contains));
    }

    default void setContainsPeanuts(boolean contains) {
        setAllergenFlags(AllergenUtils.setAllergen(getAllergenFlags(), AllergenUtils.PEANUTS, contains));
    }

    default void setContainsSoy(boolean contains) {
        setAllergenFlags(AllergenUtils.setAllergen(getAllergenFlags(), AllergenUtils.SOY, contains));
    }

    default void setContainsMilk(boolean contains) {
        setAllergenFlags(AllergenUtils.setAllergen(getAllergenFlags(), AllergenUtils.MILK, contains));
    }

    default void setContainsNuts(boolean contains) {
        setAllergenFlags(AllergenUtils.setAllergen(getAllergenFlags(), AllergenUtils.NUTS, contains));
    }

    default void setContainsCelery(boolean contains) {
        setAllergenFlags(AllergenUtils.setAllergen(getAllergenFlags(), AllergenUtils.CELERY, contains));
    }

    default void setContainsMustard(boolean contains) {
        setAllergenFlags(AllergenUtils.setAllergen(getAllergenFlags(), AllergenUtils.MUSTARD, contains));
    }

    default void setContainsSesame(boolean contains) {
        setAllergenFlags(AllergenUtils.setAllergen(getAllergenFlags(), AllergenUtils.SESAME, contains));
    }

    default void setContainsSulfites(boolean contains) {
        setAllergenFlags(AllergenUtils.setAllergen(getAllergenFlags(), AllergenUtils.SULFITES, contains));
    }

    default void setContainsLupin(boolean contains) {
        setAllergenFlags(AllergenUtils.setAllergen(getAllergenFlags(), AllergenUtils.LUPIN, contains));
    }

    default void setContainsMolluscs(boolean contains) {
        setAllergenFlags(AllergenUtils.setAllergen(getAllergenFlags(), AllergenUtils.MOLLUSCS, contains));
    }

    // ========== UTILITY METHODS ==========

    /**
     * Check if this item has any allergens
     */
    default boolean hasAnyAllergen() {
        return getAllergenFlags() != 0;
    }

    /**
     * Check if this item is safe for someone with given restrictions
     * @param userRestrictions Bit flags of allergens to avoid
     */
    default boolean isSafeFor(int userRestrictions) {
        return AllergenUtils.isSafeFor(getAllergenFlags(), userRestrictions);
    }

    /**
     * Check if this item is safe for another AllergenAware entity
     */
    default boolean isSafeFor(AllergenAware userProfile) {
        return isSafeFor(userProfile.getAllergenFlags());
    }

    /**
     * Get count of allergens present
     */
    default int getAllergenCount() {
        return Integer.bitCount(getAllergenFlags());
    }

    /**
     * Get localized names of all allergens present
     * @param context Android context for string resources
     */
    default String getLocalizedAllergenNames(Context context) {
        return AllergenUtils.getAllergenNames(context, getAllergenFlags());
    }

    /**
     * Copy allergen flags from another source
     */
    default void copyAllergensFrom(AllergenAware source) {
        setAllergenFlags(source.getAllergenFlags());
    }

    /**
     * Combine allergens from another source (OR operation)
     */
    default void addAllergensFrom(AllergenAware source) {
        setAllergenFlags(getAllergenFlags() | source.getAllergenFlags());
    }

    /**
     * Clear all allergen flags
     */
    default void clearAllAllergens() {
        setAllergenFlags(0);
    }
}