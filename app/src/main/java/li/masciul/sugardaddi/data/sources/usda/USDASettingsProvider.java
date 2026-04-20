package li.masciul.sugardaddi.data.sources.usda;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import li.masciul.sugardaddi.BuildConfig;
import li.masciul.sugardaddi.data.database.AppDatabase;
import li.masciul.sugardaddi.data.sources.base.settings.CredentialType;
import li.masciul.sugardaddi.data.sources.base.settings.SettingsProvider;

/**
 * USDASettingsProvider — SettingsProvider for the USDA FoodData Central settings card.
 *
 * CARD SECTIONS SHOWN:
 * ┌─────────────────────────────────────┐
 * │ USDA FoodData          ●  [toggle] │
 * │ US Department of Agriculture...     │
 * ├─────────────────────────────────────│
 * │ API CREDENTIALS                     │
 * │ [API key input field]               │
 * │ ⚠ Rate limited — register for key  │  ← shown when DEMO_KEY active
 * │ [Save]                              │
 * ├─────────────────────────────────────│
 * │ LOCAL DATABASE                      │
 * │ Not downloaded — online API active  │
 * │ [Check integrity] [result]          │
 * │ [Download database]                 │  ← user-initiated, Wifi-appropriate
 * └─────────────────────────────────────┘
 *
 * CREDENTIAL LIFECYCLE:
 * - Default:   BuildConfig.USDA_API_KEY (DEMO_KEY unless user set a real key in local.properties)
 * - Stored in: SharedPreferences "usda_import" under key "api_key"
 * - Priority:  Stored key > BuildConfig key > DEMO_KEY
 * - Warning:   Shown when active key equals DEMO_KEY
 *
 * LOCAL DB LIFECYCLE:
 * - Never auto-triggered (unlike Ciqual). User initiates from the settings card.
 * - USDAImportService handles the download + parse pipeline.
 * - On completion, "database_ready" prefs key is set to true.
 */
public class USDASettingsProvider implements SettingsProvider {

    // ===== CREDENTIAL SECTION =====

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
        String stored = prefs.getString(USDAConstants.PREF_API_KEY, null);
        // Prefer stored user key; fall back to BuildConfig (which itself falls
        // back to DEMO_KEY if local.properties has no real key).
        return stored != null ? stored : BuildConfig.USDA_API_KEY;
    }

    @Override
    public void saveCredential(@NonNull Context context, @NonNull String value) {
        prefs(context).edit()
                .putString(USDAConstants.PREF_API_KEY, value.trim())
                .apply();
    }

    @Nullable
    @Override
    public String getDefaultCredential() {
        return USDAConstants.DEMO_KEY;
    }

    @Nullable
    @Override
    public String getCredentialWarning() {
        return "⚠ Rate limited (30 req/h) — register for a free key at fdc.nal.usda.gov";
    }

    // ===== LOCAL DATABASE SECTION =====

    @Override
    public boolean hasLocalDatabase() {
        return true;
    }

    @Override
    public boolean isDatabaseReady(@NonNull Context context) {
        return USDAImportService.isImported(context);
    }

    @Nullable
    @Override
    public String getDatabaseVersion(@NonNull Context context) {
        return USDAImportService.getImportedVersion(context);
    }

    @Override
    public int getDatabaseProductCount(@NonNull Context context) {
        try {
            return AppDatabase.getInstance(context)
                    .combinedProductDao()
                    .getCountBySource(USDAConstants.SOURCE_ID);
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    public int getDatabaseNutritionCount(@NonNull Context context) {
        try {
            int count = 0;
            for (li.masciul.sugardaddi.data.database.dao.NutritionDao.DataSourceCount row :
                    AppDatabase.getInstance(context).nutritionDao().getNutritionCountBySource()) {
                if (USDAConstants.SOURCE_ID.equals(row.dataSource)) {
                    count = row.count;
                    break;
                }
            }
            return count;
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    public void startImport(@NonNull Context context) {
        Intent intent = new Intent(context, USDAImportService.class);
        context.startForegroundService(intent);
    }

    @Override
    public void resetDatabaseState(@NonNull Context context) {
        prefs(context).edit()
                .putBoolean(USDAConstants.PREF_DB_READY, false)
                .remove(USDAConstants.PREF_IMPORT_VERSION)
                .apply();
    }

    // ===== BROADCAST KEYS =====

    @NonNull
    @Override
    public String getBroadcastProgress() {
        return USDAConstants.BROADCAST_PROGRESS;
    }

    @NonNull
    @Override
    public String getBroadcastComplete() {
        return USDAConstants.BROADCAST_COMPLETE;
    }

    @NonNull
    @Override
    public String getBroadcastError() {
        return USDAConstants.BROADCAST_ERROR;
    }

    @NonNull
    @Override
    public String getExtraPhaseKey() {
        return USDAConstants.EXTRA_PHASE;
    }

    @NonNull
    @Override
    public String getExtraPercentKey() {
        return USDAConstants.EXTRA_PROGRESS_PCT;
    }

    @NonNull
    @Override
    public String getExtraErrorKey() {
        return USDAConstants.EXTRA_ERROR_MSG;
    }

    // ===== HELPERS =====

    private static SharedPreferences prefs(@NonNull Context context) {
        return context.getSharedPreferences(USDAConstants.PREFS_NAME, Context.MODE_PRIVATE);
    }
}