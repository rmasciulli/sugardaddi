package li.masciul.sugardaddi.managers;

import android.util.Log;

import li.masciul.sugardaddi.core.models.Error;
import li.masciul.sugardaddi.data.network.ApiConfig;
import li.masciul.sugardaddi.core.models.FoodProduct;
import li.masciul.sugardaddi.data.repository.ProductRepository;

/**
 * ProductManager - High-level orchestration for product detail operations
 *
 * ✅ FIXED VERSION - Source-specific ID support
 *
 * CRITICAL FIX:
 * - Added detection for source-specific IDs (e.g., "CIQUAL:31020")
 * - loadProduct() now handles both barcodes and source IDs
 * - Split loading logic: barcodes → repository.loadProduct()
 *                        source IDs → repository.loadProductFromSource()
 *
 * This manager sits between the UI (ItemDetailsActivity) and the data layer
 * (ProductRepository), providing a clean interface for product-related operations
 * with intelligent state management and error handling.
 *
 * KEY RESPONSIBILITIES:
 * - Coordinate product loading with proper state management
 * - Handle favorite status tracking and updates
 * - Manage loading states and error conditions
 * - Provide clean callbacks for UI updates
 * - Abstract repository complexity from UI layer
 *
 * IDENTIFIER HANDLING:
 * - Barcodes: "3017620422003" → Uses OFF barcode lookup
 * - Source IDs: "CIQUAL:31020" → Uses source-specific getProduct()
 * - Detection: Checks for ":" separator to distinguish formats
 *
 * STATE MANAGEMENT:
 * - Tracks current product and loading state
 * - Prevents duplicate operations
 * - Handles cancellation and cleanup
 * - Provides state queries for UI
 *
 * INTEGRATION PATTERN:
 * Similar to SearchManager, this provides a clean interface between
 * UI and data layers, handling all the complex orchestration internally.
 */
public class ProductManager {

    private static final String TAG = ApiConfig.NETWORK_LOG_TAG;

    // Dependencies
    private final ProductRepository repository;

    // State tracking
    private ProductListener listener;
    private FoodProduct currentProduct;
    private String currentIdentifier;  // ✅ CHANGED: Was currentBarcode
    private boolean isLoading = false;
    private boolean isFavorite = false;

    /**
     * Listener interface for product events
     *
     * This interface provides all callbacks needed for proper UI state management
     * in ItemDetailsActivity. The methods are designed to map directly to UI actions.
     */
    public interface ProductListener {
        /**
         * Called when product is successfully loaded
         * Should update all UI elements with product data
         */
        void onProductLoaded(FoodProduct product);

        /**
         * Called when product loading fails
         * Should display appropriate error UI based on error type
         */
        void onProductError(Error error);

        /**
         * Called when product loading starts
         * Should show loading indicators
         */
        void onProductLoading();

        /**
         * Called when favorite status is determined
         * Should update favorite icon/menu
         */
        void onFavoriteStatusChanged(boolean isFavorite);

        /**
         * Called when favorite toggle completes
         * Should show confirmation and update UI
         */
        void onFavoriteToggled(boolean newStatus, String message);

        /**
         * Called when favorite operation fails
         * Should show error message
         */
        void onFavoriteError(String message);
    }

    /**
     * Constructor - requires configured ProductRepository
     */
    public ProductManager(ProductRepository repository) {
        this.repository = repository;

        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, "ProductManager initialized");
        }
    }

    /**
     * Set the event listener
     * This is typically ItemDetailsActivity implementing ProductListener
     */
    public void setListener(ProductListener listener) {
        this.listener = listener;
    }

    /**
     * ✅ FIXED: Load product by identifier (barcode OR source-specific ID)
     *
     * This method now intelligently handles both:
     * - Standard barcodes: "3017620422003" (EAN-13, UPC-A, etc.)
     * - Source-specific IDs: "CIQUAL:31020", "OPENFOODFACTS:12345"
     *
     * DETECTION LOGIC:
     * - If identifier contains ":" → Parse as SOURCE:ID
     * - Otherwise → Treat as barcode
     *
     * @param identifier Product identifier (barcode or source-specific ID)
     */
    public void loadProduct(String identifier) {
        if (identifier == null || identifier.trim().isEmpty()) {
            notifyError(Error.network("Invalid identifier provided", null));
            return;
        }

        String cleanIdentifier = identifier.trim();

        // Prevent duplicate loads
        if (isLoading && cleanIdentifier.equals(currentIdentifier)) {
            if (ApiConfig.DEBUG_LOGGING) {
                Log.d(TAG, "Product " + cleanIdentifier + " already loading - ignoring duplicate request");
            }
            return;
        }

        // Cancel any ongoing operation
        if (isLoading) {
            repository.cancelCurrentOperation();
        }

        currentIdentifier = cleanIdentifier;
        isLoading = true;

        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, "Loading product: " + cleanIdentifier);
        }

        notifyLoading();

        // ✅ NEW: Detect identifier type and route accordingly
        if (cleanIdentifier.contains(":")) {
            // SOURCE:ID format (e.g., "CIQUAL:31020")
            loadProductBySourceId(cleanIdentifier);
        } else {
            // Standard barcode (e.g., "3017620422003")
            loadProductByBarcode(cleanIdentifier);
        }
    }

    /**
     * ✅ NEW: Load product using source-specific ID
     *
     * Handles identifiers like "CIQUAL:31020" by:
     * 1. Parsing the source ID and product ID
     * 2. Delegating to repository's loadProductFromSource()
     *
     * @param identifier Source-specific identifier (e.g., "CIQUAL:31020")
     */
    private void loadProductBySourceId(String identifier) {
        // Parse SOURCE:ID format
        String[] parts = identifier.split(":", 2);
        if (parts.length != 2) {
            isLoading = false;
            notifyError(Error.validation(
                    "Invalid source identifier format. Expected SOURCE:ID",
                    "Received: " + identifier
            ));
            return;
        }

        String sourceId = parts[0].trim();
        String productId = parts[1].trim();

        if (sourceId.isEmpty() || productId.isEmpty()) {
            isLoading = false;
            notifyError(Error.validation(
                    "Source ID or product ID cannot be empty",
                    "Parsed from: " + identifier
            ));
            return;
        }

        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, String.format("Loading from source: %s, product ID: %s", sourceId, productId));
        }

        // Delegate to repository
        repository.loadProductFromSource(sourceId, productId, new ProductRepository.ProductCallback() {
            @Override
            public void onSuccess(FoodProduct product) {
                isLoading = false;
                currentProduct = product;

                if (ApiConfig.DEBUG_LOGGING) {
                    Log.d(TAG, "Product loaded successfully from " + sourceId + ": " +
                            product.getDisplayName(product.getCurrentLanguage()));
                }

                notifyProductLoaded(product);

                // Load favorite status in parallel
                loadFavoriteStatus();
            }

            @Override
            public void onError(Error error) {
                isLoading = false;
                currentProduct = null;

                Log.w(TAG, "Product loading failed from " + sourceId + ": " + error.getMessage());
                notifyError(error);
            }

            @Override
            public void onLoading() {
                // Already handled by notifyLoading() call above
            }
        });
    }

    /**
     * ✅ NEW: Load product using standard barcode
     *
     * Handles standard barcodes like "3017620422003" using
     * the existing repository barcode loading logic.
     *
     * @param barcode Standard product barcode
     */
    private void loadProductByBarcode(String barcode) {
        repository.loadProduct(barcode, new ProductRepository.ProductCallback() {
            @Override
            public void onSuccess(FoodProduct product) {
                isLoading = false;
                currentProduct = product;

                if (ApiConfig.DEBUG_LOGGING) {
                    Log.d(TAG, "Product loaded successfully: " + product.getDisplayName(product.getCurrentLanguage()));
                }

                notifyProductLoaded(product);

                // Load favorite status in parallel
                loadFavoriteStatus();
            }

            @Override
            public void onError(Error error) {
                isLoading = false;
                currentProduct = null;

                Log.w(TAG, "Product loading failed: " + error.getMessage());
                notifyError(error);
            }

            @Override
            public void onLoading() {
                // Already handled
            }
        });
    }

    /**
     * Refresh current product from network
     *
     * Forces a fresh fetch from the API, bypassing cache.
     * Useful for manual refresh or when data might be stale.
     */
    public void refreshProduct() {
        if (currentIdentifier == null) {
            notifyError(Error.network("No product to refresh", null));
            return;
        }

        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, "Refreshing product: " + currentIdentifier);
        }

        isLoading = true;
        notifyLoading();

        // ✅ UPDATED: Handle both identifier types on refresh
        if (currentIdentifier.contains(":")) {
            // Can't refresh source-specific IDs through repository.refreshProduct()
            // Instead, just reload using the source
            loadProductBySourceId(currentIdentifier);
        } else {
            // Standard barcode refresh
            repository.refreshProduct(currentIdentifier, new ProductRepository.ProductCallback() {
                @Override
                public void onSuccess(FoodProduct product) {
                    isLoading = false;
                    currentProduct = product;

                    if (ApiConfig.DEBUG_LOGGING) {
                        Log.d(TAG, "Product refreshed successfully: " + product.getDisplayName(product.getCurrentLanguage()));
                    }

                    notifyProductLoaded(product);
                }

                @Override
                public void onError(Error error) {
                    isLoading = false;
                    Log.w(TAG, "Product refresh failed: " + error.getMessage());
                    notifyError(error);
                }

                @Override
                public void onLoading() {
                    // Already handled
                }
            });
        }
    }

    /**
     * Toggle favorite status for current product
     *
     * Handles the complete favorite toggle workflow with proper UI feedback.
     */
    public void toggleFavorite() {
        if (currentProduct == null) {
            notifyFavoriteError("No product loaded to favorite");
            return;
        }

        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, "Toggling favorite for: " + currentProduct.getDisplayName(currentProduct.getCurrentLanguage()));
        }

        repository.toggleFavorite(currentProduct, new ProductRepository.FavoriteCallback() {
            @Override
            public void onFavoriteStatus(boolean isFavorite) {
                // Not used in toggle operation
            }

            @Override
            public void onFavoriteToggled(boolean newStatus) {
                isFavorite = newStatus;

                String message = newStatus ? "Added to favorites" : "Removed from favorites";

                if (ApiConfig.DEBUG_LOGGING) {
                    Log.d(TAG, "Favorite toggled: " + message);
                }

                notifyFavoriteStatusChanged(newStatus);
                notifyFavoriteToggled(newStatus, message);
            }

            @Override
            public void onError(String message) {
                Log.e(TAG, "Favorite toggle failed: " + message);
                notifyFavoriteError(message);
            }
        });
    }

    /**
     * Get current product
     *
     * @return Currently loaded product, or null if none loaded
     */
    public FoodProduct getCurrentProduct() {
        return currentProduct;
    }

    /**
     * ✅ DEPRECATED: Use getCurrentIdentifier() instead
     * Kept for backward compatibility
     *
     * @return Currently loaded identifier (may be barcode or source ID)
     */
    @Deprecated
    public String getCurrentBarcode() {
        return currentIdentifier;
    }

    /**
     * ✅ NEW: Get current identifier
     *
     * @return Currently loaded identifier (barcode or source-specific ID)
     */
    public String getCurrentIdentifier() {
        return currentIdentifier;
    }

    /**
     * Check if manager is currently loading
     *
     * @return true if loading operation is in progress
     */
    public boolean isLoading() {
        return isLoading;
    }

    /**
     * Check if current product is marked as favorite
     *
     * @return true if current product is in favorites
     */
    public boolean isFavorite() {
        return isFavorite;
    }

    /**
     * Cancel any ongoing operations
     *
     * Safe to call during activity destruction or when user navigates away.
     */
    public void cancelOperations() {
        if (isLoading) {
            repository.cancelCurrentOperation();
            isLoading = false;

            if (ApiConfig.DEBUG_LOGGING) {
                Log.d(TAG, "Product operations cancelled");
            }
        }
    }

    /**
     * Clean up resources and cancel operations
     *
     * Call this in activity onDestroy() to prevent memory leaks.
     */
    public void cleanup() {
        cancelOperations();
        listener = null;
        currentProduct = null;
        currentIdentifier = null;
        isFavorite = false;

        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, "ProductManager cleaned up");
        }
    }

    // ========== PRIVATE HELPER METHODS ==========

    /**
     * Load favorite status for current product
     */
    private void loadFavoriteStatus() {
        if (currentProduct == null) {
            return;
        }

        // Use searchableId (same as toggleFavorite)
        String productId = currentProduct.getSearchableId();
        if (productId == null || productId.isEmpty()) {
            return;
        }

        repository.getFavoriteStatus(productId, new ProductRepository.FavoriteCallback() {
            @Override
            public void onFavoriteStatus(boolean isFavorite) {
                ProductManager.this.isFavorite = isFavorite;
                notifyFavoriteStatusChanged(isFavorite);

                if (ApiConfig.DEBUG_LOGGING) {
                    Log.d(TAG, "Favorite status loaded: " + isFavorite);
                }
            }

            @Override
            public void onFavoriteToggled(boolean newStatus) {
                // Not used for status loading
            }

            @Override
            public void onError(String message) {
                // Favorite status loading is non-critical - just log the error
                Log.w(TAG, "Failed to load favorite status: " + message);
            }
        });
    }

    // ========== LISTENER NOTIFICATION METHODS ==========

    /**
     * Safely notify listener of product loaded event
     */
    private void notifyProductLoaded(FoodProduct product) {
        if (listener != null) {
            try {
                listener.onProductLoaded(product);
            } catch (Exception e) {
                Log.e(TAG, "Error in product loaded callback", e);
            }
        }
    }

    /**
     * Safely notify listener of error event
     */
    private void notifyError(Error error) {
        if (listener != null) {
            try {
                listener.onProductError(error);
            } catch (Exception e) {
                Log.e(TAG, "Error in product error callback", e);
            }
        }
    }

    /**
     * Safely notify listener of loading event
     */
    private void notifyLoading() {
        if (listener != null) {
            try {
                listener.onProductLoading();
            } catch (Exception e) {
                Log.e(TAG, "Error in product loading callback", e);
            }
        }
    }

    /**
     * Safely notify listener of favorite status change
     */
    private void notifyFavoriteStatusChanged(boolean isFavorite) {
        if (listener != null) {
            try {
                listener.onFavoriteStatusChanged(isFavorite);
            } catch (Exception e) {
                Log.e(TAG, "Error in favorite status callback", e);
            }
        }
    }

    /**
     * Safely notify listener of favorite toggle completion
     */
    private void notifyFavoriteToggled(boolean newStatus, String message) {
        if (listener != null) {
            try {
                listener.onFavoriteToggled(newStatus, message);
            } catch (Exception e) {
                Log.e(TAG, "Error in favorite toggled callback", e);
            }
        }
    }

    /**
     * Safely notify listener of favorite error
     */
    private void notifyFavoriteError(String message) {
        if (listener != null) {
            try {
                listener.onFavoriteError(message);
            } catch (Exception e) {
                Log.e(TAG, "Error in favorite error callback", e);
            }
        }
    }
}