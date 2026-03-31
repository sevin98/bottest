package dpm18th.team3bot.gemini;

/**
 * Gemini로 제품 타이틀을 보정한 결과(웹/JSON에 표시용).
 */
public class GeminiRefineResult {

    private boolean enabled;
    private String model;
    private String inputTitle;
    private Long inputPriceWon;
    /** OCR 원문 일부(너무 길면 앞부분만) */
    private String inputTextPreview;

    private String outputTitle;
    /** Gemini가 추출한 가격(원). 0이면 미추출. */
    private Long outputPriceWon;
    private Double confidence;
    /** outputTitle이 실제로 productName을 대체했는지 */
    private boolean applied;
    /** 실패/스킵 사유(짧게) */
    private String note;
    /** Gemini HTTP 응답/에러 바디 미리보기(디버깅용) */
    private String responsePreview;
    /** Gemini 요청 바디 미리보기(민감정보 제외) */
    private String requestPreview;

    public static GeminiRefineResult disabled() {
        GeminiRefineResult r = new GeminiRefineResult();
        r.enabled = false;
        r.note = "disabled";
        return r;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getInputTitle() {
        return inputTitle;
    }

    public void setInputTitle(String inputTitle) {
        this.inputTitle = inputTitle;
    }

    public Long getInputPriceWon() {
        return inputPriceWon;
    }

    public void setInputPriceWon(Long inputPriceWon) {
        this.inputPriceWon = inputPriceWon;
    }

    public String getInputTextPreview() {
        return inputTextPreview;
    }

    public void setInputTextPreview(String inputTextPreview) {
        this.inputTextPreview = inputTextPreview;
    }

    public String getOutputTitle() {
        return outputTitle;
    }

    public void setOutputTitle(String outputTitle) {
        this.outputTitle = outputTitle;
    }

    public Long getOutputPriceWon() {
        return outputPriceWon;
    }

    public void setOutputPriceWon(Long outputPriceWon) {
        this.outputPriceWon = outputPriceWon;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public boolean isApplied() {
        return applied;
    }

    public void setApplied(boolean applied) {
        this.applied = applied;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getResponsePreview() {
        return responsePreview;
    }

    public void setResponsePreview(String responsePreview) {
        this.responsePreview = responsePreview;
    }

    public String getRequestPreview() {
        return requestPreview;
    }

    public void setRequestPreview(String requestPreview) {
        this.requestPreview = requestPreview;
    }
}

