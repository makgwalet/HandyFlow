package za.co.handyflow.platform.earthmoving.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.earthmoving.application.internal.EarthContractRateService;
import za.co.handyflow.platform.earthmoving.dto.ContractRateRequest;
import za.co.handyflow.platform.earthmoving.dto.ContractRateResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.UserContext;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/earthmoving/deployments/{id}/contract-rates")
@RequiredArgsConstructor
@Tag(name = "Earthmoving contract rates")
public class EarthContractRateController {

    private final EarthContractRateService rates;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAuthority('EARTHMOVING_READ')")
    @Operation(summary = "The rates held on a deployment's hire contract")
    public ResponseEntity<ApiResponse<ContractRateResponse>> get(@PathVariable UUID id) {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.ok(ApiResponse.success("Contract rates", rates.forDeployment(TenantContext.getTenantIdAsObject(), id)));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('EARTHMOVING_ADMIN')")
    @Operation(summary = "Set the rate for one billing basis on the hire contract")
    public ResponseEntity<ApiResponse<ContractRateResponse>> set(@PathVariable UUID id, @Valid @RequestBody ContractRateRequest request) {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.ok(ApiResponse.success("Rate saved",
                rates.set(TenantContext.getTenantIdAsObject(), id, request, UserContext.getCurrentUserId())));
    }

    @DeleteMapping("/{basis}")
    @PreAuthorize("hasAuthority('EARTHMOVING_ADMIN')")
    @Operation(summary = "Remove the rate for one billing basis from the hire contract")
    public ResponseEntity<ApiResponse<ContractRateResponse>> remove(@PathVariable UUID id, @PathVariable String basis) {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.ok(ApiResponse.success("Rate removed",
                rates.remove(TenantContext.getTenantIdAsObject(), id, basis, UserContext.getCurrentUserId())));
    }
}
