package za.co.handyflow.platform.debtcollection.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.debtcollection.domain.model.DebtCollectionCase;
import za.co.handyflow.platform.debtcollection.domain.model.PaymentPlan;
import za.co.handyflow.platform.debtcollection.domain.repository.DebtCollectionCaseRepository;
import za.co.handyflow.platform.debtcollection.domain.repository.PaymentPlanRepository;
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
 * Daily cross-tenant sweep for two proactive Debt Collection alerts: a
 * case's own next-action-due date (staff follow-up reminder) and a
 * PaymentPlan installment coming due or overdue. Same shape as every other
 * compliance/reminder scheduler in this codebase (see
 * LegalComplianceNotificationScheduler's own Javadoc for the established
 * pattern this follows exactly: one cross-tenant query, grouped by tenant
 * in Java, one notification per tenant to its resolved admins, no
 * dedup/idempotency since re-alerting daily on a still-due item is this
 * codebase's established convention).
 * <p>
 * Deliberately does NOT auto-mark a PaymentPlan DEFAULTED when its
 * installment is overdue — that's a human judgment call (a debtor might be
 * a few days late and still paying), so this only alerts; markDefaulted()
 * stays an explicit staff action via PaymentPlanController.
 * <p>
 * *** ACTION NEEDED IN NotificationType.java *** — see the accompanying
 * DebtCollection-NotificationType-patch-instructions.md. This class will
 * not compile until those four constants are added.
 * <p>
 * WHY 07:30? After PsiraComplianceScheduler (07:00) and
 * ArmouryComplianceScheduler (07:15) — next open slot in this codebase's
 * established 15-minute stagger convention for daily compliance/reminder
 * sweeps.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DebtCollectionNotificationScheduler {

    private static final int ACTION_DUE_SOON_DAYS = 3;
    private static final int INSTALLMENT_DUE_SOON_DAYS = 3;

    private final DebtCollectionCaseRepository caseRepository;
    private final PaymentPlanRepository paymentPlanRepository;
    private final NotificationService notificationService;
    private final TenantAdminRecipients tenantAdminRecipients;

    @Scheduled(cron = "0 30 7 * * *")
    public void checkDeadlines() {
        try {
            checkNextActionDates();
        } catch (Exception e) {
            log.error("[Debt Collection] Next-action-date sweep failed: {}", e.getMessage(), e);
        }
        try {
            checkPaymentPlanInstallments();
        } catch (Exception e) {
            log.error("[Debt Collection] Payment plan installment sweep failed: {}", e.getMessage(), e);
        }
    }

    @Transactional(readOnly = true)
    public void checkNextActionDates() {
        LocalDate today = LocalDate.now();
        LocalDate dueSoonCutoff = today.plusDays(ACTION_DUE_SOON_DAYS);
        List<DebtCollectionCase> cases = caseRepository.findWithNextActionDueWithinAcrossTenants(
                today.minusYears(1), dueSoonCutoff); // lower bound is generous — overdue cases can be arbitrarily old
        if (cases.isEmpty()) return;

        Map<TenantId, List<DebtCollectionCase>> overdueByTenant = cases.stream()
                .filter(c -> c.getNextActionDate().isBefore(today))
                .collect(Collectors.groupingBy(DebtCollectionCase::getTenantId));
        overdueByTenant.forEach((tenantId, tenantCases) ->
                notifyCases(tenantId, tenantCases, NotificationType.DEBTCOLLECTION_CASE_ACTION_OVERDUE, "overdue"));

        Map<TenantId, List<DebtCollectionCase>> dueSoonByTenant = cases.stream()
                .filter(c -> !c.getNextActionDate().isBefore(today) && !c.getNextActionDate().isAfter(dueSoonCutoff))
                .collect(Collectors.groupingBy(DebtCollectionCase::getTenantId));
        dueSoonByTenant.forEach((tenantId, tenantCases) ->
                notifyCases(tenantId, tenantCases, NotificationType.DEBTCOLLECTION_CASE_ACTION_DUE_SOON,
                        "due within " + ACTION_DUE_SOON_DAYS + " days"));
    }

    @Transactional(readOnly = true)
    public void checkPaymentPlanInstallments() {
        LocalDate today = LocalDate.now();
        LocalDate dueSoonCutoff = today.plusDays(INSTALLMENT_DUE_SOON_DAYS);

        List<PaymentPlan> overdue = paymentPlanRepository.findActiveOverdueAcrossTenants(today);
        Map<TenantId, List<PaymentPlan>> overdueByTenant = overdue.stream()
                .collect(Collectors.groupingBy(PaymentPlan::getTenantId));
        overdueByTenant.forEach((tenantId, plans) ->
                notifyPlans(tenantId, plans, NotificationType.DEBTCOLLECTION_PAYMENT_PLAN_INSTALLMENT_OVERDUE, "overdue"));

        List<PaymentPlan> dueSoon = paymentPlanRepository.findActiveWithInstallmentDueWithinAcrossTenants(today, dueSoonCutoff);
        Map<TenantId, List<PaymentPlan>> dueSoonByTenant = dueSoon.stream()
                .collect(Collectors.groupingBy(PaymentPlan::getTenantId));
        dueSoonByTenant.forEach((tenantId, plans) ->
                notifyPlans(tenantId, plans, NotificationType.DEBTCOLLECTION_PAYMENT_PLAN_INSTALLMENT_DUE_SOON,
                        "due within " + INSTALLMENT_DUE_SOON_DAYS + " days"));
    }

    private void notifyCases(TenantId tenantId, List<DebtCollectionCase> cases, NotificationType type, String label) {
        List<Recipient> admins = tenantAdminRecipients.resolveTenantAdmins(tenantId);
        if (admins.isEmpty()) {
            log.warn("[Debt Collection] {} case(s) {} for tenant={} but no admin recipients could be resolved",
                    cases.size(), label, tenantId.getValue());
            return;
        }
        String title = cases.size() + " debt collection case(s) with next action " + label;
        String message = cases.stream()
                .map(c -> c.getCaseNumber() + " — " + c.getDebtorName() + " (next action " + c.getNextActionDate() + ")")
                .collect(Collectors.joining(", "));

        notificationService.send(NotificationRequest.builder()
                .tenantId(tenantId)
                .type(type)
                .title(title)
                .message(message)
                .actionUrl("/debtcollection/cases")
                .sourceModule("debtcollection")
                .recipients(admins)
                .build());

        log.info("[Debt Collection] Next-action alert sent tenant={} count={} label={}",
                tenantId.getValue(), cases.size(), label);
    }

    private void notifyPlans(TenantId tenantId, List<PaymentPlan> plans, NotificationType type, String label) {
        List<Recipient> admins = tenantAdminRecipients.resolveTenantAdmins(tenantId);
        if (admins.isEmpty()) {
            log.warn("[Debt Collection] {} payment plan installment(s) {} for tenant={} but no admin recipients could be resolved",
                    plans.size(), label, tenantId.getValue());
            return;
        }
        // Enrich with case numbers/debtor names for a legible message — a raw caseId UUID would be meaningless to a reader.
        Map<java.util.UUID, DebtCollectionCase> casesById = caseRepository
                .findAllById(plans.stream().map(PaymentPlan::getCaseId).distinct().toList()).stream()
                .collect(Collectors.toMap(DebtCollectionCase::getId, c -> c));

        String title = plans.size() + " payment plan installment(s) " + label;
        String message = plans.stream()
                .map(p -> {
                    DebtCollectionCase c = casesById.get(p.getCaseId());
                    String label2 = c != null ? c.getCaseNumber() + " — " + c.getDebtorName() : p.getCaseId().toString();
                    return label2 + " (installment " + p.getInstallmentAmount() + " due " + p.getNextDueDate() + ")";
                })
                .collect(Collectors.joining(", "));

        notificationService.send(NotificationRequest.builder()
                .tenantId(tenantId)
                .type(type)
                .title(title)
                .message(message)
                .actionUrl("/debtcollection/cases")
                .sourceModule("debtcollection")
                .recipients(admins)
                .build());

        log.info("[Debt Collection] Payment plan installment alert sent tenant={} count={} label={}",
                tenantId.getValue(), plans.size(), label);
    }
}
