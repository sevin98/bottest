package dpm18th.team3bot.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * .env 파일을 로드하여 Spring Environment에 주입
 * 프로젝트 루트의 .env 파일 사용
 */
public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final String DOTENV_SOURCE = "dotenv";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Path envPath = Path.of(".env");
        if (!Files.exists(envPath)) return;

        Map<String, Object> map = new HashMap<>();
        try {
            Files.lines(envPath).forEach(line -> {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) return;
                int eq = line.indexOf('=');
                if (eq <= 0) return;
                String key = line.substring(0, eq).trim();
                String value = line.substring(eq + 1).trim();
                if (value.startsWith("\"") && value.endsWith("\"")) {
                    value = value.substring(1, value.length() - 1).replace("\\\"", "\"");
                } else if (value.startsWith("'") && value.endsWith("'")) {
                    value = value.substring(1, value.length() - 1).replace("\\'", "'");
                }
                map.put(key, value);
            });
        } catch (IOException e) {
            return;
        }
        if (!map.isEmpty()) {
            environment.getPropertySources().addFirst(new MapPropertySource(DOTENV_SOURCE, map));
        }
    }
}
