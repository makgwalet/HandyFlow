package za.co.handyflow.platform.evidence.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import za.co.handyflow.platform.evidence.application.EvidenceFacade;
import za.co.handyflow.platform.evidence.dto.EvidenceResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/**
 * Generic evidence endpoints — any module's own controller can call
 * these directly instead of building its own attach/list/download
 * trio, the way AccFicaDocument/TaskAttachment/RecAgencyCandidate's CV
 * each did independently. A module can also call EvidenceFacade
 * directly from its own controller if it wants a module-specific URL
 * shape instead (e.g. /expenses/claims/{id}/receipts) — this controller
 * exists for callers happy with the generic shape.
 * <p>
 * TenantContext.getTenantIdAsObject() / getCurrentUserId() /
 * getCurrentUserName() confirmed directly against real source
 * (TenantContext.java, UserController.java) — not guessed.
 * <p>
 * Deliberately has NO FeatureGuard.requireModule() check, unlike
 * CustomerController's @ModelAttribute pattern for CRM — evidence is
 * meant to be foundational shared infrastructure any tenant can use
 * regardless of which paid modules they're subscribed to, the same
 * status as notifications, not a subscribable module of its own. If
 * that's wrong and evidence SHOULD be gated/metered per tenant, this is
 * the one line to add back.
 */
@RestController
@RequestMapping("/api/v1/evidence")
@RequiredArgsConstructor
@Tag(name = "Evidence", description = "Shared evidence attachment — any module's records")
public class EvidenceController {

    private final EvidenceFacade evidenceFacade;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Attach a file as evidence against any module's entity")
    public ResponseEntity<ApiResponse<EvidenceResponse>> attach(
            @RequestParam("file") MultipartFile file,
            @RequestParam String evidenceType,
            @RequestParam String sourceModule,
            @RequestParam String relatedEntityType,
            @RequestParam UUID relatedEntityId,
            @RequestParam(required = false) UUID periodId) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        UUID uploadedBy = TenantContext.getCurrentUserId();
        String uploadedByName = TenantContext.getCurrentUserName();
        return ResponseEntity.ok(ApiResponse.success(evidenceFacade.attach(
                tenantId, file, evidenceType, sourceModule, relatedEntityType, relatedEntityId,
                periodId, uploadedBy, uploadedByName)));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List active evidence attached to a specific entity")
    public ResponseEntity<ApiResponse<List<EvidenceResponse>>> list(
            @RequestParam String sourceModule,
            @RequestParam String relatedEntityType,
            @RequestParam UUID relatedEntityId) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(
                evidenceFacade.listFor(tenantId, sourceModule, relatedEntityType, relatedEntityId)));
    }

    @GetMapping("/{evidenceId}/download")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Download a piece of evidence")
    public ResponseEntity<byte[]> download(@PathVariable UUID evidenceId) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        EvidenceFacade.DownloadedEvidence file = evidenceFacade.download(tenantId, evidenceId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.fileName() + "\"")
                .contentType(MediaType.parseMediaType(file.contentType()))
                .body(file.content());
    }

    @PostMapping("/{evidenceId}/detach")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Detach evidence (soft — the record and underlying file are preserved)")
    public ResponseEntity<ApiResponse<Void>> detach(@PathVariable UUID evidenceId) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        evidenceFacade.detach(tenantId, evidenceId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}