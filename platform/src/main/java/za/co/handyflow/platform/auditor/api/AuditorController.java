package za.co.handyflow.platform.auditor.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.auditor.application.internal.AuditorService;
import za.co.handyflow.platform.auditor.dto.AuditorAccessGrantResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auditors")
@RequiredArgsConstructor
@Tag(name = "Auditor Access", description = "Invite/manage external auditor access — Stage 3")
public class AuditorController {

    private final AuditorService auditorService;

    public record InviteAuditorRequest(@NotBlank String email, @NotBlank String businessName) {}

    @PostMapping("/invite")
    @PreAuthorize("hasAuthority('SETTINGS_MANAGE')")
    @Operation(summary = "Invite an external auditor to review this business's records")
    public ResponseEntity<ApiResponse<AuditorAccessGrantResponse>> invite(@RequestBody InviteAuditorRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Invite sent",
                auditorService.inviteAuditor(TenantContext.getTenantIdAsObject(), req.email(),
                        req.businessName(), TenantContext.getCurrentUserId())));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SETTINGS_MANAGE')")
    @Operation(summary = "List every auditor grant for this business")
    public ResponseEntity<ApiResponse<List<AuditorAccessGrantResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.success(
                auditorService.listAuditorGrants(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping("/{id}/revoke")
    @PreAuthorize("hasAuthority('SETTINGS_MANAGE')")
    @Operation(summary = "Revoke an auditor's access")
    public ResponseEntity<ApiResponse<AuditorAccessGrantResponse>> revoke(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                auditorService.revokeAuditorAccess(TenantContext.getTenantIdAsObject(), id,
                        TenantContext.getCurrentUserId())));
    }
}