package za.co.handyflow.platform.contracting.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.contracting.domain.model.*;
import za.co.handyflow.platform.contracting.domain.repository.ContractChangeProposalRepository;
import za.co.handyflow.platform.contracting.domain.repository.ContractClauseRepository;
import za.co.handyflow.platform.contracting.domain.repository.ContractClauseUsageRepository;
import za.co.handyflow.platform.contracting.domain.repository.ContractRepository;
import za.co.handyflow.platform.contracting.domain.repository.ContractVersionRepository;
import za.co.handyflow.platform.contracting.dto.ContractRedlineDtos.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.*;

/**
 * Version history and redlining for contract bodies. Every change to a body is a numbered version. A body is changed by
 * proposing a revised text; a reviewer then accepts or rejects it block by block. Only contracts that are still editable can
 * change, so a sent or signed contract's text never moves.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContractRedlineService {

    private final ContractRepository contractRepo;
    private final ContractVersionRepository versionRepo;
    private final ContractChangeProposalRepository proposalRepo;
    private final ContractClauseRepository clauseRepo;
    private final ContractClauseUsageRepository clauseUsageRepo;

    // ── Versions ─────────────────────────────────────────────────────────────

    /** Records the contract's current body as the next version. Called by the contract service when it creates or edits a body. */
    @Transactional
    public int snapshot(TenantId tenantId, Contract c, String source, String note, UUID by) {
        int next = versionRepo.latestVersionNo(tenantId.getValue(), c.getId()) + 1;
        versionRepo.save(ContractVersion.of(tenantId.getValue(), c.getId(), next, c.getBody(), source, note, by));
        return next;
    }

    /** Contracts that predate version history get their current body recorded as version 1 the first time it is needed. */
    private int ensureBaseline(TenantId tenantId, Contract c) {
        int latest = versionRepo.latestVersionNo(tenantId.getValue(), c.getId());
        return latest > 0 ? latest : snapshot(tenantId, c, "BASELINE", "Recorded when version history was first used", null);
    }

    @Transactional
    public List<VersionInfo> versions(TenantId tenantId, UUID contractId) {
        Contract c = contract(tenantId, contractId);
        ensureBaseline(tenantId, c);
        return versionRepo.findByTenantIdAndContractIdOrderByVersionNoDesc(tenantId.getValue(), contractId).stream()
                .map(v -> new VersionInfo(v.getVersionNo(), v.getSource(), v.getNote(), v.getCreatedBy(), v.getCreatedAt())).toList();
    }

    @Transactional(readOnly = true)
    public VersionBody version(TenantId tenantId, UUID contractId, int versionNo) {
        contract(tenantId, contractId);
        ContractVersion v = versionOf(tenantId, contractId, versionNo);
        return new VersionBody(v.getVersionNo(), v.getSource(), v.getNote(), v.getCreatedAt(), v.getBody());
    }

    @Transactional(readOnly = true)
    public DiffResponse compare(TenantId tenantId, UUID contractId, int from, int to) {
        contract(tenantId, contractId);
        List<BodyDiff.Segment> segments = BodyDiff.diff(versionOf(tenantId, contractId, from).getBody(), versionOf(tenantId, contractId, to).getBody());
        return new DiffResponse(from, to, BodyDiff.hunkCount(segments), segments);
    }

    // ── Proposals ────────────────────────────────────────────────────────────

    @Transactional
    public ProposalView propose(TenantId tenantId, UUID contractId, String body, String note, UUID by) {
        return propose(tenantId, contractId, body, note, by, null);
    }

    /** {@code clauseId} is set when the proposal inserts a library clause; applying it then records the clause as used in the contract. */
    @Transactional
    public ProposalView propose(TenantId tenantId, UUID contractId, String body, String note, UUID by, UUID clauseId) {
        Contract c = lock(tenantId, contractId);
        requireEditable(c);
        int base = ensureBaseline(tenantId, c);
        String clean = HtmlSanitizer.clean(body);
        if (BodyDiff.hunkCount(BodyDiff.diff(c.getBody(), clean)) == 0)
            throw new IllegalArgumentException("The proposed text is the same as the current text");
        ContractChangeProposal p = proposalRepo.save(clauseId == null
                ? ContractChangeProposal.create(tenantId.getValue(), contractId, base, clean, note, by)
                : ContractChangeProposal.forClause(tenantId.getValue(), contractId, base, clean, note, by, clauseId));
        log.info("Change proposal {} made on contract={} (base v{})", p.getId(), c.getContractNumber(), base);
        return view(tenantId, p, c.getBody(), base);
    }

    @Transactional(readOnly = true)
    public List<ProposalView> proposals(TenantId tenantId, UUID contractId) {
        contract(tenantId, contractId);
        int latest = versionRepo.latestVersionNo(tenantId.getValue(), contractId);
        return proposalRepo.findByTenantIdAndContractIdOrderByCreatedAtDesc(tenantId.getValue(), contractId).stream()
                .map(p -> view(tenantId, p, versionOf(tenantId, contractId, p.getBaseVersionNo()).getBody(), latest)).toList();
    }

    @Transactional(readOnly = true)
    public ProposalView proposal(TenantId tenantId, UUID proposalId) {
        ContractChangeProposal p = proposalOf(tenantId, proposalId);
        int latest = versionRepo.latestVersionNo(tenantId.getValue(), p.getContractId());
        return view(tenantId, p, versionOf(tenantId, p.getContractId(), p.getBaseVersionNo()).getBody(), latest);
    }

    /** Applies the accepted hunks to the contract and records the result as a new version. Hunks not accepted stay as they are. */
    @Transactional
    public ProposalView apply(TenantId tenantId, UUID proposalId, List<Integer> acceptedHunks, String note, UUID by) {
        ContractChangeProposal p = proposalOf(tenantId, proposalId);
        Contract c = lock(tenantId, p.getContractId());
        if (!p.isOpen()) throw new IllegalStateException("This proposal was already decided (" + p.getStatus() + ")");
        requireEditable(c);
        int latest = versionRepo.latestVersionNo(tenantId.getValue(), c.getId());
        if (latest != p.getBaseVersionNo())
            throw new IllegalStateException("The contract has changed since this proposal was made. Make a new proposal against the current text.");
        Set<Integer> accepted = new TreeSet<>(acceptedHunks == null ? List.of() : acceptedHunks);
        if (accepted.isEmpty()) throw new IllegalArgumentException("Accept at least one change, or reject the proposal");
        int hunks = BodyDiff.hunkCount(BodyDiff.diff(c.getBody(), p.getProposedBody()));
        if (accepted.stream().anyMatch(h -> h < 0 || h >= hunks)) throw new IllegalArgumentException("Unknown change in the accepted list");

        c.updateBody(BodyDiff.merge(c.getBody(), p.getProposedBody(), accepted));
        contractRepo.save(c);
        int version = snapshot(tenantId, c, "PROPOSAL_APPLIED", "Applied " + accepted.size() + " of " + hunks + " proposed changes", by);
        p.markApplied(accepted, version, note, by);
        if (p.getSourceClauseId() != null)
            clauseRepo.findByTenantIdAndId(tenantId.getValue(), p.getSourceClauseId())
                    .ifPresent(cl -> clauseUsageRepo.save(ContractClauseUsage.of(tenantId.getValue(), c.getId(), cl, p.getId(), version, by)));
        proposalRepo.save(p);
        log.info("Change proposal {} applied to contract={} as v{} ({} of {} changes)", p.getId(), c.getContractNumber(), version, accepted.size(), hunks);
        return view(tenantId, p, versionOf(tenantId, c.getId(), p.getBaseVersionNo()).getBody(), version);
    }

    @Transactional
    public ProposalView reject(TenantId tenantId, UUID proposalId, String reason, UUID by) {
        ContractChangeProposal p = proposalOf(tenantId, proposalId);
        p.markRejected(reason, by);
        proposalRepo.save(p);
        int latest = versionRepo.latestVersionNo(tenantId.getValue(), p.getContractId());
        return view(tenantId, p, versionOf(tenantId, p.getContractId(), p.getBaseVersionNo()).getBody(), latest);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private ProposalView view(TenantId tenantId, ContractChangeProposal p, String baseBody, int latestVersion) {
        List<BodyDiff.Segment> segments = BodyDiff.diff(baseBody, p.getProposedBody());
        return new ProposalView(p.getId(), p.getContractId(), p.getBaseVersionNo(), p.getStatus(), p.getNote(), p.getProposedBy(), p.getCreatedAt(),
                p.getDecidedAt(), p.getDecisionNote(), p.getAcceptedHunks(), p.getAppliedVersionNo(),
                p.isOpen() && latestVersion != p.getBaseVersionNo(), BodyDiff.hunkCount(segments), segments);
    }

    private static void requireEditable(Contract c) {
        if (!"DRAFT".equals(c.getStatus()) && !"UNDER_REVIEW".equals(c.getStatus()))
            throw new IllegalStateException("Only a draft or a contract under review can be changed (this one is " + c.getStatus() + ")");
    }

    private Contract contract(TenantId tenantId, UUID id) {
        return contractRepo.findActiveById(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Contract", id.toString()));
    }

    private Contract lock(TenantId tenantId, UUID id) {
        return contractRepo.findForUpdate(tenantId.getValue(), id).orElseThrow(() -> new ResourceNotFoundException("Contract", id.toString()));
    }

    private ContractVersion versionOf(TenantId tenantId, UUID contractId, int no) {
        return versionRepo.findByTenantIdAndContractIdAndVersionNo(tenantId.getValue(), contractId, no)
                .orElseThrow(() -> new ResourceNotFoundException("Contract version", String.valueOf(no)));
    }

    private ContractChangeProposal proposalOf(TenantId tenantId, UUID id) {
        return proposalRepo.findByTenantIdAndId(tenantId.getValue(), id).orElseThrow(() -> new ResourceNotFoundException("Change proposal", id.toString()));
    }
}
