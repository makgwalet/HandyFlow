package za.co.handyflow.platform.facilitiesmanagement.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmWorkOrder;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmWorkOrderRepository;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.SchedulerLease;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Every 10 minutes, watches open work orders that carry an SLA and alerts tenant admins:
 * <ul>
 *   <li><b>At risk</b> — once, when a response or resolution target enters its warning window
 *       (see {@link FmSlaStatus#warnAt});</li>
 *   <li><b>Breached</b> — once, when a target's due time passes without being met.</li>
 * </ul>
 * The warned/breached timestamps are stored on the work order, so each alert fires only once and the breach
 * is on record whether or not anyone read the notification. Runs under {@link SchedulerLease}, so only one
 * app instance sweeps. A paused resolution clock is not watched; work awaiting verification counts as resolved.
 * <p>
 * NOTE: alerts reuse the {@code FM_WORKORDER_OVERDUE} notification type until dedicated SLA types exist.
 * Escalation beyond tenant admins (a configurable path) is not built yet.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FmSlaMonitor {

    private static final String LOCK_NAME = "fm-sla-monitor";
    private static final Duration LOCK_MAX_HOLD = Duration.ofMinutes(8);

    private final FmWorkOrderRepository workOrderRepository;
    private final NotificationService notificationService;
    private final TenantAdminRecipients tenantAdminRecipients;
    private final SchedulerLease schedulerLease;

    @Scheduled(cron = "0 */10 * * * *")
    public void run() {
        schedulerLease.runExclusively(LOCK_NAME, LOCK_MAX_HOLD, () -> {
            Instant now = Instant.now();
            int response = checkResponse(now);
            int resolution = checkResolution(now);
            if (response + resolution > 0) {
                log.info("[FM] SLA monitor raised {} response and {} resolution alert(s)", response, resolution);
            }
        });
    }

    public int checkResponse(Instant now) {
        int alerts = 0;
        for (FmWorkOrder wo : workOrderRepository.findOpenForResponseSla()) {
            try {
                Instant due = wo.getResponseDueAt();
                if (now.isAfter(due)) {
                    wo.markResponseBreached(now);
                    workOrderRepository.save(wo);
                    notifyAdmins(wo, "SLA response BREACHED: " + wo.getWorkOrderNumber(),
                            wo.getWorkOrderNumber() + " (" + wo.getPriority() + ") was not assigned by " + due + ".");
                    alerts++;
                } else if (wo.getResponseWarnedAt() == null && !now.isBefore(FmSlaStatus.warnAt(wo.getCreatedAt(), due))) {
                    wo.markResponseWarned(now);
                    workOrderRepository.save(wo);
                    notifyAdmins(wo, "SLA response at risk: " + wo.getWorkOrderNumber(),
                            wo.getWorkOrderNumber() + " (" + wo.getPriority() + ") must be assigned by " + due + ".");
                    alerts++;
                }
            } catch (Exception e) {
                log.error("[FM] SLA response check failed for work order={}: {}", wo.getId(), e.getMessage(), e);
            }
        }
        return alerts;
    }

    public int checkResolution(Instant now) {
        int alerts = 0;
        for (FmWorkOrder wo : workOrderRepository.findOpenForResolutionSla()) {
            try {
                Instant due = wo.getResolutionDueAt();
                if (now.isAfter(due)) {
                    wo.markResolutionBreached(now);
                    workOrderRepository.save(wo);
                    notifyAdmins(wo, "SLA resolution BREACHED: " + wo.getWorkOrderNumber(),
                            wo.getWorkOrderNumber() + " (" + wo.getPriority() + ") was not resolved by " + due + ".");
                    alerts++;
                } else if (wo.getResolutionWarnedAt() == null && !now.isBefore(FmSlaStatus.warnAt(wo.getCreatedAt(), due))) {
                    wo.markResolutionWarned(now);
                    workOrderRepository.save(wo);
                    notifyAdmins(wo, "SLA resolution at risk: " + wo.getWorkOrderNumber(),
                            wo.getWorkOrderNumber() + " (" + wo.getPriority() + ") must be resolved by " + due + ".");
                    alerts++;
                }
            } catch (Exception e) {
                log.error("[FM] SLA resolution check failed for work order={}: {}", wo.getId(), e.getMessage(), e);
            }
        }
        return alerts;
    }

    private void notifyAdmins(FmWorkOrder wo, String title, String message) {
        List<Recipient> recipients = tenantAdminRecipients.resolveTenantAdmins(wo.getTenantId());
        if (recipients.isEmpty()) return;
        notificationService.send(NotificationRequest.builder()
                .tenantId(wo.getTenantId())
                .type(NotificationType.FM_WORKORDER_OVERDUE)
                .title(title)
                .message(message)
                .actionUrl("/facilitiesmanagement/work-orders/" + wo.getId())
                .sourceModule("facilitiesmanagement")
                .sourceEntityId(wo.getId().toString())
                .recipients(recipients)
                .build());
    }
}