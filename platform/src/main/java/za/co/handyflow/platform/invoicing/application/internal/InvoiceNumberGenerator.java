package za.co.handyflow.platform.invoicing.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.invoicing.domain.repository.InvoiceRepository;
import za.co.handyflow.platform.shared.TenantId;

@Component
@RequiredArgsConstructor
public class InvoiceNumberGenerator {

    private final InvoiceRepository invoiceRepository;

    public String next(TenantId tenantId) {
        long count = invoiceRepository.countAllByTenantId(tenantId);
        return "INV-%05d".formatted(count + 1);
        // WHY %05d? Consistent zero-padded format: INV-00001, INV-00002 …
        // Matches QuoteNumberGenerator style; sorts naturally in UIs.
    }
}
