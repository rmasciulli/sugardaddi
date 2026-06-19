package li.masciul.sugardaddi.data.sources.themealdb;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import li.masciul.sugardaddi.BuildConfig;
import li.masciul.sugardaddi.data.network.NetworkConfig;
import li.masciul.sugardaddi.data.network.RetryStrategy;

/**
 * TheMealDbConfig - NetworkConfig implementation for TheMealDB.
 *
 * API KEY ARCHITECTURE
 * ====================
 * TheMealDB embeds the API key as a PATH SEGMENT in the base URL:
 *   https://www.themealdb.com/api/json/v1/{key}/search.php?s=query
 *
 * This means the base URL is KEY-DEPENDENT and cannot be a static constant.
 * Unlike USDA (which uses a query parameter and can change keys per-request),
 * TheMealDB requires the Retrofit instance to be rebuilt when the key changes.
 *
 * TheMealDbDataSource handles this via reinitialize() - called automatically
 * from TheMealDbSettingsProvider.saveCredential() when the user saves a new key.
 *
 * KEY PRIORITY (identical to USDA pattern)
 * =========================================
 * 1. SharedPreferences - user entered their own Patreon key in Settings
 * 2. BuildConfig.THEMEALDB_API_KEY - from local.properties at compile time
 * 3. TheMealDbConstants.DEMO_KEY ("1") - always works, no signup needed
 *
 * RETRY STRATEGY
 * ==============
 * EXPONENTIAL - TheMealDB is a community-hosted API with variable availability.
 * A few retries on transient failures are appropriate. Searches are debounced
 * at the data source level so retry cost is minimal.
 *
 * CACHING
 * =======
 * No persistent disk cache - TheMealDB ToS restricts redistribution without
 * a Patreon subscription. Session-scoped LRU cache is used in TheMealDbDataSource.
 */
public class TheMealDbConfig extends NetworkConfig {

    private final Context context;

    // ===== CONSTRUCTOR =====

    /**
     * @param context Application context - needed to read SharedPreferences for active API key.
     */
    public TheMealDbConfig(@NonNull Context context) {
        super(TheMealDbConstants.SOURCE_ID, Environment.PRODUCTION);
        this.context = context.getApplicationContext();
        setRetryStrategy(RetryStrategy.EXPONENTIAL);
    }

    // ===== REQUIRED OVERRIDES =====

    /**
     * Base URL for all TheMealDB API calls.
     * Constructed dynamically - the API key is a path segment, not a query param.
     * Called once during Retrofit initialization in TheMealDbDataSource.
     */
    @NonNull
    @Override
    protected String getBaseUrl() {
        return TheMealDbConstants.buildBaseUrl(getActiveApiKey());
    }

    /**
     * User-Agent sent with every request.
     */
    @NonNull
    @Override
    protected String getUserAgent() {
        return TheMealDbConstants.USER_AGENT;
    }

    // ===== API KEY MANAGEMENT =====

    /**
     * Returns the active API key using three-tier priority:
     *
     * 1. SharedPreferences - user entered a real Patreon key in Settings.
     *    This is the highest priority and allows users to supply their own key
     *    without recompiling the app.
     *
     * 2. BuildConfig.THEMEALDB_API_KEY - set via local.properties at compile time.
     *    Used by developers who have a Patreon key but don't want to enter it
     *    in the Settings UI every time.
     *
     * 3. DEMO_KEY ("1") - TheMealDB's public development key.
     *    Always functional. Safe for open-source distribution.
     *    No rate limits are published for v1 free tier.
     *
     * @return Active API key. Never null, never empty.
     */
    @NonNull
    public String getActiveApiKey() {
        // Priority 1: user-supplied key from Settings
        SharedPreferences prefs = context.getSharedPreferences(
                TheMealDbConstants.PREFS_NAME, Context.MODE_PRIVATE);
        String stored = prefs.getString(TheMealDbConstants.PREF_API_KEY, null);
        if (stored != null && !stored.trim().isEmpty()) {
            return stored.trim();
        }

        // Priority 2: compile-time key from local.properties
        if (BuildConfig.THEMEALDB_API_KEY != null
                && !BuildConfig.THEMEALDB_API_KEY.equals(TheMealDbConstants.DEMO_KEY)) {
            return BuildConfig.THEMEALDB_API_KEY;
        }

        // Priority 3: public development key - always works
        return TheMealDbConstants.DEMO_KEY;
    }

    /**
     * True if the active key is the public development key.
     * Used by TheMealDbSettingsProvider to show a warning when DEMO_KEY is active.
     */
    public boolean isUsingDemoKey() {
        return TheMealDbConstants.DEMO_KEY.equals(getActiveApiKey());
    }
}