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
import za.co.handyflow.platform.facilitiesmanagement.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Billing workflow: prepare -> approve -> send -> payments.
 * <ul>
 *   <li>prepare, cancel a draft, send, record payments — ADMIN or FACILITIESMANAGEMENT_BILLING_MANAGE</li>
 *   <li>approve — ADMIN or FACILITIESMANAGEMENT_BILLING_APPROVE; the preparer cannot approve their own invoice
 *       (an ADMIN may; audited)</li>
 *   <li>reads — READ/MANAGE/ADMIN, BILLING_MANAGE, BILLING_APPROVE</li>
 * </ul>
 * All of it is limited to the caller's client scope; the invoice PDF checks scope first.
 */
@RestController
@RequestMapping("/api/v1/facilitiesmanagement")
@RequiredArgsConstructor
@Tag(name = "Facilities Management - Billing", description = "Client invoicing: prepare, approve, send, payments and ledger posting")
public class FmBillingController {

    private static final String CAN_READ = "hasAnyAuthority('FACILITIESMANAGEMENT_READ','FACILITIESMANAGEMENT_MANAGE','FACILITIESMANAGEMENT_ADMIN','FACILITIESMANAGEMENT_BILLING_MANAGE','FACILITIESMANAGEMENT_BILLING_APPROVE')";
    private static final String CAN_PREPARE = "hasAnyAuthority('FACILITIESMANAGEMENT_ADMIN','FACILITIESMANAGEMENT_BILLING_MANAGE')";
    private static final String CAN_APPROVE = "hasAnyAuthority('FACILITIESMANAGEMENT_ADMIN','FACILITIESMANAGEMENT_BILLING_APPROVE')";
    private static final String CAN_SEE_EXCEPTIONS = "hasAnyAuthority('FACILITIESMANAGEMENT_ADMIN','FACILITIESMANAGEMENT_BILLING_MANAGE','FACILITIESMANAGEMENT_BILLING_APPROVE')";

    private final FmBillingService billingService;
    private final FmPdfService pdfService;
    private final FeatureGuard featureGuard;

    @GetMapping("/invoices")
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<Page<FmInvoiceResponse>>> getInvoices(
            @RequestParam UUID clientId, @PageableDefault(size = 20) Pageable pageable) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.ok(ApiResponse.success(
                billingService.getInvoices(TenantContext.getTenantIdAsObject(), clientId, pageable)));
    }

    @GetMapping("/invoices/{id}")
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<FmInvoiceResponse>> getInvoice(@PathVariable UUID id) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.ok(ApiResponse.success(billingService.getInvoice(TenantContext.getTenantIdAsObject(), id)));
    }

    @GetMapping("/invoices/{id}/payments")
    @PreAuthorize(CAN_READ)
    @Operation(summary = "Payment history for an invoice")
    public ResponseEntity<ApiResponse<List<FmInvoicePaymentResponse>>> getPayments(@PathVariable UUID id) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.ok(ApiResponse.success(billingService.getPayments(TenantContext.getTenantIdAsObject(), id)));
    }

    @GetMapping(value = "/invoices/{id}/invoice.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize(CAN_READ)
    @Operation(summary = "Printable client invoice (a Tax Invoice when VAT is charged)")
    public ResponseEntity<byte[]> downloadInvoice(@PathVariable UUID id) {
        featureGuard.requireModule("facilitiesmanagement");
        billingService.requireAccessible(TenantContext.getTenantIdAsObject(), id);
        byte[] pdf = pdfService.generateInvoicePdf(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"invoice.pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @PostMapping("/clients/{clientId}/invoices/generate")
    @PreAuthorize(CAN_PREPARE)
    @Operation(summary = "Prepare a DRAFT invoice for a period: a retainer's flat fee (basis=RETAINER, agreementId if several) or chargeable work (basis=WORK_ORDERS). VAT added when registered. Must then be approved by someone else.")
    public ResponseEntity<ApiResponse<FmInvoiceResponse>> generateInvoice(
            @PathVariable UUID clientId, @Valid @RequestBody GenerateFmInvoiceRequest request) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Invoice prepared",
                billingService.generateInvoice(TenantContext.getTenantIdAsObject(), clientId, request.periodStart(), request.periodEnd(),
                        request.agreementId(), request.basis())));
    }

    @PostMapping("/invoices/{id}/approve")
    @PreAuthorize(CAN_APPROVE)
    @Operation(summary = "Approve a DRAFT invoice and post it to the ledger. The preparer cannot approve their own (ADMIN may — audited).")
    public ResponseEntity<ApiResponse<FmInvoiceResponse>> approveInvoice(@PathVariable UUID id) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.ok(ApiResponse.success("Invoice approved",
                billingService.approveInvoice(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/invoices/{id}/cancel")
    @PreAuthorize(CAN_PREPARE)
    @Operation(summary = "Cancel a DRAFT invoice; its work orders are released for re-billing. Approved invoices need a credit note instead.")
    public ResponseEntity<ApiResponse<FmInvoiceResponse>> cancelInvoice(@PathVariable UUID id, @Valid @RequestBody FmReasonRequest request) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.ok(ApiResponse.success("Invoice cancelled",
                billingService.cancelDraft(TenantContext.getTenantIdAsObject(), id, request.reason())));
    }

    @PostMapping("/invoices/{id}/send")
    @PreAuthorize(CAN_PREPARE)
    @Operation(summary = "Send an APPROVED invoice to the client")
    public ResponseEntity<ApiResponse<FmInvoiceResponse>> sendInvoice(@PathVariable UUID id) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.ok(ApiResponse.success("Invoice sent",
                billingService.sendInvoice(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/invoices/{id}/payments")
    @PreAuthorize(CAN_PREPARE)
    @Operation(summary = "Record a payment (date, method and reference optional); posts DR bank / CR receivables")
    public ResponseEntity<ApiResponse<FmInvoiceResponse>> recordPayment(
            @PathVariable UUID id, @Valid @RequestBody RecordFmPaymentRequest request) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.ok(ApiResponse.success("Payment recorded",
                billingService.recordPayment(TenantContext.getTenantIdAsObject(), id, request)));
    }

    // ── Ledger exceptions ────────────────────────────────────────────────────

    @GetMapping("/billing/gl-exceptions")
    @PreAuthorize(CAN_SEE_EXCEPTIONS)
    @Operation(summary = "Invoices and payments that failed to post to the ledger (most often a missing account in the chart of accounts)")
    public ResponseEntity<ApiResponse<List<FmGlExceptionResponse>>> glExceptions() {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.ok(ApiResponse.success(billingService.glExceptions(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping("/billing/gl-exceptions/retry")
    @PreAuthorize(CAN_APPROVE)
    @Operation(summary = "Re-queue every failed ledger posting with a fresh attempt budget (fix the cause first)")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> retryGlExceptions() {
        featureGuard.requireModule("facilitiesmanagement");
        int queued = billingService.retryGlExceptions(TenantContext.getTenantIdAsObject());
        return ResponseEntity.ok(ApiResponse.success("Re-queued", Map.of("requeued", queued)));
    }
}