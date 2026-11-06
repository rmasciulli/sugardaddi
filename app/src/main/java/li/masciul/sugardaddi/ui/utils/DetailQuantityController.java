package li.masciul.sugardaddi.ui.utils;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import li.masciul.sugardaddi.R;
import li.masciul.sugardaddi.core.enums.NutritionBasis;
import li.masciul.sugardaddi.core.enums.Unit;
import li.masciul.sugardaddi.core.interfaces.Searchable;
import li.masciul.sugardaddi.core.models.FoodPortion;
import li.masciul.sugardaddi.core.models.FoodProduct;
import li.masciul.sugardaddi.core.models.Nutrition;
import li.masciul.sugardaddi.core.models.PortionQuantity;
import li.masciul.sugardaddi.core.models.Recipe;
import li.masciul.sugardaddi.core.models.ServingSize;
import li.masciul.sugardaddi.data.database.AppDatabase;
import li.masciul.sugardaddi.data.database.entities.UserItemDataEntity;
import li.masciul.sugardaddi.ui.activities.MealDetailsActivity;
import li.masciul.sugardaddi.ui.components.NutritionLabelManager;
import li.masciul.sugardaddi.ui.components.QuantityInputView;
import li.masciul.sugardaddi.ui.components.TypicalServingDialog;

/**
 * DetailQuantityController - everything a detail screen does with its quantity
 * input, shared by ProductDetailsActivity and RecipeDetailsActivity (change 6).
 *
 * The screen owns the input; renderers only display. The controller:
 * - sets the input up for the item: one unit (the nutrition basis, else the
 *   item's serving unit, else the screen's default), the typical serving (the
 *   user's preferred one, else the source's), and the starting value;
 * - pushes every new amount to the screen (AmountListener), which forwards it
 *   to the renderer's nutrition table (DetailRenderer.onAmountChanged());
 * - keeps what the user entered across refresh re-renders and rotation;
 * - opens the typical serving editor and stores the answer (user_item_data);
 * - in meal context (RETURN_TO_MEAL): finds the item in the meal, offers Add or
 *   Update, starts from the logged amount (rule A), and saves.
 *
 * STARTING VALUE, in this order:
 *   1. what the user was entering (refresh, rotation);
 *   2. the logged entry, when updating one (rule A: its own serving size);
 *   3. 1 serving of the typical serving;
 *   4. an item with nutrition but no serving size at all: the "Portion size"
 *      page with the fallback amount (20 g / ml) typed in, so the table has a
 *      real amount and no invented serving is shown (decision, change 6).
 *
 * Threading: everything runs on the main thread except the database writes,
 * which go to a single background executor.
 */
public final class DetailQuantityController {

    /** Told every time the amount to display changes (in the item's basis unit). */
    public interface AmountListener {
        void onAmountChanged(double amount);
    }

    private static final String STATE_QUANTITY_INPUT = "quantity_input";

    private final Activity activity;
    /** UserItemDataEntity.TYPE_PRODUCT or TYPE_RECIPE. */
    private final String itemType;
    @Nullable private final String returnToMealId;
    @Nullable private final Button mealButton;
    private final AmountListener amountListener;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // ========== CURRENT ITEM AND INPUT ==========

    @Nullable private Searchable item;
    @Nullable private QuantityInputView input;
    @NonNull private Unit unit = Unit.G;
    /** The source's own serving size in unit (shown for reference when editing). */
    @Nullable private Double sourceServing;
    /** The serving size the input was set up with (user's, else source's). */
    @Nullable private Double effectiveServing;
    /** Last amount pushed to the screen, for reapplyAmount(). */
    @Nullable private Double lastAmount;

    /** Input saved before re-creation (rotation), applied at the next bind(). */
    @Nullable private Bundle pendingState;
    /** True once the input shows the user's figures (logged entry or carried state). */
    private boolean prefilled;

    // ========== MEAL ENTRY ==========

    private boolean mealLookupDone;
    private boolean inMeal;
    @Nullable private PortionQuantity loggedQuantity;
    @Nullable private String lookupItemId;
    private boolean saving;

    /**
     * @param itemType           UserItemDataEntity.TYPE_PRODUCT or TYPE_RECIPE
     * @param returnToMealId     the meal this screen adds to, or null outside a meal
     * @param mealButton         the Add / Update button (meal context only), or null
     * @param savedInstanceState the activity's saved state, to restore the input
     */
    public DetailQuantityController(@NonNull Activity activity, @NonNull String itemType,
                                    @Nullable String returnToMealId, @Nullable Button mealButton,
                                    @Nullable Bundle savedInstanceState,
                                    @NonNull AmountListener amountListener) {
        this.activity = activity;
        this.itemType = itemType;
        this.returnToMealId = returnToMealId;
        this.mealButton = mealButton;
        this.amountListener = amountListener;
        this.pendingState = savedInstanceState != null
                ? savedInstanceState.getBundle(STATE_QUANTITY_INPUT)
                : null;
        if (mealButton != null) {
            mealButton.setOnClickListener(v -> save());
            // Disabled until the item and the meal are both loaded, so the label
            // never flips from "Add" to "Update" under the finger.
            updateButton();
        }
    }

    // ========== PUBLIC API ==========

    /**
     * Set the input of a freshly rendered item up. Call on every render (a
     * refresh inflates a new layout, and with it a new input), after the
     * renderer has populated the view.
     *
     * @param rendererView        the renderer's view, holding R.id.quantityInput
     * @param unitWithoutNutrition the unit to use when the item has neither
     *                             nutrition nor a serving unit (ml for drinks)
     */
    public void bind(@NonNull View rendererView, @NonNull Searchable item,
                     @NonNull Unit unitWithoutNutrition) {
        // Read before the input is replaced: the previous one is still alive.
        Bundle carried = input != null && sameItem(item) ? input.saveState() : pendingState;
        pendingState = null;

        this.item = item;
        this.input = rendererView.findViewById(R.id.quantityInput);
        if (input == null) {
            startMealLookup();
            updateButton();
            return;
        }

        unit = unitFor(item, unitWithoutNutrition);
        sourceServing = sourceServingIn(item, unit);
        Double userServing = userServingIn(item, unit);
        effectiveServing = userServing != null ? userServing : sourceServing;

        input.setOnQuantityChangedListener(quantity -> {
            pushAmount(quantity);
            updateButton();
        });
        input.setOnTypicalServingClickListener(this::editTypicalServing);

        if (carried != null) {
            // What the user was working on wins (refresh, rotation).
            input.setup(effectiveServing, unit);
            input.restoreState(carried);
            prefilled = true;
        } else if (loggedQuantity != null) {
            // Updating an entry and the lookup already answered (rule A).
            input.setup(effectiveServing, unit, loggedQuantity);
            prefilled = true;
        } else {
            setupDefault();
            prefilled = false;
        }

        startMealLookup();
        updateButton();
    }

    /** Push the current amount again (the renderer repopulated its table). */
    public void reapplyAmount() {
        if (lastAmount != null) amountListener.onAmountChanged(lastAmount);
    }

    /** What Add / Update would log now, or null when the input holds nothing valid. */
    @Nullable
    public PortionQuantity getQuantity() {
        return input != null ? input.getQuantity() : null;
    }

    /** Keep the input across activity re-creation: call from onSaveInstanceState(). */
    public void onSaveInstanceState(@NonNull Bundle outState) {
        Bundle state = input != null ? input.saveState() : pendingState;
        if (state != null) outState.putBundle(STATE_QUANTITY_INPUT, state);
    }

    public void shutdown() {
        executor.shutdown();
    }

    // ========== SETUP ==========

    /**
     * Starting value without a logged entry: 1 serving, or, for an item with
     * nutrition and no serving size at all, the fallback amount typed on the
     * "Portion size" page (rule 4 in the class comment).
     */
    private void setupDefault() {
        if (input == null || item == null) return;
        if (effectiveServing == null && hasNutrition(item)) {
            input.setup(null, unit,
                    PortionQuantity.ofQuantity(NutritionLabelManager.FALLBACK_AMOUNT, unit, null));
        } else {
            input.setup(effectiveServing, unit);
        }
    }

    private void pushAmount(@Nullable PortionQuantity quantity) {
        if (quantity == null) return;   // invalid typed value: the table keeps the last one
        Double total = quantity.getTotalQuantity();
        if (total == null || total <= 0) return;   // servings without a known size
        lastAmount = total;
        amountListener.onAmountChanged(total);
    }

    // ========== TYPICAL SERVING (change 5b, everywhere since change 6) ==========

    /**
     * Open the editor. Saving stores the user's value and applies it at once:
     * what the user entered is kept and re-read with the new size, including an
     * entry being updated (decision 5b, question 4).
     */
    private void editTypicalServing() {
        if (input == null || item == null) return;
        Searchable current = item;
        Unit editUnit = unit;
        boolean hasUserValue = userServingIn(current, editUnit) != null;

        TypicalServingDialog.show(activity, editUnit, input.getTypicalServing(), sourceServing,
                hasUserValue, new TypicalServingDialog.Listener() {
                    @Override public void onSave(double quantity) {
                        saveTypicalServing(current, quantity, editUnit);
                    }
                    @Override public void onReset() {
                        saveTypicalServing(current, null, editUnit);
                    }
                });
    }

    /** Store (or clear, with null) the user's typical serving, then apply it. */
    private void saveTypicalServing(@NonNull Searchable target, @Nullable Double quantity,
                                    @NonNull Unit servingUnit) {
        String itemId = target.getSearchableId();
        if (itemId == null) return;
        executor.execute(() -> {
            AppDatabase.getInstance(activity).userItemDataDao().setServing(
                    itemType, itemId, quantity, UserItemDataEntity.unitToColumn(servingUnit));
            activity.runOnUiThread(() -> {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                setUserServing(target, quantity, servingUnit);
                if (target != item || input == null) return;   // the screen moved on
                effectiveServing = quantity != null ? quantity : sourceServing;
                input.setTypicalServing(effectiveServing);
            });
        });
    }

    // ========== MEAL ENTRY: LOOKUP, BUTTON, SAVE ==========

    /**
     * Check once per item whether it is already in the meal. The answer sets
     * the button (Add or Update) and fills the input with the logged amount.
     */
    private void startMealLookup() {
        if (returnToMealId == null || item == null) return;
        String itemId = item.getSearchableId();
        if (itemId == null || itemId.equals(lookupItemId)) return;
        lookupItemId = itemId;
        mealLookupDone = false;
        updateButton();

        MealEntryHelper.lookup(activity, returnToMealId, itemType, itemId, (found, logged) -> {
            if (!itemId.equals(lookupItemId)) return;   // a newer lookup took over
            mealLookupDone = true;
            inMeal = found;
            loggedQuantity = found ? logged : null;
            // The input was set up before the answer: show the logged amount now,
            // unless it already shows the user's own figures.
            if (input != null && !prefilled && loggedQuantity != null) {
                input.setup(effectiveServing, unit, loggedQuantity);
                prefilled = true;
            }
            updateButton();
        });
    }

    /**
     * Label and state of the meal button: "Add to Meal" or "Update in meal",
     * "Adding..." / "Updating..." while saving; enabled once the item and the
     * lookup are both done and the input holds a valid quantity.
     */
    private void updateButton() {
        if (mealButton == null) return;
        if (saving) {
            mealButton.setText(inMeal ? R.string.updating_meal : R.string.adding_to_meal);
            mealButton.setEnabled(false);
            return;
        }
        mealButton.setText(inMeal ? R.string.update_in_meal : R.string.add_to_meal);
        boolean ready = mealLookupDone && item != null;
        mealButton.setEnabled(ready && (input == null || input.getQuantity() != null));
    }

    /**
     * Save the item in the meal: add it, or update the amount of its entry when
     * it is already there (re-checked at save time by MealEntryHelper), then
     * return to the meal.
     */
    private void save() {
        if (returnToMealId == null || item == null) return;
        FoodPortion portion = newPortion(item);
        if (portion == null) return;
        if (input != null) {
            PortionQuantity quantity = input.getQuantity();
            if (quantity == null) return;   // the button is disabled in that state anyway
            portion.setQuantity(quantity);
        }
        portion.setParentType("MEAL");
        portion.setParentId(returnToMealId);

        saving = true;
        updateButton();

        String mealId = returnToMealId;
        MealEntryHelper.save(activity, mealId, portion, new MealEntryHelper.SaveCallback() {
            @Override
            public void onSaved(boolean updated) {
                Toast.makeText(activity, updated ? R.string.meal_updated : R.string.added_to_meal,
                        Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(activity, MealDetailsActivity.class);
                intent.putExtra(MealDetailsActivity.EXTRA_MEAL_ID, mealId);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                activity.startActivity(intent);
                activity.finish();
            }

            @Override
            public void onError(@NonNull String message) {
                Toast.makeText(activity, "Error saving: " + message, Toast.LENGTH_LONG).show();
                saving = false;
                updateButton();
            }
        });
    }

    // ========== ITEM HELPERS ==========

    private boolean sameItem(@NonNull Searchable other) {
        return item != null && item.getSearchableId() != null
                && item.getSearchableId().equals(other.getSearchableId());
    }

    /** A portion of 1 serving, replaced by the input's quantity in save(). */
    @Nullable
    private static FoodPortion newPortion(@NonNull Searchable item) {
        if (item instanceof FoodProduct) return new FoodPortion((FoodProduct) item, 1, Unit.SERVING);
        if (item instanceof Recipe) return new FoodPortion((Recipe) item, 1, Unit.SERVING);
        return null;
    }

    @Nullable
    private static Nutrition nutritionOf(@NonNull Searchable item) {
        if (item instanceof FoodProduct) return ((FoodProduct) item).getNutrition();
        if (item instanceof Recipe) return ((Recipe) item).getNutrition();
        return null;
    }

    private static boolean hasNutrition(@NonNull Searchable item) {
        Nutrition nutrition = nutritionOf(item);
        return nutrition != null && nutrition.hasData();
    }

    @Nullable
    private static ServingSize servingOf(@NonNull Searchable item) {
        if (item instanceof FoodProduct) return ((FoodProduct) item).getServingSize();
        if (item instanceof Recipe) return ((Recipe) item).getServingSize();
        return null;
    }

    /**
     * The input's one unit: the nutrition basis (g or ml); for an item without
     * nutrition, the kind of its serving unit (mass or volume); else the
     * screen's default.
     */
    @NonNull
    private static Unit unitFor(@NonNull Searchable item, @NonNull Unit fallback) {
        Nutrition nutrition = nutritionOf(item);
        if (nutrition != null && nutrition.hasData()) {
            return nutrition.getBasis() == NutritionBasis.PER_100ML ? Unit.ML : Unit.G;
        }
        ServingSize serving = servingOf(item);
        if (serving != null && serving.getUnit() != null) {
            Unit.UnitType type = serving.getUnit().getType();
            if (type == Unit.UnitType.VOLUME) return Unit.ML;
            if (type == Unit.UnitType.WEIGHT) return Unit.G;
        }
        return fallback == Unit.ML ? Unit.ML : Unit.G;
    }

    /**
     * The source's serving size read in the given unit, never converted through
     * a density: ml for a volume input, g otherwise. Same reading as
     * NutritionLabelManager.defaultAmountFor().
     */
    @Nullable
    private static Double sourceServingIn(@NonNull Searchable item, @NonNull Unit unit) {
        ServingSize serving = servingOf(item);
        if (serving == null) return null;
        Double size = unit == Unit.ML ? serving.getAsMilliliters() : serving.getAsGrams();
        return (size != null && size > 0) ? size : null;
    }

    /** The user's preferred serving, when it is in the given unit. */
    @Nullable
    private static Double userServingIn(@NonNull Searchable item, @NonNull Unit unit) {
        Double quantity = null;
        Unit userUnit = null;
        if (item instanceof FoodProduct) {
            quantity = ((FoodProduct) item).getUserServingQuantity();
            userUnit = ((FoodProduct) item).getUserServingUnit();
        } else if (item instanceof Recipe) {
            quantity = ((Recipe) item).getUserServingQuantity();
            userUnit = ((Recipe) item).getUserServingUnit();
        }
        if (quantity == null || quantity <= 0) return null;
        return (userUnit == Unit.ML) == (unit == Unit.ML) ? quantity : null;
    }

    private static void setUserServing(@NonNull Searchable item, @Nullable Double quantity,
                                       @NonNull Unit unit) {
        if (item instanceof FoodProduct) ((FoodProduct) item).setUserServing(quantity, unit);
        else if (item instanceof Recipe) ((Recipe) item).setUserServing(quantity, unit);
    }
}
