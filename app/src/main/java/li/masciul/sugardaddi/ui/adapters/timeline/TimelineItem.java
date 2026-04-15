package li.masciul.sugardaddi.ui.adapters.timeline;

import java.time.LocalTime;

/**
 * TimelineItem - Base class for items displayed in the timeline
 *
 * Supports two types of items:
 * 1. MEAL - An actual meal at a specific time
 * 2. HOUR_MARKER - A visual hour marker on the timeline
 *
 * Used by TimelineAdapter to create a unified timeline view with
 * both meals and hour markers.
 *
 * @version 1.0
 */
public abstract class TimelineItem {

    /**
     * Type of timeline item
     */
    public enum Type {
        MEAL,           // An actual meal
        HOUR_MARKER     // Hour marker (00:00, 01:00, etc.)
    }

    /**
     * Get the type of this timeline item
     *
     * @return Type (MEAL or HOUR_MARKER)
     */
    public abstract Type getType();

    /**
     * Get the time of this item (for sorting and positioning)
     *
     * @return LocalTime
     */
    public abstract LocalTime getTime();

    /**
     * Get unique ID for RecyclerView diffing
     *
     * @return Unique identifier
     */
    public abstract String getItemId();
}