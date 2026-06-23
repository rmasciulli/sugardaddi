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

/**
 * DetailRendererUtils - detail-screen-only shared helpers for the DetailRenderer
 * implementations. Image resolution/loading now lives in
 * {@link li.masciul.sugardaddi.ui.utils.ImageDisplayUtils} (shared with the search
 * delegates); this class is attribution-only.
 *
 * ATTRIBUTION PANEL STRUCTURE:
 *   @id/attributionPanel             - MaterialCardView (amber), tappable → website
 *   @id/attributionSourceName        - "🌍 Open Food Facts" (emoji + name)
 *   @id/attributionLegalText         - Short attribution / legal reference (italic)
 *   @id/attributionSourceDescription - One-line source description
 */
public final class DetailRendererUtils {

    private DetailRendererUtils() {} // no instances

    /**
     * Populate the attribution panel by DataSourceType directly.
     * Used by all detail renderers (food products, recipes, future item types).
     *
     * @param context Android context
     * @param view    Root view inflated by the renderer
     * @param source  The DataSourceType to attribute
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

        TextView nameView = view.findViewById(R.id.attributionSourceName);
        if (nameView != null) {
            nameView.setText(source.getDisplayWithEmoji(context));
        }

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