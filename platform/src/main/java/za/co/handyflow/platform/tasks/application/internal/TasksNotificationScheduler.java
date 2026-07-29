package za.co.handyflow.platform.tasks.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.tasks.domain.model.Task;
import za.co.handyflow.platform.tasks.domain.repository.TaskRepository;

import java.time.LocalDate;
import java.util.List;

/**
 * The two time-driven task notifications the gap analysis flagged as
 * missing — nothing "happens" to trigger either one, a clock does, same
 * division of responsibility as FleetNotificationScheduler /
 * CreativeNotificationScheduler: query, resolve a recipient, delegate to
 * NotificationService.send() for the actual dispatch.
 * <p>
 * Assignment and comment notifications are NOT here — those are
 * request-driven (something happens: a task gets assigned, a comment gets
 * posted) so they're fired directly from TasksService at the point of the
 * action, exactly like every other module's request-driven notifications.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TasksNotificationScheduler {

    /** Exact-day-match lead times — same idempotency style as Fleet's compliance alerts. */
    private static final int[] DUE_SOON_ALERT_DAYS = {3, 1};

    private final TaskRepository taskRepo;
    private final TasksService tasksService;
    private final NotificationService notificationService;

    // ── Due-soon reminder — daily at 08:00 SAST ─────────────────────────────

    @Scheduled(cron = "0 0 8 * * *", zone = "Africa/Johannesburg")
    @Transactional(readOnly = true)
    public void checkDueSoon() {
        int sent = 0;
        for (int leadDays : DUE_SOON_ALERT_DAYS) {
            LocalDate target = LocalDate.now().plusDays(leadDays);
            List<Task> due = taskRepo.findDueOnDateAcrossTenants(target);
            for (Task task : due) {
                if (notifyDueSoon(task, leadDays)) sent++;
            }
        }
        log.info("Task due-soon sweep complete — notifications sent={}", sent);
    }

    private boolean notifyDueSoon(Task task, int daysUntil) {
        Recipient recipient = tasksService.resolveRecipient(task.getAssigneeId());
        if (recipient == null) return false;

        notificationService.send(NotificationRequest.builder()
                .tenantId(task.getTenantId())
                .type(NotificationType.TASK_DUE_SOON)
                .title("Task due in " + daysUntil + " day" + (daysUntil == 1 ? "" : "s") + ": " + task.getTitle())
                .message("\"" + task.getTitle() + "\" is due on " + task.getDueDate()
                        + " (" + daysUntil + " day" + (daysUntil == 1 ? "" : "s") + " from now).")
                .actionUrl("/tasks")
                .sourceModule("tasks")
                .sourceEntityId(task.getId().toString())
                .recipient(recipient)
                .build());
        return true;
    }

    // ── Overdue alert — daily at 08:30 SAST, after the due-soon sweep ──────

    @Scheduled(cron = "0 30 8 * * *", zone = "Africa/Johannesburg")
    @Transactional
    public void checkOverdue() {
        List<Task> overdue = taskRepo.findOverdueNeedingAlertAcrossTenants(LocalDate.now());
        int sent = 0;

        for (Task task : overdue) {
            // Mark first — matches Trip.markLongRunningAlertSent() in Fleet: idempotency
            // guard is set regardless of whether a recipient could be resolved, so a task
            // with no resolvable recipient doesn't get re-checked (and potentially
            // re-logged as a failure) every single day.
            task.markOverdueAlertSent();
            taskRepo.save(task);

            Recipient recipient = tasksService.resolveRecipient(task.getAssigneeId());
            if (recipient == null) continue;

            long daysOverdue = LocalDate.now().toEpochDay() - task.getDueDate().toEpochDay();
            notificationService.send(NotificationRequest.builder()
                    .tenantId(task.getTenantId())
                    .type(NotificationType.TASK_OVERDUE)
                    .title("Task overdue: " + task.getTitle())
                    .message("\"" + task.getTitle() + "\" was due on " + task.getDueDate()
                            + " and is now " + daysOverdue + " day" + (daysOverdue == 1 ? "" : "s") + " overdue.")
                    .actionUrl("/tasks")
                    .sourceModule("tasks")
                    .sourceEntityId(task.getId().toString())
                    .recipient(recipient)
                    .build());
            sent++;
        }
        log.info("Task overdue sweep complete — flagged={} notifications sent={}", overdue.size(), sent);
    }
}