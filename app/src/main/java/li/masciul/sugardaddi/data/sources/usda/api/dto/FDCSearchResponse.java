package li.masciul.sugardaddi.data.sources.usda.api.dto;

import androidx.annotation.Nullable;
import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;

/**
 * FDCSearchResponse — Response from GET /foods/search
 *
 * RESPONSE STRUCTURE:
 * {
 *   "totalHits":   142,
 *   "currentPage": 1,
 *   "totalPages":  6,
 *   "foods": [ FDCSearchFood, ... ]
 * }
 */
public class FDCSearchResponse {

    @SerializedName("totalHits")
    private int totalHits;

    @SerializedName("currentPage")
    private int currentPage;

    @SerializedName("totalPages")
    private int totalPages;

    @SerializedName("foods")
    @Nullable
    private List<FDCSearchFood> foods;

    // ===== CONSTRUCTORS =====

    public FDCSearchResponse() {
        this.foods = new ArrayList<>();
    }

    // ===== ACCESSORS =====

    public int getTotalHits()   { return totalHits; }
    public int getCurrentPage() { return currentPage; }
    public int getTotalPages()  { return totalPages; }

    public List<FDCSearchFood> getFoods() {
        return foods != null ? foods : new ArrayList<>();
    }

    public boolean hasResults() {
        return foods != null && !foods.isEmpty();
    }

    public boolean hasMorePages() {
        return currentPage < totalPages;
    }

    @Override
    public String toString() {
        return "FDCSearchResponse{hits=" + totalHits
                + ", page=" + currentPage + "/" + totalPages + "}";
    }
}