package za.co.handyflow.platform.clinic.dto;

import java.time.Instant;
import java.util.UUID;

public record AppointmentResponse(
        UUID id,
        UUID patientId,
        String patientName,
        UUID practitionerId,
        String practitionerName,
        Instant scheduledAt,
        int durationMinutes,
        String appointmentType,
        String status,
        String reason,
        String notes,
        Instant createdAt
) {}