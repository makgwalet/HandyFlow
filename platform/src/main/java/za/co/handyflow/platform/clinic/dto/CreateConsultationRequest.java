package za.co.handyflow.platform.clinic.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CreateConsultationRequest(
        UUID appointmentId,
        UUID practitionerId,
        String chiefComplaint,
        // Vitals
        BigDecimal weightKg,
        BigDecimal heightCm,
        String bloodPressure,
        Integer pulseBpm,
        BigDecimal temperatureC,
        BigDecimal oxygenSatPct,
        // Clinical
        String history,
        String examination,
        String diagnosis,
        List<String> icd10Codes,
        String treatmentPlan,
        Integer followUpDays
) {}