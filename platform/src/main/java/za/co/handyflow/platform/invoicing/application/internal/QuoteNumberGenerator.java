package za.co.handyflow.platform.invoicing.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.identity.TenantNumberingFacade;
import za.co.handyflow.platform.shared.TenantId;

@Component
@RequiredArgsConstructor
class QuoteNumberGenerator {

    private static final String SEQUENCE_NAME = "QUOTE";

    private final TenantNumberingFacade numberingFacade;

    /** Same migration and same rationale as InvoiceNumberGenerator — see there. */
    public String next(TenantId tenantId) {
        return numberingFacade.next(tenantId, SEQUENCE_NAME, "QT");
    }
}