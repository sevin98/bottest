package dpm18th.team3bot.slack;

import com.slack.api.bolt.context.builtin.SlashCommandContext;
import com.slack.api.bolt.request.builtin.SlashCommandRequest;
import com.slack.api.bolt.response.Response;
import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.SlackApiException;
import dpm18th.team3bot.notion.NotionService;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class AnnounceCommandHandler {

    private final NotionService notionService;
    private final MethodsClient slackMethods;

    public AnnounceCommandHandler(NotionService notionService, MethodsClient slackMethods) {
        this.notionService = notionService;
        this.slackMethods = slackMethods;
    }

    public Response handle(SlashCommandRequest req, SlashCommandContext ctx) throws IOException, SlackApiException {
        String text = req.getPayload().getText();
        if (text == null) {
            text = "";
        }

        String title;
        String content;
        int firstNewline = text.indexOf('\n');
        if (firstNewline > 0) {
            title = text.substring(0, firstNewline).trim();
            content = text.substring(firstNewline + 1).trim();
        } else {
            int firstPipe = text.indexOf('|');
            if (firstPipe > 0) {
                title = text.substring(0, firstPipe).trim();
                content = text.substring(firstPipe + 1).trim();
            } else {
                title = text.isBlank() ? "새 공지" : text.trim();
                content = "";
            }
        }

        String userId = req.getPayload().getUserId();

        notionService.createAnnouncementPage(title, content)
                .subscribe(
                        url -> {
                            try {
                                String msg = "✅ Notion 공지 페이지가 생성되었습니다!\n" + url;
                                slackMethods.chatPostEphemeral(r -> r
                                        .channel(req.getPayload().getChannelId())
                                        .user(userId)
                                        .text(msg));
                            } catch (Exception e) {
                            }
                        },
                        err -> {
                            try {
                                slackMethods.chatPostEphemeral(r -> r
                                        .channel(req.getPayload().getChannelId())
                                        .user(userId)
                                        .text("❌ 생성 실패: " + err.getMessage()));
                            } catch (Exception ignored) {}
                        }
                );

        return ctx.ack(r -> r.responseType("ephemeral").text("공지 페이지 생성 중..."));
    }
}
