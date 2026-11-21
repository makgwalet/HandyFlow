package za.co.handyflow.platform.security.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.security.domain.model.ShiftStatus;
import za.co.handyflow.platform.security.domain.repository.ShiftRepository;

import java.time.Duration;
import java.time.Instant;

/**
 * Turns shifts nobody worked into MISSED. Before this, a shift whose guard never
 * clocked in stayed SCHEDULED forever: reports showed 0 missed and completion rates fell.
 * A shift counts as missed once its end time is {@link #GRACE} in the past.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class ShiftMissedSweeper {

    /** Lease + per-tenant transactions (optional so hand-built instances in tests keep working). */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private SecurityJobRunner jobs;

    static final Duration GRACE = Duration.ofMinutes(30);

    private final ShiftRepository shiftRepository;

    /** Customer webhooks (optional so hand-built instances in tests keep working). */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private PublicApiService webhooks;

    static final int BATCH = 500;

    @Scheduled(cron = "0 */10 * * * *", zone = "Africa/Johannesburg")
    void sweep() {
        SecurityJobRunner.exclusively(jobs, "security-shift-missed-sweep", java.time.Duration.ofMinutes(9), this::sweepNow);
    }

    void sweepNow() {
        try {
            sweepAt(Instant.now());
        } catch (Exception e) {
            log.error("[Security] Missed-shift sweep failed: {}", e.getMessage(), e);
        }
    }

    int sweepAt(Instant now) {
        Instant cutoff = now.minus(GRACE);
        if (webhooks == null) {
            int n = shiftRepository.markEndedScheduledAsMissed(ShiftStatus.SCHEDULED, ShiftStatus.MISSED, cutoff, now);
            if (n > 0) log.info("[Security] Marked {} unworked shift(s) as MISSED", n);
            return n;
        }
        // Mark one by one so each shift that THIS run marked can raise SHIFT_MISSED exactly once,
        // even if two instances sweep at the same time (the second UPDATE matches 0 rows).
        int marked = 0;
        var batch = shiftRepository.findEndedScheduled(ShiftStatus.SCHEDULED, cutoff,
                org.springframework.data.domain.PageRequest.of(0, BATCH));
        for (var shift : batch) {
            if (shiftRepository.markMissedIfScheduled(shift.getId(), ShiftStatus.SCHEDULED, ShiftStatus.MISSED, now) == 1) {
                marked++;
                try {
                    webhooks.dispatchWebhookEvent(shift.getTenantId(), "SHIFT_MISSED", shift.getId(),
                            WebhookPayloads.event("SHIFT_MISSED", shift.getId(), shift.getSiteId(), null, null, now));
                } catch (Exception e) {
                    log.warn("[Security] SHIFT_MISSED webhook failed for shift={}: {}", shift.getId(), e.getMessage());
                }
            }
        }
        if (marked > 0) log.info("[Security] Marked {} unworked shift(s) as MISSED", marked);
        return marked;
    }
}
