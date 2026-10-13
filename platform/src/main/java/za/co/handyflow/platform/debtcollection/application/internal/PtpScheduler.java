package za.co.handyflow.platform.debtcollection.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.debtcollection.domain.model.PromiseToPay;
import za.co.handyflow.platform.debtcollection.domain.repository.PromiseToPayRepository;

import java.time.LocalDate;

/**
 * Daily 07:40 cross-tenant PTP sweep — deliberately BEFORE WorkItemScheduler (07:45) so that
 * work items raised here are rescored by its pass the same morning.
 * Step 1: reminders — PTPs due within PtpService.REMINDER_DAYS_BEFORE_DUE get a PTP_DUE work item.
 * Step 2: payment check — every ACTIVE PTP is evaluated (kept as soon as paid; partial/broken only
 * once the grace period has elapsed). One record per transaction, each in its own try/catch.
 * A promise paid early leaves no stale reminder: evaluate() cancels the PTP_DUE item when it marks KEPT.
 * No @Transactional here on purpose — each PtpService call is its own transaction, so one bad record
 * cannot roll back the others.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PtpScheduler {

    private final PtpService ptpService;
    private final PromiseToPayRepository repository;

    @Scheduled(cron = "0 40 7 * * *")
    public void sweep() {
        LocalDate today = LocalDate.now();
        try {
            remind(today);
        } catch (Exception e) {
            log.error("[Debt Collection] PTP reminder step failed: {}", e.getMessage(), e);
        }
        try {
            evaluateAll(today);
        } catch (Exception e) {
            log.error("[Debt Collection] PTP evaluation step failed: {}", e.getMessage(), e);
        }
    }

    public void remind(LocalDate today) {
        for (PromiseToPay p : repository.findActiveDueWithinAcrossTenants(today,
                today.plusDays(PtpService.REMINDER_DAYS_BEFORE_DUE))) {
            try {
                ptpService.raiseReminder(p.getId());
            } catch (Exception e) {
                log.warn("[Debt Collection] PTP reminder failed ptp={}: {}", p.getId(), e.getMessage());
            }
        }
    }

    public void evaluateAll(LocalDate today) {
        for (PromiseToPay p : repository.findAllActiveAcrossTenants()) {
            try {
                ptpService.evaluate(p.getId(), today);
            } catch (Exception e) {
                log.warn("[Debt Collection] PTP evaluation failed ptp={}: {}", p.getId(), e.getMessage());
            }
        }
    }
}
