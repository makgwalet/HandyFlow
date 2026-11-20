package za.co.handyflow.platform.legalcompliance.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.legalcompliance.domain.model.*;
import za.co.handyflow.platform.legalcompliance.domain.repository.CommitteeMeetingRepository;
import za.co.handyflow.platform.legalcompliance.domain.repository.CommitteeMemberRepository;
import za.co.handyflow.platform.legalcompliance.domain.repository.ProxyAppointmentRepository;
import za.co.handyflow.platform.legalcompliance.domain.repository.ProxyVoteRepository;
import za.co.handyflow.platform.legalcompliance.dto.ProxyDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;

/**
 * Appointing and revoking proxies. Recording proxy votes is in ResolutionVotingService, next to the member votes, because both feed one tally.
 * A proxy can be made only while the meeting is still SCHEDULED, between two voting members of the same committee (rules in ProxyRules), and only with the person confirming the charter allows it.
 * A proxy that has already voted on something cannot be revoked. The audit trail records who gave a proxy to whom, never how anybody voted.
 */
@Service
@RequiredArgsConstructor
public class ProxyService {

    private static final String ENTITY = "COMMITTEE";

    private final CommitteeMeetingRepository meetings;
    private final CommitteeMemberRepository members;
    private final ProxyAppointmentRepository proxies;
    private final ProxyVoteRepository proxyVotes;
    private final RegulatoryAuditService trail;

    @Transactional(readOnly = true)
    public List<ProxyView> list(TenantId t, UUID cid, UUID mid) {
        CommitteeMeeting m = meeting(t, cid, mid);
        return proxies.findForMeeting(t, mid).stream().map(p -> view(p, m)).toList();
    }

    @Transactional
    public ProxyView appoint(TenantId t, UUID cid, UUID mid, AppointRequest r, UUID uid, String uname) {
        CommitteeMeeting m = meeting(t, cid, mid);
        if (m.getStatus() != MeetingStatus.SCHEDULED) throw new HandyFlowException("Proxies can be given only before the meeting is held", HttpStatus.CONFLICT, "INVALID_STATE");
        List<CommitteeMember> all = members.findForCommittee(t, cid);
        CommitteeMember grantor = find(all, r.grantorMemberId()), holder = find(all, r.holderMemberId());
        List<ProxyAppointment> live = proxies.findForMeeting(t, mid).stream().filter(ProxyAppointment::isActive).toList();
        String problem = ProxyRules.appointProblem(grantor, holder, m.getMeetingDate(), live);
        if (problem != null) throw new HandyFlowException(problem, HttpStatus.CONFLICT, "INVALID_STATE");
        ProxyAppointment p = guarded(() -> ProxyAppointment.appoint(t, cid, mid, grantor.getId(), grantor.getMemberName(), holder.getId(), holder.getMemberName(), r.charterClause(), r.charterPermits(), r.instructions(), uname));
        proxies.save(p);
        trail.record(t.getValue(), ENTITY, cid, "PROXY_APPOINTED", grantor.getMemberName() + " gave " + holder.getMemberName() + " a proxy for the meeting on " + m.getMeetingDate() + " (" + p.getCharterClause() + ")", uid, uname);
        return view(p, m);
    }

    @Transactional
    public ProxyView revoke(TenantId t, UUID cid, UUID mid, UUID pid, RevokeRequest r, UUID uid, String uname) {
        CommitteeMeeting m = meeting(t, cid, mid);
        ProxyAppointment p = proxies.findForTenant(t, pid).filter(x -> x.getMeetingId().equals(mid)).orElseThrow(() -> new ResourceNotFoundException("Proxy", pid.toString()));
        if (!proxyVotes.findForProxies(t, List.of(pid)).isEmpty()) throw new HandyFlowException("This proxy has already been used to vote, so it can no longer be revoked", HttpStatus.CONFLICT, "INVALID_STATE");
        guarded(() -> { p.revoke(r.reason(), uname, Instant.now()); return null; });
        proxies.save(p);
        trail.record(t.getValue(), ENTITY, cid, "PROXY_REVOKED", "The proxy from " + p.getGrantorName() + " to " + p.getHolderName() + " for the meeting on " + m.getMeetingDate() + " was revoked", uid, uname);
        return view(p, m);
    }

    static ProxyView view(ProxyAppointment p, CommitteeMeeting m) {
        // Before the meeting is held nobody has been recorded as present, so "usable" is only meaningful afterwards.
        boolean held = m.getStatus() == MeetingStatus.HELD;
        String why = held ? ProxyRules.unusableReason(p, m.getAttendance()) : null;
        return new ProxyView(p.getId(), p.getGrantorMemberId(), p.getGrantorName(), p.getHolderMemberId(), p.getHolderName(), p.getCharterClause(), p.getInstructions(), p.getStatus(),
                held && p.isActive() && why == null, why, p.getAppointedByName(), p.getRevokedReason(), p.getRevokedByName(), p.getRevokedAt(), p.getCreatedAt());
    }

    private CommitteeMeeting meeting(TenantId t, UUID cid, UUID mid) {
        CommitteeMeeting m = meetings.findForTenant(t, mid).orElseThrow(() -> new ResourceNotFoundException("Meeting", mid.toString()));
        if (!m.getCommitteeId().equals(cid)) throw new ResourceNotFoundException("Meeting", mid.toString());
        return m;
    }

    private static CommitteeMember find(List<CommitteeMember> all, UUID id) {
        return all.stream().filter(x -> x.getId().equals(id)).findFirst().orElseThrow(() -> new HandyFlowException("That person is not a member of this committee", HttpStatus.BAD_REQUEST, "VALIDATION"));
    }

    private static <T> T guarded(Supplier<T> action) {
        try { return action.get(); }
        catch (IllegalArgumentException e) { throw new HandyFlowException(e.getMessage(), HttpStatus.BAD_REQUEST, "VALIDATION"); }
        catch (IllegalStateException e) { throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "INVALID_STATE"); }
    }
}
