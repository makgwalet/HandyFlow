package za.co.handyflow.platform.clinic.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.clinic.domain.model.ClinicConsultation;
import za.co.handyflow.platform.clinic.domain.repository.*;
import za.co.handyflow.platform.clinic.dto.VisitDtos.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.*;
import java.util.stream.Collectors;

/** A patient's visits with the clinical notes, prescriptions, addenda and the people involved, newest first (patch 0160). */
@Service
@RequiredArgsConstructor
public class ClinicVisitService {

    private final ClinicPatientRepository                patientRepo;
    private final ClinicConsultationRepository           consultationRepo;
    private final ClinicConsultationTransitionRepository transitionRepo;
    private final ClinicConsultationAddendumRepository   addendumRepo;
    private final ClinicPrescriptionRepository           prescriptionRepo;
    private final ClinicPractitionerRepository           practitionerRepo;
    private final ClinicStaffNames                       staffNames;

    @Transactional(readOnly = true)
    public List<VisitResponse> visits(TenantId t, UUID patientId) {
        patientRepo.findActiveById(t, patientId).orElseThrow(() -> new ResourceNotFoundException("Patient", patientId.toString()));
        List<ClinicConsultation> list = consultationRepo.findByPatient(t, patientId).stream()
                .filter(c -> !"ABANDONED".equals(c.getStatus()))
                .sorted(Comparator.comparing(ClinicConsultation::getConsultedAt).reversed()).toList();

        // One read per kind for the whole list of visits, then group.
        Map<UUID, List<za.co.handyflow.platform.clinic.domain.model.ClinicConsultationTransition>> transitions = new HashMap<>();
        Map<UUID, List<za.co.handyflow.platform.clinic.domain.model.ClinicConsultationAddendum>> addenda = new HashMap<>();
        Map<UUID, List<za.co.handyflow.platform.clinic.domain.model.ClinicPrescription>> rx = new HashMap<>();
        Set<UUID> userIds = new HashSet<>();
        for (ClinicConsultation c : list) {
            var tr = transitionRepo.findByConsultation(t, c.getId());
            var ad = addendumRepo.findByConsultation(t, c.getId());
            transitions.put(c.getId(), tr); addenda.put(c.getId(), ad); rx.put(c.getId(), prescriptionRepo.findByConsultation(t, c.getId()));
            userIds.add(c.getCreatedBy()); userIds.add(c.getSignedBy());
            tr.forEach(x -> userIds.add(x.getActorUserId())); ad.forEach(x -> userIds.add(x.getAuthorUserId()));
        }
        Map<UUID, String> names = staffNames.names(t.getValue(), userIds);
        Set<UUID> practIds = list.stream().map(ClinicConsultation::getPractitionerId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, String> practNames = new HashMap<>();
        practitionerRepo.findAllById(practIds).forEach(p -> practNames.put(p.getId(), p.getFullName() != null ? p.getFullName() : p.getFirstName() + " " + p.getLastName()));

        List<VisitResponse> out = new ArrayList<>();
        for (ClinicConsultation c : list) {
            String doctor = practNames.get(c.getPractitionerId());
            var steps = transitions.get(c.getId()).stream()
                    .map(x -> new VisitTeamRules.Step(x.getToStatus(), x.getActorUserId(), x.getCreatedAt())).toList();
            var team = VisitTeamRules.team(steps, c.getCreatedBy(), c.getSignedBy(), c.getSignedAt(), doctor, names::get).stream()
                    .map(m -> new TeamMember(m.role(), m.name(), m.at())).toList();
            out.add(new VisitResponse(c.getId(), c.getAppointmentId(), c.getStatus(), c.getConsultedAt(), c.getSignedAt(), doctor, team,
                    c.getChiefComplaint(), c.getHistory(), c.getExamination(), c.getDiagnosis(), c.getIcd10Codes(),
                    c.getTreatmentPlan(), c.getFollowUpDays(),
                    c.getWeightKg(), c.getHeightCm(), c.getBloodPressure(), c.getPulseBpm(), c.getTemperatureC(), c.getOxygenSatPct(),
                    c.isBilled(), c.getBillingAmount(),
                    rx.get(c.getId()).stream().map(p -> new VisitPrescription(p.getId(), p.getMedicationName(), p.getDosage(), p.getFrequency(),
                            p.getDuration(), p.getQuantity(), p.getRepeats(), p.getInstructions(), p.getPrescribedAt(), p.isDispensed())).toList(),
                    addenda.get(c.getId()).stream().map(a -> new VisitAddendum(a.getId(), a.getText(), names.get(a.getAuthorUserId()), a.getCreatedAt())).toList()));
        }
        return out;
    }
}
