package li.masciul.sugardaddi.data.sources.ciqual.xml;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import li.masciul.sugardaddi.data.sources.ciqual.api.dto.CiqualElasticsearchFood;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * CiqualCategoryLookup — Category hierarchy resolver for Ciqual ES search results.
 *
 * PROBLEM SOLVED:
 * The Ciqual Elasticsearch API returns only two human-readable category fields:
 *   - groupeAfficheEng / groupeAfficheFr  (always the lvl-2 subgroup name)
 * The grps[] array contains {code, lvl} pairs for all three hierarchy levels,
 * but only as numeric codes — no names for the parent group or sub-subgroup.
 *
 * This class resolves those codes to full breadcrumb strings like:
 *   "sugar and confectionery > breakfast cereals"
 * instead of just:
 *   "breakfast cereals"
 *
 * ARCHITECTURE:
 * - Singleton, initialized once at app startup from assets
 * - Parses alim_grp_2025_11_03.xml (80KB, bundled as Android asset)
 * - Uses XmlPullParser (built into Android — no external library needed)
 * - Builds two flat lookup maps keyed by ssgrp_code and ssssgrp_code
 * - Thread-safe: reads are lock-free once initialized
 * - Graceful degradation: returns null if not ready → caller falls back to groupeAfficheEng
 *
 * ASSET FILE STRUCTURE (confirmed from real file):
 * <TABLE>
 *   <ALIM_GRP>
 *     <alim_grp_code> 01 </alim_grp_code>          ← trimmed to "01"
 *     <alim_grp_nom_fr> entrées et plats composés </alim_grp_nom_fr>
 *     <alim_grp_nom_eng> starters and dishes </alim_grp_nom_eng>
 *     <alim_ssgrp_code> 0101 </alim_ssgrp_code>
 *     <alim_ssgrp_nom_fr> salades composées et crudités </alim_ssgrp_nom_fr>
 *     <alim_ssgrp_nom_eng> mixed salads </alim_ssgrp_nom_eng>
 *     <alim_ssssgrp_code> 000000 </alim_ssssgrp_code>   ← sentinel = no 3rd level
 *     <alim_ssssgrp_nom_fr> - </alim_ssssgrp_nom_fr>    ← "-" = no 3rd level name
 *     <alim_ssssgrp_nom_eng> - </alim_ssssgrp_nom_eng>
 *   </ALIM_GRP>
 *   ...
 * </TABLE>
 *
 * LOOKUP STRATEGY (given ES grps[] for a product):
 * 1. Find lvl=3 code in grps[] → look in ssssgrpMap → 3-part breadcrumb
 * 2. If no lvl=3, find lvl=2 code → look in ssgrpMap → 2-part breadcrumb
 * 3. If not found or not initialized → return null → caller uses groupeAfficheEng
 *
 * SENTINEL VALUES (confirmed from file):
 *   ssssgrp_code = "000000" → no sub-subgroup exists
 *   ssssgrp_nom_eng = "-"   → no name (placeholder)
 *
 * FILE: assets/alim_grp_2025_11_03.xml (80KB, 138 ALIM_GRP entries)
 * Ciqual 2025 — updated ~annually by ANSES. Bundled statically for simplicity.
 * When a new version is released, replace the asset file and rebuild the app.
 */
public class CiqualCategoryLookup {

    private static final String TAG = "CiqualCategoryLookup";

    /** Asset filename — update when ANSES releases a new version */
    public static final String ASSET_FILENAME = "alim_grp_2025_11_03.xml";

    /** Sentinel code meaning "no sub-subgroup" */
    private static final String SSSSGRP_NONE = "000000";

    /** Sentinel name meaning "no name at this level" */
    private static final String NAME_NONE = "-";

    // ========== SINGLETON ==========

    private static volatile CiqualCategoryLookup instance;

    public static CiqualCategoryLookup getInstance() {
        if (instance == null) {
            synchronized (CiqualCategoryLookup.class) {
                if (instance == null) {
                    instance = new CiqualCategoryLookup();
                }
            }
        }
        return instance;
    }

    private CiqualCategoryLookup() {}

    // ========== STATE ==========

    /** True once parseFromAssets() has completed successfully */
    private final AtomicBoolean ready = new AtomicBoolean(false);

    /**
     * Flat row representing one ALIM_GRP entry.
     * Each row carries all three levels of the hierarchy.
     * Immutable after construction.
     */
    public static final class CategoryRow {
        // Level 1 — group
        public final String grpCode;
        public final String grpNameFr;
        public final String grpNameEn;

        // Level 2 — subgroup
        public final String ssgrpCode;
        public final String ssgrpNameFr;
        public final String ssgrpNameEn;

        // Level 3 — sub-subgroup (may be absent: ssssgrpCode == SSSSGRP_NONE)
        public final String ssssgrpCode;
        public final String ssssgrpNameFr;
        public final String ssssgrpNameEn;

        CategoryRow(
                String grpCode, String grpNameFr, String grpNameEn,
                String ssgrpCode, String ssgrpNameFr, String ssgrpNameEn,
                String ssssgrpCode, String ssssgrpNameFr, String ssssgrpNameEn) {
            this.grpCode = grpCode;
            this.grpNameFr = grpNameFr;
            this.grpNameEn = grpNameEn;
            this.ssgrpCode = ssgrpCode;
            this.ssgrpNameFr = ssgrpNameFr;
            this.ssgrpNameEn = ssgrpNameEn;
            this.ssssgrpCode = ssssgrpCode;
            this.ssssgrpNameFr = ssssgrpNameFr;
            this.ssssgrpNameEn = ssssgrpNameEn;
        }

        /** True if this row has a real 3rd-level sub-subgroup */
        public boolean hasSubSubGroup() {
            return !SSSSGRP_NONE.equals(ssssgrpCode)
                    && ssssgrpNameEn != null
                    && !NAME_NONE.equals(ssssgrpNameEn.trim());
        }
    }

    /**
     * Primary lookup map: ssgrp_code → CategoryRow
     * Covers all 138 entries (every entry has a ssgrp_code).
     */
    private Map<String, CategoryRow> ssgrpMap = new HashMap<>();

    /**
     * Secondary lookup map: ssssgrp_code → CategoryRow
     * Covers the 85 entries that have a real sub-subgroup.
     * Used when ES grps[] contains a lvl=3 code.
     */
    private Map<String, CategoryRow> ssssgrpMap = new HashMap<>();

    // ========== INITIALIZATION ==========

    /**
     * Parse the bundled category XML asset and build the lookup maps.
     *
     * Must be called from a background thread — opens and reads a file.
     * Safe to call multiple times: subsequent calls are no-ops.
     *
     * @param context Application context (for AssetManager access)
     */
    public void parseFromAssets(@NonNull Context context) {
        if (ready.get()) {
            Log.d(TAG, "Already initialized, skipping");
            return;
        }

        Log.d(TAG, "Initializing category lookup from asset: " + ASSET_FILENAME);
        long start = System.currentTimeMillis();

        try (InputStream is = context.getAssets().open(ASSET_FILENAME)) {

            Map<String, CategoryRow> newSsgrpMap = new HashMap<>(80);
            Map<String, CategoryRow> newSsssgrpMap = new HashMap<>(90);

            parseXml(is, newSsgrpMap, newSsssgrpMap);

            // Publish atomically — readers never see a half-built map
            ssgrpMap = newSsgrpMap;
            ssssgrpMap = newSsssgrpMap;
            ready.set(true);

            long elapsed = System.currentTimeMillis() - start;
            Log.i(TAG, String.format(
                    "Category lookup ready: %d ssgrp entries, %d ssssgrp entries (%dms)",
                    ssgrpMap.size(), ssssgrpMap.size(), elapsed));

        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize category lookup from assets", e);
            // ready stays false — callers will fall back to groupeAfficheEng
        }
    }

    /**
     * Parse alim_grp XML using XmlPullParser (built-in Android, no dependency needed).
     *
     * Uses XmlPullParser instead of Simple XML because:
     * 1. The file is structurally simple (flat list of identical records)
     * 2. No external library dependency for this lightweight use case
     * 3. XmlPullParser handles whitespace-padded values naturally via trim()
     * 4. Simple XML's strict mode choked on the old Ciqual XML files
     *
     * Element values are trimmed — the real file has spaces around codes:
     * e.g. " 0101 " → "0101"
     */
    private void parseXml(
            @NonNull InputStream is,
            @NonNull Map<String, CategoryRow> ssgrpOut,
            @NonNull Map<String, CategoryRow> ssssgrpOut) throws Exception {

        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        factory.setNamespaceAware(false);
        XmlPullParser parser = factory.newPullParser();
        parser.setInput(is, "UTF-8");

        // Fields accumulated for the current ALIM_GRP element
        String grpCode = null, grpNameFr = null, grpNameEn = null;
        String ssgrpCode = null, ssgrpNameFr = null, ssgrpNameEn = null;
        String ssssgrpCode = null, ssssgrpNameFr = null, ssssgrpNameEn = null;
        String currentTag = null;

        int eventType = parser.getEventType();
        while (eventType != XmlPullParser.END_DOCUMENT) {

            switch (eventType) {
                case XmlPullParser.START_TAG:
                    currentTag = parser.getName();
                    if ("ALIM_GRP".equals(currentTag)) {
                        // Reset all fields for this entry
                        grpCode = grpNameFr = grpNameEn = null;
                        ssgrpCode = ssgrpNameFr = ssgrpNameEn = null;
                        ssssgrpCode = ssssgrpNameFr = ssssgrpNameEn = null;
                    }
                    break;

                case XmlPullParser.TEXT:
                    if (currentTag == null) break;
                    String text = parser.getText().trim();
                    switch (currentTag) {
                        case "alim_grp_code":       grpCode = text;       break;
                        case "alim_grp_nom_fr":     grpNameFr = text;     break;
                        case "alim_grp_nom_eng":    grpNameEn = text;     break;
                        case "alim_ssgrp_code":     ssgrpCode = text;     break;
                        case "alim_ssgrp_nom_fr":   ssgrpNameFr = text;   break;
                        case "alim_ssgrp_nom_eng":  ssgrpNameEn = text;   break;
                        case "alim_ssssgrp_code":   ssssgrpCode = text;   break;
                        case "alim_ssssgrp_nom_fr": ssssgrpNameFr = text; break;
                        case "alim_ssssgrp_nom_eng":ssssgrpNameEn = text; break;
                    }
                    break;

                case XmlPullParser.END_TAG:
                    if ("ALIM_GRP".equals(parser.getName())) {
                        // Validate minimum required fields before building a row
                        if (isValid(grpCode) && isValid(ssgrpCode)) {
                            CategoryRow row = new CategoryRow(
                                    grpCode, grpNameFr, grpNameEn,
                                    ssgrpCode, ssgrpNameFr, ssgrpNameEn,
                                    ssssgrpCode != null ? ssssgrpCode : SSSSGRP_NONE,
                                    ssssgrpNameFr, ssssgrpNameEn);

                            // Index by ssgrp_code — covers all 138 entries
                            ssgrpOut.put(ssgrpCode, row);

                            // Index by ssssgrp_code — only for real sub-subgroups
                            if (isValid(ssssgrpCode) && !SSSSGRP_NONE.equals(ssssgrpCode)) {
                                ssssgrpOut.put(ssssgrpCode, row);
                            }
                        }
                        currentTag = null;
                    } else {
                        currentTag = null;
                    }
                    break;
            }

            eventType = parser.next();
        }
    }

    /** Non-null and non-empty after trim */
    private static boolean isValid(@Nullable String s) {
        return s != null && !s.isEmpty();
    }

    // ========== PUBLIC API ==========

    /**
     * Returns true if the lookup table is ready to use.
     * If false, callers should fall back to groupeAfficheEng.
     */
    public boolean isReady() {
        return ready.get();
    }

    /**
     * Resolve the Ciqual ES grps[] codes to a full category breadcrumb string.
     *
     * Examples (EN):
     *   grps=[{01,1},{0101,2}]          → "starters and dishes > mixed salads"
     *   grps=[{01,1},{0103,2},{010307,3}] → "starters and dishes > dishes > pasta or cereal dishes"
     *   grps=[{07,1},{0702,2}]          → "sugar and confectionery > chocolate and chocolate products"
     *
     * @param grps     List of CategoryGroup objects from CiqualElasticsearchFood.getGroups()
     * @param language "en" or "fr"
     * @return Breadcrumb string, or null if lookup not ready / no match found
     */
    @Nullable
    public String getCategoryHierarchy(
            @NonNull List<CiqualElasticsearchFood.CategoryGroup> grps,
            @NonNull String language) {

        if (!ready.get() || grps.isEmpty()) return null;

        // Map level → code from the ES grps[] array
        String lvl1Code = null, lvl2Code = null, lvl3Code = null;
        for (CiqualElasticsearchFood.CategoryGroup g : grps) {
            if (g.getLevel() == null || g.getCode() == null) continue;
            switch (g.getLevel()) {
                case 1: lvl1Code = g.getCode().trim(); break;
                case 2: lvl2Code = g.getCode().trim(); break;
                case 3: lvl3Code = g.getCode().trim(); break;
            }
        }

        // Resolve: try deepest level first
        CategoryRow row = null;
        if (lvl3Code != null) {
            row = ssssgrpMap.get(lvl3Code);
        }
        if (row == null && lvl2Code != null) {
            row = ssgrpMap.get(lvl2Code);
        }

        if (row == null) {
            // No match found — caller should fall back to groupeAfficheEng
            return null;
        }

        return buildBreadcrumb(row, language);
    }

    /**
     * Build a human-readable breadcrumb from a CategoryRow.
     *
     * Filters out sentinel values ("-") so they never appear in the UI.
     * Uses " > " as separator, matching the Ciqual website's breadcrumb style.
     */
    @NonNull
    private String buildBreadcrumb(@NonNull CategoryRow row, @NonNull String language) {
        boolean isEn = "en".equalsIgnoreCase(language);
        StringBuilder sb = new StringBuilder();

        // Level 1 — always present
        String grpName = isEn ? row.grpNameEn : row.grpNameFr;
        if (isValid(grpName)) {
            sb.append(grpName);
        }

        // Level 2 — always present (ssgrp is the primary key)
        String ssgrpName = isEn ? row.ssgrpNameEn : row.ssgrpNameFr;
        if (isValid(ssgrpName) && !NAME_NONE.equals(ssgrpName)) {
            if (sb.length() > 0) sb.append(" > ");
            sb.append(ssgrpName);
        }

        // Level 3 — only if real sub-subgroup exists
        if (row.hasSubSubGroup()) {
            String ssssgrpName = isEn ? row.ssssgrpNameEn : row.ssssgrpNameFr;
            if (isValid(ssssgrpName) && !NAME_NONE.equals(ssssgrpName)) {
                if (sb.length() > 0) sb.append(" > ");
                sb.append(ssssgrpName);
            }
        }

        return sb.toString();
    }

    // ========== DEBUG ==========

    /**
     * Returns a debug summary of the lookup table state.
     * Safe to call at any time.
     */
    public String getDebugSummary() {
        if (!ready.get()) return "CiqualCategoryLookup: NOT READY";
        return String.format(
                "CiqualCategoryLookup: ready, %d ssgrp entries, %d ssssgrp entries",
                ssgrpMap.size(), ssssgrpMap.size());
    }
    /**
     * Resolve category hierarchy directly from code strings — no CategoryGroup list needed.
     * Used by CiqualImportService during XML import, where we have raw code strings
     * from the alim XML but no ES DTO objects.
     *
     * @param grpCode     lvl-1 group code, e.g. "07" (may be null)
     * @param ssgrpCode   lvl-2 subgroup code, e.g. "0702" (may be null)
     * @param ssssgrpCode lvl-3 code, or null/"000000" if absent
     * @param language    "en" or "fr"
     * @return Breadcrumb string, or null if not ready / no match
     */
    @Nullable
    public String getCategoryHierarchyFromCodes(
            @Nullable String grpCode,
            @Nullable String ssgrpCode,
            @Nullable String ssssgrpCode,
            @NonNull String language) {

        if (!ready.get()) return null;

        CategoryRow row = null;

        // Try lvl-3 first (most specific)
        if (isValid(ssssgrpCode) && !SSSSGRP_NONE.equals(ssssgrpCode)) {
            row = ssssgrpMap.get(ssssgrpCode);
        }
        // Fallback to lvl-2
        if (row == null && isValid(ssgrpCode)) {
            row = ssgrpMap.get(ssgrpCode);
        }

        if (row == null) return null;
        return buildBreadcrumb(row, language);
    }


}