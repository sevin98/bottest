package dpm18th.team3bot.gemini;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * OCR로 뽑은 상품명을 Gemini로 한 번 더 "제품 타이틀" 형태로 다듬습니다.
 *
 * <p>중요: 쇼핑몰별 규칙/클래스를 만들지 않기 위해, 입력(원문 OCR 텍스트 + 1차 추출 제목)만 제공하고
 * 모델이 "가장 그럴듯한 제품명 한 줄"만 반환하도록 합니다.</p>
 */
@Service
public class GeminiTitleRefiner {

    private static final Logger log = LoggerFactory.getLogger(GeminiTitleRefiner.class);

    private static final String BASE_URL = "https://generativelanguage.googleapis.com";

    // 안전한 후처리(너무 공격적인 교정 금지)
    private static final Pattern MULTI_SPACE = Pattern.compile("\\s+");
    private static final Pattern JSON_TITLE = Pattern.compile("\"title\"\\s*:\\s*\"(.*?)\"", Pattern.DOTALL);
    private static final Pattern JSON_CONF = Pattern.compile("\"confidence\"\\s*:\\s*([0-9]+(?:\\.[0-9]+)?)", Pattern.DOTALL);

    private final GeminiProperties props;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public GeminiTitleRefiner(GeminiProperties props, WebClient.Builder builder, ObjectMapper objectMapper) {
        this.props = props;
        this.webClient = builder.baseUrl(BASE_URL).build();
        this.objectMapper = objectMapper;
    }

    /**
     * OCR 원문만 받아서 Gemini로 상품명 + 가격을 1차 추출합니다.
     * Gemini 키가 없거나 신뢰도가 낮으면 disabled/낮은 confidence 결과를 반환하며,
     * 호출 측에서 Java 휴리스틱 fallback 여부를 판단합니다.
     */
    public GeminiRefineResult extractPurchaseFromOcr(String ocrText) {
        String key = props.getApiKey();
        if (key == null || key.isBlank()) {
            return GeminiRefineResult.disabled();
        }
        if (ocrText == null || ocrText.isBlank()) {
            GeminiRefineResult r = new GeminiRefineResult();
            r.setEnabled(true);
            r.setModel(props.getModel());
            r.setNote("empty ocr text");
            return r;
        }

        GeminiRefineResult meta = new GeminiRefineResult();
        meta.setEnabled(true);
        meta.setModel(props.getModel());
        meta.setInputTextPreview(previewText(ocrText));

        // 상품명 근처 관련 텍스트만 추출해 토큰을 절약하고 Gemini 집중도를 높임
        String excerpt = extractRelevantExcerpt(ocrText, "");
        if (excerpt.isBlank()) {
            excerpt = ocrText.replace('\u00A0', ' ');
            if (excerpt.length() > 2000) excerpt = excerpt.substring(0, 2000);
        }

        String prompt =
                "아래는 온라인 쇼핑 상품 페이지 캡처의 OCR 텍스트야.\n"
                + "이 페이지에는 반드시 판매 상품이 하나 있어. 상품명과 가격을 찾아줘.\n\n"
                + "출력 형식 (JSON 한 줄만, 마크다운 코드펜스 금지):\n"
                + "{\"title\":\"...\",\"priceWon\":숫자,\"confidence\":0.0~1.0}\n\n"
                + "필수 규칙:\n"
                + "① title: 실제 판매 상품명 (브랜드·모델명·용량·색상 포함, 2~90자)\n"
                + "  - [브랜드명] 형식 발견 시 → 그 뒤 상품명과 합쳐서 title 사용\n"
                + "  - 가격 바로 위·아래 줄이 상품명일 가능성 높음\n"
                + "  - 메뉴/로그인/배송안내/적립/리뷰/날짜 문구는 제외\n"
                + "  - ⚠️ title은 절대 빈 문자열로 두지 말 것. 확신이 낮아도 가장 가능성 높은 값으로 채울 것\n"
                + "② priceWon: 최종 결제 가격 (원, 정수). 할인가 있으면 할인가. 모르면 0\n"
                + "③ confidence: 추출 확신도 0.0~1.0. 낮아도 title은 반드시 입력\n\n"
                + "OCR 텍스트:\n" + excerpt;

        ObjectNode req = objectMapper.createObjectNode();
        ArrayNode contents = req.putArray("contents");
        ObjectNode content0 = contents.addObject();
        content0.putArray("parts").addObject().put("text", prompt);

        ObjectNode cfg = req.putObject("generationConfig");
        cfg.put("temperature", 0.1);
        cfg.put("maxOutputTokens", 1024);
        // responseMimeType으로 JSON 모드만 강제. responseSchema는 Gemini가 empty title을
        // 반환하도록 유도하는 부작용이 있어 사용하지 않음.
        cfg.put("responseMimeType", "application/json");
        cfg.put("response_mime_type", "application/json");

        // gemini-2.5-flash 등 thinking 지원 모델은 thinkingConfig로 사고 과정 활성화
        // thinking 모델이 아닌 경우 Gemini가 이 필드를 무시하므로 항상 추가해도 안전
        ObjectNode thinkingCfg = cfg.putObject("thinkingConfig");
        thinkingCfg.put("thinkingBudget", 1024);

        String url = "/v1beta/models/" + props.getModel() + ":generateContent?key=" + key.trim();
        final String reqJson;
        try {
            reqJson = objectMapper.writeValueAsString(req);
            meta.setRequestPreview(previewBody(reqJson));
        } catch (Exception e) {
            meta.setNote("request serialize error");
            return meta;
        }

        try {
            String raw = webClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .bodyValue(reqJson)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofMillis(Math.max(1000, props.getTimeoutMs())))
                    .block();

            if (raw == null || raw.isBlank()) {
                meta.setNote("empty response (no body)");
                return meta;
            }
            meta.setResponsePreview(previewBody(raw));
            parsePurchaseAndFill(meta, raw);
            return meta;
        } catch (WebClientResponseException e) {
            meta.setNote("http " + e.getStatusCode().value());
            meta.setResponsePreview(previewBody(e.getResponseBodyAsString()));
            return meta;
        } catch (Exception e) {
            if (e.getCause() instanceof TimeoutException) {
                meta.setNote("timeout");
                return meta;
            }
            log.debug("Gemini extract failed: {}", e.toString());
            meta.setNote("exception: " + e.getClass().getSimpleName());
            return meta;
        }
    }

    private void parsePurchaseAndFill(GeminiRefineResult meta, String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            String modelText = extractCandidateText(root);
            if (modelText == null || modelText.isBlank()) {
                JsonNode msg = root.at("/error/message");
                meta.setNote(!msg.isMissingNode() ? "api error: " + msg.asText() : "missing candidates text");
                return;
            }
            modelText = stripCodeFences(modelText.trim());

            String title;
            long priceWon;
            double conf;
            try {
                JsonNode out = objectMapper.readTree(modelText);
                title = out.path("title").asText("").trim();
                priceWon = out.path("priceWon").asLong(0);
                conf = out.path("confidence").asDouble(0.0);
            } catch (Exception je) {
                // regex fallback
                Matcher mt = JSON_TITLE.matcher(modelText);
                title = mt.find() ? mt.group(1).trim() : "";
                Matcher mc = JSON_CONF.matcher(modelText);
                conf = mc.find() ? Double.parseDouble(mc.group(1)) : 0.0;
                priceWon = 0;
            }

            meta.setOutputTitle(postProcessTitle(title));
            meta.setOutputPriceWon(priceWon > 0 ? priceWon : null);
            meta.setConfidence(conf);

            if (title.isBlank()) {
                meta.setNote("empty title");
                return;
            }
            if (conf < props.getMinConfidence()) {
                meta.setNote("low confidence");
                return;
            }
            meta.setNote("ok");
        } catch (Exception e) {
            meta.setNote("parse error: " + e.getMessage());
        }
    }

    /** @deprecated Gemini를 1차 추출로 쓰는 구조로 전환. extractPurchaseFromOcr 사용 권장. */
    @Deprecated
    public GeminiRefineResult refineProductTitle(String ocrText, String extractedTitle, Long extractedPriceWon) {
        String key = props.getApiKey();
        if (key == null || key.isBlank()) {
            return GeminiRefineResult.disabled();
        }
        if (extractedTitle == null || extractedTitle.isBlank()) {
            GeminiRefineResult r = new GeminiRefineResult();
            r.setEnabled(true);
            r.setModel(props.getModel());
            r.setNote("empty input title");
            return r;
        }

        GeminiRefineResult meta = new GeminiRefineResult();
        meta.setEnabled(true);
        meta.setModel(props.getModel());
        meta.setInputTitle(extractedTitle);
        meta.setInputPriceWon(extractedPriceWon);
        meta.setInputTextPreview(previewText(ocrText));

        String prompt = buildPrompt(ocrText, extractedTitle, extractedPriceWon);
        ObjectNode req = objectMapper.createObjectNode();
        ArrayNode contents = req.putArray("contents");
        ObjectNode content0 = contents.addObject();
        ArrayNode parts = content0.putArray("parts");
        parts.addObject().put("text", prompt);

        // 모델이 JSON만 반환하도록 힌트
        ObjectNode cfg = req.putObject("generationConfig");
        cfg.put("temperature", 0.1);
        cfg.put("maxOutputTokens", 512);
        // API/제품군에 따라 responseMimeType 표기가 다르게 적용되는 경우가 있어 둘 다 세팅
        cfg.put("responseMimeType", "application/json");
        cfg.put("response_mime_type", "application/json");
        // Schema 기반 제약 (가능한 경우 JSON만 반환하게 강제)
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode propsNode = schema.putObject("properties");
        propsNode.putObject("title").put("type", "string");
        propsNode.putObject("confidence").put("type", "number");
        ArrayNode reqFields = schema.putArray("required");
        reqFields.add("title");
        reqFields.add("confidence");
        cfg.set("responseSchema", schema);
        cfg.set("response_schema", schema);

        String url = "/v1beta/models/" + props.getModel() + ":generateContent?key=" + key.trim();
        final String reqJson;
        try {
            reqJson = objectMapper.writeValueAsString(req);
            meta.setRequestPreview(previewBody(reqJson));
        } catch (Exception e) {
            meta.setNote("request serialize error");
            return meta;
        }

        try {
            String raw = webClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    // JsonNode가 POJO처럼 직렬화되는 환경을 피하려고, JSON 문자열로 직접 전송
                    .bodyValue(reqJson)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofMillis(Math.max(500, props.getTimeoutMs())))
                    .block();

            if (raw == null || raw.isBlank()) {
                meta.setNote("empty response (no body)");
                return meta;
            }
            // 성공/실패와 무관하게 원본 미리보기 저장 (디버깅용)
            meta.setResponsePreview(previewBody(raw));
            parseAndFill(meta, raw, extractedTitle);
            return meta;
        } catch (WebClientResponseException e) {
            meta.setNote("http " + e.getStatusCode().value());
            meta.setResponsePreview(previewBody(e.getResponseBodyAsString()));
            return meta;
        } catch (Exception e) {
            if (e.getCause() instanceof TimeoutException) {
                meta.setNote("timeout");
                return meta;
            }
            log.debug("Gemini refine failed: {}", e.toString());
            meta.setNote("exception: " + e.getClass().getSimpleName());
            return meta;
        }
    }

    private String buildPrompt(String ocrText, String extractedTitle, Long priceWon) {
        String safeText = extractRelevantExcerpt(ocrText, extractedTitle);
        String p = (priceWon == null) ? "null" : String.valueOf(priceWon);

        return ""
                + "너는 OCR 텍스트에서 실제 '제품 제목(상품명)' 한 줄만 추정해 주는 도우미야.\n"
                + "입력은 OCR 원문과 1차 추출 제목(노이즈 섞임 가능)이야.\n"
                + "아래 규칙을 지켜.\n"
                + "- 출력은 JSON만. 마크다운 코드펜스(```json) 금지.\n"
                + "- 출력 포맷: {\"title\":\"...\",\"confidence\":0.0~1.0}\n"
                + "- title은 2~90자, 한 줄.\n"
                + "- 결제/배송/적립/상품평/날짜/지역/버튼/보장 문구 같은 UI 문구는 제외.\n"
                + "- 브랜드/모델명/용량/색상 등 제품 정보는 유지.\n"
                + "- 불확실해도 가능한 한 title은 채우고 confidence만 낮게 줘.\n"
                + "- 정말 제품명을 특정할 근거가 없을 때만 title을 빈 문자열로.\n"
                + "\n"
                + "1차 추출 title: " + extractedTitle + "\n"
                + "1차 추출 priceWon: " + p + "\n"
                + "\n"
                + "OCR 발췌:\n"
                + safeText + "\n";
    }

    private void parseAndFill(GeminiRefineResult meta, String geminiResponseJson, String fallbackTitle) {
        try {
            JsonNode root = objectMapper.readTree(geminiResponseJson);
            String modelText = extractCandidateText(root);
            if (modelText == null || modelText.isBlank()) {
                JsonNode msg = root.at("/error/message");
                if (!msg.isMissingNode() && !msg.asText().isBlank()) {
                    meta.setNote("api error: " + msg.asText());
                } else {
                    meta.setNote("missing candidates text");
                }
                return;
            }
            modelText = stripCodeFences(modelText.trim());

            String title;
            double conf;
            try {
                JsonNode out = objectMapper.readTree(modelText);
                title = out.path("title").asText("");
                conf = out.path("confidence").asDouble(0.0);
            } catch (Exception jsonErr) {
                // 마지막 fallback: 정규식으로 title/confidence 추출 (모델이 JSON처럼 보이지만 파싱이 깨질 때)
                String[] tc = regexExtractTitleConfidence(modelText);
                title = tc[0];
                conf = Double.parseDouble(tc[1]);
            }

            title = postProcessTitle(title);
            meta.setOutputTitle(title);
            meta.setConfidence(conf);
            if (title.isBlank()) {
                meta.setNote("empty title");
                return;
            }
            if (conf < props.getMinConfidence()) {
                meta.setNote("low confidence");
                return;
            }
            if (title.isBlank() || title.length() < 2 || title.length() > 90) {
                meta.setNote("invalid title length");
                return;
            }
            // 모델이 아예 엉뚱한 문자열로 교체하는 걸 방지: 너무 달라지면 버림
            if (!looksRelated(title, fallbackTitle)) {
                meta.setNote("unrelated output");
                return;
            }
            meta.setNote("ok");
        } catch (Exception e) {
            meta.setNote("parse error");
        }
    }

    private static String extractCandidateText(JsonNode root) {
        // v1beta generateContent: candidates[0].content.parts[*].text
        // Thinking 모델은 thought:true 인 파트(사고 과정)를 포함하므로 제외하고 최종 답변만 수집
        JsonNode parts = root.at("/candidates/0/content/parts");
        if (!parts.isMissingNode() && parts.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode p : parts) {
                // thought:true 는 내부 추론 과정 — JSON 응답이 아니므로 건너뜀
                JsonNode thought = p.get("thought");
                if (thought != null && thought.asBoolean(false)) continue;
                JsonNode t = p.get("text");
                if (t != null && !t.asText("").isBlank()) {
                    if (!sb.isEmpty()) sb.append("\n");
                    sb.append(t.asText());
                }
            }
            String s = sb.toString();
            if (!s.isBlank()) return s;
        }
        // fallback: 일부 응답은 다른 경로로 올 수 있음
        JsonNode alt1 = root.at("/candidates/0/output");
        if (!alt1.isMissingNode() && !alt1.asText("").isBlank()) return alt1.asText();
        JsonNode alt2 = root.at("/candidates/0/content/text");
        if (!alt2.isMissingNode() && !alt2.asText("").isBlank()) return alt2.asText();
        return null;
    }

    private static String[] regexExtractTitleConfidence(String modelText) {
        String title = "";
        double conf = 0.0;
        Matcher mt = JSON_TITLE.matcher(modelText);
        if (mt.find()) {
            title = mt.group(1);
        }
        Matcher mc = JSON_CONF.matcher(modelText);
        if (mc.find()) {
            try {
                conf = Double.parseDouble(mc.group(1));
            } catch (NumberFormatException ignored) {
                conf = 0.0;
            }
        }
        return new String[]{title, String.valueOf(conf)};
    }

    private static String stripCodeFences(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.startsWith("```")) {
            int firstNl = t.indexOf('\n');
            if (firstNl >= 0) {
                t = t.substring(firstNl + 1);
            }
            int end = t.lastIndexOf("```");
            if (end >= 0) {
                t = t.substring(0, end);
            }
        }
        return t.trim();
    }

    /**
     * OCR 전체를 넣으면 토큰이 커져서 응답이 잘릴 수 있어, title 근처 줄만 발췌합니다.
     */
    private static String extractRelevantExcerpt(String ocrText, String extractedTitle) {
        if (ocrText == null || ocrText.isBlank()) {
            return "";
        }
        String[] lines = ocrText.replace('\u00A0', ' ').split("\\R");
        // extractedTitle이 오염됐을 때(네비/카테고리 등) 대비: "제품명처럼 보이는 줄"을 직접 찾는다.
        int bestIdx = -1;
        int bestScore = Integer.MIN_VALUE;
        for (int i = 0; i < lines.length; i++) {
            String t = lines[i].trim();
            if (t.isBlank() || t.length() > 140) continue;
            int hangul = 0;
            boolean hasDigit = false;
            for (int k = 0; k < t.length(); k++) {
                char c = t.charAt(k);
                if (c >= 0xAC00 && c <= 0xD7A3) hangul++;
                if (Character.isDigit(c)) hasDigit = true;
            }
            int score = hangul * 3 + Math.min(t.length(), 60);
            if (t.contains("[") || t.contains("]")) score += 25;
            if (t.contains("원") && hasDigit) score += 18;
            if (t.contains("로그인") || t.contains("카테고리") || t.contains("마이쇼핑")) score -= 30;
            if (score > bestScore) {
                bestScore = score;
                bestIdx = i;
            }
        }
        if (bestIdx < 0) {
            bestIdx = 0;
        }
        int from = Math.max(0, bestIdx - 4);
        int to = Math.min(lines.length, bestIdx + 8);
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < to; i++) {
            String line = lines[i].trim();
            if (line.isBlank()) continue;
            sb.append(line).append('\n');
        }
        String out = sb.toString().trim();
        if (out.length() > 1200) {
            out = out.substring(0, 1200);
        }
        return out;
    }

    private static String postProcessTitle(String s) {
        if (s == null) return "";
        String t = s.replace('\u00A0', ' ');
        t = MULTI_SPACE.matcher(t).replaceAll(" ").trim();
        t = t.replaceAll("(?:[,，、]\\s*)+$", "");
        return t.trim();
    }

    private static boolean looksRelated(String refined, String original) {
        if (original == null || original.isBlank()) return true;
        String a = refined.replaceAll("\\s+", "");
        String b = original.replaceAll("\\s+", "");
        if (a.isEmpty() || b.isEmpty()) return true;
        // 공통 부분 문자열이 어느 정도 있으면 OK (너무 엄격하면 실패가 많아짐)
        int common = longestCommonSubstringLen(a, b);
        int min = Math.min(a.length(), b.length());
        return common >= Math.max(3, min / 4);
    }

    // O(n*m) but titles are short.
    private static int longestCommonSubstringLen(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        int best = 0;
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                if (a.charAt(i - 1) == b.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1] + 1;
                    if (dp[i][j] > best) best = dp[i][j];
                }
            }
        }
        return best;
    }

    private static String previewText(String ocrText) {
        if (ocrText == null || ocrText.isBlank()) {
            return null;
        }
        String s = ocrText.replace('\u00A0', ' ').trim();
        s = s.length() > 420 ? s.substring(0, 420) : s;
        s = MULTI_SPACE.matcher(s).replaceAll(" ").trim();
        return s;
    }

    private static String previewBody(String body) {
        if (body == null) return null;
        String s = body.replace('\u00A0', ' ').trim();
        s = s.length() > 420 ? s.substring(0, 420) : s;
        s = MULTI_SPACE.matcher(s).replaceAll(" ").trim();
        return s.isEmpty() ? null : s;
    }
}

