package za.co.handyflow.platform.trainingprovider.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvLearnerEnrolment;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvLearnershipAgreement;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvProgramme;
import za.co.handyflow.platform.trainingprovider.domain.repository.TrainProvClientRepository;
import za.co.handyflow.platform.trainingprovider.domain.repository.TrainProvLearnerEnrolmentRepository;
import za.co.handyflow.platform.trainingprovider.domain.repository.TrainProvLearnerRepository;
import za.co.handyflow.platform.trainingprovider.domain.repository.TrainProvLearnershipAgreementRepository;
import za.co.handyflow.platform.trainingprovider.domain.repository.TrainProvProgrammeRepository;
import za.co.handyflow.platform.trainingprovider.domain.rules.LearnershipRules;
import za.co.handyflow.platform.trainingprovider.dto.LearnershipDtos.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Learnership agreements. A record of the paper agreement between learner, employer and provider. The registration reference is kept as entered and is
 * never verified, so nothing here says a learnership is registered or approved. No rates, grants or eligibility rules are held. Nothing is deleted.
 */
@Service
@RequiredArgsConstructor
public class TrainProvLearnershipService {

    private final TrainProvLearnershipAgreementRepository agreements;
    private final TrainProvLearnerEnrolmentRepository enrolments;
    private final TrainProvLearnerRepository learners;
    private final TrainProvProgrammeRepository programmes;
    private final TrainProvClientRepository clients;
    private final TrainProvNumberGenerator numbers;

    public List<AgreementView> list(TenantId t, UUID enrolmentId, String status) {
        return agreements.search(t, enrolmentId, blank(status)).stream().<AgreementView>map(a -> view(t, a)).toList();
    }

    public AgreementView get(TenantId t, UUID id) {
        return view(t, agreement(t, id));
    }

    @Transactional
    public AgreementView create(TenantId t, AgreementRequest r, UUID by) {
        TrainProvLearnerEnrolment e = enrolment(t, r.enrolmentId());
        checkClient(t, r.clientId());
        String problem = LearnershipRules.agreementProblem(e.getStatus(), r.employerName(), r.startDate(), r.endDate(), e.getStartDate(), agreements.liveExists(t, e.getId()));
        if (problem != null) {
            throw new HandyFlowException(problem, HttpStatus.CONFLICT, "TP_LEARNERSHIP_STATE");
        }
        TrainProvLearnershipAgreement a = guard(() -> TrainProvLearnershipAgreement.create(t, numbers.nextLearnershipNumber(t), e.getId(), r.clientId(), r.employerName(),
                r.employerContact(), r.startDate(), r.endDate(), by));
        agreements.save(a);
        return view(t, a);
    }

    @Transactional
    public AgreementView update(TenantId t, UUID id, AgreementUpdate r) {
        TrainProvLearnershipAgreement a = agreement(t, id);
        checkClient(t, r.clientId());
        TrainProvLearnerEnrolment e = enrolment(t, a.getEnrolmentId());
        guard(() -> {
            String details = LearnershipRules.detailsProblem(r.employerName(), r.startDate(), r.endDate(), e.getStartDate());
            if (details != null) {
                throw new IllegalArgumentException(details);
            }
            a.change(r.clientId(), r.employerName(), r.employerContact(), r.startDate(), r.endDate());
            return null;
        });
        agreements.save(a);
        return view(t, a);
    }

    @Transactional
    public AgreementView sign(TenantId t, UUID id, SignRequest r, UUID by, LocalDate today) {
        TrainProvLearnershipAgreement a = agreement(t, id);
        guard(() -> { a.sign(r.learnerSignedOn(), r.employerSignedOn(), r.providerSignedOn(), today, by); return null; });
        agreements.save(a);
        return view(t, a);
    }

    @Transactional
    public AgreementView recordRegistration(TenantId t, UUID id, RegistrationRequest r, UUID by, LocalDate today) {
        TrainProvLearnershipAgreement a = agreement(t, id);
        guard(() -> { a.recordRegistration(r.reference(), r.recordedOn(), today, by); return null; });
        agreements.save(a);
        return view(t, a);
    }

    @Transactional
    public AgreementView end(TenantId t, UUID id, EndRequest r, LocalDate today) {
        TrainProvLearnershipAgreement a = agreement(t, id);
        guard(() -> { a.end(r.endedOn(), r.reason(), today); return null; });
        agreements.save(a);
        return view(t, a);
    }

    @Transactional
    public AgreementView cancel(TenantId t, UUID id, String reason) {
        TrainProvLearnershipAgreement a = agreement(t, id);
        guard(() -> { a.cancel(reason); return null; });
        agreements.save(a);
        return view(t, a);
    }

    private void checkClient(TenantId t, UUID clientId) {
        if (clientId != null) {
            clients.findActiveById(t, clientId).orElseThrow(() -> new ResourceNotFoundException("TrainProvClient", clientId.toString()));
        }
    }

    private AgreementView view(TenantId t, TrainProvLearnershipAgreement a) {
        TrainProvLearnerEnrolment e = enrolment(t, a.getEnrolmentId());
        String learner = learners.findForTenant(t, e.getLearnerId()).map(x -> x.getFirstNames() + " " + x.getSurname()).orElse(null);
        String programme = programmes.findForTenant(t, e.getProgrammeId()).map(TrainProvProgramme::getTitle).orElse(null);
        return new AgreementView(a.getId(), a.getAgreementNo(), e.getId(), e.getEnrolmentNo(), learner, programme, a.getClientId(), a.getEmployerName(), a.getEmployerContact(),
                a.getStartDate(), a.getEndDate(), a.getStatus(), a.getLearnerSignedOn(), a.getEmployerSignedOn(), a.getProviderSignedOn(), a.getRegistrationRef(),
                a.getRegistrationRecordedOn(), a.getEndedOn(), a.getEndReason(), a.getCancelReason(),
                LearnershipRules.summary(a.getStatus(), a.getLearnerSignedOn(), a.getEmployerSignedOn(), a.getProviderSignedOn(), a.getRegistrationRef()));
    }

    private TrainProvLearnershipAgreement agreement(TenantId t, UUID id) {
        return agreements.findForTenant(t, id).orElseThrow(() -> new ResourceNotFoundException("TrainProvLearnershipAgreement", id.toString()));
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
            throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "TP_LEARNERSHIP_STATE");
        } catch (IllegalArgumentException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.BAD_REQUEST, "TP_LEARNERSHIP_INVALID");
        }
    }
}
