package li.masciul.sugardaddi.data.sources.base;

import androidx.annotation.NonNull;

import li.masciul.sugardaddi.core.models.Error;

/**
 * DataSourceCallback - Callback interface for asynchronous data source operations
 *
 * UPDATED v2.0 (Network Refactor):
 * - Uses Error instead of DataSourceException directly
 * - Added optional onProgress() for long operations
 * - Simplified interface (3 core methods)
 * - Better documentation
 *
 * DESIGN PHILOSOPHY:
 * - Simple: Just 3 required methods (onSuccess, onError, onLoading)
 * - Type-safe: Generic type parameter for result
 * - Error-friendly: Receives Error objects (not raw exceptions)
 * - UI-friendly: Separate onLoading() for progress indication
 *
 * USAGE EXAMPLE:
 * ```java
 * dataSource.search("banana", "en", 10, new DataSourceCallback<SearchResult>() {
 *     @Override
 *     public void onSuccess(SearchResult result) {
 *         // Update UI with results
 *         adapter.setItems(result.items);
 *     }
 *
 *     @Override
 *     public void onError(Error error) {
 *         // Log error and show to user
 *         ErrorLogger.log(error, "During search");
 *         showSnackBar(error.getMessage());
 *     }
 *
 *     @Override
 *     public void onLoading() {
 *         // Show loading indicator
 *         progressBar.setVisibility(View.VISIBLE);
 *     }
 * });
 * ```
 *
 * THREAD SAFETY:
 * Callbacks should be executed on the main thread for UI updates.
 * Data sources are responsible for thread switching if needed.
 *
 * @param <T> Type of result (e.g., SearchResult, FoodProduct)
 *
 * @author SugarDaddi Team
 * @version 2.0 (Network Refactor)
 */
public interface DataSourceCallback<T> {

    /**
     * Called when operation completes successfully
     *
     * THREAD: Should be called on main thread
     * GUARANTEE: Called exactly once per operation (if successful)
     *
     * @param result The successful result (never null)
     */
    void onSuccess(@NonNull T result);

    /**
     * Called when operation fails
     *
     * THREAD: Should be called on main thread
     * GUARANTEE: Called exactly once per operation (if failed)
     * NOTE: Error is automatically logged by ErrorLogger before this is called
     *
     * @param error The error that occurred (never null)
     */
    void onError(@NonNull Error error);

    /**
     * Called when operation starts (optional)
     *
     * THREAD: Should be called on main thread
     * GUARANTEE: Called once before onSuccess/onError (if implemented)
     * USE CASE: Show loading spinner, disable search button, etc.
     *
     * Default implementation does nothing (backwards compatible)
     */
    default void onLoading() {
        // Default: no-op (for backwards compatibility)
    }

    /**
     * Called to report progress during long operations (optional)
     *
     * THREAD: Should be called on main thread
     * GUARANTEE: May be called multiple times during operation
     * USE CASE: Download progress, pagination progress, etc.
     *
     * Default implementation does nothing (backwards compatible)
     *
     * @param progress Progress value (0-100)
     * @param message Progress message (e.g., "Downloading page 2/5")
     */
    default void onProgress(int progress, @NonNull String message) {
        // Default: no-op (for backwards compatibility)
    }

    // ========== UTILITY CALLBACKS ==========

    /**
     * Simple success-only callback (ignores errors and loading)
     * Useful for fire-and-forget operations
     */
    abstract class SuccessOnly<T> implements DataSourceCallback<T> {
        @Override
        public void onError(@NonNull Error error) {
            // Ignore errors (or log them)
            android.util.Log.w("DataSourceCallback", "Error ignored: " + error.getMessage());
        }

        @Override
        public void onLoading() {
            // Ignore loading
        }
    }

    /**
     * No-op callback (does nothing)
     * Useful for operations where you don't care about the result
     */
    class NoOp<T> implements DataSourceCallback<T> {
        @Override
        public void onSuccess(@NonNull T result) {
            // No-op
        }

        @Override
        public void onError(@NonNull Error error) {
            // No-op (error is still logged by ErrorLogger)
        }

        @Override
        public void onLoading() {
            // No-op
        }
    }

    /**
     * Logging callback (just logs success/error)
     * Useful for debugging
     */
    class LogOnly<T> implements DataSourceCallback<T> {
        private final String tag;

        public LogOnly(String tag) {
            this.tag = tag;
        }

        @Override
        public void onSuccess(@NonNull T result) {
            android.util.Log.d(tag, "Success: " + result.toString());
        }

        @Override
        public void onError(@NonNull Error error) {
            android.util.Log.e(tag, "Error: " + error.toLogString());
        }

        @Override
        public void onLoading() {
            android.util.Log.d(tag, "Loading...");
        }
    }
}