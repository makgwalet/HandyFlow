package za.co.handyflow.platform.legalcompliance.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.legalcompliance.domain.model.SnapshotSource;
import za.co.handyflow.platform.shared.TenantId;

/**
 * 07:18 on the 1st of each month: records a posture snapshot for each tenant that uses the module. A tenant that already has one for the day (for example a
 * manual snapshot) is skipped. One tenant failing does not stop the rest. Tenants are found through their obligations and earlier snapshots
 * (ExecutiveService.tenantsForMonthlySnapshot), so a tenant with no obligations and no earlier snapshot is not covered.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LegalComplianceSnapshotScheduler {

    private final ExecutiveService executive;

    @Scheduled(cron = "0 18 7 1 * *")
    public void monthly() {
        int taken = 0, skipped = 0, failed = 0;
        for (TenantId t : executive.tenantsForMonthlySnapshot()) {
            try {
                executive.takeSnapshot(t, SnapshotSource.MONTHLY, "System (monthly)", null);
                taken++;
            } catch (za.co.handyflow.platform.shared.HandyFlowException e) {
                skipped++;
            } catch (Exception e) {
                failed++;
                log.error("[Legal/Compliance] Monthly snapshot failed for tenant={}: {}", t.getValue(), e.getMessage(), e);
            }
        }
        log.info("[Legal/Compliance] Monthly posture snapshots: {} taken, {} skipped, {} failed", taken, skipped, failed);
    }
}
