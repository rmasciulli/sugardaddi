package li.masciul.sugardaddi.data.sources.usda;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.google.gson.stream.JsonReader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import li.masciul.sugardaddi.R;
import li.masciul.sugardaddi.core.enums.DataConfidence;
import li.masciul.sugardaddi.core.enums.DataSource;
import li.masciul.sugardaddi.core.models.FoodProduct;
import li.masciul.sugardaddi.core.models.SourceIdentifier;
import li.masciul.sugardaddi.data.database.AppDatabase;
import li.masciul.sugardaddi.data.database.dao.FoodProductDao;
import li.masciul.sugardaddi.data.database.dao.NutritionDao;
import li.masciul.sugardaddi.data.database.entities.FoodProductEntity;
import li.masciul.sugardaddi.data.database.entities.NutritionEntity;
import li.masciul.sugardaddi.data.sources.usda.mappers.USDAMapper;

/**
 * USDAImportService — Imports USDA Foundation Foods and SR Legacy into Room.
 *
 * TRIGGERED BY:
 *   USDASettingsProvider.startImport() — user-initiated from Settings card only.
 *   Never auto-triggered — ~215MB download requires user consent.
 *
 * PIPELINE:
 *   Phase 0: Clear USDA rows from food_products + nutrition tables
 *   Phase 1: Download + parse Foundation Foods JSON (~6.5MB unzipped, ~1200 foods)
 *   Phase 2: Download + parse SR Legacy JSON (~205MB unzipped, ~7700 foods)
 *   Phase 3: Mark import complete in SharedPreferences
 *
 * FILE RESOLUTION:
 *   Each ZIP is downloaded directly from fdc.nal.usda.gov.
 *   Unlike Ciqual, no asset bundling — files are too large for the APK.
 *   The JSON is stream-parsed (Gson JsonReader) to avoid OOM on SR Legacy.
 *
 * NO NEW TABLES:
 *   USDA foods   → food_products (FoodProductEntity, sourceId = "USDA")
 *   USDA nutrition → nutrition  (NutritionEntity,   sourceType = "product")
 *
 * DATA TYPES IMPORTED:
 *   Foundation Foods (~1200): highest quality raw agricultural commodities.
 *   SR Legacy (~7700):        classic USDA Standard Reference, broad coverage.
 *   Survey (FNDDS) excluded: marginal value, 64MB, US dietary survey data.
 *
 * ANNUAL UPDATE:
 *   Update USDAConstants.DATASET_VERSION and *_JSON_URL constants.
 *   Users will see an "update available" badge in the settings card.
 */
public class USDAImportService extends Service {

    private static final String TAG = "USDAImportService";

    private ExecutorService executor;
    private volatile boolean running = false;

    // ===== LIFECYCLE =====

    @Override
    public void onCreate() {
        super.onCreate();
        executor = Executors.newSingleThreadExecutor();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        if (running) {
            Log.w(TAG, "Import already running — ignoring duplicate start");
            return START_NOT_STICKY;
        }

        running = true;
        startForeground(USDAConstants.NOTIF_ID, buildNotification("Preparing…", 0));

        executor.execute(() -> {
            try {
                runImport();
                broadcastComplete();
                writePrefs(true);
            } catch (Exception e) {
                Log.e(TAG, "USDA import failed", e);
                broadcastError(e.getMessage() != null ? e.getMessage() : "Unknown error");
                writePrefs(false);
            } finally {
                running = false;
                stopForeground(STOP_FOREGROUND_REMOVE);
                stopSelf();
            }
        });

        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (executor != null) executor.shutdownNow();
    }

    @Nullable
    @Override
    public IBinder onBind(@Nullable Intent intent) {
        return null;
    }

    // ===== PIPELINE =====

    private void runImport() throws Exception {
        AppDatabase db = AppDatabase.getInstance(getApplicationContext());

        // Phase 0: Clear previous USDA data
        broadcastProgress("Clearing previous data…", 0);
        db.runInTransaction(() -> {
            db.foodProductDao().deleteProductsBySource(USDAConstants.SOURCE_ID);
            db.nutritionDao().deleteNutritionByDataSource(USDAConstants.SOURCE_ID);
        });
        Log.i(TAG, "Phase 0 complete: cleared previous USDA rows");

        // Phase 1: Foundation Foods (~1200 foods, ~6.5MB unzipped)
        broadcastProgress("Downloading Foundation Foods…", 5);
        int foundationCount = importFoundationFoods(db);
        Log.i(TAG, "Phase 1 complete: " + foundationCount + " Foundation foods imported");

        // Phase 2: SR Legacy (~7700 foods, ~205MB unzipped)
        broadcastProgress("Downloading SR Legacy…", 40);
        int srCount = importSrLegacy(db);
        Log.i(TAG, "Phase 2 complete: " + srCount + " SR Legacy foods imported");

        broadcastProgress("Done.", 100);
        Log.i(TAG, "USDA import complete: " + (foundationCount + srCount) + " foods total");
    }

    // ===== FOUNDATION FOODS IMPORT =====

    /**
     * Download and parse the Foundation Foods JSON ZIP.
     *
     * Foundation Foods structure (abbreviated):
     * {
     *   "FoundationFoods": [
     *     {
     *       "fdcId": 747447,
     *       "description": "Broccoli, raw",
     *       "foodCategory": { "description": "Vegetables and Vegetable Products" },
     *       "foodNutrients": [
     *         { "nutrient": { "id": 1008, "name": "Energy", "unitName": "kcal" }, "amount": 34.0 },
     *         ...
     *       ]
     *     },
     *     ...
     *   ]
     * }
     */
    private int importFoundationFoods(@NonNull AppDatabase db) throws IOException {
        broadcastProgress("Downloading Foundation Foods…", 5);
        try (InputStream zipStream = openDownload(USDAConstants.FOUNDATION_JSON_URL)) {
            return parseFoundationZip(zipStream, db);
        }
    }

    private int parseFoundationZip(@NonNull InputStream zipStream,
                                   @NonNull AppDatabase db) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(zipStream)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName().toLowerCase();
                if (name.endsWith(".json") && name.contains("foundation")) {
                    return parseFoundationJson(zis, db);
                }
                zis.closeEntry();
            }
        }
        throw new IOException("Foundation Foods JSON not found inside ZIP");
    }

    private int parseFoundationJson(@NonNull InputStream jsonStream,
                                    @NonNull AppDatabase db) throws IOException {
        int count = 0;
        List<FoodProductEntity> productBatch = new ArrayList<>();
        List<NutritionEntity>   nutritionBatch = new ArrayList<>();

        JsonReader reader = new JsonReader(
                new BufferedReader(new InputStreamReader(jsonStream, StandardCharsets.UTF_8)));

        // Navigate to the "FoundationFoods" array
        reader.beginObject();
        while (reader.hasNext()) {
            String topKey = reader.nextName();
            if ("FoundationFoods".equals(topKey)) {
                reader.beginArray();
                while (reader.hasNext()) {
                    parseSingleFood(reader, productBatch, nutritionBatch, "Foundation");
                    count++;

                    if (productBatch.size() >= USDAConstants.IMPORT_BATCH_SIZE) {
                        flushBatch(db, productBatch, nutritionBatch);
                        int pct = 5 + (count / 12); // 1200 foods → 5%–15%
                        broadcastProgress("Importing Foundation Foods… (" + count + ")",
                                Math.min(pct, 38));
                    }
                }
                reader.endArray();
            } else {
                reader.skipValue();
            }
        }
        reader.endObject();
        reader.close();

        // Flush remaining
        if (!productBatch.isEmpty()) flushBatch(db, productBatch, nutritionBatch);

        return count;
    }

    // ===== SR LEGACY IMPORT =====

    /**
     * Download and parse the SR Legacy JSON ZIP.
     *
     * SR Legacy structure:
     * {
     *   "SRLegacyFoods": [
     *     {
     *       "fdcId": 173944,
     *       "description": "Apples, raw, with skin",
     *       "foodCategory": { "description": "Fruits and Fruit Juices" },
     *       "foodNutrients": [ ... ]
     *     },
     *     ...
     *   ]
     * }
     */
    private int importSrLegacy(@NonNull AppDatabase db) throws IOException {
        broadcastProgress("Downloading SR Legacy…", 40);
        try (InputStream zipStream = openDownload(USDAConstants.SR_LEGACY_JSON_URL)) {
            return parseSrLegacyZip(zipStream, db);
        }
    }

    private int parseSrLegacyZip(@NonNull InputStream zipStream,
                                 @NonNull AppDatabase db) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(zipStream)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName().toLowerCase();
                if (name.endsWith(".json") && (name.contains("sr") || name.contains("legacy"))) {
                    return parseSrLegacyJson(zis, db);
                }
                zis.closeEntry();
            }
        }
        throw new IOException("SR Legacy JSON not found inside ZIP");
    }

    private int parseSrLegacyJson(@NonNull InputStream jsonStream,
                                  @NonNull AppDatabase db) throws IOException {
        int count = 0;
        List<FoodProductEntity> productBatch  = new ArrayList<>();
        List<NutritionEntity>   nutritionBatch = new ArrayList<>();

        JsonReader reader = new JsonReader(
                new BufferedReader(new InputStreamReader(jsonStream, StandardCharsets.UTF_8)));

        reader.beginObject();
        while (reader.hasNext()) {
            String topKey = reader.nextName();
            if ("SRLegacyFoods".equals(topKey)) {
                reader.beginArray();
                while (reader.hasNext()) {
                    parseSingleFood(reader, productBatch, nutritionBatch, "SR Legacy");
                    count++;

                    if (productBatch.size() >= USDAConstants.IMPORT_BATCH_SIZE) {
                        flushBatch(db, productBatch, nutritionBatch);
                        // 7700 foods: progress 40%–95%
                        int pct = 40 + (count * 55 / 7700);
                        broadcastProgress("Importing SR Legacy… (" + count + ")",
                                Math.min(pct, 95));
                    }
                }
                reader.endArray();
            } else {
                reader.skipValue();
            }
        }
        reader.endObject();
        reader.close();

        if (!productBatch.isEmpty()) flushBatch(db, productBatch, nutritionBatch);

        return count;
    }

    // ===== SINGLE FOOD PARSER =====

    /**
     * Stream-parse one food object from a JsonReader positioned at the start
     * of a food object. Populates productBatch and nutritionBatch.
     *
     * This method avoids loading the full JSON into memory — critical for the
     * 205MB SR Legacy file on devices with limited RAM.
     */
    private void parseSingleFood(
            @NonNull JsonReader reader,
            @NonNull List<FoodProductEntity> productBatch,
            @NonNull List<NutritionEntity>   nutritionBatch,
            @NonNull String dataType) throws IOException {

        int fdcId = 0;
        String description   = null;
        String categoryDesc  = null;
        // Nutrient id → amount
        Map<Integer, Double> nutrients = new HashMap<>();

        reader.beginObject();
        while (reader.hasNext()) {
            String key = reader.nextName();
            switch (key) {
                case "fdcId":
                    fdcId = reader.nextInt();
                    break;
                case "description":
                    description = reader.nextString();
                    break;
                case "foodCategory":
                    categoryDesc = parseFoodCategory(reader);
                    break;
                case "foodNutrients":
                    parseFoodNutrients(reader, nutrients);
                    break;
                default:
                    reader.skipValue();
                    break;
            }
        }
        reader.endObject();

        // Skip foods with no description
        if (fdcId == 0 || description == null || description.trim().isEmpty()) return;

        // ── Build FoodProductEntity ────────────────────────────────────────
        String entityId    = "USDA:" + fdcId;
        String displayName = USDAMapper.toSentenceCase(description.trim());

        FoodProductEntity entity = new FoodProductEntity();
        entity.setId(entityId);
        entity.setSourceId(USDAConstants.SOURCE_ID);
        entity.setOriginalId(String.valueOf(fdcId));
        entity.setName(displayName);
        entity.setCurrentLanguage("en");
        if (categoryDesc != null) {
            entity.setCategoriesText(USDAMapper.toSentenceCase(categoryDesc));
        }

        // Set searchable text for Room LIKE queries
        String searchable = displayName.toLowerCase();
        if (categoryDesc != null) searchable += " " + categoryDesc.toLowerCase();
        entity.setSearchableText(searchable);

        entity.setCreatedAt(System.currentTimeMillis());
        entity.setUpdatedAt(System.currentTimeMillis());

        productBatch.add(entity);

        // ── Build NutritionEntity from the nutrient map ────────────────────
        if (!nutrients.isEmpty()) {
            li.masciul.sugardaddi.core.models.Nutrition nutrition =
                    new li.masciul.sugardaddi.core.models.Nutrition();

            for (Map.Entry<Integer, Double> e : nutrients.entrySet()) {
                USDAMapper.mapNutrientById(nutrition, e.getKey(), e.getValue());
            }

            // Derive salt from sodium if not directly provided (sodium mg → salt g)
            if (nutrition.getSalt() == null && nutrition.getSodium() != null) {
                nutrition.setSalt(nutrition.getSodium() * 2.5);
            }

            // Set confidence before creating the entity
            nutrition.setDataConfidence(DataConfidence.SCIENTIFIC);
            NutritionEntity nutritionEntity = NutritionEntity.fromNutrition(
                    nutrition, "product", entityId);
            nutritionEntity.setDataSource(USDAConstants.SOURCE_ID);
            nutritionBatch.add(nutritionEntity);
        }
    }

    /**
     * Parse a foodCategory object: { "description": "Vegetables..." }
     * Returns the category description string, or null if absent.
     */
    @Nullable
    private String parseFoodCategory(@NonNull JsonReader reader) throws IOException {
        String desc = null;
        reader.beginObject();
        while (reader.hasNext()) {
            String key = reader.nextName();
            if ("description".equals(key)) {
                desc = reader.nextString();
            } else {
                reader.skipValue();
            }
        }
        reader.endObject();
        return desc;
    }

    /**
     * Parse the foodNutrients array into a nutrientId → amount map.
     * Handles both Foundation format (nested "nutrient" object) and any flat variants.
     *
     * Foundation structure:
     * { "nutrient": { "id": 1008, "name": "Energy", "unitName": "kcal" }, "amount": 34.0 }
     */
    private void parseFoodNutrients(@NonNull JsonReader reader,
                                    @NonNull Map<Integer, Double> out) throws IOException {
        reader.beginArray();
        while (reader.hasNext()) {
            int    nutrientId = 0;
            double amount     = 0.0;

            reader.beginObject();
            while (reader.hasNext()) {
                String key = reader.nextName();
                switch (key) {
                    case "nutrient":
                        // Nested nutrient metadata object
                        reader.beginObject();
                        while (reader.hasNext()) {
                            String nKey = reader.nextName();
                            if ("id".equals(nKey)) {
                                nutrientId = reader.nextInt();
                            } else {
                                reader.skipValue();
                            }
                        }
                        reader.endObject();
                        break;
                    case "amount":
                        // Guard against non-numeric values (rare but possible)
                        try { amount = reader.nextDouble(); }
                        catch (Exception e) { reader.skipValue(); }
                        break;
                    default:
                        reader.skipValue();
                        break;
                }
            }
            reader.endObject();

            if (nutrientId > 0 && amount > 0) {
                out.put(nutrientId, amount);
            }
        }
        reader.endArray();
    }

    // ===== BATCH DB WRITE =====

    private void flushBatch(@NonNull AppDatabase db,
                            @NonNull List<FoodProductEntity> products,
                            @NonNull List<NutritionEntity>   nutrition) {
        db.runInTransaction(() -> {
            db.foodProductDao().insertProducts(products);
            if (!nutrition.isEmpty()) {
                db.nutritionDao().insertNutritionBatch(nutrition);
            }
        });
        products.clear();
        nutrition.clear();
    }

    // ===== DOWNLOAD =====

    /**
     * Open an HTTP stream to a USDA download URL.
     * Follows redirects (USDA may redirect to a CDN).
     * Uses long timeouts — SR Legacy is 205MB.
     */
    @NonNull
    private InputStream openDownload(@NonNull String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(USDAConstants.CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(USDAConstants.DOWNLOAD_TIMEOUT_MS);
        conn.setRequestProperty("User-Agent", USDAConstants.USER_AGENT);
        conn.setInstanceFollowRedirects(true);

        int code = conn.getResponseCode();
        if (code != HttpURLConnection.HTTP_OK) {
            throw new IOException("HTTP " + code + " downloading: " + url);
        }

        Log.i(TAG, "Download started: " + url
                + " (" + conn.getContentLength() / 1024 + " KB)");
        return conn.getInputStream();
    }

    // ===== BROADCASTS =====

    private void broadcastProgress(@NonNull String phase, int pct) {
        Intent i = new Intent(USDAConstants.BROADCAST_PROGRESS);
        i.setPackage(getPackageName()); // required for RECEIVER_NOT_EXPORTED on API 34+
        i.putExtra(USDAConstants.EXTRA_PHASE, phase);
        if (pct >= 0) i.putExtra(USDAConstants.EXTRA_PROGRESS_PCT, Math.min(100, pct));
        sendBroadcast(i);
        updateNotification(phase, Math.max(0, pct));
        Log.d(TAG, (pct >= 0 ? "[" + pct + "%] " : "[dl] ") + phase);
    }

    private void broadcastComplete() {
        Intent i = new Intent(USDAConstants.BROADCAST_COMPLETE);
        i.setPackage(getPackageName());
        sendBroadcast(i);
    }

    private void broadcastError(@NonNull String msg) {
        Intent i = new Intent(USDAConstants.BROADCAST_ERROR);
        i.setPackage(getPackageName());
        i.putExtra(USDAConstants.EXTRA_ERROR_MSG, msg);
        sendBroadcast(i);
    }

    // ===== PREFS =====

    private void writePrefs(boolean success) {
        SharedPreferences.Editor ed =
                getSharedPreferences(USDAConstants.PREFS_NAME, MODE_PRIVATE).edit();
        ed.putBoolean(USDAConstants.PREF_DB_READY, success);
        if (success) {
            ed.putString(USDAConstants.PREF_IMPORT_VERSION, USDAConstants.DATASET_VERSION);
            ed.putLong(USDAConstants.PREF_IMPORT_DATE, System.currentTimeMillis());
        }
        ed.apply();
    }

    /** True if the DB has been imported AND the version matches USDAConstants.DATASET_VERSION. */
    public static boolean isImported(@NonNull Context ctx) {
        SharedPreferences prefs =
                ctx.getSharedPreferences(USDAConstants.PREFS_NAME, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(USDAConstants.PREF_DB_READY, false)) return false;
        String stored = prefs.getString(USDAConstants.PREF_IMPORT_VERSION, null);
        return USDAConstants.DATASET_VERSION.equals(stored);
    }

    /** True if the locally stored version differs from the current dataset version. */
    public static boolean needsUpdate(@NonNull Context ctx) {
        SharedPreferences prefs =
                ctx.getSharedPreferences(USDAConstants.PREFS_NAME, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(USDAConstants.PREF_DB_READY, false)) return true;
        String stored = prefs.getString(USDAConstants.PREF_IMPORT_VERSION, null);
        return !USDAConstants.DATASET_VERSION.equals(stored);
    }

    @Nullable
    public static String getImportedVersion(@NonNull Context ctx) {
        return ctx.getSharedPreferences(USDAConstants.PREFS_NAME, Context.MODE_PRIVATE)
                .getString(USDAConstants.PREF_IMPORT_VERSION, null);
    }

    // ===== NOTIFICATION =====

    private void createNotificationChannel() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) {
            nm.createNotificationChannel(new NotificationChannel(
                    USDAConstants.NOTIF_CHANNEL_ID,
                    "USDA Import",
                    NotificationManager.IMPORTANCE_LOW));
        }
    }

    @NonNull
    private android.app.Notification buildNotification(@NonNull String text, int pct) {
        return new NotificationCompat.Builder(this, USDAConstants.NOTIF_CHANNEL_ID)
                .setContentTitle("Importing USDA database")
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setProgress(100, pct, pct == 0)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build();
    }

    private void updateNotification(@NonNull String text, int pct) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(USDAConstants.NOTIF_ID, buildNotification(text, pct));
    }
}