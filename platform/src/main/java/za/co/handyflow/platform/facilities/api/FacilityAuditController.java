package za.co.handyflow.platform.facilities.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.facilities.application.internal.FacilityAuditService;
import za.co.handyflow.platform.facilities.dto.AuditLogResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.UUID;

/** Read-only audit trail for auditors (FACILITIES_AUDIT_READ) and administrators. Newest first. */
@RestController
@RequestMapping("/api/v1/facilities/audit")
@RequiredArgsConstructor
@Tag(name = "Facilities - Audit", description = "Append-only audit trail of sensitive actions")
public class FacilityAuditController {

    private final FacilityAuditService auditService;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('FACILITIES_ADMIN','FACILITIES_AUDIT_READ')")
    public ResponseEntity<ApiResponse<Page<AuditLogResponse>>> list(
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) UUID entityId,
            @PageableDefault(size = 50) Pageable pageable) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success(
                auditService.list(TenantContext.getTenantIdAsObject(), entityType, entityId, pageable)));
    }
}