package za.co.handyflow.platform.internalaudit.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.internalaudit.domain.repository.AuditUniverseEntryRepository;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

/** Nightly monitoring run for every company that uses internal audit, with the default thresholds. One company failing never stops the others. */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditMonitoringScheduler {
    private final AuditUniverseEntryRepository universe;
    private final AuditMonitoringService monitoring;

    @Scheduled(cron = "0 30 2 * * *", zone = "Africa/Johannesburg")
    public void nightly() { sweep(LocalDate.now(ZoneId.of("Africa/Johannesburg"))); }

    /** Returns how many new alerts were filed. */
    public int sweep(LocalDate today) {
        int fresh = 0;
        for (UUID t : universe.findTenantIdsWithAudit()) {
            try { fresh += monitoring.scan(TenantId.of(t), today, null).newAlerts(); }
            catch (Exception e) { log.error("[InternalAudit] Monitoring scan failed for tenant {}: {}", t, e.getMessage(), e); }
        }
        return fresh;
    }
}
