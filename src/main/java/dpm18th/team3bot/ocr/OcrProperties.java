package dpm18th.team3bot.ocr;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ocr")
public class OcrProperties {

    /**
     * false면 OCR 빈을 등록하지 않습니다(테스트/CI 등).
     */
    private boolean enabled = true;

    /**
     * tessdata 디렉터리 (eng.traineddata, kor.traineddata 등).
     * 비우면 현재 작업 디렉터리 기준 ./tessdata 를 사용합니다.
     */
    private String dataPath = "";

    /**
     * Tesseract 언어 코드. 한글 우선 예: kor+eng
     */
    private String language = "kor+eng";

    /**
     * true면 ./tessdata(또는 data-path)에 *.traineddata 가 없을 때 GitHub에서 받습니다.
     * IDE 실행만 할 때도 기동되게 하려면 true 권장.
     */
    private boolean autoDownloadTrainedData = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getDataPath() {
        return dataPath;
    }

    public void setDataPath(String dataPath) {
        this.dataPath = dataPath;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public boolean isAutoDownloadTrainedData() {
        return autoDownloadTrainedData;
    }

    public void setAutoDownloadTrainedData(boolean autoDownloadTrainedData) {
        this.autoDownloadTrainedData = autoDownloadTrainedData;
    }
}
