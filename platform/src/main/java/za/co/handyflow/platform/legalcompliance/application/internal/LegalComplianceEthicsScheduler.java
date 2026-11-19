package za.co.handyflow.platform.legalcompliance.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.legalcompliance.domain.model.CoiDeclaration;
import za.co.handyflow.platform.legalcompliance.domain.model.Committee;
import za.co.handyflow.platform.legalcompliance.domain.model.CommitteeResolution;
import za.co.handyflow.platform.legalcompliance.domain.model.CommitteeStatus;
import za.co.handyflow.platform.legalcompliance.domain.model.DeclarationStatus;
import za.co.handyflow.platform.legalcompliance.domain.model.GiftEntry;
import za.co.handyflow.platform.legalcompliance.domain.repository.CoiDeclarationRepository;
import za.co.handyflow.platform.legalcompliance.domain.repository.CommitteeRepository;
import za.co.handyflow.platform.legalcompliance.domain.repository.CommitteeResolutionRepository;
import za.co.handyflow.platform.legalcompliance.domain.repository.GiftEntryRepository;
import za.co.handyflow.platform.legalcompliance.dto.GovernanceDtos.CommitteeResponse;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Daily 07:22 sweep, to tenant admins only. Messages carry references and counts, never a person's declaration or the other party, because declarations are personal.
 *  - declarations awaiting review, and cleared/mitigated ones past their review date (weekly while outstanding);
 *  - gifts and hospitality awaiting approval (weekly);
 *  - committees overdue to meet or overdue a charter review (weekly);
 *  - committee actions due today or overdue (on the due date, then weekly).
 * Read-only. Requires NotificationType.LEGALCOMPLIANCE_ETHICS_REVIEW_DUE and LEGALCOMPLIANCE_COMMITTEE_ATTENTION (patched NotificationType included).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LegalComplianceEthicsScheduler {

    private final CoiDeclarationRepository declarations;
    private final GiftEntryRepository gifts;
    private final CommitteeRepository committees;
    private final CommitteeResolutionRepository resolutions;
    private final CommitteeService committeeService;
    private final NotificationService notificationService;
    private final TenantAdminRecipients tenantAdminRecipients;

    @Scheduled(cron = "0 22 7 * * *")
    public void sweep() {
        try { declarationsDue(); } catch (Exception e) { log.error("[Legal/Compliance] Declaration sweep failed: {}", e.getMessage(), e); }
        try { giftsPending(); } catch (Exception e) { log.error("[Legal/Compliance] Gift sweep failed: {}", e.getMessage(), e); }
        try { committeesDue(); } catch (Exception e) { log.error("[Legal/Compliance] Committee sweep failed: {}", e.getMessage(), e); }
        try { actionsDue(); } catch (Exception e) { log.error("[Legal/Compliance] Committee action sweep failed: {}", e.getMessage(), e); }
    }

    private static LocalDate today() { return LocalDate.now(ZoneId.of("Africa/Johannesburg")); }

    void declarationsDue() {
        LocalDate today = today();
        List<CoiDeclaration> due = declarations.findNeedingAttentionAcrossTenants(today).stream().filter(d -> {
            LocalDate anchor = d.getStatus() == DeclarationStatus.CLEARED || d.getStatus() == DeclarationStatus.MITIGATED ? d.getNextReviewDue() : d.getDeclaredOn();
            return EthicsGovernanceRules.weeklyReminderDue(anchor, today);
        }).toList();
        notifyAdmins(due, CoiDeclaration::getTenantId, NotificationType.LEGALCOMPLIANCE_ETHICS_REVIEW_DUE, "conflict-of-interest declaration(s) awaiting review or re-review",
                d -> d.getReference() + " (" + (d.awaitingReview() ? "awaiting review since " + d.getDeclaredOn() : "review was due " + d.getNextReviewDue()) + ")", "/legalcompliance/ethics");
    }

    void giftsPending() {
        LocalDate today = today();
        List<GiftEntry> pending = gifts.findPendingAcrossTenants().stream().filter(g -> EthicsGovernanceRules.weeklyReminderDue(g.getEntryDate(), today)).toList();
        notifyAdmins(pending, GiftEntry::getTenantId, NotificationType.LEGALCOMPLIANCE_ETHICS_REVIEW_DUE, "gift or hospitality entr(ies) awaiting approval",
                g -> g.getReference() + " (recorded " + g.getEntryDate() + ")", "/legalcompliance/ethics");
    }

    void committeesDue() {
        LocalDate today = today();
        Set<TenantId> tenants = committees.findActiveAcrossTenants().stream().map(Committee::getTenantId).collect(Collectors.toCollection(LinkedHashSet::new));
        for (TenantId t : tenants) {
            try {
                // The service decides overdue; the sweep nags on Mondays only so a standing problem is a weekly reminder rather than a daily one.
                if (today.getDayOfWeek() != java.time.DayOfWeek.MONDAY) continue;
                List<CommitteeResponse> flagged = committeeService.list(t, CommitteeStatus.ACTIVE).stream().filter(c -> c.meetingOverdue() || c.charterReviewOverdue()).toList();
                notifyAdmins(flagged, c -> t, NotificationType.LEGALCOMPLIANCE_COMMITTEE_ATTENTION, "committee(s) overdue to meet or for a charter review",
                        c -> c.reference() + " " + c.name() + " (" + (c.meetingOverdue() ? "no meeting scheduled or held within " + c.meetingFrequencyMonths() + " months" : "charter review overdue") + ")", "/legalcompliance/committees");
            } catch (Exception e) {
                log.warn("[Legal/Compliance] Committee check failed for tenant={}: {}", t.getValue(), e.getMessage());
            }
        }
    }

    void actionsDue() {
        LocalDate today = today();
        List<CommitteeResolution> due = resolutions.findDueAcrossTenants(today).stream().filter(r -> EthicsGovernanceRules.weeklyReminderDue(r.getDueDate(), today)).toList();
        notifyAdmins(due, CommitteeResolution::getTenantId, NotificationType.LEGALCOMPLIANCE_COMMITTEE_ATTENTION, "committee action(s) due or overdue",
                r -> r.getOwnerName() + ": " + abbreviate(r.getDescription()) + " (due " + r.getDueDate() + ")", "/legalcompliance/committees");
    }

    private static String abbreviate(String s) { return s.length() <= 80 ? s : s.substring(0, 77) + "…"; }

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
