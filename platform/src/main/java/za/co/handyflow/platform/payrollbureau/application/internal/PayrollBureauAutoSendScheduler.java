package za.co.handyflow.platform.payrollbureau.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.payrollbureau.domain.model.PayRun;
import za.co.handyflow.platform.payrollbureau.domain.repository.PayRunRepository;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;

/**
 * Option C auto-send: humans still create and process every pay run
 * exactly as today — nothing about payroll calculation or review
 * changes. The ONLY thing this automates is the final "Email All
 * Payslips" click, for a run that's already PROCESSED and whose real,
 * human-chosen payDate has arrived (or passed, if processing happened
 * late) — so nobody has to remember to send them.
 * <p>
 * Deliberately does NOT read client.payDay — each PayRun already carries
 * its own concrete payDate, set by a human when that specific run was
 * created. That's the only trigger this feature needs.
 * <p>
 * Same convention as every other scheduler in this codebase
 * (ApBillDueSoonScheduler, InterviewReminderScheduler, DeskScheduler):
 * separate class, cross-tenant query (schedulers have no TenantContext —
 * they run outside any HTTP request), per-item try/catch isolation so
 * one bad run never blocks the rest of the batch, and a persisted
 * "already handled" flag (payslipsAutoSentAt) so a run already sent
 * today doesn't get emailed again tomorrow if it's still sitting
 * PROCESSED.
 * <p>
 * Reuses PayrollBureauService.emailPayslips() directly rather than
 * duplicating its logic — that method is the one already proven working
 * end-to-end tonight (real email, real PDF, real skip-handling for
 * employees with no address on file).
 * <p>
 * ASSUMES @EnableScheduling is already on somewhere in this app — same
 * assumption every other scheduler class in this codebase makes, never
 * independently re-verified by any of them either.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PayrollBureauAutoSendScheduler {

    private final PayRunRepository payRunRepo;
    private final PayrollBureauService bureauService;

    // Once daily, 08:00 SAST — chosen to run well after most staff and
    // clients are awake, but early enough in the day to still be useful
    // if anyone needs to notice and react to a delivery problem.
    @Scheduled(cron = "0 0 8 * * *", zone = "Africa/Johannesburg")
    @Transactional
    public void autoSendDuePayslips() {
        LocalDate today = LocalDate.now();
        List<PayRun> due = payRunRepo.findDueForAutoSend(today);

        int sent = 0;
        for (PayRun run : due) {
            try {
                // NOTE: emailPayslips() already gracefully handles
                // per-employee missing-email cases internally (returns
                // sent/skippedNoEmail counts) — that's not a failure at
                // this level. A failure here means the WHOLE run's
                // send attempt itself blew up (e.g. a DB error), in
                // which case we deliberately do NOT mark it auto-sent,
                // so tomorrow's run picks it up and retries.
                bureauService.emailPayslips(TenantId.of(run.getTenantId()), run.getId());
                run.markPayslipsAutoSent();
                payRunRepo.save(run);
                sent++;
            } catch (Exception e) {
                log.error("Auto-send failed for pay run={} tenant={}: {}",
                        run.getId(), run.getTenantId(), e.getMessage(), e);
            }
        }

        if (!due.isEmpty()) {
            log.info("PayrollBureauAutoSendScheduler: auto-sent payslips for {}/{} due pay runs",
                    sent, due.size());
        }
    }
}