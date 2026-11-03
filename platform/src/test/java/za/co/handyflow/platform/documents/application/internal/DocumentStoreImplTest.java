package za.co.handyflow.platform.documents.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import za.co.handyflow.platform.audittrail.application.AuditTrailFacade;
import za.co.handyflow.platform.documents.application.DocumentStore;
import za.co.handyflow.platform.documents.application.StoredFileIntegrityException;
import za.co.handyflow.platform.documents.domain.model.StoreRules;
import za.co.handyflow.platform.documents.domain.model.StoreVerifyRun;
import za.co.handyflow.platform.documents.domain.model.StoredObject;
import za.co.handyflow.platform.documents.domain.repository.StoreVerifyRunRepository;
import za.co.handyflow.platform.documents.domain.repository.StoredObjectRepository;
import za.co.handyflow.platform.shared.FieldEncryptionService;
import za.co.handyflow.platform.shared.FileStorageService;
import za.co.handyflow.platform.shared.SchedulerLease;
import za.co.handyflow.platform.shared.SealedBytes;
import za.co.handyflow.platform.shared.TenantId;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The document store against an in-memory storage provider and in-memory registry, with real AES-GCM. Repositories and the audit
 * trail are java.lang.reflect.Proxy fakes rather than Mockito mocks, so this test runs without Spring or a database.
 */
class DocumentStoreImplTest {

    // ── fakes ────────────────────────────────────────────────────────────────

    /** In-memory provider. {@code broken} makes every call fail, like an unreachable store. */
    static class MemoryStorage implements FileStorageService {
        final Map<String, byte[]> files = new LinkedHashMap<>();
        boolean broken;
        int n;
        @Override public String store(String prefix, String name, String type, byte[] content) throws IOException {
            if (broken) throw new IOException("Storage is down");
            String key = prefix + "/" + (++n) + "-" + name;
            files.put(key, content.clone());
            return key;
        }
        @Override public byte[] retrieve(String key) throws IOException {
            if (broken) throw new IOException("Storage is down");
            byte[] b = files.get(key);
            if (b == null) throw new IOException("No file found for storage key: " + key);
            return b.clone();
        }
        @Override public void delete(String key) throws IOException {
            if (broken) throw new IOException("Storage is down");
            files.remove(key);
        }
    }

    static Object objectMethod(Object proxy, Method m, Object[] a) {
        return switch (m.getName()) {
            case "toString" -> "fake";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == a[0];
            default -> throw new UnsupportedOperationException(m.getName());
        };
    }

    final Map<UUID, StoredObject> rows = new LinkedHashMap<>();
    final Map<UUID, StoreVerifyRun> runRows = new LinkedHashMap<>();
    final List<Map<String, Object>> alerts = new ArrayList<>();

    static int pageSize(Object pageable) { return ((Pageable) pageable).getPageSize(); }

    StoredObjectRepository objectRepo() {
        return (StoredObjectRepository) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{StoredObjectRepository.class}, (p, m, a) -> {
            List<StoredObject> live = rows.values().stream().filter(o -> !o.isDeleted()).toList();
            switch (m.getName()) {
                case "save": { StoredObject o = (StoredObject) a[0]; rows.put(o.getId(), o); return o; }
                case "findById": return Optional.ofNullable(rows.get((UUID) a[0]));
                case "findByStorageKey": return rows.values().stream().filter(o -> o.getStorageKey().equals(a[0])).findFirst();
                case "findDue": return live.stream()
                        .sorted(Comparator.comparing(StoredObject::getLastVerifiedAt, Comparator.nullsFirst(Comparator.naturalOrder()))
                                .thenComparing(StoredObject::getCreatedAt))
                        .limit(pageSize(a[0])).map(StoredObject::getId).collect(Collectors.toList());
                case "countLiveByStatus": return live.stream().collect(Collectors.groupingBy(StoredObject::getVerifyStatus, Collectors.counting()))
                        .entrySet().stream().map(e -> new Object[]{e.getKey(), e.getValue()}).collect(Collectors.toList());
                case "countLiveEncrypted": return live.stream().filter(StoredObject::isEncrypted).count();
                case "oldestCheck": return live.stream().map(StoredObject::getLastVerifiedAt).filter(Objects::nonNull).min(Comparator.naturalOrder());
                case "findFailing": return live.stream().filter(StoredObject::isFailing).limit(pageSize(a[0])).collect(Collectors.toList());
                default: return objectMethod(p, m, a);
            }
        });
    }

    StoreVerifyRunRepository runRepo() {
        return (StoreVerifyRunRepository) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{StoreVerifyRunRepository.class}, (p, m, a) -> {
            Comparator<StoreVerifyRun> newest = Comparator.comparing(StoreVerifyRun::getStartedAt).reversed();
            switch (m.getName()) {
                case "save": { StoreVerifyRun r = (StoreVerifyRun) a[0]; runRows.put(r.getId(), r); return r; }
                case "findById": return Optional.ofNullable(runRows.get((UUID) a[0]));
                case "findFirstByOrderByStartedAtDesc": return runRows.values().stream().sorted(newest).findFirst();
                case "findFirstByFinishedAtIsNotNullOrderByStartedAtDesc":
                    return runRows.values().stream().filter(r -> r.getFinishedAt() != null).sorted(newest).findFirst();
                default: return objectMethod(p, m, a);
            }
        });
    }

    @SuppressWarnings("unchecked")
    AuditTrailFacade trail() {
        return (AuditTrailFacade) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{AuditTrailFacade.class}, (p, m, a) -> {
            if (m.getName().equals("recordAs")) {
                Map<String, Object> e = new LinkedHashMap<>();
                e.put("tenant", ((TenantId) a[0]).getValue());
                e.put("actorType", a[1]);
                e.put("action", a[4]);
                e.put("itemId", a[6]);
                e.putAll((Map<String, ?>) a[8]);
                alerts.add(e);
                return null;
            }
            if (m.getName().equals("record")) return null;
            return objectMethod(p, m, a);
        });
    }

    static final PlatformTransactionManager TX = new PlatformTransactionManager() {
        @Override public TransactionStatus getTransaction(TransactionDefinition d) { return new SimpleTransactionStatus(); }
        @Override public void commit(TransactionStatus s) {}
        @Override public void rollback(TransactionStatus s) {}
    };

    boolean leaseBusy;
    SchedulerLease lease() {
        return new SchedulerLease(null) {
            @Override public boolean runExclusively(String lockName, Duration maxHold, Runnable task) {
                if (leaseBusy) return false;
                task.run();
                return true;
            }
        };
    }

    static final String KEY = Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));

    MemoryStorage storage;
    FieldEncryptionService enc;
    TenantId tenant = TenantId.generate();
    TenantId other = TenantId.generate();
    byte[] pdf = "%PDF-1.7 the issued drawing, revision C".getBytes(StandardCharsets.UTF_8);

    DocumentStoreImpl store(boolean encrypt) {
        return new DocumentStoreImpl(objectRepo(), runRepo(), storage, enc, trail(), lease(), TX, encrypt, 1024, 1000, "database");
    }

    StoredObject row(String key) { return rows.values().stream().filter(o -> o.getStorageKey().equals(key)).findFirst().orElseThrow(); }

    @BeforeEach
    void setUp() {
        storage = new MemoryStorage();
        enc = new FieldEncryptionService(KEY);
        rows.clear(); runRows.clear(); alerts.clear(); leaseBusy = false;
    }

    // ── write and read ───────────────────────────────────────────────────────

    @Test
    void aPlainFileIsStoredAsIsRecordedAndReadBack() throws IOException {
        DocumentStoreImpl s = store(false);
        String key = s.put(tenant, "evidence", "evidence/projects/" + tenant, "C.pdf", "application/pdf", pdf);
        assertArrayEquals(pdf, storage.files.get(key));
        StoredObject o = row(key);
        assertEquals("EVIDENCE", o.getOwnerModule());
        assertEquals(tenant.getValue(), o.getTenantId());
        assertEquals("database", o.getStorageProvider());
        assertFalse(o.isEncrypted());
        assertEquals(StoreRules.sha256(pdf), o.getSha256());
        assertEquals(o.getSha256(), o.getStoredSha256());
        assertEquals("UNVERIFIED", o.getVerifyStatus());
        assertArrayEquals(pdf, s.get(tenant, key));
        assertTrue(s.exists(tenant, key));
    }

    @Test
    void anEncryptedFileIsNeverStoredInTheClearAndReadsBackUnchanged() throws IOException {
        DocumentStoreImpl s = store(true);
        String key = s.put(tenant, "EVIDENCE", "evidence", "C.pdf", "application/pdf", pdf);
        byte[] stored = storage.files.get(key);
        assertTrue(SealedBytes.looksSealed(stored));
        assertFalse(new String(stored, StandardCharsets.ISO_8859_1).contains("issued drawing"));
        StoredObject o = row(key);
        assertTrue(o.isEncrypted());
        assertEquals(FieldEncryptionService.BYTES_KEY_ID, o.getKeyId());
        assertEquals(StoreRules.sha256(pdf), o.getSha256());
        assertEquals(StoreRules.sha256(stored), o.getStoredSha256());
        assertEquals(pdf.length, o.getSizeBytes());
        assertEquals(stored.length, o.getStoredSizeBytes());
        assertArrayEquals(pdf, s.get(tenant, key));
    }

    @Test
    void emptyAndOversizeFilesAreRefusedBeforeAnythingIsWritten() {
        DocumentStoreImpl s = store(true);
        assertThrows(IllegalArgumentException.class, () -> s.put(tenant, "EVIDENCE", "p", "a.pdf", null, new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> s.put(tenant, "EVIDENCE", "p", "a.pdf", null, new byte[1025]));
        assertThrows(IllegalArgumentException.class, () -> s.put(null, "EVIDENCE", "p", "a.pdf", null, pdf));
        assertTrue(storage.files.isEmpty());
        assertTrue(rows.isEmpty());
    }

    // ── integrity on read ────────────────────────────────────────────────────

    @Test
    void aChangedEncryptedFileIsRefusedMarkedAndAlertedOnce() throws IOException {
        DocumentStoreImpl s = store(true);
        String key = s.put(tenant, "EVIDENCE", "p", "C.pdf", "application/pdf", pdf);
        storage.files.get(key)[30] ^= 1;
        assertThrows(StoredFileIntegrityException.class, () -> s.get(tenant, key));
        assertEquals("MISMATCH", row(key).getVerifyStatus());
        assertEquals(1, alerts.size());
        assertEquals("DOCUMENT_STORE_INTEGRITY_FAILED", alerts.get(0).get("action"));
        assertEquals("SYSTEM", alerts.get(0).get("actorType"));
        assertEquals(tenant.getValue(), alerts.get(0).get("tenant"));
        assertEquals("read", alerts.get(0).get("foundBy"));
        assertThrows(StoredFileIntegrityException.class, () -> s.get(tenant, key));
        assertEquals(1, alerts.size(), "a file that stays bad is reported once");
        assertEquals(2, row(key).getFailedChecks());
    }

    @Test
    void aChangedPlainFileIsRefused() throws IOException {
        DocumentStoreImpl s = store(false);
        String key = s.put(tenant, "DOCUMENTS", "p", "C.pdf", "application/pdf", pdf);
        storage.files.get(key)[0] = 'X';
        StoredFileIntegrityException e = assertThrows(StoredFileIntegrityException.class, () -> s.get(tenant, key));
        assertEquals("FILE_INTEGRITY", e.getErrorCode());
        assertEquals("MISMATCH", row(key).getVerifyStatus());
    }

    @Test
    void anEncryptedFileCopiedOntoAnotherRecordWillNotOpen() throws IOException {
        DocumentStoreImpl s = store(true);
        String a = s.put(tenant, "EVIDENCE", "p", "a.pdf", null, pdf);
        String b = s.put(tenant, "EVIDENCE", "p", "b.pdf", null, "%PDF-1.7 another file".getBytes(StandardCharsets.UTF_8));
        storage.files.put(b, storage.files.get(a).clone());
        assertThrows(StoredFileIntegrityException.class, () -> s.get(tenant, b));
        assertArrayEquals(pdf, s.get(tenant, a));
    }

    // ── tenants, legacy files, deletion ──────────────────────────────────────

    @Test
    void anotherTenantCannotReadSeeOrDeleteAFile() throws IOException {
        DocumentStoreImpl s = store(true);
        String key = s.put(tenant, "EVIDENCE", "p", "C.pdf", null, pdf);
        IOException e = assertThrows(IOException.class, () -> s.get(other, key));
        assertTrue(e.getMessage().startsWith("No file found"), "reads exactly like a missing file");
        assertFalse(s.exists(other, key));
        s.delete(other, key, "not theirs");
        assertTrue(storage.files.containsKey(key));
        assertFalse(row(key).isDeleted());
        assertArrayEquals(pdf, s.get(tenant, key));
    }

    @Test
    void aFileFromBeforeTheStoreStillReadsAsItWas() throws IOException {
        DocumentStoreImpl s = store(true);
        String legacy = storage.store("evidence/old", "old.pdf", "application/pdf", pdf);
        assertArrayEquals(pdf, s.get(tenant, legacy));
        assertTrue(s.exists(tenant, legacy));
        assertFalse(s.exists(tenant, "evidence/old/nothing-here"));
        assertThrows(IOException.class, () -> s.get(tenant, "evidence/old/nothing-here"));
        assertTrue(rows.isEmpty(), "reading a legacy file does not register it");
    }

    @Test
    void deletingRemovesTheBytesAndKeepsTheRecord() throws IOException {
        DocumentStoreImpl s = store(false);
        String key = s.put(tenant, "DOCUMENTS", "p", "C.pdf", null, pdf);
        s.delete(tenant, key, "Same file as the current version");
        assertFalse(storage.files.containsKey(key));
        StoredObject o = row(key);
        assertTrue(o.isDeleted());
        assertEquals("Same file as the current version", o.getDeletedReason());
        assertEquals(StoreRules.sha256(pdf), o.getSha256(), "the tombstone still proves what the file was");
        assertThrows(IOException.class, () -> s.get(tenant, key));
        assertFalse(s.exists(tenant, key));
        s.delete(tenant, key, "again");
        assertEquals("Same file as the current version", row(key).getDeletedReason());
    }

    @Test
    void aFileSealedWithAKeyThisServerDoesNotHoldIsUnreadableNotTampered() throws IOException {
        DocumentStoreImpl s = store(true);
        UUID id = UUID.randomUUID();
        byte[] sealed = enc.sealBytes(pdf, StoreRules.aad(tenant.getValue(), id));
        String key = storage.store("p", "old-key.pdf", null, sealed);
        rows.put(id, StoredObject.written(id, tenant.getValue(), "EVIDENCE", key, "database", null, pdf.length, StoreRules.sha256(pdf),
                sealed.length, StoreRules.sha256(sealed), "retired-key"));
        IOException e = assertThrows(IOException.class, () -> s.get(tenant, key));
        assertTrue(e.getMessage().contains("retired-key"));
        DocumentStore.RunSummary r = s.verifyNow(10, null);
        assertEquals(1, r.failed());
        assertEquals("UNREADABLE", row(key).getVerifyStatus());
    }

    // ── the check ────────────────────────────────────────────────────────────

    @Test
    void theCheckFindsMissingAndChangedFilesAndPassesTheRest() throws IOException {
        DocumentStoreImpl s = store(true);
        String good = s.put(tenant, "EVIDENCE", "p", "good.pdf", null, pdf);
        String gone = s.put(tenant, "EVIDENCE", "p", "gone.pdf", null, pdf);
        String changed = s.put(other, "DOCUMENTS", "p", "changed.pdf", null, pdf);
        storage.files.remove(gone);
        storage.files.get(changed)[25] ^= 1;
        UUID admin = UUID.randomUUID();

        DocumentStore.RunSummary r = s.verifyNow(100, admin);

        assertEquals(StoreVerifyRun.MANUAL, r.trigger());
        assertEquals(3, r.checked());
        assertEquals(1, r.ok());
        assertEquals(2, r.failed());
        assertEquals(Boolean.TRUE, r.canaryOk());
        assertNotNull(r.finishedAt());
        assertEquals(admin, runRows.get(r.id()).getTriggeredBy());
        assertEquals("OK", row(good).getVerifyStatus());
        assertEquals("MISSING", row(gone).getVerifyStatus());
        assertEquals("MISMATCH", row(changed).getVerifyStatus());
        assertEquals(2, alerts.size());
        assertTrue(alerts.stream().allMatch(a -> "check".equals(a.get("foundBy"))));
        assertEquals(other.getValue(), alerts.stream().filter(a -> "MISMATCH".equals(a.get("status"))).findFirst().orElseThrow().get("tenant"),
                "the alert goes to the tenant that owns the file");
        assertEquals(Set.of(good, changed), storage.files.keySet(), "the round-trip probe file was removed again");

        DocumentStore.Health h = s.health();
        assertEquals(3, h.liveFiles());
        assertEquals(3, h.encryptedFiles());
        assertEquals(2, h.failingCount());
        assertEquals(Long.valueOf(1), h.byStatus().get("OK"));
        assertEquals(Long.valueOf(0), h.byStatus().get("UNVERIFIED"));
        assertEquals(2, h.failing().size());
        assertTrue(h.encryptionOn());
        assertEquals(r.id(), h.lastRun().id());

        s.verifyNow(100, admin);
        assertEquals(2, alerts.size(), "still bad the next night: not alerted again");
    }

    @Test
    void theCheckTakesNeverCheckedFilesFirstAndHonoursItsLimit() throws IOException {
        DocumentStoreImpl s = store(false);
        String a = s.put(tenant, "EVIDENCE", "p", "a.pdf", null, pdf);
        String b = s.put(tenant, "EVIDENCE", "p", "b.pdf", null, pdf);
        String c = s.put(tenant, "EVIDENCE", "p", "c.pdf", null, pdf);
        assertEquals(2, s.verifyNow(2, null).checked());
        assertNotNull(row(a).getLastVerifiedAt());
        assertNotNull(row(b).getLastVerifiedAt());
        assertNull(row(c).getLastVerifiedAt());
        assertEquals(1, s.verifyNow(1, null).checked());
        assertNotNull(row(c).getLastVerifiedAt(), "the never-checked file came next");
    }

    @Test
    void deletedFilesAreNotChecked() throws IOException {
        DocumentStoreImpl s = store(false);
        String key = s.put(tenant, "DOCUMENTS", "p", "C.pdf", null, pdf);
        s.delete(tenant, key, "replaced");
        assertEquals(0, s.verifyNow(10, null).checked());
        assertEquals(0, s.health().liveFiles());
    }

    @Test
    void aStorageProviderThatCannotWriteIsReported() throws IOException {
        DocumentStoreImpl s = store(false);
        String key = s.put(tenant, "EVIDENCE", "p", "C.pdf", null, pdf);
        storage.broken = true;
        DocumentStore.RunSummary r = s.verifyNow(10, null);
        assertEquals(Boolean.FALSE, r.canaryOk());
        assertTrue(r.canaryDetail().contains("Storage is down"));
        assertEquals("UNREADABLE", row(key).getVerifyStatus(), "a provider fault is not reported as a missing file");
        assertEquals(Boolean.FALSE, s.lastFinishedRun().canaryOk());
    }

    @Test
    void aSecondCheckIsRefusedWhileOneIsRunning() {
        DocumentStoreImpl s = store(false);
        leaseBusy = true;
        assertThrows(IllegalStateException.class, () -> s.verifyNow(10, null));
        assertTrue(runRows.isEmpty());
        s.runScheduled();
        assertTrue(runRows.isEmpty(), "the nightly run skips quietly");
    }

    @Test
    void theNightlyRunIsRecordedAsScheduled() throws IOException {
        DocumentStoreImpl s = store(false);
        s.put(tenant, "EVIDENCE", "p", "C.pdf", null, pdf);
        s.runScheduled();
        StoreVerifyRun r = runRows.values().iterator().next();
        assertEquals(StoreVerifyRun.SCHEDULED, r.getTriggerType());
        assertEquals(1, r.getChecked());
        assertNull(r.getTriggeredBy());
    }

    @Test
    void aRecoveredFileGoesBackToOkAndCanAlertAgainLater() throws IOException {
        DocumentStoreImpl s = store(false);
        String key = s.put(tenant, "EVIDENCE", "p", "C.pdf", null, pdf);
        byte[] original = storage.files.get(key).clone();
        storage.files.get(key)[3] = 'Z';
        s.verifyNow(10, null);
        storage.files.put(key, original);          // restored from backup
        s.verifyNow(10, null);
        assertEquals("OK", row(key).getVerifyStatus());
        assertEquals(0, row(key).getFailedChecks());
        storage.files.remove(key);
        s.verifyNow(10, null);
        assertEquals(2, alerts.size());
    }

    @Test
    void healthWithNothingStoredYet() {
        DocumentStore.Health h = store(false).health();
        assertEquals(0, h.liveFiles());
        assertEquals(0, h.failingCount());
        assertNull(h.lastRun());
        assertNull(h.oldestCheckAt());
        assertEquals("database", h.provider());
        assertFalse(h.encryptionOn());
    }

    @Test
    void timestampsAreStoredToTheMicrosecond() throws IOException {
        DocumentStoreImpl s = store(false);
        String key = s.put(tenant, "EVIDENCE", "p", "C.pdf", null, pdf);
        Instant created = row(key).getCreatedAt();
        assertEquals(0, created.getNano() % 1000, "matches timestamptz precision, so the database guard sees no change");
    }
}
