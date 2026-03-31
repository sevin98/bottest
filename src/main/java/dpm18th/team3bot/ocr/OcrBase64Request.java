package dpm18th.team3bot.ocr;

public class OcrBase64Request {

    /**
     * data:image/png;base64,... 형태 또는 순수 base64 문자열
     */
    private String imageBase64;

    public String getImageBase64() {
        return imageBase64;
    }

    public void setImageBase64(String imageBase64) {
        this.imageBase64 = imageBase64;
    }
}
