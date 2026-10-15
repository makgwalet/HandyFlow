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

    public record InviteAuditorRequest(@NotBlank String email, @NotBlank String businessName,
                                       java.time.LocalDate expiresOn, String scopeNote, UUID engagementId) {}
    public record ChangeExpiryRequest(java.time.LocalDate expiresOn) {}

    @PostMapping("/invite")
    @PreAuthorize("hasAuthority('EXTERNAL_AUDIT_INVITE')")
    @Operation(summary = "Invite an external auditor to review this business's records")
    public ResponseEntity<ApiResponse<AuditorAccessGrantResponse>> invite(@RequestBody InviteAuditorRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Invite sent",
                auditorService.inviteAuditor(TenantContext.getTenantIdAsObject(), req.email(),
                        req.businessName(), TenantContext.getCurrentUserId(), req.expiresOn(), req.scopeNote(), req.engagementId())));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('EXTERNAL_AUDIT_INVITE')")
    @Operation(summary = "List every auditor grant for this business")
    public ResponseEntity<ApiResponse<List<AuditorAccessGrantResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.success(
                auditorService.listAuditorGrants(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping("/{id}/expiry")
    @PreAuthorize("hasAuthority('EXTERNAL_AUDIT_INVITE')")
    @Operation(summary = "Change the date an auditor's access ends")
    public ResponseEntity<ApiResponse<AuditorAccessGrantResponse>> changeExpiry(@PathVariable UUID id,
                                                                                 @RequestBody ChangeExpiryRequest req) {
        return ResponseEntity.ok(ApiResponse.success(
                auditorService.changeExpiry(TenantContext.getTenantIdAsObject(), id, req.expiresOn())));
    }

    @PostMapping("/{id}/revoke")
    @PreAuthorize("hasAuthority('EXTERNAL_AUDIT_INVITE')")
    @Operation(summary = "Revoke an auditor's access")
    public ResponseEntity<ApiResponse<AuditorAccessGrantResponse>> revoke(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                auditorService.revokeAuditorAccess(TenantContext.getTenantIdAsObject(), id,
                        TenantContext.getCurrentUserId())));
    }
}