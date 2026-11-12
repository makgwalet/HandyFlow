package za.co.handyflow.platform.fuel.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.fuel.application.internal.FuelLoadReturnService;
import za.co.handyflow.platform.fuel.dto.LoadReturnDtos.Outstanding;
import za.co.handyflow.platform.fuel.dto.LoadReturnDtos.ReturnRequest;
import za.co.handyflow.platform.fuel.dto.LoadReturnDtos.ReturnView;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/** Phase 7a: confirm which tank returned fuel was put into. Books no stock movement. */
@RestController
@RequestMapping("/api/v1/fuel")
@RequiredArgsConstructor
public class FuelLoadReturnController {

    private final FuelLoadReturnService service;
    private final FeatureGuard featureGuard;

    @GetMapping("/load-returns/outstanding")
    @PreAuthorize("hasAuthority('FUEL_READ')")
    public ResponseEntity<ApiResponse<List<Outstanding>>> outstanding() {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(service.outstanding(TenantContext.getTenantIdAsObject())));
    }

    @GetMapping("/deliveries/{id}/returns")
    @PreAuthorize("hasAuthority('FUEL_READ')")
    public ResponseEntity<ApiResponse<List<ReturnView>>> list(@PathVariable UUID id) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(service.list(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/deliveries/{id}/returns")
    @PreAuthorize("hasAnyAuthority('FUEL_MANAGE','FUEL_TRANSPORT_MANAGE')")
    public ResponseEntity<ApiResponse<ReturnView>> record(@PathVariable UUID id, @Valid @RequestBody ReturnRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Return recorded", service.record(TenantContext.getTenantIdAsObject(), id, req, TenantContext.getCurrentUserId())));
    }
}
