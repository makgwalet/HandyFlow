package za.co.handyflow.platform.bookkeeping.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.bookkeeping.application.internal.BkBillingService;
import za.co.handyflow.platform.bookkeeping.application.internal.BkPdfService;
import za.co.handyflow.platform.bookkeeping.dto.BkInvoiceResponse;
import za.co.handyflow.platform.bookkeeping.dto.GenerateBkInvoiceRequest;
import za.co.handyflow.platform.bookkeeping.dto.RecordBkPaymentRequest;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.UUID;

/**
 * Invoicing/billing — financially-critical operations (generating an
 * invoice, sending it, recording a payment) are ADMIN-only, matching this
 * codebase's own established convention for every other provider module's
 * billing controller (FmBillingController, TrainProvBillingController).
 * Reads remain open to READ/MANAGE/ADMIN.
 */
@RestController
@RequestMapping("/api/v1/bookkeeping")
@RequiredArgsConstructor
@Tag(name = "Bookkeeping - Billing", description = "Client invoicing, driven by service agreement type or billable time entries")
public class BkBillingController {

    private final BkBillingService billingService;
    private final BkPdfService pdfService;
    private final FeatureGuard featureGuard;

    @GetMapping("/invoices")
    @PreAuthorize("hasAnyAuthority('BOOKKEEPING_READ','BOOKKEEPING_MANAGE','BOOKKEEPING_ADMIN')")
    public ResponseEntity<ApiResponse<Page<BkInvoiceResponse>>> getInvoices(
            @RequestParam UUID clientId, @PageableDefault(size = 20) Pageable pageable) {
        featureGuard.requireModule("bookkeeping");
        return ResponseEntity.ok(ApiResponse.success(
                billingService.getInvoices(TenantContext.getTenantIdAsObject(), clientId, pageable)));
    }

    @GetMapping("/invoices/{id}")
    @PreAuthorize("hasAnyAuthority('BOOKKEEPING_READ','BOOKKEEPING_MANAGE','BOOKKEEPING_ADMIN')")
    public ResponseEntity<ApiResponse<BkInvoiceResponse>> getInvoice(@PathVariable UUID id) {
        featureGuard.requireModule("bookkeeping");
        return ResponseEntity.ok(ApiResponse.success(billingService.getInvoice(TenantContext.getTenantIdAsObject(), id)));
    }

    @GetMapping(value = "/invoices/{id}/invoice.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAnyAuthority('BOOKKEEPING_READ','BOOKKEEPING_MANAGE','BOOKKEEPING_ADMIN')")
    @Operation(summary = "Printable client invoice")
    public ResponseEntity<byte[]> downloadInvoice(@PathVariable UUID id) {
        featureGuard.requireModule("bookkeeping");
        byte[] pdf = pdfService.generateInvoicePdf(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"invoice.pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @GetMapping(value = "/clients/{clientId}/periods/{periodId}/trial-balance.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAnyAuthority('BOOKKEEPING_READ','BOOKKEEPING_MANAGE','BOOKKEEPING_ADMIN')")
    @Operation(summary = "Trial balance for one client/period")
    public ResponseEntity<byte[]> downloadTrialBalance(@PathVariable UUID clientId, @PathVariable UUID periodId) {
        featureGuard.requireModule("bookkeeping");
        byte[] pdf = pdfService.generateTrialBalancePdf(TenantContext.getTenantIdAsObject(), clientId, periodId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"trial-balance.pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @PostMapping("/clients/{clientId}/invoices/generate")
    @PreAuthorize("hasAuthority('BOOKKEEPING_ADMIN')")
    @Operation(summary = "Generate a client invoice for a billing period — ADMIN-only, financially-critical")
    public ResponseEntity<ApiResponse<BkInvoiceResponse>> generateInvoice(
            @PathVariable UUID clientId, @Valid @RequestBody GenerateBkInvoiceRequest request) {
        featureGuard.requireModule("bookkeeping");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Invoice generated",
                billingService.generateInvoice(TenantContext.getTenantIdAsObject(), clientId, request.periodStart(), request.periodEnd())));
    }

    @PostMapping("/invoices/{id}/send")
    @PreAuthorize("hasAuthority('BOOKKEEPING_ADMIN')")
    public ResponseEntity<ApiResponse<BkInvoiceResponse>> sendInvoice(@PathVariable UUID id) {
        featureGuard.requireModule("bookkeeping");
        return ResponseEntity.ok(ApiResponse.success("Invoice sent",
                billingService.sendInvoice(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/invoices/{id}/payments")
    @PreAuthorize("hasAuthority('BOOKKEEPING_ADMIN')")
    @Operation(summary = "Record a payment against an invoice — ADMIN-only, financially-critical")
    public ResponseEntity<ApiResponse<BkInvoiceResponse>> recordPayment(
            @PathVariable UUID id, @Valid @RequestBody RecordBkPaymentRequest request) {
        featureGuard.requireModule("bookkeeping");
        return ResponseEntity.ok(ApiResponse.success("Payment recorded",
                billingService.recordPayment(TenantContext.getTenantIdAsObject(), id, request.amount())));
    }
}
