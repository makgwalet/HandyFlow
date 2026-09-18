package za.co.handyflow.platform.invoicing.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.identity.TenantNumberingFacade;
import za.co.handyflow.platform.shared.TenantId;

@Component
@RequiredArgsConstructor
public class CreditNoteNumberGenerator {

    private static final String SEQUENCE_NAME = "CREDIT_NOTE";

    private final TenantNumberingFacade numberingFacade;

    /**
     * Same migration and same rationale as InvoiceNumberGenerator/
     * QuoteNumberGenerator. Underlying counter is still the atomic
     * read-and-increment via TenantSequenceService, reached now through
     * TenantNumberingFacade. "CREDIT_NOTE" is exactly the sequence name
     * TenantSequenceService's own doc comment already anticipated.
     */
    public String next(TenantId tenantId) {
        return numberingFacade.next(tenantId, SEQUENCE_NAME, "CN");
    }
}