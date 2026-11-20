package za.co.handyflow.platform.legalcompliance.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.legalcompliance.domain.model.WatchSource;
import za.co.handyflow.platform.legalcompliance.domain.repository.WatchSourceRepository;
import za.co.handyflow.platform.legalcompliance.dto.MonitoringDtos.FetchOutcome;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.TenantId;

import java.util.*;

/**
 * Daily at 06:40: fetch every active watch source, one at a time, each isolated so one bad feed never stops the rest. A tenant admin gets one message per tenant:
 * how many new items are waiting to be triaged, and which sources have now failed three times running (said once, on the third failure, not every day).
 * The message carries counts and source names only, never feed text. Requires NotificationType.LEGALCOMPLIANCE_MONITORING_ATTENTION (patched NotificationType lines included).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LegalComplianceMonitoringScheduler {

    private final WatchSourceRepository sources;
    private final MonitoringService monitoring;
    private final NotificationService notificationService;
    private final TenantAdminRecipients tenantAdminRecipients;

    @Scheduled(cron = "0 40 6 * * *", zone = "Africa/Johannesburg")
    public void sweep() {
        try { run(); } catch (Exception e) { log.error("[Legal/Compliance] Monitoring sweep failed: {}", e.getMessage(), e); }
    }

    void run() {
        Map<TenantId, int[]> added = new LinkedHashMap<>();
        Map<TenantId, List<String>> failing = new LinkedHashMap<>();
        for (WatchSource s : sources.findActiveAcrossTenants()) {
            TenantId t = s.getTenantId();
            added.computeIfAbsent(t, k -> new int[1]);
            try {
                FetchOutcome o = monitoring.fetchSource(t, s.getId());
                added.get(t)[0] += o.added();
                if (o.alertDue()) failing.computeIfAbsent(t, k -> new ArrayList<>()).add(s.getName());
            } catch (Exception e) {
                log.warn("[Legal/Compliance] Fetch failed for source={} tenant={}: {}", s.getId(), t.getValue(), e.getMessage());
            }
        }
        for (var e : added.entrySet()) {
            List<String> parts = new ArrayList<>();
            if (e.getValue()[0] > 0) parts.add(e.getValue()[0] + " new regulatory item(s) to review");
            List<String> bad = failing.getOrDefault(e.getKey(), List.of());
            if (!bad.isEmpty()) parts.add("source(s) failing: " + String.join(", ", bad));
            send(e.getKey(), parts);
        }
    }

    private void send(TenantId t, List<String> parts) {
        if (parts.isEmpty()) return;
        List<Recipient> admins = tenantAdminRecipients.resolveTenantAdmins(t);
        if (admins.isEmpty()) { log.warn("[Legal/Compliance] Monitoring update for tenant={} but no admin recipients could be resolved", t.getValue()); return; }
        notificationService.send(NotificationRequest.builder().tenantId(t).type(NotificationType.LEGALCOMPLIANCE_MONITORING_ATTENTION).title("Regulatory monitoring update")
                .message(String.join("; ", parts)).actionUrl("/legalcompliance/monitoring").sourceModule("legalcompliance").recipients(admins).build());
    }
}
