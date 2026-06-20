package li.masciul.sugardaddi.data.sources.ciqual;

/**
 * CiqualConstants - All Ciqual data source constants.
 *
 * DATASET UPDATE PROCEDURE (annual, when ANSES releases a new Ciqual version):
 * 1. Update DATASET_VERSION to the new date string (e.g. "2026_04_01")
 * 2. Update ZENODO_RECORD_ID to the new Zenodo record number
 * 3. Update ASSET_ALIM, ASSET_COMPO filenames to match the new release
 * 4. Replace the bundled alim_grp asset in app/src/main/assets/ (alim and compo
 *    are fetched from Zenodo, so only the category file ships)
 * 5. Bump AppDatabase version if schema changed
 *
 * The app will detect the version mismatch on next launch and re-import automatically.
 */
public class CiqualConstants {

    // ===== SOURCE IDENTIFICATION =====
    public static final String SOURCE_ID   = "CIQUAL";
    public static final String SOURCE_NAME = "Ciqual";
    public static final String ATTRIBUTION = "Table Ciqual - ANSES";

    // ===== DATASET VERSION =====
    /**
     * Current dataset version string. Matches the date suffix in Zenodo filenames.
     * When this changes, CiqualDataSource triggers a re-import on next launch.
     */
    public static final String DATASET_VERSION = "2025_11_03";

    // ===== ZENODO DOI & RECORD =====
    /**
     * Zenodo DOI for the Ciqual 2025 dataset.
     * The concept DOI (always resolves to the latest version):
     *   https://doi.org/10.5281/zenodo.17550132
     * The version-specific DOI (pinned to 2025_11_03):
     *   https://doi.org/10.5281/zenodo.17550133
     *
     * We use the VERSION-SPECIFIC record ID so imports are reproducible.
     * Update ZENODO_RECORD_ID together with DATASET_VERSION each year.
     */
    public static final String ZENODO_RECORD_ID  = "17550133";
    public static final String ZENODO_API_BASE   = "https://zenodo.org/api/records/";
    public static final String ZENODO_FILES_BASE = ZENODO_API_BASE + ZENODO_RECORD_ID + "/files/";

    // ===== DATASET FILES =====
    /**
     * The three XML files that make up the Ciqual dataset.
     * Each constant is the filename as it appears on Zenodo and in assets/.
     */
    public static final String ASSET_GRP   = "alim_grp_"   + DATASET_VERSION + ".xml";
    public static final String ASSET_ALIM  = "alim_"       + DATASET_VERSION + ".xml";
    public static final String ASSET_COMPO = "compo_"      + DATASET_VERSION + ".xml";

    // ===== ZENODO DOWNLOAD URLS =====
    /** Built from ZENODO_FILES_BASE + filename + "/content". */
    public static final String URL_GRP   = ZENODO_FILES_BASE + ASSET_GRP   + "/content";
    public static final String URL_ALIM  = ZENODO_FILES_BASE + ASSET_ALIM  + "/content";
    public static final String URL_COMPO = ZENODO_FILES_BASE + ASSET_COMPO + "/content";

    // ===== ELASTICSEARCH (live search API, unchanged) =====
    public static final String ELASTICSEARCH_BASE_URL = "https://ciqual.anses.fr/";
    public static final String ELASTICSEARCH_ENDPOINT = "esearch/aliments/_search";

    // ===== DATABASE =====
    public static final String DB_NAME    = "sugardaddi_database";

    private CiqualConstants() {}
}