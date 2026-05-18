package li.masciul.sugardaddi.ui.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.color.MaterialColors;

/**
 * DotLeaderView — Draws a consistent dotted line that fills available width.
 *
 * Used in ingredient rows to visually connect ingredient names to their
 * measures. Unlike a drawable-based approach, this calculates dot positions
 * mathematically so the pattern is always consistent regardless of view width.
 *
 * Dot size and spacing are defined in dp and converted to pixels at runtime
 * to ensure density-independence.
 */
public class DotLeaderView extends View {

    // Dot radius in dp — controls dot size
    private static final float DOT_RADIUS_DP = 1.2f;

    // Gap between dot centers in dp — controls spacing
    private static final float DOT_SPACING_DP = 6f;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float dotRadius;
    private float dotSpacing;

    public DotLeaderView(@NonNull Context context) {
        super(context);
        init(context);
    }

    public DotLeaderView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public DotLeaderView(@NonNull Context context,
                         @Nullable AttributeSet attrs,
                         int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(@NonNull Context context) {
        float density = context.getResources().getDisplayMetrics().density;
        dotRadius  = DOT_RADIUS_DP  * density;
        dotSpacing = DOT_SPACING_DP * density;

        paint.setStyle(Paint.Style.FILL);

        // Resolve colorOutlineVariant from the current theme at runtime —
        // correct for both light and dark mode
        int color = MaterialColors.getColor(
                context,
                com.google.android.material.R.attr.colorOutlineVariant,
                android.graphics.Color.LTGRAY);
        paint.setColor(color);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        float width = getWidth();
        // Align dots near the baseline of the text for better readability
        float cy    = getHeight() * 0.75f;

        if (width <= 0 || dotSpacing <= 0) return;

        // Calculate how many dots fit, then center the pattern horizontally
        int dotCount = (int) (width / dotSpacing);
        if (dotCount <= 0) return;

        float totalWidth = (dotCount - 1) * dotSpacing;
        float startX     = (width - totalWidth) / 2f;

        for (int i = 0; i < dotCount; i++) {
            float cx = startX + i * dotSpacing;
            canvas.drawCircle(cx, cy, dotRadius, paint);
        }
    }
}