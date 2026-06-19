package li.masciul.sugardaddi.ui.delegates.detail;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import li.masciul.sugardaddi.R;
import li.masciul.sugardaddi.core.enums.DataSourceType;
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
 *   @id/attributionPanel            - MaterialCardView (amber), tappable → website
 *   @id/attributionSourceName       - "🌍 Open Food Facts" (bold, emoji + name)
 *   @id/attributionLegalText        - Short attribution / legal reference (italic)
 *   @id/attributionSourceDescription- One-line description of the source
 *   (static TextView)               - "Visit website →" hint (right-aligned, static)
 *
 * DATA SOURCE API used here (from DataSource enum):
 *   getDisplayWithEmoji(context)    - "🌍 Open Food Facts"
 *   getFullAttribution(context)     - Full legal attribution text
 *   getDescription(context)         - One-line source description
 *   getWebsiteUrl(context)          - URL string or null
 *   isPublic()                      - false for USER/CUSTOM/IMPORTED → hide panel
 *
 * @version 1.0
 */
public final class DetailRendererUtils {

    // Utility class - no instances
    private DetailRendererUtils() {}

    /**
     * Populate the attribution panel by DataSourceType directly.
     *
     * Used by all detail renderers (food products, recipes, and future item types).
     * Callers pass their item's DataSourceType directly via item.getDataSource().
     *
     * @param context  Android context
     * @param view     Root view inflated by the renderer
     * @param source   The DataSourceType to attribute
     */
    public static void populateAttribution(@NonNull Context context,
                                           @NonNull View view,
                                           @NonNull DataSourceType source) {
        View panel = view.findViewById(R.id.attributionPanel);
        if (panel == null) return;

        // Hide panel for user-generated content - no third-party to attribute
        if (!source.isPublic()) {
            panel.setVisibility(View.GONE);
            return;
        }

        panel.setVisibility(View.VISIBLE);

        // "🍳 TheMealDB", "🌍 Open Food Facts", etc.
        TextView nameView = view.findViewById(R.id.attributionSourceName);
        if (nameView != null) {
            nameView.setText(source.getDisplayWithEmoji(context));
        }

        // Full legal attribution text (italic)
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

        // One-line source description
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

        // Make card tappable if a website URL is available
        String websiteUrl = source.getWebsiteUrl(context);
        if (websiteUrl != null && !websiteUrl.trim().isEmpty()) {
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
        } else {
            panel.setOnClickListener(null);
            panel.setClickable(false);
            panel.setFocusable(false);
        }
    }
}