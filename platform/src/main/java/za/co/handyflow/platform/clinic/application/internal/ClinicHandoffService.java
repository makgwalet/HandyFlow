package za.co.handyflow.platform.clinic.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.clinic.domain.model.ClinicConsultation;
import za.co.handyflow.platform.clinic.domain.model.ClinicConsultationTransition;
import za.co.handyflow.platform.clinic.domain.model.HandoffReturnReason;
import za.co.handyflow.platform.clinic.domain.repository.ClinicConsultationRepository;
import za.co.handyflow.platform.clinic.domain.repository.ClinicConsultationTransitionRepository;
import za.co.handyflow.platform.clinic.dto.HandoffDtos.TransitionResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/**
 * Nurse to doctor handoff (DEC-CLINIC-004). Every move is validated against
 * {@link ClinicConsultation#HANDOFF_TRANSITIONS} and written to the transition log.
 * Pure workflow: nothing here interprets clinical content. Role enforcement
 * (who may accept or sign) arrives with the clinical role split (S1-5).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClinicHandoffService {

    private final ClinicConsultationRepository           consultationRepo;
    private final ClinicConsultationTransitionRepository transitionRepo;

    /** DRAFT -> NURSE_IN_PROGRESS: marks the working copy as nurse-led. */
    @Transactional
    public ClinicConsultation startNurseWork(TenantId tenantId, UUID id) {
        ClinicConsultation c = load(tenantId, id);
        move(c, "NURSE_IN_PROGRESS", null, null);
        return c;
    }

    /** Nurse hands over. The nurse's portion must be complete enough for a doctor to start. */
    @Transactional
    public ClinicConsultation sendToDoctor(TenantId tenantId, UUID id, String comment) {
        ClinicConsultation c = load(tenantId, id);
        List<String> missing = missingNursePortion(c);
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("Cannot send to doctor yet. Missing: " + String.join(", ", missing) + ".");
        }
        move(c, "READY_FOR_DOCTOR", null, blankToNull(comment));
        return c;
    }

    /** Doctor accepts; the optional practitionerId is stored as the reviewing practitioner. */
    @Transactional
    public ClinicConsultation accept(TenantId tenantId, UUID id, UUID practitionerId) {
        ClinicConsultation c = load(tenantId, id);
        move(c, "DOCTOR_REVIEWING", null, null);
        if (practitionerId != null) c.assignReviewer(practitionerId);
        consultationRepo.save(c);
        return c;
    }

    /** Doctor returns it. A reason code and a comment are both required. */
    @Transactional
    public ClinicConsultation returnToNurse(TenantId tenantId, UUID id, String reasonCode, String comment) {
        HandoffReturnReason reason = parseReason(reasonCode);
        if (comment == null || comment.isBlank()) {
            throw new IllegalArgumentException("A comment is required when returning a consultation to the nurse.");
        }
        ClinicConsultation c = load(tenantId, id);
        move(c, "RETURNED_TO_NURSE", reason.name(), comment.trim());
        return c;
    }

    /** Nurse picks a returned consultation back up. */
    @Transactional
    public ClinicConsultation resumeNurseWork(TenantId tenantId, UUID id) {
        ClinicConsultation c = load(tenantId, id);
        if (!"RETURNED_TO_NURSE".equals(c.getStatus())) {
            throw new IllegalStateException("Only a returned consultation can be resumed (is " + c.getStatus() + ").");
        }
        move(c, "NURSE_IN_PROGRESS", null, null);
        return c;
    }

    /** Doctor finishes their review. Signing is a separate step. */
    @Transactional
    public ClinicConsultation doctorComplete(TenantId tenantId, UUID id) {
        ClinicConsultation c = load(tenantId, id);
        move(c, "DOCTOR_COMPLETED", null, null);
        return c;
    }

    @Transactional(readOnly = true)
    public List<ClinicConsultation> queue(TenantId tenantId) {
        return consultationRepo.findHandoffQueue(tenantId);
    }

    @Transactional(readOnly = true)
    public List<TransitionResponse> history(TenantId tenantId, UUID id) {
        load(tenantId, id);
        return transitionRepo.findByConsultation(tenantId, id).stream()
                .map(t -> new TransitionResponse(t.getId(), t.getConsultationId(), t.getFromStatus(),
                        t.getToStatus(), t.getActorUserId(), t.getReasonCode(), t.getComment(), t.getCreatedAt()))
                .toList();
    }

    /** What the doctor needs before they can start: a complaint and at least one recorded measurement. */
    static List<String> missingNursePortion(ClinicConsultation c) {
        List<String> missing = new java.util.ArrayList<>();
        if (c.getChiefComplaint() == null || c.getChiefComplaint().isBlank()) missing.add("chief complaint");
        boolean anyVital = c.getWeightKg() != null || c.getHeightCm() != null
                || (c.getBloodPressure() != null && !c.getBloodPressure().isBlank())
                || c.getPulseBpm() != null || c.getTemperatureC() != null || c.getOxygenSatPct() != null;
        if (!anyVital) missing.add("at least one vital sign");
        return missing;
    }

    private void move(ClinicConsultation c, String next, String reasonCode, String comment) {
        String from = c.getStatus();
        c.transitionTo(next);
        consultationRepo.save(c);
        transitionRepo.save(ClinicConsultationTransition.of(c, from, next, currentUserIdOrNull(), reasonCode, comment));
        log.info("Consultation={} handoff {} -> {}", c.getId(), from, next);
    }

    private ClinicConsultation load(TenantId tenantId, UUID id) {
        return consultationRepo.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Consultation", id.toString()));
    }

    private static HandoffReturnReason parseReason(String code) {
        if (code == null || code.isBlank()) throw new IllegalArgumentException("A return reason is required.");
        try {
            return HandoffReturnReason.valueOf(code.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown return reason '" + code + "'.");
        }
    }

    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }

    private static UUID currentUserIdOrNull() {
        try {
            return za.co.handyflow.platform.shared.UserContext.getCurrentUserId();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
