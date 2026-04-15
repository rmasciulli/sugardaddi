package li.masciul.sugardaddi.ui.adapters.timeline;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import li.masciul.sugardaddi.R;
import li.masciul.sugardaddi.ui.adapters.TimelineAdapter;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * TimelineDecoration - Google Calendar-inspired timeline decoration
 *
 * Layout (viewed from left):
 * [Hour Text] | ──────────────────
 *             |      [Meal Card]
 *             |
 *
 * Drawing layers:
 * 1. Horizontal lines at each hour marker (right of vertical line)
 * 2. Vertical timeline line (continuous)
 * 3. Dots on meal items
 * 4. NOW indicator (interpolated between surrounding hour markers)
 *
 * END-OF-DAY HANDLING:
 * The timeline has 25 markers: 0:00 through 23:00, plus a sentinel 0:00 at the
 * bottom (internally hour=24). When the current time is 23:xx, the now indicator
 * interpolates between the 23:00 marker and the sentinel, providing smooth
 * positioning all the way to 23:59. The sentinel uses hour=24 internally, so
 * interpolation math works naturally: fraction = minutes/60 between hours 23 and 24.
 *
 * @version 4.1 - End-of-day sentinel support
 */
public class TimelineDecoration extends RecyclerView.ItemDecoration {

    // Visual constants
    private static final int LINE_X_OFFSET_DP = 38;
    private static final int LINE_WIDTH_DP = 1;
    private static final int HORIZONTAL_LINE_WIDTH_DP = 1;
    private static final int DOT_RADIUS_DP = 4;
    private static final int NOW_INDICATOR_RADIUS_DP = 6;

    // Paints
    private final Paint linePaint;
    private final Paint horizontalLinePaint;
    private final Paint dotPaint;
    private final Paint nowIndicatorPaint;
    private final Paint nowIndicatorOuterPaint;

    // Dimensions (pixels)
    private final int lineXOffset;
    private final int lineWidth;
    private final int horizontalLineWidth;
    private final int dotRadius;
    private final int nowIndicatorRadius;

    // Current time
    private LocalTime currentTime = LocalTime.now();

    // RecyclerView reference
    private RecyclerView attachedRecyclerView;

    // ========== CONSTRUCTOR ==========

    public TimelineDecoration(Context context) {
        float density = context.getResources().getDisplayMetrics().density;

        // Convert dp to pixels
        lineXOffset = (int) (LINE_X_OFFSET_DP * density);
        lineWidth = (int) (LINE_WIDTH_DP * density);
        horizontalLineWidth = (int) (HORIZONTAL_LINE_WIDTH_DP * density);
        dotRadius = (int) (DOT_RADIUS_DP * density);
        nowIndicatorRadius = (int) (NOW_INDICATOR_RADIUS_DP * density);

        // Get colors
        int lineColor = ContextCompat.getColor(context, R.color.timeline_line);
        int dotColor = ContextCompat.getColor(context, R.color.timeline_dot);
        int nowColor = ContextCompat.getColor(context, R.color.timeline_now);
        int horizontalLineColor = 0x20000000; // 12.5% black

        // Vertical timeline
        linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setColor(lineColor);
        linePaint.setStrokeWidth(lineWidth);
        linePaint.setStyle(Paint.Style.STROKE);

        // Horizontal separators (right of vertical line only)
        horizontalLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        horizontalLinePaint.setColor(horizontalLineColor);
        horizontalLinePaint.setStrokeWidth(horizontalLineWidth);
        horizontalLinePaint.setStyle(Paint.Style.STROKE);

        // Meal dots
        dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dotPaint.setColor(dotColor);
        dotPaint.setStyle(Paint.Style.FILL);

        // NOW indicator - inner circle
        nowIndicatorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        nowIndicatorPaint.setColor(nowColor);
        nowIndicatorPaint.setStyle(Paint.Style.FILL);

        // NOW indicator - outer glow
        nowIndicatorOuterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        nowIndicatorOuterPaint.setColor(nowColor);
        nowIndicatorOuterPaint.setAlpha(80);
        nowIndicatorOuterPaint.setStyle(Paint.Style.FILL);
    }

    // ========== PUBLIC API ==========

    public void setCurrentTime(LocalTime time) {
        this.currentTime = time;
        if (attachedRecyclerView != null) {
            attachedRecyclerView.invalidate();
        }
    }

    // ========== DRAWING ==========

    @Override
    public void onDraw(@NonNull Canvas canvas, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        super.onDraw(canvas, parent, state);

        if (attachedRecyclerView == null) {
            attachedRecyclerView = parent;
            registerDataObserver(parent);
        }

        int childCount = parent.getChildCount();
        if (childCount == 0) return;

        // Draw in order: horizontal lines → vertical line → dots → NOW indicator
        drawHorizontalLines(canvas, parent);
        drawTimelineLine(canvas, parent);
        drawDots(canvas, parent);
        drawNowIndicator(canvas, parent);
    }

    /**
     * Draw horizontal lines on the RIGHT side of the vertical line.
     * Lines are drawn at every hour marker position (including end-of-day sentinel).
     */
    private void drawHorizontalLines(Canvas canvas, RecyclerView parent) {
        int childCount = parent.getChildCount();

        for (int i = 0; i < childCount; i++) {
            View child = parent.getChildAt(i);
            int position = parent.getChildAdapterPosition(child);

            if (position == RecyclerView.NO_POSITION) continue;

            if (isHourMarkerItem(parent, position)) {
                float lineY = child.getTop() + (child.getHeight() / 2f);

                canvas.drawLine(
                        lineXOffset + (lineWidth / 2f),
                        lineY,
                        parent.getWidth(),
                        lineY,
                        horizontalLinePaint
                );
            }
        }
    }

    /**
     * Draw vertical timeline line spanning all visible items.
     */
    private void drawTimelineLine(Canvas canvas, RecyclerView parent) {
        int childCount = parent.getChildCount();
        if (childCount == 0) return;

        View firstChild = parent.getChildAt(0);
        View lastChild = parent.getChildAt(childCount - 1);

        float startY = Math.max(firstChild.getTop(), 0);
        float endY = Math.min(lastChild.getBottom(), parent.getHeight());

        canvas.drawLine(lineXOffset, startY, lineXOffset, endY, linePaint);
    }

    /**
     * Draw dots on meal items (on the vertical line).
     */
    private void drawDots(Canvas canvas, RecyclerView parent) {
        int childCount = parent.getChildCount();

        for (int i = 0; i < childCount; i++) {
            View child = parent.getChildAt(i);
            int position = parent.getChildAdapterPosition(child);

            if (position == RecyclerView.NO_POSITION) continue;

            if (isMealItem(parent, position)) {
                float centerY = child.getTop() + (child.getHeight() / 2f);
                canvas.drawCircle(lineXOffset, centerY, dotRadius, dotPaint);
            }
        }
    }

    /**
     * Draw the NOW indicator (red dot with glow).
     *
     * Position is interpolated between the two surrounding hour markers.
     * For times 23:00-23:59, interpolates between 23:00 and the end-of-day
     * sentinel (hour 24), providing smooth positioning all the way to midnight.
     */
    private void drawNowIndicator(Canvas canvas, RecyclerView parent) {
        if (currentTime == null) return;

        HourMarkerPositions hourMarkers = findSurroundingHourMarkers(parent);
        if (hourMarkers == null) return;

        float nowY = calculateNowPosition(hourMarkers);
        if (nowY < 0) return;

        // Outer circle (glow)
        canvas.drawCircle(lineXOffset, nowY, nowIndicatorRadius * 1.5f, nowIndicatorOuterPaint);

        // Inner circle
        canvas.drawCircle(lineXOffset, nowY, nowIndicatorRadius, nowIndicatorPaint);
    }

    // ========== HOUR MARKER DISCOVERY ==========

    /**
     * Find the two hour markers surrounding the current time.
     *
     * Scans all visible hour marker views, extracts their hour value (0-24)
     * and Y-position, then finds the "before" and "after" markers that
     * bracket the current time.
     *
     * Uses the adapter's item list to get the actual hour value (including
     * hour=24 for the end-of-day sentinel) rather than parsing the displayed
     * text, which would confuse the top 0:00 with the bottom 0:00.
     */
    private HourMarkerPositions findSurroundingHourMarkers(RecyclerView parent) {
        RecyclerView.Adapter<?> adapter = parent.getAdapter();
        if (!(adapter instanceof TimelineAdapter)) return null;

        TimelineAdapter timelineAdapter = (TimelineAdapter) adapter;
        List<TimelineItem> items = timelineAdapter.getItems();

        List<HourMarkerInfo> allHourMarkers = new ArrayList<>();
        int childCount = parent.getChildCount();

        for (int i = 0; i < childCount; i++) {
            View child = parent.getChildAt(i);
            int position = parent.getChildAdapterPosition(child);

            if (position == RecyclerView.NO_POSITION) continue;
            if (position >= items.size()) continue;

            // Get the item from the adapter's list to access the real hour value
            TimelineItem item = items.get(position);
            if (item instanceof HourMarkerTimelineItem) {
                HourMarkerTimelineItem hourItem = (HourMarkerTimelineItem) item;
                int hour = hourItem.getHour(); // 0-24 (24 = end-of-day sentinel)
                float centerY = child.getTop() + (child.getHeight() / 2f);
                allHourMarkers.add(new HourMarkerInfo(hour, centerY));
            }
        }

        if (allHourMarkers.isEmpty()) return null;

        int currentHour = currentTime.getHour();
        int currentMinute = currentTime.getMinute();

        HourMarkerInfo before = null;
        HourMarkerInfo after = null;

        for (HourMarkerInfo marker : allHourMarkers) {
            // "Before" = marker whose hour is <= current time
            if (marker.hour < currentHour || (marker.hour == currentHour && currentMinute == 0)) {
                if (before == null || marker.hour > before.hour) {
                    before = marker;
                }
            }
            // Also consider exact hour match when minutes > 0
            // (we're past this hour, so it's "before" us)
            if (marker.hour == currentHour && currentMinute > 0) {
                if (before == null || marker.hour > before.hour) {
                    before = marker;
                }
            }

            // "After" = marker whose hour is > current time
            if (marker.hour > currentHour) {
                if (after == null || marker.hour < after.hour) {
                    after = marker;
                }
            }
        }

        // Fallback: if only one side found, use the same for both
        if (before == null && after != null) {
            before = after;
        } else if (after == null && before != null) {
            after = before;
        }

        if (before != null && after != null) {
            return new HourMarkerPositions(before, after, currentHour, currentMinute);
        }

        return null;
    }

    /**
     * Calculate the Y-position for the now indicator.
     *
     * Linearly interpolates between the "before" and "after" hour markers
     * based on the current minutes within the hour.
     *
     * Works naturally with the end-of-day sentinel (hour=24):
     * At 23:30 → before=23, after=24, hoursDiff=1, minutesFromBefore=30
     * → fraction=0.5, draws halfway between 23:00 and sentinel.
     */
    private float calculateNowPosition(HourMarkerPositions markers) {
        if (markers == null) return -1;

        HourMarkerInfo before = markers.before;
        HourMarkerInfo after = markers.after;

        // Same marker for both = draw exactly there
        if (before.hour == after.hour) {
            return before.yPosition;
        }

        int currentHour = markers.currentHour;
        int currentMinute = markers.currentMinute;

        int hoursDiff = after.hour - before.hour;
        int totalMinutes = hoursDiff * 60;
        int minutesFromBefore = (currentHour - before.hour) * 60 + currentMinute;

        float fraction = (float) minutesFromBefore / totalMinutes;
        float yDiff = after.yPosition - before.yPosition;

        return before.yPosition + (fraction * yDiff);
    }

    // ========== UTILITIES ==========

    private void registerDataObserver(RecyclerView parent) {
        if (parent.getAdapter() != null) {
            parent.getAdapter().registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
                @Override
                public void onChanged() {
                    parent.invalidate();
                }

                @Override
                public void onItemRangeInserted(int positionStart, int itemCount) {
                    parent.invalidate();
                }

                @Override
                public void onItemRangeRemoved(int positionStart, int itemCount) {
                    parent.invalidate();
                }

                @Override
                public void onItemRangeMoved(int fromPosition, int toPosition, int itemCount) {
                    parent.invalidate();
                }
            });
        }
    }

    private boolean isMealItem(RecyclerView parent, int position) {
        RecyclerView.Adapter<?> adapter = parent.getAdapter();
        if (adapter == null) return false;
        return adapter.getItemViewType(position) == 1; // VIEW_TYPE_MEAL
    }

    private boolean isHourMarkerItem(RecyclerView parent, int position) {
        RecyclerView.Adapter<?> adapter = parent.getAdapter();
        if (adapter == null) return false;
        return adapter.getItemViewType(position) == 2; // VIEW_TYPE_HOUR_MARKER
    }

    // ========== INNER CLASSES ==========

    /**
     * Hour marker info with hour value (0-24) and rendered Y-position.
     * Hour 24 represents the end-of-day sentinel.
     */
    private static class HourMarkerInfo {
        final int hour;
        final float yPosition;

        HourMarkerInfo(int hour, float yPosition) {
            this.hour = hour;
            this.yPosition = yPosition;
        }
    }

    /**
     * Pair of surrounding hour markers plus the current time,
     * used for interpolating the now indicator position.
     */
    private static class HourMarkerPositions {
        final HourMarkerInfo before;
        final HourMarkerInfo after;
        final int currentHour;
        final int currentMinute;

        HourMarkerPositions(HourMarkerInfo before, HourMarkerInfo after,
                            int currentHour, int currentMinute) {
            this.before = before;
            this.after = after;
            this.currentHour = currentHour;
            this.currentMinute = currentMinute;
        }
    }
}