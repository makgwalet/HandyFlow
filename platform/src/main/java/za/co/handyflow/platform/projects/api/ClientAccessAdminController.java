package za.co.handyflow.platform.projects.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.projects.application.internal.clientportal.ClientPortalAdminService;
import za.co.handyflow.platform.projects.application.internal.clientportal.ClientPortalInboxService;
import za.co.handyflow.platform.projects.dto.ClientPortalDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/**
 * Staff: who can sign in to the Projects client portal (PM_ADMIN), and new-project requests from clients (PM_WRITE,
 * the same right as creating a project). Requests and files on an existing project are handled in that project
 * ({@link ClientInboxController}), under the project's own access rules.
 */
@RestController
@RequestMapping("/api/v1/project-client/admin")
@RequiredArgsConstructor
@Tag(name = "Projects Client Portal - Staff", description = "Client logins and new-project requests")
public class ClientAccessAdminController {

    private final ClientPortalAdminService admin;
    private final ClientPortalInboxService inbox;

    @GetMapping("/clients")
    @PreAuthorize("hasAuthority('PM_ADMIN')")
    @Operation(summary = "Clients named on projects, with their projects, to choose whom to invite")
    public ResponseEntity<ApiResponse<List<ClientOption>>> clients() { return ResponseEntity.ok(ApiResponse.success(admin.clients(tenant()))); }

    @GetMapping("/grants")
    @PreAuthorize("hasAuthority('PM_ADMIN')")
    @Operation(summary = "Every client login, pending, active and removed")
    public ResponseEntity<ApiResponse<List<GrantView>>> grants() { return ResponseEntity.ok(ApiResponse.success(admin.list(tenant()))); }

    @PostMapping("/grants")
    @PreAuthorize("hasAuthority('PM_ADMIN')")
    @Operation(summary = "Invite a client contact: all of the client's projects, or chosen ones. The link is emailed and lasts 14 days.")
    public ResponseEntity<ApiResponse<GrantView>> invite(@Valid @RequestBody InviteRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Invite sent",
                admin.invite(tenant(), req, TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName())));
    }

    @PostMapping("/grants/{id}/resend")
    @PreAuthorize("hasAuthority('PM_ADMIN')")
    @Operation(summary = "Send a pending invite again with a new link (the old link stops working)")
    public ResponseEntity<ApiResponse<GrantView>> resend(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Invite sent again", admin.resend(tenant(), id)));
    }

    @PutMapping("/grants/{id}/projects")
    @PreAuthorize("hasAuthority('PM_ADMIN')")
    @Operation(summary = "Change which projects a login sees: all of the client's, or chosen ones")
    public ResponseEntity<ApiResponse<GrantView>> scope(@PathVariable UUID id, @Valid @RequestBody ScopeRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Access updated", admin.changeScope(tenant(), id, req)));
    }

    @PostMapping("/grants/{id}/revoke")
    @PreAuthorize("hasAuthority('PM_ADMIN')")
    @Operation(summary = "Remove access at once (the person's other portal logins are untouched)")
    public ResponseEntity<ApiResponse<GrantView>> revoke(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Access removed", admin.revoke(tenant(), id, TenantContext.getCurrentUserId())));
    }

    @GetMapping("/new-project-requests")
    @PreAuthorize("hasAuthority('PM_WRITE')")
    @Operation(summary = "Clients asking for a new project")
    public ResponseEntity<ApiResponse<List<RequestView>>> newProjectRequests() {
        return ResponseEntity.ok(ApiResponse.success(inbox.newProjectRequests(tenant())));
    }

    @PostMapping("/new-project-requests/{id}/decide")
    @PreAuthorize("hasAuthority('PM_WRITE')")
    @Operation(summary = "ACCEPT (creates the project in planning; projectType and budgetTotal required), ANSWER or DECLINE, with a reply to the client")
    public ResponseEntity<ApiResponse<RequestView>> decideNewProject(@PathVariable UUID id, @Valid @RequestBody DecideRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Request updated",
                inbox.decideNewProject(tenant(), id, req, TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName())));
    }

    private static TenantId tenant() { return TenantContext.getTenantIdAsObject(); }
}
