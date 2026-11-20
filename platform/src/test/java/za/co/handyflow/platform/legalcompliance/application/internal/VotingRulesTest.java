package za.co.handyflow.platform.legalcompliance.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.legalcompliance.application.internal.VotingRules.Tally;
import za.co.handyflow.platform.legalcompliance.domain.model.*;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class VotingRulesTest {

    private static final LocalDate MEETING = LocalDate.of(2026, 11, 10);
    private static final TenantId T = TenantId.of(UUID.randomUUID());

    private static Tally t(int f, int a, int ab, int r) { return new Tally(f, a, ab, r); }

    private static CommitteeMember member(String name, MemberRole role, LocalDate appointed) {
        return CommitteeMember.add(T, UUID.randomUUID(), name, null, role, appointed, LocalDate.of(2026, 11, 19));
    }

    private static List<String> ids(CommitteeMember... ms) { return Arrays.stream(ms).map(m -> m.getId().toString()).toList(); }

    @Test @DisplayName("Ordinary: more than half of the votes for or against; a tie is not carried")
    void ordinary() {
        assertEquals(BallotOutcome.CARRIED, VotingRules.outcome(ResolutionType.ORDINARY, t(3, 2, 0, 0)));
        assertEquals(BallotOutcome.NOT_CARRIED, VotingRules.outcome(ResolutionType.ORDINARY, t(2, 2, 0, 0)));
        assertEquals(BallotOutcome.NOT_CARRIED, VotingRules.outcome(ResolutionType.ORDINARY, t(1, 2, 0, 0)));
        assertEquals(BallotOutcome.CARRIED, VotingRules.outcome(ResolutionType.ORDINARY, t(1, 0, 0, 0)));
    }

    @Test @DisplayName("Abstentions and recusals never count for or against")
    void abstentionsIgnored() {
        assertEquals(BallotOutcome.CARRIED, VotingRules.outcome(ResolutionType.ORDINARY, t(2, 1, 5, 3)));
        assertEquals(BallotOutcome.CARRIED, VotingRules.outcome(ResolutionType.ORDINARY, t(1, 0, 6, 0)));
    }

    @Test @DisplayName("Special: at least 75% of the votes for or against, in whole-number maths")
    void special() {
        assertEquals(BallotOutcome.CARRIED, VotingRules.outcome(ResolutionType.SPECIAL, t(3, 1, 0, 0)));     // exactly 75%
        assertEquals(BallotOutcome.NOT_CARRIED, VotingRules.outcome(ResolutionType.SPECIAL, t(5, 2, 0, 0)));  // 71.4%
        assertEquals(BallotOutcome.CARRIED, VotingRules.outcome(ResolutionType.SPECIAL, t(6, 2, 9, 0)));      // 75% of for/against; abstentions ignored
        assertEquals(BallotOutcome.CARRIED, VotingRules.outcome(ResolutionType.SPECIAL, t(4, 0, 0, 0)));
        assertEquals(BallotOutcome.NOT_CARRIED, VotingRules.outcome(ResolutionType.SPECIAL, t(2, 2, 0, 0)));
    }

    @Test @DisplayName("No votes for or against is never carried")
    void nobody() {
        assertEquals(BallotOutcome.NOT_CARRIED, VotingRules.outcome(ResolutionType.ORDINARY, t(0, 0, 4, 0)));
        assertEquals(BallotOutcome.NOT_CARRIED, VotingRules.outcome(ResolutionType.SPECIAL, t(0, 0, 0, 0)));
    }

    @Test @DisplayName("Tally counts each kind of vote")
    void tally() {
        Tally x = VotingRules.tally(List.of(VoteChoice.FOR, VoteChoice.FOR, VoteChoice.AGAINST, VoteChoice.ABSTAIN, VoteChoice.RECUSED, VoteChoice.RECUSED));
        assertEquals(new Tally(2, 1, 1, 2), x);
        assertEquals(3, x.cast());
        assertEquals(new Tally(0, 0, 0, 0), VotingRules.tally(List.of()));
    }

    @Test @DisplayName("Eligible voters: present, on the committee on the day, and not observers")
    void eligible() {
        CommitteeMember chair = member("Chair", MemberRole.CHAIR, LocalDate.of(2025, 1, 1));
        CommitteeMember secretary = member("Sec", MemberRole.SECRETARY, LocalDate.of(2025, 1, 1));
        CommitteeMember observer = member("Obs", MemberRole.OBSERVER, LocalDate.of(2025, 1, 1));
        CommitteeMember absent = member("Absent", MemberRole.MEMBER, LocalDate.of(2025, 1, 1));
        CommitteeMember later = member("Later", MemberRole.MEMBER, LocalDate.of(2026, 11, 15));          // joined after the meeting
        CommitteeMember ended = member("Ended", MemberRole.MEMBER, LocalDate.of(2025, 1, 1));
        ended.end(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 19));                                // left before the meeting
        List<CommitteeMember> all = List.of(chair, secretary, observer, absent, later, ended);
        List<CommitteeMember> e = VotingRules.eligible(all, ids(chair, secretary, observer, later, ended), MEETING);
        assertEquals(List.of("Chair", "Sec"), e.stream().map(CommitteeMember::getMemberName).toList());
        assertEquals(List.of(), VotingRules.eligible(all, null, MEETING));
    }

    @Test @DisplayName("A member who left on the day of the meeting could still vote")
    void leftOnTheDay() {
        CommitteeMember m = member("M", MemberRole.MEMBER, LocalDate.of(2025, 1, 1));
        m.end(MEETING, LocalDate.of(2026, 11, 19));
        assertEquals(1, VotingRules.eligible(List.of(m), ids(m), MEETING).size());
    }

    @Test @DisplayName("Voting cannot close while a present voter has no recorded vote")
    void missingVotes() {
        CommitteeMember a = member("Anna", MemberRole.MEMBER, LocalDate.of(2025, 1, 1)), b = member("Ben", MemberRole.MEMBER, LocalDate.of(2025, 1, 1));
        Map<UUID, VoteChoice> votes = new HashMap<>();
        votes.put(a.getId(), VoteChoice.FOR);
        List<String> p = VotingRules.closeProblems(List.of(a, b), votes, 1);
        assertEquals(1, p.size());
        assertTrue(p.get(0).contains("Ben") && !p.get(0).contains("Anna") && p.get(0).contains("ABSTAIN"));
        votes.put(b.getId(), VoteChoice.AGAINST);
        assertTrue(VotingRules.closeProblems(List.of(a, b), votes, 2).isEmpty());
    }

    @Test @DisplayName("Quorum must survive recusals")
    void quorumAfterRecusal() {
        CommitteeMember a = member("A", MemberRole.MEMBER, LocalDate.of(2025, 1, 1)), b = member("B", MemberRole.MEMBER, LocalDate.of(2025, 1, 1)), c = member("C", MemberRole.MEMBER, LocalDate.of(2025, 1, 1));
        Map<UUID, VoteChoice> votes = new HashMap<>();
        votes.put(a.getId(), VoteChoice.FOR); votes.put(b.getId(), VoteChoice.RECUSED); votes.put(c.getId(), VoteChoice.RECUSED);
        List<String> p = VotingRules.closeProblems(List.of(a, b, c), votes, 2);
        assertEquals(1, p.size());
        assertTrue(p.get(0).contains("Only 1 member(s)") && p.get(0).contains("quorum is 2"));
        assertTrue(VotingRules.closeProblems(List.of(a, b, c), votes, 1).isEmpty());
    }

    @Test @DisplayName("All abstaining cannot be closed")
    void allAbstain() {
        CommitteeMember a = member("A", MemberRole.MEMBER, LocalDate.of(2025, 1, 1)), b = member("B", MemberRole.MEMBER, LocalDate.of(2025, 1, 1));
        Map<UUID, VoteChoice> votes = new HashMap<>();
        votes.put(a.getId(), VoteChoice.ABSTAIN); votes.put(b.getId(), VoteChoice.ABSTAIN);
        List<String> p = VotingRules.closeProblems(List.of(a, b), votes, 2);
        assertEquals(List.of("Nobody has voted for or against"), p);
    }

    @Test @DisplayName("A recusal needs a reason; other votes do not")
    void recusalReason() {
        UUID r = UUID.randomUUID(), m = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> ResolutionVote.cast(T, r, m, "M", VoteChoice.RECUSED, null, "sec"));
        assertThrows(IllegalArgumentException.class, () -> ResolutionVote.cast(T, r, m, "M", VoteChoice.RECUSED, "short", "sec"));
        assertThrows(IllegalArgumentException.class, () -> ResolutionVote.cast(T, r, m, "M", null, null, "sec"));
        ResolutionVote v = ResolutionVote.cast(T, r, m, "M", VoteChoice.RECUSED, "Owns shares in the supplier", "sec");
        assertEquals(VoteChoice.RECUSED, v.getChoice());
        v.change(VoteChoice.FOR, null, "sec");
        assertNull(v.getReason());
        assertThrows(IllegalArgumentException.class, () -> v.change(VoteChoice.RECUSED, "x", "sec"));
        assertEquals(VoteChoice.FOR, v.getChoice(), "a rejected change leaves the vote as it was");
    }

    @Test @DisplayName("Closing freezes a ballot: type cannot change and it cannot be closed twice")
    void ballotLifecycle() {
        ResolutionBallot b = ResolutionBallot.open(T, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        assertTrue(b.isOpen());
        assertEquals(ResolutionType.ORDINARY, b.getResolutionType());
        b.setType(ResolutionType.SPECIAL);
        assertThrows(IllegalArgumentException.class, () -> b.setType(null));
        b.close(BallotOutcome.CARRIED, new Tally(3, 1, 0, 0), MEETING, "sec");
        assertFalse(b.isOpen());
        assertEquals(BallotOutcome.CARRIED, b.getOutcome());
        assertEquals(Integer.valueOf(3), b.getVotesFor());
        assertThrows(IllegalStateException.class, () -> b.setType(ResolutionType.ORDINARY));
        assertThrows(IllegalStateException.class, () -> b.close(BallotOutcome.NOT_CARRIED, new Tally(0, 1, 0, 0), MEETING, "sec"));
        assertEquals(BallotOutcome.CARRIED, b.getOutcome());
    }

    @Test @DisplayName("The requirement text names the rule")
    void requirementText() {
        assertTrue(VotingRules.requirement(ResolutionType.SPECIAL).contains("75%"));
        assertTrue(VotingRules.requirement(ResolutionType.ORDINARY).contains("tie is not carried"));
    }

    @Test @DisplayName("Only an ordinary resolution with equal votes for and against is tied; no votes at all is not a tie")
    void tied() {
        assertTrue(VotingRules.tied(ResolutionType.ORDINARY, t(2, 2, 0, 0)));
        assertTrue(VotingRules.tied(ResolutionType.ORDINARY, t(1, 1, 3, 1)));
        assertFalse(VotingRules.tied(ResolutionType.ORDINARY, t(0, 0, 3, 0)));
        assertFalse(VotingRules.tied(ResolutionType.ORDINARY, t(3, 2, 0, 0)));
        assertFalse(VotingRules.tied(ResolutionType.SPECIAL, t(2, 2, 0, 0)), "a special resolution needs 75%, which one vote cannot supply");
    }

    @Test @DisplayName("A casting vote for carries a tie and a casting vote against does not; it is refused in every other case")
    void castingOutcome() {
        assertEquals(BallotOutcome.CARRIED, VotingRules.outcomeWithCastingVote(ResolutionType.ORDINARY, t(2, 2, 0, 0), VoteChoice.FOR));
        assertEquals(BallotOutcome.NOT_CARRIED, VotingRules.outcomeWithCastingVote(ResolutionType.ORDINARY, t(2, 2, 1, 0), VoteChoice.AGAINST));
        assertThrows(IllegalStateException.class, () -> VotingRules.outcomeWithCastingVote(ResolutionType.ORDINARY, t(3, 2, 0, 0), VoteChoice.FOR));
        assertThrows(IllegalStateException.class, () -> VotingRules.outcomeWithCastingVote(ResolutionType.ORDINARY, t(0, 0, 2, 0), VoteChoice.FOR));
        assertThrows(IllegalStateException.class, () -> VotingRules.outcomeWithCastingVote(ResolutionType.SPECIAL, t(2, 2, 0, 0), VoteChoice.FOR));
        assertThrows(IllegalArgumentException.class, () -> VotingRules.outcomeWithCastingVote(ResolutionType.ORDINARY, t(2, 2, 0, 0), VoteChoice.ABSTAIN));
        assertThrows(IllegalArgumentException.class, () -> VotingRules.outcomeWithCastingVote(ResolutionType.ORDINARY, t(2, 2, 0, 0), VoteChoice.RECUSED));
    }

    @Test @DisplayName("The casting chair is a present chair who has not recused; a member or secretary never is")
    void castingChair() {
        CommitteeMember chair = member("Chloe", MemberRole.CHAIR, MEETING.minusDays(50)), other = member("Mo", MemberRole.MEMBER, MEETING.minusDays(50));
        assertEquals(chair, VotingRules.castingChair(List.of(chair, other), Map.of(chair.getId(), VoteChoice.FOR, other.getId(), VoteChoice.AGAINST)).orElseThrow());
        assertEquals(chair, VotingRules.castingChair(List.of(chair, other), Map.of(chair.getId(), VoteChoice.ABSTAIN)).orElseThrow(), "a chair who abstained may still break the tie");
        assertTrue(VotingRules.castingChair(List.of(chair, other), Map.of(chair.getId(), VoteChoice.RECUSED)).isEmpty(), "a recused chair has a conflict");
        assertTrue(VotingRules.castingChair(List.of(other), Map.of()).isEmpty());
    }
}
