package za.co.handyflow.platform.ap.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import za.co.handyflow.platform.ap.application.internal.ApService;

/**
 * Public entry point for other modules that need to trigger AP's own
 * scheduled operations. Currently exposes exactly one thing: the
 * overdue-bill sweep, because that's the only reason anything outside
 * AP currently reaches in.
 * <p>
 * WHY THIS EXISTS: BillingScheduler previously injected
 * ap.application.internal.ApService directly, confirmed by the real
 * ArchitectureVerificationTest run (Section 26.2/27.2 of the discovery
 * doc) — a Recruiter->HR-shaped violation, not a declaration gap. This
 * facade is the fix. Deliberately minimal, matching AccountingFacade's
 * own stated philosophy: expose only what's actually needed from outside,
 * not a general-purpose AP API.
 */
@Service
@RequiredArgsConstructor
public class ApFacade {

    private final ApService apService;

    /**
     * Runs the overdue-bill sweep — marks any APPROVED bill whose due date
     * has passed as OVERDUE. Idempotent: an already-OVERDUE bill matches
     * the same query again with no effect, so calling this more than once
     * (or on top of a retried scheduled job) is safe.
     */
    public void markOverdueBills() {
        apService.markOverdueBills();
    }
}