package li.masciul.sugardaddi.data.sources.ciqual;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import li.masciul.sugardaddi.data.database.AppDatabase;
import li.masciul.sugardaddi.data.database.dao.NutritionDao;
import li.masciul.sugardaddi.data.sources.base.settings.CredentialType;
import li.masciul.sugardaddi.data.sources.base.settings.SettingsProvider;

import java.util.List;

/**
 * CiqualSettingsProvider — SettingsProvider implementation for the Ciqual data source.
 *
 * RESPONSIBILITIES
 * ================
 * This class is the single place where SettingsActivity/DataSourceCardManager learns
 * everything it needs to render and interact with the Ciqual settings card:
 *
 *   - No credentials (Ciqual is a bundled dataset, no API key required)
 *   - Local database section: import state, version, integrity counts, reinitialise
 *   - Broadcast action strings for import progress updates
 *
 * WHAT IT DOES NOT OWN
 * =====================
 * - The import pipeline itself → CiqualImportService
 * - The dataset constants (version, file names) → CiqualConstants
 * - The enabled/disabled toggle → BaseDataSource.isEnabled() / setEnabled()
 * - The source identity (name, emoji) → CiqualDataSource / strings.xml
 *
 * THREADING
 * =========
 * getDatabaseProductCount() and getDatabaseNutritionCount() are synchronous Room
 * queries — always call them on a background thread (the card manager handles this).
 *
 * startImport() posts to the main thread internally so startForegroundService() is
 * always called from the foreground, satisfying Android 12+ restrictions.
 *
 * resetDatabaseState() writes SharedPreferences synchronously (apply() is async
 * but that's fine — the card manager refreshes the UI after calling this).
 */
public class CiqualSettingsProvider implements SettingsProvider {

    private static final String TAG = "CiqualSettingsProvider";

    // =========================================================================
    // CREDENTIALS — Ciqual has none
    // =========================================================================

    @Override
    public boolean hasCredentials() {
        // Ciqual is a bundled/downloaded dataset — no API key required
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
        return null; // No credential
    }

    @Override
    public void saveCredential(@NonNull Context context, @NonNull String value) {
        // No-op — Ciqual has no credential
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
    // LOCAL DATABASE
    // =========================================================================

    @Override
    public boolean hasLocalDatabase() {
        return true;
    }

    /**
     * True if the Ciqual DB has been successfully imported and the stored version
     * matches the current dataset version in CiqualConstants.
     *
     * Reads CiqualImportService's own SharedPreferences — this is the single source
     * of truth for import state. The SettingsActivity no longer reaches into those
     * prefs directly; it asks us instead.
     */
    @Override
    public boolean isDatabaseReady(@NonNull Context context) {
        return CiqualImportService.isImported(context);
    }

    /**
     * The dataset version string stored in SharedPreferences after a successful import.
     * Example: "2025_11_03". Returns null if not yet imported.
     */
    @Nullable
    @Override
    public String getDatabaseVersion(@NonNull Context context) {
        return CiqualImportService.getImportedVersion(context);
    }

    /**
     * Count of food product rows in Room for the CIQUAL source.
     * MUST be called on a background thread — this is a synchronous DB query.
     * Expected value after a full import: 3 484.
     */
    @Override
    public int getDatabaseProductCount(@NonNull Context context) {
        try {
            return AppDatabase.getInstance(context)
                    .combinedProductDao()
                    .getCountBySource(CiqualConstants.SOURCE_ID);
        } catch (Exception e) {
            Log.e(TAG, "getDatabaseProductCount failed", e);
            return 0;
        }
    }

    /**
     * Count of nutrition rows in Room that belong to Ciqual products.
     * MUST be called on a background thread — this is a synchronous DB query.
     * Expected value after a full import equals the product count (one record per food).
     */
    @Override
    public int getDatabaseNutritionCount(@NonNull Context context) {
        try {
            // NutritionDao.getNutritionCountBySource() returns a list of DataSourceCount
            // objects (one per distinct dataSource value). We find the CIQUAL entry.
            List<NutritionDao.DataSourceCount> counts =
                    AppDatabase.getInstance(context)
                            .nutritionDao()
                            .getNutritionCountBySource();

            for (NutritionDao.DataSourceCount row : counts) {
                if (CiqualConstants.SOURCE_ID.equals(row.dataSource)) {
                    return row.count;
                }
            }
            return 0;
        } catch (Exception e) {
            Log.e(TAG, "getDatabaseNutritionCount failed", e);
            return 0;
        }
    }

    /**
     * Start CiqualImportService via startForegroundService().
     *
     * Posting to the main thread guarantees we satisfy Android 12+'s requirement
     * that startForegroundService() is called while the app is in the foreground.
     * The DataSourceCardManager calls this from a button click (already main thread),
     * but the post() is kept as a safety net.
     */
    @Override
    public void startImport(@NonNull Context context) {
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
            try {
                Intent intent = new Intent(context.getApplicationContext(),
                        CiqualImportService.class);
                context.getApplicationContext().startForegroundService(intent);
                Log.i(TAG, "CiqualImportService started");
            } catch (Exception e) {
                Log.e(TAG, "Failed to start CiqualImportService", e);
            }
        });
    }

    /**
     * Reset the import state so the card reflects an empty/unimported DB.
     *
     * Clears CiqualImportService's SharedPreferences (so isImported() returns false)
     * but does NOT delete Room rows — that is the responsibility of the "clear cache"
     * button in the preferences card, which already calls the DB wipe separately.
     *
     * Call this after a cache clear or after an integrity check finds the DB empty,
     * so the card's status dot and text update correctly.
     */
    @Override
    public void resetDatabaseState(@NonNull Context context) {
        context.getApplicationContext()
                .getSharedPreferences(CiqualImportService.PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply();
        Log.d(TAG, "Ciqual import prefs cleared");
    }

    // =========================================================================
    // BROADCAST ACTION STRINGS
    // =========================================================================
    // These delegate directly to CiqualImportService constants so there is a
    // single definition point. If the service ever renames its actions, the
    // card manager picks up the change automatically.

    @NonNull
    @Override
    public String getBroadcastProgress() {
        return CiqualImportService.BROADCAST_PROGRESS;
    }

    @NonNull
    @Override
    public String getBroadcastComplete() {
        return CiqualImportService.BROADCAST_COMPLETE;
    }

    @NonNull
    @Override
    public String getBroadcastError() {
        return CiqualImportService.BROADCAST_ERROR;
    }

    @NonNull
    @Override
    public String getExtraPhaseKey() {
        return CiqualImportService.EXTRA_PHASE;
    }

    @NonNull
    @Override
    public String getExtraPercentKey() {
        return CiqualImportService.EXTRA_PROGRESS_PCT;
    }

    @NonNull
    @Override
    public String getExtraErrorKey() {
        return CiqualImportService.EXTRA_ERROR_MSG;
    }
}