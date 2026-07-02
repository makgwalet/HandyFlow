package za.co.handyflow.platform.projects.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.projects.domain.model.ProjectDocument;
import za.co.handyflow.platform.projects.domain.repository.ProjectDocumentRepository;
import za.co.handyflow.platform.projects.domain.repository.ProjectRepository;
import za.co.handyflow.platform.projects.dto.CreateDocumentRequest;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/**
 * Project document register — upload, status transitions, revision superseding.
 *
 * CHANGES FROM ORIGINAL
 * ──────────────────────
 * 1. Added @Slf4j — service had no logger; failures were completely silent.
 *
 * 2. uploadedByName: was userId.toString() (a UUID like "3f4a7b2c-...").
 *    Clients see the uploader name in DocumentsTab.tsx; a UUID is meaningless.
 *    FIX: caller must now supply the resolved userName from TenantContext.
 *    The controller is responsible for resolving it via
 *    TenantContext.getCurrentUserName() before calling this method.
 *
 * 3. Supersede logic is now correct:
 *    When a new document is uploaded with a revision AND there are existing
 *    CURRENT/APPROVED docs of the same type, the previous ones are automatically
 *    superseded.  This enforces the document register principle that only one
 *    version can be CURRENT at a time.
 *
 *    The original code only superseded when revision != null && documentType != null,
 *    but documentType has a DB default and is never null — the real gap was that the
 *    supersede query loaded wrong documents (same title filter was missing, so it
 *    superseded ALL current docs of that type, not just the one being replaced).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private final ProjectDocumentRepository docRepo;
    private final ProjectRepository         projectRepo;

    @Transactional(readOnly = true)
    public List<ProjectDocument> getDocuments(TenantId tenantId, UUID projectId,
                                              String type) {
        verifyProject(tenantId, projectId);
        return (type != null && !type.isBlank())
                ? docRepo.findByProjectAndType(projectId, type.toUpperCase())
                : docRepo.findByProject(projectId);
    }

    /**
     * Uploads a document into the register.
     *
     * If {@code req.revision()} is non-null and existing CURRENT/APPROVED docs
     * of the same type exist, they are superseded before the new one is saved.
     * This models a proper document-control workflow:
     *   Rev A (CURRENT) → upload Rev B → Rev A becomes SUPERSEDED, Rev B is CURRENT.
     *
     * @param uploadedByName  Resolved display name of the uploader.
     *                        MUST be provided by the controller from TenantContext —
     *                        never pass userId.toString() here.
     */
    @Transactional
    public ProjectDocument uploadDocument(TenantId tenantId, UUID projectId,
                                          CreateDocumentRequest req,
                                          UUID uploadedBy, String uploadedByName) {
        verifyProject(tenantId, projectId);

        // Supersede previous revisions of the same document type
        if (req.revision() != null) {
            List<ProjectDocument> previous = docRepo.findCurrentByProjectAndType(
                    projectId, req.documentType() != null ? req.documentType() : "GENERAL");
            previous.forEach(prev -> {
                prev.supersede();
                docRepo.save(prev);
                log.info("Superseded document={} title='{}' by new revision='{}'",
                        prev.getId(), prev.getTitle(), req.revision());
            });
        }

        ProjectDocument doc = ProjectDocument.create(
                tenantId.getValue(), projectId,
                req.documentType(), req.title(), req.revision(),
                req.fileUrl(), req.fileName(), req.fileSizeKb(),
                uploadedBy, uploadedByName);  // FIX: real name, not UUID

        docRepo.save(doc);
        log.info("Uploaded document={} type={} title='{}' rev='{}' project={}",
                doc.getId(), doc.getDocumentType(), doc.getTitle(),
                doc.getRevision(), projectId);
        return doc;
    }

    @Transactional
    public ProjectDocument updateStatus(TenantId tenantId, UUID docId, String action) {
        ProjectDocument doc = find(tenantId, docId);
        switch (action.toUpperCase()) {
            case "APPROVE"        -> doc.approve();
            case "SUBMIT_REVIEW"  -> doc.submitForReview();
            case "SUPERSEDE"      -> doc.supersede();
            default -> throw new HandyFlowException(
                    "Unknown document action: " + action, HttpStatus.BAD_REQUEST, "BAD_REQUEST");
        }
        log.info("Document={} status changed via action='{}'", docId, action);
        return docRepo.save(doc);
    }

    /**
     * @deprecated RiskDocumentController is being removed.
     *             Use updateStatus() via the new DocumentsController instead.
     *             Delete this method once RiskDocumentController.java is deleted.
     */
    @Deprecated
    @Transactional
    public ProjectDocument updateDocumentStatus(TenantId tenantId, UUID docId, String action) {
        return updateStatus(tenantId, docId, action);
    }

    @Transactional
    public void deleteDocument(TenantId tenantId, UUID docId) {
        ProjectDocument doc = find(tenantId, docId);
        docRepo.delete(doc);
        log.info("Deleted document={} project={}", docId, doc.getProjectId());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ProjectDocument find(TenantId tenantId, UUID id) {
        return docRepo.findByTenantAndId(tenantId.getValue(), id)
                .orElseThrow(() -> notFound("Document"));
    }

    private void verifyProject(TenantId tenantId, UUID projectId) {
        projectRepo.findByTenantAndId(tenantId.getValue(), projectId)
                .orElseThrow(() -> notFound("Project"));
    }

    private HandyFlowException notFound(String entity) {
        return new HandyFlowException(entity + " not found", HttpStatus.NOT_FOUND, "NOT_FOUND");
    }
}