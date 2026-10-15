package za.co.handyflow.platform.internalaudit.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.internalaudit.application.internal.AuditQualityService;
import za.co.handyflow.platform.internalaudit.application.internal.AuditQualityService.Actor;
import za.co.handyflow.platform.internalaudit.application.internal.QualityMetricsBuilder.Metrics;
import za.co.handyflow.platform.internalaudit.dto.AuditQualityDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/** Engagement quality review. The appointed reviewer needs AUDIT_MANAGE to answer; the service also checks that it is that person. */
@RestController
@RequestMapping("/api/v1/internal-audit/quality")
@RequiredArgsConstructor
@Tag(name = "Internal Audit - Quality", description = "Engagement quality review, methodology checklist and quality metrics")
public class AuditQualityController {
    private final AuditQualityService service;
    private final FeatureGuard featureGuard;

    private Actor me() { return new Actor(TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName()); }
    private <T> ResponseEntity<ApiResponse<T>> ok(T body) { featureGuard.requireModule("internal-audit"); return ResponseEntity.ok(ApiResponse.success(body)); }

    @GetMapping("/checklist")
    @PreAuthorize("hasAuthority('AUDIT_READ')")
    public ResponseEntity<ApiResponse<List<CheckView>>> checklist() { return ok(service.checklist()); }

    @GetMapping("/reviews")
    @PreAuthorize("hasAuthority('AUDIT_READ')")
    public ResponseEntity<ApiResponse<List<Summary>>> list(@RequestParam(required = false) UUID engagementId) {
        return ok(service.list(TenantContext.getTenantIdAsObject(), engagementId));
    }

    @GetMapping("/reviews/{id}")
    @PreAuthorize("hasAuthority('AUDIT_READ')")
    public ResponseEntity<ApiResponse<View>> get(@PathVariable UUID id) { return ok(service.get(TenantContext.getTenantIdAsObject(), id)); }

    @PostMapping("/reviews")
    @PreAuthorize("hasAnyAuthority('AUDIT_MANAGE','AUDIT_ADMIN')")
    public ResponseEntity<ApiResponse<View>> request(@RequestBody RequestReview req) {
        return ok(service.request(TenantContext.getTenantIdAsObject(), me(), req.engagementId(), req.reviewerId()));
    }

    @PutMapping("/reviews/{id}/checks/{code}")
    @PreAuthorize("hasAnyAuthority('AUDIT_MANAGE','AUDIT_ADMIN')")
    public ResponseEntity<ApiResponse<View>> answer(@PathVariable UUID id, @PathVariable String code, @RequestBody AnswerRequest req) {
        return ok(service.answer(TenantContext.getTenantIdAsObject(), me(), id, code, req.result(), req.note()));
    }

    @PostMapping("/reviews/{id}/complete")
    @PreAuthorize("hasAnyAuthority('AUDIT_MANAGE','AUDIT_ADMIN')")
    public ResponseEntity<ApiResponse<View>> complete(@PathVariable UUID id, @RequestBody CompleteRequest req) {
        return ok(service.complete(TenantContext.getTenantIdAsObject(), me(), id, req.conclusion(), req.note()));
    }

    @PostMapping("/reviews/{id}/cancel")
    @PreAuthorize("hasAnyAuthority('AUDIT_MANAGE','AUDIT_ADMIN')")
    public ResponseEntity<ApiResponse<View>> cancel(@PathVariable UUID id, @RequestBody CancelRequest req) {
        return ok(service.cancel(TenantContext.getTenantIdAsObject(), me(), id, req.reason()));
    }

    @GetMapping("/metrics")
    @PreAuthorize("hasAuthority('AUDIT_READ')")
    @Operation(summary = "Quality metrics across reviews, review notes and findings")
    public ResponseEntity<ApiResponse<Metrics>> metrics() { return ok(service.metrics(TenantContext.getTenantIdAsObject())); }
}
