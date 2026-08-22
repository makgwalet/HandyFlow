package za.co.handyflow.platform.approvals.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.approvals.application.internal.ApprovalEngineService;
import za.co.handyflow.platform.approvals.dto.ApprovalRuleRequest;
import za.co.handyflow.platform.approvals.dto.ApprovalRuleResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/**
 * FIX: backlog 1.1 Q3 — tenant-configurable approval rules. Injects the
 * concrete ApprovalEngineService (not ApprovalFacade) — rule management
 * is this module's own configuration surface, same-module, not part of
 * the cross-module contract other modules call (see ApprovalFacade's
 * own note on why).
 * <p>
 * New permission: APPROVALS_MANAGE — module-agnostic on purpose, so one
 * tenant admin can manage AP's rules and Creative's rules (and any
 * future module's) from one place, rather than a permission duplicated
 * per module for the same underlying capability.
 */
@RestController
@RequestMapping("/api/v1/approvals/rules")
@RequiredArgsConstructor
@Tag(name = "Approval Rules", description = "Tenant-configurable approval routing")
public class ApprovalRuleController {

    private final ApprovalEngineService approvalEngineService;

    @GetMapping
    @PreAuthorize("hasAuthority('APPROVALS_MANAGE')")
    @Operation(summary = "List this tenant's approval rules, optionally filtered by module/entityType")
    public ResponseEntity<ApiResponse<List<ApprovalRuleResponse>>> list(
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String entityType) {
        return ResponseEntity.ok(ApiResponse.success(
                approvalEngineService.listRules(TenantContext.getTenantIdAsObject(), module, entityType)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('APPROVALS_MANAGE')")
    @Operation(summary = "Create a tenant approval rule")
    public ResponseEntity<ApiResponse<ApprovalRuleResponse>> create(@Valid @RequestBody ApprovalRuleRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Rule created",
                approvalEngineService.createRule(TenantContext.getTenantIdAsObject(), req)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('APPROVALS_MANAGE')")
    @Operation(summary = "Update a tenant approval rule — platform default rules cannot be edited directly")
    public ResponseEntity<ApiResponse<ApprovalRuleResponse>> update(
            @PathVariable UUID id, @Valid @RequestBody ApprovalRuleRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Rule updated",
                approvalEngineService.updateRule(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('APPROVALS_MANAGE')")
    @Operation(summary = "Deactivate a tenant approval rule")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable UUID id) {
        approvalEngineService.deactivateRule(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success("Rule deactivated", null));
    }
}