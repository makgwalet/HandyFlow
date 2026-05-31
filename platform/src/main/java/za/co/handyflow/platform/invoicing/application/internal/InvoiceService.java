package za.co.handyflow.platform.invoicing.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.invoicing.domain.model.Invoice;
import za.co.handyflow.platform.invoicing.domain.repository.InvoiceRepository;
import za.co.handyflow.platform.invoicing.dto.InvoiceResponse;
import za.co.handyflow.platform.invoicing.dto.RecordPaymentRequest;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceService {

    private final InvoiceRepository    invoiceRepo;
    private final InvoiceQueryService  queryService;

    @Transactional
    public InvoiceResponse recordPayment(TenantId tenantId, UUID id,
                                         RecordPaymentRequest req) {
        Invoice invoice = invoiceRepo.findActiveByIdWithLineItems(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", id.toString()));

        // WHY convert LocalDate to Instant?
        // paidAt is stored as Instant for precision.
        // paidDate from client is a calendar date — convert to start-of-day UTC.
        Instant paidAt = req.paidDate() != null
                ? req.paidDate().atStartOfDay().toInstant(ZoneOffset.UTC)
                : Instant.now();

        // Auto-issue if still DRAFT — recording payment implies it was sent
        invoice.markIssued();
        invoice.recordPayment(req.amountPaid(), paidAt);
        invoiceRepo.save(invoice);

        log.info("Payment recorded invoice={} amount={} status={}",
                invoice.getInvoiceNumber(), req.amountPaid(), invoice.getStatus());
        return queryService.getInvoice(tenantId, id);
    }

    @Transactional
    public InvoiceResponse issueInvoice(TenantId tenantId, UUID id) {
        Invoice invoice = invoiceRepo.findActiveByIdWithLineItems(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", id.toString()));
        invoice.markIssued();
        invoiceRepo.save(invoice);
        log.info("Issued invoice={}", invoice.getInvoiceNumber());
        return queryService.getInvoice(tenantId, id);
    }
}