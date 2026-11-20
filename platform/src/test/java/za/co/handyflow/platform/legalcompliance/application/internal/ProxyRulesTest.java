package za.co.handyflow.platform.legalcompliance.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.legalcompliance.domain.model.*;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ProxyRulesTest {

    private static final TenantId T = TenantId.of(UUID.randomUUID());
    private static final LocalDate TODAY = LocalDate.now(), MEETING = TODAY.plusDays(5);
    private final UUID committee = UUID.randomUUID(), meeting = UUID.randomUUID();

    private CommitteeMember member(String name, MemberRole role, LocalDate from) { return CommitteeMember.add(T, committee, name, null, role, from, TODAY); }

    private ProxyAppointment proxy(CommitteeMember g, CommitteeMember h) {
        return ProxyAppointment.appoint(T, committee, meeting, g.getId(), g.getMemberName(), h.getId(), h.getMemberName(), "Clause 7.2", true, null, "Sec");
    }

    private final CommitteeMember anna = member("Anna", MemberRole.CHAIR, TODAY.minusDays(100)), ben = member("Ben", MemberRole.MEMBER, TODAY.minusDays(100)),
            cara = member("Cara", MemberRole.MEMBER, TODAY.minusDays(100)), dev = member("Dev", MemberRole.MEMBER, TODAY.minusDays(100)), obi = member("Obi", MemberRole.OBSERVER, TODAY.minusDays(100));

    @Test @DisplayName("A normal proxy between two voting members is allowed")
    void allowed() { assertNull(ProxyRules.appointProblem(dev, ben, MEETING, List.of())); }

    @Test @DisplayName("Observers, the same person, people not yet on the committee and people who have left cannot be involved")
    void eligibility() {
        assertNotNull(ProxyRules.appointProblem(obi, ben, MEETING, List.of()));
        assertNotNull(ProxyRules.appointProblem(dev, obi, MEETING, List.of()));
        assertEquals("A member cannot be their own proxy", ProxyRules.appointProblem(dev, dev, MEETING, List.of()));
        assertNotNull(ProxyRules.appointProblem(dev, ben, TODAY.minusDays(200), List.of()), "not yet on the committee on the meeting date");
        CommitteeMember gone = member("Gone", MemberRole.MEMBER, TODAY.minusDays(100));
        gone.end(TODAY.minusDays(10), TODAY);
        assertNotNull(ProxyRules.appointProblem(gone, ben, MEETING, List.of()));
        assertNotNull(ProxyRules.appointProblem(null, ben, MEETING, List.of()));
    }

    @Test @DisplayName("One live proxy per grantor, at most two per holder")
    void limits() {
        assertNotNull(ProxyRules.appointProblem(dev, anna, MEETING, List.of(proxy(dev, ben))));
        CommitteeMember e1 = member("E1", MemberRole.MEMBER, TODAY.minusDays(100)), e2 = member("E2", MemberRole.MEMBER, TODAY.minusDays(100));
        List<ProxyAppointment> live = List.of(proxy(e1, ben), proxy(e2, ben));
        assertTrue(ProxyRules.appointProblem(dev, ben, MEETING, live).contains("most one member can carry"));
        assertNull(ProxyRules.appointProblem(dev, ben, MEETING, List.of(proxy(e1, ben))), "the second one is fine");
    }

    @Test @DisplayName("No chains in either direction")
    void chains() {
        ProxyAppointment devToBen = proxy(dev, ben);
        assertTrue(ProxyRules.appointProblem(ben, cara, MEETING, List.of(devToBen)).contains("holding a proxy"));
        assertTrue(ProxyRules.appointProblem(cara, dev, MEETING, List.of(devToBen)).contains("has given a proxy"));
    }

    @Test @DisplayName("Usable only if the grantor was absent and the holder present, and only while live")
    void usable() {
        ProxyAppointment p = proxy(dev, ben);
        String ben_ = ben.getId().toString(), dev_ = dev.getId().toString();
        assertTrue(ProxyRules.usable(p, List.of(ben_)));
        assertFalse(ProxyRules.usable(p, List.of(ben_, dev_)), "the grantor attended");
        assertFalse(ProxyRules.usable(p, List.of()), "the holder was absent");
        assertFalse(ProxyRules.usable(p, null));
        assertNull(ProxyRules.unusableReason(p, List.of(ben_)));
        assertTrue(ProxyRules.unusableReason(p, List.of(ben_, dev_)).contains("attended"));
        assertTrue(ProxyRules.unusableReason(p, List.of()).contains("not present"));
        p.revoke("No longer able to act", "Sec", java.time.Instant.now());
        assertFalse(ProxyRules.usable(p, List.of(ben_)));
        assertEquals("Revoked", ProxyRules.unusableReason(p, List.of(ben_)));
    }

    @Test @DisplayName("Missing proxy votes are listed; only recorded ones count")
    void votes() {
        ProxyAppointment p1 = proxy(dev, ben), p2 = proxy(cara, anna);
        Map<UUID, VoteChoice> by = new HashMap<>(Map.of(p1.getId(), VoteChoice.FOR));
        assertEquals(1, ProxyRules.missingVotes(List.of(p1, p2), by).size());
        assertTrue(ProxyRules.missingVotes(List.of(p1, p2), by).get(0).contains("Anna"));
        assertEquals(List.of(VoteChoice.FOR), ProxyRules.countable(List.of(p1, p2), by));
        assertTrue(ProxyRules.missingVotes(List.of(), by).isEmpty());
    }

    @Test @DisplayName("Proxies never help a meeting reach quorum: quorum after recusal still looks only at members")
    void quorumUnaffected() {
        Map<UUID, VoteChoice> v = Map.of(ben.getId(), VoteChoice.FOR, cara.getId(), VoteChoice.RECUSED);
        List<String> p = VotingRules.closeProblems(List.of(ben, cara), v, 2, true, 5);
        assertTrue(p.stream().anyMatch(x -> x.contains("quorum")));
        assertTrue(VotingRules.closeProblems(List.of(ben, cara), Map.of(ben.getId(), VoteChoice.ABSTAIN, cara.getId(), VoteChoice.ABSTAIN), 2, true, 1).isEmpty(), "a proxy vote for or against counts as somebody voting");
        assertTrue(VotingRules.closeProblems(List.of(ben, cara), Map.of(ben.getId(), VoteChoice.ABSTAIN, cara.getId(), VoteChoice.ABSTAIN), 2, true, 0).stream().anyMatch(x -> x.contains("Nobody")));
    }

    @Test @DisplayName("The appointment itself: charter confirmation, clause, instructions and revocation")
    void appointment() {
        UUID g = dev.getId(), h = ben.getId();
        assertThrows(IllegalArgumentException.class, () -> ProxyAppointment.appoint(T, committee, meeting, g, "Dev", h, "Ben", "Clause 7.2", false, null, "S"));
        assertThrows(IllegalArgumentException.class, () -> ProxyAppointment.appoint(T, committee, meeting, g, "Dev", h, "Ben", "  ", true, null, "S"));
        assertThrows(IllegalArgumentException.class, () -> ProxyAppointment.appoint(T, committee, meeting, g, "Dev", g, "Dev", "Clause 7.2", true, null, "S"));
        assertThrows(IllegalArgumentException.class, () -> ProxyAppointment.appoint(T, committee, meeting, g, "Dev", h, "Ben", "Clause 7.2", true, "x".repeat(501), "S"));
        ProxyAppointment p = proxy(dev, ben);
        assertThrows(IllegalArgumentException.class, () -> p.revoke("short", "S", java.time.Instant.now()));
        p.revoke("Dev is able to attend", "S", java.time.Instant.now());
        assertEquals(ProxyStatus.REVOKED, p.getStatus());
        assertThrows(IllegalStateException.class, () -> p.revoke("Dev is able to attend", "S", java.time.Instant.now()));
        assertThrows(IllegalArgumentException.class, () -> ProxyVote.cast(T, p.getId(), UUID.randomUUID(), VoteChoice.RECUSED, "A long enough reason", "S"));
    }
}
