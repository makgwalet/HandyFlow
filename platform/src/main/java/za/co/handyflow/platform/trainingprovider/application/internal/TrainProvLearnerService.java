package za.co.handyflow.platform.trainingprovider.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvClient;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvDelegate;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvLearner;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvLearnerEvent;
import za.co.handyflow.platform.trainingprovider.domain.repository.*;
import za.co.handyflow.platform.trainingprovider.domain.rules.LearnerMatchRules;
import za.co.handyflow.platform.trainingprovider.domain.rules.LearnerRules;
import za.co.handyflow.platform.trainingprovider.dto.LearnerDtos.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
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
    private final TrainProvLearnerEventRepository events;

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
        checkDelegateLink(t, null, null, r.delegateId(), r.idNumber(), r.linkReason());
        TrainProvLearner l = guard(() -> TrainProvLearner.create(t, numbers.nextLearnerNumber(t), r.firstNames(), r.surname(), r.idType(), r.idNumber(),
                r.dateOfBirth(), r.email(), r.phone(), r.address(), r.province(), r.clientId(), r.delegateId(), r.consentGiven(), r.consentOn(),
                r.consentNote(), by, today));
        learners.save(l);
        if (r.delegateId() != null) {
            events.save(TrainProvLearnerEvent.delegate(t, l.getId(), true, r.delegateId(), r.linkReason(), by));
        }
        return view(t, l, true);
    }

    @Transactional
    public LearnerView update(TenantId t, UUID id, LearnerRequest r, LocalDate today, UUID by) {
        TrainProvLearner l = learner(t, id);
        checkLinks(t, r);
        checkIdentity(t, r, id);
        UUID before = l.getDelegateId();
        if (!Objects.equals(before, r.delegateId())) {
            checkDelegateLink(t, id, before, r.delegateId(), r.idNumber(), r.linkReason());
        }
        guard(() -> { l.update(r.firstNames(), r.surname(), r.idType(), r.idNumber(), r.dateOfBirth(), r.email(), r.phone(), r.address(),
                r.province(), r.clientId(), r.delegateId(), r.consentGiven(), r.consentOn(), r.consentNote(), today); return null; });
        learners.save(l);
        if (!Objects.equals(before, r.delegateId())) {
            if (before != null) {
                events.save(TrainProvLearnerEvent.delegate(t, id, false, before, r.linkReason(), by));
            }
            if (r.delegateId() != null) {
                events.save(TrainProvLearnerEvent.delegate(t, id, true, r.delegateId(), r.linkReason(), by));
            }
        }
        return view(t, l, true);
    }

    /** Links, moves or removes the delegate link on its own, with the same checks and history as an edit. */
    @Transactional
    public LearnerView linkDelegate(TenantId t, UUID id, UUID delegateId, String reason, UUID by) {
        TrainProvLearner l = learner(t, id);
        UUID before = l.getDelegateId();
        if (Objects.equals(before, delegateId)) {
            return view(t, l, true);
        }
        checkDelegateLink(t, id, before, delegateId, l.getIdNumber(), reason);
        guard(() -> { l.relink(delegateId); return null; });
        learners.save(l);
        if (before != null) {
            events.save(TrainProvLearnerEvent.delegate(t, id, false, before, reason, by));
        }
        if (delegateId != null) {
            events.save(TrainProvLearnerEvent.delegate(t, id, true, delegateId, reason, by));
        }
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

    /**
     * A delegate can be linked to one live learner. Taking a link off, or moving it, needs a written reason. A delegate whose identity
     * number differs from the learner's can only be linked with a written reason too: it is probably a different person.
     */
    private void checkDelegateLink(TenantId t, UUID learnerId, UUID before, UUID delegateId, String learnerIdNumber, String linkReason) {
        if (before != null && !LearnerMatchRules.reasonOk(linkReason)) {
            throw new HandyFlowException("Say why the delegate link is changing, in at least " + LearnerMatchRules.MIN_REASON + " characters",
                    HttpStatus.BAD_REQUEST, "TP_LEARNER_LINK");
        }
        if (delegateId == null) {
            return;
        }
        for (TrainProvLearner other : learners.liveForDelegate(t, delegateId)) {
            if (!other.getId().equals(learnerId)) {
                throw new HandyFlowException("That delegate is already linked to learner " + other.getLearnerNo() + " (" + other.fullName()
                        + "). Unlink it there first, or merge the two learners if they are one person", HttpStatus.CONFLICT, "TP_LEARNER_LINK");
            }
        }
        TrainProvDelegate d = delegates.findActiveById(t, delegateId)
                .orElseThrow(() -> new ResourceNotFoundException("TrainProvDelegate", delegateId.toString()));
        String conflict = LearnerMatchRules.linkConflict(learnerIdNumber, d.getIdNumber());
        if (conflict != null && !LearnerMatchRules.reasonOk(linkReason)) {
            throw new HandyFlowException(conflict, HttpStatus.CONFLICT, "TP_LEARNER_LINK");
        }
    }

    private void checkIdentity(TenantId t, LearnerRequest r, UUID excludeId) {
        if (r.idNumber() != null && !r.idNumber().isBlank() && learners.identityTaken(t, r.idType(), r.idNumber().trim(), excludeId)) {
            throw new HandyFlowException("A live learner already holds that identity document", HttpStatus.CONFLICT, "TP_LEARNER_DUPLICATE");
        }
    }

    private LearnerView view(TenantId t, TrainProvLearner l, boolean full) {
        String clientName = l.getClientId() == null ? null : clients.findActiveById(t, l.getClientId()).map(TrainProvClient::getTradingName).orElse(null);
        String delegateName = l.getDelegateId() == null ? null : delegates.findActiveById(t, l.getDelegateId()).map(TrainProvDelegate::getFullName).orElse(null);
        return new LearnerView(l.getId(), l.getLearnerNo(), l.getFirstNames(), l.getSurname(), l.getIdType(), full ? l.getIdNumber() : null,
                LearnerRules.mask(l.getIdNumber()), l.getDateOfBirth(), l.getEmail(), l.getPhone(), l.getAddress(), l.getProvince(), l.getClientId(),
                clientName, l.getDelegateId(), l.isConsentGiven(), l.getConsentOn(), l.getConsentNote(), l.getStatus(), l.getArchivedReason(),
                enrolments.countLiveForLearner(t, l.getId()), l.getMergedIntoId(), delegateName);
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
