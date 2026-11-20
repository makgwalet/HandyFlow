package za.co.handyflow.platform.legalcompliance.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import za.co.handyflow.platform.legalcompliance.domain.model.*;
import za.co.handyflow.platform.legalcompliance.domain.repository.*;
import za.co.handyflow.platform.legalcompliance.dto.ProxyDtos.*;
import za.co.handyflow.platform.legalcompliance.dto.VotingDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.lang.reflect.Proxy;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** Proxies end to end: appoint before the meeting, hold it, record member and proxy votes, close. In-memory stand-ins for the repositories. */
class ProxyServiceTest {

    private static final TenantId T = TenantId.of(UUID.randomUUID());
    private static final LocalDate TODAY = LocalDate.now(), MEETING = TODAY.minusDays(3);
    private static final String CLAUSE = "Charter clause 7.2";

    private final List<String> audit = new ArrayList<>();
    private final List<ResolutionBallot> ballotStore = new ArrayList<>();
    private final List<ResolutionVote> voteStore = new ArrayList<>();
    private final List<ProxyAppointment> proxyStore = new ArrayList<>();
    private final List<ProxyVote> proxyVoteStore = new ArrayList<>();

    private Committee committee;
    private CommitteeMeeting meeting;
    private CommitteeResolution decision;
    private CommitteeMember anna, ben, cara, dev, eve, observer;
    private ProxyService proxies;
    private ResolutionVotingService voting;

    @SuppressWarnings("unchecked")
    private static <R> R proxy(Class<R> type, java.util.function.BiFunction<String, Object[], Object> handler) {
        return (R) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (p, m, args) -> m.getName().equals("toString") ? type.getSimpleName() : handler.apply(m.getName(), args == null ? new Object[0] : args));
    }

    @BeforeEach
    void setUp() {
        committee = Committee.create(T, "COM-00001", "Risk committee", CommitteeType.RISK, null, 3, 2, 12, null, "me");
        LocalDate since = MEETING.minusDays(100);
        anna = CommitteeMember.add(T, committee.getId(), "Anna", null, MemberRole.CHAIR, since, TODAY);
        ben = CommitteeMember.add(T, committee.getId(), "Ben", null, MemberRole.MEMBER, since, TODAY);
        cara = CommitteeMember.add(T, committee.getId(), "Cara", null, MemberRole.MEMBER, since, TODAY);
        dev = CommitteeMember.add(T, committee.getId(), "Dev", null, MemberRole.MEMBER, since, TODAY);
        eve = CommitteeMember.add(T, committee.getId(), "Eve", null, MemberRole.MEMBER, since, TODAY);
        observer = CommitteeMember.add(T, committee.getId(), "Obi", null, MemberRole.OBSERVER, since, TODAY);
        meeting = CommitteeMeeting.schedule(T, committee.getId(), MEETING, "Q4 meeting", null);
        decision = CommitteeResolution.create(T, meeting.getId(), committee.getId(), ResolutionKind.DECISION, "Approve the revised risk appetite statement", null, null, MEETING);

        List<CommitteeMember> all = List.of(anna, ben, cara, dev, eve, observer);
        CommitteeRepository committees = proxy(CommitteeRepository.class, (n, a) -> n.equals("findForTenant") ? Optional.of(committee) : null);
        CommitteeMemberRepository members = proxy(CommitteeMemberRepository.class, (n, a) -> n.equals("findForCommittee") ? all : null);
        CommitteeMeetingRepository meetings = proxy(CommitteeMeetingRepository.class, (n, a) -> n.equals("findForTenant") ? Optional.of(meeting) : null);
        CommitteeResolutionRepository resolutions = proxy(CommitteeResolutionRepository.class, (n, a) -> n.equals("findForTenant") ? Optional.ofNullable(a[1].equals(decision.getId()) ? decision : null) : null);
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
        ProxyAppointmentRepository appts = proxy(ProxyAppointmentRepository.class, (n, a) -> switch (n) {
            case "findForTenant" -> proxyStore.stream().filter(p -> p.getId().equals(a[1])).findFirst();
            case "findForMeeting" -> proxyStore.stream().filter(p -> p.getMeetingId().equals(a[1])).toList();
            case "save" -> { if (!proxyStore.contains(a[0])) proxyStore.add((ProxyAppointment) a[0]); yield a[0]; }
            default -> null;
        });
        ProxyVoteRepository pvotes = proxy(ProxyVoteRepository.class, (n, a) -> switch (n) {
            case "findForResolution" -> proxyVoteStore.stream().filter(v -> v.getResolutionId().equals(a[1])).toList();
            case "findForProxies" -> proxyVoteStore.stream().filter(v -> ((Collection<?>) a[1]).contains(v.getProxyId())).toList();
            case "save" -> { if (!proxyVoteStore.contains(a[0])) proxyVoteStore.add((ProxyVote) a[0]); yield a[0]; }
            default -> null;
        });
        RegulatoryAuditService trail = new RegulatoryAuditService(null) {
            @Override public void record(UUID t, String e, UUID id, String ev, String d, UUID a, String n) { audit.add(ev + "|" + d); }
        };
        CommitteeCharterRulesRepository charters = proxy(CommitteeCharterRulesRepository.class, (n, a) -> n.equals("history") ? List.of() : null);
        proxies = new ProxyService(meetings, members, appts, pvotes, trail);
        voting = new ResolutionVotingService(committees, members, meetings, resolutions, ballots, votes, trail, charters, appts, pvotes);
    }

    private ProxyView give(CommitteeMember grantor, CommitteeMember holder) {
        return proxies.appoint(T, committee.getId(), meeting.getId(), new AppointRequest(grantor.getId(), holder.getId(), CLAUSE, true, null), null, "Sec");
    }

    private void hold(CommitteeMember... present) {
        meeting.hold(Arrays.stream(present).map(m -> m.getId().toString()).toList(), present.length, 2, TODAY);
    }

    private VotingView members(VoteChoice a, VoteChoice b, VoteChoice c) {
        return voting.cast(T, committee.getId(), meeting.getId(), decision.getId(), new CastRequest(List.of(new VoteInput(anna.getId(), a, null), new VoteInput(ben.getId(), b, null), new VoteInput(cara.getId(), c, null))), null, "Sec");
    }

    private VotingView proxyVote(UUID proxyId, VoteChoice c) {
        return voting.castProxy(T, committee.getId(), meeting.getId(), decision.getId(), new ProxyCastRequest(List.of(new ProxyVoteInput(proxyId, c, null))), null, "Sec");
    }

    private VotingView close() { return voting.close(T, committee.getId(), meeting.getId(), decision.getId(), null, "Sec"); }

    private static HandyFlowException fails(org.junit.jupiter.api.function.Executable e) { return assertThrows(HandyFlowException.class, e); }

    @Test @DisplayName("A proxy vote is added to the tally and can change the outcome; the audit trail shows only counts")
    void proxyChangesOutcome() {
        ProxyView p = give(dev, ben);
        assertEquals(ProxyStatus.ACTIVE, p.status());
        assertFalse(p.usable(), "nothing is usable before the meeting is held");
        hold(anna, ben, cara);
        assertTrue(proxies.list(T, committee.getId(), meeting.getId()).get(0).usable());
        members(VoteChoice.FOR, VoteChoice.FOR, VoteChoice.AGAINST); // 2-1 without the proxy
        VotingView v = proxyVote(p.id(), VoteChoice.AGAINST);
        assertEquals(new TallyView(2, 2, 0, 0), v.tally());
        assertEquals(new TallyView(2, 2, 0, 0), voting.forMeeting(T, committee.getId(), meeting.getId()).get(0).tally(), "the meeting's ballot list counts proxy votes too");
        VotingView closed = close();
        assertEquals(BallotOutcome.NOT_CARRIED, closed.outcome(), "the proxy vote made it a tie");
        assertEquals(new TallyView(2, 2, 0, 0), closed.tally());
        assertTrue(audit.stream().anyMatch(a -> a.startsWith("VOTE_CLOSED|") && a.contains("including 1 by proxy")));
        assertTrue(audit.stream().anyMatch(a -> a.startsWith("PROXY_VOTES_RECORDED|") && a.contains("1 proxy vote(s) recorded")));
        assertTrue(audit.stream().filter(a -> a.startsWith("PROXY")).noneMatch(a -> a.contains("AGAINST")), "how a proxy voted is not written to the trail");
        assertTrue(audit.stream().anyMatch(a -> a.startsWith("PROXY_APPOINTED|") && a.contains("Dev gave Ben a proxy")));
    }

    @Test @DisplayName("Voting cannot close until every usable proxy has a vote")
    void closeNeedsProxyVote() {
        ProxyView p = give(dev, ben);
        hold(anna, ben, cara);
        members(VoteChoice.FOR, VoteChoice.FOR, VoteChoice.AGAINST);
        VotingView open = voting.view(T, committee.getId(), meeting.getId(), decision.getId());
        assertTrue(open.problems().stream().anyMatch(x -> x.contains("proxy vote is still needed from Ben for Dev")));
        HandyFlowException e = fails(this::close);
        assertEquals(HttpStatus.CONFLICT, e.getStatus());
        proxyVote(p.id(), VoteChoice.ABSTAIN);
        assertEquals(BallotOutcome.CARRIED, close().outcome());
        assertEquals(new TallyView(2, 1, 1, 0), voting.view(T, committee.getId(), meeting.getId(), decision.getId()).tally());
    }

    @Test @DisplayName("If the grantor turns up, the proxy lapses: it cannot vote and is not needed")
    void grantorAttends() {
        ProxyView p = give(dev, ben);
        meeting.hold(List.of(anna, ben, cara, dev).stream().map(m -> m.getId().toString()).toList(), 4, 2, TODAY);
        assertFalse(proxies.list(T, committee.getId(), meeting.getId()).get(0).usable());
        assertTrue(proxies.list(T, committee.getId(), meeting.getId()).get(0).unusableReason().contains("attended"));
        assertEquals(HttpStatus.BAD_REQUEST, fails(() -> proxyVote(p.id(), VoteChoice.FOR)).getStatus());
        voting.cast(T, committee.getId(), meeting.getId(), decision.getId(), new CastRequest(List.of(new VoteInput(anna.getId(), VoteChoice.FOR, null), new VoteInput(ben.getId(), VoteChoice.FOR, null),
                new VoteInput(cara.getId(), VoteChoice.FOR, null), new VoteInput(dev.getId(), VoteChoice.AGAINST, null))), null, "Sec");
        VotingView closed = close();
        assertEquals(new TallyView(3, 1, 0, 0), closed.tally());
    }

    @Test @DisplayName("If the holder is absent, the proxy cannot be used and is not needed")
    void holderAbsent() {
        ProxyView p = give(dev, eve);
        hold(anna, ben, cara);
        assertEquals(HttpStatus.BAD_REQUEST, fails(() -> proxyVote(p.id(), VoteChoice.FOR)).getStatus());
        members(VoteChoice.FOR, VoteChoice.FOR, VoteChoice.FOR);
        assertEquals(new TallyView(3, 0, 0, 0), close().tally());
    }

    @Test @DisplayName("Proxy votes can be changed while voting is open, never after it closes, and a holder cannot recuse for someone else")
    void changeAndFreeze() {
        ProxyView p = give(dev, ben);
        hold(anna, ben, cara);
        members(VoteChoice.FOR, VoteChoice.AGAINST, VoteChoice.AGAINST);
        proxyVote(p.id(), VoteChoice.AGAINST);
        assertEquals(new TallyView(1, 3, 0, 0), voting.view(T, committee.getId(), meeting.getId(), decision.getId()).tally());
        proxyVote(p.id(), VoteChoice.FOR);
        assertEquals(new TallyView(2, 2, 0, 0), voting.view(T, committee.getId(), meeting.getId(), decision.getId()).tally());
        assertEquals(HttpStatus.BAD_REQUEST, fails(() -> voting.castProxy(T, committee.getId(), meeting.getId(), decision.getId(),
                new ProxyCastRequest(List.of(new ProxyVoteInput(p.id(), VoteChoice.RECUSED, "A long enough reason"))), null, "Sec")).getStatus());
        assertEquals(HttpStatus.BAD_REQUEST, fails(() -> voting.castProxy(T, committee.getId(), meeting.getId(), decision.getId(),
                new ProxyCastRequest(List.of(new ProxyVoteInput(p.id(), VoteChoice.FOR, null), new ProxyVoteInput(p.id(), VoteChoice.AGAINST, null))), null, "Sec")).getStatus());
        close();
        assertEquals(HttpStatus.CONFLICT, fails(() -> proxyVote(p.id(), VoteChoice.AGAINST)).getStatus());
        assertEquals(new TallyView(2, 2, 0, 0), voting.view(T, committee.getId(), meeting.getId(), decision.getId()).tally());
    }

    @Test @DisplayName("A tie created by a proxy can be broken by the chair's casting vote; the counts stay as voted")
    void castingVoteWithProxy() {
        ProxyView p = give(dev, ben);
        hold(anna, ben, cara);
        members(VoteChoice.FOR, VoteChoice.FOR, VoteChoice.AGAINST);
        proxyVote(p.id(), VoteChoice.AGAINST);
        VotingView v = voting.view(T, committee.getId(), meeting.getId(), decision.getId());
        assertTrue(v.tied());
        assertEquals("Anna", v.castingChair());
        VotingView closed = voting.closeWithCastingVote(T, committee.getId(), meeting.getId(), decision.getId(), new CastingVoteRequest(VoteChoice.FOR, "Charter clause 9.1 gives the chair a casting vote", true), null, "Sec");
        assertEquals(BallotOutcome.CARRIED, closed.outcome());
        assertEquals(new TallyView(2, 2, 0, 0), closed.tally());
        assertTrue(audit.stream().anyMatch(a -> a.startsWith("VOTE_CLOSED_CASTING|") && a.contains("including 1 by proxy")));
    }

    @Test @DisplayName("Appointment rules are enforced: before the meeting, the charter tick, observers, duplicates, limits and chains")
    void appointRules() {
        assertEquals(HttpStatus.BAD_REQUEST, fails(() -> proxies.appoint(T, committee.getId(), meeting.getId(), new AppointRequest(dev.getId(), ben.getId(), CLAUSE, false, null), null, "S")).getStatus());
        assertEquals(HttpStatus.BAD_REQUEST, fails(() -> proxies.appoint(T, committee.getId(), meeting.getId(), new AppointRequest(dev.getId(), UUID.randomUUID(), CLAUSE, true, null), null, "S")).getStatus());
        assertEquals(HttpStatus.CONFLICT, fails(() -> give(observer, ben)).getStatus());
        assertEquals(HttpStatus.CONFLICT, fails(() -> give(dev, dev)).getStatus());
        give(dev, ben);
        assertEquals(HttpStatus.CONFLICT, fails(() -> give(dev, cara)).getStatus(), "one live proxy per grantor");
        give(eve, ben);
        CommitteeMember extra = CommitteeMember.add(T, committee.getId(), "Zed", null, MemberRole.MEMBER, MEETING.minusDays(100), TODAY);
        assertEquals(HttpStatus.BAD_REQUEST, fails(() -> proxies.appoint(T, committee.getId(), meeting.getId(), new AppointRequest(extra.getId(), ben.getId(), CLAUSE, true, null), null, "S")).getStatus(), "not a member of this committee");
        assertEquals(HttpStatus.CONFLICT, fails(() -> give(ben, cara)).getStatus(), "a holder cannot also give one");
        assertEquals(HttpStatus.CONFLICT, fails(() -> give(cara, dev)).getStatus(), "a grantor cannot also hold one");
        hold(anna, ben, cara);
        assertEquals(HttpStatus.CONFLICT, fails(() -> give(cara, anna)).getStatus(), "too late once the meeting is held");
    }

    @Test @DisplayName("Revoking: needs a reason, frees the grantor, removes the proxy from the count, and is refused after the proxy voted")
    void revoke() {
        ProxyView p = give(dev, ben);
        assertEquals(HttpStatus.BAD_REQUEST, fails(() -> proxies.revoke(T, committee.getId(), meeting.getId(), p.id(), new RevokeRequest("no"), null, "S")).getStatus());
        assertEquals(ProxyStatus.REVOKED, proxies.revoke(T, committee.getId(), meeting.getId(), p.id(), new RevokeRequest("Dev is able to attend after all"), null, "S").status());
        assertEquals(HttpStatus.CONFLICT, fails(() -> proxies.revoke(T, committee.getId(), meeting.getId(), p.id(), new RevokeRequest("Dev is able to attend after all"), null, "S")).getStatus());
        ProxyView again = give(dev, cara); // a revoked proxy no longer blocks a new one
        hold(anna, ben, cara);
        members(VoteChoice.FOR, VoteChoice.FOR, VoteChoice.FOR);
        assertEquals(HttpStatus.BAD_REQUEST, fails(() -> proxyVote(p.id(), VoteChoice.FOR)).getStatus(), "a revoked proxy cannot vote");
        proxyVote(again.id(), VoteChoice.FOR);
        assertEquals(HttpStatus.CONFLICT, fails(() -> proxies.revoke(T, committee.getId(), meeting.getId(), again.id(), new RevokeRequest("Changed my mind about this"), null, "S")).getStatus());
        assertEquals(new TallyView(4, 0, 0, 0), close().tally());
        assertTrue(audit.stream().anyMatch(a -> a.startsWith("PROXY_REVOKED|")));
    }

    @Test @DisplayName("A meeting or proxy from somewhere else is 'not found'")
    void scoping() {
        ProxyView p = give(dev, ben);
        assertThrows(ResourceNotFoundException.class, () -> proxies.list(T, UUID.randomUUID(), meeting.getId()));
        assertThrows(ResourceNotFoundException.class, () -> proxies.revoke(T, committee.getId(), meeting.getId(), UUID.randomUUID(), new RevokeRequest("Not this meeting at all"), null, "S"));
        ProxyAppointment other = ProxyAppointment.appoint(T, committee.getId(), UUID.randomUUID(), eve.getId(), "Eve", cara.getId(), "Cara", CLAUSE, true, null, "S");
        proxyStore.add(other);
        assertThrows(ResourceNotFoundException.class, () -> proxies.revoke(T, committee.getId(), meeting.getId(), other.getId(), new RevokeRequest("Belongs to another meeting"), null, "S"));
        assertEquals(1, proxies.list(T, committee.getId(), meeting.getId()).size());
        assertEquals(List.of(p.id()), proxies.list(T, committee.getId(), meeting.getId()).stream().map(ProxyView::id).collect(Collectors.toList()));
    }

    @Test @DisplayName("Without any proxy, voting behaves exactly as before")
    void noProxies() {
        hold(anna, ben, cara);
        members(VoteChoice.FOR, VoteChoice.FOR, VoteChoice.AGAINST);
        assertTrue(voting.view(T, committee.getId(), meeting.getId(), decision.getId()).problems().isEmpty());
        assertEquals(new TallyView(2, 1, 0, 0), close().tally());
        assertTrue(audit.stream().noneMatch(a -> a.contains("by proxy")));
        assertTrue(voting.proxyView(T, committee.getId(), meeting.getId(), decision.getId()).voters().isEmpty());
    }
}
