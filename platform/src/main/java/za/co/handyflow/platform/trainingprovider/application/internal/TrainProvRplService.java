package za.co.handyflow.platform.trainingprovider.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvLearnerEnrolment;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvProgramme;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvProgrammeModule;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvRplApplication;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvRplEvidence;
import za.co.handyflow.platform.trainingprovider.domain.repository.TrainProvLearnerEnrolmentRepository;
import za.co.handyflow.platform.trainingprovider.domain.repository.TrainProvLearnerRepository;
import za.co.handyflow.platform.trainingprovider.domain.repository.TrainProvProgrammeModuleRepository;
import za.co.handyflow.platform.trainingprovider.domain.repository.TrainProvProgrammeRepository;
import za.co.handyflow.platform.trainingprovider.domain.repository.TrainProvRplApplicationRepository;
import za.co.handyflow.platform.trainingprovider.domain.repository.TrainProvRplEvidenceRepository;
import za.co.handyflow.platform.trainingprovider.domain.rules.RplRules;
import za.co.handyflow.platform.trainingprovider.dto.RplDtos.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Recognition of prior learning. Evidence is reviewed piece by piece, then a decision is recorded with a written basis and confirmed by a second person
 * (or by the decider with a written reason). The result is its own record: it does not feed readiness, completion or any certificate.
 */
@Service
@RequiredArgsConstructor
public class TrainProvRplService {

    private final TrainProvRplApplicationRepository applications;
    private final TrainProvRplEvidenceRepository evidence;
    private final TrainProvLearnerEnrolmentRepository enrolments;
    private final TrainProvLearnerRepository learners;
    private final TrainProvProgrammeRepository programmes;
    private final TrainProvProgrammeModuleRepository modules;
    private final TrainProvNumberGenerator numbers;

    public List<ApplicationView> list(TenantId t, UUID enrolmentId, String status) {
        return applications.search(t, enrolmentId, blank(status)).stream().<ApplicationView>map(a -> view(t, a)).toList();
    }

    public ApplicationDetail get(TenantId t, UUID id) {
        TrainProvRplApplication a = application(t, id);
        return new ApplicationDetail(view(t, a), evidence.forApplication(t, a.getId()).stream().<EvidenceView>map(TrainProvRplService::evidenceView).toList());
    }

    @Transactional
    public ApplicationView create(TenantId t, ApplicationRequest r, UUID by, LocalDate today) {
        TrainProvLearnerEnrolment e = enrolment(t, r.enrolmentId());
        boolean moduleOk = true;
        if (r.moduleId() != null) {
            TrainProvProgrammeModule m = modules.findForTenant(t, r.moduleId()).orElseThrow(() -> new ResourceNotFoundException("TrainProvProgrammeModule", r.moduleId().toString()));
            moduleOk = m.getProgrammeVersionId() != null && m.getProgrammeVersionId().equals(e.getProgrammeVersionId());
        }
        String problem = RplRules.applicationProblem(e.getStatus(), e.getStartDate(), r.claim(), r.appliedOn(), today, moduleOk, applications.liveExists(t, e.getId(), r.moduleId()));
        if (problem != null) {
            throw new HandyFlowException(problem, HttpStatus.CONFLICT, "TP_RPL_STATE");
        }
        TrainProvRplApplication a = TrainProvRplApplication.open(t, numbers.nextRplNumber(t), e.getId(), r.moduleId(), r.claim(), r.appliedOn(), by);
        applications.save(a);
        return view(t, a);
    }

    @Transactional
    public EvidenceView addEvidence(TenantId t, UUID applicationId, EvidenceRequest r, UUID by, LocalDate today) {
        TrainProvRplApplication a = application(t, applicationId);
        String problem = RplRules.evidenceProblem(a.getStatus(), r.kind(), r.description(), r.receivedOn(), a.getAppliedOn(), today);
        if (problem != null) {
            throw new HandyFlowException(problem, HttpStatus.CONFLICT, "TP_RPL_STATE");
        }
        TrainProvRplEvidence e = TrainProvRplEvidence.add(t, a.getId(), r.kind(), r.description(), r.sourceRef(), r.receivedOn(), by);
        evidence.save(e);
        return evidenceView(e);
    }

    @Transactional
    public EvidenceView review(TenantId t, UUID evidenceId, ReviewRequest r, UUID by, LocalDate today) {
        TrainProvRplEvidence e = evidenceRow(t, evidenceId);
        TrainProvRplApplication a = application(t, e.getApplicationId());
        guard(() -> { e.review(a.getStatus(), r.review(), r.note(), r.reviewedOn(), by, today); return null; });
        evidence.save(e);
        return evidenceView(e);
    }

    @Transactional
    public EvidenceView withdrawEvidence(TenantId t, UUID evidenceId, String reason, UUID by) {
        TrainProvRplEvidence e = evidenceRow(t, evidenceId);
        TrainProvRplApplication a = application(t, e.getApplicationId());
        guard(() -> { e.withdraw(a.getStatus(), reason, by); return null; });
        evidence.save(e);
        return evidenceView(e);
    }

    @Transactional
    public ApplicationView decide(TenantId t, UUID id, DecisionRequest r, UUID by, LocalDate today) {
        TrainProvRplApplication a = application(t, id);
        int live = 0;
        int unreviewed = 0;
        int accepted = 0;
        for (TrainProvRplEvidence e : evidence.forApplication(t, a.getId())) {
            if (!RplRules.RECEIVED.equals(e.getStatus())) {
                continue;
            }
            live++;
            if (!e.isReviewed()) {
                unreviewed++;
            } else if (RplRules.ACCEPTED.equals(e.getReview())) {
                accepted++;
            }
        }
        String problem = RplRules.decisionProblem(a.getStatus(), live, unreviewed, accepted, r.outcome(), r.basis(), r.assessorName(), r.decidedOn(), a.getAppliedOn(), today,
                r.singleReason(), r.confirmAlone());
        if (problem != null) {
            throw new HandyFlowException(problem, HttpStatus.CONFLICT, "TP_RPL_STATE");
        }
        guard(() -> { a.decide(r.outcome(), r.basis(), r.assessorName(), r.decidedOn(), by, r.confirmAlone(), r.singleReason()); return null; });
        applications.save(a);
        return view(t, a);
    }

    @Transactional
    public ApplicationView confirm(TenantId t, UUID id, LocalDate on, UUID by, LocalDate today) {
        TrainProvRplApplication a = application(t, id);
        guard(() -> { a.confirm(by, on, today); return null; });
        applications.save(a);
        return view(t, a);
    }

    @Transactional
    public ApplicationView withdraw(TenantId t, UUID id, String reason, UUID by) {
        TrainProvRplApplication a = application(t, id);
        guard(() -> { a.withdraw(reason, by); return null; });
        applications.save(a);
        return view(t, a);
    }

    private static EvidenceView evidenceView(TrainProvRplEvidence e) {
        return new EvidenceView(e.getId(), e.getApplicationId(), e.getKind(), e.getDescription(), e.getSourceRef(), e.getReceivedOn(), e.getStatus(), e.getWithdrawReason(),
                e.getReview(), e.getReviewNote(), e.getReviewedOn());
    }

    private ApplicationView view(TenantId t, TrainProvRplApplication a) {
        TrainProvLearnerEnrolment e = enrolment(t, a.getEnrolmentId());
        int live = 0;
        int unreviewed = 0;
        int accepted = 0;
        for (TrainProvRplEvidence x : evidence.forApplication(t, a.getId())) {
            if (!RplRules.RECEIVED.equals(x.getStatus())) {
                continue;
            }
            live++;
            if (!x.isReviewed()) {
                unreviewed++;
            } else if (RplRules.ACCEPTED.equals(x.getReview())) {
                accepted++;
            }
        }
        String learner = learners.findForTenant(t, e.getLearnerId()).map(x -> x.getFirstNames() + " " + x.getSurname()).orElse(null);
        String programme = programmes.findForTenant(t, e.getProgrammeId()).map(TrainProvProgramme::getTitle).orElse(null);
        String moduleTitle = a.getModuleId() == null ? null : modules.findForTenant(t, a.getModuleId()).map(TrainProvProgrammeModule::getTitle).orElse(null);
        return new ApplicationView(a.getId(), a.getRplNo(), e.getId(), e.getEnrolmentNo(), learner, programme, a.getModuleId(), moduleTitle, a.getClaim(), a.getAppliedOn(),
                a.getStatus(), a.getOutcome(), a.getDecisionBasis(), a.getAssessorName(), a.getDecidedOn(), a.getSingleReason(), a.getConfirmedOn(), a.getWithdrawReason(),
                live, unreviewed, accepted, RplRules.stateLabel(a.getStatus(), a.getOutcome(), a.confirmedAlone()));
    }

    private TrainProvRplApplication application(TenantId t, UUID id) {
        return applications.findForTenant(t, id).orElseThrow(() -> new ResourceNotFoundException("TrainProvRplApplication", id.toString()));
    }

    private TrainProvRplEvidence evidenceRow(TenantId t, UUID id) {
        return evidence.findForTenant(t, id).orElseThrow(() -> new ResourceNotFoundException("TrainProvRplEvidence", id.toString()));
    }

    private TrainProvLearnerEnrolment enrolment(TenantId t, UUID id) {
        return enrolments.findForTenant(t, id).orElseThrow(() -> new ResourceNotFoundException("TrainProvLearnerEnrolment", id.toString()));
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static <T> T guard(Supplier<T> work) {
        try {
            return work.get();
        } catch (IllegalStateException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "TP_RPL_STATE");
        } catch (IllegalArgumentException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.BAD_REQUEST, "TP_RPL_INVALID");
        }
    }
}
