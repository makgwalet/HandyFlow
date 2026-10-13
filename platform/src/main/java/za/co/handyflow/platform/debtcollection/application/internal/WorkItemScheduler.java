package za.co.handyflow.platform.debtcollection.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.debtcollection.domain.model.DebtCollectionCase;
import za.co.handyflow.platform.debtcollection.domain.model.PaymentPlan;
import za.co.handyflow.platform.debtcollection.domain.model.WorkItem;
import za.co.handyflow.platform.debtcollection.domain.model.WorkItemType;
import za.co.handyflow.platform.debtcollection.domain.repository.DebtCollectionCaseRepository;
import za.co.handyflow.platform.debtcollection.domain.repository.PaymentPlanRepository;
import za.co.handyflow.platform.debtcollection.domain.repository.WorkItemRepository;

import java.time.LocalDate;

/**
 * Daily 07:45 cross-tenant sweep (next slot after DebtCollectionNotificationScheduler at 07:30):
 * <ol>
 *   <li>wake snoozed items whose date has arrived</li>
 *   <li>raise work items from existing triggers — case next-action date due/overdue,
 *       payment-plan instalment due today, instalment overdue</li>
 *   <li>rescore every live item (ages, due dates shift) and cancel items on closed cases</li>
 * </ol>
 * Idempotent via sourceKey: "TYPE:entityId:triggerDate". Because the date is in the key,
 * a rescheduled next-action or a new instalment due date legitimately raises a fresh item,
 * while a completed item is not re-raised for the same trigger. Trade-off: an overdue
 * instalment item that was completed without payment is not re-raised daily — the chunk-2
 * PTP engine will own re-escalation. Each record is handled in its own try/catch so one bad
 * row never aborts the sweep. No @Transactional here on purpose: each WorkItemService call is its own
 * transaction, so one bad record cannot roll back the others.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkItemScheduler {

    private final WorkItemService workItemService;
    private final WorkItemRepository workItemRepository;
    private final DebtCollectionCaseRepository caseRepository;
    private final PaymentPlanRepository paymentPlanRepository;

    @Scheduled(cron = "0 45 7 * * *")
    public void sweep() {
        LocalDate today = LocalDate.now();
        step("wake", () -> wakeExpired(today));
        step("generate", () -> generate(today));
        step("rescore", () -> rescoreAll(today));
    }

    private void step(String name, Runnable r) {
        try {
            r.run();
        } catch (Exception e) {
            log.error("[Debt Collection] work-item {} step failed: {}", name, e.getMessage(), e);
        }
    }

    public void wakeExpired(LocalDate today) {
        for (WorkItem w : workItemRepository.findSnoozeExpiredAcrossTenants(today)) {
            try {
                workItemService.wakeExpired(w.getId());
            } catch (Exception e) {
                log.warn("[Debt Collection] wake failed workItem={}: {}", w.getId(), e.getMessage());
            }
        }
    }

    public void generate(LocalDate today) {
        for (DebtCollectionCase c : caseRepository.findWithNextActionDueWithinAcrossTenants(today.minusYears(1), today)) {
            raise(c.getTenantId(), c.getId(), WorkItemType.FOLLOW_UP_DUE,
                    "Follow up " + c.getCaseNumber() + " — " + c.getDebtorName(), c.getNextActionDate(),
                    "FOLLOW_UP:" + c.getId() + ":" + c.getNextActionDate());
        }
        for (PaymentPlan p : paymentPlanRepository.findActiveOverdueAcrossTenants(today)) {
            raise(p.getTenantId(), p.getCaseId(), WorkItemType.INSTALLMENT_OVERDUE,
                    "Instalment of R" + p.getInstallmentAmount() + " overdue — confirm payment or contact debtor",
                    p.getNextDueDate(), "INSTALLMENT_OVERDUE:" + p.getId() + ":" + p.getNextDueDate());
        }
        for (PaymentPlan p : paymentPlanRepository.findActiveWithInstallmentDueWithinAcrossTenants(today, today)) {
            raise(p.getTenantId(), p.getCaseId(), WorkItemType.INSTALLMENT_DUE,
                    "Instalment of R" + p.getInstallmentAmount() + " due today — verify payment received",
                    p.getNextDueDate(), "INSTALLMENT_DUE:" + p.getId() + ":" + p.getNextDueDate());
        }
    }

    public void rescoreAll(LocalDate today) {
        for (WorkItem w : workItemRepository.findAllLiveAcrossTenants()) {
            try {
                workItemService.refreshLive(w.getId(), today);
            } catch (Exception e) {
                log.warn("[Debt Collection] rescore failed workItem={}: {}", w.getId(), e.getMessage());
            }
        }
    }

    private void raise(za.co.handyflow.platform.shared.TenantId tenantId, java.util.UUID caseId, WorkItemType type,
                       String title, LocalDate due, String sourceKey) {
        try {
            workItemService.raise(tenantId, caseId, type, title, due, sourceKey, null, "system");
        } catch (Exception e) {
            // Closed case / missing case etc. — skip, never abort the sweep.
            log.debug("[Debt Collection] skip raise {} case={}: {}", type, caseId, e.getMessage());
        }
    }
}
