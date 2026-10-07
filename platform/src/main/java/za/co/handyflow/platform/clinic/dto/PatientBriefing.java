package za.co.handyflow.platform.clinic.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Everything a clinician wants to know about a patient before the consultation starts, in one response:
 * when they were last here and why, what is wrong, what they take, what needs attention.
 */
public record PatientBriefing(
        UUID patientId,
        /** Finished (signed, locked or doctor-completed) visits. */
        int visitCount,
        Visit lastVisit,
        /** Whole clinic-local calendar days since the last finished visit; null if never seen. */
        Long daysSinceLastVisit,
        Vitals lastVitals,
        NextAppointment nextAppointment,
        Recall recall,
        OpenDraft openDraft,
        List<Visit> recentVisits,
        List<PatientClinicalDtos.AllergyResponse> allergies,
        List<PatientClinicalDtos.ConditionResponse> conditions,
        List<PatientClinicalDtos.MedicationResponse> medications,
        Labs labs,
        List<Alert> alerts
) {
    public record Visit(UUID id, Instant at, String practitionerName, String chiefComplaint, String diagnosis,
                        List<String> icd10Codes, Integer followUpDays, String status) {}

    /** The most recent vitals on record, with the visit they were taken at. */
    public record Vitals(Instant takenAt, BigDecimal weightKg, BigDecimal heightCm, String bloodPressure,
                         Integer pulseBpm, BigDecimal temperatureC, BigDecimal oxygenSatPct) {}

    public record NextAppointment(UUID id, Instant at, String type, String status, String practitionerName, String reason) {}

    /** A follow-up promised at the last visit that has not been booked. overdueDays is 0 when not yet due. */
    public record Recall(LocalDate dueDate, int overdueDays, boolean due) {}

    /** A consultation started but not signed. */
    public record OpenDraft(UUID id, String status, Instant startedAt) {}

    public record Labs(int unreviewed, int unreviewedAbnormal, int unreviewedCritical, Instant latestAt,
                       List<LabItem> recent) {}

    public record LabItem(UUID id, Instant at, String reference, boolean abnormal, boolean critical, boolean reviewed) {}

    /** severity: DANGER, WARNING or INFO. */
    public record Alert(String code, String severity, String message) {}
}
