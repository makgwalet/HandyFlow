package za.co.handyflow.platform.invoicing.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.catalogue.CatalogueFacade;
import za.co.handyflow.platform.crm.CrmFacade;
import za.co.handyflow.platform.invoicing.domain.model.*;
import za.co.handyflow.platform.invoicing.domain.repository.*;
import za.co.handyflow.platform.invoicing.dto.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;
import org.springframework.beans.factory.annotation.Value;
import za.co.handyflow.platform.identity.TenantFacade;
import za.co.handyflow.platform.shared.EmailService;
import za.co.handyflow.platform.shared.EmailTemplates;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuoteService {

    private final QuoteRepository quoteRepository;
    private final InvoiceRepository invoiceRepository;
    private final CrmFacade crmFacade;
    private final CatalogueFacade catalogueFacade;
    private final QuoteNumberGenerator quoteNumberGenerator;
    private final TenantFacade tenantFacade;
    private final EmailService emailService;
    private final InvoicePdfService invoicePdfService;

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    @Transactional(readOnly = true)
    public Page<QuoteResponse> getQuotes(TenantId tenantId, Pageable pageable) {
        return quoteRepository.findAllActive(tenantId, pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public QuoteResponse getQuote(TenantId tenantId, UUID id) {
        return quoteRepository.findActiveById(tenantId, id)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Quote", id.toString()));
    }

    @Transactional
    public QuoteResponse createQuote(TenantId tenantId, CreateQuoteRequest request) {

        // Business rule: must have either a saved customer OR a walk-in name
        boolean hasCustomer = request.customerId() != null;
        boolean hasWalkin   = request.walkinClientName() != null
                && !request.walkinClientName().isBlank();

        if (!hasCustomer && !hasWalkin) {
            throw new IllegalArgumentException(
                    "Either a customer must be selected or a walk-in client name must be provided"
            );
        }

        // Only validate CRM if a customerId was actually provided
        if (hasCustomer && !crmFacade.customerExists(tenantId, request.customerId())) {
            throw new ResourceNotFoundException("Customer",
                    request.customerId().toString());
        }

        String quoteNumber = quoteNumberGenerator.next(tenantId);

        Quote quote = Quote.create(
                tenantId,
                request.customerId(),       // null for walk-ins — that's fine now
                quoteNumber,
                request.title(),
                request.walkinClientName(),
                request.walkinClientEmail(),
                request.walkinClientPhone()
        );

        quoteRepository.save(quote);
        log.info("Created quote={} tenant={} walkin={}",
                quoteNumber, tenantId, !hasCustomer);
        return toResponse(quote);
    }

    @Transactional
    public QuoteResponse addLineItem(TenantId tenantId, UUID quoteId,
                                     AddLineItemRequest request) {
        Quote quote = findActiveQuote(tenantId, quoteId);

        // If catalogueItemId provided, fetch defaults from catalogue
        BigDecimal vatRate = request.vatRate();
        if (request.catalogueItemId() != null && vatRate == null) {
            vatRate = catalogueFacade
                    .findItemById(tenantId, request.catalogueItemId())
                    .map(item -> item.vatRate())
                    .orElse(new BigDecimal("15.00"));
        }
        if (vatRate == null) vatRate = new BigDecimal("15.00");

        QuoteLineItem lineItem = QuoteLineItem.create(
                quote, tenantId,
                request.catalogueItemId(),
                request.description(),
                request.unit(),
                request.quantity(),
                request.unitPrice(),
                vatRate,
                quote.getLineItems().size()
        );

        quote.addLineItem(lineItem);
        quoteRepository.save(quote);
        return toResponse(quote);
    }

    @Transactional
    public QuoteResponse sendQuote(TenantId tenantId, UUID quoteId) {
        Quote quote = findActiveQuote(tenantId, quoteId);
        quote.send();
        quoteRepository.save(quote);
        log.info("Sent quote={} expires={}", quoteId, quote.getExpiresAt());
        return toResponse(quote);
    }

    @Transactional
    public QuoteResponse acceptQuote(TenantId tenantId, UUID quoteId) {
        Quote quote = findActiveQuote(tenantId, quoteId);
        quote.accept();
        quoteRepository.save(quote);
        return toResponse(quote);
    }

    @Transactional
    public UUID convertToInvoice(TenantId tenantId, UUID quoteId) {
        Quote quote = findActiveQuote(tenantId, quoteId);

        if (quote.getStatus() != QuoteStatus.ACCEPTED) {
            throw new IllegalStateException(
                    "Only ACCEPTED quotes can be converted to invoices"
            );
        }

        String invoiceNumber = "INV-" + quoteId.toString().substring(0, 8).toUpperCase();

        Invoice invoice = Invoice.createFromQuote(
                tenantId, quote.getCustomerId(),
                quote.getId(), invoiceNumber,
                quote.getSubtotal(), quote.getVatTotal(), quote.getTotal(),
                quote.getWalkinClientName(),    // ← add
                quote.getWalkinClientEmail(),   // ← add
                quote.getWalkinClientPhone()    // ← add
        );
        quote.getLineItems().forEach(qli -> {
            InvoiceLineItem ili = InvoiceLineItem.create(
                    invoice, tenantId,
                    qli.getCatalogueItemId(), qli.getDescription(),
                    qli.getUnit(), qli.getQuantity(), qli.getUnitPrice(),
                    qli.getVatRate(), qli.getSortOrder()
            );
            invoice.addLineItem(ili);
        });

        invoiceRepository.save(invoice);
        invoice.markIssued();
        quote.markInvoiced();
        quoteRepository.save(quote);

        log.info("Converted quote={} to invoice={}", quoteId, invoice.getId());

        // ── Email notification ─────────────────────────────────────────────
        // WHY async? PDF/email must not block the HTTP response.
        // If email fails, the invoice is already saved — we just log the error.
        try {
            tenantFacade.findTenantDetails(tenantId).ifPresent(tenant -> {

                String customerName = (quote.getCustomerId() != null)
                        ? crmFacade.findCustomerById(tenantId, quote.getCustomerId())
                        .map(c -> c.name()).orElse("Customer")
                        : (quote.getWalkinClientName() != null
                        ? quote.getWalkinClientName() : "Walk-in Client");

                String amount = "R " + String.format(
                        java.util.Locale.US, "%,.2f", invoice.getTotal());

                // Generate PDF to attach
                byte[] pdfBytes = invoicePdfService.generateInvoicePdf(
                        invoice.getId(), tenantId);

                // Send with PDF attachment — client can open directly, no login needed
                emailService.sendWithAttachment(
                        tenant.email(),
                        "Invoice " + invoiceNumber + " — " + customerName,
                        EmailTemplates.invoiceGeneratedWithPdf(
                                tenant.companyName(), invoiceNumber,
                                customerName, amount
                        ),
                        invoiceNumber + ".pdf",
                        pdfBytes
                );
            });
        } catch (Exception e) {
            log.warn("Invoice notification/PDF not sent: {}", e.getMessage());
        }


        return invoice.getId();
    }

    @Transactional
    public void softDeleteQuote(TenantId tenantId, UUID quoteId) {
        Quote quote = findActiveQuote(tenantId, quoteId);
        quote.softDelete(null);
        quoteRepository.save(quote);
    }

    private Quote findActiveQuote(TenantId tenantId, UUID quoteId) {
        return quoteRepository.findActiveById(tenantId, quoteId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Quote", quoteId.toString()
                ));
    }

    private QuoteResponse toResponse(Quote q) {
        var lineItems = q.getLineItems().stream()
                .map(li -> new LineItemResponse(
                        li.getId(), li.getCatalogueItemId(),
                        li.getDescription(), li.getUnit(),
                        li.getQuantity(), li.getUnitPrice(),
                        li.getVatRate(), li.getLineTotal(), li.getVatAmount()
                )).toList();

        return new QuoteResponse(
                q.getId(), q.getQuoteNumber(), q.getStatus().name(),
                q.getCustomerId(), q.getTitle(), q.getNotes(),
                q.getSubtotal(), q.getVatTotal(), q.getTotal(),
                q.getCurrency(), q.getSentAt(), q.getExpiresAt(),
                q.getAcceptedAt(), lineItems, q.getCreatedAt(),
                q.getWalkinClientName(),     // ← new
                q.getWalkinClientEmail(),    // ← new
                q.getWalkinClientPhone()     // ← new
        );
    }
}
