package dpm18th.team3bot.gemini;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gemini")
public class GeminiProperties {

    /**
     * .env 의 GEMINI_API_KEY 를 그대로 쓰도록 application.yml 에서 바인딩합니다.
     * 비어 있으면 Gemini 보정 로직은 동작하지 않습니다.
     */
    private String apiKey;

    /** Google Generative Language API model name (e.g. gemini-2.0-flash). */
    private String model = "gemini-2.0-flash";

    /** Request timeout (ms). */
    private int timeoutMs = 4000;

    /**
     * 이 값 이상 confidence일 때만 productName을 대체 적용합니다.
     * (낮으면 outputTitle은 기록하되 applied=false)
     */
    private double minConfidence = 0.50;

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public double getMinConfidence() {
        return minConfidence;
    }

    public void setMinConfidence(double minConfidence) {
        this.minConfidence = minConfidence;
    }
}

