package za.co.handyflow.platform.agriculture.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Request and response shapes for the Agriculture cost ledger (ADR-001, W1). Held together because they only make sense together. */
public final class CostEntryDtos {

    private CostEntryDtos() {}

    /** One target's share of a cost. Percentages across a request must total 100. */
    public record AllocationShare(@NotBlank String targetType, @NotNull UUID targetId, @NotNull @Positive BigDecimal percentage) {}

    /**
     * Records a cost and allocates it. Only OTHER_DIRECT may be entered by hand: labour, equipment and fuel are costed from HR,
     * Fleet and Fuel when those integrations arrive, so they are never typed in twice.
     */
    public record CreateCostEntryRequest(
            @NotNull LocalDate entryDate,
            String category,                       // optional; OTHER_DIRECT when omitted
            @NotBlank String description,
            @NotNull @Positive BigDecimal amount,
            BigDecimal quantity,
            String unit,
            String notes,
            @Valid @NotNull List<AllocationShare> allocations
    ) {}

    public record ReverseCostEntryRequest(String reason) {}

    public record CostEntryResponse(
            UUID id, UUID farmId, LocalDate entryDate, String category, String description,
            String sourceType, UUID sourceRef, String targetType, UUID targetId,
            BigDecimal quantity, String unit, BigDecimal rate, BigDecimal amount, BigDecimal percentage,
            UUID allocationGroupId, UUID reversesEntryId, String status, String notes, UUID createdBy, Instant createdAt
    ) {}

    public record CategoryTotal(String category, BigDecimal amount) {}

    /** Net of reversals. */
    public record CostTotalsResponse(List<CategoryTotal> byCategory, BigDecimal total) {}
}
