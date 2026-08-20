package za.co.handyflow.platform.auditor.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.auditor.application.internal.AuditorPortalDataService;
import za.co.handyflow.platform.controls.dto.ControlExceptionResponse;
import za.co.handyflow.platform.evidence.dto.EvidenceResponse;
import za.co.handyflow.platform.shared.ApiResponse;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auditor/portal")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('PORTAL_USER')")
@Tag(name = "Auditor Client Portal", description = "External auditor's read-only view")
public class AuditorPortalDataController {

    private final AuditorPortalDataService dataService;

    @GetMapping("/tenants")
    @Operation(summary = "List every business this auditor has active access to")
    public ResponseEntity<ApiResponse<List<AuditorPortalDataService.AuditorTenantAccess>>> getMyTenants() {
        return ResponseEntity.ok(ApiResponse.success(dataService.getMyTenants(getPortalUserId())));
    }

    @GetMapping("/tenants/{tenantId}/evidence")
    @Operation(summary = "List all evidence for a business this auditor has access to")
    public ResponseEntity<ApiResponse<List<EvidenceResponse>>> getEvidence(@PathVariable UUID tenantId) {
        return ResponseEntity.ok(ApiResponse.success(dataService.getEvidence(getPortalUserId(), tenantId)));
    }

    @GetMapping("/tenants/{tenantId}/control-exceptions")
    @Operation(summary = "List every control exception (open and resolved) for a business this auditor has access to")
    public ResponseEntity<ApiResponse<List<ControlExceptionResponse>>> getControlExceptions(@PathVariable UUID tenantId) {
        return ResponseEntity.ok(ApiResponse.success(dataService.getControlExceptions(getPortalUserId(), tenantId)));
    }

    private UUID getPortalUserId() {
        return UUID.fromString(SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString());
    }
}