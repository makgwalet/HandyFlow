package za.co.handyflow.platform.auditor.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.auditor.application.internal.ExternalAuditEngagementService;
import za.co.handyflow.platform.auditor.domain.model.ExternalAuditEngagement;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auditors/engagements")
@RequiredArgsConstructor
@Tag(name = "External audit engagements", description = "The firm, period and scope of an external audit, and when auditors have access")
public class ExternalAuditEngagementController {

    private final ExternalAuditEngagementService service;

    public record EngagementRequest(String name, String firmName, LocalDate periodFrom, LocalDate periodTo, String entitiesInScope, String scopeNote) {}
    public record EngagementResponse(UUID id, String name, String firmName, LocalDate periodFrom, LocalDate periodTo, String entitiesInScope,
                                     String scopeNote, String status, Instant startedAt, Instant completedAt, Instant archivedAt) {
        static EngagementResponse of(ExternalAuditEngagement e) {
            return new EngagementResponse(e.getId(), e.getName(), e.getFirmName(), e.getPeriodFrom(), e.getPeriodTo(), e.getEntitiesInScope(),
                    e.getScopeNote(), e.getStatus(), e.getStartedAt(), e.getCompletedAt(), e.getArchivedAt());
        }
    }

    @GetMapping
    @PreAuthorize("hasAuthority('EXTERNAL_AUDIT_INVITE')")
    public ResponseEntity<ApiResponse<List<EngagementResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.success(service.list(TenantContext.getTenantIdAsObject()).stream().map(EngagementResponse::of).toList()));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('EXTERNAL_AUDIT_INVITE')")
    @Operation(summary = "Plan an external audit")
    public ResponseEntity<ApiResponse<EngagementResponse>> create(@RequestBody EngagementRequest r) {
        return ResponseEntity.ok(ApiResponse.success("Engagement created", EngagementResponse.of(service.create(TenantContext.getTenantIdAsObject(),
                r.name(), r.firmName(), r.periodFrom(), r.periodTo(), r.entitiesInScope(), r.scopeNote(), TenantContext.getCurrentUserId()))));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('EXTERNAL_AUDIT_INVITE')")
    public ResponseEntity<ApiResponse<EngagementResponse>> update(@PathVariable UUID id, @RequestBody EngagementRequest r) {
        return ResponseEntity.ok(ApiResponse.success(EngagementResponse.of(service.update(TenantContext.getTenantIdAsObject(), id,
                r.name(), r.firmName(), r.periodFrom(), r.periodTo(), r.entitiesInScope(), r.scopeNote()))));
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasAuthority('EXTERNAL_AUDIT_INVITE')")
    @Operation(summary = "Open the engagement: attached auditors can now get in")
    public ResponseEntity<ApiResponse<EngagementResponse>> start(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(EngagementResponse.of(service.start(TenantContext.getTenantIdAsObject(), id))));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('EXTERNAL_AUDIT_INVITE')")
    @Operation(summary = "Finish the engagement: attached auditors lose access")
    public ResponseEntity<ApiResponse<EngagementResponse>> complete(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(EngagementResponse.of(service.complete(TenantContext.getTenantIdAsObject(), id, TenantContext.getCurrentUserId()))));
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasAuthority('EXTERNAL_AUDIT_INVITE')")
    @Operation(summary = "Archive a finished engagement and revoke every grant that belonged to it")
    public ResponseEntity<ApiResponse<EngagementResponse>> archive(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(EngagementResponse.of(service.archive(TenantContext.getTenantIdAsObject(), id, TenantContext.getCurrentUserId()))));
    }

    @GetMapping("/{id}/manifest")
    @PreAuthorize("hasAuthority('EXTERNAL_AUDIT_INVITE')")
    @Operation(summary = "Who had access and what was shared, for the audit file")
    public ResponseEntity<ApiResponse<Object>> manifest(@PathVariable UUID id) {
        var m = service.manifest(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success((Object) java.util.Map.of(
                "engagement", EngagementResponse.of(m.engagement()), "auditors", m.auditors(), "shares", m.shares())));
    }
}
