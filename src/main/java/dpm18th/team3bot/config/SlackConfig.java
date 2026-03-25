package dpm18th.team3bot.config;

import com.slack.api.Slack;
import com.slack.api.bolt.App;
import com.slack.api.bolt.AppConfig;
import dpm18th.team3bot.slack.AnnounceCommandHandler;
import dpm18th.team3bot.slack.MeetingCommandHandler;
import dpm18th.team3bot.slack.MeetingSummaryCommandHandler;
import com.slack.api.methods.MethodsClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SlackConfig {

    @Value("${slack.bot-token:}")
    private String botToken;

    @Value("${slack.signing-secret:}")
    private String signingSecret;

    @Bean
    public MethodsClient slackMethods() {
        // SlashCommand에서 사용하는 Slack Web API 호출을 위해 MethodsClient를 직접 주입합니다.
        // (SlashCommandContext에 getClient()가 없는 SDK 버전이라서 ctx.getClient 대신 사용)
        return Slack.getInstance().methods(botToken);
    }

    @Bean
    public App slackApp(
            MeetingCommandHandler meetingHandler,
            AnnounceCommandHandler announceHandler,
            MeetingSummaryCommandHandler summaryHandler) {
        AppConfig config = new AppConfig();
        config.setSingleTeamBotToken(botToken);
        config.setSigningSecret(signingSecret);

        App app = new App(config);

        app.command("/meeting", meetingHandler::handle);
        app.command("/announce", announceHandler::handle);
        app.command("/meeting-summary", summaryHandler::handle);
        app.command("/회의요약", summaryHandler::handle);

        return app;
    }
}
