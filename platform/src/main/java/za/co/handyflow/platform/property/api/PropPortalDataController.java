package za.co.handyflow.platform.property.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.property.application.internal.PropPortalDataService;
import za.co.handyflow.platform.property.dto.PropPortalInspectionResponse;
import za.co.handyflow.platform.property.dto.PropPortalLeaseSummaryResponse;
import za.co.handyflow.platform.property.dto.PropPortalPaymentResponse;
import za.co.handyflow.platform.shared.ApiResponse;

import java.util.List;
import java.util.UUID;

/** Tenant-portal-facing reads — a tenant checking their own lease, payment history, and unit inspections. Direct mirror of WhsePortalDataController. */
@RestController
@RequestMapping("/api/v1/property/portal")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('PORTAL_USER')")
@Tag(name = "Property Tenant Portal", description = "Tenant-facing data access")
public class PropPortalDataController {

    private final PropPortalDataService portalDataService;

    @GetMapping("/leases")
    @Operation(summary = "List every lease this portal user has access to")
    public ResponseEntity<ApiResponse<List<PropPortalLeaseSummaryResponse>>> getMyLeases() {
        return ResponseEntity.ok(ApiResponse.success(portalDataService.getMyLeases(getPortalUserId())));
    }

    @GetMapping("/leases/{leaseId}")
    @Operation(summary = "Lease details for a lease this portal user has access to")
    public ResponseEntity<ApiResponse<PropPortalLeaseSummaryResponse>> getMyLease(@PathVariable UUID leaseId) {
        return ResponseEntity.ok(ApiResponse.success(portalDataService.getMyLease(getPortalUserId(), leaseId)));
    }

    @GetMapping("/leases/{leaseId}/payments")
    @Operation(summary = "Payment history for a lease this portal user has access to")
    public ResponseEntity<ApiResponse<List<PropPortalPaymentResponse>>> getMyPayments(@PathVariable UUID leaseId) {
        return ResponseEntity.ok(ApiResponse.success(portalDataService.getMyPayments(getPortalUserId(), leaseId)));
    }

    @GetMapping("/leases/{leaseId}/inspections")
    @Operation(summary = "Inspection reports for the unit under a lease this portal user has access to")
    public ResponseEntity<ApiResponse<List<PropPortalInspectionResponse>>> getMyInspections(@PathVariable UUID leaseId) {
        return ResponseEntity.ok(ApiResponse.success(portalDataService.getMyInspections(getPortalUserId(), leaseId)));
    }

    private UUID getPortalUserId() {
        return UUID.fromString(SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString());
    }
}
