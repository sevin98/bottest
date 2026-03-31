package dpm18th.team3bot.ocr;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class OcrResponse {

    private boolean ok;
    private String text;
    private String language;
    private long processingMs;
    private int charCount;
    private int lineCount;
    private String error;
    /** OCR 텍스트에서 휴리스틱으로 뽑은 상품명·가격 */
    private ParsedPurchase extractedPurchase;

    public static OcrResponse success(String text, String language, long processingMs) {
        OcrResponse r = new OcrResponse();
        r.ok = true;
        r.text = text != null ? text : "";
        r.language = language;
        r.processingMs = processingMs;
        r.charCount = r.text.length();
        r.lineCount = r.text.isEmpty() ? 0 : r.text.split("\\R", -1).length;
        return r;
    }

    public static OcrResponse failure(String message) {
        OcrResponse r = new OcrResponse();
        r.ok = false;
        r.error = message;
        r.text = "";
        r.charCount = 0;
        r.lineCount = 0;
        return r;
    }

    public boolean isOk() {
        return ok;
    }

    public void setOk(boolean ok) {
        this.ok = ok;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public long getProcessingMs() {
        return processingMs;
    }

    public void setProcessingMs(long processingMs) {
        this.processingMs = processingMs;
    }

    public int getCharCount() {
        return charCount;
    }

    public void setCharCount(int charCount) {
        this.charCount = charCount;
    }

    public int getLineCount() {
        return lineCount;
    }

    public void setLineCount(int lineCount) {
        this.lineCount = lineCount;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public ParsedPurchase getExtractedPurchase() {
        return extractedPurchase;
    }

    public void setExtractedPurchase(ParsedPurchase extractedPurchase) {
        this.extractedPurchase = extractedPurchase;
    }
}
