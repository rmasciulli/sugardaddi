package li.masciul.sugardaddi.utils.language;

import android.app.Activity;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import li.masciul.sugardaddi.R;
import li.masciul.sugardaddi.managers.LanguageManager;

public class LanguageSelector {

    public interface LanguageChangeListener {
        void onLanguageChanged(LanguageManager.SupportedLanguage newLanguage);
    }

    /**
     * Show language selection dialog
     * @param activity The current activity
     * @param listener Callback for language changes
     */
    public static void showLanguageDialog(Activity activity, LanguageChangeListener listener) {
        View dialogView = LayoutInflater.from(activity).inflate(R.layout.language_selector_dialog, null);

        // Find views
        View englishOption = dialogView.findViewById(R.id.languageEnglish);
        View frenchOption = dialogView.findViewById(R.id.languageFrench);
        ImageView checkEnglish = dialogView.findViewById(R.id.checkEnglish);
        ImageView checkFrench = dialogView.findViewById(R.id.checkFrench);

        // Get current language
        LanguageManager.SupportedLanguage currentLanguage = LanguageManager.getCurrentLanguage(activity);

        // Update UI to show current selection
        updateSelectionUI(currentLanguage, checkEnglish, checkFrench);

        // Create dialog
        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setView(dialogView)
                .create();

        // Set click listeners
        englishOption.setOnClickListener(v -> {
            LanguageManager.SupportedLanguage newLanguage = LanguageManager.SupportedLanguage.ENGLISH;
            if (newLanguage != currentLanguage) {
                changeLanguage(activity, newLanguage, listener);
            }
            dialog.dismiss();
        });

        frenchOption.setOnClickListener(v -> {
            LanguageManager.SupportedLanguage newLanguage = LanguageManager.SupportedLanguage.FRENCH;
            if (newLanguage != currentLanguage) {
                changeLanguage(activity, newLanguage, listener);
            }
            dialog.dismiss();
        });

        dialog.show();
    }

    /**
     * Update the selection UI to show current language
     */
    private static void updateSelectionUI(LanguageManager.SupportedLanguage currentLanguage,
                                          ImageView checkEnglish, ImageView checkFrench) {
        checkEnglish.setVisibility(
                currentLanguage == LanguageManager.SupportedLanguage.ENGLISH ?
                        View.VISIBLE : View.GONE
        );
        checkFrench.setVisibility(
                currentLanguage == LanguageManager.SupportedLanguage.FRENCH ?
                        View.VISIBLE : View.GONE
        );
    }

    /**
     * Change the app language and restart activity
     */
    private static void changeLanguage(Activity activity,
                                       LanguageManager.SupportedLanguage newLanguage,
                                       LanguageChangeListener listener) {
        // Set the new language
        LanguageManager.setLanguage(activity, newLanguage);

        // Notify listener
        if (listener != null) {
            listener.onLanguageChanged(newLanguage);
        }

        // Restart the activity to apply language changes
        Intent intent = activity.getIntent();
        activity.finish();
        activity.startActivity(intent);
        activity.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }
}