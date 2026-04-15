package li.masciul.sugardaddi.core.models;

import java.util.*;

/**
 * ProductTranslation - Lightweight translation container for FoodProduct
 *
 * Contains ONLY translatable text fields for food products.
 * Much lighter than LocalizedContent (9 fields vs 50+).
 *
 * Design Philosophy:
 * - Store only what varies by language (names, descriptions)
 * - Exclude structured data (nutrition, allergens handled separately)
 * - Minimal memory footprint for efficient multi-language support
 * - Language-agnostic: language code tracked by parent map key
 */
public class ProductTranslation {

    // ========== CORE TRANSLATABLE FIELDS ==========
    private String name;                // Product name ("Apple" / "Pomme")
    private String genericName;         // Generic name ("Fruit" / "Fruit")
    private String brand;               // Brand name (rarely translated but possible)
    private String description;         // Product description
    private String ingredients;         // Ingredients list text
    private String categories;          // Category text ("Fruits" / "Fruits")

    // ========== PRODUCT-SPECIFIC FIELDS ==========
    private String packaging;           // Packaging info ("Plastic bottle" / "Bouteille plastique")
    private String origins;             // Origins ("France" / "France")
    private String stores;              // Where to buy ("Walmart, Target" / "Carrefour, Auchan")

    // ========== METADATA ==========
    private long lastUpdated;           // When this translation was last updated
    private String source;              // Translation source: "manual", "api", "crowdsourced"
    private boolean verified;           // Is this translation verified/trusted?

    // ========== CONSTRUCTORS ==========

    public ProductTranslation() {
        this.lastUpdated = System.currentTimeMillis();
        this.verified = false;
    }

    /**
     * Create translation with name (most common case)
     */
    public ProductTranslation(String name) {
        this();
        this.name = name;
    }

    /**
     * Create complete translation
     */
    public ProductTranslation(String name, String brand, String description) {
        this();
        this.name = name;
        this.brand = brand;
        this.description = description;
    }

    // ========== GETTERS AND SETTERS ==========

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        touch();
    }

    public String getGenericName() {
        return genericName;
    }

    public void setGenericName(String genericName) {
        this.genericName = genericName;
        touch();
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
        touch();
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
        touch();
    }

    public String getIngredients() {
        return ingredients;
    }

    public void setIngredients(String ingredients) {
        this.ingredients = ingredients;
        touch();
    }

    public String getCategories() {
        return categories;
    }

    public void setCategories(String categories) {
        this.categories = categories;
        touch();
    }

    public String getPackaging() {
        return packaging;
    }

    public void setPackaging(String packaging) {
        this.packaging = packaging;
        touch();
    }

    public String getOrigins() {
        return origins;
    }

    public void setOrigins(String origins) {
        this.origins = origins;
        touch();
    }

    public String getStores() {
        return stores;
    }

    public void setStores(String stores) {
        this.stores = stores;
        touch();
    }

    public long getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(long lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public boolean isVerified() {
        return verified;
    }

    public void setVerified(boolean verified) {
        this.verified = verified;
    }

    // ========== UTILITY METHODS ==========

    /**
     * Update timestamp on modification
     */
    private void touch() {
        this.lastUpdated = System.currentTimeMillis();
    }

    /**
     * Check if translation has any content
     */
    public boolean hasContent() {
        return hasText(name) || hasText(genericName) || hasText(brand) ||
                hasText(description) || hasText(ingredients) || hasText(categories) ||
                hasText(packaging) || hasText(origins) || hasText(stores);
    }

    /**
     * Check if translation is complete (has core fields)
     */
    public boolean isComplete() {
        return hasText(name) && hasText(description);
    }

    /**
     * Calculate completeness score (0.0 - 1.0)
     */
    public float getCompletenessScore() {
        int totalFields = 9;
        int filledFields = 0;

        if (hasText(name)) filledFields++;
        if (hasText(genericName)) filledFields++;
        if (hasText(brand)) filledFields++;
        if (hasText(description)) filledFields++;
        if (hasText(ingredients)) filledFields++;
        if (hasText(categories)) filledFields++;
        if (hasText(packaging)) filledFields++;
        if (hasText(origins)) filledFields++;
        if (hasText(stores)) filledFields++;

        return (float) filledFields / totalFields;
    }

    /**
     * Helper to check if text has content
     */
    private boolean hasText(String text) {
        return text != null && !text.trim().isEmpty();
    }

    /**
     * Create a copy of this translation
     */
    public ProductTranslation copy() {
        ProductTranslation copy = new ProductTranslation();
        copy.name = this.name;
        copy.genericName = this.genericName;
        copy.brand = this.brand;
        copy.description = this.description;
        copy.ingredients = this.ingredients;
        copy.categories = this.categories;
        copy.packaging = this.packaging;
        copy.origins = this.origins;
        copy.stores = this.stores;
        copy.lastUpdated = this.lastUpdated;
        copy.source = this.source;
        copy.verified = this.verified;
        return copy;
    }

    /**
     * Merge another translation into this one (non-null fields only)
     */
    public void mergeFrom(ProductTranslation other) {
        if (other == null) return;

        if (other.name != null) this.name = other.name;
        if (other.genericName != null) this.genericName = other.genericName;
        if (other.brand != null) this.brand = other.brand;
        if (other.description != null) this.description = other.description;
        if (other.ingredients != null) this.ingredients = other.ingredients;
        if (other.categories != null) this.categories = other.categories;
        if (other.packaging != null) this.packaging = other.packaging;
        if (other.origins != null) this.origins = other.origins;
        if (other.stores != null) this.stores = other.stores;

        // Update metadata if other is more recent
        if (other.lastUpdated > this.lastUpdated) {
            this.lastUpdated = other.lastUpdated;
            this.source = other.source;
            this.verified = other.verified;
        }
    }

    @Override
    public String toString() {
        return String.format("ProductTranslation{name='%s', completeness=%.2f, verified=%s}",
                name, getCompletenessScore(), verified);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProductTranslation that = (ProductTranslation) o;
        return Objects.equals(name, that.name) &&
                Objects.equals(brand, that.brand) &&
                Objects.equals(description, that.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, brand, description);
    }
}