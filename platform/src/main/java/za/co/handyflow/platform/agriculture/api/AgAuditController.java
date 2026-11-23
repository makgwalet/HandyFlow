package za.co.handyflow.platform.agriculture.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import za.co.handyflow.platform.agriculture.application.internal.AgAuditService;
import za.co.handyflow.platform.agriculture.dto.AuditResponse;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/agriculture/audit")
@RequiredArgsConstructor
@Tag(name = "Agriculture - Audit", description = "Who added and last changed a record")
public class AgAuditController {

    private final AgAuditService auditService;
    private final FeatureGuard featureGuard;

    @GetMapping("/{type}/{id}")
    @PreAuthorize("hasAuthority('AGRICULTURE_READ')")
    @Operation(summary = "Who added a record and who last changed it (animals, groups, health-events, breeding-records, inventory-items, crop-cycles, weights, mortality, movements, feed-records, scouting, harvests, input-applications)")
    public ResponseEntity<ApiResponse<AuditResponse>> get(@PathVariable String type, @PathVariable UUID id) {
        featureGuard.requireModule("agriculture");
        return ResponseEntity.ok(ApiResponse.success(auditService.get(TenantContext.getTenantIdAsObject(), type, id)));
    }

    @GetMapping("/{type}")
    @PreAuthorize("hasAuthority('AGRICULTURE_READ')")
    @Operation(summary = "The same for several records of one kind at once (ids=a,b,c; 200 at most). Missing ids are left out")
    public ResponseEntity<ApiResponse<Map<UUID, AuditResponse>>> getMany(@PathVariable String type, @RequestParam List<UUID> ids) {
        featureGuard.requireModule("agriculture");
        return ResponseEntity.ok(ApiResponse.success(auditService.getMany(TenantContext.getTenantIdAsObject(), type, ids)));
    }
}
