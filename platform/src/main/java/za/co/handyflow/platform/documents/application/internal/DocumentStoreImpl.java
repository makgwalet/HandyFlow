package za.co.handyflow.platform.documents.application.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import za.co.handyflow.platform.audittrail.application.AuditTrailFacade;
import za.co.handyflow.platform.documents.application.DocumentStore;
import za.co.handyflow.platform.documents.application.StoredFileIntegrityException;
import za.co.handyflow.platform.documents.domain.model.StoreRules;
import za.co.handyflow.platform.documents.domain.model.StoreRules.Status;
import za.co.handyflow.platform.documents.domain.model.StoreRules.Verdict;
import za.co.handyflow.platform.documents.domain.model.StoreVerifyRun;
import za.co.handyflow.platform.documents.domain.model.StoredObject;
import za.co.handyflow.platform.documents.domain.repository.StoreVerifyRunRepository;
import za.co.handyflow.platform.documents.domain.repository.StoredObjectRepository;
import za.co.handyflow.platform.shared.FieldEncryptionService;
import za.co.handyflow.platform.shared.FileStorageService;
import za.co.handyflow.platform.shared.SchedulerLease;
import za.co.handyflow.platform.shared.TenantId;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * See {@link DocumentStore}. Writes and reads run in the caller's transaction. Marking a file bad on a failed read commits in
 * its own transaction before the refusal is thrown, so the failure is recorded even when the caller's work rolls back or was
 * read-only. The check marks each file in its own short transaction, so one bad file never undoes the rest of the run.
 */
@Service
class DocumentStoreImpl implements DocumentStore {

    private static final Logger log = LoggerFactory.getLogger(DocumentStoreImpl.class);

    static final String LOCK = "document-store-verify";
    static final Duration MAX_HOLD = Duration.ofHours(2);
    static final String ALERT = "DOCUMENT_STORE_INTEGRITY_FAILED";
    static final int MAX_MANUAL = 5_000;

    private final StoredObjectRepository objects;
    private final StoreVerifyRunRepository runs;
    private final FileStorageService storage;
    private final FieldEncryptionService encryption;
    private final AuditTrailFacade trail;
    private final SchedulerLease lease;
    private final TransactionTemplate tx;
    private final TransactionTemplate newTx;
    private final boolean encrypt;
    private final long maxBytes;
    private final int batchSize;
    private final String provider;

    DocumentStoreImpl(StoredObjectRepository objects, StoreVerifyRunRepository runs, FileStorageService storage,
                      FieldEncryptionService encryption, AuditTrailFacade trail, SchedulerLease lease, PlatformTransactionManager txManager,
                      @Value("${app.documents.store.encrypt:false}") boolean encrypt,
                      @Value("${app.documents.store.max-bytes:20971520}") long maxBytes,
                      @Value("${app.documents.store.verify.batch-size:1000}") int batchSize,
                      @Value("${file-storage.provider:database}") String provider) {
        this.objects = objects;
        this.runs = runs;
        this.storage = storage;
        this.encryption = encryption;
        this.trail = trail;
        this.lease = lease;
        this.tx = new TransactionTemplate(txManager);
        this.newTx = new TransactionTemplate(txManager);
        this.newTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.encrypt = encrypt;
        this.maxBytes = Math.min(Math.max(maxBytes, 1L), StoreRules.DEFAULT_MAX_BYTES * 5);
        this.batchSize = Math.max(1, Math.min(batchSize, MAX_MANUAL));
        this.provider = provider == null || provider.isBlank() ? "database" : provider.trim();
        log.info("[DocumentStore] provider={} encryption={} max-bytes={} check batch={}", this.provider, encrypt ? "on" : "off", this.maxBytes, this.batchSize);
    }

    // ── Write, read, delete ──────────────────────────────────────────────────

    @Override
    public String put(TenantId tenantId, String ownerModule, String pathPrefix, String fileName, String contentType, byte[] content) throws IOException {
        if (tenantId == null) throw new IllegalArgumentException("A stored file needs a tenant.");
        StoreRules.checkSize(content, maxBytes);
        String module = StoreRules.module(ownerModule);
        UUID id = UUID.randomUUID();
        String sha = StoreRules.sha256(content);
        byte[] stored = encrypt ? encryption.sealBytes(content, StoreRules.aad(tenantId.getValue(), id)) : content;
        String storedSha = encrypt ? StoreRules.sha256(stored) : sha;
        String key = storage.store(pathPrefix, fileName, contentType, stored);
        objects.save(StoredObject.written(id, tenantId.getValue(), module, key, provider, contentType,
                content.length, sha, stored.length, storedSha, encrypt ? FieldEncryptionService.BYTES_KEY_ID : null));
        return key;
    }

    @Override
    public byte[] get(TenantId tenantId, String storageKey) throws IOException {
        StoredObject o = objects.findByStorageKey(storageKey).orElse(null);
        if (o == null) return storage.retrieve(storageKey);          // written before the store existed
        requireLive(tenantId, o);
        if (o.isEncrypted() && !FieldEncryptionService.BYTES_KEY_ID.equals(o.getKeyId()))
            throw new IOException("This file was encrypted with key " + o.getKeyId() + ", which this server does not hold");
        byte[] stored = storage.retrieve(storageKey);
        byte[] plain;
        if (o.isEncrypted()) {
            try { plain = encryption.openBytes(stored, StoreRules.aad(o.getTenantId(), o.getId())); }
            catch (RuntimeException e) { throw refused(o, "The encrypted file failed its integrity check when it was read"); }
        } else {
            plain = stored;
        }
        if (!o.getSha256().equalsIgnoreCase(StoreRules.sha256(plain)))
            throw refused(o, "The file no longer matched its hash when it was read");
        return plain;
    }

    @Override
    public boolean exists(TenantId tenantId, String storageKey) {
        StoredObject o = objects.findByStorageKey(storageKey).orElse(null);
        if (o != null) return tenantId != null && o.getTenantId().equals(tenantId.getValue()) && !o.isDeleted();
        try { storage.retrieve(storageKey); return true; }
        catch (IOException e) { return false; }
    }

    @Override
    public void delete(TenantId tenantId, String storageKey, String reason) throws IOException {
        StoredObject o = objects.findByStorageKey(storageKey).orElse(null);
        if (o == null) { storage.delete(storageKey); return; }
        if (tenantId == null || !o.getTenantId().equals(tenantId.getValue())) {
            log.warn("[DocumentStore] Delete of a file that belongs to another tenant was ignored: object={}", o.getId());
            return;
        }
        if (o.isDeleted()) return;
        storage.delete(storageKey);
        o.markDeleted(reason, Instant.now());
        objects.save(o);
    }

    /** Another tenant's file, or a deleted one, reads exactly like a missing one: nothing about it is given away. */
    private void requireLive(TenantId tenantId, StoredObject o) throws IOException {
        if (tenantId == null || !o.getTenantId().equals(tenantId.getValue())) {
            log.warn("[DocumentStore] A read named a file of another tenant and was refused: object={}", o.getId());
            throw new IOException("No file found for storage key");
        }
        if (o.isDeleted()) throw new IOException("No file found for storage key");
    }

    /** Records the failure in its own transaction (so it sticks), raises the alert once, and returns the refusal to throw. */
    private StoredFileIntegrityException refused(StoredObject o, String detail) {
        log.error("[DocumentStore] INTEGRITY FAILURE on read: object={} module={} tenant={}: {}", o.getId(), o.getOwnerModule(), o.getTenantId(), detail);
        try { record(o.getId(), new Verdict(Status.MISMATCH, detail), "read"); }
        catch (RuntimeException e) { log.error("[DocumentStore] Could not record the integrity failure of object={}: {}", o.getId(), e.getMessage()); }
        return new StoredFileIntegrityException();
    }

    /** Saves a check result in its own transaction and raises the alert when the file has just gone bad. */
    private void record(UUID objectId, Verdict v, String where) {
        newTx.executeWithoutResult(s -> objects.findById(objectId).ifPresent(fresh -> {
            boolean newlyBad = fresh.checked(v, Instant.now());
            objects.save(fresh);
            if (newlyBad) alert(fresh, v, where);
        }));
    }

    private void alert(StoredObject o, Verdict v, String where) {
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("module", o.getOwnerModule());
        after.put("status", v.status().name());
        after.put("detail", v.detail() == null ? "" : v.detail());
        after.put("foundBy", where);
        after.put("sha256", o.getSha256());
        trail.recordAs(TenantId.of(o.getTenantId()), "SYSTEM", null, "Document store check", ALERT, "STORED_FILE", o.getId(), null, after);
    }

    // ── Health ───────────────────────────────────────────────────────────────

    @Override
    public Health health() {
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (Status s : Status.values()) byStatus.put(s.name(), 0L);
        long live = 0;
        for (Object[] row : objects.countLiveByStatus()) {
            long n = ((Number) row[1]).longValue();
            byStatus.put(String.valueOf(row[0]), n);
            live += n;
        }
        List<Failing> failing = objects.findFailing(PageRequest.of(0, 50)).stream()
                .map(o -> new Failing(o.getId(), o.getTenantId(), o.getOwnerModule(), o.getStorageKey(), o.getVerifyStatus(), o.getVerifyDetail(),
                        o.getLastVerifiedAt(), o.getFailedChecks()))
                .toList();
        RunSummary last = runs.findFirstByOrderByStartedAtDesc().map(DocumentStoreImpl::summary).orElse(null);
        return new Health(provider, encrypt, live, objects.countLiveEncrypted(), byStatus, objects.oldestCheck().orElse(null), last, failing);
    }

    /** The last finished run, for the actuator health indicator. */
    RunSummary lastFinishedRun() {
        return runs.findFirstByFinishedAtIsNotNullOrderByStartedAtDesc().map(DocumentStoreImpl::summary).orElse(null);
    }

    // ── The check ────────────────────────────────────────────────────────────

    @Override
    public RunSummary verifyNow(int limit, UUID triggeredBy) {
        int n = Math.max(1, Math.min(limit, MAX_MANUAL));
        AtomicReference<RunSummary> out = new AtomicReference<>();
        boolean ran = lease.runExclusively(LOCK, MAX_HOLD, () -> out.set(run(n, StoreVerifyRun.MANUAL, triggeredBy)));
        if (!ran) throw new IllegalStateException("A document store check is already running. Try again when it has finished.");
        return out.get();
    }

    /** The nightly run. Skipped quietly when another instance holds the lease. */
    void runScheduled() {
        lease.runExclusively(LOCK, MAX_HOLD, () -> run(batchSize, StoreVerifyRun.SCHEDULED, null));
    }

    RunSummary run(int limit, String trigger, UUID triggeredBy) {
        StoreVerifyRun started = tx.execute(s -> runs.save(StoreVerifyRun.start(trigger, triggeredBy)));
        UUID runId = started.getId();
        String canary = canary();
        int checked = 0, ok = 0, failed = 0;
        for (UUID id : objects.findDue(PageRequest.of(0, limit))) {
            Status s;
            try { s = verifyOne(id); }
            catch (RuntimeException e) {
                log.error("[DocumentStore] Checking object={} failed unexpectedly: {}", id, e.getMessage());
                continue;
            }
            if (s == null) continue;
            checked++;
            if (s == Status.OK) ok++; else failed++;
        }
        final int c = checked, k = ok, f = failed;
        StoreVerifyRun finished = tx.execute(s -> {
            StoreVerifyRun r = runs.findById(runId).orElseThrow();
            r.canary(canary == null, canary == null ? "Write, read and delete worked" : canary);
            r.finish(c, k, f);
            return runs.save(r);
        });
        if (f > 0 || canary != null)
            log.error("[DocumentStore] Check {}: {} checked, {} failed, storage round trip {}", runId, c, f, canary == null ? "ok" : "FAILED: " + canary);
        else
            log.info("[DocumentStore] Check {}: {} checked, all intact", runId, c);
        return summary(finished);
    }

    /** Checks one file and records the result. Null when the file is gone from the registry or deleted since it was listed. */
    Status verifyOne(UUID id) {
        StoredObject o = objects.findById(id).orElse(null);
        if (o == null || o.isDeleted()) return null;
        Verdict v;
        if (o.isEncrypted() && !FieldEncryptionService.BYTES_KEY_ID.equals(o.getKeyId())) {
            v = new Verdict(Status.UNREADABLE, "Encrypted with key " + o.getKeyId() + ", which this server does not hold");
        } else {
            try {
                byte[] stored = storage.retrieve(o.getStorageKey());
                v = StoreRules.judge(stored, o.getStoredSha256(), o.isEncrypted(),
                        b -> encryption.openBytes(b, StoreRules.aad(o.getTenantId(), o.getId())), o.getSha256());
            } catch (IOException e) {
                v = new Verdict(StoreRules.readFailure(e), e.getMessage());
            } catch (RuntimeException e) {
                v = new Verdict(Status.UNREADABLE, e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }
        record(o.getId(), v, "check");
        return v.status();
    }

    /** Writes, reads back and deletes a small file straight on the provider. Null when that worked, otherwise what went wrong. */
    String canary() {
        byte[] probe = ("document-store-canary " + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8);
        String key = null;
        try {
            key = storage.store("health/document-store", "canary.txt", "text/plain", probe);
            byte[] back = storage.retrieve(key);
            if (!Arrays.equals(probe, back)) return "The provider returned different bytes from those written";
            return null;
        } catch (Exception e) {
            return StoreRules.clip(e.getClass().getSimpleName() + ": " + e.getMessage(), 300);
        } finally {
            if (key != null) {
                try { storage.delete(key); }
                catch (Exception e) { log.warn("[DocumentStore] Could not remove the check file {}: {}", key, e.getMessage()); }
            }
        }
    }

    private static RunSummary summary(StoreVerifyRun r) {
        return new RunSummary(r.getId(), r.getTriggerType(), r.getStartedAt(), r.getFinishedAt(), r.getChecked(), r.getOk(), r.getFailed(),
                r.getCanaryOk(), r.getCanaryDetail());
    }
}
