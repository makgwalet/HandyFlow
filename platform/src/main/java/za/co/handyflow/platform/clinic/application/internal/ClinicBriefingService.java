package za.co.handyflow.platform.clinic.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.clinic.domain.model.ClinicAppointment;
import za.co.handyflow.platform.clinic.domain.model.ClinicConsultation;
import za.co.handyflow.platform.clinic.domain.model.ClinicLabResult;
import za.co.handyflow.platform.clinic.domain.model.ClinicPractitioner;
import za.co.handyflow.platform.clinic.domain.repository.*;
import za.co.handyflow.platform.clinic.dto.PatientBriefing;
import za.co.handyflow.platform.clinic.dto.PatientBriefing.*;
import za.co.handyflow.platform.clinic.dto.PatientClinicalDtos.AllergyResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Builds the pre-consultation briefing for one patient from existing records. Read-only. */
@Service
@RequiredArgsConstructor
public class ClinicBriefingService {

    private static final int RECENT_VISITS = 5;
    private static final int RECENT_LABS = 3;

    private final ClinicPatientRepository patientRepo;
    private final ClinicConsultationRepository consultationRepo;
    private final ClinicAppointmentRepository appointmentRepo;
    private final ClinicLabResultRepository labRepo;
    private final ClinicPractitionerRepository practitionerRepo;
    private final ClinicPatientClinicalService clinical;

    @Transactional(readOnly = true)
    public PatientBriefing briefing(TenantId t, UUID patientId) {
        return briefing(t, patientId, Instant.now());
    }

    @Transactional(readOnly = true)
    public PatientBriefing briefing(TenantId t, UUID patientId, Instant now) {
        patientRepo.findActiveById(t, patientId).orElseThrow(() -> new ResourceNotFoundException("Patient", patientId.toString()));

        List<ClinicConsultation> consults = consultationRepo.findByPatient(t, patientId);
        List<ClinicAppointment> appts = appointmentRepo.findByPatient(t, patientId);
        List<ClinicLabResult> labs = labRepo.findByPatient(t, patientId);

        Map<UUID, ClinicConsultation> byId = consults.stream().collect(Collectors.toMap(ClinicConsultation::getId, c -> c, (a, b) -> a));
        List<BriefingRules.Visit> facts = consults.stream()
                .map(c -> new BriefingRules.Visit(c.getId(), c.getConsultedAt(), c.getStatus(), c.getFollowUpDays(), c.getAppointmentId())).toList();
        List<BriefingRules.Visit> finished = BriefingRules.finishedNewestFirst(facts);

        Set<UUID> practIds = Stream.concat(
                        finished.stream().limit(RECENT_VISITS).map(v -> byId.get(v.id()).getPractitionerId()),
                        appts.stream().map(ClinicAppointment::getPractitionerId))
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, String> names = practIds.isEmpty() ? Map.of() : practitionerRepo.findAllByIds(t, practIds).stream()
                .collect(Collectors.toMap(ClinicPractitioner::getId, p -> p.getFirstName() + " " + p.getLastName(), (a, b) -> a));

        List<Visit> recent = finished.stream().limit(RECENT_VISITS).map(v -> visit(byId.get(v.id()), names)).toList();
        Visit last = recent.isEmpty() ? null : recent.get(0);
        Long days = last == null ? null : BriefingRules.daysSince(last.at(), now, BriefingRules.CLINIC_ZONE);

        Vitals vitals = finished.stream().map(v -> byId.get(v.id())).filter(ClinicBriefingService::hasVitals)
                .findFirst().map(c -> new Vitals(c.getConsultedAt(), c.getWeightKg(), c.getHeightCm(), c.getBloodPressure(),
                        c.getPulseBpm(), c.getTemperatureC(), c.getOxygenSatPct())).orElse(null);

        List<BriefingRules.Appt> apptFacts = appts.stream().map(a -> new BriefingRules.Appt(a.getId(), a.getScheduledAt(), a.getStatus())).toList();
        NextAppointment next = BriefingRules.nextAppointment(apptFacts, now, BriefingRules.CLINIC_ZONE)
                .flatMap(n -> appts.stream().filter(a -> a.getId().equals(n.id())).findFirst())
                .map(a -> new NextAppointment(a.getId(), a.getScheduledAt(), a.getAppointmentType(), a.getStatus(),
                        a.getPractitionerId() == null ? null : names.get(a.getPractitionerId()), a.getReason())).orElse(null);

        Recall recall = BriefingRules.recall(finished.isEmpty() ? null : finished.get(0), apptFacts,
                now.atZone(BriefingRules.CLINIC_ZONE).toLocalDate(), BriefingRules.CLINIC_ZONE);

        OpenDraft draft = consults.stream().filter(c -> c.isUnsigned() && !"ABANDONED".equals(c.getStatus()))
                .max(Comparator.comparing(ClinicConsultation::getConsultedAt))
                .map(c -> new OpenDraft(c.getId(), c.getStatus(), c.getConsultedAt())).orElse(null);

        var allergies = clinical.listAllergies(t, patientId, false);
        var conditions = clinical.listConditions(t, patientId, false);
        var meds = clinical.listMedications(t, patientId, false);

        Labs labSummary = labs(labs);
        List<String> severe = allergies.stream().filter(a -> a.severity() != null && BriefingRules.SEVERE.contains(a.severity()))
                .map(AllergyResponse::allergen).toList();

        return new PatientBriefing(patientId, finished.size(), last, days, vitals, next, recall, draft, recent,
                allergies, conditions, meds, labSummary,
                BriefingRules.alerts(severe, labSummary.unreviewedCritical(), labSummary.unreviewedAbnormal(), recall, draft != null, finished.size()));
    }

    private static boolean hasVitals(ClinicConsultation c) {
        return c.getWeightKg() != null || c.getHeightCm() != null || (c.getBloodPressure() != null && !c.getBloodPressure().isBlank())
                || c.getPulseBpm() != null || c.getTemperatureC() != null || c.getOxygenSatPct() != null;
    }

    private static Visit visit(ClinicConsultation c, Map<UUID, String> names) {
        return new Visit(c.getId(), c.getConsultedAt(), c.getPractitionerId() == null ? null : names.get(c.getPractitionerId()),
                blankToNull(c.getChiefComplaint()), blankToNull(c.getDiagnosis()),
                c.getIcd10Codes() == null ? List.of() : c.getIcd10Codes(), c.getFollowUpDays(), c.getStatus());
    }

    private static Labs labs(List<ClinicLabResult> all) {
        List<ClinicLabResult> unreviewed = all.stream().filter(l -> l.getReviewedAt() == null).toList();
        Comparator<ClinicLabResult> newest = Comparator.comparing(ClinicBriefingService::labTime, Comparator.nullsFirst(Comparator.naturalOrder())).reversed();
        List<LabItem> recent = all.stream().sorted(newest).limit(RECENT_LABS)
                .map(l -> new LabItem(l.getId(), labTime(l), blankToNull(l.getLabReference()), l.isHasAbnormal(), l.isHasCritical(), l.getReviewedAt() != null)).toList();
        Instant latest = recent.isEmpty() ? null : recent.get(0).at();
        return new Labs(unreviewed.size(), (int) unreviewed.stream().filter(ClinicLabResult::isHasAbnormal).count(),
                (int) unreviewed.stream().filter(ClinicLabResult::isHasCritical).count(), latest, recent);
    }

    private static Instant labTime(ClinicLabResult l) {
        return l.getCollectedAt() != null ? l.getCollectedAt() : l.getReceivedAt() != null ? l.getReceivedAt() : l.getCreatedAt();
    }

    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }
}
