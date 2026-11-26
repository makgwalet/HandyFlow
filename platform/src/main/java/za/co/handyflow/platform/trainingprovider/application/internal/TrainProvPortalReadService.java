package za.co.handyflow.platform.trainingprovider.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.*;
import za.co.handyflow.platform.trainingprovider.domain.repository.*;
import za.co.handyflow.platform.trainingprovider.domain.rules.PortalRules;
import za.co.handyflow.platform.trainingprovider.dto.LearnerPortalDtos.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Read-only views for the learner and employer portals. The caller's tenant and the one learner or employer they may see are always taken from their
 * own accepted grant, never from anything the browser sends, and never from TenantContext (a portal request has none).
 */
@Service
@RequiredArgsConstructor
public class TrainProvPortalReadService {

    private final TrainProvLearnerPortalGrantRepository learnerGrants;
    private final TrainProvPortalAccessGrantRepository clientGrants;
    private final TrainProvLearnerRepository learners;
    private final TrainProvLearnerEnrolmentRepository enrolments;
    private final TrainProvProgrammeRepository programmes;
    private final TrainProvCohortRepository cohorts;
    private final TrainProvCohortMemberRepository members;
    private final TrainProvTimetableSlotRepository slots;
    private final TrainProvAttendanceRepository attendance;
    private final TrainProvCompletionRepository completions;

    @Transactional(readOnly = true)
    public LearnerMe learner(UUID portalUserId, LocalDate today) {
        TrainProvLearnerPortalGrant g = learnerGrants.acceptedForUser(portalUserId).stream().findFirst()
                .orElseThrow(() -> new HandyFlowException("No active learner access was found for this login", HttpStatus.FORBIDDEN, "NO_LEARNER_ACCESS"));
        TenantId t = TenantId.of(g.getTenantId());
        TrainProvLearner l = learners.findForTenant(t, g.getLearnerId())
                .orElseThrow(() -> new ResourceNotFoundException("TrainProvLearner", g.getLearnerId().toString()));
        List<TrainProvCohortMember> mine = members.findForLearner(t, l.getId());
        List<EnrolmentView> views = new ArrayList<>();
        List<SessionView> upcoming = new ArrayList<>();
        for (TrainProvLearnerEnrolment e : enrolments.search(t, null, l.getId(), null)) {
            views.add(view(t, e, mine));
            TrainProvCohortMember m = liveMember(mine, e.getId());
            if (m != null) {
                TrainProvCohort c = cohorts.findForTenant(t, m.getCohortId()).orElse(null);
                for (TrainProvTimetableSlot s : slots.findForCohort(t, m.getCohortId())) {
                    if ("SCHEDULED".equals(s.getStatus()) && !s.getSlotDate().isBefore(today)) {
                        upcoming.add(new SessionView(c == null ? null : c.getName(), s.getTitle(), s.getSlotDate(), s.getStartTime(), s.getEndTime()));
                    }
                }
            }
        }
        upcoming.sort(Comparator.comparing(SessionView::date).thenComparing(SessionView::startTime));
        return new LearnerMe(l.getLearnerNo(), l.getFirstNames() + " " + l.getSurname(), PortalRules.NOTE, views, upcoming);
    }

    @Transactional(readOnly = true)
    public EmployerLearners employer(UUID portalUserId) {
        TrainProvPortalAccessGrant g = clientGrants.findAcceptedByPortalUser(portalUserId).stream().findFirst()
                .orElseThrow(() -> new HandyFlowException("No active client access found for this portal account", HttpStatus.FORBIDDEN, "NO_CLIENT_ACCESS"));
        TenantId t = TenantId.of(g.getTenantId());
        List<TrainProvLearnerEnrolment> all = enrolments.forClient(t, g.getClientId());
        Map<UUID, TrainProvLearner> byId = all.stream().map(TrainProvLearnerEnrolment::getLearnerId).distinct()
                .map(id -> learners.findForTenant(t, id).orElse(null)).filter(x -> x != null)
                .collect(Collectors.<TrainProvLearner, UUID, TrainProvLearner>toMap(TrainProvLearner::getId, Function.identity()));
        List<EmployerRow> rows = new ArrayList<>();
        int hidden = 0;
        for (TrainProvLearnerEnrolment e : all) {
            TrainProvLearner l = byId.get(e.getLearnerId());
            if (l == null || !PortalRules.employerMayView(l.isConsentGiven(), l.getMergedIntoId() != null)) {
                hidden++;
                continue;
            }
            rows.add(new EmployerRow(l.getLearnerNo(), l.getFirstNames() + " " + l.getSurname(), view(t, e, members.findForLearner(t, l.getId()))));
        }
        return new EmployerLearners(PortalRules.NOTE, rows, hidden);
    }

    private EnrolmentView view(TenantId t, TrainProvLearnerEnrolment e, List<TrainProvCohortMember> mine) {
        TrainProvProgramme p = programmes.findForTenant(t, e.getProgrammeId()).orElse(null);
        TrainProvCohortMember m = liveMember(mine, e.getId());
        TrainProvCohort c = m == null ? null : cohorts.findForTenant(t, m.getCohortId()).orElse(null);
        List<String> statuses = new ArrayList<>();
        if (m != null) {
            for (TrainProvAttendance a : attendance.findForCohort(t, m.getCohortId())) {
                if (a.getMemberId().equals(m.getId())) statuses.add(a.getStatus());
            }
        }
        PortalRules.Counts n = PortalRules.count(statuses);
        TrainProvCompletion done = completions.findLiveForEnrolment(t, e.getId()).orElse(null);
        return new EnrolmentView(e.getEnrolmentNo(), p == null ? null : p.getCode(), p == null ? null : p.getTitle(), e.getStatus(), e.getStartDate(), e.getExpectedEndDate(),
                c == null ? null : c.getName(), new AttendanceCounts(n.recorded(), n.present(), n.late(), n.absent(), n.excused()),
                PortalRules.outcomeText(done == null ? null : done.getOutcome()), done == null ? null : done.getOutcomeOn());
    }

    private static TrainProvCohortMember liveMember(List<TrainProvCohortMember> mine, UUID enrolmentId) {
        return mine.stream().filter(m -> m.getEnrolmentId().equals(enrolmentId) && "ACTIVE".equals(m.getStatus())).findFirst().orElse(null);
    }
}
