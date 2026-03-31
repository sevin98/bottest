package dpm18th.team3bot.ocr;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
@ConditionalOnProperty(prefix = "ocr", name = "enabled", havingValue = "true")
public class OcrService {

    private static final Logger log = LoggerFactory.getLogger(OcrService.class);

    private final Tesseract tesseract;
    private final String language;
    private final Path tessdataDir;

    public OcrService(OcrProperties props) {
        this.tesseract = new Tesseract();
        this.language = props.getLanguage() != null ? props.getLanguage().trim() : "kor+eng";
        String configured = props.getDataPath();
        if (configured != null && !configured.isBlank()) {
            this.tessdataDir = Path.of(configured.trim()).toAbsolutePath().normalize();
        } else {
            this.tessdataDir = Path.of("").toAbsolutePath().resolve("tessdata").normalize();
        }
        try {
            Files.createDirectories(tessdataDir);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create tessdata directory: " + tessdataDir, e);
        }
        if (props.isAutoDownloadTrainedData()) {
            try {
                TessdataDownloader.ensureLanguages(tessdataDir, language);
            } catch (IOException e) {
                throw new IllegalStateException(
                        "Could not download tessdata into " + tessdataDir
                                + ". Check network or firewall, run ./gradlew downloadTessdata,"
                                + " copy *.traineddata manually, or set OCR_AUTO_DOWNLOAD_TESSDATA=false"
                                + " with OCR_DATA_PATH pointing to a full tessdata folder.",
                        e);
            }
        }
        tesseract.setDatapath(tessdataDir.toString());
        tesseract.setLanguage(language);
        validateLanguageFiles(tessdataDir, language);
        log.info("OCR tessdata path={}, language={}", tessdataDir, language);
    }

    private static void validateLanguageFiles(Path tessDir, String langSpec) {
        String[] langs = langSpec.split("\\+");
        for (String raw : langs) {
            String lang = raw.trim();
            if (lang.isEmpty()) {
                continue;
            }
            Path trained = tessDir.resolve(lang + ".traineddata");
            if (!Files.isRegularFile(trained) || fileSizeOrZero(trained) == 0) {
                throw new IllegalStateException(
                        "Missing or empty language data: " + trained.getFileName()
                                + " in " + tessDir
                                + ". Enable ocr.auto-download-trained-data, run ./gradlew downloadTessdata,"
                                + " or copy " + lang + ".traineddata from a Tesseract install.");
            }
        }
    }

    private static long fileSizeOrZero(Path p) {
        try {
            return Files.size(p);
        } catch (IOException e) {
            return 0;
        }
    }

    public OcrResponse recognize(byte[] imageBytes) {
        long t0 = System.currentTimeMillis();
        if (imageBytes == null || imageBytes.length == 0) {
            return OcrResponse.failure("empty image");
        }
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (img == null) {
                return OcrResponse.failure("could not decode image (unsupported or corrupt format)");
            }
            String text = tesseract.doOCR(img);
            long ms = System.currentTimeMillis() - t0;
            return OcrResponse.success(text, language, ms);
        } catch (TesseractException e) {
            long ms = System.currentTimeMillis() - t0;
            OcrResponse r = OcrResponse.failure("tesseract: " + e.getMessage());
            r.setProcessingMs(ms);
            return r;
        } catch (IOException e) {
            return OcrResponse.failure("io: " + e.getMessage());
        }
    }
}
