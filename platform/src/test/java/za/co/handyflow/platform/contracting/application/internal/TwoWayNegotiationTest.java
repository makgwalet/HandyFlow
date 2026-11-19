package za.co.handyflow.platform.contracting.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.contracting.domain.model.Contract;
import za.co.handyflow.platform.contracting.domain.model.ContractNegotiationProposal;
import za.co.handyflow.platform.contracting.dto.ContractNegotiationDtos.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Both sides can propose, accept, reject and counter; every round is kept; agreement never changes the contract by itself and never
 * replaces formal signing.
 */
class TwoWayNegotiationTest {

    static final String P1 = "<p>Payment is due in 30 days.</p>", P2 = "<p>Either party may terminate on 60 days notice.</p>";

    NegotiationHarness h;
    ContractNegotiationService service;
    TenantId tenant = TenantId.generate();
    UUID staff = UUID.randomUUID();
    Contract c;
    UUID a, b;

    @BeforeEach
    void setUp() {
        h = new NegotiationHarness();
        service = h.service;
        c = h.contract(tenant, "Deal", P1 + P2);
        a = h.party(c, 0).getId();
        b = h.party(c, 1).getId();
        h.inTxDo(() -> service.open(tenant, c.getId(), LocalDate.now().plusDays(10), staff));
    }

    private ProposalView staffProposes(UUID party, String original, String proposed) {
        return h.inTx(() -> service.staffPropose(tenant, c.getId(), new StaffProposalRequest(party, "REPLACE", 0, original, proposed, "we suggest"), staff));
    }

    private ProposalView theyPropose(UUID party, String original, String proposed) {
        return h.inTx(() -> service.submitProposal(tenant, c.getId(), party, new ProposalInput("REPLACE", 0, original, proposed, "please"), "1.1.1.1"));
    }

    private String status() { return h.inTx(() -> service.staffView(tenant, c.getId())).negotiation().status(); }

    @Test
    void staffCanProposeAndTheCounterpartyCanAcceptItAndNothingChangesUntilStaffApply() {
        ProposalView p = staffProposes(a, P1, "<p>Payment is due in 45 days.</p>");
        assertEquals("STAFF", p.authorType());
        assertEquals("COUNTERPARTY", p.awaiting());
        assertEquals("OPEN", status(), "waiting on the counterparty is not waiting on staff");

        ProposalView accepted = h.inTx(() -> service.counterpartyAccept(tenant, c.getId(), a, p.id(), "fine", "1.1.1.1"));
        assertEquals("ACCEPTED", accepted.status());
        assertTrue(accepted.decidedByCounterparty());
        assertTrue(c.getBody().contains("30 days"), "accepting does not edit the contract");
        assertEquals("CHANGES_REQUIRED", status());

        h.inTx(() -> service.applyAccepted(tenant, c.getId(), null, staff));
        assertTrue(c.getBody().contains("45 days"));
        assertEquals("APPLIED", h.proposalsOf(c).get(0).getStatus());
    }

    @Test
    void theCounterpartyCanRejectAStaffProposalButOnlyWithAReason() {
        ProposalView p = staffProposes(a, P1, "<p>Payment is due in 14 days.</p>");
        assertThrows(IllegalArgumentException.class, () -> h.inTx(() -> service.counterpartyReject(tenant, c.getId(), a, p.id(), " ", "1.1.1.1")));
        ProposalView r = h.inTx(() -> service.counterpartyReject(tenant, c.getId(), a, p.id(), "Too short for us", "1.1.1.1"));
        assertEquals("REJECTED", r.status());
        assertEquals("Too short for us", r.decisionNote());
    }

    @Test
    void aCounterIsANewRoundAndTheEarlierOneIsKept() {
        ProposalView r1 = theyPropose(a, P2, "<p>Either party may terminate on 30 days notice.</p>");
        ProposalView r2 = h.inTx(() -> service.staffCounter(tenant, c.getId(), r1.id(), new CounterInput("<p>Either party may terminate on 45 days notice.</p>", "meet in the middle"), staff));
        assertEquals(2, r2.roundNo());
        assertEquals(r1.id(), r2.parentId());
        assertEquals("STAFF", r2.authorType());
        assertEquals("COUNTERPARTY", r2.awaiting());

        ProposalView r3 = h.inTx(() -> service.counterpartyCounter(tenant, c.getId(), a, r2.id(), new CounterInput("<p>Either party may terminate on 40 days notice.</p>", null), "1.1.1.1"));
        assertEquals(3, r3.roundNo());
        assertEquals(r2.id(), r3.parentId());
        assertEquals("COUNTERPARTY", r3.authorType());
        assertEquals("STAFF", r3.awaiting());

        List<ContractNegotiationProposal> all = h.proposalsOf(c);
        assertEquals(3, all.size());
        assertEquals("COUNTERED", all.get(0).getStatus());
        assertEquals("COUNTERED", all.get(1).getStatus());
        assertEquals("PENDING_REVIEW", all.get(2).getStatus());
        assertEquals("STAFF_REVIEW", status());

        // staff accept the last round, apply it, and the contract holds the agreed wording
        h.inTx(() -> service.accept(tenant, c.getId(), r3.id(), null, staff));
        h.inTx(() -> service.applyAccepted(tenant, c.getId(), null, staff));
        assertTrue(c.getBody().contains("40 days"));
    }

    @Test
    void aCounterToADeletionOffersReplacementWording() {
        ProposalView del = h.inTx(() -> service.submitProposal(tenant, c.getId(), a, new ProposalInput("DELETE", 1, P2, null, "remove it"), "1.1.1.1"));
        ProposalView counter = h.inTx(() -> service.staffCounter(tenant, c.getId(), del.id(), new CounterInput("<p>Either party may terminate on 90 days notice.</p>", null), staff));
        assertEquals("REPLACE", counter.kind());
        assertEquals(P2, counter.originalHtml());
    }

    @Test
    void youCannotCounterOrDecideYourOwnProposal() {
        ProposalView mine = staffProposes(a, P1, "<p>Payment is due in 45 days.</p>");
        assertThrows(IllegalStateException.class, () -> h.inTx(() -> service.staffCounter(tenant, c.getId(), mine.id(), new CounterInput("<p>x</p>", null), staff)));
        assertThrows(IllegalStateException.class, () -> h.inTx(() -> service.accept(tenant, c.getId(), mine.id(), null, staff)));
        assertThrows(IllegalStateException.class, () -> h.inTx(() -> service.reject(tenant, c.getId(), mine.id(), "no", staff)));

        ProposalView theirs = theyPropose(b, P2, "<p>Either party may terminate on 30 days notice.</p>");
        assertThrows(IllegalStateException.class, () -> h.inTx(() -> service.counterpartyAccept(tenant, c.getId(), b, theirs.id(), null, "1.1.1.1")));
        assertThrows(IllegalStateException.class, () -> h.inTx(() -> service.counterpartyCounter(tenant, c.getId(), b, theirs.id(), new CounterInput("<p>x</p>", null), "1.1.1.1")));
        assertThrows(IllegalStateException.class, () -> h.inTx(() -> service.staffWithdrawProposal(tenant, c.getId(), theirs.id(), staff)));
    }

    @Test
    void oneCounterpartyCannotAnswerAnotherOnesProposal() {
        ProposalView forA = staffProposes(a, P1, "<p>Payment is due in 45 days.</p>");
        assertThrows(ResourceNotFoundException.class, () -> h.inTx(() -> service.counterpartyAccept(tenant, c.getId(), b, forA.id(), null, "1.1.1.1")));
        assertThrows(ResourceNotFoundException.class, () -> h.inTx(() -> service.counterpartyReject(tenant, c.getId(), b, forA.id(), "no", "1.1.1.1")));
        assertThrows(ResourceNotFoundException.class, () -> h.inTx(() -> service.counterpartyCounter(tenant, c.getId(), b, forA.id(), new CounterInput("<p>x</p>", null), "1.1.1.1")));
        assertEquals("PENDING_REVIEW", h.proposalsOf(c).get(0).getStatus());
    }

    @Test
    void anotherTenantCannotUseTheseOperations() {
        ProposalView p = staffProposes(a, P1, "<p>Payment is due in 45 days.</p>");
        TenantId other = TenantId.generate();
        assertThrows(ResourceNotFoundException.class, () -> h.inTx(() -> service.staffPropose(other, c.getId(), new StaffProposalRequest(a, "REPLACE", 0, P1, "<p>x</p>", null), staff)));
        assertThrows(ResourceNotFoundException.class, () -> h.inTx(() -> service.counterpartyAccept(other, c.getId(), a, p.id(), null, "1.1.1.1")));
        assertThrows(ResourceNotFoundException.class, () -> h.inTx(() -> service.staffCounter(other, c.getId(), p.id(), new CounterInput("<p>x</p>", null), staff)));
    }

    @Test
    void staffCanWithdrawTheirOwnProposalAndTheCounterpartyCannotWithdrawIt() {
        ProposalView p = staffProposes(a, P1, "<p>Payment is due in 45 days.</p>");
        assertThrows(IllegalStateException.class, () -> h.inTx(() -> service.withdraw(tenant, c.getId(), a, p.id(), "1.1.1.1")));
        assertEquals("WITHDRAWN", h.inTx(() -> service.staffWithdrawProposal(tenant, c.getId(), p.id(), staff)).status());
        assertEquals("OPEN", status());
    }

    @Test
    void aWaitingStaffProposalStillBlocksMarkingReadyAndSigning() {
        staffProposes(a, P1, "<p>Payment is due in 45 days.</p>");
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> h.inTx(() -> service.markReady(tenant, c.getId(), staff)));
        assertTrue(e.getMessage().contains("still need a decision"));
        assertThrows(IllegalStateException.class, () -> h.inTx(() -> { service.assertReadyForSigning(tenant, c); return null; }));
    }

    @Test
    void agreementDoesNotReplaceSigning() {
        ProposalView p = staffProposes(a, P1, "<p>Payment is due in 45 days.</p>");
        h.inTx(() -> service.counterpartyAccept(tenant, c.getId(), a, p.id(), null, "1.1.1.1"));
        h.inTx(() -> service.applyAccepted(tenant, c.getId(), null, staff));
        // the contract is still a draft: nothing was signed or sent, and the negotiation must still be marked ready before it can be
        assertEquals("DRAFT", c.getStatus());
        assertThrows(IllegalStateException.class, () -> h.inTx(() -> { service.assertReadyForSigning(tenant, c); return null; }));
        h.inTx(() -> service.markReady(tenant, c.getId(), staff));
        assertDoesNotThrow(() -> h.inTx(() -> { service.assertReadyForSigning(tenant, c); return null; }));
        assertNull(c.getSignedAt());
    }

    @Test
    void aStaffProposalOnWordingThatChangedIsRefused() {
        assertThrows(IllegalStateException.class, () -> staffProposes(a, "<p>Not in the contract.</p>", "<p>x</p>"));
    }

    @Test
    void aNewStaffProposalOnTheSameWordingReplacesTheEarlierWaitingOne() {
        ProposalView first = staffProposes(a, P1, "<p>Payment is due in 45 days.</p>");
        staffProposes(a, P1, "<p>Payment is due in 60 days.</p>");
        assertEquals("SUPERSEDED", h.proposalsOf(c).stream().filter(x -> x.getId().equals(first.id())).findFirst().orElseThrow().getStatus());
    }

    @Test
    void roundsAreCapped() {
        ProposalView p = theyPropose(a, P1, "<p>Payment is due in 1 days.</p>");
        boolean staffTurn = true;
        for (int round = 2; round <= ContractNegotiationProposal.MAX_ROUNDS; round++) {
            String text = "<p>Payment is due in " + (round + 1) + " days.</p>";
            ProposalView prev = p;
            p = staffTurn ? h.inTx(() -> service.staffCounter(tenant, c.getId(), prev.id(), new CounterInput(text, null), staff))
                    : h.inTx(() -> service.counterpartyCounter(tenant, c.getId(), a, prev.id(), new CounterInput(text, null), "1.1.1.1"));
            staffTurn = !staffTurn;
        }
        ProposalView last = p;
        assertEquals(ContractNegotiationProposal.MAX_ROUNDS, last.roundNo());
        boolean staffNext = staffTurn;
        assertThrows(IllegalStateException.class, () -> h.inTx(() -> staffNext
                ? service.staffCounter(tenant, c.getId(), last.id(), new CounterInput("<p>one more</p>", null), staff)
                : service.counterpartyCounter(tenant, c.getId(), a, last.id(), new CounterInput("<p>one more</p>", null), "1.1.1.1")));
    }
}
