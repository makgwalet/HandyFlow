package za.co.handyflow.platform.agriculture.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.agriculture.application.internal.AgEnterpriseService;
import za.co.handyflow.platform.agriculture.dto.CreateEnterpriseRequest;
import za.co.handyflow.platform.agriculture.dto.EnterpriseResponse;
import za.co.handyflow.platform.agriculture.dto.UpdateEnterpriseRequest;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/agriculture")
@RequiredArgsConstructor
@Tag(name = "Agriculture - Enterprises", description = "Business lines within a farm — e.g. Beef Cattle, Dairy Herd")
public class AgEnterpriseController {

    private final AgEnterpriseService enterpriseService;
    private final FeatureGuard featureGuard;

    @GetMapping("/farms/{farmId}/enterprises")
    @PreAuthorize("hasAuthority('AGRICULTURE_READ')")
    public ResponseEntity<ApiResponse<Page<EnterpriseResponse>>> getEnterprisesForFarm(
            @PathVariable UUID farmId, @PageableDefault(size = 50) Pageable pageable) {
        featureGuard.requireModule("agriculture");
        return ResponseEntity.ok(ApiResponse.success(
                enterpriseService.getEnterprisesForFarm(TenantContext.getTenantIdAsObject(), farmId, pageable)));
    }

    @PostMapping("/farms/{farmId}/enterprises")
    @PreAuthorize("hasAuthority('AGRICULTURE_MANAGE')")
    @Operation(summary = "Add an enterprise (business line) to a farm")
    public ResponseEntity<ApiResponse<EnterpriseResponse>> createEnterprise(
            @PathVariable UUID farmId, @Valid @RequestBody CreateEnterpriseRequest request) {
        featureGuard.requireModule("agriculture");
        if (!farmId.equals(request.farmId())) {
            return ResponseEntity.badRequest().body(ApiResponse.error("farmId in path and body must match"));
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Enterprise created",
                enterpriseService.createEnterprise(TenantContext.getTenantIdAsObject(), request)));
    }

    @GetMapping("/enterprises/{id}")
    @PreAuthorize("hasAuthority('AGRICULTURE_READ')")
    public ResponseEntity<ApiResponse<EnterpriseResponse>> getEnterprise(@PathVariable UUID id) {
        featureGuard.requireModule("agriculture");
        return ResponseEntity.ok(ApiResponse.success(
                enterpriseService.getEnterprise(TenantContext.getTenantIdAsObject(), id)));
    }

    @PutMapping("/enterprises/{id}")
    @PreAuthorize("hasAuthority('AGRICULTURE_MANAGE')")
    public ResponseEntity<ApiResponse<EnterpriseResponse>> updateEnterprise(
            @PathVariable UUID id, @Valid @RequestBody UpdateEnterpriseRequest request) {
        featureGuard.requireModule("agriculture");
        return ResponseEntity.ok(ApiResponse.success("Enterprise updated",
                enterpriseService.updateEnterprise(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @PatchMapping("/enterprises/{id}/deactivate")
    @PreAuthorize("hasAuthority('AGRICULTURE_MANAGE')")
    public ResponseEntity<ApiResponse<EnterpriseResponse>> deactivateEnterprise(@PathVariable UUID id) {
        featureGuard.requireModule("agriculture");
        return ResponseEntity.ok(ApiResponse.success("Enterprise deactivated",
                enterpriseService.deactivateEnterprise(TenantContext.getTenantIdAsObject(), id)));
    }

    @PatchMapping("/enterprises/{id}/reactivate")
    @PreAuthorize("hasAuthority('AGRICULTURE_MANAGE')")
    public ResponseEntity<ApiResponse<EnterpriseResponse>> reactivateEnterprise(@PathVariable UUID id) {
        featureGuard.requireModule("agriculture");
        return ResponseEntity.ok(ApiResponse.success("Enterprise reactivated",
                enterpriseService.reactivateEnterprise(TenantContext.getTenantIdAsObject(), id)));
    }

    @DeleteMapping("/enterprises/{id}")
    @PreAuthorize("hasAuthority('AGRICULTURE_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteEnterprise(@PathVariable UUID id) {
        featureGuard.requireModule("agriculture");
        enterpriseService.deleteEnterprise(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success("Enterprise deleted", null));
    }
}
