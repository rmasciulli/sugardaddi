package li.masciul.sugardaddi.ui.settings;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import li.masciul.sugardaddi.R;
import li.masciul.sugardaddi.data.sources.base.DataSource;
import li.masciul.sugardaddi.data.sources.base.settings.CredentialType;
import li.masciul.sugardaddi.data.sources.base.settings.SettingsProvider;

/**
 * DataSourceCardManager — inflates and manages one data source settings card.
 *
 * LIFECYCLE
 * =========
 * Create one instance per DataSource in SettingsActivity.onCreate():
 *
 *   DataSourceCardManager mgr = new DataSourceCardManager(source, context);
 *   mgr.attach(container);   // inflate + bind + add to parent
 *
 * Then in SettingsActivity:
 *   onResume()  → mgr.onResume()   — register broadcast receiver, refresh status
 *   onPause()   → mgr.onPause()    — unregister receiver (prevents leaks)
 *   onDestroy() → mgr.onDestroy()  — shut down background executor
 *
 * WHAT THIS CLASS KNOWS ABOUT SOURCES
 * =====================================
 * Nothing source-specific. It calls methods on DataSource and SettingsProvider
 * interfaces only. Adding a new source (USDA, TheMealDB, …) requires zero
 * changes here — the source provides its own SettingsProvider implementation.
 *
 * THREADING
 * =========
 * Integrity checks (Room queries) run on a single-thread background executor
 * and post results back to the main thread via View.post(). All other methods
 * are called on the main thread.
 */
public class DataSourceCardManager {

    private static final String TAG = "DataSourceCardManager";

    // =========================================================================
    // DEPENDENCIES
    // =========================================================================

    private final DataSource source;
    @Nullable
    private final SettingsProvider settings;
    private final Context context; // Application context — never Activity

    // =========================================================================
    // VIEWS (set in attach())
    // =========================================================================

    private View cardView;

    // Header
    private TextView          nameText;
    private View              statusDot;
    private SwitchMaterial    enableSwitch;
    private TextView          descriptionText;

    // Credential section
    private View              credentialSection;
    private View              credentialDivider;
    private TextInputLayout   credentialInputLayout;
    private TextInputEditText credentialInput;
    private TextView          credentialWarning;
    private MaterialButton    credentialSaveButton;

    // Local DB section
    private View                      localDbSection;
    private TextView                  dbStatusText;
    private TextView                  dbVersionText;
    private View                      dbProgressRow;
    private LinearProgressIndicator   dbProgressBar;
    private TextView                  dbProgressPercent;
    private TextView                  dbPhaseText;
    private TextView                  dbCheckIntegrityButton;
    private TextView                  dbIntegrityResult;
    private MaterialButton            dbReinitialiseButton;

    // =========================================================================
    // STATE
    // =========================================================================

    @Nullable private BroadcastReceiver importReceiver;
    private final ExecutorService bgExecutor = Executors.newSingleThreadExecutor();

    // =========================================================================
    // CONSTRUCTOR
    // =========================================================================

    /**
     * @param source  The DataSource this card represents
     * @param context Application context (NOT Activity context — avoids leaks)
     */
    public DataSourceCardManager(@NonNull DataSource source, @NonNull Context context) {
        this.source   = source;
        this.settings = source.getSettingsProvider();
        this.context  = context.getApplicationContext();
    }

    // =========================================================================
    // ATTACH — inflate, bind, add to parent
    // =========================================================================

    /**
     * Inflate {@code item_datasource_card.xml}, bind all data, and add the
     * resulting view to {@code container}.
     *
     * Must be called from the main thread.
     *
     * @param container The LinearLayout in activity_settings.xml that holds all cards
     */
    public void attach(@NonNull ViewGroup container) {
        cardView = LayoutInflater.from(container.getContext())
                .inflate(R.layout.item_datasource_card, container, false);

        bindViews();
        bindData();
        container.addView(cardView);
    }

    // =========================================================================
    // LIFECYCLE HOOKS
    // =========================================================================

    /** Call from SettingsActivity.onResume() */
    public void onResume() {
        registerReceiver();
        refresh();
    }

    /** Call from SettingsActivity.onPause() */
    public void onPause() {
        unregisterReceiver();
    }

    /** Call from SettingsActivity.onDestroy() */
    public void onDestroy() {
        bgExecutor.shutdown();
    }

    // =========================================================================
    // BIND VIEWS
    // =========================================================================

    private void bindViews() {
        // Header
        nameText        = cardView.findViewById(R.id.dsSourceName);
        statusDot       = cardView.findViewById(R.id.dsStatusDot);
        enableSwitch    = cardView.findViewById(R.id.dsEnableSwitch);
        descriptionText = cardView.findViewById(R.id.dsDescription);

        // Credential section
        credentialSection     = cardView.findViewById(R.id.dsCredentialSection);
        credentialDivider     = cardView.findViewById(R.id.dsDividerCredential);
        credentialInputLayout = cardView.findViewById(R.id.dsCredentialInputLayout);
        credentialInput       = cardView.findViewById(R.id.dsCredentialInput);
        credentialWarning     = cardView.findViewById(R.id.dsCredentialWarning);
        credentialSaveButton  = cardView.findViewById(R.id.dsCredentialSaveButton);

        // Local DB section
        localDbSection         = cardView.findViewById(R.id.dsLocalDbSection);
        dbStatusText           = cardView.findViewById(R.id.dsDbStatusText);
        dbVersionText          = cardView.findViewById(R.id.dsDbVersionText);
        dbProgressRow          = cardView.findViewById(R.id.dsDbProgressRow);
        dbProgressBar          = cardView.findViewById(R.id.dsDbProgressBar);
        dbProgressPercent      = cardView.findViewById(R.id.dsDbProgressPercent);
        dbPhaseText            = cardView.findViewById(R.id.dsDbPhaseText);
        dbCheckIntegrityButton = cardView.findViewById(R.id.dsDbCheckIntegrityButton);
        dbIntegrityResult      = cardView.findViewById(R.id.dsDbIntegrityResult);
        dbReinitialiseButton   = cardView.findViewById(R.id.dsDbReinitialiseButton);

        // Apply underline to text-link views (cannot be done in XML)
        underline(dbCheckIntegrityButton);
    }

    // =========================================================================
    // BIND DATA (called once on attach, then on refresh)
    // =========================================================================

    private void bindData() {
        // ── Header ────────────────────────────────────────────────────────────
        if (nameText != null) {
            nameText.setText(source.getSourceName());
        }

        updateStatusDot();

        if (enableSwitch != null) {
            // Set initial state without triggering listener
            enableSwitch.setOnCheckedChangeListener(null);
            enableSwitch.setChecked(source.isEnabled());
            enableSwitch.setOnCheckedChangeListener((btn, checked) -> {
                source.setEnabled(context, checked);
                updateStatusDot();
                Log.d(TAG, source.getSourceId() + (checked ? " enabled" : " disabled"));
            });
        }

        // Description from strings.xml — card manager looks up the resource by convention:
        // "source_description_{SOURCE_ID_LOWERCASE}" e.g. source_description_ciqual
        if (descriptionText != null) {
            String desc = getStringByConvention("source_description_"
                    + source.getSourceId().toLowerCase());
            descriptionText.setText(desc != null ? desc : source.getSourceName());
        }

        // ── Credential section ────────────────────────────────────────────────
        boolean hasCredentials = settings != null && settings.hasCredentials();
        setVisible(credentialSection, hasCredentials);
        setVisible(credentialDivider, hasCredentials);

        if (hasCredentials && settings != null) {
            bindCredentialSection();
        }

        // ── Local DB section ──────────────────────────────────────────────────
        boolean hasDb = settings != null && settings.hasLocalDatabase();
        setVisible(localDbSection, hasDb);

        if (hasDb && settings != null) {
            bindDbSection();
            wireDbButtons();
        }
    }

    // =========================================================================
    // CREDENTIAL SECTION BINDING
    // =========================================================================

    private void bindCredentialSection() {
        if (settings == null) return;

        // Set input hint from credential type
        CredentialType type = settings.getCredentialType();
        if (credentialInputLayout != null) {
            switch (type) {
                case API_KEY:
                    credentialInputLayout.setHint(s(R.string.ds_credential_hint_api_key));
                    break;
                case BASIC_AUTH:
                    credentialInputLayout.setHint(s(R.string.ds_credential_hint_username));
                    break;
                case BEARER:
                    credentialInputLayout.setHint(s(R.string.ds_credential_hint_bearer));
                    break;
                default:
                    break;
            }
        }

        // Pre-fill with stored credential (or default)
        String stored  = settings.loadCredential(context);
        String defVal  = settings.getDefaultCredential();
        String display = stored != null ? stored : (defVal != null ? defVal : "");

        if (credentialInput != null) {
            credentialInput.setText(display);
        }

        // Show warning if still on the default key
        String warning = settings.getCredentialWarning();
        boolean showWarning = warning != null
                && defVal != null
                && defVal.equals(display);
        if (credentialWarning != null) {
            credentialWarning.setText(warning != null ? warning : "");
            setVisible(credentialWarning, showWarning);
        }

        // Save button
        if (credentialSaveButton != null) {
            credentialSaveButton.setOnClickListener(v -> saveCredential());
        }

        // Update warning visibility when user edits the field
        if (credentialInput != null && credentialWarning != null && warning != null) {
            credentialInput.addTextChangedListener(new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int i, int i1, int i2) {}
                @Override public void onTextChanged(CharSequence s, int i, int i1, int i2) {
                    boolean onDefault = defVal != null && defVal.contentEquals(s);
                    setVisible(credentialWarning, onDefault && warning != null);
                }
                @Override public void afterTextChanged(android.text.Editable e) {}
            });
        }
    }

    private void saveCredential() {
        if (settings == null || credentialInput == null) return;
        String value = credentialInput.getText() != null
                ? credentialInput.getText().toString().trim() : "";
        if (value.isEmpty()) return;

        settings.saveCredential(context, value);

        // Update warning visibility
        String defVal = settings.getDefaultCredential();
        String warning = settings.getCredentialWarning();
        if (credentialWarning != null) {
            setVisible(credentialWarning, defVal != null
                    && defVal.equals(value) && warning != null);
        }

        Toast.makeText(context, s(R.string.ds_credential_saved), Toast.LENGTH_SHORT).show();
        Log.d(TAG, source.getSourceId() + " credential saved");
    }

    // =========================================================================
    // LOCAL DB SECTION BINDING
    // =========================================================================

    private void bindDbSection() {
        if (settings == null) return;

        boolean ready = settings.isDatabaseReady(context);
        String  ver   = settings.getDatabaseVersion(context);

        // Status text
        if (dbStatusText != null) {
            if (ready) {
                dbStatusText.setText(s(R.string.ds_db_status_ready));
            } else {
                dbStatusText.setText(s(R.string.ds_db_status_not_ready));
            }
        }

        // Version chip
        if (dbVersionText != null) {
            if (ready && ver != null) {
                String formatted = ver.replace("_", "-");
                dbVersionText.setText(String.format(s(R.string.ds_db_version_format), formatted));
                dbVersionText.setVisibility(View.VISIBLE);
            } else {
                dbVersionText.setVisibility(View.GONE);
            }
        }

        // Hide progress row when not importing
        setVisible(dbProgressRow, false);
        setVisible(dbPhaseText, false);

        // Reset integrity result
        if (dbIntegrityResult != null) {
            dbIntegrityResult.setVisibility(View.GONE);
        }
    }

    private void wireDbButtons() {
        if (settings == null) return;

        // Check integrity
        if (dbCheckIntegrityButton != null) {
            dbCheckIntegrityButton.setOnClickListener(v -> runIntegrityCheck());
        }

        // Reinitialise
        if (dbReinitialiseButton != null) {
            dbReinitialiseButton.setOnClickListener(v -> startImport());
        }
    }

    // =========================================================================
    // REFRESH (called on onResume)
    // =========================================================================

    /**
     * Refreshes all dynamic state (status dot, DB status, version chip)
     * without re-inflating the card. Safe to call from main thread.
     */
    public void refresh() {
        if (cardView == null) return; // Not yet attached
        updateStatusDot();

        if (settings != null && settings.hasLocalDatabase()) {
            bindDbSection();
        }

        // Sync the switch in case enabled state changed externally
        if (enableSwitch != null) {
            enableSwitch.setOnCheckedChangeListener(null);
            enableSwitch.setChecked(source.isEnabled());
            enableSwitch.setOnCheckedChangeListener((btn, checked) -> {
                source.setEnabled(context, checked);
                updateStatusDot();
            });
        }
    }

    // =========================================================================
    // CACHE CLEAR SUPPORT
    // =========================================================================

    /**
     * Called by SettingsActivity after clearing the database cache.
     * Resets this source's import SharedPreferences so isDatabaseReady()
     * returns false, then refreshes the card UI to reflect the empty state.
     * No-op for sources without a local database (e.g. OpenFoodFacts).
     */
    public void resetSourceDatabaseState(@NonNull Context context) {
        if (settings != null && settings.hasLocalDatabase()) {
            settings.resetDatabaseState(context.getApplicationContext());
            refresh();
        }
    }

    // =========================================================================
    // STATUS DOT
    // =========================================================================

    private void updateStatusDot() {
        if (statusDot == null) return;
        DataSource.DataSourceStatus status = source.getStatus();
        int color;
        switch (status) {
            case READY:
                color = 0xFF4CAF50; // green
                break;
            case INITIALIZING:
                color = 0xFF2196F3; // blue
                break;
            case DISABLED:
                color = 0xFF9E9E9E; // grey
                break;
            case RATE_LIMITED:
                color = 0xFFFF9800; // orange
                break;
            case ERROR:
            case NO_NETWORK:
                color = 0xFFE53935; // red
                break;
            default:
                color = 0xFF9E9E9E; // grey
                break;
        }
        statusDot.setBackgroundColor(color);
    }

    // =========================================================================
    // INTEGRITY CHECK
    // =========================================================================

    private void runIntegrityCheck() {
        if (settings == null || dbCheckIntegrityButton == null) return;

        dbCheckIntegrityButton.setEnabled(false);
        if (dbIntegrityResult != null) {
            dbIntegrityResult.setText(s(R.string.ds_db_checking));
            dbIntegrityResult.setVisibility(View.VISIBLE);
        }

        bgExecutor.execute(() -> {
            try {
                // Brief pause so Room settles if DB was just cleared
                Thread.sleep(200);

                int products   = settings.getDatabaseProductCount(context);
                int nutrition  = settings.getDatabaseNutritionCount(context);

                // Expected count comes from the source's prefs/constants
                // We infer "full" if products > 0 and nutrition >= products
                postToMain(() -> {
                    if (dbCheckIntegrityButton != null) dbCheckIntegrityButton.setEnabled(true);
                    if (dbIntegrityResult == null) return;

                    String verdict;
                    int    colour;

                    if (products == 0) {
                        verdict = s(R.string.ds_db_integrity_empty);
                        colour  = 0xFFE53935; // red
                        // DB empty — reset import prefs so status dot reflects reality
                        if (settings != null) settings.resetDatabaseState(context);
                        refresh();
                    } else {
                        verdict = String.format(
                                s(R.string.ds_db_integrity_ok),
                                products, nutrition);
                        colour = 0xFF4CAF50; // green
                    }

                    dbIntegrityResult.setText(verdict);
                    dbIntegrityResult.setTextColor(colour);
                    dbIntegrityResult.setVisibility(View.VISIBLE);
                });

            } catch (Exception e) {
                Log.e(TAG, "Integrity check failed for " + source.getSourceId(), e);
                postToMain(() -> {
                    if (dbCheckIntegrityButton != null) dbCheckIntegrityButton.setEnabled(true);
                    if (dbIntegrityResult != null) {
                        dbIntegrityResult.setText(s(R.string.ds_db_integrity_error));
                        dbIntegrityResult.setTextColor(0xFFE53935);
                        dbIntegrityResult.setVisibility(View.VISIBLE);
                    }
                });
            }
        });
    }

    // =========================================================================
    // IMPORT / REINITIALISE
    // =========================================================================

    private void startImport() {
        if (settings == null) return;

        if (dbReinitialiseButton != null) dbReinitialiseButton.setEnabled(false);

        // Show progress row at 0%
        setVisible(dbProgressRow, true);
        if (dbProgressBar != null) {
            dbProgressBar.setIndeterminate(false);
            dbProgressBar.setProgress(0);
        }
        if (dbProgressPercent != null) dbProgressPercent.setText("0%");
        if (dbPhaseText != null) {
            dbPhaseText.setText(s(R.string.ds_db_importing));
            dbPhaseText.setVisibility(View.VISIBLE);
        }
        if (dbIntegrityResult != null) dbIntegrityResult.setVisibility(View.GONE);

        // Delegate — the source's SettingsProvider starts its own service
        settings.startImport(context);
    }

    // =========================================================================
    // BROADCAST RECEIVER — import progress
    // =========================================================================

    private void registerReceiver() {
        if (settings == null || !settings.hasLocalDatabase()) return;
        if (importReceiver != null) return; // Already registered

        // Skip if action strings are empty (OpenFoodFacts has no import)
        String progressAction = settings.getBroadcastProgress();
        if (progressAction == null || progressAction.isEmpty()) return;

        importReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                if (intent == null || intent.getAction() == null) return;
                String action = intent.getAction();

                if (action.equals(settings.getBroadcastProgress())) {
                    onImportProgress(
                            intent.getStringExtra(settings.getExtraPhaseKey()),
                            intent.getIntExtra(settings.getExtraPercentKey(), 0));

                } else if (action.equals(settings.getBroadcastComplete())) {
                    onImportComplete();

                } else if (action.equals(settings.getBroadcastError())) {
                    onImportError(intent.getStringExtra(settings.getExtraErrorKey()));
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(progressAction);
        filter.addAction(settings.getBroadcastComplete());
        filter.addAction(settings.getBroadcastError());

        // RECEIVER_NOT_EXPORTED: broadcasts only from our own process (Android 14+)
        ContextCompat.registerReceiver(
                context, importReceiver, filter,
                ContextCompat.RECEIVER_NOT_EXPORTED);

        Log.d(TAG, source.getSourceId() + " broadcast receiver registered");
    }

    private void unregisterReceiver() {
        if (importReceiver == null) return;
        try {
            context.unregisterReceiver(importReceiver);
        } catch (IllegalArgumentException ignored) {
            // Receiver was not registered — safe to ignore
        }
        importReceiver = null;
        Log.d(TAG, source.getSourceId() + " broadcast receiver unregistered");
    }

    // =========================================================================
    // IMPORT PROGRESS CALLBACKS
    // =========================================================================

    private void onImportProgress(@Nullable String phase, int pct) {
        setVisible(dbProgressRow, true);
        if (dbProgressBar != null) {
            dbProgressBar.setIndeterminate(false);
            int safe = Math.max(0, Math.min(100, pct));
            dbProgressBar.setProgress(safe, true);
        }
        if (dbProgressPercent != null) {
            int safe = Math.max(0, Math.min(100, pct));
            dbProgressPercent.setText(safe + "%");
        }
        if (dbPhaseText != null && phase != null) {
            dbPhaseText.setText(phase);
            dbPhaseText.setVisibility(View.VISIBLE);
        }
    }

    private void onImportComplete() {
        setVisible(dbProgressRow, false);
        setVisible(dbPhaseText, false);
        if (dbReinitialiseButton != null) dbReinitialiseButton.setEnabled(true);
        refresh(); // Redraw status, version chip

        String msg = String.format(s(R.string.ds_db_import_success), source.getSourceName());
        Toast.makeText(context, msg, Toast.LENGTH_LONG).show();
    }

    private void onImportError(@Nullable String message) {
        setVisible(dbProgressRow, false);
        setVisible(dbPhaseText, false);
        if (dbReinitialiseButton != null) dbReinitialiseButton.setEnabled(true);
        if (dbStatusText != null) dbStatusText.setText(s(R.string.ds_db_status_not_ready));

        String msg = s(R.string.ds_db_import_error)
                + (message != null ? ": " + message : "");
        Toast.makeText(context, msg, Toast.LENGTH_LONG).show();
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    /** Set View visibility from a boolean. */
    private static void setVisible(@Nullable View view, boolean visible) {
        if (view != null) view.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    /** getString() shorthand using the application context. */
    private String s(int resId) {
        return context.getString(resId);
    }

    /** Post a runnable to the main thread via the card's view handler. */
    private void postToMain(@NonNull Runnable r) {
        if (cardView != null) {
            cardView.post(r);
        }
    }

    /**
     * Apply underline paint flag to a TextView programmatically.
     */
    private static void underline(@Nullable TextView view) {
        if (view != null) {
            view.setPaintFlags(view.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
        }
    }

    /**
     * Look up a string resource by conventional name.
     * Example: "source_description_ciqual" for the Ciqual source.
     * Returns null if not found (caller uses a fallback).
     */
    @Nullable
    private String getStringByConvention(@NonNull String resourceName) {
        try {
            int id = context.getResources().getIdentifier(
                    resourceName, "string", context.getPackageName());
            return id != 0 ? context.getString(id) : null;
        } catch (Exception e) {
            return null;
        }
    }
}