package za.co.handyflow.platform.internalaudit.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.internalaudit.application.internal.AuditHeatMapService;
import za.co.handyflow.platform.internalaudit.application.internal.RiskHeatMap.HeatMap;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

@RestController
@RequestMapping("/api/v1/internal-audit/heat-map")
@RequiredArgsConstructor
@Tag(name = "Internal Audit - Risk Heat Map", description = "The audit universe by inherent risk, control risk and final risk level")
public class AuditHeatMapController {
    private final AuditHeatMapService service;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAuthority('AUDIT_READ')")
    @Operation(summary = "Areas placed on an inherent-by-control grid, and counts of areas by final risk for each process area")
    public ResponseEntity<ApiResponse<HeatMap>> get() {
        featureGuard.requireModule("internal-audit");
        return ResponseEntity.ok(ApiResponse.success(service.heatMap(TenantContext.getTenantIdAsObject())));
    }
}
