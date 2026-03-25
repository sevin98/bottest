package dpm18th.team3bot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class Team3BotApplication {

    public static void main(String[] args) {
        SpringApplication.run(Team3BotApplication.class, args);
    }
}
