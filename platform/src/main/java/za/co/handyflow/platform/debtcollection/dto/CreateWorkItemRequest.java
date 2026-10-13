package za.co.handyflow.platform.debtcollection.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record CreateWorkItemRequest(
        @NotNull UUID caseId,
        @NotBlank String title,
        LocalDate dueDate,
        UUID assignedToUserId,
        String assignedToUserName
) {}
