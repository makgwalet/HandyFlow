package za.co.handyflow.platform.legalcompliance.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.legalcompliance.domain.model.DsarRequest;
import za.co.handyflow.platform.legalcompliance.domain.model.ObligationStatus;
import za.co.handyflow.platform.legalcompliance.domain.model.RegulatoryObligation;
import za.co.handyflow.platform.legalcompliance.domain.repository.DsarRequestRepository;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Daily cross-tenant sweep for the two Legal/Compliance deadlines that need
 * proactive alerting: regulatory obligation review dates and DSAR statutory
 * due dates. Same shape as every other compliance scheduler in this
 * codebase (PsiraComplianceScheduler, ArmouryComplianceScheduler,
 * GuardScreeningComplianceScheduler, FleetNotificationScheduler): one
 * cross-tenant query, grouped by tenant in Java, one notification per
 * tenant to its resolved admins.
 * <p>
 * *** ACTION NEEDED IN NotificationType.java *** — this class will not
 * compile until the four constants below are added. See the accompanying
 * patch note (NotificationType-patch-instructions.md) for exactly where and
 * how, since the real file's full contents were not independently
 * confirmed this session (flagged, per the engagement's "don't guess at a
 * file whose complete content isn't confirmed" rule) — this is a precise,
 * additive patch instruction, not a full-file replacement:
 * <pre>{@code
 * LEGALCOMPLIANCE_OBLIGATION_DUE_SOON(WARNING, Set.of(IN_APP, EMAIL)),
 * LEGALCOMPLIANCE_OBLIGATION_OVERDUE(CRITICAL, Set.of(IN_APP, EMAIL)),
 * LEGALCOMPLIANCE_DSAR_DUE_SOON(WARNING, Set.of(IN_APP, EMAIL)),
 * LEGALCOMPLIANCE_DSAR_OVERDUE(CRITICAL, Set.of(IN_APP, EMAIL)),
 * }</pre>
 * <p>
 * WHY 06:45? Five minutes after GuardScreeningComplianceScheduler (06:30)
 * and fifteen before PsiraComplianceScheduler (07:00) — this codebase's
 * established convention of staggering daily compliance sweeps by five
 * minutes so they don't all compete for DB connections in the same second
 * (see ArmouryComplianceScheduler's own Javadoc for the same reasoning).
 * <p>
 * WHY no edge-trigger/idempotency dedup, unlike NoShowAlertScheduler? This
 * runs once daily, not every few minutes — re-alerting on the same
 * still-due item the next morning is the intended behaviour, same as every
 * other daily compliance scheduler in this codebase, none of which dedup
 * either (see GuardScreeningComplianceScheduler's own Javadoc for the same
 * reasoning, stated there for the identical situation).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LegalComplianceNotificationScheduler {

    private static final int OBLIGATION_DUE_SOON_DAYS = 14;
    private static final int DSAR_DUE_SOON_DAYS = 7;

    private final RegulatoryObligationService obligationService;
    private final DsarRequestRepository dsarRepository;
    private final NotificationService notificationService;
    private final TenantAdminRecipients tenantAdminRecipients;

    @Scheduled(cron = "0 45 6 * * *")
    public void checkDeadlines() {
        try {
            checkObligations();
        } catch (Exception e) {
            log.error("[Legal/Compliance] Regulatory obligation deadline sweep failed: {}", e.getMessage(), e);
        }
        try {
            checkDsarRequests();
        } catch (Exception e) {
            log.error("[Legal/Compliance] DSAR deadline sweep failed: {}", e.getMessage(), e);
        }
    }

    @Transactional
    public void checkObligations() {
        List<RegulatoryObligation> refreshed = obligationService.refreshAllStatuses(OBLIGATION_DUE_SOON_DAYS);

        Map<TenantId, List<RegulatoryObligation>> overdueByTenant = refreshed.stream()
                .filter(o -> o.getStatus() == ObligationStatus.OVERDUE)
                .collect(Collectors.groupingBy(RegulatoryObligation::getTenantId));
        overdueByTenant.forEach((tenantId, obligations) ->
                notifyObligations(tenantId, obligations, NotificationType.LEGALCOMPLIANCE_OBLIGATION_OVERDUE,
                        "overdue"));

        Map<TenantId, List<RegulatoryObligation>> dueSoonByTenant = refreshed.stream()
                .filter(o -> o.getStatus() == ObligationStatus.DUE_SOON)
                .collect(Collectors.groupingBy(RegulatoryObligation::getTenantId));
        dueSoonByTenant.forEach((tenantId, obligations) ->
                notifyObligations(tenantId, obligations, NotificationType.LEGALCOMPLIANCE_OBLIGATION_DUE_SOON,
                        "due within " + OBLIGATION_DUE_SOON_DAYS + " days"));
    }

    @Transactional(readOnly = true)
    public void checkDsarRequests() {
        LocalDate today = LocalDate.now();
        LocalDate dueSoonCutoff = today.plusDays(DSAR_DUE_SOON_DAYS);
        List<DsarRequest> open = dsarRepository.findOpenAcrossTenants();
        if (open.isEmpty()) return;

        Map<TenantId, List<DsarRequest>> overdueByTenant = open.stream()
                .filter(r -> r.getDueDate().isBefore(today))
                .collect(Collectors.groupingBy(DsarRequest::getTenantId));
        overdueByTenant.forEach((tenantId, requests) ->
                notifyDsar(tenantId, requests, NotificationType.LEGALCOMPLIANCE_DSAR_OVERDUE, "overdue"));

        Map<TenantId, List<DsarRequest>> dueSoonByTenant = open.stream()
                .filter(r -> !r.getDueDate().isBefore(today) && !r.getDueDate().isAfter(dueSoonCutoff))
                .collect(Collectors.groupingBy(DsarRequest::getTenantId));
        dueSoonByTenant.forEach((tenantId, requests) ->
                notifyDsar(tenantId, requests, NotificationType.LEGALCOMPLIANCE_DSAR_DUE_SOON,
                        "due within " + DSAR_DUE_SOON_DAYS + " days"));
    }

    private void notifyObligations(TenantId tenantId, List<RegulatoryObligation> obligations,
                                    NotificationType type, String label) {
        List<Recipient> admins = tenantAdminRecipients.resolveTenantAdmins(tenantId);
        if (admins.isEmpty()) {
            log.warn("[Legal/Compliance] {} obligation(s) {} for tenant={} but no admin recipients could be resolved",
                    obligations.size(), label, tenantId.getValue());
            return;
        }
        String title = obligations.size() + " regulatory obligation(s) " + label;
        String message = obligations.stream()
                .map(o -> o.getTitle() + " (review date " + o.getReviewDate() + ")")
                .collect(Collectors.joining(", "));

        notificationService.send(NotificationRequest.builder()
                .tenantId(tenantId)
                .type(type)
                .title(title)
                .message(message)
                .actionUrl("/legalcompliance/obligations")
                .sourceModule("legalcompliance")
                .recipients(admins)
                .build());

        log.info("[Legal/Compliance] Obligation deadline alert sent tenant={} count={} label={}",
                tenantId.getValue(), obligations.size(), label);
    }

    private void notifyDsar(TenantId tenantId, List<DsarRequest> requests, NotificationType type, String label) {
        List<Recipient> admins = tenantAdminRecipients.resolveTenantAdmins(tenantId);
        if (admins.isEmpty()) {
            log.warn("[Legal/Compliance] {} DSAR request(s) {} for tenant={} but no admin recipients could be resolved",
                    requests.size(), label, tenantId.getValue());
            return;
        }
        String title = requests.size() + " DSAR request(s) " + label;
        String message = requests.stream()
                .map(r -> r.getRequestNumber() + " — " + r.getRequesterName() + " (due " + r.getDueDate() + ")")
                .collect(Collectors.joining(", "));

        notificationService.send(NotificationRequest.builder()
                .tenantId(tenantId)
                .type(type)
                .title(title)
                .message(message)
                .actionUrl("/legalcompliance/dsar-requests")
                .sourceModule("legalcompliance")
                .recipients(admins)
                .build());

        log.info("[Legal/Compliance] DSAR deadline alert sent tenant={} count={} label={}",
                tenantId.getValue(), requests.size(), label);
    }
}
