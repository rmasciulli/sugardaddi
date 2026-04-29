package li.masciul.sugardaddi.data.sources.ciqual;

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

import li.masciul.sugardaddi.R;
import li.masciul.sugardaddi.core.enums.DataConfidence;
import li.masciul.sugardaddi.core.models.Nutrition;
import li.masciul.sugardaddi.core.models.ProductTranslation;
import li.masciul.sugardaddi.data.database.AppDatabase;
import li.masciul.sugardaddi.data.database.dao.FoodProductDao;
import li.masciul.sugardaddi.data.database.dao.NutritionDao;
import li.masciul.sugardaddi.data.database.entities.FoodProductEntity;
import li.masciul.sugardaddi.data.database.entities.NutritionEntity;
import li.masciul.sugardaddi.data.sources.ciqual.xml.CiqualCategoryLookup;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * CiqualImportService — Imports the full Ciqual dataset into the local Room database.
 *
 * NO NEW TABLES. Uses existing data model:
 *   Ciqual foods     → food_products  (FoodProductEntity, sourceId = "CIQUAL")
 *   Ciqual nutrition → nutrition      (NutritionEntity,   sourceType = "product")
 *
 * FILE RESOLUTION STRATEGY (per file):
 *   1. Check assets/ for the versioned filename (e.g. alim_2025_11_03.xml)
 *      → If present: open directly. No network needed.
 *   2. If not in assets: download from Zenodo using the URL in CiqualConstants.
 *      → Zenodo provides stable, versioned, DOI-linked downloads.
 *
 * This means a full APK with all three files bundled needs zero network access.
 * A Play Store build without bundled compo.xml (~69MB) downloads it on first launch.
 *
 * ANNUAL UPDATE:
 *   Update CiqualConstants.DATASET_VERSION + ZENODO_RECORD_ID.
 *   Replace asset files. App detects version mismatch on next launch and re-imports.
 *
 * PIPELINE:
 *   Phase 0: Clear CIQUAL rows from food_products + nutrition
 *   Phase 1: alim_2025_11_03.xml → FoodProductEntity (3484 foods)
 *   Phase 2: compo_2025_11_03.xml → Nutrition accumulator → NutritionEntity (69MB streamed)
 *
 * TRIGGERED BY:
 *   CiqualDataSource.initialize() — automatically on first launch or version change.
 *   SettingsActivity — manually by the user (force re-import / update).
 */
public class CiqualImportService extends Service {

    private static final String TAG = "CiqualImportService";

    // ===== BROADCASTS =====
    public static final String BROADCAST_PROGRESS  = "li.masciul.sugardaddi.ciqual.PROGRESS";
    public static final String BROADCAST_COMPLETE  = "li.masciul.sugardaddi.ciqual.COMPLETE";
    public static final String BROADCAST_ERROR     = "li.masciul.sugardaddi.ciqual.ERROR";
    public static final String EXTRA_PHASE         = "phase";
    public static final String EXTRA_PROGRESS_PCT  = "progress_pct";
    public static final String EXTRA_ERROR_MSG     = "error_msg";

    // ===== PREFS =====
    public static final String PREFS_NAME          = "ciqual_import";
    public static final String PREF_DB_READY       = "db_ready";
    public static final String PREF_IMPORT_VERSION = "import_version";
    public static final String PREF_IMPORT_DATE    = "import_date";

    // ===== TUNING =====
    private static final int BATCH_SIZE         = 200;
    private static final int CONNECT_TIMEOUT_MS = 30_000;
    /** 3 minutes — compo.xml is 69MB on variable connections */
    private static final int READ_TIMEOUT_MS    = 180_000;

    // ===== NOTIFICATION =====
    private static final String NOTIF_CHANNEL_ID = "ciqual_import";
    private static final int    NOTIF_ID         = 7001;

    /**
     * Maps Ciqual const_code integers to Nutrition setter keys.
     * Const codes are stable INFOODS/Ciqual identifiers — more reliable than name matching.
     *
     * VITAMIN D: 52100=total D, 52200=D2 (ergocalciferol), 52300=D3 (cholecalciferol)
     * FOLATE:    56600=total DFE, 56610=intrinsic (food matrix), 56620=folic acid (fortification)
     * ENERGY:    327=kJ (EU 1169), 328=kcal (EU 1169) — Jones-factor variants skipped
     *
     * Verify codes against assets/const_2025_11_03.xml if a nutrient appears wrong after import.
     */
    private static final Map<Integer, String> CONST_CODE_MAP = new HashMap<>();
    static {
        CONST_CODE_MAP.put(327,   "energy_kj");
        CONST_CODE_MAP.put(328,   "energy_kcal");
        CONST_CODE_MAP.put(25000, "proteins");
        CONST_CODE_MAP.put(31000, "carbohydrates");
        CONST_CODE_MAP.put(40000, "fat");
        CONST_CODE_MAP.put(34100, "fiber");
        CONST_CODE_MAP.put(32000, "sugars");
        CONST_CODE_MAP.put(33110, "starch");
        CONST_CODE_MAP.put(75100, "polyols");
        CONST_CODE_MAP.put(10400, "alcohol");
        CONST_CODE_MAP.put(400,   "water");
        CONST_CODE_MAP.put(1320,  "ash");
        CONST_CODE_MAP.put(32110, "glucose");
        CONST_CODE_MAP.put(32120, "fructose");
        CONST_CODE_MAP.put(32210, "sucrose");
        CONST_CODE_MAP.put(32130, "lactose");
        CONST_CODE_MAP.put(32140, "maltose");
        CONST_CODE_MAP.put(40302, "saturated_fat");
        CONST_CODE_MAP.put(40303, "monounsaturated_fat");
        CONST_CODE_MAP.put(40304, "polyunsaturated_fat");
        CONST_CODE_MAP.put(75200, "cholesterol");
        CONST_CODE_MAP.put(40401, "butyric_acid");
        CONST_CODE_MAP.put(40402, "caproic_acid");
        CONST_CODE_MAP.put(40403, "caprylic_acid");
        CONST_CODE_MAP.put(40404, "capric_acid");
        CONST_CODE_MAP.put(40405, "lauric_acid");
        CONST_CODE_MAP.put(40406, "myristic_acid");
        CONST_CODE_MAP.put(40407, "palmitic_acid");
        CONST_CODE_MAP.put(40408, "stearic_acid");
        CONST_CODE_MAP.put(40501, "linoleic_acid");
        CONST_CODE_MAP.put(40506, "ala");
        CONST_CODE_MAP.put(40508, "epa");
        CONST_CODE_MAP.put(40510, "dha");
        CONST_CODE_MAP.put(10110, "sodium");
        CONST_CODE_MAP.put(10100, "salt");
        CONST_CODE_MAP.put(10120, "calcium");
        CONST_CODE_MAP.put(10190, "magnesium");
        CONST_CODE_MAP.put(10200, "phosphorus");
        CONST_CODE_MAP.put(10150, "potassium");
        CONST_CODE_MAP.put(10260, "iron");
        CONST_CODE_MAP.put(10300, "zinc");
        CONST_CODE_MAP.put(10230, "copper");
        CONST_CODE_MAP.put(10210, "manganese");
        CONST_CODE_MAP.put(10320, "selenium");
        CONST_CODE_MAP.put(10170, "iodine");
        CONST_CODE_MAP.put(10340, "chloride");
        CONST_CODE_MAP.put(51200, "vitamin_a");
        CONST_CODE_MAP.put(51100, "retinol");
        CONST_CODE_MAP.put(51300, "beta_carotene");
        CONST_CODE_MAP.put(52100, "vitamin_d");
        CONST_CODE_MAP.put(52200, "vitamin_d2");
        CONST_CODE_MAP.put(52300, "vitamin_d3");
        CONST_CODE_MAP.put(53100, "vitamin_e");
        CONST_CODE_MAP.put(54100, "vitamin_k1");
        CONST_CODE_MAP.put(55100, "vitamin_c");
        CONST_CODE_MAP.put(56100, "vitamin_b1");
        CONST_CODE_MAP.put(56200, "vitamin_b2");
        CONST_CODE_MAP.put(56310, "vitamin_b3");
        CONST_CODE_MAP.put(56400, "vitamin_b5");
        CONST_CODE_MAP.put(56500, "vitamin_b6");
        CONST_CODE_MAP.put(56700, "vitamin_b12");
        CONST_CODE_MAP.put(56600, "vitamin_b9");
        CONST_CODE_MAP.put(56610, "intrinsic_folate");
        CONST_CODE_MAP.put(56620, "folic_acid");
        CONST_CODE_MAP.put(10000, "organic_acids");
    }

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
            Log.w(TAG, "Import already running — ignoring");
            return START_NOT_STICKY;
        }
        running = true;
        startForeground(NOTIF_ID, buildNotification("Preparing...", 0));
        executor.execute(() -> {
            try {
                runImport();
                broadcastComplete();
                writePrefs(true);
            } catch (Exception e) {
                Log.e(TAG, "Import failed", e);
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

    @Override public void onDestroy() { super.onDestroy(); if (executor != null) executor.shutdownNow(); }
    @Nullable @Override public IBinder onBind(@Nullable Intent intent) { return null; }

    // ===== PIPELINE =====

    private void runImport() throws Exception {
        AppDatabase db = AppDatabase.getInstance(getApplicationContext());

        // Category lookup — always from bundled asset (alim_grp is always bundled)
        CiqualCategoryLookup lookup = CiqualCategoryLookup.getInstance();
        if (!lookup.isReady()) lookup.parseFromAssets(getApplicationContext());

        broadcastProgress("Clearing previous data...", 0);
        db.runInTransaction(() -> {
            db.foodProductDao().deleteProductsBySource(CiqualConstants.SOURCE_ID);
            db.nutritionDao().deleteNutritionByDataSource(CiqualConstants.SOURCE_ID);
        });

        broadcastProgress("Loading foods...", 5);
        Map<Integer, String> codeToEntityId = importFoods(db.foodProductDao(), lookup);
        Log.i(TAG, "Phase 1 complete: " + codeToEntityId.size() + " foods");

        broadcastProgress("Loading nutrition data...", 25);
        importNutrition(db.nutritionDao(), codeToEntityId);
        Log.i(TAG, "Phase 2 complete");

        broadcastProgress("Done.", 100);
    }

    // ===== FILE RESOLUTION =====

    /**
     * Open a dataset file — from assets/ if present, download from Zenodo otherwise.
     *
     * @param assetFilename  Versioned filename, e.g. "alim_2025_11_03.xml"
     * @param zenodoUrl      Zenodo content URL to use if asset is absent
     * @param description    Human-readable name for log/progress messages
     * @return InputStream positioned at the start of the file
     * @throws IOException if neither source is reachable
     */
    @NonNull
    private InputStream openFile(@NonNull String assetFilename,
                                 @NonNull String zenodoUrl,
                                 @NonNull String description) throws IOException {
        // Try assets/ first — zero network cost, instant
        try {
            InputStream is = getAssets().open(assetFilename);
            Log.i(TAG, description + ": opened from assets/" + assetFilename);
            return is;
        } catch (IOException assetMiss) {
            // File not bundled — download from Zenodo
            Log.i(TAG, description + ": not in assets, downloading from Zenodo: " + zenodoUrl);
            broadcastProgress("Downloading " + description + " from Zenodo...", -1);
            return openDownload(zenodoUrl);
        }
    }

    /**
     * Open an HTTP stream to a Zenodo content URL.
     * Follows redirects (Zenodo uses CDN redirects), sets a generous read timeout for large files.
     */
    @NonNull
    private InputStream openDownload(@NonNull String urlStr) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
        c.setRequestMethod("GET");
        c.setConnectTimeout(CONNECT_TIMEOUT_MS);
        c.setReadTimeout(READ_TIMEOUT_MS);
        c.setRequestProperty("User-Agent", "SugarDaddi/1.0 (Ciqual import)");
        c.setInstanceFollowRedirects(true);
        int code = c.getResponseCode();
        if (code != HttpURLConnection.HTTP_OK) {
            c.disconnect();
            throw new IOException("HTTP " + code + " from " + urlStr);
        }
        return c.getInputStream();
    }

    // ===== PHASE 1: alim.xml → food_products =====

    @NonNull
    private Map<Integer, String> importFoods(@NonNull FoodProductDao dao,
                                             @NonNull CiqualCategoryLookup lookup) throws Exception {
        Map<Integer, String> codeToEntityId = new HashMap<>(4000);
        List<FoodProductEntity> batch = new ArrayList<>(BATCH_SIZE);
        int total = 0;
        long now = System.currentTimeMillis();

        try (InputStream is = openFile(
                CiqualConstants.ASSET_ALIM,
                CiqualConstants.URL_ALIM,
                "foods list")) {
            XmlPullParser p = parser(is);
            int code = 0;
            String nameFr = null, nameEn = null, nameSci = null;
            String grpCode = null, ssgrpCode = null, ssssgrpCode = null;
            String tag = null;
            boolean inAlim = false;

            int ev = p.getEventType();
            while (ev != XmlPullParser.END_DOCUMENT) {
                switch (ev) {
                    case XmlPullParser.START_TAG:
                        tag = p.getName();
                        if ("ALIM".equals(tag)) {
                            code = 0; nameFr = nameEn = nameSci = null;
                            grpCode = ssgrpCode = ssssgrpCode = null;
                            inAlim = true;
                        }
                        break;
                    case XmlPullParser.TEXT:
                        if (!inAlim || tag == null) break;
                        String t = p.getText().trim();
                        switch (tag) {
                            case "alim_code":         code = intSafe(t);  break;
                            case "alim_nom_fr":        nameFr = t;         break;
                            case "alim_nom_eng":       nameEn = t;         break;
                            case "alim_nom_sci":       nameSci = blank(t); break;
                            case "alim_grp_code":      grpCode = t;        break;
                            case "alim_ssgrp_code":    ssgrpCode = t;      break;
                            case "alim_ssssgrp_code":  ssssgrpCode = t;    break;
                        }
                        break;
                    case XmlPullParser.END_TAG:
                        if ("ALIM".equals(p.getName()) && inAlim && code > 0) {
                            inAlim = false;
                            String catCode = bestCode(grpCode, ssgrpCode, ssssgrpCode);
                            String catEn = lookup.getCategoryHierarchyFromCodes(grpCode, ssgrpCode, ssssgrpCode, "en");
                            String catFr = lookup.getCategoryHierarchyFromCodes(grpCode, ssgrpCode, ssssgrpCode, "fr");
                            String entityId = CiqualConstants.SOURCE_ID + ":" + code;
                            codeToEntityId.put(code, entityId);
                            batch.add(buildFood(code, entityId, nameFr, nameEn, nameSci, catCode, catEn, catFr, now));
                            if (batch.size() >= BATCH_SIZE) {
                                dao.insertProducts(batch); total += batch.size(); batch.clear();
                                broadcastProgress("Importing foods... (" + total + ")", 5 + Math.min(18, total * 18 / 3484));
                            }
                        }
                        tag = null;
                        break;
                }
                ev = p.next();
            }
        }
        if (!batch.isEmpty()) { dao.insertProducts(batch); total += batch.size(); }
        return codeToEntityId;
    }

    @NonNull
    private FoodProductEntity buildFood(int code, @NonNull String entityId,
                                        @Nullable String nameFr, @Nullable String nameEn, @Nullable String nameSci,
                                        @Nullable String catCode, @Nullable String catEn, @Nullable String catFr, long now) {
        FoodProductEntity e = new FoodProductEntity();
        e.setId(entityId);
        e.setOriginalId(String.valueOf(code));
        e.setSourceId(CiqualConstants.SOURCE_ID);
        boolean hasEn = ok(nameEn);
        if (hasEn) { e.setCurrentLanguage("en"); e.setName(nameEn); e.setCategoriesText(ok(catEn) ? catEn : ""); e.setNeedsDefaultLanguageUpdate(false); }
        else        { e.setCurrentLanguage("fr"); e.setName(ok(nameFr) ? nameFr : ""); e.setCategoriesText(ok(catFr) ? catFr : ""); e.setNeedsDefaultLanguageUpdate(true); }
        e.setScientificName(nameSci);
        e.setCategoryCode(catCode);
        Map<String, ProductTranslation> tr = new HashMap<>(2);
        if (hasEn && ok(nameFr)) { ProductTranslation fr = new ProductTranslation(nameFr, null, null); if (ok(catFr)) fr.setCategories(catFr); tr.put("fr", fr); }
        else if (!hasEn && ok(nameEn)) { ProductTranslation en = new ProductTranslation(nameEn, null, null); if (ok(catEn)) en.setCategories(catEn); tr.put("en", en); }
        e.setTranslations(tr);
        StringBuilder sb = new StringBuilder();
        if (ok(nameFr)) sb.append(nameFr.toLowerCase()).append(' ');
        if (ok(nameEn)) sb.append(nameEn.toLowerCase()).append(' ');
        if (ok(catEn))  sb.append(catEn.toLowerCase()).append(' ');
        if (ok(catFr))  sb.append(catFr.toLowerCase());
        e.setSearchableText(sb.toString().trim());
        e.setCreatedAt(now); e.setLastUpdated(now); e.setUpdatedAt(now); e.setDataCompleteness(0.0f);
        return e;
    }

    // ===== PHASE 2: compo.xml → nutrition (in-memory accumulation) =====

    private void importNutrition(@NonNull NutritionDao dao,
                                 @NonNull Map<Integer, String> codeToEntityId) throws Exception {
        Map<Integer, Nutrition> accumulator       = new HashMap<>(4000);
        Map<Integer, Map<String, Integer>> conf   = new HashMap<>(4000);
        int rows = 0;

        try (InputStream is = openFile(
                CiqualConstants.ASSET_COMPO,
                CiqualConstants.URL_COMPO,
                "nutrition data")) {
            XmlPullParser p = parser(is);
            int alimentCode = 0, constCode = 0;
            String value = null, confidence = null;
            String tag = null;
            boolean inCompo = false;

            int ev = p.getEventType();
            while (ev != XmlPullParser.END_DOCUMENT) {
                switch (ev) {
                    case XmlPullParser.START_TAG:
                        tag = p.getName();
                        if ("COMPO".equals(tag)) { alimentCode = constCode = 0; value = confidence = null; inCompo = true; }
                        break;
                    case XmlPullParser.TEXT:
                        if (!inCompo || tag == null) break;
                        String t = p.getText().trim();
                        switch (tag) {
                            case "alim_code":      alimentCode = intSafe(t); break;
                            case "const_code":     constCode   = intSafe(t); break;
                            case "teneur":         value       = dash(t);    break;
                            case "code_confiance": confidence  = blank(t);   break;
                        }
                        break;
                    case XmlPullParser.END_TAG:
                        if ("COMPO".equals(p.getName()) && inCompo) {
                            inCompo = false;
                            String key = CONST_CODE_MAP.get(constCode);
                            if (key != null && value != null && codeToEntityId.containsKey(alimentCode)) {
                                Double v = doubleSafe(value);
                                if (v != null) {
                                    applyValue(accumulator.computeIfAbsent(alimentCode, k -> new Nutrition()), key, v);
                                    if (confidence != null)
                                        conf.computeIfAbsent(alimentCode, k -> new HashMap<>()).merge(confidence, 1, Integer::sum);
                                }
                            }
                            if (++rows % 10_000 == 0)
                                broadcastProgress("Processing nutrition... (" + rows + " rows)", 25 + Math.min(65, rows * 65 / 174_000));
                        }
                        tag = null;
                        break;
                }
                ev = p.next();
            }
        }

        broadcastProgress("Saving nutrition records...", 92);
        List<NutritionEntity> batch = new ArrayList<>(BATCH_SIZE);
        int saved = 0;
        int total = codeToEntityId.size();
        for (Map.Entry<Integer, String> entry : codeToEntityId.entrySet()) {
            int code = entry.getKey();
            String entityId = entry.getValue();

            Nutrition n = accumulator.getOrDefault(code, new Nutrition());
            n.setDataSource(CiqualConstants.SOURCE_ID);
            n.calculateCompleteness();

            // Derive overall DataConfidence from the per-nutrient confidence codes.
            // conf.get(code) holds a frequency map e.g. {"A":12, "B":3, "C":1}.
            // We take the WORST (lowest confidence) code present — even one C or D
            // nutrient lowers the overall product confidence to ESTIMATED.
            Map<String, Integer> codeCounts = conf.get(code);
            DataConfidence confidence = DataConfidence.SCIENTIFIC; // optimistic default

            if (codeCounts != null) {
                for (String ciqualCode : codeCounts.keySet()) {
                    confidence = confidence.lowest(DataConfidence.fromCiqualCode(ciqualCode));
                }
            }
            n.setDataConfidence(confidence);

            NutritionEntity ne = NutritionEntity.fromNutrition(n, "product", entityId);
            ne.setDataSource(CiqualConstants.SOURCE_ID);
            batch.add(ne);

            if (batch.size() >= BATCH_SIZE) {
                dao.insertNutritionBatch(batch); saved += batch.size(); batch.clear();
                broadcastProgress("Saving nutrition... (" + saved + "/" + total + ")", 92 + Math.min(6, saved * 6 / total));
            }
        }
        if (!batch.isEmpty()) { dao.insertNutritionBatch(batch); saved += batch.size(); }
        Log.i(TAG, "Phase 2: " + saved + " NutritionEntity rows saved");
    }

    // ===== NUTRITION VALUE DISPATCH =====

    private void applyValue(@NonNull Nutrition n, @NonNull String key, double v) {
        switch (key) {
            case "energy_kj":           n.setEnergyKj(v);             break;
            case "energy_kcal":         n.setEnergyKcal(v);           break;
            case "proteins":            n.setProteins(v);             break;
            case "carbohydrates":       n.setCarbohydrates(v);        break;
            case "fat":                 n.setFat(v);                  break;
            case "fiber":               n.setFiber(v);                break;
            case "sugars":              n.setSugars(v);               break;
            case "starch":              n.setStarch(v);               break;
            case "polyols":             n.setPolyols(v);              break;
            case "alcohol":             n.setAlcohol(v);              break;
            case "water":               n.setWater(v);                break;
            case "ash":                 n.setAsh(v);                  break;
            case "glucose":             n.setGlucose(v);              break;
            case "fructose":            n.setFructose(v);             break;
            case "sucrose":             n.setSucrose(v);              break;
            case "lactose":             n.setLactose(v);              break;
            case "maltose":             n.setMaltose(v);              break;
            case "saturated_fat":       n.setSaturatedFat(v);         break;
            case "monounsaturated_fat": n.setMonounsaturatedFat(v);   break;
            case "polyunsaturated_fat": n.setPolyunsaturatedFat(v);   break;
            case "cholesterol":         n.setCholesterol(v);          break;
            case "butyric_acid":        n.setButyricAcid(v);          break;
            case "caproic_acid":        n.setCaproicAcid(v);          break;
            case "caprylic_acid":       n.setCaprylicAcid(v);         break;
            case "capric_acid":         n.setCapricAcid(v);           break;
            case "lauric_acid":         n.setLauricAcid(v);           break;
            case "myristic_acid":       n.setMyristicAcid(v);         break;
            case "palmitic_acid":       n.setPalmiticAcid(v);         break;
            case "stearic_acid":        n.setStearicAcid(v);          break;
            case "linoleic_acid":       n.setLinoleicAcid(v);         break;
            case "ala":                 n.setALA(v);                  break;
            case "epa":                 n.setEPA(v);                  break;
            case "dha":                 n.setDHA(v);                  break;
            case "sodium":              n.setSodium(v);               break;
            case "salt":                n.setSalt(v);                 break;
            case "calcium":             n.setCalcium(v);              break;
            case "magnesium":           n.setMagnesium(v);            break;
            case "phosphorus":          n.setPhosphorus(v);           break;
            case "potassium":           n.setPotassium(v);            break;
            case "iron":                n.setIron(v);                 break;
            case "zinc":                n.setZinc(v);                 break;
            case "copper":              n.setCopper(v);               break;
            case "manganese":           n.setManganese(v);            break;
            case "selenium":            n.setSelenium(v);             break;
            case "iodine":              n.setIodine(v);               break;
            case "chloride":            n.setChloride(v);             break;
            case "vitamin_a":           n.setVitaminA(v);             break;
            case "retinol":             n.setRetinol(v);              break;
            case "beta_carotene":       n.setBetaCarotene(v);         break;
            case "vitamin_d":           n.setVitaminD(v);             break;
            case "vitamin_d2":          n.setVitaminD2(v);            break;
            case "vitamin_d3":          n.setVitaminD3(v);            break;
            case "vitamin_e":           n.setVitaminE(v);             break;
            case "vitamin_k1":          n.setVitaminK1(v);            break;
            case "vitamin_c":           n.setVitaminC(v);             break;
            case "vitamin_b1":          n.setVitaminB1(v);            break;
            case "vitamin_b2":          n.setVitaminB2(v);            break;
            case "vitamin_b3":          n.setVitaminB3(v);            break;
            case "vitamin_b5":          n.setVitaminB5(v);            break;
            case "vitamin_b6":          n.setVitaminB6(v);            break;
            case "vitamin_b12":         n.setVitaminB12(v);           break;
            case "vitamin_b9":          n.setVitaminB9(v);            break;
            case "intrinsic_folate":    n.setIntrinsicFolate(v);      break;
            case "folic_acid":          n.setFolicAcid(v);            break;
            case "organic_acids":       n.setOrganicAcids(v);         break;
        }
    }

    // ===== HELPERS =====

    @Nullable private String bestCode(@Nullable String g, @Nullable String sg, @Nullable String ssg) {
        if (ok(ssg) && !"000000".equals(ssg)) return ssg;
        if (ok(sg)) return sg;
        if (ok(g))  return g;
        return null;
    }

    @NonNull private XmlPullParser parser(@NonNull InputStream is) throws Exception {
        XmlPullParserFactory f = XmlPullParserFactory.newInstance(); f.setNamespaceAware(false);
        XmlPullParser p = f.newPullParser(); p.setInput(is, "UTF-8"); return p;
    }

    private int intSafe(@Nullable String s) { try { return Integer.parseInt(s); } catch (Exception e) { return 0; } }
    @Nullable private Double doubleSafe(@Nullable String s) { try { return Double.parseDouble(s.replace(',','.')); } catch (Exception e) { return null; } }
    private boolean ok(@Nullable String s) { return s != null && !s.isEmpty(); }
    @Nullable private String blank(@Nullable String s) { return ok(s) ? s : null; }
    @Nullable private String dash(@Nullable String s) { return (s == null || s.trim().isEmpty() || "-".equals(s.trim())) ? null : s.trim(); }

    // ===== BROADCASTS =====

    private void broadcastProgress(@NonNull String phase, int pct) {
        Intent i = new Intent(BROADCAST_PROGRESS);
        i.setPackage(getPackageName()); // required for RECEIVER_NOT_EXPORTED on API 34+
        i.putExtra(EXTRA_PHASE, phase);
        if (pct >= 0) i.putExtra(EXTRA_PROGRESS_PCT, Math.min(100, pct));
        sendBroadcast(i);
        updateNotification(phase, Math.max(0, pct));
        Log.d(TAG, (pct >= 0 ? "[" + pct + "%] " : "[dl] ") + phase);
    }
    private void broadcastComplete() {
        Intent i = new Intent(BROADCAST_COMPLETE);
        i.setPackage(getPackageName());
        sendBroadcast(i);
    }
    private void broadcastError(@NonNull String msg) {
        Intent i = new Intent(BROADCAST_ERROR);
        i.setPackage(getPackageName());
        i.putExtra(EXTRA_ERROR_MSG, msg);
        sendBroadcast(i);
    }

    // ===== PREFS =====

    private void writePrefs(boolean success) {
        SharedPreferences.Editor ed = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
        ed.putBoolean(PREF_DB_READY, success);
        if (success) {
            ed.putString(PREF_IMPORT_VERSION, CiqualConstants.DATASET_VERSION);
            ed.putLong(PREF_IMPORT_DATE, System.currentTimeMillis());
        }
        ed.apply();
    }

    /** True if the DB has been imported AND the stored version matches the current dataset version. */
    public static boolean isImported(@NonNull Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        if (!prefs.getBoolean(PREF_DB_READY, false)) return false;
        String stored = prefs.getString(PREF_IMPORT_VERSION, null);
        return CiqualConstants.DATASET_VERSION.equals(stored);
    }

    /** True if a newer dataset version is available than what's currently imported. */
    public static boolean needsUpdate(@NonNull Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        if (!prefs.getBoolean(PREF_DB_READY, false)) return true;
        String stored = prefs.getString(PREF_IMPORT_VERSION, null);
        return !CiqualConstants.DATASET_VERSION.equals(stored);
    }

    @Nullable
    public static String getImportedVersion(@NonNull Context ctx) {
        return ctx.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getString(PREF_IMPORT_VERSION, null);
    }

    // ===== NOTIFICATION =====

    private void createNotificationChannel() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.createNotificationChannel(
                new NotificationChannel(NOTIF_CHANNEL_ID, "Ciqual Import", NotificationManager.IMPORTANCE_LOW));
    }

    @NonNull private android.app.Notification buildNotification(@NonNull String text, int pct) {
        return new NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
                .setContentTitle("Importing Ciqual database")
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setProgress(100, pct, pct == 0)
                .setOngoing(true).setOnlyAlertOnce(true).build();
    }
    private void updateNotification(@NonNull String text, int pct) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(NOTIF_ID, buildNotification(text, pct));
    }
}