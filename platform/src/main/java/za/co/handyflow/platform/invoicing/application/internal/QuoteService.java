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
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.shared.UserContext;
import za.co.handyflow.platform.shared.VatRateProvider;
import org.springframework.beans.factory.annotation.Value;
import za.co.handyflow.platform.identity.TenantFacade;
import za.co.handyflow.platform.shared.EmailService;
import za.co.handyflow.platform.shared.EmailTemplates;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * FIX: backlog 4.6 (Piece A) — two fixes in this pass.
 * sendQuote() had a broken logCommunication() call referencing
 * invoiceNumber/customerName, neither of which exist in this method's
 * scope (those belong to convertToInvoice()) — a straightforward
 * copy-paste that would not have compiled. Corrected to this method's
 * own real variables (quote.getQuoteNumber(), clientName).
 * convertToInvoice()'s own email block, the one call site I originally
 * had fully confirmed, never actually had the call added at all —
 * added now.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuoteService {

    private final QuoteRepository quoteRepository;
    private final InvoiceRepository invoiceRepository;
    private final CrmFacade crmFacade;
    private final CatalogueFacade catalogueFacade;
    private final QuoteNumberGenerator quoteNumberGenerator;
    private final InvoiceNumberGenerator invoiceNumberGenerator;
    private final TenantFacade tenantFacade;
    private final EmailService emailService;
    private final InvoicePdfService invoicePdfService;
    private final QuotePdfService quotePdfService;
    private final InvoicePaymentTermsResolver paymentTermsResolver;
    private final StaffNotifier staffNotifier;
    // FIX (VAT sweep, module 2): replaces two independent
    // new BigDecimal("15.00") fallbacks below — see VatRateProvider's own
    // Javadoc for the fuller "scattered across the codebase" finding
    // this closes.
    private final VatRateProvider vatRateProvider;

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

        boolean hasCustomer = request.customerId() != null;
        boolean hasWalkin   = request.walkinClientName() != null
                && !request.walkinClientName().isBlank();

        if (!hasCustomer && !hasWalkin) {
            throw new IllegalArgumentException(
                    "Either a customer must be selected or a walk-in client name must be provided"
            );
        }

        if (hasCustomer && !crmFacade.customerExists(tenantId, request.customerId())) {
            throw new ResourceNotFoundException("Customer",
                    request.customerId().toString());
        }

        String quoteNumber = quoteNumberGenerator.next(tenantId);

        Quote quote = Quote.create(
                tenantId,
                request.customerId(),
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

        BigDecimal vatRate = request.vatRate();
        if (request.catalogueItemId() != null && vatRate == null) {
            vatRate = catalogueFacade
                    .findItemById(tenantId, request.catalogueItemId())
                    .map(item -> item.vatRate())
                    .orElse(vatRateProvider.ratePercent());
        }
        if (vatRate == null) vatRate = vatRateProvider.ratePercent();

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

    /**
     * Emails the quote PDF directly to the client (external recipient,
     * bypasses NotificationService — see StaffNotifier's Javadoc) and
     * raises an IN_APP entry for staff. The email now includes a public
     * accept/reject link (see acceptUrl below) using the quote's own
     * publicAccessToken — no login required for the client to respond.
     */
    @Transactional
    public QuoteResponse sendQuote(TenantId tenantId, UUID quoteId) {
        Quote quote = findActiveQuote(tenantId, quoteId);
        quote.send();
        quoteRepository.save(quote);
        log.info("Sent quote={} expires={}", quoteId, quote.getExpiresAt());

        try {
            String clientEmail = resolveClientEmail(quote, tenantId);
            if (clientEmail != null && !clientEmail.isBlank()) {
                String clientName = resolveClientName(quote, tenantId);
                tenantFacade.findTenantDetails(tenantId).ifPresent(tenant -> {
                    byte[] pdfBytes = quotePdfService.generateQuotePdf(quote.getId(), tenantId);
                    String amount = "R " + String.format(java.util.Locale.US, "%,.2f", quote.getTotal());
                    String acceptUrl = frontendUrl + "/q/" + quote.getPublicAccessToken();
                    emailService.sendWithAttachment(
                            clientEmail,
                            "Quote " + quote.getQuoteNumber() + " from " + tenant.companyName(),
                            EmailTemplates.quoteSentToClient(
                                    clientName, quote.getQuoteNumber(), tenant.companyName(), amount, acceptUrl),
                            quote.getQuoteNumber() + ".pdf",
                            pdfBytes
                    );
                    // FIX: backlog 4.6 (Piece A) — corrected: the previous
                    // version of this call referenced invoiceNumber/
                    // customerName, neither of which exist in this
                    // method — those belong to convertToInvoice(), not
                    // sendQuote(). Uses this method's own real variables.
                    // Logs this outbound quote email onto the customer's
                    // communication log automatically, same log
                    // CustomerCommunicationService already backs for
                    // manual entries. Only when quote.getCustomerId() is
                    // real — a walk-in quote's client email has nothing
                    // to log against.
                    if (quote.getCustomerId() != null) {
                        crmFacade.logCommunication(tenantId, quote.getCustomerId(),
                                "EMAIL", "OUTBOUND",
                                "Quote " + quote.getQuoteNumber() + " emailed to " + clientName,
                                java.time.Instant.now(), UserContext.getCurrentUserId());
                    }
                });
            } else {
                log.warn("Quote={} marked SENT but no client email on file — PDF not emailed", quoteId);
            }
        } catch (Exception e) {
            log.warn("Quote-sent email not delivered for quote={}: {}", quoteId, e.getMessage());
        }

        staffNotifier.notify(tenantId, NotificationType.QUOTE_SENT,
                "Quote sent: " + quote.getQuoteNumber(),
                "Quote " + quote.getQuoteNumber() + " was sent to the client.",
                frontendUrl + "/quotes/" + quoteId, quoteId.toString());

        return toResponse(quote);
    }

    @Transactional
    public QuoteResponse acceptQuote(TenantId tenantId, UUID quoteId) {
        Quote quote = findActiveQuote(tenantId, quoteId);
        quote.accept();
        quoteRepository.save(quote);

        staffNotifier.notify(tenantId, NotificationType.QUOTE_ACCEPTED,
                "Quote accepted: " + quote.getQuoteNumber(),
                "The client accepted quote " + quote.getQuoteNumber() + ". Ready to convert to an invoice.",
                frontendUrl + "/quotes/" + quoteId, quoteId.toString());

        return toResponse(quote);
    }

    @Transactional
    public QuoteResponse rejectQuote(TenantId tenantId, UUID quoteId) {
        Quote quote = findActiveQuote(tenantId, quoteId);
        quote.reject();
        quoteRepository.save(quote);
        log.info("Rejected quote={} tenant={}", quoteId, tenantId);

        staffNotifier.notify(tenantId, NotificationType.QUOTE_REJECTED,
                "Quote rejected: " + quote.getQuoteNumber(),
                "Quote " + quote.getQuoteNumber() + " was rejected.",
                frontendUrl + "/quotes/" + quoteId, quoteId.toString());

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

        String invoiceNumber = invoiceNumberGenerator.next(tenantId);

        Invoice invoice = Invoice.createFromQuote(
                tenantId, quote.getCustomerId(),
                quote.getId(), invoiceNumber,
                quote.getSubtotal(), quote.getVatTotal(), quote.getTotal(),
                quote.getWalkinClientName(),
                quote.getWalkinClientEmail(),
                quote.getWalkinClientPhone()
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
        LocalDate dueDate = paymentTermsResolver.resolveDueDate(tenantId, LocalDate.now());
        invoice.markIssued(dueDate);
        quote.markInvoiced();
        quoteRepository.save(quote);

        log.info("Converted quote={} to invoice={}", quoteId, invoice.getId());

        try {
            // FIX: this previously emailed tenant.email() — the business's own
            // company email — instead of the customer's. Every other send in
            // this file (sendQuote, sendExpiryReminder) correctly resolves the
            // client's email first and skips sending (with a log line) if none
            // is on file; this path had drifted from that pattern. Reusing
            // resolveClientEmail/resolveClientName here also removes the
            // duplicate inline customer-name lookup that was sitting right
            // next to the bug.
            String clientEmail = resolveClientEmail(quote, tenantId);
            if (clientEmail != null && !clientEmail.isBlank()) {
                String customerName = resolveClientName(quote, tenantId);
                tenantFacade.findTenantDetails(tenantId).ifPresent(tenant -> {

                    String amount = "R " + String.format(
                            java.util.Locale.US, "%,.2f", invoice.getTotal());

                    byte[] pdfBytes = invoicePdfService.generateInvoicePdf(
                            invoice.getId(), tenantId);

                    emailService.sendWithAttachment(
                            clientEmail,
                            "Invoice " + invoiceNumber + " — " + customerName,
                            EmailTemplates.invoiceGeneratedWithPdf(
                                    tenant.companyName(), invoiceNumber,
                                    customerName, amount
                            ),
                            invoiceNumber + ".pdf",
                            pdfBytes
                    );

                    // FIX: backlog 4.6 (Piece A) — was never actually
                    // added here (only a broken copy ended up in
                    // sendQuote() instead, now fixed separately). Logs
                    // this outbound invoice email onto the customer's
                    // communication log automatically, same log
                    // CustomerCommunicationService already backs for
                    // manual entries. Only when quote.getCustomerId() is
                    // real — a walk-in quote's client email has nothing
                    // to log against.
                    if (quote.getCustomerId() != null) {
                        crmFacade.logCommunication(tenantId, quote.getCustomerId(),
                                "EMAIL", "OUTBOUND",
                                "Invoice " + invoiceNumber + " emailed to " + customerName,
                                java.time.Instant.now(), UserContext.getCurrentUserId());
                    }
                });
            } else {
                log.warn("Invoice={} converted from quote={} but no client email on file — PDF not emailed",
                        invoiceNumber, quoteId);
            }
        } catch (Exception e) {
            log.warn("Invoice notification/PDF not sent: {}", e.getMessage());
        }

        return invoice.getId();
    }

    @Transactional
    public void softDeleteQuote(TenantId tenantId, UUID quoteId) {
        Quote quote = findActiveQuote(tenantId, quoteId);
        quote.softDelete(UserContext.getCurrentUserId());
        quoteRepository.save(quote);
    }

    /** Called by InvoicingScheduler's expiry-reminder job. */
    @Transactional
    void sendExpiryReminder(Quote quote) {
        try {
            long daysRemaining = java.time.Duration.between(
                    java.time.Instant.now(), quote.getExpiresAt()).toDays() + 1;

            String clientEmail = resolveClientEmail(quote, quote.getTenantId());
            if (clientEmail != null && !clientEmail.isBlank()) {
                tenantFacade.findTenantDetails(quote.getTenantId()).ifPresent(tenant -> {
                    String clientName = resolveClientName(quote, quote.getTenantId());
                    String amount = "R " + String.format(java.util.Locale.US, "%,.2f", quote.getTotal());
                    String acceptUrl = frontendUrl + "/q/" + quote.getPublicAccessToken();
                    emailService.send(
                            clientEmail,
                            "Quote " + quote.getQuoteNumber() + " is expiring soon",
                            EmailTemplates.quoteExpiringSoon(
                                    clientName, quote.getQuoteNumber(), tenant.companyName(),
                                    amount, (int) daysRemaining, acceptUrl)
                    );
                });
            }

            staffNotifier.notify(quote.getTenantId(), NotificationType.QUOTE_EXPIRING_SOON,
                    "Quote expiring soon: " + quote.getQuoteNumber(),
                    "Quote " + quote.getQuoteNumber() + " expires in " + daysRemaining + " day(s).",
                    frontendUrl + "/quotes/" + quote.getId(), quote.getId().toString());

            quote.markExpiryReminderSent();
            quoteRepository.save(quote);
        } catch (Exception e) {
            log.warn("Failed to send expiry reminder for quote={}: {}", quote.getId(), e.getMessage());
        }
    }

    private Quote findActiveQuote(TenantId tenantId, UUID quoteId) {
        return quoteRepository.findActiveById(tenantId, quoteId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Quote", quoteId.toString()
                ));
    }

    private String resolveClientEmail(Quote quote, TenantId tenantId) {
        if (quote.getCustomerId() != null) {
            return crmFacade.findCustomerById(tenantId, quote.getCustomerId())
                    .map(c -> c.email())
                    .orElse(null);
        }
        return quote.getWalkinClientEmail();
    }

    private String resolveClientName(Quote quote, TenantId tenantId) {
        if (quote.getCustomerId() != null) {
            return crmFacade.findCustomerById(tenantId, quote.getCustomerId())
                    .map(c -> c.name()).orElse("Customer");
        }
        return quote.getWalkinClientName() != null ? quote.getWalkinClientName() : "Client";
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
                q.getWalkinClientName(),
                q.getWalkinClientEmail(),
                q.getWalkinClientPhone(),
                q.getFirstViewedAt(),
                q.getLastViewedAt(),
                q.getViewCount()
        );
    }
}