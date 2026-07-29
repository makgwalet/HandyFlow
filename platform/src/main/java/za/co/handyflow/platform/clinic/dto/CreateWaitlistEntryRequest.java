package za.co.handyflow.platform.clinic.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateWaitlistEntryRequest(
        @NotNull UUID patientId,
        UUID practitionerId,       // null = any available practitioner
        String appointmentType,
        String notes
) {}