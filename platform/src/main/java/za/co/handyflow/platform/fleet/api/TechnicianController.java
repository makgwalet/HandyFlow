package za.co.handyflow.platform.fleet.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.fleet.application.internal.TechnicianService;
import za.co.handyflow.platform.fleet.dto.SaveTechnicianRequest;
import za.co.handyflow.platform.fleet.dto.TechnicianResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/** Workshop technicians. There is deliberately no delete: deactivate (active=false) so work-order history keeps its reference. */
@RestController
@RequestMapping("/api/v1/fleet/technicians")
@RequiredArgsConstructor
@Tag(name = "Fleet - Technicians", description = "Workshop technicians and their hourly rates")
public class TechnicianController {

    private final TechnicianService technicianService;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAuthority('FLEET_READ')")
    @Operation(summary = "List technicians (activeOnly=true to hide deactivated ones)")
    public ResponseEntity<ApiResponse<List<TechnicianResponse>>> list(
            @RequestParam(defaultValue = "false") boolean activeOnly) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success(
                technicianService.list(TenantContext.getTenantIdAsObject(), activeOnly)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('FLEET_MANAGE')")
    public ResponseEntity<ApiResponse<TechnicianResponse>> create(@Valid @RequestBody SaveTechnicianRequest request) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Technician added",
                technicianService.create(TenantContext.getTenantIdAsObject(), request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('FLEET_MANAGE')")
    public ResponseEntity<ApiResponse<TechnicianResponse>> update(
            @PathVariable UUID id, @Valid @RequestBody SaveTechnicianRequest request) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success("Technician updated",
                technicianService.update(TenantContext.getTenantIdAsObject(), id, request)));
    }
}