package li.masciul.sugardaddi.core.models;

import li.masciul.sugardaddi.core.enums.DataSource;
import li.masciul.sugardaddi.core.enums.ProductType;
import li.masciul.sugardaddi.core.enums.Unit;
import li.masciul.sugardaddi.core.interfaces.Nutritional;
import li.masciul.sugardaddi.core.interfaces.Searchable;
import li.masciul.sugardaddi.core.interfaces.Categorizable;
import li.masciul.sugardaddi.core.interfaces.AllergenAware;
import li.masciul.sugardaddi.core.utils.AllergenUtils;
import android.content.Context;

import java.util.*;

/**
 * FoodProduct - Universal food product model (REFACTORED v2.0)
 *
 * HYBRID LANGUAGE ARCHITECTURE:
 * ===============================
 * PRIMARY FIELDS: Store content in the language we actually received (currentLanguage)
 * TRANSLATION MAP: Store all OTHER languages using lightweight ProductTranslation
 * DEFAULT_LANGUAGE: Target language for the app (typically "en")
 *
 * KEY CONCEPTS:
 * - currentLanguage: What language is stored in primary fields
 * - DEFAULT_LANGUAGE: What language we want for the app
 * - needsDefaultLanguageUpdate: Flag when currentLanguage != DEFAULT_LANGUAGE
 * - Smart getters: Return primary if matches requested language, else check translations
 * - Smart setters: Handle language switching with automatic backup
 * - searchableText: Pre-computed field containing all translations for fast DB queries
 *
 * MEMORY OPTIMIZATION:
 * - ProductTranslation has 6 fields vs LocalizedContent's 50+ fields
 * - No duplication when currentLanguage == DEFAULT_LANGUAGE
 * - Conditional backup only during language mismatches
 *
 * EXAMPLES:
 * ---------
 * Example 1 - OpenFoodFacts (English available, matches default):
 *   currentLanguage = "en"
 *   name = "Apple"
 *   translations.get("en") = null (no duplication)
 *   translations.get("fr") = ProductTranslation("Pomme")
 *   needsDefaultLanguageUpdate = false
 *
 * Example 2 - Ciqual XML (French available, English missing):
 *   Initial: currentLanguage = "fr", name = "Pomme", needsDefaultLanguageUpdate = true
 *   After sync: currentLanguage = "en", name = "Apple", translations.get("fr") = "Pomme"
 *
 * Example 3 - Ciqual API (Only French, need async English):
 *   currentLanguage = "fr"
 *   name = "Pomme"
 *   translations.get("fr") = null (no duplication)
 *   needsDefaultLanguageUpdate = true (will fetch English later)
 */
public class FoodProduct implements Nutritional, Searchable, Categorizable, AllergenAware {

    // ========== LANGUAGE CONFIGURATION ==========
    /**
     * DEFAULT_LANGUAGE: The target language for this app instance.
     * Users can change this via settings, but it's typically "en" for universal compatibility.
     * This determines what language we WANT in primary fields.
     */
    public static final String DEFAULT_LANGUAGE = "en";

    // ========== IDENTIFICATION ==========
    private SourceIdentifier sourceIdentifier;
    private String barcode;                     // Universal product code (EAN, UPC, etc.)
    private String originalId;                  // Source-specific ID
    private ProductType productType = ProductType.FOOD;

    /**
     * Scientific name. Null for most products.
     * Populated from Ciqual for aquatic products, fruits and vegetables (alim_nom_sci).
     * May also be populated from OFF for products with a scientific name.
     * Example: "Fragaria × ananassa" for strawberry.
     */
    private String scientificName;

    /**
     * Category code linking this product to the Ciqual category taxonomy.
     * For Ciqual products: ssgrp_code or ssssgrp_code (most specific available).
     *   Example: "0702" = "chocolate and chocolate products"
     * For OFF products: null until Phase 2D taxonomy mapping is implemented.
     * JOIN key for category stats and alternative product queries:
     *   - Category average comparison (how does this product rank?)
     *   - Alternatives in same subgroup (cross-source, Ciqual + OFF)
     *   - Cross-source comparison (Ciqual average vs. this OFF brand product)
     */
    private String categoryCode;

    private DataSource dataSource = null;       // Data source (set by mappers)
    private long lastUpdated;
    private long createdAt;

    // ========== PRIMARY CONTENT ==========
    /**
     * PRIMARY FIELDS store content in currentLanguage (what we actually have).
     * These are the "hot path" - accessed most frequently, optimized for performance.
     * Language may be English, French, or whatever we received from the data source.
     */
    private String name;                        // Product name in currentLanguage
    private String genericName;                 // Generic/common name
    private String brand;                       // Brand name
    private String manufacturer;                // Manufacturer
    private String description;                 // Product description
    private String ingredients;                 // Ingredients list text
    private String categoriesText;              // Categories as text (renamed from 'categories')
    private String packaging;
    private String origins;
    private String stores;

    // ========== LANGUAGE TRACKING ==========
    /**
     * currentLanguage: What language is ACTUALLY stored in primary fields
     * This may differ from DEFAULT_LANGUAGE, indicating we need translation
     */
    private String currentLanguage = DEFAULT_LANGUAGE;

    /**
     * needsDefaultLanguageUpdate: Flag indicating primary language mismatch
     * true when currentLanguage != DEFAULT_LANGUAGE (need async translation)
     * false when currentLanguage == DEFAULT_LANGUAGE (stable state)
     */
    private boolean needsDefaultLanguageUpdate = false;

    /**
     * Translation map: Contains ALL non-primary languages
     * Key = language code ("fr", "de", "es", etc.)
     * Value = ProductTranslation with translated text fields
     *
     * RULE: If currentLanguage == "en", then translations does NOT contain "en"
     * RULE: If currentLanguage == "fr" and DEFAULT_LANGUAGE == "en",
     *       translations may contain "en" once fetched
     */
    private Map<String, ProductTranslation> translations = new HashMap<>();

    /**
     * searchableText: Pre-computed field containing all names/brands/categories
     * from primary + all translations for efficient database LIKE queries.
     * Updated automatically when content changes.
     */
    private String searchableText;

    // ========== STRUCTURED DATA (language-independent) ==========
    private List<Category> categoryList = new ArrayList<>();
    private List<String> categoryHierarchy = new ArrayList<>();
    private List<String> allergens = new ArrayList<>();
    private List<String> additives = new ArrayList<>();
    private List<String> labels = new ArrayList<>();
    private Set<String> tags = new HashSet<>();

    // ========== NUTRITION & SERVING ==========
    private Nutrition nutrition;
    private ServingSize servingSize;

    // ========== PRODUCT CHARACTERISTICS ==========
    private String imageUrl;
    private String imageThumbnailUrl;
    private String nutriScore;                  // A-E nutritional rating
    private String ecoScore;                    // A-E environmental rating
    private String novaGroup;                   // 1-4 processing level
    private String quantity;                    // "500g", "1L", etc.

    // ========== PHYSICAL PROPERTIES ==========
    private boolean isLiquid;
    private Double density;                     // g/ml conversion factor

    // ========== DIETARY FLAGS ==========
    private boolean isOrganic;
    private boolean isVegan;
    private boolean isVegetarian;
    private boolean isGlutenFree;
    private boolean isPalmOilFree;
    private boolean isFairTrade;
    private int allergenFlags = 0;

    // ========== METADATA ==========
    private float dataCompleteness = 0.0f;
    private int dataQualityScore = 0;
    private boolean isFavorite = false;
    private int accessCount = 0;

    // ========== CONSTRUCTORS ==========

    public FoodProduct() {
        long currentTime = System.currentTimeMillis();
        this.createdAt = currentTime;
        this.lastUpdated = currentTime;
    }

    public FoodProduct(String sourceId, String originalId) {
        this();
        this.sourceIdentifier = new SourceIdentifier(sourceId, originalId);
        this.originalId = originalId;
    }

    public FoodProduct(DataSource dataSource, String originalId) {
        this();
        this.dataSource = dataSource;
        this.originalId = originalId;
        this.sourceIdentifier = new SourceIdentifier(dataSource.name(), originalId);
    }

    // ========== SMART LANGUAGE GETTERS ==========

    /**
     * Get product name in specified language.
     * OPTIMIZATION: Direct field access if language matches currentLanguage.
     *
     * @param language Requested language code ("en", "fr", etc.)
     * @return Name in requested language, or fallback to primary if not available
     */
    public String getName(String language) {
        // Fast path: requested language matches what's in primary field
        if (language == null || language.equals(currentLanguage)) {
            return name;
        }

        // Check translations
        ProductTranslation translation = translations.get(language);
        if (translation != null && translation.getName() != null) {
            return translation.getName();
        }

        // Fallback to primary
        return name;
    }

    /**
     * Get product name in default language (convenience method for UI)
     */
    public String getName() {
        return getName(DEFAULT_LANGUAGE);
    }

    public String getGenericName(String language) {
        if (language == null || language.equals(currentLanguage)) {
            return genericName;
        }

        ProductTranslation translation = translations.get(language);
        if (translation != null && translation.getGenericName() != null) {
            return translation.getGenericName();
        }

        return genericName;
    }

    public String getGenericName() {
        return getGenericName(DEFAULT_LANGUAGE);
    }

    public String getBrand(String language) {
        if (language == null || language.equals(currentLanguage)) {
            return brand;
        }

        ProductTranslation translation = translations.get(language);
        if (translation != null && translation.getBrand() != null) {
            return translation.getBrand();
        }

        return brand;
    }

    public String getBrand() {
        return getBrand(DEFAULT_LANGUAGE);
    }

    public String getDescription(String language) {
        if (language == null || language.equals(currentLanguage)) {
            return description;
        }

        ProductTranslation translation = translations.get(language);
        if (translation != null && translation.getDescription() != null) {
            return translation.getDescription();
        }

        return description;
    }

    public String getDescription() {
        return getDescription(DEFAULT_LANGUAGE);
    }

    public String getIngredients(String language) {
        if (language == null || language.equals(currentLanguage)) {
            return ingredients;
        }

        ProductTranslation translation = translations.get(language);
        if (translation != null && translation.getIngredients() != null) {
            return translation.getIngredients();
        }

        return ingredients;
    }

    public String getIngredients() {
        return getIngredients(DEFAULT_LANGUAGE);
    }

    /**
     * Get categories text in specified language.
     */
    public String getCategoriesText(String language) {
        if (language == null || language.equals(currentLanguage)) {
            return categoriesText;
        }

        ProductTranslation translation = translations.get(language);
        if (translation != null && translation.getCategories() != null) {
            return translation.getCategories();
        }

        return categoriesText;
    }

    public String getCategoriesText() {
        return getCategoriesText(DEFAULT_LANGUAGE);
    }

    public String getPackaging(String language) {
        if (language == null || language.equals(currentLanguage)) {
            return packaging;
        }

        ProductTranslation translation = translations.get(language);
        if (translation != null && translation.getPackaging() != null) {
            return translation.getPackaging();
        }

        return packaging;
    }

    public String getPackaging() {
        return getPackaging(DEFAULT_LANGUAGE);
    }

    public String getOrigins(String language) {
        if (language == null || language.equals(currentLanguage)) {
            return origins;
        }

        ProductTranslation translation = translations.get(language);
        if (translation != null && translation.getOrigins() != null) {
            return translation.getOrigins();
        }

        return origins;
    }

    public String getOrigins() {
        return getOrigins(DEFAULT_LANGUAGE);
    }

    public String getStores(String language) {
        if (language == null || language.equals(currentLanguage)) {
            return stores;
        }

        ProductTranslation translation = translations.get(language);
        if (translation != null && translation.getStores() != null) {
            return translation.getStores();
        }

        return stores;
    }

    public String getStores() {
        return getStores(DEFAULT_LANGUAGE);
    }

    // ========== SMART LANGUAGE SETTERS ==========

    /**
     * Set product name for a specific language with intelligent routing.
     *
     * LOGIC:
     * 1. If setting DEFAULT_LANGUAGE → always goes to primary field
     *    - If currentLanguage != DEFAULT_LANGUAGE, backup old primary to translations first
     *    - Update currentLanguage to DEFAULT_LANGUAGE
     *    - Clear needsDefaultLanguageUpdate flag
     *
     * 2. If setting currentLanguage → update primary field directly
     *
     * 3. If setting any other language → goes to translations map
     *
     * 4. Special case: If currentLanguage != DEFAULT_LANGUAGE, also backup to translations
     *    (conditional backup strategy for safety during mismatches)
     *
     * @param name Name to set
     * @param language Language code
     */
    public void setName(String name, String language) {
        if (name == null || language == null) return;

        if (language.equals(DEFAULT_LANGUAGE)) {
            // Setting the default language → always goes to primary

            // BACKUP: If we're switching from a different language, backup old primary
            if (!currentLanguage.equals(DEFAULT_LANGUAGE) && this.name != null) {
                ProductTranslation backup = getOrCreateTranslation(currentLanguage);
                backup.setName(this.name);
            }

            // Update primary field
            this.name = name;
            this.currentLanguage = DEFAULT_LANGUAGE;
            this.needsDefaultLanguageUpdate = false;

        } else if (language.equals(currentLanguage)) {
            // Updating the current primary language
            this.name = name;

            // CONDITIONAL BACKUP: If currentLanguage != DEFAULT_LANGUAGE, also save to translations
            // This protects against data loss when we later switch to DEFAULT_LANGUAGE
            if (!currentLanguage.equals(DEFAULT_LANGUAGE)) {
                ProductTranslation backup = getOrCreateTranslation(currentLanguage);
                backup.setName(name);
            }

        } else {
            // Different language → goes to translations
            ProductTranslation translation = getOrCreateTranslation(language);
            translation.setName(name);
        }

        updateSearchableText();
    }

    /**
     * Set product name in default language (convenience method)
     */
    public void setName(String name) {
        setName(name, DEFAULT_LANGUAGE);
    }

    public void setGenericName(String genericName, String language) {
        if (genericName == null || language == null) return;

        if (language.equals(DEFAULT_LANGUAGE)) {
            if (!currentLanguage.equals(DEFAULT_LANGUAGE) && this.genericName != null) {
                getOrCreateTranslation(currentLanguage).setGenericName(this.genericName);
            }
            this.genericName = genericName;
            this.currentLanguage = DEFAULT_LANGUAGE;
            this.needsDefaultLanguageUpdate = false;

        } else if (language.equals(currentLanguage)) {
            this.genericName = genericName;
            if (!currentLanguage.equals(DEFAULT_LANGUAGE)) {
                getOrCreateTranslation(currentLanguage).setGenericName(genericName);
            }

        } else {
            getOrCreateTranslation(language).setGenericName(genericName);
        }

        updateSearchableText();
    }

    public void setGenericName(String genericName) {
        setGenericName(genericName, DEFAULT_LANGUAGE);
    }

    public void setBrand(String brand, String language) {
        if (brand == null || language == null) return;

        if (language.equals(DEFAULT_LANGUAGE)) {
            if (!currentLanguage.equals(DEFAULT_LANGUAGE) && this.brand != null) {
                getOrCreateTranslation(currentLanguage).setBrand(this.brand);
            }
            this.brand = brand;
            this.currentLanguage = DEFAULT_LANGUAGE;
            this.needsDefaultLanguageUpdate = false;

        } else if (language.equals(currentLanguage)) {
            this.brand = brand;
            if (!currentLanguage.equals(DEFAULT_LANGUAGE)) {
                getOrCreateTranslation(currentLanguage).setBrand(brand);
            }

        } else {
            getOrCreateTranslation(language).setBrand(brand);
        }

        updateSearchableText();
    }

    public void setBrand(String brand) {
        setBrand(brand, DEFAULT_LANGUAGE);
    }

    public void setDescription(String description, String language) {
        if (description == null || language == null) return;

        if (language.equals(DEFAULT_LANGUAGE)) {
            if (!currentLanguage.equals(DEFAULT_LANGUAGE) && this.description != null) {
                getOrCreateTranslation(currentLanguage).setDescription(this.description);
            }
            this.description = description;
            this.currentLanguage = DEFAULT_LANGUAGE;
            this.needsDefaultLanguageUpdate = false;

        } else if (language.equals(currentLanguage)) {
            this.description = description;
            if (!currentLanguage.equals(DEFAULT_LANGUAGE)) {
                getOrCreateTranslation(currentLanguage).setDescription(description);
            }

        } else {
            getOrCreateTranslation(language).setDescription(description);
        }
    }

    public void setDescription(String description) {
        setDescription(description, DEFAULT_LANGUAGE);
    }

    public void setIngredients(String ingredients, String language) {
        if (ingredients == null || language == null) return;

        if (language.equals(DEFAULT_LANGUAGE)) {
            if (!currentLanguage.equals(DEFAULT_LANGUAGE) && this.ingredients != null) {
                getOrCreateTranslation(currentLanguage).setIngredients(this.ingredients);
            }
            this.ingredients = ingredients;
            this.currentLanguage = DEFAULT_LANGUAGE;
            this.needsDefaultLanguageUpdate = false;

        } else if (language.equals(currentLanguage)) {
            this.ingredients = ingredients;
            if (!currentLanguage.equals(DEFAULT_LANGUAGE)) {
                getOrCreateTranslation(currentLanguage).setIngredients(ingredients);
            }

        } else {
            getOrCreateTranslation(language).setIngredients(ingredients);
        }
    }

    public void setIngredients(String ingredients) {
        setIngredients(ingredients, DEFAULT_LANGUAGE);
    }

    /**
     * Set categories text for a specific language.
     * NOTE: Renamed from setCategories(String, String) to avoid any conflicts.
     */
    public void setCategoriesText(String categories, String language) {
        if (categories == null || language == null) return;

        if (language.equals(DEFAULT_LANGUAGE)) {
            if (!currentLanguage.equals(DEFAULT_LANGUAGE) && this.categoriesText != null) {
                getOrCreateTranslation(currentLanguage).setCategories(this.categoriesText);
            }
            this.categoriesText = categories;
            this.currentLanguage = DEFAULT_LANGUAGE;
            this.needsDefaultLanguageUpdate = false;

        } else if (language.equals(currentLanguage)) {
            this.categoriesText = categories;
            if (!currentLanguage.equals(DEFAULT_LANGUAGE)) {
                getOrCreateTranslation(currentLanguage).setCategories(categories);
            }

        } else {
            getOrCreateTranslation(language).setCategories(categories);
        }

        updateSearchableText();
    }

    public void setCategoriesText(String categories) {
        setCategoriesText(categories, DEFAULT_LANGUAGE);
    }

    public void setPackaging(String packaging, String language) {
        if (packaging == null || language == null) return;

        if (language.equals(DEFAULT_LANGUAGE)) {
            if (!currentLanguage.equals(DEFAULT_LANGUAGE) && this.packaging != null) {
                getOrCreateTranslation(currentLanguage).setPackaging(this.packaging);
            }
            this.packaging = packaging;
            this.currentLanguage = DEFAULT_LANGUAGE;
            this.needsDefaultLanguageUpdate = false;

        } else if (language.equals(currentLanguage)) {
            this.packaging = packaging;
            if (!currentLanguage.equals(DEFAULT_LANGUAGE)) {
                getOrCreateTranslation(currentLanguage).setPackaging(packaging);
            }

        } else {
            getOrCreateTranslation(language).setPackaging(packaging);
        }
    }

    public void setPackaging(String packaging) {
        setPackaging(packaging, DEFAULT_LANGUAGE);
    }

    public void setOrigins(String origins, String language) {
        if (origins == null || language == null) return;

        if (language.equals(DEFAULT_LANGUAGE)) {
            if (!currentLanguage.equals(DEFAULT_LANGUAGE) && this.origins != null) {
                getOrCreateTranslation(currentLanguage).setOrigins(this.origins);
            }
            this.origins = origins;
            this.currentLanguage = DEFAULT_LANGUAGE;
            this.needsDefaultLanguageUpdate = false;

        } else if (language.equals(currentLanguage)) {
            this.origins = origins;
            if (!currentLanguage.equals(DEFAULT_LANGUAGE)) {
                getOrCreateTranslation(currentLanguage).setOrigins(origins);
            }

        } else {
            getOrCreateTranslation(language).setOrigins(origins);
        }
    }

    public void setOrigins(String origins) {
        setOrigins(origins, DEFAULT_LANGUAGE);
    }

    public void setStores(String stores, String language) {
        if (stores == null || language == null) return;

        if (language.equals(DEFAULT_LANGUAGE)) {
            if (!currentLanguage.equals(DEFAULT_LANGUAGE) && this.stores != null) {
                getOrCreateTranslation(currentLanguage).setStores(this.stores);
            }
            this.stores = stores;
            this.currentLanguage = DEFAULT_LANGUAGE;
            this.needsDefaultLanguageUpdate = false;

        } else if (language.equals(currentLanguage)) {
            this.stores = stores;
            if (!currentLanguage.equals(DEFAULT_LANGUAGE)) {
                getOrCreateTranslation(currentLanguage).setStores(stores);
            }

        } else {
            getOrCreateTranslation(language).setStores(stores);
        }
    }

    public void setStores(String stores) {
        setStores(stores, DEFAULT_LANGUAGE);
    }

    // ========== TRANSLATION MANAGEMENT ==========

    /**
     * Get or create a ProductTranslation for the specified language
     */
    private ProductTranslation getOrCreateTranslation(String language) {
        ProductTranslation translation = translations.get(language);
        if (translation == null) {
            translation = new ProductTranslation();
            translations.put(language, translation);
        }
        return translation;
    }

    /**
     * Get all available languages for this product
     */
    public Set<String> getAvailableLanguages() {
        Set<String> languages = new HashSet<>();
        languages.add(currentLanguage); // Primary language
        languages.addAll(translations.keySet()); // All translations
        return languages;
    }

    /**
     * Check if a specific language is available
     */
    public boolean hasLanguage(String language) {
        if (language.equals(currentLanguage)) {
            return name != null; // Check if primary has content
        }
        ProductTranslation translation = translations.get(language);
        return translation != null && translation.hasContent();
    }

    /**
     * Get the translation map (for database persistence)
     */
    public Map<String, ProductTranslation> getTranslations() {
        return translations;
    }

    /**
     * Set the translation map (for database restoration)
     */
    public void setTranslations(Map<String, ProductTranslation> translations) {
        this.translations = translations != null ? translations : new HashMap<>();
        updateSearchableText();
    }

    /**
     * Add or update a translation
     */
    public void addTranslation(String language, ProductTranslation translation) {
        if (language != null && translation != null) {
            translations.put(language, translation);
            updateSearchableText();
        }
    }

    // ========== SEARCH OPTIMIZATION ==========

    /**
     * Update searchableText containing all names, brands, and categories
     * across all languages for efficient database LIKE queries.
     *
     * This field enables: WHERE name LIKE ? OR searchableText LIKE ?
     * Instead of complex JSON extraction from LocalizedContent.
     */
    private void updateSearchableText() {
        Set<String> searchTerms = new HashSet<>();

        // Add primary language content
        addSearchTerm(searchTerms, name);
        addSearchTerm(searchTerms, genericName);
        addSearchTerm(searchTerms, brand);
        addSearchTerm(searchTerms, categoriesText);

        // Add all translations
        for (ProductTranslation translation : translations.values()) {
            addSearchTerm(searchTerms, translation.getName());
            addSearchTerm(searchTerms, translation.getGenericName());
            addSearchTerm(searchTerms, translation.getBrand());
            addSearchTerm(searchTerms, translation.getCategories());
        }

        // Add barcode for search
        addSearchTerm(searchTerms, barcode);

        // Join all terms with spaces, lowercase for case-insensitive search
        this.searchableText = String.join(" ", searchTerms).toLowerCase();
    }

    /**
     * Helper to add non-null, non-empty terms to search set
     */
    private void addSearchTerm(Set<String> terms, String value) {
        if (value != null && !value.trim().isEmpty()) {
            terms.add(value.trim().toLowerCase());
        }
    }

    /**
     * Get searchable text (lazy initialization if needed)
     */
    public String getSearchableText() {
        if (searchableText == null) {
            updateSearchableText();
        }
        return searchableText;
    }

    /**
     * Set searchable text (for database restoration)
     */
    public void setSearchableText(String searchableText) {
        this.searchableText = searchableText;
    }

    // ========== LANGUAGE STATE MANAGEMENT ==========

    /**
     * Get current language stored in primary fields
     */
    public String getCurrentLanguage() {
        return currentLanguage;
    }

    /**
     * Set current language (use with caution - should be set by smart setters)
     */
    public void setCurrentLanguage(String currentLanguage) {
        this.currentLanguage = currentLanguage;
        this.needsDefaultLanguageUpdate = !currentLanguage.equals(DEFAULT_LANGUAGE);
    }

    /**
     * Check if product needs default language update
     */
    public boolean needsDefaultLanguageUpdate() {
        return needsDefaultLanguageUpdate;
    }

    /**
     * Set needs default language update flag
     */
    public void setNeedsDefaultLanguageUpdate(boolean needsUpdate) {
        this.needsDefaultLanguageUpdate = needsUpdate;
    }

    // ========== INTERFACE IMPLEMENTATIONS ==========

    // Searchable interface
    @Override
    public String getSearchableId() {
        return sourceIdentifier != null ? sourceIdentifier.getCombinedId() : originalId;
    }

    @Override
    public String getDisplayName(String language) {
        return getName(language);
    }

    @Override
    public Set<String> getSearchTags() {
        return new HashSet<>(tags);
    }

    @Override
    public DataSource getDataSource() {
        return dataSource;
    }

    // Categorizable interface - returns List<Category>, not String
    @Override
    public List<Category> getCategories(String language) {
        return new ArrayList<>(categoryList);
    }

    /**
     * Get primary category in specified language
     * Uses categoriesText which supports translations via ProductTranslation
     *
     * @param language Language code ("en", "fr", etc.)
     * @return Category name in requested language, or null if no category
     */
    public String getPrimaryCategory(String language) {
        // First try categoriesText which has proper translation support.
        // IMPORTANT: do NOT split on comma. categoriesText stores a single agribalyse
        // name (e.g. "Biscuit (cookie), with chocolate, prepacked") or a cleaned taxonomy
        // tag — never a comma-separated list. Splitting truncates at the first internal comma.
        String categoryText = getCategoriesText(language);

        if (categoryText != null && !categoryText.isEmpty()) {
            return categoryText.trim();
        }

        // Fallback to categoryList if categoriesText not available
        // Note: Category objects don't support translations yet
        return categoryList.isEmpty() ? null : categoryList.get(0).getName();
    }

    @Override
    public List<String> getCategoryHierarchy(String language) {
        return new ArrayList<>(categoryHierarchy);
    }

    // Nutritional interface
    @Override
    public Nutrition getNutrition() {
        return nutrition;
    }

    @Override
    public boolean hasNutritionData() {
        return nutrition != null && nutrition.hasData();
    }

    @Override
    public ServingSize getServingSize() {
        return servingSize;
    }

    @Override
    public boolean isLiquid() {
        return isLiquid;
    }

    // AllergenAware interface - only requires these two methods
    @Override
    public int getAllergenFlags() {
        return allergenFlags;
    }

    @Override
    public void setAllergenFlags(int allergenFlags) {
        this.allergenFlags = allergenFlags;
    }

    // Additional allergen methods (not from interface, but useful)
    public boolean hasAllergen(int allergenBitFlag) {
        return AllergenUtils.hasAllergen(allergenFlags, allergenBitFlag);
    }

    public String getAllergenNames(Context context) {
        return AllergenUtils.getAllergenNames(context, allergenFlags);
    }

    @Override
    public ProductType getProductType() {
        return productType;
    }

    // ========== STANDARD GETTERS AND SETTERS ==========

    public SourceIdentifier getSourceIdentifier() {
        return sourceIdentifier;
    }

    public void setSourceIdentifier(SourceIdentifier sourceIdentifier) {
        this.sourceIdentifier = sourceIdentifier;
    }

    public String getBarcode() {
        return barcode;
    }

    public void setBarcode(String barcode) {
        this.barcode = barcode;
        updateSearchableText();
    }

    public String getOriginalId() {
        return originalId;
    }

    public void setOriginalId(String originalId) {
        this.originalId = originalId;
    }

    public void setProductType(ProductType productType) {
        this.productType = productType;
    }

    public void setDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public long getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(long lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public String getManufacturer() {
        return manufacturer;
    }

    public void setManufacturer(String manufacturer) {
        this.manufacturer = manufacturer;
    }

    public List<Category> getCategoryList() {
        return categoryList;
    }

    public void setCategoryList(List<Category> categoryList) {
        this.categoryList = categoryList != null ? categoryList : new ArrayList<>();
    }

    public void setCategoryHierarchy(List<String> categoryHierarchy) {
        this.categoryHierarchy = categoryHierarchy != null ? categoryHierarchy : new ArrayList<>();
    }

    public List<String> getAllergens() {
        return allergens;
    }

    public void setAllergens(List<String> allergens) {
        this.allergens = allergens != null ? allergens : new ArrayList<>();
    }

    public List<String> getAdditives() {
        return additives;
    }

    public void setAdditives(List<String> additives) {
        this.additives = additives != null ? additives : new ArrayList<>();
    }

    public List<String> getLabels() {
        return labels;
    }

    public void setLabels(List<String> labels) {
        this.labels = labels != null ? labels : new ArrayList<>();
    }

    public Set<String> getTags() {
        return tags;
    }

    public void setTags(Set<String> tags) {
        this.tags = tags != null ? tags : new HashSet<>();
    }

    public void setNutrition(Nutrition nutrition) {
        this.nutrition = nutrition;
    }

    public void setServingSize(ServingSize servingSize) {
        this.servingSize = servingSize;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getImageThumbnailUrl() {
        return imageThumbnailUrl;
    }

    public void setImageThumbnailUrl(String imageThumbnailUrl) {
        this.imageThumbnailUrl = imageThumbnailUrl;
    }

    public String getNutriScore() {
        return nutriScore;
    }

    public void setNutriScore(String nutriScore) {
        this.nutriScore = nutriScore;
    }

    public String getEcoScore() {
        return ecoScore;
    }

    public void setEcoScore(String ecoScore) {
        this.ecoScore = ecoScore;
    }

    public String getNovaGroup() {
        return novaGroup;
    }

    public void setNovaGroup(String novaGroup) {
        this.novaGroup = novaGroup;
    }

    public String getQuantity() {
        return quantity;
    }

    public void setQuantity(String quantity) {
        this.quantity = quantity;
    }

    public void setLiquid(boolean liquid) {
        this.isLiquid = liquid;
    }

    public Double getDensity() {
        return density;
    }

    public void setDensity(Double density) {
        this.density = density;
    }

    public boolean isOrganic() {
        return isOrganic;
    }

    public void setOrganic(boolean organic) {
        this.isOrganic = organic;
    }

    public boolean isVegan() {
        return isVegan;
    }

    public void setVegan(boolean vegan) {
        this.isVegan = vegan;
    }

    public boolean isVegetarian() {
        return isVegetarian;
    }

    public void setVegetarian(boolean vegetarian) {
        this.isVegetarian = vegetarian;
    }

    public boolean isGlutenFree() {
        return isGlutenFree;
    }

    public void setGlutenFree(boolean glutenFree) {
        this.isGlutenFree = glutenFree;
    }

    public boolean isPalmOilFree() {
        return isPalmOilFree;
    }

    public void setPalmOilFree(boolean palmOilFree) {
        this.isPalmOilFree = palmOilFree;
    }

    public boolean isFairTrade() {
        return isFairTrade;
    }

    public void setFairTrade(boolean fairTrade) {
        this.isFairTrade = fairTrade;
    }

    public float getDataCompleteness() {
        return dataCompleteness;
    }

    public void setDataCompleteness(float dataCompleteness) {
        this.dataCompleteness = dataCompleteness;
    }

    public int getDataQualityScore() {
        return dataQualityScore;
    }

    public void setDataQualityScore(int dataQualityScore) {
        this.dataQualityScore = dataQualityScore;
    }

    public boolean isFavorite() {
        return isFavorite;
    }

    public void setFavorite(boolean favorite) {
        this.isFavorite = favorite;
    }

    public int getAccessCount() {
        return accessCount;
    }

    public void setAccessCount(int accessCount) {
        this.accessCount = accessCount;
    }

    public void incrementAccessCount() {
        this.accessCount++;
    }

    // ========== UTILITY METHODS ==========

    /**
     * Enrich this product with data from a richer version of the same product.
     *
     * This is the generic, source-agnostic mechanism for upgrading a lightweight
     * search result (from Searchalicious or any search API) with data from a fuller
     * version previously fetched and stored in the local database (typically from a
     * detail-view API call like OFF v2).
     *
     * DESIGN PRINCIPLES:
     * - Never downgrades: only fills null/empty fields, never replaces non-empty with empty
     * - Source-agnostic: uses dataCompleteness as the quality signal, not source name
     * - Attribution-safe: always preserves this.dataSource (UI attribution doesn't change)
     * - Idempotent: calling enrichWith() twice has the same effect as calling it once
     *
     * WHAT GETS UPGRADED:
     * - categoriesText: richer category name (e.g. agribalyse name) replaces tag fallback
     * - nutriScore, ecoScore, novaGroup: scores from full API response
     * - imageUrl, imageThumbnailUrl: better quality images
     * - nutrition: full nutrition data when only partial was available
     * - translations: merged, richer wins on per-language conflict
     * - allergenFlags: richer flags (superset of what search returned)
     *
     * WHAT IS NEVER CHANGED:
     * - dataSource: attribution must match what was originally shown to the user
     * - barcode, originalId, sourceIdentifier: identity fields are immutable
     * - name, brand: if already set, the search result name is kept (it's fine)
     *
     * @param richer A more complete version of the same product, typically from DB cache
     */
    public void enrichWith(FoodProduct richer) {
        if (richer == null) return;

        // No quality gate here — the caller (enrichSearchResultsFromDatabase) is
        // responsible for deciding WHEN to call enrichWith. It calls enrichWith only
        // when it found a DB-cached version of this product, meaning the user previously
        // fetched the full detail from OFF v2. That version is always authoritative
        // over a Searchalicious search result for the same barcode.
        // enrichWith itself just does a clean field-by-field merge: richer wins.

        // --- Category ---
        // Upgrade if this product has no category or only a raw tag fallback
        // (agribalyse names are richer and don't contain ":" or slug patterns)
        String richerCategory = richer.getCategoriesText(DEFAULT_LANGUAGE);
        String thisCategory   = this.getCategoriesText(DEFAULT_LANGUAGE);
        if (isEnrichable(thisCategory, richerCategory)) {
            // Set primary language category
            this.setCategoriesText(richerCategory, DEFAULT_LANGUAGE);
            // Also copy FR translation if richer has it and we don't
            String richerCategoryFr = richer.getCategoriesText("fr");
            String thisCategoryFr   = this.getCategoriesText("fr");
            if (isEnrichable(thisCategoryFr, richerCategoryFr)) {
                this.setCategoriesText(richerCategoryFr, "fr");
            }
        }

        // --- Quality scores ---
        if (isEnrichable(this.nutriScore, richer.nutriScore)) {
            this.nutriScore = richer.nutriScore;
        }
        if (isEnrichable(this.ecoScore, richer.ecoScore)) {
            this.ecoScore = richer.ecoScore;
        }
        if (isEnrichable(this.novaGroup, richer.novaGroup)) {
            this.novaGroup = richer.novaGroup;
        }

        // --- Images ---
        // Always prefer richer image when we have none; if both exist, keep ours
        // (search result may already have the right thumbnail)
        if (isEnrichable(this.imageUrl, richer.imageUrl)) {
            this.imageUrl = richer.imageUrl;
        }
        if (isEnrichable(this.imageThumbnailUrl, richer.imageThumbnailUrl)) {
            this.imageThumbnailUrl = richer.imageThumbnailUrl;
        }

        // --- Nutrition ---
        // Only upgrade if this has no nutrition and richer does
        if (this.nutrition == null && richer.nutrition != null && richer.nutrition.hasData()) {
            this.nutrition = richer.nutrition;
        }

        // --- Serving size ---
        if (this.servingSize == null && richer.servingSize != null) {
            this.servingSize = richer.servingSize;
        }

        // --- Allergen flags ---
        // Use bitwise OR: richer data is a superset, never lose allergen info
        if (richer.allergenFlags != 0) {
            this.allergenFlags |= richer.allergenFlags;
        }

        // --- Translations ---
        // Merge translation maps: for each language in richer, add to this if missing,
        // or merge individual fields if this already has a translation for that language
        for (Map.Entry<String, ProductTranslation> entry : richer.translations.entrySet()) {
            String lang = entry.getKey();
            ProductTranslation richTranslation = entry.getValue();
            if (richTranslation == null) continue;

            ProductTranslation existing = this.translations.get(lang);
            if (existing == null) {
                // Language not present at all — add it
                this.translations.put(lang, richTranslation);
            } else {
                // Language present — merge missing fields only (don't overwrite existing)
                existing.mergeFrom(richTranslation);
            }
        }

        // --- Category hierarchy ---
        if (this.categoryHierarchy.isEmpty() && !richer.categoryHierarchy.isEmpty()) {
            this.categoryHierarchy = new ArrayList<>(richer.categoryHierarchy);
        }

        // --- Update quality signals to reflect enrichment ---
        this.dataCompleteness = richer.dataCompleteness;
        this.dataQualityScore = richer.dataQualityScore;
        this.lastUpdated = System.currentTimeMillis();

        // Rebuild searchable text to include any new category/translation data
        updateSearchableText();
    }

    /**
     * Check if a field is worth enriching.
     * A field is enrichable if: the current value is null/empty AND the candidate is not.
     *
     * @param current   Current value on this product (may be null/empty)
     * @param candidate Candidate replacement from richer product
     * @return true if the field should be updated
     */
    /**
     * Check if a field should be replaced by the richer product's value.
     *
     * Since enrichWith() is only called when richer has higher dataCompleteness,
     * we prefer richer's value whenever it is non-null/non-empty — regardless of
     * whether this product already has a value. This is "richer wins", not "fill gaps".
     *
     * The only constraint: never replace with null or empty (never downgrade to nothing).
     */
    private boolean isEnrichable(String current, String candidate) {
        return candidate != null && !candidate.trim().isEmpty();
    }

    /**
     * Calculate and update data completeness score
     */
    public void calculateCompleteness() {
        int totalFields = 10;
        int filledFields = 0;

        if (name != null && !name.trim().isEmpty()) filledFields++;
        if (brand != null && !brand.trim().isEmpty()) filledFields++;
        if (description != null && !description.trim().isEmpty()) filledFields++;
        if (ingredients != null && !ingredients.trim().isEmpty()) filledFields++;
        if (!categoryList.isEmpty()) filledFields++;
        if (imageUrl != null) filledFields++;
        if (nutrition != null && nutrition.hasData()) filledFields++;
        if (servingSize != null) filledFields++;
        if (barcode != null) filledFields++;
        if (quantity != null) filledFields++;

        this.dataCompleteness = (float) filledFields / totalFields;
    }

    @Override
    public String toString() {
        return String.format("FoodProduct{name='%s', brand='%s', lang='%s', needsUpdate=%s}",
                name, brand, currentLanguage, needsDefaultLanguageUpdate);
    }
    // ========== SCIENTIFIC NAME ==========

    public String getScientificName() { return scientificName; }
    public void setScientificName(String scientificName) { this.scientificName = scientificName; }

    // ========== CATEGORY CODE ==========

    public String getCategoryCode() { return categoryCode; }
    public void setCategoryCode(String categoryCode) { this.categoryCode = categoryCode; }


}