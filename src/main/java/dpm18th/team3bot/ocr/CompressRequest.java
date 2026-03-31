package dpm18th.team3bot.ocr;

import java.util.List;

public class CompressRequest {

    private Long priceWon;
    private String productName;
    /**
     * {@code GET /ocr/api/units} 에서 받은 id 목록. 비우거나 생략하면 전체 기준으로 환산.
     */
    private List<String> selectedUnitIds;

    public Long getPriceWon() {
        return priceWon;
    }

    public void setPriceWon(Long priceWon) {
        this.priceWon = priceWon;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public List<String> getSelectedUnitIds() {
        return selectedUnitIds;
    }

    public void setSelectedUnitIds(List<String> selectedUnitIds) {
        this.selectedUnitIds = selectedUnitIds;
    }
}
