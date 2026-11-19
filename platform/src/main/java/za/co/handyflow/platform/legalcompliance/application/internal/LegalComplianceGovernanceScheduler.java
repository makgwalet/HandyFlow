package za.co.handyflow.platform.legalcompliance.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.legalcompliance.domain.model.Attestation;
import za.co.handyflow.platform.legalcompliance.domain.model.AttestationCampaign;
import za.co.handyflow.platform.legalcompliance.domain.model.Incident;
import za.co.handyflow.platform.legalcompliance.domain.model.Policy;
import za.co.handyflow.platform.legalcompliance.domain.repository.AttestationCampaignRepository;
import za.co.handyflow.platform.legalcompliance.domain.repository.AttestationRepository;
import za.co.handyflow.platform.legalcompliance.domain.repository.IncidentRepository;
import za.co.handyflow.platform.legalcompliance.domain.repository.PolicyRepository;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Daily 07:06 sweep:
 *  - tells tenant admins which policies are overdue for review or due within GovernanceRules.REVIEW_WARNING_DAYS;
 *  - tells tenant admins which privacy incidents have gone GovernanceRules.ASSESSMENT_TARGET_HOURS (an internal target, not a statutory deadline)
 *    without a notification assessment;
 *  - reminds each person with a pending attestation 3 days before the due date, on it, and weekly while overdue (GovernanceRules.reminderDue).
 * Read-only. Requires the LEGALCOMPLIANCE_POLICY_REVIEW_DUE / _ATTESTATION_REMINDER / _INCIDENT_ASSESSMENT_OVERDUE constants (patched NotificationType included).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LegalComplianceGovernanceScheduler {

    private final PolicyRepository policies;
    private final IncidentRepository incidents;
    private final AttestationCampaignRepository campaigns;
    private final AttestationRepository attestations;
    private final NotificationService notificationService;
    private final TenantAdminRecipients tenantAdminRecipients;

    @Scheduled(cron = "0 6 7 * * *")
    public void sweep() {
        try { policyReviews(); } catch (Exception e) { log.error("[Legal/Compliance] Policy review sweep failed: {}", e.getMessage(), e); }
        try { unassessedIncidents(); } catch (Exception e) { log.error("[Legal/Compliance] Incident sweep failed: {}", e.getMessage(), e); }
        try { attestationReminders(); } catch (Exception e) { log.error("[Legal/Compliance] Attestation reminder sweep failed: {}", e.getMessage(), e); }
    }

    void policyReviews() {
        LocalDate today = LocalDate.now();
        notifyAdmins(policies.findReviewDueAcrossTenants(today.plusDays(GovernanceRules.REVIEW_WARNING_DAYS)), Policy::getTenantId, NotificationType.LEGALCOMPLIANCE_POLICY_REVIEW_DUE,
                "policy review(s) due or overdue", p -> p.getReference() + " " + p.getTitle() + " (review " + (p.getNextReviewDue().isBefore(today) ? "was due " : "due ") + p.getNextReviewDue() + ", owner " + p.getOwnerName() + ")",
                "/legalcompliance/policies");
    }

    void unassessedIncidents() {
        Instant now = Instant.now();
        List<Incident> overdue = incidents.findUnassessedAcrossTenants().stream().filter(i -> GovernanceRules.assessmentOverdue(i.getDiscoveredAt(), i.isPersonalInfoInvolved(), false, now)).toList();
        notifyAdmins(overdue, Incident::getTenantId, NotificationType.LEGALCOMPLIANCE_INCIDENT_ASSESSMENT_OVERDUE, "incident(s) awaiting a notification assessment",
                i -> i.getReference() + " " + i.getSeverity() + " " + i.getTitle() + " (discovered " + i.getDiscoveredAt() + ")", "/legalcompliance/incidents");
    }

    void attestationReminders() {
        LocalDate today = LocalDate.now();
        Map<java.util.UUID, AttestationCampaign> due = campaigns.findOpenAcrossTenants().stream().filter(c -> GovernanceRules.reminderDue(c.getDueDate(), today))
                .collect(Collectors.toMap(AttestationCampaign::getId, Function.identity()));
        if (due.isEmpty()) return;
        for (Attestation a : attestations.findPendingForCampaigns(due.keySet())) {
            AttestationCampaign c = due.get(a.getCampaignId());
            try {
                notificationService.send(NotificationRequest.builder().tenantId(c.getTenantId()).type(NotificationType.LEGALCOMPLIANCE_ATTESTATION_REMINDER)
                        .title(c.getDueDate().isBefore(today) ? "Overdue: " + c.getTitle() : "Reminder: " + c.getTitle())
                        .message("Please read and respond by " + c.getDueDate()).actionUrl("/legalcompliance/policies").sourceModule("legalcompliance")
                        .recipients(List.of(Recipient.user(a.getUserId(), a.getUserName(), a.getUserEmail(), null))).build());
            } catch (Exception e) {
                log.warn("[Legal/Compliance] Could not remind user for campaign {}: {}", c.getReference(), e.getMessage());
            }
        }
    }

    private <T> void notifyAdmins(List<T> items, Function<T, TenantId> tenantOf, NotificationType type, String what, Function<T, String> line, String url) {
        Map<TenantId, List<T>> byTenant = items.stream().collect(Collectors.groupingBy(tenantOf));
        byTenant.forEach((tenantId, list) -> {
            List<Recipient> admins = tenantAdminRecipients.resolveTenantAdmins(tenantId);
            String title = list.size() + " " + what;
            if (admins.isEmpty()) {
                log.warn("[Legal/Compliance] {} for tenant={} but no admin recipients could be resolved", title, tenantId.getValue());
                return;
            }
            String message = list.stream().limit(10).map(line).collect(Collectors.joining("; ")) + (list.size() > 10 ? " … and " + (list.size() - 10) + " more" : "");
            notificationService.send(NotificationRequest.builder().tenantId(tenantId).type(type).title(title).message(message)
                    .actionUrl(url).sourceModule("legalcompliance").recipients(admins).build());
        });
    }
}
