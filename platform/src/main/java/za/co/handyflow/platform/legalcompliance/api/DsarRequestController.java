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
import za.co.handyflow.platform.legalcompliance.application.internal.DsarRequestService;
import za.co.handyflow.platform.legalcompliance.application.internal.LegalCompliancePdfService;
import za.co.handyflow.platform.legalcompliance.domain.model.DsarRequest;
import za.co.handyflow.platform.legalcompliance.domain.model.DsarStatus;
import za.co.handyflow.platform.legalcompliance.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/**
 * Org-wide DSAR register — see DsarRequest's own class Javadoc for why this
 * is deliberately not linked to crm.Customer. Evidence endpoints follow the
 * same convenience-wrapper pattern as LitigationMatterController, for
 * attaching the request itself (e.g. a scanned ID + request letter) and any
 * response correspondence.
 */
@RestController
@RequestMapping("/api/v1/legalcompliance/dsar-requests")
@RequiredArgsConstructor
@Tag(name = "Legal/Compliance - DSAR Requests", description = "Org-wide POPIA data subject access request register")
public class DsarRequestController {

    private static final String EVIDENCE_ENTITY_TYPE = "DsarRequest";

    private final DsarRequestService dsarService;
    private final EvidenceFacade evidenceFacade;
    private final LegalCompliancePdfService pdfService;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('LEGALCOMPLIANCE_READ','LEGALCOMPLIANCE_MANAGE','LEGALCOMPLIANCE_ADMIN')")
    public ResponseEntity<ApiResponse<Page<DsarRequestResponse>>> list(
            @RequestParam(required = false) DsarStatus status,
            @PageableDefault(size = 20, sort = "dueDate") Pageable pageable) {
        featureGuard.requireModule("legalcompliance");
        Page<DsarRequestResponse> page = dsarService
                .list(TenantContext.getTenantIdAsObject(), status, pageable)
                .map(DsarRequestResponse::of);
        return ResponseEntity.ok(ApiResponse.success(page));
    }

    @GetMapping("/open")
    @PreAuthorize("hasAnyAuthority('LEGALCOMPLIANCE_READ','LEGALCOMPLIANCE_MANAGE','LEGALCOMPLIANCE_ADMIN')")
    public ResponseEntity<ApiResponse<List<DsarRequestResponse>>> listOpen() {
        featureGuard.requireModule("legalcompliance");
        List<DsarRequestResponse> result = dsarService.listOpen(TenantContext.getTenantIdAsObject()).stream()
                .map(DsarRequestResponse::of).toList();
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('LEGALCOMPLIANCE_READ','LEGALCOMPLIANCE_MANAGE','LEGALCOMPLIANCE_ADMIN')")
    public ResponseEntity<ApiResponse<DsarRequestResponse>> get(@PathVariable UUID id) {
        featureGuard.requireModule("legalcompliance");
        DsarRequest request = dsarService.get(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success(DsarRequestResponse.of(request)));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('LEGALCOMPLIANCE_MANAGE','LEGALCOMPLIANCE_ADMIN')")
    @Operation(summary = "Log a new DSAR — due date defaults to received date + 30 days")
    public ResponseEntity<ApiResponse<DsarRequestResponse>> create(@Valid @RequestBody CreateDsarRequestRequest req) {
        featureGuard.requireModule("legalcompliance");
        DsarRequest request = dsarService.create(
                TenantContext.getTenantIdAsObject(), req.requestType(), req.dataCategory(), req.requesterName(),
                req.requesterEmail(), req.requesterContact(), req.receivedDate(), TenantContext.getCurrentUserId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("DSAR request logged", DsarRequestResponse.of(request)));
    }

    @PostMapping("/{id}/assign")
    @PreAuthorize("hasAnyAuthority('LEGALCOMPLIANCE_MANAGE','LEGALCOMPLIANCE_ADMIN')")
    public ResponseEntity<ApiResponse<DsarRequestResponse>> assign(
            @PathVariable UUID id, @Valid @RequestBody AssignDsarRequestRequest req) {
        featureGuard.requireModule("legalcompliance");
        DsarRequest request = dsarService.assign(TenantContext.getTenantIdAsObject(), id, req.userId(), req.userName());
        return ResponseEntity.ok(ApiResponse.success("Assigned", DsarRequestResponse.of(request)));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAnyAuthority('LEGALCOMPLIANCE_MANAGE','LEGALCOMPLIANCE_ADMIN')")
    public ResponseEntity<ApiResponse<DsarRequestResponse>> complete(
            @PathVariable UUID id, @RequestBody(required = false) ResolveDsarRequestRequest req) {
        featureGuard.requireModule("legalcompliance");
        String notes = req != null ? req.resolutionNotes() : null;
        DsarRequest request = dsarService.complete(TenantContext.getTenantIdAsObject(), id, notes);
        return ResponseEntity.ok(ApiResponse.success("Marked completed", DsarRequestResponse.of(request)));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyAuthority('LEGALCOMPLIANCE_MANAGE','LEGALCOMPLIANCE_ADMIN')")
    public ResponseEntity<ApiResponse<DsarRequestResponse>> reject(
            @PathVariable UUID id, @RequestBody(required = false) ResolveDsarRequestRequest req) {
        featureGuard.requireModule("legalcompliance");
        String notes = req != null ? req.resolutionNotes() : null;
        DsarRequest request = dsarService.reject(TenantContext.getTenantIdAsObject(), id, notes);
        return ResponseEntity.ok(ApiResponse.success("Marked rejected", DsarRequestResponse.of(request)));
    }

    @PostMapping("/{id}/withdraw")
    @PreAuthorize("hasAnyAuthority('LEGALCOMPLIANCE_MANAGE','LEGALCOMPLIANCE_ADMIN')")
    public ResponseEntity<ApiResponse<DsarRequestResponse>> withdraw(
            @PathVariable UUID id, @RequestBody(required = false) ResolveDsarRequestRequest req) {
        featureGuard.requireModule("legalcompliance");
        String notes = req != null ? req.resolutionNotes() : null;
        DsarRequest request = dsarService.withdraw(TenantContext.getTenantIdAsObject(), id, notes);
        return ResponseEntity.ok(ApiResponse.success("Marked withdrawn", DsarRequestResponse.of(request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('LEGALCOMPLIANCE_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        featureGuard.requireModule("legalcompliance");
        dsarService.delete(TenantContext.getTenantIdAsObject(), id, TenantContext.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success("DSAR request deleted", null));
    }

    // ── Evidence (ID scans, request letters, response correspondence) ──────

    @PostMapping(value = "/{id}/evidence", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('LEGALCOMPLIANCE_MANAGE','LEGALCOMPLIANCE_ADMIN')")
    public ResponseEntity<ApiResponse<EvidenceResponse>> attachEvidence(
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file,
            @RequestParam String evidenceType) {
        featureGuard.requireModule("legalcompliance");
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        dsarService.get(tenantId, id);
        EvidenceResponse evidence = evidenceFacade.attach(tenantId, file, evidenceType, "legalcompliance",
                EVIDENCE_ENTITY_TYPE, id, null, TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Evidence attached", evidence));
    }

    @GetMapping("/{id}/evidence")
    @PreAuthorize("hasAnyAuthority('LEGALCOMPLIANCE_READ','LEGALCOMPLIANCE_MANAGE','LEGALCOMPLIANCE_ADMIN')")
    public ResponseEntity<ApiResponse<List<EvidenceResponse>>> listEvidence(@PathVariable UUID id) {
        featureGuard.requireModule("legalcompliance");
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        dsarService.get(tenantId, id);
        return ResponseEntity.ok(ApiResponse.success(
                evidenceFacade.listFor(tenantId, "legalcompliance", EVIDENCE_ENTITY_TYPE, id)));
    }

    @GetMapping("/export/pdf")
    @PreAuthorize("hasAnyAuthority('LEGALCOMPLIANCE_READ','LEGALCOMPLIANCE_MANAGE','LEGALCOMPLIANCE_ADMIN')")
    @Operation(summary = "Export the full DSAR request log as a PDF")
    public ResponseEntity<byte[]> exportPdf() {
        featureGuard.requireModule("legalcompliance");
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        byte[] pdf = pdfService.generateDsarLog(null, dsarService.listAll(tenantId));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"dsar-request-log.pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}
