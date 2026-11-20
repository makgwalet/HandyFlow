package za.co.handyflow.platform.legalcompliance.application.internal;

import za.co.handyflow.platform.legalcompliance.domain.model.CommitteeMember;
import za.co.handyflow.platform.legalcompliance.domain.model.MemberRole;
import za.co.handyflow.platform.legalcompliance.domain.model.ProxyAppointment;
import za.co.handyflow.platform.legalcompliance.domain.model.VoteChoice;

import java.time.LocalDate;
import java.util.*;

/**
 * Pure rules for committee proxies. House rules, deliberately cautious; check them against your charters.
 *  • A proxy is for one meeting, made while the meeting is still SCHEDULED.
 *  • Both people must be voting members (not observers) who are on the committee on the meeting date.
 *  • One live proxy per grantor per meeting, and one holder may carry at most {@value #MAX_PER_HOLDER}.
 *  • No chains: a holder cannot also have given a proxy, and a grantor cannot also hold one.
 *  • A proxy can be used only if the grantor did NOT attend and the holder DID. A grantor who turns up takes their own vote back automatically.
 *  • Proxies do not count toward quorum.
 */
public final class ProxyRules {

    private ProxyRules() {}

    public static final int MAX_PER_HOLDER = 2;

    private static boolean voting(CommitteeMember m, LocalDate on) { return m.getMemberRole() != MemberRole.OBSERVER && VotingRules.memberOn(m, on); }

    /** What stops this proxy from being made. Null means it can be. live = the meeting's ACTIVE proxies. */
    public static String appointProblem(CommitteeMember grantor, CommitteeMember holder, LocalDate meetingDate, List<ProxyAppointment> live) {
        if (grantor == null || holder == null) return "Choose two members of this committee";
        if (grantor.getId().equals(holder.getId())) return "A member cannot be their own proxy";
        if (!voting(grantor, meetingDate)) return grantor.getMemberName() + " cannot give a proxy: only voting members who were on the committee on the meeting date can";
        if (!voting(holder, meetingDate)) return holder.getMemberName() + " cannot hold a proxy: only voting members who were on the committee on the meeting date can";
        if (live.stream().anyMatch(p -> p.getGrantorMemberId().equals(grantor.getId()))) return grantor.getMemberName() + " already has a proxy for this meeting. Revoke it first";
        if (live.stream().filter(p -> p.getHolderMemberId().equals(holder.getId())).count() >= MAX_PER_HOLDER) return holder.getMemberName() + " already holds " + MAX_PER_HOLDER + " proxies, which is the most one member can carry";
        if (live.stream().anyMatch(p -> p.getHolderMemberId().equals(grantor.getId()))) return grantor.getMemberName() + " is holding a proxy for someone else, so cannot also give one";
        if (live.stream().anyMatch(p -> p.getGrantorMemberId().equals(holder.getId()))) return holder.getMemberName() + " has given a proxy to someone else, so cannot also hold one";
        return null;
    }

    /** Usable for a vote: live, the grantor absent and the holder present. Attendance is the list of member ids recorded when the meeting was held. */
    public static boolean usable(ProxyAppointment p, Collection<String> attendeeIds) {
        Set<String> present = attendeeIds == null ? Set.of() : new HashSet<>(attendeeIds);
        return p.isActive() && !present.contains(p.getGrantorMemberId().toString()) && present.contains(p.getHolderMemberId().toString());
    }

    /** Why a live proxy cannot be used, for display. Null when it can. */
    public static String unusableReason(ProxyAppointment p, Collection<String> attendeeIds) {
        if (!p.isActive()) return "Revoked";
        Set<String> present = attendeeIds == null ? Set.of() : new HashSet<>(attendeeIds);
        if (present.contains(p.getGrantorMemberId().toString())) return p.getGrantorName() + " attended, so votes for themselves";
        if (!present.contains(p.getHolderMemberId().toString())) return p.getHolderName() + " was not present, so cannot vote for " + p.getGrantorName();
        return null;
    }

    /** Votes still needed before voting can close: each usable proxy needs a vote (abstain if the holder did not vote). */
    public static List<String> missingVotes(List<ProxyAppointment> usable, Map<UUID, VoteChoice> byProxy) {
        return usable.stream().filter(p -> !byProxy.containsKey(p.getId()))
                .map(p -> "A proxy vote is still needed from " + p.getHolderName() + " for " + p.getGrantorName() + " (record ABSTAIN if no vote was given)").toList();
    }

    /** The choices to add to the tally: those of usable proxies only. */
    public static List<VoteChoice> countable(List<ProxyAppointment> usable, Map<UUID, VoteChoice> byProxy) {
        return usable.stream().map(p -> byProxy.get(p.getId())).filter(Objects::nonNull).toList();
    }
}
