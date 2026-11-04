package li.masciul.sugardaddi.ui.components;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.method.DigitsKeyListener;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ViewFlipper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;

import java.math.RoundingMode;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.Locale;

import li.masciul.sugardaddi.R;
import li.masciul.sugardaddi.core.enums.Unit;
import li.masciul.sugardaddi.core.models.PortionQuantity;

/**
 * QuantityInputView - the "Measurements & quantity" card.
 *
 * Two slides, switched with the arrows, a horizontal swipe, and shown by dots:
 *   1. SERVINGS - typical serving size (read-only for now) and a number of
 *      servings with - / +: starts at 1, steps of 0.5, minimum 0.5.
 *   2. CUSTOM   - typical serving size and a quantity typed on the numpad, with
 *      the item's unit shown right after the number. The unit is fixed by the
 *      host in setup(); letting the user choose it is the job of a later slide 3.
 *
 * WHAT GETS LOGGED: the slide last EDITED decides (PortionQuantity.InputMode).
 * Swiping only shows the other figure; it never changes what will be saved.
 * When the serving size is known each slide shows the other's equivalent
 * (3.5 servings of 30 g = 105 g; 100 g = 3.33 servings). When it isn't, nothing
 * is converted, since there is nothing to convert with.
 *
 * Validity: the servings slide is always valid. The custom slide, once edited,
 * is valid only with a positive number - getQuantity() returns null otherwise,
 * and the listener is told so the host can disable its Add button.
 *
 * Built on ViewFlipper rather than ViewPager2: no extra dependency, the card
 * sizes itself to the taller slide (no fixed height to clip large fonts), and
 * the values live in plain views instead of a recycling adapter.
 *
 * The view IS the card: margins, corner radius and elevation are set where it is
 * placed in a layout, like the other detail cards.
 */
public class QuantityInputView extends MaterialCardView {

    /** Told every time the value that would be logged changes. */
    public interface OnQuantityChangedListener {
        /** @param quantity what Add would log now, or null if the input is invalid */
        void onQuantityChanged(@Nullable PortionQuantity quantity);
    }

    // ========== RULES ==========

    static final double SERVING_STEP = 0.5;
    static final double MIN_SERVINGS = 0.5;

    private static final int PAGE_SERVINGS = 0;
    private static final int PAGE_CUSTOM = 1;
    private static final long SLIDE_DURATION_MS = 200;
    private static final int SWIPE_MIN_DISTANCE_DP = 48;

    // ========== VIEWS ==========

    private ViewFlipper flipper;
    private MaterialButton previousButton;
    private MaterialButton nextButton;
    private LinearLayout dotsContainer;

    private TextView servingsTypicalValue;
    private TextView servingsValue;
    private MaterialButton servingsMinus;

    private TextView customTypicalValue;
    private EditText customQuantityInput;
    private TextView customUnitLabel;

    // ========== STATE ==========

    /** Size of one serving, in servingUnit; null when the item has none. */
    @Nullable private Double servingQuantity;
    /** Unit of servingQuantity AND of the custom quantity: one unit per card. */
    @NonNull private Unit servingUnit = Unit.G;

    private double servingCount = 1.0;
    /** Parsed custom quantity; null when the field is empty or not a positive number. */
    @Nullable private Double customQuantity;
    @NonNull private PortionQuantity.InputMode inputMode = PortionQuantity.InputMode.SERVINGS;

    /** Set while the code fills the field itself, so the watcher doesn't treat it as typing. */
    private boolean updatingField;

    @Nullable private OnQuantityChangedListener listener;
    private GestureDetector swipeDetector;

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
        LayoutInflater.from(context).inflate(R.layout.view_quantity_input, this, true);

        flipper = findViewById(R.id.quantityFlipper);
        previousButton = findViewById(R.id.quantityPrevious);
        nextButton = findViewById(R.id.quantityNext);
        dotsContainer = findViewById(R.id.quantityDots);

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

        previousButton.setOnClickListener(v -> showPage(PAGE_SERVINGS, false));
        nextButton.setOnClickListener(v -> showPage(PAGE_CUSTOM, true));
        servingsMinus.setOnClickListener(v -> changeServings(stepDown(servingCount)));
        servingsPlus.setOnClickListener(v -> changeServings(stepUp(servingCount)));
        // The number has no box and can be short ("5"): the whole row is the target.
        findViewById(R.id.customQuantityRow).setOnClickListener(v -> focusCustomField());

        customQuantityInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (!updatingField) onCustomQuantityTyped(s.toString());
            }
        });

        swipeDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(@Nullable MotionEvent down, @NonNull MotionEvent up,
                                   float velocityX, float velocityY) {
                if (down == null) return false;
                float dx = up.getX() - down.getX();
                float minDistance = SWIPE_MIN_DISTANCE_DP * getResources().getDisplayMetrics().density;
                if (Math.abs(dx) < minDistance || Math.abs(velocityX) < Math.abs(velocityY)) {
                    return false;
                }
                if (dx < 0) showPage(PAGE_CUSTOM, true);
                else showPage(PAGE_SERVINGS, false);
                return true;
            }
        });

        buildDots();
        updateNavigation();
        refreshAll();
    }

    // ========== PUBLIC API ==========

    /**
     * Configure the card for one item. Resets it to "1 serving" on the first slide.
     *
     * @param servingQuantity size of one serving in {@code unit}, or null if the
     *                        item has none
     * @param unit            G or ML (anything else is read as G): the unit of the
     *                        serving size and of the custom quantity, shown after
     *                        the typed number
     */
    public void setup(@Nullable Double servingQuantity, @NonNull Unit unit) {
        this.servingQuantity = (servingQuantity != null && servingQuantity > 0) ? servingQuantity : null;
        this.servingUnit = unit == Unit.ML ? Unit.ML : Unit.G;
        this.servingCount = 1.0;
        this.customQuantity = null;
        this.inputMode = PortionQuantity.InputMode.SERVINGS;

        flipper.setInAnimation(null);
        flipper.setOutAnimation(null);
        flipper.setDisplayedChild(PAGE_SERVINGS);
        updateNavigation();
        refreshAll();
        notifyChanged();
    }

    /**
     * What Add would log now, frozen as a PortionQuantity - or null while the
     * custom slide is the one being edited and holds no positive number.
     */
    @Nullable
    public PortionQuantity getQuantity() {
        if (inputMode == PortionQuantity.InputMode.SERVINGS) {
            return PortionQuantity.ofServings(servingCount, servingQuantity,
                    servingQuantity != null ? servingUnit : null);
        }
        if (customQuantity == null) return null;
        return PortionQuantity.ofQuantity(customQuantity, servingUnit, servingQuantity);
    }

    public void setOnQuantityChangedListener(@Nullable OnQuantityChangedListener listener) {
        this.listener = listener;
    }

    // ========== EDITS ==========

    private void changeServings(double newCount) {
        servingCount = newCount;
        inputMode = PortionQuantity.InputMode.SERVINGS;
        // Keep the custom slide showing the same amount when it can be converted.
        if (servingQuantity != null) {
            customQuantity = servingCount * servingQuantity;
            setFieldText(formatNumber(customQuantity));
        }
        refreshServingsSlide();
        notifyChanged();
    }

    private void onCustomQuantityTyped(@NonNull String text) {
        customQuantity = parseQuantity(text);
        inputMode = PortionQuantity.InputMode.QUANTITY;
        // Show how many servings that is, when it can be converted.
        if (customQuantity != null && servingQuantity != null) {
            servingCount = customQuantity / servingQuantity;
        }
        refreshServingsSlide();
        notifyChanged();
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
        String typical = servingQuantity != null
                ? formatAmount(servingQuantity, servingUnit)
                : getContext().getString(R.string.quantity_not_set);
        servingsTypicalValue.setText(typical);
        customTypicalValue.setText(typical);
        customUnitLabel.setText(servingUnit.getAbbreviation());

        String fieldText = customQuantity != null ? formatNumber(customQuantity) : "";
        if (!fieldText.contentEquals(customQuantityInput.getText())) {
            setFieldText(fieldText);
        }
        refreshServingsSlide();
    }

    private void refreshServingsSlide() {
        String count = formatNumber(servingCount);
        if (servingQuantity != null) {
            count += " (" + formatAmount(servingCount * servingQuantity, servingUnit) + ")";
        }
        servingsValue.setText(count);
        servingsMinus.setEnabled(servingCount > MIN_SERVINGS);
    }

    @NonNull
    private String formatAmount(double value, @NonNull Unit unit) {
        return formatNumber(value) + " " + unit.getAbbreviation();
    }

    // ========== SLIDES ==========

    private void showPage(int page, boolean forward) {
        if (page == flipper.getDisplayedChild()) return;
        flipper.setInAnimation(slide(forward ? 1f : -1f, 0f));
        flipper.setOutAnimation(slide(0f, forward ? -1f : 1f));
        flipper.setDisplayedChild(page);
        updateNavigation();

        if (page == PAGE_CUSTOM) {
            // Arriving on an empty custom slide: ready to type.
            if (customQuantityInput.getText().length() == 0) focusCustomField();
        } else {
            // Leaving it: the keyboard would only hide the rest of the screen.
            hideKeyboard();
        }
    }

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

    @NonNull
    private Animation slide(float fromXDelta, float toXDelta) {
        Animation animation = new TranslateAnimation(
                Animation.RELATIVE_TO_PARENT, fromXDelta, Animation.RELATIVE_TO_PARENT, toXDelta,
                Animation.RELATIVE_TO_PARENT, 0f, Animation.RELATIVE_TO_PARENT, 0f);
        animation.setDuration(SLIDE_DURATION_MS);
        return animation;
    }

    /** Arrows only where there is somewhere to go; dots follow the visible slide. */
    private void updateNavigation() {
        int current = flipper.getDisplayedChild();
        int last = flipper.getChildCount() - 1;
        // INVISIBLE, not GONE: the slides keep the same width on every page.
        previousButton.setVisibility(current > 0 ? VISIBLE : INVISIBLE);
        nextButton.setVisibility(current < last ? VISIBLE : INVISIBLE);

        int active = MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary);
        int inactive = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutlineVariant);
        for (int i = 0; i < dotsContainer.getChildCount(); i++) {
            View dot = dotsContainer.getChildAt(i);
            if (dot.getBackground() instanceof GradientDrawable) {
                ((GradientDrawable) dot.getBackground()).setColor(i == current ? active : inactive);
            }
        }
    }

    private void buildDots() {
        dotsContainer.removeAllViews();
        int size = Math.round(8 * getResources().getDisplayMetrics().density);
        int gap = Math.round(4 * getResources().getDisplayMetrics().density);
        for (int i = 0; i < flipper.getChildCount(); i++) {
            View dot = new View(getContext());
            GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            dot.setBackground(circle);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
            params.setMargins(gap, 0, gap, 0);
            dotsContainer.addView(dot, params);
        }
    }

    /**
     * Watches every touch for a horizontal swipe without consuming anything, so
     * buttons and the text field keep working normally underneath.
     */
    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (swipeDetector != null) swipeDetector.onTouchEvent(event);
        return super.dispatchTouchEvent(event);
    }

    // ========== PURE HELPERS (unit-testable) ==========

    /**
     * Next half-step strictly above: 1 -> 1.5, 3.5 -> 4, and 3.33 (typed as a
     * quantity) -> 3.5 rather than 3.83.
     */
    static double stepUp(double count) {
        return Math.floor(count / SERVING_STEP + 1e-9) * SERVING_STEP + SERVING_STEP;
    }

    /** Previous half-step strictly below, never under MIN_SERVINGS: 3.33 -> 3, 0.5 -> 0.5. */
    static double stepDown(double count) {
        double down = Math.ceil(count / SERVING_STEP - 1e-9) * SERVING_STEP - SERVING_STEP;
        return Math.max(MIN_SERVINGS, down);
    }

    /**
     * Read a typed quantity: "105", "33,5" or "33.5" (spaces ignored). Null when
     * empty, malformed, zero, negative or not finite. Only digits and one decimal
     * separator are accepted: Double.parseDouble() alone would also take pasted
     * text like "1e5" or "0x1p3".
     */
    @Nullable
    static Double parseQuantity(@Nullable String text) {
        if (text == null) return null;
        String cleaned = text.replace(" ", "").replace("\u00A0", "").replace(',', '.');
        if (!cleaned.matches("[0-9]*\\.?[0-9]*") || !cleaned.matches(".*[0-9].*")) return null;
        try {
            double value = Double.parseDouble(cleaned);
            return (value > 0 && !Double.isInfinite(value) && !Double.isNaN(value)) ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** formatNumber() with the app language's decimal separator (LanguageManager sets the default locale). */
    @NonNull
    static String formatNumber(double value) {
        char separator = DecimalFormatSymbols.getInstance(Locale.getDefault()).getDecimalSeparator();
        return formatNumber(value, separator == ',' ? ',' : '.');
    }

    /**
     * At most one decimal, rounded half up, ASCII digits, no grouping:
     * 3.5 -> "3,5" with ',', 105.0 -> "105", 3.25 -> "3,3". Digits stay ASCII
     * whatever the device language, so parseQuantity() can always read them back.
     */
    @NonNull
    static String formatNumber(double value, char decimalSeparator) {
        NumberFormat format = NumberFormat.getNumberInstance(Locale.ROOT);
        format.setGroupingUsed(false);
        format.setMinimumFractionDigits(0);
        format.setMaximumFractionDigits(1);
        format.setRoundingMode(RoundingMode.HALF_UP);
        String text = format.format(value);
        return decimalSeparator == ',' ? text.replace('.', ',') : text;
    }
}
