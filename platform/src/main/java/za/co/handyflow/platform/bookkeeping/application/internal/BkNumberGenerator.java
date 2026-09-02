package za.co.handyflow.platform.bookkeeping.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.shared.TenantSequenceService;

/**
 * Flat, tenant-scoped sequences for client codes, journal entry numbers,
 * and invoice numbers — same {@code TenantSequenceService.nextValue()}
 * pattern every other provider module's own number generator in this
 * codebase uses (FmNumberGenerator, TrainProvNumberGenerator).
 */
@Component
@RequiredArgsConstructor
public class BkNumberGenerator {

    private static final String CLIENT_SEQUENCE = "BK_CLIENT";
    private static final String JOURNAL_SEQUENCE = "BK_JOURNAL";
    private static final String INVOICE_SEQUENCE = "BK_INVOICE";

    private final TenantSequenceService sequenceService;

    public String nextClientCode(TenantId tenantId) {
        long seq = sequenceService.nextValue(tenantId, CLIENT_SEQUENCE);
        return "CLI-%04d".formatted(seq);
    }

    public String nextEntryNumber(TenantId tenantId) {
        long seq = sequenceService.nextValue(tenantId, JOURNAL_SEQUENCE);
        return "JE-%05d".formatted(seq);
    }

    public String nextInvoiceNumber(TenantId tenantId) {
        long seq = sequenceService.nextValue(tenantId, INVOICE_SEQUENCE);
        return "INV-%05d".formatted(seq);
    }
}
