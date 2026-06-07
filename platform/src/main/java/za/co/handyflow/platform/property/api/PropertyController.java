// property/api/PropertyController.java

package za.co.handyflow.platform.property.api;

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
import za.co.handyflow.platform.property.application.internal.PropertyService;
import za.co.handyflow.platform.property.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/property")
@RequiredArgsConstructor
@Tag(name = "Property", description = "Property, unit, lease and payment management")
public class PropertyController {

    private final PropertyService propertyService;
    private final FeatureGuard    featureGuard;

    // ── Properties ────────────────────────────────────────────────────────────

    @GetMapping("/properties")
    @PreAuthorize("hasAuthority('USER_READ')")
    public ResponseEntity<ApiResponse<Page<PropertyResponse>>> getProperties(
            @PageableDefault(size = 20) Pageable pageable
    ) {
        featureGuard.requireModule("property");
        return ResponseEntity.ok(ApiResponse.success(
                propertyService.getProperties(TenantContext.getTenantIdAsObject(), pageable)));
    }

    @GetMapping("/properties/{id}")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "Get property with all units and vacancy summary")
    public ResponseEntity<ApiResponse<PropertyResponse>> getProperty(@PathVariable UUID id) {
        featureGuard.requireModule("property");
        return ResponseEntity.ok(ApiResponse.success(
                propertyService.getProperty(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/properties")
    @PreAuthorize("hasAuthority('USER_CREATE')")
    @Operation(summary = "Register a new property")
    public ResponseEntity<ApiResponse<PropertyResponse>> createProperty(
            @Valid @RequestBody CreatePropertyRequest request
    ) {
        featureGuard.requireModule("property");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Property created",
                        propertyService.createProperty(
                                TenantContext.getTenantIdAsObject(), request)));
    }

    @DeleteMapping("/properties/{id}")
    @PreAuthorize("hasAuthority('USER_DELETE')")
    public ResponseEntity<ApiResponse<Void>> deleteProperty(@PathVariable UUID id) {
        featureGuard.requireModule("property");
        propertyService.deleteProperty(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success("Property deleted", null));
    }

    // ── Units ─────────────────────────────────────────────────────────────────

    @GetMapping("/units")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "List units, optionally filter by propertyId or status")
    public ResponseEntity<ApiResponse<Page<UnitResponse>>> getUnits(
            @RequestParam(required = false) UUID propertyId,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        featureGuard.requireModule("property");
        return ResponseEntity.ok(ApiResponse.success(
                propertyService.getUnits(TenantContext.getTenantIdAsObject(),
                        propertyId, status, pageable)));
    }

    @PostMapping("/properties/{id}/units")
    @PreAuthorize("hasAuthority('USER_CREATE')")
    @Operation(summary = "Add a unit to a property — validates unit number uniqueness")
    public ResponseEntity<ApiResponse<UnitResponse>> addUnit(
            @PathVariable UUID id,
            @Valid @RequestBody CreateUnitRequest request
    ) {
        featureGuard.requireModule("property");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Unit added",
                        propertyService.addUnit(TenantContext.getTenantIdAsObject(), id, request)));
    }

    // ── Leases ────────────────────────────────────────────────────────────────

    @GetMapping("/leases")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "List all leases, optionally filter by status")
    public ResponseEntity<ApiResponse<Page<LeaseResponse>>> getLeases(
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        featureGuard.requireModule("property");
        return ResponseEntity.ok(ApiResponse.success(
                propertyService.getLeases(TenantContext.getTenantIdAsObject(),
                        status, pageable)));
    }

    @GetMapping("/leases/{id}")
    @PreAuthorize("hasAuthority('USER_READ')")
    public ResponseEntity<ApiResponse<LeaseResponse>> getLease(@PathVariable UUID id) {
        featureGuard.requireModule("property");
        return ResponseEntity.ok(ApiResponse.success(
                propertyService.getLease(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/units/{id}/leases")
    @PreAuthorize("hasAuthority('USER_CREATE')")
    @Operation(summary = "Create a lease for a unit — sets unit to OCCUPIED")
    public ResponseEntity<ApiResponse<LeaseResponse>> createLease(
            @PathVariable UUID id,
            @Valid @RequestBody CreateLeaseRequest request
    ) {
        featureGuard.requireModule("property");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Lease created",
                        propertyService.createLease(TenantContext.getTenantIdAsObject(),
                                id, request)));
    }

    @PostMapping("/leases/{id}/terminate")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    @Operation(summary = "Terminate a lease — sets unit back to VACANT")
    public ResponseEntity<ApiResponse<LeaseResponse>> terminateLease(
            @PathVariable UUID id,
            @RequestParam(required = false, defaultValue = "Terminated by landlord") String reason
    ) {
        featureGuard.requireModule("property");
        return ResponseEntity.ok(ApiResponse.success("Lease terminated",
                propertyService.terminateLease(TenantContext.getTenantIdAsObject(), id, reason)));
    }

    // ── Payments ──────────────────────────────────────────────────────────────

    @GetMapping("/leases/{id}/payments")
    @PreAuthorize("hasAuthority('USER_READ')")
    public ResponseEntity<ApiResponse<Page<PaymentResponse>>> getPayments(
            @PathVariable UUID id,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        featureGuard.requireModule("property");
        return ResponseEntity.ok(ApiResponse.success(
                propertyService.getPayments(TenantContext.getTenantIdAsObject(), id, pageable)));
    }

    @GetMapping("/payments/outstanding")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "List all outstanding payments across all leases — arrears management")
    public ResponseEntity<ApiResponse<List<PaymentResponse>>> getOutstanding() {
        featureGuard.requireModule("property");
        return ResponseEntity.ok(ApiResponse.success(
                propertyService.getOutstandingPayments(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping("/leases/{id}/payments")
    @PreAuthorize("hasAuthority('USER_CREATE')")
    @Operation(summary = "Create a payment record for a rental period")
    public ResponseEntity<ApiResponse<PaymentResponse>> createPayment(
            @PathVariable UUID id,
            @Valid @RequestBody CreatePaymentRequest request
    ) {
        featureGuard.requireModule("property");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Payment record created",
                        propertyService.createPaymentRecord(
                                TenantContext.getTenantIdAsObject(), id, request)));
    }

    @PostMapping("/leases/{leaseId}/payments/{paymentId}/record")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    @Operation(summary = "Record a rent payment — auto-computes PAID/PARTIAL status")
    public ResponseEntity<ApiResponse<PaymentResponse>> recordPayment(
            @PathVariable UUID leaseId,
            @PathVariable UUID paymentId,
            @Valid @RequestBody RecordPaymentRequest request
    ) {
        featureGuard.requireModule("property");
        return ResponseEntity.ok(ApiResponse.success("Payment recorded",
                propertyService.recordPayment(TenantContext.getTenantIdAsObject(),
                        leaseId, paymentId, request)));
    }

    // ── Inspections ───────────────────────────────────────────────────────────

    @GetMapping("/units/{id}/inspections")
    @PreAuthorize("hasAuthority('USER_READ')")
    public ResponseEntity<ApiResponse<Page<InspectionResponse>>> getInspections(
            @PathVariable UUID id,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        featureGuard.requireModule("property");
        return ResponseEntity.ok(ApiResponse.success(
                propertyService.getInspections(TenantContext.getTenantIdAsObject(), id, pageable)));
    }

    @PostMapping("/units/{id}/inspections")
    @PreAuthorize("hasAuthority('USER_CREATE')")
    @Operation(summary = "Record a move-in, move-out, or routine inspection")
    public ResponseEntity<ApiResponse<InspectionResponse>> createInspection(
            @PathVariable UUID id,
            @Valid @RequestBody CreateInspectionRequest request
    ) {
        featureGuard.requireModule("property");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Inspection recorded",
                        propertyService.createInspection(
                                TenantContext.getTenantIdAsObject(), id, request)));
    }


    @PutMapping("/leases/{id}")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    @Operation(summary = "Update lease terms — rent, end date, payment day, escalation rate")
    public ResponseEntity<ApiResponse<LeaseResponse>> updateLease(
            @PathVariable UUID id,
            @RequestBody UpdateLeaseRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Lease updated",
                propertyService.updateLease(
                        TenantContext.getTenantIdAsObject(), id, req)));
    }

    @PostMapping("/leases/{id}/renew")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    @Operation(summary = "Renew a lease — extend end date, auto-apply escalation if no new rent given")
    public ResponseEntity<ApiResponse<LeaseResponse>> renewLease(
            @PathVariable UUID id,
            @Valid @RequestBody RenewLeaseRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Lease renewed",
                propertyService.renewLease(
                        TenantContext.getTenantIdAsObject(), id, req)));
    }

    @PostMapping("/leases/{id}/escalate")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    @Operation(summary = "Apply rent escalation — percentage increase or fixed new amount")
    public ResponseEntity<ApiResponse<LeaseResponse>> escalateLease(
            @PathVariable UUID id,
            @RequestBody EscalateLeaseRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Rent escalated",
                propertyService.escalateLease(
                        TenantContext.getTenantIdAsObject(), id, req)));
    }
}