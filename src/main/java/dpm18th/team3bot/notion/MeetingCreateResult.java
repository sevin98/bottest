package dpm18th.team3bot.notion;

import lombok.Value;

@Value
public class MeetingCreateResult {
    String notionUrl;
    String target;  // Slack 채널 매핑용
}
