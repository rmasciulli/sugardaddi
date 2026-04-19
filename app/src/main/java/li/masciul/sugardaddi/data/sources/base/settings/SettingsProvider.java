package li.masciul.sugardaddi.data.sources.base.settings;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * SettingsProvider — UI contract between a data source and the settings screen.
 *
 * DESIGN RATIONALE
 * ================
 * Each {@code DataSource} optionally produces one of these via
 * {@code DataSource.getSettingsProvider()}. Returning {@code null} means
 * "this source has no user-configurable settings" — the card shows only
 * the name, status dot, and enable/disable toggle.
 *
 * This interface is intentionally NOT part of the {@code DataSource} interface
 * itself, because settings concerns (credentials, import control, broadcast
 * actions) have nothing to do with search, product lookup, or lifecycle.
 * Keeping them separate means the search pipeline never needs to know whether
 * a source has an API key.
 *
 * IMPLEMENTORS
 * ============
 * - {@code CiqualSettingsProvider}        — local DB section, no credentials
 * - {@code OpenFoodFactsSettingsProvider} — no credentials, no local DB
 * - {@code USDASettingsProvider}          — credentials (API_KEY) + local DB
 *
 * SECTIONS
 * ========
 * The card manager reads these methods to decide which sections to show:
 *
 *   hasCredentials() → show credential input row
 *   hasLocalDatabase() → show local DB section (progress, integrity, reinitialise)
 *
 * Each section is independently optional. A source can have credentials but
 * no local DB (pure API with key), or a local DB but no credentials (Ciqual).
 *
 * THREADING
 * =========
 * Methods that touch SharedPreferences ({@code isEnabled}, {@code setEnabled},
 * {@code loadCredential}, {@code saveCredential}) are safe to call on the
 * main thread — they are fast prefs reads/writes.
 *
 * Methods that touch Room ({@code getDatabaseProductCount}) MUST be called
 * on a background thread; they are annotated accordingly.
 *
 * Methods that start services ({@code startImport}) must be called from the
 * foreground (Activity.onResume or later) to satisfy Android 12+ restrictions.
 */
public interface SettingsProvider {

    // =========================================================================
    // CREDENTIAL SECTION
    // =========================================================================

    /**
     * Whether this source requires user-supplied credentials.
     * If false, the credential section is hidden entirely.
     */
    boolean hasCredentials();

    /**
     * The kind of credential this source needs.
     * Only meaningful when {@link #hasCredentials()} returns true.
     */
    @NonNull
    CredentialType getCredentialType();

    /**
     * Load the currently stored credential from this source's own SharedPreferences.
     * Returns the {@link #getDefaultCredential()} if nothing has been saved yet.
     *
     * @param context Application context
     * @return Stored credential string, or default, or null if no default exists
     */
    @Nullable
    String loadCredential(@NonNull Context context);

    /**
     * Persist a new credential value in this source's own SharedPreferences.
     *
     * @param context Application context
     * @param value   The credential value entered by the user (trimmed, non-empty)
     */
    void saveCredential(@NonNull Context context, @NonNull String value);

    /**
     * The default credential pre-filled in the input field on first use.
     * Example: {@code "DEMO_KEY"} for USDA FoodData Central.
     * Return null if there is no meaningful default.
     */
    @Nullable
    String getDefaultCredential();

    /**
     * A short warning shown below the credential field when the default
     * credential is in use. Typically a rate-limit notice.
     * Example: {@code "⚠ Rate limited — register for a free key for unlimited access"}.
     * Return null if no warning is needed (or source has no default credential).
     */
    @Nullable
    String getCredentialWarning();

    // =========================================================================
    // LOCAL DATABASE SECTION
    // =========================================================================

    /**
     * Whether this source maintains a local Room database that the user
     * can download/reinitialise from the settings screen.
     * If false, the entire local DB section is hidden.
     */
    boolean hasLocalDatabase();

    /**
     * Whether the local database has been successfully imported and is
     * ready for offline search. Reads this source's own SharedPreferences.
     *
     * @param context Application context
     */
    boolean isDatabaseReady(@NonNull Context context);

    /**
     * The dataset version string currently stored in the local database,
     * or null if not yet imported.
     * Example: {@code "2025_11_03"} for Ciqual.
     *
     * @param context Application context
     */
    @Nullable
    String getDatabaseVersion(@NonNull Context context);

    /**
     * Count of food products currently stored in Room for this source.
     * MUST be called on a background thread — this is a synchronous DB query.
     *
     * @param context Application context
     * @return Row count, or 0 if the database is empty or not yet imported
     */
    int getDatabaseProductCount(@NonNull Context context);

    /**
     * Count of nutrition records currently stored in Room for this source.
     * MUST be called on a background thread — this is a synchronous DB query.
     *
     * @param context Application context
     * @return Row count, or 0 if the database is empty or not yet imported
     */
    int getDatabaseNutritionCount(@NonNull Context context);

    /**
     * Start the import foreground service for this source.
     * Must be called from the foreground (after onResume).
     *
     * @param context Activity or application context
     */
    void startImport(@NonNull Context context);

    /**
     * Clear this source's import SharedPreferences and optionally wipe
     * its Room rows, so the card reflects the real empty state.
     * Called on cache clear and after a failed integrity check.
     *
     * @param context Application context
     */
    void resetDatabaseState(@NonNull Context context);

    // =========================================================================
    // IMPORT PROGRESS BROADCASTS
    // =========================================================================
    // The DataSourceCardManager subscribes to these intent actions to drive
    // the progress bar and phase label during an ongoing import.
    // Each source defines its own action strings (they are package-scoped).

    /** Intent action broadcast periodically during import with progress data. */
    @NonNull
    String getBroadcastProgress();

    /** Intent action broadcast when the import finishes successfully. */
    @NonNull
    String getBroadcastComplete();

    /** Intent action broadcast when the import fails. */
    @NonNull
    String getBroadcastError();

    /**
     * Key for the phase description string in the progress broadcast extras.
     * Example: {@code "phase"} → intent.getStringExtra("phase")
     */
    @NonNull
    String getExtraPhaseKey();

    /**
     * Key for the progress percentage int in the progress broadcast extras.
     * Example: {@code "progress_pct"} → intent.getIntExtra("progress_pct", 0)
     */
    @NonNull
    String getExtraPercentKey();

    /**
     * Key for the error message string in the error broadcast extras.
     * Example: {@code "error_msg"} → intent.getStringExtra("error_msg")
     */
    @NonNull
    String getExtraErrorKey();
}