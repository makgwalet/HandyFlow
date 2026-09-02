package za.co.handyflow.platform.facilities.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.shared.TenantSequenceService;

/**
 * Flat, tenant-scoped sequence for work order numbers (WO-00001, WO-00002, ...).
 * Sites, assets, technicians, and vendors don't need generated numbers — a
 * site is identified by name, an asset by its own user-supplied
 * {@code assetTag} (see FacilityAsset's own Javadoc), matching the
 * established convention that only genuinely transactional records get a
 * generated sequence number.
 */
@Component
@RequiredArgsConstructor
public class FacilityNumberGenerator {

    private final TenantSequenceService sequenceService;

    public String nextWorkOrderNumber(TenantId tenantId) {
        long seq = sequenceService.nextValue(tenantId, "FACILITY_WORKORDER");
        return "WO-%05d".formatted(seq);
    }
}
