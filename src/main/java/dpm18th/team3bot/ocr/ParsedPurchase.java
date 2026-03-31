package dpm18th.team3bot.ocr;

import com.fasterxml.jackson.annotation.JsonInclude;
import dpm18th.team3bot.gemini.GeminiRefineResult;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ParsedPurchase {

    private String productName;
    /** LLM 보정 전(휴리스틱) 상품명 */
    private String productNameRaw;
    private Long priceWon;
    /** 추출 실패·불확실 시 짧은 안내 */
    private String hint;

    /** Gemini 보정 메타데이터 (표시용) */
    private GeminiRefineResult gemini;

    public static ParsedPurchase empty(String hint) {
        ParsedPurchase p = new ParsedPurchase();
        p.hint = hint;
        return p;
    }

    public static ParsedPurchase of(String productName, Long priceWon) {
        ParsedPurchase p = new ParsedPurchase();
        p.productName = productName;
        p.priceWon = priceWon;
        if (productName == null && priceWon == null) {
            p.hint = "상품명·가격을 텍스트에서 찾지 못했습니다. 아래에 금액을 직접 입력한 뒤 환산해 보세요.";
        }
        return p;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getProductNameRaw() {
        return productNameRaw;
    }

    public void setProductNameRaw(String productNameRaw) {
        this.productNameRaw = productNameRaw;
    }

    public Long getPriceWon() {
        return priceWon;
    }

    public void setPriceWon(Long priceWon) {
        this.priceWon = priceWon;
    }

    public String getHint() {
        return hint;
    }

    public void setHint(String hint) {
        this.hint = hint;
    }

    public GeminiRefineResult getGemini() {
        return gemini;
    }

    public void setGemini(GeminiRefineResult gemini) {
        this.gemini = gemini;
    }
}
