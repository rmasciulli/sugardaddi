package li.masciul.sugardaddi.data.sources.openfoodfacts.mappers;

import android.util.Log;

import li.masciul.sugardaddi.core.enums.DataConfidence;
import li.masciul.sugardaddi.core.enums.DataSourceType;
import li.masciul.sugardaddi.core.enums.NutritionBasis;
import li.masciul.sugardaddi.core.enums.NutritionPreparation;
import li.masciul.sugardaddi.core.enums.Unit;
import li.masciul.sugardaddi.core.models.SourceIdentifier;
import li.masciul.sugardaddi.core.utils.ProductUrlBuilder;
import li.masciul.sugardaddi.data.sources.openfoodfacts.OpenFoodFactsConstants;
import li.masciul.sugardaddi.data.sources.openfoodfacts.api.dto.OpenFoodFactsProduct;
import li.masciul.sugardaddi.data.sources.openfoodfacts.api.dto.OpenFoodFactsNutriments;
import li.masciul.sugardaddi.data.sources.openfoodfacts.api.dto.OpenFoodFactsSearchResponse;

import li.masciul.sugardaddi.core.models.FoodProduct;
import li.masciul.sugardaddi.core.models.Nutrition;
import li.masciul.sugardaddi.core.models.Nutrition.NutrientInfo;
import li.masciul.sugardaddi.core.models.ServingSize;
import li.masciul.sugardaddi.core.utils.AllergenUtils;
import li.masciul.sugardaddi.data.network.ApiConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

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
        product.setDataSource(DataSourceType.fromString(OpenFoodFactsConstants.SOURCE_ID));

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

        // Never set anywhere in this mapper until now - every other source
        // (Ciqual, USDA, FatSecret) sets this, OFF never did. Confirmed to
        // have real consequences beyond just this: the sourceUrl fallback
        // just below has been silently unable to do anything for OFF
        // since it was written, since ProductUrlBuilder.resolveUrl()'s
        // computed-fallback branch needs a real SourceIdentifier to work
        // from. Likely also affects favoriting/dedup/caching wherever else
        // this project keys off SourceIdentifier - not audited here, but
        // this is the actual fix regardless of what else reads it.
        product.setSourceIdentifier(new SourceIdentifier(
                OpenFoodFactsConstants.SOURCE_ID, offProduct.getCode()));

        String sourceUrl = ProductUrlBuilder.resolveUrl(product.getSourceIdentifier(), offProduct.getUrl());
        if (sourceUrl != null) {
            product.setSourceUrl(sourceUrl, language);
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
     * Name to store, see OffProductNames.pick() for the order. Null when the record
     * has no name and no brand: setName() then leaves the name empty and the
     * screens show R.string.unknown_product (translated), instead of an English
     * "Unknown Product" stored as if it were the product's name.
     */
    private String getLocalizedName(OpenFoodFactsProduct offProduct, String language) {
        return OffProductNames.pick(language,
                offProduct.getProductNameEn(), offProduct.getProductNameFr(),
                offProduct.getProductName(),
                offProduct.getGenericNameEn(), offProduct.getGenericNameFr(),
                offProduct.getGenericName(),
                offProduct.getBrands());
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

        // Set if we found any image - upgrade the 400px display URL to the full
        // original for the hero (thumbnail below keeps the small variant).
        if (imageUrl != null && !imageUrl.trim().isEmpty()) {
            product.setImageUrl(OffImageUrls.toFullResolutionUrl(imageUrl));
        }

        // Try thumbnail
        String thumbnailUrl = offProduct.getImageFrontSmallUrl();
        if (thumbnailUrl == null || thumbnailUrl.trim().isEmpty()) {
            thumbnailUrl = offProduct.getImageSmallUrl();
        }

        if (thumbnailUrl != null && !thumbnailUrl.trim().isEmpty()) {
            product.setThumbnailUrl(thumbnailUrl);
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
            // This is the most accurate, human-readable category - prefer it
            // over raw tags or free-text which are often language-mixed or stale.
            String nameEn = offProduct.getAgribalyseNameEn();
            String nameFr = offProduct.getAgribalyseNameFr();

            // Store primary language as main categoriesText
            String primary = "fr".equals(language) ? nameFr : nameEn;
            if (primary != null && !primary.trim().isEmpty()) {
                product.setCategoriesText(primary, language);
            }

            // Store other language in translations - no extra API call needed
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

        // Walk backwards - most specific tag is at the end of the hierarchy
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
     * OFF's "_100g" nutriment fields hold values per 100g OR per 100ml
     * depending on how the product is sold (OFF's own documented field
     * semantics - the suffix does not mean grams). OFF signals volume via
     * the quantity string ("33 cl", "1 L", "6 x 25cl"), which OFF itself
     * parses server-side into product_quantity. A volume unit in the
     * quantity string means the label - and therefore the _100g fields -
     * are per-100ml. Unrecognized or absent quantity defaults to per-100g,
     * matching the app's historical behavior for all OFF rows.
     */
    private static final Pattern VOLUME_QUANTITY = Pattern.compile(
            "(?i)(?:^|[\\d\\s.,x*(])\\s*(ml|cl|dl|l|litre|liter)s?\\b");

    private static NutritionBasis resolveBasis(String quantity) {
        if (quantity != null && VOLUME_QUANTITY.matcher(quantity).find()) {
            return NutritionBasis.PER_100ML;
        }
        return NutritionBasis.PER_100G;
    }

    /**
     * Map nutrition: one OFF nutrient set, AS SOLD FIRST.
     *
     * OFF can hold two sets for a product: as sold ("_100g") and prepared
     * following the package instructions ("_prepared_100g"). The as-sold set is
     * used whenever it has at least one value, even when a prepared set exists:
     * - it is the EU reference: nutrition values are for the food as sold, values
     *   after preparation are only an allowed alternative when the package gives
     *   the instructions (Regulation (EU) No 1169/2011, Article 31(3));
     * - it is what Ciqual, USDA and the other sources describe, so items stay
     *   comparable in a meal;
     * - the amount logged then means the product as bought.
     * The prepared set is used only when there is no as-sold value at all (some
     * labels only give prepared values: dried soups, infant formula, drink
     * powders), and then on its own, flagged PREPARED (see NutritionPreparation).
     * The two sets are never mixed: a per-field fallback used to put prepared
     * sugars next to as-sold energy on the same label.
     *
     * Choosing between both sets on screen is a possible later step, not done.
     */
    private void mapNutrition(FoodProduct product, OpenFoodFactsProduct offProduct) {
        OpenFoodFactsNutriments off = offProduct.getNutriments();
        if (off == null) {
            return;
        }

        Nutrition nutrition = nutrientSet(off, OpenFoodFactsNutriments.AS_SOLD,
                resolveBasis(offProduct.getQuantity()));
        if (!nutrition.hasData()) {
            Nutrition prepared = nutrientSet(off, OpenFoodFactsNutriments.PREPARED,
                    preparedBasis(offProduct.getNutritionDataPreparedPer()));
            if (prepared.hasData()) {
                prepared.setPreparation(NutritionPreparation.PREPARED);
                nutrition = prepared;
            }
        }

        // Set data source and calculate completeness
        nutrition.setDataSource("OpenFoodFacts");
        nutrition.setDataConfidence(DataConfidence.DECLARED);
        nutrition.calculateCompleteness();

        product.setNutrition(nutrition);
    }

    /**
     * Basis of the prepared set. resolveBasis() reads the package quantity, which
     * describes the product as sold (a 400 g tin of powder) and says nothing about
     * the prepared food. OFF's nutrition_data_prepared_per does: "100ml" or "100g".
     */
    private static NutritionBasis preparedBasis(String per) {
        return per != null && per.trim().equalsIgnoreCase("100ml")
                ? NutritionBasis.PER_100ML
                : NutritionBasis.PER_100G;
    }

    /**
     * One OFF nutrient set into a Nutrition object.
     *
     * Same rules for both sets: values already per 100 g / 100 ml (OFF computes
     * them from per-serving entries itself), in grams for every nutrient measured
     * by weight (converted by OffUnits to the app's g, mg or µg), kJ and kcal for
     * energy (the missing one computed with 1 kcal = 4.184 kJ), % vol for alcohol
     * (see OffAlcohol).
     *
     * @param set   OpenFoodFactsNutriments.AS_SOLD or .PREPARED
     * @param basis per 100 g or per 100 ml, for this set
     */
    private static Nutrition nutrientSet(OpenFoodFactsNutriments off, String set,
                                         NutritionBasis basis) {
        Nutrition nutrition = new Nutrition();
        nutrition.setBasis(basis);

        // ========== ENERGY (with auto-conversion between kJ and kcal) ==========
        // energy_100g is kJ too (OFF: the same as energy-kj, or computed from kcal)
        Double energyKj = off.get("energy-kj", set);
        if (energyKj == null) energyKj = off.get("energy", set);
        Double energyKcal = off.get("energy-kcal", set);

        // Auto-convert if one is missing
        if (energyKj == null && energyKcal != null) {
            energyKj = energyKcal * 4.184;
        } else if (energyKcal == null && energyKj != null) {
            energyKcal = energyKj / 4.184;
        }

        nutrition.setEnergyKj(energyKj);
        nutrition.setEnergyKcal(energyKcal);

        // ========== MACRONUTRIENTS ==========
        nutrition.setProteins(off.get("proteins", set));
        nutrition.setCarbohydrates(off.get("carbohydrates", set));
        nutrition.setFat(off.get("fat", set));
        nutrition.setFiber(off.get("fiber", set));
        // Alcohol: alcohol_100g is the strength in % vol, see OffAlcohol
        nutrition.setAlcohol(OffAlcohol.gramsPer100ml(off.get("alcohol", set),
                nutrition.getBasis() == NutritionBasis.PER_100ML));
        nutrition.setWater(off.get("water", set));

        // ========== CARBOHYDRATE DETAILS ==========
        nutrition.setSugars(off.get("sugars", set));
        nutrition.setStarch(off.get("starch", set));
        nutrition.setPolyols(off.get("polyols", set));
        nutrition.setGlucose(off.get("glucose", set));
        nutrition.setFructose(off.get("fructose", set));
        nutrition.setSucrose(off.get("sucrose", set));
        nutrition.setLactose(off.get("lactose", set));
        nutrition.setMaltose(off.get("maltose", set));
        nutrition.setGalactose(off.get("galactose", set));

        // ========== FAT DETAILS ==========
        nutrition.setSaturatedFat(off.get("saturated-fat", set));
        nutrition.setMonounsaturatedFat(off.get("monounsaturated-fat", set));
        nutrition.setPolyunsaturatedFat(off.get("polyunsaturated-fat", set));
        nutrition.setTransFat(off.get("trans-fat", set));
        nutrition.setCholesterol(fromGrams(off.get("cholesterol", set), NutrientInfo.CHOLESTEROL));

        // ========== OMEGA FATTY ACIDS ==========
        nutrition.setOmega3(off.get("omega-3-fat", set));
        nutrition.setOmega6(off.get("omega-6-fat", set));
        nutrition.setOmega9(off.get("omega-9-fat", set));
        nutrition.setLinoleicAcid(off.get("linoleic-acid", set));
        nutrition.setALA(fromGrams(off.get("alpha-linolenic-acid", set), NutrientInfo.ALA));
        nutrition.setEPA(fromGrams(off.get("eicosapentaenoic-acid", set), NutrientInfo.EPA));
        nutrition.setDHA(fromGrams(off.get("docosahexaenoic-acid", set), NutrientInfo.DHA));
        nutrition.setArachidonicAcid(fromGrams(off.get("arachidonic-acid", set), NutrientInfo.ARACHIDONIC_ACID));
        nutrition.setGammaLinolenicAcid(fromGrams(off.get("gamma-linolenic-acid", set), NutrientInfo.GAMMA_LINOLENIC_ACID));

        // ========== MINERALS ==========
        nutrition.setSalt(off.get("salt", set));
        nutrition.setSodium(fromGrams(off.get("sodium", set), NutrientInfo.SODIUM));
        nutrition.setCalcium(fromGrams(off.get("calcium", set), NutrientInfo.CALCIUM));
        nutrition.setPhosphorus(fromGrams(off.get("phosphorus", set), NutrientInfo.PHOSPHORUS));
        nutrition.setMagnesium(fromGrams(off.get("magnesium", set), NutrientInfo.MAGNESIUM));
        nutrition.setPotassium(fromGrams(off.get("potassium", set), NutrientInfo.POTASSIUM));
        nutrition.setChloride(fromGrams(off.get("chloride", set), NutrientInfo.CHLORIDE));
        nutrition.setIron(fromGrams(off.get("iron", set), NutrientInfo.IRON));
        nutrition.setZinc(fromGrams(off.get("zinc", set), NutrientInfo.ZINC));
        nutrition.setCopper(fromGrams(off.get("copper", set), NutrientInfo.COPPER));
        nutrition.setManganese(fromGrams(off.get("manganese", set), NutrientInfo.MANGANESE));
        nutrition.setSelenium(fromGrams(off.get("selenium", set), NutrientInfo.SELENIUM));
        nutrition.setIodine(fromGrams(off.get("iodine", set), NutrientInfo.IODINE));
        nutrition.setChromium(fromGrams(off.get("chromium", set), NutrientInfo.CHROMIUM));
        nutrition.setMolybdenum(fromGrams(off.get("molybdenum", set), NutrientInfo.MOLYBDENUM));
        nutrition.setFluoride(fromGrams(off.get("fluoride", set), NutrientInfo.FLUORIDE));

        // ========== VITAMINS ==========
        nutrition.setVitaminA(fromGrams(off.get("vitamin-a", set), NutrientInfo.VITAMIN_A));
        nutrition.setVitaminD(fromGrams(off.get("vitamin-d", set), NutrientInfo.VITAMIN_D));
        nutrition.setVitaminE(fromGrams(off.get("vitamin-e", set), NutrientInfo.VITAMIN_E));
        nutrition.setVitaminK(fromGrams(off.get("vitamin-k", set), NutrientInfo.VITAMIN_K));
        nutrition.setVitaminC(fromGrams(off.get("vitamin-c", set), NutrientInfo.VITAMIN_C));
        nutrition.setVitaminB1(fromGrams(off.get("vitamin-b1", set), NutrientInfo.VITAMIN_B1));
        nutrition.setVitaminB2(fromGrams(off.get("vitamin-b2", set), NutrientInfo.VITAMIN_B2));
        // OFF ids: niacin is "vitamin-pp", B5 is "pantothenic-acid" (nutrients
        // taxonomy); "vitamin-b3" / "vitamin-b5" do not exist and were never filled.
        nutrition.setVitaminB3(fromGrams(off.get("vitamin-pp", set), NutrientInfo.VITAMIN_B3));
        nutrition.setVitaminB5(fromGrams(off.get("pantothenic-acid", set), NutrientInfo.VITAMIN_B5));
        nutrition.setVitaminB6(fromGrams(off.get("vitamin-b6", set), NutrientInfo.VITAMIN_B6));
        nutrition.setVitaminB7(fromGrams(off.get("biotin", set), NutrientInfo.VITAMIN_B7));
        nutrition.setVitaminB9(fromGrams(off.get("vitamin-b9", set), NutrientInfo.VITAMIN_B9));
        nutrition.setVitaminB12(fromGrams(off.get("vitamin-b12", set), NutrientInfo.VITAMIN_B12));
        nutrition.setBetaCarotene(fromGrams(off.get("beta-carotene", set), NutrientInfo.BETA_CAROTENE));

        // ========== OTHER COMPOUNDS ==========
        nutrition.setCaffeine(fromGrams(off.get("caffeine", set), NutrientInfo.CAFFEINE));
        nutrition.setTaurine(fromGrams(off.get("taurine", set), NutrientInfo.TAURINE));

        return nutrition;
    }

    /**
     * OFF value in grams (a "_100g" field) in the unit of the app's field
     * (g, mg or µg), see OffUnits.
     */
    private static Double fromGrams(Double grams, NutrientInfo info) {
        return OffUnits.fromGrams(grams, info.getUnit());
    }

    /**
     * Map serving size.
     *
     * serving_size is free text ("1 biscuit (12 g)", "250 ml"): kept as the
     * description. serving_quantity is the same serving as a NUMBER, in
     * serving_quantity_unit (g or ml): it becomes the gram or ml equivalent, which
     * is what the default amount and the quantity input use. Parsing the text alone
     * lost it ("1 biscuit (12 g)" read as 1 x "biscuit", no grams).
     */
    private void mapServingSize(FoodProduct product, OpenFoodFactsProduct offProduct) {
        String text = offProduct.getServingSize();
        if (text != null && text.trim().isEmpty()) text = null;
        Double quantity = offProduct.getServingQuantity();
        boolean hasQuantity = quantity != null && quantity > 0
                && !quantity.isInfinite() && !quantity.isNaN();
        if (text == null && !hasQuantity) return;

        // Prepared values: OFF's serving may describe the powder (20 g) or the
        // prepared food (a 250 ml bowl). Only a volume with values per 100 ml can
        // only be the prepared food; anything else would scale the prepared values
        // by the powder's weight. Left out: the user sets the amount.
        Nutrition nutrition = product.getNutrition();
        if (nutrition != null && nutrition.isPrepared()
                && !(nutrition.getBasis() == NutritionBasis.PER_100ML
                     && isStatedVolume(text, offProduct.getServingQuantityUnit()))) {
            return;
        }

        try {
            boolean volume = hasQuantity && isVolumeServing(product, offProduct);
            ServingSize serving = text != null
                    ? new ServingSize(text.trim())           // parses the description
                    : new ServingSize(quantity, volume ? Unit.ML : Unit.G);
            if (hasQuantity) {
                if (volume) {
                    serving.setMlEquivalent(quantity);
                } else {
                    serving.setGramEquivalent(quantity);
                }
            }
            product.setServingSize(serving);
        } catch (Exception e) {
            Log.w(TAG, "Failed to map serving size: " + text + " / " + quantity, e);
        }
    }

    /**
     * Does the record itself say the serving is a volume: serving_quantity_unit
     * "ml", or, without a unit, a volume in the serving text ("250 ml", "25 cl")?
     */
    private static boolean isStatedVolume(String text, String unit) {
        if (unit != null && !unit.trim().isEmpty()) {
            return unit.trim().equalsIgnoreCase("ml");
        }
        return text != null && VOLUME_QUANTITY.matcher(text).find();
    }

    /**
     * Is serving_quantity in ml? serving_quantity_unit says so when present; older
     * records lack it, and then the nutrition basis decides (per 100 ml -> ml).
     */
    private static boolean isVolumeServing(FoodProduct product, OpenFoodFactsProduct offProduct) {
        String unit = offProduct.getServingQuantityUnit();
        if (unit != null && !unit.trim().isEmpty()) {
            return unit.trim().equalsIgnoreCase("ml");
        }
        NutritionBasis basis = product.getNutrition() != null
                ? product.getNutrition().getBasis()
                : resolveBasis(offProduct.getQuantity());
        return basis == NutritionBasis.PER_100ML;
    }
}