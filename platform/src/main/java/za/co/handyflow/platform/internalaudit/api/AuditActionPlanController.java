package za.co.handyflow.platform.internalaudit.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.internalaudit.application.internal.ActionPlanService;
import za.co.handyflow.platform.internalaudit.dto.ActionPlanDtos.CreateRequest;
import za.co.handyflow.platform.internalaudit.dto.ActionPlanDtos.NoteRequest;
import za.co.handyflow.platform.internalaudit.dto.ActionPlanDtos.Response;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/internal-audit")
@RequiredArgsConstructor
@Tag(name = "Internal Audit - Action Plans", description = "What management will do about a finding, and whether an auditor has checked it")
public class AuditActionPlanController {

    private final ActionPlanService service;
    private final FeatureGuard featureGuard;

    @GetMapping("/findings/{findingId}/action-plans")
    @PreAuthorize("hasAuthority('AUDIT_READ')")
    public ResponseEntity<ApiResponse<List<Response>>> forFinding(@PathVariable UUID findingId) {
        featureGuard.requireModule("internal-audit");
        return ResponseEntity.ok(ApiResponse.success(service.forFinding(TenantContext.getTenantIdAsObject(), findingId)));
    }

    @PostMapping("/findings/{findingId}/action-plans")
    @PreAuthorize("hasAuthority('AUDIT_MANAGE')")
    public ResponseEntity<ApiResponse<Response>> create(@PathVariable UUID findingId, @Valid @RequestBody CreateRequest req) {
        featureGuard.requireModule("internal-audit");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                service.create(TenantContext.getTenantIdAsObject(), findingId, req, TenantContext.getCurrentUserId())));
    }

    @GetMapping("/action-plans/overdue")
    @PreAuthorize("hasAuthority('AUDIT_READ')")
    @Operation(summary = "Open or awaiting-validation actions past their due date, oldest first")
    public ResponseEntity<ApiResponse<List<Response>>> overdue() {
        featureGuard.requireModule("internal-audit");
        return ResponseEntity.ok(ApiResponse.success(service.overdue(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping("/action-plans/{id}/complete")
    @PreAuthorize("hasAnyAuthority('AUDIT_READ','AUDIT_MANAGE')")
    @Operation(summary = "Mark an action complete, with a note of what was done. The action's owner or an audit manager.")
    public ResponseEntity<ApiResponse<Response>> complete(@PathVariable UUID id, @RequestBody NoteRequest req, Authentication auth) {
        featureGuard.requireModule("internal-audit");
        boolean manager = auth.getAuthorities().stream().anyMatch(a -> "AUDIT_MANAGE".equals(a.getAuthority()) || "AUDIT_ADMIN".equals(a.getAuthority()));
        return ResponseEntity.ok(ApiResponse.success(service.complete(TenantContext.getTenantIdAsObject(), id, req.note(),
                TenantContext.getCurrentUserId(), manager)));
    }

    @PostMapping("/action-plans/{id}/validate")
    @PreAuthorize("hasAuthority('AUDIT_MANAGE')")
    @Operation(summary = "Confirm the work holds up. Not the person who completed it, and not the action's owner.")
    public ResponseEntity<ApiResponse<Response>> validate(@PathVariable UUID id, @RequestBody NoteRequest req) {
        featureGuard.requireModule("internal-audit");
        return ResponseEntity.ok(ApiResponse.success(service.validate(TenantContext.getTenantIdAsObject(), id, req.note(), TenantContext.getCurrentUserId())));
    }

    @PostMapping("/action-plans/{id}/return")
    @PreAuthorize("hasAuthority('AUDIT_MANAGE')")
    @Operation(summary = "Send a completed action back for more work, with the reason")
    public ResponseEntity<ApiResponse<Response>> returnForRework(@PathVariable UUID id, @RequestBody NoteRequest req) {
        featureGuard.requireModule("internal-audit");
        return ResponseEntity.ok(ApiResponse.success(service.returnForRework(TenantContext.getTenantIdAsObject(), id, req.note(), TenantContext.getCurrentUserId())));
    }

    @PostMapping("/action-plans/{id}/cancel")
    @PreAuthorize("hasAuthority('AUDIT_MANAGE')")
    public ResponseEntity<ApiResponse<Response>> cancel(@PathVariable UUID id, @RequestBody NoteRequest req) {
        featureGuard.requireModule("internal-audit");
        return ResponseEntity.ok(ApiResponse.success(service.cancel(TenantContext.getTenantIdAsObject(), id, req.note())));
    }
}
