package dpm18th.team3bot.reminder;

import com.slack.api.Slack;
import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.request.chat.ChatPostMessageRequest;
import dpm18th.team3bot.notion.NotionMeetingQueryService;
import dpm18th.team3bot.notion.NotionMeetingSnapshot;
import dpm18th.team3bot.notion.NotionPageContentService;
import dpm18th.team3bot.slack.SlackChannelService;
import dpm18th.team3bot.summary.MeetingNotesSummarizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 노션 DB 1회 스캔으로 처리: 신규 회의 알림, 리마인더 예약, 회의록 요약 알림
 */
@Component
public class NotionMeetingLifecycleScheduler {

    private static final Logger log = LoggerFactory.getLogger(NotionMeetingLifecycleScheduler.class);

    private final NotionMeetingQueryService queryService;
    private final NotionPageContentService pageContentService;
    private final MeetingNotesSummarizer summarizer;
    private final SlackChannelService channelService;
    private final TaskScheduler taskScheduler;
    private final MethodsClient slackMethods;

    private final boolean enabled;
    private final boolean creationEnabled;
    private final int creationWindowHours;
    private final boolean summaryEnabled;
    private final String summaryDoneValue;
    private final int summaryDelayAfterEndMinutes;
    private final int meetingDefaultDurationMinutes;
    private final Set<String> skipStatuses;
    private final ZoneId zoneId;

    private final Set<String> scheduledReminderKeys = ConcurrentHashMap.newKeySet();
    private final Set<String> creationAnnounced = ConcurrentHashMap.newKeySet();
    private final Set<String> summaryPosted = ConcurrentHashMap.newKeySet();

    public NotionMeetingLifecycleScheduler(
            NotionMeetingQueryService queryService,
            NotionPageContentService pageContentService,
            MeetingNotesSummarizer summarizer,
            SlackChannelService channelService,
            TaskScheduler taskScheduler,
            @Value("${slack.bot-token:}") String botToken,
            @Value("${reminder.enabled:true}") boolean enabled,
            @Value("${notion.notify.creation-enabled:true}") boolean creationEnabled,
            @Value("${notion.notify.creation-window-hours:72}") int creationWindowHours,
            @Value("${notes.summary.enabled:true}") boolean summaryEnabled,
            @Value("${notes.summary.done-value:DONE}") String summaryDoneValue,
            @Value("${notes.summary.delay-after-end-minutes:30}") int summaryDelayAfterEndMinutes,
            @Value("${notes.meeting-default-duration-minutes:60}") int meetingDefaultDurationMinutes,
            @Value("${reminder.skip-statuses:미정,취소 및 변경}") String skipStatusesCsv,
            @Value("${reminder.timezone:Asia/Seoul}") String timezone) {
        this.queryService = queryService;
        this.pageContentService = pageContentService;
        this.summarizer = summarizer;
        this.channelService = channelService;
        this.taskScheduler = taskScheduler;
        this.slackMethods = Slack.getInstance().methods(botToken != null ? botToken : "");
        this.enabled = enabled;
        this.creationEnabled = creationEnabled;
        this.creationWindowHours = creationWindowHours;
        this.summaryEnabled = summaryEnabled;
        this.summaryDoneValue = summaryDoneValue.trim();
        this.summaryDelayAfterEndMinutes = summaryDelayAfterEndMinutes;
        this.meetingDefaultDurationMinutes = meetingDefaultDurationMinutes;
        this.skipStatuses = Arrays.stream(skipStatusesCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
        this.zoneId = ZoneId.of(timezone);
    }

    @Scheduled(fixedDelayString = "${reminder.scan-interval-ms:300000}", initialDelayString = "${reminder.scan-initial-delay-ms:15000}")
    public void scan() {
        if (!enabled) return;
        try {
            List<NotionMeetingSnapshot> meetings = queryService.queryAllMeetings().block();
            if (meetings == null) return;
            log.info("노션 스캔: meetings.size={}", meetings.size());
            Instant now = Instant.now();
            for (NotionMeetingSnapshot m : meetings) {
                if (creationEnabled) tryCreationNotify(m, now);
                if (!shouldSkipReminders(m, now)) scheduleReminders(m, now);
                if (summaryEnabled) tryMeetingNotesSummary(m, now);
            }
        } catch (Exception e) {
            log.warn("노션 회의 스캔 실패: {}", e.getMessage());
        }
    }

    private void tryCreationNotify(NotionMeetingSnapshot m, Instant now) {
        Instant start = m.getStart().toInstant();
        if (!start.isAfter(now)) return;
        if (m.getStatus() != null && !m.getStatus().isBlank()
                && skipStatuses.contains(m.getStatus().trim())) {
            return;
        }
        if (!m.getCreatedTime().isAfter(now.minus(Duration.ofHours(creationWindowHours)))) return;

        String channelId = channelService.getChannelIdForTarget(m.getTarget());
        if (channelId == null || channelId.isBlank()) {
            log.debug("신규 회의 알림 스킵(채널 없음): {}", m.getTitle());
            return;
        }
        if (!creationAnnounced.add(m.getPageId())) return;
        try {
            String when = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.KOREA)
                    .withZone(zoneId)
                    .format(start);
            String participants = m.getParticipantNames().isEmpty()
                    ? "(미정)"
                    : String.join(", ", m.getParticipantNames());
            String text = String.format(
                    ":spiral_calendar_pad: *새 회의가 잡혔어요*\n"
                            + "> *%s*\n"
                            + "> 일시: `%s`\n"
                            + "> 참여자: %s\n"
                            + "> <%s|노션에서 보기>",
                    m.getTitle(), when, participants, m.getPageUrl());
            slackMethods.chatPostMessage(ChatPostMessageRequest.builder()
                    .channel(channelId)
                    .text(text)
                    .mrkdwn(true)
                    .build());
        } catch (Exception e) {
            creationAnnounced.remove(m.getPageId());
            log.warn("신규 회의 알림 실패 {}: {}", m.getTitle(), e.getMessage());
        }
    }

    private boolean shouldSkipReminders(NotionMeetingSnapshot m, Instant now) {
        if (!m.getStart().toInstant().isAfter(now)) return true;
        if (m.getStatus() != null && !m.getStatus().isBlank()
                && skipStatuses.contains(m.getStatus().trim())) {
            return true;
        }
        return false;
    }

    private void scheduleReminders(NotionMeetingSnapshot m, Instant now) {
        Instant meetingStart = m.getStart().toInstant();
        scheduleReminderIfNeeded(m, ReminderKind.ONE_DAY, meetingStart.minus(Duration.ofDays(1)), now);
        scheduleReminderIfNeeded(m, ReminderKind.FIVE_HOURS, meetingStart.minus(Duration.ofHours(5)), now);
        scheduleReminderIfNeeded(m, ReminderKind.TEN_MINUTES, meetingStart.minus(Duration.ofMinutes(10)), now);
    }

    private void scheduleReminderIfNeeded(NotionMeetingSnapshot m, ReminderKind kind, Instant fireAt, Instant now) {
        if (!fireAt.isAfter(now)) return;
        String key = m.getPageId() + ":" + kind.name();
        if (!scheduledReminderKeys.add(key)) return;
        taskScheduler.schedule(() -> sendReminder(m, kind), fireAt);
    }

    private void sendReminder(NotionMeetingSnapshot m, ReminderKind kind) {
        try {
            if (m.getStatus() != null && !m.getStatus().isBlank()
                    && skipStatuses.contains(m.getStatus().trim())) {
                return;
            }
            String channelId = channelService.getChannelIdForTarget(m.getTarget());
            if (channelId == null || channelId.isBlank()) return;
            String when = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.KOREA)
                    .withZone(zoneId)
                    .format(m.getStart().toInstant());
            String participants = m.getParticipantNames().isEmpty()
                    ? "(없음)"
                    : String.join(", ", m.getParticipantNames());
            String label = switch (kind) {
                case ONE_DAY -> "1일 전";
                case FIVE_HOURS -> "5시간 전";
                case TEN_MINUTES -> "10분 전";
            };
            String text = String.format(
                    ":bell: *[%s]* %s에 \"%s\" 회의가 예정되어 있어요.\n"
                            + "> 일시: `%s`\n"
                            + "> 참여자: %s\n"
                            + "> 노션: <%s|바로가기>",
                    label, when, m.getTitle(), when, participants, m.getPageUrl());
            slackMethods.chatPostMessage(ChatPostMessageRequest.builder()
                    .channel(channelId)
                    .text(text)
                    .mrkdwn(true)
                    .build());
        } catch (Exception e) {
            log.warn("리마인더 전송 실패 {}: {}", m.getTitle(), e.getMessage());
        }
    }

    private void tryMeetingNotesSummary(NotionMeetingSnapshot m, Instant now) {
        if (m.getStatus() != null && !m.getStatus().isBlank()
                && skipStatuses.contains(m.getStatus().trim())) {
            return;
        }
        boolean doneFlag = m.getNotesStatus() != null
                && summaryDoneValue.equalsIgnoreCase(m.getNotesStatus().trim());

        ZonedDateTime end = m.getEnd();
        if (end == null) {
            end = m.getStart().plusMinutes(meetingDefaultDurationMinutes);
        }
        Instant summaryAfterTime = end.toInstant().plus(Duration.ofMinutes(summaryDelayAfterEndMinutes));
        boolean timeEligible = now.isAfter(summaryAfterTime) || now.equals(summaryAfterTime);

        if (!doneFlag && !timeEligible) return;

        String sumChannelId = channelService.getChannelIdForTarget(m.getTarget());
        if (sumChannelId == null || sumChannelId.isBlank()) return;
        if (!summaryPosted.add(m.getPageId())) return;

        taskScheduler.schedule(() -> runSummaryJob(m, sumChannelId), Instant.now().plusSeconds(2));
    }

    private void runSummaryJob(NotionMeetingSnapshot m, String channelId) {
        try {
            String raw = pageContentService.fetchPlainTextBlocking(m.getPageId());
            String summary = summarizer.summarize(raw);
            String text = String.format(
                    ":memo: *회의록 요약* — %s\n<%s|원문 노션>\n\n%s",
                    m.getTitle(), m.getPageUrl(), summary);
            slackMethods.chatPostMessage(ChatPostMessageRequest.builder()
                    .channel(channelId)
                    .text(text)
                    .mrkdwn(true)
                    .build());
        } catch (Exception e) {
            summaryPosted.remove(m.getPageId());
            log.warn("회의록 요약 전송 실패 {}: {}", m.getTitle(), e.getMessage());
        }
    }

    private enum ReminderKind {
        ONE_DAY, FIVE_HOURS, TEN_MINUTES
    }
}
