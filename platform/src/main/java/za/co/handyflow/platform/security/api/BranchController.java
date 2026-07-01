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

/**
 * BranchController — Phase 4 multi-branch management.
 *
 * Branches are sub-divisions of a tenant (Gauteng Region, Cape Town Office,
 * Industrial Division, VIP/CP Division, etc.). Sites and guards are assigned
 * to a branch via their branch_id column. Regional managers are scoped to
 * their branch via security_branch_assignments.
 *
 * ENFORCEMENT NOTE:
 * Branch-based query filtering is not yet enforced server-side — the current
 * controllers still return all-tenant data. The branch_id on Site/Guard/Guard
 * records is set here; the query-level filtering will be enforced as a
 * follow-on by injecting the acting user's branch scope (from their
 * security_branch_assignments row with role=MANAGER) into each repository
 * query alongside the TenantId.
 */
@Tag(name = "Security - Branches (Phase 4)")
@RestController
@RequestMapping("/api/v1/security/branches")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('USER_UPDATE')")
public class BranchController {

    private final BranchService branchService;

    @GetMapping
    @Operation(summary = "List all active branches")
    public ResponseEntity<ApiResponse<List<Branch>>> listBranches() {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(branchService.listBranches(tenantId)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a single branch")
    public ResponseEntity<ApiResponse<Branch>> getBranch(@PathVariable UUID id) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(branchService.getBranch(tenantId, id)));
    }

    @PostMapping
    @Operation(summary = "Create a branch")
    public ResponseEntity<ApiResponse<Branch>> createBranch(
            @Valid @RequestBody CreateBranchRequest req) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(branchService.createBranch(tenantId, req)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a branch's name, region, or description")
    public ResponseEntity<ApiResponse<Branch>> updateBranch(
            @PathVariable UUID id, @Valid @RequestBody CreateBranchRequest req) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(branchService.updateBranch(tenantId, id, req)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Deactivate a branch")
    public ResponseEntity<ApiResponse<Void>> deactivateBranch(@PathVariable UUID id) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        branchService.deactivateBranch(tenantId, id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
