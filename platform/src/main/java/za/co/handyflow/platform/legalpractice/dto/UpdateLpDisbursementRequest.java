package za.co.handyflow.platform.legalpractice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdateLpDisbursementRequest(
        LocalDate disbursementDate,
        @NotBlank String description,
        @NotNull BigDecimal amount
) {}
