package li.masciul.sugardaddi.data.sources.thecocktaildb;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import li.masciul.sugardaddi.BuildConfig;
import li.masciul.sugardaddi.data.sources.base.management.CredentialType;
import li.masciul.sugardaddi.data.sources.base.management.ManagementProvider;

/**
 * TheCocktailDbManagementProvider - ManagementProvider for the TheCocktailDB settings card.
 *
 * CARD LAYOUT
 * ===========
 * ┌─────────────────────────────────────┐
 * │ TheCocktailDB            ● [toggle] │
 * │ Open cocktail database              │
 * ├─────────────────────────────────────┤
 * │ API CREDENTIALS                     │
 * │ [API key input field]               │
 * │ ⚠ Using free key - Patreon key      │  ← shown when DEMO_KEY ("1") active
 * │   required for public release       │
 * │ [Save]                              │
 * └─────────────────────────────────────┘
 *
 * TheCocktailDB has NO local database - all data is fetched on demand.
 * The local DB section is hidden entirely (hasLocalDatabase() = false).
 *
 * CREDENTIAL LIFECYCLE
 * ====================
 * Default:   BuildConfig.THECOCKTAILDB_API_KEY (DEMO_KEY "1" unless local.properties has a real key)
 * Stored in: SharedPreferences "thecocktaildb_prefs" under key "api_key"
 * Priority:  Stored key > BuildConfig key > DEMO_KEY
 * Warning:   Shown when active key equals DEMO_KEY
 *
 * KEY CHANGE + RETROFIT REINITIALIZE
 * ====================================
 * Because the key is a PATH SEGMENT in the base URL, saving a new key requires
 * rebuilding the Retrofit instance. TheCocktailDbDataSource.reinitialize() handles
 * this. Wired up via the optional ReinitializeCallback after the user saves a new key.
 */
public class TheCocktailDbManagementProvider implements ManagementProvider {

    /**
     * Optional callback fired after a new API key is saved.
     * Allows TheCocktailDbDataSource to rebuild its Retrofit instance immediately
     * without waiting for the next app launch.
     */
    public interface ReinitializeCallback {
        void onKeyChanged();
    }

    @Nullable
    private ReinitializeCallback reinitializeCallback;

    // ===== CONSTRUCTORS =====

    public TheCocktailDbManagementProvider() {}

    /**
     * @param reinitializeCallback Called after a successful key save.
     *                             Pass TheCocktailDbDataSource::reinitialize.
     */
    public TheCocktailDbManagementProvider(@Nullable ReinitializeCallback reinitializeCallback) {
        this.reinitializeCallback = reinitializeCallback;
    }

    // =========================================================================
    // CREDENTIALS
    // =========================================================================

    @Override
    public boolean hasCredentials() {
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
        String stored = prefs.getString(TheCocktailDbConstants.PREF_API_KEY, null);
        // Prefer stored user key; fall back to BuildConfig (which itself falls back
        // to DEMO_KEY "1" if local.properties has no real key)
        return stored != null ? stored : BuildConfig.THECOCKTAILDB_API_KEY;
    }

    @Override
    public void saveCredential(@NonNull Context context, @NonNull String value) {
        prefs(context).edit()
                .putString(TheCocktailDbConstants.PREF_API_KEY, value.trim())
                .apply();

        // Trigger Retrofit reinitialize - the key is part of the base URL path
        if (reinitializeCallback != null) {
            reinitializeCallback.onKeyChanged();
        }
    }

    @Nullable
    @Override
    public String getDefaultCredential() {
        return TheCocktailDbConstants.DEMO_KEY;
    }

    @Nullable
    @Override
    public String getCredentialWarning() {
        return "⚠ Free development key active - a Patreon key is required for public release";
    }

    // =========================================================================
    // LOCAL DATABASE - none
    // =========================================================================

    @Override public boolean hasLocalDatabase()                              { return false; }
    @Override public boolean isDatabaseReady(@NonNull Context context)       { return false; }
    @Nullable @Override public String getDatabaseVersion(@NonNull Context c) { return null; }
    @Override public int getDatabaseProductCount(@NonNull Context context)   { return 0; }
    @Override public int getDatabaseNutritionCount(@NonNull Context context) { return 0; }
    @Override public void startImport(@NonNull Context context)              { /* no-op */ }
    @Override public void resetDatabaseState(@NonNull Context context)       { /* no-op */ }

    // =========================================================================
    // BROADCAST KEYS - not applicable (no import service)
    // =========================================================================

    @NonNull @Override public String getBroadcastProgress()  { return ""; }
    @NonNull @Override public String getBroadcastComplete()  { return ""; }
    @NonNull @Override public String getBroadcastError()     { return ""; }
    @NonNull @Override public String getExtraPhaseKey()      { return ""; }
    @NonNull @Override public String getExtraPercentKey()    { return ""; }
    @NonNull @Override public String getExtraErrorKey()      { return ""; }

    // =========================================================================
    // HELPERS
    // =========================================================================

    private SharedPreferences prefs(@NonNull Context context) {
        return context.getSharedPreferences(
                TheCocktailDbConstants.PREFS_NAME, Context.MODE_PRIVATE);
    }
}