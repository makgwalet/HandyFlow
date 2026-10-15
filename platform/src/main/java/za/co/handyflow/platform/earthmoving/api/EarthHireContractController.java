package za.co.handyflow.platform.earthmoving.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.earthmoving.application.internal.EarthHireContractService;
import za.co.handyflow.platform.earthmoving.dto.DeploymentContractResponse;
import za.co.handyflow.platform.earthmoving.dto.ExtensionRequest;
import za.co.handyflow.platform.earthmoving.dto.LinkContractRequest;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.UserContext;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/earthmoving/deployments/{id}/contract")
@RequiredArgsConstructor
@Tag(name = "Earthmoving hire contracts")
public class EarthHireContractController {

    private final EarthHireContractService contracts;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAuthority('EARTHMOVING_READ')")
    @Operation(summary = "The contract of a hire deployment, its addenda and whether one is expected")
    public ResponseEntity<ApiResponse<DeploymentContractResponse>> get(@PathVariable UUID id) {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.ok(ApiResponse.success("Hire contract", contracts.forDeployment(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/draft")
    @PreAuthorize("hasAuthority('EARTHMOVING_DEPLOY')")
    @Operation(summary = "Make a draft hire agreement from the deployment and link it")
    public ResponseEntity<ApiResponse<DeploymentContractResponse>> draft(@PathVariable UUID id) {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.ok(ApiResponse.success("Draft contract made",
                contracts.createDraft(TenantContext.getTenantIdAsObject(), id, UserContext.getCurrentUserId())));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('EARTHMOVING_DEPLOY')")
    @Operation(summary = "Link an existing contract to the deployment")
    public ResponseEntity<ApiResponse<DeploymentContractResponse>> link(@PathVariable UUID id, @Valid @RequestBody LinkContractRequest req) {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.ok(ApiResponse.success("Contract linked",
                contracts.link(TenantContext.getTenantIdAsObject(), id, req.contractId(), UserContext.getCurrentUserId())));
    }

    @PostMapping("/extension")
    @PreAuthorize("hasAuthority('EARTHMOVING_DEPLOY')")
    @Operation(summary = "Record a hire extension as a draft addendum contract")
    public ResponseEntity<ApiResponse<DeploymentContractResponse>> extend(@PathVariable UUID id, @Valid @RequestBody ExtensionRequest req) {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.ok(ApiResponse.success("Addendum drafted",
                contracts.createAddendum(TenantContext.getTenantIdAsObject(), id, req.newEndDate(), req.reason(), UserContext.getCurrentUserId())));
    }
}
