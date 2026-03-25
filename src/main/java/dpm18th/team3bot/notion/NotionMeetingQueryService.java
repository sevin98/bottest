package dpm18th.team3bot.notion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.ClientResponse;
import reactor.core.publisher.Mono;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.annotation.PostConstruct;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class NotionMeetingQueryService {

    private static final Logger log = LoggerFactory.getLogger(NotionMeetingQueryService.class);

    private static final String NOTION_API_BASE = "https://api.notion.com/v1";
    private static final String NOTION_VERSION = "2022-06-28";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final WebClient webClient;
    private final String databaseId;
    private final String notesStatusPropertyName;

    public NotionMeetingQueryService(
            WebClient.Builder webClientBuilder,
            @Value("${notion.api-token:}") String apiToken,
            @Value("${notion.meeting-database-id:}") String databaseId,
            @Value("${notion.property.notes-status:회의록 상태}") String notesStatusPropertyName) {
        this.databaseId = NotionIdNormalizer.normalize32(databaseId);
        this.notesStatusPropertyName = notesStatusPropertyName;
        this.webClient = webClientBuilder
                .baseUrl(NOTION_API_BASE)
                .defaultHeader("Authorization", "Bearer " + apiToken)
                .defaultHeader("Notion-Version", NOTION_VERSION)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @PostConstruct
    public void validateDatabaseAccess() {
        if (databaseId == null || databaseId.isBlank()) return;
        log.info("Notion DB access validate: databaseId={}", databaseId);
        // GET /databases/{database_id} 로 권한/ID 유효성 진단
        webClient.get()
                .uri("/databases/{id}", databaseId)
                .retrieve()
                .bodyToMono(String.class)
                .doOnNext(body -> {
                    try {
                        var j = MAPPER.readTree(body);
                        log.info("Notion DB validate OK: title={}", j.path("title").toString());
                    } catch (Exception e) {
                        log.info("Notion DB validate OK (raw): {}", body);
                    }
                })
                .onErrorResume(e -> {
                    log.warn("Notion DB validate failed: {}", e.getMessage());
                    log.warn("힌트: NOTION_MEETING_DATABASE_ID에는 data_sources[].id(데이터소스ID)가 아니라 database id를 넣어야 합니다. (GET /v1/databases/{id}가 200이어야 함)");
                    return Mono.empty();
                })
                .subscribe();
    }

    public Mono<List<NotionMeetingSnapshot>> queryAllMeetings() {
        if (databaseId == null || databaseId.isBlank()) {
            log.warn("NOTION_MEETING_DATABASE_ID가 비어 있어 노션 스캔을 건너뜁니다.");
            return Mono.just(List.of());
        }
        log.info("Notion query DB={}", databaseId);
        return fetchPage(null).flatMap(this::collectAll);
    }

    /**
     * 제목에 키워드가 포함된 페이지 (회의 DB). 날짜 없는 행도 포함.
     */
    public Mono<List<NotionPageHit>> searchByTitleContains(String keyword) {
        if (databaseId == null || databaseId.isBlank() || keyword == null || keyword.isBlank()) {
            return Mono.just(List.of());
        }
        String body = """
                {
                  "filter": {
                    "property": "제목",
                    "title": { "contains": "%s" }
                  },
                  "page_size": 15
                }
                """.formatted(escapeJson(keyword.trim()));
        return webClient.post()
                .uri("/databases/{id}/query", databaseId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .map(raw -> {
                    List<NotionPageHit> list = new ArrayList<>();
                    try {
                        var response = MAPPER.readTree(raw);
                        for (var page : response.path("results")) {
                            toPageHit(page).ifPresent(list::add);
                        }
                    } catch (Exception e) {
                        log.warn("Notion search parse failed: {}", e.getMessage());
                    }
                    return list;
                });
    }

    public Mono<NotionPageHit> retrievePageHit(String pageId) {
        if (pageId == null || pageId.isBlank()) {
            return Mono.empty();
        }
        return webClient.get()
                .uri("/pages/{id}", pageId)
                .retrieve()
                .bodyToMono(String.class)
                .flatMap(raw -> {
                    try {
                        return Mono.justOrEmpty(toPageHit(MAPPER.readTree(raw)));
                    } catch (Exception e) {
                        return Mono.empty();
                    }
                });
    }

    private Optional<NotionPageHit> toPageHit(com.fasterxml.jackson.databind.JsonNode page) {
        String id = page.path("id").asText(null);
        if (id == null || id.isBlank()) return Optional.empty();
        String url = page.path("url").asText("");
        String title = extractTitle(page.path("properties").path("제목"));
        return Optional.of(new NotionPageHit(id, title.isBlank() ? "(제목 없음)" : title, url));
    }

    private Mono<List<NotionMeetingSnapshot>> collectAll(com.fasterxml.jackson.databind.JsonNode first) {
        List<NotionMeetingSnapshot> out = new ArrayList<>();
        return collectRecursive(first, out);
    }

    private Mono<List<NotionMeetingSnapshot>> collectRecursive(com.fasterxml.jackson.databind.JsonNode body, List<NotionMeetingSnapshot> acc) {
        for (var page : body.path("results")) {
            parsePage(page).ifPresent(acc::add);
        }
        if (body.path("has_more").asBoolean(false)) {
            String cursor = body.path("next_cursor").asText(null);
            return fetchPage(cursor).flatMap(next -> collectRecursive(next, acc));
        }
        return Mono.just(acc);
    }

    private Mono<com.fasterxml.jackson.databind.JsonNode> fetchPage(String startCursor) {
        // Notion은 {} 형태의 JSON body를 기대합니다. (JsonNode/ObjectNode를 bodyValue로 넘기면 검증 에러가 날 수 있어 raw JSON string 사용)
        String body = (startCursor != null && !startCursor.isBlank())
                ? ("{\"start_cursor\":\"" + escapeJson(startCursor) + "\"}")
                : "{}";
        return webClient.post()
                .uri("/databases/{id}/query", databaseId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchangeToMono(resp -> handleJsonOrLogError(resp, "POST /databases/" + databaseId + "/query"))
                .map(raw -> {
                    try {
                        return MAPPER.readTree(raw);
                    } catch (Exception e) {
                        throw new RuntimeException("Notion response parse failed: " + e.getMessage());
                    }
                });
    }

    private Mono<String> handleJsonOrLogError(ClientResponse resp, String action) {
        if (resp.statusCode().is2xxSuccessful()) {
            return resp.bodyToMono(String.class).defaultIfEmpty("");
        }
        return resp.bodyToMono(String.class)
                .defaultIfEmpty("")
                .flatMap(body -> {
                    log.warn("Notion API {} failed: status={} body={}", action, resp.statusCode().value(), body);
                    return Mono.error(new RuntimeException("Notion API " + action + " failed: " + resp.statusCode().value()));
                });
    }

    private Optional<NotionMeetingSnapshot> parsePage(com.fasterxml.jackson.databind.JsonNode page) {
        String id = page.path("id").asText(null);
        if (id == null) return Optional.empty();
        String url = page.path("url").asText("");
        Instant created;
        try {
            created = Instant.parse(page.path("created_time").asText("1970-01-01T00:00:00.000Z"));
        } catch (Exception e) {
            created = Instant.EPOCH;
        }
        var props = page.path("properties");

        String title = extractTitle(props.path("제목"));
        ZonedDateTime start = extractDateStart(props.path("날짜"));
        if (start == null) return Optional.empty();
        ZonedDateTime end = extractDateEnd(props.path("날짜"));

        String target = extractTargetName(props.path("대상"));
        List<String> participants = extractMultiSelectNames(props.path("참여자"));
        String status = extractSelectName(props.path("상태"));
        String notesStatus = extractSelectName(props.path(notesStatusPropertyName));

        return Optional.of(NotionMeetingSnapshot.builder()
                .pageId(id)
                .title(title.isBlank() ? "(제목 없음)" : title)
                .pageUrl(url)
                .createdTime(created)
                .start(start)
                .end(end)
                .target(target)
                .participantNames(participants)
                .status(status)
                .notesStatus(notesStatus)
                .build());
    }

    private String extractTitle(com.fasterxml.jackson.databind.JsonNode prop) {
        if (prop.isMissingNode()) return "";
        JsonNode titleArr = prop.path("title");
        if (!titleArr.isArray() || titleArr.isEmpty()) return "";
        return titleArr.get(0).path("plain_text").asText("");
    }

    private ZonedDateTime extractDateStart(com.fasterxml.jackson.databind.JsonNode prop) {
        if (prop.isMissingNode()) return null;
        JsonNode date = prop.path("date");
        if (date.isMissingNode() || date.isNull()) return null;
        String start = date.path("start").asText(null);
        if (start == null || start.isBlank()) return null;
        // 날짜만 들어있는 경우 start는 00:00으로 잡아야 리마인더가 안정적입니다.
        if (start.length() == 10) {
            try {
                return ZonedDateTime.parse(start + "T00:00:00+09:00");
            } catch (Exception e) {
                return null;
            }
        }
        return parseNotionDateTime(start);
    }   

    private ZonedDateTime extractDateEnd(com.fasterxml.jackson.databind.JsonNode prop) {
        if (prop.isMissingNode()) return null;
        JsonNode date = prop.path("date");
        if (date.isMissingNode() || date.isNull()) return null;
        String end = date.path("end").asText(null);
        if (end == null || end.isBlank()) return null;
        // 날짜만 들어있는 경우 end는 23:59로 잡아야 "종료 시각" 기반 요약이 안정적입니다.
        if (end.length() == 10) {
            try {
                return ZonedDateTime.parse(end + "T23:59:59+09:00");
            } catch (Exception e) {
                return null;
            }
        }
        return parseNotionDateTime(end);
    }

    private ZonedDateTime parseNotionDateTime(String s) {
        try {
            if (s.length() == 10) {
                return ZonedDateTime.parse(s + "T23:59:59+09:00");
            }
            return ZonedDateTime.parse(s);
        } catch (DateTimeParseException e) {
            try {
                return java.time.LocalDateTime.parse(s.substring(0, Math.min(19, s.length())))
                        .atZone(java.time.ZoneId.of("Asia/Seoul"));
            } catch (Exception ex) {
                return null;
            }
        }
    }

    private String extractSelectName(com.fasterxml.jackson.databind.JsonNode prop) {
        if (prop.isMissingNode()) return "";
        JsonNode sel = prop.path("select");
        if (sel.isMissingNode() || sel.isNull()) return "";
        return sel.path("name").asText("");
    }

    /**
     * `대상` 속성(Select 또는 Multi-select)에서 첫번째 값의 name만 추출
     */
    private String extractTargetName(com.fasterxml.jackson.databind.JsonNode prop) {
        if (prop.isMissingNode()) return "";
        String selectName = extractSelectName(prop);
        if (selectName != null && !selectName.isBlank()) return selectName;

        JsonNode arr = prop.path("multi_select");
        if (arr.isArray() && arr.size() > 0) {
            for (JsonNode n : arr) {
                String name = n.path("name").asText("");
                if (!name.isBlank()) return name;
            }
        }
        return "";
    }

    private List<String> extractMultiSelectNames(com.fasterxml.jackson.databind.JsonNode prop) {
        List<String> names = new ArrayList<>();
        if (prop.isMissingNode()) return names;
        JsonNode arr = prop.path("multi_select");
        if (!arr.isArray()) return names;
        for (JsonNode n : arr) {
            String name = n.path("name").asText("");
            if (!name.isBlank()) names.add(name);
        }
        return names;
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
