package za.co.handyflow.platform.legalcompliance.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import za.co.handyflow.platform.legalcompliance.domain.model.*;
import za.co.handyflow.platform.legalcompliance.domain.repository.*;
import za.co.handyflow.platform.legalcompliance.dto.GovernanceDtos.ResolutionRequest;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.lang.reflect.Proxy;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** A decision that has been voted on is what was voted on: it can no longer be edited or removed. */
class CommitteeVoteGuardTest {

    private static final TenantId T = TenantId.of(UUID.randomUUID());
    private static final LocalDate TODAY = LocalDate.now(), MEETING = TODAY.minusDays(3);

    private final List<CommitteeResolution> deleted = new ArrayList<>();
    private final Set<UUID> withBallot = new HashSet<>();
    private Committee committee;
    private CommitteeMeeting meeting;
    private CommitteeResolution decision, action;
    private CommitteeService service;

    @SuppressWarnings("unchecked")
    private static <R> R proxy(Class<R> type, java.util.function.BiFunction<String, Object[], Object> handler) {
        return (R) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (p, m, args) -> {
            Object r = handler.apply(m.getName(), args == null ? new Object[0] : args);
            if (r != null) return r;
            if (m.getReturnType() == List.class) return List.of();
            if (m.getReturnType() == Optional.class) return Optional.empty();
            return null;
        });
    }

    @BeforeEach
    void setUp() {
        committee = Committee.create(T, "COM-00001", "Risk committee", CommitteeType.RISK, null, 3, 2, 12, null, "me");
        CommitteeMember a = CommitteeMember.add(T, committee.getId(), "Anna", null, MemberRole.CHAIR, MEETING.minusDays(100), TODAY);
        CommitteeMember b = CommitteeMember.add(T, committee.getId(), "Ben", null, MemberRole.MEMBER, MEETING.minusDays(100), TODAY);
        meeting = CommitteeMeeting.schedule(T, committee.getId(), MEETING, "Q4 meeting", null);
        meeting.hold(List.of(a.getId().toString(), b.getId().toString()), 2, 2, TODAY);
        decision = CommitteeResolution.create(T, meeting.getId(), committee.getId(), ResolutionKind.DECISION, "Approve the revised risk appetite statement", null, null, MEETING);
        action = CommitteeResolution.create(T, meeting.getId(), committee.getId(), ResolutionKind.ACTION, "Circulate the revised statement to all staff", "Anna", TODAY.plusDays(14), MEETING);

        service = new CommitteeService(
                proxy(CommitteeRepository.class, (n, x) -> n.equals("findForTenant") ? Optional.of(committee) : null),
                proxy(CommitteeMemberRepository.class, (n, x) -> n.equals("findForCommittee") ? List.of(a, b) : null),
                proxy(CommitteeMeetingRepository.class, (n, x) -> n.equals("findForTenant") ? Optional.of(meeting) : null),
                proxy(CommitteeResolutionRepository.class, (n, x) -> switch (n) {
                    case "findForTenant" -> Optional.ofNullable(x[1].equals(decision.getId()) ? decision : x[1].equals(action.getId()) ? action : null);
                    case "delete" -> { deleted.add((CommitteeResolution) x[0]); yield null; }
                    case "save" -> x[0];
                    default -> null;
                }),
                proxy(ResolutionBallotRepository.class, (n, x) -> n.equals("findForResolution") && withBallot.contains(x[1]) ? Optional.of(ResolutionBallot.open(T, (UUID) x[1], meeting.getId(), committee.getId())) : null),
                new RegulatoryAuditService(null) { @Override public void record(UUID t, String e, UUID id, String ev, String d, UUID u, String n) {} });
    }

    private static ResolutionRequest reworded() { return new ResolutionRequest(ResolutionKind.DECISION, "Approve a different risk appetite statement entirely", null, null); }

    @Test @DisplayName("A decision with a ballot cannot be edited")
    void editRefused() {
        withBallot.add(decision.getId());
        HandyFlowException e = assertThrows(HandyFlowException.class, () -> service.updateResolution(T, committee.getId(), meeting.getId(), decision.getId(), reworded(), null, "Sec"));
        assertEquals(HttpStatus.CONFLICT, e.getStatus());
        assertEquals("Approve the revised risk appetite statement", decision.getDescription());
    }

    @Test @DisplayName("A decision with a ballot cannot be removed")
    void removeRefused() {
        withBallot.add(decision.getId());
        HandyFlowException e = assertThrows(HandyFlowException.class, () -> service.removeResolution(T, committee.getId(), meeting.getId(), decision.getId(), null, "Sec"));
        assertEquals(HttpStatus.CONFLICT, e.getStatus());
        assertEquals(0, deleted.size());
    }

    @Test @DisplayName("A decision nobody has voted on can still be edited and removed")
    void untouchedStillWorks() {
        service.updateResolution(T, committee.getId(), meeting.getId(), decision.getId(), reworded(), null, "Sec");
        assertEquals("Approve a different risk appetite statement entirely", decision.getDescription());
        service.removeResolution(T, committee.getId(), meeting.getId(), decision.getId(), null, "Sec");
        assertEquals(1, deleted.size());
    }

    @Test @DisplayName("Actions are unaffected: the guard concerns decisions only")
    void actionsUnaffected() {
        withBallot.add(action.getId());    // cannot happen through the service, but the guard must not block actions anyway
        service.removeResolution(T, committee.getId(), meeting.getId(), action.getId(), null, "Sec");
        assertEquals(1, deleted.size());
    }
}
