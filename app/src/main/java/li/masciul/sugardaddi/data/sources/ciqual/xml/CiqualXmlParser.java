package li.masciul.sugardaddi.data.sources.ciqual.xml;

import android.content.Context;
import android.util.Log;

import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.core.Persister;
import org.simpleframework.xml.transform.Matcher;
import org.simpleframework.xml.transform.Transform;

import li.masciul.sugardaddi.core.models.*;
import li.masciul.sugardaddi.data.network.ApiConfig;
import li.masciul.sugardaddi.data.sources.ciqual.xml.CiqualXmlModels.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * CiqualXmlParser - Parses Ciqual XML database with GRACEFUL FAILURE
 *
 * STRATEGY:
 * 1. Don't try to fix broken XML - just skip problematic files
 * 2. Make all fields optional for lenient parsing
 * 3. Never crash the app - catch and log all errors
 * 4. Return whatever data we can successfully parse
 * 5. If parsing fails completely, fall back to API
 *
 * This ensures the app remains functional even if Ciqual data is corrupted.
 */
public class CiqualXmlParser {

    private static final String TAG = "CiqualXmlParser";

    private final Context context;
    private final Serializer serializer;

    // Parsed data storage
    private Map<Integer, Aliment> alimentMap = new HashMap<>();
    private Map<Integer, FoodGroup> groupMap = new HashMap<>();
    private Map<Integer, SubGroup> subGroupMap = new HashMap<>();
    private Map<Integer, Constituent> constituentMap = new HashMap<>();
    private Map<String, Source> sourceMap = new HashMap<>();
    private Map<Integer, List<Composition>> compositionMap = new HashMap<>();

    // Statistics for logging
    private int totalFilesAttempted = 0;
    private int totalFilesSucceeded = 0;
    private int totalFilesFailed = 0;

    // Nutrient code mappings (based on Ciqual documentation)
    private static final Map<Integer, String> NUTRIENT_MAPPINGS = new HashMap<>();
    static {
        // Energy
        NUTRIENT_MAPPINGS.put(327, "energy_kj");
        NUTRIENT_MAPPINGS.put(328, "energy_kcal");

        // Macronutrients
        NUTRIENT_MAPPINGS.put(400, "water");
        NUTRIENT_MAPPINGS.put(25000, "proteins");
        NUTRIENT_MAPPINGS.put(31000, "carbohydrates");
        NUTRIENT_MAPPINGS.put(40000, "fat");
        NUTRIENT_MAPPINGS.put(32000, "sugars");
        NUTRIENT_MAPPINGS.put(34100, "fiber");
        NUTRIENT_MAPPINGS.put(10110, "salt");

        // Detailed sugars
        NUTRIENT_MAPPINGS.put(32110, "glucose");
        NUTRIENT_MAPPINGS.put(32120, "fructose");
        NUTRIENT_MAPPINGS.put(32210, "sucrose");
        NUTRIENT_MAPPINGS.put(32130, "lactose");
        NUTRIENT_MAPPINGS.put(32140, "maltose");
        NUTRIENT_MAPPINGS.put(32150, "galactose");
        NUTRIENT_MAPPINGS.put(33110, "starch");

        // Fatty acids
        NUTRIENT_MAPPINGS.put(40302, "saturated_fat");
        NUTRIENT_MAPPINGS.put(40303, "monounsaturated_fat");
        NUTRIENT_MAPPINGS.put(40304, "polyunsaturated_fat");
        NUTRIENT_MAPPINGS.put(75100, "cholesterol");

        // Minerals
        NUTRIENT_MAPPINGS.put(10110, "sodium");
        NUTRIENT_MAPPINGS.put(10120, "calcium");
        NUTRIENT_MAPPINGS.put(10260, "iron");
        NUTRIENT_MAPPINGS.put(10190, "magnesium");
        NUTRIENT_MAPPINGS.put(10200, "phosphorus");
        NUTRIENT_MAPPINGS.put(10150, "potassium");
        NUTRIENT_MAPPINGS.put(10300, "zinc");

        // Vitamins
        NUTRIENT_MAPPINGS.put(51330, "vitamin_a");
        NUTRIENT_MAPPINGS.put(52100, "vitamin_d");
        NUTRIENT_MAPPINGS.put(53100, "vitamin_e");
        NUTRIENT_MAPPINGS.put(54100, "vitamin_k1");
        NUTRIENT_MAPPINGS.put(55100, "vitamin_c");
        NUTRIENT_MAPPINGS.put(56100, "vitamin_b1");
        NUTRIENT_MAPPINGS.put(56200, "vitamin_b2");
        NUTRIENT_MAPPINGS.put(56310, "vitamin_b3");
        NUTRIENT_MAPPINGS.put(56400, "vitamin_b5");
        NUTRIENT_MAPPINGS.put(56500, "vitamin_b6");
        NUTRIENT_MAPPINGS.put(56600, "vitamin_b9");
        NUTRIENT_MAPPINGS.put(56700, "vitamin_b12");
    }

    public CiqualXmlParser(Context context) {
        this.context = context;

        // Create custom matcher for trimmed integer AND double parsing
        Matcher matcher = new Matcher() {
            @Override
            public Transform match(Class type) throws Exception {
                if (type == Integer.class || type == int.class) {
                    return new SafeIntTransform();
                }
                if (type == Double.class || type == double.class) {
                    return new SafeDoubleTransform();
                }
                return null;
            }
        };

        // Create serializer with custom matcher
        this.serializer = new Persister(matcher);

        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, "CiqualXmlParser initialized with lenient parsing");
        }
    }

    /**
     * Parse Ciqual ZIP archive and return unified products
     *
     * GRACEFUL FAILURE: Never throws exceptions, always returns best-effort results
     */
    public ParseResult parseZipArchive(InputStream zipStream) {
        Log.i(TAG, "Starting Ciqual ZIP parsing with graceful failure handling...");

        try {
            // Extract and parse all XML files
            extractAndParseXmlFiles(zipStream);

            // Join data and create unified products
            List<FoodProduct> products = createUnifiedProducts();

            // Create metadata map
            Map<String, CiqualMetadata> metadataMap = createMetadataMap();

            // Log results
            logParsingResults(products.size());

            return new ParseResult(products, metadataMap, totalFilesSucceeded > 0);

        } catch (Exception e) {
            Log.e(TAG, "Fatal error during Ciqual parsing", e);
            // Return empty result instead of crashing
            return new ParseResult(new ArrayList<>(), new HashMap<>(), false);
        }
    }

    /**
     * Extract ZIP and parse each XML file
     *
     * GRACEFUL FAILURE: Catches all exceptions, continues with remaining files
     */
    private void extractAndParseXmlFiles(InputStream zipStream) {
        try (ZipInputStream zis = new ZipInputStream(zipStream, StandardCharsets.ISO_8859_1)) {
            ZipEntry entry;

            while ((entry = zis.getNextEntry()) != null) {
                String fileName = entry.getName().toLowerCase();
                totalFilesAttempted++;

                // Skip non-XML files
                if (!fileName.endsWith(".xml")) {
                    Log.d(TAG, "Skipping non-XML file: " + fileName);
                    zis.closeEntry();
                    continue;
                }

                Log.d(TAG, "Processing: " + fileName);

                try {
                    // Read file content (with size limit to prevent OOM)
                    byte[] xmlBytes = readZipEntryWithLimit(zis, 50 * 1024 * 1024); // 50MB limit

                    if (xmlBytes == null) {
                        Log.w(TAG, "File too large or empty, skipping: " + fileName);
                        totalFilesFailed++;
                        zis.closeEntry();
                        continue;
                    }

                    // Convert encoding (ISO-8859-1 to UTF-8)
                    String xmlContent = new String(xmlBytes, StandardCharsets.ISO_8859_1);
                    InputStream xmlStream = new ByteArrayInputStream(
                            xmlContent.getBytes(StandardCharsets.UTF_8)
                    );

                    // Parse based on file type
                    boolean success = parseFileByType(xmlStream, fileName);

                    if (success) {
                        totalFilesSucceeded++;
                    } else {
                        totalFilesFailed++;
                    }

                } catch (OutOfMemoryError e) {
                    Log.e(TAG, "OUT OF MEMORY parsing " + fileName + " - SKIPPING");
                    totalFilesFailed++;
                    // Force garbage collection
                    System.gc();

                } catch (Exception e) {
                    Log.e(TAG, "Error parsing " + fileName + ": " + e.getMessage());
                    totalFilesFailed++;
                }

                zis.closeEntry();
            }

        } catch (Exception e) {
            Log.e(TAG, "Error reading ZIP archive", e);
        }
    }

    /**
     * Read ZIP entry with size limit to prevent OOM
     */
    private byte[] readZipEntryWithLimit(ZipInputStream zis, int maxSize) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192]; // 8KB buffer
        int totalRead = 0;
        int len;

        while ((len = zis.read(buffer)) > 0) {
            totalRead += len;

            // Prevent OOM by limiting file size
            if (totalRead > maxSize) {
                Log.w(TAG, "File exceeds size limit (" + maxSize + " bytes)");
                return null;
            }

            baos.write(buffer, 0, len);
        }

        return baos.toByteArray();
    }

    /**
     * Parse file based on its type
     * Returns true if successful, false otherwise
     */
    private boolean parseFileByType(InputStream stream, String fileName) {
        try {
            if (fileName.contains("alim_grp")) {
                return parseGroupsXml(stream, fileName);
            } else if (fileName.contains("alim") && !fileName.contains("grp")) {
                return parseAlimentsXml(stream, fileName);
            } else if (fileName.contains("compo")) {
                return parseCompositionXml(stream, fileName);
            } else if (fileName.contains("const")) {
                return parseConstituentsXml(stream, fileName);
            } else if (fileName.contains("sources")) {
                return parseSourcesXml(stream, fileName);
            } else {
                Log.d(TAG, "Unrecognized file type: " + fileName);
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "Parse error in " + fileName + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Parse aliments (food items)
     * Returns true if successful
     */
    private boolean parseAlimentsXml(InputStream stream, String fileName) {
        try {
            AlimTable table = serializer.read(AlimTable.class, stream);
            if (table.aliments != null) {
                for (Aliment aliment : table.aliments) {
                    if (aliment != null) {
                        alimentMap.put(aliment.code, aliment);
                    }
                }
            }
            Log.d(TAG, "✓ Parsed " + alimentMap.size() + " aliments from " + fileName);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "✗ Failed to parse aliments: " + e.getMessage());
            return false;
        }
    }

    /**
     * Parse food groups
     * Returns true if successful
     */
    private boolean parseGroupsXml(InputStream stream, String fileName) {
        try {
            GroupTable table = serializer.read(GroupTable.class, stream);
            for (FoodGroup group : table.getAllGroups()) {
                if (group != null) {
                    groupMap.put(group.code, group);
                }
            }
            Log.d(TAG, "✓ Parsed " + groupMap.size() + " groups from " + fileName);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "✗ Failed to parse groups: " + e.getMessage());
            return false;
        }
    }

    /**
     * Parse composition (nutrition data)
     * Returns true if successful
     */
    private boolean parseCompositionXml(InputStream stream, String fileName) {
        try {
            CompoTable table = serializer.read(CompoTable.class, stream);
            if (table.compositions != null) {
                for (Composition comp : table.compositions) {
                    if (comp != null) {
                        compositionMap.computeIfAbsent(comp.alimentCode, k -> new ArrayList<>())
                                .add(comp);
                    }
                }
            }
            Log.d(TAG, "✓ Parsed composition for " + compositionMap.size() + " products");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "✗ Failed to parse composition: " + e.getMessage());
            return false;
        }
    }

    /**
     * Parse constituents (nutrient definitions)
     * Returns true if successful
     */
    private boolean parseConstituentsXml(InputStream stream, String fileName) {
        try {
            ConstTable table = serializer.read(ConstTable.class, stream);
            if (table.constituents != null) {
                for (Constituent constituent : table.constituents) {
                    if (constituent != null) {
                        constituentMap.put(constituent.code, constituent);
                    }
                }
            }
            Log.d(TAG, "✓ Parsed " + constituentMap.size() + " constituents");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "✗ Failed to parse constituents: " + e.getMessage());
            return false;
        }
    }

    /**
     * Parse sources
     * Returns true if successful
     */
    private boolean parseSourcesXml(InputStream stream, String fileName) {
        try {
            SourceTable table = serializer.read(SourceTable.class, stream);
            if (table.sources != null) {
                for (Source source : table.sources) {
                    if (source != null) {
                        sourceMap.put(source.code, source);
                    }
                }
            }
            Log.d(TAG, "✓ Parsed " + sourceMap.size() + " sources");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "✗ Failed to parse sources: " + e.getMessage());
            return false;
        }
    }

    /**
     * Create unified FoodProduct objects by joining all data
     */
    private List<FoodProduct> createUnifiedProducts() {
        List<FoodProduct> products = new ArrayList<>();

        for (Aliment aliment : alimentMap.values()) {
            try {
                FoodProduct product = createProductFromAliment(aliment);
                if (product != null) {
                    products.add(product);
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to create product for aliment " + aliment.code + ": " + e.getMessage());
            }
        }

        return products;
    }

    /**
     * Create a single FoodProduct from aliment and related data
     */
    private FoodProduct createProductFromAliment(Aliment aliment) {
        if (aliment.nameFr == null || aliment.nameFr.trim().isEmpty()) {
            return null; // Skip products without names
        }

        String code = String.valueOf(aliment.code);
        FoodProduct product = new FoodProduct("CIQUAL", code);

        // Set French name and categories
        product.setName(aliment.nameFr, "fr");

        // Build category hierarchy
        String categoryHierarchy = buildCategoryHierarchy(aliment);
        if (categoryHierarchy != null) {
            product.setCategoriesText(categoryHierarchy, "fr");
        }

        // Build category list as List<String>
        List<String> categoryStrings = buildCategoryListStrings(aliment);
        product.setCategoryHierarchy(categoryStrings);

        // Set English name if available
        if (aliment.nameEn != null && !aliment.nameEn.trim().isEmpty()) {
            product.setName(aliment.nameEn, "en");

            String englishCategoryHierarchy = buildCategoryHierarchyEnglish(aliment);
            if (englishCategoryHierarchy != null) {
                product.setCategoriesText(englishCategoryHierarchy, "en");
            }
        }

        // Set nutrition data
        Nutrition nutrition = buildNutrition(aliment.code);
        product.setNutrition(nutrition);

        // Set metadata
        product.setLastUpdated(System.currentTimeMillis());

        return product;
    }

    /**
     * Build French category hierarchy string
     */
    private String buildCategoryHierarchy(Aliment aliment) {
        List<String> parts = new ArrayList<>();

        FoodGroup group = groupMap.get(aliment.groupCode);
        if (group != null && group.nameFr != null) {
            parts.add(group.nameFr);
        }

        if (aliment.subgroupCode != null) {
            SubGroup subGroup = subGroupMap.get(aliment.subgroupCode);
            if (subGroup != null && subGroup.nameFr != null) {
                parts.add(subGroup.nameFr);
            }
        }

        return parts.isEmpty() ? null : String.join(" > ", parts);
    }

    /**
     * Build English category hierarchy string
     */
    private String buildCategoryHierarchyEnglish(Aliment aliment) {
        List<String> parts = new ArrayList<>();

        FoodGroup group = groupMap.get(aliment.groupCode);
        if (group != null && group.nameEn != null) {
            parts.add(group.nameEn);
        }

        if (aliment.subgroupCode != null) {
            SubGroup subGroup = subGroupMap.get(aliment.subgroupCode);
            if (subGroup != null && subGroup.nameEn != null) {
                parts.add(subGroup.nameEn);
            }
        }

        return parts.isEmpty() ? null : String.join(" > ", parts);
    }

    /**
     * Build category list as List<String>
     */
    private List<String> buildCategoryListStrings(Aliment aliment) {
        List<String> categories = new ArrayList<>();

        FoodGroup group = groupMap.get(aliment.groupCode);
        if (group != null && group.nameFr != null) {
            categories.add(group.nameFr);
        }

        if (aliment.subgroupCode != null) {
            SubGroup subGroup = subGroupMap.get(aliment.subgroupCode);
            if (subGroup != null && subGroup.nameFr != null) {
                categories.add(subGroup.nameFr);
            }
        }

        return categories;
    }

    /**
     * Build nutrition from composition data
     */
    private Nutrition buildNutrition(int alimentCode) {
        Nutrition nutrition = new Nutrition();
        List<Composition> compositions = compositionMap.get(alimentCode);

        if (compositions == null) {
            return nutrition;
        }

        for (Composition comp : compositions) {
            try {
                String nutrientKey = NUTRIENT_MAPPINGS.get(comp.constituentCode);
                if (nutrientKey != null) {
                    Double value = parseNutrientValue(comp.value);
                    if (value != null) {
                        setNutrientValue(nutrition, nutrientKey, value);
                    }
                }
            } catch (Exception e) {
                // Skip problematic nutrients
                Log.w(TAG, "Failed to parse nutrient: " + e.getMessage());
            }
        }

        nutrition.setDataSource("Ciqual");
        nutrition.calculateCompleteness();

        return nutrition;
    }

    /**
     * Parse nutrient value
     */
    private Double parseNutrientValue(String value) {
        if (value == null || value.trim().isEmpty() || value.equals("-")) {
            return null;
        }

        String trimmed = value.trim();

        if (trimmed.equalsIgnoreCase("traces")) {
            return 0.01;
        }

        try {
            trimmed = trimmed.replace(',', '.');
            return Double.parseDouble(trimmed);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Set nutrient value in Nutrition object
     */
    private void setNutrientValue(Nutrition nutrition, String key, Double value) {
        switch (key) {
            case "energy_kcal": nutrition.setEnergyKcal(value); break;
            case "energy_kj": nutrition.setEnergyKj(value); break;
            case "water": nutrition.setWater(value); break;
            case "proteins": nutrition.setProteins(value); break;
            case "carbohydrates": nutrition.setCarbohydrates(value); break;
            case "fat": nutrition.setFat(value); break;
            case "sugars": nutrition.setSugars(value); break;
            case "fiber": nutrition.setFiber(value); break;
            case "salt": nutrition.setSalt(value); break;
            case "saturated_fat": nutrition.setSaturatedFat(value); break;
            case "sodium": nutrition.setSodium(value); break;
            case "calcium": nutrition.setCalcium(value); break;
            case "iron": nutrition.setIron(value); break;
            case "magnesium": nutrition.setMagnesium(value); break;
            case "phosphorus": nutrition.setPhosphorus(value); break;
            case "potassium": nutrition.setPotassium(value); break;
            case "zinc": nutrition.setZinc(value); break;
        }
    }

    /**
     * Create metadata map for source attribution
     */
    private Map<String, CiqualMetadata> createMetadataMap() {
        Map<String, CiqualMetadata> metadataMap = new HashMap<>();

        for (Map.Entry<Integer, List<Composition>> entry : compositionMap.entrySet()) {
            try {
                String productId = "CIQUAL:" + entry.getKey();
                CiqualMetadata metadata = new CiqualMetadata();
                metadata.productId = productId;
                metadata.originalCode = String.valueOf(entry.getKey());
                metadata.sources = new ArrayList<>();
                metadata.importDate = System.currentTimeMillis();

                metadataMap.put(productId, metadata);
            } catch (Exception e) {
                // Skip problematic metadata
                Log.w(TAG, "Failed to create metadata: " + e.getMessage());
            }
        }

        return metadataMap;
    }

    /**
     * Log parsing results
     */
    private void logParsingResults(int productsCreated) {
        Log.i(TAG, "═══════════════════════════════════════════");
        Log.i(TAG, "Ciqual Parsing Results:");
        Log.i(TAG, "  Files attempted: " + totalFilesAttempted);
        Log.i(TAG, "  Files succeeded: " + totalFilesSucceeded);
        Log.i(TAG, "  Files failed: " + totalFilesFailed);
        Log.i(TAG, "  Success rate: " + (totalFilesAttempted > 0 ?
                (totalFilesSucceeded * 100 / totalFilesAttempted) : 0) + "%");
        Log.i(TAG, "  Products created: " + productsCreated);
        Log.i(TAG, "  Aliments parsed: " + alimentMap.size());
        Log.i(TAG, "  Groups parsed: " + groupMap.size());
        Log.i(TAG, "  Nutrients parsed: " + constituentMap.size());
        Log.i(TAG, "═══════════════════════════════════════════");
    }

    /**
     * Result container with success flag
     */
    public static class ParseResult {
        public final List<FoodProduct> products;
        public final Map<String, CiqualMetadata> metadata;
        public final boolean success; // Indicates if ANY data was parsed

        public ParseResult(List<FoodProduct> products, Map<String, CiqualMetadata> metadata, boolean success) {
            this.products = products;
            this.metadata = metadata;
            this.success = success;
        }
    }
}