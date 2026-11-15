package za.co.handyflow.platform.fuel.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Freight that arrives after a stock receipt. */
public final class FreightAdjustmentDtos {
    private FreightAdjustmentDtos() {}

    public record AddFreightRequest(@NotNull @DecimalMin(value = "0.01", message = "The freight must be more than zero.") BigDecimal amount,
                                    @NotBlank @Size(max = 300) String note) {}

    /** absorbed went into the tank's cost; unabsorbed belongs to fuel already sold and was not pushed back into past deliveries. */
    public record FreightAdjustmentView(UUID id, BigDecimal amount, String note, BigDecimal litresInTank, BigDecimal absorbedAmount, BigDecimal unabsorbedAmount,
                                        BigDecimal costBefore, BigDecimal costAfter, Instant createdAt, UUID createdBy) {}

    /** The receipt as it stands now, plus every addition made to it. */
    public record ReceiptFreightView(UUID receiptId, BigDecimal freightTotal, BigDecimal landedPricePerLitre, List<FreightAdjustmentView> adjustments) {}
}
