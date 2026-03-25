package dpm18th.team3bot.notion;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * 노션 페이지 본문(블록)을 평문으로 수집
 */
@Service
public class NotionPageContentService {

    private static final String NOTION_VERSION = "2022-06-28";

    private final WebClient webClient;

    public NotionPageContentService(
            WebClient.Builder webClientBuilder,
            @Value("${notion.api-token:}") String apiToken) {
        this.webClient = webClientBuilder
                .baseUrl("https://api.notion.com/v1")
                .defaultHeader("Authorization", "Bearer " + apiToken)
                .defaultHeader("Notion-Version", NOTION_VERSION)
                .build();
    }

    public String fetchPlainTextBlocking(String pageId) {
        StringBuilder sb = new StringBuilder();
        appendBlocks(pageId, null, sb);
        return sb.toString().trim();
    }

    private void appendBlocks(String blockId, String startCursor, StringBuilder sb) {
        String path = "/blocks/{id}/children?page_size=100";
        if (startCursor != null && !startCursor.isBlank()) {
            path += "&start_cursor=" + startCursor;
        }
        JsonNode body = webClient.get()
                .uri(path, blockId)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();
        if (body == null) return;

        for (JsonNode block : body.path("results")) {
            extractText(block).ifPresent(line -> {
                if (sb.length() > 0) sb.append('\n');
                sb.append(line);
            });
            if (block.path("has_children").asBoolean(false)) {
                String childId = block.path("id").asText();
                if (!childId.isBlank()) {
                    appendBlocks(childId, null, sb);
                }
            }
        }
        if (body.path("has_more").asBoolean(false)) {
            String next = body.path("next_cursor").asText(null);
            appendBlocks(blockId, next, sb);
        }
    }

    private java.util.Optional<String> extractText(JsonNode block) {
        String type = block.path("type").asText("");
        JsonNode obj = block.path(type);
        if (obj.isMissingNode()) return java.util.Optional.empty();
        JsonNode rich = obj.path("rich_text");
        if (!rich.isArray() || rich.isEmpty()) {
            if ("child_page".equals(type)) {
                String t = obj.path("title").asText(null);
                return t == null || t.isBlank() ? java.util.Optional.empty() : java.util.Optional.of(t);
            }
            return java.util.Optional.empty();
        }
        StringBuilder sb = new StringBuilder();
        for (JsonNode rt : rich) {
            sb.append(rt.path("plain_text").asText(""));
        }
        String line = sb.toString().trim();
        return line.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(line);
    }

    public Mono<String> fetchPlainText(String pageId) {
        return Mono.fromCallable(() -> fetchPlainTextBlocking(pageId));
    }
}
