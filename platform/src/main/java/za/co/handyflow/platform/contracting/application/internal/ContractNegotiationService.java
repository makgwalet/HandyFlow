package za.co.handyflow.platform.contracting.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.contracting.domain.model.*;
import za.co.handyflow.platform.contracting.domain.repository.*;
import za.co.handyflow.platform.contracting.dto.ContractNegotiationDtos.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Counterparty negotiation of a contract's wording. A counterparty can only <em>propose</em>; the contract body changes only when
 * staff accept proposals and apply them, which records a new draft revision. Signing is blocked until the negotiation is formally
 * ready (or closed) and the wording is still the pinned final revision. Every action is written to the negotiation's audit trail.
 * <p>
 * Locking order is always contract row, then negotiation row, so concurrent proposals, decisions and applications serialise safely.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContractNegotiationService {

    private static final ZoneId ZONE = ZoneId.of("Africa/Johannesburg");
    private static final int MAX_OPEN_PER_PARTY = 100;

    private final ContractRepository contractRepo;
    private final ContractPartyRepository partyRepo;
    private final ContractNegotiationRepository negotiationRepo;
    private final ContractNegotiationProposalRepository proposalRepo;
    private final ContractNegotiationCommentRepository commentRepo;
    private final ContractNegotiationEventRepository eventRepo;
    private final ContractNegotiationLinkRepository linkRepo;
    private final ContractRedlineService redline;
    private final SigningTokenService hashing;
    private final ApplicationEventPublisher publisher;   // tells staff, after the transaction commits, that a counterparty acted

    // ── Staff: lifecycle ─────────────────────────────────────────────────────

    @Transactional
    public NegotiationView open(TenantId tenantId, UUID contractId, LocalDate deadline, UUID by) {
        Contract c = lockContract(tenantId, contractId);
        requireEditable(c);
        if (partyRepo.findByContract(contractId).isEmpty()) throw new IllegalStateException("Add at least one party before opening a negotiation");
        if (negotiationRepo.findByTenantIdAndContractId(tenantId.getValue(), contractId).isPresent())
            throw new IllegalStateException("This contract already has a negotiation. Reopen it instead.");
        int version = latestVersion(tenantId, c);
        ContractNegotiation n = negotiationRepo.save(ContractNegotiation.open(tenantId.getValue(), contractId, version, deadline, by, today()));
        event(n, "STAFF", by, null, "OPENED", deadline == null ? "No response deadline" : "Respond by " + deadline, null);
        return view(n, proposalRepo.findByTenantIdAndContractIdOrderByCreatedAtAsc(tenantId.getValue(), contractId));
    }

    @Transactional
    public NegotiationView setDeadline(TenantId tenantId, UUID contractId, LocalDate deadline, UUID by) {
        lockContract(tenantId, contractId);
        ContractNegotiation n = lockNegotiation(tenantId, contractId);
        n.setDeadline(deadline, today());
        negotiationRepo.save(n);
        event(n, "STAFF", by, null, "DEADLINE_SET", deadline == null ? "Deadline removed" : "Respond by " + deadline, null);
        return view(n, proposalRepo.findByTenantIdAndContractIdOrderByCreatedAtAsc(tenantId.getValue(), contractId));
    }

    @Transactional
    public NegotiationView markReady(TenantId tenantId, UUID contractId, UUID by) {
        Contract c = lockContract(tenantId, contractId);
        requireEditable(c);
        ContractNegotiation n = lockNegotiation(tenantId, contractId);
        List<ContractNegotiationProposal> all = reconcile(n, c);
        int version = latestVersion(tenantId, c);
        n.markReady(count(all, ContractNegotiationProposal::isUnresolved), count(all, p -> ContractNegotiationProposal.ACCEPTED.equals(p.getStatus())),
                version, hashOf(c), by);
        negotiationRepo.save(n);
        revokeLinks(n, "Negotiation marked ready for signature");
        event(n, "STAFF", by, null, "READY_FOR_SIGNATURE", "Final wording pinned at revision " + version, null);
        return view(n, all);
    }

    /** Closes the discussion. Everything must be decided and applied first, so nothing is closed over a pending request. */
    @Transactional
    public NegotiationView close(TenantId tenantId, UUID contractId, String reason, UUID by) {
        Contract c = lockContract(tenantId, contractId);
        ContractNegotiation n = lockNegotiation(tenantId, contractId);
        List<ContractNegotiationProposal> all = n.isActive() ? reconcile(n, c) : proposalRepo.findByTenantIdAndContractIdOrderByCreatedAtAsc(tenantId.getValue(), contractId);
        long unresolved = count(all, ContractNegotiationProposal::isUnresolved), accepted = count(all, p -> ContractNegotiationProposal.ACCEPTED.equals(p.getStatus()));
        if (unresolved > 0) throw new IllegalStateException(unresolved + " proposal(s) still need a decision. Accept or reject them before closing.");
        if (accepted > 0) throw new IllegalStateException(accepted + " accepted proposal(s) have not been applied. Apply them before closing.");
        n.close(reason == null || reason.isBlank() ? "Closed by staff" : reason.trim(), by);
        negotiationRepo.save(n);
        revokeLinks(n, "Negotiation closed");
        event(n, "STAFF", by, null, "CLOSED", n.getCloseReason(), null);
        return view(n, all);
    }

    @Transactional
    public NegotiationView reopen(TenantId tenantId, UUID contractId, LocalDate deadline, UUID by) {
        Contract c = lockContract(tenantId, contractId);
        requireEditable(c);
        ContractNegotiation n = lockNegotiation(tenantId, contractId);
        n.reopen();
        if (deadline != null) n.setDeadline(deadline, today());
        negotiationRepo.save(n);
        event(n, "STAFF", by, null, "REOPENED", deadline == null ? null : "Respond by " + deadline, null);
        return view(n, reconcile(n, c));
    }

    // ── Staff: decisions ─────────────────────────────────────────────────────

    @Transactional
    public ProposalView accept(TenantId tenantId, UUID contractId, UUID proposalId, String note, UUID by) {
        Contract c = lockContract(tenantId, contractId);
        requireEditable(c);
        ContractNegotiation n = lockNegotiation(tenantId, contractId);
        reconcile(n, c);
        ContractNegotiationProposal p = proposalOf(tenantId, contractId, proposalId);
        if (!NegotiationEngine.targetExists(c.getBody(), p))
            throw new IllegalStateException("The wording this proposal changes has since changed. Reject it and ask the counterparty to propose again.");
        p.accept(note, by);
        proposalRepo.save(p);
        reconcile(n, c);
        event(n, "STAFF", by, p.getPartyId(), "PROPOSAL_ACCEPTED", describe(p), null);
        return proposalView(p, c.getBody(), nameOf(contractId).get(p.getPartyId()));
    }

    @Transactional
    public ProposalView reject(TenantId tenantId, UUID contractId, UUID proposalId, String reason, UUID by) {
        Contract c = lockContract(tenantId, contractId);
        ContractNegotiation n = lockNegotiation(tenantId, contractId);
        ContractNegotiationProposal p = proposalOf(tenantId, contractId, proposalId);
        p.reject(reason, by);
        proposalRepo.save(p);
        if (n.isActive()) reconcile(n, c);
        event(n, "STAFF", by, p.getPartyId(), "PROPOSAL_REJECTED", describe(p) + " — " + p.getDecisionNote(), null);
        return proposalView(p, c.getBody(), nameOf(contractId).get(p.getPartyId()));
    }

    /**
     * Applies every accepted proposal to the contract as one new draft revision. Proposals are located by the wording they were written
     * against, so edits made elsewhere since are kept. If any accepted proposal's wording has gone, nothing is applied.
     */
    @Transactional
    public StaffView applyAccepted(TenantId tenantId, UUID contractId, String note, UUID by) {
        Contract c = lockContract(tenantId, contractId);
        requireEditable(c);
        ContractNegotiation n = lockNegotiation(tenantId, contractId);
        if (!n.isActive()) throw new IllegalStateException("Accepted proposals can only be applied while the negotiation is open (it is " + n.getStatus() + ")");
        List<ContractNegotiationProposal> accepted = proposalRepo.findByTenantIdAndContractIdOrderByCreatedAtAsc(tenantId.getValue(), contractId).stream()
                .filter(p -> ContractNegotiationProposal.ACCEPTED.equals(p.getStatus())).toList();
        if (accepted.isEmpty()) throw new IllegalStateException("There are no accepted proposals to apply");
        c.updateBody(NegotiationEngine.apply(c.getBody(), accepted));
        contractRepo.save(c);
        int version = redline.snapshot(tenantId, c, "NEGOTIATION", note == null || note.isBlank() ? "Applied " + accepted.size() + " accepted proposal(s)" : note.trim(), by);
        for (ContractNegotiationProposal p : accepted) { p.markApplied(version, by); proposalRepo.save(p); }
        reconcile(n, c);
        event(n, "STAFF", by, null, "PROPOSALS_APPLIED", accepted.size() + " proposal(s) applied as revision " + version, null);
        log.info("Negotiation {}: applied {} proposal(s) to contract={} as v{}", n.getId(), accepted.size(), c.getContractNumber(), version);
        return staffView(tenantId, contractId);
    }

    @Transactional
    public CommentView staffComment(TenantId tenantId, UUID contractId, StaffCommentRequest q, UUID by) {
        Contract c = lockContract(tenantId, contractId);
        ContractNegotiation n = lockNegotiation(tenantId, contractId);
        if (n.getStatus().equals(ContractNegotiation.CLOSED)) throw new IllegalStateException("This negotiation is closed");
        UUID audience = q.audiencePartyId();
        if (q.proposalId() != null) audience = proposalOf(tenantId, contractId, q.proposalId()).getPartyId();
        if (audience != null) partyOf(contractId, audience);
        if (q.parentId() != null) commentRepo.findByTenantIdAndId(tenantId.getValue(), q.parentId()).filter(pc -> pc.getContractId().equals(contractId))
                .orElseThrow(() -> new ResourceNotFoundException("Comment", q.parentId().toString()));
        ContractNegotiationComment cm = commentRepo.save(ContractNegotiationComment.staff(tenantId.getValue(), contractId, n.getId(), by, q.internalOnly() ? null : audience,
                q.blockIndex(), q.blockExcerpt(), q.proposalId(), q.parentId(), q.body(), q.internalOnly()));
        event(n, "STAFF", by, audience, q.internalOnly() ? "INTERNAL_NOTE" : "STAFF_COMMENT", null, null);
        return commentView(cm);
    }

    // ── Staff: reading ───────────────────────────────────────────────────────

    @Transactional
    public StaffView staffView(TenantId tenantId, UUID contractId) {
        Contract c = contractOf(tenantId, contractId);
        Optional<ContractNegotiation> n = negotiationRepo.findByTenantIdAndContractId(tenantId.getValue(), contractId);
        int version = latestVersion(tenantId, c);
        if (n.isEmpty()) return new StaffView(null, version, c.getBody(), BodyDiff.blocks(c.getBody()), List.of(), List.of(), List.of());
        List<ContractNegotiationProposal> all = n.get().isActive() && isEditable(c) ? reconcile(n.get(), c)
                : proposalRepo.findByTenantIdAndContractIdOrderByCreatedAtAsc(tenantId.getValue(), contractId);
        Map<UUID, String> names = nameOf(contractId);
        return new StaffView(view(n.get(), all), version, c.getBody(), BodyDiff.blocks(c.getBody()),
                all.stream().map(p -> proposalView(p, c.getBody(), names.get(p.getPartyId()))).toList(),
                commentRepo.findByTenantIdAndContractIdOrderByCreatedAtAsc(tenantId.getValue(), contractId).stream().map(ContractNegotiationService::commentView).toList(),
                eventRepo.findTop200ByTenantIdAndContractIdOrderByCreatedAtDesc(tenantId.getValue(), contractId).stream().map(ContractNegotiationService::eventView).toList());
    }

    // ── Counterparty (called only after the access layer has verified the link and OTP) ─────

    @Transactional
    public ProposalView submitProposal(TenantId tenantId, UUID contractId, UUID partyId, ProposalInput in, String ip) {
        Contract c = lockContract(tenantId, contractId);
        requireEditable(c);
        ContractParty party = partyOf(contractId, partyId);
        ContractNegotiation n = lockNegotiation(tenantId, contractId);
        requireAcceptingInput(n);
        reconcile(n, c);
        long open = proposalRepo.findByTenantIdAndContractIdAndPartyIdOrderByCreatedAtAsc(tenantId.getValue(), contractId, partyId).stream().filter(ContractNegotiationProposal::isOpen).count();
        if (open >= MAX_OPEN_PER_PARTY) throw new IllegalStateException("Too many proposals are waiting for a decision. Wait for staff to respond.");
        ContractNegotiationProposal p = ContractNegotiationProposal.create(tenantId.getValue(), contractId, n.getId(), partyId, in.kind(), latestVersion(tenantId, c),
                in.blockIndex(), in.originalHtml(), in.proposedHtml(), in.rationale());
        if (!NegotiationEngine.targetExists(c.getBody(), p))
            throw new IllegalStateException("That wording is not in the current contract. Refresh the page and try again.");
        // a newer proposal from the same party on the same wording replaces their earlier one
        for (ContractNegotiationProposal old : proposalRepo.findByTenantIdAndContractIdAndPartyIdOrderByCreatedAtAsc(tenantId.getValue(), contractId, partyId))
            if (old.isUnresolved() && old.targetKey().equals(p.targetKey())) { old.supersede("Replaced by a newer proposal"); proposalRepo.save(old); }
        proposalRepo.save(p);
        reconcile(n, c);
        event(n, "COUNTERPARTY", null, partyId, "PROPOSAL_SUBMITTED", describe(p), ip);
        publisher.publishEvent(new NegotiationActivity(tenantId.getValue(), contractId, NegotiationActivity.PROPOSAL, party.getFullName()));
        return proposalView(p, c.getBody(), party.getFullName());
    }

    @Transactional
    public ProposalView withdraw(TenantId tenantId, UUID contractId, UUID partyId, UUID proposalId, String ip) {
        Contract c = lockContract(tenantId, contractId);
        ContractParty party = partyOf(contractId, partyId);
        ContractNegotiation n = lockNegotiation(tenantId, contractId);
        if (!n.isActive()) throw new IllegalStateException("This negotiation is no longer open");
        ContractNegotiationProposal p = proposalOf(tenantId, contractId, proposalId);
        p.withdraw(partyId);
        proposalRepo.save(p);
        reconcile(n, c);
        event(n, "COUNTERPARTY", null, partyId, "PROPOSAL_WITHDRAWN", describe(p), ip);
        return proposalView(p, c.getBody(), party.getFullName());
    }

    @Transactional
    public CommentView counterpartyComment(TenantId tenantId, UUID contractId, UUID partyId, CounterpartyCommentInput in, String ip) {
        Contract c = lockContract(tenantId, contractId);
        requireEditable(c);
        partyOf(contractId, partyId);
        ContractNegotiation n = lockNegotiation(tenantId, contractId);
        requireAcceptingInput(n);
        if (in.proposalId() != null && !proposalOf(tenantId, contractId, in.proposalId()).getPartyId().equals(partyId))
            throw new ResourceNotFoundException("Proposal", in.proposalId().toString());
        if (in.parentId() != null) commentRepo.findByTenantIdAndId(tenantId.getValue(), in.parentId())
                .filter(pc -> pc.getContractId().equals(contractId) && pc.visibleTo(partyId))
                .orElseThrow(() -> new ResourceNotFoundException("Comment", in.parentId().toString()));
        ContractNegotiationComment cm = commentRepo.save(ContractNegotiationComment.counterparty(tenantId.getValue(), contractId, n.getId(), partyId, in.blockIndex(),
                in.blockExcerpt(), in.proposalId(), in.parentId(), in.body()));
        event(n, "COUNTERPARTY", null, partyId, "COUNTERPARTY_COMMENT", null, ip);
        publisher.publishEvent(new NegotiationActivity(tenantId.getValue(), contractId, NegotiationActivity.COMMENT, nameOf(contractId).get(partyId)));
        return commentView(cm);
    }

    @Transactional // not read-only: the first look at an older contract records its baseline version
    public CounterpartyView counterpartyView(TenantId tenantId, UUID contractId, UUID partyId) {
        Contract c = contractOf(tenantId, contractId);
        partyOf(contractId, partyId);
        ContractNegotiation n = negotiationRepo.findByTenantIdAndContractId(tenantId.getValue(), contractId)
                .orElseThrow(() -> new ResourceNotFoundException("Negotiation", contractId.toString()));
        String name = nameOf(contractId).get(partyId);
        List<ProposalView> mine = proposalRepo.findByTenantIdAndContractIdAndPartyIdOrderByCreatedAtAsc(tenantId.getValue(), contractId, partyId).stream()
                .map(p -> proposalView(p, c.getBody(), name)).toList();
        List<CommentView> comments = commentRepo.findByTenantIdAndContractIdAndInternalOnlyFalseOrderByCreatedAtAsc(tenantId.getValue(), contractId).stream()
                .filter(cm -> cm.visibleTo(partyId)).map(ContractNegotiationService::commentView).toList();
        boolean can = isEditable(c) && n.acceptsCounterpartyInput(today());
        return new CounterpartyView(n.getStatus(), n.getResponseDeadline(), can, latestVersion(tenantId, c), c.getBody(), BodyDiff.blocks(c.getBody()), mine, comments);
    }

    // ── Signing hooks ────────────────────────────────────────────────────────

    /**
     * Called before a contract is sent for signature. A contract that was never negotiated is unaffected. Otherwise nothing may be waiting,
     * the negotiation must be ready (or closed), and the wording must still be the pinned final revision.
     */
    @Transactional(readOnly = true)
    public void assertReadyForSigning(TenantId tenantId, Contract c) {
        Optional<ContractNegotiation> opt = negotiationRepo.findByTenantIdAndContractId(tenantId.getValue(), c.getId());
        if (opt.isEmpty()) return;
        ContractNegotiation n = opt.get();
        List<ContractNegotiationProposal> all = proposalRepo.findByTenantIdAndContractIdOrderByCreatedAtAsc(tenantId.getValue(), c.getId());
        long unresolved = count(all, ContractNegotiationProposal::isUnresolved);
        long accepted = count(all, p -> ContractNegotiationProposal.ACCEPTED.equals(p.getStatus()));
        if (unresolved > 0) throw new IllegalStateException("Cannot send for signature: " + unresolved + " negotiation proposal(s) still need a decision");
        if (accepted > 0) throw new IllegalStateException("Cannot send for signature: " + accepted + " accepted proposal(s) have not been applied to a new revision");
        if (n.isActive()) throw new IllegalStateException("Cannot send for signature: mark the negotiation ready for signature or close it first");
        if (n.getFinalBodyHash() != null && !n.getFinalBodyHash().equals(hashOf(c)))
            throw new IllegalStateException("Cannot send for signature: the wording has changed since the negotiation was marked ready. Reopen it and mark it ready again.");
    }

    /** Called once a contract has been sent: whatever the negotiation was, it is now frozen. */
    @Transactional
    public void freezeOnSend(TenantId tenantId, Contract c, UUID by) {
        negotiationRepo.findForUpdate(tenantId.getValue(), c.getId()).ifPresent(n -> {
            if (ContractNegotiation.CLOSED.equals(n.getStatus())) return;
            n.close("Contract sent for signature", by);
            negotiationRepo.save(n);
            revokeLinks(n, "Contract sent for signature");
            event(n, "SYSTEM", by, null, "FROZEN_ON_SEND", "Negotiation closed because the contract was sent for signature", null);
        });
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /**
     * Brings every open proposal in line with the current wording: a proposal whose wording has gone is superseded, clashing proposals are
     * held in conflict, ones that no longer clash are released, and the negotiation's status is recalculated.
     */
    private List<ContractNegotiationProposal> reconcile(ContractNegotiation n, Contract c) {
        List<ContractNegotiationProposal> all = proposalRepo.findByTenantIdAndContractIdOrderByCreatedAtAsc(n.getTenantId(), n.getContractId());
        for (ContractNegotiationProposal p : all)
            if (p.isUnresolved() && !NegotiationEngine.targetExists(c.getBody(), p)) { p.supersede("The wording it changes was edited after it was proposed"); proposalRepo.save(p); }
        Map<UUID, UUID> clashes = NegotiationEngine.conflicts(all);
        for (ContractNegotiationProposal p : all) {
            UUID with = clashes.get(p.getId());
            if (with != null && (ContractNegotiationProposal.PENDING.equals(p.getStatus()) || ContractNegotiationProposal.CONFLICTED.equals(p.getStatus()))) {
                if (!with.equals(p.getConflictWith()) || !ContractNegotiationProposal.CONFLICTED.equals(p.getStatus())) { p.markConflict(with); proposalRepo.save(p); }
            } else if (with == null && ContractNegotiationProposal.CONFLICTED.equals(p.getStatus())) { p.clearConflict(); proposalRepo.save(p); }
        }
        n.refresh(count(all, ContractNegotiationProposal::isUnresolved), count(all, p -> ContractNegotiationProposal.ACCEPTED.equals(p.getStatus())));
        negotiationRepo.save(n);
        return all;
    }

    private NegotiationView view(ContractNegotiation n, List<ContractNegotiationProposal> all) {
        Map<String, Long> by = all.stream().collect(Collectors.groupingBy(ContractNegotiationProposal::getStatus, Collectors.counting()));
        Counts counts = new Counts(by.getOrDefault("PENDING_REVIEW", 0L), by.getOrDefault("CONFLICTED", 0L), by.getOrDefault("ACCEPTED", 0L), by.getOrDefault("APPLIED", 0L),
                by.getOrDefault("REJECTED", 0L), by.getOrDefault("WITHDRAWN", 0L), by.getOrDefault("SUPERSEDED", 0L));
        boolean passed = n.getResponseDeadline() != null && today().isAfter(n.getResponseDeadline());
        return new NegotiationView(n.getId(), n.getContractId(), n.getStatus(), n.getResponseDeadline(), passed, n.getBaseVersionNo(), n.getFinalVersionNo(), n.getFinalBodyHash(),
                n.getOpenedAt(), n.getReadyAt(), n.getClosedAt(), n.getCloseReason(), counts);
    }

    private ProposalView proposalView(ContractNegotiationProposal p, String currentBody, String partyName) {
        return new ProposalView(p.getId(), p.getPartyId(), partyName, p.getKind(), p.getBaseVersionNo(), p.getBlockIndex(), p.getOriginalHtml(), p.getProposedHtml(), p.getRationale(),
                p.getStatus(), p.getConflictWith(), p.getDecidedAt(), p.getDecisionNote(), p.getAppliedVersionNo(), NegotiationEngine.targetExists(currentBody, p), p.getCreatedAt());
    }

    private static CommentView commentView(ContractNegotiationComment c) {
        return new CommentView(c.getId(), c.getAuthorType(), c.getPartyId(), c.getBlockIndex(), c.getBlockExcerpt(), c.getProposalId(), c.getParentId(), c.getBody(), c.isInternalOnly(), c.getCreatedAt());
    }

    private static EventView eventView(ContractNegotiationEvent e) {
        return new EventView(e.getId(), e.getActorType(), e.getActorUserId(), e.getPartyId(), e.getEventType(), e.getDetail(), e.getCreatedAt());
    }

    /** Counterparty links stop working the moment the negotiation stops taking input. A reopened negotiation needs fresh links. */
    private void revokeLinks(ContractNegotiation n, String reason) {
        linkRepo.revokeAllActive(n.getTenantId(), n.getContractId(), reason, Instant.now());
    }

    private void event(ContractNegotiation n, String actor, UUID userId, UUID partyId, String type, String detail, String ip) {
        eventRepo.save(ContractNegotiationEvent.of(n.getTenantId(), n.getContractId(), n.getId(), actor, userId, partyId, type, detail, ip));
    }

    private static String describe(ContractNegotiationProposal p) {
        String text = p.getOriginalHtml() == null ? "" : org.jsoup.Jsoup.parse(p.getOriginalHtml()).text();
        return p.getKind() + (text.isBlank() ? "" : ": " + (text.length() > 120 ? text.substring(0, 120) + "…" : text));
    }

    private static long count(List<ContractNegotiationProposal> all, java.util.function.Predicate<ContractNegotiationProposal> f) { return all.stream().filter(f).count(); }

    private void requireAcceptingInput(ContractNegotiation n) {
        if (!n.isActive()) throw new IllegalStateException("This negotiation is no longer open for changes");
        if (!n.acceptsCounterpartyInput(today())) throw new IllegalStateException("The deadline for responding to this negotiation has passed. Ask the sender to extend it.");
    }

    private static boolean isEditable(Contract c) { return "DRAFT".equals(c.getStatus()) || "UNDER_REVIEW".equals(c.getStatus()); }

    private static void requireEditable(Contract c) {
        if (!isEditable(c)) throw new IllegalStateException("Negotiation is only possible while the contract is a draft or under review (this one is " + c.getStatus()
                + "). Use the amendment workflow for a sent or signed contract.");
    }

    private String hashOf(Contract c) { return hashing.sha256(c.getBody() == null ? "" : c.getBody()); }

    private int latestVersion(TenantId tenantId, Contract c) { return redline.versions(tenantId, c.getId()).get(0).versionNo(); }

    private Map<UUID, String> nameOf(UUID contractId) {
        Map<UUID, String> m = new HashMap<>();
        for (ContractParty p : partyRepo.findByContract(contractId)) m.put(p.getId(), p.getFullName());
        return m;
    }

    private ContractParty partyOf(UUID contractId, UUID partyId) {
        return partyRepo.findByContract(contractId).stream().filter(p -> p.getId().equals(partyId)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Party", String.valueOf(partyId)));
    }

    private static LocalDate today() { return LocalDate.now(ZONE); }

    private Contract contractOf(TenantId tenantId, UUID id) {
        return contractRepo.findActiveById(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Contract", id.toString()));
    }

    private Contract lockContract(TenantId tenantId, UUID id) {
        return contractRepo.findForUpdate(tenantId.getValue(), id).orElseThrow(() -> new ResourceNotFoundException("Contract", id.toString()));
    }

    private ContractNegotiation lockNegotiation(TenantId tenantId, UUID contractId) {
        return negotiationRepo.findForUpdate(tenantId.getValue(), contractId).orElseThrow(() -> new ResourceNotFoundException("Negotiation", contractId.toString()));
    }

    private ContractNegotiationProposal proposalOf(TenantId tenantId, UUID contractId, UUID id) {
        return proposalRepo.findByTenantIdAndId(tenantId.getValue(), id).filter(p -> p.getContractId().equals(contractId))
                .orElseThrow(() -> new ResourceNotFoundException("Proposal", id.toString()));
    }
}
