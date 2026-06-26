package li.masciul.sugardaddi.data.sources.thecocktaildb;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import li.masciul.sugardaddi.BuildConfig;
import li.masciul.sugardaddi.data.network.NetworkConfig;
import li.masciul.sugardaddi.data.network.RetryStrategy;

/**
 * TheCocktailDbConfig - NetworkConfig implementation for TheCocktailDB.
 *
 * API KEY ARCHITECTURE
 * ====================
 * TheCocktailDB embeds the API key as a PATH SEGMENT in the base URL:
 *   https://www.thecocktaildb.com/api/json/v1/{key}/search.php?s=query
 *
 * This means the base URL is KEY-DEPENDENT and cannot be a static constant.
 * Changing the key requires rebuilding the Retrofit instance - handled by
 * TheCocktailDbDataSource.reinitialize(), called via ReinitializeCallback
 * from TheCocktailDbManagementProvider after the user saves a new key.
 *
 * KEY PRIORITY
 * ============
 * 1. SharedPreferences - user entered their own Patreon key in Settings
 * 2. BuildConfig.THECOCKTAILDB_API_KEY - from local.properties at compile time
 * 3. TheCocktailDbConstants.DEMO_KEY ("1") - always works, no signup needed
 *
 * RETRY STRATEGY
 * ==============
 * EXPONENTIAL - TheCocktailDB is community-hosted with variable availability.
 *
 * CACHING
 * =======
 * No persistent disk cache. Session-scoped LRU cache used in the data source.
 */
public class TheCocktailDbConfig extends NetworkConfig {

    private final Context context;

    // ===== CONSTRUCTOR =====

    /**
     * @param context Application context - needed to read SharedPreferences for active API key.
     */
    public TheCocktailDbConfig(@NonNull Context context) {
        super(TheCocktailDbConstants.SOURCE_ID, Environment.PRODUCTION);
        this.context = context.getApplicationContext();
        setRetryStrategy(RetryStrategy.EXPONENTIAL);
    }

    // ===== REQUIRED OVERRIDES =====

    /**
     * Base URL for all TheCocktailDB API calls.
     * Constructed dynamically - the API key is a path segment, not a query param.
     * Called once during Retrofit initialization in TheCocktailDbDataSource.
     */
    @NonNull
    @Override
    protected String getBaseUrl() {
        return TheCocktailDbConstants.buildBaseUrl(getActiveApiKey());
    }

    /**
     * User-Agent sent with every request.
     */
    @NonNull
    @Override
    protected String getUserAgent() {
        return TheCocktailDbConstants.USER_AGENT;
    }

    // ===== API KEY MANAGEMENT =====

    /**
     * Returns the active API key using three-tier priority:
     *
     * 1. SharedPreferences - user entered a real Patreon key in Settings.
     * 2. BuildConfig.THECOCKTAILDB_API_KEY - set via local.properties at compile time.
     * 3. DEMO_KEY ("1") - TheCocktailDB's public development key.
     *
     * @return Active API key. Never null, never empty.
     */
    @NonNull
    public String getActiveApiKey() {
        // Priority 1: user-supplied key from Settings
        SharedPreferences prefs = context.getSharedPreferences(
                TheCocktailDbConstants.PREFS_NAME, Context.MODE_PRIVATE);
        String stored = prefs.getString(TheCocktailDbConstants.PREF_API_KEY, null);
        if (stored != null && !stored.trim().isEmpty()) {
            return stored.trim();
        }

        // Priority 2: compile-time key from local.properties
        if (BuildConfig.THECOCKTAILDB_API_KEY != null
                && !BuildConfig.THECOCKTAILDB_API_KEY.equals(TheCocktailDbConstants.DEMO_KEY)) {
            return BuildConfig.THECOCKTAILDB_API_KEY;
        }

        // Priority 3: public development key - always works
        return TheCocktailDbConstants.DEMO_KEY;
    }

    /**
     * True if the active key is the public development key.
     * Used by TheCocktailDbManagementProvider to show a warning when DEMO_KEY is active.
     */
    public boolean isUsingDemoKey() {
        return TheCocktailDbConstants.DEMO_KEY.equals(getActiveApiKey());
    }
}