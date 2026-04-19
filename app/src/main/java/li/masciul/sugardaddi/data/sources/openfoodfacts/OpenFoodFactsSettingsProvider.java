package li.masciul.sugardaddi.data.sources.openfoodfacts;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import li.masciul.sugardaddi.data.sources.base.settings.CredentialType;
import li.masciul.sugardaddi.data.sources.base.settings.SettingsProvider;

/**
 * OpenFoodFactsSettingsProvider — SettingsProvider for OpenFoodFacts.
 *
 * OFF IS A NETWORK-ONLY SOURCE. It has:
 *   - No credentials required from the user (the app uses the public API)
 *   - No local database to download or manage
 *
 * The Settings card for OpenFoodFacts therefore shows only:
 *   - Source name + emoji (from DataSource / strings.xml)
 *   - Status dot (green/grey/red — from DataSource.getStatus())
 *   - Enable / disable toggle (from DataSource.isEnabled() / setEnabled())
 *
 * No credential section, no progress bar, no integrity check button.
 *
 * IF OFF EVER NEEDS AN API KEY
 * =============================
 * Override hasCredentials() → true, getCredentialType() → API_KEY,
 * implement loadCredential()/saveCredential() against a dedicated prefs file,
 * and wire the key into OpenFoodFactsConfig. This class is already structured
 * to make that addition trivial.
 */
public class OpenFoodFactsSettingsProvider implements SettingsProvider {

    // =========================================================================
    // CREDENTIALS — none required
    // =========================================================================

    @Override
    public boolean hasCredentials() {
        return false;
    }

    @NonNull
    @Override
    public CredentialType getCredentialType() {
        return CredentialType.NONE;
    }

    @Nullable
    @Override
    public String loadCredential(@NonNull Context context) {
        return null;
    }

    @Override
    public void saveCredential(@NonNull Context context, @NonNull String value) {
        // No-op
    }

    @Nullable
    @Override
    public String getDefaultCredential() {
        return null;
    }

    @Nullable
    @Override
    public String getCredentialWarning() {
        return null;
    }

    // =========================================================================
    // LOCAL DATABASE — not applicable for OFF
    // =========================================================================

    @Override
    public boolean hasLocalDatabase() {
        return false;
    }

    @Override
    public boolean isDatabaseReady(@NonNull Context context) {
        // OFF is always "ready" in the sense that no local DB is needed
        return true;
    }

    @Nullable
    @Override
    public String getDatabaseVersion(@NonNull Context context) {
        return null; // Not applicable
    }

    @Override
    public int getDatabaseProductCount(@NonNull Context context) {
        return 0; // No local DB — results come live from the network
    }

    @Override
    public int getDatabaseNutritionCount(@NonNull Context context) {
        return 0; // No local DB
    }

    @Override
    public void startImport(@NonNull Context context) {
        // No-op — OFF has no import pipeline
    }

    @Override
    public void resetDatabaseState(@NonNull Context context) {
        // No-op — OFF has no persistent import state to reset
    }

    // =========================================================================
    // BROADCAST ACTIONS — empty strings, card manager will never subscribe
    // since hasLocalDatabase() = false means the DB section is hidden entirely
    // =========================================================================

    @NonNull
    @Override
    public String getBroadcastProgress() {
        return ""; // Never used — hasLocalDatabase() is false
    }

    @NonNull
    @Override
    public String getBroadcastComplete() {
        return "";
    }

    @NonNull
    @Override
    public String getBroadcastError() {
        return "";
    }

    @NonNull
    @Override
    public String getExtraPhaseKey() {
        return "";
    }

    @NonNull
    @Override
    public String getExtraPercentKey() {
        return "";
    }

    @NonNull
    @Override
    public String getExtraErrorKey() {
        return "";
    }
}