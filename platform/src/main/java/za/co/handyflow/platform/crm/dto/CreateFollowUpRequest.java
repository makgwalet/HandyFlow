package za.co.handyflow.platform.crm.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record CreateFollowUpRequest(
        @NotNull @FutureOrPresent LocalDate dueDate,
        @NotBlank String note,
        UUID assignedTo   // null = assign to whoever creates it
) {}