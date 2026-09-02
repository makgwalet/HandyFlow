package za.co.handyflow.platform.legalpractice.api;

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
import za.co.handyflow.platform.legalpractice.application.internal.LpBillingService;
import za.co.handyflow.platform.legalpractice.dto.GenerateInvoiceRequest;
import za.co.handyflow.platform.legalpractice.dto.LpInvoiceResponse;
import za.co.handyflow.platform.legalpractice.dto.RecordInvoicePaymentRequest;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.UUID;

/** Invoice generation from billable work, and the ordinary business-account payment lifecycle. */
@RestController
@RequestMapping("/api/v1/legal-practice/invoices")
@RequiredArgsConstructor
@Tag(name = "Legal Practice - Invoices", description = "Invoice generation, sending, payment, write-off")
public class LpInvoiceController {

    private final LpBillingService billingService;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('LEGALPRACTICE_READ','LEGALPRACTICE_MANAGE','LEGALPRACTICE_ADMIN')")
    public ResponseEntity<ApiResponse<Page<LpInvoiceResponse>>> getInvoices(
            @RequestParam(required = false) UUID clientId, @PageableDefault(size = 50) Pageable pageable) {
        featureGuard.requireModule("legalpractice");
        var tenantId = TenantContext.getTenantIdAsObject();
        Page<LpInvoiceResponse> page = clientId != null
                ? billingService.listForClient(tenantId, clientId, pageable)
                : billingService.listForFirm(tenantId, pageable);
        return ResponseEntity.ok(ApiResponse.success(page));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('LEGALPRACTICE_READ','LEGALPRACTICE_MANAGE','LEGALPRACTICE_ADMIN')")
    public ResponseEntity<ApiResponse<LpInvoiceResponse>> getInvoice(@PathVariable UUID id) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.ok(ApiResponse.success(
                billingService.getInvoice(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/generate")
    @PreAuthorize("hasAnyAuthority('LEGALPRACTICE_MANAGE','LEGALPRACTICE_ADMIN')")
    @Operation(summary = "Generate an invoice from unbilled time entries/disbursements, or a fixed fee")
    public ResponseEntity<ApiResponse<LpInvoiceResponse>> generateInvoice(@Valid @RequestBody GenerateInvoiceRequest req) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Invoice generated",
                billingService.generateInvoice(TenantContext.getTenantIdAsObject(), req)));
    }

    @PostMapping("/{id}/send")
    @PreAuthorize("hasAnyAuthority('LEGALPRACTICE_MANAGE','LEGALPRACTICE_ADMIN')")
    public ResponseEntity<ApiResponse<LpInvoiceResponse>> sendInvoice(@PathVariable UUID id) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.ok(ApiResponse.success("Invoice sent",
                billingService.markSent(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/{id}/record-payment")
    @PreAuthorize("hasAnyAuthority('LEGALPRACTICE_MANAGE','LEGALPRACTICE_ADMIN')")
    @Operation(summary = "Record an ordinary business-account payment against this invoice")
    public ResponseEntity<ApiResponse<LpInvoiceResponse>> recordPayment(
            @PathVariable UUID id, @Valid @RequestBody RecordInvoicePaymentRequest req) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Payment recorded",
                billingService.recordPayment(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @PostMapping("/{id}/write-off")
    @PreAuthorize("hasAuthority('LEGALPRACTICE_ADMIN')")
    @Operation(summary = "Write off an unpaid invoice — ADMIN only")
    public ResponseEntity<ApiResponse<LpInvoiceResponse>> writeOff(@PathVariable UUID id) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.ok(ApiResponse.success("Invoice written off",
                billingService.writeOff(TenantContext.getTenantIdAsObject(), id)));
    }
}
