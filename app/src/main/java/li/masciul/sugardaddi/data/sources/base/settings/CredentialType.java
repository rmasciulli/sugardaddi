package li.masciul.sugardaddi.data.sources.base.settings;

/**
 * CredentialType — The kind of credential a data source may require.
 *
 * Used by {@link SettingsProvider} to tell {@code DataSourceCardManager}
 * how to render the credential input section and how to label it.
 *
 * NONE is never stored on a source that returns hasCredentials() = false;
 * it exists only as a safe default so callers never have to null-check.
 */
public enum CredentialType {

    /**
     * No credential required.
     * Sources that return hasCredentials() = false use this implicitly.
     */
    NONE("None"),

    /**
     * A single opaque string token registered on a developer portal.
     * Example: USDA FoodData Central API key, OpenFoodFacts off-server key.
     * UI renders a single-line text field labelled "API key".
     */
    API_KEY("API key"),

    /**
     * HTTP Basic Authentication — username + password pair.
     * UI renders two fields: "Username" and "Password" (password masked).
     */
    BASIC_AUTH("Username / Password"),

    /**
     * Bearer token — a longer-lived JWT or OAuth token pasted by the user.
     * UI renders a single multi-line text field labelled "Bearer token".
     */
    BEARER("Bearer token");

    // Human-readable label shown in the settings card header
    private final String displayLabel;

    CredentialType(String displayLabel) {
        this.displayLabel = displayLabel;
    }

    public String getDisplayLabel() {
        return displayLabel;
    }
}