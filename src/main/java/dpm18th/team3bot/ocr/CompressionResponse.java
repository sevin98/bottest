package dpm18th.team3bot.ocr;

import java.util.ArrayList;
import java.util.List;

public class CompressionResponse {

    private boolean ok = true;
    private long priceWon;
    private String productName;
    private List<ComparisonRow> comparisons = new ArrayList<>();
    private String error;

    public static CompressionResponse failure(String message) {
        CompressionResponse r = new CompressionResponse();
        r.ok = false;
        r.error = message;
        return r;
    }

    public boolean isOk() {
        return ok;
    }

    public void setOk(boolean ok) {
        this.ok = ok;
    }

    public long getPriceWon() {
        return priceWon;
    }

    public void setPriceWon(long priceWon) {
        this.priceWon = priceWon;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public List<ComparisonRow> getComparisons() {
        return comparisons;
    }

    public void setComparisons(List<ComparisonRow> comparisons) {
        this.comparisons = comparisons != null ? comparisons : new ArrayList<>();
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}
