package dpm18th.team3bot.ocr;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dpm18th.team3bot.gemini.GeminiRefineResult;
import dpm18th.team3bot.gemini.GeminiTitleRefiner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Base64;

@RestController
@RequestMapping("/ocr")
@ConditionalOnProperty(prefix = "ocr", name = "enabled", havingValue = "true")
public class OcrController {

    private static final Logger log = LoggerFactory.getLogger(OcrController.class);

    private final OcrService ocrService;
    private final OcrPurchaseParser purchaseParser;
    private final SpendCompressionService compressionService;
    private final ObjectMapper objectMapper;
    private final GeminiTitleRefiner geminiTitleRefiner;

    public OcrController(
            OcrService ocrService,
            OcrPurchaseParser purchaseParser,
            SpendCompressionService compressionService,
            ObjectMapper objectMapper,
            GeminiTitleRefiner geminiTitleRefiner) {
        this.ocrService = ocrService;
        this.purchaseParser = purchaseParser;
        this.compressionService = compressionService;
        this.objectMapper = objectMapper;
        this.geminiTitleRefiner = geminiTitleRefiner;
    }

    @GetMapping(produces = MediaType.TEXT_HTML_VALUE)
    public Resource page() {
        return new ClassPathResource("static/ocr.html");
    }

    @PostMapping(value = "/api", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public OcrResponse recognizeMultipart(@RequestParam("image") MultipartFile file) throws JsonProcessingException {
        if (file == null || file.isEmpty()) {
            OcrResponse r = OcrResponse.failure("no file (use field name: image)");
            logJson(r);
            return r;
        }
        try {
            OcrResponse r = ocrService.recognize(file.getBytes());
            enrichPurchase(r);
            logJson(r);
            return r;
        } catch (Exception e) {
            OcrResponse r = OcrResponse.failure(e.getMessage());
            logJson(r);
            return r;
        }
    }

    @PostMapping(value = "/api", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public OcrResponse recognizeJson(@RequestBody OcrBase64Request body) throws JsonProcessingException {
        if (body == null || body.getImageBase64() == null || body.getImageBase64().isBlank()) {
            OcrResponse response = OcrResponse.failure("imageBase64 is required");
            logJson(response);
            return response;
        }
        try {
            byte[] bytes = decodeBase64Image(body.getImageBase64());
            OcrResponse r = ocrService.recognize(bytes);
            enrichPurchase(r);
            logJson(r);
            return r;
        } catch (IllegalArgumentException e) {
            OcrResponse r = OcrResponse.failure("invalid base64: " + e.getMessage());
            logJson(r);
            return r;
        } catch (Exception e) {
            OcrResponse r = OcrResponse.failure(e.getMessage());
            logJson(r);
            return r;
        }
    }

    /**
     * 금액을 햄버거·커피·두쫀쿠 등 설정 단가로 나눈 “N잔 / N개” 환산.
     */
    @GetMapping(value = "/api/units", produces = MediaType.APPLICATION_JSON_VALUE)
    public ConversionUnitsResponse listConversionUnits() {
        ConversionUnitsResponse res = new ConversionUnitsResponse();
        res.setUnits(compressionService.listSelectableUnits());
        return res;
    }

    @PostMapping(value = "/api/compress", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public CompressionResponse compress(@RequestBody CompressRequest body) throws JsonProcessingException {
        if (body == null || body.getPriceWon() == null || body.getPriceWon() <= 0) {
            CompressionResponse r = CompressionResponse.failure("priceWon is required and must be > 0");
            log.info("OCR compress:\n{}", objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(r));
            return r;
        }
        CompressionResponse r = compressionService.compress(
                body.getPriceWon(), body.getProductName(), body.getSelectedUnitIds());
        log.info("OCR compress:\n{}", objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(r));
        return r;
    }

    private void enrichPurchase(OcrResponse r) {
        if (!r.isOk() || r.getText() == null) {
            return;
        }

        // 1차: Gemini로 title + price 동시 추출
        GeminiRefineResult gr = geminiTitleRefiner.extractPurchaseFromOcr(r.getText());

        if (gr.isEnabled() && "ok".equalsIgnoreCase(gr.getNote())
                && gr.getOutputTitle() != null && !gr.getOutputTitle().isBlank()) {
            // Gemini 성공 → Gemini 결과 사용
            ParsedPurchase p = new ParsedPurchase();
            p.setProductName(gr.getOutputTitle());
            // Gemini가 가격도 추출했으면 사용, 아니면 Java 휴리스틱으로 보충
            if (gr.getOutputPriceWon() != null && gr.getOutputPriceWon() > 0) {
                p.setPriceWon(gr.getOutputPriceWon());
            } else {
                ParsedPurchase javaFallback = purchaseParser.parse(r.getText());
                p.setPriceWon(javaFallback.getPriceWon());
            }
            gr.setApplied(true);
            p.setGemini(gr);
            r.setExtractedPurchase(p);
        } else {
            // 2차 fallback: Java 휴리스틱
            ParsedPurchase p = purchaseParser.parse(r.getText());
            if (p == null) {
                p = ParsedPurchase.empty("추출 실패");
            }
            p.setProductNameRaw(p.getProductName());
            p.setGemini(gr);
            r.setExtractedPurchase(p);
        }
    }

    private void logJson(OcrResponse r) throws JsonProcessingException {
        String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(r);
        log.info("OCR result:\n{}", json);
    }

    private static byte[] decodeBase64Image(String raw) {
        String str = raw.trim();
        int comma = str.indexOf(',');
        if (str.startsWith("data:") && comma > 0) {
            str = str.substring(comma + 1);
        }
        return Base64.getDecoder().decode(str);
    }
}
