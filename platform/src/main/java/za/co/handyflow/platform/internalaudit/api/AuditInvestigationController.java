package za.co.handyflow.platform.internalaudit.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.internalaudit.application.internal.AuditInvestigationService;
import za.co.handyflow.platform.internalaudit.application.internal.AuditInvestigationService.Actor;
import za.co.handyflow.platform.internalaudit.dto.AuditInvestigationDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/**
 * Investigation case files. Every call needs AUDIT_INVESTIGATE, which no role has until an administrator gives it; the service then requires the
 * caller to be named on the case. Administrators are not an exception to the second check.
 */
@RestController
@RequestMapping("/api/v1/internal-audit/investigations")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('AUDIT_INVESTIGATE')")
@Tag(name = "Internal Audit - Investigations", description = "Confidential investigation case files with a tamper-evident record")
public class AuditInvestigationController {
    private final AuditInvestigationService service;
    private final FeatureGuard featureGuard;

    private Actor me() { return new Actor(TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName()); }
    private <T> ResponseEntity<ApiResponse<T>> ok(T body) { featureGuard.requireModule("internal-audit"); return ResponseEntity.ok(ApiResponse.success(body)); }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Summary>>> list() { return ok(service.list(TenantContext.getTenantIdAsObject(), me())); }

    @PostMapping
    public ResponseEntity<ApiResponse<View>> open(@RequestBody OpenRequest req) { return ok(service.open(TenantContext.getTenantIdAsObject(), me(), req.title(), req.summary())); }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<View>> get(@PathVariable UUID id) { return ok(service.get(TenantContext.getTenantIdAsObject(), me(), id)); }

    @GetMapping("/{id}/verify")
    public ResponseEntity<ApiResponse<Verification>> verify(@PathVariable UUID id) { return ok(service.verify(TenantContext.getTenantIdAsObject(), me(), id)); }

    @PostMapping("/{id}/members")
    public ResponseEntity<ApiResponse<View>> addMember(@PathVariable UUID id, @RequestBody MemberRequest req) {
        return ok(service.addMember(TenantContext.getTenantIdAsObject(), me(), id, req.userId(), req.role()));
    }

    @DeleteMapping("/{id}/members/{userId}")
    public ResponseEntity<ApiResponse<View>> removeMember(@PathVariable UUID id, @PathVariable UUID userId) {
        return ok(service.removeMember(TenantContext.getTenantIdAsObject(), me(), id, userId));
    }

    @PostMapping("/{id}/notes")
    public ResponseEntity<ApiResponse<View>> note(@PathVariable UUID id, @RequestBody NoteRequest req) { return ok(service.addNote(TenantContext.getTenantIdAsObject(), me(), id, req.text())); }

    @PostMapping("/{id}/evidence")
    public ResponseEntity<ApiResponse<View>> collect(@PathVariable UUID id, @RequestBody EvidenceRequest req) {
        return ok(service.collectEvidence(TenantContext.getTenantIdAsObject(), me(), id, req.description(), req.source()));
    }

    @PostMapping("/{id}/evidence/{evidenceId}/transfer")
    public ResponseEntity<ApiResponse<View>> transfer(@PathVariable UUID id, @PathVariable UUID evidenceId, @RequestBody TransferRequest req) {
        return ok(service.transferEvidence(TenantContext.getTenantIdAsObject(), me(), id, evidenceId, req.toUserId(), req.reason()));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<ApiResponse<View>> status(@PathVariable UUID id, @RequestBody StatusRequest req) { return ok(service.setReview(TenantContext.getTenantIdAsObject(), me(), id, req.underReview())); }

    @PostMapping("/{id}/close")
    public ResponseEntity<ApiResponse<View>> close(@PathVariable UUID id, @RequestBody CloseRequest req) { return ok(service.close(TenantContext.getTenantIdAsObject(), me(), id, req.reason())); }
}
