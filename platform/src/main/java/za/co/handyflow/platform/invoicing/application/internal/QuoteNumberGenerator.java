package za.co.handyflow.platform.invoicing.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.shared.TenantSequenceService;

@Component
@RequiredArgsConstructor
class QuoteNumberGenerator {

    private static final String SEQUENCE_NAME = "QUOTE";

    private final TenantSequenceService sequenceService;

    /** Same fix and same rationale as InvoiceNumberGenerator — see there. */
    public String next(TenantId tenantId) {
        long next = sequenceService.nextValue(tenantId, SEQUENCE_NAME);
        return "QT-%05d".formatted(next);
    }
}