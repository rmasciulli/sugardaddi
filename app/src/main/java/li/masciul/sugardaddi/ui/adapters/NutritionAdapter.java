package li.masciul.sugardaddi.ui.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import li.masciul.sugardaddi.R;
import li.masciul.sugardaddi.core.models.Nutrition;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * NutritionAdapter - RecyclerView adapter for nutrition facts display
 *
 * VERSION 2.0 - FULLY LOCALIZED
 *
 * Changes from v1.0:
 * - Replaced all hardcoded English strings with R.string references
 * - Now properly supports EN/FR translations via nutrients.xml
 * - Uses context.getString() for all nutrient names
 */
public class NutritionAdapter extends RecyclerView.Adapter<NutritionAdapter.NutritionViewHolder> {

    private List<NutritionItem> nutritionItems;
    private Context context;
    private double currentMultiplier = 1.0; // For calculating custom amounts

    public NutritionAdapter(Context context) {
        this.context = context;
        this.nutritionItems = new ArrayList<>();
    }

    @NonNull
    @Override
    public NutritionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_nutrition_fact, parent, false);
        return new NutritionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NutritionViewHolder holder, int position) {
        NutritionItem item = nutritionItems.get(position);
        holder.bind(item);
    }

    @Override
    public int getItemCount() {
        return nutritionItems.size();
    }

    /**
     * Update nutrition facts for 100g/ml portion
     * @param nutritionFacts Nutrition facts per 100g/ml
     */
    public void updateNutritionFacts(Nutrition nutritionFacts) {
        updateNutritionFacts(nutritionFacts, 1.0, context.getString(R.string.per_100g));
    }

    /**
     * Update nutrition facts with custom multiplier
     * @param nutritionFacts Base nutrition facts (per 100g/ml)
     * @param multiplier Multiplier for custom amount
     * @param portionDescription Description of the portion size
     */
    public void updateNutritionFacts(Nutrition nutritionFacts, double multiplier, String portionDescription) {
        this.currentMultiplier = multiplier;
        this.nutritionItems.clear();

        if (nutritionFacts == null) {
            notifyDataSetChanged();
            return;
        }

        // Add header item - NOW TRANSLATED
        nutritionItems.add(new NutritionItem(
                context.getString(R.string.nutrition_facts),  // ✅ "Nutrition Facts" / "Valeurs nutritionnelles"
                portionDescription,
                "",
                NutritionItem.Type.HEADER
        ));

        // 1. ENERGY (Calories) - NOW TRANSLATED
        if (nutritionFacts.getEnergyKcal() != null && nutritionFacts.getEnergyKcal() > 0) {
            double calories = nutritionFacts.getEnergyKcal() * multiplier;
            nutritionItems.add(new NutritionItem(
                    context.getString(R.string.nutrient_energy_kcal),  // ✅ "Energy (kcal)" / "Énergie (kcal)"
                    formatNumber(calories),
                    "kcal",
                    NutritionItem.Type.MAJOR_NUTRIENT
            ));
        }

        // 2. FAT - NOW TRANSLATED
        if (nutritionFacts.getFat() != null && nutritionFacts.getFat() > 0) {
            double fat = nutritionFacts.getFat() * multiplier;
            nutritionItems.add(new NutritionItem(
                    context.getString(R.string.nutrient_fat),  // ✅ "Fat" / "Lipides"
                    formatNumber(fat),
                    "g",
                    NutritionItem.Type.MAJOR_NUTRIENT
            ));

            // 2a. SATURATED FAT (sub-item) - NOW TRANSLATED
            if (nutritionFacts.getSaturatedFat() != null && nutritionFacts.getSaturatedFat() > 0) {
                double saturatedFat = nutritionFacts.getSaturatedFat() * multiplier;
                nutritionItems.add(new NutritionItem(
                        "  " + context.getString(R.string.nutrient_of_which_saturated),  // ✅ "of which saturated" / "dont saturés"
                        formatNumber(saturatedFat),
                        "g",
                        NutritionItem.Type.SUB_NUTRIENT
                ));
            }
        }

        // 3. CARBOHYDRATES - NOW TRANSLATED
        if (nutritionFacts.getCarbohydrates() != null && nutritionFacts.getCarbohydrates() > 0) {
            double carbs = nutritionFacts.getCarbohydrates() * multiplier;
            nutritionItems.add(new NutritionItem(
                    context.getString(R.string.nutrient_carbohydrates),  // ✅ "Carbohydrates" / "Glucides"
                    formatNumber(carbs),
                    "g",
                    NutritionItem.Type.MAJOR_NUTRIENT
            ));

            // 3a. SUGARS (sub-item of carbohydrates) - NOW TRANSLATED
            if (nutritionFacts.getSugars() != null && nutritionFacts.getSugars() > 0) {
                double sugars = nutritionFacts.getSugars() * multiplier;
                nutritionItems.add(new NutritionItem(
                        "  " + context.getString(R.string.nutrient_of_which_sugars),  // ✅ "of which sugars" / "dont sucres"
                        formatNumber(sugars),
                        "g",
                        NutritionItem.Type.SUB_NUTRIENT
                ));
            }
        }

        // 4. FIBER (separate major nutrient) - NOW TRANSLATED
        if (nutritionFacts.getFiber() != null && nutritionFacts.getFiber() > 0) {
            double fiber = nutritionFacts.getFiber() * multiplier;
            nutritionItems.add(new NutritionItem(
                    context.getString(R.string.nutrient_fiber),  // ✅ "Fiber" / "Fibres"
                    formatNumber(fiber),
                    "g",
                    NutritionItem.Type.MAJOR_NUTRIENT
            ));
        }

        // 5. PROTEIN - NOW TRANSLATED
        if (nutritionFacts.getProteins() != null && nutritionFacts.getProteins() > 0) {
            double protein = nutritionFacts.getProteins() * multiplier;
            nutritionItems.add(new NutritionItem(
                    context.getString(R.string.nutrient_proteins),  // ✅ "Protein" / "Protéines"
                    formatNumber(protein),
                    "g",
                    NutritionItem.Type.MAJOR_NUTRIENT
            ));
        }

        // 6. SALT - NOW TRANSLATED
        if (nutritionFacts.getSalt() != null && nutritionFacts.getSalt() > 0) {
            double salt = nutritionFacts.getSalt() * multiplier;
            nutritionItems.add(new NutritionItem(
                    context.getString(R.string.nutrient_salt),  // ✅ "Salt" / "Sel"
                    formatNumber(salt),
                    "g",
                    NutritionItem.Type.MAJOR_NUTRIENT
            ));
        }

        // Show message if no nutrition data is available - NOW TRANSLATED
        if (nutritionItems.size() == 1) { // Only header
            nutritionItems.add(new NutritionItem(
                    context.getString(R.string.nutrition_incomplete),  // ✅ "No nutrition information available"
                    "",
                    "",
                    NutritionItem.Type.MAJOR_NUTRIENT
            ));
        }

        notifyDataSetChanged();
    }

    /**
     * Format number to appropriate decimal places
     * @param value Number to format
     * @return Formatted string
     */
    private String formatNumber(double value) {
        if (value == 0) {
            return "0";
        } else if (value < 1) {
            return String.format(Locale.getDefault(), "%.2f", value);
        } else if (value < 10) {
            return String.format(Locale.getDefault(), "%.1f", value);
        } else {
            return String.format(Locale.getDefault(), "%.0f", value);
        }
    }

    /**
     * Clear all nutrition data
     */
    public void clearNutrition() {
        nutritionItems.clear();
        notifyDataSetChanged();
    }

    /**
     * Check if adapter has nutrition data
     * @return true if has data
     */
    public boolean hasNutritionData() {
        return !nutritionItems.isEmpty();
    }

    /**
     * ViewHolder for nutrition facts
     */
    public class NutritionViewHolder extends RecyclerView.ViewHolder {

        private TextView nutritionName;
        private TextView nutritionValue;
        private TextView nutritionUnit;
        private View divider;

        public NutritionViewHolder(@NonNull View itemView) {
            super(itemView);
            initViews();
        }

        private void initViews() {
            nutritionName = itemView.findViewById(R.id.nutritionName);
            nutritionValue = itemView.findViewById(R.id.nutritionValue);
            nutritionUnit = itemView.findViewById(R.id.nutritionUnit);
            divider = itemView.findViewById(R.id.divider);
        }

        public void bind(NutritionItem item) {
            nutritionName.setText(item.getName());
            nutritionValue.setText(item.getValue());
            nutritionUnit.setText(item.getUnit());

            // Style based on nutrition type
            switch (item.getType()) {
                case HEADER:
                    nutritionName.setTextSize(18f);
                    nutritionName.setTextColor(ContextCompat.getColor(context, R.color.text_primary));
                    nutritionValue.setTextSize(14f);
                    nutritionValue.setTextColor(ContextCompat.getColor(context, R.color.text_secondary));
                    nutritionUnit.setVisibility(View.GONE);
                    divider.setVisibility(View.VISIBLE);
                    itemView.setPadding(0, 16, 0, 8);
                    break;

                case MAJOR_NUTRIENT:
                    nutritionName.setTextSize(16f);
                    nutritionName.setTextColor(ContextCompat.getColor(context, R.color.text_primary));
                    nutritionValue.setTextSize(16f);
                    nutritionValue.setTextColor(ContextCompat.getColor(context, R.color.text_primary));
                    nutritionUnit.setVisibility(View.VISIBLE);
                    nutritionUnit.setTextSize(14f);
                    divider.setVisibility(View.GONE);
                    itemView.setPadding(0, 12, 0, 12);
                    break;

                case SUB_NUTRIENT:
                    nutritionName.setTextSize(14f);
                    nutritionName.setTextColor(ContextCompat.getColor(context, R.color.text_secondary));
                    nutritionValue.setTextSize(14f);
                    nutritionValue.setTextColor(ContextCompat.getColor(context, R.color.text_secondary));
                    nutritionUnit.setVisibility(View.VISIBLE);
                    nutritionUnit.setTextSize(12f);
                    divider.setVisibility(View.GONE);
                    itemView.setPaddingRelative(32, 8, 0, 8);
                    break;
            }
        }
    }

    /**
     * Data class for nutrition items
     */
    public static class NutritionItem {
        private String name;
        private String value;
        private String unit;
        private Type type;

        public enum Type {
            HEADER,
            MAJOR_NUTRIENT,
            SUB_NUTRIENT
        }

        public NutritionItem(String name, String value, String unit, Type type) {
            this.name = name;
            this.value = value;
            this.unit = unit;
            this.type = type;
        }

        // Getters
        public String getName() { return name; }
        public String getValue() { return value; }
        public String getUnit() { return unit; }
        public Type getType() { return type; }

        // Setters
        public void setName(String name) { this.name = name; }
        public void setValue(String value) { this.value = value; }
        public void setUnit(String unit) { this.unit = unit; }
        public void setType(Type type) { this.type = type; }
    }
}