package za.co.handyflow.platform.auditor.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.auditor.application.internal.AuditorPortalDataService;
import za.co.handyflow.platform.auditor.application.internal.EvidenceRequestService;
import za.co.handyflow.platform.auditor.application.internal.EvidenceRequestService.Actor;
import za.co.handyflow.platform.auditor.application.internal.EvidenceRequestService.Scope;
import za.co.handyflow.platform.auditor.domain.model.AuditorAccessGrant;
import za.co.handyflow.platform.auditor.dto.EvidenceRequestDtos.*;
import za.co.handyflow.platform.evidence.application.EvidenceFacade;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/** The auditor's side of evidence requests. Every call re-checks the auditor's access and limits what is visible to their engagement. */
@RestController
@RequestMapping("/api/v1/auditor/portal/tenants/{tenantId}/evidence-requests")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('PORTAL_USER')")
@Tag(name = "Auditor Client Portal", description = "Evidence requests raised and reviewed by the external auditor")
public class AuditorEvidenceRequestController {

    private final EvidenceRequestService service;
    private final AuditorPortalDataService portal;

    private UUID portalUserId() { return UUID.fromString(SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString()); }

    private record Ctx(TenantId tenant, Scope scope, Actor actor) {}

    private Ctx ctx(UUID tenantId) {
        UUID user = portalUserId();
        AuditorAccessGrant g = portal.requireGrant(user, tenantId);
        return new Ctx(TenantId.of(tenantId), Scope.engagement(g.getEngagementId()), Actor.auditor(user, g.getInviteEmail()));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<RequestView>>> list(@PathVariable UUID tenantId) {
        Ctx c = ctx(tenantId);
        return ResponseEntity.ok(ApiResponse.success(service.list(c.tenant(), c.scope())));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RequestView>> detail(@PathVariable UUID tenantId, @PathVariable UUID id) {
        Ctx c = ctx(tenantId);
        return ResponseEntity.ok(ApiResponse.success(service.detail(c.tenant(), c.scope(), id)));
    }

    @PostMapping
    @Operation(summary = "Ask the business for a document")
    public ResponseEntity<ApiResponse<RequestView>> create(@PathVariable UUID tenantId, @RequestBody CreateRequest req) {
        Ctx c = ctx(tenantId);
        return ResponseEntity.ok(ApiResponse.success(service.create(c.tenant(), c.scope(), c.actor(), req)));
    }

    @PostMapping("/{id}/comments")
    public ResponseEntity<ApiResponse<RequestView>> comment(@PathVariable UUID tenantId, @PathVariable UUID id, @RequestBody TextRequest req) {
        Ctx c = ctx(tenantId);
        return ResponseEntity.ok(ApiResponse.success(service.comment(c.tenant(), c.scope(), c.actor(), id, req.text())));
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Accept the files, or accept that the item is not available")
    public ResponseEntity<ApiResponse<RequestView>> approve(@PathVariable UUID tenantId, @PathVariable UUID id, @RequestBody(required = false) TextRequest req) {
        Ctx c = ctx(tenantId);
        return ResponseEntity.ok(ApiResponse.success(service.approve(c.tenant(), c.scope(), c.actor(), id, req == null ? null : req.text())));
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Send the answer back, saying what is wrong")
    public ResponseEntity<ApiResponse<RequestView>> reject(@PathVariable UUID tenantId, @PathVariable UUID id, @RequestBody TextRequest req) {
        Ctx c = ctx(tenantId);
        return ResponseEntity.ok(ApiResponse.success(service.reject(c.tenant(), c.scope(), c.actor(), id, req.text())));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<RequestView>> cancel(@PathVariable UUID tenantId, @PathVariable UUID id, @RequestBody TextRequest req) {
        Ctx c = ctx(tenantId);
        return ResponseEntity.ok(ApiResponse.success(service.cancel(c.tenant(), c.scope(), c.actor(), id, req.text())));
    }

    @GetMapping("/{id}/files/{evidenceId}")
    public ResponseEntity<byte[]> download(@PathVariable UUID tenantId, @PathVariable UUID id, @PathVariable UUID evidenceId) {
        Ctx c = ctx(tenantId);
        EvidenceFacade.DownloadedEvidence f = service.download(c.tenant(), c.scope(), id, evidenceId);
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + f.fileName() + "\"")
                .contentType(MediaType.parseMediaType(f.contentType())).body(f.content());
    }
}
