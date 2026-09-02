package za.co.handyflow.platform.legalpractice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CreateLpMatterKeyDateRequest(
        @NotBlank String dateType,
        @NotNull LocalDate dueDate,
        @NotBlank String description,
        String notes
) {}
