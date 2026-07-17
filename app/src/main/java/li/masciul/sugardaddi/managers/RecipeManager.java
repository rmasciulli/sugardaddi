package li.masciul.sugardaddi.managers;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import li.masciul.sugardaddi.core.models.Error;
import li.masciul.sugardaddi.core.models.Recipe;
import li.masciul.sugardaddi.data.network.ApiConfig;
import li.masciul.sugardaddi.data.repository.RecipeRepository;

/**
 * RecipeManager - Orchestration layer for recipe detail operations.
 *
 * Mirrors the design of {@link ProductManager}: sits between the UI layer
 * (RecipeDetailsActivity) and the data layer (RecipeRepository), providing
 * clean state management, loading lifecycle callbacks, and favorite toggling.
 *
 * IDENTIFIER HANDLING
 * ===================
 * Accepts source-qualified searchable IDs - the same string returned by
 * {@link Recipe#getSearchableId()}:
 *
 *   "USER:some-uuid"  → Room lookup (user-created recipe)
 *   "THEMEALDB:52772" → LRU cache → Room → TheMealDB network
 *
 * Parsing and routing are delegated entirely to
 * {@link RecipeRepository#getRecipeBySearchableId}, keeping this class
 * free of source-specific logic.
 *
 * STATE MANAGEMENT
 * ================
 * - Tracks current recipe and loading state
 * - Prevents duplicate loads for the same ID
 * - Cancels any in-flight operation before starting a new one
 * - Provides isFavorite state for toolbar icon updates
 *
 * FAVORITE HANDLING
 * ==================
 * External recipes (e.g. TheMealDB) are persisted to Room on first
 * favorite interaction via RecipeRepository.setRecipeFavorite().
 *
 * LIFECYCLE
 * =========
 * Call cleanup() in RecipeDetailsActivity.onDestroy() to release the listener
 * reference and prevent memory leaks.
 */
public class RecipeManager {

    private static final String TAG = "RecipeManager";

    // ========== DEPENDENCIES ==========

    private final RecipeRepository repository;

    // ========== STATE ==========

    @Nullable private RecipeListener listener;
    @Nullable private Recipe currentRecipe;
    @Nullable private String currentSearchableId;

    // Candidate from a stale background refresh, held until the user applies it via the FAB.
    @Nullable private Recipe pendingRefreshCandidate;

    private boolean isLoading  = false;
    private boolean isFavorite = false;

    // ========== LISTENER INTERFACE ==========

    /**
     * Listener interface for recipe detail lifecycle events.
     *
     * Implemented by RecipeDetailsActivity. Each method maps directly to a
     * UI state transition - loading spinner, content display, or error screen.
     */
    public interface RecipeListener {

        /**
         * Called when the recipe is successfully loaded.
         * The activity should hide its loading state and render the recipe.
         *
         * @param recipe The loaded Recipe domain object
         */
        void onRecipeLoaded(@NonNull Recipe recipe);

        /**
         * Called when loading fails.
         * The activity should display an appropriate error screen.
         *
         * @param error Structured error with message and optional cause
         */
        void onRecipeError(@NonNull Error error);

        /**
         * Called when a load operation begins.
         * The activity should show a loading indicator.
         */
        void onRecipeLoading();

        /**
         * Called after the initial load to report the recipe's favorite state.
         * The activity should update the toolbar star icon accordingly.
         *
         * @param isFavorite true if this recipe is in the user's favorites
         */
        void onFavoriteStatusChanged(boolean isFavorite);

        /**
         * Called after a favorite toggle completes successfully.
         *
         * @param newStatus true if the recipe is now a favorite
         * @param message   Human-readable confirmation string (for Snackbar/Toast)
         */
        void onFavoriteToggled(boolean newStatus, @NonNull String message);

        /**
         * Called when a favorite operation fails.
         *
         * @param message Human-readable error description
         */
        void onFavoriteError(@NonNull String message);

        /**
         * A stale-triggered background refresh found changed upstream content.
         * The screen should surface the refresh affordance (FAB); tapping it calls
         * applyPendingRefresh(). Default no-op for listeners with no detail screen.
         */
        default void onRefreshAvailable() {}
    }

    // ========== CONSTRUCTOR ==========

    /**
     * @param repository The RecipeRepository to delegate data operations to.
     *                   Should be created with the application context.
     */
    public RecipeManager(@NonNull RecipeRepository repository) {
        this.repository = repository;

        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, "RecipeManager initialized");
        }
    }

    // ========== PUBLIC API ==========

    /**
     * Set the event listener (typically RecipeDetailsActivity).
     *
     * @param listener Listener to receive lifecycle callbacks, or null to clear
     */
    public void setListener(@Nullable RecipeListener listener) {
        this.listener = listener;
    }

    /**
     * Load a recipe by its source-qualified searchable ID.
     *
     * Accepts the string returned by {@link Recipe#getSearchableId()}.
     * Routing to the correct backend (Room or network) is handled entirely
     * by {@link RecipeRepository#getRecipeBySearchableId}.
     *
     * Duplicate loads for the currently-loading ID are silently ignored.
     * Any in-progress load for a different ID is cancelled first.
     *
     * @param searchableId Source-qualified recipe ID (e.g. "THEMEALDB:52772")
     */
    public void loadRecipe(@NonNull String searchableId) {
        String cleanId = searchableId.trim();

        if (cleanId.isEmpty()) {
            notifyError(Error.validation("Recipe ID cannot be empty", null));
            return;
        }

        // Ignore duplicate load request for the currently-loading ID
        if (isLoading && cleanId.equals(currentSearchableId)) {
            if (ApiConfig.DEBUG_LOGGING) {
                Log.d(TAG, "Recipe " + cleanId + " already loading - ignoring duplicate");
            }
            return;
        }

        // Cancel any ongoing operation before starting a new one
        if (isLoading) {
            repository.cancelSearch();
        }

        currentSearchableId = cleanId;
        pendingRefreshCandidate = null;
        isLoading = true;

        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, "Loading recipe: " + cleanId);
        }

        notifyLoading();

        repository.getRecipeBySearchableId(cleanId, new RecipeRepository.RecipeCallback() {
            @Override
            public void onSuccess(Recipe recipe) {
                isLoading = false;
                currentRecipe = recipe;

                if (ApiConfig.DEBUG_LOGGING) {
                    Log.d(TAG, "Recipe loaded: "
                            + recipe.getDisplayName(recipe.getCurrentLanguage())
                            + " [source=" + recipe.getDataSource() + "]");
                }

                notifyRecipeLoaded(recipe);
                loadFavoriteStatus();
            }

            @Override
            public void onError(String message) {
                isLoading = false;
                currentRecipe = null;
                Log.w(TAG, "Recipe load failed: " + message);
                notifyError(Error.network(message, null));
            }

            @Override
            public void onRefreshAvailable(Recipe candidate) {
                pendingRefreshCandidate = candidate;
                notifyRefreshAvailable();
            }
        });
    }

    /**
     * Apply the candidate offered by a stale background refresh (FAB tap).
     * Saves the already-fetched candidate and re-renders - no second network call.
     */
    public void applyPendingRefresh() {
        if (pendingRefreshCandidate == null) return;
        Recipe candidate = pendingRefreshCandidate;
        pendingRefreshCandidate = null;
        repository.applyCandidate(candidate, new RecipeRepository.RecipeCallback() {
            @Override
            public void onSuccess(Recipe recipe) {
                currentRecipe = recipe;          // keep manager state in sync with the applied row
                notifyRecipeLoaded(recipe);
            }
            @Override
            public void onError(String message) {
                notifyError(Error.network(message, null));
            }
        });
    }

    /** Safely notify the listener that a refresh candidate is available. */
    private void notifyRefreshAvailable() {
        if (listener != null) {
            try {
                listener.onRefreshAvailable();
            } catch (Exception e) {
                Log.e(TAG, "Error in refresh-available callback", e);
            }
        }
    }

    /**
     * Re-read the current recipe from Room and re-notify after a local image edit,
     * so the render and overflow menu rebuild from the authoritative row instead of
     * patched in-memory state.
     */
    public void reloadCurrentFromCache() {
        if (currentRecipe == null) return;
        repository.readFromCache(currentRecipe.getSearchableId(),
                new RecipeRepository.RecipeCallback() {
                    @Override public void onSuccess(Recipe recipe) {
                        currentRecipe = recipe;
                        notifyRecipeLoaded(recipe);
                    }
                    @Override public void onError(String message) {
                        Log.w(TAG, "Reload from cache failed: " + message);
                    }
                });
    }

    /**
     * Toggle the favorite state of the currently-loaded recipe.
     * Recipes are persisted to Room on first toggle via
     * {@link RecipeRepository#setRecipeFavorite}. Does nothing if
     * no recipe is currently loaded.
     */
    public void toggleFavorite() {
        if (currentRecipe == null) {
            notifyFavoriteError("No recipe loaded");
            return;
        }

        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, "Toggling favorite for: "
                    + currentRecipe.getDisplayName(currentRecipe.getCurrentLanguage()));
        }

        boolean newFavoriteState = !currentRecipe.isFavorite();

        repository.setRecipeFavorite(
                currentRecipe,
                newFavoriteState,
                new RecipeRepository.RecipeOperationCallback() {
                    @Override
                    public void onSuccess() {
                        // Sync local state
                        currentRecipe.setFavorite(newFavoriteState);
                        isFavorite = newFavoriteState;

                        String message = newFavoriteState
                                ? "Added to favorites"
                                : "Removed from favorites";

                        if (ApiConfig.DEBUG_LOGGING) {
                            Log.d(TAG, "Favorite toggled: " + message);
                        }

                        notifyFavoriteStatusChanged(newFavoriteState);
                        notifyFavoriteToggled(newFavoriteState, message);
                    }

                    @Override
                    public void onError(String error) {
                        Log.e(TAG, "Favorite toggle failed: " + error);
                        notifyFavoriteError(error);
                    }
                });
    }

    // ========== STATE QUERIES ==========

    /** @return The currently loaded Recipe, or null if none has been loaded yet */
    @Nullable
    public Recipe getCurrentRecipe() {
        return currentRecipe;
    }

    /** @return The searchable ID passed to the last loadRecipe() call, or null */
    @Nullable
    public String getCurrentSearchableId() {
        return currentSearchableId;
    }

    /** @return true if a load operation is currently in progress */
    public boolean isLoading() {
        return isLoading;
    }

    /** @return true if the currently-loaded recipe is marked as a favorite */
    public boolean isFavorite() {
        return isFavorite;
    }

    // ========== LIFECYCLE ==========

    /**
     * Cancel any in-progress operations.
     * Safe to call at any time - no-op if nothing is loading.
     */
    public void cancelOperations() {
        if (isLoading) {
            repository.cancelSearch();
            isLoading = false;

            if (ApiConfig.DEBUG_LOGGING) {
                Log.d(TAG, "Recipe operations cancelled");
            }
        }
    }

    /**
     * Release all held references.
     * Must be called in RecipeDetailsActivity.onDestroy() to prevent leaks.
     */
    public void cleanup() {
        cancelOperations();
        listener        = null;
        currentRecipe   = null;
        currentSearchableId = null;
        pendingRefreshCandidate = null;
        isFavorite      = false;

        if (ApiConfig.DEBUG_LOGGING) {
            Log.d(TAG, "RecipeManager cleaned up");
        }
    }

    // ========== PRIVATE HELPERS ==========

    /**
     * Load the favorite status for the currently-loaded recipe from Room.
     *
     * Called after a successful load to initialise the toolbar star icon.
     * For external recipes not yet persisted, the favorite flag on the
     * in-memory Recipe object is authoritative (defaults to false).
     */
    private void loadFavoriteStatus() {
        if (currentRecipe == null) return;

        // For in-memory external recipes not yet persisted, the flag on the
        // Recipe object is already correct - use it directly without a DB call.
        boolean flag = currentRecipe.isFavorite();
        isFavorite = flag;
        notifyFavoriteStatusChanged(flag);
    }

    // ========== LISTENER NOTIFICATION HELPERS ==========

    private void notifyRecipeLoaded(@NonNull Recipe recipe) {
        if (listener != null) {
            try {
                listener.onRecipeLoaded(recipe);
            } catch (Exception e) {
                Log.e(TAG, "Error in onRecipeLoaded callback", e);
            }
        }
    }

    private void notifyError(@NonNull Error error) {
        if (listener != null) {
            try {
                listener.onRecipeError(error);
            } catch (Exception e) {
                Log.e(TAG, "Error in onRecipeError callback", e);
            }
        }
    }

    private void notifyLoading() {
        if (listener != null) {
            try {
                listener.onRecipeLoading();
            } catch (Exception e) {
                Log.e(TAG, "Error in onRecipeLoading callback", e);
            }
        }
    }

    private void notifyFavoriteStatusChanged(boolean status) {
        if (listener != null) {
            try {
                listener.onFavoriteStatusChanged(status);
            } catch (Exception e) {
                Log.e(TAG, "Error in onFavoriteStatusChanged callback", e);
            }
        }
    }

    private void notifyFavoriteToggled(boolean newStatus, @NonNull String message) {
        if (listener != null) {
            try {
                listener.onFavoriteToggled(newStatus, message);
            } catch (Exception e) {
                Log.e(TAG, "Error in onFavoriteToggled callback", e);
            }
        }
    }

    private void notifyFavoriteError(@NonNull String message) {
        if (listener != null) {
            try {
                listener.onFavoriteError(message);
            } catch (Exception e) {
                Log.e(TAG, "Error in onFavoriteError callback", e);
            }
        }
    }
}