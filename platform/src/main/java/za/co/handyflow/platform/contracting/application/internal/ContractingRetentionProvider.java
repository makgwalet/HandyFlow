package za.co.handyflow.platform.contracting.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.contracting.domain.model.Contract;
import za.co.handyflow.platform.contracting.domain.model.ContractSignature;
import za.co.handyflow.platform.contracting.domain.repository.ContractRepository;
import za.co.handyflow.platform.contracting.domain.repository.ContractSignatureRepository;
import za.co.handyflow.platform.documents.application.DocumentStore;
import za.co.handyflow.platform.documents.application.RetentionProvider;
import za.co.handyflow.platform.shared.TenantId;

import java.io.IOException;
import java.util.UUID;

/**
 * Contracts (module CONTRACTING, source type CONTRACT) for the records retention engine. The clock starts when a contract ends.
 * Disposal, once approved by a second person: the executed copy is deleted from the document store (which keeps its own
 * tombstone), the contract text is replaced by a note, and the drawn signatures and the signers' device details are cleared. The
 * contract record (number, title, parties, dates, body hash, executed-copy hash) and when each party signed stay.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class ContractingRetentionProvider implements RetentionProvider {

    private final ContractRepository contracts;
    private final ContractSignatureRepository signatures;
    private final DocumentStore store;

    @Override
    public String moduleCode() { return ContractRecordsService.MODULE; }

    @Override
    public Disposed dispose(TenantId tenantId, String sourceType, UUID sourceId, UUID batchId, UUID approvedBy) {
        if (!ContractRecordsService.SOURCE.equals(sourceType)) throw new IllegalArgumentException("Unknown Contracting record type " + sourceType);
        Contract c = contracts.findForUpdate(tenantId.getValue(), sourceId)
                .orElseThrow(() -> new IllegalStateException("The contract no longer exists"));
        if (c.isDisposed()) throw new IllegalStateException("The contract was already disposed of");
        if (!c.isEnded()) throw new IllegalStateException("The contract is in force again (status " + c.getStatus() + ")");
        int files = 0;
        if (c.hasExecutedCopy()) {
            try { store.delete(tenantId, c.getExecutedStorageKey(), "Disposed under the retention policy (request " + batchId + ")"); }
            catch (IOException e) { throw new IllegalStateException("The executed copy could not be removed from storage: " + e.getMessage()); }
            files = 1;
        }
        int scrubbed = 0;
        for (ContractSignature s : signatures.findByContract(c.getId())) {
            s.scrubForDisposal();
            signatures.save(s);
            scrubbed++;
        }
        c.dispose(batchId);
        contracts.save(c);
        log.info("Contract {} disposed (request {}): executed copy {}, {} signature(s) cleared", c.getContractNumber(), batchId,
                files == 1 ? "removed" : "none stored", scrubbed);
        String detail = (files == 1 ? "Executed copy removed (sha256 " + c.getExecutedSha256() + "); " : "No executed copy was stored; ")
                + "contract text and " + scrubbed + " drawn signature(s) cleared"
                + (c.getBodyHash() != null ? "; text sha256 " + c.getBodyHash() : "");
        return new Disposed(files, detail);
    }
}
