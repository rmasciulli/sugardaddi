package li.masciul.sugardaddi.ui.components;

import android.content.Context;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.method.DigitsKeyListener;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import li.masciul.sugardaddi.R;
import li.masciul.sugardaddi.core.enums.Unit;

/**
 * TypicalServingDialog - edit the typical serving size of one item (change 5b).
 *
 * One number field with the unit shown inside it, the source's own value
 * underneath for reference, and three actions:
 *   Save              - store the typed value as the user's preference
 *   Use source value  - remove the preference (only offered when one exists)
 *   Cancel
 *
 * The dialog only collects the answer: storing it (UserItemDataDao.setServing)
 * and applying it to the screen is the caller's job, through the Listener.
 *
 * The field reads numbers with QuantityText (digits, "." or ",") like the
 * quantity input, and Save stays disabled while it holds no positive number.
 */
public final class TypicalServingDialog {

    /** The user's choice. Called on the main thread. */
    public interface Listener {
        /** @param quantity the new typical serving, > 0, in the dialog's unit */
        void onSave(double quantity);

        /** Remove the user's value: the source's serving size applies again. */
        void onReset();
    }

    private TypicalServingDialog() {
        // Static entry point only
    }

    /**
     * @param unit         G or ML: the unit the value is typed in
     * @param current      the typical serving shown now (prefilled), or null
     * @param sourceValue  the source's own serving size in that unit, or null
     * @param hasUserValue true when the current value is the user's (offers Reset)
     */
    public static void show(@NonNull Context context, @NonNull Unit unit,
                            @Nullable Double current, @Nullable Double sourceValue,
                            boolean hasUserValue, @NonNull Listener listener) {
        float density = context.getResources().getDisplayMetrics().density;

        TextInputLayout layout = new TextInputLayout(context, null,
                com.google.android.material.R.attr.textInputOutlinedStyle);
        layout.setSuffixText(unit.getAbbreviation());
        layout.setHelperText(sourceValue != null
                ? context.getString(R.string.typical_serving_source,
                        QuantityText.formatAmount(sourceValue, unit))
                : context.getString(R.string.typical_serving_source_none));

        TextInputEditText field = new TextInputEditText(layout.getContext());
        // Accept "." and "," whatever the keyboard; keep the numeric keypad.
        field.setKeyListener(DigitsKeyListener.getInstance("0123456789.,"));
        field.setRawInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        field.setSingleLine(true);
        if (current != null) field.setText(QuantityText.format(current));
        field.setSelectAllOnFocus(true);
        layout.addView(field);

        // Dialog content padding, as in Material dialogs (24dp sides).
        FrameLayout container = new FrameLayout(context);
        int side = Math.round(24 * density);
        int top = Math.round(8 * density);
        container.setPadding(side, top, side, 0);
        container.addView(layout);

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.typical_serving_dialog_title)
                .setView(container)
                .setPositiveButton(R.string.save, (d, w) -> {
                    Double value = QuantityText.parse(textOf(field));
                    if (value != null) listener.onSave(value);
                })
                .setNegativeButton(R.string.cancel, null);
        if (hasUserValue) {
            builder.setNeutralButton(R.string.typical_serving_reset, (d, w) -> listener.onReset());
        }

        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> {
            Button save = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            save.setEnabled(QuantityText.parse(textOf(field)) != null);
            field.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable s) {
                    save.setEnabled(QuantityText.parse(s.toString()) != null);
                }
            });
            field.requestFocus();
        });
        // Open with the numpad up: the dialog exists to type one number.
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
        dialog.show();
    }

    @NonNull
    private static String textOf(@NonNull TextInputEditText field) {
        return field.getText() != null ? field.getText().toString() : "";
    }
}
