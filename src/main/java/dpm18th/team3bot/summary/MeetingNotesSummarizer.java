package dpm18th.team3bot.summary;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * OpenAI 키가 있으면 요약, 없으면 본문 앞부분만 잘라서 반환
 */
@Service
public class MeetingNotesSummarizer {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_INPUT = 12_000;

    private final WebClient openAiClient;
    private final String apiKey;
    private final String model;

    public MeetingNotesSummarizer(
            @Value("${openai.api-key:}") String apiKey,
            @Value("${openai.model:gpt-4o-mini}") String model) {
        this.apiKey = apiKey != null ? apiKey : "";
        this.model = model;
        this.openAiClient = WebClient.builder()
                .baseUrl("https://api.openai.com/v1")
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public String summarize(String rawNotes) {
        if (rawNotes == null || rawNotes.isBlank()) {
            return "(노션 페이지에 텍스트 블록이 거의 없습니다.)";
        }
        String trimmed = rawNotes.length() > MAX_INPUT ? rawNotes.substring(0, MAX_INPUT) + "\n…(이하 생략)" : rawNotes;
        if (apiKey.isBlank()) {
            return "*(OpenAI 키 없음 — 원문 앞부분만 표시)*\n```\n"
                    + trimmed.substring(0, Math.min(1500, trimmed.length()))
                    + (trimmed.length() > 1500 ? "\n…" : "")
                    + "\n```";
        }
        try {
            ObjectNode body = MAPPER.createObjectNode();
            body.put("model", model);
            ArrayNode messages = MAPPER.createArrayNode();
            ObjectNode user = MAPPER.createObjectNode();
            user.put("role", "user");
            user.put("content",
                    "다음은 팀 회의록 초안이다. 슬랙 채널에 올릴 짧은 한국어 요약을 작성해라.\n"
                            + "- 불릿 3~7개, 각 줄은 한 문장 이내\n"
                            + "- 결정 사항·액션 아이템이 있으면 반드시 포함\n"
                            + "- 말투는 ~함, ~임 같은 간결한 보고체\n\n"
                            + "---\n"
                            + trimmed);
            messages.add(user);
            body.set("messages", messages);
            body.put("max_tokens", 700);
            body.put("temperature", 0.3);

            JsonNode res = openAiClient.post()
                    .uri("/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (res == null) return fallback(trimmed);
            String content = res.path("choices").path(0).path("message").path("content").asText("");
            return content.isBlank() ? fallback(trimmed) : content.trim();
        } catch (Exception e) {
            return fallback(trimmed) + "\n_(요약 API 오류: " + e.getMessage() + ")_";
        }
    }

    private String fallback(String trimmed) {
        return trimmed.substring(0, Math.min(1200, trimmed.length())) + (trimmed.length() > 1200 ? "\n…" : "");
    }
}
