package za.co.handyflow.platform.clinic.dto;

import java.time.Instant;
import java.util.UUID;

public record WaitlistEntryResponse(
        UUID id,
        UUID patientId,
        String patientName,
        UUID practitionerId,
        String practitionerName,
        String appointmentType,
        String notes,
        String status,
        Instant createdAt
) {}