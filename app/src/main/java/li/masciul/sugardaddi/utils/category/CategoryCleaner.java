package li.masciul.sugardaddi.utils.category;

import android.util.Log;
import java.util.ArrayList;
import java.util.List;

import li.masciul.sugardaddi.utils.language.LanguageDetector;

public class CategoryCleaner {
    private static final String TAG = "CategoryCleaner";

    /**
     * Smart category parsing that automatically detects and matches languages
     * Fully extensible - no hard-coded language checks!
     */
    public static List<String> parseHierarchy(List<String> categoriesHierarchy, List<String> categoriesTags,
                                              String categories, String preferredLanguage) {
        List<String> result = new ArrayList<>();

        // Try different data sources in order of preference
        if (categoriesHierarchy != null && !categoriesHierarchy.isEmpty()) {
            result = parseSmartCategories(categoriesHierarchy, preferredLanguage);
            if (!result.isEmpty()) {
                Log.d(TAG, "Used categoriesHierarchy with " + result.size() + " items");
                return result;
            }
        }

        if (categoriesTags != null && !categoriesTags.isEmpty()) {
            result = parseSmartCategories(categoriesTags, preferredLanguage);
            if (!result.isEmpty()) {
                Log.d(TAG, "Used categoriesTags with " + result.size() + " items");
                return result;
            }
        }

        if (categories != null && !categories.isEmpty()) {
            result = parseRawCategoriesString(categories, preferredLanguage);
            Log.d(TAG, "Used raw categories with " + result.size() + " items");
        }

        return result;
    }

    /**
     * Smart category list parsing using language detection
     * Works with any language automatically!
     */
    private static List<String> parseSmartCategories(List<String> categoryList, String preferredLanguage) {
        List<String> result = new ArrayList<>();

        for (String category : categoryList) {
            String cleanCategory = null;

            // Check if category has language prefix (e.g., "fr:chocolat")
            if (category.contains(":")) {
                String[] parts = category.split(":", 2);
                if (parts.length == 2) {
                    String langPrefix = parts[0];
                    String categoryName = parts[1];

                    // Check if language prefix matches preferred language
                    if (LanguageDetector.languageMatches(langPrefix, preferredLanguage)) {
                        cleanCategory = extractCategoryName(categoryName);
                    }
                }
            } else {
                // No language prefix - use language detection
                String detectedLang = LanguageDetector.detectLanguageSync(category);

                if (LanguageDetector.languageMatches(detectedLang, preferredLanguage)) {
                    cleanCategory = extractCategoryName(category);
                }
            }

            // Add if valid
            if (cleanCategory != null && !cleanCategory.trim().isEmpty()) {
                result.add(cleanCategory);
            }
        }

        return result;
    }

    /**
     * Parse raw categories string (comma-separated) intelligently
     */
    private static List<String> parseRawCategoriesString(String categories, String preferredLanguage) {
        List<String> result = new ArrayList<>();
        String[] cats = categories.split(",");

        for (String cat : cats) {
            cat = cat.trim();
            if (cat.isEmpty()) continue;

            String cleanCategory = null;

            // Check if has language prefix
            if (cat.contains(":")) {
                String[] parts = cat.split(":", 2);
                if (parts.length == 2) {
                    String langPrefix = parts[0];
                    String categoryName = parts[1];

                    if (LanguageDetector.languageMatches(langPrefix, preferredLanguage)) {
                        cleanCategory = extractCategoryName(categoryName);
                    }
                }
            } else {
                // No prefix - use language detection
                String detectedLang = LanguageDetector.detectLanguageSync(cat);
                String bestMatch = LanguageDetector.findBestMatchingLanguage(detectedLang, preferredLanguage);

                if (LanguageDetector.languageMatches(bestMatch, preferredLanguage)) {
                    cleanCategory = extractCategoryName(cat);

                    Log.d(TAG, "Category '" + cat + "' detected as '" + detectedLang +
                            "', matched with '" + bestMatch + "' for preferred '" + preferredLanguage + "'");
                }
            }

            if (cleanCategory != null && !cleanCategory.trim().isEmpty()) {
                result.add(cleanCategory);
            }
        }

        return result;
    }

    /**
     * Get most specific category - fully dynamic
     */
    public static String getMostSpecificCategory(List<String> categoriesHierarchy, List<String> categoriesTags,
                                                 String categories, String preferredLanguage) {
        List<String> parsed = parseHierarchy(categoriesHierarchy, categoriesTags, categories, preferredLanguage);

        if (!parsed.isEmpty()) {
            String mostSpecific = parsed.get(parsed.size() - 1);
            return capitalizeWords(mostSpecific);
        }

        return null;
    }

    /**
     * Get hierarchy path - fully dynamic
     */
    public static String getHierarchyPath(List<String> categoriesHierarchy, List<String> categoriesTags,
                                          String categories, String preferredLanguage, int maxLevels) {
        List<String> parsed = parseHierarchy(categoriesHierarchy, categoriesTags, categories, preferredLanguage);

        if (parsed.isEmpty()) {
            return null;
        }

        // Show most relevant levels
        int startIndex = Math.max(0, parsed.size() - maxLevels);
        List<String> displayLevels = parsed.subList(startIndex, parsed.size());

        StringBuilder path = new StringBuilder();
        for (int i = 0; i < displayLevels.size(); i++) {
            if (i > 0) {
                path.append(" → ");
            }
            path.append(capitalizeWords(displayLevels.get(i)));
        }

        return path.toString();
    }

    // ... keep existing helper methods (extractCategoryName, capitalizeWords, etc.)

    /**
     * Extract category name from prefixed string
     */
    public static String extractCategoryName(String category) {
        if (category == null) return null;

        if (!category.contains(":")) return category;

        String categoryName = category.substring(category.indexOf(":") + 1);
        return categoryName.replace("-", " ")
                .replace("_", " ")
                .trim();
    }

    public static String capitalizeWords(String text) {
        if (text == null || text.isEmpty()) return text;

        String[] words = text.split("\\s+");
        StringBuilder result = new StringBuilder();

        for (String word : words) {
            if (!word.isEmpty()) {
                if (result.length() > 0) {
                    result.append(" ");
                }
                result.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) {
                    result.append(word.substring(1).toLowerCase());
                }
            }
        }

        return result.toString();
    }

    // Legacy compatibility
    public static List<String> cleanHierarchy(List<String> hierarchy, String preferredLanguage) {
        return parseHierarchy(hierarchy, null, null, preferredLanguage);
    }
}