package za.co.handyflow.platform.legalcompliance.application.internal;

import za.co.handyflow.platform.legalcompliance.domain.model.BallotOutcome;
import za.co.handyflow.platform.legalcompliance.domain.model.CommitteeMember;
import za.co.handyflow.platform.legalcompliance.domain.model.MemberRole;
import za.co.handyflow.platform.legalcompliance.domain.model.ResolutionType;
import za.co.handyflow.platform.legalcompliance.domain.model.VoteChoice;

import java.time.LocalDate;
import java.util.*;

/**
 * Pure rules for counting committee votes. Only votes FOR and AGAINST decide the result: abstentions and recusals are recorded but not counted either way. An ordinary
 * resolution is carried by MORE than half of those votes (a tie is not carried); a special resolution needs at least 75% of them. All maths is in whole numbers.
 *
 * Who may vote: members who were present at the meeting, were on the committee on the meeting date, and are not observers (the same people who count toward quorum).
 * Voting can close only when every one of them has a recorded vote (a member who simply did not vote is recorded as ABSTAIN), when enough members remain after recusals to
 * make up the committee's quorum, and when at least one vote was for or against. The quorum-after-recusal rule is a deliberately cautious house rule: confirm it suits your
 * committees' charters before relying on it.
 */
public final class VotingRules {

    private VotingRules() {}

    public record Tally(int votesFor, int against, int abstained, int recused) {
        public int cast() { return votesFor + against; }
    }

    public static Tally tally(Collection<VoteChoice> choices) {
        int f = 0, a = 0, ab = 0, r = 0;
        for (VoteChoice c : choices) {
            switch (c) {
                case FOR -> f++;
                case AGAINST -> a++;
                case ABSTAIN -> ab++;
                case RECUSED -> r++;
            }
        }
        return new Tally(f, a, ab, r);
    }

    public static BallotOutcome outcome(ResolutionType type, Tally t) {
        int cast = t.cast();
        if (cast == 0) return BallotOutcome.NOT_CARRIED;
        boolean carried = type == ResolutionType.SPECIAL ? t.votesFor() * 4 >= cast * 3 : t.votesFor() * 2 > cast;
        return carried ? BallotOutcome.CARRIED : BallotOutcome.NOT_CARRIED;
    }

    /** A tie on an ordinary resolution: at least one vote cast and exactly as many for as against. A special resolution is never "tied" (it needs 75%, which a casting vote cannot supply). */
    public static boolean tied(ResolutionType type, Tally t) { return type == ResolutionType.ORDINARY && t.cast() > 0 && t.votesFor() == t.against(); }

    /** The outcome when the chair breaks a tie: for carries it, against does not. Refused unless the resolution is ordinary and really tied. */
    public static BallotOutcome outcomeWithCastingVote(ResolutionType type, Tally t, VoteChoice casting) {
        if (casting != VoteChoice.FOR && casting != VoteChoice.AGAINST) throw new IllegalArgumentException("A casting vote must be for or against");
        if (type != ResolutionType.ORDINARY) throw new IllegalStateException("A casting vote can be used only on an ordinary resolution");
        if (!tied(type, t)) throw new IllegalStateException("A casting vote can be used only when the votes for and against are equal");
        return casting == VoteChoice.FOR ? BallotOutcome.CARRIED : BallotOutcome.NOT_CARRIED;
    }

    /** The chair who may cast it: a chair who was present, is entitled to vote and has not recused. Empty when there is none. */
    public static Optional<CommitteeMember> castingChair(List<CommitteeMember> eligible, Map<UUID, VoteChoice> votes) {
        return eligible.stream().filter(m -> m.getMemberRole() == MemberRole.CHAIR && votes.get(m.getId()) != VoteChoice.RECUSED).findFirst();
    }

    public static String requirement(ResolutionType type) {
        return type == ResolutionType.SPECIAL ? "Special resolution: at least 75% of the votes for or against must be for"
                : "Ordinary resolution: more than half of the votes for or against must be for (a tie is not carried)";
    }

    static boolean memberOn(CommitteeMember m, LocalDate on) { return !m.getAppointedOn().isAfter(on) && (m.getEndedOn() == null || !m.getEndedOn().isBefore(on)); }

    /** Present, on the committee on the meeting date, and not an observer. */
    public static List<CommitteeMember> eligible(Collection<CommitteeMember> members, Collection<String> attendeeIds, LocalDate meetingDate) {
        Set<String> present = attendeeIds == null ? Set.of() : new HashSet<>(attendeeIds);
        return members.stream().filter(m -> present.contains(m.getId().toString()) && memberOn(m, meetingDate) && m.getMemberRole() != MemberRole.OBSERVER)
                .sorted(Comparator.comparing(CommitteeMember::getMemberName, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    /** What stops voting from being closed. Empty means it can be closed. */
    public static List<String> closeProblems(List<CommitteeMember> eligible, Map<UUID, VoteChoice> votes, int quorumMin) {
        List<String> out = new ArrayList<>();
        List<String> missing = eligible.stream().filter(m -> !votes.containsKey(m.getId())).map(CommitteeMember::getMemberName).toList();
        if (!missing.isEmpty()) out.add("A vote is still needed from: " + String.join(", ", missing) + " (record ABSTAIN for a member who did not vote)");
        List<VoteChoice> counted = eligible.stream().map(m -> votes.get(m.getId())).filter(Objects::nonNull).toList();
        Tally t = tally(counted);
        int able = eligible.size() - t.recused();
        if (able < quorumMin) out.add("Only " + able + " member(s) can vote after recusals, but the committee's quorum is " + quorumMin);
        if (t.cast() == 0) out.add("Nobody has voted for or against");
        return out;
    }
}
