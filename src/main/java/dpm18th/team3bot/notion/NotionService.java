package dpm18th.team3bot.notion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class NotionService {

    private static final String NOTION_API_BASE = "https://api.notion.com/v1";
    private static final String NOTION_VERSION = "2022-06-28";
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final WebClient webClient;
    private final String parentPageId;
    private final String parentDatabaseId;

    public NotionService(
            WebClient.Builder webClientBuilder,
            @Value("${notion.api-token:}") String apiToken,
            @Value("${notion.parent-page-id:}") String parentPageId,
            @Value("${notion.meeting-database-id:}") String parentDatabaseId) {
        this.parentPageId = NotionIdNormalizer.normalize32(parentPageId);
        this.parentDatabaseId = NotionIdNormalizer.normalize32(parentDatabaseId);
        this.webClient = webClientBuilder
                .baseUrl(NOTION_API_BASE)
                .defaultHeader("Authorization", "Bearer " + apiToken)
                .defaultHeader("Notion-Version", NOTION_VERSION)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * 회의 페이지를 Notion Database에 생성
     * 속성: 제목, 날짜(로컬), 대상, 서기, 참여자
     */
    public Mono<MeetingCreateResult> createMeetingPage(MeetingCreateRequest req) {
        ObjectNode parent = objectMapper.createObjectNode();
        if (parentDatabaseId != null && !parentDatabaseId.isBlank()) {
            parent.put("database_id", parentDatabaseId);
        } else {
            parent.put("page_id", parentPageId);
        }

        String dateStr = req.getDate();
        String timeStr = req.getTime();
        if (dateStr == null || dateStr.isBlank()) {
            LocalDate today = LocalDate.now();
            dateStr = today.format(DateTimeFormatter.ISO_LOCAL_DATE);
        }
        if (timeStr == null || timeStr.isBlank()) {
            timeStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
        }

        ObjectNode properties = objectMapper.createObjectNode();

        // 제목
        ObjectNode titleProp = objectMapper.createObjectNode();
        ArrayNode titleArray = objectMapper.createArrayNode();
        ObjectNode titleText = objectMapper.createObjectNode();
        titleText.put("type", "text");
        ObjectNode textContent = objectMapper.createObjectNode();
        textContent.put("content", req.getTitle() != null && !req.getTitle().isBlank() ? req.getTitle() : "새 회의");
        titleText.set("text", textContent);
        titleArray.add(titleText);
        titleProp.set("title", titleArray);
        properties.set("제목", titleProp);

        // 날짜 (로컬 타임)
        ObjectNode dateProp = objectMapper.createObjectNode();
        ObjectNode dateObj = objectMapper.createObjectNode();
        dateObj.put("start", dateStr + (timeStr != null && !timeStr.isBlank() ? "T" + timeStr + ":00" : ""));
        dateProp.set("date", dateObj);
        properties.set("날짜", dateProp);

        // 대상 (Select: Server, Design, Web, 전체)
        if (req.getTarget() != null && !req.getTarget().isBlank()) {
            ObjectNode targetProp = objectMapper.createObjectNode();
            ObjectNode select = objectMapper.createObjectNode();
            select.put("name", normalizeTarget(req.getTarget()));
            targetProp.set("select", select);
            properties.set("대상", targetProp);
        }

        // 서기 (Text 또는 Select - Notion에서 People이면 rich_text로 대체)
        if (req.getScribe() != null && !req.getScribe().isBlank()) {
            ObjectNode scribeProp = objectMapper.createObjectNode();
            ArrayNode richText = objectMapper.createArrayNode();
            ObjectNode rt = objectMapper.createObjectNode();
            rt.put("type", "text");
            ObjectNode rtText = objectMapper.createObjectNode();
            rtText.put("content", req.getScribe().trim());
            rt.set("text", rtText);
            richText.add(rt);
            scribeProp.set("rich_text", richText);
            properties.set("서기", scribeProp);
        }

        // 참여자 (Multi-select)
        if (req.getParticipants() != null && !req.getParticipants().isEmpty()) {
            ObjectNode peopleProp = objectMapper.createObjectNode();
            ArrayNode multiSelect = objectMapper.createArrayNode();
            for (String p : req.getParticipants()) {
                if (p != null && !p.trim().isBlank()) {
                    ObjectNode opt = objectMapper.createObjectNode();
                    opt.put("name", p.trim());
                    multiSelect.add(opt);
                }
            }
            peopleProp.set("multi_select", multiSelect);
            properties.set("참여자", peopleProp);
        }

        ObjectNode body = objectMapper.createObjectNode();
        body.set("parent", parent);
        body.set("properties", properties);

        ArrayNode children = objectMapper.createArrayNode();

        // 회의 주제 블록
        ObjectNode topicHeading = objectMapper.createObjectNode();
        topicHeading.put("object", "block");
        topicHeading.put("type", "heading_2");
        ObjectNode topicProps = objectMapper.createObjectNode();
        ArrayNode topicRt = objectMapper.createArrayNode();
        ObjectNode topicRtItem = objectMapper.createObjectNode();
        topicRtItem.put("type", "text");
        ObjectNode topicContent = objectMapper.createObjectNode();
        topicContent.put("content", "회의 주제");
        topicRtItem.set("text", topicContent);
        topicRt.add(topicRtItem);
        topicProps.set("rich_text", topicRt);
        topicHeading.set("heading_2", topicProps);
        children.add(topicHeading);

        if (req.getAgenda() != null && !req.getAgenda().isBlank()) {
            ObjectNode para = objectMapper.createObjectNode();
            para.put("object", "block");
            para.put("type", "paragraph");
            ObjectNode paraProps = objectMapper.createObjectNode();
            ArrayNode paraRt = objectMapper.createArrayNode();
            ObjectNode paraRtItem = objectMapper.createObjectNode();
            paraRtItem.put("type", "text");
            ObjectNode paraContent = objectMapper.createObjectNode();
            paraContent.put("content", req.getAgenda());
            paraRtItem.set("text", paraContent);
            paraRt.add(paraRtItem);
            paraProps.set("rich_text", paraRt);
            para.set("paragraph", paraProps);
            children.add(para);
        }

        body.set("children", children);

        String targetForSlack = req.getTarget() != null && !req.getTarget().isBlank()
                ? normalizeTarget(req.getTarget()) : "";

        return webClient.post()
                .uri("/pages")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(node -> new MeetingCreateResult(node.get("url").asText(), targetForSlack))
                .onErrorResume(e -> Mono.error(new RuntimeException("Notion 생성 실패: " + e.getMessage())));
    }

    private String normalizeTarget(String target) {
        String t = target.trim();
        return switch (t.toLowerCase()) {
            case "server", "서버" -> "Server";
            case "design", "디자인" -> "Design";
            case "web", "웹" -> "Web";
            case "전체", "all" -> "전체";
            default -> t;
        };
    }

    /**
     * 공지 페이지 생성
     */
    public Mono<String> createAnnouncementPage(String title, String content) {
        if (parentPageId == null || parentPageId.isBlank()) {
            return Mono.error(new IllegalStateException("notion.parent-page-id가 설정되지 않았습니다."));
        }
        ObjectNode parentObj = objectMapper.createObjectNode();
        parentObj.put("page_id", parentPageId);

        ObjectNode properties = objectMapper.createObjectNode();
        ObjectNode titleProp = objectMapper.createObjectNode();
        ArrayNode titleArray = objectMapper.createArrayNode();
        ObjectNode titleText = objectMapper.createObjectNode();
        titleText.put("type", "text");
        ObjectNode textContent = objectMapper.createObjectNode();
        textContent.put("content", title != null ? title : "새 공지");
        titleText.set("text", textContent);
        titleArray.add(titleText);
        titleProp.set("title", titleArray);
        properties.set("title", titleProp);

        ObjectNode body = objectMapper.createObjectNode();
        body.set("parent", parentObj);
        body.set("properties", properties);

        ArrayNode children = objectMapper.createArrayNode();
        ObjectNode para = objectMapper.createObjectNode();
        para.put("object", "block");
        para.put("type", "paragraph");
        ObjectNode paraProps = objectMapper.createObjectNode();
        ArrayNode paraRt = objectMapper.createArrayNode();
        ObjectNode paraRtItem = objectMapper.createObjectNode();
        paraRtItem.put("type", "text");
        ObjectNode paraContent = objectMapper.createObjectNode();
        paraContent.put("content", content != null ? content : "");
        paraRtItem.set("text", paraContent);
        paraRt.add(paraRtItem);
        paraProps.set("rich_text", paraRt);
        para.set("paragraph", paraProps);
        children.add(para);
        body.set("children", children);

        return webClient.post()
                .uri("/pages")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(node -> node.get("url").asText())
                .onErrorResume(e -> Mono.just("Error: " + e.getMessage()));
    }
}
