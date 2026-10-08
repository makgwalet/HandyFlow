package za.co.handyflow.platform.clinic.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.clinic.domain.repository.*;
import za.co.handyflow.platform.clinic.dto.TimelineEvent;
import za.co.handyflow.platform.clinic.dto.VisitDtos.VisitResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** One chronological view of a patient's visits, prescriptions, results and (for billing users) claims and payments. */
@Service
@RequiredArgsConstructor
public class ClinicTimelineService {

    private final ClinicPatientRepository patientRepo;
    private final ClinicAppointmentRepository appointmentRepo;
    private final ClinicConsultationRepository consultationRepo;
    private final ClinicPrescriptionRepository prescriptionRepo;
    private final ClinicLabResultRepository labRepo;
    private final ClinicClaimRepository claimRepo;
    private final ClinicPaymentRepository paymentRepo;
    private final ClinicVisitService visitService;

    @Transactional(readOnly = true)
    public List<TimelineEvent> timeline(TenantId t, UUID patientId, Set<String> kinds, Instant from, Instant to,
                                        boolean includeBilling, Integer limit) {
        patientRepo.findActiveById(t, patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", patientId.toString()));
        boolean want = kinds == null || kinds.isEmpty();
        List<TimelineEvent> all = new ArrayList<>();

        if (want || kinds.contains("APPOINTMENT")) appointmentRepo.findByPatient(t, patientId).forEach(a ->
                all.add(new TimelineEvent("APPOINTMENT", a.getId(), a.getScheduledAt(),
                        "Appointment: " + label(a.getAppointmentType()), clean(a.getReason()), a.getStatus())));

        if (want || kinds.contains("CONSULTATION")) {
            Map<UUID, VisitResponse> visits = visitService.visits(t, patientId).stream()
                    .collect(Collectors.toMap(VisitResponse::id, v -> v, (a, b) -> a));
            consultationRepo.findByPatient(t, patientId).stream()
                    .filter(c -> !"ABANDONED".equals(c.getStatus())).forEach(c -> {
                TimelineEvent e = new TimelineEvent("CONSULTATION", c.getId(), c.getConsultedAt(),
                        "Consultation: " + (clean(c.getChiefComplaint()) == null ? "no complaint recorded" : c.getChiefComplaint()),
                        clean(c.getDiagnosis()), c.getStatus());
                VisitResponse v = visits.get(c.getId());
                all.add(v == null ? e : e.withVisit(TimelineVisitRules.summary(v), TimelineVisitRules.people(v)));
            });
        }

        if (want || kinds.contains("PRESCRIPTION")) prescriptionRepo.findByPatient(t, patientId).forEach(p ->
                all.add(new TimelineEvent("PRESCRIPTION", p.getId(), p.getPrescribedAt(),
                        "Prescribed: " + p.getMedicationName(),
                        clean(java.util.stream.Stream.of(p.getDosage(), p.getFrequency(), p.getDuration())
                                .filter(Objects::nonNull).filter(s -> !s.isBlank()).collect(Collectors.joining(" · "))),
                        p.isDispensed() ? "DISPENSED" : "ACTIVE")));

        if (want || kinds.contains("LAB")) labRepo.findByPatient(t, patientId).forEach(l ->
                all.add(new TimelineEvent("LAB", l.getId(),
                        l.getCollectedAt() != null ? l.getCollectedAt() : l.getReceivedAt() != null ? l.getReceivedAt() : l.getCreatedAt(),
                        "Lab result" + (clean(l.getLabReference()) == null ? "" : ": " + l.getLabReference()),
                        clean(l.getSource()), l.getStatus())));

        if (includeBilling) {
            if (want || kinds.contains("CLAIM")) claimRepo.findByPatient(t, patientId).forEach(c ->
                    all.add(new TimelineEvent("CLAIM", c.getId(), c.getSubmittedAt() != null ? c.getSubmittedAt() : c.getCreatedAt(),
                            "Claim" + (clean(c.getSchemeName()) == null ? "" : ": " + c.getSchemeName()),
                            "Gross R" + c.getGrossAmount(), c.getStatus())));
            if (want || kinds.contains("PAYMENT")) paymentRepo.findByPatient(t, patientId).forEach(p ->
                    all.add(new TimelineEvent("PAYMENT", p.getId(), p.getRecordedAt(),
                            "Payment received: " + label(p.getPaymentMethod()),
                            "R" + p.getAmount() + (clean(p.getReference()) == null ? "" : " · " + p.getReference()), null)));
        }
        return TimelineAssembler.select(all, kinds, from, to, includeBilling, limit);
    }

    private static String label(String v) { return v == null ? "" : v.toLowerCase().replace('_', ' '); }
    private static String clean(String v) { return v == null || v.isBlank() ? null : v.trim(); }
}
