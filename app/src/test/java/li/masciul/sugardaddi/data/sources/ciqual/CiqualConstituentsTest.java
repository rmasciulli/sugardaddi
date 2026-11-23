package li.masciul.sugardaddi.data.sources.ciqual;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * CiqualConstituents against Ciqual's own files: the const file of the current
 * dataset (app/src/main/assets/const_<DATASET_VERSION>.xml, with the other Ciqual
 * files), and values of real foods copied from compo_2025_11_03.xml.
 *
 * When DATASET_VERSION changes and the new const file is not in assets yet, the
 * test fails on purpose: see the update procedure in CiqualConstants.
 */
public class CiqualConstituentsTest {

    private static final double EPS = 1e-9;

    /**
     * The const file of the current dataset. Unit tests do not see assets on the
     * classpath: the file is opened by its path, from the module folder (the
     * working directory Gradle runs unit tests in).
     */
    private static File constFilePath() {
        File file = new File("src/main/assets/const_" + CiqualConstants.DATASET_VERSION + ".xml");
        assertTrue("missing " + file.getPath() + ": add the const file of the new Ciqual"
                + " release (update procedure in CiqualConstants)", file.isFile());
        return file;
    }

    /** const_code -> {nom_fr, nom_eng}, read from Ciqual's const file. */
    private static Map<Integer, String[]> constFile() throws Exception {
        try (InputStream in = new FileInputStream(constFilePath())) {
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in);
            NodeList rows = doc.getElementsByTagName("CONST");
            Map<Integer, String[]> map = new HashMap<>();
            for (int i = 0; i < rows.getLength(); i++) {
                Element row = (Element) rows.item(i);
                int code = Integer.parseInt(text(row, "const_code"));
                map.put(code, new String[] { text(row, "const_nom_fr"), text(row, "const_nom_eng") });
            }
            return map;
        }
    }

    private static String text(Element row, String tag) {
        return row.getElementsByTagName(tag).item(0).getTextContent().trim();
    }

    // ========== the table matches Ciqual's const file ==========

    @Test
    public void everyCodeAndNameIsCiqualsOwn() throws Exception {
        Map<Integer, String[]> ciqual = constFile();
        for (CiqualConstituents.Constituent c : CiqualConstituents.all()) {
            String[] names = ciqual.get(c.code);
            assertNotNull("not a Ciqual code: " + c.code, names);
            assertEquals("French name of " + c.code, names[0], c.nameFr);
            assertEquals("English name of " + c.code, names[1], c.nameEn);
        }
    }

    @Test
    public void everyNameFindsItsOwnRow() throws Exception {
        for (Map.Entry<Integer, String[]> e : constFile().entrySet()) {
            CiqualConstituents.Constituent byCode = CiqualConstituents.byCode(e.getKey());
            if (byCode == null) continue;   // not read on purpose
            assertSame(byCode, CiqualConstituents.byName(e.getValue()[0]));
            assertSame(byCode, CiqualConstituents.byName(e.getValue()[1]));
        }
    }

    @Test
    public void constituentsLeftOutOnPurpose() {
        assertNull(CiqualConstituents.byCode(332));     // energy, Jones' factor
        assertNull(CiqualConstituents.byCode(333));
        assertNull(CiqualConstituents.byCode(25003));   // protein N x 6.25
        assertNull(CiqualConstituents.byCode(56702));   // folates DFE
        assertNull(CiqualConstituents.byCode(71010));   // alpha-tocopherol
        assertNull(CiqualConstituents.byCode(41819));   // oleic acid
    }

    // ========== names: exact, not loose ==========

    @Test
    public void namesThatLooseMatchingMixedUp() {
        assertEquals(CiqualConstituents.Field.VITAMIN_B12,
                CiqualConstituents.byName("Vitamine B12 (µg/100 g)").field);
        assertEquals(CiqualConstituents.Field.VITAMIN_B1,
                CiqualConstituents.byName("Vitamine B1 ou Thiamine (mg/100 g)").field);
        assertEquals(CiqualConstituents.Field.SALT,
                CiqualConstituents.byName("Sel chlorure de sodium (g/100 g)").field);
        assertEquals(CiqualConstituents.Field.IRON,
                CiqualConstituents.byName("Fer (mg/100 g)").field);
        assertEquals(CiqualConstituents.Field.FOLIC_ACID,
                CiqualConstituents.byName("Acide folique (enrichissement) (µg/100 g)").field);
    }

    @Test
    public void namesIgnoreCaseAccentsSpacesAndMicroSign() {
        assertEquals(CiqualConstituents.Field.IRON, CiqualConstituents.byName("  fer (mg/100g) ").field);
        assertEquals(CiqualConstituents.Field.MAGNESIUM,
                CiqualConstituents.byName("Magnesium (mg/100 g)").field);
        assertEquals(CiqualConstituents.Field.SELENIUM,
                CiqualConstituents.byName("Sélénium (μg/100 g)").field);   // Greek mu
        assertNull(CiqualConstituents.byName("Fer"));
        assertNull(CiqualConstituents.byName(null));
    }

    // ========== values ==========

    @Test
    public void values() {
        assertEquals(11.5, CiqualConstituents.parseValue(" 11,5 "), EPS);
        assertEquals(0.048, CiqualConstituents.parseValue("0,048"), EPS);
        assertEquals(0.0, CiqualConstituents.parseValue("traces"), EPS);
        assertNull(CiqualConstituents.parseValue("< 0,5"));
        assertNull(CiqualConstituents.parseValue("&lt; 0,013"));
        assertNull(CiqualConstituents.parseValue("-"));
        assertNull(CiqualConstituents.parseValue(""));
        assertNull(CiqualConstituents.parseValue(null));
    }

    // ========== real foods (compo_2025_11_03.xml) ==========

    /** 19010 "Lait entier, cru": the minerals and vitamins the old table mixed up. */
    @Test
    public void rawWholeMilk_eachValueInItsField() {
        assertField(10200, "120", CiqualConstituents.Field.CALCIUM, 120);
        assertField(10120, "11", CiqualConstituents.Field.MAGNESIUM, 11);
        assertField(10190, "153", CiqualConstituents.Field.POTASSIUM, 153);
        assertField(10150, "88,3", CiqualConstituents.Field.PHOSPHORUS, 88.3);
        assertField(10170, "109", CiqualConstituents.Field.CHLORIDE, 109);
        assertField(10340, "2,21", CiqualConstituents.Field.SELENIUM, 2.21);
        assertField(56600, "0,39", CiqualConstituents.Field.VITAMIN_B12, 0.39);
        assertField(56700, "5,29", CiqualConstituents.Field.VITAMIN_B9, 5.29);
        assertField(75100, "11,5", CiqualConstituents.Field.CHOLESTEROL, 11.5);
        assertField(10004, "0,12", CiqualConstituents.Field.SALT, 0.12);
    }

    /** 6100 beef rib steak: fatty acids in g in Ciqual, ALA / EPA / DHA in mg in the app. */
    @Test
    public void beef_fattyAcidsInMilligrams() {
        assertField(41833, "0,037", CiqualConstituents.Field.ALA, 37);
        assertField(42053, "0,001", CiqualConstituents.Field.EPA, 1);
        assertField(42263, "0,00018", CiqualConstituents.Field.DHA, 0.18);
    }

    /** 1000 Pastis (alcohol 37.5 g) and 1032 beef and soy mince (folic acid 5.7 µg). */
    @Test
    public void alcoholAndFolicAcid() {
        assertField(60000, "37,5", CiqualConstituents.Field.ALCOHOL, 37.5);
        assertField(56708, "5,7", CiqualConstituents.Field.FOLIC_ACID, 5.7);
    }

    private static void assertField(int code, String teneur, CiqualConstituents.Field field,
                                    double appValue) {
        CiqualConstituents.Constituent c = CiqualConstituents.byCode(code);
        assertNotNull("code " + code, c);
        assertEquals("field of " + code, field, c.field);
        assertEquals("value of " + code, appValue,
                c.toAppUnit(CiqualConstituents.parseValue(teneur)), 1e-6);
    }
}
