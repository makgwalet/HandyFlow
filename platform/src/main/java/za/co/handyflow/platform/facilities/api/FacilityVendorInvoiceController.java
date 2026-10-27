package za.co.handyflow.platform.facilities.api;

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
import za.co.handyflow.platform.facilities.application.internal.FacilityApprovalLimitService;
import za.co.handyflow.platform.facilities.application.internal.FacilityVendorInvoiceService;
import za.co.handyflow.platform.facilities.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/** Supplier invoices and their match to purchase orders, and the purchase order approval limits. */
@RestController
@RequestMapping("/api/v1/facilities")
@RequiredArgsConstructor
@Tag(name = "Facilities - Invoices & approval limits", description = "Three-way match of orders, receipts and supplier invoices")
public class FacilityVendorInvoiceController {

    private static final String CAN_VIEW = "hasAnyAuthority('FACILITIES_READ','FACILITIES_MANAGE','FACILITIES_ADMIN')";
    private static final String CAN_MANAGE = "hasAnyAuthority('FACILITIES_MANAGE','FACILITIES_ADMIN')";

    private final FacilityVendorInvoiceService invoiceService;
    private final FacilityApprovalLimitService limitService;
    private final FeatureGuard featureGuard;

    @GetMapping("/vendor-invoices")
    @PreAuthorize(CAN_VIEW)
    public ResponseEntity<ApiResponse<Page<VendorInvoiceResponse>>> list(@RequestParam(required = false) String status,
                                                                          @PageableDefault(size = 20) Pageable pageable) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success(invoiceService.list(TenantContext.getTenantIdAsObject(), status, pageable)));
    }

    @PostMapping("/vendor-invoices")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<VendorInvoiceResponse>> record(@Valid @RequestBody VendorInvoiceRequest request) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Invoice recorded",
                invoiceService.record(TenantContext.getTenantIdAsObject(), request)));
    }

    @PostMapping("/vendor-invoices/{id}/approve")
    @PreAuthorize(CAN_MANAGE)
    @Operation(summary = "Approve for payment. Someone other than who captured it; a reason is needed when billed above what was received")
    public ResponseEntity<ApiResponse<VendorInvoiceResponse>> approve(@PathVariable UUID id,
                                                                       @Valid @RequestBody(required = false) InvoiceDecisionRequest request) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success("Invoice approved", invoiceService.approve(TenantContext.getTenantIdAsObject(), id,
                request == null ? null : request.note())));
    }

    @PostMapping("/vendor-invoices/{id}/dispute")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<VendorInvoiceResponse>> dispute(@PathVariable UUID id, @Valid @RequestBody ReasonRequest request) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success("Invoice disputed",
                invoiceService.dispute(TenantContext.getTenantIdAsObject(), id, request.reason())));
    }

    @GetMapping("/purchase-orders/{id}/match")
    @PreAuthorize(CAN_VIEW)
    @Operation(summary = "Ordered vs received vs invoiced for one purchase order, with its invoices")
    public ResponseEntity<ApiResponse<PurchaseOrderMatchResponse>> match(@PathVariable UUID id) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success(invoiceService.match(TenantContext.getTenantIdAsObject(), id)));
    }

    // ── Approval limits (administrators) ─────────────────────────────────────

    @GetMapping("/approval-limits")
    @PreAuthorize(CAN_VIEW)
    public ResponseEntity<ApiResponse<List<ApprovalLimitResponse>>> limits() {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success(limitService.list(TenantContext.getTenantIdAsObject())));
    }

    @PutMapping("/approval-limits/{userId}")
    @PreAuthorize("hasAuthority('FACILITIES_ADMIN')")
    @Operation(summary = "Set the most this user may approve on one purchase order. Once any limit exists, approvers without one cannot approve; admins are never limited")
    public ResponseEntity<ApiResponse<ApprovalLimitResponse>> setLimit(@PathVariable UUID userId, @Valid @RequestBody ApprovalLimitRequest request) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success("Approval limit saved",
                limitService.set(TenantContext.getTenantIdAsObject(), userId, request.maxOrderValue())));
    }

    @DeleteMapping("/approval-limits/{userId}")
    @PreAuthorize("hasAuthority('FACILITIES_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> removeLimit(@PathVariable UUID userId) {
        featureGuard.requireModule("facilities");
        limitService.remove(TenantContext.getTenantIdAsObject(), userId);
        return ResponseEntity.ok(ApiResponse.success("Approval limit removed", null));
    }
}
