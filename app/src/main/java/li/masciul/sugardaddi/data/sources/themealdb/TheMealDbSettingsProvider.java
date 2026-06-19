package li.masciul.sugardaddi.data.sources.themealdb;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import li.masciul.sugardaddi.BuildConfig;
import li.masciul.sugardaddi.data.sources.base.settings.CredentialType;
import li.masciul.sugardaddi.data.sources.base.settings.SettingsProvider;

/**
 * TheMealDbSettingsProvider - SettingsProvider for the TheMealDB settings card.
 *
 * CARD LAYOUT
 * ===========
 * ┌─────────────────────────────────────┐
 * │ 🍳 TheMealDB          ●  [toggle]   │
 * │ Open recipe database                │
 * ├─────────────────────────────────────┤
 * │ API CREDENTIALS                     │
 * │ [API key input field]               │
 * │ ⚠ Using free key - Patreon key      │  ← shown when DEMO_KEY ("1") active
 * │   required for public release       │
 * │ [Save]                              │
 * └─────────────────────────────────────┘
 *
 * TheMealDB has NO local database - all data is fetched on demand.
 * The local DB section is hidden entirely (hasLocalDatabase() = false).
 *
 * CREDENTIAL LIFECYCLE
 * ====================
 * Default:   BuildConfig.THEMEALDB_API_KEY (DEMO_KEY "1" unless local.properties has a real key)
 * Stored in: SharedPreferences "themealdb_prefs" under key "api_key"
 * Priority:  Stored key > BuildConfig key > DEMO_KEY
 * Warning:   Shown when active key equals DEMO_KEY
 *
 * KEY CHANGE + RETROFIT REINITIALIZE
 * ====================================
 * Because the TheMealDB API key is a PATH SEGMENT in the base URL, saving a new
 * key requires rebuilding the Retrofit instance. TheMealDbDataSource.reinitialize()
 * handles this. TheMealDbSettingsProvider calls it after saving via the optional
 * ReinitializeCallback - wired up by DataSourceManager at registration time.
 */
public class TheMealDbSettingsProvider implements SettingsProvider {

    /**
     * Optional callback fired after a new API key is saved.
     * Allows TheMealDbDataSource to rebuild its Retrofit instance immediately
     * without waiting for the next app launch.
     */
    public interface ReinitializeCallback {
        void onKeyChanged();
    }

    @Nullable
    private ReinitializeCallback reinitializeCallback;

    // ===== CONSTRUCTOR =====

    public TheMealDbSettingsProvider() {}

    /**
     * @param reinitializeCallback Called after a successful key save.
     *                             Pass TheMealDbDataSource::reinitialize.
     */
    public TheMealDbSettingsProvider(@Nullable ReinitializeCallback reinitializeCallback) {
        this.reinitializeCallback = reinitializeCallback;
    }

    // =========================================================================
    // CREDENTIALS
    // =========================================================================

    @Override
    public boolean hasCredentials() {
        // TheMealDB has an API key - it's free/public by default ("1"),
        // but users with a Patreon key can enter it here for public release builds.
        return true;
    }

    @NonNull
    @Override
    public CredentialType getCredentialType() {
        return CredentialType.API_KEY;
    }

    @Nullable
    @Override
    public String loadCredential(@NonNull Context context) {
        SharedPreferences prefs = prefs(context);
        String stored = prefs.getString(TheMealDbConstants.PREF_API_KEY, null);
        // Prefer stored user key; fall back to BuildConfig (which itself falls back
        // to DEMO_KEY "1" if local.properties has no real key)
        return stored != null ? stored : BuildConfig.THEMEALDB_API_KEY;
    }

    @Override
    public void saveCredential(@NonNull Context context, @NonNull String value) {
        prefs(context).edit()
                .putString(TheMealDbConstants.PREF_API_KEY, value.trim())
                .apply();

        // Trigger Retrofit reinitialize - the key is part of the base URL path
        if (reinitializeCallback != null) {
            reinitializeCallback.onKeyChanged();
        }
    }

    @Nullable
    @Override
    public String getDefaultCredential() {
        // The public development key - safe to show as the default value
        return TheMealDbConstants.DEMO_KEY;
    }

    @Nullable
    @Override
    public String getCredentialWarning() {
        // Shown when the demo key is active - reminds users that a Patreon
        // key is required for public app store distribution
        return "⚠ Free development key active - a Patreon key is required for public release";
    }

    // =========================================================================
    // LOCAL DATABASE - none
    // =========================================================================

    @Override
    public boolean hasLocalDatabase() {
        // TheMealDB is network-only with session-scoped LRU cache.
        // No persistent local database exists or is planned for v1.
        return false;
    }

    @Override
    public boolean isDatabaseReady(@NonNull Context context) {
        return false;
    }

    @Nullable
    @Override
    public String getDatabaseVersion(@NonNull Context context) {
        return null;
    }

    @Override
    public int getDatabaseProductCount(@NonNull Context context) {
        return 0;
    }

    @Override
    public int getDatabaseNutritionCount(@NonNull Context context) {
        return 0;
    }

    @Override
    public void startImport(@NonNull Context context) {
        // No-op - no import pipeline
    }

    @Override
    public void resetDatabaseState(@NonNull Context context) {
        // No-op - no persistent database state to reset
    }

    // =========================================================================
    // BROADCAST KEYS - not applicable (no import service)
    // =========================================================================

    @NonNull
    @Override
    public String getBroadcastProgress() { return ""; }

    @NonNull
    @Override
    public String getBroadcastComplete() { return ""; }

    @NonNull
    @Override
    public String getBroadcastError() { return ""; }

    @NonNull
    @Override
    public String getExtraPhaseKey() { return ""; }

    @NonNull
    @Override
    public String getExtraPercentKey() { return ""; }

    @NonNull
    @Override
    public String getExtraErrorKey() { return ""; }

    // =========================================================================
    // HELPERS
    // =========================================================================

    private static SharedPreferences prefs(@NonNull Context context) {
        return context.getSharedPreferences(
                TheMealDbConstants.PREFS_NAME, Context.MODE_PRIVATE);
    }
}