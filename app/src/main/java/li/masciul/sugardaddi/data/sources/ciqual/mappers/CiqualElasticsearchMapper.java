package li.masciul.sugardaddi.data.sources.ciqual.mappers;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.util.Log;

import li.masciul.sugardaddi.core.enums.DataConfidence;
import li.masciul.sugardaddi.core.enums.DataSourceType;
import li.masciul.sugardaddi.core.models.Category;
import li.masciul.sugardaddi.core.models.FoodProduct;
import li.masciul.sugardaddi.core.models.Nutrition;
import li.masciul.sugardaddi.core.models.ProductTranslation;
import li.masciul.sugardaddi.core.models.SourceIdentifier;
import li.masciul.sugardaddi.core.utils.ProductUrlBuilder;
import li.masciul.sugardaddi.data.sources.ciqual.CiqualConstants;
import li.masciul.sugardaddi.data.sources.ciqual.CiqualConstituents;
import li.masciul.sugardaddi.data.sources.ciqual.CiqualNutritionWriter;
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
 * The live API gives no constituent code, only the names with their unit
 * ("Fer (mg/100 g)", "Iron (mg/100g)"). Each name is looked up, exactly, in
 * CiqualConstituents, the table the downloaded dataset uses too, and the value is
 * written by CiqualNutritionWriter: same field, same unit as the import.
 *
 * Loose patterns used to do this and mixed nutrients up: "vitamine b1" also
 * matched "Vitamine B12", "sodium" matched "Sel chlorure de sodium", "^fer$"
 * never matched "Fer (mg/100 g)", and several constituents (energy variants,
 * B9 / folates / folic acid, K1 / K2) landed in one field, the last one winning.
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
     * 2. Finds each constituent by its exact name (CiqualConstituents)
     * 3. Writes its value with CiqualNutritionWriter
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
        product.setDataSource(DataSourceType.fromString(CiqualConstants.SOURCE_ID));

        // Set source identifier
        product.setSourceIdentifier(new SourceIdentifier(CiqualConstants.SOURCE_ID, food.getCode()));

        String sourceUrlEn = ProductUrlBuilder.resolveUrl(product.getSourceIdentifier(), food.getUrlEng());
        if (sourceUrlEn != null) {
            product.setSourceUrl(sourceUrlEn, "en");
        }
        String sourceUrlFr = ProductUrlBuilder.resolveUrl(product.getSourceIdentifier(), food.getUrlFr());
        if (sourceUrlFr != null) {
            product.setSourceUrl(sourceUrlFr, "fr");
        }

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
        // Always available - was the only source before Phase 2A.
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
     * - constNomFr: French name with unit (e.g., "Fer (mg/100 g)")
     * - constNomEng: English name (e.g., "Energy...kcal", "Protein")
     *
     * This method:
     * 1. Iterates through all compositions
     * 2. Finds the constituent by its exact name (CiqualConstituents)
     * 3. Reads the value with CiqualConstituents.parseValue()
     * 4. Writes it with CiqualNutritionWriter
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

        // Same rule as the downloaded dataset (CiqualImportService): the WORST
        // ANSES grade among the values read sets the badge. A or B: SCIENTIFIC;
        // one C or D: ESTIMATED. A value without a grade does not count.
        DataConfidence confidence = DataConfidence.SCIENTIFIC;

        // Iterate through all composition entries
        for (CiqualElasticsearchComposition comp : compos) {
            if (comp == null) {
                continue;
            }

            // Exact name, French or English (see CiqualConstituents.byName)
            CiqualConstituents.Constituent constituent =
                    CiqualConstituents.byName(comp.getConstNomFr());
            if (constituent == null) {
                constituent = CiqualConstituents.byName(comp.getConstNomEng());
            }
            if (constituent == null) {
                // Not read by the app on purpose (see CiqualConstituents), or a
                // name Ciqual changed: logged so a rename shows up.
                Log.d(TAG, "Constituent not read: " + comp.getConstNomFr());
                continue;
            }

            // "traces" = 0, "< x" = unknown: see CiqualConstituents.parseValue
            Double value = CiqualConstituents.parseValue(comp.getCompoTeneur());
            if (value == null) {
                continue;
            }

            CiqualNutritionWriter.write(nutrition, constituent, value);
            matchedCount++;

            String grade = comp.getCompoCodeConfiance();
            if (grade != null && !grade.trim().isEmpty()) {
                confidence = confidence.lowest(DataConfidence.fromCiqualCode(grade));
            }
        }

        Log.d(TAG, "Matched " + matchedCount + " nutrients out of " + compos.size() + " entries");

        // Ciqual usually reports salt directly (constituent 10004, "Sel
        // chlorure de sodium") - but like any per-product field in a real
        // dataset, some specific entries lack it even when sodium is
        // present. Fallback only: never overrides a real measured value,
        // which is better data than a formula. Same derivation as
        // USDAMapper/FatSecretMapper (salt = sodium x 2.5, mg to g) for
        // consistency across all four numeric sources.
        if (nutrition.getSalt() == null && nutrition.getSodium() != null) {
            nutrition.setSalt(nutrition.getSodium() * 2.5 / 1000.0);
        }

        // Return nutrition object only if we matched at least some basic nutrients
        if (matchedCount > 0) {
            nutrition.setDataConfidence(confidence);
            return nutrition;
        }

        Log.w(TAG, "No nutrients matched from compos array");
        return null;
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