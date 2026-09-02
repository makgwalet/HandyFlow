package za.co.handyflow.platform.facilitiesmanagement.api;

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
import za.co.handyflow.platform.facilitiesmanagement.application.internal.FmBillingService;
import za.co.handyflow.platform.facilitiesmanagement.application.internal.FmPdfService;
import za.co.handyflow.platform.facilitiesmanagement.dto.FmInvoiceResponse;
import za.co.handyflow.platform.facilitiesmanagement.dto.GenerateFmInvoiceRequest;
import za.co.handyflow.platform.facilitiesmanagement.dto.RecordFmPaymentRequest;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.UUID;

/**
 * Invoicing/billing — financially-critical operations (generating an
 * invoice, sending it, recording a payment) are ADMIN-only, matching this
 * codebase's own established convention for every other provider module's
 * billing controller (TrainProvBillingController etc.). Reads remain open
 * to READ/MANAGE/ADMIN.
 */
@RestController
@RequestMapping("/api/v1/facilitiesmanagement")
@RequiredArgsConstructor
@Tag(name = "Facilities Management - Billing", description = "Client invoicing, driven by service agreement type or billable work orders")
public class FmBillingController {

    private final FmBillingService billingService;
    private final FmPdfService pdfService;
    private final FeatureGuard featureGuard;

    @GetMapping("/invoices")
    @PreAuthorize("hasAnyAuthority('FACILITIESMANAGEMENT_READ','FACILITIESMANAGEMENT_MANAGE','FACILITIESMANAGEMENT_ADMIN')")
    public ResponseEntity<ApiResponse<Page<FmInvoiceResponse>>> getInvoices(
            @RequestParam UUID clientId, @PageableDefault(size = 20) Pageable pageable) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.ok(ApiResponse.success(
                billingService.getInvoices(TenantContext.getTenantIdAsObject(), clientId, pageable)));
    }

    @GetMapping("/invoices/{id}")
    @PreAuthorize("hasAnyAuthority('FACILITIESMANAGEMENT_READ','FACILITIESMANAGEMENT_MANAGE','FACILITIESMANAGEMENT_ADMIN')")
    public ResponseEntity<ApiResponse<FmInvoiceResponse>> getInvoice(@PathVariable UUID id) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.ok(ApiResponse.success(billingService.getInvoice(TenantContext.getTenantIdAsObject(), id)));
    }

    @GetMapping(value = "/invoices/{id}/invoice.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAnyAuthority('FACILITIESMANAGEMENT_READ','FACILITIESMANAGEMENT_MANAGE','FACILITIESMANAGEMENT_ADMIN')")
    @Operation(summary = "Printable client invoice")
    public ResponseEntity<byte[]> downloadInvoice(@PathVariable UUID id) {
        featureGuard.requireModule("facilitiesmanagement");
        byte[] pdf = pdfService.generateInvoicePdf(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"invoice.pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @PostMapping("/clients/{clientId}/invoices/generate")
    @PreAuthorize("hasAuthority('FACILITIESMANAGEMENT_ADMIN')")
    @Operation(summary = "Generate a client invoice for a billing period — ADMIN-only, financially-critical")
    public ResponseEntity<ApiResponse<FmInvoiceResponse>> generateInvoice(
            @PathVariable UUID clientId, @Valid @RequestBody GenerateFmInvoiceRequest request) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Invoice generated",
                billingService.generateInvoice(TenantContext.getTenantIdAsObject(), clientId, request.periodStart(), request.periodEnd())));
    }

    @PostMapping("/invoices/{id}/send")
    @PreAuthorize("hasAuthority('FACILITIESMANAGEMENT_ADMIN')")
    public ResponseEntity<ApiResponse<FmInvoiceResponse>> sendInvoice(@PathVariable UUID id) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.ok(ApiResponse.success("Invoice sent",
                billingService.sendInvoice(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/invoices/{id}/payments")
    @PreAuthorize("hasAuthority('FACILITIESMANAGEMENT_ADMIN')")
    @Operation(summary = "Record a payment against an invoice — ADMIN-only, financially-critical")
    public ResponseEntity<ApiResponse<FmInvoiceResponse>> recordPayment(
            @PathVariable UUID id, @Valid @RequestBody RecordFmPaymentRequest request) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.ok(ApiResponse.success("Payment recorded",
                billingService.recordPayment(TenantContext.getTenantIdAsObject(), id, request.amount())));
    }
}
