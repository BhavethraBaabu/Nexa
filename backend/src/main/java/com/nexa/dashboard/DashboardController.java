package com.nexa.dashboard;

import com.nexa.action.ActionStatus;
import com.nexa.action.AiActionRepository;
import com.nexa.common.security.AuthenticatedUser;
import com.nexa.decision.DecisionRepository;
import com.nexa.meeting.MeetingRepository;
import com.nexa.meeting.MeetingStatus;
import com.nexa.task.TaskRepository;
import com.nexa.task.TaskStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;

/** Dashboard counts and recent items (PRD sections 24 and 44), scoped to the caller's organization. */
@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final MeetingRepository meetingRepository;
    private final TaskRepository taskRepository;
    private final DecisionRepository decisionRepository;
    private final AiActionRepository actionRepository;
    private final Clock clock;

    public DashboardController(MeetingRepository meetingRepository, TaskRepository taskRepository,
                               DecisionRepository decisionRepository, AiActionRepository actionRepository, Clock clock) {
        this.meetingRepository = meetingRepository;
        this.taskRepository = taskRepository;
        this.decisionRepository = decisionRepository;
        this.actionRepository = actionRepository;
        this.clock = clock;
    }

    public record DashboardResponse(long meetingsThisWeek, long totalMeetings, long actionItems, long completedTasks,
                                    long overdueTasks, long pendingApprovals,
                                    List<RecentMeeting> recentMeetings, List<RecentDecision> recentDecisions) {
    }

    public record RecentMeeting(UUID id, String title, LocalDate meetingDate, MeetingStatus status) {
    }

    public record RecentDecision(UUID id, UUID meetingId, String decision) {
    }

    @GetMapping
    @Transactional(readOnly = true)
    public DashboardResponse dashboard(@AuthenticationPrincipal AuthenticatedUser current) {
        UUID org = current.organizationId();
        LocalDate today = LocalDate.now(clock);
        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return new DashboardResponse(
                meetingRepository.countByOrganizationIdAndMeetingDateBetween(org, monday, monday.plusDays(6)),
                meetingRepository.countByOrganizationId(org),
                taskRepository.countByOrganizationIdAndStatusNot(org, TaskStatus.CANCELLED),
                taskRepository.countByOrganizationIdAndStatus(org, TaskStatus.DONE),
                taskRepository.countOverdue(org, today),
                actionRepository.countByOrganizationIdAndStatus(org, ActionStatus.PENDING),
                meetingRepository.findTop5ByOrganizationIdOrderByMeetingDateDescCreatedAtDesc(org).stream()
                        .map(m -> new RecentMeeting(m.getId(), m.getTitle(), m.getMeetingDate(), m.getStatus()))
                        .toList(),
                decisionRepository.findTop5ByOrganizationIdOrderByCreatedAtDescPositionAsc(org).stream()
                        .map(d -> new RecentDecision(d.getId(), d.getMeetingId(), d.getDecision()))
                        .toList());
    }
}
