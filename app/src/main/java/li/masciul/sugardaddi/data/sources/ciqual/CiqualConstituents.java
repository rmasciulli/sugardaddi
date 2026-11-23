package li.masciul.sugardaddi.data.sources.ciqual;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * CiqualConstituents - the Ciqual constituents the app reads, with their real
 * codes and names, and how a Ciqual value is read.
 *
 * SOURCE: const_2025_11_03.xml (Ciqual 2025, Zenodo record 17550133). Codes,
 * French and English names are copied from that file as they are (the English
 * name of 51104 says "µg/100mg" in the source: kept, it is what the API sends).
 * When the dataset is updated, compare this table with the new const file; the
 * unit test CiqualConstituentsTest does that against the copy in test resources.
 *
 * ONE TABLE FOR BOTH PATHS:
 * - the downloaded dataset (CiqualImportService) finds a row by its const_code;
 * - the live search API (CiqualElasticsearchMapper) gives no code, only the
 *   names, so it finds the row by its exact name, French or English.
 * Both then write the value with CiqualNutritionWriter, so a nutrient is read
 * the same way whichever path brought it.
 *
 * UNITS: Ciqual's unit is in each name. All match the unit of the app's field
 * except four fatty acids: ALA, arachidonic acid, EPA and DHA are g/100 g in
 * Ciqual and mg in the app, hence their factor of 1000.
 *
 * NOT READ, on purpose:
 * - 332 / 333 energy "N x Jones' factor, with fibres": the EU Regulation values
 *   (327 / 328) are the ones on labels;
 * - 25003 protein "N x 6.25": 25000 (Jones' factor) is Ciqual's reference;
 * - 56702 folates as DFE: no field for it in the app;
 * - 71010 alpha-tocopherol: 53100 vitamin E is the total;
 * - 41819 oleic acid: no field for it in the app (omega 9 is a wider total).
 *
 * Pure Java on purpose (no Android types) so it can be unit tested.
 */
public final class CiqualConstituents {

    /** The app field a constituent fills. See CiqualNutritionWriter. */
    public enum Field {
        ENERGY_KJ, ENERGY_KCAL, WATER, ASH, SALT, SODIUM, MAGNESIUM, PHOSPHORUS,
        CHLORIDE, POTASSIUM, CALCIUM, MANGANESE, IRON, COPPER, ZINC, SELENIUM,
        IODINE, PROTEINS, CARBOHYDRATES, SUGARS, FRUCTOSE, GALACTOSE, GLUCOSE,
        LACTOSE, MALTOSE, SUCROSE, STARCH, POLYOLS, FIBER, FAT, SATURATED_FAT,
        MONOUNSATURATED_FAT, POLYUNSATURATED_FAT, BUTYRIC_ACID, CAPROIC_ACID,
        CAPRYLIC_ACID, CAPRIC_ACID, LAURIC_ACID, MYRISTIC_ACID, PALMITIC_ACID,
        STEARIC_ACID, LINOLEIC_ACID, ALA, ARACHIDONIC_ACID, EPA, DHA, VITAMIN_A,
        RETINOL, BETA_CAROTENE, VITAMIN_D, VITAMIN_D2, VITAMIN_D3, VITAMIN_E,
        VITAMIN_K1, VITAMIN_K2, VITAMIN_C, VITAMIN_B1, VITAMIN_B2, VITAMIN_B3,
        VITAMIN_B5, VITAMIN_B6, VITAMIN_B12, VITAMIN_B9, INTRINSIC_FOLATE,
        FOLIC_ACID, ALCOHOL, ORGANIC_ACIDS, CHOLESTEROL
    }

    /** One Ciqual constituent the app reads. */
    public static final class Constituent {
        public final int code;
        public final String nameFr;
        public final String nameEn;
        public final Field field;
        /** Multiplier from Ciqual's unit to the app field's unit (1 or 1000). */
        public final double factor;

        Constituent(int code, String nameFr, String nameEn, Field field, double factor) {
            this.code = code;
            this.nameFr = nameFr;
            this.nameEn = nameEn;
            this.field = field;
            this.factor = factor;
        }

        /** Ciqual's value in the unit of the app's field. */
        public double toAppUnit(double ciqualValue) {
            return ciqualValue * factor;
        }
    }

    /** g/100 g in Ciqual, mg/100 g in the app. */
    private static final double G_TO_MG = 1000;

    private static final List<Constituent> ALL = new ArrayList<>();
    private static final Map<Integer, Constituent> BY_CODE = new HashMap<>();
    private static final Map<String, Constituent> BY_NAME = new HashMap<>();

    static {
        // Energy: the EU Regulation 1169/2011 values
        add(327, "Energie, Règlement UE N° 1169/2011 (kJ/100 g)",
                "Energy, Regulation EU No 1169/2011 (kJ/100g)", Field.ENERGY_KJ);
        add(328, "Energie, Règlement UE N° 1169/2011 (kcal/100 g)",
                "Energy, Regulation EU No 1169/2011 (kcal/100g)", Field.ENERGY_KCAL);

        add(400, "Eau (g/100 g)", "Water (g/100g)", Field.WATER);
        add(10000, "Cendres (g/100 g)", "Ash (g/100g)", Field.ASH);

        // Minerals (mg or µg, as in the app)
        add(10004, "Sel chlorure de sodium (g/100 g)", "Salt (g/100g)", Field.SALT);
        add(10110, "Sodium (mg/100 g)", "Sodium (mg/100g)", Field.SODIUM);
        add(10120, "Magnésium (mg/100 g)", "Magnesium (mg/100g)", Field.MAGNESIUM);
        add(10150, "Phosphore (mg/100 g)", "Phosphorus (mg/100g)", Field.PHOSPHORUS);
        add(10170, "Chlorure (mg/100 g)", "Chloride (mg/100g)", Field.CHLORIDE);
        add(10190, "Potassium (mg/100 g)", "Potassium (mg/100g)", Field.POTASSIUM);
        add(10200, "Calcium (mg/100 g)", "Calcium (mg/100g)", Field.CALCIUM);
        add(10251, "Manganèse (mg/100 g)", "Manganese (mg/100g)", Field.MANGANESE);
        add(10260, "Fer (mg/100 g)", "Iron (mg/100g)", Field.IRON);
        add(10290, "Cuivre (mg/100 g)", "Copper (mg/100g)", Field.COPPER);
        add(10300, "Zinc (mg/100 g)", "Zinc (mg/100g)", Field.ZINC);
        add(10340, "Sélénium (µg/100 g)", "Selenium (µg/100g)", Field.SELENIUM);
        add(10530, "Iode (µg/100 g)", "Iodine (µg/100g)", Field.IODINE);

        // Protein: Jones' factor, Ciqual's reference
        add(25000, "Protéines, N x facteur de Jones (g/100 g)", "Protein (g/100g)", Field.PROTEINS);

        // Carbohydrates and sugars
        add(31000, "Glucides (g/100 g)", "Carbohydrate (g/100g)", Field.CARBOHYDRATES);
        add(32000, "Sucres (g/100 g)", "Sugars (g/100g)", Field.SUGARS);
        add(32210, "Fructose (g/100 g)", "Fructose (g/100g)", Field.FRUCTOSE);
        add(32220, "Galactose (g/100 g)", "Galactose (g/100g)", Field.GALACTOSE);
        add(32250, "Glucose (g/100 g)", "Glucose (g/100g)", Field.GLUCOSE);
        add(32410, "Lactose (g/100 g)", "Lactose (g/100g)", Field.LACTOSE);
        add(32430, "Maltose (g/100 g)", "Maltose (g/100g)", Field.MALTOSE);
        add(32480, "Saccharose (g/100 g)", "Sucrose (g/100g)", Field.SUCROSE);
        add(33110, "Amidon (g/100 g)", "Starch (g/100g)", Field.STARCH);
        add(34000, "Polyols totaux (g/100 g)", "Polyols (g/100g)", Field.POLYOLS);
        add(34100, "Fibres alimentaires (g/100 g)", "Fibres (g/100g)", Field.FIBER);

        // Fat and fatty acids (g, except the four in mg in the app)
        add(40000, "Lipides (g/100 g)", "Fat (g/100g)", Field.FAT);
        add(40302, "AG saturés (g/100 g)", "FA saturated (g/100g)", Field.SATURATED_FAT);
        add(40303, "AG monoinsaturés (g/100 g)", "FA mono (g/100g)", Field.MONOUNSATURATED_FAT);
        add(40304, "AG polyinsaturés (g/100 g)", "FA poly (g/100g)", Field.POLYUNSATURATED_FAT);
        add(40400, "AG 4:0, butyrique (g/100 g)", "FA 4:0 (g/100g)", Field.BUTYRIC_ACID);
        add(40600, "AG 6:0, caproïque (g/100 g)", "FA 6:0 (g/100g)", Field.CAPROIC_ACID);
        add(40800, "AG 8:0, caprylique (g/100 g)", "FA 8:0 (g/100g)", Field.CAPRYLIC_ACID);
        add(41000, "AG 10:0, caprique (g/100 g)", "FA 10:0 (g/100g)", Field.CAPRIC_ACID);
        add(41200, "AG 12:0, laurique (g/100 g)", "FA 12:0 (g/100g)", Field.LAURIC_ACID);
        add(41400, "AG 14:0, myristique (g/100 g)", "FA 14:0 (g/100g)", Field.MYRISTIC_ACID);
        add(41600, "AG 16:0, palmitique (g/100 g)", "FA 16:0 (g/100g)", Field.PALMITIC_ACID);
        add(41800, "AG 18:0, stéarique (g/100 g)", "FA 18:0 (g/100g)", Field.STEARIC_ACID);
        add(41826, "AG 18:2 9c,12c (n-6), linoléique (g/100 g)",
                "FA 18:2 9c,12c (n-6) (g/100g)", Field.LINOLEIC_ACID);
        add(41833, "AG 18:3 c9,c12,c15 (n-3), alpha-linolénique (g/100 g)",
                "FA 18:3 c9,c12,c15 (n-3) (g/100g)", Field.ALA, G_TO_MG);
        add(42046, "AG 20:4 5c,8c,11c,14c (n-6), arachidonique (g/100 g)",
                "FA 20:4 5c,8c,11c,14c (n-6) (g/100g)", Field.ARACHIDONIC_ACID, G_TO_MG);
        add(42053, "AG 20:5 5c,8c,11c,14c,17c (n-3) EPA (g/100 g)",
                "FA 20:5 5c,8c,11c,14c,17c (n-3) EPA (g/100g)", Field.EPA, G_TO_MG);
        add(42263, "AG 22:6 4c,7c,10c,13c,16c,19c (n-3) DHA (g/100 g)",
                "FA 22:6 4c,7c,10c,13c,16c,19c (n-3) DHA (g/100g)", Field.DHA, G_TO_MG);

        // Vitamins
        add(51104, "Activité vitaminique A, équivalents rétinol (µg/100 g)",
                "Vitamin A activity, retinol equivalent (µg/100mg)", Field.VITAMIN_A);
        add(51200, "Rétinol (µg/100 g)", "Retinol (µg/100g)", Field.RETINOL);
        add(51330, "Beta-Carotène (µg/100 g)", "Beta-carotene (µg/100g)", Field.BETA_CAROTENE);
        add(52100, "Vitamine D (µg/100 g)", "Vitamin D (µg/100g)", Field.VITAMIN_D);
        add(52200, "Vitamine D2 (ergocalciférol) (µg/100 g)",
                "Vitamin D2 (ergocalciferol) (µg/100g)", Field.VITAMIN_D2);
        add(52300, "Vitamine D3 (cholécalciférol) (µg/100 g)",
                "Vitamin D3 (cholecalciferol) (µg/100g)", Field.VITAMIN_D3);
        add(53100, "Vitamine E (mg/100 g)", "Vitamin E (mg/100g)", Field.VITAMIN_E);
        add(54101, "Vitamine K1 (µg/100 g)", "Vitamin K1 (µg/100g)", Field.VITAMIN_K1);
        add(54104, "Vitamine K2 (µg/100 g)", "Vitamin K2 (µg/100g)", Field.VITAMIN_K2);
        add(55100, "Vitamine C (mg/100 g)", "Vitamin C (mg/100g)", Field.VITAMIN_C);
        add(56100, "Vitamine B1 ou Thiamine (mg/100 g)",
                "Vitamin B1 or Thiamin (mg/100g)", Field.VITAMIN_B1);
        add(56200, "Vitamine B2 ou Riboflavine (mg/100 g)",
                "Vitamin B2 or Riboflavin (mg/100g)", Field.VITAMIN_B2);
        add(56310, "Vitamine B3 ou PP ou Niacine (mg/100 g)",
                "Vitamin B3 or Niacin (mg/100g)", Field.VITAMIN_B3);
        add(56400, "Vitamine B5 ou Acide pantothénique (mg/100 g)",
                "Vitamin B5 or Pantothenic acid (mg/100g)", Field.VITAMIN_B5);
        add(56500, "Vitamine B6 (mg/100 g)", "Vitamin B6 (mg/100g)", Field.VITAMIN_B6);
        add(56600, "Vitamine B12 (µg/100 g)", "Vitamin B12 (µg/100g)", Field.VITAMIN_B12);
        add(56700, "Vitamine B9 ou Folates totaux (µg/100 g)",
                "Vitamin B9 or total folates (µg/100g)", Field.VITAMIN_B9);
        add(56704, "Folates intrinsèques (µg/100 g)", "Intrinsic folate (µg/100 g)",
                Field.INTRINSIC_FOLATE);
        add(56708, "Acide folique (enrichissement) (µg/100 g)",
                "Folic acid (enrichment) (µg/100g)", Field.FOLIC_ACID);

        // Others
        add(60000, "Alcool (éthanol) (g/100 g)", "Alcohol (g/100g)", Field.ALCOHOL);
        add(65000, "Acides organiques (g/100 g)", "Organic acids (g/100g)", Field.ORGANIC_ACIDS);
        add(75100, "Cholestérol (mg/100 g)", "Cholesterol (mg/100g)", Field.CHOLESTEROL);
    }

    private CiqualConstituents() { /* no instances */ }

    private static void add(int code, String nameFr, String nameEn, Field field) {
        add(code, nameFr, nameEn, field, 1);
    }

    private static void add(int code, String nameFr, String nameEn, Field field, double factor) {
        Constituent c = new Constituent(code, nameFr, nameEn, field, factor);
        ALL.add(c);
        BY_CODE.put(code, c);
        BY_NAME.put(nameKey(nameFr), c);
        BY_NAME.put(nameKey(nameEn), c);
    }

    /** Every constituent the app reads, in the const file's order. */
    public static List<Constituent> all() {
        return Collections.unmodifiableList(ALL);
    }

    /** The constituent with this Ciqual const_code, or null when the app does not read it. */
    public static Constituent byCode(int code) {
        return BY_CODE.get(code);
    }

    /**
     * The constituent with this name, French or English, or null when the app
     * does not read it. Case, accents and spaces are ignored ("100 g" and "100g"
     * compare equal), nothing else: "Vitamine B1 ..." never matches "Vitamine B12
     * ...", and "Sel chlorure de sodium" never matches "Sodium".
     */
    public static Constituent byName(String name) {
        if (name == null) return null;
        return BY_NAME.get(nameKey(name));
    }

    /**
     * Ciqual value ("teneur") as a number, in Ciqual's unit.
     *
     * - "12,5" or "12.5": the number;
     * - "traces": 0 (present in a negligible amount, what a label shows as 0);
     * - "< 0,5" (also "&lt; 0,5" if the XML entity was not decoded): unknown.
     *   It is an upper limit, not a measure, and limits go up to "< 2" g of salt:
     *   storing 0 would sometimes be clearly wrong;
     * - "-", empty or anything else: unknown.
     *
     * @return the value, or null when unknown
     */
    public static Double parseValue(String teneur) {
        if (teneur == null) return null;
        String t = teneur.trim();
        if (t.isEmpty() || t.equals("-")) return null;
        if (t.equalsIgnoreCase("traces")) return 0.0;
        if (t.startsWith("<") || t.startsWith("&lt;")) return null;
        try {
            double v = Double.parseDouble(t.replace(',', '.'));
            return Double.isNaN(v) || Double.isInfinite(v) ? null : v;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Lower case, no accents, no spaces: the key names are compared on. NFKD also
     * folds look-alike characters (the micro sign "µ" and the Greek "μ").
     */
    static String nameKey(String name) {
        String decomposed = Normalizer.normalize(name, Normalizer.Form.NFKD);
        return decomposed.replaceAll("\\p{M}", "")
                .replaceAll("\\s+", "")
                .toLowerCase(Locale.ROOT);
    }
}
