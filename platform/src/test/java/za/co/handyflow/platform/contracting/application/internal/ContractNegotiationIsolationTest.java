package za.co.handyflow.platform.contracting.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.contracting.application.internal.ContractNegotiationAccessService.Access;
import za.co.handyflow.platform.contracting.domain.model.Contract;
import za.co.handyflow.platform.contracting.domain.model.ContractParty;
import za.co.handyflow.platform.contracting.domain.model.ContractNegotiationProposal;
import za.co.handyflow.platform.contracting.dto.ContractNegotiationDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tenant and contract isolation. Two tenants, and two contracts inside the first one, each with a live negotiation. Every operation is tried
 * with the other tenant's or the other contract's identifiers and must find nothing; the original negotiation must be untouched afterwards.
 */
class ContractNegotiationIsolationTest {

    static final String P1 = "<p>Payment is due in 30 days.</p>", P2 = "<p>Either party may terminate on 60 days notice.</p>";

    NegotiationHarness h;
    ContractNegotiationService service;
    TenantId tenantA = TenantId.generate(), tenantB = TenantId.generate();
    UUID staff = UUID.randomUUID();
    Contract a1, a2, b1;
    ProposalView proposalA1;

    @BeforeEach
    void setUp() {
        h = new NegotiationHarness();
        service = h.service;
        a1 = h.contract(tenantA, "A one", P1 + P2);
        a2 = h.contract(tenantA, "A two", P1 + P2);
        b1 = h.contract(tenantB, "B one", P1 + P2);
        for (Contract c : new Contract[]{a1, a2, b1}) {
            TenantId t = TenantId.of(c.getTenantId());
            h.inTxDo(() -> service.open(t, c.getId(), LocalDate.now().plusDays(10), staff));
        }
        proposalA1 = h.inTx(() -> service.submitProposal(tenantA, a1.getId(), h.party(a1, 0).getId(),
                new ProposalInput("REPLACE", 0, P1, "<p>Payment is due in 14 days.</p>", "why"), "1.1.1.1"));
    }

    static void notFound(Callable<?> call) {
        Exception e = assertThrows(Exception.class, call::call);
        assertTrue(e instanceof ResourceNotFoundException, "expected not found but got " + e);
    }

    // ── another tenant ───────────────────────────────────────────────────────

    @Test
    void anotherTenantFindsNothingOfThisContract() {
        UUID c = a1.getId(), p = proposalA1.id(), party = h.party(a1, 0).getId();
        notFound(() -> h.inTx(() -> service.open(tenantB, c, null, staff)));
        notFound(() -> h.inTx(() -> service.setDeadline(tenantB, c, null, staff)));
        notFound(() -> h.inTx(() -> service.accept(tenantB, c, p, null, staff)));
        notFound(() -> h.inTx(() -> service.reject(tenantB, c, p, "no", staff)));
        notFound(() -> h.inTx(() -> service.applyAccepted(tenantB, c, null, staff)));
        notFound(() -> h.inTx(() -> service.markReady(tenantB, c, staff)));
        notFound(() -> h.inTx(() -> service.close(tenantB, c, null, staff)));
        notFound(() -> h.inTx(() -> service.reopen(tenantB, c, null, staff)));
        notFound(() -> h.inTx(() -> service.staffView(tenantB, c)));
        notFound(() -> h.inTx(() -> service.staffComment(tenantB, c, new StaffCommentRequest("hi", null, null, null, null, null, false), staff)));
        notFound(() -> h.inTx(() -> service.submitProposal(tenantB, c, party, new ProposalInput("DELETE", 0, P1, null, null), null)));
        notFound(() -> h.inTx(() -> service.withdraw(tenantB, c, party, p, null)));
        notFound(() -> h.inTx(() -> service.counterpartyComment(tenantB, c, party, new CounterpartyCommentInput("hi", null, null, null, null), null)));
        notFound(() -> h.inTx(() -> service.counterpartyView(tenantB, c, party)));
    }

    @Test
    void theOtherTenantsAttemptsLeaveTheNegotiationUntouched() {
        UUID c = a1.getId(), p = proposalA1.id();
        try { h.inTx(() -> service.accept(tenantB, c, p, null, staff)); } catch (ResourceNotFoundException expected) { /* isolation */ }
        try { h.inTx(() -> service.close(tenantB, c, null, staff)); } catch (ResourceNotFoundException expected) { /* isolation */ }
        assertEquals("PENDING_REVIEW", h.proposalsOf(a1).get(0).getStatus());
        assertEquals("STAFF_REVIEW", h.inTx(() -> service.staffView(tenantA, c)).negotiation().status());
    }

    @Test
    void sendingAnotherTenantsContractNeverFreezesThisNegotiation() {
        // freezeOnSend looks the negotiation up with the caller's tenant, so a call made with the wrong one finds none
        h.inTxDo(() -> service.freezeOnSend(tenantB, a1, staff));
        assertEquals("STAFF_REVIEW", h.inTx(() -> service.staffView(tenantA, a1.getId())).negotiation().status());
    }

    @Test
    void theStaffListNeverMixesTenantsOrContracts() {
        StaffView v = h.inTx(() -> service.staffView(tenantB, b1.getId()));
        assertTrue(v.proposals().isEmpty());
        assertTrue(v.comments().isEmpty());
        assertEquals(1, v.events().size(), "only its own opening is listed");
        assertEquals(1, h.inTx(() -> service.staffView(tenantA, a1.getId())).proposals().size());
        assertEquals(0, h.inTx(() -> service.staffView(tenantA, a2.getId())).proposals().size());
    }

    // ── another contract of the same tenant ──────────────────────────────────

    @Test
    void aProposalIdFromOneContractIsUselessOnAnother() {
        UUID p = proposalA1.id();
        notFound(() -> h.inTx(() -> service.accept(tenantA, a2.getId(), p, null, staff)));
        notFound(() -> h.inTx(() -> service.reject(tenantA, a2.getId(), p, "no", staff)));
        notFound(() -> h.inTx(() -> service.withdraw(tenantA, a2.getId(), h.party(a2, 0).getId(), p, null)));
        notFound(() -> h.inTx(() -> service.staffComment(tenantA, a2.getId(), new StaffCommentRequest("hi", null, null, p, null, null, false), staff)));
        assertEquals("PENDING_REVIEW", h.proposalsOf(a1).get(0).getStatus());
    }

    @Test
    void aPartyOfOneContractCannotActOnAnother() {
        UUID foreignParty = h.party(a2, 0).getId();
        notFound(() -> h.inTx(() -> service.submitProposal(tenantA, a1.getId(), foreignParty, new ProposalInput("DELETE", 0, P1, null, null), null)));
        notFound(() -> h.inTx(() -> service.counterpartyView(tenantA, a1.getId(), foreignParty)));
        notFound(() -> h.inTx(() -> service.counterpartyComment(tenantA, a1.getId(), foreignParty, new CounterpartyCommentInput("hi", null, null, null, null), null)));
        assertEquals(1, h.proposalsOf(a1).size());
        assertEquals(0, h.proposalsOf(a2).size());
    }

    @Test
    void aCommentThreadCannotBeContinuedFromAnotherContract() {
        CommentView c = h.inTx(() -> service.staffComment(tenantA, a1.getId(), new StaffCommentRequest("question", null, null, null, null, null, false), staff));
        notFound(() -> h.inTx(() -> service.staffComment(tenantA, a2.getId(), new StaffCommentRequest("reply", null, null, null, c.id(), null, false), staff)));
        notFound(() -> h.inTx(() -> service.counterpartyComment(tenantA, a2.getId(), h.party(a2, 0).getId(), new CounterpartyCommentInput("reply", null, null, null, c.id()), null)));
    }

    @Test
    void aProposalOnAContractIsNeverShownThroughAnotherContractsView() {
        CounterpartyView v = h.inTx(() -> service.counterpartyView(tenantA, a2.getId(), h.party(a2, 0).getId()));
        assertTrue(v.proposals().isEmpty());
        CounterpartyView own = h.inTx(() -> service.counterpartyView(tenantA, a1.getId(), h.party(a1, 0).getId()));
        assertEquals(1, own.proposals().size());
        assertTrue(h.inTx(() -> service.counterpartyView(tenantA, a1.getId(), h.party(a1, 1).getId())).proposals().isEmpty(), "the other party on the same contract sees none of it");
    }

    // ── links ────────────────────────────────────────────────────────────────

    private ContractNegotiationAccessService access() { return h.access; }

    @Test
    void anotherTenantCannotIssueListOrRevokeLinks() {
        IssuedLinkView link = h.inTx(() -> access().issueLink(tenantA, a1.getId(), h.party(a1, 0).getId(), staff));
        notFound(() -> access().issueLink(tenantB, a1.getId(), h.party(a1, 0).getId(), staff));
        notFound(() -> access().revokeLink(tenantB, a1.getId(), link.linkId(), staff));
        assertTrue(access().links(tenantB, a1.getId()).isEmpty());
        assertEquals(1, access().links(tenantA, a1.getId()).size());
        assertNull(h.links.get(0).getRevokedAt());
    }

    @Test
    void aLinkRevokeCannotBeAimedThroughAnotherContract() {
        IssuedLinkView link = h.inTx(() -> access().issueLink(tenantA, a1.getId(), h.party(a1, 0).getId(), staff));
        notFound(() -> access().revokeLink(tenantA, a2.getId(), link.linkId(), staff));
        assertNull(h.links.get(0).getRevokedAt());
    }

    @Test
    void aPartyCannotBeInvitedToAContractItIsNotOn() {
        notFound(() -> h.inTx(() -> access().issueLink(tenantA, a1.getId(), h.party(a2, 0).getId(), staff)));
    }

    @Test
    void aSessionFromOneContractsLinkNeverOpensAnother() {
        ContractParty p1 = h.party(a1, 0), p2 = h.party(a2, 0);
        String token1 = tokenOf(h.inTx(() -> access().issueLink(tenantA, a1.getId(), p1.getId(), staff)));
        String token2 = tokenOf(h.inTx(() -> access().issueLink(tenantA, a2.getId(), p2.getId(), staff)));
        String session1 = access().verifyCode(token1, "123456", "1.1.1.1").sessionToken();
        HandyFlowException e = assertThrows(HandyFlowException.class, () -> access().authenticate(token2, session1));
        assertEquals("NEGOTIATION_SESSION_REQUIRED", e.getErrorCode());
        Access ok = access().authenticate(token1, session1);
        assertEquals(a1.getId(), ok.contractId());
        assertEquals(tenantA, ok.tenantId());
    }

    @Test
    void theIdentityBehindALinkIsFixedAtIssueAndCarriesItsOwnTenant() {
        String tokenB = tokenOf(h.inTx(() -> access().issueLink(tenantB, b1.getId(), h.party(b1, 0).getId(), staff)));
        String session = access().verifyCode(tokenB, "123456", "1.1.1.1").sessionToken();
        Access a = access().authenticate(tokenB, session);
        assertEquals(tenantB, a.tenantId());
        assertEquals(b1.getId(), a.contractId());
        // acting with that identity reaches only tenant B's contract
        ProposalView made = h.inTx(() -> access().propose(a, new ProposalInput("DELETE", 0, P1, null, null), "2.2.2.2"));
        ContractNegotiationProposal stored = h.proposals.stream().filter(p -> p.getId().equals(made.id())).findFirst().orElseThrow();
        assertEquals(tenantB.getValue(), stored.getTenantId());
        assertEquals(b1.getId(), stored.getContractId());
        assertEquals(1, h.proposalsOf(a1).size(), "tenant A's negotiation is unchanged");
    }

    private static String tokenOf(IssuedLinkView v) { return v.url().substring(v.url().lastIndexOf('/') + 1); }
}
