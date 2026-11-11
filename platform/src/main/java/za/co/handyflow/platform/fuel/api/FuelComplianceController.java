package za.co.handyflow.platform.fuel.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.fuel.application.internal.FuelComplianceService;
import za.co.handyflow.platform.fuel.dto.ComplianceRequest;
import za.co.handyflow.platform.fuel.dto.ComplianceResponse;
import za.co.handyflow.platform.fuel.dto.RenewRequest;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/** Stage 4B: compliance register. */
@RestController
@RequestMapping("/api/v1/fuel/compliance")
@RequiredArgsConstructor
@Tag(name = "Fuel Compliance", description = "Licences, calibrations, inspections and certificates")
public class FuelComplianceController {

    private final FuelComplianceService compliance;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAuthority('FUEL_READ')")
    public ResponseEntity<ApiResponse<List<ComplianceResponse>>> list() {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(compliance.list(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('FUEL_MANAGE')")
    public ResponseEntity<ApiResponse<ComplianceResponse>> create(@Valid @RequestBody ComplianceRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Added", compliance.create(TenantContext.getTenantIdAsObject(), req)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('FUEL_MANAGE')")
    public ResponseEntity<ApiResponse<ComplianceResponse>> update(@PathVariable UUID id, @Valid @RequestBody ComplianceRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Updated", compliance.update(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @PostMapping("/{id}/renew")
    @PreAuthorize("hasAuthority('FUEL_MANAGE')")
    public ResponseEntity<ApiResponse<ComplianceResponse>> renew(@PathVariable UUID id, @Valid @RequestBody RenewRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Renewed", compliance.renew(TenantContext.getTenantIdAsObject(), id, req, TenantContext.getCurrentUserId())));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('FUEL_MANAGE')")
    public ResponseEntity<ApiResponse<Void>> remove(@PathVariable UUID id) {
        featureGuard.requireModule("fuel");
        compliance.remove(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success("Removed", null));
    }
}
