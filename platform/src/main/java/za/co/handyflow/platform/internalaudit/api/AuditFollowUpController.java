package za.co.handyflow.platform.internalaudit.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.internalaudit.application.internal.AuditFollowUpService;
import za.co.handyflow.platform.internalaudit.application.internal.AuditFollowUpService.Actor;
import za.co.handyflow.platform.internalaudit.dto.AuditFollowUpDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/internal-audit/follow-ups")
@RequiredArgsConstructor
@Tag(name = "Internal Audit - Follow-up audits", description = "Checking what became of the findings of an earlier engagement")
public class AuditFollowUpController {
    private final AuditFollowUpService service;
    private final FeatureGuard featureGuard;

    private Actor me() { return new Actor(TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName()); }
    private <T> ResponseEntity<ApiResponse<T>> ok(T body) { featureGuard.requireModule("internal-audit"); return ResponseEntity.ok(ApiResponse.success(body)); }

    @GetMapping
    @PreAuthorize("hasAuthority('AUDIT_READ')")
    public ResponseEntity<ApiResponse<List<Summary>>> list() { return ok(service.list(TenantContext.getTenantIdAsObject())); }

    @GetMapping("/{engagementId}")
    @PreAuthorize("hasAuthority('AUDIT_READ')")
    public ResponseEntity<ApiResponse<View>> get(@PathVariable UUID engagementId) { return ok(service.get(TenantContext.getTenantIdAsObject(), engagementId)); }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('AUDIT_MANAGE','AUDIT_ADMIN')")
    public ResponseEntity<ApiResponse<View>> start(@RequestBody StartRequest req) {
        return ok(service.start(TenantContext.getTenantIdAsObject(), me(), req.engagementId(), req.priorEngagementId()));
    }

    @PutMapping("/{engagementId}/findings/{findingId}")
    @PreAuthorize("hasAnyAuthority('AUDIT_MANAGE','AUDIT_ADMIN')")
    public ResponseEntity<ApiResponse<View>> assess(@PathVariable UUID engagementId, @PathVariable UUID findingId, @RequestBody AssessRequest req) {
        return ok(service.assess(TenantContext.getTenantIdAsObject(), me(), engagementId, findingId, req.outcome(), req.note()));
    }
}
