package dpm18th.team3bot.ocr;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * tessdata 저장소에서 *.traineddata 가 없으면 내려받습니다 (IDE 실행 시 Gradle 태스크 없이 동작).
 */
public final class TessdataDownloader {

    private static final Logger log = LoggerFactory.getLogger(TessdataDownloader.class);
    private static final String BASE = "https://github.com/tesseract-ocr/tessdata/raw/main/";
    private static final Duration TIMEOUT = Duration.ofMinutes(10);

    private TessdataDownloader() {}

    public static void ensureLanguages(Path tessDir, String languageSpec) throws IOException {
        Files.createDirectories(tessDir);
        List<String> langs = new ArrayList<>();
        for (String raw : languageSpec.split("\\+")) {
            String lang = raw.trim();
            if (!lang.isEmpty()) {
                langs.add(lang);
            }
        }
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        for (String lang : langs) {
            Path dest = tessDir.resolve(lang + ".traineddata");
            if (Files.isRegularFile(dest) && Files.size(dest) > 0) {
                continue;
            }
            downloadOne(client, lang, dest);
        }
    }

    private static void downloadOne(HttpClient client, String lang, Path dest) throws IOException {
        String url = BASE + lang + ".traineddata";
        log.info("Downloading tessdata {} -> {}", lang, dest.toAbsolutePath());
        Path part = dest.resolveSibling(dest.getFileName().toString() + ".part");
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(TIMEOUT)
                    .header("User-Agent", "team3Bot/ocr (Java HttpClient)")
                    .GET()
                    .build();
            HttpResponse<InputStream> res = client.send(req, HttpResponse.BodyHandlers.ofInputStream());
            if (res.statusCode() / 100 != 2) {
                throw new IOException("HTTP " + res.statusCode() + " for " + url);
            }
            try (InputStream in = res.body(); OutputStream out = Files.newOutputStream(part)) {
                in.transferTo(out);
            }
            long n = Files.size(part);
            if (n < 1024) {
                throw new IOException("Downloaded file too small (" + n + " bytes), likely not a valid traineddata");
            }
            Files.move(part, dest, StandardCopyOption.REPLACE_EXISTING);
            log.info("Saved {} ({} bytes)", dest.getFileName(), n);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while downloading " + lang, e);
        } finally {
            try {
                Files.deleteIfExists(part);
            } catch (IOException ignored) {
                // ignore
            }
        }
    }
}
