package dpm18th.team3bot.ocr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 이미지 업로드 → OCR 실행 → 환산 API 까지 전체 흐름을 10회 반복해
 * 각 요청의 소요 시간(ms)을 측정합니다.
 *
 * <pre>
 *  1번 테스트:  1,234ms  (추출가격=  7,140원)
 *  2번 테스트:    876ms  (추출가격=  7,140원)
 *  ...
 * 평균: 1,050ms  |  최소: 800ms  |  최대: 1,400ms
 * </pre>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "ocr.enabled=true",
        "gemini.api-key=",          // Java fallback 사용 (Gemini 없이도 동작)
        "spring.security.user.name=test",
        "spring.security.user.password=test"
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OcrE2ETimingTest {

    private static final int REPEAT = 10;
    private static final String IMAGE_RESOURCE = "test-images/maychef-kurly.png";

    @LocalServerPort int port;
    @Autowired ObjectMapper objectMapper;

    private byte[] imageBytes;
    private HttpClient http;

    @BeforeAll
    void setUp() throws IOException {
        imageBytes = new ClassPathResource(IMAGE_RESOURCE).getInputStream().readAllBytes();
        http = HttpClient.newBuilder()
                .version(java.net.http.HttpClient.Version.HTTP_1_1)
                .build();
    }

    @Test
    void ocrToCompressE2ETiming() throws Exception {
        String base = "http://localhost:" + port;
        List<Long> durations = new ArrayList<>();

        System.out.println("=== OCR → 환산 E2E 타이밍 테스트 (" + REPEAT + "회) ===");

        for (int i = 1; i <= REPEAT; i++) {
            long start = System.currentTimeMillis();

            // ── Step 1: 이미지 → OCR ─────────────────────────────────────
            String boundary = UUID.randomUUID().toString().replace("-", "");
            byte[] multipartBody = buildMultipartBody(boundary, imageBytes, "test.png");

            HttpRequest ocrReq = HttpRequest.newBuilder()
                    .uri(URI.create(base + "/ocr/api"))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .header("Authorization", basicAuth("test", "test"))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(multipartBody))
                    .build();

            HttpResponse<String> ocrResp = http.send(ocrReq, HttpResponse.BodyHandlers.ofString());
            assertThat(ocrResp.statusCode())
                    .withFailMessage("OCR HTTP %d: %s", ocrResp.statusCode(), ocrResp.body())
                    .isEqualTo(200);

            JsonNode ocrJson = objectMapper.readTree(ocrResp.body());
            assertThat(ocrJson.path("ok").asBoolean())
                    .withFailMessage("OCR ok=false: %s", ocrJson)
                    .isTrue();

            long priceWon = ocrJson.at("/extractedPurchase/priceWon").asLong(1_000L);

            // ── Step 2: 가격 → 환산(compress) ────────────────────────────
            String compressBody = "{\"priceWon\":" + priceWon + "}";

            HttpRequest compressReq = HttpRequest.newBuilder()
                    .uri(URI.create(base + "/ocr/api/compress"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", basicAuth("test", "test"))
                    .POST(HttpRequest.BodyPublishers.ofString(compressBody))
                    .build();

            HttpResponse<String> compressResp = http.send(compressReq, HttpResponse.BodyHandlers.ofString());
            assertThat(compressResp.statusCode()).isEqualTo(200);

            JsonNode compressJson = objectMapper.readTree(compressResp.body());

            long elapsed = System.currentTimeMillis() - start;
            durations.add(elapsed);

            System.out.printf("%2d번 테스트: %,6dms  (추출가격=%,8d원  |  compress.ok=%s)%n",
                    i, elapsed, priceWon, compressJson.path("ok").asBoolean());
        }

        long avg  = (long) durations.stream().mapToLong(Long::longValue).average().orElse(0);
        long min  = durations.stream().mapToLong(Long::longValue).min().orElse(0);
        long max  = durations.stream().mapToLong(Long::longValue).max().orElse(0);

        System.out.println("──────────────────────────────────────────────────────");
        System.out.printf("평균: %,dms  |  최소: %,dms  |  최대: %,dms%n", avg, min, max);

        assertThat(max).isLessThan(120_000L);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static byte[] buildMultipartBody(String boundary, byte[] fileBytes, String filename) {
        String crlf = "\r\n";
        String header =
                "--" + boundary + crlf
                + "Content-Disposition: form-data; name=\"image\"; filename=\"" + filename + "\"" + crlf
                + "Content-Type: image/png" + crlf
                + crlf;
        String footer = crlf + "--" + boundary + "--" + crlf;

        byte[] h = header.getBytes(StandardCharsets.US_ASCII);
        byte[] f = footer.getBytes(StandardCharsets.US_ASCII);
        byte[] result = new byte[h.length + fileBytes.length + f.length];
        System.arraycopy(h, 0, result, 0, h.length);
        System.arraycopy(fileBytes, 0, result, h.length, fileBytes.length);
        System.arraycopy(f, 0, result, h.length + fileBytes.length, f.length);
        return result;
    }

    private static String basicAuth(String user, String password) {
        String token = user + ":" + password;
        return "Basic " + java.util.Base64.getEncoder()
                .encodeToString(token.getBytes(StandardCharsets.UTF_8));
    }
}
