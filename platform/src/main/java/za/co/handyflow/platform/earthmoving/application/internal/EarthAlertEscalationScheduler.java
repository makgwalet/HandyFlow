package za.co.handyflow.platform.earthmoving.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.earthmoving.domain.repository.EarthAlertRepository;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/** Every five minutes: tells the next level about alerts nobody has acknowledged in time. One company failing never stops the others. */
@Slf4j
@Component
@RequiredArgsConstructor
public class EarthAlertEscalationScheduler {
    private final EarthAlertRepository alerts;
    private final EarthAlertService service;

    @Scheduled(cron = "0 */5 * * * *", zone = "Africa/Johannesburg")
    public void run() { sweep(Instant.now()); }

    /** Returns how many alerts were escalated. */
    public int sweep(Instant now) {
        int n = 0;
        for (UUID t : alerts.findTenantIdsWithEscalationCandidates()) {
            try { n += service.escalateDue(TenantId.of(t), now); }
            catch (Exception e) { log.error("[Earthmoving] Alert escalation failed for tenant {}: {}", t, e.getMessage(), e); }
        }
        return n;
    }
}
