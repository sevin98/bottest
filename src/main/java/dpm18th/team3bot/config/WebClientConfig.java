package dpm18th.team3bot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    /**
     * 생성자 주입용 WebClient.Builder 빈을 명시적으로 제공
     * (환경에 따라 spring-webflux auto-config이 누락되는 경우를 방지)
     */
    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }
}

