package za.co.handyflow.platform.invoicing.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.shared.TenantId;

@Component
@RequiredArgsConstructor
public class CreditNoteNumberGenerator {

    private static final String SEQUENCE_NAME = "CREDIT_NOTE";

    private final TenantSequenceService sequenceService;

    /**
     * Same fix and same rationale as InvoiceNumberGenerator/
     * QuoteNumberGenerator — atomic read-and-increment via
     * TenantSequenceService, not a count()+1 race. "CREDIT_NOTE" is exactly
     * the sequence name TenantSequenceService's own doc comment already
     * anticipated.
     */
    public String next(TenantId tenantId) {
        long next = sequenceService.nextValue(tenantId, SEQUENCE_NAME);
        return "CN-%05d".formatted(next);
    }
}