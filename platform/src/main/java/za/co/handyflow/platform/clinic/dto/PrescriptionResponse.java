package za.co.handyflow.platform.clinic.dto;

import java.time.Instant;
import java.util.UUID;

public record PrescriptionResponse(
        UUID id,
        UUID consultationId,
        UUID patientId,
        String medicationName,
        String dosage,
        String frequency,
        String duration,
        Integer quantity,
        int repeats,
        String instructions,
        boolean dispensed,
        Instant prescribedAt,
        String nappiCode,
        Integer schedule,
        String allergyOverrideReason,
        String allergyAlertSummary,
        int fillsUsed,
        int fillsRemaining
) {
    /** Pre-fills constructor kept so existing callers compile. */
    public PrescriptionResponse(UUID id, UUID consultationId, UUID patientId, String medicationName,
                                String dosage, String frequency, String duration, Integer quantity,
                                int repeats, String instructions, boolean dispensed,
                                Instant prescribedAt, String nappiCode, Integer schedule,
                                String allergyOverrideReason, String allergyAlertSummary) {
        this(id, consultationId, patientId, medicationName, dosage, frequency, duration, quantity,
                repeats, instructions, dispensed, prescribedAt, nappiCode, schedule,
                allergyOverrideReason, allergyAlertSummary, dispensed ? 1 : 0, dispensed ? repeats : repeats + 1);
    }

    /** Pre-override constructor kept so existing callers compile. */
    public PrescriptionResponse(UUID id, UUID consultationId, UUID patientId, String medicationName,
                                String dosage, String frequency, String duration, Integer quantity,
                                int repeats, String instructions, boolean dispensed,
                                Instant prescribedAt, String nappiCode, Integer schedule) {
        this(id, consultationId, patientId, medicationName, dosage, frequency, duration, quantity,
                repeats, instructions, dispensed, prescribedAt, nappiCode, schedule, null, null);
    }

    /** Pre-NAPPI constructor kept so existing callers compile. */
    public PrescriptionResponse(UUID id, UUID consultationId, UUID patientId, String medicationName,
                                String dosage, String frequency, String duration, Integer quantity,
                                int repeats, String instructions, boolean dispensed,
                                Instant prescribedAt) {
        this(id, consultationId, patientId, medicationName, dosage, frequency, duration, quantity,
                repeats, instructions, dispensed, prescribedAt, null, null, null, null);
    }
}