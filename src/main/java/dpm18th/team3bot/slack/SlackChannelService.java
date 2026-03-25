package dpm18th.team3bot.slack;

import com.slack.api.methods.MethodsClient;
import dpm18th.team3bot.config.SlackChannelMappingProperties;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 대상(Target)에 따라 해당 Slack 채널에 회의 공지
 * Server → 3팀-server, Design → 3팀-design, Web → 3팀-web, 전체 → 3팀-전체
 */
@Service
public class SlackChannelService {

    private final Map<String, String> channelMapping;

    public SlackChannelService(SlackChannelMappingProperties props) {
        this.channelMapping = props.getChannelMapping();
    }

    /**
     * 대상에 해당하는 Slack 채널 ID 반환. 없으면 null
     */
    public String getChannelIdForTarget(String target) {
        if (target == null || target.isBlank()) return null;
        String normalized = normalizeTargetKey(target);

        String channel = channelMapping.entrySet().stream()
                .filter(e -> e.getKey().equalsIgnoreCase(normalized))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);

        if (channel == null || channel.isBlank()) {
            channel = channelMapping.entrySet().stream()
                    .filter(e -> e.getKey().equalsIgnoreCase("Test"))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElse(null);
        }
        return channel;
    }

    private String normalizeTargetKey(String raw) {
        String t = raw.trim();
        String low = t.toLowerCase();
        return switch (low) {
            case "server", "서버" -> "Server";
            case "design", "디자인" -> "Design";
            case "web", "웹" -> "Web";
            case "전체", "all" -> "전체";
            case "test", "테스트" -> "Test";
            default -> t;
        };
    }

    /**
     * 해당 채널에 회의 공지 (노이즈 최소화: 제목 + 링크만)
     */
    public void postMeetingNotice(MethodsClient client, String channelId, String title, String notionUrl) {
        if (channelId == null || channelId.isBlank()) return;
        try {
            String text = String.format("📋 *%s*\n<%s|Notion에서 보기>", title, notionUrl);
            client.chatPostMessage(r -> r
                    .channel(channelId)
                    .text(text)
                    .mrkdwn(true));
        } catch (Exception e) {
            // 채널 없거나 권한 없으면 무시 (노이즈 방지)
        }
    }
}
