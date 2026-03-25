package dpm18th.team3bot.slack;

import com.slack.api.bolt.context.builtin.SlashCommandContext;
import com.slack.api.bolt.request.builtin.SlashCommandRequest;
import com.slack.api.bolt.response.Response;
import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.SlackApiException;
import dpm18th.team3bot.notion.MeetingCreateRequest;
import dpm18th.team3bot.notion.MeetingCreateResult;
import dpm18th.team3bot.notion.NotionService;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class MeetingCommandHandler {

    private final NotionService notionService;
    private final SlackChannelService slackChannelService;
    private final MethodsClient slackMethods;

    public MeetingCommandHandler(
            NotionService notionService,
            SlackChannelService slackChannelService,
            MethodsClient slackMethods) {
        this.notionService = notionService;
        this.slackChannelService = slackChannelService;
        this.slackMethods = slackMethods;
    }

    public Response handle(SlashCommandRequest req, SlashCommandContext ctx) throws IOException, SlackApiException {
        String text = req.getPayload().getText();
        if (text == null) text = "";

        String[] parts = text.split("\\|", -1);
        String title = parts.length >= 1 ? parts[0].trim() : "";
        String target = parts.length >= 2 ? parts[1].trim() : "";
        String scribe = parts.length >= 3 ? parts[2].trim() : "";
        List<String> participants = parts.length >= 4 ? parseParticipants(parts[3]) : List.of();
        String agenda = parts.length >= 5 ? parts[4].trim() : "";
        String date = null;
        String time = null;

        MeetingCreateRequest request = MeetingCreateRequest.builder()
                .title(title.isEmpty() ? "새 회의" : title)
                .target(target.isEmpty() ? null : target)
                .scribe(scribe.isEmpty() ? null : scribe)
                .participants(participants)
                .date(date)
                .time(time)
                .agenda(agenda.isEmpty() ? null : agenda)
                .build();

        String userId = req.getPayload().getUserId();
        String channelId = req.getPayload().getChannelId();

        notionService.createMeetingPage(request)
                .subscribe(
                        result -> {
                            try {
                                String msg = "✅ Notion 회의 페이지가 생성되었습니다!\n" + result.getNotionUrl();
                                slackMethods.chatPostEphemeral(r -> r
                                        .channel(channelId)
                                        .user(userId)
                                        .text(msg));

                                String targetChannelId = slackChannelService.getChannelIdForTarget(result.getTarget());
                                if (targetChannelId != null && !targetChannelId.isBlank()) {
                                    slackChannelService.postMeetingNotice(
                                            slackMethods,
                                            targetChannelId,
                                            request.getTitle(),
                                            result.getNotionUrl());
                                }
                            } catch (Exception e) {
                            }
                        },
                        err -> {
                            try {
                                slackMethods.chatPostEphemeral(r -> r
                                        .channel(channelId)
                                        .user(userId)
                                        .text("❌ 생성 실패: " + err.getMessage()));
                            } catch (Exception ignored) {}
                        }
                );

        return ctx.ack(r -> r.responseType("ephemeral").text("회의 페이지 생성 중..."));
    }

    private List<String> parseParticipants(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        return Arrays.stream(raw.split("[,，]"))
                .map(String::trim)
                .map(s -> s.replaceAll("<@([A-Z0-9]+)>", ""))
                .filter(s -> !s.isBlank())
                .collect(Collectors.toList());
    }
}
