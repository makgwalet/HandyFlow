package za.co.handyflow.platform.projects.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.projects.application.internal.DocumentService;
import za.co.handyflow.platform.projects.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Project document register — drawings, RFIs, contracts, reports.
 *
 * CHANGES FROM ORIGINAL (was part of RiskDocumentController):
 *
 * 1. @Validated + @Valid on all request bodies.
 *
 * 2. uploadedByName was userId.toString() — a UUID.
 *    FIX: TenantContext.getCurrentUserName() provides the real display name.
 *    The name is stored permanently in uploaded_by_name for audit purposes;
 *    it must be a human-readable name, not a UUID.
 *
 * 3. The document type filter is now applied in the service layer via the
 *    repository, not in application code via stream().filter() — which loaded
 *    all documents for the project and filtered in JVM memory.
 */
@Validated
@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
@Tag(name = "Documents", description = "Document register — drawings, RFIs, submittals, contracts")
public class DocumentsController {

    private final DocumentService documentService;

    @GetMapping("/{projectId}/documents")
    @PreAuthorize("hasAuthority('PM_READ')")
    @Operation(summary = "Document register — optionally filter by type (DRAWING, RFI, etc.)")
    public ResponseEntity<ApiResponse<List<DocumentResponse>>> getDocuments(
            @PathVariable UUID projectId,
            @RequestParam(required = false) String type) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                documentService.getDocuments(TenantContext.getTenantIdAsObject(), projectId, type)
                        .stream().map(DocumentResponse::of).toList()));
    }

    /**
     * Uploads a document and supersedes any previous CURRENT revision of the
     * same type when a revision number is provided.
     *
     * FIX: uploadedByName now resolved from TenantContext rather than being
     * set to userId.toString().
     *
     * NOTE on file storage: this endpoint accepts a pre-signed URL or storage
     * path.  Actual file upload is handled by a separate file-storage service
     * (e.g. S3 pre-signed URL flow) — the document register stores the reference.
     */
    @PostMapping("/{projectId}/documents")
    @PreAuthorize("hasAuthority('PM_WRITE')")
    @Operation(summary = "Register a document — supersedes previous revision if revision != null")
    public ResponseEntity<ApiResponse<DocumentResponse>> uploadDocument(
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateDocumentRequest req) {
        UUID   uploadedBy   = TenantContext.getCurrentUserId();
        String uploaderName = TenantContext.getCurrentUserName();  // FIX: real name not UUID
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Document uploaded",
                        DocumentResponse.of(documentService.uploadDocument(
                                TenantContext.getTenantIdAsObject(), projectId,
                                req, uploadedBy, uploaderName))));
    }

    /**
     * Document status transitions: APPROVE | SUBMIT_REVIEW | SUPERSEDE.
     *
     * Typical workflow:
     *   Uploaded (CURRENT) → submitForReview → FOR_REVIEW → approve → APPROVED
     *   Previous revision → SUPERSEDED when a new revision is uploaded
     */
    @PostMapping("/documents/{docId}/{action}")
    @PreAuthorize("hasAuthority('PM_WRITE')")
    @Operation(summary = "Status transition: APPROVE | SUBMIT_REVIEW | SUPERSEDE")
    public ResponseEntity<ApiResponse<DocumentResponse>> updateStatus(
            @PathVariable UUID   docId,
            @PathVariable String action) {
        return ResponseEntity.ok(ApiResponse.success("Document updated",
                DocumentResponse.of(documentService.updateStatus(
                        TenantContext.getTenantIdAsObject(), docId, action))));
    }

    @DeleteMapping("/documents/{docId}")
    @PreAuthorize("hasAuthority('PM_ADMIN')")
    @Operation(summary = "Delete a document from the register")
    public ResponseEntity<ApiResponse<Void>> deleteDocument(@PathVariable UUID docId) {
        documentService.deleteDocument(TenantContext.getTenantIdAsObject(), docId);
        return ResponseEntity.ok(ApiResponse.success("Document deleted", null));
    }
}
