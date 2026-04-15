package li.masciul.sugardaddi.data.sources.openfoodfacts;

import androidx.annotation.NonNull;

import li.masciul.sugardaddi.data.network.NetworkConfig;
import li.masciul.sugardaddi.data.network.RetryStrategy;

/**
 * OpenFoodFactsConfig - NetworkConfig implementation for OpenFoodFacts
 *
 * DUAL-DOMAIN ARCHITECTURE:
 * OpenFoodFacts uses TWO separate services for optimal performance:
 *
 * 1. SEARCH: search-a-licious (Elasticsearch-backed modern search API)
 *    - Domain: https://search.openfoodfacts.org/
 *    - Why: Official OFF search solution, full-text search, better performance
 *    - Docs: https://search.openfoodfacts.org/docs
 *    - Features: Autocomplete, advanced filters, user preferences
 *
 * 2. PRODUCTS: OFF v2 API (Product data and details)
 *    - Domain: https://world.openfoodfacts.org/api/v2/
 *    - Why: Comprehensive product data, nutrition facts, images
 *    - Docs: https://openfoodfacts.github.io/openfoodfacts-server/api/ref-v2/
 *
 * CONFIGURATION:
 * - Retry: EXPONENTIAL (3 attempts)
 * - Timeout: Standard (15s connect, 30s read, 15s write)
 * - User-Agent: SugarDaddi identification
 *
 * USAGE:
 * ```java
 * OpenFoodFactsConfig config = new OpenFoodFactsConfig();
 *
 * // For search (search-a-licious)
 * Retrofit searchRetrofit = new Retrofit.Builder()
 *     .baseUrl(config.getSearchBaseUrl())
 *     .client(NetworkClient.createHttpClient(config, context))
 *     .build();
 *
 * // For products (OFF v2)
 * Retrofit productRetrofit = NetworkClient.createRetrofit(config, context);
 * // Uses getBaseUrl() by default
 * ```
 *
 * @author SugarDaddi Team
 * @version 2.0 (Network Refactor)
 */
public class OpenFoodFactsConfig extends NetworkConfig {

    // ========== CONSTRUCTORS ==========

    /**
     * Create OpenFoodFactsConfig with default settings (PRODUCTION environment)
     */
    public OpenFoodFactsConfig() {
        this(Environment.PRODUCTION);
    }

    /**
     * Create OpenFoodFactsConfig with specific environment
     */
    public OpenFoodFactsConfig(@NonNull Environment environment) {
        super(OpenFoodFactsConstants.SOURCE_ID, environment);

        // Set retry strategy (EXPONENTIAL with 3 retries)
        setRetryStrategy(RetryStrategy.EXPONENTIAL);

        // Optional: Add custom headers
        // addHeader("Accept-Language", "en,fr");
        // addHeader("Accept-Encoding", "gzip");
    }

    // ========== ABSTRACT METHOD IMPLEMENTATIONS ==========

    /**
     * Get base URL for NetworkConfig contract
     * Defaults to PRODUCT base URL (most common use case)
     */
    @NonNull
    @Override
    protected String getBaseUrl() {
        return getProductBaseUrl();
    }

    @NonNull
    @Override
    protected String getUserAgent() {
        return OpenFoodFactsConstants.USER_AGENT;
    }

    // ========== DUAL BASE URLS ==========

    /**
     * Get search base URL (search-a-licious)
     *
     * Search-a-licious is OFF's official modern search solution with:
     * - Elasticsearch backend for fast, relevant results
     * - Full-text search across all product fields
     * - Autocomplete and suggestions
     * - Advanced filtering (nutrition, allergens, etc.)
     *
     * @return Search API base URL
     */
    @NonNull
    public String getSearchBaseUrl() {
        if (isProduction()) {
            // Production environment (TLD="org")
            return "https://search.openfoodfacts.org/";
        } else {
            // Staging environment for testing (TLD="net")
            return "https://search.openfoodfacts.net/";
        }
    }

    /**
     * Get product base URL (OFF v2 API)
     *
     * OFF v2 API provides:
     * - Complete product data
     * - Nutrition facts
     * - Images (front, nutrition label, ingredients)
     * - Categories, brands, stores
     * - Allergens and traces
     *
     * @return Product API base URL
     */
    @NonNull
    public String getProductBaseUrl() {
        if (isProduction()) {
            // Staging environment for testing (TLD="org")
            return "https://world.openfoodfacts.org/api/v2/";
        } else {
            // Staging environment for testing (TLD="net")
            return "https://world.openfoodfacts.net/api/v2/";
        }
    }

    // ========== RETRY LOGIC ==========

    @Override
    protected boolean shouldRetryForError(int statusCode, String error) {
        // OFF-specific retry logic

        if (statusCode == -1) {
            // Network errors (timeout, no connection) - retry
            return true;
        }

        if (statusCode == 429) {
            // Rate limiting - retry with exponential backoff
            return true;
        }

        if (statusCode >= 500 && statusCode < 600) {
            // Server errors - retry (might be temporary)
            return true;
        }

        if (statusCode == 404) {
            // Not found - don't retry (won't help)
            return false;
        }

        if (statusCode >= 400 && statusCode < 500) {
            // Client errors (except 429) - don't retry (our fault)
            return false;
        }

        // Default: retry for unknown errors
        return true;
    }

    // ========== UTILITY METHODS ==========

    /**
     * Check if search-a-licious is being used
     * @return true (always, it's our default search solution)
     */
    public boolean usesSearchAlicious() {
        return true;
    }

    /**
     * Get full search endpoint URL
     * @return Complete URL for search endpoint
     */
    @NonNull
    public String getFullSearchEndpoint() {
        return getSearchBaseUrl() + "search";
    }

    /**
     * Get full autocomplete endpoint URL
     * @return Complete URL for autocomplete endpoint
     */
    @NonNull
    public String getFullAutocompleteEndpoint() {
        return getSearchBaseUrl() + "autocomplete";
    }

    // ========== SUMMARY ==========

    @NonNull
    @Override
    public String getSummary() {
        return String.format("OFF[%s, %s, enabled=%s, search=%s]",
                getEnvironment().name(),
                getRetryStrategy().name(),
                isEnabled(),
                "search-a-licious");
    }

    @Override
    @NonNull
    public String toString() {
        return String.format("OpenFoodFactsConfig{env=%s, enabled=%s, search=%s, product=%s}",
                getEnvironment(), isEnabled(),
                getSearchBaseUrl(), getProductBaseUrl());
    }
}