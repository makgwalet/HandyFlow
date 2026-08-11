package za.co.handyflow.platform.ap.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Owns AP's own scheduled maintenance jobs. Previously this trigger lived
 * in billing.BillingScheduler, which called ApFacade.markOverdueBills()
 * directly — a dependency that existed purely because "all @Scheduled
 * methods lived in one class", not for any architectural reason. That one
 * edge (billing -> ap) was the root cause of 5 of the 6 cycles found by
 * ArchitectureVerificationTest's second real run (see HandyFlow BOS
 * Discovery doc, Section 30) — every cycle funneled through
 * billing -> ap -> accounting -> {crm|identity|invoicing} -> billing.
 * Moving the trigger here, next to the logic it actually calls, removes
 * that edge entirely rather than declaring it away.
 * <p>
 * Same time/cron as before — no behavior change, just ownership.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApScheduler {

    private final ApService apService;

    // ── Daily at 03:00 SAST — mark overdue bills ──────────────────────────────
    @Scheduled(cron = "0 0 3 * * *", zone = "Africa/Johannesburg")
    void markOverdueBills() {
        log.info("ApScheduler: checking for overdue bills");
        apService.markOverdueBills();
    }
}