package za.co.handyflow.platform.projects.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.projects.domain.repository.ProjectRiskRepository;
import za.co.handyflow.platform.projects.domain.repository.ProjectTaskRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Runs daily in SAST (Africa/Johannesburg) to detect and notify on:
 *  1. Overdue milestones  — planned_end < today and status != COMPLETED
 *  2. Red risk escalations — rating='RED' and updated_at in last 24 h
 *
 * Notification failures are swallowed by PmNotificationService (@Async + try-catch).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PmScheduledChecks {

    private final ProjectTaskRepository     taskRepo;
    private final ProjectRiskRepository     riskRepo;
    private final PmNotificationService     notificationService;

    // ── Overdue milestones — runs at 07:00 SAST every day ────────────────────

    @Scheduled(cron = "0 0 7 * * *", zone = "Africa/Johannesburg")
    @Transactional(readOnly = true)
    public void checkOverdueMilestones() {
        log.info("[PM] Running overdue milestone check");
        LocalDate today = LocalDate.now();

        taskRepo.findOverdueMilestones(today).forEach(row -> {
            try {
                notificationService.notifyMilestoneOverdue(
                        (java.util.UUID) row[0],    // tenantId
                        (String) row[1],             // projectName
                        (String) row[2],             // milestoneTitle
                        row[3].toString()            // plannedEnd
                );
            } catch (Exception e) {
                log.error("[PM] Error processing overdue milestone notification: {}", e.getMessage());
            }
        });
    }

    // ── Red risk escalations — runs at 08:00 SAST every day ─────────────────

    @Scheduled(cron = "0 0 8 * * *", zone = "Africa/Johannesburg")
    @Transactional(readOnly = true)
    public void checkRiskEscalations() {
        log.info("[PM] Running risk escalation check");
        LocalDateTime since = LocalDateTime.now().minusHours(24);

        riskRepo.findRecentlyEscalated(since).forEach(row -> {
            try {
                notificationService.notifyRiskEscalated(
                        (java.util.UUID) row[0],    // tenantId
                        (String) row[1],             // projectName
                        (String) row[2],             // riskTitle
                        (String) row[3]              // rating
                );
            } catch (Exception e) {
                log.error("[PM] Error processing risk escalation notification: {}", e.getMessage());
            }
        });
    }
}
