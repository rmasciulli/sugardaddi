package li.masciul.sugardaddi.ui.adapters;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import li.masciul.sugardaddi.R;
import li.masciul.sugardaddi.core.models.Category;
import li.masciul.sugardaddi.core.models.FoodPortion;
import li.masciul.sugardaddi.core.models.FoodProduct;
import li.masciul.sugardaddi.core.models.Nutrition;
import li.masciul.sugardaddi.core.models.ServingSize;

/**
 * MealPortionsAdapter - Redesigned with expandable cards
 *
 * CORRECTED VERSION using actual API methods:
 * - nutrition.getProteins() not getProtein()
 * - nutrition.getCarbohydrates() not getCarbohydrate()
 * - portion.getServing() not getServingSize()
 * - product.getCategoryList() for category display
 */
public class MealPortionsAdapter extends RecyclerView.Adapter<MealPortionsAdapter.PortionViewHolder> {

    private final Context context;
    private List<FoodPortion> portions = new ArrayList<>();
    private final PortionInteractionListener listener;
    private final Set<Integer> expandedPositions = new HashSet<>();
    private boolean editMode = false;

    public interface PortionInteractionListener {
        void onPortionClicked(FoodPortion portion);
        void onPortionQuantityChanged(FoodPortion portion, double newQuantity);
        void onPortionDeleted(FoodPortion portion);
    }

    public MealPortionsAdapter(Context context, PortionInteractionListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setPortions(List<FoodPortion> portions) {
        this.portions = portions != null ? portions : new ArrayList<>();
        notifyDataSetChanged();
    }

    public void setEditMode(boolean editMode) {
        this.editMode = editMode;
        notifyDataSetChanged();
    }

    public FoodPortion getPortionAt(int position) {
        if (position >= 0 && position < portions.size()) {
            return portions.get(position);
        }
        return null;
    }

    @NonNull
    @Override
    public PortionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context)
                .inflate(R.layout.item_meal_portion, parent, false);
        return new PortionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PortionViewHolder holder, int position) {
        FoodPortion portion = portions.get(position);
        holder.bind(portion, position);
    }

    @Override
    public int getItemCount() {
        return portions.size();
    }

    class PortionViewHolder extends RecyclerView.ViewHolder {

        private final MaterialCardView portionCard;
        private final ImageView productImage;
        private final TextView productName;
        private final TextView productCategory;
        private final TextView portionSize;
        private final TextView nutritionPreview;
        private final ImageView expandIndicator;
        private final ViewGroup expandedLayout;

        // Expanded detail views
        private final TextView detailEnergy;
        private final TextView detailCarbs;
        private final TextView detailSugars;
        private final TextView detailProteins;
        private final TextView detailFats;
        private final TextView detailFibers;

        PortionViewHolder(@NonNull View itemView) {
            super(itemView);

            portionCard = itemView.findViewById(R.id.portionCard);
            productImage = itemView.findViewById(R.id.productImage);
            productName = itemView.findViewById(R.id.productName);
            productCategory = itemView.findViewById(R.id.productCategory);
            portionSize = itemView.findViewById(R.id.portionSize);
            nutritionPreview = itemView.findViewById(R.id.nutritionPreview);
            expandIndicator = itemView.findViewById(R.id.expandIndicator);
            expandedLayout = itemView.findViewById(R.id.expandedLayout);

            // Detail views
            detailEnergy = itemView.findViewById(R.id.detailEnergy);
            detailCarbs = itemView.findViewById(R.id.detailCarbs);
            detailSugars = itemView.findViewById(R.id.detailSugars);
            detailProteins = itemView.findViewById(R.id.detailProteins);
            detailFats = itemView.findViewById(R.id.detailFats);
            detailFibers = itemView.findViewById(R.id.detailFibers);
        }

        void bind(FoodPortion portion, int position) {
            FoodProduct product = portion.getFoodProduct();

            if (product == null) {
                // Fallback if product not loaded
                String fallbackName = portion.getItemId();
                productName.setText(fallbackName);
                portionSize.setText(formatPortion(portion));
                productImage.setImageResource(R.drawable.ic_food_placeholder);
                productCategory.setVisibility(View.GONE);
                nutritionPreview.setText(R.string.no_nutrition_data);
                return;
            }

            // Product name
            String productNameText = product.getName();
            productName.setText(productNameText);

            // Category - use first from category list
            List<Category> categories = product.getCategoryList();
            if (categories != null && !categories.isEmpty()) {
                Category firstCategory = categories.get(0);
                String categoryName = firstCategory.getName();  // Corrected: getName() not getLocalizedName()
                if (categoryName != null && !categoryName.isEmpty()) {
                    productCategory.setVisibility(View.VISIBLE);
                    productCategory.setText(categoryName);
                } else {
                    productCategory.setVisibility(View.GONE);
                }
            } else {
                productCategory.setVisibility(View.GONE);
            }

            // Portion size
            portionSize.setText(formatPortion(portion));

            // Load product image
            String imageUrl = product.getImageUrl();
            if (imageUrl != null && !imageUrl.isEmpty()) {
                Glide.with(context)
                        .load(imageUrl)
                        .placeholder(R.drawable.ic_food_placeholder)
                        .error(R.drawable.ic_food_error)
                        .centerCrop()
                        .into(productImage);
            } else {
                productImage.setImageResource(R.drawable.ic_food_placeholder);
            }

            // Nutrition preview (compact)
            Nutrition nutrition = portion.calculateNutrition();
            if (nutrition != null && nutrition.hasData()) {
                nutritionPreview.setText(formatNutritionPreview(nutrition));

                // Detailed nutrition for expanded view
                updateDetailedNutrition(nutrition);
            } else {
                nutritionPreview.setText(R.string.no_nutrition_data);
            }

            // Expanded state
            boolean isExpanded = expandedPositions.contains(position);
            expandedLayout.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
            expandIndicator.setRotation(isExpanded ? 180f : 0f);

            // Card click - Toggle expand/collapse
            portionCard.setOnClickListener(v -> {
                toggleExpansion(position);
            });

            // Image click - Open ItemDetailsActivity
            productImage.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onPortionClicked(portion);
                }
            });
        }

        private void toggleExpansion(int position) {
            boolean wasExpanded = expandedPositions.contains(position);

            if (wasExpanded) {
                expandedPositions.remove(position);

                // Collapse animation
                collapseView(expandedLayout);
                rotateIndicator(expandIndicator, 180f, 0f);

                productName.setHorizontallyScrolling(false);
            } else {
                expandedPositions.add(position);

                // Expand animation
                expandView(expandedLayout);
                rotateIndicator(expandIndicator, 0f, 180f);

                productName.setHorizontallyScrolling(true);
                productName.setSelected(true);
                productName.requestFocus();
            }
        }

        private void expandView(View view) {
            view.setVisibility(View.VISIBLE);
            view.setAlpha(0f);
            view.animate()
                    .alpha(1f)
                    .setDuration(200)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }

        private void collapseView(View view) {
            view.animate()
                    .alpha(0f)
                    .setDuration(150)
                    .setInterpolator(new DecelerateInterpolator())
                    .withEndAction(() -> view.setVisibility(View.GONE))
                    .start();
        }

        private void rotateIndicator(ImageView indicator, float from, float to) {
            ObjectAnimator.ofFloat(indicator, "rotation", from, to)
                    .setDuration(200)
                    .start();
        }

        private void updateDetailedNutrition(Nutrition nutrition) {
            // Energy (kcal)
            double kcal = nutrition.getEnergyKcal();
            detailEnergy.setText(String.format(Locale.getDefault(), "%.0f kcal", kcal));

            // Carbohydrates (note: getCarbohydrates not getCarbohydrate)
            Double carbs = nutrition.getCarbohydrates();
            detailCarbs.setText(carbs != null ?
                    String.format(Locale.getDefault(), "%.1f g", carbs) : "-");

            // Sugars
            Double sugars = nutrition.getSugars();
            detailSugars.setText(sugars != null ?
                    String.format(Locale.getDefault(), "%.1f g", sugars) : "-");

            // Proteins (note: getProteins not getProtein)
            Double proteins = nutrition.getProteins();
            detailProteins.setText(proteins != null ?
                    String.format(Locale.getDefault(), "%.1f g", proteins) : "-");

            // Fat
            Double fats = nutrition.getFat();
            detailFats.setText(fats != null ?
                    String.format(Locale.getDefault(), "%.1f g", fats) : "-");

            // Fiber
            Double fibers = nutrition.getFiber();
            detailFibers.setText(fibers != null ?
                    String.format(Locale.getDefault(), "%.1f g", fibers) : "-");
        }

        private String formatNutritionPreview(Nutrition nutrition) {
            // Format: "213 kcal • P: 2.3g • C: 24.2g • F: 11.6g"
            double kcal = nutrition.getEnergyKcal();
            Double proteins = nutrition.getProteins();  // Corrected
            Double carbs = nutrition.getCarbohydrates();  // Corrected
            Double fat = nutrition.getFat();

            StringBuilder preview = new StringBuilder();
            preview.append(String.format(Locale.getDefault(), "%.0f kcal", kcal));

            if (proteins != null) {
                preview.append(String.format(Locale.getDefault(), " • P: %.1fg", proteins));
            }
            if (carbs != null) {
                preview.append(String.format(Locale.getDefault(), " • C: %.1fg", carbs));
            }
            if (fat != null) {
                preview.append(String.format(Locale.getDefault(), " • F: %.1fg", fat));
            }

            return preview.toString();
        }

        private String formatPortion(FoodPortion portion) {
            // Note: getServing() not getServingSize()
            ServingSize serving = portion.getServing();
            if (serving != null) {
                Double grams = serving.getAsGrams();
                if (grams != null) {
                    return String.format(Locale.getDefault(), "%.0f g", grams);
                }
                return serving.toString();
            }
            return "";
        }
    }
}