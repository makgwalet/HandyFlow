package za.co.handyflow.platform.security.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

public record RecordScreeningResultRequest(
        @NotBlank String result,         // PASS | FAIL | INCONCLUSIVE
        String conductedBy,
        LocalDate conductedAt,
        LocalDate nextDueAt,
        String reportRef,
        String notes
) {}