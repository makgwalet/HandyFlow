package za.co.handyflow.platform.internalaudit.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.internalaudit.application.internal.AuditProgrammeService;
import za.co.handyflow.platform.internalaudit.application.internal.AuditProgrammeService.Actor;
import za.co.handyflow.platform.internalaudit.dto.AuditProgrammeDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/internal-audit/programme")
@RequiredArgsConstructor
@Tag(name = "Internal Audit - Programme library", description = "IT, cyber, operational and compliance control library and engagement programmes")
public class AuditProgrammeController {
    private final AuditProgrammeService service;
    private final FeatureGuard featureGuard;

    private Actor me() { return new Actor(TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName()); }
    private <T> ResponseEntity<ApiResponse<T>> ok(T body) { featureGuard.requireModule("internal-audit"); return ResponseEntity.ok(ApiResponse.success(body)); }

    @GetMapping("/library")
    @PreAuthorize("hasAuthority('AUDIT_READ')")
    public ResponseEntity<ApiResponse<Library>> library() { return ok(service.library()); }

    @GetMapping("/engagements/{engagementId}")
    @PreAuthorize("hasAuthority('AUDIT_READ')")
    public ResponseEntity<ApiResponse<Programme>> get(@PathVariable UUID engagementId) { return ok(service.get(TenantContext.getTenantIdAsObject(), engagementId)); }

    @PostMapping("/engagements/{engagementId}/apply")
    @PreAuthorize("hasAnyAuthority('AUDIT_MANAGE','AUDIT_ADMIN')")
    public ResponseEntity<ApiResponse<Programme>> apply(@PathVariable UUID engagementId, @RequestBody ApplyRequest req) {
        return ok(service.apply(TenantContext.getTenantIdAsObject(), me(), engagementId, req.domains(), req.codes()));
    }

    @PutMapping("/engagements/{engagementId}/steps/{code}")
    @PreAuthorize("hasAnyAuthority('AUDIT_MANAGE','AUDIT_ADMIN')")
    public ResponseEntity<ApiResponse<Programme>> record(@PathVariable UUID engagementId, @PathVariable String code, @RequestBody RecordRequest req) {
        return ok(service.record(TenantContext.getTenantIdAsObject(), me(), engagementId, code, req.status(), req.conclusion(), req.note()));
    }

    @PutMapping("/engagements/{engagementId}/steps/{code}/finding")
    @PreAuthorize("hasAnyAuthority('AUDIT_MANAGE','AUDIT_ADMIN')")
    public ResponseEntity<ApiResponse<Programme>> link(@PathVariable UUID engagementId, @PathVariable String code, @RequestBody LinkRequest req) {
        return ok(service.linkFinding(TenantContext.getTenantIdAsObject(), me(), engagementId, code, req.findingId()));
    }
}
