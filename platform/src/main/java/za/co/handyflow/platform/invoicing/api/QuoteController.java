// invoicing/api/QuoteController.java

package za.co.handyflow.platform.invoicing.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.invoicing.application.internal.QuoteService;
import za.co.handyflow.platform.invoicing.application.internal.QuotePdfService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import za.co.handyflow.platform.invoicing.dto.AddLineItemRequest;
import za.co.handyflow.platform.invoicing.dto.CreateQuoteRequest;
import za.co.handyflow.platform.invoicing.dto.QuoteResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/invoicing/quotes")
@RequiredArgsConstructor
@Tag(name = "Invoicing - Quotes", description = "Quote management with 30-day auto-expiry")
public class QuoteController {

    private final QuoteService quoteService;
    private final QuotePdfService quotePdfService;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAuthority('INVOICE_READ')")
    @Operation(summary = "List all quotes")
    public ResponseEntity<ApiResponse<Page<QuoteResponse>>> getQuotes(
            @PageableDefault(size = 20) Pageable pageable
    ) {
        featureGuard.requireModule("invoicing");
        var tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(
                quoteService.getQuotes(tenantId, pageable)
        ));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('INVOICE_READ')")
    @Operation(summary = "Get quote by ID")
    public ResponseEntity<ApiResponse<QuoteResponse>> getQuote(@PathVariable UUID id) {
        featureGuard.requireModule("invoicing");
        var tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(
                quoteService.getQuote(tenantId, id)
        ));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('INVOICE_CREATE')")
    @Operation(summary = "Create a new quote")
    public ResponseEntity<ApiResponse<QuoteResponse>> createQuote(
            @Valid @RequestBody CreateQuoteRequest request
    ) {
        featureGuard.requireModule("invoicing");
        var tenantId = TenantContext.getTenantIdAsObject();
        var quote = quoteService.createQuote(tenantId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Quote created", quote));
    }

    @PostMapping("/{id}/line-items")
    @PreAuthorize("hasAuthority('INVOICE_CREATE')")
    @Operation(summary = "Add a line item to a quote")
    public ResponseEntity<ApiResponse<QuoteResponse>> addLineItem(
            @PathVariable UUID id,
            @Valid @RequestBody AddLineItemRequest request
    ) {
        featureGuard.requireModule("invoicing");
        var tenantId = TenantContext.getTenantIdAsObject();
        var quote = quoteService.addLineItem(tenantId, id, request);
        return ResponseEntity.ok(ApiResponse.success("Line item added", quote));
    }

    @PostMapping("/{id}/send")
    @PreAuthorize("hasAuthority('INVOICE_SEND')")
    @Operation(summary = "Send quote to customer â€” starts 30-day expiry countdown")
    public ResponseEntity<ApiResponse<QuoteResponse>> sendQuote(@PathVariable UUID id) {
        featureGuard.requireModule("invoicing");
        var tenantId = TenantContext.getTenantIdAsObject();
        var quote = quoteService.sendQuote(tenantId, id);
        return ResponseEntity.ok(ApiResponse.success("Quote sent", quote));
    }

    @PostMapping("/{id}/accept")
    @PreAuthorize("hasAuthority('INVOICE_CREATE')")
    @Operation(summary = "Accept a quote")
    public ResponseEntity<ApiResponse<QuoteResponse>> acceptQuote(@PathVariable UUID id) {
        featureGuard.requireModule("invoicing");
        var tenantId = TenantContext.getTenantIdAsObject();
        var quote = quoteService.acceptQuote(tenantId, id);
        return ResponseEntity.ok(ApiResponse.success("Quote accepted", quote));
    }

    @PostMapping("/{id}/convert-to-invoice")
    @PreAuthorize("hasAuthority('INVOICE_CREATE')")
    @Operation(summary = "Convert accepted quote to invoice")
    public ResponseEntity<ApiResponse<UUID>> convertToInvoice(@PathVariable UUID id) {
        featureGuard.requireModule("invoicing");
        var tenantId = TenantContext.getTenantIdAsObject();
        var invoiceId = quoteService.convertToInvoice(tenantId, id);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Invoice created", invoiceId));
    }

    @GetMapping("/{id}/pdf")
    @PreAuthorize("hasAuthority('INVOICE_READ')")
    @Operation(summary = "Download quote as PDF — send to client for approval")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable UUID id) {
        featureGuard.requireModule("invoicing");
        var tenantId = TenantContext.getTenantIdAsObject();
        byte[] pdf = quotePdfService.generateQuotePdf(id, tenantId);
        var quote = quoteService.getQuote(tenantId, id);
        String filename = quote.quoteNumber() + ".pdf";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(pdf.length)
                .body(pdf);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('INVOICE_DELETE')")
    @Operation(summary = "Soft delete a draft quote")
    public ResponseEntity<ApiResponse<Void>> deleteQuote(@PathVariable UUID id) {
        featureGuard.requireModule("invoicing");
        var tenantId = TenantContext.getTenantIdAsObject();
        quoteService.softDeleteQuote(tenantId, id);
        return ResponseEntity.ok(ApiResponse.success("Quote deleted", null));
    }
}

