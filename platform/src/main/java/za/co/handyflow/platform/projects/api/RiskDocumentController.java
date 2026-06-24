package za.co.handyflow.platform.projects.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.projects.application.internal.DocumentService;
import za.co.handyflow.platform.projects.application.internal.ProjectService;
import za.co.handyflow.platform.projects.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
@Tag(name = "Risks & Documents", description = "Risk register and document management")
public class RiskDocumentController {

    private final ProjectService  projectService;
    private final DocumentService documentService;

    // ── Risks ─────────────────────────────────────────────────────────────────

    @GetMapping("/{projectId}/risks")
    @PreAuthorize("hasAuthority('PM_READ')")
    @Operation(summary = "Risk register — ordered by risk score (probability × impact) descending")
    public ResponseEntity<ApiResponse<List<RiskResponse>>> getRisks(@PathVariable UUID projectId) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                projectService.getRisks(TenantContext.getTenantIdAsObject(), projectId)
                        .stream().map(RiskResponse::of).toList()));
    }

    @PostMapping("/{projectId}/risks")
    @PreAuthorize("hasAuthority('PM_WRITE')")
    @Operation(summary = "Log a risk — rating (GREEN/AMBER/RED) calculated automatically from probability × impact")
    public ResponseEntity<ApiResponse<RiskResponse>> createRisk(
            @PathVariable UUID projectId, @RequestBody CreateRiskRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Risk logged",
                RiskResponse.of(projectService.createRisk(
                        TenantContext.getTenantIdAsObject(), projectId, req))));
    }

    @PostMapping("/risks/{riskId}/action")
    @PreAuthorize("hasAuthority('PM_WRITE')")
    @Operation(summary = "Risk action — action: MITIGATE | CLOSE | ACCEPT")
    public ResponseEntity<ApiResponse<RiskResponse>> riskAction(
            @PathVariable UUID riskId, @RequestBody RiskActionRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Risk updated",
                RiskResponse.of(projectService.updateRiskStatus(
                        TenantContext.getTenantIdAsObject(), riskId,
                        req.action(), req.notes()))));
    }

    // ── Documents ─────────────────────────────────────────────────────────────

    @GetMapping("/{projectId}/documents")
    @PreAuthorize("hasAuthority('PM_READ')")
    @Operation(summary = "Document register — optionally filter by type (DRAWING/RFI/SUBMITTAL/CONTRACT/REPORT/PHOTO/GENERAL)")
    public ResponseEntity<ApiResponse<List<DocumentResponse>>> getDocuments(
            @PathVariable UUID projectId,
            @RequestParam(required = false) String type) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                documentService.getDocuments(TenantContext.getTenantIdAsObject(), projectId, type)
                        .stream().map(DocumentResponse::of).toList()));
    }

    @PostMapping("/{projectId}/documents")
    @PreAuthorize("hasAuthority('PM_WRITE')")
    @Operation(summary = "Upload a document — supersedes previous CURRENT revision of same title automatically")
    public ResponseEntity<ApiResponse<DocumentResponse>> uploadDocument(
            @PathVariable UUID projectId, @RequestBody UploadDocumentRequest req) {
        UUID userId = TenantContext.getCurrentUserId();
        String name = userId != null ? userId.toString() : "unknown";
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Document uploaded",
                DocumentResponse.of(documentService.uploadDocument(
                        TenantContext.getTenantIdAsObject(), projectId, req, userId, name))));
    }

    @PostMapping("/documents/{docId}/{action}")
    @PreAuthorize("hasAuthority('PM_WRITE')")
    @Operation(summary = "Document workflow — action: APPROVE | SUBMIT_REVIEW | SUPERSEDE")
    public ResponseEntity<ApiResponse<DocumentResponse>> updateDocStatus(
            @PathVariable UUID docId, @PathVariable String action) {
        return ResponseEntity.ok(ApiResponse.success("Document updated",
                DocumentResponse.of(documentService.updateDocumentStatus(
                        TenantContext.getTenantIdAsObject(), docId, action))));
    }
}
