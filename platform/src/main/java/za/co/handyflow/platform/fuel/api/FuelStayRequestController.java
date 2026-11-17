package za.co.handyflow.platform.fuel.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.fuel.application.internal.FuelStayRequestService;
import za.co.handyflow.platform.fuel.dto.StayRequestDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/** Staff side of the customers' overnight stay requests. Under /truck-stop, so the Truck Stop division is required on the server. */
@RestController
@RequestMapping("/api/v1/fuel/truck-stop/stay-requests")
@RequiredArgsConstructor
@Tag(name = "Fuel Truck Stop: Stay Requests", description = "Approve or decline overnight stays customers asked for in the portal")
public class FuelStayRequestController {

    private final FuelStayRequestService service;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('FUEL_READ','FUEL_MANAGE','FUEL_TRUCKSTOP_MANAGE')")
    public ResponseEntity<ApiResponse<List<StaffStayRequestView>>> list(@RequestParam(required = false) String status, @RequestParam(defaultValue = "50") int limit) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(service.list(TenantContext.getTenantIdAsObject(), status, limit)));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyAuthority('FUEL_MANAGE','FUEL_TRUCKSTOP_MANAGE')")
    @Operation(summary = "Approve: books the stay through the normal stay rules, charged to the customer's account. A refusal leaves the request pending")
    public ResponseEntity<ApiResponse<StaffStayRequestView>> approve(@PathVariable UUID id, @Valid @RequestBody(required = false) ApproveBody body) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Stay booked", service.approve(TenantContext.getTenantIdAsObject(), id, body == null ? null : body.note(), TenantContext.getCurrentUserId())));
    }

    @PostMapping("/{id}/decline")
    @PreAuthorize("hasAnyAuthority('FUEL_MANAGE','FUEL_TRUCKSTOP_MANAGE')")
    public ResponseEntity<ApiResponse<StaffStayRequestView>> decline(@PathVariable UUID id, @Valid @RequestBody DeclineBody body) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Request declined", service.decline(TenantContext.getTenantIdAsObject(), id, body.reason(), TenantContext.getCurrentUserId())));
    }
}
