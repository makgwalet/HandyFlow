// security/api/SecurityAccessLogController.java
package za.co.handyflow.platform.security.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.security.application.internal.SensitiveReadAuditService;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Security - Access log")
public class SecurityAccessLogController {

    private final SensitiveReadAuditService audit;
    private final FeatureGuard featureGuard;

    @GetMapping("/api/v1/security/access-log")
    @PreAuthorize("hasAuthority('SECURITY_ADMIN')")
    @Operation(summary = "Who revealed a guard's full ID or bank number, downloaded evidence, exported payroll or opened vetting",
            description = "Newest first, within your company. Optionally narrow to one record (entityId) or one kind (GUARD, COMPLAINT, INCIDENT, " +
                    "PAYROLL_PERIOD, PRINCIPAL, DECLINED_REGISTER). Shows who, what and when, and ids of the items; never the data itself.")
    public ResponseEntity<ApiResponse<List<SensitiveReadAuditService.Entry>>> list(
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) UUID entityId,
            @RequestParam(required = false) Integer limit) {
        featureGuard.requireModule("security");
        return ResponseEntity.ok(ApiResponse.success("OK", audit.recent(TenantContext.getTenantIdAsObject(), entityType, entityId, limit)));
    }
}
