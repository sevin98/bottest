package dpm18th.team3bot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 대상 → Slack 채널 ID 매핑
 * slack.channel-mapping.Server=Cxxx (3팀-server)
 * slack.channel-mapping.Design=Cyyy (3팀-design)
 * slack.channel-mapping.Web=Czzz (3팀-web)
 * slack.channel-mapping.전체=Cwww (3팀-전체)
 */
@Component
@ConfigurationProperties(prefix = "slack")
public class SlackChannelMappingProperties {

    private Map<String, String> channelMapping = new HashMap<>();

    public Map<String, String> getChannelMapping() {
        return channelMapping;
    }

    public void setChannelMapping(Map<String, String> channelMapping) {
        this.channelMapping = channelMapping != null ? channelMapping : new HashMap<>();
    }
}
