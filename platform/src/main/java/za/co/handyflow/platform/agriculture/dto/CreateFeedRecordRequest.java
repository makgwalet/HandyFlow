package za.co.handyflow.platform.agriculture.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateFeedRecordRequest(
        UUID animalId,
        UUID groupId,
        @NotNull LocalDate feedDate,
        UUID inventoryItemId,
        @NotBlank String feedType,
        BigDecimal quantityKg,      // give this or quantityBags, not both (the service checks)
        BigDecimal costPerKg,
        String notes,
        BigDecimal quantityBags     // bags, bales or sacks of the chosen stock item; needs its kg per bag
) {
    public CreateFeedRecordRequest(UUID animalId, UUID groupId, LocalDate feedDate, UUID inventoryItemId, String feedType,
                                   BigDecimal quantityKg, BigDecimal costPerKg, String notes) {
        this(animalId, groupId, feedDate, inventoryItemId, feedType, quantityKg, costPerKg, notes, null);
    }
}
