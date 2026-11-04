package za.co.handyflow.platform.projects.application.internal.notify;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.projects.domain.enums.ProjectMemberRole;
import za.co.handyflow.platform.shared.SchedulerLease;

import java.sql.Date;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;

/**
 * Daily at 07:15 SAST, on live projects only:
 * <ul>
 *   <li>tasks past their end date → the assignee, the first day and then weekly;</li>
 *   <li>open action items past their due date → the owner, the first day and then weekly;</li>
 *   <li>milestones due in exactly 3 days → the assignee and the project managers.</li>
 * </ul>
 * Each item is reminded at most once a day (pm_notification_sent), even if the job runs twice.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectReminderJob {

    static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");
    static final String LIVE = "('APPROVED','ACTIVE','ON_HOLD')";

    private final JdbcTemplate jdbc;
    private final SchedulerLease lease;
    private final ProjectNotifier notifier;
    private final ProjectNotificationSettings settings;

    /** First day overdue, then every seventh day. */
    static boolean remindToday(LocalDate due, LocalDate today) {
        long late = ChronoUnit.DAYS.between(due, today);
        return late >= 1 && (late - 1) % 7 == 0;
    }

    @Scheduled(cron = "0 15 7 * * *", zone = "Africa/Johannesburg")
    public void daily() {
        lease.runExclusively("pm-daily-reminders", Duration.ofMinutes(15), () -> run(LocalDate.now(SAST)));
    }

    void run(LocalDate today) {
        int sent = 0;
        sent += overdueTasks(today);
        sent += overdueActions(today);
        sent += milestonesDueSoon(today);
        log.info("[PM] Daily reminders sent: {}", sent);
    }

    private int overdueTasks(LocalDate today) {
        int[] n = {0};
        jdbc.query("""
                SELECT t.id, t.tenant_id, t.project_id, t.task_number, t.title, t.planned_end, t.assignee_id, p.name
                  FROM project_tasks t JOIN projects p ON p.id = t.project_id
                 WHERE t.status NOT IN ('COMPLETED','CANCELLED') AND t.assignee_id IS NOT NULL
                   AND t.is_milestone = false AND t.planned_end < ? AND p.status IN """ + LIVE, rs -> {
            LocalDate due = rs.getDate("planned_end").toLocalDate();
            UUID id = (UUID) rs.getObject("id"), tenant = (UUID) rs.getObject("tenant_id");
            if (!remindToday(due, today) || !settings.firstToday(tenant, PmEvent.TASK_OVERDUE, id, today)) return;
            long late = ChronoUnit.DAYS.between(due, today);
            notifier.publish(ProjectEvent.to(tenant, (UUID) rs.getObject("project_id"), PmEvent.TASK_OVERDUE,
                    rs.getString("task_number") + " is " + late + " day" + (late == 1 ? "" : "s") + " past its end date — " + rs.getString("name"),
                    rs.getString("title") + " was due " + due + ". Update its progress or move its dates.", id, (UUID) rs.getObject("assignee_id"), null));
            n[0]++;
        }, Date.valueOf(today));
        return n[0];
    }

    private int overdueActions(LocalDate today) {
        int[] n = {0};
        jdbc.query("""
                SELECT a.id, a.tenant_id, a.project_id, a.title, a.due_date, a.owner_id, p.name
                  FROM project_action_items a JOIN projects p ON p.id = a.project_id
                 WHERE a.status = 'OPEN' AND a.owner_id IS NOT NULL AND a.due_date < ? AND p.status IN """ + LIVE, rs -> {
            LocalDate due = rs.getDate("due_date").toLocalDate();
            UUID id = (UUID) rs.getObject("id"), tenant = (UUID) rs.getObject("tenant_id");
            if (!remindToday(due, today) || !settings.firstToday(tenant, PmEvent.ACTION_OVERDUE, id, today)) return;
            notifier.publish(ProjectEvent.to(tenant, (UUID) rs.getObject("project_id"), PmEvent.ACTION_OVERDUE,
                    "Your action is overdue — " + rs.getString("name"), rs.getString("title") + " was due " + due + ".",
                    id, (UUID) rs.getObject("owner_id"), null));
            n[0]++;
        }, Date.valueOf(today));
        return n[0];
    }

    private int milestonesDueSoon(LocalDate today) {
        int[] n = {0};
        LocalDate due = today.plusDays(3);
        jdbc.query("""
                SELECT t.id, t.tenant_id, t.project_id, t.task_number, t.title, t.assignee_id, p.name
                  FROM project_tasks t JOIN projects p ON p.id = t.project_id
                 WHERE t.is_milestone = true AND t.status NOT IN ('COMPLETED','CANCELLED')
                   AND t.planned_end = ? AND p.status IN """ + LIVE, rs -> {
            UUID id = (UUID) rs.getObject("id"), tenant = (UUID) rs.getObject("tenant_id");
            if (!settings.firstToday(tenant, PmEvent.MILESTONE_DUE_SOON, id, today)) return;
            UUID assignee = (UUID) rs.getObject("assignee_id");
            notifier.publish(new ProjectEvent(tenant, (UUID) rs.getObject("project_id"), PmEvent.MILESTONE_DUE_SOON,
                    "Milestone in 3 days: " + rs.getString("title") + " — " + rs.getString("name"),
                    rs.getString("task_number") + " is due " + due + ".", id,
                    assignee == null ? Set.of() : Set.of(assignee), Set.of(ProjectMemberRole.MANAGER), null));
            n[0]++;
        }, Date.valueOf(due));
        return n[0];
    }
}
