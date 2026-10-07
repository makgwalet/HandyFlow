package za.co.handyflow.platform.clinic.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** One historical version of a consultation: the values it had BEFORE the edit at {@code editedAt}. */
public record ConsultationEditResponse(
        UUID id,
        UUID consultationId,
        UUID editedBy,
        Instant editedAt,
        String chiefComplaint,
        String history,
        String examination,
        String diagnosis,
        List<String> icd10Codes,
        String treatmentPlan,
        Integer followUpDays,
        BigDecimal weightKg,
        BigDecimal heightCm,
        String bloodPressure,
        Integer pulseBpm,
        BigDecimal temperatureC,
        BigDecimal oxygenSatPct
) {}
