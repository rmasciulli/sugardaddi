package li.masciul.sugardaddi.core.utils;

import androidx.annotation.Nullable;

import li.masciul.sugardaddi.core.models.SourceIdentifier;

/**
 * RecipeUrlBuilder — Centralised URL generation for recipe source websites.
 *
 * Mirrors ProductUrlBuilder exactly in structure and pattern.
 * Builds external website URLs for recipes from TheMealDB and TheCocktailDB.
 *
 * SUPPORTED SOURCES
 * =================
 * - TheMealDB    : https://www.themealdb.com/meal/{id}
 * - TheCocktailDB: https://www.thecocktaildb.com/drink/{id}
 *
 * Both URLs are publicly accessible and display the full recipe page
 * with ingredients, instructions, and images — suitable for sharing.
 *
 * USAGE
 * =====
 * <pre>
 *   SourceIdentifier sourceId = recipe.getSourceIdentifier();
 *   String url = RecipeUrlBuilder.getWebsiteUrl(sourceId);
 *   if (url != null) {
 *       startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
 *   }
 * </pre>
 *
 * NOTES
 * =====
 * - Returns null for USER recipes (no external page exists)
 * - URLs are built on-the-fly, not stored in the database
 * - Thread-safe: all methods are static and stateless
 *
 * Place in: core/utils/RecipeUrlBuilder.java (alongside ProductUrlBuilder)
 */
public final class RecipeUrlBuilder {

    // =========================================================================
    // URL PATTERNS
    // =========================================================================

    /**
     * TheMealDB recipe page URL pattern.
     * Example: https://www.themealdb.com/meal/52772
     */
    private static final String THEMEALDB_URL_PATTERN =
            "https://www.themealdb.com/meal/%s";

    /**
     * TheCocktailDB recipe page URL pattern.
     * Example: https://www.thecocktaildb.com/drink/11007
     */
    private static final String THECOCKTAILDB_URL_PATTERN =
            "https://www.thecocktaildb.com/drink/%s";

    // Private constructor — utility class
    private RecipeUrlBuilder() {
        throw new UnsupportedOperationException("RecipeUrlBuilder is a utility class");
    }

    // =========================================================================
    // PUBLIC API
    // =========================================================================

    /**
     * Returns the external website URL for a recipe.
     *
     * @param sourceIdentifier The recipe's source identifier.
     * @return Website URL, or null if the source has no public recipe page
     *         (e.g. user-created recipes).
     */
    @Nullable
    public static String getWebsiteUrl(@Nullable SourceIdentifier sourceIdentifier) {
        if (sourceIdentifier == null) return null;

        String sourceId    = sourceIdentifier.getSourceId();
        String originalId  = sourceIdentifier.getOriginalId();

        if (sourceId == null || originalId == null || originalId.trim().isEmpty()) {
            return null;
        }

        switch (sourceId) {
            case "THEMEALDB":
                return String.format(THEMEALDB_URL_PATTERN, originalId);

            case "THECOCKTAILDB":
                return String.format(THECOCKTAILDB_URL_PATTERN, originalId);

            default:
                // USER recipes and any unknown source have no external page.
                return null;
        }
    }

    /**
     * Returns true if the recipe's source supports an external website link.
     *
     * @param sourceIdentifier The recipe's source identifier.
     * @return true if a URL can be built for this source.
     */
    public static boolean hasWebsiteSupport(@Nullable SourceIdentifier sourceIdentifier) {
        return getWebsiteUrl(sourceIdentifier) != null;
    }

    /**
     * Returns the human-readable website name for display in UI hints.
     * E.g. "View on TheMealDB" vs "View on TheCocktailDB".
     *
     * @param sourceIdentifier The recipe's source identifier.
     * @return Display name, or null if the source has no website.
     */
    @Nullable
    public static String getWebsiteName(@Nullable SourceIdentifier sourceIdentifier) {
        if (sourceIdentifier == null) return null;

        switch (sourceIdentifier.getSourceId() != null
                ? sourceIdentifier.getSourceId() : "") {
            case "THEMEALDB":     return "TheMealDB";
            case "THECOCKTAILDB": return "TheCocktailDB";
            default:              return null;
        }
    }
}