// security/api/BranchController.java
package za.co.handyflow.platform.security.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.security.application.internal.BranchService;
import za.co.handyflow.platform.security.domain.model.Branch;
import za.co.handyflow.platform.security.dto.CreateBranchRequest;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

@Tag(name = "Security - Branches (Phase 4)")
@RestController
@RequestMapping("/api/v1/security/branches")
@RequiredArgsConstructor
public class BranchController {

    private final BranchService branchService;

    @GetMapping
    @PreAuthorize("hasAuthority('SECURITY_READ')")
    @Operation(summary = "List all active branches")
    public ResponseEntity<ApiResponse<List<Branch>>> listBranches() {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(branchService.listBranches(tenantId)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SECURITY_READ')")
    @Operation(summary = "Get a single branch")
    public ResponseEntity<ApiResponse<Branch>> getBranch(@PathVariable UUID id) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(branchService.getBranch(tenantId, id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(summary = "Create a branch")
    public ResponseEntity<ApiResponse<Branch>> createBranch(
            @Valid @RequestBody CreateBranchRequest req) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(branchService.createBranch(tenantId, req)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(summary = "Update a branch's name, region, or description")
    public ResponseEntity<ApiResponse<Branch>> updateBranch(
            @PathVariable UUID id, @Valid @RequestBody CreateBranchRequest req) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(branchService.updateBranch(tenantId, id, req)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(summary = "Deactivate a branch")
    public ResponseEntity<ApiResponse<Void>> deactivateBranch(@PathVariable UUID id) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        branchService.deactivateBranch(tenantId, id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}