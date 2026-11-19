package za.co.handyflow.platform.legalcompliance.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
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
 * Daily 07:10 sweep, to tenant admins, read-only:
 *  - PAIA requests overdue or due within PrivacyPartyRules.PAIA_WARNING_DAYS (overdue ones risk being treated as refused);
 *  - regulator filings not yet submitted that are overdue or due within FILING_WARNING_DAYS;
 *  - approved PIAs whose review date has arrived or is within 30 days;
 *  - third parties whose periodic review is due, and verified items (certificates, insurance, agreements) that expired or expire within 30 days;
 *  - FICA parties whose periodic review is due, as references only — never names: FICA matters are restricted and a notification can reach
 *    people who do not hold the FICA permission.
 * Requires the new LEGALCOMPLIANCE_*_DUE / _EXPIRY constants in NotificationType (patched file included).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LegalComplianceInfoGovScheduler {

    private final PaiaRequestRepository paia;
    private final FilingRepository filings;
    private final PiaRepository pias;
    private final ThirdPartyRepository thirdParties;
    private final ThirdPartyItemRepository items;
    private final FicaPartyRepository ficaParties;
    private final NotificationService notificationService;
    private final TenantAdminRecipients tenantAdminRecipients;

    @Scheduled(cron = "0 10 7 * * *")
    public void sweep() {
        run("PAIA", this::paiaRequests);
        run("Filing", this::filingDeadlines);
        run("PIA", this::piaReviews);
        run("Third-party review", this::thirdPartyReviews);
        run("Third-party item", this::thirdPartyItems);
        run("FICA review", this::ficaReviews);
    }

    private void run(String name, Runnable r) {
        try { r.run(); } catch (Exception e) { log.error("[Legal/Compliance] {} sweep failed: {}", name, e.getMessage(), e); }
    }

    void paiaRequests() {
        LocalDate today = LocalDate.now();
        List<PaiaRequest> due = paia.findAwaitingDecisionAcrossTenants().stream().filter(p -> {
            LocalDate eff = PrivacyPartyRules.paiaEffectiveDue(p.getDueDate(), p.getExtendedUntil());
            return PrivacyPartyRules.paiaOverdue(eff, false, today) || PrivacyPartyRules.paiaDueSoon(eff, false, today);
        }).toList();
        notifyAdmins(due, PaiaRequest::getTenantId, NotificationType.LEGALCOMPLIANCE_PAIA_REQUEST_DUE, "PAIA request(s) due or overdue",
                p -> p.getReference() + " (due " + PrivacyPartyRules.paiaEffectiveDue(p.getDueDate(), p.getExtendedUntil()) + ")", "/legalcompliance/privacy");
    }

    void filingDeadlines() {
        LocalDate today = LocalDate.now();
        notifyAdmins(filings.findPendingAcrossTenants(today.plusDays(PrivacyPartyRules.FILING_WARNING_DAYS)), Filing::getTenantId, NotificationType.LEGALCOMPLIANCE_FILING_DUE,
                "regulator filing(s) due or overdue", f -> f.getReference() + " " + f.getTitle() + " (due " + f.getDueDate() + ", owner " + f.getOwnerName() + ")", "/legalcompliance/privacy");
    }

    void piaReviews() {
        LocalDate today = LocalDate.now();
        notifyAdmins(pias.findReviewDueAcrossTenants(today.plusDays(30)), Pia::getTenantId, NotificationType.LEGALCOMPLIANCE_PIA_REVIEW_DUE, "privacy impact assessment review(s) due",
                p -> p.getReference() + " " + p.getTitle() + " (review " + p.getNextReviewDue() + ")", "/legalcompliance/privacy");
    }

    void thirdPartyReviews() {
        LocalDate today = LocalDate.now();
        notifyAdmins(thirdParties.findReviewDueAcrossTenants(today.plusDays(30)), ThirdParty::getTenantId, NotificationType.LEGALCOMPLIANCE_THIRDPARTY_REVIEW_DUE,
                "third-party review(s) due or overdue", p -> p.getReference() + " " + p.getName() + " (review " + p.getNextReviewDue() + ", owner " + p.getOwnerName() + ")", "/legalcompliance/third-parties");
    }

    void thirdPartyItems() {
        LocalDate today = LocalDate.now();
        notifyAdmins(items.findExpiringAcrossTenants(today.plusDays(PrivacyPartyRules.EXPIRY_WARNING_DAYS)), ThirdPartyItem::getTenantId, NotificationType.LEGALCOMPLIANCE_THIRDPARTY_ITEM_EXPIRY,
                "third-party document(s) expired or expiring", i -> i.getTitle() + " (" + (i.getValidTo().isBefore(today) ? "expired " : "expires ") + i.getValidTo() + ")", "/legalcompliance/third-parties");
    }

    void ficaReviews() {
        LocalDate today = LocalDate.now();
        notifyAdmins(ficaParties.findReviewDueAcrossTenants(today.plusDays(30)), FicaParty::getTenantId, NotificationType.LEGALCOMPLIANCE_FICA_REVIEW_DUE, "due-diligence review(s) due",
                p -> p.getReference() + " (review " + p.getNextReviewDue() + ")", "/legalcompliance/fica");
    }

    private <T> void notifyAdmins(List<T> list, Function<T, TenantId> tenantOf, NotificationType type, String what, Function<T, String> line, String url) {
        Map<TenantId, List<T>> byTenant = list.stream().collect(Collectors.groupingBy(tenantOf));
        byTenant.forEach((tenantId, ts) -> {
            List<Recipient> admins = tenantAdminRecipients.resolveTenantAdmins(tenantId);
            String title = ts.size() + " " + what;
            if (admins.isEmpty()) {
                log.warn("[Legal/Compliance] {} for tenant={} but no admin recipients could be resolved", title, tenantId.getValue());
                return;
            }
            String message = ts.stream().limit(10).map(line).collect(Collectors.joining("; ")) + (ts.size() > 10 ? " … and " + (ts.size() - 10) + " more" : "");
            notificationService.send(NotificationRequest.builder().tenantId(tenantId).type(type).title(title).message(message).actionUrl(url).sourceModule("legalcompliance").recipients(admins).build());
        });
    }
}
