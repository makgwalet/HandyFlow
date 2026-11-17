package za.co.handyflow.platform.fuel.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.fuel.application.internal.FuelAccessService;
import za.co.handyflow.platform.fuel.dto.FuelAccessDtos.AccessSummary;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

@RestController
@RequestMapping("/api/v1/fuel/me")
@RequiredArgsConstructor
@Tag(name = "Fuel Access", description = "What the signed-in person can reach in Fuel")
public class FuelAccessController {

    private final FuelAccessService access;
    private final FeatureGuard featureGuard;

    /** Open to anyone with a Fuel role, including a driver who has no read permission, because it only describes their own access. */
    @GetMapping("/access")
    @PreAuthorize("hasAnyAuthority('FUEL_READ','FUEL_DRIVER','FUEL_FORECOURT','FUEL_MANAGE','FUEL_FORECOURT_MANAGE','FUEL_TRANSPORT_MANAGE','FUEL_TRUCKSTOP_MANAGE','FUEL_OPERATIONS_MANAGE','FUEL_STOCK_MANAGE','FUEL_DISTRIBUTION_MANAGE')")
    public ResponseEntity<ApiResponse<AccessSummary>> mine() {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(access.summary(TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId())));
    }
}
