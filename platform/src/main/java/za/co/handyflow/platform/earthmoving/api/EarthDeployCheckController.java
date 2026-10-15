package za.co.handyflow.platform.earthmoving.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.earthmoving.application.internal.EarthDeployCheckService;
import za.co.handyflow.platform.earthmoving.dto.DeployWarningsResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/earthmoving/assets/{id}/deploy-warnings")
@RequiredArgsConstructor
@Tag(name = "Earthmoving deployment checks")
public class EarthDeployCheckController {

    private final EarthDeployCheckService checks;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAuthority('EARTHMOVING_READ')")
    @Operation(summary = "Warnings to show before deploying a machine (overdue service, no recent inspection). They do not block.")
    public ResponseEntity<ApiResponse<DeployWarningsResponse>> warnings(@PathVariable UUID id) {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.ok(ApiResponse.success("Deployment warnings", checks.warnings(TenantContext.getTenantIdAsObject(), id)));
    }
}
