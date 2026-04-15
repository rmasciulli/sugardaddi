package li.masciul.sugardaddi.data.sources.ciqual.mappers;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.util.Log;

import li.masciul.sugardaddi.core.enums.DataSource;
import li.masciul.sugardaddi.core.models.Category;
import li.masciul.sugardaddi.core.models.FoodProduct;
import li.masciul.sugardaddi.core.models.Nutrition;
import li.masciul.sugardaddi.core.models.ProductTranslation;
import li.masciul.sugardaddi.core.models.SourceIdentifier;
import li.masciul.sugardaddi.data.sources.ciqual.CiqualConstants;
import li.masciul.sugardaddi.data.sources.ciqual.xml.CiqualCategoryLookup;
import li.masciul.sugardaddi.data.sources.ciqual.api.dto.CiqualElasticsearchComposition;
import li.masciul.sugardaddi.data.sources.ciqual.api.dto.CiqualElasticsearchFood;
import li.masciul.sugardaddi.data.sources.ciqual.api.dto.CiqualElasticsearchHit;
import li.masciul.sugardaddi.data.sources.ciqual.api.dto.CiqualElasticsearchResponse;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * CiqualElasticsearchMapper - Maps Elasticsearch responses to domain models
 *
 * Converts Ciqual Elasticsearch DTOs to FoodProduct domain objects.
 * Handles both search results and detailed product queries.
 *
 * VERSION 3.0 - COMPOS ARRAY PARSING:
 * ====================================
 * CRITICAL FIX: The Ciqual Elasticsearch API does NOT return nutrition as direct
 * fields (energie_kcal, proteines, etc.). Instead, ALL nutrition data is in a
 * "compos" array where each entry represents one nutrient.
 *
 * This version properly parses the compos array by matching nutrient names against
 * patterns to extract values.
 *
 * MAPPING MODES:
 * ==============
 * 1. SEARCH RESULTS: Basic info only (no nutrition)
 *    - compos excluded via _source.excludes for performance
 *    - Maps code, names in both languages, categories
 *    - Lightweight for list display
 *
 * 2. PRODUCT DETAILS: Complete info with nutrition
 *    - compos included in response
 *    - Parses compos array to build Nutrition object
 *    - Comprehensive for detail views
 *
 * COMPOS PARSING STRATEGY:
 * ========================
 * Each CiqualElasticsearchComposition has constNomFr and constNomEng fields containing
 * the nutrient name with unit. We use regex patterns to match these:
 *
 * - "Energie.*kcal" â†’ nutrition.setEnergyKcal()
 * - "Energie.*kJ" â†’ nutrition.setEnergyKj()
 * - "ProtÃ©ines|Protein" â†’ nutrition.setProteins()
 * - "Glucides|Carbohydrate" â†’ nutrition.setCarbohydrates()
 * - "Lipides|Fat" â†’ nutrition.setFat()
 * - etc.
 *
 * USAGE:
 * ```java
 * // Map search results in French (no nutrition)
 * List<FoodProduct> products = CiqualElasticsearchMapper.mapSearchResponse(
 *     response, "fr"
 * );
 *
 * // Map single product with nutrition in English
 * FoodProduct product = CiqualElasticsearchMapper.mapToFoodProduct(
 *     ciqualFood, "en", true
 * );
 * ```
 *
 * @see CiqualElasticsearchResponse
 * @see CiqualElasticsearchFood
 * @see CiqualElasticsearchComposition
 * @see FoodProduct
 * @author SugarDaddi Team
 * @version 3.0 (Compos Array Parsing Fix)
 */
public class CiqualElasticsearchMapper {

    private static final String TAG = "CiqualESMapper";

    // ========== NUTRIENT NAME PATTERNS ==========
    // Patterns to match nutrient names in constNomFr and constNomEng fields
    // Case-insensitive matching to handle variations

    // Energy patterns
    private static final Pattern PATTERN_ENERGY_KCAL = Pattern.compile(
            "energie.*kcal|energy.*kcal",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern PATTERN_ENERGY_KJ = Pattern.compile(
            "energie.*kj|energy.*kj",
            Pattern.CASE_INSENSITIVE
    );

    // Macronutrients
    private static final Pattern PATTERN_PROTEIN = Pattern.compile(
            "prot[Ã©e]ines|protein",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern PATTERN_CARBS = Pattern.compile(
            "glucides|carbohydrate",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern PATTERN_FAT = Pattern.compile(
            "lipides(?!.*insatur)|^fat(?!.*fatty)|total fat",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern PATTERN_SUGAR = Pattern.compile(
            "sucres|sugar",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern PATTERN_FIBER = Pattern.compile(
            "fibres|fiber|fibre",
            Pattern.CASE_INSENSITIVE
    );

    // Specific fats
    private static final Pattern PATTERN_SATURATED_FAT = Pattern.compile(
            "ag satur[Ã©e]s|saturated",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern PATTERN_MONOUNSAT_FAT = Pattern.compile(
            "ag monoinsatur[Ã©e]s|monounsaturated",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern PATTERN_POLYUNSAT_FAT = Pattern.compile(
            "ag polyinsatur[Ã©e]s|polyunsaturated",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern PATTERN_CHOLESTEROL = Pattern.compile(
            "cholest[Ã©e]rol|cholesterol",
            Pattern.CASE_INSENSITIVE
    );

    // Minerals
    private static final Pattern PATTERN_SODIUM = Pattern.compile(
            "sodium",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern PATTERN_SALT = Pattern.compile(
            "sel chlorure|^sel$|^salt$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern PATTERN_CALCIUM = Pattern.compile(
            "calcium",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern PATTERN_MAGNESIUM = Pattern.compile(
            "magn[Ã©e]sium|magnesium",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern PATTERN_PHOSPHORUS = Pattern.compile(
            "phosphore|phosphorus",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern PATTERN_POTASSIUM = Pattern.compile(
            "potassium",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern PATTERN_IRON = Pattern.compile(
            "^fer$|^iron$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern PATTERN_ZINC = Pattern.compile(
            "zinc",
            Pattern.CASE_INSENSITIVE
    );

    // Vitamins
    private static final Pattern PATTERN_VITAMIN_C = Pattern.compile(
            "vitamine c|vitamin c|acide ascorbique|ascorbic",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern PATTERN_VITAMIN_B1 = Pattern.compile(
            "vitamine b1|vitamin b1|thiamine",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern PATTERN_VITAMIN_B2 = Pattern.compile(
            "vitamine b2|vitamin b2|riboflavine",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern PATTERN_VITAMIN_B3 = Pattern.compile(
            "vitamine b3|vitamin b3|niacine|niacin",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern PATTERN_VITAMIN_B5 = Pattern.compile(
            "vitamine b5|vitamin b5|pantoth[Ã©e]nique|pantothenic",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern PATTERN_VITAMIN_B6 = Pattern.compile(
            "vitamine b6|vitamin b6|pyridoxine",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern PATTERN_VITAMIN_B9 = Pattern.compile(
            "vitamine b9|vitamin b9|folates|folate|acide folique|folic",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern PATTERN_VITAMIN_B12 = Pattern.compile(
            "vitamine b12|vitamin b12|cobalamine|cobalamin",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern PATTERN_VITAMIN_A = Pattern.compile(
            "vitamine a|vitamin a|r[Ã©e]tinol|retinol",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern PATTERN_VITAMIN_D = Pattern.compile(
            "vitamine d|vitamin d",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern PATTERN_VITAMIN_E = Pattern.compile(
            "vitamine e|vitamin e|tocoph[Ã©e]rol|tocopherol",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern PATTERN_VITAMIN_K = Pattern.compile(
            "vitamine k|vitamin k",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Maps Elasticsearch search response to list of FoodProducts
     *
     * Extracts hits from response and converts each to a FoodProduct.
     * Returns lightweight products suitable for list display (no nutrition).
     *
     * @param response Elasticsearch response
     * @param language Requested language ("fr" or "en")
     * @return List of FoodProducts (empty if no results)
     */
    @NonNull
    public static List<FoodProduct> mapSearchResponse(
            @Nullable CiqualElasticsearchResponse response,
            @NonNull String language) {

        List<FoodProduct> products = new ArrayList<>();

        if (response == null || !response.hasResults()) {
            return products;
        }

        for (CiqualElasticsearchHit hit : response.getHits()) {
            if (hit.hasSource()) {
                FoodProduct product = mapToFoodProduct(
                        hit.getSource(),
                        language,
                        true   // compos included in search results (no longer excluded)
                );

                if (product != null) {
                    products.add(product);
                }
            }
        }

        return products;
    }

    /**
     * Maps CiqualElasticsearchFood DTO to FoodProduct domain model
     *
     * Creates a complete FoodProduct with:
     * - Basic identification (code, names in both FR and EN)
     * - Categories (in both languages)
     * - Translations map (stores non-current language)
     * - Nutrition data (if available and includeNutrition=true)
     * - Proper language tracking (currentLanguage, needsDefaultLanguageUpdate)
     *
     * NUTRITION PARSING:
     * If includeNutrition=true and compos array exists, this method:
     * 1. Iterates through all CiqualElasticsearchComposition entries
     * 2. Matches constNomFr/constNomEng against patterns
     * 3. Extracts numeric values and populates Nutrition object
     *
     * @param food CiqualElasticsearchFood DTO from Elasticsearch
     * @param language Requested language ("fr" or "en")
     * @param includeNutrition Whether to parse nutrition data from compos
     * @return FoodProduct, or null if mapping fails
     */
    @Nullable
    public static FoodProduct mapToFoodProduct(
            @Nullable CiqualElasticsearchFood food,
            @NonNull String language,
            boolean includeNutrition) {

        if (food == null || food.getCode() == null) {
            return null;
        }

        // Normalize language
        final String effectiveLanguage = normalizeLanguage(language);

        // Build translations map (contains the NON-current language)
        Map<String, ProductTranslation> translations = buildTranslations(food, effectiveLanguage);

        // Extract main category
        Category category = extractCategory(food);

        // Map nutrition if requested and available
        Nutrition nutrition = null;
        if (includeNutrition && food.hasNutritionData()) {
            Log.d(TAG, "Parsing nutrition from compos array for code: " + food.getCode());
            nutrition = mapNutritionFromCompos(food);

            if (nutrition != null) {
                Log.d(TAG, "Nutrition parsed successfully: " + nutrition.getEnergyKcal() + " kcal");
            } else {
                Log.w(TAG, "Failed to parse nutrition from compos");
            }
        }

        // Get name and category in requested language
        String displayName = food.getName(effectiveLanguage);
        String categoryText = getCategoryText(food, effectiveLanguage);

        // Create FoodProduct
        FoodProduct product = new FoodProduct();

        // Set core identification
        product.setOriginalId(food.getCode());  // Ciqual code (e.g., "13004")

        // Set currentLanguage first
        product.setCurrentLanguage(effectiveLanguage);
        // Set name in current language using language-aware setter
        product.setName(displayName, effectiveLanguage);

        product.setBrand(null);  // Ciqual doesn't have brands

        // Set category text in current language
        if (categoryText != null && !categoryText.isEmpty()) {
            product.setCategoriesText(categoryText, effectiveLanguage);
        }

        // Set translations (already excludes current language)
        product.setTranslations(translations);

        // Flag if we need to fetch English translation later
        if (!"en".equals(effectiveLanguage)) {
            product.setNeedsDefaultLanguageUpdate(true);
        }

        // Set categories list (Category objects don't have translations yet)
        if (category != null) {
            product.setCategoryList(Arrays.asList(category));
        } else {
            product.setCategoryList(new ArrayList<>());
        }

        // Set data source
        product.setDataSource(DataSource.fromString(CiqualConstants.SOURCE_ID));

        // Set source identifier
        product.setSourceIdentifier(new SourceIdentifier(CiqualConstants.SOURCE_ID, food.getCode()));

        // Ciqual doesn't support barcodes
        product.setBarcode(null);

        // Image URL not available in Elasticsearch response
        product.setImageUrl(null);

        // Set nutrition if available
        if (nutrition != null) {
            product.setNutrition(nutrition);
        }

        // Calculate data completeness so Ciqual products rank correctly in
        // SmartMergeStrategy.sortByRelevance() alongside OFF products.
        // Without this, Ciqual completeness stays 0.0 and they always sink to last.
        product.calculateCompleteness();

        return product;
    }

    // ========== PRIVATE: TRANSLATIONS ==========

    /**
     * Builds translations map from CiqualElasticsearchFood
     *
     * The translations map contains the NON-current language:
     * - If current language is "fr", translations["en"] gets English data
     * - If current language is "en", translations["fr"] gets French data
     *
     * This follows FoodProduct's language architecture where:
     * - Primary fields store content in currentLanguage
     * - translations map stores OTHER languages
     *
     * @param food CiqualElasticsearchFood with both FR and EN data
     * @param currentLanguage The language being used for primary fields
     * @return Translations map (contains other language)
     */
    @NonNull
    private static Map<String, ProductTranslation> buildTranslations(
            @NonNull CiqualElasticsearchFood food,
            @NonNull String currentLanguage) {

        Map<String, ProductTranslation> translations = new HashMap<>();

        // If current language is English, store French in translations
        if ("en".equals(currentLanguage)) {
            if (food.getNomFr() != null && !food.getNomFr().isEmpty()) {
                ProductTranslation frTranslation = new ProductTranslation(
                        food.getNomFr(),  // name
                        null,             // brand (Ciqual doesn't have brands)
                        null              // description (not in Elasticsearch)
                );

                // Add French category if available
                if (food.getGroupeAfficheFr() != null && !food.getGroupeAfficheFr().isEmpty()) {
                    frTranslation.setCategories(food.getGroupeAfficheFr());
                }

                translations.put("fr", frTranslation);
            }
        }
        // If current language is French, store English in translations
        else if ("fr".equals(currentLanguage)) {
            if (food.getNomEng() != null && !food.getNomEng().isEmpty()) {
                ProductTranslation enTranslation = new ProductTranslation(
                        food.getNomEng(),  // name
                        null,              // brand (Ciqual doesn't have brands)
                        null               // description (not in Elasticsearch)
                );

                // Add English category if available
                if (food.getGroupeAfficheEng() != null && !food.getGroupeAfficheEng().isEmpty()) {
                    enTranslation.setCategories(food.getGroupeAfficheEng());
                }

                translations.put("en", enTranslation);
            }
        }

        return translations;
    }

    // ========== PRIVATE: CATEGORY EXTRACTION ==========

    /**
     * Extracts category from Ciqual food data
     *
     * Prefers English category name, falls back to French if English not available.
     * This ensures the Category object has the most internationally useful name.
     *
     * Note: Category objects don't support translations yet, so we pick the "best" name.
     * Full translation support is provided via ProductTranslation.categories field.
     *
     * @param food CiqualElasticsearchFood with category data
     * @return Category, or null if no category data
     */
    @Nullable
    private static Category extractCategory(@NonNull CiqualElasticsearchFood food) {
        String categoryEn = food.getGroupeAfficheEng();
        String categoryFr = food.getGroupeAfficheFr();
        String categoryCode = food.getTopCategoryCode();

        // Prefer English, fallback to French
        String categoryName = (categoryEn != null && !categoryEn.isEmpty())
                ? categoryEn
                : categoryFr;

        if (categoryName == null && categoryCode == null) {
            return null;
        }

        Category category = new Category(
                categoryName != null ? categoryName : "Unknown",
                categoryCode != null ? categoryCode : "unknown"
        );

        return category;
    }

    /**
     * Gets category text in specified language.
     *
     * PHASE 2A: First attempts to resolve the full category hierarchy from
     * CiqualCategoryLookup using the grps[] codes in the ES response.
     * This produces richer breadcrumbs like "sugar and confectionery > breakfast cereals"
     * instead of just "breakfast cereals" (groupeAfficheEng alone).
     *
     * Falls back gracefully to groupeAfficheEng/Fr if:
     * - CiqualCategoryLookup is not yet initialized (app just started)
     * - The grps[] codes have no match in the lookup table
     * - The food has no grps[] data
     *
     * @param food     CiqualElasticsearchFood with category data and grps[] codes
     * @param language "en" or "fr"
     * @return Category breadcrumb string, or flat fallback, or null
     */
    @Nullable
    private static String getCategoryText(@NonNull CiqualElasticsearchFood food,
                                          @NonNull String language) {
        // Phase 2A: try the hierarchy lookup first
        CiqualCategoryLookup lookup = CiqualCategoryLookup.getInstance();
        if (lookup.isReady() && !food.getGroups().isEmpty()) {
            String hierarchy = lookup.getCategoryHierarchy(food.getGroups(), language);
            if (hierarchy != null && !hierarchy.isEmpty()) {
                return hierarchy;
            }
        }

        // Fallback: flat groupeAfficheEng/Fr from ES response
        // Always available — was the only source before Phase 2A.
        if ("en".equals(language)) {
            String categoryEn = food.getGroupeAfficheEng();
            if (categoryEn != null && !categoryEn.isEmpty()) {
                return categoryEn;
            }
            return food.getGroupeAfficheFr();
        } else {
            String categoryFr = food.getGroupeAfficheFr();
            if (categoryFr != null && !categoryFr.isEmpty()) {
                return categoryFr;
            }
            return food.getGroupeAfficheEng();
        }
    }

    // ========== PRIVATE: NUTRITION PARSING FROM COMPOS ==========

    /**
     * Maps nutrition data from compos array to Nutrition domain model
     *
     * CRITICAL: This is where the actual parsing happens!
     *
     * The Ciqual API returns nutrition as an array of CiqualElasticsearchComposition objects.
     * Each composition has:
     * - compoTeneur: The value (e.g., "492", "8.7")
     * - constNomFr: French name (e.g., "Energie...kcal", "ProtÃ©ines")
     * - constNomEng: English name (e.g., "Energy...kcal", "Protein")
     *
     * This method:
     * 1. Iterates through all compositions
     * 2. Matches constNomFr/constNomEng against regex patterns
     * 3. Extracts numeric values using getValueAsDouble()
     * 4. Populates the Nutrition object
     *
     * All values are per 100g (Ciqual standard).
     *
     * @param food CiqualElasticsearchFood with compos array
     * @return Nutrition object, or null if no valid data
     */
    @Nullable
    private static Nutrition mapNutritionFromCompos(@NonNull CiqualElasticsearchFood food) {
        List<CiqualElasticsearchComposition> compos = food.getCompos();

        if (compos == null || compos.isEmpty()) {
            Log.d(TAG, "No compos data available");
            return null;
        }

        Log.d(TAG, "Processing " + compos.size() + " composition entries");
        Nutrition nutrition = new Nutrition();
        int matchedCount = 0;

        // Iterate through all composition entries
        for (CiqualElasticsearchComposition comp : compos) {
            if (comp == null || !comp.hasValue()) {
                continue;
            }

            String nameFr = comp.getConstNomFr();
            String nameEn = comp.getConstNomEng();
            Double value = comp.getValueAsDouble();

            if (value == null) {
                continue;
            }

            // Try to match against known patterns
            // Check both French and English names for maximum compatibility

            // Energy
            if (matches(PATTERN_ENERGY_KCAL, nameFr, nameEn)) {
                nutrition.setEnergyKcal(value);
                matchedCount++;
                Log.d(TAG, "Matched energy kcal: " + value);
            } else if (matches(PATTERN_ENERGY_KJ, nameFr, nameEn)) {
                nutrition.setEnergyKj(value);
                matchedCount++;
                Log.d(TAG, "Matched energy kJ: " + value);
            }
            // Macronutrients
            else if (matches(PATTERN_PROTEIN, nameFr, nameEn)) {
                nutrition.setProteins(value);
                matchedCount++;
            } else if (matches(PATTERN_CARBS, nameFr, nameEn)) {
                nutrition.setCarbohydrates(value);
                matchedCount++;
            } else if (matches(PATTERN_FAT, nameFr, nameEn)) {
                nutrition.setFat(value);
                matchedCount++;
            } else if (matches(PATTERN_SUGAR, nameFr, nameEn)) {
                nutrition.setSugars(value);
                matchedCount++;
            } else if (matches(PATTERN_FIBER, nameFr, nameEn)) {
                nutrition.setFiber(value);
                matchedCount++;
            }
            // Specific fats
            else if (matches(PATTERN_SATURATED_FAT, nameFr, nameEn)) {
                nutrition.setSaturatedFat(value);
                matchedCount++;
            } else if (matches(PATTERN_MONOUNSAT_FAT, nameFr, nameEn)) {
                nutrition.setMonounsaturatedFat(value);
                matchedCount++;
            } else if (matches(PATTERN_POLYUNSAT_FAT, nameFr, nameEn)) {
                nutrition.setPolyunsaturatedFat(value);
                matchedCount++;
            } else if (matches(PATTERN_CHOLESTEROL, nameFr, nameEn)) {
                nutrition.setCholesterol(value);
                matchedCount++;
            }
            // Minerals
            else if (matches(PATTERN_SODIUM, nameFr, nameEn)) {
                nutrition.setSodium(value);
                matchedCount++;
            } else if (matches(PATTERN_SALT, nameFr, nameEn)) {
                nutrition.setSalt(value);
                matchedCount++;
            } else if (matches(PATTERN_CALCIUM, nameFr, nameEn)) {
                nutrition.setCalcium(value);
                matchedCount++;
            } else if (matches(PATTERN_MAGNESIUM, nameFr, nameEn)) {
                nutrition.setMagnesium(value);
                matchedCount++;
            } else if (matches(PATTERN_PHOSPHORUS, nameFr, nameEn)) {
                nutrition.setPhosphorus(value);
                matchedCount++;
            } else if (matches(PATTERN_POTASSIUM, nameFr, nameEn)) {
                nutrition.setPotassium(value);
                matchedCount++;
            } else if (matches(PATTERN_IRON, nameFr, nameEn)) {
                nutrition.setIron(value);
                matchedCount++;
            } else if (matches(PATTERN_ZINC, nameFr, nameEn)) {
                nutrition.setZinc(value);
                matchedCount++;
            }
            // Vitamins
            else if (matches(PATTERN_VITAMIN_C, nameFr, nameEn)) {
                nutrition.setVitaminC(value);
                matchedCount++;
            } else if (matches(PATTERN_VITAMIN_B1, nameFr, nameEn)) {
                nutrition.setVitaminB1(value);
                matchedCount++;
            } else if (matches(PATTERN_VITAMIN_B2, nameFr, nameEn)) {
                nutrition.setVitaminB2(value);
                matchedCount++;
            } else if (matches(PATTERN_VITAMIN_B3, nameFr, nameEn)) {
                nutrition.setVitaminB3(value);
                matchedCount++;
            } else if (matches(PATTERN_VITAMIN_B5, nameFr, nameEn)) {
                nutrition.setVitaminB5(value);
                matchedCount++;
            } else if (matches(PATTERN_VITAMIN_B6, nameFr, nameEn)) {
                nutrition.setVitaminB6(value);
                matchedCount++;
            } else if (matches(PATTERN_VITAMIN_B9, nameFr, nameEn)) {
                nutrition.setVitaminB9(value);
                matchedCount++;
            } else if (matches(PATTERN_VITAMIN_B12, nameFr, nameEn)) {
                nutrition.setVitaminB12(value);
                matchedCount++;
            } else if (matches(PATTERN_VITAMIN_A, nameFr, nameEn)) {
                nutrition.setVitaminA(value);
                matchedCount++;
            } else if (matches(PATTERN_VITAMIN_D, nameFr, nameEn)) {
                nutrition.setVitaminD(value);
                matchedCount++;
            } else if (matches(PATTERN_VITAMIN_E, nameFr, nameEn)) {
                nutrition.setVitaminE(value);
                matchedCount++;
            } else if (matches(PATTERN_VITAMIN_K, nameFr, nameEn)) {
                nutrition.setVitaminK(value);
                matchedCount++;
            }
        }

        Log.d(TAG, "Matched " + matchedCount + " nutrients out of " + compos.size() + " entries");

        // Return nutrition object only if we matched at least some basic nutrients
        if (matchedCount > 0) {
            return nutrition;
        }

        Log.w(TAG, "No nutrients matched from compos array");
        return null;
    }

    /**
     * Helper method to match a pattern against French and English names
     *
     * @param pattern Regex pattern to match
     * @param nameFr French nutrient name
     * @param nameEn English nutrient name
     * @return true if pattern matches either name
     */
    private static boolean matches(Pattern pattern, String nameFr, String nameEn) {
        if (nameFr != null && pattern.matcher(nameFr).find()) {
            return true;
        }
        if (nameEn != null && pattern.matcher(nameEn).find()) {
            return true;
        }
        return false;
    }

    // ========== PRIVATE: UTILITIES ==========

    /**
     * Normalizes language code to supported values
     *
     * @param language Language code to normalize
     * @return "en" or "fr" (defaults to "fr")
     */
    @NonNull
    private static String normalizeLanguage(@Nullable String language) {
        return "en".equalsIgnoreCase(language) ? "en" : "fr";
    }

    /**
     * Validates that a CiqualElasticsearchFood has minimum required data
     *
     * Checks for code and at least one name (French or English).
     *
     * @param food CiqualElasticsearchFood to validate
     * @return true if valid, false otherwise
     */
    public static boolean isValid(@Nullable CiqualElasticsearchFood food) {
        if (food == null) {
            return false;
        }

        // Must have code
        if (food.getCode() == null || food.getCode().isEmpty()) {
            return false;
        }

        // Must have at least one name (French or English)
        boolean hasName = (food.getNomFr() != null && !food.getNomFr().isEmpty()) ||
                (food.getNomEng() != null && !food.getNomEng().isEmpty());

        return hasName;
    }
}