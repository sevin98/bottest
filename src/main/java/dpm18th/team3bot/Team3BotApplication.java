package dpm18th.team3bot;

import dpm18th.team3bot.ocr.OcrProperties;
import dpm18th.team3bot.ocr.OcrPurchaseParseProperties;
import dpm18th.team3bot.ocr.OcrSpendConversionProperties;
import dpm18th.team3bot.gemini.GeminiProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({
        OcrProperties.class,
        OcrSpendConversionProperties.class,
        OcrPurchaseParseProperties.class,
        GeminiProperties.class
})
public class Team3BotApplication {

    public static void main(String[] args) {
        SpringApplication.run(Team3BotApplication.class, args);
    }
}
