package za.co.handyflow.platform.collectionsagency.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.collectionsagency.domain.model.CollAgencyCollector;
import za.co.handyflow.platform.collectionsagency.domain.model.CollAgencyDebtorAccount;
import za.co.handyflow.platform.collectionsagency.domain.model.CollAgencyPaymentPlan;
import za.co.handyflow.platform.collectionsagency.domain.model.CollAgencyProfile;
import za.co.handyflow.platform.collectionsagency.domain.repository.CollAgencyCollectorRepository;
import za.co.handyflow.platform.collectionsagency.domain.repository.CollAgencyDebtorAccountRepository;
import za.co.handyflow.platform.collectionsagency.domain.repository.CollAgencyPaymentPlanRepository;
import za.co.handyflow.platform.collectionsagency.domain.repository.CollAgencyProfileRepository;
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
 * Daily cross-tenant sweep for three proactive Collections Agency
 * alerts, same shape/stagger convention as DebtCollectionNotification
 * Scheduler (07:30) and every other compliance/reminder scheduler in
 * this codebase — one cross-tenant query per concern, grouped by tenant
 * in Java (this module's entities carry a raw UUID tenantId, not
 * TenantId directly — TenantId.of(UUID) bridges that, confirmed real
 * factory method), one notification per tenant to its resolved admins,
 * no dedup/idempotency (re-alerting daily on a still-outstanding item is
 * this codebase's established convention, not a bug):
 * <p>
 * 1. Firm Debt Collectors Act registration expiry (CollAgencyProfile).
 * 2. Individual collector registration expiry (CollAgencyCollector) —
 *    arguably the more operationally dangerous of the two, since the
 *    Debt Collectors Act makes an individual collecting while
 *    unregistered a criminal offence, not just a compliance gap.
 * 3. Payment-plan installment due/overdue — direct mirror of
 *    DebtCollectionNotificationScheduler's own installment sweep.
 *    Deliberately does NOT auto-mark a plan DEFAULTED — same reasoning:
 *    a human judgment call, not this scheduler's to make.
 * <p>
 * *** ACTION NEEDED IN NotificationType.java *** — see the accompanying
 * CollectionsAgency-NotificationType-patch-instructions.md. This class
 * will not compile until those six constants are added.
 * <p>
 * WHY 07:45? After DebtCollectionNotificationScheduler (07:30) —
 * next open slot in this codebase's 15-minute stagger convention.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CollAgencyNotificationScheduler {

    private static final int REGISTRATION_DUE_SOON_DAYS = 30; // Council for Debt Collectors renewal — a bigger lead time than a 3-day payment reminder, since re-registration is an administrative process, not a same-day fix
    private static final int INSTALLMENT_DUE_SOON_DAYS = 3;

    private final CollAgencyProfileRepository profileRepository;
    private final CollAgencyCollectorRepository collectorRepository;
    private final CollAgencyPaymentPlanRepository paymentPlanRepository;
    private final CollAgencyDebtorAccountRepository debtorAccountRepository;
    private final NotificationService notificationService;
    private final TenantAdminRecipients tenantAdminRecipients;

    @Scheduled(cron = "0 45 7 * * *")
    public void checkDeadlines() {
        try {
            checkFirmRegistration();
        } catch (Exception e) {
            log.error("[Collections Agency] Firm registration sweep failed: {}", e.getMessage(), e);
        }
        try {
            checkCollectorRegistrations();
        } catch (Exception e) {
            log.error("[Collections Agency] Collector registration sweep failed: {}", e.getMessage(), e);
        }
        try {
            checkPaymentPlanInstallments();
        } catch (Exception e) {
            log.error("[Collections Agency] Payment plan installment sweep failed: {}", e.getMessage(), e);
        }
    }

    @Transactional(readOnly = true)
    public void checkFirmRegistration() {
        LocalDate today = LocalDate.now();
        LocalDate dueSoonCutoff = today.plusDays(REGISTRATION_DUE_SOON_DAYS);

        List<CollAgencyProfile> profiles = profileRepository.findAllWithFirmRegistrationAcrossTenants();
        for (CollAgencyProfile profile : profiles) {
            LocalDate expiry = profile.getFirmRegistrationExpiryDate();
            if (expiry.isBefore(today)) {
                notifyOne(TenantId.of(profile.getTenantId()),
                        NotificationType.COLLECTIONSAGENCY_FIRM_REGISTRATION_EXPIRED,
                        "Firm Debt Collectors Act registration has EXPIRED",
                        profile.getAgencyName() + " — registration " + profile.getFirmRegistrationNumber()
                                + " expired " + expiry + ". Collecting while unregistered is a criminal offence "
                                + "under the Debt Collectors Act — renew immediately.");
            } else if (!expiry.isAfter(dueSoonCutoff)) {
                notifyOne(TenantId.of(profile.getTenantId()),
                        NotificationType.COLLECTIONSAGENCY_FIRM_REGISTRATION_DUE_SOON,
                        "Firm Debt Collectors Act registration expires soon",
                        profile.getAgencyName() + " — registration " + profile.getFirmRegistrationNumber()
                                + " expires " + expiry + ".");
            }
        }
    }

    @Transactional(readOnly = true)
    public void checkCollectorRegistrations() {
        LocalDate today = LocalDate.now();
        LocalDate dueSoonCutoff = today.plusDays(REGISTRATION_DUE_SOON_DAYS);

        List<CollAgencyCollector> collectors = collectorRepository.findAllActiveWithRegistrationAcrossTenants();
        Map<TenantId, List<CollAgencyCollector>> expiredByTenant = collectors.stream()
                .filter(c -> c.getRegistrationExpiryDate().isBefore(today))
                .collect(Collectors.groupingBy(c -> TenantId.of(c.getTenantId())));
        expiredByTenant.forEach((tenantId, tenantCollectors) ->
                notifyCollectors(tenantId, tenantCollectors, NotificationType.COLLECTIONSAGENCY_COLLECTOR_REGISTRATION_EXPIRED,
                        "EXPIRED — collecting while unregistered is a criminal offence under the Debt Collectors Act"));

        Map<TenantId, List<CollAgencyCollector>> dueSoonByTenant = collectors.stream()
                .filter(c -> !c.getRegistrationExpiryDate().isBefore(today) && !c.getRegistrationExpiryDate().isAfter(dueSoonCutoff))
                .collect(Collectors.groupingBy(c -> TenantId.of(c.getTenantId())));
        dueSoonByTenant.forEach((tenantId, tenantCollectors) ->
                notifyCollectors(tenantId, tenantCollectors, NotificationType.COLLECTIONSAGENCY_COLLECTOR_REGISTRATION_DUE_SOON,
                        "expiring within " + REGISTRATION_DUE_SOON_DAYS + " days"));
    }

    @Transactional(readOnly = true)
    public void checkPaymentPlanInstallments() {
        LocalDate today = LocalDate.now();
        LocalDate dueSoonCutoff = today.plusDays(INSTALLMENT_DUE_SOON_DAYS);

        List<CollAgencyPaymentPlan> overdue = paymentPlanRepository.findActiveOverdueAcrossTenants(today);
        Map<TenantId, List<CollAgencyPaymentPlan>> overdueByTenant = overdue.stream()
                .collect(Collectors.groupingBy(p -> TenantId.of(p.getTenantId())));
        overdueByTenant.forEach((tenantId, plans) ->
                notifyPlans(tenantId, plans, NotificationType.COLLECTIONSAGENCY_PAYMENT_PLAN_INSTALLMENT_OVERDUE, "overdue"));

        List<CollAgencyPaymentPlan> dueSoon = paymentPlanRepository.findActiveWithInstallmentDueWithinAcrossTenants(today, dueSoonCutoff);
        Map<TenantId, List<CollAgencyPaymentPlan>> dueSoonByTenant = dueSoon.stream()
                .collect(Collectors.groupingBy(p -> TenantId.of(p.getTenantId())));
        dueSoonByTenant.forEach((tenantId, plans) ->
                notifyPlans(tenantId, plans, NotificationType.COLLECTIONSAGENCY_PAYMENT_PLAN_INSTALLMENT_DUE_SOON,
                        "due within " + INSTALLMENT_DUE_SOON_DAYS + " days"));
    }

    private void notifyOne(TenantId tenantId, NotificationType type, String title, String message) {
        List<Recipient> admins = tenantAdminRecipients.resolveTenantAdmins(tenantId);
        if (admins.isEmpty()) {
            log.warn("[Collections Agency] {} for tenant={} but no admin recipients could be resolved", title, tenantId.getValue());
            return;
        }
        notificationService.send(NotificationRequest.builder()
                .tenantId(tenantId).type(type).title(title).message(message)
                .actionUrl("/collections-agency/profile").sourceModule("collectionsagency").recipients(admins).build());
        log.info("[Collections Agency] {} sent tenant={}", title, tenantId.getValue());
    }

    private void notifyCollectors(TenantId tenantId, List<CollAgencyCollector> collectors, NotificationType type, String label) {
        List<Recipient> admins = tenantAdminRecipients.resolveTenantAdmins(tenantId);
        if (admins.isEmpty()) {
            log.warn("[Collections Agency] {} collector registration(s) {} for tenant={} but no admin recipients could be resolved",
                    collectors.size(), label, tenantId.getValue());
            return;
        }
        String title = collectors.size() + " collector registration(s) " + label;
        String message = collectors.stream()
                .map(c -> c.getFullName() + " — " + c.getRegistrationNumber() + " (expires " + c.getRegistrationExpiryDate() + ")")
                .collect(Collectors.joining(", "));
        notificationService.send(NotificationRequest.builder()
                .tenantId(tenantId).type(type).title(title).message(message)
                .actionUrl("/collections-agency/collectors").sourceModule("collectionsagency").recipients(admins).build());
        log.info("[Collections Agency] Collector registration alert sent tenant={} count={} label={}",
                tenantId.getValue(), collectors.size(), label);
    }

    private void notifyPlans(TenantId tenantId, List<CollAgencyPaymentPlan> plans, NotificationType type, String label) {
        List<Recipient> admins = tenantAdminRecipients.resolveTenantAdmins(tenantId);
        if (admins.isEmpty()) {
            log.warn("[Collections Agency] {} payment plan installment(s) {} for tenant={} but no admin recipients could be resolved",
                    plans.size(), label, tenantId.getValue());
            return;
        }
        // Enrich with debtor names for a legible message — a raw debtorAccountId UUID would be meaningless to a reader,
        // same reasoning DebtCollectionNotificationScheduler already applies for its own case-number enrichment.
        Map<java.util.UUID, CollAgencyDebtorAccount> accountsById = debtorAccountRepository
                .findAllById(plans.stream().map(CollAgencyPaymentPlan::getDebtorAccountId).distinct().toList()).stream()
                .collect(Collectors.toMap(CollAgencyDebtorAccount::getId, a -> a));

        String title = plans.size() + " payment plan installment(s) " + label;
        String message = plans.stream()
                .map(p -> {
                    CollAgencyDebtorAccount a = accountsById.get(p.getDebtorAccountId());
                    String debtorLabel = a != null ? a.getDebtorName() + " (" + a.getAccountReference() + ")"
                            : p.getDebtorAccountId().toString();
                    return debtorLabel + " — installment " + p.getInstallmentAmount() + " due " + p.getNextDueDate();
                })
                .collect(Collectors.joining(", "));
        notificationService.send(NotificationRequest.builder()
                .tenantId(tenantId).type(type).title(title).message(message)
                .actionUrl("/collections-agency/debtor-accounts").sourceModule("collectionsagency").recipients(admins).build());
        log.info("[Collections Agency] Payment plan installment alert sent tenant={} count={} label={}",
                tenantId.getValue(), plans.size(), label);
    }
}
