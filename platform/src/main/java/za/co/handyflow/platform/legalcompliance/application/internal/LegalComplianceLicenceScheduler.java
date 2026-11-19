package za.co.handyflow.platform.legalcompliance.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.legalcompliance.domain.model.Licence;
import za.co.handyflow.platform.legalcompliance.domain.model.LicenceCondition;
import za.co.handyflow.platform.legalcompliance.domain.repository.LicenceConditionRepository;
import za.co.handyflow.platform.legalcompliance.domain.repository.LicenceRepository;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Daily 07:14 sweep. Tells tenant admins which licences are due for renewal or have expired (on the schedule in LicenceRules.reminderDue, then weekly once
 * expired) and which licence conditions are due or overdue (LicenceRules.conditionReminderDue). Read-only.
 * Requires NotificationType.LEGALCOMPLIANCE_LICENCE_EXPIRY and LEGALCOMPLIANCE_LICENCE_CONDITION_DUE (patched NotificationType included).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LegalComplianceLicenceScheduler {

    private final LicenceRepository licences;
    private final LicenceConditionRepository conditions;
    private final NotificationService notificationService;
    private final TenantAdminRecipients tenantAdminRecipients;

    @Scheduled(cron = "0 14 7 * * *")
    public void sweep() {
        try { expiries(); } catch (Exception e) { log.error("[Legal/Compliance] Licence expiry sweep failed: {}", e.getMessage(), e); }
        try { conditionsDue(); } catch (Exception e) { log.error("[Legal/Compliance] Licence condition sweep failed: {}", e.getMessage(), e); }
    }

    void expiries() {
        LocalDate today = LocalDate.now();
        // Longest possible lead time is 365 days; reminderDue decides which of these is actually reminded today.
        List<Licence> due = licences.findExpiringAcrossTenants(today.plusDays(365)).stream().filter(l -> LicenceRules.reminderDue(l.getExpiryDate(), l.getRenewalLeadDays(), today)).toList();
        notifyAdmins(due, Licence::getTenantId, NotificationType.LEGALCOMPLIANCE_LICENCE_EXPIRY, "licence(s) or permit(s) due for renewal or expired",
                l -> l.getReference() + " " + l.getTitle() + " (" + (l.getExpiryDate().isBefore(today) ? "expired " : "expires ") + l.getExpiryDate() + ", owner " + l.getOwnerName() + ")", "/legalcompliance/licences");
    }

    void conditionsDue() {
        LocalDate today = LocalDate.now();
        List<LicenceCondition> due = conditions.findDueAcrossTenants(today.plusDays(LicenceRules.CONDITION_WARNING_DAYS)).stream().filter(c -> LicenceRules.conditionReminderDue(c.getDueDate(), today)).toList();
        notifyAdmins(due, LicenceCondition::getTenantId, NotificationType.LEGALCOMPLIANCE_LICENCE_CONDITION_DUE, "licence condition(s) due or overdue",
                c -> c.getDescription() + " (" + (c.getDueDate().isBefore(today) ? "was due " : "due ") + c.getDueDate() + ")", "/legalcompliance/licences");
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
