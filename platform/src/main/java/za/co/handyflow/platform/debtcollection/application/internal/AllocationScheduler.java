package za.co.handyflow.platform.debtcollection.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.debtcollection.domain.model.AllocationScope;
import za.co.handyflow.platform.debtcollection.domain.model.AllocationSettings;
import za.co.handyflow.platform.debtcollection.domain.repository.AllocationSettingsRepository;

/**
 * Every 15 minutes (at :05, :20, :35, :50 — clear of the 07:xx daily sweeps) for tenants that switched automatic
 * allocation ON: allocate unassigned cases (and, if configured, cases held by unavailable collectors), then make work
 * items follow their cases. Tenants are independent: one failing is logged and skipped. Intentionally not
 * @Transactional — AllocationService moves each case in its own transaction. Cases nobody is eligible for are logged at
 * INFO with counts and stay visible via GET /allocation/preview.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AllocationScheduler {

    private final AllocationSettingsRepository settingsRepository;
    private final AllocationService allocationService;

    @Scheduled(cron = "0 5/15 * * * *")
    public void run() {
        for (AllocationSettings s : settingsRepository.findAllEnabled()) {
            try {
                var scope = s.isReallocateUnavailable() ? AllocationScope.UNAVAILABLE : AllocationScope.UNASSIGNED;
                var result = allocationService.apply(s.getTenantId(), scope, null);
                int moved = allocationService.reconcileWorkItems(s.getTenantId());
                if (result.applied() > 0 || result.failed() > 0 || !result.plan().unallocated().isEmpty() || moved > 0) {
                    log.info("[Debt Collection] allocation tenant={} applied={} skipped={} failed={} unallocated={} workItemsMoved={}",
                            s.getTenantId(), result.applied(), result.skipped(), result.failed(),
                            result.plan().unallocated().size(), moved);
                }
            } catch (Exception e) {
                log.error("[Debt Collection] allocation run failed tenant={}: {}", s.getTenantId(), e.getMessage(), e);
            }
        }
    }
}
