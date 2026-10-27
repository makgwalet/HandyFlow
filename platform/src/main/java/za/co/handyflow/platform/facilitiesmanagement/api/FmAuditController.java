package za.co.handyflow.platform.facilitiesmanagement.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.facilitiesmanagement.application.internal.FmAuditService;
import za.co.handyflow.platform.facilitiesmanagement.dto.FmAuditLogResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.UUID;

/** Read-only audit trail for auditors (FACILITIESMANAGEMENT_AUDIT_READ) and administrators. Newest first. */
@RestController
@RequestMapping("/api/v1/facilitiesmanagement/audit")
@RequiredArgsConstructor
@Tag(name = "Facilities Management - Audit", description = "Append-only audit trail of sensitive commercial and operational actions")
public class FmAuditController {

    private final FmAuditService auditService;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('FACILITIESMANAGEMENT_ADMIN','FACILITIESMANAGEMENT_AUDIT_READ')")
    public ResponseEntity<ApiResponse<Page<FmAuditLogResponse>>> list(
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) UUID entityId,
            @PageableDefault(size = 50) Pageable pageable) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.ok(ApiResponse.success(
                auditService.list(TenantContext.getTenantIdAsObject(), entityType, entityId, pageable)));
    }
}