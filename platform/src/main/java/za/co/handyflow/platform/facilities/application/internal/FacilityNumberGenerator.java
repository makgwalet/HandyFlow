package za.co.handyflow.platform.facilities.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.identity.TenantNumberingFacade;
import za.co.handyflow.platform.shared.TenantId;

/**
 * Flat, tenant-scoped sequence for work order numbers. Sites, assets,
 * technicians, and vendors don't need generated numbers — a site is
 * identified by name, an asset by its own user-supplied {@code assetTag}
 * (see FacilityAsset's own Javadoc), matching the established convention
 * that only genuinely transactional records get a generated sequence
 * number.
 * <p>
 * MIGRATED: this module's work order numbers ("WO-00001") were a real,
 * previously-identified collision with facilitiesmanagement's own
 * work-order numbers (same plain "WO-" format, unrelated documents) —
 * left deliberately unmigrated earlier because facilities' own
 * package-info.java didn't allow depending on identity, and expanding a
 * module's Modulith boundary shouldn't happen as a side effect of a
 * numbering fix. Now that tenant branding/platform-capability decisions
 * are being made deliberately (strategic roadmap backlog, Part 0,
 * Decision 2) rather than deferred, that boundary was widened
 * specifically for this. Same resolution pattern already used
 * successfully for trainingprovider vs. training's CRS-/CERT- collision:
 * migrate this side to a tenant-prefixed, distinctly-coded format
 * ("{tenantCode}-FWO-00001"), which fully disambiguates against
 * facilitiesmanagement's unchanged plain "WO-00001" without needing to
 * touch that module at all.
 */
@Component
@RequiredArgsConstructor
public class FacilityNumberGenerator {

    private static final String WORKORDER_SEQUENCE = "FACILITY_WORKORDER";

    private final TenantNumberingFacade numberingFacade;

    public String nextWorkOrderNumber(TenantId tenantId) {
        return numberingFacade.next(tenantId, WORKORDER_SEQUENCE, "FWO");
    }
}
