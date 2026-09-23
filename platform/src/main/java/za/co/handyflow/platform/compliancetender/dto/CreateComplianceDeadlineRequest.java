package za.co.handyflow.platform.compliancetender.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

public record CreateComplianceDeadlineRequest(
        UUID registrationId, @NotBlank String deadlineType, String description, @NotNull LocalDate dueDate
) {}
