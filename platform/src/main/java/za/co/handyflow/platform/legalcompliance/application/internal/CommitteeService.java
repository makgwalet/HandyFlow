package za.co.handyflow.platform.legalcompliance.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.legalcompliance.dto.AssuranceDtos.ReasonRequest;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Governance committees. A meeting records who attended, so quorum is a fact rather than a claim; decisions and actions can be recorded only for a meeting
 * held with a quorum, and not after the minutes are approved. The person who wrote the minutes cannot approve them. A committee is "overdue to meet" when its last
 * held meeting plus its frequency has passed and nothing is scheduled; a committee that has never met is measured from the day it was set up.
 */
@Service
@RequiredArgsConstructor
public class CommitteeService {

    static final String ENTITY = "COMMITTEE";

    private final CommitteeRepository committees;
    private final CommitteeMemberRepository members;
    private final CommitteeMeetingRepository meetings;
    private final CommitteeResolutionRepository resolutions;
    private final RegulatoryAuditService trail;

    // ── Committees ──

    @Transactional
    public CommitteeDetail create(TenantId t, CommitteeRequest r, UUID uid, String uname) {
        Committee c = guarded(() -> Committee.create(t, String.format("COM-%05d", committees.nextRef()), r.name(), r.committeeType(), r.charter(), orDefault(r.meetingFrequencyMonths(), 3),
                orDefault(r.quorumMin(), 2), orDefault(r.charterReviewMonths(), 12), uid, uname));
        committees.save(c);
        trail.record(t.getValue(), ENTITY, c.getId(), "CREATED", c.getReference() + " " + c.getName() + " (" + c.getCommitteeType() + ")", uid, uname);
        return detail(t, c);
    }

    @Transactional
    public CommitteeDetail update(TenantId t, UUID id, CommitteeRequest r, UUID uid, String uname) {
        Committee c = load(t, id);
        guarded(() -> { c.update(r.name(), r.committeeType(), r.charter(), orDefault(r.meetingFrequencyMonths(), c.getMeetingFrequencyMonths()), orDefault(r.quorumMin(), c.getQuorumMin()),
                orDefault(r.charterReviewMonths(), c.getCharterReviewMonths())); return null; });
        committees.save(c);
        trail.record(t.getValue(), ENTITY, id, "UPDATED", "Details changed", uid, uname);
        return detail(t, c);
    }

    @Transactional
    public CommitteeDetail reviewCharter(TenantId t, UUID id, CharterReviewRequest r, UUID uid, String uname) {
        Committee c = load(t, id);
        guarded(() -> { c.reviewCharter(r.reviewedOn(), today()); return null; });
        committees.save(c);
        trail.record(t.getValue(), ENTITY, id, "CHARTER_REVIEWED", "Charter reviewed " + r.reviewedOn(), uid, uname);
        return detail(t, c);
    }

    @Transactional
    public CommitteeDetail dissolve(TenantId t, UUID id, ReasonRequest r, UUID uid, String uname) {
        Committee c = load(t, id);
        if (meetings.findForCommittee(t, id).stream().anyMatch(m -> m.getStatus() == MeetingStatus.SCHEDULED))
            throw new HandyFlowException("Cancel or hold the scheduled meetings first", HttpStatus.CONFLICT, "INVALID_STATE");
        guarded(() -> { c.dissolve(r.reason()); return null; });
        committees.save(c);
        trail.record(t.getValue(), ENTITY, id, "DISSOLVED", r.reason().trim(), uid, uname);
        return detail(t, c);
    }

    // ── Members ──

    @Transactional
    public CommitteeDetail addMember(TenantId t, UUID id, MemberRequest r, UUID uid, String uname) {
        Committee c = activeCommittee(t, id);
        if (r.memberRole() == MemberRole.CHAIR && members.findForCommittee(t, id).stream().anyMatch(m -> m.isActive() && m.getMemberRole() == MemberRole.CHAIR))
            throw new HandyFlowException("This committee already has a chair — end that membership first", HttpStatus.CONFLICT, "INVALID_STATE");
        CommitteeMember m = guarded(() -> CommitteeMember.add(t, id, r.memberName(), r.userId(), r.memberRole(), r.appointedOn(), today()));
        members.save(m);
        trail.record(t.getValue(), ENTITY, id, "MEMBER_ADDED", m.getMemberName() + " as " + m.getMemberRole(), uid, uname);
        return detail(t, c);
    }

    @Transactional
    public CommitteeDetail endMember(TenantId t, UUID id, UUID memberId, EndMemberRequest r, UUID uid, String uname) {
        Committee c = activeCommittee(t, id);
        CommitteeMember m = loadMember(t, id, memberId);
        guarded(() -> { m.end(r.endedOn(), today()); return null; });
        members.save(m);
        trail.record(t.getValue(), ENTITY, id, "MEMBER_ENDED", m.getMemberName() + " left on " + r.endedOn(), uid, uname);
        return detail(t, c);
    }

    // ── Meetings ──

    @Transactional
    public MeetingDetail scheduleMeeting(TenantId t, UUID id, MeetingRequest r, UUID uid, String uname) {
        activeCommittee(t, id);
        CommitteeMeeting m = guarded(() -> CommitteeMeeting.schedule(t, id, r.meetingDate(), r.title(), r.agenda()));
        meetings.save(m);
        trail.record(t.getValue(), ENTITY, id, "MEETING_SCHEDULED", m.getTitle() + " on " + m.getMeetingDate(), uid, uname);
        return meetingDetail(t, m);
    }

    @Transactional
    public MeetingDetail updateMeeting(TenantId t, UUID id, UUID mid, MeetingRequest r, UUID uid, String uname) {
        CommitteeMeeting m = loadMeeting(t, id, mid);
        guarded(() -> { m.update(r.meetingDate(), r.title(), r.agenda()); return null; });
        meetings.save(m);
        trail.record(t.getValue(), ENTITY, id, "MEETING_UPDATED", m.getTitle() + " on " + m.getMeetingDate(), uid, uname);
        return meetingDetail(t, m);
    }

    @Transactional
    public MeetingDetail cancelMeeting(TenantId t, UUID id, UUID mid, ReasonRequest r, UUID uid, String uname) {
        CommitteeMeeting m = loadMeeting(t, id, mid);
        guarded(() -> { m.cancel(r.reason()); return null; });
        meetings.save(m);
        trail.record(t.getValue(), ENTITY, id, "MEETING_CANCELLED", m.getTitle() + ": " + r.reason().trim(), uid, uname);
        return meetingDetail(t, m);
    }

    @Transactional
    public MeetingDetail holdMeeting(TenantId t, UUID id, UUID mid, HoldRequest r, UUID uid, String uname) {
        Committee c = load(t, id);
        CommitteeMeeting m = loadMeeting(t, id, mid);
        Map<UUID, CommitteeMember> onDate = members.findForCommittee(t, id).stream().filter(x -> memberOn(x, m.getMeetingDate())).collect(Collectors.toMap(CommitteeMember::getId, x -> x));
        List<UUID> ids = r.attendeeIds().stream().distinct().toList();
        for (UUID a : ids) if (!onDate.containsKey(a)) throw new HandyFlowException("An attendee is not a member of this committee on the meeting date", HttpStatus.BAD_REQUEST, "VALIDATION");
        int qualifying = (int) ids.stream().filter(a -> onDate.get(a).getMemberRole() != MemberRole.OBSERVER).count();
        guarded(() -> { m.hold(ids.stream().map(UUID::toString).toList(), qualifying, c.getQuorumMin(), today()); return null; });
        meetings.save(m);
        trail.record(t.getValue(), ENTITY, id, "MEETING_HELD", m.getTitle() + ": " + qualifying + " of quorum " + c.getQuorumMin() + (Boolean.TRUE.equals(m.getQuorumMet()) ? " — quorum met" : " — NO QUORUM"), uid, uname);
        return meetingDetail(t, m);
    }

    @Transactional
    public MeetingDetail recordMinutes(TenantId t, UUID id, UUID mid, MinutesRequest r, UUID uid, String uname) {
        CommitteeMeeting m = loadMeeting(t, id, mid);
        guarded(() -> { m.recordMinutes(r.minutes(), uid, uname); return null; });
        meetings.save(m);
        trail.record(t.getValue(), ENTITY, id, "MINUTES_RECORDED", m.getTitle(), uid, uname);
        return meetingDetail(t, m);
    }

    @Transactional
    public MeetingDetail approveMinutes(TenantId t, UUID id, UUID mid, UUID uid, String uname) {
        CommitteeMeeting m = loadMeeting(t, id, mid);
        guarded(() -> { m.approveMinutes(uid, uname, today()); return null; });
        meetings.save(m);
        trail.record(t.getValue(), ENTITY, id, "MINUTES_APPROVED", m.getTitle(), uid, uname);
        return meetingDetail(t, m);
    }

    // ── Decisions and actions ──

    @Transactional
    public MeetingDetail addResolution(TenantId t, UUID id, UUID mid, ResolutionRequest r, UUID uid, String uname) {
        CommitteeMeeting m = loadMeeting(t, id, mid);
        requireOpenForResolutions(m);
        CommitteeResolution x = guarded(() -> CommitteeResolution.create(t, mid, id, r.kind(), r.description(), r.ownerName(), r.dueDate(), m.getMeetingDate()));
        resolutions.save(x);
        trail.record(t.getValue(), ENTITY, id, "RESOLUTION_ADDED", x.getKind() + ": " + abbreviate(x.getDescription()), uid, uname);
        return meetingDetail(t, m);
    }

    @Transactional
    public MeetingDetail updateResolution(TenantId t, UUID id, UUID mid, UUID rid, ResolutionRequest r, UUID uid, String uname) {
        CommitteeMeeting m = loadMeeting(t, id, mid);
        requireOpenForResolutions(m);
        CommitteeResolution x = loadResolution(t, mid, rid);
        guarded(() -> { x.update(r.kind(), r.description(), r.ownerName(), r.dueDate(), m.getMeetingDate()); return null; });
        resolutions.save(x);
        trail.record(t.getValue(), ENTITY, id, "RESOLUTION_UPDATED", abbreviate(x.getDescription()), uid, uname);
        return meetingDetail(t, m);
    }

    @Transactional
    public MeetingDetail removeResolution(TenantId t, UUID id, UUID mid, UUID rid, UUID uid, String uname) {
        CommitteeMeeting m = loadMeeting(t, id, mid);
        requireOpenForResolutions(m);
        CommitteeResolution x = loadResolution(t, mid, rid);
        if (x.getStatus() == ResolutionStatus.DONE) throw new HandyFlowException("A completed action cannot be removed", HttpStatus.CONFLICT, "INVALID_STATE");
        resolutions.delete(x);
        trail.record(t.getValue(), ENTITY, id, "RESOLUTION_REMOVED", abbreviate(x.getDescription()), uid, uname);
        return meetingDetail(t, m);
    }

    /** Completing an action stays possible after the minutes are approved: the minutes record what was decided, not what has since been done. */
    @Transactional
    public MeetingDetail completeAction(TenantId t, UUID id, UUID mid, UUID rid, CompleteRequest r, UUID uid, String uname) {
        CommitteeMeeting m = loadMeeting(t, id, mid);
        CommitteeResolution x = loadResolution(t, mid, rid);
        guarded(() -> { x.complete(r.completedOn(), r.note(), today()); return null; });
        resolutions.save(x);
        trail.record(t.getValue(), ENTITY, id, "ACTION_COMPLETED", abbreviate(x.getDescription()) + " on " + r.completedOn(), uid, uname);
        return meetingDetail(t, m);
    }

    // ── Reads ──

    @Transactional(readOnly = true)
    public List<CommitteeResponse> list(TenantId t, CommitteeStatus status) {
        List<Committee> cs = committees.search(t, status);
        if (cs.isEmpty()) return List.of();
        List<UUID> ids = cs.stream().map(Committee::getId).toList();
        Map<UUID, List<CommitteeMember>> ms = members.findForCommittees(t, ids).stream().collect(Collectors.groupingBy(CommitteeMember::getCommitteeId));
        Map<UUID, List<CommitteeMeeting>> mt = meetings.findForCommittees(t, ids).stream().collect(Collectors.groupingBy(CommitteeMeeting::getCommitteeId));
        Map<UUID, List<CommitteeResolution>> acts = resolutions.findOpenActionsForCommittees(t, ids).stream().collect(Collectors.groupingBy(CommitteeResolution::getCommitteeId));
        return cs.stream().map(c -> view(c, ms.getOrDefault(c.getId(), List.of()), mt.getOrDefault(c.getId(), List.of()), acts.getOrDefault(c.getId(), List.of()))).toList();
    }

    @Transactional(readOnly = true)
    public CommitteeDetail get(TenantId t, UUID id) { return detail(t, load(t, id)); }

    @Transactional(readOnly = true)
    public MeetingDetail getMeeting(TenantId t, UUID id, UUID mid) { return meetingDetail(t, loadMeeting(t, id, mid)); }

    /** Actions across every committee, soonest due first. */
    @Transactional(readOnly = true)
    public List<ResolutionResponse> actions(TenantId t, boolean openOnly) {
        List<CommitteeResolution> rs = resolutions.findActions(t, openOnly);
        if (rs.isEmpty()) return List.of();
        Map<UUID, Committee> cs = committees.search(t, null).stream().collect(Collectors.toMap(Committee::getId, c -> c));
        Map<UUID, CommitteeMeeting> ms = new HashMap<>();
        meetings.findForCommittees(t, cs.keySet()).forEach(m -> ms.put(m.getId(), m));
        LocalDate today = today();
        return rs.stream().map(x -> resView(x, today, cs.get(x.getCommitteeId()), ms.get(x.getMeetingId()))).toList();
    }

    @Transactional(readOnly = true)
    public GovernanceSummary summary(TenantId t) {
        List<CommitteeResponse> all = list(t, CommitteeStatus.ACTIVE);
        long open = all.stream().mapToLong(CommitteeResponse::openActions).sum();
        return new GovernanceSummary(all.size(), all.stream().filter(CommitteeResponse::meetingOverdue).count(), all.stream().filter(CommitteeResponse::charterReviewOverdue).count(),
                all.stream().mapToLong(CommitteeResponse::minutesPending).sum(), open, all.stream().mapToLong(CommitteeResponse::overdueActions).sum(),
                all.stream().filter(c -> c.qualifyingMembers() < c.quorumMin()).count());
    }

    // ── internals ──

    private static LocalDate today() { return LocalDate.now(ZoneId.of("Africa/Johannesburg")); }

    private static int orDefault(int v, int d) { return v == 0 ? d : v; }

    private static boolean memberOn(CommitteeMember m, LocalDate d) { return !m.getAppointedOn().isAfter(d) && (m.getEndedOn() == null || !m.getEndedOn().isBefore(d)); }

    private static String abbreviate(String s) { return s.length() <= 120 ? s : s.substring(0, 117) + "…"; }

    private void requireOpenForResolutions(CommitteeMeeting m) {
        if (m.canRecordResolutions()) return;
        String why = m.getStatus() != MeetingStatus.HELD ? "Decisions and actions can be recorded only for a meeting that was held"
                : !Boolean.TRUE.equals(m.getQuorumMet()) ? "This meeting did not have a quorum, so decisions cannot be recorded" : "The minutes are approved, so decisions and actions can no longer be changed";
        throw new HandyFlowException(why, HttpStatus.CONFLICT, "INVALID_STATE");
    }

    private Committee load(TenantId t, UUID id) { return committees.findForTenant(t, id).orElseThrow(() -> new ResourceNotFoundException("Committee", id.toString())); }

    private Committee activeCommittee(TenantId t, UUID id) {
        Committee c = load(t, id);
        if (c.getStatus() == CommitteeStatus.DISSOLVED) throw new HandyFlowException("A dissolved committee cannot be changed", HttpStatus.CONFLICT, "INVALID_STATE");
        return c;
    }

    private CommitteeMember loadMember(TenantId t, UUID id, UUID memberId) {
        CommitteeMember m = members.findForTenant(t, memberId).orElseThrow(() -> new ResourceNotFoundException("Member", memberId.toString()));
        if (!m.getCommitteeId().equals(id)) throw new ResourceNotFoundException("Member", memberId.toString());
        return m;
    }

    private CommitteeMeeting loadMeeting(TenantId t, UUID id, UUID mid) {
        CommitteeMeeting m = meetings.findForTenant(t, mid).orElseThrow(() -> new ResourceNotFoundException("Meeting", mid.toString()));
        if (!m.getCommitteeId().equals(id)) throw new ResourceNotFoundException("Meeting", mid.toString());
        return m;
    }

    private CommitteeResolution loadResolution(TenantId t, UUID mid, UUID rid) {
        CommitteeResolution x = resolutions.findForTenant(t, rid).orElseThrow(() -> new ResourceNotFoundException("Resolution", rid.toString()));
        if (!x.getMeetingId().equals(mid)) throw new ResourceNotFoundException("Resolution", rid.toString());
        return x;
    }

    private CommitteeDetail detail(TenantId t, Committee c) {
        List<CommitteeMember> ms = members.findForCommittee(t, c.getId());
        List<CommitteeMeeting> mt = meetings.findForCommittee(t, c.getId());
        List<CommitteeResolution> acts = resolutions.findOpenActionsForCommittees(t, List.of(c.getId()));
        Map<UUID, String> names = ms.stream().collect(Collectors.toMap(CommitteeMember::getId, CommitteeMember::getMemberName));
        return new CommitteeDetail(view(c, ms, mt, acts), ms.stream().map(m -> new MemberResponse(m.getId(), m.getMemberName(), m.getUserId(), m.getMemberRole(), m.getAppointedOn(), m.getEndedOn(), m.isActive())).toList(),
                mt.stream().map(m -> meetingView(m, names)).toList());
    }

    private MeetingDetail meetingDetail(TenantId t, CommitteeMeeting m) {
        Map<UUID, String> names = members.findForCommittee(t, m.getCommitteeId()).stream().collect(Collectors.toMap(CommitteeMember::getId, CommitteeMember::getMemberName));
        LocalDate today = today();
        return new MeetingDetail(meetingView(m, names), resolutions.findForMeeting(t, m.getId()).stream().map(x -> resView(x, today, null, m)).toList());
    }

    private static MeetingResponse meetingView(CommitteeMeeting m, Map<UUID, String> names) {
        List<String> attendees = m.getAttendance() == null ? List.of() : m.getAttendance().stream().map(s -> names.getOrDefault(UUID.fromString(s), "Former member")).toList();
        return new MeetingResponse(m.getId(), m.getCommitteeId(), m.getMeetingDate(), m.getTitle(), m.getAgenda(), m.getStatus(), attendees, m.getPresentCount(), m.getQuorumMet(), m.getCancelReason(),
                m.getMinutes(), m.getMinutesStatus(), m.getMinutesByName(), m.getMinutesApprovedByName(), m.getMinutesApprovedOn(), m.canRecordResolutions());
    }

    private static ResolutionResponse resView(CommitteeResolution x, LocalDate today, Committee c, CommitteeMeeting m) {
        return new ResolutionResponse(x.getId(), x.getMeetingId(), x.getCommitteeId(), x.getKind(), x.getDescription(), x.getOwnerName(), x.getDueDate(), x.getStatus(), x.getCompletedOn(), x.getNote(),
                x.getStatus() == ResolutionStatus.OPEN && EthicsGovernanceRules.actionOverdue(x.getDueDate(), today), c == null ? null : c.getName(), m == null ? null : m.getMeetingDate());
    }

    private CommitteeResponse view(Committee c, List<CommitteeMember> ms, List<CommitteeMeeting> mt, List<CommitteeResolution> openActions) {
        LocalDate today = today();
        List<CommitteeMember> active = ms.stream().filter(CommitteeMember::isActive).toList();
        LocalDate lastHeld = mt.stream().filter(m -> m.getStatus() == MeetingStatus.HELD).map(CommitteeMeeting::getMeetingDate).max(Comparator.naturalOrder()).orElse(null);
        LocalDate next = mt.stream().filter(m -> m.getStatus() == MeetingStatus.SCHEDULED && !m.getMeetingDate().isBefore(today)).map(CommitteeMeeting::getMeetingDate).min(Comparator.naturalOrder()).orElse(null);
        LocalDate start = lastHeld != null ? lastHeld : c.getCreatedAt().atZone(ZoneId.of("Africa/Johannesburg")).toLocalDate();
        boolean live = c.getStatus() == CommitteeStatus.ACTIVE;
        LocalDate charterFrom = c.getLastCharterReview() != null ? c.getLastCharterReview() : c.getCreatedAt().atZone(ZoneId.of("Africa/Johannesburg")).toLocalDate();
        return new CommitteeResponse(c.getId(), c.getReference(), c.getName(), c.getCommitteeType(), c.getCharter(), c.getMeetingFrequencyMonths(), c.getQuorumMin(), c.getCharterReviewMonths(),
                c.getLastCharterReview(), c.getStatus(), c.getDissolvedReason(), active.size(), (int) active.stream().filter(CommitteeMember::countsForQuorum).count(),
                active.stream().anyMatch(m -> m.getMemberRole() == MemberRole.CHAIR), lastHeld, next, live && EthicsGovernanceRules.meetingOverdue(start, c.getMeetingFrequencyMonths(), next != null, today),
                live && EthicsGovernanceRules.charterReviewOverdue(charterFrom, c.getCharterReviewMonths(), today), live ? openActions.size() : 0,
                live ? (int) openActions.stream().filter(x -> EthicsGovernanceRules.actionOverdue(x.getDueDate(), today)).count() : 0,
                live ? (int) mt.stream().filter(m -> m.getMinutesStatus() == MinutesStatus.DRAFT).count() : 0, c.getCreatedByName());
    }

    private static <T> T guarded(java.util.function.Supplier<T> action) {
        try { return action.get(); }
        catch (IllegalArgumentException e) { throw new HandyFlowException(e.getMessage(), HttpStatus.BAD_REQUEST, "VALIDATION"); }
        catch (IllegalStateException e) { throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "INVALID_STATE"); }
    }
}
