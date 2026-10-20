package za.co.handyflow.platform.documents.application.internal;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.audittrail.application.AuditTrailFacade;
import za.co.handyflow.platform.documents.application.DocumentScanner;
import za.co.handyflow.platform.documents.application.DocumentsFacade;
import za.co.handyflow.platform.documents.domain.model.Document;
import za.co.handyflow.platform.documents.domain.model.DocumentRules;
import za.co.handyflow.platform.documents.domain.model.DocumentVersion;
import za.co.handyflow.platform.documents.domain.repository.DocumentRepository;
import za.co.handyflow.platform.documents.domain.repository.DocumentVersionRepository;
import za.co.handyflow.platform.shared.FileStorageService;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
class DocumentsFacadeImpl implements DocumentsFacade {

    private static final ZoneId ZONE = ZoneId.of("Africa/Johannesburg");

    private final DocumentRepository documents;
    private final DocumentVersionRepository versions;
    private final FileStorageService storage;
    private final DocumentScanner scanner;
    private final AuditTrailFacade trail;
    private final boolean scanRequired;

    DocumentsFacadeImpl(DocumentRepository documents, DocumentVersionRepository versions, FileStorageService storage, DocumentScanner scanner,
                        AuditTrailFacade trail, @Value("${documents.scan.required:false}") boolean scanRequired) {
        this.documents = documents;
        this.versions = versions;
        this.storage = storage;
        this.scanner = scanner;
        this.trail = trail;
        this.scanRequired = scanRequired;
    }

    static LocalDate today() { return LocalDate.now(ZONE); }

    @Override
    @Transactional
    public DocumentInfo upload(TenantId tenantId, String ownerType, UUID ownerId, String category, String title, String description,
                               LocalDate expiresOn, FileUpload file, UUID actor) {
        if (ownerType == null || ownerType.isBlank() || ownerId == null || category == null || category.isBlank())
            throw new IllegalArgumentException("A document needs an owner and a category.");
        Document d = Document.create(tenantId, ownerType, ownerId, category, title, description, DocumentRules.expiry(expiresOn, today()), actor);
        DocumentVersion v = storeVersion(tenantId, d, 1, file, null, actor);
        documents.save(d);
        versions.save(v);
        trail.record(tenantId, "DOCUMENT_UPLOADED", "DOCUMENT", d.getId(), null, summary(d, v));
        return info(d, v);
    }

    @Override
    @Transactional
    public DocumentInfo addVersion(TenantId tenantId, UUID documentId, FileUpload file, String note, LocalDate renewedExpiry, UUID actor) {
        Document d = owned(tenantId, documentId);
        if (!d.isActive()) throw new IllegalStateException("This document is archived and cannot be changed.");
        DocumentVersion current = versions.findOne(tenantId, d.getId(), d.getCurrentVersion()).orElse(null);
        Map<String, Object> before = summary(d, current);
        DocumentRules.expiry(renewedExpiry, today());
        int next = d.getCurrentVersion() + 1;
        DocumentVersion v = storeVersion(tenantId, d, next, file, note, actor);
        if (current != null && current.getSha256().equals(v.getSha256())) {
            deleteQuietly(v.getStorageKey());
            throw new IllegalArgumentException("That is the same file as the current version.");
        }
        d.nextVersion(renewedExpiry);
        documents.save(d);
        versions.save(v);
        trail.record(tenantId, "DOCUMENT_VERSION_ADDED", "DOCUMENT", d.getId(), before, summary(d, v));
        return info(d, v);
    }

    @Override
    @Transactional
    public DocumentInfo updateDetails(TenantId tenantId, UUID documentId, String title, String description, LocalDate expiresOn, UUID actor) {
        Document d = owned(tenantId, documentId);
        DocumentVersion current = versions.findOne(tenantId, d.getId(), d.getCurrentVersion()).orElse(null);
        Map<String, Object> before = summary(d, current);
        d.details(title, description, DocumentRules.expiry(expiresOn, today()));
        documents.save(d);
        trail.record(tenantId, "DOCUMENT_UPDATED", "DOCUMENT", d.getId(), before, summary(d, current));
        return info(d, current);
    }

    @Override
    @Transactional
    public DocumentInfo archive(TenantId tenantId, UUID documentId, String reason, UUID actor) {
        Document d = owned(tenantId, documentId);
        DocumentVersion current = versions.findOne(tenantId, d.getId(), d.getCurrentVersion()).orElse(null);
        Map<String, Object> before = summary(d, current);
        d.archive(reason, actor);
        documents.save(d);
        Map<String, Object> after = summary(d, current);
        after.put("reason", d.getArchivedReason());
        trail.record(tenantId, "DOCUMENT_ARCHIVED", "DOCUMENT", d.getId(), before, after);
        return info(d, current);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DocumentInfo> find(TenantId tenantId, UUID documentId) {
        return documents.findOwned(tenantId, documentId).map(d -> info(d, versions.findOne(tenantId, d.getId(), d.getCurrentVersion()).orElse(null)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentInfo> list(TenantId tenantId, String ownerType, UUID ownerId, boolean includeArchived) {
        return withCurrent(tenantId, documents.findForOwner(tenantId, ownerType, ownerId, includeArchived));
    }

    @Override
    @Transactional(readOnly = true)
    public List<VersionInfo> versions(TenantId tenantId, UUID documentId) {
        owned(tenantId, documentId);
        return versions.findForDocument(tenantId, documentId).stream().map(DocumentsFacadeImpl::info).toList();
    }

    @Override
    @Transactional
    public Download download(TenantId tenantId, UUID documentId, Integer versionNo, UUID actor) {
        Document d = owned(tenantId, documentId);
        int n = versionNo == null ? d.getCurrentVersion() : versionNo;
        DocumentVersion v = versions.findOne(tenantId, d.getId(), n).orElseThrow(() -> new ResourceNotFoundException("Document version", documentId + "/" + n));
        byte[] bytes;
        try { bytes = storage.retrieve(v.getStorageKey()); }
        catch (IOException e) {
            log.error("Document {} version {} could not be read from storage: {}", documentId, n, e.getMessage());
            throw new IllegalStateException("The file could not be read from storage. Try again, or contact support.");
        }
        trail.record(tenantId, "DOCUMENT_DOWNLOADED", "DOCUMENT", d.getId(), null, Map.of("title", d.getTitle(), "version", n, "file", v.getFileName()));
        return new Download(bytes, v.getFileName(), v.getContentType());
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentInfo> expiring(TenantId tenantId, Collection<String> ownerTypes, LocalDate until) {
        return withCurrent(tenantId, documents.findExpiring(tenantId, ownerTypes, until));
    }

    @Override
    @Transactional(readOnly = true)
    public List<TenantId> tenantsWithExpiring(Collection<String> ownerTypes, LocalDate until) {
        return documents.findTenantsWithExpiring(ownerTypes, until);
    }

    @Override
    @Transactional(readOnly = true)
    public long countActive(TenantId tenantId, String ownerType, UUID ownerId, String category) {
        return documents.countActive(tenantId, ownerType, ownerId, category);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** Checks, scans and stores the file, and returns the unsaved version row. An infected file, or an unscanned one when scanning is required, is refused. */
    private DocumentVersion storeVersion(TenantId tenantId, Document d, int versionNo, FileUpload file, String note, UUID actor) {
        if (file == null) throw new IllegalArgumentException("Choose a file to upload.");
        DocumentRules.Checked checked = DocumentRules.check(file.fileName(), file.contentType(), file.content(), DocumentRules.allowedFor(d.getCategory()));
        DocumentScanner.Result scan;
        try { scan = scanner.scan(checked.fileName(), checked.contentType(), file.content()); }
        catch (RuntimeException e) { scan = new DocumentScanner.Result(DocumentScanner.Status.ERROR, "The scan failed"); }
        if (scan.status() == DocumentScanner.Status.INFECTED) {
            trail.record(tenantId, "DOCUMENT_REJECTED_INFECTED", "DOCUMENT", d.getId(), null,
                    Map.of("file", checked.fileName(), "detail", scan.detail() == null ? "infected" : scan.detail()));
            throw new IllegalArgumentException("The file was refused: the malware scan found a problem with it.");
        }
        if (scanRequired && scan.status() != DocumentScanner.Status.CLEAN)
            throw new IllegalStateException("The file could not be scanned for malware, so it was not stored. Try again later.");
        String key;
        try { key = storage.store("documents/" + tenantId.getValue() + "/" + d.getOwnerType() + "/" + d.getOwnerId(), checked.fileName(), checked.contentType(), file.content()); }
        catch (IOException e) {
            log.error("Document file could not be stored: {}", e.getMessage());
            throw new IllegalStateException("The file could not be stored. Try again, or contact support.");
        }
        return DocumentVersion.of(tenantId, d.getId(), versionNo, checked, file.content().length, key,
                scan.status() == DocumentScanner.Status.CLEAN ? "CLEAN" : "SKIPPED", scan.detail(), note, actor);
    }

    private void deleteQuietly(String key) {
        try { storage.delete(key); } catch (IOException e) { log.warn("Could not remove an unused stored file {}: {}", key, e.getMessage()); }
    }

    private Document owned(TenantId tenantId, UUID id) {
        return documents.findOwned(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Document", String.valueOf(id)));
    }

    private List<DocumentInfo> withCurrent(TenantId tenantId, List<Document> docs) {
        if (docs.isEmpty()) return List.of();
        Map<UUID, Map<Integer, DocumentVersion>> byDoc = versions.findForDocuments(tenantId, docs.stream().map(Document::getId).toList()).stream()
                .collect(Collectors.groupingBy(DocumentVersion::getDocumentId, Collectors.toMap(DocumentVersion::getVersionNo, Function.identity())));
        return docs.stream().map(d -> info(d, byDoc.getOrDefault(d.getId(), Map.of()).get(d.getCurrentVersion()))).toList();
    }

    private static Map<String, Object> summary(Document d, DocumentVersion v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ownerType", d.getOwnerType());
        m.put("ownerId", d.getOwnerId().toString());
        m.put("category", d.getCategory());
        m.put("title", d.getTitle());
        m.put("version", d.getCurrentVersion());
        if (d.getExpiresOn() != null) m.put("expiresOn", d.getExpiresOn().toString());
        if (v != null) { m.put("file", v.getFileName()); m.put("sha256", v.getSha256()); m.put("scan", v.getScanStatus()); }
        return m;
    }

    private static VersionInfo info(DocumentVersion v) {
        return new VersionInfo(v.getId(), v.getVersionNo(), v.getFileName(), v.getContentType(), v.getSizeBytes(), v.getSha256(), v.getScanStatus(),
                v.getNote(), v.getUploadedBy(), v.getUploadedAt());
    }

    private static DocumentInfo info(Document d, DocumentVersion v) {
        return new DocumentInfo(d.getId(), d.getOwnerType(), d.getOwnerId(), d.getCategory(), d.getTitle(), d.getDescription(), d.getExpiresOn(),
                d.getStatus(), d.getArchivedReason(), d.getCurrentVersion(), v == null ? null : info(v), d.getCreatedAt(), d.getCreatedBy());
    }
}
