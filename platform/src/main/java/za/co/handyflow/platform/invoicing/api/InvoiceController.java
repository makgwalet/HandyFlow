// invoicing/api/InvoiceController.java

package za.co.handyflow.platform.invoicing.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.invoicing.application.internal.InvoicePdfService;
import za.co.handyflow.platform.invoicing.application.internal.InvoiceQueryService;
import za.co.handyflow.platform.invoicing.application.internal.InvoiceService;
import za.co.handyflow.platform.invoicing.dto.InvoiceResponse;
import za.co.handyflow.platform.invoicing.dto.RecordPaymentRequest;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/invoicing/invoices")
@RequiredArgsConstructor
@Tag(name = "Invoicing - Invoices", description = "Invoice management and PDF generation")
public class InvoiceController {

    private final InvoiceQueryService invoiceQueryService;
    private final InvoicePdfService   invoicePdfService;
    private final InvoiceService invoiceService;

    @GetMapping
    @PreAuthorize("hasAuthority('INVOICE_READ')")
    @Operation(summary = "List all invoices")
    public ResponseEntity<ApiResponse<Page<InvoiceResponse>>> getInvoices(
            @PageableDefault(size = 20) Pageable pageable
    ) {
        var tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(
                invoiceQueryService.getInvoices(tenantId, pageable)
        ));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('INVOICE_READ')")
    @Operation(summary = "Get invoice by ID")
    public ResponseEntity<ApiResponse<InvoiceResponse>> getInvoice(
            @PathVariable UUID id
    ) {
        var tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(
                invoiceQueryService.getInvoice(tenantId, id)
        ));
    }

    @GetMapping("/{id}/pdf")
    @PreAuthorize("hasAuthority('INVOICE_READ')")
    @Operation(summary = "Download VAT-compliant PDF invoice")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable UUID id) {
        var tenantId = TenantContext.getTenantIdAsObject();
        byte[] pdf = invoicePdfService.generateInvoicePdf(id, tenantId);

        // Fetch invoice number for filename
        var invoice = invoiceQueryService.getInvoice(tenantId, id);
        String filename = invoice.invoiceNumber() + ".pdf";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(pdf.length)
                .body(pdf);
    }

    @PostMapping("/{id}/issue")
    @PreAuthorize("hasAuthority('INVOICE_CREATE')")
    @Operation(summary = "Mark a draft invoice as issued — sets issuedAt timestamp")
    public ResponseEntity<ApiResponse<InvoiceResponse>> issueInvoice(
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Invoice issued",
                invoiceService.issueInvoice(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/{id}/payments")
    @PreAuthorize("hasAuthority('INVOICE_CREATE')")
    @Operation(summary = "Record a payment — auto-marks PAID when amountPaid >= total")
    public ResponseEntity<ApiResponse<InvoiceResponse>> recordPayment(
            @PathVariable UUID id,
            @Valid @RequestBody RecordPaymentRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Payment recorded",
                invoiceService.recordPayment(TenantContext.getTenantIdAsObject(), id, req)));
    }
}