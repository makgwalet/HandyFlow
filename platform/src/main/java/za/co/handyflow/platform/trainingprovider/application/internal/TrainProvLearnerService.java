package za.co.handyflow.platform.trainingprovider.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvClient;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvLearner;
import za.co.handyflow.platform.trainingprovider.domain.repository.*;
import za.co.handyflow.platform.trainingprovider.domain.rules.LearnerRules;
import za.co.handyflow.platform.trainingprovider.dto.LearnerDtos.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Learners: the people the provider trains in its own right. This sits beside the client and delegate records
 * and changes none of them. A learner can optionally be linked to a client (employer or sponsor) and to an
 * existing delegate. Archived with a reason, never deleted, and never while an enrolment is live.
 */
@Service
@RequiredArgsConstructor
public class TrainProvLearnerService {

    private final TrainProvLearnerRepository learners;
    private final TrainProvLearnerEnrolmentRepository enrolments;
    private final TrainProvClientRepository clients;
    private final TrainProvDelegateRepository delegates;
    private final TrainProvEnrolmentService enrolmentService;
    private final TrainProvNumberGenerator numbers;

    public List<LearnerView> list(TenantId t, String status, UUID clientId, String search) {
        String q = search == null || search.isBlank() ? null : search.trim();
        return learners.search(t, status, clientId, q).stream().<LearnerView>map(l -> view(t, l, false)).toList();
    }

    public LearnerDetail detail(TenantId t, UUID id) {
        TrainProvLearner l = learner(t, id);
        return new LearnerDetail(view(t, l, true), enrolmentService.applicationsFor(t, id), enrolmentService.enrolmentsFor(t, id));
    }

    @Transactional
    public LearnerView create(TenantId t, LearnerRequest r, UUID by, LocalDate today) {
        checkLinks(t, r);
        checkIdentity(t, r, null);
        TrainProvLearner l = guard(() -> TrainProvLearner.create(t, numbers.nextLearnerNumber(t), r.firstNames(), r.surname(), r.idType(), r.idNumber(),
                r.dateOfBirth(), r.email(), r.phone(), r.address(), r.province(), r.clientId(), r.delegateId(), r.consentGiven(), r.consentOn(),
                r.consentNote(), by, today));
        learners.save(l);
        return view(t, l, true);
    }

    @Transactional
    public LearnerView update(TenantId t, UUID id, LearnerRequest r, LocalDate today) {
        TrainProvLearner l = learner(t, id);
        checkLinks(t, r);
        checkIdentity(t, r, id);
        guard(() -> { l.update(r.firstNames(), r.surname(), r.idType(), r.idNumber(), r.dateOfBirth(), r.email(), r.phone(), r.address(),
                r.province(), r.clientId(), r.delegateId(), r.consentGiven(), r.consentOn(), r.consentNote(), today); return null; });
        learners.save(l);
        return view(t, l, true);
    }

    @Transactional
    public LearnerView archive(TenantId t, UUID id, String reason, UUID by) {
        TrainProvLearner l = learner(t, id);
        long live = enrolments.countLiveForLearner(t, id);
        if (live > 0) {
            throw new HandyFlowException("This learner has " + live + " live enrolment" + (live == 1 ? "" : "s")
                    + ". Withdraw or finish them first", HttpStatus.CONFLICT, "TP_LEARNER_STATE");
        }
        guard(() -> { l.archive(reason, by); return null; });
        learners.save(l);
        return view(t, l, true);
    }

    @Transactional
    public LearnerView restore(TenantId t, UUID id) {
        TrainProvLearner l = learner(t, id);
        if (l.getIdNumber() != null && learners.identityTaken(t, l.getIdType(), l.getIdNumber(), id)) {
            throw new HandyFlowException("Another live learner now holds that identity document", HttpStatus.CONFLICT, "TP_LEARNER_DUPLICATE");
        }
        guard(() -> { l.restore(); return null; });
        learners.save(l);
        return view(t, l, true);
    }

    private void checkLinks(TenantId t, LearnerRequest r) {
        if (r.clientId() != null) {
            clients.findActiveById(t, r.clientId()).orElseThrow(() -> new ResourceNotFoundException("TrainProvClient", r.clientId().toString()));
        }
        if (r.delegateId() != null) {
            delegates.findActiveById(t, r.delegateId()).orElseThrow(() -> new ResourceNotFoundException("TrainProvDelegate", r.delegateId().toString()));
        }
    }

    private void checkIdentity(TenantId t, LearnerRequest r, UUID excludeId) {
        if (r.idNumber() != null && !r.idNumber().isBlank() && learners.identityTaken(t, r.idType(), r.idNumber().trim(), excludeId)) {
            throw new HandyFlowException("A live learner already holds that identity document", HttpStatus.CONFLICT, "TP_LEARNER_DUPLICATE");
        }
    }

    private LearnerView view(TenantId t, TrainProvLearner l, boolean full) {
        String clientName = l.getClientId() == null ? null : clients.findActiveById(t, l.getClientId()).map(TrainProvClient::getTradingName).orElse(null);
        return new LearnerView(l.getId(), l.getLearnerNo(), l.getFirstNames(), l.getSurname(), l.getIdType(), full ? l.getIdNumber() : null,
                LearnerRules.mask(l.getIdNumber()), l.getDateOfBirth(), l.getEmail(), l.getPhone(), l.getAddress(), l.getProvince(), l.getClientId(),
                clientName, l.getDelegateId(), l.isConsentGiven(), l.getConsentOn(), l.getConsentNote(), l.getStatus(), l.getArchivedReason(),
                enrolments.countLiveForLearner(t, l.getId()));
    }

    private TrainProvLearner learner(TenantId t, UUID id) {
        return learners.findForTenant(t, id).orElseThrow(() -> new ResourceNotFoundException("TrainProvLearner", id.toString()));
    }

    private static <T> T guard(Supplier<T> work) {
        try {
            return work.get();
        } catch (IllegalStateException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "TP_LEARNER_STATE");
        } catch (IllegalArgumentException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.BAD_REQUEST, "TP_LEARNER_INVALID");
        }
    }
}
