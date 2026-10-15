package za.co.handyflow.platform.earthmoving.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.earthmoving.application.internal.EarthDeployCheckService;
import za.co.handyflow.platform.earthmoving.dto.DeployWarningsResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.time.LocalDate;
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
    public ResponseEntity<ApiResponse<DeployWarningsResponse>> warnings(
            @PathVariable UUID id,
            @RequestParam(required = false) UUID customerId, @RequestParam(required = false) UUID siteId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID contractId) {
        featureGuard.requireModule("earthmoving");
        EarthDeployCheckService.HireContext hire = customerId == null && siteId == null ? null
                : new EarthDeployCheckService.HireContext(customerId, siteId, from, to, contractId);
        return ResponseEntity.ok(ApiResponse.success("Deployment warnings",
                checks.warnings(TenantContext.getTenantIdAsObject(), id, java.time.Instant.now(), hire)));
    }
}
