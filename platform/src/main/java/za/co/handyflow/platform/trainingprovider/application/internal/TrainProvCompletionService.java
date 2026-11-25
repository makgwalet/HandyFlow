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
import za.co.handyflow.platform.trainingprovider.domain.rules.CompletionRules;
import za.co.handyflow.platform.trainingprovider.dto.CompletionDtos.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Completion records. Readiness is worked out from the approved instruments for the enrolment's programme version, the learner's live attempts,
 * their competency decisions and moderation. It is advice only: a person records the outcome with a written basis, and completing with items
 * still open needs a written reason, kept together with a snapshot of what was open. Attendance is not consulted, no threshold exists, and
 * completion is not certification and does not touch the enrolment's own status.
 */
@Service
@RequiredArgsConstructor
public class TrainProvCompletionService {

    static final String NOTE = "Readiness is built from competency decisions and moderation. It is advice. A person records the outcome, and completion is not a certificate.";

    private final TrainProvCompletionRepository completions;
    private final TrainProvLearnerEnrolmentRepository enrolments;
    private final TrainProvLearnerRepository learners;
    private final TrainProvProgrammeRepository programmes;
    private final TrainProvProgrammeVersionRepository versions;
    private final TrainProvAssessmentInstrumentRepository instruments;
    private final TrainProvAssessmentAttemptRepository attempts;
    private final TrainProvAssessmentDecisionRepository decisions;
    private final TrainProvAssessmentModerationRepository moderations;
    private final TrainProvNumberGenerator numbers;

    public Readiness readiness(TenantId t, UUID enrolmentId) {
        TrainProvLearnerEnrolment e = enrolment(t, enrolmentId);
        List<TrainProvAssessmentInstrument> required = instruments.search(t, e.getProgrammeVersionId(), null, "APPROVED");
        List<TrainProvAssessmentAttempt> live = attempts.search(t, null, null, enrolmentId).stream().filter(a -> CompletionRules.RECORDED.equals(a.getStatus())).toList();
        Map<UUID, TrainProvAssessmentDecision> decided = live.isEmpty() ? Map.of()
                : decisions.findForAttempts(t, live.stream().map(TrainProvAssessmentAttempt::getId).toList()).stream()
                        .collect(Collectors.toMap(TrainProvAssessmentDecision::getAttemptId, Function.identity()));
        List<ReadinessRow> rows = new ArrayList<>();
        int gaps = 0;
        for (TrainProvAssessmentInstrument i : required) {
            List<CompletionRules.AttemptFact> facts = new ArrayList<>();
            int count = 0;
            for (TrainProvAssessmentAttempt a : live) {
                if (!a.getInstrumentId().equals(i.getId())) {
                    continue;
                }
                count++;
                TrainProvAssessmentDecision d = decided.get(a.getId());
                TrainProvAssessmentModeration m = d == null ? null : moderations.findForAttempt(t, a.getId()).orElse(null);
                facts.add(new CompletionRules.AttemptFact(d == null ? null : d.getDecision(), m == null ? null : m.getOutcome(), m != null && m.isResolved()));
            }
            String state = CompletionRules.instrumentState(i.isRequiresModeration(), facts);
            if (!CompletionRules.COMPETENT.equals(state)) {
                gaps++;
            }
            rows.add(new ReadinessRow(i.getId(), i.getInstrumentNo(), i.getTitle(), i.isRequiresModeration(), state, count));
        }
        return new Readiness(e.getId(), e.getEnrolmentNo(), e.getStatus(), learnerName(t, e.getLearnerId()), programmeTitle(t, e.getProgrammeId()),
                versions.findForTenant(t, e.getProgrammeVersionId()).map(TrainProvProgrammeVersion::getVersionNo).orElse(null), rows, gaps, NOTE);
    }

    public List<CompletionView> list(TenantId t, UUID enrolmentId, String outcome, String status) {
        return completions.search(t, enrolmentId, blank(outcome), blank(status)).stream().<CompletionView>map(c -> view(t, c)).toList();
    }

    @Transactional
    public CompletionView record(TenantId t, RecordCompletion r, UUID by, LocalDate today) {
        TrainProvLearnerEnrolment e = enrolment(t, r.enrolmentId());
        Readiness ready = readiness(t, e.getId());
        boolean completing = CompletionRules.COMPLETED.equals(r.outcome());
        String problem = CompletionRules.recordProblem(e.getStatus(), e.getStartDate(), completions.findLiveForEnrolment(t, e.getId()).isPresent(), r.outcome(), r.outcomeOn(), today,
                r.basis(), ready.openGaps(), r.overrideReason());
        if (problem != null) {
            boolean shape = r.outcome() == null || !CompletionRules.OUTCOMES.contains(r.outcome()) || r.outcomeOn() == null;
            throw new HandyFlowException(problem, shape ? HttpStatus.BAD_REQUEST : HttpStatus.CONFLICT, shape ? "TP_COMPLETION_INVALID" : "TP_COMPLETION_STATE");
        }
        String snapshot = ready.rows().stream().filter(x -> !CompletionRules.COMPETENT.equals(x.state())).map(x -> x.instrumentTitle() + " (" + x.state() + ")")
                .collect(Collectors.joining("; "));
        String override = completing && ready.openGaps() > 0 ? r.overrideReason() : null;
        TrainProvCompletion c = TrainProvCompletion.create(t, numbers.nextCompletionNumber(t), e.getId(), r.outcome(), r.outcomeOn(), r.basis(), ready.openGaps(), snapshot, override, by);
        completions.save(c);
        return view(t, c);
    }

    @Transactional
    public CompletionView withdraw(TenantId t, UUID id, String reason, UUID by) {
        TrainProvCompletion c = completions.findForTenant(t, id).orElseThrow(() -> new ResourceNotFoundException("TrainProvCompletion", id.toString()));
        guard(() -> { c.withdraw(reason, by); return null; });
        completions.save(c);
        return view(t, c);
    }

    private CompletionView view(TenantId t, TrainProvCompletion c) {
        TrainProvLearnerEnrolment e = enrolment(t, c.getEnrolmentId());
        return new CompletionView(c.getId(), c.getCompletionNo(), e.getId(), e.getEnrolmentNo(), e.getLearnerId(), learnerName(t, e.getLearnerId()), programmeTitle(t, e.getProgrammeId()),
                c.getOutcome(), c.getOutcomeOn(), c.getBasis(), c.getOpenGaps(), c.getGapsSnapshot(), c.getGapsOverrideReason(), c.getStatus(), c.getWithdrawnReason(), c.getCreatedAt());
    }

    private TrainProvLearnerEnrolment enrolment(TenantId t, UUID id) {
        return enrolments.findForTenant(t, id).orElseThrow(() -> new ResourceNotFoundException("TrainProvLearnerEnrolment", id.toString()));
    }

    private String learnerName(TenantId t, UUID learnerId) {
        return learners.findForTenant(t, learnerId).map(l -> l.getFirstNames() + " " + l.getSurname()).orElse(null);
    }

    private String programmeTitle(TenantId t, UUID programmeId) {
        return programmes.findForTenant(t, programmeId).map(TrainProvProgramme::getTitle).orElse(null);
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static <T> T guard(Supplier<T> work) {
        try {
            return work.get();
        } catch (IllegalStateException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "TP_COMPLETION_STATE");
        } catch (IllegalArgumentException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.BAD_REQUEST, "TP_COMPLETION_INVALID");
        }
    }
}
