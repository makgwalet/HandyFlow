package za.co.handyflow.platform.collectionsagency.api;

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
import za.co.handyflow.platform.collectionsagency.application.internal.CollAgencyCommissionInvoiceService;
import za.co.handyflow.platform.collectionsagency.domain.model.CollAgencyCommissionInvoice;
import za.co.handyflow.platform.collectionsagency.dto.CommissionInvoiceResponse;
import za.co.handyflow.platform.collectionsagency.dto.RecordInvoicePaymentRequest;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.UUID;

/**
 * Read/settle side of commission invoices. Creation only ever happens as
 * part of CollAgencyTrustController's processRemittance() — there is no
 * standalone "create invoice" endpoint here, deliberately.
 */
@RestController
@RequestMapping("/api/v1/collections-agency")
@RequiredArgsConstructor
@Tag(name = "Collections Agency - Commission Invoices", description = "Commission invoices billed to creditor clients")
public class CollAgencyCommissionInvoiceController {

    private final CollAgencyCommissionInvoiceService invoiceService;
    private final FeatureGuard featureGuard;

    @GetMapping("/clients/{clientId}/commission-invoices")
    @PreAuthorize("hasAnyAuthority('COLLECTIONSAGENCY_READ','COLLECTIONSAGENCY_MANAGE','COLLECTIONSAGENCY_ADMIN')")
    public ResponseEntity<ApiResponse<Page<CommissionInvoiceResponse>>> list(@PathVariable UUID clientId,
            @PageableDefault(size = 24) Pageable pageable) {
        featureGuard.requireModule("collectionsagency");
        return ResponseEntity.ok(ApiResponse.success(
                invoiceService.listForClient(TenantContext.getTenantIdAsObject(), clientId, pageable)
                        .map(this::toResponse)));
    }

    @GetMapping("/commission-invoices/{id}")
    @PreAuthorize("hasAnyAuthority('COLLECTIONSAGENCY_READ','COLLECTIONSAGENCY_MANAGE','COLLECTIONSAGENCY_ADMIN')")
    public ResponseEntity<ApiResponse<CommissionInvoiceResponse>> get(@PathVariable UUID id) {
        featureGuard.requireModule("collectionsagency");
        return ResponseEntity.ok(ApiResponse.success(
                toResponse(invoiceService.get(TenantContext.getTenantIdAsObject(), id))));
    }

    @PostMapping("/commission-invoices/{id}/payments")
    @PreAuthorize("hasAnyAuthority('COLLECTIONSAGENCY_MANAGE','COLLECTIONSAGENCY_ADMIN')")
    @Operation(summary = "Record that this commission invoice has been settled — internal tracking only, does NOT post a second GL journal (see CollAgencyCommissionInvoice's own Javadoc)")
    public ResponseEntity<ApiResponse<CommissionInvoiceResponse>> recordPayment(@PathVariable UUID id,
            @Valid @RequestBody RecordInvoicePaymentRequest req) {
        featureGuard.requireModule("collectionsagency");
        return ResponseEntity.ok(ApiResponse.success("Payment recorded", toResponse(
                invoiceService.recordPayment(TenantContext.getTenantIdAsObject(), id, req.amount()))));
    }

    private CommissionInvoiceResponse toResponse(CollAgencyCommissionInvoice i) {
        return new CommissionInvoiceResponse(i.getId(), i.getClientId(), i.getInvoiceNumber(), i.getDescription(),
                i.getInvoiceDate(), i.getDueDate(), i.getSubtotal(), i.getVatAmount(), i.getTotal(),
                i.getAmountPaid(), i.balance(), i.getStatus(), i.getSentAt(), i.getPaidAt());
    }
}
