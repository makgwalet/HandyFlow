package za.co.handyflow.platform.internalaudit.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.internalaudit.application.internal.ReminderRules.Stage;
import za.co.handyflow.platform.internalaudit.domain.model.AuditActionPlan;
import za.co.handyflow.platform.internalaudit.domain.model.AuditReportFactItem;
import za.co.handyflow.platform.internalaudit.domain.model.EngagementAssignment;
import za.co.handyflow.platform.internalaudit.domain.repository.*;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.notifications.application.UserRecipientResolver;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Daily reminders for audit follow-up. An action plan that is late gets a reminder to its owner on the first day and then weekly; from 14 days
 * late it is also escalated to the Head of Internal Audit on the engagement (or to the company's administrators if no one holds that role).
 * A factual-accuracy comment nobody has answered is brought to the report's author. A failed reminder is logged and never stops the sweep.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditReminderScheduler {
    private final AuditActionPlanRepository actions;
    private final AuditFindingRepository findings;
    private final EngagementAssignmentRepository assignments;
    private final AuditReportFactItemRepository factItems;
    private final AuditReportRepository reports;
    private final UserRecipientResolver userResolver;
    private final TenantAdminRecipients adminRecipients;
    private final NotificationService notifications;

    @Scheduled(cron = "0 30 7 * * *", zone = "Africa/Johannesburg")
    public void daily() {
        sweep(LocalDate.now(ZoneId.of("Africa/Johannesburg")));
    }

    /** Returns how many notifications were sent, so the caller (and tests) can see what happened. */
    public int sweep(LocalDate today) {
        int sent = 0;
        try { sent += overdueActions(today); } catch (Exception e) { log.error("[InternalAudit] Overdue action sweep failed: {}", e.getMessage(), e); }
        try { sent += unansweredComments(today); } catch (Exception e) { log.error("[InternalAudit] Factual-review reminder sweep failed: {}", e.getMessage(), e); }
        return sent;
    }

    int overdueActions(LocalDate today) {
        int sent = 0;
        for (AuditActionPlan a : actions.findOverdueAcrossTenants(today)) {
            long days = ChronoUnit.DAYS.between(a.getDueDate(), today);
            Stage stage = ReminderRules.stageFor(days);
            if (stage == Stage.NONE) continue;
            TenantId tenant = TenantId.of(a.getTenantId());
            String what = a.getDescription() + " (" + days + " day" + (days == 1 ? "" : "s") + " late, due " + a.getDueDate() + ")";
            if (a.getOwner() != null) sent += send(tenant, NotificationType.AUDIT_ACTION_OVERDUE, "Audit action overdue", what, a.getFindingId(), owner(tenant, a));
            if (stage == Stage.ESCALATE) sent += send(tenant, NotificationType.AUDIT_ACTION_ESCALATED, "Audit action escalated: " + days + " days late", what, a.getFindingId(), escalationTargets(tenant, a));
        }
        return sent;
    }

    int unansweredComments(LocalDate today) {
        int sent = 0;
        Instant cutoff = today.minusDays(3).atStartOfDay(ZoneId.of("Africa/Johannesburg")).toInstant();
        for (AuditReportFactItem i : factItems.findOpenRaisedBefore(cutoff.plus(1, ChronoUnit.DAYS))) {
            long waiting = ChronoUnit.DAYS.between(i.getRaisedAt().atZone(ZoneId.of("Africa/Johannesburg")).toLocalDate(), today);
            if (!ReminderRules.reviewReminderDue(waiting)) continue;
            var report = reports.findByTenantIdAndId(i.getTenantId(), i.getReportId()).orElse(null);
            if (report == null || !"FACTUAL_REVIEW".equals(report.getStatus())) continue;
            TenantId tenant = TenantId.of(i.getTenantId());
            sent += send(tenant, NotificationType.AUDIT_REPORT_REVIEW_REQUESTED, "Reviewer comment waiting for an answer",
                    i.getRaisedByName() + " raised a point on \"" + report.getTitle() + "\" " + waiting + " days ago", report.getId(),
                    resolve(tenant, List.of(report.getCreatedBy())));
        }
        return sent;
    }

    private List<Recipient> owner(TenantId tenant, AuditActionPlan a) { return resolve(tenant, List.of(a.getOwner())); }

    private List<Recipient> escalationTargets(TenantId tenant, AuditActionPlan a) {
        List<UUID> heads = findings.findByTenantAndId(a.getTenantId(), a.getFindingId())
                .map(f -> assignments.findByEngagement(a.getTenantId(), f.getEngagementId()).stream()
                        .filter(x -> "HEAD_OF_INTERNAL_AUDIT".equals(x.getRole())).map(EngagementAssignment::getUserId).toList())
                .orElse(List.of());
        List<Recipient> r = resolve(tenant, heads);
        return r.isEmpty() ? adminRecipients.resolveTenantAdmins(tenant) : r;
    }

    private List<Recipient> resolve(TenantId tenant, Collection<UUID> ids) {
        List<Recipient> out = new ArrayList<>();
        for (UUID id : new LinkedHashSet<>(ids)) userResolver.resolveUser(tenant, id).ifPresent(out::add);
        return out;
    }

    private int send(TenantId tenant, NotificationType type, String title, String message, UUID entityId, List<Recipient> to) {
        if (to.isEmpty()) return 0;
        try {
            notifications.send(NotificationRequest.builder().tenantId(tenant).type(type).title(title).message(message)
                    .actionUrl("/internal-audit").sourceModule("internalaudit").sourceEntityId(entityId.toString()).recipients(to).build());
            return 1;
        } catch (Exception e) {
            log.warn("[InternalAudit] Could not send {} for tenant={}: {}", type, tenant.getValue(), e.getMessage());
            return 0;
        }
    }
}
