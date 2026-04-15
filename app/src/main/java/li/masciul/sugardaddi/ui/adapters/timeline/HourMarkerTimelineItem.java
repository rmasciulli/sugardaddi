package li.masciul.sugardaddi.ui.adapters.timeline;

import java.time.LocalTime;

/**
 * HourMarkerTimelineItem - Represents an hour marker in the timeline
 *
 * Visual markers at each hour (00:00, 01:00, 02:00, etc.) to help
 * users orient themselves on the 24-hour timeline.
 *
 * END-OF-DAY SENTINEL:
 * A special marker at the bottom of the timeline displaying "0:00" that represents
 * midnight / end of day. This provides visual space for the "now" indicator to
 * reach the end of the day (23:00 → 23:59). Internally uses hour=24 for correct
 * sorting and interpolation, but displays as "0:00".
 *
 * Timeline layout: 0:00, 1:00, 2:00, ... 23:00, 0:00(sentinel)
 *
 * At midnight, the now indicator jumps back to the top 0:00 marker (new day).
 *
 * @version 1.1 - Added end-of-day sentinel support
 */
public class HourMarkerTimelineItem extends TimelineItem {

    private final int hour; // 0-24 (24 = end-of-day sentinel)
    private final boolean isEndOfDay; // true for the bottom "0:00" sentinel

    /**
     * Create a standard hour marker item
     *
     * @param hour Hour of the day (0-23)
     */
    public HourMarkerTimelineItem(int hour) {
        if (hour < 0 || hour > 23) {
            throw new IllegalArgumentException("Hour must be 0-23, got: " + hour);
        }
        this.hour = hour;
        this.isEndOfDay = false;
    }

    /**
     * Private constructor for creating the end-of-day sentinel.
     * Uses hour=24 internally for correct sorting and interpolation math.
     */
    private HourMarkerTimelineItem(int hour, boolean isEndOfDay) {
        this.hour = hour;
        this.isEndOfDay = isEndOfDay;
    }

    /**
     * Factory method to create the end-of-day sentinel marker.
     *
     * Displays as "0:00" but sorts after all other items (including 23:00 and
     * any meals between 23:00-23:59). Uses hour=24 internally so that
     * interpolation between 23:00 and this marker works correctly:
     * fraction = minutes / 60 maps naturally to the 23:00→24:00 range.
     *
     * @return End-of-day sentinel marker
     */
    public static HourMarkerTimelineItem createEndOfDaySentinel() {
        return new HourMarkerTimelineItem(24, true);
    }

    @Override
    public Type getType() {
        return Type.HOUR_MARKER;
    }

    /**
     * Get the time for sorting.
     *
     * End-of-day sentinel returns LocalTime.MAX (23:59:59.999) to sort after
     * everything else, including meals at 23:xx.
     * Regular markers return their exact hour time.
     */
    @Override
    public LocalTime getTime() {
        if (isEndOfDay) {
            // Sort after everything, including 23:59 meals
            return LocalTime.MAX;
        }
        return LocalTime.of(hour, 0);
    }

    @Override
    public String getItemId() {
        return isEndOfDay ? "hour_end_of_day" : "hour_" + hour;
    }

    /**
     * Get the hour value (0-24).
     *
     * Returns 24 for the end-of-day sentinel, which allows natural interpolation
     * math in TimelineDecoration: at 23:30, fraction = 30/60 between hour 23
     * and hour 24.
     *
     * @return Hour of day (0-23 for regular, 24 for end-of-day)
     */
    public int getHour() {
        return hour;
    }

    /**
     * Check if this is the end-of-day sentinel marker
     *
     * @return true if this is the bottom "0:00" sentinel
     */
    public boolean isEndOfDay() {
        return isEndOfDay;
    }

    /**
     * Get formatted hour string for display.
     *
     * Both the top 0:00 marker and the end-of-day sentinel display as "0:00".
     * Regular markers display as "HH:00".
     *
     * @return Formatted hour string
     */
    public String getFormattedHour() {
        if (isEndOfDay) {
            return "0:00";
        }
        return String.format("%02d:00", hour);
    }
}