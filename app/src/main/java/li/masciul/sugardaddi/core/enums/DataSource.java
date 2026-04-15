package li.masciul.sugardaddi.core.enums;

import android.content.Context;
import li.masciul.sugardaddi.R;

/**
 * DataSource - Defines where food data comes from
 *
 * ARCHITECTURE v2.0 - Internationalization & Caching Support:
 * - Removed hardcoded English strings (now uses strings.xml)
 * - Removed scoring priority (now handled by dedicated scorers)
 * - Added requiresCaching flag (respects API terms of service)
 * - Full i18n support via Context.getString(R.string.*)
 *
 * Used for:
 * - Source attribution in UI
 * - Cache policy enforcement
 * - Network requirement detection
 * - UI display (localized names and descriptions)
 */
public enum DataSource {

    /**
     * OpenFoodFacts - Collaborative open database
     * - Allows caching: Yes (Open Database License)
     * - Requires network: Yes
     * - Data quality: Very good for branded products
     */
    OPENFOODFACTS("OPENFOODFACTS", true, true),

    /**
     * Ciqual - French scientific database by ANSES
     * - Allows caching: Yes, but not used (local database instance)
     * - Requires network: Yes (until local database implemented)
     * - Data quality: Excellent scientific data
     */
    CIQUAL("CIQUAL", false, true),

    /**
     * USDA FoodData Central - US Department of Agriculture
     * - Allows caching: No (prohibited by terms of service)
     * - Requires network: Yes
     * - Data quality: Excellent scientific data
     */
    USDA("USDA", false, true),

    /**
     * User-created recipes and custom foods
     * - Allows caching: Yes (user owns the data)
     * - Requires network: No (stored locally)
     * - Data quality: Variable
     */
    USER("USER", true, false),

    /**
     * Manually entered food data
     * - Allows caching: Yes (user owns the data)
     * - Requires network: No (stored locally)
     * - Data quality: Variable
     */
    CUSTOM("CUSTOM", true, false),

    /**
     * Data imported from other apps
     * - Allows caching: Yes (user owns the data)
     * - Requires network: No (already imported)
     * - Data quality: Variable
     */
    IMPORTED("IMPORTED", true, false),

    /**
     * Locally cached data (legacy/internal)
     * - Allows caching: Yes (already cached)
     * - Requires network: No
     * - Data quality: N/A
     */
    API_CACHE("CACHE", true, false);

    // ========== ENUM FIELDS ==========

    private final String id;
    private final boolean allowsCaching;
    private final boolean requiresNetwork;

    /**
     * Constructor
     *
     * @param id Unique identifier (used for database storage)
     * @param allowsCaching Whether this source's data can be cached
     * @param requiresNetwork Whether this source requires network access
     */
    DataSource(String id, boolean allowsCaching, boolean requiresNetwork) {
        this.id = id;
        this.allowsCaching = allowsCaching;
        this.requiresNetwork = requiresNetwork;
    }

    // ========== BASIC GETTERS ==========

    /**
     * Get unique identifier
     * Used for database storage and API communication
     */
    public String getId() {
        return id;
    }

    /**
     * Check if this source's data can be cached
     *
     * Important for respecting API terms of service:
     * - OpenFoodFacts: YES (Open Database License allows caching)
     * - Ciqual: NO (Terms prohibit caching)
     * - USDA: NO (Terms prohibit caching)
     * - User content: YES (User owns the data)
     *
     * @return true if caching is allowed
     */
    public boolean allowsCaching() {
        return allowsCaching;
    }

    /**
     * Check if this source requires network access
     *
     * @return true if network is required
     */
    public boolean requiresNetwork() {
        return requiresNetwork;
    }

    // ========== LOCALIZED STRINGS (i18n) ==========

    /**
     * Get localized display name
     *
     * Examples:
     * - EN: "OpenFoodFacts"
     * - FR: "OpenFoodFacts" (proper noun)
     *
     * @param context Android context for string resources
     * @return Localized name
     */
    public String getDisplayName(Context context) {
        int stringId = getNameStringId();
        return stringId != 0 ? context.getString(stringId) : id;
    }

    /**
     * Get localized description
     *
     * Examples:
     * - EN: "Open database of food products"
     * - FR: "Base de données ouverte des produits alimentaires"
     *
     * @param context Android context for string resources
     * @return Localized description
     */
    public String getDescription(Context context) {
        int stringId = getDescriptionStringId();
        return stringId != 0 ? context.getString(stringId) : "";
    }

    /**
     * Get localized attribution text (short version)
     *
     * Used for inline attribution in UI
     *
     * Examples:
     * - EN: "Data from OpenFoodFacts.org"
     * - FR: "Données de OpenFoodFacts.org"
     *
     * @param context Android context for string resources
     * @return Localized attribution (short)
     */
    public String getAttributionText(Context context) {
        int stringId = getAttributionStringId();
        return stringId != 0 ? context.getString(stringId) : "";
    }

    /**
     * Get localized full attribution (legal notice)
     *
     * Used for detailed attribution panels, about screens, etc.
     * Includes license information and usage terms.
     *
     * Examples:
     * - OpenFoodFacts: Full ODbL license text
     * - Ciqual: Full ANSES attribution with Open Licence reference
     *
     * @param context Android context for string resources
     * @return Localized full attribution with legal notice
     */
    public String getFullAttribution(Context context) {
        int stringId = getFullAttributionStringId();
        return stringId != 0 ? context.getString(stringId) : getAttributionText(context);
    }

    /**
     * Get website URL
     *
     * @param context Android context for string resources
     * @return Website URL or null if not applicable
     */
    public String getWebsiteUrl(Context context) {
        int stringId = getWebsiteStringId();
        return stringId != 0 ? context.getString(stringId) : null;
    }

    /**
     * Get display text with emoji (if emoji is defined in strings)
     *
     * Examples:
     * - "🌍 OpenFoodFacts"
     * - "🇫🇷 Ciqual"
     *
     * @param context Android context for string resources
     * @return Display name with emoji prefix if available
     */
    public String getDisplayWithEmoji(Context context) {
        int emojiId = getEmojiStringId();
        if (emojiId != 0) {
            String emoji = context.getString(emojiId);
            return emoji + " " + getDisplayName(context);
        }
        return getDisplayName(context);
    }

    // ========== PRIVATE: String Resource ID Mapping ==========

    /**
     * Map enum value to name string resource ID
     */
    private int getNameStringId() {
        switch (this) {
            case OPENFOODFACTS: return R.string.source_name_openfoodfacts;
            case CIQUAL: return R.string.source_name_ciqual;
            case USDA: return R.string.source_name_usda;
            case USER: return R.string.source_name_user;
            case CUSTOM: return R.string.source_name_custom;
            case IMPORTED: return R.string.source_name_imported;
            default: return 0;
        }
    }

    /**
     * Map enum value to description string resource ID
     */
    private int getDescriptionStringId() {
        switch (this) {
            case OPENFOODFACTS: return R.string.source_description_openfoodfacts;
            case CIQUAL: return R.string.source_description_ciqual;
            case USDA: return R.string.source_description_usda;
            case USER: return R.string.source_description_user;
            case CUSTOM: return R.string.source_description_custom;
            case IMPORTED: return R.string.source_description_imported;
            default: return 0;
        }
    }

    /**
     * Map enum value to attribution (short) string resource ID
     */
    private int getAttributionStringId() {
        switch (this) {
            case OPENFOODFACTS: return R.string.source_attribution_openfoodfacts;
            case CIQUAL: return R.string.source_attribution_ciqual;
            case USDA: return R.string.source_attribution_usda;
            case USER: return R.string.source_attribution_user;
            case CUSTOM: return R.string.source_attribution_custom;
            case IMPORTED: return R.string.source_attribution_imported;
            default: return 0;
        }
    }

    /**
     * Map enum value to full attribution string resource ID
     */
    private int getFullAttributionStringId() {
        switch (this) {
            case OPENFOODFACTS: return R.string.source_full_attribution_openfoodfacts;
            case CIQUAL: return R.string.source_full_attribution_ciqual;
            case USDA: return R.string.source_full_attribution_usda;
            default: return 0; // User content doesn't need full attribution
        }
    }

    /**
     * Map enum value to website string resource ID
     */
    private int getWebsiteStringId() {
        switch (this) {
            case OPENFOODFACTS: return R.string.source_website_openfoodfacts;
            case CIQUAL: return R.string.source_website_ciqual;
            case USDA: return R.string.source_website_usda;
            default: return 0;
        }
    }

    /**
     * Map enum value to emoji string resource ID
     */
    private int getEmojiStringId() {
        switch (this) {
            case OPENFOODFACTS: return R.string.source_emoji_openfoodfacts;
            case CIQUAL: return R.string.source_emoji_ciqual;
            case USDA: return R.string.source_emoji_usda;
            case USER: return R.string.source_emoji_user;
            case CUSTOM: return R.string.source_emoji_custom;
            case IMPORTED: return R.string.source_emoji_imported;
            default: return 0;
        }
    }

    // ========== UTILITY METHODS ==========

    /**
     * Get DataSource from string ID
     */
    public static DataSource fromString(String id) {
        if (id == null) return null;

        for (DataSource source : values()) {
            if (source.id.equalsIgnoreCase(id)) {
                return source;
            }
        }
        return null;
    }

    /**
     * Check if this is a public/open data source
     */
    public boolean isPublic() {
        return this == OPENFOODFACTS || this == CIQUAL || this == USDA;
    }

    /**
     * Check if this is user-generated content
     */
    public boolean isUserGenerated() {
        return this == USER || this == CUSTOM || this == IMPORTED;
    }

    /**
     * Check if this source provides high-quality scientific data
     */
    public boolean isScientific() {
        return this == CIQUAL || this == USDA;
    }

    @Override
    public String toString() {
        return id;
    }
}