package za.co.handyflow.platform.legalcompliance.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.evidence.application.EvidenceFacade;
import za.co.handyflow.platform.evidence.dto.EvidenceResponse;
import za.co.handyflow.platform.legalcompliance.application.internal.LegalCompliancePdfService;
import za.co.handyflow.platform.legalcompliance.application.internal.LitigationMatterService;
import za.co.handyflow.platform.legalcompliance.domain.model.LitigationMatter;
import za.co.handyflow.platform.legalcompliance.domain.model.LitigationStatus;
import za.co.handyflow.platform.legalcompliance.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Litigation / dispute register. The evidence endpoints here are a
 * module-specific convenience wrapper over the shared EvidenceFacade —
 * EvidenceController's own Javadoc explicitly says a module may do this
 * instead of making every caller use the generic
 * /api/v1/evidence?sourceModule=...&relatedEntityType=... shape. This is
 * the Layer 4 cross-module integration: attaching court filings,
 * correspondence, and other matter documents without legalcompliance
 * duplicating any file-storage logic of its own.
 */
@RestController
@RequestMapping("/api/v1/legalcompliance/matters")
@RequiredArgsConstructor
@Tag(name = "Legal/Compliance - Litigation Matters", description = "Litigation / dispute register")
public class LitigationMatterController {

    private static final String EVIDENCE_ENTITY_TYPE = "LitigationMatter";

    private final LitigationMatterService matterService;
    private final EvidenceFacade evidenceFacade;
    private final LegalCompliancePdfService pdfService;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('LEGALCOMPLIANCE_READ','LEGALCOMPLIANCE_MANAGE','LEGALCOMPLIANCE_ADMIN')")
    public ResponseEntity<ApiResponse<Page<LitigationMatterResponse>>> list(
            @RequestParam(required = false) LitigationStatus status,
            @PageableDefault(size = 20, sort = "openedDate") Pageable pageable) {
        featureGuard.requireModule("legalcompliance");
        Page<LitigationMatterResponse> page = matterService
                .list(TenantContext.getTenantIdAsObject(), status, pageable)
                .map(LitigationMatterResponse::of);
        return ResponseEntity.ok(ApiResponse.success(page));
    }

    @GetMapping("/count")
    @PreAuthorize("hasAnyAuthority('LEGALCOMPLIANCE_READ','LEGALCOMPLIANCE_MANAGE','LEGALCOMPLIANCE_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Long>>> count() {
        featureGuard.requireModule("legalcompliance");
        long count = matterService.count(TenantContext.getTenantIdAsObject());
        return ResponseEntity.ok(ApiResponse.success(Map.of("count", count)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('LEGALCOMPLIANCE_READ','LEGALCOMPLIANCE_MANAGE','LEGALCOMPLIANCE_ADMIN')")
    public ResponseEntity<ApiResponse<LitigationMatterResponse>> get(@PathVariable UUID id) {
        featureGuard.requireModule("legalcompliance");
        LitigationMatter matter = matterService.get(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success(LitigationMatterResponse.of(matter)));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('LEGALCOMPLIANCE_MANAGE','LEGALCOMPLIANCE_ADMIN')")
    @Operation(summary = "Open a litigation matter — assigns a matter number")
    public ResponseEntity<ApiResponse<LitigationMatterResponse>> create(
            @Valid @RequestBody CreateLitigationMatterRequest req) {
        featureGuard.requireModule("legalcompliance");
        LitigationMatter matter = matterService.create(
                TenantContext.getTenantIdAsObject(), req.title(), req.matterType(), req.opposingParty(),
                req.ourSide(), req.estimatedExposure(), req.legalRepresentative(), req.courtOrForum(),
                req.caseReference(), req.openedDate(), req.nextKeyDate(), req.description(),
                req.linkedContractId(), TenantContext.getCurrentUserId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Litigation matter opened", LitigationMatterResponse.of(matter)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('LEGALCOMPLIANCE_MANAGE','LEGALCOMPLIANCE_ADMIN')")
    public ResponseEntity<ApiResponse<LitigationMatterResponse>> update(
            @PathVariable UUID id, @Valid @RequestBody UpdateLitigationMatterRequest req) {
        featureGuard.requireModule("legalcompliance");
        LitigationMatter matter = matterService.update(
                TenantContext.getTenantIdAsObject(), id, req.title(), req.opposingParty(), req.ourSide(),
                req.estimatedExposure(), req.legalRepresentative(), req.courtOrForum(), req.caseReference(),
                req.nextKeyDate(), req.description());
        return ResponseEntity.ok(ApiResponse.success("Matter updated", LitigationMatterResponse.of(matter)));
    }

    @PostMapping("/{id}/advance-status")
    @PreAuthorize("hasAnyAuthority('LEGALCOMPLIANCE_MANAGE','LEGALCOMPLIANCE_ADMIN')")
    public ResponseEntity<ApiResponse<LitigationMatterResponse>> advanceStatus(
            @PathVariable UUID id, @Valid @RequestBody AdvanceLitigationStatusRequest req) {
        featureGuard.requireModule("legalcompliance");
        LitigationMatter matter = matterService.advanceStatus(TenantContext.getTenantIdAsObject(), id, req.status());
        return ResponseEntity.ok(ApiResponse.success("Status updated", LitigationMatterResponse.of(matter)));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAnyAuthority('LEGALCOMPLIANCE_MANAGE','LEGALCOMPLIANCE_ADMIN')")
    @Operation(summary = "Close a matter — requires a terminal status (SETTLED, WITHDRAWN, or CLOSED)")
    public ResponseEntity<ApiResponse<LitigationMatterResponse>> close(
            @PathVariable UUID id, @Valid @RequestBody CloseLitigationMatterRequest req) {
        featureGuard.requireModule("legalcompliance");
        LitigationMatter matter = matterService.close(
                TenantContext.getTenantIdAsObject(), id, req.finalStatus(), req.outcomeNotes());
        return ResponseEntity.ok(ApiResponse.success("Matter closed", LitigationMatterResponse.of(matter)));
    }

    @PostMapping("/{id}/link-contract")
    @PreAuthorize("hasAnyAuthority('LEGALCOMPLIANCE_MANAGE','LEGALCOMPLIANCE_ADMIN')")
    public ResponseEntity<ApiResponse<LitigationMatterResponse>> linkContract(
            @PathVariable UUID id, @Valid @RequestBody LinkContractRequest req) {
        featureGuard.requireModule("legalcompliance");
        LitigationMatter matter = matterService.linkContract(
                TenantContext.getTenantIdAsObject(), id, req.contractId());
        return ResponseEntity.ok(ApiResponse.success("Contract linked", LitigationMatterResponse.of(matter)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('LEGALCOMPLIANCE_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        featureGuard.requireModule("legalcompliance");
        matterService.delete(TenantContext.getTenantIdAsObject(), id, TenantContext.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success("Matter deleted", null));
    }

    // ── Evidence (court filings, correspondence, etc.) ──────────────────────

    @PostMapping(value = "/{id}/evidence", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('LEGALCOMPLIANCE_MANAGE','LEGALCOMPLIANCE_ADMIN')")
    @Operation(summary = "Attach a document (filing, correspondence, ...) to this matter")
    public ResponseEntity<ApiResponse<EvidenceResponse>> attachEvidence(
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file,
            @RequestParam String evidenceType) {
        featureGuard.requireModule("legalcompliance");
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        matterService.get(tenantId, id); // 404s if the matter doesn't exist or isn't this tenant's
        EvidenceResponse evidence = evidenceFacade.attach(tenantId, file, evidenceType, "legalcompliance",
                EVIDENCE_ENTITY_TYPE, id, null, TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Evidence attached", evidence));
    }

    @GetMapping("/{id}/evidence")
    @PreAuthorize("hasAnyAuthority('LEGALCOMPLIANCE_READ','LEGALCOMPLIANCE_MANAGE','LEGALCOMPLIANCE_ADMIN')")
    public ResponseEntity<ApiResponse<List<EvidenceResponse>>> listEvidence(@PathVariable UUID id) {
        featureGuard.requireModule("legalcompliance");
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        matterService.get(tenantId, id);
        return ResponseEntity.ok(ApiResponse.success(
                evidenceFacade.listFor(tenantId, "legalcompliance", EVIDENCE_ENTITY_TYPE, id)));
    }

    @GetMapping("/export/pdf")
    @PreAuthorize("hasAnyAuthority('LEGALCOMPLIANCE_READ','LEGALCOMPLIANCE_MANAGE','LEGALCOMPLIANCE_ADMIN')")
    @Operation(summary = "Export the full litigation register as a PDF")
    public ResponseEntity<byte[]> exportPdf() {
        featureGuard.requireModule("legalcompliance");
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        byte[] pdf = pdfService.generateLitigationRegister(null, matterService.listAll(tenantId));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"litigation-register.pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}
