package za.co.handyflow.platform.accountant.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.accountant.domain.model.AccWorkpaperAudit;
import za.co.handyflow.platform.accountant.domain.model.AccWorkpaperFile;
import za.co.handyflow.platform.accountant.domain.model.AccWorkpaperFolder;
import za.co.handyflow.platform.accountant.domain.repository.AccClientRepository;
import za.co.handyflow.platform.accountant.domain.repository.AccWorkpaperAuditRepository;
import za.co.handyflow.platform.accountant.domain.repository.AccWorkpaperFileRepository;
import za.co.handyflow.platform.accountant.domain.repository.AccWorkpaperFolderRepository;
import za.co.handyflow.platform.accountant.dto.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Closes the accountant module audit's "larger workpaper system" gap.
 * Deliberately a separate service, not folded into the already-large
 * AccountantService — matches the precedent already set with
 * AccountantPortalDataService/AccountantPortalAuthService rather than
 * letting one class keep growing indefinitely.
 * <p>
 * Scope for this pass: folders, versioned file upload/list/download,
 * the full review workflow, soft delete/restore, and audit logging.
 * Document requests (acc_document_requests) are a genuinely separate
 * feature with its own lifecycle — deliberately not built here.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccWorkpaperService {

    private final AccClientRepository clientRepo;
    private final AccWorkpaperFolderRepository folderRepo;
    private final AccWorkpaperFileRepository fileRepo;
    private final AccWorkpaperAuditRepository auditRepo;

    private static final long MAX_WORKPAPER_FILE_BYTES = 10L * 1024 * 1024;
    // Expanded beyond the base FICA-document allowlist: workpaper
    // folder types include TB (trial balance) and RECONS
    // (reconciliations) — real accounting workpapers for these are
    // typically Excel files, a justified domain-specific addition, not
    // an arbitrary guess.
    private static final Set<String> ALLOWED_WORKPAPER_TYPES = Set.of(
            "application/pdf", "image/jpeg", "image/jpg", "image/png",
            "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    );

    private void findActiveClient(TenantId tenantId, UUID clientId) {
        clientRepo.findActiveById(tenantId, clientId)
                .orElseThrow(() -> new HandyFlowException("Client not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
    }

    // ── Folders ──────────────────────────────────────────────────────────────

    @Transactional
    public WorkpaperFolderResponse createFolder(TenantId tenantId, UUID clientId, CreateFolderRequest req) {
        findActiveClient(tenantId, clientId);
        AccWorkpaperFolder folder = AccWorkpaperFolder.create(tenantId.getValue(), clientId, req.parentId(),
                req.engagementYear(), req.name(), req.folderType(), 0);
        folderRepo.save(folder);
        return toFolderResponse(folder);
    }

    @Transactional(readOnly = true)
    public List<WorkpaperFolderResponse> getFolders(TenantId tenantId, UUID clientId) {
        findActiveClient(tenantId, clientId);
        return folderRepo.findByTenantIdAndClientId(tenantId.getValue(), clientId).stream()
                .map(this::toFolderResponse).toList();
    }

    // ── Files ────────────────────────────────────────────────────────────────

    /**
     * Uploads a file. If a current (non-superseded, non-deleted)
     * version already exists in the folder with the same name, the new
     * upload becomes the next version and supersedes it — versioning
     * happens automatically on the natural "re-upload this file" action,
     * not a separate explicit step.
     */
    @Transactional
    public WorkpaperFileResponse uploadFile(TenantId tenantId, UUID clientId,
                                            UploadWorkpaperFileRequest req, UUID uploadedBy) {
        findActiveClient(tenantId, clientId);
        AccWorkpaperFolder folder = folderRepo.findByTenantIdAndId(tenantId.getValue(), req.folderId())
                .orElseThrow(() -> new HandyFlowException("Folder not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
        if (!folder.getClientId().equals(clientId)) {
            throw new HandyFlowException("Folder not found", HttpStatus.NOT_FOUND, "NOT_FOUND");
        }

        String mimeType = req.mimeType() != null ? req.mimeType() : "application/octet-stream";
        if (!ALLOWED_WORKPAPER_TYPES.contains(mimeType)) {
            throw new HandyFlowException(
                    "Unsupported file type — please upload a PDF, JPG, PNG, Word, or Excel document",
                    HttpStatus.BAD_REQUEST, "UNSUPPORTED_FILE_TYPE");
        }
        long approxDecodedBytes = (req.fileContentBase64().length() * 3L) / 4;
        if (approxDecodedBytes > MAX_WORKPAPER_FILE_BYTES) {
            throw new HandyFlowException(
                    "File is too large — maximum is " + (MAX_WORKPAPER_FILE_BYTES / (1024 * 1024)) + "MB",
                    HttpStatus.BAD_REQUEST, "FILE_TOO_LARGE");
        }

        AccWorkpaperFile previous = fileRepo.findCurrentVersionByName(tenantId.getValue(), req.folderId(), req.fileName())
                .orElse(null);
        int nextVersion = previous != null ? previous.getVersionNumber() + 1 : 1;

        AccWorkpaperFile file = AccWorkpaperFile.create(tenantId.getValue(), clientId, req.folderId(),
                req.fileName(), mimeType, req.fileSizeBytes(), req.fileContentBase64(), nextVersion);
        fileRepo.save(file);

        if (previous != null) {
            previous.markSuperseded(file.getId());
            fileRepo.save(previous);
        }

        auditRepo.save(AccWorkpaperAudit.record(tenantId.getValue(), file.getId(), "UPLOADED", uploadedBy));
        log.info("Workpaper file '{}' (v{}) uploaded to folder={} for client={}",
                req.fileName(), nextVersion, req.folderId(), clientId);
        return toFileResponse(file);
    }

    @Transactional(readOnly = true)
    public List<WorkpaperFileResponse> getFiles(TenantId tenantId, UUID clientId, UUID folderId) {
        findActiveClient(tenantId, clientId);
        AccWorkpaperFolder folder = folderRepo.findByTenantIdAndId(tenantId.getValue(), folderId)
                .orElseThrow(() -> new HandyFlowException("Folder not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
        if (!folder.getClientId().equals(clientId)) {
            throw new HandyFlowException("Folder not found", HttpStatus.NOT_FOUND, "NOT_FOUND");
        }
        return fileRepo.findSummariesByFolder(tenantId.getValue(), folderId).stream()
                .map(p -> new WorkpaperFileResponse(p.getId(), folderId, p.getFileName(), p.getMimeType(),
                        p.getFileSizeBytes(), p.getReviewStatus(), p.getVersionNumber(), p.getSupersededBy(),
                        p.getCreatedAt()))
                .toList();
    }

    public record WorkpaperFileDownload(byte[] content, String mimeType, String fileName) {}

    @Transactional
    public WorkpaperFileDownload downloadFile(TenantId tenantId, UUID clientId, UUID fileId, UUID downloadedBy) {
        AccWorkpaperFile file = requireOwnedFile(tenantId, clientId, fileId);
        auditRepo.save(AccWorkpaperAudit.record(tenantId.getValue(), fileId, "DOWNLOADED", downloadedBy));
        byte[] content = Base64.getDecoder().decode(file.getFileContentBase64());
        String mimeType = file.getMimeType() != null && !file.getMimeType().isBlank()
                ? file.getMimeType() : "application/octet-stream";
        return new WorkpaperFileDownload(content, mimeType, file.getFileName());
    }

    /**
     * Dispatches to the correct state-machine transition based on
     * req.status() — PREPARED/REVIEWED/SIGNED_OFF/DRAFT (reopen).
     * Deliberately ignores req.reviewedBy() (client-supplied) and
     * always derives the actor from the authenticated session instead —
     * same fix already applied to TimeEntry.logTime() this session, for
     * the same reason: nothing should let a caller claim an action was
     * performed by someone else.
     */
    @Transactional
    public WorkpaperFileResponse updateFileStatus(TenantId tenantId, UUID clientId, UUID fileId,
                                                  UpdateWorkpaperStatusRequest req, UUID actorId) {
        AccWorkpaperFile file = requireOwnedFile(tenantId, clientId, fileId);
        switch (req.status()) {
            case "PREPARED"   -> file.markPrepared(actorId);
            case "REVIEWED"   -> file.markReviewed(actorId);
            case "SIGNED_OFF" -> file.signOff(actorId);
            case "DRAFT"      -> file.reopen();
            default -> throw new HandyFlowException(
                    "Unknown status: " + req.status(), HttpStatus.BAD_REQUEST, "INVALID_STATUS");
        }
        fileRepo.save(file);
        auditRepo.save(AccWorkpaperAudit.record(tenantId.getValue(), fileId, "STATUS_CHANGED", actorId));
        return toFileResponse(file);
    }

    @Transactional
    public void deleteFile(TenantId tenantId, UUID clientId, UUID fileId, UUID deletedBy) {
        AccWorkpaperFile file = requireOwnedFile(tenantId, clientId, fileId);
        file.softDelete();
        fileRepo.save(file);
        auditRepo.save(AccWorkpaperAudit.record(tenantId.getValue(), fileId, "DELETED", deletedBy));
    }

    @Transactional
    public WorkpaperFileResponse restoreFile(TenantId tenantId, UUID clientId, UUID fileId, UUID restoredBy) {
        AccWorkpaperFile file = requireOwnedFile(tenantId, clientId, fileId);
        file.restore();
        fileRepo.save(file);
        auditRepo.save(AccWorkpaperAudit.record(tenantId.getValue(), fileId, "RESTORED", restoredBy));
        return toFileResponse(file);
    }

    @Transactional(readOnly = true)
    public List<WorkpaperAuditResponse> getFileAuditLog(TenantId tenantId, UUID clientId, UUID fileId) {
        requireOwnedFile(tenantId, clientId, fileId);
        return auditRepo.findByFile(tenantId.getValue(), fileId).stream()
                .map(a -> new WorkpaperAuditResponse(a.getId(), a.getEventType(), a.getPerformedBy(), a.getPerformedAt()))
                .toList();
    }

    private AccWorkpaperFile requireOwnedFile(TenantId tenantId, UUID clientId, UUID fileId) {
        AccWorkpaperFile file = fileRepo.findByTenantIdAndId(tenantId.getValue(), fileId)
                .orElseThrow(() -> new HandyFlowException("File not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
        if (!file.getClientId().equals(clientId)) {
            throw new HandyFlowException("File not found", HttpStatus.NOT_FOUND, "NOT_FOUND");
        }
        return file;
    }

    private WorkpaperFolderResponse toFolderResponse(AccWorkpaperFolder f) {
        return new WorkpaperFolderResponse(f.getId(), f.getName(), f.getParentId(), f.getEngagementYear(),
                f.getFolderType(), f.getSortOrder());
    }

    private WorkpaperFileResponse toFileResponse(AccWorkpaperFile f) {
        return new WorkpaperFileResponse(f.getId(), f.getFolderId(), f.getFileName(), f.getMimeType(),
                f.getFileSizeBytes(), f.getReviewStatus(), f.getVersionNumber(), f.getSupersededBy(), f.getCreatedAt());
    }
}