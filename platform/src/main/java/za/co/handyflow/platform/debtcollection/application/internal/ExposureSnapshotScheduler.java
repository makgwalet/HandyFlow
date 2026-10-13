package za.co.handyflow.platform.debtcollection.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.debtcollection.domain.repository.ExposureSnapshotRepository;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;

/**
 * Daily 07:50 (after the 07:40 PTP and 07:45 work-item sweeps, so the snapshot reflects their effects).
 * One transaction per tenant via ExposureSnapshotService — a failing tenant is logged and skipped, never aborting the others.
 * Intentionally not @Transactional itself.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExposureSnapshotScheduler {

    private final ExposureSnapshotRepository repository;
    private final ExposureSnapshotService service;

    @Scheduled(cron = "0 50 7 * * *")
    public void run() {
        LocalDate today = LocalDate.now();
        for (TenantId tenantId : repository.findTenantsWithActiveCases()) {
            try {
                service.snapshot(tenantId, today);
            } catch (Exception e) {
                log.error("[Debt Collection] exposure snapshot failed tenant={}: {}", tenantId, e.getMessage(), e);
            }
        }
    }
}
