package za.co.handyflow.platform.legalcompliance.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.legalcompliance.domain.model.BcProcess;
import za.co.handyflow.platform.legalcompliance.domain.model.EsgMetric;
import za.co.handyflow.platform.legalcompliance.domain.repository.BcProcessRepository;
import za.co.handyflow.platform.legalcompliance.domain.repository.EsgMetricRepository;
import za.co.handyflow.platform.legalcompliance.dto.ContinuityDtos.ContinuitySummary;
import za.co.handyflow.platform.legalcompliance.dto.EsgDtos.EsgSummary;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.TenantId;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Mondays at 07:30, to tenant admins only: one message per tenant per topic, with counts, never the content of a plan or a reading.
 *  - continuity: critical processes without an approved plan, exercises overdue, plan reviews overdue, impact-analysis reviews overdue;
 *  - ESG: metrics whose next reading is overdue.
 * Read-only. Requires NotificationType.LEGALCOMPLIANCE_CONTINUITY_ATTENTION and LEGALCOMPLIANCE_ESG_ATTENTION (patched NotificationType included).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LegalComplianceResilienceScheduler {

    private final BcProcessRepository processes;
    private final EsgMetricRepository metrics;
    private final ContinuityService continuity;
    private final EsgService esg;
    private final NotificationService notificationService;
    private final TenantAdminRecipients tenantAdminRecipients;

    @Scheduled(cron = "0 30 7 * * MON", zone = "Africa/Johannesburg")
    public void sweep() {
        try { continuityAttention(); } catch (Exception e) { log.error("[Legal/Compliance] Continuity sweep failed: {}", e.getMessage(), e); }
        try { esgAttention(); } catch (Exception e) { log.error("[Legal/Compliance] ESG sweep failed: {}", e.getMessage(), e); }
    }

    void continuityAttention() {
        Set<TenantId> tenants = processes.findActiveAcrossTenants().stream().map(BcProcess::getTenantId).collect(Collectors.toCollection(LinkedHashSet::new));
        for (TenantId t : tenants) {
            try {
                ContinuitySummary s = continuity.summary(t);
                List<String> parts = new ArrayList<>();
                if (s.criticalWithoutPlan() > 0) parts.add(s.criticalWithoutPlan() + " critical process(es) without an approved plan");
                if (s.testOverdue() > 0) parts.add(s.testOverdue() + " plan(s) overdue for an exercise");
                if (s.planReviewOverdue() > 0) parts.add(s.planReviewOverdue() + " plan review(s) overdue");
                if (s.biaOverdue() > 0) parts.add(s.biaOverdue() + " impact analysis review(s) overdue");
                send(t, NotificationType.LEGALCOMPLIANCE_CONTINUITY_ATTENTION, "Business continuity needs attention", parts, "/legalcompliance/continuity");
            } catch (Exception e) {
                log.warn("[Legal/Compliance] Continuity check failed for tenant={}: {}", t.getValue(), e.getMessage());
            }
        }
    }

    void esgAttention() {
        Set<TenantId> tenants = metrics.findActiveAcrossTenants().stream().map(EsgMetric::getTenantId).collect(Collectors.toCollection(LinkedHashSet::new));
        for (TenantId t : tenants) {
            try {
                EsgSummary s = esg.summary(t);
                List<String> parts = new ArrayList<>();
                if (s.readingsOverdue() > 0) parts.add(s.readingsOverdue() + " ESG metric(s) with a reading overdue");
                send(t, NotificationType.LEGALCOMPLIANCE_ESG_ATTENTION, "ESG readings are overdue", parts, "/legalcompliance/esg");
            } catch (Exception e) {
                log.warn("[Legal/Compliance] ESG check failed for tenant={}: {}", t.getValue(), e.getMessage());
            }
        }
    }

    private void send(TenantId t, NotificationType type, String title, List<String> parts, String url) {
        if (parts.isEmpty()) return;
        List<Recipient> admins = tenantAdminRecipients.resolveTenantAdmins(t);
        if (admins.isEmpty()) { log.warn("[Legal/Compliance] {} for tenant={} but no admin recipients could be resolved", title, t.getValue()); return; }
        notificationService.send(NotificationRequest.builder().tenantId(t).type(type).title(title).message(String.join("; ", parts)).actionUrl(url)
                .sourceModule("legalcompliance").recipients(admins).build());
    }
}
