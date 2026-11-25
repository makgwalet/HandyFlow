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
import za.co.handyflow.platform.trainingprovider.domain.rules.AttemptRules;
import za.co.handyflow.platform.trainingprovider.domain.rules.PractitionerRules;
import za.co.handyflow.platform.trainingprovider.dto.AttemptDtos.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Assessment attempts and competency decisions. An attempt needs an approved instrument for the cohort's programme version, a learner still in the
 * cohort, and an assessor whose appointment was checked and in force on the day. The mark and the decision are separate records. The decision is
 * written once, with a basis, and only by the assessor who ran the assessment. Attendance is not consulted and no pass mark exists.
 */
@Service
@RequiredArgsConstructor
public class TrainProvAttemptService {

    private final TrainProvAssessmentAttemptRepository attempts;
    private final TrainProvAssessmentDecisionRepository decisions;
    private final TrainProvAssessmentAttemptEventRepository events;
    private final TrainProvAssessmentInstrumentRepository instruments;
    private final TrainProvCohortRepository cohorts;
    private final TrainProvCohortMemberRepository members;
    private final TrainProvLearnerEnrolmentRepository enrolments;
    private final TrainProvLearnerRepository learners;
    private final TrainProvPractitionerRepository practitioners;
    private final TrainProvPractitionerAppointmentRepository appointments;
    private final TrainProvNumberGenerator numbers;

    public List<AttemptView> list(TenantId t, UUID cohortId, UUID instrumentId, UUID enrolmentId) {
        List<TrainProvAssessmentAttempt> rows = attempts.search(t, cohortId, instrumentId, enrolmentId);
        Map<UUID, TrainProvAssessmentDecision> byAttempt = rows.isEmpty() ? Map.of() : decisions.findForAttempts(t, rows.stream().map(TrainProvAssessmentAttempt::getId).toList())
                .stream().collect(Collectors.toMap(TrainProvAssessmentDecision::getAttemptId, Function.identity()));
        return rows.stream().<AttemptView>map(a -> view(t, a, byAttempt.get(a.getId()), false)).toList();
    }

    public AttemptView get(TenantId t, UUID id) {
        TrainProvAssessmentAttempt a = attempt(t, id);
        return view(t, a, decisions.findForAttempt(t, id).orElse(null), true);
    }

    @Transactional
    public AttemptView record(TenantId t, RecordAttempt r, UUID by, LocalDate today) {
        TrainProvAssessmentInstrument i = instruments.findForTenant(t, r.instrumentId())
                .orElseThrow(() -> new ResourceNotFoundException("TrainProvAssessmentInstrument", r.instrumentId().toString()));
        TrainProvCohortMember m = members.findForTenant(t, r.memberId()).orElseThrow(() -> new ResourceNotFoundException("TrainProvCohortMember", r.memberId().toString()));
        TrainProvCohort c = cohorts.findForTenant(t, m.getCohortId()).orElseThrow(() -> new ResourceNotFoundException("TrainProvCohort", m.getCohortId().toString()));
        TrainProvPractitioner p = practitioners.findForTenant(t, r.assessorId()).orElseThrow(() -> new ResourceNotFoundException("TrainProvPractitioner", r.assessorId().toString()));
        Optional<TrainProvPractitionerAppointment> appt = appointments.findLiveForProgramme(t, c.getProgrammeId(), "ASSESSOR").stream()
                .filter(a -> a.getPractitionerId().equals(p.getId())).findFirst();
        PractitionerRules.Standing standing = appt.map(a -> a.standingOn(r.assessedOn())).orElse(PractitionerRules.Standing.NOT_VERIFIED);
        List<TrainProvAssessmentAttempt> earlier = attempts.findForMemberInstrument(t, i.getId(), m.getId());
        String problem = AttemptRules.attemptProblem(i.getStatus(), new AttemptRules.UUIDPair(i.getProgrammeVersionId(), c.getProgrammeVersionId()), m.getStatus(), c.getStatus(),
                c.getStartDate(), r.assessedOn(), today, "ACTIVE".equals(p.getStatus()), standing, prior(t, earlier));
        if (problem == null) {
            problem = AttemptRules.markProblem(i.getMaxMark(), r.mark());
            if (problem != null) {
                throw new HandyFlowException(problem, HttpStatus.BAD_REQUEST, "TP_ATTEMPT_INVALID");
            }
        } else {
            throw new HandyFlowException(appt.isEmpty() && "ACTIVE".equals(p.getStatus()) ? "That person has no assessor appointment for this programme" : problem,
                    HttpStatus.CONFLICT, "TP_ATTEMPT_STATE");
        }
        int seq = earlier.stream().mapToInt(TrainProvAssessmentAttempt::getAttemptSeq).max().orElse(0) + 1;
        TrainProvAssessmentAttempt a = TrainProvAssessmentAttempt.create(t, numbers.nextAttemptNumber(t), i.getId(), m.getId(), c.getId(), m.getEnrolmentId(), seq,
                r.assessedOn(), p.getId(), appt.get().getId(), r.mark(), r.notes(), by);
        attempts.save(a);
        events.save(TrainProvAssessmentAttemptEvent.of(t, a.getId(), "RECORDED", "Attempt " + seq + (r.mark() == null ? "" : ", mark " + r.mark().stripTrailingZeros().toPlainString()), by));
        return view(t, a, null, true);
    }

    @Transactional
    public AttemptView correctMark(TenantId t, UUID id, CorrectMark r, UUID by) {
        TrainProvAssessmentAttempt a = attempt(t, id);
        TrainProvAssessmentInstrument i = instrument(t, a.getInstrumentId());
        boolean decided = decisions.findForAttempt(t, id).isPresent();
        String before = a.getMark() == null ? "none" : a.getMark().stripTrailingZeros().toPlainString();
        guard(() -> { a.correctMark(i.getMaxMark(), r.mark(), decided, r.reason()); return null; });
        attempts.save(a);
        events.save(TrainProvAssessmentAttemptEvent.of(t, id, "MARK_CORRECTED", "Mark " + before + " to "
                + (r.mark() == null ? "none" : r.mark().stripTrailingZeros().toPlainString()) + ": " + r.reason().trim(), by));
        return view(t, a, null, true);
    }

    @Transactional
    public AttemptView decide(TenantId t, UUID id, DecisionRequest r, UUID by, LocalDate today) {
        TrainProvAssessmentAttempt a = attempt(t, id);
        TrainProvPractitioner assessor = practitioners.findForTenant(t, a.getAssessorId())
                .orElseThrow(() -> new ResourceNotFoundException("TrainProvPractitioner", a.getAssessorId().toString()));
        boolean decided = decisions.findForAttempt(t, id).isPresent();
        boolean linkedToSomeoneElse = assessor.getUserId() != null && !assessor.getUserId().equals(by);
        String problem = AttemptRules.decisionProblem(a.getStatus(), decided, r.decision(), r.basis(), linkedToSomeoneElse);
        if (problem != null) {
            throw new HandyFlowException(problem, decided || !AttemptRules.RECORDED.equals(a.getStatus()) || linkedToSomeoneElse ? HttpStatus.CONFLICT : HttpStatus.BAD_REQUEST,
                    decided || !AttemptRules.RECORDED.equals(a.getStatus()) || linkedToSomeoneElse ? "TP_ATTEMPT_STATE" : "TP_ATTEMPT_INVALID");
        }
        TrainProvAssessmentDecision d = decisions.save(TrainProvAssessmentDecision.of(t, id, r.decision(), r.basis(), by, today));
        events.save(TrainProvAssessmentAttemptEvent.of(t, id, "DECIDED", r.decision().equals(AttemptRules.COMPETENT) ? "Competent" : "Not yet competent", by));
        return view(t, a, d, true);
    }

    @Transactional
    public AttemptView voidAttempt(TenantId t, UUID id, String reason, UUID by) {
        TrainProvAssessmentAttempt a = attempt(t, id);
        guard(() -> { a.voidIt(reason, by); return null; });
        attempts.save(a);
        events.save(TrainProvAssessmentAttemptEvent.of(t, id, "VOIDED", a.getVoidReason(), by));
        return view(t, a, decisions.findForAttempt(t, id).orElse(null), true);
    }

    private AttemptRules.Prior prior(TenantId t, List<TrainProvAssessmentAttempt> earlier) {
        AttemptRules.Prior result = AttemptRules.Prior.NONE;
        for (TrainProvAssessmentAttempt e : earlier) {
            if (AttemptRules.VOID.equals(e.getStatus())) {
                continue;
            }
            Optional<TrainProvAssessmentDecision> d = decisions.findForAttempt(t, e.getId());
            if (d.isEmpty()) {
                result = AttemptRules.Prior.OPEN_ATTEMPT;
            } else if (AttemptRules.COMPETENT.equals(d.get().getDecision())) {
                return AttemptRules.Prior.ALREADY_COMPETENT;
            }
        }
        return result;
    }

    private AttemptView view(TenantId t, TrainProvAssessmentAttempt a, TrainProvAssessmentDecision d, boolean withHistory) {
        TrainProvAssessmentInstrument i = instrument(t, a.getInstrumentId());
        String cohortName = cohorts.findForTenant(t, a.getCohortId()).map(TrainProvCohort::getName).orElse(null);
        TrainProvLearnerEnrolment en = enrolments.findForTenant(t, a.getEnrolmentId()).orElse(null);
        UUID learnerId = en == null ? null : en.getLearnerId();
        String learnerName = learnerId == null ? null : learners.findForTenant(t, learnerId).map(l -> l.getFirstNames() + " " + l.getSurname()).orElse(null);
        String assessorName = practitioners.findForTenant(t, a.getAssessorId()).map(p -> p.getFirstNames() + " " + p.getSurname()).orElse(null);
        List<AttemptEvent> history = withHistory
                ? events.findForAttempt(t, a.getId()).stream().<AttemptEvent>map(e -> new AttemptEvent(e.getId(), e.getAction(), e.getNote(), e.getActedBy(), e.getActedAt())).toList()
                : List.of();
        DecisionView dv = d == null ? null : new DecisionView(d.getDecision(), d.getBasis(), d.getDecidedBy(), d.getDecidedOn());
        return new AttemptView(a.getId(), a.getAttemptNo(), i.getId(), i.getInstrumentNo(), i.getTitle(), i.getMaxMark(), i.isRequiresModeration(), a.getMemberId(), a.getCohortId(),
                cohortName, a.getEnrolmentId(), learnerId, learnerName, a.getAttemptSeq(), a.getAssessedOn(), a.getAssessorId(), assessorName, a.getMark(), a.getNotes(), a.getStatus(),
                a.getVoidReason(), dv, history);
    }

    private TrainProvAssessmentAttempt attempt(TenantId t, UUID id) {
        return attempts.findForTenant(t, id).orElseThrow(() -> new ResourceNotFoundException("TrainProvAssessmentAttempt", id.toString()));
    }

    private TrainProvAssessmentInstrument instrument(TenantId t, UUID id) {
        return instruments.findForTenant(t, id).orElseThrow(() -> new ResourceNotFoundException("TrainProvAssessmentInstrument", id.toString()));
    }

    private static <T> T guard(Supplier<T> work) {
        try {
            return work.get();
        } catch (IllegalStateException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "TP_ATTEMPT_STATE");
        } catch (IllegalArgumentException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.BAD_REQUEST, "TP_ATTEMPT_INVALID");
        }
    }
}
