package za.co.handyflow.platform.legalcompliance.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.legalcompliance.application.internal.VotingRules.Tally;
import za.co.handyflow.platform.legalcompliance.domain.model.*;
import za.co.handyflow.platform.legalcompliance.domain.repository.*;
import za.co.handyflow.platform.legalcompliance.dto.ProxyDtos.*;
import za.co.handyflow.platform.legalcompliance.dto.VotingDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Supplier;

/**
 * Votes on committee decisions. A secretary records each present member's vote; the votes can be changed until voting is closed, and closing works out the outcome
 * from the rules in VotingRules and freezes the ballot. Only a DECISION can be voted on, only for a meeting that was held with a quorum and whose minutes are not yet
 * approved (the same window in which decisions can be recorded). Individual votes are never written to the audit trail, only counts and the outcome.
 */
@Service
@RequiredArgsConstructor
public class ResolutionVotingService {

    private static final String ENTITY = "COMMITTEE";

    private final CommitteeRepository committees;
    private final CommitteeMemberRepository members;
    private final CommitteeMeetingRepository meetings;
    private final CommitteeResolutionRepository resolutions;
    private final ResolutionBallotRepository ballots;
    private final ResolutionVoteRepository votes;
    private final RegulatoryAuditService trail;
    private final CommitteeCharterRulesRepository charters;
    private final ProxyAppointmentRepository proxies;
    private final ProxyVoteRepository proxyVotes;

    @Transactional(readOnly = true)
    public VotingView view(TenantId t, UUID cid, UUID mid, UUID rid) {
        Ctx c = load(t, cid, mid, rid);
        return render(t, c, ballots.findForResolution(t, rid).orElse(null));
    }

    @Transactional(readOnly = true)
    public List<BallotSummary> forMeeting(TenantId t, UUID cid, UUID mid) {
        loadMeeting(t, cid, mid);
        List<BallotSummary> out = new ArrayList<>();
        for (ResolutionBallot b : ballots.findForMeeting(t, mid)) out.add(summary(t, b));
        return out;
    }

    @Transactional
    public VotingView setType(TenantId t, UUID cid, UUID mid, UUID rid, ResolutionType type, UUID uid, String uname) {
        Ctx c = writable(t, cid, mid, rid);
        ResolutionBallot b = ballot(t, c);
        guarded(() -> { b.setType(type); return null; });
        ballots.save(b);
        trail.record(t.getValue(), ENTITY, cid, "VOTE_TYPE_SET", abbreviate(c.resolution.getDescription()) + ": " + type, uid, uname);
        return render(t, c, b);
    }

    @Transactional
    public VotingView cast(TenantId t, UUID cid, UUID mid, UUID rid, CastRequest r, UUID uid, String uname) {
        Ctx c = writable(t, cid, mid, rid);
        ResolutionBallot b = ballot(t, c);
        if (!b.isOpen()) throw new HandyFlowException("Voting is closed, so votes can no longer be changed", HttpStatus.CONFLICT, "INVALID_STATE");
        Map<UUID, CommitteeMember> eligible = new HashMap<>();
        for (CommitteeMember m : VotingRules.eligible(c.members, c.meeting.getAttendance(), c.meeting.getMeetingDate())) eligible.put(m.getId(), m);
        Map<UUID, ResolutionVote> existing = new HashMap<>();
        for (ResolutionVote v : votes.findForResolution(t, rid)) existing.put(v.getMemberId(), v);
        Set<UUID> seen = new HashSet<>();
        for (VoteInput in : r.votes()) {
            CommitteeMember m = eligible.get(in.memberId());
            if (m == null) throw new HandyFlowException("That person was not present and entitled to vote at this meeting", HttpStatus.BAD_REQUEST, "VALIDATION");
            if (!seen.add(in.memberId())) throw new HandyFlowException("Each member can have only one vote", HttpStatus.BAD_REQUEST, "VALIDATION");
            ResolutionVote v = existing.get(in.memberId());
            if (v == null) votes.save(guarded(() -> ResolutionVote.cast(t, rid, m.getId(), m.getMemberName(), in.choice(), in.reason(), uname)));
            else { guarded(() -> { v.change(in.choice(), in.reason(), uname); return null; }); votes.save(v); }
        }
        trail.record(t.getValue(), ENTITY, cid, "VOTES_RECORDED", abbreviate(c.resolution.getDescription()) + ": " + r.votes().size() + " vote(s) recorded", uid, uname);
        return render(t, c, b);
    }

    @Transactional
    public VotingView close(TenantId t, UUID cid, UUID mid, UUID rid, UUID uid, String uname) {
        Ctx c = writable(t, cid, mid, rid);
        ResolutionBallot b = ballot(t, c);
        if (!b.isOpen()) throw new HandyFlowException("Voting is already closed", HttpStatus.CONFLICT, "INVALID_STATE");
        List<CommitteeMember> eligible = VotingRules.eligible(c.members, c.meeting.getAttendance(), c.meeting.getMeetingDate());
        Map<UUID, VoteChoice> recorded = choices(votes.findForResolution(t, rid));
        CharterRules.Applied rules = charter(t, cid);
        Proxied px = proxied(t, c, rid);
        List<String> problems = closeProblems(eligible, recorded, c, rules, px);
        if (!problems.isEmpty()) throw new HandyFlowException("Voting cannot be closed yet. " + String.join(". ", problems), HttpStatus.CONFLICT, "INVALID_STATE");
        Tally tally = tallyOf(eligible, recorded, px);
        BallotOutcome outcome = VotingRules.outcome(b.getResolutionType(), tally, rules.specialPercent());
        b.close(outcome, tally, today(), uname);
        ballots.save(b);
        trail.record(t.getValue(), ENTITY, cid, "VOTE_CLOSED", abbreviate(c.resolution.getDescription()) + ": " + outcome + " (" + tally.votesFor() + " for, " + tally.against() + " against, "
                + tally.abstained() + " abstained, " + tally.recused() + " recused" + proxyNote(px) + "), " + b.getResolutionType().name().toLowerCase(Locale.ROOT) + " resolution", uid, uname);
        return render(t, c, b);
    }

    /**
     * Closes a tied ordinary resolution on the chair's casting vote. Only when the votes for and against are equal, the chair was present and entitled to vote and did not recuse,
     * the closing person confirms the charter allows it, and a reason is given. The counts are stored exactly as voted. Plain close() still works on a tie (not carried) for
     * committees whose charter gives the chair no casting vote.
     */
    @Transactional
    public VotingView closeWithCastingVote(TenantId t, UUID cid, UUID mid, UUID rid, CastingVoteRequest r, UUID uid, String uname) {
        Ctx c = writable(t, cid, mid, rid);
        ResolutionBallot b = ballot(t, c);
        if (!b.isOpen()) throw new HandyFlowException("Voting is already closed", HttpStatus.CONFLICT, "INVALID_STATE");
        CharterRules.Applied rules = charter(t, cid);
        String charterProblem = CharterRules.castingVoteProblem(rules, r.charterPermits());
        if (charterProblem != null) throw new HandyFlowException(charterProblem, rules.recorded() ? HttpStatus.CONFLICT : HttpStatus.BAD_REQUEST, rules.recorded() ? "INVALID_STATE" : "VALIDATION");
        List<CommitteeMember> eligible = VotingRules.eligible(c.members, c.meeting.getAttendance(), c.meeting.getMeetingDate());
        Map<UUID, VoteChoice> recorded = choices(votes.findForResolution(t, rid));
        Proxied px = proxied(t, c, rid);
        List<String> problems = closeProblems(eligible, recorded, c, rules, px);
        if (!problems.isEmpty()) throw new HandyFlowException("Voting cannot be closed yet. " + String.join(". ", problems), HttpStatus.CONFLICT, "INVALID_STATE");
        CommitteeMember chair = VotingRules.castingChair(eligible, recorded)
                .orElseThrow(() -> new HandyFlowException("The casting vote belongs to the chair, and there is no chair present who can vote (a chair who recused cannot use it)", HttpStatus.CONFLICT, "INVALID_STATE"));
        Tally tally = tallyOf(eligible, recorded, px);
        BallotOutcome outcome = guarded(() -> VotingRules.outcomeWithCastingVote(b.getResolutionType(), tally, r.choice()));
        guarded(() -> { b.closeWithCastingVote(outcome, tally, r.choice(), chair.getMemberName(), r.reason(), today(), uname); return null; });
        ballots.save(b);
        trail.record(t.getValue(), ENTITY, cid, "VOTE_CLOSED_CASTING", abbreviate(c.resolution.getDescription()) + ": " + outcome + " on the chair's casting vote after a tie of " + tally.votesFor() + " for and "
                + tally.against() + " against (" + tally.abstained() + " abstained, " + tally.recused() + " recused" + proxyNote(px) + ")", uid, uname);
        return render(t, c, b);
    }

    /** Proxy votes on one decision: every live proxy of the meeting, whether it can be used, and what (if anything) was recorded. */
    @Transactional(readOnly = true)
    public ProxyVotingView proxyView(TenantId t, UUID cid, UUID mid, UUID rid) {
        Ctx c = load(t, cid, mid, rid);
        ResolutionBallot b = ballots.findForResolution(t, rid).orElse(null);
        boolean open = b == null || b.isOpen();
        Map<UUID, ProxyVote> byProxy = new HashMap<>();
        for (ProxyVote pv : proxyVotes.findForResolution(t, rid)) byProxy.put(pv.getProxyId(), pv);
        List<ProxyVoterView> out = new ArrayList<>();
        for (ProxyAppointment p : proxies.findForMeeting(t, mid)) {
            if (!p.isActive()) continue;
            String why = ProxyRules.unusableReason(p, c.meeting.getAttendance());
            ProxyVote pv = byProxy.get(p.getId());
            out.add(new ProxyVoterView(p.getId(), p.getGrantorName(), p.getHolderName(), why == null, why, pv == null ? null : pv.getChoice(), pv == null ? null : pv.getReason()));
        }
        boolean canVote = open && c.resolution.getKind() == ResolutionKind.DECISION && c.meeting.canRecordResolutions();
        return new ProxyVotingView(rid, open, canVote, out);
    }

    /** Records proxy votes, like cast() does for members: only for usable proxies, only while voting is open, each proxy once per call. Only the count goes to the audit trail. */
    @Transactional
    public VotingView castProxy(TenantId t, UUID cid, UUID mid, UUID rid, ProxyCastRequest r, UUID uid, String uname) {
        Ctx c = writable(t, cid, mid, rid);
        ResolutionBallot b = ballot(t, c);
        if (!b.isOpen()) throw new HandyFlowException("Voting is closed, so votes can no longer be changed", HttpStatus.CONFLICT, "INVALID_STATE");
        Map<UUID, ProxyAppointment> usable = new HashMap<>();
        for (ProxyAppointment p : proxies.findForMeeting(t, mid)) if (ProxyRules.usable(p, c.meeting.getAttendance())) usable.put(p.getId(), p);
        Map<UUID, ProxyVote> existing = new HashMap<>();
        for (ProxyVote pv : proxyVotes.findForResolution(t, rid)) existing.put(pv.getProxyId(), pv);
        Set<UUID> seen = new HashSet<>();
        for (ProxyVoteInput in : r.votes()) {
            if (!usable.containsKey(in.proxyId())) throw new HandyFlowException("That proxy cannot be used here: it was revoked, the member who gave it attended, or the holder was not present", HttpStatus.BAD_REQUEST, "VALIDATION");
            if (!seen.add(in.proxyId())) throw new HandyFlowException("Each proxy can have only one vote", HttpStatus.BAD_REQUEST, "VALIDATION");
            ProxyVote v = existing.get(in.proxyId());
            if (v == null) proxyVotes.save(guarded(() -> ProxyVote.cast(t, in.proxyId(), rid, in.choice(), in.reason(), uname)));
            else { guarded(() -> { v.change(in.choice(), in.reason(), uname); return null; }); proxyVotes.save(v); }
        }
        trail.record(t.getValue(), ENTITY, cid, "PROXY_VOTES_RECORDED", abbreviate(c.resolution.getDescription()) + ": " + r.votes().size() + " proxy vote(s) recorded", uid, uname);
        return render(t, c, b);
    }

    // ── Helpers ──

    /** The meeting's proxies that can be used for votes (live, grantor absent, holder present) and the proxy votes recorded on this resolution. */
    private record Proxied(List<ProxyAppointment> usable, Map<UUID, VoteChoice> byProxy) {}

    private Proxied proxied(TenantId t, Ctx c, UUID resolutionId) {
        List<ProxyAppointment> usable = proxies.findForMeeting(t, c.meeting.getId()).stream().filter(p -> ProxyRules.usable(p, c.meeting.getAttendance())).toList();
        Map<UUID, VoteChoice> byProxy = new HashMap<>();
        for (ProxyVote pv : proxyVotes.findForResolution(t, resolutionId)) byProxy.put(pv.getProxyId(), pv.getChoice());
        return new Proxied(usable, byProxy);
    }

    private static List<String> closeProblems(List<CommitteeMember> eligible, Map<UUID, VoteChoice> recorded, Ctx c, CharterRules.Applied rules, Proxied px) {
        List<VoteChoice> counted = ProxyRules.countable(px.usable, px.byProxy);
        int proxyCast = (int) counted.stream().filter(v -> v == VoteChoice.FOR || v == VoteChoice.AGAINST).count();
        List<String> out = new ArrayList<>(VotingRules.closeProblems(eligible, recorded, c.committee.getQuorumMin(), rules.quorumAfterRecusal(), proxyCast));
        out.addAll(ProxyRules.missingVotes(px.usable, px.byProxy));
        return out;
    }

    private static Tally tallyOf(List<CommitteeMember> eligible, Map<UUID, VoteChoice> recorded, Proxied px) {
        return VotingRules.tally(concat(eligible.stream().map(m -> recorded.get(m.getId())).toList(), ProxyRules.countable(px.usable, px.byProxy)));
    }

    private Tally summaryTally(TenantId t, ResolutionBallot b) {
        List<VoteChoice> own = votes.findForResolution(t, b.getResolutionId()).stream().map(ResolutionVote::getChoice).toList();
        Optional<CommitteeMeeting> m = meetings.findForTenant(t, b.getMeetingId());
        if (m.isEmpty()) return VotingRules.tally(own);
        Set<UUID> usable = new HashSet<>();
        for (ProxyAppointment p : proxies.findForMeeting(t, b.getMeetingId())) if (ProxyRules.usable(p, m.get().getAttendance())) usable.add(p.getId());
        List<VoteChoice> viaProxy = proxyVotes.findForResolution(t, b.getResolutionId()).stream().filter(pv -> usable.contains(pv.getProxyId())).map(ProxyVote::getChoice).toList();
        return VotingRules.tally(concat(own, viaProxy));
    }

    private static List<VoteChoice> concat(List<VoteChoice> a, List<VoteChoice> b) { List<VoteChoice> out = new ArrayList<>(a); out.addAll(b); return out; }

    private static String proxyNote(Proxied px) {
        int n = ProxyRules.countable(px.usable, px.byProxy).size();
        return n == 0 ? "" : ", including " + n + " by proxy";
    }

    private CharterRules.Applied charter(TenantId t, UUID committeeId) { return CharterRules.current(charters.history(t, committeeId), today()); }

    private record Ctx(Committee committee, CommitteeMeeting meeting, CommitteeResolution resolution, List<CommitteeMember> members) {}

    private Ctx load(TenantId t, UUID cid, UUID mid, UUID rid) {
        CommitteeMeeting m = loadMeeting(t, cid, mid);
        CommitteeResolution r = resolutions.findForTenant(t, rid).orElseThrow(() -> new ResourceNotFoundException("Resolution", rid.toString()));
        if (!r.getMeetingId().equals(mid)) throw new ResourceNotFoundException("Resolution", rid.toString());
        Committee c = committees.findForTenant(t, cid).orElseThrow(() -> new ResourceNotFoundException("Committee", cid.toString()));
        return new Ctx(c, m, r, members.findForCommittee(t, cid));
    }

    /** Loaded for a change: must be a decision, in a meeting that can still record decisions. */
    private Ctx writable(TenantId t, UUID cid, UUID mid, UUID rid) {
        Ctx c = load(t, cid, mid, rid);
        if (c.resolution.getKind() != ResolutionKind.DECISION) throw new HandyFlowException("Only a decision can be voted on, not an action", HttpStatus.CONFLICT, "INVALID_STATE");
        if (!c.meeting.canRecordResolutions()) throw new HandyFlowException("Votes can be recorded only for a meeting held with a quorum and before its minutes are approved", HttpStatus.CONFLICT, "INVALID_STATE");
        return c;
    }

    private CommitteeMeeting loadMeeting(TenantId t, UUID cid, UUID mid) {
        CommitteeMeeting m = meetings.findForTenant(t, mid).orElseThrow(() -> new ResourceNotFoundException("Meeting", mid.toString()));
        if (!m.getCommitteeId().equals(cid)) throw new ResourceNotFoundException("Meeting", mid.toString());
        return m;
    }

    private ResolutionBallot ballot(TenantId t, Ctx c) {
        return ballots.findForResolution(t, c.resolution.getId()).orElseGet(() -> ballots.save(ResolutionBallot.open(t, c.resolution.getId(), c.meeting.getId(), c.committee.getId())));
    }

    private static Map<UUID, VoteChoice> choices(List<ResolutionVote> vs) {
        Map<UUID, VoteChoice> m = new HashMap<>();
        for (ResolutionVote v : vs) m.put(v.getMemberId(), v.getChoice());
        return m;
    }

    private BallotSummary summary(TenantId t, ResolutionBallot b) {
        Tally tally = b.isOpen() ? summaryTally(t, b) : stored(b);
        return new BallotSummary(b.getResolutionId(), b.getResolutionType(), b.getStatus(), b.getOutcome(), view(tally));
    }

    private static Tally stored(ResolutionBallot b) { return new Tally(nz(b.getVotesFor()), nz(b.getVotesAgainst()), nz(b.getAbstained()), nz(b.getRecused())); }

    private static int nz(Integer i) { return i == null ? 0 : i; }

    private static TallyView view(Tally t) { return new TallyView(t.votesFor(), t.against(), t.abstained(), t.recused()); }

    private VotingView render(TenantId t, Ctx c, ResolutionBallot b) {
        ResolutionType type = b == null ? ResolutionType.ORDINARY : b.getResolutionType();
        boolean open = b == null || b.isOpen();
        List<ResolutionVote> vs = votes.findForResolution(t, c.resolution.getId());
        Map<UUID, ResolutionVote> byMember = new HashMap<>();
        for (ResolutionVote v : vs) byMember.put(v.getMemberId(), v);
        Set<UUID> eligibleIds = new HashSet<>();
        List<CommitteeMember> eligible = VotingRules.eligible(c.members, c.meeting.getAttendance(), c.meeting.getMeetingDate());
        for (CommitteeMember m : eligible) eligibleIds.add(m.getId());
        Set<String> present = c.meeting.getAttendance() == null ? Set.of() : new HashSet<>(c.meeting.getAttendance());
        List<VoterView> voters = new ArrayList<>();
        for (CommitteeMember m : c.members) {
            if (!present.contains(m.getId().toString())) continue;
            ResolutionVote v = byMember.get(m.getId());
            voters.add(new VoterView(m.getId(), m.getMemberName(), m.getMemberRole().name(), eligibleIds.contains(m.getId()), v == null ? null : v.getChoice(), v == null ? null : v.getReason()));
        }
        voters.sort(Comparator.comparing(VoterView::name, String.CASE_INSENSITIVE_ORDER));
        Proxied px = proxied(t, c, c.resolution.getId());
        Tally tally = open ? VotingRules.tally(concat(eligible.stream().map(m -> byMember.get(m.getId())).filter(Objects::nonNull).map(ResolutionVote::getChoice).toList(), ProxyRules.countable(px.usable, px.byProxy))) : stored(b);
        boolean canVote = open && c.resolution.getKind() == ResolutionKind.DECISION && c.meeting.canRecordResolutions();
        List<String> problems = open && canVote ? closeProblems(eligible, choices(vs), c, charter(t, c.committee.getId()), px) : List.of();
        boolean tied = VotingRules.tied(type, tally) && (open ? problems.isEmpty() && canVote : true);
        String chair = open && tied ? VotingRules.castingChair(eligible, choices(vs)).map(CommitteeMember::getMemberName).orElse(null) : null;
        return new VotingView(c.resolution.getId(), c.resolution.getDescription(), type, VotingRules.requirement(type, charter(t, c.committee.getId()).specialPercent()), open ? BallotStatus.OPEN : BallotStatus.CLOSED,
                b == null ? null : b.getOutcome(), b == null ? null : b.getClosedOn(), b == null ? null : b.getClosedByName(), view(tally), c.committee.getQuorumMin(), canVote, voters, problems,
                tied, chair, b == null ? null : b.getCastingVote(), b == null ? null : b.getCastingByName(), b == null ? null : b.getCastingReason());
    }

    private static String abbreviate(String s) { return s.length() <= 100 ? s : s.substring(0, 97) + "…"; }

    private static LocalDate today() { return LocalDate.now(ZoneId.of("Africa/Johannesburg")); }

    private static <T> T guarded(Supplier<T> action) {
        try { return action.get(); }
        catch (IllegalArgumentException e) { throw new HandyFlowException(e.getMessage(), HttpStatus.BAD_REQUEST, "VALIDATION"); }
        catch (IllegalStateException e) { throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "INVALID_STATE"); }
    }
}
