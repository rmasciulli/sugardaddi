package li.masciul.sugardaddi.data.sources.ciqual.xml;

import org.simpleframework.xml.*;
import java.util.ArrayList;
import java.util.List;

/**
 * XML models for Ciqual database files
 * Using Simple XML framework for Android-friendly parsing
 *
 * FIXED: Updated to match actual Ciqual XML structure
 * - ALIM_GRP instead of GROUPE for food groups
 * - SOURCES instead of SOURCE for sources
 * - ref_citation instead of source_citation
 */
public class CiqualXmlModels {

    /**
     * Model for alim_*.xml (food items)
     */
    @Root(name = "TABLE", strict = false)
    public static class AlimTable {
        @ElementList(inline = true, entry = "ALIM")
        public List<Aliment> aliments;
    }

    @Root(name = "ALIM", strict = false)
    public static class Aliment {
        @Element(name = "alim_code")
        public int code;

        @Element(name = "alim_nom_fr", required = false)
        public String nameFr;

        @Element(name = "alim_nom_eng", required = false)
        public String nameEn;

        @Element(name = "alim_grp_code")
        public int groupCode;

        @Element(name = "alim_ssgrp_code", required = false)
        public Integer subgroupCode;

        @Element(name = "alim_ssssgrp_code", required = false)
        public Integer subsubgroupCode;
    }

    /**
     * Model for alim_grp_*.xml (food groups/categories) — LEGACY
     *
     * NOTE: This model and the FoodGroup/SubGroup classes below are kept for
     * backward compatibility with the legacy full-ZIP parsing path (Phase 2B).
     * For Phase 2A (ES result enrichment), use CiqualCategoryLookup instead,
     * which uses XmlPullParser directly and the CiqualGroupEntry flat model.
     *
     * FIXED: The actual XML uses ALIM_GRP, not GROUPE
     * We support both for backwards compatibility
     */
    @Root(name = "TABLE", strict = false)
    public static class GroupTable {
        @ElementList(inline = true, entry = "ALIM_GRP", required = false)
        public List<FoodGroup> groups;

        @ElementList(inline = true, entry = "GROUPE", required = false)
        public List<FoodGroup> groupsAlt;

        /**
         * Merge both possible entry types into one list
         */
        public List<FoodGroup> getAllGroups() {
            List<FoodGroup> all = new ArrayList<>();
            if (groups != null) all.addAll(groups);
            if (groupsAlt != null) all.addAll(groupsAlt);
            return all;
        }
    }

    @Root(name = "ALIM_GRP", strict = false)
    public static class FoodGroup {
        @Element(name = "alim_grp_code")
        public int code;

        @Element(name = "alim_grp_nom_fr")
        public String nameFr;

        @Element(name = "alim_grp_nom_eng", required = false)
        public String nameEn;
    }

    @Root(name = "SOUS_GROUPE", strict = false)
    public static class SubGroup {
        @Element(name = "alim_ssgrp_code")
        public int code;

        @Element(name = "alim_ssgrp_nom_fr")
        public String nameFr;

        @Element(name = "alim_ssgrp_nom_eng", required = false)
        public String nameEn;

        @Element(name = "alim_grp_code")
        public int parentGroupCode;
    }

    /**
     * Model for compo_*.xml (composition/nutrition data)
     */
    @Root(name = "TABLE", strict = false)
    public static class CompoTable {
        @ElementList(inline = true, entry = "COMPO")
        public List<Composition> compositions;
    }

    @Root(name = "COMPO", strict = false)
    public static class Composition {
        @Element(name = "alim_code")
        public int alimentCode;

        @Element(name = "const_code")
        public int constituentCode;

        @Element(name = "teneur", required = false)
        public String value; // Can be number or "traces" or "-"

        @Element(name = "min", required = false)
        public Double min;

        @Element(name = "max", required = false)
        public Double max;

        @Element(name = "code_confiance", required = false)
        public String confidenceCode;

        @Element(name = "source_code", required = false)
        public String sourceCode;
    }

    /**
     * Model for const_*.xml (constituents/nutrients definitions)
     */
    @Root(name = "TABLE", strict = false)
    public static class ConstTable {
        @ElementList(inline = true, entry = "CONST")
        public List<Constituent> constituents;
    }

    @Root(name = "CONST", strict = false)
    public static class Constituent {
        @Element(name = "const_code")
        public int code;

        @Element(name = "const_nom_fr")
        public String nameFr;

        @Element(name = "const_nom_eng", required = false)
        public String nameEn;

        @Element(name = "const_symbole", required = false)
        public String symbol; // Like "kcal", "g", "mg"

        @Element(name = "const_unite", required = false)
        public String unit;
    }

    /**
     * Model for sources_*.xml (data sources)
     * FIXED: XML uses SOURCES, not SOURCE
     * FIXED: Uses ref_citation, not source_citation
     */
    @Root(name = "TABLE", strict = false)
    public static class SourceTable {
        @ElementList(inline = true, entry = "SOURCES")
        public List<Source> sources;
    }

    @Root(name = "SOURCES", strict = false)
    public static class Source {
        @Element(name = "source_code")
        public String code;

        @Element(name = "source_nom", required = false)
        public String name;

        @Element(name = "ref_citation", required = false)
        public String citation;
    }

    /**
     * Metadata to store source information with products
     */
    public static class CiqualMetadata {
        public String productId; // Links to FoodProductEntity
        public String originalCode; // Original Ciqual code
        public List<SourceReference> sources;
        public String confidenceLevel;
        public long importDate;

        public static class SourceReference {
            public String nutrientName;
            public String sourceCode;
            public String sourceName;
            public String citation;
        }
    }

    // ========== PHASE 2A: FLAT GROUP MODEL (alim_grp_2025_11_03.xml) ==========

    /**
     * Flat model representing one ALIM_GRP row from alim_grp_2025_11_03.xml.
     *
     * The alim_grp XML is a FLAT denormalized table — each row contains all three
     * levels of the category hierarchy (group + subgroup + sub-subgroup) in one record.
     * This is NOT a nested structure; do not attempt to nest FoodGroup/SubGroup here.
     *
     * This class is used as a data holder only; actual parsing is done by
     * CiqualCategoryLookup using XmlPullParser (not Simple XML) because:
     * 1. Simple XML had issues with the old Ciqual XML format
     * 2. XmlPullParser is built into Android — no extra dependency
     * 3. The flat structure does not benefit from annotation-based parsing
     *
     * Documented field values (from Ciqual 2025 documentation + verified from file):
     *   grpCode:      "01" .. "11"  (text, leading zero, 2 chars)
     *   ssgrpCode:    "0101" .. "1104"  (text, 4 chars)
     *   ssssgrpCode:  "000000" (= no sub-subgroup) or "010301" .. "040433" (6 chars)
     *   names:        "-" means no name at this level (occurs with ssssgrp=000000)
     */
    public static class CiqualGroupEntry {
        // Level 1
        public String grpCode;
        public String grpNameFr;
        public String grpNameEng;

        // Level 2
        public String ssgrpCode;
        public String ssgrpNameFr;
        public String ssgrpNameEng;

        // Level 3 — "000000" means absent
        public String ssssgrpCode;
        public String ssssgrpNameFr;
        public String ssssgrpNameEng;

        /** True if this row has a real (non-sentinel) sub-subgroup */
        public boolean hasSubSubGroup() {
            return ssssgrpCode != null
                    && !"000000".equals(ssssgrpCode)
                    && ssssgrpNameEng != null
                    && !"-".equals(ssssgrpNameEng.trim());
        }
    }

}