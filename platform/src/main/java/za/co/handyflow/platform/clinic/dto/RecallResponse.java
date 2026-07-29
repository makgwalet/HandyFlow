package za.co.handyflow.platform.clinic.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record RecallResponse(
        UUID consultationId,
        UUID patientId,
        String patientName,
        String patientPhone,
        UUID practitionerId,
        String practitionerName,
        Instant consultedAt,
        int followUpDays,
        LocalDate dueDate,
        int overdueDays,
        String diagnosis
) {}