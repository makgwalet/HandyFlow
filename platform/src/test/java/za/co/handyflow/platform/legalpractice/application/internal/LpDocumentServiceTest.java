package za.co.handyflow.platform.legalpractice.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.multipart.MultipartFile;
import za.co.handyflow.platform.evidence.application.EvidenceFacade;
import za.co.handyflow.platform.evidence.dto.EvidenceResponse;
import za.co.handyflow.platform.legalpractice.domain.model.*;
import za.co.handyflow.platform.legalpractice.domain.repository.*;
import za.co.handyflow.platform.legalpractice.dto.DocumentDtos.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Matter documents against in-memory stand-ins for the repositories and the evidence store (no database, no Spring): versions, hashes, check-out,
 * legal hold, archiving, the event log, tenant isolation and ethical walls. The migration's constraints and triggers were run against PostgreSQL separately.
 */
class LpDocumentServiceTest {

    private final TenantId firm = TenantId.of(UUID.randomUUID());
    private final TenantId otherFirm = TenantId.of(UUID.randomUUID());
    private final UUID anele = UUID.randomUUID(), buhle = UUID.randomUUID(), admin = UUID.randomUUID();

    private final Map<UUID, LpDocument> docs = new LinkedHashMap<>();
    private final List<LpDocumentVersion> versions = new ArrayList<>();
    private final List<LpDocumentEvent> events = new ArrayList<>();
    private final Map<UUID, LpMatter> matters = new LinkedHashMap<>();
    private final Map<UUID, byte[]> store = new HashMap<>();
    private final List<String> attached = new ArrayList<>();
    private final List<UUID> walledMatters = new ArrayList<>(), walledClients = new ArrayList<>();
    private final UUID clientId = UUID.randomUUID();

    private LpDocumentService service;
    private LpMatter matter;

    record Upload(String name, byte[] bytes) implements MultipartFile {
        @Override public String getName() { return "file"; }
        @Override public String getOriginalFilename() { return name; }
        @Override public String getContentType() { return "application/octet-stream"; }
        @Override public boolean isEmpty() { return bytes.length == 0; }
        @Override public long getSize() { return bytes.length; }
        @Override public byte[] getBytes() { return bytes; }
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(bytes); }
        @Override public void transferTo(java.io.File dest) { }
    }

    private static byte[] pdf(String body) { return ("%PDF-1.7 " + body).getBytes(StandardCharsets.ISO_8859_1); }
    private static Upload file(String name, String body) { return new Upload(name, pdf(body)); }

    @SuppressWarnings("unchecked")
    private static <T> T fake(Class<T> type, Map<String, Function<Object[], Object>> handlers) {
        InvocationHandler h = (proxy, m, args) -> {
            Function<Object[], Object> f = handlers.get(m.getName());
            if (f != null) return f.apply(args == null ? new Object[0] : args);
            if (m.isDefault()) return InvocationHandler.invokeDefault(proxy, m, args);
            throw new UnsupportedOperationException(m.getName());
        };
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, h);
    }

    @BeforeEach
    void setUp() {
        docs.clear(); versions.clear(); events.clear(); matters.clear(); store.clear(); attached.clear(); walledMatters.clear(); walledClients.clear();
        var docRepo = fake(LpDocumentRepository.class, Map.of(
                "findActiveById", a -> Optional.ofNullable(docs.get((UUID) a[1])).filter(d -> d.getTenantId().equals(a[0])),
                "findForMatter", a -> docs.values().stream().filter(d -> d.getTenantId().equals(a[0]) && d.getMatterId().equals(a[1])).toList(),
                "save", a -> { LpDocument d = (LpDocument) a[0]; docs.put(d.getId(), d); return d; },
                "findFirmExcluding", a -> {
                    boolean archived = (Boolean) a[3]; String cat = (String) a[4]; String pat = (String) a[5];
                    String needle = pat.substring(1, pat.length() - 1);
                    return new PageImpl<>(docs.values().stream().filter(d -> d.getTenantId().equals(a[0]) && !((List<?>) a[1]).contains(d.getMatterId()) && !((List<?>) a[2]).contains(d.getClientId())
                            && (archived || d.active()) && (cat.isEmpty() || d.getCategory().equals(cat)) && d.getTitle().toLowerCase().contains(needle)).toList());
                }));
        var versionRepo = fake(LpDocumentVersionRepository.class, Map.of(
                "save", a -> { versions.add((LpDocumentVersion) a[0]); return a[0]; },
                "findForDocument", a -> versions.stream().filter(v -> v.getTenantId().equals(a[0]) && v.getDocumentId().equals(a[1])).sorted(Comparator.comparingInt(LpDocumentVersion::getVersionNo).reversed()).toList(),
                "findByNo", a -> versions.stream().filter(v -> v.getTenantId().equals(a[0]) && v.getDocumentId().equals(a[1]) && v.getVersionNo() == (Integer) a[2]).findFirst()));
        var eventRepo = fake(LpDocumentEventRepository.class, Map.of(
                "save", a -> { events.add((LpDocumentEvent) a[0]); return a[0]; },
                "findForDocument", a -> { List<LpDocumentEvent> l = new ArrayList<>(events.stream().filter(e -> e.getTenantId().equals(a[0]) && e.getDocumentId().equals(a[1])).toList()); Collections.reverse(l); return l; }));
        var matterRepo = fake(LpMatterRepository.class, Map.of(
                "findActiveById", a -> Optional.ofNullable(matters.get((UUID) a[1])).filter(m -> m.getTenantId().equals(a[0])),
                "findAllById", a -> { List<LpMatter> l = new ArrayList<>(); for (Object id : (Iterable<?>) a[0]) if (matters.containsKey(id)) l.add(matters.get(id)); return l; }));
        EvidenceFacade evidence = fake(EvidenceFacade.class, Map.of(
                "attachRestricted", a -> {
                    MultipartFile f = (MultipartFile) a[1];
                    UUID id = UUID.randomUUID();
                    try { store.put(id, f.getBytes()); } catch (Exception e) { throw new RuntimeException(e); }
                    attached.add(a[3] + "|" + a[4] + "|" + a[5]);
                    return new EvidenceResponse(id, f.getOriginalFilename(), f.getContentType(), f.getSize(), (String) a[2], "ACTIVE", "x", Instant.now());
                },
                "downloadRestricted", a -> {
                    byte[] c = store.get((UUID) a[4]);
                    if (c == null || !a[1].equals(LpDocumentService.SOURCE) || !a[2].equals(LpDocumentService.ENTITY) || !docs.containsKey((UUID) a[3])) throw new ResourceNotFoundException("Evidence", a[4].toString());
                    return new EvidenceFacade.DownloadedEvidence(c, "stored.bin", "application/octet-stream");
                }));
        LpWallGuard guard = new LpWallGuard() {
            @Override public void requireAccess(TenantId t, LpMatter m) { }
            @Override public void requireMatterAccess(TenantId t, UUID id) { if (walledMatters.contains(id)) throw new AccessDeniedException("walled"); }
            @Override public void requireClientAccess(TenantId t, UUID id) { if (walledClients.contains(id)) throw new AccessDeniedException("walled"); }
            @Override public void requireScopeAccess(TenantId t, UUID m, UUID c) { }
            @Override public Barred barred(TenantId t) { return Barred.of(walledMatters, walledClients); }
        };
        service = new LpDocumentService(docRepo, versionRepo, eventRepo, matterRepo, evidence, guard);
        matter = LpMatter.create(firm, clientId, UUID.randomUUID(), "M-1", "LITIGATION", "Dlamini v Nkosi", null, "HOURLY", null, LocalDate.of(2026, 1, 5), null);
        matters.put(matter.getId(), matter);
    }

    private UUID upload(String title) {
        return service.upload(firm, matter.getId(), title, "PLEADING", file(title + ".pdf", "v1 of " + title), "First draft", anele, "Anele").document().id();
    }

    private DocumentDetail version(UUID id, UUID by, String body) { return service.addVersion(firm, id, file("x.pdf", body), "Changes", false, by, by.equals(anele) ? "Anele" : "Buhle"); }

    private List<String> eventTypes() { return events.stream().map(LpDocumentEvent::getEventType).toList(); }

    // ---- adding --------------------------------------------------------------------------------------------------------

    @Test
    void uploadingCreatesVersionOneWithItsHashAndLogsIt() {
        var d = service.upload(firm, matter.getId(), "  Letter of demand ", "CORRESPONDENCE", file("..\\letter.pdf", "hello"), " Sent to client ", anele, "Anele");
        assertEquals("Letter of demand", d.document().title());
        assertEquals(1, d.document().currentVersion());
        assertEquals("M-1 Dlamini v Nkosi", d.document().matterLabel());
        assertEquals(1, d.versions().size());
        var v = d.versions().get(0);
        assertEquals("letter.pdf", v.fileName());
        assertEquals("application/pdf", v.contentType());
        assertEquals(DocRules.sha256Hex(pdf("hello")), v.sha256());
        assertEquals("Sent to client", v.changeNote());
        assertEquals(List.of("UPLOADED"), eventTypes());
        assertEquals("Anele", d.events().get(0).actorName());
    }

    @Test
    void theFileIsKeptAsRestrictedEvidenceOnThisDocumentNotInTheGeneralStore() {
        UUID id = upload("Brief");
        assertEquals(List.of("restricted-legalpractice|LpDocument|" + id), attached);
        assertTrue(EvidenceFacade.isRestricted(attached.get(0).split("\\|")[0]));
    }

    @Test
    void aFileThatIsNotAcceptableStoresNothing() {
        assertThrows(IllegalArgumentException.class, () -> service.upload(firm, matter.getId(), "Bad", "OTHER", new Upload("run.exe", "MZ..".getBytes()), null, anele, "Anele"));
        assertThrows(IllegalArgumentException.class, () -> service.upload(firm, matter.getId(), "Fake", "OTHER", new Upload("fake.pdf", "MZ..".getBytes()), null, anele, "Anele"));
        assertThrows(IllegalArgumentException.class, () -> service.upload(firm, matter.getId(), "Empty", "OTHER", new Upload("a.pdf", new byte[0]), null, anele, "Anele"));
        assertThrows(IllegalArgumentException.class, () -> service.upload(firm, matter.getId(), "Nothing", "OTHER", null, null, anele, "Anele"));
        assertTrue(attached.isEmpty());
        assertTrue(docs.isEmpty());
        assertTrue(events.isEmpty());
    }

    @Test
    void aMissingTitleOrCategoryStoresNothing() {
        assertThrows(IllegalArgumentException.class, () -> service.upload(firm, matter.getId(), " ", "OTHER", file("a.pdf", "x"), null, anele, "Anele"));
        assertThrows(IllegalArgumentException.class, () -> service.upload(firm, matter.getId(), "Title", "MEMES", file("a.pdf", "x"), null, anele, "Anele"));
        assertTrue(attached.isEmpty());
        assertTrue(docs.isEmpty());
    }

    @Test
    void aMatterOfAnotherFirmOrThatDoesNotExistIsNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> service.upload(otherFirm, matter.getId(), "A", "OTHER", file("a.pdf", "x"), null, anele, "Anele"));
        assertThrows(ResourceNotFoundException.class, () -> service.upload(firm, UUID.randomUUID(), "A", "OTHER", file("a.pdf", "x"), null, anele, "Anele"));
        assertThrows(ResourceNotFoundException.class, () -> service.forMatter(otherFirm, matter.getId()));
    }

    // ---- versions ------------------------------------------------------------------------------------------------------

    @Test
    void eachNewVersionIsKeptAndEveryOldOneCanStillBeOpened() {
        UUID id = upload("Contract");
        var d = version(id, anele, "second");
        d = version(id, buhle, "third");
        assertEquals(3, d.document().currentVersion());
        assertEquals(List.of(3, 2, 1), d.versions().stream().map(VersionDto::versionNo).toList());
        assertArrayEquals(pdf("v1 of Contract"), service.download(firm, id, 1, anele, "Anele").content());
        assertArrayEquals(pdf("second"), service.download(firm, id, 2, anele, "Anele").content());
        assertArrayEquals(pdf("third"), service.download(firm, id, null, anele, "Anele").content());
        assertEquals(3, service.download(firm, id, null, anele, "Anele").versionNo());
    }

    @Test
    void eachVersionHasItsOwnHash() {
        UUID id = upload("Contract");
        var d = version(id, anele, "second");
        assertNotEquals(d.versions().get(0).sha256(), d.versions().get(1).sha256());
    }

    @Test
    void anUnknownVersionIsNotFound() {
        UUID id = upload("Contract");
        assertThrows(ResourceNotFoundException.class, () -> service.download(firm, id, 7, anele, "Anele"));
    }

    @Test
    void aDocumentOfAnotherFirmIsNotFound() {
        UUID id = upload("Contract");
        assertThrows(ResourceNotFoundException.class, () -> service.get(otherFirm, id));
        assertThrows(ResourceNotFoundException.class, () -> service.download(otherFirm, id, null, anele, "Anele"));
        assertThrows(ResourceNotFoundException.class, () -> service.checkOut(otherFirm, id, anele, "Anele"));
        assertThrows(ResourceNotFoundException.class, () -> service.setHold(otherFirm, id, "Litigation is likely", anele, "Anele"));
    }

    @Test
    void aRejectedNewVersionLeavesTheDocumentAsItWas() {
        UUID id = upload("Contract");
        assertThrows(IllegalArgumentException.class, () -> service.addVersion(firm, id, new Upload("a.exe", "MZ".getBytes()), null, false, anele, "Anele"));
        assertEquals(1, docs.get(id).getCurrentVersion());
        assertEquals(1, versions.size());
    }

    // ---- check-out -----------------------------------------------------------------------------------------------------

    @Test
    void whileCheckedOutOnlyTheHolderMayAddAVersion() {
        UUID id = upload("Contract");
        service.checkOut(firm, id, anele, "Anele");
        var e = assertThrows(IllegalStateException.class, () -> version(id, buhle, "mine"));
        assertTrue(e.getMessage().contains("Anele has this document checked out"));
        assertEquals(1, docs.get(id).getCurrentVersion());
        assertEquals(1, attached.size());
        assertThrows(IllegalStateException.class, () -> service.checkOut(firm, id, buhle, "Buhle"));
        assertThrows(IllegalStateException.class, () -> service.cancelCheckout(firm, id, buhle, "Buhle"));
    }

    @Test
    void theHoldersNewVersionChecksItIn() {
        UUID id = upload("Contract");
        service.checkOut(firm, id, anele, "Anele");
        var d = service.addVersion(firm, id, file("c.pdf", "edited"), "Edited", true, anele, "Anele");
        assertNull(d.document().checkedOutBy());
        assertEquals(2, d.document().currentVersion());
        assertTrue(d.events().get(0).detail().contains("checked in"));
        version(id, buhle, "now free");
    }

    @Test
    void checkInNeedsYouToHaveItCheckedOut() {
        UUID id = upload("Contract");
        assertThrows(IllegalStateException.class, () -> service.addVersion(firm, id, file("c.pdf", "x"), null, true, anele, "Anele"));
        service.checkOut(firm, id, buhle, "Buhle");
        assertThrows(IllegalStateException.class, () -> service.addVersion(firm, id, file("c.pdf", "x"), null, true, anele, "Anele"));
    }

    @Test
    void theHolderMayCancelTheirCheckOut() {
        UUID id = upload("Contract");
        service.checkOut(firm, id, anele, "Anele");
        assertThrows(IllegalStateException.class, () -> service.checkOut(firm, id, anele, "Anele"));
        var d = service.cancelCheckout(firm, id, anele, "Anele");
        assertNull(d.document().checkedOutBy());
        assertThrows(IllegalStateException.class, () -> service.cancelCheckout(firm, id, anele, "Anele"));
        assertEquals(List.of("UPLOADED", "CHECKED_OUT", "CHECKOUT_CANCELLED"), eventTypes());
    }

    @Test
    void releasingSomeoneElsesCheckOutNeedsAReasonAndIsRecordedWithWhoLostIt() {
        UUID id = upload("Contract");
        service.checkOut(firm, id, anele, "Anele");
        assertThrows(IllegalArgumentException.class, () -> service.overrideCheckout(firm, id, "no", admin, "Admin"));
        var d = service.overrideCheckout(firm, id, "Anele is on leave and the filing is due", admin, "Admin");
        assertNull(d.document().checkedOutBy());
        var last = events.get(events.size() - 1);
        assertEquals("LOCK_OVERRIDDEN", last.getEventType());
        assertTrue(last.getDetail().contains("Anele"));
        assertTrue(last.getDetail().contains("on leave"));
        assertEquals("Admin", last.getActorName());
        version(id, buhle, "now free");
        assertThrows(IllegalStateException.class, () -> service.overrideCheckout(firm, id, "Nobody has it checked out", admin, "Admin"));
    }

    // ---- hold and archive ----------------------------------------------------------------------------------------------

    @Test
    void aDocumentUnderLegalHoldCannotBeArchived() {
        UUID id = upload("Contract");
        assertThrows(IllegalArgumentException.class, () -> service.setHold(firm, id, "short", admin, "Admin"));
        var d = service.setHold(firm, id, "Litigation against the supplier is likely", admin, "Admin");
        assertTrue(d.document().legalHold());
        assertEquals("Litigation against the supplier is likely", d.holdReason());
        assertEquals("Admin", d.holdSetByName());
        assertThrows(IllegalStateException.class, () -> service.setHold(firm, id, "Litigation against the supplier is likely", admin, "Admin"));
        var e = assertThrows(IllegalStateException.class, () -> service.archive(firm, id, "Matter closed and filed away", anele, "Anele"));
        assertTrue(e.getMessage().contains("legal hold"));
        assertTrue(docs.get(id).active());
    }

    @Test
    void aHeldDocumentCanStillGetNewVersionsAndReleasingNeedsAReason() {
        UUID id = upload("Contract");
        service.setHold(firm, id, "Litigation against the supplier is likely", admin, "Admin");
        assertEquals(2, version(id, anele, "more").document().currentVersion());
        assertThrows(IllegalArgumentException.class, () -> service.releaseHold(firm, id, "ok", admin, "Admin"));
        var d = service.releaseHold(firm, id, "The dispute was settled in writing", admin, "Admin");
        assertFalse(d.document().legalHold());
        assertNull(d.holdReason());
        assertThrows(IllegalStateException.class, () -> service.releaseHold(firm, id, "The dispute was settled in writing", admin, "Admin"));
        assertEquals("HOLD_RELEASED", events.get(events.size() - 1).getEventType());
        service.archive(firm, id, "Matter closed and filed away", anele, "Anele");
    }

    @Test
    void anArchivedDocumentIsReadOnlyUntilRestored() {
        UUID id = upload("Contract");
        assertThrows(IllegalArgumentException.class, () -> service.archive(firm, id, "old", anele, "Anele"));
        var d = service.archive(firm, id, "Matter closed and filed away", anele, "Anele");
        assertEquals("ARCHIVED", d.document().status());
        assertEquals("Matter closed and filed away", d.archivedReason());
        assertThrows(IllegalStateException.class, () -> version(id, anele, "late"));
        assertThrows(IllegalStateException.class, () -> service.checkOut(firm, id, anele, "Anele"));
        assertThrows(IllegalStateException.class, () -> service.edit(firm, id, "New", "OTHER", anele, "Anele"));
        assertThrows(IllegalStateException.class, () -> service.setHold(firm, id, "Litigation against the supplier is likely", admin, "Admin"));
        service.download(firm, id, null, anele, "Anele");   // still readable
        var r = service.restore(firm, id, anele, "Anele");
        assertEquals("ACTIVE", r.document().status());
        assertNull(r.archivedReason());
        assertThrows(IllegalStateException.class, () -> service.restore(firm, id, anele, "Anele"));
        version(id, anele, "back");
    }

    @Test
    void aCheckedOutDocumentCannotBeArchived() {
        UUID id = upload("Contract");
        service.checkOut(firm, id, anele, "Anele");
        assertThrows(IllegalStateException.class, () -> service.archive(firm, id, "Matter closed and filed away", anele, "Anele"));
    }

    @Test
    void editingChangesTheTitleAndCategoryAndLogsTheOldOnes() {
        UUID id = upload("Contract");
        var d = service.edit(firm, id, " Signed contract ", "CONTRACT", anele, "Anele");
        assertEquals("Signed contract", d.document().title());
        assertEquals("CONTRACT", d.document().category());
        assertTrue(d.events().get(0).detail().startsWith("Contract / PLEADING"));
        assertThrows(IllegalArgumentException.class, () -> service.edit(firm, id, "", "CONTRACT", anele, "Anele"));
        assertThrows(IllegalArgumentException.class, () -> service.edit(firm, id, "Fine", "NOPE", anele, "Anele"));
    }

    // ---- reading, hashes, events ---------------------------------------------------------------------------------------

    @Test
    void everyDownloadIsLoggedWithWhoAndWhichVersion() {
        UUID id = upload("Contract");
        version(id, anele, "second");
        service.download(firm, id, 1, buhle, "Buhle");
        var last = events.get(events.size() - 1);
        assertEquals("DOWNLOADED", last.getEventType());
        assertEquals("Buhle", last.getActorName());
        assertEquals(1, last.getVersionNo());
        assertEquals("Contract.pdf", last.getDetail());
    }

    @Test
    void verifyingMatchesTheStoredFileToTheHashTakenOnReceipt() {
        UUID id = upload("Contract");
        var ok = service.verify(firm, id, null, anele, "Anele");
        assertTrue(ok.matches());
        assertEquals(DocRules.sha256Hex(pdf("v1 of Contract")), ok.recordedSha256());
        assertTrue(events.get(events.size() - 1).getDetail().contains("matches"));
    }

    @Test
    void aFileThatHasBeenAlteredInTheStoreFailsVerificationAndSaysSoInTheLog() {
        UUID id = upload("Contract");
        UUID evidenceId = versions.get(0).getEvidenceId();
        store.put(evidenceId, pdf("tampered"));
        var bad = service.verify(firm, id, 1, anele, "Anele");
        assertFalse(bad.matches());
        var last = events.get(events.size() - 1);
        assertEquals("VERIFIED", last.getEventType());
        assertTrue(last.getDetail().contains("DOES NOT MATCH"));
    }

    @Test
    void theDetailShowsVersionsAndEventsNewestFirst() {
        UUID id = upload("Contract");
        version(id, anele, "second");
        service.checkOut(firm, id, anele, "Anele");
        var d = service.get(firm, id);
        assertEquals(List.of(2, 1), d.versions().stream().map(VersionDto::versionNo).toList());
        assertEquals(List.of("CHECKED_OUT", "NEW_VERSION", "UPLOADED"), d.events().stream().map(EventDto::eventType).toList());
        assertEquals("Anele", d.document().checkedOutByName());
    }

    // ---- lists ---------------------------------------------------------------------------------------------------------

    @Test
    void theFirmListFiltersByTitleCategoryAndArchivedAndLabelsTheMatter() {
        UUID a = upload("Letter of demand");
        service.edit(firm, a, "Letter of demand", "CORRESPONDENCE", anele, "Anele");
        UUID b = upload("Particulars of claim");
        UUID c = upload("Old letter");
        service.archive(firm, c, "Matter closed and filed away", anele, "Anele");
        var all = service.firmList(firm, null, null, false, PageRequest.of(0, 20)).getContent();
        assertEquals(2, all.size());
        assertEquals("M-1 Dlamini v Nkosi", all.get(0).matterLabel());
        assertEquals(1, service.firmList(firm, "LETTER", null, false, PageRequest.of(0, 20)).getContent().size());
        assertEquals(2, service.firmList(firm, "letter", null, true, PageRequest.of(0, 20)).getContent().size());
        assertEquals(List.of(a), service.firmList(firm, null, "CORRESPONDENCE", false, PageRequest.of(0, 20)).getContent().stream().map(DocumentSummary::id).toList());
        assertEquals(2, service.firmList(firm, "%_", null, false, PageRequest.of(0, 20)).getContent().size());   // wildcards are not passed through
        assertEquals(b, service.firmList(firm, "particulars", null, false, PageRequest.of(0, 20)).getContent().get(0).id());
    }

    @Test
    void theFirmListNeverShowsAnotherFirmsDocuments() {
        upload("Letter of demand");
        assertTrue(service.firmList(otherFirm, null, null, false, PageRequest.of(0, 20)).getContent().isEmpty());
    }

    @Test
    void aMatterListShowsOnlyThatMattersDocuments() {
        upload("Letter of demand");
        var other = LpMatter.create(firm, clientId, UUID.randomUUID(), "M-2", "LITIGATION", "Other", null, "HOURLY", null, LocalDate.of(2026, 1, 6), null);
        matters.put(other.getId(), other);
        service.upload(firm, other.getId(), "Other doc", "OTHER", file("o.pdf", "o"), null, anele, "Anele");
        assertEquals(1, service.forMatter(firm, matter.getId()).size());
        assertEquals(1, service.forMatter(firm, other.getId()).size());
    }

    // ---- ethical walls -------------------------------------------------------------------------------------------------

    @Test
    void aPersonWalledFromTheMatterCannotUseItsDocuments() {
        UUID id = upload("Contract");
        walledMatters.add(matter.getId());
        int filesBefore = attached.size(), eventsBefore = events.size();
        assertThrows(AccessDeniedException.class, () -> service.forMatter(firm, matter.getId()));
        assertThrows(AccessDeniedException.class, () -> service.upload(firm, matter.getId(), "New", "OTHER", file("n.pdf", "n"), null, buhle, "Buhle"));
        assertThrows(AccessDeniedException.class, () -> service.get(firm, id));
        assertThrows(AccessDeniedException.class, () -> service.download(firm, id, null, buhle, "Buhle"));
        assertThrows(AccessDeniedException.class, () -> service.verify(firm, id, null, buhle, "Buhle"));
        assertThrows(AccessDeniedException.class, () -> service.addVersion(firm, id, file("n.pdf", "n"), null, false, buhle, "Buhle"));
        assertThrows(AccessDeniedException.class, () -> service.checkOut(firm, id, buhle, "Buhle"));
        assertThrows(AccessDeniedException.class, () -> service.cancelCheckout(firm, id, buhle, "Buhle"));
        assertThrows(AccessDeniedException.class, () -> service.overrideCheckout(firm, id, "Needed for the filing today", buhle, "Buhle"));
        assertThrows(AccessDeniedException.class, () -> service.setHold(firm, id, "Litigation is likely here", buhle, "Buhle"));
        assertThrows(AccessDeniedException.class, () -> service.releaseHold(firm, id, "Litigation is not likely", buhle, "Buhle"));
        assertThrows(AccessDeniedException.class, () -> service.archive(firm, id, "Matter closed and filed away", buhle, "Buhle"));
        assertThrows(AccessDeniedException.class, () -> service.restore(firm, id, buhle, "Buhle"));
        assertThrows(AccessDeniedException.class, () -> service.edit(firm, id, "Renamed", "OTHER", buhle, "Buhle"));
        assertEquals(filesBefore, attached.size());
        assertEquals(eventsBefore, events.size());
    }

    @Test
    void walledMattersAndClientsAreLeftOutOfTheFirmList() {
        upload("Contract");
        assertEquals(1, service.firmList(firm, null, null, false, PageRequest.of(0, 20)).getContent().size());
        walledMatters.add(matter.getId());
        assertTrue(service.firmList(firm, null, null, false, PageRequest.of(0, 20)).getContent().isEmpty());
        walledMatters.clear();
        walledClients.add(clientId);
        assertTrue(service.firmList(firm, null, null, false, PageRequest.of(0, 20)).getContent().isEmpty());
    }
}
