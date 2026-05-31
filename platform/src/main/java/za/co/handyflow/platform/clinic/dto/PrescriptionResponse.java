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
        Instant prescribedAt
) {}