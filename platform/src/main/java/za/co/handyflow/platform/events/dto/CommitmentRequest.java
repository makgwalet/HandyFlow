package za.co.handyflow.platform.events.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CommitmentRequest(
        @NotNull UUID budgetLineId,
        UUID vendorId,
        @NotBlank @Size(max = 300) String description,
        @NotNull @Positive BigDecimal amount,
        @Size(max = 100) String quoteReference,
        @Size(max = 100) String poReference,
        @Size(max = 100) String contractReference,
        @Size(max = 500) String deliverable,
        LocalDate dueDate,
        @PositiveOrZero BigDecimal depositAmount,
        LocalDate depositDueDate
) {}