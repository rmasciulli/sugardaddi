package li.masciul.sugardaddi.ui.components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.color.MaterialColors;

import li.masciul.sugardaddi.R;

/**
 * PagerDotsView - one dot per page of a SwipePagerView, tappable.
 *
 * The current page's dot uses the primary color, the others the outline
 * variant, as before. Dots are drawn close together (16dp apart, centre to
 * centre), but the touch area is wider: 16dp of extra width on each side and
 * 24dp of height, and a tap goes to the NEAREST dot, so a tap lands on the
 * intended page without aiming at an 8dp circle.
 *
 * Accessibility: read as "Page 1 of 2". Activating it with TalkBack (no touch
 * position) goes to the next page, wrapping around; the pager itself also
 * offers scroll forward / backward actions.
 *
 * Use: put it under the pager, wrap_content, then call attachTo(pager) once the
 * pages are in place.
 */
public class PagerDotsView extends View {

    private static final float DOT_DIAMETER_DP = 8f;
    /** Centre to centre: 8dp dots with an 8dp gap. */
    private static final float DOT_SPACING_DP = 16f;
    /** Touch area added on each side of the first and last dot's centre. */
    private static final float TOUCH_HALF_WIDTH_DP = 16f;
    private static final float TOUCH_HEIGHT_DP = 24f;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float dotRadius;
    private final int dotSpacing;
    private final int touchHalfWidth;
    private final int touchHeight;
    private final int activeColor;
    private final int inactiveColor;

    @Nullable private SwipePagerView pager;
    private int pageCount;
    private int currentPage;
    /** Page under the last tap, consumed by performClick(); -1 when not from a touch. */
    private int tappedPage = -1;

    private final SwipePagerView.OnPageChangeListener pageListener = page -> {
        currentPage = page;
        updateDescription();
        invalidate();
    };

    // ========== CONSTRUCTORS ==========

    public PagerDotsView(@NonNull Context context) {
        this(context, null);
    }

    public PagerDotsView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PagerDotsView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        float density = getResources().getDisplayMetrics().density;
        dotRadius = DOT_DIAMETER_DP * density / 2f;
        dotSpacing = Math.round(DOT_SPACING_DP * density);
        touchHalfWidth = Math.round(TOUCH_HALF_WIDTH_DP * density);
        touchHeight = Math.round(TOUCH_HEIGHT_DP * density);
        activeColor = MaterialColors.getColor(context,
                com.google.android.material.R.attr.colorPrimary, Color.DKGRAY);
        inactiveColor = MaterialColors.getColor(context,
                com.google.android.material.R.attr.colorOutlineVariant, Color.LTGRAY);
        setClickable(true);
    }

    // ========== PUBLIC API ==========

    /** Follow this pager: one dot per page, current page highlighted. */
    public void attachTo(@NonNull SwipePagerView pager) {
        if (this.pager != null) this.pager.removeOnPageChangeListener(pageListener);
        this.pager = pager;
        pager.addOnPageChangeListener(pageListener);
        pageCount = pager.getPageCount();
        currentPage = pager.getCurrentPage();
        updateDescription();
        requestLayout();
        invalidate();
    }

    // ========== MEASURE / DRAW ==========

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int dotsSpan = Math.max(0, pageCount - 1) * dotSpacing;
        int width = getPaddingLeft() + getPaddingRight() + dotsSpan + 2 * touchHalfWidth;
        int height = getPaddingTop() + getPaddingBottom() + touchHeight;
        setMeasuredDimension(resolveSize(width, widthMeasureSpec), resolveSize(height, heightMeasureSpec));
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        float firstCenter = firstDotCenterX();
        float centerY = getPaddingTop() + (getHeight() - getPaddingTop() - getPaddingBottom()) / 2f;
        for (int slot = 0; slot < pageCount; slot++) {
            paint.setColor(pageAtSlot(slot) == currentPage ? activeColor : inactiveColor);
            canvas.drawCircle(firstCenter + slot * dotSpacing, centerY, dotRadius, paint);
        }
    }

    // ========== TOUCH ==========

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (pager == null || pageCount < 2 || !isEnabled()) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                return true;
            case MotionEvent.ACTION_UP: {
                // Nearest dot to the finger; anywhere in the view counts.
                int slot = Math.round((event.getX() - firstDotCenterX()) / dotSpacing);
                slot = Math.max(0, Math.min(pageCount - 1, slot));
                tappedPage = pageAtSlot(slot);
                performClick();
                return true;
            }
            default:
                return true;
        }
    }

    /**
     * A tap goes to the tapped page. Without a touch (TalkBack, keyboard), goes
     * to the next page, wrapping around to the first.
     */
    @Override
    public boolean performClick() {
        super.performClick();
        if (pager != null && pageCount > 1) {
            int target = tappedPage >= 0 ? tappedPage : (currentPage + 1) % pageCount;
            pager.setCurrentPage(target, true);
        }
        tappedPage = -1;
        return true;
    }

    // ========== HELPERS ==========

    /** X of the first dot's centre; the dots are centred in the view. */
    private float firstDotCenterX() {
        float span = Math.max(0, pageCount - 1) * (float) dotSpacing;
        float available = getWidth() - getPaddingLeft() - getPaddingRight();
        return getPaddingLeft() + (available - span) / 2f;
    }

    /** Dots follow the pager's mirroring in right-to-left languages. */
    private int pageAtSlot(int slot) {
        return getLayoutDirection() == LAYOUT_DIRECTION_RTL ? pageCount - 1 - slot : slot;
    }

    private void updateDescription() {
        setContentDescription(pageCount > 0
                ? getContext().getString(R.string.pager_page_indicator, currentPage + 1, pageCount)
                : null);
    }
}
