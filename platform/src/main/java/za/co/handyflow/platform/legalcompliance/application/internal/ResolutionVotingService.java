package za.co.handyflow.platform.legalcompliance.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.legalcompliance.application.internal.VotingRules.Tally;
import za.co.handyflow.platform.legalcompliance.domain.model.*;
import za.co.handyflow.platform.legalcompliance.domain.repository.*;
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
        List<String> problems = VotingRules.closeProblems(eligible, recorded, c.committee.getQuorumMin());
        if (!problems.isEmpty()) throw new HandyFlowException("Voting cannot be closed yet. " + String.join(". ", problems), HttpStatus.CONFLICT, "INVALID_STATE");
        Tally tally = VotingRules.tally(eligible.stream().map(m -> recorded.get(m.getId())).toList());
        BallotOutcome outcome = VotingRules.outcome(b.getResolutionType(), tally);
        b.close(outcome, tally, today(), uname);
        ballots.save(b);
        trail.record(t.getValue(), ENTITY, cid, "VOTE_CLOSED", abbreviate(c.resolution.getDescription()) + ": " + outcome + " (" + tally.votesFor() + " for, " + tally.against() + " against, "
                + tally.abstained() + " abstained, " + tally.recused() + " recused), " + b.getResolutionType().name().toLowerCase(Locale.ROOT) + " resolution", uid, uname);
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
        if (!r.charterPermits()) throw new HandyFlowException("Confirm that the committee's charter or standing orders give the chair a casting vote", HttpStatus.BAD_REQUEST, "VALIDATION");
        List<CommitteeMember> eligible = VotingRules.eligible(c.members, c.meeting.getAttendance(), c.meeting.getMeetingDate());
        Map<UUID, VoteChoice> recorded = choices(votes.findForResolution(t, rid));
        List<String> problems = VotingRules.closeProblems(eligible, recorded, c.committee.getQuorumMin());
        if (!problems.isEmpty()) throw new HandyFlowException("Voting cannot be closed yet. " + String.join(". ", problems), HttpStatus.CONFLICT, "INVALID_STATE");
        CommitteeMember chair = VotingRules.castingChair(eligible, recorded)
                .orElseThrow(() -> new HandyFlowException("The casting vote belongs to the chair, and there is no chair present who can vote (a chair who recused cannot use it)", HttpStatus.CONFLICT, "INVALID_STATE"));
        Tally tally = VotingRules.tally(eligible.stream().map(m -> recorded.get(m.getId())).toList());
        BallotOutcome outcome = guarded(() -> VotingRules.outcomeWithCastingVote(b.getResolutionType(), tally, r.choice()));
        guarded(() -> { b.closeWithCastingVote(outcome, tally, r.choice(), chair.getMemberName(), r.reason(), today(), uname); return null; });
        ballots.save(b);
        trail.record(t.getValue(), ENTITY, cid, "VOTE_CLOSED_CASTING", abbreviate(c.resolution.getDescription()) + ": " + outcome + " on the chair's casting vote after a tie of " + tally.votesFor() + " for and "
                + tally.against() + " against (" + tally.abstained() + " abstained, " + tally.recused() + " recused)", uid, uname);
        return render(t, c, b);
    }

    // ── Helpers ──

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
        Tally tally = b.isOpen() ? VotingRules.tally(votes.findForResolution(t, b.getResolutionId()).stream().map(ResolutionVote::getChoice).toList()) : stored(b);
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
        Tally tally = open ? VotingRules.tally(eligible.stream().map(m -> byMember.get(m.getId())).filter(Objects::nonNull).map(ResolutionVote::getChoice).toList()) : stored(b);
        boolean canVote = open && c.resolution.getKind() == ResolutionKind.DECISION && c.meeting.canRecordResolutions();
        List<String> problems = open && canVote ? VotingRules.closeProblems(eligible, choices(vs), c.committee.getQuorumMin()) : List.of();
        boolean tied = VotingRules.tied(type, tally) && (open ? problems.isEmpty() && canVote : true);
        String chair = open && tied ? VotingRules.castingChair(eligible, choices(vs)).map(CommitteeMember::getMemberName).orElse(null) : null;
        return new VotingView(c.resolution.getId(), c.resolution.getDescription(), type, VotingRules.requirement(type), open ? BallotStatus.OPEN : BallotStatus.CLOSED,
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
