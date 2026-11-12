package za.co.handyflow.platform.fuel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** B05: purchase requisitions. */
public final class RequisitionDtos {
    private RequisitionDtos() {}

    public record RequisitionRequest(@NotBlank String fuelType, @NotNull @Positive BigDecimal litres, UUID tankId, UUID supplierId,
                                     @PositiveOrZero BigDecimal estimatedPrice, LocalDate neededBy, @NotBlank @Size(min = 5, max = 300, message = "Reason must be 5 to 300 characters") String reason) {}

    /** Anything left out falls back to what the requisition already holds; a supplier and a price must exist one way or the other. */
    public record ConvertRequest(UUID supplierId, @PositiveOrZero BigDecimal pricePerLitre, LocalDate expectedDate) {}

    public record RequisitionView(UUID id, String reqNumber, String status, String fuelType, BigDecimal litres, UUID tankId, UUID supplierId, String supplierName,
                                  BigDecimal estimatedPrice, BigDecimal estimatedValue, LocalDate neededBy, String reason, UUID requestedBy, String approvalNote,
                                  UUID purchaseOrderId, String purchaseOrderNumber, String cancelReason, Instant createdAt) {}
}
