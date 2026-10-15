package za.co.handyflow.platform.auditor.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.auditor.application.internal.AuditQueryService;
import za.co.handyflow.platform.auditor.application.internal.AuditorPortalDataService;
import za.co.handyflow.platform.auditor.application.internal.EvidenceRequestService.Actor;
import za.co.handyflow.platform.auditor.application.internal.EvidenceRequestService.Scope;
import za.co.handyflow.platform.auditor.domain.model.AuditorAccessGrant;
import za.co.handyflow.platform.auditor.dto.AuditQueryDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/** The auditor's side. Every call re-checks access and limits what is visible to the auditor's engagement. */
@RestController
@RequestMapping("/api/v1/auditor/portal/tenants/{tenantId}/queries")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('PORTAL_USER')")
@Tag(name = "Auditor Client Portal", description = "Queries and review notes raised by the external auditor")
public class AuditorQueryController {

    private final AuditQueryService service;
    private final AuditorPortalDataService portal;

    private record Ctx(TenantId tenant, Scope scope, Actor actor) {}

    private Ctx ctx(UUID tenantId) {
        UUID user = UUID.fromString(SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString());
        AuditorAccessGrant g = portal.requireGrant(user, tenantId);
        return new Ctx(TenantId.of(tenantId), Scope.engagement(g.getEngagementId()), Actor.auditor(user, g.getInviteEmail()));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<QueryView>>> list(@PathVariable UUID tenantId) {
        Ctx c = ctx(tenantId);
        return ResponseEntity.ok(ApiResponse.success(service.list(c.tenant(), c.scope(), c.actor())));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<QueryView>> detail(@PathVariable UUID tenantId, @PathVariable UUID id) {
        Ctx c = ctx(tenantId);
        return ResponseEntity.ok(ApiResponse.success(service.detail(c.tenant(), c.scope(), c.actor(), id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<QueryView>> raise(@PathVariable UUID tenantId, @RequestBody RaiseRequest req) {
        Ctx c = ctx(tenantId);
        return ResponseEntity.ok(ApiResponse.success(service.raise(c.tenant(), c.scope(), c.actor(), req)));
    }

    @PostMapping("/{id}/follow-up")
    public ResponseEntity<ApiResponse<QueryView>> followUp(@PathVariable UUID tenantId, @PathVariable UUID id, @RequestBody TextRequest req) {
        Ctx c = ctx(tenantId);
        return ResponseEntity.ok(ApiResponse.success(service.followUp(c.tenant(), c.scope(), c.actor(), id, req.text())));
    }

    @PostMapping("/{id}/resolve")
    public ResponseEntity<ApiResponse<QueryView>> resolve(@PathVariable UUID tenantId, @PathVariable UUID id, @RequestBody(required = false) TextRequest req) {
        Ctx c = ctx(tenantId);
        return ResponseEntity.ok(ApiResponse.success(service.resolve(c.tenant(), c.scope(), c.actor(), id, req == null ? null : req.text())));
    }

    @PostMapping("/{id}/withdraw")
    public ResponseEntity<ApiResponse<QueryView>> withdraw(@PathVariable UUID tenantId, @PathVariable UUID id, @RequestBody TextRequest req) {
        Ctx c = ctx(tenantId);
        return ResponseEntity.ok(ApiResponse.success(service.withdraw(c.tenant(), c.scope(), c.actor(), id, req.text())));
    }
}
