package li.masciul.sugardaddi.data.sources.usda.api;

import java.util.List;

import li.masciul.sugardaddi.data.sources.usda.api.dto.FDCFoodDetail;
import li.masciul.sugardaddi.data.sources.usda.api.dto.FDCSearchResponse;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

/**
 * FoodDataCentralAPI — Retrofit interface for the USDA FoodData Central REST API.
 *
 * BASE URL: https://api.nal.usda.gov/fdc/v1/
 * DOCS:     https://fdc.nal.usda.gov/api-spec/fdc_api.html
 *
 * AUTHENTICATION:
 * All requests require an api_key query parameter. DEMO_KEY works but is
 * rate-limited (30 req/hour per IP). Register for a free key at:
 * https://fdc.nal.usda.gov/api-key-signup/
 * The key is injected at the USDADataSource level, not hardcoded here.
 *
 * TWO ENDPOINTS
 * =============
 * 1. GET /foods/search  — full-text search returning FDCSearchResponse (food list)
 * 2. GET /food/{fdcId}  — detail fetch returning FDCFoodDetail (single food)
 *
 * DATA TYPES (dataType parameter):
 * - "Foundation"      — ~1,200 raw agricultural commodities, highest quality
 * - "SR Legacy"       — ~7,700 foods, classic USDA Standard Reference
 * - "Survey (FNDDS)"  — ~7,300 dietary survey foods (composite dishes)
 * - "Branded"         — ~400,000 consumer products (excluded — OFF handles this)
 *
 * We pass USDAConstants.API_DATA_TYPES = "Foundation,SR Legacy,Survey (FNDDS)"
 * on all search requests.
 */
public interface FoodDataCentralAPI {

    // =========================================================================
    // SEARCH ENDPOINT
    // =========================================================================

    /**
     * Full-text food search.
     *
     * EXAMPLE REQUEST:
     *   GET /foods/search?api_key=DEMO_KEY&query=broccoli
     *       &dataType=Foundation,SR Legacy,Survey (FNDDS)
     *       &pageSize=25&pageNumber=1
     *       &sortBy=score&sortOrder=desc
     *
     * EXAMPLE RESPONSE (abbreviated):
     * {
     *   "totalHits": 142,
     *   "currentPage": 1,
     *   "totalPages": 6,
     *   "pageList": [1,2,3,4,5,6],
     *   "foodSearchCriteria": { ... },
     *   "foods": [
     *     {
     *       "fdcId": 747447,
     *       "description": "Broccoli, raw",
     *       "dataType": "Foundation",
     *       "foodCategory": "Vegetables and Vegetable Products",
     *       "publishedDate": "2019-04-01",
     *       "score": 934.8,
     *       "foodNutrients": [
     *         { "nutrientId": 1008, "nutrientName": "Energy",
     *           "nutrientNumber": "208", "unitName": "KCAL", "value": 34 },
     *         ...
     *       ]
     *     }
     *   ]
     * }
     *
     * NUTRIENT DATA IN SEARCH RESULTS:
     * The search endpoint returns a subset of nutrient data per food — enough
     * for list display (energy, protein, fat, carbs). For full nutrient profiles,
     * use getFood().
     *
     * @param query       Full-text search term (required)
     * @param apiKey      FDC API key (use BuildConfig.USDA_API_KEY)
     * @param dataType    Comma-separated data type filter (use USDAConstants.API_DATA_TYPES)
     * @param pageSize    Results per page (1–200, default 25)
     * @param pageNumber  Page number, 1-based
     * @param sortBy      Sort field: "score", "description", "dataType", "publishedDate", "fdcId"
     * @param sortOrder   Sort direction: "asc" or "desc"
     * @return Call wrapping FDCSearchResponse
     */
    @GET("foods/search")
    Call<FDCSearchResponse> searchFoods(
            @Query("query")      String query,
            @Query("api_key")    String apiKey,
            @Query("dataType")   List<String> dataType,
            @Query("pageSize")   int pageSize,
            @Query("pageNumber") int pageNumber,
            @Query("sortBy")     String sortBy,
            @Query("sortOrder")  String sortOrder
    );

    // =========================================================================
    // FOOD DETAIL ENDPOINT
    // =========================================================================

    /**
     * Retrieve full details for a single food by its FDC ID.
     *
     * EXAMPLE REQUEST:
     *   GET /food/747447?api_key=DEMO_KEY&format=full
     *
     * EXAMPLE RESPONSE (abbreviated):
     * {
     *   "fdcId": 747447,
     *   "description": "Broccoli, raw",
     *   "dataType": "Foundation",
     *   "publicationDate": "2019-04-01",
     *   "foodCategory": { "description": "Vegetables and Vegetable Products" },
     *   "foodNutrients": [
     *     {
     *       "nutrient": { "id": 1008, "name": "Energy", "number": "208", "unitName": "kcal" },
     *       "amount": 34.0
     *     },
     *     {
     *       "nutrient": { "id": 1003, "name": "Protein", "number": "203", "unitName": "g" },
     *       "amount": 2.82
     *     },
     *     ...
     *   ]
     * }
     *
     * FORMAT PARAMETER:
     * "full"   — all nutrient data (default, what we want)
     * "abridged" — key nutrients only
     *
     * @param fdcId  The FoodData Central integer ID (e.g. 747447)
     * @param apiKey FDC API key
     * @param format Response format: "full" or "abridged"
     * @return Call wrapping FDCFoodDetail
     */
    @GET("food/{fdcId}")
    Call<FDCFoodDetail> getFood(
            @Path("fdcId")   int fdcId,
            @Query("api_key") String apiKey,
            @Query("format")  String format
    );
}