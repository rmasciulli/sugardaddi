package li.masciul.sugardaddi.data.database.entities;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverters;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import li.masciul.sugardaddi.core.enums.DataSourceType;
import li.masciul.sugardaddi.core.enums.ProductType;
import li.masciul.sugardaddi.core.models.FoodProduct;
import li.masciul.sugardaddi.core.models.ProductTranslation;
import li.masciul.sugardaddi.core.models.ServingSize;
import li.masciul.sugardaddi.core.models.SourceIdentifier;
import li.masciul.sugardaddi.data.database.converters.GeneralConverters;
import li.masciul.sugardaddi.data.database.converters.ProductTranslationMapConverter;
import li.masciul.sugardaddi.data.database.converters.ServingSizeConverter;
import li.masciul.sugardaddi.data.network.ApiConfig;
import android.util.Log;

/**
 * FoodProductEntity - Database entity for food products (Hybrid Translation)
 *
 * ARCHITECTURE UPDATE:
 * - Nutrition data stored separately in NutritionEntity table
 * - NEW: Hybrid translation system replacing LocalizedContent
 * - Primary fields store content in currentLanguage (direct access)
 * - Translation map stores all OTHER languages (ProductTranslation objects)
 * - Searchable text pre-computed for fast queries
 *
 * STORAGE EFFICIENCY:
 * - OLD: 50+ fields × N languages in LocalizedContent map
 * - NEW: 9 primary fields + lightweight ProductTranslation map
 * - Reduction: ~85% less translation storage
 *
 * CACHE MANAGEMENT:
 * - Tracks access patterns via accessCount
 * - Supports favorite marking for offline access
 * - Automatic timestamp tracking for cache invalidation
 */
@Entity(
        tableName = "food_products",
        indices = {
                @Index(value = {"barcode"}, unique = true),
                @Index(value = {"sourceId", "originalId"}),
                @Index(value = {"isFavorite"}),
                @Index(value = {"lastUpdated"}),
                @Index(value = {"accessCount"}),
                @Index(value = {"category_code"}),
                @Index(value = {"sourceId", "category_code"}),
        }
)
public class FoodProductEntity {

    private static final String TAG = ApiConfig.DATABASE_LOG_TAG;

    // ========== PRIMARY KEY ==========
    @PrimaryKey
    @NonNull
    private String id = "";  // Format: "SOURCE:ID" e.g., "OFF:3017620422003"

    // ========== IDENTIFICATION ==========
    private String barcode;
    private String originalId;
    private String sourceId;
    private String productType;  // Stored as String for Room compatibility

    // ========== EXTENDED IDENTIFICATION ==========
    /**
     * Scientific name. Null for most products.
     * Populated for Ciqual aquatic/fruit/veg entries from alim_nom_sci.
     */
    @ColumnInfo(name = "scientific_name")
    private String scientificName;

    /**
     * Category code for JOIN-based stats and alternative queries.
     * Ciqual: ssgrp_code or ssssgrp_code. OFF: null until Phase 2D.
     */
    @ColumnInfo(name = "category_code")
    private String categoryCode;

    // ========== PRIMARY CONTENT (in currentLanguage) ==========
    private String name;
    private String genericName;
    private String brand;
    private String description;
    private String ingredients;
    private String categoriesText;
    private String packaging;
    private String origins;
    private String stores;

    // ========== LANGUAGE MANAGEMENT (NEW v3.0) ==========
    private String currentLanguage = "en";
    private boolean needsDefaultLanguageUpdate = false;

    @TypeConverters(ProductTranslationMapConverter.class)
    private Map<String, ProductTranslation> translations;

    private String searchableText;

    // ========== MEDIA ==========

    // Remote URLs - provided by the data source, never written to disk.
    private String thumbnailUrl;
    private String imageUrl;

    /**
     * Auto-cached local paths - written by the repositories (cacheFavouriteImages)
     * via ImageDownloader. Downloaded automatically (e.g. on favourite). Deleted when
     * no longer needed. Stored in "sugardaddi/thumbnails/" and "sugardaddi/products/"
     * respectively.
     */
    private String thumbnailPath;
    private String imagePath;

    /**
     * User-defined local paths - set explicitly via ImagePickerHelper.
     * Co-exist with the auto-cached counterparts; take display priority over them.
     * Stored in "sugardaddi/thumbnails/({id}_custom.jpg)" and "sugardaddi/products/".
     */
    private String userThumbnailPath;
    private String userImagePath;

    // ========== PRODUCT CHARACTERISTICS ==========
    private String nutriScore;
    private String ecoScore;
    private String novaGroup;
    private String quantity;

    // ========== PHYSICAL PROPERTIES ==========
    private boolean isLiquid = false;
    private Double density;

    // ========== DIETARY FLAGS ==========
    private boolean isOrganic = false;
    private boolean isVegan = false;
    private boolean isVegetarian = false;
    private boolean isGlutenFree = false;
    private boolean isPalmOilFree = false;
    private boolean isFairTrade = false;

    // ========== ALLERGEN FLAGS (NEW v3.0) ==========
    /**
     * Allergen bit flags using AllergenUtils constants
     * Combines both definite allergens AND traces for safety
     */
    private int allergenFlags = 0;

    // ========== COMPLEX OBJECTS ==========
    @TypeConverters(ServingSizeConverter.class)
    private ServingSize servingSize;

    @TypeConverters(GeneralConverters.class)
    private Set<String> tags;

    // ========== QUALITY METRICS ==========
    private float dataCompleteness = 0.0f;
    private int dataQualityScore = 0;

    // ========== CACHE METADATA ==========
    private boolean isFavorite = false;
    private int accessCount = 0;

    // True for rows populated by a downloaded dataset (Ciqual / USDA bulk import).
    // Such rows are exempt from TTL eviction - they're dataset members, not
    // on-demand cache - and are removed only via Settings ▸ cache management.
    private boolean localImport = false;

    // ========== TIMESTAMPS ==========
    private long createdAt;
    private long lastUpdated;   // last successful network sync - drives staleness
    private long lastViewed;    // last detail-screen open - drives eviction

    // ========== CONSTRUCTORS ==========

    public FoodProductEntity() {
        this.translations = new HashMap<>();
        this.tags = new HashSet<>();
        this.createdAt = System.currentTimeMillis();
        this.lastUpdated = this.createdAt;
        this.lastViewed = System.currentTimeMillis();
    }

    // ========== CONVERSION METHODS ==========

    /**
     * Convert entity to domain model
     * NOTE: Nutrition data must be loaded separately using FoodProductWithNutrition relation
     */
    public FoodProduct toFoodProduct() {
        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, "Converting FoodProductEntity to FoodProduct: " + id);
        }

        FoodProduct product = new FoodProduct();

        // Set identification
        if (sourceId != null && originalId != null) {
            product.setSourceIdentifier(new SourceIdentifier(sourceId, originalId));
        }
        product.setBarcode(this.barcode);
        product.setOriginalId(this.originalId);

        // Set data source
        if (sourceId != null) {
            try {
                product.setDataSource(DataSourceType.fromString(sourceId));
            } catch (Exception e) {
                if (ApiConfig.DEBUG_LOGGING) {
                    Log.w(TAG, "Unknown data source: " + sourceId);
                }
            }
        }

        // Set product type
        if (productType != null) {
            try {
                product.setProductType(ProductType.valueOf(productType));
            } catch (IllegalArgumentException e) {
                product.setProductType(ProductType.FOOD);
                if (ApiConfig.DEBUG_LOGGING) {
                    Log.w(TAG, "Invalid product type: " + productType);
                }
            }
        }

        // Set primary content fields
        product.setName(this.name);
        product.setGenericName(this.genericName);
        product.setBrand(this.brand);
        product.setDescription(this.description);
        product.setIngredients(this.ingredients);
        product.setCategoriesText(this.categoriesText);
        product.setPackaging(this.packaging);
        product.setOrigins(this.origins);
        product.setStores(this.stores);

        // Set language management
        product.setCurrentLanguage(this.currentLanguage);
        product.setNeedsDefaultLanguageUpdate(this.needsDefaultLanguageUpdate);
        product.setTranslations(this.translations != null ? this.translations : new HashMap<>());
        product.setSearchableText(this.searchableText);

        // Set media properties
        product.setThumbnailUrl(this.thumbnailUrl);
        product.setImageUrl(this.imageUrl);
        product.setThumbnailPath(this.thumbnailPath);
        product.setImagePath(this.imagePath);
        product.setUserThumbnailPath(this.userThumbnailPath);
        product.setUserImagePath(this.userImagePath);

        // Set product characteristics
        product.setNutriScore(this.nutriScore);
        product.setEcoScore(this.ecoScore);
        product.setNovaGroup(this.novaGroup);
        product.setQuantity(this.quantity);

        // Set physical properties
        product.setLiquid(this.isLiquid);
        product.setDensity(this.density);

        // Set dietary characteristics
        product.setOrganic(this.isOrganic);
        product.setVegan(this.isVegan);
        product.setVegetarian(this.isVegetarian);
        product.setGlutenFree(this.isGlutenFree);
        product.setPalmOilFree(this.isPalmOilFree);
        product.setFairTrade(this.isFairTrade);

        // Set allergen flags (NEW v3.0)
        product.setAllergenFlags(this.allergenFlags);

        // Set complex objects
        product.setServingSize(this.servingSize);
        product.setTags(this.tags != null ? new HashSet<>(this.tags) : new HashSet<>());

        // Set quality metrics
        product.setDataCompleteness(this.dataCompleteness);
        product.setDataQualityScore(this.dataQualityScore);

        // Set metadata
        product.setLastUpdated(this.lastUpdated);
        product.setCreatedAt(this.createdAt);
        product.setAccessCount(this.accessCount);

        // Extended identification
        product.setScientificName(this.scientificName);
        product.setCategoryCode(this.categoryCode);

        if (ApiConfig.DEBUG_LOGGING) {
            String statusFlag = product.needsDefaultLanguageUpdate() ? " [NEEDS_TRANSLATION]" : "";
            Log.d(TAG, "Converted entity to FoodProduct: " +
                    product.getName(product.getCurrentLanguage()) +
                    " (lang=" + product.getCurrentLanguage() + statusFlag +
                    ", nutrition must be loaded separately)");
        }

        // Recalculate completeness from current field state rather than relying
        // on the stored value - ensures correctness regardless of when/how the
        // entity was originally created (XML importer, API mapper, etc.)
        product.calculateCompleteness();

        return product;
    }

    /**
     * Create entity from domain model
     * NOTE: Nutrition data must be saved separately to NutritionEntity table
     */
    public static FoodProductEntity fromFoodProduct(FoodProduct product) {
        if (product == null) {
            return null;
        }

        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, "Converting FoodProduct to FoodProductEntity: " + product.getSearchableId());
        }

        FoodProductEntity entity = new FoodProductEntity();

        // Set identification
        entity.setId(product.getSearchableId());
        entity.setBarcode(product.getBarcode());
        entity.setOriginalId(product.getOriginalId());

        if (product.getSourceIdentifier() != null) {
            entity.setSourceId(product.getSourceIdentifier().getSourceId());
        } else if (product.getDataSource() != null) {
            entity.setSourceId(product.getDataSource().getId());
        }

        if (product.getProductType() != null) {
            entity.setProductType(product.getProductType().name());
        }

        // Set primary content fields (in currentLanguage)
        entity.setName(product.getName(product.getCurrentLanguage()));
        entity.setGenericName(product.getGenericName(product.getCurrentLanguage()));
        entity.setBrand(product.getBrand(product.getCurrentLanguage()));
        entity.setDescription(product.getDescription(product.getCurrentLanguage()));
        entity.setIngredients(product.getIngredients(product.getCurrentLanguage()));
        entity.setCategoriesText(product.getCategoriesText(product.getCurrentLanguage()));
        entity.setPackaging(product.getPackaging(product.getCurrentLanguage()));
        entity.setOrigins(product.getOrigins(product.getCurrentLanguage()));
        entity.setStores(product.getStores(product.getCurrentLanguage()));

        // Set language management
        entity.setCurrentLanguage(product.getCurrentLanguage());
        entity.setNeedsDefaultLanguageUpdate(product.needsDefaultLanguageUpdate());
        entity.setTranslations(product.getTranslations());
        entity.setSearchableText(product.getSearchableText());

        // Set media properties
        entity.setThumbnailUrl(product.getThumbnailUrl());
        entity.setImageUrl(product.getImageUrl());
        entity.setThumbnailPath(product.getThumbnailPath());
        entity.setImagePath(product.getImagePath());
        entity.setUserThumbnailPath(product.getUserThumbnailPath());
        entity.setUserImagePath(product.getUserImagePath());

        // Set product characteristics
        entity.setNutriScore(product.getNutriScore());
        entity.setEcoScore(product.getEcoScore());
        entity.setNovaGroup(product.getNovaGroup());
        entity.setQuantity(product.getQuantity());

        // Set physical properties
        entity.setLiquid(product.isLiquid());
        entity.setDensity(product.getDensity());

        // Set dietary characteristics
        entity.setOrganic(product.isOrganic());
        entity.setVegan(product.isVegan());
        entity.setVegetarian(product.isVegetarian());
        entity.setGlutenFree(product.isGlutenFree());
        entity.setPalmOilFree(product.isPalmOilFree());
        entity.setFairTrade(product.isFairTrade());

        // Set allergen flags (NEW v3.0)
        entity.setAllergenFlags(product.getAllergenFlags());

        // Set complex objects
        entity.setServingSize(product.getServingSize());
        entity.setTags(product.getTags());

        // Set quality metrics
        entity.setDataCompleteness(product.getDataCompleteness());
        entity.setDataQualityScore(product.getDataQualityScore());

        // Set metadata
        entity.setCreatedAt(product.getCreatedAt() > 0 ?
                product.getCreatedAt() : System.currentTimeMillis());
        entity.setLastUpdated(product.getLastUpdated() > 0 ?
                product.getLastUpdated() : System.currentTimeMillis());
        entity.setAccessCount(product.getAccessCount());

        // Extended identification
        entity.setScientificName(product.getScientificName());
        entity.setCategoryCode(product.getCategoryCode());

        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, "Converted FoodProduct to entity: " + entity.getId());
        }

        return entity;
    }

    // ========== CACHE MANAGEMENT ==========

    public void recordAccess() {
        this.accessCount++;
        this.lastViewed = System.currentTimeMillis();
    }

    public boolean isStale(long maxAgeMillis) {
        long age = System.currentTimeMillis() - lastUpdated;
        return age > maxAgeMillis;
    }

    public void touch() {
        this.lastUpdated = System.currentTimeMillis();
    }

    // ========== UTILITY METHODS ==========

    public String getDisplayName() {
        return name != null ? name : (barcode != null ? "Product " + barcode : "Unknown Product");
    }

    public boolean hasBasicInfo() {
        return barcode != null && name != null && !name.trim().isEmpty();
    }

    public int calculateQualityScore() {
        int score = 0;
        int maxScore = 0;

        // Basic fields (40 points)
        maxScore += 10; if (barcode != null && !barcode.isEmpty()) score += 10;
        maxScore += 10; if (imageUrl != null && !imageUrl.isEmpty()) score += 10;
        maxScore += 10; if (quantity != null && !quantity.isEmpty()) score += 10;
        maxScore += 10; if (name != null && !name.isEmpty()) score += 10;

        // Quality indicators (30 points)
        maxScore += 10; if (nutriScore != null && !nutriScore.isEmpty()) score += 10;
        maxScore += 10; if (ecoScore != null && !ecoScore.isEmpty()) score += 10;
        maxScore += 10; if (novaGroup != null && !novaGroup.isEmpty()) score += 10;

        // Content richness (30 points)
        maxScore += 10; if (description != null && !description.isEmpty()) score += 10;
        maxScore += 10; if (ingredients != null && !ingredients.isEmpty()) score += 10;
        maxScore += 10; if (translations != null && !translations.isEmpty()) score += 10;

        return maxScore > 0 ? (int) ((score / (float) maxScore) * 100) : 0;
    }

    // ========== GETTERS AND SETTERS ==========

    @NonNull
    public String getId() { return id; }
    public void setId(@NonNull String id) { this.id = id; }

    public String getBarcode() { return barcode; }
    public void setBarcode(String barcode) { this.barcode = barcode; }

    public String getOriginalId() { return originalId; }
    public void setOriginalId(String originalId) { this.originalId = originalId; }

    public String getSourceId() { return sourceId; }
    public void setSourceId(String sourceId) { this.sourceId = sourceId; }

    public String getProductType() { return productType; }
    public void setProductType(String productType) { this.productType = productType; }

    // Primary content fields
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getGenericName() { return genericName; }
    public void setGenericName(String genericName) { this.genericName = genericName; }

    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getIngredients() { return ingredients; }
    public void setIngredients(String ingredients) { this.ingredients = ingredients; }

    public String getCategoriesText() { return categoriesText; }
    public void setCategoriesText(String categoriesText) { this.categoriesText = categoriesText; }

    public String getPackaging() { return packaging; }
    public void setPackaging(String packaging) { this.packaging = packaging; }

    public String getOrigins() { return origins; }
    public void setOrigins(String origins) { this.origins = origins; }

    public String getStores() { return stores; }
    public void setStores(String stores) { this.stores = stores; }

    // Language management
    public String getCurrentLanguage() { return currentLanguage; }
    public void setCurrentLanguage(String currentLanguage) { this.currentLanguage = currentLanguage; }

    public boolean isNeedsDefaultLanguageUpdate() { return needsDefaultLanguageUpdate; }
    public void setNeedsDefaultLanguageUpdate(boolean needsDefaultLanguageUpdate) {
        this.needsDefaultLanguageUpdate = needsDefaultLanguageUpdate;
    }

    public Map<String, ProductTranslation> getTranslations() { return translations; }
    public void setTranslations(Map<String, ProductTranslation> translations) {
        this.translations = translations;
    }

    public String getSearchableText() { return searchableText; }
    public void setSearchableText(String searchableText) { this.searchableText = searchableText; }

    // Media - remote URLs
    public String getThumbnailUrl() { return thumbnailUrl; }
    public void setThumbnailUrl(String thumbnailUrl) { this.thumbnailUrl = thumbnailUrl; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    // Media - auto-cached local paths
    public String getThumbnailPath() { return thumbnailPath; }
    public void setThumbnailPath(String thumbnailPath) { this.thumbnailPath = thumbnailPath; }

    public String getImagePath() { return imagePath; }
    public void setImagePath(String imagePath) { this.imagePath = imagePath; }

    // Media - user defined local paths
    public String getUserThumbnailPath() { return userThumbnailPath; }
    public void setUserThumbnailPath(String userThumbnailPath) { this.userThumbnailPath = userThumbnailPath; }

    public String getUserImagePath() { return userImagePath; }
    public void setUserImagePath(String userImagePath) { this.userImagePath = userImagePath; }

    // Product characteristics
    public String getNutriScore() { return nutriScore; }
    public void setNutriScore(String nutriScore) { this.nutriScore = nutriScore; }

    public String getEcoScore() { return ecoScore; }
    public void setEcoScore(String ecoScore) { this.ecoScore = ecoScore; }

    public String getNovaGroup() { return novaGroup; }
    public void setNovaGroup(String novaGroup) { this.novaGroup = novaGroup; }

    public String getQuantity() { return quantity; }
    public void setQuantity(String quantity) { this.quantity = quantity; }

    // Physical properties
    public boolean isLiquid() { return isLiquid; }
    public void setLiquid(boolean liquid) { isLiquid = liquid; }

    public Double getDensity() { return density; }
    public void setDensity(Double density) { this.density = density; }

    // Dietary flags
    public boolean isOrganic() { return isOrganic; }
    public void setOrganic(boolean organic) { isOrganic = organic; }

    public boolean isVegan() { return isVegan; }
    public void setVegan(boolean vegan) { isVegan = vegan; }

    public boolean isVegetarian() { return isVegetarian; }
    public void setVegetarian(boolean vegetarian) { isVegetarian = vegetarian; }

    public boolean isGlutenFree() { return isGlutenFree; }
    public void setGlutenFree(boolean glutenFree) { isGlutenFree = glutenFree; }

    public boolean isPalmOilFree() { return isPalmOilFree; }
    public void setPalmOilFree(boolean palmOilFree) { isPalmOilFree = palmOilFree; }

    public boolean isFairTrade() { return isFairTrade; }
    public void setFairTrade(boolean fairTrade) { isFairTrade = fairTrade; }

    public int getAllergenFlags() { return allergenFlags; }
    public void setAllergenFlags(int allergenFlags) { this.allergenFlags = allergenFlags; }

    // Complex objects
    public ServingSize getServingSize() { return servingSize; }
    public void setServingSize(ServingSize servingSize) { this.servingSize = servingSize; }

    public Set<String> getTags() { return tags; }
    public void setTags(Set<String> tags) { this.tags = tags; }

    // Quality metrics
    public float getDataCompleteness() { return dataCompleteness; }
    public void setDataCompleteness(float dataCompleteness) {
        this.dataCompleteness = dataCompleteness;
    }

    public int getDataQualityScore() { return dataQualityScore; }
    public void setDataQualityScore(int dataQualityScore) {
        this.dataQualityScore = dataQualityScore;
    }

    // Cache metadata
    public boolean isFavorite() { return isFavorite; }
    public void setFavorite(boolean favorite) { isFavorite = favorite; }

    public int getAccessCount() { return accessCount; }
    public void setAccessCount(int accessCount) { this.accessCount = accessCount; }

    public boolean isLocalImport() { return localImport; }
    public void setLocalImport(boolean localImport) { this.localImport = localImport; }

    // Timestamps
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(long lastUpdated) { this.lastUpdated = lastUpdated; }

    public long getLastViewed() { return lastViewed; }
    public void setLastViewed(long lastViewed) { this.lastViewed = lastViewed; }

    // ========== EXTENDED IDENTIFICATION ==========
    public String getScientificName() { return scientificName; }
    public void setScientificName(String scientificName) { this.scientificName = scientificName; }

    public String getCategoryCode() { return categoryCode; }
    public void setCategoryCode(String categoryCode) { this.categoryCode = categoryCode; }
}