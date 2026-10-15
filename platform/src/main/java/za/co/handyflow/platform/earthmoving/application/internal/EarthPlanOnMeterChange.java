package za.co.handyflow.platform.earthmoving.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Runs after the reading is saved, in its own transaction, so a failure here can never undo or block the reading.
 * The nightly check still runs and will pick up anything this misses.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EarthPlanOnMeterChange {
    private final EarthMaintenancePlanService plans;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onMeterChanged(MeterChanged e) {
        try {
            int raised = plans.scanAsset(e.tenantId(), e.assetId(), LocalDate.now(ZoneId.of("Africa/Johannesburg")));
            if (raised > 0) log.info("[Earthmoving] {} work order(s) raised after a meter change on asset {}", raised, e.assetId());
        } catch (Exception ex) {
            log.warn("[Earthmoving] Plan check after meter change failed for asset {}: {}", e.assetId(), ex.getMessage());
        }
    }
}
