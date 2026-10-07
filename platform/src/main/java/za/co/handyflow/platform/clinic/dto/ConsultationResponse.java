package za.co.handyflow.platform.clinic.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ConsultationResponse(
        UUID id,
        UUID patientId,
        String patientName,
        UUID practitionerId,
        String practitionerName,
        UUID appointmentId,
        Instant consultedAt,
        BigDecimal weightKg,
        BigDecimal heightCm,
        String bloodPressure,
        Integer pulseBpm,
        BigDecimal temperatureC,
        BigDecimal oxygenSatPct,
        String chiefComplaint,
        String history,
        String examination,
        String diagnosis,
        List<String> icd10Codes,
        String treatmentPlan,
        Integer followUpDays,
        boolean billed,
        BigDecimal billingAmount,
        Instant createdAt,
        String status,
        Instant updatedAt
) {
    /** Pre-status constructor kept so existing callers compile; such rows are SIGNED. */
    public ConsultationResponse(UUID id, UUID patientId, String patientName, UUID practitionerId,
                                String practitionerName, UUID appointmentId, Instant consultedAt,
                                BigDecimal weightKg, BigDecimal heightCm, String bloodPressure,
                                Integer pulseBpm, BigDecimal temperatureC, BigDecimal oxygenSatPct,
                                String chiefComplaint, String history, String examination,
                                String diagnosis, List<String> icd10Codes, String treatmentPlan,
                                Integer followUpDays, boolean billed, BigDecimal billingAmount,
                                Instant createdAt) {
        this(id, patientId, patientName, practitionerId, practitionerName, appointmentId,
                consultedAt, weightKg, heightCm, bloodPressure, pulseBpm, temperatureC,
                oxygenSatPct, chiefComplaint, history, examination, diagnosis, icd10Codes,
                treatmentPlan, followUpDays, billed, billingAmount, createdAt, "SIGNED", createdAt);
    }

    /** Pre-updatedAt constructor kept so existing callers compile; last-saved falls back to creation. */
    public ConsultationResponse(UUID id, UUID patientId, String patientName, UUID practitionerId,
                                String practitionerName, UUID appointmentId, Instant consultedAt,
                                BigDecimal weightKg, BigDecimal heightCm, String bloodPressure,
                                Integer pulseBpm, BigDecimal temperatureC, BigDecimal oxygenSatPct,
                                String chiefComplaint, String history, String examination,
                                String diagnosis, List<String> icd10Codes, String treatmentPlan,
                                Integer followUpDays, boolean billed, BigDecimal billingAmount,
                                Instant createdAt, String status) {
        this(id, patientId, patientName, practitionerId, practitionerName, appointmentId,
                consultedAt, weightKg, heightCm, bloodPressure, pulseBpm, temperatureC,
                oxygenSatPct, chiefComplaint, history, examination, diagnosis, icd10Codes,
                treatmentPlan, followUpDays, billed, billingAmount, createdAt, status, createdAt);
    }
}