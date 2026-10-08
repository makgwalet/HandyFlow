package za.co.handyflow.platform.clinic.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A patient's visit with everything a clinician wants when reading the history (patch 0160). */
public final class VisitDtos {
    private VisitDtos() {}

    public record TeamMember(String role, String name, Instant at) {}

    public record VisitPrescription(UUID id, String medicationName, String dosage, String frequency, String duration,
                                    Integer quantity, int repeats, String instructions, Instant prescribedAt, boolean dispensed) {}

    public record VisitAddendum(UUID id, String text, String authorName, Instant createdAt) {}

    public record VisitResponse(
            UUID id, UUID appointmentId, String status, Instant consultedAt, Instant signedAt,
            String doctorName, List<TeamMember> team,
            String chiefComplaint, String history, String examination, String diagnosis, List<String> icd10Codes,
            String treatmentPlan, Integer followUpDays,
            BigDecimal weightKg, BigDecimal heightCm, String bloodPressure, Integer pulseBpm, BigDecimal temperatureC, BigDecimal oxygenSatPct,
            boolean billed, BigDecimal billingAmount,
            List<VisitPrescription> prescriptions, List<VisitAddendum> addenda) {}
}
