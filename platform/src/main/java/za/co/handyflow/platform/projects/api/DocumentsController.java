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
import java.util.UUID;

/**
 * Controlled document register.
 *
 * CHUNK 6 — what changed for callers:
 *  - Documents have a documentNumber. To add a REVISION of an existing document, send its
 *    documentNumber (and a revision label). Without a number a NEW document is created. Callers
 *    that relied on "same type + new revision supersedes the old one" must now send the number.
 *  - POST /documents/{id}/{action} now accepts SUBMIT_REVIEW, APPROVE, REJECT and VOID (with an
 *    optional/required comment body). SUPERSEDE is automatic and refused here. APPROVE and REJECT
 *    need PM_APPROVE (checked in the service); the rest need PM_WRITE.
 *  - DELETE no longer removes anything: it VOIDS an unreviewed document (reason via ?reason=).
 *    Approved / rejected / superseded documents cannot be removed.
 *  - The register hides VOIDED documents; pass liveOnly=true to see only what is in force.
 */
@Validated
@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
@Tag(name = "Documents", description = "Controlled document register — revisions, review, visibility")
public class DocumentsController {

    private final DocumentService documentService;

    @GetMapping("/{projectId}/documents")
    @PreAuthorize("hasAuthority('PM_READ')")
    @Operation(summary = "Document register (voided hidden). Optional type filter; liveOnly=true shows only CURRENT / FOR_REVIEW / APPROVED.")
    public ResponseEntity<ApiResponse<List<DocumentResponse>>> list(
            @PathVariable UUID projectId,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "false") boolean liveOnly) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                documentService.getDocuments(TenantContext.getTenantIdAsObject(), projectId, type, liveOnly)
                        .stream().map(DocumentResponse::of).toList()));
    }

    @GetMapping("/{projectId}/documents/revisions")
    @PreAuthorize("hasAuthority('PM_READ')")
    @Operation(summary = "Every revision of one document (by number), newest first — including superseded and voided")
    public ResponseEntity<ApiResponse<List<DocumentResponse>>> revisions(
            @PathVariable UUID projectId, @RequestParam String number) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                documentService.getRevisions(TenantContext.getTenantIdAsObject(), projectId, number)
                        .stream().map(DocumentResponse::of).toList()));
    }

    @PostMapping("/{projectId}/documents")
    @PreAuthorize("hasAuthority('PM_WRITE')")
    @Operation(summary = "Register a document or a new revision of one (see CreateDocumentRequest for the numbering rules)")
    public ResponseEntity<ApiResponse<DocumentResponse>> upload(
            @PathVariable UUID projectId, @Valid @RequestBody CreateDocumentRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Document registered",
                DocumentResponse.of(documentService.uploadDocument(
                        TenantContext.getTenantIdAsObject(), projectId, req,
                        TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName()))));
    }

    @PostMapping("/documents/{docId}/{action}")
    @PreAuthorize("hasAuthority('PM_WRITE')")
    @Operation(summary = "Workflow action: SUBMIT_REVIEW | APPROVE | REJECT | VOID. REJECT and VOID need a comment; APPROVE / REJECT need PM_APPROVE.")
    public ResponseEntity<ApiResponse<DocumentResponse>> act(
            @PathVariable UUID docId, @PathVariable String action,
            @RequestBody(required = false) @Valid DocumentActionRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Document updated",
                DocumentResponse.of(documentService.performAction(
                        TenantContext.getTenantIdAsObject(), docId, action,
                        req != null ? req.comment() : null,
                        TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName()))));
    }

    @PutMapping("/documents/{docId}/visibility")
    @PreAuthorize("hasAuthority('PM_WRITE')")
    @Operation(summary = "Mark a document INTERNAL or CLIENT-visible (the client portal lists only CLIENT + APPROVED)")
    public ResponseEntity<ApiResponse<DocumentResponse>> visibility(
            @PathVariable UUID docId, @Valid @RequestBody DocumentVisibilityRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Visibility updated",
                DocumentResponse.of(documentService.setVisibility(
                        TenantContext.getTenantIdAsObject(), docId, req.visibility()))));
    }

    @GetMapping("/documents/{docId}/history")
    @PreAuthorize("hasAuthority('PM_READ')")
    @Operation(summary = "Lifecycle trail of one revision: uploaded, submitted, approved, rejected, superseded, voided")
    public ResponseEntity<ApiResponse<List<DocumentReviewResponse>>> history(@PathVariable UUID docId) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                documentService.getHistory(TenantContext.getTenantIdAsObject(), docId)
                        .stream().map(DocumentReviewResponse::of).toList()));
    }

    @DeleteMapping("/documents/{docId}")
    @PreAuthorize("hasAuthority('PM_ADMIN')")
    @Operation(summary = "VOIDS an unreviewed document (kept in the record, hidden from the register). Reviewed documents cannot be removed.")
    public ResponseEntity<ApiResponse<DocumentResponse>> voidDocument(
            @PathVariable UUID docId,
            @RequestParam(defaultValue = "Removed from register") String reason) {
        return ResponseEntity.ok(ApiResponse.success("Document voided",
                DocumentResponse.of(documentService.performAction(
                        TenantContext.getTenantIdAsObject(), docId, "VOID", reason,
                        TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName()))));
    }
}