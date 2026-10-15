package za.co.handyflow.platform.internalaudit.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.internalaudit.application.internal.AuditReportService;
import za.co.handyflow.platform.internalaudit.application.internal.AuditReportService.Actor;
import za.co.handyflow.platform.internalaudit.dto.AuditReportDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/**
 * Engagement reports. Audit managers draft, run the factual-accuracy review and name recipients; the Head of Internal Audit (AUDIT_ADMIN) issues.
 * Reviewers and recipients need no audit permission of their own: the service lets them see only the reports they are named on.
 */
@RestController
@RequestMapping("/api/v1/internal-audit/reports")
@RequiredArgsConstructor
@Tag(name = "Internal Audit - Reports", description = "Report drafting, factual-accuracy review, issue and controlled distribution")
public class AuditReportController {
    private final AuditReportService service;
    private final FeatureGuard featureGuard;

    private Actor actor(Authentication auth) {
        boolean manager = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("AUDIT_MANAGE") || a.getAuthority().equals("AUDIT_ADMIN"));
        return new Actor(TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName(), manager);
    }
    private <T> ResponseEntity<ApiResponse<T>> ok(T body) { return ResponseEntity.ok(ApiResponse.success(body)); }
    private void gate() { featureGuard.requireModule("internal-audit"); }

    @GetMapping("/templates")
    @PreAuthorize("hasAnyAuthority('AUDIT_MANAGE','AUDIT_ADMIN')")
    public ResponseEntity<ApiResponse<List<TemplateView>>> templates() { gate(); return ok(service.templates()); }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Reports: all of them for audit managers; only the ones you are named on otherwise")
    public ResponseEntity<ApiResponse<List<Summary>>> list(Authentication auth, @RequestParam(required = false) UUID engagementId) {
        gate(); return ok(service.list(TenantContext.getTenantIdAsObject(), actor(auth), engagementId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Read a report. Reading an issued report as a named recipient is counted and put on the audit trail")
    public ResponseEntity<ApiResponse<View>> get(Authentication auth, @PathVariable UUID id) {
        gate(); return ok(service.get(TenantContext.getTenantIdAsObject(), actor(auth), id));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('AUDIT_MANAGE','AUDIT_ADMIN')")
    public ResponseEntity<ApiResponse<View>> create(Authentication auth, @RequestBody CreateRequest req) {
        gate(); return ok(service.create(TenantContext.getTenantIdAsObject(), actor(auth), req.engagementId(), req.template(), req.title()));
    }

    @PostMapping("/{id}/new-version")
    @PreAuthorize("hasAnyAuthority('AUDIT_MANAGE','AUDIT_ADMIN')")
    public ResponseEntity<ApiResponse<View>> newVersion(Authentication auth, @PathVariable UUID id) {
        gate(); return ok(service.newVersion(TenantContext.getTenantIdAsObject(), actor(auth), id));
    }

    @PutMapping("/{id}/sections/{position}")
    @PreAuthorize("hasAnyAuthority('AUDIT_MANAGE','AUDIT_ADMIN')")
    public ResponseEntity<ApiResponse<View>> editSection(Authentication auth, @PathVariable UUID id, @PathVariable int position, @RequestBody SectionRequest req) {
        gate(); return ok(service.editSection(TenantContext.getTenantIdAsObject(), actor(auth), id, position, req.title(), req.body()));
    }

    @PostMapping("/{id}/people")
    @PreAuthorize("hasAnyAuthority('AUDIT_MANAGE','AUDIT_ADMIN')")
    public ResponseEntity<ApiResponse<View>> addPerson(Authentication auth, @PathVariable UUID id, @RequestBody PersonRequest req) {
        gate(); return ok(service.addPerson(TenantContext.getTenantIdAsObject(), actor(auth), id, req.kind(), req.userId()));
    }

    @DeleteMapping("/{id}/people/{personId}")
    @PreAuthorize("hasAnyAuthority('AUDIT_MANAGE','AUDIT_ADMIN')")
    public ResponseEntity<ApiResponse<View>> removePerson(Authentication auth, @PathVariable UUID id, @PathVariable UUID personId) {
        gate(); return ok(service.removePerson(TenantContext.getTenantIdAsObject(), actor(auth), id, personId));
    }

    @PostMapping("/{id}/factual-review/start")
    @PreAuthorize("hasAnyAuthority('AUDIT_MANAGE','AUDIT_ADMIN')")
    public ResponseEntity<ApiResponse<View>> start(Authentication auth, @PathVariable UUID id) {
        gate(); return ok(service.startFactualReview(TenantContext.getTenantIdAsObject(), actor(auth), id));
    }

    @PostMapping("/{id}/factual-review/items")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<View>> raise(Authentication auth, @PathVariable UUID id, @RequestBody ItemRequest req) {
        gate(); return ok(service.raiseItem(TenantContext.getTenantIdAsObject(), actor(auth), id, req.sectionPosition(), req.comment()));
    }

    @PostMapping("/{id}/factual-review/items/{itemId}/respond")
    @PreAuthorize("hasAnyAuthority('AUDIT_MANAGE','AUDIT_ADMIN')")
    public ResponseEntity<ApiResponse<View>> respond(Authentication auth, @PathVariable UUID id, @PathVariable UUID itemId, @RequestBody RespondRequest req) {
        gate(); return ok(service.respondItem(TenantContext.getTenantIdAsObject(), actor(auth), id, itemId, req.decision(), req.response()));
    }

    @PostMapping("/{id}/factual-review/close")
    @PreAuthorize("hasAnyAuthority('AUDIT_MANAGE','AUDIT_ADMIN')")
    public ResponseEntity<ApiResponse<View>> close(Authentication auth, @PathVariable UUID id) {
        gate(); return ok(service.closeFactualReview(TenantContext.getTenantIdAsObject(), actor(auth), id));
    }

    @PostMapping("/{id}/reopen")
    @PreAuthorize("hasAnyAuthority('AUDIT_MANAGE','AUDIT_ADMIN')")
    public ResponseEntity<ApiResponse<View>> reopen(Authentication auth, @PathVariable UUID id) {
        gate(); return ok(service.reopen(TenantContext.getTenantIdAsObject(), actor(auth), id));
    }

    @PostMapping("/{id}/issue")
    @PreAuthorize("hasAuthority('AUDIT_ADMIN')")
    @Operation(summary = "Issue the report: needs a closed factual-accuracy review and an approved sign-off")
    public ResponseEntity<ApiResponse<View>> issue(Authentication auth, @PathVariable UUID id) {
        gate(); return ok(service.issue(TenantContext.getTenantIdAsObject(), actor(auth), id));
    }
}
