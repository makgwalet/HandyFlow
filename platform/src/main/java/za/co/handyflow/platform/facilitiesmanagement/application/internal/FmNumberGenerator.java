package za.co.handyflow.platform.facilitiesmanagement.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.shared.TenantSequenceService;

/**
 * Flat, tenant-scoped sequences for client codes, work order numbers, and
 * invoice numbers — same {@code TenantSequenceService.nextValue()} pattern
 * every other provider module's own number generator in this codebase uses
 * (FacilityNumberGenerator, TrainProvNumberGenerator). Sites, assets,
 * technicians and vendors don't need generated numbers, matching
 * {@code FacilityNumberGenerator}'s own established convention: a site is
 * identified by name, an asset by its own user-supplied {@code assetTag}.
 */
@Component
@RequiredArgsConstructor
public class FmNumberGenerator {

    private static final String CLIENT_SEQUENCE = "FM_CLIENT";
    private static final String WORKORDER_SEQUENCE = "FM_WORKORDER";
    private static final String INVOICE_SEQUENCE = "FM_INVOICE";

    private final TenantSequenceService sequenceService;

    public String nextClientCode(TenantId tenantId) {
        long seq = sequenceService.nextValue(tenantId, CLIENT_SEQUENCE);
        return "CLI-%04d".formatted(seq);
    }

    public String nextWorkOrderNumber(TenantId tenantId) {
        long seq = sequenceService.nextValue(tenantId, WORKORDER_SEQUENCE);
        return "WO-%05d".formatted(seq);
    }

    public String nextInvoiceNumber(TenantId tenantId) {
        long seq = sequenceService.nextValue(tenantId, INVOICE_SEQUENCE);
        return "INV-%05d".formatted(seq);
    }
}
