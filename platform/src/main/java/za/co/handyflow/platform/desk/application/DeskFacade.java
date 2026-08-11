package za.co.handyflow.platform.desk.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import za.co.handyflow.platform.desk.application.internal.DeskService;

/**
 * Public entry point for other modules that need to trigger Desk Support's
 * own scheduled operations. Currently exposes exactly one thing: the
 * SLA-breach sweep, because that's the only reason anything outside Desk
 * currently reaches in.
 * <p>
 * WHY THIS EXISTS: BillingScheduler previously injected
 * desk.application.internal.DeskService directly, confirmed by the real
 * ArchitectureVerificationTest run (Section 26.2/27.2 of the discovery
 * doc) — same shape as the AP violation above, fixed the same way.
 */
@Service
@RequiredArgsConstructor
public class DeskFacade {

    private final DeskService deskService;

    /**
     * Runs the SLA-breach sweep — finds tickets past their first-response
     * or resolution due time (excluding paused tickets, per
     * DeskTicketRepository.findSlaBreaches()) and marks them breached.
     * Idempotent per ticket, same reasoning as ApFacade.markOverdueBills().
     */
    public void checkSlaBreaches() {
        deskService.checkSlaBreaches();
    }
}