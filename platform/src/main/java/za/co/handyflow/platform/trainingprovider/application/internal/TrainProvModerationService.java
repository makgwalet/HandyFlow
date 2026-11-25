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
import za.co.handyflow.platform.trainingprovider.domain.rules.ModerationRules;
import za.co.handyflow.platform.trainingprovider.domain.rules.PractitionerRules;
import za.co.handyflow.platform.trainingprovider.dto.ModerationDtos.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Moderation of decided attempts. The moderator reviews a decision that has been made and confirms or queries it, with a written basis. The
 * moderator must have a moderator appointment that was checked and in force on the day, cannot be the assessor, and cannot be a user who took
 * part in recording the attempt or its decision. The decision itself is never edited: a query is answered with a written resolution.
 */
@Service
@RequiredArgsConstructor
public class TrainProvModerationService {

    private final TrainProvAssessmentModerationRepository moderations;
    private final TrainProvAssessmentAttemptRepository attempts;
    private final TrainProvAssessmentDecisionRepository decisions;
    private final TrainProvAssessmentInstrumentRepository instruments;
    private final TrainProvCohortRepository cohorts;
    private final TrainProvLearnerEnrolmentRepository enrolments;
    private final TrainProvLearnerRepository learners;
    private final TrainProvPractitionerRepository practitioners;
    private final TrainProvPractitionerAppointmentRepository appointments;
    private final TrainProvNumberGenerator numbers;

    public List<PendingRow> pending(TenantId t) {
        return moderations.pending(t).stream().<PendingRow>map(a -> {
            TrainProvAssessmentDecision d = decisions.findForAttempt(t, a.getId()).orElse(null);
            return new PendingRow(a.getId(), a.getAttemptNo(), instrumentTitle(t, a.getInstrumentId()), cohortName(t, a.getCohortId()), learnerName(t, a.getEnrolmentId()),
                    a.getAssessorId(), practitionerName(t, a.getAssessorId()), d == null ? null : d.getDecision(), d == null ? null : d.getDecidedOn());
        }).toList();
    }

    public List<ModerationView> list(TenantId t, String outcome, boolean openQueriesOnly) {
        return moderations.search(t, outcome == null || outcome.isBlank() ? null : outcome.trim(), openQueriesOnly).stream().<ModerationView>map(m -> view(t, m)).toList();
    }

    public Optional<ModerationView> forAttempt(TenantId t, UUID attemptId) {
        return moderations.findForAttempt(t, attemptId).map(m -> view(t, m));
    }

    @Transactional
    public ModerationView record(TenantId t, UUID attemptId, RecordModeration r, UUID by, LocalDate today) {
        TrainProvAssessmentAttempt a = attempt(t, attemptId);
        TrainProvAssessmentInstrument i = instruments.findForTenant(t, a.getInstrumentId())
                .orElseThrow(() -> new ResourceNotFoundException("TrainProvAssessmentInstrument", a.getInstrumentId().toString()));
        TrainProvCohort c = cohorts.findForTenant(t, a.getCohortId()).orElseThrow(() -> new ResourceNotFoundException("TrainProvCohort", a.getCohortId().toString()));
        TrainProvPractitioner mod = practitioners.findForTenant(t, r.moderatorId()).orElseThrow(() -> new ResourceNotFoundException("TrainProvPractitioner", r.moderatorId().toString()));
        Optional<TrainProvAssessmentDecision> decision = decisions.findForAttempt(t, attemptId);
        Optional<TrainProvPractitionerAppointment> appt = appointments.findLiveForProgramme(t, c.getProgrammeId(), "MODERATOR").stream()
                .filter(x -> x.getPractitionerId().equals(mod.getId())).findFirst();
        PractitionerRules.Standing standing = appt.map(x -> x.standingOn(r.moderatedOn() == null ? today : r.moderatedOn())).orElse(PractitionerRules.Standing.NOT_VERIFIED);
        UUID decider = decision.map(TrainProvAssessmentDecision::getDecidedBy).orElse(null);
        boolean userInvolved = mod.getUserId() != null && (mod.getUserId().equals(a.getCreatedBy()) || mod.getUserId().equals(decider));
        ModerationRules.Facts facts = new ModerationRules.Facts(a.getStatus(), decision.isPresent(), decision.map(TrainProvAssessmentDecision::getDecidedOn).orElse(null),
                i.isRequiresModeration(), moderations.findForAttempt(t, attemptId).isPresent(), "ACTIVE".equals(mod.getStatus()), standing,
                mod.getId().equals(a.getAssessorId()), userInvolved, by.equals(decider));
        String problem = ModerationRules.recordProblem(facts, r.outcome(), r.basis(), r.moderatedOn(), today);
        if (problem != null) {
            boolean shape = r.outcome() == null || !ModerationRules.OUTCOMES.contains(r.outcome()) || r.moderatedOn() == null;
            throw new HandyFlowException(appt.isEmpty() && problem.contains("checked and in force") ? "That person has no moderator appointment for this programme" : problem,
                    shape ? HttpStatus.BAD_REQUEST : HttpStatus.CONFLICT, shape ? "TP_MODERATION_INVALID" : "TP_MODERATION_STATE");
        }
        TrainProvAssessmentModeration m = TrainProvAssessmentModeration.create(t, numbers.nextModerationNumber(t), attemptId, mod.getId(), appt.get().getId(), r.moderatedOn(),
                r.outcome(), r.basis(), by);
        moderations.save(m);
        return view(t, m);
    }

    @Transactional
    public ModerationView resolve(TenantId t, UUID moderationId, String note, UUID by) {
        TrainProvAssessmentModeration m = moderations.findForTenant(t, moderationId)
                .orElseThrow(() -> new ResourceNotFoundException("TrainProvAssessmentModeration", moderationId.toString()));
        guard(() -> { m.resolve(note, by); return null; });
        moderations.save(m);
        return view(t, m);
    }

    private ModerationView view(TenantId t, TrainProvAssessmentModeration m) {
        TrainProvAssessmentAttempt a = attempt(t, m.getAttemptId());
        String decision = decisions.findForAttempt(t, a.getId()).map(TrainProvAssessmentDecision::getDecision).orElse(null);
        return new ModerationView(m.getId(), m.getModerationNo(), a.getId(), a.getAttemptNo(), instrumentTitle(t, a.getInstrumentId()), learnerName(t, a.getEnrolmentId()),
                practitionerName(t, a.getAssessorId()), decision, m.getModeratorId(), practitionerName(t, m.getModeratorId()), m.getModeratedOn(), m.getOutcome(), m.getBasis(),
                m.getResolutionNote(), m.getResolvedAt());
    }

    private String instrumentTitle(TenantId t, UUID id) {
        return instruments.findForTenant(t, id).map(TrainProvAssessmentInstrument::getTitle).orElse(null);
    }

    private String cohortName(TenantId t, UUID id) {
        return cohorts.findForTenant(t, id).map(TrainProvCohort::getName).orElse(null);
    }

    private String learnerName(TenantId t, UUID enrolmentId) {
        return enrolments.findForTenant(t, enrolmentId).flatMap(e -> learners.findForTenant(t, e.getLearnerId())).map(l -> l.getFirstNames() + " " + l.getSurname()).orElse(null);
    }

    private String practitionerName(TenantId t, UUID id) {
        return practitioners.findForTenant(t, id).map(p -> p.getFirstNames() + " " + p.getSurname()).orElse(null);
    }

    private TrainProvAssessmentAttempt attempt(TenantId t, UUID id) {
        return attempts.findForTenant(t, id).orElseThrow(() -> new ResourceNotFoundException("TrainProvAssessmentAttempt", id.toString()));
    }

    private static <T> T guard(Supplier<T> work) {
        try {
            return work.get();
        } catch (IllegalStateException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "TP_MODERATION_STATE");
        } catch (IllegalArgumentException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.BAD_REQUEST, "TP_MODERATION_INVALID");
        }
    }
}
