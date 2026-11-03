package za.co.handyflow.platform.projects.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.projects.application.internal.ProjectAccess;
import za.co.handyflow.platform.projects.application.internal.ProjectAccessGuard;
import za.co.handyflow.platform.projects.application.internal.ProjectMemberService;
import za.co.handyflow.platform.projects.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/**
 * Who is on a project, and in what role.
 *
 * Reading needs PM_READ. Changing needs PM_WRITE plus the project's MANAGER role
 * (or PM_ADMIN) — enforced here explicitly as well as by ProjectAccessInterceptor,
 * so the rule holds even if the interceptor is ever bypassed or misconfigured.
 * When restriction is switched off, the manager check is a no-op and PM_WRITE alone suffices.
 */
@Validated
@RestController
@RequestMapping("/api/v1/projects/{projectId}/members")
@RequiredArgsConstructor
@Tag(name = "Project Members", description = "Project team membership and roles")
public class ProjectMemberController {

    private final ProjectMemberService memberService;
    private final ProjectAccessGuard   accessGuard;

    @GetMapping
    @PreAuthorize("hasAuthority('PM_READ')")
    @Operation(summary = "List the project team")
    public ResponseEntity<ApiResponse<List<MemberResponse>>> list(@PathVariable UUID projectId) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                memberService.list(tenant(), projectId).stream().map(MemberResponse::of).toList()));
    }

    @GetMapping("/me")
    @PreAuthorize("hasAuthority('PM_READ')")
    @Operation(summary = "What the current user can do on this project — drives UI visibility")
    public ResponseEntity<ApiResponse<MyAccessResponse>> myAccess(@PathVariable UUID projectId) {
        TenantId tenant = tenant();
        ProjectAccess a = accessGuard.accessFor(tenant.getValue(), projectId);
        String role = null;
        if (accessGuard.isRestricted() && a.view()) {
            role = memberService.find(tenant, projectId, TenantContext.getCurrentUserId())
                    .map(m -> m.getRole().name()).orElse(null);
        }
        return ResponseEntity.ok(ApiResponse.success("Success",
                new MyAccessResponse(accessGuard.isRestricted(), role,
                        a.view(), a.write(), a.cost(), a.manageMembers())));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PM_WRITE')")
    @Operation(summary = "Add a member — 409 if already on the project")
    public ResponseEntity<ApiResponse<MemberResponse>> add(
            @PathVariable UUID projectId, @Valid @RequestBody AddMemberRequest req) {
        TenantId tenant = tenant();
        accessGuard.requireManageMembers(tenant.getValue(), projectId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Member added",
                MemberResponse.of(memberService.add(tenant, projectId, req.userId(), req.userName(),
                        req.role(), TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName()))));
    }

    @PutMapping("/{userId}")
    @PreAuthorize("hasAuthority('PM_WRITE')")
    @Operation(summary = "Change a member's role — the last MANAGER cannot be demoted")
    public ResponseEntity<ApiResponse<MemberResponse>> changeRole(
            @PathVariable UUID projectId, @PathVariable UUID userId,
            @Valid @RequestBody UpdateMemberRoleRequest req) {
        TenantId tenant = tenant();
        accessGuard.requireManageMembers(tenant.getValue(), projectId);
        return ResponseEntity.ok(ApiResponse.success("Role updated",
                MemberResponse.of(memberService.changeRole(tenant, projectId, userId, req.role(),
                        TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName()))));
    }

    @DeleteMapping("/{userId}")
    @PreAuthorize("hasAuthority('PM_WRITE')")
    @Operation(summary = "Remove a member — the last MANAGER cannot be removed")
    public ResponseEntity<ApiResponse<Void>> remove(
            @PathVariable UUID projectId, @PathVariable UUID userId) {
        TenantId tenant = tenant();
        accessGuard.requireManageMembers(tenant.getValue(), projectId);
        memberService.remove(tenant, projectId, userId,
                TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName());
        return ResponseEntity.ok(ApiResponse.success("Member removed", null));
    }

    @GetMapping("/history")
    @PreAuthorize("hasAuthority('PM_READ')")
    @Operation(summary = "Audit trail of membership changes — newest first")
    public ResponseEntity<ApiResponse<List<MemberEventResponse>>> history(@PathVariable UUID projectId) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                memberService.history(tenant(), projectId).stream().map(MemberEventResponse::of).toList()));
    }

    private static TenantId tenant() { return TenantContext.getTenantIdAsObject(); }
}