package za.co.handyflow.platform.agriculture.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record RecordPlantingRequest(
        @NotNull LocalDate plantingDate,
        UUID seedInventoryItemId,
        BigDecimal seedQuantity,
        String seedSource
) {}
