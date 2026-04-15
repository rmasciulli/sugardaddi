package li.masciul.sugardaddi.ui.adapters.timeline;

import li.masciul.sugardaddi.core.models.Meal;

import java.time.LocalTime;

/**
 * MealTimelineItem - Represents an actual meal in the timeline
 *
 * Wraps a Meal object and provides timeline-specific functionality.
 * Used by TimelineAdapter to display meals at their correct positions
 * on the 24-hour timeline.
 *
 * @version 1.0
 */
public class MealTimelineItem extends TimelineItem {

    private final Meal meal;

    /**
     * Create a meal timeline item
     *
     * @param meal The meal to display
     */
    public MealTimelineItem(Meal meal) {
        this.meal = meal;
    }

    @Override
    public Type getType() {
        return Type.MEAL;
    }

    @Override
    public LocalTime getTime() {
        if (meal == null || meal.getStartTime() == null) {
            return LocalTime.now();
        }
        return meal.getStartTime().toLocalTime();
    }

    @Override
    public String getItemId() {
        return "meal_" + (meal != null ? meal.getId() : "unknown");
    }

    /**
     * Get the wrapped meal
     *
     * @return Meal object
     */
    public Meal getMeal() {
        return meal;
    }
}