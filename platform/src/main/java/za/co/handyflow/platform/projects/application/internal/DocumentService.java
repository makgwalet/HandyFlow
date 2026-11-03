package za.co.handyflow.platform.projects.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import za.co.handyflow.platform.documents.application.DocumentScanner;
import za.co.handyflow.platform.documents.application.RetentionFacade;
import za.co.handyflow.platform.evidence.application.EvidenceFacade;
import za.co.handyflow.platform.evidence.dto.EvidenceResponse;
import za.co.handyflow.platform.projects.application.internal.document.DocumentAccessRules;
import za.co.handyflow.platform.projects.application.internal.document.DocumentRules;
import za.co.handyflow.platform.projects.domain.enums.DocumentLinkType;
import za.co.handyflow.platform.projects.domain.enums.DocumentStatus;
import za.co.handyflow.platform.projects.domain.enums.ProjectStatus;
import za.co.handyflow.platform.projects.domain.model.Project;
import za.co.handyflow.platform.projects.domain.model.ProjectDocument;
import za.co.handyflow.platform.projects.domain.model.ProjectDocumentLink;
import za.co.handyflow.platform.projects.domain.model.ProjectDocumentReview;
import za.co.handyflow.platform.projects.domain.repository.ProjectDocumentLinkRepository;
import za.co.handyflow.platform.projects.domain.repository.ProjectDocumentRepository;
import za.co.handyflow.platform.projects.domain.repository.ProjectDocumentReviewRepository;
import za.co.handyflow.platform.projects.domain.repository.ProjectMemberRepository;
import za.co.handyflow.platform.projects.domain.repository.ProjectRepository;
import za.co.handyflow.platform.projects.dto.CreateDocumentRequest;
import za.co.handyflow.platform.projects.dto.RegisterFileRequest;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
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
 * DOCUMENT CONTROL (who may do what — see DocumentAccessRules)
 *  - Void: a document controller, or the uploader for their own unreviewed document. A document
 *    under legal hold cannot be voided.
 *  - Visibility (INTERNAL / CLIENT) and legal hold: document controllers only. A document controller
 *    holds PM_APPROVE or PM_ADMIN, or is MANAGER / DOCUMENT_CONTROLLER on that project.
 *    app.projects.documents.restrict-controls=false returns void and visibility to every writer.
 *  - Approve / reject / void / visibility / legal hold lock the document row first, so two people
 *    acting on the same revision at once are serialised.
 *
 * FILES
 *  - A register entry is a LINK (fileUrl only) or a STORED FILE. Stored files go to the Evidence
 *    module (any file type, SHA-256 kept here and re-checked on every download). Before storing:
 *    executables are refused by name and by their first bytes, and the shared DocumentScanner hook
 *    runs — INFECTED is always refused; app.projects.documents.scan-required=true also refuses a
 *    file that could not be scanned. No scanner product is chosen yet, so by default scanning is
 *    SKIPPED. Every download is written to the document's trail.
 *  - Links must be http(s) or a relative storage reference (DocumentRules.isSafeUrl).
 *
 * RETENTION
 *  - Closing a project stamps every document with retention_until (closed date + retention-years).
 *    Nothing is ever deleted automatically: documents past their date are only listed for review,
 *    and a legal hold keeps a document off that list.
 *
 * CLOSED projects accept no document changes (legal hold excepted). Documents may still be added
 * after COMPLETION (as-builts, handover packs) and on CANCELLED projects (records still matter).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private static final ZoneId ZONE = ZoneId.of("Africa/Johannesburg");

    private final ProjectDocumentRepository       docRepo;
    private final ProjectDocumentReviewRepository reviewRepo;
    private final ProjectDocumentLinkRepository   linkRepo;
    private final ProjectRepository               projectRepo;
    private final ProjectMemberRepository         memberRepo;
    private final ProjectScopeResolver            scopeResolver;
    private final SequenceService                 sequenceService;
    private final EvidenceFacade                  evidenceFacade;
    private final DocumentScanner                 scanner;
    private final RetentionFacade                 retention;

    @Value("${app.projects.documents.allow-self-approval:true}")
    private boolean allowSelfApproval;

    @Value("${app.projects.documents.restrict-controls:true}")
    private boolean restrictControls;

    @Value("${app.projects.documents.retention-years:7}")
    private int retentionYears;

    /** Must not exceed spring.servlet.multipart.max-file-size, which is the real ceiling. */
    @Value("${app.projects.documents.max-file-bytes:10485760}")
    private long maxFileBytes;

    @Value("${app.projects.documents.scan-required:false}")
    private boolean scanRequired;

    /** What goes into the register for one revision, whether it is a link or a stored file. */
    public record Registration(String documentType, String documentNumber, String title, String revision,
                               String fileUrl, String fileName, Integer fileSizeKb,
                               String description, String visibility) {}

    /** The bytes of a stored file, ready to send. fileName and contentType are already safe to put in headers. */
    public record FileDownload(byte[] content, String fileName, String contentType) {}

    private record StoredUpload(MultipartFile file, String fileName, String sha256, String contentType, int sizeKb) {}

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
        String number;
        try {
            number = DocumentRules.normalizeNumber(documentNumber);
        } catch (IllegalArgumentException e) {
            throw bad(e.getMessage(), "INVALID_NUMBER");
        }
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

    /** Documents past their retention date and not on legal hold. For a person to review — nothing is deleted. */
    @Transactional(readOnly = true)
    public List<ProjectDocument> getDueForRetentionReview(TenantId tenantId, UUID projectId) {
        verifyProject(tenantId, projectId);
        return docRepo.findDueForRetentionReview(tenantId.getValue(), projectId, LocalDate.now(ZONE));
    }

    // ── Register: a link ──────────────────────────────────────────────────────

    @Transactional
    public ProjectDocument uploadDocument(TenantId tenantId, UUID projectId, CreateDocumentRequest req,
                                          UUID uploadedBy, String uploadedByName) {
        if (!DocumentRules.isSafeUrl(req.fileUrl())) {
            throw bad("That file link is not allowed. Use an http(s) address or a storage reference.", "URL_NOT_ALLOWED");
        }
        Registration reg = new Registration(req.documentType(), req.documentNumber(), req.title(), req.revision(),
                req.fileUrl(), req.fileName(), req.fileSizeKb(), req.description(), req.visibility());
        return register(tenantId, projectId, reg, null, uploadedBy, uploadedByName);
    }

    // ── Register: a stored file ───────────────────────────────────────────────

    @Transactional
    public ProjectDocument registerFile(TenantId tenantId, UUID projectId, RegisterFileRequest meta,
                                        MultipartFile file, UUID uploadedBy, String uploadedByName) {
        if (file == null || file.isEmpty()) throw bad("A file is required", "FILE_REQUIRED");
        if (file.getSize() > maxFileBytes) {
            throw new HandyFlowException(
                    "File exceeds the maximum allowed size of " + (maxFileBytes / (1024 * 1024)) + " MB",
                    HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE");
        }
        String name = DocumentRules.safeFileName(file.getOriginalFilename());
        if (DocumentRules.hasBlockedExtension(name)) {
            throw bad("Executable and script files cannot be stored as project documents", "FILE_TYPE_NOT_ALLOWED");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            log.error("Could not read uploaded project document '{}': {}", name, e.getMessage());
            throw new HandyFlowException("The file could not be read", HttpStatus.INTERNAL_SERVER_ERROR, "READ_FAILED");
        }
        if (DocumentRules.looksExecutable(bytes)) {
            throw bad("This file is a program, not a document, and cannot be stored", "FILE_TYPE_NOT_ALLOWED");
        }

        DocumentScanner.Result scan = scanner.scan(name, file.getContentType(), bytes);
        if (scan.status() == DocumentScanner.Status.INFECTED) {
            log.warn("Project document '{}' rejected by the malware scan: {}", name, scan.detail());
            throw new HandyFlowException("The file was rejected by the malware scan and was not stored",
                    HttpStatus.UNPROCESSABLE_ENTITY, "FILE_INFECTED");
        }
        if (scanRequired && scan.status() != DocumentScanner.Status.CLEAN) {
            throw new HandyFlowException("The file could not be scanned, so it was not stored. Try again shortly.",
                    HttpStatus.SERVICE_UNAVAILABLE, "SCAN_UNAVAILABLE");
        }

        String declared = file.getContentType();
        String contentType = (declared == null || declared.isBlank()) ? null
                : (declared.length() > 150 ? declared.substring(0, 150) : declared.trim());
        int sizeKb = (int) Math.max(1L, (bytes.length + 1023L) / 1024L);
        StoredUpload upload = new StoredUpload(file, name, DocumentRules.sha256(bytes), contentType, sizeKb);

        Registration reg = new Registration(meta.documentType(), meta.documentNumber(), meta.title(), meta.revision(),
                null, name, sizeKb, meta.description(), meta.visibility());
        return register(tenantId, projectId, reg, upload, uploadedBy, uploadedByName);
    }

    /** The one place a revision enters the register. All checks run BEFORE any file is stored. */
    private ProjectDocument register(TenantId tenantId, UUID projectId, Registration reg, StoredUpload upload,
                                     UUID uploadedBy, String uploadedByName) {
        Project project = verifyProject(tenantId, projectId);
        requireNotClosed(project);

        if (upload == null
                && (DocumentRules.hasBlockedExtension(reg.fileName()) || DocumentRules.hasBlockedExtension(reg.fileUrl()))) {
            throw bad("Executable and script files cannot be registered as project documents", "FILE_TYPE_NOT_ALLOWED");
        }

        String type = reg.documentType() != null ? reg.documentType().toUpperCase() : "GENERAL";
        String revision = blankToNull(reg.revision());
        String number;
        try {
            number = DocumentRules.normalizeNumber(reg.documentNumber());
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
                reg.title().trim(), revision, reg.fileUrl(), reg.fileName(), reg.fileSizeKb(),
                reg.description(), reg.visibility(), uploadedBy, uploadedByName);

        if (upload != null) {
            EvidenceResponse stored = evidenceFacade.attach(tenantId, upload.file(), "PROJECT_DOCUMENT", "projects",
                    "ProjectDocument", doc.getId(), null, uploadedBy, uploadedByName);
            doc.attachStoredFile(stored.id(), upload.sha256(), upload.contentType(), upload.fileName(), upload.sizeKb());
        }
        docRepo.save(doc);
        if (upload != null) {
            // Tracked for records retention; the clock starts when the project is closed.
            retention.track(tenantId, ProjectsRetentionProvider.MODULE, ProjectsRetentionProvider.SOURCE, doc.getId(), type,
                    ProjectsRetentionProvider.reference(doc), doc.getTitle(), project.getName());
        }
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
        log.info("Registered document={} number={} rev='{}' type={} stored={} project={}",
                doc.getId(), number, revision, type, upload != null, projectId);
        return doc;
    }

    // ── Workflow ──────────────────────────────────────────────────────────────

    @Transactional
    public ProjectDocument performAction(TenantId tenantId, UUID docId, String action, String comment,
                                         UUID userId, String userName) {
        ProjectDocument doc = findForUpdate(tenantId, docId);
        requireNotClosed(verifyProject(tenantId, doc.getProjectId()));
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
                if (doc.isLegalHold()) {
                    throw new HandyFlowException("This document is under legal hold and cannot be voided",
                            HttpStatus.CONFLICT, "LEGAL_HOLD");
                }
                boolean uploader = userId != null && userId.equals(doc.getUploadedBy());
                if (restrictControls
                        && !DocumentAccessRules.mayVoid(isDocumentController(tenantId, doc.getProjectId(), userId), uploader)) {
                    throw forbidden("Only a document controller, or the person who uploaded it, can void a document");
                }
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
    public ProjectDocument setVisibility(TenantId tenantId, UUID docId, String visibility,
                                         UUID userId, String userName) {
        ProjectDocument doc = findForUpdate(tenantId, docId);
        requireNotClosed(verifyProject(tenantId, doc.getProjectId()));
        if (restrictControls
                && !DocumentAccessRules.mayChangeVisibility(isDocumentController(tenantId, doc.getProjectId(), userId))) {
            throw forbidden("Only a document controller can change who can see a document");
        }
        String target = "CLIENT".equals(visibility) ? "CLIENT" : "INTERNAL";
        if (target.equals(doc.getVisibility())) return doc;
        String before = doc.getVisibility();
        doc.changeVisibility(target);
        ProjectDocument saved = docRepo.save(doc);
        record(saved, "VISIBILITY", before + " → " + target, userId, userName);
        return saved;
    }

    /** Placing or lifting a legal hold works on any status, and on a CLOSED project (that is when it is needed). */
    @Transactional
    public ProjectDocument setLegalHold(TenantId tenantId, UUID docId, boolean hold, String reason,
                                        UUID userId, String userName) {
        ProjectDocument doc = findForUpdate(tenantId, docId);
        verifyProject(tenantId, doc.getProjectId());
        if (restrictControls
                && !DocumentAccessRules.mayChangeLegalHold(isDocumentController(tenantId, doc.getProjectId(), userId))) {
            throw forbidden("Only a document controller can place or lift a legal hold");
        }
        String note = blankToNull(reason);
        if (note == null) throw bad("A reason is required to " + (hold ? "place" : "lift") + " a legal hold", "REASON_REQUIRED");
        if (doc.isDisposed()) throw new HandyFlowException("This document's file was disposed of under the retention policy", HttpStatus.CONFLICT, "DISPOSED");
        if (doc.isLegalHold() == hold) return doc;
        doc.changeLegalHold(hold);
        ProjectDocument saved = docRepo.save(doc);
        retention.moduleHoldChanged(tenantId, ProjectsRetentionProvider.MODULE, ProjectsRetentionProvider.SOURCE, doc.getId(), hold, note, userId, userName);
        record(saved, "LEGAL_HOLD", (hold ? "Placed: " : "Lifted: ") + note, userId, userName);
        return saved;
    }

    // ── Retention ─────────────────────────────────────────────────────────────

    /** Called when a project is closed. Stamps the documents that have no retention date yet. */
    @Transactional
    public int stampRetention(TenantId tenantId, UUID projectId, LocalDate closedOn) {
        LocalDate until = DocumentAccessRules.retentionUntil(closedOn, retentionYears);
        int n = docRepo.stampRetention(tenantId.getValue(), projectId, until);
        retention.startClock(tenantId, ProjectsRetentionProvider.MODULE, ProjectsRetentionProvider.SOURCE,
                docRepo.findStoredFileIds(tenantId.getValue(), projectId), closedOn);
        log.info("Retention stamped on {} document(s) of project={} until {}", n, projectId, until);
        return n;
    }

    // ── Files ─────────────────────────────────────────────────────────────────

    /** Internal download. Written to the document's trail. */
    @Transactional
    public FileDownload download(TenantId tenantId, UUID docId, UUID userId, String userName) {
        ProjectDocument doc = find(tenantId, docId);
        FileDownload file = readStored(tenantId, doc);
        record(doc, "DOWNLOADED", null, userId, userName);
        return file;
    }

    /**
     * Portal download. Only an APPROVED document marked CLIENT, of THIS project, is released; anything
     * else is the same 404 as a document that does not exist.
     */
    @Transactional
    public FileDownload downloadForPortal(Project resolvedProject, UUID docId) {
        ProjectDocument doc = docRepo.findByTenantAndId(resolvedProject.getTenantId(), docId)
                .filter(d -> d.getProjectId().equals(resolvedProject.getId()))
                .filter(d -> "CLIENT".equals(d.getVisibility()) && d.statusEnum() == DocumentStatus.APPROVED)
                .orElseThrow(() -> notFound("Document"));
        FileDownload file = readStored(TenantId.of(resolvedProject.getTenantId()), doc);
        record(doc, "DOWNLOADED", "Client portal", null, "Client portal");
        return file;
    }

    /**
     * Releases the exact revision that was issued on a transmittal. The caller (TransmittalAccessService)
     * has already proved the recipient may have it; this checks the document still belongs to the project,
     * is not voided, and still matches the hash recorded when it was issued, so a recipient never receives
     * a different file from the one the transmittal lists. Written to the document's trail.
     */
    @Transactional(noRollbackFor = HandyFlowException.class)
    public FileDownload downloadIssuedRevision(UUID tenantId, UUID projectId, UUID docId,
                                               String issuedSha256, String recipientLabel) {
        ProjectDocument doc = docRepo.findByTenantAndId(tenantId, docId)
                .filter(d -> d.getProjectId().equals(projectId))
                .filter(d -> d.statusEnum() != DocumentStatus.VOIDED)
                .orElseThrow(() -> notFound("Document"));
        if (issuedSha256 != null && doc.hasStoredFile() && !issuedSha256.equalsIgnoreCase(doc.getFileSha256())) {
            log.error("Issued revision mismatch: document={} issued hash differs from the stored file", docId);
            throw new HandyFlowException("This file no longer matches the revision that was issued, so it was not released",
                    HttpStatus.CONFLICT, "FILE_INTEGRITY");
        }
        FileDownload file = readStored(TenantId.of(tenantId), doc);
        record(doc, "DOWNLOADED", "Transmittal recipient", null, recipientLabel);
        return file;
    }

    private FileDownload readStored(TenantId tenantId, ProjectDocument doc) {
        if (!doc.hasStoredFile()) {
            throw new HandyFlowException("This entry is a link, not a stored file", HttpStatus.CONFLICT, "NO_STORED_FILE");
        }
        if (doc.isDisposed()) {
            throw new HandyFlowException("This document's file was disposed of under the retention policy. The register entry is kept.",
                    HttpStatus.GONE, "DISPOSED");
        }
        EvidenceFacade.DownloadedEvidence stored = evidenceFacade.download(tenantId, doc.getEvidenceId());
        String expected = doc.getFileSha256();
        if (expected != null && !expected.equalsIgnoreCase(DocumentRules.sha256(stored.content()))) {
            log.error("INTEGRITY FAILURE: document={} evidence={} no longer matches the hash taken at upload",
                    doc.getId(), doc.getEvidenceId());
            throw new HandyFlowException(
                    "This file no longer matches the record made when it was uploaded, so it was not released. "
                            + "Contact your administrator.", HttpStatus.CONFLICT, "FILE_INTEGRITY");
        }
        return new FileDownload(stored.content(), DocumentRules.safeFileName(doc.getFileName()),
                DocumentRules.downloadContentType(doc.getContentType()));
    }

    // ── Links to other project records ────────────────────────────────────────

    @Transactional
    public ProjectDocumentLink link(TenantId tenantId, UUID docId, DocumentLinkType type, UUID targetId,
                                    String note, UUID userId, String userName) {
        ProjectDocument doc = find(tenantId, docId);
        requireNotClosed(verifyProject(tenantId, doc.getProjectId()));
        if (doc.statusEnum() == DocumentStatus.VOIDED) {
            throw new HandyFlowException("A voided document cannot be linked", HttpStatus.CONFLICT, "DOCUMENT_VOIDED");
        }
        // The record must exist, in this tenant, AND in this project; anything else is the same 404.
        boolean sameProject = scopeResolver.projectIdFor(type.scopeKeyword(), targetId, tenantId.getValue())
                .map(doc.getProjectId()::equals).orElse(false);
        if (!sameProject) throw notFound("The record to link");
        if (linkRepo.existsLink(doc.getId(), type.name(), targetId)) {
            throw new HandyFlowException("This document is already linked to that record",
                    HttpStatus.CONFLICT, "ALREADY_LINKED");
        }
        String cleanNote = blankToNull(note);
        ProjectDocumentLink saved = linkRepo.save(ProjectDocumentLink.create(tenantId.getValue(), doc.getProjectId(),
                doc.getId(), type, targetId, cleanNote, userId, userName));
        record(doc, "LINKED", type.name() + " " + targetId, userId, userName);
        return saved;
    }

    @Transactional
    public void unlink(TenantId tenantId, UUID docId, UUID linkId, UUID userId, String userName) {
        ProjectDocument doc = find(tenantId, docId);
        requireNotClosed(verifyProject(tenantId, doc.getProjectId()));
        ProjectDocumentLink link = linkRepo.findByDocumentAndId(tenantId.getValue(), doc.getId(), linkId)
                .orElseThrow(() -> notFound("Link"));
        boolean linker = userId != null && userId.equals(link.getLinkedBy());
        if (restrictControls
                && !(linker || isDocumentController(tenantId, doc.getProjectId(), userId))) {
            throw forbidden("Only a document controller, or the person who made the link, can remove it");
        }
        linkRepo.delete(link);
        record(doc, "UNLINKED", link.getLinkType() + " " + link.getTargetId(), userId, userName);
    }

    @Transactional(readOnly = true)
    public List<ProjectDocumentLink> getLinks(TenantId tenantId, UUID docId) {
        ProjectDocument doc = find(tenantId, docId);
        return linkRepo.findByDocument(tenantId.getValue(), doc.getId());
    }

    /** "Which documents are evidence for this RFI / change order / task…?" */
    @Transactional(readOnly = true)
    public List<ProjectDocument> getDocumentsLinkedTo(TenantId tenantId, UUID projectId,
                                                      DocumentLinkType type, UUID targetId) {
        verifyProject(tenantId, projectId);
        List<UUID> ids = linkRepo.findByTarget(tenantId.getValue(), projectId, type.name(), targetId).stream()
                .map(ProjectDocumentLink::getDocumentId).toList();
        if (ids.isEmpty()) return List.of();
        return docRepo.findAllById(ids).stream()
                .filter(d -> tenantId.getValue().equals(d.getTenantId()) && projectId.equals(d.getProjectId()))
                .toList();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void record(ProjectDocument doc, String action, String comment, UUID by, String byName) {
        reviewRepo.save(ProjectDocumentReview.record(doc.getTenantId(), doc.getProjectId(), doc.getId(),
                action, comment, by, byName));
    }

    private ProjectDocument find(TenantId tenantId, UUID id) {
        return docRepo.findByTenantAndId(tenantId.getValue(), id).orElseThrow(() -> notFound("Document"));
    }

    private ProjectDocument findForUpdate(TenantId tenantId, UUID id) {
        return docRepo.findByTenantAndIdForUpdate(tenantId.getValue(), id).orElseThrow(() -> notFound("Document"));
    }

    private Project verifyProject(TenantId tenantId, UUID projectId) {
        return projectRepo.findByTenantAndId(tenantId.getValue(), projectId).orElseThrow(() -> notFound("Project"));
    }

    /** PM_APPROVE or PM_ADMIN on the tenant, or MANAGER / DOCUMENT_CONTROLLER on this project. */
    public boolean isDocumentController(TenantId tenantId, UUID projectId, UUID userId) {
        if (hasAuthority("PM_APPROVE") || hasAuthority("PM_ADMIN")) return true;
        if (userId == null) return false;
        return memberRepo.findByProjectAndUser(tenantId.getValue(), projectId, userId)
                .map(m -> m.getRole().canControlDocuments())
                .orElse(false);
    }

    private static void requireNotClosed(Project p) {
        if (p.getStatus() == ProjectStatus.CLOSED) {
            throw new HandyFlowException("Project is CLOSED — its documents can no longer be changed",
                    HttpStatus.CONFLICT, "PROJECT_CLOSED");
        }
    }

    private static boolean hasAuthority(String authority) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> authority.equals(a.getAuthority()));
    }

    private static void requireAuthority(String authority) {
        if (!hasAuthority(authority)) {
            throw new HandyFlowException("You need the " + authority + " permission for this action",
                    HttpStatus.FORBIDDEN, "FORBIDDEN");
        }
    }

    private static HandyFlowException forbidden(String message) {
        return new HandyFlowException(message, HttpStatus.FORBIDDEN, "FORBIDDEN");
    }

    private static HandyFlowException bad(String message, String code) {
        return new HandyFlowException(message, HttpStatus.BAD_REQUEST, code);
    }

    private static HandyFlowException notFound(String entity) {
        return new HandyFlowException(entity + " not found", HttpStatus.NOT_FOUND, "NOT_FOUND");
    }

    private static String blankToNull(String s) { return (s == null || s.isBlank()) ? null : s.trim(); }
}
