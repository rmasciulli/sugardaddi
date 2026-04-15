package li.masciul.sugardaddi.data.sources.openfoodfacts.api.dto;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * OpenFoodFactsSearchResponse - OpenFoodFacts API search response
 *
 * This is what we receive from the OFF API when searching products
 */
public class OpenFoodFactsSearchResponse {

    @SerializedName("count")
    private int count;

    @SerializedName("page")
    private int page;

    @SerializedName("page_size")
    private int pageSize;

    @SerializedName("page_count")
    private int pageCount;

    @SerializedName("products")
    private List<OpenFoodFactsProduct> products;

    // Constructor
    public OpenFoodFactsSearchResponse() {}

    // Getters
    public int getCount() { return count; }
    public void setCount(int count) { this.count = count; }

    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }

    public int getPageSize() { return pageSize; }
    public void setPageSize(int pageSize) { this.pageSize = pageSize; }

    public int getPageCount() { return pageCount; }
    public void setPageCount(int pageCount) { this.pageCount = pageCount; }

    public List<OpenFoodFactsProduct> getProducts() { return products; }
    public void setProducts(List<OpenFoodFactsProduct> products) { this.products = products; }

    // Helper methods
    public boolean hasResults() {
        return products != null && !products.isEmpty();
    }

    public boolean hasMorePages() {
        return page < pageCount;
    }

    public int getResultCount() {
        return products != null ? products.size() : 0;
    }
}