package za.co.handyflow.platform.events.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Give commitmentId (the budget line is then taken from it) or, for a cost with
 * no supplier commitment, budgetLineId. sourceModule and sourceRef link the
 * entry to the authoritative accounting record and must be given together.
 */
public record CostEntryRequest(
        UUID commitmentId,
        UUID budgetLineId,
        @NotBlank @Pattern(regexp = "INVOICE|PAYMENT", message = "kind must be INVOICE or PAYMENT") String kind,
        boolean deposit,
        @NotNull @Positive BigDecimal amount,
        @NotNull LocalDate entryDate,
        @Size(max = 100) String reference,
        @Size(max = 300) String description,
        @Size(max = 50) String sourceModule,
        @Size(max = 100) String sourceRef
) {}