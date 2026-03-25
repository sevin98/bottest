package dpm18th.team3bot.slack;

import com.slack.api.Slack;
import com.slack.api.bolt.context.builtin.SlashCommandContext;
import com.slack.api.bolt.request.builtin.SlashCommandRequest;
import com.slack.api.bolt.response.Response;
import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.request.chat.ChatPostMessageRequest;
import dpm18th.team3bot.notion.NotionMeetingQueryService;
import dpm18th.team3bot.notion.NotionPageContentService;
import dpm18th.team3bot.notion.NotionPageHit;
import dpm18th.team3bot.notion.NotionPageIdParser;
import dpm18th.team3bot.summary.MeetingNotesSummarizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Component
public class MeetingSummaryCommandHandler {

    private final NotionMeetingQueryService queryService;
    private final NotionPageContentService pageContentService;
    private final MeetingNotesSummarizer summarizer;
    private final MethodsClient slackMethods;

    public MeetingSummaryCommandHandler(
            NotionMeetingQueryService queryService,
            NotionPageContentService pageContentService,
            MeetingNotesSummarizer summarizer,
            @Value("${slack.bot-token:}") String botToken) {
        this.queryService = queryService;
        this.pageContentService = pageContentService;
        this.summarizer = summarizer;
        this.slackMethods = Slack.getInstance().methods(botToken != null ? botToken : "");
    }

    public Response handle(SlashCommandRequest req, SlashCommandContext ctx) throws IOException, SlackApiException {
        String text = req.getPayload().getText();
        if (text == null) text = "";
        text = text.trim();

        String channelId = req.getPayload().getChannelId();
        String userId = req.getPayload().getUserId();

        if (text.isBlank()) {
            return ctx.ack(r -> r.responseType("ephemeral").text(
                    "사용법: 노션 페이지 URL 또는 페이지 ID, 또는 회의 제목 일부를 입력하세요.\n"
                            + "예: `/meeting-summary 스프린트` 또는 `/meeting-summary https://www.notion.so/...`"));
        }

        Optional<String> pageIdOpt = NotionPageIdParser.parse(text);
        if (pageIdOpt.isPresent()) {
            runSummaryAsync(pageIdOpt.get(), channelId, userId, null);
            return ctx.ack(r -> r.responseType("ephemeral").text("회의록 요약 중… 잠시만 기다려 주세요."));
        }

        List<NotionPageHit> hits = queryService.searchByTitleContains(text).block();
        if (hits == null || hits.isEmpty()) {
            return ctx.ack(r -> r.responseType("ephemeral").text(
                    "검색 결과가 없습니다. 제목 키워드를 바꿔 보거나, 노션 페이지 링크·ID를 붙여 넣어 주세요."));
        }
        if (hits.size() == 1) {
            NotionPageHit h = hits.get(0);
            runSummaryAsync(h.pageId(), channelId, userId, h.title());
            return ctx.ack(r -> r.responseType("ephemeral").text("회의록 요약 중… 잠시만 기다려 주세요."));
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%d개가 검색됐어요. 아래 중 맞는 페이지의 *링크*를 복사해 다시 실행하거나, 더 구체적인 제목을 입력하세요.\n\n", hits.size()));
        int n = Math.min(hits.size(), 8);
        for (int i = 0; i < n; i++) {
            NotionPageHit h = hits.get(i);
            sb.append(String.format("%d. *%s*\n<%s|열기>\n", i + 1, h.title(), h.pageUrl().isBlank() ? h.pageId() : h.pageUrl()));
        }
        if (hits.size() > n) {
            sb.append("\n…외 ").append(hits.size() - n).append("건");
        }
        return ctx.ack(r -> r.responseType("ephemeral").text(sb.toString()));
    }

    private void runSummaryAsync(String pageId, String channelId, String userId, String knownTitle) {
        new Thread(() -> {
            try {
                NotionPageHit meta = queryService.retrievePageHit(pageId).blockOptional()
                        .orElse(new NotionPageHit(pageId, knownTitle != null ? knownTitle : "(회의)", ""));
                String title = meta.title();
                String url = meta.pageUrl();
                if (url == null || url.isBlank()) {
                    url = "https://www.notion.so/" + pageId.replace("-", "");
                }

                String raw = pageContentService.fetchPlainTextBlocking(pageId);
                String summary = summarizer.summarize(raw);
                String msg = String.format(
                        ":memo: *회의록 요약 (수동)* — %s\n<%s|원문 노션>\n\n%s",
                        title, url, summary);

                slackMethods.chatPostMessage(ChatPostMessageRequest.builder()
                        .channel(channelId)
                        .text(msg)
                        .mrkdwn(true)
                        .build());

                slackMethods.chatPostEphemeral(r -> r
                        .channel(channelId)
                        .user(userId)
                        .text("요약을 이 채널에 올렸어요."));
            } catch (Exception e) {
                try {
                    slackMethods.chatPostEphemeral(r -> r
                            .channel(channelId)
                            .user(userId)
                            .text("요약 실패: " + e.getMessage()));
                } catch (Exception ignored) {}
            }
        }, "meeting-summary").start();
    }
}
