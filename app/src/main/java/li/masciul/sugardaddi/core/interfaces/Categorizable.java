package li.masciul.sugardaddi.core.interfaces;

import li.masciul.sugardaddi.core.models.Category;

import java.util.List;
import java.util.Set;
import java.util.HashSet;

/**
 * Categorizable - Interface for items that can be categorized
 *
 * Implemented by FoodProduct, Recipe, and Meal to enable:
 * - Unified category-based filtering
 * - Hierarchical category navigation
 * - Consistent category display
 */
public interface Categorizable {

    /**
     * Get categories for this item in specified language
     * @param language Language code
     * @return List of categories (ordered by specificity)
     */
    List<Category> getCategories(String language);

    /**
     * Get primary category name
     * @param language Language code
     * @return Primary category name, or null if no categories
     */
    String getPrimaryCategory(String language);

    /**
     * Get category hierarchy path
     * @param language Language code
     * @return Hierarchical path from general to specific
     */
    List<String> getCategoryHierarchy(String language);

    /**
     * Check if this item belongs to a specific category
     * @param categoryName Category to check
     * @param language Language code
     * @return true if item belongs to the category
     */
    default boolean belongsToCategory(String categoryName, String language) {
        if (categoryName == null) return false;

        List<Category> categories = getCategories(language);
        for (Category category : categories) {
            if (categoryName.equalsIgnoreCase(category.getName())) {
                return true;
            }
        }

        // Also check hierarchy
        List<String> hierarchy = getCategoryHierarchy(language);
        for (String hierarchyLevel : hierarchy) {
            if (categoryName.equalsIgnoreCase(hierarchyLevel)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Check if this item belongs to any of the specified categories
     * @param categoryNames Categories to check
     * @param language Language code
     * @return true if item belongs to any of the categories
     */
    default boolean belongsToAnyCategory(Set<String> categoryNames, String language) {
        if (categoryNames == null || categoryNames.isEmpty()) return false;

        for (String categoryName : categoryNames) {
            if (belongsToCategory(categoryName, language)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get all category names (flat list)
     * @param language Language code
     * @return Set of all category names
     */
    default Set<String> getAllCategoryNames(String language) {
        Set<String> allNames = new HashSet<>();

        // Add category names
        List<Category> categories = getCategories(language);
        for (Category category : categories) {
            allNames.add(category.getName());
        }

        // Add hierarchy levels
        List<String> hierarchy = getCategoryHierarchy(language);
        allNames.addAll(hierarchy);

        return allNames;
    }

    /**
     * Get category display string for UI
     * @param language Language code
     * @param maxCategories Maximum number of categories to show
     * @return Formatted category string
     */
    default String getCategoryDisplayString(String language, int maxCategories) {
        List<Category> categories = getCategories(language);
        if (categories.isEmpty()) {
            return "Uncategorized";
        }

        StringBuilder display = new StringBuilder();
        int count = Math.min(categories.size(), maxCategories);

        for (int i = 0; i < count; i++) {
            if (i > 0) display.append(", ");
            display.append(categories.get(i).getName());
        }

        if (categories.size() > maxCategories) {
            display.append(" (+").append(categories.size() - maxCategories).append(" more)");
        }

        return display.toString();
    }

    /**
     * Get category breadcrumb for navigation
     * @param language Language code
     * @param separator Separator between levels
     * @return Breadcrumb string
     */
    default String getCategoryBreadcrumb(String language, String separator) {
        List<String> hierarchy = getCategoryHierarchy(language);
        if (hierarchy.isEmpty()) {
            return "Home";
        }

        return String.join(separator, hierarchy);
    }

    /**
     * Calculate category similarity with another categorizable item
     * @param other Other categorizable item
     * @param language Language code
     * @return Similarity score (0.0-1.0, higher = more similar)
     */
    default double getCategorySimilarity(Categorizable other, String language) {
        if (other == null) return 0.0;

        Set<String> thisCategories = getAllCategoryNames(language);
        Set<String> otherCategories = other.getAllCategoryNames(language);

        if (thisCategories.isEmpty() && otherCategories.isEmpty()) {
            return 1.0; // Both uncategorized
        }

        if (thisCategories.isEmpty() || otherCategories.isEmpty()) {
            return 0.0; // One categorized, one not
        }

        // Calculate Jaccard similarity
        Set<String> intersection = new HashSet<>(thisCategories);
        intersection.retainAll(otherCategories);

        Set<String> union = new HashSet<>(thisCategories);
        union.addAll(otherCategories);

        return (double) intersection.size() / union.size();
    }

    /**
     * Get suggested related categories
     * @param language Language code
     * @return Set of categories that might be related
     */
    default Set<String> getSuggestedRelatedCategories(String language) {
        Set<String> suggestions = new HashSet<>();

        // This is a basic implementation - could be enhanced with ML
        List<String> hierarchy = getCategoryHierarchy(language);

        // Suggest sibling categories (same parent)
        if (hierarchy.size() >= 2) {
            String parentCategory = hierarchy.get(hierarchy.size() - 2);
            suggestions.add(parentCategory + " - Related");
        }

        // Suggest broader categories
        for (int i = 0; i < hierarchy.size() - 1; i++) {
            suggestions.add(hierarchy.get(i));
        }

        return suggestions;
    }
}