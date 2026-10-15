package za.co.handyflow.platform.audittrail.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.audittrail.application.internal.AuditTrailService;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/audit-trail")
@RequiredArgsConstructor
@Tag(name = "Audit Trail", description = "Who did what to which audit item")
public class AuditTrailController {

    private final AuditTrailService service;

    @GetMapping
    @PreAuthorize("hasAuthority('AUDIT_ADMIN')")
    @Operation(summary = "Newest first. Filter by item type and id; at most 500 rows.")
    public ResponseEntity<ApiResponse<List<AuditTrailService.Event>>> list(
            @RequestParam(required = false) String itemType,
            @RequestParam(required = false) UUID itemId,
            @RequestParam(defaultValue = "200") int limit) {
        return ResponseEntity.ok(ApiResponse.success(service.list(TenantContext.getTenantIdAsObject(), itemType, itemId, limit)));
    }
}
