package za.co.handyflow.platform.legalpractice.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import za.co.handyflow.platform.evidence.application.EvidenceFacade;
import za.co.handyflow.platform.evidence.dto.EvidenceResponse;
import za.co.handyflow.platform.legalpractice.domain.model.*;
import za.co.handyflow.platform.legalpractice.domain.repository.*;
import za.co.handyflow.platform.legalpractice.dto.DocumentDtos.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Matter documents with version history. Files are kept as restricted evidence, so they never appear in the general evidence screens or to an
 * external auditor. Every version is kept; nothing is deleted. Every read of a file, and every change, is written to the document's event log.
 * Ethical walls apply: a person barred from a matter cannot see, open or add to its documents, and does not find them in the firm-wide list.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LpDocumentService {

    public static final String SOURCE = "restricted-legalpractice";
    public static final String ENTITY = "LpDocument";
    private static final String EVIDENCE_TYPE = "MATTER_DOCUMENT";
    private static final int EVENTS_SHOWN = 100;

    private final LpDocumentRepository docRepo;
    private final LpDocumentVersionRepository versionRepo;
    private final LpDocumentEventRepository eventRepo;
    private final LpMatterRepository matterRepo;
    private final EvidenceFacade evidence;
    private final LpWallGuard walls;

    /** A file read back, with the version it is. */
    public record DocumentFile(byte[] content, String fileName, String contentType, int versionNo) { }

    // ---- reading -------------------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<DocumentSummary> forMatter(TenantId tenantId, UUID matterId) {
        walls.requireMatterAccess(tenantId, matterId);
        LpMatter matter = findMatter(tenantId, matterId);
        String label = label(matter);
        return docRepo.findForMatter(tenantId, matterId).stream().map(d -> summary(d, label)).toList();
    }

    @Transactional(readOnly = true)
    public Page<DocumentSummary> firmList(TenantId tenantId, String search, String category, boolean includeArchived, Pageable pageable) {
        var barred = walls.barred(tenantId);
        String q = search == null ? "" : search.trim().toLowerCase(Locale.ROOT).replace("%", "").replace("_", "");
        Page<LpDocument> page = docRepo.findFirmExcluding(tenantId, barred.matterIds(), barred.clientIds(), includeArchived,
                category == null ? "" : category, "%" + q + "%", pageable);
        Map<UUID, String> labels = labels(tenantId, page.getContent().stream().map(LpDocument::getMatterId).collect(Collectors.toSet()));
        return page.map(d -> summary(d, labels.getOrDefault(d.getMatterId(), "")));
    }

    @Transactional(readOnly = true)
    public DocumentDetail get(TenantId tenantId, UUID id) {
        LpDocument d = find(tenantId, id);
        return detail(d);
    }

    // ---- adding --------------------------------------------------------------------------------------------------------

    @Transactional
    public DocumentDetail upload(TenantId tenantId, UUID matterId, String title, String category, MultipartFile file, String note, UUID by, String byName) {
        walls.requireMatterAccess(tenantId, matterId);
        LpMatter matter = findMatter(tenantId, matterId);
        LpDocument doc = LpDocument.create(tenantId, matter.getId(), matter.getClientId(), title, category, by, byName, Instant.now());
        Stored s = store(tenantId, doc.getId(), file, by, byName);
        docRepo.save(doc);
        versionRepo.save(LpDocumentVersion.record(tenantId, doc.getId(), 1, s.evidenceId, s.name, s.type, s.size, s.hash, note, by, byName, doc.getCreatedAt()));
        event(tenantId, doc.getId(), 1, "UPLOADED", by, byName, s.name);
        log.info("Uploaded legal practice document={} matter={} tenant={}", doc.getId(), matterId, tenantId);
        return detail(doc);
    }

    /**
     * Adds the next version. A document checked out to someone else is refused. When the person adding it is the one who has it checked out,
     * this is the check-in. With {@code mustHoldCheckout} the call is refused unless they do (the check-in button).
     */
    @Transactional
    public DocumentDetail addVersion(TenantId tenantId, UUID id, MultipartFile file, String note, boolean mustHoldCheckout, UUID by, String byName) {
        LpDocument d = find(tenantId, id);
        if (mustHoldCheckout && !by.equals(d.getCheckedOutBy())) throw new IllegalStateException("You do not have this document checked out");
        d.requireCanAddVersion(by);
        boolean wasCheckedOut = by.equals(d.getCheckedOutBy());
        Stored s = store(tenantId, d.getId(), file, by, byName);
        int no = d.addVersion(by, Instant.now());
        docRepo.save(d);
        versionRepo.save(LpDocumentVersion.record(tenantId, d.getId(), no, s.evidenceId, s.name, s.type, s.size, s.hash, note, by, byName, d.getUpdatedAt()));
        event(tenantId, d.getId(), no, "NEW_VERSION", by, byName, s.name + (wasCheckedOut ? " (checked in)" : ""));
        return detail(d);
    }

    // ---- reading a file ------------------------------------------------------------------------------------------------

    @Transactional
    public DocumentFile download(TenantId tenantId, UUID id, Integer versionNo, UUID by, String byName) {
        LpDocument d = find(tenantId, id);
        LpDocumentVersion v = version(tenantId, d, versionNo);
        EvidenceFacade.DownloadedEvidence file = evidence.downloadRestricted(tenantId, SOURCE, ENTITY, d.getId(), v.getEvidenceId());
        event(tenantId, d.getId(), v.getVersionNo(), "DOWNLOADED", by, byName, v.getFileName());
        return new DocumentFile(file.content(), v.getFileName(), v.getContentType(), v.getVersionNo());
    }

    /** Hashes the stored file again and compares it with the hash taken when it was received. The result is written to the event log. */
    @Transactional
    public VerifyResponse verify(TenantId tenantId, UUID id, Integer versionNo, UUID by, String byName) {
        LpDocument d = find(tenantId, id);
        LpDocumentVersion v = version(tenantId, d, versionNo);
        EvidenceFacade.DownloadedEvidence file = evidence.downloadRestricted(tenantId, SOURCE, ENTITY, d.getId(), v.getEvidenceId());
        boolean matches = DocRules.sha256Hex(file.content()).equals(v.getSha256());
        event(tenantId, d.getId(), v.getVersionNo(), "VERIFIED", by, byName, matches ? "The file matches the hash taken when it was received" : "THE FILE DOES NOT MATCH the hash taken when it was received");
        if (!matches) log.error("Document integrity check failed document={} version={} tenant={}", d.getId(), v.getVersionNo(), tenantId);
        return new VerifyResponse(v.getVersionNo(), matches, v.getSha256());
    }

    // ---- check-out, hold, archive, edit ---------------------------------------------------------------------------------

    @Transactional
    public DocumentDetail checkOut(TenantId tenantId, UUID id, UUID by, String byName) {
        LpDocument d = find(tenantId, id);
        d.checkOut(by, byName, Instant.now());
        docRepo.save(d);
        event(tenantId, id, null, "CHECKED_OUT", by, byName, null);
        return detail(d);
    }

    @Transactional
    public DocumentDetail cancelCheckout(TenantId tenantId, UUID id, UUID by, String byName) {
        LpDocument d = find(tenantId, id);
        d.cancelCheckout(by, Instant.now());
        docRepo.save(d);
        event(tenantId, id, null, "CHECKOUT_CANCELLED", by, byName, null);
        return detail(d);
    }

    @Transactional
    public DocumentDetail overrideCheckout(TenantId tenantId, UUID id, String reason, UUID by, String byName) {
        LpDocument d = find(tenantId, id);
        String problem = DocRules.reasonProblem(reason);
        if (problem != null) throw new IllegalArgumentException(problem);
        String was = d.overrideCheckout(Instant.now());
        docRepo.save(d);
        event(tenantId, id, null, "LOCK_OVERRIDDEN", by, byName, "Released " + was + "'s check-out: " + reason.trim());
        return detail(d);
    }

    @Transactional
    public DocumentDetail setHold(TenantId tenantId, UUID id, String reason, UUID by, String byName) {
        LpDocument d = find(tenantId, id);
        d.setHold(reason, by, byName, Instant.now());
        docRepo.save(d);
        event(tenantId, id, null, "HOLD_SET", by, byName, reason.trim());
        return detail(d);
    }

    @Transactional
    public DocumentDetail releaseHold(TenantId tenantId, UUID id, String reason, UUID by, String byName) {
        LpDocument d = find(tenantId, id);
        String problem = DocRules.reasonProblem(reason);
        if (problem != null) throw new IllegalArgumentException(problem);
        d.releaseHold(Instant.now());
        docRepo.save(d);
        event(tenantId, id, null, "HOLD_RELEASED", by, byName, reason.trim());
        return detail(d);
    }

    @Transactional
    public DocumentDetail archive(TenantId tenantId, UUID id, String reason, UUID by, String byName) {
        LpDocument d = find(tenantId, id);
        d.archive(reason, Instant.now());
        docRepo.save(d);
        event(tenantId, id, null, "ARCHIVED", by, byName, reason.trim());
        return detail(d);
    }

    @Transactional
    public DocumentDetail restore(TenantId tenantId, UUID id, UUID by, String byName) {
        LpDocument d = find(tenantId, id);
        d.restore(Instant.now());
        docRepo.save(d);
        event(tenantId, id, null, "RESTORED", by, byName, null);
        return detail(d);
    }

    @Transactional
    public DocumentDetail edit(TenantId tenantId, UUID id, String title, String category, UUID by, String byName) {
        LpDocument d = find(tenantId, id);
        String before = d.getTitle() + " / " + d.getCategory();
        d.edit(title, category, Instant.now());
        docRepo.save(d);
        event(tenantId, id, null, "EDITED", by, byName, before + " -> " + d.getTitle() + " / " + d.getCategory());
        return detail(d);
    }

    // ---- helpers -------------------------------------------------------------------------------------------------------

    private record Stored(UUID evidenceId, String name, String type, long size, String hash) { }

    private Stored store(TenantId tenantId, UUID documentId, MultipartFile file, UUID by, String byName) {
        String name = DocRules.safeName(file == null ? null : file.getOriginalFilename());
        byte[] content = read(file);
        String problem = DocRules.fileProblem(name, content.length, Arrays.copyOf(content, Math.min(content.length, 16)));
        if (problem != null) throw new IllegalArgumentException(problem);
        String type = DocRules.contentType(name);
        EvidenceResponse saved = evidence.attachRestricted(tenantId, new Named(file, name, type, content), EVIDENCE_TYPE, SOURCE, ENTITY, documentId, by, byName);
        return new Stored(saved.id(), name, type, content.length, DocRules.sha256Hex(content));
    }

    private static byte[] read(MultipartFile file) {
        if (file == null || file.isEmpty()) return new byte[0];
        try (InputStream in = file.getInputStream()) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new IllegalArgumentException("The file could not be read");
        }
    }

    private void event(TenantId tenantId, UUID documentId, Integer versionNo, String type, UUID by, String byName, String detail) {
        eventRepo.save(LpDocumentEvent.record(tenantId, documentId, versionNo, type, by, byName, detail, Instant.now()));
    }

    private LpDocument find(TenantId tenantId, UUID id) {
        LpDocument d = docRepo.findActiveById(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("LpDocument", id.toString()));
        walls.requireMatterAccess(tenantId, d.getMatterId());
        return d;
    }

    private LpDocumentVersion version(TenantId tenantId, LpDocument d, Integer versionNo) {
        int no = versionNo == null ? d.getCurrentVersion() : versionNo;
        return versionRepo.findByNo(tenantId, d.getId(), no).orElseThrow(() -> new ResourceNotFoundException("LpDocumentVersion", d.getId() + "/" + no));
    }

    private LpMatter findMatter(TenantId tenantId, UUID matterId) {
        return matterRepo.findActiveById(tenantId, matterId).orElseThrow(() -> new ResourceNotFoundException("LpMatter", matterId.toString()));
    }

    private static String label(LpMatter m) { return m.getMatterNumber() + " " + m.getMatterName(); }

    private Map<UUID, String> labels(TenantId tenantId, Set<UUID> matterIds) {
        Map<UUID, String> out = new HashMap<>();
        for (LpMatter m : matterRepo.findAllById(matterIds)) if (m.getTenantId().equals(tenantId)) out.put(m.getId(), label(m));
        return out;
    }

    private static DocumentSummary summary(LpDocument d, String matterLabel) {
        return new DocumentSummary(d.getId(), d.getMatterId(), matterLabel, d.getClientId(), d.getTitle(), d.getCategory(), d.getStatus(), d.getCurrentVersion(),
                d.isLegalHold(), d.getCheckedOutBy(), d.getCheckedOutByName(), d.getCheckedOutAt(), d.getUpdatedAt());
    }

    private DocumentDetail detail(LpDocument d) {
        TenantId t = d.getTenantId();
        String matterLabel = matterRepo.findActiveById(t, d.getMatterId()).map(LpDocumentService::label).orElse("");
        List<VersionDto> versions = versionRepo.findForDocument(t, d.getId()).stream()
                .map(v -> new VersionDto(v.getVersionNo(), v.getFileName(), v.getContentType(), v.getSizeBytes(), v.getSha256(), v.getChangeNote(), v.getUploadedByName(), v.getUploadedAt())).toList();
        List<EventDto> events = eventRepo.findForDocument(t, d.getId(), PageRequest.of(0, EVENTS_SHOWN)).stream()
                .map(e -> new EventDto(e.getEventType(), e.getVersionNo(), e.getActorName(), e.getDetail(), e.getOccurredAt())).toList();
        return new DocumentDetail(summary(d, matterLabel), d.getHoldReason(), d.getHoldSetByName(), d.getHoldSetAt(), d.getArchivedReason(), d.getCreatedByName(), d.getCreatedAt(), versions, events);
    }

    /** The upload with the cleaned name and the type decided by the extension, so what is stored is what was checked. */
    private record Named(MultipartFile inner, String name, String type, byte[] bytes) implements MultipartFile {
        @Override public String getName() { return inner.getName(); }
        @Override public String getOriginalFilename() { return name; }
        @Override public String getContentType() { return type; }
        @Override public boolean isEmpty() { return bytes.length == 0; }
        @Override public long getSize() { return bytes.length; }
        @Override public byte[] getBytes() { return bytes; }
        @Override public InputStream getInputStream() { return new java.io.ByteArrayInputStream(bytes); }
        @Override public void transferTo(java.io.File dest) throws IOException { java.nio.file.Files.write(dest.toPath(), bytes); }
    }
}
