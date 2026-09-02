package za.co.handyflow.platform.insurancebrokerage.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.insurancebrokerage.application.internal.InsBrokCommissionInvoiceService;
import za.co.handyflow.platform.insurancebrokerage.domain.model.InsBrokCommissionInvoice;
import za.co.handyflow.platform.insurancebrokerage.dto.InsBrokCommissionInvoiceResponse;
import za.co.handyflow.platform.insurancebrokerage.dto.RecordCommissionInvoicePaymentRequest;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.UUID;

/**
 * Read + settle side only — creation itself only ever happens as part of
 * {@code InsBrokPolicyService.activate()}/{@code renew()}; there is no
 * standalone "create invoice" endpoint here, deliberately, same
 * discipline {@code CollAgencyCommissionInvoiceController} already
 * documents for its own equivalent endpoint.
 */
@RestController
@RequestMapping("/api/v1/insurance-brokerage")
@RequiredArgsConstructor
@Tag(name = "Insurance Brokerage - Commission Invoices", description = "The brokerage's own earned commission, billed on policy activation")
public class InsBrokCommissionInvoiceController {

    private final InsBrokCommissionInvoiceService invoiceService;
    private final FeatureGuard featureGuard;

    @GetMapping("/commission-invoices")
    @PreAuthorize("hasAnyAuthority('INSURANCEBROKERAGE_READ','INSURANCEBROKERAGE_MANAGE','INSURANCEBROKERAGE_ADMIN')")
    public ResponseEntity<ApiResponse<Page<InsBrokCommissionInvoiceResponse>>> listAll(
            @PageableDefault(size = 24) Pageable pageable) {
        featureGuard.requireModule("insurancebrokerage");
        return ResponseEntity.ok(ApiResponse.success(
                invoiceService.listAll(TenantContext.getTenantIdAsObject(), pageable).map(this::toResponse)));
    }

    @GetMapping("/clients/{clientId}/commission-invoices")
    @PreAuthorize("hasAnyAuthority('INSURANCEBROKERAGE_READ','INSURANCEBROKERAGE_MANAGE','INSURANCEBROKERAGE_ADMIN')")
    public ResponseEntity<ApiResponse<Page<InsBrokCommissionInvoiceResponse>>> listForClient(@PathVariable UUID clientId,
            @PageableDefault(size = 24) Pageable pageable) {
        featureGuard.requireModule("insurancebrokerage");
        return ResponseEntity.ok(ApiResponse.success(
                invoiceService.listForClient(TenantContext.getTenantIdAsObject(), clientId, pageable).map(this::toResponse)));
    }

    @GetMapping("/commission-invoices/{id}")
    @PreAuthorize("hasAnyAuthority('INSURANCEBROKERAGE_READ','INSURANCEBROKERAGE_MANAGE','INSURANCEBROKERAGE_ADMIN')")
    public ResponseEntity<ApiResponse<InsBrokCommissionInvoiceResponse>> get(@PathVariable UUID id) {
        featureGuard.requireModule("insurancebrokerage");
        return ResponseEntity.ok(ApiResponse.success(toResponse(invoiceService.get(TenantContext.getTenantIdAsObject(), id))));
    }

    @PostMapping("/commission-invoices/{id}/payments")
    @PreAuthorize("hasAnyAuthority('INSURANCEBROKERAGE_MANAGE','INSURANCEBROKERAGE_ADMIN')")
    @Operation(summary = "Records that this commission invoice has been settled — internal tracking only, does NOT post a second GL journal (see InsBrokCommissionInvoice's own Javadoc)")
    public ResponseEntity<ApiResponse<InsBrokCommissionInvoiceResponse>> recordPayment(@PathVariable UUID id,
            @Valid @RequestBody RecordCommissionInvoicePaymentRequest req) {
        featureGuard.requireModule("insurancebrokerage");
        return ResponseEntity.ok(ApiResponse.success("Payment recorded", toResponse(
                invoiceService.recordPayment(TenantContext.getTenantIdAsObject(), id, req.amount()))));
    }

    private InsBrokCommissionInvoiceResponse toResponse(InsBrokCommissionInvoice i) {
        return new InsBrokCommissionInvoiceResponse(i.getId(), i.getClientId(), i.getPolicyId(), i.getInvoiceNumber(),
                i.getDescription(), i.getInvoiceDate(), i.getDueDate(), i.getSubtotal(), i.getVatAmount(), i.getTotal(),
                i.getAmountPaid(), i.balance(), i.getStatus(), i.getSentAt(), i.getPaidAt());
    }
}
