package li.masciul.sugardaddi.ui.components;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.method.DigitsKeyListener;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;

import li.masciul.sugardaddi.R;
import li.masciul.sugardaddi.core.enums.Unit;
import li.masciul.sugardaddi.core.models.PortionQuantity;

/**
 * QuantityInputView - the quantity input of a detail screen.
 *
 * Two pages, swiped with the finger (SwipePagerView) and shown by tappable dots
 * (PagerDotsView):
 *   1. "Number of servings" - typical serving size (read-only until change 5)
 *      and a number of servings with - / +.
 *   2. "Portion size" - typical serving size and a quantity typed on the
 *      numpad, with the input's unit shown right after the number.
 *
 * This view only displays and forwards: every rule (steps, conversions, which
 * value gets logged, validity) lives in QuantityInputState, and every number is
 * read and written by QuantityText. Both are plain Java and unit-tested.
 *
 * It is not a card: it draws a white rounded rectangle (bg_quantity_input, same
 * spirit as the confidence banner) and sits in a detail card's "Nutrition
 * Facts" section. The rectangle stays still; the pages slide inside it.
 *
 * STATE ACROSS RE-CREATION: the screen rebuilds this view after an asynchronous
 * load, too late for Android's automatic view-state restore. The owner calls
 * saveState() in onSaveInstanceState() and restoreState() after setup() instead.
 */
public class QuantityInputView extends LinearLayout {

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

    // ========== VIEWS ==========

    private SwipePagerView pager;

    private TextView servingsTypicalValue;
    private TextView servingsValue;
    private MaterialButton servingsMinus;

    private TextView customTypicalValue;
    private EditText customQuantityInput;
    private TextView customUnitLabel;

    // ========== STATE ==========

    /** The rules and values; replaced by setup() for each item. */
    @NonNull private QuantityInputState state = new QuantityInputState(null, Unit.G);

    /** Set while the code fills the field itself, so the watcher doesn't treat it as typing. */
    private boolean updatingField;

    @Nullable private OnQuantityChangedListener listener;

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
        // White rounded rectangle; spacing comes from the pages and the dots.
        setBackgroundResource(R.drawable.bg_quantity_input);

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
        state = new QuantityInputState(servingQuantity, unit);
        pager.setCurrentPage(PAGE_SERVINGS, false);
        refreshAll();
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

    /** What the user entered and the page shown, for restoreState(). */
    @NonNull
    public Bundle saveState() {
        Bundle bundle = new Bundle();
        bundle.putString(KEY_INPUT_MODE, state.getInputMode().name());
        bundle.putDouble(KEY_SERVING_COUNT, state.getServingCount());
        Double custom = state.getCustomQuantity();
        if (custom != null) bundle.putDouble(KEY_CUSTOM_QUANTITY, custom);
        bundle.putInt(KEY_PAGE, pager.getCurrentPage());
        return bundle;
    }

    /**
     * Put back what saveState() captured. Call after setup(): the value edited
     * last is kept and the other one is derived again from the current serving
     * size, which a refresh may have changed.
     */
    public void restoreState(@Nullable Bundle bundle) {
        if (bundle == null) return;
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

        Double custom = state.getCustomQuantity();
        String fieldText = custom != null ? QuantityText.format(custom) : "";
        if (!fieldText.contentEquals(customQuantityInput.getText())) {
            setFieldText(fieldText);
        }
        refreshServingsPage();
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
