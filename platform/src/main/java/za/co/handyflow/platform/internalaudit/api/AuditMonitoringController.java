package za.co.handyflow.platform.internalaudit.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.internalaudit.application.internal.AuditMonitoringService;
import za.co.handyflow.platform.internalaudit.application.internal.AuditMonitoringService.Actor;
import za.co.handyflow.platform.internalaudit.dto.AuditMonitoringDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/internal-audit/monitoring")
@RequiredArgsConstructor
@Tag(name = "Internal Audit - Continuous monitoring", description = "Patterns in journals, bank payments and permissions for a person to review")
public class AuditMonitoringController {
    private final AuditMonitoringService service;
    private final FeatureGuard featureGuard;

    private Actor me() { return new Actor(TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName()); }
    private <T> ResponseEntity<ApiResponse<T>> ok(T body) { featureGuard.requireModule("internal-audit"); return ResponseEntity.ok(ApiResponse.success(body)); }

    @GetMapping("/alerts")
    @PreAuthorize("hasAuthority('AUDIT_READ')")
    public ResponseEntity<ApiResponse<Alerts>> alerts() { return ok(service.list(TenantContext.getTenantIdAsObject())); }

    @PostMapping("/scan")
    @PreAuthorize("hasAnyAuthority('AUDIT_MANAGE','AUDIT_ADMIN')")
    public ResponseEntity<ApiResponse<ScanResult>> scan(@RequestBody(required = false) ScanRequest req) {
        return ok(service.scan(TenantContext.getTenantIdAsObject(), LocalDate.now(ZoneId.of("Africa/Johannesburg")), req));
    }

    @PostMapping("/alerts/{id}/review")
    @PreAuthorize("hasAnyAuthority('AUDIT_MANAGE','AUDIT_ADMIN')")
    public ResponseEntity<ApiResponse<Alerts>> review(@PathVariable UUID id, @RequestBody ReviewRequest req) {
        return ok(service.review(TenantContext.getTenantIdAsObject(), me(), id, req.status(), req.note(), req.findingId()));
    }
}
