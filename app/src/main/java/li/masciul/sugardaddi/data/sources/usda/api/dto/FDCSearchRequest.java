package li.masciul.sugardaddi.data.sources.usda.api.dto;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class FDCSearchRequest {

    @SerializedName("query")
    private final String query;

    @SerializedName("dataType")
    private final List<String> dataType;

    @SerializedName("pageSize")
    private final int pageSize;

    @SerializedName("pageNumber")
    private final int pageNumber;

    @SerializedName("sortBy")
    private final String sortBy;

    @SerializedName("sortOrder")
    private final String sortOrder;

    public FDCSearchRequest(String query, List<String> dataType,
                            int pageSize, int pageNumber,
                            String sortBy, String sortOrder) {
        this.query      = query;
        this.dataType   = dataType;
        this.pageSize   = pageSize;
        this.pageNumber = pageNumber;
        this.sortBy     = sortBy;
        this.sortOrder  = sortOrder;
    }
}