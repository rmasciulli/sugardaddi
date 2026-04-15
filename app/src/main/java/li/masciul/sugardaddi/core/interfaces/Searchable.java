package li.masciul.sugardaddi.core.interfaces;

import li.masciul.sugardaddi.core.enums.ProductType;
import li.masciul.sugardaddi.core.enums.DataSource;

import java.util.Set;

/**
 * Searchable - Interface for items that can be searched and displayed
 *
 * Implemented by FoodProduct, Recipe, and Meal to enable:
 * - Unified search across all types
 * - Polymorphic display in search results
 * - Consistent search metadata handling
 */
public interface Searchable {

    /**
     * Get unique searchable identifier
     * @return Unique ID for this searchable item
     */
    String getSearchableId();

    /**
     * Get display name in specified language
     * @param language Language code (e.g., "en", "fr")
     * @return Localized display name
     */
    String getDisplayName(String language);

    /**
     * Get description in specified language
     * @param language Language code
     * @return Localized description, or null if not available
     */
    String getDescription(String language);

    /**
     * Get product type for filtering and display
     * @return ProductType (FOOD, RECIPE, MEAL)
     */
    ProductType getProductType();

    /**
     * Get data source information
     * @return DataSource that provided this item
     */
    DataSource getDataSource();

    /**
     * Get search tags for enhanced matching
     * @return Set of tags associated with this item
     */
    Set<String> getSearchTags();

    /**
     * Get relevance score for a search query
     * @param query Search query to match against
     * @param language Language to search in
     * @return Relevance score (0-100, higher = more relevant)
     */
    default int getSearchRelevance(String query, String language) {
        if (query == null || query.trim().isEmpty()) {
            return 0;
        }

        String normalizedQuery = query.toLowerCase().trim();
        int score = 0;

        // Name matching (highest weight)
        String name = getDisplayName(language);
        if (name != null) {
            String normalizedName = name.toLowerCase();
            if (normalizedName.equals(normalizedQuery)) {
                score += 100; // Exact match
            } else if (normalizedName.startsWith(normalizedQuery)) {
                score += 80; // Starts with query
            } else if (normalizedName.contains(normalizedQuery)) {
                score += 60; // Contains query
            }
        }

        // Description matching (medium weight)
        String description = getDescription(language);
        if (description != null && description.toLowerCase().contains(normalizedQuery)) {
            score += 40;
        }

        // Tag matching (lower weight)
        Set<String> tags = getSearchTags();
        for (String tag : tags) {
            if (tag.toLowerCase().contains(normalizedQuery)) {
                score += 20;
                break; // Only count once per item
            }
        }

        // Boost score based on data source reliability
        DataSource source = getDataSource();
        if (source != null) {
            switch (source) {
                case OPENFOODFACTS:
                    score += 10; // Reliable public data
                    break;
                case USER:
                    score += 5; // User-created content
                    break;
                case CIQUAL:
                    score += 15; // High-quality scientific data
                    break;
            }
        }

        return Math.min(score, 100); // Cap at 100
    }

    /**
     * Check if this item matches a search query
     * @param query Search query
     * @param language Language to search in
     * @param minRelevance Minimum relevance threshold
     * @return true if item matches with sufficient relevance
     */
    default boolean matchesSearch(String query, String language, int minRelevance) {
        return getSearchRelevance(query, language) >= minRelevance;
    }

    /**
     * Get search summary for display in results
     * @param language Language for the summary
     * @return Brief summary suitable for search results
     */
    default String getSearchSummary(String language) {
        StringBuilder summary = new StringBuilder();

        // Add type indicator
        summary.append(getProductType().getDisplayName()).append(": ");

        // Add name
        summary.append(getDisplayName(language));

        // Add brief description if available
        String description = getDescription(language);
        if (description != null && !description.trim().isEmpty()) {
            String shortDesc = description.length() > 50 ?
                    description.substring(0, 47) + "..." : description;
            summary.append(" - ").append(shortDesc);
        }

        return summary.toString();
    }

    /**
     * Get search keywords for indexing
     * @param language Language to generate keywords for
     * @return Set of keywords for search indexing
     */
    default Set<String> getSearchKeywords(String language) {
        Set<String> keywords = new java.util.HashSet<>();

        // Add name words
        String name = getDisplayName(language);
        if (name != null) {
            String[] nameWords = name.toLowerCase().split("\\s+");
            for (String word : nameWords) {
                if (word.length() > 2) { // Skip very short words
                    keywords.add(word);
                }
            }
        }

        // Add description words
        String description = getDescription(language);
        if (description != null) {
            String[] descWords = description.toLowerCase().split("\\s+");
            for (String word : descWords) {
                if (word.length() > 3) { // Longer threshold for description
                    keywords.add(word);
                }
            }
        }

        // Add tags
        keywords.addAll(getSearchTags());

        return keywords;
    }
}