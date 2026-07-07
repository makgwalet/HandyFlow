package za.co.handyflow.platform.invoicing.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.shared.TenantId;

@Component
@RequiredArgsConstructor
public class InvoiceNumberGenerator {

    private static final String SEQUENCE_NAME = "INVOICE";

    private final TenantSequenceService sequenceService;

    /**
     * FIXED: was count() + 1, a classic race condition — two concurrent
     * invoice creations (very plausible: InvoicingScheduler loops over many
     * due schedules in one run) could both read the same count and produce
     * duplicate numbers. Now delegates to TenantSequenceService, which does
     * an atomic read-and-increment in a single SQL statement.
     *
     * WHY %05d still works after this change?
     * It means "at least 5 digits, zero-padded" — not a cap. A tenant's
     * 100,000th invoice becomes INV-100000, not an overflow or wraparound.
     * Same formatting contract as before, just backed by a race-free source
     * of truth.
     */
    public String next(TenantId tenantId) {
        long next = sequenceService.nextValue(tenantId, SEQUENCE_NAME);
        return "INV-%05d".formatted(next);
    }
}