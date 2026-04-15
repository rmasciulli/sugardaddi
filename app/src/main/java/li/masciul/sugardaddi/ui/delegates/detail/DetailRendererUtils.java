package li.masciul.sugardaddi.ui.delegates.detail;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import li.masciul.sugardaddi.R;
import li.masciul.sugardaddi.core.enums.DataSource;
import li.masciul.sugardaddi.core.models.FoodProduct;

/**
 * DetailRendererUtils - Shared static helpers used by all DetailRenderer implementations.
 *
 * Centralises logic that would otherwise be copy-pasted across
 * OffProductDetailRenderer, CiqualProductDetailRenderer, and DefaultProductDetailRenderer.
 *
 * Currently handles:
 *   - Attribution panel population (dataSourceName, dataSourceDescription,
 *     dataSourceAttribution, website link)
 *
 * ATTRIBUTION PANEL STRUCTURE (matches all three renderer layouts):
 *   @id/attributionPanel            — MaterialCardView (amber), tappable → website
 *   @id/attributionSourceName       — "🌍 Open Food Facts" (bold, emoji + name)
 *   @id/attributionLegalText        — Short attribution / legal reference (italic)
 *   @id/attributionSourceDescription— One-line description of the source
 *   (static TextView)               — "Visit website →" hint (right-aligned, static)
 *
 * DATA SOURCE API used here (from DataSource enum):
 *   getDisplayWithEmoji(context)    — "🌍 Open Food Facts"
 *   getFullAttribution(context)     — Full legal attribution text
 *   getDescription(context)         — One-line source description
 *   getWebsiteUrl(context)          — URL string or null
 *   isPublic()                      — false for USER/CUSTOM/IMPORTED → hide panel
 *
 * @version 1.0
 */
public final class DetailRendererUtils {

    // Utility class — no instances
    private DetailRendererUtils() {}

    /**
     * Populate the data source attribution panel in a renderer's inflated view.
     *
     * This method handles ALL attribution panel state:
     *   - If the product's data source is not public (user-created content),
     *     the panel is hidden entirely.
     *   - If the source has a website URL, the card is made tappable and opens
     *     the URL in an external browser.
     *   - If the source has no website (e.g. CUSTOM), the card is shown but
     *     not tappable (no "visit website" hint is shown).
     *
     * Expected view IDs in the renderer layout:
     *   R.id.attributionPanel              — MaterialCardView root
     *   R.id.attributionSourceName         — Source name with emoji
     *   R.id.attributionLegalText          — Short attribution (italic)
     *   R.id.attributionSourceDescription  — Source description
     *
     * @param context  Android context (used for string resolution and Intent)
     * @param view     The root view inflated by the renderer
     * @param product  The FoodProduct being displayed
     */
    public static void populateAttribution(@NonNull Context context,
                                           @NonNull View view,
                                           @NonNull FoodProduct product) {

        View panel = view.findViewById(R.id.attributionPanel);
        if (panel == null) return; // Layout doesn't have the panel (shouldn't happen)

        DataSource source = product.getDataSource();
        if (source == null) {
            panel.setVisibility(View.GONE);
            return;
        }

        // Hide panel entirely for user-generated content — there's no third-party to attribute
        if (!source.isPublic()) {
            panel.setVisibility(View.GONE);
            return;
        }

        // Show panel and populate all fields
        panel.setVisibility(View.VISIBLE);

        // "🌍 Open Food Facts" — emoji prefix + display name
        TextView nameView = view.findViewById(R.id.attributionSourceName);
        if (nameView != null) {
            nameView.setText(source.getDisplayWithEmoji(context));
        }

        // Full legal attribution text (italic)
        // e.g. full ODbL license paragraph for OFF, full ANSES/Etalab text for Ciqual
        TextView legalView = view.findViewById(R.id.attributionLegalText);
        if (legalView != null) {
            String attribution = source.getFullAttribution(context);
            if (attribution != null && !attribution.trim().isEmpty()) {
                legalView.setText(attribution);
                legalView.setVisibility(View.VISIBLE);
            } else {
                legalView.setVisibility(View.GONE);
            }
        }

        // One-line description of what this source is
        // e.g. "French food composition table by Anses"
        TextView descView = view.findViewById(R.id.attributionSourceDescription);
        if (descView != null) {
            String description = source.getDescription(context);
            if (description != null && !description.trim().isEmpty()) {
                descView.setText(description);
                descView.setVisibility(View.VISIBLE);
            } else {
                descView.setVisibility(View.GONE);
            }
        }

        // Website tap handler
        String websiteUrl = source.getWebsiteUrl(context);
        if (websiteUrl != null && !websiteUrl.trim().isEmpty()) {
            // Card is tappable — open website in browser
            panel.setOnClickListener(v -> {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(websiteUrl));
                    context.startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(context,
                            context.getString(R.string.browser_open_failed),
                            Toast.LENGTH_SHORT).show();
                }
            });
            // Ensure the "Visit website" hint TextView is visible
            // It is a static TextView in the layout with no id — always shown
            // when the panel is visible and source has a URL. No action needed.
        } else {
            // No website — remove the ripple/click feedback so it doesn't look interactive
            panel.setOnClickListener(null);
            panel.setClickable(false);
            panel.setFocusable(false);
        }
    }
}