package dpm18th.team3bot.notion;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;

@Value
@Builder
public class NotionMeetingSnapshot {
    String pageId;
    String title;
    String pageUrl;
    Instant createdTime;
    ZonedDateTime start;
    /** 노션 날짜 속성의 end. 없으면 start + 기본 회의 시간으로 종료 시각 계산 */
    ZonedDateTime end;
    String target;
    List<String> participantNames;
    /** 회의 일정/취소 등 */
    String status;
    /** 회의록 작성 상태 (예: DONE) */
    String notesStatus;
}
