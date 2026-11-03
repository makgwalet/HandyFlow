package za.co.handyflow.platform.projects.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.projects.application.internal.document.DocumentRules;
import za.co.handyflow.platform.projects.domain.enums.DocumentStatus;
import za.co.handyflow.platform.projects.domain.enums.ProjectStatus;
import za.co.handyflow.platform.projects.domain.model.Project;
import za.co.handyflow.platform.projects.domain.model.ProjectDocument;
import za.co.handyflow.platform.projects.domain.model.ProjectDocumentReview;
import za.co.handyflow.platform.projects.domain.repository.ProjectDocumentRepository;
import za.co.handyflow.platform.projects.domain.repository.ProjectDocumentReviewRepository;
import za.co.handyflow.platform.projects.domain.repository.ProjectRepository;
import za.co.handyflow.platform.projects.dto.CreateDocumentRequest;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/**
 * Controlled document register.
 *
 * WHAT WAS WRONG BEFORE
 *  - A new revision superseded EVERY current document of the same TYPE (the repository query had
 *    no document identity to filter on). Documents now have a number; revisions are found by it.
 *  - Any status could be set from any other, and approve() worked on a never-reviewed document.
 *    All changes now go through DocumentStatus.
 *  - Deleting removed a document outright, including approved ones. Reviewed documents are now
 *    permanent; unreviewed ones are VOIDED (kept, marked, with a reason).
 *
 * REVISION RULES
 *  - Uploading a revision supersedes earlier UNREVIEWED revisions of that document immediately.
 *  - An APPROVED revision stays in force until the new one is itself APPROVED, at which point it is
 *    superseded — keeping its approver, date and a pointer to its replacement.
 *  - Uploading to an existing number needs a revision label, the same document type, and a label
 *    not already used in that series.
 *
 * REVIEW RULES
 *  - SUBMIT_REVIEW needs CURRENT/DRAFT. APPROVE / REJECT need FOR_REVIEW and the PM_APPROVE
 *    authority (checked here, not just on the endpoint). REJECT and VOID need a comment.
 *  - app.projects.documents.allow-self-approval=false stops the uploader approving their own file
 *    (default true, as before).
 *  - Every step is written to project_document_reviews.
 *
 * CLOSED projects accept no document changes. Documents may still be added after COMPLETION
 * (as-builts, handover packs) and on CANCELLED projects (records still matter).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private final ProjectDocumentRepository       docRepo;
    private final ProjectDocumentReviewRepository reviewRepo;
    private final ProjectRepository               projectRepo;
    private final SequenceService                 sequenceService;

    @Value("${app.projects.documents.allow-self-approval:true}")
    private boolean allowSelfApproval;

    // ── Queries ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ProjectDocument> getDocuments(TenantId tenantId, UUID projectId, String type, boolean liveOnly) {
        verifyProject(tenantId, projectId);
        boolean hasType = type != null && !type.isBlank();
        String t = hasType ? type.toUpperCase() : null;
        if (liveOnly) return hasType ? docRepo.findLiveByType(projectId, t) : docRepo.findLive(projectId);
        return hasType ? docRepo.findRegisterByType(projectId, t) : docRepo.findRegister(projectId);
    }

    /** Every revision of one document, newest first, including superseded and voided ones. */
    @Transactional(readOnly = true)
    public List<ProjectDocument> getRevisions(TenantId tenantId, UUID projectId, String documentNumber) {
        verifyProject(tenantId, projectId);
        String number = DocumentRules.normalizeNumber(documentNumber);
        if (number == null) throw bad("A document number is required", "NUMBER_REQUIRED");
        return docRepo.findSeries(projectId, number);
    }

    /** For the client portal: takes the already-resolved Project, like FieldService's portal variant. */
    @Transactional(readOnly = true)
    public List<ProjectDocument> getClientVisibleForPortal(Project resolvedProject) {
        return docRepo.findClientVisible(resolvedProject.getId());
    }

    @Transactional(readOnly = true)
    public List<ProjectDocumentReview> getHistory(TenantId tenantId, UUID docId) {
        ProjectDocument doc = find(tenantId, docId);
        return reviewRepo.findByDocument(tenantId.getValue(), doc.getId());
    }

    // ── Upload ────────────────────────────────────────────────────────────────

    @Transactional
    public ProjectDocument uploadDocument(TenantId tenantId, UUID projectId, CreateDocumentRequest req,
                                          UUID uploadedBy, String uploadedByName) {
        Project project = verifyProject(tenantId, projectId);
        requireNotClosed(project);

        if (DocumentRules.hasBlockedExtension(req.fileName()) || DocumentRules.hasBlockedExtension(req.fileUrl())) {
            throw bad("Executable and script files cannot be registered as project documents", "FILE_TYPE_NOT_ALLOWED");
        }

        String type = req.documentType() != null ? req.documentType().toUpperCase() : "GENERAL";
        String revision = blankToNull(req.revision());
        String number;
        try {
            number = DocumentRules.normalizeNumber(req.documentNumber());
        } catch (IllegalArgumentException e) {
            throw bad(e.getMessage(), "INVALID_NUMBER");
        }

        List<ProjectDocument> series = List.of();
        if (number == null) {
            number = DocumentRules.autoNumber(type,
                    sequenceService.next(tenantId.getValue(), "DOC:" + projectId + ":" + type));
        } else {
            series = docRepo.findSeries(projectId, number);
            if (!series.isEmpty()) {
                if (revision == null) {
                    throw bad("A revision label is required when adding to existing document " + number, "REVISION_REQUIRED");
                }
                if (!series.get(0).getDocumentType().equals(type)) {
                    throw new HandyFlowException("Document " + number + " is a " + series.get(0).getDocumentType()
                            + " — it cannot take a " + type + " revision", HttpStatus.CONFLICT, "TYPE_MISMATCH");
                }
                final String label = revision;
                if (series.stream().anyMatch(d -> d.statusEnum() != DocumentStatus.VOIDED
                        && label.equalsIgnoreCase(d.getRevision()))) {
                    throw new HandyFlowException("Revision '" + label + "' already exists for document " + number,
                            HttpStatus.CONFLICT, "DUPLICATE_REVISION");
                }
            }
        }

        ProjectDocument doc = ProjectDocument.create(tenantId.getValue(), projectId, number, type,
                req.title(), revision, req.fileUrl(), req.fileName(), req.fileSizeKb(),
                req.description(), req.visibility(), uploadedBy, uploadedByName);
        docRepo.save(doc);
        record(doc, "UPLOADED", revision != null ? "Revision " + revision : null, uploadedBy, uploadedByName);

        // Earlier UNREVIEWED revisions are replaced now; an APPROVED one waits for this one's approval.
        for (ProjectDocument prev : series) {
            if (prev.statusEnum().supersededOnUpload()) {
                prev.supersede(doc.getId());
                docRepo.save(prev);
                record(prev, "SUPERSEDED", "Replaced by revision " + (revision != null ? revision : doc.getId()),
                        uploadedBy, uploadedByName);
            }
        }
        log.info("Registered document={} number={} rev='{}' type={} project={}",
                doc.getId(), number, revision, type, projectId);
        return doc;
    }

    // ── Workflow ──────────────────────────────────────────────────────────────

    @Transactional
    public ProjectDocument performAction(TenantId tenantId, UUID docId, String action, String comment,
                                         UUID userId, String userName) {
        ProjectDocument doc = find(tenantId, docId);
        requireNotClosed(projectRepo.findByTenantAndId(tenantId.getValue(), doc.getProjectId())
                .orElseThrow(() -> notFound("Project")));
        String note = blankToNull(comment);

        switch (action == null ? "" : action.toUpperCase()) {
            case "SUBMIT_REVIEW" -> {
                doc.submitForReview();
                record(doc, "SUBMITTED", note, userId, userName);
            }
            case "APPROVE" -> {
                requireAuthority("PM_APPROVE");
                if (!allowSelfApproval && userId != null && userId.equals(doc.getUploadedBy())) {
                    throw new HandyFlowException("You cannot approve a document you uploaded",
                            HttpStatus.FORBIDDEN, "SELF_APPROVAL_NOT_ALLOWED");
                }
                doc.approve(userId, userName);
                record(doc, "APPROVED", note, userId, userName);
                // The previously approved revision of this document is replaced now.
                for (ProjectDocument prev : docRepo.findSeries(doc.getProjectId(), doc.getDocumentNumber())) {
                    if (!prev.getId().equals(doc.getId()) && prev.statusEnum() == DocumentStatus.APPROVED) {
                        prev.supersede(doc.getId());
                        docRepo.save(prev);
                        record(prev, "SUPERSEDED", "Replaced by approved revision " + doc.getRevision(), userId, userName);
                    }
                }
            }
            case "REJECT" -> {
                requireAuthority("PM_APPROVE");
                if (note == null) throw bad("A comment is required to reject a document", "COMMENT_REQUIRED");
                doc.reject();
                record(doc, "REJECTED", note, userId, userName);
            }
            case "VOID" -> {
                if (note == null) throw bad("A reason is required to void a document", "REASON_REQUIRED");
                doc.voidDocument(userId, note);
                record(doc, "VOIDED", note, userId, userName);
            }
            case "SUPERSEDE" -> throw bad(
                    "Documents are superseded automatically when a newer revision is uploaded or approved", "AUTOMATIC_ONLY");
            default -> throw bad("Unknown document action: " + action, "BAD_REQUEST");
        }
        ProjectDocument saved = docRepo.save(doc);
        log.info("Document={} action={} by user={} → {}", docId, action, userId, saved.getStatus());
        return saved;
    }

    @Transactional
    public ProjectDocument setVisibility(TenantId tenantId, UUID docId, String visibility) {
        ProjectDocument doc = find(tenantId, docId);
        doc.changeVisibility(visibility);
        return docRepo.save(doc);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void record(ProjectDocument doc, String action, String comment, UUID by, String byName) {
        reviewRepo.save(ProjectDocumentReview.record(doc.getTenantId(), doc.getProjectId(), doc.getId(),
                action, comment, by, byName));
    }

    private ProjectDocument find(TenantId tenantId, UUID id) {
        return docRepo.findByTenantAndId(tenantId.getValue(), id).orElseThrow(() -> notFound("Document"));
    }

    private Project verifyProject(TenantId tenantId, UUID projectId) {
        return projectRepo.findByTenantAndId(tenantId.getValue(), projectId).orElseThrow(() -> notFound("Project"));
    }

    private static void requireNotClosed(Project p) {
        if (p.getStatus() == ProjectStatus.CLOSED) {
            throw new HandyFlowException("Project is CLOSED — its documents can no longer be changed",
                    HttpStatus.CONFLICT, "PROJECT_CLOSED");
        }
    }

    private static void requireAuthority(String authority) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        boolean ok = auth != null && auth.getAuthorities().stream().anyMatch(a -> authority.equals(a.getAuthority()));
        if (!ok) throw new HandyFlowException("You need the " + authority + " permission for this action",
                HttpStatus.FORBIDDEN, "FORBIDDEN");
    }

    private static HandyFlowException bad(String message, String code) {
        return new HandyFlowException(message, HttpStatus.BAD_REQUEST, code);
    }

    private static HandyFlowException notFound(String entity) {
        return new HandyFlowException(entity + " not found", HttpStatus.NOT_FOUND, "NOT_FOUND");
    }

    private static String blankToNull(String s) { return (s == null || s.isBlank()) ? null : s.trim(); }
}