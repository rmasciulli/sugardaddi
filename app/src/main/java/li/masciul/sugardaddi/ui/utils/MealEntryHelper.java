package li.masciul.sugardaddi.ui.utils;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import li.masciul.sugardaddi.core.models.FoodPortion;
import li.masciul.sugardaddi.core.models.Meal;
import li.masciul.sugardaddi.core.models.PortionQuantity;
import li.masciul.sugardaddi.data.repository.MealRepository;

/**
 * MealEntryHelper - "is this item in the meal, and with what amount?" and
 * "save this amount in the meal", shared by the product and recipe screens.
 *
 * ADD OR UPDATE: a meal holds each item once (Meal.addPortion()). Saving an
 * item already in the meal updates its logged amount in place (same row, same
 * position); saving one that isn't adds it. The meal is read again at save
 * time, so the decision is right even if the meal changed since the screen
 * opened (the item removed elsewhere, or added from another screen).
 *
 * Callbacks always run on the main thread, and are dropped when the activity
 * is already finishing or destroyed.
 */
public final class MealEntryHelper {

    /** Result of lookup(). */
    public interface LookupCallback {
        /**
         * @param inMeal true when the item is already in the meal
         * @param logged its logged amount; null when not in the meal, when the
         *               entry has no snapshot, or when the meal could not be read
         */
        void onResult(boolean inMeal, @Nullable PortionQuantity logged);
    }

    /** Result of save(). */
    public interface SaveCallback {
        /** @param updated true when an existing entry was updated, false when added */
        void onSaved(boolean updated);

        void onError(@NonNull String message);
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private MealEntryHelper() {
        // Static helpers only
    }

    /**
     * Find the item in the meal. A meal that cannot be read counts as "not in
     * the meal": the screen then offers Add, and save() reports the real error.
     */
    public static void lookup(@NonNull Activity activity, @NonNull String mealId,
                              @NonNull String itemType, @NonNull String itemId,
                              @NonNull LookupCallback callback) {
        repository(activity).getMeal(mealId, new MealRepository.MealCallback() {
            @Override
            public void onSuccess(Meal meal) {
                FoodPortion existing = meal != null ? meal.findPortion(itemType, itemId) : null;
                PortionQuantity logged = existing != null ? existing.getQuantity() : null;
                post(activity, () -> callback.onResult(existing != null, logged));
            }

            @Override
            public void onError(String error) {
                post(activity, () -> callback.onResult(false, null));
            }
        });
    }

    /**
     * Add the portion to the meal, or update the amount of the entry already
     * there. The portion must carry its PortionQuantity snapshot (every
     * FoodPortion built from a product or recipe does).
     */
    public static void save(@NonNull Activity activity, @NonNull String mealId,
                            @NonNull FoodPortion portion, @NonNull SaveCallback callback) {
        MealRepository repository = repository(activity);
        repository.getMeal(mealId, new MealRepository.MealCallback() {
            @Override
            public void onSuccess(Meal meal) {
                if (meal == null) {
                    post(activity, () -> callback.onError("Meal not found"));
                    return;
                }
                boolean updated;
                if (meal.findPortion(portion.getItemType(), portion.getItemId()) != null) {
                    PortionQuantity quantity = portion.getQuantity();
                    if (quantity == null) {
                        post(activity, () -> callback.onError("No quantity to save"));
                        return;
                    }
                    meal.updatePortionQuantity(portion.getItemType(), portion.getItemId(), quantity);
                    updated = true;
                } else {
                    meal.addPortion(portion);
                    updated = false;
                }

                repository.updateMeal(meal, new MealRepository.MealCallback() {
                    @Override
                    public void onSuccess(Meal savedMeal) {
                        post(activity, () -> callback.onSaved(updated));
                    }

                    @Override
                    public void onError(String error) {
                        post(activity, () -> callback.onError(error != null ? error : ""));
                    }
                });
            }

            @Override
            public void onError(String error) {
                post(activity, () -> callback.onError(error != null ? error : ""));
            }
        });
    }

    // ========== INTERNALS ==========

    @NonNull
    private static MealRepository repository(@NonNull Context context) {
        return new MealRepository(context);
    }

    /** Run on the main thread, unless the screen is already going away. */
    private static void post(@NonNull Activity activity, @NonNull Runnable action) {
        MAIN.post(() -> {
            if (activity.isFinishing() || activity.isDestroyed()) return;
            action.run();
        });
    }
}
