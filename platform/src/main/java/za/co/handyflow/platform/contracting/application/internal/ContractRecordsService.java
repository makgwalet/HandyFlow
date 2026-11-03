package za.co.handyflow.platform.contracting.application.internal;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import za.co.handyflow.platform.contracting.domain.model.Contract;
import za.co.handyflow.platform.contracting.domain.model.ContractParty;
import za.co.handyflow.platform.contracting.domain.model.ContractSignature;
import za.co.handyflow.platform.contracting.domain.repository.ContractPartyRepository;
import za.co.handyflow.platform.contracting.domain.repository.ContractRepository;
import za.co.handyflow.platform.contracting.domain.repository.ContractSignatureRepository;
import za.co.handyflow.platform.documents.application.DocumentStore;
import za.co.handyflow.platform.documents.application.RetentionFacade;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.SchedulerLease;
import za.co.handyflow.platform.shared.TenantId;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The signed contract as a record: one frozen executed copy (the PDF rendered once and kept in the document store, so its hash
 * never changes and it can be shared through a secure link), and its place in the records retention engine (tracked once signed,
 * clock started when it ends). Signing itself never waits on any of this: the copy is made after the signing transaction commits,
 * and a nightly sync catches up anything that was missed (a failed render, a contract that expired overnight).
 */
@Slf4j
@Service
public class ContractRecordsService {

    public static final String MODULE = "CONTRACTING";
    public static final String SOURCE = "CONTRACT";
    static final int SYNC_BATCH = 500;

    private final ContractRepository contracts;
    private final ContractPartyRepository parties;
    private final ContractSignatureRepository signatures;
    private final ContractPdfGenerator pdf;
    private final DocumentStore store;
    private final RetentionFacade retention;
    private final SchedulerLease lease;
    private final TransactionTemplate newTx;

    ContractRecordsService(ContractRepository contracts, ContractPartyRepository parties, ContractSignatureRepository signatures,
                           ContractPdfGenerator pdf, DocumentStore store, RetentionFacade retention, SchedulerLease lease,
                           PlatformTransactionManager txManager) {
        this.contracts = contracts;
        this.parties = parties;
        this.signatures = signatures;
        this.pdf = pdf;
        this.store = store;
        this.retention = retention;
        this.lease = lease;
        this.newTx = new TransactionTemplate(txManager);
        this.newTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // ── Hooks from ContractingService ────────────────────────────────────────

    /** The last party signed. After the signing commits: track the contract for retention and store its executed copy. */
    public void contractSigned(TenantId tenantId, UUID contractId) {
        Runnable work = () -> {
            try {
                newTx.executeWithoutResult(s -> {
                    Contract c = contracts.findForUpdate(tenantId.getValue(), contractId).orElse(null);
                    if (c == null) return;
                    track(tenantId, c);
                    if (!c.hasExecutedCopy()) storeExecutedCopy(tenantId, c);
                });
            } catch (RuntimeException e) {
                log.error("Contract {}: the executed copy could not be stored now ({}); the nightly sync will retry", contractId, e.getMessage());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { work.run(); }
            });
        } else {
            work.run();
        }
    }

    /** Terminated (or otherwise ended) now, in the caller's transaction: the retention clock starts today. */
    public void contractEnded(TenantId tenantId, Contract c) {
        if (!c.isEnded() || c.getRetentionClockAt() != null) return;
        track(tenantId, c);
        retention.startClock(tenantId, MODULE, SOURCE, List.of(c.getId()), c.endedOn());
        c.markRetentionClockStarted();
        contracts.save(c);
    }

    // ── The executed copy ────────────────────────────────────────────────────

    /** Makes sure a signed contract has its executed copy, in the caller's transaction. Returns the contract (locked). */
    public Contract ensureExecutedCopy(TenantId tenantId, UUID contractId) {
        Contract c = contracts.findForUpdate(tenantId.getValue(), contractId)
                .orElseThrow(() -> new HandyFlowException("Contract not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
        if (c.isDisposed()) throw disposed();
        if (!c.isExecuted()) throw new HandyFlowException("Only a signed contract can be shared", HttpStatus.CONFLICT, "NOT_SIGNED");
        if (!c.hasExecutedCopy()) storeExecutedCopy(tenantId, c);
        return c;
    }

    /** The stored executed copy, hash-checked by the document store and against the hash recorded on the contract. */
    public byte[] executedCopy(TenantId tenantId, Contract c) {
        if (c.isDisposed()) throw disposed();
        if (!c.hasExecutedCopy()) throw new HandyFlowException("This contract has no stored executed copy yet", HttpStatus.CONFLICT, "NO_EXECUTED_COPY");
        byte[] bytes;
        try { bytes = store.get(tenantId, c.getExecutedStorageKey()); }
        catch (IOException e) {
            log.error("Contract {}: executed copy could not be read: {}", c.getId(), e.getMessage());
            throw new HandyFlowException("The executed copy could not be read from storage", HttpStatus.INTERNAL_SERVER_ERROR, "STORAGE_FAILED");
        }
        if (!c.getExecutedSha256().equalsIgnoreCase(sha256(bytes))) {
            log.error("INTEGRITY FAILURE: contract {} executed copy no longer matches its recorded hash", c.getId());
            throw new HandyFlowException("The executed copy no longer matches the record made when it was stored, so it was not released.",
                    HttpStatus.CONFLICT, "FILE_INTEGRITY");
        }
        return bytes;
    }

    private void storeExecutedCopy(TenantId tenantId, Contract c) {
        c.setParties(parties.findByContract(c.getId()));
        List<ContractSignature> sigs = signatures.findByContract(c.getId());
        byte[] rendered = pdf.generate(c, sigs, "HandyFlow Tenant", null);
        if (rendered == null || rendered.length == 0) throw new IllegalStateException("The contract PDF could not be rendered");
        String key;
        try {
            key = store.put(tenantId, MODULE, "contracting/" + tenantId.getValue() + "/" + c.getId(), fileName(c), "application/pdf", rendered);
        } catch (IOException e) {
            throw new HandyFlowException("The executed copy could not be stored: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, "STORAGE_FAILED");
        }
        c.recordExecutedCopy(key, sha256(rendered), rendered.length);
        contracts.save(c);
        log.info("Contract {}: executed copy stored ({} bytes)", c.getContractNumber(), rendered.length);
    }

    // ── Retention ────────────────────────────────────────────────────────────

    void track(TenantId tenantId, Contract c) {
        retention.track(tenantId, MODULE, SOURCE, c.getId(), c.getContractType(), c.getContractNumber(), c.getTitle(), counterparties(c));
    }

    private String counterparties(Contract c) {
        List<ContractParty> ps = c.getParties() == null || c.getParties().isEmpty() ? parties.findByContract(c.getId()) : c.getParties();
        String names = ps.stream().filter(p -> "EXTERNAL".equals(p.getPartyType()))
                .map(p -> p.getCompanyName() != null && !p.getCompanyName().isBlank() ? p.getCompanyName() : p.getFullName())
                .collect(Collectors.joining(", "));
        return names.isBlank() ? null : names;
    }

    /** Nightly: start the clock on contracts that ended (expired overnight, archived) and store any missing executed copy. */
    @Scheduled(cron = "${app.contracting.records-sync.cron:0 20 3 * * *}", zone = "Africa/Johannesburg")
    void nightly() {
        try { lease.runExclusively("contracting-records-sync", Duration.ofHours(1), this::syncAll); }
        catch (RuntimeException e) { log.error("Contracting records sync did not complete: {}", e.getMessage(), e); }
    }

    /** Each contract in its own transaction; one that fails is logged and retried the next night. Returns how many were done. */
    int syncAll() {
        int done = 0;
        for (Contract c : contracts.findEndedWithoutRetentionClock(PageRequest.of(0, SYNC_BATCH))) {
            UUID id = c.getId(); TenantId t = TenantId.of(c.getTenantId());
            try {
                newTx.executeWithoutResult(s -> contracts.findForUpdate(t.getValue(), id).ifPresent(x -> contractEnded(t, x)));
                done++;
            } catch (RuntimeException e) { log.warn("Contract {}: retention clock not started: {}", id, e.getMessage()); }
        }
        for (Contract c : contracts.findExecutedWithoutCopy(PageRequest.of(0, SYNC_BATCH))) {
            UUID id = c.getId(); TenantId t = TenantId.of(c.getTenantId());
            try {
                newTx.executeWithoutResult(s -> contracts.findForUpdate(t.getValue(), id).ifPresent(x -> {
                    track(t, x);
                    if (!x.hasExecutedCopy() && !x.isDisposed()) storeExecutedCopy(t, x);
                }));
                done++;
            } catch (RuntimeException e) { log.warn("Contract {}: executed copy not stored: {}", id, e.getMessage()); }
        }
        if (done > 0) log.info("Contracting records sync: {} contract(s) brought up to date", done);
        return done;
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    static String fileName(Contract c) {
        String n = c.getContractNumber() == null || c.getContractNumber().isBlank() ? c.getId().toString() : c.getContractNumber();
        return n.replaceAll("[^A-Za-z0-9._-]", "_") + ".pdf";
    }

    static HandyFlowException disposed() {
        return new HandyFlowException("This contract was disposed of under the records retention policy. The record of it is kept.",
                HttpStatus.GONE, "DISPOSED");
    }

    static String sha256(byte[] b) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b)); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
