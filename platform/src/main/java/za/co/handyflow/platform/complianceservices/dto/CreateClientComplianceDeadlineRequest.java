package za.co.handyflow.platform.complianceservices.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

public record CreateClientComplianceDeadlineRequest(
        UUID registrationId, @NotBlank String deadlineType, String description, @NotNull LocalDate dueDate
) {}
