package li.masciul.sugardaddi.data.sources.openfoodfacts.mappers;

import android.util.Log;

import li.masciul.sugardaddi.core.enums.DataSource;
import li.masciul.sugardaddi.data.sources.openfoodfacts.OpenFoodFactsConstants;
import li.masciul.sugardaddi.data.sources.openfoodfacts.api.dto.SearchAliciousHit;
import li.masciul.sugardaddi.data.sources.openfoodfacts.api.dto.SearchAliciousResponse;
import li.masciul.sugardaddi.core.models.FoodProduct;
import li.masciul.sugardaddi.core.models.Category;
import li.masciul.sugardaddi.core.models.ProductTranslation;
import li.masciul.sugardaddi.core.models.ServingSize;
import li.masciul.sugardaddi.core.enums.Unit;

import java.util.ArrayList;
import java.util.List;

/**
 * SearchAliciousMapper - Maps search-a-licious DTOs to domain models
 *
 * PURPOSE:
 * Converts SearchAliciousHit (Elasticsearch documents) to FoodProduct domain models
 * for use in search results and list views.
 *
 * KEY DIFFERENCES FROM OpenFoodFactsMapper:
 * - SearchAlicious returns PARTIAL data (optimized for search performance)
 * - Field names differ (nutrition_grades vs nutrition_grade)
 * - Brands is an array — take first element via getBrand()
 * - No comprehensive nutrition data — use OFF v2 API for product details
 * - Has ES-specific fields (completeness, _score)
 *
 * CATEGORY STRATEGY:
 * Searchalicious provides agribalyse.name_en AND name_fr simultaneously inside
 * ecoscore_data.agribalyse. Both languages are stored at mapping time:
 * - Primary language → product.categoriesText (via setCategoriesText)
 * - Other language   → product.translations[lang].categories
 * This eliminates the need for a second API call to get the other language's category.
 *
 * If agribalyse data is absent (product has no Green-Score), we fall back to
 * categories_tags[last], cleaned from its "en:" prefix.
 *
 * CATEGORY HIERARCHY:
 * categories_tags from Searchalicious is a stale snapshot — a subset of what
 * OFF v2 categories_hierarchy returns. We store it as the hierarchy for now, but
 * the full hierarchy is only available from the OFF v2 detail call.
 *
 * @version 3.0 - Bilingual category mapping via Agribalyse nested DTO
 */
public class SearchAliciousMapper {

    private static final String TAG = "SearchAliciousMapper";

    // ========== PUBLIC API ==========

    /**
     * Map search response to list of domain products.
     * Filters out invalid products (no barcode, no name, insufficient completeness).
     *
     * @param response SearchAlicious response with hits
     * @param language Requested display language ("en", "fr")
     * @return List of mapped FoodProducts, empty if no valid products
     */
    public static List<FoodProduct> mapSearchResponse(SearchAliciousResponse response, String language) {
        List<FoodProduct> products = new ArrayList<>();

        if (response == null || !response.hasResults()) {
            Log.d(TAG, "Search response is empty or null");
            return products;
        }

        int validCount = 0;
        int skippedCount = 0;

        for (SearchAliciousHit hit : response.getHits()) {
            FoodProduct product = mapToDomainModel(hit, language);
            if (product != null) {
                products.add(product);
                validCount++;
            } else {
                skippedCount++;
            }
        }

        Log.d(TAG, String.format("Mapped %d products, skipped %d invalid hits",
                validCount, skippedCount));

        return products;
    }

    /**
     * Map single SearchAliciousHit to a lightweight FoodProduct for list display.
     *
     * Contains: name, brand, category (bilingual), images, scores, quantity.
     * Does NOT contain: full nutrition, allergen details, full ingredients.
     * Use the OFF v2 API (OpenFoodFactsMapper) to enrich with complete data.
     *
     * @param hit      SearchAlicious hit (Elasticsearch document)
     * @param language Requested display language ("en", "fr")
     * @return FoodProduct or null if invalid (no code, no name)
     */
    public static FoodProduct mapToDomainModel(SearchAliciousHit hit, String language) {
        if (hit == null || hit.getCode() == null || hit.getCode().trim().isEmpty()) {
            Log.w(TAG, "Invalid hit: null or missing barcode");
            return null;
        }

        // Validate minimum data quality
        String productName = hit.getLocalizedProductName(language);
        if (productName == null || productName.trim().isEmpty()) {
            Log.w(TAG, "Skipping product " + hit.getCode() + ": no product name");
            return null;
        }

        // Create product with unified source identifier
        FoodProduct product = new FoodProduct(OpenFoodFactsConstants.SOURCE_ID, hit.getCode());
        product.setDataSource(DataSource.fromString(OpenFoodFactsConstants.SOURCE_ID));
        product.setBarcode(hit.getCode());

        mapProductInfo(product, hit, language);
        mapImages(product, hit);
        mapQualityScores(product, hit);
        mapCategory(product, hit, language);
        mapMetadata(product, hit);

        product.setLastUpdated(System.currentTimeMillis());

        return product;
    }

    // ========== PRIVATE MAPPING METHODS ==========

    /**
     * Map product basic info with language handling.
     *
     * Sets current language, name, brand, and quantity on the product.
     * The name is stored in the primary language field; the other language
     * is stored in translations if available (handled in mapCategory for categories,
     * and here for names via the hit's localization helpers).
     */
    private static void mapProductInfo(FoodProduct product, SearchAliciousHit hit, String language) {
        product.setCurrentLanguage(language);

        // Product name in requested language
        String name = hit.getLocalizedProductName(language);
        if (name != null && !name.trim().isEmpty()) {
            product.setName(name, language);
        }

        // Store the other language's name in translations if available.
        // Searchalicious provides both product_name_en and product_name_fr.
        // We use setName(name, lang) which correctly routes non-primary languages
        // into the translations map via FoodProduct's internal translation logic.
        String otherLang = "en".equals(language) ? "fr" : "en";
        String otherName = hit.getLocalizedProductName(otherLang);
        if (otherName != null && !otherName.trim().isEmpty()) {
            product.setName(otherName, otherLang);
        }

        // Brand (language-independent — take first from brands array)
        String brand = hit.getBrand();
        if (brand != null && !brand.trim().isEmpty()) {
            product.setBrand(brand, language);
        }

        // Quantity (language-independent)
        if (hit.getQuantity() != null && !hit.getQuantity().trim().isEmpty()) {
            product.setQuantity(hit.getQuantity());
        }
    }

    /**
     * Map images with fallback priority.
     * Uses hit.getBestImageUrl() which prefers small front image for list performance.
     */
    private static void mapImages(FoodProduct product, SearchAliciousHit hit) {
        String imageUrl = hit.getBestImageUrl();
        if (imageUrl != null) {
            product.setImageUrl(imageUrl);
            // If we have a dedicated small/thumbnail URL, set it separately
            String thumbUrl = hit.getImageFrontSmallUrl() != null
                    ? hit.getImageFrontSmallUrl()
                    : hit.getImageSmallUrl();
            if (thumbUrl != null) {
                product.setImageThumbnailUrl(thumbUrl);
            }
        }
    }

    /**
     * Map quality scores (Nutri-Score, EcoScore, NOVA).
     *
     * Searchalicious field names differ from OFF v2:
     * - "nutrition_grades" (plural) here vs "nutrition_grade" (singular) in OFF v2
     * - Both use lowercase letters (a-e / a-f)
     */
    private static void mapQualityScores(FoodProduct product, SearchAliciousHit hit) {
        // Nutri-Score: normalize to uppercase (A-E)
        if (hit.getNutritionGrades() != null && !hit.getNutritionGrades().trim().isEmpty()) {
            String nutriScore = hit.getNutritionGrades().trim();
            if (nutriScore.length() == 1) {
                product.setNutriScore(nutriScore.toUpperCase());
            }
        }

        // EcoScore: normalize to uppercase (A-F)
        if (hit.getEcoscoreGrade() != null && !hit.getEcoscoreGrade().trim().isEmpty()) {
            String ecoScore = hit.getEcoscoreGrade().trim();
            if (ecoScore.length() == 1) {
                product.setEcoScore(ecoScore.toUpperCase());
            }
        }

        // NOVA group (1-4, language-independent)
        if (hit.getNovaGroup() != null) {
            product.setNovaGroup(String.valueOf(hit.getNovaGroup()));
        }
    }

    /**
     * Map category with bilingual population and hierarchy storage.
     *
     * STRATEGY:
     * When agribalyse data is present (most products with a Green-Score):
     *   - Store name_en as EN category text
     *   - Store name_fr as FR category text
     *   Both are populated in a single pass since Searchalicious returns both together.
     *
     * When agribalyse data is absent (product has no Green-Score, ~20% of OFF):
     *   - Fall back to categories_tags[last], cleaned from "en:" prefix
     *   - Only English is available from the tag (no bilingual fallback)
     *
     * HIERARCHY:
     * categories_tags is stored as the category hierarchy. Note: this is a stale
     * snapshot — the full hierarchy is only available from the OFF v2 detail call.
     * category_hierarchy is populated but NOTE: it is not persisted to the DB yet
     * (Phase 2 will add the column).
     *
     * BUG FIX (vs previous version): Previously, categoryList was set from agribalyse
     * then immediately overwritten by categories_tags. Now agribalyse is used for
     * categoriesText (display), and categories_tags populates the hierarchy separately.
     */
    private static void mapCategory(FoodProduct product, SearchAliciousHit hit, String language) {

        if (hit.hasAgribalyseData()) {
            // === AGRIBALYSE PATH: bilingual, standardized ===
            // Get the name for the primary (requested) language
            String primaryCategory = hit.getLocalizedAgribalyseName(language);
            if (primaryCategory != null && !primaryCategory.trim().isEmpty()) {
                product.setCategoriesText(primaryCategory, language);
            }

            // Store the other language directly via setCategoriesText — it routes into
            // translations map automatically when lang != primary language.
            String otherLang = "en".equals(language) ? "fr" : "en";
            String otherCategory = hit.getLocalizedAgribalyseName(otherLang);
            if (otherCategory != null && !otherCategory.trim().isEmpty()) {
                product.setCategoriesText(otherCategory, otherLang);
            }

            // Also store the agribalyse code for future taxonomy linking (Phase 2)
            // For now, this is just logged; the DB column doesn't exist yet
            String agriCode = hit.getAgribalyseCode();
            if (agriCode != null) {
                Log.d(TAG, "Agribalyse code for " + hit.getCode() + ": " + agriCode);
            }

        } else {
            // === FALLBACK PATH: most specific real-English tag from categories_tags ===
            // categories_tags[last] is NOT safe — OFF allows French contributors to
            // enter French words via the English interface, producing tags like
            // "en:petales-de-ble-chocolates" that have "en:" prefix but French content.
            // We walk backwards from the last tag to find the last genuinely English one.
            String bestTag = getMostSpecificEnglishTag(hit.getCategoriesTags());
            if (bestTag != null) {
                String cleaned = cleanCategoryTag(bestTag);
                if (!cleaned.isEmpty()) {
                    product.setCategoriesText(cleaned, language);
                }
            }
        }

        // Store categories_tags as the hierarchy (independent of display category).
        // This gives us the taxonomy breadcrumb even when not displayed.
        // BUG FIX: separate from categoriesText — do not overwrite the display category.
        if (hit.getCategoriesTags() != null && !hit.getCategoriesTags().isEmpty()) {
            product.setCategoryHierarchy(hit.getCategoriesTags());
        }
    }

    /**
     * Map metadata: completeness score, quantity/serving size.
     */
    private static void mapMetadata(FoodProduct product, SearchAliciousHit hit) {
        // Completeness maps to data quality fields
        if (hit.getCompleteness() != null) {
            product.setDataCompleteness(hit.getCompleteness().floatValue());
            product.setDataQualityScore((int) (hit.getCompleteness() * 100));
        }

        // Parse serving size from quantity string
        if (hit.getQuantity() != null && !hit.getQuantity().trim().isEmpty()) {
            ServingSize servingSize = parseServingSize(hit.getQuantity());
            if (servingSize != null) {
                product.setServingSize(servingSize);
            }
        }
    }

    // ========== UTILITY METHODS ==========

    /**
     * Clean an OFF category tag for display.
     * Removes the language prefix and formats as sentence case (first word capitalized).
     *
     * Examples:
     * - "en:biscuits-and-cakes"         → "Biscuits and cakes"
     * - "en:dark-chocolate-biscuits"    → "Dark chocolate biscuits"
     * - "fr:produits-laitiers"          → "Produits laitiers"
     * - "en:plain-skyrs"                → "Plain skyrs"
     *
     * Note: Sentence case (only first word) is intentional — these are category names,
     * not titles. Title case (every word capitalized) looks odd for long category names.
     *
     * @param categoryTag Raw category tag from OFF/Searchalicious
     * @return Cleaned, displayable category name
     */
    private static String cleanCategoryTag(String categoryTag) {
        if (categoryTag == null || categoryTag.trim().isEmpty()) {
            return "";
        }

        String cleaned = categoryTag.trim();

        // Remove language prefix (e.g., "en:", "fr:")
        int colonIndex = cleaned.indexOf(':');
        if (colonIndex >= 0 && colonIndex < cleaned.length() - 1) {
            cleaned = cleaned.substring(colonIndex + 1);
        }

        // Replace hyphens and underscores with spaces
        cleaned = cleaned.replace("-", " ").replace("_", " ");

        // Sentence case: capitalize only the first character
        if (!cleaned.isEmpty()) {
            cleaned = Character.toUpperCase(cleaned.charAt(0)) + cleaned.substring(1).toLowerCase();
        }

        return cleaned;
    }

    /**
     * Get the most specific English category tag from a categories_tags list.
     *
     * Walks the list from end to start (most→least specific) and returns the
     * last tag that has a genuinely English slug. Skips tags where the slug
     * content is detectably French or another language, even if prefixed "en:".
     *
     * @param tags List of category tags from Searchalicious
     * @return Most specific real-English tag, or null if none found
     */
    private static String getMostSpecificEnglishTag(List<String> tags) {
        if (tags == null || tags.isEmpty()) return null;

        for (int i = tags.size() - 1; i >= 0; i--) {
            String tag = tags.get(i);
            if (tag != null && tag.startsWith("en:") && isLikelyEnglishTag(tag)) {
                return tag;
            }
        }
        return null;
    }

    /**
     * Check whether a category tag's slug content appears to be English.
     *
     * OFF tags French-language content with "en:" when a French contributor uses
     * the English input field. This heuristic detects the most common false positives
     * in OFF's French-contributed taxonomy data.
     *
     * @param tag Full tag including "en:" prefix
     * @return false if slug is detectably non-English, true otherwise
     */
    private static boolean isLikelyEnglishTag(String tag) {
        if (tag == null) return false;
        String slug = tag.startsWith("en:") ? tag.substring(3) : tag;

        // Non-ASCII characters = not English
        for (char c : slug.toCharArray()) {
            if (c > 127) return false;
        }

        // Known French root words that appear in OFF taxonomy tags
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
     * Parse serving size from a quantity string.
     *
     * Handles formats like "100 g", "1 L", "250ml", "500g", "1.5 kg".
     *
     * @param quantityStr Quantity string from SearchAlicious
     * @return ServingSize or null if not parseable
     */
    private static ServingSize parseServingSize(String quantityStr) {
        if (quantityStr == null || quantityStr.trim().isEmpty()) {
            return null;
        }

        try {
            String normalized = quantityStr.trim().toLowerCase();

            // Extract numeric part and unit part
            String numberPart = normalized.replaceAll("[^0-9.,]", "");
            String unitPart = normalized.replaceAll("[0-9.,\\s]", "");

            if (numberPart.isEmpty()) {
                return null;
            }

            double amount = Double.parseDouble(numberPart.replace(',', '.'));
            Unit unit = parseUnit(unitPart);
            if (unit == null) {
                unit = Unit.G; // default to grams if unit unrecognized
            }

            ServingSize servingSize = new ServingSize(amount, unit);
            servingSize.setDescription(quantityStr);
            return servingSize;

        } catch (NumberFormatException e) {
            Log.w(TAG, "Failed to parse quantity: " + quantityStr, e);
            return null;
        }
    }

    /**
     * Parse unit string to Unit enum.
     *
     * @param unitStr Unit string (e.g., "g", "ml", "l", "kg")
     * @return Unit enum value, or null if unrecognized
     */
    private static Unit parseUnit(String unitStr) {
        if (unitStr == null || unitStr.isEmpty()) return null;

        switch (unitStr.toLowerCase()) {
            case "g": case "gr": case "gram": case "grams":         return Unit.G;
            case "kg": case "kilo": case "kilogram": case "kilograms": return Unit.KG;
            case "mg": case "milligram": case "milligrams":         return Unit.MG;
            case "cg": case "centigram": case "centigrams":         return Unit.CG;
            case "l": case "liter": case "liters":
            case "litre": case "litres":                            return Unit.L;
            case "ml": case "milliliter": case "milliliters":
            case "millilitre": case "millilitres":                  return Unit.ML;
            case "cl": case "centiliter": case "centiliters":
            case "centilitre": case "centilitres":                  return Unit.CL;
            default:                                                return null;
        }
    }

    // ========== VALIDATION ==========

    /**
     * Check if a SearchAliciousHit has sufficient data for mapping.
     * Requires: barcode, product name (any language), completeness >= 0.5.
     */
    public static boolean isValidHit(SearchAliciousHit hit) {
        if (hit == null) return false;
        if (hit.getCode() == null || hit.getCode().trim().isEmpty()) return false;
        if (hit.getLocalizedProductName("en") == null) return false;
        return hit.isPertinent();
    }

    /**
     * Filter a list of hits to only include valid, pertinent ones.
     *
     * @param hits List of SearchAlicious hits to filter
     * @return Filtered list of valid hits
     */
    public static List<SearchAliciousHit> filterValidHits(List<SearchAliciousHit> hits) {
        List<SearchAliciousHit> valid = new ArrayList<>();
        if (hits == null) return valid;
        for (SearchAliciousHit hit : hits) {
            if (isValidHit(hit)) {
                valid.add(hit);
            }
        }
        return valid;
    }
}