package za.co.handyflow.platform.clinic.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.clinic.domain.model.ClinicConsultation;
import za.co.handyflow.platform.clinic.domain.model.ClinicPatient;
import za.co.handyflow.platform.clinic.domain.model.ClinicPractitioner;
import za.co.handyflow.platform.clinic.domain.repository.ClinicConsultationRepository;
import za.co.handyflow.platform.clinic.domain.repository.ClinicPatientRepository;
import za.co.handyflow.platform.clinic.domain.repository.ClinicPractitionerRepository;
import za.co.handyflow.platform.clinic.dto.RecallResponse;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * FIX: "no recall/follow-up dashboard" gap — ConsultationSession captured
 * followUpDays on every consultation, but nothing surfaced "patients due
 * for follow-up" anywhere; it was captured and then seemingly unused.
 * <p>
 * Deliberately its own small service rather than folded into ClinicService
 * — this is a read-only derived view over consultations, not a mutation of
 * consultation state.
 */
@Service
@RequiredArgsConstructor
public class ClinicRecallService {

    private final ClinicConsultationRepository consultationRepo;
    private final ClinicPatientRepository patientRepo;
    private final ClinicPractitionerRepository practitionerRepo;

    @Transactional(readOnly = true)
    public List<RecallResponse> getDueRecalls(TenantId tenantId) {
        List<ClinicConsultation> all = consultationRepo.findAllWithFollowUp(tenantId);

        // Only the most recent consultation per patient matters — an earlier
        // visit's follow-up window is moot if they've already been seen
        // again since (the later visit either re-set the follow-up window
        // or resolved whatever the earlier one was tracking).
        Map<UUID, ClinicConsultation> latestByPatient = new LinkedHashMap<>();
        for (ClinicConsultation c : all) {
            latestByPatient.merge(c.getPatientId(), c,
                    (existing, incoming) -> incoming.getConsultedAt().isAfter(existing.getConsultedAt()) ? incoming : existing);
        }

        LocalDate today = LocalDate.now();
        List<ClinicConsultation> due = latestByPatient.values().stream()
                .filter(c -> !dueDate(c).isAfter(today))
                .sorted(Comparator.comparing(this::dueDate))
                .toList();

        Set<UUID> patientIds = due.stream().map(ClinicConsultation::getPatientId).collect(Collectors.toSet());
        Set<UUID> practIds = due.stream().map(ClinicConsultation::getPractitionerId).filter(Objects::nonNull).collect(Collectors.toSet());

        List<ClinicPatient> patients = patientIds.isEmpty() ? List.of() : patientRepo.findAllByIds(tenantId, patientIds);
        Map<UUID, String> patientNames = patients.stream().collect(Collectors.toMap(ClinicPatient::getId, ClinicPatient::getFullName));
        Map<UUID, String> patientPhones = patients.stream()
                .collect(Collectors.toMap(ClinicPatient::getId, p -> p.getPhone() != null ? p.getPhone() : ""));
        Map<UUID, String> practNames = practIds.isEmpty() ? Map.of()
                : practitionerRepo.findAllByIds(tenantId, practIds).stream()
                .collect(Collectors.toMap(ClinicPractitioner::getId, ClinicPractitioner::getFullName));

        return due.stream()
                .map(c -> {
                    LocalDate dd = dueDate(c);
                    int overdueDays = (int) Math.max(0, ChronoUnit.DAYS.between(dd, today));
                    return new RecallResponse(
                            c.getId(), c.getPatientId(),
                            patientNames.getOrDefault(c.getPatientId(), "Patient"),
                            patientPhones.get(c.getPatientId()),
                            c.getPractitionerId(),
                            c.getPractitionerId() != null ? practNames.get(c.getPractitionerId()) : null,
                            c.getConsultedAt(), c.getFollowUpDays(), dd, overdueDays,
                            c.getDiagnosis()
                    );
                })
                .toList();
    }

    private LocalDate dueDate(ClinicConsultation c) {
        return c.getConsultedAt().atZone(ZoneId.systemDefault()).toLocalDate().plusDays(c.getFollowUpDays());
    }
}