package za.co.handyflow.platform.contracting.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.contracting.domain.model.*;
import za.co.handyflow.platform.contracting.domain.repository.*;
import za.co.handyflow.platform.contracting.dto.ContractNegotiationDtos.*;
import za.co.handyflow.platform.contracting.dto.ContractRedlineDtos.VersionInfo;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Runs the negotiation service against in-memory repositories so the whole lifecycle can be exercised without a database. */
class ContractNegotiationServiceTest {

    static final String P1 = "<p>Payment is due in 30 days.</p>", P2 = "<p>Either party may terminate on 60 days notice.</p>", P3 = "<p>Governed by South African law.</p>";

    TenantId tenant = TenantId.generate();
    UUID staff = UUID.randomUUID();
    Contract contract;
    ContractParty partyA, partyB;
    final List<ContractNegotiation> negotiations = new ArrayList<>();
    final List<ContractNegotiationProposal> proposals = new ArrayList<>();
    final List<ContractNegotiationComment> comments = new ArrayList<>();
    final List<ContractNegotiationEvent> events = new ArrayList<>();
    final AtomicInteger version = new AtomicInteger(1);
    final SigningTokenService hashing = new SigningTokenService(mock(ContractSigningTokenRepository.class));
    ContractNegotiationService service;

    @BeforeEach
    void setUp() {
        contract = Contract.create(tenant, "CTR-1", "Service agreement", "SERVICE", P1 + P2 + P3, null, staff);
        partyA = ContractParty.create(tenant, contract.getId(), "COMPANY", "CLIENT", "Acme", "a@x.co", null, "Acme", 1);
        partyB = ContractParty.create(tenant, contract.getId(), "COMPANY", "SUPPLIER", "Beta", "b@x.co", null, "Beta", 2);
        UUID tid = tenant.getValue(), cid = contract.getId();

        ContractRepository contractRepo = mock(ContractRepository.class);
        when(contractRepo.findForUpdate(tid, cid)).thenReturn(Optional.of(contract));
        when(contractRepo.findActiveById(tenant, cid)).thenReturn(Optional.of(contract));
        when(contractRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        ContractPartyRepository partyRepo = mock(ContractPartyRepository.class);
        when(partyRepo.findByContract(cid)).thenReturn(List.of(partyA, partyB));

        ContractNegotiationRepository nRepo = mock(ContractNegotiationRepository.class);
        when(nRepo.save(any())).thenAnswer(i -> { ContractNegotiation n = i.getArgument(0); if (!negotiations.contains(n)) negotiations.add(n); return n; });
        when(nRepo.findForUpdate(tid, cid)).thenAnswer(i -> negotiations.stream().findFirst());
        when(nRepo.findByTenantIdAndContractId(tid, cid)).thenAnswer(i -> negotiations.stream().findFirst());

        ContractNegotiationProposalRepository pRepo = mock(ContractNegotiationProposalRepository.class);
        when(pRepo.save(any())).thenAnswer(i -> { ContractNegotiationProposal p = i.getArgument(0); if (!proposals.contains(p)) proposals.add(p); return p; });
        when(pRepo.findByTenantIdAndId(eq(tid), any())).thenAnswer(i -> proposals.stream().filter(p -> p.getId().equals(i.getArgument(1))).findFirst());
        when(pRepo.findByTenantIdAndContractIdOrderByCreatedAtAsc(tid, cid)).thenAnswer(i -> new ArrayList<>(proposals));
        when(pRepo.findByTenantIdAndContractIdAndPartyIdOrderByCreatedAtAsc(eq(tid), eq(cid), any()))
                .thenAnswer(i -> proposals.stream().filter(p -> p.getPartyId().equals(i.getArgument(2))).toList());

        ContractNegotiationCommentRepository cRepo = mock(ContractNegotiationCommentRepository.class);
        when(cRepo.save(any())).thenAnswer(i -> { ContractNegotiationComment c = i.getArgument(0); comments.add(c); return c; });
        when(cRepo.findByTenantIdAndId(eq(tid), any())).thenAnswer(i -> comments.stream().filter(c -> c.getId().equals(i.getArgument(1))).findFirst());
        when(cRepo.findByTenantIdAndContractIdOrderByCreatedAtAsc(tid, cid)).thenAnswer(i -> new ArrayList<>(comments));
        when(cRepo.findByTenantIdAndContractIdAndInternalOnlyFalseOrderByCreatedAtAsc(tid, cid)).thenAnswer(i -> comments.stream().filter(c -> !c.isInternalOnly()).toList());

        ContractNegotiationEventRepository eRepo = mock(ContractNegotiationEventRepository.class);
        when(eRepo.save(any())).thenAnswer(i -> { ContractNegotiationEvent e = i.getArgument(0); events.add(e); return e; });
        when(eRepo.findTop200ByTenantIdAndContractIdOrderByCreatedAtDesc(tid, cid)).thenAnswer(i -> new ArrayList<>(events));

        ContractRedlineService redline = mock(ContractRedlineService.class);
        when(redline.versions(tenant, cid)).thenAnswer(i -> List.of(new VersionInfo(version.get(), "X", null, null, Instant.now())));
        when(redline.snapshot(any(), any(), anyString(), any(), any())).thenAnswer(i -> version.incrementAndGet());

        service = new ContractNegotiationService(contractRepo, partyRepo, nRepo, pRepo, cRepo, eRepo, redline, hashing);
    }

    UUID cid() { return contract.getId(); }

    NegotiationView open() { return service.open(tenant, cid(), LocalDate.now().plusDays(14), staff); }

    ProposalView propose(ContractParty party, String kind, int idx, String original, String proposed) {
        return service.submitProposal(tenant, cid(), party.getId(), new ProposalInput(kind, idx, original, proposed, "reason"), "1.2.3.4");
    }

    @Test
    void cannotOpenTwiceOrOnceSent() {
        open();
        assertThrows(IllegalStateException.class, this::open);
        negotiations.clear();
        contract.send();
        assertThrows(IllegalStateException.class, this::open);
    }

    @Test
    void aProposalNeverChangesTheContract() {
        open();
        ProposalView v = propose(partyA, "REPLACE", 0, P1, "<p>Payment is due in 14 days.</p>");
        assertEquals("PENDING_REVIEW", v.status());
        assertTrue(contract.getBody().contains("30 days"));
        assertEquals("STAFF_REVIEW", service.staffView(tenant, cid()).negotiation().status());
    }

    @Test
    void acceptThenApplyCreatesOneNewRevision() {
        open();
        ProposalView v = propose(partyA, "REPLACE", 0, P1, "<p>Payment is due in 14 days.</p>");
        service.accept(tenant, cid(), v.id(), "agreed", staff);
        assertTrue(contract.getBody().contains("30 days"), "accepting alone does not change the contract");
        assertEquals("CHANGES_REQUIRED", service.staffView(tenant, cid()).negotiation().status());
        StaffView after = service.applyAccepted(tenant, cid(), null, staff);
        assertTrue(contract.getBody().contains("14 days"));
        assertEquals(2, version.get());
        assertEquals("APPLIED", after.proposals().get(0).status());
        assertEquals(Integer.valueOf(2), after.proposals().get(0).appliedVersionNo());
        assertEquals("OPEN", after.negotiation().status());
        assertTrue(events.stream().anyMatch(e -> e.getEventType().equals("PROPOSALS_APPLIED")));
    }

    @Test
    void applyKeepsStaffEditsMadeAfterTheProposal() {
        open();
        ProposalView v = propose(partyA, "REPLACE", 0, P1, "<p>Payment is due in 14 days.</p>");
        service.accept(tenant, cid(), v.id(), null, staff);
        contract.updateBody("<p>Preamble.</p>" + contract.getBody().replace("60 days", "90 days"));
        service.applyAccepted(tenant, cid(), null, staff);
        assertTrue(contract.getBody().contains("Preamble."));
        assertTrue(contract.getBody().contains("90 days"));
        assertTrue(contract.getBody().contains("14 days"));
    }

    @Test
    void aProposalWhoseWordingWasEditedIsSupersededNotApplied() {
        open();
        ProposalView v = propose(partyA, "REPLACE", 0, P1, "<p>Payment is due in 14 days.</p>");
        contract.updateBody(contract.getBody().replace("30 days", "45 days"));
        StaffView view = service.staffView(tenant, cid());
        assertEquals("SUPERSEDED", view.proposals().get(0).status());
        assertThrows(IllegalStateException.class, () -> service.accept(tenant, cid(), v.id(), null, staff));
    }

    @Test
    void twoPartiesOnTheSameParagraphAreHeldInConflictUntilStaffRejectOne() {
        open();
        ProposalView a = propose(partyA, "REPLACE", 0, P1, "<p>Payment is due in 14 days.</p>");
        ProposalView b = propose(partyB, "REPLACE", 0, P1, "<p>Payment is due in 60 days.</p>");
        StaffView view = service.staffView(tenant, cid());
        assertTrue(view.proposals().stream().allMatch(p -> p.status().equals("CONFLICTED")));
        assertThrows(IllegalStateException.class, () -> service.accept(tenant, cid(), a.id(), null, staff), "a conflicted proposal cannot be accepted");
        service.reject(tenant, cid(), b.id(), "Not acceptable", staff);
        view = service.staffView(tenant, cid());
        assertEquals("PENDING_REVIEW", view.proposals().stream().filter(p -> p.id().equals(a.id())).findFirst().orElseThrow().status());
        service.accept(tenant, cid(), a.id(), null, staff);
        assertEquals("REJECTED", view.proposals().stream().filter(p -> p.id().equals(b.id())).findFirst().orElseThrow().status());
    }

    @Test
    void aNewProposalFromTheSamePartyOnTheSameWordingSupersedesTheOldOne() {
        open();
        ProposalView first = propose(partyA, "REPLACE", 0, P1, "<p>Payment is due in 14 days.</p>");
        propose(partyA, "REPLACE", 0, P1, "<p>Payment is due in 21 days.</p>");
        assertEquals("SUPERSEDED", proposals.stream().filter(p -> p.getId().equals(first.id())).findFirst().orElseThrow().getStatus());
        assertEquals(1, proposals.stream().filter(ContractNegotiationProposal::isOpen).count());
    }

    @Test
    void rejectedAndWithdrawnProposalsStayInTheHistory() {
        open();
        ProposalView a = propose(partyA, "DELETE", 2, P3, null);
        ProposalView b = propose(partyB, "REPLACE", 1, P2, "<p>Either party may terminate on 30 days notice.</p>");
        service.reject(tenant, cid(), a.id(), "Required by law", staff);
        service.withdraw(tenant, cid(), partyB.getId(), b.id(), "1.2.3.4");
        StaffView view = service.staffView(tenant, cid());
        assertEquals(2, view.proposals().size());
        assertEquals(List.of("REJECTED", "WITHDRAWN"), view.proposals().stream().map(ProposalView::status).toList());
        assertEquals("Required by law", view.proposals().get(0).decisionNote());
        assertEquals("OPEN", view.negotiation().status());
    }

    @Test
    void aPartyCannotWithdrawOrCommentOnAnotherPartysProposal() {
        open();
        ProposalView a = propose(partyA, "DELETE", 2, P3, null);
        assertThrows(IllegalStateException.class, () -> service.withdraw(tenant, cid(), partyB.getId(), a.id(), null));
        assertThrows(ResourceNotFoundException.class, () -> service.counterpartyComment(tenant, cid(), partyB.getId(), new CounterpartyCommentInput("hi", null, null, a.id(), null), null));
        assertThrows(ResourceNotFoundException.class, () -> service.withdraw(tenant, cid(), UUID.randomUUID(), a.id(), null), "an unknown party is rejected");
    }

    @Test
    void proposalMustTargetWordingThatIsInTheContract() {
        open();
        assertThrows(IllegalStateException.class, () -> propose(partyA, "REPLACE", 0, "<p>Something never written.</p>", "<p>x</p>"));
    }

    @Test
    void counterpartiesNeverSeeInternalNotesOrEachOthersThreads() {
        open();
        ProposalView a = propose(partyA, "DELETE", 2, P3, null);
        service.staffComment(tenant, cid(), new StaffCommentRequest("internal only", null, null, null, null, null, true), staff);
        service.staffComment(tenant, cid(), new StaffCommentRequest("to everyone", null, null, null, null, null, false), staff);
        service.staffComment(tenant, cid(), new StaffCommentRequest("about A's proposal", null, null, a.id(), null, null, false), staff);
        service.counterpartyComment(tenant, cid(), partyB.getId(), new CounterpartyCommentInput("B asks", null, null, null, null), null);
        List<String> forA = service.counterpartyView(tenant, cid(), partyA.getId()).comments().stream().map(CommentView::body).toList();
        List<String> forB = service.counterpartyView(tenant, cid(), partyB.getId()).comments().stream().map(CommentView::body).toList();
        assertEquals(List.of("to everyone", "about A's proposal"), forA);
        assertEquals(List.of("to everyone", "B asks"), forB);
        assertEquals(4, service.staffView(tenant, cid()).comments().size());
        assertTrue(service.counterpartyView(tenant, cid(), partyB.getId()).proposals().isEmpty(), "B sees none of A's proposals");
        assertEquals(1, service.counterpartyView(tenant, cid(), partyA.getId()).proposals().size());
    }

    @Test
    void aClosedNegotiationTakesNoCounterpartyInput() {
        open();
        negotiations.get(0).close("done", staff);
        assertThrows(IllegalStateException.class, () -> propose(partyA, "DELETE", 2, P3, null));
        assertFalse(service.counterpartyView(tenant, cid(), partyA.getId()).canPropose());
    }

    // ── signing ──────────────────────────────────────────────────────────────

    @Test
    void aContractNeverNegotiatedIsNotBlocked() {
        assertDoesNotThrow(() -> service.assertReadyForSigning(tenant, contract));
    }

    @Test
    void pendingOrUnappliedProposalsBlockSending() {
        open();
        ProposalView v = propose(partyA, "REPLACE", 0, P1, "<p>Payment is due in 14 days.</p>");
        assertTrue(assertThrows(IllegalStateException.class, () -> service.assertReadyForSigning(tenant, contract)).getMessage().contains("still need a decision"));
        service.accept(tenant, cid(), v.id(), null, staff);
        assertTrue(assertThrows(IllegalStateException.class, () -> service.assertReadyForSigning(tenant, contract)).getMessage().contains("not been applied"));
        assertThrows(IllegalStateException.class, () -> service.markReady(tenant, cid(), staff));
        service.applyAccepted(tenant, cid(), null, staff);
        assertThrows(IllegalStateException.class, () -> service.assertReadyForSigning(tenant, contract), "still open, not yet marked ready");
        service.markReady(tenant, cid(), staff);
        assertDoesNotThrow(() -> service.assertReadyForSigning(tenant, contract));
    }

    @Test
    void editingAfterReadyBreaksThePinAndBlocksSending() {
        open();
        service.markReady(tenant, cid(), staff);
        assertDoesNotThrow(() -> service.assertReadyForSigning(tenant, contract));
        contract.updateBody(contract.getBody() + "<p>Sneaky extra clause.</p>");
        assertTrue(assertThrows(IllegalStateException.class, () -> service.assertReadyForSigning(tenant, contract)).getMessage().contains("changed since"));
    }

    @Test
    void closingNeedsEveryProposalDecided() {
        open();
        ProposalView v = propose(partyA, "DELETE", 2, P3, null);
        assertThrows(IllegalStateException.class, () -> service.close(tenant, cid(), null, staff));
        service.reject(tenant, cid(), v.id(), "No", staff);
        assertEquals("CLOSED", service.close(tenant, cid(), "Agreed", staff).status());
        assertThrows(IllegalStateException.class, () -> propose(partyA, "DELETE", 2, P3, null));
        assertEquals("OPEN", service.reopen(tenant, cid(), null, staff).status());
    }

    @Test
    void sendingFreezesTheNegotiation() {
        open();
        service.markReady(tenant, cid(), staff);
        service.freezeOnSend(tenant, contract, staff);
        assertEquals("CLOSED", negotiations.get(0).getStatus());
        assertEquals("Contract sent for signature", negotiations.get(0).getCloseReason());
        contract.send();
        assertThrows(IllegalStateException.class, () -> service.reopen(tenant, cid(), null, staff), "a sent contract cannot be renegotiated");
        assertThrows(IllegalStateException.class, () -> propose(partyA, "DELETE", 2, P3, null));
        assertTrue(events.stream().anyMatch(e -> e.getEventType().equals("FROZEN_ON_SEND") && e.getActorType().equals("SYSTEM")));
    }

    @Test
    void everyActionIsAudited() {
        open();
        ProposalView v = propose(partyA, "DELETE", 2, P3, null);
        service.accept(tenant, cid(), v.id(), null, staff);
        List<String> types = events.stream().map(ContractNegotiationEvent::getEventType).toList();
        assertEquals(List.of("OPENED", "PROPOSAL_SUBMITTED", "PROPOSAL_ACCEPTED"), types);
        assertEquals("1.2.3.4", events.get(1).getIpAddress());
        assertEquals("COUNTERPARTY", events.get(1).getActorType());
    }
}
