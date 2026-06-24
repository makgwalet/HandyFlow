package za.co.handyflow.platform.supplychain.dto;

import java.time.LocalDate;
import java.util.UUID;

public record CreatePurchaseOrderRequest(
        UUID supplierId,
        UUID deliverToLocation,
        LocalDate orderDate,
        LocalDate requiredByDate,
        String currency,
        String projectRef,
        String notes,
        String internalNotes
) {}

