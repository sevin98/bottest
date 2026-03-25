package dpm18th.team3bot.notion;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class MeetingCreateRequest {
    String title;
    String target;       // 대상: Server, Design, Web, 전체
    String scribe;       // 서기
    List<String> participants;  // 참여자
    String date;         // yyyy-MM-dd, null이면 로컬 오늘
    String time;         // HH:mm
    String agenda;
}
