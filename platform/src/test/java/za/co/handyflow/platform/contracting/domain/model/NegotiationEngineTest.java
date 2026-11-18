package za.co.handyflow.platform.contracting.domain.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NegotiationEngineTest {

    static final UUID T = UUID.randomUUID(), C = UUID.randomUUID(), N = UUID.randomUUID();
    static final UUID A = UUID.randomUUID(), B = UUID.randomUUID();
    static final String BODY = "<p>Payment is due in 30 days.</p><p>Either party may terminate on 60 days notice.</p><p>This agreement is governed by South African law.</p>";

    static ContractNegotiationProposal replace(UUID party, int idx, String original, String proposed) {
        return ContractNegotiationProposal.create(T, C, N, party, "REPLACE", 1, idx, original, proposed, "because");
    }

    @Test
    void replaceDeleteAndAddAreAppliedByWording() {
        var r = replace(A, 0, "<p>Payment is due in 30 days.</p>", "<p>Payment is due in 14 days.</p>");
        var d = ContractNegotiationProposal.create(T, C, N, A, "DELETE", 1, 2, "<p>This agreement is governed by South African law.</p>", null, null);
        var a = ContractNegotiationProposal.create(T, C, N, B, "ADD", 1, 1, "<p>Either party may terminate on 60 days notice.</p>", "<p>Notices must be in writing.</p>", null);
        String out = NegotiationEngine.apply(BODY, List.of(r, d, a));
        assertEquals("<p>Payment is due in 14 days.</p><p>Either party may terminate on 60 days notice.</p><p>Notices must be in writing.</p>", out);
    }

    @Test
    void additionAtTheVeryStartNeedsNoAnchor() {
        var a = ContractNegotiationProposal.create(T, C, N, A, "ADD", 1, -1, null, "<p>Definitions.</p>", null);
        assertTrue(NegotiationEngine.apply(BODY, List.of(a)).startsWith("<p>Definitions.</p><p>Payment"));
    }

    @Test
    void unrelatedEditsMadeSinceTheProposalAreKept() {
        var r = replace(A, 0, "<p>Payment is due in 30 days.</p>", "<p>Payment is due in 14 days.</p>");
        String edited = "<p>Preamble added later.</p>" + BODY.replace("60 days notice", "90 days notice");
        String out = NegotiationEngine.apply(edited, List.of(r));
        assertTrue(out.contains("Preamble added later."));
        assertTrue(out.contains("90 days notice"));
        assertTrue(out.contains("14 days"));
        assertFalse(out.contains("30 days"));
    }

    @Test
    void whenTheWordingHasGoneNothingIsApplied() {
        var r = replace(A, 0, "<p>Payment is due in 30 days.</p>", "<p>Payment is due in 14 days.</p>");
        var other = replace(A, 1, "<p>Either party may terminate on 60 days notice.</p>", "<p>Either party may terminate on 30 days notice.</p>");
        String changed = BODY.replace("Payment is due in 30 days.", "Payment is due in 45 days.");
        assertFalse(NegotiationEngine.targetExists(changed, r));
        assertThrows(IllegalStateException.class, () -> NegotiationEngine.apply(changed, List.of(other, r)));
    }

    @Test
    void sameWordingTwiceIsFoundNearTheHint() {
        String body = "<p>Fees apply.</p><p>Other.</p><p>Fees apply.</p>";
        var r = replace(A, 2, "<p>Fees apply.</p>", "<p>No fees apply.</p>");
        assertEquals("<p>Fees apply.</p><p>Other.</p><p>No fees apply.</p>", NegotiationEngine.apply(body, List.of(r)));
    }

    @Test
    void whitespaceDifferencesDoNotHideTheTarget() {
        var r = replace(A, 0, "<p>Payment  is due\n in 30 days.</p>", "<p>Payment is due in 14 days.</p>");
        assertTrue(NegotiationEngine.targetExists(BODY, r));
    }

    @Test
    void twoPartiesOnTheSameWordingClash() {
        var pa = replace(A, 0, "<p>Payment is due in 30 days.</p>", "<p>Payment is due in 14 days.</p>");
        var pb = replace(B, 0, "<p>Payment is due in 30 days.</p>", "<p>Payment is due in 60 days.</p>");
        var other = replace(B, 1, "<p>Either party may terminate on 60 days notice.</p>", "<p>Either party may terminate on 30 days notice.</p>");
        Map<UUID, UUID> c = NegotiationEngine.conflicts(List.of(pa, pb, other));
        assertEquals(pb.getId(), c.get(pa.getId()));
        assertEquals(pa.getId(), c.get(pb.getId()));
        assertFalse(c.containsKey(other.getId()));
    }

    @Test
    void anAcceptedProposalWinsAndHoldsTheOthersInConflict() {
        var pa = replace(A, 0, "<p>Payment is due in 30 days.</p>", "<p>Payment is due in 14 days.</p>");
        var pb = replace(B, 0, "<p>Payment is due in 30 days.</p>", "<p>Payment is due in 60 days.</p>");
        pa.accept(null, UUID.randomUUID());
        Map<UUID, UUID> c = NegotiationEngine.conflicts(List.of(pa, pb));
        assertEquals(Map.of(pb.getId(), pa.getId()), c);
    }

    @Test
    void closedProposalsDoNotClash() {
        var pa = replace(A, 0, "<p>Payment is due in 30 days.</p>", "<p>Payment is due in 14 days.</p>");
        var pb = replace(B, 0, "<p>Payment is due in 30 days.</p>", "<p>Payment is due in 60 days.</p>");
        pb.reject("No", UUID.randomUUID());
        assertTrue(NegotiationEngine.conflicts(List.of(pa, pb)).isEmpty());
    }

    // ── entity rules ─────────────────────────────────────────────────────────

    @Test
    void proposalRules() {
        assertThrows(IllegalArgumentException.class, () -> ContractNegotiationProposal.create(T, C, N, A, "REPLACE", 1, 0, "<p>x</p>", "<p>x</p>", null));
        assertThrows(IllegalArgumentException.class, () -> ContractNegotiationProposal.create(T, C, N, A, "DELETE", 1, 0, "<p>x</p>", "<p>y</p>", null));
        assertThrows(IllegalArgumentException.class, () -> ContractNegotiationProposal.create(T, C, N, A, "ADD", 1, 0, "<p>x</p>", " ", null));
        assertThrows(IllegalArgumentException.class, () -> ContractNegotiationProposal.create(T, C, N, A, "EDIT", 1, 0, "<p>x</p>", "<p>y</p>", null));
        assertThrows(IllegalArgumentException.class, () -> ContractNegotiationProposal.create(T, C, N, A, "REPLACE", 1, 0, null, "<p>y</p>", null));
    }

    @Test
    void proposedWordingIsSanitised() {
        var p = replace(A, 0, "<p>Payment is due in 30 days.</p>", "<p>Pay <script>alert(1)</script>now</p>");
        assertFalse(p.getProposedHtml().contains("<script"));
    }

    @Test
    void proposalLifecycle() {
        var p = replace(A, 0, "<p>a</p>", "<p>b</p>");
        assertThrows(IllegalArgumentException.class, () -> p.reject(" ", UUID.randomUUID()));
        assertThrows(IllegalStateException.class, () -> p.withdraw(B));
        p.markConflict(UUID.randomUUID());
        assertThrows(IllegalStateException.class, () -> p.accept(null, UUID.randomUUID()));
        p.clearConflict();
        p.accept("ok", UUID.randomUUID());
        assertThrows(IllegalStateException.class, () -> p.accept(null, UUID.randomUUID()));
        p.markApplied(4, UUID.randomUUID());
        assertEquals("APPLIED", p.getStatus());
        assertThrows(IllegalStateException.class, () -> p.withdraw(A));
        assertThrows(IllegalStateException.class, () -> p.reject("late", UUID.randomUUID()));
    }

    @Test
    void negotiationStatusFollowsTheProposals() {
        LocalDate today = LocalDate.now();
        var n = ContractNegotiation.open(T, C, 1, today.plusDays(7), UUID.randomUUID(), today);
        assertEquals("OPEN", n.getStatus());
        n.refresh(2, 0); assertEquals("STAFF_REVIEW", n.getStatus());
        n.refresh(0, 1); assertEquals("CHANGES_REQUIRED", n.getStatus());
        assertThrows(IllegalStateException.class, () -> n.markReady(0, 1, 2, "h", UUID.randomUUID()));
        assertThrows(IllegalStateException.class, () -> n.markReady(1, 0, 2, "h", UUID.randomUUID()));
        n.refresh(0, 0); assertEquals("OPEN", n.getStatus());
        n.markReady(0, 0, 2, "h", UUID.randomUUID());
        assertEquals("READY_FOR_SIGNATURE", n.getStatus());
        assertEquals(Integer.valueOf(2), n.getFinalVersionNo());
        n.refresh(5, 0); assertEquals("READY_FOR_SIGNATURE", n.getStatus(), "a pinned negotiation is not recalculated");
        assertFalse(n.acceptsCounterpartyInput(today));
        n.reopen();
        assertNull(n.getFinalBodyHash());
        n.close("done", UUID.randomUUID());
        assertThrows(IllegalStateException.class, () -> n.close("again", UUID.randomUUID()));
    }

    @Test
    void deadlineStopsCounterpartyInput() {
        LocalDate today = LocalDate.now();
        var n = ContractNegotiation.open(T, C, 1, today, UUID.randomUUID(), today);
        assertTrue(n.acceptsCounterpartyInput(today));
        assertFalse(n.acceptsCounterpartyInput(today.plusDays(1)));
        assertThrows(IllegalArgumentException.class, () -> ContractNegotiation.open(T, C, 1, today.minusDays(1), UUID.randomUUID(), today));
    }

    @Test
    void internalNotesAndOtherPartiesThreadsAreInvisibleToACounterparty() {
        var internal = ContractNegotiationComment.staff(T, C, N, UUID.randomUUID(), null, null, null, null, null, "note", true);
        var forA = ContractNegotiationComment.staff(T, C, N, UUID.randomUUID(), A, null, null, null, null, "hi A", false);
        var forAll = ContractNegotiationComment.staff(T, C, N, UUID.randomUUID(), null, null, null, null, null, "hi all", false);
        var fromB = ContractNegotiationComment.counterparty(T, C, N, B, null, null, null, null, "from B");
        assertFalse(internal.visibleTo(A));
        assertTrue(forA.visibleTo(A));
        assertFalse(forA.visibleTo(B));
        assertTrue(forAll.visibleTo(B));
        assertFalse(fromB.visibleTo(A));
    }
}
