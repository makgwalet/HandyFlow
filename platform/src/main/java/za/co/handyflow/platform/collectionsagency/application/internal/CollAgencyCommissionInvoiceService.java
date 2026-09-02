package za.co.handyflow.platform.collectionsagency.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.collectionsagency.domain.model.CollAgencyCommissionInvoice;
import za.co.handyflow.platform.collectionsagency.domain.repository.CollAgencyCommissionInvoiceRepository;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Read/settle side for commission invoices — creation itself only ever
 * happens as part of CollAgencyTrustTransactionService.processRemittance()
 * (one invoice per remittance run, immediately issued), never
 * independently here. See CollAgencyCommissionInvoice's own Javadoc for
 * why recordPayment() below does NOT post a second GL journal.
 */
@Service
@RequiredArgsConstructor
public class CollAgencyCommissionInvoiceService {

    private final CollAgencyCommissionInvoiceRepository repository;

    @Transactional(readOnly = true)
    public Page<CollAgencyCommissionInvoice> listForClient(TenantId tenantId, UUID clientId, Pageable pageable) {
        return repository.findByClient(tenantId.getValue(), clientId, pageable);
    }

    @Transactional(readOnly = true)
    public CollAgencyCommissionInvoice get(TenantId tenantId, UUID id) {
        return findActive(tenantId, id);
    }

    @Transactional
    public CollAgencyCommissionInvoice recordPayment(TenantId tenantId, UUID id, BigDecimal amount) {
        CollAgencyCommissionInvoice invoice = findActive(tenantId, id);
        invoice.recordPayment(amount);
        return repository.save(invoice);
    }

    private CollAgencyCommissionInvoice findActive(TenantId tenantId, UUID id) {
        return repository.findByTenantAndId(tenantId.getValue(), id)
                .orElseThrow(() -> new ResourceNotFoundException("CollAgencyCommissionInvoice", id.toString()));
    }
}
