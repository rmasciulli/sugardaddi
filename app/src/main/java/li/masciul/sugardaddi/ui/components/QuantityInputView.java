package li.masciul.sugardaddi.ui.components;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.method.DigitsKeyListener;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;

import li.masciul.sugardaddi.R;
import li.masciul.sugardaddi.core.enums.Unit;
import li.masciul.sugardaddi.core.models.PortionQuantity;

/**
 * QuantityInputView - the quantity input of a detail screen.
 *
 * Three pages, swiped with the finger (SwipePagerView) and shown by tappable
 * dots (PagerDotsView):
 *   1. "Number of servings" - typical serving size (editable, the screen opens
 *      the editor) and a number of servings with - / +.
 *   2. "Portion size" - typical serving size and a quantity typed on the
 *      numpad, with the input's unit shown right after the number.
 *   3. "Measurements & quantity" - the preferred unit, g | ml. The view only
 *      reports the tap (OnUnitSelectedListener): the screen knows the item's
 *      density, works the conversion out and calls switchUnit(). Disabled,
 *      with a line saying why, when the item has only one unit.
 *
 * This view only displays and forwards: every rule (steps, conversions, which
 * value gets logged, validity) lives in QuantityInputState, and every number is
 * read and written by QuantityText. Both are plain Java and unit-tested.
 *
 * It is not a card: it draws the same box as the nutrition table below it
 * (bg_nutrition_block: white, square corners, thin grey border) and sits in a
 * detail card's "Nutrition Facts" section. The box stays still; the pages
 * slide inside it, kept 1dp inside the border so they never cover it.
 *
 * STATE ACROSS RE-CREATION: the screen rebuilds this view after an asynchronous
 * load, too late for Android's automatic view-state restore. The owner calls
 * saveState() in onSaveInstanceState() and restoreState() after setup() instead.
 */
public class QuantityInputView extends LinearLayout {

    /** Told when the user picks the other unit on the "Measurements & quantity" page. */
    public interface OnUnitSelectedListener {
        void onUnitSelected(@NonNull Unit unit);
    }

    /** Told every time the value that would be logged changes. */
    public interface OnQuantityChangedListener {
        /** @param quantity what Add would log now, or null if the input is invalid */
        void onQuantityChanged(@Nullable PortionQuantity quantity);
    }

    private static final int PAGE_SERVINGS = 0;
    private static final int PAGE_PORTION = 1;

    // saveState() / restoreState() keys
    private static final String KEY_INPUT_MODE = "inputMode";
    private static final String KEY_SERVING_COUNT = "servingCount";
    private static final String KEY_CUSTOM_QUANTITY = "customQuantity";
    private static final String KEY_PAGE = "page";
    private static final String KEY_SERVING_QUANTITY = "servingQuantity";
    private static final String KEY_UNIT = "unit";

    // ========== VIEWS ==========

    private SwipePagerView pager;

    private TextView servingsTypicalValue;
    private TextView servingsValue;
    private MaterialButton servingsMinus;

    private TextView customTypicalValue;
    private EditText customQuantityInput;
    private TextView customUnitLabel;

    private MaterialButtonToggleGroup unitToggle;
    private MaterialButton unitGButton;
    private MaterialButton unitMlButton;
    private TextView unitOnlyNote;

    // ========== STATE ==========

    /** The rules and values; replaced by setup() for each item. */
    @NonNull private QuantityInputState state = new QuantityInputState(null, Unit.G);

    /** Set while the code fills the field itself, so the watcher doesn't treat it as typing. */
    private boolean updatingField;

    /** True when the item can be shown in both g and ml (it has a density). */
    private boolean unitSwitchable;
    /** Set while the code checks a segment itself, so the toggle doesn't report it. */
    private boolean updatingToggle;

    @Nullable private OnQuantityChangedListener listener;
    @Nullable private OnUnitSelectedListener unitListener;
    /** Told when the user taps "Typical serving size" (the screen opens the editor). */
    @Nullable private Runnable typicalServingClickListener;

    // ========== CONSTRUCTORS ==========

    public QuantityInputView(@NonNull Context context) {
        super(context);
        init(context);
    }

    public QuantityInputView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public QuantityInputView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(@NonNull Context context) {
        setOrientation(VERTICAL);
        // Same box as the nutrition table; spacing comes from the pages and the
        // dots. The 1dp padding keeps the sliding pages inside the 1dp border
        // (the pager clips its pages to its own bounds).
        setBackgroundResource(R.drawable.bg_nutrition_block);
        int border = Math.round(getResources().getDisplayMetrics().density);
        setPadding(border, border, border, border);

        LayoutInflater.from(context).inflate(R.layout.view_quantity_input, this, true);

        pager = findViewById(R.id.quantityPager);
        PagerDotsView dots = findViewById(R.id.quantityDots);

        servingsTypicalValue = findViewById(R.id.servingsTypicalValue);
        servingsValue = findViewById(R.id.servingsValue);
        servingsMinus = findViewById(R.id.servingsMinus);
        MaterialButton servingsPlus = findViewById(R.id.servingsPlus);

        customTypicalValue = findViewById(R.id.customTypicalValue);
        customQuantityInput = findViewById(R.id.customQuantityInput);
        customUnitLabel = findViewById(R.id.customUnitLabel);

        unitToggle = findViewById(R.id.unitToggle);
        unitGButton = findViewById(R.id.unitG);
        unitMlButton = findViewById(R.id.unitMl);
        unitOnlyNote = findViewById(R.id.unitOnlyNote);
        unitGButton.setText(Unit.G.getAbbreviation());
        unitMlButton.setText(Unit.ML.getAbbreviation());
        unitToggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked || updatingToggle) return;
            Unit chosen = checkedId == R.id.unitMl ? Unit.ML : Unit.G;
            if (chosen == state.getUnit()) return;
            if (unitListener != null) {
                unitListener.onUnitSelected(chosen);
            } else {
                refreshUnitPage();   // nobody to convert: show the current unit again
            }
        });

        // Accept "." and "," whatever the keyboard sends (French keyboards show ","),
        // while keeping the numeric keypad. setKeyListener() resets the input type,
        // so the raw type is set again afterwards.
        customQuantityInput.setKeyListener(DigitsKeyListener.getInstance("0123456789.,"));
        customQuantityInput.setRawInputType(
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);

        servingsMinus.setOnClickListener(v -> {
            state.decreaseServings();
            onServingsEdited();
        });
        servingsPlus.setOnClickListener(v -> {
            state.increaseServings();
            onServingsEdited();
        });
        // The number has no box and can be short ("5"): the whole row is the target.
        findViewById(R.id.customQuantityRow).setOnClickListener(v -> focusCustomField());
        findViewById(R.id.customQuantityEdit).setOnClickListener(v -> focusCustomField());
        // Typical serving size, on both pages: the screen decides what editing means.
        OnClickListener typicalClick = v -> {
            if (typicalServingClickListener != null) typicalServingClickListener.run();
        };
        // The pencil buttons are the visible control; the rows answer too, so
        // tapping the label or the value works as well.
        findViewById(R.id.servingsTypicalEdit).setOnClickListener(typicalClick);
        findViewById(R.id.customTypicalEdit).setOnClickListener(typicalClick);
        findViewById(R.id.servingsTypicalRow).setOnClickListener(typicalClick);
        findViewById(R.id.customTypicalRow).setOnClickListener(typicalClick);

        customQuantityInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (updatingField) return;
                state.setCustomQuantity(QuantityText.parse(s.toString()));
                refreshServingsPage();
                notifyChanged();
            }
        });

        pager.addOnPageChangeListener(this::onPageSelected);
        dots.attachTo(pager);

        refreshAll();
    }

    // ========== PUBLIC API ==========

    /**
     * Configure the input for one item. Resets it to "1 serving" on the first page.
     *
     * @param servingQuantity size of one serving in {@code unit}, or null if the
     *                        item has none
     * @param unit            G or ML (anything else is read as G): the unit of the
     *                        serving size and of the typed quantity
     */
    public void setup(@Nullable Double servingQuantity, @NonNull Unit unit) {
        setup(servingQuantity, unit, null);
    }

    /**
     * Configure the input for one item, starting from an entry already logged in
     * a meal when there is one (Update): its figures, with its logged serving
     * size (see QuantityInputState.from()), on the page it was logged with.
     *
     * @param logged the entry's snapshot, or null for a fresh "1 serving"
     */
    public void setup(@Nullable Double servingQuantity, @NonNull Unit unit,
                      @Nullable PortionQuantity logged) {
        state = logged != null
                ? QuantityInputState.from(logged, servingQuantity, unit)
                : new QuantityInputState(servingQuantity, unit);
        // Field text first: arriving on the portion size page focuses it only when empty.
        refreshAll();
        boolean typed = logged != null && logged.getInputMode() == PortionQuantity.InputMode.QUANTITY;
        pager.setCurrentPage(typed ? PAGE_PORTION : PAGE_SERVINGS, false);
        notifyChanged();
    }

    /**
     * What Add would log now, frozen as a PortionQuantity - or null while the
     * typed quantity is the value being edited and holds no positive number.
     */
    @Nullable
    public PortionQuantity getQuantity() {
        return state.toPortionQuantity();
    }

    public void setOnQuantityChangedListener(@Nullable OnQuantityChangedListener listener) {
        this.listener = listener;
    }

    public void setOnUnitSelectedListener(@Nullable OnUnitSelectedListener listener) {
        this.unitListener = listener;
    }

    /**
     * Whether the "Measurements & quantity" toggle can be used: true only when
     * the item can be shown in both g and ml. Call after setup().
     */
    public void setUnitSwitchable(boolean switchable) {
        this.unitSwitchable = switchable;
        refreshUnitPage();
    }

    /** G or ML: the unit the input works in now. */
    @NonNull
    public Unit getUnit() {
        return state.getUnit();
    }

    /**
     * Show the input in the other unit, keeping the same physical amount
     * (QuantityInputState.inUnit()): servings stay, a typed quantity is
     * converted.
     *
     * @param unit            G or ML
     * @param servingQuantity the typical serving in that unit, or null
     * @param factor          amount in unit for 1 of the current unit (> 0)
     */
    public void switchUnit(@NonNull Unit unit, @Nullable Double servingQuantity, double factor) {
        state = state.inUnit(unit, servingQuantity, factor);
        refreshAll();
        notifyChanged();
    }

    /**
     * Called when the user taps "Typical serving size". Without a listener the
     * rows still look editable, so a screen showing the input must set one.
     */
    public void setOnTypicalServingClickListener(@Nullable Runnable listener) {
        this.typicalServingClickListener = listener;
    }

    /** The serving size the input currently works with (null when unknown). */
    @Nullable
    public Double getTypicalServing() {
        return state.getServingQuantity();
    }

    /**
     * The typical serving size changed (the user edited it). What the user
     * entered is kept and re-read with the new size (decision 5b, question 4):
     * a number of servings stays and its amount follows (2 x 40 g = 80 g); a
     * typed quantity stays and its number of servings follows.
     *
     * @param servingQuantity the new size in the input's unit, or null for none
     */
    public void setTypicalServing(@Nullable Double servingQuantity) {
        PortionQuantity.InputMode mode = state.getInputMode();
        double count = state.getServingCount();
        Double custom = state.getCustomQuantity();
        state = new QuantityInputState(servingQuantity, state.getUnit());
        state.restoreEdits(mode, count, custom);
        refreshAll();
        notifyChanged();
    }

    /**
     * What the user entered, the serving size and unit it was entered against,
     * and the page shown, for restoreState().
     */
    @NonNull
    public Bundle saveState() {
        Bundle bundle = new Bundle();
        Double serving = state.getServingQuantity();
        if (serving != null) bundle.putDouble(KEY_SERVING_QUANTITY, serving);
        bundle.putString(KEY_UNIT, state.getUnit().name());
        bundle.putString(KEY_INPUT_MODE, state.getInputMode().name());
        bundle.putDouble(KEY_SERVING_COUNT, state.getServingCount());
        Double custom = state.getCustomQuantity();
        if (custom != null) bundle.putDouble(KEY_CUSTOM_QUANTITY, custom);
        bundle.putInt(KEY_PAGE, pager.getCurrentPage());
        return bundle;
    }

    /**
     * Put back what saveState() captured. Call after setup().
     *
     * The serving size and unit saved with the input win over the ones setup()
     * received: the user keeps seeing the figures they were working with (the
     * logged serving size of an entry being updated, rule A of 4b), even if a
     * refresh brought a new serving size in the meantime.
     */
    public void restoreState(@Nullable Bundle bundle) {
        if (bundle == null) return;
        if (bundle.containsKey(KEY_UNIT)) {
            Unit unit = Unit.ML.name().equals(bundle.getString(KEY_UNIT)) ? Unit.ML : Unit.G;
            Double serving = bundle.containsKey(KEY_SERVING_QUANTITY)
                    ? bundle.getDouble(KEY_SERVING_QUANTITY)
                    : null;
            state = new QuantityInputState(serving, unit);
        }
        PortionQuantity.InputMode mode = PortionQuantity.InputMode.QUANTITY.name()
                .equals(bundle.getString(KEY_INPUT_MODE))
                ? PortionQuantity.InputMode.QUANTITY
                : PortionQuantity.InputMode.SERVINGS;
        Double custom = bundle.containsKey(KEY_CUSTOM_QUANTITY)
                ? bundle.getDouble(KEY_CUSTOM_QUANTITY)
                : null;
        state.restoreEdits(mode, bundle.getDouble(KEY_SERVING_COUNT, 1.0), custom);
        pager.setCurrentPage(bundle.getInt(KEY_PAGE, PAGE_SERVINGS), false);
        refreshAll();
        notifyChanged();
    }

    // ========== EDITS ==========

    private void onServingsEdited() {
        // Keep the portion size page showing the same amount (empty when unknown).
        Double custom = state.getCustomQuantity();
        setFieldText(custom != null ? QuantityText.format(custom) : "");
        refreshServingsPage();
        notifyChanged();
    }

    private void onPageSelected(int page) {
        if (page == PAGE_PORTION) {
            // Arriving on an empty portion size page: ready to type.
            if (customQuantityInput.getText().length() == 0) focusCustomField();
        } else {
            // Leaving it: the keyboard would only hide the rest of the screen.
            hideKeyboard();
        }
    }

    private void setFieldText(@NonNull String text) {
        updatingField = true;
        try {
            customQuantityInput.setText(text);
        } finally {
            updatingField = false;
        }
    }

    private void notifyChanged() {
        if (listener != null) listener.onQuantityChanged(getQuantity());
    }

    // ========== DISPLAY ==========

    private void refreshAll() {
        Double serving = state.getServingQuantity();
        String typical = serving != null
                ? QuantityText.formatAmount(serving, state.getUnit())
                : getContext().getString(R.string.quantity_not_set);
        servingsTypicalValue.setText(typical);
        customTypicalValue.setText(typical);
        customUnitLabel.setText(state.getUnit().getAbbreviation());
        refreshUnitPage();

        Double custom = state.getCustomQuantity();
        String fieldText = custom != null ? QuantityText.format(custom) : "";
        if (!fieldText.contentEquals(customQuantityInput.getText())) {
            setFieldText(fieldText);
        }
        refreshServingsPage();
    }

    /**
     * Page 3: the segment of the current unit checked. When the item has only
     * one unit, the OTHER segment is disabled and the reason shown underneath;
     * the current one stays enabled (only made inert), so it keeps the checked
     * color and the user still sees which unit is used. Disabling it too would
     * drop that color: Material draws a disabled segment unfilled.
     *
     * Both segments are enabled BEFORE checking: MaterialButton.setChecked()
     * ignores a disabled button, so checking "ml" while it was still disabled
     * from the previous refresh (a fresh input starts in g) left it unchecked.
     */
    private void refreshUnitPage() {
        unitGButton.setEnabled(true);
        unitMlButton.setEnabled(true);
        updatingToggle = true;
        try {
            unitToggle.check(state.getUnit() == Unit.ML ? R.id.unitMl : R.id.unitG);
        } finally {
            updatingToggle = false;
        }
        MaterialButton current = state.getUnit() == Unit.ML ? unitMlButton : unitGButton;
        MaterialButton other = current == unitGButton ? unitMlButton : unitGButton;
        current.setEnabled(true);
        current.setClickable(unitSwitchable);
        current.setFocusable(unitSwitchable);
        other.setEnabled(unitSwitchable);
        other.setClickable(true);
        other.setFocusable(true);
        unitOnlyNote.setText(state.getUnit() == Unit.ML
                ? R.string.quantity_unit_only_ml
                : R.string.quantity_unit_only_g);
        unitOnlyNote.setVisibility(unitSwitchable ? View.GONE : View.VISIBLE);
    }

    private void refreshServingsPage() {
        String count = QuantityText.format(state.getServingCount());
        Double serving = state.getServingQuantity();
        if (serving != null) {
            count += " (" + QuantityText.formatAmount(
                    state.getServingCount() * serving, state.getUnit()) + ")";
        }
        servingsValue.setText(count);
        servingsMinus.setEnabled(state.canDecrease());
    }

    // ========== KEYBOARD ==========

    /**
     * Focus the quantity field and open the numpad. selectAllOnFocus (layout)
     * selects any value already there, so typing replaces it.
     */
    private void focusCustomField() {
        customQuantityInput.requestFocus();
        InputMethodManager imm = inputMethodManager();
        if (imm != null) imm.showSoftInput(customQuantityInput, InputMethodManager.SHOW_IMPLICIT);
    }

    private void hideKeyboard() {
        InputMethodManager imm = inputMethodManager();
        if (imm != null) imm.hideSoftInputFromWindow(customQuantityInput.getWindowToken(), 0);
    }

    @Nullable
    private InputMethodManager inputMethodManager() {
        return (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
    }
}
