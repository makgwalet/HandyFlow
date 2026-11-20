package za.co.handyflow.platform.legalcompliance.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import za.co.handyflow.platform.legalcompliance.domain.model.*;
import za.co.handyflow.platform.legalcompliance.domain.repository.*;
import za.co.handyflow.platform.legalcompliance.dto.VotingDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.lang.reflect.Proxy;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** The voting flow end to end, with in-memory stand-ins for the repositories. */
class ResolutionVotingServiceTest {

    private static final TenantId T = TenantId.of(UUID.randomUUID());
    private static final LocalDate TODAY = LocalDate.now(), MEETING = TODAY.minusDays(3);

    private final List<String> audit = new ArrayList<>();
    private final List<ResolutionBallot> ballotStore = new ArrayList<>();
    private final List<ResolutionVote> voteStore = new ArrayList<>();

    private Committee committee;
    private CommitteeMeeting meeting;
    private CommitteeResolution decision, action;
    private CommitteeMember anna, ben, cara, observer;
    private ResolutionVotingService service;

    @SuppressWarnings("unchecked")
    private static <R> R proxy(Class<R> type, java.util.function.BiFunction<String, Object[], Object> handler) {
        return (R) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (p, m, args) -> {
            if (m.getName().equals("toString")) return type.getSimpleName();
            return handler.apply(m.getName(), args == null ? new Object[0] : args);
        });
    }

    @BeforeEach
    void setUp() {
        committee = Committee.create(T, "COM-00001", "Risk committee", CommitteeType.RISK, null, 3, 2, 12, null, "me");
        anna = CommitteeMember.add(T, committee.getId(), "Anna", null, MemberRole.CHAIR, MEETING.minusDays(100), TODAY);
        ben = CommitteeMember.add(T, committee.getId(), "Ben", null, MemberRole.MEMBER, MEETING.minusDays(100), TODAY);
        cara = CommitteeMember.add(T, committee.getId(), "Cara", null, MemberRole.MEMBER, MEETING.minusDays(100), TODAY);
        observer = CommitteeMember.add(T, committee.getId(), "Obi", null, MemberRole.OBSERVER, MEETING.minusDays(100), TODAY);
        meeting = CommitteeMeeting.schedule(T, committee.getId(), MEETING, "Q4 meeting", null);
        meeting.hold(List.of(anna.getId().toString(), ben.getId().toString(), cara.getId().toString(), observer.getId().toString()), 3, 2, TODAY);
        decision = CommitteeResolution.create(T, meeting.getId(), committee.getId(), ResolutionKind.DECISION, "Approve the revised risk appetite statement", null, null, MEETING);
        action = CommitteeResolution.create(T, meeting.getId(), committee.getId(), ResolutionKind.ACTION, "Circulate the revised statement to all staff", "Anna", TODAY.plusDays(14), MEETING);

        List<CommitteeMember> all = List.of(anna, ben, cara, observer);
        CommitteeRepository committees = proxy(CommitteeRepository.class, (n, a) -> n.equals("findForTenant") ? Optional.of(committee) : null);
        CommitteeMemberRepository members = proxy(CommitteeMemberRepository.class, (n, a) -> n.equals("findForCommittee") ? all : null);
        CommitteeMeetingRepository meetings = proxy(CommitteeMeetingRepository.class, (n, a) -> n.equals("findForTenant") ? Optional.of(meeting) : null);
        CommitteeResolutionRepository resolutions = proxy(CommitteeResolutionRepository.class, (n, a) ->
                n.equals("findForTenant") ? Optional.ofNullable(a[1].equals(decision.getId()) ? decision : a[1].equals(action.getId()) ? action : null) : null);
        ResolutionBallotRepository ballots = proxy(ResolutionBallotRepository.class, (n, a) -> switch (n) {
            case "findForResolution" -> ballotStore.stream().filter(b -> b.getResolutionId().equals(a[1])).findFirst();
            case "findForMeeting" -> ballotStore;
            case "save" -> { if (!ballotStore.contains(a[0])) ballotStore.add((ResolutionBallot) a[0]); yield a[0]; }
            default -> null;
        });
        ResolutionVoteRepository votes = proxy(ResolutionVoteRepository.class, (n, a) -> switch (n) {
            case "findForResolution" -> voteStore.stream().filter(v -> v.getResolutionId().equals(a[1])).toList();
            case "save" -> { if (!voteStore.contains(a[0])) voteStore.add((ResolutionVote) a[0]); yield a[0]; }
            default -> null;
        });
        RegulatoryAuditService trail = new RegulatoryAuditService(null) {
            @Override public void record(UUID t, String e, UUID id, String ev, String d, UUID a, String n) { audit.add(ev + "|" + d); }
        };
        service = new ResolutionVotingService(committees, members, meetings, resolutions, ballots, votes, trail);
    }

    private CastRequest cast(Object... pairs) {
        List<VoteInput> in = new ArrayList<>();
        for (int i = 0; i < pairs.length; i += 2) in.add(new VoteInput(((CommitteeMember) pairs[i]).getId(), (VoteChoice) pairs[i + 1], null));
        return new CastRequest(in);
    }

    private VotingView castAll(VoteChoice a, VoteChoice b, VoteChoice c) {
        return service.cast(T, committee.getId(), meeting.getId(), decision.getId(), cast(anna, a, ben, b, cara, c), null, "Sec");
    }

    @Test @DisplayName("Record every vote, close, and the outcome is worked out and frozen")
    void happyPath() {
        VotingView v = castAll(VoteChoice.FOR, VoteChoice.FOR, VoteChoice.AGAINST);
        assertEquals(BallotStatus.OPEN, v.status());
        assertTrue(v.canVote());
        assertTrue(v.problems().isEmpty());
        assertEquals(4, v.voters().size());
        assertFalse(v.voters().stream().filter(x -> x.name().equals("Obi")).findFirst().orElseThrow().eligible(), "an observer is shown but cannot vote");
        VotingView closed = service.close(T, committee.getId(), meeting.getId(), decision.getId(), null, "Sec");
        assertEquals(BallotStatus.CLOSED, closed.status());
        assertEquals(BallotOutcome.CARRIED, closed.outcome());
        assertEquals(new TallyView(2, 1, 0, 0), closed.tally());
        assertFalse(closed.canVote());
        HandyFlowException e = assertThrows(HandyFlowException.class, () -> castAll(VoteChoice.AGAINST, VoteChoice.AGAINST, VoteChoice.AGAINST));
        assertEquals(HttpStatus.CONFLICT, e.getStatus());
        assertEquals(BallotOutcome.CARRIED, service.view(T, committee.getId(), meeting.getId(), decision.getId()).outcome());
    }

    @Test @DisplayName("A vote can be changed while voting is open")
    void changeVote() {
        castAll(VoteChoice.AGAINST, VoteChoice.AGAINST, VoteChoice.FOR);
        service.cast(T, committee.getId(), meeting.getId(), decision.getId(), cast(anna, VoteChoice.FOR, ben, VoteChoice.FOR), null, "Sec");
        assertEquals(3, voteStore.size(), "changed in place, not added again");
        VotingView closed = service.close(T, committee.getId(), meeting.getId(), decision.getId(), null, "Sec");
        assertEquals(new TallyView(3, 0, 0, 0), closed.tally());
    }

    @Test @DisplayName("A special resolution needs 75%")
    void special() {
        service.setType(T, committee.getId(), meeting.getId(), decision.getId(), ResolutionType.SPECIAL, null, "Sec");
        castAll(VoteChoice.FOR, VoteChoice.FOR, VoteChoice.AGAINST);
        assertEquals(BallotOutcome.NOT_CARRIED, service.close(T, committee.getId(), meeting.getId(), decision.getId(), null, "Sec").outcome());
    }

    @Test @DisplayName("Closing is refused while a present voter has not voted, and the message names them")
    void missing() {
        service.cast(T, committee.getId(), meeting.getId(), decision.getId(), cast(anna, VoteChoice.FOR, ben, VoteChoice.FOR), null, "Sec");
        HandyFlowException e = assertThrows(HandyFlowException.class, () -> service.close(T, committee.getId(), meeting.getId(), decision.getId(), null, "Sec"));
        assertEquals(HttpStatus.CONFLICT, e.getStatus());
        assertTrue(e.getMessage().contains("Cara"));
        assertEquals(BallotStatus.OPEN, service.view(T, committee.getId(), meeting.getId(), decision.getId()).status());
    }

    @Test @DisplayName("Observers, absentees and strangers cannot be given a vote")
    void notEntitled() {
        HandyFlowException e = assertThrows(HandyFlowException.class, () -> service.cast(T, committee.getId(), meeting.getId(), decision.getId(), cast(observer, VoteChoice.FOR), null, "Sec"));
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
        assertThrows(HandyFlowException.class, () -> service.cast(T, committee.getId(), meeting.getId(), decision.getId(),
                new CastRequest(List.of(new VoteInput(UUID.randomUUID(), VoteChoice.FOR, null))), null, "Sec"));
        assertEquals(0, voteStore.size());
    }

    @Test @DisplayName("A member cannot have two votes in one request")
    void duplicate() {
        assertThrows(HandyFlowException.class, () -> service.cast(T, committee.getId(), meeting.getId(), decision.getId(), cast(anna, VoteChoice.FOR, anna, VoteChoice.AGAINST), null, "Sec"));
    }

    @Test @DisplayName("A recusal without a reason is refused")
    void recusalNeedsReason() {
        HandyFlowException e = assertThrows(HandyFlowException.class, () -> service.cast(T, committee.getId(), meeting.getId(), decision.getId(), cast(anna, VoteChoice.RECUSED), null, "Sec"));
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
    }

    @Test @DisplayName("Only decisions can be voted on")
    void actionsRefused() {
        HandyFlowException e = assertThrows(HandyFlowException.class, () -> service.cast(T, committee.getId(), meeting.getId(), action.getId(), cast(anna, VoteChoice.FOR), null, "Sec"));
        assertEquals(HttpStatus.CONFLICT, e.getStatus());
    }

    @Test @DisplayName("Once the minutes are approved, voting is read-only")
    void minutesApproved() {
        castAll(VoteChoice.FOR, VoteChoice.FOR, VoteChoice.FOR);
        meeting.recordMinutes("The committee met and discussed the revised risk appetite statement at length.", UUID.randomUUID(), "Writer");
        meeting.approveMinutes(UUID.randomUUID(), "Approver", TODAY);
        assertThrows(HandyFlowException.class, () -> service.close(T, committee.getId(), meeting.getId(), decision.getId(), null, "Sec"));
        VotingView v = service.view(T, committee.getId(), meeting.getId(), decision.getId());
        assertFalse(v.canVote());
        assertEquals(3, v.tally().votesFor());
    }

    @Test @DisplayName("The audit trail gets counts and the outcome, never who voted how")
    void auditHasNoNames() {
        castAll(VoteChoice.FOR, VoteChoice.AGAINST, VoteChoice.FOR);
        service.close(T, committee.getId(), meeting.getId(), decision.getId(), null, "Sec");
        assertEquals(2, audit.size());
        assertTrue(audit.get(0).startsWith("VOTES_RECORDED|") && audit.get(0).contains("3 vote(s)"));
        assertTrue(audit.get(1).startsWith("VOTE_CLOSED|") && audit.get(1).contains("CARRIED (2 for, 1 against, 0 abstained, 0 recused)"));
        for (String line : audit) for (String n : new String[]{"Anna", "Ben", "Cara"}) assertFalse(line.contains(n), line);
    }

    @Test @DisplayName("The meeting summary lists each ballot with its live tally")
    void summaries() {
        service.cast(T, committee.getId(), meeting.getId(), decision.getId(), cast(anna, VoteChoice.FOR), null, "Sec");
        List<BallotSummary> s = service.forMeeting(T, committee.getId(), meeting.getId());
        assertEquals(1, s.size());
        assertEquals(BallotStatus.OPEN, s.get(0).status());
        assertEquals(new TallyView(1, 0, 0, 0), s.get(0).tally());
        assertNull(s.get(0).outcome());
    }

    @Test @DisplayName("Viewing a decision nobody has voted on yet does not create a ballot")
    void viewIsReadOnly() {
        VotingView v = service.view(T, committee.getId(), meeting.getId(), decision.getId());
        assertEquals(ResolutionType.ORDINARY, v.type());
        assertEquals(0, ballotStore.size());
        assertEquals(0, voteStore.size());
    }

    // ── Chunk 23: the chair's casting vote ──

    private VotingView tie() { return castAll(VoteChoice.FOR, VoteChoice.AGAINST, VoteChoice.ABSTAIN); }

    private VotingView casting(VoteChoice choice, boolean charter) {
        return service.closeWithCastingVote(T, committee.getId(), meeting.getId(), decision.getId(), new CastingVoteRequest(choice, "Standing order 12.3: the chair has a casting vote", charter), null, "Sec");
    }

    @Test @DisplayName("A tie is flagged, with the chair who can break it, and the counts are kept exactly as voted")
    void castingFlow() {
        VotingView open = tie();
        assertTrue(open.tied());
        assertEquals("Anna", open.castingChair());
        VotingView closed = casting(VoteChoice.FOR, true);
        assertEquals(BallotStatus.CLOSED, closed.status());
        assertEquals(BallotOutcome.CARRIED, closed.outcome());
        assertEquals(new TallyView(1, 1, 1, 0), closed.tally(), "the casting vote is not added to the counts");
        assertEquals(VoteChoice.FOR, closed.castingVote());
        assertEquals("Anna", closed.castingVoteBy());
        assertTrue(closed.castingVoteReason().contains("Standing order"));
        assertEquals(BallotOutcome.CARRIED, service.view(T, committee.getId(), meeting.getId(), decision.getId()).outcome());
    }

    @Test @DisplayName("A casting vote against means not carried")
    void castingAgainst() {
        tie();
        assertEquals(BallotOutcome.NOT_CARRIED, casting(VoteChoice.AGAINST, true).outcome());
    }

    @Test @DisplayName("It is refused unless the closer confirms the charter allows it, and nothing is closed")
    void needsCharterConfirmation() {
        tie();
        HandyFlowException e = assertThrows(HandyFlowException.class, () -> casting(VoteChoice.FOR, false));
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
        assertEquals(BallotStatus.OPEN, service.view(T, committee.getId(), meeting.getId(), decision.getId()).status());
        assertNull(ballotStore.get(0).getCastingVote());
    }

    @Test @DisplayName("It is refused when the votes are not tied, and for a special resolution")
    void onlyOnTie() {
        castAll(VoteChoice.FOR, VoteChoice.FOR, VoteChoice.AGAINST);
        HandyFlowException e = assertThrows(HandyFlowException.class, () -> casting(VoteChoice.FOR, true));
        assertEquals(HttpStatus.CONFLICT, e.getStatus());
        assertFalse(service.view(T, committee.getId(), meeting.getId(), decision.getId()).tied());
        service.setType(T, committee.getId(), meeting.getId(), decision.getId(), ResolutionType.SPECIAL, null, "Sec");
        castAll(VoteChoice.FOR, VoteChoice.AGAINST, VoteChoice.ABSTAIN);
        assertThrows(HandyFlowException.class, () -> casting(VoteChoice.FOR, true));
        assertFalse(service.view(T, committee.getId(), meeting.getId(), decision.getId()).tied());
        assertEquals(BallotStatus.OPEN, ballotStore.get(0).getStatus());
    }

    @Test @DisplayName("Everyone still has to have voted, and a casting vote needs a reason")
    void sameClosingRules() {
        service.cast(T, committee.getId(), meeting.getId(), decision.getId(), cast(anna, VoteChoice.FOR, ben, VoteChoice.AGAINST), null, "Sec");
        HandyFlowException e = assertThrows(HandyFlowException.class, () -> casting(VoteChoice.FOR, true));
        assertTrue(e.getMessage().contains("Cara"));
        castAll(VoteChoice.FOR, VoteChoice.AGAINST, VoteChoice.ABSTAIN);
        HandyFlowException r = assertThrows(HandyFlowException.class, () -> service.closeWithCastingVote(T, committee.getId(), meeting.getId(), decision.getId(), new CastingVoteRequest(VoteChoice.FOR, "short", true), null, "Sec"));
        assertEquals(HttpStatus.BAD_REQUEST, r.getStatus());
        assertEquals(BallotStatus.OPEN, ballotStore.get(0).getStatus());
    }

    @Test @DisplayName("A casting vote must be for or against")
    void forOrAgainstOnly() {
        tie();
        assertThrows(HandyFlowException.class, () -> casting(VoteChoice.ABSTAIN, true));
        assertEquals(BallotStatus.OPEN, ballotStore.get(0).getStatus());
    }

    @Test @DisplayName("A chair who recused has no casting vote, and neither does a committee with no chair present")
    void chairConditions() {
        // Anna (chair) recuses; Ben and Cara split 1-1 → tied, but the chair has a conflict
        service.cast(T, committee.getId(), meeting.getId(), decision.getId(), new CastRequest(List.of(
                new VoteInput(anna.getId(), VoteChoice.RECUSED, "I own shares in the supplier"), new VoteInput(ben.getId(), VoteChoice.FOR, null), new VoteInput(cara.getId(), VoteChoice.AGAINST, null))), null, "Sec");
        VotingView v = service.view(T, committee.getId(), meeting.getId(), decision.getId());
        assertTrue(v.tied());
        assertNull(v.castingChair(), "the screen must not offer it");
        HandyFlowException e = assertThrows(HandyFlowException.class, () -> casting(VoteChoice.FOR, true));
        assertEquals(HttpStatus.CONFLICT, e.getStatus());
        assertEquals(BallotStatus.OPEN, ballotStore.get(0).getStatus());
    }

    @Test @DisplayName("Once closed, a ballot cannot be closed again with a casting vote")
    void notTwice() {
        tie();
        casting(VoteChoice.FOR, true);
        HandyFlowException e = assertThrows(HandyFlowException.class, () -> casting(VoteChoice.AGAINST, true));
        assertEquals(HttpStatus.CONFLICT, e.getStatus());
        assertEquals(BallotOutcome.CARRIED, ballotStore.get(0).getOutcome());
        assertEquals(VoteChoice.FOR, ballotStore.get(0).getCastingVote());
    }

    @Test @DisplayName("Ordinary close still works on a tie (not carried) for charters with no casting vote, and records no casting vote")
    void plainCloseOnTie() {
        tie();
        VotingView closed = service.close(T, committee.getId(), meeting.getId(), decision.getId(), null, "Sec");
        assertEquals(BallotOutcome.NOT_CARRIED, closed.outcome());
        assertNull(closed.castingVote());
        assertTrue(closed.tied());
    }

    @Test @DisplayName("The audit line says the casting vote decided it, with counts, and names nobody")
    void castingAudit() {
        tie();
        casting(VoteChoice.FOR, true);
        String line = audit.get(audit.size() - 1);
        assertTrue(line.startsWith("VOTE_CLOSED_CASTING|") && line.contains("CARRIED on the chair's casting vote after a tie of 1 for and 1 against"), line);
        for (String n : new String[]{"Anna", "Ben", "Cara"}) assertFalse(line.contains(n), line);
    }
}
