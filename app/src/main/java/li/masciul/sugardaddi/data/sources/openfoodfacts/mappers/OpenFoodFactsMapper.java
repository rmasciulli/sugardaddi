package li.masciul.sugardaddi.data.sources.openfoodfacts.mappers;

import android.util.Log;

import li.masciul.sugardaddi.core.enums.DataSource;
import li.masciul.sugardaddi.core.models.SourceIdentifier;
import li.masciul.sugardaddi.data.sources.openfoodfacts.OpenFoodFactsConstants;
import li.masciul.sugardaddi.data.sources.openfoodfacts.api.dto.OpenFoodFactsProduct;
import li.masciul.sugardaddi.data.sources.openfoodfacts.api.dto.OpenFoodFactsNutriments;
import li.masciul.sugardaddi.data.sources.openfoodfacts.api.dto.OpenFoodFactsSearchResponse;

import li.masciul.sugardaddi.core.models.FoodProduct;
import li.masciul.sugardaddi.core.models.Nutrition;
import li.masciul.sugardaddi.core.models.Category;
import li.masciul.sugardaddi.core.models.ServingSize;
import li.masciul.sugardaddi.core.utils.AllergenUtils;
import li.masciul.sugardaddi.data.network.ApiConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * OpenFoodFactsMapper - Maps OpenFoodFacts DTOs to domain models
 *
 * REFACTORED VERSION - Properly uses hybrid translation system
 *
 * ARCHITECTURE:
 * - Maps ALL available OFF fields to FoodProduct/Nutrition
 * - Handles ONLY languages that exist in OFF: EN, FR
 * - Uses hybrid translation with FoodProduct.DEFAULT_LANGUAGE = "en"
 * - Comprehensive nutrition mapping with null safety
 * - Working allergen parsing (kept from original)
 *
 * LANGUAGE HANDLING:
 * - Primary language is always set to the requested language
 * - If EN available: stored in primary fields + FR in translations
 * - If FR available: stored in primary fields + EN in translations (if exists)
 * - Generic fallbacks when specific language unavailable
 */
public class OpenFoodFactsMapper {

    private static final String TAG = "OpenFoodFactsMapper";

    // ========== PUBLIC API ==========

    /**
     * Map search response to list of domain products
     */
    public List<FoodProduct> mapSearchResponse(OpenFoodFactsSearchResponse offResponse, String language) {
        List<FoodProduct> products = new ArrayList<>();

        if (offResponse != null && offResponse.getProducts() != null) {
            for (OpenFoodFactsProduct offProduct : offResponse.getProducts()) {
                FoodProduct product = mapToDomainModel(offProduct, language);
                if (product != null) {
                    products.add(product);
                }
            }
        }

        return products;
    }

    /**
     * Map single OpenFoodFactsProduct to FoodProduct domain model
     *
     * @param offProduct OFF DTO from API
     * @param language Requested language (typically user's app language)
     * @return FoodProduct with hybrid translation, or null if invalid
     */
    public FoodProduct mapToDomainModel(OpenFoodFactsProduct offProduct, String language) {
        if (offProduct == null || offProduct.getCode() == null) {
            Log.w(TAG, "Invalid OFF product: null or missing barcode");
            return null;
        }

        // Create product with unified source identifier
        FoodProduct product = new FoodProduct(OpenFoodFactsConstants.SOURCE_ID, offProduct.getCode());

        // Set data source
        product.setDataSource(DataSource.fromString(OpenFoodFactsConstants.SOURCE_ID));

        // Set barcode (OFF always has this)
        product.setBarcode(offProduct.getCode());

        // Map basic product info with language handling
        mapProductInfo(product, offProduct, language);

        // Map images
        mapImages(product, offProduct);

        // Map quality scores (NutriScore, EcoScore, NOVA)
        mapQualityScores(product, offProduct);

        // Map categories
        mapCategories(product, offProduct, language);

        // Map allergens (working implementation from original)
        mapAllergens(product, offProduct);

        // Map metadata (countries, stores, etc.)
        mapMetadata(product, offProduct, language);

        // Map nutrition (comprehensive)
        mapNutrition(product, offProduct);

        // Map serving size
        mapServingSize(product, offProduct);

        // Set timestamps
        if (offProduct.getLastModified() != null) {
            product.setLastUpdated(offProduct.getLastModified() * 1000L);
        }

        // Calculate data completeness so the enrichment system can compare
        // this product's quality against Searchalicious lightweight results.
        // Searchalicious sets completeness from its ES index field (0.0-1.0).
        // Without this call, OFF v2 products would have completeness=0.0 and
        // would never win the quality gate in FoodProduct.enrichWith().
        product.calculateCompleteness();

        return product;
    }

    // ========== PRIVATE MAPPING METHODS ==========

    /**
     * Map product basic info with proper language handling
     *
     * OFF provides: product_name, product_name_en, product_name_fr
     * Strategy:
     * 1. Get name in requested language if available
     * 2. Store in primary fields
     * 3. Store other available language in translations
     */
    private void mapProductInfo(FoodProduct product, OpenFoodFactsProduct offProduct, String language) {
        // Set currentLanguage. This ensures all setName/setBrand/setGenericName calls route to primary fields
        product.setCurrentLanguage(language);

        // Map product name with language handling
        String primaryName = getLocalizedName(offProduct, language);
        product.setName(primaryName, language);

        // Store other available language names in translations
        addAlternativeLanguageTranslations(product, offProduct, language);

        // Map generic name
        String genericName = getLocalizedGenericName(offProduct, language);
        if (genericName != null && !genericName.trim().isEmpty()) {
            product.setGenericName(genericName, language);
        }

        // Map brand (language-independent)
        if (offProduct.getBrands() != null && !offProduct.getBrands().trim().isEmpty()) {
            product.setBrand(offProduct.getBrands(), language);
        }

        // Map quantity (language-independent)
        if (offProduct.getQuantity() != null && !offProduct.getQuantity().trim().isEmpty()) {
            product.setQuantity(offProduct.getQuantity());
        }

        // Map ingredients with language handling
        String ingredients = getLocalizedIngredients(offProduct, language);
        if (ingredients != null && !ingredients.trim().isEmpty()) {
            product.setIngredients(ingredients, language);
        }
    }

    /**
     * Get localized product name with proper fallback chain
     */
    private String getLocalizedName(OpenFoodFactsProduct offProduct, String language) {
        String name = null;

        // Try requested language first
        if ("en".equals(language) && offProduct.getProductNameEn() != null) {
            name = offProduct.getProductNameEn();
        } else if ("fr".equals(language) && offProduct.getProductNameFr() != null) {
            name = offProduct.getProductNameFr();
        }

        // Fallback chain
        if (name == null || name.trim().isEmpty()) {
            name = offProduct.getProductName();  // Generic name
        }
        if (name == null || name.trim().isEmpty()) {
            name = offProduct.getGenericName();  // Even more generic
        }
        if (name == null || name.trim().isEmpty()) {
            name = "Unknown Product";  // Last resort
        }

        return name.trim();
    }

    /**
     * Get localized generic name
     */
    private String getLocalizedGenericName(OpenFoodFactsProduct offProduct, String language) {
        String genericName = null;

        if ("en".equals(language) && offProduct.getGenericNameEn() != null) {
            genericName = offProduct.getGenericNameEn();
        } else if ("fr".equals(language) && offProduct.getGenericNameFr() != null) {
            genericName = offProduct.getGenericNameFr();
        }

        if (genericName == null || genericName.trim().isEmpty()) {
            genericName = offProduct.getGenericName();
        }

        return genericName;
    }

    /**
     * Get localized ingredients
     */
    private String getLocalizedIngredients(OpenFoodFactsProduct offProduct, String language) {
        String ingredients = null;

        if ("en".equals(language) && offProduct.getIngredientsTextEn() != null) {
            ingredients = offProduct.getIngredientsTextEn();
        } else if ("fr".equals(language) && offProduct.getIngredientsTextFr() != null) {
            ingredients = offProduct.getIngredientsTextFr();
        }

        if (ingredients == null || ingredients.trim().isEmpty()) {
            ingredients = offProduct.getIngredientsText();
        }

        return ingredients;
    }

    /**
     * Add alternative language translations
     *
     * If user requested EN, store FR in translations (and vice versa)
     */
    private void addAlternativeLanguageTranslations(FoodProduct product, OpenFoodFactsProduct offProduct, String requestedLanguage) {
        // If requested EN, store FR translation if available
        if ("en".equals(requestedLanguage)) {
            if (offProduct.getProductNameFr() != null && !offProduct.getProductNameFr().trim().isEmpty()) {
                product.setName(offProduct.getProductNameFr(), "fr");
            }
            if (offProduct.getGenericNameFr() != null && !offProduct.getGenericNameFr().trim().isEmpty()) {
                product.setGenericName(offProduct.getGenericNameFr(), "fr");
            }
            if (offProduct.getIngredientsTextFr() != null && !offProduct.getIngredientsTextFr().trim().isEmpty()) {
                product.setIngredients(offProduct.getIngredientsTextFr(), "fr");
            }
        }
        // If requested FR, store EN translation if available
        else if ("fr".equals(requestedLanguage)) {
            if (offProduct.getProductNameEn() != null && !offProduct.getProductNameEn().trim().isEmpty()) {
                product.setName(offProduct.getProductNameEn(), "en");
            }
            if (offProduct.getGenericNameEn() != null && !offProduct.getGenericNameEn().trim().isEmpty()) {
                product.setGenericName(offProduct.getGenericNameEn(), "en");
            }
            if (offProduct.getIngredientsTextEn() != null && !offProduct.getIngredientsTextEn().trim().isEmpty()) {
                product.setIngredients(offProduct.getIngredientsTextEn(), "en");
            }
        }
    }

    /**
     * Map product images
     * Tries multiple image fields in order of preference
     */
    private void mapImages(FoodProduct product, OpenFoodFactsProduct offProduct) {
        // Try image_front_url first (highest quality, detailed view)
        String imageUrl = offProduct.getImageFrontUrl();

        // Fallback to generic image_url (what we request in search)
        if (imageUrl == null || imageUrl.trim().isEmpty()) {
            imageUrl = offProduct.getImageUrl();
        }

        // Set if we found any image
        if (imageUrl != null && !imageUrl.trim().isEmpty()) {
            product.setImageUrl(imageUrl);
        }

        // Try thumbnail
        String thumbnailUrl = offProduct.getImageFrontSmallUrl();
        if (thumbnailUrl == null || thumbnailUrl.trim().isEmpty()) {
            thumbnailUrl = offProduct.getImageSmallUrl();
        }

        if (thumbnailUrl != null && !thumbnailUrl.trim().isEmpty()) {
            product.setImageThumbnailUrl(thumbnailUrl);
        }
    }

    /**
     * Map quality scores (NutriScore, EcoScore, NOVA)
     *
     * FIXED: Uses correct field names from DTO
     */
    private void mapQualityScores(FoodProduct product, OpenFoodFactsProduct offProduct) {
        // NutriScore (A-E) - CORRECTED field name
        if (offProduct.getNutriscoreGrade() != null && !offProduct.getNutriscoreGrade().trim().isEmpty()) {
            product.setNutriScore(offProduct.getNutriscoreGrade().toUpperCase());
        }

        // EcoScore (A-E) - correct field name
        if (offProduct.getEcoscoreGrade() != null && !offProduct.getEcoscoreGrade().trim().isEmpty()) {
            product.setEcoScore(offProduct.getEcoscoreGrade().toUpperCase());
        }

        // NOVA Group (1-4)
        if (offProduct.getNovaGroup() != null && !offProduct.getNovaGroup().trim().isEmpty()) {
            product.setNovaGroup(offProduct.getNovaGroup());
        }
    }

    /**
     * Map categories with hierarchy
     */
    private void mapCategories(FoodProduct product, OpenFoodFactsProduct offProduct, String language) {

        if (offProduct.hasAgribalyseData()) {
            // === AGRIBALYSE PATH: bilingual, standardized ===
            // ecoscore_data.agribalyse always carries both EN and FR names.
            // This is the most accurate, human-readable category — prefer it
            // over raw tags or free-text which are often language-mixed or stale.
            String nameEn = offProduct.getAgribalyseNameEn();
            String nameFr = offProduct.getAgribalyseNameFr();

            // Store primary language as main categoriesText
            String primary = "fr".equals(language) ? nameFr : nameEn;
            if (primary != null && !primary.trim().isEmpty()) {
                product.setCategoriesText(primary, language);
            }

            // Store other language in translations — no extra API call needed
            String otherLang = "en".equals(language) ? "fr" : "en";
            String other = "en".equals(language) ? nameFr : nameEn;
            if (other != null && !other.trim().isEmpty()) {
                product.setCategoriesText(other, otherLang);
            }

            if (ApiConfig.DEBUG_LOGGING) {
                Log.d(TAG, "Agribalyse code for " + offProduct.getCode()
                        + ": " + offProduct.getAgribalyseCode());
            }

        } else {
            // === HIERARCHY FALLBACK: most specific en: tag ===
            // Walk categories_hierarchy backwards for the most specific English tag.
            // Skips French-content tags sitting under en: prefixes.
            String hierarchyCategory = getMostSpecificEnglishTag(offProduct.getCategoriesHierarchy());
            if (hierarchyCategory != null) {
                String cleaned = cleanCategoryTag(hierarchyCategory);
                if (!cleaned.isEmpty()) {
                    product.setCategoriesText(cleaned, language);
                }
            } else if (offProduct.getCategories() != null && !offProduct.getCategories().trim().isEmpty()) {
                // Last resort: raw categories free-text (language-mixed, not ideal)
                product.setCategoriesText(offProduct.getCategories(), language);
            }
        }

        // Always store the full hierarchy for breadcrumbs / future taxonomy work.
        if (offProduct.getCategoriesHierarchy() != null && !offProduct.getCategoriesHierarchy().isEmpty()) {
            product.setCategoryHierarchy(offProduct.getCategoriesHierarchy());
        } else if (offProduct.getCategoriesTags() != null && !offProduct.getCategoriesTags().isEmpty()) {
            product.setCategoryHierarchy(offProduct.getCategoriesTags());
        }
    }

    /**
     * Clean OFF category tag
     * Removes language prefix (e.g., "en:beverages" → "Beverages")
     */
    /**
     * Get the most specific English category tag from a categories_hierarchy list.
     * Walks backwards through the hierarchy (most specific last) and returns
     * the last tag that starts with "en:" and passes isLikelyEnglishTag().
     */
    private String getMostSpecificEnglishTag(List<String> hierarchy) {
        if (hierarchy == null || hierarchy.isEmpty()) return null;

        // Walk backwards — most specific tag is at the end of the hierarchy
        for (int i = hierarchy.size() - 1; i >= 0; i--) {
            String tag = hierarchy.get(i);
            if (tag != null && tag.startsWith("en:") && isLikelyEnglishTag(tag)) {
                return tag;
            }
        }
        return null;
    }

    /**
     * Heuristic: is this an en:-prefixed tag likely to contain English content?
     *
     * OFF sometimes stores French-content tags under the en: prefix due to
     * taxonomy gaps (e.g. en:petales-de-ble-chocolates...). This filter
     * skips those to avoid showing French text on English cards.
     */
    private boolean isLikelyEnglishTag(String tag) {
        if (tag == null) return false;
        String slug = tag.startsWith("en:") ? tag.substring(3) : tag;

        // Non-ASCII = not English
        for (char c : slug.toCharArray()) {
            if (c > 127) return false;
        }

        // Known French roots that appear in OFF taxonomy slugs
        String[] frenchRoots = {
                "cereale", "cereales", "petale", "petales", "aliment", "aliments",
                "boisson", "boissons", "vegetaux", "legume", "legumes",
                "petit-dejeuner", "dejeuner", "produit", "produits",
                "pate", "pates", "tartiner", "noisette", "noisettes",
                "extrudee", "extrudees", "enrichie", "enrichies",
                "derive", "derives", "pomme", "pommes"
        };

        for (String root : frenchRoots) {
            if (slug.contains(root)) return false;
        }
        return true;
    }

    /**
     * Clean an OFF category tag for display.
     * Removes language prefix and formats as sentence case.
     *
     * Examples:
     *   "en:sandwich-biscuits"       → "Sandwich biscuits"
     *   "en:dark-chocolate-biscuits" → "Dark chocolate biscuits"
     */
    private String cleanCategoryTag(String categoryTag) {
        if (categoryTag == null || categoryTag.trim().isEmpty()) {
            return "";
        }

        String cleaned = categoryTag.trim();

        // Remove language prefix (e.g. "en:", "fr:")
        int colonIndex = cleaned.indexOf(':');
        if (colonIndex >= 0 && colonIndex < cleaned.length() - 1) {
            cleaned = cleaned.substring(colonIndex + 1);
        }

        // Replace hyphens and underscores with spaces
        cleaned = cleaned.replace("-", " ").replace("_", " ");

        // Sentence case: only first character capitalized
        if (!cleaned.isEmpty()) {
            cleaned = Character.toUpperCase(cleaned.charAt(0)) + cleaned.substring(1).toLowerCase();
        }

        return cleaned;
    }

    /**
     * Map allergens (WORKING implementation from original)
     *
     * Combines allergens AND traces for safety (KISS principle)
     */
    private void mapAllergens(FoodProduct product, OpenFoodFactsProduct offProduct) {
        int allergenFlags = 0;

        // Parse allergens
        if (offProduct.getAllergensTags() != null && !offProduct.getAllergensTags().isEmpty()) {
            allergenFlags |= AllergenUtils.parseFromOFFTags(offProduct.getAllergensTags());
        }

        // Parse traces (for safety, include potential allergens)
        if (offProduct.getTracesTags() != null && !offProduct.getTracesTags().isEmpty()) {
            allergenFlags |= AllergenUtils.parseFromOFFTags(offProduct.getTracesTags());
        }

        product.setAllergenFlags(allergenFlags);
    }

    /**
     * Map metadata (countries, stores, labels)
     */
    private void mapMetadata(FoodProduct product, OpenFoodFactsProduct offProduct, String language) {
        // Origins/Countries
        if (offProduct.getCountries() != null && !offProduct.getCountries().trim().isEmpty()) {
            product.setOrigins(offProduct.getCountries(), language);
        }

        // Stores
        if (offProduct.getStores() != null && !offProduct.getStores().trim().isEmpty()) {
            product.setStores(offProduct.getStores(), language);
        }

        // Labels (certifications)
        if (offProduct.getLabels() != null && !offProduct.getLabels().trim().isEmpty()) {
            // Parse labels and add to product labels list
            String[] labelArray = offProduct.getLabels().split(",");
            List<String> labels = new ArrayList<>();
            for (String label : labelArray) {
                String trimmed = label.trim();
                if (!trimmed.isEmpty()) {
                    labels.add(trimmed);
                }
            }
            product.setLabels(labels);
        }
    }

    /**
     * Map nutrition with per-serving to per-100g conversion AND energy conversion
     *
     * FEATURES:
     * - Handles both nutrition_data_per formats ("100g" and "serving")
     * - Automatically converts between kJ and kcal when one is missing
     * - Uses standard conversion: 1 kcal = 4.184 kJ
     */
    private void mapNutrition(FoodProduct product, OpenFoodFactsProduct offProduct) {
        if (offProduct.getNutriments() == null) {
            return;
        }

        OpenFoodFactsNutriments off = offProduct.getNutriments();
        Nutrition nutrition = new Nutrition();

        // Check if data needs conversion from serving to 100g
        String nutritionDataPer = offProduct.getNutritionDataPer();
        Double servingQuantity = offProduct.getServingQuantity();
        boolean needsConversion = "serving".equals(nutritionDataPer) &&
                servingQuantity != null &&
                servingQuantity > 0;

        // ========== ENERGY (with auto-conversion between kJ and kcal) ==========
        Double energyKj = convertTo100g(off.getEnergyKj100g(), servingQuantity, needsConversion);
        Double energyKcal = convertTo100g(off.getEnergyKcal100g(), servingQuantity, needsConversion);

        // Auto-convert if one is missing
        if (energyKj == null && energyKcal != null) {
            // Calculate kJ from kcal: kJ = kcal * 4.184
            energyKj = energyKcal * 4.184;
            Log.d(TAG, "Calculated missing energy kJ from kcal: " + energyKj);
        } else if (energyKcal == null && energyKj != null) {
            // Calculate kcal from kJ: kcal = kJ / 4.184
            energyKcal = energyKj / 4.184;
            Log.d(TAG, "Calculated missing energy kcal from kJ: " + energyKcal);
        }

        nutrition.setEnergyKj(energyKj);
        nutrition.setEnergyKcal(energyKcal);

        // ========== MACRONUTRIENTS ==========
        nutrition.setProteins(convertTo100g(off.getProteins100g(), servingQuantity, needsConversion));
        nutrition.setCarbohydrates(convertTo100g(off.getCarbohydrates100g(), servingQuantity, needsConversion));
        nutrition.setFat(convertTo100g(off.getFat100g(), servingQuantity, needsConversion));
        nutrition.setFiber(convertTo100g(off.getFiber100g(), servingQuantity, needsConversion));
        nutrition.setAlcohol(convertTo100g(off.getAlcohol100g(), servingQuantity, needsConversion));
        nutrition.setWater(convertTo100g(off.getWater100g(), servingQuantity, needsConversion));

        // ========== CARBOHYDRATE DETAILS ==========
        nutrition.setSugars(convertTo100g(off.getSugars100g(), servingQuantity, needsConversion));
        nutrition.setStarch(convertTo100g(off.getStarch100g(), servingQuantity, needsConversion));
        nutrition.setPolyols(convertTo100g(off.getPolyols100g(), servingQuantity, needsConversion));
        nutrition.setGlucose(convertTo100g(off.getGlucose100g(), servingQuantity, needsConversion));
        nutrition.setFructose(convertTo100g(off.getFructose100g(), servingQuantity, needsConversion));
        nutrition.setSucrose(convertTo100g(off.getSucrose100g(), servingQuantity, needsConversion));
        nutrition.setLactose(convertTo100g(off.getLactose100g(), servingQuantity, needsConversion));
        nutrition.setMaltose(convertTo100g(off.getMaltose100g(), servingQuantity, needsConversion));
        nutrition.setGalactose(convertTo100g(off.getGalactose100g(), servingQuantity, needsConversion));

        // ========== FAT DETAILS ==========
        nutrition.setSaturatedFat(convertTo100g(off.getSaturatedFat100g(), servingQuantity, needsConversion));
        nutrition.setMonounsaturatedFat(convertTo100g(off.getMonounsaturatedFat100g(), servingQuantity, needsConversion));
        nutrition.setPolyunsaturatedFat(convertTo100g(off.getPolyunsaturatedFat100g(), servingQuantity, needsConversion));
        nutrition.setTransFat(convertTo100g(off.getTransFat100g(), servingQuantity, needsConversion));
        nutrition.setCholesterol(convertTo100g(off.getCholesterol100g(), servingQuantity, needsConversion));

        // ========== OMEGA FATTY ACIDS ==========
        nutrition.setOmega3(convertTo100g(off.getOmega3Fat100g(), servingQuantity, needsConversion));
        nutrition.setOmega6(convertTo100g(off.getOmega6Fat100g(), servingQuantity, needsConversion));
        nutrition.setOmega9(convertTo100g(off.getOmega9Fat100g(), servingQuantity, needsConversion));
        nutrition.setLinoleicAcid(convertTo100g(off.getLinoleicAcid100g(), servingQuantity, needsConversion));
        nutrition.setALA(convertTo100g(off.getAlphaLinolenicAcid100g(), servingQuantity, needsConversion));
        nutrition.setEPA(convertTo100g(off.getEicosapentaenoicAcid100g(), servingQuantity, needsConversion));
        nutrition.setDHA(convertTo100g(off.getDocosahexaenoicAcid100g(), servingQuantity, needsConversion));
        nutrition.setArachidonicAcid(convertTo100g(off.getArachidonicAcid100g(), servingQuantity, needsConversion));
        nutrition.setGammaLinolenicAcid(convertTo100g(off.getGammaLinolenicAcid100g(), servingQuantity, needsConversion));

        // ========== MINERALS ==========
        nutrition.setSalt(convertTo100g(off.getSalt100g(), servingQuantity, needsConversion));
        nutrition.setSodium(convertTo100g(off.getSodium100g(), servingQuantity, needsConversion));
        nutrition.setCalcium(convertTo100g(off.getCalcium100g(), servingQuantity, needsConversion));
        nutrition.setPhosphorus(convertTo100g(off.getPhosphorus100g(), servingQuantity, needsConversion));
        nutrition.setMagnesium(convertTo100g(off.getMagnesium100g(), servingQuantity, needsConversion));
        nutrition.setPotassium(convertTo100g(off.getPotassium100g(), servingQuantity, needsConversion));
        nutrition.setChloride(convertTo100g(off.getChloride100g(), servingQuantity, needsConversion));
        nutrition.setIron(convertTo100g(off.getIron100g(), servingQuantity, needsConversion));
        nutrition.setZinc(convertTo100g(off.getZinc100g(), servingQuantity, needsConversion));
        nutrition.setCopper(convertTo100g(off.getCopper100g(), servingQuantity, needsConversion));
        nutrition.setManganese(convertTo100g(off.getManganese100g(), servingQuantity, needsConversion));
        nutrition.setSelenium(convertTo100g(off.getSelenium100g(), servingQuantity, needsConversion));
        nutrition.setIodine(convertTo100g(off.getIodine100g(), servingQuantity, needsConversion));
        nutrition.setChromium(convertTo100g(off.getChromium100g(), servingQuantity, needsConversion));
        nutrition.setMolybdenum(convertTo100g(off.getMolybdenum100g(), servingQuantity, needsConversion));
        nutrition.setFluoride(convertTo100g(off.getFluoride100g(), servingQuantity, needsConversion));

        // ========== VITAMINS ==========
        nutrition.setVitaminA(convertTo100g(off.getVitaminA100g(), servingQuantity, needsConversion));
        nutrition.setVitaminD(convertTo100g(off.getVitaminD100g(), servingQuantity, needsConversion));
        nutrition.setVitaminE(convertTo100g(off.getVitaminE100g(), servingQuantity, needsConversion));
        nutrition.setVitaminK(convertTo100g(off.getVitaminK100g(), servingQuantity, needsConversion));
        nutrition.setVitaminC(convertTo100g(off.getVitaminC100g(), servingQuantity, needsConversion));
        nutrition.setVitaminB1(convertTo100g(off.getVitaminB1100g(), servingQuantity, needsConversion));
        nutrition.setVitaminB2(convertTo100g(off.getVitaminB2100g(), servingQuantity, needsConversion));
        nutrition.setVitaminB3(convertTo100g(off.getVitaminB3100g(), servingQuantity, needsConversion));
        nutrition.setVitaminB5(convertTo100g(off.getVitaminB5100g(), servingQuantity, needsConversion));
        nutrition.setVitaminB6(convertTo100g(off.getVitaminB6100g(), servingQuantity, needsConversion));
        nutrition.setVitaminB7(convertTo100g(off.getBiotin100g(), servingQuantity, needsConversion));
        nutrition.setVitaminB9(convertTo100g(off.getVitaminB9100g(), servingQuantity, needsConversion));
        nutrition.setVitaminB12(convertTo100g(off.getVitaminB12100g(), servingQuantity, needsConversion));
        nutrition.setBetaCarotene(convertTo100g(off.getBetaCarotene100g(), servingQuantity, needsConversion));

        // ========== OTHER COMPOUNDS ==========
        nutrition.setCaffeine(convertTo100g(off.getCaffeine100g(), servingQuantity, needsConversion));
        nutrition.setTaurine(convertTo100g(off.getTaurine100g(), servingQuantity, needsConversion));

        // Set data source and calculate completeness
        nutrition.setDataSource("OpenFoodFacts");
        nutrition.calculateCompleteness();

        product.setNutrition(nutrition);
    }

    /**
     * Convert nutrient value from per-serving to per-100g if needed
     *
     * @param value Value (either per-100g or per-serving depending on needsConversion)
     * @param servingSize Serving size in grams (only used if needsConversion is true)
     * @param needsConversion True if value is per-serving and needs conversion
     * @return Value per 100g, or null if input is null
     */
    private Double convertTo100g(Double value, Double servingSize, boolean needsConversion) {
        if (value == null) {
            return null;
        }

        if (!needsConversion) {
            // Already per 100g, return as-is
            return value;
        }

        // Convert from per-serving to per-100g
        // Formula: (valuePerServing / servingSize) * 100
        if (servingSize != null && servingSize > 0) {
            return (value / servingSize) * 100.0;
        }

        // If serving size is missing, we can't convert - return null
        Log.w(TAG, "Cannot convert nutrient to per-100g: serving size missing or zero");
        return null;
    }

    /**
     * Map serving size
     */
    private void mapServingSize(FoodProduct product, OpenFoodFactsProduct offProduct) {
        if (offProduct.getServingSize() != null && !offProduct.getServingSize().trim().isEmpty()) {
            try {
                // Use ServingSize constructor that parses description
                ServingSize serving = new ServingSize(offProduct.getServingSize());
                product.setServingSize(serving);
            } catch (Exception e) {
                Log.w(TAG, "Failed to parse serving size: " + offProduct.getServingSize(), e);
            }
        }
    }
}