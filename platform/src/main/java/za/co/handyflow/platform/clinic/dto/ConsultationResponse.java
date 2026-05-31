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
        Instant createdAt
) {}