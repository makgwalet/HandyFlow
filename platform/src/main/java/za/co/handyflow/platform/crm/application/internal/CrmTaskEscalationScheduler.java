package za.co.handyflow.platform.crm.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.crm.domain.model.Customer;
import za.co.handyflow.platform.crm.domain.repository.CustomerFollowUpRepository;
import za.co.handyflow.platform.crm.domain.repository.CustomerRepository;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Tells the account's admins, once, about follow-ups that are several days overdue. One digest per tenant per sweep, never one
 * message per task, and a task is only reported once until it is rescheduled or reopened. The task queue screen shows the same
 * threshold as "late by 3+ days".
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CrmTaskEscalationScheduler {

    /** Matches ESCALATE_AFTER_DAYS in the task queue screen. */
    public static final int ESCALATE_AFTER_DAYS = 3;

    private final CustomerFollowUpRepository followUpRepository;
    private final CustomerRepository         customerRepository;
    private final TenantAdminRecipients      tenantAdminRecipients;
    private final NotificationService        notificationService;

    @Scheduled(cron = "0 30 7 * * *")  // 7:30 AM daily, after the 7:00 reminders
    public void sweep() {
        int total = 0;
        for (UUID tenantId : customerRepository.findDistinctActiveTenantIds()) {
            try {
                total += escalateForTenant(TenantId.of(tenantId), LocalDate.now());
            } catch (Exception ex) {
                log.error("[CRM] Task escalation failed for tenant={}: {}", tenantId, ex.getMessage(), ex);
            }
        }
        log.info("[CRM] Task escalation sweep complete — {} tasks escalated", total);
    }

    /** Returns how many tasks were reported. Zero when nothing is late, or when no admin could be found to tell. */
    @Transactional
    public int escalateForTenant(TenantId tenantId, LocalDate today) {
        var late = followUpRepository.findForEscalation(tenantId, today.minusDays(ESCALATE_AFTER_DAYS));
        if (late.isEmpty()) return 0;

        var recipients = tenantAdminRecipients.resolveTenantAdmins(tenantId);
        if (recipients.isEmpty()) {
            log.info("[CRM] {} late tasks for tenant={} but no admin recipients could be resolved — not escalated", late.size(), tenantId);
            return 0;
        }

        List<EscalationDigest.Item> items = new ArrayList<>();
        for (var f : late) {
            // Tasks of deleted customers are not reported, but are still marked so they do not come back every day.
            var customer = customerRepository.findActiveById(tenantId, f.getCustomerId());
            f.markEscalated();
            followUpRepository.save(f);
            customer.map(Customer::getName).ifPresent(name -> items.add(new EscalationDigest.Item(name, f.getNote(), f.getDueDate())));
        }
        if (items.isEmpty()) return 0;

        try {
            notificationService.send(NotificationRequest.builder()
                    .tenantId(tenantId)
                    .type(NotificationType.CRM_TASK_ESCALATED)
                    .title(EscalationDigest.title(items.size()))
                    .message(EscalationDigest.message(items, today))
                    .actionUrl("/crm")
                    .sourceModule("crm")
                    .sourceEntityId(tenantId.toString())
                    .recipients(recipients)
                    .build());
        } catch (Exception e) {
            // The tasks are already marked; a failed alert must not roll that back or the same tasks would be retried forever.
            log.warn("[CRM] Task escalation alert not sent for tenant={}: {}", tenantId, e.getMessage());
        }
        return items.size();
    }
}
