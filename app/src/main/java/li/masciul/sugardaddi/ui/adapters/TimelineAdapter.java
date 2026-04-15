package li.masciul.sugardaddi.ui.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import li.masciul.sugardaddi.R;
import li.masciul.sugardaddi.core.enums.MealType;
import li.masciul.sugardaddi.core.models.Meal;
import li.masciul.sugardaddi.core.models.Nutrition;
import li.masciul.sugardaddi.ui.adapters.timeline.TimelineItem;
import li.masciul.sugardaddi.ui.adapters.timeline.MealTimelineItem;
import li.masciul.sugardaddi.ui.adapters.timeline.HourMarkerTimelineItem;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * TimelineAdapter - RecyclerView adapter for the 24-hour timeline
 *
 * Displays a mixed list of:
 * - Hour markers (00:00, 01:00, etc.)
 * - Actual meals at their specific times
 *
 * Features:
 * - Two view types (MEAL, HOUR_MARKER)
 * - Click listener for meals
 * - Efficient ViewHolder pattern
 * - Automatic sorting by time
 * - Read-only item access for scroll position lookups
 *
 * @version 1.1 - Added getItems() for scroll position calculations
 */
public class TimelineAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    // View types
    private static final int VIEW_TYPE_MEAL = 1;
    private static final int VIEW_TYPE_HOUR_MARKER = 2;

    // Data
    private List<TimelineItem> items = new ArrayList<>();
    private Context context;

    // Click listener
    private OnMealClickListener mealClickListener;

    // Time formatter
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    /**
     * Listener interface for meal clicks
     */
    public interface OnMealClickListener {
        void onMealClick(Meal meal);
    }

    // ========== CONSTRUCTOR ==========

    public TimelineAdapter(Context context) {
        this.context = context;
    }

    // ========== PUBLIC API ==========

    /**
     * Set timeline items (meals + hour markers)
     *
     * @param items List of timeline items (already sorted)
     */
    public void setItems(List<TimelineItem> items) {
        this.items = items != null ? items : new ArrayList<>();
        notifyDataSetChanged();
    }

    /**
     * Get the current timeline items (read-only view)
     *
     * Used by JournalActivity to find adapter positions for scroll calculations
     * without rebuilding the item list. Returns an unmodifiable list.
     *
     * @return Read-only list of current timeline items
     */
    public List<TimelineItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    /**
     * Set meal click listener
     *
     * @param listener Click listener
     */
    public void setOnMealClickListener(OnMealClickListener listener) {
        this.mealClickListener = listener;
    }

    // ========== RECYCLERVIEW METHODS ==========

    @Override
    public int getItemViewType(int position) {
        TimelineItem item = items.get(position);
        switch (item.getType()) {
            case MEAL:
                return VIEW_TYPE_MEAL;
            case HOUR_MARKER:
                return VIEW_TYPE_HOUR_MARKER;
            default:
                return VIEW_TYPE_HOUR_MARKER;
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(context);

        if (viewType == VIEW_TYPE_MEAL) {
            View view = inflater.inflate(R.layout.item_timeline_meal, parent, false);
            return new MealViewHolder(view);
        } else {
            View view = inflater.inflate(R.layout.item_timeline_hour_marker, parent, false);
            return new HourMarkerViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        TimelineItem item = items.get(position);

        if (holder instanceof MealViewHolder && item instanceof MealTimelineItem) {
            ((MealViewHolder) holder).bind((MealTimelineItem) item);
        } else if (holder instanceof HourMarkerViewHolder && item instanceof HourMarkerTimelineItem) {
            ((HourMarkerViewHolder) holder).bind((HourMarkerTimelineItem) item);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    // ========== VIEW HOLDERS ==========

    /**
     * ViewHolder for meal items
     */
    class MealViewHolder extends RecyclerView.ViewHolder {

        TextView mealTimeText;
        MaterialCardView mealCard;
        TextView mealNameText;
        TextView caloriesText;
        TextView carbsText;
        TextView proteinText;

        MealViewHolder(View itemView) {
            super(itemView);

            mealTimeText = itemView.findViewById(R.id.mealTimeText);
            mealCard = itemView.findViewById(R.id.mealCard);
            mealNameText = itemView.findViewById(R.id.mealNameText);
            caloriesText = itemView.findViewById(R.id.caloriesText);
            carbsText = itemView.findViewById(R.id.carbsText);
            proteinText = itemView.findViewById(R.id.proteinText);
        }

        void bind(MealTimelineItem item) {
            Meal meal = item.getMeal();
            if (meal == null) return;

            // Time
            if (meal.getStartTime() != null) {
                mealTimeText.setText(meal.getStartTime().format(TIME_FORMATTER));
            } else {
                mealTimeText.setText("--:--");
            }

            // Meal name/type - use localized string
            String displayText;
            if (meal.getName() != null && !meal.getName().isEmpty()) {
                displayText = meal.getName();
            } else if (meal.getMealType() != null) {
                displayText = getLocalizedMealType(meal.getMealType());
            } else {
                displayText = context.getString(R.string.meal_type_other);
            }
            mealNameText.setText(displayText);

            // Nutrition summary
            Nutrition nutrition = meal.getNutrition();
            if (nutrition != null) {
                // Calories
                Double kcal = nutrition.getEnergyKcal();
                if (kcal != null && kcal > 0) {
                    caloriesText.setText(String.format("%.0f %s", kcal, context.getString(R.string.unit_kcal)));
                } else {
                    caloriesText.setText("-- " + context.getString(R.string.unit_kcal));
                }

                // Carbs
                Double carbs = nutrition.getCarbohydrates();
                if (carbs != null && carbs > 0) {
                    carbsText.setText(context.getString(R.string.nutrient_carbs_format, String.format("%.0f", carbs)));
                } else {
                    carbsText.setText(context.getString(R.string.nutrient_carbs_empty));
                }

                // Protein
                Double protein = nutrition.getProteins();
                if (protein != null && protein > 0) {
                    proteinText.setText(context.getString(R.string.nutrient_protein_format, String.format("%.0f", protein)));
                } else {
                    proteinText.setText(context.getString(R.string.nutrient_protein_empty));
                }
            } else {
                caloriesText.setText("-- " + context.getString(R.string.unit_kcal));
                carbsText.setText(context.getString(R.string.nutrient_carbs_empty));
                proteinText.setText(context.getString(R.string.nutrient_protein_empty));
            }

            // Click listener
            itemView.setOnClickListener(v -> {
                if (mealClickListener != null) {
                    mealClickListener.onMealClick(meal);
                }
            });
        }

        /**
         * Get localized meal type name from string resources
         */
        private String getLocalizedMealType(MealType mealType) {
            int stringResId;
            switch (mealType) {
                case BREAKFAST:
                    stringResId = R.string.meal_type_breakfast;
                    break;
                case MORNING_SNACK:
                    stringResId = R.string.meal_type_morning_snack;
                    break;
                case LUNCH:
                    stringResId = R.string.meal_type_lunch;
                    break;
                case AFTERNOON_SNACK:
                    stringResId = R.string.meal_type_afternoon_snack;
                    break;
                case DINNER:
                    stringResId = R.string.meal_type_dinner;
                    break;
                case EVENING_SNACK:
                    stringResId = R.string.meal_type_evening_snack;
                    break;
                case SNACK:
                    stringResId = R.string.meal_type_snack;
                    break;
                case OTHER:
                default:
                    stringResId = R.string.meal_type_other;
                    break;
            }
            return context.getString(stringResId);
        }
    }

    /**
     * ViewHolder for hour markers
     */
    class HourMarkerViewHolder extends RecyclerView.ViewHolder {

        TextView hourText;

        HourMarkerViewHolder(View itemView) {
            super(itemView);
            hourText = itemView.findViewById(R.id.hourText);
        }

        void bind(HourMarkerTimelineItem item) {
            hourText.setText(item.getFormattedHour());
        }
    }
}