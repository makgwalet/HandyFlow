package za.co.handyflow.platform.supplychain.dto;

import za.co.handyflow.platform.supplychain.domain.enums.SupplierStatus;

import java.time.Instant;
import java.util.UUID;

public record SupplierStatusResponse(
        UUID id,
        String name,
        SupplierStatus status,
        String statusReason,
        Instant statusChangedAt,
        long openPurchaseOrders
) {}
