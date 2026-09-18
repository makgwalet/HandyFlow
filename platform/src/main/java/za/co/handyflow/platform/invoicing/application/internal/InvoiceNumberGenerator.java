package za.co.handyflow.platform.invoicing.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.identity.TenantNumberingFacade;
import za.co.handyflow.platform.shared.TenantId;

@Component
@RequiredArgsConstructor
public class InvoiceNumberGenerator {

    private static final String SEQUENCE_NAME = "INVOICE";

    private final TenantNumberingFacade numberingFacade;

    /**
     * FIXED (previously): was count() + 1, a classic race condition — two
     * concurrent invoice creations (very plausible: InvoicingScheduler loops
     * over many due schedules in one run) could both read the same count
     * and produce duplicate numbers. The underlying counter is still the
     * atomic, race-free TenantSequenceService — unchanged by this class.
     *
     * MIGRATED to TenantNumberingFacade: this is the reference migration for
     * the platform-wide Tenant Numbering Engine (see TenantNumberingFacade
     * Javadoc). Old output: "INV-00001". New output:
     * "{tenantDocumentCode}-INV-00001", e.g. "FPS-INV-00001" — every invoice
     * now visibly identifies which tenant it belongs to, and no longer
     * collides in appearance with bookkeeping's or facilities-management's
     * own, separately-counted "INV-00001" series. Existing invoice numbers
     * already issued are untouched; only numbers issued from now on carry
     * the tenant code. The underlying "INVOICE" sequence counter keeps
     * incrementing from wherever it already was — no reset, no gap.
     */
    public String next(TenantId tenantId) {
        return numberingFacade.next(tenantId, SEQUENCE_NAME, "INV");
    }
}