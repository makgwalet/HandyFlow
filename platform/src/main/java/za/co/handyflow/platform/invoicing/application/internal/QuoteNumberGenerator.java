package za.co.handyflow.platform.invoicing.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.invoicing.domain.repository.QuoteRepository;
import za.co.handyflow.platform.shared.TenantId;

@Component
@RequiredArgsConstructor
class QuoteNumberGenerator {

    private final QuoteRepository quoteRepository;

    public String next(TenantId tenantId) {
        long count = quoteRepository.countAllByTenantId(tenantId);
        return "QT-%05d".formatted(count + 1);
        // WHY %05d? Zero-padded 5 digits: QT-00001, QT-00002...
        // Professional appearance + natural sort order in UIs.
    }
}
